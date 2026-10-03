#!/usr/bin/env bash
# Regenerates every textbeeqtt icon from branding/bee.png.
# Needs: python3 with numpy, scipy and pillow; ImageMagick (magick); cwebp.
#   python3 -m venv .venv && .venv/bin/pip install numpy scipy pillow
#   PYTHON=.venv/bin/python branding/generate-icons.sh
set -euo pipefail

cd "$(dirname "$0")/.."
PY=${PYTHON:-python3}
B=branding
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT

compose() { "$PY" $B/compose.py "$@"; }
webp() { cwebp -quiet -lossless "$1" -o "$2"; }

# masters
compose $B/icon-master.png
"$PY" $B/masks.py $B/icon-master.png $B/icon
compose "$T/fav-master.png" --width 0.85
"$PY" $B/masks.py "$T/fav-master.png" "$T/fav"

# web: circular everywhere, Apple applies its own mask
W=web
magick "$T/fav-circle.png" -define icon:auto-resize=48,32,16 $W/public/favicon.ico
magick $B/icon-circle.png -resize 512x512 $W/app/icon.png
magick $B/icon-master.png -resize 180x180 $W/app/apple-icon.png
magick $B/icon-circle.png -resize 512x512 $W/public/images/logo.png

# android adaptive layers (108dp canvas, 72dp visible, 66dp safe zone)
compose "$T/fg.png" --mode fg --width 0.44
compose "$T/bg.png" --mode bg --hex-scale 0.67
compose "$T/mono.png" --mode mono --width 0.44
compose "$T/stat.png" --mode mono --width 0.92 --size 512

R=android/app/src/main/res
for pair in mdpi:1 hdpi:1.5 xhdpi:2 xxhdpi:3 xxxhdpi:4; do
  d=${pair%%:*}; k=${pair##*:}
  icon=$(awk "BEGIN{print int(48*$k)}")
  layer=$(awk "BEGIN{print int(108*$k)}")
  stat=$(awk "BEGIN{print int(24*$k)}")
  magick $B/icon-squircle.png -resize ${icon}x${icon} "$T/l.png"; webp "$T/l.png" $R/mipmap-$d/ic_launcher.webp
  magick $B/icon-circle.png -resize ${icon}x${icon} "$T/r.png"; webp "$T/r.png" $R/mipmap-$d/ic_launcher_round.webp
  for layer_name in fg:foreground bg:background mono:monochrome; do
    magick "$T/${layer_name%%:*}.png" -resize ${layer}x${layer} "$T/a.png"
    webp "$T/a.png" $R/mipmap-$d/ic_launcher_${layer_name##*:}.webp
  done
  mkdir -p $R/drawable-$d
  magick "$T/stat.png" -resize ${stat}x${stat} $R/drawable-$d/ic_stat_bee.png
done

# play store wants a full-bleed square; it applies the mask itself
magick $B/icon-master.png -resize 512x512 android/app/src/main/ic_launcher-playstore.png
magick $B/icon-circle.png -resize 384x384 "$T/logo.png"; webp "$T/logo.png" $R/drawable/ic_app_logo.webp

echo "icons regenerated"
