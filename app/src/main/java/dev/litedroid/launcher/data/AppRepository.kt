package dev.litedroid.launcher.data

import dev.litedroid.launcher.media.NowPlayingStore
import android.content.Context
import dev.litedroid.launcher.tx
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ana ekranda gösterilen tek bir uygulama. */
data class LauncherApp(
    val packageName: String,
    val label: String,
    /** TV uygulamalarının 16:9 banner'ı (yoksa null). */
    val banner: ImageBitmap?,
    val icon: ImageBitmap,
    /** Banner/ikondan çıkarılan baskın renk: gölge, vitrin ve duvar kâğıdı tonu için. */
    val accent: Color,
    val launchIntent: Intent,
    /**
     * Vitrin fonu: banner'ın 64x36'ya küçültülüp yumuşatılmış hali. Ekrana büyütülerek çizilince
     * bulanık görünür; canlı GPU bulanıklığına göre çok daha ucuzdur.
     */
    val backdrop: ImageBitmap? = null,
    /** Uygulamanın Play kategorisi (Video, Müzik, Oyun...), biliniyorsa. */
    val category: String? = null,
    val isGame: Boolean = false,
    /**
     * İkonun 40x40'a küçültülüp iki kez bulanıklaştırılmış hali. Kartın arkasına büyütülerek çizilince
     * renkli, yumuşak bir zemin olur; canlı blur'a göre çok ucuzdur.
     */
    val iconBlur: ImageBitmap? = null,
    /** Ayarlar, Play Store, dosya yöneticisi gibi araçlar: vitrinde ve ana satırlarda gösterilmez. */
    val isUtility: Boolean = false,
    /** Müzik/podcast uygulaması: "izlemeye devam et" listesine girmez. */
    val isAudio: Boolean = false,
)

class AppRepository(private val context: Context) {

    private val pm = context.packageManager
    /** Sıra, gizli uygulamalar, tema ve son açılanlar etkin profile özel. */
    private val prefs get() = appContext.getSharedPreferences(Profiles.scoped("litedroid"), Context.MODE_PRIVATE)
    private val appContext = context.applicationContext.also { Profiles.load(it) }

    /** Yüklü TV uygulamalarını (ve TV'ye yandan yüklenmiş telefon uygulamalarını) okur. */
    suspend fun loadApps(): List<LauncherApp> = withContext(Dispatchers.IO) {
        val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val phoneIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val byPackage = LinkedHashMap<String, ResolveInfo>()
        pm.queryIntentActivities(tvIntent, 0).forEach { byPackage.putIfAbsent(it.activityInfo.packageName, it) }
        pm.queryIntentActivities(phoneIntent, 0).forEach { byPackage.putIfAbsent(it.activityInfo.packageName, it) }
        byPackage.remove(context.packageName)

        byPackage.values.mapNotNull { runCatching { toApp(it) }.getOrNull() }
    }

