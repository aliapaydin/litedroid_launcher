package dev.litedroid.launcher.overlay

import dev.litedroid.launcher.data.UpdateMode
import dev.litedroid.launcher.data.UpdateState
import dev.litedroid.launcher.data.Updater
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.media.LiveMediaHub
import androidx.compose.runtime.collectAsState
import android.os.Build
import android.os.SystemClock
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.litedroid.launcher.I18n
import dev.litedroid.launcher.MainActivity
import dev.litedroid.launcher.data.ScoresHub
import dev.litedroid.launcher.data.SettingsRepository
import dev.litedroid.launcher.ui.theme.LiteDroidTheme

/**
 * Kumanda kısayolu (erişilebilirlik hizmeti). Başka bir uygulama açıkken Geri'ye iki kez basınca
 * LiteDroid'in yüzen menüsünü açar. Tek basış (kısa bir beklemeyle) normal Geri olarak uygulamaya iletilir.
 * LiteDroid'in kendisi ya da ekran koruyucu öndeyken Geri tuşuna karışmaz.
 *
 * Home yönlendirme: bazı TV kutuları (ör. Botech Wzone) kendi launcher'ının kapatılmasına ve başka bir
 * ana ekran seçilmesine izin vermez. Varsayılan ana ekran başka bir uygulamaysa servis Home tuşunu
 * yutup LiteDroid'i açar; o ana ekran başka bir yoldan (açılış, uygulamadan çıkış) öne gelirse de
 * hemen LiteDroid'e geçer. LiteDroid zaten varsayılan ana ekransa hiçbir şey yapmaz.
 *
 * Açmak için: Ayarlar → Erişilebilirlik → LiteDroid kumanda kısayolu, ya da
 * adb shell settings put secure enabled_accessibility_services dev.litedroid.launcher/dev.litedroid.launcher.overlay.RemoteKeyService
 * adb shell settings put secure accessibility_enabled 1
 */
