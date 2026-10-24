<p align="center">
  <img src="docs/hero.png" alt="LiteDroid Launcher" width="100%">
</p>

<p align="center">
  <b>Android TV ve Google TV için hızlı, cam görünümlü bir ana ekran.</b><br>
  Sesle Claude'a sor · canlı maç skorları · uygulamalar arası kaldığın yerden devam · altı dahili oyun · sinematik ekran koruyucu · reklamsız, root gerektirmez.
</p>

<p align="center">
  <img alt="Sürüm" src="https://img.shields.io/badge/s%C3%BCr%C3%BCm-1.2.0-ff5c8a?style=for-the-badge">
  <img alt="Platform" src="https://img.shields.io/badge/Android%20TV%20%7C%20Google%20TV-3ddc84?style=for-the-badge&logo=androidtv&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0-7f52ff?style=for-the-badge&logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-4285f4?style=for-the-badge&logo=jetpackcompose&logoColor=white">
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-26-555?style=for-the-badge">
  <a href="LICENSE"><img alt="Lisans: MIT" src="https://img.shields.io/badge/lisans-MIT-f5c542?style=for-the-badge"></a>
</p>

<p align="center">
  <a href="README.en.md">🇬🇧 English</a> ·
  <a href="#-özellikler">Özellikler</a> ·
  <a href="#-kaputun-altında">Kaputun altında</a> ·
  <a href="#-kurulum">Kurulum</a> ·
  <a href="CHANGELOG.md">Sürüm notları</a>
</p>

---

LiteDroid bir hafta sonu sorusuyla başladı: *"Chromecast with Google TV'm Apple TV gibi hissettirebilir mi?"* Sonra Kotlin ve Jetpack Compose ile sıfırdan yazılmış tam bir launcher'a dönüştü.

Standart ana ekranın yerine buzlu cam menüler, uygulamalar arası çalışan bir "kaldığın yerden devam et" rafı, sesle soru sorabildiğin bir yapay zekâ asistanı, maç izlerken köşede duran canlı skorlar, kumandayla oynanan oyunlar ve kodla çizilmiş bir ekran koruyucu getiriyor. Bunların hepsini mütevazı TV donanımında akıcı kalarak yapıyor.

## ✨ Özellikler

### 🏠 Modern ana ekran

<p align="center"><img src="docs/screenshots/modern-home.png" alt="Modern ana ekran" width="100%"></p>

- **Ana sayfa · Uygulamalar · Oyunlar · Mağaza** sekmeleri, uygulamalarını dönerek tanıtan bir vitrin ve geniş kart satırları.
- Üst barda Claude düğmesi, arama, bildirimler, hızlı panel, profil ve büyük saat. Alt kısımda bar yok, ekran tamamen içeriğe ayrılmış. Bağlantı ve hava durumu hızlı panelde.
- Bir uygulamaya basılı tutunca favorilere ekle, gizle ya da taşı.

| Uygulamalar | Klasik ("Casual") arayüz |
|---|---|
| ![Uygulamalar](docs/screenshots/apps.png) | ![Casual](docs/screenshots/casual-home.png) |

İstersen **Casual** arayüze geç: favori uygulamaların ortada durduğu yüzen bir cam bar, odaktaki uygulamanın rengini alan sinematik bir vitrin ve zıplayan büyük karolar. Ayarlar → Görünüm → Arayüz'den anında değişir.

### ▶️ Kaldığın yerden devam et

- YouTube, Prime Video, Apple TV, Netflix gibi uygulamalarda izlediklerini Android medya oturumlarından yakalar. Spotify gibi müzik uygulamaları bu rafa girmez.
- Her içerik tek kart olur ve bir uygulamadan **en fazla 3 yarım içerik** tutulur. Aynı uygulamadan birden fazla kart varsa her birinin kendi rengi, numarası, "21 dk kaldı" ve "2 sa önce" bilgisi olur.
- Karta basınca:
  - YouTube videoyu **kaldığı saniyeden** açar.
  - Tam içerik bağlantısı olmayan uygulamalar **temiz bir başlangıçla** açılır, yarım kalmış bir ekrana takılmazsın.

### ✨ Claude'a sor

<p align="center"><img src="docs/screenshots/ask-claude.png" alt="Claude'a sor" width="100%"></p>

