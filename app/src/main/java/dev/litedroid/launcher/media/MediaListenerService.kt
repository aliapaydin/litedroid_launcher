package dev.litedroid.launcher.media

import android.os.Bundle
import android.graphics.drawable.BitmapDrawable
import android.graphics.Canvas
import android.graphics.Bitmap
import android.os.Build
import android.app.Notification
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Medya oturumlarını ve bildirimleri dinler. Android, diğer uygulamaların "şu an ne oynuyor"
 * bilgisini ve bildirimlerini yalnızca bildirim erişimi verilmiş uygulamalara açar.
 *
 * İzin (bir kez):
 * adb shell cmd notification allow_listener dev.litedroid.launcher/dev.litedroid.launcher.media.MediaListenerService
 */
class MediaListenerService : NotificationListenerService() {

    private var sessionManager: MediaSessionManager? = null
    private val callbacks = HashMap<MediaSession.Token, MediaController.Callback>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        val list = controllers.orEmpty()
        track(list)
        LiveMediaHub.retain(list.mapNotNull { it.packageName }.toSet())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        android.util.Log.i("LiteDroidMedia", "listener connected")
        NotificationsHub.dismissAll = { runCatching { cancelAllNotifications() } }
        publishNotifications()
        runCatching { activeNotifications }.getOrNull().orEmpty().forEach { readMediaNotification(it) }
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        sessionManager = manager
        val component = NowPlayingStore.listenerComponent(this)
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component)
            track(manager.getActiveSessions(component))
        }
    }

    override fun onListenerDisconnected() {
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        callbacks.clear()
        NotificationsHub.dismissAll = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { readMediaNotification(it) }
        publishNotifications()
    }

    /**
     * Medya bildirimi (YouTube Music, Apple Music, TIDAL, Qobuz… çalarken çıkan): içindeki oturumu da takibe al
     * ve başlık/sanatçı/kapak bilgisini yedek olarak sakla. Oturumuna başlık ya da kapak koymayan uygulamalar
     * da böylece "Şimdi çalıyor" kartında düzgün görünür.
     */
    @Suppress("DEPRECATION")
    private fun sessionTokenOf(extras: Bundle): MediaSession.Token? =
        if (Build.VERSION.SDK_INT >= 33) extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        else extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token

    private fun readMediaNotification(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification?.extras ?: return
        val token = runCatching { sessionTokenOf(extras) }.getOrNull() ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val art = runCatching {
            sbn.notification.getLargeIcon()?.loadDrawable(this)?.let { d ->
                if (d is BitmapDrawable && d.bitmap != null) d.bitmap
                else {
                    val w = d.intrinsicWidth.takeIf { it > 0 } ?: 256
                    val h = d.intrinsicHeight.takeIf { it > 0 } ?: 256
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
                        val c = Canvas(b)
                        d.setBounds(0, 0, w, h)
                        d.draw(c)
                    }
                }
            }
        }.getOrNull()
        runCatching { LiveMediaHub.hint(this, sbn.packageName, title, text, art) }
        runCatching { track(listOf(MediaController(this, token))) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publishNotifications()

    /** Kalıcı (oynatıcı, servis) ve özet bildirimler hariç, en yeniden eskiye. */
    private fun publishNotifications() {
        val list = runCatching { activeNotifications }.getOrNull().orEmpty().mapNotNull { sbn ->
            if (sbn.packageName == packageName || sbn.isOngoing) return@mapNotNull null
            val n = sbn.notification
            if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return@mapNotNull null
            val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            NotifItem(
                key = sbn.key,
                packageName = sbn.packageName,
                title = title,
                text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                postTime = sbn.postTime,
                contentIntent = n.contentIntent,
            )
        }.sortedByDescending { it.postTime }.take(20)
        NotificationsHub.update(list)
    }

    private fun track(controllers: List<MediaController>) {
        android.util.Log.i("LiteDroidMedia", "active sessions: " + controllers.joinToString { it.packageName })
        controllers.forEach { controller ->
            val token = controller.sessionToken
            if (callbacks.containsKey(token)) return@forEach
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = save(controller)
                override fun onPlaybackStateChanged(state: PlaybackState?) = save(controller)
                override fun onSessionDestroyed() {
                    save(controller)
                    NowPlayingStore.forget(controller)
                    controller.packageName?.let { LiveMediaHub.forget(it) }
                    runCatching { controller.unregisterCallback(this) }
                    callbacks.remove(token)
                }
            }
            runCatching { controller.registerCallback(callback) }
            callbacks[token] = callback
            save(controller)
        }
    }

    private fun save(controller: MediaController) {
        runCatching { LiveMediaHub.update(this, controller) }
        runCatching { NowPlayingStore.save(this, controller) }
    }
}
