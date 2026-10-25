package dev.litedroid.launcher.ui

import androidx.compose.runtime.collectAsState
import dev.litedroid.launcher.data.UpdateState
import dev.litedroid.launcher.data.Updater
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shadow
import coil3.size.Precision
import coil3.request.ImageRequest
import coil3.compose.LocalPlatformContext
import dev.litedroid.launcher.games.GameGlyph
import dev.litedroid.launcher.games.BuiltInGame
import dev.litedroid.launcher.media.PlayedItem
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import dev.litedroid.launcher.ui.theme.Jakarta
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import dev.litedroid.launcher.I18n
import dev.litedroid.launcher.LauncherViewModel
import dev.litedroid.launcher.R
import dev.litedroid.launcher.Recommendation
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.data.LauncherApp
import dev.litedroid.launcher.data.StoreApp
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

enum class ModernTab(private val tr: String, private val en: String) {
    HOME("Ana sayfa", "Home"),
    APPS("Uygulamalar", "Apps"),
    GAMES("Ekstra", "Extras"),
    STORE("Mağaza", "Store");

    val label: String get() = tx(tr, en)
}

private val BLUE = Color(0xFF3A7BFF)
private val VIOLET = Color(0xFF7B5CFF)
private val PRIMARY_BRUSH = Brush.horizontalGradient(listOf(BLUE, VIOLET))

private val REC_COLORS = listOf(
    Color(0xFF9B4DFF) to Color(0xFF4A1D9E),
    Color(0xFF22B573) to Color(0xFF0C5E3B),
    Color(0xFFFF6A4D) to Color(0xFF9E2A1D),
    Color(0xFF3A8BFF) to Color(0xFF173E9E),
)

// ------------------------------------------------------------------
// Odak hafızası: bir menü kapandığında odak en son nerede kaldıysa oraya döner.
// ------------------------------------------------------------------

class FocusMemory {
    private val requesters = HashMap<String, FocusRequester>()
    val attached = HashSet<String>()
    var lastKey: String? = null

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun restore(vararg fallbacks: String) {
        val key = (listOfNotNull(lastKey) + fallbacks).firstOrNull { it in attached } ?: return
        runCatching { requester(key).requestFocus() }
    }
}

private fun Modifier.track(fm: FocusMemory, key: String): Modifier =
    this
        .focusRequester(fm.requester(key))
        .onFocusChanged { if (it.isFocused) fm.lastKey = key }

@Composable
private fun RegisterKey(fm: FocusMemory, key: String) {
    DisposableEffect(fm, key) {
        fm.attached += key
        onDispose { fm.attached -= key }
    }
}

/** Odaklanınca büyüyen, parlayan ve beyaz çerçeve alan genel kart. */
@Composable
private fun FocusCard(
    fm: FocusMemory,
    key: String,
    enabled: Boolean,
    glow: Color,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    corner: Dp = 18.dp,
    focusScale: Float = 1.07f,
    onFocused: () -> Unit = {},
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    RegisterKey(fm, key)
    val motion = LocalMotion.current
    val sounds = LocalSounds.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(corner)
    val scale by animateFloatAsState(
        targetValue = if (focused) focusScale else 1f,
        animationSpec = spring(dampingRatio = motion.tileDamping, stiffness = motion.tileStiffness),
        label = "cardScale",
    )
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .softGlow(focused, lerp(glow, Color.White, 0.45f), corner)
            .clip(shape)
            .then(if (focused) Modifier.border(1.5.dp, Color.White.copy(alpha = 0.8f), shape) else Modifier)
            .track(fm, key)
            .onFocusChanged {
                if (it.isFocused && !focused) {
                    sounds?.play(Sfx.NAV)
                    onFocused()
                }
                focused = it.isFocused
            }
            .onRemoteSelect(onSelect, onLongPress)
            .focusable(enabled = enabled)
    ) {
        content(focused)
        if (focused) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = 0.07f))
            )
        }
    }
}

// ------------------------------------------------------------------
// Ana bileşen
// ------------------------------------------------------------------

