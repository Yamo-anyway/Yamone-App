package com.yamo.snorelab;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Local-only route statistics shared by walk/run/cycle and hiking detail screens. */
public final class ActivityRouteAnalysis {
    public static final class Sample {
        public final long timeMs;
        public final double lat;
        public final double lon;
        public final float accuracyM;
        public final double altitudeM;
        public final float speedKmh;
        public final boolean altitudeValid;
        public final boolean speedValid;

        Sample(long timeMs, double lat, double lon, float accuracyM, double altitudeM,
               float speedKmh, boolean altitudeValid, boolean speedValid) {
            this.timeMs = timeMs;
            this.lat = lat;
            this.lon = lon;
            this.accuracyM = accuracyM;
            this.altitudeM = altitudeM;
            this.speedKmh = speedKmh;
            this.altitudeValid = altitudeValid;
            this.speedValid = speedValid;
        }
    }

    public static final class Result {
        public final List<Sample> samples;
        public final boolean hasAltitude;
        public final boolean hasSpeed;
        public final double minAltitudeM;
        public final double maxAltitudeM;
        public final double averageAltitudeM;
        public final double ascentM;
        public final double descentM;
        public final float averageSpeedKmh;
        public final float maxSpeedKmh;
        public final int gpsQualityPercent;
        public final long firstTimeMs;
        public final long lastTimeMs;

        Result(List<Sample> samples, boolean hasAltitude, boolean hasSpeed,
               double minAltitudeM, double maxAltitudeM, double averageAltitudeM,
               double ascentM, double descentM, float averageSpeedKmh, float maxSpeedKmh,
               int gpsQualityPercent, long firstTimeMs, long lastTimeMs) {
            this.samples = Collections.unmodifiableList(samples);
            this.hasAltitude = hasAltitude;
            this.hasSpeed = hasSpeed;
            this.minAltitudeM = minAltitudeM;
            this.maxAltitudeM = maxAltitudeM;
            this.averageAltitudeM = averageAltitudeM;
            this.ascentM = ascentM;
            this.descentM = descentM;
            this.averageSpeedKmh = averageSpeedKmh;
            this.maxSpeedKmh = maxSpeedKmh;
            this.gpsQualityPercent = gpsQualityPercent;
            this.firstTimeMs = firstTimeMs;
            this.lastTimeMs = lastTimeMs;
        }
    }

    private ActivityRouteAnalysis() {}

    public static Result analyze(File sessionDir, String type) {
        ArrayList<RawSample> raw = readRaw(sessionDir);
        ArrayList<Sample> out = new ArrayList<>();
        if (raw.isEmpty()) return empty();

        float maxSpeedMps = maxSpeedMps(type);
        int goodGps = 0;
        int gpsCount = 0;
        double smoothAltitude = Double.NaN;
        double previousAltitude = Double.NaN;
        long previousAltitudeTime = 0L;
        double minAltitude = Double.POSITIVE_INFINITY;
        double maxAltitude = Double.NEGATIVE_INFINITY;
        double altitudeSum = 0.0;
        int altitudeCount = 0;
        double ascent = 0.0;
        double descent = 0.0;
        double speedSum = 0.0;
        int speedCount = 0;
        float maxSpeed = 0f;

        for (RawSample r : raw) {
            gpsCount++;
            boolean goodAccuracy = Float.isNaN(r.accuracyM) || r.accuracyM <= 40f;
            if (goodAccuracy) goodGps++;

            boolean altitudeValid = goodAccuracy && r.altitudeM != 0.0 && !Double.isNaN(r.altitudeM)
                    && r.altitudeM > -500.0 && r.altitudeM < 9000.0;
            double altitude = Double.NaN;
            if (altitudeValid) {
                if (Double.isNaN(smoothAltitude)) smoothAltitude = r.altitudeM;
                else smoothAltitude = smoothAltitude * 0.82 + r.altitudeM * 0.18;
                altitude = smoothAltitude;

                boolean verticalJumpOk = true;
                if (!Double.isNaN(previousAltitude) && previousAltitudeTime > 0L) {
                    double dt = Math.max(1.0, (r.timeMs - previousAltitudeTime) / 1000.0);
                    double delta = altitude - previousAltitude;
                    double allowed = Math.max(8.0, dt * 4.0 + 5.0);
                    if (Math.abs(delta) > allowed) verticalJumpOk = false;
                    if (verticalJumpOk) {
                        // Ignore tiny GPS altitude flutter, but keep meaningful climbing/descending.
                        if (delta >= 0.8) ascent += delta;
                        else if (delta <= -0.8) descent += -delta;
                    }
                }
                if (verticalJumpOk) {
                    previousAltitude = altitude;
                    previousAltitudeTime = r.timeMs;
                    minAltitude = Math.min(minAltitude, altitude);
                    maxAltitude = Math.max(maxAltitude, altitude);
                    altitudeSum += altitude;
                    altitudeCount++;
                } else {
                    altitudeValid = false;
                    altitude = Double.NaN;
                }
            }

            boolean speedValid = goodAccuracy && r.speedMps >= 0f && r.speedMps <= maxSpeedMps;
            float speedKmh = speedValid ? r.speedMps * 3.6f : 0f;
            if (speedValid) {
                speedSum += speedKmh;
                speedCount++;
                maxSpeed = Math.max(maxSpeed, speedKmh);
            }

            out.add(new Sample(r.timeMs, r.lat, r.lon, r.accuracyM, altitude, speedKmh,
                    altitudeValid, speedValid));
        }

        boolean hasAltitude = altitudeCount >= 2;
        boolean hasSpeed = speedCount >= 2;
        return new Result(out, hasAltitude, hasSpeed,
                hasAltitude ? minAltitude : Double.NaN,
                hasAltitude ? maxAltitude : Double.NaN,
                hasAltitude ? altitudeSum / altitudeCount : Double.NaN,
                ascent, descent,
                hasSpeed ? (float) (speedSum / speedCount) : 0f,
                maxSpeed,
                gpsCount == 0 ? 0 : Math.round(goodGps * 100f / gpsCount),
                raw.get(0).timeMs, raw.get(raw.size() - 1).timeMs);
    }

    private static float maxSpeedMps(String type) {
        if ("cycling".equals(type)) return 25f;
        if ("running".equals(type) || "walkrun".equals(type)) return 7.5f;
        if ("hiking".equals(type)) return 8.5f;
        return 3.5f;
    }

    private static Result empty() {
        return new Result(new ArrayList<>(), false, false, Double.NaN, Double.NaN,
                Double.NaN, 0, 0, 0, 0, 0, 0, 0);
    }

    private static final class RawSample {
        long timeMs;
        double lat;
        double lon;
        float accuracyM;
        double altitudeM;
        float speedMps;
    }

    private static ArrayList<RawSample> readRaw(File dir) {
        ArrayList<RawSample> out = new ArrayList<>();
        if (dir == null) return out;
        File route = new File(dir, "route.csv");
        if (!route.exists()) return out;
        try (BufferedReader reader = new BufferedReader(new FileReader(route))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) { header = false; continue; }
                String[] p = line.split(",");
                if (p.length < 6) continue;
                try {
                    RawSample r = new RawSample();
                    r.timeMs = Long.parseLong(p[0]);
                    r.lat = Double.parseDouble(p[1]);
                    r.lon = Double.parseDouble(p[2]);
                    r.accuracyM = Float.parseFloat(p[3]);
                    r.altitudeM = Double.parseDouble(p[4]);
                    r.speedMps = Float.parseFloat(p[5]);
                    out.add(r);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return out;
    }
}
