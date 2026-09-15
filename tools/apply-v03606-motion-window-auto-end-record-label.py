#!/usr/bin/env python3
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parent.parent
HYBRID=ROOT/'app/src/main/java/com/yamo/snorelab/ActivityHybridDetectorService.java'
MANAGER=ROOT/'app/src/main/java/com/yamo/snorelab/ActivityAutoDetectManager.java'
WALK=ROOT/'app/src/main/java/com/yamo/snorelab/WalkingRecorderService.java'
BRIDGE=ROOT/'app/src/main/java/com/yamo/snorelab/AutoDetectSettingsBridge.java'
RECORDS_BRIDGE=ROOT/'app/src/main/java/com/yamo/snorelab/SavedMovementRecordsBridge.java'
AUTO_JS=ROOT/'app/src/main/assets/yamone-v23/v02516-auto-detect.js'
RECORDS_JS=ROOT/'app/src/main/assets/yamone-v23/v02513-records.js'
INDEX=ROOT/'app/src/main/assets/yamone-v23/index.html'
TARGET=ROOT/'app/src/main/assets/yamone-v23'
BUILD=ROOT/'app/build.gradle'


def replace_once(path,old,new,label):
    s=path.read_text(encoding='utf-8')
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'v0.36.06 target not found: {label} ({path})')
    path.write_text(s.replace(old,new,1),encoding='utf-8')


def sub_once(path,pattern,repl,label):
    s=path.read_text(encoding='utf-8')
    ns,n=re.subn(pattern,repl,s,count=1,flags=re.S)
    if n!=1:
        raise SystemExit(f'v0.36.06 regex target not found: {label} ({path})')
    path.write_text(ns,encoding='utf-8')

# ---------------------------------------------------------------------------
# Start detection: the selected 15/30/60 seconds now means supported movement
# time, not wall-clock time after a single candidate hit. 1-2 second gaps are
# tolerated; 3 seconds without valid movement resets the counter to zero.
# ---------------------------------------------------------------------------
replace_once(HYBRID,
'''    private static final long INACTIVE_NOTIFY_MS = 30_000L;
    private static final long RESUME_AFTER_RECORD_MS = 1_500L;''',
'''    private static final long INACTIVE_NOTIFY_MS = 30_000L;
    private static final long START_NO_MOTION_RESET_MS = 3_000L;
    private static final long MOTION_LOCATION_FRESH_MS = 12_000L;
    private static final long RESUME_AFTER_RECORD_MS = 1_500L;''',
'start no-motion reset constants')

replace_once(HYBRID,
'''    static final String KEY_CANDIDATE = "hybrid_candidate";
    static final String KEY_CANDIDATE_SINCE = "hybrid_candidate_since";
    static final String KEY_CONFIDENCE = "hybrid_confidence";''',
'''    static final String KEY_CANDIDATE = "hybrid_candidate";
    static final String KEY_CANDIDATE_SINCE = "hybrid_candidate_since";
    static final String KEY_CANDIDATE_ACTIVE_MS = "hybrid_candidate_active_ms";
    static final String KEY_CONFIDENCE = "hybrid_confidence";''',
'candidate active-time key')

replace_once(HYBRID,
'''    private String candidate = "none";
    private long candidateSince;
    private long candidateLastSupport;''',
'''    private String candidate = "none";
    private long candidateSince;
    private long candidateActiveMs;
    private long candidateLastSupport;
    private long lastEvaluateAt;''',
'candidate active-time fields')

replace_once(HYBRID,
'''                .putString(KEY_CANDIDATE, "none")
                .putLong(KEY_CANDIDATE_SINCE, 0L)
                .putInt(KEY_CONFIDENCE, 0)''',
'''                .putString(KEY_CANDIDATE, "none")
                .putLong(KEY_CANDIDATE_SINCE, 0L)
                .putLong(KEY_CANDIDATE_ACTIVE_MS, 0L)
                .putInt(KEY_CONFIDENCE, 0)''',
'reset candidate active time')

replace_once(HYBRID,
'''        long now = System.currentTimeMillis();
        if (lastLocationWallMs > 0 && now - lastLocationWallMs > LOCATION_STALE_MS) speedKmh *= 0.75f;''',
'''        long now = System.currentTimeMillis();
        long evalDeltaMs = lastEvaluateAt > 0L ? Math.min(2_000L, Math.max(0L, now - lastEvaluateAt)) : 0L;
        lastEvaluateAt = now;
        if (lastLocationWallMs > 0 && now - lastLocationWallMs > LOCATION_STALE_MS) speedKmh *= 0.75f;''',
'evaluation active-time delta')

