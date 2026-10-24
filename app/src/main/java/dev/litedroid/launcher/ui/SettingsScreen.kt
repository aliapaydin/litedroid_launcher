package dev.litedroid.launcher.ui

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.focusProperties
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

/** Ayarlar bir başlık altında toplanan satırlar: her başlık bir sekme olur. */
private data class SettingsSection(val title: String, val rows: List<SettingsEntry.Row>)

private fun sectionsOf(entries: List<SettingsEntry>): List<SettingsSection> {
    val out = ArrayList<SettingsSection>()
    var title: String? = null
    var rows = ArrayList<SettingsEntry.Row>()
    entries.forEach { e ->
        when (e) {
            is SettingsEntry.Header -> {
                if (rows.isNotEmpty()) out += SettingsSection(title ?: "", rows)
                title = e.title
                rows = ArrayList()
            }
            is SettingsEntry.Row -> rows += e
        }
    }
    if (rows.isNotEmpty()) out += SettingsSection(title ?: "", rows)
    return out
}

/**
 * Tam ekran ayarlar, sekmeli: solda kategoriler (odaklanınca açılır), sağda o kategorinin satırları,
 * altta odaktaki satırın açıklaması. Değerli satırlar seçildikçe bir sonraki seçeneğe geçer.
 */
@Composable
fun SettingsScreen(
    entries: List<SettingsEntry>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    /** Üstte açılan bir pencere (ör. sürüm notları) kapanınca artırılır: odak seçili sekmeye döner. */
    refocusSignal: Int = 0,
) {
    val sections = remember(entries) { sectionsOf(entries) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val current = sections.getOrNull(selected.coerceIn(0, (sections.size - 1).coerceAtLeast(0)))
    val tabRequesters = remember(sections.size) { List(sections.size) { FocusRequester() } }
    val firstRow = remember { FocusRequester() }
    var hint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { tabRequesters.getOrNull(selected)?.requestFocus() }
    }
    LaunchedEffect(refocusSignal) {
        if (refocusSignal == 0) return@LaunchedEffect
        delay(60)
        runCatching { tabRequesters.getOrNull(selected)?.requestFocus() }
    }

    Row(
        modifier
            .fillMaxSize()
            .padding(start = 56.dp, end = 56.dp, top = 30.dp, bottom = 24.dp)
    ) {
        // ---- Sol: başlık ve sekmeler ----
        Column(Modifier.width(250.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo_mark), contentDescription = null, modifier = Modifier.size(38.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(tx("Ayarlar", "Settings"), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp)
                    Text(tx("LiteDroid'i kendine göre ayarla", "Make LiteDroid your own"), color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sections.forEachIndexed { i, section ->
                    SectionTab(
                        title = section.title,
                        icon = section.rows.first().icon,
                        count = section.rows.size,
                        selected = i == selected,
                        enabled = enabled,
                        focusRequester = tabRequesters[i],
                        rightTarget = firstRow,
                        onFocus = {
                            selected = i
                            hint = null
                        },
                    )
                }
            }
        }
        Spacer(Modifier.width(30.dp))

        // ---- Sağ: seçili kategorinin satırları + açıklama ----
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Text(
                current?.title.orEmpty(),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp, start = 8.dp, end = 8.dp),
            ) {
                val rows = current?.rows.orEmpty()
                items(rows.size, key = { rows[it].key }) { i ->
                    val row = rows[i]
                    SettingRowView(
                        row = row,
                        enabled = enabled,
                        focusRequester = if (i == 0) firstRow else null,
                        leftTarget = tabRequesters.getOrNull(selected),
                        onFocus = { hint = row.hint },
                    )
                }
            }
            val h = hint
            if (h != null) {
                Text(
                    text = h,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .glass(RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Sol kenardaki kategori sekmesi. Odaklanınca o kategori sağda açılır. */
@Composable
private fun SectionTab(
    title: String,
    icon: ImageVector,
    count: Int,
    selected: Boolean,
    enabled: Boolean,
    focusRequester: FocusRequester,
    rightTarget: FocusRequester,
    onFocus: () -> Unit,
) {
    val sounds = LocalSounds.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val fg = when {
        focused -> Color(0xFF111114)
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.7f)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.94f)
                    selected -> Color(0xFF6B4DFF).copy(alpha = 0.55f)
                    else -> Color.Transparent
                }
            )
            .focusRequester(focusRequester)
            .focusProperties { right = rightTarget }
            .onFocusChanged {
                if (it.isFocused && !focused) {
                    sounds?.play(Sfx.NAV)
                    onFocus()
                }
                focused = it.isFocused
            }
            .onRemoteSelect({ runCatching { rightTarget.requestFocus() } })
            .focusable(enabled = enabled)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            color = fg,
            fontSize = 14.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("$count", color = fg.copy(alpha = 0.5f), fontSize = 11.sp)
    }
}

@Composable
private fun SettingRowView(
    row: SettingsEntry.Row,
    enabled: Boolean,
    focusRequester: FocusRequester?,
    leftTarget: FocusRequester?,
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
            // Sola basınca her zaman seçili sekmeye dön (başka sekmeye atlamasın)
            .then(if (leftTarget != null) Modifier.focusProperties { left = leftTarget } else Modifier)
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
    maxLength: Int = 20,
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
            onValueChange = { text = it.take(maxLength) },
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
