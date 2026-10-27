package dev.litedroid.launcher.wrapped

import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.litedroid.launcher.data.UsageRepository
import dev.litedroid.launcher.games.BuiltInGame
import dev.litedroid.launcher.overlay.RemoteKeyService
import dev.litedroid.launcher.quiz.QrCode
import dev.litedroid.launcher.tools.ProfileAvatar
import dev.litedroid.launcher.tools.ScreenshotStore
import dev.litedroid.launcher.tools.ShotShareServer
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.isSelectKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Ortak yardımcılar
// ---------------------------------------------------------------------------

private val Gold = Color(0xFFFFD35C)
private val Mint = Color(0xFF5CFFB0)
private val Coral = Color(0xFFFF7A8A)

internal fun dur(ms: Long): String {
    val m = ms / 60_000
    val h = m / 60
    val mm = m % 60
    return when {
        h > 0 && mm > 0 -> tx("$h sa $mm dk", "${h}h ${mm}m")
        h > 0 -> tx("$h saat", "$h h")
        else -> tx("$mm dk", "$mm min")
    }
}

internal fun dayShort(i: Int) = tx(listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")[i], listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[i])
internal fun dayLong(i: Int) = tx(
    listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")[i],
    listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[i],
)

private val MONTHS_TR = listOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")
private val MONTHS_EN = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "20 - 26 Ekim" ya da "28 Eki - 3 Kas". */
internal fun rangeLabel(w: WeekData): String {
    val a = w.start
    val b = w.end
    val ma = tx(MONTHS_TR[a.monthValue - 1], MONTHS_EN[a.monthValue - 1])
    val mb = tx(MONTHS_TR[b.monthValue - 1], MONTHS_EN[b.monthValue - 1])
    return if (a.month == b.month) "${a.dayOfMonth} - ${b.dayOfMonth} $mb" else "${a.dayOfMonth} ${ma.take(3)} - ${b.dayOfMonth} ${mb.take(3)}"
}

private fun clock(t: Long): String {
    val z = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())
    return "%02d:%02d".format(z.hour, z.minute)
}

private fun dayOf(t: Long, w: WeekData): Int =
    (Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() - w.start.toEpochDay()).toInt().coerceIn(0, 6)

/** İçerik girişi: gecikmeyle yukarı kayarak belirir. */
@Composable
private fun Modifier.reveal(delayMs: Int, dy: Dp = 26.dp): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * dy.toPx()
    }
}

/** Zıplayarak büyüme. */
@Composable
private fun Modifier.pop(delayMs: Int): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
    }
    return this.graphicsLayer {
        scaleX = a.value
        scaleY = a.value
        alpha = a.value.coerceIn(0f, 1f)
    }
}

/** 0'dan 1'e giden sayaç (sayıların sayarak yükselmesi için). */
@Composable
private fun rise(delayMs: Int, durationMs: Int = 1600): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
    }
    return a.value
}

@Composable
private fun Kicker(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, modifier = modifier)
}

@Composable
private fun Headline(text: String, modifier: Modifier = Modifier, size: Int = 34) {
    Text(text, color = Color.White, fontSize = size.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, lineHeight = (size + 4).sp, textAlign = TextAlign.Center, modifier = modifier)
}

