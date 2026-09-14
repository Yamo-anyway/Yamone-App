package com.yamo.snorelab;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import org.json.JSONObject;

import java.util.List;

/** Developer-only geofence receiver for installed Snow offline packages. */
public class SnowGeofenceReceiver extends BroadcastReceiver {
    public static final String ACTION_GEOFENCE = "com.yamo.snorelab.SNOW_GEOFENCE";
    private static final String CHANNEL = "yamone_snow_dev_detect";

    @Override public void onReceive(Context context, Intent intent) {
        if (!SnowBridge.isDeveloperAvailable(context)) return;
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError() || event.getGeofenceTransition() != Geofence.GEOFENCE_TRANSITION_ENTER) return;
        List<Geofence> triggers = event.getTriggeringGeofences();
        if (triggers == null || triggers.isEmpty()) return;
        String key = triggers.get(0).getRequestId();
        JSONObject map = SnowOfflineMapStore.readManifest(context, key);
        if (map.length() == 0) return;
        String name = map.optString("resortName", "스키장");
        SkiResortStore.setCurrent(context, key, name);
        notify(context, name);
    }

    private void notify(Context context, String resortName) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "Snow 개발 자동감지", NotificationManager.IMPORTANCE_DEFAULT);
            c.setDescription("개발자 모드에서 설치된 스키장 지도 경계 진입을 알립니다.");
            nm.createNotificationChannel(c);
        }
        Intent open = new Intent(context, YamoneDesignPreviewActivity.class)
                .putExtra(YamoneDesignPreviewActivity.EXTRA_OPEN_SNOW, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 5829, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        b.setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Snow 스키장 감지")
                .setContentText(resortName + " 경계 안으로 들어왔습니다. 기록을 시작할 수 있어요.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_RECOMMENDATION);
        nm.notify(5829, b.build());
    }
}
