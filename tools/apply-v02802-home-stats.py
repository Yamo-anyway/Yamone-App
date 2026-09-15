#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TARGET = ROOT / 'app/src/main/assets/yamone-v23'


def add_before(path: Path, marker: str, text: str, label: str):
    s = path.read_text(encoding='utf-8')
    if text.strip() in s:
        return
    if marker not in s:
        raise SystemExit(f'insertion target not found: {label} ({path})')
    path.write_text(s.replace(marker, text + marker, 1), encoding='utf-8')

for name in ['v02802-home-stats.css', 'v02802-home-stats.js', 'v02802-version.js']:
    src = ROOT / 'design-preview' / name
    if not src.is_file():
        raise SystemExit(f'missing v0.28.02 design file: {src}')
    (TARGET / name).write_bytes(src.read_bytes())

for name in ['v02900-record-maintenance.css', 'v02900-record-maintenance.js', 'v02900-version.js']:
    src = ROOT / 'design-preview' / name
    if not src.is_file():
        raise SystemExit(f'missing v0.29.00 design file: {src}')
    (TARGET / name).write_bytes(src.read_bytes())

index = TARGET / 'index.html'
add_before(index, '</head>', '  <link rel="stylesheet" href="v02802-home-stats.css">\n', 'home stats css')
add_before(index, '</body>', '  <script src="v02802-home-stats.js"></script>\n  <script src="v02802-version.js"></script>\n', 'home stats js')
add_before(index, '</head>', '  <link rel="stylesheet" href="v02900-record-maintenance.css">\n', 'v0.29 record maintenance css')
add_before(index, '</body>', '  <script src="v02900-record-maintenance.js"></script>\n  <script src="v02900-version.js"></script>\n', 'v0.29 record maintenance js')

print('Applied v0.28.02 Home stats + v0.29.00 record maintenance overlay.')
