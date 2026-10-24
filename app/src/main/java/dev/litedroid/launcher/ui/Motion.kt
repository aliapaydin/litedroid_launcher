package dev.litedroid.launcher.ui

import androidx.compose.runtime.staticCompositionLocalOf
import dev.litedroid.launcher.data.AnimSpeed

/**
 * Animasyon hızı ayarı. Karo yayları (bounce) ve tüm geçiş süreleri buradan okunur.
 * Hızlı: sert ve zıplamalı yay; Sakin: yumuşak ve uzun geçişler.
 */
data class Motion(
    val tileStiffness: Float,
    val tileDamping: Float,
    val durationScale: Float,
    val shelfDelayMs: Long,
) {
    fun ms(base: Int): Int = (base * durationScale).toInt().coerceAtLeast(60)
}

fun motionFor(speed: AnimSpeed): Motion = when (speed) {
    AnimSpeed.FAST -> Motion(tileStiffness = 900f, tileDamping = 0.72f, durationScale = 0.6f, shelfDelayMs = 260)
    AnimSpeed.NORMAL -> Motion(tileStiffness = 520f, tileDamping = 0.8f, durationScale = 1f, shelfDelayMs = 320)
    AnimSpeed.CALM -> Motion(tileStiffness = 300f, tileDamping = 0.9f, durationScale = 1.4f, shelfDelayMs = 380)
}

val LocalMotion = staticCompositionLocalOf { motionFor(AnimSpeed.FAST) }
