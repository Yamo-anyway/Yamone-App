package com.yamo.snorelab;

import android.location.Location;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/** Reads local ski route.csv and derives display-only summaries. */
public final class SkiSessionAnalysis {
    private SkiSessionAnalysis() {}

    public static final class RoutePoint {
        public final long timeMs;
        public final double lat;
        public final double lon;
        public final float accuracyM;
        public final double altitudeM;
        public final float speedMps;
        public final String state;

        RoutePoint(long timeMs, double lat, double lon, float accuracyM,
                   double altitudeM, float speedMps, String state) {
            this.timeMs = timeMs;
            this.lat = lat;
            this.lon = lon;
            this.accuracyM = accuracyM;
            this.altitudeM = altitudeM;
            this.speedMps = speedMps;
            this.state = state == null ? "" : state;
        }
    }

    public static final class DescentSummary {
        public final long startMs;
        public final long endMs;
        public final double distanceM;
        public final double verticalM;
        public final float maxSpeedKmh;
        public final float avgSpeedKmh;

        DescentSummary(long startMs, long endMs, double distanceM, double verticalM,
                       float maxSpeedKmh, float avgSpeedKmh) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.distanceM = distanceM;
            this.verticalM = verticalM;
            this.maxSpeedKmh = maxSpeedKmh;
            this.avgSpeedKmh = avgSpeedKmh;
        }
    }

    public static List<RoutePoint> readRoute(File sessionDir) {
        List<RoutePoint> out = new ArrayList<>();
        if (sessionDir == null) return out;
        File route = new File(sessionDir, "route.csv");
        if (!route.exists()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(route))) {
            String line;
            boolean first = true;
            while ((line = r.readLine()) != null) {
                if (first) { first = false; continue; }
                String[] p = line.split(",", -1);
                if (p.length < 7) continue;
                try {
                    long time = Long.parseLong(p[0]);
                    double lat = Double.parseDouble(p[1]);
                    double lon = Double.parseDouble(p[2]);
                    float acc = Float.parseFloat(p[3]);
                    double alt = Double.parseDouble(p[4]);
                    float speed = Float.parseFloat(p[5]);
                    if (lat < -90 || lat > 90 || lon < -180 || lon > 180) continue;
                    out.add(new RoutePoint(time, lat, lon, acc, alt, Math.max(0, speed), p[6]));
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static List<WalkingStore.Point> readMapPoints(File sessionDir, int maxPoints) {
        List<RoutePoint> route = readRoute(sessionDir);
        List<WalkingStore.Point> out = new ArrayList<>();
        if (route.isEmpty()) return out;
        int limit = Math.max(50, maxPoints);
        int stride = Math.max(1, (int) Math.ceil(route.size() / (double) limit));
        for (int i = 0; i < route.size(); i += stride) {
            RoutePoint p = route.get(i);
            out.add(new WalkingStore.Point(p.timeMs, p.lat, p.lon, p.accuracyM, p.altitudeM, p.speedMps));
        }
        RoutePoint last = route.get(route.size() - 1);
        if (out.isEmpty() || out.get(out.size() - 1).timeMs != last.timeMs) {
            out.add(new WalkingStore.Point(last.timeMs, last.lat, last.lon, last.accuracyM, last.altitudeM, last.speedMps));
        }
        return out;
    }

    public static List<DescentSummary> readDescents(File sessionDir) {
        List<RoutePoint> route = readRoute(sessionDir);
        List<DescentSummary> out = new ArrayList<>();
        List<RoutePoint> active = new ArrayList<>();
        for (RoutePoint p : route) {
            if (SkiRecorderService.STATE_DESCENT.equals(p.state)) {
                active.add(p);
            } else if (!active.isEmpty()) {
                addDescent(out, active);
                active.clear();
            }
        }
        if (!active.isEmpty()) addDescent(out, active);
        return out;
    }

    private static void addDescent(List<DescentSummary> out, List<RoutePoint> points) {
        if (points.size() < 2) return;
        RoutePoint first = points.get(0);
        RoutePoint last = points.get(points.size() - 1);
        long duration = Math.max(0, last.timeMs - first.timeMs);
        double distance = 0;
        double vertical = 0;
        float maxSpeed = 0;
        RoutePoint previous = null;
        for (RoutePoint p : points) {
            maxSpeed = Math.max(maxSpeed, p.speedMps * 3.6f);
            if (previous != null) {
                float[] result = new float[1];
                Location.distanceBetween(previous.lat, previous.lon, p.lat, p.lon, result);
                if (result[0] >= 0 && result[0] < 500) distance += result[0];
                double drop = previous.altitudeM - p.altitudeM;
                if (drop > 0.25 && drop < 80) vertical += drop;
            }
            previous = p;
        }
        // Ignore tiny transition fragments that are not useful as a ski run.
        if (duration < 4_000L || distance < 10.0) return;
        float avg = duration > 0 ? (float) ((distance / (duration / 1000.0)) * 3.6) : 0f;
        out.add(new DescentSummary(first.timeMs, last.timeMs, distance, vertical, maxSpeed, avg));
    }
}
