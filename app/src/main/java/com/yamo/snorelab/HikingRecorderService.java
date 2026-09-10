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

/** Local-only hiking GPS recorder. No route data is uploaded. */
public final class HikingRecorderService extends Service {
    public static final String ACTION_START = "com.yamo.snorelab.HIKING_START";
    public static final String ACTION_STOP = "com.yamo.snorelab.HIKING_STOP";

    public static final String PREFS = "yamone_hiking_runtime_v1";
    public static final String KEY_RECORDING = "recording";
    public static final String KEY_START_MS = "start_ms";
    public static final String KEY_DURATION_MS = "duration_ms";
    public static final String KEY_DISTANCE_M = "distance_m";
    public static final String KEY_ASCENT_M = "ascent_m";
    public static final String KEY_ALTITUDE_M = "altitude_m";
    public static final String KEY_MAX_ALTITUDE_M = "max_altitude_m";
    public static final String KEY_MIN_ALTITUDE_M = "min_altitude_m";
    public static final String KEY_ACCURACY_M = "accuracy_m";
    public static final String KEY_SESSION_DIR = "session_dir";

    private static final String CHANNEL = "hiking_recording_v1";
    private static final int NOTIFY = 5401;
    private static final float MAX_ACCURACY_M = 45f;
    private static final float MAX_SPEED_MPS = 8.5f;
    private static final float MIN_MOVE_M = 2.0f;
    private static final long GPS_SHADOW_MIN_MS = 10_000L;
    private static final long GPS_SHADOW_MAX_MS = 10 * 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences runtime;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private boolean recording;
    private long startMs;
    private double distanceM;
    private double ascentM;
    private double smoothedAltitude = Double.NaN;
    private double lastAltitude = Double.NaN;
    private double maxAltitude = Double.NaN;
    private double minAltitude = Double.NaN;
    private float accuracyM = Float.NaN;
    private Location lastLocation;
    private long lastLocationTime;
    private float lastSpeedMps;
    private int gpsShadowSegments;
    private double gpsShadowDistanceM;
    private long gpsShadowDurationMs;
    private File sessionDir;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!recording) return;
            persist();
            updateNotification();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        runtime = getSharedPreferences(PREFS, MODE_PRIVATE);
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_START.equals(action)) startRecording();
        else if (ACTION_STOP.equals(action)) stopRecording();
        return START_NOT_STICKY;
    }

    private void startRecording() {
        if (recording || runtime.getBoolean(KEY_RECORDING, false)) return;
        if (!hasLocationPermission()) {
            stopSelf();
            return;
        }
        startMs = System.currentTimeMillis();
        distanceM = 0;
        ascentM = 0;
        smoothedAltitude = Double.NaN;
        lastAltitude = Double.NaN;
        maxAltitude = Double.NaN;
        minAltitude = Double.NaN;
        accuracyM = Float.NaN;
        lastLocation = null;
        lastLocationTime = 0L;
        lastSpeedMps = 0f;
        gpsShadowSegments = 0;
        gpsShadowDistanceM = 0;
        gpsShadowDurationMs = 0L;
        sessionDir = HikingStore.createSession(this, startMs);
        recording = true;

        Notification notification = buildNotification("등산 기록을 시작합니다.");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFY, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFY, notification);
        }
        startLocation();
        writeMeta("recording", 0L);
        persist();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void startLocation() {
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
    }

    private void onLocation(Location loc) {
        if (!recording || loc == null) return;
        if (loc.hasAccuracy() && loc.getAccuracy() > MAX_ACCURACY_M) return;
        long now = loc.getTime() > 0 ? loc.getTime() : System.currentTimeMillis();
        accuracyM = loc.hasAccuracy() ? loc.getAccuracy() : Float.NaN;

        if (loc.hasAltitude()) {
            double raw = loc.getAltitude();
            if (Double.isNaN(smoothedAltitude)) smoothedAltitude = raw;
            else smoothedAltitude = smoothedAltitude * 0.82 + raw * 0.18;
            if (Double.isNaN(maxAltitude) || smoothedAltitude > maxAltitude) maxAltitude = smoothedAltitude;
            if (Double.isNaN(minAltitude) || smoothedAltitude < minAltitude) minAltitude = smoothedAltitude;
            if (!Double.isNaN(lastAltitude)) {
                double gain = smoothedAltitude - lastAltitude;
                if (gain >= 1.2 && gain <= 18.0) ascentM += gain;
            }
            lastAltitude = smoothedAltitude;
        }

        if (lastLocation == null) {
            lastLocation = new Location(loc);
            lastLocationTime = now;
            append(loc, now, 0f);
            persist();
            return;
        }
        if (now <= lastLocationTime) return;
        long dtMs = now - lastLocationTime;
        if (dtMs > GPS_SHADOW_MIN_MS) {
            if (bridgeGpsShadow(loc, now, dtMs)) {
                persist();
                return;
            }
            lastLocation = new Location(loc);
            lastLocationTime = now;
            lastSpeedMps = 0f;
            append(loc, now, 0f);
            persist();
            return;
        }
        float d = lastLocation.distanceTo(loc);
        float speed = d / Math.max(0.5f, dtMs / 1000f);
        float noise = Math.max(MIN_MOVE_M, Math.max(lastLocation.hasAccuracy() ? lastLocation.getAccuracy() : 0f,
                loc.hasAccuracy() ? loc.getAccuracy() : 0f) * 0.22f);
        if (d >= noise && speed <= MAX_SPEED_MPS) distanceM += d;
        else if (speed > MAX_SPEED_MPS && d > 15f) return;

        lastLocation = new Location(loc);
        lastLocationTime = now;
        lastSpeedMps = speed <= MAX_SPEED_MPS ? Math.max(0f, speed) : 0f;
        append(loc, now, lastSpeedMps);
        persist();
    }

    private boolean bridgeGpsShadow(Location loc, long now, long dtMs) {
        if (lastLocation == null || dtMs <= GPS_SHADOW_MIN_MS || dtMs > GPS_SHADOW_MAX_MS) return false;
        float d = lastLocation.distanceTo(loc);
        float dtSec = dtMs / 1000f;
        float averageMps = d / Math.max(0.001f, dtSec);
        float previousAccuracy = lastLocation.hasAccuracy() ? lastLocation.getAccuracy() : 0f;
        float combinedAccuracy = Math.max(previousAccuracy, loc.hasAccuracy() ? loc.getAccuracy() : 0f);
        float reportedMps = loc.hasSpeed() ? Math.max(0f, loc.getSpeed()) : Float.NaN;
        boolean movingBefore = lastSpeedMps >= 0.30f;
        boolean movingAfter = !Float.isNaN(reportedMps)
                && reportedMps >= 0.30f && reportedMps <= MAX_SPEED_MPS * 1.10f;
        float minimumBridgeDistance = Math.max(8f, combinedAccuracy * 0.50f);

        if (d < minimumBridgeDistance) return false;
        if (averageMps < 0.18f || averageMps > MAX_SPEED_MPS) return false;
        if (!(movingBefore || movingAfter || averageMps >= 0.45f)) return false;

        distanceM += d;
        gpsShadowSegments++;
        gpsShadowDistanceM += d;
        gpsShadowDurationMs += dtMs;
        lastLocation = new Location(loc);
        lastLocationTime = now;
        lastSpeedMps = averageMps;
        append(loc, now, averageMps);
        return true;
    }

    private void append(Location loc, long now, float speedMps) {
        HikingStore.appendRoute(sessionDir, now, loc.getLatitude(), loc.getLongitude(), accuracyM,
                Double.isNaN(smoothedAltitude) ? 0 : smoothedAltitude, Math.max(0f, speedMps));
    }

    private long durationMs() {
        return recording && startMs > 0 ? Math.max(0L, System.currentTimeMillis() - startMs)
                : runtime.getLong(KEY_DURATION_MS, 0L);
    }

    private void persist() {
        runtime.edit()
                .putBoolean(KEY_RECORDING, recording)
                .putLong(KEY_START_MS, startMs)
                .putLong(KEY_DURATION_MS, durationMs())
                .putLong(KEY_DISTANCE_M, Math.round(distanceM))
                .putLong(KEY_ASCENT_M, Math.round(ascentM))
                .putFloat(KEY_ALTITUDE_M, Double.isNaN(smoothedAltitude) ? Float.NaN : (float) smoothedAltitude)
                .putFloat(KEY_MAX_ALTITUDE_M, Double.isNaN(maxAltitude) ? Float.NaN : (float) maxAltitude)
                .putFloat(KEY_MIN_ALTITUDE_M, Double.isNaN(minAltitude) ? Float.NaN : (float) minAltitude)
                .putFloat(KEY_ACCURACY_M, accuracyM)
                .putString(KEY_SESSION_DIR, sessionDir == null ? "" : sessionDir.getAbsolutePath())
                .apply();
    }

    private void writeMeta(String status, long endMs) {
        if (sessionDir == null) return;
        try {
            JSONObject m = new JSONObject();
            m.put("type", "hiking");
            m.put("status", status);
            m.put("startEpochMs", startMs);
            if (endMs > 0) m.put("endEpochMs", endMs);
            m.put("durationMs", durationMs());
            m.put("distanceM", Math.round(distanceM));
            m.put("ascentM", Math.round(ascentM));
            m.put("lastAltitudeM", Double.isNaN(smoothedAltitude) ? JSONObject.NULL : smoothedAltitude);
            m.put("maxAltitudeM", Double.isNaN(maxAltitude) ? JSONObject.NULL : maxAltitude);
            m.put("minAltitudeM", Double.isNaN(minAltitude) ? JSONObject.NULL : minAltitude);
            m.put("locationStorage", "local_only");
            m.put("gpsFilter", "local_hiking_v2_shadow");
            m.put("gpsShadowSegments", gpsShadowSegments);
            m.put("gpsShadowDistanceM", Math.round(gpsShadowDistanceM));
            m.put("gpsShadowDurationMs", gpsShadowDurationMs);
            HikingStore.writeMeta(sessionDir, m);
        } catch (Exception ignored) {}
    }

    private void stopRecording() {
        if (!recording && !runtime.getBoolean(KEY_RECORDING, false)) {
            stopSelf();
            return;
        }
        long end = System.currentTimeMillis();
        if (!recording) {
            String path = runtime.getString(KEY_SESSION_DIR, "");
            if (!path.isEmpty()) sessionDir = new File(path);
            startMs = runtime.getLong(KEY_START_MS, 0L);
            distanceM = runtime.getLong(KEY_DISTANCE_M, 0L);
            ascentM = runtime.getLong(KEY_ASCENT_M, 0L);
            smoothedAltitude = runtime.getFloat(KEY_ALTITUDE_M, Float.NaN);
            maxAltitude = runtime.getFloat(KEY_MAX_ALTITUDE_M, Float.NaN);
            minAltitude = runtime.getFloat(KEY_MIN_ALTITUDE_M, Float.NaN);
        }
        recording = true;
        writeMeta("complete", end);
        recording = false;
        persist();
        handler.removeCallbacks(ticker);
        stopLocation();
        stopForeground(true);
        stopSelf();
    }

    private void stopLocation() {
        if (locationManager != null && locationListener != null) {
            try { locationManager.removeUpdates(locationListener); } catch (Exception ignored) {}
        }
        locationListener = null;
        locationManager = null;
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "등산 기록", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("화면이 꺼진 동안에도 사용자가 시작한 등산 GPS 기록을 유지합니다.");
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification(String message) {
        Intent open = new Intent(this, HikingActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 5401, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, HikingRecorderService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 5402, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("야모네 등산 기록 중")
                .setContentText(message)
                .setContentIntent(openPi)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "기록 종료", stopPi).build())
                .build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.notify(NOTIFY, buildNotification(String.format(Locale.KOREAN, "%.2f km · 상승 %dm · %s",
                distanceM / 1000.0, Math.round(ascentM), format(durationMs()))));
    }

    private static String format(long ms) {
        long s = Math.max(0, ms / 1000), h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.KOREAN, "%d:%02d:%02d", h, m, sec)
                : String.format(Locale.KOREAN, "%02d:%02d", m, sec);
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopLocation();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