class RemoteKeyService : AccessibilityService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val handler = Handler(Looper.getMainLooper())
    private var menuView: ComposeView? = null
    private var launcherInFront = false

    // Uygulama kilidi: kilit ekranı penceresi, kilitlenen ve PIN'le açılan uygulama
    private var lockView: ComposeView? = null
    private var lockedPkg: String? = null
    private var unlockedPkg: String? = null

    // Mola hatırlatıcısı: öndeki uygulama ve ne zamandan beri önde
    private var fgPkg: String? = null
    /** Son kullanılan uygulamalar (en yeni başta): Input tuşuyla "son uygulamaya dön" için. */
    private val recentApps = ArrayDeque<String>()
    private var fgSince = 0L
    private var breakNotified = 0
    private val breakTick = object : Runnable {
        override fun run() {
            checkBreak()
            handler.postDelayed(this, 60_000)
        }
    }

    // Çift tıklama durumu
    private var backDownSeen = false
    private var pendingBack = false
    private var swallowNextUp = false
    /** Kendi gönderdiğimiz Geri olaylarını yeniden yakalamamak için. */
    private var passThroughUntil = 0L

    // Menü açılış/kapanış animasyonu
    private val menuVisible = androidx.compose.runtime.mutableStateOf(false)

    // Uyku zamanlayıcısı
    private var noticeView: ComposeView? = null
    private val notice = androidx.compose.runtime.mutableStateOf<SleepNotice?>(null)
    private val announced = HashSet<Int>()
    private var countingDown = false
    private var swallowUpAfterCancel = false
    private val hideNotice = Runnable { notice.value = null }
    private val sleepTick = object : Runnable {
        override fun run() = tickSleep()
    }

    // Home yönlendirme durumu
    private var foreignHome: String? = null
    private var foreignHomeCheckedAt = 0L
    private var lastRedirectAt = 0L

    /** İkinci basış gelmediyse ilk basış normal Geri olarak uygulamaya gider. */
    private val sendBackRunnable = Runnable {
        pendingBack = false
        sendBack()
    }

    private fun sendBack() {
        passThroughUntil = SystemClock.uptimeMillis() + 400
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        Log.i(TAG, "remote shortcut connected, default home: ${defaultHome(force = true)}")
        SleepTimer.onChange = { fromUser -> handler.post { onSleepChanged(fromUser) } }
        // Servis yeniden başladıysa (ör. güncelleme) "bitince kapat" izlemeye devam etsin
        if (SleepTimer.untilEnd.value) handler.post { onSleepChanged(false) }
        handler.removeCallbacks(breakTick)
        handler.postDelayed(breakTick, 60_000)
        watchUpdates()
        SleepTimer.load(this)
        onSleepChanged(fromUser = false)
    }

    // ------------------------------------------------------------------
    // Güncelleme bildirimi: başka bir uygulama açıkken sağ altta
    // ------------------------------------------------------------------

    private var announcedUpdate: String? = null

    /** Yeni sürüm bulununca (LiteDroid önde değilse) köşede bir kez haber verir. */
    private fun watchUpdates() {
        lifecycleScope.launch {
            Updater.state.collect { s ->
                val r = (s as? UpdateState.Available)?.release ?: return@collect
                if (announcedUpdate == r.version || launcherInFront || ScoreOverlay.launcherVisible.value) return@collect
                val mode = runCatching { SettingsRepository(this@RemoteKeyService).load().updateMode }.getOrDefault(UpdateMode.AUTO)
                if (mode == UpdateMode.OFF) return@collect
                announcedUpdate = r.version
                showNotice(
                    SleepNotice(
                        tx("LiteDroid ${r.version} çıktı", "LiteDroid ${r.version} is out"),
                        if (mode == UpdateMode.AUTO) tx("Ana ekrana dönünce güncellemek isteyip istemediğin sorulacak.", "You'll be asked to update when you're back on the home screen.")
                        else r.title,
                        icon = "⬆",
                    )
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Uyku zamanlayıcısı
    // ------------------------------------------------------------------

    // ---- İzlediğin bitince kapat ----
    private var sawPlaying = false
    private var stoppedSince = 0L
    private var endCountdown = 0
    private val contentTick = object : Runnable {
        override fun run() = tickContent()
    }
    private val endCountdownTick = object : Runnable {
        override fun run() = tickEndCountdown()
    }

    /** Herhangi bir uygulama şu an oynatıyor mu (medya oturumları; bildirim dinleyici izni gerekir). Bilinmiyorsa null. */
    private fun somethingPlaying(): Boolean? = runCatching {
        val msm = getSystemService(android.media.session.MediaSessionManager::class.java) ?: return null
        val sessions = msm.getActiveSessions(ComponentName(this, dev.litedroid.launcher.media.MediaListenerService::class.java))
        sessions.any {
            when (it.playbackState?.state) {
                android.media.session.PlaybackState.STATE_PLAYING,
                android.media.session.PlaybackState.STATE_BUFFERING,
                android.media.session.PlaybackState.STATE_FAST_FORWARDING,
                android.media.session.PlaybackState.STATE_REWINDING,
                android.media.session.PlaybackState.STATE_CONNECTING -> true
                else -> false
            }
        }
    }.getOrNull()

    private fun tickContent() {
        if (!SleepTimer.untilEnd.value) return
        val playing = somethingPlaying()
        val now = System.currentTimeMillis()
        when {
            playing == null -> Unit
            playing -> {
                sawPlaying = true
                stoppedSince = 0L
            }
            // Oynatma durdu ya da duraklatıldı: 2 dakika içinde yeniden başlamazsa kapanış
            sawPlaying -> {
                if (stoppedSince == 0L) stoppedSince = now
                if (now - stoppedSince >= CONTENT_END_WAIT_MS) {
                    startEndCountdown()
                    return
                }
            }
        }
        handler.postDelayed(contentTick, 5_000)
    }

    private fun startEndCountdown() {
        Log.i(TAG, "content finished, counting down")
        countingDown = true
        endCountdown = 5
        ensureNoticeWindow()
        handler.removeCallbacks(hideNotice)
        handler.post(endCountdownTick)
    }

    private fun tickEndCountdown() {
        if (!countingDown || !SleepTimer.untilEnd.value) return
        if (endCountdown <= 0) {
            goToSleep()
            return
        }
        notice.value = SleepNotice(
            tx("İçerik bitti, TV kapanıyor", "Your show ended, turning the TV off"),
            tx("İptal etmek için kumandada herhangi bir tuşa bas.", "Press any key on the remote to cancel."),
            countdown = endCountdown,
            id = notice.value?.takeIf { it.countdown != null }?.id ?: System.nanoTime(),
            icon = "🌙",
        )
        endCountdown--
        handler.postDelayed(endCountdownTick, 1_000)
    }

    private fun onSleepChanged(fromUser: Boolean) {
        handler.removeCallbacks(sleepTick)
        handler.removeCallbacks(contentTick)
        handler.removeCallbacks(endCountdownTick)
        countingDown = false
        announced.clear()
        if (SleepTimer.untilEnd.value) {
            sawPlaying = somethingPlaying() == true
            stoppedSince = 0L
            if (fromUser) {
                showNotice(
                    if (somethingPlaying() == null) SleepNotice(
                        tx("Medya izni gerekli", "Media access needed"),
                        tx("İçeriğin bittiğini anlamak için LiteDroid'in medya geçmişi izni açık olmalı (Ayarlar → İçerik ve gizlilik).",
                            "LiteDroid needs media history access to know when your show ends (Settings → Content and privacy)."),
                        icon = "🌙",
                    ) else SleepNotice(
                        tx("İzlediğin bitince kapanacak", "Off when your show ends"),
                        tx("Oynatma durur ve 2 dakika içinde yeniden başlamazsa TV 5 saniyelik geri sayımla kapanır.",
                            "When playback stops and doesn't resume within 2 minutes, the TV turns off after a 5-second countdown."),
                        icon = "🌙",
                    )
                )
            }
            handler.postDelayed(contentTick, 5_000)
            return
        }
        val remaining = SleepTimer.remainingMs()
        val end = SleepTimer.endAt.value
        if (remaining == null || end == null) {
            if (fromUser) showNotice(SleepNotice(tx("Uyku zamanlayıcısı kapalı", "Sleep timer off"), tx("TV açık kalacak.", "The TV will stay on.")))
            else notice.value = null
            return
        }
        // Zaten geçilmiş eşikler için uyarı gösterme
        WARN_MINUTES.forEach { if (remaining <= it * 60_000L) announced += it }
        if (fromUser) {
            showNotice(
                SleepNotice(
                    tx("Uyku zamanlayıcısı kuruldu", "Sleep timer set"),
                    tx("TV ${SleepTimer.formatRemaining(remaining)} sonra kapanacak (saat ${SleepTimer.clock(end)}).",
                        "The TV turns off in ${SleepTimer.formatRemaining(remaining)} (at ${SleepTimer.clock(end)})."),
                )
            )
        }
        handler.post(sleepTick)
    }

    private fun tickSleep() {
        val remaining = SleepTimer.remainingMs() ?: return
        val end = SleepTimer.endAt.value ?: return
        if (remaining <= 0) {
            goToSleep()
            return
        }
        if (remaining <= 10_000) {
            countingDown = true
            ensureNoticeWindow()
            val sec = ((remaining + 999) / 1000).toInt().coerceIn(1, 10)
            handler.removeCallbacks(hideNotice)
            val cur = notice.value
            if (cur?.countdown != sec) {
                notice.value = SleepNotice(
                    tx("TV kapanıyor", "The TV is turning off"),
                    tx("İptal etmek için kumandada herhangi bir tuşa bas.", "Press any key on the remote to cancel."),
                    countdown = sec,
                    id = cur?.takeIf { it.countdown != null }?.id ?: System.nanoTime(),
                )
            }
        } else {
            WARN_MINUTES.firstOrNull { it !in announced && remaining <= it * 60_000L }?.let { min ->
                announced += min
                showNotice(
                    SleepNotice(
                        tx("TV $min dakika sonra kapanacak", "The TV turns off in $min minutes"),
                        tx("Kapanış saati ${SleepTimer.clock(end)}. Menüden süreyi değiştirebilirsin.",
                            "Off at ${SleepTimer.clock(end)}. Change it from the menu."),
                    )
                )
            }
        }
        handler.postDelayed(sleepTick, if (remaining <= 11_000) 200L else 1_000L)
    }

    private fun goToSleep() {
        Log.i(TAG, "sleep timer finished, going to sleep")
        countingDown = false
        SleepTimer.finished(this)
        notice.value = null
        closeMenu(animated = false)
        // Çalan bir şey varsa önce duraklat (uyanınca kaldığı yerden devam etsin)
        runCatching { if (LiveMediaHub.current.value?.playing == true) LiveMediaHub.playPause() }
        handler.postDelayed({
            val ok = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            Log.i(TAG, "lock screen action: $ok")
        }, 400)
    }

    private fun cancelCountdown() {
        countingDown = false
        SleepTimer.cancel(this) // onChange → "kapalı" bildirimi gösterir
    }

    private fun showNotice(n: SleepNotice) {
        ensureNoticeWindow()
        handler.removeCallbacks(hideNotice)
        notice.value = n
        handler.postDelayed(hideNotice, NOTICE_MS)
    }

    /** Sağ alttaki bildirim penceresi: odak ve dokunma almaz, ilk bildirimde bir kez eklenir. */
    private fun ensureNoticeWindow() {
        if (noticeView != null) return
        val wm = getSystemService(WindowManager::class.java) ?: return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.END or Gravity.BOTTOM }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@RemoteKeyService)
            setViewTreeSavedStateRegistryOwner(this@RemoteKeyService)
            setContent { LiteDroidTheme { SleepNoticeView(notice.value) } }
        }
        runCatching { wm.addView(view, params) }
            .onSuccess { noticeView = view }
            .onFailure { Log.w(TAG, "notice addView failed: ${it.message}") }
    }

    /**
     * LiteDroid dışında bir varsayılan ana ekran varsa onun paket adı (yönlendirme kapalıysa null).
     * Sonuç 30 sn önbelleklenir; "android" çözümlemesi seçim penceresi demektir, o da yakalanır.
     */
    private fun defaultHome(force: Boolean = false): String? {
        val now = SystemClock.uptimeMillis()
        if (force || now - foreignHomeCheckedAt > 30_000) {
            foreignHomeCheckedAt = now
            val enabled = runCatching { SettingsRepository(this).load().homeRedirect }.getOrDefault(true)
            foreignHome = if (!enabled) null else runCatching {
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                val pm = packageManager
                val resolved = pm.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
                // Çözümleme boş dönerse: ana ekran olabilen uygulamalardan en yüksek öncelikli olan (LiteDroid ve ayar yedeği hariç)
                resolved ?: pm.queryIntentActivities(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                    .filter { it.activityInfo.packageName != packageName && it.priority >= 0 }
                    .maxByOrNull { it.priority }?.activityInfo?.packageName
            }.getOrNull()?.takeIf { it != packageName && it != "com.android.tv.settings" }
        }
        return foreignHome
    }

    private fun runInputAction(action: dev.litedroid.launcher.data.InputKeyAction) {
        val inLauncher = launcherInFront || ScoreOverlay.launcherVisible.value
        when (action) {
            dev.litedroid.launcher.data.InputKeyAction.PANEL -> openQuickPanel()
            dev.litedroid.launcher.data.InputKeyAction.MENU ->
                if (menuView != null) closeMenu() else if (inLauncher) openQuickPanel() else openMenu()
            dev.litedroid.launcher.data.InputKeyAction.LAST_APP -> {
                // LiteDroid öndeyse en son uygulama, bir uygulamadaysan ondan önceki
                val target = if (inLauncher) recentApps.firstOrNull() else recentApps.getOrNull(1)
                if (target == null) {
                    showNotice(SleepNotice(tx("Dönülecek uygulama yok", "No app to switch to"), tx("Önce bir iki uygulama aç.", "Open an app or two first."), icon = "↩"))
                    return
                }
                closeMenu(animated = false)
                val intent = packageManager.getLeanbackLaunchIntentForPackage(target) ?: packageManager.getLaunchIntentForPackage(target)
                runCatching { startActivity(intent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { Log.w(TAG, "last app launch failed: ${it.message}") }
            }
            dev.litedroid.launcher.data.InputKeyAction.SCREENSHOT -> {
                closeMenu(animated = false)
                captureScreen(250)
            }
            dev.litedroid.launcher.data.InputKeyAction.HOME -> openLiteDroidHome()
            dev.litedroid.launcher.data.InputKeyAction.OFF -> Unit
        }
    }

    private fun openQuickPanel() {
        closeMenu(animated = false)
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_PANEL, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Log.w(TAG, "panel open failed: ${it.message}") }
    }

    /** LiteDroid ana ekranını açar (MainActivity bunu Home basışı sayar: başa döner). */
    private fun openLiteDroidHome() {
        val now = SystemClock.uptimeMillis()
        if (now - lastRedirectAt < 600) return
        lastRedirectAt = now
        closeMenu()
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setPackage(packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        }.onFailure { Log.w(TAG, "home redirect failed: ${it.message}") }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Kutunun kendi ana ekranı öne geldiyse (açılışta, uygulamadan çıkınca...) LiteDroid'e geç
        val home = defaultHome()
        if (home != null && pkg == home) {
            Log.i(TAG, "$pkg came to front, redirecting to LiteDroid")
            openLiteDroidHome()
            return
        }
        watchForeground(pkg, event.className?.toString())
        if (menuView != null) return
        if (pkg == packageName) {
            // Kendi ana ekranımız öne geldi mi? (Menü penceresi bu bilgiyi bozmasın diye sınıf adına bakılır)
            if (event.className?.toString()?.contains("MainActivity") == true) launcherInFront = true
        } else if (pkg != "com.android.systemui") {
            launcherInFront = false
        }
    }

    // ------------------------------------------------------------------
    // Uygulama kilidi ve mola hatırlatıcısı
    // ------------------------------------------------------------------

    private val imePackages by lazy {
        runCatching {
            getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.inputMethodList?.map { it.packageName }?.toSet()
        }.getOrNull().orEmpty()
    }

    private fun watchForeground(pkg: String, className: String?) {
        // Klavye ve sistem arayüzü "uygulama değişti" sayılmaz
        if (pkg == "com.android.systemui" || pkg in imePackages) return
        val settings = SettingsRepository(this).load()
        if (pkg == packageName) {
            if (className?.contains("MainActivity") == true) {
                fgPkg = null
                unlockedPkg = null
                hideLock()
            }
            return
        }
        if (pkg != fgPkg) {
            fgPkg = pkg
            recentApps.remove(pkg)
            recentApps.addFirst(pkg)
            while (recentApps.size > 4) recentApps.removeLast()
            fgSince = System.currentTimeMillis()
            breakNotified = 0
        }
        if (pkg != unlockedPkg) unlockedPkg = null
        if (pkg in settings.lockedApps && settings.lockPin.length == 4 && pkg != unlockedPkg) {
            if (lockedPkg != pkg || lockView == null) showLock(pkg, settings.lockPin)
        } else if (lockView != null && pkg != lockedPkg) {
            hideLock()
        }
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun showLock(pkg: String, pin: String) {
        hideLock()
        val wm = getSystemService(WindowManager::class.java) ?: return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        val label = appLabel(pkg)
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@RemoteKeyService)
            setViewTreeSavedStateRegistryOwner(this@RemoteKeyService)
            setContent {
                LiteDroidTheme {
                    AppLockScreen(label, pin) {
                        unlockedPkg = pkg
                        hideLock()
                    }
                }
            }
        }
        runCatching { wm.addView(view, params) }
            .onSuccess {
                lockView = view
                lockedPkg = pkg
                Log.i(TAG, "locked $pkg")
            }
            .onFailure { Log.w(TAG, "lock addView failed: ${it.message}") }
    }

    private fun hideLock() {
        val v = lockView ?: return
        lockView = null
        lockedPkg = null
        runCatching { getSystemService(WindowManager::class.java)?.removeView(v) }
    }

    private fun checkBreak() {
        val pkg = fgPkg ?: return
        val minutes = SettingsRepository(this).load().breakMinutes
        if (minutes <= 0) return
        val interactive = getSystemService(android.os.PowerManager::class.java)?.isInteractive ?: true
        // Ekran kapalıyken ya da ekran koruyucu açıkken süre sayılmaz
        if (!interactive || ScoreOverlay.dreaming.value || launcherInFront) {
            fgSince = System.currentTimeMillis()
            breakNotified = 0
            return
        }
        val n = ((System.currentTimeMillis() - fgSince) / (minutes * 60_000L)).toInt()
        if (n > breakNotified) {
            breakNotified = n
            val total = n * minutes
            val time = if (total >= 60) tx("${total / 60} sa ${if (total % 60 > 0) "${total % 60} dk" else ""}", "${total / 60} h ${if (total % 60 > 0) "${total % 60} min" else ""}").trim()
            else tx("$total dakika", "$total minutes")
            showNotice(SleepNotice(
                tx("${appLabel(pkg)} · $time oldu", "${appLabel(pkg)} · $time"),
                tx("Gözlerini dinlendirmek için kısa bir mola vermeye ne dersin?", "How about a short break to rest your eyes?"),
                icon = "☕",
            ))
        }
    }

    /** Telefondan gönderilen yazıyı öndeki uygulamanın odaklı yazı kutusuna yazar. */
    fun typeIntoFocused(text: String): Boolean {
        val node = runCatching { rootInActiveWindow?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull() ?: return false
        if (!node.isEditable) return false
        val args = android.os.Bundle().apply {
            putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        // Yazılanı göstermeyiz (şifre olabilir)
        if (ok) showNotice(SleepNotice(tx("Telefondan yazıldı", "Typed from your phone"), tx("${text.length} karakter", "${text.length} characters"), icon = "📲"))
        return ok
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Kilit ekranı açıkken: Geri ana ekrana döner, diğer tuşlar PIN ekranına gider
        if (lockView != null) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) {
                    hideLock()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
                return true
            }
            if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        }
        // Son 10 saniye: herhangi bir tuş kapanmayı iptal eder (tuş uygulamaya gitmez)
        if (countingDown && event.action == KeyEvent.ACTION_DOWN) {
            cancelCountdown()
            swallowUpAfterCancel = true
            return true
        }
        if (swallowUpAfterCancel && event.action == KeyEvent.ACTION_UP) {
            swallowUpAfterCancel = false
            return true
        }
        // Kumandadaki Input (TV) tuşu: ayarlardaki işe atanır
        if (event.keyCode == KeyEvent.KEYCODE_TV || event.keyCode == KeyEvent.KEYCODE_TV_INPUT) {
            val action = SettingsRepository(this).load().inputKey
            if (action == dev.litedroid.launcher.data.InputKeyAction.OFF) return false
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) handler.post { runInputAction(action) }
            return true
        }
        // Home: varsayılan ana ekran başkasıysa tuşu yut, LiteDroid'i aç
        if (event.keyCode == KeyEvent.KEYCODE_HOME && event.action == KeyEvent.ACTION_DOWN) {
            Log.i(TAG, "home key seen, default home: ${defaultHome()}")
        }
        if (event.keyCode == KeyEvent.KEYCODE_HOME && defaultHome() != null) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) openLiteDroidHome()
            return true
        }
        val isBack = event.keyCode == KeyEvent.KEYCODE_BACK

        // Menü açıkken: menüyü açan ikinci basışın bırakılması yutulur; sonraki Geri menüyü kapatır,
        // diğer tuşlar menüye gider.
        if (menuView != null) {
            if (!isBack) return false
            if (event.action == KeyEvent.ACTION_UP) {
                if (swallowNextUp) swallowNextUp = false else closeMenu()
            }
            return true
        }

        // Bekleyen tek Geri varken başka bir tuşa basıldıysa önce o Geri'yi gönder (sıra bozulmasın)
        if (!isBack) {
            if (pendingBack && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(sendBackRunnable)
                pendingBack = false
                sendBack()
            }
            return false
        }
        if (SystemClock.uptimeMillis() < passThroughUntil) return false
        if (launcherInFront || ScoreOverlay.launcherVisible.value || ScoreOverlay.dreaming.value) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) return true
                if (pendingBack) {
                    // İkinci basış: Geri gönderilmez, menü açılır
                    handler.removeCallbacks(sendBackRunnable)
                    pendingBack = false
                    swallowNextUp = true
                    openMenu()
                } else {
                    backDownSeen = true
                }
                return true
            }
            KeyEvent.ACTION_UP -> {
                if (swallowNextUp) {
                    swallowNextUp = false
                    return true
                }
                if (backDownSeen) {
                    backDownSeen = false
                    pendingBack = true
                    handler.postDelayed(sendBackRunnable, DOUBLE_TAP_MS)
                }
                return true
            }
        }
        return false
    }

    private fun openMenu() {
        if (menuView != null) return
        // Kapanma animasyonu sürüyorsa eski pencereyi hemen kaldır
        handler.removeCallbacks(removeMenuRunnable)
        removeMenuRunnable.run()
        val wm = getSystemService(WindowManager::class.java) ?: return
        val settings = SettingsRepository(this).load()
        I18n.lang = settings.language
        ScoresHub.configure(settings)
        ScoresHub.acquire(this, "menu")
        ScoreOverlay.menuOpen.value = true
        val density = resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            (370 * density).toInt(),
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.TOP
            dimAmount = 0.25f
        }
        menuVisible.value = false
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@RemoteKeyService)
            setViewTreeSavedStateRegistryOwner(this@RemoteKeyService)
            setContent {
                LiteDroidTheme {
                    FloatingMenu(
                        visible = menuVisible.value,
                        sleepEnd = SleepTimer.endAt.collectAsState().value,
                        onSleep = { minutes ->
                            when {
                                minutes == null -> SleepTimer.cancel(this@RemoteKeyService)
                                minutes < 0 -> SleepTimer.startUntilEnd(this@RemoteKeyService)
                                else -> SleepTimer.startIn(this@RemoteKeyService, minutes)
                            }
                        },
                        onClose = { closeMenu() },
                        onHome = {
                            closeMenu()
                            startActivity(
                                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                                    .setPackage(packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        onScreenshot = {
                            // Menü kapanıp ekrandan çekilsin diye kısa bekleme
                            closeMenu(animated = false)
                            captureScreen(450)
                        },
                        onAskClaude = {
                            closeMenu()
                            startActivity(
                                Intent(this@RemoteKeyService, MainActivity::class.java)
                                    .putExtra(MainActivity.EXTRA_OPEN_CLAUDE, true)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                    )
                }
            }
        }
        runCatching { wm.addView(view, params) }
            .onSuccess {
                menuView = view
                // Pencere eklendikten sonra görünür yap: sağdan kayarak girsin
                handler.post { menuVisible.value = true }
            }
            .onFailure {
                Log.w(TAG, "menu addView failed: ${it.message}")
                ScoresHub.release("menu")
                ScoreOverlay.menuOpen.value = false
            }
    }

    private var closingView: ComposeView? = null
    private val removeMenuRunnable = Runnable {
        val v = closingView ?: return@Runnable
        closingView = null
        runCatching { getSystemService(WindowManager::class.java)?.removeView(v) }
    }

    /** Menü sağa kayarak çıkar, animasyon bitince pencere kaldırılır. */
    private fun closeMenu(animated: Boolean = true) {
        val v = menuView ?: return
        menuView = null
        menuVisible.value = false
        ScoresHub.release("menu")
        ScoreOverlay.menuOpen.value = false
        handler.removeCallbacks(removeMenuRunnable)
        closingView?.let { old -> runCatching { getSystemService(WindowManager::class.java)?.removeView(old) } }
        closingView = v
        if (animated) handler.postDelayed(removeMenuRunnable, MENU_EXIT_MS) else removeMenuRunnable.run()
    }

    override fun onInterrupt() {
        if (pendingBack) {
            handler.removeCallbacks(sendBackRunnable)
            pendingBack = false
            sendBack()
        }
    }

    // ------------------------------------------------------------------
    // Ekran görüntüsü (Android 11+: erişilebilirlik servisinin ekran görüntüsü yetkisi)
    // ------------------------------------------------------------------

    /** [delayMs] sonra o anki ekranın görüntüsünü alır ve Araçlar → Ekran görüntüleri'ne kaydeder. */
    fun captureScreen(delayMs: Long) {
        handler.postDelayed({ takeShot() }, delayMs)
    }

    private fun takeShot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            showNotice(SleepNotice(tx("Ekran görüntüsü alınamadı", "Couldn't capture"), tx("Android 11 ve üstü gerekir.", "Needs Android 11 or newer."), icon = "📸"))
            return
        }
        takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                val hb = result.hardwareBuffer
                val bmp = android.graphics.Bitmap.wrapHardwareBuffer(hb, result.colorSpace)?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                hb.close()
                if (bmp == null) {
                    showNotice(SleepNotice(tx("Ekran görüntüsü alınamadı", "Couldn't capture"), "", icon = "📸"))
                    return
                }
                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val ok = runCatching { dev.litedroid.launcher.tools.ScreenshotStore.save(this@RemoteKeyService, bmp) }.isSuccess
                    bmp.recycle()
                    handler.post {
                        showNotice(
                            if (ok) SleepNotice(tx("Ekran görüntüsü alındı", "Screenshot saved"), tx("Ekstra → Araçlar → Ekran görüntüleri", "Extras → Tools → Screenshots"), icon = "📸")
                            else SleepNotice(tx("Ekran görüntüsü kaydedilemedi", "Couldn't save the screenshot"), "", icon = "📸")
                        )
                    }
                }
            }

            override fun onFailure(errorCode: Int) {
                Log.w(TAG, "screenshot failed: $errorCode")
                showNotice(SleepNotice(tx("Ekran görüntüsü alınamadı", "Couldn't capture"), tx("Kumanda kısayolunu kapatıp yeniden açmak gerekebilir (kod $errorCode).", "You may need to turn the remote shortcut off and on again (code $errorCode)."), icon = "📸"))
            }
        })
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        handler.removeCallbacks(breakTick)
        hideLock()
        closeMenu(animated = false)
        handler.removeCallbacks(sleepTick)
        SleepTimer.onChange = null
        noticeView?.let { v -> runCatching { getSystemService(WindowManager::class.java)?.removeView(v) } }
        noticeView = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LiteDroidRemote"

        /** Çalışan servis (araçlardan ekran görüntüsü istemek için); kapalıysa null. */
        @Volatile var instance: RemoteKeyService? = null
            private set
        /** İki basış arasındaki en uzun süre. Tek Geri de bu kadar gecikmeyle uygulamaya gider. */
        private const val DOUBLE_TAP_MS = 320L
        private const val MENU_EXIT_MS = 260L
        private const val NOTICE_MS = 5_000L
        /** Kapanmaya bu kadar dakika kala sağ altta uyarı. */
        private val WARN_MINUTES = listOf(45, 30, 15, 5)
        /** "İzlediğin bitince": oynatma bu kadar süre durunca kapanış geri sayımı başlar. */
        private const val CONTENT_END_WAIT_MS = 120_000L

        const val ADB_ENABLE =
            "adb shell settings put secure enabled_accessibility_services dev.litedroid.launcher/dev.litedroid.launcher.overlay.RemoteKeyService\n" +
                "adb shell settings put secure accessibility_enabled 1"

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, RemoteKeyService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
