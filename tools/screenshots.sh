#!/bin/bash
# Makes the README screenshots from the real app (docs/index.html) in demo mode: seven made-up cities in seven
# different weathers, with the clock stopped so each one shows its own time of day. Then a banner with five of them
# in phone frames. Nothing is fetched or saved: your own browser data and your phone aren't touched.
#
#   tools/screenshots.sh          English
#   tools/screenshots.sh pl       Polish (any of the 33 language codes)
#   tools/screenshots.sh banner   only remake the banner from the screenshots already there

cd "$(dirname "$0")/.."
LANG_CODE="${1:-en}"
BANNER_ONLY=0; [ "$LANG_CODE" = banner ] && { BANNER_ONLY=1; LANG_CODE=en; }
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
[ -x "$CHROME" ] || { echo "Google Chrome isn't in /Applications. Install it, or change CHROME in this file."; exit 1; }
ROOT="$(pwd)"
mkdir -p screenshots

shoot() { # url, file, width, height, pixel scale
  # Headless Chrome sometimes writes the picture and then never quits, so each try gets 20 seconds and
  # is then stopped (with all its helper processes); a missing picture gets one more try.
  local out="$ROOT/screenshots/$2" try i prof pid
  for try in 1 2; do
    rm -f "$out"
    prof="$(mktemp -d)"
    "$CHROME" --headless --disable-gpu --hide-scrollbars --no-first-run --no-default-browser-check --allow-file-access-from-files \
      --user-data-dir="$prof" --force-device-scale-factor="$5" --window-size="$3,$4" \
      --virtual-time-budget=7000 --screenshot="$out" "$1" >/dev/null 2>&1 &
    pid=$!
    for i in $(seq 1 40); do kill -0 "$pid" 2>/dev/null || break; sleep 0.5; done
    pkill -9 -f "$prof" 2>/dev/null; wait "$pid" 2>/dev/null
    rm -rf "$prof"
    if [ -s "$out" ]; then echo "  screenshots/$2"; return; fi
  done
  echo "  screenshots/$2 FAILED"
}

# each phone screen is 390 × 844 points at 3× (1170 × 2532 px, an iPhone-sized screenshot), via tools/phone.html
# file:what to show. Cities: 0 Gliwice rain, 1 Singapore storm, 2 Reykjavík snow, 3 Dubai sandstorm,
# 4 Tokyo clear night, 5 Cape Town sun, 6 San Francisco fog. &screen= opens a sheet, &at= scrolls to a card.
echo "Phone screens ($LANG_CODE):"
for pair in "rain:city=0" "storm:city=1" "snow:city=2" "sand:city=3" "night:city=4" "fog:city=6" \
            "week:city=5&at=%23chartBox" "tips:city=5&at=.sunmoon" "parts:city=0&screen=parts" "menu:city=4&screen=menu"; do
  [ "$BANNER_ONLY" = 1 ] && break
  shoot "file://$ROOT/tools/phone.html?demo&${pair#*:}&lang=$LANG_CODE" "${pair%%:*}.png" 780 1688 1.5
done

echo "Banner:"
shoot "file://$ROOT/tools/banner.html" "banner.png" 1280 720 2
echo "Done. Open the folder with: open screenshots"
