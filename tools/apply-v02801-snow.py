#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TARGET = ROOT / 'app/src/main/assets/yamone-v23'


def replace_once(path: Path, old: str, new: str, label: str):
    s = path.read_text(encoding='utf-8')
    if old not in s:
        raise SystemExit(f'v0.28.01 patch target not found: {label} ({path})')
    path.write_text(s.replace(old, new, 1), encoding='utf-8')


def add_before(path: Path, marker: str, text: str, label: str):
    s = path.read_text(encoding='utf-8')
    if text.strip() in s:
        return
    if marker not in s:
        raise SystemExit(f'v0.28.01 insertion target not found: {label} ({path})')
    path.write_text(s.replace(marker, text + marker, 1), encoding='utf-8')

# UI patch loads last so it can gate the existing Snow mock behind native developer mode.
for name in ['v02801-snow.css', 'v02801-snow.js', 'v02801-version.js']:
    src = ROOT / 'design-preview' / name
    if not src.is_file():
        raise SystemExit(f'missing v0.28.01 design file: {src}')
    (TARGET / name).write_bytes(src.read_bytes())

index = TARGET / 'index.html'
add_before(index, '</head>', '  <link rel="stylesheet" href="v02801-snow.css">\n', 'snow css')
add_before(index, '</body>', '  <script src="v02801-snow.js"></script>\n  <script src="v02801-version.js"></script>\n', 'snow js')

# Store additional real runtime metadata needed by pause/resume and developer simulation.
store = ROOT / 'app/src/main/java/com/yamo/snorelab/SkiLiftStore.java'
replace_once(store,
'''                "descentDistanceM", "descentVerticalM", "liftTimeMs", "waitTimeMs",\n                "rejectedGpsPoints", "detectorVersion"''',
'''                "descentDistanceM", "descentVerticalM", "liftTimeMs", "waitTimeMs",\n                "rejectedGpsPoints", "detectorVersion", "activeDurationMs", "pausedMs",\n                "developerSimulated", "lastFixMs", "mergedSessionCount"''',
'ski extra metrics')

