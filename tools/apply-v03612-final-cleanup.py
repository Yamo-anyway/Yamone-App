#!/usr/bin/env python3
from pathlib import Path

R = Path(__file__).resolve().parent.parent
BUILD = R / 'app/build.gradle'
ASSETS = R / 'app/src/main/assets/yamone-v23'
DATASET_JS = ASSETS / 'v03610-dataset.js'

s = BUILD.read_text(encoding='utf-8')
s = s.replace("versionCode 105", "versionCode 106").replace("versionName '0.36.11'", "versionName '0.36.12'")
BUILD.write_text(s, encoding='utf-8')

s = DATASET_JS.read_text(encoding='utf-8')
s = s.replace("const V='0.36.11'", "const V='0.36.12'")
DATASET_JS.write_text(s, encoding='utf-8')

# The imported v23 UI still contains its historical 0.00.23 display string.
# Replace every rendered asset copy after all earlier patches so home/settings/app-info
# cannot redraw the stale version after the v0.36.x overlay updates it.
for p in ASSETS.rglob('*'):
    if not p.is_file() or p.suffix.lower() not in {'.js', '.html', '.css'}:
        continue
    text = p.read_text(encoding='utf-8')
    new = text.replace('0.00.23', '0.36.12')
    if new != text:
        p.write_text(new, encoding='utf-8')

print('Applied v0.36.12 final Mac mini dataset cleanup and stable version display.')
