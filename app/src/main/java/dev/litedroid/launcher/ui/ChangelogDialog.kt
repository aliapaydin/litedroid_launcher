package dev.litedroid.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.data.Changelog
import dev.litedroid.launcher.tx

/**
 * Sürüm notları: solda sürümler (yukarı/aşağı ile gezilir), sağda odaktaki sürümün yenilikleri.
 * Geri tuşu pencereyi kapatır (HomeScreen'deki BackHandler).
 */
@Composable
fun ChangelogDialog(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val installed = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    val releases = remember { Changelog.releases }
    var selected by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Row(
        modifier
            .width(940.dp)
            .height(540.dp)
            .glass(RoundedCornerShape(30.dp), strong = true)
            // Odak pencereden kaçmasın (sağ/sol ve listenin uçları)
            .onPreviewKeyEvent {
                it.key == Key.DirectionLeft || it.key == Key.DirectionRight ||
                    (it.key == Key.DirectionUp && selected == 0) ||
                    (it.key == Key.DirectionDown && selected == releases.lastIndex)
            }
            .padding(22.dp),
    ) {
        // ---- Sol: sürüm listesi ----
        Column(Modifier.width(300.dp).fillMaxHeight()) {
            Text(
                tx("Sürüm notları", "Release notes"),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 8.dp),
            )
            Text(
                tx("Yukarı/aşağı: sürümler · Geri: kapat", "Up/down: versions · Back: close"),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 12.dp),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                itemsIndexed(releases, key = { _, r -> r.version }) { i, r ->
                    VersionRow(
                        version = r.version,
                        title = r.title,
                        current = r.version == installed,
                        selected = i == selected,
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        onFocused = { selected = i },
                    )
                }
            }
        }

        Spacer(Modifier.width(22.dp))
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.1f))
        )
        Spacer(Modifier.width(26.dp))

        // ---- Sağ: seçili sürümün yenilikleri ----
        AnimatedContent(
            targetState = selected,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            label = "release",
        ) { index ->
            val r = releases[index]
            Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "v${r.version}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF3A7BFF), Color(0xFF7B5CFF))))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    if (r.version == installed) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            tx("KURULU SÜRÜM", "INSTALLED"),
                            color = Color(0xFF7CF0A8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(r.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 27.sp)
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    r.items.forEach { item ->
                        Row {
                            Box(
                                Modifier
                                    .padding(top = 7.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF8E7BFF))
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(item, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionRow(
    version: String,
    title: String,
    current: Boolean,
    selected: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.16f)
                    selected -> Color.White.copy(alpha = 0.06f)
                    else -> Color.Transparent
                }
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            version,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.8f),
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.width(52.dp),
        )
        Text(
            title,
            color = Color.White.copy(alpha = if (focused) 0.85f else 0.55f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (current) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF7CF0A8))
            )
        }
    }
}