# Ski recorder: actual pause/resume, last GPS fix, local package resort detection,
# developer simulation, day merging and renewed notification routing.
service = ROOT / 'app/src/main/java/com/yamo/snorelab/SkiRecorderService.java'
replace_once(service,
'''    public static final String ACTION_START = "com.yamo.snorelab.SKI_START";\n    public static final String ACTION_STOP = "com.yamo.snorelab.SKI_STOP";\n    public static final String PREFS = "yamone_ski_runtime_v1";''',
'''    public static final String ACTION_START = "com.yamo.snorelab.SKI_START";\n    public static final String ACTION_STOP = "com.yamo.snorelab.SKI_STOP";\n    public static final String ACTION_PAUSE = "com.yamo.snorelab.SKI_PAUSE";\n    public static final String ACTION_RESUME = "com.yamo.snorelab.SKI_RESUME";\n    public static final String ACTION_DEBUG_START = "com.yamo.snorelab.SKI_DEBUG_START";\n    public static final String PREFS = "yamone_ski_runtime_v1";''',
'ski actions')
replace_once(service,
'''    public static final String KEY_WAIT_TIME_MS = "wait_time_ms";''',
'''    public static final String KEY_WAIT_TIME_MS = "wait_time_ms";\n    public static final String KEY_PAUSED = "paused";\n    public static final String KEY_PAUSE_STARTED_MS = "pause_started_ms";\n    public static final String KEY_PAUSED_TOTAL_MS = "paused_total_ms";\n    public static final String KEY_LAST_FIX_MS = "last_fix_ms";\n    public static final String KEY_DEBUG_SIMULATION = "developer_simulation";''',
'ski runtime keys')
replace_once(service,
'''    private boolean recording;\n    private String sport = "ski";\n    private long startMs;''',
'''    private boolean recording;\n    private boolean paused;\n    private long pauseStartedMs;\n    private long pausedTotalMs;\n    private long lastFixMs;\n    private boolean debugSimulation;\n    private int debugStep;\n    private long debugBaseEpochMs;\n    private String sport = "ski";\n    private long startMs;''',
'ski pause/debug fields')
replace_once(service,
'''    private final Runnable ticker = new Runnable() {\n        @Override public void run() {\n            if (!recording) return;\n            if (System.currentTimeMillis() - lastProviderRefreshAt >= 30_000L) refreshLocationProviderRegistrations();\n            persistRuntime();\n            persistSessionMetrics();\n            updateNotification();\n            handler.postDelayed(this, 3000L);\n        }\n    };''',
'''    private final Runnable ticker = new Runnable() {\n        @Override public void run() {\n            if (!recording) return;\n            if (!paused && !debugSimulation && System.currentTimeMillis() - lastProviderRefreshAt >= 30_000L) {\n                refreshLocationProviderRegistrations();\n            }\n            persistRuntime();\n            persistSessionMetrics();\n            updateNotification();\n            handler.postDelayed(this, 3000L);\n        }\n    };\n\n    private final Runnable debugTicker = new Runnable() {\n        @Override public void run() {\n            if (!recording || !debugSimulation) return;\n            if (!paused) emitDeveloperSimulationLocation();\n            handler.postDelayed(this, 250L);\n        }\n    };''',
'ski tickers')
replace_once(service,
'''        } else if (ACTION_START.equals(action)) begin(intent);\n        else if (ACTION_STOP.equals(action)) finishRecording();''',
'''        } else if (ACTION_START.equals(action)) begin(intent);\n        else if (ACTION_DEBUG_START.equals(action)) beginDeveloperSimulation(intent);\n        else if (ACTION_PAUSE.equals(action)) pauseRecording();\n        else if (ACTION_RESUME.equals(action)) resumeRecording();\n        else if (ACTION_STOP.equals(action)) finishRecording();''',
'ski command routing')
replace_once(service,
'''        sport = "snowboard".equals(intent.getStringExtra("sport")) ? "snowboard" : "ski";\n        startMs = System.currentTimeMillis();\n        sessionDir = SkiLiftStore.createSession(this, startMs, sport);''',
'''        sport = "snowboard".equals(intent.getStringExtra("sport")) ? "snowboard" : "ski";\n        startMs = System.currentTimeMillis();\n        paused = false;\n        pauseStartedMs = 0L;\n        pausedTotalMs = 0L;\n        lastFixMs = 0L;\n        debugSimulation = false;\n        debugStep = 0;\n        sessionDir = SkiLiftStore.createSession(this, startMs, sport);''',
'ski begin pause reset')
replace_once(service,
'''    private void onLocationChanged(Location loc) {\n        if (!recording || loc == null) return;''',
'''    private void onLocationChanged(Location loc) {\n        if (!recording || paused || loc == null) return;''',
'ski ignore paused fixes')
replace_once(service,
'''        long now = loc.getTime() > 0 ? loc.getTime() : System.currentTimeMillis();\n        accuracyM = loc.hasAccuracy() ? loc.getAccuracy() : Float.NaN;''',
'''        long now = loc.getTime() > 0 ? loc.getTime() : System.currentTimeMillis();\n        lastFixMs = System.currentTimeMillis();\n        accuracyM = loc.hasAccuracy() ? loc.getAccuracy() : Float.NaN;''',
'ski last fix')
replace_once(service,
'''    private void maybeDetectResort(Location loc) {\n        if (!SkiLiftStore.isRealtimeExchangeEnabled(this)) return;\n        if (!SkiResortStore.currentKey(this).isEmpty()) return;''',
'''    private void maybeDetectResort(Location loc) {\n        if (!SkiResortStore.currentKey(this).isEmpty()) return;\n        JSONObject local = SnowOfflineMapStore.detectInstalledResort(this, loc.getLatitude(), loc.getLongitude());\n        if (local.length() > 0) {\n            SkiResortStore.setCurrent(this, local.optString("resortKey", ""), local.optString("resortName", ""));\n            return;\n        }\n        if (!SkiLiftStore.isRealtimeExchangeEnabled(this)) return;''',
'local offline resort detection')
replace_once(service,
'''        recording = true;\n        state = STATE_CHECKING;\n        lastLoc = null;''',
'''        recording = true;\n        paused = runtime.getBoolean(KEY_PAUSED, false);\n        pauseStartedMs = runtime.getLong(KEY_PAUSE_STARTED_MS, 0L);\n        pausedTotalMs = Math.max(0L, runtime.getLong(KEY_PAUSED_TOTAL_MS, 0L));\n        lastFixMs = runtime.getLong(KEY_LAST_FIX_MS, 0L);\n        debugSimulation = false;\n        state = paused ? STATE_STOPPED : STATE_CHECKING;\n        lastLoc = null;''',
'ski recovery pause state')
replace_once(service,
'''        startLocation();\n        persistRuntime();\n        persistSessionMetrics();''',
'''        if (!paused) startLocation();\n        persistRuntime();\n        persistSessionMetrics();''',
'ski recovery location resume')
replace_once(service,
'''            waitTimeMs = runtime.getLong(KEY_WAIT_TIME_MS, 0L);\n            state = STATE_CHECKING;''',
'''            waitTimeMs = runtime.getLong(KEY_WAIT_TIME_MS, 0L);\n            paused = runtime.getBoolean(KEY_PAUSED, false);\n            pauseStartedMs = runtime.getLong(KEY_PAUSE_STARTED_MS, 0L);\n            pausedTotalMs = Math.max(0L, runtime.getLong(KEY_PAUSED_TOTAL_MS, 0L));\n            debugSimulation = runtime.getBoolean(KEY_DEBUG_SIMULATION, false);\n            state = paused ? STATE_STOPPED : STATE_CHECKING;''',
'ski stop restore pause')
replace_once(service,
'''        long now = System.currentTimeMillis();\n        if (STATE_LIFT.equals(state) && activeLiftLastLoc != null) {''',
'''        long now = System.currentTimeMillis();\n        if (paused && pauseStartedMs > 0L) {\n            pausedTotalMs += Math.max(0L, now - pauseStartedMs);\n            pauseStartedMs = 0L;\n            paused = false;\n        }\n        if (STATE_LIFT.equals(state) && activeLiftLastLoc != null) {''',
'ski finalize pause on stop')
replace_once(service,
'''            SkiLiftStore.completeSession(sessionDir, now,\n                    SkiResortStore.currentKey(this), SkiResortStore.currentName(this));''',
'''            SkiLiftStore.completeSession(sessionDir, now,\n                    SkiResortStore.currentKey(this), SkiResortStore.currentName(this));\n            SnowDayMerger.mergePriorSameDayIntoCurrent(this, sessionDir);''',
'ski same day merge')
replace_once(service,
'''        handler.removeCallbacks(ticker);\n        clearRuntime();''',
'''        handler.removeCallbacks(ticker);\n        handler.removeCallbacks(debugTicker);\n        clearRuntime();''',
'ski stop debug ticker')
replace_once(service,
'''                .putLong(KEY_LIFT_TIME_MS, liftTimeMs)\n                .putLong(KEY_WAIT_TIME_MS, waitTimeMs);''',
'''                .putLong(KEY_LIFT_TIME_MS, liftTimeMs)\n                .putLong(KEY_WAIT_TIME_MS, waitTimeMs)\n                .putBoolean(KEY_PAUSED, paused)\n                .putLong(KEY_PAUSE_STARTED_MS, pauseStartedMs)\n                .putLong(KEY_PAUSED_TOTAL_MS, pausedTotalMs)\n                .putLong(KEY_LAST_FIX_MS, lastFixMs)\n                .putBoolean(KEY_DEBUG_SIMULATION, debugSimulation);''',
'ski persist pause/debug')
replace_once(service,
'''            m.put("durationMs", Math.max(0, System.currentTimeMillis() - startMs));\n            m.put("currentState", state);''',
'''            long now = System.currentTimeMillis();\n            long pausedNow = pausedTotalMs + (paused && pauseStartedMs > 0L ? Math.max(0L, now - pauseStartedMs) : 0L);\n            m.put("durationMs", Math.max(0, now - startMs));\n            m.put("activeDurationMs", Math.max(0, now - startMs - pausedNow));\n            m.put("pausedMs", pausedNow);\n            m.put("developerSimulated", debugSimulation);\n            m.put("lastFixMs", lastFixMs);\n            m.put("currentState", state);''',
'ski session active metrics')
replace_once(service,
'''    private Notification buildNotification(String text) {\n        Intent open = new Intent(this, SkiActivity.class);\n        PendingIntent pi = PendingIntent.getActivity(this, 5401, open,\n                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);''',
'''    private Notification buildNotification(String text) {\n        Intent open = new Intent(this, YamoneDesignPreviewActivity.class)\n                .putExtra(YamoneDesignPreviewActivity.EXTRA_OPEN_SNOW, true)\n                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);\n        PendingIntent pi = PendingIntent.getActivity(this, 5401, open,\n                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);''',
'ski renewed notification intent')
replace_once(service,
'''        String label = STATE_DESCENT.equals(state) ? "활주 중"\n                : STATE_LIFT.equals(state) ? "리프트 이동"''',
'''        String label = paused ? "일시정지" : STATE_DESCENT.equals(state) ? "활주 중"\n                : STATE_LIFT.equals(state) ? "리프트 이동"''',
'ski paused notification')
replace_once(service,
'''    private void stopLocation() {''',
'''    private void pauseRecording() {\n        if (!recording || paused) return;\n        paused = true;\n        pauseStartedMs = System.currentTimeMillis();\n        currentSpeedKmh = 0f;\n        state = STATE_STOPPED;\n        liftCandidate = null;\n        descentCandidate = null;\n        if (!debugSimulation) stopLocation();\n        persistRuntime();\n        persistSessionMetrics();\n        updateNotification();\n    }\n\n    private void resumeRecording() {\n        if (!recording || !paused) return;\n        long now = System.currentTimeMillis();\n        if (pauseStartedMs > 0L) pausedTotalMs += Math.max(0L, now - pauseStartedMs);\n        pauseStartedMs = 0L;\n        paused = false;\n        state = STATE_CHECKING;\n        lastLoc = null;\n        lastTime = 0L;\n        currentSpeedKmh = 0f;\n        if (!debugSimulation) startLocation();\n        persistRuntime();\n        persistSessionMetrics();\n        updateNotification();\n    }\n\n    private void beginDeveloperSimulation(Intent intent) {\n        if (!isDebuggable() || !SnowBridge.isDeveloperAvailable(this)) { stopSelf(); return; }\n        if (recording || runtime.getBoolean(KEY_RECORDING, false)) return;\n        sport = "snowboard".equals(intent.getStringExtra("sport")) ? "snowboard" : "ski";\n        startMs = System.currentTimeMillis();\n        paused = false; pauseStartedMs = 0L; pausedTotalMs = 0L; lastFixMs = 0L;\n        debugSimulation = true; debugStep = 0; debugBaseEpochMs = System.currentTimeMillis();\n        SnowOfflineMapStore.ensureDeveloperTestMap(this);\n        SkiResortStore.setCurrent(this, SnowOfflineMapStore.DEV_RESORT_KEY, SnowOfflineMapStore.DEV_RESORT_NAME);\n        sessionDir = SkiLiftStore.createSession(this, startMs, sport);\n        if (sessionDir == null || !sessionDir.exists()) { clearRuntime(); stopSelf(); return; }\n        recording = true; state = STATE_CHECKING;\n        Notification n = buildNotification("개발용 Snow 시뮬레이션");\n        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFY_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);\n        else startForeground(NOTIFY_ID, n);\n        resetDetector();\n        debugSimulation = true;\n        persistRuntime(); persistSessionMetrics();\n        handler.removeCallbacks(ticker); handler.post(ticker);\n        handler.removeCallbacks(debugTicker); handler.post(debugTicker);\n    }\n\n    private void emitDeveloperSimulationLocation() {\n        int cycle = (debugStep / 90) % 2;\n        int phase = debugStep % 90;\n        boolean b = cycle == 1;\n        double bottomLat = SnowOfflineMapStore.DEV_CENTER_LAT - 0.0035;\n        double bottomLon = SnowOfflineMapStore.DEV_CENTER_LON + (b ? 0.0012 : -0.0010);\n        double topLat = SnowOfflineMapStore.DEV_CENTER_LAT + 0.0041;\n        double topLon = SnowOfflineMapStore.DEV_CENTER_LON + (b ? 0.0027 : -0.0030);\n        double lat = bottomLat, lon = bottomLon, alt = 220.0;\n        float speed = 0f;\n        if (phase >= 12 && phase < 45) {\n            double f = (phase - 12) / 32.0;\n            lat = bottomLat + (topLat - bottomLat) * f;\n            lon = bottomLon + (topLon - bottomLon) * f;\n            alt = 220 + 145 * f;\n            speed = 6.5f;\n        } else if (phase >= 45 && phase < 50) {\n            lat = topLat; lon = topLon; alt = 365; speed = 0.2f;\n        } else if (phase >= 50 && phase < 82) {\n            double f = (phase - 50) / 31.0;\n            lat = topLat + (bottomLat - topLat) * f;\n            lon = topLon + (bottomLon - topLon) * f;\n            alt = 365 - 145 * f;\n            speed = 11.5f;\n        }\n        Location loc = new Location("yamone-snow-dev");\n        loc.setLatitude(lat); loc.setLongitude(lon); loc.setAltitude(alt);\n        loc.setAccuracy(4f); loc.setSpeed(speed);\n        loc.setTime(debugBaseEpochMs + debugStep * 3000L);\n        onLocationChanged(loc);\n        debugStep++;\n    }\n\n    private boolean isDebuggable() {\n        return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;\n    }\n\n    private void stopLocation() {''',
'ski pause/resume/debug methods')
replace_once(service,
'''    @Override public void onDestroy() {\n        stopLocation();\n        handler.removeCallbacks(ticker);''',
'''    @Override public void onDestroy() {\n        stopLocation();\n        handler.removeCallbacks(ticker);\n        handler.removeCallbacks(debugTicker);''',
'ski destroy debug ticker')

