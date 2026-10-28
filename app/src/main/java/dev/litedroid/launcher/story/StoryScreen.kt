package dev.litedroid.launcher.story

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.data.GeminiMedia
import dev.litedroid.launcher.data.GeminiVoice
import dev.litedroid.launcher.data.LauncherSettings
import dev.litedroid.launcher.data.Pcm
import dev.litedroid.launcher.data.PcmPlayer
import dev.litedroid.launcher.data.SettingsRepository
import dev.litedroid.launcher.overlay.SleepTimer
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.isSelectKey
import dev.litedroid.launcher.ui.onRemoteSelect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Seçenekler
// ---------------------------------------------------------------------------

private class Option(val tr: String, val en: String, val prompt: String? = null) {
    val label get() = tx(tr, en)
}

private val HEROES = listOf(
    Option("🎲 Sürpriz", "🎲 Surprise", "masalcının seçeceği sürpriz, sevimli bir kahraman"),
    Option("Uçan bir kedi", "A flying cat"), Option("Cesur bir tavşan", "A brave bunny"),
    Option("Minik bir ejderha", "A tiny dragon"), Option("Utangaç bir robot", "A shy robot"),
    Option("Astronot bir kız", "A girl astronaut"), Option("Konuşan bir ağaç", "A talking tree"),
    Option("Meraklı bir penguen", "A curious penguin"), Option("Sihirli bir tek boynuzlu at", "A magic unicorn"),
    Option("Kaşif bir kaplumbağa", "An explorer turtle"),
)
private val PLACES = listOf(
    Option("🎲 Sürpriz", "🎲 Surprise", "masalcının seçeceği sürpriz, büyülü bir yer"),
    Option("Denizin altı", "Under the sea"), Option("Bulutların üstü", "Above the clouds"),
    Option("Büyülü bir orman", "An enchanted forest"), Option("Uzay", "Outer space"),
    Option("Şekerden bir kasaba", "A candy town"), Option("Buzul diyarı", "The land of ice"),
    Option("Eski bir kütüphane", "An old library"), Option("Gizli bir bahçe", "A secret garden"),
)
private val THEMES = listOf(
    Option("🎲 Sürpriz", "🎲 Surprise", "masalcının seçeceği güzel bir değer"),
    Option("Arkadaşlık", "Friendship"), Option("Cesaret", "Courage"), Option("Paylaşmak", "Sharing"),
    Option("Merak ve keşif", "Curiosity"), Option("Yardımlaşma", "Helping others"),
    Option("Farklılıklar güzeldir", "Being different is great"), Option("Sabır", "Patience"),
)

enum class StoryLength(val intro: Int, val branch: Int, private val tr: String, private val en: String) {
    SHORT(2, 2, "Kısa · ~3 dk", "Short · ~3 min"),
    MEDIUM(3, 3, "Orta · ~5 dk", "Medium · ~5 min"),
    LONG(4, 4, "Uzun · ~7 dk", "Long · ~7 min");

    val label get() = tx(tr, en)
}

/** Resim sayısı: maliyet ve bekleme süresi buna bağlı. */
enum class ImageMode(private val tr: String, private val en: String) {
    ALL("Her sayfa", "Every page"),
    HALF("İki sayfada bir", "Every other page"),
    COVER("Sadece kapak", "Cover only"),
    NONE("Resimsiz", "No pictures");

    val label get() = tx(tr, en)
}

private const val NARRATOR_STYLE = "warm, gentle bedtime storyteller, speaking slowly and softly, full of wonder"

// ---------------------------------------------------------------------------
// Durum ve akış
// ---------------------------------------------------------------------------

sealed interface Stage {
    data object Setup : Stage
    data object Library : Stage
    data class Preparing(val title: String, val detail: String) : Stage
    data object Playing : Stage
    data class Choosing(val choice: StoryChoice) : Stage
    data object End : Stage
    data class Error(val message: String) : Stage
}

private class PageAssets(val audio: Pcm?, val hasImage: Boolean, val error: Throwable?)

/**
 * Masalın beyni: yazdırır, resimleri ve sesleri sırayla (bir sayfa önden) hazırlar, çalar,
 * seçimde bekler. Üretilen her şey kitaplığa yazılır.
 */
class StoryController(private val context: Context, private val scope: CoroutineScope) {

    private val prefs = context.getSharedPreferences(dev.litedroid.launcher.data.Profiles.scoped("litedroid_story"), Context.MODE_PRIVATE)
    val settings: LauncherSettings = SettingsRepository(context).load()
    val key: String = settings.geminiKey.trim()
    val library = StoryLibrary(context)

    var stage by mutableStateOf<Stage>(Stage.Setup)
        private set

