#!/usr/bin/env python3
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parent.parent
MANAGER=ROOT/'app/src/main/java/com/yamo/snorelab/ActivityAutoDetectManager.java'
BRIDGE=ROOT/'app/src/main/java/com/yamo/snorelab/AutoDetectSettingsBridge.java'
HYBRID=ROOT/'app/src/main/java/com/yamo/snorelab/ActivityHybridDetectorService.java'
MANIFEST=ROOT/'app/src/main/AndroidManifest.xml'
JS=ROOT/'app/src/main/assets/yamone-v23/v02516-auto-detect.js'
INDEX=ROOT/'app/src/main/assets/yamone-v23/index.html'
TARGET=ROOT/'app/src/main/assets/yamone-v23'
BUILD=ROOT/'app/build.gradle'


def replace_once(path,old,new,label):
    s=path.read_text(encoding='utf-8')
    if new in s:return
    if old not in s:raise SystemExit(f'v0.36.03 target not found: {label} ({path})')
    path.write_text(s.replace(old,new,1),encoding='utf-8')


def sub_once(path,pattern,repl,label):
    s=path.read_text(encoding='utf-8')
    ns,n=re.subn(pattern,repl,s,count=1,flags=re.S)
    if n!=1:raise SystemExit(f'v0.36.03 regex target not found: {label} ({path})')
    path.write_text(ns,encoding='utf-8')

# Fix source typo if present (the class is intentionally added before this build-time patch).
replace_once(HYBRID,'addSteps((int) delta;','addSteps((int) delta);','hybrid step counter syntax')

replace_once(MANAGER,
'import android.os.Build;\nimport android.os.SystemClock;',
'import android.os.Build;\nimport android.os.Handler;\nimport android.os.Looper;\nimport android.os.SystemClock;',
'manager handler imports')
replace_once(MANAGER,
'    public static final String KEY_SENSITIVITY = "sensitivity";',
'    public static final String KEY_SENSITIVITY = "sensitivity";\n    public static final String KEY_CONFIRM_SECONDS = "confirm_seconds";',
'confirm seconds key')
replace_once(MANAGER,
'    private static final long END_INACTIVITY_MS = 3 * 60_000L;',
'    private static final long END_INACTIVITY_MS = 3 * 60_000L;\n    private static final Handler MAIN = new Handler(Looper.getMainLooper());',
'main handler')

sub_once(MANAGER,
r'    public static void syncRegistration\(Context context\) \{.*?\n    \}\n\n    private static void removeRegistration',
'''    public static void syncRegistration(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences p = prefs(app);
        clearDisabledState(app, p);
        if (!anyEnabled(app)) {
            removeRegistration(app);
            ActivityHybridDetectorService.stop(app);
            return;
        }
        ActivityHybridDetectorService.sync(app);
        if (!hasRecognitionPermission(app)) {
            removeRegistration(app);
            return;
        }

        List<ActivityTransition> transitions = new ArrayList<>();
        if (p.getBoolean(KEY_WALK_ENABLED, false)) addTransitions(transitions, DetectedActivity.WALKING);
        if (p.getBoolean(KEY_RUN_ENABLED, false)) addTransitions(transitions, DetectedActivity.RUNNING);
        if (p.getBoolean(KEY_BIKE_ENABLED, false)) {
            addTransitions(transitions, DetectedActivity.ON_BICYCLE);
            addTransitions(transitions, DetectedActivity.IN_VEHICLE);
        }
        if (transitions.isEmpty()) {
            removeRegistration(app);
            return;
        }
        try {
            ActivityTransitionRequest request = new ActivityTransitionRequest(transitions);
            ActivityRecognition.getClient(app).requestActivityTransitionUpdates(request, transitionPendingIntent(app));
        } catch (RuntimeException ignored) { }
    }

    private static void removeRegistration''',
'sync registration hybrid')

