package ir.kimia.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.UnsupportedArchitectureException
import ir.kimia.app.data.ChatMessage
import ir.kimia.app.data.ChatMode
import ir.kimia.app.data.ConversationStore
import ir.kimia.app.data.DeviceInfo
import ir.kimia.app.data.ModelStore
import ir.kimia.app.data.OnlineClient
import ir.kimia.app.data.OnlineConfig
import ir.kimia.app.data.OnlineSettings
import ir.kimia.app.data.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

sealed interface Status {
    data object NoModel : Status
    data class Loading(val what: String) : Status
    data object Ready : Status
    data object Generating : Status
    data class Error(val message: String) : Status
}

enum class Route { ONLINE, LOCAL }

data class DownloadState(val received: Long = 0, val total: Long = -1, val error: String? = null)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    val device: DeviceInfo = DeviceInfo.read(app)
    val modelStore = ModelStore(app)
    private val conversationStore = ConversationStore(app)
    private val prefs = app.getSharedPreferences("kimia", Context.MODE_PRIVATE)

    /** Null when the native engine could not be loaded on this phone. */
    private val engine: InferenceEngine? = try {
        AiChat.getInferenceEngine(app)
    } catch (t: Throwable) {
        null
    }

    private val _messages = MutableStateFlow(conversationStore.load())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _status = MutableStateFlow<Status>(Status.NoModel)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _currentModel = MutableStateFlow<String?>(null)
    val currentModel: StateFlow<String?> = _currentModel.asStateFlow()

    private val _installed = MutableStateFlow(modelStore.installed().map { it.name })
    val installed: StateFlow<List<String>> = _installed.asStateFlow()

    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** True when the visible conversation was loaded from disk, so the model has no memory of it. */
    private val _historyRestored = MutableStateFlow(_messages.value.isNotEmpty())
    val historyRestored: StateFlow<Boolean> = _historyRestored.asStateFlow()

    private val onlineSettings = OnlineSettings(app)
    private val onlineClient = OnlineClient()

    private val _config = MutableStateFlow(onlineSettings.loadConfig())
    val config: StateFlow<OnlineConfig> = _config.asStateFlow()

    private val _mode = MutableStateFlow(onlineSettings.loadMode())
    val mode: StateFlow<ChatMode> = _mode.asStateFlow()

    private val _connected = MutableStateFlow(true)

    /** True while any answer (online or local) is being produced. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _chatNotice = MutableStateFlow<String?>(null)
    val chatNotice: StateFlow<String?> = _chatNotice.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    /** Where the next message would go; null means chat is not possible yet. */
    val route: StateFlow<Route?> = combine(_mode, _config, _connected, _status) { m, c, conn, st ->
        pickRoute(m, c.configured, conn, st is Status.Ready)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var nextId = _messages.value.size.toLong()
    private var genJob: Job? = null
    private var loadAttempted = false
    private var localDirty = false
    private val downloadJobs = mutableMapOf<String, Job>()

    init {
        watchConnectivity(app)
        if (engine == null) {
            _status.value = Status.Error("موتور هوش مصنوعی روی این گوشی بارگذاری نشد (فقط گوشی‌های ۶۴بیتی ARM پشتیبانی می‌شوند)")
        } else {
            val last = prefs.getString(KEY_MODEL, null)
            if (last != null && modelStore.fileFor(last).exists()) useModel(last)
        }
    }

    // ---------------------------------------------------------------- model loading

    fun useModel(fileName: String) {
        val eng = engine ?: return
        if (_status.value is Status.Loading || _status.value is Status.Generating || _busy.value) return
        val file = modelStore.fileFor(fileName)
        if (!file.exists()) return

        viewModelScope.launch {
            _status.value = Status.Loading("در حال بارگذاری مدل…")
            try {
                loadIntoEngine(eng, file)
                localDirty = false
                _currentModel.value = fileName
                prefs.edit().putString(KEY_MODEL, fileName).apply()
                _status.value = Status.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _currentModel.value = null
                _status.value = Status.Error(friendly(e))
            }
        }
    }

    private suspend fun loadIntoEngine(eng: InferenceEngine, file: File) = withContext(Dispatchers.Default) {
        val st = eng.state.first {
            it !is InferenceEngine.State.Uninitialized && it !is InferenceEngine.State.Initializing
        }
        if (st is InferenceEngine.State.Error && !loadAttempted) {
            throw IllegalStateException(
                "موتور هوش مصنوعی راه‌اندازی نشد: ${st.exception.message ?: "خطای ناشناخته"}"
            )
        }
        loadAttempted = true
        if (st is InferenceEngine.State.ModelReady || st is InferenceEngine.State.Error) {
            eng.cleanUp()
        }
        eng.loadModel(file.absolutePath)
        eng.setSystemPrompt(SYSTEM_PROMPT)
    }

    fun retryLoad() {
        val name = _currentModel.value ?: prefs.getString(KEY_MODEL, null) ?: return
        if (_status.value is Status.Error) useModel(name)
    }

    // ---------------------------------------------------------------- chat

    fun send(text: String) {
        val prompt = text.trim()
        val chosen = route.value
        if (prompt.isEmpty() || _busy.value || chosen == null) return

        val history = buildOnlineHistory(prompt)
        _chatNotice.value = null
        _messages.update {
            it + ChatMessage(nextId++, Role.USER, prompt) + ChatMessage(nextId++, Role.ASSISTANT, "")
        }
        _busy.value = true

        genJob = viewModelScope.launch {
            var failure: Exception? = null
            var useLocal = chosen == Route.LOCAL

            if (!useLocal) {
                var gotToken = false
                try {
                    onlineClient.stream(_config.value, history).collect {
                        gotToken = true
                        appendToLast(it)
                    }
                } catch (e: CancellationException) {
                    // the user pressed stop
                } catch (e: Exception) {
                    failure = e
                }
                val err = failure
                if (err != null) {
                    if (!gotToken && _mode.value == ChatMode.AUTO && _status.value is Status.Ready) {
                        _chatNotice.value = "آنلاین در دسترس نبود (${friendlyOnline(err)}). با مدل آفلاین پاسخ داده شد."
                        failure = null
                        useLocal = true
                    } else {
                        _chatNotice.value = friendlyOnline(err)
                    }
                }
            }

            if (useLocal && isActive) generateLocal(prompt)

            withContext(NonCancellable) {
                dropEmptyAssistant()
                conversationStore.save(_messages.value)
                _busy.value = false
            }
        }
    }

    /** Runs the prompt on the on-device model. Errors are reported through [status]. */
    private suspend fun generateLocal(prompt: String) {
        val eng = engine
        if (eng == null) {
            _chatNotice.value = "موتور آفلاین روی این گوشی در دسترس نیست"
            return
        }
        var failure: Exception? = null
        try {
            if (localDirty) {
                _status.value = Status.Loading("در حال آماده‌سازی گفتگوی جدید…")
                _currentModel.value?.let { loadIntoEngine(eng, modelStore.fileFor(it)) }
                localDirty = false
            }
            _status.value = Status.Generating
            eng.sendUserPrompt(prompt).collect { appendToLast(it) }
        } catch (e: CancellationException) {
            // the user pressed stop
        } catch (e: Exception) {
            failure = e
        }
        withContext(NonCancellable) {
            // let the native side settle before accepting another message
            withTimeoutOrNull(5_000) {
                eng.state.first {
                    it is InferenceEngine.State.ModelReady || it is InferenceEngine.State.Error
                }
            }
            _status.value = if (failure != null || eng.state.value is InferenceEngine.State.Error) {
                Status.Error(friendly(failure ?: IllegalStateException("خطا در تولید پاسخ")))
            } else Status.Ready
        }
    }

    fun stop() {
        genJob?.cancel()
    }

    fun newChat() {
        if (_busy.value || _status.value is Status.Loading) return
        _messages.value = emptyList()
        _historyRestored.value = false
        _chatNotice.value = null
        conversationStore.clear()
        // the on-device model keeps its conversation in native memory; it is reset lazily
        // the next time it is used, so starting a new chat is instant in online mode
        if (_currentModel.value != null) localDirty = true
    }

    fun dismissChatNotice() {
        _chatNotice.value = null
    }

    private fun appendToLast(token: String) {
        _messages.update { list ->
            if (list.isEmpty()) list else {
                val last = list.last()
                list.dropLast(1) + last.copy(text = last.text + token)
            }
        }
    }

    private fun dropEmptyAssistant() {
        _messages.update { list ->
            val last = list.lastOrNull()
            if (last != null && last.role == Role.ASSISTANT && last.text.isEmpty()) list.dropLast(1) else list
        }
    }

    /** The conversation so far plus the new prompt, trimmed to a size cloud models accept easily. */
    private fun buildOnlineHistory(prompt: String): List<Pair<String, String>> {
        val past = _messages.value
            .filter { it.text.isNotEmpty() }
            .map { (if (it.role == Role.USER) "user" else "assistant") to it.text }
            .takeLast(24)
        val all = past + ("user" to prompt)
        var budget = 12_000
        val kept = ArrayDeque<Pair<String, String>>()
        for (m in all.asReversed()) {
            if (budget - m.second.length < 0 && kept.isNotEmpty()) break
            budget -= m.second.length
            kept.addFirst(m)
        }
        return listOf("system" to SYSTEM_PROMPT) + kept
    }

    // ---------------------------------------------------------------- online settings

    fun setMode(m: ChatMode) {
        _mode.value = m
        onlineSettings.saveMode(m)
    }

    fun saveConfig(c: OnlineConfig) {
        val clean = c.copy(baseUrl = c.baseUrl.trim(), model = c.model.trim(), apiKey = c.apiKey.trim())
        onlineSettings.saveConfig(clean)
        _config.value = clean
    }

    fun testOnline(c: OnlineConfig) {
        val clean = c.copy(baseUrl = c.baseUrl.trim(), model = c.model.trim(), apiKey = c.apiKey.trim())
        if (!clean.configured) {
            _testResult.value = "آدرس، مدل و کلید را کامل وارد کن."
            return
        }
        _testResult.value = "در حال آزمایش…"
        viewModelScope.launch {
            _testResult.value = try {
                onlineClient.stream(
                    clean,
                    listOf("user" to "فقط یک کلمه بگو: سلام"),
                ).first()
                "✓ اتصال موفق بود. حالا «ذخیره» را بزن."
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoSuchElementException) {
                "سرویس پاسخ خالی داد؛ نام مدل را بررسی کن."
            } catch (e: Exception) {
                "✗ " + friendlyOnline(e)
            }
        }
    }

    private fun watchConnectivity(app: Application) {
        try {
            val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
            _connected.value = cm.activeNetwork != null
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _connected.value = true
                }

                override fun onLost(network: Network) {
                    _connected.value = false
                }
            })
        } catch (e: Exception) {
            _connected.value = true
        }
    }

    private fun friendlyOnline(e: Exception): String = when (e) {
        is UnknownHostException, is ConnectException, is SocketTimeoutException, is SSLException ->
            "اتصال به سرویس برقرار نشد؛ اینترنت یا فیلترشکن را بررسی کن"
        is IOException -> e.message ?: "خطای شبکه"
        else -> e.message ?: "خطای ناشناخته"
    }

    // ---------------------------------------------------------------- model files

    fun startDownload(url: String, fileName: String, approxSizeMb: Int = 0) {
        if (downloadJobs[fileName]?.isActive == true) return
        if (approxSizeMb > 0 && device.freeStorageMb < approxSizeMb * 1.1) {
            setDownload(fileName, DownloadState(error = "فضای خالی کافی نیست (حدود $approxSizeMb مگابایت لازم است)"))
            return
        }
        setDownload(fileName, DownloadState())
        downloadJobs[fileName] = viewModelScope.launch {
            try {
                modelStore.download(url, fileName) { r, t -> setDownload(fileName, DownloadState(r, t)) }
                _downloads.update { it - fileName }
                refreshInstalled()
            } catch (e: CancellationException) {
                _downloads.update { it - fileName }
                throw e
            } catch (e: Exception) {
                setDownload(
                    fileName,
                    DownloadState(
                        error = (e.message ?: "خطای شبکه") +
                            "\nاگر دانلود باز نمی‌شود، فایل GGUF را از راه دیگری بگیر و با «افزودن از فایل» وارد کن.",
                    ),
                )
            }
        }
    }

    fun cancelDownload(fileName: String) {
        downloadJobs[fileName]?.cancel()
        _downloads.update { it - fileName }
    }

    fun dismissDownloadError(fileName: String) {
        _downloads.update { it - fileName }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            _notice.value = "در حال کپی کردن مدل…"
            try {
                val f = modelStore.importFrom(uri)
                refreshInstalled()
                _notice.value = "مدل «${f.name}» اضافه شد"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _notice.value = e.message ?: "افزودن مدل ناموفق بود"
            }
        }
    }

    fun deleteModel(fileName: String) {
        val s = _status.value
        if (s is Status.Loading || s is Status.Generating || _busy.value) return
        viewModelScope.launch {
            if (_currentModel.value == fileName) {
                val eng = engine
                if (eng != null) withContext(Dispatchers.Default) {
                    val st = eng.state.value
                    if (st is InferenceEngine.State.ModelReady || st is InferenceEngine.State.Error) eng.cleanUp()
                }
                _currentModel.value = null
                prefs.edit().remove(KEY_MODEL).apply()
                _status.value = Status.NoModel
            }
            withContext(Dispatchers.IO) { modelStore.delete(fileName) }
            refreshInstalled()
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    private fun refreshInstalled() {
        _installed.value = modelStore.installed().map { it.name }
    }

    private fun setDownload(fileName: String, state: DownloadState) {
        _downloads.update { it + (fileName to state) }
    }

    private fun friendly(e: Exception): String = when (e) {
        is UnsupportedArchitectureException ->
            "این مدل روی این گوشی بارگذاری نشد (مدل پشتیبانی‌نشده یا حافظه‌ی کم). مدل کوچک‌تری امتحان کن."
        else -> e.message ?: "خطای ناشناخته"
    }

    override fun onCleared() {
        super.onCleared()
        // Do not destroy the native engine here: it is a process-wide singleton.
    }

    companion object {
        private const val KEY_MODEL = "model_file"

        fun pickRoute(mode: ChatMode, configured: Boolean, connected: Boolean, localReady: Boolean): Route? =
            when (mode) {
                ChatMode.ONLINE -> if (configured) Route.ONLINE else null
                ChatMode.OFFLINE -> if (localReady) Route.LOCAL else null
                ChatMode.AUTO -> when {
                    configured && connected -> Route.ONLINE
                    localReady -> Route.LOCAL
                    else -> null
                }
            }

        const val SYSTEM_PROMPT =
            "تو «کیمیا» هستی، دستیار هوش مصنوعی شخصی کاربر. " +
                "به فارسی روان و ساده پاسخ بده، مگر اینکه کاربر زبان دیگری بخواهد. " +
                "کوتاه، دقیق و صادق باش. اگر جواب را نمی‌دانی یا مطمئن نیستی، همین را بگو و چیزی از خودت نساز."
    }
}
