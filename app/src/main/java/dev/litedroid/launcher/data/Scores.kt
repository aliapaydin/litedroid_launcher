package dev.litedroid.launcher.data

import java.time.OffsetDateTime
import android.content.Context
import android.util.Log
import dev.litedroid.launcher.tx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Takip edilebilen ligler (ESPN lig kodları). */
enum class League(val code: String, private val tr: String, private val en: String, val flag: String) {
    TUR("tur.1", "Süper Lig", "Süper Lig", "🇹🇷"),
    ENG("eng.1", "Premier League", "Premier League", "🏴"),
    ESP("esp.1", "LaLiga", "LaLiga", "🇪🇸"),
    ITA("ita.1", "Serie A", "Serie A", "🇮🇹"),
    GER("ger.1", "Bundesliga", "Bundesliga", "🇩🇪"),
    FRA("fra.1", "Ligue 1", "Ligue 1", "🇫🇷");

    val label: String get() = tx(tr, en)

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code }
    }
}

data class TeamScore(
    val name: String,
    val short: String,
    val abbr: String,
    val logo: String?,
    val score: Int?,
    val id: String = "",
    val redCards: Int = 0,
)

/** Maçtaki son gol ya da kırmızı kart: oyuncu, dakika, hangi takım. */
data class MatchMoment(val player: String?, val minute: String?, val home: Boolean, val ownGoal: Boolean = false, val penalty: Boolean = false)

enum class MatchState { PRE, LIVE, DONE }

data class Match(
    val id: String,
    val league: League,
    val home: TeamScore,
    val away: TeamScore,
    val state: MatchState,
    /** Canlıyken dakika ("67'"), bittiyse "MS"/"FT", başlamadıysa saat. */
    val status: String,
    val start: Instant,
    val lastGoal: MatchMoment? = null,
    val lastRedCard: MatchMoment? = null,
)

/** Şeritte ve ekran koruyucuda kısa animasyonla gösterilen maç olayları. */
sealed interface MatchEvent {
    val match: Match
    val at: Long

    data class Goal(override val match: Match, val home: Boolean, val moment: MatchMoment?, override val at: Long = System.currentTimeMillis()) : MatchEvent
    data class RedCard(override val match: Match, val home: Boolean, val moment: MatchMoment?, override val at: Long = System.currentTimeMillis()) : MatchEvent
    data class FullTime(override val match: Match, override val at: Long = System.currentTimeMillis()) : MatchEvent
}

/**
 * Canlı skorlar. Ekran koruyucu, yüzen skor şeridi ya da launcher ihtiyaç duyduğunda [acquire] ile
 * çalışır, kimse kullanmayınca durur. Canlı maç varken 45 sn'de, yokken 5 dk'da bir yenilenir.
 * Veri ESPN'in herkese açık skor tablosundan gelir (anahtar gerektirmez).
 */
object ScoresHub {

    private const val TAG = "LiteDroidScores"

    /** ESPN tarihleri saniyesiz gelir: "2026-08-14T18:30Z". */
    private val ESPN_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm[:ss]X")

    private val _matches = MutableStateFlow<List<Match>>(emptyList())
    val matches: StateFlow<List<Match>> = _matches

    private val _events = MutableSharedFlow<MatchEvent>(extraBufferCapacity = 16)
    /** Gol, kırmızı kart, maç sonu. */
    val events: SharedFlow<MatchEvent> = _events

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val users = HashSet<String>()

    @Volatile
    var leagues: Set<String> = League.entries.map { it.code }.toSet()
        set(value) {
            if (field != value) {
                field = value
                refreshNow()
            }
        }

    /** [who] skorlara ihtiyaç duyuyor (ör. "saver", "overlay"). */
    fun acquire(context: Context, who: String) {
        synchronized(this) {
            users += who
            if (job?.isActive == true) return
            job = scope.launch { loop() }
        }
        Log.i(TAG, "acquire $who users=$users")
    }

    fun release(who: String) {
        synchronized(this) {
            users -= who
            if (users.isEmpty()) {
                job?.cancel()
                job = null
            }
        }
    }

    fun refreshNow() {
        synchronized(this) {
            if (users.isEmpty()) return
            job?.cancel()
            job = scope.launch { loop() }
        }
    }

    private suspend fun loop() {
        while (scope.isActive) {
            val list = runCatching { fetchAll(leagues) }
                .onFailure { Log.w(TAG, "fetch failed: ${it.message}") }
                .getOrNull()
            if (list != null) publish(list)
            val anyLive = _matches.value.any { it.state == MatchState.LIVE }
            val soon = _matches.value.any {
                it.state == MatchState.PRE && it.start.toEpochMilli() - System.currentTimeMillis() < 15 * 60_000
            }
            delay(if (anyLive || soon) 45_000 else 300_000)
        }
    }

