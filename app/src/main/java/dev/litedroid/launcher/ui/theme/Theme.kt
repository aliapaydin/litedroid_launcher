package dev.litedroid.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.litedroid.launcher.R

/** Plus Jakarta Sans (SIL Open Font License 1.1). Tüm arayüzün yazı tipi. */
val Jakarta = FontFamily(
    Font(R.font.jakarta_extralight, FontWeight.Thin),
    Font(R.font.jakarta_extralight, FontWeight.ExtraLight),
    Font(R.font.jakarta_extralight, FontWeight.Light),
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
    Font(R.font.jakarta_extrabold, FontWeight.Black),
)

@Composable
fun LiteDroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Black,
            surface = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White,
        ),
    ) {
        ProvideTextStyle(TextStyle(fontFamily = Jakarta, letterSpacing = TextStyle.Default.letterSpacing), content)
    }
}
