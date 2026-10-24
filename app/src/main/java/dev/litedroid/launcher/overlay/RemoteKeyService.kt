package dev.litedroid.launcher.overlay

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
 * LiteDroid'in kendisi ya da ekran koruyucu öndeyken hiçbir tuşa karışmaz.
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
        Log.i(TAG, "remote shortcut connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || menuView != null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) {
            // Kendi ana ekranımız öne geldi mi? (Menü penceresi bu bilgiyi bozmasın diye sınıf adına bakılır)
            if (event.className?.toString()?.contains("MainActivity") == true) launcherInFront = true
        } else if (pkg != "com.android.systemui") {
            launcherInFront = false
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
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
        val wm = getSystemService(WindowManager::class.java) ?: return
        val settings = SettingsRepository(this).load()
        I18n.lang = settings.language
        ScoresHub.leagues = settings.scoreLeagues
        ScoresHub.acquire(this, "menu")
        ScoreOverlay.menuOpen.value = true
        val density = resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            (460 * density).toInt(),
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.TOP
            dimAmount = 0.35f
        }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@RemoteKeyService)
            setViewTreeSavedStateRegistryOwner(this@RemoteKeyService)
            setContent {
                LiteDroidTheme {
                    FloatingMenu(
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
            .onSuccess { menuView = view }
            .onFailure {
                Log.w(TAG, "menu addView failed: ${it.message}")
                ScoresHub.release("menu")
                ScoreOverlay.menuOpen.value = false
            }
    }

    private fun closeMenu() {
        val v = menuView ?: return
        menuView = null
        runCatching { getSystemService(WindowManager::class.java)?.removeView(v) }
        ScoresHub.release("menu")
        ScoreOverlay.menuOpen.value = false
    }

    override fun onInterrupt() {
        if (pendingBack) {
            handler.removeCallbacks(sendBackRunnable)
            pendingBack = false
            sendBack()
        }
    }

    override fun onDestroy() {
        closeMenu()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LiteDroidRemote"
        /** İki basış arasındaki en uzun süre. Tek Geri de bu kadar gecikmeyle uygulamaya gider. */
        private const val DOUBLE_TAP_MS = 320L

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
