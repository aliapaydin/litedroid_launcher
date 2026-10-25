package dev.litedroid.launcher.quiz

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * Yerel ağdaki telefonların bağlandığı küçük HTTP sunucusu. Telefon tarayıcısı (Android ya da iPhone)
 * QR koddaki adresi açar, sayfa ([assets/quiz/index.html]) yarım saniyede bir durumu sorar ve
 * cevabı gönderir. Kurulum, uygulama ya da internet gerekmez; TV ile telefonlar aynı ağda olmalı.
 *
 * Uç noktalar:
 *   GET  /                 telefon arayüzü
 *   GET  /api/state?pid=   oyunun o oyuncuya göre durumu
 *   POST /api/join         {name, avatar, pid?} → {pid}
 *   POST /api/answer       {pid, index, choice}
 */
class QuizServer(context: Context, private val game: QuizGame) {
    private val app = context.applicationContext
    private val pool = Executors.newCachedThreadPool()
    private var server: ServerSocket? = null
    @Volatile private var running = false
    private val page: ByteArray by lazy {
        runCatching { app.assets.open("quiz/index.html").use { it.readBytes() } }.getOrDefault("LiteDroid".toByteArray())
    }

    /** Dinlenen port (başlamadıysa 0). */
    var port: Int = 0
        private set

    /** Telefonların açacağı adres, ör. http://192.168.0.40:8765 (ağ yoksa null). */
    val url: String? get() = localIp()?.let { "http://$it:$port" }

