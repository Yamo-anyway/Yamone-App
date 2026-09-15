#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TARGET = ROOT / 'app/src/main/assets/yamone-v23'
ASSETS = TARGET / 'assets'
INDEX = TARGET / 'index.html'
BUILD = ROOT / 'app/build.gradle'


def add_before(path: Path, marker: str, text: str, label: str):
    s = path.read_text(encoding='utf-8')
    if text.strip() in s:
        return
    if marker not in s:
        raise SystemExit(f'v0.36.02 insertion target not found: {label} ({path})')
    path.write_text(s.replace(marker, text + marker, 1), encoding='utf-8')


def replace_once(path: Path, old: str, new: str, label: str):
    s = path.read_text(encoding='utf-8')
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'v0.36.02 patch target not found: {label} ({path})')
    path.write_text(s.replace(old, new, 1), encoding='utf-8')


ASSETS.mkdir(parents=True, exist_ok=True)
for name in ['autodetect-sound-v03602.svg', 'autodetect-vibrate-v03602.svg']:
    (ASSETS / name).write_bytes((ROOT / 'design-preview/assets' / name).read_bytes())

for name in ['v03602-autodetect-assets.css', 'v03602-autodetect-assets.js', 'v03602-version.js']:
    (TARGET / name).write_bytes((ROOT / 'design-preview' / name).read_bytes())

add_before(INDEX, '</head>', '  <link rel="stylesheet" href="v03602-autodetect-assets.css">\n', 'auto-detect assets css')

s = INDEX.read_text(encoding='utf-8')
s = s.replace('  <script src="v03601-version.js"></script>\n', '').replace('<script src="v03601-version.js"></script>\n', '')
INDEX.write_text(s, encoding='utf-8')
add_before(INDEX, '</body>', '  <script src="v03602-autodetect-assets.js"></script>\n  <script src="v03602-version.js"></script>\n', 'auto-detect assets js')

replace_once(BUILD, "        versionCode 95\n        versionName '0.36.01'", "        versionCode 96\n        versionName '0.36.02'", 'android version')

print('Applied v0.36.02 auto-detect sound/vibration assets.')
