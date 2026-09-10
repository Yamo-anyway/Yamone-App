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
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
import java.util.Locale;
import java.util.UUID;

/**
 * Local-first ski/snowboard GPS recorder and heuristic motion detector.
 * Full routes stay on-device. Only lift observations may be sent when realtime exchange is ON.
 */
public class SkiRecorderService extends Service {
    public static final String ACTION_START = "com.yamo.snorelab.SKI_START";
    public static final String ACTION_STOP = "com.yamo.snorelab.SKI_STOP";
    public static final String PREFS = "yamone_ski_runtime_v1";

    public static final String KEY_RECORDING = "recording";
    public static final String KEY_SPORT = "sport";
    public static final String KEY_START_MS = "start_ms";
    public static final String KEY_SESSION_DIR = "session_dir";
    public static final String KEY_STATE = "state";
    public static final String KEY_SPEED_KMH = "speed_kmh";
    public static final String KEY_MAX_SPEED_KMH = "max_speed_kmh";
    public static final String KEY_ALTITUDE_M = "altitude_m";
    public static final String KEY_ACCURACY_M = "accuracy_m";
    public static final String KEY_LAT = "lat";
    public static final String KEY_LON = "lon";
    public static final String KEY_DESCENT_COUNT = "descent_count";
    public static final String KEY_LIFT_COUNT = "lift_count";
    public static final String KEY_DESCENT_DISTANCE_M = "descent_distance_m";
    public static final String KEY_DESCENT_VERTICAL_M = "descent_vertical_m";
    public static final String KEY_LIFT_TIME_MS = "lift_time_ms";
    public static final String KEY_WAIT_TIME_MS = "wait_time_ms";

    public static final String STATE_STOPPED = "STOPPED";
    public static final String STATE_DESCENT = "DESCENT";
    public static final String STATE_LIFT = "LIFT";
    public static final String STATE_CHECKING = "CHECKING";
    public static final String DETECTOR_VERSION = "ski-heuristic-0.2";

    private static final String CHANNEL = "ski_recording_v1";
    private static final int NOTIFY_ID = 5401;
    private static final float MAX_ACCURACY_M = 50f;
    private static final float HARD_MAX_SPEED_MPS = 45f;
    private static final long GPS_GAP_RESET_MS = 20_000L;
    private static final long RESORT_DETECT_RETRY_MS = 5 * 60_000L;

    private SharedPreferences runtime;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean recording;
    private String sport = "ski";
    private long startMs;
    private File sessionDir;
    private String state = STATE_CHECKING;
    private Location lastLoc;
    private long lastTime;
    private double smoothedAltitude = Double.NaN;
    private float accuracyM = Float.NaN;
    private float currentSpeedKmh;
    private float maxSpeedKmh;
    private double lastLat = Double.NaN;
    private double lastLon = Double.NaN;
    private int rejectedGpsPoints;
    private boolean gpsRegistered;
    private boolean networkRegistered;
    private long lastProviderRefreshAt;

    private int descentCount;
    private int liftCount;
    private double descentDistanceM;
    private double descentVerticalM;
    private long liftTimeMs;
    private long waitTimeMs;

    private long slowStartMs;
    private Location slowAnchor;
    private long waitCandidateStartMs;
    private Location waitCandidateAnchor;

    private Candidate liftCandidate;
    private Candidate descentCandidate;

    private long activeLiftStartMs;
    private long activeLiftWaitStartMs;
    private Location activeLiftLower;
    private double activeLiftLowerAlt;
    private double activeLiftMaxAlt;
    private double activeLiftPathM;
    private long activeLiftLastRiseMs;
    private Location activeLiftLastLoc;

