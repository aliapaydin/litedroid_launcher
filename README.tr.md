<p align="center">
  <img src="docs/hero.png" alt="LiteDroid Launcher" width="100%">
</p>

<p align="center">
  <b>Android TV ve Google TV için hızlı, cam görünümlü ana ekran.</b><br>
  Uygulamalar arası kaldığın yerden devam · iki arayüz · altı dahili oyun · sinematik ekran koruyucu · reklamsız, root gerektirmez.
</p>

<p align="center">
  <a href="README.md">🇬🇧 English</a> ·
  <a href="#-özellikler">Özellikler</a> ·
  <a href="#-performans">Performans</a> ·
  <a href="#-kurulum">Kurulum</a> ·
  <a href="CHANGELOG.md">Sürüm notları</a>
</p>

---

LiteDroid bir hafta sonu sorusuyla başladı: *"Chromecast with Google TV'm Apple TV gibi hissettirebilir mi?"* Sonunda Kotlin ve Jetpack Compose ile sıfırdan yazılmış tam bir launcher'a dönüştü. Standart ana ekranın yerine buzlu cam menüler, odaktaki uygulamaya göre renk değiştiren bir vitrin, uygulamalar arası çalışan bir "kaldığın yerden devam et" rafı, kumandayla oynanan oyunlar ve kodla çizilmiş animasyonlu bir ekran koruyucu getiriyor. Bunları yaparken mütevazı TV donanımında da akıcı kalıyor.

## 📸 Ekran görüntüleri

| Modern arayüz | Klasik ("Casual") arayüz |
|---|---|
| ![Modern](docs/screenshots/modern-home.png) | ![Casual](docs/screenshots/casual-home.png) |
| **Dahili oyunlar** | **Ayarlar** |
| ![Oyunlar](docs/screenshots/games.png) | ![Ayarlar](docs/screenshots/settings.png) |

## ✨ Özellikler

**İki arayüz**
- **Modern:**
  - Ana sayfa · Uygulamalar · Oyunlar · Mağaza sekmeleri.
  - Uygulamaları tanıtan dönen bir vitrin ve geniş kart satırları.
  - Arama, çan, hızlı ayarlar, profil ve büyük saat.
  - Altta ince bir çubukta Wi-Fi, hava durumu ve konum.
- **Casual:**
  - Ortasında favori uygulamaların durduğu yüzen bir cam üst bar.
  - Odaktaki uygulamanın rengini alan sinematik bir vitrin ve zıplamalı büyük karolar.
  - Üç vitrin stili var: Sinematik, Kart ve Sade.

**Kaldığın yerden devam et**
- YouTube, Prime Video, Apple TV, Netflix gibi video uygulamalarında oynattıklarını medya oturumlarından yakalar. Spotify gibi müzik uygulamaları bu rafa girmez.
- Her içerik tek kart olarak görünür, bir uygulamadan en fazla 3 yarım içerik tutulur.
- Aynı uygulamadan birden fazla kart varsa her birinin kendi rengi, numarası, "21 dk kaldı" ve "2 sa önce" bilgisi olur.
- Karta basınca mümkün olan en iyi şekilde devam eder:
  - Uygulama arkada açıksa oynatıcıya döner.
  - YouTube'u kaldığı saniyeden açar.
  - Netflix, Prime ve Disney+ için doğrudan içerik bağlantısını dener.
  - Hiçbiri olmazsa uygulamayı açar.

**✨ Claude'a sor**
- Işıltı düğmesine bas, sorunu söyle. Cevap ekranda büyük yazıyla akarak gelir, sonra sesli okunur.
- Güncel sorularda (bu akşamın maçları, haberler, hava) web'de arar ve kaynakları gösterir. Takip soruları bağlamı korur, istersen yazarak da sorabilirsin.
- Kendi Anthropic API anahtarınla çalışır: Ayarlar → Claude'a sor → API anahtarı. Kumandayla yazmak zor olduğu için bilgisayardan gönderebilirsin: `adb shell am start -n dev.litedroid.launcher/.MainActivity --es claude_key "sk-ant-api03-..."`

