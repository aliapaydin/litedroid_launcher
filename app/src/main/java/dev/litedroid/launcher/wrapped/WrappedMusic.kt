package dev.litedroid.launcher.wrapped

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import dev.litedroid.launcher.story.Lullaby
import dev.litedroid.launcher.story.Mood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Gösterinin müziği: haftaya özel üretilmiş parça (MediaPlayer, döngüde, yumuşak giriş/çıkış).
 * Parça henüz hazır değilse cihazda üretilen neşeli bir fon çalar, parça gelince ona geçilir.
 */
class WrappedMusic(private val scope: CoroutineScope) {
    private var player: MediaPlayer? = null
    private var fallback: Lullaby? = null
    private var fadeJob: Job? = null
    private var volume = 0f
    private var paused = false
    var playingTrack = false
        private set

    /** Yapay zekâ parçası yokken: cihazda üretilen fon. */
    fun startFallback() {
        if (player != null || fallback != null) return
        fallback = Lullaby().apply {
            setMood(Mood.HAPPY)
            start()
            duck(false)
        }
    }

    fun play(file: File) {
        if (playingTrack) return
        runCatching {
            val mp = MediaPlayer()
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            mp.setDataSource(file.path)
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.prepare()
            mp.start()
            player = mp
            playingTrack = true
            if (paused) mp.pause()
        }.onFailure {
            Log.w("LiteDroidWrapped", "music play failed: ${it.message}")
            startFallback()
            return
        }
        // Fon susar, parça yükselir
        fallback?.fadeOut()
        fallback = null
        fadeTo(0.85f, 2_500)
    }

    private fun fadeTo(target: Float, ms: Long) {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val from = volume
            val steps = 25
            for (i in 1..steps) {
                volume = from + (target - from) * i / steps
                runCatching { player?.setVolume(volume, volume) }
                delay(ms / steps)
            }
        }
    }

    fun pause() {
        paused = true
        runCatching { player?.pause() }
        fallback?.duck(true)
    }

    fun resume() {
        paused = false
        runCatching { player?.start() }
        fallback?.duck(false)
    }

    fun release() {
        fadeJob?.cancel()
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playingTrack = false
        fallback?.fadeOut()
        fallback = null
    }
}
