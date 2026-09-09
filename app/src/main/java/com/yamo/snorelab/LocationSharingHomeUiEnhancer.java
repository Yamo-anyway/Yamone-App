package com.yamo.snorelab;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.WeakHashMap;

/** Adds the standalone location-sharing entry to Home without modifying MainActivity's established layout code. */
public final class LocationSharingHomeUiEnhancer {
    private static final String CARD_TAG = "yamone_home_location_share_card_v1";
    private static final String TITLE_TAG = "yamone_home_location_share_title_v1";
    private static final String DESC_TAG = "yamone_home_location_share_desc_v1";
    private static final String ACTION_TAG = "yamone_home_location_share_action_v1";
    private static final WeakHashMap<MainActivity, ViewTreeObserver.OnGlobalLayoutListener> LISTENERS = new WeakHashMap<>();

    private LocationSharingHomeUiEnhancer() {}

    public static synchronized void attach(MainActivity activity) {
        if (activity == null) return;
        View decor = activity.getWindow().getDecorView();
        if (!LISTENERS.containsKey(activity)) {
            ViewTreeObserver.OnGlobalLayoutListener listener = () -> enhance(activity);
            decor.getViewTreeObserver().addOnGlobalLayoutListener(listener);
            LISTENERS.put(activity, listener);
        }
        decor.post(() -> enhance(activity));
    }

    public static synchronized void detach(MainActivity activity) {
        if (activity == null) return;
        ViewTreeObserver.OnGlobalLayoutListener listener = LISTENERS.remove(activity);
        View decor = activity.getWindow().getDecorView();
        if (listener != null && decor.getViewTreeObserver().isAlive()) {
            decor.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        }
    }

    private static void enhance(MainActivity activity) {
        View root = activity.getWindow().getDecorView();
        TextView homeHeroTitle = findExactText(root, "편하게 기록하고, 천천히 쌓아가요.");
        if (homeHeroTitle == null) return;

        View hero = homeHeroTitle;
        while (hero.getParent() instanceof View && !(hero.getParent() instanceof android.widget.ScrollView)) {
            View parent = (View) hero.getParent();
            if (parent.getParent() instanceof LinearLayout) {
                hero = parent;
                break;
            }
            hero = parent;
        }
        if (!(hero.getParent() instanceof LinearLayout)) return;
        LinearLayout page = (LinearLayout) hero.getParent();

        View existing = page.findViewWithTag(CARD_TAG);
        if (existing == null) {
            LinearLayout card = buildCard(activity);
            card.setTag(CARD_TAG);
            int index = Math.min(page.indexOfChild(hero) + 1, page.getChildCount());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(activity, 12);
            page.addView(card, index, params);
            existing = card;
        }
        updateCard(activity, existing);
    }

    private static LinearLayout buildCard(MainActivity activity) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 18), dp(activity, 16), dp(activity, 18), dp(activity, 16));
        card.setBackground(round(activity, cardColor(activity), 22, 1, border(activity)));

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = text(activity, "📍", 24, textColor(activity), false);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(activity, card2(activity), 17, 0, 0));
        top.addView(icon, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));

        LinearLayout words = new LinearLayout(activity);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(activity, 12), 0, 0, 0);
        TextView title = text(activity, "위치 공유", 16, textColor(activity), true);
        title.setTag(TITLE_TAG);
        words.addView(title);
        TextView desc = text(activity, "방을 만들거나 참여해 서로의 현재 위치를 확인해요.", 11, muted(activity), false);
        desc.setTag(DESC_TAG);
        desc.setPadding(0, dp(activity, 3), 0, 0);
        words.addView(desc);
        top.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(top);

        TextView action = text(activity, "위치 공유 열기", 13, Color.WHITE, true);
        action.setTag(ACTION_TAG);
        action.setGravity(Gravity.CENTER);
        action.setBackground(round(activity, primary(activity), 16, 0, 0));
        action.setOnClickListener(v -> activity.startActivity(new Intent(activity, LocationSharingActivityV2.class)));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 48));
        ap.topMargin = dp(activity, 13);
        card.addView(action, ap);
        return card;
    }

    private static void updateCard(MainActivity activity, View card) {
        boolean active = LocationSharingStateStore.isActive(activity);
        TextView title = card.findViewWithTag(TITLE_TAG);
        TextView desc = card.findViewWithTag(DESC_TAG);
        TextView action = card.findViewWithTag(ACTION_TAG);
        if (title == null || desc == null || action == null) return;

        if (active) {
            title.setText("● 위치 공유 중 · " + LocationSharingStateStore.roomName(activity));
            title.setTextColor(primary2(activity));
            String remaining = LocationSharingStateStore.remainingText(activity);
            int count = LocationSharingStateStore.memberCount(activity);
            desc.setText("참여 " + count + "명" + (remaining.isEmpty() ? "" : " · " + remaining) + " · 현재 위치만 공유 중");
            action.setText("공유 중인 방 보기");
        } else {
            title.setText("위치 공유");
            title.setTextColor(textColor(activity));
            desc.setText("방을 만들거나 참여해 서로의 현재 위치를 확인해요.");
            action.setText("위치 공유 열기");
        }
        card.setBackground(round(activity, cardColor(activity), 22, 1, border(activity)));
        action.setBackground(round(activity, primary(activity), 16, 0, 0));
    }

    private static TextView findExactText(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findExactText(group.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean pink(MainActivity activity) {
        return "pink".equals(activity.getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "mint"));
    }

    private static int cardColor(MainActivity a) { return 0xFFFFFFFF; }
    private static int card2(MainActivity a) { return pink(a) ? 0xFFFFEEF3 : 0xFFF0FAF6; }
    private static int textColor(MainActivity a) { return pink(a) ? 0xFF4B2633 : 0xFF153633; }
    private static int muted(MainActivity a) { return pink(a) ? 0xFF9A7180 : 0xFF718984; }
    private static int primary(MainActivity a) { return pink(a) ? 0xFFFF769F : 0xFF56D1B3; }
    private static int primary2(MainActivity a) { return pink(a) ? 0xFFE94778 : 0xFF159A7A; }
    private static int border(MainActivity a) { return pink(a) ? 0xFFFFD7E3 : 0xFFD7EFE7; }

    private static TextView text(MainActivity activity, String value, int sp, int color, boolean bold) {
        TextView v = new TextView(activity);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setLineSpacing(0, 1.08f);
        return v;
    }

    private static GradientDrawable round(MainActivity activity, int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(activity, radiusDp));
        if (strokeDp > 0) d.setStroke(dp(activity, strokeDp), strokeColor);
        return d;
    }

    private static int dp(MainActivity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
