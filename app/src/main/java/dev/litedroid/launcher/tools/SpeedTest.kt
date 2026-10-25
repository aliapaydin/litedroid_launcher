package dev.litedroid.launcher.tools

import android.content.Context
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.onRemoteSelect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * İnternet hız testi: Cloudflare'in herkese açık hız testi sunucularıyla (speed.cloudflare.com).
 * Gecikme ve dalgalanma (ping/jitter), 4 paralel bağlantıyla indirme, 3 paralel bağlantıyla yükleme.
 */
object SpeedTester {
    private const val TAG = "LiteDroidSpeed"
    private const val BASE = "https://speed.cloudflare.com"

    /** Yanıtı veren Cloudflare veri merkezi (ör. IST). */
    @Volatile var colo: String? = null
        private set

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 10_000
        useCaches = false
        setRequestProperty("User-Agent", "LiteDroid-Launcher")
    }

    /** Ortanca gecikme ve dalgalanma (ms). */
    suspend fun ping(): Pair<Double, Double> = withContext(Dispatchers.IO) {
        val times = ArrayList<Double>()
        repeat(9) { i ->
            val t0 = System.nanoTime()
            val c = open("$BASE/__down?bytes=0")
            try {
                c.responseCode
                c.getHeaderField("cf-ray")?.substringAfterLast('-')?.takeIf { it.length in 3..4 }?.let { colo = it }
                c.inputStream.use { it.readBytes() }
            } finally {
                c.disconnect()
            }
            // İlk istek bağlantı kurulumunu da içerir, sayılmaz
            if (i > 0) times += (System.nanoTime() - t0) / 1e6
        }
        val sorted = times.sorted()
        val median = sorted[sorted.size / 2]
        val jitter = times.zipWithNext { a, b -> abs(a - b) }.average()
        median to jitter
    }

    /** [seconds] boyunca indirir; ilerlemeyi Mbps olarak bildirir, sonunda ortalamayı döner. */
    suspend fun download(seconds: Int = 10, onProgress: (Float, Float) -> Unit): Float =
        measure(seconds, parallel = 4, onProgress) { bytes, end ->
            while (System.currentTimeMillis() < end) {
                val c = open("$BASE/__down?bytes=25000000")
                try {
                    c.inputStream.use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (System.currentTimeMillis() < end) {
                            val n = input.read(buf)
                            if (n < 0) break
                            bytes.addAndGet(n.toLong())
                        }
                    }
                } finally {
                    c.disconnect()
                }
            }
        }

    private val payload by lazy { ByteArray(64 * 1024).also { java.util.Random().nextBytes(it) } }

    suspend fun upload(seconds: Int = 8, onProgress: (Float, Float) -> Unit): Float =
        measure(seconds, parallel = 3, onProgress) { bytes, end ->
            while (System.currentTimeMillis() < end) {
                val size = 8 * 1024 * 1024
                val c = open("$BASE/__up").apply {
                    requestMethod = "POST"
                    doOutput = true
                    setFixedLengthStreamingMode(size)
                    setRequestProperty("Content-Type", "application/octet-stream")
                }
                try {
                    c.outputStream.use { out ->
                        var sent = 0
                        while (sent < size && System.currentTimeMillis() < end) {
                            val n = minOf(payload.size, size - sent)
                            out.write(payload, 0, n)
                            sent += n
                            bytes.addAndGet(n.toLong())
                        }
                        if (sent < size) return@measure // süre doldu: bağlantı kesilir
                    }
                    c.responseCode
                } finally {
                    c.disconnect()
                }
            }
        }

    /**
     * Ortak ölçüm: [parallel] bağlantı aynı anda çalışır. İlk 1,5 saniye (TCP ısınması) ortalamaya
     * katılmaz. [onProgress] (anlık Mbps, ilerleme 0..1).
     */
    private suspend fun measure(
        seconds: Int,
        parallel: Int,
        onProgress: (Float, Float) -> Unit,
        worker: suspend (AtomicLong, Long) -> Unit,
    ): Float = coroutineScope {
        val bytes = AtomicLong()
        val start = System.currentTimeMillis()
        val end = start + seconds * 1000L
        val jobs = List(parallel) {
            launch(Dispatchers.IO) { runCatching { worker(bytes, end) }.onFailure { Log.w(TAG, "worker: ${it.message}") } }
        }
        var warmBytes = -1L
        var warmAt = 0L
        var lastBytes = 0L
        var lastAt = start
        var smooth = 0f
        while (isActive && System.currentTimeMillis() < end) {
            delay(200)
            val now = System.currentTimeMillis()
            val b = bytes.get()
            if (warmBytes < 0 && now - start >= 1_500) { warmBytes = b; warmAt = now }
            val inst = ((b - lastBytes) * 8f / ((now - lastAt).coerceAtLeast(1) / 1000f)) / 1e6f
            smooth = if (smooth == 0f) inst else smooth * 0.7f + inst * 0.3f
            lastBytes = b
            lastAt = now
            onProgress(smooth, ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f))
        }
        jobs.forEach { it.cancel() }
        val total = bytes.get()
        if (warmBytes < 0) { warmBytes = 0; warmAt = start }
        val secs = ((System.currentTimeMillis() - warmAt) / 1000f).coerceAtLeast(0.5f)
        ((total - warmBytes) * 8f / secs) / 1e6f
    }
}

