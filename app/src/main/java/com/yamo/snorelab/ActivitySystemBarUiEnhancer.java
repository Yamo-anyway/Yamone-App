package com.yamo.snorelab;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.WeakHashMap;

/** Applies Yamone mint/pink styling and safe system-bar insets to activity-family screens. */
public final class ActivitySystemBarUiEnhancer {
    private static final int OLD_BG = 0xFF0B1324;
    private static final int OLD_CARD = 0xFF16243B;
    private static final int OLD_CARD2 = 0xFF111C31;
    private static final int OLD_NAV = 0xFF0E182A;
    private static final int OLD_TEXT = 0xFFF5F7FF;
    private static final int OLD_MUTED = 0xFF9DA9BF;
    private static final int OLD_PRIMARY = 0xFF6D72FF;
    private static final int OLD_PRIMARY2 = 0xFF8B8FFF;
    private static final int OLD_SUCCESS = 0xFF61D6A8;
    private static final int OLD_WARNING = 0xFFFFC56D;

    private static final WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener> LISTENERS = new WeakHashMap<>();

    private ActivitySystemBarUiEnhancer() {}

    public static synchronized void apply(Activity activity) {
        if (activity == null) return;
        applyBars(activity);
        applyInsets(activity);

        View decor = activity.getWindow().getDecorView();
        if (!LISTENERS.containsKey(activity)) {
            ViewTreeObserver.OnGlobalLayoutListener listener = () -> styleNow(activity);
            decor.getViewTreeObserver().addOnGlobalLayoutListener(listener);
            LISTENERS.put(activity, listener);
        }
        decor.post(() -> styleNow(activity));
    }

    public static synchronized void detach(Activity activity) {
        if (activity == null) return;
        ViewTreeObserver.OnGlobalLayoutListener listener = LISTENERS.remove(activity);
        View decor = activity.getWindow().getDecorView();
        if (listener != null && decor.getViewTreeObserver().isAlive()) {
            decor.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        }
    }

    private static void applyBars(Activity activity) {
        int bg = bg(activity);
        activity.getWindow().setStatusBarColor(bg);
        activity.getWindow().setNavigationBarColor(bg);
        View decor = activity.getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= 23) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(flags);
    }

    private static void applyInsets(Activity activity) {
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

    private static void styleNow(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        ViewGroup host = (ViewGroup) content;
        host.setBackgroundColor(bg(activity));
        for (int i = 0; i < host.getChildCount(); i++) styleTree(activity, host.getChildAt(i));
    }

    private static void styleTree(Activity activity, View view) {
        if (view == null) return;
        if (view instanceof ScrollView || view instanceof FrameLayout) {
            mapBackground(activity, view);
        } else {
            mapBackground(activity, view);
        }

        if (view instanceof TextView) styleText(activity, (TextView) view);
        if (view instanceof ProgressBar && Build.VERSION.SDK_INT >= 21) {
            ((ProgressBar) view).setProgressTintList(ColorStateList.valueOf(primary(activity)));
            ((ProgressBar) view).setProgressBackgroundTintList(ColorStateList.valueOf(card2(activity)));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) styleTree(activity, group.getChildAt(i));
        }
    }

    private static void styleText(Activity activity, TextView view) {
        int current = view.getCurrentTextColor();
        if (current == OLD_TEXT) view.setTextColor(text(activity));
        else if (current == OLD_MUTED) view.setTextColor(muted(activity));
        else if (current == OLD_PRIMARY) view.setTextColor(primary(activity));
        else if (current == OLD_PRIMARY2) view.setTextColor(primary2(activity));
        else if (current == OLD_SUCCESS) view.setTextColor(success(activity));
        else if (current == OLD_WARNING) view.setTextColor(warning(activity));

        if (view instanceof Button) {
            String label = view.getText() == null ? "" : view.getText().toString();
            if (label.contains("삭제") || label.contains("기록 종료") || label.contains("중단")) {
                view.setTextColor(0xFFE75B6D);
                view.setBackground(round(activity, 0xFFFFEFF3, 16, 1, 0xFFFFCCD6));
            } else if (label.contains("시작") || label.contains("저장") || label.contains("완료")) {
                view.setTextColor(0xFFFFFFFF);
                view.setBackground(round(activity, primary(activity), 16, 0, 0));
            } else {
                view.setTextColor(text(activity));
                view.setBackground(round(activity, card2(activity), 14, 1, border(activity)));
            }
        } else if (view instanceof EditText) {
            view.setTextColor(text(activity));
            ((EditText) view).setHintTextColor(muted(activity));
            view.setBackground(round(activity, 0xFFFFFFFF, 14, 1, border(activity)));
        }
    }

    private static void mapBackground(Activity activity, View view) {
        Drawable drawable = view.getBackground();
        if (drawable instanceof ColorDrawable) {
            int color = ((ColorDrawable) drawable).getColor();
            if (color == OLD_BG) view.setBackgroundColor(bg(activity));
            else if (color == OLD_NAV) view.setBackgroundColor(0xFFFFFFFF);
            else if (color == OLD_CARD) view.setBackgroundColor(0xFFFFFFFF);
            else if (color == OLD_CARD2) view.setBackgroundColor(card2(activity));
            return;
        }
        if (!(drawable instanceof GradientDrawable) || Build.VERSION.SDK_INT < 24) return;
        GradientDrawable gradient = (GradientDrawable) drawable;
        ColorStateList colors = gradient.getColor();
        if (colors == null) return;
        int color = colors.getDefaultColor();
        if (color == OLD_BG) gradient.setColor(bg(activity));
        else if (color == OLD_NAV || color == OLD_CARD) {
            gradient.setColor(0xFFFFFFFF);
            gradient.setStroke(dp(activity, 1), border(activity));
        } else if (color == OLD_CARD2 || color == 0xFF33425B) {
            gradient.setColor(card2(activity));
            gradient.setStroke(dp(activity, 1), border(activity));
        } else if (color == OLD_PRIMARY || color == OLD_PRIMARY2) {
            gradient.setColor(primary(activity));
        }
    }

    private static GradientDrawable round(Activity activity, int fill, int radiusDp, int strokeDp, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(activity, radiusDp));
        if (strokeDp > 0) d.setStroke(dp(activity, strokeDp), stroke);
        return d;
    }

    private static boolean pink(Activity activity) {
        return "pink".equals(activity.getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "pink"));
    }

    private static int bg(Activity a) { return pink(a) ? 0xFFFFF7FA : 0xFFF7FFFB; }
    private static int card2(Activity a) { return pink(a) ? 0xFFFFEEF3 : 0xFFF0FAF6; }
    private static int text(Activity a) { return pink(a) ? 0xFF4B2633 : 0xFF153633; }
    private static int muted(Activity a) { return pink(a) ? 0xFF9A7180 : 0xFF718984; }
    private static int primary(Activity a) { return pink(a) ? 0xFFFF769F : 0xFF56D1B3; }
    private static int primary2(Activity a) { return pink(a) ? 0xFFE94778 : 0xFF159A7A; }
    private static int success(Activity a) { return pink(a) ? 0xFFE94778 : 0xFF159A7A; }
    private static int warning(Activity a) { return 0xFFE9A642; }
    private static int border(Activity a) { return pink(a) ? 0xFFFFD7E3 : 0xFFD7EFE7; }
    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