    // Kurulum seçimleri (son seçilenler hatırlanır)
    var hero by mutableIntStateOf(prefs.getInt("hero", 0))
    var place by mutableIntStateOf(prefs.getInt("place", 0))
    var theme by mutableIntStateOf(prefs.getInt("theme", 0))
    var length by mutableStateOf(StoryLength.entries.getOrElse(prefs.getInt("length", 1)) { StoryLength.MEDIUM })
    var imageMode by mutableStateOf(ImageMode.entries.getOrElse(prefs.getInt("images", 0)) { ImageMode.ALL })
    var narrator by mutableStateOf(GeminiVoice.entries.firstOrNull { it.name == prefs.getString("narrator", null) } ?: settings.geminiVoice)
    var music by mutableStateOf(prefs.getBoolean("music", true))
    var sleepAfter by mutableStateOf(prefs.getBoolean("sleep", false))

    // Oynatma durumu
    var story by mutableStateOf<Story?>(null)
        private set
    var page by mutableStateOf<StoryPage?>(null)
        private set
    var pageNumber by mutableIntStateOf(0)
        private set
    var pageCount by mutableIntStateOf(0)
        private set
    var image by mutableStateOf<ImageBitmap?>(null)
        private set
    /** Resim her değiştiğinde artar: yavaş yakınlaşma baştan başlar. */
    var imageVersion by mutableIntStateOf(0)
        private set
    var titleCard by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set
    var showText by mutableStateOf(true)
    var notice by mutableStateOf<String?>(null)
        private set
    var mood by mutableStateOf(Mood.CALM)
        private set

    private val player = PcmPlayer()
    private val lullaby = Lullaby()
    private var job: Job? = null
    private var pageJob: Job? = null
    private var nav = 0
    private var choice: CompletableDeferred<Boolean>? = null
    private val loading = HashMap<String, Deferred<PageAssets>>()
    private var lastAction: (() -> Unit)? = null

    fun savePicks() {
        prefs.edit().putInt("hero", hero).putInt("place", place).putInt("theme", theme)
            .putInt("length", length.ordinal).putInt("images", imageMode.ordinal)
            .putString("narrator", narrator.name).putBoolean("music", music).putBoolean("sleep", sleepAfter).apply()
    }

    val heroLabel get() = HEROES[hero].label
    val placeLabel get() = PLACES[place].label
    val themeLabel get() = THEMES[theme].label

    fun cycleHero(d: Int) { hero = (hero + d).mod(HEROES.size) }
    fun cyclePlace(d: Int) { place = (place + d).mod(PLACES.size) }
    fun cycleTheme(d: Int) { theme = (theme + d).mod(THEMES.size) }

    fun libraryCount(): Int = library.list(narrator.id).size

    // ---------------- Başlat ----------------

    fun newStory() {
        if (key.isEmpty()) {
            stage = Stage.Error(tx("Masal Makinesi Gemini ile çalışır (yazı, resim ve ses). Ayarlar → Claude'a sor → Gemini API anahtarı'nı gir.",
                "The Story Machine runs on Gemini (text, pictures and voice). Add your key in Settings → Ask Claude → Gemini API key."))
            return
        }
        savePicks()
        lastAction = { newStory() }
        dev.litedroid.launcher.wrapped.WrappedStats.count(context, "story.new")
        dev.litedroid.launcher.wrapped.WrappedStats.count(context, "story.hero." + HEROES[hero].label)
        val en = !tx("tr", "en").startsWith("tr")
        val req = StoryRequest(
            hero = HEROES[hero].prompt ?: HEROES[hero].label,
            place = PLACES[place].prompt ?: PLACES[place].label,
            theme = THEMES[theme].prompt ?: THEMES[theme].label,
            introPages = length.intro,
            branchPages = length.branch,
            narratorVoice = narrator.id,
            english = en,
        )
        job?.cancel()
        job = scope.launch {
            val lines = listOf(
                tx("Kalemler açılıyor ✏️", "Sharpening the pencils ✏️"),
                tx("Karakterler sahneye çıkıyor 🎭", "The characters take the stage 🎭"),
                tx("Yıldızlar yerine yerleştiriliyor ✨", "Placing the stars ✨"),
                tx("Masalcı çayını yudumluyor 🍵", "The storyteller sips some tea 🍵"),
            )
            val ticker = launch {
                var i = 0
                while (true) {
                    stage = Stage.Preparing(tx("Masal yazılıyor…", "Writing your story…"), lines[i % lines.size])
                    i++
                    delay(3_000)
                }
            }
            if (music) {
                lullaby.setMood(Mood.CALM)
                lullaby.start()
            }
            val s = try {
                StoryWriter.write(key, settings.geminiModel.id, req)
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                ticker.cancel()
                Log.w(TAG, "write failed", t)
                lullaby.fadeOut()
                stage = Stage.Error(GeminiMedia.describe(t))
                return@launch
            }
            ticker.cancel()
            library.save(s)
            play(s)
        }
    }

    fun replay(s: Story) {
        lastAction = { replay(s) }
        dev.litedroid.launcher.wrapped.WrappedStats.count(context, "story.replay")
        job?.cancel()
        job = scope.launch {
            if (music) lullaby.start()
            play(s)
        }
    }

