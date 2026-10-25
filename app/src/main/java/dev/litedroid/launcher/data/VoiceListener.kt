package dev.litedroid.launcher.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Uygulama içi konuşma tanıma. Sistemin konuşma ekranı yerine doğrudan [SpeechRecognizer] kullanılır:
 * - Mikrofon gerçekten hazır olunca ([State.LISTENING]) "Şimdi konuş" denir, ilk kelimeler kaçmaz.
 * - Söylenenler konuşurken ekranda canlı görünür (ara sonuçlar).
 * - Sessizlik süresi uzun tutulur, cümle ortasında kesilmez; sonuç boş gelirse son ara sonuç kullanılır.
 */
class VoiceListener(private val context: Context) {

    enum class State { IDLE, PREPARING, LISTENING, PROCESSING }

    interface Callback {
        fun onState(state: State)
        fun onPartial(text: String)
        fun onLevel(level: Float)
        /** Bitti: söylenen cümle (boşsa null). */
        fun onResult(text: String?)
        /** İzin yok ya da tanıma hizmeti kullanılamıyor: sistemin konuşma ekranına geçilmeli. */
        fun onUnavailable()
    }

    private var recognizer: SpeechRecognizer? = null
    private var lastPartial = ""
    private var finished = true

    fun available(): Boolean = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    fun start(languageTag: String, callback: Callback) {
        cancel()
        lastPartial = ""
        finished = false
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (r == null) {
            callback.onUnavailable()
            return
        }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = callback.onState(State.LISTENING)
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = callback.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = callback.onState(State.PROCESSING)
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                if (!text.isNullOrBlank()) {
                    lastPartial = text
                    callback.onPartial(text)
                }
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                finish(callback, text?.takeIf { it.isNotBlank() } ?: lastPartial.takeIf { it.isNotBlank() })
            }

            override fun onError(error: Int) {
                Log.w(TAG, "recognizer error $error")
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        finished = true
                        release()
                        callback.onState(State.IDLE)
                        callback.onUnavailable()
                    }
                    // Söylenen bir şey yakalandıysa onu kullan
                    else -> finish(callback, lastPartial.takeIf { it.isNotBlank() })
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Cümle ortasındaki kısa duraksamalarda kesilmesin
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_800L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_500L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1_500L)
        callback.onState(State.PREPARING)
        runCatching { r.startListening(intent) }.onFailure {
            Log.w(TAG, "startListening failed: ${it.message}")
            finished = true
            release()
            callback.onState(State.IDLE)
            callback.onUnavailable()
        }
    }

    /** Konuşmayı bitir: o ana kadar söylenen değerlendirilir. */
    fun stop() {
        runCatching { recognizer?.stopListening() }
    }

    /** Vazgeç: sonuç gönderilmez. */
    fun cancel() {
        finished = true
        release()
    }

    private fun finish(callback: Callback, text: String?) {
        if (finished) return
        finished = true
        release()
        callback.onState(State.IDLE)
        callback.onResult(text)
    }

    private fun release() {
        val r = recognizer ?: return
        recognizer = null
        runCatching { r.cancel() }
        runCatching { r.destroy() }
    }

    private companion object {
        const val TAG = "LiteDroidVoice"
    }
}
