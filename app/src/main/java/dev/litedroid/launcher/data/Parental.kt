package dev.litedroid.launcher.data

import android.content.Context
import dev.litedroid.launcher.tx
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** Çocuk profilinde Claude'a sor: kapalı ya da çocuklara uygun kısıtlı mod. */
enum class KidAi(private val tr: String, private val en: String) {
    KIDS("Çocuk modu", "Kids mode"),
    OFF("Kapalı", "Off");

    val label get() = tx(tr, en)
}

/**
 * Bir çocuk profilinin kuralları (Ebeveyn denetimi).
 * Süreler dakika; 0 = sınırsız. Gün sabah 06:00'da başlar.
 */
data class ChildRules(
    val weekdayMin: Int = 0,
    val weekendMin: Int = 0,
    /** İzin verilen saat aralığı (gece yarısından dakika). */
    val hoursOn: Boolean = false,
    val fromMin: Int = 8 * 60,
    val toMin: Int = 20 * 60 + 30,
    /** Açılamayan uygulamalar (Android ayarları ve mağaza varsayılan olarak kapalı). */
    val blocked: Set<String> = Parental.DEFAULT_BLOCKED,
    /** Uygulama başına günlük süre (dakika). */
    val appLimits: Map<String, Int> = emptyMap(),
    val ai: KidAi = KidAi.KIDS,
    /** LiteDroid tarayıcısı ve uygulama mağazası. */
    val browser: Boolean = false,
    val store: Boolean = false,
) {
    /** Gerçek bir sınır var mı (varsa yetişkinlere PIN zorunlu). */
    val hasLimits: Boolean
        get() = weekdayMin > 0 || weekendMin > 0 || hoursOn || appLimits.isNotEmpty() || blocked != Parental.DEFAULT_BLOCKED

    fun toJson(): JSONObject = JSONObject()
        .put("wd", weekdayMin).put("we", weekendMin).put("hOn", hoursOn).put("from", fromMin).put("to", toMin)
        .put("blocked", JSONArray(blocked.toList()))
        .put("limits", JSONObject(appLimits as Map<*, *>))
        .put("ai", ai.name).put("browser", browser).put("store", store)

    companion object {
        fun from(o: JSONObject?): ChildRules {
            if (o == null) return ChildRules()
            val b = o.optJSONArray("blocked")
            val l = o.optJSONObject("limits")
            return ChildRules(
                weekdayMin = o.optInt("wd"),
                weekendMin = o.optInt("we"),
                hoursOn = o.optBoolean("hOn"),
                fromMin = o.optInt("from", 8 * 60),
                toMin = o.optInt("to", 20 * 60 + 30),
                blocked = if (b == null) Parental.DEFAULT_BLOCKED else (0 until b.length()).map { b.optString(it) }.toSet(),
                appLimits = l?.keys()?.asSequence()?.associateWith { l.optInt(it) }.orEmpty(),
                ai = runCatching { KidAi.valueOf(o.optString("ai")) }.getOrDefault(KidAi.KIDS),
                browser = o.optBoolean("browser"),
                store = o.optBoolean("store"),
            )
        }
    }
}

/** Çocuk profili neden kilitli. */
enum class LockReason { TIME, MANUAL, HOURS }

/**
 * Ebeveyn denetimi: kurallar, günlük kullanım, ek süre, kilit ve süre istekleri.
 * Kullanımı kumanda kısayolu servisi sayar (ekran açık, ekran koruyucu ve profil ekranı dışında).
 * Gün sabah 06:00'da sıfırlanır; gece yarısını geçen izleme bir önceki güne yazılır.
 */
object Parental {
    const val DAY_START_HOUR = 6
    val DEFAULT_BLOCKED = setOf("com.android.tv.settings", "com.android.settings", "com.android.vending", "com.google.android.apps.tv.launcherx.settings")
    val DAILY_OPTIONS = listOf(0, 5, 10, 20, 30, 45, 60, 90, 120, 150, 180)
    val APP_LIMIT_OPTIONS = listOf(0, 15, 30, 45, 60, 90, 120)
    val ADD_OPTIONS = listOf(15, 30, 60)

    private const val RULES = "litedroid_parental"
    private const val USAGE = "litedroid_parental_usage"

    /** Ekran koruyucu ve "Kim İzliyor?" açıkken süre sayılmaz (uygulama bunları bildirir). */
    @Volatile var saverOn = false
    @Volatile var pickerOn = false

