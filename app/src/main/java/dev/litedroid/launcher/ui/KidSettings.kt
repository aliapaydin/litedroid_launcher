package dev.litedroid.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.LauncherViewModel
import dev.litedroid.launcher.data.Parental
import dev.litedroid.launcher.data.Profiles
import dev.litedroid.launcher.data.SaverScene
import dev.litedroid.launcher.data.SoundLevel
import dev.litedroid.launcher.data.UiLayout
import dev.litedroid.launcher.tools.ProfileFace
import dev.litedroid.launcher.tx
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** Çocuk ayar kartı: emoji, başlık, şu anki değer, iki renkli zemin. */
private class KidTile(val emoji: String, val title: String, val value: String?, val colors: List<Color>, val action: () -> Unit)

/**
 * Çocuk profilinin ayarları: büyük, renkli kartlar. Yalnızca çocuğun kendi zevkine ait şeyler var
 * (tema, arayüz, sesler, ekran koruyucu, avatar, Wrapped). Ağ, anahtar, güncelleme, kumanda, kilitler,
 * canlı skorlar ve ebeveyn ayarları burada yok: ebeveynin bıraktığı gibi geçerli.
 */
@Composable
fun KidSettingsScreen(
    vm: LauncherViewModel,
    onClose: () -> Unit,
    onSwitchProfile: () -> Unit,
    onAvatar: () -> Unit,
    onWrapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val settings = vm.settings
    val profiles by Profiles.list.collectAsState()
    val activeId by Profiles.active.collectAsState()
    val me = profiles.firstOrNull { it.id == activeId }
    val version by Parental.version.collectAsState()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { first.requestFocus() }
    }
    BackHandler(onBack = onClose)

    val hasKey = settings.geminiKey.isNotBlank()
    val scenes = listOfNotNull(SaverScene.MIXED, SaverScene.CINEMA, SaverScene.GAMES, SaverScene.NIGHT, if (hasKey) SaverScene.CANVAS else null)
    val tiles = listOf(
        KidTile("🎨", tx("Renkler", "Colors"), vm.wallpaper.label, listOf(Color(0xFFFF5FA2), Color(0xFFFF9A3D))) { vm.cycleTheme() },
        KidTile("🧩", tx("Ekran düzeni", "Layout"), settings.layout.label, listOf(Color(0xFF7C5CFF), Color(0xFF4FA8FF))) {
            vm.updateSettings { it.copy(layout = if (it.layout == UiLayout.CASUAL) UiLayout.MODERN else UiLayout.CASUAL) }
        },
        KidTile("🔔", tx("Tık sesleri", "Click sounds"), if (settings.soundsEnabled) tx("Açık", "On") else tx("Kapalı", "Off"), listOf(Color(0xFF22C55E), Color(0xFF14B8A6))) {
            vm.updateSettings { it.copy(soundsEnabled = !it.soundsEnabled) }
        },
        KidTile("🔊", tx("Ses yüksekliği", "Sound level"), settings.soundLevel.label, listOf(Color(0xFFFFC23D), Color(0xFFFF7A45))) {
            vm.updateSettings { it.copy(soundLevel = SoundLevel.entries[(it.soundLevel.ordinal + 1) % SoundLevel.entries.size]) }
        },
        KidTile("🌙", tx("Ekran koruyucu", "Screensaver"), (if (settings.saverScene in scenes) settings.saverScene else SaverScene.MIXED).label, listOf(Color(0xFF3B5BFF), Color(0xFF9D4DFF))) {
            vm.updateSettings { s -> s.copy(saverScene = scenes[(scenes.indexOf(s.saverScene) + 1).mod(scenes.size)]) }
        },
        KidTile("🧑‍🎨", tx("Avatarım", "My avatar"), tx("Yüzünü tasarla", "Design your face"), listOf(Color(0xFFFF6B6B), Color(0xFFFFB86B)), onAvatar),
        KidTile("🎁", tx("Haftam", "My week"), "Wrapped", listOf(Color(0xFFEC4899), Color(0xFF8B5CF6)), onWrapped),
        KidTile("👋", tx("Profil değiştir", "Switch profile"), tx("Kim İzliyor?", "Who's watching?"), listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)), onSwitchProfile),
    )

    Box(modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2B1260), Color(0xFF5B1E7A), Color(0xFF1B3A7A))))) {
        Bubbles()
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 30.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (me != null) ProfileFace(me, 64.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(tx("🎈 Benim ayarlarım", "🎈 My settings"), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Text(tx("Merhaba ${me?.name ?: ""}! Kartı seç, Tamam'a bas, değişsin.", "Hi ${me?.name ?: ""}! Pick a card and press OK to change it."),
                        color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                }
                // Bugün kalan süre (ebeveyn koyduysa)
                if (me != null && version >= 0) {
                    val rem = Parental.remainingMs(ctx, me.id)
                    if (rem != null) {
                        Column(
                            Modifier.clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.16f))
                                .border(2.dp, Color(0xFFFFE066), RoundedCornerShape(22.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("⏳ " + msLabel(rem), color = Color(0xFFFFE066), fontSize = 22.sp, fontWeight = FontWeight.Black)
                            Text(tx("bugün kalan", "left today"), color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            tiles.chunked(4).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                    row.forEachIndexed { c, t ->
                        KidCard(t, if (r == 0 && c == 0) Modifier.focusRequester(first) else Modifier)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                tx("Diğer ayarları annen ya da baban yapar 💛", "Your parent takes care of the other settings 💛"),
                color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun KidCard(t: KidTile, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "s")
    val wobble by rememberInfiniteTransition(label = "w").animateFloat(-6f, 6f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "r")
    Column(
        modifier
            .size(width = 196.dp, height = 150.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(t.colors))
            .border(if (focused) 4.dp else 0.dp, Color.White, RoundedCornerShape(30.dp))
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(t.action)
            .focusable()
            .padding(16.dp),
    ) {
        Text(t.emoji, fontSize = 40.sp, modifier = Modifier.graphicsLayer { rotationZ = if (focused) wobble else 0f })
        Spacer(Modifier.weight(1f))
        Text(t.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (t.value != null) {
            Text(t.value, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 2.dp))
        }
    }
}

/** Arkada yavaşça süzülen renkli baloncuklar. */
@Composable
private fun Bubbles() {
    val t by rememberInfiniteTransition(label = "b").animateFloat(0f, 6.2832f, infiniteRepeatable(tween(20_000, easing = LinearEasing)), label = "t")
    val colors = listOf(Color(0xFFFF5FA2), Color(0xFFFFE066), Color(0xFF6BCBFF), Color(0xFF7CFFCB), Color(0xFFFF9A3D), Color(0xFFB794FF))
    Canvas(Modifier.fillMaxSize()) {
        for (i in 0 until 12) {
            val ph = i * 0.9f
            val x = size.width * ((i * 0.083f + 0.05f + 0.03f * sin(t + ph)) % 1f)
            val y = size.height * (0.15f + 0.7f * ((0.5f + 0.5f * cos(t * 0.7f + ph))))
            val r = 20f + (i % 4) * 14f
            drawCircle(colors[i % colors.size].copy(alpha = 0.18f), r, Offset(x, y))
        }
    }
}
