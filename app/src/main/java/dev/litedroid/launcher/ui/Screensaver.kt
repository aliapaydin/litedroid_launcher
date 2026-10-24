package dev.litedroid.launcher.ui

import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import dev.litedroid.launcher.data.MatchState
import dev.litedroid.launcher.data.ScoresHub
import dev.litedroid.launcher.media.LiveMediaHub
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import dev.litedroid.launcher.ui.theme.Jakarta
import dev.litedroid.launcher.I18n
import dev.litedroid.launcher.tx
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.R
import dev.litedroid.launcher.data.SaverScene
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

private const val TWO_PI = (2 * PI).toFloat()

private data class SceneInfo(val title: String, val tint: Color)

private fun infoFor(scene: SaverScene) = when (scene) {
    SaverScene.CINEMA -> SceneInfo(tx("Sinema gecesi", "Movie night"), Color(0xFFFF6A5A))
    SaverScene.SPORT -> SceneInfo(tx("Maç günü", "Match day"), Color(0xFF3DDC84))
    SaverScene.GAMES -> SceneInfo(tx("Oyun zamanı", "Game time"), Color(0xFFFF4DD8))
    else -> SceneInfo(tx("Sakin gece", "Quiet night"), Color(0xFF5FD8FF))
}

/**
 * Ekran koruyucu. Uygulama içinde (boşta kalınca) ve sistem ekran koruyucusu olarak
 * aynı içerik kullanılır. "Karışık" seçiliyse sahneler 40 saniyede bir değişir.
 * Ekran yanmasını önlemek için saat bloğu yavaşça yer değiştirir.
 */
@Composable
fun ScreensaverContent(
    scene: SaverScene,
    userName: String?,
    modifier: Modifier = Modifier,
    showNowPlaying: Boolean = true,
    showScores: Boolean = true,
    leagues: Set<String>? = null,
) {
    var current by remember { mutableStateOf(if (scene == SaverScene.MIXED) SaverScene.CINEMA else scene) }
    LaunchedEffect(scene) {
        if (scene == SaverScene.MIXED) {
            val order = listOf(SaverScene.CINEMA, SaverScene.SPORT, SaverScene.GAMES, SaverScene.NIGHT)
            var i = 0
            while (true) {
                current = order[i % order.size]
                i++
                delay(40_000)
            }
        } else {
            current = scene
        }
    }

    // 0..1 arası, 120 sn'de bir dönen zaman. Sahnelerdeki tüm periyotlar bunun tam katı,
    // böylece başa sarışta sıçrama olmaz.
    val time = rememberInfiniteTransition(label = "saver").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(120_000, easing = LinearEasing)),
        label = "t",
    )

    // Karışık modda sahne değişimine kalan süre (saat kartındaki ince çubuk için)
    val sceneProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(current, scene) {
        if (scene == SaverScene.MIXED) {
            sceneProgress.snapTo(0f)
            sceneProgress.animateTo(1f, tween(40_000, easing = LinearEasing))
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Ek ekran tamponu açmayan geçiş: sahneler 2,5 sn'de birbirine karışır
        FastCrossfade(targetState = current, durationMillis = 2_500, modifier = Modifier.fillMaxSize()) { s ->
            Canvas(Modifier.fillMaxSize()) {
                val t = time.value
                when (s) {
                    SaverScene.CINEMA -> drawCinema(t)
                    SaverScene.SPORT -> drawSport(t)
                    SaverScene.GAMES -> drawGames(t)
                    else -> drawNight(t)
                }
            }
        }
        // Ortak sinematik katman: üst/alt yumuşak kararma, havada süzülen ışık tozları, köşe kararması
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            val w = size.width
            val h = size.height
            drawRect(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), endY = h * 0.22f),
                size = Size(w, h * 0.22f),
            )
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)), startY = h * 0.6f, endY = h),
                topLeft = Offset(0f, h * 0.6f),
                size = Size(w, h * 0.4f),
            )
            val tint = infoFor(current).tint
            for (i in 0 until 36) {
                val speed = 1 + (i % 3)
                val px = (hash(i * 7 + 1) + t * speed * 0.5f + sin(TWO_PI * (t * speed * 2 + hash(i))) * 0.02f + 1f) % 1f
                val py = (hash(i * 13 + 5) - t * speed + 2f) % 1f
                val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(TWO_PI * (t * 12 * speed + hash(i * 3))))
                val r = (1.2f + hash(i * 5) * 2.4f) * density
                drawCircle(
                    lerp(Color.White, tint, 0.4f).copy(alpha = 0.22f * twinkle),
                    radius = r * 2.6f,
                    center = Offset(px * w, py * h),
                )
                drawCircle(Color.White.copy(alpha = 0.55f * twinkle), radius = r * 0.7f, center = Offset(px * w, py * h))
            }
            vignette(0.55f)
        }
        SaverClock(current, userName, time, if (scene == SaverScene.MIXED) sceneProgress.asState() else null)
        SaverWidgets(time, showNowPlaying, showScores, leagues)
    }
}

