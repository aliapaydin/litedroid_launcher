package dev.litedroid.launcher.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import android.os.Build
import dev.litedroid.launcher.tx
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.R

object Credits {
    const val APP_NAME = "LiteDroid Launcher"
    /** Hakkında ekranında görünen geliştirici bilgileri. */
    const val DEVELOPER = "Ali Apaydın"
    const val EMAIL = "apaydin.a@gmail.com"
    const val YEAR = "2026"
}

/** Cam "Hakkında" penceresi: solda uygulama ve geliştirici, sağda cihaz bilgisi, özellikler ve emeği geçenler. */
@Composable
fun AboutDialog(onClose: () -> Unit, contentInfo: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "-"
    }
    val focusRequester = remember { FocusRequester() }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Row(
        modifier
            .width(760.dp)
            .glass(RoundedCornerShape(30.dp), strong = true)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                when {
                    event.key == Key.Back -> false
                    event.key.isSelectKey -> {
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) armed = true
                        if (event.type == KeyEventType.KeyUp) {
                            if (armed) onClose()
                            armed = false
                        }
                        true
                    }
                    else -> true // yön tuşları pencerenin dışına çıkmasın
                }
            }
            .focusable()
            .padding(26.dp),
    ) {
        // ---- Sol: uygulama + geliştirici ----
        Column(
            Modifier.width(280.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier
                    .size(76.dp)
                    .softShadow(20.dp, strength = 1.4f)
                    .clip(RoundedCornerShape(20.dp)),
            )
            Spacer(Modifier.height(12.dp))
            Wordmark(26.sp)
            Text(
                "Launcher",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                tx("Sürüm $version", "Version $version"),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF3A7BFF), Color(0xFF7B5CFF))))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = tx(
                    "Android TV ve Google TV için cam görünümlü, hızlı, sade ve reklamsız ana ekran",
                    "A fast, clean, ad-free glass-style home screen for Android TV and Google TV",
                ),
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            // Geliştirici kartı
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AVATAR_BRUSH)
                        .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("AA", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        tx("GELİŞTİRİCİ", "DEVELOPER"),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp,
                    )
                    Text(Credits.DEVELOPER, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Email, null, tint = Color(0xFF9DB8FF), modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(Credits.EMAIL, color = Color(0xFFCFDCFF), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = tx("Kapat", "Close"),
                color = Color(0xFF111114),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.94f))
                    .padding(horizontal = 40.dp, vertical = 9.dp),
            )
        }

        Spacer(Modifier.width(24.dp))
        Box(
            Modifier
                .width(1.dp)
                .height(420.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent)))
        )
        Spacer(Modifier.width(24.dp))

        // ---- Sağ: bilgiler ----
        Column(Modifier.weight(1f)) {
            SectionLabel(tx("CİHAZ", "DEVICE"))
            InfoRow(tx("Model", "Model"), "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}")
            InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            InfoRow(tx("İçerik erişimi", "Content access"), contentInfo)
            Spacer(Modifier.height(12.dp))
            SectionLabel(tx("ÖZELLİKLER", "FEATURES"))
            Spacer(Modifier.height(4.dp))
            FeatureChips(
                listOf(
                    tx("Casual ve Modern arayüz", "Casual & Modern layouts"),
                    tx("Kaldığın yerden devam", "Continue watching"),
                    tx("Dahili oyunlar", "Built-in games"),
                    tx("Animasyonlu ekran koruyucu", "Animated screensaver"),
                    tx("Hava durumu", "Weather"),
                    tx("Türkçe / English", "Turkish / English"),
                    tx("Reklamsız", "Ad-free"),
                    tx("Root gerektirmez", "No root needed"),
                )
            )
            Spacer(Modifier.height(12.dp))
            SectionLabel(tx("TEŞEKKÜRLER", "CREDITS"))
            Credit("Plus Jakarta Sans", tx("Yazı tipi · SIL Open Font License", "Typeface · SIL Open Font License"))
            Credit("Open-Meteo", tx("Hava durumu verisi", "Weather data"))
            Credit("Jetpack Compose · Coil", tx("Arayüz ve görsel yükleme", "UI and image loading"))
            Spacer(Modifier.height(12.dp))
            Text(
                "© ${Credits.YEAR} ${Credits.DEVELOPER} · ${Credits.APP_NAME}",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Credit(name: String, what: String) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(what, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeatureChips(items: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { label ->
            Text(
                label,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
