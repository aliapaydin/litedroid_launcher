#!/usr/bin/env python3
"""Sürüm notlarını uygulamanın kendi listesinden (Changelog.kt) Türkçe olarak çıkarır.

Kullanım: python3 tools/release_notes.py 1.8.0  > notes.md
Çıktı: "## Başlık" ve "- madde" satırları; LiteDroid güncelleme penceresinde bu biçimi okur.
"""
import re
import sys
from pathlib import Path

version = sys.argv[1].lstrip("v")
src = Path(__file__).resolve().parent.parent / "app/src/main/java/dev/litedroid/launcher/data/Changelog.kt"
text = src.read_text(encoding="utf-8")

start = text.find(f'Release(\n                "{version}",')
if start < 0:
    m = re.search(r'Release\(\s*"' + re.escape(version) + r'"', text)
    if not m:
        sys.exit(f"Changelog.kt içinde {version} bulunamadı")
    start = m.start()
end = text.find("\n            Release(", start + 10)
block = text[start:end if end > 0 else len(text)]

# tx("Türkçe", "English") çağrılarından Türkçe olanı al (kaçışlı tırnaklar dahil)
strings = re.findall(r'tx\(\s*"((?:[^"\\]|\\.)*)"', block)
strings = [s.replace('\\"', '"').replace("\\\\", "\\") for s in strings]
if not strings:
    sys.exit("sürüm notu bulunamadı")
print(f"## {strings[0]}\n")
for s in strings[1:]:
    print(f"- {s}")
