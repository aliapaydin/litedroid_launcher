package dev.litedroid.launcher.data

import dev.litedroid.launcher.tx

/** Ayarlar → Diğer → Sürüm notları: LiteDroid'in önemli sürümleri, özetle. */
data class Release(val version: String, val title: String, val items: List<String>)

object Changelog {
    /**
     * En yeni sürüm en üstte. Ara sürümler (düzeltmeler, küçük iyileştirmeler) bir üstündeki
     * büyük sürümün altında toplanır. Dil değişince yeniden okunur (tx o anki dile göre döner).
     */
    val releases: List<Release>
        get() = listOf(
            Release(
                "2.4.1",
                tx("Input tuşu artık işe yarıyor", "The Input button now does something"),
                listOf(
                    tx("Kumandadaki Input tuşuna iş atanabiliyor: hızlı panel (varsayılan), son uygulamaya dön (ör. Netflix ↔ YouTube), yüzen menü, ekran görüntüsü ya da LiteDroid ana ekranı.", "The remote's Input button can be assigned: quick panel (default), switch to the last app, floating menu, screenshot or LiteDroid home."),
                    tx("Ayarlar → Kumanda → Input tuşu. Kapalı'da tuş sistemin işini yapar. Kumandayla ilgili ayarlar artık kendi kategorisinde.", "Settings → Remote → Input button. Off leaves it to the system. Remote settings now have their own category."),
                ),
            ),
            Release(
                "2.4.0",
                tx("Ev ve aile araçları", "Home and family tools"),
                listOf(
                    tx("📶 Misafir Wi-Fi: hızlı panelde QR; misafir telefonla okutup bağlanır, şifre yazdırmaya gerek yok.", "📶 Guest Wi-Fi: a QR in the quick panel; guests scan it to join, no typing."),
                    tx("📲 Telefondan TV'ye gönder: QR'ı okut, yazdığın yazı TV'de seçili kutuya gider (Netflix şifresi, YouTube araması), bağlantı tarayıcıda açılır.", "📲 Send from your phone: scan the QR; text goes into the selected box on the TV, links open in the browser."),
                    tx("☔ Sabah hava uyarısı: gün içinde yağmur ya da kar bekleniyorsa sabah ana ekranda kısa bir uyarı.", "☔ Morning weather alert: a short heads-up on the home screen if rain or snow is expected later."),
                    tx("☕ Mola hatırlatıcısı: bir uygulamada seçtiğin süre (30 dk - 2 sa) dolunca nazik bir hatırlatma.", "☕ Break reminder: a gentle nudge after the time you choose (30 min - 2 h) in one app."),
                    tx("🔒 Uygulama kilidi: seçtiğin uygulamalar açılınca 4 haneli PIN sorulur. Ana ekranda uygulamada Tamam'a basılı tut → Kilitle.", "🔒 App lock: chosen apps ask for a 4-digit PIN. Hold OK on an app on the home screen → Lock."),
                    tx("İyileştirmeler: Oyunlar sekmesi artık Ekstra (Oyunlar ve Araçlar rafları), hızlı panel sadeleşti, üst bardaki düğmesi aşağı ok oldu; ayarlarda kategoriler kayıyor, liste sonu esneyerek belli oluyor.", "Improvements: the Games tab is now Extras (Games and Tools shelves), the quick panel is simpler with a down-arrow button; settings categories scroll and list ends bounce."),
                    tx("Düzeltmeler: yazı pencereleri hep odağı alıyor; profil seçiliyken saat sıkışmıyor.", "Fixes: text windows always get the focus; the clock no longer squeezes next to the profile."),
                ),
            ),
            Release(
                "2.2.0",
                tx("Tarayıcı, Canlı Tablo ve Araçlar", "Browser, Living Painting and Tools"),
                listOf(
                    tx("🌐 Hafif tarayıcı: kumandayla fare oku, TV klavyesiyle form doldurma, kendi indiricisi (APK'lar tek tuşla kurulur). Kapatınca bellekte iz bırakmaz.", "🌐 A light browser: remote pointer, TV keyboard for forms, its own downloader (one-press APK install). Leaves nothing in memory when closed."),
                    tx("🎨 Canlı Tablo ekran koruyucusu: yapay zekâ şehirleri o anki gerçek saatlerine ve havalarına göre resmeder; benim şehrim ya da dünya turu, altı farklı stil.", "🎨 Living Painting screensaver: AI paints cities at their real local time and weather; my city or a world tour, six styles."),
                    tx("🛠 Araçlar: Hesap Makinesi, Hız Testi, Ekran Görüntüleri (QR ile telefona), Sistem Özellikleri ve Avatar Düzenleyici (avatar profil resmi olur).", "🛠 Tools: Calculator, Speed Test, Screenshots (to your phone via QR), System Info and Avatar Maker (your avatar becomes the profile picture)."),
                    tx("Düzeltme: gece yarısını geçince akşam maçları kaybolmuyor; maç günü sabah 06:00'da değişiyor.", "Fix: evening matches no longer vanish after midnight; the match day changes at 06:00."),
                ),
            ),
            Release(
                "2.0.0",
                tx("Masal Makinesi ve Gemini sesi", "Story Machine and Gemini voice"),
                listOf(
                    tx("🌙 Masal Makinesi: kahramanı, yeri ve konuyu seç; yapay zekâ masalı yazar, her sayfasını çizer ve anlatır. Ortada çocuk yolu seçer, karakterler kendi sesleriyle konuşur.", "🌙 Story Machine: pick a hero, a place and a theme; AI writes, illustrates and narrates the story. The child picks the path, characters have their own voices."),
                    tx("Film gibi akış, sayfaya göre değişen ninni müziği, bitince TV'nin kapanması ve ücretsiz yeniden dinlenen masal kitaplığı.", "Film-like pages, a mood-following lullaby, optional TV-off at the end and a library that replays for free."),
                    tx("🎙️ Claude'a sor cevapları Gemini'nin doğal sesleriyle okunuyor (Sulafat ve dokuz ses daha).", "🎙️ Ask Claude answers are read in Gemini's natural voices (Sulafat and nine more)."),
                    tx("Düzeltmeler: güncelleme pencereleri ekrana sığıyor, üst üste binmiyor ve kumanda arkaya kaçmıyor.", "Fixes: update windows fit the screen, never stack, and the remote no longer slips behind them."),
                ),
            ),
            Release(
                "1.9.0",
                tx("Kendi kendine güncelleme ve Mayın Tarlası", "Self-update and Minesweeper"),
                listOf(
                    tx("🔄 LiteDroid yeni sürümleri GitHub'dan kendisi indirip kuruyor: Otomatik kur, Sadece haber ver ya da Kapalı. Ertele (1 / 3 saat), tam ekran ya da köşede ilerleme, bitince \"Güncellendi\" ekranı.", "🔄 LiteDroid downloads and installs new versions from GitHub: Automatic, Notify only or Off. Snooze (1 / 3 h), full-screen or corner progress, an \"Updated\" screen when done."),
                    tx("💣 Mayın Tarlası: üç zorluk, ilk kare hep güvenli, en iyi süreler kaydediliyor.", "💣 Minesweeper: three levels, the first square is always safe, best times are saved."),
                    tx("Düzeltmeler: güncellemeden sonraki açılış hatası giderildi, indirme boyutu gösteriliyor.", "Fixes: a startup crash after updating, and the download size is shown."),
                ),
            ),
            Release(
                "1.7.0",
                tx("Bilgi Yarışması, sesli soru ve Gemini", "Quiz Night, voice questions and Gemini"),
                listOf(
                    tx("🧠 Bilgi Yarışması: telefonlar QR'ı okutup katılır, en hızlı doğru cevap kazanır; yüzlerce Türkçe soru.", "🧠 Quiz Night: phones scan the QR to join, the fastest right answer wins; hundreds of questions."),
                    tx("🎤 Sesli soru: konuşurken yazı canlı görünüyor, cevap ilk cümleden itibaren okunuyor.", "🎤 Voice questions: your words appear live, answers are read from the first sentence."),
                    tx("✨ Claude'un yanında Gemini desteği.", "✨ Gemini support alongside Claude."),
                    tx("🏆 Avrupa kupaları, takım takibi, uyku zamanlayıcısı ve sağdan kayan yüzen menü.", "🏆 European cups, team follow, the sleep timer and a sliding floating menu."),
                ),
            ),
            Release(
                "1.4.0",
                tx("LiteDroid'in temelleri", "LiteDroid's foundations"),
                listOf(
                    tx("Apple TV tarzı cam görünüm: Casual ve Modern arayüz, vitrin stilleri, kodla çizilmiş ekran koruyucu.", "Apple TV-style glass look: Casual and Modern layouts, showcase styles, a code-drawn screensaver."),
                    tx("Uygulamalar arası \"Kaldığın yerden devam\" ve her müzik uygulaması için \"Şimdi çalıyor\".", "\"Continue watching\" across apps and \"Now playing\" for every music app."),
                    tx("Claude'a sor, canlı skorlar, yüzen skor şeridi ve dahili oyunlar.", "Ask Claude, live scores, a floating score strip and built-in games."),
                    tx("Kullanım raporu ve Home tuşunu kilitleyen kutularda da LiteDroid'e yönlendirme.", "A usage report, and redirecting Home to LiteDroid on boxes that lock it."),
                    tx("Sayısız performans iyileştirmesi ve hata düzeltmesi (0.1 Camekan'dan 1.4'e).", "Countless performance improvements and fixes (from 0.1 Camekan to 1.4)."),
                ),
            ),
        )
}