    private long lastResortDetectAttemptMs;
    private boolean resortDetectInFlight;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!recording) return;
            if (System.currentTimeMillis() - lastProviderRefreshAt >= 30_000L) refreshLocationProviderRegistrations();
            persistRuntime();
            persistSessionMetrics();
            updateNotification();
            handler.postDelayed(this, 3000L);
        }
    };

    private static final class Candidate {
        final long startMs;
        final Location start;
        final double startAlt;
        double pathM;

        Candidate(long startMs, Location start, double startAlt) {
            this.startMs = startMs;
            this.start = new Location(start);
            this.startAlt = startAlt;
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        runtime = getSharedPreferences(PREFS, MODE_PRIVATE);
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            if (runtime.getBoolean(KEY_RECORDING, false)) recoverRecordingAfterProcessRestart();
            else stopSelf();
        } else if (ACTION_START.equals(action)) begin(intent);
        else if (ACTION_STOP.equals(action)) finishRecording();
        return recording ? START_STICKY : START_NOT_STICKY;
    }

    private void begin(Intent intent) {
        if (recording || runtime.getBoolean(KEY_RECORDING, false)) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            clearRuntime();
            stopSelf();
            return;
        }

        if (SkiResortStore.detectedAt(this) > 0
                && System.currentTimeMillis() - SkiResortStore.detectedAt(this) > 12 * 60 * 60_000L) {
            SkiResortStore.clearCurrent(this);
        }

        sport = "snowboard".equals(intent.getStringExtra("sport")) ? "snowboard" : "ski";
        startMs = System.currentTimeMillis();
        sessionDir = SkiLiftStore.createSession(this, startMs, sport);
        if (sessionDir == null || !sessionDir.exists()) {
            clearRuntime();
            stopSelf();
            return;
        }

        recording = true;
        state = STATE_CHECKING;
        Notification n = buildNotification("GPS 상태를 확인하고 있어요");
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFY_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        else startForeground(NOTIFY_ID, n);

        resetDetector();
        startLocation();
        persistRuntime();
        persistSessionMetrics();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void resetDetector() {
        lastLoc = null;
        lastTime = 0;
        smoothedAltitude = Double.NaN;
        accuracyM = Float.NaN;
        currentSpeedKmh = 0;
        maxSpeedKmh = 0;
        lastLat = Double.NaN;
        lastLon = Double.NaN;
        rejectedGpsPoints = 0;
        descentCount = 0;
        liftCount = 0;
        descentDistanceM = 0;
        descentVerticalM = 0;
        liftTimeMs = 0;
        waitTimeMs = 0;
        slowStartMs = 0;
        slowAnchor = null;
        waitCandidateStartMs = 0;
        waitCandidateAnchor = null;
        liftCandidate = null;
        descentCandidate = null;
        lastResortDetectAttemptMs = 0;
        resortDetectInFlight = false;
        clearActiveLift();
    }

    private void startLocation() {
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
    }

    private void onLocationChanged(Location loc) {
        if (!recording || loc == null) return;
        if (loc.hasAccuracy() && loc.getAccuracy() > MAX_ACCURACY_M) {
            rejectedGpsPoints++;
            return;
        }

        long now = loc.getTime() > 0 ? loc.getTime() : System.currentTimeMillis();
        accuracyM = loc.hasAccuracy() ? loc.getAccuracy() : Float.NaN;
        lastLat = loc.getLatitude();
        lastLon = loc.getLongitude();

        double previousSmoothedAltitude = smoothedAltitude;
        double rawAlt = loc.hasAltitude() ? loc.getAltitude() : smoothedAltitude;
        if (Double.isNaN(smoothedAltitude) && !Double.isNaN(rawAlt)) smoothedAltitude = rawAlt;
        else if (!Double.isNaN(rawAlt)) smoothedAltitude = smoothedAltitude * 0.75 + rawAlt * 0.25;

        maybeDetectResort(loc);

        if (lastLoc == null || lastTime <= 0) {
            lastLoc = new Location(loc);
            lastTime = now;
            SkiLiftStore.appendRoute(sessionDir, now, loc.getLatitude(), loc.getLongitude(), accuracyM,
                    safeAltitude(), 0f, state);
            persistRuntime();
            return;
        }
        if (now <= lastTime) return;
        long dtMs = now - lastTime;
        if (dtMs > GPS_GAP_RESET_MS) {
            lastLoc = new Location(loc);
            lastTime = now;
            liftCandidate = null;
            descentCandidate = null;
            currentSpeedKmh = 0;
            state = STATE_CHECKING;
            persistRuntime();
            return;
        }

        float distanceM = lastLoc.distanceTo(loc);
        float dtSec = dtMs / 1000f;
        float derivedMps = distanceM / Math.max(0.1f, dtSec);
        float reportedMps = loc.hasSpeed() ? Math.max(0, loc.getSpeed()) : derivedMps;
        float speedMps = derivedMps * 0.65f + reportedMps * 0.35f;
        if (speedMps > HARD_MAX_SPEED_MPS && distanceM > Math.max(25f, accuracyM * 1.2f)) {
            rejectedGpsPoints++;
            return;
        }

        double currentAlt = safeAltitude();
        double altDelta = !Double.isNaN(previousSmoothedAltitude) ? currentAlt - previousSmoothedAltitude : 0;
        currentSpeedKmh = Math.max(0, speedMps * 3.6f);
        if (currentSpeedKmh <= HARD_MAX_SPEED_MPS * 3.6f) maxSpeedKmh = Math.max(maxSpeedKmh, currentSpeedKmh);

        updateWaitCandidate(loc, now, speedMps);
        updateMotionCandidates(loc, now, speedMps, distanceM, currentAlt);

        if (STATE_LIFT.equals(state)) {
            updateActiveLift(loc, now, distanceM, currentAlt, altDelta, speedMps);
        } else {
            if (isLiftCandidateConfirmed(loc, now, currentAlt)) {
                enterLift(loc, now, currentAlt);
            } else if (isDescentCandidateConfirmed(now, currentAlt)) {
                enterDescent();
            } else {
                updateStoppedState(now, speedMps);
            }
        }

        if (STATE_DESCENT.equals(state)) {
            if (distanceM > 0 && speedMps >= 1.5f) descentDistanceM += distanceM;
            if (altDelta < -0.3) descentVerticalM += -altDelta;
        }

        SkiLiftStore.appendRoute(sessionDir, now, loc.getLatitude(), loc.getLongitude(), accuracyM,
                currentAlt, speedMps, state);
        lastLoc = new Location(loc);
        lastTime = now;
        persistRuntime();
    }

    private void maybeDetectResort(Location loc) {
        if (!SkiLiftStore.isRealtimeExchangeEnabled(this)) return;
        if (!SkiResortStore.currentKey(this).isEmpty()) return;
        long now = System.currentTimeMillis();
        if (resortDetectInFlight || now - lastResortDetectAttemptMs < RESORT_DETECT_RETRY_MS) return;
        lastResortDetectAttemptMs = now;
        resortDetectInFlight = true;
        SkiLiftApi.detectResort(this, loc.getLatitude(), loc.getLongitude(), new SkiLiftApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                resortDetectInFlight = false;
                if (data.optBoolean("found", false)) {
                    SkiResortStore.setCurrent(SkiRecorderService.this,
                            data.optString("resort_key", ""), data.optString("resort_name", ""));
                }
            }
            @Override public void onFailure(String message) { resortDetectInFlight = false; }
        });
    }

    private double safeAltitude() {
        return Double.isNaN(smoothedAltitude) ? 0 : smoothedAltitude;
    }

    private void updateWaitCandidate(Location loc, long now, float speedMps) {
        if (STATE_DESCENT.equals(state)) {
            waitCandidateStartMs = 0;
            waitCandidateAnchor = null;
            return;
        }
        if (speedMps <= 2.2f) {
            if (waitCandidateStartMs <= 0) {
                waitCandidateStartMs = now;
                waitCandidateAnchor = new Location(loc);
            }
        } else if (speedMps > 5.0f) {
            waitCandidateStartMs = 0;
            waitCandidateAnchor = null;
        }

        if (speedMps < 0.8f) {
            if (slowStartMs <= 0) {
                slowStartMs = now;
                slowAnchor = new Location(loc);
            }
        } else {
            slowStartMs = 0;
            slowAnchor = null;
        }
    }

    private void updateMotionCandidates(Location loc, long now, float speedMps, float distanceM, double currentAlt) {
        if (!STATE_LIFT.equals(state)) {
            if (speedMps >= 0.5f && speedMps <= 10.0f) {
                if (liftCandidate == null) liftCandidate = new Candidate(now, loc, currentAlt);
                liftCandidate.pathM += Math.max(0, distanceM);
                if (currentAlt < liftCandidate.startAlt - 4 || speedMps > 12f) liftCandidate = null;
            } else if (speedMps > 12f) {
                liftCandidate = null;
            }
        }

        if (speedMps >= 2.0f) {
            if (descentCandidate == null) descentCandidate = new Candidate(now, loc, currentAlt);
            descentCandidate.pathM += Math.max(0, distanceM);
            if (currentAlt > descentCandidate.startAlt + 5) descentCandidate = null;
        } else if (speedMps < 1.0f) {
            descentCandidate = null;
        }
    }

    private boolean isLiftCandidateConfirmed(Location loc, long now, double currentAlt) {
        if (liftCandidate == null) return false;
        long duration = now - liftCandidate.startMs;
        double ascent = currentAlt - liftCandidate.startAlt;
        float straight = liftCandidate.start.distanceTo(loc);
        double straightness = liftCandidate.pathM <= 1 ? 0 : straight / liftCandidate.pathM;
        return duration >= 12_000L && ascent >= 8.0 && liftCandidate.pathM >= 15.0 && straightness >= 0.70;
    }

    private boolean isDescentCandidateConfirmed(long now, double currentAlt) {
        if (descentCandidate == null) return false;
        long duration = now - descentCandidate.startMs;
        double drop = descentCandidate.startAlt - currentAlt;
        return duration >= 6_000L && drop >= 8.0 && descentCandidate.pathM >= 20.0;
    }

    private void updateStoppedState(long now, float speedMps) {
        if (speedMps < 0.8f && slowStartMs > 0 && now - slowStartMs >= 10_000L) {
            state = STATE_STOPPED;
        } else if (!STATE_DESCENT.equals(state)) {
            state = STATE_CHECKING;
        }
    }

    private void enterDescent() {
        if (!STATE_DESCENT.equals(state)) descentCount++;
        state = STATE_DESCENT;
        liftCandidate = null;
        descentCandidate = null;
        waitCandidateStartMs = 0;
        waitCandidateAnchor = null;
    }

    private void enterLift(Location loc, long now, double currentAlt) {
        state = STATE_LIFT;
        activeLiftStartMs = liftCandidate == null ? now : liftCandidate.startMs;
        activeLiftLower = liftCandidate == null ? new Location(loc) : new Location(liftCandidate.start);
        activeLiftLowerAlt = liftCandidate == null ? currentAlt : liftCandidate.startAlt;
        activeLiftMaxAlt = currentAlt;
        activeLiftPathM = liftCandidate == null ? 0 : liftCandidate.pathM;
        activeLiftLastRiseMs = now;
        activeLiftLastLoc = new Location(loc);

        activeLiftWaitStartMs = activeLiftStartMs;
        if (waitCandidateStartMs > 0 && waitCandidateAnchor != null && activeLiftLower != null) {
            long waitAge = activeLiftStartMs - waitCandidateStartMs;
            float baseDistance = waitCandidateAnchor.distanceTo(activeLiftLower);
            if (waitAge >= 10_000L && waitAge <= 20 * 60_000L && baseDistance <= 150f) {
                activeLiftWaitStartMs = waitCandidateStartMs;
            }
        }
        liftCandidate = null;
        descentCandidate = null;
    }

    private void updateActiveLift(Location loc, long now, float segmentDistanceM, double currentAlt,
                                  double altDelta, float speedMps) {
        activeLiftPathM += Math.max(0, segmentDistanceM);
        if (currentAlt > activeLiftMaxAlt + 0.4) {
            activeLiftMaxAlt = currentAlt;
            activeLiftLastRiseMs = now;
        } else if (altDelta > 0.25) {
            activeLiftLastRiseMs = now;
        }
        activeLiftLastLoc = new Location(loc);

        boolean clearDescent = isDescentCandidateConfirmed(now, currentAlt);
        boolean noRise = now - activeLiftLastRiseMs >= 18_000L;
        boolean stoppedAtTop = speedMps < 1.0f && noRise;
        boolean movingAway = clearDescent || (noRise && currentAlt < activeLiftMaxAlt - 3.0);
        if (stoppedAtTop || movingAway) {
            finishActiveLift(loc, now, currentAlt);
            if (clearDescent) enterDescent();
            else state = STATE_CHECKING;
        }
    }

    private void finishActiveLift(Location upper, long endMs, double upperAlt) {
        if (activeLiftStartMs <= 0 || activeLiftLower == null) {
            clearActiveLift();
            return;
        }
        long rideDuration = Math.max(0, endMs - activeLiftStartMs);
        double ascent = Math.max(0, activeLiftMaxAlt - activeLiftLowerAlt);
        if (rideDuration < 15_000L || ascent < 10.0) {
            clearActiveLift();
            return;
        }

        float straightDistance = activeLiftLower.distanceTo(upper);
        double straightness = activeLiftPathM <= 1 ? 0 : Math.max(0, Math.min(1, straightDistance / activeLiftPathM));
        long waitDuration = Math.max(0, activeLiftStartMs - activeLiftWaitStartMs);
        if (waitDuration < 10_000L) waitDuration = 0;
        double confidence = 0.55;
        if (ascent >= 50) confidence += 0.15;
        if (rideDuration >= 60_000L) confidence += 0.10;
        if (straightness >= 0.80) confidence += 0.15;
        confidence = Math.min(0.98, confidence);

        JSONObject item = new JSONObject();
        try {
            item.put("observationId", UUID.randomUUID().toString());
            item.put("waitStartEpochMs", waitDuration > 0 ? activeLiftWaitStartMs : activeLiftStartMs);
            item.put("rideStartEpochMs", activeLiftStartMs);
            item.put("rideEndEpochMs", endMs);
            item.put("waitDurationMs", waitDuration);
            item.put("rideDurationMs", rideDuration);
            item.put("lowerLat", activeLiftLower.getLatitude());
            item.put("lowerLon", activeLiftLower.getLongitude());
            item.put("upperLat", upper.getLatitude());
            item.put("upperLon", upper.getLongitude());
            item.put("lowerAltitudeM", activeLiftLowerAlt);
            item.put("upperAltitudeM", upperAlt);
            item.put("ascentM", ascent);
            item.put("pathDistanceM", activeLiftPathM);
            item.put("straightness", straightness);
            item.put("confidence", confidence);
            item.put("nameStatus", "unknown");
            item.put("detectorVersion", DETECTOR_VERSION);

            String resortKey = SkiResortStore.currentKey(this);
            String resortName = SkiResortStore.currentName(this);
            item.put("resortKey", resortKey);
            item.put("resortName", resortName);
            JSONObject cachedLift = SkiResortStore.resolveCachedLift(this, item);
            if (cachedLift != null) {
                item.put("liftKey", cachedLift.optString("lift_key", ""));
                item.put("liftName", cachedLift.optString("lift_name", ""));
                item.put("nameStatus", "verified");
            }

            String id = SkiLiftStore.appendLiftObservation(sessionDir, item);
            if (!id.isEmpty()) {
                liftCount++;
                liftTimeMs += rideDuration;
                waitTimeMs += waitDuration;
                item.put("observationId", id);
                if (SkiLiftStore.isRealtimeExchangeEnabled(this) && !resortKey.isEmpty()) {
                    SkiLiftApi.submitRealtime(this, resortKey, item, new SkiLiftApi.JsonCallback() {
                        @Override public void onSuccess(JSONObject data) {}
                        @Override public void onFailure(String message) {}
                    });
                }
            }
        } catch (Exception ignored) {}
        clearActiveLift();
    }

    private void clearActiveLift() {
        activeLiftStartMs = 0;
        activeLiftWaitStartMs = 0;
        activeLiftLower = null;
        activeLiftLowerAlt = 0;
        activeLiftMaxAlt = 0;
        activeLiftPathM = 0;
        activeLiftLastRiseMs = 0;
        activeLiftLastLoc = null;
    }

    private void recoverRecordingAfterProcessRestart() {
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

    private void finishRecording() {
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
        long now = System.currentTimeMillis();
        if (STATE_LIFT.equals(state) && activeLiftLastLoc != null) {
            finishActiveLift(activeLiftLastLoc, now, activeLiftMaxAlt);
        }
        persistSessionMetrics();
        if (sessionDir != null) {
            SkiLiftStore.completeSession(sessionDir, now,
                    SkiResortStore.currentKey(this), SkiResortStore.currentName(this));
        }
        recording = false;
        stopLocation();
        handler.removeCallbacks(ticker);
        clearRuntime();
        try { stopForeground(true); } catch (Exception ignored) {}
        stopSelf();
    }

    private void stopLocation() {
        if (locationManager != null && locationListener != null) {
            try { locationManager.removeUpdates(locationListener); } catch (Exception ignored) {}
        }
        locationListener = null;
        locationManager = null;
        gpsRegistered = false;
        networkRegistered = false;
    }

    private void persistRuntime() {
        if (runtime == null) return;
        SharedPreferences.Editor editor = runtime.edit()
                .putBoolean(KEY_RECORDING, recording)
                .putString(KEY_SPORT, sport)
                .putLong(KEY_START_MS, startMs)
                .putString(KEY_SESSION_DIR, sessionDir == null ? "" : sessionDir.getAbsolutePath())
                .putString(KEY_STATE, state)
                .putFloat(KEY_SPEED_KMH, currentSpeedKmh)
                .putFloat(KEY_MAX_SPEED_KMH, maxSpeedKmh)
                .putFloat(KEY_ALTITUDE_M, (float) safeAltitude())
                .putFloat(KEY_ACCURACY_M, accuracyM)
                .putInt(KEY_DESCENT_COUNT, descentCount)
                .putInt(KEY_LIFT_COUNT, liftCount)
                .putLong(KEY_DESCENT_DISTANCE_M, Math.round(descentDistanceM))
                .putLong(KEY_DESCENT_VERTICAL_M, Math.round(descentVerticalM))
                .putLong(KEY_LIFT_TIME_MS, liftTimeMs)
                .putLong(KEY_WAIT_TIME_MS, waitTimeMs);
        if (Double.isFinite(lastLat) && Double.isFinite(lastLon)) {
            editor.putFloat(KEY_LAT, (float) lastLat).putFloat(KEY_LON, (float) lastLon);
        }
        editor.apply();
    }

    private void persistSessionMetrics() {
        if (sessionDir == null) return;
        JSONObject m = new JSONObject();
        try {
            m.put("durationMs", Math.max(0, System.currentTimeMillis() - startMs));
            m.put("currentState", state);
            m.put("currentSpeedKmh", currentSpeedKmh);
            m.put("maxSpeedKmh", maxSpeedKmh);
            m.put("lastAltitudeM", safeAltitude());
            m.put("gpsAccuracyM", Float.isNaN(accuracyM) ? JSONObject.NULL : accuracyM);
            m.put("descentCount", descentCount);
            m.put("liftCount", liftCount);
            m.put("descentDistanceM", Math.round(descentDistanceM));
            m.put("descentVerticalM", Math.round(descentVerticalM));
            m.put("liftTimeMs", liftTimeMs);
            m.put("waitTimeMs", waitTimeMs);
            m.put("rejectedGpsPoints", rejectedGpsPoints);
            m.put("detectorVersion", DETECTOR_VERSION);
            SkiLiftStore.updateSessionMetrics(sessionDir, m);
        } catch (Exception ignored) {}
    }

    private void clearRuntime() {
        if (runtime != null) runtime.edit().clear().apply();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel c = new NotificationChannel(CHANNEL, "스키 기록", NotificationManager.IMPORTANCE_LOW);
        c.setDescription("화면이 꺼져 있어도 스키 GPS 기록을 계속합니다.");
        nm.createNotificationChannel(c);
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, SkiActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 5401, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("야모네 스키 기록 중")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null || !recording) return;
        String label = STATE_DESCENT.equals(state) ? "활주 중"
                : STATE_LIFT.equals(state) ? "리프트 이동"
                : STATE_STOPPED.equals(state) ? "정지"
                : "GPS 움직임 판별 중";
        nm.notify(NOTIFY_ID, buildNotification(String.format(Locale.KOREAN, "%s · %.1f km/h", label, currentSpeedKmh)));
    }

    @Override public void onDestroy() {
        stopLocation();
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