/** Geçen haftaya göre değişim. */
@Composable
private fun DeltaChip(now: Long, before: Long?, modifier: Modifier = Modifier) {
    if (before == null || before <= 0) return
    val pct = ((now - before) * 100 / before).toInt()
    val (text, color) = when {
        abs(pct) < 3 -> tx("Geçen haftayla neredeyse aynı", "About the same as last week") to Color.White
        pct > 0 -> tx("Geçen haftadan %$pct fazla ↑", "$pct% more than last week ↑") to Mint
        else -> tx("Geçen haftadan %${-pct} az ↓", "${-pct}% less than last week ↓") to Coral
    }
    Text(
        text, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun AppIcon(icons: IconCache, pkg: String, label: String, size: Dp) {
    val img = icons.get(pkg)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(Color.White.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(label.take(1).uppercase(), color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Uygulama simgeleri (gösteri boyunca bir kez yüklenir). */
private class IconCache(private val usage: UsageRepository) {
    private val map = HashMap<String, ImageBitmap?>()
    fun get(pkg: String): ImageBitmap? = map.getOrPut(pkg) { runCatching { usage.icon(pkg) }.getOrNull() }
}

// ---------------------------------------------------------------------------
// Sayfalar
// ---------------------------------------------------------------------------

private enum class PageKind(val ms: Int, val colors: List<Long>) {
    INTRO(7_000, listOf(0xFF5B2EFF, 0xFFFF3D8B, 0xFF14B8FF)),
    TOTAL(9_000, listOf(0xFFFF6A3D, 0xFFFF2E7E, 0xFF7A2CFF)),
    TOP(10_000, listOf(0xFF00B37E, 0xFF0077FF, 0xFF7CFFCB)),
    STAR(9_000, listOf(0xFFFFB800, 0xFFFF5E3A, 0xFFB5179E)),
    HEAT(10_000, listOf(0xFF0B1E5B, 0xFF5B2EFF, 0xFF00D1FF)),
    PERSONA(9_000, listOf(0xFFB5179E, 0xFF7209B7, 0xFFFF9E00)),
    RECORDS(10_000, listOf(0xFF1F7A5C, 0xFF0F4C81, 0xFF4CC9F0)),
    MOMENTS(10_000, listOf(0xFFE63946, 0xFF6A00F4, 0xFFFFB703)),
    GALLERY(10_000, listOf(0xFF22223B, 0xFF9A8C98, 0xFFC9ADA7)),
    MATCHES(9_000, listOf(0xFF0B6E4F, 0xFF08A045, 0xFF1B4332)),
    SUMMARY(0, listOf(0xFF3A0CA3, 0xFFF72585, 0xFF4361EE)),
}

private fun pagesFor(w: WeekData, ctx: android.content.Context): List<PageKind> = buildList {
    add(PageKind.INTRO)
    if (w.totalMs >= 60_000) {
        add(PageKind.TOTAL)
        if (w.apps.size >= 2) add(PageKind.TOP)
        if (w.apps.isNotEmpty()) add(PageKind.STAR)
        add(PageKind.HEAT)
    }
    add(PageKind.PERSONA)
    if (w.totalMs >= 60_000) add(PageKind.RECORDS)
    if (moments(w).isNotEmpty()) add(PageKind.MOMENTS)
    if (w.paintings.any { File(ctx.filesDir, "canvas/$it.jpg").exists() }) add(PageKind.GALLERY)
    if (w.matches.isNotEmpty()) add(PageKind.MATCHES)
    add(PageKind.SUMMARY)
}

/**
 * Tam ekran Wrapped gösterisi. ◀ ▶ sayfa değiştirir, Tamam duraklatır, Geri kapatır.
 * Son sayfada ▲ ▼ ile "Telefona al" ya da "Baştan izle" seçilir.
 */
@Composable
fun WrappedShow(week: WeekData, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prev = remember(week) { WrappedEngine.previous(ctx, week) }
    val icons = remember { IconCache(UsageRepository(ctx)) }
    val pages = remember(week) { pagesFor(week, ctx) }
    var page by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    var summarySel by remember { mutableIntStateOf(0) }
    var musicNote by remember { mutableStateOf<String?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var shareUrl by remember { mutableStateOf<String?>(null) }
    var shareError by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        if (!week.preview) WrappedEngine.markSeen(ctx, week.start)
    }
    BackHandler(onBack = onClose)

    // ---- Müzik ----
    val music = remember { WrappedMusic(scope) }
    val shareServer = remember { ShotShareServer(ctx) }
    DisposableEffect(Unit) {
        val ready = WrappedEngine.musicFile(ctx, week.start)
        if (ready != null) music.play(ready) else scope.launch {
            // Müzik yoksa gösteri sessiz devam eder; müzik hazır olunca yumuşakça girer
            val hasKey = withContext(Dispatchers.IO) { dev.litedroid.launcher.data.SettingsRepository(ctx).load().geminiKey.isNotBlank() }
            if (!hasKey) {
                musicNote = tx("🔇 Müzik için Gemini anahtarı gerekli, gösteri sessiz", "🔇 Music needs a Gemini key, playing silently")
                delay(6_000)
                musicNote = null
                return@launch
            }
            musicNote = tx("🎵 Yapay zekâ haftanın müziğini besteliyor…", "🎵 The AI is composing this week's music…")
            val f = runCatching { WrappedEngine.ensureMusic(ctx, week) }.getOrNull()
            if (f != null) {
                music.play(f)
                musicNote = tx("🎵 Haftanın müziği hazır", "🎵 This week's music is ready")
            } else {
                musicNote = tx("🔇 Müzik bu sefer üretilemedi, gösteri sessiz", "🔇 Couldn't make the music this time, playing silently")
            }
            delay(5_000)
            musicNote = null
        }
        onDispose {
            music.release()
            shareServer.stop()
        }
    }
    LaunchedEffect(paused) { if (paused) music.pause() else music.resume() }

    // ---- Otomatik ilerleme ----
    val progress = remember(page, round) { Animatable(0f) }
    LaunchedEffect(page, paused, round) {
        val kind = pages[page]
        if (paused || kind == PageKind.SUMMARY) return@LaunchedEffect
        val left = ((1f - progress.value) * kind.ms).toInt().coerceAtLeast(1)
        progress.animateTo(1f, tween(left, easing = LinearEasing))
        if (page < pages.lastIndex) page++
    }

    fun go(d: Int) {
        val n = (page + d).coerceIn(0, pages.lastIndex)
        if (n != page) {
            page = n
            paused = false
            shareUrl = null
            shareError = null
        }
    }

    fun shareToPhone() {
        shareError = null
        val svc = RemoteKeyService.instance
        if (svc == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            shareError = tx("Bunun için Ayarlar → Kumanda'dan kumanda kısayolu servisini aç (Android 11+).", "Turn on the remote shortcut service in Settings → Remote (Android 11+).")
            return
        }
        scope.launch {
            val t0 = System.currentTimeMillis()
            capturing = true
            delay(450)
            svc.captureScreen(0)
            var file: File? = null
            for (i in 0 until 40) {
                delay(200)
                file = withContext(Dispatchers.IO) { ScreenshotStore.list(ctx).firstOrNull { it.lastModified() >= t0 - 1_000 } }
                if (file != null) break
            }
            capturing = false
            if (file == null) {
                shareError = tx("Görüntü alınamadı, tekrar dene.", "Couldn't capture, try again.")
                return@launch
            }
            shareServer.start()
            val base = shareServer.url
            if (base == null) shareError = tx("Ağ bağlantısı yok.", "No network.") else shareUrl = "$base/f/${file.name}"
        }
    }

    var armed by remember { mutableStateOf(false) }
    val kind = pages[page]
    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF07060F))
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
                if (e.key == Key.Back) return@onPreviewKeyEvent false
                val down = e.type == KeyEventType.KeyDown
                when {
                    e.key.isSelectKey -> {
                        if (down && e.nativeKeyEvent.repeatCount == 0) armed = true
                        if (!down && armed) {
                            armed = false
                            if (kind == PageKind.SUMMARY) {
                                if (summarySel == 0) shareToPhone() else {
                                    page = 0
                                    round++
                                    paused = false
                                    shareUrl = null
                                }
                            } else paused = !paused
                        }
                        true
                    }
                    down && e.key == Key.DirectionRight -> { go(1); true }
                    down && e.key == Key.DirectionLeft -> { go(-1); true }
                    down && (e.key == Key.DirectionUp || e.key == Key.DirectionDown) -> {
                        if (kind == PageKind.SUMMARY) summarySel = if (e.key == Key.DirectionUp) 0 else 1
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        Backdrop(kind.colors)
        AnimatedContent(
            targetState = page to round,
            transitionSpec = { (fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 0.96f)) togetherWith fadeOut(tween(250)) },
            label = "wrappedPage",
        ) { (p, _) ->
            Box(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 46.dp), contentAlignment = Alignment.Center) {
                when (pages[p]) {
                    PageKind.INTRO -> IntroPage(week)
                    PageKind.TOTAL -> TotalPage(week, prev)
                    PageKind.TOP -> TopAppsPage(week, prev, icons)
                    PageKind.STAR -> StarPage(week, prev, icons)
                    PageKind.HEAT -> HeatPage(week)
                    PageKind.PERSONA -> PersonaPage(week)
                    PageKind.RECORDS -> RecordsPage(week)
                    PageKind.MOMENTS -> MomentsPage(week, prev)
                    PageKind.GALLERY -> GalleryPage(week)
                    PageKind.MATCHES -> MatchesPage(week)
                    PageKind.SUMMARY -> SummaryPage(week, prev, icons, summarySel, capturing, shareUrl, shareError)
                }
            }
        }
        if (!capturing) {
            // Hikâye tarzı ilerleme çubukları
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 16.dp).align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                pages.indices.forEach { i ->
                    val f = when {
                        i < page -> 1f
                        i == page -> if (pages[i] == PageKind.SUMMARY) 1f else progress.value
                        else -> 0f
                    }
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(Color.White))
                    }
                }
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                musicNote?.let {
                    Text(it, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                    Spacer(Modifier.width(18.dp))
                }
                Text(
                    if (paused) tx("⏸ Duraklatıldı · Tamam: devam", "⏸ Paused · OK: resume")
                    else if (kind == PageKind.SUMMARY) tx("▲▼ seç · Tamam · ◀ geri · Geri: kapat", "▲▼ choose · OK · ◀ back · Back: close")
                    else tx("◀ ▶ gez · Tamam: duraklat · Geri: kapat", "◀ ▶ browse · OK: pause · Back: close"),
                    color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp,
                )
            }
        }
    }
}

