package com.yamo.snorelab;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared Yamone back header: bold arrow + title on one row, subtitle on its own row. */
public final class YamoneBackHeader {
    private YamoneBackHeader() {}

    public static LinearLayout create(Activity activity,
                                      String title,
                                      String subtitle,
                                      int backgroundColor,
                                      int titleColor,
                                      int subtitleColor,
                                      View.OnClickListener backClick) {
        LinearLayout outer = new LinearLayout(activity);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(backgroundColor);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView back = new ImageView(activity);
        back.setImageResource(R.drawable.ic_yamone_arrow_back_bold);
        back.setColorFilter(accent(activity));
        back.setPadding(dp(activity, 6), dp(activity, 6), dp(activity, 6), dp(activity, 6));
        back.setContentDescription("뒤로");
        back.setOnClickListener(backClick);
        back.setClickable(true);
        back.setFocusable(true);

        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42));
        backParams.rightMargin = dp(activity, 8);
        row.addView(back, backParams);

        TextView titleView = new TextView(activity);
        titleView.setText(title == null ? "" : title);
        titleView.setTextSize(22);
        titleView.setTextColor(titleColor);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(titleView, new LinearLayout.LayoutParams(0, dp(activity, 50), 1f));
        outer.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 50)));

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView subtitleView = new TextView(activity);
            subtitleView.setText(subtitle);
            subtitleView.setTextSize(11);
            subtitleView.setTextColor(subtitleColor);
            subtitleView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            subtitleView.setPadding(dp(activity, 50), dp(activity, 1), 0, dp(activity, 4));
            outer.addView(subtitleView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return outer;
    }

    private static int accent(Activity activity) {
        return pink(activity) ? 0xFFE94778 : 0xFF159A7A;
    }

    private static boolean pink(Activity activity) {
        return "pink".equals(activity.getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "pink"));
    }

    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
