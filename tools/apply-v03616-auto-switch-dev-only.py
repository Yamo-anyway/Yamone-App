#!/usr/bin/env python3
from pathlib import Path
import re

R = Path(__file__).resolve().parent.parent
A = R / 'app/src/main/assets/yamone-v23'
BUILD = R / 'app/build.gradle'
VERSION = '0.36.16'

move = A / 'v02508-real-move.js'
s = move.read_text(encoding='utf-8')

# Developer-only automatic mode. Normal users keep all three manual modes:
# walking, running, cycling. Do this at render time so there is no flash/partial
# exposure before the later developer-mode overlay runs.
needle = "  function bridge(){return window.YamoneMovement||null;}\n"
insert = """  function bridge(){return window.YamoneMovement||null;}\n  function developerAutoMode(){\n    try{return !!window.YamoneSystemSettings&&typeof YamoneSystemSettings.isDeveloperMode==='function'&&YamoneSystemSettings.isDeveloperMode();}\n    catch(e){return false;}\n  }\n"""
if 'function developerAutoMode()' not in s:
    if needle not in s:
        raise SystemExit('v0.36.16 movement bridge target missing')
    s = s.replace(needle, insert, 1)

old = """  window.moveReady=function(){
    setFocusMode(true);
    const active=getState();
    if(active.recording){latestState=active;syncModeFromState(active);moveState=active.paused?'paused':'recording';render();return;}
    screen.innerHTML=`
"""
new = """  window.moveReady=function(){
    setFocusMode(true);
    const active=getState();
    if(active.recording){latestState=active;syncModeFromState(active);moveState=active.paused?'paused':'recording';render();return;}
    const devAuto=developerAutoMode();
    if(!devAuto&&moveMode==='auto'){moveMode='walk';moveSubtype='walk';}
    screen.innerHTML=`
"""
if new not in s:
    if old not in s:
        raise SystemExit('v0.36.16 moveReady target missing')
    s = s.replace(old, new, 1)

old_grid = """      <div class=\"move-mode-grid\">${moveModeButton('auto','auto-switch.png','자동 전환','걷기 · 달리기')}${moveModeButton('walk','walk.png','걷기','페이스')}${moveModeButton('run','run.png','달리기','페이스')}${moveModeButton('bike','bike.png','자전거','속도')}</div>
"""
new_grid = """      <div class=\"move-mode-grid\">${devAuto?moveModeButton('auto','auto-switch.png','자동 전환','걷기 · 달리기'):''}${moveModeButton('walk','walk.png','걷기','페이스')}${moveModeButton('run','run.png','달리기','페이스')}${moveModeButton('bike','bike.png','자전거','속도')}</div>
"""
if new_grid not in s:
    if old_grid not in s:
        raise SystemExit('v0.36.16 mode grid target missing')
    s = s.replace(old_grid, new_grid, 1)

old_note = """      <div class=\"move-section\">안내</div><div class=\"move-note\">자동 전환은 현재 실제 기록 서비스의 걷기↔달리기 구분을 사용합니다. 자전거는 자전거를 선택해 시작합니다.</div>
"""
new_note = """      <div class=\"move-section\">안내</div><div class=\"move-note\">${devAuto?'자동 전환은 개발자 테스트 기능입니다. 현재 걷기↔달리기 구분을 분석하고 있습니다.':'걷기 · 달리기 · 자전거 중 기록할 활동을 직접 선택해 시작합니다.'}</div>
"""
if new_note not in s:
    if old_note not in s:
        raise SystemExit('v0.36.16 move note target missing')
    s = s.replace(old_note, new_note, 1)

move.write_text(s, encoding='utf-8')

# Version bump.
s = BUILD.read_text(encoding='utf-8')
s = s.replace('versionCode 109', 'versionCode 110')
s = s.replace("versionName '0.36.15'", "versionName '0.36.16'")
BUILD.write_text(s, encoding='utf-8')

# Keep all visible version writers pinned to this build.
for p in A.glob('*.js'):
    text = p.read_text(encoding='utf-8')
    new_text = text
    if 'home-version' in new_text or p.name == 'v03610-dataset.js':
        new_text = re.sub(r"const V='[^']+'", f"const V='{VERSION}'", new_text)
    if new_text != text:
        p.write_text(new_text, encoding='utf-8')

app = A / 'app.js'
s = app.read_text(encoding='utf-8')
s = re.sub(r'Version\s+\d+(?:\.\d+){2}', f'Version {VERSION}', s)
app.write_text(s, encoding='utf-8')

index = A / 'index.html'
s = index.read_text(encoding='utf-8')
s = re.sub(r'Version\s+\d+(?:\.\d+){2}', f'Version {VERSION}', s)
index.write_text(s, encoding='utf-8')

print('Applied v0.36.16: normal users manual walking/running/cycling only; auto switch developer-only.')
