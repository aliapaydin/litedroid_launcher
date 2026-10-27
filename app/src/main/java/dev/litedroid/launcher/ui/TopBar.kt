package dev.litedroid.launcher.ui

import androidx.compose.animation.animateColorAsState
import dev.litedroid.launcher.I18n
import dev.litedroid.launcher.tx
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.litedroid.launcher.R
import dev.litedroid.launcher.data.LauncherApp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Yüzen üst barın kapladığı toplam yükseklik (kenar boşlukları dahil). */
val TOP_BAR_HEIGHT = 84.dp

val AVATAR_BRUSH = Brush.linearGradient(listOf(Color(0xFF8B5CFF), Color(0xFFFF4D9D), Color(0xFFFFA23D)))

/** Dakika başlarında güncellenen şimdiki zaman. */
@Composable
fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay((60 - now.second) * 1000L)
        }
    }
    return now
}

fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> tx("Günaydın", "Good morning")
    in 12..17 -> tx("İyi günler", "Good afternoon")
    in 18..22 -> tx("İyi akşamlar", "Good evening")
    else -> tx("İyi geceler", "Good night")
}

/**
 * Ekranın üstünde yüzen cam bar:
 * logo · profil ("İyi akşamlar, Ali") · ortada favori uygulamalar · saat · ayarlar.
 */
@Composable
fun TopBar(
    userName: String,
    showGreeting: Boolean,
    favorites: List<LauncherApp>,
    enabled: Boolean,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onBarFocus: (key: String, app: LauncherApp?) -> Unit,
    onFavoriteSelect: (LauncherApp) -> Unit,
    onFavoriteLongPress: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
    onAskClaude: (() -> Unit)? = null,
    /** Yeni Wrapped varsa barda hediye düğmesi çıkar (çan menüsünü açar). */
    onWrapped: (() -> Unit)? = null,
) {
    val now = rememberNow()
    Box(
        modifier
            .fillMaxWidth()
            .height(TOP_BAR_HEIGHT)
            .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 10.dp)
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(30.dp))
                .background(Color(0xFF0E0C1A).copy(alpha = 0.55f))
                .glass(RoundedCornerShape(30.dp))
                .glassSheen()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Logo + isim
            Image(
                painter = painterResource(R.drawable.logo_mark),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.width(9.dp))
            Wordmark(17.sp)
            Spacer(Modifier.width(14.dp))
            BarDivider(30.dp)
            Spacer(Modifier.width(10.dp))

            ProfileChip(
                name = userName,
                greeting = if (showGreeting) greetingFor(now.hour) else null,
                enabled = enabled,
                onFocus = { onBarFocus("profile", null) },
                onSelect = onProfile,
            )

            Spacer(Modifier.weight(1f))
            if (favorites.isNotEmpty()) {
                FavoritesDock(
                    apps = favorites,
                    enabled = enabled,
                    onFocus = { app -> onBarFocus("fav_" + app.packageName, app) },
                    onSelect = onFavoriteSelect,
                    onLongPress = onFavoriteLongPress,
                )
            }
            Spacer(Modifier.weight(1f))

            BarClock(now, 21.sp)
            Spacer(Modifier.width(12.dp))
            BarDivider(30.dp)
            Spacer(Modifier.width(12.dp))
            if (onWrapped != null) {
                GiftBarButton(enabled = enabled, onFocus = { onBarFocus("wrapped", null) }, onSelect = onWrapped)
                Spacer(Modifier.width(8.dp))
            }
            if (onAskClaude != null) {
                RoundBarButton(enabled = enabled, onFocus = { onBarFocus("claude", null) }, onSelect = onAskClaude, claude = true)
                Spacer(Modifier.width(8.dp))
            }
            RoundBarButton(enabled = enabled, onFocus = { onBarFocus("settings", null) }, onSelect = onSettings)
        }
    }
}

