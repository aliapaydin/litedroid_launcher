package dev.litedroid.launcher.wrapped

import android.content.Context
import android.util.Log
import dev.litedroid.launcher.data.GeminiMedia
import dev.litedroid.launcher.data.ProfileTimeline
import dev.litedroid.launcher.data.Profiles
import dev.litedroid.launcher.data.ScoresHub
import dev.litedroid.launcher.data.SettingsRepository
import dev.litedroid.launcher.data.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Haftalık LiteDroid Wrapped'in arka planı.
 *
 * Android uygulama kullanım olaylarını yalnızca birkaç gün tutar; bu yüzden her biten günün oturumları
 * ve LiteDroid sayaçları gün gün diske yazılır ([tick]). Pazartesi 09:00'dan sonra biten haftanın
 * Wrapped'i bu günlük kayıtlardan hesaplanıp `wrapped/<profil>/weeks/<pazartesi>.json` olarak saklanır,
 * çan menüsünde "yeni" olarak işaretlenir. Hafta boşsa (hiç kullanım yok) Wrapped üretilmez.
 * Müzik her hafta için bir kez yapay zekâyla üretilip yanına kaydedilir.
 */
object WrappedEngine {
    private const val TAG = "LiteDroidWrapped"
    /** Her profilin kendi Wrapped'i var; arşiv ve çan etkin profile göre. */
    private fun active() = Profiles.activeId
    const val READY_HOUR = 9

    private val lock = Mutex()
    private val _unseen = MutableStateFlow<LocalDate?>(null)
    /** Henüz açılmamış en yeni Wrapped haftası (çan ve bildirim için). */
    val unseen: StateFlow<LocalDate?> = _unseen
    private var loaded = false

    private fun root(ctx: Context, pid: String = active()) = File(ctx.filesDir, "wrapped/$pid").apply { mkdirs() }
    private fun daysDir(ctx: Context, pid: String = active()) = File(root(ctx, pid), "days").apply { mkdirs() }
    private fun weeksDir(ctx: Context, pid: String = active()) = File(root(ctx, pid), "weeks").apply { mkdirs() }
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("litedroid_wrapped", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        Profiles.load(ctx)
        reload(ctx)
    }