/** Sayfanın renginde yavaşça dolaşan ışık lekeleri. */
@Composable
private fun Backdrop(colors: List<Long>) {
    val c0 by animateColorAsState(Color(colors[0]), tween(900), label = "c0")
    val c1 by animateColorAsState(Color(colors[1]), tween(900), label = "c1")
    val c2 by animateColorAsState(Color(colors[2]), tween(900), label = "c2")
    val t by rememberInfiniteTransition(label = "bg").animateFloat(0f, 6.2832f, infiniteRepeatable(tween(18_000, easing = LinearEasing)), label = "t")
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        fun blob(c: Color, x: Float, y: Float, r: Float) =
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.75f), Color.Transparent), Offset(x, y), r), r, Offset(x, y))
        blob(c0, w * (0.2f + 0.12f * cos(t)), h * (0.25f + 0.15f * sin(t * 1.3f)), w * 0.55f)
        blob(c1, w * (0.8f + 0.1f * sin(t * 0.8f)), h * (0.7f + 0.12f * cos(t)), w * 0.5f)
        blob(c2, w * (0.55f + 0.2f * cos(t * 0.6f)), h * (0.1f + 0.1f * sin(t * 1.7f)), w * 0.38f)
        drawRect(Color.Black.copy(alpha = 0.28f))
    }
}