replace_once(HYBRID,
'''        updateCandidate(best, now);
        String platform = gVehicle ? "vehicle→cycling" : gBike ? "bicycle" : gRun ? "running" : gWalk ? "walking" : "none";
        prefs.edit().putBoolean(KEY_RUNNING,true).putString(KEY_CANDIDATE,candidate).putLong(KEY_CANDIDATE_SINCE,candidateSince)
                .putInt(KEY_CONFIDENCE,score).putFloat(KEY_CADENCE,cadence).putFloat(KEY_SPEED_KMH,speedKmh)''',
'''        boolean validMovement = hasValidMovementEvidence(best, cadence, gBike, gVehicle, now);
        if (!validMovement) best = "none";
        updateCandidate(best, validMovement, now, evalDeltaMs);
        String platform = gVehicle ? "vehicle→cycling" : gBike ? "bicycle" : gRun ? "running" : gWalk ? "walking" : "none";
        prefs.edit().putBoolean(KEY_RUNNING,true).putString(KEY_CANDIDATE,candidate).putLong(KEY_CANDIDATE_SINCE,candidateSince)
                .putLong(KEY_CANDIDATE_ACTIVE_MS,candidateActiveMs)
                .putInt(KEY_CONFIDENCE,score).putFloat(KEY_CADENCE,cadence).putFloat(KEY_SPEED_KMH,speedKmh)''',
'valid movement gate and active-time persistence')

helper='''
    private boolean hasValidMovementEvidence(String best, float cadence, boolean platformBike, boolean platformVehicle, long now){
        if ("none".equals(best)) return false;
        boolean locationFresh = lastLocationWallMs > 0L && now - lastLocationWallMs <= MOTION_LOCATION_FRESH_MS;
        boolean gait = stepTimes.size() >= 3 && cadence >= 35f && cadence <= 235f;
        boolean recentHardwareGait = lastHardwareStepMs > 0L && now - lastHardwareStepMs <= START_NO_MOTION_RESET_MS && gait;
        if ("walking".equals(best)) {
            boolean gpsWalking = locationFresh && speedKmh >= 0.6f && speedKmh <= 11f && gait;
            return recentHardwareGait || gpsWalking;
        }
        if ("running".equals(best)) {
            boolean hardwareRun = recentHardwareGait && cadence >= 100f;
            boolean gpsRun = locationFresh && speedKmh >= 4.5f && gait && cadence >= 80f;
            return hardwareRun || gpsRun;
        }
        if ("cycling".equals(best)) {
            float minimumRideKmh = (platformBike || platformVehicle) ? 1.5f : 4.0f;
            return locationFresh && speedKmh >= minimumRideKmh;
        }
        return false;
    }

'''
s=HYBRID.read_text(encoding='utf-8')
marker='    private int walkScore(float cadence,float speed,boolean platformWalk,boolean platformRun){'
if 'private boolean hasValidMovementEvidence' not in s:
    if marker not in s: raise SystemExit('v0.36.06 movement evidence insertion target missing')
    HYBRID.write_text(s.replace(marker,helper+marker,1),encoding='utf-8')

