#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TARGET = ROOT / 'app/src/main/assets/yamone-v23'


def replace_once(path: Path, old: str, new: str, label: str):
    s = path.read_text(encoding='utf-8')
    if old not in s:
        raise SystemExit(f'v0.27.01 patch target not found: {label} ({path})')
    path.write_text(s.replace(old, new, 1), encoding='utf-8')


def add_before(path: Path, marker: str, text: str, label: str):
    s = path.read_text(encoding='utf-8')
    if text.strip() in s:
        return
    if marker not in s:
        raise SystemExit(f'v0.27.01 insertion target not found: {label} ({path})')
    path.write_text(s.replace(marker, text + marker, 1), encoding='utf-8')

# v27 UI files load after v26 so the alarm bridge overrides only alarm behavior.
for name in ['v02701-alarm.css', 'v02701-alarm.js', 'v02701-version.js']:
    src = ROOT / 'design-preview' / name
    if not src.is_file():
        raise SystemExit(f'missing v0.27.01 design file: {src}')
    (TARGET / name).write_bytes(src.read_bytes())

index = TARGET / 'index.html'
add_before(index, '</head>', '  <link rel="stylesheet" href="v02701-alarm.css">\n', 'alarm css')
add_before(index, '</body>', '  <script src="v02701-alarm.js"></script>\n  <script src="v02701-version.js"></script>\n', 'alarm js')

# Alarm model: add actual snooze limit and shake sensitivity, and migrate old tone names.
store = ROOT / 'app/src/main/java/com/yamo/snorelab/AlarmStore.java'
replace_once(store,
'''        public int snoozeMinutes = 5;\n\n        // Exactly one primary alert method is active: SOUND or TTS.''',
'''        public int snoozeMinutes = 5;\n        // Maximum user-triggered snoozes. 99 means unlimited.\n        public int snoozeCount = 3;\n\n        // Exactly one primary alert method is active: SOUND or TTS.''',
'snooze count field')
replace_once(store,
'''        public boolean shakeToStop = false;\n        public int shakeCount = 3;''',
'''        public boolean shakeToStop = false;\n        public int shakeCount = 3;\n        public String shakeStrength = "WEAK";''',
'shake strength field')
replace_once(store,
'''            o.put("snoozeMinutes", snoozeMinutes);\n            o.put("alertMode", alertMode);''',
'''            o.put("snoozeMinutes", snoozeMinutes);\n            o.put("snoozeCount", snoozeCount);\n            o.put("alertMode", alertMode);''',
'snooze count json')
replace_once(store,
'''            o.put("shakeToStop", shakeToStop);\n            o.put("shakeCount", shakeCount);''',
'''            o.put("shakeToStop", shakeToStop);\n            o.put("shakeCount", shakeCount);\n            o.put("shakeStrength", shakeStrength);''',
'shake strength json')
replace_once(store,
'''            a.snoozeMinutes = clamp(o.optInt("snoozeMinutes", 5), 1, 60);\n\n            a.alertMode = o.has("alertMode") ? o.optString("alertMode", "SOUND") : "SOUND";''',
'''            a.snoozeMinutes = clamp(o.optInt("snoozeMinutes", 5), 1, 60);\n            a.snoozeCount = normalizeSnoozeCount(o.optInt("snoozeCount", 3));\n\n            a.alertMode = o.has("alertMode") ? o.optString("alertMode", "SOUND") : "SOUND";''',
'snooze count parse')
replace_once(store,
'''            a.soundStyle = o.optString("soundStyle", "STRONG");\n            if (!"PULSE".equals(a.soundStyle) && !"SOFT".equals(a.soundStyle)) {\n                a.soundStyle = "STRONG";\n            }''',
'''            a.soundStyle = YamoneAlarmTone.normalizeStyle(o.optString("soundStyle", "BASIC"));''',
'five tone migration')
replace_once(store,
'''            a.shakeToStop = o.optBoolean("shakeToStop", false);\n            a.shakeCount = clamp(o.optInt("shakeCount", 3), 3, 10);\n            return a;''',
'''            a.shakeToStop = o.optBoolean("shakeToStop", false);\n            a.shakeCount = clamp(o.optInt("shakeCount", 3), 3, 10);\n            a.shakeStrength = "STRONG".equals(o.optString("shakeStrength", "WEAK")) ? "STRONG" : "WEAK";\n            return a;''',
'shake strength parse')
replace_once(store,
'''    private static int clampRetryCount(int value) {\n        if (value < 0) return -1;\n        return clamp(value, 0, 10);\n    }\n\n    private static int clamp(int value, int min, int max) {''',
'''    private static int clampRetryCount(int value) {\n        if (value < 0) return -1;\n        return clamp(value, 0, 10);\n    }\n\n    private static int normalizeSnoozeCount(int value) {\n        if (value < 0 || value >= 99) return 99;\n        if (value <= 1) return 1;\n        if (value <= 3) return 3;\n        return 5;\n    }\n\n    private static int clamp(int value, int min, int max) {''',
'snooze count helper')

