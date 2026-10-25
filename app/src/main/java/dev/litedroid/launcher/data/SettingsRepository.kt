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
    NIGHT("Gece", "Night"),
    CANVAS("Canlı Tablo", "Living Painting");

    val label: String get() = tx(tr, en)
}

/** Canlı Tablo: hangi şehirler resmedilsin. */
enum class CanvasCities(private val tr: String, private val en: String) {
    MIXED("Benim şehrim + dünya", "My city + the world"),
    HOME("Sadece benim şehrim", "Only my city"),
    WORLD("Dünya turu", "World tour");

    val label: String get() = tx(tr, en)
}

/** Canlı Tablo: resim stili (yapay zekâya tarif olarak gider). */
enum class CanvasStyle(private val tr: String, private val en: String, val prompt: String) {
    OIL("Yağlıboya", "Oil painting", "Impressionist oil painting with visible brushstrokes, rich luminous color"),
    WATERCOLOR("Suluboya", "Watercolor", "Delicate watercolor painting, soft washes and paper texture"),
    VANGOGH("Van Gogh tarzı", "Van Gogh style", "Post-impressionist painting in the style of Vincent van Gogh, swirling expressive brushstrokes"),
    UKIYOE("Japon baskısı", "Ukiyo-e", "Japanese ukiyo-e woodblock print, flat colors and elegant lines"),
    PIXEL("Piksel sanatı", "Pixel art", "Detailed 16-bit pixel art scene, cozy retro game look"),
    PHOTO("Fotoğraf gibi", "Photographic", "Breathtaking cinematic landscape photograph, golden detail, high dynamic range");

    val label: String get() = tx(tr, en)
}

/** Claude'a sor için model seçimi. */
enum class ClaudeModel(val id: String, private val tr: String, private val en: String) {
    SONNET("claude-sonnet-5-5", "Sonnet · dengeli", "Sonnet · balanced"),
    HAIKU("claude-haiku-5-5", "Haiku · en hızlı", "Haiku · fastest");

    val label: String get() = tx(tr, en)
}

/** Güncellemeler: kendiliğinden kur, sadece haber ver ya da hiç bakma. */
enum class UpdateMode(private val tr: String, private val en: String) {
    AUTO("Otomatik kur", "Install automatically"),
    NOTIFY("Sadece haber ver", "Notify only"),
    OFF("Kapalı", "Off");

    val label: String get() = tx(tr, en)
}

/** Soruları cevaplayan yapay zekâ. */
enum class AiProvider(val label: String) {
    CLAUDE("Claude"),
    GEMINI("Gemini"),
}

/** Gemini modeli: Google'ın kayan takma adları, yeni sürüm çıkınca kendiliğinden güncellenir. */
enum class GeminiModel(val id: String, private val tr: String, private val en: String) {
    FLASH("gemini-flash-latest", "Flash · dengeli", "Flash · balanced"),
    FLASH_LITE("gemini-flash-lite-latest", "Flash-Lite · en hızlı", "Flash-Lite · fastest");

    val label: String get() = tx(tr, en)
}

/** Cevapları ve masalları kim seslendirsin. */
enum class VoiceEngine(private val tr: String, private val en: String) {
    GEMINI("Gemini sesi", "Gemini voice"),
    DEVICE("Cihaz sesi", "Device voice");

    val label: String get() = tx(tr, en)
}

/** Gemini'nin hazır seslerinden seçilenler (anlatıcı ve cevaplar için). */
enum class GeminiVoice(val id: String, private val tr: String, private val en: String) {
    SULAFAT("Sulafat", "sıcak", "warm"),
    ACHERNAR("Achernar", "yumuşak", "soft"),
    VINDEMIATRIX("Vindemiatrix", "nazik", "gentle"),
    AOEDE("Aoede", "ferah", "breezy"),
    ACHIRD("Achird", "samimi", "friendly"),
    GACRUX("Gacrux", "olgun", "mature"),
    CHARON("Charon", "anlatıcı", "informative"),
    KORE("Kore", "kararlı", "firm"),
    PUCK("Puck", "neşeli", "upbeat"),
    LEDA("Leda", "genç", "youthful");

