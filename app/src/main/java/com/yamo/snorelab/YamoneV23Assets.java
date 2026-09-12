package com.yamo.snorelab;

import android.content.Context;

/** Resource-name bridge for Yamone Renewal v0.00.23 PNG artwork. */
public final class YamoneV23Assets {
    private static final String PREFIX = "v23_";

    private YamoneV23Assets() {}

    public static int id(Context context, String sourceName) {
        if (context == null || sourceName == null) return 0;
        String normalized = sourceName;
        int dot = normalized.lastIndexOf('.');
        if (dot > 0) normalized = normalized.substring(0, dot);
        normalized = normalized.replace('-', '_').replace(' ', '_').toLowerCase();
        return context.getResources().getIdentifier(PREFIX + normalized, "drawable", context.getPackageName());
    }

    public static boolean has(Context context, String sourceName) {
        return id(context, sourceName) != 0;
    }
}