    private fun toApp(info: ResolveInfo): LauncherApp? {
        val pkg = info.activityInfo.packageName
        val intent = (pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            ?: return null

        val bannerDrawable = info.activityInfo.loadBanner(pm)
            ?: runCatching { pm.getApplicationBanner(pkg) }.getOrNull()
        val banner = bannerDrawable?.toBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val icon = info.loadIcon(pm).toBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val label = info.loadLabel(pm).toString()

        val appInfo = info.activityInfo.applicationInfo
        val categoryId = appInfo?.category ?: ApplicationInfo.CATEGORY_UNDEFINED
        @Suppress("DEPRECATION")
        val isGame = categoryId == ApplicationInfo.CATEGORY_GAME ||
            (appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0)
        val category = if (categoryId != ApplicationInfo.CATEGORY_UNDEFINED) {
            runCatching { ApplicationInfo.getCategoryTitle(context, categoryId)?.toString() }.getOrNull()
        } else null

        return LauncherApp(
            packageName = pkg,
            label = label,
            banner = banner?.asImageBitmap(),
            icon = icon.asImageBitmap(),
            accent = averageColor(banner ?: icon),
            launchIntent = intent,
            backdrop = banner?.let { softBackdrop(it).asImageBitmap() },
            category = category ?: if (isGame) tx("Oyun", "Game") else null,
            isGame = isGame,
            iconBlur = softIcon(icon).asImageBitmap(),
            isUtility = isUtility(pkg, label, categoryId),
            isAudio = categoryId == ApplicationInfo.CATEGORY_AUDIO || pkg in NowPlayingStore.MUSIC_PACKAGES,
        )
    }

    private fun softIcon(src: Bitmap): Bitmap {
        val s = 40
        val px = IntArray(s * s)
        Bitmap.createScaledBitmap(src, s, s, true).getPixels(px, 0, s, 0, 0, s, s)
        repeat(2) { boxBlur(px, s, s, radius = 4) }
        return Bitmap.createBitmap(px, s, s, Bitmap.Config.ARGB_8888)
    }

    private fun softBackdrop(src: Bitmap): Bitmap {
        val w = 64
        val h = 36
        val px = IntArray(w * h)
        Bitmap.createScaledBitmap(src, w, h, true).getPixels(px, 0, w, 0, 0, w, h)
        repeat(2) { boxBlur(px, w, h, radius = 3) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Küçük bitmap üzerinde iki geçişli kutu bulanıklığı. */
    private fun boxBlur(px: IntArray, w: Int, h: Int, radius: Int) {
        val tmp = IntArray(px.size)
        fun pass(src: IntArray, dst: IntArray, horizontal: Boolean) {
            for (y in 0 until h) for (x in 0 until w) {
                var a = 0; var r = 0; var g = 0; var b = 0; var n = 0
                for (k in -radius..radius) {
                    val xx = if (horizontal) (x + k).coerceIn(0, w - 1) else x
                    val yy = if (horizontal) y else (y + k).coerceIn(0, h - 1)
                    val c = src[yy * w + xx]
                    a += c ushr 24; r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                }
                dst[y * w + x] = ((a / n) shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
        pass(px, tmp, horizontal = true)
        pass(tmp, px, horizontal = false)
    }

    private fun averageColor(bitmap: Bitmap): Color {
        val pixel = Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
        val color = Color(pixel).copy(alpha = 1f)
        // Çok koyu ikonlar için rengi biraz aç ki gölge/parıltı görünsün
        return if (color.luminance() < 0.05f) lerp(color, Color(0xFF3A3A48), 0.6f) else color
    }

    // --- Kalıcı ayarlar ---

    fun savedOrder(): List<String> =
        prefs.getString(KEY_ORDER, "").orEmpty().split(',').filter { it.isNotBlank() }

    fun saveOrder(packages: List<String>) {
        prefs.edit().putString(KEY_ORDER, packages.joinToString(",")).apply()
    }

    fun hidden(): Set<String> = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()

    fun saveHidden(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN, packages).apply()
    }

    var themeIndex: Int
        get() = prefs.getInt(KEY_THEME, 0)
        set(value) = prefs.edit().putInt(KEY_THEME, value).apply()

    var shelfStyle: Int
        get() = prefs.getInt(KEY_SHELF, 0)
        set(value) = prefs.edit().putInt(KEY_SHELF, value).apply()

    /** En son açılanlar başta olacak şekilde paket adları. */
    fun recentPackages(): List<String> =
        prefs.getString(KEY_RECENT, "").orEmpty().split(',').filter { it.isNotBlank() }

    fun clearRecent() {
        prefs.edit().remove(KEY_RECENT).apply()
    }

    fun recordLaunch(packageName: String) {
        val list = (listOf(packageName) + recentPackages().filter { it != packageName }).take(12)
        prefs.edit().putString(KEY_RECENT, list.joinToString(",")).apply()
    }

    companion object {
        /** Sistem araçları ve ayar uygulamaları: Tüm uygulamalar'da durur, vitrinde/ana satırlarda görünmez. */
        val UTILITY_PACKAGES = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
            "com.android.tv.settings",
            "com.android.settings",
            "com.google.android.tungsten.setupwraith",
            "com.android.vending",
            "com.google.android.katniss",
            "com.google.android.googlequicksearchbox",
            "com.google.android.apps.nbu.files",
            "com.android.documentsui",
            "com.google.android.documentsui",
            "com.google.android.play.games",
            "com.google.android.gms",
            "com.google.android.tv.remote.service",
            "com.google.android.apps.tv.dreamx",
            "com.google.android.backdrop",
            "com.google.android.feedback",
            "com.android.providers.tv",
            "com.android.camera2",
        )

        private val UTILITY_WORDS = listOf(
            "settings", "ayarlar", "play store", "google play", "file manager", "files", "dosya",
            "downloader", "send files", "setup", "kurulum",
        )

        fun isUtility(pkg: String, label: String, categoryId: Int): Boolean {
            if (pkg in UTILITY_PACKAGES) return true
            val l = label.lowercase()
            if (UTILITY_WORDS.any { l == it || l.startsWith("$it ") || l.endsWith(" $it") }) return true
            return categoryId == ApplicationInfo.CATEGORY_PRODUCTIVITY || categoryId == ApplicationInfo.CATEGORY_ACCESSIBILITY
        }

        private const val KEY_ORDER = "order"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_THEME = "theme"
        private const val KEY_SHELF = "shelf_style"
        private const val KEY_RECENT = "recent"
    }
}