sub_once(MANAGER,
r'    private static void handleTransition\(Context context, Intent intent\) \{.*?\n    \}\n\n    private static void onEnter',
'''    private static void handleTransition(Context context, Intent intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return;
        ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
        if (result == null) return;
        for (ActivityTransitionEvent event : result.getTransitionEvents()) {
            boolean entering = event.getTransitionType() == ActivityTransition.ACTIVITY_TRANSITION_ENTER;
            ActivityHybridDetectorService.notePlatformActivity(context, event.getActivityType(), entering);
            String type = fromDetectedActivity(event.getActivityType());
            if (type == null) continue;
            if (entering) onEnter(context, type); else onExit(context, type);
        }
    }

    private static void onEnter''',
'platform hints to hybrid')

sub_once(MANAGER,
r'    private static void onEnter\(Context context, String type\) \{.*?\n    \}\n\n    private static void onExit',
'''    private static void onEnter(Context context, String type) {
        SharedPreferences p = prefs(context);
        if (!isEnabled(p, type)) return;
        setActive(p, type, true);
        cancelAlarm(context, ACTION_REPROMPT, REQ_REPROMPT_BASE + typeIndex(type), type);
        ActivityHybridDetectorService.ensureRunning(context);
    }

    private static void onExit''',
'disable legacy direct start')

sub_once(MANAGER,
r'    private static void onExit\(Context context, String type\) \{.*?\n    \}\n\n    private static void confirmCandidate',
'''    private static void onExit(Context context, String type) {
        SharedPreferences p = prefs(context);
        setActive(p, type, false);
        cancelAlarm(context, ACTION_CONFIRM_CANDIDATE, REQ_CANDIDATE_BASE + typeIndex(type), type);
        cancelAlarm(context, ACTION_REPROMPT, REQ_REPROMPT_BASE + typeIndex(type), type);
    }

    private static void confirmCandidate''',
'disable legacy platform auto-end')

sub_once(MANAGER,
r'    private static void reprompt\(Context context, String type\) \{.*?\n    \}\n\n    private static void startFromPrompt',
'''    private static void reprompt(Context context, String type) {
        SharedPreferences p = prefs(context);
        if (!isEnabled(p, type) || isRecording(context)) return;
        if (!isActive(p, type) && !ActivityHybridDetectorService.candidateMatches(context, type)) return;
        showStartPrompt(context, type);
    }

    private static void startFromPrompt''',
'hybrid reprompt')

sub_once(MANAGER,
r'    private static void cancelPrompt\(Context context, String type\) \{.*?\n    \}\n\n    private static void checkAutoEnd',
'''    private static void cancelPrompt(Context context, String type) {
        cancelStartPrompt(context, type);
        SharedPreferences p = prefs(context);
        if (!isEnabled(p, type)) return;
        if (!isActive(p, type) && !ActivityHybridDetectorService.candidateMatches(context, type)) return;
        int minutes = sanitizeReprompt(p.getInt(KEY_REPROMPT_MIN, 5));
        scheduleAlarm(context, ACTION_REPROMPT,
                REQ_REPROMPT_BASE + typeIndex(type), type, minutes * 60_000L);
    }

    private static void checkAutoEnd''',
'hybrid cancel reprompt')

sub_once(MANAGER,
r'    private static boolean anyActive\(Context context\) \{.*?\n    \}',
'''    private static boolean anyActive(Context context) {
        SharedPreferences p = prefs(context);
        return p.getBoolean(KEY_ACTIVE_WALK, false)
                || p.getBoolean(KEY_ACTIVE_RUN, false)
                || p.getBoolean(KEY_ACTIVE_BIKE, false)
                || ActivityHybridDetectorService.motionActive(context);
    }''',
'any active includes hybrid')

