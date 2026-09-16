#!/usr/bin/env python3
from pathlib import Path
import re

R = Path(__file__).resolve().parent.parent
J = R / 'app/src/main/java/com/yamo/snorelab'
A = R / 'app/src/main/assets/yamone-v23'
BUILD = R / 'app/build.gradle'


def replace_once(path: Path, old: str, new: str, label: str):
    s = path.read_text(encoding='utf-8')
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'v0.36.14 target not found: {label} ({path})')
    path.write_text(s.replace(old, new, 1), encoding='utf-8')


# ---------------------------------------------------------------------------
# 1) One unified 7-tap developer mode.
# Snow previously kept a second independent tap/unlock state. From now on Snow
# follows SystemSettingsBridge developer mode only, so nothing changes before
# the seventh tap and every developer-only feature changes together.
# ---------------------------------------------------------------------------
settings = J / 'SystemSettingsBridge.java'
replace_once(
    settings,
    '''    @JavascriptInterface
    public boolean setDeveloperMode(boolean enabled) {
        activity.getSharedPreferences(INTERNAL_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_DEVELOPER_MODE, enabled).apply();
        return enabled;
    }''',
    '''    @JavascriptInterface
    public boolean setDeveloperMode(boolean enabled) {
        activity.getSharedPreferences(INTERNAL_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_DEVELOPER_MODE, enabled).apply();
        if (enabled) SnowAutoDetectManager.sync(activity);
        else SnowAutoDetectManager.remove(activity);
        return enabled;
    }''',
    'developer mode Snow sync')

snow_bridge = J / 'SnowBridge.java'
s = snow_bridge.read_text(encoding='utf-8')
s = s.replace(
    '''    public static boolean isDeveloperAvailable(Context context) {
        if (!isDebuggable(context)) return false;
        return context.getSharedPreferences(DEV_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_UNLOCKED, false);
    }''',
    '''    public static boolean isDeveloperAvailable(Context context) {
        return isDebuggable(context) && SystemSettingsBridge.isDeveloperMode(context);
    }''')
s = s.replace(
    '''            boolean debug = isDebuggable(activity);
            boolean unlocked = debug && devPrefs.getBoolean(KEY_UNLOCKED, false);
            out.put("developerBuild", debug);
            out.put("unlocked", unlocked);
            out.put("available", unlocked);
            out.put("tapCount", debug ? Math.max(0, devPrefs.getInt(KEY_TAPS, 0)) : 0);
            out.put("releaseExcluded", !debug);''',
    '''            boolean debug = isDebuggable(activity);
            boolean unlocked = debug && SystemSettingsBridge.isDeveloperMode(activity);
            out.put("developerBuild", debug);
            out.put("unlocked", unlocked);
            out.put("available", unlocked);
            out.put("tapCount", unlocked ? 7 : 0);
            out.put("releaseExcluded", !debug);''')
s = re.sub(
    r'''    @JavascriptInterface\n    public String registerDeveloperTap\(\) \{.*?\n    \}\n\n    @JavascriptInterface\n    public String lockDeveloperMode\(\) \{.*?\n    \}\n''',
    '''    @JavascriptInterface\n    public String registerDeveloperTap() {\n        JSONObject out = new JSONObject();\n        try {\n            boolean unlocked = isDeveloperAvailable(activity);\n            out.put("ok", true).put("unlocked", unlocked).put("remaining", unlocked ? 0 : 7);\n        } catch (Exception ignored) {}\n        return out.toString();\n    }\n\n    @JavascriptInterface\n    public String lockDeveloperMode() {\n        return result(false, "use_system_developer_mode").toString();\n    }\n''',
    s,
    count=1,
    flags=re.S)
snow_bridge.write_text(s, encoding='utf-8')

# The core app had an old independent appInfoTapCount mock panel. Keep it inert;
# v03607-developer-mode.js is the sole unlock owner.
app_js = A / 'app.js'
s = app_js.read_text(encoding='utf-8')
s = s.replace('  const unlocked=appInfoTapCount>=7;', '  const unlocked=false;')
s = s.replace(
    '''  document.getElementById('appVersionTap').onclick=()=>{
    appInfoTapCount++;
    if(appInfoTapCount>=7)renderAppInfoSettings();
  };''',
    '''  document.getElementById('appVersionTap').onclick=()=>{};''')
app_js.write_text(s, encoding='utf-8')

# Snow must not own/replace App Info anymore. Snow stays visible in Activity only
# when the unified developer mode is enabled (its renderActivity wrapper remains).
snow_js = A / 'v02801-snow.js'
s = snow_js.read_text(encoding='utf-8')
s, count = re.subn(
    r'''\n  /\* Actual persisted developer unlock through the existing hidden App Info gesture\. \*/\n  window\.renderAppInfoSettings=function\(\)\{.*?\n  \};\n\n  /\* Real Snow setting page:''',
    '''\n  /* App Info unlock is owned by the unified system developer mode. */\n\n  /* Real Snow setting page:''',
    s,
    count=1,
    flags=re.S)
if count != 1:
    raise SystemExit('v0.36.14 Snow App Info override not found')