// ---- 1. Giriş ----
@Composable
private fun IntroPage(w: WeekData) {
    val spin by rememberInfiniteTransition(label = "ring").animateFloat(0f, 360f, infiniteRepeatable(tween(6_000, easing = LinearEasing)), label = "s")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Kicker(if (w.preview) tx("Bu hafta şimdiye kadar", "This week so far") else tx("Haftalık özet", "Weekly recap"), Modifier.reveal(0))
        Spacer(Modifier.height(8.dp))
        Text(
            "LiteDroid Wrapped",
            style = TextStyle(brush = Brush.linearGradient(listOf(Gold, Color(0xFFFF6FB5), Color(0xFF8EC5FF))), fontSize = 52.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp),
            modifier = Modifier.reveal(200),
        )
        Spacer(Modifier.height(22.dp))
        Box(Modifier.size(132.dp).pop(600), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = spin }) {
                drawCircle(Brush.sweepGradient(listOf(Gold, Color(0xFFFF6FB5), Color(0xFF8EC5FF), Gold)), style = Stroke(6.dp.toPx()))
            }
            ProfileAvatar(w.name, 110.dp, 0.dp, 46)
        }
        Spacer(Modifier.height(18.dp))
        Headline(if (w.preview) tx("${w.name}, haftan nasıl gidiyor?", "${w.name}, how's your week going?") else tx("${w.name}, haftan hazır!", "${w.name}, your week is ready!"), Modifier.reveal(1000))
        Text(rangeLabel(w), color = Color.White.copy(alpha = 0.75f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.reveal(1300))
        if (!w.usageAccess) {
            Spacer(Modifier.height(10.dp))
            Text(
                tx("Kullanım istatistikleri izni kapalı olduğu için uygulama süreleri yok. Ayarlar → İçerik ve gizlilik.", "Usage access is off, so app times are missing. Settings → Content and privacy."),
                color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.reveal(1600),
            )
        }
    }
}

// ---- 2. Toplam süre ----
@Composable
private fun TotalPage(w: WeekData, prev: WeekData?) {
    val r = rise(500, 1800)
    val maxDay = w.daily.maxOrNull()?.coerceAtLeast(1) ?: 1
    val best = w.busiestDay
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Kicker(tx("Bu hafta TV başında", "In front of the TV this week"), Modifier.reveal(0))
        Text(dur((w.totalMs * r).toLong()), color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp, modifier = Modifier.reveal(200))
        Text(
            tx("günde ortalama ${dur(w.totalMs / 7)}", "about ${dur(w.totalMs / 7)} a day"),
            color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp, modifier = Modifier.reveal(700),
        )
        Spacer(Modifier.height(10.dp))
        DeltaChip(w.totalMs, prev?.totalMs, Modifier.reveal(1100))
        Spacer(Modifier.height(22.dp))
        Row(Modifier.height(150.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
            w.daily.forEachIndexed { i, ms ->
                val g = rise(900 + i * 120, 900)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (i == best && ms > 0) Text("👑", fontSize = 18.sp, modifier = Modifier.graphicsLayer { alpha = g })
                    Text(if (ms >= 60_000) dur(ms) else "—", color = Color.White.copy(alpha = 0.75f * g), fontSize = 10.sp)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .width(44.dp)
                            .height((96f * (ms.toFloat() / maxDay) * g).coerceAtLeast(3f).dp)
                            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                            .background(if (i == best) Brush.verticalGradient(listOf(Gold, Color(0xFFFF8A3D))) else Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.55f))))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(dayShort(i), color = Color.White, fontSize = 13.sp, fontWeight = if (i == best) FontWeight.ExtraBold else FontWeight.Medium)
                }
            }
        }
    }
}

