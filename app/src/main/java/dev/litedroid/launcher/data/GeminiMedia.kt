package dev.litedroid.launcher.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/** 16 bit, mono PCM ses. */
class Pcm(val data: ByteArray, val rate: Int) {
    val seconds: Float get() = data.size / 2f / rate

    /** Kendi başına çalınabilen WAV dosyası (masal kitaplığı için). */
    fun toWav(): ByteArray {
        val out = ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVE".toByteArray())
        out.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        out.put("data".toByteArray()).putInt(data.size).put(data)
        return out.array()
    }

    companion object {
        /** WAV başlığı varsa çözer; yoksa ham L16 kabul eder. */
        fun from(bytes: ByteArray, mime: String?): Pcm {
            if (bytes.size > 44 && String(bytes, 0, 4) == "RIFF") {
                val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                var rate = 24_000
                var pos = 12
                while (pos + 8 <= bytes.size) {
                    val id = String(bytes, pos, 4)
                    val len = b.getInt(pos + 4)
                    if (id == "fmt ") rate = b.getInt(pos + 12)
                    if (id == "data") {
                        val end = (pos + 8 + len).coerceAtMost(bytes.size)
                        return Pcm(bytes.copyOfRange(pos + 8, end), rate)
                    }
                    pos += 8 + len + (len and 1)
                }
                return Pcm(bytes.copyOfRange(44, bytes.size), rate)
            }
            val rate = mime?.let { Regex("rate=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 24_000
            return Pcm(bytes, rate)
        }

        fun silence(seconds: Float, rate: Int = 24_000) = Pcm(ByteArray((rate * seconds).toInt() * 2), rate)

        fun join(parts: List<Pcm>, gapSeconds: Float = 0.25f): Pcm {
            val rate = parts.firstOrNull()?.rate ?: 24_000
            val out = ByteArrayOutputStream()
            parts.forEachIndexed { i, p ->
                if (i > 0) out.write(ByteArray((rate * gapSeconds).toInt() * 2))
                out.write(p.data)
            }
            return Pcm(out.toByteArray(), rate)
        }
    }
}

/**
 * Gemini ile ses (TTS) ve resim üretimi, kullanıcının kendi anahtarıyla.
 *
 * Google yeni "Interactions" API'sine geçiyor ama eski generateContent de tam destekli. Hangisinin
 * ve hangi modelin bu anahtarla çalıştığı cihazdan cihaza değişebildiği için sırayla denenir;
 * çalışan yol hatırlanır ve sonraki isteklerde doğrudan o kullanılır.
 */
object GeminiMedia {

    private const val TAG = "LiteDroidVoice"
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/"

    class ApiException(val code: Int, message: String, val auth: Boolean = false, val quota: Boolean = false) : Exception(message)

    /** Seslendirilecek bir satır. [speaker] boşsa tek sesli okunur. */
    data class Turn(val speaker: String?, val text: String, val voice: String, val style: String? = null)

    enum class Purpose { CHAT, STORY }

    private data class Route(val api: Char, val model: String) {
        val key get() = "$api:$model"
    }

    private val ttsRoutes = mapOf(
        Purpose.CHAT to listOf(
            Route('I', "gemini-3.8-flash-lite-tts"), Route('I', "gemini-3.8-flash-tts"),
            Route('G', "gemini-3.8-flash-lite-tts"), Route('G', "gemini-3.1-flash-tts-preview"),
            Route('G', "gemini-2.5-flash-preview-tts"),
        ),
        Purpose.STORY to listOf(
            Route('I', "gemini-3.8-flash-tts"), Route('I', "gemini-3.8-flash-lite-tts"),
            Route('G', "gemini-3.8-flash-tts"), Route('G', "gemini-3.1-flash-tts-preview"),
            Route('G', "gemini-2.5-flash-preview-tts"), Route('G', "gemini-2.5-pro-preview-tts"),
        ),
    )
    private val imageRoutes = listOf(
        Route('I', "gemini-3.1-flash-lite-image"), Route('I', "gemini-nano-banana-2.1"),
        Route('G', "gemini-3.1-flash-lite-image"), Route('G', "gemini-3.1-flash-image"),
        Route('G', "gemini-2.5-flash-image"),
    )

    /** Son başarılı ses modeli (ayarlardaki "Sesi dene" gösterir). */
    @Volatile var lastVoiceModel: String? = null
        private set

    private val remembered = HashMap<String, Route>()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("litedroid_gemini_routes", Context.MODE_PRIVATE)

    private fun recall(ctx: Context, slot: String, all: List<Route>): List<Route> {
        val known = remembered[slot] ?: prefs(ctx).getString(slot, null)?.let { k -> all.firstOrNull { it.key == k } }
        return if (known != null) listOf(known) + (all - known) else all
    }

    private fun remember(ctx: Context, slot: String, r: Route) {
        remembered[slot] = r
        prefs(ctx).edit().putString(slot, r.key).apply()
    }

    // ------------------------------------------------------------------
    // Ses
    // ------------------------------------------------------------------

    /**
     * Satırları seslendirir. Bir istekte en çok iki konuşmacı olabildiği için satırlar sırayla
     * gruplanır (her grupta en çok iki ses), gruplar ayrı ayrı üretilip arka arkaya eklenir.
     */
    suspend fun speak(ctx: Context, key: String, turns: List<Turn>, purpose: Purpose): Pcm = withContext(Dispatchers.IO) {
        val groups = ArrayList<MutableList<Turn>>()
        for (t in turns.filter { it.text.isNotBlank() }) {
            val g = groups.lastOrNull()
            val voices = g?.map { it.voice }?.toSet().orEmpty()
            if (g != null && (t.voice in voices || voices.size < 2)) g += t else groups += mutableListOf(t)
        }
        val parts = groups.map { g ->
            try {
                synthesize(ctx, key, g, purpose)
            } catch (e: ApiException) {
                if (e.auth || e.quota || g.map { it.voice }.distinct().size < 2) throw e
                // Çok sesli istek hiçbir yolda olmadıysa: her satır kendi sesiyle tek tek
                Log.w(TAG, "multi-speaker failed, falling back to single voice: ${e.message}")
                Pcm.join(g.map { synthesize(ctx, key, listOf(it.copy(speaker = null)), purpose) }, 0.2f)
            }
        }
        Pcm.join(parts)
    }

    private suspend fun synthesize(ctx: Context, key: String, turns: List<Turn>, purpose: Purpose): Pcm {
        val slot = "tts_${purpose.name}"
        var last: Exception? = null
        for (r in recall(ctx, slot, ttsRoutes.getValue(purpose))) {
            coroutineContext.ensureActive()
            try {
                val (mime, bytes) = if (r.api == 'I') ttsInteractions(key, r.model, turns) else ttsGenerate(key, r.model, turns, purpose)
                remember(ctx, slot, r)
                lastVoiceModel = r.model
                return Pcm.from(bytes, mime)
            } catch (e: ApiException) {
                Log.w(TAG, "tts ${r.key} → ${e.code} ${e.message}")
                if (e.auth || e.quota) throw e
                last = e
            }
        }
        throw last ?: ApiException(0, "Ses üretilemedi")
    }

    private fun voicesOf(turns: List<Turn>): List<Pair<String, String>> =
        turns.map { speakerId(it) to it.voice }.distinct()

    /** Konuşmacı adları API'de boşluksuz ve sade olmalı. */
    private fun speakerId(t: Turn): String =
        (t.speaker ?: "Narrator").filter { it.isLetterOrDigit() }.ifBlank { "Speaker" }.take(20)

    private fun ttsInteractions(key: String, model: String, turns: List<Turn>): Pair<String?, ByteArray> {
        val voices = voicesOf(turns)
        val multi = voices.map { it.second }.distinct().size > 1
        val content = JSONArray()
        turns.forEach { t ->
            val item = JSONObject().put("type", "text").put("text", t.text)
            val meta = JSONObject().put("type", "speech_metadata")
            if (multi) meta.put("speaker", speakerId(t))
            t.style?.let { meta.put("style", it) }
            if (meta.length() > 1) item.put("annotations", JSONArray().put(meta))
            content.put(item)
        }
        val speech: Any = if (multi) {
            JSONObject().put("speakers", JSONArray().apply {
                voices.distinctBy { it.first }.forEach { (s, v) -> put(JSONObject().put("speaker", s).put("voice", v)) }
            })
        } else JSONArray().put(JSONObject().put("voice", turns.first().voice))
        val body = JSONObject()
            .put("model", model)
            .put("input", JSONArray().put(JSONObject().put("type", "user_input").put("content", content)))
            .put("response_format", JSONObject().put("type", "audio"))
            .put("generation_config", JSONObject().put("speech_config", speech))
        val json = post(BASE + "interactions", key, body, 120_000)
        return findBlob(json, "audio") ?: throw ApiException(400, "Yanıtta ses yok")
    }

    private fun ttsGenerate(key: String, model: String, turns: List<Turn>, purpose: Purpose): Pair<String?, ByteArray> {
        val voices = voicesOf(turns)
        val multi = voices.map { it.second }.distinct().size > 1
        // Eski modeller köşeli parantezli etiket bekler; 3.8 açılı parantezli
        val legacyTags = !model.startsWith("gemini-3.8")
        fun tags(s: String) = if (legacyTags) s.replace(Regex("<([a-z ]{2,20})>")) { "[${it.groupValues[1]}]" } else s
        val lines = turns.joinToString("\n") { t -> if (multi) "${speakerId(t)}: ${tags(t.text)}" else tags(t.text) }
        // 2.5 modelleri nasıl okunacağını metnin başındaki tariften anlar
        val prompt = if (model.startsWith("gemini-2.5")) {
            val how = if (purpose == Purpose.STORY) "Read aloud as a warm, gentle bedtime storyteller, slowly and softly" else "Read aloud in a natural, friendly voice"
            "$how:\n$lines"
        } else lines
        val speechConfig = if (multi) {
            JSONObject().put("multiSpeakerVoiceConfig", JSONObject().put("speakerVoiceConfigs", JSONArray().apply {
                voices.distinctBy { it.first }.forEach { (s, v) -> put(JSONObject().put("speaker", s).put("voiceConfig", prebuilt(v))) }
            }))
        } else JSONObject().put("voiceConfig", prebuilt(turns.first().voice))
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("AUDIO")).put("speechConfig", speechConfig))
        val json = post(BASE + "models/$model:generateContent", key, body, 120_000)
        return findBlob(json, "audio") ?: throw ApiException(400, "Yanıtta ses yok")
    }

