# Kullanım: python3 tools/guide_images.py docs/kurulum
# Kurulum rehberi görselleri: temsili TV menüleri, terminal ekranları, akış şemaları.
import sys, pathlib
from playwright.sync_api import sync_playwright

OUT = pathlib.Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)

BASE_CSS = """
*{box-sizing:border-box;margin:0;padding:0}
body{width:1280px;height:720px;font-family:'Inter','Segoe UI',Roboto,Arial,sans-serif;color:#fff;overflow:hidden;
 background:radial-gradient(1200px 700px at 15% 10%,#2a2350 0%,#121022 55%,#09080f 100%)}
.tag{position:absolute;left:28px;bottom:20px;font-size:14px;color:#ffffff70;letter-spacing:.3px}
.step{position:absolute;left:28px;top:24px;font-size:15px;font-weight:700;color:#b9a8ff;letter-spacing:1.5px;text-transform:uppercase}
"""

TV_CSS = BASE_CSS + """
.left{position:absolute;left:80px;top:170px;width:470px}
.crumb{font-size:18px;color:#ffffff80;margin-bottom:14px}
.big{font-size:46px;font-weight:800;line-height:1.1}
.sub{font-size:19px;color:#ffffffa0;margin-top:16px;line-height:1.45}
.panel{position:absolute;right:0;top:0;width:560px;height:720px;background:#1b1a27;border-left:1px solid #ffffff14;padding:70px 36px}
.ptitle{font-size:26px;font-weight:700;margin-bottom:26px}
.item{display:flex;align-items:center;justify-content:space-between;padding:17px 22px;border-radius:14px;margin-bottom:6px;font-size:21px;color:#ffffffd0}
.item small{display:block;font-size:15px;color:#ffffff80;margin-top:4px}
.item.hl{background:#f2f2f6;color:#14131c;box-shadow:0 0 0 4px #8e7bff,0 10px 40px #8e7bff55}
.item.hl small{color:#14131caa}
.sw{width:50px;height:28px;border-radius:14px;background:#ffffff30;position:relative;flex:none}
.sw:after{content:'';position:absolute;width:22px;height:22px;border-radius:50%;background:#fff;top:3px;left:3px}
.sw.on{background:#7b5cff}.sw.on:after{left:25px}
.hl .sw{background:#14131c40}.hl .sw.on{background:#6b4dff}
.callout{position:absolute;background:#ffd166;color:#1a1400;font-weight:800;font-size:18px;padding:10px 16px;border-radius:12px;box-shadow:0 8px 30px #0008}
.callout:before{content:'';position:absolute;width:0;height:0;border:10px solid transparent}
.callout.r:before{right:-19px;top:50%;margin-top:-10px;border-left-color:#ffd166}
.dialog{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);width:620px;background:#232232;border-radius:24px;padding:40px;border:1px solid #ffffff22;box-shadow:0 30px 90px #000a}
.code{font-size:64px;font-weight:800;letter-spacing:12px;text-align:center;margin:20px 0;color:#fff}
.mono{font-family:'JetBrains Mono','DejaVu Sans Mono',Menlo,monospace}
.toast{position:absolute;left:80px;bottom:90px;background:#f2f2f6;color:#14131c;font-size:20px;font-weight:600;padding:14px 24px;border-radius:30px}
.badge{display:inline-block;background:#ffd166;color:#1a1400;border-radius:10px;padding:2px 10px;font-weight:800;font-size:16px;margin-left:10px}
"""

TERM_CSS = BASE_CSS + """
.win{position:absolute;left:90px;top:80px;width:1100px;height:560px;background:#0d0f14;border-radius:16px;border:1px solid #ffffff1c;box-shadow:0 30px 80px #000c;overflow:hidden}
.bar{height:42px;background:#1a1d25;display:flex;align-items:center;padding:0 16px;gap:8px}
.dot{width:13px;height:13px;border-radius:50%}
.bar span{margin-left:14px;color:#ffffff70;font-size:14px;font-family:'DejaVu Sans',sans-serif}
pre{font-family:'JetBrains Mono','DejaVu Sans Mono',Menlo,monospace;font-size:19px;line-height:1.62;padding:22px 26px;color:#d6dae3;white-space:pre-wrap}
.p{color:#7cf0a8}.c{color:#ffffff}.o{color:#9aa3b5}.ok{color:#7cf0a8;font-weight:700}.k{color:#ffd166}.cm{color:#6f7891}
"""