- Üst bardaki ✦ düğmesine bas ve **sorunu söyle**. Cevap ekranda büyük yazıyla akarak gelir, sonra **sesli okunur**.
- Güncel sorularda (bu akşamın maçları, haberler, hava, vizyondaki filmler) **web'de arar** ve kullandığı **kaynakları** altta gösterir.
- Takip soruları bağlamı korur. İstersen yazarak da sorabilirsin.
- Model seçimi: **Haiku** (en hızlı ve en ucuz) ya da **Sonnet** (daha dengeli).
- Kendi Anthropic API anahtarınla çalışır. Anahtar sadece TV'de saklanır ve doğrudan `api.anthropic.com`'a gider. Kurulumu aşağıda: [Claude'a sor kurulumu](#claudea-sor-kurulumu).

### ⚽ Canlı skorlar

Süper Lig, Premier League, LaLiga, Serie A, Bundesliga ve Ligue 1 varsayılan olarak takipte. Hangi ligleri izleyeceğini ayarlardan seçersin.

<p align="center"><img src="docs/screenshots/score-strip-context.png" alt="Prime Video izlerken köşedeki skor şeridi" width="80%"></p>

**Yüzen skor şeridi:** Netflix, YouTube, Prime ya da başka bir şey izlerken sağ üst köşede küçük bir şerit durur.
- Takım logoları, skor ve dakika görünür.
- Aynı anda **en fazla 3 maç** gösterir. Daha fazla canlı maç varsa her 10 saniyede bir yukarı kayarak sıradaki maçlara geçer.
- Tek ya da iki maç varken koyu kutu ona göre küçülür.
- **Gol, kırmızı kart ya da maç sonu** olunca şeridin içinde ayrı bir kutu açılır ve animasyonla gösterir, sonra normale döner.
- Alttaki uygulamanın odağını hiç almaz. Ana ekranda, ekran koruyucuda ve yüzen menü açıkken kendiliğinden gizlenir.

<p align="center"><img src="docs/screenshots/score-strip.png" alt="Skor şeridi yakından" width="55%"></p>

### 🎛️ Her uygulamanın üstünde yüzen menü

- Netflix, YouTube ya da başka bir şey açıkken kumandada **Geri'ye iki kez hızlıca bas**.
- Sağdan bir LiteDroid menüsü açılır. İçinde saat, hava durumu, çalan müziğin kontrolleri, bugünün tüm maçları, skor şeridi ayarları ve Ana ekran / Claude'a sor kısayolları var.
- **Tek basış normal Geri olarak çalışmaya devam eder.**
- Bir kez açman gerekiyor: **Android Ayarları → Erişilebilirlik → LiteDroid kumanda kısayolu**. Servis yalnızca tuş basışlarını ve öndeki uygulamanın adını görür.

### 🧩 Hızlı panel

<p align="center"><img src="docs/screenshots/quick-panel.png" alt="Hızlı panel" width="100%"></p>

Android'deki gibi sağdan kayarak açılan widget tarzı bir panel:
- Çalan müziğin kapağı ve kontrolleri.
- Saat, tarih, hava durumu ve ağ bağlantısı.
- Arayüz, ses efektleri, hava durumu, animasyon ve vitrin stili için tek tuşla değişen kutucuklar.
- Tüm ayarlar, Mağaza, Android ayarları ve Hakkında kısayolları.
- İçerik uzarsa panel kendi içinde kayar.

### 🌙 Ekran koruyucu

<p align="center"><img src="docs/screenshots/screensaver.png" alt="Ekran koruyucu" width="100%"></p>

- Tamamen kodla çizilmiş dört sahne: **Sinema gecesi, Maç günü, Oyun zamanı** (synthwave) ve **Sakin gece** (kuzey ışıkları).
- Cam bir saat kartı ve süzülen ışık tozları.
- **Şimdi çalıyor** kartı: Spotify, YouTube Music, Apple Music, TIDAL, Qobuz, Deezer, fizy… çalan her şeyin kapağı, adı, sanatçısı ve canlı ilerleme çubuğu. Aynı kart hızlı panelde ve yüzen menüde de var.
- **Canlı skor kartı:** Bugünün maçlarını ve gol bildirimlerini gösterir. Ekranı korumak için yavaşça yer değiştirir.
- İstersen sistem ekran koruyucusu olarak da ayarlayabilirsin.

### 🎮 Kumandaya göre yapılmış altı oyun

| Oyunlar | Yılan | Sudoku |
|---|---|---|
| ![Oyunlar](docs/screenshots/games.png) | ![Yılan](docs/screenshots/game-snake.png) | ![Sudoku](docs/screenshots/game-sudoku.png) |

| | |
|---|---|
| 🐍 **Yılan** | Uzadıkça hızlanır, en iyi skor kaydedilir. |
| 📦 **Kutu İtme** | Elle tasarlanmış 5 Sokoban bölümü. Hepsinin çözülebildiği doğrulandı. |
| 🏎️ **Mini Yarış** | Üç şerit, artan hız, akıcı 60 fps. |
| 🔤 **SOS** | 6×6 tahtada, SOS kovalayan ve sana fırsat vermemeye çalışan bilgisayara karşı. |
| ❌ **XOX** | Minimax rakip, biraz rastgelelikle, yani yenilebilir. |
| 🔢 **Sudoku** | Kolay ve orta 6 bulmaca. Her birinin tek çözümü olduğu doğrulandı. Rakam tuşları da çalışır. |

Google Play'den yüklediğin oyunlar altta ayrı bir rafta durur.

### ⚙️ Sekmeli ayarlar

<p align="center"><img src="docs/screenshots/settings.png" alt="Ayarlar" width="100%"></p>

Ayarlar solda sekmelere ayrılmıştır: **Görünüm, Profil, Hava durumu, Ses, Ekran koruyucu, Canlı skorlar, Claude'a sor, İçerik ve gizlilik** ve **Diğer**. Odaktaki satırın açıklaması altta görünür, kumandayla aradığını bulmak kolaydır. **Diğer → Sürüm notları**'nda ilk sürümden bugüne tüm yenilikler sürüm sürüm listelenir.

### Ve diğerleri

- **Mağaza:** Yayın platformları, yerli platformlar, müzik, oynatıcılar ve araçlardan seçilmiş uygulamalar. *Çok sevilen / Ücretsiz / Yerli* etiketleri var. Kurulum ve güncellemeler Google Play üzerinden yapılır, LiteDroid APK yüklemez.
- **Ses ve hareket:** Gezinme, seçme, geri ve kenara çarpma sesleri. Üç hız ayarlı yaylı animasyonlar.
- **Türkçe ve İngilizce arayüz.** Dil anında değişir.
- **Hava durumu** Open-Meteo'dan gelir. Konum IP'den bulunur ya da şehri kendin yazarsın.
- **Diğer:** Favoriler, uygulama gizleme ve taşıma, 7 duvar kâğıdı. Geri tuşu seni asla launcher'ın dışına atmaz.

<p align="center"><img src="docs/screenshots/about.png" alt="Hakkında" width="70%"></p>

## 🛠 Kaputun altında

TV çipleri, launcher'ların en sevdiği şeylerde zorlanır: tam ekran bulanıklık, gölgeler ve üst üste binen saydamlık. LiteDroid bunlardan kaçınacak şekilde tasarlandı.

| Sorun | LiteDroid'in çözümü |
|---|---|
| Hareketli duvar kâğıdı her karede yeniden çiziliyor | Bir kez **320×180'lik bir bitmap'e** pişirilip GPU ile ölçekleniyor |
| `Modifier.blur` / canlı bulanıklık | Hiç yok. Bulanık uygulama görselleri yüklenirken küçük bitmap'lere **önceden hazırlanıyor** |
| Her kartta `Modifier.shadow` | Kartın arkasına çizilen **birkaç ince yuvarlak halka** (`softGlow`, `softShadow`) |
| Ekran dışı katman açan `Crossfade` | `CompositingStrategy.ModulateAlpha` kullanan özel bir **`FastCrossfade`** |
| Sürekli çalışan sonsuz animasyonlar | **Sadece hareket açıkken** oluşturuluyor, menülerin altında duraklıyor |
| Animasyon değerlerinin ekranı yeniden kurması | Değerler `graphicsLayer` ve layout lambda'larında okunuyor, kompozisyonda değil |

**Canlı skor sistemi** (`data/Scores.kt`)
- ESPN'in herkese açık skor akışından 6 ligin günlük maçlarını çeker.
- Canlı maç varken her 45 saniyede, yokken her 5 dakikada bir yeniler.
- Ekranlar akışa abone olup bırakır. Kimse dinlemiyorsa ağ isteği atılmaz.
- Önceki ve yeni durumu karşılaştırıp **Gol, Kırmızı kart ve Maç sonu** olayları üretir. Golü atan oyuncu ve dakika da olaya eklenir.

**Yüzen şerit ve menü** (`overlay/`)
- Şerit, `TYPE_APPLICATION_OVERLAY` türünde, odak almayan ve dokunulamayan bir pencerede çizilen bir ön plan servisidir. Gösterecek bir şey yokken pencere tamamen gizlenir.
- Menü bir erişilebilirlik servisidir: Geri tuşunu dinler, 320 ms içindeki ikinci basışta `TYPE_ACCESSIBILITY_OVERLAY` penceresini açar, tek basışı sisteme geri iletir.

**Claude entegrasyonu** (`data/ClaudeClient.kt`)
- Messages API'ye **akışlı (SSE)** istek atar. Cevap kelime kelime ekrana gelir.
- `web_search` aracıyla en fazla 3 arama yapar ve kaynakları gösterir.
- Ses girişi TV'nin kendi konuşma tanıma servisiyle, okuma ise TextToSpeech ile yapılır.

**Kaldığın yerden devam** (`media/`)
- İçerikleri medya kimliği → gerçek başlık → uygulama sırasıyla eşleştirir. Uygulamanın kendi adını başlık gibi gönderdiği sahte başlıkları yok sayar.
- Fragmanları elemek için başlıksız oturumlarda en az 12 saniye oynatma ister.
- Kesin içerik bağlantısı yoksa uygulamayı eski ekranına değil, baştan açar.

## 📦 Kurulum

**Gerekenler:** Android TV ya da Google TV cihazı (Chromecast with Google TV, Android 14 üzerinde test edildi), `adb` kurulu bir bilgisayar ve JDK 17.

```bash
git clone https://github.com/aliapaydin/litedroid_launcher.git
cd litedroid_launcher
./gradlew assembleRelease        # JDK 17 gerekir (macOS: export JAVA_HOME=$(/usr/libexec/java_home -v 17))
```

TV'de **Geliştirici seçenekleri → Kablosuz hata ayıklama**'yı aç, sonra:

```bash
adb pair  <TV_IP>:<EŞLEŞME_PORTU>     # sadece ilk sefer
adb connect <TV_IP>:<PORT>
adb install -r app/build/outputs/apk/release/app-release.apk
```

Kumandada **Ana ekran** tuşuna bas ve **LiteDroid Launcher → Her zaman**'ı seç.

### Tek seferlik izinler

Android TV bu izinleri arayüzünde göstermediği için bir kez adb ile verilir:

| İzin | Ne işe yarar | Komut |
|---|---|---|
| Bildirim dinleyici | Kaldığın yerden devam, şimdi çalıyor kartları | `adb shell cmd notification allow_listener dev.litedroid.launcher/dev.litedroid.launcher.media.MediaListenerService` |
| Kullanım istatistikleri | Gerçek "son kullanılanlar" listesi | `adb shell appops set dev.litedroid.launcher GET_USAGE_STATS allow` |
| Diğer uygulamaların üstünde gösterme | Yüzen skor şeridi | `adb shell appops set dev.litedroid.launcher SYSTEM_ALERT_WINDOW allow` |
| Güvenli ayarlar | LiteDroid'i sistem ekran koruyucusu yapmak | `adb shell pm grant dev.litedroid.launcher android.permission.WRITE_SECURE_SETTINGS` |
| Sistem ayarları | Aynı amaçla | `adb shell appops set dev.litedroid.launcher WRITE_SETTINGS allow` |

**Yüzen menü** için ayrıca TV'de: **Ayarlar → Sistem → Erişilebilirlik → LiteDroid kumanda kısayolu → Aç**.

### Claude'a sor kurulumu

1. [console.anthropic.com](https://console.anthropic.com) adresinden bir API anahtarı oluştur ve hesabına bakiye yükle. Kullanım senin hesabından ücretlendirilir, Haiku en ekonomik seçenektir.
2. Kumandayla uzun anahtar yazmak zor olduğu için anahtarı bilgisayardan gönder:
   ```bash
   adb shell am start -n dev.litedroid.launcher/.MainActivity --es claude_key "sk-ant-api03-..."
   ```
   İstersen **Ayarlar → Claude'a sor → API anahtarı**'ndan da girebilirsin.
3. Üst bardaki ✦ düğmesine bas ve sor.

> Anahtarını kimseyle paylaşma. Ekran görüntüsünde ya da bir yerde göründüyse konsoldan silip yenisini oluştur.

<details>
<summary>Tek ana ekran yap (isteğe bağlı, geri alınabilir)</summary>

```bash
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
# geri almak için:
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith
```
</details>

<details>
<summary>TV'den ekran görüntüsü al</summary>

```bash
TV=<TV_IP>:<PORT> ./tools/tvshot.sh        # tek görüntü
TV=<TV_IP>:<PORT> ./tools/tvshot.sh 5 1    # 1 saniye arayla 5 görüntü
```
DRM korumalı içerikler (Netflix, Prime Video…) siyah görünür. Bu Android'in korumasıdır.
</details>

<details>
<summary>Sorun giderme</summary>

| Sorun | Bakılacak yer |
|---|---|
| Kaldığın yerden devam kartı gelmiyor | Bildirim dinleyici iznini ver, sonra `adb logcat -s LiteDroidMedia` |
| Skorlar ya da şerit görünmüyor | Ayarlar → Canlı skorlar'da lig ve şerit açık mı? `adb logcat -s LiteDroidScores` |
| Geri'ye iki kez basınca menü açılmıyor | Erişilebilirlik servisi açık mı? `adb logcat -s LiteDroidRemote` |
| Claude "bakiye yok" diyor | Anthropic konsolunda hesabına kredi yükle |
| Claude anahtarı kabul etmiyor | Anahtar `sk-ant-api03-` ile başlamalı, adb komutuyla tam olarak gönder |
</details>

## ℹ️ Uygulamalara göre "kaldığın yerden devam"

Google TV kendi devam etme verilerini sadece sistem uygulamalarına açar. LiteDroid bu yüzden her uygulamanın medya oturumunda paylaştığı bilgiyle çalışır:

| Uygulama | Paylaştığı bilgi | Sonuç |
|---|---|---|
| YouTube | Başlık, kanal, kapak görseli, konum, video kimliği | Başlıklı, kapaklı kart. Tam kaldığın saniyeden açılır |
| Prime Video | İçerik kimliği, konum (başlık yok) | Her içerik için numaralı ve renkli kart (en fazla 3) |
| Apple TV | Sadece süre | Kalan süreyi gösteren uygulama kartı |
| Netflix | Oynat/duraklat dışında hiçbir şey | Uygulama kartı |

## 🗂 Proje yapısı

```
app/src/main/java/dev/litedroid/launcher/
├── MainActivity.kt          Ana ekran intent'i, tuşlar, boşta kalma zamanlayıcısı, sesler
├── LauncherViewModel.kt     Uygulamalar, favoriler, ayarlar, geçmiş, hava, ağ
├── I18n.kt                  Türkçe / İngilizce
├── data/                    Uygulamalar, ayarlar, hava, mağaza, skorlar, Claude istemcisi, sesli okuma
├── media/                   Medya oturumu dinleyicisi, geçmiş, devam ettirme mantığı
├── overlay/                 Yüzen skor şeridi, kumanda kısayolu servisi, yüzen menü
├── dream/                   Sistem ekran koruyucusu (DreamService)
├── games/                   Yılan, Kutu İtme, Mini Yarış, SOS, XOX, Sudoku
└── ui/                      Modern ve Casual arayüzler, Claude ekranı, hızlı panel, widget'lar, ayarlar
```

**Kullanılanlar:** Kotlin 2.0 · Jetpack Compose (BOM 2024.12, Material 3) · Coroutines · Coil 3 · Android Gradle Plugin 8.7 · minSdk 26 / targetSdk 35.

Hazır TV arayüz kütüphanesi yok, kumanda odağı elle yönetiliyor. Üçüncü parti arayüz kiti de yok: simgeler, oyunlar ve ekran koruyucu sahnelerinin hepsi kodla çiziliyor. Ses efektleri bu proje için sentezlendi.

## 🙏 Teşekkürler

- [Plus Jakarta Sans](https://github.com/tokotype/PlusJakartaSans) yazı tipi, SIL Open Font License 1.1 ([lisans](licenses/PlusJakartaSans-OFL.txt))
- Hava durumu için [Open-Meteo](https://open-meteo.com), yaklaşık konum için [ipwho.is](https://ipwhois.io)
- *Claude'a sor* için Anthropic'in [Claude API](https://platform.claude.com/docs)'si (kendi anahtarınla)
- Maç verileri ESPN'in herkese açık skor akışından gelir. Resmi değildir, anahtar gerektirmez ve haber verilmeden değişebilir.
- Jetpack Compose ve Coil

## 📄 Lisans

LiteDroid Launcher [MIT Lisansı](LICENSE) ile açık kaynaktır. Kodu kullanabilir, değiştirebilir ve paylaşabilirsin, yeter ki telif bildirimi korunsun. Pakete dahil Plus Jakarta Sans yazı tipi kendi [SIL Open Font License](licenses/PlusJakartaSans-OFL.txt) lisansıyla gelir.

## 👤 Geliştirici

**Ali Apaydın** · [apaydin.a@gmail.com](mailto:apaydin.a@gmail.com)

LiteDroid'i kendi TV'nde denersen bir ekran görüntüsü görmek isterim. Hata bildirimleri, fikirler ve pull request'ler her zaman açık.