# Developer simulation still uses a location-type foreground service, so request
# normal location permission first on current Android releases.
bridge = ROOT / 'app/src/main/java/com/yamo/snorelab/SnowBridge.java'
replace_once(bridge,
'''        if (!isDeveloperAvailable(activity)) return result(false, "locked").toString();\n        if (runtime.getBoolean(SkiRecorderService.KEY_RECORDING, false)) return result(false, "already_recording").toString();\n        SnowOfflineMapStore.ensureDeveloperTestMap(activity);''',
'''        if (!isDeveloperAvailable(activity)) return result(false, "locked").toString();\n        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) return result(false, "location_permission_required").toString();\n        if (runtime.getBoolean(SkiRecorderService.KEY_RECORDING, false)) return result(false, "already_recording").toString();\n        SnowOfflineMapStore.ensureDeveloperTestMap(activity);''',
'dev simulation location permission')

# Register SnowBridge only in debuggable builds. Release gets no Snow JS bridge.
movement = ROOT / 'app/src/main/java/com/yamo/snorelab/YamoneMovementActivity.java'
replace_once(movement,
'''    private SleepBridge sleepBridge;\n    private AlarmBridge alarmBridge;\n    private Runnable unregisterSystemBack;''',
'''    private SleepBridge sleepBridge;\n    private AlarmBridge alarmBridge;\n    private SnowBridge snowBridge;\n    private Runnable unregisterSystemBack;''',
'movement snow bridge field')
replace_once(movement,
'''        alarmBridge = new AlarmBridge(this);\n        webView.addJavascriptInterface(alarmBridge, "YamoneAlarm");''',
'''        alarmBridge = new AlarmBridge(this);\n        webView.addJavascriptInterface(alarmBridge, "YamoneAlarm");\n        if (SnowBridge.isDebuggable(this)) {\n            snowBridge = new SnowBridge(this);\n            webView.addJavascriptInterface(snowBridge, "YamoneSnow");\n        }''',
'movement snow bridge registration')
replace_once(movement,
'''        if (alarmBridge != null) { alarmBridge.release(); alarmBridge = null; }\n        if (sleepBridge != null) { sleepBridge.release(); sleepBridge = null; }''',
'''        if (snowBridge != null) { snowBridge.release(); snowBridge = null; }\n        if (alarmBridge != null) { alarmBridge.release(); alarmBridge = null; }\n        if (sleepBridge != null) { sleepBridge.release(); sleepBridge = null; }''',
'movement snow release')
replace_once(movement,
'''            webView.loadUrl("about:blank");\n            webView.removeJavascriptInterface("YamoneAlarm");''',
'''            webView.loadUrl("about:blank");\n            webView.removeJavascriptInterface("YamoneSnow");\n            webView.removeJavascriptInterface("YamoneAlarm");''',
'movement remove snow interface')

