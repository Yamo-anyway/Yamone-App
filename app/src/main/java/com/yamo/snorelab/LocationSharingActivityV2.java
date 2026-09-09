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

/**
 * Adds share-time controls without altering the established LocationSharingActivity layout.
 */
public class LocationSharingActivityV2 extends LocationSharingActivity {
    private static final String EXTEND_TAG = "yamone_location_time_extend";
    private static final int CARD = 0xFF16243B;
    private static final int TEXT = 0xFFF5F7FF;
    private static final int PRIMARY2 = 0xFF8B8FFF;
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

        TextView oldNote = findContaining(group, "종료 5분 전 알림과 시간 연장은 다음 단계");
        if (oldNote != null) {
            oldNote.setText("공유 시간이 끝나면 자동으로 방에서 나갑니다. 종료 5분 전에 알림으로 알려주며 알림 또는 위치 공유 화면에서 시간을 연장할 수 있습니다.");
        }

        TextView leave = findExact(group, "위치 공유 종료 · 방 나가기");
        if (leave == null || !(leave.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) leave.getParent();
        if (parent.findViewWithTag(EXTEND_TAG) != null) return;

        TextView extend = new TextView(this);
        extend.setTag(EXTEND_TAG);
        extend.setText("⏱  공유 시간 연장 / 변경");
        extend.setTextColor(PRIMARY2);
        extend.setTextSize(14);
        extend.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        extend.setGravity(android.view.Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(15));
        bg.setStroke(dp(1), 0xFF33435F);
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

    private TextView findContaining(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(text)) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findContaining(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        enhancer.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
