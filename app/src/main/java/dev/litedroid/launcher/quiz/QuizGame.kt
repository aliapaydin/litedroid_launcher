package dev.litedroid.launcher.quiz

import android.content.Context
import dev.litedroid.launcher.tx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Soru kategorileri (questions.json'daki "c" alanı). */
enum class QuizCategory(val code: String, private val tr: String, private val en: String, val emoji: String) {
    MIXED("", "Karışık", "Mixed", "🎲"),
    GENERAL("genel", "Genel kültür", "General knowledge", "🧠"),
    HISTORY("tarih", "Tarih", "History", "🏛️"),
    GEOGRAPHY("cografya", "Coğrafya", "Geography", "🌍"),
    SCIENCE("bilim", "Bilim ve doğa", "Science & nature", "🔬"),
    TECH("teknoloji", "Teknoloji", "Technology", "💻"),
    CINEMA("sinema", "Sinema ve dizi", "Film & TV", "🎬"),
    MUSIC("muzik", "Müzik", "Music", "🎵"),
    BOOKS("edebiyat", "Kitaplar", "Books", "📚"),
    ART("sanat", "Sanat", "Art", "🎨"),
    SPORTS("spor", "Spor", "Sports", "⚽"),
    CARS("araba", "Arabalar", "Cars", "🚗"),
    GAMES("oyun", "Oyun dünyası", "Gaming", "🎮");

    val label: String get() = tx(tr, en)

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code } ?: MIXED
    }
}

/** Zorluk: 0 = karışık. */
enum class QuizLevel(val value: Int, private val tr: String, private val en: String) {
    EASY(1, "Başlangıç", "Beginner"),
    MEDIUM(2, "Orta", "Medium"),
    HARD(3, "Zor", "Hard"),
    MIXED(0, "Karışık", "Mixed");

    val label: String get() = tx(tr, en)

    companion object {
        fun of(value: Int) = entries.firstOrNull { it.value == value } ?: MIXED
    }
}

data class QuizQuestion(
    val category: QuizCategory,
    val level: Int,
    val text: String,
    /** Karıştırılmış seçenekler. */
    val options: List<String>,
    val correct: Int,
)

data class QuizPlayer(
    val id: String,
    val name: String,
    val avatar: String,
    val color: Int,
    val score: Int = 0,
    val streak: Int = 0,
    /** Bu sorudaki cevabı (seçenek sırası) ya da null. */
    val choice: Int? = null,
    val answeredMs: Long? = null,
    val lastCorrect: Boolean? = null,
    val lastGain: Int = 0,
    /** Bir önceki puan tablosundaki sırası (animasyon için). */
    val prevRank: Int = 0,
    val lastSeen: Long = System.currentTimeMillis(),
)

enum class QuizPhase { LOBBY, GET_READY, QUESTION, REVEAL, SCOREBOARD, FINISHED }

data class QuizState(
    val phase: QuizPhase = QuizPhase.LOBBY,
    val players: List<QuizPlayer> = emptyList(),
    val category: QuizCategory = QuizCategory.MIXED,
    val level: QuizLevel = QuizLevel.MEDIUM,
    val count: Int = 10,
    val question: QuizQuestion? = null,
    /** 0'dan başlayan soru sırası. */
    val index: Int = 0,
    /** Bu aşamanın bittiği an (System.currentTimeMillis) — geri sayım için. */
    val phaseEndsAt: Long = 0L,
    val phaseMs: Long = 0L,
) {
    val ranked: List<QuizPlayer> get() = players.sortedWith(compareByDescending<QuizPlayer> { it.score }.thenBy { it.name })
}

/**
 * Bilgi yarışması oyunu: oyuncular telefonlarından katılır ([join]), soruları cevaplar ([answer]).
 * TV ekranı [state]'i çizer, kumandayla [start] / [skip] / [backToLobby] çağırır.
 * Aşamalar: LOBBY → (GET_READY → QUESTION → REVEAL → SCOREBOARD) × soru sayısı → FINISHED.
 */
