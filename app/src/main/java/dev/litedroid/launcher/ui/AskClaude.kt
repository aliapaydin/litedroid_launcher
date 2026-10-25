package dev.litedroid.launcher.ui

import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import dev.litedroid.launcher.data.VoiceListener
import dev.litedroid.launcher.data.AiProvider
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.LauncherViewModel
import dev.litedroid.launcher.tx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.cos
import kotlin.math.sin

/** Sesli soru başlatır (MainActivity sağlar). Konuşma tanıma yoksa false döner. */
val LocalVoiceInput = staticCompositionLocalOf<() -> Boolean> { { false } }

private val CLAUDE_WARM = Color(0xFFE0835E)
private val CLAUDE_BRUSH = Brush.linearGradient(listOf(Color(0xFFF09A6B), Color(0xFFC8553D)))

/** Sekiz kollu yumuşak ışıltı simgesi. */
@Composable
fun SparkleGlyph(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val c = center
        val r = this.size.minDimension / 2
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0 - 90)
            val len = if (i % 2 == 0) r * 0.95f else r * 0.6f
            drawLine(
                color,
                c + Offset((cos(a) * r * 0.18f).toFloat(), (sin(a) * r * 0.18f).toFloat()),
                c + Offset((cos(a) * len).toFloat(), (sin(a) * len).toFloat()),
                strokeWidth = r * 0.2f,
                cap = StrokeCap.Round,
            )
        }
    }
}

private val EXAMPLES_TR = listOf(
    "Bu akşam hangi maçlar var?",
    "Hafta sonu için bilim kurgu dizisi öner",
    "20 dakikada pişen pratik bir akşam yemeği",
    "Satürn'ün halkaları neden var?",
    "Yarın İzmir'de hava nasıl olacak?",
)
private val EXAMPLES_EN = listOf(
    "Which matches are on tonight?",
    "Recommend a sci-fi series for the weekend",
    "A quick dinner I can cook in 20 minutes",
    "Why does Saturn have rings?",
    "What's the weather tomorrow?",
)

