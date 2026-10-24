package dev.litedroid.launcher.media

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
        track(controllers.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        android.util.Log.i("LiteDroidMedia", "listener connected")
        NotificationsHub.dismissAll = { runCatching { cancelAllNotifications() } }
        publishNotifications()
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

    override fun onNotificationPosted(sbn: StatusBarNotification?) = publishNotifications()

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
