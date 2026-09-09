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

/** Replaces the old Home hero with the approved Yamone rounded quick-menu layout. */
public final class LocationSharingHomeUiEnhancer {
    private static final String PANEL_TAG = "yamone_home_quick_panel_v3";
    private static final String ACTIVE_TAG = "yamone_home_location_active_v3";
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
        TextView heroTitle = findExactText(root, "편하게 기록하고, 천천히 쌓아가요.");
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

        View oldPanel = page.findViewWithTag(PANEL_TAG);
        if (oldPanel != null) {
            refreshActive(activity, oldPanel);
            return;
        }

        int index = page.indexOfChild(hero);
        if (index < 0) return;
        ViewGroup.LayoutParams oldParams = hero.getLayoutParams();
        page.removeView(hero);

        LinearLayout panel = buildPanel(activity);
        panel.setTag(PANEL_TAG);
        page.addView(panel, index, oldParams);
        refreshActive(activity, panel);
    }

    private static LinearLayout buildPanel(MainActivity activity) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, dp(activity, 4), 0, dp(activity, 8));

        LinearLayout active = buildActiveCard(activity);
        active.setTag(ACTIVE_TAG);
        panel.addView(active, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row1 = new LinearLayout(activity);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(tile(activity, YamonePastelArtView.MODE_ACTIVITY,
                        "활동 기록", "걷기 · 러닝 · 자전거\n스키 등",
                        v -> activity.startActivity(new Intent(activity, ExerciseActivity.class))),
                new LinearLayout.LayoutParams(0, dp(activity, 154), 1f));
        LinearLayout.LayoutParams locP = new LinearLayout.LayoutParams(0, dp(activity, 154), 1f);
        locP.leftMargin = dp(activity, 10);
        row1.addView(tile(activity, YamonePastelArtView.MODE_LOCATION,
                        "위치 공유", "지금, 친구와\n함께 있어요",
                        v -> activity.startActivity(new Intent(activity, LocationSharingActivityV2.class))), locP);
        panel.addView(row1);

        LinearLayout row2 = new LinearLayout(activity);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row2P = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row2P.topMargin = dp(activity, 10);
        panel.addView(row2, row2P);

        row2.addView(tile(activity, YamonePastelArtView.MODE_LIFT,
                        "리프트 정보", "스키장 리프트\n대기 정보를 봐요",
                        v -> activity.startActivity(new Intent(activity, SkiWaitTimesActivity.class))),
                new LinearLayout.LayoutParams(0, dp(activity, 154), 1f));
        LinearLayout.LayoutParams statP = new LinearLayout.LayoutParams(0, dp(activity, 154), 1f);
        statP.leftMargin = dp(activity, 10);
        row2.addView(tile(activity, YamonePastelArtView.MODE_STATS,
                        "통계", "나의 활동과\n기록을 한눈에",
                        v -> activity.startActivity(new Intent(activity, ExerciseActivity.class))), statP);

        LinearLayout banner = new LinearLayout(activity);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        banner.setBackground(round(activity, pink(activity) ? 0xFFFFEFF4 : 0xFFE9FBF5, 20, 0, 0));
        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(activity, "함께하는", 12, primary2(activity), true));
        copy.addView(text(activity, "더 즐거운 야외활동", 15, textColor(activity), true));
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
        tile.setPadding(dp(activity, 13), dp(activity, 12), dp(activity, 13), dp(activity, 11));
        tile.setBackground(round(activity, 0xFFFFFFFF, 22, 1, border(activity)));
        tile.setOnClickListener(click);
        if (android.os.Build.VERSION.SDK_INT >= 21) tile.setElevation(dp(activity, 1));

        YamonePastelArtView icon = new YamonePastelArtView(activity, mode);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(activity, 62), dp(activity, 62)));
        TextView t = text(activity, title, 14, textColor(activity), true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(activity, 4), 0, dp(activity, 2));
        tile.addView(t);
        TextView d = text(activity, desc, 10, muted(activity), false);
        d.setGravity(Gravity.CENTER);
        tile.addView(d);
        return tile;
    }

    private static LinearLayout buildActiveCard(MainActivity activity) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        card.setBackground(round(activity, 0xFFFFFFFF, 22, 1, primary(activity)));
        card.setOnClickListener(v -> activity.startActivity(new Intent(activity, LocationSharingActivityV2.class)));

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        YamonePastelArtView pin = new YamonePastelArtView(activity, YamonePastelArtView.MODE_LOCATION);
        head.addView(pin, new LinearLayout.LayoutParams(dp(activity, 38), dp(activity, 38)));
        TextView title = text(activity, "위치 공유 중", 15, textColor(activity), true);
        title.setTag("active_title");
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(activity, 9);
        head.addView(title, tp);
        TextView arrow = text(activity, "›", 27, primary2(activity), false);
        head.addView(arrow);
        card.addView(head);

        TextView room = text(activity, "", 13, textColor(activity), true);
        room.setTag("active_room");
        room.setPadding(0, dp(activity, 7), 0, 0);
        card.addView(room);
        TextView info = text(activity, "", 11, muted(activity), false);
        info.setTag("active_info");
        info.setPadding(0, dp(activity, 5), 0, 0);
        card.addView(info);

        TextView action = text(activity, "공유 중인 방 보기", 12, primary2(activity), true);
        action.setGravity(Gravity.CENTER);
        action.setBackground(round(activity, pink(activity) ? 0xFFFFE5ED : 0xFFE0F8F1, 15, 0, 0));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 42));
        ap.topMargin = dp(activity, 10);
        card.addView(action, ap);
        return card;
    }

    private static void refreshActive(MainActivity activity, View panel) {
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
            info.setText("참여 " + count + "명 · " + remaining + " · " + interval + "분 간격");
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