/**
 * Claude'a sor ekranı: sesle ya da yazarak soru, akış halinde gelen büyük yazılı cevap,
 * kaynaklar ve sesli okuma. Geri tuşu kapatır.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskClaudeScreen(
    vm: LauncherViewModel,
    onType: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val voice = LocalVoiceInput.current
    val turns = vm.claudeTurns
    val status = vm.claudeStatus
    val busy = status != LauncherViewModel.ClaudeStatus.IDLE
    val settings = vm.settings
    val micFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    val gemini = settings.aiProvider == AiProvider.GEMINI
    val listening = vm.voiceState != VoiceListener.State.IDLE
    LaunchedEffect(Unit) {
        // Okuma motoru şimdiden hazırlansın: ilk cevap gecikmeden okunur
        vm.warmUpAssistant()
        delay(120)
        runCatching { micFocus.requestFocus() }
    }
    // Cevap geldikçe aşağı kaydır
    val lastLen = turns.lastOrNull()?.let { it.answer.length + it.sources.size + (it.error?.length ?: 0) } ?: 0
    LaunchedEffect(lastLen, turns.size) {
        scroll.animateScrollTo(scroll.maxValue)
    }

    fun askByVoice() {
        if (!voice()) {
            Toast.makeText(context, tx("Bu cihazda sesli giriş bulunamadı, yazarak sorabilirsin", "Voice input isn't available, you can type instead"), Toast.LENGTH_LONG).show()
            onType()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF14110F), Color(0xFF07070B))))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(CLAUDE_WARM.copy(alpha = 0.28f), Color.Transparent), center = Offset(size.width * 0.12f, size.height * 0.08f), radius = size.maxDimension * 0.45f),
                radius = size.maxDimension * 0.45f,
                center = Offset(size.width * 0.12f, size.height * 0.08f),
            )
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF6B5CFF).copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width * 0.9f, size.height * 0.95f), radius = size.maxDimension * 0.4f),
                radius = size.maxDimension * 0.4f,
                center = Offset(size.width * 0.9f, size.height * 0.95f),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp, vertical = 28.dp)
        ) {
            // ---- Başlık ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .softShadow(24.dp, 1.2f)
                        .clip(CircleShape)
                        .background(CLAUDE_BRUSH),
                    contentAlignment = Alignment.Center,
                ) { SparkleGlyph(Color.White, 28.dp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (gemini) tx("Gemini'ye sor", "Ask Gemini") else tx("Claude'a sor", "Ask Claude"),
                        color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp,
                    )
                    Text(
                        listOf(
                            if (gemini) "Gemini " + settings.geminiModel.label else settings.claudeModel.label,
                            if (settings.claudeWebSearch) tx("web araması açık", "web search on") else tx("web araması kapalı", "web search off"),
                            if (settings.claudeSpeak) tx("sesli yanıt açık", "spoken replies on") else tx("sesli yanıt kapalı", "spoken replies off"),
                        ).joinToString("  ·  "),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                    )
                }
                Text(tx("Geri: kapat", "Back: close"), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
            }
            Spacer(Modifier.height(18.dp))

            // ---- Sohbet ----
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionUp -> if (scroll.value > 0) {
                                scope.launch { scroll.animateScrollTo((scroll.value - 260).coerceAtLeast(0)) }
                                true
                            } else false
                            Key.DirectionDown -> if (scroll.value < scroll.maxValue) {
                                scope.launch { scroll.animateScrollTo((scroll.value + 260).coerceAtMost(scroll.maxValue)) }
                                true
                            } else false
                            else -> false
                        }
                    }
                    .focusable()
            ) {
                if (turns.isEmpty()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(tx("Ne merak ediyorsun?", "What are you curious about?"), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            tx(
                                "Mikrofona bas, \"Şimdi konuş\" yazısını görünce sor. Güncel sorularda (maçlar, haberler, hava) web'de aranır. Örnekler:",
                                "Press the mic and ask when you see \"Speak now\". Current topics (matches, news, weather) are searched on the web. Try:",
                            ),
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            (if (tx("tr", "en") == "tr") EXAMPLES_TR else EXAMPLES_EN).forEach { ex ->
                                PillButton(ex, null, primary = false) { vm.askClaude(ex) }
                            }
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        turns.forEachIndexed { i, t ->
                            val latest = i == turns.lastIndex
                            TurnView(t, latest, if (latest) status else LauncherViewModel.ClaudeStatus.IDLE)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // ---- Dinleme paneli: hazır olunca "Şimdi konuş", söylenenler canlı ----
            AnimatedVisibility(
                visible = listening,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ListeningPanel(vm.voiceState, vm.voicePartial, vm.voiceLevel)
            }
            Spacer(Modifier.height(16.dp))
            // ---- Düğmeler ----
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton(
                    when {
                        listening -> tx("Bitir ve sor", "Done, ask")
                        busy -> tx("Yeni soru (sesle)", "New question (voice)")
                        else -> tx("Sesle sor", "Ask by voice")
                    },
                    null, primary = true, focusRequester = micFocus, mic = true,
                ) { askByVoice() }
                if (listening) {
                    PillButton(tx("Vazgeç", "Cancel"), Icons.Filled.Delete, primary = false) { vm.cancelVoice() }
                }
                PillButton(tx("Yazarak sor", "Type a question"), Icons.Filled.Create, primary = false) { onType() }
                if (busy) {
                    PillButton(tx("Durdur", "Stop"), Icons.Filled.Delete, primary = false) { vm.stopClaude() }
                }
                if (turns.any { it.answer.isNotBlank() }) {
                    PillButton(tx("Tekrar oku", "Read again"), Icons.Filled.PlayArrow, primary = false) { vm.speakLastAnswer() }
                }
                if (turns.isNotEmpty()) {
                    PillButton(tx("Yeni sohbet", "New chat"), Icons.Filled.Refresh, primary = false) { vm.clearClaude() }
                }
                if (turns.lastOrNull()?.authProblem == true || (if (gemini) settings.geminiKey else settings.claudeKey).isBlank()) {
                    PillButton(tx("Ayarlar", "Settings"), Icons.Filled.Settings, primary = false) { onOpenSettings() }
                }
            }
        }
    }
}

@Composable
private fun TurnView(t: LauncherViewModel.ClaudeTurn, latest: Boolean, status: LauncherViewModel.ClaudeStatus) {
    Column(Modifier.fillMaxWidth()) {
        // Soru
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Text(
                t.question,
                color = Color.White,
                fontSize = if (latest) 16.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.09f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        // Cevap
        when {
            t.error != null -> Text(
                t.error,
                color = Color(0xFFFFB4A8),
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFF5C6E).copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            t.answer.isBlank() && latest && status != LauncherViewModel.ClaudeStatus.IDLE -> ThinkingDots(status)
            else -> {
                Text(
                    t.answer.replace(Regex("\\*\\*|__|#+\\s"), ""),
                    color = Color.White.copy(alpha = if (latest) 0.96f else 0.75f),
                    fontSize = if (latest) 22.sp else 15.sp,
                    lineHeight = if (latest) 32.sp else 21.sp,
                    fontWeight = if (latest) FontWeight.Medium else FontWeight.Normal,
                )
                if (latest && status == LauncherViewModel.ClaudeStatus.SEARCHING) {
                    Spacer(Modifier.height(8.dp))
                    ThinkingDots(status)
                }
            }
        }
        if (t.sources.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                tx("Kaynaklar: ", "Sources: ") + t.sources.take(4).joinToString("  ·  ") { s ->
                    val host = runCatching { Uri.parse(s.url).host?.removePrefix("www.") }.getOrNull()
                    // Gemini kaynakları yönlendirme adresiyle gelir: başlık (alan adı) daha anlamlı
                    if (host == null || host.contains("vertexaisearch") || host.contains("google.com")) s.title.ifBlank { host ?: "" } else host
                },
                color = Color(0xFFF2B89E).copy(alpha = 0.85f),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ThinkingDots(status: LauncherViewModel.ClaudeStatus) {
    var n by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(380)
            n = (n + 1) % 4
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SparkleGlyph(CLAUDE_WARM, 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            (if (status == LauncherViewModel.ClaudeStatus.SEARCHING) tx("Web'de arıyor", "Searching the web") else tx("Düşünüyor", "Thinking")) + ".".repeat(n),
            color = CLAUDE_WARM,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Kumandayla seçilen yuvarlak düğme. */
