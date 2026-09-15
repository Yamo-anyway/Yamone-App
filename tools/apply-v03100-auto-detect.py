#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MANAGER = ROOT / 'app/src/main/java/com/yamo/snorelab/ActivityAutoDetectManager.java'
SETTINGS_JS = ROOT / 'app/src/main/assets/yamone-v23/v02516-auto-detect.js'


def replace_once(path: Path, old: str, new: str, label: str):
    s = path.read_text(encoding='utf-8')
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'v0.31.00 patch target not found: {label} ({path})')
    path.write_text(s.replace(old, new, 1), encoding='utf-8')

# Keep an IN_VEHICLE transition only as a suppression signal for bicycle candidates.
replace_once(MANAGER,
'''    static final String TYPE_WALK = "walking";
    static final String TYPE_RUN = "running";
    static final String TYPE_BIKE = "cycling";

    private static final String KEY_ACTIVE_WALK = "active_walking";''',
'''    static final String TYPE_WALK = "walking";
    static final String TYPE_RUN = "running";
    static final String TYPE_BIKE = "cycling";
    private static final String TYPE_VEHICLE = "vehicle";

    private static final String KEY_ACTIVE_WALK = "active_walking";''',
'vehicle type')

replace_once(MANAGER,
'''    private static final String KEY_ACTIVE_RUN = "active_running";
    private static final String KEY_ACTIVE_BIKE = "active_cycling";''',
'''    private static final String KEY_ACTIVE_RUN = "active_running";
    private static final String KEY_ACTIVE_BIKE = "active_cycling";
    private static final String KEY_ACTIVE_VEHICLE = "active_vehicle";''',
'vehicle active key')

replace_once(MANAGER,
'''        if (p.getBoolean(KEY_WALK_ENABLED, false)) addTransitions(transitions, DetectedActivity.WALKING);
        if (p.getBoolean(KEY_RUN_ENABLED, false)) addTransitions(transitions, DetectedActivity.RUNNING);
        if (p.getBoolean(KEY_BIKE_ENABLED, false)) addTransitions(transitions, DetectedActivity.ON_BICYCLE);''',
'''        if (p.getBoolean(KEY_WALK_ENABLED, false)) addTransitions(transitions, DetectedActivity.WALKING);
        if (p.getBoolean(KEY_RUN_ENABLED, false)) addTransitions(transitions, DetectedActivity.RUNNING);
        if (p.getBoolean(KEY_BIKE_ENABLED, false)) {
            addTransitions(transitions, DetectedActivity.ON_BICYCLE);
            // Vehicle is never recorded as an activity. It only suppresses bicycle false starts.
            addTransitions(transitions, DetectedActivity.IN_VEHICLE);
        }''',
'register vehicle suppression transition')

replace_once(MANAGER,
'''    private static void onEnter(Context context, String type) {
        SharedPreferences p = prefs(context);
        if (!isEnabled(p, type)) return;
        setActive(p, type, true);''',
'''    private static void onEnter(Context context, String type) {
        SharedPreferences p = prefs(context);
        if (TYPE_VEHICLE.equals(type)) {
            p.edit().putBoolean(KEY_ACTIVE_VEHICLE, true).apply();
            cancelAlarm(context, ACTION_CONFIRM_CANDIDATE, REQ_CANDIDATE_BASE + typeIndex(TYPE_BIKE), TYPE_BIKE);
            cancelAlarm(context, ACTION_REPROMPT, REQ_REPROMPT_BASE + typeIndex(TYPE_BIKE), TYPE_BIKE);
            cancelStartPrompt(context, TYPE_BIKE);
            return;
        }
        if (!isEnabled(p, type)) return;
        setActive(p, type, true);''',
'vehicle enter suppression')

replace_once(MANAGER,
'''    private static void onExit(Context context, String type) {
        SharedPreferences p = prefs(context);
        setActive(p, type, false);''',
'''    private static void onExit(Context context, String type) {
        SharedPreferences p = prefs(context);
        if (TYPE_VEHICLE.equals(type)) {
            p.edit().putBoolean(KEY_ACTIVE_VEHICLE, false).apply();
            return;
        }
        setActive(p, type, false);''',
'vehicle exit')

replace_once(MANAGER,
'''    private static void confirmCandidate(Context context, String type) {
        SharedPreferences p = prefs(context);
        if (!isEnabled(p, type) || !isActive(p, type) || isRecording(context)) return;''',
'''    private static void confirmCandidate(Context context, String type) {
        SharedPreferences p = prefs(context);
        if (!isEnabled(p, type) || !isActive(p, type) || isRecording(context)) return;
        if (TYPE_BIKE.equals(type) && isVehicleActive(p)) return;''',
'candidate vehicle guard')

replace_once(MANAGER,
'''    private static void reprompt(Context context, String type) {
        SharedPreferences p = prefs(context);
        if (!isEnabled(p, type) || !isActive(p, type) || isRecording(context)) return;
        showStartPrompt(context, type);''',
'''    private static void reprompt(Context context, String type) {
        SharedPreferences p = prefs(context);
        if (!isEnabled(p, type) || !isActive(p, type) || isRecording(context)) return;
        if (TYPE_BIKE.equals(type) && isVehicleActive(p)) return;
        showStartPrompt(context, type);''',
'reprompt vehicle guard')

