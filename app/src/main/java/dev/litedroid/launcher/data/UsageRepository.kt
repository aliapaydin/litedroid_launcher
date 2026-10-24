package dev.litedroid.launcher.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Process
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kullanım istatistikleri: TV'de hangi yoldan açılırsa açılsın (kumanda tuşu, sesli komut...)
 * en son kullanılan uygulamaları verir.
 *
 * İzin (bir kez):
 * adb shell appops set dev.litedroid.launcher GET_USAGE_STATS allow
 */
class UsageRepository(context: Context) {

    private val ctx = context.applicationContext

    fun hasPermission(): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Son 14 günde kullanılan paketler, en yeniden eskiye. */
    suspend fun recentPackages(): List<String> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return@withContext emptyList()
        val now = System.currentTimeMillis()
        runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 14L * 24 * 3600 * 1000, now).orEmpty()
                .filter { it.lastTimeUsed > 0 && it.totalTimeInForeground > 3_000 }
                .groupBy { it.packageName }
                .map { (pkg, stats) -> pkg to stats.maxOf { it.lastTimeUsed } }
                .sortedByDescending { it.second }
                .map { it.first }
        }.getOrDefault(emptyList())
    }

    /**
     * Son 7 günün kullanım raporu. Ön plan olaylarından (uygulama öne geldi / arkaya gitti, ekran kapandı)
     * tek tek oturumlar çıkarılır; [range] o oturumların hangi dilimde özetleneceğini seçer.
     * Ana ekranın kendisi, sistem arayüzü ve diğer launcher'lar sayılmaz.
     */
    suspend fun report(range: UsageRange): UsageReport? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return@withContext null
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekStart = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val sessions = runCatching { sessionsOf(usm, weekStart, now) }.getOrElse { return@withContext null }
            .filter { displayName(it.pkg) != null }

        val (from, to) = when (range) {
            UsageRange.TODAY -> today.atStartOfDay(zone).toInstant().toEpochMilli() to now
            UsageRange.YESTERDAY -> today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() to
                today.atStartOfDay(zone).toInstant().toEpochMilli()
            UsageRange.WEEK -> weekStart to now
        }
        val inRange = sessions.mapNotNull { s ->
            val a = maxOf(s.start, from)
            val b = minOf(s.end, to)
            if (b > a) s.copy(start = a, end = b) else null
        }

        // Saatlere göre (günün saati, seçili dilimin tüm günleri toplanır)
        val hourly = LongArray(24)
        inRange.forEach { s -> splitByHour(s.start, s.end, zone) { hour, ms -> hourly[hour] += ms } }

        // Son 7 gün (grafik her zaman bir haftayı gösterir)
        val daily = (6 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            val a = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val b = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val perApp = HashMap<String, Long>()
            sessions.forEach { s ->
                val ms = minOf(s.end, b) - maxOf(s.start, a)
                if (ms > 0) perApp[s.pkg] = (perApp[s.pkg] ?: 0L) + ms
            }
            DayUsage(day, perApp.values.sum(), perApp.maxByOrNull { it.value }?.key?.let { displayName(it) })
        }

        val apps = inRange.groupBy { it.pkg }.mapNotNull { (pkg, list) ->
            val label = displayName(pkg) ?: return@mapNotNull null
            AppUsage(
                packageName = pkg,
                label = label,
                icon = iconOf(pkg),
                totalMs = list.sumOf { it.end - it.start },
                opens = list.size,
                longestMs = list.maxOf { it.end - it.start },
                lastUsed = list.maxOf { it.end },
            )
        }.filter { it.totalMs >= 5_000 }.sortedByDescending { it.totalMs }

        val total = apps.sumOf { it.totalMs }
        val yesterdayTotal = daily.getOrNull(5)?.totalMs ?: 0L
        UsageReport(
            range = range,
            from = from,
            to = to,
            totalMs = total,
            opens = apps.sumOf { it.opens },
            apps = apps,
            hourly = hourly.toList(),
            daily = daily,
            firstUse = inRange.minOfOrNull { it.start },
            lastUse = inRange.maxOfOrNull { it.end },
            compareMs = when (range) {
                UsageRange.TODAY -> yesterdayTotal
                UsageRange.YESTERDAY -> daily.getOrNull(4)?.totalMs ?: 0L
                UsageRange.WEEK -> null
            },
        )
    }

    private data class Session(val pkg: String, val start: Long, val end: Long)

    /** Olay akışından ön plan oturumları; aynı uygulamanın 15 sn'den kısa aralıklı parçaları birleştirilir. */
    private fun sessionsOf(usm: UsageStatsManager, from: Long, to: Long): List<Session> {
        val events = usm.queryEvents(from, to) ?: return emptyList()
        val raw = ArrayList<Session>()
        var cur: String? = null
        var curStart = 0L
        fun close(t: Long) {
            val p = cur ?: return
            val end = minOf(t, curStart + MAX_SESSION_MS)
            if (end > curStart && p !in IGNORED && p != ctx.packageName) raw += Session(p, curStart, end)
            cur = null
        }
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val t = e.timeStamp
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> if (cur != e.packageName) {
                    close(t)
                    cur = e.packageName
                    curStart = t
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    if (e.packageName == cur) close(t)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> close(t)
            }
        }
        close(to)
        val merged = ArrayList<Session>()
        raw.sortedBy { it.start }.forEach { s ->
            val last = merged.lastOrNull()
            if (last != null && last.pkg == s.pkg && s.start - last.end < 15_000) {
                merged[merged.lastIndex] = last.copy(end = maxOf(last.end, s.end))
            } else merged += s
        }
        return merged
    }

    private inline fun splitByHour(start: Long, end: Long, zone: ZoneId, add: (Int, Long) -> Unit) {
        var t = start
        while (t < end) {
            val z = Instant.ofEpochMilli(t).atZone(zone)
            val next = z.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli()
            val stop = minOf(next, end)
            add(z.hour, stop - t)
            t = stop
        }
    }

    private val labels = HashMap<String, String>()
    private val icons = HashMap<String, ImageBitmap?>()

    /**
     * Rapor için görünen ad. Ana ekrandan açılmayan sistem bileşenleri (Google TV ortam modu, asistan
     * katmanı gibi) uygulama listesine erişilemediği için paket adıyla gelir: bilinenler adlandırılır,
     * diğer Google TV / Android sistem paketleri rapora alınmaz.
     */
    private fun displayName(pkg: String): String? {
        KNOWN_NAMES[pkg]?.let { return it }
        val label = labelOf(pkg)
        if (label != pkg) return label
        if (pkg.startsWith("com.google.android.apps.tv.") || pkg.startsWith("com.android.") ||
            pkg.startsWith("com.google.android.tv") || pkg.startsWith("com.google.android.gms")
        ) return null
        // Son anlamlı parçadan okunur bir ad: com.example.myplayer → Myplayer
        return pkg.split('.').lastOrNull { it.length > 2 && it !in setOf("android", "app", "tv", "atv") }
            ?.replaceFirstChar { it.uppercase() } ?: pkg
    }

    private fun labelOf(pkg: String): String = labels.getOrPut(pkg) {
        runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }
            .getOrDefault(pkg)
    }

    private fun iconOf(pkg: String): ImageBitmap? = icons.getOrPut(pkg) {
        runCatching {
            val d = ctx.packageManager.getApplicationIcon(pkg)
            val size = 96
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, size, size)
            d.draw(Canvas(bmp))
            bmp.asImageBitmap()
        }.getOrNull()
    }

    private companion object {
        /** Kapanış olayı kaçırılırsa bir oturum en fazla bu kadar sayılır. */
        const val MAX_SESSION_MS = 6L * 3600 * 1000
        val KNOWN_NAMES = mapOf(
            "com.google.android.katniss" to "Google Asistan",
            "com.google.android.youtube.tv" to "YouTube",
            "com.google.android.youtube.tvkids" to "YouTube Kids",
        )
        val IGNORED = setOf(
            "com.google.android.apps.tv.dreamx",
            "android",
            "com.android.systemui",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
            "com.google.android.tungsten.setupwraith",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
        )
    }
}

enum class UsageRange { TODAY, YESTERDAY, WEEK }

data class AppUsage(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val totalMs: Long,
    /** Kaç kez açıldı (birleştirilmiş oturum sayısı). */
    val opens: Int,
    val longestMs: Long,
    val lastUsed: Long,
)

data class DayUsage(val date: LocalDate, val totalMs: Long, val topApp: String?)

data class UsageReport(
    val range: UsageRange,
    val from: Long,
    val to: Long,
    val totalMs: Long,
    val opens: Int,
    val apps: List<AppUsage>,
    /** 24 saat dilimi, ms. */
    val hourly: List<Long>,
    /** Son 7 gün, eskiden yeniye. */
    val daily: List<DayUsage>,
    val firstUse: Long?,
    val lastUse: Long?,
    /** Karşılaştırma: Bugün için dün, Dün için evvelsi gün. */
    val compareMs: Long?,
)