sub_once(HYBRID,
r'    private void updateCandidate\(String best,long now\)\{.*?\n    \}\n\n    private void setCandidate\(String value,long now\)\{.*?\n    \}',
'''    private void updateCandidate(String best, boolean validMovement, long now, long evalDeltaMs){
        if(!"none".equals(best) && validMovement){
            if(best.equals(candidate)){
                mismatchCandidate="none";
                mismatchSince=0;
                candidateLastSupport=now;
                candidateActiveMs += Math.max(0L, evalDeltaMs);
            } else if("none".equals(candidate)) {
                setCandidate(best,now);
            } else if(!best.equals(mismatchCandidate)) {
                mismatchCandidate=best;
                mismatchSince=now;
            } else if(now-mismatchSince>=START_NO_MOTION_RESET_MS) {
                setCandidate(best,now);
            }
        } else if(!"none".equals(candidate) && now-candidateLastSupport>=START_NO_MOTION_RESET_MS) {
            setCandidate("none",now);
        }

        if(!"none".equals(candidate)){
            inactiveSince=0;
            inactiveSignaled=false;
            if(validMovement && best.equals(candidate)) ActivityAutoDetectManager.onHybridMotionResumed(this);
            int confirmSec=ActivityAutoDetectManager.confirmSeconds(this);
            if(candidateActiveMs>=confirmSec*1000L&&!candidate.equals(lastConfirmed)){
                lastConfirmed=candidate;
                ActivityAutoDetectManager.onHybridCandidateConfirmed(this,candidate);
            }
        } else {
            if(inactiveSince<=0)inactiveSince=now;
            if(!inactiveSignaled&&now-inactiveSince>=INACTIVE_NOTIFY_MS){
                inactiveSignaled=true;
                lastConfirmed="none";
                ActivityAutoDetectManager.onHybridInactive(this);
            }
        }
    }

    private void setCandidate(String value,long now){
        candidate=value;
        candidateSince="none".equals(value)?0L:now;
        candidateActiveMs=0L;
        candidateLastSupport="none".equals(value)?0L:now;
        mismatchCandidate="none";
        mismatchSince=0;
        if("none".equals(value))lastConfirmed="none";
    }''',
'sustained supported-time candidate logic')

replace_once(HYBRID,
'''        prefs.edit().putBoolean(KEY_RUNNING,false).putString(KEY_CANDIDATE,"none").apply();''',
'''        prefs.edit().putBoolean(KEY_RUNNING,false).putString(KEY_CANDIDATE,"none")
                .putLong(KEY_CANDIDATE_ACTIVE_MS,0L).apply();''',
'clear active time on detector stop')

# ---------------------------------------------------------------------------
# Configurable auto-end: 1/3/5 minutes of continuous inactivity. The movement
# recorder, not the suspended start detector, owns this timer while recording.
# ---------------------------------------------------------------------------
replace_once(MANAGER,
'''    public static final String KEY_END_MODE = "end_mode";''',
'''    public static final String KEY_END_MODE = "end_mode";
    public static final String KEY_END_INACTIVITY_MIN = "end_inactivity_min";''',
'auto-end inactivity setting key')

# Keep an internal throttle so frequent GPS/step motion does not hammer AlarmManager.
s=MANAGER.read_text(encoding='utf-8')
marker='    private static final int REQ_END_KEEP = 6671;'
if 'KEY_END_ARMED_AT' not in s:
    if marker not in s: raise SystemExit('v0.36.06 end-arm insertion target missing')
    s=s.replace(marker,marker+'\n    private static final String KEY_END_ARMED_AT = "end_armed_at";',1)
    MANAGER.write_text(s,encoding='utf-8')

sub_once(MANAGER,
r'    private static void checkAutoEnd\(Context context\) \{.*?\n    \}\n\n    private static void endAndSave',
'''    private static void checkAutoEnd(Context context) {
        if (!isRecording(context) || !startedByAutoDetect(context)) return;
        SharedPreferences runtime = context.getSharedPreferences(WalkingRecorderService.PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long lastMotion = runtime.getLong(WalkingRecorderService.KEY_LAST_MEANINGFUL_MOTION_MS,
                runtime.getLong(WalkingRecorderService.KEY_START_MS, now));
        long threshold = endInactivityMs(context);
        long age = Math.max(0L, now - lastMotion);
        if (age < threshold) {
            scheduleAlarm(context, ACTION_END_CHECK, REQ_END_CHECK, null, threshold - age);
            prefs(context).edit().putLong(KEY_END_ARMED_AT, now).apply();
            return;
        }
        String mode = prefs(context).getString(KEY_END_MODE, "ask");
        if ("auto".equals(mode)) endAndSave(context);
        else showEndPrompt(context);
    }

    private static void endAndSave''',
'continuous inactivity auto-end check')

sub_once(MANAGER,
r'    private static void keepRecording\(Context context\) \{.*?\n    \}',
'''    private static void keepRecording(Context context) {
        cancelEndPrompt(context);
        if (!isRecording(context) || !startedByAutoDetect(context)) return;
        long now = System.currentTimeMillis();
        context.getSharedPreferences(WalkingRecorderService.PREFS, Context.MODE_PRIVATE)
                .edit().putLong(WalkingRecorderService.KEY_LAST_MEANINGFUL_MOTION_MS, now).apply();
        armAutoEnd(context);
    }''',
'keep recording resets inactivity window')