# Scheduler: enforce the per-alarm snooze count and route alarm-clock details to the renewed UI.
scheduler = ROOT / 'app/src/main/java/com/yamo/snorelab/AlarmScheduler.java'
replace_once(scheduler,
'''        Intent show = new Intent(context, AlarmActivity.class).putExtra("alarm_id", itemId);''',
'''        Intent show = new Intent(context, YamoneDesignPreviewActivity.class)\n                .putExtra(YamoneDesignPreviewActivity.EXTRA_OPEN_ALARM, true);''',
'renewed alarm clock intent')
replace_once(scheduler,
'''    public static boolean scheduleSnooze(Context context, long id, int minutes) {\n        cancelSnooze(context, id);\n        markActive(context, id, true, retryAttempt(context, id));\n        long trigger = System.currentTimeMillis() + Math.max(1, minutes) * 60_000L;\n        return scheduleExact(context, snoozePendingIntent(context, id), trigger, id);\n    }''',
'''    public static boolean scheduleSnooze(Context context, long id, int minutes) {\n        AlarmStore.Item item = AlarmStore.find(context, id);\n        if (item == null || !canSnooze(context, item)) return false;\n        cancelSnooze(context, id);\n        long trigger = System.currentTimeMillis() + Math.max(1, minutes) * 60_000L;\n        boolean ok = scheduleExact(context, snoozePendingIntent(context, id), trigger, id);\n        if (ok) {\n            setSnoozeAttempt(context, id, snoozeAttempt(context, id) + 1);\n            markActive(context, id, true, retryAttempt(context, id));\n        }\n        return ok;\n    }''',
'enforce snooze count')
replace_once(scheduler,
'''    public static void cancelAll(Context context, long id) {\n        cancelRegular(context, id);\n        cancelRetry(context, id);\n        cancelSnooze(context, id);\n        markActive(context, id, false, 0);\n    }''',
'''    public static void cancelAll(Context context, long id) {\n        cancelRegular(context, id);\n        cancelRetry(context, id);\n        cancelSnooze(context, id);\n        markActive(context, id, false, 0);\n        resetSnoozeCount(context, id);\n    }''',
'cancel snooze runtime')
replace_once(scheduler,
'''    public static void dismiss(Context context, long id) {\n        cancelRetry(context, id);\n        cancelSnooze(context, id);\n        markActive(context, id, false, 0);\n        context.stopService(new Intent(context, AlarmRingService.class));\n    }\n\n    public static String nextDateText(AlarmStore.Item item) {''',
'''    public static void dismiss(Context context, long id) {\n        cancelRetry(context, id);\n        cancelSnooze(context, id);\n        markActive(context, id, false, 0);\n        resetSnoozeCount(context, id);\n        context.stopService(new Intent(context, AlarmRingService.class));\n    }\n\n    public static boolean canSnooze(Context context, AlarmStore.Item item) {\n        if (item == null) return false;\n        return item.snoozeCount >= 99 || snoozeAttempt(context, item.id) < Math.max(0, item.snoozeCount);\n    }\n\n    public static int snoozeAttempt(Context context, long id) {\n        return context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE).getInt("snooze_" + id, 0);\n    }\n\n    private static void setSnoozeAttempt(Context context, long id, int attempt) {\n        context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE).edit().putInt("snooze_" + id, Math.max(0, attempt)).apply();\n    }\n\n    public static void resetSnoozeCount(Context context, long id) {\n        setSnoozeAttempt(context, id, 0);\n    }\n\n    public static String nextDateText(AlarmStore.Item item) {''',
'snooze runtime helpers')

receiver = ROOT / 'app/src/main/java/com/yamo/snorelab/AlarmReceiver.java'
replace_once(receiver,
'''        if (ACTION_FIRE.equals(intent.getAction())) {\n            if (!item.enabled) return;\n            AlarmScheduler.markActive(context, id, true, 0);''',
'''        if (ACTION_FIRE.equals(intent.getAction())) {\n            if (!item.enabled) return;\n            AlarmScheduler.resetSnoozeCount(context, id);\n            AlarmScheduler.markActive(context, id, true, 0);''',
'reset snooze at regular fire')

