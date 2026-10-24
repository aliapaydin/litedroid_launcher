package dev.litedroid.launcher.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/** Cevapları sesli okur (cihazın metin okuma motoru). İlk kullanımda hazırlanır. */
class Speaker(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, Locale>? = null

    fun speak(text: String, locale: Locale) {
        val clean = text
            .replace(Regex("[*#_`>]"), "")
            .replace(Regex("\\[(\\d+)]"), "")
            .trim()
        if (clean.isBlank()) return
        val engine = tts
        if (engine == null) {
            pending = clean to locale
            tts = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (!ready) Log.w("LiteDroidClaude", "TTS init failed: $status")
                pending?.let { (t, l) -> if (ready) say(t, l) }
                pending = null
            }
            return
        }
        if (ready) say(clean, locale) else pending = clean to locale
    }

    private fun say(text: String, locale: Locale) {
        val engine = tts ?: return
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w("LiteDroidClaude", "TTS language not available: $locale")
        }
        // Uzun metinler motorun sınırına takılmasın diye parçalara bölünür
        val max = TextToSpeech.getMaxSpeechInputLength().coerceAtMost(3_000)
        val chunks = text.split(Regex("(?<=[.!?…])\\s+")).fold(mutableListOf<StringBuilder>()) { acc, s ->
            if (acc.isEmpty() || acc.last().length + s.length > max) acc.add(StringBuilder(s)) else acc.last().append(' ').append(s)
            acc
        }
        chunks.forEachIndexed { i, c ->
            engine.speak(c.toString(), if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "litedroid_$i")
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    }
}