snow_js.write_text(s, encoding='utf-8')

# Tighten the seven-tap listener to the actual version card only.
dev_js = A / 'v03607-developer-mode.js'
s = dev_js.read_text(encoding='utf-8')
s = s.replace(
    '''    const candidate=e.target.closest('.app-info-card,.card,button,div,span,small');
    const text=((candidate&&candidate.textContent)||e.target.textContent||'').trim();
    if(!/Version\\s*\\d|버전/i.test(text))return;''',
    '''    const candidate=e.target.closest('#appVersionTap');
    if(!candidate)return;''')
dev_js.write_text(s, encoding='utf-8')


# ---------------------------------------------------------------------------
# 2) Developer-mode sleep record upload in the renewed WebView sleep detail.
# The native legacy enhancer did not reach this screen, so expose an explicit
# SleepBridge action and show it only while unified developer mode is enabled.
# ---------------------------------------------------------------------------
sleep_state = J / 'SleepUploadState.java'
s = sleep_state.read_text(encoding='utf-8').replace(
    'private static final String FILE_NAME = "server-upload.json";',
    'private static final String FILE_NAME = "developer-macmini-upload.json";')
sleep_state.write_text(s, encoding='utf-8')

sleep_bridge = J / 'SleepBridge.java'
s = sleep_bridge.read_text(encoding='utf-8')
if 'import android.widget.Toast;' not in s:
    s = s.replace('import android.webkit.JavascriptInterface;\n', 'import android.webkit.JavascriptInterface;\nimport android.widget.Toast;\n')

old = '''            out.put("storageBytes", SessionStore.folderSize(dir));'''
new = '''            out.put("storageBytes", SessionStore.folderSize(dir));
            out.put("developerMode", SystemSettingsBridge.isDeveloperMode(activity));
            out.put("developerPaired", !DevCaptureService.prefs(activity).getString("macmini_token_v1", "").isEmpty());
            out.put("developerUploaded", SleepUploadState.wasUploaded(dir, meta));'''
if new not in s:
    if old not in s:
        raise SystemExit('v0.36.14 sleep detail state target not found')
    s = s.replace(old, new, 1)

marker = '''    @JavascriptInterface
    public String reviewEvent(String sessionId, int eventIndex, String label) {'''
upload_method = '''    @JavascriptInterface
    public String uploadDeveloperAnalysis(String sessionId) {
        if (!SystemSettingsBridge.isDeveloperMode(activity)) return result(false, "developer_mode_required").toString();
        File dir = sessionDir(sessionId);
        if (dir == null) return result(false, "not_found").toString();
        JSONObject meta = SessionStore.readMeta(dir);
        if (!"complete".equals(meta.optString("status"))) return result(false, "not_complete").toString();
        if (DevCaptureService.prefs(activity).getString("macmini_token_v1", "").isEmpty()) {
            return result(false, "not_paired").toString();
        }
        if (SleepUploadState.wasUploaded(dir, meta)) return result(true, "already_uploaded").toString();
        MacMiniSleepUploader.upload(activity, dir, new MacMiniSleepUploader.Callback() {
            @Override public void onSuccess(boolean alreadyUploaded) {
                activity.runOnUiThread(() -> Toast.makeText(activity,
                        alreadyUploaded ? "이미 Mac mini에 전송된 수면 기록입니다." : "수면 분석 데이터를 Mac mini에 전송했습니다.",
                        Toast.LENGTH_SHORT).show());
            }
            @Override public void onFailure(String message) {
                activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
            }
        });
        return result(true, "started").toString();
    }

'''
if upload_method not in s:
    if marker not in s:
        raise SystemExit('v0.36.14 SleepBridge insert target not found')
    s = s.replace(marker, upload_method + marker, 1)
sleep_bridge.write_text(s, encoding='utf-8')

sleep_js = A / 'v02602-sleep.js'
s = sleep_js.read_text(encoding='utf-8')
old_actions = '''      <div class="v02602-sleep-detail-actions"><button id="v02602DeleteSleepRecord" class="v02602-danger">이 수면 기록 삭제</button></div>'''
new_actions = '''      <div class="v02602-sleep-detail-actions">
        ${d.developerMode?`<button id="v03614SleepDeveloperUpload" ${d.developerUploaded||!d.developerPaired?'disabled':''}>${d.developerUploaded?'✓ 개발자 분석 전송 완료':d.developerPaired?'개발자 분석 전송':'테스트 기기 연결 필요'}</button>`:''}
        <button id="v02602DeleteSleepRecord" class="v02602-danger">이 수면 기록 삭제</button>
      </div>'''
if new_actions not in s:
    if old_actions not in s:
        raise SystemExit('v0.36.14 renewed sleep detail action target not found')
    s = s.replace(old_actions, new_actions, 1)

old_bind = '''    document.getElementById('v02602SleepBack').onclick=backToSleepRecords;
    const del=document.getElementById('v02602DeleteSleepRecord');'''