FLOW_CSS = BASE_CSS + """
h1{position:absolute;left:60px;top:46px;font-size:38px;font-weight:800}
h1 small{display:block;font-size:18px;color:#ffffff90;font-weight:500;margin-top:8px}
.grid{position:absolute;left:60px;right:60px;top:170px;display:grid;grid-template-columns:repeat(4,1fr);gap:22px}
.card{background:#ffffff0d;border:1px solid #ffffff1c;border-radius:22px;padding:22px;min-height:200px;position:relative}
.n{width:44px;height:44px;border-radius:50%;background:linear-gradient(135deg,#3a7bff,#7b5cff);display:flex;align-items:center;justify-content:center;font-weight:800;font-size:21px;margin-bottom:14px}
.card b{display:block;font-size:21px;margin-bottom:8px}
.card p{font-size:16px;color:#ffffffa8;line-height:1.45}
.where{position:absolute;right:16px;top:20px;font-size:13px;font-weight:700;padding:4px 10px;border-radius:20px}
.tv{background:#7b5cff33;color:#c9bcff}.pc{background:#3be07f26;color:#7cf0a8}
"""

def tv(step, crumb, big, sub, ptitle, items, extra="", tag="Temsili görünüm · menü adları cihaza ve sürüme göre biraz değişebilir"):
    rows = []
    for it in items:
        label, kind = it[0], it[1] if len(it) > 1 else ""
        small = it[2] if len(it) > 2 else ""
        sw = ""
        if "on" in kind: sw = '<div class="sw on"></div>'
        elif "off" in kind: sw = '<div class="sw"></div>'
        rows.append(f'<div class="item {"hl" if "hl" in kind else ""}"><div>{label}{f"<small>{small}</small>" if small else ""}</div>{sw}</div>')
    return f"""<html><head><style>{TV_CSS}</style></head><body>
<div class="step">{step}</div>
<div class="left"><div class="crumb">{crumb}</div><div class="big">{big}</div><div class="sub">{sub}</div></div>
<div class="panel"><div class="ptitle">{ptitle}</div>{''.join(rows)}</div>
{extra}<div class="tag">{tag}</div></body></html>"""

def term(step, title, body, tag="Örnek çıktı · IP ve port senin cihazında farklı olacak"):
    return f"""<html><head><style>{TERM_CSS}</style></head><body>
<div class="step">{step}</div>
<div class="win"><div class="bar"><div class="dot" style="background:#ff5f57"></div><div class="dot" style="background:#febc2e"></div><div class="dot" style="background:#28c840"></div><span>{title}</span></div>
<pre>{body}</pre></div><div class="tag">{tag}</div></body></html>"""

def flow(title, sub, cards):
    cs = "".join(f'<div class="card"><span class="where {w}">{"TV" if w=="tv" else "Bilgisayar" if w=="pc" else ""}</span><div class="n">{i+1}</div><b>{t}</b><p>{d}</p></div>' for i, (t, d, w) in enumerate(cards))
    return f"""<html><head><style>{FLOW_CSS}</style></head><body><h1>{title}<small>{sub}</small></h1><div class="grid">{cs}</div></body></html>"""

P = '<span class="p">$</span> '
pages = {}

pages["00-genel-akis"] = flow("LiteDroid kurulumu: 8 adım", "Toplam 15–20 dakika. Bir kez yapılır, sonraki güncellemeler kendiliğinden gelir.", [
    ("Bilgisayara adb kur", "Mac, Windows ya da Linux. Google'ın ücretsiz Platform Tools paketi.", "pc"),
    ("Geliştirici modunu aç", "Ayarlar → Sistem → Hakkında → derleme numarasına 7 kez bas.", "tv"),
    ("Kablosuz hata ayıklama", "Geliştirici seçeneklerinde aç; IP ve port burada yazar.", "tv"),
    ("Eşle ve bağlan", "adb pair ile bir kez eşle, adb connect ile bağlan.", "pc"),
    ("LiteDroid'i kur", "GitHub'dan son APK'yı indir, adb install ile kur.", "pc"),
    ("İzinleri ver", "Tek blok komut: güncelleme, medya, skor şeridi, mikrofon…", "pc"),
    ("Ana ekran yap", "Home tuşu LiteDroid'i açsın. Cihaza göre iki yol var.", "pc"),
    ("Güncellemeleri ayarla", "Otomatik kur, Sadece haber ver ya da Kapalı.", "tv"),
])