@Composable
fun ModernHome(
    vm: LauncherViewModel,
    enabled: Boolean,
    paused: () -> Boolean,
    restoreSignal: Int,
    onLaunchApp: (LauncherApp) -> Unit,
    onAppMenu: (LauncherApp) -> Unit,
    onLaunchStrip: (StripItem) -> Unit,
    onGetFromPlay: (StoreApp) -> Unit,
    onOpenPlayStore: () -> Unit,
    onFindGames: () -> Unit,
    onPlayGame: (BuiltInGame) -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onQuickSettings: () -> Unit,
    onProfile: () -> Unit,
    onAskClaude: () -> Unit,
) {
    val fm = remember { FocusMemory() }
    var tab by remember { mutableStateOf(ModernTab.HOME) }
    val listState = rememberLazyListState()
    val settings = vm.settings

    LaunchedEffect(Unit) {
        delay(150)
        fm.restore("hero_play", "app_0", "tab_HOME")
    }
    LaunchedEffect(restoreSignal) {
        if (restoreSignal > 0) {
            delay(50)
            fm.restore("hero_play", "app_0", "tab_HOME")
        }
    }
    LaunchedEffect(vm.homeSignal) {
        if (vm.homeSignal > 0) {
            tab = ModernTab.HOME
            fm.lastKey = null
            runCatching { listState.scrollToItem(0) }
            delay(80)
            fm.restore("hero_play", "app_0", "tab_HOME")
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Arka plan: canlı duvar kâğıdı + okunabilirlik için koyu katman
        AnimatedWallpaper(Wallpapers.all[vm.themeIndex], accent = null, paused = paused, dim = 0.6f)

        Column(Modifier.fillMaxSize()) {
            ModernTopBar(
                fm = fm,
                tab = tab,
                enabled = enabled,
                userName = settings.userName,
                showGreeting = settings.showGreeting,
                notificationCount = vm.unseenContinue + (if (Updater.state.collectAsState().value.let { it is UpdateState.Available || it is UpdateState.NeedsConfirm }) 1 else 0),
                onTab = { tab = it },
                onSearch = onSearch,
                onNotifications = onNotifications,
                onSettings = onQuickSettings,
                onProfile = onProfile,
                onAskClaude = onAskClaude,
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (tab) {
                    ModernTab.HOME -> HomeTab(
                        vm, fm, enabled, listState,
                        onLaunchApp, onAppMenu, onLaunchStrip, onGetFromPlay,
                        onAllApps = { tab = ModernTab.APPS },
                        onOpenSettings = onProfile,
                    )
                    ModernTab.APPS -> AppGridTab(
                        title = tx("Tüm uygulamalar", "All apps"),
                        apps = vm.apps.sortedBy { it.label.lowercase() },
                        fm = fm, enabled = enabled,
                        onLaunchApp = onLaunchApp, onAppMenu = onAppMenu,
                        trailing = null,
                    )
                    ModernTab.GAMES -> GamesTab(
                        games = vm.games,
                        fm = fm,
                        enabled = enabled,
                        onPlayGame = onPlayGame,
                        onLaunchApp = onLaunchApp,
                        onAppMenu = onAppMenu,
                        onFindGames = onFindGames,
                    )
                    ModernTab.STORE -> StoreScreen(
                        installed = vm.installed,
                        onLaunch = onLaunchApp,
                        onGetFromPlay = onGetFromPlay,
                        onOpenPlayStore = onOpenPlayStore,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// Üst gezinme çubuğu
// ------------------------------------------------------------------

@Composable
private fun ModernTopBar(
    fm: FocusMemory,
    tab: ModernTab,
    enabled: Boolean,
    userName: String,
    showGreeting: Boolean,
    notificationCount: Int,
    onTab: (ModernTab) -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onSettings: () -> Unit,
    onProfile: () -> Unit,
    onAskClaude: () -> Unit,
) {
    val now = rememberNow()
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            // Üstte hafif koyu perde: duvar kâğıdı ne olursa olsun bar okunur kalır
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.42f), Color.Transparent)))
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.logo_mark), contentDescription = null, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(9.dp))
        Wordmark(18.sp)
        Spacer(Modifier.width(20.dp))
        // Sekmeler tek bir cam kapsülün içinde (segment kontrol)
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(50))
                .glassSheen(0.7f)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModernTab.entries.forEach { t ->
                TabPill(
                    fm = fm,
                    tab = t,
                    selected = t == tab,
                    enabled = enabled,
                    onSelect = { onTab(t) },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(fm, "top_claude", enabled, Icons.Filled.Search, tx("Claude'a sor", "Ask Claude"), badge = false, onSelect = onAskClaude, accent = true)
            IconCircle(fm, "top_search", enabled, Icons.Filled.Search, tx("Ara", "Search"), badge = false, onSelect = onSearch)
            IconCircle(fm, "top_bell", enabled, Icons.Filled.Notifications, tx("Bildirimler", "Notifications"), badge = notificationCount > 0, onSelect = onNotifications)
            // Açılır hızlı panel: aşağı ok (tüm ayarlar profil resminde)
            IconCircle(fm, "top_gear", enabled, Icons.Filled.KeyboardArrowDown, tx("Hızlı panel", "Quick panel"), badge = false, onSelect = onSettings)
        }
        Spacer(Modifier.width(10.dp))
        ProfilePill(fm, enabled, userName, if (showGreeting) greetingFor(now.hour) else null, onProfile)
        Spacer(Modifier.width(14.dp))
        BarDivider(32.dp)
        Spacer(Modifier.width(14.dp))
        BarClock(now, 24.sp)
    }
}

@Composable
private fun TabPill(fm: FocusMemory, tab: ModernTab, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val key = "tab_" + tab.name
    RegisterKey(fm, key)
    val motion = LocalMotion.current
    val sounds = LocalSounds.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.06f else 1f,
        spring(dampingRatio = motion.tileDamping, stiffness = motion.tileStiffness),
        label = "tabScale",
    )
    val textColor = when {
        focused -> Color(0xFF0E1222)
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.78f)
    }
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .then(
                when {
                    focused -> Modifier.background(Color.White.copy(alpha = 0.95f))
                    selected -> Modifier
                        .background(PRIMARY_BRUSH)
                        .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
                    else -> Modifier
                }
            )
            .track(fm, key)
            .onFocusChanged {
                if (it.isFocused && !focused) sounds?.play(Sfx.NAV)
                focused = it.isFocused
            }
            .onRemoteSelect(onSelect)
            .focusable(enabled = enabled)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Simge yalnızca seçili sekmede: bar daha sade, saate yer kalır
        if (selected) {
            when (tab) {
                ModernTab.HOME -> Icon(Icons.Filled.Home, null, tint = textColor, modifier = Modifier.size(16.dp))
                ModernTab.APPS -> GridGlyph(textColor, 14.dp)
                ModernTab.GAMES -> GamepadGlyph(textColor, 17.dp)
                ModernTab.STORE -> Icon(Icons.Filled.ShoppingCart, null, tint = textColor, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(7.dp))
        }
        Text(
            tab.label,
            color = textColor,
            fontSize = 13.5.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.SemiBold,
            letterSpacing = 0.1.sp,
        )
    }
}

