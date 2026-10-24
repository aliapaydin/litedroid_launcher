package dev.litedroid.launcher.dream

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * LiteDroid'i sistemin ekran koruyucusu yapar (TV'de başka bir uygulama açıkken de çalışsın diye).
 *
 * Gereken izin (bir kez):
 * adb shell pm grant dev.litedroid.launcher android.permission.WRITE_SECURE_SETTINGS
 * Başlama süresini de sisteme yazabilmek için ayrıca:
 * adb shell appops set dev.litedroid.launcher WRITE_SETTINGS allow
 */
object SystemScreensaver {

    fun canConfigure(context: Context): Boolean =
        context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    /** Başarılıysa true. [minutes] 0 ise sistem ekran koruyucusu kapatılır. */
    fun apply(context: Context, minutes: Int): Boolean = runCatching {
        val cr = context.contentResolver
        val component = ComponentName(context, LiteDroidDream::class.java).flattenToString()
        Settings.Secure.putString(cr, "screensaver_components", component)
        Settings.Secure.putString(cr, "screensaver_default_component", component)
        Settings.Secure.putInt(cr, "screensaver_enabled", if (minutes > 0) 1 else 0)
        Settings.Secure.putInt(cr, "screensaver_activate_on_sleep", 1)
        if (minutes > 0) {
            runCatching { Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, minutes * 60_000) }
        }
        true
    }.getOrDefault(false)
}
