package com.yamo.snorelab;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Local-first ski/lift store.
 *
 * Ski routes and lift observations are owned by the phone. Historical lift
 * statistics are marked as provided only after a future server upload succeeds.
 * Realtime sharing is a separate preference and never changes the historical
 * provided marker.
 */
public final class SkiLiftStore {
    public static final int SCHEMA_VERSION = 1;
    private static final String PREFS = "yamone_ski_lift_v1";
    private static final String KEY_REALTIME = "realtime_exchange_enabled";
    private static final String FILE_SESSION = "session.json";
    private static final String FILE_LIFTS = "lift_observations.json";
    private static final String FILE_RESORT_STATS = "lift_stats.json";

    private SkiLiftStore() {}

    public static final class PendingObservation {
        public final File sessionDir;
        public final JSONObject observation;

        PendingObservation(File sessionDir, JSONObject observation) {
            this.sessionDir = sessionDir;
            this.observation = observation;
        }
    }

    public static File root(Context context) {
        File dir = new File(context.getFilesDir(), "activity/ski");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File sessionsRoot(Context context) {
        File dir = new File(root(context), "sessions");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File resortCacheRoot(Context context) {
        File dir = new File(root(context), "resort_cache");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static synchronized File createSession(Context context, long startMs, String sport) {
        String safeSport = "snowboard".equals(sport) ? "snowboard" : "ski";
        String time = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(startMs));
        String localId = UUID.randomUUID().toString();
        File dir = new File(sessionsRoot(context), time + "_" + localId.substring(0, 8));
        if (!dir.exists()) dir.mkdirs();

        JSONObject meta = new JSONObject();
        try {
            meta.put("schemaVersion", SCHEMA_VERSION);
            meta.put("localSessionId", localId);
            meta.put("status", "recording");
            meta.put("sport", safeSport);
            meta.put("startEpochMs", startMs);
            meta.put("endEpochMs", 0);
            meta.put("resortKey", "");
            meta.put("resortName", "");
            meta.put("createdAtEpochMs", System.currentTimeMillis());
            writeJson(new File(dir, FILE_SESSION), meta);
            writeJsonArray(new File(dir, FILE_LIFTS), new JSONArray());
        } catch (Exception ignored) {}
        return dir;
    }

    public static synchronized boolean completeSession(File sessionDir, long endMs, String resortKey, String resortName) {
        JSONObject meta = readSessionMeta(sessionDir);
        if (meta.length() == 0) return false;
        try {
            meta.put("status", "complete");
            meta.put("endEpochMs", Math.max(endMs, meta.optLong("startEpochMs", endMs)));
            meta.put("resortKey", clean(resortKey));
            meta.put("resortName", clean(resortName));
            meta.put("updatedAtEpochMs", System.currentTimeMillis());
            return writeJson(new File(sessionDir, FILE_SESSION), meta);
        } catch (Exception e) {
            return false;
        }
    }

    public static JSONObject readSessionMeta(File sessionDir) {
        if (sessionDir == null) return new JSONObject();
        return readJson(new File(sessionDir, FILE_SESSION));
    }

    public static List<File> listSessions(Context context) {
        File[] files = sessionsRoot(context).listFiles(File::isDirectory);
        if (files == null) return new ArrayList<>();
        List<File> out = new ArrayList<>();
        Collections.addAll(out, files);
        out.sort(Comparator.comparing(File::getName).reversed());
        return out;
    }

    /**
     * Adds one locally detected lift ride. No server operation occurs here.
     * historicalProvidedAtEpochMs remains zero until a future explicit
     * "리프트 정보 제공" upload has succeeded for this observation.
     */
    public static synchronized String appendLiftObservation(File sessionDir, JSONObject values) {
        if (sessionDir == null || values == null) return "";
        JSONArray all = readLiftObservations(sessionDir);
        String id = clean(values.optString("observationId", ""));
        if (!isUuid(id)) id = UUID.randomUUID().toString();

        JSONObject item = new JSONObject();
        try {
            item.put("schemaVersion", SCHEMA_VERSION);
            item.put("observationId", id);
            item.put("resortKey", clean(values.optString("resortKey", "")));
            item.put("resortName", clean(values.optString("resortName", "")));
            item.put("liftKey", clean(values.optString("liftKey", "")));
            item.put("liftName", clean(values.optString("liftName", "")));
            item.put("nameStatus", normalizeNameStatus(values.optString("nameStatus", "unknown")));
            item.put("waitStartEpochMs", nonNegative(values.optLong("waitStartEpochMs", 0)));
            item.put("rideStartEpochMs", nonNegative(values.optLong("rideStartEpochMs", 0)));
            item.put("rideEndEpochMs", nonNegative(values.optLong("rideEndEpochMs", 0)));
            item.put("waitDurationMs", nonNegative(values.optLong("waitDurationMs", 0)));
            item.put("rideDurationMs", nonNegative(values.optLong("rideDurationMs", 0)));
            putCoordinate(item, "lowerLat", values.optDouble("lowerLat", Double.NaN), -90, 90);
            putCoordinate(item, "lowerLon", values.optDouble("lowerLon", Double.NaN), -180, 180);
            putCoordinate(item, "upperLat", values.optDouble("upperLat", Double.NaN), -90, 90);
            putCoordinate(item, "upperLon", values.optDouble("upperLon", Double.NaN), -180, 180);
            putFinite(item, "lowerAltitudeM", values.optDouble("lowerAltitudeM", Double.NaN));
            putFinite(item, "upperAltitudeM", values.optDouble("upperAltitudeM", Double.NaN));
            putFinite(item, "ascentM", Math.max(0, values.optDouble("ascentM", 0)));
            double confidence = values.optDouble("confidence", 0);
            item.put("confidence", Math.max(0, Math.min(1, Double.isFinite(confidence) ? confidence : 0)));
            item.put("historicalProvidedAtEpochMs", 0);
            item.put("historicalProvideBatchId", "");
            item.put("createdAtEpochMs", System.currentTimeMillis());
            all.put(item);
            return writeJsonArray(new File(sessionDir, FILE_LIFTS), all) ? id : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static JSONArray readLiftObservations(File sessionDir) {
        if (sessionDir == null) return new JSONArray();
        return readJsonArray(new File(sessionDir, FILE_LIFTS));
    }

    public static List<PendingObservation> listPendingHistorical(Context context) {
        List<PendingObservation> out = new ArrayList<>();
        for (File dir : listSessions(context)) {
            JSONArray items = readLiftObservations(dir);
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                if (item.optLong("historicalProvidedAtEpochMs", 0) > 0) continue;
                String id = clean(item.optString("observationId", ""));
                if (!isUuid(id)) continue;
                try { out.add(new PendingObservation(dir, new JSONObject(item.toString()))); }
                catch (Exception ignored) {}
            }
        }
        return out;
    }

    public static int countPendingHistorical(Context context) {
        return listPendingHistorical(context).size();
    }

    public static int countAllObservations(Context context) {
        int count = 0;
        for (File dir : listSessions(context)) count += readLiftObservations(dir).length();
        return count;
    }

    /** Marks only confirmed observation IDs after a successful future upload. */
    public static synchronized int markHistoricalProvided(Context context, List<String> observationIds,
                                                          long providedAtEpochMs, String batchId) {
        if (observationIds == null || observationIds.isEmpty()) return 0;
        Set<String> wanted = new HashSet<>();
        for (String id : observationIds) if (isUuid(id)) wanted.add(id);
        if (wanted.isEmpty()) return 0;

        int marked = 0;
        long at = Math.max(1, providedAtEpochMs);
        String batch = clean(batchId);
        for (File dir : listSessions(context)) {
            JSONArray items = readLiftObservations(dir);
            boolean changed = false;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String id = clean(item.optString("observationId", ""));
                if (!wanted.contains(id) || item.optLong("historicalProvidedAtEpochMs", 0) > 0) continue;
                try {
                    item.put("historicalProvidedAtEpochMs", at);
                    item.put("historicalProvideBatchId", batch);
                    marked++;
                    changed = true;
                } catch (Exception ignored) {}
            }
            if (changed && !writeJsonArray(new File(dir, FILE_LIFTS), items)) return Math.max(0, marked - 1);
        }
        return marked;
    }

    /** Realtime exchange is independent from historical provided state. */
    public static boolean isRealtimeExchangeEnabled(Context context) {
        return prefs(context).getBoolean(KEY_REALTIME, false);
    }

    public static void setRealtimeExchangeEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_REALTIME, enabled).apply();
    }

