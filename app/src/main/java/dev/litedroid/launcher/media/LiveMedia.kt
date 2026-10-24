package dev.litedroid.launcher.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Şu an çalan şey (müzik dahil): ekran koruyucudaki "Şimdi çalıyor" kartı için. */
data class LiveMedia(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String?,
    val art: ImageBitmap?,
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    val speed: Float,
    /** [positionMs]'in ölçüldüğü an (SystemClock.elapsedRealtime). */
    val measuredAt: Long,
    /** Bu kaydın en son güncellendiği an: birden fazla uygulama çalarken en yenisi öne alınır. */
    val updatedAt: Long = SystemClock.elapsedRealtime(),
    /** Kapak önbelleği anahtarı (uygulama + parça). */
    val artKey: String = "",
) {
    /** Şu anki konum: çalıyorsa geçen süre eklenir. */
    fun positionNow(): Long {
        if (!playing) return positionMs
        val p = positionMs + ((SystemClock.elapsedRealtime() - measuredAt) * speed).toLong()
        return if (durationMs > 0) p.coerceAtMost(durationMs) else p
    }
}

/**
 * Şu an çalanları uygulama başına tutar. Spotify, YouTube Music, Apple Music, TIDAL, Qobuz, Deezer, fizy…
 * hangi uygulama olursa olsun Android medya oturumu açan her şey burada görünür.
 *
 * Uygulamalar bilgiyi farklı yollarla verir; hepsi denenir:
 * - Başlık: oturum başlığı → görünen başlık → medya bildiriminin başlığı → uygulama adı
 * - Sanatçı: sanatçı → albüm sanatçısı → alt başlık → bildirim metni → albüm
 * - Kapak: oturumdaki bitmap → kapak adresi (content:// ya da https://, arka planda yüklenir) → bildirimin büyük simgesi
 */
object LiveMediaHub {
    private val _current = MutableStateFlow<LiveMedia?>(null)
    val current: StateFlow<LiveMedia?> = _current

    private val byPackage = HashMap<String, LiveMedia>()
    private val controllers = HashMap<String, MediaController>()

    /** Medya bildiriminden gelen yedek bilgiler (bazı uygulamalar oturuma başlık/kapak koymuyor). */
    private data class Hint(val title: String?, val text: String?, val art: ImageBitmap?)
    private val hints = HashMap<String, Hint>()

    /** Hızlı paneldeki oynat/duraklat, ileri, geri düğmeleri için. */
    fun playPause() {
        val m = _current.value ?: return
        val c = synchronized(this) { controllers[m.packageName] } ?: return
        runCatching { if (m.playing) c.transportControls.pause() else c.transportControls.play() }
    }

    fun next() {
        val m = _current.value ?: return
        synchronized(this) { controllers[m.packageName] }?.let { runCatching { it.transportControls.skipToNext() } }
    }

    fun previous() {
        val m = _current.value ?: return
        synchronized(this) { controllers[m.packageName] }?.let { runCatching { it.transportControls.skipToPrevious() } }
    }

