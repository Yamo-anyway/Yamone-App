package com.yamo.snorelab;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

/** Keeps Yamone activity-family content outside Android status/navigation bar areas. */
public final class ActivitySystemBarUiEnhancer {
    private static final int DARK_BG = 0xFF0B1324;

    private ActivitySystemBarUiEnhancer() {}

    public static void apply(Activity activity) {
        if (activity == null) return;
        activity.getWindow().setStatusBarColor(DARK_BG);
        activity.getWindow().setNavigationBarColor(DARK_BG);

        View decor = activity.getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= 23) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(flags);

        // Android 11+ / targetSdk 35+ can render edge-to-edge. Apply each system inset once
        // to the activity-family root so content never hides under the bars.
        if (Build.VERSION.SDK_INT < 30) return;
        activity.getWindow().setDecorFitsSystemWindows(false);

        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        ViewGroup host = (ViewGroup) content;
        if (host.getChildCount() == 0) return;
        View root = host.getChildAt(0);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getInsets(WindowInsets.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bottom);
            return insets;
        });
        root.requestApplyInsets();
    }
}
