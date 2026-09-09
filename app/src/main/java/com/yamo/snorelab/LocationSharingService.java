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
 * Foreground service that keeps ephemeral location sharing alive while the screen is off.
 * Only the latest newly received location is sent. No route history is accumulated.
 */
public final class LocationSharingService extends Service {
    public static final String ACTION_START = "com.yamo.snorelab.LOCATION_SHARE_START";
    public static final String ACTION_EXTEND_30 = "com.yamo.snorelab.LOCATION_SHARE_EXTEND_30";
    public static final String ACTION_EXTEND_60 = "com.yamo.snorelab.LOCATION_SHARE_EXTEND_60";
    public static final String ACTION_STOP_SHARE = "com.yamo.snorelab.LOCATION_SHARE_STOP";

    private static final String CHANNEL_ID = "location_sharing_v1";
    private static final String WARNING_CHANNEL_ID = "location_sharing_expiry_v1";
    private static final int NOTIFY_ID = 6201;
    private static final int WARNING_NOTIFY_ID = 6202;
    private static final int ENDED_NOTIFY_ID = 6203;
    private static final long WARNING_BEFORE_MS = 5L * 60L * 1000L;
    private static final long EXPIRY_CHECK_MS = 15_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location latestLocation;
    private long latestLocationReceivedAt;
    private long lastReportedLocationReceivedAt;
    private int intervalSeconds = 60;
    private long shareUntilMs;
    private String roomName = "위치 공유 방";
    private int memberCount;
    private boolean configured;
    private boolean reportInFlight;
    private boolean warningShown;
    private boolean leaveInFlight;

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

    private final Runnable expiryChecker = new Runnable() {
        @Override public void run() {
            if (!configured) return;
            long remaining = shareUntilMs <= 0 ? Long.MAX_VALUE : shareUntilMs - System.currentTimeMillis();
            if (remaining <= 0) {
                expireAndStop();
                return;
            }
            if (remaining <= WARNING_BEFORE_MS && !warningShown) {
                warningShown = true;
                showExpiryWarning(remaining);
            } else if (remaining > WARNING_BEFORE_MS + 30_000L && warningShown) {
                warningShown = false;
                cancelWarning();
            }
            handler.postDelayed(this, EXPIRY_CHECK_MS);
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
        createChannels();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundCompat(buildServiceNotification("위치 공유 상태를 확인하는 중…"));

        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_EXTEND_30.equals(action)) {
            extendSharing(30);
            return START_STICKY;
        }
        if (ACTION_EXTEND_60.equals(action)) {
            extendSharing(60);
            return START_STICKY;
        }
        if (ACTION_STOP_SHARE.equals(action)) {
            requestStopSharing();
            return START_NOT_STICKY;
        }

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
            LocationSharingStateStore.clear(this);
            cancelWarning();
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
            LocationSharingStateStore.clear(this);
            cancelWarning();
            stopSelf();
            return;
        }

        roomName = data.optString("room_name", "위치 공유 방");
        memberCount = members == null ? 0 : members.length();
        intervalSeconds = clampInterval(self.optInt("update_interval_seconds", 60));
        String shareUntil = self.optString("share_until", "");
        shareUntilMs = parseInstant(shareUntil);
        configured = true;
        leaveInFlight = false;
        LocationSharingStateStore.update(this, roomName, shareUntil, intervalSeconds, memberCount);

        long remaining = shareUntilMs <= 0 ? Long.MAX_VALUE : shareUntilMs - System.currentTimeMillis();
        if (remaining <= 0) {
            expireAndStop();
            return;
        }
        if (remaining > WARNING_BEFORE_MS + 30_000L) {
            warningShown = false;
            cancelWarning();
        }

