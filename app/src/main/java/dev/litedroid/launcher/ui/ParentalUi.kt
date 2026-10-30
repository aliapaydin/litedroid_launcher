package dev.litedroid.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.data.ChildRules
import dev.litedroid.launcher.data.KidAi
import dev.litedroid.launcher.data.LauncherApp
import dev.litedroid.launcher.data.Parental
import dev.litedroid.launcher.data.Profile
import dev.litedroid.launcher.data.Profiles
import dev.litedroid.launcher.overlay.RemoteKeyService
import dev.litedroid.launcher.tools.ProfileFace
import dev.litedroid.launcher.tx
import kotlinx.coroutines.delay

private val ParentBg = Brush.verticalGradient(listOf(Color(0xFF0E1A2E), Color(0xFF080B16), Color(0xFF05050B)))
private val Mint = Color(0xFF7CFFCB)
private val Warn = Color(0xFFFFB4A8)

internal fun minutesLabel(m: Int): String = when {
    m <= 0 -> tx("Sınırsız", "Unlimited")
    m >= 60 && m % 60 == 0 -> tx("${m / 60} saat", "${m / 60} h")
    m > 60 -> tx("${m / 60} sa ${m % 60} dk", "${m / 60}h ${m % 60}m")
    else -> tx("$m dk", "$m min")
}

internal fun msLabel(ms: Long): String = minutesLabel(((ms + 59_999) / 60_000).toInt()).let { if (ms < 60_000) tx("0 dk", "0 min") else it }

/**
 * Ayarlar → Ebeveyn denetimi. Çocuk profili seçilir; bugünkü durum (kullanılan, kalan, kilit, istek),
 * hızlı işlemler (süre ekle, hemen kilitle), günlük süre (hafta içi / hafta sonu), izin verilen saatler,
 * uygulama izinleri ve süreleri, Claude, tarayıcı ve mağaza.
 * Bir çocuğa sınır konunca PIN'i olmayan yetişkinlere önce PIN belirletilir.
 */
