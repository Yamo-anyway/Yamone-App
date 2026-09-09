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

/** Simple local-first hiking recorder screen. */
public final class HikingActivity extends Activity {
    private static final int REQ_LOCATION = 5601;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences runtime;
    private LinearLayout page;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            render();
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
        page.addView(header());
        if (runtime.getBoolean(HikingRecorderService.KEY_RECORDING, false)) buildLive();
        else buildReady();
        buildPrivacy();
    }

    private View header() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 34, textColor(), false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        row.addView(back, new LinearLayout.LayoutParams(dp(42), dp(54)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text("등산", 23, textColor(), true));
        words.addView(text("산길도 야모네와 가볍게 기록해요", 12, muted(), false));
        row.addView(words, new LinearLayout.LayoutParams(0, dp(58), 1f));
        return row;
    }

    private void buildReady() {
        LinearLayout hero = card();
        TextView icon = text("🥾  ⛰️", 38, primary2(), false);
        icon.setGravity(Gravity.CENTER);
        hero.addView(icon);
        TextView title = text("등산 기록 준비 완료", 19, textColor(), true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(5));
        hero.addView(title);
        TextView desc = text("거리 · 시간 · 현재 고도 · 최고/최저 고도 · 누적 상승고도와 GPS 이동 경로를 휴대폰에 저장합니다.", 12, muted(), false);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(dp(4), 0, dp(4), dp(16));
        hero.addView(desc);
        Button start = primaryButton("▶  등산 기록 시작");
        start.setOnClickListener(v -> requestStart());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        sp.bottomMargin = dp(4);
        hero.addView(start, sp);
        page.addView(hero, cardParams());

        List<File> sessions = HikingStore.listSessions(this);
        if (!sessions.isEmpty()) {
            TextView recent = text("최근 등산 기록", 16, textColor(), true);
            recent.setPadding(0, dp(10), 0, dp(8));
            page.addView(recent);
            for (int i = 0; i < Math.min(4, sessions.size()); i++) page.addView(recordRow(sessions.get(i)), cardParams());
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
        TextView status = text("● 등산 기록 중", 16, primary2(), true);
        live.addView(status);
        TextView distanceView = text(String.format(Locale.KOREAN, "%.2f km", distance / 1000.0), 38, textColor(), true);
        distanceView.setGravity(Gravity.CENTER);
        distanceView.setPadding(0, dp(10), 0, dp(8));
        live.addView(distanceView);

        LinearLayout row1 = metricRow(
                "활동시간", format(duration),
                "누적 상승", ascent + " m");
        live.addView(row1);
        LinearLayout row2 = metricRow(
                "현재 고도", floatText(altitude, "m"),
                "최고 고도", floatText(maxAlt, "m"));
        LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2p.topMargin = dp(8);
        live.addView(row2, r2p);
        LinearLayout row3 = metricRow(
                "최저 고도", floatText(minAlt, "m"),
                "GPS 정확도", floatText(accuracy, "m"));
        LinearLayout.LayoutParams r3p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r3p.topMargin = dp(8);
        live.addView(row3, r3p);

        Button stop = dangerButton("■  등산 기록 종료");
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
        TextView icon = text("⛰️", 24, primary2(), false);
        icon.setGravity(Gravity.CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(10), 0, 0, 0);
        long start = meta.optLong("startEpochMs", 0L);
        words.addView(text(new SimpleDateFormat("M월 d일 HH:mm", Locale.KOREAN).format(new Date(start)), 13, textColor(), true));
        words.addView(text(String.format(Locale.KOREAN, "%.2f km · 상승 %dm · %s",
                meta.optLong("distanceM", 0L) / 1000.0,
                meta.optLong("ascentM", 0L), format(meta.optLong("durationMs", 0L))), 11, muted(), false));
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private void buildPrivacy() {
        LinearLayout privacy = card();
        privacy.addView(text("🔒 등산 경로는 내 휴대폰에", 14, textColor(), true));
        TextView desc = text("등산 GPS 경로와 기록은 자동으로 서버에 업로드하지 않습니다.", 11, muted(), false);
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
            Toast.makeText(this, "등산 기록을 시작합니다.", Toast.LENGTH_SHORT).show();
            handler.postDelayed(this::render, 400L);
        } catch (Exception e) {
            Toast.makeText(this, "등산 기록을 시작하지 못했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        startService(new Intent(this, HikingRecorderService.class).setAction(HikingRecorderService.ACTION_STOP));
        Toast.makeText(this, "등산 기록을 저장합니다.", Toast.LENGTH_SHORT).show();
        handler.postDelayed(this::render, 500L);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            startRecording();
        }
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
        TextView v = text(value, 16, textColor(), true);
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
        return "pink".equals(getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "mint"));
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

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