sub_once(MANAGER,
r'    private static String fromDetectedActivity\(int activity\) \{.*?\n    \}',
'''    private static String fromDetectedActivity(int activity) {
        if (activity == DetectedActivity.WALKING) return TYPE_WALK;
        if (activity == DetectedActivity.RUNNING) return TYPE_RUN;
        if (activity == DetectedActivity.ON_BICYCLE) return TYPE_BIKE;
        // v0.36.03 policy: vehicle joins the RIDE/cycling bucket.
        if (activity == DetectedActivity.IN_VEHICLE) return TYPE_BIKE;
        return null;
    }''',
'vehicle becomes cycling')

insert='''
    static int confirmSeconds(Context context) {
        int sec = prefs(context).getInt(KEY_CONFIRM_SECONDS, 30);
        return sec == 15 || sec == 60 ? sec : 30;
    }

    static boolean isTypeEnabled(Context context, String type) {
        return isEnabled(prefs(context), type);
    }

    static void onHybridCandidateConfirmed(Context context, String rawType) {
        final Context app = context.getApplicationContext();
        final String type = normalizeType(rawType);
        SharedPreferences p = prefs(app);
        if (!isEnabled(p, type)) return;

        if (isRecording(app)) {
            if (!startedByAutoDetect(app)) return;
            String current = app.getSharedPreferences(WalkingRecorderService.PREFS, Context.MODE_PRIVATE)
                    .getString(WalkingRecorderService.KEY_ACTIVITY_TYPE, "walking");
            String target = recorderType(app, type);
            boolean currentRide = "cycling".equals(current);
            boolean targetRide = "cycling".equals(target);
            if (currentRide == targetRide) return;

            Intent stop = new Intent(app, WalkingRecorderService.class).setAction(WalkingRecorderService.ACTION_STOP);
            try { app.startService(stop); } catch (RuntimeException ignored) { }
            MAIN.postDelayed(() -> {
                if (!isRecording(app) && ActivityHybridDetectorService.candidateMatches(app, type)) {
                    startRecorder(app, type);
                }
            }, 1200L);
            return;
        }

        String mode = p.getString(KEY_START_MODE, "ask");
        if ("auto".equals(mode)) {
            if (!canAutoStartLocation(app)) {
                showBackgroundLocationRequired(app, type);
                return;
            }
            startRecorder(app, type);
        } else {
            showStartPrompt(app, type);
        }
    }

    static void onHybridInactive(Context context) {
        if (!isRecording(context) || !startedByAutoDetect(context)) return;
        cancelAlarm(context, ACTION_END_CHECK, REQ_END_CHECK, null);
        scheduleAlarm(context, ACTION_END_CHECK, REQ_END_CHECK, null, END_INACTIVITY_MS);
    }

    static void onHybridMotionResumed(Context context) {
        cancelAlarm(context, ACTION_END_CHECK, REQ_END_CHECK, null);
        cancelEndPrompt(context);
    }

'''
s=MANAGER.read_text(encoding='utf-8')
marker='    static int sanitizeReprompt(int minutes) {'
if 'static int confirmSeconds(Context context)' not in s:
    if marker not in s:raise SystemExit('v0.36.03 helper insertion target missing')
    MANAGER.write_text(s.replace(marker,insert+marker,1),encoding='utf-8')