design = ROOT / 'app/src/main/java/com/yamo/snorelab/YamoneDesignPreviewActivity.java'
replace_once(design,
'''    public static final String EXTRA_OPEN_ALARM = "yamone_open_alarm";''',
'''    public static final String EXTRA_OPEN_ALARM = "yamone_open_alarm";\n    public static final String EXTRA_OPEN_SNOW = "yamone_open_snow";''',
'design snow extra')
replace_once(design,
'''    private SleepBridge sleepBridge;\n    private AlarmBridge alarmBridge;\n    private Runnable unregisterSystemBack;''',
'''    private SleepBridge sleepBridge;\n    private AlarmBridge alarmBridge;\n    private SnowBridge snowBridge;\n    private Runnable unregisterSystemBack;''',
'design snow bridge field')
replace_once(design,
'''    private boolean pendingAlarmOpen;''',
'''    private boolean pendingAlarmOpen;\n    private boolean pendingSnowOpen;''',
'design pending snow')
replace_once(design,
'''        alarmBridge = new AlarmBridge(this);\n        webView.addJavascriptInterface(alarmBridge, "YamoneAlarm");''',
'''        alarmBridge = new AlarmBridge(this);\n        webView.addJavascriptInterface(alarmBridge, "YamoneAlarm");\n        if (SnowBridge.isDebuggable(this)) {\n            snowBridge = new SnowBridge(this);\n            webView.addJavascriptInterface(snowBridge, "YamoneSnow");\n        }''',
'design snow bridge registration')
replace_once(design,
'''        if (intent.getBooleanExtra(EXTRA_OPEN_ALARM, false)) {\n            pendingAlarmOpen = true;\n            intent.removeExtra(EXTRA_OPEN_ALARM);\n        }''',
'''        if (intent.getBooleanExtra(EXTRA_OPEN_ALARM, false)) {\n            pendingAlarmOpen = true;\n            intent.removeExtra(EXTRA_OPEN_ALARM);\n        }\n        if (intent.getBooleanExtra(EXTRA_OPEN_SNOW, false)) {\n            pendingSnowOpen = true;\n            intent.removeExtra(EXTRA_OPEN_SNOW);\n        }''',
'design capture snow intent')
replace_once(design,
'''        if (!webPageReady || webView == null || isFinishing() || isDestroyed()) return;\n        if (pendingAlarmOpen) {''',
'''        if (!webPageReady || webView == null || isFinishing() || isDestroyed()) return;\n        if (pendingSnowOpen) {\n            pendingSnowOpen = false;\n            webView.evaluateJavascript("Boolean(window.yamoneOpenSnow && window.yamoneOpenSnow())", ignored -> { });\n            return;\n        }\n        if (pendingAlarmOpen) {''',
'design dispatch snow intent')
replace_once(design,
'''        if (alarmBridge != null) { alarmBridge.release(); alarmBridge = null; }\n        if (sleepBridge != null) { sleepBridge.release(); sleepBridge = null; }''',
'''        if (snowBridge != null) { snowBridge.release(); snowBridge = null; }\n        if (alarmBridge != null) { alarmBridge.release(); alarmBridge = null; }\n        if (sleepBridge != null) { sleepBridge.release(); sleepBridge = null; }''',
'design snow release')
replace_once(design,
'''            webView.loadUrl("about:blank");\n            webView.removeJavascriptInterface("YamoneAlarm");''',
'''            webView.loadUrl("about:blank");\n            webView.removeJavascriptInterface("YamoneSnow");\n            webView.removeJavascriptInterface("YamoneAlarm");''',
'design remove snow interface')

