package dev.litedroid.launcher.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.LocalSounds
import dev.litedroid.launcher.ui.Sfx
import kotlin.math.abs
import kotlin.random.Random

private const val LANES = 3
private const val CAR_LEN = 0.16f // yol yüksekliğine oranla
private const val PLAYER_Y = 0.80f

private data class Car(val lane: Int, val y: Float, val color: Color)

private val TRAFFIC = listOf(Color(0xFFFFB13D), Color(0xFF4D9BFF), Color(0xFFB06BFF), Color(0xFF2ED3C8), Color(0xFFE6ECF5))

private enum class RacePhase { READY, RUNNING, OVER }

@Composable
fun RacerGame(game: BuiltInGame) {
    val ctx = LocalContext.current
    val sounds = LocalSounds.current
    var phase by remember { mutableStateOf(RacePhase.READY) }
    var lane by remember { mutableIntStateOf(1) }
    var playerX by remember { mutableFloatStateOf(1f) } // şeritler arasında yumuşak kayma
    var cars by remember { mutableStateOf(emptyList<Car>()) }
    var distance by remember { mutableFloatStateOf(0f) }
    var best by remember { mutableIntStateOf(GameScores.best(ctx, "racer")) }
    var newRecord by remember { mutableStateOf(false) }
    val score = (distance * 10).toInt()

    fun reset() {
        lane = 1
        playerX = 1f
        cars = emptyList()
        distance = 0f
        newRecord = false
    }

    LaunchedEffect(phase) {
        if (phase != RacePhase.RUNNING) return@LaunchedEffect
        var last = 0L
        var sinceSpawn = 0f
        while (phase == RacePhase.RUNNING) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                val speed = 0.45f + distance * 0.012f // ekran/sn
                distance += speed * dt
                playerX += (lane - playerX) * (dt * 14f).coerceAtMost(1f)

                var moved = cars.map { it.copy(y = it.y + speed * dt) }.filter { it.y < 1.2f }
                sinceSpawn += speed * dt
                if (sinceSpawn > 0.42f - (distance * 0.004f).coerceAtMost(0.16f)) {
                    sinceSpawn = 0f
                    // En az bir şerit hep boş kalsın
                    val blocked = if (Random.nextFloat() < 0.25f + distance * 0.01f) 2 else 1
                    val lanes = (0 until LANES).shuffled().take(blocked)
                    moved = moved + lanes.map { Car(it, -CAR_LEN, TRAFFIC.random()) }
                }
                cars = moved

                val hit = cars.any { c ->
                    abs(c.lane - playerX) < 0.6f && abs(c.y - PLAYER_Y) < CAR_LEN * 0.9f
                }
                if (hit) {
                    sounds?.play(Sfx.BUMP)
                    newRecord = GameScores.submit(ctx, "racer", (distance * 10).toInt())
                    if (newRecord) best = (distance * 10).toInt()
                    phase = RacePhase.OVER
                }
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GameStats(
            tx("Mesafe", "Distance") to "$score m",
            tx("En iyi", "Best") to "$best m",
            tx("Hız", "Speed") to "${(80 + distance * 4).toInt()} km/s",
            accent = game.top,
        )
        Spacer(Modifier.height(12.dp))
        GameSurface(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(0.62f, matchHeightConstraintsFirst = true),
            onDir = { d ->
                when (d) {
                    Dir.LEFT -> if (lane > 0) { lane--; sounds?.play(Sfx.NAV) }
                    Dir.RIGHT -> if (lane < LANES - 1) { lane++; sounds?.play(Sfx.NAV) }
                    else -> Unit
                }
                if (phase == RacePhase.READY && (d == Dir.LEFT || d == Dir.RIGHT || d == Dir.UP)) phase = RacePhase.RUNNING
            },
            onSelect = {
                when (phase) {
                    RacePhase.READY -> phase = RacePhase.RUNNING
                    RacePhase.OVER -> {
                        reset()
                        phase = RacePhase.RUNNING
                    }
                    RacePhase.RUNNING -> Unit
                }
            },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // Çimen + yol
                drawRoundRect(Color(0xFF0D2A1A), cornerRadius = CornerRadius(18.dp.toPx()))
                val roadL = w * 0.1f
                val roadW = w * 0.8f
                drawRect(Brush.horizontalGradient(listOf(Color(0xFF22242E), Color(0xFF2C2F3B), Color(0xFF22242E)), startX = roadL, endX = roadL + roadW), Offset(roadL, 0f), Size(roadW, h))
                drawRect(Color.White.copy(alpha = 0.85f), Offset(roadL - 4.dp.toPx(), 0f), Size(3.dp.toPx(), h))
                drawRect(Color.White.copy(alpha = 0.85f), Offset(roadL + roadW + 1.dp.toPx(), 0f), Size(3.dp.toPx(), h))
                // Kayan şerit çizgileri
                val laneW = roadW / LANES
                val dash = h * 0.08f
                val offset = (distance * h * 1.0f) % (dash * 2)
                for (i in 1 until LANES) {
                    val x = roadL + laneW * i - 1.5.dp.toPx()
                    var y = -dash * 2 + offset
                    while (y < h) {
                        drawRect(Color.White.copy(alpha = 0.5f), Offset(x, y), Size(3.dp.toPx(), dash))
                        y += dash * 2
                    }
                }
                cars.forEach { c -> drawCar(roadL + laneW * (c.lane + 0.5f), c.y * h, laneW, h, c.color, false) }
                drawCar(roadL + laneW * (playerX + 0.5f), PLAYER_Y * h, laneW, h, game.top, true)
            }
            when (phase) {
                RacePhase.READY -> GameMessage(tx("Mini Yarış", "Mini Racer"), tx("Başlamak için sol/sağ ya da orta tuş", "Left/right or center to start"), game.top)
                RacePhase.OVER -> GameMessage(
                    if (newRecord) tx("Yeni rekor: $score m!", "New record: $score m!") else tx("Kaza! $score m", "Crash! $score m"),
                    tx("Tekrar için orta tuş", "Center to race again"),
                    game.top,
                )
                RacePhase.RUNNING -> Unit
            }
        }
    }
}

private fun DrawScope.drawCar(cx: Float, cy: Float, laneW: Float, h: Float, color: Color, player: Boolean) {
    val cw = laneW * 0.56f
    val ch = h * CAR_LEN
    val tl = Offset(cx - cw / 2, cy - ch / 2)
    // gölge
    drawRoundRect(Color.Black.copy(alpha = 0.35f), tl + Offset(3f, 6f), Size(cw, ch), CornerRadius(cw * 0.28f))
    drawRoundRect(color, tl, Size(cw, ch), CornerRadius(cw * 0.28f))
    // camlar
    val glass = Color(0xFF0E1222).copy(alpha = 0.75f)
    drawRoundRect(glass, tl + Offset(cw * 0.15f, ch * 0.2f), Size(cw * 0.7f, ch * 0.2f), CornerRadius(cw * 0.1f))
    drawRoundRect(glass, tl + Offset(cw * 0.15f, ch * 0.62f), Size(cw * 0.7f, ch * 0.14f), CornerRadius(cw * 0.1f))
    // farlar
    val lightY = if (player) tl.y + ch * 0.04f else tl.y + ch * 0.9f
    val light = if (player) Color(0xFFFFF4C2) else Color(0xFFFF4D5E)
    drawCircle(light, radius = cw * 0.09f, center = Offset(tl.x + cw * 0.22f, lightY))
    drawCircle(light, radius = cw * 0.09f, center = Offset(tl.x + cw * 0.78f, lightY))
}