# Bridge the 15/30/60 confirmation time and live detector diagnostics.
replace_once(BRIDGE,
'''            out.put("activityRecognition", ActivityAutoDetectManager.hasRecognitionPermission(activity));''',
'''            out.put("confirmSeconds", ActivityAutoDetectManager.confirmSeconds(activity));
            out.put("hybridRunning", prefs.getBoolean(ActivityHybridDetectorService.KEY_RUNNING, false));
            out.put("hybridCandidate", prefs.getString(ActivityHybridDetectorService.KEY_CANDIDATE, "none"));
            out.put("hybridCandidateSince", prefs.getLong(ActivityHybridDetectorService.KEY_CANDIDATE_SINCE, 0L));
            out.put("hybridConfidence", prefs.getInt(ActivityHybridDetectorService.KEY_CONFIDENCE, 0));
            out.put("hybridCadence", prefs.getFloat(ActivityHybridDetectorService.KEY_CADENCE, 0f));
            out.put("hybridSpeedKmh", prefs.getFloat(ActivityHybridDetectorService.KEY_SPEED_KMH, 0f));
            out.put("hybridAccel", prefs.getFloat(ActivityHybridDetectorService.KEY_ACCEL, 0f));
            out.put("hybridGyro", prefs.getFloat(ActivityHybridDetectorService.KEY_GYRO, 0f));
            out.put("hybridPlatform", prefs.getString(ActivityHybridDetectorService.KEY_PLATFORM, "none"));
            out.put("activityRecognition", ActivityAutoDetectManager.hasRecognitionPermission(activity));''',
'bridge hybrid output')
replace_once(BRIDGE,
'''            String sensitivity = sanitizeSensitivity(in.optString("sensitivity", "normal"));''',
'''            String sensitivity = sanitizeSensitivity(in.optString("sensitivity", "normal"));
            int confirmSeconds = sanitizeConfirmSeconds(in.optInt("confirmSeconds",
                    prefs.getInt(ActivityAutoDetectManager.KEY_CONFIRM_SECONDS, 30)));''',
'bridge confirm input')
replace_once(BRIDGE,
'''                    .putString(ActivityAutoDetectManager.KEY_SENSITIVITY, sensitivity)''',
'''                    .putString(ActivityAutoDetectManager.KEY_SENSITIVITY, sensitivity)
                    .putInt(ActivityAutoDetectManager.KEY_CONFIRM_SECONDS, confirmSeconds)''',
'bridge confirm persistence')
replace_once(BRIDGE,
'''    private static String sanitizeSensitivity(String value) {''',
'''    private static int sanitizeConfirmSeconds(int value) {
        return value == 15 || value == 60 ? value : 30;
    }

    private static String sanitizeSensitivity(String value) {''',
'bridge confirm sanitizer')

# Settings UI: replace abstract sensitivity with explicit confirmation duration.
s=JS.read_text(encoding='utf-8')
s=s.replace("sensitivity:'normal',endMode:'ask'","sensitivity:'normal',confirmSeconds:30,endMode:'ask'",1)
s=s.replace("          sensitivity:['fast','normal','accurate'].includes(s.sensitivity)?s.sensitivity:'normal',\n",
            "          confirmSeconds:[15,30,60].includes(Number(s.confirmSeconds))?Number(s.confirmSeconds):30,\n",1)
s=s.replace("${settingSection('감지 민감도',`<div class=\"card setting-block\">${settingChips('v2516Sensitivity',[['fast','빠르게'],['normal','기본'],['accurate','정확하게']],s.sensitivity)}</div>`)}",
            "${settingSection('자동감지 확정 시간',`<div class=\"card setting-block\">${settingChips('v3603ConfirmSeconds',[['15','15초 이후'],['30','30초 이후'],['60','60초 이후']],String(s.confirmSeconds||30))}</div>`) }",1)
s=s.replace("    screen.querySelectorAll('[data-setting-chip=\"v2516Sensitivity\"]').forEach(btn=>btn.onclick=()=>mutate(x=>{x.sensitivity=btn.dataset.value;},false));",
            "    screen.querySelectorAll('[data-setting-chip=\"v3603ConfirmSeconds\"]').forEach(btn=>btn.onclick=()=>mutate(x=>{x.confirmSeconds=Number(btn.dataset.value);},false));",1)
