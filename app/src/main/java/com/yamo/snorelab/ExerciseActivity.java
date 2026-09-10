package com.yamo.snorelab;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExerciseActivity extends Activity {
    private static final int REQ_ACTIVITY = 4301;
    private static final int BG = 0xFF0B1324;
    private static final int CARD = 0xFF16243B;
    private static final int CARD2 = 0xFF111C31;
    private static final int TEXT = 0xFFF5F7FF;
    private static final int MUTED = 0xFF9DA9BF;
    private static final int PRIMARY = 0xFF6D72FF;
    private static final int PRIMARY2 = 0xFF8B8FFF;
    private static final int SUCCESS = 0xFF61D6A8;
    private static final int WARNING = 0xFFFFC56D;

    private FrameLayout content;
    private SharedPreferences runtime;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean detailOpen;
    private File detailDir;
    private String activityFilter = "all";
    private String weeklyChartMetric = "distance";

    private TextView liveDistance;
    private TextView liveTime;
    private TextView livePace;
    private TextView liveSteps;
    private TextView liveSpeed;
    private TextView liveMoving;
    private TextView liveAltitude;
    private TextView liveAccuracy;
    private TextView liveModeBreakdown;
    private TextView liveGoal;
    private ProgressBar liveProgress;
    private WalkingMapView liveRoute;
    private long lastRouteReload;

    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            boolean rec = runtime != null && runtime.getBoolean(WalkingRecorderService.KEY_RECORDING, false);
            if (!detailOpen && rec && liveDistance != null) updateLive();
            else if (!detailOpen && !rec && liveDistance != null) showHome();
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        runtime = getSharedPreferences(WalkingRecorderService.PREFS, MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildRoot();
        showHome();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresher);
        handler.post(refresher);
        if (!detailOpen) showHome();
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresher);
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (detailOpen) { detailOpen = false; detailDir = null; showHome(); return; }
        super.onBackPressed();
    }

    private void buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(7), dp(8), dp(8));
        nav.setBackgroundColor(0xFFFFFFFF);
        if (Build.VERSION.SDK_INT >= 21) nav.setElevation(dp(6));
        nav.addView(navItem("⌂\n홈", MUTED, v -> { startActivity(new Intent(this, MainActivity.class).putExtra("start_screen", "home")); finish(); }), new LinearLayout.LayoutParams(0, dp(60), 1f));
        nav.addView(navItem("🏃\n활동", PRIMARY2, v -> { detailOpen = false; showHome(); }), new LinearLayout.LayoutParams(0, dp(60), 1f));
        nav.addView(navItem("⏰\n알람", MUTED, v -> { startActivity(new Intent(this, AlarmActivity.class)); finish(); }), new LinearLayout.LayoutParams(0, dp(60), 1f));
        nav.addView(navItem("☾\n수면", MUTED, v -> { startActivity(new Intent(this, MainActivity.class).putExtra("start_screen", "sleep")); finish(); }), new LinearLayout.LayoutParams(0, dp(60), 1f));
        nav.addView(navItem("🎮\n미니게임", MUTED, v -> { startActivity(new Intent(this, MiniGameActivity.class)); finish(); }), new LinearLayout.LayoutParams(0, dp(60), 1f));
        root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (Build.VERSION.SDK_INT >= 21) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                int bottom = Build.VERSION.SDK_INT >= 30 ? insets.getInsets(WindowInsets.Type.navigationBars()).bottom : insets.getSystemWindowInsetBottom();
                v.setPadding(0, 0, 0, bottom);
                return insets;
            });
            root.requestApplyInsets();
        }
        setContentView(root);
    }

    private void showHome() {
        detailOpen = false;
        detailDir = null;
        // Clear stale live-view references before rebuilding the non-recording home.
        // Otherwise the 1-second refresher sees the old liveDistance reference and
        // calls showHome() repeatedly after recording has ended, resetting ScrollView to the top.
        liveDistance = null;
        liveTime = null;
        livePace = null;
        liveSteps = null;
        liveSpeed = null;
        liveMoving = null;
        liveAltitude = null;
        liveAccuracy = null;
        liveModeBreakdown = null;
        liveGoal = null;
        liveProgress = null;
        liveRoute = null;
        lastRouteReload = 0L;
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = page();
        scroll.addView(page);
        content.addView(scroll);

        page.addView(text("활동", 24, TEXT, true));
        TextView sub = text("걷기/러닝 · 자전거 · 스키/스노우보드", 12, MUTED, false);
        sub.setPadding(0, dp(3), 0, dp(14));
        page.addView(sub);

        if (runtime.getBoolean(WalkingRecorderService.KEY_RECORDING, false)) {
            buildLive(page);
        } else {
            buildStart(page);
            buildPeriodSummary(page);
            buildWeeklyDistanceChart(page);
            buildRecent(page);
        }

        LinearLayout privacy = card();
        privacy.addView(text("🔒 위치 기록 원칙", 15, TEXT, true));
        TextView p = text("GPS 경로와 활동 기록은 휴대폰 내부에만 저장됩니다. 지도 배경을 표시할 때만 OpenFreeMap 지도 타일을 인터넷으로 불러오며, 기록한 GPS 경로를 서버에 업로드하지 않습니다.", 12, MUTED, false);
        p.setPadding(0, dp(8), 0, 0);
        privacy.addView(p);
        page.addView(privacy, cardParams());
    }

    private void buildStart(LinearLayout page) {
        long totalDistance = 0;
        long totalDuration = 0;
        long totalSteps = 0;
        long walkRunDistance = 0;
        long walkRunDuration = 0;
        long cyclingDistance = 0;
        long cyclingDuration = 0;
        int activityCount = 0;
        String todayKey = new SimpleDateFormat("yyyyMMdd", Locale.KOREAN).format(new Date());

        for (File dir : WalkingStore.listSessions(this)) {
            JSONObject m = WalkingStore.readMeta(dir);
            if (!"complete".equals(m.optString("status"))) continue;
            long start = m.optLong("startEpochMs", 0);
            if (start <= 0) continue;
            String recordDay = new SimpleDateFormat("yyyyMMdd", Locale.KOREAN).format(new Date(start));
            if (!todayKey.equals(recordDay)) continue;

            long distance = Math.max(0, m.optLong("distanceM", 0));
            long duration = Math.max(0, m.optLong("durationMs", 0));
            long steps = Math.max(0, m.optLong("steps", 0));
            String type = m.optString("type", "walking");

            activityCount++;
            totalDistance += distance;
            totalDuration += duration;
            totalSteps += steps;
            if ("cycling".equals(type)) {
                cyclingDistance += distance;
                cyclingDuration += duration;
            } else {
                walkRunDistance += distance;
                walkRunDuration += duration;
            }
        }

        LinearLayout today = card();
        today.addView(text("오늘의 활동", 14, MUTED, true));
        TextView distance = text(String.format(Locale.KOREAN, "%.2f km", totalDistance / 1000.0), 32, TEXT, true);
        distance.setPadding(0, dp(5), 0, dp(7));
        today.addView(distance);

        LinearLayout totals = new LinearLayout(this);
        totals.setOrientation(LinearLayout.HORIZONTAL);
        TextView totalDistanceValue = metricValue(String.format(Locale.KOREAN, "%.2f km", totalDistance / 1000.0));
        TextView totalTimeValue = metricValue(formatClock(totalDuration));
        TextView totalStepsValue = metricValue(String.format(Locale.KOREAN, "%,d", totalSteps));
        totals.addView(metricBox("총 거리", totalDistanceValue), new LinearLayout.LayoutParams(0, dp(66), 1f));
        totals.addView(metricBox("활동 시간", totalTimeValue), new LinearLayout.LayoutParams(0, dp(66), 1f));
        totals.addView(metricBox("걸음", totalStepsValue), new LinearLayout.LayoutParams(0, dp(66), 1f));
        today.addView(totals);

        TextView walkRun = text(String.format(Locale.KOREAN, "🚶🏃 걷기/러닝  %.2f km · %s", walkRunDistance / 1000.0, formatClock(walkRunDuration)), 12, TEXT, true);
        walkRun.setPadding(0, dp(8), 0, dp(4));
        today.addView(walkRun);
        today.addView(text(String.format(Locale.KOREAN, "🚴 자전거  %.2f km · %s", cyclingDistance / 1000.0, formatClock(cyclingDuration)), 12, TEXT, true));

        TextView note = text(activityCount == 0
                ? "오늘 완료한 활동이 아직 없어요."
                : String.format(Locale.KOREAN, "오늘 완료한 활동 %,d회 · 걸음수는 걷기/러닝 기록만 합산합니다.", activityCount),
                11, MUTED, false);
        note.setPadding(0, dp(8), 0, 0);
        today.addView(note);
        page.addView(today, cardParams());

        addCategoryCard(page, "🚶  🏃", "걷기 / 러닝", "걷기·러닝 단일 모드 또는 자동 통합모드로 기록합니다.", v -> showWalkRunMenu());
        addCategoryCard(page, "🚴", "자전거", "거리와 현재·평균·최고 속도, 이동 경로를 기록합니다.", v -> showCyclingMenu());
        addCategoryCard(page, "⛷  🏂", "스키 / 스노우보드", "겨울 활동은 지금은 준비된 화면만 보여줍니다.", v -> showSkiPreview());
    }

    private void buildPeriodSummary(LinearLayout page) {
        long todayStart = startOfDay(System.currentTimeMillis());
        long weekStart = todayStart - 6L * 24L * 60L * 60L * 1000L;
        java.util.Calendar month = java.util.Calendar.getInstance();
        month.setTimeInMillis(todayStart);
        month.set(java.util.Calendar.DAY_OF_MONTH, 1);
        long monthStart = month.getTimeInMillis();

        long weekDistance = 0, weekDuration = 0, weekSteps = 0;
        long monthDistance = 0, monthDuration = 0, monthSteps = 0;
        int weekCount = 0, monthCount = 0;

        for (File dir : WalkingStore.listSessions(this)) {
            JSONObject m = WalkingStore.readMeta(dir);
            if (!"complete".equals(m.optString("status"))) continue;
            long start = m.optLong("startEpochMs", 0);
            if (start <= 0) continue;
            long distance = Math.max(0, m.optLong("distanceM", 0));
            long duration = Math.max(0, m.optLong("durationMs", 0));
            long steps = Math.max(0, m.optLong("steps", 0));

            if (start >= weekStart) {
                weekCount++;
                weekDistance += distance;
                weekDuration += duration;
                weekSteps += steps;
            }
            if (start >= monthStart) {
                monthCount++;
                monthDistance += distance;
                monthDuration += duration;
                monthSteps += steps;
            }
        }

        LinearLayout stats = card();
        stats.addView(text("누적 활동", 15, TEXT, true));
        TextView weekTitle = text("최근 7일", 12, PRIMARY2, true);
        weekTitle.setPadding(0, dp(10), 0, dp(2));
        stats.addView(weekTitle);
        stats.addView(text(String.format(Locale.KOREAN, "%.2f km · %s · %,d회 · %,d걸음", weekDistance / 1000.0, formatClock(weekDuration), weekCount, weekSteps), 12, TEXT, true));

        TextView monthTitle = text("이번 달", 12, PRIMARY2, true);
        monthTitle.setPadding(0, dp(10), 0, dp(2));
        stats.addView(monthTitle);
        stats.addView(text(String.format(Locale.KOREAN, "%.2f km · %s · %,d회 · %,d걸음", monthDistance / 1000.0, formatClock(monthDuration), monthCount, monthSteps), 12, TEXT, true));

        TextView note = text("완료된 활동만 합산하며, 자전거는 걸음수에 포함하지 않습니다.", 11, MUTED, false);
        note.setPadding(0, dp(8), 0, 0);
        stats.addView(note);
        page.addView(stats, cardParams());
    }

    private void buildWeeklyDistanceChart(LinearLayout page) {
        boolean timeMode = "time".equals(weeklyChartMetric);
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
            JSONObject m = WalkingStore.readMeta(dir);
            if (!"complete".equals(m.optString("status"))) continue;
            long start = m.optLong("startEpochMs", 0);
            if (start <= 0) continue;
            long sessionDay = startOfDay(start);
            for (int i = 0; i < 7; i++) {
                if (sessionDay == dayStarts[i]) {
                    distances[i] += Math.max(0, m.optLong("distanceM", 0));
                    durations[i] += Math.max(0, m.optLong("durationMs", 0));
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
        chart.addView(text("최근 7일 활동", 15, TEXT, true));

        LinearLayout toggles = new LinearLayout(this);
        toggles.setOrientation(LinearLayout.HORIZONTAL);
        toggles.setPadding(0, dp(8), 0, dp(4));
        toggles.addView(weeklyMetricChip("distance", "거리"), new LinearLayout.LayoutParams(0, dp(38), 1f));
        LinearLayout.LayoutParams timeToggle = new LinearLayout.LayoutParams(0, dp(38), 1f);
        timeToggle.leftMargin = dp(7);
        toggles.addView(weeklyMetricChip("time", "활동시간"), timeToggle);
        chart.addView(toggles);

        String summaryText = timeMode
                ? String.format(Locale.KOREAN, "합계 %s · 활동한 날 %d일", formatClock(totalValue), activeDays)
                : String.format(Locale.KOREAN, "합계 %.2f km · 활동한 날 %d일", totalValue / 1000.0, activeDays);
        TextView summary = text(summaryText, 12, MUTED, false);
        summary.setPadding(0, dp(4), 0, dp(10));
        chart.addView(summary);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);

        for (int i = 0; i < 7; i++) {
            final boolean today = i == 6;
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

            int barHeight;
            if (value <= 0 || maxValue <= 0) {
                barHeight = dp(4);
            } else {
                barHeight = dp(14) + (int) Math.round(dp(76) * (value / (double) maxValue));
            }
            View bar = new View(this);
            bar.setBackground(round(today ? PRIMARY2 : PRIMARY, 7, 0, 0));
            column.addView(bar, new LinearLayout.LayoutParams(dp(18), barHeight));

            String day = new SimpleDateFormat("E", Locale.KOREAN).format(new Date(dayStarts[i]));
            String date = new SimpleDateFormat("M/d", Locale.KOREAN).format(new Date(dayStarts[i]));
            TextView label = text(today ? "오늘\n" + date : day + "\n" + date, 9, today ? PRIMARY2 : MUTED, today);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
            lp.topMargin = dp(5);
            column.addView(label, lp);

            row.addView(column, new LinearLayout.LayoutParams(0, dp(158), 1f));
        }

        chart.addView(row, match(dp(158)));
        TextView note = text(timeMode
                ? "걷기·러닝·통합·자전거의 완료된 활동시간을 날짜별로 합산합니다."
                : "걷기·러닝·통합·자전거의 완료된 거리 기록을 날짜별로 합산합니다.", 10, MUTED, false);
        note.setPadding(0, dp(8), 0, 0);
        chart.addView(note);
        page.addView(chart, cardParams());
    }

    private TextView weeklyMetricChip(String key, String label) {
        boolean selected = key.equals(weeklyChartMetric);
        TextView chip = text(label, 12, selected ? Color.WHITE : MUTED, true);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(round(selected ? PRIMARY : CARD2, 12, selected ? 0 : 1, 0xFF35445F));
        chip.setOnClickListener(v -> {
            if (!key.equals(weeklyChartMetric)) {
                weeklyChartMetric = key;
                showHome();
            }
        });
        return chip;
    }

    private void addCategoryCard(LinearLayout page, String icon, String title, String desc, View.OnClickListener click) {
        LinearLayout c = card();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(icon + "  " + title, 18, TEXT, true));
        TextView d = text(desc, 12, MUTED, false);
        d.setPadding(0, dp(6), dp(8), 0);
        words.addView(d);
        head.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = text("›", 30, PRIMARY2, false);
        arrow.setGravity(Gravity.CENTER);
        head.addView(arrow, new LinearLayout.LayoutParams(dp(34), dp(60)));
        c.addView(head);
        c.setOnClickListener(click);
        page.addView(c, cardParams());
    }

    private void showWalkRunMenu() {
        detailOpen = true;
        detailDir = null;
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = page();
        scroll.addView(page);
        content.addView(scroll);
        page.addView(backHeader("걷기 / 러닝"));

        TextView guide = text("운동 방식을 선택하세요. 통합모드는 걷기와 러닝을 자동으로 구분합니다.", 12, MUTED, false);
        guide.setPadding(0, 0, 0, dp(12));
        page.addView(guide);

        addActivityStartCard(page, "walking", "🚶", "걷기", "예: 거리 5 + 제한시간 60 → 60분 안에 5km 목표");
        addActivityStartCard(page, "running", "🏃", "러닝", "예: 거리 10 + 제한시간 60 → 60분 안에 10km 목표");
        addActivityStartCard(page, "walkrun", "🚶🏃", "통합모드", "걷기와 러닝을 섞어 운동할 때 자동으로 구간을 구분합니다.");
    }

    private void showCyclingMenu() {
        detailOpen = true;
        detailDir = null;
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = page();
        scroll.addView(page);
        content.addView(scroll);
        page.addView(backHeader("자전거"));
        addActivityStartCard(page, "cycling", "🚴", "자전거", "예: 거리 20 + 제한시간 90 → 90분 안에 20km 목표");
    }

    private void showSkiPreview() {
        detailOpen = true;
        detailDir = null;
        content.removeAllViews();
        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.yamone_ski);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setBackgroundColor(BG);
        image.setPadding(dp(18), dp(18), dp(18), dp(18));
        content.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private View backHeader(String title) {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 34, TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> showHome());
        top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(52)));
        top.addView(text(title, 22, TEXT, true), new LinearLayout.LayoutParams(0, dp(52), 1f));
        return top;
    }

    private void addActivityStartCard(LinearLayout page, String type, String icon, String label, String example) {
        boolean cycling = "cycling".equals(type);
        boolean walkrun = "walkrun".equals(type);
        LinearLayout startCard = card();
        startCard.addView(text(icon + " " + label, 19, TEXT, true));
        String description;
        if (cycling) {
            description = "GPS 경로 · 거리 · 시간 · 이동/정지 · 현재/평균/최고 속도 · 1km 구간을 기록합니다.";
        } else if (walkrun) {
            description = "GPS와 걸음 정보를 이용해 걷기/러닝을 자동 구분합니다. 순간 속도 한 번으로는 모드를 바꾸지 않습니다.";
        } else {
            description = "GPS 경로 · 거리 · 시간 · 이동/정지 · 걸음수 · 페이스 · 1km 랩을 기록합니다.";
        }
        TextView desc = text(description, 12, MUTED, false);
        desc.setPadding(0, dp(6), 0, dp(12));
        startCard.addView(desc);

        startCard.addView(text("이번 " + label + " 목표 (선택)", 13, PRIMARY2, true));
        LinearLayout goals = new LinearLayout(this);
        goals.setOrientation(LinearLayout.HORIZONTAL);
        EditText km = numberField("거리 km");
        EditText minutes = numberField("제한시간 분");
        goals.addView(km, new LinearLayout.LayoutParams(0, dp(52), 1f));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(0, dp(52), 1f); gp.leftMargin = dp(8);
        goals.addView(minutes, gp);
        startCard.addView(goals);
        TextView hint = text(example, 11, MUTED, false);
        hint.setPadding(0, dp(6), 0, dp(12));
        startCard.addView(hint);

        Button start = actionButton("▶ " + label + " 기록 시작", true,
                v -> startExercise(type, km.getText().toString(), minutes.getText().toString()));
        startCard.addView(start, match(dp(56)));
        page.addView(startCard, cardParams());
    }

    private void buildLive(LinearLayout page) {
        String type = runtime.getString(WalkingRecorderService.KEY_ACTIVITY_TYPE, "walking");
        String label = activityLabel(type);
        String icon = activityIcon(type);
        boolean cycling = "cycling".equals(type);
        boolean walkrun = "walkrun".equals(type);

        LinearLayout hero = card();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text(icon + " " + label + " 기록 중", 18, TEXT, true), new LinearLayout.LayoutParams(0, dp(38), 1f));
        String stateText;
        if (runtime.getBoolean(WalkingRecorderService.KEY_PAUSED, false)) stateText = "일시정지";
        else if (walkrun) stateText = "running".equals(runtime.getString(WalkingRecorderService.KEY_AUTO_MOTION_MODE, "walking")) ? "현재 러닝" : "현재 걷기";
        else stateText = "GPS 기록";
        TextView state = text(stateText, 12,
                runtime.getBoolean(WalkingRecorderService.KEY_PAUSED, false) ? WARNING : SUCCESS, true);
        state.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        top.addView(state, new LinearLayout.LayoutParams(dp(100), dp(38)));
        hero.addView(top);

        liveDistance = text("0.00 km", 42, TEXT, true);
        liveDistance.setGravity(Gravity.CENTER);
        liveDistance.setPadding(0, dp(5), 0, 0);
        hero.addView(liveDistance, match(dp(64)));

        LinearLayout row1 = new LinearLayout(this); row1.setOrientation(LinearLayout.HORIZONTAL);
        liveTime = metricValue("00:00");
        livePace = metricValue(cycling ? "0.0 km/h" : "--'--\"/km");
        liveSteps = metricValue(cycling ? "0.0 km/h" : "0");
        row1.addView(metricBox("전체 시간", liveTime), new LinearLayout.LayoutParams(0, dp(74), 1f));
        row1.addView(metricBox(cycling ? "평균 속도" : "평균 페이스", livePace), new LinearLayout.LayoutParams(0, dp(74), 1f));
        row1.addView(metricBox(cycling ? "최고 속도" : "걸음", liveSteps), new LinearLayout.LayoutParams(0, dp(74), 1f));
        hero.addView(row1);

        LinearLayout row2 = new LinearLayout(this); row2.setOrientation(LinearLayout.HORIZONTAL);
        liveSpeed = metricValue("0.0 km/h"); liveMoving = metricValue("00:00"); liveAltitude = metricValue("-- m");
        row2.addView(metricBox("현재 속도", liveSpeed), new LinearLayout.LayoutParams(0, dp(70), 1f));
        row2.addView(metricBox("이동 시간", liveMoving), new LinearLayout.LayoutParams(0, dp(70), 1f));
        row2.addView(metricBox("GPS 고도", liveAltitude), new LinearLayout.LayoutParams(0, dp(70), 1f));
        hero.addView(row2);
        liveAccuracy = text("GPS 정확도 --", 11, MUTED, false); liveAccuracy.setGravity(Gravity.CENTER); hero.addView(liveAccuracy);
        if (walkrun) {
            liveModeBreakdown = text("자동 구분 준비 중", 12, PRIMARY2, true);
            liveModeBreakdown.setGravity(Gravity.CENTER);
            liveModeBreakdown.setPadding(0, dp(8), 0, 0);
            hero.addView(liveModeBreakdown);
        }
        page.addView(hero, cardParams());

        long gd = runtime.getLong(WalkingRecorderService.KEY_GOAL_DISTANCE_M, 0);
        long gt = runtime.getLong(WalkingRecorderService.KEY_GOAL_TIME_MS, 0);
        if (gd > 0 || gt > 0) {
            LinearLayout goal = card(); goal.addView(text("오늘의 이번 " + label + " 목표", 15, TEXT, true));
            liveGoal = text("", 13, MUTED, false); liveGoal.setPadding(0, dp(8), 0, dp(7)); goal.addView(liveGoal);
            liveProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); liveProgress.setMax(100); goal.addView(liveProgress, match(dp(24)));
            page.addView(goal, cardParams());
        }

        LinearLayout routeCard = card();
        routeCard.addView(text("이동 경로", 15, TEXT, true));
        liveRoute = new WalkingMapView(this);
        LinearLayout.LayoutParams rp = match(dp(210)); rp.topMargin = dp(8); routeCard.addView(liveRoute, rp);
        page.addView(routeCard, cardParams());

        LinearLayout controls = new LinearLayout(this); controls.setOrientation(LinearLayout.HORIZONTAL);
        boolean paused = runtime.getBoolean(WalkingRecorderService.KEY_PAUSED, false);
        Button pause = ghostButton(paused ? "▶ 계속" : "Ⅱ 일시정지", v -> togglePause());
        Button stop = actionButton("■ 종료", false, v -> stopExercise());
        controls.addView(pause, new LinearLayout.LayoutParams(0, dp(54), 1f));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(54), 1f); sp.leftMargin = dp(10); controls.addView(stop, sp);
        page.addView(controls, cardParams());

        updateLive();
    }

    private void updateLive() {
        if (liveDistance == null) return;
        String type = runtime.getString(WalkingRecorderService.KEY_ACTIVITY_TYPE, "walking");
        boolean cycling = "cycling".equals(type);
        boolean walkrun = "walkrun".equals(type);
        long distance = runtime.getLong(WalkingRecorderService.KEY_DISTANCE_M, 0);
        long elapsed = runtime.getLong(WalkingRecorderService.KEY_ELAPSED_MS, 0);
        long moving = runtime.getLong(WalkingRecorderService.KEY_MOVING_MS, 0);
        long steps = runtime.getLong(WalkingRecorderService.KEY_STEPS, 0);
        boolean stepAvailable = runtime.getBoolean(WalkingRecorderService.KEY_STEP_AVAILABLE, false);
        float speed = runtime.getFloat(WalkingRecorderService.KEY_CURRENT_SPEED_KMH, 0);
        float maxSpeed = runtime.getFloat(WalkingRecorderService.KEY_MAX_SPEED_KMH, 0);
        float altitude = runtime.getFloat(WalkingRecorderService.KEY_ALTITUDE_M, Float.NaN);
        float accuracy = runtime.getFloat(WalkingRecorderService.KEY_ACCURACY_M, Float.NaN);

        liveDistance.setText(String.format(Locale.KOREAN, "%.2f km", distance / 1000.0));
        liveTime.setText(formatClock(elapsed));
        liveMoving.setText(formatClock(moving));
        livePace.setText(cycling ? formatAverageSpeed(moving, distance) : formatPace(moving, distance));
        liveSteps.setText(cycling ? String.format(Locale.KOREAN, "%.1f km/h", maxSpeed)
                : (stepAvailable ? String.format(Locale.KOREAN, "%,d", steps) : "미지원"));
        liveSpeed.setText(String.format(Locale.KOREAN, "%.1f km/h", speed));
        liveAltitude.setText(Float.isNaN(altitude) ? "-- m" : String.format(Locale.KOREAN, "%.0f m", altitude));
        liveAccuracy.setText(Float.isNaN(accuracy) ? "GPS 정확도 확인 중" : String.format(Locale.KOREAN, "GPS 정확도 ±%.0fm", accuracy));

        if (walkrun && liveModeBreakdown != null) {
            long wd = runtime.getLong(WalkingRecorderService.KEY_WALKING_DISTANCE_M, 0);
            long rd = runtime.getLong(WalkingRecorderService.KEY_RUNNING_DISTANCE_M, 0);
            long wt = runtime.getLong(WalkingRecorderService.KEY_WALKING_MOVING_MS, 0);
            long rt = runtime.getLong(WalkingRecorderService.KEY_RUNNING_MOVING_MS, 0);
            String mode = "running".equals(runtime.getString(WalkingRecorderService.KEY_AUTO_MOTION_MODE, "walking")) ? "🏃 러닝" : "🚶 걷기";
            liveModeBreakdown.setText(String.format(Locale.KOREAN, "%s · 걷기 %.2fkm %s · 러닝 %.2fkm %s", mode, wd / 1000.0, formatClock(wt), rd / 1000.0, formatClock(rt)));
        }

        if (liveGoal != null && liveProgress != null) {
            long gd = runtime.getLong(WalkingRecorderService.KEY_GOAL_DISTANCE_M, 0);
            long gt = runtime.getLong(WalkingRecorderService.KEY_GOAL_TIME_MS, 0);
            String state = runtime.getString(WalkingRecorderService.KEY_GOAL_STATE, "ACTIVE");
            StringBuilder b = new StringBuilder();
            if (gd > 0) b.append(String.format(Locale.KOREAN, "거리 %.2f / %.2f km", distance / 1000.0, gd / 1000.0));
            if (gt > 0) {
                if (b.length() > 0) b.append(" · ");
                long remain = Math.max(0, gt - elapsed);
                b.append("남은 시간 ").append(formatClock(remain));
            }
            if ("SUCCESS".equals(state)) b.append(" · ✓ 목표 달성");
            else if ("TIMEOUT".equals(state)) b.append(" · 목표 시간 종료");
            liveGoal.setText(b.toString());
            int progress = gd > 0 ? (int) Math.min(100, distance * 100 / Math.max(1, gd)) : (int) Math.min(100, elapsed * 100 / Math.max(1, gt));
            liveProgress.setProgress(progress);
        }

        long now = System.currentTimeMillis();
        if (liveRoute != null && now - lastRouteReload > 4000) {
            String path = runtime.getString(WalkingRecorderService.KEY_SESSION_DIR, "");
            if (!path.isEmpty()) liveRoute.setPoints(WalkingStore.readRoute(new File(path), 1000));
            lastRouteReload = now;
        }
    }

    private void buildRecent(LinearLayout page) {
        List<File> sessions = WalkingStore.listSessions(this);
        TextView h = text("활동 기록", 17, TEXT, true); h.setPadding(0, dp(12), 0, dp(7)); page.addView(h);

        LinearLayout filters1 = new LinearLayout(this);
        filters1.setOrientation(LinearLayout.HORIZONTAL);
        filters1.addView(filterChip("all", "전체"), new LinearLayout.LayoutParams(0, dp(40), 1f));
        LinearLayout.LayoutParams f12 = new LinearLayout.LayoutParams(0, dp(40), 1f); f12.leftMargin = dp(6);
        filters1.addView(filterChip("walking", "걷기"), f12);
        LinearLayout.LayoutParams f13 = new LinearLayout.LayoutParams(0, dp(40), 1f); f13.leftMargin = dp(6);
        filters1.addView(filterChip("running", "러닝"), f13);
        page.addView(filters1);

        LinearLayout filters2 = new LinearLayout(this);
        filters2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams filters2Params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        filters2Params.topMargin = dp(6);
        filters2Params.bottomMargin = dp(2);
        filters2.addView(filterChip("walkrun", "통합"), new LinearLayout.LayoutParams(0, dp(40), 1f));
        LinearLayout.LayoutParams f22 = new LinearLayout.LayoutParams(0, dp(40), 1f); f22.leftMargin = dp(6);
        filters2.addView(filterChip("cycling", "자전거"), f22);
        page.addView(filters2, filters2Params);

        if (sessions.isEmpty()) {
            TextView empty = text("아직 저장된 활동 기록이 없어요.", 12, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(18), 0, dp(10));
            page.addView(empty);
            return;
        }

        String lastDay = "";
        int shown = 0;
        for (File dir : sessions) {
            if (shown >= 20) break;
            JSONObject m = WalkingStore.readMeta(dir);
            if (!"complete".equals(m.optString("status"))) continue;
            long start = m.optLong("startEpochMs", 0);
            if (start <= 0) continue;
            String type = m.optString("type", "walking");
            if (!matchesActivityFilter(type)) continue;

            String dayKey = new SimpleDateFormat("yyyyMMdd", Locale.KOREAN).format(new Date(start));
            if (!dayKey.equals(lastDay)) {
                TextView group = text(dayLabel(start), 13, MUTED, true);
                group.setPadding(0, dp(10), 0, dp(6));
                page.addView(group);
                lastDay = dayKey;
            }

            String label = activityLabel(type);
            String icon = activityIcon(type);
            boolean cycling = "cycling".equals(type);
            boolean walkrun = "walkrun".equals(type);
            LinearLayout c = card(); c.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout left = new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL);
            long dist = m.optLong("distanceM", 0); long moving = m.optLong("movingMs", 0);
            String time = new SimpleDateFormat("HH:mm", Locale.KOREAN).format(new Date(start));
            left.addView(text(icon + " " + label + " · " + time, 14, TEXT, true));
            String line;
            if (cycling) {
                line = String.format(Locale.KOREAN, "%.2f km · %s · 평균 %s · 최고 %.1f km/h", dist / 1000.0, formatClock(m.optLong("durationMs", 0)), formatAverageSpeed(moving, dist), m.optDouble("maxSpeedKmh", 0));
            } else if (walkrun) {
                line = String.format(Locale.KOREAN, "%.2f km · 걷기 %.2f km · 러닝 %.2f km · %,d걸음", dist / 1000.0, m.optLong("walkingDistanceM", 0) / 1000.0, m.optLong("runningDistanceM", 0) / 1000.0, m.optLong("steps", 0));
            } else {
                line = String.format(Locale.KOREAN, "%.2f km · %s · %s · %,d걸음", dist / 1000.0, formatClock(m.optLong("durationMs", 0)), formatPace(moving, dist), m.optLong("steps", 0));
            }
            left.addView(text(line, 12, MUTED, false));
            c.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView arrow = text("›", 28, PRIMARY2, false); arrow.setGravity(Gravity.CENTER); c.addView(arrow, new LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.MATCH_PARENT));
            c.setOnClickListener(v -> showDetail(dir)); page.addView(c, cardParamsCompact());
            shown++;
        }

        if (shown == 0) {
            TextView empty = text("해당 활동 기록이 없어요.", 12, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(18), 0, dp(10));
            page.addView(empty);
        }
    }

    private TextView filterChip(String key, String label) {
        boolean selected = key.equals(activityFilter);
        TextView chip = text(label, 12, selected ? Color.WHITE : MUTED, true);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(round(selected ? PRIMARY : CARD2, 12, selected ? 0 : 1, 0xFF35445F));
        chip.setOnClickListener(v -> {
            if (!key.equals(activityFilter)) {
                activityFilter = key;
                showHome();
            }
        });
        return chip;
    }

    private boolean matchesActivityFilter(String type) {
        return "all".equals(activityFilter) || activityFilter.equals(type);
    }

    private static long startOfDay(long epochMs) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(epochMs);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static String dayLabel(long epochMs) {
        long day = startOfDay(epochMs);
        long today = startOfDay(System.currentTimeMillis());
        java.util.Calendar yesterday = java.util.Calendar.getInstance();
        yesterday.setTimeInMillis(today);
        yesterday.add(java.util.Calendar.DAY_OF_MONTH, -1);
        if (day == today) return "오늘";
        if (day == yesterday.getTimeInMillis()) return "어제";
        return new SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(new Date(epochMs));
    }

    private void showDetail(File dir) { showActivitySummary(dir, false); }
    private void showResult(File dir) { showActivitySummary(dir, true); }

    private void showActivitySummary(File dir, boolean justFinished) {
        detailOpen = true; detailDir = dir; content.removeAllViews();
        ScrollView scroll = new ScrollView(this); LinearLayout page = page(); scroll.addView(page); content.addView(scroll);
        JSONObject m = WalkingStore.readMeta(dir);
        String type = m.optString("type", "walking");
        String label = activityLabel(type);
        String icon = activityIcon(type);
        boolean cycling = "cycling".equals(type);
        boolean walkrun = "walkrun".equals(type);

        LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 34, TEXT, false); back.setGravity(Gravity.CENTER); back.setOnClickListener(v -> { detailOpen = false; showHome(); }); top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(50)));
        top.addView(text(justFinished ? icon + " " + label + " 완료" : icon + " " + label + " 기록", 22, TEXT, true), new LinearLayout.LayoutParams(0, dp(50), 1f)); page.addView(top);

        if (justFinished) {
            LinearLayout completed = card();
            completed.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView check = text("✓", 34, SUCCESS, true); check.setGravity(Gravity.CENTER); completed.addView(check, match(dp(48)));
            TextView done = text(label + " 기록이 저장되었습니다.", 17, TEXT, true); done.setGravity(Gravity.CENTER); completed.addView(done);
            String goalState = m.optString("goalState", "ACTIVE");
            if ("SUCCESS".equals(goalState)) {
                TextView goalDone = text("설정한 목표도 달성했어요.", 12, SUCCESS, true); goalDone.setGravity(Gravity.CENTER); goalDone.setPadding(0, dp(5), 0, 0); completed.addView(goalDone);
            } else if ("TIMEOUT".equals(goalState)) {
                TextView goalEnd = text("설정한 목표 시간은 종료되었습니다.", 12, WARNING, true); goalEnd.setGravity(Gravity.CENTER); goalEnd.setPadding(0, dp(5), 0, 0); completed.addView(goalEnd);
            }
            page.addView(completed, cardParams());
        }

        long start = m.optLong("startEpochMs", 0); long dist = m.optLong("distanceM", 0); long duration = m.optLong("durationMs", 0); long moving = m.optLong("movingMs", 0);
        TextView date = text(new SimpleDateFormat("yyyy년 M월 d일 (E) HH:mm", Locale.KOREAN).format(new Date(start)), 12, MUTED, false); date.setPadding(0, 0, 0, dp(10)); page.addView(date);

        LinearLayout summary = card(); summary.addView(text(String.format(Locale.KOREAN, "%.2f km", dist / 1000.0), 36, TEXT, true));
        summary.addView(kv("전체 시간", formatClock(duration)));
        summary.addView(kv("이동 시간", formatClock(moving)));
        if (cycling) {
            summary.addView(kv("평균 속도", formatAverageSpeed(moving, dist)));
            summary.addView(kv("최고 속도", String.format(Locale.KOREAN, "%.1f km/h", m.optDouble("maxSpeedKmh", 0))));
        } else {
            summary.addView(kv("평균 페이스", formatPace(moving, dist)));
            summary.addView(kv("걸음수", String.format(Locale.KOREAN, "%,d", m.optLong("steps", 0))));
            summary.addView(kv("최고 속도", String.format(Locale.KOREAN, "%.1f km/h", m.optDouble("maxSpeedKmh", 0))));
        }
        page.addView(summary, cardParams());

        if (!cycling) {
            Button editType = ghostButton("운동 종류 변경", v -> showActivityTypeEditor(dir, justFinished));
            editType.setTextColor(PRIMARY2);
            LinearLayout.LayoutParams editParams = match(dp(52));
            editParams.topMargin = dp(2);
            editParams.bottomMargin = dp(10);
            page.addView(editType, editParams);
        }

        if (walkrun) {
            long wd = m.optLong("walkingDistanceM", 0);
            long rd = m.optLong("runningDistanceM", 0);
            long wt = m.optLong("walkingMovingMs", 0);
            long rt = m.optLong("runningMovingMs", 0);
            LinearLayout modes = card();
            modes.addView(text("걷기 / 러닝 자동 구분", 15, TEXT, true));
            modes.addView(kv("🚶 걷기", String.format(Locale.KOREAN, "%.2f km · %s · %s", wd / 1000.0, formatClock(wt), formatPace(wt, wd))));
            modes.addView(kv("🏃 러닝", String.format(Locale.KOREAN, "%.2f km · %s · %s", rd / 1000.0, formatClock(rt), formatPace(rt, rd))));
            modes.addView(kv("자동 전환", m.optInt("autoModeSwitches", 0) + "회"));
            TextView note = text("속도 변화가 일정 시간 유지될 때만 걷기↔러닝을 전환합니다.", 11, MUTED, false);
            note.setPadding(0, dp(7), 0, 0);
            modes.addView(note);
            page.addView(modes, cardParams());
        }

        LinearLayout routeCard = card(); routeCard.addView(text("이동 경로", 15, TEXT, true)); WalkingMapView rv = new WalkingMapView(this); rv.setPoints(WalkingStore.readRoute(dir, 1200)); LinearLayout.LayoutParams rp = match(dp(230)); rp.topMargin = dp(8); routeCard.addView(rv, rp); page.addView(routeCard, cardParams());

        JSONArray splits = m.optJSONArray("splitsMs");
        if (splits != null && splits.length() > 0) {
            LinearLayout splitCard = card(); splitCard.addView(text("1km 구간", 15, TEXT, true));
            for (int i = 0; i < splits.length(); i++) splitCard.addView(kv((i + 1) + " km", formatClock(splits.optLong(i, 0))));
            page.addView(splitCard, cardParams());
        }

        Button delete = ghostButton("기록 삭제", v -> confirmDeleteActivity(dir));
        delete.setTextColor(WARNING);
        LinearLayout.LayoutParams deleteParams = match(dp(52));
        deleteParams.topMargin = dp(2);
        deleteParams.bottomMargin = dp(8);
        page.addView(delete, deleteParams);

        if (justFinished) {
            Button done = actionButton("완료", true, v -> { detailOpen = false; detailDir = null; showHome(); });
            LinearLayout.LayoutParams dpv = match(dp(56));
            dpv.topMargin = dp(2);
            dpv.bottomMargin = dp(10);
            page.addView(done, dpv);
        }
    }

    private void showActivityTypeEditor(File dir, boolean justFinished) {
        if (dir == null || !dir.exists() || !isOwnedSessionDir(dir)) {
            Toast.makeText(this, "수정할 수 없는 기록입니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        JSONObject meta = WalkingStore.readMeta(dir);
        if (!"complete".equals(meta.optString("status"))) {
            Toast.makeText(this, "완료된 기록만 수정할 수 있습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        String currentType = meta.optString("type", "walking");
        if ("cycling".equals(currentType)) {
            Toast.makeText(this, "자전거 기록은 운동 종류를 변경하지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean hasWalkRunBreakdown = meta.has("walkingDistanceM") || meta.has("runningDistanceM")
                || "walkrun".equals(meta.optString("originalType", ""));
        final String[] types = hasWalkRunBreakdown
                ? new String[]{"walking", "running", "walkrun"}
                : new String[]{"walking", "running"};
        final String[] labels = hasWalkRunBreakdown
                ? new String[]{"🚶 걷기", "🏃 러닝", "🚶🏃 통합모드"}
                : new String[]{"🚶 걷기", "🏃 러닝"};

        int selected = 0;
        for (int i = 0; i < types.length; i++) {
            if (types[i].equals(currentType)) { selected = i; break; }
        }
        final int[] chosen = {selected};

        new AlertDialog.Builder(this)
                .setTitle("운동 종류 변경")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> chosen[0] = which)
                .setNegativeButton("취소", null)
                .setPositiveButton("변경", (dialog, which) -> updateActivityType(dir, types[chosen[0]], justFinished))
                .show();
    }

    private void updateActivityType(File dir, String newType, boolean justFinished) {
        if (dir == null || !dir.exists() || !isOwnedSessionDir(dir)) {
            Toast.makeText(this, "수정할 수 없는 기록입니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!"walking".equals(newType) && !"running".equals(newType) && !"walkrun".equals(newType)) {
            Toast.makeText(this, "지원하지 않는 운동 종류입니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject meta = WalkingStore.readMeta(dir);
        if (!"complete".equals(meta.optString("status"))) {
            Toast.makeText(this, "완료된 기록만 수정할 수 있습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        String oldType = meta.optString("type", "walking");
        if ("cycling".equals(oldType)) {
            Toast.makeText(this, "자전거 기록은 운동 종류를 변경하지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean canUseWalkRun = meta.has("walkingDistanceM") || meta.has("runningDistanceM")
                || "walkrun".equals(meta.optString("originalType", "")) || "walkrun".equals(oldType);
        if ("walkrun".equals(newType) && !canUseWalkRun) {
            Toast.makeText(this, "통합모드 구간 정보가 없는 기록입니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (newType.equals(oldType)) {
            Toast.makeText(this, "이미 " + activityLabel(newType) + " 기록입니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (!meta.has("originalType")) meta.put("originalType", oldType);
            meta.put("type", newType);
            meta.put("typeEditedAtEpochMs", System.currentTimeMillis());
            WalkingStore.writeMeta(dir, meta);
            String savedType = WalkingStore.readMeta(dir).optString("type", "");
            if (!newType.equals(savedType)) {
                Toast.makeText(this, "운동 종류 변경에 실패했습니다.", Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, activityLabel(newType) + " 기록으로 변경했습니다.", Toast.LENGTH_SHORT).show();
            showActivitySummary(dir, justFinished);
        } catch (Exception e) {
            Toast.makeText(this, "운동 종류 변경에 실패했습니다.", Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDeleteActivity(File dir) {
        if (dir == null || !dir.exists()) {
            Toast.makeText(this, "이미 삭제된 기록입니다.", Toast.LENGTH_SHORT).show();
            showHome();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("활동 기록 삭제")
                .setMessage("이 기록을 삭제할까요? 저장된 GPS 경로와 운동 기록도 함께 삭제되며 되돌릴 수 없습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> {
                    if (!isOwnedSessionDir(dir)) {
                        Toast.makeText(this, "삭제할 수 없는 기록입니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    boolean deleted = deleteRecursively(dir);
                    if (deleted || !dir.exists()) {
                        detailOpen = false;
                        detailDir = null;
                        Toast.makeText(this, "활동 기록을 삭제했습니다.", Toast.LENGTH_SHORT).show();
                        showHome();
                    } else {
                        Toast.makeText(this, "기록 삭제에 실패했습니다.", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private boolean isOwnedSessionDir(File dir) {
        try {
            String rootPath = WalkingStore.root(this).getCanonicalPath() + File.separator;
            String dirPath = dir.getCanonicalPath();
            return dirPath.startsWith(rootPath) && !dirPath.equals(WalkingStore.root(this).getCanonicalPath());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) return true;
        boolean ok = true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) ok = deleteRecursively(child) && ok;
            }
        }
        return file.delete() && ok;
    }

    private void startExercise(String type, String kmText, String minText) {
        String label = activityLabel(type);
        boolean cycling = "cycling".equals(type);
        boolean noLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED;
        boolean noActivity = !cycling && Build.VERSION.SDK_INT >= 29
                && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED;
        if (noLocation || noActivity) {
            if (Build.VERSION.SDK_INT >= 29 && !cycling) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACTIVITY_RECOGNITION}, REQ_ACTIVITY);
            } else {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_ACTIVITY);
            }
            Toast.makeText(this, cycling ? "위치 권한을 허용한 뒤 자전거 시작 버튼을 다시 눌러주세요."
                    : "위치와 활동 권한을 허용한 뒤 " + label + " 시작 버튼을 다시 눌러주세요.", Toast.LENGTH_LONG).show();
            return;
        }
        double km = parseDouble(kmText); long minutes = Math.round(parseDouble(minText));
        Intent i = new Intent(this, WalkingRecorderService.class).setAction(WalkingRecorderService.ACTION_START)
                .putExtra("activity_type", type)
                .putExtra("goal_distance_m", Math.max(0, Math.round(km * 1000.0)))
                .putExtra("goal_time_ms", Math.max(0, minutes * 60_000L));
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            Toast.makeText(this, label + " 기록을 시작합니다. 화면을 꺼도 GPS 기록은 계속됩니다.", Toast.LENGTH_LONG).show();
            handler.postDelayed(this::showHome, 500);
        } catch (Exception e) { Toast.makeText(this, label + " 기록 시작 실패: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void togglePause() {
        boolean paused = runtime.getBoolean(WalkingRecorderService.KEY_PAUSED, false);
        startService(new Intent(this, WalkingRecorderService.class).setAction(paused ? WalkingRecorderService.ACTION_RESUME : WalkingRecorderService.ACTION_PAUSE));
        handler.postDelayed(this::showHome, 250);
    }

    private void stopExercise() {
        String type = runtime.getString(WalkingRecorderService.KEY_ACTIVITY_TYPE, "walking");
        String path = runtime.getString(WalkingRecorderService.KEY_SESSION_DIR, "");
        File completedDir = path.isEmpty() ? null : new File(path);
        startService(new Intent(this, WalkingRecorderService.class).setAction(WalkingRecorderService.ACTION_STOP));
        Toast.makeText(this, activityLabel(type) + " 기록을 저장합니다.", Toast.LENGTH_SHORT).show();
        waitForExerciseStop(completedDir, 0);
    }

    private void waitForExerciseStop(File completedDir, int attempt) {
        handler.postDelayed(() -> {
            boolean recording = runtime.getBoolean(WalkingRecorderService.KEY_RECORDING, false);
            boolean complete = completedDir != null && completedDir.exists()
                    && "complete".equals(WalkingStore.readMeta(completedDir).optString("status"));
            if (!recording && complete) {
                showResult(completedDir);
                return;
            }
            if (attempt >= 20) {
                if (complete) showResult(completedDir); else showHome();
                return;
            }
            waitForExerciseStop(completedDir, attempt + 1);
        }, 200L);
    }

    private static String activityLabel(String type) {
        if ("cycling".equals(type)) return "자전거";
        if ("walkrun".equals(type)) return "통합모드";
        return "running".equals(type) ? "러닝" : "걷기";
    }

    private static String activityIcon(String type) {
        if ("cycling".equals(type)) return "🚴";
        if ("walkrun".equals(type)) return "🚶🏃";
        return "running".equals(type) ? "🏃" : "🚶";
    }

    private EditText numberField(String hint) {
        EditText e = new EditText(this); e.setHint(hint); e.setTextColor(TEXT); e.setHintTextColor(MUTED); e.setTextSize(13); e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); e.setPadding(dp(11), 0, dp(11), 0); e.setBackground(round(CARD2, 12, 1, 0xFF35445F)); return e;
    }

    private TextView metricValue(String value) { TextView v = text(value, 16, TEXT, true); v.setGravity(Gravity.CENTER); return v; }
    private View metricBox(String label, TextView value) { LinearLayout b = new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL); b.setGravity(Gravity.CENTER); b.addView(value); TextView l = text(label, 10, MUTED, false); l.setGravity(Gravity.CENTER); b.addView(l); return b; }
    private View kv(String key, String value) { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setPadding(0, dp(9), 0, 0); TextView k = text(key, 12, MUTED, false); TextView v = text(value, 13, TEXT, true); v.setGravity(Gravity.RIGHT); r.addView(k, new LinearLayout.LayoutParams(0, dp(27), 1f)); r.addView(v, new LinearLayout.LayoutParams(0, dp(27), 1f)); return r; }

    private LinearLayout page() { LinearLayout p = new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL); p.setPadding(dp(18), dp(18), dp(18), dp(30)); p.setBackgroundColor(BG); return p; }
    private LinearLayout card() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(16), dp(15), dp(16), dp(15)); v.setBackground(round(CARD, 18, 0, 0)); return v; }
    private TextView navItem(String label, int color, View.OnClickListener click) { TextView v = text(label, 12, color, true); v.setGravity(Gravity.CENTER); if (color == PRIMARY2) v.setBackground(round(CARD2, 19, 0, 0)); v.setOnClickListener(click); return v; }
    private TextView text(String s, int sp, int color, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); v.setLineSpacing(0, 1.08f); return v; }
    private Button actionButton(String s, boolean primary, View.OnClickListener click) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setTextColor(Color.WHITE); b.setBackground(round(primary ? PRIMARY : 0xFF33425B, 16, 0, 0)); b.setOnClickListener(click); return b; }
    private Button ghostButton(String s, View.OnClickListener click) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(13); b.setTextColor(TEXT); b.setBackground(round(CARD2, 13, 1, 0xFF35445F)); b.setOnClickListener(click); return b; }
    private GradientDrawable round(int color, int radiusDp, int strokeDp, int strokeColor) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radiusDp)); if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor); return g; }
    private LinearLayout.LayoutParams cardParams() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.bottomMargin = dp(12); return p; }
    private LinearLayout.LayoutParams cardParamsCompact() { LinearLayout.LayoutParams p = cardParams(); p.bottomMargin = dp(8); return p; }
    private LinearLayout.LayoutParams match(int h) { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h); }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private static double parseDouble(String s) { try { return s == null || s.trim().isEmpty() ? 0 : Double.parseDouble(s.trim()); } catch (Exception e) { return 0; } }

    private static String formatClock(long ms) {
        long s = Math.max(0, ms / 1000), h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.KOREAN, "%d:%02d:%02d", h, m, sec) : String.format(Locale.KOREAN, "%02d:%02d", m, sec);
    }

    private static String formatPace(long movingMs, long distanceM) {
        if (distanceM < 100 || movingMs <= 0) return "--'--\"/km";
        double secPerKm = (movingMs / 1000.0) / (distanceM / 1000.0);
        if (secPerKm > 60 * 60) return "--'--\"/km";
        int min = (int) (secPerKm / 60); int sec = (int) Math.round(secPerKm - min * 60);
        if (sec >= 60) { min++; sec = 0; }
        return String.format(Locale.KOREAN, "%d'%02d\"/km", min, sec);
    }

    private static String formatAverageSpeed(long movingMs, long distanceM) {
        if (movingMs <= 0 || distanceM < 20) return "0.0 km/h";
        double hours = movingMs / 3_600_000.0;
        if (hours <= 0) return "0.0 km/h";
        return String.format(Locale.KOREAN, "%.1f km/h", (distanceM / 1000.0) / hours);
    }
}
