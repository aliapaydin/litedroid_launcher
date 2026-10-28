package dev.litedroid.launcher.tools

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.litedroid.launcher.I18n
import dev.litedroid.launcher.tx
import dev.litedroid.launcher.ui.AVATAR_BRUSH
import dev.litedroid.launcher.ui.isSelectKey
import dev.litedroid.launcher.ui.onRemoteSelect
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Avatar: parçalar ve çizim
// ---------------------------------------------------------------------------

/** Bir avatarın tüm parçaları (her biri listelerdeki sıra numarası). */
data class AvatarSpec(
    val id: String,
    val skin: Int = 1,
    val hair: Int = 1,
    val hairColor: Int = 1,
    val eyes: Int = 0,
    val mouth: Int = 0,
    val accessory: Int = 0,
    val blush: Boolean = true,
    val bg: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("skin", skin).put("hair", hair).put("hc", hairColor)
        .put("eyes", eyes).put("mouth", mouth).put("acc", accessory).put("blush", blush).put("bg", bg)

    companion object {
        fun from(o: JSONObject) = AvatarSpec(
            o.getString("id"), o.optInt("skin"), o.optInt("hair"), o.optInt("hc"), o.optInt("eyes"),
            o.optInt("mouth"), o.optInt("acc"), o.optBoolean("blush", true), o.optInt("bg"),
        )

        fun random(id: String = "a" + System.currentTimeMillis()): AvatarSpec {
            val r = java.util.Random()
            return AvatarSpec(id, r.nextInt(SKINS.size), r.nextInt(HAIRS.size), r.nextInt(HAIR_COLORS.size), r.nextInt(EYES.size),
                r.nextInt(MOUTHS.size), if (r.nextBoolean()) 0 else r.nextInt(ACCESSORIES.size), r.nextBoolean(), r.nextInt(BACKGROUNDS.size))
        }
    }
}

private class Part(val tr: String, val en: String) {
    val label get() = tx(tr, en)
}

val SKINS = listOf(Color(0xFFFFE0C7), Color(0xFFF6C7A0), Color(0xFFE0A47A), Color(0xFFC07F55), Color(0xFF8D5A3B), Color(0xFF5E3B26))
private val SKIN_NAMES = listOf(Part("Çok açık", "Very light"), Part("Açık", "Light"), Part("Buğday", "Wheat"), Part("Esmer", "Tan"), Part("Koyu", "Dark"), Part("Çok koyu", "Very dark"))
private val HAIRS = listOf(Part("Yok", "None"), Part("Kısa", "Short"), Part("Uzun", "Long"), Part("Kıvırcık", "Curly"), Part("Topuz", "Bun"), Part("Diken diken", "Spiky"), Part("At kuyruğu", "Ponytail"), Part("Küt", "Bob"))
val HAIR_COLORS = listOf(Color(0xFF2B1D16), Color(0xFF5A3825), Color(0xFF9A5B2E), Color(0xFFE2B45A), Color(0xFFC0392B), Color(0xFFB8B8C0), Color(0xFF7B5CFF), Color(0xFFFF6FB5))
private val HAIR_COLOR_NAMES = listOf(Part("Siyah", "Black"), Part("Kahverengi", "Brown"), Part("Kumral", "Auburn"), Part("Sarı", "Blonde"), Part("Kızıl", "Red"), Part("Gri", "Grey"), Part("Mor", "Purple"), Part("Pembe", "Pink"))
private val EYES = listOf(Part("Nokta", "Dots"), Part("Kocaman", "Big"), Part("Mutlu", "Happy"), Part("Göz kırpan", "Wink"), Part("Uykulu", "Sleepy"), Part("Yıldız", "Stars"))
private val MOUTHS = listOf(Part("Gülümseme", "Smile"), Part("Kahkaha", "Grin"), Part("Şaşkın", "Surprised"), Part("Dil çıkaran", "Tongue"), Part("Ciddi", "Neutral"), Part("Kedi", "Cat"))
private val ACCESSORIES = listOf(Part("Yok", "None"), Part("Gözlük", "Glasses"), Part("Güneş gözlüğü", "Sunglasses"), Part("Taç", "Crown"), Part("Bere", "Beanie"), Part("Parti şapkası", "Party hat"), Part("Kulaklık", "Headphones"), Part("Fiyonk", "Bow"))
private val BACKGROUNDS = listOf(
    listOf(Color(0xFF8B5CFF), Color(0xFFFF4D9D)), listOf(Color(0xFF3A7BFF), Color(0xFF2ED3C8)), listOf(Color(0xFFFFB13D), Color(0xFFFF5C6E)),
    listOf(Color(0xFF3BE07F), Color(0xFF0B6B3E)), listOf(Color(0xFF1A1240), Color(0xFF4D3BAE)), listOf(Color(0xFFFFD27A), Color(0xFFFF9A3D)),
    listOf(Color(0xFF7FD6FF), Color(0xFFB7A6FF)), listOf(Color(0xFF2B2B33), Color(0xFF55556A)),
)
private val BG_NAMES = listOf(Part("Gün batımı", "Sunset"), Part("Okyanus", "Ocean"), Part("Mercan", "Coral"), Part("Orman", "Forest"), Part("Gece", "Night"), Part("Güneş", "Sun"), Part("Gökyüzü", "Sky"), Part("Duman", "Smoke"))

