#!/usr/bin/env bash
set -euo pipefail

ZIP_PATH="${1:-design-source/yamone-v23.zip}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET="$ROOT/app/src/main/assets/yamone-v23"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

if [ ! -f "$ZIP_PATH" ]; then
  echo "Yamone v0.00.23 design zip not found: $ZIP_PATH" >&2
  exit 2
fi

unzip -q "$ZIP_PATH" -d "$TMP"
SOURCE_DIR="$(find "$TMP" -type f -name index.html -print -quit | xargs -r dirname)"
if [ -z "$SOURCE_DIR" ] || [ ! -f "$SOURCE_DIR/app.js" ] || [ ! -f "$SOURCE_DIR/styles.css" ]; then
  echo "Invalid design zip: index.html/app.js/styles.css not found" >&2
  exit 3
fi

rm -rf "$TARGET"
mkdir -p "$TARGET"
cp -R "$SOURCE_DIR"/. "$TARGET"/
rm -f "$TARGET/asset-audit.png" "$TARGET/move-assets-audit.png" || true

cp "$ROOT/design-preview/mobile.css" "$TARGET/mobile.css"
cp "$ROOT/design-preview/app-mobile.js" "$TARGET/app-mobile.js"

python3 - "$TARGET/index.html" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text(encoding='utf-8')
if 'name="viewport"' not in s:
    s = s.replace('<meta charset="utf-8">', '<meta charset="utf-8">\n  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, viewport-fit=cover">', 1)
if 'mobile.css' not in s:
    s = s.replace('</head>', '  <link rel="stylesheet" href="mobile.css">\n</head>', 1)
if 'app-mobile.js' not in s:
    s = s.replace('</body>', '  <script src="app-mobile.js"></script>\n</body>', 1)
p.write_text(s, encoding='utf-8')
PY

echo "Imported Yamone v0.00.23 design into: $TARGET"
echo "Source: $ZIP_PATH"
