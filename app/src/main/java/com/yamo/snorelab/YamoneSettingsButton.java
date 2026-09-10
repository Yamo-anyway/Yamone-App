package com.yamo.snorelab;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;

/** Shared white circular settings button for Yamone main tabs. */
public final class YamoneSettingsButton {
    private YamoneSettingsButton() {}

    public static ImageView create(Activity activity, View.OnClickListener click) {
        boolean pink = "pink".equals(activity.getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "pink"));
        int accentColor = pink ? 0xFFE94778 : 0xFF159A7A;
        int borderColor = pink ? 0xFFFFD7E3 : 0xFFD7EFE7;

        ImageView button = new ImageView(activity);
        button.setImageResource(R.drawable.ic_yamone_settings);
        button.setColorFilter(accentColor);
        button.setPadding(dp(activity, 11), dp(activity, 11), dp(activity, 11), dp(activity, 11));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(activity, 22));
        bg.setStroke(dp(activity, 1), borderColor);
        button.setBackground(bg);
        button.setOnClickListener(click);
        button.setClickable(true);
        button.setContentDescription("설정");
        if (Build.VERSION.SDK_INT >= 21) button.setElevation(dp(activity, 1.5f));
        return button;
    }

    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
