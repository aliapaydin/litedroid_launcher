package dev.litedroid.launcher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.LauncherViewModel
import dev.litedroid.launcher.data.AppRelease
import dev.litedroid.launcher.data.UpdateState
import dev.litedroid.launcher.data.Updater
import dev.litedroid.launcher.tx

/** Bilinmeyen kaynaktan kurma izni (Android TV'de ayar ekranı yoksa bu komutla verilir). */
const val CMD_INSTALL = "adb shell appops set dev.litedroid.launcher REQUEST_INSTALL_PACKAGES allow"

/**
 * Güncelleme penceresi: yeni sürümün yenilikleri, indirme ilerlemesi ve "Şimdi güncelle".
 * Kurulumdan sonra LiteDroid kendiliğinden yeniden açılır. Geri kapatır.
 */
@Composable
fun UpdateDialog(vm: LauncherViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by Updater.state.collectAsState()
    val current = remember { Updater.currentVersion(context) }
    val primary = remember { FocusRequester() }
    // Düğme durumla birlikte değişir: odak her seferinde ana düğmeye dönsün
    LaunchedEffect(state.javaClass) {
        kotlinx.coroutines.delay(60)
        runCatching { primary.requestFocus() }
    }
    var permissionHint by remember { mutableStateOf(false) }

    val release: AppRelease? = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.Installing -> s.release
        is UpdateState.NeedsConfirm -> s.release
        is UpdateState.Failed -> s.release
        else -> null
    }

    fun update(r: AppRelease) {
        if (!Updater.canInstall(context)) {
            // İzin ekranını açmayı dene; Android TV'de yoksa komutu göster
            val opened = runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
            permissionHint = true
            if (!opened) android.util.Log.i("LiteDroidUpdate", "unknown sources settings not available")
            return
        }
        vm.installUpdate(r)
    }

    Column(
        modifier
            .width(820.dp)
            .glass(RoundedCornerShape(30.dp), strong = true)
            .padding(28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (release != null) "v${release.version}" else "v$current",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF3A7BFF), Color(0xFF7B5CFF))))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (release != null) tx("Yeni sürüm · şu an v$current", "New version · you have v$current") else tx("Güncellemeler", "Updates"),
                color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            release?.title ?: when (state) {
                UpdateState.Checking -> tx("Güncelleme denetleniyor…", "Checking for updates…")
                else -> tx("LiteDroid güncel", "LiteDroid is up to date")
            },
            color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 31.sp,
        )
        Spacer(Modifier.height(14.dp))
        if (release != null) {
            Column(Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                release.notes.take(8).forEach { n ->
                    Row {
                        Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(Color(0xFF8E7BFF)))
                        Spacer(Modifier.width(12.dp))
                        Text(n, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Durum satırı
        when (val s = state) {
            is UpdateState.Downloading -> {
                Text(tx("İndiriliyor… %${(s.progress * 100).toInt()}", "Downloading… ${(s.progress * 100).toInt()}%"), color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
                    Box(Modifier.fillMaxWidth(s.progress.coerceIn(0.02f, 1f)).height(6.dp).clip(RoundedCornerShape(50)).background(Color(0xFF8E7BFF)))
                }
                Spacer(Modifier.height(14.dp))
            }
            is UpdateState.Installing -> Status(tx("Kuruluyor… LiteDroid birazdan yeniden açılacak.", "Installing… LiteDroid will restart in a moment."))
            is UpdateState.NeedsConfirm -> Status(tx("Android bu ilk güncelleme için onay istiyor. Açılan ekranda \"Güncelle\"yi seç; sonraki güncellemeler onaysız kurulur.",
                "Android asks to confirm this first update. Choose \"Update\" on the screen that opens; later updates install without asking."))
            is UpdateState.Failed -> Status(s.message, error = true)
            else -> Unit
        }
        if (permissionHint && !Updater.canInstall(context)) {
            Status(
                tx("LiteDroid'in uygulama kurmasına izin ver: açılan ekranda izni aç ya da bilgisayardan bir kez şu komutu çalıştır:\n$CMD_INSTALL",
                    "Allow LiteDroid to install apps: turn it on in the screen that opened, or run this once from a computer:\n$CMD_INSTALL"),
                error = true,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (val s = state) {
                is UpdateState.Available -> DialogPill(tx("Şimdi güncelle", "Update now"), true, Modifier.focusRequester(primary)) { update(s.release) }
                is UpdateState.Failed -> s.release?.let { r -> DialogPill(tx("Tekrar dene", "Try again"), true, Modifier.focusRequester(primary)) { update(r) } }
                    ?: DialogPill(tx("Tekrar denetle", "Check again"), true, Modifier.focusRequester(primary)) { vm.checkForUpdateNow { } }
                is UpdateState.NeedsConfirm -> DialogPill(tx("Kurulumu onayla", "Confirm install"), true, Modifier.focusRequester(primary)) { Updater.confirmPending(context) }
                is UpdateState.Downloading, is UpdateState.Installing -> DialogPill(tx("Arka planda devam et", "Continue in background"), true, Modifier.focusRequester(primary)) { onClose() }
                else -> DialogPill(tx("Şimdi denetle", "Check now"), true, Modifier.focusRequester(primary)) { vm.checkForUpdateNow { } }
            }
            DialogPill(tx("Kapat", "Close"), false, Modifier) { onClose() }
        }
    }
}

@Composable
private fun Status(text: String, error: Boolean = false) {
    Text(
        text,
        color = if (error) Color(0xFFFFB4A8) else Color.White.copy(alpha = 0.75f),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier.padding(bottom = 14.dp),
    )
}

@Composable
private fun DialogPill(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    focused -> Color.White
                    primary -> Color(0xFF6B4DFF)
                    else -> Color.White.copy(alpha = 0.1f)
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onClick)
            .focusable()
            .padding(horizontal = 24.dp, vertical = 11.dp),
    ) {
        Text(label, color = if (focused) Color(0xFF111114) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
