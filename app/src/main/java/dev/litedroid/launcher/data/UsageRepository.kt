package dev.litedroid.launcher.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
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
}
