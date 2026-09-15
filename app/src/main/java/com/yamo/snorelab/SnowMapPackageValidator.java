package com.yamo.snorelab;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

/** Validates local Snow map packages before detector/classifier use. */
public final class SnowMapPackageValidator {
    private SnowMapPackageValidator() {}

    public static JSONObject validate(Context context, String resortKey) {
        JSONObject out = new JSONObject();
        JSONArray issues = new JSONArray();
        try {
            JSONObject manifest = SnowOfflineMapStore.readManifest(context, resortKey);
            JSONObject map = SnowOfflineMapStore.readMapData(context, resortKey);
            boolean developerTest = manifest.optBoolean("developerTest", false);

            if (manifest.length() == 0) issues.put("manifest_missing");
            if (map.length() == 0) issues.put("map_missing");
            if (manifest.optInt("schemaVersion", -1) != SnowOfflineMapStore.SCHEMA_VERSION) issues.put("schema_version");
            if (clean(manifest.optString("resortKey")).isEmpty()) issues.put("resort_key");
            if (clean(manifest.optString("resortName")).isEmpty()) issues.put("resort_name");

            double lat = manifest.optDouble("centerLat", Double.NaN);
            double lon = manifest.optDouble("centerLon", Double.NaN);
            double radius = manifest.optDouble("geofenceRadiusM", Double.NaN);
            if (!Double.isFinite(lat) || lat < -90 || lat > 90) issues.put("center_lat");
            if (!Double.isFinite(lon) || lon < -180 || lon > 180) issues.put("center_lon");
            if (!Double.isFinite(radius) || radius < 100 || radius > 20_000) issues.put("geofence_radius");

            JSONArray boundary = map.optJSONArray("boundary");
            JSONArray trails = map.optJSONArray("trails");
            JSONArray lifts = map.optJSONArray("lifts");
            if (boundary == null || boundary.length() < 3) issues.put("boundary");
            if (trails == null) issues.put("trails");
            if (lifts == null) issues.put("lifts");

            boolean licenseReady = developerTest || licenseReady(manifest);
            if (!licenseReady) issues.put("osm_odbl_attribution");

            boolean valid = issues.length() == 0;
            out.put("valid", valid);
            out.put("developerTest", developerTest);
            out.put("licenseReady", licenseReady);
            out.put("productionReady", valid && !developerTest && licenseReady);
            out.put("issues", issues);
        } catch (Exception e) {
            try {
                issues.put("validation_error");
                out.put("valid", false).put("developerTest", false)
                        .put("licenseReady", false).put("productionReady", false).put("issues", issues);
            } catch (Exception ignored) { }
        }
        return out;
    }

    private static boolean licenseReady(JSONObject manifest) {
        String source = clean(manifest.optString("source")).toLowerCase();
        String attribution = clean(manifest.optString("attribution")).toLowerCase();
        String license = clean(manifest.optString("license")).toLowerCase();
        boolean sourceOk = source.contains("openstreetmap") || source.contains("osm");
        boolean attributionOk = attribution.contains("openstreetmap") || attribution.contains("osm");
        boolean licenseOk = license.contains("odbl") || attribution.contains("odbl");
        return sourceOk && attributionOk && licenseOk;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
