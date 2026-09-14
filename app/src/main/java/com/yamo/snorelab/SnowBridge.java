package com.yamo.snorelab;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Developer-gated bridge for v0.28 Snow.
 * Release/non-debug builds always report unavailable and cannot start Snow services.
 */
public final class SnowBridge {
    private static final int REQUEST_LOCATION = 7281;
    private static final String DEV_PREFS = "yamone_developer_mode_v1";
    private static final String KEY_UNLOCKED = "unlocked";
    private static final String KEY_TAPS = "version_taps";
    private static final String UI_PREFS = "yamone_snow_ui_v2";
    private static final String KEY_SPORT = "sport";

    private final Activity activity;
    private final SharedPreferences devPrefs;
    private final SharedPreferences runtime;
    private final SharedPreferences uiPrefs;

    public SnowBridge(Activity activity) {
        this.activity = activity;
        this.devPrefs = activity.getSharedPreferences(DEV_PREFS, Context.MODE_PRIVATE);
        this.runtime = activity.getSharedPreferences(SkiRecorderService.PREFS, Context.MODE_PRIVATE);
        this.uiPrefs = activity.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isDebuggable(Context context) {
        if (context == null) return false;
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    public static boolean isDeveloperAvailable(Context context) {
        if (!isDebuggable(context)) return false;
        return context.getSharedPreferences(DEV_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_UNLOCKED, false);
    }

    @JavascriptInterface
    public String getGateState() {
        JSONObject out = new JSONObject();
        try {
            boolean debug = isDebuggable(activity);
            boolean unlocked = debug && devPrefs.getBoolean(KEY_UNLOCKED, false);
            out.put("developerBuild", debug);
            out.put("unlocked", unlocked);
            out.put("available", unlocked);
            out.put("tapCount", debug ? Math.max(0, devPrefs.getInt(KEY_TAPS, 0)) : 0);
            out.put("releaseExcluded", !debug);
        } catch (Exception ignored) {}
        return out.toString();
    }

    @JavascriptInterface
    public String registerDeveloperTap() {
        JSONObject out = new JSONObject();
        try {
            if (!isDebuggable(activity)) {
                out.put("ok", false).put("unlocked", false).put("remaining", 7);
                return out.toString();
            }
            int taps = Math.max(0, devPrefs.getInt(KEY_TAPS, 0)) + 1;
            boolean unlocked = taps >= 7 || devPrefs.getBoolean(KEY_UNLOCKED, false);
            SharedPreferences.Editor e = devPrefs.edit().putInt(KEY_TAPS, Math.min(7, taps));
            if (unlocked) e.putBoolean(KEY_UNLOCKED, true);
            e.apply();
            if (unlocked) SnowAutoDetectManager.sync(activity);
            out.put("ok", true).put("unlocked", unlocked).put("remaining", Math.max(0, 7 - taps));
        } catch (Exception ignored) {}
        return out.toString();
    }

    @JavascriptInterface
    public String lockDeveloperMode() {
        if (!isDebuggable(activity)) return result(false, "release").toString();
        devPrefs.edit().putBoolean(KEY_UNLOCKED, false).putInt(KEY_TAPS, 0).apply();
        SnowAutoDetectManager.remove(activity);
        return result(true, "locked").toString();
    }

    @JavascriptInterface
    public String getPermissionState() {
        JSONObject out = new JSONObject();
        try {
            out.put("fineLocation", granted(Manifest.permission.ACCESS_FINE_LOCATION));
            out.put("coarseLocation", granted(Manifest.permission.ACCESS_COARSE_LOCATION));
            out.put("backgroundLocation", Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION));
            out.put("notifications", Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS));
        } catch (Exception ignored) {}
        return out.toString();
    }

