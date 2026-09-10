package com.yamo.snorelab;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Local-only alerting for urgent states discovered during the normal room snapshot refresh.
 * No FCM/push token is used. The server only stores the participant's current predefined state.
 */
public final class LocationStatusAlert {
    private static final String PREFS = "yamone_location_status_alerts_v1";
    private static final String CHANNEL = "location_sharing_status_alert_v1";
    private static final int NOTIFY_BASE = 6400;

    private LocationStatusAlert() {}

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "위치 공유 도움·긴급 알림", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("위치 공유 참여자가 도움 필요 또는 긴급 상태로 바뀌었을 때 기기에서 알려줍니다.");
        channel.enableVibration(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(channel);
    }

    public static void inspect(Context context, JSONObject snapshot) {
        if (context == null || snapshot == null || !snapshot.optBoolean("active", false)) return;
        JSONArray members = snapshot.optJSONArray("members");
        if (members == null) return;
        createChannel(context);

        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.optJSONObject(i);
            if (member == null || member.optBoolean("is_self", false)) continue;
            String status = member.optString("user_status", "normal");
            if (!isUrgent(status)) continue;

            String memberId = member.optString("member_id", "");
            String updatedAt = member.optString("status_updated_at", "");
            if (memberId.isEmpty() || updatedAt.isEmpty()) continue;

            String fingerprint = status + "|" + updatedAt;
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String key = "seen_" + memberId;
            if (fingerprint.equals(prefs.getString(key, ""))) continue;

            if (show(context, memberId, member.optString("nickname", "참여자"), status)) {
                prefs.edit().putString(key, fingerprint).apply();
            }
        }
    }

    public static void clear(Context context) {
        if (context != null) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static boolean isUrgent(String status) {
        return "help".equals(status) || "emergency".equals(status);
    }

    private static boolean show(Context context, String memberId, String nickname, String status) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        Intent open = new Intent(context, LocationSharingActivityV2.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int suffix = Math.abs(memberId.hashCode() % 500);
        PendingIntent pending = PendingIntent.getActivity(
                context, NOTIFY_BASE + suffix, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        boolean emergency = "emergency".equals(status);
        String title = emergency ? "위치공유 긴급 상태" : "위치공유 도움 요청";
        String body = nickname + "님이 " + (emergency ? "긴급" : "도움 필요")
                + " 상태로 변경했습니다. 위치공유 화면에서 확인하세요.";

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL)
                : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(pending)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT < 26) {
            builder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
        }

        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return false;
            manager.notify(NOTIFY_BASE + suffix, builder.build());
            return true;
        } catch (SecurityException ignored) {
            return false;
        }
    }
}