    private val _version = MutableStateFlow(0)
    /** Kural, kullanım, kilit ya da istek değişince artar (ekranlar yenilensin). */
    val version: StateFlow<Int> = _version
    private fun bump() { _version.value++ }
    /** Telefon eşleşmesi gibi dış değişikliklerde ekranları yenile. */
    fun bumpExternal() = bump()

    private fun rulesPrefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(RULES, Context.MODE_PRIVATE)
    private fun usagePrefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(USAGE, Context.MODE_PRIVATE)

    fun day(now: LocalDateTime = LocalDateTime.now()): LocalDate = now.minusHours(DAY_START_HOUR.toLong()).toLocalDate()

    // ---- Kurallar ----

    /** Kurallar her 15 sn'de ve her pencere değişiminde okunur: aynı metin tekrar ayrıştırılmaz. */
    private val rulesCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String?, ChildRules>>()

    fun rules(ctx: Context, pid: String): ChildRules {
        val raw = rulesPrefs(ctx).getString("rules_$pid", null)
        rulesCache[pid]?.let { (r, parsed) -> if (r == raw) return parsed }
        val parsed = ChildRules.from(raw?.let { runCatching { JSONObject(it) }.getOrNull() })
        rulesCache[pid] = raw to parsed
        return parsed
    }

    fun setRules(ctx: Context, pid: String, r: ChildRules) {
        rulesPrefs(ctx).edit().putString("rules_$pid", r.toJson().toString()).apply()
        bump()
    }

    /** Herhangi bir çocukta sınır varsa yetişkinlerin PIN'i zorunludur. */
    fun anyLimits(ctx: Context): Boolean = Profiles.children.any { rules(ctx, it.id).hasLimits }

    fun adultsWithoutPin(): List<Profile> = Profiles.adults.filter { !it.hasPin }

    // ---- Günlük kullanım ----

    private fun k(pid: String, d: LocalDate, what: String) = "$pid|$d|$what"

    fun usedMs(ctx: Context, pid: String, d: LocalDate = day()): Long = usagePrefs(ctx).getLong(k(pid, d, "used"), 0L)
    fun appUsedMs(ctx: Context, pid: String, pkg: String, d: LocalDate = day()): Long = usagePrefs(ctx).getLong(k(pid, d, "app:$pkg"), 0L)
    fun bonusMin(ctx: Context, pid: String, d: LocalDate = day()): Int = usagePrefs(ctx).getInt(k(pid, d, "bonus"), 0)

    /** Uygulama süreleri (bugün), en çok kullanılan başta. */
    fun appUsage(ctx: Context, pid: String, d: LocalDate = day()): List<Pair<String, Long>> {
        val prefix = "$pid|$d|app:"
        return usagePrefs(ctx).all.mapNotNull { (key, v) ->
            if (key.startsWith(prefix) && v is Long) key.removePrefix(prefix) to v else null
        }.sortedByDescending { it.second }
    }

    fun addUsed(ctx: Context, pid: String, ms: Long, pkg: String?) {
        if (ms <= 0) return
        val p = usagePrefs(ctx)
        val d = day()
        val e = p.edit().putLong(k(pid, d, "used"), p.getLong(k(pid, d, "used"), 0L) + ms)
        if (pkg != null) e.putLong(k(pid, d, "app:$pkg"), p.getLong(k(pid, d, "app:$pkg"), 0L) + ms)
        e.apply()
        bump()
        prune(ctx)
    }

    fun limitMin(ctx: Context, pid: String, d: LocalDate = day()): Int {
        val r = rules(ctx, pid)
        return if (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) r.weekendMin else r.weekdayMin
    }

    /** Bugün kalan süre (ms); sınır yoksa null. */
    fun remainingMs(ctx: Context, pid: String): Long? {
        val limit = limitMin(ctx, pid)
        if (limit <= 0) return null
        return ((limit + bonusMin(ctx, pid)) * 60_000L - usedMs(ctx, pid)).coerceAtLeast(0L)
    }

    /** Telefondan ya da TV'den ek süre: kilit de açılır. */
    fun addBonus(ctx: Context, pid: String, minutes: Int) {
        val p = usagePrefs(ctx)
        val d = day()
        val e = p.edit().putInt(k(pid, d, "bonus"), p.getInt(k(pid, d, "bonus"), 0) + minutes).remove(k(pid, d, "manual")).remove("$pid|req")
            // Ek süre verildiyse saat sınırı da bugünlük esner
            .putBoolean(k(pid, d, "hoursOk"), true)
        e.apply()
        bump()
    }

