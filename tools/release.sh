#!/bin/zsh
# release.sh — yeni sürümü derleyip GitHub Releases'e yükler. TV'lerdeki LiteDroid buradan güncellenir.
#
# Gerekenler (bir kez):  brew install gh  &&  gh auth login
# Kullanım:             ./tools/release.sh
#
# Sürüm numarası app/build.gradle.kts'teki versionName'den, notlar Changelog.kt'den alınır.
# APK bu bilgisayarın imza anahtarıyla imzalanır; TV'lerdeki sürüm de bu bilgisayarda derlendiği
# için güncelleme olarak kabul edilir. Başka bir bilgisayardan yayınlama.
set -e
cd "$(dirname "$0")/.."

VERSION=$(grep -m1 'versionName' app/build.gradle.kts | sed -E 's/.*"(.*)".*/\1/')
TAG="v$VERSION"
echo "Sürüm: $TAG"

if gh release view "$TAG" >/dev/null 2>&1; then
  echo "$TAG zaten yayınlanmış. Önce versionName ve versionCode'u artır."
  exit 1
fi

export JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}
./gradlew assembleRelease

APK="LiteDroid-$VERSION.apk"
OUT=$(mktemp -d)
cp app/build/outputs/apk/release/app-release.apk "$OUT/$APK"
python3 tools/release_notes.py "$VERSION" > "$OUT/notes.md"

echo "--- Notlar ---"
cat "$OUT/notes.md"
echo "--------------"

gh release create "$TAG" "$OUT/$APK" --title "LiteDroid $VERSION" --notes-file "$OUT/notes.md"
echo "Yayınlandı: $TAG. TV'ler yarım saat içinde görür (Ayarlar → Diğer → Güncellemeleri denetle ile hemen)."
