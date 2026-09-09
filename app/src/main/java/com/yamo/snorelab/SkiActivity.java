package com.yamo.snorelab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

import org.json.JSONObject;

import java.io.File;
import java.util.Locale;

/** Local-first ski/snowboard home. GPS lift detection is attached in later steps. */
public class SkiActivity extends Activity {
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
    private static final String KEY_ACTIVE_PATH = "active_path";
    private static final String KEY_ACTIVE_START = "active_start";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (isRecording()) render();
            handler.postDelayed(this, 1000L);
        }
    };

    private LinearLayout page;
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildRoot();
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 1000L);
        render();
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
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            scroll.setOnApplyWindowInsetsListener((v, insets) -> {
                int bottom = android.os.Build.VERSION.SDK_INT >= 30
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
        GradientDrawable heroBg = rounded(CARD2, 22, 0, 0);
        heroImage.setBackground(heroBg);
        heroImage.setClipToOutline(true);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150));
        hp.bottomMargin = dp(12);
        page.addView(heroImage, hp);

        buildSportCard();
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
        TextView desc = text("기록을 시작하기 전에 종목을 선택하세요.", 11, MUTED, false);
        desc.setPadding(0, dp(4), 0, dp(10));
        c.addView(desc);

        String sport = selectedSport();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button ski = choiceButton("⛷  스키", "ski".equals(sport), v -> selectSport("ski"));
        Button snowboard = choiceButton("🏂  스노보드", "snowboard".equals(sport), v -> selectSport("snowboard"));
        row.addView(ski, new LinearLayout.LayoutParams(0, dp(52), 1f));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1f);
        p.leftMargin = dp(8);
        row.addView(snowboard, p);
        c.addView(row);
        page.addView(c, cardParams());
    }

    private void buildRecordingCard() {
        boolean recording = isRecording();
        LinearLayout c = card();
        c.addView(text(recording ? "● 기록 중" : "스키 준비 완료", 16, recording ? SUCCESS : TEXT, true));

        if (recording) {
            long start = prefs.getLong(KEY_ACTIVE_START, System.currentTimeMillis());
            long elapsed = Math.max(0, System.currentTimeMillis() - start);
            TextView time = text(formatElapsed(elapsed), 38, TEXT, true);
            time.setGravity(Gravity.CENTER);
            time.setPadding(0, dp(8), 0, dp(8));
            c.addView(time);
            c.addView(text("스키장 이용 세션을 휴대폰에 기록하고 있습니다.", 12, MUTED, false));
            Button stop = actionButton("■ 기록 종료", false, v -> confirmStop());
            LinearLayout.LayoutParams sp = match(dp(54)); sp.topMargin = dp(12);
            c.addView(stop, sp);
        } else {
            TextView desc = text("스키장 도착 후 시작하면 하루의 스키/스노보드 기록을 하나의 세션으로 보관합니다.", 12, MUTED, false);
            desc.setPadding(0, dp(7), 0, dp(12));
            c.addView(desc);
            Button start = actionButton("▶ " + ("snowboard".equals(selectedSport()) ? "스노보드" : "스키") + " 기록 시작", true, v -> startSession());
            c.addView(start, match(dp(56)));
        }
        page.addView(c, cardParams());
    }

    private void buildLiftCard() {
        int total = SkiLiftStore.countAllObservations(this);
        int pending = SkiLiftStore.countPendingHistorical(this);
        boolean realtime = SkiLiftStore.isRealtimeExchangeEnabled(this);

        LinearLayout c = card();
        c.addView(text("🚡 리프트 정보", 16, TEXT, true));
        TextView summary = text(String.format(Locale.KOREAN, "내 기록 %,d건 · 미제공 %,d건", total, pending), 12, MUTED, false);
        summary.setPadding(0, dp(5), 0, dp(10));
        c.addView(summary);

        LinearLayout realtimeRow = new LinearLayout(this);
        realtimeRow.setOrientation(LinearLayout.HORIZONTAL);
        realtimeRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text("실시간 리프트 정보", 14, TEXT, true));
        words.addView(text(realtime ? "최근 대기정보 제공·수신 ON" : "현재 OFF · 개인 기록만 저장", 11, realtime ? SUCCESS : MUTED, false));
        realtimeRow.addView(words, new LinearLayout.LayoutParams(0, dp(52), 1f));
        Switch toggle = new Switch(this);
        toggle.setChecked(realtime);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SkiLiftStore.setRealtimeExchangeEnabled(this, isChecked);
            render();
        });
        realtimeRow.addView(toggle, new LinearLayout.LayoutParams(dp(58), dp(52)));
        c.addView(realtimeRow);

        TextView realtimeGuide = text("ON이면 현재 스키장의 최근 리프트 대기정보를 서로 제공하고 받아 예상 대기시간을 확인합니다.", 11, MUTED, false);
        realtimeGuide.setPadding(0, dp(2), 0, dp(10));
        c.addView(realtimeGuide);

        Button waits = actionButton("현재 예상 대기시간 보기", realtime, v -> showWaitTimes());
        c.addView(waits, match(dp(52)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button provide = ghostButton("리프트 정보 제공", v -> showProvideInfo(pending));
        Button update = ghostButton("정보 업데이트", v -> showUpdateInfo());
        row.addView(provide, new LinearLayout.LayoutParams(0, dp(50), 1f));
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(0, dp(50), 1f); up.leftMargin = dp(8);
        row.addView(update, up);
        LinearLayout.LayoutParams rp = match(dp(50)); rp.topMargin = dp(8);
        c.addView(row, rp);

        TextView note = text("과거 통계 제공과 실시간 공유는 별개입니다. 과거 제공은 아직 제공하지 않은 리프트 기록만 대상으로 합니다.", 10, MUTED, false);
        note.setPadding(0, dp(9), 0, 0);
        c.addView(note);
        page.addView(c, cardParams());
    }

    private void buildPrivacyCard() {
        LinearLayout c = card();
        c.addView(text("🔒 스키 기록 원칙", 14, TEXT, true));
        TextView p = text("스키 전체 경로와 개인 활동 기록은 휴대폰에 보관합니다. 리프트 정보 제공을 선택한 경우에도 리프트 이용·대기 관련 최소 정보만 서버 제공 대상으로 사용합니다.", 11, MUTED, false);
        p.setPadding(0, dp(6), 0, 0);
        c.addView(p);
        page.addView(c, cardParams());
    }

    private void selectSport(String sport) {
        if (isRecording()) {
            Toast.makeText(this, "기록 중에는 종목을 바꿀 수 없어요.", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit().putString(KEY_SPORT, sport).apply();
        render();
    }

    private String selectedSport() {
        String sport = prefs.getString(KEY_SPORT, "ski");
        return "snowboard".equals(sport) ? "snowboard" : "ski";
    }

    private void startSession() {
        if (isRecording()) return;
        long now = System.currentTimeMillis();
        File dir = SkiLiftStore.createSession(this, now, selectedSport());
        if (dir == null || !dir.exists()) {
            Toast.makeText(this, "기록을 시작하지 못했어요.", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit()
                .putString(KEY_ACTIVE_PATH, dir.getAbsolutePath())
                .putLong(KEY_ACTIVE_START, now)
                .apply();
        render();
    }

    private void confirmStop() {
        new AlertDialog.Builder(this)
                .setTitle("스키 기록 종료")
                .setMessage("오늘의 스키장 기록을 종료할까요?")
                .setNegativeButton("계속 기록", null)
                .setPositiveButton("종료", (d, w) -> stopSession())
                .show();
    }

    private void stopSession() {
        File dir = activeDir();
        if (dir != null) SkiLiftStore.completeSession(dir, System.currentTimeMillis(), "", "");
        prefs.edit().remove(KEY_ACTIVE_PATH).remove(KEY_ACTIVE_START).apply();
        Toast.makeText(this, "스키 기록을 저장했어요.", Toast.LENGTH_SHORT).show();
        render();
    }

    private boolean isRecording() {
        File dir = activeDir();
        if (dir == null) return false;
        JSONObject meta = SkiLiftStore.readSessionMeta(dir);
        if (!"recording".equals(meta.optString("status", ""))) {
            prefs.edit().remove(KEY_ACTIVE_PATH).remove(KEY_ACTIVE_START).apply();
            return false;
        }
        return true;
    }

    private File activeDir() {
        String path = prefs == null ? "" : prefs.getString(KEY_ACTIVE_PATH, "");
        if (path == null || path.trim().isEmpty()) return null;
        File dir = new File(path);
        return dir.exists() && dir.isDirectory() ? dir : null;
    }

    private void showProvideInfo(int pending) {
        String message = pending == 0
                ? "아직 제공하지 않은 리프트 기록이 없습니다."
                : String.format(Locale.KOREAN, "미제공 리프트 기록 %,d건이 있습니다.\n\n서버 연결 단계에서는 이 기록들만 골라 제공하고, 성공한 기록만 제공 완료로 표시합니다.", pending);
        new AlertDialog.Builder(this)
                .setTitle("리프트 정보 제공")
                .setMessage(message)
                .setPositiveButton("확인", null)
                .show();
    }

    private void showUpdateInfo() {
        new AlertDialog.Builder(this)
                .setTitle("리프트 정보 업데이트")
                .setMessage("현재 스키장의 리프트 이름·통계만 내려받아 휴대폰에 저장하는 구조가 준비되어 있습니다. 서버 통계 연결은 다음 단계에서 붙입니다.")
                .setPositiveButton("확인", null)
                .show();
    }

    private void showWaitTimes() {
        if (!SkiLiftStore.isRealtimeExchangeEnabled(this)) {
            Toast.makeText(this, "실시간 리프트 정보를 먼저 ON 해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("현재 예상 대기시간")
                .setMessage("아직 수신된 실시간 리프트 정보가 없습니다.\n\n데이터가 들어오면 이 화면에는 리프트 이름과 예상 대기시간을 목록으로 보여주고, 별도의 ‘지도로 보기’에서 리프트 하단 위치에 표시합니다.")
                .setPositiveButton("확인", null)
                .show();
    }

    private Button choiceButton(String label, boolean selected, View.OnClickListener click) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextColor(selected ? Color.WHITE : TEXT);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(selected ? PRIMARY : CARD2, 16, selected ? PRIMARY : 0xFF31415B, selected ? 0 : 1));
        b.setOnClickListener(click);
        return b;
    }

    private Button actionButton(String label, boolean primary, View.OnClickListener click) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(primary ? PRIMARY : 0xFFB64D64, 18, 0, 0));
        b.setOnClickListener(click);
        b.setEnabled(primary || !label.startsWith("현재 예상") || SkiLiftStore.isRealtimeExchangeEnabled(this));
        b.setAlpha(b.isEnabled() ? 1f : 0.45f);
        return b;
    }

    private Button ghostButton(String label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextColor(TEXT);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(CARD2, 16, 0xFF31415B, 1));
        b.setOnClickListener(click);
        return b;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(15), dp(16), dp(15));
        c.setBackground(rounded(CARD, 20, 0xFF243552, 1));
        return c;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(12);
        return p;
    }

    private LinearLayout.LayoutParams match(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable rounded(int fill, float radiusDp, int stroke, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), stroke);
        return d;
    }

    private String formatElapsed(long ms) {
        long totalSec = Math.max(0, ms / 1000L);
        long h = totalSec / 3600L;
        long m = (totalSec % 3600L) / 60L;
        long s = totalSec % 60L;
        return h > 0 ? String.format(Locale.KOREAN, "%02d:%02d:%02d", h, m, s)
                : String.format(Locale.KOREAN, "%02d:%02d", m, s);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
