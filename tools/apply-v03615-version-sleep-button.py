#!/usr/bin/env python3
from pathlib import Path
import re

R = Path(__file__).resolve().parent.parent
A = R / 'app/src/main/assets/yamone-v23'
BUILD = R / 'app/build.gradle'
VERSION = '0.36.15'

# Android package version.
s = BUILD.read_text(encoding='utf-8')
s = s.replace('versionCode 108', 'versionCode 109')
s = s.replace("versionName '0.36.14'", "versionName '0.36.15'")
BUILD.write_text(s, encoding='utf-8')

# ---------------------------------------------------------------------------
# Visible version fix.
# v0.36.14 still loaded the historical v02501-ui.js observer whose own V was
# 0.25.03. It repeatedly overwrote Home + Settings list after the correct app.js
# render. Pin every script that actually writes .home-version to this build.
# ---------------------------------------------------------------------------
for p in A.glob('*.js'):
    text = p.read_text(encoding='utf-8')
    new = text
    if 'home-version' in new:
        new = re.sub(r"const V='[^']+'", f"const V='{VERSION}'", new)
    # Keep the developer dataset observer in sync as well.
    if p.name == 'v03610-dataset.js':
        new = re.sub(r"const V='[^']+'", f"const V='{VERSION}'", new)
    if new != text:
        p.write_text(new, encoding='utf-8')

# Render-time strings must also be correct before any observer runs.
app = A / 'app.js'
s = app.read_text(encoding='utf-8')
s = re.sub(r'Version\s+\d+(?:\.\d+){2}', f'Version {VERSION}', s)
app.write_text(s, encoding='utf-8')

index = A / 'index.html'
s = index.read_text(encoding='utf-8')
s = re.sub(r'Version\s+\d+(?:\.\d+){2}', f'Version {VERSION}', s)
index.write_text(s, encoding='utf-8')

# ---------------------------------------------------------------------------
# Developer sleep upload button styling.
# ---------------------------------------------------------------------------
sleep_js = A / 'v02602-sleep.js'
s = sleep_js.read_text(encoding='utf-8')
s = s.replace(
    '<button id="v03614SleepDeveloperUpload" ',
    '<button id="v03614SleepDeveloperUpload" class="v03615-dev-upload" ')
s = s.replace(
    "d.developerPaired?'개발자 분석 전송':'테스트 기기 연결 필요'",
    "d.developerPaired?'☁ 개발자 분석 전송':'테스트 기기 연결 필요'")
s = s.replace("devUpload.textContent='전송 중…';", "devUpload.textContent='☁ 전송 중…';")
s = s.replace("devUpload.textContent='개발자 분석 전송';", "devUpload.textContent='☁ 개발자 분석 전송';")
sleep_js.write_text(s, encoding='utf-8')

sleep_css = A / 'v02602-sleep.css'
s = sleep_css.read_text(encoding='utf-8')
style = '''\n/* v0.36.15 developer sleep upload */\n.v03615-dev-upload{width:100%;min-height:52px;border:1px solid color-mix(in srgb,var(--primary) 78%,#ffffff 22%);border-radius:17px;background:var(--primary);color:#fff;font-size:13px;font-weight:900;letter-spacing:-.15px;box-shadow:0 7px 18px color-mix(in srgb,var(--primary) 24%,transparent);transition:transform .12s ease,box-shadow .12s ease,opacity .12s ease}\n.v03615-dev-upload:active:not(:disabled){transform:translateY(1px) scale(.995);box-shadow:0 3px 10px color-mix(in srgb,var(--primary) 22%,transparent)}\n.v03615-dev-upload:disabled{background:var(--soft);color:var(--muted);border-color:rgba(110,130,125,.14);box-shadow:none;opacity:1}\n'''
if '.v03615-dev-upload{' not in s:
    s += style
sleep_css.write_text(s, encoding='utf-8')

print('Applied v0.36.15 visible version fix and styled developer sleep upload button.')
