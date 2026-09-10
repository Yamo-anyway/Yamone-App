from pathlib import Path

ROOT = Path('app/src/main/java/com/yamo/snorelab')

def p(name): return ROOT / name

def read(name): return p(name).read_text()

def write(name, text): p(name).write_text(text)

def rep(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing {label}: {old[:160]}')
    return text.replace(old, new, 1)

# 1) Developer-only full-night recording must be opt-in, not default-on.
s = read('SleepRecorderService.java')
s = rep(s,
        'boolean fullRecording = prefs.getBoolean("developer_full_recording", true);',
        'boolean fullRecording = prefs.getBoolean("developer_full_recording", false);',
        'sleep developer full recording default')
write('SleepRecorderService.java', s)

s = read('MainActivity.java')
s = rep(s,
        'settingSwitch("전체 녹음", "개발자 검증용 AAC 전체 녹음 저장", "developer_full_recording", true)',
        'settingSwitch("전체 녹음", "개발자 검증용 AAC 전체 녹음 저장", "developer_full_recording", false)',
        'settings developer full recording default')
write('MainActivity.java', s)

# 2) Recover a persisted walking/running/cycling session before stopping it.
# Hiking/Ski already restore their runtime state on a service-process restart; Walking should too.
s = read('WalkingRecorderService.java')
old = '''    private void finishRecording() {
        if (!recording && !runtime.getBoolean(KEY_RECORDING, false)) {
            stopSelf();
            return;
        }
        long end = System.currentTimeMillis();
        if (paused && pauseStartedMs > 0) pausedAccumMs += Math.max(0, end - pauseStartedMs);
        paused = false;
        pauseStartedMs = 0;
        persistRuntime();
        writeMeta("complete", end);
        recording = false;
        currentSpeedKmh = 0;
        runtime.edit()
                .putBoolean(KEY_RECORDING, false)
                .putBoolean(KEY_PAUSED, false)
                .putFloat(KEY_CURRENT_SPEED_KMH, 0f)
                .apply();
        handler.removeCallbacks(ticker);
        stopSensors();
        stopForeground(true);
        stopSelf();
    }'''
new = '''    private void finishRecording() {
        if (!recording && !runtime.getBoolean(KEY_RECORDING, false)) {
            stopSelf();
            return;
        }
        long end = System.currentTimeMillis();
        if (!recording) restorePersistedSessionForStop();
        if (paused && pauseStartedMs > 0) pausedAccumMs += Math.max(0, end - pauseStartedMs);
        paused = false;
        pauseStartedMs = 0;
        // Temporarily treat a restored session as active so elapsedMs() and writeMeta()
        // can finalize the persisted record instead of leaving status=recording forever.
        recording = true;
        persistRuntime();
        writeMeta("complete", end);
        recording = false;
        currentSpeedKmh = 0;
        runtime.edit()
                .putBoolean(KEY_RECORDING, false)
                .putBoolean(KEY_PAUSED, false)
                .putFloat(KEY_CURRENT_SPEED_KMH, 0f)
                .apply();
        handler.removeCallbacks(ticker);
        stopSensors();
        try { stopForeground(true); } catch (Exception ignored) {}
        stopSelf();
    }

    private void restorePersistedSessionForStop() {
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
s = rep(s, old, new, 'walking stop recovery')
write('WalkingRecorderService.java', s)

# 3) If an alarm has no retry path, its one-minute automatic sound stop should also clear active state.
s = read('AlarmRingService.java')
s = rep(s,
        'private final Runnable autoStop = () -> stopRinging(false);',
        '''private final Runnable autoStop = () -> {
        if (item != null && item.retryCount == 0 && alarmId >= 0) {
            AlarmScheduler.markActive(this, alarmId, false, 0);
        }
        stopRinging(false);
    };''',
        'alarm auto-stop active cleanup')
write('AlarmRingService.java', s)

# Guards.
checks = {
    'SleepRecorderService.java': ['getBoolean("developer_full_recording", false)'],
    'MainActivity.java': ['"developer_full_recording", false'],
    'WalkingRecorderService.java': ['restorePersistedSessionForStop()', 'writeMeta("complete", end)'],
    'AlarmRingService.java': ['item.retryCount == 0', 'AlarmScheduler.markActive(this, alarmId, false, 0)'],
}
for name, tokens in checks.items():
    text = read(name)
    for token in tokens:
        if token not in text:
            raise SystemExit(f'guard failed {name}: {token}')