pages["01-hakkinda"] = tv("Adım 2 · Geliştirici modu", "Ayarlar › Sistem", "Hakkında",
    "<b>Android TV OS derlemesi</b> satırına gel ve kumandada <b>OK</b>'a art arda <b>7 kez</b> bas. "
    "Kilit PIN'i sorarsa gir.",
    "Hakkında",
    [("Cihaz adı", "", "Oturma odası TV"), ("Yazılım güncellemesi",), ("Yeniden başlat",), ("Durum", "", "IP adresi, Wi-Fi"),
     ("Model", "", "Chromecast with Google TV"), ("Android TV OS sürümü", "", "14"),
     ("Android TV OS derlemesi", "hl", "UTTK.250405.001 ← 7 kez OK")],
    extra='<div class="toast">Artık bir geliştiricisiniz! <span class="badge">7×</span></div>')

pages["02-gelistirici"] = tv("Adım 3 · Kablosuz hata ayıklama", "Ayarlar › Sistem", "Geliştirici seçenekleri",
    "Hakkında'dan geri çıkınca <b>Sistem</b> menüsünün altında belirir. <b>USB hata ayıklama</b>'yı aç, sonra "
    "<b>Kablosuz hata ayıklama</b>'ya gir.",
    "Geliştirici seçenekleri",
    [("Geliştirici seçeneklerini kullan", "on"), ("Uyanık kal", "off"), ("USB hata ayıklama", "on", "Onay sorarsa: Tamam"),
     ("Kablosuz hata ayıklama", "hl on", "Aç ve içine gir"), ("Hata ayıklama yetkilerini iptal et",), ("Animasyon ölçeği", "", "1x")])

pages["03-kablosuz"] = tv("Adım 3 · IP ve port", "Geliştirici seçenekleri", "Kablosuz hata ayıklama",
    "Burada iki farklı port var: <b>bağlantı portu</b> (her zaman görünür) ve <b>eşleme portu</b> "
    "(eşleme penceresinde, sadece o pencere açıkken çalışır).",
    "Kablosuz hata ayıklama",
    [("Kablosuz hata ayıklamayı kullan", "on"), ("Cihaz adı", "", "Oturma odası TV"),
     ("IP adresi ve bağlantı noktası", "hl", "192.168.1.50:41235"),
     ("Cihazı eşleme koduyla eşle", "", "6 haneli kod + eşleme portu"), ("Eşlenmiş cihazlar", "", "Henüz yok")],
    extra='<div class="callout r" style="left:300px;top:450px">adb connect için bu adres →</div>')

pages["04-eslesme"] = tv("Adım 4 · Eşleme kodu", "", "",
    "", "Kablosuz hata ayıklama",
    [("Kablosuz hata ayıklamayı kullan", "on"), ("IP adresi ve bağlantı noktası", "", "192.168.1.50:41235"), ("Cihazı eşleme koduyla eşle", "hl")],
    extra="""<div class="dialog"><div style="font-size:24px;font-weight:700">Cihazla eşle</div>
<div style="color:#ffffffa0;margin-top:8px;font-size:17px">Wi-Fi eşleme kodu</div>
<div class="code mono">482913</div>
<div style="color:#ffffffa0;font-size:17px">IP adresi ve bağlantı noktası</div>
<div class="mono" style="font-size:30px;font-weight:700;margin-top:6px;color:#ffd166">192.168.1.50:37119</div>
<div style="margin-top:22px;font-size:16px;color:#ffffff90">Bu pencereyi açık tut. Kod ve port, pencere kapanınca geçersiz olur.</div></div>
<div class="callout" style="left:700px;top:560px">adb pair için bu adres ve kod</div>""")

pages["05-adb-baglan"] = term("Adım 4 · Eşle ve bağlan", "Terminal",
    f"""<span class="cm"># 1) Sadece ilk sefer: eşleme penceresindeki adres ve kod</span>
{P}<span class="c">adb pair 192.168.1.50:37119</span>
<span class="o">Enter pairing code: </span><span class="k">482913</span>
<span class="ok">Successfully paired to 192.168.1.50:37119 [guid=adb-…]</span>

<span class="cm"># 2) Bağlan: Kablosuz hata ayıklama ekranındaki "IP adresi ve bağlantı noktası"</span>
{P}<span class="c">adb connect 192.168.1.50:41235</span>
<span class="ok">connected to 192.168.1.50:41235</span>

<span class="cm"># 3) Kontrol</span>
{P}<span class="c">adb devices</span>
<span class="o">List of devices attached
192.168.1.50:41235     device</span>""")