    /** Cache only the selected/current resort's lift statistics payload. */
    public static synchronized boolean writeResortStatsCache(Context context, String resortKey, JSONObject payload) {
        String key = safeKey(resortKey);
        if (key.isEmpty() || payload == null) return false;
        File dir = new File(resortCacheRoot(context), key);
        if (!dir.exists()) dir.mkdirs();
        try {
            JSONObject wrapper = new JSONObject();
            wrapper.put("schemaVersion", SCHEMA_VERSION);
            wrapper.put("resortKey", clean(resortKey));
            wrapper.put("updatedAtEpochMs", System.currentTimeMillis());
            wrapper.put("payload", new JSONObject(payload.toString()));
            return writeJson(new File(dir, FILE_RESORT_STATS), wrapper);
        } catch (Exception e) {
            return false;
        }
    }

    public static JSONObject readResortStatsCache(Context context, String resortKey) {
        String key = safeKey(resortKey);
        if (key.isEmpty()) return new JSONObject();
        return readJson(new File(new File(resortCacheRoot(context), key), FILE_RESORT_STATS));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JSONObject readJson(File file) {
        if (file == null || !file.exists()) return new JSONObject();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) b.append(line).append('\n');
            return new JSONObject(b.toString());
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static JSONArray readJsonArray(File file) {
        if (file == null || !file.exists()) return new JSONArray();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) b.append(line).append('\n');
            return new JSONArray(b.toString());
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static boolean writeJson(File target, JSONObject value) {
        return writeTextAtomic(target, value == null ? "{}" : value.toString(2));
    }

    private static boolean writeJsonArray(File target, JSONArray value) {
        return writeTextAtomic(target, value == null ? "[]" : value.toString(2));
    }

    private static boolean writeTextAtomic(File target, String text) {
        if (target == null) return false;
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(temp, false))) {
            writer.write(text);
        } catch (Exception e) {
            return false;
        }
        if (target.exists() && !target.delete()) {
            temp.delete();
            return false;
        }
        if (temp.renameTo(target)) return true;
        temp.delete();
        return false;
    }

    private static void putCoordinate(JSONObject target, String key, double value, double min, double max) throws Exception {
        if (Double.isFinite(value) && value >= min && value <= max) target.put(key, value);
        else target.put(key, JSONObject.NULL);
    }

    private static void putFinite(JSONObject target, String key, double value) throws Exception {
        if (Double.isFinite(value)) target.put(key, value);
        else target.put(key, JSONObject.NULL);
    }

    private static long nonNegative(long value) {
        return Math.max(0, value);
    }

    private static boolean isUuid(String value) {
        try { UUID.fromString(value); return true; }
        catch (Exception e) { return false; }
    }

    private static String normalizeNameStatus(String value) {
        String v = clean(value).toLowerCase(Locale.US);
        if ("verified".equals(v) || "suggested".equals(v) || "correction_pending".equals(v)) return v;
        return "unknown";
    }

    private static String safeKey(String value) {
        String v = clean(value).toLowerCase(Locale.US).replaceAll("[^a-z0-9._-]", "_");
        while (v.contains("__")) v = v.replace("__", "_");
        return v.length() > 80 ? v.substring(0, 80) : v;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
