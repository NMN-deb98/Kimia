package ir.kimia.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Streams a chat completion from any OpenAI-compatible endpoint ("/chat/completions"). */
class OnlineClient {

    /** [messages] are (role, content) pairs, roles "system" | "user" | "assistant". Emits text pieces as they arrive. */
    fun stream(cfg: OnlineConfig, messages: List<Pair<String, String>>): Flow<String> = flow {
        val url = URL(cfg.baseUrl.trim().trimEnd('/') + "/chat/completions")
        val arr = JSONArray()
        messages.forEach { (role, content) -> arr.put(JSONObject().put("role", role).put("content", content)) }
        val body = JSONObject()
            .put("model", cfg.model.trim())
            .put("stream", true)
            .put("temperature", 0.6)
            .put("messages", arr)

        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "text/event-stream")
            conn.setRequestProperty("Authorization", "Bearer ${cfg.apiKey.trim()}")
            conn.setRequestProperty("User-Agent", "KIMIA-Android/0.2")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                throw IOException(describe(code, err))
            }
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val piece = parse(data)
                    if (!piece.isNullOrEmpty()) emit(piece)
                }
            }
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private fun parse(data: String): String? = try {
        val o = JSONObject(data)
        if (o.has("error")) {
            throw IOException(o.optJSONObject("error")?.optString("message").orEmpty().ifBlank { "خطای سرویس" })
        }
        val delta = o.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
        if (delta == null || delta.isNull("content")) null else delta.optString("content", "")
    } catch (e: JSONException) {
        null
    }

    private fun describe(code: Int, body: String): String {
        val detail = try {
            val j = JSONObject(body)
            (j.optJSONObject("error")?.optString("message") ?: j.optString("message", "")).take(200)
        } catch (e: Exception) {
            ""
        }
        val base = when (code) {
            400 -> "درخواست نامعتبر است؛ نام مدل را بررسی کن (400)"
            401, 403 -> "کلید API نامعتبر است یا دسترسی از این منطقه/شبکه بسته است ($code)"
            404 -> "آدرس یا نام مدل اشتباه است (404)"
            429 -> "سقف رایگان این سرویس پر شده؛ کمی بعد دوباره امتحان کن (429)"
            in 500..599 -> "سرویس موقتاً در دسترس نیست ($code)"
            else -> "خطای سرویس ($code)"
        }
        return if (detail.isNotBlank()) "$base\n$detail" else base
    }
}
