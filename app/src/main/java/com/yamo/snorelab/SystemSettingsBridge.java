package com.yamo.snorelab;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import org.json.JSONObject;

import java.io.File;

/** Native settings bridge for real Android permission/storage state. */
public final class SystemSettingsBridge {
    private static final int REQUEST_CORE_PERMISSIONS = 6130;
    private final Activity activity;

    public SystemSettingsBridge(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public String getPermissionState() {
        JSONObject out = new JSONObject();
        try {
            boolean fine = granted(Manifest.permission.ACCESS_FINE_LOCATION);
            boolean coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION);
            boolean background = Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            boolean recognition = Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACTIVITY_RECOGNITION);
            boolean notifications = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS);
            boolean microphone = granted(Manifest.permission.RECORD_AUDIO);
            boolean exactAlarm = canScheduleExactAlarm();
            boolean fullScreenIntent = canUseFullScreenIntent();

            out.put("location", fine || coarse);
            out.put("fineLocation", fine);
            out.put("coarseLocation", coarse);
            out.put("backgroundLocation", background);
            out.put("activityRecognition", recognition);
            out.put("notifications", notifications);
            out.put("microphone", microphone);
            out.put("exactAlarm", exactAlarm);
            out.put("fullScreenIntent", fullScreenIntent);
            out.put("sdkInt", Build.VERSION.SDK_INT);
        } catch (Exception ignored) { }
        return out.toString();
    }

    @JavascriptInterface
    public String requestCorePermissions() {
        java.util.ArrayList<String> wanted = new java.util.ArrayList<>();
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) wanted.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (!granted(Manifest.permission.ACCESS_COARSE_LOCATION)) wanted.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 29 && !granted(Manifest.permission.ACTIVITY_RECOGNITION)) {
            wanted.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!granted(Manifest.permission.RECORD_AUDIO)) wanted.add(Manifest.permission.RECORD_AUDIO);

        activity.runOnUiThread(() -> {
            if (!wanted.isEmpty() && !activity.isFinishing() && !activity.isDestroyed()) {
                activity.requestPermissions(wanted.toArray(new String[0]), REQUEST_CORE_PERMISSIONS);
            }
        });
        return result(true, wanted.isEmpty() ? "already_granted" : "requested").toString();
    }

    @JavascriptInterface
    public String openAppSettings() {
        return launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + activity.getPackageName())));
    }

    @JavascriptInterface
    public String openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
        return launch(intent);
    }

    @JavascriptInterface
    public String openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < 31) return result(true, "not_required").toString();
        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:" + activity.getPackageName()));
        return launch(intent);
    }

    @JavascriptInterface
    public String getStorageState() {
        JSONObject out = new JSONObject();
        try {
            File files = activity.getFilesDir();
            File cache = activity.getCacheDir();
            File movement = new File(files, "activity/walking");
            File sleep = new File(files, "sessions");
            File snow = new File(files, "activity/ski");

            long movementBytes = folderSize(movement);
            long sleepBytes = folderSize(sleep);
            long snowBytes = folderSize(snow);
            long cacheBytes = folderSize(cache);
            long filesBytes = folderSize(files);

            out.put("movementBytes", movementBytes);
            out.put("sleepBytes", sleepBytes);
            out.put("snowBytes", snowBytes);
            out.put("cacheBytes", cacheBytes);
            out.put("filesBytes", filesBytes);
            out.put("trackedBytes", movementBytes + sleepBytes + snowBytes);
            out.put("movementCount", completedMovementCount());
            out.put("sleepCount", SessionStore.listSessions(activity).size());
            out.put("snowCount", completedSnowCount());
            out.put("locationSharingExcluded", true);
        } catch (Exception ignored) { }
        return out.toString();
    }

    private int completedMovementCount() {
        int count = 0;
        for (File dir : WalkingStore.listSessions(activity)) {
            if ("complete".equals(WalkingStore.readMeta(dir).optString("status"))) count++;
        }
        return count;
    }

    private int completedSnowCount() {
        int count = 0;
        File[] dirs = SkiLiftStore.sessionsRoot(activity).listFiles(File::isDirectory);
        if (dirs == null) return 0;
        for (File dir : dirs) {
            JSONObject meta = SkiLiftStore.readMeta(dir);
            if ("complete".equals(meta.optString("status"))) count++;
        }
        return count;
    }

    private boolean granted(String permission) {
        return activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean canScheduleExactAlarm() {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager manager = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
        return manager != null && manager.canScheduleExactAlarms();
    }

    private boolean canUseFullScreenIntent() {
        if (Build.VERSION.SDK_INT < 34) return true;
        NotificationManager manager = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager != null && manager.canUseFullScreenIntent();
    }

    private String launch(Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.runOnUiThread(() -> {
                try { activity.startActivity(intent); } catch (Exception ignored) { }
            });
            return result(true, "opened").toString();
        } catch (Exception e) {
            return result(false, "open_failed").toString();
        }
    }

    private static long folderSize(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return Math.max(0L, file.length());
        long total = 0L;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) total += folderSize(child);
        }
        return total;
    }

    private static JSONObject result(boolean ok, String status) {
        JSONObject out = new JSONObject();
        try { out.put("ok", ok).put("status", status); } catch (Exception ignored) { }
        return out;
    }
}
