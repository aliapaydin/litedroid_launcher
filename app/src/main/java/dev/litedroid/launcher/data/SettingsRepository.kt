package dev.litedroid.launcher.data

import android.content.Context
import dev.litedroid.launcher.AppLanguage
import dev.litedroid.launcher.tx

enum class AnimSpeed(private val tr: String, private val en: String) {
    FAST("Hızlı", "Fast"),
    NORMAL("Normal", "Normal"),
    CALM("Sakin", "Calm");

    val label: String get() = tx(tr, en)
}

/** Ana ekran düzeni: Casual (cam vitrin + ızgara) ya da Modern (sekmeli, satırlı). */
enum class UiLayout(val label: String) { CASUAL("Casual"), MODERN("Modern") }

enum class SoundLevel(private val tr: String, private val en: String, val volume: Float) {
    LOW("Düşük", "Low", 0.25f),
    MEDIUM("Orta", "Medium", 0.5f),
    HIGH("Yüksek", "High", 0.9f);

    val label: String get() = tx(tr, en)
}

enum class SaverScene(private val tr: String, private val en: String) {
    MIXED("Karışık", "Mixed"),
    CINEMA("Sinema", "Cinema"),
    SPORT("Spor", "Sports"),
    GAMES("Oyun", "Games"),
    NIGHT("Gece", "Night");

    val label: String get() = tx(tr, en)
}

/** LiteDroid'in kullanıcı tarafından değiştirilebilen tüm ayarları. */
data class LauncherSettings(
    val language: AppLanguage = AppLanguage.TR,
    val layout: UiLayout = UiLayout.CASUAL,
    val weatherEnabled: Boolean = true,
    /** Boşsa konum IP'den otomatik bulunur. */
    val weatherCity: String = "",
    val columns: Int = 5,
    val animSpeed: AnimSpeed = AnimSpeed.FAST,
    /** Sürekli arka plan hareketi. Akıcılık için varsayılan kapalı. */
    val wallpaperMotion: Boolean = false,
    val userName: String = "Ali",
    val showGreeting: Boolean = true,
    val soundsEnabled: Boolean = true,
    val soundLevel: SoundLevel = SoundLevel.MEDIUM,
    /** Ekran koruyucunun devreye gireceği süre (dakika). 0 = kapalı. */
    val saverMinutes: Int = 5,
    val saverScene: SaverScene = SaverScene.MIXED,
    val favorites: List<String> = emptyList(),
    val favoritesInitialized: Boolean = false,
) {
    companion object {
        val SAVER_OPTIONS = listOf(0, 1, 2, 5, 10, 15, 30)
        const val MAX_FAVORITES = 7
    }
}

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("litedroid", Context.MODE_PRIVATE)

    fun load(): LauncherSettings = LauncherSettings(
        language = enumOr(prefs.getString(K_LANGUAGE, null), AppLanguage.TR),
        layout = enumOr(prefs.getString(K_LAYOUT, null), UiLayout.CASUAL),
        weatherEnabled = prefs.getBoolean(K_WEATHER, true),
        weatherCity = prefs.getString(K_CITY, "").orEmpty(),
        columns = prefs.getInt(K_COLUMNS, 5).coerceIn(4, 6),
        animSpeed = enumOr(prefs.getString(K_ANIM, null), AnimSpeed.FAST),
        wallpaperMotion = prefs.getBoolean(K_WP_MOTION, false),
        userName = prefs.getString(K_NAME, null)?.takeIf { it.isNotBlank() } ?: "Ali",
        showGreeting = prefs.getBoolean(K_GREETING, true),
        soundsEnabled = prefs.getBoolean(K_SOUNDS, true),
        soundLevel = enumOr(prefs.getString(K_SOUND_LEVEL, null), SoundLevel.MEDIUM),
        saverMinutes = prefs.getInt(K_SAVER_MIN, 5),
        saverScene = enumOr(prefs.getString(K_SAVER_SCENE, null), SaverScene.MIXED),
        favorites = prefs.getString(K_FAVORITES, "").orEmpty().split(',').filter { it.isNotBlank() },
        favoritesInitialized = prefs.getBoolean(K_FAV_INIT, false),
    )

    fun save(s: LauncherSettings) {
        prefs.edit()
            .putString(K_LANGUAGE, s.language.name)
            .putString(K_LAYOUT, s.layout.name)
            .putBoolean(K_WEATHER, s.weatherEnabled)
            .putString(K_CITY, s.weatherCity)
            .putInt(K_COLUMNS, s.columns)
            .putString(K_ANIM, s.animSpeed.name)
            .putBoolean(K_WP_MOTION, s.wallpaperMotion)
            .putString(K_NAME, s.userName)
            .putBoolean(K_GREETING, s.showGreeting)
            .putBoolean(K_SOUNDS, s.soundsEnabled)
            .putString(K_SOUND_LEVEL, s.soundLevel.name)
            .putInt(K_SAVER_MIN, s.saverMinutes)
            .putString(K_SAVER_SCENE, s.saverScene.name)
            .putString(K_FAVORITES, s.favorites.joinToString(","))
            .putBoolean(K_FAV_INIT, s.favoritesInitialized)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default

    private companion object {
        const val K_LANGUAGE = "language"
        const val K_LAYOUT = "ui_layout"
        const val K_WEATHER = "weather_enabled"
        const val K_CITY = "weather_city"
        const val K_COLUMNS = "columns"
        const val K_ANIM = "anim_speed"
        const val K_WP_MOTION = "wallpaper_motion_v2"
        const val K_NAME = "user_name"
        const val K_GREETING = "show_greeting"
        const val K_SOUNDS = "sounds"
        const val K_SOUND_LEVEL = "sound_level"
        const val K_SAVER_MIN = "saver_minutes"
        const val K_SAVER_SCENE = "saver_scene"
        const val K_FAVORITES = "favorites"
        const val K_FAV_INIT = "favorites_initialized"
    }
}
