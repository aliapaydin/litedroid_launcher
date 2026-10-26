package dev.litedroid.launcher.tools

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.overlay.RemoteKeyService
import dev.litedroid.launcher.quiz.QrCode
import dev.litedroid.launcher.quiz.QuizServer
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.onRemoteSelect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Ekran görüntüleri: LiteDroid'in kendi klasöründe saklanır, ayrıca cihazın Resimler/LiteDroid klasörüne kopyalanır. */
object ScreenshotStore {
    private const val TAG = "LiteDroidShot"

    fun dir(ctx: Context) = File(ctx.filesDir, "screenshots").apply { mkdirs() }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.name.endsWith(".jpg") }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun save(ctx: Context, bmp: Bitmap): File {
        val name = "LiteDroid_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val f = File(dir(ctx), name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        // Cihazın galerisine de (dosya yöneticileri ve Google Fotoğraflar görebilsin)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LiteDroid")
                }
                ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                    ctx.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
                }
            }.onFailure { Log.w(TAG, "gallery copy failed: ${it.message}") }
        }
        // En yeni 60 görüntü tutulur
        list(ctx).drop(60).forEach { it.delete() }
        return f
    }

    fun thumbnail(file: File, targetWidth: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= targetWidth) s *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })
    }
}

/**
 * Ekran görüntülerini aynı Wi-Fi'deki telefona veren küçük web sunucusu. Telefon QR'ı okutunca
 * galeri sayfası açılır; her görüntü dokununca tam boy açılır ve kaydedilebilir.
 */
class ShotShareServer(private val context: Context) {
    private var server: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(3)
    @Volatile private var running = false
    var port = 0
        private set

    val url: String? get() = if (port == 0) null else QuizServer.localIp()?.let { "http://$it:$port" }

    fun start() {
        if (running) return
        for (p in 8781..8790) {
            val s = runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(p)) } }.getOrNull() ?: continue
            server = s
            port = p
            running = true
            Thread({
                while (running) {
                    val socket = runCatching { s.accept() }.getOrNull() ?: break
                    pool.execute { runCatching { handle(socket) } }
                }
            }, "shot-server").apply { isDaemon = true }.start()
            return
        }
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        pool.shutdownNow()
    }

    private fun handle(socket: Socket) = socket.use { s ->
        s.soTimeout = 10_000
        val line = s.getInputStream().bufferedReader().readLine() ?: return
        val path = URLDecoder.decode(line.split(" ").getOrNull(1) ?: "/", "UTF-8")
        val out = s.getOutputStream()
        if (path.startsWith("/f/")) {
            val f = File(ScreenshotStore.dir(context), path.removePrefix("/f/").substringBefore('?'))
            if (!f.exists() || f.parentFile != ScreenshotStore.dir(context)) {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                return
            }
            out.write("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: ${f.length()}\r\nContent-Disposition: inline; filename=\"${f.name}\"\r\nConnection: close\r\n\r\n".toByteArray())
            f.inputStream().use { it.copyTo(out) }
        } else {
            val items = ScreenshotStore.list(context).joinToString("") {
                "<a href=\"/f/${it.name}\"><img loading=\"lazy\" src=\"/f/${it.name}\"><span>${it.name.removePrefix("LiteDroid_").removeSuffix(".jpg")}</span></a>"
            }
            val html = """<!doctype html><html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>LiteDroid ekran görüntüleri</title><style>body{margin:0;font-family:system-ui,sans-serif;background:#0b0b1a;color:#fff}
h1{font-size:20px;padding:16px 16px 4px;margin:0}p{margin:0 16px 12px;color:#aab}main{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:10px;padding:0 12px 24px}
a{color:#fff;text-decoration:none;background:#1a1a33;border-radius:12px;overflow:hidden;display:block}img{width:100%;display:block;aspect-ratio:16/9;object-fit:cover}
span{display:block;font-size:11px;padding:6px 8px;color:#bbc}</style></head><body><h1>📸 LiteDroid ekran görüntüleri</h1>
<p>Görüntüye dokun, açılınca basılı tutup kaydet.</p><main>$items</main></body></html>"""
            val bytes = html.toByteArray(Charsets.UTF_8)
            out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(bytes)
        }
        out.flush()
    }
}

