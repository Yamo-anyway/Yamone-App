#!/usr/bin/env python3
from pathlib import Path
import re
import runpy

ROOT=Path(__file__).resolve().parent.parent
WALK=ROOT/'app/src/main/java/com/yamo/snorelab/WalkingRecorderService.java'
APP=ROOT/'app/src/main/java/com/yamo/snorelab/YamoneApplication.java'
BRIDGE=ROOT/'app/src/main/java/com/yamo/snorelab/AutoDetectSettingsBridge.java'
JS=ROOT/'app/src/main/assets/yamone-v23/v02516-auto-detect.js'
INDEX=ROOT/'app/src/main/assets/yamone-v23/index.html'
TARGET=ROOT/'app/src/main/assets/yamone-v23'
BUILD=ROOT/'app/build.gradle'

# Run the main v0.36.05 patch. It intentionally reaches the recorder-finalization
# insertion after all detector/manager/start hooks have already been applied. Older
# GPS patches changed that exact block, so catch only that known target miss and
# finish with a regex-safe insertion below.
try:
    runpy.run_path(str(ROOT/'tools/apply-v03605-autodetect-lifecycle.py'), run_name='__main__')
except SystemExit as e:
    msg=str(e)
    if 'restart auto-detect after movement record finishes' not in msg:
        raise


def replace_once(path,old,new,label):
    s=path.read_text(encoding='utf-8')
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'v0.36.05 fixed target not found: {label} ({path})')
    path.write_text(s.replace(old,new,1),encoding='utf-8')

# Insert after the final runtime editor regardless of extra GPS runtime fields introduced
# by v0.32.00.
s=WALK.read_text(encoding='utf-8')
if 'ActivityAutoDetectManager.onRecordingFinished(this);' not in s:
    pattern=r'(runtime\.edit\(\)\s*\.putBoolean\(KEY_RECORDING, false\).*?\.apply\(\);)(\s*handler\.removeCallbacks\(ticker\);)'
    ns,n=re.subn(pattern,r'\1\n        ActivityAutoDetectManager.onRecordingFinished(this);\2',s,count=1,flags=re.S)
    if n!=1:
        raise SystemExit('v0.36.05 fixed target not found: recorder finalization lifecycle')
    WALK.write_text(ns,encoding='utf-8')

replace_once(APP,
'''    @Override public void onActivityResumed(Activity activity) {
        if (activity instanceof MainActivity) {''',
'''    @Override public void onActivityResumed(Activity activity) {
        try { ActivityAutoDetectManager.syncRegistration(activity); } catch (RuntimeException ignored) { }
        if (activity instanceof MainActivity) {''',
'resync auto-detect on app resume')

replace_once(BRIDGE,
'''            out.put("hybridRunning", prefs.getBoolean(ActivityHybridDetectorService.KEY_RUNNING, false));''',
'''            out.put("movementRecording", activity.getSharedPreferences(WalkingRecorderService.PREFS, Activity.MODE_PRIVATE)
                    .getBoolean(WalkingRecorderService.KEY_RECORDING, false));
            out.put("hybridPausedForRecording", prefs.getBoolean(ActivityHybridDetectorService.KEY_PAUSED_FOR_RECORDING, false));
            out.put("hybridRunning", prefs.getBoolean(ActivityHybridDetectorService.KEY_RUNNING, false));''',
'bridge recording pause status')

replace_once(JS,
'''  function hybridStatusText(s){
    const label={walking:'걷기 후보',running:'달리기 후보',cycling:'자전거/RIDE 후보',none:'대기'}[s.hybridCandidate]||'대기';''',
'''  function hybridStatusText(s){
    if(s.movementRecording||s.hybridPausedForRecording)return '운동 기록 중 · 자동감지 일시중지 · 기록 종료 후 자동으로 다시 시작';
    const label={walking:'걷기 후보',running:'달리기 후보',cycling:'자전거/RIDE 후보',none:'대기'}[s.hybridCandidate]||'대기';''',
'UI recording pause status')

replace_once(JS,
'''      <div class="setting-info">Android 활동 이벤트와 자체 센서 판단을 함께 사용합니다. 같은 활동 후보가 선택 시간 이상 유지되면 자동 시작/전환합니다.</div>''',
'''      <div class="setting-info">Android 활동 이벤트와 자체 센서 판단을 함께 사용합니다. 같은 활동 후보가 선택 시간 이상 유지되면 자동 시작합니다. 기록 중에는 자동감지를 중지하고, 기록 종료 후 초기화하여 다시 감지합니다.</div>''',
'UI lifecycle explanation')

(TARGET/'v03605-version.js').write_bytes((ROOT/'design-preview/v03605-version.js').read_bytes())
s=INDEX.read_text(encoding='utf-8')
s=s.replace('  <script src="v03604-version.js"></script>\n','').replace('<script src="v03604-version.js"></script>\n','')
if 'v03605-version.js' not in s:
    if '</body>' not in s: raise SystemExit('v0.36.05 index body missing')
    s=s.replace('</body>','  <script src="v03605-version.js"></script>\n</body>',1)
INDEX.write_text(s,encoding='utf-8')

replace_once(BUILD,
"        versionCode 98\n        versionName '0.36.04'",
"        versionCode 99\n        versionName '0.36.05'",
'android version')

print('Applied v0.36.05 fixed auto-detect lifecycle, resume, and normal-gait wake behavior.')