/**
 * Sağ üstte "Şimdi çalıyor" ve canlı skor kartları, en üstte gol bildirimi.
 * Kartlar saat kartının tersi yönde çok yavaş gezinir (ekran yanmasına karşı).
 */
@Composable
private fun SaverWidgets(time: State<Float>, showNowPlaying: Boolean, showScores: Boolean, leagues: Set<String>?) {
    val context = LocalContext.current
    val media by LiveMediaHub.current.collectAsState()
    val matches by ScoresHub.matches.collectAsState()
    if (showScores) {
        DisposableEffect(Unit) {
            leagues?.let { ScoresHub.leagues = it }
            ScoresHub.acquire(context, "saver")
            onDispose { ScoresHub.release("saver") }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 48.dp)
                .graphicsLayer {
                    val t = time.value
                    translationX = -sin(TWO_PI * t * 2) * 14.dp.toPx()
                    translationY = abs(cos(TWO_PI * t * 2)) * 12.dp.toPx()
                },
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val m = media
            if (showNowPlaying && m != null) NowPlayingCard(m)
            val visible = matches.filter { it.state != MatchState.DONE || System.currentTimeMillis() - it.start.toEpochMilli() < 4 * 3_600_000 }
            if (showScores && visible.isNotEmpty()) ScoresCard(visible, max = if (showNowPlaying && m != null) 4 else 6)
        }
        if (showScores) {
            GoalBanner(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
            )
        }
    }
}

/**
 * Saat kartı: koyu cam panel üzerinde ince, sabit genişlikli rakamlı büyük saat, büyük harfli tarih,
 * sahne etiketi ve selamlama. Ekran yanmasını önlemek için kart çok yavaş gezinir.
 */
@Composable
private fun SaverClock(scene: SaverScene, userName: String?, time: State<Float>, sceneProgress: State<Float>?) {
    val now = rememberNow()
    val info = infoFor(scene)
    val shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 16f)
    val cardShape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxSize()
            .padding(start = 56.dp, bottom = 48.dp)
    ) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .graphicsLayer {
                    val t = time.value
                    translationX = sin(TWO_PI * t * 2) * 26.dp.toPx()
                    translationY = -abs(cos(TWO_PI * t * 2)) * 18.dp.toPx()
                }
                .clip(cardShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0E0F1C).copy(alpha = 0.62f), Color(0xFF07070E).copy(alpha = 0.42f))
                    )
                )
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.03f))), cardShape)
                .glassSheen(0.8f)
                .padding(start = 26.dp, end = 30.dp, top = 18.dp, bottom = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo_mark), contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Wordmark(14.sp)
                Spacer(Modifier.width(12.dp))
                // Sahne etiketi: renkli nokta + ad
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(info.tint.copy(alpha = 0.22f))
                        .border(1.dp, info.tint.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(info.tint)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        info.title.uppercase(I18n.locale),
                        style = TextStyle(fontFamily = Jakarta, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                now.format(DateTimeFormatter.ofPattern("HH:mm", I18n.locale)),
                style = TextStyle(
                    fontFamily = Jakarta,
                    color = Color.White,
                    fontSize = 96.sp,
                    lineHeight = 100.sp,
                    fontWeight = FontWeight.ExtraLight,
                    letterSpacing = (-3).sp,
                    fontFeatureSettings = "tnum",
                    shadow = shadow,
                ),
            )
            Text(
                now.format(DateTimeFormatter.ofPattern(tx("d MMMM EEEE", "EEEE, MMMM d"), I18n.locale)).uppercase(I18n.locale),
                style = TextStyle(
                    fontFamily = Jakarta,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    shadow = shadow,
                ),
            )
            if (!userName.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${greetingFor(now.hour)}, $userName",
                    style = TextStyle(fontFamily = Jakarta, color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp, fontWeight = FontWeight.Medium, shadow = shadow),
                )
            }
            if (sceneProgress != null) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .width(220.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.12f))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(sceneProgress.value.coerceIn(0f, 1f))
                            .background(Brush.horizontalGradient(listOf(info.tint.copy(alpha = 0.6f), info.tint)))
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// Yardımcılar
// ------------------------------------------------------------------

/** 0..1 arası sözde rastgele sayı (her karede aynı sonucu verir). */
private fun hash(n: Int): Float {
    val x = sin(n * 12.9898f + 78.233f) * 43758.547f
    return x - kotlin.math.floor(x)
}

/** Renk listesinde yumuşak geçişle dolaşır (p: 0..1 bir tur). */
private fun cycleColor(colors: List<Color>, p: Float): Color {
    val x = (((p % 1f) + 1f) % 1f) * colors.size
    val i = x.toInt().coerceIn(0, colors.lastIndex)
    val f = x - i
    val s = f * f * (3 - 2 * f)
    return lerp(colors[i], colors[(i + 1) % colors.size], s)
}