@Composable
private fun IconCircle(
    fm: FocusMemory,
    key: String,
    enabled: Boolean,
    icon: ImageVector,
    description: String,
    badge: Boolean,
    onSelect: () -> Unit,
    accent: Boolean = false,
) {
    RegisterKey(fm, key)
    val motion = LocalMotion.current
    val sounds = LocalSounds.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.15f else 1f,
        spring(dampingRatio = motion.tileDamping, stiffness = motion.tileStiffness),
        label = "iconScale",
    )
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(38.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(if (focused) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.07f))
                .then(if (focused) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape))
                .track(fm, key)
                .onFocusChanged {
                    if (it.isFocused && !focused) sounds?.play(Sfx.NAV)
                    focused = it.isFocused
                }
                .onRemoteSelect(onSelect)
                .focusable(enabled = enabled),
            contentAlignment = Alignment.Center,
        ) {
            if (accent) {
                // Claude'a sor: sıcak renkli ışıltı simgesi
                SparkleGlyph(if (focused) Color(0xFFC8553D) else Color(0xFFF2A07B), 20.dp)
            } else {
                Icon(icon, description, tint = if (focused) Color(0xFF0E1222) else Color.White, modifier = Modifier.size(20.dp))
            }
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF4D5E))
                    .border(1.5.dp, Color(0xFF0E1222), CircleShape)
            )
        }
    }
}

