package dev.litedroid.launcher.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import dev.litedroid.launcher.ui.ActionView
import dev.litedroid.launcher.ui.MatchRow
import dev.litedroid.launcher.ui.MediaButton
import dev.litedroid.launcher.ui.QuickAction
import dev.litedroid.launcher.ui.QuickTile
import dev.litedroid.launcher.ui.SparkleGlyph
import dev.litedroid.launcher.ui.TileView
import dev.litedroid.launcher.ui.WeatherGlyph
import dev.litedroid.launcher.ui.glassSheen
import dev.litedroid.launcher.ui.rememberNow
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

/**
 * Başka bir uygulama açıkken (Geri'ye iki kez basınca) sağdan açılan yüzen menü:
 * saat ve hava, çalan müzik kontrolleri, skor ayarları, bugünün tüm maçları ve kısayollar.
 * İçerik sığmazsa odak indikçe kendiliğinden kayar. Geri ile kapanır.
 */
@Composable
fun FloatingMenu(onClose: () -> Unit, onHome: () -> Unit, onAskClaude: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    var settings by remember { mutableStateOf(repo.load()) }
    val weather = remember { if (settings.weatherEnabled) WeatherRepository(context).cached() else null }
    val now = rememberNow()
    val media by LiveMediaHub.current.collectAsState()
    val matches by ScoresHub.matches.collectAsState()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { first.requestFocus() }
    }

    fun update(transform: (dev.litedroid.launcher.data.LauncherSettings) -> dev.litedroid.launcher.data.LauncherSettings) {
        val next = transform(settings)
        repo.save(next)
        settings = next
        ScoreOverlay.restart(context, next)
    }

    Column(
        Modifier
            .fillMaxHeight()
            .padding(top = 16.dp, bottom = 16.dp, end = 16.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF15172A).copy(alpha = 0.97f), Color(0xFF0A0B14).copy(alpha = 0.98f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))), RoundedCornerShape(30.dp))
            .glassSheen(0.6f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        // ---- Saat ----
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
        if (weather != null) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherGlyph(weather.code, weather.isDay, 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${weather.temperature}°C  ${weather.description}" + if (weather.city.isNotBlank()) " · ${weather.city}" else "",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                )
            }
        }

        // ---- Çalan ----
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
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF3A7BFF), Color(0xFF7B5CFF)))),
                    contentAlignment = Alignment.Center,
                ) {
                    val art = m.art
                    if (art != null) Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp))
                    else Text("♪", color = Color.White, fontSize = 20.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(m.artist, m.appLabel).joinToString(" · "), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                MediaButton("⏮") { LiveMediaHub.previous() }
                MediaButton(if (m.playing) "⏸" else "▶", big = true) { LiveMediaHub.playPause() }
                MediaButton("⏭") { LiveMediaHub.next() }
            }
        }

        // ---- Skor ayarları ----
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TileView(
                QuickTile(
                    "float", tx("Yüzen skor", "Floating scores"),
                    if (settings.floatingScores) tx("Açık", "On") else tx("Kapalı", "Off"),
                    Icons.Filled.Star, active = settings.floatingScores,
                ) { update { it.copy(floatingScores = !it.floatingScores) } },
                Modifier.weight(1f),
                first,
            )
            TileView(
                QuickTile(
                    "live", tx("Şeritte", "In the strip"),
                    if (settings.floatingOnlyLive) tx("Yalnızca canlı", "Live only") else tx("Bugünün tümü", "All today"),
                    Icons.Filled.Refresh, active = settings.floatingOnlyLive,
                ) { update { it.copy(floatingOnlyLive = !it.floatingOnlyLive) } },
                Modifier.weight(1f),
                null,
            )
        }

        // ---- Bugünün maçları ----
        Spacer(Modifier.height(16.dp))
        val live = matches.count { it.state == MatchState.LIVE }
        Text(
            if (live > 0) tx("CANLI · $live MAÇ", "LIVE · $live MATCHES") else tx("BUGÜNÜN MAÇLARI", "TODAY'S MATCHES"),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(6.dp))
        if (matches.isEmpty()) {
            Text(
                tx("Bugün takip ettiğin liglerde maç yok.", "No matches today in the leagues you follow."),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                var lastLeague: String? = null
                matches.forEach { match ->
                    if (match.league.code != lastLeague) {
                        lastLeague = match.league.code
                        Text(
                            match.league.label,
                            color = Color(0xFF9DB8FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        )
                    }
                    Box(Modifier.padding(vertical = 3.dp)) { MatchRow(match, compact = true) }
                }
            }
        }

        // ---- Kısayollar ----
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionView(QuickAction(tx("Ana ekran", "Home"), Icons.Filled.Home) { onHome() }, Modifier.weight(1f))
            ActionView(QuickAction(tx("Claude'a sor", "Ask Claude"), Icons.Filled.Star) { onAskClaude() }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        ActionView(QuickAction(tx("Kapat (Geri)", "Close (Back)"), Icons.Filled.Clear) { onClose() }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SparkleGlyph(Color.White.copy(alpha = 0.4f), 12.dp)
            Spacer(Modifier.width(6.dp))
            Text(tx("LiteDroid · Geri'ye iki kez bas: menü", "LiteDroid · Double-press Back: menu"), color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
        }
    }
}
