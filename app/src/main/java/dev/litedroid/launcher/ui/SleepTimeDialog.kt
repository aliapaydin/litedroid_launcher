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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import java.time.LocalTime

/**
 * "Belirli saatte kapan" için saat seçici: yukarı/aşağı ile saat ve dakika (5'er) değişir,
 * sağ/sol ile alanlar arasında gezilir, "Kur" ile zamanlayıcı başlar. Geri kapatır.
 */
@Composable
fun SleepTimeDialog(onPick: (hour: Int, minute: Int) -> Unit, modifier: Modifier = Modifier) {
    // Varsayılan: bir saat sonrası, 5 dakikaya yuvarlanmış
    val start = remember {
        val t = LocalTime.now().plusMinutes(60)
        val m = (t.minute + 4) / 5 * 5
        if (m == 60) t.plusHours(1).withMinute(0) else t.withMinute(m)
    }
    var hour by remember { mutableIntStateOf(start.hour) }
    var minute by remember { mutableIntStateOf(start.minute) }
    val hourFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { hourFocus.requestFocus() } }

    Column(
        modifier
            .width(420.dp)
            .glass(RoundedCornerShape(28.dp), strong = true)
            .padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tx("Belirli saatte kapan", "Turn off at a time"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            tx("Yukarı/aşağı: değiştir · Sağ/sol: geç · Tamam: kur", "Up/down: change · Left/right: move · OK: set"),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spinner(hour, Modifier.focusRequester(hourFocus), onSubmit = { onPick(hour, minute) }) { delta -> hour = (hour + delta).mod(24) }
            Text(":", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(horizontal = 8.dp))
            Spinner(minute, Modifier, onSubmit = { onPick(hour, minute) }) { delta -> minute = (minute + delta * 5).mod(60) }
            Spacer(Modifier.width(16.dp))
            DialogButton(tx("Kur", "Set"), primary = true) { onPick(hour, minute) }
        }
    }
}

@Composable
private fun Spinner(value: Int, modifier: Modifier, onSubmit: () -> Unit, onChange: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("▲", color = Color.White.copy(alpha = if (focused) 0.8f else 0.25f), fontSize = 12.sp)
        Box(
            modifier
                .size(width = 96.dp, height = 76.dp)
                .clip(shape)
                .background(if (focused) Color.White else Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { e ->
                    if (e.key.isSelectKey) {
                        if (e.type == KeyEventType.KeyDown) armed = true
                        if (e.type == KeyEventType.KeyUp && armed) {
                            armed = false
                            onSubmit()
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionUp -> { onChange(1); true }
                        Key.DirectionDown -> { onChange(-1); true }
                        else -> false
                    }
                }
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            Text("%02d".format(value), color = if (focused) Color(0xFF111114) else Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        }
        Text("▼", color = Color.White.copy(alpha = if (focused) 0.8f else 0.25f), fontSize = 12.sp)
    }
}

@Composable
private fun DialogButton(label: String, primary: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    focused -> Color.White
                    primary -> Color(0xFF6B4DFF)
                    else -> Color.White.copy(alpha = 0.12f)
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onClick)
            .focusable()
            .padding(horizontal = 30.dp, vertical = 10.dp),
    ) {
        Text(label, color = if (focused) Color(0xFF111114) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