// ---- 3. En çok izlenenler ----
@Composable
private fun TopAppsPage(w: WeekData, prev: WeekData?, icons: IconCache) {
    val top = w.apps.take(5)
    val max = top.firstOrNull()?.ms?.coerceAtLeast(1) ?: 1
    Column(Modifier.fillMaxWidth(0.82f)) {
        Kicker(tx("Haftanın listesi", "The weekly chart"), Modifier.reveal(0))
        Headline(tx("En çok izlenenler", "Most watched"), Modifier.reveal(150), size = 36)
        Spacer(Modifier.height(16.dp))
        top.forEachIndexed { i, a ->
            val g = rise(500 + i * 260, 1200)
            val prevRank = prev?.apps?.indexOfFirst { it.pkg == a.pkg }
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).reveal(400 + i * 260, 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = if (i == 0) Gold else Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(34.dp))
                AppIcon(icons, a.pkg, a.label, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(8.dp))
                        if (prev != null) RankBadge(prevRank, i)
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth((a.ms.toFloat() / max) * g).clip(RoundedCornerShape(50))
                                .background(Brush.horizontalGradient(if (i == 0) listOf(Gold, Color(0xFFFF8A3D)) else listOf(Mint, Color(0xFF4DA3FF))))
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(110.dp)) {
                    Text(dur(a.ms), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text("%${a.ms * 100 / w.totalMs.coerceAtLeast(1)}", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun RankBadge(prevIndex: Int?, now: Int) {
    val (text, color) = when {
        prevIndex == null -> return
        prevIndex < 0 -> tx("YENİ", "NEW") to Gold
        prevIndex > now -> "↑${prevIndex - now}" to Mint
        prevIndex < now -> "↓${now - prevIndex}" to Coral
        else -> "=" to Color.White.copy(alpha = 0.7f)
    }
    Text(text, color = Color(0xFF111114), fontSize = 10.sp, fontWeight = FontWeight.Black,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 7.dp, vertical = 2.dp))
}

// ---- 4. Haftanın uygulaması ----
@Composable
private fun StarPage(w: WeekData, prev: WeekData?, icons: IconCache) {
    val a = w.apps.first()
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(0.85f, 1.12f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "g")
    val spin by rememberInfiniteTransition(label = "rays").animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "r")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(250.dp).pop(200), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = spin; scaleX = glow; scaleY = glow }) {
                val c = Offset(size.width / 2, size.height / 2)
                for (i in 0 until 12) {
                    val ang = i * 30.0 * Math.PI / 180
                    drawLine(
                        Brush.linearGradient(listOf(Gold.copy(alpha = 0.7f), Color.Transparent), c, Offset(c.x + cos(ang).toFloat() * size.width / 2, c.y + sin(ang).toFloat() * size.height / 2)),
                        c, Offset(c.x + cos(ang).toFloat() * size.width / 2, c.y + sin(ang).toFloat() * size.height / 2), strokeWidth = 14f,
                    )
                }
            }
            AppIcon(icons, a.pkg, a.label, 120.dp)
        }
        Spacer(Modifier.width(36.dp))
        Column {
            Kicker(tx("Haftanın uygulaması", "App of the week"), Modifier.reveal(300))
            Text(a.label, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.reveal(500))
            Text(
                tx("Toplam sürenin %${a.ms * 100 / w.totalMs.coerceAtLeast(1)}'i onunla geçti", "${a.ms * 100 / w.totalMs.coerceAtLeast(1)}% of all your time"),
                color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, modifier = Modifier.reveal(800),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(dur(a.ms), tx("toplam", "total"), Modifier.pop(1100))
                StatTile("${a.opens}", tx("kez açıldı", "opens"), Modifier.pop(1300))
                StatTile(dur(a.longestMs), tx("en uzun oturum", "longest sitting"), Modifier.pop(1500))
            }
            if (prev != null) {
                Spacer(Modifier.height(14.dp))
                val before = prev.apps.firstOrNull { it.pkg == a.pkg }
                val prevTop = prev.apps.firstOrNull()
                val line = when {
                    prevTop != null && prevTop.pkg != a.pkg -> tx("Geçen haftanın birincisi ${prevTop.label} tahtını kaptırdı! 👑", "Last week's #1, ${prevTop.label}, lost the crown! 👑")
                    before != null -> tx("Geçen hafta: ${dur(before.ms)}", "Last week: ${dur(before.ms)}")
                    else -> null
                }
                if (line != null) Text(line, color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.reveal(1800))
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier, emoji: String? = null, wide: Boolean = false) {
    Column(
        modifier
            .then(if (wide) Modifier.width(196.dp) else Modifier)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.13f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (emoji != null) Text(emoji, fontSize = 24.sp)
        Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, maxLines = 2, lineHeight = 15.sp)
    }
}