@Composable
private fun ProfileChip(
    name: String,
    greeting: String?,
    enabled: Boolean,
    onFocus: () -> Unit,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val motion = LocalMotion.current
    val sounds = LocalSounds.current
    val scale by animateFloatAsState(
        if (focused) 1.06f else 1f,
        spring(dampingRatio = motion.tileDamping, stiffness = motion.tileStiffness),
        label = "profileScale",
    )
    val bg by animateColorAsState(
        if (focused) Color.White.copy(alpha = 0.92f) else Color.Transparent,
        label = "profileBg",
    )
    val textColor = if (focused) Color(0xFF111114) else Color.White

    Row(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50))
            .background(bg)
            .onFocusChanged {
                if (it.isFocused && !focused) {
                    sounds?.play(Sfx.NAV)
                    onFocus()
                }
                focused = it.isFocused
            }
            .onRemoteSelect(onSelect)
            .focusable(enabled = enabled)
            .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dev.litedroid.launcher.tools.ProfileAvatar(name, 34.dp, 1.5.dp, 15)
        Spacer(Modifier.width(9.dp))
        Column {
            if (greeting != null) {
                Text(
                    greeting.uppercase(I18n.locale),
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = 8.5.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.9.sp,
                    maxLines = 1,
                )
            }
            Text(
                text = name,
                color = textColor,
                fontSize = 15.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Üst bardaki küçük, yüzen favori uygulamalar. */
@Composable
private fun FavoritesDock(
    apps: List<LauncherApp>,
    enabled: Boolean,
    onFocus: (LauncherApp) -> Unit,
    onSelect: (LauncherApp) -> Unit,
    onLongPress: (LauncherApp) -> Unit,
) {
    Row(
        Modifier
            .fillMaxHeight(0.82f)
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.04f))))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        apps.forEach { app ->
            key(app.packageName) {
                DockIcon(app, enabled, onFocus, onSelect, onLongPress)
            }
        }
    }
}

@Composable
private fun DockIcon(
    app: LauncherApp,
    enabled: Boolean,
    onFocus: (LauncherApp) -> Unit,
    onSelect: (LauncherApp) -> Unit,
    onLongPress: (LauncherApp) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val motion = LocalMotion.current
    val sounds = LocalSounds.current
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.3f else 1f,
        animationSpec = spring(dampingRatio = motion.tileDamping, stiffness = motion.tileStiffness),
        label = "dockScale",
    )
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(32.dp)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = if (focused) -2.dp.toPx() else 0f
            }
            .softGlow(focused, lerp(app.accent, Color.White, 0.35f), 10.dp)
            .clip(shape)
            .background(app.accent)
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .onFocusChanged {
                if (it.isFocused && !focused) {
                    sounds?.play(Sfx.NAV)
                    onFocus(app)
                }
                focused = it.isFocused
            }
            .onRemoteSelect({ onSelect(app) }, { onLongPress(app) })
            .focusable(enabled = enabled)
    ) {
        Image(app.icon, contentDescription = app.label, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun RoundBarButton(enabled: Boolean, onFocus: () -> Unit, onSelect: () -> Unit, claude: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val motion = LocalMotion.current
    val sounds = LocalSounds.current
    val scale by animateFloatAsState(
        if (focused) 1.15f else 1f,
        spring(dampingRatio = motion.tileDamping, stiffness = motion.tileStiffness),
        label = "gearScale",
    )
    Box(
        Modifier
            .size(38.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (focused) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.09f))
            .then(if (focused) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape))
            .onFocusChanged {
                if (it.isFocused && !focused) {
                    sounds?.play(Sfx.NAV)
                    onFocus()
                }
                focused = it.isFocused
            }
            .onRemoteSelect(onSelect)
            .focusable(enabled = enabled),
        contentAlignment = Alignment.Center,
    ) {
        if (claude) {
            SparkleGlyph(if (focused) Color(0xFFC8553D) else Color(0xFFF2A07B), 19.dp)
        } else {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = tx("Hızlı panel", "Quick panel"),
                tint = if (focused) Color(0xFF111114) else Color.White,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/** Yeni Wrapped düğmesi: dönen renkli halka içinde zıplayan 🎁. */
@Composable
private fun GiftBarButton(enabled: Boolean, onFocus: () -> Unit, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val sounds = LocalSounds.current
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "gift")
    val spin by t.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2_200, easing = androidx.compose.animation.core.LinearEasing)), label = "spin")
    val hop by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "hop")
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(44.dp).graphicsLayer { rotationZ = spin }) {
            drawCircle(
                Brush.sweepGradient(listOf(Color(0xFFFFD35C), Color(0xFFFF6FB5), Color(0xFF8E7BFF), Color(0xFF5CFFB0), Color(0xFFFFD35C))),
                radius = size.minDimension / 2 - 2.dp.toPx(),
                style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()),
            )
        }
        Box(
            Modifier
                .size(36.dp)
                .graphicsLayer { val s = if (focused) 1.15f else 1f; scaleX = s; scaleY = s }
                .clip(CircleShape)
                .background(if (focused) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.09f))
                .onFocusChanged {
                    if (it.isFocused && !focused) {
                        sounds?.play(Sfx.NAV)
                        onFocus()
                    }
                    focused = it.isFocused
                }
                .onRemoteSelect(onSelect)
                .focusable(enabled = enabled),
            contentAlignment = Alignment.Center,
        ) {
            Text("🎁", fontSize = 17.sp, modifier = Modifier.graphicsLayer { translationY = -3f * hop })
        }
    }
}
