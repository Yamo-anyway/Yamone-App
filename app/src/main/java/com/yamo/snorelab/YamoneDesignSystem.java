package com.yamo.snorelab;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class YamoneDesignSystem {
    private YamoneDesignSystem() {}

    public static final String KEY_THEME = "yamone_theme";
    public static final String THEME_MINT = "mint";
    public static final String THEME_PINK = "pink";

    public static final class Palette {
        public final int background;
        public final int surface;
        public final int surfaceSoft;
        public final int text;
        public final int muted;
        public final int primary;
        public final int primaryStrong;
        public final int border;
        public final int danger;
        public final int warning;
        public final int success;

        private Palette(int background, int surface, int surfaceSoft, int text, int muted,
                        int primary, int primaryStrong, int border,
                        int danger, int warning, int success) {
            this.background = background;
            this.surface = surface;
            this.surfaceSoft = surfaceSoft;
            this.text = text;
            this.muted = muted;
            this.primary = primary;
            this.primaryStrong = primaryStrong;
            this.border = border;
            this.danger = danger;
            this.warning = warning;
            this.success = success;
        }
    }

    public static Palette palette(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SleepRecorderService.PREFS, Context.MODE_PRIVATE);
        return paletteFor(prefs.getString(KEY_THEME, THEME_MINT));
    }

    public static Palette paletteFor(String theme) {
        boolean pink = THEME_PINK.equals(theme);
        if (pink) {
            return new Palette(
                    0xFFFFFAFC, 0xFFFFFFFF, 0xFFFFF0F5, 0xFF38262D, 0xFF8D737E,
                    0xFFFF88AA, 0xFFE94F7D, 0xFFF4D9E2,
                    0xFFE45D6D, 0xFFE4A33F, 0xFF2C9C78
            );
        }
        return new Palette(
                0xFFF8FCFA, 0xFFFFFFFF, 0xFFEAF8F3, 0xFF203532, 0xFF70837F,
                0xFF77D8BF, 0xFF2AA887, 0xFFD7ECE5,
                0xFFE45D6D, 0xFFE4A33F, 0xFF2C9C78
        );
    }

    public static String currentTheme(Context context) {
        return context.getSharedPreferences(SleepRecorderService.PREFS, Context.MODE_PRIVATE)
                .getString(KEY_THEME, THEME_MINT);
    }

    public static void setTheme(Context context, String theme) {
        String safe = THEME_PINK.equals(theme) ? THEME_PINK : THEME_MINT;
        context.getSharedPreferences(SleepRecorderService.PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_THEME, safe).apply();
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, (int) radiusDp));
        return drawable;
    }

    public static GradientDrawable roundedBorder(int fill, int stroke, float radiusDp, int strokeDp, Context context) {
        GradientDrawable drawable = rounded(fill, radiusDp, context);
        drawable.setStroke(dp(context, strokeDp), stroke);
        return drawable;
    }

    public static TextView text(Context context, String value, int sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    public static LinearLayout card(Context context, Palette palette) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedBorder(palette.surface, palette.border, 18, 1, context));
        if (android.os.Build.VERSION.SDK_INT >= 21) card.setElevation(dp(context, 1));
        return card;
    }

    public static TextView badge(Context context, String label, Palette palette) {
        TextView badge = text(context, label, 11, palette.primaryStrong, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5));
        badge.setBackground(rounded(palette.surfaceSoft, 999, context));
        return badge;
    }

    public static TextView button(Context context, String label, boolean primary, Palette palette) {
        int fill = primary ? palette.primaryStrong : palette.surfaceSoft;
        int textColor = primary ? Color.WHITE : palette.primaryStrong;
        TextView button = text(context, label, 15, textColor, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        button.setBackground(rounded(fill, 16, context));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    public static LinearLayout.LayoutParams match(int heightDp, Context context) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, heightDp));
    }

    public static View divider(Context context, Palette palette) {
        View divider = new View(context);
        divider.setBackgroundColor(palette.border);
        return divider;
    }
}
