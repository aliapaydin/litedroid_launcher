package dev.litedroid.launcher.wrapped

import dev.litedroid.launcher.tx
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Haftanın uygulaması: süre, açılış ve en uzun oturum. */
data class WApp(val pkg: String, val label: String, val ms: Long, val opens: Int, val longestMs: Long)

/** Haftanın bir anı (en uzun oturum, en geç kapanış…). */
data class WMoment(val pkg: String, val label: String, val at: Long, val ms: Long = 0L)

/** Takip edilen takımın haftadaki maçı ve o saatte TV'nin açık olup olmadığı. */
data class WMatch(
    val league: String,
    val home: String,
    val away: String,
    val homeLogo: String?,
    val awayLogo: String?,
    val homeScore: Int,
    val awayScore: Int,
    val start: Long,
    /** Takip edilen taraf: "home", "away" ya da "both". */
    val side: String,
    val watched: Boolean,
)

/** Haftanın kişiliği: hangi izleyici tipiydiniz. Müziğin tarzı da buna göre seçilir. */
enum class Persona(val emoji: String, private val trT: String, private val enT: String, private val trD: String, private val enD: String, val music: String) {
    NIGHT_OWL("🦉", "Gece Kuşu", "Night Owl", "Ekran gece yarısından sonra da parlıyordu. Sessiz saatler sizindi.", "The screen kept glowing past midnight. The quiet hours were yours.",
        "dreamy late-night synthwave with warm analog pads, a soft driving beat and a glowing, euphoric climax"),
    BINGER("🍿", "Maraton Ustası", "Marathon Master", "Bir oturdunuz mu kalkmadınız. Bölüm bölüm, saat saat.", "Once you sat down, you stayed. Episode after episode.",
        "cinematic orchestral pop with big drums, sweeping strings and a triumphant finale"),
    GAMER("🎮", "Oyun Canavarı", "Game Beast", "Kumanda elinizden düşmedi. Rekorlar titredi.", "The remote never left your hand. Records trembled.",
        "energetic chiptune meets modern electro, bouncy arpeggios, punchy drums, playful and victorious"),
    STORYTELLER("🌙", "Masal Sever", "Story Lover", "Masallar bu hafta evin yıldızıydı.", "Stories were the star of the house this week.",
        "whimsical music-box and celesta melody over warm strings, magical and heartwarming, gently building"),
    WEEKEND("🛋️", "Hafta Sonu Keyifçisi", "Weekend Lounger", "Hafta içi çalışmak, hafta sonu koltuk. Klasik!", "Weekdays for work, weekends for the couch. A classic!",
        "laid-back funky disco house with groovy bass, bright keys and handclaps"),
    EARLY_BIRD("☀️", "Sabahçı", "Early Bird", "Güne TV ile merhaba dediniz.", "You said good morning with the TV.",
        "bright, sunny acoustic pop with ukulele, glockenspiel, whistling melody and claps"),
    LOYAL("💛", "Sadık İzleyici", "Loyal Fan", "Bir favoriniz var ve ondan vazgeçmiyorsunuz.", "You have a favorite and you stick with it.",
        "warm feel-good indie pop with jangly guitars and an uplifting chorus melody"),
    EXPLORER("🧭", "Kâşif", "Explorer", "Bu hafta bir sürü farklı uygulamaya uğradınız.", "You dropped by lots of different apps this week.",
        "adventurous world-fusion groove with marimba, hand percussion and a soaring flute lead"),
    BALANCED("⚖️", "Dengeli İzleyici", "Balanced Viewer", "Ne az ne çok, tam kıvamında bir hafta.", "Not too little, not too much. Just right.",
        "upbeat modern lo-fi pop with crisp beats, mellow keys and a catchy hook");

    val title get() = tx(trT, enT)
    val description get() = tx(trD, enD)

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: BALANCED
    }
}

