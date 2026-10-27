package dev.litedroid.launcher.draw

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.quiz.Confetti
import dev.litedroid.launcher.quiz.OptionLabel
import dev.litedroid.launcher.quiz.PlayerSlot
import dev.litedroid.launcher.quiz.PodiumColumn
import dev.litedroid.launcher.quiz.QrCode
import dev.litedroid.launcher.quiz.QuizChip
import dev.litedroid.launcher.quiz.QuizPlayer
import dev.litedroid.launcher.quiz.StartButton
import dev.litedroid.launcher.quiz.TimerRing
import dev.litedroid.launcher.quiz.rememberRemaining
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.isSelectKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.sin

private val Bg = Brush.verticalGradient(listOf(Color(0xFF14233F), Color(0xFF0A0F1E), Color(0xFF05060C)))
private val CardBg = Color(0xFF121A2E)
private val Gold = Color(0xFFFFD35C)
private val Mint = Color(0xFF3BE07F)
private val Paper = Color(0xFFFFFDF7)

private fun DrawPlayer.asQuiz() = QuizPlayer(id, name, avatar, color, score)

/**
 * Çiz-Bil (Pictionary tarzı). Telefonlar QR'dan katılır; sırası gelen telefonunda gizli kelimeyi çizer,
 * çizim burada canlı belirir, diğerleri telefonlarından tahmin eder. Yapay zekâ turunda resmi
 * Gemini çizer ve pikseller yavaş yavaş netleşir.
 * Kumanda: Tamam = beklemeyi geç / turu bitir · Geri = lobiye dön (lobideyken oyundan çık).
 */
