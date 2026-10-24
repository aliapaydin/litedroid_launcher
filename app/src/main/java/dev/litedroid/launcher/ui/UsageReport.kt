package dev.litedroid.launcher.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.I18n
import dev.litedroid.launcher.LauncherViewModel
import dev.litedroid.launcher.data.AppUsage
import dev.litedroid.launcher.data.UsageRange
import dev.litedroid.launcher.data.UsageReport
import dev.litedroid.launcher.tx
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

// Grafik renkleri: tek ton (büyüklük), vurgu aynı tonun dolu hali; yazılar her zaman nötr.
private val BarStrong = Color(0xFF8E7BFF)
private val BarSoft = Color(0xFF8E7BFF).copy(alpha = 0.38f)
private val BarTrack = Color.White.copy(alpha = 0.06f)
private val InkPrimary = Color.White
private val InkSecondary = Color.White.copy(alpha = 0.68f)
private val InkMuted = Color.White.copy(alpha = 0.45f)

/** "3 sa 12 dk", "45 dk", "1 dk'dan az". */
fun formatDuration(ms: Long): String {
    val min = ms / 60_000
    val h = min / 60
    val m = min % 60
    return when {
        min < 1 -> tx("1 dk'dan az", "under 1 min")
        h == 0L -> tx("$m dk", "$m min")
        m == 0L -> tx("$h sa", "$h h")
        else -> tx("$h sa $m dk", "${h}h ${m}m")
    }
}

/** Grafik etiketleri için kısa: "2,4 sa", "45 dk". */
private fun shortDuration(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> "0"
        min < 60 -> tx("$min dk", "${min}m")
        else -> {
            val h = min / 60.0
            val s = if (h >= 10) "%.0f".format(I18n.locale, h) else "%.1f".format(I18n.locale, h)
            tx("$s sa", "${s}h")
        }
    }
}

private fun clock(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

/** Rapor kartları: neredeyse opak koyu yüzey, arkadaki ana ekran okunurluğu bozmasın. */
private val CardColor = Color(0xFF14162A).copy(alpha = 0.96f)
private val CardShape = RoundedCornerShape(22.dp)

/** Odaklanabilir kart: kumandayla sayfada aşağı inerken her kart sırayla odak alır, sayfa onunla kayar. */
@Composable
private fun ReportCard(
    modifier: Modifier = Modifier,
    focusable: Boolean = true,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(CardShape)
            .background(CardColor)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f),
                CardShape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled = focusable)
    ) { content() }
}

/**
 * Kullanım raporu: ekran süresi, açılışlar, saatlere ve günlere göre dağılım, uygulama sıralaması.
 * Tek sütun, kumandayla aşağı kayan bir sayfa: başlık → özet → grafikler → uygulama kartları.
 * Veri Android'in kullanım istatistiklerinden gelir (izin: Ayarlar → İçerik ve gizlilik).
 */
