package com.yamo.snorelab;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/** Native bridge for the renewed v0.26 sleep UI. All sleep data stays local unless an existing explicit upload action is used. */
public final class SleepBridge {
    private static final int REQUEST_SLEEP_PERMISSIONS = 6132;
    private static final String KEY_CURRENT_DBFS = "sleep_current_dbfs";
    private static final String KEY_CURRENT_SCORE = "sleep_current_score";
    private static final String KEY_CURRENT_CANDIDATE = "sleep_current_candidate";
    private static final String KEY_SNORE_ENABLED = "snore_detection_enabled";

    private final Activity activity;
    private final SharedPreferences prefs;
    private MediaPlayer player;
    private String playingSessionId = "";
    private int playingEventIndex = -1;

    public SleepBridge(Activity activity) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences(SleepRecorderService.PREFS, Activity.MODE_PRIVATE);
    }

    @JavascriptInterface
    public String getPermissionState() {
        JSONObject out = new JSONObject();
        try {
            out.put("microphone", granted(Manifest.permission.RECORD_AUDIO));
            out.put("notifications", Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS));
        } catch (Exception ignored) { }
        return out.toString();
    }

    @JavascriptInterface
    public String requestPermissions() {
        ArrayList<String> wanted = new ArrayList<>();
        if (!granted(Manifest.permission.RECORD_AUDIO)) wanted.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        activity.runOnUiThread(() -> {
            if (!wanted.isEmpty() && !activity.isFinishing() && !activity.isDestroyed()) {
                activity.requestPermissions(wanted.toArray(new String[0]), REQUEST_SLEEP_PERMISSIONS);
            }
        });
        return result(true, wanted.isEmpty() ? "already_granted" : "requested").toString();
    }

    @JavascriptInterface
    public String start() {
        if (!granted(Manifest.permission.RECORD_AUDIO)) return result(false, "microphone_permission_required").toString();
        if (prefs.getBoolean(SleepRecorderService.KEY_RECORDING, false)) return result(true, "already_recording").toString();
        activity.runOnUiThread(() -> {
            Intent intent = new Intent(activity, SleepRecorderService.class).setAction(SleepRecorderService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) activity.startForegroundService(intent);
            else activity.startService(intent);
        });
        return result(true, "starting").toString();
    }

    @JavascriptInterface
    public String stop() {
        JSONObject out = result(true, "stopping");
        try { out.put("sessionId", prefs.getString(SleepRecorderService.KEY_SESSION_ID, "")); } catch (Exception ignored) { }
        activity.runOnUiThread(() -> {
            Intent intent = new Intent(activity, SleepRecorderService.class).setAction(SleepRecorderService.ACTION_STOP);
            try { activity.startService(intent); } catch (Exception ignored) { }
        });
        return out.toString();
    }

    @JavascriptInterface
    public String getState() {
        JSONObject out = new JSONObject();
        try {
            boolean recording = prefs.getBoolean(SleepRecorderService.KEY_RECORDING, false);
            String id = prefs.getString(SleepRecorderService.KEY_SESSION_ID, "");
            long start = prefs.getLong(SleepRecorderService.KEY_START_MS, 0L);
            out.put("recording", recording);
            out.put("sessionId", id == null ? "" : id);
            out.put("startMs", start);
            out.put("elapsedMs", recording && start > 0 ? Math.max(0L, System.currentTimeMillis() - start) : 0L);
            float dbfs = prefs.getFloat(KEY_CURRENT_DBFS, Float.NaN);
            float score = prefs.getFloat(KEY_CURRENT_SCORE, Float.NaN);
            out.put("currentDbfs", Float.isNaN(dbfs) ? JSONObject.NULL : dbfs);
            out.put("currentScore", Float.isNaN(score) ? JSONObject.NULL : score);
            out.put("currentCandidate", prefs.getBoolean(KEY_CURRENT_CANDIDATE, false));
            File dir = sessionDir(id);
            JSONObject meta = dir == null ? new JSONObject() : SessionStore.readMeta(dir);
            out.put("candidateCount", Math.max(0, meta.optInt("eventCount", 0)));
            out.put("snoreDetection", prefs.getBoolean(KEY_SNORE_ENABLED, true));
        } catch (Exception ignored) { }
        return out.toString();
    }

    @JavascriptInterface
    public String getSettings() {
        JSONObject out = new JSONObject();
        try {
            int sensitivity = Math.max(0, Math.min(100, prefs.getInt("sensitivity", 65)));
            out.put("snoreDetection", prefs.getBoolean(KEY_SNORE_ENABLED, true));
            out.put("sensitivity", sensitivity >= 78 ? "high" : sensitivity <= 52 ? "low" : "normal");
            out.put("candidateClips", prefs.getBoolean("save_candidate_clips", true));
        } catch (Exception ignored) { }
        return out.toString();
    }

    @JavascriptInterface
    public String saveSettings(String rawJson) {
        try {
            JSONObject in = new JSONObject(rawJson == null ? "{}" : rawJson);
            String level = in.optString("sensitivity", "normal");
            int sensitivity = "high".equals(level) ? 85 : "low".equals(level) ? 45 : 65;
            boolean enabled = in.optBoolean("snoreDetection", true);
            prefs.edit()
                    .putBoolean(KEY_SNORE_ENABLED, enabled)
                    .putInt("sensitivity", sensitivity)
                    .putBoolean("save_candidate_clips", true)
                    .apply();
            return result(true, "saved").toString();
        } catch (Exception e) {
            return result(false, "invalid_settings").toString();
        }
    }

    @JavascriptInterface
    public String getStorageInfo() {
        JSONObject out = new JSONObject();
        try {
            File root = SessionStore.sessionsRoot(activity);
            List<File> sessions = SessionStore.listSessions(activity);
            int complete = 0;
            for (File dir : sessions) if ("complete".equals(SessionStore.readMeta(dir).optString("status"))) complete++;
            out.put("bytes", SessionStore.folderSize(root));
            out.put("records", complete);
            out.put("recording", prefs.getBoolean(SleepRecorderService.KEY_RECORDING, false));
        } catch (Exception ignored) { }
        return out.toString();
    }

    @JavascriptInterface
    public String deleteAll() {
        if (prefs.getBoolean(SleepRecorderService.KEY_RECORDING, false)) return result(false, "recording").toString();
        stopPlaybackInternal();
        SessionStore.deleteAll(activity);
        return result(true, "deleted").toString();
    }

    @JavascriptInterface
    public String listRecords(int requestedMax) {
        JSONArray records = new JSONArray();
        int max = clamp(requestedMax, 1, 200);
        try {
            for (File dir : SessionStore.listSessions(activity)) {
                if (records.length() >= max) break;
                JSONObject meta = SessionStore.readMeta(dir);
                if (!"complete".equals(meta.optString("status"))) continue;
                JSONArray events = SessionStore.readEvents(dir);
                long candidateMs = 0L;
                for (int i = 0; i < events.length(); i++) {
                    JSONObject e = events.optJSONObject(i);
                    if (e != null) candidateMs += Math.max(0L, e.optLong("durationMs", 0L));
                }
                JSONObject r = new JSONObject();
                r.put("sessionId", dir.getName());
                r.put("startEpochMs", meta.optLong("startEpochMs", 0L));
                r.put("endEpochMs", meta.optLong("endEpochMs", 0L));
                r.put("durationMs", Math.max(0L, meta.optLong("durationMs", 0L)));
                r.put("candidateCount", Math.max(0, meta.optInt("eventCount", events.length())));
                r.put("candidateDurationMs", candidateMs);
                r.put("reviewedCount", Math.max(0, meta.optInt("reviewedCount", 0)));
                r.put("confirmedCount", Math.max(0, meta.optInt("snoreConfirmedCount", 0)));
                r.put("rejectedCount", Math.max(0, meta.optInt("snoreRejectedCount", 0)));
                r.put("uncertainCount", Math.max(0, meta.optInt("uncertainCount", 0)));
                r.put("storageBytes", SessionStore.folderSize(dir));
                records.put(r);
            }
        } catch (Exception ignored) { }
        JSONObject out = new JSONObject();
        try { out.put("records", records).put("count", records.length()); } catch (Exception ignored) { }
        return out.toString();
    }

    @JavascriptInterface
    public String getActiveGraph(int requestedMaxPoints) {
        String id = prefs.getString(SleepRecorderService.KEY_SESSION_ID, "");
        File dir = sessionDir(id);
        if (dir == null) return emptyGraph().toString();
        return readFrames(dir, clamp(requestedMaxPoints, 20, 240)).toString();
    }

    @JavascriptInterface
    public String getRecordDetail(String sessionId, int requestedMaxPoints) {
        JSONObject out = new JSONObject();
        try {
            File dir = sessionDir(sessionId);
            if (dir == null) return out.put("found", false).toString();
            JSONObject meta = SessionStore.readMeta(dir);
            if (!"complete".equals(meta.optString("status"))) return out.put("found", false).toString();
            JSONArray source = SessionStore.readEvents(dir);
            JSONArray events = new JSONArray();
            long candidateMs = 0L;
            for (int i = 0; i < source.length(); i++) {
                JSONObject e = source.optJSONObject(i);
                if (e == null) continue;
                candidateMs += Math.max(0L, e.optLong("durationMs", 0L));
                JSONObject clean = new JSONObject();
                clean.put("index", i);
                clean.put("displayNumber", e.optInt("displayNumber", i + 1));
                clean.put("startOffsetMs", Math.max(0L, e.optLong("startOffsetMs", 0L)));
                clean.put("endOffsetMs", Math.max(0L, e.optLong("endOffsetMs", 0L)));
                clean.put("durationMs", Math.max(0L, e.optLong("durationMs", 0L)));
                clean.put("scoreAvg", e.optDouble("scoreAvg", 0));
                clean.put("scoreMax", e.optDouble("scoreMax", 0));
                clean.put("dbfsAvg", e.optDouble("dbfsAvg", -120));
                clean.put("dbfsMax", e.optDouble("dbfsMax", -120));
                clean.put("reviewLabel", e.optString("reviewLabel", "UNREVIEWED"));
                String clip = e.optString("clipFile", "");
                clean.put("hasClip", !clip.isEmpty() && new File(dir, clip).isFile());
                clean.put("clipDeleted", e.optBoolean("clipDeleted", false));
                events.put(clean);
            }
            JSONObject frames = readFrames(dir, clamp(requestedMaxPoints, 30, 360));
            out.put("found", true);
            out.put("sessionId", dir.getName());
            out.put("startEpochMs", meta.optLong("startEpochMs", 0L));
            out.put("endEpochMs", meta.optLong("endEpochMs", 0L));
            out.put("durationMs", Math.max(0L, meta.optLong("durationMs", 0L)));
            out.put("candidateCount", source.length());
            out.put("candidateDurationMs", candidateMs);
            out.put("reviewedCount", Math.max(0, meta.optInt("reviewedCount", 0)));
            out.put("confirmedCount", Math.max(0, meta.optInt("snoreConfirmedCount", 0)));
            out.put("rejectedCount", Math.max(0, meta.optInt("snoreRejectedCount", 0)));
            out.put("uncertainCount", Math.max(0, meta.optInt("uncertainCount", 0)));
            out.put("events", events);
            out.put("graph", frames.optJSONArray("points"));
            out.put("averageDbfs", frames.opt("averageDbfs"));
            out.put("maxDbfs", frames.opt("maxDbfs"));
            out.put("frameCount", frames.optInt("frameCount", 0));
            out.put("storageBytes", SessionStore.folderSize(dir));
        } catch (Exception e) {
            try { out.put("found", false).put("error", "read_failed"); } catch (Exception ignored) { }
        }
        return out.toString();
    }

    @JavascriptInterface
    public String reviewEvent(String sessionId, int eventIndex, String label) {
        if (!("UNREVIEWED".equals(label) || "SNORE".equals(label) || "NOT_SNORE".equals(label) || "UNCERTAIN".equals(label))) {
            return result(false, "invalid_label").toString();
        }
        File dir = sessionDir(sessionId);
        if (dir == null) return result(false, "not_found").toString();
        try {
            SessionStore.reviewEvent(dir, eventIndex, label);
            return result(true, "saved").toString();
        } catch (Exception e) {
            return result(false, "save_failed").toString();
        }
    }

    @JavascriptInterface
    public String deleteClip(String sessionId, int eventIndex) {
        File dir = sessionDir(sessionId);
        if (dir == null) return result(false, "not_found").toString();
        try {
            JSONArray events = SessionStore.readEvents(dir);
            if (eventIndex < 0 || eventIndex >= events.length()) return result(false, "not_found").toString();
            JSONObject event = events.optJSONObject(eventIndex);
            if (event == null) return result(false, "not_found").toString();
            String clip = event.optString("clipFile", "");
            if (!clip.isEmpty()) {
                File file = new File(dir, clip);
                if (isInside(dir, file) && file.isFile()) file.delete();
            }
            event.put("clipFile", JSONObject.NULL);
            event.put("clipDeleted", true);
            SessionStore.writeJson(new File(dir, "events.json"), events);
            if (playingSessionId.equals(sessionId) && playingEventIndex == eventIndex) stopPlaybackInternal();
            return result(true, "deleted").toString();
        } catch (Exception e) {
            return result(false, "delete_failed").toString();
        }
    }

    @JavascriptInterface
    public String deleteRecord(String sessionId) {
        String active = prefs.getString(SleepRecorderService.KEY_SESSION_ID, "");
        if (prefs.getBoolean(SleepRecorderService.KEY_RECORDING, false) && sessionId != null && sessionId.equals(active)) {
            return result(false, "recording").toString();
        }
        File dir = sessionDir(sessionId);
        if (dir == null) return result(false, "not_found").toString();
        if (playingSessionId.equals(sessionId)) stopPlaybackInternal();
        SessionStore.deleteSession(dir);
        return result(true, "deleted").toString();
    }

    @JavascriptInterface
    public String playClip(String sessionId, int eventIndex) {
        File dir = sessionDir(sessionId);
        if (dir == null) return result(false, "not_found").toString();
        JSONArray events = SessionStore.readEvents(dir);
        JSONObject event = eventIndex >= 0 && eventIndex < events.length() ? events.optJSONObject(eventIndex) : null;
        if (event == null) return result(false, "not_found").toString();
        String rel = event.optString("clipFile", "");
        File clip = rel.isEmpty() ? null : new File(dir, rel);
        if (clip == null || !isInside(dir, clip) || !clip.isFile()) return result(false, "clip_missing").toString();
        activity.runOnUiThread(() -> {
            try {
                stopPlaybackInternal();
                MediaPlayer mp = new MediaPlayer();
                mp.setDataSource(clip.getAbsolutePath());
                mp.setOnCompletionListener(done -> stopPlaybackInternal());
                mp.prepare();
                mp.start();
                player = mp;
                playingSessionId = sessionId == null ? "" : sessionId;
                playingEventIndex = eventIndex;
            } catch (Exception e) {
                stopPlaybackInternal();
            }
        });
        return result(true, "starting").toString();
    }

    @JavascriptInterface
    public String pauseClip() {
        activity.runOnUiThread(() -> {
            try { if (player != null && player.isPlaying()) player.pause(); } catch (Exception ignored) { }
        });
        return result(true, "paused").toString();
    }

    @JavascriptInterface
    public String resumeClip() {
        activity.runOnUiThread(() -> {
            try { if (player != null && !player.isPlaying()) player.start(); } catch (Exception ignored) { }
        });
        return result(true, "resumed").toString();
    }

    @JavascriptInterface
    public String seekClip(int positionMs) {
        activity.runOnUiThread(() -> {
            try { if (player != null) player.seekTo(Math.max(0, Math.min(positionMs, player.getDuration()))); } catch (Exception ignored) { }
        });
        return result(true, "seeked").toString();
    }

    @JavascriptInterface
    public String stopPlayback() {
        activity.runOnUiThread(this::stopPlaybackInternal);
        return result(true, "stopped").toString();
    }

    @JavascriptInterface
    public String getPlaybackState() {
        JSONObject out = new JSONObject();
        try {
            MediaPlayer p = player;
            out.put("active", p != null);
            out.put("sessionId", playingSessionId);
            out.put("eventIndex", playingEventIndex);
            if (p != null) {
                boolean playing = false;
                int pos = 0, duration = 0;
                try { playing = p.isPlaying(); pos = p.getCurrentPosition(); duration = p.getDuration(); } catch (Exception ignored) { }
                out.put("playing", playing).put("positionMs", pos).put("durationMs", duration);
            } else {
                out.put("playing", false).put("positionMs", 0).put("durationMs", 0);
            }
        } catch (Exception ignored) { }
        return out.toString();
    }

    public void release() {
        activity.runOnUiThread(this::stopPlaybackInternal);
    }

    private void stopPlaybackInternal() {
        MediaPlayer p = player;
        player = null;
        playingSessionId = "";
        playingEventIndex = -1;
        if (p != null) {
            try { p.stop(); } catch (Exception ignored) { }
            try { p.release(); } catch (Exception ignored) { }
        }
    }

    private JSONObject readFrames(File dir, int maxPoints) {
        JSONObject out = emptyGraph();
        File file = new File(dir, "frames.csv");
        if (!file.isFile()) return out;
        ArrayList<FrameSample> samples = new ArrayList<>();
        double sum = 0.0;
        double max = -120.0;
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] c = line.split(",");
                if (c.length < 8) continue;
                try {
                    long offset = Long.parseLong(c[0]);
                    double dbfs = Double.parseDouble(c[1]);
                    double score = Double.parseDouble(c[5]);
                    boolean candidate = "1".equals(c[7].trim());
                    samples.add(new FrameSample(offset, dbfs, score, candidate));
                    sum += dbfs;
                    max = Math.max(max, dbfs);
                    count++;
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        JSONArray points = new JSONArray();
        if (!samples.isEmpty()) {
            int stride = Math.max(1, (int) Math.ceil(samples.size() / (double) Math.max(1, maxPoints)));
            for (int i = 0; i < samples.size(); i += stride) points.put(samples.get(i).json());
            FrameSample last = samples.get(samples.size() - 1);
            if (samples.size() > 1 && ((samples.size() - 1) % stride) != 0) points.put(last.json());
        }
        try {
            out.put("points", points);
            out.put("frameCount", count);
            out.put("averageDbfs", count == 0 ? JSONObject.NULL : sum / count);
            out.put("maxDbfs", count == 0 ? JSONObject.NULL : max);
        } catch (Exception ignored) { }
        return out;
    }

    private static JSONObject emptyGraph() {
        JSONObject out = new JSONObject();
        try { out.put("points", new JSONArray()).put("frameCount", 0).put("averageDbfs", JSONObject.NULL).put("maxDbfs", JSONObject.NULL); }
        catch (Exception ignored) { }
        return out;
    }

    private File sessionDir(String id) {
        if (id == null || id.trim().isEmpty()) return null;
        File root = SessionStore.sessionsRoot(activity);
        File dir = new File(root, id);
        if (!dir.isDirectory() || !isInside(root, dir)) return null;
        return dir;
    }

    private static boolean isInside(File root, File child) {
        try {
            String rp = root.getCanonicalPath() + File.separator;
            return child.getCanonicalPath().startsWith(rp);
        } catch (Exception e) { return false; }
    }

    private boolean granted(String permission) {
        return activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private static JSONObject result(boolean ok, String status) {
        JSONObject out = new JSONObject();
        try { out.put("ok", ok).put("status", status); } catch (Exception ignored) { }
        return out;
    }

    private static final class FrameSample {
        final long offsetMs;
        final double dbfs;
        final double score;
        final boolean candidate;
        FrameSample(long offsetMs, double dbfs, double score, boolean candidate) {
            this.offsetMs = offsetMs; this.dbfs = dbfs; this.score = score; this.candidate = candidate;
        }
        JSONObject json() {
            JSONObject out = new JSONObject();
            try { out.put("offsetMs", offsetMs).put("dbfs", dbfs).put("score", score).put("candidate", candidate); }
            catch (Exception ignored) { }
            return out;
        }
    }
}
