package dev.litedroid.launcher.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.LocalSounds
import dev.litedroid.launcher.ui.Sfx
import dev.litedroid.launcher.ui.isSelectKey
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Zorluk: tahta boyutu ve mayın sayısı. */
private enum class MineLevel(val cols: Int, val rows: Int, val mines: Int, val cell: Dp, private val tr: String, private val en: String) {
    EASY(9, 9, 10, 40.dp, "Kolay", "Easy"),
    MEDIUM(12, 11, 22, 34.dp, "Orta", "Medium"),
    HARD(16, 12, 38, 30.dp, "Zor", "Hard");

    val label: String get() = tx(tr, en)
}

private enum class MineState { READY, PLAYING, WON, LOST }

/** Rakam renkleri (klasik). */
private val NUMBER_COLORS = listOf(
    Color.Transparent,
    Color(0xFF6FA8FF), Color(0xFF5BD68A), Color(0xFFFF6B6B), Color(0xFFB98CFF),
    Color(0xFFFFB347), Color(0xFF4FD1C5), Color(0xFFEDEDED), Color(0xFFAAAAAA),
)

/**
 * Mayın tarlası, kumandaya göre: yön tuşları gezer, Tamam açar, Tamam'a basılı tutmak bayrak koyar.
 * Açılmış bir rakama Tamam: etrafındaki bayrak sayısı tutuyorsa kalan kareleri birden açar.
 * İlk açılan kare (ve çevresi) her zaman güvenlidir. En üstteki satırdan yukarı çıkınca zorluk seçilir.
 */
