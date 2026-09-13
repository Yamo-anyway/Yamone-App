#!/usr/bin/env bash
set -euo pipefail

ZIP_PATH="${1:-design-source/yamone-v23.zip}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET="$ROOT/app/src/main/assets/yamone-v23"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

if [ ! -f "$ZIP_PATH" ]; then
  echo "Yamone base design zip not found: $ZIP_PATH" >&2
  exit 2
fi

unzip -q "$ZIP_PATH" -d "$TMP"
SOURCE_DIR="$(find "$TMP" -type f -name index.html -print -quit | xargs -r dirname)"
if [ -z "$SOURCE_DIR" ] || [ ! -f "$SOURCE_DIR/app.js" ] || [ ! -f "$SOURCE_DIR/styles.css" ]; then
  echo "Invalid design zip: index.html/app.js/styles.css not found" >&2
  exit 3
fi

rm -rf "$TARGET"
mkdir -p "$TARGET/assets"
cp -R "$SOURCE_DIR"/. "$TARGET"/
rm -f "$TARGET/asset-audit.png" "$TARGET/move-assets-audit.png" || true

# Keep approved v24/v25 design patches. v0.25.10 keeps the real recorder from
# v0.25.08, prevents full Summary View rerenders, and pins live controls to bottom.
for file in mobile.css app-mobile.js v02403.css v02501-ui.js v02503-summary.css v02505-summary.css v02505-summary.js v02507-fit.css v02507-move.css v02507-move.js v02507-fit.js v02501-navigation.js v02508-real-move.css v02508-real-move.js v02509-ui.css v02510-ui.css v02510-ui.js v02510-version.js; do
  cp "$ROOT/design-preview/$file" "$TARGET/$file"
done
for file in title-activity-v02402.png title-records-v02402.png title-alarm-v02402.png title-settings-v02402.png back-v02402.png summary-view-button-v02503.svg title-summary-walk-v02503.svg title-summary-run-v02503.svg title-summary-bike-v02503.svg; do
  cp "$ROOT/design-preview/assets/$file" "$TARGET/assets/$file"
done

# v0.25.08 used to rebuild the entire Summary View every second. Replace only
# that polling branch so values are updated in place by v02510-ui.js instead.
python3 - "$TARGET/v02508-real-move.js" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text(encoding='utf-8')
old = "}else if(view==='move'&&moveState==='glance'&&s.recording){realGlance();}"
new = "}else if(view==='move'&&moveState==='glance'&&s.recording){if(window.v02510UpdateGlance)window.v02510UpdateGlance(s);}"
if old not in s:
    raise SystemExit('v0.25.10 glance polling patch target not found')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
PY

python3 - "$TARGET/index.html" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text(encoding='utf-8')
if 'name="viewport"' not in s:
    s = s.replace('<meta charset="utf-8">', '<meta charset="utf-8">\n  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, viewport-fit=cover">', 1)
for css in ['mobile.css', 'v02403.css', 'v02503-summary.css', 'v02505-summary.css', 'v02507-fit.css', 'v02507-move.css', 'v02508-real-move.css', 'v02509-ui.css', 'v02510-ui.css']:
    if f'href="{css}"' not in s:
        s = s.replace('</head>', f'  <link rel="stylesheet" href="{css}">\n</head>', 1)
for js in ['app-mobile.js', 'v02501-ui.js', 'v02505-summary.js', 'v02507-move.js', 'v02507-fit.js', 'v02501-navigation.js', 'v02508-real-move.js', 'v02510-ui.js', 'v02510-version.js']:
    if f'src="{js}"' not in s:
        s = s.replace('</body>', f'  <script src="{js}"></script>\n</body>', 1)
p.write_text(s, encoding='utf-8')
PY

echo "Imported approved design with v0.25.10 stable Summary View + bottom live controls into: $TARGET"