private fun DrawScope.vignette(strength: Float) {
    drawRect(
        Brush.radialGradient(
            0f to Color.Transparent,
            0.6f to Color.Transparent,
            1f to Color.Black.copy(alpha = strength),
            center = center,
            radius = size.maxDimension * 0.72f,
        )
    )
}

private fun DrawScope.glowDot(c: Offset, r: Float, color: Color) {
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = c, radius = r), radius = r, center = c)
}

/** Sivri tepeli sıradağ silueti. */
private fun ridge(w: Float, h: Float, baseY: Float, amp: Float, seed: Int, steps: Int): Path = Path().apply {
    moveTo(0f, h)
    for (i in 0..steps) {
        val x = w * i / steps
        val peak = 0.35f + 0.65f * hash(seed + i)
        val y = baseY - amp * peak * (if (i % 2 == 0) 1f else 0.55f)
        lineTo(x, y)
    }
    lineTo(w, h)
    close()
}

private fun ridgeTop(w: Float, baseY: Float, amp: Float, seed: Int, steps: Int): Path = Path().apply {
    for (i in 0..steps) {
        val x = w * i / steps
        val peak = 0.35f + 0.65f * hash(seed + i)
        val y = baseY - amp * peak * (if (i % 2 == 0) 1f else 0.55f)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
}

// ------------------------------------------------------------------
// 1) Sinema gecesi: salonun içinden perde, projektör ışını, havada toz, koltuk sıraları
// ------------------------------------------------------------------

private val FILM = listOf(
    Color(0xFFFF8A3D), Color(0xFFFF3D7F), Color(0xFF7B5CFF), Color(0xFF2EC5FF), Color(0xFF3DFFB0),
)

private fun DrawScope.drawCinema(t: Float) {
    val w = size.width
    val h = size.height
    val sl = w * 0.17f
    val st = h * 0.07f
    val sw = w * 0.66f
    val sh = h * 0.42f
    val glow = cycleColor(FILM, t * 3f)
    val glow2 = cycleColor(FILM, t * 3f + 0.45f)

    // Salon ve perdenin odaya yansıyan ışığı
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0C0712), Color(0xFF050308))))
    glowDot(Offset(w / 2, st + sh * 0.7f), w * 0.8f, glow.copy(alpha = 0.3f))

    // Yan duvarlar (perspektif) ve aplikler
    for (side in 0..1) {
        val wall = Path().apply {
            if (side == 0) {
                moveTo(0f, 0f); lineTo(sl * 0.82f, st * 0.5f); lineTo(sl * 0.82f, st + sh + h * 0.12f); lineTo(0f, h)
            } else {
                moveTo(w, 0f); lineTo(w - sl * 0.82f, st * 0.5f); lineTo(w - sl * 0.82f, st + sh + h * 0.12f); lineTo(w, h)
            }
            close()
        }
        drawPath(wall, Brush.horizontalGradient(
            if (side == 0) listOf(Color(0xFF07040B), lerp(Color(0xFF1A0E22), glow, 0.12f))
            else listOf(lerp(Color(0xFF1A0E22), glow, 0.12f), Color(0xFF07040B)),
            startX = if (side == 0) 0f else w - sl * 0.82f,
            endX = if (side == 0) sl * 0.82f else w,
        ))
        for (k in 0..1) {
            val x = if (side == 0) sl * (0.25f + k * 0.35f) else w - sl * (0.25f + k * 0.35f)
            val y = h * (0.32f + k * 0.04f)
            glowDot(Offset(x, y), w * 0.05f, Color(0xFFFFB45A).copy(alpha = 0.55f))
            drawCircle(Color(0xFFFFE1B0), radius = 3f, center = Offset(x, y))
        }
    }

    // Perde parlaması (bloom)
    for (i in 6 downTo 1) {
        drawRoundRect(
            glow.copy(alpha = 0.035f * (7 - i) / 6f + 0.01f),
            topLeft = Offset(sl - i * 8f, st - i * 8f),
            size = Size(sw + i * 16f, sh + i * 16f),
            cornerRadius = CornerRadius(10f + i * 8f),
        )
    }

    // Projektör ışını (salonun arkasından perdeye)
    val flicker = 0.88f + 0.12f * sin(TWO_PI * t * 240)
    val src = Offset(w * 0.5f, h * 1.02f)
    val beam = Path().apply {
        moveTo(src.x - w * 0.012f, src.y)
        lineTo(sl, st + sh); lineTo(sl, st); lineTo(sl + sw, st); lineTo(sl + sw, st + sh)
        lineTo(src.x + w * 0.012f, src.y)
        close()
    }
    drawPath(beam, Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.13f * flicker), glow.copy(alpha = 0.05f * flicker)),
        start = src,
        end = Offset(w / 2, st + sh / 2),
    ))

    // Perdedeki "film": gökyüzü, ilerleyen güneş, paralaks tepeler
    clipRect(sl, st, sl + sw, st + sh) {
        drawRect(
            Brush.verticalGradient(listOf(lerp(glow2, Color(0xFF0A0420), 0.55f), lerp(glow, Color.White, 0.15f)), startY = st, endY = st + sh),
            topLeft = Offset(sl, st), size = Size(sw, sh),
        )
        val sunX = sl + sw * (0.15f + 0.7f * ((t * 4f) % 1f))
        val sunY = st + sh * (0.55f - 0.25f * sin(PI.toFloat() * ((t * 4f) % 1f)))
        glowDot(Offset(sunX, sunY), sh * 0.6f, Color(0xFFFFF1C2).copy(alpha = 0.55f))
        drawCircle(Color(0xFFFFF4D6), radius = sh * 0.09f, center = Offset(sunX, sunY))
        for (layer in 0..2) {
            val baseY = st + sh * (0.62f + layer * 0.12f)
            val col = lerp(lerp(glow2, Color(0xFF0B0418), 0.45f + layer * 0.2f), Color.Black, layer * 0.15f)
            val hills = Path().apply {
                moveTo(sl, st + sh)
                val steps = 48
                for (i in 0..steps) {
                    val xn = i / steps.toFloat()
                    val y = baseY +
                        sin(TWO_PI * (xn * (1.5f + layer) + t * (2 + layer * 2))) * sh * 0.05f +
                        sin(TWO_PI * (xn * (3.7f + layer) - t * (3 + layer))) * sh * 0.025f
                    lineTo(sl + sw * xn, y)
                }
                lineTo(sl + sw, st + sh)
                close()
            }
            drawPath(hills, col)
        }
        // Perde kenar kararması
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f),
                center = Offset(sl + sw / 2, st + sh / 2), radius = sw * 0.62f,
            ),
            topLeft = Offset(sl, st), size = Size(sw, sh),
        )
    }
    drawRoundRect(Color.Black.copy(alpha = 0.8f), topLeft = Offset(sl, st), size = Size(sw, sh), cornerRadius = CornerRadius(6f), style = Stroke(4f))

    // Işının içinde süzülen toz
    for (i in 0 until 80) {
        val u = (hash(i) + t * (1f + hash(i + 300) * 2f)) % 1f
        val v = hash(i + 100)
        val target = Offset(sl + v * sw, st + hash(i + 200) * sh)
        val p = Offset(src.x + (target.x - src.x) * u, src.y + (target.y - src.y) * u)
        val twinkle = 0.5f + 0.5f * sin(TWO_PI * (t * 30 + hash(i + 400)))
        drawCircle(Color(0xFFFFF3DD).copy(alpha = 0.55f * sin(PI.toFloat() * u) * twinkle), radius = 1.2f + hash(i + 500) * 1.8f, center = p)
    }

    // Koltuk sıraları ve izleyiciler (perde ışığıyla kenarları aydınlanır)
    for (row in 0..3) {
        val y = h * (0.69f + row * 0.085f)
        val seatW = w * (0.064f + row * 0.013f)
        val offset = if (row % 2 == 0) 0f else seatW * 0.5f
        val body = lerp(Color(0xFF1E1024), Color(0xFF060308), row / 3f)
        var x = -seatW + offset
        var n = 0
        while (x < w + seatW) {
            drawRoundRect(body, topLeft = Offset(x, y), size = Size(seatW * 0.92f, h - y + 20f), cornerRadius = CornerRadius(seatW * 0.28f))
            drawRoundRect(
                glow.copy(alpha = 0.32f - row * 0.06f),
                topLeft = Offset(x + 3f, y), size = Size(seatW * 0.92f - 6f, 3f), cornerRadius = CornerRadius(2f),
            )
            if (hash(row * 97 + n) < 0.45f) {
                val head = Offset(x + seatW * 0.46f, y - seatW * 0.1f)
                val r = seatW * 0.21f
                drawCircle(Color(0xFF040205), radius = r, center = head)
                drawArc(
                    glow.copy(alpha = 0.45f - row * 0.08f),
                    startAngle = 200f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(head.x - r, head.y - r), size = Size(r * 2, r * 2),
                    style = Stroke(2f, cap = StrokeCap.Round),
                )
            }
            x += seatW
            n++
        }
    }
    // Koridor ışıkları
    for (k in 0 until 6) {
        glowDot(Offset(w * (0.05f + k * 0.18f), h * 0.985f), 14f, Color(0xFFFFB45A).copy(alpha = 0.7f))
    }
    vignette(0.8f)
}