class QuizGame(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var phaseJob: Job? = null
    private val _state = MutableStateFlow(QuizState())
    val state: StateFlow<QuizState> = _state
    private var queue: List<QuizQuestion> = emptyList()

    private val bank: List<JSONObject> by lazy {
        runCatching {
            val text = app.assets.open("quiz/questions.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        }.getOrDefault(emptyList())
    }

    // ------------------------------------------------------------------
    // Oyuncular (sunucu iş parçacıklarından çağrılır)
    // ------------------------------------------------------------------

    /** Katılır ya da (aynı kimlikle) adını/avatarını günceller. Doluysa null. */
    @Synchronized
    fun join(name: String, avatar: String, pid: String?): QuizPlayer? {
        val clean = name.trim().take(16).ifBlank { tx("Oyuncu", "Player") }
        val s = _state.value
        s.players.firstOrNull { it.id == pid }?.let { existing ->
            val updated = existing.copy(name = clean, avatar = avatar.take(8), lastSeen = System.currentTimeMillis())
            _state.value = s.copy(players = s.players.map { if (it.id == existing.id) updated else it })
            return updated
        }
        if (s.players.size >= MAX_PLAYERS) return null
        val used = s.players.map { it.color }.toSet()
        val color = PLAYER_COLORS.firstOrNull { it !in used } ?: PLAYER_COLORS[s.players.size % PLAYER_COLORS.size]
        // Oyun ortasında katılan: sıfır puanla girer
        val p = QuizPlayer(UUID.randomUUID().toString().take(12), uniqueName(clean, s.players), avatar.take(8), color, prevRank = s.players.size + 1)
        _state.value = s.copy(players = s.players + p)
        return p
    }

    private fun uniqueName(name: String, players: List<QuizPlayer>): String {
        if (players.none { it.name.equals(name, true) }) return name
        var i = 2
        while (players.any { it.name.equals("$name $i", true) }) i++
        return "$name $i"
    }

    @Synchronized
    fun touch(pid: String) {
        val s = _state.value
        if (s.players.none { it.id == pid }) return
        _state.value = s.copy(players = s.players.map { if (it.id == pid) it.copy(lastSeen = System.currentTimeMillis()) else it })
    }

    fun player(pid: String?): QuizPlayer? = _state.value.players.firstOrNull { it.id == pid }

    @Synchronized
    fun remove(pid: String) {
        val s = _state.value
        _state.value = s.copy(players = s.players.filterNot { it.id == pid })
    }

    /** Cevap: yalnızca soru aşamasında, soru sırası tutuyorsa ve ilk cevapsa kabul edilir. */
    @Synchronized
    fun answer(pid: String, index: Int, choice: Int): Boolean {
        val s = _state.value
        if (s.phase != QuizPhase.QUESTION || index != s.index || choice !in 0..3) return false
        val p = s.players.firstOrNull { it.id == pid } ?: return false
        if (p.choice != null) return false
        val elapsed = (s.phaseMs - (s.phaseEndsAt - System.currentTimeMillis())).coerceAtLeast(0L)
        _state.value = s.copy(players = s.players.map { if (it.id == pid) it.copy(choice = choice, answeredMs = elapsed, lastSeen = System.currentTimeMillis()) else it })
        // Herkes cevapladıysa beklemeden aç
        if (_state.value.players.all { it.choice != null }) scope.launch { reveal() }
        return true
    }

    // ------------------------------------------------------------------
    // TV (kumanda) tarafı
    // ------------------------------------------------------------------

    @Synchronized
    fun setCategory(c: QuizCategory) { if (_state.value.phase == QuizPhase.LOBBY) _state.value = _state.value.copy(category = c) }
    @Synchronized
    fun setLevel(l: QuizLevel) { if (_state.value.phase == QuizPhase.LOBBY) _state.value = _state.value.copy(level = l) }
    @Synchronized
    fun setCount(n: Int) { if (_state.value.phase == QuizPhase.LOBBY) _state.value = _state.value.copy(count = n) }

    @Synchronized
    fun start() {
        val s = _state.value
        if (s.players.isEmpty()) return
        queue = pickQuestions(s.category, s.level, s.count)
        if (queue.isEmpty()) return
        _state.value = s.copy(
            players = s.players.mapIndexed { i, p -> p.copy(score = 0, streak = 0, choice = null, answeredMs = null, lastCorrect = null, lastGain = 0, prevRank = i + 1) },
            count = queue.size,
            index = 0,
        )
        getReady(0)
    }

    /** Kumandada Tamam: bekleme aşamalarını hızlandırır. */
    @Synchronized
    fun skip() {
        when (_state.value.phase) {
            QuizPhase.GET_READY -> question(_state.value.index)
            QuizPhase.QUESTION -> reveal()
            QuizPhase.REVEAL -> scoreboard()
            QuizPhase.SCOREBOARD -> nextOrFinish()
            else -> Unit
        }
    }

    @Synchronized
    fun backToLobby() {
        phaseJob?.cancel()
        val s = _state.value
        _state.value = s.copy(
            phase = QuizPhase.LOBBY,
            question = null,
            index = 0,
            players = s.players.map { it.copy(choice = null, answeredMs = null, lastCorrect = null, lastGain = 0) },
        )
    }

    fun close() {
        phaseJob?.cancel()
        scope.cancel()
    }

    private fun schedule(ms: Long, next: () -> Unit) {
        phaseJob?.cancel()
        phaseJob = scope.launch {
            delay(ms)
            next()
        }
    }

    @Synchronized
    private fun getReady(i: Int) {
        val now = System.currentTimeMillis()
        val s = _state.value
        _state.value = s.copy(
            phase = QuizPhase.GET_READY,
            index = i,
            question = queue[i],
            phaseMs = READY_MS,
            phaseEndsAt = now + READY_MS,
            players = s.players.map { it.copy(choice = null, answeredMs = null, lastCorrect = null, lastGain = 0) },
        )
        schedule(READY_MS) { question(i) }
    }

    @Synchronized
    private fun question(i: Int) {
        val q = queue.getOrNull(i) ?: return
        val limit = when (q.level) { 3 -> 25_000L; 2 -> 22_000L; else -> 20_000L }
        val now = System.currentTimeMillis()
        _state.value = _state.value.copy(phase = QuizPhase.QUESTION, index = i, question = q, phaseMs = limit, phaseEndsAt = now + limit)
        schedule(limit) { reveal() }
    }

    @Synchronized
    private fun reveal() {
        val s = _state.value
        if (s.phase != QuizPhase.QUESTION) return
        val q = s.question ?: return
        val ranksBefore = s.ranked.mapIndexed { i, p -> p.id to i + 1 }.toMap()
        val players = s.players.map { p ->
            val correct = p.choice == q.correct
            val gain = if (correct) {
                val speed = 1.0 - ((p.answeredMs ?: s.phaseMs).toDouble() / s.phaseMs).coerceIn(0.0, 1.0)
                val base = 500 + (500 * speed).toInt()
                val streakBonus = (p.streak * 100).coerceAtMost(300)
                ((base + streakBonus) / 10) * 10
            } else 0
            p.copy(
                score = p.score + gain,
                streak = if (correct) p.streak + 1 else 0,
                lastCorrect = if (p.choice == null) null else correct,
                lastGain = gain,
                prevRank = ranksBefore[p.id] ?: p.prevRank,
            )
        }
        _state.value = s.copy(phase = QuizPhase.REVEAL, players = players, phaseMs = REVEAL_MS, phaseEndsAt = System.currentTimeMillis() + REVEAL_MS)
        schedule(REVEAL_MS) { scoreboard() }
    }

    @Synchronized
    private fun scoreboard() {
        if (_state.value.phase != QuizPhase.REVEAL) return
        _state.value = _state.value.copy(phase = QuizPhase.SCOREBOARD, phaseMs = BOARD_MS, phaseEndsAt = System.currentTimeMillis() + BOARD_MS)
        schedule(BOARD_MS) { nextOrFinish() }
    }

    @Synchronized
    private fun nextOrFinish() {
        val s = _state.value
        if (s.phase != QuizPhase.SCOREBOARD) return
        if (s.index + 1 < queue.size) getReady(s.index + 1)
        else {
            phaseJob?.cancel()
            _state.value = s.copy(phase = QuizPhase.FINISHED, phaseMs = 0, phaseEndsAt = 0)
        }
    }

    /** Havuzdan rastgele sorular; aynı soruyu art arda oyunlarda tekrar etmemeye çalışır. */
    private fun pickQuestions(category: QuizCategory, level: QuizLevel, count: Int): List<QuizQuestion> {
        val pool = bank.filter { o ->
            (category == QuizCategory.MIXED || o.optString("c") == category.code) &&
                (level == QuizLevel.MIXED || o.optInt("l") == level.value)
        }
        val fresh = pool.filter { it.optString("q") !in recentlyAsked }
        val chosen = (fresh.shuffled() + pool.filter { it.optString("q") in recentlyAsked }.shuffled()).take(count)
        recentlyAsked.addAll(chosen.map { it.optString("q") })
        while (recentlyAsked.size > 150) recentlyAsked.removeAt(0)
        // Karışık zorlukta kolaydan zora sırala
        val ordered = if (level == QuizLevel.MIXED) chosen.sortedBy { it.optInt("l") } else chosen
        return ordered.map { o ->
            val a = o.getJSONArray("a")
            val answers = (0 until a.length()).map { a.getString(it) }
            val shuffled = answers.indices.shuffled()
            QuizQuestion(
                category = QuizCategory.of(o.optString("c")),
                level = o.optInt("l"),
                text = o.optString("q"),
                options = shuffled.map { answers[it] },
                correct = shuffled.indexOf(0),
            )
        }
    }

    fun available(category: QuizCategory, level: QuizLevel): Int = bank.count { o ->
        (category == QuizCategory.MIXED || o.optString("c") == category.code) &&
            (level == QuizLevel.MIXED || o.optInt("l") == level.value)
    }

    companion object {
        const val MAX_PLAYERS = 8
        const val READY_MS = 3_500L
        const val REVEAL_MS = 6_000L
        const val BOARD_MS = 6_000L
        val AVATARS = listOf("🦊", "🐼", "🐯", "🦁", "🐸", "🐵", "🐙", "🦄", "🐲", "🐧", "🐨", "🐰", "🐶", "🐱", "🦉", "🐢", "🐝", "🦖", "👽", "🤖")
        /** Oyuncu renkleri (ARGB). */
        val PLAYER_COLORS = listOf(0xFFFF5C8A.toInt(), 0xFF4D9BFF.toInt(), 0xFFFFC23D.toInt(), 0xFF3BE07F.toInt(), 0xFFB06BFF.toInt(), 0xFFFF8A3D.toInt(), 0xFF2ED3C8.toInt(), 0xFFFF6B6B.toInt())
        private val recentlyAsked = ArrayList<String>()
    }
}
