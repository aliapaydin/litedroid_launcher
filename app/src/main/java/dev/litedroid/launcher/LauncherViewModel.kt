package dev.litedroid.launcher

import dev.litedroid.launcher.data.UsageReport
import dev.litedroid.launcher.data.UsageRange
import dev.litedroid.launcher.overlay.RemoteKeyService
import androidx.compose.runtime.mutableStateListOf
import dev.litedroid.launcher.data.Speaker
import dev.litedroid.launcher.data.ClaudeExchange
import dev.litedroid.launcher.data.ClaudeEvent
import dev.litedroid.launcher.data.ClaudeClient
import dev.litedroid.launcher.overlay.ScoreOverlay
import dev.litedroid.launcher.data.ScoresHub
import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.litedroid.launcher.data.AppRepository
import dev.litedroid.launcher.data.ContentRepository
import dev.litedroid.launcher.data.ContentState
import dev.litedroid.launcher.data.LauncherApp
import dev.litedroid.launcher.data.LauncherSettings
import dev.litedroid.launcher.data.SettingsRepository
import dev.litedroid.launcher.data.StoreApp
import dev.litedroid.launcher.data.StoreCatalog
import dev.litedroid.launcher.data.UsageRepository
import dev.litedroid.launcher.data.Weather
import dev.litedroid.launcher.data.WeatherRepository
import dev.litedroid.launcher.dream.SystemScreensaver
import dev.litedroid.launcher.media.NotifItem
import dev.litedroid.launcher.media.NotificationsHub
import dev.litedroid.launcher.media.NowPlayingStore
import dev.litedroid.launcher.media.PlayedItem
import dev.litedroid.launcher.ui.ShelfStyle
import dev.litedroid.launcher.ui.Wallpapers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NetKind(val tr: String, val en: String) {
    WIFI("Wi-Fi", "Wi-Fi"),
    ETHERNET("Ethernet", "Ethernet"),
    OTHER("Ağ", "Network"),
    NONE("", ""),
}

/** Alt çubukta gösterilen bağlantı durumu. Metin, seçili dile göre okunurken üretilir. */
data class NetState(val connected: Boolean, val kind: NetKind) {
    val label: String
        get() = when {
            kind == NetKind.NONE -> tx("Bağlantı yok", "No connection")
            connected -> tx("${kind.tr} bağlı", "${kind.en} connected")
            else -> tx("${kind.tr} · internet yok", "${kind.en} · no internet")
        }
}

