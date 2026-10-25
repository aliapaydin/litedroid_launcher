package dev.litedroid.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.data.WifiSecurity
import dev.litedroid.launcher.quiz.QrCode
import dev.litedroid.launcher.tools.PhoneBridge
import dev.litedroid.launcher.tx
import kotlinx.coroutines.delay

/** Wi-Fi'ye bağlan QR'ı (telefon kameraları tanır): WIFI:T:WPA;S:ağ;P:şifre;; */
fun wifiQrPayload(ssid: String, password: String, security: WifiSecurity): String {
    fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")
    return if (security == WifiSecurity.OPEN) "WIFI:T:nopass;S:${esc(ssid)};;"
    else "WIFI:T:${security.qr};S:${esc(ssid)};P:${esc(password)};;"
}

@Composable
private fun DialogCard(content: @Composable () -> Unit) {
    Box(Modifier.background(Color.Black.copy(alpha = 0.001f))) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .glass(RoundedCornerShape(30.dp), strong = true)
                .padding(28.dp),
        ) { content() }
    }
}

@Composable
private fun ExtraButton(label: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else if (primary) Color(0xFF6B4DFF) else Color(0xFF3A3658))
            .border(1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onClick)
            .focusable()
            .padding(horizontal = 22.dp, vertical = 11.dp),
    ) {
        Text(label, color = if (focused) Color(0xFF111114) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Misafir Wi-Fi: büyük QR, ağ adı ve şifre. Ağ girilmediyse ayarlamaya yönlendirir. */
@Composable
fun WifiQrDialog(ssid: String, password: String, security: WifiSecurity, onEdit: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { first.requestFocus() }
    }
    Box(modifier) {
        DialogCard {
            Text("📶 " + tx("Misafir Wi-Fi", "Guest Wi-Fi"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(14.dp))
            if (ssid.isBlank()) {
                Text(tx("Henüz ağ bilgisi girilmedi. Ağ adını ve şifreni bir kez gir; misafirler QR'ı okutup bağlansın.",
                    "No network saved yet. Enter your network name and password once; guests scan the QR to join."),
                    color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExtraButton(tx("Ağı ayarla", "Set up network"), true, Modifier.focusRequester(first), onEdit)
                    ExtraButton(tx("Kapat", "Close"), false, onClick = onClose)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)) {
                        QrCode(wifiQrPayload(ssid, password, security), 210.dp)
                    }
                    Spacer(Modifier.width(26.dp))
                    Column(Modifier.width(320.dp)) {
                        Text(tx("AĞ", "NETWORK"), color = Color(0xFFB9A8FF), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text(ssid, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        if (security != WifiSecurity.OPEN) {
                            Text(tx("ŞİFRE", "PASSWORD"), color = Color(0xFFB9A8FF), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Text(password, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(10.dp))
                        }
                        Text(tx("Telefonun kamerasını QR'a tut, \"Ağa katıl\"a dokun. Şifre yazmaya gerek yok.",
                            "Point the phone camera at the QR and tap \"Join\". No typing needed."),
                            color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ExtraButton(tx("Kapat", "Close"), true, Modifier.focusRequester(first), onClose)
                            ExtraButton(tx("Düzenle", "Edit"), false, onClick = onEdit)
                        }
                    }
                }
            }
        }
    }
}

/** Telefondan TV'ye gönder: QR'ı okutan telefon yazı ya da bağlantı gönderir. */
@Composable
fun PhoneSendDialog(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var url by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        PhoneBridge.start(context)
        url = PhoneBridge.url
        onDispose { }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { first.requestFocus() }
    }
    val last by PhoneBridge.last
    Box(modifier) {
        DialogCard {
            Text("📲 " + tx("Telefondan TV'ye gönder", "Send from your phone"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val u = url
                if (u != null) {
                    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)) { QrCode(u, 200.dp) }
                    Spacer(Modifier.width(26.dp))
                }
                Column(Modifier.width(340.dp)) {
                    if (u == null) {
                        Text(tx("Ağ bağlantısı yok.", "No network connection."), color = Color(0xFFFFB4A8), fontSize = 15.sp)
                    } else {
                        Text(tx("1. Aynı Wi-Fi'deki telefonla QR'ı okut.\n2. Açılan sayfaya yaz ya da yapıştır.\n3. \"Yazı olarak gönder\": TV'de seçili yazı kutusuna gider (Netflix şifresi, YouTube araması…).\n4. \"TV'de aç\": bağlantı LiteDroid tarayıcısında açılır.",
                            "1. Scan the QR with a phone on the same Wi-Fi.\n2. Type or paste on the page that opens.\n3. \"Send as text\": goes into the selected text box on the TV.\n4. \"Open on TV\": the link opens in LiteDroid's browser."),
                            color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 19.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(tx("Bu pencereyi kapatıp başka uygulamaya geçebilirsin; telefon sayfası 30 dakika çalışır.", "You can close this and switch apps; the phone page keeps working for 30 minutes."),
                            color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        Text(u, color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp)
                    }
                    last?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(tx("Son gelen: ", "Last received: ") + it, color = Color(0xFF7CF0A8), fontSize = 12.sp, maxLines = 2)
                    }
                    Spacer(Modifier.height(14.dp))
                    ExtraButton(tx("Kapat", "Close"), true, Modifier.focusRequester(first), onClose)
                }
            }
        }
    }
}

/** Sabah hava uyarısı: sağ altta kısa süre görünen kart. */
@Composable
fun WeatherAlertCard(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(22.dp)
            .widthIn(max = 460.dp)
            .glass(RoundedCornerShape(22.dp), strong = true)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(tx("GÜNAYDIN · HAVA UYARISI", "GOOD MORNING · WEATHER"), color = Color(0xFF7FD6FF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(3.dp))
            Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
