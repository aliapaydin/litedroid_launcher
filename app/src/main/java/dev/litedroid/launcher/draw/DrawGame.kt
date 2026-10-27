package dev.litedroid.launcher.draw

import android.content.Context
import android.util.Log
import dev.litedroid.launcher.data.GeminiMedia
import dev.litedroid.launcher.data.SettingsRepository
import dev.litedroid.launcher.quiz.QuizGame
import dev.litedroid.launcher.tx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.util.Locale
import java.util.UUID

data class DrawPlayer(
    val id: String,
    val name: String,
    val avatar: String,
    val color: Int,
    val score: Int = 0,
    /** Bu turda bildi mi? */
    val guessed: Boolean = false,
    /** Bu turda kazandığı puan (çizen için bilenlerden gelen toplam). */
    val lastGain: Int = 0,
    val lastSeen: Long = System.currentTimeMillis(),
)

enum class DrawPhase { LOBBY, CHOOSE, DRAW, AI_LOADING, REVEAL, FINISHED }

/** TV'de sağda akan tahminler: yanlış tahminler yazısıyla, doğrular "bildi!" olarak. */
data class FeedItem(val id: Long, val avatar: String, val name: String, val color: Int, val text: String, val correct: Boolean)

data class DrawState(
    val phase: DrawPhase = DrawPhase.LOBBY,
    val players: List<DrawPlayer> = emptyList(),
    /** Herkes kaç kez çizecek. */
    val roundsEach: Int = 2,
    val drawSeconds: Int = 80,
    /** Her turun sonunda bir "Yapay zekâ çizsin" turu. */
    val aiRound: Boolean = true,
    /** Gemini anahtarı var mı (yoksa yapay zekâ turu kapalı). */
    val aiAvailable: Boolean = false,
    /** 0'dan başlayan tur sırası ve toplam tur. */
    val round: Int = 0,
    val totalRounds: Int = 0,
    val drawerId: String? = null,
    val isAi: Boolean = false,
    val word: DrawWord? = null,
    val choices: List<DrawWord> = emptyList(),
    /** İpucu olarak sırayla açılacak harflerin konumları. */
    val hintSlots: List<Int> = emptyList(),
    val phaseEndsAt: Long = 0L,
    val phaseMs: Long = 0L,
    val feed: List<FeedItem> = emptyList(),
    /** Kısa bilgi (ör. "Yapay zekâ bu sefer çizemedi"). */
    val note: String? = null,
) {
    val ranked: List<DrawPlayer> get() = players.sortedWith(compareByDescending<DrawPlayer> { it.score }.thenBy { it.name })
    val drawer: DrawPlayer? get() = players.firstOrNull { it.id == drawerId }
    /** Bu turda tahmin edenler: yapay zekâ turunda herkes, normal turda çizen hariç herkes. */
    val guessers: List<DrawPlayer> get() = if (isAi) players else players.filter { it.id != drawerId }

    /** Şu ana kadar açılan ipucu harfleri: sürenin yarısında bir, dörtte üçünde bir tane daha. */
    fun revealed(now: Long = System.currentTimeMillis()): Set<Int> {
        if (phase == DrawPhase.REVEAL) return word?.label?.indices?.toSet().orEmpty()
        if (phase != DrawPhase.DRAW || phaseMs <= 0) return emptySet()
        val frac = 1f - (phaseEndsAt - now).toFloat() / phaseMs
        val n = (if (frac >= 0.5f) 1 else 0) + (if (frac >= 0.75f) 1 else 0)
        return hintSlots.take(n).toSet()
    }

    /** Tahmin edenlerin gördüğü kalıp: "K _ _ _   _ _ _" gibi. */
    fun pattern(now: Long = System.currentTimeMillis()): String {
        val w = word?.label ?: return ""
        val open = revealed(now)
        return w.mapIndexed { i, c -> if (c == ' ') " " else if (i in open) c.uppercase(Locale.forLanguageTag("tr")) else "_" }.joinToString(" ")
    }
}

