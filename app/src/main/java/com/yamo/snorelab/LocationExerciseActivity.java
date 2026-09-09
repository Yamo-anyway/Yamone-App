package com.yamo.snorelab;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Adds Yamone activity entries without mixing location-sharing UI into Activity. */
public class LocationExerciseActivity extends EnhancedExerciseActivity {
    private static final String HIKING_TAG = "yamone_hiking_entry_v1";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        attachEnhancements();
    }

    @Override protected void onResume() {
        super.onResume();
        attachEnhancements();
    }

    private void attachEnhancements() {
        View root = findViewById(android.R.id.content);
        if (root == null) return;
        root.post(() -> {
            renameActivityCopy(root);
            attachSkiEntry(root);
            attachHikingEntry(root);
            ActivitySystemBarUiEnhancer.apply(this);
        });
    }

    private void renameActivityCopy(View root) {
        TextView oldSki = findText(root, "스키 / 스노우보드");
        if (oldSki != null) oldSki.setText("스키 / 스노보드");
        TextView subtitle = findContaining(root, "걷기/러닝 · 자전거 · 스키/스노우보드");
        if (subtitle != null) subtitle.setText("걷기/러닝 · 자전거 · 등산 · 스키/스노보드");
    }

    private void attachSkiEntry(View root) {
        TextView title = findText(root, "스키 / 스노보드");
        if (title == null) return;
        View card = clickableAncestor(title, root);
        if (card != null) card.setOnClickListener(v -> startActivity(new Intent(this, SkiActivity.class)));
    }

    private void attachHikingEntry(View root) {
        if (root.findViewWithTag(HIKING_TAG) != null) return;
        TextView skiTitle = findText(root, "스키 / 스노보드");
        if (skiTitle == null) return;
        View skiCard = clickableAncestor(skiTitle, root);
        if (skiCard == null || !(skiCard.getParent() instanceof LinearLayout)) return;
        LinearLayout page = (LinearLayout) skiCard.getParent();
        int index = page.indexOfChild(skiCard);
        if (index < 0) return;

        LinearLayout card = new LinearLayout(this);
        card.setTag(HIKING_TAG);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(round(0xFFFFFFFF, 18, 1, border()));
        card.setOnClickListener(v -> startActivity(new Intent(this, HikingActivity.class)));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("🥾  ⛰️  등산", 18, textColor(), true);
        words.addView(title);
        TextView desc = text("거리 · 시간 · 고도 · 누적 상승 · 이동 경로를 기록합니다.", 12, muted(), false);
        desc.setPadding(0, dp(6), dp(8), 0);
        words.addView(desc);
        head.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = text("›", 30, primary2(), false);
        arrow.setGravity(Gravity.CENTER);
        head.addView(arrow, new LinearLayout.LayoutParams(dp(34), dp(60)));
        card.addView(head);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        page.addView(card, index, params);
    }

    private View clickableAncestor(View start, View root) {
        View current = start;
        while (current != null && current != root) {
            if (current.hasOnClickListeners()) return current;
            if (!(current.getParent() instanceof View)) break;
            current = (View) current.getParent();
        }
        return null;
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

    private TextView findContaining(View view, String wanted) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && value.toString().contains(wanted)) return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findContaining(group.getChildAt(i), wanted);
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean pink() {
        return "pink".equals(getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "mint"));
    }

    private int textColor() { return pink() ? 0xFF4B2633 : 0xFF153633; }
    private int muted() { return pink() ? 0xFF9A7180 : 0xFF718984; }
    private int primary2() { return pink() ? 0xFFE94778 : 0xFF159A7A; }
    private int border() { return pink() ? 0xFFFFD7E3 : 0xFFD7EFE7; }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