    fun retry() {
        lastAction?.invoke() ?: run { stage = Stage.Setup }
    }

    // ---------------- Hazırlık (bir sayfa önden) ----------------

    private fun wantsImage(s: Story, p: StoryPage): Boolean {
        val inIntro = s.intro.indexOf(p)
        val idx = if (inIntro >= 0) inIntro else maxOf(s.branchA.indexOf(p), s.branchB.indexOf(p)) + s.intro.size
        return when (imageMode) {
            ImageMode.ALL -> true
            ImageMode.HALF -> idx % 2 == 0
            ImageMode.COVER -> idx == 0
            ImageMode.NONE -> false
        }
    }

    private fun imagePrompt(s: Story, p: StoryPage): String {
        val cast = s.characters.joinToString("; ") { "${it.name}: ${it.look}" }
        return "${s.artStyle}. Children's picture-book illustration, wide 16:9 cinematic scene, gentle, cozy and colorful, " +
            "absolutely no text, letters or words in the image. Characters (keep them consistent): $cast. Scene: ${p.imagePrompt}"
    }

    private fun turns(s: Story, lines: List<StoryLine>) = lines.map { l ->
        val c = s.character(l.speaker)
        GeminiMedia.Turn(c?.name ?: "Narrator", l.text, c?.voice ?: s.narratorVoice, c?.style ?: NARRATOR_STYLE)
    }

    private fun ensure(s: Story, id: String, lines: List<StoryLine>, imagePage: StoryPage?): Deferred<PageAssets> =
        loading.getOrPut("${s.id}/$id") {
            scope.async(Dispatchers.IO) {
                val img = async<Throwable?> {
                    if (imagePage == null || library.imageFile(s, id).exists()) return@async null
                    if (key.isEmpty()) return@async null
                    runCatching { library.imageFile(s, id).writeBytes(GeminiMedia.image(context, key, imagePrompt(s, imagePage))) }
                        .exceptionOrNull()?.also { Log.w(TAG, "image $id failed: ${it.message}") }
                }
                val audio = async<Pair<Pcm?, Throwable?>> {
                    library.loadAudio(s, id)?.let { return@async it to null }
                    if (key.isEmpty()) return@async null to IllegalStateException(tx("Gemini anahtarı yok", "No Gemini key"))
                    try {
                        val pcm = GeminiMedia.speak(context, key, turns(s, lines), GeminiMedia.Purpose.STORY)
                        library.audioFile(s, id).writeBytes(pcm.toWav())
                        pcm to null
                    } catch (c: kotlinx.coroutines.CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        Log.w(TAG, "audio $id failed: ${t.message}")
                        null to t
                    }
                }
                val imgErr = img.await()
                val (pcm, err) = audio.await()
                PageAssets(pcm, library.imageFile(s, id).exists(), err ?: imgErr.takeIf { pcm == null })
            }
        }

    private fun ensurePage(s: Story, p: StoryPage) = ensure(s, p.id, p.lines, if (wantsImage(s, p)) p else null)

    private fun questionLines(c: StoryChoice) = listOf(StoryLine(Story.NARRATOR,
        "${c.question} <short pause> ${c.a}, <short pause> " + tx("ya da", "or") + " ${c.b}?"))

    // ---------------- Oynatma ----------------

    private suspend fun play(s: Story) {
        story = s
        choice = null
        var choseA: Boolean? = null
        paused = false
        player.resume()
        val first = s.intro.first()
        stage = Stage.Preparing(tx("Masal hazırlanıyor…", "Getting the story ready…"), tx("İlk resim çiziliyor, anlatıcı sesini hazırlıyor 🎨🎙️", "Painting the first picture, warming up the narrator 🎨🎙️"))
        val titleAudio = ensure(s, "title", listOf(StoryLine(Story.NARRATOR, "${s.title}. <long pause>")), null)
        val firstAssets = ensurePage(s, first).await()
        if (firstAssets.audio == null && firstAssets.error != null) {
            lullaby.fadeOut()
            stage = Stage.Error(GeminiMedia.describe(firstAssets.error))
            return
        }
        // Kapak: ilk resim ve masalın adı
        showImageFor(s, first.id)
        mood = first.mood
        lullaby.setMood(first.mood)
        page = null
        titleCard = true
        stage = Stage.Playing
        titleAudio.await().audio?.let { speak(it) } ?: delay(2_500)
        delay(600)
        titleCard = false

        var i = 0
        while (true) {
            val list = s.pages(choseA)
            if (i >= list.size) {
                val c = s.choice
                if (choseA == null && c != null) {
                    choseA = askChoice(s, c)
                    stage = Stage.Playing
                    continue
                }
                if (choseA == null && c == null && s.branchA.isNotEmpty()) {
                    choseA = true
                    continue
                }
                break
            }
            val p = list[i]
            // Sonraki sayfa (ve seçimden önce iki dalın ilk sayfası) arkada hazırlanır
            list.getOrNull(i + 1)?.let { ensurePage(s, it) }
            if (i == s.intro.lastIndex && choseA == null && s.choice != null) {
                ensure(s, "q", questionLines(s.choice), null)
                s.branchA.firstOrNull()?.let { ensurePage(s, it) }
                s.branchB.firstOrNull()?.let { ensurePage(s, it) }
            }
            val assets = ensurePage(s, p).await()
            page = p
            pageNumber = i + 1
            pageCount = list.size + if (choseA == null && s.choice != null) s.branchA.size else 0
            mood = p.mood
            lullaby.setMood(p.mood)
            if (assets.hasImage) showImageFor(s, p.id)
            notice = if (assets.audio == null) tx("Bu sayfanın sesi hazırlanamadı, yazıdan okuyabilirsin.", "This page's audio couldn't be made; read it on screen.") else null
            nav = 0
            pageJob = scope.launch {
                val a = assets.audio
                if (a != null) speak(a) else waitWhileReading(p)
                delay(1_100)
            }
            pageJob?.join()
            i = when (nav) {
                -1 -> maxOf(0, i - 1)
                else -> i + 1
            }
        }
        // Son
        page = null
        stage = Stage.End
        lullaby.setMood(Mood.SLEEPY)
        if (sleepAfter) {
            SleepTimer.startIn(context, 1)
            delay(25_000)
            lullaby.fadeOut()
        }
    }