@Composable
private fun ProfilePill(fm: FocusMemory, enabled: Boolean, name: String, greeting: String?, onSelect: () -> Unit) {
    val key = "top_profile"
    RegisterKey(fm, key)
    val motion = LocalMotion.current
    val sounds = LocalSounds.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.06f else 1f,
        spring(dampingRatio = motion.tileDamping, stiffness = motion.tileStiffness),
        label = "profileScale",
    )
    val bg by animateColorAsState(if (focused) Color.White.copy(alpha = 0.95f) else Color.Transparent, label = "profileBg")
    val textColor = if (focused) Color(0xFF0E1222) else Color.White
    Row(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50))
            .background(bg)
            .track(fm, key)
            .onFocusChanged {
                if (it.isFocused && !focused) sounds?.play(Sfx.NAV)
                focused = it.isFocused
            }
            .onRemoteSelect(onSelect)
            .focusable(enabled = enabled)
            .padding(start = 3.dp, end = if (focused) 12.dp else 3.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dev.litedroid.launcher.tools.ProfileAvatar(name, 36.dp, 2.dp, 16)
        // Ad ve selamlama odaklanınca açılır; bar daralır, saat her zaman sığar
        if (focused) Spacer(Modifier.width(8.dp))
        if (focused) Column {
            if (greeting != null) {
                Text(
                    greeting.uppercase(I18n.locale),
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = 8.5.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.9.sp,
                    maxLines = 1,
                )
            }
            Text(name, color = textColor, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp, maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------
// Ana sayfa sekmesi
// ------------------------------------------------------------------

private data class HeroItem(
    val key: String,
    val title: String,
    val tagline: String,
    val description: String,
    val tags: String,
    val image: String?,
    val app: LauncherApp?,
    val primaryLabel: String,
    val onPrimary: () -> Unit,
    val onSecondary: (() -> Unit)?,
)

@Composable
private fun HomeTab(
    vm: LauncherViewModel,
    fm: FocusMemory,
    enabled: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onLaunchApp: (LauncherApp) -> Unit,
    onAppMenu: (LauncherApp) -> Unit,
    onLaunchStrip: (StripItem) -> Unit,
    onGetFromPlay: (StoreApp) -> Unit,
    onAllApps: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val byPkg = remember(vm.installed) { vm.installed.associateBy { it.packageName } }

    // Vitrin sadece uygulamaları tanıtır; yarım kalan içerikler aşağıdaki "devam et" rafında
    val heroItems = remember(vm.favorites, vm.apps) {
        buildList {
            // Geniş vitrinde yalnızca içerik uygulamaları: Ayarlar, Play Store, dosya yöneticisi gibi araçlar yok
            val pool = (vm.favorites + vm.apps).distinctBy { it.packageName }.filter { it.banner != null && !it.isUtility }
            pool.take(6).forEach { app ->
                add(
                    HeroItem(
                        key = "app_" + app.packageName,
                        title = app.label,
                        tagline = app.category ?: tx("Uygulama", "App"),
                        description = if (vm.isFavorite(app.packageName)) tx("Favori uygulaman. Açmak için seç.", "Your favorite app. Select to open.") else tx("Açmak için seç, seçenekler için basılı tut.", "Select to open, hold for options."),
                        tags = listOfNotNull(app.category, tx("Uygulama", "App")).distinct().joinToString("  •  ").uppercase(I18n.locale),
                        image = null,
                        app = app,
                        primaryLabel = tx("Aç", "Open"),
                        onPrimary = { onLaunchApp(app) },
                        onSecondary = { onAppMenu(app) },
                    )
                )
            }
        }
    }

    // Sadece kaldığı yerden devam edilebilen videolar (son kullanılan uygulamalar değil)
    val continueItems = remember(vm.played, vm.content, byPkg) {
        buildList<StripItem> {
            vm.played.forEach { add(StripItem.Played(it, byPkg[it.packageName])) }
            vm.content.watchNext.forEach { add(StripItem.Content(it, byPkg[it.packageName])) }
        }.distinctBy { it.key }.take(12)
    }
    // Aynı uygulamadan birden fazla yarım içerik: kart numaraları için grup sırası
    val sameAppGroups = remember(continueItems) {
        continueItems.filterIsInstance<StripItem.Played>().groupBy({ it.item.packageName }, { it.key })
    }
    val rowApps = remember(vm.favorites, vm.apps) {
        (vm.favorites + vm.apps.filterNot { it.isUtility }).distinctBy { it.packageName }.take(9)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 2.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (heroItems.isNotEmpty()) {
            item(key = "hero") {
                HeroCarousel(heroItems, fm, enabled)
            }
        }
        if (continueItems.isNotEmpty() || !vm.mediaAccess) {
            item(key = "continue") {
                Section(tx("Kaldığın yerden devam et", "Continue where you left off")) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (!vm.mediaAccess) {
                            item(key = "media_hint") { MediaAccessHint(fm, enabled, onOpenSettings) }
                        }
                        itemsIndexed(continueItems, key = { _, it -> it.key }) { i, item ->
                            val pkg = (item as? StripItem.Played)?.item?.packageName
                            val group = if (pkg == null) null else sameAppGroups[pkg]
                            ContinueCard(
                                item, fm, "cont_$i", enabled, onLaunchStrip,
                                sameAppIndex = group?.indexOf(item.key) ?: 0,
                                sameAppCount = group?.size ?: 1,
                            )
                        }
                    }
                }
            }
        }
        item(key = "apps") {
            Section(tx("Uygulamaların", "Your apps")) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(rowApps, key = { _, it -> it.packageName }) { i, app ->
                        AppColorTile(
                            app, fm, "app_$i", enabled,
                            Modifier
                                .width(132.dp)
                                .height(76.dp),
                            onLaunchApp, onAppMenu,
                            compact = true,
                        )
                    }
                    item(key = "all_apps") { AllAppsTile(fm, enabled, onAllApps) }
                }
            }
        }
        if (vm.games.isNotEmpty()) {
            item(key = "games") {
                Section(tx("Oyunlar", "Games")) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(vm.games, key = { _, it -> it.packageName }) { i, app ->
                            AppColorTile(
                                app, fm, "game_$i", enabled,
                                Modifier
                                    .width(150.dp)
                                    .height(78.dp),
                                onLaunchApp, onAppMenu,
                            )
                        }
                    }
                }
            }
        }
        if (vm.recommendations.isNotEmpty()) {
            item(key = "recs") {
                Section(tx("Senin için önerilenler", "Recommended for you")) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(vm.recommendations, key = { _, it -> it.app.name }) { i, rec ->
                            RecommendationCard(rec, fm, "rec_$i", enabled, onGetFromPlay)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Row(Modifier.padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
            Spacer(Modifier.width(6.dp))
            Text("›", color = Color.White.copy(alpha = 0.5f), fontSize = 18.sp, fontWeight = FontWeight.Light)
        }
        content()
    }
}

@Composable
private fun HeroCarousel(items: List<HeroItem>, fm: FocusMemory, enabled: Boolean) {
    var index by remember { mutableIntStateOf(0) }
    var heroHasFocus by remember { mutableStateOf(false) }
    val current = items[index.coerceIn(0, items.lastIndex)]
    val accent = current.app?.accent ?: VIOLET
    val sounds = LocalSounds.current

    // Odak vitrinde değilken 8 saniyede bir sonraki öne çıkana geç
    LaunchedEffect(index, heroHasFocus, items.size) {
        if (!heroHasFocus && items.size > 1) {
            delay(8_000)
            index = (index + 1) % items.size
        }
    }
    fun step(delta: Int): Boolean {
        if (items.size <= 1) return false
        sounds?.play(Sfx.NAV)
        index = (index + delta + items.size) % items.size
        return true
    }

    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .padding(horizontal = 28.dp)
            .fillMaxWidth()
            .height(200.dp)
            .clip(shape)
            .border(1.dp, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f))), shape)
            .onFocusChanged { heroHasFocus = it.hasFocus }
    ) {
        FastCrossfade(targetState = current, durationMillis = 600, modifier = Modifier.fillMaxSize()) { item ->
            HeroBackground(item)
        }

        // Metin bloğu
        FastCrossfade(
            targetState = current,
            durationMillis = 400,
            modifier = Modifier
                .fillMaxWidth(0.56f)
                .padding(start = 30.dp, top = 20.dp),
        ) { item ->
            Column {
                Text(
                    tx("ÖNE ÇIKAN", "FEATURED"),
                    color = lerp(item.app?.accent ?: VIOLET, Color.White, 0.6f),
                    fontSize = 10.sp,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    item.title,
                    color = Color.White,
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    letterSpacing = (-0.3).sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.tagline.uppercase(I18n.locale),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    item.description,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Düğmeler
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 30.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HeroButton(
                fm = fm,
                key = "hero_play",
                enabled = enabled,
                primary = true,
                label = current.primaryLabel,
                icon = Icons.Filled.PlayArrow,
                onSelect = { current.onPrimary() },
                onLeft = { step(-1) },
                onRight = if (current.onSecondary == null) ({ step(1) }) else null,
            )
            val secondary = current.onSecondary
            if (secondary != null) {
                HeroButton(
                    fm = fm,
                    key = "hero_info",
                    enabled = enabled,
                    primary = false,
                    label = "Seçenekler",
                    icon = Icons.Filled.Info,
                    onSelect = secondary,
                    onLeft = null,
                    onRight = { step(1) },
                )
            }
        }

        // Etiketler ve sayfa noktaları
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(current.tags, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == index) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (i == index) Color.White else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroBackground(item: HeroItem) {
    val accent = item.app?.accent ?: VIOLET
    val (top, bottom) = vividPair(accent)
    Box(Modifier.fillMaxSize()) {
        if (item.image != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(bottom, Color(0xFF0B0F24))))
            )
            AsyncImage(
                model = artModel(item.image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(lerp(bottom, Color.Black, 0.35f), Color(0xFF0B0F24), lerp(top, Color.Black, 0.3f))))
            )
            item.app?.backdrop?.let {
                Image(
                    it, null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 0.55f
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        },
                )
            }
            // Dekoratif ışık halkaları
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(listOf(top.copy(alpha = 0.55f), Color.Transparent), radius = 700f))
            )
            item.app?.banner?.let { banner ->
                Image(
                    banner, null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 40.dp)
                        .fillMaxHeight(0.72f)
                        .aspectRatio(16f / 9f)
                        .graphicsLayer { rotationZ = -2f }
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xFF05070F).copy(alpha = 0.88f),
                        0.42f to Color(0xFF05070F).copy(alpha = 0.55f),
                        0.72f to Color.Transparent,
                    )
                )
        )
    }
}

