#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent.parent
TARGET = ROOT / 'app/src/main/assets/yamone-v23'


def replace_once(path: Path, old: str, new: str, label: str):
    s = path.read_text(encoding='utf-8')
    if old not in s:
        raise SystemExit(f'v0.26.02 patch target not found: {label} ({path})')
    path.write_text(s.replace(old, new, 1), encoding='utf-8')


def add_before(path: Path, marker: str, text: str, label: str):
    s = path.read_text(encoding='utf-8')
    if text.strip() in s:
        return
    if marker not in s:
        raise SystemExit(f'v0.26.02 insertion target not found: {label} ({path})')
    path.write_text(s.replace(marker, text + marker, 1), encoding='utf-8')

# Copy v26 sleep UI patches into the imported WebView bundle.
for name in ['v02602-sleep.css', 'v02602-sleep.js', 'v02602-version.js']:
    src = ROOT / 'design-preview' / name
    if not src.is_file():
        raise SystemExit(f'missing v0.26.02 design file: {src}')
    (TARGET / name).write_bytes(src.read_bytes())

index = TARGET / 'index.html'
add_before(index, '</head>', '  <link rel="stylesheet" href="v02602-sleep.css">\n', 'sleep css')
add_before(index, '</body>', '  <script src="v02602-sleep.js"></script>\n  <script src="v02602-version.js"></script>\n', 'sleep js')

# Sleep recorder: persist live values, honor the real snore-detection setting,
# and route notification taps to the renewed UI instead of the legacy activity.
service = ROOT / 'app/src/main/java/com/yamo/snorelab/SleepRecorderService.java'
replace_once(service,
'''        int sensitivity = prefs.getInt("sensitivity", 65);\n        boolean fullRecording = prefs.getBoolean("developer_full_recording", false);\n        boolean saveClips = prefs.getBoolean("save_candidate_clips", true);''',
'''        int sensitivity = prefs.getInt("sensitivity", 65);\n        boolean fullRecording = prefs.getBoolean("developer_full_recording", false);\n        boolean saveClips = prefs.getBoolean("save_candidate_clips", true);\n        boolean detectSnore = prefs.getBoolean("snore_detection_enabled", true);''',
'sleep detection setting')
replace_once(service,
'''            prefs.edit().putBoolean(KEY_RECORDING, true).putString(KEY_SESSION_ID, sessionDir.getName())\n                    .putLong(KEY_START_MS, startMs).apply();''',
'''            prefs.edit().putBoolean(KEY_RECORDING, true).putString(KEY_SESSION_ID, sessionDir.getName())\n                    .putLong(KEY_START_MS, startMs)\n                    .remove("sleep_current_dbfs")\n                    .remove("sleep_current_score")\n                    .putBoolean("sleep_current_candidate", false)\n                    .apply();''',
'sleep live reset')
replace_once(service,
'''            SnoreDetector detector = new SnoreDetector(sensitivity);\n            accumulator = new CandidateEventAccumulator(sessionDir, SAMPLE_RATE, saveClips, 0);''',
'''            SnoreDetector detector = new SnoreDetector(sensitivity);\n            accumulator = detectSnore ? new CandidateEventAccumulator(sessionDir, SAMPLE_RATE, saveClips, 0) : null;''',
'conditional candidate accumulator')
replace_once(service,
'''                        SnoreDetector.Result result = detector.analyze(oneSecond, SAMPLE_RATE);\n                        long offsetMs = processedWindowSamples * 1000L / SAMPLE_RATE;\n                        accumulator.onWindow(oneSecond, offsetMs, result);\n                        frameWriter.write(String.format(Locale.US, "%d,%.4f,%.6f,%.6f,%.6f,%.3f,%.3f,%d\\n",\n                                offsetMs, result.dbfs, result.zeroCrossRate, result.lowBandRatio, result.periodicity,\n                                result.score, result.threshold, result.candidate ? 1 : 0));''',
'''                        SnoreDetector.Result result = detector.analyze(oneSecond, SAMPLE_RATE);\n                        long offsetMs = processedWindowSamples * 1000L / SAMPLE_RATE;\n                        boolean candidate = detectSnore && result.candidate;\n                        prefs.edit()\n                                .putFloat("sleep_current_dbfs", (float) result.dbfs)\n                                .putFloat("sleep_current_score", (float) result.score)\n                                .putBoolean("sleep_current_candidate", candidate)\n                                .apply();\n                        if (accumulator != null) accumulator.onWindow(oneSecond, offsetMs, result);\n                        frameWriter.write(String.format(Locale.US, "%d,%.4f,%.6f,%.6f,%.6f,%.3f,%.3f,%d\\n",\n                                offsetMs, result.dbfs, result.zeroCrossRate, result.lowBandRatio, result.periodicity,\n                                result.score, result.threshold, candidate ? 1 : 0));''',
'live detector values')
replace_once(service,
'''            prefs.edit().putBoolean(KEY_RECORDING, false).remove(KEY_SESSION_ID).remove(KEY_START_MS).apply();''',
'''            prefs.edit().putBoolean(KEY_RECORDING, false).remove(KEY_SESSION_ID).remove(KEY_START_MS)\n                    .remove("sleep_current_dbfs").remove("sleep_current_score")\n                    .putBoolean("sleep_current_candidate", false).apply();''',
'sleep live cleanup')
replace_once(service,
'''    private Notification buildNotification() {\n        Intent openIntent = new Intent(this, MainActivity.class);\n        PendingIntent open = PendingIntent.getActivity(this, 1, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);\n        Intent stopIntent = new Intent(this, SleepRecorderService.class).setAction(ACTION_STOP);\n        PendingIntent stop = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);\n        return new Notification.Builder(this, CHANNEL_ID)\n                .setSmallIcon(R.drawable.ic_notification).setContentTitle("수면 측정 중")\n                .setContentText("화면을 꺼도 코골이 검증 측정을 계속합니다.").setContentIntent(open)\n                .setOngoing(true).setCategory(Notification.CATEGORY_SERVICE)\n                .addAction(new Notification.Action.Builder(null, "측정 종료", stop).build()).build();\n    }''',
'''    private Notification buildNotification() {\n        Intent openIntent = sleepNotificationIntent(false);\n        PendingIntent open = PendingIntent.getActivity(this, 3201, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);\n        Intent stopIntent = sleepNotificationIntent(true);\n        PendingIntent stop = PendingIntent.getActivity(this, 3202, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);\n        return new Notification.Builder(this, CHANNEL_ID)\n                .setSmallIcon(R.drawable.ic_notification).setContentTitle("수면 측정 중")\n                .setContentText("화면을 꺼도 수면 소리 측정을 계속합니다.").setContentIntent(open)\n                .setOngoing(true).setCategory(Notification.CATEGORY_SERVICE)\n                .addAction(new Notification.Action.Builder(null, "측정 종료", stop).build()).build();\n    }\n\n    private Intent sleepNotificationIntent(boolean openStopConfirm) {\n        Intent intent = new Intent(this, YamoneDesignPreviewActivity.class);\n        intent.putExtra(YamoneDesignPreviewActivity.EXTRA_OPEN_SLEEP, true);\n        intent.putExtra(YamoneDesignPreviewActivity.EXTRA_OPEN_SLEEP_STOP_CONFIRM, openStopConfirm);\n        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);\n        return intent;\n    }''',
'sleep notification routing')