        updateNotification("위치 공유 중 · " + intervalLabel(intervalSeconds) + "마다 갱신");
        startLocationUpdates();
        handler.removeCallbacks(reporter);
        handler.postDelayed(reporter, Math.max(10_000L, intervalSeconds * 1000L));
        handler.removeCallbacks(expiryChecker);
        handler.post(expiryChecker);
    }

    private void startLocationUpdates() {
        stopLocationUpdates();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null || !hasLocationPermission()) return;

        locationListener = this::onLocationChanged;
        long minTimeMs = Math.max(10_000L, intervalSeconds * 1000L);
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
                            String shareUntil = shareUntilMs > 0 ? Instant.ofEpochMilli(shareUntilMs).toString() : "";
                            LocationSharingStateStore.update(LocationSharingService.this, roomName, shareUntil, intervalSeconds, memberCount);
                            updateNotification("위치 공유 중 · " + intervalLabel(intervalSeconds) + "마다 갱신");
                        });
                    }

                    @Override public void onFailure(String message) {
                        handler.post(() -> {
                            reportInFlight = false;
                            String lower = message == null ? "" : message.toLowerCase(Locale.KOREAN);
                            if (lower.contains("참여 중인 위치 공유 방이 없습니다")) {
                                LocationSharingStateStore.clear(LocationSharingService.this);
                                cancelWarning();
                                stopSelf();
                            } else {
                                updateNotification("네트워크 연결 대기 중 · 위치 공유 유지");
                            }
                        });
                    }
                });
    }

    private void extendSharing(int minutes) {
        updateNotification("위치 공유 시간을 연장하는 중…");
        LocationSharingApi.extend(this, minutes, new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                handler.post(() -> {
                    warningShown = false;
                    cancelWarning();
                    loadRoomConfiguration();
                    showBriefMessage("위치 공유 시간을 " + minutes + "분 연장했습니다.");
                });
            }

            @Override public void onFailure(String message) {
                handler.post(() -> {
                    updateNotification("시간 연장 실패 · 앱을 열어 다시 시도해 주세요.");
                    showExpiryWarning(Math.max(0L, shareUntilMs - System.currentTimeMillis()));
                });
            }
        });
    }

    private void requestStopSharing() {
        if (leaveInFlight) return;
        configured = false;
        leaveInFlight = true;
        handler.removeCallbacks(reporter);
        handler.removeCallbacks(expiryChecker);
        stopLocationUpdates();
        cancelWarning();
        updateNotification("위치 공유 종료 요청 중…");

        LocationSharingApi.leave(this, new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                handler.post(thisService()::finishUserRequestedStop);
            }

            @Override public void onFailure(String message) {
                handler.post(() -> {
                    String lower = message == null ? "" : message.toLowerCase(Locale.KOREAN);
                    if (lower.contains("참여 중인 위치 공유 방이 없습니다")) {
                        finishUserRequestedStop();
                        return;
                    }
                    leaveInFlight = false;
                    updateNotification("종료 요청 대기 중 · 네트워크 연결 후 다시 시도합니다.");
                    handler.postDelayed(this::retryStopSharing, 30_000L);
                });
            }

            private void retryStopSharing() {
                requestStopSharing();
            }

            private LocationSharingService thisService() {
                return LocationSharingService.this;
            }
        });
    }

    private void finishUserRequestedStop() {
        leaveInFlight = false;
        LocationSharingStateStore.clear(this);
        postEndedNotification("위치 공유를 종료했습니다.");
        stopSelf();
    }

    private void expireAndStop() {
        if (!configured && leaveInFlight) return;
        configured = false;
        leaveInFlight = true;
        handler.removeCallbacks(reporter);
        handler.removeCallbacks(expiryChecker);
        stopLocationUpdates();
        cancelWarning();

        LocationSharingApi.leave(this, new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                handler.post(LocationSharingService.this::finishExpiredStop);
            }
            @Override public void onFailure(String message) {
                handler.post(LocationSharingService.this::finishExpiredStop);
            }
        });
    }

    private void finishExpiredStop() {
        leaveInFlight = false;
        LocationSharingStateStore.clear(this);
        postEndedNotification("설정한 공유 시간이 끝나 위치 공유가 자동 종료되었습니다.");
        stopSelf();
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

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationChannel service = new NotificationChannel(
                CHANNEL_ID,
                "위치 공유",
                NotificationManager.IMPORTANCE_LOW);
        service.setDescription("위치 공유가 켜져 있을 때 표시됩니다.");
        manager.createNotificationChannel(service);

        NotificationChannel warning = new NotificationChannel(
                WARNING_CHANNEL_ID,
                "위치 공유 종료 알림",
                NotificationManager.IMPORTANCE_HIGH);
        warning.setDescription("위치 공유 종료 전과 종료 시 알려줍니다.");
        warning.enableVibration(true);
        manager.createNotificationChannel(warning);
    }

    private Notification buildServiceNotification(String message) {
        Intent open = new Intent(this, LocationSharingActivityV2.class)
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

    private void showExpiryWarning(long remainingMs) {
        long minutes = Math.max(1L, (remainingMs + 59_999L) / 60_000L);
        Intent choose = new Intent(this, LocationSharingTimeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent choosePending = PendingIntent.getActivity(
                this,
                6205,
                choose,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, WARNING_CHANNEL_ID)
                : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("위치 공유가 곧 종료됩니다")
                .setContentText(minutes + "분 후 자동 종료 · 연장하거나 종료할 수 있어요.")
                .setContentIntent(choosePending)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .addAction(R.drawable.ic_notification, "+30분", serviceAction(ACTION_EXTEND_30, 6230))
                .addAction(R.drawable.ic_notification, "+1시간", serviceAction(ACTION_EXTEND_60, 6260))
                .addAction(R.drawable.ic_notification, "종료", serviceAction(ACTION_STOP_SHARE, 6290))
                .build();

        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(WARNING_NOTIFY_ID, notification);
        } catch (SecurityException ignored) {}
    }

    private PendingIntent serviceAction(String action, int requestCode) {
        Intent intent = new Intent(this, LocationSharingService.class).setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (Build.VERSION.SDK_INT >= 26) return PendingIntent.getForegroundService(this, requestCode, intent, flags);
        return PendingIntent.getService(this, requestCode, intent, flags);
    }

    private void cancelWarning() {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.cancel(WARNING_NOTIFY_ID);
        } catch (Exception ignored) {}
    }

    private void showBriefMessage(String message) {
        try {
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, WARNING_CHANNEL_ID)
                    : new Notification.Builder(this);
            Notification n = builder
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("야모네 위치 공유")
                    .setContentText(message)
                    .setAutoCancel(true)
                    .setTimeoutAfter(5_000L)
                    .build();
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(WARNING_NOTIFY_ID, n);
        } catch (Exception ignored) {}
    }

    private void postEndedNotification(String message) {
        try {
            Intent open = new Intent(this, LocationSharingActivityV2.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pending = PendingIntent.getActivity(
                    this,
                    6203,
                    open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, WARNING_CHANNEL_ID)
                    : new Notification.Builder(this);
            Notification n = builder
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("야모네 위치 공유")
                    .setContentText(message)
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .build();
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(ENDED_NOTIFY_ID, n);
        } catch (Exception ignored) {}
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFY_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFY_ID, notification);
        }
    }

    private void updateNotification(String message) {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFY_ID, buildServiceNotification(message));
        } catch (SecurityException ignored) {}
    }

    private int clampInterval(int seconds) {
        if (seconds <= 10) return 10;
        if (seconds <= 30) return 30;
        if (seconds <= 60) return 60;
        return 180;
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
        leaveInFlight = false;
        handler.removeCallbacksAndMessages(null);
        stopLocationUpdates();
        cancelWarning();
        stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
