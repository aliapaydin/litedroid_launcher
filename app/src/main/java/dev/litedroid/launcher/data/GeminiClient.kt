package dev.litedroid.launcher.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Gemini API istemcisi (akışlı / SSE). Kullanıcının kendi Google AI Studio anahtarıyla doğrudan
 * generativelanguage.googleapis.com'a gider. Web araması için "Google Search ile temellendirme" aracı
 * kullanılır, kaynaklar ayrıca bildirilir. Olaylar Claude istemcisiyle aynı türdendir ([ClaudeEvent]).
 */
object GeminiClient {

    private const val TAG = "LiteDroidGemini"
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"

    fun ask(
        apiKey: String,
        model: String,
        system: String,
        history: List<ClaudeExchange>,
        question: String,
        webSearch: Boolean,
    ): Flow<ClaudeEvent> = flow {
        val contents = JSONArray()
        history.takeLast(6).forEach { h ->
            contents.put(turn("user", h.question))
            contents.put(turn("model", h.answer.ifBlank { "…" }))
        }
        contents.put(turn("user", question))
        val body = JSONObject()
            .put("contents", contents)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 1024).put("temperature", 0.7))
        if (webSearch) body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))

        val url = URL(BASE + URLEncoder.encode(model, "UTF-8") + ":streamGenerateContent?alt=sse")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
            setRequestProperty("accept", "text/event-stream")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
                val e = runCatching { JSONObject(err).getJSONObject("error") }.getOrNull()
                val msg = when {
                    // Bazı hesaplarda yeni "AQ." anahtarlar bu uç noktada reddediliyor
                    err.contains("ACCESS_TOKEN_TYPE_UNSUPPORTED") ->
                        "Google bu anahtarı Gemini API için kabul etmedi (ACCESS_TOKEN_TYPE_UNSUPPORTED). AI Studio'da yeni bir anahtar oluşturup tekrar dene."
                    else -> e?.optString("message")?.takeIf { it.isNotBlank() } ?: "HTTP $code"
                }
                val auth = code == 401 || code == 403 || err.contains("API_KEY_INVALID") || err.contains("API key not valid")
                Log.w(TAG, "HTTP $code $err")
                emit(ClaudeEvent.Failed(msg, authProblem = auth))
                return@flow
            }
            var finish: String? = null
            val seen = HashSet<String>()
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val json = runCatching { JSONObject(line.removePrefix("data:").trim()) }.getOrNull() ?: continue
                    json.optJSONObject("error")?.let {
                        emit(ClaudeEvent.Failed(it.optString("message", "error")))
                        return@use
                    }
                    val cand = json.optJSONArray("candidates")?.optJSONObject(0) ?: continue
                    val parts = cand.optJSONObject("content")?.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val p = parts.optJSONObject(i) ?: continue
                            // Düşünce özetleri cevaba karışmasın
                            if (p.optBoolean("thought")) continue
                            val t = p.optString("text")
                            if (t.isNotEmpty()) emit(ClaudeEvent.Text(t))
                        }
                    }
                    cand.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")?.let { chunks ->
                        for (i in 0 until chunks.length()) {
                            val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
                            val uri = web.optString("uri")
                            if (uri.isNotBlank() && seen.add(uri)) emit(ClaudeEvent.Source(web.optString("title"), uri))
                        }
                    }
                    cand.optString("finishReason").takeIf { it.isNotBlank() }?.let { finish = it }
                }
            }
            emit(ClaudeEvent.Done(finish))
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private fun turn(role: String, text: String) =
        JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", text)))
}
