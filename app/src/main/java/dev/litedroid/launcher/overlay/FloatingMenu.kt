package dev.litedroid.launcher.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.I18n
import dev.litedroid.launcher.data.MatchState
import dev.litedroid.launcher.data.ScoresHub
import dev.litedroid.launcher.data.SettingsRepository
import dev.litedroid.launcher.data.WeatherRepository
import dev.litedroid.launcher.media.LiveMediaHub
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.MatchRow
import dev.litedroid.launcher.ui.WeatherGlyph
import dev.litedroid.launcher.ui.onRemoteSelect
import dev.litedroid.launcher.ui.rememberNow
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

private val MenuAccent = Color(0xFF6B4DFF)
private val Ink = Color.White
private val InkSoft = Color.White.copy(alpha = 0.6f)

/**
 * Başka bir uygulama açıkken (Geri'ye iki kez basınca) sağdan kayarak açılan sade yüzen menü:
 * saat ve hava, çalan müzik, uyku zamanlayıcısı, skor şeridi ayarları, bugünün maçları ve kısayollar.
 * [visible] false olunca sağa kayarak çıkar (pencereyi servis animasyon bitince kaldırır).
 */
@Composable
fun FloatingMenu(
    visible: Boolean,
    sleepEnd: Long?,
    onSleep: (minutes: Int?) -> Unit,
    onClose: () -> Unit,
    onHome: () -> Unit,
    onAskClaude: () -> Unit,
    onScreenshot: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(tween(240)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(200)),
        ) {
            MenuCard(sleepEnd, onSleep, onClose, onHome, onAskClaude, onScreenshot)
        }
    }
}