// ---- 5. Isı haritası ----
@Composable
private fun HeatPage(w: WeekData) {
    val sweep = rise(400, 2600)
    val max = w.heat.maxOrNull()?.coerceAtLeast(1) ?: 1
    val h = w.busiestHour
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Kicker(tx("Saat saat", "Hour by hour"), Modifier.reveal(0))
        Headline(tx("Ne zaman izlediniz?", "When did you watch?"), Modifier.reveal(150), size = 34)
        Spacer(Modifier.height(16.dp))
        Row {
            Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 7).forEach { d -> Box(Modifier.height(22.dp), contentAlignment = Alignment.CenterStart) { Text(dayShort(d), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp) } }
            }
            Column {
                Canvas(Modifier.width(600.dp).height((7 * 26 - 4).dp)) {
                    val cw = size.width / 24f
                    val ch = 22.dp.toPx()
                    val gap = 4.dp.toPx()
                    for (d in 0 until 7) for (hr in 0 until 24) {
                        val v = w.heat[d * 24 + hr].toFloat() / max
                        val show = ((hr + d * 0.5f) / 27f) < sweep
                        val c = if (v <= 0f) Color.White.copy(alpha = 0.07f) else androidx.compose.ui.graphics.lerp(Color(0xFF3A6BFF), Gold, v).copy(alpha = 0.35f + 0.65f * v)
                        drawRoundRect(
                            if (show) c else Color.White.copy(alpha = 0.04f),
                            Offset(hr * cw + 1.5f, d * (ch + gap)), Size(cw - 3f, ch), CornerRadius(5f),
                        )
                    }
                }
                Row(Modifier.width(600.dp)) {
                    listOf(0, 6, 12, 18).forEach { hr -> Text("%02d:00".format(hr), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, modifier = Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("%02d:00 - %02d:00".format(h, (h + 1) % 24), tx("en yoğun saat", "peak hour"), Modifier.pop(1600), "⏰")
            StatTile(dayLong(w.busiestDay), tx("en çok izlenen gün", "busiest day"), Modifier.pop(1800), "📅")
            StatTile("%${w.weekendMs * 100 / w.totalMs.coerceAtLeast(1)}", tx("hafta sonu payı", "weekend share"), Modifier.pop(2000), "🛋️")
        }
    }
}

// ---- 6. Kişilik ----
@Composable
private fun PersonaPage(w: WeekData) {
    val p = w.persona
    val spin = rise(300, 1400)
    val facts = buildList {
        when (p) {
            Persona.NIGHT_OWL -> w.latest?.let { add(tx("En geç ${clock(it.at)}'de kapandı", "Latest switch-off at ${clock(it.at)}")) }
            Persona.BINGER -> w.longest?.let { add(tx("Tek oturumda ${dur(it.ms)} ${it.label}", "${dur(it.ms)} of ${it.label} in one go")) }
            Persona.GAMER -> add(tx("${w.gamesPlayed} oyun açıldı", "${w.gamesPlayed} games played"))
            Persona.STORYTELLER -> add(tx("${(w.counters["story.new"] ?: 0) + (w.counters["story.replay"] ?: 0)} masal dinlendi", "${(w.counters["story.new"] ?: 0) + (w.counters["story.replay"] ?: 0)} stories"))
            Persona.WEEKEND -> add(tx("Sürenin %${w.weekendMs * 100 / w.totalMs.coerceAtLeast(1)}'i hafta sonu", "${w.weekendMs * 100 / w.totalMs.coerceAtLeast(1)}% on the weekend"))
            Persona.EARLY_BIRD -> w.earliest?.let { add(tx("En erken ${clock(it.at)}'de açıldı", "Earliest at ${clock(it.at)}")) }
            Persona.LOYAL -> w.apps.firstOrNull()?.let { add(tx("${it.label} sürenin %${it.ms * 100 / w.totalMs.coerceAtLeast(1)}'i", "${it.label}: ${it.ms * 100 / w.totalMs.coerceAtLeast(1)}% of the time")) }
            Persona.EXPLORER -> add(tx("${w.distinctApps} farklı uygulama", "${w.distinctApps} different apps"))
            Persona.BALANCED -> if (w.totalMs > 0) add(tx("Günde ortalama ${dur(w.totalMs / 7)}", "${dur(w.totalMs / 7)} a day on average"))
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Kicker(tx("Bu haftaki izleyici tipin", "Your viewer type this week"), Modifier.reveal(0))
        Spacer(Modifier.height(10.dp))
        Text(p.emoji, fontSize = 110.sp, modifier = Modifier.graphicsLayer { rotationZ = (1f - spin) * -200f; scaleX = spin; scaleY = spin; alpha = spin })
        Text(
            p.title,
            style = TextStyle(brush = Brush.linearGradient(listOf(Color.White, Gold)), fontSize = 50.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
            modifier = Modifier.reveal(1200),
        )
        Text(p.description, color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.7f).reveal(1500))
        Spacer(Modifier.height(14.dp))
        facts.forEachIndexed { i, f ->
            Text(f, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.reveal(1900 + i * 200).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 16.dp, vertical = 7.dp))
        }
    }
}

// ---- 7. Rekorlar ----
@Composable
private fun RecordsPage(w: WeekData) {
    val tiles = buildList<Triple<String, String, String>> {
        w.longest?.let { add(Triple("⏱️", dur(it.ms), tx("en uzun oturum · ${it.label}, ${dayLong(dayOf(it.at, w))}", "longest sitting · ${it.label}, ${dayLong(dayOf(it.at, w))}"))) }
        w.latest?.let { add(Triple("🌙", clock(it.at), tx("en geç kapanış · ${it.label}", "latest night · ${it.label}"))) }
        w.earliest?.let { add(Triple("☀️", clock(it.at), tx("en erken açılış · ${it.label}", "earliest start · ${it.label}"))) }
        add(Triple("🔁", "${w.opens}", tx("kez uygulama açıldı", "app opens")))
        add(Triple("🧩", "${w.distinctApps}", tx("farklı uygulama", "different apps")))
        add(
            if (w.zeroDays == 0) Triple("📺", tx("7/7", "7/7"), tx("TV her gün açıldı", "the TV was on every day"))
            else Triple("😴", tx("${w.zeroDays} gün", "${w.zeroDays} days"), tx("TV hiç açılmadı", "with the TV off"))
        )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Kicker(tx("Haftanın rekorları", "Records of the week"), Modifier.reveal(0))
        Headline(tx("Bunları biliyor muydunuz?", "Did you know?"), Modifier.reveal(150))
        Spacer(Modifier.height(18.dp))
        tiles.chunked(3).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                row.forEachIndexed { c, (e, v, l) -> StatTile(v, l, Modifier.pop(500 + (r * 3 + c) * 220), e, wide = true) }
            }
        }
    }
}

// ---- 8. LiteDroid anları ----
private data class Moment(val emoji: String, val value: String, val label: String)

private fun champion(w: WeekData, prefix: String): String? =
    w.counters.filterKeys { it.startsWith(prefix) }.maxByOrNull { it.value }?.key?.removePrefix(prefix)

