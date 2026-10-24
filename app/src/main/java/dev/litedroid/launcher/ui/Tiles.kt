package dev.litedroid.launcher.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.litedroid.launcher.data.LauncherApp
import kotlinx.coroutines.launch

private val CARD_TEXT_SHADOW = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1.5f), 6f)

/**
 * Banner'ı olmayan uygulamalar için kart görseli:
 * arkada ikonun kendisi büyütülmüş ve bulanık (önceden pişirilmiş küçük bitmap), sağda büyük yarı saydam
 * ikon filigranı, üstte koyu degrade ve cam parlaması. [showText] ise sol üstte gölgeli ikon, altta ad.
 */
@Composable
fun IconCardArt(
    app: LauncherApp,
    focused: Boolean,
    compact: Boolean,
    showText: Boolean = true,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        listOf(lerp(app.accent, Color.Black, 0.3f), lerp(app.accent, Color(0xFF07070C), 0.78f))
                    )
                )
        )
        val blur = app.iconBlur
        if (blur != null) {
            Image(
                blur,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1.6f
                        scaleY = 1.6f
                        alpha = 0.9f
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
            )
        }
        // Sağda taşan büyük, silik ikon filigranı
        Image(
            app.icon,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = 1.3f
                    scaleY = 1.3f
                    translationX = size.width * 0.3f
                    rotationZ = -14f
                    alpha = 0.2f
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.02f),
                        0.5f to Color.Black.copy(alpha = 0.12f),
                        1f to Color.Black.copy(alpha = 0.62f),
                    )
                )
        )
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (focused) 0.16f else 0.07f), Color.Transparent)))
        )
        if (showText) {
            val iconSize = if (compact) 24.dp else 30.dp
            Image(
                app.icon,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 9.dp, top = 8.dp)
                    .size(iconSize)
                    .softShadow(8.dp, strength = 1.2f)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = if (compact) 6.dp else 8.dp)
            ) {
                Text(
                    app.label,
                    style = LocalTextStyle.current.copy(shadow = CARD_TEXT_SHADOW),
                    color = Color.White,
                    fontSize = if (compact) 11.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.1).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val category = app.category
                if (!compact && category != null) {
                    Text(
                        category,
                        style = LocalTextStyle.current.copy(shadow = CARD_TEXT_SHADOW),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Image(
                app.icon,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight(0.5f)
                    .aspectRatio(1f)
                    .softShadow(12.dp, strength = 1.3f)
                    .clip(RoundedCornerShape(22)),
            )
        }
    }
}