    @JavascriptInterface
    public String requestCorePermissions() {
        if (!isDeveloperAvailable(activity)) return result(false, "locked").toString();
        ArrayList<String> wanted = new ArrayList<>();
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) wanted.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) wanted.add(Manifest.permission.POST_NOTIFICATIONS);
        activity.runOnUiThread(() -> {
            if (!wanted.isEmpty() && !activity.isFinishing() && !activity.isDestroyed()) {
                activity.requestPermissions(wanted.toArray(new String[0]), REQUEST_LOCATION);
            }
        });
        return result(true, wanted.isEmpty() ? "already_granted" : "requested").toString();
    }

    @JavascriptInterface
    public String openLocationSettings() {
        if (!isDeveloperAvailable(activity)) return result(false, "locked").toString();
        activity.runOnUiThread(() -> {
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(i);
            } catch (Exception ignored) {}
        });
        return result(true, "opened").toString();
    }

    @JavascriptInterface
    public String getSettings() {
        JSONObject out = new JSONObject();
        try {
            out.put("autoDetect", SnowAutoDetectManager.enabled(activity));
            out.put("sport", "snowboard".equals(uiPrefs.getString(KEY_SPORT, "ski")) ? "snowboard" : "ski");
            out.put("offlineMaps", SnowOfflineMapStore.listInstalled(activity));
        } catch (Exception ignored) {}
        return out.toString();
    }

    @JavascriptInterface
    public String saveSettings(String rawJson) {
        if (!isDeveloperAvailable(activity)) return result(false, "locked").toString();
        try {
            JSONObject in = new JSONObject(rawJson == null ? "{}" : rawJson);
            if (in.has("autoDetect")) SnowAutoDetectManager.setEnabled(activity, in.optBoolean("autoDetect", true));
            if (in.has("sport")) {
                String sport = "snowboard".equals(in.optString("sport")) ? "snowboard" : "ski";
                uiPrefs.edit().putString(KEY_SPORT, sport).apply();
            }
            return result(true, "saved").toString();
        } catch (Exception e) {
            return result(false, "invalid_settings").toString();
        }
    }

    @JavascriptInterface
    public String installDeveloperTestMap() {
        if (!isDeveloperAvailable(activity)) return result(false, "locked").toString();
        JSONObject map = SnowOfflineMapStore.ensureDeveloperTestMap(activity);
        if (map.length() == 0) return result(false, "install_failed").toString();
        SnowAutoDetectManager.sync(activity);
        JSONObject out = result(true, "installed");
        try { out.put("map", map); } catch (Exception ignored) {}
        return out.toString();
    }

    @JavascriptInterface
    public String deleteOfflineMap(String resortKey) {
        if (!isDeveloperAvailable(activity)) return result(false, "locked").toString();
        boolean ok = SnowOfflineMapStore.delete(activity, resortKey);
        if (resortKey != null && resortKey.equals(SkiResortStore.currentKey(activity))) SkiResortStore.clearCurrent(activity);
        SnowAutoDetectManager.sync(activity);
        return result(ok, ok ? "deleted" : "delete_failed").toString();
    }

    @JavascriptInterface
    public String detectCurrentResort() {
        JSONObject out = new JSONObject();
        try {
            if (!isDeveloperAvailable(activity)) return out.put("found", false).put("status", "locked").toString();
            Location loc = lastKnownLocation();
            if (loc == null) return out.put("found", false).put("status", "no_location").toString();
            JSONObject found = SnowOfflineMapStore.detectInstalledResort(activity, loc.getLatitude(), loc.getLongitude());
            if (found.length() == 0) return out.put("found", false).put("status", "outside_installed_maps").toString();
            String key = found.optString("resortKey", "");
            String name = found.optString("resortName", "");
            SkiResortStore.setCurrent(activity, key, name);
            out.put("found", true).put("resortKey", key).put("resortName", name)
                    .put("distanceM", found.optDouble("distanceM", 0));
        } catch (Exception ignored) {}
        return out.toString();
    }

    @JavascriptInterface
    public String start(String requestedSport) {
        if (!isDeveloperAvailable(activity)) return result(false, "locked").toString();
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) return result(false, "location_permission_required").toString();
        if (runtime.getBoolean(SkiRecorderService.KEY_RECORDING, false)) return result(true, "already_recording").toString();
        String sport = "snowboard".equals(requestedSport) ? "snowboard" : "ski";
        uiPrefs.edit().putString(KEY_SPORT, sport).apply();
        activity.runOnUiThread(() -> {
            Intent i = new Intent(activity, SkiRecorderService.class)
                    .setAction(SkiRecorderService.ACTION_START).putExtra("sport", sport);
            if (Build.VERSION.SDK_INT >= 26) activity.startForegroundService(i); else activity.startService(i);
        });
        return result(true, "starting").toString();
    }

    @JavascriptInterface
    public String startDeveloperSimulation(String requestedSport) {
        if (!isDeveloperAvailable(activity)) return result(false, "locked").toString();
        if (runtime.getBoolean(SkiRecorderService.KEY_RECORDING, false)) return result(false, "already_recording").toString();
        SnowOfflineMapStore.ensureDeveloperTestMap(activity);
        SkiResortStore.setCurrent(activity, SnowOfflineMapStore.DEV_RESORT_KEY, SnowOfflineMapStore.DEV_RESORT_NAME);
        String sport = "snowboard".equals(requestedSport) ? "snowboard" : "ski";
        uiPrefs.edit().putString(KEY_SPORT, sport).apply();
        activity.runOnUiThread(() -> {
            Intent i = new Intent(activity, SkiRecorderService.class)
                    .setAction(SkiRecorderService.ACTION_DEBUG_START).putExtra("sport", sport);
            if (Build.VERSION.SDK_INT >= 26) activity.startForegroundService(i); else activity.startService(i);
        });
        return result(true, "simulation_starting").toString();
    }

    @JavascriptInterface
    public String pause() { return serviceAction(SkiRecorderService.ACTION_PAUSE, "pausing"); }

    @JavascriptInterface
    public String resume() { return serviceAction(SkiRecorderService.ACTION_RESUME, "resuming"); }

    @JavascriptInterface
    public String stop() {
        if (!isDeveloperAvailable(activity)) return result(false, "locked").toString();
        JSONObject out = result(true, "stopping");
        String path = runtime.getString(SkiRecorderService.KEY_SESSION_DIR, "");
        File dir = path.isEmpty() ? null : new File(path);
        try { out.put("sessionId", dir == null ? "" : dir.getName()); } catch (Exception ignored) {}
        activity.runOnUiThread(() -> {
            try { activity.startService(new Intent(activity, SkiRecorderService.class).setAction(SkiRecorderService.ACTION_STOP)); }
            catch (Exception ignored) {}
        });
        return out.toString();
    }

    @JavascriptInterface
    public String getState() {
        JSONObject out = new JSONObject();
        try {
            boolean available = isDeveloperAvailable(activity);
            boolean recording = available && runtime.getBoolean(SkiRecorderService.KEY_RECORDING, false);
            boolean paused = recording && runtime.getBoolean(SkiRecorderService.KEY_PAUSED, false);
            long start = runtime.getLong(SkiRecorderService.KEY_START_MS, 0L);
            long pauseStarted = runtime.getLong(SkiRecorderService.KEY_PAUSE_STARTED_MS, 0L);
            long pausedMs = Math.max(0L, runtime.getLong(SkiRecorderService.KEY_PAUSED_TOTAL_MS, 0L));
            if (paused && pauseStarted > 0L) pausedMs += Math.max(0L, System.currentTimeMillis() - pauseStarted);
            long elapsed = recording && start > 0 ? Math.max(0L, System.currentTimeMillis() - start - pausedMs) : 0L;
            String path = runtime.getString(SkiRecorderService.KEY_SESSION_DIR, "");
            File dir = path.isEmpty() ? null : new File(path);
            long lastFix = runtime.getLong(SkiRecorderService.KEY_LAST_FIX_MS, 0L);
            out.put("available", available);
            out.put("recording", recording);
            out.put("paused", paused);
            out.put("simulation", runtime.getBoolean(SkiRecorderService.KEY_DEBUG_SIMULATION, false));
            out.put("sessionId", dir == null ? "" : dir.getName());
            out.put("sport", runtime.getString(SkiRecorderService.KEY_SPORT, uiPrefs.getString(KEY_SPORT, "ski")));
            out.put("startMs", start);
            out.put("activeElapsedMs", elapsed);
            out.put("pausedMs", pausedMs);
            out.put("state", runtime.getString(SkiRecorderService.KEY_STATE, SkiRecorderService.STATE_CHECKING));
            out.put("speedKmh", runtime.getFloat(SkiRecorderService.KEY_SPEED_KMH, 0f));
            out.put("maxSpeedKmh", runtime.getFloat(SkiRecorderService.KEY_MAX_SPEED_KMH, 0f));
            out.put("altitudeM", runtime.getFloat(SkiRecorderService.KEY_ALTITUDE_M, 0f));
            out.put("accuracyM", runtime.contains(SkiRecorderService.KEY_ACCURACY_M)
                    ? runtime.getFloat(SkiRecorderService.KEY_ACCURACY_M, Float.NaN) : JSONObject.NULL);
            out.put("lastFixMs", lastFix);
            out.put("fixAgeMs", lastFix <= 0 ? JSONObject.NULL : Math.max(0L, System.currentTimeMillis() - lastFix));
            out.put("descentCount", runtime.getInt(SkiRecorderService.KEY_DESCENT_COUNT, 0));
            out.put("liftCount", runtime.getInt(SkiRecorderService.KEY_LIFT_COUNT, 0));
            out.put("descentDistanceM", runtime.getLong(SkiRecorderService.KEY_DESCENT_DISTANCE_M, 0L));
            out.put("descentVerticalM", runtime.getLong(SkiRecorderService.KEY_DESCENT_VERTICAL_M, 0L));
            out.put("liftTimeMs", runtime.getLong(SkiRecorderService.KEY_LIFT_TIME_MS, 0L));
            out.put("waitTimeMs", runtime.getLong(SkiRecorderService.KEY_WAIT_TIME_MS, 0L));
            out.put("resortKey", SkiResortStore.currentKey(activity));
            out.put("resortName", SkiResortStore.currentName(activity));
            out.put("mapInstalled", SnowOfflineMapStore.isInstalled(activity, SkiResortStore.currentKey(activity)));
        } catch (Exception ignored) {}
        return out.toString();
    }

    @JavascriptInterface
    public String getActiveRoute(int requestedMax) {
        String path = runtime.getString(SkiRecorderService.KEY_SESSION_DIR, "");
        File dir = path.isEmpty() ? null : new File(path);
        return routePayload(dir, clamp(requestedMax, 20, 700)).toString();
    }

    @JavascriptInterface
    public String listRecords(int requestedMax) {
        JSONArray records = new JSONArray();
        int max = clamp(requestedMax, 1, 200);
        if (!isDeveloperAvailable(activity)) return wrapRecords(records).toString();
        try {
            for (File dir : SkiLiftStore.listSessions(activity)) {
                if (records.length() >= max) break;
                JSONObject m = SkiLiftStore.readSessionMeta(dir);
                if (!"complete".equals(m.optString("status", ""))) continue;
                records.put(summaryJson(dir, m));
            }
        } catch (Exception ignored) {}
        return wrapRecords(records).toString();
    }

    @JavascriptInterface
    public String getRecordDetail(String sessionId, int requestedMaxPoints) {
        JSONObject out = new JSONObject();
        try {
            if (!isDeveloperAvailable(activity)) return out.put("found", false).put("status", "locked").toString();
            File dir = sessionDir(sessionId);
            if (dir == null) return out.put("found", false).toString();
            JSONObject meta = SkiLiftStore.readSessionMeta(dir);
            if (!"complete".equals(meta.optString("status", ""))) return out.put("found", false).toString();
            JSONObject summary = summaryJson(dir, meta);
            out = new JSONObject(summary.toString());
            out.put("found", true);

            List<SkiSessionAnalysis.RoutePoint> route = SkiSessionAnalysis.readRoute(dir);
            out.put("route", sampledRoute(route, clamp(requestedMaxPoints, 50, 1200)));
            JSONArray descents = new JSONArray();
            List<SkiSessionAnalysis.DescentSummary> ds = SkiSessionAnalysis.readDescents(dir);
            for (int i = 0; i < ds.size(); i++) {
                SkiSessionAnalysis.DescentSummary d = ds.get(i);
                List<SkiSessionAnalysis.RoutePoint> section = routeBetween(route, d.startMs, d.endMs);
                JSONObject c = SnowOfflineMapStore.classifyDescent(activity, meta.optString("resortKey", ""), section);
                JSONObject j = new JSONObject();
                j.put("index", i);
                j.put("startMs", d.startMs);
                j.put("endMs", d.endMs);
                j.put("durationMs", Math.max(0L, d.endMs - d.startMs));
                j.put("distanceM", d.distanceM);
                j.put("verticalM", d.verticalM);
                j.put("maxSpeedKmh", d.maxSpeedKmh);
                j.put("avgSpeedKmh", d.avgSpeedKmh);
                j.put("classification", c);
                j.put("route", sampledRoute(section, 120));
                descents.put(j);
            }
            out.put("descents", descents);
            out.put("lifts", SkiLiftStore.readLiftObservations(dir));
            String resortKey = meta.optString("resortKey", "");
            out.put("mapManifest", SnowOfflineMapStore.readManifest(activity, resortKey));
            out.put("mapData", SnowOfflineMapStore.readMapData(activity, resortKey));
            out.put("storageBytes", folderSize(dir));
        } catch (Exception e) {
            try { out.put("found", false).put("status", "read_failed"); } catch (Exception ignored) {}
        }
        return out.toString();
    }

    @JavascriptInterface
    public String getStorageInfo() {
        JSONObject out = new JSONObject();
        try {
            out.put("bytes", folderSize(SkiLiftStore.root(activity)));
            out.put("records", completedCount());
            out.put("offlineMaps", SnowOfflineMapStore.listInstalled(activity).length());
            out.put("recording", runtime.getBoolean(SkiRecorderService.KEY_RECORDING, false));
        } catch (Exception ignored) {}
        return out.toString();
    }

    @JavascriptInterface
    public String deleteRecord(String sessionId) {
        if (!isDeveloperAvailable(activity)) return result(false, "locked").toString();
        if (runtime.getBoolean(SkiRecorderService.KEY_RECORDING, false)) {
            String path = runtime.getString(SkiRecorderService.KEY_SESSION_DIR, "");
            if (!path.isEmpty() && new File(path).getName().equals(sessionId)) return result(false, "recording").toString();
        }
        File dir = sessionDir(sessionId);
        if (dir == null) return result(false, "not_found").toString();
        deleteRecursive(dir);
        return result(!dir.exists(), !dir.exists() ? "deleted" : "delete_failed").toString();
    }

    @JavascriptInterface
    public String deleteAllRecords() {
        if (!isDeveloperAvailable(activity)) return result(false, "locked").toString();
        if (runtime.getBoolean(SkiRecorderService.KEY_RECORDING, false)) return result(false, "recording").toString();
        File root = SkiLiftStore.sessionsRoot(activity);
        File[] kids = root.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        return result(true, "deleted").toString();
    }

    @JavascriptInterface
    public String syncAutoDetect() {
        if (!isDeveloperAvailable(activity)) return result(false, "locked").toString();
        SnowAutoDetectManager.sync(activity);
        return result(true, "synced").toString();
    }

    public void release() { }

    private String serviceAction(String action, String status) {
        if (!isDeveloperAvailable(activity)) return result(false, "locked").toString();
        if (!runtime.getBoolean(SkiRecorderService.KEY_RECORDING, false)) return result(false, "not_recording").toString();
        activity.runOnUiThread(() -> {
            try { activity.startService(new Intent(activity, SkiRecorderService.class).setAction(action)); }
            catch (Exception ignored) {}
        });
        return result(true, status).toString();
    }

    private JSONObject summaryJson(File dir, JSONObject m) throws Exception {
        JSONObject r = new JSONObject();
        r.put("sessionId", dir.getName());
        r.put("sport", "snowboard".equals(m.optString("sport")) ? "snowboard" : "ski");
        r.put("startEpochMs", m.optLong("startEpochMs", 0L));
        r.put("endEpochMs", m.optLong("endEpochMs", 0L));
        r.put("durationMs", Math.max(0L, m.optLong("durationMs", 0L)));
        r.put("activeDurationMs", Math.max(0L, m.has("activeDurationMs") ? m.optLong("activeDurationMs", 0L) : m.optLong("durationMs", 0L)));
        r.put("pausedMs", Math.max(0L, m.optLong("pausedMs", 0L)));
        r.put("resortKey", m.optString("resortKey", ""));
        r.put("resortName", m.optString("resortName", ""));
        r.put("descentCount", Math.max(0, m.optInt("descentCount", 0)));
        r.put("liftCount", Math.max(0, m.optInt("liftCount", 0)));
        r.put("descentDistanceM", Math.max(0L, m.optLong("descentDistanceM", 0L)));
        r.put("descentVerticalM", Math.max(0L, m.optLong("descentVerticalM", 0L)));
        r.put("liftTimeMs", Math.max(0L, m.optLong("liftTimeMs", 0L)));
        r.put("waitTimeMs", Math.max(0L, m.optLong("waitTimeMs", 0L)));
        r.put("maxSpeedKmh", Math.max(0, m.optDouble("maxSpeedKmh", 0)));
        r.put("developerSimulated", m.optBoolean("developerSimulated", false));
        r.put("mergedSessionCount", Math.max(1, m.optInt("mergedSessionCount", 1)));
        r.put("mapInstalled", SnowOfflineMapStore.isInstalled(activity, m.optString("resortKey", "")));
        return r;
    }

    private JSONObject routePayload(File dir, int max) {
        JSONObject out = new JSONObject();
        try {
            List<SkiSessionAnalysis.RoutePoint> route = dir == null ? new ArrayList<>() : SkiSessionAnalysis.readRoute(dir);
            out.put("points", sampledRoute(route, max));
            String resortKey = dir == null ? SkiResortStore.currentKey(activity) : SkiLiftStore.readSessionMeta(dir).optString("resortKey", SkiResortStore.currentKey(activity));
            out.put("mapManifest", SnowOfflineMapStore.readManifest(activity, resortKey));
            out.put("mapData", SnowOfflineMapStore.readMapData(activity, resortKey));
        } catch (Exception ignored) {}
        return out;
    }

    private static JSONArray sampledRoute(List<SkiSessionAnalysis.RoutePoint> route, int max) {
        JSONArray out = new JSONArray();
        if (route == null || route.isEmpty()) return out;
        int stride = Math.max(1, (int) Math.ceil(route.size() / (double) Math.max(1, max)));
        for (int i = 0; i < route.size(); i += stride) putRoute(out, route.get(i));
        SkiSessionAnalysis.RoutePoint last = route.get(route.size() - 1);
        if ((route.size() - 1) % stride != 0) putRoute(out, last);
        return out;
    }

    private static void putRoute(JSONArray out, SkiSessionAnalysis.RoutePoint p) {
        try {
            JSONObject j = new JSONObject();
            j.put("t", p.timeMs).put("lat", p.lat).put("lon", p.lon)
                    .put("accuracy", p.accuracyM).put("altitude", p.altitudeM)
                    .put("speedKmh", p.speedMps * 3.6).put("state", p.state);
            out.put(j);
        } catch (Exception ignored) {}
    }

    private static List<SkiSessionAnalysis.RoutePoint> routeBetween(List<SkiSessionAnalysis.RoutePoint> route, long start, long end) {
        List<SkiSessionAnalysis.RoutePoint> out = new ArrayList<>();
        if (route == null) return out;
        for (SkiSessionAnalysis.RoutePoint p : route) if (p.timeMs >= start && p.timeMs <= end) out.add(p);
        return out;
    }

    private JSONObject wrapRecords(JSONArray records) {
        JSONObject out = new JSONObject();
        try { out.put("records", records).put("count", records.length()); } catch (Exception ignored) {}
        return out;
    }

    private int completedCount() {
        int count = 0;
        for (File dir : SkiLiftStore.listSessions(activity)) {
            if ("complete".equals(SkiLiftStore.readSessionMeta(dir).optString("status", ""))) count++;
        }
        return count;
    }

    private File sessionDir(String id) {
        if (id == null || id.trim().isEmpty()) return null;
        File root = SkiLiftStore.sessionsRoot(activity);
        File dir = new File(root, id);
        return dir.isDirectory() && isInside(root, dir) ? dir : null;
    }

    private Location lastKnownLocation() {
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION) && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) return null;
        LocationManager lm = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return null;
        Location best = null;
        try {
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l == null) continue;
                if (best == null || l.getTime() > best.getTime()) best = l;
            }
        } catch (SecurityException ignored) {}
        return best;
    }

    private boolean granted(String permission) {
        return activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isInside(File root, File child) {
        try { return child.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator); }
        catch (Exception e) { return false; }
    }

    private static long folderSize(File file) {
        if (file == null || !file.exists()) return 0;
        if (file.isFile()) return file.length();
        long total = 0;
        File[] kids = file.listFiles();
        if (kids != null) for (File k : kids) total += folderSize(k);
        return total;
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] kids = file.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        try { file.delete(); } catch (Exception ignored) {}
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private static JSONObject result(boolean ok, String status) {
        JSONObject out = new JSONObject();
        try { out.put("ok", ok).put("status", status); } catch (Exception ignored) {}
        return out;
    }
}