private fun moments(w: WeekData): List<Moment> = buildList {
    val games = w.counters.filterKeys { it.startsWith("game.") }
    if (games.isNotEmpty()) {
        val topName = games.maxByOrNull { it.value }?.key?.removePrefix("game.")
        val title = runCatching { BuiltInGame.valueOf(topName ?: "") .title }.getOrNull()
        add(Moment("🎮", "${games.values.sum()}", tx("oyun açıldı", "games played") + (title?.let { tx(" · en çok $it", " · mostly $it") } ?: "")))
    }
    if (w.records.isNotEmpty()) add(Moment("🏆", "${w.records.size}", tx("rekor kırıldı", "records broken")))
    (w.counters["quiz.games"] ?: 0).takeIf { it > 0 }?.let { n ->
        add(Moment("🧠", "$n", tx("Bilgi Yarışması", "Quiz Nights") + (champion(w, "quiz.win.")?.let { tx(" · şampiyon $it", " · champ $it") } ?: "")))
    }
    val drawGames = w.counters["draw.games"] ?: 0
    val drawRounds = (w.counters["draw.rounds"] ?: 0) + (w.counters["draw.ai"] ?: 0)
    if (drawGames > 0 || drawRounds > 0) {
        add(Moment("🎨", "${maxOf(drawGames, 1)}", tx("Çiz-Bil · $drawRounds tur çizildi", "Draw & Guess · $drawRounds rounds") + (champion(w, "draw.win.")?.let { tx(" · usta $it", " · star $it") } ?: "")))
    }
    val stories = (w.counters["story.new"] ?: 0) + (w.counters["story.replay"] ?: 0)
    if (stories > 0) add(Moment("🌙", "$stories", tx("masal dinlendi", "stories") + (champion(w, "story.hero.")?.let { tx(" · gözde kahraman $it", " · favorite hero $it") } ?: "")))
    (w.counters["claude.ask"] ?: 0).takeIf { it > 0 }?.let { add(Moment("✨", "$it", tx("soru soruldu", "questions asked"))) }
    (w.counters["sleep.off"] ?: 0).takeIf { it > 0 }?.let { add(Moment("😴", "$it", tx("kez uyku zamanlayıcısı TV'yi kapattı", "sleep timer switch-offs"))) }
    (w.times["saver"] ?: 0L).takeIf { it >= 60_000 }?.let { add(Moment("🖼️", dur(it), tx("ekran koruyucu", "screensaver"))) }
    (w.counters["shot"] ?: 0).takeIf { it > 0 }?.let { add(Moment("📸", "$it", tx("ekran görüntüsü", "screenshots"))) }
    val tools = w.counters.filterKeys { it.startsWith("tool.") }
    if (tools.isNotEmpty()) {
        val t = runCatching { BuiltInGame.valueOf(tools.maxByOrNull { it.value }!!.key.removePrefix("tool.")).title }.getOrNull()
        add(Moment("🛠️", "${tools.values.sum()}", tx("kez araç açıldı", "tool uses") + (t?.let { tx(" · en çok $it", " · mostly $it") } ?: "")))
    }
    if (w.litedroidMs >= 5 * 60_000) add(Moment("🏠", dur(w.litedroidMs), tx("LiteDroid'de", "in LiteDroid")))
}.take(8)

@Composable
private fun MomentsPage(w: WeekData, prev: WeekData?) {
    val list = moments(w)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Kicker(tx("LiteDroid anları", "LiteDroid moments"), Modifier.reveal(0))
        Headline(tx("Evde neler oldu?", "What happened at home?"), Modifier.reveal(150))
        if (prev != null && prev.gamesPlayed > 0 && w.gamesPlayed > 0) {
            Spacer(Modifier.height(6.dp))
            DeltaChip(w.gamesPlayed.toLong(), prev.gamesPlayed.toLong(), Modifier.reveal(400))
        }
        Spacer(Modifier.height(16.dp))
        list.chunked(4).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                row.forEachIndexed { c, m -> StatTile(m.value, m.label, Modifier.width(196.dp).pop(500 + (r * 4 + c) * 200), m.emoji) }
            }
        }
    }
}

// ---- 9. Canlı Tablo galerisi ----
@Composable
private fun GalleryPage(w: WeekData) {
    val ctx = LocalContext.current
    val images by produceState<List<ImageBitmap>>(emptyList(), w) {
        value = withContext(Dispatchers.IO) {
            w.paintings.mapNotNull { id ->
                val f = File(ctx.filesDir, "canvas/$id.jpg")
                if (!f.exists()) null else runCatching {
                    BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
                }.getOrNull()
            }.take(6)
        }
    }
    val drift by rememberInfiniteTransition(label = "kb").animateFloat(1f, 1.12f, infiniteRepeatable(tween(9_000), RepeatMode.Reverse), label = "z")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Kicker(tx("Canlı Tablo", "Living Painting"), Modifier.reveal(0))
        Headline(tx("Bu hafta ekranınıza bunlar çizildi", "Painted on your screen this week"), Modifier.reveal(150), size = 30)
        Spacer(Modifier.height(16.dp))
        images.chunked(3).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                row.forEachIndexed { c, img ->
                    Box(Modifier.width(240.dp).aspectRatio(16f / 9f).pop(400 + (r * 3 + c) * 250).clip(RoundedCornerShape(16.dp)).border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))) {
                        Image(img, null, Modifier.fillMaxSize().graphicsLayer { scaleX = drift; scaleY = drift }, contentScale = ContentScale.Crop)
                    }
                }
            }
        }
    }
}

