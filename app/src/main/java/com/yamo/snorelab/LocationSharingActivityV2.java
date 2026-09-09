package com.yamo.snorelab;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Adds share-time controls while keeping the Yamone mint/pink theme. */
public class LocationSharingActivityV2 extends LocationSharingActivity {
    private static final String EXTEND_TAG = "yamone_location_time_extend";
    private final Handler enhancer = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scheduleEnhance();
    }

    @Override protected void onResume() {
        super.onResume();
        scheduleEnhance();
    }

    private void scheduleEnhance() {
        enhancer.removeCallbacksAndMessages(null);
        enhancer.postDelayed(this::enhance, 150L);
        enhancer.postDelayed(this::enhance, 700L);
        enhancer.postDelayed(this::enhance, 1_500L);
    }

    private void enhance() {
        View root = findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) root;

        TextView leave = findExact(group, "위치 공유 종료 · 방 나가기");
        if (leave == null || !(leave.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) leave.getParent();
        if (parent.findViewWithTag(EXTEND_TAG) != null) return;

        TextView extend = new TextView(this);
        extend.setTag(EXTEND_TAG);
        extend.setText("⏱  공유 시간 연장 / 변경");
        extend.setTextColor(primary2());
        extend.setTextSize(14);
        extend.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        extend.setGravity(android.view.Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(card2());
        bg.setCornerRadius(dp(15));
        bg.setStroke(dp(1), border());
        extend.setBackground(bg);
        extend.setOnClickListener(v -> startActivity(new Intent(this, LocationSharingTimeActivity.class)));

        int index = parent.indexOfChild(leave);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        params.topMargin = dp(10);
        if (index >= 0) parent.addView(extend, index, params);
        else parent.addView(extend, params);
    }

    private TextView findExact(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findExact(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean pink() {
        return "pink".equals(getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "mint"));
    }

    private int card2() { return pink() ? 0xFFFFEEF3 : 0xFFF0FAF6; }
    private int primary2() { return pink() ? 0xFFE94778 : 0xFF159A7A; }
    private int border() { return pink() ? 0xFFFFD7E3 : 0xFFD7EFE7; }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        enhancer.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
