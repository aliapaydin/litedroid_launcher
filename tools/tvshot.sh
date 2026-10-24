#!/bin/zsh
# tvshot.sh — TV'deki anlık ekranın görüntüsünü Mac'e kaydeder.
# Kullanım:
#   ./tvshot.sh            tek ekran görüntüsü
#   ./tvshot.sh 5          5 tane, aralarında 2 saniye
#   ./tvshot.sh 5 1        5 tane, aralarında 1 saniye

DEVICE="${TV:-192.168.0.40:45299}"
OUT_DIR="${OUT_DIR:-$HOME/litedroid-screenshots}"
COUNT="${1:-1}"
GAP="${2:-2}"

mkdir -p "$OUT_DIR"

# Bağlı değilse bağlan
if ! adb devices | grep -q "^$DEVICE[[:space:]]*device"; then
  adb connect "$DEVICE" >/dev/null
fi

for i in $(seq 1 "$COUNT"); do
  FILE="$OUT_DIR/litedroid_$(date +%Y%m%d_%H%M%S).png"
  if adb -s "$DEVICE" exec-out screencap -p > "$FILE" && [ -s "$FILE" ]; then
    echo "Kaydedildi: $FILE"
    LAST="$FILE"
  else
    echo "Ekran görüntüsü alınamadı (TV açık ve bağlı mı?)"
    rm -f "$FILE"
  fi
  [ "$i" -lt "$COUNT" ] && sleep "$GAP"
done

# Sonuncuyu Önizleme'de aç
[ -n "$LAST" ] && open "$LAST"