@Composable
fun MinesweeperGame(game: BuiltInGame) {
    val context = LocalContext.current
    val sounds = LocalSounds.current
    var level by remember { mutableStateOf(MineLevel.EASY) }
    var mines by remember { mutableStateOf(BooleanArray(level.cols * level.rows)) }
    var revealed by remember { mutableStateOf(BooleanArray(level.cols * level.rows)) }
    var flags by remember { mutableStateOf(BooleanArray(level.cols * level.rows)) }
    var state by remember { mutableStateOf(MineState.READY) }
    var cx by remember { mutableIntStateOf(level.cols / 2) }
    /** -1: zorluk satırı. */
    var cy by remember { mutableIntStateOf(level.rows / 2) }
    var exploded by remember { mutableIntStateOf(-1) }
    var seconds by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(GameScores.best(context, "mines_${level.name}")) }
    var newRecord by remember { mutableStateOf(false) }

    val cols = level.cols
    val rows = level.rows
    fun idx(x: Int, y: Int) = y * cols + x
    fun neighbors(i: Int): List<Int> {
        val x = i % cols
        val y = i / cols
        val out = ArrayList<Int>(8)
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until cols && ny in 0 until rows) out += idx(nx, ny)
        }
        return out
    }
    fun count(i: Int) = neighbors(i).count { mines[it] }

    fun reset(l: MineLevel = level) {
        level = l
        val n = l.cols * l.rows
        mines = BooleanArray(n)
        revealed = BooleanArray(n)
        flags = BooleanArray(n)
        state = MineState.READY
        exploded = -1
        seconds = 0
        newRecord = false
        best = GameScores.best(context, "mines_${l.name}")
        cx = cx.coerceIn(0, l.cols - 1)
        if (cy >= 0) cy = cy.coerceIn(0, l.rows - 1)
    }

    /** Mayınlar ilk açılışta, açılan karenin çevresine konmadan yerleştirilir. */
    fun plant(first: Int) {
        val safe = (neighbors(first) + first).toSet()
        val m = BooleanArray(cols * rows)
        val candidates = (0 until cols * rows).filter { it !in safe }.shuffled(Random(System.nanoTime()))
        candidates.take(level.mines).forEach { m[it] = true }
        mines = m
        state = MineState.PLAYING
    }

    fun checkWin(o: BooleanArray) {
        val safeCells = cols * rows - level.mines
        if (o.count { it } == safeCells) {
            state = MineState.WON
            // Kazanınca kalan mayınlara bayrak
            flags = BooleanArray(cols * rows) { mines[it] }
            sounds?.play(Sfx.SELECT)
            if (GameScores.submit(context, "mines_${level.name}", seconds, lowerIsBetter = true)) {
                newRecord = true
                best = seconds
            }
        }
    }

    /** Boş karelerden başlayarak açar (taşma dolgusu). */
    fun reveal(start: Int) {
        val o = revealed.copyOf()
        val stack = ArrayDeque<Int>()
        stack.add(start)
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            if (o[i] || flags[i]) continue
            o[i] = true
            if (!mines[i] && count(i) == 0) neighbors(i).forEach { if (!o[it]) stack.add(it) }
        }
        revealed = o
        checkWin(o)
    }

    fun lose(at: Int) {
        exploded = at
        state = MineState.LOST
        revealed = BooleanArray(cols * rows) { revealed[it] || mines[it] }
        sounds?.play(Sfx.BACK)
    }

    fun press() {
        if (state == MineState.WON || state == MineState.LOST) {
            reset()
            return
        }
        if (cy < 0) return
        val i = idx(cx, cy)
        if (flags[i]) return
        if (state == MineState.READY) plant(i)
        if (revealed[i]) {
            // Akor: bayraklar tutuyorsa komşuları aç
            val around = neighbors(i)
            if (count(i) > 0 && around.count { flags[it] } == count(i)) {
                val hit = around.firstOrNull { !flags[it] && !revealed[it] && mines[it] }
                if (hit != null) lose(hit) else around.filter { !flags[it] && !revealed[it] }.forEach { reveal(it) }
            }
            return
        }
        if (mines[i]) lose(i) else {
            reveal(i)
            sounds?.play(Sfx.NAV)
        }
    }

    fun toggleFlag() {
        if (cy < 0 || state == MineState.WON || state == MineState.LOST) return
        val i = idx(cx, cy)
        if (revealed[i]) return
        flags = flags.copyOf().also { it[i] = !it[i] }
        sounds?.play(Sfx.SELECT)
    }

    // Süre
    LaunchedEffect(state) {
        while (state == MineState.PLAYING) {
            delay(1_000)
            if (state == MineState.PLAYING) seconds++
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    var heldFlagged by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }

    Row(
        Modifier
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
                val down = e.type == KeyEventType.KeyDown
                when {
                    e.key.isSelectKey -> {
                        if (down) {
                            if (e.nativeKeyEvent.repeatCount == 0) {
                                armed = true
                                heldFlagged = false
                            } else if (!heldFlagged && armed && e.nativeKeyEvent.repeatCount >= 2) {
                                // Basılı tutma: bayrak
                                heldFlagged = true
                                if (cy < 0) Unit else toggleFlag()
                            }
                        } else if (armed) {
                            armed = false
                            if (!heldFlagged) {
                                if (cy < 0) reset() else press()
                            }
                        }
                        true
                    }
                    !down -> e.key == Key.DirectionUp || e.key == Key.DirectionDown || e.key == Key.DirectionLeft || e.key == Key.DirectionRight
                    e.key == Key.DirectionUp -> { if (cy >= 0) cy--; true }
                    e.key == Key.DirectionDown -> { if (cy < rows - 1) cy++; true }
                    e.key == Key.DirectionLeft -> {
                        if (cy < 0) {
                            val l = MineLevel.entries[(level.ordinal - 1).mod(MineLevel.entries.size)]
                            reset(l)
                        } else if (cx > 0) cx--
                        true
                    }
                    e.key == Key.DirectionRight -> {
                        if (cy < 0) {
                            val l = MineLevel.entries[(level.ordinal + 1).mod(MineLevel.entries.size)]
                            reset(l)
                        } else if (cx < cols - 1) cx++
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Sol: bilgiler ve zorluk
        Column(Modifier.width(300.dp)) {
            GameStats(
                tx("Mayın", "Mines") to "${level.mines - flags.count { it }}",
                tx("Süre", "Time") to "${seconds}s",
                tx("En iyi", "Best") to if (best > 0) "${best}s" else "–",
                accent = game.top,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                tx("ZORLUK · en üstten yukarı çık, sağ/sol ile seç", "LEVEL · go up from the top row, left/right to pick"),
                color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MineLevel.entries.forEach { l ->
                    val selected = l == level
                    val cursor = cy < 0 && selected
                    Text(
                        "${l.label} · ${l.cols}×${l.rows}",
                        color = if (cursor) Color(0xFF111114) else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                when {
                                    cursor -> Color.White
                                    selected -> game.top.copy(alpha = 0.55f)
                                    else -> Color.White.copy(alpha = 0.08f)
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                tx("Tamam: aç\nTamam'a basılı tut: bayrak\nRakama Tamam: çevresini aç", "OK: open\nHold OK: flag\nOK on a number: open around it"),
                color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 18.sp,
            )
        }
        Spacer(Modifier.width(24.dp))
        // Sağ: tahta
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0B0C16))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    for (y in 0 until rows) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            for (x in 0 until cols) {
                                val i = idx(x, y)
                                MineCell(
                                    size = level.cell,
                                    opened = revealed[i],
                                    mine = mines[i],
                                    flagged = flags[i],
                                    number = if (revealed[i] && !mines[i]) count(i) else 0,
                                    exploded = i == exploded,
                                    wrongFlag = state == MineState.LOST && flags[i] && !mines[i],
                                    cursor = cy == y && cx == x,
                                    accent = game.top,
                                )
                            }
                        }
                    }
                }
                when (state) {
                    MineState.WON -> GameMessage(
                        if (newRecord) tx("Yeni rekor! ${seconds}s", "New record! ${seconds}s") else tx("Temizlendi! ${seconds}s", "Cleared! ${seconds}s"),
                        tx("Tamam: yeni oyun", "OK: new game"), game.top,
                    )
                    MineState.LOST -> GameMessage(tx("Bum! 💥", "Boom! 💥"), tx("Tamam: yeniden dene", "OK: try again"), game.top)
                    MineState.READY -> if (seconds == 0 && revealed.none { it }) GameMessage(
                        tx("Bir kare seç", "Pick a square"),
                        tx("Tamam: aç · Basılı tut: bayrak", "OK: open · Hold: flag"), game.top,
                    )
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun MineCell(
    size: Dp,
    opened: Boolean,
    mine: Boolean,
    flagged: Boolean,
    number: Int,
    exploded: Boolean,
    wrongFlag: Boolean,
    cursor: Boolean,
    accent: Color,
) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(
                when {
                    exploded -> Brush.linearGradient(listOf(Color(0xFFFF5C6E), Color(0xFFB3263A)))
                    opened -> Brush.linearGradient(listOf(Color(0xFF1B1D2E), Color(0xFF15172A)))
                    else -> Brush.linearGradient(listOf(Color(0xFF3B3F63), Color(0xFF2A2D4A)))
                }
            )
            .border(
                if (cursor) 2.5.dp else 1.dp,
                when {
                    cursor -> Color.White
                    opened -> Color.White.copy(alpha = 0.04f)
                    else -> accent.copy(alpha = 0.25f)
                },
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val fs = (size.value * 0.48f).sp
        when {
            wrongFlag -> Text("✕", color = Color(0xFFFF8A8A), fontSize = fs, fontWeight = FontWeight.ExtraBold)
            flagged && !(opened && mine) -> Text("🚩", fontSize = (size.value * 0.42f).sp)
            opened && mine -> Text("💣", fontSize = (size.value * 0.44f).sp)
            opened && number > 0 -> Text("$number", color = NUMBER_COLORS[number], fontSize = fs, fontWeight = FontWeight.ExtraBold)
        }
    }
}
