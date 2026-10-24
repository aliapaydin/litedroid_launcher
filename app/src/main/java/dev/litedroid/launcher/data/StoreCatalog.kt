package dev.litedroid.launcher.data

import android.content.Intent
import dev.litedroid.launcher.tx
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Mağazadaki bir uygulama.
 *
 * Kurulum ve güncelleme Google Play üzerinden yapılır; LiteDroid APK indirmez.
 * - [playId] doğrulanmış Play paket adı: doğrudan uygulamanın Play sayfası açılır.
 * - [playId] null ise Play'de [searchQuery] aranır (paket adı değişebilen yerel servisler için).
 * - "Yüklü mü?" kontrolü [packages] veya cihazdaki uygulama adının [labelHints] ile eşleşmesine bakar.
 */
data class StoreApp(
    val name: String,
    val description: String,
    val packages: List<String> = emptyList(),
    val playId: String? = packages.firstOrNull(),
    val searchQuery: String = name,
    val labelHints: List<String> = listOf(name),
    /** Kartta küçük etiket: "Çok sevilen", "Ücretsiz"... */
    val tag: String? = null,
)

data class StoreSection(val title: String, val apps: List<StoreApp>)

object StoreCatalog {

    const val PLAY_PACKAGE = "com.android.vending"

    private val popular get() = tx("Çok sevilen", "Popular")
    private val free get() = tx("Ücretsiz", "Free")
    private val local get() = tx("Yerli", "Turkish")

