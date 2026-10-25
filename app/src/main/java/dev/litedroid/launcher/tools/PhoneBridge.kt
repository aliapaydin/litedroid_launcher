package dev.litedroid.launcher.tools

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import dev.litedroid.launcher.MainActivity
import dev.litedroid.launcher.overlay.RemoteKeyService
import dev.litedroid.launcher.quiz.QuizServer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/** Tarayıcıyı bir adresle açma isteği (telefondan gelen bağlantı gibi). */
object BrowserLaunch {
    val pending = MutableStateFlow<String?>(null)
}

/**
 * Telefondan TV'ye gönder: aynı Wi-Fi'deki telefon QR'ı okutunca açılan sayfadan yazı ya da bağlantı
 * gönderir. Bağlantı LiteDroid tarayıcısında açılır. Yazı, o an açık olan LiteDroid yazı penceresine
 * ya da (başka bir uygulamadaysan) odaklı yazı kutusuna yazılır. Sunucu son kullanımdan 30 dakika
 * sonra kendiliğinden kapanır.
 */
object PhoneBridge {
    private const val TAG = "LiteDroidPhone"
    private const val IDLE_MS = 30 * 60_000L

    /** LiteDroid'in açık yazı pencerelerine giden metinler. */
    val text = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Şu an kaç LiteDroid yazı penceresi telefondan yazı bekliyor. */
    @Volatile var listeners = 0

    /** Son gelen (pencerede gösterilir). */
    val last = mutableStateOf<String?>(null)

    private var server: ServerSocket? = null
    private var pool = Executors.newFixedThreadPool(2)
    @Volatile private var running = false
    @Volatile private var lastUse = 0L
    private var port = 0
    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private val idleStop = object : Runnable {
        override fun run() {
            if (System.currentTimeMillis() - lastUse >= IDLE_MS) stop() else main.postDelayed(this, 60_000)
        }
    }

    val url: String? get() = if (!running) null else QuizServer.localIp()?.let { "http://$it:$port" }