    private fun publish(list: List<Match>) {
        val old = _matches.value.associateBy { it.id }
        list.forEach { m ->
            val o = old[m.id] ?: return@forEach
            if ((m.home.score ?: 0) > (o.home.score ?: 0)) {
                _events.tryEmit(MatchEvent.Goal(m, home = true, moment = m.lastGoal?.takeIf { it.home }))
            }
            if ((m.away.score ?: 0) > (o.away.score ?: 0)) {
                _events.tryEmit(MatchEvent.Goal(m, home = false, moment = m.lastGoal?.takeIf { !it.home }))
            }
            if (m.home.redCards > o.home.redCards) {
                _events.tryEmit(MatchEvent.RedCard(m, home = true, moment = m.lastRedCard?.takeIf { it.home }))
            }
            if (m.away.redCards > o.away.redCards) {
                _events.tryEmit(MatchEvent.RedCard(m, home = false, moment = m.lastRedCard?.takeIf { !it.home }))
            }
            if (o.state == MatchState.LIVE && m.state == MatchState.DONE) _events.tryEmit(MatchEvent.FullTime(m))
        }
        // Canlılar önce, sonra başlayacaklar (saate göre), sonra bitenler
        _matches.value = list.sortedWith(
            compareBy<Match>({ when (it.state) { MatchState.LIVE -> 0; MatchState.PRE -> 1; MatchState.DONE -> 2 } }, { it.start })
        )
    }

    private suspend fun fetchAll(codes: Set<String>): List<Match> = kotlinx.coroutines.coroutineScope {
        val today = LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.BASIC_ISO_DATE)
        codes.mapNotNull { League.of(it) }
            .map { league -> async { runCatching { fetch(league, today) }.getOrDefault(emptyList()) } }
            .awaitAll()
            .flatten()
    }

    private fun fetch(league: League, date: String): List<Match> {
        val url = URL("https://site.api.espn.com/apis/site/v2/sports/soccer/${league.code}/scoreboard?dates=$date")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "LiteDroid-Launcher")
        }
        val body = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val events = JSONObject(body).optJSONArray("events") ?: return emptyList()
        return (0 until events.length()).mapNotNull { i ->
            runCatching { parse(league, events.getJSONObject(i)) }.getOrNull()
        }
    }

    private fun parse(league: League, e: JSONObject): Match? {
        val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: return null
        val competitors = comp.optJSONArray("competitors") ?: return null
        var home: TeamScore? = null
        var away: TeamScore? = null
        for (i in 0 until competitors.length()) {
            val c = competitors.getJSONObject(i)
            val t = c.optJSONObject("team") ?: continue
            val team = TeamScore(
                name = t.optString("displayName"),
                short = t.optString("shortDisplayName").ifBlank { t.optString("displayName") },
                abbr = t.optString("abbreviation").ifBlank { t.optString("displayName").take(3).uppercase() },
                logo = t.optString("logo").takeIf { it.startsWith("http") },
                score = c.optString("score").toIntOrNull(),
                id = t.optString("id").ifBlank { c.optString("id") },
            )
            if (c.optString("homeAway") == "home") home = team else away = team
        }
        val homeTeam: TeamScore = home ?: return null
        val awayTeam: TeamScore = away ?: return null

        // Gol ve kart ayrıntıları: kırmızı kart sayısı, son gol, son kırmızı kart
        var lastGoal: MatchMoment? = null
        var lastRed: MatchMoment? = null
        var homeReds = 0
        var awayReds = 0
        comp.optJSONArray("details")?.let { details ->
            for (i in 0 until details.length()) {
                val d = details.optJSONObject(i) ?: continue
                val teamId = d.optJSONObject("team")?.optString("id").orEmpty()
                val isHome = teamId == homeTeam.id
                val player = d.optJSONArray("athletesInvolved")?.optJSONObject(0)?.optString("displayName")?.takeIf { it.isNotBlank() }
                val minute = d.optJSONObject("clock")?.optString("displayValue")?.takeIf { it.isNotBlank() }
                if (d.optBoolean("scoringPlay")) {
                    val own = d.optBoolean("ownGoal")
                    lastGoal = MatchMoment(player, minute, home = isHome, ownGoal = own, penalty = d.optBoolean("penaltyKick"))
                }
                if (d.optBoolean("redCard")) {
                    if (isHome) homeReds++ else awayReds++
                    lastRed = MatchMoment(player, minute, home = isHome)
                }
            }
        }
        val homeFinal = homeTeam.copy(redCards = homeReds)
        val awayFinal = awayTeam.copy(redCards = awayReds)
        val status = e.optJSONObject("status") ?: comp.optJSONObject("status")
        val type = status?.optJSONObject("type")
        val state = when (type?.optString("state")) {
            "in" -> MatchState.LIVE
            "post" -> MatchState.DONE
            else -> MatchState.PRE
        }
        val start = runCatching { OffsetDateTime.parse(e.optString("date"), ESPN_DATE).toInstant() }
            .getOrDefault(Instant.now())
        val shortDetail = type?.optString("shortDetail").orEmpty()
        val label = when (state) {
            MatchState.LIVE -> when {
                shortDetail.equals("HT", true) || shortDetail.contains("Half", true) -> tx("İY", "HT")
                else -> status?.optString("displayClock")?.takeIf { it.isNotBlank() } ?: shortDetail
            }
            MatchState.DONE -> tx("MS", "FT")
            MatchState.PRE -> DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(start)
        }
        // Skor yoksa (başlamamış) null kalsın
        val fixedHome = if (state == MatchState.PRE) homeFinal.copy(score = null) else homeFinal
        val fixedAway = if (state == MatchState.PRE) awayFinal.copy(score = null) else awayFinal
        return Match(e.optString("id"), league, fixedHome, fixedAway, state, label, start, lastGoal, lastRed)
    }
}
