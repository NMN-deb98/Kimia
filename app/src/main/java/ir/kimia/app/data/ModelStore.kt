package ir.kimia.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** Files on disk: installed models, resumable downloads, and imports from the user's storage. */
class ModelStore(private val context: Context) {

    val dir: File = File(context.filesDir, "models").apply { mkdirs() }

    fun fileFor(fileName: String) = File(dir, fileName)

    /** Every completed .gguf in the models folder (partial downloads end in .part and are ignored). */
    fun installed(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".gguf", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun delete(fileName: String): Boolean {
        fileFor("$fileName.part").delete()
        return fileFor(fileName).delete()
    }

    /**
     * Downloads [url] into the models folder as [fileName], resuming a previous partial download.
     * Throws [IOException] with a human-readable message on failure. Cancel the coroutine to stop;
     * the partial file is kept so the next attempt resumes.
     */
    suspend fun download(
        url: String,
        fileName: String,
        onProgress: (received: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val target = fileFor(fileName)
        val part = fileFor("$fileName.part")
        var offset = if (part.exists()) part.length() else 0L

        var conn = open(url, offset)
        var code = conn.responseCode
        if (code == 416 && offset > 0) {
            // server doesn't accept our resume offset: start over
            conn.disconnect()
            part.delete()
            offset = 0L
            conn = open(url, 0)
            code = conn.responseCode
        }
        if (code != 200 && code != 206) {
            conn.disconnect()
            throw IOException("پاسخ سرور: $code")
        }
        if (code == 200 && offset > 0) {
            // server ignored Range: restart from zero
            part.delete()
            offset = 0L
        }

        val total = when (code) {
            206 -> conn.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
            else -> conn.contentLengthLong
        }

        var received = offset
        try {
            conn.inputStream.use { input ->
                java.io.FileOutputStream(part, offset > 0).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var lastReport = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        received += n
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 250) {
                            lastReport = now
                            onProgress(received, total)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            throw IOException(e.message ?: "قطع شدن ارتباط", e)
        } finally {
            conn.disconnect()
        }

        if (total > 0 && part.length() != total) {
            throw IOException("دانلود کامل نشد؛ دوباره امتحان کن تا ادامه پیدا کند")
        }
        if (!looksLikeGguf(part)) {
            part.delete()
            throw IOException("فایل دانلودشده یک مدل GGUF معتبر نیست")
        }
        target.delete()
        if (!part.renameTo(target)) throw IOException("ذخیره‌ی فایل ناموفق بود")
        onProgress(target.length(), target.length())
        target
    }

    /** Copies a .gguf the user picked from their storage into the models folder. */
    suspend fun importFrom(uri: Uri): File = withContext(Dispatchers.IO) {
        val name = displayName(uri)?.takeIf { it.endsWith(".gguf", ignoreCase = true) }
            ?: throw IOException("فایل باید پسوند .gguf داشته باشد")
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val part = fileFor("$safeName.part")
        context.contentResolver.openInputStream(uri)?.use { input ->
            part.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
            }
        } ?: throw IOException("خواندن فایل ممکن نشد")
        if (!looksLikeGguf(part)) {
            part.delete()
            throw IOException("این فایل یک مدل GGUF معتبر نیست")
        }
        val target = fileFor(safeName)
        target.delete()
        if (!part.renameTo(target)) throw IOException("ذخیره‌ی فایل ناموفق بود")
        target
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun looksLikeGguf(f: File): Boolean = try {
        f.inputStream().use { s ->
            val magic = ByteArray(4)
            s.read(magic) == 4 && String(magic, Charsets.ISO_8859_1) == "GGUF"
        }
    } catch (e: IOException) {
        false
    }

    /** Opens the connection, following redirects by hand so the Range header survives them. */
    private fun open(startUrl: String, offset: Long): HttpURLConnection {
        var current = startUrl
        repeat(8) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "KIMIA-Android/0.1")
            if (offset > 0) conn.setRequestProperty("Range", "bytes=$offset-")
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) throw IOException("تغییر مسیر نامعتبر")
                current = URL(URL(current), loc).toString()
            } else {
                return conn
            }
        }
        throw IOException("تعداد تغییر مسیرها بیش از حد است")
    }
}
