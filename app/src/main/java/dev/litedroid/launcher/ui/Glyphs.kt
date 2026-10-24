package dev.litedroid.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.graphics.Color as AndroidColor

// Material'ın temel ikon setinde olmayan birkaç simge, kodla çizilir.

/** Accent rengini daha canlı bir degrade çiftine çevirir (kutucuk zeminleri için). */
fun vividPair(accent: Color): Pair<Color, Color> {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(
        AndroidColor.argb(255, (accent.red * 255).toInt(), (accent.green * 255).toInt(), (accent.blue * 255).toInt()),
        hsv,
    )
    val sat = maxOf(hsv[1], 0.45f).coerceAtMost(0.72f)
    val top = Color(AndroidColor.HSVToColor(floatArrayOf((hsv[0] + 8f) % 360f, sat * 0.8f, 0.78f)))
    val bottom = Color(AndroidColor.HSVToColor(floatArrayOf(hsv[0], sat, 0.42f)))
    return top to bottom
}

@Composable
fun GridGlyph(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width
        val cell = s * 0.42f
        val gap = s - cell * 2
        for (i in 0..1) for (j in 0..1) {
            drawRoundRect(
                color,
                topLeft = Offset(i * (cell + gap), j * (cell + gap)),
                size = Size(cell, cell),
                cornerRadius = CornerRadius(cell * 0.28f),
            )
        }
    }
}

@Composable
fun GamepadGlyph(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val body = Path().apply {
            moveTo(w * 0.22f, h * 0.28f)
            lineTo(w * 0.78f, h * 0.28f)
            cubicTo(w * 0.98f, h * 0.28f, w * 1.02f, h * 0.86f, w * 0.86f, h * 0.86f)
            cubicTo(w * 0.74f, h * 0.86f, w * 0.7f, h * 0.68f, w * 0.6f, h * 0.68f)
            lineTo(w * 0.4f, h * 0.68f)
            cubicTo(w * 0.3f, h * 0.68f, w * 0.26f, h * 0.86f, w * 0.14f, h * 0.86f)
            cubicTo(-w * 0.02f, h * 0.86f, w * 0.02f, h * 0.28f, w * 0.22f, h * 0.28f)
            close()
        }
        drawPath(body, color)
        val bg = Color.Black.copy(alpha = 0.55f)
        drawLine(bg, Offset(w * 0.2f, h * 0.48f), Offset(w * 0.36f, h * 0.48f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
        drawLine(bg, Offset(w * 0.28f, h * 0.4f), Offset(w * 0.28f, h * 0.56f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
        drawCircle(bg, radius = w * 0.045f, center = Offset(w * 0.68f, h * 0.42f))
        drawCircle(bg, radius = w * 0.045f, center = Offset(w * 0.77f, h * 0.53f))
    }
}

@Composable
fun WifiGlyph(color: Color, connected: Boolean, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val c = Offset(w / 2f, w * 0.82f)
        val stroke = w * 0.1f
        listOf(0.32f, 0.56f, 0.8f).forEachIndexed { i, r ->
            val alpha = if (connected || i == 0) 1f else 0.3f
            drawArc(
                color = color.copy(alpha = color.alpha * alpha),
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(c.x - w * r, c.y - w * r),
                size = Size(w * r * 2, w * r * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        drawCircle(color, radius = w * 0.07f, center = c)
        if (!connected) {
            drawLine(Color(0xFFFF6B6B), Offset(w * 0.15f, w * 0.15f), Offset(w * 0.85f, w * 0.85f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

/** WMO hava koduna göre güneş / ay / bulut / yağmur / kar / şimşek / sis. */
@Composable
fun WeatherGlyph(code: Int, isDay: Boolean, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val sun = Color(0xFFFFC94D)
        val cloud = Color(0xFFE6ECF5)
        val rain = Color(0xFF6FC3FF)

        fun drawSunOrMoon(cx: Float, cy: Float, r: Float) {
            if (isDay) {
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    val dx = kotlin.math.cos(a).toFloat()
                    val dy = kotlin.math.sin(a).toFloat()
                    drawLine(sun, Offset(cx + dx * r * 1.35f, cy + dy * r * 1.35f), Offset(cx + dx * r * 1.75f, cy + dy * r * 1.75f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
                }
                drawCircle(sun, radius = r, center = Offset(cx, cy))
            } else {
                drawCircle(Color(0xFFDDE6FF), radius = r, center = Offset(cx, cy))
                drawCircle(Color(0xFF14183A), radius = r * 0.85f, center = Offset(cx + r * 0.45f, cy - r * 0.3f))
            }
        }

        fun drawCloud(cx: Float, cy: Float, s: Float, col: Color = cloud) {
            drawCircle(col, radius = s * 0.22f, center = Offset(cx - s * 0.18f, cy))
            drawCircle(col, radius = s * 0.3f, center = Offset(cx + s * 0.05f, cy - s * 0.08f))
            drawCircle(col, radius = s * 0.2f, center = Offset(cx + s * 0.28f, cy + s * 0.02f))
            drawRoundRect(col, topLeft = Offset(cx - s * 0.4f, cy), size = Size(s * 0.88f, s * 0.22f), cornerRadius = CornerRadius(s * 0.11f))
        }

        when {
            code == 0 -> drawSunOrMoon(w / 2, w / 2, w * 0.22f)
            code in 1..2 -> {
                drawSunOrMoon(w * 0.36f, w * 0.36f, w * 0.17f)
                drawCloud(w * 0.55f, w * 0.62f, w * 0.8f)
            }
            code == 3 || code == 45 || code == 48 -> {
                drawCloud(w * 0.5f, w * 0.5f, w * 0.95f)
                if (code != 3) {
                    drawLine(cloud.copy(alpha = 0.7f), Offset(w * 0.15f, w * 0.82f), Offset(w * 0.85f, w * 0.82f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
                }
            }
            code in 71..77 || code == 85 || code == 86 -> {
                drawCloud(w * 0.5f, w * 0.4f, w * 0.9f)
                for (i in 0..2) drawCircle(Color.White, radius = w * 0.05f, center = Offset(w * (0.3f + i * 0.2f), w * 0.82f))
            }
            code in 95..99 -> {
                drawCloud(w * 0.5f, w * 0.38f, w * 0.9f, Color(0xFFB8C2D6))
                val bolt = Path().apply {
                    moveTo(w * 0.52f, w * 0.52f); lineTo(w * 0.38f, w * 0.78f); lineTo(w * 0.5f, w * 0.78f)
                    lineTo(w * 0.42f, w * 0.98f); lineTo(w * 0.66f, w * 0.68f); lineTo(w * 0.54f, w * 0.68f); close()
                }
                drawPath(bolt, sun)
            }
            code in 51..67 || code in 80..82 -> {
                drawCloud(w * 0.5f, w * 0.38f, w * 0.9f)
                for (i in 0..2) {
                    val x = w * (0.3f + i * 0.2f)
                    drawLine(rain, Offset(x, w * 0.68f), Offset(x - w * 0.06f, w * 0.9f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
                }
            }
            else -> drawCloud(w * 0.5f, w * 0.5f, w * 0.95f)
        }
    }
}
