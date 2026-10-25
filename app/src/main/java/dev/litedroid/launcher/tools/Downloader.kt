package dev.litedroid.launcher.tools

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Bir indirme: ilerleme ve durum ekranda canlı güncellenir. */
class DownloadItem(val id: Long, val name: String, val url: String, val mime: String?, val file: File) {
    var total by mutableLongStateOf(-1L)
    var done by mutableLongStateOf(0L)
    /** Bayt/sn (son ölçüm). */
    var speed by mutableLongStateOf(0L)
    var state by mutableStateOf(State.RUNNING)
    var error by mutableStateOf<String?>(null)
    internal var job: Job? = null

    enum class State { RUNNING, DONE, FAILED, CANCELLED }

    val isApk get() = name.endsWith(".apk", ignoreCase = true) || mime == "application/vnd.android.package-archive"
    val progress get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
}

/**
 * Tarayıcının kendi indiricisi. Sayfa bir dosya indirmek isteyince devralır: çerezlerle (oturum
 * gerektiren indirmeler için) tek bağlantıdan indirir, ilerlemeyi gösterir. Biten dosya cihazın
 * İndirilenler klasörüne de kopyalanır. APK'lar tek tuşla kurulur. Tarayıcı kapansa da indirme
 * biter, sonra hiçbir şey çalışmaz.
 */
object Downloader {
    private const val TAG = "LiteDroidDownload"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val items = mutableStateListOf<DownloadItem>()

    fun dir(ctx: Context) = File(ctx.filesDir, "downloads").apply { mkdirs() }

    val active: Int get() = items.count { it.state == DownloadItem.State.RUNNING }

    /** Var olan dosyaları (önceki oturumlardan) listeye ekler. */
    fun loadExisting(ctx: Context) {
        if (items.isNotEmpty()) return
        dir(ctx).listFiles()?.sortedByDescending { it.lastModified() }?.take(30)?.forEach { f ->
            items += DownloadItem(f.lastModified(), f.name, "", guessMime(f.name), f).apply {
                total = f.length()
                done = f.length()
                state = DownloadItem.State.DONE
            }
        }
    }

    fun start(ctx: Context, url: String, userAgent: String?, fileName: String, mime: String?): DownloadItem {
        val app = ctx.applicationContext
        val safe = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "indirilen" }
        var file = File(dir(app), safe)
        var n = 1
        while (file.exists()) {
            val dot = safe.lastIndexOf('.')
            file = File(dir(app), if (dot > 0) "${safe.substring(0, dot)} ($n)${safe.substring(dot)}" else "$safe ($n)")
            n++
        }
        val item = DownloadItem(System.currentTimeMillis(), file.name, url, mime ?: guessMime(file.name), file)
        items.add(0, item)
        item.job = scope.launch {
            try {
                download(item, userAgent)
                item.state = DownloadItem.State.DONE
                publish(app, item)
            } catch (c: kotlinx.coroutines.CancellationException) {
                item.state = DownloadItem.State.CANCELLED
                item.file.delete()
            } catch (t: Throwable) {
                Log.w(TAG, "download failed: ${t.message}")
                item.state = DownloadItem.State.FAILED
                item.error = t.message ?: t.javaClass.simpleName
                item.file.delete()
            }
        }
        return item
    }

    private suspend fun download(item: DownloadItem, userAgent: String?) = withContext(Dispatchers.IO) {
        var url = URL(item.url)
        var conn: HttpURLConnection
        var redirects = 0
        while (true) {
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                userAgent?.let { setRequestProperty("User-Agent", it) }
                CookieManager.getInstance().getCookie(url.toString())?.let { setRequestProperty("Cookie", it) }
            }
            val code = conn.responseCode
            if (code in 300..399 && redirects < 8) {
                val loc = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                url = URL(url, loc)
                redirects++
                continue
            }
            if (code !in 200..299) {
                conn.disconnect()
                error("HTTP $code")
            }
            break
        }
        try {
            item.total = conn.contentLengthLong
            conn.inputStream.use { input ->
                item.file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var lastAt = System.currentTimeMillis()
                    var lastDone = 0L
                    while (true) {
                        ensureActive()
                        val r = input.read(buf)
                        if (r < 0) break
                        out.write(buf, 0, r)
                        item.done += r
                        val now = System.currentTimeMillis()
                        if (now - lastAt >= 500) {
                            item.speed = (item.done - lastDone) * 1000 / (now - lastAt)
                            lastAt = now
                            lastDone = item.done
                        }
                    }
                }
            }
            if (item.total <= 0) item.total = item.done
            item.speed = 0
        } finally {
            conn.disconnect()
        }
    }

    /** Biten dosyayı cihazın İndirilenler klasörüne kopyalar (dosya yöneticileri görsün). */
    private fun publish(ctx: Context, item: DownloadItem) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, item.name)
                put(MediaStore.Downloads.MIME_TYPE, item.mime ?: "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LiteDroid")
            }
            ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                ctx.contentResolver.openOutputStream(uri)?.use { out -> item.file.inputStream().use { it.copyTo(out) } }
            }
        }.onFailure { Log.w(TAG, "publish failed: ${it.message}") }
    }

    fun cancel(item: DownloadItem) {
        item.job?.cancel()
    }

    fun delete(item: DownloadItem) {
        item.job?.cancel()
        item.file.delete()
        items.remove(item)
    }

    /** APK'yı kurar ya da dosyayı uygun uygulamayla açar. Açacak uygulama yoksa false. */
    fun open(ctx: Context, item: DownloadItem): Boolean {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", item.file)
        val mime = if (item.isApk) "application/vnd.android.package-archive" else item.mime ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(intent) }.isSuccess
    }

    private fun guessMime(name: String): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())

    fun size(b: Long): String = when {
        b < 0 -> "?"
        b >= 1L shl 30 -> "%.2f GB".format(b / (1L shl 30).toFloat())
        b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toFloat())
        b >= 1L shl 10 -> "${b / 1024} KB"
        else -> "$b B"
    }
}
