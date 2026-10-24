package dev.litedroid.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import dev.litedroid.launcher.ui.HomeScreen
import dev.litedroid.launcher.ui.LocalSounds
import dev.litedroid.launcher.ui.SoundFx
import dev.litedroid.launcher.ui.theme.LiteDroidTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private lateinit var sounds: SoundFx

    /** Uygulama kurulunca/kaldırılınca listeyi yenile. */
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            viewModel.refresh()
            viewModel.refreshContent()
        }
    }

    private val epgPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshContent() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sounds = SoundFx(this)
        setContent {
            LiteDroidTheme {
                CompositionLocalProvider(LocalSounds provides sounds) {
                    HomeScreen(viewModel)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        askContentPermissionOnce()
    }

    /** "Devam et" verisi için izni bir kez iste; cihaz bu izni vermiyorsa sessizce geçer. */
    private fun askContentPermissionOnce() {
        if (ContextCompat.checkSelfPermission(this, READ_EPG) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("litedroid", Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false)) return
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        runCatching { epgPermission.launch(READ_EPG) }
    }

    /**
     * Ekran koruyucu açıksa ilk tuş yalnızca onu kapatır (arkadaki uygulamayı açmasın diye
     * hem basma hem bırakma olayı yutulur). Diğer her tuş boşta kalma sayacını sıfırlar.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (viewModel.screensaverActive) {
            if (event.action == KeyEvent.ACTION_UP) viewModel.dismissScreensaver()
            return true
        }
        viewModel.onUserInteraction()
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        viewModel.setResumed(true)
        // Bir uygulamadan dönünce "devam et" bilgisi değişmiş olabilir
        viewModel.refreshContent()
    }

    override fun onPause() {
        viewModel.setResumed(false)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) viewModel.onHomePressed()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        sounds.release()
        super.onDestroy()
    }

    private companion object {
        const val READ_EPG = "com.android.providers.tv.permission.READ_EPG_DATA"
        const val KEY_ASKED = "epg_permission_asked"
    }
}