// ---- 10. Maç haftası ----
@Composable
private fun MatchesPage(w: WeekData) {
    val watched = w.matches.count { it.watched }
    Column(Modifier.fillMaxWidth(0.8f), horizontalAlignment = Alignment.CenterHorizontally) {
        Kicker(tx("Maç haftası", "Match week"), Modifier.reveal(0))
        Headline(tx("Takımlarınız sahadaydı ⚽", "Your teams were on the pitch ⚽"), Modifier.reveal(150), size = 32)
        Text(
            tx("${w.matches.size} maçın $watched tanesinde TV açıktı", "The TV was on for $watched of ${w.matches.size} matches"),
            color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.reveal(400),
        )
        Spacer(Modifier.height(14.dp))
        w.matches.take(5).forEachIndexed { i, m ->
            val mine = if (m.side == "away") m.awayScore - m.homeScore else m.homeScore - m.awayScore
            val (res, rc) = when {
                m.side == "both" -> tx("Derbi", "Derby") to Gold
                mine > 0 -> tx("Galibiyet", "Win") to Mint
                mine < 0 -> tx("Mağlubiyet", "Loss") to Coral
                else -> tx("Beraberlik", "Draw") to Color.White
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).reveal(600 + i * 220, 14.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(dayShort(dayOf(m.start, w)), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.width(36.dp))
                Text(m.home, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                m.homeLogo?.let { AsyncImage(it, null, Modifier.size(24.dp)) }
                Text(" ${m.homeScore} - ${m.awayScore} ", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                m.awayLogo?.let { AsyncImage(it, null, Modifier.size(24.dp)) }
                Spacer(Modifier.width(8.dp))
                Text(m.away, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                Text(res, color = Color(0xFF111114), fontSize = 11.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(rc).padding(horizontal = 8.dp, vertical = 2.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (m.watched) "📺" else "💤", fontSize = 16.sp)
            }
        }
    }
}

// ---- 11. Özet kartı ----
@Composable
private fun SummaryPage(w: WeekData, prev: WeekData?, icons: IconCache, sel: Int, capturing: Boolean, shareUrl: String?, shareError: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Paylaşmalık kart
        Column(
            Modifier
                .width(470.dp)
                .pop(100)
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF241056), Color(0xFF5A1250), Color(0xFF0E2A63))))
                .border(2.dp, Brush.linearGradient(listOf(Gold, Color(0xFFFF6FB5), Color(0xFF8EC5FF))), RoundedCornerShape(30.dp))
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(w.name, 52.dp, 2.dp, 22)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(w.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(rangeLabel(w) + (if (w.preview) tx(" · şimdiye kadar", " · so far") else ""), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                Text(w.persona.emoji, fontSize = 34.sp)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(tx("TV BAŞINDA", "SCREEN TIME"), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(if (w.totalMs > 0) dur(w.totalMs) else "—", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(tx("TİP", "TYPE"), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(w.persona.title, color = Gold, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            if (prev != null && prev.totalMs > 0 && w.totalMs > 0) {
                val pct = ((w.totalMs - prev.totalMs) * 100 / prev.totalMs).toInt()
                Text(
                    if (pct >= 0) tx("Geçen haftadan %$pct fazla", "$pct% more than last week") else tx("Geçen haftadan %${-pct} az", "${-pct}% less than last week"),
                    color = if (pct >= 0) Mint else Coral, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
            if (w.apps.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                w.apps.take(3).forEachIndexed { i, a ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", color = if (i == 0) Gold else Color.White.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(22.dp))
                        AppIcon(icons, a.pkg, a.label, 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(a.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(dur(a.ms), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                }
            }
            val extra = moments(w).take(3)
            if (extra.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    extra.forEach { m ->
                        Text("${m.emoji} ${m.value}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("LiteDroid Wrapped", style = TextStyle(brush = Brush.linearGradient(listOf(Gold, Color(0xFFFF6FB5))), fontSize = 13.sp, fontWeight = FontWeight.Black))
        }
        if (!capturing) {
            Spacer(Modifier.width(30.dp))
            Column(Modifier.width(250.dp)) {
                ActionChip("📲  " + tx("Telefona al", "Send to phone"), sel == 0)
                Spacer(Modifier.height(10.dp))
                ActionChip("↻  " + tx("Baştan izle", "Watch again"), sel == 1)
                Spacer(Modifier.height(16.dp))
                if (shareUrl != null) {
                    Box(Modifier.size(150.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(8.dp)) { QrCode(shareUrl, 134.dp) }
                    Text(tx("Telefonun kamerasıyla okut. Aynı Wi-Fi'da olmalısın.", "Scan with your phone camera (same Wi-Fi)."), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
                shareError?.let { Text(it, color = Coral, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, selected: Boolean) {
    Text(
        label, color = if (selected) Color(0xFF111114) else Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}
