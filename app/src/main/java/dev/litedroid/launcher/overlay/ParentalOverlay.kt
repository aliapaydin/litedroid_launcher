package dev.litedroid.launcher.overlay

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.data.Profile
import dev.litedroid.launcher.tools.ProfileFace
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.isSelectKey
import kotlinx.coroutines.delay

/**
 * Çocuğun süresi dolunca (ya da ebeveyn kapatınca) her şeyin üstüne gelen kilit ekranı.
 * Tamam: ebeveynden süre ister. Diğer tuşlar: "Kim İzliyor?" ekranına döner.
 */
@Composable
fun TimeUpScreen(profile: Profile, reason: String, alreadyRequested: Boolean, onRequest: () -> Unit, onLeave: () -> Unit) {
    val fr = remember { FocusRequester() }
    var requested by remember { mutableStateOf(alreadyRequested) }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { fr.requestFocus() }
    }
    // Kilit ekranında kimse yoksa bir süre sonra profil ekranına döner
    LaunchedEffect(requested) {
        delay(if (requested) 8_000 else 60_000)
        onLeave()
    }
    val moon by rememberInfiniteTransition(label = "moon").animateFloat(-6f, 6f, infiniteRepeatable(tween(2_400), RepeatMode.Reverse), label = "m")
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1446), Color(0xFF0B0A1C), Color(0xFF05050B))))
            .focusRequester(fr)
            .onPreviewKeyEvent { e ->
                if (e.key.isSelectKey && !requested) {
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) armed = true
                    if (e.type == KeyEventType.KeyUp && armed) {
                        armed = false
                        requested = true
                        onRequest()
                    }
                    return@onPreviewKeyEvent true
                }
                if (e.type == KeyEventType.KeyUp && e.key != Key.Home) onLeave()
                true
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 640.dp)) {
            Text("🌙", fontSize = 64.sp, modifier = Modifier.graphicsLayer { translationY = moon })
            Spacer(Modifier.height(10.dp))
            ProfileFace(profile, 84.dp)
            Spacer(Modifier.height(14.dp))
            Text(tx("Şimdilik bu kadar, ${profile.name}!", "That's all for now, ${profile.name}!"), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(reason, color = Color.White.copy(alpha = 0.75f), fontSize = 17.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (requested) tx("✓ İsteğin ebeveynine gönderildi", "✓ Your request was sent to your parent")
                    else "🙏  " + tx("Tamam: biraz daha süre iste", "OK: ask for a bit more time"),
                    color = if (requested) Color(0xFF7CFFCB) else Color(0xFF14121F), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (requested) Color.White.copy(alpha = 0.1f) else Color.White)
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                tx("Ebeveynin telefondan ya da TV'deki Ebeveyn denetiminden süre ekleyebilir. Başka bir tuş: profil seçimi.",
                    "Your parent can add time from their phone or Parental controls on the TV. Any other key: profile selection."),
                color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, textAlign = TextAlign.Center,
            )
        }
    }
}
