package dev.litedroid.launcher.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.onRemoteSelect
import kotlinx.coroutines.delay

/**
 * Uygulama kilidi ekranı: kilitli bir uygulama açılınca uygulamanın üstünü tamamen kaplar.
 * 4 haneli PIN kumandadaki rakam tuşlarıyla ya da ekrandaki tuş takımıyla girilir.
 * Doğruysa uygulama açılır, Geri ana ekrana döner.
 */
@Composable
fun AppLockScreen(appLabel: String, pin: String, onUnlock: () -> Unit) {
    var entered by remember { mutableStateOf("") }
    var wrong by remember { mutableIntStateOf(0) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(wrong) {
        if (wrong == 0) return@LaunchedEffect
        for (x in listOf(16f, -14f, 10f, -6f, 0f)) shake.animateTo(x, tween(45))
    }
    fun press(k: String) {
        when (k) {
            "⌫" -> entered = entered.dropLast(1)
            "C" -> entered = ""
            else -> if (entered.length < 4) {
                entered += k
                if (entered.length == 4) {
                    if (entered == pin) onUnlock() else {
                        wrong++
                        entered = ""
                    }
                }
            }
        }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { first.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E1024), Color(0xFF05060C))))
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val d = when (e.key) {
                    Key.Zero, Key.NumPad0 -> "0"; Key.One, Key.NumPad1 -> "1"; Key.Two, Key.NumPad2 -> "2"
                    Key.Three, Key.NumPad3 -> "3"; Key.Four, Key.NumPad4 -> "4"; Key.Five, Key.NumPad5 -> "5"
                    Key.Six, Key.NumPad6 -> "6"; Key.Seven, Key.NumPad7 -> "7"; Key.Eight, Key.NumPad8 -> "8"
                    Key.Nine, Key.NumPad9 -> "9"; Key.Backspace, Key.Delete -> "⌫"
                    else -> null
                }
                if (d != null) { press(d); true } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔒", fontSize = 44.sp)
            Text(tx("$appLabel kilitli", "$appLabel is locked"), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                if (wrong > 0) tx("PIN yanlış, tekrar dene", "Wrong PIN, try again") else tx("Açmak için PIN'i gir · Geri: ana ekran", "Enter the PIN to open · Back: home"),
                color = if (wrong > 0) Color(0xFFFFB4A8) else Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.offset(x = shake.value.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(4) { i ->
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(if (i < entered.length) Color.White else Color.White.copy(alpha = 0.15f))
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("C", "0", "⌫"))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { c, k ->
                            PinKey(k, if (r == 0 && c == 0) Modifier.focusRequester(first) else Modifier) { press(k) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(width = 76.dp, height = 46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (focused) Color(0xFF14131C) else Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}
