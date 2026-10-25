package dev.litedroid.launcher.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** GitHub'da yayınlanmış bir sürüm. */
data class AppRelease(
    val version: String,
    val title: String,
    /** Madde madde yenilikler (release açıklamasından). */
    val notes: List<String>,
    val apkUrl: String,
    val apkSize: Long,
    val pageUrl: String,
)

/** Güncellemenin o anki durumu (bildirim, ayarlar ve güncelleme penceresi bunu çizer). */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    data class Installing(val release: AppRelease) : UpdateState
    /** İlk güncellemede Android onay ister: kurulum ekranı açıldı ya da açılmayı bekliyor. */
    data class NeedsConfirm(val release: AppRelease, val confirm: Intent) : UpdateState
    data class Failed(val message: String, val release: AppRelease?) : UpdateState
}

/**
 * Kendini güncelleme. Yeni sürümler GitHub Releases'te APK olarak yayınlanır (tools/release.sh).
 * LiteDroid en son sürümü kontrol eder, APK'yı indirir ve Android'in paket yükleyicisiyle kurar.
 *
 * İlk kendi kendine güncellemede Android bir kez onay ister (LiteDroid adb ile kurulduğu için).
 * Bundan sonra kurulumu yapan LiteDroid olduğundan, Android 12 ve sonrasında onaysız kurulur.
 * APK, ilk kurulumla aynı anahtarla imzalanmış olmalı (aynı bilgisayarda derlenmiş).
 */
object Updater {
    private const val TAG = "LiteDroidUpdate"
    const val REPO = "aliapaydin/litedroid_launcher"
    private const val ACTION_STATUS = "dev.litedroid.launcher.UPDATE_STATUS"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    /** Kurulum ekranını kullanıcı mı istedi (otomatik kurulumda onay ekranı ekrana atlamasın). */
    @Volatile
    private var userInitiated = false

    fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    /** GitHub'daki en son sürüm; yeniyse [UpdateState.Available]. */
    suspend fun check(context: Context, quiet: Boolean = true): AppRelease? = withContext(Dispatchers.IO) {
        val busy = _state.value
        if (busy is UpdateState.Downloading || busy is UpdateState.Installing) return@withContext null
        if (!quiet) _state.value = UpdateState.Checking
        // Ağ hatası: exception; yayınlanmış sürüm yok (404) ya da APK yok: null
        val result = runCatching { fetchLatest() }.onFailure { Log.w(TAG, "check failed: ${it.message}") }
        val release = result.getOrNull()
        val current = currentVersion(context)
        when {
            result.isSuccess && release == null -> {
                Log.i(TAG, "no published release yet")
                _state.value = if (quiet) UpdateState.Idle else UpdateState.UpToDate
                null
            }
            release == null -> {
                if (!quiet) _state.value = UpdateState.Failed("Sürüm bilgisi alınamadı. İnternet bağlantısını kontrol et.", null)
                else if (_state.value == UpdateState.Checking) _state.value = UpdateState.Idle
                null
            }
            isNewer(release.version, current) -> {
                // Zaten onay bekleyen ya da hata almış aynı sürüm varsa durumu bozma
                val s = _state.value
                val same = (s as? UpdateState.NeedsConfirm)?.release?.version == release.version
                if (!same) _state.value = UpdateState.Available(release)
                release
            }
            else -> {
                _state.value = if (quiet && _state.value !is UpdateState.Checking) UpdateState.Idle else UpdateState.UpToDate
                null
            }
        }
    }