/** Telefondan gelen çizgiler. Koordinatlar tahtanın 0..1 aralığında, kalınlık tahta genişliğine oranla. */
class DrawBoard {
    class Stroke(val id: Int, val color: Int, val width: Float) {
        val pts = ArrayList<Float>()
    }

    /** TV'nin çizdiği kopya. */
    class Snap(val color: Int, val width: Float, val pts: FloatArray)

    private val strokes = ArrayList<Stroke>()
    private val _version = MutableStateFlow(0)
    /** Her değişiklikte artar; TV bunu izleyip yeniden çizer. */
    val version: StateFlow<Int> = _version

    @Synchronized
    fun clear() {
        strokes.clear()
        _version.value++
    }

    /** Telefonun gönderdiği işlemler: p = nokta ekle, u = geri al, x = temizle. */
    @Synchronized
    fun apply(ops: JSONArray) {
        for (i in 0 until ops.length()) {
            val o = ops.optJSONObject(i) ?: continue
            when (o.optString("t")) {
                "p" -> {
                    val id = o.optInt("s", -1)
                    var s = strokes.lastOrNull()?.takeIf { it.id == id }
                    if (s == null) {
                        if (strokes.size >= MAX_STROKES) continue
                        s = Stroke(id, parseColor(o.optString("c")), (o.optInt("w", 12).coerceIn(2, 120)) / 1000f)
                        strokes.add(s)
                    }
                    val p = o.optJSONArray("p") ?: continue
                    var k = 0
                    while (k + 1 < p.length() && s.pts.size < MAX_POINTS * 2) {
                        s.pts.add(p.optInt(k).coerceIn(0, 1000) / 1000f)
                        s.pts.add(p.optInt(k + 1).coerceIn(0, 1000) / 1000f)
                        k += 2
                    }
                }
                "u" -> if (strokes.isNotEmpty()) strokes.removeAt(strokes.size - 1)
                "x" -> strokes.clear()
            }
        }
        _version.value++
    }

    @Synchronized
    fun snapshot(): List<Snap> = strokes.map { Snap(it.color, it.width, it.pts.toFloatArray()) }

    private fun parseColor(s: String): Int =
        runCatching { (0xFF000000.toInt()) or s.removePrefix("#").toInt(16) }.getOrDefault(0xFF111114.toInt())

    companion object {
        private const val MAX_STROKES = 600
        private const val MAX_POINTS = 3_000
    }
}

/** Tahmin sonucu: telefona söylenir. */
enum class GuessResult { CORRECT, CLOSE, WRONG, ALREADY, NOT_ALLOWED }

/**
 * Çiz-Bil: sırası gelen oyuncu telefonunda gizli kelimeyi parmağıyla çizer, çizim TV'de canlı belirir;
 * diğerleri telefonlarından tahmin yazar, en hızlı bilen en çok puanı alır. Her turun sonunda
 * (Gemini anahtarı varsa) yapay zekâ çizer, evdeki herkes birlikte tahmin eder.
 * Aşamalar: LOBBY → (CHOOSE → DRAW | AI_LOADING → DRAW) → REVEAL → … → FINISHED.
 */