    private suspend fun showImageFor(s: Story, id: String) {
        val bmp: Bitmap = library.loadImage(s, id) ?: return
        image = bmp.asImageBitmap()
        imageVersion++
    }

    private suspend fun speak(pcm: Pcm) {
        lullaby.duck(true)
        try {
            player.play(pcm)
        } finally {
            lullaby.duck(false)
        }
    }

    private suspend fun waitWhileReading(p: StoryPage) {
        val words = p.lines.sumOf { it.text.split(' ').size }
        var left = words * 420L
        while (left > 0) {
            delay(100)
            if (!paused) left -= 100
        }
    }

    private suspend fun askChoice(s: Story, c: StoryChoice): Boolean {
        val d = CompletableDeferred<Boolean>()
        choice = d
        stage = Stage.Choosing(c)
        lullaby.setMood(Mood.MYSTERY)
        val q = ensure(s, "q", questionLines(c), null)
        val speakJob = scope.launch { q.await().audio?.let { speak(it) } }
        val picked = d.await()
        speakJob.cancel()
        player.stop()
        choice = null
        return picked
    }

    // ---------------- Kumanda ----------------

    fun pick(a: Boolean) {
        choice?.complete(a)
    }

    fun togglePause() {
        paused = !paused
        if (paused) player.pause() else player.resume()
    }

    fun next() {
        if (titleCard) return
        nav = 1
        if (paused) togglePause()
        player.stop()
        pageJob?.cancel()
    }

    fun previous() {
        if (titleCard) return
        nav = -1
        if (paused) togglePause()
        player.stop()
        pageJob?.cancel()
    }

    fun openLibrary() {
        stage = Stage.Library
    }

    /** Geri tuşu: alt ekranlardan kurulum ekranına döner. */
    fun back() {
        job?.cancel()
        pageJob?.cancel()
        choice?.cancel()
        player.stop()
        lullaby.fadeOut()
        titleCard = false
        page = null
        notice = null
        stage = Stage.Setup
    }

    fun cancelSleep() {
        SleepTimer.cancel(context)
        sleepAfter = false
    }

    fun close() {
        job?.cancel()
        pageJob?.cancel()
        player.stop()
        lullaby.stop()
        loading.values.forEach { it.cancel() }
    }

    companion object {
        const val TAG = "LiteDroidStory"
    }
}

// ---------------------------------------------------------------------------
// Ekranlar
// ---------------------------------------------------------------------------

private val Night = Color(0xFF070816)
private val Gold = Color(0xFFFFD27A)
private val Lilac = Color(0xFFB9A8FF)

/** Masal Makinesi: tam ekran. Geri tuşu alt ekranlardan kuruluma, kurulumdan dışarı çıkar. */
@Composable
fun StoryScreen(modifier: Modifier = Modifier, onExit: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ctrl = remember { StoryController(context.applicationContext, scope) }
    DisposableEffect(Unit) { onDispose { ctrl.close() } }
    BackHandler(enabled = ctrl.stage != Stage.Setup) { ctrl.back() }

    Box(modifier.fillMaxSize().background(Night)) {
        Stars(Modifier.fillMaxSize())
        when (val s = ctrl.stage) {
            // Gemini anahtarı yoksa yeni masal yazılamaz: kilit ekranı (kayıtlı masallar dinlenebilir)
            Stage.Setup -> if (ctrl.key.isEmpty()) LockedView(ctrl, onExit) else SetupView(ctrl, onExit)
            Stage.Library -> LibraryView(ctrl)
            is Stage.Preparing -> PreparingView(s.title, s.detail)
            Stage.Playing, is Stage.Choosing, Stage.End -> PlayerView(ctrl, s, onExit)
            is Stage.Error -> ErrorView(ctrl, s.message)
        }
    }
}