/** Mağazadan önerilen, cihazda yüklü olmayan bir uygulama ve ait olduğu bölüm. */
data class Recommendation(val app: StoreApp, val sectionIndex: Int, val sectionTitle: String)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val repo = AppRepository(application)
    private val settingsRepo = SettingsRepository(application)
    private val contentRepo = ContentRepository(application)
    private val usageRepo = UsageRepository(application)
    private val weatherRepo = WeatherRepository(application)
    private val connectivity = application.getSystemService(ConnectivityManager::class.java)
    private var weatherJob: Job? = null
    private val recommendationSeed = System.currentTimeMillis()

    private var all: List<LauncherApp> = emptyList()
    private var usageRecent: List<String> = emptyList()
    private var loadJob: Job? = null
    private var contentJob: Job? = null
    private var idleJob: Job? = null
    private var resumed = false

    /** Ana ekranda görünen uygulamalar, kullanıcının sırasıyla. */
    var apps by mutableStateOf<List<LauncherApp>>(emptyList())
        private set

    var hiddenApps by mutableStateOf<List<LauncherApp>>(emptyList())
        private set

    /** Yüklü tüm uygulamalar (gizliler dahil). */
    var installed by mutableStateOf<List<LauncherApp>>(emptyList())
        private set

    /** Üst bardaki favori uygulamalar. */
    var favorites by mutableStateOf<List<LauncherApp>>(emptyList())
        private set

    /** En son kullanılan uygulamalar (kullanım istatistikleri + LiteDroid'den açılanlar). */
    var recent by mutableStateOf<List<LauncherApp>>(emptyList())
        private set

    /** Medya oturumlarından toplanan son oynatılanlar. */
    var played by mutableStateOf<List<PlayedItem>>(emptyList())
        private set

    /** Android TV içerik deposundan okunanlar (cihaz izin verirse). */
    var content by mutableStateOf(ContentState())
        private set

    var settings by mutableStateOf(settingsRepo.load().also { I18n.lang = it.language })
        private set

    var mediaAccess by mutableStateOf(false)
        private set
    /** Ayarlar → İçerik ve gizlilik → Kullanım raporu. İzin yoksa null. */
    suspend fun usageReport(range: UsageRange): UsageReport? = usageRepo.report(range)

    var usageAccess by mutableStateOf(false)
        private set
    var systemSaverReady by mutableStateOf(false)
        private set

    var screensaverActive by mutableStateOf(false)
        private set

    /** Yüklü oyunlar (Play kategorisi "Oyun" olanlar). */
    var games by mutableStateOf<List<LauncherApp>>(emptyList())
        private set

    /** Mağazadan, cihazda olmayan önerilen uygulamalar. */
    var recommendations by mutableStateOf<List<Recommendation>>(emptyList())
        private set

    var weather by mutableStateOf(weatherRepo.cached())
        private set

    var network by mutableStateOf(NetState(connected = true, kind = NetKind.WIFI))
        private set

    var notifications by mutableStateOf<List<NotifItem>>(emptyList())
        private set

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val state = describe(caps)
            viewModelScope.launch { this@LauncherViewModel.network = state }
        }

        override fun onLost(network: Network) {
            viewModelScope.launch { this@LauncherViewModel.network = NetState(false, NetKind.NONE) }
        }
    }

    var loading by mutableStateOf(true)
        private set

    var showHidden by mutableStateOf(false)
        private set

    var themeIndex by mutableIntStateOf(repo.themeIndex.coerceIn(0, Wallpapers.all.lastIndex))
        private set

    var shelfStyle by mutableStateOf(ShelfStyle.entries.getOrElse(repo.shelfStyle) { ShelfStyle.CINEMATIC })
        private set

    /** Ana ekrandayken Home tuşuna her basışta artar; ekran başa döner. */
    var homeSignal by mutableIntStateOf(0)
        private set

    init {
        refresh()
        runCatching {
            val cm = connectivity
            if (cm != null) {
                val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
                network = if (caps != null) describe(caps) else NetState(false, NetKind.NONE)
                cm.registerDefaultNetworkCallback(networkCallback)
            }
        }
        viewModelScope.launch {
            NotificationsHub.items.collect { notifications = it }
        }
        ScoresHub.configure(settings)
        ScoreOverlay.sync(app, settings)
    }

    override fun onCleared() {
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        speaker.shutdown()
        super.onCleared()
    }

    /** Uygulamaya dönülünce (ör. izin yeni verildiyse) yüzen skor şeridini ayarlara göre aç/kapat. */
    fun syncOverlay() = ScoreOverlay.sync(app, settings)

    fun overlayAllowed(): Boolean = ScoreOverlay.canDraw(app)

    fun remoteShortcutEnabled(): Boolean = RemoteKeyService.isEnabled(app)

    /** Yüzen menüden "Claude'a sor" seçilince artar; ana ekran Claude ekranını açar. */
    var openClaudeSignal by mutableIntStateOf(0)
        private set

    fun requestOpenClaude() {
        openClaudeSignal++
    }

    /** Ayarlar başka bir yerden (yüzen menü) değiştiyse yeniden oku. */
    fun reloadSettings() {
        val fresh = settingsRepo.load()
        if (fresh != settings) settings = fresh
    }

    // ---------------- Claude'a sor ----------------

    enum class ClaudeStatus { IDLE, THINKING, SEARCHING, ANSWERING }

    data class ClaudeTurn(
        val question: String,
        val answer: String = "",
        val sources: List<ClaudeEvent.Source> = emptyList(),
        val error: String? = null,
        val authProblem: Boolean = false,
    )

    val claudeTurns = mutableStateListOf<ClaudeTurn>()
    var claudeStatus by mutableStateOf(ClaudeStatus.IDLE)
        private set
    private var claudeJob: Job? = null
    private val speaker = Speaker(app)

    private fun claudeSystemPrompt(): String {
        val now = java.time.ZonedDateTime.now()
        val date = now.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy EEEE, HH:mm", I18n.locale))
        val place = weather?.city?.takeIf { it.isNotBlank() } ?: settings.weatherCity
        return tx(
            "Sen LiteDroid Launcher'ın içindeki asistansın. Kullanıcı televizyonun karşısında, sorusunu sesle ya da " +
                "kumandayla soruyor. Cevabın TV ekranında büyük yazıyla gösterilecek ve sesli okunacak. Bu yüzden kısa, " +
                "net ve sohbet havasında cevap ver: genellikle 2-5 cümle, gerekirse en fazla birkaç kısa paragraf. " +
                "Markdown, başlık, madde işareti, tablo ya da emoji kullanma; düz cümlelerle yaz. Güncel bilgi gerektiren " +
                "sorularda (haberler, maç sonuçları, hava, fiyatlar, yayın tarihleri) web aramasını kullan. Soru hangi " +
                "dildeyse o dilde cevap ver. Şu an: $date. Kullanıcının konumu: ${place.ifBlank { "Türkiye" }}.",
            "You are the assistant inside LiteDroid Launcher. The user is sitting in front of their TV and asks by voice " +
                "or with the remote. Your answer is shown in large text on the TV and read aloud, so keep it short, clear " +
                "and conversational: usually 2-5 sentences, at most a few short paragraphs. Don't use markdown, headings, " +
                "bullet points, tables or emoji; write plain sentences. Use web search for anything current (news, match " +
                "results, weather, prices, release dates). Answer in the language of the question. Now: $date. " +
                "User's location: ${place.ifBlank { "Turkey" }}.",
        )
    }

    /** Soruyu Claude'a gönderir; cevap akış halinde gelir, bitince istenirse sesli okunur. */
    fun askClaude(question: String) {
        val q = question.trim()
        if (q.isEmpty()) return
        claudeJob?.cancel()
        speaker.stop()
        val key = settings.claudeKey.trim()
        if (key.isEmpty()) {
            claudeTurns += ClaudeTurn(
                q,
                error = tx("Önce Ayarlar → Claude'a sor → API anahtarı'nı gir.", "Add your API key first: Settings → Ask Claude → API key."),
                authProblem = true,
            )
            return
        }
        val history = claudeTurns.filter { it.error == null && it.answer.isNotBlank() }.map { ClaudeExchange(it.question, it.answer) }
        claudeTurns += ClaudeTurn(q)
        val index = claudeTurns.lastIndex
        claudeStatus = ClaudeStatus.THINKING
        claudeJob = viewModelScope.launch {
            val text = StringBuilder()
            val sources = LinkedHashMap<String, ClaudeEvent.Source>()
            try {
                ClaudeClient.ask(
                    apiKey = key,
                    model = settings.claudeModel.id,
                    system = claudeSystemPrompt(),
                    history = history,
                    question = q,
                    webSearch = settings.claudeWebSearch,
                    timezone = java.time.ZoneId.systemDefault().id,
                    city = weather?.city ?: settings.weatherCity,
                ).collect { e ->
                    when (e) {
                        is ClaudeEvent.Text -> {
                            text.append(e.delta)
                            claudeStatus = ClaudeStatus.ANSWERING
                            claudeTurns[index] = claudeTurns[index].copy(answer = text.toString())
                        }
                        is ClaudeEvent.Searching -> {
                            claudeStatus = ClaudeStatus.SEARCHING
                            // Aramadan önce yazılan "bakayım" cümlesi cevaptan çıkarılır
                            text.clear()
                            claudeTurns[index] = claudeTurns[index].copy(answer = "")
                        }
                        is ClaudeEvent.Source -> {
                            sources.putIfAbsent(e.url, e)
                            claudeTurns[index] = claudeTurns[index].copy(sources = sources.values.toList())
                        }
                        is ClaudeEvent.Failed -> {
                            claudeTurns[index] = claudeTurns[index].copy(error = e.message, authProblem = e.authProblem)
                        }
                        is ClaudeEvent.Done -> Unit
                    }
                }
                val answer = text.toString().trim()
                if (answer.isNotEmpty() && settings.claudeSpeak) speaker.speak(answer, I18n.locale)
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                claudeTurns[index] = claudeTurns[index].copy(
                    error = tx("Bağlantı kurulamadı: ", "Couldn't connect: ") + (t.message ?: t.javaClass.simpleName),
                )
            } finally {
                claudeStatus = ClaudeStatus.IDLE
            }
        }
    }

    fun stopClaude() {
        claudeJob?.cancel()
        speaker.stop()
        claudeStatus = ClaudeStatus.IDLE
    }

    fun clearClaude() {
        stopClaude()
        claudeTurns.clear()
    }

    fun speakLastAnswer() {
        claudeTurns.lastOrNull { it.answer.isNotBlank() }?.let { speaker.speak(it.answer, I18n.locale) }
    }

    private fun describe(caps: NetworkCapabilities): NetState {
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val kind = when {
            ethernet -> NetKind.ETHERNET
            wifi -> NetKind.WIFI
            else -> NetKind.OTHER
        }
        return NetState(connected = online, kind = kind)
    }

    // ---------------- Hava durumu ----------------

    private fun startWeather(force: Boolean = false) {
        if (!settings.weatherEnabled) {
            weatherJob?.cancel()
            return
        }
        if (!force && weatherJob?.isActive == true) return
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            while (true) {
                weatherRepo.fetch(settings.weatherCity)?.let { weather = it }
                delay(30 * 60_000L)
            }
        }
    }

    fun refreshWeather() = startWeather(force = true)

    // ---------------- Uygulamalar ----------------

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            all = repo.loadApps()
            installed = all
            publish()
            loading = false
        }
    }

    private fun publish() {
        val order = repo.savedOrder()
        val hidden = repo.hidden()
        val sorted = all.sortedWith(
            compareBy<LauncherApp>(
                { order.indexOf(it.packageName).let { i -> if (i < 0) Int.MAX_VALUE else i } },
                { it.label.lowercase() },
            )
        )
        apps = sorted.filterNot { it.packageName in hidden }
        hiddenApps = sorted.filter { it.packageName in hidden }
        ensureFavorites()
        publishFavorites()
        publishRecent()
        games = sorted.filter { it.isGame && it.packageName !in hidden }
        publishRecommendations()
    }

    private fun publishRecommendations() {
        val random = kotlin.random.Random(recommendationSeed)
        recommendations = StoreCatalog.sections.flatMapIndexed { index, section ->
            section.apps.map { Recommendation(it, index, section.title) }
        }.filter { StoreCatalog.findInstalled(it.app, all) == null }
            .shuffled(random)
            .take(10)
    }

    /** Uygulamayı [delta] kadar kaydırır, yeni konumunu döndürür. */
    fun move(packageName: String, delta: Int): Int {
        val list = apps.toMutableList()
        val from = list.indexOfFirst { it.packageName == packageName }
        if (from < 0) return -1
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return from
        list.add(to, list.removeAt(from))
        apps = list
        repo.saveOrder(list.map { it.packageName } + hiddenApps.map { it.packageName })
        return to
    }

    fun setHidden(packageName: String, hide: Boolean) {
        val set = repo.hidden().toMutableSet()
        if (hide) set += packageName else set -= packageName
        repo.saveHidden(set)
        publish()
    }

    fun toggleShowHidden() {
        showHidden = !showHidden
    }

    fun recordLaunch(packageName: String) {
        repo.recordLaunch(packageName)
        usageRecent = listOf(packageName) + usageRecent.filter { it != packageName }
        publishRecent()
    }

    private fun publishRecent() {
        val byPkg = all.associateBy { it.packageName }
        recent = (usageRecent + repo.recentPackages())
            .distinct()
            .filter { it != app.packageName && it !in SYSTEM_PACKAGES }
            .mapNotNull { byPkg[it] }
            .filterNot { it.isUtility }
    }

    // ---------------- Favoriler ----------------

    /** İlk çalıştırmada favorileri rastgele seçilmiş birkaç uygulamayla doldurur. */
    private fun ensureFavorites() {
        if (settings.favoritesInitialized || all.isEmpty()) return
        val pool = all.filter { it.banner != null && !it.isUtility }.ifEmpty { all.filterNot { it.isUtility } }.ifEmpty { all }
        val picks = pool.shuffled().take(5).map { it.packageName }
        updateSettings { it.copy(favorites = picks, favoritesInitialized = true) }
    }

    private fun publishFavorites() {
        val byPkg = all.associateBy { it.packageName }
        favorites = settings.favorites.mapNotNull { byPkg[it] }
    }

    fun isFavorite(packageName: String) = packageName in settings.favorites

    /** Ekler ya da çıkarır. Liste doluysa false döner. */
    fun toggleFavorite(packageName: String): Boolean {
        val list = settings.favorites.toMutableList()
        if (packageName in list) {
            list.remove(packageName)
        } else {
            if (list.size >= LauncherSettings.MAX_FAVORITES) return false
            list.add(packageName)
        }
        updateSettings { it.copy(favorites = list) }
        return true
    }

    fun moveFavorite(packageName: String, delta: Int) {
        val list = settings.favorites.toMutableList()
        val from = list.indexOf(packageName)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return
        list.add(to, list.removeAt(from))
        updateSettings { it.copy(favorites = list) }
    }

    fun shuffleFavorites() {
        updateSettings { it.copy(favorites = emptyList(), favoritesInitialized = false) }
        ensureFavorites()
        publishFavorites()
    }

    // ---------------- İçerik ----------------

    fun refreshContent() {
        contentJob?.cancel()
        contentJob = viewModelScope.launch {
            content = contentRepo.load()
            usageAccess = usageRepo.hasPermission()
            usageRecent = usageRepo.recentPackages()
            mediaAccess = NowPlayingStore.isAccessEnabled(app)
            systemSaverReady = SystemScreensaver.canConfigure(app)
            val history = withContext(Dispatchers.IO) { NowPlayingStore.load(app) }
            val installedPkgs = all.mapTo(HashSet()) { it.packageName }
            val audio = all.filter { it.isAudio }.mapTo(HashSet()) { it.packageName }
            played = history.filter {
                (installedPkgs.isEmpty() || it.packageName in installedPkgs) &&
                    it.packageName !in audio && it.packageName !in NowPlayingStore.MUSIC_PACKAGES
            }
            publishRecent()
        }
    }

    // ---------------- Çan: izlemeye devam et ----------------

    private val bellPrefs = app.getSharedPreferences("litedroid_bell", android.content.Context.MODE_PRIVATE)
    private var continueSeenAt by mutableStateOf(bellPrefs.getLong("seen_at", 0L))

    /** Çan menüsü en son açıldığından beri güncellenen "devam et" içerikleri. */
    val unseenContinue: Int
        get() = played.count { it.updatedAt > continueSeenAt }

    fun markContinueSeen() {
        continueSeenAt = System.currentTimeMillis()
        bellPrefs.edit().putLong("seen_at", continueSeenAt).apply()
    }

    fun clearHistory() {
        NowPlayingStore.clear(app)
        repo.clearRecent()
        played = emptyList()
        usageRecent = emptyList()
        publishRecent()
    }

    // ---------------- Ayarlar ----------------

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        val old = settings
        settings = transform(settings)
        settingsRepo.save(settings)
        if (old.scoreLeagues != settings.scoreLeagues || old.followedTeams != settings.followedTeams) ScoresHub.configure(settings)
        if (old.floatingScores != settings.floatingScores || old.floatingOnlyLive != settings.floatingOnlyLive) {
            ScoreOverlay.restart(app, settings)
        }
        if (!settings.claudeSpeak && old.claudeSpeak) speaker.stop()
        if (old.language != settings.language) {
            I18n.lang = settings.language
            publishRecommendations()
        }
        if (old.favorites != settings.favorites) publishFavorites()
        if (old.saverMinutes != settings.saverMinutes) restartIdleTimer()
        if (old.weatherEnabled != settings.weatherEnabled || old.weatherCity != settings.weatherCity) {
            if (settings.weatherEnabled) startWeather(force = true) else weatherJob?.cancel()
        }
    }

    fun cycleTheme() {
        themeIndex = (themeIndex + 1) % Wallpapers.all.size
        repo.themeIndex = themeIndex
    }

    fun cycleShelfStyle() {
        shelfStyle = ShelfStyle.entries[(shelfStyle.ordinal + 1) % ShelfStyle.entries.size]
        repo.shelfStyle = shelfStyle.ordinal
    }

    fun applySystemScreensaver(): Boolean = SystemScreensaver.apply(app, settings.saverMinutes)

    // ---------------- Ekran koruyucu ----------------

    fun setResumed(value: Boolean) {
        resumed = value
        if (value) {
            restartIdleTimer()
            startWeather()
        } else {
            idleJob?.cancel()
            weatherJob?.cancel()
            screensaverActive = false
        }
    }

    /** Her tuş basışında çağrılır; boşta kalma sayacını sıfırlar. */
    fun onUserInteraction() = restartIdleTimer()

    private fun restartIdleTimer() {
        idleJob?.cancel()
        val minutes = settings.saverMinutes
        if (!resumed || minutes <= 0 || screensaverActive) return
        idleJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            screensaverActive = true
        }
    }

    fun startScreensaver() {
        idleJob?.cancel()
        screensaverActive = true
    }

    fun dismissScreensaver() {
        screensaverActive = false
        restartIdleTimer()
    }

    fun onHomePressed() {
        dismissScreensaver()
        homeSignal++
    }

    private companion object {
        val SYSTEM_PACKAGES = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.android.tv.settings",
            "com.google.android.tungsten.setupwraith",
            "com.android.vending",
        )
    }
}