# Register one SleepBridge per WebView host so local audio playback is released
# together with the host activity.
movement = ROOT / 'app/src/main/java/com/yamo/snorelab/YamoneMovementActivity.java'
replace_once(movement,
'''    private WebView webView;\n    private Runnable unregisterSystemBack;''',
'''    private WebView webView;\n    private SleepBridge sleepBridge;\n    private Runnable unregisterSystemBack;''',
'movement sleep bridge field')
replace_once(movement,
'''        webView.addJavascriptInterface(new AutoDetectSettingsBridge(this), "YamoneAutoDetect");''',
'''        webView.addJavascriptInterface(new AutoDetectSettingsBridge(this), "YamoneAutoDetect");\n        sleepBridge = new SleepBridge(this);\n        webView.addJavascriptInterface(sleepBridge, "YamoneSleep");''',
'movement sleep bridge registration')
replace_once(movement,
'''        if (webView != null) {\n            webView.loadUrl("about:blank");\n            webView.removeJavascriptInterface("YamoneAutoDetect");''',
'''        if (sleepBridge != null) { sleepBridge.release(); sleepBridge = null; }\n        if (webView != null) {\n            webView.loadUrl("about:blank");\n            webView.removeJavascriptInterface("YamoneSleep");\n            webView.removeJavascriptInterface("YamoneAutoDetect");''',
'movement sleep bridge release')