/** Yavaşça parıldayan yıldızlı gece. */
@Composable
private fun Stars(modifier: Modifier) {
    val t by rememberInfiniteTransition(label = "stars").animateFloat(0f, 1f, infiniteRepeatable(tween(6_000, easing = LinearEasing)), label = "t")
    val stars = remember { List(70) { Triple(Math.random().toFloat(), Math.random().toFloat(), Math.random().toFloat()) } }
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0D26), Color(0xFF1A1240), Color(0xFF07081A))))
        stars.forEach { (x, y, p) ->
            val a = 0.25f + 0.6f * (0.5f + 0.5f * kotlin.math.sin((t + p) * 2 * Math.PI.toFloat()))
            drawCircle(Color.White.copy(alpha = a), radius = 1.2f + p * 1.8f, center = Offset(x * size.width, y * size.height * 0.85f))
        }
    }
}

// ---------------- Kurulum ----------------

@Composable
private fun SetupView(ctrl: StoryController, onExit: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { first.requestFocus() }
    }
    val libCount = remember { ctrl.libraryCount() }
    Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 22.dp)) {
        Column(Modifier.weight(1.15f).fillMaxHeight()) {
            Text("🌙 " + tx("Masal Makinesi", "Story Machine"), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text(tx("Seç, yapay zekâ yazsın, çizsin ve anlatsın", "Pick, and AI writes, draws and tells it"), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Picker(tx("Kahraman", "Hero"), ctrl.heroLabel, Modifier.focusRequester(first)) { ctrl.cycleHero(it) }
            Picker(tx("Yer", "Place"), ctrl.placeLabel) { ctrl.cyclePlace(it) }
            Picker(tx("Konu", "Theme"), ctrl.themeLabel) { ctrl.cycleTheme(it) }
            Picker(tx("Uzunluk", "Length"), ctrl.length.label) { ctrl.length = StoryLength.entries[(ctrl.length.ordinal + it).mod(StoryLength.entries.size)] }
            Picker(tx("Anlatıcı", "Narrator"), ctrl.narrator.label) { ctrl.narrator = GeminiVoice.entries[(ctrl.narrator.ordinal + it).mod(GeminiVoice.entries.size)] }
            Picker(tx("Resimler", "Pictures"), ctrl.imageMode.label) { ctrl.imageMode = ImageMode.entries[(ctrl.imageMode.ordinal + it).mod(ImageMode.entries.size)] }
            Picker(tx("Müzik", "Music"), if (ctrl.music) tx("Açık", "On") else tx("Kapalı", "Off")) { ctrl.music = !ctrl.music }
            Picker(tx("Bitince", "At the end"), if (ctrl.sleepAfter) tx("TV kapansın", "Turn the TV off") else tx("TV açık kalsın", "Keep the TV on")) { ctrl.sleepAfter = !ctrl.sleepAfter }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("✨ " + tx("Masalı başlat", "Start the story"), primary = true) { ctrl.newStory() }
                Pill("📚 " + tx("Kitaplık", "Library") + if (libCount > 0) " ($libCount)" else "", primary = false) {
                    ctrl.savePicks()
                    ctrl.openLibrary()
                }
                Pill("✕ " + tx("Çık", "Exit"), primary = false) {
                    ctrl.savePicks()
                    onExit()
                }
            }
        }
        Spacer(Modifier.width(28.dp))
        // Sağ: seçimlerin masal cümlesi
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = 30.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2B1F63), Color(0xFF132046))))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(tx("BU AKŞAMIN MASALI", "TONIGHT'S STORY"), color = Lilac, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                tx("${ctrl.heroLabel.lowercase()}, ${ctrl.placeLabel.lowercase()} ve ${ctrl.themeLabel.lowercase()} üzerine bir masal.",
                    "${ctrl.heroLabel}, ${ctrl.placeLabel.lowercase()}, a story about ${ctrl.themeLabel.lowercase()}.").replaceFirstChar { it.uppercase() },
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp,
            )
            Spacer(Modifier.height(16.dp))
            val pages = ctrl.length.intro + ctrl.length.branch
            Text(
                tx("$pages sayfa · ortada bir seçim anı · ${ctrl.narrator.id} anlatıyor", "$pages pages · one choice in the middle · ${ctrl.narrator.id} narrates"),
                color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                tx("◀ ▶ ile seç · Tamam: sonraki seçenek\nMasal sırasında: Tamam duraklat · ▶ sonraki · ◀ önceki · ▼ yazı",
                    "◀ ▶ to choose · OK: next option\nDuring the story: OK pause · ▶ next · ◀ previous · ▼ text"),
                color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, lineHeight = 16.sp,
            )
            if (ctrl.key.isEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(tx("Gemini API anahtarı gerekli: Ayarlar → Claude'a sor.", "Needs a Gemini API key: Settings → Ask Claude."), color = Color(0xFFFFB4A8), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Picker(label: String, value: String, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .padding(vertical = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.06f))
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent e.key == Key.DirectionLeft || e.key == Key.DirectionRight || e.key.isSelectKey
                when {
                    e.key == Key.DirectionLeft -> { onChange(-1); true }
                    e.key == Key.DirectionRight || e.key.isSelectKey -> { onChange(1); true }
                    else -> false
                }
            }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (focused) Color(0xFF14131C) else Color.White
        Text(label, color = fg.copy(alpha = 0.65f), fontSize = 13.sp, modifier = Modifier.width(110.dp))
        Text(if (focused) "◀" else "", color = fg.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.width(16.dp))
        Text(value, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (focused) "▶" else "", color = fg.copy(alpha = 0.5f), fontSize = 12.sp)
    }
}

