package dev.litedroid.launcher

import dev.litedroid.launcher.data.UpdateMode
import dev.litedroid.launcher.data.AppRelease
import dev.litedroid.launcher.data.UpdateState
import dev.litedroid.launcher.data.Updater
import androidx.compose.runtime.mutableFloatStateOf
import dev.litedroid.launcher.data.AiProvider
import dev.litedroid.launcher.data.GeminiClient
import dev.litedroid.launcher.data.VoiceListener
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
        voice.cancel()
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

    // ---------------- Sesli soru ----------------

    private val voice = VoiceListener(app)
    var voiceState by mutableStateOf(VoiceListener.State.IDLE)
        private set
    /** Konuşurken ekranda canlı görünen metin. */
    var voicePartial by mutableStateOf("")
        private set
    var voiceLevel by mutableFloatStateOf(0f)
        private set
    /** Uygulama içi tanıma kullanılamazsa sistemin konuşma ekranını açar (MainActivity bağlar). */
    var onVoiceFallback: (() -> Unit)? = null

    enum class VoiceStart { STARTED, NEED_PERMISSION, UNAVAILABLE }

    /** Claude ekranı açılınca: okuma motoru önceden hazırlanır, ilk cevap gecikmeden okunur. */
    fun warmUpAssistant() {
        speaker.warmUp()
    }

    /**
     * Sesli soru başlatır. Önce konuşan cevap ve süren istek hemen kesilir. Zaten dinliyorsa
     * dinlemeyi bitirir (söylenen gönderilir).
     */
    fun startVoice(): VoiceStart {
        if (voiceState != VoiceListener.State.IDLE) {
            voice.stop()
            return VoiceStart.STARTED
        }
        stopClaude()
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) return VoiceStart.NEED_PERMISSION
        if (!voice.available()) return VoiceStart.UNAVAILABLE
        voicePartial = ""
        voiceLevel = 0f
        voice.start(
            if (I18n.lang == AppLanguage.EN) "en-US" else "tr-TR",
            object : VoiceListener.Callback {
                override fun onState(state: VoiceListener.State) {
                    voiceState = state
                    if (state == VoiceListener.State.IDLE) voiceLevel = 0f
                }
                override fun onPartial(text: String) { voicePartial = text }
                override fun onLevel(level: Float) { voiceLevel = level }
                override fun onResult(text: String?) {
                    voicePartial = ""
                    if (!text.isNullOrBlank()) askClaude(text)
                }
                override fun onUnavailable() {
                    voiceState = VoiceListener.State.IDLE
                    onVoiceFallback?.invoke()
                }
            },
        )
        return VoiceStart.STARTED
    }

    fun cancelVoice() {
        voice.cancel()
        voiceState = VoiceListener.State.IDLE
        voicePartial = ""
        voiceLevel = 0f
    }

    /** Seçili yapay zekânın adı (ekran başlığı ve mesajlar için). */
    val assistantName: String get() = settings.aiProvider.label

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
        val provider = settings.aiProvider
        val key = (if (provider == AiProvider.GEMINI) settings.geminiKey else settings.claudeKey).trim()
        if (key.isEmpty()) {
            claudeTurns += ClaudeTurn(
                q,
                error = if (provider == AiProvider.GEMINI) tx("Önce Ayarlar → Claude'a sor → Gemini API anahtarı'nı gir.", "Add your Gemini API key first: Settings → Ask Claude → Gemini API key.")
                else tx("Önce Ayarlar → Claude'a sor → API anahtarı'nı gir.", "Add your API key first: Settings → Ask Claude → API key."),
                authProblem = true,
            )
            return
        }
        val speak = settings.claudeSpeak
        val history = claudeTurns.filter { it.error == null && it.answer.isNotBlank() }.map { ClaudeExchange(it.question, it.answer) }
        claudeTurns += ClaudeTurn(q)
        val index = claudeTurns.lastIndex
        claudeStatus = ClaudeStatus.THINKING
        if (speak) speaker.startStream(I18n.locale)
        claudeJob = viewModelScope.launch {
            val text = StringBuilder()
            val sources = LinkedHashMap<String, ClaudeEvent.Source>()
            try {
                val events = if (provider == AiProvider.GEMINI) {
                    GeminiClient.ask(
                        apiKey = key,
                        model = settings.geminiModel.id,
                        system = claudeSystemPrompt(),
                        history = history,
                        question = q,
                        webSearch = settings.claudeWebSearch,
                    )
                } else {
                    ClaudeClient.ask(
                        apiKey = key,
                        model = settings.claudeModel.id,
                        system = claudeSystemPrompt(),
                        history = history,
                        question = q,
                        webSearch = settings.claudeWebSearch,
                        timezone = java.time.ZoneId.systemDefault().id,
                        city = weather?.city ?: settings.weatherCity,
                    )
                }
                events.collect { e ->
                    when (e) {
                        is ClaudeEvent.Text -> {
                            text.append(e.delta)
                            claudeStatus = ClaudeStatus.ANSWERING
                            claudeTurns[index] = claudeTurns[index].copy(answer = text.toString())
                            // Cümle tamamlandıkça hemen okunur
                            if (speak) speaker.feed(e.delta)
                        }
                        is ClaudeEvent.Searching -> {
                            claudeStatus = ClaudeStatus.SEARCHING
                            // Aramadan önce yazılan "bakayım" cümlesi cevaptan çıkarılır
                            text.clear()
                            claudeTurns[index] = claudeTurns[index].copy(answer = "")
                            if (speak) speaker.startStream(I18n.locale)
                        }
                        is ClaudeEvent.Source -> {
                            sources.putIfAbsent(e.url, e)
                            claudeTurns[index] = claudeTurns[index].copy(sources = sources.values.toList())
                        }
                        is ClaudeEvent.Failed -> {
                            claudeTurns[index] = claudeTurns[index].copy(error = e.message, authProblem = e.authProblem)
                            speaker.stop()
                        }
                        is ClaudeEvent.Done -> Unit
                    }
                }
                if (speak) speaker.endStream()
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
        if (voiceState != VoiceListener.State.IDLE) cancelVoice()
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

    // ---------------- Güncellemeler ----------------

    /** Güncelleme kurulduktan sonraki ilk açılış: "şu sürüme güncellendi" ekranı bir kez gösterilir. */
    var updatedTo by mutableStateOf<String?>(null)
        private set

    fun updatedShown() {
        updatedTo = null
    }

    /** Otomatik modda yeni sürüm bulununca ekrana gelen soru ("Şimdi güncelle / 1 saat / 3 saat ertele"). */
    var updatePrompt by mutableStateOf<AppRelease?>(null)
        private set

    /** Güncelleme ilerlemesi tam ekranda mı (değilse sağ altta küçük kart). */
    var updateFullScreen by mutableStateOf(false)

    /**
     * Ayarlar ya da çan menüsünden açılan güncelleme penceresi açık mı. Açıkken otomatik soru
     * gelmez: ekranda aynı anda tek güncelleme penceresi olur, kumanda arkaya kaçmaz.
     */
    var updateDialogOpen by mutableStateOf(false)

    /** Kurma izni yok: soruda izin açıklaması ve komut gösterilir. */
    var installPermissionHint by mutableStateOf(false)

    /** Sadece haber ver modunda köşede birkaç saniye görünen bildirim. */
    var updateCornerNotice by mutableStateOf<AppRelease?>(null)
        private set

    private fun detectFreshUpdate() {
        val prefs = app.getSharedPreferences("litedroid_update", android.content.Context.MODE_PRIVATE)
        val current = Updater.currentVersion(app)
        val last = prefs.getString("last_run_version", null)
        if (last != null && last != current && Updater.isNewer(current, last)) updatedTo = current
        prefs.edit().putString("last_run_version", current).apply()
    }

    private var notifiedVersion: String? = null

    // Güncelleme durumları yukarıda tanımlandıktan sonra başlatılır (sınıf alanları sırayla
    // oluşturulur; daha önce çağrılınca "güncellendi" bilgisi henüz oluşmamış alana yazılıp çöküyordu).
    init {
        detectFreshUpdate()
        startUpdateChecks()
    }

    /**
     * Açılıştan kısa süre sonra ve yarım saatte bir GitHub'daki son sürüme bakar.
     * Otomatik: LiteDroid öndeyken sorar (ertelenmediyse). Sadece haber ver: köşede bildirim.
     * Kapalı: hiç bakmaz.
     */
    private fun startUpdateChecks() {
        viewModelScope.launch {
            delay(20_000)
            while (true) {
                if (settings.updateMode != UpdateMode.OFF) Updater.check(app)
                delay(30 * 60_000L)
            }
        }
        // Soru ve köşe bildirimi: uygun an gelince (LiteDroid öndeyken, ekran koruyucu kapalıyken)
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                val release = (Updater.state.value as? UpdateState.Available)?.release ?: continue
                if (!resumed || screensaverActive) continue
                // Başka bir güncelleme penceresi ya da ekranı açıkken üstüne ikinci pencere açılmaz
                if (updateDialogOpen || updatedTo != null || updateFullScreen) continue
                when (settings.updateMode) {
                    // Ertelendiyse süre dolunca yeniden sorulur
                    UpdateMode.AUTO -> if (updatePrompt == null && !Updater.isSnoozed(app, release.version)) {
                        updatePrompt = release
                    }
                    UpdateMode.NOTIFY -> if (notifiedVersion != release.version) {
                        notifiedVersion = release.version
                        updateCornerNotice = release
                        delay(10_000)
                        updateCornerNotice = null
                    }
                    UpdateMode.OFF -> Unit
                }
            }
        }
    }

    /** Sorudaki "Şimdi güncelle": indirme başlar, ilerleme tam ekranda görünür. */
    fun startUpdate(release: AppRelease) {
        if (!Updater.canInstall(app)) {
            // Kurma izni yok: izin ekranını açmayı dene (Google TV'de çoğunlukla var), soru açık kalsın
            installPermissionHint = true
            if (updatePrompt == null) updatePrompt = release
            runCatching {
                app.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${app.packageName}"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        installPermissionHint = false
        updatePrompt = null
        updateDialogOpen = false
        updateFullScreen = true
        viewModelScope.launch { Updater.downloadAndInstall(app, release, byUser = true) }
    }

    /** "1 saat sonra" / "3 saat sonra". */
    fun snoozeUpdate(hours: Int) {
        val r = updatePrompt ?: return
        Updater.snooze(app, r.version, hours)
        installPermissionHint = false
        updatePrompt = null
    }

    /**
     * Elle açılan soru (Ayarlar → Güncellemeleri denetle ya da çan menüsü): ertelenmiş olsa da gösterilir.
     * Otomatik modda yeni sürüm için tek pencere budur.
     */
    fun showUpdatePrompt(release: AppRelease) {
        updateDialogOpen = false
        updatePrompt = release
    }

    /** Geri ile kapatıldı: bir saat sonra yeniden sorulur. */
    fun dismissUpdatePrompt() = snoozeUpdate(1)

    /** Ayarlardan "Şimdi denetle". */
    fun checkForUpdateNow(onDone: (AppRelease?) -> Unit) {
        viewModelScope.launch { onDone(Updater.check(app, quiet = false)) }
    }

    fun installUpdate(release: AppRelease) = startUpdate(release)

    /** Kurulu sürüm (ör. "1.8.0"). */
    val appVersion: String get() = Updater.currentVersion(app)

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
