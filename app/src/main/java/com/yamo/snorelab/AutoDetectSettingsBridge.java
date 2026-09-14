package com.yamo.snorelab;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import org.json.JSONObject;

/** JavaScript bridge for Settings > 자동감지. */
public final class AutoDetectSettingsBridge {
    private final Activity activity;
    private final SharedPreferences prefs;

    public AutoDetectSettingsBridge(Activity activity) {
        this.activity = activity;
        this.prefs = ActivityAutoDetectManager.prefs(activity);
    }

    @JavascriptInterface
    public String getSettings() {
        JSONObject out = new JSONObject();
        try {
            out.put("walk", prefs.getBoolean(ActivityAutoDetectManager.KEY_WALK_ENABLED, false));
            out.put("run", prefs.getBoolean(ActivityAutoDetectManager.KEY_RUN_ENABLED, false));
            out.put("bike", prefs.getBoolean(ActivityAutoDetectManager.KEY_BIKE_ENABLED, false));
            out.put("startMode", prefs.getString(ActivityAutoDetectManager.KEY_START_MODE, "ask"));
            out.put("repromptMin", ActivityAutoDetectManager.sanitizeReprompt(
                    prefs.getInt(ActivityAutoDetectManager.KEY_REPROMPT_MIN, 5)));
            out.put("sensitivity", sanitizeSensitivity(
                    prefs.getString(ActivityAutoDetectManager.KEY_SENSITIVITY, "normal")));
            out.put("endMode", sanitizeEndMode(
                    prefs.getString(ActivityAutoDetectManager.KEY_END_MODE, "ask")));
            out.put("activityRecognition", ActivityAutoDetectManager.hasRecognitionPermission(activity));
            out.put("backgroundLocation", ActivityAutoDetectManager.hasBackgroundLocation(activity));
            out.put("notifications", Build.VERSION.SDK_INT < 33
                    || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED);
        } catch (Exception ignored) { }
        return out.toString();
    }

    @JavascriptInterface
    public String saveSettings(String rawJson) {
        JSONObject result = new JSONObject();
        try {
            JSONObject in = new JSONObject(rawJson == null ? "{}" : rawJson);
            boolean walk = in.optBoolean("walk", prefs.getBoolean(ActivityAutoDetectManager.KEY_WALK_ENABLED, false));
            boolean run = in.optBoolean("run", prefs.getBoolean(ActivityAutoDetectManager.KEY_RUN_ENABLED, false));
            boolean bike = in.optBoolean("bike", prefs.getBoolean(ActivityAutoDetectManager.KEY_BIKE_ENABLED, false));
            String startMode = "auto".equals(in.optString("startMode")) ? "auto" : "ask";
            int repromptMin = ActivityAutoDetectManager.sanitizeReprompt(in.optInt("repromptMin", 5));
            String sensitivity = sanitizeSensitivity(in.optString("sensitivity", "normal"));
            String endMode = sanitizeEndMode(in.optString("endMode", "ask"));

            prefs.edit()
                    .putBoolean(ActivityAutoDetectManager.KEY_WALK_ENABLED, walk)
                    .putBoolean(ActivityAutoDetectManager.KEY_RUN_ENABLED, run)
                    .putBoolean(ActivityAutoDetectManager.KEY_BIKE_ENABLED, bike)
                    .putString(ActivityAutoDetectManager.KEY_START_MODE, startMode)
                    .putInt(ActivityAutoDetectManager.KEY_REPROMPT_MIN, repromptMin)
                    .putString(ActivityAutoDetectManager.KEY_SENSITIVITY, sensitivity)
                    .putString(ActivityAutoDetectManager.KEY_END_MODE, endMode)
                    .apply();

            ActivityAutoDetectManager.syncRegistration(activity);
            result.put("ok", true);
        } catch (Exception e) {
            try { result.put("ok", false); } catch (Exception ignored) { }
        }
        return result.toString();
    }

    @JavascriptInterface
    public void openBackgroundLocationSettings() {
        activity.runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
            } catch (Exception ignored) { }
        });
    }

    @JavascriptInterface
    public void syncRegistration() {
        ActivityAutoDetectManager.syncRegistration(activity);
    }

    private static String sanitizeSensitivity(String value) {
        if ("fast".equals(value) || "accurate".equals(value)) return value;
        return "normal";
    }

    private static String sanitizeEndMode(String value) {
        return "auto".equals(value) ? "auto" : "ask";
    }
}
