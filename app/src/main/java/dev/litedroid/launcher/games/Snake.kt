package dev.litedroid.launcher.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.LocalSounds
import dev.litedroid.launcher.ui.Sfx
import kotlinx.coroutines.delay
import kotlin.random.Random

private data class Cell(val x: Int, val y: Int)

private enum class Phase { READY, RUNNING, OVER }

private const val SNAKE_COLS = 26
private const val SNAKE_ROWS = 15

@Composable
fun SnakeGame(game: BuiltInGame) {
    val ctx = LocalContext.current
    val sounds = LocalSounds.current
    val start = listOf(Cell(8, 7), Cell(7, 7), Cell(6, 7))
    var snake by remember { mutableStateOf(start) }
    var dir by remember { mutableStateOf(Dir.RIGHT) }
    var pending by remember { mutableStateOf(Dir.RIGHT) }
    var food by remember { mutableStateOf(Cell(17, 7)) }
    var score by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(GameScores.best(ctx, "snake")) }
    var phase by remember { mutableStateOf(Phase.READY) }
    var newRecord by remember { mutableStateOf(false) }

    fun freeCell(body: List<Cell>): Cell {
        val taken = body.toHashSet()
        while (true) {
            val c = Cell(Random.nextInt(SNAKE_COLS), Random.nextInt(SNAKE_ROWS))
            if (c !in taken) return c
        }
    }

    fun reset() {
        snake = start
        dir = Dir.RIGHT
        pending = Dir.RIGHT
        food = freeCell(start)
        score = 0
        newRecord = false
    }

    LaunchedEffect(phase) {
        while (phase == Phase.RUNNING) {
            delay((150L - score * 3L).coerceAtLeast(65L))
            dir = pending
            val head = snake.first()
            val next = Cell(head.x + dir.dx, head.y + dir.dy)
            val hitsWall = next.x !in 0 until SNAKE_COLS || next.y !in 0 until SNAKE_ROWS
            if (hitsWall || next in snake.dropLast(1)) {
                sounds?.play(Sfx.BUMP)
                newRecord = GameScores.submit(ctx, "snake", score)
                if (newRecord) best = score
                phase = Phase.OVER
                break
            }
            val ate = next == food
            snake = listOf(next) + if (ate) snake else snake.dropLast(1)
            if (ate) {
                score++
                sounds?.play(Sfx.SELECT)
                food = freeCell(snake)
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GameStats(
            tx("Skor", "Score") to "$score",
            tx("En iyi", "Best") to "$best",
            tx("Uzunluk", "Length") to "${snake.size}",
            accent = game.top,
        )
        Spacer(Modifier.height(12.dp))
        GameSurface(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(SNAKE_COLS / SNAKE_ROWS.toFloat(), matchHeightConstraintsFirst = true),
            onDir = { d ->
                if (phase == Phase.READY) phase = Phase.RUNNING
                if (phase == Phase.RUNNING && !d.isOpposite(dir)) pending = d
            },
            onSelect = {
                when (phase) {
                    Phase.READY -> phase = Phase.RUNNING
                    Phase.OVER -> {
                        reset()
                        phase = Phase.RUNNING
                    }
                    Phase.RUNNING -> Unit
                }
            },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cw = size.width / SNAKE_COLS
                val ch = size.height / SNAKE_ROWS
                drawRoundRect(Color(0xFF0E1A14), cornerRadius = CornerRadius(18.dp.toPx()))
                // Dama deseni zemin
                for (y in 0 until SNAKE_ROWS) for (x in 0 until SNAKE_COLS) {
                    if ((x + y) % 2 == 0) {
                        drawRect(Color.White.copy(alpha = 0.025f), Offset(x * cw, y * ch), Size(cw, ch))
                    }
                }
                // Elma: parlayan kırmızı
                val fc = Offset((food.x + 0.5f) * cw, (food.y + 0.5f) * ch)
                drawCircle(Brush.radialGradient(listOf(Color(0x66FF4D5E), Color.Transparent), center = fc, radius = cw * 1.6f), radius = cw * 1.6f, center = fc)
                drawCircle(Color(0xFFFF4D5E), radius = cw * 0.38f, center = fc)
                drawCircle(Color.White.copy(alpha = 0.5f), radius = cw * 0.1f, center = fc + Offset(-cw * 0.12f, -ch * 0.12f))
                // Yılan: baştan kuyruğa koyulaşan yeşil
                val n = snake.size
                snake.forEachIndexed { i, c ->
                    val f = if (n <= 1) 0f else i / (n - 1f)
                    val color = lerp(game.top, game.bottom, f * 0.8f)
                    val inset = cw * (0.06f + f * 0.08f)
                    drawRoundRect(
                        color,
                        topLeft = Offset(c.x * cw + inset, c.y * ch + inset),
                        size = Size(cw - inset * 2, ch - inset * 2),
                        cornerRadius = CornerRadius(cw * 0.32f),
                    )
                }
                // Gözler
                val h = snake.first()
                val hc = Offset((h.x + 0.5f) * cw, (h.y + 0.5f) * ch)
                val side = Offset(-dir.dy * cw * 0.2f, dir.dx * ch * 0.2f)
                val fwd = Offset(dir.dx * cw * 0.15f, dir.dy * ch * 0.15f)
                listOf(hc + side + fwd, hc - side + fwd).forEach {
                    drawCircle(Color.White, radius = cw * 0.11f, center = it)
                    drawCircle(Color(0xFF0A1A10), radius = cw * 0.05f, center = it)
                }
            }
            when (phase) {
                Phase.READY -> GameMessage(tx("Yılan", "Snake"), tx("Başlamak için bir yön tuşuna bas", "Press an arrow key to start"), game.top)
                Phase.OVER -> GameMessage(
                    if (newRecord) tx("Yeni rekor: $score!", "New record: $score!") else tx("Oyun bitti · $score", "Game over · $score"),
                    tx("Tekrar oynamak için orta tuş", "Press center to play again"),
                    game.top,
                )
                Phase.RUNNING -> Unit
            }
        }
    }
}
