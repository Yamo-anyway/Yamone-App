package com.yamo.snorelab;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** Shared rounded Yamone bottom navigation item with vector icon. */
public final class YamoneBottomNav {
    private YamoneBottomNav() {}

    public static TextView create(Activity activity, int iconRes, String label, boolean selected,
                                  int activeColor, int mutedColor, int selectedBg,
                                  View.OnClickListener click) {
        TextView item = new TextView(activity);
        item.setText(label);
        item.setTextSize(11.5f);
        item.setGravity(Gravity.CENTER);
        item.setCompoundDrawablePadding(dp(activity, 3));
        item.setPadding(dp(activity, 6), dp(activity, 5), dp(activity, 6), dp(activity, 4));
        item.setTag(iconRes);
        item.setOnClickListener(click);
        apply(activity, item, selected, activeColor, mutedColor, selectedBg);
        return item;
    }

    public static void apply(Activity activity, TextView item, boolean selected,
                             int activeColor, int mutedColor, int selectedBg) {
        if (item == null) return;
        int color = selected ? activeColor : mutedColor;
        item.setTextColor(color);
        item.setTypeface(android.graphics.Typeface.DEFAULT,
                selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

        Object tag = item.getTag();
        if (tag instanceof Integer) {
            Drawable icon = activity.getResources().getDrawable((Integer) tag);
            if (Build.VERSION.SDK_INT >= 21) icon.setTint(color);
            icon.setBounds(0, 0, dp(activity, 23), dp(activity, 23));
            item.setCompoundDrawables(null, icon, null, null);
        }

        item.setBackground(selected ? rounded(selectedBg, dp(activity, 18)) : null);
    }

    private static GradientDrawable rounded(int fill, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radiusPx);
        return d;
    }

    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
