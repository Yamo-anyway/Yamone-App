#!/usr/bin/env python3
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parent.parent
HYBRID=ROOT/'app/src/main/java/com/yamo/snorelab/ActivityHybridDetectorService.java'
MANAGER=ROOT/'app/src/main/java/com/yamo/snorelab/ActivityAutoDetectManager.java'
WALK=ROOT/'app/src/main/java/com/yamo/snorelab/WalkingRecorderService.java'
APP=ROOT/'app/src/main/java/com/yamo/snorelab/YamoneApplication.java'
BRIDGE=ROOT/'app/src/main/java/com/yamo/snorelab/AutoDetectSettingsBridge.java'
JS=ROOT/'app/src/main/assets/yamone-v23/v02516-auto-detect.js'
INDEX=ROOT/'app/src/main/assets/yamone-v23/index.html'
TARGET=ROOT/'app/src/main/assets/yamone-v23'
BUILD=ROOT/'app/build.gradle'


def replace_once(path,old,new,label):
    s=path.read_text(encoding='utf-8')
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'v0.36.05 target not found: {label} ({path})')
    path.write_text(s.replace(old,new,1),encoding='utf-8')


def sub_once(path,pattern,repl,label):
    s=path.read_text(encoding='utf-8')
    ns,n=re.subn(pattern,repl,s,count=1,flags=re.S)
    if n!=1:
        raise SystemExit(f'v0.36.05 regex target not found: {label} ({path})')
    path.write_text(ns,encoding='utf-8')

# ---------------------------------------------------------------------------
# Hybrid detector lifecycle:
# - detector is suspended and reset as soon as a movement record starts
# - detector restarts from a clean slate after the record finishes
# - platform hints received while recording are ignored so they cannot leak into
#   the next detection session.
# ---------------------------------------------------------------------------
replace_once(HYBRID,
'''    private static final long INACTIVE_NOTIFY_MS = 30_000L;''',
'''    private static final long INACTIVE_NOTIFY_MS = 30_000L;
    private static final long RESUME_AFTER_RECORD_MS = 1_500L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());''',
'hybrid lifecycle constants')

replace_once(HYBRID,
'''    static final String KEY_LAST_EVAL = "hybrid_last_eval";''',
'''    static final String KEY_LAST_EVAL = "hybrid_last_eval";
    static final String KEY_PAUSED_FOR_RECORDING = "hybrid_paused_for_recording";''',
'hybrid paused state key')

replace_once(HYBRID,
'''    private double accelMotionEma;
    private double gyroMotionEma;''',
'''    private double accelMotionEma;
    private double gyroMotionEma;
    private long lastHardwareStepMs;
    private long lastPseudoStepMs;
    private boolean accelStepAbove;''',
'hybrid pseudo-step fields')

replace_once(HYBRID,
'''    public static void sync(Context context) {
        Context app = context.getApplicationContext();
        if (!ActivityAutoDetectManager.anyEnabled(app) || !hasForegroundLocation(app)) {
            stop(app);
            return;
        }
        ensureRunning(app);
    }''',
'''    public static void sync(Context context) {
        Context app = context.getApplicationContext();
        if (!ActivityAutoDetectManager.anyEnabled(app) || !hasForegroundLocation(app)) {
            stop(app);
            return;
        }
        if (isMovementRecording(app)) {
            suspendForRecording(app);
            return;
        }
        ensureRunning(app);
    }''',
'hybrid sync respects active recording')

replace_once(HYBRID,
'''    static void ensureRunning(Context context) {
        Context app = context.getApplicationContext();
        if (!ActivityAutoDetectManager.anyEnabled(app) || !hasForegroundLocation(app)) return;
        Intent i = new Intent(app, ActivityHybridDetectorService.class);''',
'''    static void ensureRunning(Context context) {
        Context app = context.getApplicationContext();
        if (!ActivityAutoDetectManager.anyEnabled(app) || !hasForegroundLocation(app) || isMovementRecording(app)) return;
        Intent i = new Intent(app, ActivityHybridDetectorService.class);''',
'hybrid ensure running respects active recording')

