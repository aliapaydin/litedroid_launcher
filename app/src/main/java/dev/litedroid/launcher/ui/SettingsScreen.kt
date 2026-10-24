package dev.litedroid.launcher.ui

import dev.litedroid.launcher.ui.theme.Jakarta
import androidx.compose.animation.animateColorAsState
import dev.litedroid.launcher.tx
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.R
import kotlinx.coroutines.delay

/** Ayarlar sayfasındaki bir satır ya da bölüm başlığı. */
sealed interface SettingsEntry {
    data class Header(val title: String) : SettingsEntry

    data class Row(
        val key: String,
        val title: String,
        val icon: ImageVector,
        val value: String? = null,
        /** Sol paneldeki açıklama (odaklanınca gösterilir). */
        val hint: String? = null,
        val onSelect: () -> Unit,
    ) : SettingsEntry
}

/**
 * Tam ekran ayarlar. Solda logo ve odaktaki satırın açıklaması, sağda kaydırılabilir liste.
 * Değerli satırlar seçildikçe bir sonraki seçeneğe geçer (Hızlı → Normal → Sakin gibi).
 */
@Composable
fun SettingsScreen(entries: List<SettingsEntry>, enabled: Boolean, modifier: Modifier = Modifier) {
    val firstRow = remember { FocusRequester() }
    var hint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { firstRow.requestFocus() }
    }
    val firstKey = entries.filterIsInstance<SettingsEntry.Row>().firstOrNull()?.key

    Row(
        modifier
            .fillMaxSize()
            .padding(start = 64.dp, end = 64.dp, top = 36.dp)
    ) {
        Column(Modifier.width(250.dp)) {
            Image(painterResource(R.drawable.logo_mark), contentDescription = null, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(10.dp))
            Text(tx("Ayarlar", "Settings"), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(
                tx("LiteDroid'i kendine göre ayarla", "Make LiteDroid your own"),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(24.dp))
            if (hint != null) {
                Text(
                    text = hint ?: "",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(18.dp))
                        .padding(14.dp),
                )
            }
        }
        Spacer(Modifier.width(36.dp))
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 48.dp, start = 8.dp, end = 8.dp),
        ) {
            entries.forEach { entry ->
                when (entry) {
                    is SettingsEntry.Header -> item(key = "h_" + entry.title) {
                        Text(
                            text = entry.title,
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp, start = 4.dp),
                        )
                    }
                    is SettingsEntry.Row -> item(key = entry.key) {
                        SettingRowView(
                            row = entry,
                            enabled = enabled,
                            focusRequester = if (entry.key == firstKey) firstRow else null,
                            onFocus = { hint = entry.hint },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRowView(
    row: SettingsEntry.Row,
    enabled: Boolean,
    focusRequester: FocusRequester?,
    onFocus: () -> Unit,
) {
    val motion = LocalMotion.current
    val sounds = LocalSounds.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.03f else 1f,
        spring(dampingRatio = motion.tileDamping, stiffness = motion.tileStiffness),
        label = "rowScale",
    )
    val bg by animateColorAsState(
        if (focused) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.07f),
        label = "rowBg",
    )
    val fg = if (focused) Color(0xFF111114) else Color.White
    val shape = RoundedCornerShape(16.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = if (focused) 0f else 0.1f), shape)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                if (it.isFocused && !focused) {
                    sounds?.play(Sfx.NAV)
                    onFocus()
                }
                focused = it.isFocused
            }
            .onRemoteSelect(row.onSelect)
            .focusable(enabled = enabled)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(row.icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = row.title,
            color = fg,
            fontSize = 15.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (row.value != null) {
            Text(
                text = row.value,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (focused) Color(0xFF6B4DFF) else Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

/** Metin giriş penceresi (kullanıcı adı, şehir). Metin alanına odaklanınca TV klavyesi açılır. */
@Composable
fun TextInputDialog(
    title: String,
    subtitle: String,
    initial: String,
    allowEmpty: Boolean,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(initial) }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { fieldFocus.requestFocus() }
        keyboard?.show()
    }

    Column(
        modifier
            .width(440.dp)
            .glass(RoundedCornerShape(30.dp), strong = true)
            .padding(28.dp),
    ) {
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(18.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(20) },
            singleLine = true,
            textStyle = TextStyle(fontFamily = Jakarta, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                keyboard?.hide()
                onSave(if (allowEmpty) text.trim() else text.trim().ifEmpty { initial })
            }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fieldFocus)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(tx("Kaydet", "Save"), primary = true) {
                keyboard?.hide()
                onSave(if (allowEmpty) text.trim() else text.trim().ifEmpty { initial })
            }
            PillButton(tx("Vazgeç", "Cancel"), primary = false) {
                keyboard?.hide()
                onCancel()
            }
        }
    }
}

@Composable
private fun PillButton(label: String, primary: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val motion = LocalMotion.current
    val scale by animateFloatAsState(
        if (focused) 1.08f else 1f,
        spring(dampingRatio = motion.tileDamping, stiffness = motion.tileStiffness),
        label = "pillScale",
    )
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
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
            .padding(horizontal = 26.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = if (focused) Color(0xFF111114) else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
