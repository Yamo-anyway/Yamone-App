from pathlib import Path
ROOT = Path('app/src/main/java/com/yamo/snorelab')

def patch(name, old, new, label):
    p = ROOT / name
    s = p.read_text()
    if old not in s:
        raise SystemExit('missing ' + label)
    p.write_text(s.replace(old, new, 1))

patch('WalkingRecorderService.java',
'''    @Override public void onSensorChanged(SensorEvent event) {
        if (isCycling() || !recording || event == null || event.sensor == null || event.sensor.getType() != Sensor.TYPE_STEP_COUNTER) return;''',
'''    @Override public void onSensorChanged(SensorEvent event) {
        if (isCycling() || !recording || paused || event == null || event.sensor == null || event.sensor.getType() != Sensor.TYPE_STEP_COUNTER) return;''',
'ignore steps while paused')

patch('WalkingRecorderService.java',
'''        lastStepDetectedMs = 0;
        currentSpeedKmh = 0;
        resetAutoPending();''',
'''        lastStepDetectedMs = 0;
        stepBase = -1f;
        currentSpeedKmh = 0;
        resetAutoPending();''',
'rebase step counter after resume')

patch('SkiRecorderService.java',
'''    private void finishRecording() {
        if (!recording && !runtime.getBoolean(KEY_RECORDING, false)) {
            stopSelf();
            return;
        }
        recording = true;
        if (sessionDir == null) {
            String path = runtime.getString(KEY_SESSION_DIR, "");
            if (!path.isEmpty()) sessionDir = new File(path);
        }
        long now = System.currentTimeMillis();''',
'''    private void finishRecording() {
        if (!recording && !runtime.getBoolean(KEY_RECORDING, false)) {
            stopSelf();
            return;
        }
        if (!recording) {
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
            state = STATE_CHECKING;
            currentSpeedKmh = 0f;
            clearActiveLift();
        }
        recording = true;
        if (sessionDir == null) {
            String path = runtime.getString(KEY_SESSION_DIR, "");
            if (!path.isEmpty()) sessionDir = new File(path);
        }
        long now = System.currentTimeMillis();''',
'ski stop restores persisted aggregates')

for name, token in {
    'WalkingRecorderService.java': '|| paused ||',
    'SkiRecorderService.java': 'descentCount = runtime.getInt(KEY_DESCENT_COUNT, 0);',
}.items():
    if token not in (ROOT / name).read_text():
        raise SystemExit('guard failed ' + name)
