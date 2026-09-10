package com.yamo.snorelab;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WalkingRecorderService extends Service implements SensorEventListener {
    public static final String ACTION_START = "com.yamo.snorelab.WALK_START";
    public static final String ACTION_PAUSE = "com.yamo.snorelab.WALK_PAUSE";
    public static final String ACTION_RESUME = "com.yamo.snorelab.WALK_RESUME";
    public static final String ACTION_STOP = "com.yamo.snorelab.WALK_STOP";

    public static final String PREFS = "snorelab_walking_runtime_v1";
    public static final String KEY_RECORDING = "recording";
    public static final String KEY_PAUSED = "paused";
    public static final String KEY_ACTIVITY_TYPE = "activity_type";
    public static final String KEY_START_MS = "start_ms";
    public static final String KEY_ELAPSED_MS = "elapsed_ms";
    public static final String KEY_MOVING_MS = "moving_ms";
    public static final String KEY_DISTANCE_M = "distance_m";
    public static final String KEY_STEPS = "steps";
    public static final String KEY_STEP_AVAILABLE = "step_available";
    public static final String KEY_CURRENT_SPEED_KMH = "current_speed_kmh";
    public static final String KEY_MAX_SPEED_KMH = "max_speed_kmh";
    public static final String KEY_ALTITUDE_M = "altitude_m";
    public static final String KEY_ACCURACY_M = "accuracy_m";
    public static final String KEY_SESSION_DIR = "session_dir";
    public static final String KEY_GOAL_DISTANCE_M = "goal_distance_m";
    public static final String KEY_GOAL_TIME_MS = "goal_time_ms";
    public static final String KEY_GOAL_STATE = "goal_state";
    public static final String KEY_AUTO_MOTION_MODE = "auto_motion_mode";
    public static final String KEY_WALKING_DISTANCE_M = "walking_distance_m";
    public static final String KEY_RUNNING_DISTANCE_M = "running_distance_m";
    public static final String KEY_WALKING_MOVING_MS = "walking_moving_ms";
    public static final String KEY_RUNNING_MOVING_MS = "running_moving_ms";
    public static final String KEY_PAUSED_ACCUM_MS = "paused_accum_ms";
    public static final String KEY_PAUSE_STARTED_MS = "pause_started_ms";
    public static final String KEY_PERSISTED_AT_MS = "persisted_at_ms";
    public static final String KEY_SPLITS_JSON = "splits_json";

    private static final String CHANNEL_RECORDING = "walking_recording_v1";
    private static final String CHANNEL_GOAL = "walking_goal_v1";
    private static final int NOTIFY_RECORDING = 5101;
    private static final int NOTIFY_GOAL = 5102;

    // Local GPS filter V5. No paid/external road matching API is used.
    private static final float MAX_ACCEPTABLE_ACCURACY_M = 45f;
    private static final float MIN_NOISE_FLOOR_M = 2.0f;
    private static final float MAX_NOISE_FLOOR_M = 6.0f;
    private static final long GPS_GAP_RESET_MS = 10_000L;
    private static final long GPS_SHADOW_MAX_MS = 10 * 60_000L;
    private static final long RECENT_STEP_WINDOW_MS = 5_000L;

    // Combined walk/run mode uses hysteresis so GPS noise does not flip modes repeatedly.
    private static final float AUTO_RUN_ENTER_MPS = 2.30f; // about 8.3 km/h
    private static final float AUTO_WALK_RETURN_MPS = 1.80f; // about 6.5 km/h
    private static final long AUTO_RUN_CONFIRM_MS = 5_000L;
    private static final long AUTO_WALK_CONFIRM_MS = 8_000L;

    private SharedPreferences runtime;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private SensorManager sensorManager;
    private Sensor stepCounter;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String activityType = "walking";
    private String autoMotionMode = "walking";
    private String pendingAutoMotionMode = "walking";
    private long pendingAutoMotionSince;
    private int autoModeSwitches;
    private long walkingMovingMs;
    private long runningMovingMs;
    private double walkingDistanceM;
    private double runningDistanceM;

    private long startMs;
    private long pausedAccumMs;
    private long pauseStartedMs;
    private long movingMs;
    private double distanceM;
    private long steps;
    private float stepBase = -1f;
    private boolean stepAvailable;
    private long lastStepDetectedMs;
    private boolean recording;
    private boolean paused;
    private float currentSpeedKmh;
    private float maxSpeedKmh;
    private float maxSpeedCandidateKmh;
    private int maxSpeedCandidateSamples;
    private long maxSpeedCandidateStartedAt;
    private double altitudeM = Double.NaN;
    private float accuracyM = Float.NaN;
    private Location lastAccepted;
    private long lastAcceptedTime;
    private float lastAcceptedSpeedMps;
    private long lastWrittenTime;
    private int rejectedGpsPoints;
    private int gpsGapResets;
    private int gpsShadowSegments;
    private double gpsShadowDistanceM;
    private long gpsShadowDurationMs;
    private int stationaryGpsDiscards;
    private File sessionDir;
    private boolean gpsRegistered;
    private boolean networkRegistered;
    private long lastProviderRefreshAt;
    private long goalDistanceM;
    private long goalTimeMs;
    private String goalState = "ACTIVE";
    private boolean goalNotified;
    private long nextSplitM = 1000;
    private long lastSplitMovingMs;
    private final List<Long> splitsMs = new ArrayList<>();

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!recording) return;
            long now = System.currentTimeMillis();
            if (now - lastProviderRefreshAt >= 30_000L) refreshLocationProviderRegistrations();
            persistRuntime();
            checkGoal();
            updateForegroundNotification();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        runtime = getSharedPreferences(PREFS, MODE_PRIVATE);
        createChannels();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            if (runtime.getBoolean(KEY_RECORDING, false)) recoverRecordingAfterProcessRestart();
            else stopSelf();
        } else if (ACTION_START.equals(action)) begin(intent);
        else if (ACTION_PAUSE.equals(action)) pauseRecording();
        else if (ACTION_RESUME.equals(action)) resumeRecording();
        else if (ACTION_STOP.equals(action)) finishRecording();
        return recording ? START_STICKY : START_NOT_STICKY;
    }

    private void begin(Intent intent) {
        if (recording || runtime.getBoolean(KEY_RECORDING, false)) return;
        String requestedType = intent.getStringExtra("activity_type");
        if ("cycling".equals(requestedType)) activityType = "cycling";
        else if ("running".equals(requestedType)) activityType = "running";
        else if ("walkrun".equals(requestedType)) activityType = "walkrun";
        else activityType = "walking";

        startMs = System.currentTimeMillis();
        goalDistanceM = Math.max(0, intent.getLongExtra("goal_distance_m", 0));
        goalTimeMs = Math.max(0, intent.getLongExtra("goal_time_ms", 0));
        sessionDir = WalkingStore.createSession(this, startMs);
        recording = true;
        paused = false;
        goalState = "ACTIVE";
        goalNotified = false;
        rejectedGpsPoints = 0;
        gpsGapResets = 0;
        gpsShadowSegments = 0;
        gpsShadowDistanceM = 0;
        gpsShadowDurationMs = 0;
        stationaryGpsDiscards = 0;
        lastStepDetectedMs = 0;
        lastAcceptedSpeedMps = 0f;
        lastAccepted = null;
        lastAcceptedTime = 0;
        lastWrittenTime = 0;
        distanceM = 0;
        movingMs = 0;
        steps = 0;
        stepBase = -1f;
        stepAvailable = false;
        maxSpeedKmh = 0f;
        currentSpeedKmh = 0f;
        maxSpeedCandidateKmh = 0f;
        maxSpeedCandidateSamples = 0;
        maxSpeedCandidateStartedAt = 0L;
        altitudeM = Double.NaN;
        accuracyM = Float.NaN;
        nextSplitM = 1000;
        lastSplitMovingMs = 0;
        splitsMs.clear();

        autoMotionMode = "walking";
        pendingAutoMotionMode = "walking";
        pendingAutoMotionSince = 0;
        autoModeSwitches = 0;
        walkingMovingMs = 0;
        runningMovingMs = 0;
        walkingDistanceM = 0;
        runningDistanceM = 0;

        Notification n = buildRecordingNotification(activityLabel() + " 기록을 시작합니다");
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFY_RECORDING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        else startForeground(NOTIFY_RECORDING, n);

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            goalState = "ERROR";
            finishRecording();
            return;
        }

        startLocation();
        if (!isCycling()) startSteps();
        persistRuntime();
        writeMeta("recording", 0L);
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void startLocation() {
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
    }

    private void startSteps() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) return;
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        stepAvailable = stepCounter != null;
        if (stepCounter != null) {
            try { sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL); }
            catch (Exception ignored) { stepAvailable = false; }
        }
    }

    private void onLocationChanged(Location loc) {
        if (!recording || paused || loc == null) return;
        float activityAccuracyLimit = isCycling() ? MAX_ACCEPTABLE_ACCURACY_M
                : (isRunning() || isWalkRun() ? 35f : 30f);
        if (loc.hasAccuracy() && loc.getAccuracy() > activityAccuracyLimit) {
            rejectedGpsPoints++;
            resetMaxSpeedCandidate();
            return;
        }

        long now = loc.getTime() > 0 ? loc.getTime() : System.currentTimeMillis();
        accuracyM = loc.hasAccuracy() ? loc.getAccuracy() : Float.NaN;
        if (loc.hasAltitude()) {
            if (Double.isNaN(altitudeM)) altitudeM = loc.getAltitude();
            else if (!loc.hasAccuracy() || loc.getAccuracy() <= 25f) altitudeM = altitudeM * 0.80 + loc.getAltitude() * 0.20;
        }

        if (lastAccepted == null) {
            acceptAnchor(loc, now, 0f, false);
            persistRuntime();
            return;
        }
        if (now <= lastAcceptedTime) {
            rejectedGpsPoints++;
            return;
        }

        long dtMs = now - lastAcceptedTime;
        if (dtMs > GPS_GAP_RESET_MS) {
            gpsGapResets++;
            boolean bridged = bridgeGpsShadow(loc, now, dtMs);
            currentSpeedKmh = 0f;
            resetAutoPending();
            resetMaxSpeedCandidate();
            if (!bridged) rebaseStationaryAnchor(loc, now, true);
            persistRuntime();
            checkGoal();
            return;
        }

        float dtSec = dtMs / 1000f;
        float d = lastAccepted.distanceTo(loc);
        float derivedMps = d / Math.max(0.001f, dtSec);
        float previousAccuracy = lastAccepted.hasAccuracy() ? lastAccepted.getAccuracy() : 0f;
        float combinedAccuracy = Math.max(previousAccuracy, loc.hasAccuracy() ? loc.getAccuracy() : 0f);
        float noiseFloorM = clamp(combinedAccuracy * 0.25f, MIN_NOISE_FLOOR_M, MAX_NOISE_FLOOR_M);
        float reportedMps = loc.hasSpeed() ? Math.max(0f, loc.getSpeed()) : Float.NaN;
        float teleportDistanceM = Math.max(10f, combinedAccuracy * 0.65f);
        float hardMaxSpeedMps = hardMaxSpeedMps();
        float minMovingSpeedMps = minMovingSpeedMps();
        float maxMovingSpeedMps = maxMovingSpeedMps();

        // A physically implausible activity speed is invalid even when the GPS jump is short.
        // The previous distance condition allowed 1-second 3~4m jumps to appear as 13+ km/h walking.
        if (derivedMps > hardMaxSpeedMps
                || (!Float.isNaN(reportedMps) && reportedMps > hardMaxSpeedMps * 1.15f)) {
            rejectedGpsPoints++;
            currentSpeedKmh = 0f;
            resetMaxSpeedCandidate();
            return;
        }

        boolean runningLimits = isRunning() || isWalkRun();
        float mismatchDerivedLimit = isCycling() ? 18.0f : (runningLimits ? 7.0f : 4.5f);
        float mismatchReportedLimit = isCycling() ? 12.0f : (runningLimits ? 5.5f : 3.0f);
        float mismatchDifference = isCycling() ? 5.0f : 2.5f;
        if (!Float.isNaN(reportedMps)
                && derivedMps > mismatchDerivedLimit
                && reportedMps < mismatchReportedLimit
                && derivedMps - reportedMps > mismatchDifference
                && d > teleportDistanceM) {
            rejectedGpsPoints++;
            return;
        }

        if (lastAcceptedSpeedMps > 0.3f && dtSec <= 4.0f) {
            float acceleration = Math.abs(derivedMps - lastAcceptedSpeedMps) / Math.max(0.25f, dtSec);
            if (acceleration > maxAccelerationMps2() && d > Math.max(8f, noiseFloorM * 1.5f)) {
                rejectedGpsPoints++;
                return;
            }
        }

        boolean recentStep = !isCycling() && stepAvailable && lastStepDetectedMs > 0
                && System.currentTimeMillis() - lastStepDetectedMs <= RECENT_STEP_WINDOW_MS;
        float stoppedSpeedLimit = isCycling() ? 0.80f : (isRunning() ? 0.45f : 0.30f);
        boolean deviceSaysStopped = !Float.isNaN(reportedMps) && reportedMps <= stoppedSpeedLimit;
        float stationaryEnvelopeM = Math.max(isCycling() ? 10f : 8f, combinedAccuracy * 1.10f);

        if (deviceSaysStopped && (isCycling() || !recentStep) && d <= stationaryEnvelopeM) {
            stationaryGpsDiscards++;
            currentSpeedKmh = 0f;
            resetAutoPending();
            rebaseStationaryAnchor(loc, now, false);
            persistRuntime();
            return;
        }

        if (d < noiseFloorM) {
            currentSpeedKmh = 0f;
            if (now - lastWrittenTime >= 10_000L) {
                WalkingStore.appendRoute(sessionDir, now, loc.getLatitude(), loc.getLongitude(), accuracyM,
                        Double.isNaN(altitudeM) ? 0 : altitudeM, 0f);
                lastWrittenTime = now;
            }
            persistRuntime();
            return;
        }

        if (derivedMps < minMovingSpeedMps) {
            currentSpeedKmh = 0f;
            if (isWalkRun()) updateAutoMode(0f, now);
            rebaseStationaryAnchor(loc, now, false);
            persistRuntime();
            return;
        }

        float filteredMps = derivedMps;
        if (!Float.isNaN(reportedMps)) filteredMps = derivedMps * 0.65f + reportedMps * 0.35f;
        if (lastAcceptedSpeedMps > 0f) filteredMps = lastAcceptedSpeedMps * 0.25f + filteredMps * 0.75f;

        if (filteredMps > maxMovingSpeedMps) {
            rejectedGpsPoints++;
            currentSpeedKmh = 0f;
            resetMaxSpeedCandidate();
            return;
        }

        if (isWalkRun()) updateAutoMode(filteredMps, now);

        boolean movingPoint = filteredMps >= minMovingSpeedMps && filteredMps <= maxMovingSpeedMps;
        currentSpeedKmh = movingPoint ? Math.max(0f, filteredMps * 3.6f) : 0f;
        if (movingPoint) {
            distanceM += d;
            updateConfirmedMaxSpeed(filteredMps, combinedAccuracy, recentStep, now);
            movingMs += dtMs;
            if (isWalkRun()) {
                if ("running".equals(autoMotionMode)) {
                    runningMovingMs += dtMs;
                    runningDistanceM += d;
                } else {
                    walkingMovingMs += dtMs;
                    walkingDistanceM += d;
                }
            }
        }

        while (distanceM >= nextSplitM) {
            long split = Math.max(0, movingMs - lastSplitMovingMs);
            splitsMs.add(split);
            lastSplitMovingMs = movingMs;
            nextSplitM += 1000;
        }

        acceptAnchor(loc, now, filteredMps, true);
        persistRuntime();
        checkGoal();
    }

    private boolean bridgeGpsShadow(Location loc, long now, long dtMs) {
        if (lastAccepted == null || dtMs <= GPS_GAP_RESET_MS || dtMs > GPS_SHADOW_MAX_MS) return false;

        float d = lastAccepted.distanceTo(loc);
        float dtSec = dtMs / 1000f;
        float averageMps = d / Math.max(0.001f, dtSec);
        float previousAccuracy = lastAccepted.hasAccuracy() ? lastAccepted.getAccuracy() : 0f;
        float combinedAccuracy = Math.max(previousAccuracy, loc.hasAccuracy() ? loc.getAccuracy() : 0f);
        float minSpeed = minMovingSpeedMps();
        float maxSpeed = maxMovingSpeedMps();
        float reportedMps = loc.hasSpeed() ? Math.max(0f, loc.getSpeed()) : Float.NaN;
        boolean recentStep = !isCycling() && stepAvailable && lastStepDetectedMs > 0
                && System.currentTimeMillis() - lastStepDetectedMs <= RECENT_STEP_WINDOW_MS;
        boolean movingBefore = lastAcceptedSpeedMps >= minSpeed * 0.75f;
        boolean movingAfter = !Float.isNaN(reportedMps)
                && reportedMps >= minSpeed * 0.75f && reportedMps <= maxSpeed * 1.10f;
        float minimumBridgeDistance = Math.max(isCycling() ? 15f : 8f, combinedAccuracy * 0.50f);

        // Bridge only plausible movement. A straight line deliberately underestimates curved tunnels,
        // but avoids inventing distance without a paid/external road-matching service.
        if (d < minimumBridgeDistance) return false;
        if (averageMps < minSpeed * 0.45f || averageMps > maxSpeed * 1.05f) return false;
        if (!(movingBefore || movingAfter || recentStep)) return false;

        distanceM += d;
        movingMs += dtMs;
        gpsShadowSegments++;
        gpsShadowDistanceM += d;
        gpsShadowDurationMs += dtMs;

        if (isWalkRun()) {
            if ("running".equals(autoMotionMode)) {
                runningDistanceM += d;
                runningMovingMs += dtMs;
            } else {
                walkingDistanceM += d;
                walkingMovingMs += dtMs;
            }
        }

        while (distanceM >= nextSplitM) {
            long split = Math.max(0, movingMs - lastSplitMovingMs);
            splitsMs.add(split);
            lastSplitMovingMs = movingMs;
            nextSplitM += 1000;
        }

        // Never feed the estimated bridge speed into max-speed confirmation.
        acceptAnchor(loc, now, Math.min(averageMps, maxSpeed), true);
        return true;
    }

    private void updateAutoMode(float filteredMps, long now) {
        if (!isWalkRun()) return;
        String desired = autoMotionMode;
        if ("walking".equals(autoMotionMode) && filteredMps >= AUTO_RUN_ENTER_MPS) desired = "running";
        else if ("running".equals(autoMotionMode) && filteredMps <= AUTO_WALK_RETURN_MPS) desired = "walking";
        else {
            resetAutoPending();
            return;
        }

        if (!desired.equals(pendingAutoMotionMode) || pendingAutoMotionSince <= 0) {
            pendingAutoMotionMode = desired;
            pendingAutoMotionSince = now;
            return;
        }
        long confirm = "running".equals(desired) ? AUTO_RUN_CONFIRM_MS : AUTO_WALK_CONFIRM_MS;
        if (now - pendingAutoMotionSince >= confirm) {
            autoMotionMode = desired;
            autoModeSwitches++;
            resetAutoPending();
        }
    }

    private void resetAutoPending() {
        pendingAutoMotionMode = autoMotionMode;
        pendingAutoMotionSince = 0;
    }

    private void updateConfirmedMaxSpeed(float speedMps, float combinedAccuracy, boolean recentStep, long now) {
        float speedKmh = Math.max(0f, speedMps * 3.6f);
        if (speedKmh <= maxSpeedKmh + 0.1f) {
            resetMaxSpeedCandidate();
            return;
        }
        float accuracyLimit = isCycling() ? 35f : (isRunning() || isWalkRun() ? 25f : 20f);
        if (combinedAccuracy > accuracyLimit) {
            resetMaxSpeedCandidate();
            return;
        }
        if (!isCycling() && stepAvailable && !recentStep) {
            resetMaxSpeedCandidate();
            return;
        }
        float allowedKmh = maxMovingSpeedMps() * 3.6f;
        if (speedKmh > allowedKmh) {
            resetMaxSpeedCandidate();
            return;
        }

        float tolerance = isCycling() ? 5.0f : (isRunning() || isWalkRun() ? 2.5f : 1.5f);
        if (maxSpeedCandidateSamples == 0
                || now - maxSpeedCandidateStartedAt > 5_000L
                || Math.abs(speedKmh - maxSpeedCandidateKmh) > tolerance) {
            maxSpeedCandidateKmh = speedKmh;
            maxSpeedCandidateSamples = 1;
            maxSpeedCandidateStartedAt = now;
            return;
        }

        maxSpeedCandidateKmh = (maxSpeedCandidateKmh * maxSpeedCandidateSamples + speedKmh)
                / (maxSpeedCandidateSamples + 1);
        maxSpeedCandidateSamples++;
        int requiredSamples = isWalkingOnly() ? 3 : 2;
        if (maxSpeedCandidateSamples >= requiredSamples) {
            maxSpeedKmh = Math.max(maxSpeedKmh, maxSpeedCandidateKmh);
            resetMaxSpeedCandidate();
        }
    }

    private void resetMaxSpeedCandidate() {
        maxSpeedCandidateKmh = 0f;
        maxSpeedCandidateSamples = 0;
        maxSpeedCandidateStartedAt = 0L;
    }

    private boolean isWalkingOnly() { return "walking".equals(activityType); }

    private boolean isRunning() { return "running".equals(activityType); }
    private boolean isCycling() { return "cycling".equals(activityType); }
    private boolean isWalkRun() { return "walkrun".equals(activityType); }

    private String activityLabel() {
        if (isCycling()) return "자전거";
        if (isWalkRun()) return "걷기/러닝 통합";
        return isRunning() ? "러닝" : "걷기";
    }

    private float hardMaxSpeedMps() {
        if (isCycling()) return 25.0f;
        if (isRunning() || isWalkRun()) return 7.0f;
        return 3.4f; // walking: about 12.2 km/h; anything faster is treated as a GPS/mode mismatch
    }

    private float minMovingSpeedMps() {
        if (isCycling()) return 0.80f;
        if (isRunning()) return 0.60f;
        return 0.35f;
    }

    private float maxMovingSpeedMps() {
        if (isCycling()) return 22.2f;
        if (isRunning() || isWalkRun()) return 6.5f;
        return 3.2f; // walking: about 11.5 km/h
    }

    private float maxAccelerationMps2() {
        if (isCycling()) return 7.0f;
        if (isRunning() || isWalkRun()) return 4.5f;
        return 2.8f;
    }

    private void acceptAnchor(Location loc, long now, float speedMps, boolean writeMovingPoint) {
        lastAccepted = new Location(loc);
        lastAcceptedTime = now;
        lastAcceptedSpeedMps = Math.max(0f, speedMps);
        WalkingStore.appendRoute(sessionDir, now, loc.getLatitude(), loc.getLongitude(), accuracyM,
                Double.isNaN(altitudeM) ? 0 : altitudeM, writeMovingPoint ? speedMps : 0f);
        lastWrittenTime = now;
    }

    private void rebaseStationaryAnchor(Location loc, long now, boolean forceWrite) {
        lastAccepted = new Location(loc);
        lastAcceptedTime = now;
        lastAcceptedSpeedMps = 0f;
        if (forceWrite || now - lastWrittenTime >= 10_000L) {
            WalkingStore.appendRoute(sessionDir, now, loc.getLatitude(), loc.getLongitude(), accuracyM,
                    Double.isNaN(altitudeM) ? 0 : altitudeM, 0f);
            lastWrittenTime = now;
        }
    }

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }

    @Override public void onSensorChanged(SensorEvent event) {
        if (isCycling() || !recording || paused || event == null || event.sensor == null || event.sensor.getType() != Sensor.TYPE_STEP_COUNTER) return;
        float current = event.values.length > 0 ? event.values[0] : 0f;
        if (stepBase < 0) stepBase = current - steps;
        long previousSteps = steps;
        steps = Math.max(0, Math.round(current - stepBase));
        if (steps > previousSteps) lastStepDetectedMs = System.currentTimeMillis();
        persistRuntime();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void pauseRecording() {
        if (!recording || paused) return;
        paused = true;
        pauseStartedMs = System.currentTimeMillis();
        currentSpeedKmh = 0;
        resetAutoPending();
        resetMaxSpeedCandidate();
        persistRuntime();
        updateForegroundNotification();
    }

    private void resumeRecording() {
        if (!recording || !paused) return;
        long now = System.currentTimeMillis();
        pausedAccumMs += Math.max(0, now - pauseStartedMs);
        pauseStartedMs = 0;
        paused = false;
        lastAccepted = null;
        lastAcceptedTime = 0;
        lastAcceptedSpeedMps = 0f;
        lastStepDetectedMs = 0;
        stepBase = -1f;
        currentSpeedKmh = 0;
        resetAutoPending();
        resetMaxSpeedCandidate();
        persistRuntime();
        updateForegroundNotification();
    }

    private long elapsedMs() {
        if (!recording || startMs <= 0) return 0;
        long now = System.currentTimeMillis();
        long pausedNow = paused && pauseStartedMs > 0 ? now - pauseStartedMs : 0;
        return Math.max(0, now - startMs - pausedAccumMs - pausedNow);
    }

    private void checkGoal() {
        if (!recording || goalNotified || "ERROR".equals(goalState)) return;
        long elapsed = elapsedMs();
        if (goalDistanceM > 0 && distanceM >= goalDistanceM) {
            if (goalTimeMs <= 0 || elapsed <= goalTimeMs) {
                goalState = "SUCCESS";
                goalNotified = true;
                notifyGoal(activityLabel() + " 목표 달성", String.format(Locale.KOREAN, "%.2f km 목표를 달성했습니다.", distanceM / 1000.0));
            }
        } else if (goalDistanceM == 0 && goalTimeMs > 0 && elapsed >= goalTimeMs) {
            goalState = "SUCCESS";
            goalNotified = true;
            notifyGoal(activityLabel() + " 목표 달성", "설정한 활동 시간을 완료했습니다.");
        } else if (goalDistanceM > 0 && goalTimeMs > 0 && elapsed > goalTimeMs) {
            goalState = "TIMEOUT";
            goalNotified = true;
            notifyGoal("목표 시간 종료", String.format(Locale.KOREAN, "현재 %.2f km / 목표 %.2f km", distanceM / 1000.0, goalDistanceM / 1000.0));
        }
        persistRuntime();
    }

    private void notifyGoal(String title, String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        Intent open = new Intent(this, ExerciseActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 5102, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_GOAL) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle(title).setContentText(text).setContentIntent(pi).setAutoCancel(true);
        nm.notify(NOTIFY_GOAL, b.build());
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) {
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(new long[]{0, 250, 120, 250}, -1));
                else v.vibrate(new long[]{0, 250, 120, 250}, -1);
            }
        } catch (Exception ignored) {}
    }

    private void persistRuntime() {
        if (runtime == null) return;
        runtime.edit()
                .putBoolean(KEY_RECORDING, recording)
                .putBoolean(KEY_PAUSED, paused)
                .putString(KEY_ACTIVITY_TYPE, activityType)
                .putString(KEY_AUTO_MOTION_MODE, autoMotionMode)
                .putLong(KEY_START_MS, startMs)
                .putLong(KEY_ELAPSED_MS, elapsedMs())
                .putLong(KEY_MOVING_MS, movingMs)
                .putLong(KEY_DISTANCE_M, Math.round(distanceM))
                .putLong(KEY_WALKING_DISTANCE_M, Math.round(walkingDistanceM))
                .putLong(KEY_RUNNING_DISTANCE_M, Math.round(runningDistanceM))
                .putLong(KEY_WALKING_MOVING_MS, walkingMovingMs)
                .putLong(KEY_RUNNING_MOVING_MS, runningMovingMs)
                .putLong(KEY_STEPS, steps)
                .putBoolean(KEY_STEP_AVAILABLE, stepAvailable)
                .putFloat(KEY_CURRENT_SPEED_KMH, currentSpeedKmh)
                .putFloat(KEY_MAX_SPEED_KMH, maxSpeedKmh)
                .putFloat(KEY_ALTITUDE_M, Double.isNaN(altitudeM) ? Float.NaN : (float) altitudeM)
                .putFloat(KEY_ACCURACY_M, accuracyM)
                .putString(KEY_SESSION_DIR, sessionDir == null ? "" : sessionDir.getAbsolutePath())
                .putLong(KEY_GOAL_DISTANCE_M, goalDistanceM)
                .putLong(KEY_GOAL_TIME_MS, goalTimeMs)
                .putString(KEY_GOAL_STATE, goalState)
                .putLong(KEY_PAUSED_ACCUM_MS, pausedAccumMs)
                .putLong(KEY_PAUSE_STARTED_MS, pauseStartedMs)
                .putLong(KEY_PERSISTED_AT_MS, System.currentTimeMillis())
                .putString(KEY_SPLITS_JSON, WalkingStore.longListToJson(splitsMs).toString())
                .apply();
    }

    private void writeMeta(String status, long endMs) {
        if (sessionDir == null) return;
        try {
            JSONObject m = new JSONObject();
            m.put("type", activityType);
            m.put("status", status);
            m.put("startEpochMs", startMs);
            if (endMs > 0) m.put("endEpochMs", endMs);
            m.put("durationMs", elapsedMs());
            m.put("movingMs", movingMs);
            m.put("distanceM", Math.round(distanceM));
            m.put("steps", isCycling() ? 0 : steps);
            m.put("stepSensorAvailable", !isCycling() && stepAvailable);
            m.put("maxSpeedKmh", maxSpeedKmh);
            m.put("lastAltitudeM", Double.isNaN(altitudeM) ? JSONObject.NULL : altitudeM);
            m.put("goalDistanceM", goalDistanceM);
            m.put("goalTimeMs", goalTimeMs);
            m.put("goalState", goalState);
            m.put("splitsMs", WalkingStore.longListToJson(splitsMs));
            m.put("locationStorage", "local_only");
            m.put("gpsFilter", "local_" + activityType + "_v6_shadow");
            m.put("rejectedGpsPoints", rejectedGpsPoints);
            m.put("gpsGapResets", gpsGapResets);
            m.put("gpsShadowSegments", gpsShadowSegments);
            m.put("gpsShadowDistanceM", Math.round(gpsShadowDistanceM));
            m.put("gpsShadowDurationMs", gpsShadowDurationMs);
            m.put("stationaryGpsDiscards", stationaryGpsDiscards);
            if (isWalkRun()) {
                m.put("autoMotionModeLast", autoMotionMode);
                m.put("autoModeSwitches", autoModeSwitches);
                m.put("walkingDistanceM", Math.round(walkingDistanceM));
                m.put("runningDistanceM", Math.round(runningDistanceM));
                m.put("walkingMovingMs", walkingMovingMs);
                m.put("runningMovingMs", runningMovingMs);
                m.put("autoRunEnterKmh", AUTO_RUN_ENTER_MPS * 3.6f);
                m.put("autoWalkReturnKmh", AUTO_WALK_RETURN_MPS * 3.6f);
            }
            WalkingStore.writeMeta(sessionDir, m);
        } catch (Exception ignored) {}
    }

    private void finishRecording() {
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
    }

    private void stopLocationOnly() {
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
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel rec = new NotificationChannel(CHANNEL_RECORDING, "활동 기록", NotificationManager.IMPORTANCE_LOW);
        rec.setDescription("화면이 꺼진 동안에도 사용자가 시작한 걷기/러닝/통합/자전거 GPS 기록을 유지합니다.");
        nm.createNotificationChannel(rec);
        NotificationChannel goal = new NotificationChannel(CHANNEL_GOAL, "활동 목표", NotificationManager.IMPORTANCE_HIGH);
        goal.setDescription("사용자가 설정한 활동 목표 달성 또는 목표 시간 종료를 알려줍니다.");
        goal.enableVibration(true);
        nm.createNotificationChannel(goal);
    }

    private Notification buildRecordingNotification(String message) {
        Intent open = new Intent(this, ExerciseActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 5101, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, WalkingRecorderService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 5103, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_RECORDING) : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(paused ? activityLabel() + " 기록 일시정지" : activityLabel() + " 기록 중")
                .setContentText(message)
                .setContentIntent(openPi)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "기록 종료", stopPi).build())
                .build();
    }

    private void updateForegroundNotification() {
        if (!recording) return;
        String text;
        if (isCycling()) {
            text = String.format(Locale.KOREAN, "%.2f km · %s · %.1f km/h", distanceM / 1000.0, formatClock(elapsedMs()), currentSpeedKmh);
        } else if (isWalkRun()) {
            text = String.format(Locale.KOREAN, "%s · %.2f km · %s", "running".equals(autoMotionMode) ? "현재 러닝" : "현재 걷기", distanceM / 1000.0, formatClock(elapsedMs()));
        } else {
            text = String.format(Locale.KOREAN, "%.2f km · %s · %d걸음", distanceM / 1000.0, formatClock(elapsedMs()), steps);
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFY_RECORDING, buildRecordingNotification(text));
    }

    private static String formatClock(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        return h > 0 ? String.format(Locale.KOREAN, "%d:%02d:%02d", h, m, sec) : String.format(Locale.KOREAN, "%02d:%02d", m, sec);
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(ticker);
        stopSensors();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