    private fun prebuilt(voice: String) = JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))

    // ------------------------------------------------------------------
    // Resim
    // ------------------------------------------------------------------

    /** 16:9 resim üretir; JPEG ya da PNG baytları döner. */
    suspend fun image(ctx: Context, key: String, prompt: String): ByteArray = withContext(Dispatchers.IO) {
        var last: Exception? = null
        for (r in recall(ctx, "image", imageRoutes)) {
            ensureActive()
            try {
                val json = if (r.api == 'I') {
                    post(BASE + "interactions", key, JSONObject()
                        .put("model", r.model)
                        .put("input", prompt)
                        .put("response_format", JSONObject().put("type", "image").put("mime_type", "image/jpeg").put("aspect_ratio", "16:9").put("image_size", "1K")), 150_000)
                } else {
                    post(BASE + "models/${r.model}:generateContent", key, JSONObject()
                        .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                        .put("generationConfig", JSONObject()
                            .put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
                            .put("imageConfig", JSONObject().put("aspectRatio", "16:9"))), 150_000)
                }
                val blob = findBlob(json, "image") ?: throw ApiException(400, "Yanıtta resim yok")
                remember(ctx, "image", r)
                return@withContext blob.second
            } catch (e: ApiException) {
                Log.w(TAG, "image ${r.key} → ${e.code} ${e.message}")
                if (e.auth || e.quota) throw e
                last = e
            }
        }
        throw last ?: ApiException(0, "Resim üretilemedi")
    }

    // ------------------------------------------------------------------
    // Müzik (Lyria): Wrapped'in fon müziği
    // ------------------------------------------------------------------

    private val musicRoutes = listOf(Route('I', "lyria-3.5"), Route('I', "lyria-3-clip-preview"))

    /**
     * Sözsüz müzik üretir (Lyria 3.5 birkaç dakikalık tam parça, olmazsa Lyria 3 Clip 30 sn).
     * Dönen: (uzantı, baytlar); uzantı "mp3" ya da "wav".
     */
    suspend fun music(ctx: Context, key: String, prompt: String): Pair<String, ByteArray> = withContext(Dispatchers.IO) {
        var last: Exception? = null
        for (r in recall(ctx, "music", musicRoutes)) {
            ensureActive()
            try {
                val json = post(BASE + "interactions", key, JSONObject().put("model", r.model).put("input", prompt), 300_000)
                val blob = findBlob(json, "audio") ?: throw ApiException(400, "Yanıtta müzik yok")
                remember(ctx, "music", r)
                val b = blob.second
                val ext = when {
                    b.size > 4 && b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() -> "wav"
                    blob.first?.contains("wav") == true -> "wav"
                    else -> "mp3"
                }
                return@withContext ext to b
            } catch (e: ApiException) {
                Log.w(TAG, "music ${r.key} → ${e.code} ${e.message}")
                if (e.auth || e.quota) throw e
                last = e
            }
        }
        throw last ?: ApiException(0, "Müzik üretilemedi")
    }

    // ------------------------------------------------------------------
    // JSON metin (masalın kendisi)
    // ------------------------------------------------------------------

    suspend fun json(key: String, model: String, system: String, prompt: String): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("generationConfig", JSONObject()
                .put("responseMimeType", "application/json")
                .put("temperature", 1.0)
                .put("maxOutputTokens", 12_000))
        val res = post(BASE + "models/$model:generateContent", key, body, 180_000)
        val parts = res.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
        val text = buildString {
            if (parts != null) for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                if (!p.optBoolean("thought")) append(p.optString("text"))
            }
        }.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        if (text.isEmpty()) throw ApiException(500, "Masal boş geldi, tekrar dene")
        runCatching { JSONObject(text) }.getOrElse {
            // Bazen başına/sonuna açıklama ekler: ilk { ile son } arası
            val s = text.indexOf('{')
            val e = text.lastIndexOf('}')
            if (s >= 0 && e > s) JSONObject(text.substring(s, e + 1)) else throw ApiException(500, "Masal okunamadı, tekrar dene")
        }
    }

    // ------------------------------------------------------------------
    // Ortak
    // ------------------------------------------------------------------

    private fun post(url: String, key: String, body: JSONObject, readTimeout: Int): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            this.readTimeout = readTimeout
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-goog-api-key", key)
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
                val msg = runCatching { JSONObject(err).getJSONObject("error").optString("message") }.getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP $code"
                val auth = code == 401 || code == 403 && !err.contains("PERMISSION_DENIED") || err.contains("API_KEY_INVALID") || err.contains("API key not valid")
                val quota = code == 429 || err.contains("RESOURCE_EXHAUSTED")
                throw ApiException(code, msg.take(300), auth = auth, quota = quota)
            }
            return JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (e: IOException) {
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /** Yanıt içindeki ilk ses/resim verisini bulur (iki API'nin biçimi de desteklenir). */
    private fun findBlob(node: Any?, kind: String): Pair<String?, ByteArray>? {
        when (node) {
            is JSONObject -> {
                val inline = node.optJSONObject("inlineData") ?: node.optJSONObject("inline_data")
                if (inline != null) {
                    val mime = inline.optString("mimeType", inline.optString("mime_type"))
                    val data = inline.optString("data")
                    if (data.isNotEmpty() && mime.startsWith(kind)) return mime to Base64.decode(data, Base64.DEFAULT)
                }
                val data = node.optString("data")
                if (data.length > 64) {
                    val mime = node.optString("mime_type", node.optString("mimeType")).takeIf { it.isNotBlank() }
                    if (node.optString("type") == kind || mime?.startsWith(kind) == true) return mime to Base64.decode(data, Base64.DEFAULT)
                }
                val keys = node.keys()
                while (keys.hasNext()) findBlob(node.opt(keys.next()), kind)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findBlob(node.opt(i), kind)?.let { return it }
        }
        return null
    }

    /** Kullanıcıya gösterilecek kısa hata metni. */
    fun describe(t: Throwable): String = when {
        t is ApiException && t.auth -> dev.litedroid.launcher.tx("Gemini anahtarı kabul edilmedi. Ayarlar'dan anahtarı kontrol et.", "The Gemini key was rejected. Check it in Settings.")
        t is ApiException && t.quota -> dev.litedroid.launcher.tx("Gemini kotası doldu. Biraz bekle ya da AI Studio'da faturalandırmayı kontrol et.", "Gemini quota reached. Wait a bit or check billing in AI Studio.")
        t is IOException -> dev.litedroid.launcher.tx("İnternete ulaşılamadı.", "No internet connection.")
        else -> t.message ?: t.javaClass.simpleName
    }
}

/** PCM sesi AudioTrack ile çalar; duraklatılabilir, anında durdurulabilir. */
class PcmPlayer {
    @Volatile private var track: AudioTrack? = null
    @Volatile var paused = false
        private set

    /** Ses bitene kadar bekler. İptal edilirse hemen susar. */
    suspend fun play(pcm: Pcm, volume: Float = 1f) = withContext(Dispatchers.IO) {
        val min = AudioTrack.getMinBufferSize(pcm.rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(pcm.rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(min, 32 * 1024))
            .build()
        track = t
        try {
            t.setVolume(volume)
            if (!paused) t.play()
            var off = 0
            while (off < pcm.data.size) {
                ensureActive()
                if (paused) { delay(60); continue }
                val n = t.write(pcm.data, off, minOf(8192, pcm.data.size - off))
                if (n <= 0) break
                off += n
            }
            val frames = pcm.data.size / 2
            while (t.playbackHeadPosition < frames && t.playState != AudioTrack.PLAYSTATE_STOPPED) {
                ensureActive()
                delay(40)
            }
        } finally {
            runCatching { t.stop() }
            t.release()
            if (track === t) track = null
        }
    }

    fun pause() {
        paused = true
        runCatching { track?.pause() }
    }

    fun resume() {
        paused = false
        runCatching { track?.play() }
    }

    fun stop() {
        paused = false
        runCatching { track?.pause(); track?.flush(); track?.stop() }
    }
}