/** Uygulamanın görseli: TV banner'ı varsa o, yoksa bulanık ikon zeminli kart. */
@Composable
fun AppArt(app: LauncherApp, modifier: Modifier = Modifier) {
    Box(modifier) {
        val banner = app.banner
        if (banner != null) {
            Image(banner, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            IconCardArt(app, focused = false, compact = false, showText = false)
        }
    }
}

/**
 * Ana ekran karosu.
 * - Odaklanınca yaylanarak büyür (hız ayarından gelen sert/zıplamalı yay), renkli gölge alır,
 *   üzerinden ışık süzülür, adı belirir ve "tık" sesi çalar.
 * - Orta tuşa basınca hafifçe içeri çöker, bırakınca geri zıplar.
 * - Kenarda gidilecek yer yokken o yöne doğru kısa bir sarsıntı ve "tok" sesi verir.
 * - [moving] modunda titrer; yön tuşları karoyu taşır, orta tuş bitirir.
 */
@Composable
fun AppTile(
    app: LauncherApp,
    dimmed: Boolean,
    moving: Boolean,
    focusRequester: FocusRequester,
    columns: Int,
    index: Int,
    total: Int,
    onFocused: () -> Unit,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
    onMove: (Int) -> Unit,
    onMoveDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** Tam ekran bir sayfa açıkken false: odak arkaya kaçmasın. */
    enabled: Boolean = true,
) {
    val motion = LocalMotion.current
    val sounds = LocalSounds.current
    val scope = rememberCoroutineScope()

    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = when {
            focused && pressed -> 1.04f
            focused -> 1.14f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = motion.tileDamping, stiffness = motion.tileStiffness),
        label = "tileScale",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(motion.ms(200)),
        label = "tileLabel",
    )

    // Kenara çarpma sarsıntısı
    val shakeX = remember { Animatable(0f) }
    val shakeY = remember { Animatable(0f) }
    fun bump(dx: Float, dy: Float) {
        sounds?.play(Sfx.BUMP)
        scope.launch {
            if (dx != 0f) shakeX.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 900f), initialVelocity = dx * 900f)
        }
        scope.launch {
            if (dy != 0f) shakeY.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 900f), initialVelocity = dy * 900f)
        }
    }

    // Odaklanınca karonun üzerinden geçen ışık
    val sheen = remember { Animatable(-1f) }
    LaunchedEffect(focused) {
        if (focused) {
            sheen.snapTo(-0.5f)
            sheen.animateTo(1.5f, tween(motion.ms(1000), easing = FastOutSlowInEasing))
        }
    }

    val wobble = if (moving) {
        val transition = rememberInfiniteTransition(label = "wobble")
        transition.animateFloat(
            initialValue = -1.6f,
            targetValue = 1.6f,
            animationSpec = infiniteRepeatable(tween(110), RepeatMode.Reverse),
            label = "wobbleAngle",
        ).value
    } else 0f

    val shape = RoundedCornerShape(16.dp)
    val column = index % columns
    val lastRow = (total - 1) / columns

    Column(modifier.zIndex(if (focused) 1f else 0f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = wobble
                    translationX = shakeX.value
                    translationY = shakeY.value
                    alpha = if (dimmed) 0.4f else 1f
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                // Gölge yerine yumuşak ışıma halkası (GPU için çok daha ucuz)
                .softGlow(focused, lerp(app.accent, Color.White, 0.35f), 16.dp)
                .clip(shape)
                .then(if (moving) Modifier.border(2.dp, Color.White.copy(alpha = 0.9f), shape) else Modifier)
                .drawWithContent {
                    drawContent()
                    if (focused) {
                        drawRect(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                                endY = size.height * 0.5f,
                            )
                        )
                        val x = sheen.value * size.width
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
                                start = Offset(x - size.width * 0.35f, 0f),
                                end = Offset(x + size.width * 0.35f, size.height),
                            )
                        )
                    }
                }
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused && !focused) sounds?.play(Sfx.NAV)
                    focused = it.isFocused
                    if (!it.isFocused) pressed = false
                    if (it.isFocused) onFocused()
                }
                // Taşıma modu, normal seçimden önce tuşları yakalar
                .onPreviewKeyEvent { event ->
                    if (!moving || event.key == Key.Back) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionLeft -> onMove(-1)
                            Key.DirectionRight -> onMove(1)
                            Key.DirectionUp -> onMove(-columns)
                            Key.DirectionDown -> onMove(columns)
                            else -> Unit
                        }
                    }
                    if (event.type == KeyEventType.KeyUp && event.key.isSelectKey) onMoveDone()
                    true
                }
                // Basma tepkisi ve kenar sarsıntısı (olayı tüketmez, sadece izler)
                .onPreviewKeyEvent { event ->
                    if (event.key.isSelectKey) {
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) pressed = true
                        if (event.type == KeyEventType.KeyUp) pressed = false
                    } else if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        when (event.key) {
                            Key.DirectionLeft -> if (column == 0) bump(-1f, 0f)
                            Key.DirectionRight -> if (column == columns - 1 || index == total - 1) bump(1f, 0f)
                            Key.DirectionDown -> if (index / columns == lastRow) bump(0f, 1f)
                            else -> Unit
                        }
                    }
                    false
                }
                .onRemoteSelect(onSelect, onLongPress)
                .focusable(enabled = enabled)
        ) {
            AppArt(app, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer {
                alpha = labelAlpha
                compositingStrategy = CompositingStrategy.ModulateAlpha
                translationY = 6.dp.toPx() * labelAlpha
            },
        )
    }
}
