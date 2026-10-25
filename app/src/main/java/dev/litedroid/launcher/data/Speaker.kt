package dev.litedroid.launcher.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Gemini sesiyle okuma: anahtar ve ses adı (ör. "Sulafat"). */
data class CloudVoice(val key: String, val voice: String)

/**
 * Cevapları sesli okur. Gemini sesi açıksa ([cloud] bir ses döndürüyorsa) cümleler Gemini'de
 * seslendirilir: bir parça çalarken sonrakiler arkada hazırlanır. Gemini o an olmazsa (ağ, kota)
 * aynı parça cihazın metin okuma motoruyla okunur, cevap yarıda kalmaz.
 *
 * Akış halinde çalışır: cevap ekrana gelirken ilk cümle tamamlanır tamamlanmaz okumaya başlar,
 * sonraki cümleler sıraya eklenir. Böylece cevabın bitmesi beklenmez. Motor ekran açılırken
 * [warmUp] ile önceden hazırlanır; ilk okumada gecikme olmaz. [stop] her şeyi anında keser.
 */
class Speaker(private val context: Context, private val cloud: () -> CloudVoice? = { null }) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val player = PcmPlayer()
    private var cloudJob: Job? = null
    private var cloudTexts: Channel<String>? = null
    /** Bu oturumda Gemini sesi art arda başarısız olduysa cihaz sesine geçilir. */
    private var cloudFailures = 0

    private var tts: TextToSpeech? = null
    private var ready = false
    private var locale: Locale = Locale.getDefault()
    private var appliedLocale: Locale? = null
    /** Motor hazır olmadan gelen cümleler. */
    private val pending = ArrayList<String>()
    private val buffer = StringBuilder()
    private var spokenInStream = 0
    private var utteranceId = 0

    /** Motoru önceden başlatır (Claude ekranı açılınca). */
    fun warmUp() {
        ensureEngine()
    }

    private fun ensureEngine() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) {
                Log.w(TAG, "TTS init failed: $status")
                return@TextToSpeech
            }
            if (pending.isNotEmpty()) {
                val queued = pending.toList()
                pending.clear()
                queued.forEach { say(it) }
            }
        }
    }

    // ------------------------------------------------------------------
    // Akışlı okuma
    // ------------------------------------------------------------------

    /** Yeni bir cevap başlıyor: önceki okuma kesilir. */
    fun startStream(locale: Locale) {
        stop()
        this.locale = locale
        ensureEngine()
        val cv = cloud()?.takeIf { cloudFailures < 3 }
        if (cv != null) startCloud(cv)
    }

    private fun startCloud(cv: CloudVoice) {
        val texts = Channel<String>(Channel.UNLIMITED)
        val audios = Channel<Deferred<Pair<Pcm?, String>>>(2)
        cloudTexts = texts
        cloudJob = scope.launch {
            // Üretici: her parça hemen istenir (en çok iki parça önden), sıra korunur
            launch {
                for (t in texts) {
                    audios.send(async(Dispatchers.IO) {
                        val pcm = runCatching {
                            GeminiMedia.speak(context, cv.key, listOf(GeminiMedia.Turn(null, t, cv.voice, "natural, warm and friendly")), GeminiMedia.Purpose.CHAT)
                        }.onFailure { Log.w(TAG, "gemini voice failed: ${it.message}") }.getOrNull()
                        pcm to t
                    })
                }
                audios.close()
            }
            for (d in audios) {
                val (pcm, text) = d.await()
                if (pcm != null) {
                    cloudFailures = 0
                    player.play(pcm)
                } else {
                    cloudFailures++
                    sayDeviceAndWait(text)
                }
            }
        }
    }

    private suspend fun sayDeviceAndWait(text: String) {
        if (!ready) return
        say(text)
        delay(250)
        while (tts?.isSpeaking == true) delay(100)
    }

    /** Gelen metin parçası: tamamlanan cümleler hemen okunur. */
    fun feed(delta: String) {
        buffer.append(delta)
        flush(final = false)
    }

    /** Cevap bitti: kalan her şey okunur. */
    fun endStream() {
        flush(final = true)
        cloudTexts?.close()
    }

    /** Bir metni baştan okur ("Tekrar oku"). */
    fun speak(text: String, locale: Locale) {
        startStream(locale)
        feed(text)
        endStream()
    }

    fun stop() {
        cloudTexts?.close()
        cloudTexts = null
        cloudJob?.cancel()
        cloudJob = null
        player.stop()
        buffer.clear()
        pending.clear()
        spokenInStream = 0
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        stop()
        scope.coroutineContext[Job]?.cancel()
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
        appliedLocale = null
    }

    private fun flush(final: Boolean) {
        while (true) {
            // İlk cümle kısa da olsa hemen okunur; sonrakiler doğal duyulsun diye biraz biriktirilir.
            // Gemini sesinde parçalar daha uzun: daha az istek, daha akıcı tonlama.
            val cloudOn = cloudTexts != null
            val minLen = when {
                spokenInStream == 0 -> if (cloudOn) 24 else 8
                else -> if (cloudOn) 160 else 50
            }
            val cut = boundary(minLen)
            if (cut <= 0) break
            val piece = buffer.substring(0, cut)
            buffer.delete(0, cut)
            enqueue(piece)
        }
        if (final && buffer.isNotBlank()) {
            enqueue(buffer.toString())
            buffer.clear()
        }
    }

    /** [minLen]'den sonraki ilk cümle sonu (noktalama + boşluk ya da satır sonu); yoksa -1. */
    private fun boundary(minLen: Int): Int {
        val s = buffer
        if (s.length < minLen) return -1
        for (i in minLen - 1 until s.length - 1) {
            val c = s[i]
            val next = s[i + 1]
            if ((c == '.' || c == '!' || c == '?' || c == '…' || c == ':' || c == ';') && next.isWhitespace()) return i + 2
            if (c == '\n') return i + 1
        }
        return -1
    }

    private fun enqueue(raw: String) {
        val text = clean(raw)
        if (text.isBlank()) return
        cloudTexts?.let {
            it.trySend(text)
            spokenInStream++
            return
        }
        if (!ready) {
            pending += text
            spokenInStream++
            return
        }
        say(text)
    }

    private fun say(text: String) {
        val engine = tts ?: return
        if (appliedLocale != locale) {
            val result = engine.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "TTS language not available: $locale")
            }
            appliedLocale = locale
        }
        val max = TextToSpeech.getMaxSpeechInputLength().coerceAtMost(3_000)
        text.chunked(max).forEach { part ->
            val mode = if (spokenInStream == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(part, mode, null, "litedroid_${utteranceId++}")
            spokenInStream++
        }
    }

    private fun clean(text: String): String = text
        .replace(Regex("[*#_`>]"), "")
        .replace(Regex("\\[(\\d+)]"), "")
        .replace(Regex("https?://\\S+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private companion object {
        const val TAG = "LiteDroidSpeaker"
    }
}