@Composable
private fun HeroButton(
    fm: FocusMemory,
    key: String,
    enabled: Boolean,
    primary: Boolean,
    label: String,
    icon: ImageVector,
    onSelect: () -> Unit,
    onLeft: (() -> Boolean)?,
    onRight: (() -> Boolean)?,
) {
    RegisterKey(fm, key)
    val motion = LocalMotion.current
    val sounds = LocalSounds.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.08f else 1f,
        spring(dampingRatio = motion.tileDamping, stiffness = motion.tileStiffness),
        label = "heroBtn",
    )
    val shape = RoundedCornerShape(50)
    val textColor = if (focused) Color(0xFF0E1222) else Color.White
    Row(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .then(
                when {
                    focused -> Modifier.background(Color.White)
                    primary -> Modifier.background(PRIMARY_BRUSH)
                    else -> Modifier
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), shape)
                }
            )
            .track(fm, key)
            .onFocusChanged {
                if (it.isFocused && !focused) sounds?.play(Sfx.NAV)
                focused = it.isFocused
            }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> onLeft?.invoke() ?: false
                    Key.DirectionRight -> onRight?.invoke() ?: false
                    else -> false
                }
            }
            .onRemoteSelect(onSelect)
            .focusable(enabled = enabled)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = textColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ------------------------------------------------------------------
// Kartlar
// ------------------------------------------------------------------

/** Devam kartlarını birbirinden ayırmak için renk paleti: her içerik kimliğine sabit bir renk düşer. */
private val CONTINUE_TINTS = listOf(
    Color(0xFF4D8DFF), Color(0xFFFF5C8A), Color(0xFF2ED3A0), Color(0xFFFFB13D),
    Color(0xFFA96BFF), Color(0xFF3DD6FF), Color(0xFFFF7A45),
)

private val CARD_SHADOW = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 1.5f), 6f)

/**
 * Görüntünün yalnızca alt kısmını çizer (yazı bandı) ve üstüne ince bir ışık çizgisi koyar.
 * Altına konan küçük çözünürlüklü kopya ile birlikte buzlu cam etkisi verir.
 */