# Actual alarm sound service uses the five new Yamone tones.
ring_service = ROOT / 'app/src/main/java/com/yamo/snorelab/AlarmRingService.java'
replace_once(ring_service,
'''            short[] pcm = AlarmActivity.synth(style, sampleRate, 4);''',
'''            short[] pcm = YamoneAlarmTone.synth(style, sampleRate, 4);''',
'five tones in ring service')

# Ringing activity respects the configured shake strength and snooze maximum.
ring_activity = ROOT / 'app/src/main/java/com/yamo/snorelab/AlarmRingActivity.java'
replace_once(ring_activity,
'''        Button snooze = button(item.snoozeMinutes + "분 후 다시", CARD2, PRIMARY2);\n        snooze.setOnClickListener(v -> snooze());\n        root.addView(snooze, match(dp(58)));''',
'''        if (AlarmScheduler.canSnooze(this, item)) {\n            Button snooze = button(item.snoozeMinutes + "분 후 다시", CARD2, PRIMARY2);\n            snooze.setOnClickListener(v -> snooze());\n            root.addView(snooze, match(dp(58)));\n        } else {\n            TextView snoozeDone = text("설정한 스누즈 횟수를 모두 사용했습니다.", 12, MUTED, true);\n            snoozeDone.setGravity(Gravity.CENTER);\n            snoozeDone.setPadding(0, dp(8), 0, dp(8));\n            root.addView(snoozeDone, matchWrap());\n        }''',
'ringing snooze max UI')
replace_once(ring_activity,
'''    private void snooze() {\n        AlarmScheduler.cancelRetry(this, alarmId);\n        if (AlarmScheduler.scheduleSnooze(this, alarmId, item.snoozeMinutes)) {''',
'''    private void snooze() {\n        if (!AlarmScheduler.canSnooze(this, item)) {\n            Toast.makeText(this, "설정한 스누즈 횟수를 모두 사용했습니다.", Toast.LENGTH_SHORT).show();\n            return;\n        }\n        AlarmScheduler.cancelRetry(this, alarmId);\n        if (AlarmScheduler.scheduleSnooze(this, alarmId, item.snoozeMinutes)) {''',
'ringing snooze check')
replace_once(ring_activity,
'''        if (magnitude < 18.5) return;''',
'''        double shakeThreshold = "STRONG".equals(item.shakeStrength) ? 23.0 : 18.0;\n        if (magnitude < shakeThreshold) return;''',
'shake strength threshold')

# AlarmBridge can re-arm saved alarms immediately after the user grants exact-alarm access.
bridge = ROOT / 'app/src/main/java/com/yamo/snorelab/AlarmBridge.java'
replace_once(bridge,
'''    @JavascriptInterface\n    public String previewTone(String ringtoneId) {''',
'''    @JavascriptInterface\n    public String rescheduleEnabled() {\n        if (!AlarmScheduler.canScheduleExact(activity)) return result(false, "permission_required").toString();\n        int scheduled = 0;\n        for (AlarmStore.Item item : AlarmStore.load(activity)) {\n            if (item.enabled && AlarmScheduler.scheduleNext(activity, item)) scheduled++;\n        }\n        JSONObject out = result(true, "scheduled");\n        try { out.put("count", scheduled); } catch (Exception ignored) { }\n        return out.toString();\n    }\n\n    @JavascriptInterface\n    public String previewTone(String ringtoneId) {''',
'alarm bridge reschedule')

# Register AlarmBridge in both WebView hosts after v26 has installed SleepBridge.
movement = ROOT / 'app/src/main/java/com/yamo/snorelab/YamoneMovementActivity.java'
replace_once(movement,
'''    private WebView webView;\n    private SleepBridge sleepBridge;\n    private Runnable unregisterSystemBack;''',
'''    private WebView webView;\n    private SleepBridge sleepBridge;\n    private AlarmBridge alarmBridge;\n    private Runnable unregisterSystemBack;''',
'movement alarm bridge field')
replace_once(movement,
'''        sleepBridge = new SleepBridge(this);\n        webView.addJavascriptInterface(sleepBridge, "YamoneSleep");''',
'''        sleepBridge = new SleepBridge(this);\n        webView.addJavascriptInterface(sleepBridge, "YamoneSleep");\n        alarmBridge = new AlarmBridge(this);\n        webView.addJavascriptInterface(alarmBridge, "YamoneAlarm");''',
'movement alarm bridge registration')
replace_once(movement,
'''        if (sleepBridge != null) { sleepBridge.release(); sleepBridge = null; }\n        if (webView != null) {\n            webView.loadUrl("about:blank");\n            webView.removeJavascriptInterface("YamoneSleep");''',
'''        if (alarmBridge != null) { alarmBridge.release(); alarmBridge = null; }\n        if (sleepBridge != null) { sleepBridge.release(); sleepBridge = null; }\n        if (webView != null) {\n            webView.loadUrl("about:blank");\n            webView.removeJavascriptInterface("YamoneAlarm");\n            webView.removeJavascriptInterface("YamoneSleep");''',
'movement alarm bridge release')

