package dev.litedroid.launcher.story

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Masalın arkasında çalan, cihazda anlık üretilen yumuşak ninni müziği: yavaş akorlu bir
 * "pad" ve ara ara çınlayan çan notaları. Dosya yok, telif yok. Sayfanın havasına ([Mood])
 * göre akorlar, tempo ve parlaklık değişir; geçişler yumuşaktır.
 */
class Lullaby {

    private val rate = 22_050
    @Volatile private var running = false
    @Volatile private var mood = Mood.CALM
    /** Hedef ses düzeyi (anlatım varken kısılır). */
    @Volatile private var target = 0f
    private var thread: Thread? = null

    // Akorlar (MIDI nota numaraları): her havanın kendi dizisi
    private val chords = mapOf(
        Mood.CALM to listOf(intArrayOf(48, 55, 64, 67), intArrayOf(45, 52, 60, 64), intArrayOf(41, 48, 57, 64), intArrayOf(43, 50, 59, 62)),
        Mood.HAPPY to listOf(intArrayOf(48, 55, 64, 72), intArrayOf(53, 60, 65, 69), intArrayOf(43, 55, 62, 71), intArrayOf(48, 55, 64, 67)),
        Mood.MYSTERY to listOf(intArrayOf(45, 52, 60, 64), intArrayOf(40, 47, 55, 59), intArrayOf(41, 48, 57, 60), intArrayOf(38, 45, 53, 57)),
        Mood.ADVENTURE to listOf(intArrayOf(50, 57, 62, 66), intArrayOf(47, 54, 59, 62), intArrayOf(43, 50, 55, 59), intArrayOf(45, 52, 57, 61)),
        Mood.SLEEPY to listOf(intArrayOf(48, 55, 60, 64), intArrayOf(41, 48, 53, 57), intArrayOf(45, 52, 57, 60), intArrayOf(43, 50, 55, 59)),
    )

    fun start() {
        target = 0.55f
        running = true
        // Kısılmakta olan önceki ses hâlâ çalıyorsa yenisi açılmaz, o devam eder
        if (thread?.isAlive == true) return
        thread = Thread({ loop() }, "LiteDroidLullaby").apply { isDaemon = true; start() }
    }

    fun setMood(m: Mood) {
        mood = m
    }

    /** Anlatım sırasında müzik kısılır, aralarda biraz açılır. */
    fun duck(narrating: Boolean) {
        target = if (mood == Mood.SLEEPY) (if (narrating) 0.32f else 0.45f) else if (narrating) 0.38f else 0.6f
    }

    /** Yavaşça susar; sessizliğe inince ses üretimi de durur (işlemci boşa çalışmaz). */
    fun fadeOut() {
        running = false
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    private fun midiHz(n: Int) = 440.0 * Math.pow(2.0, (n - 69) / 12.0)

    private fun loop() {
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(min, 8192))
            .build()
        // Genel ses: anlatımı bastırmasın
        track.setVolume(0.16f)
        track.play()
        val buf = ShortArray(1024)
        val padPhase = DoubleArray(4)
        val padFreq = DoubleArray(4)
        val padTarget = DoubleArray(4)
        var t = 0L
        var gain = 0.0
        var chordIndex = 0
        var nextChordAt = 0L
        // Çan sesi: tek nota, üstel sönümlü
        var bellFreq = 0.0
        var bellPhase = 0.0
        var bellAge = Long.MAX_VALUE / 4
        var nextBellAt = rate * 2L
        val rnd = java.util.Random()
        try {
            while (running || gain > 0.002) {
                val m = mood
                val chordLen = when (m) {
                    Mood.ADVENTURE -> rate * 4L
                    Mood.HAPPY -> rate * 5L
                    Mood.SLEEPY -> rate * 9L
                    else -> rate * 7L
                }
                for (i in buf.indices) {
                    if (t >= nextChordAt) {
                        val list = chords.getValue(m)
                        chordIndex = (chordIndex + 1) % list.size
                        val c = list[chordIndex]
                        for (v in 0 until 4) padTarget[v] = midiHz(c[v])
                        if (padFreq[0] == 0.0) for (v in 0 until 4) padFreq[v] = padTarget[v]
                        nextChordAt = t + chordLen
                    }
                    if (t >= nextBellAt) {
                        val c = chords.getValue(m)[chordIndex]
                        bellFreq = midiHz(c[rnd.nextInt(4)] + if (m == Mood.MYSTERY) 12 else 24)
                        bellPhase = 0.0
                        bellAge = 0
                        val gap = when (m) {
                            Mood.ADVENTURE -> 0.5 + rnd.nextDouble() * 0.6
                            Mood.HAPPY -> 0.7 + rnd.nextDouble() * 0.8
                            Mood.SLEEPY -> 2.2 + rnd.nextDouble() * 2.5
                            else -> 1.2 + rnd.nextDouble() * 1.6
                        }
                        nextBellAt = t + (gap * rate).toLong()
                    }
                    // Akor geçişi: frekanslar yavaşça kayar (portamento)
                    var pad = 0.0
                    for (v in 0 until 4) {
                        padFreq[v] += (padTarget[v] - padFreq[v]) * 0.00012
                        padPhase[v] += 2 * PI * padFreq[v] / rate
                        if (padPhase[v] > 2 * PI) padPhase[v] -= 2 * PI
                        // Hafif titreşimli, yumuşak ton (temel + biraz oktav)
                        val wob = 1.0 + 0.002 * sin(t * 2 * PI * (0.13 + v * 0.05) / rate)
                        pad += sin(padPhase[v] * wob) * 0.8 + sin(padPhase[v] * 2) * 0.12
                    }
                    pad /= 4
                    var bell = 0.0
                    if (bellAge < rate * 4L) {
                        bellPhase += 2 * PI * bellFreq / rate
                        val env = exp(-bellAge / (rate * 0.9)) * minOf(1.0, bellAge / (rate * 0.004))
                        bell = (sin(bellPhase) + 0.3 * sin(bellPhase * 2.76)) * env * 0.35
                        bellAge++
                    }
                    val goal = if (running) target.toDouble() else 0.0
                    gain += (goal - gain) * 0.00005
                    val s = (pad * 0.55 + bell) * gain
                    buf[i] = (s.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
                    t++
                }
                if (track.write(buf, 0, buf.size) < 0) break
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }
}
