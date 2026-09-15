package com.yamo.snorelab;

import android.content.Context;
import android.location.Location;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Final post-stop editor for the saved tail of a local movement record. */
final class MovementRecordEditor {
    private MovementRecordEditor() {}

    static JSONObject trimTail(Context context, File dir, int minutes) {
        JSONObject out = new JSONObject();
        try {
            if (minutes != 1 && minutes != 3 && minutes != 5) return result(out, false, "invalid_minutes");
            if (!isDirectSession(context, dir)) return result(out, false, "invalid_session");

            JSONObject original = WalkingStore.readMeta(dir);
            if (!"complete".equals(original.optString("status"))) return result(out, false, "not_completed");
            if (original.optBoolean("uploaded", false)) return result(out, false, "already_uploaded");
            if (original.optBoolean("tailTrimFinalized", false)) return result(out, false, "trim_finalized");

            long trimMs = minutes * 60_000L;
            long startMs = original.optLong("startEpochMs", 0L);
            long endMs = original.optLong("endEpochMs", 0L);
            long oldDurationMs = Math.max(0L, original.optLong("durationMs", 0L));
            long oldMovingMs = Math.max(0L, original.optLong("movingMs", 0L));
            long oldDistanceM = Math.max(0L, original.optLong("distanceM", 0L));
            if (startMs <= 0L || endMs <= startMs || oldDurationMs <= trimMs) {
                return result(out, false, "too_short");
            }

            long cutoffMs = endMs - trimMs;
            if (cutoffMs <= startMs) return result(out, false, "too_short");

            List<WalkingStore.Point> all = WalkingStore.readRoute(dir, 0);
            List<WalkingStore.Point> kept = new ArrayList<>();
            for (WalkingStore.Point point : all) {
                if (point.timeMs <= cutoffMs) kept.add(point);
            }
            if (oldDistanceM > 0L && kept.size() < 2) return result(out, false, "insufficient_route");

            RouteMetrics allMetrics = metrics(all);
            RouteMetrics keptMetrics = metrics(kept);
            if (oldDistanceM > 0L && allMetrics.distanceM <= 0.0) {
                return result(out, false, "insufficient_route");
            }

            double durationRatio = clamp01((oldDurationMs - trimMs) / (double) oldDurationMs);
            double distanceRatio = allMetrics.distanceM > 0.0
                    ? clamp01(keptMetrics.distanceM / allMetrics.distanceM) : durationRatio;
            double movingRatio = allMetrics.movingMs > 0L
                    ? clamp01(keptMetrics.movingMs / (double) allMetrics.movingMs) : durationRatio;

            long newDurationMs = Math.max(0L, oldDurationMs - trimMs);
            long newDistanceM = Math.min(oldDistanceM, Math.max(0L, Math.round(oldDistanceM * distanceRatio)));
            long newMovingMs = Math.min(newDurationMs,
                    Math.min(oldMovingMs, Math.max(0L, Math.round(oldMovingMs * movingRatio))));

            long oldSteps = Math.max(0L, original.optLong("steps", 0L));
            long newSteps = oldMovingMs > 0L
                    ? Math.min(oldSteps, Math.max(0L, Math.round(oldSteps * (newMovingMs / (double) oldMovingMs))))
                    : Math.min(oldSteps, Math.max(0L, Math.round(oldSteps * durationRatio)));

            double oldMaxSpeed = Math.max(0.0, original.optDouble("maxSpeedKmh", 0.0));
            double keptRawMax = Math.max(0.0, keptMetrics.maxSpeedMps * 3.6);
            double newMaxSpeed = kept.isEmpty() ? 0.0 : Math.min(oldMaxSpeed, keptRawMax);

            JSONObject edited = new JSONObject(original.toString());
            edited.put("endEpochMs", cutoffMs);
            edited.put("durationMs", newDurationMs);
            edited.put("movingMs", newMovingMs);
            edited.put("distanceM", newDistanceM);
            edited.put("steps", "cycling".equals(edited.optString("type")) ? 0L : newSteps);
            edited.put("maxSpeedKmh", newMaxSpeed);
            edited.put("splitsMs", trimmedSplits(original.optJSONArray("splitsMs"), newDistanceM));
            if (!kept.isEmpty()) edited.put("lastAltitudeM", kept.get(kept.size() - 1).altitude);
            edited.put("goalState", recomputeGoal(edited, newDistanceM, newDurationMs));

            scaleWalkRunFields(edited, newDistanceM, newMovingMs);
            scaleGpsShadowFields(edited, distanceRatio, movingRatio);

            edited.put("tailTrimFinalized", true);
            edited.put("tailTrimMinutes", minutes);
            edited.put("tailTrimOriginalEndEpochMs", endMs);
            edited.put("tailTrimAppliedAtEpochMs", System.currentTimeMillis());
            edited.put("tailTrimRecalculation", "local_route_ratio_v1");
            if (!"cycling".equals(edited.optString("type")) && oldSteps > 0L) {
                edited.put("tailTrimStepsEstimated", true);
            }
            if ("walkrun".equals(edited.optString("type"))) {
                edited.put("tailTrimWalkRunSegmentsEstimated", true);
            }

            if (!writeRoute(dir, kept)) return result(out, false, "route_write_failed");
            WalkingStore.writeMeta(dir, edited);
            JSONObject verify = WalkingStore.readMeta(dir);
            if (!verify.optBoolean("tailTrimFinalized", false)
                    || verify.optInt("tailTrimMinutes", 0) != minutes
                    || verify.optLong("endEpochMs", 0L) != cutoffMs) {
                writeRoute(dir, all);
                WalkingStore.writeMeta(dir, original);
                return result(out, false, "meta_write_failed");
            }

            result(out, true, "trimmed");
            out.put("minutes", minutes);
            out.put("cutoffEpochMs", cutoffMs);
            out.put("durationMs", newDurationMs);
            out.put("movingMs", newMovingMs);
            out.put("distanceM", newDistanceM);
            out.put("steps", edited.optLong("steps", 0L));
            return out;
        } catch (Exception e) {
            return result(out, false, "trim_failed");
        }
    }

