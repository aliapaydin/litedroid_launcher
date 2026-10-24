package dev.litedroid.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Kumandanın orta tuşu ya da klavye Enter'ı. */
val Key.isSelectKey: Boolean
    get() = this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter

private val DIRECTION_KEYS = setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)

/**
 * Koyu cam yüzey: koyu, hafif saydam zemin + üstte çok hafif ışık + yumuşak ince kenar.
 * Canlı bulanıklık (blur) kullanılmaz; TV GPU'sunda en pahalı efekt odur. Koyu zemin,
 * bulanıklık olmadan da okunaklı ve "premium" bir görünüm verir.
 */
fun Modifier.glass(shape: Shape = RoundedCornerShape(28.dp), strong: Boolean = false): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                Color(0xFF1C1A2E).copy(alpha = if (strong) 0.94f else 0.78f),
                Color(0xFF0E0D18).copy(alpha = if (strong) 0.96f else 0.82f),
            )
        )
    )
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))),
        shape = shape,
    )

/**
 * Odaktaki öğenin etrafında yumuşak, ışıyan halka. Gölge (shadow) yerine birkaç saydam
 * çizgi çizer: hem daha yumuşak görünür hem de GPU için çok daha ucuzdur.
 */
/**
 * Ucuz yumuşak gölge: altında hafifçe aşağı kaymış, gittikçe silikleşen birkaç yuvarlak dikdörtgen.
 * GPU gölgesi (elevation) gibi her karede bulanıklık hesaplamaz.
 */
fun Modifier.softShadow(corner: Dp, strength: Float = 1f): Modifier = this.drawBehind {
    val r = corner.toPx()
    val drop = 2.dp.toPx()
    for (i in 1..4) {
        val spread = i * 1.6f * density
        drawRoundRect(
            color = Color.Black.copy(alpha = (0.24f - i * 0.05f) * strength),
            topLeft = Offset(-spread, -spread + drop),
            size = Size(size.width + spread * 2, size.height + spread * 2),
            cornerRadius = CornerRadius(r + spread),
        )
    }
}

fun Modifier.softGlow(visible: Boolean, color: Color, corner: Dp): Modifier =
    if (!visible) this else this.drawBehind {
        val r = corner.toPx()
        for (i in 1..4) {
            val spread = i * 3.dp.toPx()
            drawRoundRect(
                color = color.copy(alpha = 0.16f - i * 0.03f),
                topLeft = Offset(-spread, -spread),
                size = Size(size.width + spread * 2, size.height + spread * 2),
                cornerRadius = CornerRadius(r + spread),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }

/**
 * Kumanda seçimi. Tıklama tuş BIRAKILINCA tetiklenir ve yalnızca basış bu öğede başladıysa;
 * böylece uzun basışla açılan menüyü kapatan tuş, arkadaki uygulamayı yanlışlıkla açmaz.
 * Tuşu basılı tutmak (ilk tekrar ≈ 0,5 sn) [onLongPress]'i çağırır.
 */
fun Modifier.onRemoteSelect(onSelect: () -> Unit, onLongPress: (() -> Unit)? = null): Modifier = composed {
    var armed by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    val currentSelect by rememberUpdatedState(onSelect)
    val currentLong by rememberUpdatedState(onLongPress)
    val sounds = LocalSounds.current

    onPreviewKeyEvent { event ->
        if (!event.key.isSelectKey) return@onPreviewKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (event.nativeKeyEvent.repeatCount == 0) {
                    armed = true
                    longFired = false
                } else if (armed && !longFired && currentLong != null) {
                    longFired = true
                    armed = false
                    currentLong?.invoke()
                }
            }
            KeyEventType.KeyUp -> {
                if (armed && !longFired) {
                    sounds?.play(Sfx.SELECT)
                    currentSelect()
                }
                armed = false
            }
        }
        true
    }
}

data class MenuItem(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val action: () -> Unit,
)

/**
 * Cam menü. Odak tek bir düğümde kalır ve seçim "sanal" olarak yönetilir;
 * böylece yön tuşları odağı menünün dışına, arkadaki karolara kaçıramaz.
 */
@Composable
fun GlassMenu(
    title: String,
    subtitle: String?,
    items: List<MenuItem>,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    var selected by remember { mutableIntStateOf(0) }
    var armed by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val sounds = LocalSounds.current
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val current = selected.coerceIn(0, (items.size - 1).coerceAtLeast(0))

    Column(
        modifier
            .width(360.dp)
            .glass(RoundedCornerShape(28.dp), strong = true)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                when {
                    event.key == Key.Back -> false
                    event.key.isSelectKey -> {
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) armed = true
                        if (event.type == KeyEventType.KeyUp) {
                            if (armed) {
                                sounds?.play(Sfx.SELECT)
                                items.getOrNull(current)?.action?.invoke()
                            }
                            armed = false
                        }
                        true
                    }
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp -> {
                        sounds?.play(if (current > 0) Sfx.NAV else Sfx.BUMP)
                        selected = (current - 1).coerceAtLeast(0); true
                    }
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> {
                        sounds?.play(if (current < items.lastIndex) Sfx.NAV else Sfx.BUMP)
                        selected = (current + 1).coerceAtMost(items.lastIndex); true
                    }
                    event.key in DIRECTION_KEYS -> true
                    else -> false
                }
            }
            .focusable()
            .padding(20.dp)
    ) {
        if (header != null) {
            header()
            Spacer(Modifier.height(14.dp))
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        items.forEachIndexed { index, item -> MenuRow(item, selected = index == current) }
    }
}

@Composable
private fun MenuRow(item: MenuItem, selected: Boolean) {
    val background by animateColorAsState(
        targetValue = if (selected) Color.White.copy(alpha = 0.92f) else Color.Transparent,
        label = "rowBg",
    )
    val foreground = when {
        selected && item.destructive -> Color(0xFFD93025)
        selected -> Color(0xFF111114)
        item.destructive -> Color(0xFFFF6B6B)
        else -> Color.White
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, contentDescription = null, tint = foreground, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = item.label,
            color = foreground,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
