package dev.litedroid.launcher.ui

import dev.litedroid.launcher.data.Changelog
import androidx.compose.material.icons.filled.List
import dev.litedroid.launcher.overlay.RemoteKeyService
import dev.litedroid.launcher.data.ClaudeModel
import dev.litedroid.launcher.data.League
import dev.litedroid.launcher.overlay.ScoreOverlay
import dev.litedroid.launcher.games.GameScreen
import dev.litedroid.launcher.games.BuiltInGame
import dev.litedroid.launcher.LocalLanguage
import dev.litedroid.launcher.AppLanguage
import android.content.Intent
import dev.litedroid.launcher.tx
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.LauncherViewModel
import dev.litedroid.launcher.data.AnimSpeed
import dev.litedroid.launcher.data.ContentAccess
import dev.litedroid.launcher.data.LauncherApp
import dev.litedroid.launcher.data.LauncherSettings
import dev.litedroid.launcher.data.SaverScene
import dev.litedroid.launcher.data.SoundLevel
import dev.litedroid.launcher.data.StoreCatalog
import dev.litedroid.launcher.data.UiLayout
import dev.litedroid.launcher.media.NotificationsHub
import dev.litedroid.launcher.media.Resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class MenuTarget(val app: LauncherApp, val fromDock: Boolean)

private enum class TextTarget { NAME, CITY, CLAUDE_KEY, CLAUDE_QUESTION }

private const val CMD_MEDIA =
    "adb shell cmd notification allow_listener dev.litedroid.launcher/dev.litedroid.launcher.media.MediaListenerService"
private const val CMD_KEY = "adb shell am start -n dev.litedroid.launcher/.MainActivity --es claude_key ANAHTAR"
private const val CMD_OVERLAY = "adb shell appops set dev.litedroid.launcher SYSTEM_ALERT_WINDOW allow"
private const val CMD_USAGE = "adb shell appops set dev.litedroid.launcher GET_USAGE_STATS allow"
private const val CMD_SAVER =
    "adb shell pm grant dev.litedroid.launcher android.permission.WRITE_SECURE_SETTINGS\n" +
        "adb shell appops set dev.litedroid.launcher WRITE_SETTINGS allow"

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val settings = vm.settings
    val motion = remember(settings.animSpeed) { motionFor(settings.animSpeed) }
    val sounds = LocalSounds.current
    LaunchedEffect(settings.soundsEnabled, settings.soundLevel, sounds) {
        sounds?.enabled = settings.soundsEnabled
        sounds?.volume = settings.soundLevel.volume
    }
    // Dil değişince tüm ağaç yeniden çizilsin diye dil de bir CompositionLocal
    CompositionLocalProvider(LocalMotion provides motion, LocalLanguage provides settings.language) {
        HomeContent(vm)
    }
}