class DrawGame(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var phaseJob: Job? = null
    private var watchJob: Job? = null
    private val geminiKey: String = runCatching { SettingsRepository(app).load().geminiKey.trim() }.getOrDefault("")
    private val _state = MutableStateFlow(DrawState(aiAvailable = geminiKey.isNotBlank(), aiRound = geminiKey.isNotBlank()))
    val state: StateFlow<DrawState> = _state
    val board = DrawBoard()

    /** Yapay zekânın çizdiği resim (JPEG/PNG baytları); yalnızca yapay zekâ turunda dolu. */
    private val _aiImage = MutableStateFlow<ByteArray?>(null)
    val aiImage: StateFlow<ByteArray?> = _aiImage

    /** Tur sırası: oyuncu kimliği ya da null (yapay zekâ). */
    private var schedule: List<String?> = emptyList()
    private var lastSeq = -1
    private var feedId = 0L
    private val lastGuessAt = HashMap<String, Long>()
    private var aiPending: Deferred<Pair<DrawWord, ByteArray>?>? = null
    /** Yapay zekâ bir kez çizemediyse (kota, ağ, anahtar) bu oyunun kalan yapay zekâ turları atlanır. */
    private var aiBroken = false

    // ------------------------------------------------------------------
    // Oyuncular (sunucu iş parçacıklarından çağrılır)
    // ------------------------------------------------------------------

    @Synchronized
    fun join(name: String, avatar: String, pid: String?): DrawPlayer? {
        val clean = name.trim().take(16).ifBlank { tx("Oyuncu", "Player") }
        val s = _state.value
        s.players.firstOrNull { it.id == pid }?.let { existing ->
            val updated = existing.copy(name = clean, avatar = avatar.take(8), lastSeen = System.currentTimeMillis())
            _state.value = s.copy(players = s.players.map { if (it.id == existing.id) updated else it })
            return updated
        }
        if (s.players.size >= MAX_PLAYERS) return null
        val used = s.players.map { it.color }.toSet()
        val color = QuizGame.PLAYER_COLORS.firstOrNull { it !in used } ?: QuizGame.PLAYER_COLORS[s.players.size % QuizGame.PLAYER_COLORS.size]
        var unique = clean
        var n = 2
        while (s.players.any { it.name.equals(unique, true) }) unique = "$clean ${n++}"
        val p = DrawPlayer(UUID.randomUUID().toString().take(12), unique, avatar.take(8), color)
        _state.value = s.copy(players = s.players + p)
        return p
    }

    @Synchronized
    fun touch(pid: String) {
        val s = _state.value
        if (s.players.none { it.id == pid }) return
        _state.value = s.copy(players = s.players.map { if (it.id == pid) it.copy(lastSeen = System.currentTimeMillis()) else it })
    }

    @Synchronized
    fun remove(pid: String) {
        val s = _state.value
        _state.value = s.copy(players = s.players.filterNot { it.id == pid })
    }

    /** Çizen üç kelimeden birini seçti. */
    @Synchronized
    fun choose(pid: String, round: Int, index: Int): Boolean {
        val s = _state.value
        if (s.phase != DrawPhase.CHOOSE || s.round != round || s.drawerId != pid) return false
        val w = s.choices.getOrNull(index) ?: return false
        beginDraw(w, ai = false)
        return true
    }

    /** Çizenin telefonundan gelen çizgiler; aynı paket iki kez gelirse ([seq]) yok sayılır. */
    @Synchronized
    fun draw(pid: String, round: Int, seq: Int, ops: JSONArray): Boolean {
        val s = _state.value
        if (s.isAi || s.drawerId != pid || s.round != round) return false
        if (s.phase != DrawPhase.DRAW && s.phase != DrawPhase.CHOOSE) return false
        if (seq <= lastSeq) return true
        lastSeq = seq
        board.apply(ops)
        return true
    }

    @Synchronized
    fun guess(pid: String, text: String): Pair<GuessResult, Int> {
        val s = _state.value
        val w = s.word
        if (s.phase != DrawPhase.DRAW || w == null) return GuessResult.NOT_ALLOWED to 0
        val p = s.players.firstOrNull { it.id == pid } ?: return GuessResult.NOT_ALLOWED to 0
        if (!s.isAi && pid == s.drawerId) return GuessResult.NOT_ALLOWED to 0
        if (p.guessed) return GuessResult.ALREADY to 0
        val now = System.currentTimeMillis()
        if (now - (lastGuessAt[pid] ?: 0L) < 600) return GuessResult.WRONG to 0
        lastGuessAt[pid] = now
        val clean = text.trim().take(28)
        if (clean.isEmpty()) return GuessResult.WRONG to 0

        when (judge(clean, w)) {
            GuessResult.CORRECT -> {
                val frac = ((s.phaseEndsAt - now).toFloat() / s.phaseMs.coerceAtLeast(1)).coerceIn(0f, 1f)
                val first = s.players.none { it.guessed }
                val gain = ((100 + 300 * frac).toInt() / 10) * 10 + if (first) 50 else 0
                val drawerGain = ((50 + 50 * frac).toInt() / 10) * 10
                val players = s.players.map {
                    when {
                        it.id == pid -> it.copy(score = it.score + gain, guessed = true, lastGain = gain, lastSeen = now)
                        !s.isAi && it.id == s.drawerId -> it.copy(score = it.score + drawerGain, lastGain = it.lastGain + drawerGain)
                        else -> it
                    }
                }
                val item = FeedItem(++feedId, p.avatar, p.name, p.color, tx("bildi! +$gain", "got it! +$gain"), correct = true)
                _state.value = s.copy(players = players, feed = (s.feed + item).takeLast(FEED_SIZE))
                // Herkes bildiyse kısa bir an sonra turu bitir
                val st = _state.value
                if (st.guessers.isNotEmpty() && st.guessers.all { it.guessed }) {
                    val r = st.round
                    schedule(1_400) { if (_state.value.round == r) reveal() }
                }
                return GuessResult.CORRECT to gain
            }
            GuessResult.CLOSE -> return GuessResult.CLOSE to 0
            else -> {
                val item = FeedItem(++feedId, p.avatar, p.name, p.color, clean, correct = false)
                _state.value = s.copy(feed = (s.feed + item).takeLast(FEED_SIZE))
                return GuessResult.WRONG to 0
            }
        }
    }

    // ------------------------------------------------------------------
    // TV (kumanda) tarafı
    // ------------------------------------------------------------------

    @Synchronized
    fun setRoundsEach(n: Int) { if (_state.value.phase == DrawPhase.LOBBY) _state.value = _state.value.copy(roundsEach = n) }
    @Synchronized
    fun setDrawSeconds(n: Int) { if (_state.value.phase == DrawPhase.LOBBY) _state.value = _state.value.copy(drawSeconds = n) }
    @Synchronized
    fun setAiRound(on: Boolean) {
        val s = _state.value
        if (s.phase == DrawPhase.LOBBY && s.aiAvailable) _state.value = s.copy(aiRound = on)
    }

    /** Başlayabilir mi: iki oyuncu, ya da yapay zekâ turu açıksa tek oyuncu yeter. */
    fun canStart(s: DrawState = _state.value): Boolean =
        s.players.size >= 2 || (s.players.size == 1 && s.aiRound && s.aiAvailable)

    @Synchronized
    fun start() {
        val s = _state.value
        if (!canStart(s)) return
        val ai = s.aiRound && s.aiAvailable
        schedule = if (s.players.size >= 2) {
            buildList {
                repeat(s.roundsEach) {
                    addAll(s.players.map { it.id })
                    if (ai) add(null)
                }
            }
        } else List(s.roundsEach * 3) { null }
        _state.value = s.copy(
            players = s.players.map { it.copy(score = 0, guessed = false, lastGain = 0) },
            totalRounds = schedule.size,
            round = 0,
            note = null,
        )
        aiBroken = false
        if (null in schedule) prefetchAi()
        startWatchdog()
        nextTurn(0)
    }

    /** Kumandada Tamam: beklemeyi geçer, çizimi erken bitirir. */
    @Synchronized
    fun skip() {
        val s = _state.value
        when (s.phase) {
            DrawPhase.CHOOSE -> s.choices.randomOrNull()?.let { beginDraw(it, ai = false) }
            DrawPhase.DRAW -> reveal()
            DrawPhase.AI_LOADING -> nextTurn(s.round + 1)
            DrawPhase.REVEAL -> nextTurn(s.round + 1)
            else -> Unit
        }
    }

    @Synchronized
    fun backToLobby() {
        phaseJob?.cancel()
        watchJob?.cancel()
        board.clear()
        _aiImage.value = null
        val s = _state.value
        _state.value = s.copy(
            phase = DrawPhase.LOBBY, drawerId = null, isAi = false, word = null, choices = emptyList(),
            feed = emptyList(), round = 0, note = null,
            players = s.players.map { it.copy(guessed = false, lastGain = 0) },
        )
    }

    fun close() {
        phaseJob?.cancel()
        watchJob?.cancel()
        aiPending?.cancel()
        scope.cancel()
    }

    // ------------------------------------------------------------------
    // Akış
    // ------------------------------------------------------------------

    private fun schedule(ms: Long, next: () -> Unit) {
        phaseJob?.cancel()
        phaseJob = scope.launch {
            delay(ms)
            synchronized(this@DrawGame) { next() }
        }
    }

    @Synchronized
    private fun nextTurn(from: Int) {
        var i = from
        // Oyundan çıkmış oyuncuların sırasını atla
        while (i < schedule.size && (
                (schedule[i] != null && _state.value.players.none { it.id == schedule[i] }) || (schedule[i] == null && aiBroken)
            )
        ) i++
        if (i >= schedule.size) {
            finish()
            return
        }
        board.clear()
        lastSeq = -1
        _aiImage.value = null
        val now = System.currentTimeMillis()
        val base = _state.value.let { s ->
            s.copy(
                round = i, word = null, choices = emptyList(), hintSlots = emptyList(), feed = emptyList(), note = null,
                players = s.players.map { it.copy(guessed = false, lastGain = 0) },
            )
        }
        val pid = schedule[i]
        if (pid != null) {
            _state.value = base.copy(
                phase = DrawPhase.CHOOSE, drawerId = pid, isAi = false, choices = DrawWords.choices(),
                phaseMs = CHOOSE_MS, phaseEndsAt = now + CHOOSE_MS,
            )
            schedule(CHOOSE_MS) {
                val s = _state.value
                if (s.phase == DrawPhase.CHOOSE && s.round == i) s.choices.randomOrNull()?.let { beginDraw(it, ai = false) }
            }
        } else {
            _state.value = base.copy(phase = DrawPhase.AI_LOADING, drawerId = null, isAi = true, phaseMs = AI_WAIT_MS, phaseEndsAt = now + AI_WAIT_MS)
            if (aiPending == null) prefetchAi()
            phaseJob?.cancel()
            phaseJob = scope.launch {
                val res = withTimeoutOrNull(AI_WAIT_MS) { aiPending?.await() }
                synchronized(this@DrawGame) {
                    aiPending = null
                    val s = _state.value
                    if (s.phase != DrawPhase.AI_LOADING || s.round != i) return@launch
                    if (res == null) {
                        aiBroken = true
                        _state.value = s.copy(note = tx("Yapay zekâ şu an çizemiyor, yapay zekâ turları atlanıyor", "The AI can't draw right now, skipping AI rounds"))
                        schedule(2_500) { if (_state.value.round == i) nextTurn(i + 1) }
                    } else {
                        _aiImage.value = res.second
                        beginDraw(res.first, ai = true)
                        // Sonraki yapay zekâ turunun resmini şimdiden hazırla
                        if (schedule.drop(i + 1).contains(null)) prefetchAi()
                    }
                }
            }
        }
    }

    private fun beginDraw(w: DrawWord, ai: Boolean) {
        val label = w.label
        val letters = label.indices.filter { label[it] != ' ' }
        val hints = letters.shuffled().take(
            when {
                letters.size >= 5 -> 2
                letters.size >= 3 -> 1
                else -> 0
            }
        )
        dev.litedroid.launcher.wrapped.WrappedStats.count(app, if (ai) "draw.ai" else "draw.rounds")
        val ms = if (ai) AI_DRAW_MS else _state.value.drawSeconds * 1000L
        val now = System.currentTimeMillis()
        val r = _state.value.round
        _state.value = _state.value.copy(
            phase = DrawPhase.DRAW, word = w, choices = emptyList(), hintSlots = hints,
            phaseMs = ms, phaseEndsAt = now + ms,
        )
        schedule(ms) { if (_state.value.round == r) reveal() }
    }

    private fun reveal() {
        val s = _state.value
        if (s.phase != DrawPhase.DRAW && s.phase != DrawPhase.CHOOSE) return
        val r = s.round
        _state.value = s.copy(phase = DrawPhase.REVEAL, phaseMs = REVEAL_MS, phaseEndsAt = System.currentTimeMillis() + REVEAL_MS)
        schedule(REVEAL_MS) { if (_state.value.round == r) nextTurn(r + 1) }
    }

    private fun finish() {
        phaseJob?.cancel()
        watchJob?.cancel()
        dev.litedroid.launcher.wrapped.WrappedStats.count(app, "draw.games")
        _state.value.ranked.firstOrNull()?.let { dev.litedroid.launcher.wrapped.WrappedStats.count(app, "draw.win." + it.avatar + " " + it.name) }
        _state.value = _state.value.copy(phase = DrawPhase.FINISHED, drawerId = null, phaseMs = 0, phaseEndsAt = 0)
    }

    /** Çizenin telefonu 20 sn sessiz kalırsa (ekran kapandı, ağdan düştü) tur bitirilir. */
    private fun startWatchdog() {
        watchJob?.cancel()
        watchJob = scope.launch {
            while (isActive) {
                delay(2_000)
                synchronized(this@DrawGame) {
                    val s = _state.value
                    val d = s.drawer
                    if (!s.isAi && (s.phase == DrawPhase.CHOOSE || s.phase == DrawPhase.DRAW)) {
                        if (d == null || System.currentTimeMillis() - d.lastSeen > 20_000) {
                            val who = d?.name ?: tx("Çizen", "The drawer")
                            if (s.phase == DrawPhase.CHOOSE) nextTurn(s.round + 1)
                            else {
                                reveal()
                                _state.value = _state.value.copy(note = tx("$who bağlantısını kaybetti", "$who lost the connection"))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun prefetchAi() {
        if (geminiKey.isBlank()) return
        aiPending?.cancel()
        aiPending = scope.async(Dispatchers.IO) {
            val w = DrawWords.forAi()
            runCatching { w to GeminiMedia.image(app, geminiKey, aiPrompt(w)) }
                .onFailure { Log.w(TAG, "AI drawing failed: ${it.message}") }
                .getOrNull()
        }
    }

    private fun aiPrompt(w: DrawWord) =
        "A fun hand-drawn doodle of ${w.en}, like a quick drawing in a party guessing game: bold black marker outlines " +
            "with a few bright crayon colors, on plain white paper, centered, simple and clearly recognizable. " +
            "Absolutely no text, letters, words, numbers or labels anywhere in the image."

    companion object {
        private const val TAG = "LiteDroidDraw"
        const val MAX_PLAYERS = 8
        const val CHOOSE_MS = 12_000L
        const val AI_WAIT_MS = 45_000L
        const val AI_DRAW_MS = 60_000L
        const val REVEAL_MS = 6_500L
        private const val FEED_SIZE = 9

        private val TR = Locale.forLanguageTag("tr")

        /** Küçük harf, Türkçe karakterler sadeleşir, boşluk ve noktalama atılır: "Köpek!" → "kopek". */
        fun norm(s: String): String {
            val sb = StringBuilder()
            for (ch in s.lowercase(TR)) {
                val c = when (ch) {
                    'ç' -> 'c'; 'ğ' -> 'g'; 'ı' -> 'i'; 'ö' -> 'o'; 'ş' -> 's'; 'ü' -> 'u'; 'â' -> 'a'; 'î' -> 'i'; 'û' -> 'u'
                    else -> ch
                }
                if (c.isLetterOrDigit()) sb.append(c)
            }
            return sb.toString()
        }

        fun judge(guess: String, w: DrawWord): GuessResult {
            val whole = norm(guess)
            val tokens = guess.split(' ', ',', '.').map { norm(it) }.filter { it.isNotEmpty() }
            for (target in listOf(norm(w.tr), norm(w.en)).filter { it.isNotEmpty() }) {
                if (whole == target || target in tokens) return GuessResult.CORRECT
            }
            val target = norm(w.label)
            val d = levenshtein(whole, target)
            if ((target.length >= 4 && d == 1) || (target.length >= 7 && d == 2)) return GuessResult.CLOSE
            return GuessResult.WRONG
        }

        private fun levenshtein(a: String, b: String): Int {
            if (kotlin.math.abs(a.length - b.length) > 2) return 99
            var prev = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val cur = IntArray(b.length + 1)
                cur[0] = i
                for (j in 1..b.length) {
                    cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                }
                prev = cur
            }
            return prev[b.length]
        }
    }
}

/** Ekranda gösterilen hali (uygulama diline göre). */
val DrawWord.label: String get() = tx(tr, en)
