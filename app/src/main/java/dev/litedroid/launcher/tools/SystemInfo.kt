package dev.litedroid.launcher.tools

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.data.Updater
import dev.litedroid.launcher.overlay.RemoteKeyService
import dev.litedroid.launcher.quiz.QuizServer
import dev.litedroid.launcher.tx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

data class InfoCard(val icon: String, val title: String, val rows: List<Pair<String, String>>)

/** Cihazın özellikleri: donanım, Android, bellek, ekran, desteklenen video/ses biçimleri, ağ, LiteDroid izinleri. */
object SystemInfo {

    fun collect(ctx: Context): List<InfoCard> {
        val yes = "✓"
        val no = "—"
        val cards = ArrayList<InfoCard>()

        cards += InfoCard("📺", tx("Cihaz", "Device"), listOf(
            tx("Marka", "Brand") to Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            tx("Model", "Model") to Build.MODEL,
            tx("Kod adı", "Codename") to Build.DEVICE,
            tx("Ad", "Name") to (Settings.Global.getString(ctx.contentResolver, "device_name") ?: "—"),
        ))

        val up = SystemClock.elapsedRealtime() / 60_000
        cards += InfoCard("🤖", "Android", listOf(
            tx("Sürüm", "Version") to "Android ${Build.VERSION.RELEASE}",
            "API" to "${Build.VERSION.SDK_INT}",
            tx("Güvenlik yaması", "Security patch") to Build.VERSION.SECURITY_PATCH,
            tx("Derleme", "Build") to Build.ID,
            tx("Açık kalma", "Uptime") to if (up >= 60 * 24) tx("${up / 1440} gün ${up / 60 % 24} sa", "${up / 1440} d ${up / 60 % 24} h") else tx("${up / 60} sa ${up % 60} dk", "${up / 60} h ${up % 60} min"),
        ))

        val freq = runCatching { File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").readText().trim().toLong() / 1000 }.getOrNull()
        cards += InfoCard("⚙️", tx("İşlemci", "Processor"), listOfNotNull(
            if (Build.VERSION.SDK_INT >= 31) tx("Yonga", "SoC") to "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else tx("Donanım", "Hardware") to Build.HARDWARE,
            tx("Çekirdek", "Cores") to "${Runtime.getRuntime().availableProcessors()}",
            freq?.let { tx("En yüksek hız", "Max clock") to "${"%.2f".format(it / 1000f)} GHz" },
            tx("Mimari", "ABI") to (Build.SUPPORTED_ABIS.firstOrNull() ?: "—"),
        ))

        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val data = StatFs(Environment.getDataDirectory().path)
        cards += InfoCard("🧠", tx("Bellek ve depolama", "Memory & storage"), listOf(
            "RAM" to "${gb(mi.totalMem)} " + tx("(boş ${gb(mi.availMem)})", "(${gb(mi.availMem)} free)"),
            tx("Depolama", "Storage") to "${gb(data.totalBytes)} " + tx("(boş ${gb(data.availableBytes)})", "(${gb(data.availableBytes)} free)"),
            tx("Bellek durumu", "Memory state") to if (mi.lowMemory) tx("Düşük ⚠️", "Low ⚠️") else tx("Normal", "Normal"),
        ))

        @Suppress("DEPRECATION")
        val display: Display? = ctx.getSystemService(WindowManager::class.java)?.defaultDisplay
        val mode = display?.mode
        val maxMode = display?.supportedModes?.maxByOrNull { it.physicalWidth * it.physicalHeight * it.refreshRate }
        @Suppress("DEPRECATION")
        val hdr = display?.hdrCapabilities?.supportedHdrTypes?.map {
            when (it) {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
                else -> "HDR"
            }
        }.orEmpty()
        cards += InfoCard("🖥️", tx("Ekran", "Display"), listOfNotNull(
            mode?.let { tx("Şu an", "Current") to "${it.physicalWidth}×${it.physicalHeight} · ${it.refreshRate.toInt()} Hz" },
            maxMode?.let { tx("En yüksek", "Maximum") to "${it.physicalWidth}×${it.physicalHeight} · ${it.refreshRate.toInt()} Hz" },
            "HDR" to hdr.distinct().joinToString(", ").ifEmpty { no },
            tx("Yoğunluk", "Density") to "${ctx.resources.displayMetrics.densityDpi} dpi",
        ))

        val types = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }.flatMap { it.supportedTypes.toList() }.map { it.lowercase() }.toSet()
        }.getOrDefault(emptySet())
        fun has(t: String) = if (t in types) yes else no
        cards += InfoCard("🎬", tx("Video çözücüler", "Video decoders"), listOf(
            "H.264 (AVC)" to has("video/avc"),
            "H.265 (HEVC)" to has("video/hevc"),
            "VP9" to has("video/x-vnd.on2.vp9"),
            "AV1" to has("video/av01"),
            "Dolby Vision" to has("video/dolby-vision"),
        ))
        cards += InfoCard("🔊", tx("Ses biçimleri", "Audio formats"), listOf(
            "Dolby Digital (AC-3)" to has("audio/ac3"),
            "Dolby Digital Plus" to has("audio/eac3"),
            "Dolby Atmos (E-AC-3 JOC)" to has("audio/eac3-joc"),
            "DTS" to has("audio/vnd.dts"),
            "AAC" to has("audio/mp4a-latm"),
        ))

        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        val kind = when {
            caps == null -> tx("Bağlı değil", "Offline")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            else -> tx("Diğer", "Other")
        }
        @Suppress("DEPRECATION")
        val wifi = runCatching { ctx.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo }.getOrNull()
        cards += InfoCard("📶", tx("Ağ", "Network"), listOfNotNull(
            tx("Bağlantı", "Connection") to kind,
            tx("IP adresi", "IP address") to (QuizServer.localIp() ?: "—"),
            wifi?.takeIf { kind == "Wi-Fi" && it.linkSpeed > 0 }?.let { tx("Wi-Fi hızı", "Wi-Fi link") to "${it.linkSpeed} Mbps · ${if (it.frequency > 4900) "5 GHz" else "2,4 GHz"}" },
            wifi?.takeIf { kind == "Wi-Fi" }?.let { tx("Sinyal", "Signal") to signal(it.rssi) },
        ))