    fun start(): Boolean {
        if (running) return true
        for (p in PORTS) {
            val s = runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(p))
                }
            }.getOrNull() ?: continue
            server = s
            port = p
            running = true
            Thread({ acceptLoop(s) }, "quiz-server").apply { isDaemon = true }.start()
            Log.i(TAG, "quiz server on port $p, url=$url")
            return true
        }
        Log.w(TAG, "no free port for quiz server")
        return false
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        server = null
        pool.shutdownNow()
    }

    private fun acceptLoop(s: ServerSocket) {
        while (running) {
            val socket = runCatching { s.accept() }.getOrNull() ?: break
            runCatching { pool.execute { handle(socket) } }.onFailure { runCatching { socket.close() } }
        }
    }

    private fun handle(socket: Socket) {
        socket.use { sock ->
            runCatching {
                sock.soTimeout = 8_000
                val input = BufferedInputStream(sock.getInputStream())
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val target = parts[1]
                var length = 0
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val i = line.indexOf(':')
                    if (i > 0 && line.substring(0, i).trim().equals("content-length", true)) {
                        length = line.substring(i + 1).trim().toIntOrNull()?.coerceIn(0, 16_384) ?: 0
                    }
                }
                val body = if (length > 0) readBytes(input, length).toString(Charsets.UTF_8) else ""
                val path = target.substringBefore('?')
                val query = parseQuery(target.substringAfter('?', ""))
                val out = sock.getOutputStream()
                when {
                    method == "GET" && (path == "/" || path == "/index.html") -> respond(out, 200, "text/html; charset=utf-8", page)
                    method == "GET" && path == "/api/state" -> respondJson(out, stateFor(query["pid"]))
                    method == "POST" && path == "/api/join" -> {
                        val o = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
                        val p = game.join(o.optString("name"), o.optString("avatar", QuizGame.AVATARS.first()), o.optString("pid").takeIf { it.isNotBlank() })
                        if (p == null) respondJson(out, JSONObject().put("error", "full"), 409)
                        else respondJson(out, JSONObject().put("pid", p.id).put("name", p.name))
                    }
                    method == "POST" && path == "/api/answer" -> {
                        val o = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
                        val ok = game.answer(o.optString("pid"), o.optInt("index", -1), o.optInt("choice", -1))
                        respondJson(out, JSONObject().put("ok", ok))
                    }
                    method == "POST" && path == "/api/leave" -> {
                        val o = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
                        game.remove(o.optString("pid"))
                        respondJson(out, JSONObject().put("ok", true))
                    }
                    path == "/favicon.ico" -> respond(out, 204, "text/plain", ByteArray(0))
                    else -> respond(out, 404, "text/plain; charset=utf-8", "Not found".toByteArray())
                }
            }.onFailure { Log.d(TAG, "request failed: ${it.message}") }
        }
    }

    /** Oyunun telefona gönderilen hali: o oyuncunun kendi cevabı ve sonucu dahil. */
    private fun stateFor(pid: String?): JSONObject {
        val s = game.state.value
        val me = s.players.firstOrNull { it.id == pid }
        if (pid != null && me != null) game.touch(pid)
        val ranked = s.ranked
        val o = JSONObject()
            .put("phase", s.phase.name)
            .put("index", s.index)
            .put("total", s.count)
            .put("remainingMs", (s.phaseEndsAt - System.currentTimeMillis()).coerceAtLeast(0))
            .put("phaseMs", s.phaseMs)
            .put("category", s.category.label)
            .put("level", s.level.label)
            .put("joined", me != null)
            .put("full", me == null && s.players.size >= QuizGame.MAX_PLAYERS)
            .put("avatars", JSONArray(QuizGame.AVATARS))
            .put("takenAvatars", JSONArray(s.players.filter { it.id != pid }.map { it.avatar }))
        val q = s.question
        if (q != null && s.phase != QuizPhase.LOBBY && s.phase != QuizPhase.FINISHED) {
            val qo = JSONObject()
                .put("text", q.text)
                .put("category", q.category.label)
                .put("emoji", q.category.emoji)
            if (s.phase != QuizPhase.GET_READY) qo.put("options", JSONArray(q.options))
            if (s.phase == QuizPhase.REVEAL || s.phase == QuizPhase.SCOREBOARD) qo.put("correct", q.correct)
            o.put("question", qo)
        }
        o.put("answeredCount", s.players.count { it.choice != null })
        o.put("playerCount", s.players.size)
        if (me != null) {
            o.put(
                "me",
                JSONObject()
                    .put("name", me.name)
                    .put("avatar", me.avatar)
                    .put("color", String.format("#%06X", me.color and 0xFFFFFF))
                    .put("score", me.score)
                    .put("streak", me.streak)
                    .put("choice", me.choice ?: -1)
                    .put("lastCorrect", me.lastCorrect?.let { if (it) 1 else 0 } ?: -1)
                    .put("lastGain", me.lastGain)
                    .put("rank", ranked.indexOfFirst { it.id == me.id } + 1),
            )
        }
        o.put("players", JSONArray(ranked.map { p ->
            JSONObject().put("name", p.name).put("avatar", p.avatar).put("score", p.score)
                .put("color", String.format("#%06X", p.color and 0xFFFFFF))
        }))
        return o
    }

    private fun respondJson(out: java.io.OutputStream, json: JSONObject, code: Int = 200) =
        respond(out, code, "application/json; charset=utf-8", json.toString().toByteArray(Charsets.UTF_8))

    private fun respond(out: java.io.OutputStream, code: Int, type: String, body: ByteArray) {
        val reason = when (code) { 200 -> "OK"; 204 -> "No Content"; 404 -> "Not Found"; 409 -> "Conflict"; else -> "OK" }
        val head = "HTTP/1.1 $code $reason\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.write(body)
        out.flush()
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) return buf.toString("UTF-8").trimEnd('\r')
            if (buf.size() > 8_192) return null
            buf.write(b)
        }
    }

    private fun readBytes(input: InputStream, n: Int): ByteArray {
        val out = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = input.read(out, read, n - read)
            if (r < 0) break
            read += r
        }
        return if (read == n) out else out.copyOf(read)
    }

    private fun parseQuery(q: String): Map<String, String> =
        q.split('&').filter { it.contains('=') }.associate {
            val k = it.substringBefore('=')
            val v = it.substringAfter('=')
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }

    companion object {
        private const val TAG = "LiteDroidQuiz"
        private val PORTS = 8765..8775

        /** Yerel ağdaki IPv4 adresi (Wi-Fi ya da Ethernet). */
        fun localIp(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .sortedBy { iface ->
                    val n = iface.name.lowercase()
                    when {
                        n.startsWith("wlan") -> 0
                        n.startsWith("eth") -> 1
                        else -> 2
                    }
                }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