    /** Her okunuşta yeniden kurulur ki açıklamalar seçili dilde gelsin. */
    val sections: List<StoreSection>
        get() = listOf(
            StoreSection(
                tx("Dizi ve film", "Shows and movies"),
                listOf(
                    StoreApp("Netflix", tx("Dizi, film ve belgesel", "Shows, movies, documentaries"), listOf("com.netflix.ninja", "com.netflix.mediaclient"), tag = popular),
                    StoreApp(
                        "Prime Video", tx("Amazon dizi ve filmleri", "Amazon shows and movies"),
                        listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"), tag = popular,
                    ),
                    StoreApp("Disney+", tx("Disney, Pixar, Marvel, Star Wars", "Disney, Pixar, Marvel, Star Wars"), listOf("com.disney.disneyplus"), tag = popular),
                    StoreApp("HBO Max", tx("HBO dizileri ve filmler", "HBO series and movies"), listOf("com.wbd.stream"), labelHints = listOf("HBO Max", "Max")),
                    StoreApp("Apple TV", tx("Apple TV+ dizi ve filmleri", "Apple TV+ shows and movies"), listOf("com.apple.atve.androidtv.appletv")),
                    StoreApp("MUBI", tx("Seçkin sanat filmleri", "Curated arthouse films"), playId = null),
                    StoreApp("Pluto TV", tx("Ücretsiz canlı kanallar ve filmler", "Free live channels and movies"), listOf("tv.pluto.android"), tag = free),
                ),
            ),
            StoreSection(
                tx("Yerli platformlar", "Turkish platforms"),
                listOf(
                    StoreApp("Exxen", tx("Yerli dizi, program ve spor", "Turkish shows and sports"), playId = null, tag = local),
                    StoreApp("TV+", tx("Canlı TV ve dizi", "Live TV and shows"), playId = null, searchQuery = "TV+ Turkcell", tag = local),
                    StoreApp("GAİN", tx("Yerli dizi ve film", "Turkish shows and movies"), playId = null, labelHints = listOf("GAİN", "Gain"), tag = local),
                    StoreApp("tabii", tx("TRT dizi, film ve belgeseller", "TRT shows, films, documentaries"), playId = null, tag = free),
                    StoreApp("puhutv", tx("Ücretsiz yerli dizi ve film", "Free Turkish shows and movies"), playId = null, tag = free),
                    StoreApp("TOD", tx("beIN Sports, dizi ve film", "beIN Sports, shows and movies"), playId = null, searchQuery = "TOD beIN", labelHints = listOf("TOD", "beIN CONNECT")),
                    StoreApp("S Sport Plus", tx("Canlı spor yayınları", "Live sports"), playId = null),
                ),
            ),
            StoreSection(
                tx("Müzik", "Music"),
                listOf(
                    StoreApp("Spotify", tx("Müzik ve podcast", "Music and podcasts"), listOf("com.spotify.tv.android"), tag = popular),
                    StoreApp("YouTube Music", tx("Şarkılar, klipler, canlılar", "Songs, videos, live"), listOf("com.google.android.youtube.tvmusic")),
                    StoreApp("Apple Music", tx("100 milyon şarkı", "100 million songs"), playId = null),
                    StoreApp("Deezer", tx("Müzik ve radyo", "Music and radio"), listOf("deezer.android.tv"), playId = null),
                    StoreApp("fizy", tx("Yerli müzik platformu", "Turkish music service"), playId = null, tag = local),
                ),
            ),
            StoreSection(
                tx("Video ve canlı yayın", "Video and live streaming"),
                listOf(
                    StoreApp("YouTube", "Video", listOf("com.google.android.youtube.tv"), tag = popular),
                    StoreApp("YouTube Kids", tx("Çocuklar için güvenli video", "Safe videos for kids"), listOf("com.google.android.youtube.tvkids"), tag = free),
                    StoreApp("Twitch", tx("Oyun ve canlı yayın", "Gaming and live streams"), playId = null),
                    StoreApp("TED", tx("İlham veren konuşmalar", "Inspiring talks"), playId = null, tag = free),
                ),
            ),
            StoreSection(
                tx("Medya oynatıcılar", "Media players"),
                listOf(
                    StoreApp("VLC", tx("Her formatı oynatır", "Plays every format"), listOf("org.videolan.vlc"), tag = free),
                    StoreApp("Kodi", tx("Açık kaynak medya merkezi", "Open-source media center"), listOf("org.xbmc.kodi"), tag = free),
                    StoreApp("Plex", tx("Kendi arşivini her yerde izle", "Your library, anywhere"), listOf("com.plexapp.android"), tag = popular),
                    StoreApp("Jellyfin", tx("Ücretsiz kendi medya sunucun", "Free self-hosted media server"), listOf("org.jellyfin.androidtv"), tag = free),
                    StoreApp("Just Player", tx("Hafif, reklamsız oynatıcı", "Light, ad-free player"), listOf("com.brouken.player"), tag = free),
                    StoreApp("MX Player", tx("Altyazı desteği güçlü oynatıcı", "Player with great subtitles"), listOf("com.mxtech.videoplayer.ad")),
                ),
            ),
            StoreSection(
                tx("Araçlar", "Tools"),
                listOf(
                    StoreApp("Send Files to TV", tx("Telefondan TV'ye dosya gönder", "Send files from phone to TV"), listOf("com.yablio.sendfilestotv"), tag = popular),
                    StoreApp("X-plore", tx("Gelişmiş dosya yöneticisi", "Advanced file manager"), listOf("com.lonelycatgames.Xplore")),
                    StoreApp("Downloader", tx("Adresten dosya indir, tarayıcı", "Download by URL, browser"), listOf("com.esaba.downloader")),
                    StoreApp("TV Bro", tx("Kumandaya uygun web tarayıcı", "Remote-friendly web browser"), listOf("com.phlox.tvwebbrowser"), tag = free),
                    StoreApp("Button Mapper", tx("Kumanda tuşlarını özelleştir", "Remap remote buttons"), listOf("flar2.homebutton")),
                    StoreApp("Analiti", tx("İnternet hız testi", "Internet speed test"), playId = null, searchQuery = "Analiti speed test"),
                ),
            ),
        )

    /** Cihazda yüklüyse karşılık gelen uygulamayı döndürür. */
    fun findInstalled(app: StoreApp, installed: List<LauncherApp>): LauncherApp? =
        installed.firstOrNull { it.packageName in app.packages }
            ?: installed.firstOrNull { inst -> app.labelHints.any { inst.label.equals(it, ignoreCase = true) } }

    /** Uygulamanın Play sayfası (ya da Play araması) için intent'ler; ilki çalışmazsa sıradakini dene. */
    fun playIntents(app: StoreApp): List<Intent> {
        val (market, web) = if (app.playId != null) {
            "market://details?id=${app.playId}" to "https://play.google.com/store/apps/details?id=${app.playId}"
        } else {
            val q = Uri.encode(app.searchQuery)
            "market://search?q=$q&c=apps" to "https://play.google.com/store/search?q=$q&c=apps"
        }
        return listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse(market)).setPackage(PLAY_PACKAGE),
            Intent(Intent.ACTION_VIEW, Uri.parse(market)),
            Intent(Intent.ACTION_VIEW, Uri.parse(web)),
        ).map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    /** Play Store'un kendisi: "Uygulamalarım" ve güncellemeler buradan. */
    fun playStoreIntent(pm: PackageManager): Intent? =
        (pm.getLeanbackLaunchIntentForPackage(PLAY_PACKAGE) ?: pm.getLaunchIntentForPackage(PLAY_PACKAGE))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
