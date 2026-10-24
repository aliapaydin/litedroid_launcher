package dev.litedroid.launcher.ui

import androidx.compose.animation.Crossfade
import dev.litedroid.launcher.tx
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.litedroid.launcher.data.ContentItem
import dev.litedroid.launcher.data.ContentState
import dev.litedroid.launcher.data.LauncherApp
import dev.litedroid.launcher.media.PlayedItem
import java.io.File

/** Vitrin görünümleri. Ayarlar'dan ya da Hızlı Ayarlar'dan değiştirilir. */
enum class ShelfStyle(private val tr: String, private val en: String) {
    CINEMATIC("Sinematik", "Cinematic"),
    CARD("Kart", "Card"),
    MINIMAL("Sade", "Minimal");

    val label: String get() = tx(tr, en)
}

private val DEFAULT_ACCENT = Color(0xFF7B5CFF)

/** Vitrinin o an anlattığı şey: bir uygulama ya da bir içerik. */
data class ShelfFocus(
    val key: String,
    val title: String,
    val subtitle: String,
    /** Başlığın üstündeki küçük renkli etiket. */
    val chip: String,
    val app: LauncherApp?,
    /** İçerik görseli: dosya yolu ya da URI. */
    val image: String?,
)

/** Vitrindeki küçük "devam et" şeridinin bir öğesi. */
sealed interface StripItem {
    val key: String

    data class Played(val item: PlayedItem, val app: LauncherApp?) : StripItem {
        override val key: String get() = "m_" + item.key
    }

    data class Content(val item: ContentItem, val app: LauncherApp?) : StripItem {
        override val key: String get() = "c_" + item.key
    }

    data class Recent(val app: LauncherApp) : StripItem {
        override val key: String get() = "r_" + app.packageName
    }
}

data class ContinueStrip(val title: String, val items: List<StripItem>)

private const val STRIP_MAX_ITEMS = 3

/** Coil için model: dosya yolu ise File, değilse olduğu gibi (http/content URI). */
fun artModel(path: String?): Any? = path?.let { if (it.startsWith("/")) File(it) else it }

fun shelfFocusFor(app: LauncherApp) = ShelfFocus(
    key = app.packageName,
    title = app.label,
    subtitle = tx("Açmak için seç  ·  Seçenekler için basılı tut", "Select to open  ·  Hold for options"),
    chip = tx("Uygulama", "App"),
    app = app,
    image = null,
)

fun shelfFocusFor(item: StripItem): ShelfFocus = when (item) {
    is StripItem.Recent -> shelfFocusFor(item.app).copy(key = item.key, chip = tx("Son kullanılan", "Recently used"))
    is StripItem.Played -> ShelfFocus(
        key = item.key,
        title = item.item.title,
        subtitle = listOfNotNull(item.app?.label, item.item.subtitle).joinToString("  ·  "),
        chip = tx("Kaldığın yerden", "Pick up where you left off"),
        app = item.app,
        image = item.item.art,
    )
    is StripItem.Content -> ShelfFocus(
        key = item.key,
        title = item.item.title,
        subtitle = listOfNotNull(item.app?.label, item.item.subtitle).joinToString("  ·  "),
        chip = if (item.item.isWatchNext) tx("Devam et", "Continue") else tx("Öneri", "Suggested"),
        app = item.app,
        image = item.item.imageUri,
    )
}

/**
 * Şeritte ne gösterileceğine karar verir:
 * 1. Odaktaki uygulamada son oynatılanlar ya da o uygulamanın yayınladığı içerikler
 * 2. Yoksa tüm uygulamalardan son oynatılanlar ("Kaldığın yerden devam et")
 * 3. Yoksa sistemin "İzlemeye devam et" listesi
 * 4. O da yoksa son kullanılan uygulamalar
 */
