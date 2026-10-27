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
                "2.7.0",
                tx("LiteDroid Wrapped", "LiteDroid Wrapped"),
                listOf(
                    tx("🎁 Her pazartesi 09:00'da geçen haftanın Wrapped'i hazır: kendiliğinden açılmaz, çan menüsüne gelir. Yeni olduğunda çanın etrafında renkli bir halka döner.", "🎁 Every Monday at 09:00 last week's Wrapped is ready: it lands in the bell menu instead of popping up, with a spinning colorful ring around the bell."),
                    tx("Tam ekran gösteri: TV başında geçen süre, en çok izlenenler, haftanın uygulaması, saat saat ısı haritası, izleyici tipin, rekorlar, oyunlar ve masallar, Canlı Tablo resimleri, takımlarının maçları ve paylaşmalık özet kartı.", "A full-screen show: screen time, top apps, app of the week, an hour-by-hour heatmap, your viewer type, records, games and stories, Living Painting art, your teams' matches and a shareable summary card."),
                    tx("Geçen haftayla karşılaştırma: hangi uygulama yükseldi, hangisi düştü, süre ne kadar değişti.", "Compared with last week: which apps rose or fell and how your time changed."),
                    tx("🎵 Her haftanın müziğini yapay zekâ (Lyria) sana özel besteliyor; anahtar yoksa ya da üretilemezse gösteri sessiz oynar.", "🎵 Each week's music is composed for you by AI (Lyria); without a key, or if it fails, the show plays silently."),
                    tx("Ayarlar → Diğer → LiteDroid Wrapped: geçmiş haftalar ve bu haftanın önizlemesi. Özet kartı QR ile telefona alınır.", "Settings → More → LiteDroid Wrapped: past weeks and a preview of this week. Send the summary card to your phone via QR."),
                    tx("Yapay zekâ anahtarı yoksa: Canlı Tablo seçilemez (ekran koruyucu Karışık sahneye geçer), Masal Makinesi kilitli görünür (kayıtlı masallar yine dinlenir). Yapay zekâ o an çalışmazsa Canlı Tablo eski resimlerle ya da gece sahnesiyle, Çiz-Bil yapay zekâ turları atlanarak devam eder.", "Without an AI key: Living Painting can't be picked (the screensaver uses Mixed) and the Story Machine is locked (saved stories still play). If the AI fails, Living Painting keeps old paintings or the night scene and Draw & Guess skips AI rounds."),
                    tx("Düzeltme: yüzen menü açıkken ana ekrana dönülünce menü artık kapanıyor.", "Fix: the floating menu now closes when you go back to the home screen."),
                ),
            ),
            Release(
                "2.6.0",
                tx("Çiz-Bil", "Draw & Guess"),
                listOf(
                    tx("🎨 Çiz-Bil: telefonlar QR'ı okutup katılır. Sırası gelen gizli kelimeyi telefonunda parmağıyla çizer, çizim TV'de canlı belirir; diğerleri tahminini telefondan yazar, en hızlı bilen en çok puanı alır.", "🎨 Draw & Guess: phones scan the QR to join. Whoever's turn it is draws the secret word with a finger, live on the TV; the others type guesses, the fastest right answer scores most."),
                    tx("Üç kelimeden birini seç (kolay, orta, zor), renkler, kalınlık, silgi ve geri al. Süre ilerledikçe harf ipuçları açılır, \"çok yakın\" uyarısı yalnızca sana görünür.", "Pick one of three words (easy, medium, hard), colors, brush sizes, eraser and undo. Letter hints open as time runs out; \"so close\" is shown only to you."),
                    tx("🤖 Yapay zekâ çizsin turu: her turun sonunda resmi yapay zekâ çizer, iri piksellerden başlayıp yavaş yavaş netleşir, evdeki herkes birlikte tahmin eder (Gemini anahtarı gerekir). Tek kişiyle de oynanır.", "🤖 AI draws round: after each round the AI paints a picture that sharpens from big pixels, and everyone guesses together (needs a Gemini key). Works solo too."),
                    tx("Yüzen menü sadeleşti: Claude ve ekran görüntüsü düğmeleri kaldırıldı (ekran görüntüsü Input tuşuna atanabilir).", "A simpler floating menu: the Claude and screenshot buttons are gone (screenshots can go on the Input button)."),
                    tx("2.5.0'daki \"İzlediğin bitince kapat\" kaldırıldı: uygulamalar oynatmanın bittiğini güvenilir şekilde bildirmiyor. Uyku zamanlayıcısı süreyle ve saatle çalışmaya devam ediyor.", "\"Turn off when the show ends\" from 2.5.0 is gone: apps don't reliably report when playback ends. The sleep timer still works by duration or time."),
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
                    tx("Kumandadaki Input tuşuna iş atanabiliyor: hızlı panel, son uygulamaya dön, ekran görüntüsü… (Ayarlar → Kumanda).", "The remote's Input button can be assigned: quick panel, last app, screenshot… (Settings → Remote)."),
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
                tx("Masal Makinesi, Gemini sesi ve kendi kendine güncelleme", "Story Machine, Gemini voice and self-update"),
                listOf(
                    tx("🌙 Masal Makinesi: kahramanı, yeri ve konuyu seç; yapay zekâ masalı yazar, her sayfasını çizer ve anlatır. Ortada çocuk yolu seçer, karakterler kendi sesleriyle konuşur.", "🌙 Story Machine: pick a hero, a place and a theme; AI writes, illustrates and narrates the story. The child picks the path, characters have their own voices."),
                    tx("Film gibi akış, sayfaya göre değişen ninni müziği, bitince TV'nin kapanması ve ücretsiz yeniden dinlenen masal kitaplığı.", "Film-like pages, a mood-following lullaby, optional TV-off at the end and a library that replays for free."),
                    tx("🎙️ Claude'a sor cevapları Gemini'nin doğal sesleriyle okunuyor (Sulafat ve dokuz ses daha).", "🎙️ Ask Claude answers are read in Gemini's natural voices (Sulafat and nine more)."),
                    tx("🔄 Kendi kendine güncelleme: yeni sürümler GitHub'dan iniyor; Otomatik kur, Sadece haber ver ya da Kapalı. Ertele, ilerlemeyi izle, bitince \"Güncellendi\" ekranı.", "🔄 Self-update from GitHub: Automatic, Notify only or Off. Snooze, watch the progress, an \"Updated\" screen when done."),
                    tx("💣 Mayın Tarlası: üç zorluk, ilk kare hep güvenli.", "💣 Minesweeper: three levels, the first square is always safe."),
                    tx("Düzeltmeler: güncelleme pencereleri ekrana sığıyor, üst üste binmiyor ve kumanda arkaya kaçmıyor.", "Fixes: update windows fit the screen, never stack, and the remote no longer slips behind them."),
                ),
            ),
            Release(
                "1.7.0",
                tx("LiteDroid'in temelleri ve Bilgi Yarışması", "LiteDroid's foundations and Quiz Night"),
                listOf(
                    tx("Apple TV tarzı cam görünüm: Casual ve Modern arayüz, vitrin stilleri, kodla çizilmiş ekran koruyucu.", "Apple TV-style glass look: Casual and Modern layouts, showcase styles, a code-drawn screensaver."),
                    tx("🧠 Bilgi Yarışması: telefonlar QR'ı okutup katılır, en hızlı doğru cevap kazanır; yüzlerce Türkçe soru.", "🧠 Quiz Night: phones scan the QR to join, the fastest right answer wins; hundreds of questions."),
                    tx("Claude'a sor (sesli soru, Gemini desteği), canlı skorlar, Avrupa kupaları, takım takibi ve yüzen skor şeridi.", "Ask Claude (voice questions, Gemini support), live scores, European cups, team follow and a floating score strip."),
                    tx("Kaldığın yerden devam et, Şimdi çalıyor, uyku zamanlayıcısı, yüzen menü, kullanım raporu ve dahili oyunlar.", "Continue watching, Now playing, the sleep timer, floating menu, usage report and built-in games."),
                ),
            ),
        )
}
