package dev.litedroid.launcher.overlay

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
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        Log.i(TAG, "remote shortcut connected, default home: ${defaultHome(force = true)}")
        SleepTimer.onChange = { fromUser -> handler.post { onSleepChanged(fromUser) } }
        SleepTimer.load(this)
        onSleepChanged(fromUser = false)
    }

    // ------------------------------------------------------------------
    // Uyku zamanlayıcısı
    // ------------------------------------------------------------------

    private fun onSleepChanged(fromUser: Boolean) {
        handler.removeCallbacks(sleepTick)
        countingDown = false
        announced.clear()
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
        if (menuView != null) return
        if (pkg == packageName) {
            // Kendi ana ekranımız öne geldi mi? (Menü penceresi bu bilgiyi bozmasın diye sınıf adına bakılır)
            if (event.className?.toString()?.contains("MainActivity") == true) launcherInFront = true
        } else if (pkg != "com.android.systemui") {
            launcherInFront = false
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
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
                            if (minutes == null) SleepTimer.cancel(this@RemoteKeyService)
                            else SleepTimer.startIn(this@RemoteKeyService, minutes)
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

    override fun onDestroy() {
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
        /** İki basış arasındaki en uzun süre. Tek Geri de bu kadar gecikmeyle uygulamaya gider. */
        private const val DOUBLE_TAP_MS = 320L
        private const val MENU_EXIT_MS = 260L
        private const val NOTICE_MS = 5_000L
        /** Kapanmaya bu kadar dakika kala sağ altta uyarı. */
        private val WARN_MINUTES = listOf(45, 30, 15, 5)

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
