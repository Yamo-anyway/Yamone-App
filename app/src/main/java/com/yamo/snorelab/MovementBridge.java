package com.yamo.snorelab;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * JavaScript bridge used by the renewed Yamone movement UI.
 * GPS samples stay in the app-private WalkingStore and are never uploaded here.
 */
public final class MovementBridge {
    private static final int REQUEST_MOVEMENT_PERMISSIONS = 6128;

    private final Activity activity;
    private final SharedPreferences runtime;

    public MovementBridge(Activity activity) {
        this.activity = activity;
        this.runtime = activity.getSharedPreferences(WalkingRecorderService.PREFS, Activity.MODE_PRIVATE);
    }

    @JavascriptInterface
    public String getPermissionState() {
        JSONObject out = new JSONObject();
        try {
            boolean fine = granted(Manifest.permission.ACCESS_FINE_LOCATION);
            boolean coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION);
            boolean recognition = Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACTIVITY_RECOGNITION);
            boolean notifications = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS);
            out.put("location", fine || coarse);
            out.put("fineLocation", fine);
            out.put("coarseLocation", coarse);
            out.put("activityRecognition", recognition);
            out.put("notifications", notifications);
        } catch (Exception ignored) { }
        return out.toString();
    }

    @JavascriptInterface
    public String requestPermissions() {
        ArrayList<String> wanted = new ArrayList<>();
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) wanted.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (!granted(Manifest.permission.ACCESS_COARSE_LOCATION)) wanted.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 29 && !granted(Manifest.permission.ACTIVITY_RECOGNITION)) {
            wanted.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        activity.runOnUiThread(() -> {
            if (!wanted.isEmpty() && !activity.isFinishing() && !activity.isDestroyed()) {
                activity.requestPermissions(wanted.toArray(new String[0]), REQUEST_MOVEMENT_PERMISSIONS);
            }
        });
        return result(true, wanted.isEmpty() ? "already_granted" : "requested").toString();
    }

    @JavascriptInterface
    public String start(String requestedType) {
        if (!hasLocationPermission()) return result(false, "location_permission_required").toString();
        if (runtime.getBoolean(WalkingRecorderService.KEY_RECORDING, false)) {
            JSONObject out = result(true, "already_recording");
            try { out.put("state", stateJson()); } catch (Exception ignored) { }
            return out.toString();
        }
        String type = normalizeType(requestedType);
        activity.runOnUiThread(() -> {
            Intent intent = new Intent(activity, WalkingRecorderService.class);
            intent.setAction(WalkingRecorderService.ACTION_START);
            intent.putExtra("activity_type", type);
            if (Build.VERSION.SDK_INT >= 26) activity.startForegroundService(intent);
            else activity.startService(intent);
        });
        JSONObject out = result(true, "starting");
        try { out.put("activityType", type); } catch (Exception ignored) { }
        return out.toString();
    }

    @JavascriptInterface public String pause() { return command(WalkingRecorderService.ACTION_PAUSE, "pausing"); }
    @JavascriptInterface public String resume() { return command(WalkingRecorderService.ACTION_RESUME, "resuming"); }
    @JavascriptInterface public String stop() { return command(WalkingRecorderService.ACTION_STOP, "stopping"); }

    @JavascriptInterface
    public String getState() {
        return stateJson().toString();
    }

    @JavascriptInterface
    public String getRoute(int requestedMaxPoints) {
        JSONObject out = new JSONObject();
        try {
            File dir = currentSessionDir();
            out.put("sessionId", dir == null ? "" : dir.getName());
            out.put("points", routeJson(dir, clamp(requestedMaxPoints, 20, 400)));
        } catch (Exception ignored) { }
        return out.toString();
    }

    @JavascriptInterface
    public String getLatestRecord(int requestedMaxPoints) {
        JSONObject out = new JSONObject();
        try {
            File dir = latestSessionDir();
            if (dir == null) {
                out.put("found", false);
                return out.toString();
            }
            JSONObject meta = WalkingStore.readMeta(dir);
            out.put("found", true);
            out.put("sessionId", dir.getName());
            out.put("meta", meta);
            out.put("points", routeJson(dir, clamp(requestedMaxPoints, 20, 500)));
        } catch (Exception e) {
            try { out.put("found", false).put("error", "read_failed"); } catch (Exception ignored) { }
        }
        return out.toString();
    }

    private String command(String action, String status) {
        if (!runtime.getBoolean(WalkingRecorderService.KEY_RECORDING, false)
                && !WalkingRecorderService.ACTION_STOP.equals(action)) {
            return result(false, "not_recording").toString();
        }
        activity.runOnUiThread(() -> {
            Intent intent = new Intent(activity, WalkingRecorderService.class);
            intent.setAction(action);
            activity.startService(intent);
        });
        return result(true, status).toString();
    }

    private JSONObject stateJson() {
        JSONObject out = new JSONObject();
        try {
            long lastFix = runtime.getLong(WalkingRecorderService.KEY_LAST_ACCEPTED_FIX_MS, 0L);
            float altitude = runtime.getFloat(WalkingRecorderService.KEY_ALTITUDE_M, Float.NaN);
            float accuracy = runtime.getFloat(WalkingRecorderService.KEY_ACCURACY_M, Float.NaN);
            out.put("recording", runtime.getBoolean(WalkingRecorderService.KEY_RECORDING, false));
            out.put("paused", runtime.getBoolean(WalkingRecorderService.KEY_PAUSED, false));
            out.put("activityType", runtime.getString(WalkingRecorderService.KEY_ACTIVITY_TYPE, "walking"));
            out.put("autoMotionMode", runtime.getString(WalkingRecorderService.KEY_AUTO_MOTION_MODE, "walking"));
            out.put("startMs", runtime.getLong(WalkingRecorderService.KEY_START_MS, 0L));
            out.put("elapsedMs", runtime.getLong(WalkingRecorderService.KEY_ELAPSED_MS, 0L));
            out.put("movingMs", runtime.getLong(WalkingRecorderService.KEY_MOVING_MS, 0L));
            out.put("distanceM", runtime.getLong(WalkingRecorderService.KEY_DISTANCE_M, 0L));
            out.put("walkingDistanceM", runtime.getLong(WalkingRecorderService.KEY_WALKING_DISTANCE_M, 0L));
            out.put("runningDistanceM", runtime.getLong(WalkingRecorderService.KEY_RUNNING_DISTANCE_M, 0L));
            out.put("walkingMovingMs", runtime.getLong(WalkingRecorderService.KEY_WALKING_MOVING_MS, 0L));
            out.put("runningMovingMs", runtime.getLong(WalkingRecorderService.KEY_RUNNING_MOVING_MS, 0L));
            out.put("steps", runtime.getLong(WalkingRecorderService.KEY_STEPS, 0L));
            out.put("stepAvailable", runtime.getBoolean(WalkingRecorderService.KEY_STEP_AVAILABLE, false));
            out.put("currentSpeedKmh", runtime.getFloat(WalkingRecorderService.KEY_CURRENT_SPEED_KMH, 0f));
            out.put("maxSpeedKmh", runtime.getFloat(WalkingRecorderService.KEY_MAX_SPEED_KMH, 0f));
            out.put("altitudeM", Float.isNaN(altitude) ? JSONObject.NULL : altitude);
            out.put("accuracyM", Float.isNaN(accuracy) ? JSONObject.NULL : accuracy);
            out.put("lastFixMs", lastFix);
            out.put("fixAgeMs", lastFix <= 0 ? -1 : Math.max(0L, System.currentTimeMillis() - lastFix));
            out.put("goalState", runtime.getString(WalkingRecorderService.KEY_GOAL_STATE, "ACTIVE"));
            out.put("sessionDir", runtime.getString(WalkingRecorderService.KEY_SESSION_DIR, ""));
            out.put("splitsMs", splitsJson(runtime.getString(WalkingRecorderService.KEY_SPLITS_JSON, "[]")));
        } catch (Exception ignored) { }
        return out;
    }

    private JSONArray splitsJson(String raw) {
        try { return new JSONArray(raw == null || raw.trim().isEmpty() ? "[]" : raw); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    private JSONArray routeJson(File dir, int maxPoints) {
        JSONArray points = new JSONArray();
        if (dir == null) return points;
        List<WalkingStore.Point> route = WalkingStore.readRoute(dir, maxPoints);
        for (WalkingStore.Point p : route) {
            try {
                JSONObject j = new JSONObject();
                j.put("t", p.timeMs);
                j.put("lat", p.lat);
                j.put("lon", p.lon);
                j.put("accuracy", p.accuracy);
                j.put("altitude", p.altitude);
                j.put("speedKmh", p.speedMps * 3.6f);
                points.put(j);
            } catch (Exception ignored) { }
        }
        return points;
    }

    private File currentSessionDir() {
        String path = runtime.getString(WalkingRecorderService.KEY_SESSION_DIR, "");
        if (path == null || path.trim().isEmpty()) return null;
        File dir = new File(path);
        return dir.isDirectory() ? dir : null;
    }

    private File latestSessionDir() {
        File current = currentSessionDir();
        if (current != null) return current;
        for (File dir : WalkingStore.listSessions(activity)) {
            JSONObject meta = WalkingStore.readMeta(dir);
            if ("complete".equals(meta.optString("status"))) return dir;
        }
        return null;
    }

    private boolean granted(String permission) {
        return activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocationPermission() {
        return granted(Manifest.permission.ACCESS_FINE_LOCATION)
                || granted(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    private static String normalizeType(String requested) {
        if ("bike".equals(requested) || "cycling".equals(requested)) return "cycling";
        if ("run".equals(requested) || "running".equals(requested)) return "running";
        if ("auto".equals(requested) || "walkrun".equals(requested)) return "walkrun";
        return "walking";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static JSONObject result(boolean ok, String status) {
        JSONObject out = new JSONObject();
        try { out.put("ok", ok).put("status", status); } catch (Exception ignored) { }
        return out;
    }
}
