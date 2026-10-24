package dev.litedroid.launcher.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.LocalSounds
import dev.litedroid.launcher.ui.Sfx

/** Bölümler (hepsi çözülebilir olarak doğrulandı). # duvar, . hedef, $ kutu, * hedefte kutu, @ oyuncu. */
private val LEVELS = listOf(
    listOf(
        "#######",
        "#     #",
        "# .$@ #",
        "#     #",
        "#######",
    ),
    listOf(
        "########",
        "#      #",
        "# .$   #",
        "#   $. #",
        "#  @   #",
        "########",
    ),
    listOf(
        " ######",
        " #    #",
        "##$## #",
        "# .   #",
        "# $@. #",
        "#   ###",
        "#####",
    ),
    listOf(
        "#######",
        "#.   .#",
        "# $$  #",
        "#  @  #",
        "# $   #",
        "#.    #",
        "#######",
    ),
    listOf(
        "  ######",
        "###    #",
        "#.  $# #",
        "#. $ @ #",
        "###  $.#",
        "  #    #",
        "  ######",
    ),
)

private data class P(val x: Int, val y: Int)

private class Level(rows: List<String>) {
    val width = rows.maxOf { it.length }
    val height = rows.size
    val walls = HashSet<P>()
    val goals = HashSet<P>()
    val boxes = HashSet<P>()
    var player = P(0, 0)

    init {
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                val p = P(x, y)
                when (c) {
                    '#' -> walls += p
                    '.' -> goals += p
                    '$' -> boxes += p
                    '*' -> { boxes += p; goals += p }
                    '@' -> player = p
                    '+' -> { player = p; goals += p }
                }
            }
        }
    }
}

@Composable
fun BoxPushGame(game: BuiltInGame) {
    val sounds = LocalSounds.current
    var levelIndex by remember { mutableIntStateOf(0) }
    var level by remember { mutableStateOf(Level(LEVELS[0])) }
    var player by remember { mutableStateOf(level.player) }
    var boxes by remember { mutableStateOf(level.boxes.toSet()) }
    var moves by remember { mutableIntStateOf(0) }
    val solved = boxes == level.goals

    fun load(i: Int) {
        levelIndex = i
        level = Level(LEVELS[i])
        player = level.player
        boxes = level.boxes.toSet()
        moves = 0
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GameStats(
            tx("Bölüm", "Level") to "${levelIndex + 1}/${LEVELS.size}",
            tx("Hamle", "Moves") to "$moves",
            tx("Yerleşen", "Placed") to "${boxes.count { it in level.goals }}/${level.goals.size}",
            accent = game.top,
        )
        Spacer(Modifier.height(12.dp))
        GameSurface(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(level.width / level.height.toFloat(), matchHeightConstraintsFirst = true),
            onDir = { d ->
                if (solved) return@GameSurface
                val next = P(player.x + d.dx, player.y + d.dy)
                if (next in level.walls) {
                    sounds?.play(Sfx.BUMP)
                    return@GameSurface
                }
                if (next in boxes) {
                    val beyond = P(next.x + d.dx, next.y + d.dy)
                    if (beyond in level.walls || beyond in boxes) {
                        sounds?.play(Sfx.BUMP)
                        return@GameSurface
                    }
                    boxes = boxes - next + beyond
                    sounds?.play(if (beyond in level.goals) Sfx.SELECT else Sfx.NAV)
                } else {
                    sounds?.play(Sfx.NAV)
                }
                player = next
                moves++
            },
            onSelect = {
                if (solved) load((levelIndex + 1) % LEVELS.size) else load(levelIndex)
            },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c = size.width / level.width
                val r = CornerRadius(c * 0.2f)
                for (y in 0 until level.height) for (x in 0 until level.width) {
                    val p = P(x, y)
                    val tl = Offset(x * c, y * c)
                    if (p in level.walls) {
                        drawRoundRect(Color(0xFF3A2E5C), tl + Offset(c * 0.04f, c * 0.04f), Size(c * 0.92f, c * 0.92f), r)
                        drawRoundRect(Color.White.copy(alpha = 0.08f), tl + Offset(c * 0.04f, c * 0.04f), Size(c * 0.92f, c * 0.3f), r)
                    } else {
                        drawRoundRect(Color.White.copy(alpha = 0.035f), tl + Offset(c * 0.03f, c * 0.03f), Size(c * 0.94f, c * 0.94f), r)
                    }
                    if (p in level.goals) {
                        drawCircle(game.top.copy(alpha = 0.8f), radius = c * 0.2f, center = tl + Offset(c / 2, c / 2), style = Stroke(c * 0.06f))
                    }
                }
                boxes.forEach { b ->
                    val tl = Offset(b.x * c + c * 0.12f, b.y * c + c * 0.12f)
                    val done = b in level.goals
                    val body = if (done) Color(0xFF3BE07F) else game.top
                    drawRoundRect(body, tl, Size(c * 0.76f, c * 0.76f), CornerRadius(c * 0.14f))
                    drawRoundRect(Color.Black.copy(alpha = 0.25f), tl, Size(c * 0.76f, c * 0.76f), CornerRadius(c * 0.14f), style = Stroke(c * 0.05f))
                    drawLine(Color.Black.copy(alpha = 0.25f), tl + Offset(c * 0.12f, c * 0.12f), tl + Offset(c * 0.64f, c * 0.64f), c * 0.05f)
                    drawLine(Color.Black.copy(alpha = 0.25f), tl + Offset(c * 0.64f, c * 0.12f), tl + Offset(c * 0.12f, c * 0.64f), c * 0.05f)
                }
                val pc = Offset((player.x + 0.5f) * c, (player.y + 0.5f) * c)
                drawCircle(Color(0xFF4D9BFF), radius = c * 0.34f, center = pc)
                drawCircle(Color.White, radius = c * 0.07f, center = pc + Offset(-c * 0.1f, -c * 0.05f))
                drawCircle(Color.White, radius = c * 0.07f, center = pc + Offset(c * 0.1f, -c * 0.05f))
            }
            if (solved) {
                val last = levelIndex == LEVELS.lastIndex
                GameMessage(
                    if (last) tx("Tüm bölümler bitti!", "All levels cleared!") else tx("Bölüm tamam!", "Level clear!"),
                    tx("$moves hamle · Orta tuş: ", "$moves moves · Center: ") +
                        if (last) tx("baştan başla", "start over") else tx("sonraki bölüm", "next level"),
                    game.top,
                )
            }
        }
    }
}
