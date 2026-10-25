# Kullanım: python3 tools/guide_pdf.py docs/KURULUM.md docs/LiteDroid-Kurulum-Rehberi.pdf
import re, sys, pathlib, markdown
from playwright.sync_api import sync_playwright

md_path = pathlib.Path(sys.argv[1]).resolve()
out_pdf = pathlib.Path(sys.argv[2]).resolve()
docs = md_path.parent
md = md_path.read_text()

# PDF'te tüm <details> açık, içindekiler bağlantıları çalışır kalsın
md = md.replace("<details open>", "<details>").replace("<details>", "<details open markdown=\"1\">")
md = re.sub(r"<summary>(.*?)</summary>", r"<summary>\1</summary>\n", md)
# İlk başlık kapakta
md = md.split("\n", 1)[1]

html_body = markdown.markdown(md, extensions=["tables", "fenced_code", "toc", "md_in_html", "sane_lists"])

css = """
@page{size:A4;margin:16mm 15mm 18mm 15mm}
@page:first{margin:0}
*{box-sizing:border-box}
body{font-family:'Inter','Segoe UI','DejaVu Sans',Arial,sans-serif;font-size:10.6pt;line-height:1.55;color:#1d1b29}
.cover{display:flex;flex-direction:column;justify-content:center;padding:0 6mm;page-break-after:always;
 background:radial-gradient(800px 500px at 20% 15%,#2a2350 0%,#121022 60%,#09080f 100%);color:#fff;height:295mm;overflow:hidden;padding:30mm 22mm}
.cover .k{color:#b9a8ff;font-weight:800;letter-spacing:3px;font-size:11pt}
.cover h1{font-size:40pt;line-height:1.05;margin:10px 0 14px;font-weight:800;border:0;color:#fff}
.cover p{font-size:13pt;color:#ffffffc0;max-width:150mm}
.cover img{width:100%;border-radius:12px;margin-top:22px;border:1px solid #ffffff22}
.cover .meta{margin-top:22px;font-size:10pt;color:#ffffff80}
h2{font-size:17pt;margin:26px 0 8px;padding-top:6px;color:#2b1f7a;border-bottom:2px solid #e6e1ff;padding-bottom:5px;page-break-after:avoid}
h3{font-size:12.5pt;margin:16px 0 6px;color:#3a2f8f;page-break-after:avoid}
img{max-width:100%;border-radius:10px;margin:8px 0;page-break-inside:avoid;box-shadow:0 2px 10px #0002}
pre{background:#12131a;color:#e7e9f0;border-radius:9px;padding:11px 13px;font-size:8.6pt;line-height:1.5;white-space:pre-wrap;word-break:break-all;page-break-inside:avoid}
code{font-family:'DejaVu Sans Mono',Menlo,monospace;font-size:8.8pt;background:#f0eefb;padding:1px 4px;border-radius:4px}
pre code{background:none;padding:0;font-size:inherit}
table{border-collapse:collapse;width:100%;margin:8px 0;font-size:9.4pt;page-break-inside:avoid}
th,td{border:1px solid #e1def0;padding:6px 8px;vertical-align:top;text-align:left}
th{background:#f4f2ff}
blockquote{margin:10px 0;padding:8px 14px;background:#fff8e1;border-left:4px solid #ffc94a;border-radius:6px;color:#3d3420}
blockquote p{margin:4px 0}
details{margin:8px 0;padding:6px 12px;border:1px solid #e1def0;border-radius:8px;page-break-inside:avoid}
summary{font-weight:700;color:#3a2f8f;list-style:none}
summary::-webkit-details-marker{display:none}
a{color:#5a3fe0;text-decoration:none}
hr{display:none}
sub{font-size:8.5pt;color:#777}
"""

cover = f"""<div class="cover"><div class="k">LITEDROID LAUNCHER</div>
<h1>Kurulum ve<br>Güncelleme Rehberi</h1>
<p>Android TV ve Google TV için sıfırdan, adım adım: geliştirici modu, IP ve port, eşleme, izinler, ana ekran ve otomatik güncellemeler.</p>
<img src="kurulum/00-genel-akis.png">
<div class="meta">github.com/aliapaydin/litedroid_launcher · docs/KURULUM.md · Ekim 2026</div></div>"""

html = f"<!doctype html><html lang='tr'><head><meta charset='utf-8'><base href='{docs.as_uri()}/'><style>{css}</style></head><body>{cover}{html_body}</body></html>"
tmp = docs / "_kurulum_pdf.html"
tmp.write_text(html)
with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page()
    pg.goto(tmp.as_uri())
    pg.wait_for_timeout(400)
    pg.pdf(path=str(out_pdf), format="A4", print_background=True,
           display_header_footer=True, header_template="<span></span>",
           footer_template="<div style='font-size:8px;color:#999;width:100%;text-align:center'>LiteDroid Kurulum Rehberi · <span class='pageNumber'></span>/<span class='totalPages'></span></div>", prefer_css_page_size=True)
    b.close()
tmp.unlink()
print("pdf ok", out_pdf)
