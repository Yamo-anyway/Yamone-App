package com.yamo.snorelab;

import android.app.Activity;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

/** Read-only bridge exposing completed local movement records to the renewed Records tab. */
public final class SavedMovementRecordsBridge {
    private final Activity activity;

    public SavedMovementRecordsBridge(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public String getSavedRecords(int requestedMaxRecords) {
        JSONArray records = new JSONArray();
        int maxRecords = Math.max(1, Math.min(100, requestedMaxRecords));
        try {
            List<File> sessions = WalkingStore.listSessions(activity);
            for (File dir : sessions) {
                if (records.length() >= maxRecords) break;
                JSONObject meta = WalkingStore.readMeta(dir);
                if (!"complete".equals(meta.optString("status"))) continue;

                JSONObject item = new JSONObject();
                item.put("sessionId", dir.getName());
                item.put("type", meta.optString("type", "walking"));
                item.put("startEpochMs", meta.optLong("startEpochMs", 0L));
                item.put("endEpochMs", meta.optLong("endEpochMs", 0L));
                item.put("durationMs", Math.max(0L, meta.optLong("durationMs", 0L)));
                item.put("movingMs", Math.max(0L, meta.optLong("movingMs", 0L)));
                item.put("distanceM", Math.max(0L, meta.optLong("distanceM", 0L)));
                item.put("steps", Math.max(0L, meta.optLong("steps", 0L)));
                item.put("maxSpeedKmh", Math.max(0.0, meta.optDouble("maxSpeedKmh", 0.0)));
                item.put("autoMotionModeLast", meta.optString("autoMotionModeLast", ""));
                item.put("splitsMs", meta.optJSONArray("splitsMs") == null ? new JSONArray() : meta.optJSONArray("splitsMs"));
                records.put(item);
            }
        } catch (Exception ignored) { }

        JSONObject out = new JSONObject();
        try {
            out.put("records", records);
            out.put("count", records.length());
        } catch (Exception ignored) { }
        return out.toString();
    }
}
