package ir.kimia.app

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.UnsupportedArchitectureException
import ir.kimia.app.data.ChatMessage
import ir.kimia.app.data.ConversationStore
import ir.kimia.app.data.DeviceInfo
import ir.kimia.app.data.ModelStore
import ir.kimia.app.data.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface Status {
    data object NoModel : Status
    data class Loading(val what: String) : Status
    data object Ready : Status
    data object Generating : Status
    data class Error(val message: String) : Status
}

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

    private var nextId = _messages.value.size.toLong()
    private var genJob: Job? = null
    private var loadAttempted = false
    private val downloadJobs = mutableMapOf<String, Job>()

    init {
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
        if (_status.value is Status.Loading || _status.value is Status.Generating) return
        val file = modelStore.fileFor(fileName)
        if (!file.exists()) return

        viewModelScope.launch {
            _status.value = Status.Loading("در حال بارگذاری مدل…")
            try {
                withContext(Dispatchers.Default) {
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

    fun retryLoad() {
        val name = _currentModel.value ?: prefs.getString(KEY_MODEL, null) ?: return
        if (_status.value is Status.Error) useModel(name)
    }

    // ---------------------------------------------------------------- chat

    fun send(text: String) {
        val eng = engine ?: return
        val prompt = text.trim()
        if (prompt.isEmpty() || _status.value != Status.Ready) return

        _messages.update {
            it + ChatMessage(nextId++, Role.USER, prompt) + ChatMessage(nextId++, Role.ASSISTANT, "")
        }
        _status.value = Status.Generating

        genJob = viewModelScope.launch {
            var failure: Exception? = null
            try {
                eng.sendUserPrompt(prompt).collect { token -> appendToLast(token) }
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
                conversationStore.save(_messages.value)
            }
        }
    }

    fun stop() {
        genJob?.cancel()
    }

    fun newChat() {
        val s = _status.value
        if (s is Status.Loading || s is Status.Generating) return
        _messages.value = emptyList()
        _historyRestored.value = false
        conversationStore.clear()
        // the model keeps its conversation in native memory, so reload it to start clean
        _currentModel.value?.let { useModel(it) }
    }

    private fun appendToLast(token: String) {
        _messages.update { list ->
            if (list.isEmpty()) list else {
                val last = list.last()
                list.dropLast(1) + last.copy(text = last.text + token)
            }
        }
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
        if (s is Status.Loading || s is Status.Generating) return
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

        const val SYSTEM_PROMPT =
            "تو «کیمیا» هستی، یک دستیار هوش مصنوعی شخصی که روی خود گوشی کاربر و بدون اینترنت اجرا می‌شود. " +
                "به فارسی روان و ساده پاسخ بده، مگر اینکه کاربر زبان دیگری بخواهد. " +
                "کوتاه، دقیق و صادق باش. اگر جواب را نمی‌دانی یا مطمئن نیستی، همین را بگو و چیزی از خودت نساز."
    }
}
