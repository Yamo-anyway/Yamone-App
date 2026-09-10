package com.yamo.snorelab;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Compact, theme-aware activity category card used by the Activity overview. */
public final class YamoneActivityEntry {
    private YamoneActivityEntry() {}

    public static LinearLayout create(Activity activity, int iconRes, String title, View.OnClickListener click) {
        boolean pink = "pink".equals(activity.getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "pink"));
        int accent = pink ? 0xFFE94778 : 0xFF159A7A;
        int text = pink ? 0xFF4B2633 : 0xFF153633;
        int soft = pink ? 0xFFFFEEF3 : 0xFFF0FAF6;
        int border = pink ? 0xFFFFD7E3 : 0xFFD7EFE7;

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(activity, 13), dp(activity, 10), dp(activity, 12), dp(activity, 10));
        card.setBackground(round(activity, 0xFFFFFFFF, 20, 1, border));
        card.setOnClickListener(click);
        card.setClickable(true);
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(activity, 0.8f));

        FrameLayout iconBubble = new FrameLayout(activity);
        iconBubble.setBackground(round(activity, soft, 18, 0, 0));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setColorFilter(accent);
        icon.setPadding(dp(activity, 11), dp(activity, 11), dp(activity, 11), dp(activity, 11));
        iconBubble.addView(icon, new FrameLayout.LayoutParams(dp(activity, 50), dp(activity, 50), Gravity.CENTER));
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(dp(activity, 50), dp(activity, 50));
        bubbleParams.rightMargin = dp(activity, 13);
        card.addView(iconBubble, bubbleParams);

        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextSize(17);
        titleView.setTextColor(text);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(titleView, new LinearLayout.LayoutParams(0, dp(activity, 50), 1f));

        FrameLayout arrowBubble = new FrameLayout(activity);
        arrowBubble.setBackground(round(activity, soft, 16, 0, 0));
        ImageView arrow = new ImageView(activity);
        arrow.setImageResource(R.drawable.ic_yamone_chevron_right);
        arrow.setColorFilter(accent);
        arrow.setPadding(dp(activity, 7), dp(activity, 7), dp(activity, 7), dp(activity, 7));
        arrowBubble.addView(arrow, new FrameLayout.LayoutParams(dp(activity, 30), dp(activity, 30), Gravity.CENTER));
        card.addView(arrowBubble, new LinearLayout.LayoutParams(dp(activity, 34), dp(activity, 34)));

        return card;
    }

    private static GradientDrawable round(Activity activity, int fill, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(activity, radiusDp));
        if (strokeDp > 0) d.setStroke(dp(activity, strokeDp), strokeColor);
        return d;
    }

    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