insert='''
    static int sanitizeEndInactivityMin(int minutes) {
        return minutes == 1 || minutes == 5 ? minutes : 3;
    }

    private static long endInactivityMs(Context context) {
        return sanitizeEndInactivityMin(prefs(context).getInt(KEY_END_INACTIVITY_MIN, 3)) * 60_000L;
    }

    static void armAutoEnd(Context context) {
        if (!isRecording(context) || !startedByAutoDetect(context)) return;
        long now = System.currentTimeMillis();
        cancelAlarm(context, ACTION_END_CHECK, REQ_END_CHECK, null);
        scheduleAlarm(context, ACTION_END_CHECK, REQ_END_CHECK, null, endInactivityMs(context));
        prefs(context).edit().putLong(KEY_END_ARMED_AT, now).apply();
    }

    static void onRecordingMotion(Context context) {
        if (!isRecording(context) || !startedByAutoDetect(context)) return;
        cancelEndPrompt(context);
        long now = System.currentTimeMillis();
        SharedPreferences p = prefs(context);
        long armedAt = p.getLong(KEY_END_ARMED_AT, 0L);
        if (now - armedAt < 10_000L) return;
        cancelAlarm(context, ACTION_END_CHECK, REQ_END_CHECK, null);
        scheduleAlarm(context, ACTION_END_CHECK, REQ_END_CHECK, null, endInactivityMs(context));
        p.edit().putLong(KEY_END_ARMED_AT, now).apply();
    }

'''
s=MANAGER.read_text(encoding='utf-8')
marker='    static void onRecordingStarted(Context context) {'
if 'static void armAutoEnd(Context context)' not in s:
    if marker not in s: raise SystemExit('v0.36.06 auto-end helper insertion target missing')
    MANAGER.write_text(s.replace(marker,insert+marker,1),encoding='utf-8')

replace_once(MANAGER,
'''        cancelAlarm(app, ACTION_END_CHECK, REQ_END_CHECK, null);
        cancelEndPrompt(app);
        ActivityHybridDetectorService.resumeAfterRecording(app);''',
'''        cancelAlarm(app, ACTION_END_CHECK, REQ_END_CHECK, null);
        cancelEndPrompt(app);
        prefs(app).edit().remove(KEY_END_ARMED_AT).apply();
        ActivityHybridDetectorService.resumeAfterRecording(app);''',
'clear auto-end arm state at record finish')

# Tag auto-detect records with whether the detector auto-started them or the user
# manually accepted the detection prompt.
replace_once(MANAGER,
'''                .setAction(WalkingRecorderService.ACTION_START)
                .putExtra("activity_type", recorderType)
                .putExtra(WalkingRecorderService.EXTRA_STARTED_BY_AUTO_DETECT, true);''',
'''                .setAction(WalkingRecorderService.ACTION_START)
                .putExtra("activity_type", recorderType)
                .putExtra(WalkingRecorderService.EXTRA_STARTED_BY_AUTO_DETECT, true)
                .putExtra(WalkingRecorderService.EXTRA_AUTO_DETECT_START_MODE,
                        "auto".equals(prefs(context).getString(KEY_START_MODE, "ask")) ? "auto" : "manual");''',
'auto/manual auto-detect record source extra')

# ---------------------------------------------------------------------------
# Movement recorder tracks meaningful motion during an active auto-detected
# record and persists the auto/manual detection source into session metadata.
# ---------------------------------------------------------------------------
replace_once(WALK,
'''    public static final String ACTION_STOP = "com.yamo.snorelab.WALK_STOP";''',
'''    public static final String ACTION_STOP = "com.yamo.snorelab.WALK_STOP";
    public static final String EXTRA_AUTO_DETECT_START_MODE = "auto_detect_start_mode";''',
'auto-detect start mode extra')

replace_once(WALK,
'''    public static final String KEY_LAST_ACCEPTED_FIX_MS = "last_accepted_fix_ms";''',
'''    public static final String KEY_LAST_ACCEPTED_FIX_MS = "last_accepted_fix_ms";
    public static final String KEY_AUTO_DETECT_START_MODE = "auto_detect_start_mode";
    public static final String KEY_LAST_MEANINGFUL_MOTION_MS = "last_meaningful_motion_ms";''',
'auto-detect mode and motion runtime keys')

