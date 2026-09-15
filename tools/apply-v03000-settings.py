#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TARGET = ROOT / 'app/src/main/assets/yamone-v23'


def add_before(path: Path, marker: str, text: str, label: str):
    s = path.read_text(encoding='utf-8')
    if text.strip() in s:
        return
    if marker not in s:
        raise SystemExit(f'v0.30.00 insertion target not found: {label} ({path})')
    path.write_text(s.replace(marker, text + marker, 1), encoding='utf-8')


def replace_once(path: Path, old: str, new: str, label: str):
    s = path.read_text(encoding='utf-8')
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'v0.30.00 patch target not found: {label} ({path})')
    path.write_text(s.replace(old, new, 1), encoding='utf-8')

for name in ['v03000-settings.css', 'v03000-settings.js', 'v03000-version.js']:
    src = ROOT / 'design-preview' / name
    if not src.is_file():
        raise SystemExit(f'missing v0.30.00 design file: {src}')
    (TARGET / name).write_bytes(src.read_bytes())

index = TARGET / 'index.html'
add_before(index, '</head>', '  <link rel="stylesheet" href="v03000-settings.css">\n', 'settings css')

s = index.read_text(encoding='utf-8')
s = s.replace('  <script src="v02900-version.js"></script>\n', '')
s = s.replace('<script src="v02900-version.js"></script>\n', '')
index.write_text(s, encoding='utf-8')
add_before(index, '</body>', '  <script src="v03000-settings.js"></script>\n  <script src="v03000-version.js"></script>\n', 'settings js')

for name in ['YamoneMovementActivity.java', 'YamoneDesignPreviewActivity.java']:
    host = ROOT / 'app/src/main/java/com/yamo/snorelab' / name
    replace_once(
        host,
        '        webView.addJavascriptInterface(new AutoDetectSettingsBridge(this), "YamoneAutoDetect");',
        '        webView.addJavascriptInterface(new AutoDetectSettingsBridge(this), "YamoneAutoDetect");\n'
        '        webView.addJavascriptInterface(new SystemSettingsBridge(this), "YamoneSystemSettings");',
        f'{name} settings bridge registration')
    replace_once(
        host,
        '            webView.removeJavascriptInterface("YamoneAutoDetect");',
        '            webView.removeJavascriptInterface("YamoneSystemSettings");\n'
        '            webView.removeJavascriptInterface("YamoneAutoDetect");',
        f'{name} settings bridge cleanup')

print('Applied v0.30.00 real Android permission/storage settings.')
