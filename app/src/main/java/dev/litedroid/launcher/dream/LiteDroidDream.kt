package dev.litedroid.launcher.dream

import dev.litedroid.launcher.I18n
import android.service.dreams.DreamService
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.litedroid.launcher.data.SettingsRepository
import dev.litedroid.launcher.ui.ScreensaverContent
import dev.litedroid.launcher.ui.theme.LiteDroidTheme

/**
 * Sistem ekran koruyucusu: TV'de başka bir uygulama açıkken de boşta kalınca devreye girer.
 * Uygulama içi ekran koruyucuyla aynı sahneleri gösterir.
 */
class LiteDroidDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val settings = SettingsRepository(this).load()
        I18n.lang = settings.language
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LiteDroidDream)
            setViewTreeSavedStateRegistryOwner(this@LiteDroidDream)
            setContent {
                LiteDroidTheme { ScreensaverContent(settings.saverScene, settings.userName) }
            }
        }
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }
}