fun buildStrip(
    focusedApp: LauncherApp?,
    played: List<PlayedItem>,
    content: ContentState,
    recent: List<LauncherApp>,
    installed: List<LauncherApp>,
): ContinueStrip? {
    val byPkg = installed.associateBy { it.packageName }
    fun wrapContent(items: List<ContentItem>) =
        items.take(STRIP_MAX_ITEMS).map { StripItem.Content(it, byPkg[it.packageName]) }
    fun wrapPlayed(items: List<PlayedItem>) =
        items.take(STRIP_MAX_ITEMS).map { StripItem.Played(it, byPkg[it.packageName]) }

    if (focusedApp != null) {
        val pkg = focusedApp.packageName
        val ownPlayed = played.filter { it.packageName == pkg }
        if (ownPlayed.isNotEmpty()) return ContinueStrip(tx("${focusedApp.label} · kaldığın yerden", "${focusedApp.label} · where you left off"), wrapPlayed(ownPlayed))
        val ownWatch = content.watchNext.filter { it.packageName == pkg }
        val ownPreview = content.preview.filter { it.packageName == pkg }
        if (ownWatch.isNotEmpty()) return ContinueStrip(tx("${focusedApp.label} · devam et", "${focusedApp.label} · continue"), wrapContent(ownWatch + ownPreview))
        if (ownPreview.isNotEmpty()) return ContinueStrip(tx("${focusedApp.label} · öne çıkanlar", "${focusedApp.label} · highlights"), wrapContent(ownPreview))
    }
    if (played.isNotEmpty()) return ContinueStrip(tx("Kaldığın yerden devam et", "Continue where you left off"), wrapPlayed(played))
    if (content.watchNext.isNotEmpty()) return ContinueStrip(tx("İzlemeye devam et", "Continue watching"), wrapContent(content.watchNext))
    val recents = recent.filter { it.packageName != focusedApp?.packageName }.take(STRIP_MAX_ITEMS)
    if (recents.isNotEmpty()) return ContinueStrip(tx("Son kullanılanlar", "Recently used"), recents.map { StripItem.Recent(it) })
    return null
}

/**
 * Vitrin: tüm ekranı kaplayan hareketli, renkli fon + üst bölümde seçilen stile göre
 * başlık ve "devam et" şeridi. [fraction] ve [paused] lambda olarak verilir; animasyon
 * karelerinde sadece çizim katmanı güncellenir.
 */
@Composable
fun TopShelf(
    style: ShelfStyle,
    focus: ShelfFocus?,
    strip: ContinueStrip?,
    heroHeight: Dp,
    fraction: () -> Float,
    paused: () -> Boolean,
    stripEnabled: Boolean,
    userName: String,
    onStripFocus: (StripItem) -> Unit,
    onStripSelect: (StripItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotion.current
    val accent = focus?.app?.accent ?: DEFAULT_ACCENT
    val fade = motion.ms(450)

    Box(modifier.fillMaxSize()) {
        val backdropStrength: () -> Float =
            if (style == ShelfStyle.MINIMAL) ({ 0.5f * fraction() }) else fraction
        ShelfBackdrop(focus, backdropStrength, paused)

        // Renk: uygulamanın rengiyle sol üstten yayılan parıltı ve sağ alttan karşı ton
        FastCrossfade(targetState = accent, durationMillis = fade, modifier = Modifier.fillMaxSize()) { c ->
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 0.35f + 0.4f * fraction()
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(c.copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(0f, 0f),
                            radius = 1400f,
                        )
                    )
            )
        }

        // Okunabilirlik için hafif, renk tonlu karartma
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.Black.copy(alpha = 0.18f),
                        1f to Color.Black.copy(alpha = 0.68f),
                    )
                )
        )
        if (style == ShelfStyle.CINEMATIC) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to lerp(accent, Color.Black, 0.75f).copy(alpha = 0.6f),
                            0.55f to Color.Transparent,
                        )
                    )
            )
        }

        Box(
            Modifier
                .padding(top = TOP_BAR_HEIGHT)
                .fillMaxWidth()
                .height(heroHeight)
                .graphicsLayer {
                    val f = fraction()
                    alpha = f
                    translationY = -40.dp.toPx() * (1f - f)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .padding(horizontal = 64.dp, vertical = 14.dp)
        ) {
            val stripView: @Composable (Modifier) -> Unit = { m ->
                if (strip != null) {
                    ContinueStripView(strip, stripEnabled, onStripFocus, onStripSelect, m)
                }
            }
            when (style) {
                ShelfStyle.CINEMATIC -> {
                    FastCrossfade(
                        targetState = focus,
                        durationMillis = fade,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(0.5f),
                    ) { f -> if (f != null) TitleBlock(f, big = true) }
                    stripView(Modifier.align(Alignment.BottomEnd))
                }

                ShelfStyle.CARD -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    val shape = RoundedCornerShape(20.dp)
                    FastCrossfade(
                        targetState = focus,
                        durationMillis = fade,
                        modifier = Modifier
                            .fillMaxHeight(0.86f)
                            .aspectRatio(16f / 9f)
                            .softGlow(true, lerp(accent, Color.White, 0.3f), 20.dp)
                            .clip(shape),
                    ) { f -> if (f != null) FocusArt(f, Modifier.fillMaxSize()) }
                    Spacer(Modifier.width(28.dp))
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        FastCrossfade(targetState = focus, durationMillis = fade) { f ->
                            if (f != null) TitleBlock(f, big = false)
                        }
                        stripView(Modifier)
                    }
                }

                ShelfStyle.MINIMAL -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        val now = rememberNow()
                        Text(
                            text = "%02d:%02d".format(now.hour, now.minute),
                            color = Color.White,
                            fontSize = 76.sp,
                            fontWeight = FontWeight.Thin,
                            lineHeight = 76.sp,
                        )
                        Text(
                            text = "${greetingFor(now.hour)}, $userName",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 20.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        FastCrossfade(targetState = focus, durationMillis = fade) { f ->
                            if (f != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(accent)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = f.title,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    stripView(Modifier)
                }
            }
        }
    }
}

