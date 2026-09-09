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

/** Adds ski entry and an active-only location-sharing status without changing the established activity layout. */
public class LocationExerciseActivity extends EnhancedExerciseActivity {
    private static final String TAG = "yamone_location_share_active_entry";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        attachEnhancements();
    }

    @Override protected void onResume() {
        super.onResume();
        attachEnhancements();
    }

    private void attachEnhancements() {
        attachLocationActiveEntry();
        attachSkiEntry();
    }

    private void attachLocationActiveEntry() {
        View host = findViewById(android.R.id.content);
        if (!(host instanceof FrameLayout)) return;
        FrameLayout frame = (FrameLayout) host;
        View old = frame.findViewWithTag(TAG);

        if (!LocationSharingStateStore.isActive(this)) {
            if (old != null) frame.removeView(old);
            return;
        }

        if (old instanceof TextView) {
            ((TextView) old).setText(activeLabel());
            old.setBackground(round(primary(), 18));
            return;
        }

        TextView button = new TextView(this);
        button.setTag(TAG);
        button.setText(activeLabel());
        button.setTextColor(Color.WHITE);
        button.setTextSize(11);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setElevation(dp(6));
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackground(round(primary(), 18));
        button.setOnClickListener(v -> startActivity(new Intent(this, LocationSharingActivityV2.class)));

        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        p.gravity = Gravity.END | Gravity.BOTTOM;
        p.rightMargin = dp(16);
        p.bottomMargin = dp(78);
        frame.addView(button, p);
    }

    private String activeLabel() {
        String room = LocationSharingStateStore.roomName(this);
        String remaining = LocationSharingStateStore.remainingText(this);
        return "📍 공유 중 · " + room + (remaining.isEmpty() ? "" : " · " + remaining);
    }

    private void attachSkiEntry() {
        View root = findViewById(android.R.id.content);
        TextView title = findText(root, "스키 / 스노우보드");
        if (title == null) return;

        View current = title;
        while (current != null && current != root) {
            if (current.hasOnClickListeners()) {
                current.setOnClickListener(v -> startActivity(new Intent(this, SkiActivity.class)));
                return;
            }
            if (!(current.getParent() instanceof View)) break;
            current = (View) current.getParent();
        }
    }

    private TextView findText(View view, String wanted) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && wanted.contentEquals(value)) return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), wanted);
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean pink() {
        return "pink".equals(getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "mint"));
    }

    private int primary() { return pink() ? 0xFFFF769F : 0xFF56D1B3; }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
