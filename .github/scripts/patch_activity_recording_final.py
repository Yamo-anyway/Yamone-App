from pathlib import Path

ROOT = Path('app/src/main/java/com/yamo/snorelab')

def read(name): return (ROOT / name).read_text()
def write(name, text): (ROOT / name).write_text(text)
def rep(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing {label}: {old[:180]}')
    return text.replace(old, new, 1)

# -----------------------------------------------------------------------------
# Walking / running / cycling: sticky recovery, pause timing persistence,
# split persistence, step continuity, and provider re-registration.
# -----------------------------------------------------------------------------
s = read('WalkingRecorderService.java')
s = rep(s, 'import org.json.JSONObject;\n', 'import org.json.JSONArray;\nimport org.json.JSONObject;\n', 'walking JSONArray import')
s = rep(s,
'''    public static final String KEY_RUNNING_MOVING_MS = "running_moving_ms";''',
'''    public static final String KEY_RUNNING_MOVING_MS = "running_moving_ms";
    public static final String KEY_PAUSED_ACCUM_MS = "paused_accum_ms";
    public static final String KEY_PAUSE_STARTED_MS = "pause_started_ms";
    public static final String KEY_PERSISTED_AT_MS = "persisted_at_ms";
    public static final String KEY_SPLITS_JSON = "splits_json";''', 'walking recovery keys')
s = rep(s,
'''    private File sessionDir;
    private long goalDistanceM;''',
'''    private File sessionDir;
    private boolean gpsRegistered;
    private boolean networkRegistered;
    private long lastProviderRefreshAt;
    private long goalDistanceM;''', 'walking provider fields')
s = rep(s,
'''    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!recording) return;
            persistRuntime();
            checkGoal();
            updateForegroundNotification();
            handler.postDelayed(this, 1000);
        }
    };''',
'''    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!recording) return;
            long now = System.currentTimeMillis();
            if (now - lastProviderRefreshAt >= 30_000L) refreshLocationProviderRegistrations();
            persistRuntime();
            checkGoal();
            updateForegroundNotification();
            handler.postDelayed(this, 1000);
        }
    };''', 'walking ticker provider recovery')
s = rep(s,
'''    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_START.equals(action)) begin(intent);
        else if (ACTION_PAUSE.equals(action)) pauseRecording();
        else if (ACTION_RESUME.equals(action)) resumeRecording();
        else if (ACTION_STOP.equals(action)) finishRecording();
        return START_NOT_STICKY;
    }''',
'''    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            if (runtime.getBoolean(KEY_RECORDING, false)) recoverRecordingAfterProcessRestart();
            else stopSelf();
        } else if (ACTION_START.equals(action)) begin(intent);
        else if (ACTION_PAUSE.equals(action)) pauseRecording();
        else if (ACTION_RESUME.equals(action)) resumeRecording();
        else if (ACTION_STOP.equals(action)) finishRecording();
        return recording ? START_STICKY : START_NOT_STICKY;
    }''', 'walking sticky onStart')
s = rep(s,
'''    private void startLocation() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return;
        locationListener = this::onLocationChanged;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, locationListener, Looper.getMainLooper());
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 3f, locationListener, Looper.getMainLooper());
            }
        } catch (SecurityException ignored) {}
    }''',
'''    private void startLocation() {
        stopLocationOnly();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return;
        locationListener = this::onLocationChanged;
        gpsRegistered = false;
        networkRegistered = false;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, locationListener, Looper.getMainLooper());
                gpsRegistered = true;
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 3f, locationListener, Looper.getMainLooper());
                networkRegistered = true;
            }
        } catch (SecurityException ignored) {}
        lastProviderRefreshAt = System.currentTimeMillis();
    }

    private void refreshLocationProviderRegistrations() {
        lastProviderRefreshAt = System.currentTimeMillis();
        if (!recording || !hasLocationPermission()) return;
        LocationManager manager = locationManager;
        if (manager == null) {
            startLocation();
            return;
        }
        boolean gpsEnabled = false, networkEnabled = false;
        try { gpsEnabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
        try { networkEnabled = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER); } catch (Exception ignored) {}
        if (!gpsEnabled) gpsRegistered = false;
        if (!networkEnabled) networkRegistered = false;
        if ((gpsEnabled && !gpsRegistered) || (networkEnabled && !networkRegistered)) {
            lastAccepted = null;
            lastAcceptedTime = 0L;
            lastAcceptedSpeedMps = 0f;
            startLocation();
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }''', 'walking provider helpers')
s = rep(s,
'''        if (stepBase < 0) stepBase = current;
        long previousSteps = steps;''',
'''        if (stepBase < 0) stepBase = current - steps;
        long previousSteps = steps;''', 'walking step continuity')
s = rep(s,
'''                .putString(KEY_GOAL_STATE, goalState)
                .apply();''',
'''                .putString(KEY_GOAL_STATE, goalState)
                .putLong(KEY_PAUSED_ACCUM_MS, pausedAccumMs)
                .putLong(KEY_PAUSE_STARTED_MS, pauseStartedMs)
                .putLong(KEY_PERSISTED_AT_MS, System.currentTimeMillis())
                .putString(KEY_SPLITS_JSON, WalkingStore.longListToJson(splitsMs).toString())
                .apply();''', 'walking persist recovery state')

# Replace persisted stop restore with common recovery-capable restore and recovery method.
old = '''    private void restorePersistedSessionForStop() {
        activityType = runtime.getString(KEY_ACTIVITY_TYPE, "walking");
        startMs = runtime.getLong(KEY_START_MS, 0L);
        movingMs = runtime.getLong(KEY_MOVING_MS, 0L);
        distanceM = runtime.getLong(KEY_DISTANCE_M, 0L);
        steps = runtime.getLong(KEY_STEPS, 0L);
        stepAvailable = runtime.getBoolean(KEY_STEP_AVAILABLE, false);
        maxSpeedKmh = runtime.getFloat(KEY_MAX_SPEED_KMH, 0f);
        altitudeM = runtime.getFloat(KEY_ALTITUDE_M, Float.NaN);
        accuracyM = runtime.getFloat(KEY_ACCURACY_M, Float.NaN);
        goalDistanceM = runtime.getLong(KEY_GOAL_DISTANCE_M, 0L);
        goalTimeMs = runtime.getLong(KEY_GOAL_TIME_MS, 0L);
        goalState = runtime.getString(KEY_GOAL_STATE, "ACTIVE");
        autoMotionMode = runtime.getString(KEY_AUTO_MOTION_MODE, "walking");
        walkingDistanceM = runtime.getLong(KEY_WALKING_DISTANCE_M, 0L);
        runningDistanceM = runtime.getLong(KEY_RUNNING_DISTANCE_M, 0L);
        walkingMovingMs = runtime.getLong(KEY_WALKING_MOVING_MS, 0L);
        runningMovingMs = runtime.getLong(KEY_RUNNING_MOVING_MS, 0L);
        paused = runtime.getBoolean(KEY_PAUSED, false);
        String path = runtime.getString(KEY_SESSION_DIR, "");
        sessionDir = path.isEmpty() ? null : new File(path);

        long persistedElapsed = runtime.getLong(KEY_ELAPSED_MS, 0L);
        if (startMs > 0L && persistedElapsed >= 0L) {
            pausedAccumMs = Math.max(0L, endOfPersistedWindow() - startMs - persistedElapsed);
        }
        pauseStartedMs = 0L;
    }

    private long endOfPersistedWindow() {
        return System.currentTimeMillis();
    }'''
new = '''    private void restorePersistedSessionForStop() {
        restorePersistedState();
    }

    private void restorePersistedState() {
        activityType = runtime.getString(KEY_ACTIVITY_TYPE, "walking");
        startMs = runtime.getLong(KEY_START_MS, 0L);
        movingMs = runtime.getLong(KEY_MOVING_MS, 0L);
        distanceM = runtime.getLong(KEY_DISTANCE_M, 0L);
        steps = runtime.getLong(KEY_STEPS, 0L);
        stepAvailable = runtime.getBoolean(KEY_STEP_AVAILABLE, false);
        maxSpeedKmh = runtime.getFloat(KEY_MAX_SPEED_KMH, 0f);
        altitudeM = runtime.getFloat(KEY_ALTITUDE_M, Float.NaN);
        accuracyM = runtime.getFloat(KEY_ACCURACY_M, Float.NaN);
        goalDistanceM = runtime.getLong(KEY_GOAL_DISTANCE_M, 0L);
        goalTimeMs = runtime.getLong(KEY_GOAL_TIME_MS, 0L);
        goalState = runtime.getString(KEY_GOAL_STATE, "ACTIVE");
        autoMotionMode = runtime.getString(KEY_AUTO_MOTION_MODE, "walking");
        pendingAutoMotionMode = autoMotionMode;
        walkingDistanceM = runtime.getLong(KEY_WALKING_DISTANCE_M, 0L);
        runningDistanceM = runtime.getLong(KEY_RUNNING_DISTANCE_M, 0L);
        walkingMovingMs = runtime.getLong(KEY_WALKING_MOVING_MS, 0L);
        runningMovingMs = runtime.getLong(KEY_RUNNING_MOVING_MS, 0L);
        paused = runtime.getBoolean(KEY_PAUSED, false);
        pausedAccumMs = runtime.getLong(KEY_PAUSED_ACCUM_MS, -1L);
        pauseStartedMs = runtime.getLong(KEY_PAUSE_STARTED_MS, 0L);
        if (pausedAccumMs < 0L) {
            long persistedElapsed = runtime.getLong(KEY_ELAPSED_MS, 0L);
            long persistedAt = runtime.getLong(KEY_PERSISTED_AT_MS, System.currentTimeMillis());
            pausedAccumMs = Math.max(0L, persistedAt - startMs - persistedElapsed);
            if (paused && pauseStartedMs <= 0L) pauseStartedMs = persistedAt;
        }
        String path = runtime.getString(KEY_SESSION_DIR, "");
        sessionDir = path.isEmpty() ? null : new File(path);
        splitsMs.clear();
        try {
            JSONArray values = new JSONArray(runtime.getString(KEY_SPLITS_JSON, "[]"));
            for (int i = 0; i < values.length(); i++) splitsMs.add(Math.max(0L, values.optLong(i, 0L)));
        } catch (Exception ignored) {}
        long splitTotal = 0L;
        for (Long value : splitsMs) splitTotal += value == null ? 0L : Math.max(0L, value);
        lastSplitMovingMs = Math.min(movingMs, splitTotal);
        nextSplitM = Math.max(1000L, ((long) Math.floor(distanceM / 1000.0) + 1L) * 1000L);
        lastAccepted = null;
        lastAcceptedTime = 0L;
        lastAcceptedSpeedMps = 0f;
        lastStepDetectedMs = 0L;
        currentSpeedKmh = 0f;
        stepBase = -1f;
        resetAutoPending();
        resetMaxSpeedCandidate();
    }

    private void recoverRecordingAfterProcessRestart() {
        if (recording || !runtime.getBoolean(KEY_RECORDING, false)) return;
        if (!hasLocationPermission()) {
            stopSelf();
            return;
        }
        restorePersistedState();
        if (sessionDir == null || !sessionDir.exists() || startMs <= 0L) {
            runtime.edit().putBoolean(KEY_RECORDING, false).putBoolean(KEY_PAUSED, false).apply();
            stopSelf();
            return;
        }
        recording = true;
        Notification n = buildRecordingNotification(activityLabel() + (paused ? " 기록 일시정지" : " 기록 복구 중"));
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFY_RECORDING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        else startForeground(NOTIFY_RECORDING, n);
        startLocation();
        if (!isCycling()) startSteps();
        persistRuntime();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }'''
s = rep(s, old, new, 'walking persisted restore/recovery')
s = rep(s,
'''    private void stopSensors() {
        if (locationManager != null && locationListener != null) {
            try { locationManager.removeUpdates(locationListener); } catch (Exception ignored) {}
        }
        if (sensorManager != null) {
            try { sensorManager.unregisterListener(this); } catch (Exception ignored) {}
        }
    }''',
'''    private void stopLocationOnly() {
        if (locationManager != null && locationListener != null) {
            try { locationManager.removeUpdates(locationListener); } catch (Exception ignored) {}
        }
        locationListener = null;
        locationManager = null;
        gpsRegistered = false;
        networkRegistered = false;
    }

    private void stopSensors() {
        stopLocationOnly();
        if (sensorManager != null) {
            try { sensorManager.unregisterListener(this); } catch (Exception ignored) {}
        }
        sensorManager = null;
        stepCounter = null;
    }''', 'walking stop location helper')
write('WalkingRecorderService.java', s)

# -----------------------------------------------------------------------------
# Hiking: sticky recovery and provider re-registration.
# -----------------------------------------------------------------------------
s = read('HikingRecorderService.java')
s = rep(s,
'''    private File sessionDir;

    private final Runnable ticker''',
'''    private File sessionDir;
    private boolean gpsRegistered;
    private boolean networkRegistered;
    private long lastProviderRefreshAt;

    private final Runnable ticker''', 'hiking provider fields')
s = rep(s,
'''        @Override public void run() {
            if (!recording) return;
            persist();
            updateNotification();
            handler.postDelayed(this, 1000L);
        }''',
'''        @Override public void run() {
            if (!recording) return;
            if (System.currentTimeMillis() - lastProviderRefreshAt >= 30_000L) refreshLocationProviderRegistrations();
            persist();
            updateNotification();
            handler.postDelayed(this, 1000L);
        }''', 'hiking ticker provider recovery')
s = rep(s,
'''    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_START.equals(action)) startRecording();
        else if (ACTION_STOP.equals(action)) stopRecording();
        return START_NOT_STICKY;
    }''',
'''    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            if (runtime.getBoolean(KEY_RECORDING, false)) recoverRecordingAfterProcessRestart();
            else stopSelf();
        } else if (ACTION_START.equals(action)) startRecording();
        else if (ACTION_STOP.equals(action)) stopRecording();
        return recording ? START_STICKY : START_NOT_STICKY;
    }''', 'hiking sticky onStart')
s = rep(s,
'''    private void startLocation() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null || !hasLocationPermission()) return;
        locationListener = this::onLocation;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f,
                        locationListener, Looper.getMainLooper());
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 3f,
                        locationListener, Looper.getMainLooper());
            }
        } catch (SecurityException ignored) {}
    }''',
'''    private void startLocation() {
        stopLocation();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null || !hasLocationPermission()) return;
        locationListener = this::onLocation;
        gpsRegistered = false;
        networkRegistered = false;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f,
                        locationListener, Looper.getMainLooper());
                gpsRegistered = true;
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 3f,
                        locationListener, Looper.getMainLooper());
                networkRegistered = true;
            }
        } catch (SecurityException ignored) {}
        lastProviderRefreshAt = System.currentTimeMillis();
    }

    private void refreshLocationProviderRegistrations() {
        lastProviderRefreshAt = System.currentTimeMillis();
        if (!recording || !hasLocationPermission()) return;
        LocationManager manager = locationManager;
        if (manager == null) { startLocation(); return; }
        boolean gpsEnabled = false, networkEnabled = false;
        try { gpsEnabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
        try { networkEnabled = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER); } catch (Exception ignored) {}
        if (!gpsEnabled) gpsRegistered = false;
        if (!networkEnabled) networkRegistered = false;
        if ((gpsEnabled && !gpsRegistered) || (networkEnabled && !networkRegistered)) {
            lastLocation = null;
            lastLocationTime = 0L;
            lastSpeedMps = 0f;
            startLocation();
        }
    }''', 'hiking provider helpers')
insert = '''    private void stopRecording() {'''
helper = '''    private void recoverRecordingAfterProcessRestart() {
        if (recording || !runtime.getBoolean(KEY_RECORDING, false)) return;
        if (!hasLocationPermission()) { stopSelf(); return; }
        String path = runtime.getString(KEY_SESSION_DIR, "");
        sessionDir = path.isEmpty() ? null : new File(path);
        startMs = runtime.getLong(KEY_START_MS, 0L);
        distanceM = runtime.getLong(KEY_DISTANCE_M, 0L);
        ascentM = runtime.getLong(KEY_ASCENT_M, 0L);
        smoothedAltitude = runtime.getFloat(KEY_ALTITUDE_M, Float.NaN);
        maxAltitude = runtime.getFloat(KEY_MAX_ALTITUDE_M, Float.NaN);
        minAltitude = runtime.getFloat(KEY_MIN_ALTITUDE_M, Float.NaN);
        accuracyM = runtime.getFloat(KEY_ACCURACY_M, Float.NaN);
        lastAltitude = smoothedAltitude;
        lastLocation = null;
        lastLocationTime = 0L;
        lastSpeedMps = 0f;
        if (sessionDir == null || !sessionDir.exists() || startMs <= 0L) {
            runtime.edit().putBoolean(KEY_RECORDING, false).apply();
            stopSelf();
            return;
        }
        recording = true;
        Notification notification = buildNotification("등산 기록을 복구했습니다.");
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFY, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        else startForeground(NOTIFY, notification);
        startLocation();
        persist();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

'''
if insert not in s: raise SystemExit('missing hiking recovery insertion')
s = s.replace(insert, helper + insert, 1)
s = rep(s,
'''        locationListener = null;
        locationManager = null;
    }''',
'''        locationListener = null;
        locationManager = null;
        gpsRegistered = false;
        networkRegistered = false;
    }''', 'hiking clear provider flags')
write('HikingRecorderService.java', s)

# -----------------------------------------------------------------------------
# Ski / snowboard: sticky aggregate recovery and provider re-registration.
# Transient detector candidates deliberately restart from a fresh anchor.
# -----------------------------------------------------------------------------
s = read('SkiRecorderService.java')
s = rep(s,
'''    private int rejectedGpsPoints;

    private int descentCount;''',
'''    private int rejectedGpsPoints;
    private boolean gpsRegistered;
    private boolean networkRegistered;
    private long lastProviderRefreshAt;

    private int descentCount;''', 'ski provider fields')
s = rep(s,
'''        @Override public void run() {
            if (!recording) return;
            persistRuntime();
            persistSessionMetrics();
            updateNotification();
            handler.postDelayed(this, 3000L);
        }''',
'''        @Override public void run() {
            if (!recording) return;
            if (System.currentTimeMillis() - lastProviderRefreshAt >= 30_000L) refreshLocationProviderRegistrations();
            persistRuntime();
            persistSessionMetrics();
            updateNotification();
            handler.postDelayed(this, 3000L);
        }''', 'ski ticker provider recovery')
s = rep(s,
'''    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_START.equals(intent.getAction())) begin(intent);
        else if (ACTION_STOP.equals(intent.getAction())) finishRecording();
        return START_NOT_STICKY;
    }''',
'''    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            if (runtime.getBoolean(KEY_RECORDING, false)) recoverRecordingAfterProcessRestart();
            else stopSelf();
        } else if (ACTION_START.equals(action)) begin(intent);
        else if (ACTION_STOP.equals(action)) finishRecording();
        return recording ? START_STICKY : START_NOT_STICKY;
    }''', 'ski sticky onStart')
s = rep(s,
'''    private void startLocation() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return;
        locationListener = this::onLocationChanged;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, locationListener, Looper.getMainLooper());
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 3f, locationListener, Looper.getMainLooper());
            }
        } catch (SecurityException ignored) {}
    }''',
'''    private void startLocation() {
        stopLocation();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return;
        locationListener = this::onLocationChanged;
        gpsRegistered = false;
        networkRegistered = false;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, locationListener, Looper.getMainLooper());
                gpsRegistered = true;
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 3f, locationListener, Looper.getMainLooper());
                networkRegistered = true;
            }
        } catch (SecurityException ignored) {}
        lastProviderRefreshAt = System.currentTimeMillis();
    }

    private void refreshLocationProviderRegistrations() {
        lastProviderRefreshAt = System.currentTimeMillis();
        if (!recording || !hasLocationPermission()) return;
        LocationManager manager = locationManager;
        if (manager == null) { startLocation(); return; }
        boolean gpsEnabled = false, networkEnabled = false;
        try { gpsEnabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
        try { networkEnabled = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER); } catch (Exception ignored) {}
        if (!gpsEnabled) gpsRegistered = false;
        if (!networkEnabled) networkRegistered = false;
        if ((gpsEnabled && !gpsRegistered) || (networkEnabled && !networkRegistered)) {
            lastLoc = null;
            lastTime = 0L;
            currentSpeedKmh = 0f;
            state = STATE_CHECKING;
            liftCandidate = null;
            descentCandidate = null;
            startLocation();
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }''', 'ski provider helpers')
insert = '''    private void finishRecording() {'''
helper = '''    private void recoverRecordingAfterProcessRestart() {
        if (recording || !runtime.getBoolean(KEY_RECORDING, false)) return;
        if (!hasLocationPermission()) { stopSelf(); return; }
        sport = runtime.getString(KEY_SPORT, "ski");
        startMs = runtime.getLong(KEY_START_MS, 0L);
        String path = runtime.getString(KEY_SESSION_DIR, "");
        sessionDir = path.isEmpty() ? null : new File(path);
        maxSpeedKmh = runtime.getFloat(KEY_MAX_SPEED_KMH, 0f);
        smoothedAltitude = runtime.getFloat(KEY_ALTITUDE_M, Float.NaN);
        accuracyM = runtime.getFloat(KEY_ACCURACY_M, Float.NaN);
        descentCount = runtime.getInt(KEY_DESCENT_COUNT, 0);
        liftCount = runtime.getInt(KEY_LIFT_COUNT, 0);
        descentDistanceM = runtime.getLong(KEY_DESCENT_DISTANCE_M, 0L);
        descentVerticalM = runtime.getLong(KEY_DESCENT_VERTICAL_M, 0L);
        liftTimeMs = runtime.getLong(KEY_LIFT_TIME_MS, 0L);
        waitTimeMs = runtime.getLong(KEY_WAIT_TIME_MS, 0L);
        lastLat = runtime.contains(KEY_LAT) ? runtime.getFloat(KEY_LAT, Float.NaN) : Double.NaN;
        lastLon = runtime.contains(KEY_LON) ? runtime.getFloat(KEY_LON, Float.NaN) : Double.NaN;
        if (sessionDir == null || !sessionDir.exists() || startMs <= 0L) {
            clearRuntime();
            stopSelf();
            return;
        }
        recording = true;
        state = STATE_CHECKING;
        lastLoc = null;
        lastTime = 0L;
        currentSpeedKmh = 0f;
        slowStartMs = 0L;
        slowAnchor = null;
        waitCandidateStartMs = 0L;
        waitCandidateAnchor = null;
        liftCandidate = null;
        descentCandidate = null;
        resortDetectInFlight = false;
        clearActiveLift();
        Notification n = buildNotification("스키 기록을 복구했습니다.");
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFY_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        else startForeground(NOTIFY_ID, n);
        startLocation();
        persistRuntime();
        persistSessionMetrics();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

'''
if insert not in s: raise SystemExit('missing ski recovery insertion')
s = s.replace(insert, helper + insert, 1)
s = rep(s,
'''        locationListener = null;
        locationManager = null;
    }''',
'''        locationListener = null;
        locationManager = null;
        gpsRegistered = false;
        networkRegistered = false;
    }''', 'ski clear provider flags')
s = rep(s,
'''        String label = STATE_DESCENT.equals(state) ? "⛷ 활주 중"
                : STATE_LIFT.equals(state) ? "🚡 리프트 이동"
                : STATE_STOPPED.equals(state) ? "● 정지"
                : "GPS 움직임 판별 중";''',
'''        String label = STATE_DESCENT.equals(state) ? "활주 중"
                : STATE_LIFT.equals(state) ? "리프트 이동"
                : STATE_STOPPED.equals(state) ? "정지"
                : "GPS 움직임 판별 중";''', 'ski notification text cleanup')
write('SkiRecorderService.java', s)

# -----------------------------------------------------------------------------
# App process re-open fallback: restart any user-started active recorder that
# still has an active local runtime mirror. START_STICKY remains the primary path.
# -----------------------------------------------------------------------------
s = read('YamoneApplication.java')
s = rep(s,
'''    private boolean locationSharingRecoveryAttempted;''',
'''    private boolean locationSharingRecoveryAttempted;
    private boolean activityRecordingRecoveryAttempted;''', 'application activity recovery flag')
s = rep(s,
'''            LocationSharingHomeUiEnhancer.refresh(main);
            recoverLocationSharingIfNeeded(main);''',
'''            LocationSharingHomeUiEnhancer.refresh(main);
            recoverLocationSharingIfNeeded(main);
            recoverActivityRecordingIfNeeded(main);''', 'application activity recovery call')
insert = '''    @Override public void onActivityDestroyed(Activity activity) {'''
helper = '''    private void recoverActivityRecordingIfNeeded(MainActivity activity) {
        if (activityRecordingRecoveryAttempted) return;
        activityRecordingRecoveryAttempted = true;
        boolean hasLocation = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasLocation) return;
        if (activity.getSharedPreferences(WalkingRecorderService.PREFS, MODE_PRIVATE)
                .getBoolean(WalkingRecorderService.KEY_RECORDING, false)) {
            startRecorder(activity, new Intent(activity, WalkingRecorderService.class));
        }
        if (activity.getSharedPreferences(HikingRecorderService.PREFS, MODE_PRIVATE)
                .getBoolean(HikingRecorderService.KEY_RECORDING, false)) {
            startRecorder(activity, new Intent(activity, HikingRecorderService.class));
        }
        if (activity.getSharedPreferences(SkiRecorderService.PREFS, MODE_PRIVATE)
                .getBoolean(SkiRecorderService.KEY_RECORDING, false)) {
            startRecorder(activity, new Intent(activity, SkiRecorderService.class));
        }
    }

    private void startRecorder(Activity activity, Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= 26) activity.startForegroundService(intent);
            else activity.startService(intent);
        } catch (Exception ignored) {}
    }

'''
if insert not in s: raise SystemExit('missing application activity recovery insertion')
s = s.replace(insert, helper + insert, 1)
write('YamoneApplication.java', s)

for name, tokens in {
    'WalkingRecorderService.java': ['recoverRecordingAfterProcessRestart', 'KEY_SPLITS_JSON', 'refreshLocationProviderRegistrations', 'START_STICKY'],
    'HikingRecorderService.java': ['recoverRecordingAfterProcessRestart', 'refreshLocationProviderRegistrations', 'START_STICKY'],
    'SkiRecorderService.java': ['recoverRecordingAfterProcessRestart', 'refreshLocationProviderRegistrations', 'START_STICKY'],
    'YamoneApplication.java': ['recoverActivityRecordingIfNeeded(main)', 'WalkingRecorderService.KEY_RECORDING'],
}.items():
    text = read(name)
    for token in tokens:
        if token not in text:
            raise SystemExit(f'guard failed {name}: {token}')
