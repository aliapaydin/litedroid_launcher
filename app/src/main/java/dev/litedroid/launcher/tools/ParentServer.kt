package dev.litedroid.launcher.tools

import android.content.Context
import android.util.Log
import dev.litedroid.launcher.data.Parental
import dev.litedroid.launcher.data.Profiles
import dev.litedroid.launcher.quiz.QuizServer
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * Ebeveyn telefon paneli: evdeki Wi-Fi'da çalışan küçük bir sunucu. Telefon Ebeveyn denetimindeki QR'ı
 * bir kez okutup eşleşir (gizli bir anahtar alır); sonra her işlemde bu anahtar ve bir yetişkin PIN'i
 * istenir. Aynı ağdaki başka biri (çocuğun tableti) eşleşmeden ya da PIN'siz bir şey yapamaz.
 *
 * Uç noktalar (hepsi POST, JSON):
 *   /api/pair    {code}                     → {token}
 *   /api/state   {token, pin}               → çocukların bugünkü durumu
 *   /api/action  {token, pin, child, action, minutes?, which?}
 *                action: add | lock | unlock | approve | decline | daily
 */
object ParentServer {
    private const val TAG = "LiteDroidParent"
    private val PORTS = 8801..8809
    private const val PREFS = "litedroid_parent_phones"

    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()
    @Volatile private var running = false
    private lateinit var app: Context
    @Volatile var port = 0
        private set

    /** Bir kez kullanılan eşleşme kodu (10 dakika geçerli). */
    @Volatile private var pairCode: String? = null
    @Volatile private var pairUntil = 0L
    private val wrongPins = HashMap<String, Pair<Int, Long>>()

    val url: String? get() = if (port == 0) null else QuizServer.localIp()?.let { "http://$it:$port" }

