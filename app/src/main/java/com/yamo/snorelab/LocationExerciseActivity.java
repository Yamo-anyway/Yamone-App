package com.yamo.snorelab;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Adds location sharing access without changing the existing activity screen layout. */
public class LocationExerciseActivity extends EnhancedExerciseActivity {
    private static final String TAG = "yamone_location_share_entry";
    private static final int PRIMARY = 0xFF6D72FF;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        attachLocationEntry();
    }

    @Override protected void onResume() {
        super.onResume();
        attachLocationEntry();
    }

    private void attachLocationEntry() {
        View host = findViewById(android.R.id.content);
        if (!(host instanceof FrameLayout)) return;
        FrameLayout frame = (FrameLayout) host;
        if (frame.findViewWithTag(TAG) != null) return;

        TextView button = new TextView(this);
        button.setTag(TAG);
        button.setText("📍 위치 공유");
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setElevation(dp(8));
        button.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PRIMARY);
        bg.setCornerRadius(dp(18));
        button.setBackground(bg);
        button.setOnClickListener(v -> startActivity(new Intent(this, LocationSharingActivityV2.class)));

        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        p.gravity = Gravity.END | Gravity.BOTTOM;
        p.rightMargin = dp(16);
        p.bottomMargin = dp(78);
        frame.addView(button, p);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
