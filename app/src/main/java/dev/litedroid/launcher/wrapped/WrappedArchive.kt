package dev.litedroid.launcher.wrapped

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.onRemoteSelect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Ayarlar → Diğer → LiteDroid Wrapped: geçmiş haftaların listesi. Her hafta açılıp yeniden izlenebilir;
 * en üstte bu haftanın şimdiye kadarki önizlemesi var.
 */
@Composable
fun WrappedArchiveScreen(onOpen: (WeekData) -> Unit, modifier: Modifier = Modifier, active: Boolean = true) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val weeks by produceState<List<WeekRef>?>(null) { value = withContext(Dispatchers.IO) { WrappedEngine.weeks(ctx) } }
    val unseen by WrappedEngine.unseen.collectAsState()
    var loading by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    // Gösteri kapanınca odak listeye geri gelir
    LaunchedEffect(active) {
        if (active) {
            kotlinx.coroutines.delay(120)
            runCatching { first.requestFocus() }
        }
    }

    val now = LocalDateTime.now()
    val thisMonday = WrappedEngine.mondayOf(now.toLocalDate())
    val nextAt = thisMonday.plusDays(7)

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A0E3A), Color(0xFF0A0815), Color(0xFF05050B))))
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 30.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Column {
                    Text(
                        "LiteDroid Wrapped",
                        style = TextStyle(brush = Brush.linearGradient(listOf(Color(0xFFFFD35C), Color(0xFFFF6FB5), Color(0xFF8EC5FF))), fontSize = 32.sp, fontWeight = FontWeight.Black),
                    )
                    Text(
                        tx("Her pazartesi 09:00'da geçen haftanın özeti hazırlanır ve çan menüsüne gelir. Sıradaki: ${nextAt.dayOfMonth}.${"%02d".format(nextAt.monthValue)} Pazartesi.",
                            "Every Monday at 09:00 last week's recap is prepared and lands in the bell menu. Next: Monday ${nextAt.dayOfMonth}/${nextAt.monthValue}."),
                        color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            item(key = "preview") {
                ArchiveRow(
                    emoji = "✨",
                    title = tx("Bu hafta şimdiye kadar", "This week so far"),
                    subtitle = if (loading == "preview") tx("Hazırlanıyor…", "Preparing…") else tx("Haftanın ortasında bir göz at (önizleme)", "Take a peek mid-week (preview)"),
                    badge = null,
                    modifier = Modifier.focusRequester(first),
                ) {
                    if (loading != null) return@ArchiveRow
                    loading = "preview"
                    scope.launch {
                        val w = runCatching { WrappedEngine.previewThisWeek(ctx) }.getOrNull()
                        loading = null
                        if (w != null) onOpen(w)
                    }
                }
            }
            val list = weeks
            if (list == null) {
                item(key = "loading") { Text(tx("Yükleniyor…", "Loading…"), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp) }
            } else if (list.isEmpty()) {
                item(key = "empty") {
                    Text(
                        tx("Henüz Wrapped yok. LiteDroid her günü kaydediyor; ilk özet pazartesi sabahı gelecek.",
                            "No Wrapped yet. LiteDroid is recording each day; the first recap arrives Monday morning."),
                        color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                item(key = "past") {
                    Text(tx("GEÇMİŞ HAFTALAR", "PAST WEEKS"), color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(top = 10.dp))
                }
                items(list, key = { it.start.toString() }) { r ->
                    ArchiveRow(
                        emoji = r.persona.emoji,
                        title = rangeOf(r.start) + (if (r.start == thisMonday.minusDays(7)) tx(" · geçen hafta", " · last week") else ""),
                        subtitle = listOfNotNull(if (r.totalMs > 0) dur(r.totalMs) else null, r.persona.title, r.topApp).joinToString(" · "),
                        badge = if (unseen == r.start) tx("YENİ", "NEW") else null,
                    ) {
                        if (loading != null) return@ArchiveRow
                        loading = r.start.toString()
                        scope.launch {
                            val w = withContext(Dispatchers.IO) { WrappedEngine.week(ctx, r.start) }
                            loading = null
                            if (w != null) onOpen(w)
                        }
                    }
                }
            }
        }
    }
}

private fun rangeOf(start: LocalDate): String {
    val end = start.plusDays(6)
    val tr = listOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")
    val en = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    fun m(d: LocalDate) = tx(tr[d.monthValue - 1], en[d.monthValue - 1])
    return if (start.month == end.month) "${start.dayOfMonth} - ${end.dayOfMonth} ${m(end)} ${end.year}"
    else "${start.dayOfMonth} ${m(start)} - ${end.dayOfMonth} ${m(end)} ${end.year}"
}

@Composable
private fun ArchiveRow(emoji: String, title: String, subtitle: String, badge: String?, modifier: Modifier = Modifier, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) Color.White else Color.White.copy(alpha = 0.07f), label = "rowBg")
    val ink = if (focused) Color(0xFF14121F) else Color.White
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = if (focused) 0f else 0.08f), RoundedCornerShape(20.dp))
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onSelect)
            .focusable()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF5B2EFF), Color(0xFFFF3D8B)))), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 24.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = ink.copy(alpha = 0.65f), fontSize = 12.sp)
        }
        if (badge != null) WrappedGiftBadge(badge)
    }
}

/** Yeni Wrapped rozeti: parıldayan altın-pembe hap. */
@Composable
fun WrappedGiftBadge(text: String) {
    val shift by rememberInfiniteTransition(label = "badge").animateFloat(0f, 1f, infiniteRepeatable(tween(2_000, easing = LinearEasing)), label = "s")
    Text(
        "🎁 $text", color = Color(0xFF1A1030), fontSize = 11.sp, fontWeight = FontWeight.Black,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.linearGradient(listOf(Color(0xFFFFD35C), Color(0xFFFF6FB5), Color(0xFFFFD35C)), start = androidx.compose.ui.geometry.Offset(shift * 200f - 100f, 0f), end = androidx.compose.ui.geometry.Offset(shift * 200f + 100f, 40f)))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