    @Synchronized
    fun start(ctx: Context) {
        app = ctx.applicationContext
        if (running) return
        for (p in PORTS) {
            val s = runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(p)) } }.getOrNull() ?: continue
            server = s
            port = p
            running = true
            Thread({
                while (running) {
                    val socket = runCatching { s.accept() }.getOrNull() ?: break
                    runCatching { pool.execute { handle(socket) } }.onFailure { runCatching { socket.close() } }
                }
            }, "parent-server").apply { isDaemon = true }.start()
            Log.i(TAG, "parent panel on $p")
            return
        }
    }

    /** Eşleşme için QR adresi (her açılışta yeni kod). */
    fun newPairUrl(ctx: Context): String? {
        start(ctx)
        val code = (100000 + SecureRandom().nextInt(900000)).toString()
        pairCode = code
        pairUntil = System.currentTimeMillis() + 10 * 60_000L
        return url?.let { "$it/?pair=$code" }
    }

    // ---- Eşleşmiş telefonlar ----

    data class Phone(val token: String, val name: String, val at: Long)

    fun phones(ctx: Context): List<Phone> {
        val arr = runCatching { JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { Phone(it.optString("t"), it.optString("n"), it.optLong("at")) }
    }

    private fun savePhones(ctx: Context, list: List<Phone>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("list", JSONArray().apply { list.forEach { put(JSONObject().put("t", it.token).put("n", it.name).put("at", it.at)) } }.toString())
            .apply()
    }

    fun forget(ctx: Context, token: String) = savePhones(ctx, phones(ctx).filter { it.token != token })
    fun forgetAll(ctx: Context) = savePhones(ctx, emptyList())

    // ---- HTTP ----

    private fun handle(socket: Socket) = socket.use { sock ->
        runCatching {
            sock.soTimeout = 8_000
            val input = BufferedInputStream(sock.getInputStream())
            val request = readLine(input) ?: return@runCatching
            val parts = request.split(" ")
            if (parts.size < 2) return@runCatching
            var length = 0
            var agent = ""
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val i = line.indexOf(':')
                if (i <= 0) continue
                val name = line.substring(0, i).trim()
                val value = line.substring(i + 1).trim()
                if (name.equals("content-length", true)) length = value.toIntOrNull()?.coerceIn(0, 8_192) ?: 0
                if (name.equals("user-agent", true)) agent = value
            }
            val body = if (length > 0) readBytes(input, length).toString(Charsets.UTF_8) else ""
            val path = parts[1].substringBefore('?')
            val out = sock.getOutputStream()
            if (parts[0] == "GET" && (path == "/" || path == "/index.html")) {
                val page = runCatching { app.assets.open("parent/index.html").use { it.readBytes() } }.getOrDefault("LiteDroid".toByteArray())
                respond(out, 200, "text/html; charset=utf-8", page)
                return@runCatching
            }
            if (parts[0] != "POST") {
                respond(out, 404, "text/plain", "Not found".toByteArray())
                return@runCatching
            }
            val o = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
            val res = when (path) {
                "/api/pair" -> pair(o.optString("code"), agent)
                "/api/state" -> guarded(o) { state() }
                "/api/action" -> guarded(o) { action(o) }
                else -> JSONObject().put("error", "notfound")
            }
            respond(out, 200, "application/json; charset=utf-8", res.toString().toByteArray(Charsets.UTF_8))
        }.onFailure { Log.d(TAG, "request failed: ${it.message}") }
    }

    @Synchronized
    private fun pair(code: String, agent: String): JSONObject {
        val ok = code.isNotEmpty() && code == pairCode && System.currentTimeMillis() < pairUntil
        if (!ok) return JSONObject().put("error", "pair")
        pairCode = null
        val token = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val name = when {
            agent.contains("iPhone") -> "iPhone"
            agent.contains("iPad") -> "iPad"
            agent.contains("Android") -> Regex("Android [^;]*; ([^;)]+)").find(agent)?.groupValues?.get(1)?.trim()?.take(24) ?: "Android"
            else -> "Telefon"
        }
        savePhones(app, phones(app) + Phone(token, name, System.currentTimeMillis()))
        Parental.bumpExternal()
        return JSONObject().put("token", token)
    }

    /** Eşleşmiş telefon + yetişkin PIN'i (PIN'i olan yetişkin yoksa yalnızca eşleşme). 5 yanlış PIN'de 5 dk bekleme. */
    @Synchronized
    private fun guarded(o: JSONObject, block: () -> JSONObject): JSONObject {
        val token = o.optString("token")
        if (token.isEmpty() || phones(app).none { it.token == token }) return JSONObject().put("error", "unpaired")
        val (fails, until) = wrongPins[token] ?: (0 to 0L)
        if (System.currentTimeMillis() < until) return JSONObject().put("error", "wait").put("seconds", (until - System.currentTimeMillis()) / 1000)
        val adults = Profiles.adults.filter { it.hasPin }
        val pin = o.optString("pin")
        if (adults.isNotEmpty() && adults.none { Profiles.checkPin(it, pin) }) {
            val n = fails + 1
            wrongPins[token] = if (n >= 5) 0 to System.currentTimeMillis() + 5 * 60_000L else n to 0L
            return JSONObject().put("error", "pin").put("pinRequired", true)
        }
        wrongPins.remove(token)
        return block().put("pinRequired", adults.isNotEmpty())
    }

    /** Panel 5 sn'de bir yenilenir: uygulama adları her seferinde paket yöneticisine sorulmaz. */
    private val labelCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun state(): JSONObject {
        val kids = JSONArray()
        Profiles.children.forEach { c ->
            val rules = Parental.rules(app, c.id)
            val reason = Parental.lockReason(app, c.id)
            val req = Parental.pendingRequest(app, c.id)
            val labels = app.packageManager
            kids.put(
                JSONObject()
                    .put("id", c.id).put("name", c.name).put("emoji", c.emoji).put("color", String.format("#%06X", c.color and 0xFFFFFF))
                    .put("active", Profiles.activeId == c.id)
                    .put("usedMs", Parental.usedMs(app, c.id))
                    .put("limitMin", Parental.limitMin(app, c.id))
                    .put("bonusMin", Parental.bonusMin(app, c.id))
                    .put("remainingMs", Parental.remainingMs(app, c.id) ?: -1)
                    .put("locked", reason != null)
                    .put("reason", reason?.let { Parental.describe(it, rules) } ?: "")
                    .put("request", req?.second ?: 0)
                    .put("weekdayMin", rules.weekdayMin)
                    .put("weekendMin", rules.weekendMin)
                    .put("hours", if (rules.hoursOn) "${Parental.hhmm(rules.fromMin)} - ${Parental.hhmm(rules.toMin)}" else "")
                    .put("apps", JSONArray(Parental.appUsage(app, c.id).take(6).map { (pkg, ms) ->
                        val label = if (pkg == app.packageName) "LiteDroid" else labelCache.getOrPut(pkg) {
                            runCatching { labels.getApplicationLabel(labels.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                        }
                        JSONObject().put("label", label).put("ms", ms)
                    }))
            )
        }
        return JSONObject()
            .put("children", kids)
            .put("dailyOptions", JSONArray(Parental.DAILY_OPTIONS))
            .put("addOptions", JSONArray(Parental.ADD_OPTIONS))
            .put("device", deviceInfo())
    }

    /** Panelin altında: hangi TV, hangi Android, hangi LiteDroid sürümü, açık profil. */
    private fun deviceInfo(): JSONObject {
        val name = runCatching { android.provider.Settings.Global.getString(app.contentResolver, android.provider.Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
        val maker = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = android.os.Build.MODEL
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: ""
        val active = Profiles.activeProfile
        return JSONObject()
            .put("name", name ?: model)
            .put("model", if (model.startsWith(maker, true)) model else "$maker $model")
            .put("android", android.os.Build.VERSION.RELEASE)
            .put("version", version)
            .put("ip", QuizServer.localIp() ?: "")
            .put("profiles", Profiles.list.value.size)
            .put("activeName", active?.name ?: "")
            .put("activeEmoji", active?.emoji ?: "")
            .put("activeKid", active?.isChild == true)
    }

    private fun action(o: JSONObject): JSONObject {
        val id = o.optString("child")
        val child = Profiles.get(id)?.takeIf { it.isChild } ?: return JSONObject().put("error", "child")
        val minutes = o.optInt("minutes")
        when (o.optString("action")) {
            "add" -> if (minutes in 1..600) Parental.addBonus(app, child.id, minutes)
            "lock" -> Parental.lockNow(app, child.id)
            "unlock" -> Parental.unlock(app, child.id)
            "approve" -> Parental.pendingRequest(app, child.id)?.let { Parental.addBonus(app, child.id, it.second) }
            "decline" -> Parental.clearRequest(app, child.id)
            "daily" -> {
                val r = Parental.rules(app, child.id)
                if (minutes in Parental.DAILY_OPTIONS) {
                    Parental.setRules(app, child.id, if (o.optString("which") == "weekend") r.copy(weekendMin = minutes) else r.copy(weekdayMin = minutes))
                }
            }
            else -> return JSONObject().put("error", "action")
        }
        return state().put("ok", true)
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: ByteArray) {
        val head = "HTTP/1.1 $code ${if (code == 200) "OK" else "Not Found"}\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
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
}