    fun start(context: Context) {
        app = context.applicationContext
        lastUse = System.currentTimeMillis()
        if (running) return
        if (pool.isShutdown) pool = Executors.newFixedThreadPool(2)
        for (p in 8791..8799) {
            val s = runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(p)) } }.getOrNull() ?: continue
            server = s
            port = p
            running = true
            Thread({
                while (running) {
                    val socket = runCatching { s.accept() }.getOrNull() ?: break
                    pool.execute { runCatching { handle(socket) } }
                }
            }, "phone-bridge").apply { isDaemon = true }.start()
            main.removeCallbacks(idleStop)
            main.postDelayed(idleStop, 60_000)
            Log.i(TAG, "phone bridge on $url")
            return
        }
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        server = null
        pool.shutdownNow()
        main.removeCallbacks(idleStop)
    }

    private fun handle(socket: Socket) = socket.use { s ->
        s.soTimeout = 10_000
        val input = s.getInputStream().bufferedReader(Charsets.UTF_8)
        val request = input.readLine() ?: return
        var length = 0
        while (true) {
            val h = input.readLine() ?: break
            if (h.isEmpty()) break
            if (h.startsWith("Content-Length:", ignoreCase = true)) length = h.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val out = s.getOutputStream()
        val path = request.split(" ").getOrNull(1) ?: "/"
        if (request.startsWith("POST") && path.startsWith("/send")) {
            val body = CharArray(length.coerceAtMost(20_000)).let { buf ->
                var read = 0
                while (read < buf.size) {
                    val n = input.read(buf, read, buf.size - read)
                    if (n < 0) break
                    read += n
                }
                String(buf, 0, read)
            }
            val params = body.split('&').associate { kv ->
                val (k, v) = kv.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                k to URLDecoder.decode(v, "UTF-8")
            }
            val value = params["t"].orEmpty().trim()
            val result = if (value.isEmpty()) "Boş" else deliver(value, params["m"] == "link")
            lastUse = System.currentTimeMillis()
            val bytes = result.toByteArray(Charsets.UTF_8)
            out.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(bytes)
        } else {
            val bytes = PAGE.toByteArray(Charsets.UTF_8)
            out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(bytes)
        }
        out.flush()
    }

    /** Gelen yazı ya da bağlantıyı yerine ulaştırır; telefona gösterilecek kısa sonucu döner. */
    private fun deliver(value: String, asLink: Boolean): String {
        last.value = if (asLink) "🌐 $value" else "📝 " + value.take(60)
        if (asLink) {
            val u = if (value.startsWith("http://") || value.startsWith("https://")) value else "https://$value"
            BrowserLaunch.pending.value = u
            main.post {
                runCatching {
                    app.startActivity(Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_BROWSER, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            return "TV'de açılıyor ✓"
        }
        // LiteDroid'de açık bir yazı penceresi varsa oraya
        if (listeners > 0 && text.tryEmit(value)) return "Yazıldı ✓"
        // Başka bir uygulamadaysan odaklı yazı kutusuna (erişilebilirlik servisiyle)
        val svc = RemoteKeyService.instance ?: return "TV'de açık bir yazı kutusu yok. Önce TV'de yazı kutusunu seç."
        val latch = java.util.concurrent.CountDownLatch(1)
        var ok = false
        main.post {
            ok = svc.typeIntoFocused(value)
            latch.countDown()
        }
        latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
        return if (ok) "Yazıldı ✓" else "TV'de odaklı bir yazı kutusu bulunamadı. Kutuyu seçip tekrar gönder."
    }

    private val PAGE = """<!doctype html><html lang="tr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>LiteDroid · Telefondan gönder</title>
<style>*{box-sizing:border-box}body{margin:0;font-family:system-ui,-apple-system,sans-serif;background:#0b0b1a;color:#fff;padding:20px}
h1{font-size:22px;margin:4px 0 4px}p{color:#aab;margin:0 0 16px;font-size:14px;line-height:1.45}
textarea{width:100%;min-height:120px;border-radius:16px;border:1px solid #334;background:#16162a;color:#fff;font-size:18px;padding:14px;outline:none}
.row{display:flex;gap:10px;margin-top:12px}button{flex:1;border:0;border-radius:14px;padding:16px 10px;font-size:16px;font-weight:700;color:#fff}
.t{background:#6b4dff}.l{background:#2a7fff}#s{margin-top:14px;font-size:15px;min-height:22px;color:#7cf0a8}
small{display:block;margin-top:24px;color:#667;font-size:12px}</style></head><body>
<h1>📲 Telefondan TV'ye gönder</h1>
<p>Yazdığını TV'ye gönder: şifreler, arama kelimeleri ya da açmak istediğin bir web adresi. Kumandayla harf harf yazmaya gerek yok.</p>
<textarea id="t" placeholder="Buraya yaz ya da yapıştır…" autofocus></textarea>
<div class="row"><button class="t" onclick="send('text')">📝 Yazı olarak gönder</button><button class="l" onclick="send('link')">🌐 TV'de aç</button></div>
<div id="s"></div>
<small>Yazı, TV'de seçili olan yazı kutusuna gider. Bağlantı LiteDroid tarayıcısında açılır. Bu sayfa yalnızca ev ağında çalışır.</small>
<script>
function send(m){var t=document.getElementById('t').value;if(!t.trim())return;var s=document.getElementById('s');s.style.color='#aab';s.textContent='Gönderiliyor…';
fetch('/send',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'m='+m+'&t='+encodeURIComponent(t)})
.then(function(r){return r.text()}).then(function(x){s.style.color=x.indexOf('✓')>=0?'#7cf0a8':'#ffb4a8';s.textContent=x;if(x.indexOf('✓')>=0)document.getElementById('t').value='';})
.catch(function(){s.style.color='#ffb4a8';s.textContent="TV'ye ulaşılamadı. Aynı Wi-Fi'de misin?";});}
</script></body></html>"""
}