@Composable
fun ParentalScreen(apps: List<LauncherApp>, onClose: () -> Unit, onAddChild: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val profiles by Profiles.list.collectAsState()
    val version by Parental.version.collectAsState()
    val children = profiles.filter { it.isChild }
    var selected by remember { mutableStateOf(children.firstOrNull()?.id) }
    if (selected == null || children.none { it.id == selected }) selected = children.firstOrNull()?.id
    var appsPage by remember { mutableStateOf(false) }
    var phoneOpen by remember { mutableStateOf(false) }
    var dropdown by remember { mutableStateOf<Dropdown?>(null) }
    var pendingRules by remember { mutableStateOf<Pair<String, ChildRules>?>(null) }
    var pinFor by remember { mutableStateOf<Pair<Profile, String?>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val firstFocus = remember { FocusRequester() }
    // Süreler canlı akar
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            tick++
        }
    }
    LaunchedEffect(appsPage) {
        delay(150)
        runCatching { firstFocus.requestFocus() }
    }
    BackHandler(enabled = pinFor == null && !phoneOpen && dropdown == null) {
        if (appsPage) appsPage = false else onClose()
    }

    /** Kuralı kaydeder; sınır varsa ve PIN'siz yetişkin varsa önce PIN istenir. */
    fun save(pid: String, r: ChildRules) {
        val missing = Parental.adultsWithoutPin()
        if (r.hasLimits && missing.isNotEmpty()) {
            pendingRules = pid to r
            pinFor = missing.first() to null
            message = tx("Çocuğa sınır konunca yetişkin profillerinin PIN'i zorunlu: önce ${missing.first().name} için PIN belirle.",
                "Kid limits need PINs on adult profiles: set a PIN for ${missing.first().name} first.")
            return
        }
        Parental.setRules(ctx, pid, r)
    }

    Box(modifier.fillMaxSize().background(ParentBg)) {
        val child = children.firstOrNull { it.id == selected }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 72.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Kural ya da kullanım değişince (ve 5 sn'de bir) satırlar yeniden okunur
            if (version + tick < 0) return@LazyColumn
            item(key = "head") {
                Column {
                    Text(if (appsPage && child != null) tx("${child.name} · Uygulamalar", "${child.name} · Apps") else tx("Ebeveyn denetimi", "Parental controls"),
                        color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (appsPage) tx("Tamam'a basarak sırayla: açık → günlük 15/30/45/60/90/120 dk → kapalı. Kapalı uygulama açılınca uyarı verilir, açılmaz.",
                            "Press OK to cycle: allowed → 15/30/45/60/90/120 min a day → blocked. Blocked apps show a notice and don't open.")
                        else tx("Çocuk profillerinin günlük süresi, saatleri ve uygulamaları. Gün sabah 06:00'da sıfırlanır.",
                            "Daily time, hours and apps for kid profiles. The day resets at 06:00."),
                        color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
                    )
                    if (!RemoteKeyService.isEnabled(ctx)) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            tx("⚠ Kumanda kısayolu servisi kapalı: süre ve uygulama sınırları uygulanamaz. Ayarlar → Kumanda'dan aç.",
                                "⚠ The remote shortcut service is off: time and app limits can't be enforced. Turn it on in Settings → Remote."),
                            color = Color(0xFF14121F), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Warn).padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Color(0xFFFFD35C), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            if (children.isEmpty()) {
                item(key = "none") {
                    PRow(tx("＋  Çocuk profili ekle", "＋  Add a kid profile"), null, tx("Önce Profiller'den bir çocuk profili oluştur", "Create a kid profile in Profiles first"), Modifier.focusRequester(firstFocus)) { onAddChild() }
                }
                return@LazyColumn
            }
            if (child == null) return@LazyColumn
            val rules = Parental.rules(ctx, child.id)

            if (appsPage) {
                val sorted = apps.sortedWith(compareByDescending<LauncherApp> { it.packageName in rules.blocked }.thenBy { it.label.lowercase() })
                items(sorted, key = { it.packageName }) { app ->
                    val blocked = app.packageName in rules.blocked
                    val limit = rules.appLimits[app.packageName] ?: 0
                    val used = Parental.appUsedMs(ctx, child.id, app.packageName)
                    PRow(
                        app.label,
                        when {
                            blocked -> tx("Kapalı", "Blocked")
                            limit > 0 -> tx("Günde ${minutesLabel(limit)}", "${minutesLabel(limit)} a day")
                            else -> tx("Açık", "Allowed")
                        },
                        if (used > 0) tx("Bugün ${msLabel(used)}", "Today ${msLabel(used)}") else null,
                        if (app.packageName == sorted.first().packageName) Modifier.focusRequester(firstFocus) else Modifier,
                        valueColor = if (blocked) Warn else if (limit > 0) Color(0xFFFFD35C) else Mint,
                        icon = { Image(app.icon, null, Modifier.size(36.dp).clip(RoundedCornerShape(9.dp))) },
                    ) {
                        // açık → 15 → 30 → … → 120 → kapalı → açık
                        val next: ChildRules = when {
                            blocked -> rules.copy(blocked = rules.blocked - app.packageName, appLimits = rules.appLimits - app.packageName)
                            else -> {
                                val opts = Parental.APP_LIMIT_OPTIONS
                                val i = opts.indexOf(limit)
                                if (i < 0 || i == opts.lastIndex) rules.copy(blocked = rules.blocked + app.packageName, appLimits = rules.appLimits - app.packageName)
                                else rules.copy(appLimits = rules.appLimits + (app.packageName to opts[i + 1]))
                            }
                        }
                        save(child.id, next)
                    }
                }
                return@LazyColumn
            }

            // ---- Çocuk seçimi ----
            if (children.size > 1) {
                item(key = "kids") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(children, key = { it.id }) { c ->
                            KidChip(c, c.id == child.id, if (c.id == children.first().id) Modifier.focusRequester(firstFocus) else Modifier) { selected = c.id }
                        }
                    }
                }
            }

            // ---- Bugün ----
            item(key = "today_${child.id}") {
                val used = Parental.usedMs(ctx, child.id)
                val limit = Parental.limitMin(ctx, child.id)
                val bonus = Parental.bonusMin(ctx, child.id)
                val rem = Parental.remainingMs(ctx, child.id)
                val reason = Parental.lockReason(ctx, child.id)
                val req = Parental.pendingRequest(ctx, child.id)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(22.dp)).padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfileFace(child, 52.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tx("${child.name} · bugün", "${child.name} · today"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                            Text(
                                listOfNotNull(
                                    tx("${msLabel(used)} kullandı", "used ${msLabel(used)}"),
                                    if (limit > 0) tx("sınır ${minutesLabel(limit)}${if (bonus > 0) " + $bonus dk" else ""}", "limit ${minutesLabel(limit)}${if (bonus > 0) " + $bonus min" else ""}") else tx("süre sınırı yok", "no time limit"),
                                ).joinToString(" · "),
                                color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                when {
                                    reason != null -> tx("🔒 Kilitli", "🔒 Locked")
                                    rem != null -> tx("${msLabel(rem)} kaldı", "${msLabel(rem)} left")
                                    else -> tx("Açık", "Open")
                                },
                                color = if (reason != null) Warn else Mint, fontSize = 22.sp, fontWeight = FontWeight.Black,
                            )
                            if (limit > 0) {
                                val frac = (used.toFloat() / ((limit + bonus) * 60_000f)).coerceIn(0f, 1f)
                                Box(Modifier.width(160.dp).height(8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))) {
                                    Box(Modifier.fillMaxWidth(frac).height(8.dp).background(if (frac >= 1f) Warn else Mint))
                                }
                            }
                        }
                    }
                    if (req != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            tx("🙏 ${child.name} ${req.second} dakika daha istiyor", "🙏 ${child.name} is asking for ${req.second} more minutes"),
                            color = Color(0xFFFFD35C), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            item(key = "actions_${child.id}") {
                val reason = Parental.lockReason(ctx, child.id)
                val req = Parental.pendingRequest(ctx, child.id)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                    if (req != null) {
                        item { ActionChip(tx("✓ İsteği onayla (+${req.second} dk)", "✓ Approve (+${req.second} min)"), Mint, if (children.size == 1) Modifier.focusRequester(firstFocus) else Modifier) {
                            Parental.addBonus(ctx, child.id, req.second)
                            message = tx("${child.name} için ${req.second} dk eklendi", "Added ${req.second} min for ${child.name}")
                        } }
                        item { ActionChip(tx("✕ Reddet", "✕ Decline"), Warn) { Parental.clearRequest(ctx, child.id) } }
                    }
                    items(Parental.ADD_OPTIONS) { m ->
                        ActionChip(tx("＋$m dk", "＋$m min"), Color.White, if (req == null && m == Parental.ADD_OPTIONS.first() && children.size == 1) Modifier.focusRequester(firstFocus) else Modifier) {
                            Parental.addBonus(ctx, child.id, m)
                            message = tx("${child.name} için $m dk eklendi", "Added $m min for ${child.name}")
                        }
                    }
                    item {
                        if (reason == null) ActionChip(tx("🔒 Hemen kilitle", "🔒 Lock now"), Warn) {
                            Parental.lockNow(ctx, child.id)
                            message = tx("${child.name} kilitlendi", "${child.name} is locked")
                        } else ActionChip(tx("🔓 Kilidi aç", "🔓 Unlock"), Mint) {
                            Parental.unlock(ctx, child.id)
                            message = tx("Kilit açıldı. Süre dolduysa süre eklemen gerekir.", "Unlocked. If time is up, add time too.")
                        }
                    }
                }
            }

            // ---- Kurallar ----
            item(key = "wd_${child.id}") {
                PRow(tx("Günlük süre · hafta içi", "Daily time · weekdays"), minutesLabel(rules.weekdayMin) + "  ▾", tx("Pazartesi-Cuma", "Monday-Friday")) {
                    dropdown = Dropdown(tx("Hafta içi günlük süre", "Weekday daily time"), Parental.DAILY_OPTIONS.map { minutesLabel(it) },
                        Parental.DAILY_OPTIONS.indexOf(rules.weekdayMin)) { i -> save(child.id, Parental.rules(ctx, child.id).copy(weekdayMin = Parental.DAILY_OPTIONS[i])) }
                }
            }
            item(key = "we_${child.id}") {
                PRow(tx("Günlük süre · hafta sonu", "Daily time · weekend"), minutesLabel(rules.weekendMin) + "  ▾", tx("Cumartesi-Pazar", "Saturday-Sunday")) {
                    dropdown = Dropdown(tx("Hafta sonu günlük süre", "Weekend daily time"), Parental.DAILY_OPTIONS.map { minutesLabel(it) },
                        Parental.DAILY_OPTIONS.indexOf(rules.weekendMin)) { i -> save(child.id, Parental.rules(ctx, child.id).copy(weekendMin = Parental.DAILY_OPTIONS[i])) }
                }
            }
            item(key = "hours_${child.id}") {
                PRow(tx("Ekran saatleri", "Screen hours"),
                    if (rules.hoursOn) "${Parental.hhmm(rules.fromMin)} - ${Parental.hhmm(rules.toMin)}" else tx("Kapalı", "Off"),
                    tx("Bu saatlerin dışında süre kalsa bile kilitlenir", "Outside these hours it locks even with time left")) {
                    save(child.id, rules.copy(hoursOn = !rules.hoursOn))
                }
            }
            if (rules.hoursOn) {
                item(key = "from_${child.id}") {
                    PRow(tx("   Başlangıç", "   From"), Parental.hhmm(rules.fromMin) + "  ▾", null) {
                        dropdown = Dropdown(tx("Ekran saati başlangıcı", "Screen time starts"), FROM_OPTIONS.map { Parental.hhmm(it) },
                            FROM_OPTIONS.indexOf(rules.fromMin)) { i -> save(child.id, Parental.rules(ctx, child.id).copy(fromMin = FROM_OPTIONS[i])) }
                    }
                }
                item(key = "to_${child.id}") {
                    PRow(tx("   Bitiş (yatma saati)", "   Until (bedtime)"), Parental.hhmm(rules.toMin) + "  ▾", null) {
                        dropdown = Dropdown(tx("Yatma saati", "Bedtime"), TO_OPTIONS.map { Parental.hhmm(it) },
                            TO_OPTIONS.indexOf(rules.toMin)) { i -> save(child.id, Parental.rules(ctx, child.id).copy(toMin = TO_OPTIONS[i])) }
                    }
                }
            }
            item(key = "apps_${child.id}") {
                val off = rules.blocked.size
                PRow(tx("Uygulamalar", "Apps"), tx("$off kapalı · ${rules.appLimits.size} süreli", "$off blocked · ${rules.appLimits.size} timed"),
                    tx("Hangi uygulamalar açılabilir, hangisi günde ne kadar", "Which apps can open and for how long a day")) { appsPage = true }
            }
            item(key = "phone") {
                val n = dev.litedroid.launcher.tools.ParentServer.phones(ctx).size
                PRow(tx("📱 Telefon paneli", "📱 Phone panel"), if (n > 0) tx("$n telefon", "$n phones") else tx("Eşleştir", "Pair"),
                    tx("Telefondan süreyi gör, süre ekle, hemen kapat, istekleri onayla (evdeki Wi-Fi'da)", "See time, add time, lock now and approve requests from your phone (home Wi-Fi)")) { phoneOpen = true }
            }
            item(key = "ai_${child.id}") {
                PRow(tx("Claude'a sor", "Ask Claude"), rules.ai.label,
                    tx("Çocuk modu: yaşa uygun, kısa cevaplar, web araması yok", "Kids mode: age-appropriate short answers, no web search")) {
                    save(child.id, rules.copy(ai = if (rules.ai == KidAi.KIDS) KidAi.OFF else KidAi.KIDS))
                }
            }
            item(key = "browser_${child.id}") {
                PRow(tx("Tarayıcı", "Browser"), if (rules.browser) tx("İzinli", "Allowed") else tx("Kapalı", "Off"), null) {
                    save(child.id, rules.copy(browser = !rules.browser))
                }
            }
            item(key = "store_${child.id}") {
                PRow(tx("Uygulama mağazası", "App store"), if (rules.store) tx("İzinli", "Allowed") else tx("Kapalı", "Off"), null) {
                    save(child.id, rules.copy(store = !rules.store))
                }
            }
        }

        if (phoneOpen) PhonePanelDialog { phoneOpen = false }
        dropdown?.let { d ->
            DropdownPicker(d, onDismiss = { dropdown = null }) { i ->
                dropdown = null
                d.onPick(i)
            }
        }

        // Yetişkin PIN'i zorunlu: sırayla belirlenir, bitince bekleyen kural kaydedilir
        pinFor?.let { (adult, first) ->
            key(adult.id, first) {
                PinDialog(
                    title = if (first == null) tx("${adult.name} için yeni PIN", "New PIN for ${adult.name}") else tx("PIN'i tekrar gir", "Enter the PIN again"),
                    subtitle = tx("Çocuk sınırları için yetişkin PIN'i zorunlu", "Adult PINs are required for kid limits"),
                    face = adult,
                    check = { pin -> first == null || pin == first },
                    onSuccess = { pin ->
                        if (first == null) pinFor = adult to pin else {
                            Profiles.setPin(ctx, adult.id, pin)
                            val rest = Parental.adultsWithoutPin()
                            if (rest.isNotEmpty()) pinFor = rest.first() to null else {
                                pinFor = null
                                pendingRules?.let { (pid, r) -> Parental.setRules(ctx, pid, r) }
                                pendingRules = null
                                message = tx("PIN kaydedildi, sınır uygulandı", "PIN saved, limit applied")
                            }
                        }
                    },
                    onCancel = {
                        pinFor = null
                        pendingRules = null
                        message = tx("PIN belirlenmedi, sınır kaydedilmedi", "No PIN set, the limit wasn't saved")
                    },
                )
            }
        }
    }
}

