package com.yamo.snorelab;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Foreground service that keeps location sharing alive while the screen is off.
 * Only a newly received latest location is sent; no local/server route history
 * is accumulated by this service.
 */
public final class LocationSharingService extends Service {
    public static final String ACTION_START = "com.yamo.snorelab.LOCATION_SHARE_START";

    private static final String CHANNEL_ID = "location_sharing_v1";
    private static final int NOTIFY_ID = 6201;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location latestLocation;
    private long latestLocationReceivedAt;
    private long lastReportedLocationReceivedAt;
    private int intervalSeconds = 60;
    private long shareUntilMs;
    private boolean configured;
    private boolean reportInFlight;

    private final Runnable reporter = new Runnable() {
        @Override public void run() {
            if (!configured) return;
            if (shareUntilMs > 0 && System.currentTimeMillis() >= shareUntilMs) {
                expireAndStop();
                return;
            }
            reportLatestIfNew();
            handler.postDelayed(this, Math.max(10_000L, intervalSeconds * 1000L));
        }
    };

    public static void start(Context context) {
        Intent intent = new Intent(context, LocationSharingService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, LocationSharingService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundCompat(buildNotification("위치 공유 상태를 확인하는 중…"));
        if (!hasLocationPermission()) {
            updateNotification("위치 권한이 필요합니다.");
            stopSelf();
            return START_NOT_STICKY;
        }
        loadRoomConfiguration();
        return START_STICKY;
    }

    private void loadRoomConfiguration() {
        LocationSharingApi.snapshot(this, new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                handler.post(() -> applyConfiguration(data));
            }

            @Override public void onFailure(String message) {
                handler.post(() -> updateNotification("서버 연결을 다시 시도합니다."));
                handler.postDelayed(LocationSharingService.this::loadRoomConfiguration, 30_000L);
            }
        });
    }

    private void applyConfiguration(JSONObject data) {
        if (!data.optBoolean("active", false)) {
            stopSelf();
            return;
        }

        JSONArray members = data.optJSONArray("members");
        JSONObject self = null;
        if (members != null) {
            for (int i = 0; i < members.length(); i++) {
                JSONObject member = members.optJSONObject(i);
                if (member != null && member.optBoolean("is_self", false)) {
                    self = member;
                    break;
                }
            }
        }
        if (self == null) {
            stopSelf();
            return;
        }

        intervalSeconds = clampInterval(self.optInt("update_interval_seconds", 60));
        shareUntilMs = parseInstant(self.optString("share_until", ""));
        configured = true;
        updateNotification("위치 공유 중 · " + intervalLabel(intervalSeconds) + "마다 갱신");
        startLocationUpdates();
        handler.removeCallbacks(reporter);
        handler.postDelayed(reporter, Math.max(10_000L, intervalSeconds * 1000L));
    }

    private void startLocationUpdates() {
        stopLocationUpdates();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null || !hasLocationPermission()) return;

        locationListener = this::onLocationChanged;
        long minTimeMs = Math.max(30_000L, intervalSeconds * 1000L);
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        minTimeMs,
                        0f,
                        locationListener,
                        Looper.getMainLooper());
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        minTimeMs,
                        0f,
                        locationListener,
                        Looper.getMainLooper());
            }
        } catch (SecurityException ignored) {}
    }

    private void onLocationChanged(Location location) {
        if (location == null) return;
        long locationTime = location.getTime() > 0 ? location.getTime() : System.currentTimeMillis();
        if (latestLocation != null) {
            long currentTime = latestLocation.getTime() > 0 ? latestLocation.getTime() : 0;
            if (locationTime < currentTime) return;
        }
        latestLocation = new Location(location);
        latestLocationReceivedAt = System.currentTimeMillis();
        if (lastReportedLocationReceivedAt == 0) reportLatestIfNew();
    }

    private void reportLatestIfNew() {
        if (!configured || reportInFlight || latestLocation == null) return;
        if (latestLocationReceivedAt <= lastReportedLocationReceivedAt) return;
        if (shareUntilMs > 0 && System.currentTimeMillis() >= shareUntilMs) {
            expireAndStop();
            return;
        }

        final long candidateReceipt = latestLocationReceivedAt;
        final Location candidate = new Location(latestLocation);
        Float accuracy = candidate.hasAccuracy() ? candidate.getAccuracy() : null;
        reportInFlight = true;
        LocationSharingApi.report(
                this,
                candidate.getLatitude(),
                candidate.getLongitude(),
                accuracy,
                new LocationSharingApi.JsonCallback() {
                    @Override public void onSuccess(JSONObject data) {
                        handler.post(() -> {
                            reportInFlight = false;
                            lastReportedLocationReceivedAt = Math.max(lastReportedLocationReceivedAt, candidateReceipt);
                            long serverUntil = parseInstant(data.optString("share_until", ""));
                            if (serverUntil > 0) shareUntilMs = serverUntil;
                            updateNotification("위치 공유 중 · " + intervalLabel(intervalSeconds) + "마다 갱신");
                        });
                    }

                    @Override public void onFailure(String message) {
                        handler.post(() -> {
                            reportInFlight = false;
                            String lower = message == null ? "" : message.toLowerCase(Locale.KOREAN);
                            if (lower.contains("참여 중인 위치 공유 방이 없습니다")) {
                                stopSelf();
                            } else {
                                updateNotification("네트워크 연결 대기 중 · 위치 공유 유지");
                            }
                        });
                    }
                });
    }

    private void expireAndStop() {
        configured = false;
        handler.removeCallbacks(reporter);
        LocationSharingApi.leave(this, new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) { stopSelf(); }
            @Override public void onFailure(String message) { stopSelf(); }
        });
    }

    private void stopLocationUpdates() {
        if (locationManager != null && locationListener != null) {
            try { locationManager.removeUpdates(locationListener); }
            catch (SecurityException ignored) {}
            catch (Exception ignored) {}
        }
        locationListener = null;
        locationManager = null;
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "위치 공유",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("위치 공유가 켜져 있을 때 표시됩니다.");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private Notification buildNotification(String message) {
        Intent open = new Intent(this, LocationSharingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                this,
                6201,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("야모네 위치 공유")
                .setContentText(message)
                .setContentIntent(pending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFY_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFY_ID, notification);
        }
    }

    private void updateNotification(String message) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFY_ID, buildNotification(message));
    }

    private int clampInterval(int seconds) {
        if (seconds <= 30) return 30;
        if (seconds <= 60) return 60;
        if (seconds <= 180) return 180;
        if (seconds <= 300) return 300;
        return 600;
    }

    private String intervalLabel(int seconds) {
        if (seconds < 60) return seconds + "초";
        return (seconds / 60) + "분";
    }

    private long parseInstant(String iso) {
        if (iso == null || iso.isEmpty()) return 0L;
        try { return Instant.parse(iso).toEpochMilli(); }
        catch (DateTimeParseException e) { return 0L; }
    }

    @Override public void onDestroy() {
        configured = false;
        reportInFlight = false;
        handler.removeCallbacksAndMessages(null);
        stopLocationUpdates();
        stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
