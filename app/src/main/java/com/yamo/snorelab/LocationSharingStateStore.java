package com.yamo.snorelab;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/** Lightweight local mirror of the currently active location-sharing room for UI only. */
public final class LocationSharingStateStore {
    private static final String PREFS = "yamone_location_share_ui_v1";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_ROOM = "room";
    private static final String KEY_SHARE_UNTIL = "share_until";
    private static final String KEY_INTERVAL = "interval_seconds";
    private static final String KEY_MEMBER_COUNT = "member_count";

    private LocationSharingStateStore() {}

    public static void update(Context context, String roomName, String shareUntil, int intervalSeconds, int memberCount) {
        prefs(context).edit()
                .putBoolean(KEY_ACTIVE, true)
                .putString(KEY_ROOM, roomName == null ? "" : roomName)
                .putString(KEY_SHARE_UNTIL, shareUntil == null ? "" : shareUntil)
                .putInt(KEY_INTERVAL, intervalSeconds)
                .putInt(KEY_MEMBER_COUNT, Math.max(0, memberCount))
                .apply();
    }

    public static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    public static boolean isActive(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.getBoolean(KEY_ACTIVE, false)) return false;
        String until = p.getString(KEY_SHARE_UNTIL, "");
        if (until == null || until.isEmpty()) return true;
        try {
            if (Instant.parse(until).toEpochMilli() <= System.currentTimeMillis()) {
                clear(context);
                return false;
            }
        } catch (DateTimeParseException ignored) {}
        return true;
    }

    public static String roomName(Context context) {
        return prefs(context).getString(KEY_ROOM, "위치 공유 방");
    }

    public static int memberCount(Context context) {
        return prefs(context).getInt(KEY_MEMBER_COUNT, 0);
    }

    public static int intervalSeconds(Context context) {
        return prefs(context).getInt(KEY_INTERVAL, 60);
    }

    public static String remainingText(Context context) {
        String until = prefs(context).getString(KEY_SHARE_UNTIL, "");
        if (until == null || until.isEmpty()) return "";
        try {
            long seconds = Math.max(0L, (Instant.parse(until).toEpochMilli() - System.currentTimeMillis()) / 1000L);
            long hours = seconds / 3600L;
            long minutes = (seconds % 3600L) / 60L;
            if (hours > 0) return hours + "시간 " + minutes + "분 남음";
            return minutes + "분 남음";
        } catch (DateTimeParseException ignored) {
            return "";
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
