package dev.litedroid.launcher.overlay

import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import dev.litedroid.launcher.ui.MatchEventCard
import dev.litedroid.launcher.data.MatchEvent
import dev.litedroid.launcher.data.Match
import androidx.core.content.ContextCompat
import android.content.IntentFilter
import android.content.BroadcastReceiver
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
import dev.litedroid.launcher.ui.MatchRow
import dev.litedroid.launcher.ui.theme.LiteDroidTheme
import dev.litedroid.launcher.ui.widgetGlass
import kotlinx.coroutines.flow.MutableStateFlow

/** Yüzen skor şeridinin açılıp kapanmasını yönetir. */
object ScoreOverlay {
    /** LiteDroid ön plandayken şerit gizlenir (ana ekranda zaten skorlar var). */
    val launcherVisible = MutableStateFlow(false)

    /** Ekran koruyucu açıkken (LiteDroid'inki ya da sistemin) şerit gizlenir; koruyucunun kendi skor kartı var. */
    val dreaming = MutableStateFlow(false)

    /** Yüzen menü açıkken şerit gizlenir (menü tüm skorları zaten gösteriyor). */
    val menuOpen = MutableStateFlow(false)

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

    /** Sistem ekran koruyucusu başlayınca/bitince haber verir. */
    private val dreamReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_DREAMING_STARTED, Intent.ACTION_SCREEN_OFF -> ScoreOverlay.dreaming.value = true
                Intent.ACTION_DREAMING_STOPPED, Intent.ACTION_SCREEN_ON -> ScoreOverlay.dreaming.value = false
            }
        }
    }

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
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DREAMING_STARTED)
            addAction(Intent.ACTION_DREAMING_STOPPED)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, dreamReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
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
        runCatching { unregisterReceiver(dreamReceiver) }
        view?.let { v -> runCatching { getSystemService(WindowManager::class.java)?.removeView(v) } }
        view = null
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "live_scores"
        const val NOTIFICATION_ID = 41
    }
}

/** Şeritte gösterilen şey: bir sayfa maç ya da bir olay (gol, kırmızı kart, maç sonu). */
private sealed interface StripContent {
    data class Page(val index: Int, val matches: List<Match>) : StripContent
    data class Event(val event: MatchEvent) : StripContent
}

/**
 * Yüzen skor şeridi: en fazla 3 maç, kompakt satırlar. Daha fazla maç varsa 10 sn'de bir sonraki
 * üçlüye yukarı kayarak geçer. Gol / kırmızı kart / maç sonu olunca aynı kutunun içinde birkaç saniye
 * animasyonlu olay kartı gösterilir, sonra maçlara döner. Kutu, içindeki satır sayısına göre boyutlanır.
 */
@Composable
private fun OverlayContent(onlyLive: Boolean) {
    val matches by ScoresHub.matches.collectAsState()
    val launcherVisible by ScoreOverlay.launcherVisible.collectAsState()
    val dreaming by ScoreOverlay.dreaming.collectAsState()
    val menuOpen by ScoreOverlay.menuOpen.collectAsState()

    var event by remember { mutableStateOf<MatchEvent?>(null) }
    LaunchedEffect(Unit) {
        ScoresHub.events.collect { e ->
            event = e
            delay(7_000)
            event = null
            delay(500)
        }
    }

    val shown = matches.filter { if (onlyLive) it.state == MatchState.LIVE else it.state != MatchState.DONE }
    val pages = shown.chunked(3)
    var page by remember { mutableIntStateOf(0) }
    LaunchedEffect(pages.size) {
        page = 0
        while (pages.size > 1) {
            delay(10_000)
            page = (page + 1) % pages.size
        }
    }

    if (launcherVisible || dreaming || menuOpen) {
        Box {}
        return
    }
    val e = event
    val content: StripContent? = when {
        e != null -> StripContent.Event(e)
        pages.isNotEmpty() -> StripContent.Page(page.coerceIn(0, pages.lastIndex), pages[page.coerceIn(0, pages.lastIndex)])
        else -> null
    }
    if (content == null) {
        Box {}
        return
    }
    Box(
        Modifier
            .widgetGlass(RoundedCornerShape(14.dp), alpha = 0.78f)
            .padding(horizontal = 9.dp, vertical = 6.dp)
    ) {
        AnimatedContent(
            targetState = content,
            contentKey = { c ->
                when (c) {
                    is StripContent.Page -> "page_${c.index}"
                    is StripContent.Event -> "event_${c.event.at}"
                }
            },
            transitionSpec = {
                (slideInVertically(tween(450)) { it } + fadeIn(tween(450)))
                    .togetherWith(slideOutVertically(tween(450)) { -it } + fadeOut(tween(300)))
                    .using(SizeTransform(clip = true))
            },
            label = "strip",
        ) { c ->
            when (c) {
                is StripContent.Page -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    c.matches.forEach { MatchRow(it, tiny = true) }
                    if (pages.size > 1) {
                        // Hangi üçlüde olduğumuzu gösteren küçük noktalar
                        Row(
                            Modifier.align(Alignment.CenterHorizontally),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            repeat(pages.size) { i ->
                                Box(
                                    Modifier
                                        .size(if (i == c.index) 5.dp else 3.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White.copy(alpha = if (i == c.index) 0.8f else 0.3f))
                                )
                            }
                        }
                    }
                }
                is StripContent.Event -> Box(Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                    MatchEventCard(c.event, compact = true)
                }
            }
        }
    }
}