    val label: String get() = "$id · " + tx(tr, en)
}

/** Misafir Wi-Fi QR'ı için ağ güvenliği. */
enum class WifiSecurity(val qr: String, private val tr: String, private val en: String) {
    WPA("WPA", "WPA/WPA2/WPA3", "WPA/WPA2/WPA3"),
    WEP("WEP", "WEP (eski)", "WEP (old)"),
    OPEN("nopass", "Şifresiz", "Open");

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
    val canvasCities: CanvasCities = CanvasCities.MIXED,
    val canvasStyle: CanvasStyle = CanvasStyle.OIL,
    /** Canlı Tablo'da resimler kaç dakikada bir değişsin. */
    val canvasMinutes: Int = 10,
    val favorites: List<String> = emptyList(),
    val favoritesInitialized: Boolean = false,
    /** Ekran koruyucuda "Şimdi çalıyor" ve skor kartları. */
    val saverNowPlaying: Boolean = true,
    val saverScores: Boolean = true,
    /** Takip edilen ligler (ESPN kodları). Varsayılan: Türkiye, İtalya, İngiltere, İspanya, Almanya, Fransa. */
    val scoreLeagues: Set<String> = League.entries.map { it.code }.toSet(),
    /** Başka bir uygulama açıkken ekranın köşesinde yüzen skor şeridi. */
    /**
     * Lig başına takip edilen takımlar (ESPN takım kimlikleri). Haritada olmayan ligde tüm takımlar takipte;
     * boş küme o ligden hiçbir maçın gösterilmemesi demek.
     */
    val followedTeams: Map<String, Set<String>> = emptyMap(),
    val floatingScores: Boolean = false,
    val floatingOnlyLive: Boolean = true,
    /**
     * Kutunun kendi ana ekranı varsayılan kalıyorsa (bazı TV kutuları başka launcher'a izin vermez),
     * kumanda kısayolu servisi Home tuşunu ve o ana ekranı yakalayıp LiteDroid'i açar.
     */
    val homeRedirect: Boolean = true,
    /** Claude'a sor: kullanıcının kendi Anthropic API anahtarı. */
    val claudeKey: String = "",
    val claudeModel: ClaudeModel = ClaudeModel.SONNET,
    val claudeSpeak: Boolean = true,
    val claudeWebSearch: Boolean = true,
    /** Hangi yapay zekâ cevaplasın. */
    val aiProvider: AiProvider = AiProvider.CLAUDE,
    /** Gemini: kullanıcının kendi Google AI Studio API anahtarı. */
    val geminiKey: String = "",
    val geminiModel: GeminiModel = GeminiModel.FLASH,
    /** Gemini anahtarı varsa cevaplar ve masallar Gemini sesiyle okunur. */
    val voiceEngine: VoiceEngine = VoiceEngine.GEMINI,
    val geminiVoice: GeminiVoice = GeminiVoice.SULAFAT,
    val updateMode: UpdateMode = UpdateMode.AUTO,
    /** Misafir Wi-Fi: hızlı paneldeki QR için. */
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val wifiSecurity: WifiSecurity = WifiSecurity.WPA,
    /** Sabah (06-12) gün içinde yağmur/kar bekleniyorsa ana ekranda uyarı. */
    val morningWeather: Boolean = true,
    /** Bir uygulamada bu kadar dakika kesintisiz kalınca mola hatırlatması (0: kapalı). */
    val breakMinutes: Int = 0,
    /** Uygulama kilidi: bu uygulamalar açılınca PIN sorulur. */
    val lockedApps: Set<String> = emptySet(),
    val lockPin: String = "",
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
        canvasCities = enumOr(prefs.getString(K_CANVAS_CITIES, null), CanvasCities.MIXED),
        canvasStyle = enumOr(prefs.getString(K_CANVAS_STYLE, null), CanvasStyle.OIL),
        canvasMinutes = prefs.getInt(K_CANVAS_MINUTES, 10),
        favorites = prefs.getString(K_FAVORITES, "").orEmpty().split(',').filter { it.isNotBlank() },
        favoritesInitialized = prefs.getBoolean(K_FAV_INIT, false),
        saverNowPlaying = prefs.getBoolean(K_SAVER_NP, true),
        saverScores = prefs.getBoolean(K_SAVER_SCORES, true),
        scoreLeagues = run {
            val all = League.entries.map { it.code }.toSet()
            val stored = prefs.getStringSet(K_LEAGUES, null)?.toSet() ?: return@run all
            // Sonradan eklenen ligler (ör. UEFA turnuvaları) eski kurulumlarda varsayılan olarak açık gelsin
            val known = prefs.getStringSet(K_LEAGUES_KNOWN, null)?.toSet() ?: ORIGINAL_LEAGUES
            stored + (all - known)
        },
        followedTeams = run {
            val customized = prefs.getStringSet(K_TEAM_LEAGUES, null).orEmpty()
            val pairs = prefs.getStringSet(K_TEAMS, null).orEmpty().mapNotNull {
                val i = it.indexOf('|')
                if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
            }
            customized.associateWith { code -> pairs.filter { it.first == code }.map { it.second }.toSet() }
        },
        floatingScores = prefs.getBoolean(K_FLOAT, false),
        floatingOnlyLive = prefs.getBoolean(K_FLOAT_LIVE, true),
        homeRedirect = prefs.getBoolean(K_HOME_REDIRECT, true),
        claudeKey = prefs.getString(K_CLAUDE_KEY, "").orEmpty(),
        claudeModel = enumOr(prefs.getString(K_CLAUDE_MODEL, null), ClaudeModel.SONNET),
        claudeSpeak = prefs.getBoolean(K_CLAUDE_SPEAK, true),
        claudeWebSearch = prefs.getBoolean(K_CLAUDE_WEB, true),
        aiProvider = enumOr(prefs.getString(K_AI_PROVIDER, null), AiProvider.CLAUDE),
        geminiKey = prefs.getString(K_GEMINI_KEY, "").orEmpty(),
        geminiModel = enumOr(prefs.getString(K_GEMINI_MODEL, null), GeminiModel.FLASH),
        voiceEngine = enumOr(prefs.getString(K_VOICE_ENGINE, null), VoiceEngine.GEMINI),
        geminiVoice = enumOr(prefs.getString(K_GEMINI_VOICE, null), GeminiVoice.SULAFAT),
        updateMode = enumOr(prefs.getString(K_UPDATE_MODE, null), UpdateMode.AUTO),
        wifiSsid = prefs.getString(K_WIFI_SSID, "").orEmpty(),
        wifiPassword = prefs.getString(K_WIFI_PASS, "").orEmpty(),
        wifiSecurity = enumOr(prefs.getString(K_WIFI_SEC, null), WifiSecurity.WPA),
        morningWeather = prefs.getBoolean(K_MORNING_WEATHER, true),
        breakMinutes = prefs.getInt(K_BREAK_MINUTES, 0),
        lockedApps = prefs.getStringSet(K_LOCKED_APPS, emptySet()).orEmpty().toSet(),
        lockPin = prefs.getString(K_LOCK_PIN, "").orEmpty(),
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
            .putString(K_CANVAS_CITIES, s.canvasCities.name)
            .putString(K_CANVAS_STYLE, s.canvasStyle.name)
            .putInt(K_CANVAS_MINUTES, s.canvasMinutes)
            .putString(K_FAVORITES, s.favorites.joinToString(","))
            .putBoolean(K_FAV_INIT, s.favoritesInitialized)
            .putBoolean(K_SAVER_NP, s.saverNowPlaying)
            .putBoolean(K_SAVER_SCORES, s.saverScores)
            .putStringSet(K_LEAGUES, s.scoreLeagues)
            .putStringSet(K_LEAGUES_KNOWN, League.entries.map { it.code }.toSet())
            .putStringSet(K_TEAM_LEAGUES, s.followedTeams.keys)
            .putStringSet(K_TEAMS, s.followedTeams.flatMap { (code, ids) -> ids.map { "$code|$it" } }.toSet())
            .putBoolean(K_FLOAT, s.floatingScores)
            .putBoolean(K_FLOAT_LIVE, s.floatingOnlyLive)
            .putBoolean(K_HOME_REDIRECT, s.homeRedirect)
            .putString(K_CLAUDE_KEY, s.claudeKey)
            .putString(K_CLAUDE_MODEL, s.claudeModel.name)
            .putBoolean(K_CLAUDE_SPEAK, s.claudeSpeak)
            .putBoolean(K_CLAUDE_WEB, s.claudeWebSearch)
            .putString(K_AI_PROVIDER, s.aiProvider.name)
            .putString(K_GEMINI_KEY, s.geminiKey)
            .putString(K_GEMINI_MODEL, s.geminiModel.name)
            .putString(K_VOICE_ENGINE, s.voiceEngine.name)
            .putString(K_GEMINI_VOICE, s.geminiVoice.name)
            .putString(K_UPDATE_MODE, s.updateMode.name)
            .putString(K_WIFI_SSID, s.wifiSsid)
            .putString(K_WIFI_PASS, s.wifiPassword)
            .putString(K_WIFI_SEC, s.wifiSecurity.name)
            .putBoolean(K_MORNING_WEATHER, s.morningWeather)
            .putInt(K_BREAK_MINUTES, s.breakMinutes)
            .putStringSet(K_LOCKED_APPS, s.lockedApps)
            .putString(K_LOCK_PIN, s.lockPin)
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
        const val K_CANVAS_CITIES = "canvas_cities"
        const val K_CANVAS_STYLE = "canvas_style"
        const val K_CANVAS_MINUTES = "canvas_minutes"
        const val K_FAVORITES = "favorites"
        const val K_FAV_INIT = "favorites_initialized"
        const val K_SAVER_NP = "saver_now_playing"
        const val K_SAVER_SCORES = "saver_scores"
        const val K_LEAGUES = "score_leagues"
        const val K_FLOAT = "floating_scores"
        const val K_LEAGUES_KNOWN = "leagues_known"
        const val K_TEAM_LEAGUES = "team_leagues"
        const val K_TEAMS = "followed_teams"
        /** 1.5.0 öncesi ligler: bunların dışındakiler eski kurulumlara açık olarak eklenir. */
        val ORIGINAL_LEAGUES = setOf("tur.1", "eng.1", "esp.1", "ita.1", "ger.1", "fra.1")
        const val K_HOME_REDIRECT = "home_redirect"
        const val K_FLOAT_LIVE = "floating_only_live"
        const val K_CLAUDE_KEY = "claude_key"
        const val K_CLAUDE_MODEL = "claude_model"
        const val K_CLAUDE_SPEAK = "claude_speak"
        const val K_CLAUDE_WEB = "claude_web_search"
        const val K_AI_PROVIDER = "ai_provider"
        const val K_GEMINI_KEY = "gemini_key"
        const val K_GEMINI_MODEL = "gemini_model"
        const val K_VOICE_ENGINE = "voice_engine"
        const val K_WIFI_SSID = "wifi_ssid"
        const val K_WIFI_PASS = "wifi_pass"
        const val K_WIFI_SEC = "wifi_sec"
        const val K_MORNING_WEATHER = "morning_weather"
        const val K_BREAK_MINUTES = "break_minutes"
        const val K_LOCKED_APPS = "locked_apps"
        const val K_LOCK_PIN = "lock_pin"
        const val K_GEMINI_VOICE = "gemini_voice"
        const val K_UPDATE_MODE = "update_mode"
    }
}
