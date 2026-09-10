package com.yamo.snorelab;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Locale;

/** Injects richer local-only statistics into completed walk/run/cycle detail screens. */
public final class ActivityDetailStatsEnhancer {
    private static final String TAG = "yamone_activity_detail_stats_v1";

    private ActivityDetailStatsEnhancer() {}

    public static void apply(Activity activity) {
        if (activity == null) return;
        View decor = activity.getWindow().getDecorView();
        if (decor.findViewWithTag(TAG) != null) return;

        File dir = currentDetailDir(activity);
        if (dir == null || !dir.isDirectory()) return;
        JSONObject meta = WalkingStore.readMeta(dir);
        if (!"complete".equals(meta.optString("status"))) return;
        String type = meta.optString("type", "walking");
        if ("hiking".equals(type) || "ski".equals(type) || "snowboard".equals(type)) return;

        TextView routeTitle = findExact(decor, "이동 경로");
        if (routeTitle == null || !(routeTitle.getParent() instanceof ViewGroup)) return;
        View routeCard = (View) routeTitle.getParent();
        if (!(routeCard.getParent() instanceof LinearLayout)) return;
        LinearLayout page = (LinearLayout) routeCard.getParent();
        int insertAt = page.indexOfChild(routeCard) + 1;
        if (insertAt <= 0) return;

        ActivityRouteAnalysis.Result analysis = ActivityRouteAnalysis.analyze(dir, type);
        LinearLayout container = new LinearLayout(activity);
        container.setTag(TAG);
        container.setOrientation(LinearLayout.VERTICAL);

        long duration = Math.max(0L, meta.optLong("durationMs", 0L));
        long moving = Math.max(0L, meta.optLong("movingMs", 0L));
        long stopped = Math.max(0L, duration - moving);
        int movingRate = duration <= 0 ? 0 : Math.round(moving * 100f / duration);

        LinearLayout stats = card(activity);
        stats.addView(text(activity, "활동 통계", 15, textColor(activity), true));
        LinearLayout r1 = metricRow(activity,
                "이동률", movingRate + "%",
                "정지 시간", formatClock(stopped));
        r1.setPadding(0, dp(activity, 10), 0, 0);
        stats.addView(r1);
        LinearLayout r2 = metricRow(activity,
                "GPS 품질", analysis.gpsQualityPercent + "%",
                "기록 지점", String.format(Locale.KOREAN, "%,d", analysis.samples.size()));
        LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2p.topMargin = dp(activity, 8);
        stats.addView(r2, r2p);
        container.addView(stats, cardParams(activity));

        if (analysis.hasAltitude) {
            LinearLayout elevation = card(activity);
            elevation.addView(text(activity, "고도 변화", 15, textColor(activity), true));
            TextView sub = text(activity, "활동 전체 시간에 따른 GPS 고도 변화", 11, muted(activity), false);
            sub.setPadding(0, dp(activity, 4), 0, dp(activity, 4));
            elevation.addView(sub);
            ActivityProfileChartView chart = new ActivityProfileChartView(activity);
            chart.setData(analysis, ActivityProfileChartView.MODE_ALTITUDE);
            elevation.addView(chart, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 178)));
            LinearLayout a1 = metricRow(activity,
                    "최고 고도", meter(analysis.maxAltitudeM),
                    "최저 고도", meter(analysis.minAltitudeM));
            elevation.addView(a1);
            LinearLayout a2 = metricRow(activity,
                    "누적 상승", meter(analysis.ascentM),
                    "누적 하강", meter(analysis.descentM));
            LinearLayout.LayoutParams a2p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            a2p.topMargin = dp(activity, 8);
            elevation.addView(a2, a2p);
            container.addView(elevation, cardParams(activity));
        }

        if (analysis.hasSpeed) {
            LinearLayout speed = card(activity);
            speed.addView(text(activity, "속도 변화", 15, textColor(activity), true));
            TextView sub = text(activity, "GPS가 안정된 구간의 이동 속도 변화", 11, muted(activity), false);
            sub.setPadding(0, dp(activity, 4), 0, dp(activity, 4));
            speed.addView(sub);
            ActivityProfileChartView chart = new ActivityProfileChartView(activity);
            chart.setData(analysis, ActivityProfileChartView.MODE_SPEED);
            speed.addView(chart, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 168)));
            LinearLayout values = metricRow(activity,
                    "평균 기록 속도", String.format(Locale.KOREAN, "%.1f km/h", analysis.averageSpeedKmh),
                    "최고 유효 속도", String.format(Locale.KOREAN, "%.1f km/h", analysis.maxSpeedKmh));
            speed.addView(values);
            container.addView(speed, cardParams(activity));
        }

        if (container.getChildCount() > 0) page.addView(container, insertAt);
    }

    private static File currentDetailDir(Activity activity) {
        try {
            Field field = ExerciseActivity.class.getDeclaredField("detailDir");
            field.setAccessible(true);
            Object value = field.get(activity);
            return value instanceof File ? (File) value : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static LinearLayout metricRow(Activity activity, String l1, String v1, String l2, String v2) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(metric(activity, l1, v1), new LinearLayout.LayoutParams(0, dp(activity, 64), 1f));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(activity, 64), 1f);
        p.leftMargin = dp(activity, 8);
        row.addView(metric(activity, l2, v2), p);
        return row;
    }

    private static View metric(Activity activity, String label, String value) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackground(round(activity, card2(activity), 15, 1, border(activity)));
        TextView v = text(activity, value, 14, textColor(activity), true);
        v.setGravity(Gravity.CENTER);
        box.addView(v);
        TextView l = text(activity, label, 10, muted(activity), false);
        l.setGravity(Gravity.CENTER);
        box.addView(l);
        return box;
    }

    private static LinearLayout card(Activity activity) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 16), dp(activity, 15), dp(activity, 16), dp(activity, 15));
        card.setBackground(round(activity, 0xFFFFFFFF, 18, 1, border(activity)));
        return card;
    }

    private static LinearLayout.LayoutParams cardParams(Activity activity) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(activity, 13);
        return p;
    }

    private static TextView text(Activity activity, String value, int sp, int color, boolean bold) {
        TextView v = new TextView(activity);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setLineSpacing(0, 1.08f);
        return v;
    }

    private static TextView findExact(View view, String wanted) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && wanted.contentEquals(value)) return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findExact(group.getChildAt(i), wanted);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String meter(double value) {
        return Double.isNaN(value) ? "-" : String.format(Locale.KOREAN, "%.0f m", value);
    }

    private static String formatClock(long ms) {
        long s = Math.max(0L, ms / 1000L);
        long h = s / 3600L;
        long m = (s % 3600L) / 60L;
        long sec = s % 60L;
        return h > 0 ? String.format(Locale.KOREAN, "%d:%02d:%02d", h, m, sec)
                : String.format(Locale.KOREAN, "%02d:%02d", m, sec);
    }

    private static GradientDrawable round(Activity activity, int fill, int radiusDp, int strokeDp, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(activity, radiusDp));
        if (strokeDp > 0) d.setStroke(dp(activity, strokeDp), stroke);
        return d;
    }

    private static boolean pink(Activity a) {
        return "pink".equals(a.getSharedPreferences(SleepRecorderService.PREFS, 0).getString("yamone_theme", "mint"));
    }
    private static int textColor(Activity a) { return pink(a) ? 0xFF4B2633 : 0xFF153633; }
    private static int muted(Activity a) { return pink(a) ? 0xFF9A7180 : 0xFF718984; }
    private static int card2(Activity a) { return pink(a) ? 0xFFFFEEF3 : 0xFFF0FAF6; }
    private static int border(Activity a) { return pink(a) ? 0xFFFFD7E3 : 0xFFD7EFE7; }
    private static int dp(Activity a, float value) { return Math.round(value * a.getResources().getDisplayMetrics().density); }
}