@Composable
private fun Pill(label: String, primary: Boolean, modifier: Modifier = Modifier, onLongPress: (() -> Unit)? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    focused -> Color.White
                    primary -> Color(0xFF7B5CFF)
                    else -> Color(0xFF3A3658)
                }
            )
            .border(1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onClick, onLongPress)
            .focusable()
            .padding(horizontal = 22.dp, vertical = 11.dp),
    ) {
        Text(label, color = if (focused) Color(0xFF111114) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------- Hazırlanıyor / hata ----------------

@Composable
private fun PreparingView(title: String, detail: String) {
    val pulse by rememberInfiniteTransition(label = "p").animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(1_200), RepeatMode.Reverse), label = "s")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("📖", fontSize = 64.sp, modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse })
        Spacer(Modifier.height(16.dp))
        Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Crossfade(detail, label = "d") { d -> Text(d, color = Gold, fontSize = 16.sp) }
        Spacer(Modifier.height(22.dp))
        Text(tx("İlk seferde 20-40 saniye sürebilir. Sonraki sayfalar sen dinlerken hazırlanır.", "The first time can take 20-40 seconds. Later pages get ready while you listen."),
            color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
    }
}

@Composable
private fun LockedView(ctrl: StoryController, onExit: () -> Unit) {
    val fr = remember { FocusRequester() }
    val saved = remember { runCatching { ctrl.libraryCount() }.getOrDefault(0) }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { fr.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("🔒", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text(tx("Masal Makinesi yapay zekâ ister", "The Story Machine needs AI"), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Text(
            tx("Masalları Gemini yazar, çizer ve anlatır. Kullanmak için Ayarlar → Claude'a sor → Gemini API anahtarı'nı gir.",
                "Gemini writes, illustrates and narrates the stories. Add your key in Settings → Ask Claude → Gemini API key.") +
                if (saved > 0) tx("\nDaha önce yapılan $saved masalı anahtarsız da dinleyebilirsin.", "\nYou can still replay your $saved saved stories without a key.") else "",
            color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 700.dp),
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (saved > 0) Pill(tx("Kayıtlı masallar", "Saved stories"), true, Modifier.focusRequester(fr)) { ctrl.openLibrary() }
            Pill(tx("Çık", "Exit"), saved == 0, if (saved == 0) Modifier.focusRequester(fr) else Modifier) { onExit() }
        }
    }
}

@Composable
private fun ErrorView(ctrl: StoryController, message: String) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { fr.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("🌧️", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text(tx("Masal bu sefer olmadı", "The story didn't work this time"), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Text(message, color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 700.dp))
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(tx("Tekrar dene", "Try again"), true, Modifier.focusRequester(fr)) { ctrl.retry() }
            Pill(tx("Geri", "Back"), false) { ctrl.back() }
        }
    }
}

// ---------------- Oynatıcı ----------------