design = ROOT / 'app/src/main/java/com/yamo/snorelab/YamoneDesignPreviewActivity.java'
replace_once(design,
'''    public static final String EXTRA_OPEN_MOVEMENT = "yamone_open_movement";\n    public static final String EXTRA_OPEN_STOP_CONFIRM = "yamone_open_stop_confirm";''',
'''    public static final String EXTRA_OPEN_MOVEMENT = "yamone_open_movement";\n    public static final String EXTRA_OPEN_STOP_CONFIRM = "yamone_open_stop_confirm";\n    public static final String EXTRA_OPEN_SLEEP = "yamone_open_sleep";\n    public static final String EXTRA_OPEN_SLEEP_STOP_CONFIRM = "yamone_open_sleep_stop_confirm";''',
'design sleep extras')
replace_once(design,
'''    private WebView webView;\n    private Runnable unregisterSystemBack;''',
'''    private WebView webView;\n    private SleepBridge sleepBridge;\n    private Runnable unregisterSystemBack;''',
'design sleep bridge field')
replace_once(design,
'''    private boolean pendingMovementOpen;\n    private boolean pendingStopConfirm;''',
'''    private boolean pendingMovementOpen;\n    private boolean pendingStopConfirm;\n    private boolean pendingSleepOpen;\n    private boolean pendingSleepStopConfirm;''',
'design sleep pending fields')
replace_once(design,
'''        webView.addJavascriptInterface(new AutoDetectSettingsBridge(this), "YamoneAutoDetect");''',
'''        webView.addJavascriptInterface(new AutoDetectSettingsBridge(this), "YamoneAutoDetect");\n        sleepBridge = new SleepBridge(this);\n        webView.addJavascriptInterface(sleepBridge, "YamoneSleep");''',
'design sleep bridge registration')
replace_once(design,
'''        if (openMovement || openStop) {\n            pendingMovementOpen = true;\n            pendingStopConfirm = pendingStopConfirm || openStop;\n            intent.removeExtra(EXTRA_OPEN_MOVEMENT);\n            intent.removeExtra(EXTRA_OPEN_STOP_CONFIRM);\n        }''',
'''        if (openMovement || openStop) {\n            pendingMovementOpen = true;\n            pendingStopConfirm = pendingStopConfirm || openStop;\n            intent.removeExtra(EXTRA_OPEN_MOVEMENT);\n            intent.removeExtra(EXTRA_OPEN_STOP_CONFIRM);\n        }\n        boolean openSleep = intent.getBooleanExtra(EXTRA_OPEN_SLEEP, false);\n        boolean openSleepStop = intent.getBooleanExtra(EXTRA_OPEN_SLEEP_STOP_CONFIRM, false);\n        if (openSleep || openSleepStop) {\n            pendingSleepOpen = true;\n            pendingSleepStopConfirm = pendingSleepStopConfirm || openSleepStop;\n            intent.removeExtra(EXTRA_OPEN_SLEEP);\n            intent.removeExtra(EXTRA_OPEN_SLEEP_STOP_CONFIRM);\n        }''',
'design capture sleep intent')
replace_once(design,
'''    private void dispatchPendingNotificationIntent() {\n        if (!webPageReady || webView == null || !pendingMovementOpen || isFinishing() || isDestroyed()) return;\n        final boolean openStop = pendingStopConfirm;\n        pendingMovementOpen = false;\n        pendingStopConfirm = false;\n        String js = "Boolean(window.yamoneOpenActiveMovement && window.yamoneOpenActiveMovement("\n                + (openStop ? "true" : "false") + "))";\n        webView.evaluateJavascript(js, ignored -> { });\n    }''',
'''    private void dispatchPendingNotificationIntent() {\n        if (!webPageReady || webView == null || isFinishing() || isDestroyed()) return;\n        if (pendingSleepOpen) {\n            final boolean openStop = pendingSleepStopConfirm;\n            pendingSleepOpen = false;\n            pendingSleepStopConfirm = false;\n            String js = "Boolean(window.yamoneOpenActiveSleep && window.yamoneOpenActiveSleep("\n                    + (openStop ? "true" : "false") + "))";\n            webView.evaluateJavascript(js, ignored -> { });\n            return;\n        }\n        if (!pendingMovementOpen) return;\n        final boolean openStop = pendingStopConfirm;\n        pendingMovementOpen = false;\n        pendingStopConfirm = false;\n        String js = "Boolean(window.yamoneOpenActiveMovement && window.yamoneOpenActiveMovement("\n                + (openStop ? "true" : "false") + "))";\n        webView.evaluateJavascript(js, ignored -> { });\n    }''',
'design dispatch sleep intent')
replace_once(design,
'''        if (webView != null) {\n            webView.loadUrl("about:blank");\n            webView.removeJavascriptInterface("YamoneAutoDetect");''',
'''        if (sleepBridge != null) { sleepBridge.release(); sleepBridge = null; }\n        if (webView != null) {\n            webView.loadUrl("about:blank");\n            webView.removeJavascriptInterface("YamoneSleep");\n            webView.removeJavascriptInterface("YamoneAutoDetect");''',
'design sleep bridge release')

print('Applied v0.26.02 real sleep integration.')