// ------------------------------------------------------------------
// 2) Maç günü: gece stadyumu, tribünde flaşlar, projektörler, çizgili çim, iz bırakan top
// ------------------------------------------------------------------

private fun DrawScope.drawSport(t: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(0f to Color(0xFF02060F), 0.45f to Color(0xFF0A1C38), 1f to Color(0xFF0C1A2C)))
    for (i in 0 until 60) {
        val tw = 0.4f + 0.6f * abs(sin(TWO_PI * (t * 6 + hash(i))))
        drawCircle(Color.White.copy(alpha = 0.5f * tw), radius = 0.8f + hash(i + 7) * 1.2f, center = Offset(hash(i + 11) * w, hash(i + 13) * h * 0.22f))
    }

    // Tribün kasesi
    val bowlCenter = Offset(w / 2, h * 0.72f)
    val rx = w * 0.62f
    val ry = h * 0.5f
    drawOval(
        Brush.verticalGradient(listOf(Color(0xFF151D33), Color(0xFF0A0F1C)), startY = bowlCenter.y - ry, endY = bowlCenter.y),
        topLeft = Offset(bowlCenter.x - rx, bowlCenter.y - ry), size = Size(rx * 2, ry * 2),
    )
    // Seyirciler: renkli ışık noktaları ve flaşlar
    val teamA = listOf(Color(0xFFFF4D4D), Color(0xFFFFFFFF), Color(0xFFFFD34D))
    val teamB = listOf(Color(0xFF4DA6FF), Color(0xFFFFFFFF), Color(0xFF3DDC84))
    for (i in 0 until 260) {
        val a = PI.toFloat() * (0.92f + 1.16f * hash(i))
        val r = 0.64f + 0.33f * hash(i + 1000)
        val p = Offset(bowlCenter.x + cos(a) * rx * r, bowlCenter.y + sin(a) * ry * r)
        if (p.y > h * 0.62f) continue
        val palette = if (p.x < w / 2) teamA else teamB
        val c = palette[(hash(i + 2000) * palette.size).toInt().coerceIn(0, palette.lastIndex)]
        val tw = 0.35f + 0.65f * abs(sin(TWO_PI * (t * 10 + hash(i + 3000))))
        drawCircle(c.copy(alpha = 0.55f * tw), radius = 1.3f + hash(i + 4000) * 1.4f, center = p)
        if (hash(i + 5000) > 0.93f) {
            val flash = sin(TWO_PI * (t * 24 + hash(i + 6000))).coerceAtLeast(0f).pow(40)
            if (flash > 0.02f) glowDot(p, 14f, Color.White.copy(alpha = flash))
        }
    }

    // Saha (perspektif) ve çim şeritleri
    val top = h * 0.47f
    val bottom = h * 1.04f
    val topHalf = w * 0.24f
    val bottomHalf = w * 0.6f
    fun half(y: Float) = topHalf + (bottomHalf - topHalf) * ((y - top) / (bottom - top))
    fun depth(f: Float) = top + (bottom - top) * f.pow(1.6f)
    val bands = 12
    for (b in 0 until bands) {
        val y0 = depth(b / bands.toFloat())
        val y1 = depth((b + 1) / bands.toFloat())
        val band = Path().apply {
            moveTo(w / 2 - half(y0), y0); lineTo(w / 2 + half(y0), y0)
            lineTo(w / 2 + half(y1), y1); lineTo(w / 2 - half(y1), y1); close()
        }
        drawPath(band, if (b % 2 == 0) Color(0xFF137A37) else Color(0xFF189446))
    }
    glowDot(Offset(w / 2, h * 0.72f), w * 0.45f, Color.White.copy(alpha = 0.1f))

    val line = Color.White.copy(alpha = 0.75f)
    val outline = Path().apply {
        moveTo(w / 2 - half(top), top); lineTo(w / 2 + half(top), top)
        lineTo(w / 2 + half(bottom), bottom); lineTo(w / 2 - half(bottom), bottom); close()
    }
    drawPath(outline, line, style = Stroke(2.5f))
    val midY = depth(0.5f)
    drawLine(line, Offset(w / 2 - half(midY), midY), Offset(w / 2 + half(midY), midY), strokeWidth = 2.5f)
    drawOval(line, topLeft = Offset(w / 2 - half(midY) * 0.22f, midY - h * 0.05f), size = Size(half(midY) * 0.44f, h * 0.1f), style = Stroke(2.5f))
    drawCircle(line, radius = 4f, center = Offset(w / 2, midY))
    val boxTop = depth(0.82f)
    val box = Path().apply {
        moveTo(w / 2 - half(boxTop) * 0.45f, bottom); lineTo(w / 2 - half(boxTop) * 0.45f, boxTop)
        lineTo(w / 2 + half(boxTop) * 0.45f, boxTop); lineTo(w / 2 + half(boxTop) * 0.45f, bottom)
    }
    drawPath(box, line, style = Stroke(2.5f))
    val farBox = depth(0.12f)
    val fbox = Path().apply {
        moveTo(w / 2 - half(top) * 0.45f, top); lineTo(w / 2 - half(farBox) * 0.45f, farBox)
        lineTo(w / 2 + half(farBox) * 0.45f, farBox); lineTo(w / 2 + half(top) * 0.45f, top)
    }
    drawPath(fbox, line, style = Stroke(2f))

    // Projektör kuleleri: ışık, parlama çizgisi ve sahaya inen hüzme
    val towers = listOf(Offset(w * 0.06f, h * 0.1f), Offset(w * 0.3f, h * 0.05f), Offset(w * 0.7f, h * 0.05f), Offset(w * 0.94f, h * 0.1f))
    towers.forEachIndexed { k, p ->
        val pulse = 0.85f + 0.15f * sin(TWO_PI * (t * 8 + k * 0.25f))
        val beamPath = Path().apply {
            moveTo(p.x - 18f, p.y); lineTo(p.x + 18f, p.y)
            lineTo(w / 2 + (p.x - w / 2) * 0.3f + w * 0.12f, h * 0.8f)
            lineTo(w / 2 + (p.x - w / 2) * 0.3f - w * 0.12f, h * 0.8f); close()
        }
        drawPath(beamPath, Brush.verticalGradient(listOf(Color(0xFFE8F4FF).copy(alpha = 0.07f * pulse), Color.Transparent), startY = p.y, endY = h * 0.8f))
        glowDot(p, w * 0.14f, Color(0xFFDDEEFF).copy(alpha = 0.55f * pulse))
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f * pulse), Color.Transparent), startX = p.x - w * 0.14f, endX = p.x + w * 0.14f),
            Offset(p.x - w * 0.14f, p.y), Offset(p.x + w * 0.14f, p.y), strokeWidth = 2f,
        )
        drawRoundRect(Color(0xFFF7FBFF), topLeft = Offset(p.x - 20f, p.y - 7f), size = Size(40f, 14f), cornerRadius = CornerRadius(3f))
        for (gx in 0..3) for (gy in 0..1) {
            drawCircle(Color(0xFFBFD9FF), radius = 2f, center = Offset(p.x - 13f + gx * 8.7f, p.y - 3f + gy * 6f))
        }
    }

    // Top: sahada sekip dolaşır, arkasında ışıklı iz bırakır
    fun ballAt(p: Float): Pair<Offset, Float> {
        val gy = top + (bottom - top) * (0.45f + 0.2f * sin(TWO_PI * p * 2))
        val gx = w / 2 + sin(TWO_PI * p) * half(gy) * 0.6f
        val lift = abs(sin(TWO_PI * p * 4)) * h * 0.07f
        return Offset(gx, gy) to lift
    }
    val bp = (t * 8f) % 1f
    for (k in 10 downTo 1) {
        val (g, lift) = ballAt(bp - k * 0.004f)
        drawCircle(Color.White.copy(alpha = 0.3f * (1f - k / 11f)), radius = 7f * (1f - k / 14f), center = Offset(g.x, g.y - lift))
    }
    val (ground, lift) = ballAt(bp)
    drawOval(Color.Black.copy(alpha = 0.35f), topLeft = Offset(ground.x - 16f, ground.y - 4f), size = Size(32f, 8f))
    val ball = Offset(ground.x, ground.y - lift)
    glowDot(ball, 30f, Color.White.copy(alpha = 0.35f))
    drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFD7DDE6)), center = ball - Offset(3f, 3f), radius = 12f), radius = 11f, center = ball)
    drawCircle(Color(0xFF20242C), radius = 3.5f, center = ball + Offset(2f, 1f))

    // Saha üstü hafif sis
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.05f), Color.Transparent), startY = top - h * 0.08f, endY = top + h * 0.1f))
    vignette(0.65f)
}