/** Bir haftanın Wrapped'i (Pazartesi-Pazar). Diskte JSON olarak saklanır, eski günler silinse de kalır. */
data class WeekData(
    val start: LocalDate,
    val profile: String,
    val name: String,
    val usageAccess: Boolean,
    val totalMs: Long,
    val litedroidMs: Long,
    val opens: Int,
    val distinctApps: Int,
    /** Pazartesiden pazara günlük süre. */
    val daily: List<Long>,
    /** 7×24 ısı haritası (gün × saat), ms. */
    val heat: List<Long>,
    val apps: List<WApp>,
    val longest: WMoment?,
    val latest: WMoment?,
    val earliest: WMoment?,
    val counters: Map<String, Int>,
    val times: Map<String, Long>,
    val records: Map<String, Int>,
    val paintings: List<String>,
    val matches: List<WMatch>,
    val persona: Persona,
    val generatedAt: Long,
    /** Önizleme (hafta bitmeden): kaydedilmez. */
    val preview: Boolean = false,
) {
    val end: LocalDate get() = start.plusDays(6)
    val activeDays: Int get() = daily.count { it > 60_000 }
    val zeroDays: Int get() = daily.count { it <= 60_000 }
    val busiestDay: Int get() = daily.indices.maxByOrNull { daily[it] } ?: 0
    val hourly: List<Long> get() = (0 until 24).map { h -> (0 until 7).sumOf { d -> heat[d * 24 + h] } }
    val busiestHour: Int get() = hourly.indices.maxByOrNull { hourly[it] } ?: 20
    val weekendMs: Long get() = daily[5] + daily[6]
    val gamesPlayed: Int get() = counters.filterKeys { it.startsWith("game.") }.values.sum()
    val isEmpty: Boolean get() = totalMs < 60_000 && counters.isEmpty() && paintings.isEmpty()

    fun toJson(): JSONObject = JSONObject()
        .put("v", 1)
        .put("start", start.toString())
        .put("profile", profile)
        .put("name", name)
        .put("usage", usageAccess)
        .put("total", totalMs)
        .put("litedroid", litedroidMs)
        .put("opens", opens)
        .put("distinct", distinctApps)
        .put("daily", JSONArray(daily))
        .put("heat", JSONArray(heat))
        .put("apps", JSONArray(apps.map { JSONObject().put("p", it.pkg).put("l", it.label).put("ms", it.ms).put("o", it.opens).put("lg", it.longestMs) }))
        .put("longest", longest?.json())
        .put("latest", latest?.json())
        .put("earliest", earliest?.json())
        .put("counters", JSONObject(counters as Map<*, *>))
        .put("times", JSONObject(times as Map<*, *>))
        .put("records", JSONObject(records as Map<*, *>))
        .put("paintings", JSONArray(paintings))
        .put("matches", JSONArray(matches.map {
            JSONObject().put("lg", it.league).put("h", it.home).put("a", it.away).put("hl", it.homeLogo).put("al", it.awayLogo)
                .put("hs", it.homeScore).put("as", it.awayScore).put("t", it.start).put("side", it.side).put("w", it.watched)
        }))
        .put("persona", persona.name)
        .put("at", generatedAt)

    companion object {
        private fun WMoment.json() = JSONObject().put("p", pkg).put("l", label).put("at", at).put("ms", ms)
        private fun moment(o: JSONObject?) = o?.let { WMoment(it.optString("p"), it.optString("l"), it.optLong("at"), it.optLong("ms")) }
        private fun longs(a: JSONArray?, n: Int) = List(n) { a?.optLong(it) ?: 0L }
        private fun ints(o: JSONObject?): Map<String, Int> = o?.keys()?.asSequence()?.associateWith { o.optInt(it) }.orEmpty()
        private fun longMap(o: JSONObject?): Map<String, Long> = o?.keys()?.asSequence()?.associateWith { o.optLong(it) }.orEmpty()

        fun from(o: JSONObject): WeekData {
            val apps = o.optJSONArray("apps")
            val ps = o.optJSONArray("paintings")
            val ms = o.optJSONArray("matches")
            return WeekData(
                start = LocalDate.parse(o.getString("start")),
                profile = o.optString("profile", "main"),
                name = o.optString("name"),
                usageAccess = o.optBoolean("usage", true),
                totalMs = o.optLong("total"),
                litedroidMs = o.optLong("litedroid"),
                opens = o.optInt("opens"),
                distinctApps = o.optInt("distinct"),
                daily = longs(o.optJSONArray("daily"), 7),
                heat = longs(o.optJSONArray("heat"), 7 * 24),
                apps = (0 until (apps?.length() ?: 0)).mapNotNull { i ->
                    apps?.optJSONObject(i)?.let { WApp(it.optString("p"), it.optString("l"), it.optLong("ms"), it.optInt("o"), it.optLong("lg")) }
                },
                longest = moment(o.optJSONObject("longest")),
                latest = moment(o.optJSONObject("latest")),
                earliest = moment(o.optJSONObject("earliest")),
                counters = ints(o.optJSONObject("counters")),
                times = longMap(o.optJSONObject("times")),
                records = ints(o.optJSONObject("records")),
                paintings = (0 until (ps?.length() ?: 0)).map { ps!!.optString(it) },
                matches = (0 until (ms?.length() ?: 0)).mapNotNull { i ->
                    ms?.optJSONObject(i)?.let {
                        WMatch(
                            it.optString("lg"), it.optString("h"), it.optString("a"),
                            it.optString("hl").takeIf { s -> s.isNotBlank() && s != "null" }, it.optString("al").takeIf { s -> s.isNotBlank() && s != "null" },
                            it.optInt("hs"), it.optInt("as"), it.optLong("t"), it.optString("side"), it.optBoolean("w"),
                        )
                    }
                },
                persona = Persona.of(o.optString("persona")),
                generatedAt = o.optLong("at"),
            )
        }
    }
}

/** Arşiv satırı için kısa bilgi. */
data class WeekRef(val start: LocalDate, val totalMs: Long, val persona: Persona, val topApp: String?)