@Composable
fun DrawScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val game = remember { DrawGame(context) }
    val server = remember { DrawServer(context, game) }
    var url by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        server.start()
        url = server.url
        onDispose {
            server.stop()
            game.close()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            server.url?.let { if (it != url) url = it }
        }
    }
    val state by game.state.collectAsState()
    BackHandler(enabled = state.phase != DrawPhase.LOBBY) { game.backToLobby() }

    Box(modifier.fillMaxSize().background(Bg)) {
        AnimatedContent(
            targetState = when (state.phase) {
                DrawPhase.LOBBY -> 0
                DrawPhase.FINISHED -> 2
                else -> 1
            },
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
            label = "drawPhase",
        ) { screen ->
            when (screen) {
                0 -> DrawLobby(game, state, url)
                2 -> DrawFinished(game, state)
                else -> PlaySurface({ game.skip() }) { Playing(game, state) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Lobi
// ---------------------------------------------------------------------------

@Composable
private fun DrawLobby(game: DrawGame, state: DrawState, url: String?) {
    val startFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { startFocus.requestFocus() } }
    Row(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 28.dp)) {
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
            Text(tx("Kamerayla QR kodu okut", "Scan the QR code with your camera"), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.size(200.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (url != null) QrCode(url, 180.dp)
                else Text(tx("Ağ bağlantısı yok", "No network"), color = Color(0xFF111114), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(14.dp))
            Text(tx("ya da tarayıcıya yaz:", "or type in a browser:"), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            Text(url?.removePrefix("http://") ?: "—", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(
                tx("Sırası gelen telefonunda çizer, çizim TV'de canlı görünür. Diğerleri telefondan tahmin yazar.",
                    "Whoever's turn it is draws on their phone, live on the TV. Everyone else types guesses."),
                color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 15.sp,
            )
        }
        Spacer(Modifier.width(26.dp))

        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎨", fontSize = 34.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(tx("Çiz-Bil", "Draw & Guess"), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        tx("${state.players.size} oyuncu · en fazla ${DrawGame.MAX_PLAYERS}", "${state.players.size} players · up to ${DrawGame.MAX_PLAYERS}"),
                        color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 0 until 4) PlayerSlot(state.players.getOrNull(i)?.asQuiz(), Modifier.weight(1f))
            }
            if (state.players.size > 3) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (i in 4 until 8) PlayerSlot(state.players.getOrNull(i)?.asQuiz(), Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Top) {
                Column {
                    OptionLabel(tx("Herkes kaç kez çizsin", "Turns each"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3).forEach { n -> QuizChip("$n", selected = state.roundsEach == n) { game.setRoundsEach(n) } }
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column {
                    OptionLabel(tx("Çizim süresi", "Drawing time"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(60, 80, 100).forEach { n -> QuizChip(tx("$n sn", "${n}s"), selected = state.drawSeconds == n) { game.setDrawSeconds(n) } }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OptionLabel(tx("Yapay zekâ çizsin turu", "AI draws round"))
            if (state.aiAvailable) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    QuizChip("🤖 " + tx("Açık", "On"), selected = state.aiRound) { game.setAiRound(true) }
                    QuizChip(tx("Kapalı", "Off"), selected = !state.aiRound) { game.setAiRound(false) }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        tx("Her turun sonunda yapay zekâ çizer, herkes birlikte tahmin eder", "After each round the AI draws and everyone guesses"),
                        color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                    )
                }
            } else {
                Text(
                    tx("Bu tur için Ayarlar → Yapay zekâ'dan bir Gemini anahtarı ekle.", "Add a Gemini key in Settings → AI to enable this round."),
                    color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val n = state.players.size
                val ready = game.canStart(state)
                StartButton(
                    when {
                        n == 0 -> tx("Oyuncuları bekliyoruz…", "Waiting for players…")
                        !ready -> tx("En az 2 oyuncu gerekli", "At least 2 players needed")
                        else -> tx("Başla · $n oyuncu", "Start · $n players")
                    },
                    enabled = ready,
                    modifier = Modifier.focusRequester(startFocus),
                ) { game.start() }
                Spacer(Modifier.width(16.dp))
                Text(
                    if (n == 1 && ready) tx("Tek kişiyle yalnızca yapay zekâ çizer. Geri: oyundan çık", "Solo: only the AI draws. Back: leave")
                    else tx("${DrawWords.all.size} kelime · Geri: oyundan çık", "${DrawWords.all.size} words · Back: leave"),
                    color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Oyun
// ---------------------------------------------------------------------------

@Composable
private fun PlaySurface(onSkip: () -> Unit, content: @Composable BoxScope.() -> Unit) {
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
                    onSkip()
                }
                true
            }
            .focusable(),
        content = content,
    )
}

@Composable
private fun BoxScope.Playing(game: DrawGame, state: DrawState) {
    val remaining = rememberRemaining(state.phaseEndsAt)
    Row(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
        // ---- Sol: puan durumu ----
        Column(Modifier.width(200.dp).fillMaxHeight()) {
            Text(
                tx("TUR ${state.round + 1}/${state.totalRounds}", "ROUND ${state.round + 1}/${state.totalRounds}"),
                color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.ranked.forEach { p -> key(p.id) { ScoreRow(p, state) } }
            }
        }
        Spacer(Modifier.width(18.dp))

        // ---- Orta: tahta ----
        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            TopLine(state)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    state.phase == DrawPhase.AI_LOADING -> AiLoading(state)
                    state.phase == DrawPhase.CHOOSE -> Choosing(state, remaining)
                    state.isAi -> {
                        val bytes by game.aiImage.collectAsState()
                        AiPicture(bytes, state, remaining)
                    }
                    else -> StrokeBoard(game.board, Modifier.fillMaxHeight().aspectRatio(1f))
                }
                if (state.phase == DrawPhase.REVEAL) RevealBanner(state)
            }
        }
        Spacer(Modifier.width(18.dp))

        // ---- Sağ: süre ve tahminler ----
        Column(Modifier.width(220.dp).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.phase == DrawPhase.DRAW || state.phase == DrawPhase.CHOOSE) TimerRing(remaining, state.phaseMs)
                Spacer(Modifier.width(10.dp))
                Column {
                    val g = state.guessers
                    Text(
                        tx("${g.count { it.guessed }}/${g.size} bildi", "${g.count { it.guessed }}/${g.size} got it"),
                        color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    )
                    Text(tx("Tamam: turu geç", "OK: skip"), color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                tx("TAHMİNLER", "GUESSES"),
                color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(6.dp))
            if (state.feed.isEmpty()) {
                Text(
                    tx("Telefonlardan yazılan tahminler burada akar", "Guesses typed on phones show up here"),
                    color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp, lineHeight = 16.sp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                state.feed.reversed().forEach { f -> key(f.id) { FeedRow(f) } }
            }
        }
    }
}

@Composable
private fun ScoreRow(p: DrawPlayer, state: DrawState) {
    val drawing = !state.isAi && p.id == state.drawerId
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    p.guessed -> Mint.copy(alpha = 0.22f)
                    drawing -> Color(p.color).copy(alpha = 0.28f)
                    else -> Color.White.copy(alpha = 0.06f)
                }
            )
            .border(1.dp, if (drawing) Color(p.color) else Color.Transparent, shape)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(p.avatar, fontSize = 20.sp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${p.score}" + if (p.lastGain > 0) "  +${p.lastGain}" else "",
                color = if (p.lastGain > 0) Gold else Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            when {
                drawing -> "✏️"
                p.guessed -> "✅"
                else -> ""
            },
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun FeedRow(f: FeedItem) {
    val pop = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)) }
    Row(
        Modifier
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value }
            .clip(RoundedCornerShape(12.dp))
            .background(if (f.correct) Mint.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(f.avatar, fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Text(f.name, color = Color(f.color), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text(
            f.text, color = if (f.correct) Mint else Color.White, fontSize = 13.sp,
            fontWeight = if (f.correct) FontWeight.ExtraBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Tahtanın üstü: kim çiziyor ve kelimenin harf kalıbı. */
@Composable
private fun TopLine(state: DrawState) {
    val d = state.drawer
    val who = when {
        state.isAi -> "🤖 " + tx("Yapay zekâ çiziyor, herkes tahmin etsin!", "The AI is drawing, everyone guess!")
        d != null -> "${d.avatar} " + tx("${d.name} çiziyor", "${d.name} is drawing")
        else -> ""
    }
    Text(who, color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val w = state.word
    if (w != null && state.phase == DrawPhase.DRAW) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(state.pattern(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                tx("${w.label.count { it != ' ' }} harf", "${w.label.count { it != ' ' }} letters"),
                color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    } else {
        Spacer(Modifier.height(38.dp))
    }
}

@Composable
private fun Choosing(state: DrawState, remaining: Long) {
    val d = state.drawer ?: return
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(0f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "b")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(d.avatar, fontSize = 92.sp, modifier = Modifier.offset(y = (-10 * sin(bob * Math.PI)).dp))
        Text(tx("${d.name} kelimesini seçiyor…", "${d.name} is picking a word…"), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            tx("Telefonuna bak ${d.name}! ${(remaining + 999) / 1000} sn", "Check your phone, ${d.name}! ${(remaining + 999) / 1000}s"),
            color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp,
        )
    }
}

@Composable
private fun AiLoading(state: DrawState) {
    val t by rememberInfiniteTransition(label = "pen").animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "t")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("🤖", fontSize = 80.sp)
            Text("✏️", fontSize = 44.sp, modifier = Modifier.offset(x = (-6 + 12 * t).dp, y = (-8 * t).dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(state.note ?: tx("Yapay zekâ çiziyor…", "The AI is drawing…"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(
            tx("Resim yavaş yavaş netleşecek. Ne kadar erken bilirsen o kadar çok puan!", "The picture will sharpen bit by bit. Guess early for more points!"),
            color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, textAlign = TextAlign.Center,
        )
    }
}

/** Telefondan gelen çizgileri yumuşatarak çizen beyaz kâğıt. */
@Composable
private fun StrokeBoard(board: DrawBoard, modifier: Modifier) {
    val version by board.version.collectAsState()
    val strokes = remember(version) { board.snapshot() }
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Paper)
            .border(3.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (strokes.isEmpty()) {
            Text("✍️", fontSize = 40.sp, modifier = Modifier.graphicsLayer { alpha = 0.25f })
        }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (s in strokes) {
                val p = s.pts
                if (p.size < 2) continue
                val color = Color(s.color)
                val sw = max(1.5f, s.width * w)
                if (p.size <= 4) {
                    drawCircle(color, sw / 2, Offset(p[0] * w, p[1] * h))
                    continue
                }
                val path = Path().apply {
                    moveTo(p[0] * w, p[1] * h)
                    var i = 2
                    while (i + 3 < p.size) {
                        // Ardışık noktaların ortasından ikinci dereceden eğri: titrek çizgiyi yumuşatır
                        val cx = p[i] * w
                        val cy = p[i + 1] * h
                        val nx = (p[i] + p[i + 2]) / 2 * w
                        val ny = (p[i + 1] + p[i + 3]) / 2 * h
                        quadraticTo(cx, cy, nx, ny)
                        i += 2
                    }
                    lineTo(p[p.size - 2] * w, p[p.size - 1] * h)
                }
                drawPath(path, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** Yapay zekânın resmi: iri piksellerden başlar, birkaç saniyede bir netleşir. */
@Composable
private fun AiPicture(bytes: ByteArray?, state: DrawState, remaining: Long) {
    val levels by produceState<List<ImageBitmap>?>(null, bytes) {
        value = if (bytes == null) null else withContext(Dispatchers.Default) { pixelLevels(bytes) }
    }
    val frame = Modifier.fillMaxHeight().aspectRatio(16f / 9f).clip(RoundedCornerShape(22.dp)).background(Paper)
    val list = levels
    if (list.isNullOrEmpty()) {
        Box(frame)
        return
    }
    val elapsed = state.phaseMs - remaining
    val stage = if (state.phase == DrawPhase.REVEAL) list.lastIndex else (elapsed / PIXEL_STEP_MS).toInt().coerceIn(0, list.lastIndex)
    val img = list[stage]
    Canvas(frame) {
        drawImage(
            img,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = if (stage == list.lastIndex) FilterQuality.Medium else FilterQuality.None,
        )
    }
}

private const val PIXEL_STEP_MS = 5_000L

private fun pixelLevels(bytes: ByteArray): List<ImageBitmap> {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 800) sample *= 2
    val full = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return emptyList()
    val out = ArrayList<ImageBitmap>()
    for (n in listOf(6, 9, 13, 18, 25, 34, 46, 64, 90)) {
        val h = max(1, n * full.height / full.width)
        out.add(Bitmap.createScaledBitmap(full, n, h, true).asImageBitmap())
    }
    out.add(full.asImageBitmap())
    return out
}

@Composable
private fun RevealBanner(state: DrawState) {
    val w = state.word
    val pop = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
    Column(
        Modifier
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xEE10142A))
            .border(2.dp, Gold, RoundedCornerShape(24.dp))
            .padding(horizontal = 30.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tx("KELİME", "THE WORD"), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text(w?.label?.uppercase(java.util.Locale.forLanguageTag("tr")) ?: "?", color = Gold, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
        val got = state.guessers.count { it.guessed }
        Text(
            state.note ?: when {
                got == 0 -> tx("Kimse bilemedi 😅", "Nobody got it 😅")
                got == state.guessers.size -> tx("Herkes bildi! 🎉", "Everyone got it! 🎉")
                else -> tx("$got kişi bildi", "$got got it")
            },
            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

// ---------------------------------------------------------------------------
// Son
// ---------------------------------------------------------------------------

@Composable
private fun DrawFinished(game: DrawGame, state: DrawState) {
    val ranked = state.ranked
    val again = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(400)
        runCatching { again.requestFocus() }
    }
    Box(Modifier.fillMaxSize()) {
        Confetti()
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tx("Çiz-Bil bitti!", "That's a wrap!"), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            ranked.firstOrNull()?.let {
                Text(tx("Usta ressam: ${it.avatar} ${it.name}", "Master artist: ${it.avatar} ${it.name}"), color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PodiumColumn(ranked.getOrNull(1)?.asQuiz(), 2, 150.dp, 400)
                PodiumColumn(ranked.getOrNull(0)?.asQuiz(), 1, 210.dp, 900)
                PodiumColumn(ranked.getOrNull(2)?.asQuiz(), 3, 110.dp, 0)
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
