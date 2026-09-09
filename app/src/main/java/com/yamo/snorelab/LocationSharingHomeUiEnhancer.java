package com.yamo.snorelab;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.WeakHashMap;

/** Replaces the old Home hero with the approved Yamone activity/location quick menu. */
public final class LocationSharingHomeUiEnhancer {
    private static final String PANEL_TAG = "yamone_home_quick_panel_v4";
    private static final String ACTIVE_TAG = "yamone_home_location_active_v4";
    private static final WeakHashMap<MainActivity, ViewTreeObserver.OnGlobalLayoutListener> LAYOUT_LISTENERS = new WeakHashMap<>();
    private static final WeakHashMap<MainActivity, LocationSharingUiBus.Listener> STATE_LISTENERS = new WeakHashMap<>();

    private LocationSharingHomeUiEnhancer() {}

    public static synchronized void attach(MainActivity activity) {
        if (activity == null) return;
        View decor = activity.getWindow().getDecorView();
        if (!LAYOUT_LISTENERS.containsKey(activity)) {
            ViewTreeObserver.OnGlobalLayoutListener listener = () -> enhance(activity);
            decor.getViewTreeObserver().addOnGlobalLayoutListener(listener);
            LAYOUT_LISTENERS.put(activity, listener);
        }
        if (!STATE_LISTENERS.containsKey(activity)) {
            LocationSharingUiBus.Listener listener = () -> activity.runOnUiThread(() -> enhance(activity));
            LocationSharingUiBus.add(listener);
            STATE_LISTENERS.put(activity, listener);
        }
        decor.post(() -> enhance(activity));
    }

    public static synchronized void detach(MainActivity activity) {
        if (activity == null) return;
        View decor = activity.getWindow().getDecorView();
        ViewTreeObserver.OnGlobalLayoutListener layout = LAYOUT_LISTENERS.remove(activity);
        if (layout != null && decor.getViewTreeObserver().isAlive()) {
            decor.getViewTreeObserver().removeOnGlobalLayoutListener(layout);
        }
        LocationSharingUiBus.Listener state = STATE_LISTENERS.remove(activity);
        if (state != null) LocationSharingUiBus.remove(state);
    }

    public static void refresh(MainActivity activity) {
        if (activity != null) activity.runOnUiThread(() -> enhance(activity));
    }

    private static void enhance(MainActivity activity) {
        View root = activity.getWindow().getDecorView();
        TextView heroTitle = findExactText(root, "편하게 기록하고, 천천히 쌓아가요.");
        LinearLayout panel = findPanel(root);
        if (panel != null) {
            refreshActive(activity, panel);
            return;
        }
        if (heroTitle == null) return;

        View hero = heroTitle;
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
        int index = page.indexOfChild(hero);
        if (index < 0) return;
        ViewGroup.LayoutParams oldParams = hero.getLayoutParams();
        page.removeView(hero);

        panel = buildPanel(activity);
        panel.setTag(PANEL_TAG);
        page.addView(panel, index, oldParams);
        refreshActive(activity, panel);
    }

    private static LinearLayout findPanel(View root) {
        View found = root.findViewWithTag(PANEL_TAG);
        return found instanceof LinearLayout ? (LinearLayout) found : null;
    }