insert='''
    static void suspendForRecording(Context context) {
        Context app = context.getApplicationContext();
        resetPersistentState(app, true);
        stop(app);
    }

    static void resumeAfterRecording(Context context) {
        final Context app = context.getApplicationContext();
        resetPersistentState(app, false);
        MAIN.removeCallbacksAndMessages(app);
        MAIN.postAtTime(() -> {
            if (!isMovementRecording(app) && ActivityAutoDetectManager.anyEnabled(app)) {
                ActivityAutoDetectManager.syncRegistration(app);
                ensureRunning(app);
            }
        }, app, android.os.SystemClock.uptimeMillis() + RESUME_AFTER_RECORD_MS);
    }

    private static boolean isMovementRecording(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(WalkingRecorderService.PREFS, Context.MODE_PRIVATE)
                .getBoolean(WalkingRecorderService.KEY_RECORDING, false);
    }

    private static void resetPersistentState(Context context, boolean pausedForRecording) {
        SharedPreferences p = ActivityAutoDetectManager.prefs(context);
        p.edit()
                .putBoolean(KEY_RUNNING, false)
                .putBoolean(KEY_PAUSED_FOR_RECORDING, pausedForRecording)
                .putString(KEY_CANDIDATE, "none")
                .putLong(KEY_CANDIDATE_SINCE, 0L)
                .putInt(KEY_CONFIDENCE, 0)
                .putFloat(KEY_CADENCE, 0f)
                .putFloat(KEY_SPEED_KMH, 0f)
                .putFloat(KEY_ACCEL, 0f)
                .putFloat(KEY_GYRO, 0f)
                .putString(KEY_PLATFORM, "none")
                .putLong(KEY_LAST_EVAL, 0L)
                .putBoolean(KEY_PLATFORM_WALK, false)
                .putBoolean(KEY_PLATFORM_RUN, false)
                .putBoolean(KEY_PLATFORM_BIKE, false)
                .putBoolean(KEY_PLATFORM_VEHICLE, false)
                .putLong(KEY_PLATFORM_WALK_AT, 0L)
                .putLong(KEY_PLATFORM_RUN_AT, 0L)
                .putLong(KEY_PLATFORM_BIKE_AT, 0L)
                .putLong(KEY_PLATFORM_VEHICLE_AT, 0L)
                .apply();
    }

'''
s=HYBRID.read_text(encoding='utf-8')
marker='    static void stop(Context context) {'
if 'static void suspendForRecording(Context context)' not in s:
    if marker not in s: raise SystemExit('v0.36.05 hybrid lifecycle insertion target missing')
    HYBRID.write_text(s.replace(marker,insert+marker,1),encoding='utf-8')

replace_once(HYBRID,
'''    static void notePlatformActivity(Context context, int activityType, boolean entering) {
        SharedPreferences p = ActivityAutoDetectManager.prefs(context);''',
'''    static void notePlatformActivity(Context context, int activityType, boolean entering) {
        if (isMovementRecording(context)) return;
        SharedPreferences p = ActivityAutoDetectManager.prefs(context);''',
'ignore platform hints while recording')

replace_once(HYBRID,
'''    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!ActivityAutoDetectManager.anyEnabled(this) || !hasForegroundLocation(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }''',
'''    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!ActivityAutoDetectManager.anyEnabled(this) || !hasForegroundLocation(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (isMovementRecording(this)) {
            resetPersistentState(this, true);
            stopSelf();
            return START_NOT_STICKY;
        }
        prefs.edit().putBoolean(KEY_PAUSED_FOR_RECORDING, false).apply();
        return START_STICKY;
    }''',
'hybrid service stop while recording')

# Get an immediate coarse speed seed instead of waiting for the first balanced-location batch.
replace_once(HYBRID,
'''        try { fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper()); }
        catch (SecurityException ignored) { }
    }''',
'''        try {
            fused.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) acceptLocation(location);
            });
            fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
        } catch (SecurityException ignored) { }
    }''',
'prime detector with last location')

