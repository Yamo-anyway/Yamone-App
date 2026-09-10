package com.yamo.snorelab;

import android.Manifest;
import android.app.Activity;
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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Local-first hiking/trekking recorder and detail screen. */
public final class HikingActivity extends Activity {
    private static final int REQ_LOCATION = 5601;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences runtime;
    private LinearLayout page;
    private File detailSession;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (runtime != null && runtime.getBoolean(HikingRecorderService.KEY_RECORDING, false)) render();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        runtime = getSharedPreferences(HikingRecorderService.PREFS, MODE_PRIVATE);
        buildRoot();
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        ActivitySystemBarUiEnhancer.apply(this);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (detailSession != null) {
            detailSession = null;
            render();
            return;
        }
        super.onBackPressed();
    }

    private void buildRoot() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg());
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(10), dp(18), dp(34));
        page.setBackgroundColor(bg());
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    private void render() {
        if (page == null) return;
        page.removeAllViews();
        if (detailSession != null) {
            buildDetail(detailSession);
        } else {
            page.addView(header("등산 / 트레킹", "산길과 트레일을 야모네와 기록해요", v -> finish()));
            if (runtime.getBoolean(HikingRecorderService.KEY_RECORDING, false)) buildLive();
            else buildReady();
            buildPrivacy();
        }
        ActivitySystemBarUiEnhancer.apply(this);
    }

    private View header(String title, String subtitle, View.OnClickListener backClick) {
    return YamoneBackHeader.create(this, title, subtitle, bg(), textColor(), muted(), backClick);
}

    private void buildReady() {
        LinearLayout hero = card();
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_activity_hike);
        icon.setColorFilter(primary2());
        icon.setPadding(dp(14), dp(14), dp(14), dp(14));
        icon.setBackground(round(card2(), 30, 0, 0));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(60), dp(60));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        hero.addView(icon, iconParams);
        TextView title = text("등산 / 트레킹 기록 준비", 19, textColor(), true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(5));
        hero.addView(title);
        TextView desc = text("거리 · 시간 · 고도 · 누적 상승고도와 GPS 이동 경로를 휴대폰에 저장합니다.", 12, muted(), false);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(dp(4), 0, dp(4), dp(16));
        hero.addView(desc);
        Button start = primaryButton("등산 / 트레킹 기록 시작");
        start.setOnClickListener(v -> requestStart());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        sp.bottomMargin = dp(4);
        hero.addView(start, sp);
        page.addView(hero, cardParams());

        List<File> sessions = HikingStore.listSessions(this);
        if (!sessions.isEmpty()) {
            TextView recent = text("최근 등산 / 트레킹 기록", 16, textColor(), true);
            recent.setPadding(0, dp(10), 0, dp(8));
            page.addView(recent);
            for (int i = 0; i < Math.min(8, sessions.size()); i++) page.addView(recordRow(sessions.get(i)), cardParams());
        }
    }

    private void buildLive() {
        long duration = runtime.getLong(HikingRecorderService.KEY_DURATION_MS, 0L);
        long distance = runtime.getLong(HikingRecorderService.KEY_DISTANCE_M, 0L);
        long ascent = runtime.getLong(HikingRecorderService.KEY_ASCENT_M, 0L);
        float altitude = runtime.getFloat(HikingRecorderService.KEY_ALTITUDE_M, Float.NaN);
        float maxAlt = runtime.getFloat(HikingRecorderService.KEY_MAX_ALTITUDE_M, Float.NaN);
        float minAlt = runtime.getFloat(HikingRecorderService.KEY_MIN_ALTITUDE_M, Float.NaN);
        float accuracy = runtime.getFloat(HikingRecorderService.KEY_ACCURACY_M, Float.NaN);

        LinearLayout live = card();
        live.addView(text("● 등산 / 트레킹 기록 중", 16, primary2(), true));
        TextView distanceView = text(String.format(Locale.KOREAN, "%.2f km", distance / 1000.0), 38, textColor(), true);
        distanceView.setGravity(Gravity.CENTER);
        distanceView.setPadding(0, dp(10), 0, dp(8));
        live.addView(distanceView);
        live.addView(metricRow("활동시간", format(duration), "누적 상승", ascent + " m"));
        LinearLayout row2 = metricRow("현재 고도", floatText(altitude, "m"), "최고 고도", floatText(maxAlt, "m"));
        LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2p.topMargin = dp(8);
        live.addView(row2, r2p);
        LinearLayout row3 = metricRow("최저 고도", floatText(minAlt, "m"), "GPS 정확도", floatText(accuracy, "m"));
        LinearLayout.LayoutParams r3p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r3p.topMargin = dp(8);
        live.addView(row3, r3p);

        Button stop = dangerButton("등산 / 트레킹 기록 종료");
        stop.setOnClickListener(v -> stopRecording());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        bp.topMargin = dp(18);
        bp.bottomMargin = dp(5);
        live.addView(stop, bp);
        page.addView(live, cardParams());
    }

    private View recordRow(File dir) {
        JSONObject meta = HikingStore.readMeta(dir);
        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOnClickListener(v -> {
            detailSession = dir;
            render();
        });
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_activity_hike);
        icon.setColorFilter(primary2());
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(round(card2(), 22, 0, 0));
        row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(10), 0, 0, 0);
        long start = meta.optLong("startEpochMs", 0L);
        words.addView(text(new SimpleDateFormat("M월 d일 HH:mm", Locale.KOREAN).format(new Date(start)), 13, textColor(), true));
        words.addView(text(String.format(Locale.KOREAN, "%.2f km · 상승 %dm · %s",
                meta.optLong("distanceM", 0L) / 1000.0,
                meta.optLong("ascentM", 0L), format(meta.optLong("durationMs", 0L))), 11, muted(), false));
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_yamone_chevron_right);
        arrow.setColorFilter(primary2());
        arrow.setPadding(dp(9), dp(9), dp(9), dp(9));
        arrow.setBackground(round(card2(), 18, 0, 0));
        row.addView(arrow, new LinearLayout.LayoutParams(dp(36), dp(36)));
        return row;
    }

    private void buildDetail(File dir) {
        JSONObject meta = HikingStore.readMeta(dir);
        page.addView(header("등산 / 트레킹 기록", "경로와 고도를 확인해요", v -> {
            detailSession = null;
            render();
        }));
        long start = meta.optLong("startEpochMs", 0L);
        TextView date = text(new SimpleDateFormat("yyyy년 M월 d일 (E) HH:mm", Locale.KOREAN).format(new Date(start)), 12, muted(), false);
        date.setPadding(0, 0, 0, dp(10));
        page.addView(date);

        long distance = meta.optLong("distanceM", 0L);
        long duration = meta.optLong("durationMs", 0L);
        ActivityRouteAnalysis.Result analysis = ActivityRouteAnalysis.analyze(dir, "hiking");

        LinearLayout summary = card();
        summary.addView(text(String.format(Locale.KOREAN, "%.2f km", distance / 1000.0), 36, textColor(), true));
        summary.addView(kv("활동 시간", format(duration)));
        summary.addView(kv("누적 상승", meter(analysis.hasAltitude ? analysis.ascentM : meta.optDouble("ascentM", 0))));
        if (analysis.hasAltitude) {
            summary.addView(kv("누적 하강", meter(analysis.descentM)));
            summary.addView(kv("최고 / 최저 고도", meter(analysis.maxAltitudeM) + " / " + meter(analysis.minAltitudeM)));
        }
        summary.addView(kv("GPS 품질", analysis.gpsQualityPercent + "%"));
        page.addView(summary, cardParams());

        if (!analysis.samples.isEmpty()) {
            LinearLayout route = card();
            route.addView(text("이동 경로", 15, textColor(), true));
            WalkingMapView map = new WalkingMapView(this);
            map.setInteractive(true);
            map.setAnalysisSamples(analysis.samples);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230));
            mp.topMargin = dp(8);
            route.addView(map, mp);
            page.addView(route, cardParams());
        }

        if (analysis.hasAltitude) {
            LinearLayout elevation = card();
            elevation.addView(text("고도", 15, textColor(), true));
            TextView sub = text("전체 기록 · 두 손가락 확대 · 좌우 이동", 11, muted(), false);
            sub.setPadding(0, dp(4), 0, dp(4));
            elevation.addView(sub);
            ActivityProfileChartView chart = new ActivityProfileChartView(this);
            chart.setData(analysis, ActivityProfileChartView.MODE_ALTITUDE);
            elevation.addView(chart, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(185)));
            elevation.addView(metricRow("최고 고도", meter(analysis.maxAltitudeM), "최저 고도", meter(analysis.minAltitudeM)));
            LinearLayout r2 = metricRow("누적 상승", meter(analysis.ascentM), "누적 하강", meter(analysis.descentM));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = dp(8);
            elevation.addView(r2, p);
            page.addView(elevation, cardParams());
        }

        if (analysis.hasSpeed) {
            LinearLayout speed = card();
            speed.addView(text("속도", 15, textColor(), true));
            ActivityProfileChartView chart = new ActivityProfileChartView(this);
            chart.setData(analysis, ActivityProfileChartView.MODE_SPEED);
            speed.addView(chart, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(165)));
            speed.addView(metricRow("평균 기록 속도", String.format(Locale.KOREAN, "%.1f km/h", analysis.averageSpeedKmh),
                    "최고 유효 속도", String.format(Locale.KOREAN, "%.1f km/h", analysis.maxSpeedKmh)));
            page.addView(speed, cardParams());
        }
    }

    private TextView kv(String label, String value) {
        TextView view = text(label + "    " + value, 13, textColor(), false);
        view.setPadding(0, dp(8), 0, 0);
        return view;
    }

    private void buildPrivacy() {
        LinearLayout privacy = card();
        privacy.addView(text("등산 / 트레킹 경로는 내 휴대폰에", 14, textColor(), true));
        TextView desc = text("GPS 경로와 기록은 자동으로 서버에 업로드하지 않습니다.", 11, muted(), false);
        desc.setPadding(0, dp(6), 0, 0);
        privacy.addView(desc);
        page.addView(privacy, cardParams());
    }

    private void requestStart() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            return;
        }
        startRecording();
    }

    private void startRecording() {
        Intent intent = new Intent(this, HikingRecorderService.class).setAction(HikingRecorderService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
            Toast.makeText(this, "등산 / 트레킹 기록을 시작합니다.", Toast.LENGTH_SHORT).show();
            handler.postDelayed(this::render, 400L);
        } catch (Exception e) {
            Toast.makeText(this, "기록을 시작하지 못했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        String path = runtime.getString(HikingRecorderService.KEY_SESSION_DIR, "");
        startService(new Intent(this, HikingRecorderService.class).setAction(HikingRecorderService.ACTION_STOP));
        Toast.makeText(this, "등산 / 트레킹 기록을 저장합니다.", Toast.LENGTH_SHORT).show();
        handler.postDelayed(() -> {
            if (!path.isEmpty()) {
                File saved = new File(path);
                if (saved.isDirectory() && "complete".equals(HikingStore.readMeta(saved).optString("status"))) detailSession = saved;
            }
            render();
        }, 650L);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) startRecording();
    }

    private LinearLayout metricRow(String l1, String v1, String l2, String v2) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(metric(l1, v1), new LinearLayout.LayoutParams(0, dp(66), 1f));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(66), 1f);
        p.leftMargin = dp(8);
        row.addView(metric(l2, v2), p);
        return row;
    }

    private View metric(String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackground(round(card2(), 16, 1, border()));
        TextView v = text(value, 15, textColor(), true);
        v.setGravity(Gravity.CENTER);
        box.addView(v);
        TextView l = text(label, 10, muted(), false);
        l.setGravity(Gravity.CENTER);
        box.addView(l);
        return box;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(16));
        card.setBackground(round(Color.WHITE, 20, 1, border()));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(13);
        return p;
    }

    private Button primaryButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(primary(), 17, 0, 0));
        return b;
    }

    private Button dangerButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(0xFFE75B6D);
        b.setBackground(round(0xFFFFEEF2, 17, 1, 0xFFFFCCD6));
        return b;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setLineSpacing(0, 1.08f);
        return v;
    }

    private GradientDrawable round(int fill, int radiusDp, int strokeDp, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), stroke);
        return d;
    }

    private boolean pink() {
        return "pink".equals(getSharedPreferences(SleepRecorderService.PREFS, 0).getString("yamone_theme", "pink"));
    }
    private int bg() { return pink() ? 0xFFFFF7FA : 0xFFF7FFFB; }
    private int card2() { return pink() ? 0xFFFFEEF3 : 0xFFF0FAF6; }
    private int textColor() { return pink() ? 0xFF4B2633 : 0xFF153633; }
    private int muted() { return pink() ? 0xFF9A7180 : 0xFF718984; }
    private int primary() { return pink() ? 0xFFFF769F : 0xFF56D1B3; }
    private int primary2() { return pink() ? 0xFFE94778 : 0xFF159A7A; }
    private int border() { return pink() ? 0xFFFFD7E3 : 0xFFD7EFE7; }

    private static String format(long ms) {
        long s = Math.max(0, ms / 1000), h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.KOREAN, "%d:%02d:%02d", h, m, sec)
                : String.format(Locale.KOREAN, "%02d:%02d", m, sec);
    }

    private static String floatText(float value, String unit) {
        return Float.isNaN(value) ? "-" : String.format(Locale.KOREAN, "%.0f %s", value, unit);
    }

    private static String meter(double value) {
        return Double.isNaN(value) ? "-" : String.format(Locale.KOREAN, "%.0f m", value);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
