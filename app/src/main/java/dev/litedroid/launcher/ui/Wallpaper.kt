package dev.litedroid.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import dev.litedroid.launcher.tx
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class Wallpaper(val name: String, val nameEn: String, val base: Color, val blobs: List<Color>) {
    val label: String get() = tx(name, nameEn)
}

object Wallpapers {
    val all = listOf(
        Wallpaper("Gece", "Night", Color(0xFF0B0B14), listOf(Color(0xFF4A35C8), Color(0xFF0E74B0), Color(0xFF9A2475))),
        Wallpaper("Ege", "Aegean", Color(0xFF051C25), listOf(Color(0xFF0B88AA), Color(0xFF1FBFA8), Color(0xFF1A4F8A))),
        Wallpaper("Gün batımı", "Sunset", Color(0xFF1C0B0B), listOf(Color(0xFFD0532E), Color(0xFF8A2260), Color(0xFFE89A30))),
        Wallpaper("Orman", "Forest", Color(0xFF06160D), listOf(Color(0xFF238A55), Color(0xFF125C44), Color(0xFF86A830))),
        Wallpaper("Neon", "Neon", Color(0xFF0A0614), listOf(Color(0xFFB0179E), Color(0xFF2C3BD6), Color(0xFF0FB5A6))),
        Wallpaper("Kum", "Sand", Color(0xFF1A140C), listOf(Color(0xFFC08A3E), Color(0xFF8A4B2A), Color(0xFFE0B870))),
        Wallpaper("Grafit", "Graphite", Color(0xFF101012), listOf(Color(0xFF4A4A58), Color(0xFF2E2E38), Color(0xFF5E5E6C))),
    )
}

/**
 * Duvar kâğıdını küçük bir bitmap'e (320x180) bir kez "pişirir". Ekrana büyütülerek tek bir
 * doku olarak çizilir: her karede renk lekelerini yeniden hesaplamak yerine GPU sadece tek bir
 * görüntü kopyalar. Bu, zayıf TV GPU'larında en büyük akıcılık kazancıdır.
 * [dim]: üstüne pişirilen koyulaştırma (Modern arayüz için), ayrı bir katman gerekmesin diye.
 */
fun bakeWallpaper(wallpaper: Wallpaper, accent: Color?, dim: Float): ImageBitmap {
    val w = 320
    val h = 180
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(wallpaper.base.toArgb())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = w * 0.75f
    wallpaper.blobs.forEachIndexed { i, base ->
        val mix = when {
            accent == null -> 0f
            i == 0 -> 0.7f
            i == 2 -> 0.45f
            else -> 0.2f
        }
        val c = if (accent == null) base else lerp(base, accent, mix)
        val a = i * 2.094f
        val cx = w * (0.5f + 0.38f * cos(a))
        val cy = h * (0.5f + 0.32f * sin(a * 1.3f + i))
        paint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(c.copy(alpha = 0.9f).toArgb(), c.copy(alpha = 0.3f).toArgb(), Color.Transparent.toArgb()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }
    if (dim > 0f) {
        paint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                Color(0xFF050814).copy(alpha = dim * 0.8f).toArgb(),
                Color(0xFF050814).copy(alpha = dim).toArgb(),
                Color(0xFF050814).copy(alpha = (dim * 1.4f).coerceAtMost(0.95f)).toArgb(),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }
    return bmp.asImageBitmap()
}

/**
 * Pişirilmiş duvar kâğıdı. Hareket açıksa tek dokuyu yavaşça kaydırır (ucuz GPU dönüşümü);
 * kapalıysa ekran hiç yeniden çizilmez. Tema değişimi hızlı geçişle yapılır.
 */
@Composable
fun AnimatedWallpaper(
    wallpaper: Wallpaper,
    accent: Color?,
    paused: () -> Boolean,
    modifier: Modifier = Modifier,
    dim: Float = 0f,
) {
    val bitmap = remember(wallpaper, accent?.toArgb(), dim) { bakeWallpaper(wallpaper, accent, dim) }
    // Hareket kapalıyken animasyon saati hiç çalışmaz: ekran ve işlemci tamamen dinlenir
    val moving = !paused()
    val phase: State<Float>? = if (moving) {
        rememberInfiniteTransition(label = "wallpaper").animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(durationMillis = 90_000, easing = LinearEasing)),
            label = "phase",
        )
    } else null
    val frozen = remember { floatArrayOf(0f) }

    FastCrossfade(targetState = bitmap, durationMillis = 500, modifier = modifier.fillMaxSize()) { bmp ->
        Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.Low,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = phase?.value?.also { frozen[0] = it } ?: frozen[0]
                    scaleX = 1.15f
                    scaleY = 1.15f
                    translationX = sin(p) * size.width * 0.05f
                    translationY = cos(p * 2f) * size.height * 0.04f
                },
        )
    }
}

/**
 * Ekran dışı tampon kullanmayan çapraz geçiş. Compose'un Crossfade'i saydamlığı ayrı bir
 * tamponda hesaplar; tam ekran görsellerde bu TV GPU'sunu çok yorar. Burada saydamlık doğrudan
 * çizime uygulanır (ModulateAlpha). Tek bir görsel ya da üst üste binmeyen içerik için uygundur.
 */
@Composable
fun <T> FastCrossfade(
    targetState: T,
    durationMillis: Int,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    var current by remember { mutableStateOf(targetState) }
    var previous by remember { mutableStateOf<T?>(null) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(targetState) {
        if (targetState != current) {
            previous = current
            current = targetState
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
            previous = null
        }
    }

    Box(modifier) {
        val prev = previous
        if (prev != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = 1f - progress.value
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
            ) { content(prev) }
        }
        Box(
            Modifier
                .graphicsLayer {
                    alpha = progress.value
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
        ) { content(current) }
    }
}
