package com.yamo.snorelab;

import android.content.Context;
import android.content.SharedPreferences;

/** Shared profile values used by features such as location sharing. */
public final class LocationProfileStore {
    private static final String PREFS = "yamone_profile_v1";
    private static final String KEY_NICKNAME = "nickname";
    private static final String DEFAULT_NICKNAME = "사용자";

    private LocationProfileStore() {}

    public static String getNickname(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_NICKNAME, "");
        value = value == null ? "" : value.trim();
        return value.isEmpty() ? DEFAULT_NICKNAME : value;
    }

    public static void setNickname(Context context, String nickname) {
        String value = nickname == null ? "" : nickname.trim();
        if (value.isEmpty()) value = DEFAULT_NICKNAME;
        prefs(context).edit().putString(KEY_NICKNAME, value).apply();
    }

    public static boolean isDefaultNickname(Context context) {
        return DEFAULT_NICKNAME.equals(getNickname(context));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