/** Avatarı çizer (kare alan, yuvarlak kırpma çağırana ait). */
@Composable
fun AvatarImage(spec: AvatarSpec, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawAvatar(spec) }
}

private fun DrawScope.drawAvatar(a: AvatarSpec) {
    val u = size.minDimension / 100f
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    fun sz(w: Float, h: Float) = Size(w * u, h * u)
    val skin = SKINS[a.skin.mod(SKINS.size)]
    val hairC = HAIR_COLORS[a.hairColor.mod(HAIR_COLORS.size)]
    val bg = BACKGROUNDS[a.bg.mod(BACKGROUNDS.size)]
    val ink = Color(0xFF2A1B14)
    val shade = Color.Black.copy(alpha = 0.12f)

    drawRect(Brush.linearGradient(bg, o(0f, 0f), o(100f, 100f)))
    val hair = a.hair.mod(HAIRS.size)
    // Arkadaki saç
    when (hair) {
        2 -> drawRoundRect(hairC, o(20f, 26f), sz(60f, 66f), CornerRadius(18 * u))
        6 -> {
            drawOval(hairC, o(70f, 40f), sz(18f, 40f))
            drawCircle(hairC.copy(alpha = 0.9f), 5 * u, o(76f, 40f))
        }
        7 -> drawRoundRect(hairC, o(20f, 26f), sz(60f, 46f), CornerRadius(16 * u))
    }
    // Gövde
    drawRoundRect(Brush.verticalGradient(listOf(bg.last().copy(alpha = 1f), Color.Black.copy(alpha = 0.5f)), o(0f, 80f).y, o(0f, 110f).y), o(18f, 82f), sz(64f, 40f), CornerRadius(30 * u))
    drawRoundRect(skin, o(43f, 72f), sz(14f, 14f), CornerRadius(4 * u))
    // Kulaklar ve baş
    drawCircle(skin, 5.5f * u, o(24f, 53f))
    drawCircle(skin, 5.5f * u, o(76f, 53f))
    drawOval(skin, o(24f, 22f), sz(52f, 58f))
    drawOval(shade, o(26f, 60f), sz(48f, 20f), alpha = 0.35f)
    // Öndeki saç
    when (hair) {
        1, 2, 6, 7 -> {
            drawArc(hairC, 180f, 180f, true, o(22f, 18f), sz(56f, 42f))
            drawOval(hairC, o(30f, 30f), sz(26f, 12f))
        }
        3 -> for (i in 0..10) {
            val ang = Math.toRadians((170 + i * 20).toDouble())
            drawCircle(hairC, 9.5f * u, o(50f + (cos(ang) * 27).toFloat(), 46f + (sin(ang) * 26).toFloat()))
        }
        4 -> {
            drawArc(hairC, 180f, 180f, true, o(23f, 19f), sz(54f, 38f))
            drawCircle(hairC, 10 * u, o(50f, 14f))
        }
        5 -> {
            val p = Path().apply {
                moveTo(22 * u, 42 * u)
                for (i in 0..6) {
                    lineTo((25 + i * 8.5f) * u, (8 + (i % 2) * 6) * u)
                    lineTo((29 + i * 8.5f) * u, 30 * u)
                }
                lineTo(78 * u, 42 * u)
                lineTo(70 * u, 26 * u)
                lineTo(30 * u, 26 * u)
                close()
            }
            drawPath(p, hairC)
        }
    }
    // Gözler
    val ly = 52f
    when (a.eyes.mod(EYES.size)) {
        0 -> { drawCircle(ink, 3.2f * u, o(40f, ly)); drawCircle(ink, 3.2f * u, o(60f, ly)) }
        1 -> for (x in listOf(40f, 60f)) {
            drawCircle(Color.White, 6f * u, o(x, ly))
            drawCircle(ink, 3.6f * u, o(x + 0.6f, ly + 0.6f))
            drawCircle(Color.White, 1.3f * u, o(x + 1.8f, ly - 1.2f))
        }
        2 -> for (x in listOf(40f, 60f)) drawArc(ink, 200f, 140f, false, o(x - 5f, ly - 3f), sz(10f, 8f), style = Stroke(2.4f * u, cap = StrokeCap.Round))
        3 -> {
            drawCircle(ink, 3.2f * u, o(40f, ly))
            drawArc(ink, 200f, 140f, false, o(55f, ly - 3f), sz(10f, 8f), style = Stroke(2.4f * u, cap = StrokeCap.Round))
        }
        4 -> for (x in listOf(40f, 60f)) drawArc(ink, 20f, 140f, false, o(x - 5f, ly - 5f), sz(10f, 7f), style = Stroke(2.4f * u, cap = StrokeCap.Round))
        else -> for (x in listOf(40f, 60f)) drawPath(star(o(x, ly), 5.5f * u), Color(0xFFFFC83D))
    }
    // Yanaklar
    if (a.blush) {
        drawCircle(Color(0xFFFF7A8A).copy(alpha = 0.45f), 4.5f * u, o(33f, 61f))
        drawCircle(Color(0xFFFF7A8A).copy(alpha = 0.45f), 4.5f * u, o(67f, 61f))
    }
    // Ağız
    val my = 65f
    when (a.mouth.mod(MOUTHS.size)) {
        0 -> drawArc(ink, 20f, 140f, false, o(42f, my - 6f), sz(16f, 10f), style = Stroke(2.4f * u, cap = StrokeCap.Round))
        1 -> {
            drawArc(Color(0xFF8E1F2C), 0f, 180f, true, o(41f, my - 6f), sz(18f, 14f))
            drawRect(Color.White, o(43f, my + 0.2f), sz(14f, 2.6f))
        }
        2 -> drawOval(Color(0xFF8E1F2C), o(46f, my - 2f), sz(8f, 9f))
        3 -> {
            drawArc(ink, 20f, 140f, false, o(42f, my - 6f), sz(16f, 10f), style = Stroke(2.4f * u, cap = StrokeCap.Round))
            drawArc(Color(0xFFFF6F8F), 0f, 180f, true, o(49f, my + 1f), sz(7f, 8f))
        }
        4 -> drawLine(ink, o(44f, my + 1f), o(56f, my + 1f), 2.4f * u, cap = StrokeCap.Round)
        else -> {
            drawArc(ink, 0f, 180f, false, o(43f, my - 2f), sz(7f, 6f), style = Stroke(2.2f * u, cap = StrokeCap.Round))
            drawArc(ink, 0f, 180f, false, o(50f, my - 2f), sz(7f, 6f), style = Stroke(2.2f * u, cap = StrokeCap.Round))
        }
    }
    // Aksesuar
    when (a.accessory.mod(ACCESSORIES.size)) {
        1 -> {
            for (x in listOf(40f, 60f)) drawCircle(Color(0xFF1C1C24), 7.5f * u, o(x, ly), style = Stroke(2.2f * u))
            drawLine(Color(0xFF1C1C24), o(47.5f, ly), o(52.5f, ly), 2.2f * u)
        }
        2 -> {
            for (x in listOf(40f, 60f)) drawRoundRect(Color(0xFF15151C), o(x - 8f, ly - 5f), sz(16f, 10f), CornerRadius(4 * u))
            drawLine(Color(0xFF15151C), o(47f, ly - 2f), o(53f, ly - 2f), 2.4f * u)
            drawLine(Color.White.copy(alpha = 0.5f), o(34f, ly - 2.5f), o(38f, ly - 2.5f), 1.2f * u)
        }
        3 -> {
            val p = Path().apply {
                moveTo(34 * u, 24 * u); lineTo(34 * u, 10 * u); lineTo(42 * u, 17 * u); lineTo(50 * u, 7 * u)
                lineTo(58 * u, 17 * u); lineTo(66 * u, 10 * u); lineTo(66 * u, 24 * u); close()
            }
            drawPath(p, Color(0xFFFFC83D))
            drawCircle(Color(0xFFFF4D6D), 2.2f * u, o(50f, 17f))
        }
        4 -> {
            drawArc(Color(0xFFE0525C), 180f, 180f, true, o(23f, 14f), sz(54f, 34f))
            drawRoundRect(Color(0xFFB83A44), o(23f, 28f), sz(54f, 7f), CornerRadius(3 * u))
            drawCircle(Color.White, 5 * u, o(50f, 13f))
        }
        5 -> {
            val p = Path().apply { moveTo(38 * u, 26 * u); lineTo(56 * u, 2 * u); lineTo(64 * u, 28 * u); close() }
            drawPath(p, Brush.linearGradient(listOf(Color(0xFF3BA7FF), Color(0xFFFF7AB6)), o(38f, 26f), o(64f, 2f)))
            drawCircle(Color(0xFFFFC83D), 3.5f * u, o(56f, 3f))
        }
        6 -> {
            drawArc(Color(0xFF22222C), 180f, 180f, false, o(20f, 18f), sz(60f, 60f), style = Stroke(4 * u))
            drawRoundRect(Color(0xFF3A3A48), o(16f, 44f), sz(10f, 18f), CornerRadius(4 * u))
            drawRoundRect(Color(0xFF3A3A48), o(74f, 44f), sz(10f, 18f), CornerRadius(4 * u))
        }
        7 -> {
            val c = o(66f, 24f)
            val l = Path().apply { moveTo(c.x, c.y); lineTo(c.x - 10 * u, c.y - 7 * u); lineTo(c.x - 10 * u, c.y + 7 * u); close() }
            val r = Path().apply { moveTo(c.x, c.y); lineTo(c.x + 10 * u, c.y - 7 * u); lineTo(c.x + 10 * u, c.y + 7 * u); close() }
            drawPath(l, Color(0xFFFF4D9D))
            drawPath(r, Color(0xFFFF4D9D))
            drawCircle(Color(0xFFD12A78), 3 * u, c)
        }
    }
}