@Composable
private fun PlayerView(ctrl: StoryController, stage: Stage, onExit: () -> Unit) {
    val fr = remember { FocusRequester() }
    val playing = stage == Stage.Playing
    LaunchedEffect(playing) {
        if (playing) {
            delay(80)
            runCatching { fr.requestFocus() }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(fr)
            .onPreviewKeyEvent { e ->
                if (!playing) return@onPreviewKeyEvent false
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent e.key.isSelectKey
                when {
                    e.key.isSelectKey -> { if (e.nativeKeyEvent.repeatCount == 0) ctrl.togglePause(); true }
                    e.key == Key.DirectionRight -> { ctrl.next(); true }
                    e.key == Key.DirectionLeft -> { ctrl.previous(); true }
                    e.key == Key.DirectionDown || e.key == Key.DirectionUp -> { ctrl.showText = !ctrl.showText; true }
                    else -> false
                }
            }
            .focusable()
    ) {
        // Resim: sayfalar arasında yumuşak geçiş, sayfa boyunca yavaş yakınlaşma
        Crossfade(ctrl.image to ctrl.imageVersion, animationSpec = tween(1_400), label = "img") { (img, v) ->
            if (img != null) KenBurns(img, v, ctrl.paused) else MoodBackdrop(ctrl.mood)
        }
        // Alt kısımda yazı okunsun diye karartma
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f))))

        val s = ctrl.story
        if (s != null && !ctrl.titleCard && stage == Stage.Playing) {
            Row(Modifier.align(Alignment.TopStart).padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip("📖 ${s.title}")
                Spacer(Modifier.width(8.dp))
                if (ctrl.pageCount > 0) Chip("${ctrl.pageNumber} / ${ctrl.pageCount}")
            }
        }

        AnimatedVisibility(ctrl.titleCard, enter = fadeIn(tween(900)), exit = fadeOut(tween(900)), modifier = Modifier.align(Alignment.Center)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
                Text(tx("Bir varmış, bir yokmuş…", "Once upon a time…"), color = Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(s?.title.orEmpty(), color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, lineHeight = 48.sp)
            }
        }

        val page = ctrl.page
        AnimatedVisibility(
            page != null && ctrl.showText && stage == Stage.Playing && !ctrl.titleCard,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (page != null && s != null) PageText(s, page, ctrl.notice)
        }

        if (ctrl.paused && stage == Stage.Playing) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Text("❚❚", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text(tx("Duraklatıldı · Tamam: devam", "Paused · OK: resume"), color = Color.White, fontSize = 14.sp)
            }
        }

        if (stage is Stage.Choosing) ChoiceView(ctrl, stage.choice)
        if (stage == Stage.End) EndView(ctrl, onExit)
    }
}

@Composable
private fun KenBurns(img: ImageBitmap, version: Int, paused: Boolean) {
    val p = remember(version) { Animatable(0f) }
    LaunchedEffect(version, paused) {
        if (!paused) p.animateTo(1f, tween(((1f - p.value) * 26_000).toInt().coerceAtLeast(1), easing = LinearEasing))
    }
    val dir = if (version % 2 == 0) 1f else -1f
    Image(
        img, contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            val v = p.value
            scaleX = 1.05f + 0.11f * v
            scaleY = scaleX
            translationX = dir * (v - 0.5f) * 46.dp.toPx()
            translationY = (v - 0.5f) * -14.dp.toPx()
        },
    )
}

/** Resim yokken (ya da henüz gelmediyse) sayfanın havasında renkli bir gökyüzü. */
@Composable
private fun MoodBackdrop(mood: Mood) {
    val colors = when (mood) {
        Mood.HAPPY -> listOf(Color(0xFFFFB86B), Color(0xFFE85D9A), Color(0xFF3B2A86))
        Mood.MYSTERY -> listOf(Color(0xFF1B2A6B), Color(0xFF2C1650), Color(0xFF07081A))
        Mood.ADVENTURE -> listOf(Color(0xFF2E9BD6), Color(0xFF3B4FB8), Color(0xFF1A1240))
        Mood.SLEEPY -> listOf(Color(0xFF2A2563), Color(0xFF140F36), Color(0xFF05060F))
        Mood.CALM -> listOf(Color(0xFF4C6ED8), Color(0xFF3A2A86), Color(0xFF0E0B26))
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colors))) {
        Stars(Modifier.fillMaxSize().graphicsLayer { alpha = 0.6f })
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

private val TAG_RE = Regex("<[^>]{1,24}>")

@Composable
private fun PageText(s: Story, page: StoryPage, notice: String?) {
    Column(
        Modifier
            .padding(horizontal = 60.dp, vertical = 22.dp)
            .widthIn(max = 980.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.42f))
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        page.lines.forEach { l ->
            val text = l.text.replace(TAG_RE, "").replace(Regex("\\s+"), " ").trim()
            val c = s.character(l.speaker)
            if (c != null) {
                Text(c.name.uppercase(), color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("“$text”", color = Color.White, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text(text, color = Color.White.copy(alpha = 0.92f), fontSize = 18.sp, lineHeight = 25.sp)
            }
            Spacer(Modifier.height(4.dp))
        }
        if (notice != null) Text(notice, color = Color(0xFFFFB4A8), fontSize = 11.sp)
    }
}

// ---------------- Seçim ----------------

@Composable
private fun BoxScope.ChoiceView(ctrl: StoryController, c: StoryChoice) {
    val frA = remember { FocusRequester() }
    LaunchedEffect(c) {
        delay(150)
        runCatching { frA.requestFocus() }
    }
    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.55f)))
    Column(Modifier.align(Alignment.Center).padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tx("SEN KARAR VER", "YOU DECIDE"), color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Spacer(Modifier.height(10.dp))
        Text(c.question.replace(TAG_RE, ""), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, lineHeight = 34.sp, modifier = Modifier.widthIn(max = 820.dp))
        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            ChoiceCard("A", c.a, Color(0xFF3BA7FF), Modifier.focusRequester(frA)) { ctrl.pick(true) }
            ChoiceCard("B", c.b, Color(0xFFFF7AB6)) { ctrl.pick(false) }
        }
        Spacer(Modifier.height(16.dp))
        Text(tx("◀ ▶ seç · Tamam: bu olsun", "◀ ▶ choose · OK: this one"), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
    }
}

