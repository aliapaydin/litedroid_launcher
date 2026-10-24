package dev.litedroid.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.litedroid.launcher.data.GoalEvent
import dev.litedroid.launcher.data.Match
import dev.litedroid.launcher.data.MatchState
import dev.litedroid.launcher.data.ScoresHub
import dev.litedroid.launcher.data.TeamScore
import dev.litedroid.launcher.media.LiveMedia
import dev.litedroid.launcher.tx
import kotlinx.coroutines.delay

private val LIVE_RED = Color(0xFFFF3B5C)
private val CARD_SHAPE = RoundedCornerShape(22.dp)

/** Koyu cam kart zemini (ekran koruyucu ve yüzen şerit için). */
fun Modifier.widgetGlass(shape: RoundedCornerShape = CARD_SHAPE, alpha: Float = 0.62f): Modifier =
    this
        .clip(shape)
        .background(Brush.linearGradient(listOf(Color(0xFF12131F).copy(alpha = alpha), Color(0xFF07070E).copy(alpha = alpha * 0.8f))))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f))), shape)

private fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** "Şimdi çalıyor": kapak, ad, sanatçı, uygulama ve ilerleme. Konum her saniye güncellenir. */
@Composable
fun NowPlayingCard(media: LiveMedia, modifier: Modifier = Modifier) {
    var pos by remember { mutableLongStateOf(media.positionNow()) }
    LaunchedEffect(media) {
        while (true) {
            pos = media.positionNow()
            delay(1_000)
        }
    }
    Row(
        modifier
            .width(400.dp)
            .widgetGlass()
            .glassSheen(0.7f)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(84.dp)
                .softShadow(14.dp, 1.3f)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3A7BFF), Color(0xFFB06BFF)))),
            contentAlignment = Alignment.Center,
        ) {
            val art = media.art
            if (art != null) {
                Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.size(84.dp))
            } else {
                Text("♪", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (media.playing) {
                    Equalizer()
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    (if (media.playing) tx("ŞİMDİ ÇALIYOR", "NOW PLAYING") else tx("DURAKLATILDI", "PAUSED")) + " · " + media.appLabel.uppercase(),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(media.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            media.artist?.let {
                Text(it, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (media.durationMs > 0) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((pos.toFloat() / media.durationMs).coerceIn(0f, 1f))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF3A7BFF), Color(0xFFB06BFF))))
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row {
                    Text(mmss(pos), color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Text(mmss(media.durationMs), color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                }
            }
        }
    }
}

/** Üç çubuklu küçük ekolayzer animasyonu (yalnızca çalarken). */
@Composable
private fun Equalizer() {
    var t by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(180)
            t++
        }
    }
    Row(Modifier.height(10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(0.3f, 0.9f, 0.6f).forEachIndexed { i, base ->
            val h = (base + ((t + i * 2) % 4) * 0.2f) % 1f
            Box(
                Modifier
                    .width(2.dp)
                    .fillMaxHeight(0.3f + h * 0.7f)
                    .background(Color(0xFF3DDC84))
            )
        }
    }
}

@Composable
fun TeamLogo(team: TeamScore, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        if (team.logo != null) {
            AsyncImage(team.logo, contentDescription = team.name, modifier = Modifier.size(size * 0.82f))
        } else {
            Text(team.abbr.take(3), color = Color.White, fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusChip(m: Match) {
    val live = m.state == MatchState.LIVE
    Text(
        m.status,
        color = if (live) Color.White else Color.White.copy(alpha = 0.7f),
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .width(44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (live) LIVE_RED else Color.White.copy(alpha = 0.08f))
            .padding(vertical = 2.dp),
    )
}

/** Tek maç satırı: logo · kısa ad · skor · kısa ad · logo · durum. */
@Composable
fun MatchRow(m: Match, compact: Boolean = false) {
    val logo = if (compact) 20.dp else 24.dp
    val nameSize = if (compact) 11.sp else 13.sp
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TeamLogo(m.home, logo)
        Spacer(Modifier.width(6.dp))
        Text(
            if (compact) m.home.abbr else m.home.short,
            color = Color.White, fontSize = nameSize, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text(
            if (m.state == MatchState.PRE) "–" else "${m.home.score ?: 0}  ${m.away.score ?: 0}",
            color = Color.White, fontSize = if (compact) 13.sp else 16.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Text(
            if (compact) m.away.abbr else m.away.short,
            color = Color.White, fontSize = nameSize, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        TeamLogo(m.away, logo)
        Spacer(Modifier.width(8.dp))
        StatusChip(m)
    }
}

/** Ekran koruyucudaki skor kartı. */
@Composable
fun ScoresCard(matches: List<Match>, modifier: Modifier = Modifier, max: Int = 5) {
    val live = matches.count { it.state == MatchState.LIVE }
    Column(
        modifier
            .width(400.dp)
            .widgetGlass()
            .glassSheen(0.7f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (live > 0) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(LIVE_RED)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                if (live > 0) tx("CANLI · $live MAÇ", "LIVE · $live MATCHES") else tx("BUGÜNÜN MAÇLARI", "TODAY'S MATCHES"),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        var lastLeague: String? = null
        matches.take(max).forEach { m ->
            if (m.league.code != lastLeague) {
                lastLeague = m.league.code
                Text(
                    m.league.label,
                    color = Color(0xFF9DB8FF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
            }
            Box(Modifier.padding(vertical = 3.dp)) { MatchRow(m) }
        }
        if (matches.size > max) {
            Text(
                tx("+${matches.size - max} maç daha", "+${matches.size - max} more"),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Gol olunca beliren kısa bildirim (8 sn). */
@Composable
fun GoalBanner(modifier: Modifier = Modifier) {
    var goal by remember { mutableStateOf<GoalEvent?>(null) }
    LaunchedEffect(Unit) {
        ScoresHub.goals.collect { g ->
            goal = g
            delay(8_000)
            if (goal == g) goal = null
        }
    }
    AnimatedVisibility(
        visible = goal != null,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier,
    ) {
        val g = goal ?: return@AnimatedVisibility
        val team = if (g.homeScored) g.match.home else g.match.away
        Row(
            Modifier
                .widthIn(max = 460.dp)
                .widgetGlass(RoundedCornerShape(50), alpha = 0.85f)
                .border(2.dp, LIVE_RED, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⚽", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(tx("GOL!", "GOAL!"), color = LIVE_RED, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(10.dp))
            TeamLogo(team, 24.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "${g.match.home.abbr} ${g.match.home.score ?: 0} - ${g.match.away.score ?: 0} ${g.match.away.abbr}",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            Text(g.match.status, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
}
