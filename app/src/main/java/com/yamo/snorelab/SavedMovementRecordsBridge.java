package com.yamo.snorelab;

import android.app.Activity;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

/** Bridge exposing completed local movement records to the renewed Records tab. */
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
                records.put(summaryItem(dir, meta));
            }
        } catch (Exception ignored) { }

        JSONObject out = new JSONObject();
        try {
            out.put("records", records);
            out.put("count", records.length());
        } catch (Exception ignored) { }
        return out.toString();
    }

    /** Returns one completed record with enough route points to fit the full saved path on the detail map. */
    @JavascriptInterface
    public String getSavedRecordDetail(String requestedSessionId, int requestedMaxPoints) {
        JSONObject out = new JSONObject();
        String sessionId = requestedSessionId == null ? "" : requestedSessionId.trim();
        int maxPoints = Math.max(20, Math.min(800, requestedMaxPoints));
        if (sessionId.isEmpty()) {
            try { out.put("found", false); } catch (Exception ignored) { }
            return out.toString();
        }
        try {
            File dir = findSession(sessionId);
            if (dir == null) {
                out.put("found", false);
                return out.toString();
            }
            JSONObject meta = WalkingStore.readMeta(dir);
            if (!"complete".equals(meta.optString("status"))) {
                out.put("found", false);
                return out.toString();
            }
            out.put("found", true);
            out.put("record", summaryItem(dir, meta));
            out.put("points", routeJson(dir, maxPoints));
        } catch (Exception e) {
            try { out.put("found", false).put("error", "read_failed"); } catch (Exception ignored) { }
        }
        return out.toString();
    }

    /** Erases one completed local movement record and its stored GPS/activity values. */
    @JavascriptInterface
    public String deleteSavedRecord(String requestedSessionId) {
        JSONObject out = new JSONObject();
        String sessionId = requestedSessionId == null ? "" : requestedSessionId.trim();
        try {
            if (sessionId.isEmpty()) return result(out, false, "invalid_session").toString();
            File dir = findSession(sessionId);
            if (dir == null) return result(out, false, "not_found").toString();
            JSONObject meta = WalkingStore.readMeta(dir);
            if (!"complete".equals(meta.optString("status"))) {
                return result(out, false, "not_completed").toString();
            }
            boolean erased = WalkingStore.eraseSession(activity, dir);
            result(out, erased, erased ? "deleted" : "erase_failed");
            out.put("sessionId", sessionId);
            out.put("remainingCount", completedCount());
        } catch (Exception e) {
            result(out, false, "erase_failed");
        }
        return out.toString();
    }

    private JSONObject summaryItem(File dir, JSONObject meta) {
        JSONObject item = new JSONObject();
        try {
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
            item.put("tailTrimFinalized", meta.optBoolean("tailTrimFinalized", false));
            item.put("tailTrimMinutes", Math.max(0, meta.optInt("tailTrimMinutes", 0)));
        } catch (Exception ignored) { }
        return item;
    }

    private int completedCount() {
        int count = 0;
        for (File dir : WalkingStore.listSessions(activity)) {
            if ("complete".equals(WalkingStore.readMeta(dir).optString("status"))) count++;
        }
        return count;
    }

    private JSONObject result(JSONObject out, boolean ok, String status) {
        try { out.put("ok", ok).put("status", status); } catch (Exception ignored) { }
        return out;
    }

    private File findSession(String sessionId) {
        for (File dir : WalkingStore.listSessions(activity)) {
            if (dir != null && sessionId.equals(dir.getName())) return dir;
        }
        return null;
    }

    private JSONArray routeJson(File dir, int maxPoints) {
        JSONArray points = new JSONArray();
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
}