private val FROM_OPTIONS = (12..24).map { it * 30 }          // 06:00 … 12:00
private val TO_OPTIONS = (34..47).map { it * 30 }            // 17:00 … 23:30

private fun <T> List<T>.next(current: T): T = this[(indexOf(current) + 1).mod(size)]

@Composable
private fun KidChip(p: Profile, selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(when { focused -> Color.White; selected -> Color.White.copy(alpha = 0.2f); else -> Color.White.copy(alpha = 0.06f) })
            .border(if (selected) 2.dp else 0.dp, Mint, RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onSelect)
            .focusable()
            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileFace(p, 34.dp)
        Spacer(Modifier.width(8.dp))
        Text(p.name, color = if (focused) Color(0xFF14121F) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ActionChip(label: String, accent: Color, modifier: Modifier = Modifier, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label, color = if (focused) Color(0xFF14121F) else accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onSelect)
            .focusable()
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun PRow(
    title: String,
    value: String?,
    subtitle: String?,
    modifier: Modifier = Modifier,
    valueColor: Color = Mint,
    icon: (@Composable () -> Unit)? = null,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) Color.White else Color.White.copy(alpha = 0.06f), label = "bg")
    val ink = if (focused) Color(0xFF14121F) else Color.White
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onSelect)
            .focusable()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = ink.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) Text(value, color = if (focused) Color(0xFF14121F) else valueColor, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}