    private fun fetchLatest(): AppRelease? {
        val conn = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "LiteDroid-Launcher")
        }
        try {
            if (conn.responseCode == 404) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(body)
            if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
            val version = o.optString("tag_name").removePrefix("v").removePrefix("V").trim()
            val assets = o.optJSONArray("assets") ?: return null
            var url = ""
            var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    url = a.optString("browser_download_url")
                    size = a.optLong("size")
                    break
                }
            }
            if (version.isBlank() || url.isBlank()) return null
            val (title, notes) = parseNotes(o.optString("body"), o.optString("name"))
            return AppRelease(version, title, notes, url, size, o.optString("html_url"))
        } finally {
            conn.disconnect()
        }
    }

    /** Açıklamadaki "## Başlık" satırı başlık, "- " ile başlayan satırlar madde olur. */
    private fun parseNotes(body: String, name: String): Pair<String, List<String>> {
        val lines = body.lines().map { it.trim() }
        val title = lines.firstOrNull { it.startsWith("## ") }?.removePrefix("## ")?.trim()
            ?: name.takeIf { it.isNotBlank() } ?: "LiteDroid"
        val items = lines.filter { it.startsWith("- ") || it.startsWith("* ") }
            .map { it.drop(2).replace("**", "").replace("`", "").trim() }
            .filter { it.isNotBlank() }
        return title to items.ifEmpty { lines.filter { it.isNotBlank() && !it.startsWith("#") }.take(6) }
    }

    /** "1.10.0" > "1.9.2" gibi sürüm karşılaştırması. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.split('.', '-', '+').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    // ------------------------------------------------------------------
    // Erteleme ("1 saat sonra hatırlat")
    // ------------------------------------------------------------------

    private fun prefs(context: Context) = context.getSharedPreferences("litedroid_update", Context.MODE_PRIVATE)

    /** Bu sürüm için soru şu ana kadar ertelendi mi. */
    fun isSnoozed(context: Context, version: String): Boolean {
        val p = prefs(context)
        return p.getString("snooze_version", null) == version && System.currentTimeMillis() < p.getLong("snooze_until", 0L)
    }

    fun snooze(context: Context, version: String, hours: Int) {
        prefs(context).edit()
            .putString("snooze_version", version)
            .putLong("snooze_until", System.currentTimeMillis() + hours * 3_600_000L)
            .apply()
    }

    fun snoozedUntil(context: Context): Long = prefs(context).getLong("snooze_until", 0L)

    /** Bilinmeyen kaynaktan kurma izni var mı (kendi kendini güncellemek için gerekli). */
    fun canInstall(context: Context): Boolean =
        runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

    /**
     * İndirip kurar. [byUser] false ise (otomatik güncelleme) Android onay isterse ekrana atlamaz,
     * durum [UpdateState.NeedsConfirm] olarak kalır; çan menüsünden onaylanır.
     */
    suspend fun downloadAndInstall(context: Context, release: AppRelease, byUser: Boolean) {
        userInitiated = byUser
        val app = context.applicationContext
        val file = runCatching { download(app, release) }.getOrElse {
            Log.w(TAG, "download failed: ${it.message}")
            _state.value = UpdateState.Failed("İndirme başarısız: ${it.message ?: it.javaClass.simpleName}", release)
            return
        }
        _state.value = UpdateState.Installing(release)
        runCatching { install(app, file) }.onFailure {
            Log.w(TAG, "install failed: ${it.message}")
            _state.value = UpdateState.Failed("Kurulum başlatılamadı: ${it.message ?: it.javaClass.simpleName}", release)
        }
    }

    private suspend fun download(context: Context, release: AppRelease): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "LiteDroid-${release.version}.apk")
        _state.value = UpdateState.Downloading(release, 0f)
        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "LiteDroid-Launcher")
        }
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - lastReport > 256 * 1024) {
                            lastReport = done
                            _state.value = UpdateState.Downloading(release, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (file.length() < 100_000) error("dosya eksik indi")
        file
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // Kurulumu daha önce LiteDroid yaptıysa Android 12+ onay istemez
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, UpdateReceiver::class.java).setAction(ACTION_STATUS)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, id, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    /** Paket yükleyicinin sonucu. */
    internal fun onStatus(context: Context, intent: Intent) {
        val release = when (val s = _state.value) {
            is UpdateState.Installing -> s.release
            is UpdateState.NeedsConfirm -> s.release
            is UpdateState.Available -> s.release
            else -> null
        }
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.i(TAG, "install status $status $message")
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (release != null) _state.value = UpdateState.NeedsConfirm(release, confirm)
                if (userInitiated) runCatching { context.startActivity(confirm) }
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = UpdateState.Idle // süreç zaten yeniden başlayacak
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                _state.value = UpdateState.Failed(
                    "Kurulamadı: APK farklı bir anahtarla imzalanmış. Güncellemeler, ilk kurulumu yaptığın bilgisayarda derlenip yayınlanmalı.",
                    release,
                )
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = release?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            else -> _state.value = UpdateState.Failed("Kurulamadı: ${message ?: "durum $status"}", release)
        }
    }

    /** Bekleyen onay ekranını açar (çan menüsünden). */
    fun confirmPending(context: Context): Boolean {
        val s = _state.value as? UpdateState.NeedsConfirm ?: return false
        return runCatching { context.startActivity(s.confirm) }.isSuccess
    }
}

/** Paket yükleyiciden gelen sonuçları [Updater]'a iletir. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Updater.onStatus(context, intent)
}

/** Güncelleme kurulunca LiteDroid'i yeniden açar ve "yenilikler" gösterilsin diye işaret bırakır. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
