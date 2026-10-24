package dev.litedroid.launcher

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

enum class AppLanguage(val label: String, val tag: String) {
    TR("Türkçe", "tr"),
    EN("English", "en"),
}

/**
 * Basit iki dilli metin altyapısı. Dil ayarı değişince [LocalLanguage] da değişir ve
 * tüm arayüz yeni dille yeniden çizilir.
 */
object I18n {
    @Volatile
    var lang: AppLanguage = AppLanguage.TR

    /** Tarih/saat biçimleri için. */
    val locale: Locale
        get() = if (lang == AppLanguage.EN) Locale.ENGLISH else Locale.forLanguageTag("tr")
}

/** Seçili dile göre Türkçe ya da İngilizce metni döndürür. */
fun tx(tr: String, en: String): String = if (I18n.lang == AppLanguage.EN) en else tr

val LocalLanguage = staticCompositionLocalOf { AppLanguage.TR }