replace_once(WALK,
'''    private String activityType = "walking";''',
'''    private String activityType = "walking";
    private String autoDetectStartMode = "";
    private long lastMeaningfulMotionMs;''',
'auto-detect mode and motion fields')

replace_once(WALK,
'''        String requestedType = intent.getStringExtra("activity_type");''',
'''        boolean autoDetected = intent.getBooleanExtra(EXTRA_STARTED_BY_AUTO_DETECT, false);
        autoDetectStartMode = autoDetected ? sanitizeAutoDetectStartMode(intent.getStringExtra(EXTRA_AUTO_DETECT_START_MODE)) : "";
        String requestedType = intent.getStringExtra("activity_type");''',
'read auto-detect start source')

replace_once(WALK,
'''        startMs = System.currentTimeMillis();
        goalDistanceM = Math.max(0, intent.getLongExtra("goal_distance_m", 0));''',
'''        startMs = System.currentTimeMillis();
        lastMeaningfulMotionMs = startMs;
        goalDistanceM = Math.max(0, intent.getLongExtra("goal_distance_m", 0));''',
'initialize meaningful motion clock')

replace_once(WALK,
'''        if (movingPoint) {
            distanceM += d;''',
'''        if (movingPoint) {
            markMeaningfulMotion();
            distanceM += d;''',
'GPS movement refreshes inactivity clock')

replace_once(WALK,
'''        if (steps > previousSteps) lastStepDetectedMs = System.currentTimeMillis();
        persistRuntime();''',
'''        if (steps > previousSteps) {
            lastStepDetectedMs = System.currentTimeMillis();
            markMeaningfulMotion();
        }
        persistRuntime();''',
'steps refresh inactivity clock')

replace_once(WALK,
'''                .putLong(KEY_PERSISTED_AT_MS, System.currentTimeMillis())
                .putLong(KEY_LAST_ACCEPTED_FIX_MS, lastAcceptedFixWallMs)''',
'''                .putLong(KEY_PERSISTED_AT_MS, System.currentTimeMillis())
                .putLong(KEY_LAST_ACCEPTED_FIX_MS, lastAcceptedFixWallMs)
                .putString(KEY_AUTO_DETECT_START_MODE, autoDetectStartMode)
                .putLong(KEY_LAST_MEANINGFUL_MOTION_MS, lastMeaningfulMotionMs)''',
'persist record source and motion clock')

replace_once(WALK,
'''        lastAcceptedFixWallMs = runtime.getLong(KEY_LAST_ACCEPTED_FIX_MS, 0L);''',
'''        lastAcceptedFixWallMs = runtime.getLong(KEY_LAST_ACCEPTED_FIX_MS, 0L);
        autoDetectStartMode = runtime.getString(KEY_AUTO_DETECT_START_MODE, "");
        lastMeaningfulMotionMs = runtime.getLong(KEY_LAST_MEANINGFUL_MOTION_MS, startMs);''',
'restore record source and motion clock')

replace_once(WALK,
'''            m.put("type", activityType);
            m.put("status", status);''',
'''            m.put("type", activityType);
            m.put("autoDetected", !autoDetectStartMode.isEmpty());
            m.put("autoDetectStartMode", autoDetectStartMode);
            m.put("status", status);''',
'write record source metadata')

helper='''
    private static String sanitizeAutoDetectStartMode(String value) {
        if ("auto".equals(value)) return "auto";
        if ("manual".equals(value)) return "manual";
        return "manual";
    }

    private void markMeaningfulMotion() {
        long now = System.currentTimeMillis();
        lastMeaningfulMotionMs = now;
        if (runtime != null) runtime.edit().putLong(KEY_LAST_MEANINGFUL_MOTION_MS, now).apply();
        ActivityAutoDetectManager.onRecordingMotion(this);
    }

'''
s=WALK.read_text(encoding='utf-8')
marker='    private void persistRuntime() {'
if 'private void markMeaningfulMotion()' not in s:
    if marker not in s: raise SystemExit('v0.36.06 motion helper insertion target missing')
    WALK.write_text(s.replace(marker,helper+marker,1),encoding='utf-8')

replace_once(WALK,
'''        if (!isCycling()) startSteps();
        persistRuntime();
        writeMeta("recording", 0L);''',
'''        if (!isCycling()) startSteps();
        persistRuntime();
        ActivityAutoDetectManager.armAutoEnd(this);
        writeMeta("recording", 0L);''',
'arm auto-end after runtime source is persisted')

