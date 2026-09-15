package com.yamo.snorelab;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

/** Runtime diagnostics for the non-location-sharing Yamone feature set. */
public final class AppReadinessBridge {
    private final Activity activity;

    public AppReadinessBridge(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public String getState() {
        JSONObject out = new JSONObject();
        try {
            boolean fine = granted(Manifest.permission.ACCESS_FINE_LOCATION);
            boolean coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION);
            boolean background = Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            boolean recognition = Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACTIVITY_RECOGNITION);
            boolean microphone = granted(Manifest.permission.RECORD_AUDIO);
            boolean notifications = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS);
            boolean exactAlarm = exactAlarmReady();

            SharedPreferences movement = activity.getSharedPreferences(WalkingRecorderService.PREFS, Context.MODE_PRIVATE);
            boolean movementActive = movement.getBoolean(WalkingRecorderService.KEY_RECORDING, false);
            String gpsState = movement.getString(WalkingRecorderService.KEY_GPS_SIGNAL_STATE, "waiting");
            long longestGpsGapMs = movement.getLong(WalkingRecorderService.KEY_LONGEST_GPS_GAP_MS, 0L);

            SharedPreferences sleep = activity.getSharedPreferences(SleepRecorderService.PREFS, Context.MODE_PRIVATE);
            boolean sleepActive = sleep.getBoolean(SleepRecorderService.KEY_RECORDING, false);
            long heartbeat = sleep.getLong(SleepRecorderService.KEY_HEARTBEAT_MS, 0L);
            long heartbeatAge = heartbeat <= 0L ? -1L : Math.max(0L, System.currentTimeMillis() - heartbeat);
            boolean sleepHealthy = !sleepActive || (heartbeat > 0L && heartbeatAge <= 15_000L);

            JSONArray maps = SnowOfflineMapStore.listInstalled(activity);
            int productionReadyMaps = 0;
            int invalidMaps = 0;
            for (int i = 0; i < maps.length(); i++) {
                JSONObject map = maps.optJSONObject(i);
                if (map == null) continue;
                if (map.optBoolean("productionReady", false)) productionReadyMaps++;
                if (!map.optBoolean("packageValid", false)) invalidMaps++;
            }

            out.put("version", appVersionName());
            out.put("location", fine || coarse);
            out.put("fineLocation", fine);
            out.put("backgroundLocation", background);
            out.put("activityRecognition", recognition);
            out.put("microphone", microphone);
            out.put("notifications", notifications);
            out.put("exactAlarm", exactAlarm);
            out.put("movementActive", movementActive);
            out.put("gpsState", gpsState == null ? "waiting" : gpsState);
            out.put("longestGpsGapMs", longestGpsGapMs);
            out.put("sleepActive", sleepActive);
            out.put("sleepHeartbeatAgeMs", heartbeatAge);
            out.put("sleepHealthy", sleepHealthy);
            out.put("snowDeveloperAvailable", SnowBridge.isDeveloperAvailable(activity));
            out.put("snowInstalledMaps", maps.length());
            out.put("snowProductionReadyMaps", productionReadyMaps);
            out.put("snowInvalidMaps", invalidMaps);
            out.put("locationSharingExcluded", true);
            out.put("corePermissionReady", (fine || coarse) && recognition && microphone && notifications && exactAlarm);
            out.put("backgroundReady", background);
        } catch (Exception e) {
            try { out.put("error", "readiness_failed"); } catch (Exception ignored) { }
        }
        return out.toString();
    }

    private String appVersionName() {
        try {
            String version = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0).versionName;
            return version == null ? "" : version;
        } catch (Exception e) {
            return "";
        }
    }

    private boolean granted(String permission) {
        return activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean exactAlarmReady() {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager manager = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
        return manager != null && manager.canScheduleExactAlarms();
    }
}
