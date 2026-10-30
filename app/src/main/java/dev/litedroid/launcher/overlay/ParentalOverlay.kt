package dev.litedroid.launcher.overlay

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.data.Profile
import dev.litedroid.launcher.tools.ProfileFace
import dev.litedroid.launcher.tx

/**
 * Çocuğun süresi dolunca (ya da ebeveyn kapatınca) her şeyin üstüne gelen kilit ekranı.
 * Yalnızca gösterir: tuşları kumanda servisi karşılar (Tamam: süre iste, diğer tuşlar: profil seçimi),
 * pencere odak almaz. Sürekli dönen animasyon yok, zayıf kutularda video çözücüyle yarışmasın.
 */
@Composable
fun TimeUpScreen(profile: Profile, reason: String, requested: Boolean) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1446), Color(0xFF0B0A1C), Color(0xFF05050B)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 640.dp)) {
            Text("🌙", fontSize = 64.sp)
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