    /** Profil değişince çandaki "yeni Wrapped" o profile göre. */
    fun reload(ctx: Context) {
        _unseen.value = prefs(ctx).getString("unseen_${active()}", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    fun markSeen(ctx: Context, week: LocalDate) {
        if (_unseen.value == week) {
            _unseen.value = null
            prefs(ctx).edit().remove("unseen_${active()}").apply()
        }
    }

    fun mondayOf(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    // ------------------------------------------------------------------
    // Arşiv
    // ------------------------------------------------------------------

    fun weeks(ctx: Context): List<WeekRef> =
        weeksDir(ctx).listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .mapNotNull { f -> runCatching { WeekData.from(JSONObject(f.readText())) }.getOrNull() }
            .sortedByDescending { it.start }
            .map { WeekRef(it.start, it.totalMs, it.persona, it.apps.firstOrNull()?.label) }

    fun week(ctx: Context, start: LocalDate, pid: String = active()): WeekData? =
        File(weeksDir(ctx, pid), "$start.json").takeIf { it.exists() }?.let { f -> runCatching { WeekData.from(JSONObject(f.readText())) }.getOrNull() }

    fun previous(ctx: Context, w: WeekData): WeekData? = week(ctx, w.start.minusDays(7), w.profile)

    // ------------------------------------------------------------------
    // Günlük kayıt ve haftalık üretim
    // ------------------------------------------------------------------

    /** Açılışta ve yarım saatte bir: biten günleri kaydeder, zamanı gelen haftanın Wrapped'ini üretir. */
    suspend fun tick(ctx: Context) = withContext(Dispatchers.IO) {
        lock.withLock {
            load(ctx)
            runCatching { snapshotDays(ctx) }.onFailure { Log.w(TAG, "snapshot failed", it) }
            runCatching { buildDueWeeks(ctx) }.onFailure { Log.w(TAG, "week build failed", it) }
            runCatching { prune(ctx) }
        }
    }

    private fun zone() = ZoneId.systemDefault()
    private fun startOf(d: LocalDate) = d.atStartOfDay(zone()).toInstant().toEpochMilli()

    private suspend fun snapshotDays(ctx: Context) {
        val today = LocalDate.now(zone())
        val usage = UsageRepository(ctx)
        val profiles = Profiles.list.value.map { it.id }
        // Android olayları ~7 gün tutar: daha eskisi kaydedilemez
        for (back in 7 downTo 1) {
            val d = today.minusDays(back.toLong())
            val missing = profiles.filter { !File(daysDir(ctx, it), "$d.json").exists() }
            if (missing.isEmpty()) continue
            val from = startOf(d)
            val to = startOf(d.plusDays(1))
            val sessions = usage.sessions(from, to)
            for (pid in missing) {
                File(daysDir(ctx, pid), "$d.json").writeText(dayJson(ctx, usage, d, sessions, pid, from, to).toString())
                WrappedStats.clear(ctx, d.toString(), pid)
            }
        }
    }

    /**
     * Bir günün bir profile ait kaydı: o profil açıkken geçen oturumlar (paket sırası + başlangıç + bitiş)
     * ve profilin LiteDroid sayaçları.
     */
    private fun dayJson(ctx: Context, usage: UsageRepository, d: LocalDate, all: List<UsageRepository.Session>?, pid: String, from: Long, to: Long): JSONObject {
        val ranges = ProfileTimeline.intervals(ctx, pid, from, to)
        val sessions = all?.flatMap { s ->
            ranges.mapNotNull { r ->
                val a = maxOf(s.start, r.first)
                val b = minOf(s.end, r.last + 1)
                if (b > a) s.copy(start = a, end = b) else null
            }
        }
        val pkgs = sessions.orEmpty().map { it.pkg }.distinct()
        val stats = WrappedStats.read(ctx, d.toString(), pid)
        return JSONObject()
            .put("date", d.toString())
            .put("usage", sessions != null)
            .put("pkgs", JSONArray(pkgs))
            .put("labels", JSONArray(pkgs.map { usage.label(it) ?: it }))
            .put("s", JSONArray().apply { sessions.orEmpty().forEach { put(JSONArray().put(pkgs.indexOf(it.pkg)).put(it.start).put(it.end)) } })
            .put("counters", JSONObject(stats.counters as Map<*, *>))
            .put("times", JSONObject(stats.times as Map<*, *>))
            .put("records", JSONObject(stats.records as Map<*, *>))
            .put("paintings", JSONArray(stats.paintings))
    }

    private suspend fun buildDueWeeks(ctx: Context) {
        for (pid in Profiles.list.value.map { it.id }) buildDueWeeks(ctx, pid)
    }

    private suspend fun buildDueWeeks(ctx: Context, pid: String) {
        val now = LocalDateTime.now(zone())
        val thisMonday = mondayOf(now.toLocalDate())
        var last = thisMonday.minusDays(7)
        if (now.isBefore(thisMonday.atTime(READY_HOUR, 0))) last = last.minusDays(7)
        val p = prefs(ctx)
        val skipped = HashSet(p.getStringSet("skipped_$pid", emptySet()).orEmpty())
        fun skip(d: LocalDate) {
            skipped += d.toString()
            p.edit().putStringSet("skipped_$pid", HashSet(skipped)).apply()
        }
        for (start in listOf(last.minusDays(7), last)) {
            if (File(weeksDir(ctx, pid), "$start.json").exists() || start.toString() in skipped) continue
            val days = (0..6).map { File(daysDir(ctx, pid), "${start.plusDays(it.toLong())}.json") }
            if (days.none { it.exists() }) {
                // Bu hafta için kayıt yok (LiteDroid henüz kurulmamıştı)
                skip(start)
                continue
            }
            val w = compute(ctx, pid, start, days.map { f -> f.takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() } }, preview = false)
            if (w.isEmpty) {
                // Bu profil o hafta hiç kullanılmadı: rapor üretilmez
                skip(start)
                continue
            }
            File(weeksDir(ctx, pid), "$start.json").writeText(w.toJson().toString())
            if (start == last) {
                if (pid == active()) _unseen.value = start
                p.edit().putString("unseen_$pid", start.toString()).apply()
            }
            Log.i(TAG, "wrapped ready for $start")
            // Müziği şimdiden hazırla: açınca beklemesin
            runCatching { ensureMusic(ctx, w) }
        }
    }

    /** Bu haftanın şimdiye kadarki hali (önizleme): kaydedilmez. */
    suspend fun previewThisWeek(ctx: Context): WeekData = withContext(Dispatchers.IO) {
        lock.withLock { runCatching { snapshotDays(ctx) } }
        val today = LocalDate.now(zone())
        val start = mondayOf(today)
        val usage = UsageRepository(ctx)
        val pid = active()
        val days = (0..6).map { i ->
            val d = start.plusDays(i.toLong())
            when {
                d.isAfter(today) -> null
                d == today -> {
                    val now = System.currentTimeMillis()
                    dayJson(ctx, usage, d, usage.sessions(startOf(d), now), pid, startOf(d), now)
                }
                else -> File(daysDir(ctx, pid), "$d.json").takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
            }
        }
        compute(ctx, pid, start, days, preview = true)
    }

    private class S(val pkg: String, val label: String, val start: Long, val end: Long) {
        val ms get() = end - start
    }

    private suspend fun compute(ctx: Context, pid: String, start: LocalDate, days: List<JSONObject?>, preview: Boolean): WeekData {
        val self = ctx.packageName
        val tz = zone()
        val all = ArrayList<S>()
        var usageAccess = false
        val counters = HashMap<String, Int>()
        val times = HashMap<String, Long>()
        val records = HashMap<String, Int>()
        val paintings = ArrayList<String>()
        days.forEach { o ->
            if (o == null) return@forEach
            if (o.optBoolean("usage")) usageAccess = true
            val pkgs = o.optJSONArray("pkgs") ?: JSONArray()
            val labels = o.optJSONArray("labels") ?: JSONArray()
            val ss = o.optJSONArray("s") ?: JSONArray()
            for (i in 0 until ss.length()) {
                val a = ss.optJSONArray(i) ?: continue
                val idx = a.optInt(0)
                all += S(pkgs.optString(idx), labels.optString(idx), a.optLong(1), a.optLong(2))
            }
            o.optJSONObject("counters")?.let { c -> c.keys().forEach { k -> counters[k] = (counters[k] ?: 0) + c.optInt(k) } }
            o.optJSONObject("times")?.let { c -> c.keys().forEach { k -> times[k] = (times[k] ?: 0L) + c.optLong(k) } }
            o.optJSONObject("records")?.let { c -> c.keys().forEach { k -> records[k] = maxOf(records[k] ?: 0, c.optInt(k)) } }
            o.optJSONArray("paintings")?.let { a -> for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() && it !in paintings }?.let { paintings += it } }
        }
        // Bugünün henüz kaydedilmemiş sayaçları (önizleme)
        if (preview) {
            val t = WrappedStats.read(ctx, LocalDate.now(tz).toString(), pid)
            t.counters.forEach { (k, v) -> counters[k] = (counters[k] ?: 0) + v }
            t.times.forEach { (k, v) -> times[k] = (times[k] ?: 0L) + v }
            t.records.forEach { (k, v) -> records[k] = maxOf(records[k] ?: 0, v) }
            t.paintings.forEach { if (it !in paintings) paintings += it }
        }

        // Kilitli uygulamalar Wrapped'te görünmez (gizli kalsınlar diye kilitlendiler)
        val settings = SettingsRepository(ctx, forProfile = pid).load()
        val apps = all.filter { it.pkg != self && it.pkg !in settings.lockedApps }
        val mine = all.filter { it.pkg == self }
        val weekStartMs = startOf(start)
        val daily = LongArray(7)
        val heat = LongArray(7 * 24)
        apps.forEach { s ->
            var t = s.start
            while (t < s.end) {
                val z = Instant.ofEpochMilli(t).atZone(tz)
                val next = z.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli()
                val stop = minOf(next, s.end)
                val day = ((z.toLocalDate().toEpochDay() - start.toEpochDay()).toInt())
                if (day in 0..6) {
                    daily[day] += stop - t
                    heat[day * 24 + z.hour] += stop - t
                }
                t = stop
            }
        }
        val byApp = apps.groupBy { it.pkg }.map { (pkg, list) ->
            WApp(pkg, list.first().label, list.sumOf { it.ms }, list.size, list.maxOf { it.ms })
        }.filter { it.ms >= 30_000 }.sortedByDescending { it.ms }
        val total = byApp.sumOf { it.ms }

        val longest = apps.maxByOrNull { it.ms }?.takeIf { it.ms >= 10 * 60_000 }?.let { WMoment(it.pkg, it.label, it.start, it.ms) }
        // En geç kapanış: 18:00'den sonra biten oturumlarda "gece ne kadar ilerlemişti" (05:00'e kadar sayılır)
        fun lateness(t: Long): Int {
            val z = Instant.ofEpochMilli(t).atZone(tz)
            val m = z.hour * 60 + z.minute
            return if (z.hour < 5) m + 24 * 60 else m
        }
        val latest = apps.filter { lateness(it.end) >= 21 * 60 }.maxByOrNull { lateness(it.end) }?.let { WMoment(it.pkg, it.label, it.end) }
        val earliest = apps.filter { Instant.ofEpochMilli(it.start).atZone(tz).hour in 5..10 }
            .minByOrNull { Instant.ofEpochMilli(it.start).atZone(tz).let { z -> z.hour * 60 + z.minute } }
            ?.let { WMoment(it.pkg, it.label, it.start) }

        // Takip edilen takımların maçları (TV o saatte açık mıydı?)
        val matches = if (preview || settings.followedTeams.values.all { it.isEmpty() }) emptyList() else {
            val list = withTimeoutOrNull(20_000) { ScoresHub.finishedBetween(settings.followedTeams, start, start.plusDays(6)) }.orEmpty()
            val ids = settings.followedTeams.values.flatten().toSet()
            list.filter { it.start.toEpochMilli() >= weekStartMs }.map { m ->
                val t0 = m.start.toEpochMilli()
                val t1 = t0 + 110 * 60_000L
                val watched = all.any { it.start < t1 && it.end > t0 + 15 * 60_000L }
                WMatch(
                    m.league.label, m.home.short, m.away.short, m.home.logo, m.away.logo,
                    m.home.score ?: 0, m.away.score ?: 0, t0,
                    when {
                        m.home.id in ids && m.away.id in ids -> "both"
                        m.home.id in ids -> "home"
                        else -> "away"
                    },
                    watched,
                )
            }
        }

        val w = WeekData(
            start = start,
            profile = pid,
            name = settings.userName,
            usageAccess = usageAccess,
            totalMs = total,
            litedroidMs = mine.sumOf { it.ms },
            opens = byApp.sumOf { it.opens },
            distinctApps = byApp.size,
            daily = daily.toList(),
            heat = heat.toList(),
            apps = byApp.take(15),
            longest = longest,
            latest = latest,
            earliest = earliest,
            counters = counters,
            times = times,
            records = records,
            paintings = paintings.takeLast(9),
            matches = matches,
            persona = Persona.BALANCED,
            generatedAt = System.currentTimeMillis(),
            preview = preview,
        )
        return w.copy(persona = personaOf(w, tz))
    }

    private fun personaOf(w: WeekData, zone: ZoneId): Persona {
        val total = w.totalMs.coerceAtLeast(1)
        val night = (0..4).sumOf { w.hourly[it] }
        val morning = (5..10).sumOf { w.hourly[it] }
        val lateNights = w.latest?.let { Instant.ofEpochMilli(it.at).atZone(zone).hour in 1..4 } == true
        val stories = (w.counters["story.new"] ?: 0) + (w.counters["story.replay"] ?: 0)
        val top = w.apps.firstOrNull()?.ms ?: 0L
        return when {
            w.totalMs > 2 * 3600_000L && (night * 100 / total >= 15 || lateNights && night * 100 / total >= 8) -> Persona.NIGHT_OWL
            (w.longest?.ms ?: 0L) >= 3 * 3600_000L -> Persona.BINGER
            w.gamesPlayed >= 10 -> Persona.GAMER
            stories >= 3 -> Persona.STORYTELLER
            w.totalMs > 3600_000L && w.weekendMs * 100 / total >= 55 -> Persona.WEEKEND
            w.totalMs > 3600_000L && morning * 100 / total >= 25 -> Persona.EARLY_BIRD
            w.totalMs > 3600_000L && top * 100 / total >= 60 -> Persona.LOYAL
            w.distinctApps >= 10 -> Persona.EXPLORER
            else -> Persona.BALANCED
        }
    }

    // ------------------------------------------------------------------
    // Müzik
    // ------------------------------------------------------------------

    fun musicFile(ctx: Context, start: LocalDate, pid: String = active()): File? =
        listOf("mp3", "wav").map { File(weeksDir(ctx, pid), "$start.$it") }.firstOrNull { it.exists() && it.length() > 10_000 }

    /**
     * Haftanın müziği: yoksa Lyria ile üretir (Gemini anahtarı gerekir). Her hafta farklı bir parça:
     * tarz haftanın kişiliğinden, hava ve tempo en çok izlenen saatten ve haftanın kendisinden gelir.
     * Anahtar yoksa ya da üretilemezse null (gösteri cihazda üretilen müzikle çalar).
     */
    suspend fun ensureMusic(ctx: Context, w: WeekData): File? = withContext(Dispatchers.IO) {
        musicFile(ctx, w.start, w.profile)?.let { return@withContext it }
        val key = SettingsRepository(ctx).load().geminiKey.trim()
        if (key.isEmpty()) return@withContext null
        val flavors = listOf(
            "with a catchy whistled hook", "with a bright piano riff", "with shimmering bells", "with a funky bassline",
            "with a soaring synth lead", "with playful pizzicato strings", "with a warm brass section", "with a dreamy guitar motif",
        )
        val flavor = flavors[(w.start.toEpochDay() / 7).toInt().mod(flavors.size)]
        val energy = when (w.busiestHour) {
            in 0..5 -> "mysterious and glowing"
            in 6..11 -> "fresh and sunny"
            in 12..17 -> "playful and bouncy"
            else -> "warm, cozy and celebratory"
        }
        val prompt = "Instrumental only, no vocals, no lyrics. A joyful recap soundtrack for a weekly TV highlights show called LiteDroid Wrapped, " +
            "made for ${w.name}'s week. Style: ${w.persona.music}, $flavor. Feeling: $energy. " +
            "About two minutes long, around 110-124 BPM, a short intro, steady build-up and a big happy finale, loop-friendly ending."
        val (ext, bytes) = GeminiMedia.music(ctx, key, prompt)
        val f = File(weeksDir(ctx, w.profile), "${w.start}.$ext")
        f.writeBytes(bytes)
        Log.i(TAG, "music ready for ${w.start} (${bytes.size / 1024} KB)")
        f
    }

    /** 3 haftadan eski günlük kayıtlar ve 12 haftadan eski müzikler silinir; haftalık özetler kalır. */
    private fun prune(ctx: Context) {
        Profiles.list.value.forEach { prune(ctx, it.id) }
    }

    private fun prune(ctx: Context, pid: String) {
        val old = LocalDate.now(zone()).minusDays(21).toString()
        daysDir(ctx, pid).listFiles().orEmpty().filter { it.name.removeSuffix(".json") < old }.forEach { it.delete() }
        val music = weeksDir(ctx, pid).listFiles { f -> f.name.endsWith(".mp3") || f.name.endsWith(".wav") }.orEmpty().sortedByDescending { it.name }
        music.drop(12).forEach { it.delete() }
    }
}
