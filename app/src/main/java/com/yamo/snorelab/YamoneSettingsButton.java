package com.yamo.snorelab;

import android.app.Activity;
import android.view.View;
import android.widget.ImageView;

/** Shared Yamone three-line menu entry for the main tabs. */
public final class YamoneSettingsButton {
    private YamoneSettingsButton() {}

    public static ImageView create(Activity activity, View.OnClickListener click) {
        boolean pink = "pink".equals(activity.getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "pink"));
        int accentColor = pink ? 0xFFE94778 : 0xFF159A7A;

        ImageView button = new ImageView(activity);
        button.setImageResource(R.drawable.ic_yamone_menu);
        button.setColorFilter(accentColor);
        button.setPadding(dp(activity, 9), dp(activity, 9), dp(activity, 9), dp(activity, 9));
        button.setBackground(null);
        button.setOnClickListener(click);
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription("메뉴");
        return button;
    }

    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