@Composable
fun ScreenshotsTool(accent: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val server = remember { ShotShareServer(context.applicationContext) }
    var url by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        server.start()
        url = server.url
        onDispose { server.stop() }
    }
    var refresh by remember { mutableIntStateOf(0) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    LaunchedEffect(refresh) { files = withContext(Dispatchers.IO) { ScreenshotStore.list(context) } }
    // Görüntü alınınca liste kendiliğinden yenilensin
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            val n = withContext(Dispatchers.IO) { ScreenshotStore.list(context).size }
            if (n != files.size) refresh++
        }
    }
    var opened by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { first.requestFocus() }
    }

    val viewing = opened
    if (viewing != null) {
        ShotViewer(viewing, url, accent, onClose = { opened = null }, onDelete = {
            viewing.delete()
            opened = null
            refresh++
        })
        return
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(
                tx("Herhangi bir uygulamadayken kumandada Geri'ye iki kez bas → yüzen menüde 📸 Görüntü. Görüntüler burada ve cihazın Resimler/LiteDroid klasöründe saklanır.",
                    "In any app, double-press Back → 📸 Capture in the floating menu. Shots are kept here and in Pictures/LiteDroid on the device."),
                color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 17.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolButton("📸 " + tx("3 sn sonra bu ekranı çek", "Capture this screen in 3 s"), accent, if (files.isEmpty()) Modifier.focusRequester(first) else Modifier) {
                    val svc = RemoteKeyService.instance
                    message = when {
                        svc == null -> tx("Önce kumanda kısayolunu (erişilebilirlik) aç: Ayarlar → Kumanda → Kumanda kısayolu.", "Turn on the remote shortcut (accessibility) first.")
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> tx("Bu özellik Android 11 ve üstünde çalışır.", "Needs Android 11 or newer.")
                        else -> {
                            svc.captureScreen(3_000)
                            scope.launch {
                                delay(4_500)
                                refresh++
                            }
                            tx("3 saniye sonra çekiliyor…", "Capturing in 3 seconds…")
                        }
                    }
                }
                message?.let { Text(it, color = accent, fontSize = 12.sp) }
            }
            Spacer(Modifier.height(12.dp))
            if (files.isEmpty()) {
                Text(tx("Henüz ekran görüntüsü yok.", "No screenshots yet."), color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
            } else {
                LazyVerticalGrid(GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(files, key = { _, f -> f.name }) { i, f ->
                        ShotThumb(f, accent, if (i == 0) Modifier.focusRequester(first) else Modifier) { opened = f }
                    }
                }
            }
        }
        // Telefona aktarma
        Column(
            Modifier
                .width(230.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(tx("TELEFONA AL", "GET ON YOUR PHONE"), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(10.dp))
            val u = url
            if (u != null) {
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp)) { QrCode(u, 150.dp) }
                Spacer(Modifier.height(8.dp))
                Text(tx("Aynı Wi-Fi'deki telefonla okut, tüm görüntüler açılsın.", "Scan with a phone on the same Wi-Fi to see them all."), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                Text(u, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
            } else {
                Text(tx("Ağ bağlantısı yok.", "No network."), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(tx("Netflix gibi korumalı içerikler siyah çıkar (Android'in koruması).", "Protected content like Netflix comes out black (Android's protection)."), color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun ShotThumb(file: File, accent: Color, modifier: Modifier, onOpen: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bmp by produceState<ImageBitmap?>(null, file.name) {
        value = withContext(Dispatchers.IO) { ScreenshotStore.thumbnail(file, 320)?.asImageBitmap() }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(3.dp, if (focused) accent else Color.Transparent, RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onOpen)
            .focusable(),
    ) {
        bmp?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Text(
            file.name.removePrefix("LiteDroid_").removeSuffix(".jpg").replace('_', ' '),
            color = Color.White, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ShotViewer(file: File, url: String?, accent: Color, onClose: () -> Unit, onDelete: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(null, file.name) {
        value = withContext(Dispatchers.IO) { ScreenshotStore.thumbnail(file, 1280)?.asImageBitmap() }
    }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { fr.requestFocus() }
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
            bmp?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        }
        Column(Modifier.width(200.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (url != null) {
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)) { QrCode("$url/f/${file.name}", 140.dp) }
                Text(tx("Telefonla okut, görüntü açılsın", "Scan to open it on your phone"), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            ToolButton(tx("Kapat", "Close"), accent, Modifier.focusRequester(fr), onClick = onClose)
            ToolButton(tx("Sil", "Delete"), Color(0xFFE0525C), onClick = onDelete)
        }
    }
}
