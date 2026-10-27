package dev.litedroid.launcher.quiz

import androidx.compose.runtime.key
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.isSelectKey
import dev.litedroid.launcher.ui.onRemoteSelect
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Cevap renkleri ve şekilleri (telefondakilerle aynı sırada). */
private val AnswerColors = listOf(Color(0xFFE5374F), Color(0xFF2F6FDC), Color(0xFFE0A100), Color(0xFF2C9A3F))
private val Bg = Brush.verticalGradient(listOf(Color(0xFF1A1640), Color(0xFF0B0B1A), Color(0xFF05060C)))
private val CardBg = Color(0xFF15172C)
private val Accent = Color(0xFF6B4DFF)
private val Gold = Color(0xFFFFD35C)

/**
 * Bilgi yarışması (Kahoot tarzı). Telefonlar QR koddaki adresten katılır; TV soruyu, süreyi,
 * kimin cevapladığını, doğru cevabı ve canlı puan tablosunu gösterir.
 * Kumanda: Tamam = bekleme aşamasını geç · Geri = lobiye dön (lobideyken oyundan çık).
 */
@Composable
fun QuizScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val game = remember { QuizGame(context) }
    val server = remember { QuizServer(context, game) }
    var url by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        server.start()
        url = server.url
        onDispose {
            server.stop()
            game.close()
        }
    }
    // Ağ değişirse (ör. Wi-Fi geç bağlandıysa) adresi yenile
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            server.url?.let { if (it != url) url = it }
        }
    }
    val state by game.state.collectAsState()

    // Oyun sürerken Geri lobiye döner; lobideyken dış katman oyunu kapatır
    BackHandler(enabled = state.phase != QuizPhase.LOBBY) { game.backToLobby() }

    Box(modifier.fillMaxSize().background(Bg)) {
        AnimatedContent(
            targetState = state.phase.ordinal to state.index,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
            label = "quizPhase",
        ) { (phaseOrdinal, _) ->
            when (QuizPhase.entries[phaseOrdinal]) {
                QuizPhase.LOBBY -> Lobby(game, state, url)
                QuizPhase.GET_READY -> PlaySurface(game) { GetReady(state) }
                QuizPhase.QUESTION, QuizPhase.REVEAL -> PlaySurface(game) { QuestionView(state) }
                QuizPhase.SCOREBOARD -> PlaySurface(game) { Scoreboard(state) }
                QuizPhase.FINISHED -> Finished(game, state)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Lobi
// ---------------------------------------------------------------------------

@Composable
private fun Lobby(game: QuizGame, state: QuizState, url: String?) {
    val startFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { startFocus.requestFocus() } }
    Row(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 28.dp)) {
        // ---- Sol: QR ve adres ----
        Column(
            Modifier
                .width(300.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(28.dp))
                .background(CardBg)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(tx("Telefonunla katıl", "Join with your phone"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                tx("Kamerayla QR kodu okut", "Scan the QR code with your camera"),
                color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
            )
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (url != null) QrCode(url, 180.dp)
                else Text(tx("Ağ bağlantısı yok", "No network"), color = Color(0xFF111114), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(14.dp))
            Text(tx("ya da tarayıcıya yaz:", "or type in a browser:"), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            Text(
                url?.removePrefix("http://") ?: "—",
                color = Gold, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                tx("Telefonlar TV ile aynı Wi-Fi ağında olmalı. Android ve iPhone, uygulama gerekmez.",
                    "Phones must be on the same Wi-Fi as the TV. Android and iPhone, no app needed."),
                color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 15.sp,
            )
        }
        Spacer(Modifier.width(26.dp))

        // ---- Sağ: oyuncular ve ayarlar ----
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧠", fontSize = 34.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(tx("Bilgi Yarışması", "Quiz Night"), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        tx("${state.players.size} oyuncu · en fazla ${QuizGame.MAX_PLAYERS}", "${state.players.size} players · up to ${QuizGame.MAX_PLAYERS}"),
                        color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            // Oyuncu yuvaları
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 0 until 4) PlayerSlot(state.players.getOrNull(i), Modifier.weight(1f))
            }
            if (state.players.size > 3) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (i in 4 until 8) PlayerSlot(state.players.getOrNull(i), Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(18.dp))
            OptionLabel(tx("Kategori", "Category"))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 20.dp)) {
                items(QuizCategory.entries.toList(), key = { it.name }) { c ->
                    QuizChip(c.emoji + "  " + c.label, selected = state.category == c) { game.setCategory(c) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Column {
                    OptionLabel(tx("Zorluk", "Level"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuizLevel.entries.forEach { l -> QuizChip(l.label, selected = state.level == l) { game.setLevel(l) } }
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column {
                    OptionLabel(tx("Soru sayısı", "Questions"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 20).forEach { n -> QuizChip("$n", selected = state.count == n) { game.setCount(n) } }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            val available = remember(state.category, state.level) { game.available(state.category, state.level) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StartButton(
                    if (state.players.isEmpty()) tx("Oyuncuları bekliyoruz…", "Waiting for players…")
                    else tx("Başla · ${state.players.size} oyuncu", "Start · ${state.players.size} players"),
                    enabled = state.players.isNotEmpty(),
                    modifier = Modifier.focusRequester(startFocus),
                ) { game.start() }
                Spacer(Modifier.width(16.dp))
                Text(
                    tx("Havuzda bu seçime uygun $available soru var. Geri: yarışmadan çık",
                        "$available questions match. Back: leave the quiz"),
                    color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
internal fun OptionLabel(text: String) {
    Text(
        text.uppercase(), color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
internal fun PlayerSlot(p: QuizPlayer?, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    if (p == null) {
        Box(
            modifier
                .height(92.dp)
                .clip(shape)
                .border(1.5.dp, Color.White.copy(alpha = 0.12f), shape)
                .background(Color.White.copy(alpha = 0.03f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(tx("Bekleniyor", "Waiting"), color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp)
        }
        return
    }
    // Katılınca küçük bir zıplama
    val pop = remember(p.id) { Animatable(0.6f) }
    LaunchedEffect(p.id) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
    Column(
        modifier
            .height(92.dp)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .clip(shape)
            .background(Color(p.color).copy(alpha = 0.22f))
            .border(1.5.dp, Color(p.color).copy(alpha = 0.8f), shape)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(p.avatar, fontSize = 34.sp)
        Text(p.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun QuizChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    focused -> Color.White
                    selected -> Accent
                    else -> Color.White.copy(alpha = 0.08f)
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onClick)
            .focusable()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (focused) Color(0xFF111114) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
internal fun StartButton(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val glow by rememberInfiniteTransition(label = "start").animateFloat(
        0.85f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow",
    )
    Box(
        modifier
            .scale(if (focused && enabled) glow * 1.04f else 1f)
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    !enabled -> Color.White.copy(alpha = 0.08f)
                    focused -> Color.White
                    else -> Color(0xFF8B5CF6)
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect({ if (enabled) onClick() })
            .focusable()
            .padding(horizontal = 30.dp, vertical = 14.dp),
    ) {
        Text(
            label,
            color = when {
                !enabled -> Color.White.copy(alpha = 0.5f)
                focused -> Color(0xFF111114)
                else -> Color.White
            },
            fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
        )
    }
}

/** QR kod: ZXing ile üretilir, kare modüller olarak çizilir. */
@Composable
fun QrCode(text: String, size: Dp) {
    val matrix = remember(text) {
        runCatching {
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
        }.getOrNull()
    }
    Canvas(Modifier.size(size)) {
        val m = matrix ?: return@Canvas
        val cell = this.size.width / m.width
        for (y in 0 until m.height) for (x in 0 until m.width) {
            if (m.get(x, y)) drawRect(Color(0xFF111114), Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
        }
    }
}

// ---------------------------------------------------------------------------
// Oyun aşamaları
// ---------------------------------------------------------------------------

/** Oyun sırasında odağı tutar: Tamam bekleme aşamasını geçer. */
@Composable
private fun PlaySurface(game: QuizGame, content: @Composable BoxScope.() -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    var armed by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(fr)
            .onPreviewKeyEvent { e ->
                if (!e.key.isSelectKey) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyDown) armed = true
                if (e.type == KeyEventType.KeyUp && armed) {
                    armed = false
                    game.skip()
                }
                true
            }
            .focusable(),
        content = content,
    )
}

/** Aşamanın kalan süresi (ms), her karede güncellenir. */
@Composable
internal fun rememberRemaining(endsAt: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(100)
        }
    }
    return (endsAt - now).coerceAtLeast(0)
}

@Composable
private fun BoxScope.GetReady(state: QuizState) {
    val q = state.question ?: return
    val remaining = rememberRemaining(state.phaseEndsAt)
    val n = max(1, ((remaining + 999) / 1000).toInt())
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            tx("SORU ${state.index + 1} / ${state.count}", "QUESTION ${state.index + 1} / ${state.count}"),
            color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(q.category.emoji, fontSize = 72.sp)
        Text(q.category.label, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text(QuizLevel.of(q.level).label, color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        AnimatedContent(targetState = n, transitionSpec = { scaleIn(tween(300), initialScale = 1.8f) + fadeIn() togetherWith fadeOut(tween(150)) }, label = "count") { v ->
            Box(
                Modifier.size(110.dp).clip(CircleShape).background(Accent),
                contentAlignment = Alignment.Center,
            ) { Text("$v", color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
}

@Composable
private fun BoxScope.QuestionView(state: QuizState) {
    val q = state.question ?: return
    val reveal = state.phase == QuizPhase.REVEAL
    val remaining = rememberRemaining(state.phaseEndsAt)
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
        // Üst bilgi
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tx("Soru ${state.index + 1}/${state.count}", "Question ${state.index + 1}/${state.count}") + "  ·  " + q.category.emoji + " " + q.category.label + "  ·  " + QuizLevel.of(q.level).label,
                color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            )
            if (!reveal) TimerRing(remaining, state.phaseMs)
            else Text(tx("Tamam: devam", "OK: continue"), color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(14.dp))
        // Soru
        Box(
            Modifier
                .fillMaxWidth()
                .weight(0.9f)
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White)
                .padding(horizontal = 30.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                q.text, color = Color(0xFF15172A), fontSize = if (q.text.length > 90) 24.sp else 30.sp,
                fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, lineHeight = 36.sp,
            )
        }
        Spacer(Modifier.height(14.dp))
        // Cevaplar 2x2
        val pickers = q.options.indices.map { i -> state.players.filter { it.choice == i } }
        Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (row in 0..1) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (col in 0..1) {
                        val i = row * 2 + col
                        AnswerTile(
                            index = i,
                            text = q.options.getOrElse(i) { "" },
                            reveal = reveal,
                            correct = i == q.correct,
                            pickers = if (reveal) pickers[i] else emptyList(),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // Oyuncular: kim cevapladı / kim kazandı
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!reveal) {
                Text(
                    tx("${state.players.count { it.choice != null }}/${state.players.size} cevapladı", "${state.players.count { it.choice != null }}/${state.players.size} answered"),
                    color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            state.players.forEach { p -> PlayerBadge(p, reveal) }
        }
    }
}

@Composable
internal fun TimerRing(remaining: Long, total: Long) {
    val secs = ((remaining + 999) / 1000).toInt()
    val urgent = remaining < 5_000
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(1f, 1.12f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "p")
    Box(Modifier.size(58.dp).scale(if (urgent) pulse else 1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(58.dp)) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
            drawArc(
                if (urgent) Color(0xFFFF5C6E) else Gold, -90f, 360f * (remaining.toFloat() / total.coerceAtLeast(1)), false,
                Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Text("$secs", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun AnswerTile(index: Int, text: String, reveal: Boolean, correct: Boolean, pickers: List<QuizPlayer>, modifier: Modifier) {
    val alpha by animateFloatAsState(if (reveal && !correct) 0.28f else 1f, tween(400), label = "a")
    val bump by animateFloatAsState(if (reveal && correct) 1.03f else 1f, spring(dampingRatio = 0.4f), label = "b")
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .graphicsLayer { this.alpha = alpha; scaleX = bump; scaleY = bump }
            .clip(shape)
            .background(AnswerColors[index])
            .then(if (reveal && correct) Modifier.border(4.dp, Color.White, shape) else Modifier)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnswerShape(index, 34.dp)
        Spacer(Modifier.width(14.dp))
        Text(
            text, color = Color.White, fontSize = if (text.length > 40) 17.sp else 21.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp,
        )
        if (reveal) {
            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                pickers.take(5).forEach { p ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.25f)).border(2.dp, Color(p.color), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(p.avatar, fontSize = 17.sp) }
                }
            }
            if (correct) {
                Spacer(Modifier.width(8.dp))
                Text("✓", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** Kahoot'takine benzer ama kendi çizimimiz: üçgen, karo, daire, kare. */
@Composable
private fun AnswerShape(index: Int, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val c = Color.White
        when (index) {
            0 -> drawPath(Path().apply { moveTo(w / 2, w * 0.1f); lineTo(w * 0.92f, w * 0.88f); lineTo(w * 0.08f, w * 0.88f); close() }, c)
            1 -> drawPath(Path().apply { moveTo(w / 2, w * 0.05f); lineTo(w * 0.95f, w / 2); lineTo(w / 2, w * 0.95f); lineTo(w * 0.05f, w / 2); close() }, c)
            2 -> drawCircle(c, w * 0.42f)
            else -> drawRect(c, Offset(w * 0.12f, w * 0.12f), Size(w * 0.76f, w * 0.76f))
        }
    }
}

@Composable
private fun PlayerBadge(p: QuizPlayer, reveal: Boolean) {
    val answered = p.choice != null
    val pop by animateFloatAsState(if (answered) 1f else 0.85f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium), label = "pb")
    Row(
        Modifier
            .graphicsLayer { scaleX = pop; scaleY = pop; alpha = if (answered || reveal) 1f else 0.45f }
            .clip(RoundedCornerShape(50))
            .background(Color(p.color).copy(alpha = 0.25f))
            .border(1.5.dp, Color(p.color), RoundedCornerShape(50))
            .padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(p.avatar, fontSize = 18.sp)
        Spacer(Modifier.width(6.dp))
        Text(p.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (reveal) {
            Spacer(Modifier.width(8.dp))
            when (p.lastCorrect) {
                true -> Text("+${p.lastGain}", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                false -> Text("✗", color = Color(0xFFFF7B8A), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                null -> Text("⏱", fontSize = 13.sp)
            }
        } else if (answered) {
            Spacer(Modifier.width(6.dp))
            Text("✓", color = Color(0xFF7CF0A8), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/**
 * Puan tablosu: her satır önceki sırasından yeni sırasına kayarak gelir, puanlar sayarak artar,
 * yükselen oyuncunun yanında yeşil ok görünür. Lider taç takar.
 */
@Composable
private fun BoxScope.Scoreboard(state: QuizState) {
    val ranked = state.ranked
    val rowH = 58.dp
    val gap = 10.dp
    val density = LocalDensity.current
    val step = with(density) { (rowH + gap).toPx() }
    val top = ranked.firstOrNull()?.score?.coerceAtLeast(1) ?: 1
    Column(Modifier.align(Alignment.TopCenter).padding(top = 30.dp).width(720.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tx("Puan durumu", "Leaderboard"), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            tx("Soru ${state.index + 1}/${state.count} sonrası · Tamam: devam", "After question ${state.index + 1}/${state.count} · OK: continue"),
            color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp,
        )
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height((rowH + gap) * ranked.size.coerceAtLeast(1))) {
            ranked.forEachIndexed { newIndex, p -> key(p.id) {
                val from = (p.prevRank - 1).coerceAtLeast(0)
                val y = remember(p.id) { Animatable(from * step) }
                LaunchedEffect(p.id, newIndex) {
                    delay(500)
                    y.animateTo(newIndex * step, tween(900, easing = FastOutSlowInEasing))
                }
                // Puan önceki değerden sayarak artar
                val shown = remember(p.id) { Animatable((p.score - p.lastGain).toFloat()) }
                LaunchedEffect(p.id, p.score) {
                    delay(300)
                    shown.animateTo(p.score.toFloat(), tween(1200, easing = LinearEasing))
                }
                val display = shown.value.toInt()
                val climbed = p.prevRank - (newIndex + 1)
                Row(
                    Modifier
                        .graphicsLayer { translationY = y.value }
                        .fillMaxWidth()
                        .height(rowH)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (newIndex == 0) Brush.horizontalGradient(listOf(Color(0xFF5B3FE0), Color(0xFF8B5CF6))) else Brush.horizontalGradient(listOf(CardBg, CardBg)))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${newIndex + 1}", color = Color.White.copy(alpha = 0.7f), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(34.dp))
                    Text(if (newIndex == 0) "👑" else "", fontSize = 18.sp, modifier = Modifier.width(26.dp))
                    Text(p.avatar, fontSize = 28.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        // Puan çubuğu
                        val frac = shown.value / top.toFloat()
                        Box(Modifier.fillMaxWidth(0.9f).height(5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
                            Box(Modifier.fillMaxWidth(frac.coerceIn(0.01f, 1f)).height(5.dp).clip(RoundedCornerShape(50)).background(Color(p.color)))
                        }
                    }
                    if (climbed > 0) Text("▲$climbed", color = Color(0xFF7CF0A8), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(end = 10.dp))
                    if (p.lastGain > 0) Text("+${p.lastGain}", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
                    Text("$display", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
            } }
        }
    }
}

// ---------------------------------------------------------------------------
// Final: kürsü ve konfeti
// ---------------------------------------------------------------------------

@Composable
private fun Finished(game: QuizGame, state: QuizState) {
    val ranked = state.ranked
    val again = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(400)
        runCatching { again.requestFocus() }
    }
    Box(Modifier.fillMaxSize()) {
        Confetti()
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tx("Yarışma bitti!", "That's a wrap!"), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            ranked.firstOrNull()?.let {
                Text(tx("Şampiyon: ${it.avatar} ${it.name}", "Champion: ${it.avatar} ${it.name}"), color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            // Kürsü: 2 - 1 - 3
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PodiumColumn(ranked.getOrNull(1), 2, 150.dp, 400)
                PodiumColumn(ranked.getOrNull(0), 1, 210.dp, 900)
                PodiumColumn(ranked.getOrNull(2), 3, 110.dp, 0)
            }
            if (ranked.size > 3) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ranked.drop(3).forEachIndexed { i, p ->
                        Text("${i + 4}. ${p.avatar} ${p.name} · ${p.score}", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StartButton(tx("Tekrar oyna", "Play again"), enabled = true, modifier = Modifier.focusRequester(again)) { game.start() }
                StartButton(tx("Lobiye dön", "Back to lobby"), enabled = true, modifier = Modifier) { game.backToLobby() }
            }
        }
    }
}

@Composable
internal fun PodiumColumn(p: QuizPlayer?, place: Int, height: Dp, delayMs: Int) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        rise.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "b")
    val colors = when (place) {
        1 -> listOf(Color(0xFFFFD35C), Color(0xFFE0A100))
        2 -> listOf(Color(0xFFD9DEE8), Color(0xFF9AA3B5))
        else -> listOf(Color(0xFFE39A5B), Color(0xFFA9622B))
    }
    Column(Modifier.width(170.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (p != null) {
            Text(
                p.avatar, fontSize = if (place == 1) 64.sp else 50.sp,
                modifier = Modifier.offset(y = (-6 * sin(bob * Math.PI)).dp).graphicsLayer { alpha = rise.value },
            )
            Text(p.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = rise.value })
            Text("${p.score}", color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.graphicsLayer { alpha = rise.value })
            Spacer(Modifier.height(6.dp))
        }
        Box(
            Modifier
                .width(150.dp)
                .height(height * rise.value)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(Brush.verticalGradient(colors)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text("$place", color = Color.White.copy(alpha = 0.9f), fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Hafif konfeti: 60 parça, tek Canvas, sonsuz döngü. */
@Composable
internal fun Confetti() {
    val pieces = remember {
        List(60) {
            floatArrayOf(Random.nextFloat(), Random.nextFloat(), 0.5f + Random.nextFloat(), Random.nextFloat() * 360f, Random.nextInt(5).toFloat())
        }
    }
    val colors = listOf(Color(0xFFFF5C8A), Gold, Color(0xFF4D9BFF), Color(0xFF3BE07F), Color(0xFFB06BFF))
    val t by rememberInfiniteTransition(label = "confetti").animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "t")
    Canvas(Modifier.fillMaxSize()) {
        pieces.forEach { p ->
            val y = ((p[1] + t * p[2]) % 1f) * size.height
            val x = p[0] * size.width + sin((t * 6.28f * 2 + p[3])) * 20f
            val c = colors[p[4].toInt()]
            drawRect(c, Offset(x, y), Size(9f, 14f))
        }
    }
}
