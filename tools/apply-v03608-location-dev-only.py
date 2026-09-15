#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TARGET = ROOT / 'app/src/main/assets/yamone-v23'
INDEX = TARGET / 'index.html'
BUILD = ROOT / 'app/build.gradle'


def replace_once(path: Path, old: str, new: str, label: str):
    s = path.read_text(encoding='utf-8')
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'v0.36.08 target not found: {label} ({path})')
    path.write_text(s.replace(old, new, 1), encoding='utf-8')

# The v0.36.07 developer-mode source is copied by the previous patch step.
# v0.36.08 extends that same gate so the Activity > 위치공유 entry is visible
# only while developer mode is enabled.
dev = TARGET / 'v03607-developer-mode.js'
if not dev.is_file():
    raise SystemExit('v0.36.08 developer-mode script missing')
text = dev.read_text(encoding='utf-8')
for marker in ['gateLocationSharing', '위치공유', 'v3608DeveloperOnlyLocation']:
    if marker not in text:
        raise SystemExit(f'v0.36.08 location developer gate missing: {marker}')

version_src = ROOT / 'design-preview/v03608-version.js'
if not version_src.is_file():
    raise SystemExit('v0.36.08 version source missing')
(TARGET / 'v03608-version.js').write_bytes(version_src.read_bytes())

s = INDEX.read_text(encoding='utf-8')
s = s.replace('  <script src="v03607-version.js"></script>\n', '')
s = s.replace('<script src="v03607-version.js"></script>\n', '')
if 'v03608-version.js' not in s:
    if '</body>' not in s:
        raise SystemExit('v0.36.08 index body missing')
    s = s.replace('</body>', '  <script src="v03608-version.js"></script>\n</body>', 1)
INDEX.write_text(s, encoding='utf-8')

replace_once(
    BUILD,
    "        versionCode 101\n        versionName '0.36.07'",
    "        versionCode 102\n        versionName '0.36.08'",
    'android version')

print('Applied v0.36.08 developer-only location sharing entry.')
