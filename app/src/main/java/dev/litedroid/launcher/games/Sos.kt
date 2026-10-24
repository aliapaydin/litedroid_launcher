package dev.litedroid.launcher.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.LocalSounds
import dev.litedroid.launcher.ui.Sfx
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val N = 6
private val YOU = Color(0xFF4D9BFF)
private val CPU = Color(0xFFFF5C8A)

/** Bir SOS dizisi: üç hücrenin indeksleri ve kimin yaptığı (0 sen, 1 bilgisayar). */
private data class SosLine(val a: Int, val c: Int, val owner: Int)

private val DIRS = listOf(1 to 0, 0 to 1, 1 to 1, 1 to -1)

/** [i] hücresine [ch] konursa oluşan SOS dizileri. */
private fun linesAt(board: CharArray, i: Int, ch: Char): List<Pair<Int, Int>> {
    val x = i % N
    val y = i / N
    fun at(cx: Int, cy: Int): Char? = if (cx in 0 until N && cy in 0 until N) {
        val k = cy * N + cx
        if (k == i) ch else board[k]
    } else null
    val out = ArrayList<Pair<Int, Int>>()
    for ((dx, dy) in DIRS) {
        if (ch == 'O') {
            if (at(x - dx, y - dy) == 'S' && at(x + dx, y + dy) == 'S') out += ((y - dy) * N + (x - dx)) to ((y + dy) * N + (x + dx))
        } else {
            if (at(x + dx, y + dy) == 'O' && at(x + 2 * dx, y + 2 * dy) == 'S') out += i to ((y + 2 * dy) * N + (x + 2 * dx))
            if (at(x - dx, y - dy) == 'O' && at(x - 2 * dx, y - 2 * dy) == 'S') out += i to ((y - 2 * dy) * N + (x - 2 * dx))
        }
    }
    return out
}

