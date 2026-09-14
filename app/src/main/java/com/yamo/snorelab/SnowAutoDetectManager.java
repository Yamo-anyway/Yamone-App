package com.yamo.snorelab;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Registers geofences only for locally installed Snow map packages in developer mode. */
public final class SnowAutoDetectManager {
    public static final String PREFS = "yamone_snow_settings_v1";
    public static final String KEY_AUTO_DETECT = "auto_detect";

    private SnowAutoDetectManager() {}

    public static boolean enabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_DETECT, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_DETECT, enabled).apply();
        sync(context);
    }

    public static void sync(Context context) {
        Context app = context.getApplicationContext();
        if (!SnowBridge.isDeveloperAvailable(app) || !enabled(app) || !hasRequiredLocation(app)) {
            remove(app);
            return;
        }
        JSONArray installed = SnowOfflineMapStore.listInstalled(app);
        List<Geofence> geofences = new ArrayList<>();
        for (int i = 0; i < installed.length(); i++) {
            JSONObject map = installed.optJSONObject(i);
            if (map == null) continue;
            String key = map.optString("resortKey", "").trim();
            double lat = map.optDouble("centerLat", Double.NaN);
            double lon = map.optDouble("centerLon", Double.NaN);
            float radius = (float) Math.max(100, Math.min(5000, map.optDouble("geofenceRadiusM", 1200)));
            if (key.isEmpty() || !Double.isFinite(lat) || !Double.isFinite(lon)) continue;
            geofences.add(new Geofence.Builder()
                    .setRequestId(key)
                    .setCircularRegion(lat, lon, radius)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setLoiteringDelay(0)
                    .build());
        }
        if (geofences.isEmpty()) { remove(app); return; }
        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofences)
                .build();
        GeofencingClient client = LocationServices.getGeofencingClient(app);
        try {
            client.removeGeofences(pendingIntent(app)).addOnCompleteListener(task -> {
                try { client.addGeofences(request, pendingIntent(app)); }
                catch (SecurityException ignored) {}
            });
        } catch (SecurityException ignored) {}
    }

    public static void remove(Context context) {
        try { LocationServices.getGeofencingClient(context.getApplicationContext()).removeGeofences(pendingIntent(context)); }
        catch (Exception ignored) {}
    }

    private static boolean hasRequiredLocation(Context context) {
        boolean fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fine) return false;
        if (Build.VERSION.SDK_INT >= 29) {
            return context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent i = new Intent(context.getApplicationContext(), SnowGeofenceReceiver.class)
                .setAction(SnowGeofenceReceiver.ACTION_GEOFENCE);
        return PendingIntent.getBroadcast(context.getApplicationContext(), 5828, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
