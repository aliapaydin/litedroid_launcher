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

# Yalnızca güncel main'den yayınlanır (birleştirilmemiş ya da çekilmemiş kod yayına girmesin)
BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [[ "$BRANCH" != "main" ]]; then
  echo "Şu an '$BRANCH' dalındasın. Önce: git checkout main && git pull"
  exit 1
fi
git fetch -q origin main
if [[ "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ]]; then
  echo "main GitHub'dakiyle aynı değil. Önce: git pull"
  exit 1
fi

if gh release view "$TAG" >/dev/null 2>&1; then
  echo "$TAG zaten yayınlanmış. Önce versionName ve versionCode'u artır."
  exit 1
fi

# Notlar derlemeden önce hazırlanır: Changelog.kt'de sorun varsa derlemeyi beklemeden durur
APK="LiteDroid-$VERSION.apk"
OUT=$(mktemp -d)
python3 tools/release_notes.py "$VERSION" > "$OUT/notes.md"

# Gradle 8.11 Java 17-21 ister. Kabuktaki JAVA_HOME başka bir sürümü gösteriyorsa (ör. Android Studio
# güncellenince gelen Java 25) yok sayılır, bu Mac'teki Java 17 (yoksa 21) kullanılır.
java_major() { "$1/bin/java" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/'; }
if [[ -z "$JAVA_HOME" || ! -x "$JAVA_HOME/bin/java" || ! "$(java_major "$JAVA_HOME")" =~ ^(17|18|19|20|21)$ ]]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 21 2>/dev/null || true)
fi
if [[ -z "$JAVA_HOME" ]]; then
  echo "Java 17 bulunamadı. Kur: brew install --cask zulu@17"
  exit 1
fi
export JAVA_HOME
echo "Java: $(java_major "$JAVA_HOME") ($JAVA_HOME)"

./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk "$OUT/$APK"

echo "--- Notlar ---"
cat "$OUT/notes.md"
echo "--------------"

gh release create "$TAG" "$OUT/$APK" --title "LiteDroid $VERSION" --notes-file "$OUT/notes.md"
echo "Yayınlandı: $TAG. TV'ler yarım saat içinde görür (Ayarlar → Diğer → Güncellemeleri denetle ile hemen)."