    private static boolean isDirectSession(Context context, File dir) {
        if (context == null || dir == null || !dir.isDirectory()) return false;
        try {
            File root = WalkingStore.root(context).getCanonicalFile();
            File target = dir.getCanonicalFile();
            File parent = target.getParentFile();
            return parent != null && root.equals(parent.getCanonicalFile());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean writeRoute(File dir, List<WalkingStore.Point> points) {
        if (dir == null || points == null) return false;
        File route = new File(dir, "route.csv");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(route, false))) {
            w.write("time_ms,lat,lon,accuracy_m,altitude_m,speed_mps\n");
            for (WalkingStore.Point p : points) {
                w.write(String.format(Locale.US, "%d,%.7f,%.7f,%.1f,%.1f,%.3f\n",
                        p.timeMs, p.lat, p.lon, p.accuracy, p.altitude, p.speedMps));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static RouteMetrics metrics(List<WalkingStore.Point> points) {
        RouteMetrics result = new RouteMetrics();
        if (points == null || points.isEmpty()) return result;
        for (WalkingStore.Point p : points) result.maxSpeedMps = Math.max(result.maxSpeedMps, Math.max(0f, p.speedMps));
        float[] distance = new float[1];
        for (int i = 1; i < points.size(); i++) {
            WalkingStore.Point previous = points.get(i - 1);
            WalkingStore.Point current = points.get(i);
            long dt = current.timeMs - previous.timeMs;
            if (dt <= 0L || current.speedMps <= 0.05f) continue;
            Location.distanceBetween(previous.lat, previous.lon, current.lat, current.lon, distance);
            double d = Math.max(0.0, distance[0]);
            result.distanceM += d;
            result.movingMs += dt;
        }
        return result;
    }

    private static JSONArray trimmedSplits(JSONArray original, long newDistanceM) {
        JSONArray out = new JSONArray();
        if (original == null) return out;
        int keep = Math.min(original.length(), (int) Math.max(0L, newDistanceM / 1000L));
        for (int i = 0; i < keep; i++) out.put(Math.max(0L, original.optLong(i, 0L)));
        return out;
    }

    private static void scaleWalkRunFields(JSONObject meta, long newDistanceM, long newMovingMs) {
        if (!"walkrun".equals(meta.optString("type"))) return;
        long oldWalkD = Math.max(0L, meta.optLong("walkingDistanceM", 0L));
        long oldRunD = Math.max(0L, meta.optLong("runningDistanceM", 0L));
        long dTotal = oldWalkD + oldRunD;
        if (dTotal > 0L) {
            long walk = Math.max(0L, Math.min(newDistanceM, Math.round(newDistanceM * (oldWalkD / (double) dTotal))));
            metaPut(meta, "walkingDistanceM", walk);
            metaPut(meta, "runningDistanceM", Math.max(0L, newDistanceM - walk));
        }

        long oldWalkM = Math.max(0L, meta.optLong("walkingMovingMs", 0L));
        long oldRunM = Math.max(0L, meta.optLong("runningMovingMs", 0L));
        long mTotal = oldWalkM + oldRunM;
        if (mTotal > 0L) {
            long walk = Math.max(0L, Math.min(newMovingMs, Math.round(newMovingMs * (oldWalkM / (double) mTotal))));
            metaPut(meta, "walkingMovingMs", walk);
            metaPut(meta, "runningMovingMs", Math.max(0L, newMovingMs - walk));
        }
    }

    private static void scaleGpsShadowFields(JSONObject meta, double distanceRatio, double movingRatio) {
        if (meta.has("gpsShadowDistanceM")) {
            metaPut(meta, "gpsShadowDistanceM", Math.max(0L,
                    Math.round(Math.max(0L, meta.optLong("gpsShadowDistanceM", 0L)) * distanceRatio)));
        }
        if (meta.has("gpsShadowDurationMs")) {
            metaPut(meta, "gpsShadowDurationMs", Math.max(0L,
                    Math.round(Math.max(0L, meta.optLong("gpsShadowDurationMs", 0L)) * movingRatio)));
        }
        if (meta.has("gpsShadowSegments")) {
            metaPut(meta, "gpsShadowSegments", Math.max(0,
                    (int) Math.round(Math.max(0, meta.optInt("gpsShadowSegments", 0)) * distanceRatio)));
        }
    }

    private static String recomputeGoal(JSONObject meta, long distanceM, long durationMs) {
        long goalDistanceM = Math.max(0L, meta.optLong("goalDistanceM", 0L));
        long goalTimeMs = Math.max(0L, meta.optLong("goalTimeMs", 0L));
        if (goalDistanceM > 0L && distanceM >= goalDistanceM && (goalTimeMs <= 0L || durationMs <= goalTimeMs)) return "SUCCESS";
        if (goalDistanceM == 0L && goalTimeMs > 0L && durationMs >= goalTimeMs) return "SUCCESS";
        if (goalDistanceM > 0L && goalTimeMs > 0L && durationMs > goalTimeMs) return "TIMEOUT";
        return "ACTIVE";
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static void metaPut(JSONObject meta, String key, Object value) {
        try { meta.put(key, value); } catch (Exception ignored) { }
    }

    private static JSONObject result(JSONObject out, boolean ok, String status) {
        try { out.put("ok", ok).put("status", status); } catch (Exception ignored) { }
        return out;
    }

    private static final class RouteMetrics {
        double distanceM;
        long movingMs;
        float maxSpeedMps;
    }
}