@Composable
private fun PillButton(
    label: String,
    icon: ImageVector?,
    primary: Boolean,
    focusRequester: FocusRequester? = null,
    mic: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val sounds = LocalSounds.current
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, spring(dampingRatio = 0.7f, stiffness = 700f), label = "pill")
    val shape = RoundedCornerShape(50)
    val fg = when {
        focused -> Color(0xFF1A1310)
        else -> Color.White
    }
    Row(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .softGlow(focused, CLAUDE_WARM, 26.dp)
            .clip(shape)
            .background(
                when {
                    focused -> Brush.linearGradient(listOf(Color.White, Color(0xFFFFE6DA)))
                    primary -> CLAUDE_BRUSH
                    else -> Brush.linearGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.06f)))
                }
            )
            .border(1.dp, Color.White.copy(alpha = if (focused) 0f else 0.1f), shape)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                if (it.isFocused && !focused) sounds?.play(Sfx.NAV)
                focused = it.isFocused
            }
            .onRemoteSelect({
                sounds?.play(Sfx.SELECT)
                onClick()
            })
            .focusable()
            .padding(horizontal = if (primary) 20.dp else 16.dp, vertical = if (primary) 12.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mic) {
            MicGlyph(fg, 18.dp)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(label, color = fg, fontSize = if (primary) 16.sp else 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Mikrofon simgesi (temel ikon setinde yok). */
@Composable
fun MicGlyph(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.11f
        drawRoundRect(
            color,
            topLeft = Offset(w * 0.33f, h * 0.04f),
            size = androidx.compose.ui.geometry.Size(w * 0.34f, h * 0.56f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.17f),
        )
        drawArc(
            color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.18f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.5f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round),
        )
        drawLine(color, Offset(w / 2, h * 0.72f), Offset(w / 2, h * 0.92f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.34f, h * 0.94f), Offset(w * 0.66f, h * 0.94f), stroke, cap = StrokeCap.Round)
    }
}

/**
 * Dinlerken soru alanının altında: mikrofon hazırlanırken "Hazırlanıyor…", hazır olunca "Şimdi konuş",
 * ses seviyesine göre hareket eden çubuklar ve söylenenin canlı yazısı.
 */
@Composable
private fun ListeningPanel(state: VoiceListener.State, partial: String, level: Float) {
    val ready = state == VoiceListener.State.LISTENING
    val shown by animateFloatAsState(level, spring(dampingRatio = 0.6f, stiffness = 900f), label = "lvl")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, (if (ready) CLAUDE_WARM else Color.White).copy(alpha = if (ready) 0.7f else 0.12f), RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .graphicsLayer { val s = 1f + (if (ready) shown * 0.25f else 0f); scaleX = s; scaleY = s }
                .clip(CircleShape)
                .background(if (ready) CLAUDE_BRUSH else Brush.linearGradient(listOf(Color(0xFF3A3A44), Color(0xFF2A2A33)))),
            contentAlignment = Alignment.Center,
        ) { MicGlyph(Color.White, 22.dp) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when (state) {
                    VoiceListener.State.PREPARING -> tx("Mikrofon hazırlanıyor…", "Getting the mic ready…")
                    VoiceListener.State.LISTENING -> tx("Şimdi konuş", "Speak now")
                    else -> tx("Anlaşılıyor…", "Understanding…")
                },
                color = if (ready) CLAUDE_WARM else Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                partial.ifBlank { if (ready) tx("Dinliyorum…", "Listening…") else " " },
                color = Color.White.copy(alpha = if (partial.isBlank()) 0.45f else 0.95f),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Ses seviyesi çubukları
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(0.55f, 0.85f, 1f, 0.75f, 0.5f).forEach { f ->
                Box(
                    Modifier
                        .width(5.dp)
                        .height((8f + 30f * (if (ready) shown * f else 0.1f)).dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (ready) CLAUDE_WARM else Color.White.copy(alpha = 0.3f))
                )
            }
        }
    }
}