# Accelerometer fallback step peaks make walking/running detectable even on devices whose
# hardware step detector is slow to wake. A refractory interval and the 15/30/60 second
# confirmation window keep a one-off shake from becoming a completed activity detection.
replace_once(HYBRID,
'''        if (type == Sensor.TYPE_ACCELEROMETER && event.values.length >= 3) {
            double x = event.values[0], y = event.values[1], z = event.values[2];
            double motion = Math.abs(Math.sqrt(x*x + y*y + z*z) - SensorManager.GRAVITY_EARTH);
            accelMotionEma = accelMotionEma * 0.92 + motion * 0.08;
        } else if (type == Sensor.TYPE_GYROSCOPE && event.values.length >= 3) {''',
'''        if (type == Sensor.TYPE_ACCELEROMETER && event.values.length >= 3) {
            double x = event.values[0], y = event.values[1], z = event.values[2];
            double motion = Math.abs(Math.sqrt(x*x + y*y + z*z) - SensorManager.GRAVITY_EARTH);
            accelMotionEma = accelMotionEma * 0.90 + motion * 0.10;
            long now = System.currentTimeMillis();
            boolean above = motion >= 0.42;
            if (above && !accelStepAbove
                    && now - lastHardwareStepMs > 2_500L
                    && now - lastPseudoStepMs >= 280L) {
                lastPseudoStepMs = now;
                addSteps(1);
            }
            accelStepAbove = above;
        } else if (type == Sensor.TYPE_GYROSCOPE && event.values.length >= 3) {''',
'accelerometer pseudo step fallback')

replace_once(HYBRID,
'''        } else if (type == Sensor.TYPE_STEP_DETECTOR) {
            addSteps(1);
        } else if (type == Sensor.TYPE_STEP_COUNTER && event.values.length > 0) {''',
'''        } else if (type == Sensor.TYPE_STEP_DETECTOR) {
            lastHardwareStepMs = System.currentTimeMillis();
            addSteps(1);
        } else if (type == Sensor.TYPE_STEP_COUNTER && event.values.length > 0) {''',
'hardware step timestamp')

replace_once(HYBRID,
'''                if (delta > 0) addSteps((int) delta);''',
'''                if (delta > 0) {
                    lastHardwareStepMs = System.currentTimeMillis();
                    addSteps((int) delta);
                }''',
'step counter timestamp')

replace_once(HYBRID,
'''    private void evaluate() {
        if (!ActivityAutoDetectManager.anyEnabled(this)) { stopSelf(); return; }
        long now = System.currentTimeMillis();''',
'''    private void evaluate() {
        if (!ActivityAutoDetectManager.anyEnabled(this)) { stopSelf(); return; }
        if (isMovementRecording(this)) { suspendForRecording(this); return; }
        long now = System.currentTimeMillis();''',
'evaluator suspends during recording')

# Tuning: allow normal gait or a platform WALK/RUN hint to establish the candidate without a
# deliberate phone shake. The explicit confirmation duration remains the anti-false-positive gate.
sub_once(HYBRID,
r'    private int walkScore\(float cadence,float speed,boolean platformWalk,boolean platformRun\)\{.*?\n    \}',
'''    private int walkScore(float cadence,float speed,boolean platformWalk,boolean platformRun){
        int s=platformWalk?45:0;
        if(cadence>=50&&cadence<=155)s+=35; else if(cadence>=35&&cadence<=175)s+=20;
        if(speed>=1&&speed<=7.5)s+=25; else if(speed>=0.4&&speed<=10)s+=10;
        if(accelMotionEma>=0.04&&accelMotionEma<=3.2)s+=12;
        if(gyroMotionEma>=0.015&&gyroMotionEma<=3.0)s+=6;
        if(platformRun)s-=20; if(speed>12)s-=40; return s;
    }''',
'walking wake-friendly score')