pages["06-adb-kur"] = term("Adım 5 · LiteDroid'i kur", "Terminal",
    f"""<span class="cm"># GitHub → Releases → son sürümdeki LiteDroid-X.Y.Z.apk dosyasını indir</span>
{P}<span class="c">cd ~/Downloads</span>
{P}<span class="c">adb install -r LiteDroid-1.9.3.apk</span>
<span class="o">Performing Streamed Install</span>
<span class="ok">Success</span>

<span class="cm"># Birden fazla cihaz bağlıysa hangisine kurulacağını -s ile söyle</span>
{P}<span class="c">adb -s 192.168.1.50:41235 install -r LiteDroid-1.9.3.apk</span>
<span class="ok">Success</span>""")

pages["07-adb-izinler"] = term("Adım 6 · İzinler", "Terminal · izinler (hepsini birden yapıştırabilirsin)",
    f"""{P}<span class="c">P=dev.litedroid.launcher</span>
{P}<span class="c">adb shell appops set $P REQUEST_INSTALL_PACKAGES allow</span>      <span class="cm"># güncelleme</span>
{P}<span class="c">adb shell cmd notification allow_listener $P/$P.media.MediaListenerService</span>
{P}<span class="c">adb shell appops set $P GET_USAGE_STATS allow</span>               <span class="cm"># son kullanılanlar</span>
{P}<span class="c">adb shell appops set $P SYSTEM_ALERT_WINDOW allow</span>           <span class="cm"># skor şeridi</span>
{P}<span class="c">adb shell pm grant $P android.permission.RECORD_AUDIO</span>      <span class="cm"># mikrofon</span>
{P}<span class="c">adb shell pm grant $P android.permission.WRITE_SECURE_SETTINGS</span>
{P}<span class="c">adb shell appops set $P WRITE_SETTINGS allow</span>
{P}<span class="c">adb shell settings put secure enabled_accessibility_services \\
    $P/$P.overlay.RemoteKeyService</span>
{P}<span class="c">adb shell settings put secure accessibility_enabled 1</span>
<span class="cm"># Hata yazmıyorsa tamam. Komutların çoğu başarılı olunca hiçbir şey yazmaz.</span>""",
    tag="Komutlar sessizdir: çıktı yoksa başarılı demektir")

pages["08-bilinmeyen"] = tv("adb olmadan · güncelleme izni", "Ayarlar › Uygulamalar › Güvenlik ve kısıtlamalar", "Bilinmeyen kaynaklar",
    "LiteDroid'in kendi güncellemesini kurabilmesi için listede <b>LiteDroid Launcher</b>'ı aç. "
    "adb ile <span class='mono'>REQUEST_INSTALL_PACKAGES</span> verdiysen zaten açıktır.",
    "Bilinmeyen kaynaklar",
    [("Downloader", "off"), ("Files by Google", "off"), ("LiteDroid Launcher", "hl on", "Güncellemeleri kurabilir"), ("Send Files to TV", "off")])

pages["09-erisilebilirlik"] = tv("adb olmadan · kumanda kısayolu", "Ayarlar › Sistem", "Erişilebilirlik",
    "Yüzen menü, uyku zamanlayıcısı ve Home yönlendirme bu servisle çalışır. Listede görünüyorsa buradan açabilirsin; "
    "görünmüyorsa adb komutu yeterli.",
    "Erişilebilirlik",
    [("Altyazılar",), ("Yüksek kontrastlı metin", "off"), ("Metin okuma",), ("Erişilebilirlik kısayolu",),
     ("LiteDroid kumanda kısayolu", "hl on", "Açık"), ("TalkBack", "off")])

pages["10-guncelleme"] = flow("Güncellemeler nasıl gelir", "LiteDroid yarım saatte bir GitHub'daki son sürüme bakar. Ayarlar → Diğer → Güncellemeler.", [
    ("Yeni sürüm hazır", "Otomatik kur modunda ekrana soru gelir: Şimdi güncelle · 1 saat · 3 saat sonra.", "tv"),
    ("İndirme ve kurulum", "Tam ekran ilerleme. Geri ile köşeye küçülür, çan menüsünden geri açılır.", "tv"),
    ("İlk seferde onay", "Android bir kez \"Güncelle\" onayı ister. Play Protect uyarırsa \"Yine de yükle\".", "tv"),
    ("Güncellendi!", "LiteDroid kendiliğinden yeniden açılır ve yenilikleri gösterir.", "tv"),
])

with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page(viewport={"width": 1280, "height": 720}, device_scale_factor=1)
    for name, html in pages.items():
        pg.set_content(html)
        pg.wait_for_timeout(80)
        pg.screenshot(path=str(OUT / f"{name}.png"))
        print("ok", name)
    b.close()
