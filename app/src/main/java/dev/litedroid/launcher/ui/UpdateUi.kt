package dev.litedroid.launcher.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.R
import dev.litedroid.launcher.data.AppRelease
import dev.litedroid.launcher.data.Changelog
import dev.litedroid.launcher.data.UpdateState
import dev.litedroid.launcher.tx
import kotlinx.coroutines.delay

private val UAccent = Color(0xFF6B4DFF)
private val UBar = Color(0xFF8E7BFF)
private val UBg = Brush.verticalGradient(listOf(Color(0xFF1A1640), Color(0xFF0B0B1A), Color(0xFF05060C)))

/** Durumdan sürüm bilgisini çıkarır. */
fun UpdateState.releaseOrNull(): AppRelease? = when (this) {
    is UpdateState.Available -> release
    is UpdateState.Downloading -> release
    is UpdateState.Installing -> release
    is UpdateState.NeedsConfirm -> release
    is UpdateState.Failed -> release
    else -> null
}

/** Güncelleme sürüyor mu (ilerleme gösterilecek aşamalar). */
val UpdateState.inProgress: Boolean
    get() = this is UpdateState.Downloading || this is UpdateState.Installing || this is UpdateState.NeedsConfirm

// ---------------------------------------------------------------------------
// Otomatik mod: yeni sürüm sorusu
// ---------------------------------------------------------------------------

/**
 * "LiteDroid 1.8.1 hazır": yenilikler, Şimdi güncelle, 1 saat sonra, 3 saat sonra.
 * Geri tuşu bir saat erteler.
 */
