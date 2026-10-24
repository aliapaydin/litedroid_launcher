package dev.litedroid.launcher.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.LocalSounds
import dev.litedroid.launcher.ui.Sfx

/** Bulmaca + çözüm. Hepsi tek çözümlü olarak üretildi ve doğrulandı. */
private data class Puzzle(val level: Int, val givens: String, val solution: String)

private val PUZZLES = listOf(
    Puzzle(0, "600320050042000609005010304000540186000180203068293500020030071050870902800960005", "691324758342758619785619324239547186574186293168293547926435871453871962817962435"),
    Puzzle(0, "002071480000680500806035009167000903400300000500700248004597860600400000975060020", "352971486719684532846235179167842953428359617593716248234597861681423795975168324"),
    Puzzle(0, "000250030300107205820040070007081423000076510000400960400000002502764800080000706", "719258634364197285825643179697581423243976518158432967476819352532764891981325746"),
    Puzzle(1, "720009308030000000005804000002070003593048700001300080207960504000700009009003800", "726519348438627195915834276842176953593248761671395482287961534354782619169453827"),
    Puzzle(1, "024000000000003100600010007400076500700001498002040003000687001001920800870005000", "124798635987563124635412987498376512763251498512849763249687351351924876876135249"),
    Puzzle(1, "000070015000400907000003000640000001000905600510300082090504260000700003405600000", "864279315351468927972153846643827591728915634519346782197534268286791453435682179"),
)

private const val NEXT_COL = 9 // imleç bu sütundaysa "Sonraki bulmaca" düğmesinde

@Composable
fun SudokuGame(game: BuiltInGame) {
    val sounds = LocalSounds.current
    var index by remember { mutableIntStateOf(0) }
    val puzzle = PUZZLES[index]
    var cells by remember { mutableStateOf(IntArray(81) { puzzle.givens[it] - '0' }) }
    var cx by remember { mutableIntStateOf(0) }
    var cy by remember { mutableIntStateOf(0) }
    val given = remember(index) { BooleanArray(81) { puzzle.givens[it] != '0' } }
    val solved = (0 until 81).all { cells[it] == puzzle.solution[it] - '0' }
    val mistakes = (0 until 81).count { !given[it] && cells[it] != 0 && cells[it] != puzzle.solution[it] - '0' }

    fun load(i: Int) {
        index = i
        val p = PUZZLES[i]
        cells = IntArray(81) { p.givens[it] - '0' }
        cx = 0
        cy = 0
    }

    fun setDigit(v: Int) {
        val i = cy * 9 + cx
        if (cx == NEXT_COL || given[i] || solved) return
        cells = cells.copyOf().also { it[i] = v }
        sounds?.play(if (v != 0 && v == puzzle.solution[i] - '0') Sfx.SELECT else Sfx.NAV)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GameStats(
                tx("Bulmaca", "Puzzle") to "${index + 1}/${PUZZLES.size}",
                tx("Zorluk", "Level") to if (puzzle.level == 0) tx("Kolay", "Easy") else tx("Orta", "Medium"),
                tx("Hata", "Mistakes") to "$mistakes",
                accent = game.top,
            )
            Spacer(Modifier.height(10.dp))
            GameSurface(
                onDir = { d ->
                    cx = (cx + d.dx).coerceIn(0, NEXT_COL)
                    if (cx != NEXT_COL) cy = (cy + d.dy).coerceIn(0, 8)
                    sounds?.play(Sfx.NAV)
                },
                onSelect = {
                    when {
                        solved || cx == NEXT_COL -> load((index + 1) % PUZZLES.size)
                        else -> {
                            val i = cy * 9 + cx
                            if (!given[i]) setDigit((cells[i] + 1) % 10) else sounds?.play(Sfx.BUMP)
                        }
                    }
                },
                onDigit = { setDigit(it) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SudokuBoard(cells, given, puzzle, cx, cy, game.top)
                    Spacer(Modifier.width(14.dp))
                    val onNext = cx == NEXT_COL
                    Text(
                        tx("Sonraki ›", "Next ›"),
                        color = if (onNext) Color(0xFF0E1222) else Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (onNext) Color.White else Color.White.copy(alpha = 0.07f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                if (solved) {
                    GameMessage(
                        tx("Tebrikler, çözdün!", "Solved, well done!"),
                        tx("Sonraki bulmaca için orta tuş", "Center for the next puzzle"),
                        game.top,
                    )
                }
            }
        }
    }
}

@Composable
private fun SudokuBoard(cells: IntArray, given: BooleanArray, puzzle: Puzzle, cx: Int, cy: Int, accent: Color) {
    val cell = 38.dp
    val selected = if (cx in 0..8) cy * 9 + cx else -1
    val selectedValue = if (selected >= 0) cells[selected] else 0
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF10131F))
    ) {
        Column {
            for (y in 0 until 9) Row {
                for (x in 0 until 9) {
                    val i = y * 9 + x
                    val v = cells[i]
                    val sameBox = selected >= 0 && (x / 3 == cx / 3 && y / 3 == cy / 3)
                    val related = selected >= 0 && (x == cx || y == cy || sameBox)
                    val wrong = !given[i] && v != 0 && v != puzzle.solution[i] - '0'
                    Box(
                        Modifier
                            .size(cell)
                            .background(
                                when {
                                    i == selected -> accent.copy(alpha = 0.55f)
                                    v != 0 && v == selectedValue -> accent.copy(alpha = 0.22f)
                                    related -> Color.White.copy(alpha = 0.06f)
                                    else -> Color.Transparent
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (v != 0) {
                            Text(
                                "$v",
                                color = when {
                                    wrong -> Color(0xFFFF5C6E)
                                    given[i] -> Color.White
                                    else -> Color(0xFF8FE9E1)
                                },
                                fontSize = 20.sp,
                                fontWeight = if (given[i]) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
        // Izgara çizgileri: kutular kalın, hücreler ince
        Canvas(Modifier.size(cell * 9)) {
            val step = size.width / 9
            for (k in 0..9) {
                val thick = k % 3 == 0
                val color = if (thick) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.1f)
                val stroke = if (thick) 2.dp.toPx() else 1.dp.toPx()
                drawLine(color, Offset(k * step, 0f), Offset(k * step, size.height), stroke)
                drawLine(color, Offset(0f, k * step), Offset(size.width, k * step), stroke)
            }
        }
    }
}