# ---------------------------------------------------------------------------
# Settings bridge/UI: explicit 1/3/5 minute inactivity threshold and live
# supported movement seconds instead of wall-clock candidate age.
# ---------------------------------------------------------------------------
replace_once(BRIDGE,
'''            out.put("endMode", sanitizeEndMode(
                    prefs.getString(ActivityAutoDetectManager.KEY_END_MODE, "ask")));''',
'''            out.put("endMode", sanitizeEndMode(
                    prefs.getString(ActivityAutoDetectManager.KEY_END_MODE, "ask")));
            out.put("endInactivityMin", ActivityAutoDetectManager.sanitizeEndInactivityMin(
                    prefs.getInt(ActivityAutoDetectManager.KEY_END_INACTIVITY_MIN, 3)));''',
'bridge auto-end inactivity output')

replace_once(BRIDGE,
'''            out.put("hybridCandidateSince", prefs.getLong(ActivityHybridDetectorService.KEY_CANDIDATE_SINCE, 0L));''',
'''            out.put("hybridCandidateSince", prefs.getLong(ActivityHybridDetectorService.KEY_CANDIDATE_SINCE, 0L));
            out.put("hybridActiveMs", prefs.getLong(ActivityHybridDetectorService.KEY_CANDIDATE_ACTIVE_MS, 0L));''',
'bridge supported movement time output')

replace_once(BRIDGE,
'''            String endMode = sanitizeEndMode(in.optString("endMode", "ask"));''',
'''            String endMode = sanitizeEndMode(in.optString("endMode", "ask"));
            int endInactivityMin = ActivityAutoDetectManager.sanitizeEndInactivityMin(
                    in.optInt("endInactivityMin", prefs.getInt(ActivityAutoDetectManager.KEY_END_INACTIVITY_MIN, 3)));''',
'bridge auto-end inactivity input')

replace_once(BRIDGE,
'''                    .putString(ActivityAutoDetectManager.KEY_END_MODE, endMode)''',
'''                    .putString(ActivityAutoDetectManager.KEY_END_MODE, endMode)
                    .putInt(ActivityAutoDetectManager.KEY_END_INACTIVITY_MIN, endInactivityMin)''',
'persist auto-end inactivity setting')

replace_once(AUTO_JS,
'''sensitivity:'normal',confirmSeconds:30,endMode:'ask',notifySound:true''',
'''sensitivity:'normal',confirmSeconds:30,endMode:'ask',endInactivityMin:3,notifySound:true''',
'JS default end inactivity')

replace_once(AUTO_JS,
'''          endMode:s.endMode==='auto'?'auto':'ask',
          notifySound:s.notifySound!==false,''',
'''          endMode:s.endMode==='auto'?'auto':'ask',
          endInactivityMin:[1,3,5].includes(Number(s.endInactivityMin))?Number(s.endInactivityMin):3,
          notifySound:s.notifySound!==false,''',
'JS save end inactivity')

replace_once(AUTO_JS,
'''      ${settingSection('자동 종료',`<div class="card setting-block v2516-two">${settingChips('v2516EndMode',[['auto','자동 종료'],['ask','확인 후 종료']],s.endMode)}</div>`)}
      ${settingSection('자동감지 알림',''',
'''      ${settingSection('자동 종료',`<div class="card setting-block v2516-two">${settingChips('v2516EndMode',[['auto','자동 종료'],['ask','확인 후 종료']],s.endMode)}</div>`)}
      ${settingSection('비활동 종료 시간',`<div class="card setting-block">${settingChips('v3606EndInactive',[['1','1분 이후'],['3','3분 이후'],['5','5분 이후']],String(s.endInactivityMin||3))}</div>`)}
      ${settingSection('자동감지 알림',''',
'UI inactivity duration setting')

replace_once(AUTO_JS,
'''    screen.querySelectorAll('[data-setting-chip="v2516EndMode"]').forEach(btn=>btn.onclick=()=>mutate(x=>{x.endMode=btn.dataset.value;},false));''',
'''    screen.querySelectorAll('[data-setting-chip="v2516EndMode"]').forEach(btn=>btn.onclick=()=>mutate(x=>{x.endMode=btn.dataset.value;},false));
    screen.querySelectorAll('[data-setting-chip="v3606EndInactive"]').forEach(btn=>btn.onclick=()=>mutate(x=>{x.endInactivityMin=Number(btn.dataset.value);},false));''',
'bind inactivity duration chips')