s=s.replace("      <div class=\"setting-info\">기본/빠르게는 Android 활동 전환 감지 즉시 처리하고, 정확하게만 추가 확인 시간을 둡니다.</div>",
            "      <div class=\"setting-info\">Android 활동 이벤트와 자체 센서 판단을 함께 사용합니다. 같은 활동 후보가 선택 시간 이상 유지되면 자동 시작/전환합니다.</div>\n      <div class=\"setting-info\">자동차 이동도 현재 테스트 정책에서는 자전거(RIDE)로 분류합니다.</div>\n      <div class=\"setting-info\" id=\"v3603HybridStatus\">${hybridStatusText(s)}</div>",1)
helper='''  function hybridStatusText(s){
    const label={walking:'걷기 후보',running:'달리기 후보',cycling:'자전거/RIDE 후보',none:'대기'}[s.hybridCandidate]||'대기';
    const target=[15,30,60].includes(Number(s.confirmSeconds))?Number(s.confirmSeconds):30;
    const elapsed=s.hybridCandidateSince?Math.max(0,Math.floor((Date.now()-Number(s.hybridCandidateSince))/1000)):0;
    const cadence=Math.round(Number(s.hybridCadence)||0);
    const speed=(Number(s.hybridSpeedKmh)||0).toFixed(1);
    const confidence=Math.round(Number(s.hybridConfidence)||0);
    const platform=s.hybridPlatform&&s.hybridPlatform!=='none'?` · Android ${s.hybridPlatform}`:'';
    return `자체감지 ${s.hybridRunning?'동작 중':'대기'} · ${label}${s.hybridCandidate&&s.hybridCandidate!=='none'?` ${Math.min(elapsed,target)}/${target}초`:''} · 걸음 ${cadence}spm · 속도 ${speed}km/h · 점수 ${confidence}${platform}`;
  }

'''
needle='  function renderAutoDetectSettingsV2516(){'
if 'function hybridStatusText' not in s:
    if needle not in s:raise SystemExit('v0.36.03 JS helper target missing')
    s=s.replace(needle,helper+needle,1)
poll='''  setInterval(()=>{
    const el=document.getElementById('v3603HybridStatus');
    if(!el)return;
    const st=loadSettings();
    el.textContent=hybridStatusText(st);
  },1000);
  try{if(window.YamoneAutoDetect&&typeof YamoneAutoDetect.syncRegistration==='function')YamoneAutoDetect.syncRegistration();}catch(e){}
'''
needle2='  schedule();\n})();'
if 'v3603HybridStatus' in s and 'YamoneAutoDetect.syncRegistration' not in s:
    if needle2 not in s:raise SystemExit('v0.36.03 JS poll target missing')
    s=s.replace(needle2,poll+'  schedule();\n})();',1)
JS.write_text(s,encoding='utf-8')

# Register the always-on low-power detector service while auto-detect is enabled.
replace_once(MANIFEST,
'        <service android:name=".WalkingRecorderService" android:foregroundServiceType="location" android:exported="false" />',
'        <service android:name=".WalkingRecorderService" android:foregroundServiceType="location" android:exported="false" />\n        <service android:name=".ActivityHybridDetectorService" android:foregroundServiceType="location" android:exported="false" />',
'hybrid service manifest')

# Version marker and Android version.
(TARGET/'v03603-version.js').write_bytes((ROOT/'design-preview/v03603-version.js').read_bytes())
s=INDEX.read_text(encoding='utf-8')
s=s.replace('  <script src="v03602-version.js"></script>\n','').replace('<script src="v03602-version.js"></script>\n','')
INDEX.write_text(s,encoding='utf-8')
s=INDEX.read_text(encoding='utf-8')
if 'v03603-version.js' not in s:
    if '</body>' not in s:raise SystemExit('v0.36.03 index body missing')
    INDEX.write_text(s.replace('</body>','  <script src="v03603-version.js"></script>\n</body>',1),encoding='utf-8')
replace_once(BUILD,"        versionCode 96\n        versionName '0.36.02'","        versionCode 97\n        versionName '0.36.03'",'android version')

print('Applied v0.36.03 hybrid auto-detection with 15/30/60 second confirmation.')