replace_once(MANAGER,
'''    private static void startFromPrompt(Context context, String type) {
        cancelStartPrompt(context, type);
        cancelAlarm(context, ACTION_REPROMPT, REQ_REPROMPT_BASE + typeIndex(type), type);
        if (!isRecording(context)) startRecorder(context, type);''',
'''    private static void startFromPrompt(Context context, String type) {
        cancelStartPrompt(context, type);
        cancelAlarm(context, ACTION_REPROMPT, REQ_REPROMPT_BASE + typeIndex(type), type);
        SharedPreferences p = prefs(context);
        if (TYPE_BIKE.equals(type) && isVehicleActive(p)) return;
        if (!isRecording(context)) startRecorder(context, type);''',
'prompt start vehicle guard')

# If the user says "keep recording" while still inactive, ask again after another inactivity window.
replace_once(MANAGER,
'''    private static void keepRecording(Context context) {
        cancelEndPrompt(context);
    }''',
'''    private static void keepRecording(Context context) {
        cancelEndPrompt(context);
        if (isRecording(context) && startedByAutoDetect(context) && !anyActive(context)) {
            scheduleAlarm(context, ACTION_END_CHECK, REQ_END_CHECK, null, END_INACTIVITY_MS);
        }
    }''',
'recheck after keep recording')

replace_once(MANAGER,
'''        if (!p.getBoolean(KEY_BIKE_ENABLED, false)) {
            e.putBoolean(KEY_ACTIVE_BIKE, false);
            cancelAlarm(context, ACTION_CONFIRM_CANDIDATE, REQ_CANDIDATE_BASE + 2, TYPE_BIKE);
            cancelAlarm(context, ACTION_REPROMPT, REQ_REPROMPT_BASE + 2, TYPE_BIKE);
        }''',
'''        if (!p.getBoolean(KEY_BIKE_ENABLED, false)) {
            e.putBoolean(KEY_ACTIVE_BIKE, false);
            e.putBoolean(KEY_ACTIVE_VEHICLE, false);
            cancelAlarm(context, ACTION_CONFIRM_CANDIDATE, REQ_CANDIDATE_BASE + 2, TYPE_BIKE);
            cancelAlarm(context, ACTION_REPROMPT, REQ_REPROMPT_BASE + 2, TYPE_BIKE);
        }''',
'clear vehicle when bike disabled')

replace_once(MANAGER,
'''    private static String fromDetectedActivity(int activity) {
        if (activity == DetectedActivity.WALKING) return TYPE_WALK;
        if (activity == DetectedActivity.RUNNING) return TYPE_RUN;
        if (activity == DetectedActivity.ON_BICYCLE) return TYPE_BIKE;
        return null;
    }''',
'''    private static String fromDetectedActivity(int activity) {
        if (activity == DetectedActivity.WALKING) return TYPE_WALK;
        if (activity == DetectedActivity.RUNNING) return TYPE_RUN;
        if (activity == DetectedActivity.ON_BICYCLE) return TYPE_BIKE;
        if (activity == DetectedActivity.IN_VEHICLE) return TYPE_VEHICLE;
        return null;
    }''',
'map vehicle transition')

replace_once(MANAGER,
'''    private static boolean isActive(SharedPreferences p, String type) {
        return p.getBoolean(activeKey(type), false);
    }

    private static void setActive''',
'''    private static boolean isActive(SharedPreferences p, String type) {
        return p.getBoolean(activeKey(type), false);
    }

    private static boolean isVehicleActive(SharedPreferences p) {
        return p.getBoolean(KEY_ACTIVE_VEHICLE, false);
    }

    static boolean vehicleSuppressionActive(Context context) {
        return isVehicleActive(prefs(context));
    }

    private static void setActive''',
'vehicle helper')

# Expose suppression state in Settings for diagnostics and explain the guard to the user.
BRIDGE = ROOT / 'app/src/main/java/com/yamo/snorelab/AutoDetectSettingsBridge.java'
replace_once(BRIDGE,
'''            out.put("notifications", Build.VERSION.SDK_INT < 33
                    || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED);''',
'''            out.put("notifications", Build.VERSION.SDK_INT < 33
                    || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED);
            out.put("vehicleSuppression", ActivityAutoDetectManager.vehicleSuppressionActive(activity));''',
'expose vehicle suppression')

replace_once(SETTINGS_JS,
'''      <div class="setting-info">활동이 일정 시간 계속될 때만 시작 후보로 판단합니다. 민감도는 감지 확인 시간을 조절합니다.</div>`;''',
'''      <div class="setting-info">활동이 일정 시간 계속될 때만 시작 후보로 판단합니다. 민감도는 감지 확인 시간을 조절합니다.</div>
      <div class="setting-info">자전거 자동감지는 휴대폰의 자전거 활동 신호를 사용하며, 자동차 탑승 신호가 함께 감지되면 자전거 자동 시작을 차단합니다.</div>`;''',
'auto detect vehicle guidance')

print('Applied v0.31.00 vehicle suppression and repeated inactivity end-check.')
