package dev.litedroid.launcher.media

import android.app.PendingIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NotifItem(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String?,
    val postTime: Long,
    val contentIntent: PendingIntent?,
)

/**
 * Bildirim servisi ile launcher arasındaki köprü (aynı süreçte çalışırlar).
 * Bildirim erişimi, medya geçmişi için verilen izinle aynıdır.
 */
object NotificationsHub {
    private val _items = MutableStateFlow<List<NotifItem>>(emptyList())
    val items: StateFlow<List<NotifItem>> = _items

    /** Servis bağlıyken tüm bildirimleri temizler. */
    @Volatile
    var dismissAll: (() -> Unit)? = null

    fun update(list: List<NotifItem>) {
        _items.value = list
    }
}