new_bind = '''    document.getElementById('v02602SleepBack').onclick=backToSleepRecords;
    const devUpload=document.getElementById('v03614SleepDeveloperUpload');
    if(devUpload&&d.developerPaired&&!d.developerUploaded)devUpload.onclick=()=>{
      const r=command('uploadDeveloperAnalysis',[selectedSleepId]);
      if(!r.ok){if(typeof say==='function')say(r.status==='not_paired'?'설정 > 업로드에서 테스트 기기를 먼저 연결해 주세요.':'수면 분석 전송을 시작하지 못했습니다.');return;}
      devUpload.disabled=true;devUpload.textContent='전송 중…';
      let tries=0;const poll=setInterval(()=>{tries++;const now=detail(selectedSleepId,80);if(now&&now.developerUploaded){clearInterval(poll);renderSleepDetail();}else if(tries>=20){clearInterval(poll);devUpload.disabled=false;devUpload.textContent='개발자 분석 전송';}},750);
    };
    const del=document.getElementById('v02602DeleteSleepRecord');'''
if new_bind not in s:
    if old_bind not in s:
        raise SystemExit('v0.36.14 renewed sleep upload bind target not found')
    s = s.replace(old_bind, new_bind, 1)
sleep_js.write_text(s, encoding='utf-8')


# ---------------------------------------------------------------------------
# 3) Home/version: all legacy version observers must agree on one value.
# Several historical *-version.js observers were still loaded at once and could
# race after a redraw, so pin every one of them to v0.36.14.
# ---------------------------------------------------------------------------
for p in A.rglob('*'):
    if not p.is_file():
        continue
    try:
        text = p.read_text(encoding='utf-8')
    except (UnicodeDecodeError, OSError):
        continue
    new_text = text.replace('0.36.13', '0.36.14')
    if p.name.endswith('-version.js'):
        new_text = re.sub(r"const V='[^']+'", "const V='0.36.14'", new_text)
    if new_text != text:
        p.write_text(new_text, encoding='utf-8')


# ---------------------------------------------------------------------------
# 4) Upload settings: only automatic record/upload remains. Pairing is shown
# only when a fresh install is not yet paired; after pairing the page contains
# only the automatic control plus status/error text.
# ---------------------------------------------------------------------------
dataset = A / 'v03610-dataset.js'
s = dataset.read_text(encoding='utf-8')
new_renderer = r''' window.yamoneRenderDataset=function(){
  if(!developer()){settingPage=null;view='main';tab='settings';render();return;}
  const s=state();signature=JSON.stringify(s);if(typeof setFocusMode==='function')setFocusMode(true);
  screen.innerHTML=`${settingHeader('업로드')}
   <div class="card dataset-panel"><div class="dataset-head"><b>자동 기록 · 업로드</b><button id="datasetToggle" class="dataset-button dataset-toggle">${s.automatic?'ON':'OFF'}</button></div>
   <p>ON부터 OFF까지를 개발자 진단 기록 1개로 저장하고 Mac mini로 자동 전송합니다.</p>
   <small>${s.active?'자동 기록 수집 중':'수집 대기'} · ${s.uploading?'서버 전송 중':'전송 대기'}<br>마지막 전송: ${date(s.lastUploadMs)}</small></div>
   ${s.paired?'':`<div class="card dataset-panel"><b>테스트 기기 연결</b><p>처음 한 번만 관리자에서 발급한 연결 코드를 입력합니다.</p><input id="datasetCode" class="dataset-input" type="password" autocomplete="off" maxlength="32" placeholder="테스터 연결 코드"><div class="dataset-actions"><button id="datasetEnroll">기기 연결</button></div></div>`}
   <div class="dataset-error">${esc(s.error||'')}</div>`;
  const back=document.getElementById('settingBack');if(back)back.onclick=()=>{settingPage=null;view='main';tab='settings';setFocusMode(false);syncTabs();render();};
  const toggle=document.getElementById('datasetToggle');if(toggle)toggle.onclick=()=>call(s.automatic?'stopAutomatic':'startAutomatic');
  const enroll=document.getElementById('datasetEnroll');if(enroll)enroll.onclick=()=>{const code=document.getElementById('datasetCode');call('enroll',code.value);code.value='';say('기기 연결을 확인합니다.');};
 };
'''
s, count = re.subn(r''' window\.yamoneRenderDataset=function\(\)\{.*?\n \};\n function update\(\)\{''', new_renderer + ' function update(){', s, count=1, flags=re.S)
if count != 1:
    raise SystemExit('v0.36.14 dataset renderer target not found')
dataset.write_text(s, encoding='utf-8')


# ---------------------------------------------------------------------------
# 5) Snow is controlled by unified developer mode and appears in Activity then.
# No Snow controls are placed in the App Info developer section.
# ---------------------------------------------------------------------------
# (renderActivity wrapper in v02801-snow.js already adds/removes the Snow card
# according to SnowBridge.getGateState(), which is now unified above.)

# Android version.
s = BUILD.read_text(encoding='utf-8')
s = s.replace('versionCode 107', 'versionCode 108').replace("versionName '0.36.13'", "versionName '0.36.14'")
BUILD.write_text(s, encoding='utf-8')

print('Applied v0.36.14 unified developer mode, sleep upload, version, simplified upload UI, and Snow placement.')
