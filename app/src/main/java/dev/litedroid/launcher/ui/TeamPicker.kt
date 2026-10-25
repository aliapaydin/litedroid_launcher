package dev.litedroid.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.litedroid.launcher.LauncherViewModel
import dev.litedroid.launcher.data.League
import dev.litedroid.launcher.data.TeamCatalog
import dev.litedroid.launcher.data.TeamInfo
import dev.litedroid.launcher.tx

private val Accent = Color(0xFF6B4DFF)

/**
 * Takım seçici: solda ligler, sağda seçili ligin takımları. Bir ligde hiç seçim yapılmamışsa tüm takımlar
 * takiptedir; tek tek seçince yalnızca seçili takımların maçları skor kartında, yüzen şeritte ve
 * ekran koruyucuda görünür. "Tümünü seç" ve "Seçimi kaldır" tek dokunuşla hepsini değiştirir.
 */
@Composable
fun TeamPickerScreen(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val settings = vm.settings
    var league by remember { mutableStateOf(League.entries.first()) }
    var teams by remember { mutableStateOf<List<TeamInfo>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(league) {
        teams = null
        failed = false
        teams = runCatching { TeamCatalog.teams(league) }.getOrNull()
        failed = teams.isNullOrEmpty()
    }
    val firstLeague = remember { FocusRequester() }
    val firstAction = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstLeague.requestFocus() } }

    val follow: Set<String>? = settings.followedTeams[league.code]
    val leagueOn = league.code in settings.scoreLeagues
    val allIds = teams.orEmpty().map { it.id }.toSet()

    fun setFollow(next: Set<String>?) {
        vm.updateSettings { s ->
            val map = s.followedTeams.toMutableMap()
            if (next == null) map.remove(league.code) else map[league.code] = next
            // Takım seçilince lig de açılsın
            val leagues = if (!next.isNullOrEmpty() || next == null) s.scoreLeagues + league.code else s.scoreLeagues
            s.copy(followedTeams = map, scoreLeagues = leagues)
        }
    }

    fun toggle(id: String) {
        val current = follow ?: allIds
        val next = if (id in current) current - id else current + id
        setFollow(if (allIds.isNotEmpty() && next == allIds) null else next)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0C0D1C), Color(0xFF07080F))))
    ) {
        Row(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 30.dp, bottom = 24.dp)) {
            // ---- Sol: ligler ----
            Column(Modifier.width(250.dp).fillMaxHeight()) {
                Text(tx("Takımlar", "Teams"), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    tx("Her ligden takip edeceğin takımları seç", "Pick the teams to follow in each league"),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(14.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(League.entries.toList(), key = { it.code }) { l ->
                        val f = settings.followedTeams[l.code]
                        LeagueRow(
                            league = l,
                            status = when {
                                l.code !in settings.scoreLeagues -> tx("Kapalı", "Off")
                                f == null -> tx("Tüm takımlar", "All teams")
                                f.isEmpty() -> tx("Takım yok", "No teams")
                                else -> tx("${f.size} takım", "${f.size} teams")
                            },
                            on = l.code in settings.scoreLeagues,
                            selected = l == league,
                            modifier = (if (l == League.entries.first()) Modifier.focusRequester(firstLeague) else Modifier)
                                .focusProperties { right = firstAction },
                            onFocused = { league = l },
                        )
                    }
                }
            }
            Spacer(Modifier.width(28.dp))

            // ---- Sağ: takımlar ----
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(league.flag + "  " + league.label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Pill(
                        if (leagueOn) tx("Lig açık", "League on") else tx("Lig kapalı", "League off"),
                        active = leagueOn,
                        modifier = Modifier.focusRequester(firstAction),
                    ) {
                        vm.updateSettings { s ->
                            s.copy(scoreLeagues = if (leagueOn) s.scoreLeagues - league.code else s.scoreLeagues + league.code)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Pill(tx("Tümünü seç", "Select all"), active = follow == null) { setFollow(null) }
                    Spacer(Modifier.width(8.dp))
                    Pill(tx("Seçimi kaldır", "Clear"), active = follow?.isEmpty() == true) { setFollow(emptySet()) }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        !leagueOn -> tx("Bu lig kapalı: maçları hiçbir yerde görünmez. Bir takım seçersen lig açılır.",
                            "This league is off: its matches show nowhere. Picking a team turns it on.")
                        follow == null -> tx("Tüm takımlar takipte. Yalnızca istediklerini görmek için takımlara tek tek bas.",
                            "All teams are followed. Press teams one by one to keep only the ones you want.")
                        follow.isEmpty() -> tx("Hiç takım seçili değil: bu ligden maç gösterilmez.", "No team selected: no matches from this league.")
                        else -> tx("Yalnızca seçili ${follow.size} takımın maçları skor kartında, şeritte ve ekran koruyucuda görünür.",
                            "Only matches of the ${follow.size} selected teams show in the scores card, strip and screensaver.")
                    },
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
                val list = teams
                when {
                    list == null && !failed -> Text(tx("Takımlar yükleniyor…", "Loading teams…"), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                    list.isNullOrEmpty() -> Text(
                        tx("Takım listesi alınamadı. İnternet bağlantısını kontrol et.", "Couldn't load the teams. Check the internet connection."),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(list, key = { it.id }) { team ->
                            TeamTile(team, selected = follow == null || team.id in follow, dim = !leagueOn) { toggle(team.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeagueRow(
    league: League,
    status: String,
    on: Boolean,
    selected: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.94f)
                    selected -> Accent.copy(alpha = 0.5f)
                    else -> Color.Transparent
                }
            )
            .onFocusChanged {
                if (it.isFocused && !focused) onFocused()
                focused = it.isFocused
            }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(league.flag, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                league.label,
                color = if (focused) Color(0xFF111114) else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                status,
                color = when {
                    focused -> Color(0xFF111114).copy(alpha = 0.6f)
                    on -> Color.White.copy(alpha = 0.6f)
                    else -> Color(0xFFFF8A8A)
                },
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun Pill(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    focused -> Color.White
                    active -> Accent
                    else -> Color.White.copy(alpha = 0.1f)
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onClick)
            .focusable()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (focused) Color(0xFF111114) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TeamTile(team: TeamInfo, selected: Boolean, dim: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(shape)
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.94f)
                    selected && !dim -> Accent.copy(alpha = 0.32f)
                    else -> Color(0xFF14162A)
                }
            )
            .border(1.dp, if (selected && !dim) Accent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.08f), shape)
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onClick)
            .focusable()
            .padding(8.dp),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (team.logo != null) {
                    AsyncImage(team.logo, contentDescription = team.name, modifier = Modifier.size(44.dp))
                } else {
                    Text(team.short.take(2).uppercase(), color = if (focused) Color(0xFF111114) else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                team.short,
                color = when {
                    focused -> Color(0xFF111114)
                    selected && !dim -> Color.White
                    else -> Color.White.copy(alpha = 0.55f)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Seçim işareti
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (selected) Accent else Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
