package com.yamo.snorelab;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Local-only day merger for Snow records.
 * Multiple completed recordings from the same resort and same local date become
 * one logical day record. The current session directory is retained so callers
 * can continue to reference the just-finished session id.
 */
public final class SnowDayMerger {
    private SnowDayMerger() {}

    public static File mergePriorSameDayIntoCurrent(Context context, File currentDir) {
        if (context == null || currentDir == null || !currentDir.isDirectory()) return currentDir;
        JSONObject current = SkiLiftStore.readSessionMeta(currentDir);
        if (!"complete".equals(current.optString("status", ""))) return currentDir;
        String resortKey = clean(current.optString("resortKey", ""));
        if (resortKey.isEmpty()) return currentDir;
        boolean simulated = current.optBoolean("developerSimulated", false);
        long currentStart = current.optLong("startEpochMs", 0L);
        LocalDate day = localDate(currentStart);
        if (day == null) return currentDir;

        List<File> merge = new ArrayList<>();
        for (File dir : SkiLiftStore.listSessions(context)) {
            if (dir.equals(currentDir)) continue;
            JSONObject meta = SkiLiftStore.readSessionMeta(dir);
            if (!"complete".equals(meta.optString("status", ""))) continue;
            if (!resortKey.equals(clean(meta.optString("resortKey", "")))) continue;
            if (simulated != meta.optBoolean("developerSimulated", false)) continue;
            LocalDate other = localDate(meta.optLong("startEpochMs", 0L));
            if (day.equals(other)) merge.add(dir);
        }
        if (merge.isEmpty()) return currentDir;

        try {
            List<RouteLine> routeLines = readRouteLines(currentDir);
            JSONArray lifts = SkiLiftStore.readLiftObservations(currentDir);
            JSONObject mergedMeta = new JSONObject(current.toString());
            int mergedCount = Math.max(1, mergedMeta.optInt("mergedSessionCount", 1));

            for (File oldDir : merge) {
                JSONObject old = SkiLiftStore.readSessionMeta(oldDir);
                mergeMeta(mergedMeta, old);
                routeLines.addAll(readRouteLines(oldDir));
                JSONArray oldLifts = SkiLiftStore.readLiftObservations(oldDir);
                for (int i = 0; i < oldLifts.length(); i++) {
                    JSONObject item = oldLifts.optJSONObject(i);
                    if (item != null) lifts.put(new JSONObject(item.toString()));
                }
                mergedCount += Math.max(1, old.optInt("mergedSessionCount", 1));
            }
            mergedMeta.put("mergedSessionCount", mergedCount);
            mergedMeta.put("updatedAtEpochMs", System.currentTimeMillis());

            routeLines.sort(Comparator.comparingLong(a -> a.timeMs));
            sortLiftArray(lifts);
            if (!writeRoute(currentDir, routeLines)) return currentDir;
            if (!writeJsonArray(new File(currentDir, "lift_observations.json"), lifts)) return currentDir;
            if (!writeJson(new File(currentDir, "session.json"), mergedMeta)) return currentDir;

            for (File oldDir : merge) deleteRecursive(oldDir);
        } catch (Exception ignored) {}
        return currentDir;
    }

    private static void mergeMeta(JSONObject target, JSONObject old) throws Exception {
        long start = minPositive(target.optLong("startEpochMs", 0), old.optLong("startEpochMs", 0));
        long end = Math.max(target.optLong("endEpochMs", 0), old.optLong("endEpochMs", 0));
        target.put("startEpochMs", start);
        target.put("endEpochMs", end);
        target.put("durationMs", sum(target, old, "durationMs"));
        target.put("activeDurationMs", sumEffective(target, old, "activeDurationMs", "durationMs"));
        target.put("pausedMs", sum(target, old, "pausedMs"));
        target.put("descentCount", (int) sum(target, old, "descentCount"));
        target.put("liftCount", (int) sum(target, old, "liftCount"));
        target.put("descentDistanceM", sum(target, old, "descentDistanceM"));
        target.put("descentVerticalM", sum(target, old, "descentVerticalM"));
        target.put("liftTimeMs", sum(target, old, "liftTimeMs"));
        target.put("waitTimeMs", sum(target, old, "waitTimeMs"));
        target.put("rejectedGpsPoints", sum(target, old, "rejectedGpsPoints"));
        target.put("maxSpeedKmh", Math.max(target.optDouble("maxSpeedKmh", 0), old.optDouble("maxSpeedKmh", 0)));
        if (target.optString("resortName", "").isEmpty()) target.put("resortName", old.optString("resortName", ""));
    }

    private static long sum(JSONObject a, JSONObject b, String key) {
        return Math.max(0L, a.optLong(key, 0L)) + Math.max(0L, b.optLong(key, 0L));
    }

    private static long sumEffective(JSONObject a, JSONObject b, String preferred, String fallback) {
        long av = a.has(preferred) ? a.optLong(preferred, 0L) : a.optLong(fallback, 0L);
        long bv = b.has(preferred) ? b.optLong(preferred, 0L) : b.optLong(fallback, 0L);
        return Math.max(0L, av) + Math.max(0L, bv);
    }

    private static long minPositive(long a, long b) {
        if (a <= 0) return b;
        if (b <= 0) return a;
        return Math.min(a, b);
    }

    private static LocalDate localDate(long epochMs) {
        if (epochMs <= 0) return null;
        try { return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate(); }
        catch (Exception e) { return null; }
    }

    private static final class RouteLine {
        final long timeMs;
        final String line;
        RouteLine(long timeMs, String line) { this.timeMs = timeMs; this.line = line; }
    }

    private static List<RouteLine> readRouteLines(File dir) {
        List<RouteLine> out = new ArrayList<>();
        File route = new File(dir, "route.csv");
        if (!route.isFile()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(route))) {
            String line;
            boolean header = true;
            while ((line = r.readLine()) != null) {
                if (header) { header = false; continue; }
                int comma = line.indexOf(',');
                if (comma <= 0) continue;
                try { out.add(new RouteLine(Long.parseLong(line.substring(0, comma)), line)); }
                catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static boolean writeRoute(File dir, List<RouteLine> lines) {
        File target = new File(dir, "route.csv");
        File temp = new File(dir, "route.csv.tmp");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(temp))) {
            w.write("time_ms,lat,lon,accuracy_m,altitude_m,speed_mps,state\n");
            for (RouteLine line : lines) { w.write(line.line); w.write('\n'); }
        } catch (Exception e) { return false; }
        if (target.exists() && !target.delete()) return false;
        return temp.renameTo(target);
    }

    private static void sortLiftArray(JSONArray array) throws Exception {
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o != null) list.add(new JSONObject(o.toString()));
        }
        list.sort(Comparator.comparingLong(a -> a.optLong("rideStartEpochMs", 0L)));
        while (array.length() > 0) array.remove(array.length() - 1);
        for (JSONObject o : list) array.put(o);
    }

    private static boolean writeJson(File file, JSONObject value) {
        try { return writeAtomic(file, value.toString(2)); }
        catch (Exception e) { return false; }
    }

    private static boolean writeJsonArray(File file, JSONArray value) {
        try { return writeAtomic(file, value.toString(2)); }
        catch (Exception e) { return false; }
    }

    private static boolean writeAtomic(File file, String text) {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(temp))) { w.write(text); }
        catch (Exception e) { return false; }
        if (file.exists() && !file.delete()) return false;
        return temp.renameTo(file);
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursive(child);
        }
        try { file.delete(); } catch (Exception ignored) {}
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