# Supported-motion seconds are what the user selected 15/30/60 seconds for.
sub_once(AUTO_JS,
r'''    const elapsed=s\.hybridCandidateSince\?Math\.max\(0,Math\.floor\(\(Date\.now\(\)-Number\(s\.hybridCandidateSince\)\)/1000\)\):0;''',
'''    const elapsed=Math.max(0,Math.floor((Number(s.hybridActiveMs)||0)/1000));''',
'live diagnostic uses supported movement time')

replace_once(AUTO_JS,
'''    return `자체감지 ${s.hybridRunning?'동작 중':'대기'} · ${label}${s.hybridCandidate&&s.hybridCandidate!=='none'?` ${Math.min(elapsed,target)}/${target}초`:''} · 걸음 ${cadence}spm · 속도 ${speed}km/h · 점수 ${confidence}${platform}`;''',
'''    return `자체감지 ${s.hybridRunning?'동작 중':'대기'} · ${label}${s.hybridCandidate&&s.hybridCandidate!=='none'?` · 유효 움직임 ${Math.min(elapsed,target)}/${target}초`:''} · 걸음 ${cadence}spm · 속도 ${speed}km/h · 점수 ${confidence}${platform}`;''',
'live diagnostic label')

replace_once(AUTO_JS,
'''      <div class="setting-info">Android 활동 이벤트와 자체 센서 판단을 함께 사용합니다. 같은 활동 후보가 선택 시간 이상 유지되면 자동 시작합니다. 기록 중에는 자동감지를 중지하고, 기록 종료 후 초기화하여 다시 감지합니다.</div>''',
'''      <div class="setting-info">Android 활동 이벤트와 자체 센서 판단을 함께 사용합니다. 선택한 시간은 단순 경과시간이 아니라 유효한 움직임 시간입니다. 움직임 증거가 3초 이상 끊기면 시작 카운트를 0초로 초기화합니다.</div>
      <div class="setting-info">기록 중에는 시작 자동감지를 중지합니다. 자동감지로 시작된 기록은 설정한 1·3·5분 동안 실제 이동이 없을 때 종료 또는 종료 확인을 실행하고, 기록 종료 후 자동감지를 초기화하여 다시 시작합니다.</div>''',
'UI sustained motion and auto-end explanation')

# ---------------------------------------------------------------------------
# Records > 이동: distinguish auto-start and user-accepted auto-detect records.
# ---------------------------------------------------------------------------
replace_once(RECORDS_BRIDGE,
'''            item.put("type", meta.optString("type", "walking"));''',
'''            item.put("type", meta.optString("type", "walking"));
            item.put("autoDetected", meta.optBoolean("autoDetected", false));
            item.put("autoDetectStartMode", meta.optString("autoDetectStartMode", ""));''',
'expose record auto-detect source')

replace_once(RECORDS_JS,
'''  function titleOf(r){const info=typeInfo(r.type);return `${daypart(r.startEpochMs)} ${info.label}`;}''',
'''  function titleOf(r){
    const source=String(r&&r.autoDetectStartMode||'');
    if(source==='auto')return '자동감지 이동(자동)';
    if(source==='manual')return '자동감지 이동(수동)';
    const info=typeInfo(r.type);return `${daypart(r.startEpochMs)} ${info.label}`;
  }''',
'record title auto/manual distinction')

# Version marker.
(TARGET/'v03606-version.js').write_bytes((ROOT/'design-preview/v03606-version.js').read_bytes())
s=INDEX.read_text(encoding='utf-8')
s=s.replace('  <script src="v03605-version.js"></script>\n','').replace('<script src="v03605-version.js"></script>\n','')
if 'v03606-version.js' not in s:
    if '</body>' not in s: raise SystemExit('v0.36.06 index body missing')
    s=s.replace('</body>','  <script src="v03606-version.js"></script>\n</body>',1)
INDEX.write_text(s,encoding='utf-8')

replace_once(BUILD,
"        versionCode 99\n        versionName '0.36.05'",
"        versionCode 100\n        versionName '0.36.06'",
'android version')

print('Applied v0.36.06 sustained motion window, configurable inactivity end, and auto/manual record labels.')