// ------------------------------------------------------------------
// 3) Oyun zamanı: synthwave gün batımı, çizgili güneş, neon dağlar, akan ızgara
// ------------------------------------------------------------------

private fun DrawScope.drawGames(t: Float) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.6f
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF07001A),
            0.32f to Color(0xFF1E0640),
            0.48f to Color(0xFF5A1470),
            0.6f to Color(0xFFFF4F8B),
            startY = 0f,
            endY = horizon,
        ),
        size = Size(w, horizon),
    )
    for (i in 0 until 90) {
        val tw = 0.3f + 0.7f * abs(sin(TWO_PI * (t * 8 + hash(i + 50))))
        drawCircle(Color.White.copy(alpha = 0.7f * tw * (1f - hash(i + 60) * 0.5f)), radius = 0.7f + hash(i + 70) * 1.3f, center = Offset(hash(i + 80) * w, hash(i + 90) * horizon * 0.55f))
    }
    // Kayan yıldız (20 sn'de bir)
    val shoot = (t * 6f) % 1f
    if (shoot < 0.06f) {
        val q = shoot / 0.06f
        val start = Offset(w * (0.2f + 0.5f * hash((t * 6f).toInt())), h * 0.08f)
        val head = start + Offset(w * 0.25f * q, h * 0.12f * q)
        drawLine(
            Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 1f - q)), start = head - Offset(w * 0.08f, h * 0.04f), end = head),
            head - Offset(w * 0.08f, h * 0.04f), head, strokeWidth = 2f, cap = StrokeCap.Round,
        )
    }

    // Güneş: hale + degrade disk + aşağı akan şeritler
    val sunC = Offset(w / 2, horizon - h * 0.05f)
    val sunR = h * 0.22f
    glowDot(sunC, sunR * 2.4f, Color(0xFFFF3D8B).copy(alpha = 0.5f))
    clipPath(Path().apply { addOval(Rect(sunC, sunR)) }) {
        drawCircle(
            Brush.verticalGradient(listOf(Color(0xFFFFF27A), Color(0xFFFFA23D), Color(0xFFFF3D8B)), startY = sunC.y - sunR, endY = sunC.y + sunR),
            radius = sunR, center = sunC,
        )
        val flow = (t * 24f) % 1f
        for (k in 0 until 9) {
            val y = sunC.y + sunR * (0.02f + (k + flow) * 0.11f)
            drawRect(Color(0xFF4A0E62), topLeft = Offset(sunC.x - sunR, y), size = Size(sunR * 2, 1.5f + (k + flow) * 1.8f))
        }
    }

    // Dağlar: uzak (mor, pembe kenar) ve yakın (koyu, camgöbeği neon kenar)
    drawPath(ridge(w, horizon, horizon - h * 0.02f, h * 0.17f, 11, 20), Color(0xFF2A0B52))
    drawPath(ridgeTop(w, horizon - h * 0.02f, h * 0.17f, 11, 20), Color(0xFFFF4FD8).copy(alpha = 0.55f), style = Stroke(2f))
    drawPath(ridge(w, horizon, horizon + h * 0.005f, h * 0.1f, 37, 14), Color(0xFF12042A))
    val nearTop = ridgeTop(w, horizon + h * 0.005f, h * 0.1f, 37, 14)
    drawPath(nearTop, Color(0xFF3DF0FF).copy(alpha = 0.18f), style = Stroke(7f))
    drawPath(nearTop, Color(0xFF3DF0FF).copy(alpha = 0.85f), style = Stroke(1.6f))

    // Zemin ve akan neon ızgara
    drawRect(Brush.verticalGradient(listOf(Color(0xFF14032E), Color(0xFF05000F)), startY = horizon, endY = h), topLeft = Offset(0f, horizon), size = Size(w, h - horizon))
    val vp = Offset(w / 2, horizon)
    val magenta = Color(0xFFFF3DD8)
    for (i in -22..22) {
        val end = Offset(w / 2 + i * w * 0.075f, h)
        val brush = Brush.linearGradient(listOf(magenta.copy(alpha = 0f), magenta.copy(alpha = 0.9f)), start = vp, end = end)
        drawLine(brush, vp, end, strokeWidth = 5f, alpha = 0.18f)
        drawLine(brush, vp, end, strokeWidth = 1.5f)
    }
    val flowLines = (t * 48f) % 1f
    for (k in 0 until 16) {
        val z = (k + flowLines) / 16f
        val y = horizon + (h - horizon) * z.pow(2.2f)
        val c = Color(0xFF3DE5FF)
        drawLine(c.copy(alpha = 0.15f * z), Offset(0f, y), Offset(w, y), strokeWidth = 6f * z + 1f)
        drawLine(c.copy(alpha = 0.25f + 0.65f * z), Offset(0f, y), Offset(w, y), strokeWidth = 1f + 1.5f * z)
    }
    // Ufuk parlaması
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFFF4F8B).copy(alpha = 0.55f), Color.Transparent), startY = horizon - 18f, endY = horizon + 18f), topLeft = Offset(0f, horizon - 18f), size = Size(w, 36f))

    // Yükselen piksel parçacıklar
    for (i in 0 until 22) {
        val rise = (hash(i + 700) + t * (3f + hash(i + 710) * 3f)) % 1f
        val x = hash(i + 720) * w
        val y = horizon - rise * horizon * 0.9f
        val c = listOf(Color(0xFF3DE5FF), Color(0xFFFF4DD8), Color(0xFFFFE066))[(hash(i + 730) * 3).toInt().coerceIn(0, 2)]
        val s = 3f + hash(i + 740) * 5f
        val a = sin(PI.toFloat() * rise)
        glowDot(Offset(x + s / 2, y + s / 2), s * 3f, c.copy(alpha = 0.35f * a))
        drawRect(c.copy(alpha = 0.85f * a), topLeft = Offset(x, y), size = Size(s, s))
    }
    vignette(0.7f)
}

