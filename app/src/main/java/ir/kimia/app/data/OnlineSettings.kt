package ir.kimia.app.data

import android.content.Context

enum class ChatMode { AUTO, OFFLINE, ONLINE }

data class OnlineConfig(
    val providerId: String,
    val baseUrl: String,
    val model: String,
    val apiKey: String,
) {
    val configured: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()
}

/** A free-tier chat service that speaks the OpenAI-compatible protocol. */
data class Provider(
    val id: String,
    val title: String,
    val baseUrl: String,
    val model: String,
    val keySite: String,
)

object Providers {
    val all = listOf(
        Provider("groq", "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "console.groq.com/keys"),
        Provider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free", "openrouter.ai/keys"),
        Provider("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", "aistudio.google.com/apikey"),
        Provider("custom", "سفارشی", "", "", ""),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.last()
}

/** Online settings on disk. The API key is stored encrypted (see [SecretBox]). */
class OnlineSettings(context: Context) {
    private val prefs = context.getSharedPreferences("kimia_online", Context.MODE_PRIVATE)

    fun loadConfig(): OnlineConfig {
        val first = Providers.all.first()
        val key = prefs.getString("key", null)?.let { SecretBox.decrypt(it) }.orEmpty()
        return OnlineConfig(
            providerId = prefs.getString("provider", first.id) ?: first.id,
            baseUrl = prefs.getString("base", first.baseUrl) ?: first.baseUrl,
            model = prefs.getString("model", first.model) ?: first.model,
            apiKey = key,
        )
    }

    fun saveConfig(c: OnlineConfig) {
        prefs.edit()
            .putString("provider", c.providerId)
            .putString("base", c.baseUrl.trim())
            .putString("model", c.model.trim())
            .putString("key", if (c.apiKey.isBlank()) null else SecretBox.encrypt(c.apiKey.trim()))
            .apply()
    }

    fun loadMode(): ChatMode =
        runCatching { ChatMode.valueOf(prefs.getString("mode", "AUTO") ?: "AUTO") }.getOrDefault(ChatMode.AUTO)

    fun saveMode(m: ChatMode) {
        prefs.edit().putString("mode", m.name).apply()
    }
}
