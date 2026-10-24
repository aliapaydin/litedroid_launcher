package dev.litedroid.launcher.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.LocalSounds
import dev.litedroid.launcher.ui.Sfx
import kotlinx.coroutines.delay
import kotlin.random.Random

private val WINS = listOf(
    intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
    intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
    intArrayOf(0, 4, 8), intArrayOf(2, 4, 6),
)

private fun winLine(b: CharArray): IntArray? =
    WINS.firstOrNull { w -> b[w[0]] != ' ' && b[w[0]] == b[w[1]] && b[w[1]] == b[w[2]] }

/** Minimax: O (bilgisayar) için en iyi hamle. */
private fun minimax(b: CharArray, cpuTurn: Boolean, depth: Int): Int {
    winLine(b)?.let { return if (b[it[0]] == 'O') 10 - depth else depth - 10 }
    if (b.none { it == ' ' }) return 0
    var best = if (cpuTurn) Int.MIN_VALUE else Int.MAX_VALUE
    for (i in 0 until 9) if (b[i] == ' ') {
        b[i] = if (cpuTurn) 'O' else 'X'
        val v = minimax(b, !cpuTurn, depth + 1)
        b[i] = ' '
        best = if (cpuTurn) maxOf(best, v) else minOf(best, v)
    }
    return best
}

@Composable
fun TicTacToeGame(game: BuiltInGame) {
    val sounds = LocalSounds.current
    var board by remember { mutableStateOf(CharArray(9) { ' ' }) }
    var cursor by remember { mutableIntStateOf(4) }
    var cpuTurn by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    var wins by remember { mutableIntStateOf(0) }
    var losses by remember { mutableIntStateOf(0) }
    var draws by remember { mutableIntStateOf(0) }
    val line = winLine(board)
    val over = line != null || board.none { it == ' ' }

    fun finish(b: CharArray) {
        val w = winLine(b)
        when {
            w != null && b[w[0]] == 'X' -> { wins++; sounds?.play(Sfx.SELECT) }
            w != null -> { losses++; sounds?.play(Sfx.BUMP) }
            b.none { it == ' ' } -> draws++
        }
    }

    LaunchedEffect(cpuTurn, round) {
        if (!cpuTurn || over) return@LaunchedEffect
        delay(450)
        val empties = (0 until 9).filter { board[it] == ' ' }
        // %25 ihtimalle rastgele oynar ki yenilebilsin
        val move = if (Random.nextFloat() < 0.25f) empties.random() else {
            val b = board.copyOf()
            empties.maxBy { i ->
                b[i] = 'O'
                val v = minimax(b, false, 0)
                b[i] = ' '
                v * 10 + Random.nextInt(3)
            }
        }
        val next = board.copyOf().also { it[move] = 'O' }
        board = next
        cpuTurn = false
        if (winLine(next) != null || next.none { it == ' ' }) finish(next)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GameStats(
            tx("Galibiyet", "Wins") to "$wins",
            tx("Yenilgi", "Losses") to "$losses",
            tx("Beraberlik", "Draws") to "$draws",
            accent = game.top,
        )
        Spacer(Modifier.height(16.dp))
        GameSurface(
            onDir = { d ->
                val x = (cursor % 3 + d.dx).coerceIn(0, 2)
                val y = (cursor / 3 + d.dy).coerceIn(0, 2)
                cursor = y * 3 + x
                sounds?.play(Sfx.NAV)
            },
            onSelect = {
                when {
                    over -> {
                        board = CharArray(9) { ' ' }
                        round++
                        cpuTurn = round % 2 == 1 // sırayla başlasınlar
                    }
                    cpuTurn -> Unit
                    board[cursor] != ' ' -> sounds?.play(Sfx.BUMP)
                    else -> {
                        val next = board.copyOf().also { it[cursor] = 'X' }
                        board = next
                        sounds?.play(Sfx.NAV)
                        if (winLine(next) != null || next.none { it == ' ' }) finish(next) else cpuTurn = true
                    }
                }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (y in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (x in 0 until 3) {
                        val i = y * 3 + x
                        val inLine = line?.contains(i) == true
                        val selected = i == cursor && !over
                        Box(
                            Modifier
                                .size(104.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    when {
                                        inLine -> game.top.copy(alpha = 0.35f)
                                        selected -> Color.White.copy(alpha = 0.18f)
                                        else -> Color.White.copy(alpha = 0.05f)
                                    }
                                )
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) Color.White else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(20.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            val c = board[i]
                            if (c != ' ') {
                                Text(
                                    c.toString(),
                                    color = if (c == 'X') Color(0xFF4D9BFF) else Color(0xFFFF5C8A),
                                    fontSize = 56.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                    }
                }
            }
            if (over) {
                val winner = line?.let { board[it[0]] }
                GameMessage(
                    when (winner) {
                        'X' -> tx("Kazandın!", "You win!")
                        'O' -> tx("Bilgisayar kazandı", "CPU wins")
                        else -> tx("Berabere", "Draw")
                    },
                    tx("Yeni el için orta tuş", "Center for another round"),
                    game.top,
                )
            }
        }
    }
}