**⚽ Canlı skorlar**
- Süper Lig, Premier League, LaLiga, Serie A, Bundesliga ve Ligue 1 varsayılan olarak takipte.
- Takım logoları, dakika ve skorla gösterilir; gol olunca **GOL!** bildirimi çıkar.
- **Yüzen skor şeridi:** Netflix, YouTube ya da başka bir şey izlerken köşede küçük bir skor şeridi durur, alttaki uygulamayı engellemez. Ayarlardan açılır; bir kez `adb shell appops set dev.litedroid.launcher SYSTEM_ALERT_WINDOW allow` gerekir.

**🎧 Ekran koruyucu kartları**
- Spotify dahil çalan her şeyin kapağı, adı ve ilerlemesi.
- Bugünün maçları ve gol bildirimleri.

**Altı dahili oyun:**
- **Yılan**
- **Kutu İtme:** 5 bölüm, hepsi çözülebilir olarak doğrulandı.
- **Mini Yarış**
- **SOS:** Bilgisayara karşı.
- **XOX**
- **Sudoku:** 6 bulmaca, her biri tek çözümlü.

Mağazadan indirdiğin oyunlar ayrı bir rafta durur.

**Diğerleri**
- **Mağaza:** Etiketli öneriler (Çok sevilen, Ücretsiz, Yerli). Kurulum ve güncelleme Google Play üzerinden yapılır.
- **Ekran koruyucu:** Kodla çizilen dört sahne var: Sinema gecesi, Maç günü, Oyun zamanı ve Sakin gece. İstersen sistem ekran koruyucusu olarak da ayarlanabilir.
- **Diğer:**
  - Ses efektleri ve yaylı animasyonlar.
  - Anında değişen Türkçe/İngilizce arayüz.
  - Hava durumu, favoriler, uygulama gizleme ve taşıma, 7 duvar kâğıdı.

## ⚡ Performans

TV çipleri tam ekran bulanıklık, gölge ve üst üste saydamlıkta zorlanır. LiteDroid bu yükleri en baştan almayacak şekilde tasarlandı:
- **Duvar kâğıdı:** Her karede çizilmiyor, 320×180'lik bir resme bir kez pişirilip ekran kartında ölçekleniyor.
- **Bulanık görseller:** Canlı bulanıklık yok. Uygulama açılırken küçük bitmaplere önceden hazırlanıyor.
- **Gölgeler:** Gerçek gölge yerine kartın arkasına çizilen birkaç ince halka kullanılıyor.
- **Geçişler:** Ek ekran tamponu açmayan özel bir `FastCrossfade` ile yapılıyor.
- **Animasyonlar:** Sadece hareket açıkken çalışıyor. Değerleri çizim aşamasında okunduğu için ekranı yeniden kurmuyor.

## 📦 Kurulum

```bash
git clone https://github.com/aliapaydin/litedroid_launcher.git
cd litedroid_launcher
./gradlew assembleRelease        # JDK 17 gerekir (macOS: export JAVA_HOME=$(/usr/libexec/java_home -v 17))

adb connect <TV_IP>:<PORT>
adb install -r app/build/outputs/apk/release/app-release.apk
```

Kumandada ana ekran tuşuna bas ve **LiteDroid Launcher → Her zaman**'ı seç.

Önerilen tek seferlik izinler:

```bash
adb shell cmd notification allow_listener dev.litedroid.launcher/dev.litedroid.launcher.media.MediaListenerService
adb shell appops set dev.litedroid.launcher GET_USAGE_STATS allow
adb shell pm grant dev.litedroid.launcher android.permission.WRITE_SECURE_SETTINGS
adb shell appops set dev.litedroid.launcher WRITE_SETTINGS allow
```

TV'den ekran görüntüsü almak için `TV=<TV_IP>:<PORT> ./tools/tvshot.sh`, devam et kayıtlarını görmek için `adb logcat -s LiteDroidMedia` kullanabilirsin.

## 📄 Lisans

LiteDroid Launcher [MIT Lisansı](LICENSE) ile açık kaynaktır: kodu kullanabilir, değiştirebilir ve paylaşabilirsin; telif bildirimi korunmalıdır. Pakete dahil Plus Jakarta Sans yazı tipi kendi [SIL Open Font License](licenses/PlusJakartaSans-OFL.txt) lisansıyla gelir.

## 👤 Geliştirici

**Ali Apaydın** · [apaydin.a@gmail.com](mailto:apaydin.a@gmail.com)

Yazı tipi: [Plus Jakarta Sans](https://github.com/tokotype/PlusJakartaSans), SIL Open Font License 1.1 ([lisans](licenses/PlusJakartaSans-OFL.txt)).
