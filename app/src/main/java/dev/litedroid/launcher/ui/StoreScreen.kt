package dev.litedroid.launcher.ui

import androidx.compose.ui.graphics.lerp
import dev.litedroid.launcher.I18n
import androidx.compose.animation.animateColorAsState
import dev.litedroid.launcher.tx
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.data.LauncherApp
import dev.litedroid.launcher.data.StoreApp
import dev.litedroid.launcher.data.StoreCatalog
import kotlinx.coroutines.delay

private val SECTION_COLORS = listOf(
    Color(0xFF7A3B9C), Color(0xFF1E7A52), Color(0xFFB0402E), Color(0xFF2E5FA0),
)

/**
 * Tam ekran mağaza. Yüklü uygulamada seçim uygulamayı açar,
 * yüklü değilse Google Play'deki sayfasını açar (kurulum ve otomatik güncelleme Play'de).
 */
@Composable
fun StoreScreen(
    installed: List<LauncherApp>,
    onLaunch: (LauncherApp) -> Unit,
    onGetFromPlay: (StoreApp) -> Unit,
    onOpenPlayStore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstCard = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { firstCard.requestFocus() }
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(start = 64.dp, end = 64.dp, top = 36.dp)
    ) {
        Text(tx("Mağaza", "Store"), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            text = tx("Kurulum ve otomatik güncellemeler Google Play üzerinden yapılır", "Installs and automatic updates go through Google Play"),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(18.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 56.dp, start = 6.dp, end = 6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            StoreCatalog.sections.forEachIndexed { sectionIndex, section ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_${section.title}") {
                    SectionTitle(section.title)
                }
                items(section.apps, key = { "a_${it.name}" }) { app ->
                    val inst = StoreCatalog.findInstalled(app, installed)
                    StoreCard(
                        title = app.name,
                        subtitle = app.description,
                        tag = app.tag,
                        installed = inst != null,
                        icon = inst?.icon,
                        color = SECTION_COLORS[sectionIndex % SECTION_COLORS.size],
                        focusRequester = if (sectionIndex == 0 && app == section.apps.first()) firstCard else null,
                        onSelect = { if (inst != null) onLaunch(inst) else onGetFromPlay(app) },
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "h_other") { SectionTitle(tx("Diğer", "More")) }
            item(key = "play") {
                StoreCard(
                    title = "Google Play",
                    subtitle = tx("Güncellemeler ve tüm uygulamalar", "Updates and all apps"),
                    installed = false,
                    actionLabel = tx("Aç", "Open"),
                    icon = null,
                    color = Color(0xFF3A3A48),
                    focusRequester = null,
                    onSelect = onOpenPlayStore,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.85f),
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun StoreCard(
    title: String,
    subtitle: String,
    installed: Boolean,
    icon: ImageBitmap?,
    color: Color,
    focusRequester: FocusRequester?,
    onSelect: () -> Unit,
    actionLabel: String = if (installed) tx("Aç", "Open") else tx("Yükle", "Install"),
    tag: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "cardScale")
    val background by animateColorAsState(
        if (focused) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.08f),
        label = "cardBg",
    )
    val textColor = if (focused) Color(0xFF111114) else Color.White
    val shape = RoundedCornerShape(18.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(background)
            .border(1.dp, Color.White.copy(alpha = if (focused) 0f else 0.12f), shape)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onSelect)
            .focusable()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Text(initials(title), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (tag != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        tag.uppercase(I18n.locale),
                        color = if (focused) Color.White else Color(0xFF111114),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (focused) color else lerp(color, Color.White, 0.55f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                subtitle, color = textColor.copy(alpha = 0.6f), fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = actionLabel,
            color = if (focused) Color.White else Color(0xFF111114),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    when {
                        focused -> Color(0xFF111114)
                        installed -> Color(0xFF8EE3B5)
                        else -> Color.White.copy(alpha = 0.9f)
                    }
                )
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

private fun initials(name: String): String =
    name.split(' ', '+').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
        .ifEmpty { name.take(1) }
