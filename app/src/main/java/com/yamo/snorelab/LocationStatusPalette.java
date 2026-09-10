package com.yamo.snorelab;

/** Single source of truth for location-sharing status labels and colors. */
public final class LocationStatusPalette {
    public static final int NORMAL = 0xFF35A854;
    public static final int CONTACT = 0xFF3B82F6;
    public static final int HELP = 0xFFF59E0B;
    public static final int EMERGENCY = 0xFFE53935;

    private LocationStatusPalette() {}

    public static int color(String status) {
        if ("emergency".equals(status)) return EMERGENCY;
        if ("help".equals(status)) return HELP;
        if ("contact".equals(status)) return CONTACT;
        return NORMAL;
    }

    public static int softColor(String status) {
        if ("emergency".equals(status)) return 0xFFFFE8E8;
        if ("help".equals(status)) return 0xFFFFF3D6;
        if ("contact".equals(status)) return 0xFFEAF2FF;
        return 0xFFE9F7EC;
    }

    public static String label(String status) {
        if ("emergency".equals(status)) return "긴급";
        if ("help".equals(status)) return "도움 필요";
        if ("contact".equals(status)) return "연락 요청";
        return "정상";
    }
}