@Composable
fun UpdatePrompt(
    release: AppRelease,
    current: String,
    onUpdate: () -> Unit,
    onSnooze: (hours: Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Kurma izni yok: açıklama ve tek seferlik komut gösterilir. */
    permissionHint: Boolean = false,
) {
    val primary = remember { FocusRequester() }
    // Açılınca ve izin ekranından dönünce odak "Şimdi güncelle"de olsun
    LaunchedEffect(release.version, permissionHint) {
        delay(120)
        runCatching { primary.requestFocus() }
    }
    Column(
        modifier
            // Chromecast ekranı ~540 dp yüksek: pencere taşmasın, düğmeler hep görünsün
            .padding(vertical = 20.dp)
            .width(780.dp)
            .glass(RoundedCornerShape(30.dp), strong = true)
            .padding(30.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_launcher), null, Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(tx("Yeni sürüm hazır", "A new version is ready"), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("LiteDroid ${release.version}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            }
            VersionChip("v$current → v${release.version}")
        }
        Spacer(Modifier.height(16.dp))
        Text(release.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Notes(release.notes.take(5), Modifier.weight(1f, fill = false))
        Spacer(Modifier.height(22.dp))
        if (permissionHint) {
            Text(
                tx("LiteDroid'in uygulama kurmasına izin ver: açılan ekranda LiteDroid'i aç (Ayarlar → Uygulamalar → Güvenlik ve kısıtlamalar → Bilinmeyen kaynaklar), sonra yeniden \"Şimdi güncelle\" de. O ekran yoksa bilgisayardan bir kez:\n$CMD_INSTALL",
                    "Allow LiteDroid to install apps: turn LiteDroid on in the screen that opened (Settings → Apps → Security & restrictions → Unknown sources), then choose \"Update now\" again. If there's no such screen, run once from a computer:\n$CMD_INSTALL"),
                color = Color(0xFFFFB4A8), fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            UPill(tx("Şimdi güncelle", "Update now"), primary = true, modifier = Modifier.focusRequester(primary), onClick = onUpdate)
            UPill(tx("1 saat sonra", "In 1 hour"), primary = false, onClick = { onSnooze(1) })
            UPill(tx("3 saat sonra", "In 3 hours"), primary = false, onClick = { onSnooze(3) })
        }
        Spacer(Modifier.height(12.dp))
        val size = if (release.apkSize > 0) "%.1f MB".format(release.apkSize / 1_048_576f) else null
        Text(
            listOfNotNull(
                size?.let { tx("İndirilecek: $it", "Download: $it") },
                tx("bir iki dakika sürer, bitince LiteDroid kendiliğinden yeniden açılır", "takes a minute or two, LiteDroid restarts by itself when done"),
            ).joinToString(" · "),
            color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// İlerleme: tam ekran
// ---------------------------------------------------------------------------

/**
 * Tam ekran güncelleme: büyük ilerleme çubuğu ve aşamalar (İndiriliyor → Kuruluyor → Yeniden başlatılıyor).
 * "Arka planda devam et" (ya da Geri) sağ alttaki küçük karta küçültür.
 */
@Composable
fun UpdateProgressScreen(state: UpdateState, onMinimize: () -> Unit, onConfirm: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val release = state.releaseOrNull()
    val primary = remember { FocusRequester() }
    LaunchedEffect(state.javaClass) {
        delay(80)
        runCatching { primary.requestFocus() }
    }
    val progress = when (state) {
        is UpdateState.Downloading -> state.progress
        is UpdateState.Installing, is UpdateState.NeedsConfirm -> 1f
        else -> 0f
    }
    val shown by animateFloatAsState(progress, tween(400), label = "up")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0.92f, 1.04f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "p")

    Box(modifier.fillMaxSize().background(UBg), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 900.dp).padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.ic_launcher), null,
                Modifier.size(96.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }.clip(RoundedCornerShape(26.dp)),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                tx("LiteDroid ${release?.version ?: ""} sürümüne güncelleniyor", "Updating LiteDroid to ${release?.version ?: ""}"),
                color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (state) {
                    is UpdateState.Downloading -> tx("İndiriliyor · %${(state.progress * 100).toInt()}", "Downloading · ${(state.progress * 100).toInt()}%")
                    is UpdateState.Installing -> tx("Kuruluyor… birazdan yeniden başlayacak", "Installing… restarting in a moment")
                    is UpdateState.NeedsConfirm -> tx("Android onay istiyor: açılan ekranda \"Güncelle\"yi seç", "Android asks to confirm: choose \"Update\"")
                    is UpdateState.Failed -> state.message
                    else -> tx("Hazırlanıyor…", "Preparing…")
                },
                color = if (state is UpdateState.Failed) Color(0xFFFFB4A8) else Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Box(Modifier.width(640.dp).height(12.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
                Box(
                    Modifier.fillMaxWidth(shown.coerceIn(0.02f, 1f)).height(12.dp).clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF3A7BFF), UBar)))
                )
            }
            Spacer(Modifier.height(18.dp))
            // Aşamalar
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                val step = when (state) {
                    is UpdateState.Downloading -> 0
                    is UpdateState.Installing, is UpdateState.NeedsConfirm -> 1
                    else -> -1
                }
                listOf(tx("İndir", "Download"), tx("Kur", "Install"), tx("Yeniden başlat", "Restart")).forEachIndexed { i, label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(
                                when {
                                    i < step -> Color(0xFF3BE07F)
                                    i == step -> UAccent
                                    else -> Color.White.copy(alpha = 0.12f)
                                }
                            ),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (i < step) "✓" else "${i + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = Color.White.copy(alpha = if (i <= step) 0.9f else 0.45f), fontSize = 14.sp)
                    }
                }
            }
            if (release != null && release.notes.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Column(
                    Modifier
                        .widthIn(max = 720.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(18.dp)
                ) {
                    Text(tx("Bu sürümde", "In this version"), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Notes(release.notes.take(4))
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    is UpdateState.NeedsConfirm -> UPill(tx("Kurulumu onayla", "Confirm install"), true, Modifier.focusRequester(primary), onConfirm)
                    is UpdateState.Failed -> UPill(tx("Tekrar dene", "Try again"), true, Modifier.focusRequester(primary), onRetry)
                    else -> UPill(tx("Arka planda devam et", "Continue in background"), true, Modifier.focusRequester(primary), onMinimize)
                }
                if (state is UpdateState.NeedsConfirm || state is UpdateState.Failed) {
                    UPill(tx("Kapat", "Close"), false, Modifier, onMinimize)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// İlerleme ya da bildirim: sağ alt köşe
// ---------------------------------------------------------------------------

/** Sağ altta küçük kart: indirme ilerlemesi ya da "yeni sürüm var" bildirimi. Odak almaz. */
@Composable
fun UpdateCornerCard(title: String, text: String, progress: Float?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .padding(end = 28.dp, bottom = 28.dp)
            .widthIn(max = 440.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1C33).copy(alpha = 0.97f), Color(0xFF0D0E1A).copy(alpha = 0.97f))))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(Color(0xFF3A7BFF), UAccent))),
            contentAlignment = Alignment.Center,
        ) { Text("⬆", color = Color.White, fontSize = 20.sp) }
        Spacer(Modifier.width(13.dp))
        Column {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(text, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 16.sp)
            if (progress != null) {
                Spacer(Modifier.height(7.dp))
                Box(Modifier.width(280.dp).height(5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0.02f, 1f)).height(5.dp).clip(RoundedCornerShape(50)).background(UBar))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Güncellemeden sonra: "şu sürüme güncellendi"
// ---------------------------------------------------------------------------

/** Kurulumdan sonraki ilk açılış: "LiteDroid 1.8.1'e güncellendi" ve bu sürümün yenilikleri. */
@Composable
fun UpdatedScreen(version: String, onClose: () -> Unit, onAllNotes: () -> Unit, modifier: Modifier = Modifier) {
    val release = remember(version) { Changelog.releases.firstOrNull { it.version == version } }
    val ok = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { ok.requestFocus() }
    }
    val pop by rememberInfiniteTransition(label = "pop").animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "s")
    Box(modifier.fillMaxSize().background(UBg), contentAlignment = Alignment.Center) {
        // Ekran yüksekliğine sığar: yenilikler kutusu daralır, düğmeler her zaman ekranda kalır
        Column(Modifier.widthIn(max = 900.dp).padding(horizontal = 40.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Image(painterResource(R.drawable.ic_launcher), null, Modifier.size(76.dp).graphicsLayer { scaleX = pop; scaleY = pop }.clip(RoundedCornerShape(26.dp)))
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF3BE07F)).border(3.dp, Color(0xFF0B0B1A), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("✓", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
            }
            Spacer(Modifier.height(12.dp))
            Text(tx("Güncellendi!", "Updated!"), color = Color(0xFF7CF0A8), fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(
                tx("LiteDroid $version sürümüne güncellendi", "LiteDroid was updated to $version"),
                color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
            )
            if (release != null) {
                Spacer(Modifier.height(6.dp))
                Text(release.title, color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = 760.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(22.dp)
                ) {
                    Text(tx("YENİLİKLER", "WHAT'S NEW"), color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(10.dp))
                    Notes(release.items.take(6))
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                UPill(tx("Harika, devam", "Great, continue"), true, Modifier.focusRequester(ok), onClose)
                UPill(tx("Tüm sürüm notları", "All release notes"), false, Modifier, onAllNotes)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Ortak parçalar
// ---------------------------------------------------------------------------

@Composable
private fun Notes(items: List<String>, modifier: Modifier = Modifier) {
    // Yer kalmazsa kesilmek yerine kaydırılabilir kutuda kalır
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        items.forEach { n ->
            Row {
                Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(UBar))
                Spacer(Modifier.width(12.dp))
                Text(n, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun VersionChip(text: String) {
    Text(
        text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Color(0xFF3A7BFF), Color(0xFF7B5CFF))))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun UPill(label: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    focused -> Color.White
                    primary -> UAccent
                    else -> Color(0xFF3A3658)
                }
            )
            .border(1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onClick)
            .focusable()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(label, color = if (focused) Color(0xFF111114) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