/**
 * "Kim İzliyor?" ekranında kilitli çocuk profili seçilince: neden kilitli, ebeveyninle konuş, süre iste.
 */
@Composable
fun LockedChildDialog(profile: Profile, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val reason = Parental.lockReason(ctx, profile.id)
    var requested by remember { mutableStateOf(Parental.pendingRequest(ctx, profile.id) != null) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { fr.requestFocus() }
    }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(520.dp).clip(RoundedCornerShape(30.dp)).background(Brush.verticalGradient(listOf(Color(0xFF221A44), Color(0xFF120F24))))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProfileFace(profile, 72.dp)
            Spacer(Modifier.height(12.dp))
            Text(tx("Ebeveyninle konuş", "Talk to your parent"), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text(
                (reason?.let { Parental.describe(it, Parental.rules(ctx, profile.id)) } ?: "") + " " +
                    tx("${profile.name} profili şimdilik kilitli.", "The ${profile.name} profile is locked for now."),
                color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!requested) {
                    ActionChip("🙏  " + tx("Süre iste", "Ask for time"), Color(0xFFFFD35C), Modifier.focusRequester(fr)) {
                        Parental.request(ctx, profile.id)
                        requested = true
                    }
                    ActionChip(tx("Tamam", "OK"), Color.White) { onClose() }
                } else {
                    ActionChip(tx("✓ İstek gönderildi · Tamam", "✓ Request sent · OK"), Mint, Modifier.focusRequester(fr)) { onClose() }
                }
            }
        }
    }
}

