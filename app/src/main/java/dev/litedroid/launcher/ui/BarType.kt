package dev.litedroid.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.I18n
import dev.litedroid.launcher.tx
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Üst barların ortak tipografisi: logo yazısı, saat/tarih bloğu, ayırıcı ve cam parlaması.

private val WORDMARK_BRUSH = Brush.linearGradient(
    listOf(Color.White, Color(0xFFE4DCFF), Color(0xFFFFD9EE)),
)
private val SOFT_TEXT_SHADOW = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1f), 6f)

/** "LiteDroid" yazısı: hafif degradeli, sıkı harf aralıklı, kalın. */
@OptIn(ExperimentalTextApi::class)
@Composable
fun Wordmark(fontSize: TextUnit = 17.sp) {
    Text(
        "LiteDroid",
        style = LocalTextStyle.current.copy(
            brush = WORDMARK_BRUSH,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.4).sp,
            shadow = SOFT_TEXT_SHADOW,
        ),
    )
}

/**
 * Saat ve tarih: rakamlar sabit genişlikte (tnum) olduğu için dakika değişince yazı kıpırdamaz;
 * tarih küçük, büyük harf ve geniş aralıklı.
 */
@Composable
fun BarClock(now: LocalDateTime, timeSize: TextUnit) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            now.format(DateTimeFormatter.ofPattern("HH:mm", I18n.locale)),
            style = LocalTextStyle.current.copy(
                color = Color.White,
                fontSize = timeSize,
                lineHeight = timeSize * 1.05f,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.6).sp,
                fontFeatureSettings = "tnum",
                shadow = SOFT_TEXT_SHADOW,
            ),
        )
        Text(
            now.format(DateTimeFormatter.ofPattern(tx("d MMM EEE", "EEE, MMM d"), I18n.locale)).uppercase(I18n.locale),
            style = LocalTextStyle.current.copy(
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.1.sp,
            ),
        )
    }
}

/** Uçlara doğru silikleşen ince dikey ayırıcı. */
@Composable
fun BarDivider(height: Dp) {
    Box(
        Modifier
            .width(1.dp)
            .height(height)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.26f), Color.Transparent)
                )
            )
    )
}

/** Cam yüzeyin üst yarısında çok hafif ışık: barı düz bir kutudan ayırır. Çizimi tek bir dikdörtgen. */
fun Modifier.glassSheen(strength: Float = 1f): Modifier = this.drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.09f * strength), Color.Transparent),
            endY = size.height * 0.55f,
        ),
        size = Size(size.width, size.height * 0.55f),
    )
}