@Composable
fun UsageReportScreen(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    var range by remember { mutableStateOf(UsageRange.TODAY) }
    var report by remember { mutableStateOf<UsageReport?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(range) {
        loading = true
        report = vm.usageReport(range)
        loading = false
    }
    val pillRequesters = remember { UsageRange.entries.associateWith { FocusRequester() } }
    val summaryRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { runCatching { pillRequesters.getValue(UsageRange.TODAY).requestFocus() } }

    val r = report

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0C0D1C), Color(0xFF07080F))))
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 30.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Başlık ve dilim seçimi ----
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tx("Kullanım raporu", "Usage report"), color = InkPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                        Text(rangeSubtitle(range), color = InkMuted, fontSize = 12.sp)
                    }
                    UsageRange.entries.forEach { option ->
                        RangePill(
                            label = when (option) {
                                UsageRange.TODAY -> tx("Bugün", "Today")
                                UsageRange.YESTERDAY -> tx("Dün", "Yesterday")
                                UsageRange.WEEK -> tx("Son 7 gün", "Last 7 days")
                            },
                            selected = option == range,
                            modifier = Modifier
                                .focusRequester(pillRequesters.getValue(option))
                                .focusProperties { if (r != null) down = summaryRequester },
                            onFocused = { range = option },
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }

            when {
                r == null -> item(key = "message") {
                    Box(Modifier.fillMaxWidth().height(360.dp)) {
                        if (!vm.usageAccess && !loading) {
                            Message(
                                tx("Kullanım istatistikleri izni kapalı", "Usage access is off"),
                                tx("Bilgisayardan bir kez şu komutu çalıştır:\nadb shell appops set dev.litedroid.launcher GET_USAGE_STATS allow",
                                    "Run this once from your computer:\nadb shell appops set dev.litedroid.launcher GET_USAGE_STATS allow"),
                            )
                        } else {
                            Message(if (loading) tx("Rapor hazırlanıyor…", "Preparing the report…") else tx("Rapor alınamadı", "Couldn't read usage data"), null)
                        }
                    }
                }
                else -> {
                    // ---- Özet ----
                    item(key = "summary") {
                        ReportCard(Modifier.fillMaxWidth().focusRequester(summaryRequester)) { Summary(r) }
                    }
                    // ---- Grafikler yan yana ----
                    item(key = "charts") {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            ReportCard(Modifier.weight(1f)) {
                                ChartBody(tx("Saatlere göre", "By hour of day")) { HourlyChart(r.hourly) }
                            }
                            ReportCard(Modifier.weight(1f)) {
                                ChartBody(tx("Son 7 gün", "Last 7 days")) { DailyChart(r) }
                            }
                        }
                    }
                    // ---- Uygulamalar: iki sütunlu kartlar ----
                    item(key = "apps_title") {
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp, start = 4.dp)) {
                            Text(tx("Uygulamalar", "Apps"), color = InkPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (r.apps.isEmpty()) tx("Bu dilimde kullanım yok", "No usage in this period")
                                else tx("${r.apps.size} uygulama · en çok kullanılandan", "${r.apps.size} apps · most used first"),
                                color = InkMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    val top = r.apps.firstOrNull()?.totalMs?.coerceAtLeast(1) ?: 1L
                    val rows = r.apps.withIndex().toList().chunked(2)
                    items(rows.size, key = { "apps_$it" }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            rows[row].forEach { (i, app) ->
                                AppCard(
                                    rank = i + 1,
                                    app = app,
                                    icon = vm.installed.firstOrNull { it.packageName == app.packageName }?.icon,
                                    share = app.totalMs / r.totalMs.coerceAtLeast(1).toFloat(),
                                    fraction = app.totalMs / top.toFloat(),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rows[row].size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private fun rangeSubtitle(range: UsageRange): String {
    val today = LocalDate.now()
    val day = DateTimeFormatter.ofPattern(tx("d MMMM EEEE", "EEEE, MMMM d"), I18n.locale)
    val short = DateTimeFormatter.ofPattern(tx("d MMM", "MMM d"), I18n.locale)
    return when (range) {
        UsageRange.TODAY -> today.format(day)
        UsageRange.YESTERDAY -> today.minusDays(1).format(day)
        UsageRange.WEEK -> today.minusDays(6).format(short) + " – " + today.format(short)
    }
}

@Composable
private fun Summary(r: UsageReport) {
    val peak = r.hourly.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tx("EKRAN SÜRESİ", "SCREEN TIME"), color = InkMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text(formatDuration(r.totalMs), color = InkPrimary, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 42.sp)
            val note = when {
                r.range == UsageRange.WEEK -> tx("Günlük ortalama ", "Daily average ") + formatDuration(r.totalMs / 7)
                r.compareMs != null && r.compareMs > 0 -> {
                    val diff = r.totalMs - r.compareMs
                    val what = if (r.range == UsageRange.TODAY) tx("Düne göre", "Than yesterday") else tx("Önceki güne göre", "Than the day before")
                    if (kotlin.math.abs(diff) < 60_000) tx("$what aynı", "About the same as the day before")
                    else if (diff > 0) tx("$what ${formatDuration(diff)} daha fazla", "${formatDuration(diff)} more ${what.lowercase()}")
                    else tx("$what ${formatDuration(-diff)} daha az", "${formatDuration(-diff)} less ${what.lowercase()}")
                }
                else -> null
            }
            note?.let { Text(it, color = InkSecondary, fontSize = 13.sp) }
        }
        Row(Modifier.weight(1.4f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(tx("Açılış", "Opens"), "${r.opens}", Modifier.weight(1f))
            StatTile(tx("Uygulama", "Apps"), "${r.apps.size}", Modifier.weight(1f))
            StatTile(tx("En yoğun saat", "Busiest hour"), peak?.let { "%02d:00".format(it) } ?: "–", Modifier.weight(1f))
            StatTile(
                if (r.range == UsageRange.WEEK) tx("Son kullanım", "Last used") else tx("İlk açılış", "First use"),
                (if (r.range == UsageRange.WEEK) r.lastUse else r.firstUse)?.let { clock(it) } ?: "–",
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(value, color = InkPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = InkMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChartBody(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(title, color = InkSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** Yuvarlak tepeli, tabana oturan ince çubuk. */
private fun DrawScope.bar(x: Float, w: Float, h: Float, bottom: Float, color: Color) {
    if (h <= 0f) return
    val r = minOf(4.dp.toPx(), w / 2, h)
    val path = Path().apply {
        moveTo(x, bottom)
        lineTo(x, bottom - h + r)
        quadraticTo(x, bottom - h, x + r, bottom - h)
        lineTo(x + w - r, bottom - h)
        quadraticTo(x + w, bottom - h, x + w, bottom - h + r)
        lineTo(x + w, bottom)
        close()
    }
    drawPath(path, color)
}

@Composable
private fun HourlyChart(hourly: List<Long>) {
    val max = hourly.maxOrNull()?.takeIf { it > 0 } ?: 1L
    val peak = hourly.indexOf(hourly.maxOrNull() ?: 0L).takeIf { (hourly.maxOrNull() ?: 0L) > 0 }
    Column {
        Box(Modifier.fillMaxWidth().height(14.dp)) {
            // Sadece tepe saat etiketlenir
            if (peak != null) {
                Text(
                    shortDuration(hourly[peak]),
                    color = InkSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.align(
                        androidx.compose.ui.BiasAlignment(horizontalBias = (peak + 0.5f) / 12f - 1f, verticalBias = 0f)
                    ),
                )
            }
        }
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            val gap = 2.dp.toPx()
            val w = (size.width - gap * 23) / 24
            hourly.forEachIndexed { i, ms ->
                val x = i * (w + gap)
                drawRoundRect(BarTrack, Offset(x, 0f), Size(w, size.height), CornerRadius(3.dp.toPx()))
                bar(x, w, size.height * ms / max.toFloat(), size.height, if (i == peak) BarStrong else BarSoft)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("00", "06", "12", "18", "23").forEachIndexed { i, label ->
                Text(
                    label,
                    color = InkMuted,
                    fontSize = 10.sp,
                    textAlign = when (i) {
                        0 -> TextAlign.Start
                        4 -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DailyChart(r: UsageReport) {
    val max = r.daily.maxOfOrNull { it.totalMs }?.takeIf { it > 0 } ?: 1L
    val today = LocalDate.now()
    fun inRange(d: LocalDate) = when (r.range) {
        UsageRange.TODAY -> d == today
        UsageRange.YESTERDAY -> d == today.minusDays(1)
        UsageRange.WEEK -> true
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        r.daily.forEach { day ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (day.totalMs > 0) shortDuration(day.totalMs) else "–",
                    color = if (inRange(day.date)) InkPrimary else InkMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                    drawRoundRect(BarTrack, Offset.Zero, size, CornerRadius(4.dp.toPx()))
                    bar(0f, size.width, size.height * day.totalMs / max.toFloat(), size.height, if (inRange(day.date)) BarStrong else BarSoft)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (day.date == today) tx("Bugün", "Today") else day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, I18n.locale),
                    color = if (day.date == today) InkPrimary else InkMuted,
                    fontSize = 10.sp,
                    fontWeight = if (day.date == today) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AppCard(
    rank: Int,
    app: AppUsage,
    icon: androidx.compose.ui.graphics.ImageBitmap?,
    share: Float,
    fraction: Float,
    modifier: Modifier,
) {
    ReportCard(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val img = icon ?: app.icon
            Box {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (img != null) Image(img, contentDescription = null, modifier = Modifier.size(48.dp))
                    else Text(app.label.take(1).uppercase(), color = InkPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                // Sıra rozeti
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .size(18.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (rank == 1) BarStrong else Color(0xFF2A2D48)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$rank", color = InkPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.label,
                        color = InkPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(formatDuration(app.totalMs), color = InkPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(BarTrack)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                                .height(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (rank == 1) BarStrong else BarSoft)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    val pct = (share * 100).toInt()
                    Text(tx("%$pct", "$pct%"), color = InkSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    tx("${app.opens} açılış · en uzun ${formatDuration(app.longestMs)} · son ${clock(app.lastUsed)}",
                        "${app.opens} opens · longest ${formatDuration(app.longestMs)} · last ${clock(app.lastUsed)}"),
                    color = InkMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RangePill(label: String, selected: Boolean, modifier: Modifier, onFocused: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    focused -> Color.White
                    selected -> Color(0xFF6B4DFF)
                    else -> Color.White.copy(alpha = 0.1f)
                }
            )
            .onFocusChanged {
                if (it.isFocused && !focused) onFocused()
                focused = it.isFocused
            }
            .focusable()
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (focused) Color(0xFF111114) else InkPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Message(title: String, body: String?) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, color = InkPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        body?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = InkSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp)
        }
    }
}