/**
 * Telefon paneli eşleştirme: QR (tek kullanımlık kod, 10 dk) ve eşleşmiş telefonlar.
 * Telefon QR'ı okutunca gizli bir anahtar alır; sonra her işlemde yetişkin PIN'i de sorulur.
 */
@Composable
private fun PhonePanelDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val url = remember { dev.litedroid.launcher.tools.ParentServer.newPairUrl(ctx) }
    val version by Parental.version.collectAsState()
    val phones = remember(version) { dev.litedroid.launcher.tools.ParentServer.phones(ctx) }
    val fr = remember { FocusRequester() }
    var confirmAll by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { fr.requestFocus() }
    }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(RoundedCornerShape(30.dp)).background(Brush.verticalGradient(listOf(Color(0xFF14263F), Color(0xFF0A1220))))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(30.dp)).padding(28.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(220.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(10.dp), contentAlignment = Alignment.Center) {
                    if (url != null) dev.litedroid.launcher.quiz.QrCode(url, 200.dp)
                    else Text(tx("Ağ bağlantısı yok", "No network"), color = Color(0xFF111114), fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(tx("QR 10 dakika ve bir kez geçerli", "The QR works once, for 10 minutes"), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            }
            Spacer(Modifier.width(26.dp))
            Column(Modifier.width(340.dp)) {
                Text(tx("Telefon paneli", "Phone panel"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(
                    tx("Telefonun kamerasıyla QR'ı okut. Açılan sayfayı ana ekrana ekleyebilirsin. Her işlemde bir yetişkin PIN'i sorulur; telefon TV ile aynı Wi-Fi'da olmalı.",
                        "Scan the QR with your phone camera and add the page to your home screen. An adult PIN is asked for actions; the phone must be on the TV's Wi-Fi."),
                    color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp,
                )
                Spacer(Modifier.height(14.dp))
                Text(tx("EŞLEŞMİŞ TELEFONLAR", "PAIRED PHONES"), color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(6.dp))
                if (phones.isEmpty()) Text(tx("Henüz yok", "None yet"), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                phones.forEach { ph ->
                    PRow("📱 " + ph.name, tx("Kaldır", "Remove"), null, valueColor = Warn) {
                        dev.litedroid.launcher.tools.ParentServer.forget(ctx, ph.token)
                        Parental.bumpExternal()
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionChip(tx("Tamam", "Done"), Mint, Modifier.focusRequester(fr)) { onClose() }
                    if (phones.isNotEmpty()) ActionChip(if (confirmAll) tx("Emin misin?", "Sure?") else tx("Tümünü kaldır", "Remove all"), Warn) {
                        if (!confirmAll) confirmAll = true else {
                            dev.litedroid.launcher.tools.ParentServer.forgetAll(ctx)
                            Parental.bumpExternal()
                            confirmAll = false
                        }
                    }
                }
            }
        }
    }
}

/** Açılır seçim listesi (günlük süre, saatler). */
private class Dropdown(val title: String, val options: List<String>, val current: Int, val onPick: (Int) -> Unit)

/**
 * Açılır liste: sağda bir panel; ▲▼ ile gezilir, Tamam seçer, Geri kapatır. Mevcut değer ✓ ile işaretli.
 */
@Composable
private fun DropdownPicker(d: Dropdown, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var sel by remember { mutableStateOf(d.current.coerceIn(0, d.options.lastIndex)) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (sel - 3).coerceAtLeast(0))
    val fr = remember { FocusRequester() }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { fr.requestFocus() }
    }
    LaunchedEffect(sel) {
        val first = listState.firstVisibleItemIndex
        val visible = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        if (sel < first + 1 || sel > first + visible - 2) runCatching { listState.animateScrollToItem((sel - 3).coerceAtLeast(0)) }
    }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .focusRequester(fr)
            .onPreviewKeyEvent { e ->
                if (e.key == androidx.compose.ui.input.key.Key.Back) return@onPreviewKeyEvent false
                val down = e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown
                when {
                    e.key.isSelectKey -> {
                        if (down && e.nativeKeyEvent.repeatCount == 0) armed = true
                        if (!down && armed) {
                            armed = false
                            onPick(sel)
                        }
                    }
                    down && e.key == androidx.compose.ui.input.key.Key.DirectionUp -> sel = (sel - 1).coerceAtLeast(0)
                    down && e.key == androidx.compose.ui.input.key.Key.DirectionDown -> sel = (sel + 1).coerceAtMost(d.options.lastIndex)
                }
                true
            }
            .focusable(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            Modifier
                .padding(end = 80.dp)
                .width(320.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1B2A44), Color(0xFF0E1626))))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(26.dp))
                .padding(16.dp),
        ) {
            Text(d.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
            LazyColumn(state = listState, modifier = Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(d.options.size) { i ->
                    val on = i == sel
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) Color.White else Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(d.options[i], color = if (on) Color(0xFF14121F) else Color.White, fontSize = 17.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (i == d.current) Text("✓", color = if (on) Color(0xFF1F8F6A) else Mint, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Text(tx("▲▼ seç · Tamam · Geri: vazgeç", "▲▼ choose · OK · Back: cancel"), color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
        }
    }
}