@Composable
fun SosGame(game: BuiltInGame) {
    val sounds = LocalSounds.current
    var board by remember { mutableStateOf(CharArray(N * N) { ' ' }) }
    var lines by remember { mutableStateOf(emptyList<SosLine>()) }
    var scores by remember { mutableStateOf(listOf(0, 0)) }
    var turn by remember { mutableIntStateOf(0) }
    var cursor by remember { mutableIntStateOf(14) }
    var picking by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf('S') }
    var round by remember { mutableIntStateOf(0) }
    val full = board.none { it == ' ' }

    /** Hamleyi uygular; SOS yapıldıysa sıra aynı oyuncuda kalır. */
    fun play(i: Int, ch: Char, who: Int) {
        val made = linesAt(board, i, ch)
        board = board.copyOf().also { it[i] = ch }
        if (made.isNotEmpty()) {
            lines = lines + made.map { SosLine(it.first, it.second, who) }
            scores = scores.toMutableList().also { it[who] += made.size }
            sounds?.play(Sfx.SELECT)
        } else {
            turn = 1 - who
            sounds?.play(Sfx.NAV)
        }
    }

    // Bilgisayar: önce en çok SOS yapan hamle, yoksa rakibe en az fırsat bırakan hamle
    LaunchedEffect(turn, board, round) {
        if (turn != 1 || board.none { it == ' ' }) return@LaunchedEffect
        delay(650)
        val empties = board.indices.filter { board[it] == ' ' }
        var bestMoves = ArrayList<Pair<Int, Char>>()
        var bestScore = Int.MIN_VALUE
        for (i in empties) for (ch in listOf('S', 'O')) {
            val gain = linesAt(board, i, ch).size
            val score = if (gain > 0) gain * 100 else {
                val next = board.copyOf().also { it[i] = ch }
                val threat = empties.filter { it != i }.maxOfOrNull { j ->
                    maxOf(linesAt(next, j, 'S').size, linesAt(next, j, 'O').size)
                } ?: 0
                -threat * 10
            }
            if (score > bestScore) {
                bestScore = score
                bestMoves = arrayListOf(i to ch)
            } else if (score == bestScore) {
                bestMoves += i to ch
            }
        }
        val (i, ch) = bestMoves[Random.nextInt(bestMoves.size)]
        play(i, ch, 1)
    }

    fun restart() {
        board = CharArray(N * N) { ' ' }
        lines = emptyList()
        scores = listOf(0, 0)
        round++
        turn = round % 2 // sırayla başlasınlar
        picking = false
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GameStats(
            tx("Sen", "You") to "${scores[0]}",
            tx("Bilgisayar", "CPU") to "${scores[1]}",
            tx("Sıra", "Turn") to if (turn == 0) tx("Sende", "You") else tx("Bilgisayarda", "CPU"),
            accent = game.top,
        )
        Spacer(Modifier.height(12.dp))
        val cell = 50.dp
        GameSurface(
            onDir = { d ->
                if (picking) {
                    if (d == Dir.LEFT || d == Dir.RIGHT) {
                        pick = if (pick == 'S') 'O' else 'S'
                    } else {
                        picking = false
                    }
                    sounds?.play(Sfx.NAV)
                    return@GameSurface
                }
                val x = (cursor % N + d.dx).coerceIn(0, N - 1)
                val y = (cursor / N + d.dy).coerceIn(0, N - 1)
                cursor = y * N + x
                sounds?.play(Sfx.NAV)
            },
            onSelect = {
                when {
                    full -> restart()
                    turn != 0 -> Unit
                    picking -> {
                        picking = false
                        play(cursor, pick, 0)
                    }
                    board[cursor] == ' ' -> {
                        picking = true
                        sounds?.play(Sfx.OPEN)
                    }
                    else -> sounds?.play(Sfx.BUMP)
                }
            },
        ) {
            Box {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (y in 0 until N) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (x in 0 until N) {
                            val i = y * N + x
                            val selected = i == cursor
                            val ch = board[i]
                            val owner = lines.lastOrNull { i == it.a || i == it.c || i == (it.a + it.c) / 2 }?.owner
                            Box(
                                Modifier
                                    .size(cell)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            selected && turn == 0 -> Color.White.copy(alpha = 0.22f)
                                            owner == 0 -> YOU.copy(alpha = 0.18f)
                                            owner == 1 -> CPU.copy(alpha = 0.18f)
                                            else -> Color.White.copy(alpha = 0.05f)
                                        }
                                    )
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) Color.White else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(10.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                val shown = if (selected && picking) pick else ch
                                Text(
                                    shown.toString(),
                                    color = if (selected && picking) Color.White.copy(alpha = 0.6f) else Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                    }
                }
                // Yapılan SOS'ların üstüne oyuncu renginde çizgi
                Canvas(Modifier.matchParentSize()) {
                    val step = (cell + 4.dp).toPx()
                    val half = cell.toPx() / 2
                    lines.forEach { l ->
                        val a = Offset((l.a % N) * step + half, (l.a / N) * step + half)
                        val c = Offset((l.c % N) * step + half, (l.c / N) * step + half)
                        drawLine((if (l.owner == 0) YOU else CPU).copy(alpha = 0.85f), a, c, 5.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
            if (picking) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF07080F).copy(alpha = 0.9f))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    listOf('S', 'O').forEach { c ->
                        Text(
                            c.toString(),
                            color = if (pick == c) Color(0xFF0E1222) else Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (pick == c) Color.White else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }
            if (full) {
                val (a, b) = scores
                GameMessage(
                    when {
                        a > b -> tx("Kazandın! $a - $b", "You win! $a - $b")
                        a < b -> tx("Bilgisayar kazandı $b - $a", "CPU wins $b - $a")
                        else -> tx("Berabere $a - $b", "Draw $a - $b")
                    },
                    tx("Yeni oyun için orta tuş", "Center for a new game"),
                    game.top,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.Text(
            if (picking) tx("Sol/Sağ: S ya da O seç · Orta tuş: koy · Yukarı/Aşağı: vazgeç", "Left/Right: choose S or O · Center: place · Up/Down: cancel")
            else tx("SOS yapan bir kez daha oynar", "Making an SOS gives you another turn"),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
        )
    }
}