    private val artCache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 12
    }
    private val artLoading = HashSet<String>()
    private val artExecutor = Executors.newSingleThreadExecutor()

    /** Medya bildirimindeki başlık, metin ve büyük simge (oturumda eksik olanları tamamlamak için). */
    fun hint(context: Context, pkg: String, title: String?, text: String?, art: Bitmap?) {
        if (pkg == context.packageName) return
        synchronized(this) {
            val old = hints[pkg]
            val cleanTitle = title?.takeIf { it.isNotBlank() }
            val img = if (old != null && old.title == cleanTitle && art == null) old.art else art?.let { scaled(it) }?.asImageBitmap()
            hints[pkg] = Hint(cleanTitle, text?.takeIf { it.isNotBlank() }, img)
            controllers[pkg]?.let { c -> runCatching { update(context, c) } }
        }
    }

    fun update(context: Context, controller: MediaController) {
        val pkg = controller.packageName ?: return
        if (pkg == context.packageName) return
        val meta = controller.metadata
        val state = controller.playbackState
        val label = runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
        val playing = state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING
        synchronized(this) {
            if (state == null || state.state == PlaybackState.STATE_STOPPED || state.state == PlaybackState.STATE_NONE ||
                state.state == PlaybackState.STATE_ERROR
            ) {
                byPackage.remove(pkg)
                controllers.remove(pkg)
            } else {
                controllers[pkg] = controller
                val hint = hints[pkg]
                val title = meta.text(MediaMetadata.METADATA_KEY_TITLE)
                    ?: meta.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                    ?: hint?.title?.takeIf { it != label }
                val artist = meta.text(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: meta.text(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: meta.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                    ?: hint?.text
                    ?: meta.text(MediaMetadata.METADATA_KEY_ALBUM)
                val artKey = "$pkg|$title|$artist"
                val position = state.position.takeIf { it >= 0 } ?: byPackage[pkg]?.positionNow() ?: 0L
                byPackage[pkg] = LiveMedia(
                    packageName = pkg,
                    appLabel = label,
                    title = title ?: label,
                    artist = artist?.takeIf { it != title && it != label },
                    art = artFor(context, pkg, artKey, meta, hint),
                    positionMs = position,
                    durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 } ?: 0L,
                    playing = playing,
                    speed = if (state.playbackSpeed > 0f) state.playbackSpeed else 1f,
                    measuredAt = if (state.lastPositionUpdateTime > 0) state.lastPositionUpdateTime else SystemClock.elapsedRealtime(),
                    updatedAt = if (byPackage[pkg]?.playing == playing && byPackage[pkg]?.artKey == artKey) {
                        byPackage[pkg]?.updatedAt ?: SystemClock.elapsedRealtime()
                    } else SystemClock.elapsedRealtime(),
                    artKey = artKey,
                )
            }
            publish()
        }
    }

    /** Aktif oturum listesinde artık olmayan uygulamaları kaldırır (kapatılan oynatıcılar ekranda kalmasın). */
    fun retain(packages: Set<String>) {
        synchronized(this) {
            val gone = byPackage.keys.filter { it !in packages }
            if (gone.isEmpty()) return
            gone.forEach {
                byPackage.remove(it)
                controllers.remove(it)
                hints.remove(it)
            }
            publish()
        }
    }

    fun forget(pkg: String) {
        synchronized(this) {
            byPackage.remove(pkg)
            controllers.remove(pkg)
            hints.remove(pkg)
            publish()
        }
    }

    /** Çalanlar arasından en son değişeni öne al; hiçbiri çalmıyorsa en son duraklatılanı göster. */
    private fun publish() {
        _current.value = byPackage.values.filter { it.playing }.maxByOrNull { it.updatedAt }
            ?: byPackage.values.maxByOrNull { it.updatedAt }
    }

    private fun MediaMetadata?.text(key: String): String? =
        this?.getString(key)?.trim()?.takeIf { it.isNotBlank() }

    private fun artFor(context: Context, pkg: String, key: String, meta: MediaMetadata?, hint: Hint?): ImageBitmap? {
        artCache[key]?.let { return it }
        val bmp = meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bmp != null) {
            val img = scaled(bmp)?.asImageBitmap() ?: return null
            artCache[key] = img
            return img
        }
        val uri = meta.text(MediaMetadata.METADATA_KEY_ART_URI)
            ?: meta.text(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: meta.text(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        if (uri != null && artLoading.add(key)) {
            val app = context.applicationContext
            artExecutor.execute { loadArt(app, pkg, key, uri) }
        }
        // Adres yüklenene kadar (ya da hiç yoksa) bildirimin büyük simgesi
        return hint?.art
    }

    private fun loadArt(context: Context, pkg: String, key: String, uri: String) {
        val bmp = runCatching { decode(context, uri) }.getOrNull()
        val img = bmp?.let { scaled(it) }?.asImageBitmap()
        synchronized(this) {
            artLoading.remove(key)
            img ?: return
            artCache[key] = img
            val m = byPackage[pkg] ?: return
            if (m.artKey == key) {
                byPackage[pkg] = m.copy(art = img)
                publish()
            }
        }
    }

    private fun decode(context: Context, uri: String): Bitmap? {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return if (uri.startsWith("http://") || uri.startsWith("https://")) {
            val conn = URL(uri).openConnection() as HttpURLConnection
            conn.connectTimeout = 6_000
            conn.readTimeout = 8_000
            try {
                conn.inputStream.use { BitmapFactory.decodeStream(it, null, opts) }
            } finally {
                conn.disconnect()
            }
        } else {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    }

    /** Kapak 320 piksele küçültülür. */
    private fun scaled(bmp: Bitmap): Bitmap? = runCatching {
        val max = 320
        if (bmp.width > max || bmp.height > max) {
            val f = max / maxOf(bmp.width, bmp.height).toFloat()
            Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt().coerceAtLeast(1), (bmp.height * f).toInt().coerceAtLeast(1), true)
        } else bmp.copy(Bitmap.Config.ARGB_8888, false)
    }.getOrNull()
}
