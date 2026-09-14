package com.yamo.snorelab;

import android.content.Context;
import android.location.Location;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Local Snow map-package store.
 *
 * Production packages are expected to be prepared from OSM source data into an
 * app-owned offline package. This class never saves public OSM raster tiles.
 * The v0.28 developer build can install a tiny synthetic package solely for UI/
 * detector testing before a real ski-resort field test.
 */
public final class SnowOfflineMapStore {
    public static final int SCHEMA_VERSION = 1;
    public static final String DEV_RESORT_KEY = "yamone-dev-snow";
    public static final String DEV_RESORT_NAME = "Snow 개발 테스트장";
    public static final double DEV_CENTER_LAT = 37.56650;
    public static final double DEV_CENTER_LON = 126.97800;

    private SnowOfflineMapStore() {}

    public static File root(Context context) {
        File dir = new File(SkiLiftStore.root(context), "offline_maps");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File resortDir(Context context, String resortKey) {
        String safe = safeKey(resortKey);
        if (safe.isEmpty()) return null;
        return new File(root(context), safe);
    }

    public static JSONArray listInstalled(Context context) {
        JSONArray out = new JSONArray();
        File[] dirs = root(context).listFiles(File::isDirectory);
        if (dirs == null) return out;
        List<File> list = new ArrayList<>();
        Collections.addAll(list, dirs);
        list.sort(Comparator.comparing(File::getName));
        for (File dir : list) {
            JSONObject m = readManifest(dir);
            if (m.length() == 0) continue;
            try {
                JSONObject clean = new JSONObject(m.toString());
                clean.put("installed", true);
                clean.put("bytes", folderSize(dir));
                out.put(clean);
            } catch (Exception ignored) {}
        }
        return out;
    }

    public static JSONObject readManifest(Context context, String resortKey) {
        File dir = resortDir(context, resortKey);
        return dir == null ? new JSONObject() : readManifest(dir);
    }

    public static JSONObject readMapData(Context context, String resortKey) {
        File dir = resortDir(context, resortKey);
        return dir == null ? new JSONObject() : readJson(new File(dir, "map.json"));
    }

    public static boolean isInstalled(Context context, String resortKey) {
        return readManifest(context, resortKey).length() > 0;
    }

    /** Install a tiny developer-only package. Never call this from a release surface. */
    public static JSONObject ensureDeveloperTestMap(Context context) {
        File dir = resortDir(context, DEV_RESORT_KEY);
        if (dir == null) return new JSONObject();
        if (!dir.exists()) dir.mkdirs();
        JSONObject manifest = new JSONObject();
        JSONObject map = new JSONObject();
        try {
            manifest.put("schemaVersion", SCHEMA_VERSION);
            manifest.put("resortKey", DEV_RESORT_KEY);
            manifest.put("resortName", DEV_RESORT_NAME);
            manifest.put("mapVersion", 1);
            manifest.put("centerLat", DEV_CENTER_LAT);
            manifest.put("centerLon", DEV_CENTER_LON);
            manifest.put("geofenceRadiusM", 1500);
            manifest.put("developerTest", true);
            manifest.put("source", "synthetic-developer-test");
            manifest.put("attribution", "Developer test geometry only. Real packages must retain OpenStreetMap attribution and ODbL compliance.");
            manifest.put("installedAtEpochMs", System.currentTimeMillis());

            JSONArray boundary = new JSONArray();
            boundary.put(point(DEV_CENTER_LAT - 0.009, DEV_CENTER_LON - 0.010));
            boundary.put(point(DEV_CENTER_LAT - 0.009, DEV_CENTER_LON + 0.010));
            boundary.put(point(DEV_CENTER_LAT + 0.009, DEV_CENTER_LON + 0.010));
            boundary.put(point(DEV_CENTER_LAT + 0.009, DEV_CENTER_LON - 0.010));
            boundary.put(point(DEV_CENTER_LAT - 0.009, DEV_CENTER_LON - 0.010));
            map.put("boundary", boundary);

            JSONArray trails = new JSONArray();
            trails.put(lineFeature("dev-slope-a", "테스트 슬로프 A", new double[][]{
                    {DEV_CENTER_LAT + 0.0042, DEV_CENTER_LON - 0.0030},
                    {DEV_CENTER_LAT + 0.0022, DEV_CENTER_LON - 0.0020},
                    {DEV_CENTER_LAT, DEV_CENTER_LON - 0.0010},
                    {DEV_CENTER_LAT - 0.0035, DEV_CENTER_LON + 0.0005}
            }));
            trails.put(lineFeature("dev-slope-b", "테스트 슬로프 B", new double[][]{
                    {DEV_CENTER_LAT + 0.0040, DEV_CENTER_LON + 0.0027},
                    {DEV_CENTER_LAT + 0.0020, DEV_CENTER_LON + 0.0015},
                    {DEV_CENTER_LAT - 0.0005, DEV_CENTER_LON + 0.0010},
                    {DEV_CENTER_LAT - 0.0032, DEV_CENTER_LON + 0.0002}
            }));
            map.put("trails", trails);

            JSONArray lifts = new JSONArray();
            lifts.put(lineFeature("dev-lift-a", "테스트 리프트 A", new double[][]{
                    {DEV_CENTER_LAT - 0.0036, DEV_CENTER_LON - 0.0010},
                    {DEV_CENTER_LAT + 0.0041, DEV_CENTER_LON - 0.0030}
            }));
            lifts.put(lineFeature("dev-lift-b", "테스트 리프트 B", new double[][]{
                    {DEV_CENTER_LAT - 0.0035, DEV_CENTER_LON + 0.0012},
                    {DEV_CENTER_LAT + 0.0040, DEV_CENTER_LON + 0.0027}
            }));
            map.put("lifts", lifts);
            map.put("source", "synthetic-developer-test");

            if (!writeJson(new File(dir, "manifest.json"), manifest)) return new JSONObject();
            if (!writeJson(new File(dir, "map.json"), map)) return new JSONObject();
            return manifest;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static boolean delete(Context context, String resortKey) {
        File dir = resortDir(context, resortKey);
        if (dir == null || !dir.exists()) return true;
        deleteRecursive(dir);
        return !dir.exists();
    }

    public static JSONObject detectInstalledResort(Context context, double lat, double lon) {
        JSONObject best = null;
        double bestDistance = Double.MAX_VALUE;
        JSONArray installed = listInstalled(context);
        for (int i = 0; i < installed.length(); i++) {
            JSONObject m = installed.optJSONObject(i);
            if (m == null) continue;
            double clat = m.optDouble("centerLat", Double.NaN);
            double clon = m.optDouble("centerLon", Double.NaN);
            double radius = Math.max(100.0, m.optDouble("geofenceRadiusM", 1200));
            if (!Double.isFinite(clat) || !Double.isFinite(clon)) continue;
            double d = distanceM(lat, lon, clat, clon);
            if (d <= radius && d < bestDistance) {
                best = m;
                bestDistance = d;
            }
        }
        if (best == null) return new JSONObject();
        try {
            JSONObject out = new JSONObject(best.toString());
            out.put("distanceM", bestDistance);
            return out;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /**
     * Classifies one descent using only the installed local package.
     * No package => UNMAPPED. Inside resort but not close to a mapped slope => UNKNOWN.
     * Outside the resort boundary/radius => TREE_OR_OUTSIDE.
     */
    public static JSONObject classifyDescent(Context context, String resortKey,
                                             List<SkiSessionAnalysis.RoutePoint> points) {
        JSONObject out = new JSONObject();
        try {
            out.put("type", "UNMAPPED");
            out.put("label", "미확인 활주");
            if (points == null || points.isEmpty()) return out;
            JSONObject manifest = readManifest(context, resortKey);
            JSONObject map = readMapData(context, resortKey);
            if (manifest.length() == 0 || map.length() == 0) return out;

            SkiSessionAnalysis.RoutePoint mid = points.get(points.size() / 2);
            boolean inside = isInsideBoundary(map.optJSONArray("boundary"), mid.lat, mid.lon);
            JSONArray trails = map.optJSONArray("trails");
            JSONObject nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            if (trails != null) {
                for (int i = 0; i < trails.length(); i++) {
                    JSONObject trail = trails.optJSONObject(i);
                    if (trail == null) continue;
                    double d = distanceToLine(mid.lat, mid.lon, trail.optJSONArray("points"));
                    if (d < nearestDistance) { nearestDistance = d; nearest = trail; }
                }
            }
            if (nearest != null && nearestDistance <= 85.0) {
                out.put("type", "SLOPE");
                out.put("label", nearest.optString("name", "슬로프"));
                out.put("featureId", nearest.optString("id", ""));
                out.put("distanceM", nearestDistance);
                return out;
            }
            if (!inside) {
                out.put("type", "TREE_OR_OUTSIDE");
                out.put("label", "트리런 / 경계 밖 미확인");
            } else {
                out.put("type", "UNKNOWN");
                out.put("label", "미확인 활주");
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static JSONObject readManifest(File dir) {
        return readJson(new File(dir, "manifest.json"));
    }

    private static JSONObject lineFeature(String id, String name, double[][] coords) throws Exception {
        JSONObject out = new JSONObject();
        out.put("id", id);
        out.put("name", name);
        JSONArray points = new JSONArray();
        for (double[] c : coords) points.put(point(c[0], c[1]));
        out.put("points", points);
        return out;
    }

    private static JSONArray point(double lat, double lon) {
        JSONArray p = new JSONArray();
        p.put(lat); p.put(lon);
        return p;
    }

    private static boolean isInsideBoundary(JSONArray polygon, double lat, double lon) {
        if (polygon == null || polygon.length() < 3) return true;
        boolean inside = false;
        for (int i = 0, j = polygon.length() - 1; i < polygon.length(); j = i++) {
            JSONArray pi = polygon.optJSONArray(i), pj = polygon.optJSONArray(j);
            if (pi == null || pj == null || pi.length() < 2 || pj.length() < 2) continue;
            double yi = pi.optDouble(0), xi = pi.optDouble(1);
            double yj = pj.optDouble(0), xj = pj.optDouble(1);
            boolean cross = ((yi > lat) != (yj > lat)) &&
                    (lon < (xj - xi) * (lat - yi) / Math.max(1e-12, (yj - yi)) + xi);
            if (cross) inside = !inside;
        }
        return inside;
    }

    private static double distanceToLine(double lat, double lon, JSONArray points) {
        if (points == null || points.length() == 0) return Double.MAX_VALUE;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < points.length(); i++) {
            JSONArray p = points.optJSONArray(i);
            if (p == null || p.length() < 2) continue;
            best = Math.min(best, distanceM(lat, lon, p.optDouble(0), p.optDouble(1)));
            if (i > 0) {
                JSONArray a = points.optJSONArray(i - 1);
                if (a != null && a.length() >= 2) {
                    best = Math.min(best, distanceToSegmentApprox(lat, lon,
                            a.optDouble(0), a.optDouble(1), p.optDouble(0), p.optDouble(1)));
                }
            }
        }
        return best;
    }

    private static double distanceToSegmentApprox(double lat, double lon,
                                                   double lat1, double lon1,
                                                   double lat2, double lon2) {
        // Local equirectangular projection is sufficient for ski-resort scale.
        double kx = Math.cos(Math.toRadians(lat)) * 111320.0;
        double ky = 110540.0;
        double ax = (lon1 - lon) * kx, ay = (lat1 - lat) * ky;
        double bx = (lon2 - lon) * kx, by = (lat2 - lat) * ky;
        double vx = bx - ax, vy = by - ay;
        double denom = vx * vx + vy * vy;
        if (denom <= 1e-9) return Math.sqrt(ax * ax + ay * ay);
        double t = Math.max(0, Math.min(1, -(ax * vx + ay * vy) / denom));
        double px = ax + t * vx, py = ay + t * vy;
        return Math.sqrt(px * px + py * py);
    }

    private static double distanceM(double lat1, double lon1, double lat2, double lon2) {
        float[] result = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, result);
        return result[0];
    }

    private static JSONObject readJson(File file) {
        if (file == null || !file.isFile()) return new JSONObject();
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) b.append(line).append('\n');
            return new JSONObject(b.toString());
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static boolean writeJson(File file, JSONObject value) {
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) file.getParentFile().mkdirs();
            File temp = new File(file.getParentFile(), file.getName() + ".tmp");
            try (BufferedWriter w = new BufferedWriter(new FileWriter(temp))) {
                w.write(value.toString(2));
            }
            if (file.exists() && !file.delete()) return false;
            return temp.renameTo(file);
        } catch (Exception e) {
            return false;
        }
    }

    private static long folderSize(File file) {
        if (file == null || !file.exists()) return 0;
        if (file.isFile()) return file.length();
        long total = 0;
        File[] kids = file.listFiles();
        if (kids != null) for (File k : kids) total += folderSize(k);
        return total;
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] kids = file.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        try { file.delete(); } catch (Exception ignored) {}
    }

    private static String safeKey(String value) {
        if (value == null) return "";
        String cleaned = value.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }
}