@Composable
private fun MenuCard(
    sleepEnd: Long?,
    onSleep: (minutes: Int?) -> Unit,
    onClose: () -> Unit,
    onHome: () -> Unit,
    onAskClaude: () -> Unit,
    onScreenshot: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    var settings by remember { mutableStateOf(repo.load()) }
    val weather = remember { if (settings.weatherEnabled) WeatherRepository(context).cached() else null }
    val now = rememberNow()
    val media by LiveMediaHub.current.collectAsState()
    val matches by ScoresHub.matches.collectAsState()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(260)
        runCatching { first.requestFocus() }
    }
    // Kalan süre her saniye yenilensin
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepEnd) {
        while (sleepEnd != null) {
            tick = System.currentTimeMillis()
            delay(1_000)
        }
    }

    fun update(transform: (dev.litedroid.launcher.data.LauncherSettings) -> dev.litedroid.launcher.data.LauncherSettings) {
        val next = transform(settings)
        repo.save(next)
        settings = next
        ScoreOverlay.restart(context, next)
    }

    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .padding(top = 14.dp, bottom = 14.dp, end = 14.dp)
            .width(340.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF15172A).copy(alpha = 0.97f), Color(0xFF0A0B14).copy(alpha = 0.98f))))
            .border(1.dp, Color.White.copy(alpha = 0.1f), shape)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // ---- Saat ve hava ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    now.format(DateTimeFormatter.ofPattern("HH:mm", I18n.locale)),
                    color = Ink, fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.Light,
                )
                Text(
                    now.format(DateTimeFormatter.ofPattern(tx("d MMMM EEEE", "EEEE, MMMM d"), I18n.locale)),
                    color = InkSoft, fontSize = 10.sp,
                )
            }
            if (weather != null) {
                WeatherGlyph(weather.code, weather.isDay, 16.dp)
                Spacer(Modifier.width(5.dp))
                Text("${weather.temperature}°", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ---- Çalan ----
        val m = media
        if (m != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF3A7BFF), Color(0xFF7B5CFF)))),
                    contentAlignment = Alignment.Center,
                ) {
                    val art = m.art
                    if (art != null) Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp))
                    else Text("♪", color = Ink, fontSize = 15.sp)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.title, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(m.artist, m.appLabel).joinToString(" · "), color = InkSoft, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RoundButton("⏮", 28) { LiveMediaHub.previous() }
                RoundButton(if (m.playing) "⏸" else "▶", 32) { LiveMediaHub.playPause() }
                RoundButton("⏭", 28) { LiveMediaHub.next() }
            }
        }

        // ---- Uyku zamanlayıcısı ----
        Spacer(Modifier.height(12.dp))
        val untilEnd by SleepTimer.untilEnd.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(tx("UYKU ZAMANLAYICISI", "SLEEP TIMER"), Modifier.weight(1f))
            if (untilEnd) {
                Text(tx("İzlediğin bitince", "When the show ends"), color = Color(0xFFB9A8FF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            } else if (sleepEnd != null) {
                Text(
                    SleepTimer.formatRemaining(sleepEnd - tick) + " · " + SleepTimer.clock(sleepEnd),
                    color = Color(0xFFB9A8FF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val activeMinutes = if (sleepEnd != null) (SleepTimer.totalMs / 60_000L).toInt() else null
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(tx("Kapalı", "Off"), selected = sleepEnd == null && !untilEnd, modifier = Modifier.weight(1.3f).focusRequester(first)) { onSleep(null) }
            listOf(15, 30, 60, 90).forEach { min ->
                Chip("$min", selected = activeMinutes == min, modifier = Modifier.weight(1f)) { onSleep(min) }
            }
        }
        Spacer(Modifier.height(6.dp))
        // Süre yerine: oynatma durup 2 dk başlamazsa 5 sn geri sayımla kapanır
        Chip(
            "🌙 " + tx("İzlediğin bitince kapat", "Turn off when the show ends"),
            selected = untilEnd, modifier = Modifier.fillMaxWidth(),
        ) { onSleep(if (untilEnd) null else -1) }

        // ---- Skor şeridi ----
        Spacer(Modifier.height(12.dp))
        SectionLabel(tx("SKOR ŞERİDİ", "SCORE STRIP"))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(
                if (settings.floatingScores) tx("Şerit açık", "Strip on") else tx("Şerit kapalı", "Strip off"),
                selected = settings.floatingScores, modifier = Modifier.weight(1f),
            ) { update { it.copy(floatingScores = !it.floatingScores) } }
            Chip(
                if (settings.floatingOnlyLive) tx("Yalnızca canlı", "Live only") else tx("Bugünün tümü", "All today"),
                selected = settings.floatingOnlyLive, modifier = Modifier.weight(1f),
            ) { update { it.copy(floatingOnlyLive = !it.floatingOnlyLive) } }
        }

        // ---- Maçlar ----
        Spacer(Modifier.height(12.dp))
        val live = matches.count { it.state == MatchState.LIVE }
        SectionLabel(if (live > 0) tx("CANLI · $live MAÇ", "LIVE · $live MATCHES") else tx("BUGÜNÜN MAÇLARI", "TODAY'S MATCHES"))
        Spacer(Modifier.height(6.dp))
        if (matches.isEmpty()) {
            Text(tx("Takip ettiğin takımların bugün maçı yok.", "No matches today for the teams you follow."), color = InkSoft, fontSize = 11.sp)
        } else {
            val shown = matches.take(MAX_MATCHES)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                shown.forEach { match -> Box(Modifier.padding(vertical = 2.dp)) { MatchRow(match, compact = true) } }
                if (matches.size > shown.size) {
                    Text(
                        tx("+${matches.size - shown.size} maç daha", "+${matches.size - shown.size} more"),
                        color = InkSoft, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        // ---- Kısayollar ----
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconAction(tx("Ana ekran", "Home"), Icons.Filled.Home, Modifier.weight(1f)) { onHome() }
            IconAction(tx("Kapat", "Close"), Icons.Filled.Clear, Modifier.weight(1f)) { onClose() }
        }
    }
}

private const val MAX_MATCHES = 6

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = InkSoft, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = modifier)
}

@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    focused -> Color.White
                    selected -> MenuAccent
                    else -> Color.White.copy(alpha = 0.08f)
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (focused) Color(0xFF111114) else Ink,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun RoundButton(symbol: String, size: Int, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .padding(start = 3.dp)
            .size(size.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.1f))
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = if (focused) Color(0xFF111114) else Ink, fontSize = (size / 2.6f).sp)
    }
}

@Composable
private fun IconAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.07f))
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onClick)
            .focusable()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (focused) Color(0xFF111114) else Ink
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
