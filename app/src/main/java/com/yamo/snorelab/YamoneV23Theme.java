package com.yamo.snorelab;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.TextView;

/** Design tokens for the approved Yamone Renewal v0.00.23 mockup. */
public final class YamoneV23Theme {
    public static final String VERSION = "0.00.23";
    public static final String PREF_THEME = "yamone_theme";
    public static final String THEME_MINT = "mint";
    public static final String THEME_PINK = "pink";
    public static final float CARD_RADIUS_DP = 15f;
    public static final float CARD_BORDER_DP = 1.5f;
    public static final float NAV_RADIUS_DP = 13f;

    private YamoneV23Theme() {}

    public static final class Palette {
        public final int bg, surface, soft, text, muted, primary, primary2, border, danger, warning, navInactive;
        private Palette(int bg, int surface, int soft, int text, int muted, int primary, int primary2, int border) {
            this.bg = bg; this.surface = surface; this.soft = soft; this.text = text; this.muted = muted;
            this.primary = primary; this.primary2 = primary2; this.border = border;
            this.danger = Color.rgb(231, 91, 109); this.warning = Color.rgb(233, 166, 66);
            this.navInactive = Color.rgb(133, 145, 161);
        }
    }

    public static Palette mint() {
        return new Palette(Color.rgb(251,253,252), Color.WHITE, Color.rgb(233,250,245),
                Color.rgb(17,24,39), Color.rgb(119,130,149), Color.rgb(11,184,142),
                Color.rgb(67,210,176), Color.rgb(220,227,231));
    }

    public static Palette pink() {
        return new Palette(Color.rgb(255,250,251), Color.WHITE, Color.rgb(255,240,244),
                Color.rgb(33,23,27), Color.rgb(136,117,125), Color.rgb(240,111,149),
                Color.rgb(255,173,196), Color.rgb(239,223,229));
    }

    public static Palette from(SharedPreferences prefs) {
        String value = prefs == null ? THEME_MINT : prefs.getString(PREF_THEME, THEME_MINT);
        return THEME_PINK.equals(value) ? pink() : mint();
    }

    public static void saveTheme(SharedPreferences prefs, String theme) {
        if (prefs != null) prefs.edit().putString(PREF_THEME, THEME_PINK.equals(theme) ? THEME_PINK : THEME_MINT).apply();
    }

    public static void applySystemBars(Activity activity, Palette p) {
        activity.getWindow().setStatusBarColor(p.bg);
        activity.getWindow().setNavigationBarColor(p.bg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = activity.getWindow().getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            activity.getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    public static GradientDrawable card(Context context, Palette p) {
        return rounded(context, p.surface, p.border, CARD_RADIUS_DP, CARD_BORDER_DP);
    }

    public static GradientDrawable navSelection(Context context, Palette p) {
        return rounded(context, p.soft, Color.TRANSPARENT, NAV_RADIUS_DP, 0f);
    }

    public static GradientDrawable rounded(Context context, int fill, int stroke, float radiusDp, float strokeDp) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(context, radiusDp));
        if (strokeDp > 0 && Color.alpha(stroke) > 0) d.setStroke(Math.max(1, Math.round(dp(context, strokeDp))), stroke);
        return d;
    }

    public static void title(TextView view, Palette p) { view.setTextColor(p.text); view.setTextSize(25f); view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); }
    public static void sectionTitle(TextView view, Palette p) { view.setTextColor(p.text); view.setTextSize(15f); view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); }
    public static void body(TextView view, Palette p) { view.setTextColor(p.text); view.setTextSize(13f); }
    public static void caption(TextView view, Palette p) { view.setTextColor(p.muted); view.setTextSize(11f); }
    public static int dp(Context context, float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