private enum class Phase { IDLE, PING, DOWN, UP, DONE, ERROR }

private data class SpeedResult(val ping: Double, val jitter: Double, val down: Float, val up: Float, val colo: String?, val at: Long)

@Composable
fun SpeedTestTool(accent: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var live by remember { mutableFloatStateOf(0f) }
    var progress by remember { mutableFloatStateOf(0f) }
    var ping by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var down by remember { mutableStateOf<Float?>(null) }
    var up by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(loadHistory(context)) }
    var job by remember { mutableStateOf<Job?>(null) }
    val start = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { start.requestFocus() }
    }

    fun run(scope: CoroutineScope) {
        job?.cancel()
        ping = null; down = null; up = null; error = null; live = 0f
        job = scope.launch {
            try {
                phase = Phase.PING
                val p = SpeedTester.ping()
                ping = p
                phase = Phase.DOWN
                val d = SpeedTester.download { mbps, f -> live = mbps; progress = f }
                down = d
                live = 0f
                phase = Phase.UP
                val u = SpeedTester.upload { mbps, f -> live = mbps; progress = f }
                up = u
                live = 0f
                phase = Phase.DONE
                val r = SpeedResult(p.first, p.second, d, u, SpeedTester.colo, System.currentTimeMillis())
                history = (listOf(r) + history).take(5)
                saveHistory(context, history)
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                phase = Phase.ERROR
                error = tx("Test tamamlanamadı: ", "The test couldn't finish: ") + (t.message ?: t.javaClass.simpleName)
            }
        }
    }

    val needle by animateFloatAsState(gaugeFraction(if (phase == Phase.DOWN || phase == Phase.UP) live else (if (phase == Phase.DONE) (down ?: 0f) else 0f)), tween(350), label = "needle")
    Row(Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.CenterVertically) {
        // Gösterge
        Box(Modifier.size(width = 380.dp, height = 300.dp), contentAlignment = Alignment.Center) {
            val color = if (phase == Phase.UP) Color(0xFFB06BFF) else accent
            Canvas(Modifier.size(320.dp)) {
                val stroke = 22.dp.toPx()
                val r = size.minDimension / 2 - stroke
                val tl = Offset(center.x - r, center.y - r)
                drawArc(Color.White.copy(alpha = 0.08f), 150f, 240f, false, tl, Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(Brush.sweepGradient(listOf(color.copy(alpha = 0.4f), color, color), center), 150f, 240f * needle, false, tl, Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
                // Ölçek: 0, 10, 50, 100, 250, 500, 1000
                listOf(0f, 10f, 50f, 100f, 250f, 500f, 1000f).forEach { v ->
                    val a = (150f + 240f * gaugeFraction(v)) * PI.toFloat() / 180f
                    val p1 = Offset(center.x + cos(a) * (r - stroke * 1.1f), center.y + sin(a) * (r - stroke * 1.1f))
                    drawCircle(Color.White.copy(alpha = 0.4f), 2.5f, p1)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val value = when (phase) {
                    Phase.DOWN, Phase.UP -> live
                    Phase.DONE -> down ?: 0f
                    else -> null
                }
                Text(value?.let { mbps(it) } ?: "—", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light)
                Text("Mbps", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    when (phase) {
                        Phase.IDLE -> tx("Hazır", "Ready")
                        Phase.PING -> tx("Gecikme ölçülüyor…", "Measuring latency…")
                        Phase.DOWN -> tx("İndirme ↓", "Download ↓")
                        Phase.UP -> tx("Yükleme ↑", "Upload ↑")
                        Phase.DONE -> tx("Bitti ✓", "Done ✓")
                        Phase.ERROR -> tx("Hata", "Error")
                    },
                    color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        // Sonuçlar
        Column(Modifier.width(420.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(tx("İndirme", "Download"), down?.let { "${mbps(it)} Mbps" } ?: "—", accent)
                Stat(tx("Yükleme", "Upload"), up?.let { "${mbps(it)} Mbps" } ?: "—", Color(0xFFB06BFF))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Ping", ping?.let { "${it.first.toInt()} ms" } ?: "—", Color(0xFF3BE07F))
                Stat("Jitter", ping?.let { "${"%.1f".format(it.second)} ms" } ?: "—", Color(0xFFFFB13D))
            }
            Spacer(Modifier.height(12.dp))
            val d = down
            if (phase == Phase.DONE && d != null) {
                Text(verdict(d), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(tx("Sunucu: Cloudflare ${SpeedTester.colo ?: ""}", "Server: Cloudflare ${SpeedTester.colo ?: ""}"), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
            }
            error?.let {
                Text(it, color = Color(0xFFFFB4A8), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
            val busy = phase == Phase.PING || phase == Phase.DOWN || phase == Phase.UP
            ToolButton(
                if (busy) tx("Durdur", "Stop") else if (phase == Phase.IDLE) tx("▶ Testi başlat", "▶ Start test") else tx("↻ Tekrar test et", "↻ Test again"),
                accent, Modifier.focusRequester(start),
            ) {
                if (busy) {
                    job?.cancel()
                    phase = Phase.IDLE
                    live = 0f
                } else run(scope)
            }
            if (busy) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(300.dp).height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
                    Box(Modifier.width(300.dp * progress).height(4.dp).background(accent))
                }
            }
            if (history.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(tx("ÖNCEKİ TESTLER", "PREVIOUS TESTS"), color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()) }
                history.forEach { r ->
                    Text("${fmt.format(Date(r.at))}  ·  ↓ ${mbps(r.down)}  ↑ ${mbps(r.up)}  ·  ${r.ping.toInt()} ms",
                        color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                }
            }
        }
    }
}

/** 0-1000 Mbps karekök ölçeğinde: düşük hızlarda da ibre belirgin oynar. */
private fun gaugeFraction(mbps: Float) = (sqrt(mbps.coerceIn(0f, 1000f) / 1000f))

private fun mbps(v: Float) = if (v >= 100) v.toInt().toString() else "%.1f".format(v)

private fun verdict(down: Float) = when {
    down >= 50 -> tx("Mükemmel: birden fazla 4K yayın aynı anda rahat oynar.", "Excellent: several 4K streams at once.")
    down >= 25 -> tx("Çok iyi: 4K yayın için yeterli.", "Very good: enough for 4K streaming.")
    down >= 10 -> tx("İyi: Full HD (1080p) rahat oynar, 4K zorlanabilir.", "Good: Full HD plays fine, 4K may struggle.")
    down >= 5 -> tx("Orta: HD (720p) yayın için yeterli.", "Fair: enough for HD (720p).")
    else -> tx("Düşük: yayınlar takılabilir. Modeme yakınlaş ya da kablo kullan.", "Low: streams may buffer. Move closer to the router or use a cable.")
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column(
        Modifier
            .width(205.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(label.uppercase(), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/** Araçlarda ortak düğme. */
@Composable
fun ToolButton(label: String, accent: Color, modifier: Modifier = Modifier, onLongPress: (() -> Unit)? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else accent.copy(alpha = 0.85f))
            .border(1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onClick, onLongPress)
            .focusable()
            .padding(horizontal = 22.dp, vertical = 11.dp),
    ) {
        Text(label, color = if (focused) Color(0xFF14131C) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun loadHistory(ctx: Context): List<SpeedResult> = runCatching {
    val arr = JSONArray(ctx.getSharedPreferences("litedroid_tools", Context.MODE_PRIVATE).getString("speed", "[]"))
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        SpeedResult(o.getDouble("p"), o.getDouble("j"), o.getDouble("d").toFloat(), o.getDouble("u").toFloat(), o.optString("c").ifBlank { null }, o.getLong("t"))
    }
}.getOrDefault(emptyList())

private fun saveHistory(ctx: Context, list: List<SpeedResult>) {
    val arr = JSONArray()
    list.forEach { r -> arr.put(JSONObject().put("p", r.ping).put("j", r.jitter).put("d", r.down.toDouble()).put("u", r.up.toDouble()).put("c", r.colo ?: "").put("t", r.at)) }
    ctx.getSharedPreferences("litedroid_tools", Context.MODE_PRIVATE).edit().putString("speed", arr.toString()).apply()
}