        val pkg = ctx.packageName
        val appOps = ctx.getSystemService(android.app.AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        fun op(name: String) = runCatching {
            appOps.checkOpNoThrow(name, android.os.Process.myUid(), pkg) == android.app.AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
        val listeners = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners").orEmpty()
        cards += InfoCard("🔐", tx("LiteDroid izinleri", "LiteDroid permissions"), listOf(
            tx("Sürüm", "Version") to "v" + Updater.currentVersion(ctx),
            tx("Kendi güncellemesi", "Self-update") to if (ctx.packageManager.canRequestPackageInstalls()) yes else no,
            tx("Medya geçmişi", "Media history") to if (listeners.contains(pkg)) yes else no,
            tx("Kullanım istatistikleri", "Usage stats") to if (op(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS)) yes else no,
            tx("Üstte gösterme", "Draw over apps") to if (Settings.canDrawOverlays(ctx)) yes else no,
            tx("Kumanda kısayolu", "Remote shortcut") to if (RemoteKeyService.isEnabled(ctx)) yes else no,
            tx("Mikrofon", "Microphone") to if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) yes else no,
        ))
        return cards
    }

    private fun gb(bytes: Long) = if (bytes >= 1L shl 30) "%.1f GB".format(bytes / (1L shl 30).toFloat()) else "${bytes / (1L shl 20)} MB"

    private fun signal(rssi: Int) = when {
        rssi >= -55 -> tx("Mükemmel", "Excellent")
        rssi >= -67 -> tx("İyi", "Good")
        rssi >= -75 -> tx("Orta", "Fair")
        else -> tx("Zayıf", "Weak")
    } + " ($rssi dBm)"
}

@Composable
fun SystemInfoTool(accent: Color) {
    val context = LocalContext.current
    val cards by produceState<List<InfoCard>>(emptyList()) { value = withContext(Dispatchers.IO) { SystemInfo.collect(context.applicationContext) } }
    val first = remember { FocusRequester() }
    LaunchedEffect(cards.isNotEmpty()) {
        if (cards.isNotEmpty()) {
            delay(100)
            runCatching { first.requestFocus() }
        }
    }
    LazyVerticalGrid(GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(cards, key = { _, c -> c.title }) { i, c -> InfoCardView(c, accent, if (i == 0) Modifier.focusRequester(first) else Modifier) }
    }
}

@Composable
private fun InfoCardView(card: InfoCard, accent: Color, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = if (focused) 0.12f else 0.06f))
            .border(2.dp, if (focused) accent else Color.Transparent, RoundedCornerShape(18.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(14.dp),
    ) {
        Text("${card.icon}  ${card.title}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        card.rows.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(k, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.width(120.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(v, color = if (v == "✓") Color(0xFF7CF0A8) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