@Composable
private fun HomeContent(vm: LauncherViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val motion = LocalMotion.current
    val sounds = LocalSounds.current
    val settings = vm.settings
    val columns = settings.columns
    val modern = settings.layout == UiLayout.MODERN

    val visibleApps = vm.apps
    val hiddenApps = vm.hiddenApps
    val showHidden = vm.showHidden
    val gridApps = remember(visibleApps, hiddenApps, showHidden) {
        if (showHidden) visibleApps + hiddenApps else visibleApps
    }
    val hiddenSet = remember(hiddenApps) { hiddenApps.mapTo(HashSet()) { it.packageName } }

    val requesters = remember { HashMap<String, FocusRequester>() }
    fun requesterFor(pkg: String) = requesters.getOrPut(pkg) { FocusRequester() }

    var focusedPkg by remember { mutableStateOf<String?>(null) }
    var barFocus by remember { mutableStateOf<String?>(null) }
    var dockApp by remember { mutableStateOf<LauncherApp?>(null) }
    var stripFocusedKey by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<MenuTarget?>(null) }
    var movingPkg by remember { mutableStateOf<String?>(null) }
    var panelOpen by remember { mutableStateOf(false) }
    var notifOpen by remember { mutableStateOf(false) }
    var storeOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var changelogOpen by remember { mutableStateOf(false) }
    var usageOpen by remember { mutableStateOf(false) }
    var settingsRefocus by remember { mutableIntStateOf(0) }
    var settingsOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var gameOpen by remember { mutableStateOf<BuiltInGame?>(null) }
    var askOpen by remember { mutableStateOf(false) }
    // Kapanış animasyonu sırasında son oyun çizilmeye devam etsin diye ayrı tutulur
    var lastGame by remember { mutableStateOf(BuiltInGame.SNAKE) }
    var gamePickerOpen by remember { mutableStateOf(false) }
    var textTarget by remember { mutableStateOf<TextTarget?>(null) }
    var restoreSignal by remember { mutableIntStateOf(0) }

    // Tam ekran sayfalar açıkken ana ekran odak alamaz
    val pageOpen = storeOpen || aboutOpen || changelogOpen || usageOpen || settingsOpen || searchOpen || gameOpen != null || askOpen || panelOpen
    val overlayOpen = menuFor != null || panelOpen || notifOpen || gamePickerOpen || pageOpen
    val motionPaused = rememberUpdatedState(overlayOpen || vm.screensaverActive || !settings.wallpaperMotion)

    val focusedIndex = gridApps.indexOfFirst { it.packageName == focusedPkg }
    val focusedApp = gridApps.getOrNull(focusedIndex)
    val heroExpanded = barFocus != null || stripFocusedKey != null || focusedIndex < columns

    // Hızlı gezinirken vitrin her karoda değişmesin diye kısa gecikme
    var shelfApp by remember { mutableStateOf<LauncherApp?>(null) }
    LaunchedEffect(focusedApp) {
        if (focusedApp != null) {
            delay(motion.shelfDelayMs)
            shelfApp = focusedApp
        }
    }

    val strip = remember(shelfApp, vm.played, vm.content, vm.recent, vm.installed) {
        buildStrip(shelfApp, vm.played, vm.content, vm.recent, vm.installed)
    }
    val stripFocusedItem = strip?.items?.firstOrNull { it.key == stripFocusedKey }
    val barApp = if (barFocus?.startsWith("fav_") == true) dockApp else null
    val shelfFocus = remember(stripFocusedItem, barApp, shelfApp) {
        stripFocusedItem?.let { shelfFocusFor(it) }
            ?: barApp?.let { shelfFocusFor(it) }
            ?: shelfApp?.let { shelfFocusFor(it) }
    }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    fun focusTile(pkg: String?) {
        val target = gridApps.firstOrNull { it.packageName == pkg } ?: gridApps.firstOrNull() ?: return
        runCatching { requesterFor(target.packageName).requestFocus() }
    }

    fun startSafely(intent: Intent, failMessage: String = tx("Bu işlem bu cihazda desteklenmiyor", "This is not supported on this device")): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        toast(failMessage)
        false
    }

    /** Sırayla dener: ilk açılabilen intent başlatılır. */
    fun startFirstWorking(intents: List<Intent>, failMessage: String?): Boolean {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
            }
        }
        failMessage?.let { toast(it) }
        return false
    }

    fun launchApp(app: LauncherApp) {
        vm.recordLaunch(app.packageName)
        startSafely(app.launchIntent, tx("${app.label} açılamadı", "Couldn't open ${app.label}"))
    }

    fun launchStripItem(item: StripItem) {
        when (item) {
            is StripItem.Recent -> launchApp(item.app)
            is StripItem.Played -> {
                // Önce içeriğin kendisini aç (oynatıcıya dön / derin bağlantı), olmazsa uygulamayı
                if (Resume.resume(context, item.item)) vm.recordLaunch(item.item.packageName)
                else item.app?.let { app ->
                    // Uygulamayı sıfırdan aç: arkada kalmış bir oynatıcıya (başka bir videoya) dönmesin
                    vm.recordLaunch(app.packageName)
                    startSafely(
                        Intent(app.launchIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        tx("${app.label} açılamadı", "Couldn't open ${app.label}"),
                    )
                }
            }
            is StripItem.Content -> {
                val intent = item.item.intentUri?.let {
                    runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull()
                }
                if (intent != null) {
                    vm.recordLaunch(item.item.packageName)
                    startSafely(intent, tx("İçerik açılamadı", "Couldn't open content"))
                } else {
                    item.app?.let { launchApp(it) }
                }
            }
        }
    }

    fun openPlayStore() {
        val intent = StoreCatalog.playStoreIntent(context.packageManager)
        if (intent != null) startSafely(intent, tx("Google Play açılamadı", "Couldn't open Google Play")) else toast(tx("Google Play bu cihazda yok", "Google Play is not on this device"))
    }

    fun findGames() {
        startFirstWorking(
            listOf(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=oyun&c=apps")).setPackage(StoreCatalog.PLAY_PACKAGE),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=oyun&c=apps")),
            ),
            tx("Google Play açılamadı", "Couldn't open Google Play"),
        )
    }

    // İlk açılışta (Casual) ilk karoya odaklan
    LaunchedEffect(gridApps.isEmpty(), modern) {
        if (!modern && gridApps.isNotEmpty() && focusedPkg == null) {
            delay(80)
            focusTile(null)
        }
    }

    // Katman açılınca "vuuş", kapanınca odağı son kalınan yere geri ver
    var overlayWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(overlayOpen) {
        if (overlayOpen && !overlayWasOpen) sounds?.play(Sfx.OPEN)
        if (!overlayOpen && overlayWasOpen) {
            delay(40)
            if (modern) restoreSignal++ else focusTile(focusedPkg)
        }
        overlayWasOpen = overlayOpen
    }

    // Arayüz değişince odağı yeni düzene taşı
    LaunchedEffect(modern) {
        delay(120)
        if (modern) restoreSignal++ else focusTile(focusedPkg)
    }

    // Ana ekrandayken Home'a basılınca başa dön
    LaunchedEffect(vm.homeSignal) {
        if (vm.homeSignal == 0) return@LaunchedEffect
        menuFor = null
        panelOpen = false
        notifOpen = false
        storeOpen = false
        aboutOpen = false
        changelogOpen = false
        usageOpen = false
        settingsOpen = false
        searchOpen = false
        gameOpen = null
        gamePickerOpen = false
        if (askOpen) vm.stopClaude()
        askOpen = false
        textTarget = null
        movingPkg = null
        stripFocusedKey = null
        barFocus = null
        if (!modern) {
            focusedPkg = gridApps.firstOrNull()?.packageName
            runCatching { gridState.animateScrollToItem(0) }
            focusTile(null)
        }
    }

    // Yüzen menüden "Claude'a sor" seçildi
    LaunchedEffect(vm.openClaudeSignal) {
        if (vm.openClaudeSignal == 0) return@LaunchedEffect
        menuFor = null
        panelOpen = false
        notifOpen = false
        storeOpen = false
        aboutOpen = false
        changelogOpen = false
        usageOpen = false
        settingsOpen = false
        searchOpen = false
        gameOpen = null
        askOpen = true
    }

    // Launcher'da Geri tuşu uygulamadan çıkmaz: önce katmanları kapatır, sonra başa döner
    BackHandler {
        sounds?.play(Sfx.BACK)
        when {
            textTarget != null -> textTarget = null
            gameOpen != null -> gameOpen = null
            askOpen -> {
                vm.stopClaude()
                askOpen = false
            }
            gamePickerOpen -> gamePickerOpen = false
            movingPkg != null -> movingPkg = null
            aboutOpen -> aboutOpen = false
            usageOpen -> {
                usageOpen = false
                settingsOpen = true
            }
            changelogOpen -> {
                changelogOpen = false
                settingsRefocus++
            }
            settingsOpen -> settingsOpen = false
            searchOpen -> searchOpen = false
            storeOpen -> storeOpen = false
            menuFor != null -> menuFor = null
            notifOpen -> notifOpen = false
            panelOpen -> panelOpen = false
            modern -> vm.onHomePressed()
            else -> scope.launch {
                gridState.animateScrollToItem(0)
                focusTile(null)
            }
        }
    }

    // Animasyon değerleri State olarak tutulur ve çizim/yerleşim aşamasında okunur
    val dimState = animateFloatAsState(
        targetValue = when {
            usageOpen -> 0.96f
            storeOpen || settingsOpen || searchOpen -> 0.78f
            overlayOpen -> 0.55f
            else -> 0f
        },
        animationSpec = tween(motion.ms(300)),
        label = "dim",
    )
    val heroState = animateFloatAsState(
        targetValue = if (heroExpanded) 1f else 0f,
        animationSpec = tween(motion.ms(450), easing = FastOutSlowInEasing),
        label = "hero",
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroMax = maxHeight * 0.46f

        // ---------- Arka katman: Casual ya da Modern (menü açılınca bir kez bulanıklaşır) ----------
        Box(Modifier.fillMaxSize()) {
            if (modern) {
                ModernHome(
                    vm = vm,
                    enabled = !pageOpen,
                    paused = { motionPaused.value },
                    restoreSignal = restoreSignal,
                    onLaunchApp = { launchApp(it) },
                    onAppMenu = { menuFor = MenuTarget(it, fromDock = false) },
                    onLaunchStrip = { launchStripItem(it) },
                    onGetFromPlay = { startFirstWorking(StoreCatalog.playIntents(it), tx("Google Play açılamadı", "Couldn't open Google Play")) },
                    onOpenPlayStore = { openPlayStore() },
                    onFindGames = { findGames() },
                    onPlayGame = {
                        lastGame = it
                        gameOpen = it
                    },
                    onSearch = { searchOpen = true },
                    onAskClaude = { askOpen = true },
                    onNotifications = { notifOpen = true },
                    onQuickSettings = { panelOpen = true },
                    onProfile = { settingsOpen = true },
                )
            } else {
                AnimatedWallpaper(
                    wallpaper = Wallpapers.all[vm.themeIndex],
                    accent = shelfFocus?.app?.accent,
                    paused = { motionPaused.value },
                )
                TopShelf(
                    style = vm.shelfStyle,
                    focus = shelfFocus,
                    strip = strip,
                    heroHeight = heroMax,
                    fraction = { heroState.value },
                    paused = { motionPaused.value },
                    stripEnabled = !pageOpen,
                    userName = settings.userName,
                    onStripFocus = { item ->
                        stripFocusedKey = item.key
                        barFocus = null
                    },
                    onStripSelect = { launchStripItem(it) },
                )

                Column(Modifier.fillMaxSize()) {
                    TopBar(
                        userName = settings.userName,
                        showGreeting = settings.showGreeting,
                        favorites = vm.favorites,
                        enabled = !pageOpen,
                        onProfile = { settingsOpen = true },
                        onSettings = { panelOpen = true },
                        onBarFocus = { key, app ->
                            barFocus = key
                            dockApp = app
                            stripFocusedKey = null
                        },
                        onFavoriteSelect = { launchApp(it) },
                        onFavoriteLongPress = { menuFor = MenuTarget(it, fromDock = true) },
                        onAskClaude = { askOpen = true },
                    )
                    // Vitrin yüksekliği yerleşim aşamasında okunur (yeniden oluşturma yok)
                    Spacer(
                        Modifier.layout { measurable, constraints ->
                            val h = (heroMax.toPx() * heroState.value).roundToInt()
                            val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, h))
                            layout(placeable.width, h) { placeable.place(0, 0) }
                        }
                    )

                    if (gridApps.isEmpty()) {
                        Text(
                            text = if (vm.loading) tx("Uygulamalar yükleniyor…", "Loading apps…") else tx("Gösterilecek uygulama yok", "No apps to show"),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 64.dp, vertical = 24.dp),
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            state = gridState,
                            contentPadding = PaddingValues(start = 64.dp, end = 64.dp, top = 28.dp, bottom = 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(if (columns >= 6) 26.dp else 34.dp),
                            verticalArrangement = Arrangement.spacedBy(26.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(gridApps, key = { _, app -> app.packageName }) { index, app ->
                                AppTile(
                                    app = app,
                                    dimmed = app.packageName in hiddenSet,
                                    moving = movingPkg == app.packageName,
                                    focusRequester = requesterFor(app.packageName),
                                    columns = columns,
                                    index = index,
                                    total = gridApps.size,
                                    onFocused = {
                                        focusedPkg = app.packageName
                                        barFocus = null
                                        stripFocusedKey = null
                                    },
                                    onSelect = { launchApp(app) },
                                    onLongPress = { menuFor = MenuTarget(app, fromDock = false) },
                                    onMove = { delta ->
                                        val newIndex = vm.move(app.packageName, delta)
                                        if (newIndex >= 0) scope.launch {
                                            val visible = gridState.layoutInfo.visibleItemsInfo
                                            if (visible.none { it.index == newIndex }) {
                                                gridState.animateScrollToItem((newIndex - columns).coerceAtLeast(0))
                                            }
                                        }
                                    },
                                    onMoveDone = { movingPkg = null },
                                    modifier = Modifier.animateItem(),
                                    enabled = !pageOpen,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------- Ön katman: karartma + cam menüler ----------
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = dimState.value
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .background(Color(0xFF05060C))
        )

        menuFor?.let { target ->
            val app = target.app
            val isHidden = app.packageName in hiddenSet
            val isFavorite = vm.isFavorite(app.packageName)
            key(app.packageName, target.fromDock) {
                GlassMenu(
                    title = app.label,
                    subtitle = if (target.fromDock) tx("Favori uygulama", "Favorite app") else (app.category ?: app.packageName),
                    header = {
                        AppArt(
                            app,
                            Modifier
                                .width(120.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    },
                    items = buildList {
                        add(MenuItem(tx("Aç", "Open"), Icons.Filled.PlayArrow) {
                            menuFor = null
                            launchApp(app)
                        })
                        if (target.fromDock) {
                            add(MenuItem(tx("Sola taşı", "Move left"), Icons.AutoMirrored.Filled.KeyboardArrowLeft) {
                                vm.moveFavorite(app.packageName, -1)
                            })
                            add(MenuItem(tx("Sağa taşı", "Move right"), Icons.AutoMirrored.Filled.KeyboardArrowRight) {
                                vm.moveFavorite(app.packageName, 1)
                            })
                            add(MenuItem(tx("Favorilerden çıkar", "Remove from favorites"), Icons.Filled.FavoriteBorder) {
                                vm.toggleFavorite(app.packageName)
                                menuFor = null
                            })
                        } else {
                            add(
                                MenuItem(
                                    if (isFavorite) tx("Favorilerden çıkar", "Remove from favorites") else tx("Favorilere ekle", "Add to favorites"),
                                    if (isFavorite) Icons.Filled.FavoriteBorder else Icons.Filled.Favorite,
                                ) {
                                    if (!vm.toggleFavorite(app.packageName)) {
                                        toast(tx("En fazla ${LauncherSettings.MAX_FAVORITES} favori eklenebilir", "You can add up to ${LauncherSettings.MAX_FAVORITES} favorites"))
                                    }
                                    menuFor = null
                                }
                            )
                            if (!isHidden) {
                                if (!modern) {
                                    add(MenuItem(tx("Taşı", "Move"), Icons.Filled.Edit) {
                                        movingPkg = app.packageName
                                        menuFor = null
                                    })
                                }
                                add(MenuItem(tx("Gizle", "Hide"), Icons.Filled.Clear) {
                                    vm.setHidden(app.packageName, true)
                                    menuFor = null
                                })
                            } else {
                                add(MenuItem(tx("Ana ekranda göster", "Show on home screen"), Icons.Filled.Done) {
                                    vm.setHidden(app.packageName, false)
                                    menuFor = null
                                })
                            }
                        }
                        add(MenuItem(tx("Uygulama bilgisi", "App info"), Icons.Filled.Info) {
                            menuFor = null
                            startSafely(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                            )
                        })
                        if (!target.fromDock) {
                            add(MenuItem(tx("Kaldır", "Uninstall"), Icons.Filled.Delete, destructive = true) {
                                menuFor = null
                                startSafely(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
                            })
                        }
                    },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        // Çan menüsü: şimdilik yalnızca "izlemeye devam et" (yarım kalan videolar)
        if (notifOpen) {
            LaunchedEffect(Unit) { vm.markContinueSeen() }
            val byPkg = remember(vm.installed) { vm.installed.associateBy { it.packageName } }
            val items = vm.played.take(7)
            GlassMenu(
                title = tx("İzlemeye devam et", "Continue watching"),
                subtitle = when {
                    !vm.mediaAccess -> tx("Medya geçmişi izni kapalı", "Media history access is off")
                    items.isEmpty() -> tx("Yarım kalan video yok", "Nothing to continue")
                    else -> tx("${items.size} yarım kalan video", "${items.size} unfinished videos")
                },
                items = when {
                    !vm.mediaAccess -> listOf(
                        MenuItem(tx("İzni aç (Ayarlar → Medya geçmişi)", "Enable access (Settings → Media history)"), Icons.Filled.Lock) {
                            notifOpen = false
                            settingsOpen = true
                        }
                    )
                    items.isEmpty() -> listOf(MenuItem(tx("Kapat", "Close"), Icons.Filled.Done) { notifOpen = false })
                    else -> items.map { p ->
                        val app = byPkg[p.packageName]
                        val left = if (p.duration > 0 && p.position > 0) {
                            val m = ((p.duration - p.position) / 60_000).coerceAtLeast(1)
                            tx("$m dk kaldı", "$m min left")
                        } else null
                        val source = listOfNotNull(app?.label, left).joinToString(" · ")
                        val label = when {
                            p.anonymous -> "${app?.label ?: p.title}  ·  " + tx("kaldığın yerden", "where you left off")
                            source.isEmpty() -> p.title
                            else -> "${p.title}  ·  $source"
                        }
                        MenuItem(label, Icons.Filled.PlayArrow) {
                            notifOpen = false
                            launchStripItem(StripItem.Played(p, app))
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 64.dp, end = 120.dp),
            )
        }

        AnimatedVisibility(
            visible = panelOpen,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            QuickPanel(
                vm = vm,
                tiles = buildList {
                    add(QuickTile("layout", tx("Arayüz", "Layout"), settings.layout.label, QuickIcons.layout, active = modern) {
                        vm.updateSettings { it.copy(layout = UiLayout.entries.toList().next(it.layout)) }
                    })
                    add(QuickTile("sound", tx("Ses efektleri", "Sound effects"), if (settings.soundsEnabled) tx("Açık", "On") else tx("Kapalı", "Off"), QuickIcons.sound, active = settings.soundsEnabled) {
                        vm.updateSettings { it.copy(soundsEnabled = !it.soundsEnabled) }
                    })
                    add(QuickTile(
                        "scores", tx("Yüzen skor", "Floating scores"),
                        when {
                            !settings.floatingScores -> tx("Kapalı", "Off")
                            vm.overlayAllowed() -> tx("Açık", "On")
                            else -> tx("İzin gerekli", "Permission needed")
                        },
                        QuickIcons.scores, active = settings.floatingScores,
                    ) {
                        val turnOn = !settings.floatingScores
                        vm.updateSettings { it.copy(floatingScores = turnOn) }
                        if (turnOn && !vm.overlayAllowed()) toast(tx("Önce izin ver: Ayarlar → Canlı skorlar", "Grant permission first: Settings → Live scores"))
                    })
                    add(QuickTile("weather", tx("Hava durumu", "Weather"), if (settings.weatherEnabled) tx("Açık", "On") else tx("Kapalı", "Off"), QuickIcons.weather, active = settings.weatherEnabled) {
                        vm.updateSettings { it.copy(weatherEnabled = !it.weatherEnabled) }
                    })
                    add(QuickTile(
                        "hidden", tx("Gizli uygulamalar", "Hidden apps"),
                        if (showHidden) tx("Gösteriliyor", "Shown") else tx("${hiddenApps.size} gizli", "${hiddenApps.size} hidden"),
                        QuickIcons.hidden, active = showHidden,
                    ) { vm.toggleShowHidden() })
                    if (modern) {
                        add(QuickTile("anim", tx("Animasyon", "Animation"), settings.animSpeed.label, QuickIcons.motion, active = settings.animSpeed != AnimSpeed.CALM) {
                            vm.updateSettings { it.copy(animSpeed = AnimSpeed.entries.toList().next(it.animSpeed)) }
                        })
                    } else {
                        add(QuickTile("shelf", tx("Vitrin", "Showcase"), vm.shelfStyle.label, QuickIcons.showcase, active = true) { vm.cycleShelfStyle() })
                    }
                },
                actions = listOf(
                    QuickAction(tx("Tüm ayarlar", "All settings"), QuickIcons.settings) {
                        panelOpen = false
                        settingsOpen = true
                    },
                    QuickAction(tx("Mağaza", "Store"), QuickIcons.store) {
                        panelOpen = false
                        storeOpen = true
                    },
                    QuickAction(tx("Android", "Android"), QuickIcons.settings) {
                        panelOpen = false
                        startSafely(Intent(Settings.ACTION_SETTINGS))
                    },
                    QuickAction(tx("Hakkında", "About"), QuickIcons.about) {
                        panelOpen = false
                        aboutOpen = true
                    },
                ),
            )
        }

        // Dahili oyun seçici (Casual'da hızlı ayarlardan açılır)
        if (gamePickerOpen) {
            GlassMenu(
                title = tx("Dahili oyunlar", "Built-in games"),
                subtitle = tx("Kumandayla oynanır", "Played with the remote"),
                items = BuiltInGame.entries.map { g ->
                    MenuItem("${g.title}  ·  ${g.description}", Icons.Filled.PlayArrow) {
                        gamePickerOpen = false
                        lastGame = g
                        gameOpen = g
                    }
                },
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxHeight(),
            )
        }

        AnimatedVisibility(
            visible = gameOpen != null,
            enter = fadeIn() + slideInVertically { it / 10 },
            exit = fadeOut(),
        ) {
            key(lastGame) { GameScreen(lastGame) }
        }

        AnimatedVisibility(
            visible = storeOpen,
            enter = fadeIn() + slideInVertically { it / 8 },
            exit = fadeOut() + slideOutVertically { it / 8 },
        ) {
            StoreScreen(
                installed = vm.installed,
                onLaunch = { app -> launchApp(app) },
                onGetFromPlay = { app -> startFirstWorking(StoreCatalog.playIntents(app), tx("Google Play açılamadı", "Couldn't open Google Play")) },
                onOpenPlayStore = { openPlayStore() },
            )
        }

        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn() + slideInVertically { -it / 8 },
            exit = fadeOut() + slideOutVertically { -it / 8 },
        ) {
            SearchScreen(
                apps = vm.apps,
                enabled = searchOpen,
                onLaunchApp = {
                    searchOpen = false
                    launchApp(it)
                },
                onAppMenu = { menuFor = MenuTarget(it, fromDock = false) },
                onGetFromPlay = { startFirstWorking(StoreCatalog.playIntents(it), tx("Google Play açılamadı", "Couldn't open Google Play")) },
            )
        }

        AnimatedVisibility(
            visible = settingsOpen,
            enter = fadeIn() + slideInVertically { it / 8 },
            exit = fadeOut() + slideOutVertically { it / 8 },
        ) {
            SettingsScreen(
                entries = settingsEntries(
                    vm = vm,
                    settings = settings,
                    onEditName = { textTarget = TextTarget.NAME },
                    onEditCity = { textTarget = TextTarget.CITY },
                    onEditClaudeKey = { textTarget = TextTarget.CLAUDE_KEY },
                    onOpenClaude = {
                        settingsOpen = false
                        askOpen = true
                    },
                    onRemoteShortcut = {
                        if (!startFirstWorking(listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)), null)) {
                            toast(tx("Komut sol panelde", "The command is in the left panel"))
                        }
                    },
                    onGames = {
                        settingsOpen = false
                        gamePickerOpen = true
                    },
                    onFloatingScores = {
                        val turnOn = !settings.floatingScores
                        vm.updateSettings { it.copy(floatingScores = turnOn) }
                        if (turnOn && !ScoreOverlay.canDraw(context)) {
                            toast(tx("Önce izin ver: komut sol panelde", "Grant the permission first: the command is in the left panel"))
                        }
                    },
                    onOpenStore = {
                        settingsOpen = false
                        storeOpen = true
                    },
                    onOpenAbout = {
                        settingsOpen = false
                        aboutOpen = true
                    },
                    onChangelog = { changelogOpen = true },
                    onUsageReport = {
                        settingsOpen = false
                        usageOpen = true
                    },
                    onAndroidSettings = { startSafely(Intent(Settings.ACTION_SETTINGS)) },
                    onMediaAccess = {
                        if (vm.mediaAccess) toast(tx("Medya geçmişi izni açık", "Media history permission is on"))
                        else if (!startFirstWorking(listOf(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)), null)) {
                            toast(tx("Bu izin adb ile verilir, komut sol panelde", "This permission is granted via adb; the command is in the left panel"))
                        }
                    },
                    onUsageAccess = {
                        if (vm.usageAccess) toast(tx("Kullanım istatistikleri izni açık", "Usage stats permission is on"))
                        else if (!startFirstWorking(listOf(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)), null)) {
                            toast(tx("Bu izin adb ile verilir, komut sol panelde", "This permission is granted via adb; the command is in the left panel"))
                        }
                    },
                    onSystemSaver = {
                        if (!vm.systemSaverReady) toast(tx("Önce izin gerekiyor, komut sol panelde", "Permission needed first; the command is in the left panel"))
                        else if (vm.applySystemScreensaver()) toast(tx("LiteDroid sistem ekran koruyucusu yapıldı", "LiteDroid is now the system screensaver"))
                        else toast(tx("Sistem ayarı yazılamadı", "Couldn't write the system setting"))
                    },
                    onClearHistory = {
                        vm.clearHistory()
                        toast(tx("İzleme geçmişi temizlendi", "Watch history cleared"))
                    },
                ),
                enabled = textTarget == null && !changelogOpen,
                refocusSignal = settingsRefocus,
            )
        }

        AnimatedVisibility(
            visible = usageOpen,
            enter = fadeIn() + slideInVertically { it / 8 },
            exit = fadeOut() + slideOutVertically { it / 8 },
        ) {
            UsageReportScreen(vm)
        }

        AnimatedVisibility(
            visible = askOpen,
            enter = fadeIn() + slideInVertically { it / 10 },
            exit = fadeOut(),
        ) {
            AskClaudeScreen(
                vm = vm,
                onType = { textTarget = TextTarget.CLAUDE_QUESTION },
                onOpenSettings = {
                    askOpen = false
                    settingsOpen = true
                },
            )
        }

        textTarget?.let { target ->
            key(target) {
                TextInputDialog(
                    title = when (target) {
                        TextTarget.NAME -> tx("Kullanıcı adı", "User name")
                        TextTarget.CITY -> tx("Hava durumu konumu", "Weather location")
                        TextTarget.CLAUDE_KEY -> tx("Claude API anahtarı", "Claude API key")
                        TextTarget.CLAUDE_QUESTION -> tx("Claude'a sor", "Ask Claude")
                    },
                    subtitle = when (target) {
                        TextTarget.NAME -> tx("Üst barda ve selamlamada görünür", "Shown in the top bar and greeting")
                        TextTarget.CITY -> tx("Şehir adı yaz (ör. İzmir). Boş bırakırsan konum otomatik bulunur.", "Type a city name (e.g. Izmir). Leave empty to detect it automatically.")
                        TextTarget.CLAUDE_KEY -> tx(
                            "console.anthropic.com → API Keys'ten aldığın anahtar (sk-ant-api03-…). Kumandayla yazmak zor: bilgisayardan da gönderebilirsin: $CMD_KEY",
                            "Your key from console.anthropic.com → API Keys (sk-ant-api03-…). Typing with the remote is tedious, you can send it from a computer: $CMD_KEY",
                        )
                        TextTarget.CLAUDE_QUESTION -> tx("Sorunu yaz, Claude cevaplasın", "Type your question for Claude")
                    },
                    initial = when (target) {
                        TextTarget.NAME -> settings.userName
                        TextTarget.CITY -> settings.weatherCity
                        TextTarget.CLAUDE_KEY -> settings.claudeKey
                        TextTarget.CLAUDE_QUESTION -> ""
                    },
                    allowEmpty = target == TextTarget.CITY || target == TextTarget.CLAUDE_KEY,
                    maxLength = when (target) {
                        TextTarget.NAME -> 20
                        TextTarget.CITY -> 40
                        TextTarget.CLAUDE_KEY -> 400
                        TextTarget.CLAUDE_QUESTION -> 500
                    },
                    onSave = { value ->
                        when (target) {
                            TextTarget.NAME -> vm.updateSettings { it.copy(userName = value) }
                            TextTarget.CITY -> vm.updateSettings { it.copy(weatherCity = value) }
                            TextTarget.CLAUDE_KEY -> vm.updateSettings { it.copy(claudeKey = value.trim()) }
                            TextTarget.CLAUDE_QUESTION -> vm.askClaude(value)
                        }
                        textTarget = null
                    },
                    onCancel = { textTarget = null },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        if (changelogOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )
            ChangelogDialog(modifier = Modifier.align(Alignment.Center))
        }

        if (aboutOpen) {
            val content = vm.content
            AboutDialog(
                onClose = { aboutOpen = false },
                contentInfo = when {
                    vm.mediaAccess && vm.usageAccess -> tx("Medya + kullanım açık", "Media + usage on")
                    vm.mediaAccess -> tx("Medya geçmişi açık", "Media history on")
                    vm.usageAccess -> tx("Kullanım istatistikleri açık", "Usage stats on")
                    content.access == ContentAccess.GRANTED && content.total > 0 -> tx("TV içerikleri açık", "TV content on")
                    else -> tx("Sadece LiteDroid geçmişi", "LiteDroid history only")
                },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        AnimatedVisibility(
            visible = movingPkg != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = tx("Yön tuşlarıyla taşı  ·  Bitirmek için seç", "Move with the arrow keys  ·  Select to finish"),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .glass(RoundedCornerShape(50))
                    .padding(horizontal = 22.dp, vertical = 10.dp),
            )
        }

        // ---------- Ekran koruyucu (en üstte) ----------
        AnimatedVisibility(
            visible = vm.screensaverActive,
            enter = fadeIn(tween(900)),
            exit = fadeOut(tween(350)),
        ) {
            ScreensaverContent(
                settings.saverScene,
                settings.userName,
                showNowPlaying = settings.saverNowPlaying,
                showScores = settings.saverScores,
                leagues = settings.scoreLeagues,
            )
        }
    }
}

private fun <T> List<T>.next(current: T): T = this[(indexOf(current) + 1).mod(size)]

/** Ayarlar sayfasının satırları. Değerli satırlar seçildikçe bir sonraki seçeneğe geçer. */
private fun settingsEntries(
    vm: LauncherViewModel,
    settings: LauncherSettings,
    onEditName: () -> Unit,
    onEditCity: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenAbout: () -> Unit,
    onChangelog: () -> Unit,
    onUsageReport: () -> Unit,
    onAndroidSettings: () -> Unit,
    onMediaAccess: () -> Unit,
    onUsageAccess: () -> Unit,
    onSystemSaver: () -> Unit,
    onClearHistory: () -> Unit,
    onEditClaudeKey: () -> Unit,
    onOpenClaude: () -> Unit,
    onFloatingScores: () -> Unit,
    onGames: () -> Unit,
    onRemoteShortcut: () -> Unit,
): List<SettingsEntry> = listOf(
    SettingsEntry.Header(tx("Görünüm", "Appearance")),
    SettingsEntry.Row("language", "Dil / Language", Icons.Filled.Face, settings.language.label,
        tx("Uygulamanın dili. Varsayılan Türkçe.", "The app language. Turkish is the default.")) {
        vm.updateSettings { it.copy(language = AppLanguage.entries.toList().next(it.language)) }
    },
    SettingsEntry.Row("layout", tx("Arayüz", "Layout"), Icons.Filled.ThumbUp, settings.layout.label,
        tx("Casual: cam vitrin ve büyük karolar. Modern: sekmeler, öne çıkan vitrin, geniş kart satırları, ", "Casual: glass showcase and big tiles. Modern: tabs, featured hero, wide card rows, ") +
            tx("üst barda Claude, arama ve hızlı panel.", "Claude, search and quick panel in the top bar.")) {
        vm.updateSettings { it.copy(layout = UiLayout.entries.toList().next(it.layout)) }
    },
    SettingsEntry.Row("shelf", tx("Vitrin stili (Casual)", "Showcase style (Casual)"), Icons.Filled.Home, vm.shelfStyle.label,
        tx("Sinematik: görsel tüm ekranı kaplar. Kart: solda büyük kart. Sade: büyük saat ve selamlama.", "Cinematic: art fills the screen. Card: a big card on the left. Minimal: big clock and greeting.")) {
        vm.cycleShelfStyle()
    },
    SettingsEntry.Row("wallpaper", tx("Duvar kâğıdı", "Wallpaper"), Icons.Filled.Create, Wallpapers.all[vm.themeIndex].label,
        tx("Arka plan renk teması. Casual'da renkler odaktaki uygulamaya göre de kayar.", "Background color theme. In Casual, colors also follow the focused app.")) { vm.cycleTheme() },
    SettingsEntry.Row("wp_motion", tx("Duvar kâğıdı hareketi", "Wallpaper motion"), Icons.Filled.Refresh, if (settings.wallpaperMotion) tx("Açık", "On") else tx("Kapalı", "Off"),
        tx("Arka plandaki renk lekelerinin ve vitrin fonunun yavaşça hareket etmesi.", "Slowly drifts the background color blobs and the showcase art.")) {
        vm.updateSettings { it.copy(wallpaperMotion = !it.wallpaperMotion) }
    },
    SettingsEntry.Row("columns", tx("Satırdaki uygulama sayısı (Casual)", "Apps per row (Casual)"), Icons.Filled.Menu, "${settings.columns}",
        tx("Karoların boyutunu belirler: 4 büyük, 6 küçük karo.", "Sets tile size: 4 is big, 6 is small.")) {
        vm.updateSettings { it.copy(columns = listOf(4, 5, 6).next(it.columns)) }
    },
    SettingsEntry.Row("anim", tx("Animasyon hızı", "Animation speed"), Icons.Filled.PlayArrow, settings.animSpeed.label,
        tx("Hızlı: sert ve zıplamalı. Normal: dengeli. Sakin: yumuşak ve uzun geçişler.", "Fast: snappy and bouncy. Normal: balanced. Calm: soft, longer transitions.")) {
        vm.updateSettings { it.copy(animSpeed = AnimSpeed.entries.toList().next(it.animSpeed)) }
    },

    SettingsEntry.Header(tx("Profil", "Profile")),
    SettingsEntry.Row("name", tx("Kullanıcı adı", "User name"), Icons.Filled.Person, settings.userName,
        tx("Üst barda ve selamlamada görünen ad.", "The name shown in the top bar and greeting.")) { onEditName() },
    SettingsEntry.Row("greeting", tx("Selamlama", "Greeting"), Icons.Filled.Face, if (settings.showGreeting) tx("Açık", "On") else tx("Kapalı", "Off"),
        tx("Adının üstünde \"İyi akşamlar\" gibi saate göre selamlama.", "A time-based greeting like \"Good evening\" above your name.")) {
        vm.updateSettings { it.copy(showGreeting = !it.showGreeting) }
    },
    SettingsEntry.Row("fav_shuffle", tx("Favorileri rastgele yeniden seç", "Shuffle favorites"), Icons.Filled.Favorite, null,
        tx("Favorileri bir uygulamaya basılı tutup \"Favorilere ekle\" ile kendin de seçebilirsin.", "You can also hold an app and pick \"Add to favorites\".")) { vm.shuffleFavorites() },

    SettingsEntry.Header(tx("Hava durumu", "Weather")),
    SettingsEntry.Row("weather", tx("Hava durumu", "Weather"), Icons.Filled.Star, if (settings.weatherEnabled) tx("Açık", "On") else tx("Kapalı", "Off"),
        tx("Hızlı panelde ve yüzen menüde sıcaklık ve konum. Veri Open-Meteo'dan gelir.", "Temperature and location in the quick panel and floating menu. Data from Open-Meteo.")) {
        vm.updateSettings { it.copy(weatherEnabled = !it.weatherEnabled) }
    },
    SettingsEntry.Row("city", tx("Konum", "Location"), Icons.Filled.Place, settings.weatherCity.ifBlank { tx("Otomatik", "Automatic") },
        tx("Otomatik: konum internet bağlantının IP adresinden yaklaşık olarak bulunur (ipwho.is). ", "Automatic: location is estimated from your internet IP address (ipwho.is). ") +
            tx("Daha doğru sonuç için şehir adını kendin yazabilirsin.", "For better accuracy, type your city yourself.")) { onEditCity() },
    SettingsEntry.Row("weather_refresh", tx("Hava durumunu şimdi yenile", "Refresh weather now"), Icons.Filled.Refresh,
        vm.weather?.let { "${it.temperature}°C ${it.city}" }, null) { vm.refreshWeather() },

    SettingsEntry.Header(tx("Ses", "Sound")),
    SettingsEntry.Row("sounds", tx("Ses efektleri", "Sound effects"), Icons.Filled.Notifications, if (settings.soundsEnabled) tx("Açık", "On") else tx("Kapalı", "Off"),
        tx("Gezinme, seçme, geri ve kenara çarpma sesleri.", "Navigate, select, back and edge-bump sounds.")) {
        vm.updateSettings { it.copy(soundsEnabled = !it.soundsEnabled) }
    },
    SettingsEntry.Row("sound_level", tx("Ses seviyesi", "Volume"), Icons.Filled.KeyboardArrowUp, settings.soundLevel.label, null) {
        vm.updateSettings { it.copy(soundLevel = SoundLevel.entries.toList().next(it.soundLevel)) }
    },

    SettingsEntry.Header(tx("Ekran koruyucu", "Screensaver")),
    SettingsEntry.Row("saver_min", tx("Başlama süresi", "Start after"), Icons.Filled.DateRange,
        if (settings.saverMinutes == 0) tx("Kapalı", "Off") else tx("${settings.saverMinutes} dk", "${settings.saverMinutes} min"),
        tx("Ana ekranda bu süre boyunca tuşa basılmazsa ekran koruyucu açılır.", "The screensaver starts after this long without a key press on the home screen.")) {
        vm.updateSettings { it.copy(saverMinutes = LauncherSettings.SAVER_OPTIONS.next(it.saverMinutes)) }
    },
    SettingsEntry.Row("saver_scene", tx("Sahne", "Scene"), Icons.Filled.Star, settings.saverScene.label,
        tx("Karışık: sinema salonu, maç günü, retro oyun ve kutup ışıkları sahneleri 40 saniyede bir değişir.", "Mixed: cinema, match day, retro game and aurora scenes rotate every 40 seconds.")) {
        vm.updateSettings { it.copy(saverScene = SaverScene.entries.toList().next(it.saverScene)) }
    },
    SettingsEntry.Row("saver_now", tx("Şimdi başlat", "Start now"), Icons.Filled.PlayArrow, null, null) { vm.startScreensaver() },
    SettingsEntry.Row("saver_system", tx("Sistem ekran koruyucusu yap", "Set as system screensaver"), Icons.Filled.Settings,
        if (vm.systemSaverReady) tx("Uygula", "Apply") else tx("İzin gerekli", "Permission needed"),
        tx("Başka bir uygulama açıkken de LiteDroid ekran koruyucusu çalışsın. Bir kez şu komutları çalıştır:\n\n$CMD_SAVER", "Run the LiteDroid screensaver even while another app is open. Run these commands once:\n\n$CMD_SAVER")) {
        onSystemSaver()
    },

    SettingsEntry.Row("saver_np", tx("Şimdi çalıyor kartı", "Now playing card"), Icons.Filled.PlayArrow,
        if (settings.saverNowPlaying) tx("Açık", "On") else tx("Kapalı", "Off"),
        tx("Ekran koruyucuda, çalan müzik ya da videonun kapağı, adı ve ilerlemesi.", "On the screensaver: artwork, title and progress of what's playing.")) {
        vm.updateSettings { it.copy(saverNowPlaying = !it.saverNowPlaying) }
    },
    SettingsEntry.Row("saver_scores", tx("Skor kartı", "Scores card"), Icons.Filled.Star,
        if (settings.saverScores) tx("Açık", "On") else tx("Kapalı", "Off"),
        tx("Ekran koruyucuda bugünün maçları, canlı skorlar ve gol bildirimi.", "On the screensaver: today's matches, live scores and goal alerts.")) {
        vm.updateSettings { it.copy(saverScores = !it.saverScores) }
    },

    SettingsEntry.Header(tx("Canlı skorlar", "Live scores")),
    SettingsEntry.Row("float_scores", tx("Yüzen skor şeridi", "Floating score strip"), Icons.Filled.Star,
        when {
            !settings.floatingScores -> tx("Kapalı", "Off")
            vm.overlayAllowed() -> tx("Açık", "On")
            else -> tx("İzin gerekli", "Permission needed")
        },
        tx("Netflix, YouTube ya da başka bir şey izlerken sağ üst köşede canlı maçların küçük skor şeridi; gol olunca kısa bildirim. " +
            "Bir kez şu komutu çalıştır:\n\n$CMD_OVERLAY",
            "While watching anything, a small live-score strip in the top-right corner, with a short goal alert. Run this command once:\n\n$CMD_OVERLAY")) {
        onFloatingScores()
    },
    SettingsEntry.Row("home_redirect", tx("Home tuşunu LiteDroid'e yönlendir", "Send Home to LiteDroid"), Icons.Filled.Home,
        if (settings.homeRedirect) tx("Açık", "On") else tx("Kapalı", "Off"),
        tx("Bazı TV kutuları kendi ana ekranlarının kapatılmasına izin vermez ve Home tuşu hep oraya gider. Bu açıkken kumanda kısayolu servisi " +
            "Home'u yakalar ve kutunun ana ekranı öne geldiğinde LiteDroid'i açar. LiteDroid zaten varsayılan ana ekransa hiçbir şey yapmaz. " +
            "Kumanda kısayolu (erişilebilirlik) açık olmalı.",
            "Some TV boxes don't let you turn off their own home screen, so Home always goes there. With this on, the remote shortcut service " +
            "catches Home and opens LiteDroid whenever the box's home screen comes to the front. Does nothing when LiteDroid is already the default. " +
            "Needs the remote shortcut (accessibility) on.")) {
        vm.updateSettings { it.copy(homeRedirect = !it.homeRedirect) }
    },
    SettingsEntry.Row("remote_menu", tx("Kumanda kısayolu (Geri'ye iki kez bas)", "Remote shortcut (double-press Back)"), Icons.Filled.Menu,
        if (vm.remoteShortcutEnabled()) tx("Açık", "On") else tx("Kapalı", "Off"),
        tx("Bir uygulama açıkken Geri'ye hızlıca iki kez basınca yüzen menü açılır: saat, tüm skorlar, çalan müzik, skor ayarları. " +
            "Tek basış normal Geri'dir (çok kısa bir gecikmeyle). Açmak için: Android Ayarları → Erişilebilirlik → LiteDroid kumanda kısayolu. " +
            "Ya da bilgisayardan (başka erişilebilirlik hizmetin yoksa):\n\n${RemoteKeyService.ADB_ENABLE}",
            "While an app is open, press Back twice quickly to open the floating menu: clock, all scores, now playing, score settings. " +
            "A single press is a normal Back (with a tiny delay). Turn it on in Android Settings → Accessibility → LiteDroid remote shortcut, " +
            "or from a computer (if you use no other accessibility service):\n\n${RemoteKeyService.ADB_ENABLE}")) {
        onRemoteShortcut()
    },
    SettingsEntry.Row("float_live", tx("Şeritte yalnızca canlı maçlar", "Only live matches in the strip"), Icons.Filled.Refresh,
        if (settings.floatingOnlyLive) tx("Evet", "Yes") else tx("Bugünün tümü", "All today"),
        tx("Kapalıysa henüz başlamamış bugünkü maçlar da görünür.", "When off, today's upcoming matches are shown too.")) {
        vm.updateSettings { it.copy(floatingOnlyLive = !it.floatingOnlyLive) }
    },
) + League.entries.map { league ->
    SettingsEntry.Row("league_${league.code}", league.label, Icons.Filled.Star,
        if (league.code in settings.scoreLeagues) tx("Takipte", "Following") else tx("Kapalı", "Off"),
        tx("Bu ligin maçları skor kartında ve yüzen şeritte görünsün.", "Show this league in the scores card and the floating strip.")) {
        vm.updateSettings {
            val now = if (league.code in it.scoreLeagues) it.scoreLeagues - league.code else it.scoreLeagues + league.code
            it.copy(scoreLeagues = now)
        }
    }
} + listOf(
    SettingsEntry.Header(tx("Claude'a sor", "Ask Claude")),
    SettingsEntry.Row("claude_open", tx("Claude'a sor'u aç", "Open Ask Claude"), Icons.Filled.Face, null,
        tx("Sesle ya da yazarak her şeyi sor; cevap ekranda ve sesli gelir.", "Ask anything by voice or text; the answer appears on screen and is read aloud.")) {
        onOpenClaude()
    },
    SettingsEntry.Row("claude_key", tx("API anahtarı", "API key"), Icons.Filled.Lock,
        settings.claudeKey.takeIf { it.isNotBlank() }?.let { "…" + it.takeLast(4) } ?: tx("Girilmedi", "Not set"),
        tx("Claude'a sor kendi Anthropic API anahtarınla çalışır (console.anthropic.com → API Keys). Kullandıkça API ücreti senin hesabına yazılır. " +
            "Anahtar yalnızca bu cihazda saklanır, doğrudan api.anthropic.com'a gönderilir.",
            "Ask Claude uses your own Anthropic API key (console.anthropic.com → API Keys). Usage is billed to your account. " +
            "The key is stored only on this device and sent directly to api.anthropic.com.")) {
        onEditClaudeKey()
    },
    SettingsEntry.Row("claude_model", tx("Model", "Model"), Icons.Filled.Build, settings.claudeModel.label,
        tx("Sonnet daha kapsamlı, Haiku daha hızlı ve ucuz cevap verir.", "Sonnet is more thorough; Haiku is faster and cheaper.")) {
        vm.updateSettings { it.copy(claudeModel = ClaudeModel.entries.toList().next(it.claudeModel)) }
    },
    SettingsEntry.Row("claude_speak", tx("Sesli yanıt", "Spoken replies"), Icons.Filled.Notifications,
        if (settings.claudeSpeak) tx("Açık", "On") else tx("Kapalı", "Off"),
        tx("Cevap bitince cihazın metin okuma sesiyle okunur.", "Answers are read aloud with the device's text-to-speech voice.")) {
        vm.updateSettings { it.copy(claudeSpeak = !it.claudeSpeak) }
    },
    SettingsEntry.Row("claude_web", tx("Web araması", "Web search"), Icons.Filled.Search,
        if (settings.claudeWebSearch) tx("Açık", "On") else tx("Kapalı", "Off"),
        tx("Güncel sorularda (maçlar, haberler, hava) Claude web'de arar ve kaynak gösterir. Arama başına küçük bir ek API ücreti vardır.",
            "For current topics Claude searches the web and cites sources. Each search adds a small API fee.")) {
        vm.updateSettings { it.copy(claudeWebSearch = !it.claudeWebSearch) }
    },

    SettingsEntry.Header(tx("İçerik ve gizlilik", "Content and privacy")),
    SettingsEntry.Row("media", tx("Medya geçmişi", "Media history"), Icons.Filled.Lock, if (vm.mediaAccess) tx("Açık", "On") else tx("Kapalı", "Off"),
        tx("Netflix, YouTube, Prime gibi uygulamalarda yarım kalan videoları kapağıyla \"Kaldığın yerden devam et\" rafında ve çan menüsünde gösterir. ", "Shows unfinished videos from apps like Netflix, YouTube and Prime with artwork in the continue row and the bell menu. ") +
            tx("Bir kez şu komutu çalıştır:\n\n$CMD_MEDIA", "Run this command once:\n\n$CMD_MEDIA")) {
        onMediaAccess()
    },
    SettingsEntry.Row("usage", tx("Kullanım istatistikleri", "Usage stats"), Icons.Filled.Lock, if (vm.usageAccess) tx("Açık", "On") else tx("Kapalı", "Off"),
        tx("Kumanda tuşuyla açtıkların dahil gerçek son kullanılanlar. Bir kez şu komutu çalıştır:\n\n$CMD_USAGE", "True recently used apps, including ones opened with remote buttons. Run this command once:\n\n$CMD_USAGE")) {
        onUsageAccess()
    },
    SettingsEntry.Row("usage_report", tx("Kullanım raporu", "Usage report"), Icons.Filled.DateRange, null,
        tx("Bugün, dün ve son 7 günde hangi uygulamada ne kadar vakit geçti: ekran süresi, açılışlar, saatlere ve günlere göre dağılım. ",
            "How much time went to which app today, yesterday and over the last 7 days: screen time, opens, by hour and by day. ") +
            tx("Kullanım istatistikleri izni gerekir.", "Needs usage access.")) { onUsageReport() },
    SettingsEntry.Row("clear", tx("İzleme geçmişini temizle", "Clear watch history"), Icons.Filled.Delete, null,
        tx("Kaldığın yerden devam et ve son kullanılanlar listesini sıfırlar.", "Resets the continue-watching and recently used lists.")) { onClearHistory() },

    SettingsEntry.Header(tx("Diğer", "More")),
    SettingsEntry.Row("games", tx("Dahili oyunlar", "Built-in games"), Icons.Filled.PlayArrow, null,
        tx("Yılan, Kutu İtme, Mini Yarış, SOS, XOX ve Sudoku. Modern arayüzde Oyunlar sekmesinde de var.",
            "Snake, Box Push, Mini Racer, SOS, Tic-Tac-Toe and Sudoku. Also in the Games tab of the Modern layout.")) { onGames() },
    SettingsEntry.Row("hidden", if (vm.showHidden) tx("Gizli uygulamaları sakla", "Conceal hidden apps") else tx("Gizli uygulamaları göster", "Show hidden apps"),
        Icons.Filled.Search, "${vm.hiddenApps.size}", null) { vm.toggleShowHidden() },
    SettingsEntry.Row("store", tx("Uygulama mağazası", "App store"), Icons.Filled.ShoppingCart, null, null) { onOpenStore() },
    SettingsEntry.Row("android", tx("Android ayarları", "Android settings"), Icons.Filled.Build, null, null) { onAndroidSettings() },
    SettingsEntry.Row("changelog", tx("Sürüm notları", "Release notes"), Icons.Filled.List, "v" + Changelog.releases.first().version,
        tx("İlk sürümden bugüne LiteDroid'e gelen tüm yenilikler, sürüm sürüm.", "Everything new in LiteDroid since the first version, release by release.")) { onChangelog() },
    SettingsEntry.Row("about", tx("Hakkında", "About"), Icons.Filled.Info, null, null) { onOpenAbout() },
)
