#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
JS=ROOT/'app/src/main/assets/yamone-v23/v02516-auto-detect.js'
INDEX=ROOT/'app/src/main/assets/yamone-v23/index.html'
TARGET=ROOT/'app/src/main/assets/yamone-v23'
BUILD=ROOT/'app/build.gradle'


def replace_once(path,old,new,label):
    s=path.read_text(encoding='utf-8')
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'v0.36.04 target not found: {label} ({path})')
    path.write_text(s.replace(old,new,1),encoding='utf-8')


# The fifth argument of settingToggle() is an icon basename, not a preference key.
# v0.36.01 passed notifySound/notifyVibrate there, which rendered broken *.png images.
replace_once(JS,
"        settingToggle('v3601Sound','알림 소리','자동감지 안내 소리',s.notifySound,'notifySound')+\n        settingToggle('v3601Vibrate','진동','자동감지 안내 진동',s.notifyVibrate,'notifyVibrate')",
"        settingToggle('v3601Sound','알림 소리','자동감지 안내 소리',s.notifySound,'')+\n        settingToggle('v3601Vibrate','진동','자동감지 안내 진동',s.notifyVibrate,'')",
'clear invalid icon basenames')

# Put the live hybrid detector readout in its own explicit section and remove the old
# vehicle-suppression explanation, which contradicts the v0.36.03 RIDE policy.
replace_once(JS,
'''      <div class="setting-info">Android 활동 이벤트와 자체 센서 판단을 함께 사용합니다. 같은 활동 후보가 선택 시간 이상 유지되면 자동 시작/전환합니다.</div>
      <div class="setting-info">자동차 이동도 현재 테스트 정책에서는 자전거(RIDE)로 분류합니다.</div>
      <div class="setting-info" id="v3603HybridStatus">${hybridStatusText(s)}</div>
      <div class="setting-info">자전거 자동감지는 휴대폰의 자전거 활동 신호를 사용하며, 자동차 탑승 신호가 함께 감지되면 자전거 자동 시작을 차단합니다.</div>`;''',
'''      ${settingSection('자체 자동감지 상태',`<div class="card v3604-hybrid-status" id="v3603HybridStatus">${hybridStatusText(s)}</div>`)}
      <div class="setting-info">Android 활동 이벤트와 자체 센서 판단을 함께 사용합니다. 같은 활동 후보가 선택 시간 이상 유지되면 자동 시작/전환합니다.</div>
      <div class="setting-info">자동차 이동도 현재 테스트 정책에서는 자전거(RIDE)로 분류합니다.</div>`;''',
'hybrid status section and current ride policy')

# v0.36.02 decorated the switch button itself. Stop loading that decorator/CSS;
# the v0.36.04 decorator uses the existing left icon slot on the setting row instead.
s=INDEX.read_text(encoding='utf-8')
for line in [
    '  <link rel="stylesheet" href="v03602-autodetect-assets.css">\n',
    '<link rel="stylesheet" href="v03602-autodetect-assets.css">\n',
    '  <script src="v03602-autodetect-assets.js"></script>\n',
    '<script src="v03602-autodetect-assets.js"></script>\n',
    '  <script src="v03603-version.js"></script>\n',
    '<script src="v03603-version.js"></script>\n',
]:
    s=s.replace(line,'')
INDEX.write_text(s,encoding='utf-8')

for name in ['v03604-autodetect-ui.css','v03604-autodetect-ui.js','v03604-version.js']:
    (TARGET/name).write_bytes((ROOT/'design-preview'/name).read_bytes())

s=INDEX.read_text(encoding='utf-8')
if 'v03604-autodetect-ui.css' not in s:
    if '</head>' not in s:raise SystemExit('v0.36.04 index head missing')
    s=s.replace('</head>','  <link rel="stylesheet" href="v03604-autodetect-ui.css">\n</head>',1)
if 'v03604-autodetect-ui.js' not in s:
    if '</body>' not in s:raise SystemExit('v0.36.04 index body missing')
    s=s.replace('</body>','  <script src="v03604-autodetect-ui.js"></script>\n  <script src="v03604-version.js"></script>\n</body>',1)
INDEX.write_text(s,encoding='utf-8')

replace_once(BUILD,
"        versionCode 97\n        versionName '0.36.03'",
"        versionCode 98\n        versionName '0.36.04'",
'android version')

print('Applied v0.36.04 auto-detect alert icon placement and diagnostics UI fix.')
