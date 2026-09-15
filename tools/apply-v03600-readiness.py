#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
TARGET=ROOT/'app/src/main/assets/yamone-v23'


def add_before(path,marker,text,label):
    s=path.read_text(encoding='utf-8')
    if text.strip() in s:return
    if marker not in s:raise SystemExit(f'v0.36.00 insertion target not found: {label} ({path})')
    path.write_text(s.replace(marker,text+marker,1),encoding='utf-8')


def replace_once(path,old,new,label):
    s=path.read_text(encoding='utf-8')
    if new in s:return
    if old not in s:raise SystemExit(f'v0.36.00 patch target not found: {label} ({path})')
    path.write_text(s.replace(old,new,1),encoding='utf-8')

for name in ['v03600-readiness.css','v03600-readiness.js','v03600-version.js']:
    src=ROOT/'design-preview'/name
    (TARGET/name).write_bytes(src.read_bytes())

index=TARGET/'index.html'
add_before(index,'</head>','  <link rel="stylesheet" href="v03600-readiness.css">\n','readiness css')
s=index.read_text(encoding='utf-8')
for old in ['v03500-version.js','v03400-version.js','v03300-version.js','v03200-version.js','v03100-version.js','v03000-version.js','v02900-version.js','v02802-version.js','v02801-version.js','v02701-version.js','v02602-version.js','v02516-version.js']:
    s=s.replace(f'  <script src="{old}"></script>\n','').replace(f'<script src="{old}"></script>\n','')
index.write_text(s,encoding='utf-8')
add_before(index,'</body>','  <script src="v03600-readiness.js"></script>\n  <script src="v03600-version.js"></script>\n','readiness js')

for name in ['YamoneMovementActivity.java','YamoneDesignPreviewActivity.java']:
    host=ROOT/'app/src/main/java/com/yamo/snorelab'/name
    replace_once(host,
        '        webView.addJavascriptInterface(new SystemSettingsBridge(this), "YamoneSystemSettings");',
        '        webView.addJavascriptInterface(new SystemSettingsBridge(this), "YamoneSystemSettings");\n        webView.addJavascriptInterface(new AppReadinessBridge(this), "YamoneReadiness");',
        name+' readiness registration')
    replace_once(host,
        '            webView.removeJavascriptInterface("YamoneSystemSettings");',
        '            webView.removeJavascriptInterface("YamoneReadiness");\n            webView.removeJavascriptInterface("YamoneSystemSettings");',
        name+' readiness cleanup')

print('Applied v0.36.00 integrated readiness diagnostics.')
