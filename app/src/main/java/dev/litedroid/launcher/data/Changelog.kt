package dev.litedroid.launcher.data

import dev.litedroid.launcher.tx

/** Ayarlar → Diğer → Sürüm notları: LiteDroid'in ilk günden bugüne tüm sürümleri. */
data class Release(val version: String, val title: String, val items: List<String>)

object Changelog {
    /** En yeni sürüm en üstte. Dil değişince yeniden okunur (tx o anki dile göre döner). */
    val releases: List<Release>
        get() = listOf(
            Release(
                "1.4.0",
                tx("Home tuşu kilitli kutularda da LiteDroid", "LiteDroid on boxes that lock the Home key"),
                listOf(
                    tx("Kendi ana ekranının kapatılmasına izin vermeyen TV kutularında (ör. Botech Wzone) Home tuşu artık LiteDroid'i açar.",
                        "On TV boxes that won't let you turn off their own home screen (e.g. Botech Wzone), Home now opens LiteDroid."),
                    tx("Kutunun ana ekranı açılışta ya da bir uygulamadan çıkınca öne gelirse LiteDroid hemen devralır.",
                        "If the box's home screen comes up at boot or when leaving an app, LiteDroid takes over right away."),
                    tx("Ayarlar'da \"Home tuşunu LiteDroid'e yönlendir\" ile açılıp kapatılır; kumanda kısayolu servisi açık olmalı.",
                        "Switch it in Settings with \"Send Home to LiteDroid\"; it needs the remote shortcut service on."),
                ),
            ),
            Release(
                "1.3.1",
                tx("Kullanım raporu yeniden düzenlendi", "Usage report reworked"),
                listOf(
                    tx("Rapor artık koyu, okunaklı bir sayfa; arkadaki ana ekran görünmüyor.", "The report is now a dark, readable page; the home screen no longer shows through."),
                    tx("Sayfa kumandayla aşağı kayar: özet, grafikler ve uygulamalar sırayla gelir.", "The whole page scrolls with the remote: summary, charts, then apps."),
                    tx("Uygulamalar iki sütunlu kartlarda: simge, sıra, süre, pay ve açılış bilgisi.", "Apps in two-column cards with icon, rank, time, share and opens."),
                    tx("Ortam modu gibi sistem bileşenleri paket adıyla görünmez, rapora alınmaz.", "System components like ambient mode no longer show up as package names."),
                ),
            ),
            Release(
                "1.3.0",
                tx("Kullanım raporu", "Usage report"),
                listOf(
                    tx("Ayarlar → İçerik ve gizlilik → Kullanım raporu: Bugün, Dün ve Son 7 gün için ekran süresi.",
                        "Settings → Content and privacy → Usage report: screen time for Today, Yesterday and the Last 7 days."),
                    tx("Uygulama sıralaması: toplam süre, pay, açılış sayısı, en uzun oturum ve son kullanım.",
                        "App ranking: total time, share, opens, longest session and last use."),
                    tx("Saatlere göre ve son 7 güne göre grafikler, en yoğun saat ve düne göre fark.",
                        "Charts by hour and by day, the busiest hour and the change from yesterday."),
                    tx("Süreler Android'in ön plan kayıtlarından hesaplanır; ana ekran ve ekran koruyucu sayılmaz.",
                        "Times come from Android's foreground records; the home screen and screensaver don't count."),
                ),
            ),
            Release(
                "1.2.0",
                tx("Her müzik uygulaması, sade alt kısım, sürüm notları", "Every music app, cleaner bottom, release notes"),
                listOf(
                    tx("Şimdi çalıyor kartı Spotify'ın yanında YouTube Music, Apple Music, TIDAL, Qobuz, Deezer, fizy gibi tüm müzik uygulamalarıyla çalışır: ekran koruyucu, yüzen menü ve hızlı panelde şarkı adı, sanatçı, kapak ve ilerleme.",
                        "The Now playing card works with every music app besides Spotify (YouTube Music, Apple Music, TIDAL, Qobuz, Deezer, fizy…): title, artist, artwork and progress on the screensaver, floating menu and quick panel."),
                    tx("Oturumuna başlık ya da kapak koymayan uygulamalar için bilgiler medya bildiriminden tamamlanır, kapak adresleri arka planda yüklenir.",
                        "Apps that leave title or artwork out of their session are completed from the media notification; artwork URLs load in the background."),
                    tx("Kapatılan oynatıcılar karttan kalkar, birden fazla uygulama çalıyorsa en son değişen öne çıkar.",
                        "Closed players drop off the card; with several apps playing, the most recent one comes first."),
                    tx("Modern arayüzün alttaki siyah durum çubuğu kaldırıldı, ekran daha ferah. Bağlantı ve hava durumu hızlı panelde.",
                        "The black status bar at the bottom of the Modern layout is gone; connection and weather live in the quick panel."),
                    tx("Ayarlar → Diğer → Sürüm notları: bu ekran.", "Settings → More → Release notes: this screen."),
                ),
            ),
            Release(
                "1.1.2",
                tx("Skor şeridi düzeltmesi, yeni README", "Score strip fix, new README"),
                listOf(
                    tx("Gösterecek maç yokken skor şeridi penceresi tamamen gizlenir, ekran kenarında boş çerçeve kalmaz.",
                        "With nothing to show, the score strip window is fully hidden, so no empty frame lingers at the screen edge."),
                    tx("GitHub sayfası baştan, Türkçe olarak yeniden tasarlandı.", "The GitHub page was redesigned from scratch, in Turkish."),
                ),
            ),
            Release(
                "1.1.1",
                tx("Yüzen menü ve akıllı skor şeridi", "Floating menu and smarter score strip"),
                listOf(
                    tx("Herhangi bir uygulama açıkken Geri'ye iki kez bas: saat, hava, müzik kontrolleri, bugünün maçları ve skor ayarlarıyla yüzen LiteDroid menüsü. Tek basış normal Geri.",
                        "Press Back twice over any app: a floating LiteDroid menu with clock, weather, music controls, today's matches and score settings. A single press is a normal Back."),
                    tx("Skor şeridi ana ekranda, ekran koruyucuda ve menü açıkken gizlenir.", "The score strip hides on the home screen, the screensaver and while the menu is open."),
                    tx("Şerit küçüldü: en fazla 3 maç, daha fazlası 10 saniyede bir yukarı kayarak döner; kutu maç sayısına göre boyutlanır.",
                        "Smaller strip: up to 3 matches, more rotate every 10 s with a slide-up; the box sizes itself to the match count."),
                    tx("Gol, kırmızı kart ve maç sonu şeridin içinde animasyonlu ayrı bir kutuda gösterilir.", "Goals, red cards and full time play in their own animated box inside the strip."),
                    tx("Hızlı panel uzunsa kayar, müzik kartının altındaki kontrollere ulaşılır.", "The quick panel scrolls, so controls below the music card are reachable."),
                ),
            ),
            Release(
                "1.1.0",
                tx("Claude'a sor ve canlı skorlar", "Ask Claude and live scores"),
                listOf(
                    tx("Claude'a sor: sesle ya da yazarak soru, ekranda akan büyük cevap, sesli okuma, web araması ve kaynaklar, Haiku/Sonnet seçimi.",
                        "Ask Claude: voice or typed questions, streamed large answers, read aloud, web search with sources, Haiku/Sonnet choice."),
                    tx("Süper Lig, Premier League, LaLiga, Serie A, Bundesliga ve Ligue 1 için canlı skorlar ve gol bildirimleri.",
                        "Live scores and goal alerts for Süper Lig, Premier League, LaLiga, Serie A, Bundesliga and Ligue 1."),
                    tx("Diğer uygulamaların üstünde yüzen skor şeridi.", "A floating score strip over other apps."),
                    tx("Ekran koruyucuda Şimdi çalıyor ve canlı skor kartları.", "Now playing and live score cards on the screensaver."),
                    tx("Sekmeli ayarlar ve sağdan kayan yeni hızlı panel.", "Tabbed settings and a new quick panel sliding in from the right."),
                    tx("Devam kartları kesin içerik bağlantısı yoksa uygulamayı baştan açar.", "Continue cards start the app fresh when there is no exact content link."),
                ),
            ),
            Release(
                "1.0.1",
                tx("YouTube kapakları ve MIT lisansı", "YouTube thumbnails and MIT license"),
                listOf(
                    tx("Devam kartlarında YouTube videolarının gerçek kapak görseli.", "Real video thumbnails on YouTube continue cards."),
                    tx("Kartların altında buzlu yazı bandı, parlak görsellerde de okunur.", "A frosted text band on cards keeps titles readable on bright images."),
                    tx("Proje MIT lisansıyla açık kaynak oldu.", "The project became open source under the MIT License."),
                ),
            ),
            Release(
                "1.0.0",
                tx("LiteDroid Launcher doğdu", "LiteDroid Launcher is born"),
                listOf(
                    tx("Camekan'ın adı LiteDroid Launcher oldu.", "Camekan was renamed to LiteDroid Launcher."),
                    tx("Kaldığın yerden devam: içerik başına tek kart, uygulama başına en fazla 3, renk, numara, kalan süre.",
                        "Continue watching: one card per content, up to 3 per app, with color, number and time left."),
                    tx("Altı dahili oyun: Yılan, Kutu İtme, Mini Yarış, SOS, XOX, Sudoku.", "Six built-in games: Snake, Box Push, Mini Racer, SOS, Tic-Tac-Toe, Sudoku."),
                    tx("Etiketli önerilerle Mağaza, yeni Hakkında ekranı, geliştirilmiş ekran koruyucu.", "Store with tagged picks, a new About screen, an upgraded screensaver."),
                ),
            ),
            Release(
                "0.7",
                tx("Akıllı devam rafı", "Smarter continue row"),
                listOf(
                    tx("Vitrin sadece uygulamaları, devam rafı sadece videoları gösterir; müzik uygulamaları rafa girmez.",
                        "The hero shows only apps, the continue row only videos; music apps stay out."),
                    tx("Çan menüsü Kaldığın yerden devam listesine dönüştü.", "The bell menu became a Continue watching list."),
                    tx("YouTube, Netflix, Prime Video ve Disney+ için doğrudan bağlantılar.", "Direct links for YouTube, Netflix, Prime Video and Disney+."),
                ),
            ),
            Release(
                "0.6",
                tx("Medya oturumlarından devam", "Resume from media sessions"),
                listOf(
                    tx("Kaldığın yerden devam Android medya oturumlarıyla çalışmaya başladı.", "Continue watching started working from Android media sessions."),
                    tx("Ayarlar, Play Store gibi yardımcı uygulamalar vitrinden çıkarıldı.", "Utility apps like Settings and Play Store left the hero."),
                    tx("Bulanık simgeli kartlar ve yenilenen üst barlar.", "Blurred-icon cards and redesigned top bars."),
                ),
            ),
            Release(
                "0.5",
                tx("Büyük performans turu", "Big performance pass"),
                listOf(
                    tx("Pişirilmiş duvar kâğıdı, canlı bulanıklık ve gölge yok, hızlı geçişler.", "Baked wallpaper, no live blur or shadows, fast crossfades."),
                    tx("Her yerde Plus Jakarta Sans yazı tipi.", "Plus Jakarta Sans everywhere."),
                    tx("İngilizce arayüz seçeneği.", "English UI option."),
                ),
            ),
            Release(
                "0.4",
                tx("Modern arayüz", "Modern layout"),
                listOf(
                    tx("Sekmeler, öne çıkan vitrin, geniş kart satırları, arama ve çan.", "Tabs, featured hero, wide card rows, search and bell."),
                    tx("Yeniden çizilen ekran koruyucu sahneleri.", "Redrawn screensaver scenes."),
                ),
            ),
            Release(
                "0.3",
                tx("Yüzen üst bar ve ekran koruyucu", "Floating top bar and screensaver"),
                listOf(
                    tx("Favori uygulamalı yüzen cam üst bar, profil ve saat.", "A floating glass top bar with favorites, profile and clock."),
                    tx("Ses efektleri, yaylı animasyonlar ve logo.", "Sound effects, bouncy animations and a logo."),
                    tx("Dört sahneli ekran koruyucu ve ayarlar sayfası.", "A four-scene screensaver and a settings page."),
                ),
            ),
            Release(
                "0.2",
                tx("Vitrin stilleri", "Showcase styles"),
                listOf(
                    tx("Vitrin stilleri, renkli arka planlar ve devam şeridi.", "Showcase styles, colorful backgrounds and a continue strip."),
                    tx("İlk performans iyileştirmeleri.", "First performance work."),
                ),
            ),
            Release(
                "0.1",
                tx("İlk sürüm: Camekan", "First version: Camekan"),
                listOf(
                    tx("Apple TV tarzı ilk launcher: cam menüler, uygulama ızgarası, Hakkında ekranı.", "The first Apple TV-style launcher: glass menus, app grid, About screen."),
                    tx("Google Play'e bağlanan mağaza.", "A store linking to Google Play."),
                ),
            ),
        )
}