@Composable
private fun TitleBlock(focus: ShelfFocus, big: Boolean) {
    val accent = focus.app?.accent ?: DEFAULT_ACCENT
    Column {
        // Uygulamanın renginde küçük etiket
        Text(
            text = focus.chip,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(lerp(accent, Color.White, 0.15f), lerp(accent, Color.Black, 0.25f))))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = focus.title,
            color = Color.White,
            fontSize = if (big) 40.sp else 30.sp,
            lineHeight = if (big) 44.sp else 34.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        // Renkli ince çizgi
        Box(
            Modifier
                .width(56.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(accent, lerp(accent, Color.White, 0.5f))))
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = focus.subtitle,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Kart stilindeki büyük görsel: içerik kapağı ya da uygulama banner'ı. */
@Composable
private fun FocusArt(focus: ShelfFocus, modifier: Modifier) {
    Box(modifier.background(lerp(focus.app?.accent ?: DEFAULT_ACCENT, Color.Black, 0.3f))) {
        when {
            focus.image != null -> AsyncImage(
                model = artModel(focus.image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            focus.app != null -> AppArt(focus.app, Modifier.fillMaxSize())
        }
    }
}

private enum class BackdropMotion { ZOOM_IN, ZOOM_OUT, PAN_LEFT, PAN_RIGHT, DRIFT }

/**
 * Tüm ekranı kaplayan fon. Her uygulama/içerik kendine özgü (anahtarından seçilen) yavaş bir
 * kamera hareketiyle gelir: yakınlaşma, uzaklaşma, sağa/sola kayma ya da süzülme.
 * Görsel küçük çözünürlükte yüklenip büyütüldüğü için bulanık görünür ve ucuzdur.
 */
@Composable
private fun ShelfBackdrop(focus: ShelfFocus?, strength: () -> Float, paused: () -> Boolean) {
    val context = LocalContext.current
    val motion = LocalMotion.current
    FastCrossfade(targetState = focus, durationMillis = motion.ms(700), modifier = Modifier.fillMaxSize()) { f ->
        if (f == null) return@FastCrossfade
        val kind = remember(f.key) {
            BackdropMotion.entries[(f.key.hashCode() and 0x7fffffff) % BackdropMotion.entries.size]
        }
        // Hareket kapalıyken (varsayılan) animasyon saati hiç çalışmaz
        val progress: State<Float>? = if (!paused()) {
            rememberInfiniteTransition(label = "kenBurns").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse),
                label = "kb",
            )
        } else null
        val frozen = remember { floatArrayOf(0.5f) }
        val accent = f.app?.accent ?: DEFAULT_ACCENT

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress?.value?.also { frozen[0] = it } ?: frozen[0]
                    alpha = 0.4f + 0.4f * strength()
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    when (kind) {
                        BackdropMotion.ZOOM_IN -> {
                            scaleX = 1.05f + 0.15f * p; scaleY = scaleX
                        }
                        BackdropMotion.ZOOM_OUT -> {
                            scaleX = 1.2f - 0.15f * p; scaleY = scaleX
                        }
                        BackdropMotion.PAN_LEFT -> {
                            scaleX = 1.2f; scaleY = 1.2f
                            translationX = (0.5f - p) * size.width * 0.12f
                        }
                        BackdropMotion.PAN_RIGHT -> {
                            scaleX = 1.2f; scaleY = 1.2f
                            translationX = (p - 0.5f) * size.width * 0.12f
                        }
                        BackdropMotion.DRIFT -> {
                            scaleX = 1.15f; scaleY = 1.15f
                            translationY = (p - 0.5f) * size.height * 0.1f
                            rotationZ = (p - 0.5f) * 2f
                        }
                    }
                }
        ) {
            val backdrop = f.app?.backdrop
            when {
                f.image != null -> AsyncImage(
                    model = ImageRequest.Builder(context).data(artModel(f.image)).size(96, 54).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low,
                    modifier = Modifier.fillMaxSize(),
                )
                backdrop != null -> Image(
                    bitmap = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.9f), Color.Transparent)))
                )
            }
        }
    }
}

