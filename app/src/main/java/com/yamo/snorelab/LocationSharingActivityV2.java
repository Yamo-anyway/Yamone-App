package com.yamo.snorelab;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Compatibility entry point used by notifications and existing intents. */
public class LocationSharingActivityV2 extends LocationSharingActivity {
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scheduleLandingBackFix();
    }

    @Override protected void onResume() {
        super.onResume();
        scheduleLandingBackFix();
    }

    private void scheduleLandingBackFix() {
        ui.postDelayed(this::fixLandingBack, 180L);
        ui.postDelayed(this::fixLandingBack, 700L);
    }

    private void fixLandingBack() {
        View root = findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;
        // Only the location-sharing landing screen has this exact page title.
        TextView title = findExact(root, "위치 공유");
        TextView back = findExact(root, "‹");
        if (title != null && back != null) back.setOnClickListener(v -> finish());
    }

    private TextView findExact(View view, String wanted) {
        if (view instanceof TextView && wanted.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findExact(group.getChildAt(i), wanted);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
