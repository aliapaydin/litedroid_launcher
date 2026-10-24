package dev.litedroid.launcher.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.litedroid.launcher.I18n
import dev.litedroid.launcher.R
import dev.litedroid.launcher.data.LauncherSettings
import dev.litedroid.launcher.data.MatchState
import dev.litedroid.launcher.data.ScoresHub
import dev.litedroid.launcher.data.SettingsRepository
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.GoalBanner
import dev.litedroid.launcher.ui.MatchRow
import dev.litedroid.launcher.ui.theme.LiteDroidTheme
import dev.litedroid.launcher.ui.widgetGlass
import kotlinx.coroutines.flow.MutableStateFlow

/** Yüzen skor şeridinin açılıp kapanmasını yönetir. */
object ScoreOverlay {
    /** LiteDroid ön plandayken şerit gizlenir (ana ekranda zaten skorlar var). */
    val launcherVisible = MutableStateFlow(false)

    const val ADB_COMMAND = "adb shell appops set dev.litedroid.launcher SYSTEM_ALERT_WINDOW allow"

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Ayarlara göre servisi başlatır ya da durdurur. */
    fun sync(context: Context, settings: LauncherSettings) {
        val intent = Intent(context, ScoreOverlayService::class.java)
        if (settings.floatingScores && canDraw(context)) {
            runCatching {
                context.startForegroundService(intent)
            }.onFailure { Log.w("LiteDroidScores", "overlay start failed: ${it.message}") }
        } else {
            context.stopService(intent)
        }
    }

    /** Ayar değişince (ör. "yalnızca canlı") servisi yeniden kurar. */
    fun restart(context: Context, settings: LauncherSettings) {
        context.stopService(Intent(context, ScoreOverlayService::class.java))
        sync(context, settings)
    }
}

/**
 * Başka bir uygulama açıkken ekranın sağ üst köşesinde yüzen küçük skor şeridi.
 * Dokunma ve kumanda olaylarını almaz (alttaki uygulamayı engellemez).
 * İzin bir kez adb ile verilir: [ScoreOverlay.ADB_COMMAND].
 */
class ScoreOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private var view: ComposeView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        startAsForeground()
        val settings = SettingsRepository(this).load()
        I18n.lang = settings.language
        ScoresHub.leagues = settings.scoreLeagues
        ScoresHub.acquire(this, "overlay")
        if (ScoreOverlay.canDraw(this)) addOverlay(settings.floatingOnlyLive) else stopSelf()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(CHANNEL, tx("Canlı skorlar", "Live scores"), NotificationManager.IMPORTANCE_MIN)
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.logo_mark)
            .setContentTitle(tx("Canlı skorlar", "Live scores"))
            .setContentText(tx("Yüzen skor şeridi açık", "Floating score strip is on"))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun addOverlay(onlyLive: Boolean) {
        val wm = getSystemService(WindowManager::class.java) ?: return
        val density = resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (28 * density).toInt()
            y = (24 * density).toInt()
        }
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ScoreOverlayService)
            setViewTreeSavedStateRegistryOwner(this@ScoreOverlayService)
            setContent { LiteDroidTheme { OverlayContent(onlyLive) } }
        }
        runCatching { wm.addView(composeView, params) }
            .onSuccess { view = composeView }
            .onFailure { Log.w("LiteDroidScores", "overlay addView failed: ${it.message}"); stopSelf() }
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        ScoresHub.release("overlay")
        view?.let { v -> runCatching { getSystemService(WindowManager::class.java)?.removeView(v) } }
        view = null
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "live_scores"
        const val NOTIFICATION_ID = 41
    }
}

@Composable
private fun OverlayContent(onlyLive: Boolean) {
    val matches by ScoresHub.matches.collectAsState()
    val launcherVisible by ScoreOverlay.launcherVisible.collectAsState()
    if (launcherVisible) {
        Box {}
        return
    }
    val shown = matches.filter { if (onlyLive) it.state == MatchState.LIVE else it.state != MatchState.DONE }.take(3)
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GoalBanner()
        if (shown.isNotEmpty()) {
            Column(
                Modifier
                    .width(290.dp)
                    .widgetGlass(RoundedCornerShape(16.dp), alpha = 0.72f)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                shown.forEach { MatchRow(it, compact = true) }
            }
        } else {
            Spacer(Modifier.height(1.dp))
        }
    }
}
