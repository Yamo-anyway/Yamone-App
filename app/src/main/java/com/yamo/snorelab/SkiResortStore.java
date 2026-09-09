package com.yamo.snorelab;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Small local store for the currently detected ski resort and cached lift matching. */
public final class SkiResortStore {
    private static final String PREFS = "yamone_ski_resort_v1";
    private static final String KEY_RESORT_KEY = "current_resort_key";
    private static final String KEY_RESORT_NAME = "current_resort_name";
    private static final String KEY_DETECTED_AT = "detected_at_ms";

    private SkiResortStore() {}

    public static void setCurrent(Context context, String key, String name) {
        prefs(context).edit()
                .putString(KEY_RESORT_KEY, clean(key))
                .putString(KEY_RESORT_NAME, clean(name))
                .putLong(KEY_DETECTED_AT, System.currentTimeMillis())
                .apply();
    }

    public static String currentKey(Context context) {
        return prefs(context).getString(KEY_RESORT_KEY, "");
    }

    public static String currentName(Context context) {
        return prefs(context).getString(KEY_RESORT_NAME, "");
    }

    public static long detectedAt(Context context) {
        return prefs(context).getLong(KEY_DETECTED_AT, 0);
    }

    public static void clearCurrent(Context context) {
        prefs(context).edit().remove(KEY_RESORT_KEY).remove(KEY_RESORT_NAME).remove(KEY_DETECTED_AT).apply();
    }

    /** Match one local observation against the cached current-resort lift endpoints. */
    public static JSONObject resolveCachedLift(Context context, JSONObject observation) {
        if (observation == null) return null;
        String resortKey = clean(observation.optString("resortKey", ""));
        if (resortKey.isEmpty()) resortKey = currentKey(context);
        if (resortKey.isEmpty()) return null;
        JSONObject wrapper = SkiLiftStore.readResortStatsCache(context, resortKey);
        JSONObject payload = wrapper.optJSONObject("payload");
        if (payload == null) return null;
        JSONArray lifts = payload.optJSONArray("lifts");
        if (lifts == null) return null;

        double lowerLat = observation.optDouble("lowerLat", Double.NaN);
        double lowerLon = observation.optDouble("lowerLon", Double.NaN);
        double upperLat = observation.optDouble("upperLat", Double.NaN);
        double upperLon = observation.optDouble("upperLon", Double.NaN);
        if (!finiteCoord(lowerLat, lowerLon) || !finiteCoord(upperLat, upperLon)) return null;

        JSONObject best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < lifts.length(); i++) {
            JSONObject lift = lifts.optJSONObject(i);
            if (lift == null) continue;
            double ll = lift.optDouble("lower_lat", Double.NaN);
            double lo = lift.optDouble("lower_lon", Double.NaN);
            double ul = lift.optDouble("upper_lat", Double.NaN);
            double uo = lift.optDouble("upper_lon", Double.NaN);
            if (!finiteCoord(ll, lo) || !finiteCoord(ul, uo)) continue;
            double d1 = distanceM(lowerLat, lowerLon, ll, lo);
            double d2 = distanceM(upperLat, upperLon, ul, uo);
            if (d1 <= 220 && d2 <= 260 && d1 + d2 < bestScore) {
                best = lift;
                bestScore = d1 + d2;
            }
        }
        return best;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean finiteCoord(double lat, double lon) {
        return Double.isFinite(lat) && Double.isFinite(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    private static double distanceM(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371000.0 * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)));
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
