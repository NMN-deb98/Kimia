package ir.kimia.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class Role { USER, ASSISTANT }

data class ChatMessage(val id: Long, val role: Role, val text: String)

/** Keeps the current conversation on the phone as a small JSON file. */
class ConversationStore(context: Context) {
    private val file = File(context.filesDir, "conversation.json")

    fun load(): List<ChatMessage> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                ChatMessage(
                    id = i.toLong(),
                    role = if (o.getString("r") == "u") Role.USER else Role.ASSISTANT,
                    text = o.getString("t"),
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(messages: List<ChatMessage>) {
        val arr = JSONArray()
        messages.filter { it.text.isNotEmpty() }.forEach {
            arr.put(JSONObject().put("r", if (it.role == Role.USER) "u" else "a").put("t", it.text))
        }
        file.writeText(arr.toString())
    }

    fun clear() {
        file.delete()
    }
}
