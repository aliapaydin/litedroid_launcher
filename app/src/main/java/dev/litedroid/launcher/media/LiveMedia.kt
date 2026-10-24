package dev.litedroid.launcher.media

import android.content.Context
import android.graphics.Bitmap
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
) {
    /** Şu anki konum: çalıyorsa geçen süre eklenir. */
    fun positionNow(): Long {
        if (!playing) return positionMs
        val p = positionMs + ((SystemClock.elapsedRealtime() - measuredAt) * speed).toLong()
        return if (durationMs > 0) p.coerceAtMost(durationMs) else p
    }
}

object LiveMediaHub {
    private val _current = MutableStateFlow<LiveMedia?>(null)
    val current: StateFlow<LiveMedia?> = _current

    private val byPackage = HashMap<String, LiveMedia>()
    private val controllers = HashMap<String, MediaController>()

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
    private var lastArtKey: String? = null
    private var lastArt: ImageBitmap? = null

    fun update(context: Context, controller: MediaController) {
        val pkg = controller.packageName ?: return
        if (pkg == context.packageName) return
        val meta = controller.metadata
        val state = controller.playbackState
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val label = runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
        val playing = state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING
        synchronized(this) {
            if (state == null || state.state == PlaybackState.STATE_STOPPED || state.state == PlaybackState.STATE_NONE) {
                byPackage.remove(pkg)
                controllers.remove(pkg)
            } else {
                controllers[pkg] = controller
                val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                    ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM)
                byPackage[pkg] = LiveMedia(
                    packageName = pkg,
                    appLabel = label,
                    title = title?.takeIf { it.isNotBlank() } ?: label,
                    artist = artist?.takeIf { it.isNotBlank() && it != title },
                    art = artFor(pkg, title, meta),
                    positionMs = state.position.coerceAtLeast(0),
                    durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 } ?: 0L,
                    playing = playing,
                    speed = if (state.playbackSpeed > 0f) state.playbackSpeed else 1f,
                    measuredAt = if (state.lastPositionUpdateTime > 0) state.lastPositionUpdateTime else SystemClock.elapsedRealtime(),
                )
            }
            // Çalanı öne al; hiçbiri çalmıyorsa en son duraklatılanı göster
            _current.value = byPackage.values.firstOrNull { it.playing } ?: byPackage[pkg] ?: byPackage.values.firstOrNull()
        }
    }

    fun forget(pkg: String) {
        synchronized(this) {
            byPackage.remove(pkg)
            controllers.remove(pkg)
            _current.value = byPackage.values.firstOrNull { it.playing } ?: byPackage.values.firstOrNull()
        }
    }

    /** Kapak küçültülerek bir kez dönüştürülür; aynı parça için tekrar dönüştürülmez. */
    private fun artFor(pkg: String, title: String?, meta: MediaMetadata?): ImageBitmap? {
        meta ?: return null
        val key = "$pkg|$title"
        if (key == lastArtKey && lastArt != null) return lastArt
        val bmp = meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        val scaled = runCatching {
            val max = 320
            if (bmp.width > max || bmp.height > max) {
                val f = max / maxOf(bmp.width, bmp.height).toFloat()
                Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt().coerceAtLeast(1), (bmp.height * f).toInt().coerceAtLeast(1), true)
            } else bmp.copy(Bitmap.Config.ARGB_8888, false)
        }.getOrNull() ?: return null
        lastArtKey = key
        lastArt = scaled.asImageBitmap()
        return lastArt
    }
}
