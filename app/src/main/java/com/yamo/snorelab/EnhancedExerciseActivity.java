package com.yamo.snorelab;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Keeps the existing ExerciseActivity UI intact and upgrades only the weekly
 * chart with activity-family and metric filters.
 */
public class EnhancedExerciseActivity extends UploadExerciseActivity {
    private int CARD = 0xFFFFFFFF;
    private int CARD2 = 0xFFFFEEF3;
    private int TEXT = 0xFF4B2633;
    private int MUTED = 0xFF9A7180;
    private int PRIMARY = 0xFFFF769F;
    private int PRIMARY2 = 0xFFE94778;
    private int BORDER = 0xFFFFD7E3;
    private static final String WEEKLY_TAG = "yamone_weekly_activity_filter";

    private String weeklyMetric = "distance";
    private String weeklyActivity = "all";
    private FrameLayout activityContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyPalette();
        attachWeeklyChartEnhancer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPalette();
        scheduleWeeklyEnhance();
    }

    private void applyPalette() {
        boolean pink = "pink".equals(getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "pink"));
        CARD = 0xFFFFFFFF;
        CARD2 = pink ? 0xFFFFEEF3 : 0xFFF0FAF6;
        TEXT = pink ? 0xFF4B2633 : 0xFF153633;
        MUTED = pink ? 0xFF9A7180 : 0xFF718984;
        PRIMARY = pink ? 0xFFFF769F : 0xFF56D1B3;
        PRIMARY2 = pink ? 0xFFE94778 : 0xFF159A7A;
        BORDER = pink ? 0xFFFFD7E3 : 0xFFD7EFE7;
    }

    private void attachWeeklyChartEnhancer() {
        View host = findViewById(android.R.id.content);
        if (!(host instanceof ViewGroup)) return;
        ViewGroup contentHost = (ViewGroup) host;
        if (contentHost.getChildCount() == 0) return;

        View root = contentHost.getChildAt(0);
        if (!(root instanceof ViewGroup)) return;
        ViewGroup rootGroup = (ViewGroup) root;
        for (int i = 0; i < rootGroup.getChildCount(); i++) {
            View child = rootGroup.getChildAt(i);
            if (child instanceof FrameLayout) {
                activityContent = (FrameLayout) child;
                break;
            }
        }
        if (activityContent == null) return;

        activityContent.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
            @Override public void onChildViewAdded(View parent, View child) { scheduleWeeklyEnhance(); }
            @Override public void onChildViewRemoved(View parent, View child) { }
        });
        scheduleWeeklyEnhance();
    }

    private void scheduleWeeklyEnhance() {
        if (activityContent == null) return;
        activityContent.post(this::enhanceWeeklyChart);
    }

    private void enhanceWeeklyChart() {
        if (activityContent == null) return;
        if (activityContent.findViewWithTag(WEEKLY_TAG) != null) return;

        TextView title = findText(activityContent, "최근 7일 활동");
        if (title == null || !(title.getParent() instanceof ViewGroup)) return;
        View oldCard = (View) title.getParent();
        if (!(oldCard.getParent() instanceof ViewGroup)) return;

        ViewGroup page = (ViewGroup) oldCard.getParent();
        int index = page.indexOfChild(oldCard);
        if (index < 0) return;
        ViewGroup.LayoutParams params = oldCard.getLayoutParams();
        page.removeViewAt(index);
        page.addView(buildWeeklyChart(), index, params);
    }

    private void rebuildWeeklyChart() {
        if (activityContent == null) return;
        View oldCard = activityContent.findViewWithTag(WEEKLY_TAG);
        if (oldCard == null || !(oldCard.getParent() instanceof ViewGroup)) {
            enhanceWeeklyChart();
            return;
        }
        ViewGroup page = (ViewGroup) oldCard.getParent();
        int index = page.indexOfChild(oldCard);
        ViewGroup.LayoutParams params = oldCard.getLayoutParams();
        page.removeViewAt(index);
        page.addView(buildWeeklyChart(), index, params);
    }

    private LinearLayout buildWeeklyChart() {
        boolean timeMode = "time".equals(weeklyMetric);
        long todayStart = startOfDay(System.currentTimeMillis());
        long[] dayStarts = new long[7];
        long[] distances = new long[7];
        long[] durations = new long[7];

        java.util.Calendar cursor = java.util.Calendar.getInstance();
        cursor.setTimeInMillis(todayStart);
        cursor.add(java.util.Calendar.DAY_OF_MONTH, -6);
        for (int i = 0; i < 7; i++) {
            dayStarts[i] = cursor.getTimeInMillis();
            cursor.add(java.util.Calendar.DAY_OF_MONTH, 1);
        }

        for (File dir : WalkingStore.listSessions(this)) {
            JSONObject meta = WalkingStore.readMeta(dir);
            if (!"complete".equals(meta.optString("status"))) continue;
            String type = meta.optString("type", "walking");
            if (!matchesWeeklyActivity(type)) continue;

            long start = meta.optLong("startEpochMs", 0);
            if (start <= 0) continue;
            long sessionDay = startOfDay(start);
            for (int i = 0; i < 7; i++) {
                if (sessionDay == dayStarts[i]) {
                    distances[i] += Math.max(0, meta.optLong("distanceM", 0));
                    durations[i] += Math.max(0, meta.optLong("durationMs", 0));
                    break;
                }
            }
        }

        long maxValue = 0;
        long totalValue = 0;
        int activeDays = 0;
        for (int i = 0; i < 7; i++) {
            long value = timeMode ? durations[i] : distances[i];
            maxValue = Math.max(maxValue, value);
            totalValue += value;
            if (value > 0) activeDays++;
        }

        LinearLayout chart = card();
        chart.setTag(WEEKLY_TAG);
        chart.addView(text("최근 7일 활동", 15, TEXT, true));

        LinearLayout activityRow = new LinearLayout(this);
        activityRow.setOrientation(LinearLayout.HORIZONTAL);
        activityRow.setPadding(0, dp(8), 0, 0);
        activityRow.addView(activityChip("all", "전체"), new LinearLayout.LayoutParams(0, dp(38), 1f));
        LinearLayout.LayoutParams walkRunParams = new LinearLayout.LayoutParams(0, dp(38), 1.35f);
        walkRunParams.leftMargin = dp(7);
        activityRow.addView(activityChip("walkrun", "걷기·러닝"), walkRunParams);
        LinearLayout.LayoutParams cyclingParams = new LinearLayout.LayoutParams(0, dp(38), 1f);
        cyclingParams.leftMargin = dp(7);
        activityRow.addView(activityChip("cycling", "자전거"), cyclingParams);
        chart.addView(activityRow);

        LinearLayout metricRow = new LinearLayout(this);
        metricRow.setOrientation(LinearLayout.HORIZONTAL);
        metricRow.setPadding(0, dp(7), 0, dp(4));
        metricRow.addView(metricChip("distance", "거리"), new LinearLayout.LayoutParams(0, dp(38), 1f));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(0, dp(38), 1f);
        timeParams.leftMargin = dp(7);
        metricRow.addView(metricChip("time", "활동시간"), timeParams);
        chart.addView(metricRow);

        String scope = weeklyScopeLabel();
        String summaryText = timeMode
                ? String.format(Locale.KOREAN, "%s · 합계 %s · 활동한 날 %d일", scope, formatClock(totalValue), activeDays)
                : String.format(Locale.KOREAN, "%s · 합계 %.2f km · 활동한 날 %d일", scope, totalValue / 1000.0, activeDays);
        TextView summary = text(summaryText, 12, MUTED, false);
        summary.setPadding(0, dp(4), 0, dp(10));
        chart.addView(summary);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);

        for (int i = 0; i < 7; i++) {
            boolean today = i == 6;
            long value = timeMode ? durations[i] : distances[i];
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);

            String valueText;
            if (timeMode) {
                if (durations[i] <= 0) valueText = "0";
                else if (durations[i] < 3_600_000L) valueText = String.format(Locale.KOREAN, "%.0f분", durations[i] / 60_000.0);
                else valueText = String.format(Locale.KOREAN, "%.1fh", durations[i] / 3_600_000.0);
            } else {
                valueText = distances[i] <= 0 ? "0" : String.format(Locale.KOREAN, "%.1f", distances[i] / 1000.0);
            }
            TextView valueLabel = text(valueText, 9, today ? PRIMARY2 : MUTED, true);
            valueLabel.setGravity(Gravity.CENTER);
            column.addView(valueLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)));

            View spacer = new View(this);
            column.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

            int barHeight = value <= 0 || maxValue <= 0
                    ? dp(4)
                    : dp(14) + (int) Math.round(dp(76) * (value / (double) maxValue));
            View bar = new View(this);
            bar.setBackground(round(today ? PRIMARY2 : PRIMARY, 7, 0, 0));
            column.addView(bar, new LinearLayout.LayoutParams(dp(18), barHeight));

            String day = new SimpleDateFormat("E", Locale.KOREAN).format(new Date(dayStarts[i]));
            String date = new SimpleDateFormat("M/d", Locale.KOREAN).format(new Date(dayStarts[i]));
            TextView label = text(today ? "오늘\n" + date : day + "\n" + date, 9, today ? PRIMARY2 : MUTED, today);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
            labelParams.topMargin = dp(5);
            column.addView(label, labelParams);
            row.addView(column, new LinearLayout.LayoutParams(0, dp(158), 1f));
        }

        chart.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(158)));
        TextView note = text(scope + " 완료 기록의 " + (timeMode ? "활동시간" : "거리") + "을 날짜별로 합산합니다.", 10, MUTED, false);
        note.setPadding(0, dp(8), 0, 0);
        chart.addView(note);
        return chart;
    }

    private TextView activityChip(String key, String label) {
        boolean selected = key.equals(weeklyActivity);
        TextView chip = chip(label, selected);
        chip.setOnClickListener(v -> {
            if (!key.equals(weeklyActivity)) {
                weeklyActivity = key;
                rebuildWeeklyChart();
            }
        });
        return chip;
    }

    private TextView metricChip(String key, String label) {
        boolean selected = key.equals(weeklyMetric);
        TextView chip = chip(label, selected);
        chip.setOnClickListener(v -> {
            if (!key.equals(weeklyMetric)) {
                weeklyMetric = key;
                rebuildWeeklyChart();
            }
        });
        return chip;
    }

    private TextView chip(String label, boolean selected) {
        TextView chip = text(label, 12, selected ? Color.WHITE : TEXT, true);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(round(selected ? PRIMARY2 : CARD2, 14, selected ? 0 : 1, BORDER));
        return chip;
    }

    private boolean matchesWeeklyActivity(String type) {
        if ("all".equals(weeklyActivity)) return true;
        if ("cycling".equals(weeklyActivity)) return "cycling".equals(type);
        return "walking".equals(type) || "running".equals(type) || "walkrun".equals(type);
    }

    private String weeklyScopeLabel() {
        if ("cycling".equals(weeklyActivity)) return "자전거";
        if ("walkrun".equals(weeklyActivity)) return "걷기·러닝";
        return "전체";
    }

    private TextView findText(View view, String target) {
        if (view instanceof TextView && target.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), target);
                if (found != null) return found;
            }
        }
        return null;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(round(CARD, 20, 1, BORDER));
        return card;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setLineSpacing(0, 1.08f);
        return view;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static long startOfDay(long epochMs) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(epochMs);
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static String formatClock(long ms) {
        long seconds = Math.max(0, ms / 1000);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long sec = seconds % 60;
        return hours > 0
                ? String.format(Locale.KOREAN, "%d:%02d:%02d", hours, minutes, sec)
                : String.format(Locale.KOREAN, "%02d:%02d", minutes, sec);
    }
}