private fun star(c: Offset, r: Float) = Path().apply {
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.45f
        val a = Math.toRadians((-90 + i * 36).toDouble())
        val x = c.x + (cos(a) * rr).toFloat()
        val y = c.y + (sin(a) * rr).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

// ---------------------------------------------------------------------------
// Saklama ve profil
// ---------------------------------------------------------------------------

/** Kaydedilen avatarlar ve profil resmi olarak seçilen. Değişiklikler ekranda hemen görünür. */
object AvatarStore {
    var avatars by mutableStateOf<List<AvatarSpec>>(emptyList())
        private set
    var selectedId by mutableStateOf<String?>(null)
        private set
    private var loaded = false

    val selected: AvatarSpec? get() = avatars.firstOrNull { it.id == selectedId }

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(ctx)
        avatars = runCatching {
            val arr = JSONArray(p.getString("list", "[]"))
            (0 until arr.length()).map { AvatarSpec.from(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        selectedId = p.getString("selected", null)
    }

    /** Profil değişince o profilin avatarları yüklenir. */
    fun reload(ctx: Context) {
        loaded = false
        load(ctx)
    }

    fun save(ctx: Context, spec: AvatarSpec) {
        avatars = if (avatars.any { it.id == spec.id }) avatars.map { if (it.id == spec.id) spec else it } else avatars + spec
        persist(ctx)
        if (spec.id == selectedId) dev.litedroid.launcher.data.Profiles.setAvatar(ctx, spec)
    }

    fun delete(ctx: Context, id: String) {
        avatars = avatars.filter { it.id != id }
        if (selectedId == id) {
            selectedId = null
            dev.litedroid.launcher.data.Profiles.setAvatar(ctx, null)
        }
        persist(ctx)
    }

    fun select(ctx: Context, id: String?) {
        selectedId = id
        persist(ctx)
        // Profil resmi "Kim İzliyor?" ekranında da görünsün
        dev.litedroid.launcher.data.Profiles.setAvatar(ctx, avatars.firstOrNull { it.id == id })
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(dev.litedroid.launcher.data.Profiles.scoped("litedroid_avatars"), Context.MODE_PRIVATE)

    private fun persist(ctx: Context) {
        val arr = JSONArray().apply { avatars.forEach { put(it.toJson()) } }
        prefs(ctx).edit().putString("list", arr.toString()).putString("selected", selectedId).apply()
    }
}

/** Üst bardaki profil yuvarlağı: seçili avatar varsa o, yoksa adın baş harfi. */
@Composable
fun ProfileAvatar(name: String, size: Dp, border: Dp, initialSize: Int) {
    val context = LocalContext.current
    remember { AvatarStore.load(context.applicationContext) }
    val list by dev.litedroid.launcher.data.Profiles.list.collectAsState()
    val activeId by dev.litedroid.launcher.data.Profiles.active.collectAsState()
    val profile = list.firstOrNull { it.id == activeId }
    if (profile != null) {
        ProfileFace(profile, size, border)
        return
    }
    val spec = AvatarStore.selected
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(AVATAR_BRUSH)
            .border(border, Color.White.copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (spec != null) AvatarImage(spec, Modifier.fillMaxSize())
        else Text(name.take(1).uppercase(I18n.locale), color = Color.White, fontSize = initialSize.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Bir profilin yüzü: avatarı varsa avatar, yoksa renkli zemin üstünde emojisi. */
@Composable
fun ProfileFace(profile: dev.litedroid.launcher.data.Profile, size: Dp, border: Dp = 0.dp) {
    val c = Color(profile.color)
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (profile.avatar != null) AVATAR_BRUSH else Brush.linearGradient(listOf(c, c.copy(alpha = 0.55f))))
            .border(border, Color.White.copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val spec = profile.avatar
        if (spec != null) AvatarImage(spec, Modifier.fillMaxSize())
        else Text(profile.emoji, fontSize = (size.value * 0.5f).sp)
    }
}

// ---------------------------------------------------------------------------
// Düzenleyici
// ---------------------------------------------------------------------------

@Composable
fun AvatarEditorTool(accent: Color) {
    val context = LocalContext.current
    remember { AvatarStore.load(context.applicationContext) }
    var spec by remember { mutableStateOf(AvatarStore.selected ?: AvatarStore.avatars.lastOrNull() ?: AvatarSpec.random()) }
    var message by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { first.requestFocus() }
    }
    val saved = AvatarStore.avatars.any { it.id == spec.id }
    val isProfile = AvatarStore.selectedId == spec.id

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        // Önizleme ve kayıtlı avatarlar
        Column(Modifier.width(300.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(200.dp).clip(CircleShape).border(4.dp, if (isProfile) Color(0xFFFFC83D) else Color.White.copy(alpha = 0.5f), CircleShape),
            ) { AvatarImage(spec, Modifier.fillMaxSize()) }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    isProfile -> "⭐ " + tx("Profil resmin", "Your profile picture")
                    saved -> tx("Kaydedildi", "Saved")
                    else -> tx("Kaydedilmedi", "Not saved")
                },
                color = if (isProfile) Color(0xFFFFC83D) else Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
            )
            message?.let { Text(it, color = accent, fontSize = 12.sp) }
            Spacer(Modifier.height(10.dp))
            if (AvatarStore.avatars.isNotEmpty()) {
                Text(tx("KAYDEDİLENLER · basılı tut: sil", "SAVED · hold: delete"), color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(AvatarStore.avatars, key = { _, a -> a.id }) { _, a ->
                        SavedAvatar(a, a.id == spec.id, a.id == AvatarStore.selectedId, onPick = { spec = a; message = null }) {
                            AvatarStore.delete(context, a.id)
                            message = tx("Silindi", "Deleted")
                        }
                    }
                }
            }
        }
        // Parçalar
        Column(Modifier.weight(1f)) {
            PartPicker(tx("Ten", "Skin"), SKIN_NAMES[spec.skin.mod(SKINS.size)].label, Modifier.focusRequester(first)) { spec = spec.copy(skin = (spec.skin + it).mod(SKINS.size)) }
            PartPicker(tx("Saç", "Hair"), HAIRS[spec.hair.mod(HAIRS.size)].label) { spec = spec.copy(hair = (spec.hair + it).mod(HAIRS.size)) }
            PartPicker(tx("Saç rengi", "Hair color"), HAIR_COLOR_NAMES[spec.hairColor.mod(HAIR_COLORS.size)].label) { spec = spec.copy(hairColor = (spec.hairColor + it).mod(HAIR_COLORS.size)) }
            PartPicker(tx("Gözler", "Eyes"), EYES[spec.eyes.mod(EYES.size)].label) { spec = spec.copy(eyes = (spec.eyes + it).mod(EYES.size)) }
            PartPicker(tx("Ağız", "Mouth"), MOUTHS[spec.mouth.mod(MOUTHS.size)].label) { spec = spec.copy(mouth = (spec.mouth + it).mod(MOUTHS.size)) }
            PartPicker(tx("Aksesuar", "Accessory"), ACCESSORIES[spec.accessory.mod(ACCESSORIES.size)].label) { spec = spec.copy(accessory = (spec.accessory + it).mod(ACCESSORIES.size)) }
            PartPicker(tx("Yanaklar", "Cheeks"), if (spec.blush) tx("Pembe", "Rosy") else tx("Sade", "Plain")) { spec = spec.copy(blush = !spec.blush) }
            PartPicker(tx("Arka plan", "Background"), BG_NAMES[spec.bg.mod(BACKGROUNDS.size)].label) { spec = spec.copy(bg = (spec.bg + it).mod(BACKGROUNDS.size)) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolButton("💾 " + tx("Kaydet", "Save"), accent) {
                    AvatarStore.save(context, spec)
                    message = tx("Kaydedildi", "Saved")
                }
                ToolButton("⭐ " + tx("Profil resmi yap", "Use as profile"), Color(0xFFE0A800)) {
                    AvatarStore.save(context, spec)
                    AvatarStore.select(context, spec.id)
                    message = tx("Üst bardaki profil resmin oldu", "Now your profile picture in the top bar")
                }
                ToolButton("🎲", Color(0xFF3A3658)) { spec = AvatarSpec.random(spec.id) }
                ToolButton("＋ " + tx("Yeni", "New"), Color(0xFF3A3658)) {
                    spec = AvatarSpec.random()
                    message = null
                }
            }
            if (AvatarStore.selectedId != null) {
                Spacer(Modifier.height(6.dp))
                Text(tx("Baş harfe dönmek için: profil avatarını basılı tutarak sil.", "To go back to your initial: hold the profile avatar to delete it."), color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SavedAvatar(a: AvatarSpec, current: Boolean, profile: Boolean, onPick: () -> Unit, onDelete: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .border(3.dp, when { focused -> Color.White; profile -> Color(0xFFFFC83D); current -> Color.White.copy(alpha = 0.5f); else -> Color.Transparent }, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .onRemoteSelect(onPick, onDelete)
            .focusable(),
    ) { AvatarImage(a, Modifier.fillMaxSize().padding(3.dp).clip(CircleShape)) }
}

@Composable
private fun PartPicker(label: String, value: String, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .padding(vertical = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.06f))
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent e.key == Key.DirectionLeft || e.key == Key.DirectionRight || e.key.isSelectKey
                when {
                    e.key == Key.DirectionLeft -> { onChange(-1); true }
                    e.key == Key.DirectionRight || e.key.isSelectKey -> { onChange(1); true }
                    else -> false
                }
            }
            .focusable()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (focused) Color(0xFF14131C) else Color.White
        Text(label, color = fg.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.width(110.dp))
        Text(if (focused) "◀" else "", color = fg.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.width(16.dp))
        Text(value, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (focused) "▶" else "", color = fg.copy(alpha = 0.5f), fontSize = 11.sp)
    }
}