@Composable
private fun ChoiceCard(letter: String, text: String, accent: Color, modifier: Modifier = Modifier, onPick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (focused) 1.06f else 1f, label = "sc")
    Column(
        modifier
            .width(300.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(26.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.1f))
            .border(2.dp, if (focused) accent else Color.White.copy(alpha = 0.2f), RoundedCornerShape(26.dp))
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onPick)
            .focusable()
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Text(letter, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(12.dp))
        Text(text, color = if (focused) Color(0xFF14131C) else Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

// ---------------- Son ----------------

@Composable
private fun BoxScope.EndView(ctrl: StoryController, onExit: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(400)
        runCatching { fr.requestFocus() }
    }
    val glow by rememberInfiniteTransition(label = "g").animateFloat(0.6f, 1f, infiniteRepeatable(tween(2_200), RepeatMode.Reverse), label = "a")
    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.5f)))
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🌙", fontSize = 60.sp, modifier = Modifier.graphicsLayer { alpha = glow })
        Text(tx("Son", "The End"), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
        Text(tx("Tatlı rüyalar…", "Sweet dreams…"), color = Gold, fontSize = 18.sp)
        Spacer(Modifier.height(20.dp))
        if (ctrl.sleepAfter) {
            Text(tx("TV birazdan kendiliğinden kapanacak. İyi geceler!", "The TV will turn off shortly. Good night!"), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val s = ctrl.story
            Pill(tx("Yeniden dinle", "Listen again"), true, Modifier.focusRequester(fr)) { s?.let { ctrl.replay(it) } }
            Pill(tx("Yeni masal", "New story"), false) { ctrl.back() }
            if (ctrl.sleepAfter) Pill(tx("Kapanmasın", "Keep TV on"), false) { ctrl.cancelSleep() }
            Pill("✕ " + tx("Çık", "Exit"), false) { onExit() }
        }
    }
}

// ---------------- Kitaplık ----------------

@Composable
private fun LibraryView(ctrl: StoryController) {
    var stories by remember { mutableStateOf<List<Story>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) { stories = withContext(Dispatchers.IO) { ctrl.library.list(ctrl.narrator.id) } }
    val fr = remember { FocusRequester() }
    LaunchedEffect(stories) {
        delay(120)
        runCatching { fr.requestFocus() }
    }
    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 26.dp)) {
        Text("📚 " + tx("Masal kitaplığı", "Story library"), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(tx("Kaydedilen masallar ücretsiz yeniden dinlenir · Tamam: oynat · Basılı tut: sil · Geri: dön",
            "Saved stories replay for free · OK: play · Hold: delete · Back: return"), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        Spacer(Modifier.height(22.dp))
        val list = stories
        when {
            list == null -> Unit
            list.isEmpty() -> {
                Text(tx("Henüz masal yok. İlk masalı başlat, burada saklanır.", "No stories yet. Start one and it will be kept here."), color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Pill(tx("Geri", "Back"), true, Modifier.focusRequester(fr)) { ctrl.back() }
            }
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                    LibraryCard(
                        ctrl, s, fmt.format(Date(s.createdAt)),
                        if (i == 0) Modifier.focusRequester(fr) else Modifier,
                        onDelete = {
                            ctrl.library.delete(s)
                            refresh++
                        },
                    ) { ctrl.replay(s) }
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(ctrl: StoryController, s: Story, date: String, modifier: Modifier, onDelete: () -> Unit, onPlay: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val cover by produceState<ImageBitmap?>(null, s.id) {
        value = s.intro.firstOrNull()?.let { ctrl.library.loadImage(s, it.id) }?.asImageBitmap()
    }
    Column(
        modifier
            .width(300.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.08f))
            .border(2.dp, if (focused) Gold else Color.Transparent, RoundedCornerShape(20.dp))
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onPlay, onDelete)
            .focusable(),
    ) {
        Box(Modifier.fillMaxWidth().height(168.dp).background(Brush.linearGradient(listOf(Color(0xFF3A2A86), Color(0xFF132046))))) {
            cover?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                ?: Text("🌙", fontSize = 44.sp, modifier = Modifier.align(Alignment.Center))
        }
        Column(Modifier.padding(14.dp)) {
            Text(s.title, color = if (focused) Color(0xFF14131C) else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("$date · ${s.intro.size + s.branchA.size} " + tx("sayfa", "pages"), color = (if (focused) Color(0xFF14131C) else Color.White).copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}
