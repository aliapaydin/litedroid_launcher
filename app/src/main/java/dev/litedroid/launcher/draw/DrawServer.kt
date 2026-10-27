package dev.litedroid.launcher.draw

import android.content.Context
import android.util.Log
import dev.litedroid.launcher.quiz.QuizGame
import dev.litedroid.launcher.quiz.QuizServer
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * Çiz-Bil'in telefon sunucusu (Bilgi Yarışması'nınkiyle aynı yapı). Telefon [assets/draw/index.html]'i açar,
 * durumu yarım saniyede bir sorar; çizen çizgilerini, diğerleri tahminlerini gönderir.
 *
 * Uç noktalar:
 *   GET  /                 telefon arayüzü
 *   GET  /api/state?pid=   oyunun o oyuncuya göre durumu
 *   POST /api/join         {name, avatar, pid?} → {pid}
 *   POST /api/choose       {pid, round, index}
 *   POST /api/draw         {pid, round, seq, ops:[{t:"p",s,c,w,p:[x,y,…]} | {t:"u"} | {t:"x"}]}
 *   POST /api/guess        {pid, text} → {result, gain}
 */
class DrawServer(context: Context, private val game: DrawGame) {
    private val app = context.applicationContext
    private val pool = Executors.newCachedThreadPool()
    private var server: ServerSocket? = null
    @Volatile private var running = false
    private val page: ByteArray by lazy {
        runCatching { app.assets.open("draw/index.html").use { it.readBytes() } }.getOrDefault("LiteDroid".toByteArray())
    }

    var port: Int = 0
        private set

    val url: String? get() = QuizServer.localIp()?.let { "http://$it:$port" }

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
            Thread({ acceptLoop(s) }, "draw-server").apply { isDaemon = true }.start()
            Log.i(TAG, "draw server on port $p, url=$url")
            return true
        }
        Log.w(TAG, "no free port for draw server")
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
                        length = line.substring(i + 1).trim().toIntOrNull()?.coerceIn(0, 65_536) ?: 0
                    }
                }
                val body = if (length > 0) readBytes(input, length).toString(Charsets.UTF_8) else ""
                val path = target.substringBefore('?')
                val query = parseQuery(target.substringAfter('?', ""))
                val out = sock.getOutputStream()
                val o = if (method == "POST") runCatching { JSONObject(body) }.getOrDefault(JSONObject()) else JSONObject()
                when {
                    method == "GET" && (path == "/" || path == "/index.html") -> respond(out, 200, "text/html; charset=utf-8", page)
                    method == "GET" && path == "/api/state" -> respondJson(out, stateFor(query["pid"]))
                    method == "POST" && path == "/api/join" -> {
                        val p = game.join(o.optString("name"), o.optString("avatar", QuizGame.AVATARS.first()), o.optString("pid").takeIf { it.isNotBlank() })
                        if (p == null) respondJson(out, JSONObject().put("error", "full"), 409)
                        else respondJson(out, JSONObject().put("pid", p.id).put("name", p.name))
                    }
                    method == "POST" && path == "/api/choose" ->
                        respondJson(out, JSONObject().put("ok", game.choose(o.optString("pid"), o.optInt("round", -1), o.optInt("index", -1))))
                    method == "POST" && path == "/api/draw" -> {
                        val ok = game.draw(o.optString("pid"), o.optInt("round", -1), o.optInt("seq", -1), o.optJSONArray("ops") ?: JSONArray())
                        respondJson(out, JSONObject().put("ok", ok))
                    }
                    method == "POST" && path == "/api/guess" -> {
                        val (r, gain) = game.guess(o.optString("pid"), o.optString("text"))
                        respondJson(out, JSONObject().put("result", r.name).put("gain", gain))
                    }
                    method == "POST" && path == "/api/leave" -> {
                        game.remove(o.optString("pid"))
                        respondJson(out, JSONObject().put("ok", true))
                    }
                    path == "/favicon.ico" -> respond(out, 204, "text/plain", ByteArray(0))
                    else -> respond(out, 404, "text/plain; charset=utf-8", "Not found".toByteArray())
                }
            }.onFailure { Log.d(TAG, "request failed: ${it.message}") }
        }
    }

    /** Oyuncuya özel durum: kelimeyi yalnızca çizen (ve bilen) görür. */
    private fun stateFor(pid: String?): JSONObject {
        val s = game.state.value
        val me = s.players.firstOrNull { it.id == pid }
        if (pid != null && me != null) game.touch(pid)
        val ranked = s.ranked
        val now = System.currentTimeMillis()
        val drawer = s.drawer
        val amDrawer = me != null && !s.isAi && me.id == s.drawerId
        val o = JSONObject()
            .put("phase", s.phase.name)
            .put("round", s.round)
            .put("total", s.totalRounds)
            .put("remainingMs", (s.phaseEndsAt - now).coerceAtLeast(0))
            .put("phaseMs", s.phaseMs)
            .put("joined", me != null)
            .put("full", me == null && s.players.size >= DrawGame.MAX_PLAYERS)
            .put("avatars", JSONArray(QuizGame.AVATARS))
            .put("takenAvatars", JSONArray(s.players.filter { it.id != pid }.map { it.avatar }))
            .put("isAi", s.isAi)
            .put("role", if (me == null) "none" else if (amDrawer) "drawer" else "guesser")
            .put("drawSeconds", s.drawSeconds)
            .put("roundsEach", s.roundsEach)
            .put("guessedCount", s.guessers.count { it.guessed })
            .put("guesserCount", s.guessers.size)
            .put("note", s.note ?: "")
        if (drawer != null) o.put("drawerName", drawer.name).put("drawerAvatar", drawer.avatar)
        val w = s.word
        if (w != null) {
            o.put("pattern", s.pattern(now))
            o.put("letters", w.label.count { it != ' ' })
            if (amDrawer || s.phase == DrawPhase.REVEAL || me?.guessed == true) o.put("word", w.label)
        }
        if (amDrawer && s.phase == DrawPhase.CHOOSE) o.put("choices", JSONArray(s.choices.map { it.label }))
        if (me != null) {
            o.put(
                "me",
                JSONObject()
                    .put("name", me.name)
                    .put("avatar", me.avatar)
                    .put("color", String.format("#%06X", me.color and 0xFFFFFF))
                    .put("score", me.score)
                    .put("guessed", me.guessed)
                    .put("lastGain", me.lastGain)
                    .put("rank", ranked.indexOfFirst { it.id == me.id } + 1),
            )
        }
        o.put("players", JSONArray(ranked.map { p ->
            JSONObject().put("name", p.name).put("avatar", p.avatar).put("score", p.score).put("guessed", p.guessed)
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
        private const val TAG = "LiteDroidDraw"
        // Bilgi Yarışması ile aynı aralık: ikisi aynı anda açık olmaz
        private val PORTS = 8765..8775
    }
}