design = ROOT / 'app/src/main/java/com/yamo/snorelab/YamoneDesignPreviewActivity.java'
replace_once(design,
'''    public static final String EXTRA_OPEN_SLEEP = "yamone_open_sleep";\n    public static final String EXTRA_OPEN_SLEEP_STOP_CONFIRM = "yamone_open_sleep_stop_confirm";''',
'''    public static final String EXTRA_OPEN_SLEEP = "yamone_open_sleep";\n    public static final String EXTRA_OPEN_SLEEP_STOP_CONFIRM = "yamone_open_sleep_stop_confirm";\n    public static final String EXTRA_OPEN_ALARM = "yamone_open_alarm";''',
'design alarm extra')
replace_once(design,
'''    private WebView webView;\n    private SleepBridge sleepBridge;\n    private Runnable unregisterSystemBack;''',
'''    private WebView webView;\n    private SleepBridge sleepBridge;\n    private AlarmBridge alarmBridge;\n    private Runnable unregisterSystemBack;''',
'design alarm bridge field')
replace_once(design,
'''    private boolean pendingSleepOpen;\n    private boolean pendingSleepStopConfirm;''',
'''    private boolean pendingSleepOpen;\n    private boolean pendingSleepStopConfirm;\n    private boolean pendingAlarmOpen;''',
'design alarm pending field')
replace_once(design,
'''        sleepBridge = new SleepBridge(this);\n        webView.addJavascriptInterface(sleepBridge, "YamoneSleep");''',
'''        sleepBridge = new SleepBridge(this);\n        webView.addJavascriptInterface(sleepBridge, "YamoneSleep");\n        alarmBridge = new AlarmBridge(this);\n        webView.addJavascriptInterface(alarmBridge, "YamoneAlarm");''',
'design alarm bridge registration')
replace_once(design,
'''        if (openSleep || openSleepStop) {\n            pendingSleepOpen = true;\n            pendingSleepStopConfirm = pendingSleepStopConfirm || openSleepStop;\n            intent.removeExtra(EXTRA_OPEN_SLEEP);\n            intent.removeExtra(EXTRA_OPEN_SLEEP_STOP_CONFIRM);\n        }''',
'''        if (openSleep || openSleepStop) {\n            pendingSleepOpen = true;\n            pendingSleepStopConfirm = pendingSleepStopConfirm || openSleepStop;\n            intent.removeExtra(EXTRA_OPEN_SLEEP);\n            intent.removeExtra(EXTRA_OPEN_SLEEP_STOP_CONFIRM);\n        }\n        if (intent.getBooleanExtra(EXTRA_OPEN_ALARM, false)) {\n            pendingAlarmOpen = true;\n            intent.removeExtra(EXTRA_OPEN_ALARM);\n        }''',
'design capture alarm intent')
replace_once(design,
'''        if (!webPageReady || webView == null || isFinishing() || isDestroyed()) return;\n        if (pendingSleepOpen) {''',
'''        if (!webPageReady || webView == null || isFinishing() || isDestroyed()) return;\n        if (pendingAlarmOpen) {\n            pendingAlarmOpen = false;\n            webView.evaluateJavascript("Boolean(window.yamoneOpenAlarmList && window.yamoneOpenAlarmList())", ignored -> { });\n            return;\n        }\n        if (pendingSleepOpen) {''',
'design dispatch alarm intent')
replace_once(design,
'''        if (sleepBridge != null) { sleepBridge.release(); sleepBridge = null; }\n        if (webView != null) {\n            webView.loadUrl("about:blank");\n            webView.removeJavascriptInterface("YamoneSleep");''',
'''        if (alarmBridge != null) { alarmBridge.release(); alarmBridge = null; }\n        if (sleepBridge != null) { sleepBridge.release(); sleepBridge = null; }\n        if (webView != null) {\n            webView.loadUrl("about:blank");\n            webView.removeJavascriptInterface("YamoneAlarm");\n            webView.removeJavascriptInterface("YamoneSleep");''',
'design alarm bridge release')

print('Applied v0.27.01 real alarm integration.')