// ------------------------------------------------------------------
// 4) Sakin gece: dalgalanan kutup ışığı perdeleri, yıldızlar, dağlar ve gölde yansıma
// ------------------------------------------------------------------

private val AURORA = listOf(Color(0xFF3DFFB0), Color(0xFF3DD8FF), Color(0xFFB06BFF))

private fun DrawScope.drawNight(t: Float) {
    val w = size.width
    val h = size.height
    val lakeTop = h * 0.8f
    drawRect(Brush.verticalGradient(0f to Color(0xFF010309), 0.55f to Color(0xFF04142A), 1f to Color(0xFF0A2A3C), startY = 0f, endY = lakeTop), size = Size(w, lakeTop))

    for (i in 0 until 150) {
        val tw = 0.3f + 0.7f * abs(sin(TWO_PI * (t * 5 + hash(i + 900))))
        val p = Offset(hash(i + 910) * w, hash(i + 920) * h * 0.6f)
        val r = 0.6f + hash(i + 930) * 1.4f
        drawCircle(Color.White.copy(alpha = 0.75f * tw), radius = r, center = p)
        if (hash(i + 940) > 0.96f) {
            drawLine(Color.White.copy(alpha = 0.35f * tw), p - Offset(6f, 0f), p + Offset(6f, 0f), strokeWidth = 1f)
            drawLine(Color.White.copy(alpha = 0.35f * tw), p - Offset(0f, 6f), p + Offset(0f, 6f), strokeWidth = 1f)
        }
    }

    glowDot(Offset(w * 0.5f, h * 0.38f), w * 0.55f, Color(0xFF3DFFB0).copy(alpha = 0.1f))

    // Kutup ışığı: dikey ışın perdeleri
    data class Ray(val x: Float, val base: Float, val top: Float, val a: Float, val color: Color)
    val rays = ArrayList<Ray>(270)
    AURORA.forEachIndexed { c, color ->
        val count = 90
        for (i in 0 until count) {
            val xn = i / (count - 1f)
            val x = xn * w * 1.1f - w * 0.05f
            val base = h * (0.42f + c * 0.05f) +
                sin(TWO_PI * (xn * 1.3f + t * 2 + c * 0.3f)) * h * 0.06f +
                sin(TWO_PI * (xn * 3.1f - t * 3 + c)) * h * 0.02f
            val height = h * (0.16f + 0.14f * (0.5f + 0.5f * sin(TWO_PI * (xn * 2.2f + t * 4 + c * 0.7f))))
            val intensity = 0.35f + 0.65f * (0.5f + 0.5f * sin(TWO_PI * (xn * 5f + t * 6 + c * 1.3f)))
            rays += Ray(x, base, base - height, intensity, color)
        }
    }
    val rayWidth = w / 90f * 1.7f
    rays.forEach { r ->
        drawLine(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.6f to r.color.copy(alpha = 0.12f * r.a),
                0.93f to r.color.copy(alpha = 0.42f * r.a),
                1f to Color.Transparent,
                startY = r.top,
                endY = r.base + 6f,
            ),
            Offset(r.x, r.top), Offset(r.x, r.base + 6f), strokeWidth = rayWidth,
        )
    }

    // Dağlar
    drawPath(ridge(w, lakeTop + 2f, lakeTop - h * 0.04f, h * 0.16f, 71, 18), Color(0xFF07131F))
    drawPath(ridge(w, lakeTop + 2f, lakeTop + 2f, h * 0.08f, 91, 26), Color(0xFF02070D))

    // Göl ve yansıma
    drawRect(Brush.verticalGradient(listOf(Color(0xFF041220), Color(0xFF010407)), startY = lakeTop, endY = h), topLeft = Offset(0f, lakeTop), size = Size(w, h - lakeTop))
    clipRect(0f, lakeTop, w, h) {
        rays.forEachIndexed { i, r ->
            val shimmer = sin(TWO_PI * (t * 16 + i * 0.07f)) * 2.5f
            val y0 = lakeTop + (lakeTop - r.base) * 0.5f
            val y1 = lakeTop + (lakeTop - r.top) * 0.5f
            drawLine(
                Brush.verticalGradient(listOf(r.color.copy(alpha = 0.22f * r.a), Color.Transparent), startY = y0, endY = y1),
                Offset(r.x + shimmer, y0), Offset(r.x + shimmer, y1), strokeWidth = rayWidth,
            )
        }
        for (k in 0 until 10) {
            val y = lakeTop + (h - lakeTop) * ((k + (t * 10f) % 1f) / 10f)
            drawLine(Color.White.copy(alpha = 0.035f), Offset(w * 0.1f, y), Offset(w * 0.9f, y), strokeWidth = 1f)
        }
    }
    vignette(0.6f)
}