@Composable
private fun ContinueStripView(
    strip: ContinueStrip,
    enabled: Boolean,
    onFocus: (StripItem) -> Unit,
    onSelect: (StripItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = strip.title,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            strip.items.take(STRIP_MAX_ITEMS).forEach { item ->
                key(item.key) { StripCard(item, enabled, onFocus, onSelect) }
            }
        }
    }
}

@Composable
private fun StripCard(
    item: StripItem,
    enabled: Boolean,
    onFocus: (StripItem) -> Unit,
    onSelect: (StripItem) -> Unit,
) {
    val motion = LocalMotion.current
    val sounds = LocalSounds.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.1f else 1f,
        animationSpec = spring(dampingRatio = motion.tileDamping, stiffness = motion.tileStiffness),
        label = "stripScale",
    )
    val shape = RoundedCornerShape(10.dp)
    val (title, image, progress, app) = when (item) {
        is StripItem.Played -> StripData(item.item.subtitle ?: item.item.title, item.item.art, item.item.progress, item.app)
        is StripItem.Content -> StripData(item.item.subtitle ?: item.item.title, item.item.imageUri, item.item.progress, item.app)
        is StripItem.Recent -> StripData(item.app.label, null, null, item.app)
    }

    Column(
        Modifier
            .width(124.dp)
            .zIndex(if (focused) 1f else 0f)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .softGlow(focused, lerp(app?.accent ?: Color.White, Color.White, 0.35f), 10.dp)
                .clip(shape)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = Color.White.copy(alpha = if (focused) 1f else 0.15f),
                    shape = shape,
                )
                .onFocusChanged {
                    if (it.isFocused && !focused) sounds?.play(Sfx.NAV)
                    focused = it.isFocused
                    if (it.isFocused) onFocus(item)
                }
                .onRemoteSelect({ onSelect(item) })
                .focusable(enabled = enabled)
        ) {
            if (item is StripItem.Recent) {
                AppArt(item.app, Modifier.fillMaxSize())
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    lerp(app?.accent ?: DEFAULT_ACCENT, Color.White, 0.1f),
                                    lerp(app?.accent ?: DEFAULT_ACCENT, Color.Black, 0.4f),
                                )
                            )
                        )
                )
                if (image == null && app != null) {
                    Image(
                        app.icon,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxHeight(0.5f)
                            .aspectRatio(1f),
                    )
                }
                if (image != null) {
                    AsyncImage(
                        model = artModel(image),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (progress != null && progress > 0.01f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(Color.White)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            color = Color.White.copy(alpha = if (focused) 1f else 0.75f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class StripData(val title: String, val image: String?, val progress: Float?, val app: LauncherApp?)
