package com.yamo.snorelab;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Adds Yamone activity entries without mixing location-sharing UI into Activity. */
public class LocationExerciseActivity extends EnhancedExerciseActivity {
    private static final String HIKING_TAG = "yamone_hiking_entry_v2";
    private boolean enhancementPosted;
    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::scheduleEnhancements;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        scheduleEnhancements();
    }

    @Override protected void onResume() {
        super.onResume();
        scheduleEnhancements();
    }

    @Override protected void onDestroy() {
        View decor = getWindow().getDecorView();
        if (decor.getViewTreeObserver().isAlive()) {
            decor.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
        }
        ActivitySystemBarUiEnhancer.detach(this);
        super.onDestroy();
    }

    private void scheduleEnhancements() {
        if (enhancementPosted) return;
        enhancementPosted = true;
        View decor = getWindow().getDecorView();
        decor.post(() -> {
            enhancementPosted = false;
            enhanceNow();
        });
    }

    private void enhanceNow() {
        View root = findViewById(android.R.id.content);
        if (root == null) return;
        renameActivityCopy(root);
        attachSkiEntry(root);
        attachHikingEntry(root);
        compactActivityEntries(root);
        ActivityDetailStatsEnhancer.apply(this);
        ActivitySystemBarUiEnhancer.apply(this);
    }

    private void renameActivityCopy(View root) {
        // Update the overview subtitle before changing the individual ski entry text.
        TextView subtitle = findContaining(root, "걷기/러닝 · 자전거 · 스키/스노우보드");
        if (subtitle == null) subtitle = findContaining(root, "걷기/러닝 · 자전거 · 스키/스노보드");
        if (subtitle != null) subtitle.setText("걷기/러닝 · 자전거 · 등산/트레킹 · 스키/스노보드");

        TextView oldSki = findContaining(root, "스키 / 스노우보드");
        if (oldSki != null) {
            oldSki.setText(oldSki.getText().toString().replace("스키 / 스노우보드", "스키 / 스노보드"));
        }
    }

    private void attachSkiEntry(View root) {
        TextView title = findClickableContaining(root, "스키 / 스노보드");
        if (title == null) title = findClickableContaining(root, "스키 / 스노우보드");
        if (title == null) return;
        View card = clickableAncestor(title, root);
        if (card != null) card.setOnClickListener(v -> startActivity(new Intent(this, SkiActivity.class)));
    }

    private void attachHikingEntry(View root) {
        if (root.findViewWithTag(HIKING_TAG) != null) return;
        TextView skiTitle = findClickableContaining(root, "스키 / 스노보드");
        if (skiTitle == null) skiTitle = findClickableContaining(root, "스키 / 스노우보드");
        if (skiTitle == null) return;
        View skiCard = clickableAncestor(skiTitle, root);
        if (skiCard == null || !(skiCard.getParent() instanceof LinearLayout)) return;
        LinearLayout page = (LinearLayout) skiCard.getParent();
        int index = page.indexOfChild(skiCard);
        if (index < 0) return;

        LinearLayout card = new LinearLayout(this);
        card.setTag(HIKING_TAG);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(10), dp(16), dp(10));
        card.setBackground(round(0xFFFFFFFF, 18, 1, border()));
        card.setOnClickListener(v -> startActivity(new Intent(this, HikingActivity.class)));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("🥾  ⛰️  등산 / 트레킹", 18, textColor(), true);
        head.addView(title, new LinearLayout.LayoutParams(0, dp(40), 1f));
        TextView arrow = text("›", 28, primary2(), false);
        arrow.setGravity(Gravity.CENTER);
        head.addView(arrow, new LinearLayout.LayoutParams(dp(30), dp(40)));
        card.addView(head);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(9);
        page.addView(card, index, params);
    }

    private void compactActivityEntries(View root) {
        compactEntry(root, "걷기 / 러닝");
        compactEntry(root, "자전거");
        compactEntry(root, "스키 / 스노보드");
    }

    private void compactEntry(View root, String titleText) {
        TextView title = findClickableContaining(root, titleText);
        if (title == null) return;
        View card = clickableAncestor(title, root);
        if (!(card instanceof ViewGroup)) return;
        int wanted = dp(10);
        if (card.getPaddingTop() != wanted || card.getPaddingBottom() != wanted) {
            card.setPadding(dp(16), wanted, dp(16), wanted);
        }
        hideRecordDescription((ViewGroup) card);
        TextView arrow = findText((ViewGroup) card, "›");
        if (arrow != null) {
            ViewGroup.LayoutParams lp = arrow.getLayoutParams();
            if (lp != null && (lp.width != dp(30) || lp.height != dp(40))) {
                lp.width = dp(30);
                lp.height = dp(40);
                arrow.setLayoutParams(lp);
            }
        }
        ViewGroup.LayoutParams cardLp = card.getLayoutParams();
        if (cardLp instanceof LinearLayout.LayoutParams
                && ((LinearLayout.LayoutParams) cardLp).bottomMargin != dp(9)) {
            ((LinearLayout.LayoutParams) cardLp).bottomMargin = dp(9);
            card.setLayoutParams(cardLp);
        }
    }

    private void hideRecordDescription(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) {
                CharSequence value = ((TextView) child).getText();
                String s = value == null ? "" : value.toString();
                if ((s.contains("기록합니다") || s.contains("보여줍니다")) && child.getVisibility() != View.GONE) {
                    child.setVisibility(View.GONE);
                }
            } else if (child instanceof ViewGroup) {
                hideRecordDescription((ViewGroup) child);
            }
        }
    }

    private TextView findClickableContaining(View root, String wanted) {
        if (root instanceof TextView) {
            CharSequence value = ((TextView) root).getText();
            if (value != null && value.toString().contains(wanted)
                    && clickableAncestor(root, findViewById(android.R.id.content)) != null) {
                return (TextView) root;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findClickableContaining(group.getChildAt(i), wanted);
                if (found != null) return found;
            }
        }
        return null;
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
                .getString("yamone_theme", "pink"));
    }

    private int textColor() { return pink() ? 0xFF4B2633 : 0xFF153633; }
    private int primary2() { return pink() ? 0xFFE94778 : 0xFF159A7A; }
    private int border() { return pink() ? 0xFFFFD7E3 : 0xFFD7EFE7; }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);
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
