package dev.litedroid.launcher

import dev.litedroid.launcher.ui.LocalVoiceInput
import dev.litedroid.launcher.overlay.ScoreOverlay
import android.speech.RecognizerIntent
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

    /** Claude'a sor: sistemin konuşma tanıma ekranı; söylenen cümle Claude'a gönderilir. */
    private val voiceInput =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) viewModel.askClaude(text)
        }

    /**
     * Claude API anahtarını bilgisayardan göndermek için (kumandayla uzun anahtar yazmak zor):
     * adb shell am start -n dev.litedroid.launcher/.MainActivity --es claude_key "sk-ant-api03-..."
     */
    private fun handleKeyIntent(intent: Intent?): Boolean {
        // Gemini anahtarı: --es gemini_key "AQ.Ab..." (ya da eski biçim "AIza...")
        intent?.getStringExtra(EXTRA_GEMINI_KEY)?.trim()?.takeIf { it.isNotEmpty() }?.let { gKey ->
            intent.removeExtra(EXTRA_GEMINI_KEY)
            viewModel.updateSettings { it.copy(geminiKey = gKey) }
            android.widget.Toast.makeText(
                this,
                tx("Gemini API anahtarı kaydedildi (…${gKey.takeLast(4)})", "Gemini API key saved (…${gKey.takeLast(4)})"),
                android.widget.Toast.LENGTH_LONG,
            ).show()
            return true
        }
        val key = intent?.getStringExtra(EXTRA_CLAUDE_KEY)?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        intent.removeExtra(EXTRA_CLAUDE_KEY)
        viewModel.updateSettings { it.copy(claudeKey = key) }
        android.widget.Toast.makeText(
            this,
            tx("Claude API anahtarı kaydedildi (…${key.takeLast(4)})", "Claude API key saved (…${key.takeLast(4)})"),
            android.widget.Toast.LENGTH_LONG,
        ).show()
        return true
    }

    private fun handleOpenClaude(intent: Intent?): Boolean {
        if (intent?.getBooleanExtra(EXTRA_OPEN_CLAUDE, false) != true) return false
        intent.removeExtra(EXTRA_OPEN_CLAUDE)
        viewModel.requestOpenClaude()
        return true
    }

    /** Mikrofon izni sorulduktan sonra: verildiyse uygulama içi dinleme, verilmediyse sistemin ekranı. */
    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted || viewModel.startVoice() != LauncherViewModel.VoiceStart.STARTED) launchSystemVoice()
        }

    /**
     * Sesli soru: önce konuşan cevap kesilir, sonra uygulama içi dinleme başlar (canlı yazı, hazır olunca
     * "şimdi konuş"). İzin yoksa bir kez sorulur; tanıma hizmeti yoksa sistemin konuşma ekranı açılır.
     */
    private fun startVoiceInput(): Boolean = when (viewModel.startVoice()) {
        LauncherViewModel.VoiceStart.STARTED -> true
        LauncherViewModel.VoiceStart.NEED_PERMISSION -> runCatching { micPermission.launch(android.Manifest.permission.RECORD_AUDIO) }.isSuccess
        LauncherViewModel.VoiceStart.UNAVAILABLE -> launchSystemVoice()
    }

    private fun launchSystemVoice(): Boolean {
        viewModel.stopClaude()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (I18n.lang == AppLanguage.EN) "en-US" else "tr-TR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, tx("Claude'a sor…", "Ask Claude…"))
        return runCatching { voiceInput.launch(intent) }.isSuccess
    }

    private val epgPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshContent() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sounds = SoundFx(this)
        viewModel.onVoiceFallback = { launchSystemVoice() }
        setContent {
            LiteDroidTheme {
                CompositionLocalProvider(
                    LocalSounds provides sounds,
                    LocalVoiceInput provides { startVoiceInput() },
                ) {
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
        handleKeyIntent(intent)
        handleOpenClaude(intent)
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
        // Yüzen menüden değiştirilen ayarlar (ör. yüzen skor) burada da görünsün
        viewModel.reloadSettings()
        viewModel.setResumed(true)
        ScoreOverlay.launcherVisible.value = true
        viewModel.syncOverlay()
        // Bir uygulamadan dönünce "devam et" bilgisi değişmiş olabilir
        viewModel.refreshContent()
    }

    override fun onPause() {
        viewModel.setResumed(false)
        ScoreOverlay.launcherVisible.value = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (handleKeyIntent(intent)) return
        if (handleOpenClaude(intent)) return
        if (intent.hasCategory(Intent.CATEGORY_HOME)) viewModel.onHomePressed()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        sounds.release()
        super.onDestroy()
    }

    companion object {
        private const val READ_EPG = "com.android.providers.tv.permission.READ_EPG_DATA"
        private const val KEY_ASKED = "epg_permission_asked"
        private const val EXTRA_CLAUDE_KEY = "claude_key"
        private const val EXTRA_GEMINI_KEY = "gemini_key"

        /** Yüzen menüdeki "Claude'a sor" kısayolu LiteDroid'i bu ekranla açar. */
        const val EXTRA_OPEN_CLAUDE = "open_claude"
    }
}
