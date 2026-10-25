package dev.litedroid.launcher.overlay

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.tx
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Uyku zamanlayıcısı: belirli bir süre sonra ya da belirli bir saatte TV'yi kapatır.
 * Durumu burada tutulur (süreç ölse de kaybolmasın diye diske de yazılır); zamanı sayan, uyarıları
 * gösteren ve sonunda cihazı uyutan kumanda kısayolu servisidir ([RemoteKeyService]).
 */
object SleepTimer {
    private const val PREFS = "litedroid_sleep"
    private const val K_END = "end_at"
    private const val K_TOTAL = "total_ms"

    /** Menüdeki hazır süreler (dakika). */
    val PRESETS = listOf(15, 30, 45, 60, 90, 120)

    private val _endAt = MutableStateFlow<Long?>(null)
    /** Kapanma anı (epoch ms) ya da kapalıysa null. */
    val endAt: StateFlow<Long?> = _endAt

    /** Kurulan toplam süre (menüde hangi hazır sürenin seçili olduğunu göstermek için). */
    var totalMs: Long = 0L
        private set

    /** Servis dinler: zamanlayıcı kurulunca/iptal edilince çağrılır. [fromUser] kullanıcı mı değiştirdi. */
    var onChange: ((fromUser: Boolean) -> Unit)? = null

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val end = p.getLong(K_END, 0L)
        if (end > System.currentTimeMillis()) {
            _endAt.value = end
            totalMs = p.getLong(K_TOTAL, 0L)
        } else {
            _endAt.value = null
            totalMs = 0L
        }
    }

    fun startIn(context: Context, minutes: Int) {
        store(context, System.currentTimeMillis() + minutes * 60_000L, minutes * 60_000L)
    }

    /** Bugün o saat geçtiyse yarın. */
    fun startAt(context: Context, hour: Int, minute: Int) {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var at = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val end = at.atZone(zone).toInstant().toEpochMilli()
        store(context, end, end - System.currentTimeMillis())
    }

    fun cancel(context: Context) = store(context, null, 0L)

    /** Süre doldu: servis cihazı uyuturken çağırır (dinleyiciyi tetiklemez). */
    fun finished(context: Context) {
        persist(context, null, 0L)
        _endAt.value = null
        totalMs = 0L
    }

    fun remainingMs(): Long? = _endAt.value?.let { it - System.currentTimeMillis() }

    private fun store(context: Context, end: Long?, total: Long) {
        persist(context, end, total)
        totalMs = total
        _endAt.value = end
        onChange?.invoke(true)
    }

    private fun persist(context: Context, end: Long?, total: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(K_END, end ?: 0L)
            .putLong(K_TOTAL, total)
            .apply()
    }

    fun clock(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

    /** "1 sa 5 dk", "42 dk", "40 sn". */
    fun formatRemaining(ms: Long): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        val min = (sec + 59) / 60
        val h = min / 60
        val m = min % 60
        return when {
            sec < 60 -> tx("$sec sn", "${sec}s")
            h == 0L -> tx("$m dk", "$m min")
            m == 0L -> tx("$h sa", "${h}h")
            else -> tx("$h sa $m dk", "${h}h ${m}m")
        }
    }

    /** Ayarlar ve menü için kısa durum: "Kapanış 22:45" ya da null. */
    fun statusLabel(end: Long?): String? = end?.let { tx("Kapanış ${clock(it)}", "Off at ${clock(it)}") }
}

/** Sağ altta 5 sn görünen uyarı ya da son 10 sn geri sayımı. */
data class SleepNotice(
    val title: String,
    val text: String,
    /** Geri sayım (saniye); null ise normal uyarı. */
    val countdown: Int? = null,
    val id: Long = System.nanoTime(),
    /** Soldaki simge (uyku: ay, güncelleme: ok). */
    val icon: String = "☾",
    /** 0..1 arası ilerleme çubuğu (güncelleme indirilirken), yoksa null. */
    val progress: Float? = null,
)

/** Bildirim penceresinin içeriği: sağdan kayarak girer ve çıkar. */
@Composable
fun SleepNoticeView(notice: SleepNotice?) {
    // Çıkış animasyonu sırasında son bildirim çizilmeye devam etsin
    var last by remember { mutableStateOf(notice) }
    if (notice != null) last = notice
    AnimatedVisibility(
        visible = notice != null,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        val n = last ?: return@AnimatedVisibility
        val shape = RoundedCornerShape(22.dp)
        Row(
            Modifier
                .padding(end = 28.dp, bottom = 28.dp)
                .widthIn(max = 420.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF1A1C33).copy(alpha = 0.97f), Color(0xFF0D0E1A).copy(alpha = 0.97f))))
                .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val c = n.countdown
            if (c != null) {
                // Geri sayım halkası
                Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(58.dp)) {
                        val stroke = 5.dp.toPx()
                        val inset = stroke / 2
                        drawArc(
                            Color.White.copy(alpha = 0.12f), 0f, 360f, false,
                            topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(stroke),
                        )
                        drawArc(
                            Color(0xFFFF6B6B), -90f, 360f * c / 10f, false,
                            topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                    Text("$c", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                }
            } else {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF3A3F8F), Color(0xFF6B4DFF)))),
                    contentAlignment = Alignment.Center,
                ) { Text(n.icon, color = Color.White, fontSize = 22.sp) }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(n.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(n.text, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 16.sp)
                n.progress?.let { p ->
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(260.dp).height(5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))) {
                        Box(Modifier.fillMaxWidth(p.coerceIn(0.02f, 1f)).height(5.dp).clip(RoundedCornerShape(50)).background(Color(0xFF8E7BFF)))
                    }
                }
            }
        }
    }
}
