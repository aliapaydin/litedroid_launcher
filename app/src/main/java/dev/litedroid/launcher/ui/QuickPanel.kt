package dev.litedroid.launcher.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.I18n
import dev.litedroid.launcher.LauncherViewModel
import dev.litedroid.launcher.media.LiveMediaHub
import dev.litedroid.launcher.tx
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

private val TILE_ON = Brush.linearGradient(listOf(Color(0xFF3A7BFF), Color(0xFF7B5CFF)))
private val TILE_OFF = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.05f)))
private val TILE_FOCUS = Brush.linearGradient(listOf(Color.White, Color(0xFFE6E9FF)))

/** Hızlı panelde bir kutucuk: simge, ad ve durum. */
data class QuickTile(
    val key: String,
    val title: String,
    val state: String,
    val icon: ImageVector,
    val active: Boolean,
    val onClick: () -> Unit,
)

/** Alt kısımdaki kısayol düğmesi. */
data class QuickAction(val title: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * Android'in yukarıdan inen hızlı ayarlarından esinlenen, sağdan kayarak açılan panel:
 * saat ve durum, şimdi çalan için oynatma kontrolleri, açılıp kapanan kutucuklar ve kısayollar.
 */
@Composable
fun QuickPanel(
    vm: LauncherViewModel,
    tiles: List<QuickTile>,
    actions: List<QuickAction>,
    modifier: Modifier = Modifier,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { first.requestFocus() }
    }
    val now = rememberNow()
    val media by LiveMediaHub.current.collectAsState()
    val weather = if (vm.settings.weatherEnabled) vm.weather else null

    // İçerik ekrana sığmazsa (ör. şimdi çalan kartı açıkken) odak aşağı indikçe panel kendiliğinden kayar
    Column(
        modifier
            .width(440.dp)
            .fillMaxHeight()
            .padding(top = 16.dp, bottom = 16.dp, end = 16.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF15172A).copy(alpha = 0.97f), Color(0xFF0A0B14).copy(alpha = 0.98f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))), RoundedCornerShape(30.dp))
            .glassSheen(0.6f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        // ---- Saat ve durum ----
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                now.format(DateTimeFormatter.ofPattern("HH:mm", I18n.locale)),
                color = Color.White,
                fontSize = 40.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-1).sp,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                now.format(DateTimeFormatter.ofPattern(tx("d MMMM EEEE", "EEEE, MMMM d"), I18n.locale)).uppercase(I18n.locale),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 7.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WifiGlyph(Color.White.copy(alpha = 0.85f), vm.network.connected, 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(vm.network.label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            if (weather != null) {
                Spacer(Modifier.width(14.dp))
                WeatherGlyph(weather.code, weather.isDay, 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${weather.temperature}°C  ${weather.description}" + if (weather.city.isNotBlank()) " · ${weather.city}" else "",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // ---- Şimdi çalan ----
        val m = media
        if (m != null) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TILE_ON),
                    contentAlignment = Alignment.Center,
                ) {
                    val art = m.art
                    if (art != null) Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.size(50.dp))
                    else Text("♪", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(m.artist, m.appLabel).joinToString(" · "),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MediaButton("⏮") { LiveMediaHub.previous() }
                MediaButton(if (m.playing) "⏸" else "▶", big = true) { LiveMediaHub.playPause() }
                MediaButton("⏭") { LiveMediaHub.next() }
            }
        }

        // ---- Kutucuklar ----
        Spacer(Modifier.height(14.dp))
        tiles.chunked(2).forEachIndexed { r, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEachIndexed { c, tile ->
                    TileView(tile, Modifier.weight(1f), if (r == 0 && c == 0) first else null)
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(6.dp))
        // ---- Kısayollar ----
        actions.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { a -> ActionView(a, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun TileView(tile: QuickTile, modifier: Modifier, focusRequester: FocusRequester?) {
    var focused by remember { mutableStateOf(false) }
    val sounds = LocalSounds.current
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, spring(dampingRatio = 0.7f, stiffness = 700f), label = "tile")
    val fg = if (focused) Color(0xFF111322) else Color.White
    Row(
        modifier
            .height(70.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(22.dp))
            .background(
                when {
                    focused -> TILE_FOCUS
                    tile.active -> TILE_ON
                    else -> TILE_OFF
                }
            )
            .border(1.dp, Color.White.copy(alpha = if (focused || tile.active) 0f else 0.08f), RoundedCornerShape(22.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                if (it.isFocused && !focused) sounds?.play(Sfx.NAV)
                focused = it.isFocused
            }
            .onRemoteSelect({
                sounds?.play(Sfx.SELECT)
                tile.onClick()
            })
            .focusable()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    when {
                        focused && tile.active -> Color(0xFF3A7BFF)
                        focused -> Color(0xFF111322).copy(alpha = 0.08f)
                        tile.active -> Color.White.copy(alpha = 0.22f)
                        else -> Color.White.copy(alpha = 0.08f)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tile.icon, null, tint = if (focused && tile.active) Color.White else fg, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(tile.title, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tile.state, color = fg.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun ActionView(action: QuickAction, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val sounds = LocalSounds.current
    val fg = if (focused) Color(0xFF111322) else Color.White
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.07f))
            .onFocusChanged {
                if (it.isFocused && !focused) sounds?.play(Sfx.NAV)
                focused = it.isFocused
            }
            .onRemoteSelect({
                sounds?.play(Sfx.SELECT)
                action.onClick()
            })
            .focusable()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(action.icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(action.title, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
internal fun MediaButton(symbol: String, big: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val size = if (big) 40.dp else 34.dp
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(size)
            .clip(CircleShape)
            .background(
                when {
                    focused -> Color.White
                    big -> Color.White.copy(alpha = 0.16f)
                    else -> Color.Transparent
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = if (focused) Color(0xFF111322) else Color.White, fontSize = if (big) 16.sp else 14.sp)
    }
}

/** Panelde kullanılan simgeler (HomeScreen kutucukları kurarken kullanır). */
object QuickIcons {
    val layout = Icons.Filled.ThumbUp
    val sound = Icons.Filled.Notifications
    val scores = Icons.Filled.Star
    val weather = Icons.Filled.Place
    val hidden = Icons.Filled.Lock
    val motion = Icons.Filled.PlayArrow
    val showcase = Icons.Filled.Home
    val settings = Icons.Filled.Settings
    val store = Icons.Filled.ShoppingCart
    val about = Icons.Filled.Info
}
