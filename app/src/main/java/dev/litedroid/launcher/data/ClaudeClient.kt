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

/** Claude'un akış halinde gönderdiği olaylar. */
sealed interface ClaudeEvent {
    data class Text(val delta: String) : ClaudeEvent
    data class Searching(val query: String?) : ClaudeEvent
    data class Source(val title: String, val url: String) : ClaudeEvent
    data class Done(val stopReason: String?) : ClaudeEvent
    data class Failed(val message: String, val authProblem: Boolean = false) : ClaudeEvent
}

/** Önceki bir soru-cevap (takip soruları için bağlam). */
data class ClaudeExchange(val question: String, val answer: String)

/**
 * Claude Messages API istemcisi (akışlı / SSE). Kullanıcının kendi API anahtarıyla doğrudan
 * api.anthropic.com'a gider; arada sunucu yok. İstenirse web araması aracı eklenir, kaynaklar
 * (citations) ayrıca bildirilir.
 */
object ClaudeClient {

    private const val TAG = "LiteDroidClaude"
    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val API_VERSION = "2023-06-01"

    fun ask(
        apiKey: String,
        model: String,
        system: String,
        history: List<ClaudeExchange>,
        question: String,
        webSearch: Boolean,
        timezone: String?,
        city: String?,
    ): Flow<ClaudeEvent> = flow {
        val messages = JSONArray()
        history.takeLast(6).forEach { h ->
            messages.put(JSONObject().put("role", "user").put("content", h.question))
            messages.put(JSONObject().put("role", "assistant").put("content", h.answer.ifBlank { "…" }))
        }
        messages.put(JSONObject().put("role", "user").put("content", question))

        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 1024)
            .put("stream", true)
            .put("system", system)
            .put("messages", messages)
        if (webSearch) {
            val tool = JSONObject()
                .put("type", "web_search_20250305")
                .put("name", "web_search")
                .put("max_uses", 3)
            val location = JSONObject().put("type", "approximate")
            timezone?.takeIf { it.isNotBlank() }?.let { location.put("timezone", it) }
            city?.takeIf { it.isNotBlank() }?.let { location.put("city", it) }
            if (location.length() > 1) tool.put("user_location", location)
            body.put("tools", JSONArray().put(tool))
        }

        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", API_VERSION)
            setRequestProperty("accept", "text/event-stream")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
                val msg = runCatching { JSONObject(err).getJSONObject("error").getString("message") }.getOrNull()
                    ?: "HTTP $code"
                Log.w(TAG, "HTTP $code $err")
                emit(ClaudeEvent.Failed(msg, authProblem = code == 401 || code == 403))
                return@flow
            }
            var stopReason: String? = null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val json = runCatching { JSONObject(line.removePrefix("data:").trim()) }.getOrNull() ?: continue
                    when (json.optString("type")) {
                        "content_block_start" -> {
                            val block = json.optJSONObject("content_block")
                            if (block?.optString("type") == "server_tool_use") emit(ClaudeEvent.Searching(null))
                        }
                        "content_block_delta" -> {
                            val delta = json.optJSONObject("delta") ?: continue
                            when (delta.optString("type")) {
                                "text_delta" -> emit(ClaudeEvent.Text(delta.optString("text")))
                                "citations_delta" -> {
                                    val c = delta.optJSONObject("citation")
                                    val url = c?.optString("url").orEmpty()
                                    if (url.isNotBlank()) emit(ClaudeEvent.Source(c?.optString("title").orEmpty(), url))
                                }
                                "input_json_delta" -> Unit
                            }
                        }
                        "message_delta" -> {
                            json.optJSONObject("delta")?.optString("stop_reason")?.takeIf { it.isNotBlank() && it != "null" }
                                ?.let { stopReason = it }
                        }
                        "error" -> {
                            val msg = json.optJSONObject("error")?.optString("message") ?: "error"
                            emit(ClaudeEvent.Failed(msg))
                            return@use
                        }
                    }
                }
            }
            emit(ClaudeEvent.Done(stopReason))
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)
}