    fun lockNow(ctx: Context, pid: String) {
        usagePrefs(ctx).edit().putBoolean(k(pid, day(), "manual"), true).apply()
        bump()
    }

    fun unlock(ctx: Context, pid: String) {
        usagePrefs(ctx).edit().remove(k(pid, day(), "manual")).putBoolean(k(pid, day(), "hoursOk"), true).apply()
        bump()
    }

    fun inAllowedHours(r: ChildRules, now: LocalDateTime = LocalDateTime.now()): Boolean {
        if (!r.hoursOn) return true
        val m = now.hour * 60 + now.minute
        return if (r.fromMin <= r.toMin) m in r.fromMin until r.toMin else m >= r.fromMin || m < r.toMin
    }

    /** Profil şu an kilitli mi, neden. Yetişkin ve kuralsız çocuk için null. */
    fun lockReason(ctx: Context, pid: String): LockReason? {
        val p = Profiles.get(pid) ?: return null
        if (!p.isChild) return null
        if (usagePrefs(ctx).getBoolean(k(pid, day(), "manual"), false)) return LockReason.MANUAL
        val r = rules(ctx, pid)
        if (!inAllowedHours(r) && !usagePrefs(ctx).getBoolean(k(pid, day(), "hoursOk"), false)) return LockReason.HOURS
        val rem = remainingMs(ctx, pid)
        if (rem != null && rem <= 0L) return LockReason.TIME
        return null
    }

    fun isBlocked(ctx: Context, pid: String, pkg: String): Boolean = Profiles.get(pid)?.isChild == true && pkg in rules(ctx, pid).blocked

    /** Uygulamanın bugünkü süresi doldu mu. */
    fun appTimeUp(ctx: Context, pid: String, pkg: String): Boolean {
        val limit = rules(ctx, pid).appLimits[pkg] ?: return false
        return limit > 0 && appUsedMs(ctx, pid, pkg) >= limit * 60_000L
    }

    // ---- Süre istekleri ----

    fun request(ctx: Context, pid: String, minutes: Int = 15) {
        usagePrefs(ctx).edit().putString("$pid|req", "${System.currentTimeMillis()}|$minutes").apply()
        bump()
    }

    /** Bekleyen istek: (zaman, dakika). Bugün yapılmadıysa yok sayılır. */
    fun pendingRequest(ctx: Context, pid: String): Pair<Long, Int>? {
        val v = usagePrefs(ctx).getString("$pid|req", null) ?: return null
        val t = v.substringBefore('|').toLongOrNull() ?: return null
        if (System.currentTimeMillis() - t > 12 * 3600_000L) return null
        return t to (v.substringAfter('|').toIntOrNull() ?: 15)
    }

    fun clearRequest(ctx: Context, pid: String) {
        usagePrefs(ctx).edit().remove("$pid|req").apply()
        bump()
    }

    fun describe(reason: LockReason, r: ChildRules): String = when (reason) {
        LockReason.TIME -> tx("Bugünlük ekran süren doldu.", "Your screen time for today is up.")
        LockReason.MANUAL -> tx("Ebeveynin ekranı şimdilik kapattı.", "Your parent turned the screen off for now.")
        LockReason.HOURS -> tx("Şu an ekran saati değil (${hhmm(r.fromMin)} - ${hhmm(r.toMin)}).", "It's not screen time now (${hhmm(r.fromMin)} - ${hhmm(r.toMin)}).")
    }

    fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    private var prunedOn: LocalDate? = null

    /** 35 günden eski kullanım kayıtları silinir. */
    private fun prune(ctx: Context) {
        val today = day()
        if (prunedOn == today) return
        prunedOn = today
        val old = today.minusDays(35).toString()
        val p = usagePrefs(ctx)
        val e = p.edit()
        p.all.keys.forEach { key ->
            val parts = key.split('|')
            if (parts.size >= 3 && parts[1] < old) e.remove(key)
        }
        e.apply()
    }

    /** Silinen profilin kural ve kullanımları. */
    fun erase(ctx: Context, pid: String) {
        rulesPrefs(ctx).edit().remove("rules_$pid").apply()
        val p = usagePrefs(ctx)
        val e = p.edit()
        p.all.keys.filter { it.startsWith("$pid|") }.forEach { e.remove(it) }
        e.apply()
    }
}
