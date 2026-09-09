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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Locale;

/** Local-first ski/snowboard activity screen. */
public class SkiActivity extends Activity {
    private static final int REQ_LOCATION = 5501;
    private static final int BG = 0xFF0B1324;
    private static final int CARD = 0xFF16243B;
    private static final int CARD2 = 0xFF111C31;
    private static final int TEXT = 0xFFF5F7FF;
    private static final int MUTED = 0xFF9DA9BF;
    private static final int PRIMARY = 0xFF6D72FF;
    private static final int PRIMARY2 = 0xFF8B8FFF;
    private static final int SUCCESS = 0xFF61D6A8;
    private static final int WARNING = 0xFFFFC56D;

    private static final String PREFS = "yamone_ski_ui_v1";
    private static final String KEY_SPORT = "sport";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            render();
            handler.postDelayed(this, 1000L);
        }
    };

    private LinearLayout page;
    private SharedPreferences prefs;
    private SharedPreferences runtime;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        runtime = getSharedPreferences(SkiRecorderService.PREFS, MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildRoot();
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private void buildRoot() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(10), dp(16), dp(28));
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (Build.VERSION.SDK_INT >= 21) {
            scroll.setOnApplyWindowInsetsListener((v, insets) -> {
                int bottom = Build.VERSION.SDK_INT >= 30
                        ? insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                        : insets.getSystemWindowInsetBottom();
                v.setPadding(0, 0, 0, bottom);
                return insets;
            });
        }
        setContentView(scroll);
    }

    private void render() {
        if (page == null) return;
        page.removeAllViews();
        page.addView(header());

        ImageView heroImage = new ImageView(this);
        heroImage.setImageResource(R.drawable.yamone_ski);
        heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroImage.setBackground(rounded(CARD2, 22, 0, 0));
        heroImage.setClipToOutline(true);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(138));
        hp.bottomMargin = dp(12);
        page.addView(heroImage, hp);

        if (!isRecording()) buildSportCard();
        buildRecordingCard();
        buildLiftCard();
        buildPrivacyCard();
    }

    private View header() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 34, TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        row.addView(back, new LinearLayout.LayoutParams(dp(42), dp(52)));
        LinearLayout titleWrap = new LinearLayout(this);
        titleWrap.setOrientation(LinearLayout.VERTICAL);
        titleWrap.addView(text("스키 / 스노보드", 22, TEXT, true));
        titleWrap.addView(text("오늘의 설원을 기록해요", 12, MUTED, false));
        row.addView(titleWrap, new LinearLayout.LayoutParams(0, dp(58), 1f));
        return row;
    }

    private void buildSportCard() {
        LinearLayout c = card();
        c.addView(text("종목 선택", 15, TEXT, true));
        TextView desc = text("기록 시작 전에 종목을 선택하세요.", 11, MUTED, false);
        desc.setPadding(0, dp(4), 0, dp(10));
        c.addView(desc);

        String sport = selectedSport();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(choiceButton("⛷  스키", "ski".equals(sport), v -> selectSport("ski")), new LinearLayout.LayoutParams(0, dp(52), 1f));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1f); p.leftMargin = dp(8);
        row.addView(choiceButton("🏂  스노보드", "snowboard".equals(sport), v -> selectSport("snowboard")), p);
        c.addView(row);
        page.addView(c, cardParams());
    }

    private void buildRecordingCard() {
        boolean recording = isRecording();
        LinearLayout c = card();
        if (!recording) {
            c.addView(text("스키 준비 완료", 16, TEXT, true));
            TextView desc = text("시작하면 화면이 꺼져 있어도 GPS 기록과 활주·리프트·정지 판별을 계속합니다.", 12, MUTED, false);
            desc.setPadding(0, dp(7), 0, dp(12));
            c.addView(desc);
            c.addView(actionButton("▶ " + ("snowboard".equals(selectedSport()) ? "스노보드" : "스키") + " 기록 시작", true, v -> startSession()), match(dp(56)));
            page.addView(c, cardParams());
            return;
        }

        String state = runtime.getString(SkiRecorderService.KEY_STATE, SkiRecorderService.STATE_CHECKING);
        String stateLabel = stateLabel(state);
        int stateColor = SkiRecorderService.STATE_DESCENT.equals(state) ? SUCCESS
                : SkiRecorderService.STATE_LIFT.equals(state) ? PRIMARY2
                : SkiRecorderService.STATE_STOPPED.equals(state) ? WARNING : MUTED;
        c.addView(text(stateLabel, 18, stateColor, true));

        TextView speed = text(String.format(Locale.KOREAN, "%.1f km/h", runtime.getFloat(SkiRecorderService.KEY_SPEED_KMH, 0)), 38, TEXT, true);
        speed.setGravity(Gravity.CENTER);
        speed.setPadding(0, dp(5), 0, dp(3));
        c.addView(speed);
        TextView elapsed = text("전체 시간  " + formatElapsed(System.currentTimeMillis() - runtime.getLong(SkiRecorderService.KEY_START_MS, System.currentTimeMillis())), 12, MUTED, true);
        elapsed.setGravity(Gravity.CENTER);
        c.addView(elapsed);

        LinearLayout row1 = new LinearLayout(this); row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(metric("활주", runtime.getInt(SkiRecorderService.KEY_DESCENT_COUNT, 0) + "회"), new LinearLayout.LayoutParams(0, dp(68), 1f));
        row1.addView(metric("리프트", runtime.getInt(SkiRecorderService.KEY_LIFT_COUNT, 0) + "회"), new LinearLayout.LayoutParams(0, dp(68), 1f));
        row1.addView(metric("최고속도", String.format(Locale.KOREAN, "%.1f", runtime.getFloat(SkiRecorderService.KEY_MAX_SPEED_KMH, 0)) + " km/h"), new LinearLayout.LayoutParams(0, dp(68), 1f));
        c.addView(row1);

        LinearLayout row2 = new LinearLayout(this); row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(metric("활주거리", String.format(Locale.KOREAN, "%.2f km", runtime.getLong(SkiRecorderService.KEY_DESCENT_DISTANCE_M, 0) / 1000.0)), new LinearLayout.LayoutParams(0, dp(68), 1f));
        row2.addView(metric("누적 하강", runtime.getLong(SkiRecorderService.KEY_DESCENT_VERTICAL_M, 0) + " m"), new LinearLayout.LayoutParams(0, dp(68), 1f));
        row2.addView(metric("GPS 고도", Math.round(runtime.getFloat(SkiRecorderService.KEY_ALTITUDE_M, 0)) + " m"), new LinearLayout.LayoutParams(0, dp(68), 1f));
        c.addView(row2);

        TextView detector = text("자동 판별: 속도 · 고도 변화 · 지속시간 · 리프트 이동 직선성을 함께 확인합니다.", 10, MUTED, false);
        detector.setPadding(0, dp(7), 0, dp(11));
        c.addView(detector);
        c.addView(actionButton("■ 기록 종료", false, v -> confirmStop()), match(dp(54)));
        page.addView(c, cardParams());
    }

    private void buildLiftCard() {
        int total = SkiLiftStore.countAllObservations(this);
        int pending = SkiLiftStore.countPendingHistorical(this);
        boolean realtime = SkiLiftStore.isRealtimeExchangeEnabled(this);
        long waitMs = runtime.getLong(SkiRecorderService.KEY_WAIT_TIME_MS, 0);
        long liftMs = runtime.getLong(SkiRecorderService.KEY_LIFT_TIME_MS, 0);

        LinearLayout c = card();
        c.addView(text("🚡 리프트 정보", 16, TEXT, true));
        TextView summary = text(String.format(Locale.KOREAN, "내 기록 %,d건 · 미제공 %,d건", total, pending), 12, MUTED, false);
        summary.setPadding(0, dp(5), 0, dp(8));
        c.addView(summary);

        if (isRecording()) {
            LinearLayout today = new LinearLayout(this); today.setOrientation(LinearLayout.HORIZONTAL);
            today.addView(metric("오늘 대기", formatElapsed(waitMs)), new LinearLayout.LayoutParams(0, dp(62), 1f));
            today.addView(metric("리프트 시간", formatElapsed(liftMs)), new LinearLayout.LayoutParams(0, dp(62), 1f));
            c.addView(today);
        }

        LinearLayout realtimeRow = new LinearLayout(this);
        realtimeRow.setOrientation(LinearLayout.HORIZONTAL);
        realtimeRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text("실시간 리프트 정보", 14, TEXT, true));
        words.addView(text(realtime ? "최근 대기정보 제공·수신 ON" : "현재 OFF · 개인 기록만 저장", 11, realtime ? SUCCESS : MUTED, false));
        realtimeRow.addView(words, new LinearLayout.LayoutParams(0, dp(52), 1f));
        Switch toggle = new Switch(this);
        toggle.setChecked(realtime);
        toggle.setOnCheckedChangeListener((buttonView, checked) -> {
            SkiLiftStore.setRealtimeExchangeEnabled(this, checked);
            render();
        });
        realtimeRow.addView(toggle, new LinearLayout.LayoutParams(dp(58), dp(52)));
        c.addView(realtimeRow);

        TextView realtimeGuide = text("ON이면 추후 현재 스키장의 최근 대기정보를 서로 제공·수신합니다. 현재 GPS 기록 자체는 서버로 보내지 않습니다.", 11, MUTED, false);
        realtimeGuide.setPadding(0, dp(2), 0, dp(10));
        c.addView(realtimeGuide);
        c.addView(actionButton("현재 예상 대기시간 보기", realtime, v -> showWaitTimes()), match(dp(52)));

        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(ghostButton("리프트 정보 제공", v -> showProvideInfo(pending)), new LinearLayout.LayoutParams(0, dp(50), 1f));
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(0, dp(50), 1f); up.leftMargin = dp(8);
        row.addView(ghostButton("정보 업데이트", v -> showUpdateInfo()), up);
        LinearLayout.LayoutParams rp = match(dp(50)); rp.topMargin = dp(8); c.addView(row, rp);
        page.addView(c, cardParams());
    }

    private void buildPrivacyCard() {
        LinearLayout c = card();
        c.addView(text("🔒 스키 기록 원칙", 14, TEXT, true));
        TextView p = text("전체 스키 GPS 경로는 휴대폰 내부에만 저장합니다. 리프트 정보 제공을 선택해도 리프트 이용·대기 관련 최소 정보만 별도로 제공하도록 설계합니다.", 11, MUTED, false);
        p.setPadding(0, dp(6), 0, 0);
        c.addView(p);
        page.addView(c, cardParams());
    }

    private void selectSport(String sport) {
        prefs.edit().putString(KEY_SPORT, sport).apply();
        render();
    }

    private String selectedSport() {
        return "snowboard".equals(prefs.getString(KEY_SPORT, "ski")) ? "snowboard" : "ski";
    }

    private void startSession() {
        if (isRecording()) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            Toast.makeText(this, "위치 권한을 허용한 뒤 기록 시작을 다시 눌러주세요.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(this, SkiRecorderService.class).setAction(SkiRecorderService.ACTION_START).putExtra("sport", selectedSport());
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            Toast.makeText(this, "스키 기록을 시작합니다. 화면을 꺼도 GPS 기록은 계속됩니다.", Toast.LENGTH_LONG).show();
            handler.postDelayed(this::render, 500L);
        } catch (Exception e) {
            Toast.makeText(this, "스키 기록 시작 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmStop() {
        new AlertDialog.Builder(this)
                .setTitle("스키 기록 종료")
                .setMessage("오늘의 스키장 기록을 종료할까요? 감지된 리프트 이용 정보도 함께 휴대폰에 저장됩니다.")
                .setNegativeButton("계속 기록", null)
                .setPositiveButton("종료", (d, w) -> stopSession())
                .show();
    }

    private void stopSession() {
        startService(new Intent(this, SkiRecorderService.class).setAction(SkiRecorderService.ACTION_STOP));
        Toast.makeText(this, "스키 기록을 저장합니다.", Toast.LENGTH_SHORT).show();
        handler.postDelayed(this::render, 700L);
    }

    private boolean isRecording() {
        return runtime != null && runtime.getBoolean(SkiRecorderService.KEY_RECORDING, false);
    }

    private void showProvideInfo(int pending) {
        String message = pending == 0 ? "아직 제공하지 않은 리프트 기록이 없습니다."
                : String.format(Locale.KOREAN, "미제공 리프트 기록 %,d건이 있습니다.\n\n서버 연결 단계에서는 이 기록들만 제공하고 성공한 기록만 제공 완료로 표시합니다.", pending);
        new AlertDialog.Builder(this).setTitle("리프트 정보 제공").setMessage(message).setPositiveButton("확인", null).show();
    }

    private void showUpdateInfo() {
        new AlertDialog.Builder(this).setTitle("리프트 정보 업데이트")
                .setMessage("현재 스키장의 리프트 이름·통계만 내려받는 서버 연결은 다음 단계에서 붙입니다.")
                .setPositiveButton("확인", null).show();
    }

    private void showWaitTimes() {
        if (!SkiLiftStore.isRealtimeExchangeEnabled(this)) {
            Toast.makeText(this, "실시간 리프트 정보를 먼저 ON 해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("현재 예상 대기시간")
                .setMessage("아직 수신된 실시간 리프트 정보가 없습니다. 서버 연결 후에는 리프트 이름과 예상 대기시간 목록을 보여줍니다.")
                .setPositiveButton("확인", null).show();
    }

    private String stateLabel(String state) {
        if (SkiRecorderService.STATE_DESCENT.equals(state)) return "⛷ 활주 중";
        if (SkiRecorderService.STATE_LIFT.equals(state)) return "🚡 리프트 이동";
        if (SkiRecorderService.STATE_STOPPED.equals(state)) return "● 정지";
        return "GPS 움직임 판별 중";
    }

    private View metric(String label, String value) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER);
        TextView v = text(value, 15, TEXT, true); v.setGravity(Gravity.CENTER); box.addView(v);
        TextView l = text(label, 10, MUTED, false); l.setGravity(Gravity.CENTER); box.addView(l);
        return box;
    }

    private Button choiceButton(String label, boolean selected, View.OnClickListener click) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(label); b.setTextColor(TEXT); b.setTextSize(13); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(selected ? PRIMARY : CARD2, 14, selected ? 0 : 1, 0xFF35445F)); b.setOnClickListener(click); return b;
    }

    private Button actionButton(String label, boolean primary, View.OnClickListener click) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(label); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(primary ? PRIMARY : 0xFF33425B, 16, 0, 0)); b.setOnClickListener(click); return b;
    }

    private Button ghostButton(String label, View.OnClickListener click) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(label); b.setTextColor(TEXT); b.setTextSize(12);
        b.setBackground(rounded(CARD2, 13, 1, 0xFF35445F)); b.setOnClickListener(click); return b;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(16), dp(15), dp(16), dp(15)); c.setBackground(rounded(CARD, 18, 0, 0)); return c;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); v.setLineSpacing(0, 1.08f); return v;
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radiusDp)); if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor); return g;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.bottomMargin = dp(12); return p;
    }

    private LinearLayout.LayoutParams match(int height) { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height); }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private static String formatElapsed(long ms) {
        long sec = Math.max(0, ms / 1000); long h = sec / 3600; long m = (sec % 3600) / 60; long s = sec % 60;
        return h > 0 ? String.format(Locale.KOREAN, "%d:%02d:%02d", h, m, s) : String.format(Locale.KOREAN, "%02d:%02d", m, s);
    }
}