# Manifest feature gate: these components are enabled only in debug builds via
# the snowFeatureEnabled manifest placeholder.
manifest = ROOT / 'app/src/main/AndroidManifest.xml'
replace_once(manifest,
'''        <activity android:name=".SkiActivity" android:screenOrientation="portrait" android:exported="false" />''',
'''        <activity android:name=".SkiActivity" android:screenOrientation="portrait" android:enabled="${snowFeatureEnabled}" android:exported="false" />''',
'manifest SkiActivity gate')
replace_once(manifest,
'''        <activity android:name=".SkiSessionDetailActivity" android:screenOrientation="portrait" android:exported="false" />''',
'''        <activity android:name=".SkiSessionDetailActivity" android:screenOrientation="portrait" android:enabled="${snowFeatureEnabled}" android:exported="false" />''',
'manifest ski detail gate')
replace_once(manifest,
'''        <activity android:name=".SkiWaitTimesActivity" android:screenOrientation="portrait" android:exported="false" />''',
'''        <activity android:name=".SkiWaitTimesActivity" android:screenOrientation="portrait" android:enabled="${snowFeatureEnabled}" android:exported="false" />''',
'manifest ski wait gate')
replace_once(manifest,
'''        <service android:name=".SkiRecorderService" android:foregroundServiceType="location" android:exported="false" />''',
'''        <service android:name=".SkiRecorderService" android:foregroundServiceType="location" android:enabled="${snowFeatureEnabled}" android:exported="false" />''',
'manifest ski recorder gate')
replace_once(manifest,
'''        <receiver android:name=".AlarmReceiver" android:exported="false" />''',
'''        <receiver android:name=".SnowGeofenceReceiver" android:enabled="${snowFeatureEnabled}" android:exported="false" />\n        <receiver android:name=".AlarmReceiver" android:exported="false" />''',
'manifest snow geofence receiver')

print('Applied v0.28.01 developer-only real Snow integration.')