private fun Modifier.frostedBand(from: Float = 0.45f): Modifier = this.drawWithContent {
    val top = size.height * from
    clipRect(top = top) { this@drawWithContent.drawContent() }
    drawRect(Color.Black.copy(alpha = 0.18f), topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
    drawLine(Color.White.copy(alpha = 0.14f), Offset(0f, top), Offset(size.width, top), 1f)
}

private fun tintFor(key: String): Color = CONTINUE_TINTS[(key.hashCode() and 0x7fffffff) % CONTINUE_TINTS.size]

/** "az önce", "5 dk önce", "3 sa önce", "dün", "4 gün önce". */
fun agoText(updatedAt: Long): String {
    val diff = (System.currentTimeMillis() - updatedAt).coerceAtLeast(0)
    val min = diff / 60_000
    val hour = min / 60
    val day = hour / 24
    return when {
        min < 1 -> tx("az önce", "just now")
        min < 60 -> tx("$min dk önce", "$min min ago")
        hour < 24 -> tx("$hour sa önce", "$hour h ago")
        day < 2 -> tx("dün", "yesterday")
        else -> tx("$day gün önce", "$day days ago")
    }
}

/** Kaldığı dakika / kalan süre metni. */
fun positionText(p: PlayedItem): String? = when {
    p.duration > 0 && p.position > 0 -> {
        val left = ((p.duration - p.position) / 60_000).coerceAtLeast(1)
        tx("$left dk kaldı", "$left min left")
    }
    p.position > 60_000 -> {
        val at = p.position / 60_000
        tx("$at. dakikada kaldın", "stopped at $at min")
    }
    else -> null
}

/**
 * Devam kartı. Aynı uygulamadan birden fazla içerik varsa her biri kendi rengini, sıra numarasını,
 * kaldığı dakikayı ve ne zaman izlendiğini gösterir; başlığı olmayan içerikler de böylece ayırt edilir.
 * Odaktaki kartın uzun başlığı kayarak akar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCard(
    item: StripItem,
    fm: FocusMemory,
    key: String,
    enabled: Boolean,
    onSelect: (StripItem) -> Unit,
    sameAppIndex: Int = 0,
    sameAppCount: Int = 1,
) {
    val app = when (item) {
        is StripItem.Played -> item.app
        is StripItem.Content -> item.app
        is StripItem.Recent -> item.app
    }
    val tint = tintFor(item.key)
    val (title, subtitle, image, progress) = when (item) {
        is StripItem.Played -> {
            val p = item.item
            val parts = listOfNotNull(
                p.subtitle.takeIf { !p.anonymous || it != null },
                positionText(p),
                agoText(p.updatedAt),
            )
            CardText(
                if (p.anonymous) (app?.label ?: p.title) else p.title,
                parts.joinToString(" · "),
                p.art,
                p.progress,
            )
        }
        is StripItem.Content -> CardText(item.item.title, item.item.subtitle ?: (app?.label ?: ""), item.item.imageUri, item.item.progress)
        is StripItem.Recent -> CardText(item.app.label, tx("Son kullanılan", "Recently used"), null, null)
    }
    FocusCard(
        fm = fm, key = key, enabled = enabled, glow = if (sameAppCount > 1) tint else (app?.accent ?: tint),
        onSelect = { onSelect(item) },
        modifier = Modifier
            .width(214.dp)
            .height(100.dp),
    ) { focused ->
        when {
            image != null -> {
                val (top, bottom) = vividPair(app?.accent ?: tint)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(top, bottom)))
                )
                AsyncImage(
                    model = artModel(image),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Yazının olduğu alt kısım: aynı görselin çok küçük çözülüp büyütülmüş hali = buzlu cam.
                // Canlı blur kullanmaz, TV'yi yormaz.
                val ctx = LocalPlatformContext.current
                val tiny = remember(image) {
                    ImageRequest.Builder(ctx)
                        .data(artModel(image))
                        .size(28, 16)
                        .precision(Precision.EXACT)
                        .build()
                }
                AsyncImage(
                    model = tiny,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low,
                    modifier = Modifier
                        .fillMaxSize()
                        .frostedBand(),
                )
            }
            app?.banner != null -> {
                AppArt(app, Modifier.fillMaxSize())
                app.backdrop?.let { b ->
                    Image(
                        b, null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Low,
                        modifier = Modifier
                            .fillMaxSize()
                            .frostedBand(),
                    )
                }
            }
            app != null -> IconCardArt(app, focused = false, compact = false, showText = false)
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(tint, lerp(tint, Color.Black, 0.6f))))
            )
        }
        // Renk kimliği: soldan kartın rengine boyanan degrade + sol kenarda renkli şerit
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to tint.copy(alpha = if (sameAppCount > 1) 0.55f else 0.25f),
                        0.65f to Color.Transparent,
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.42f to Color.Black.copy(alpha = 0.12f),
                        0.48f to Color.Black.copy(alpha = 0.38f),
                        1f to Color.Black.copy(alpha = 0.78f),
                    )
                )
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(4.dp)
                .fillMaxHeight()
                .background(tint)
        )
        // Sol üst: uygulama ikonu
        if (app != null && (image != null || sameAppCount > 1)) {
            Image(
                app.icon, null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 11.dp, top = 8.dp)
                    .size(22.dp)
                    .softShadow(6.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
        }
        // Sağ üst: aynı uygulamadan birden fazla içerik varsa sıra numarası
        if (sameAppCount > 1) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .softShadow(12.dp)
                    .clip(CircleShape)
                    .background(tint)
                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("${sameAppIndex + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 10.dp, bottom = 8.dp)
        ) {
            Text(
                title,
                style = LocalTextStyle.current.copy(shadow = CARD_SHADOW),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.1).sp,
                maxLines = 1,
                overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 600) else Modifier,
            )
            Text(
                subtitle,
                style = LocalTextStyle.current.copy(shadow = CARD_SHADOW),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 900) else Modifier,
            )
            if (progress != null && progress > 0.01f) {
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(Brush.horizontalGradient(listOf(tint, lerp(tint, Color.White, 0.35f))))
                    )
                }
            }
        }
    }
}

private data class CardText(val title: String, val subtitle: String, val image: String?, val progress: Float?)

/**
 * Uygulama kartı: arkada uygulamanın ikonu büyük ve bulanık (önceden pişirilmiş), üstünde koyu degrade;
 * sol üstte gölgeli küçük ikon, altta ad ve kategori.
 */
@Composable
private fun AppColorTile(
    app: LauncherApp,
    fm: FocusMemory,
    key: String,
    enabled: Boolean,
    modifier: Modifier,
    onLaunchApp: (LauncherApp) -> Unit,
    onAppMenu: (LauncherApp) -> Unit,
    compact: Boolean = false,
) {
    FocusCard(
        fm = fm, key = key, enabled = enabled, glow = app.accent,
        onSelect = { onLaunchApp(app) },
        onLongPress = { onAppMenu(app) },
        modifier = modifier,
        corner = 16.dp,
    ) { focused ->
        IconCardArt(app, focused, compact = compact)
    }
}

/** Medya izni kapalıyken "devam et" satırının başında yol gösteren kart. */
@Composable
private fun MediaAccessHint(fm: FocusMemory, enabled: Boolean, onSelect: () -> Unit) {
    FocusCard(
        fm = fm, key = "media_hint", enabled = enabled, glow = BLUE,
        onSelect = onSelect,
        modifier = Modifier
            .width(260.dp)
            .height(96.dp),
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFF26304F), Color(0xFF151A2C))))
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PRIMARY_BRUSH),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tx("Netflix, YouTube, Prime…", "Netflix, YouTube, Prime…"),
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    tx(
                        "İzlediklerin burada çıksın: Ayarlar → Medya geçmişi iznini aç",
                        "Show what you watch here: Settings → turn on Media history",
                    ),
                    color = Color.White.copy(alpha = if (focused) 0.9f else 0.7f),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AllAppsTile(fm: FocusMemory, enabled: Boolean, onSelect: () -> Unit) {
    FocusCard(
        fm = fm, key = "all_apps", enabled = enabled, glow = Color.White,
        onSelect = onSelect,
        modifier = Modifier
            .width(132.dp)
            .height(76.dp),
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .background(if (focused) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.07f))
        )
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GridGlyph(Color.White, 22.dp)
            Spacer(Modifier.height(6.dp))
            Text(tx("Tümü", "All"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RecommendationCard(
    rec: Recommendation,
    fm: FocusMemory,
    key: String,
    enabled: Boolean,
    onGet: (StoreApp) -> Unit,
    modifier: Modifier = Modifier
        .width(180.dp)
        .height(96.dp),
) {
    val (top, bottom) = REC_COLORS[rec.sectionIndex % REC_COLORS.size]
    FocusCard(
        fm = fm, key = key, enabled = enabled, glow = top,
        onSelect = { onGet(rec.app) },
        modifier = modifier,
    ) { _ ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(top, bottom)))
        )
        Box(
            Modifier
                .size(120.dp)
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-40).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
        )
        Text(
            rec.app.name.split(' ', '+').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() },
            color = Color.White.copy(alpha = 0.95f),
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 8.dp),
        )
        Text(
            tx("Play'den yükle", "Get on Play"),
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.3f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 10.dp, bottom = 8.dp)
        ) {
            Text(rec.app.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(rec.app.description, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ------------------------------------------------------------------
// Uygulamalar / Oyunlar sekmeleri
// ------------------------------------------------------------------

@Composable
private fun AppGridTab(
    title: String,
    apps: List<LauncherApp>,
    fm: FocusMemory,
    enabled: Boolean,
    onLaunchApp: (LauncherApp) -> Unit,
    onAppMenu: (LauncherApp) -> Unit,
    trailing: (() -> Unit)?,
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "$title  ·  ${apps.size}",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 10.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(apps, key = { _, it -> it.packageName }) { i, app ->
                AppColorTile(
                    app, fm, "grid_$i", enabled,
                    Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    onLaunchApp, onAppMenu,
                )
            }
            if (trailing != null) {
                item(key = "more_games") {
                    FocusCard(
                        fm = fm, key = "more_games", enabled = enabled, glow = Color.White,
                        onSelect = trailing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                    ) { _ ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.White.copy(alpha = 0.08f))
                        )
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            GamepadGlyph(Color.White, 26.dp)
                            Spacer(Modifier.height(4.dp))
                            Text(tx("Daha fazla oyun", "More games"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/** Oyunlar sekmesi: önce dahili oyunlar, altında mağazadan indirilen oyunlar. */
@Composable
private fun GamesTab(
    games: List<LauncherApp>,
    fm: FocusMemory,
    enabled: Boolean,
    onPlayGame: (BuiltInGame) -> Unit,
    onLaunchApp: (LauncherApp) -> Unit,
    onAppMenu: (LauncherApp) -> Unit,
    onFindGames: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "builtin") {
            Section(tx("Oyunlar", "Games")) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(BuiltInGame.entries.filter { !it.isTool }, key = { _, g -> g.name }) { i, g ->
                        BuiltInGameCard(g, fm, "bg_$i", enabled) { onPlayGame(g) }
                    }
                }
            }
        }
        item(key = "tools") {
            Section(tx("Araçlar", "Tools")) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(BuiltInGame.entries.filter { it.isTool }, key = { _, g -> g.name }) { i, g ->
                        BuiltInGameCard(g, fm, "tool_$i", enabled) { onPlayGame(g) }
                    }
                }
            }
        }
        item(key = "installed") {
            Section(tx("Mağazadan indirdiğin oyunlar", "Downloaded games")) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(games, key = { _, it -> it.packageName }) { i, app ->
                        AppColorTile(
                            app, fm, "game_$i", enabled,
                            Modifier
                                .width(160.dp)
                                .height(90.dp),
                            onLaunchApp, onAppMenu,
                        )
                    }
                    item(key = "find_games") {
                        FocusCard(
                            fm = fm, key = "find_games", enabled = enabled, glow = Color.White,
                            onSelect = onFindGames,
                            modifier = Modifier
                                .width(160.dp)
                                .height(90.dp),
                        ) { focused ->
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.White.copy(alpha = if (focused) 0.16f else 0.07f))
                            )
                            Column(
                                Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                GamepadGlyph(Color.White, 26.dp)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (games.isEmpty()) tx("Play'den oyun bul", "Find games on Play") else tx("Daha fazla oyun", "More games"),
                                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Dahili oyun kartı: oyunun renginde degrade, büyük silik simge, ad, açıklama ve "Oyna". */
@Composable
private fun BuiltInGameCard(game: BuiltInGame, fm: FocusMemory, key: String, enabled: Boolean, onSelect: () -> Unit) {
    FocusCard(
        fm = fm, key = key, enabled = enabled, glow = game.top,
        onSelect = onSelect,
        modifier = Modifier
            .width(210.dp)
            .height(124.dp),
        corner = 20.dp,
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(game.top, game.bottom)))
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 26.dp)
                .graphicsLayer {
                    alpha = 0.22f
                    rotationZ = -12f
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
        ) { GameGlyph(game, 120.dp) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f)))
        )
        Box(
            Modifier
                .padding(12.dp)
                .size(34.dp)
                .softShadow(12.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { GameGlyph(game, 20.dp) }
        Text(
            if (game.isTool) tx("AÇ", "OPEN") else tx("OYNA", "PLAY"),
            color = if (focused) game.bottom else Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(50))
                .background(if (focused) Color.White else Color.Black.copy(alpha = 0.25f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
        ) {
            Text(game.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp)
            Text(
                game.description,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyGames(fm: FocusMemory, enabled: Boolean, onFindGames: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(420.dp)
                .glass(RoundedCornerShape(28.dp), strong = true)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GamepadGlyph(Color.White, 54.dp)
            Spacer(Modifier.height(12.dp))
            Text(tx("Henüz oyun yok", "No games yet"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                tx("Yüklediğin oyunlar burada görünecek.", "Games you install will show up here."),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(18.dp))
            HeroButton(
                fm = fm,
                key = "find_games",
                enabled = enabled,
                primary = true,
                label = tx("Google Play'de oyun bul", "Find games on Google Play"),
                icon = Icons.Filled.Search,
                onSelect = onFindGames,
                onLeft = null,
                onRight = null,
            )
        }
    }
}

// ------------------------------------------------------------------
// Arama
// ------------------------------------------------------------------

/**
 * Tam ekran arama: yazdıkça yüklü uygulamalar ve mağazadaki uygulamalar süzülür.
 * Metin alanına odaklanınca TV klavyesi açılır; aşağı ok ile sonuçlara inilir.
 */
@Composable
fun SearchScreen(
    apps: List<LauncherApp>,
    enabled: Boolean,
    onLaunchApp: (LauncherApp) -> Unit,
    onAppMenu: (LauncherApp) -> Unit,
    onGetFromPlay: (StoreApp) -> Unit,
) {
    val fm = remember { FocusMemory() }
    var query by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { field.requestFocus() }
        keyboard?.show()
    }
    val tr = java.util.Locale.forLanguageTag("tr")
    val q = query.trim().lowercase(tr)
    val matched = remember(q, apps) {
        if (q.isEmpty()) apps.sortedBy { it.label.lowercase(tr) }
        else apps.filter { it.label.lowercase(tr).contains(q) }
    }
    val storeMatches = remember(q, apps) {
        if (q.isEmpty()) emptyList()
        else dev.litedroid.launcher.data.StoreCatalog.sections
            .flatMapIndexed { i, s -> s.apps.map { Recommendation(it, i, s.title) } }
            .filter { it.app.name.lowercase(tr).contains(q) && dev.litedroid.launcher.data.StoreCatalog.findInstalled(it.app, apps) == null }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 32.dp, end = 32.dp, top = 30.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = { query = it.take(40) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Jakarta, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                    keyboard?.hide()
                    fm.restore("search_0")
                }),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(tx("Uygulama ara…", "Search apps…"), color = Color.White.copy(alpha = 0.45f), fontSize = 22.sp)
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .width(560.dp)
                    .focusRequester(field)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.1f))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (q.isEmpty()) tx("Tüm uygulamalar · ${matched.size}", "All apps · ${matched.size}") else tx("${matched.size} uygulama bulundu", "${matched.size} apps found"),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(matched, key = { _, it -> it.packageName }) { i, app ->
                AppColorTile(
                    app, fm, "search_$i", enabled,
                    Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    onLaunchApp, onAppMenu,
                )
            }
            if (storeMatches.isNotEmpty()) {
                item(key = "store_header", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text(
                        tx("Mağazada", "In the store"),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                itemsIndexed(storeMatches, key = { _, it -> "s_" + it.app.name }) { i, rec ->
                    RecommendationCard(
                        rec, fm, "search_store_$i", enabled, onGetFromPlay,
                        Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                    )
                }
            }
        }
    }
}