sub_once(HYBRID,
r'    private int runScore\(float cadence,float speed,boolean platformRun,boolean platformWalk\)\{.*?\n    \}',
'''    private int runScore(float cadence,float speed,boolean platformRun,boolean platformWalk){
        int s=platformRun?45:0;
        if(cadence>=135&&cadence<=225)s+=35; else if(cadence>=110&&cadence<=235)s+=20;
        if(speed>=6.5&&speed<=24)s+=30; else if(speed>=4.5&&speed<=26)s+=15;
        if(accelMotionEma>=0.18)s+=15; if(gyroMotionEma>=0.05)s+=6;
        if(platformWalk&&cadence<130)s-=15; if(speed>0&&speed<2.5)s-=25; return s;
    }''',
'running wake-friendly score')

# ---------------------------------------------------------------------------
# Manager lifecycle. No cross-activity auto-switch while a record is active: detection is
# intentionally suspended and starts fresh after the record closes.
# ---------------------------------------------------------------------------
sub_once(MANAGER,
r'    static void onHybridCandidateConfirmed\(Context context, String rawType\) \{.*?\n    \}\n\n    static void onHybridInactive',
'''    static void onHybridCandidateConfirmed(Context context, String rawType) {
        final Context app = context.getApplicationContext();
        final String type = normalizeType(rawType);
        SharedPreferences p = prefs(app);
        if (!isEnabled(p, type) || isRecording(app)) return;

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

    static void onRecordingStarted(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences p = prefs(app);
        p.edit()
                .putBoolean(KEY_ACTIVE_WALK, false)
                .putBoolean(KEY_ACTIVE_RUN, false)
                .putBoolean(KEY_ACTIVE_BIKE, false)
                .apply();
        cancelAlarm(app, ACTION_END_CHECK, REQ_END_CHECK, null);
        cancelEndPrompt(app);
        cancelStartPrompt(app, TYPE_WALK);
        cancelStartPrompt(app, TYPE_RUN);
        cancelStartPrompt(app, TYPE_BIKE);
        ActivityHybridDetectorService.suspendForRecording(app);
    }

    static void onRecordingFinished(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences p = prefs(app);
        p.edit()
                .putBoolean(KEY_ACTIVE_WALK, false)
                .putBoolean(KEY_ACTIVE_RUN, false)
                .putBoolean(KEY_ACTIVE_BIKE, false)
                .apply();
        cancelAlarm(app, ACTION_END_CHECK, REQ_END_CHECK, null);
        cancelEndPrompt(app);
        ActivityHybridDetectorService.resumeAfterRecording(app);
    }

    static void onHybridInactive''',
'manager record lifecycle')

# ---------------------------------------------------------------------------
# Recorder calls manager hooks for every manual or automatic movement record.
# ---------------------------------------------------------------------------
replace_once(WALK,
'''        recording = true;
        paused = false;''',
'''        recording = true;
        paused = false;
        ActivityAutoDetectManager.onRecordingStarted(this);''',
'suspend auto-detect when movement record starts')

replace_once(WALK,
'''        runtime.edit()
                .putBoolean(KEY_RECORDING, false)
                .putBoolean(KEY_PAUSED, false)
                .putFloat(KEY_CURRENT_SPEED_KMH, 0f)
                .apply();
        handler.removeCallbacks(ticker);''',
'''        runtime.edit()
                .putBoolean(KEY_RECORDING, false)
                .putBoolean(KEY_PAUSED, false)
                .putFloat(KEY_CURRENT_SPEED_KMH, 0f)
                .apply();
        ActivityAutoDetectManager.onRecordingFinished(this);
        handler.removeCallbacks(ticker);''',
'restart auto-detect after movement record finishes')

# Make a normal foreground app resume repair a detector service that Android killed or that was
# intentionally stopped for the previous record.
replace_once(APP,
'''    @Override public void onActivityResumed(Activity activity) {
        if (activity instanceof MainActivity) {''',
'''    @Override public void onActivityResumed(Activity activity) {
        try { ActivityAutoDetectManager.syncRegistration(activity); } catch (RuntimeException ignored) { }
        if (activity instanceof MainActivity) {''',
'resync auto-detect on app resume')

# Diagnostics explicitly show the intentional pause while a record is in progress.
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

# Version marker.
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

print('Applied v0.36.05 auto-detect reset/resume lifecycle and walking wake improvements.')