    private static LinearLayout buildPanel(MainActivity activity) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, dp(activity, 4), 0, dp(activity, 20));

        LinearLayout active = buildActiveCard(activity);
        active.setTag(ACTIVE_TAG);
        panel.addView(active, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(tile(activity, YamonePastelArtView.MODE_ACTIVITY,
                        "활동", "걷기 · 러닝 · 자전거\n등산 · 스키",
                        v -> activity.startActivity(new Intent(activity, LocationExerciseActivity.class))),
                new LinearLayout.LayoutParams(0, dp(activity, 160), 1f));
        LinearLayout.LayoutParams locP = new LinearLayout.LayoutParams(0, dp(activity, 160), 1f);
        locP.leftMargin = dp(activity, 10);
        row.addView(tile(activity, YamonePastelArtView.MODE_LOCATION,
                        "위치 공유", "친구와 서로의\n마지막 위치 확인",
                        v -> activity.startActivity(new Intent(activity, LocationSharingActivityV2.class))), locP);
        panel.addView(row);

        LinearLayout banner = new LinearLayout(activity);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        banner.setBackground(round(activity, pink(activity) ? 0xFFFFEFF4 : 0xFFE9FBF5, 20, 0, 0));
        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(activity, "함께하면", 12, primary2(activity), true));
        copy.addView(text(activity, "더 즐거운 하루", 15, textColor(activity), true));
        copy.addView(text(activity, "Yamone ♥", 14, primary(activity), true));
        banner.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        YamonePastelArtView art = new YamonePastelArtView(activity, YamonePastelArtView.MODE_LOCATION_SCENE);
        banner.addView(art, new LinearLayout.LayoutParams(dp(activity, 126), dp(activity, 82)));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 106));
        bp.topMargin = dp(activity, 12);
        panel.addView(banner, bp);
        return panel;
    }

    private static LinearLayout tile(MainActivity activity, int mode, String title, String desc, View.OnClickListener click) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(activity, 13), dp(activity, 12), dp(activity, 13), dp(activity, 14));
        tile.setBackground(round(activity, 0xFFFFFFFF, 22, 1, border(activity)));
        tile.setOnClickListener(click);
        if (android.os.Build.VERSION.SDK_INT >= 21) tile.setElevation(dp(activity, 1));

        YamonePastelArtView icon = new YamonePastelArtView(activity, mode);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(activity, 64), dp(activity, 64)));
        TextView t = text(activity, title, 15, textColor(activity), true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(activity, 5), 0, dp(activity, 3));
        tile.addView(t);
        TextView d = text(activity, desc, 10, muted(activity), false);
        d.setGravity(Gravity.CENTER);
        tile.addView(d);
        return tile;
    }

    private static LinearLayout buildActiveCard(MainActivity activity) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 15));
        card.setBackground(round(activity, pink(activity) ? 0xFFFFF4F7 : 0xFFF2FCF8, 22, 1, primary(activity)));
        card.setOnClickListener(v -> activity.startActivity(new Intent(activity, LocationSharingActivityV2.class)));

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        YamonePastelArtView pin = new YamonePastelArtView(activity, YamonePastelArtView.MODE_LOCATION);
        head.addView(pin, new LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)));
        LinearLayout words = new LinearLayout(activity);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(activity, 9), 0, 0, 0);
        TextView title = text(activity, "● 위치 공유 중", 15, primary2(activity), true);
        words.addView(title);
        TextView room = text(activity, "", 13, textColor(activity), true);
        room.setTag("active_room");
        room.setPadding(0, dp(activity, 2), 0, 0);
        words.addView(room);
        head.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = text(activity, "›", 27, primary2(activity), false);
        head.addView(arrow);
        card.addView(head);

        TextView info = text(activity, "", 11, muted(activity), false);
        info.setTag("active_info");
        info.setPadding(0, dp(activity, 8), 0, 0);
        card.addView(info);

        TextView action = text(activity, "공유 중인 방 보기", 12, primary2(activity), true);
        action.setGravity(Gravity.CENTER);
        action.setBackground(round(activity, pink(activity) ? 0xFFFFE3EC : 0xFFDFF8F0, 15, 0, 0));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44));
        ap.topMargin = dp(activity, 11);
        card.addView(action, ap);
        return card;
    }

    private static void refreshActive(MainActivity activity, LinearLayout panel) {
        View active = panel.findViewWithTag(ACTIVE_TAG);
        if (!(active instanceof LinearLayout)) return;
        boolean sharing = LocationSharingStateStore.isActive(activity);
        active.setVisibility(sharing ? View.VISIBLE : View.GONE);
        ViewGroup.LayoutParams lp = active.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) lp;
            p.bottomMargin = sharing ? dp(activity, 12) : 0;
            active.setLayoutParams(p);
        }
        if (!sharing) return;
        TextView room = active.findViewWithTag("active_room");
        TextView info = active.findViewWithTag("active_info");
        if (room != null) room.setText(LocationSharingStateStore.roomName(activity));
        if (info != null) {
            int count = LocationSharingStateStore.memberCount(activity);
            int interval = Math.max(1, LocationSharingStateStore.intervalSeconds(activity) / 60);
            String remaining = LocationSharingStateStore.remainingText(activity);
            info.setText("👥 " + count + "명  ·  ⏱ " + remaining + "  ·  위치 " + interval + "분 간격");
        }
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
