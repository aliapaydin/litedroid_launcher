package dev.litedroid.launcher.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import androidx.annotation.RawRes
import androidx.compose.runtime.staticCompositionLocalOf
import dev.litedroid.launcher.R

enum class Sfx(@RawRes val res: Int, val gain: Float) {
    NAV(R.raw.sfx_nav, 0.55f),
    SELECT(R.raw.sfx_select, 0.9f),
    BACK(R.raw.sfx_back, 0.8f),
    BUMP(R.raw.sfx_bump, 0.9f),
    OPEN(R.raw.sfx_open, 0.7f),
}

/** Arayüz ses efektleri. Ayarlardan kapatılabilir, ses seviyesi seçilebilir. */
class SoundFx(context: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids: Map<Sfx, Int> = Sfx.entries.associateWith { pool.load(context, it.res, 1) }
    private val lastPlayed = HashMap<Sfx, Long>()

    var enabled = true
    var volume = 0.5f

    fun play(sfx: Sfx) {
        if (!enabled) return
        // Tuşa basılı tutup hızla gezerken sesler üst üste binmesin
        val minGap = when (sfx) {
            Sfx.NAV -> 45L
            Sfx.BUMP -> 250L
            else -> 0L
        }
        if (minGap > 0) {
            val now = SystemClock.uptimeMillis()
            if (now - (lastPlayed[sfx] ?: 0L) < minGap) return
            lastPlayed[sfx] = now
        }
        val id = ids[sfx] ?: return
        val v = (volume * sfx.gain).coerceIn(0f, 1f)
        pool.play(id, v, v, 1, 0, 1f)
    }

    fun release() = pool.release()
}

val LocalSounds = staticCompositionLocalOf<SoundFx?> { null }
