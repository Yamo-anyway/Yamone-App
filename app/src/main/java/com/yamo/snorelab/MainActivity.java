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
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_MIC = 4101;
    private static final int REQ_EXPORT = 4102;
    private static final String KEY_THEME = "yamone_theme";
    private int BG = 0xFFF7FFFB;
    private int CARD = 0xFFFFFFFF;
    private int CARD2 = 0xFFF0FAF6;
    private int TEXT = 0xFF153633;
    private int MUTED = 0xFF718984;
    private int PRIMARY = 0xFF56D1B3;
    private int PRIMARY2 = 0xFF159A7A;
    private int DANGER = 0xFFE75B6D;
    private int WARNING = 0xFFE9A642;
    private int SUCCESS = 0xFF159A7A;

    private FrameLayout content;
    private TextView alarmNav;
    private TextView sleepNav;
    private TextView homeNav;
    private TextView activityNav;
    private TextView miniGameNav;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String screen = "sleep";
    private File detailSession;
    private boolean pendingStartAfterPermission;
    private File pendingExportSession;
    private boolean pendingExportClips;
    private boolean pendingExportFull;
    private MediaPlayer player;
    private String activeClipPath;
    private Button activePlaybackToggle;
    private SeekBar activePlaybackSeek;
    private TextView activePlaybackTime;
    private boolean activePlaybackSeeking;
    private SharedPreferences prefs;
    private Boolean lastSleepRecordingUiState;

    private final Runnable playbackTicker = new Runnable() {
        @Override public void run() {
            if (player == null || activePlaybackSeek == null || activePlaybackTime == null) return;
            try {
                int duration = Math.max(1, player.getDuration());
                int position = Math.max(0, player.getCurrentPosition());
                if (!activePlaybackSeeking) {
                    activePlaybackSeek.setMax(duration);
                    activePlaybackSeek.setProgress(Math.min(position, duration));
                }
                activePlaybackTime.setText(formatPlaybackTime(position) + " / " + formatPlaybackTime(duration));
                if (activePlaybackToggle != null) {
                    activePlaybackToggle.setText(player.isPlaying() ? "⏸ 일시정지" : "▶ 재생");
                }
                handler.postDelayed(this, 250);
            } catch (Exception ignored) {}
        }
    };

    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            if ("sleep".equals(screen) && detailSession == null && prefs != null) {
                boolean recording = prefs.getBoolean(SleepRecorderService.KEY_RECORDING, false);
                boolean changed = lastSleepRecordingUiState == null || lastSleepRecordingUiState != recording;
                if (changed || recording) {
                    lastSleepRecordingUiState = recording;
                    showSleep();
                }
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(SleepRecorderService.PREFS, MODE_PRIVATE);
        applyThemeFromPrefs();
        buildRoot();
        int retention = prefs.getInt("retention_days", 30);
        new Thread(() -> SessionStore.cleanupAudioOlderThan(this, retention)).start();
        String startScreen = getIntent().getStringExtra("start_screen");
        if ("settings".equals(startScreen)) showSettings();
        else if ("home".equals(startScreen)) showHome();
        else if ("activity".equals(startScreen)) startActivity(new Intent(this, ExerciseActivity.class));
        else if ("minigame".equals(startScreen)) startActivity(new Intent(this, MiniGameActivity.class));
        else if ("alarm".equals(startScreen)) startActivity(new Intent(this, AlarmActivity.class));
        else showSleep();
        requestNotificationPermissionIfHelpful();
    }

    @Override protected void onResume() {
        super.onResume();
        lastSleepRecordingUiState = null;
        handler.removeCallbacks(refresher);
        if ("sleep".equals(screen) && detailSession == null) showSleep();
        handler.post(refresher);
    }
    @Override protected void onPause() { handler.removeCallbacks(refresher); super.onPause(); }
    @Override protected void onDestroy() { stopPlayer(); super.onDestroy(); }

    @Override public void onBackPressed() {
        if (detailSession != null) { detailSession = null; showSleep(); return; }
        if (!"sleep".equals(screen)) { screen = "sleep"; showSleep(); return; }
        super.onBackPressed();
    }

    private boolean pinkTheme() {
        return "pink".equals(prefs == null ? "mint" : prefs.getString(KEY_THEME, "mint"));
    }

    private void applyThemeFromPrefs() {
        boolean pink = pinkTheme();
        BG = pink ? 0xFFFFF7FA : 0xFFF7FFFB;
        CARD = 0xFFFFFFFF;
        CARD2 = pink ? 0xFFFFEEF3 : 0xFFF0FAF6;
        TEXT = pink ? 0xFF4B2633 : 0xFF153633;
        MUTED = pink ? 0xFF9A7180 : 0xFF718984;
        PRIMARY = pink ? 0xFFFF769F : 0xFF56D1B3;
        PRIMARY2 = pink ? 0xFFE94778 : 0xFF159A7A;
        DANGER = 0xFFE75B6D;
        WARNING = 0xFFE9A642;
        SUCCESS = pink ? 0xFFE94778 : 0xFF159A7A;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private void setThemeAndRefresh(String theme) {
        prefs.edit().putString(KEY_THEME, theme).apply();
        applyThemeFromPrefs();
        buildRoot();
        showSettings();
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
        nav.setBackgroundColor(CARD);

        homeNav = navItem("⌂\n홈", false, v -> { detailSession = null; showHome(); });
        activityNav = navItem("🏃\n활동", false, v -> startActivity(new Intent(this, ExerciseActivity.class)));
        alarmNav = navItem("⏰\n알람", false, v -> startActivity(new Intent(this, AlarmActivity.class)));
        sleepNav = navItem("☾\n수면", true, v -> { detailSession = null; showSleep(); });
        miniGameNav = navItem("🎮\n미니게임", false, v -> startActivity(new Intent(this, MiniGameActivity.class)));

        nav.addView(homeNav, new LinearLayout.LayoutParams(0, dp(60), 1f));
        nav.addView(activityNav, new LinearLayout.LayoutParams(0, dp(60), 1f));
        nav.addView(alarmNav, new LinearLayout.LayoutParams(0, dp(60), 1f));
        nav.addView(sleepNav, new LinearLayout.LayoutParams(0, dp(60), 1f));
        nav.addView(miniGameNav, new LinearLayout.LayoutParams(0, dp(60), 1f));
        root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                int topInset;
                int bottomInset;
                if (Build.VERSION.SDK_INT >= 30) {
                    topInset = insets.getInsets(WindowInsets.Type.statusBars()).top;
                    bottomInset = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                } else {
                    topInset = insets.getSystemWindowInsetTop();
                    bottomInset = insets.getSystemWindowInsetBottom();
                }
                v.setPadding(0, topInset + dp(4), 0, bottomInset);
                return insets;
            });
            root.requestApplyInsets();
        }
        setContentView(root);
    }

    private TextView navItem(String label, boolean selected, View.OnClickListener click) {
        TextView v = text(label, 12, selected ? PRIMARY2 : MUTED, true);
        v.setGravity(Gravity.CENTER);
        v.setOnClickListener(click);
        return v;
    }

    private void styleNav(TextView item, boolean selected) {
        if (item == null) return;
        item.setTextColor(selected ? PRIMARY2 : MUTED);
        item.setBackground(selected ? round(CARD2, 19, 0, 0) : null);
    }

    private void updateNav() {
        styleNav(homeNav, "home".equals(screen));
        styleNav(activityNav, false);
        styleNav(alarmNav, false);
        styleNav(sleepNav, "sleep".equals(screen));
        styleNav(miniGameNav, false);
    }

    private LinearLayout fixedHeader(String title, String subtitle) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(7), dp(18), dp(8));
        header.setBackgroundColor(BG);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(title, 26, TEXT, true));
        TextView sub = text(subtitle, 12, MUTED, false);
        sub.setPadding(0, dp(2), 0, 0);
        words.addView(sub);
        header.addView(words, new LinearLayout.LayoutParams(0, dp(58), 1f));

        TextView gear = text("⚙", 24, TEXT, false);
        gear.setGravity(Gravity.CENTER);
        gear.setBackground(round(CARD2, 18, 0, 0));
        gear.setOnClickListener(v -> showSettings());
        header.addView(gear, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return header;
    }

    private LinearLayout fixedBackHeader(String title, String subtitle, View.OnClickListener backClick) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(7), dp(18), dp(8));
        header.setBackgroundColor(BG);

        TextView back = text("‹", 34, TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(backClick);
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(58)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(title, 22, TEXT, true));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = text(subtitle, 11, MUTED, false);
            sub.setPadding(0, dp(2), 0, 0);
            words.addView(sub);
        }
        header.addView(words, new LinearLayout.LayoutParams(0, dp(58), 1f));
        return header;
    }

    private LinearLayout bodyPage() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(18), dp(6), dp(18), dp(34));
        p.setBackgroundColor(BG);
        return p;
    }

    private void showHome() {
        detailSession = null;
        screen = "home";
        updateNav();
        content.removeAllViews();

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);
        shell.addView(fixedHeader("야모네", "오늘도, 좋은 하루가 쌓여요."), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = bodyPage();
        scroll.addView(page);
        shell.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(shell);

        LinearLayout hero = card();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.yamone_home);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        hero.addView(image, match(dp(210)));
        TextView title = text("편하게 기록하고, 천천히 쌓아가요.", 18, TEXT, true);
        title.setGravity(Gravity.CENTER);
        hero.addView(title);
        TextView desc = text("지금은 알람과 수면 기능을 사용할 수 있어요.", 12, MUTED, false);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, dp(6), 0, dp(14));
        hero.addView(desc);
        hero.addView(actionButton("수면으로 가기", true, v -> showSleep()), match(dp(52)));
        page.addView(hero, cardParams());
    }

    private void showPlaceholderScreen(String target) {
        detailSession = null;
        screen = target;
        updateNav();
        content.removeAllViews();

        String title;
        String subtitle;
        String message;
        int imageRes;
        if ("alarm".equals(target)) {
            title = "알람";
            subtitle = "기분 좋은 시작을 준비해요.";
            message = "알람 기능은 다음 단계에서 연결할게요.";
            imageRes = R.drawable.yamone_alarm;
        } else if ("activity".equals(target)) {
            title = "활동";
            subtitle = "걷고, 뛰고, 달린 하루를 기록해요.";
            message = "걷기·러닝과 자전거 기능을 준비하고 있어요.";
            imageRes = R.drawable.yamone_activity;
        } else {
            title = "스키";
            subtitle = "겨울의 즐거움도 야모네와 함께.";
            message = "스키·스노보드 기능은 나중에 만나요.";
            imageRes = R.drawable.yamone_ski;
        }

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);
        shell.addView(fixedHeader(title, subtitle), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = bodyPage();
        scroll.addView(page);
        shell.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(shell);

        LinearLayout hero = card();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(dp(18), dp(22), dp(18), dp(24));
        ImageView image = new ImageView(this);
        image.setImageResource(imageRes);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        hero.addView(image, match(dp(240)));
        TextView soon = text(message, 15, TEXT, true);
        soon.setGravity(Gravity.CENTER);
        soon.setPadding(0, dp(8), 0, dp(4));
        hero.addView(soon);
        TextView note = text("지금은 화면과 분위기만 먼저 맞춰두었어요.", 11, MUTED, false);
        note.setGravity(Gravity.CENTER);
        hero.addView(note);
        page.addView(hero, cardParams());
    }

    private void showSleep() {
        screen = "sleep";
        updateNav();
        if (detailSession != null) { showSessionDetail(detailSession); return; }
        content.removeAllViews();

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);
        shell.addView(fixedHeader("수면", "잘 자는 것이, 더 좋은 나를 만들어요."), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = bodyPage();
        scroll.addView(page);
        shell.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(shell);

        boolean recording = prefs.getBoolean(SleepRecorderService.KEY_RECORDING, false);
        if (recording) {
            long start = prefs.getLong(SleepRecorderService.KEY_START_MS, System.currentTimeMillis());
            LinearLayout live = new LinearLayout(this);
            live.setOrientation(LinearLayout.VERTICAL);
            live.setGravity(Gravity.CENTER_HORIZONTAL);
            live.setPadding(dp(20), dp(28), dp(20), dp(26));
            int night = pinkTheme() ? 0xFF6D3047 : 0xFF155B55;
            live.setBackground(round(night, 28, 0, 0));
            TextView moon = text("☾", 42, Color.WHITE, false);
            moon.setGravity(Gravity.CENTER);
            live.addView(moon, match(dp(56)));
            TextView state = text("수면 측정 중", 18, Color.WHITE, true);
            state.setGravity(Gravity.CENTER);
            live.addView(state);
            TextView elapsed = text(formatClockDuration(System.currentTimeMillis() - start), 40, Color.WHITE, true);
            elapsed.setGravity(Gravity.CENTER);
            elapsed.setPadding(0, dp(10), 0, dp(6));
            live.addView(elapsed);
            TextView hint = text("조용히, 편안하게 좋은 꿈 꾸세요.\n화면을 꺼도 계속 측정해요.", 13, 0xFFEAF8F4, false);
            hint.setGravity(Gravity.CENTER);
            live.addView(hint);
            Button stop = actionButton("■  측정 종료", true, v -> stopMeasurement());
            LinearLayout.LayoutParams sp = match(dp(56));
            sp.topMargin = dp(24);
            live.addView(stop, sp);
            page.addView(live, cardParams());
            return;
        }

        List<File> sessions = SessionStore.listSessions(this);
        LinearLayout hero = card();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(dp(18), dp(22), dp(18), dp(20));
        TextView mascot = text(pinkTheme() ? "♡  ᵕ̈" : "☁  ᵕ̈", 30, PRIMARY2, true);
        mascot.setGravity(Gravity.CENTER);
        hero.addView(mascot, match(dp(48)));
        TextView hello = text("오늘도\n좋은 잠 되세요.", 23, TEXT, true);
        hello.setGravity(Gravity.CENTER);
        hero.addView(hello);
        if (!sessions.isEmpty()) {
            File last = sessions.get(0);
            JSONObject meta = SessionStore.readMeta(last);
            JSONArray events = SessionStore.readEvents(last);
            long duration = meta.optLong("durationMs", 0);
            TextView lastLabel = text("최근 수면 요약", 12, MUTED, true);
            lastLabel.setPadding(0, dp(20), 0, dp(4));
            lastLabel.setGravity(Gravity.CENTER);
            hero.addView(lastLabel);
            TextView lastTime = text(formatDuration(duration), 28, TEXT, true);
            lastTime.setGravity(Gravity.CENTER);
            hero.addView(lastTime);
            TextView lastSub = text(SessionStore.formatLocalDateTime(meta.optLong("startEpochMs", 0)) + "  ·  코골이 후보 " + events.length() + "건", 11, MUTED, false);
            lastSub.setGravity(Gravity.CENTER);
            lastSub.setPadding(0, dp(4), 0, 0);
            hero.addView(lastSub);
            hero.setOnClickListener(v -> { detailSession = last; showSessionDetail(last); });
        } else {
            TextView first = text("첫 수면 기록을 시작해보세요.", 12, MUTED, false);
            first.setPadding(0, dp(14), 0, 0);
            first.setGravity(Gravity.CENTER);
            hero.addView(first);
        }
        page.addView(hero, cardParams());

        Button startButton = actionButton("☾  수면 측정 시작", true, v -> ensureMicAndStart());
        page.addView(startButton, match(dp(60)));

        if (!sessions.isEmpty()) {
            TextView h = text("최근 기록", 17, TEXT, true);
            h.setPadding(0, dp(24), 0, dp(10));
            page.addView(h);
            for (int i = 0; i < Math.min(6, sessions.size()); i++) page.addView(sessionRow(sessions.get(i)), cardParamsCompact());
        }

        LinearLayout privacy = card();
        privacy.addView(text("🔒  수면 기록은 내 휴대폰에", 14, TEXT, true));
        TextView p = text("녹음과 분석 기록은 앱 내부에 저장하며 자동 업로드하지 않습니다.", 11, MUTED, false);
        p.setPadding(0, dp(6), 0, 0);
        privacy.addView(p);
        LinearLayout.LayoutParams pp = cardParams();
        pp.topMargin = dp(12);
        page.addView(privacy, pp);
    }

    private View sessionRow(File dir) {
        JSONObject meta = SessionStore.readMeta(dir); JSONArray events = SessionStore.readEvents(dir);
        LinearLayout c = card(); c.setOrientation(LinearLayout.HORIZONTAL); c.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = text("☾", 22, PRIMARY2, true); icon.setGravity(Gravity.CENTER); icon.setBackground(round(CARD2, 20, 0, 0));
        c.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout left = new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL); left.setPadding(dp(12), 0, 0, 0);
        left.addView(text(SessionStore.formatLocalDateTime(meta.optLong("startEpochMs", 0)), 13, TEXT, true));
        left.addView(text(formatDuration(meta.optLong("durationMs", 0)) + "  ·  후보 " + events.length() + "건", 11, MUTED, false));
        c.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = text("›", 26, PRIMARY2, false); arrow.setGravity(Gravity.CENTER); c.addView(arrow, new LinearLayout.LayoutParams(dp(32), dp(44)));
        c.setOnClickListener(v -> { detailSession = dir; showSessionDetail(dir); }); return c;
    }

    private void showSessionDetail(File dir) {
        screen = "sleep"; updateNav(); content.removeAllViews(); stopPlayer();
        JSONObject meta = SessionStore.readMeta(dir); JSONArray events = SessionStore.readEvents(dir);
        long start = meta.optLong("startEpochMs", 0); long duration = meta.optLong("durationMs", Math.max(1, System.currentTimeMillis() - start));
        long candidateMs = 0; for (int i = 0; i < events.length(); i++) { JSONObject e = events.optJSONObject(i); if (e != null) candidateMs += e.optLong("durationMs", 0); }

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);
        String detailDate = new SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(new Date(start));
        shell.addView(fixedBackHeader("수면 결과", detailDate, v -> { detailSession = null; showSleep(); }),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = bodyPage();
        scroll.addView(page);
        shell.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(shell);

        LinearLayout summary = card(); summary.setGravity(Gravity.CENTER_HORIZONTAL); summary.setPadding(dp(18), dp(20), dp(18), dp(20));
        TextView ring = text("◔", 34, PRIMARY, true); ring.setGravity(Gravity.CENTER); summary.addView(ring, match(dp(48)));
        TextView total = text(formatDuration(duration), 31, TEXT, true); total.setGravity(Gravity.CENTER); summary.addView(total);
        TextView status = text("측정된 수면 기록", 12, MUTED, false); status.setGravity(Gravity.CENTER); summary.addView(status);
        LinearLayout metrics = new LinearLayout(this); metrics.setOrientation(LinearLayout.HORIZONTAL); metrics.setPadding(0, dp(16), 0, 0);
        metrics.addView(metric("코골이 후보", events.length() + "건"), new LinearLayout.LayoutParams(0, dp(66), 1f));
        metrics.addView(metric("후보 시간", formatDuration(candidateMs)), new LinearLayout.LayoutParams(0, dp(66), 1f));
        metrics.addView(metric("확정", meta.optInt("snoreConfirmedCount", 0) + "건"), new LinearLayout.LayoutParams(0, dp(66), 1f));
        summary.addView(metrics); page.addView(summary, cardParams());

        LinearLayout timelineCard = card(); timelineCard.addView(text("코골이 타임라인", 15, TEXT, true));
        TextView span = text(new SimpleDateFormat("HH:mm", Locale.KOREAN).format(new Date(start)) + "  →  " + new SimpleDateFormat("HH:mm", Locale.KOREAN).format(new Date(start + duration)), 11, MUTED, false); span.setPadding(0, dp(4), 0, dp(6)); timelineCard.addView(span);
        SnoreTimelineView timeline = new SnoreTimelineView(this); timeline.setData(events, Math.max(1, duration)); timelineCard.addView(timeline, match(dp(86)));
        timelineCard.addView(text("후보 구간을 눌러 듣고 직접 판정할 수 있어요.", 11, MUTED, false)); page.addView(timelineCard, cardParams());

        TextView listHeader = text("코골이 후보 구간", 17, TEXT, true); listHeader.setPadding(0, dp(14), 0, dp(9)); page.addView(listHeader);
        if (events.length() == 0) { LinearLayout empty = card(); empty.addView(text("감지된 후보가 없습니다.", 13, MUTED, false)); page.addView(empty, cardParams()); }
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i); if (e == null) continue; final int idx = i;
            LinearLayout row = card();
            LinearLayout head = new LinearLayout(this); head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL);
            long eventStart = start + e.optLong("startOffsetMs", 0);
            head.addView(text(new SimpleDateFormat("HH:mm:ss", Locale.KOREAN).format(new Date(eventStart)), 14, TEXT, true), new LinearLayout.LayoutParams(0, dp(32), 1f));
            TextView score = text(String.format(Locale.US, "점수 %.0f", e.optDouble("scoreMax", 0)), 11, e.optDouble("scoreMax", 0) >= 72 ? DANGER : WARNING, true); score.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            head.addView(score, new LinearLayout.LayoutParams(dp(84), dp(32))); row.addView(head);
            String label = e.optString("reviewLabel", "UNREVIEWED");
            TextView info = text(formatDuration(e.optLong("durationMs", 0)) + "  ·  " + labelKorean(label), 11, labelColor(label), false); info.setPadding(0, 0, 0, dp(8)); row.addView(info);
            TextView playbackTime = text("00:00 / --:--", 10, MUTED, false); playbackTime.setGravity(Gravity.RIGHT); row.addView(playbackTime, match(dp(22)));
            SeekBar playbackSeek = new SeekBar(this); playbackSeek.setMax(1000); playbackSeek.setProgress(0);
            playbackSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { if (fromUser && seekBar == activePlaybackSeek && player != null) { try { int d = Math.max(1, player.getDuration()); player.seekTo(Math.min(progress, d)); } catch (Exception ignored) {} } }
                @Override public void onStartTrackingTouch(SeekBar seekBar) { if (seekBar == activePlaybackSeek) activePlaybackSeeking = true; }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { if (seekBar == activePlaybackSeek) activePlaybackSeeking = false; }
            });
            row.addView(playbackSeek, match(dp(34)));
            LinearLayout buttons = new LinearLayout(this); buttons.setOrientation(LinearLayout.HORIZONTAL); buttons.setPadding(0, 0, 0, dp(9));
            Button restart = ghostButton("↺ 처음", null); Button playToggle = ghostButton("▶ 재생", null);
            restart.setOnClickListener(v -> restartClip(dir, e, playToggle, playbackSeek, playbackTime));
            playToggle.setOnClickListener(v -> toggleClip(dir, e, playToggle, playbackSeek, playbackTime));
            buttons.addView(restart, new LinearLayout.LayoutParams(0, dp(42), 1f));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(42), 1f); bp.leftMargin = dp(7); buttons.addView(playToggle, bp);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, dp(42), 1f); rp.leftMargin = dp(7); buttons.addView(ghostButton("판정", v -> showReviewDialog(dir, idx)), rp);
            row.addView(buttons); page.addView(row, cardParamsCompact());
        }

        LinearLayout export = card(); export.addView(text("기록 관리", 15, TEXT, true));
        TextView exp = text("내보내기는 사용자가 직접 선택할 때만 실행됩니다.", 11, MUTED, false); exp.setPadding(0, dp(5), 0, dp(8)); export.addView(exp);
        export.addView(ghostButton("분석 데이터 내보내기", v -> beginExport(dir, false, false)), match(dp(44)));
        LinearLayout.LayoutParams ep = match(dp(44)); ep.topMargin = dp(7); export.addView(ghostButton("후보 음원 포함 내보내기", v -> beginExport(dir, true, false)), ep);
        LinearLayout.LayoutParams ep2 = match(dp(44)); ep2.topMargin = dp(7); export.addView(ghostButton("전체 녹음까지 포함", v -> beginExport(dir, true, true)), ep2);
        LinearLayout.LayoutParams delp = match(dp(44)); delp.topMargin = dp(10); Button del = ghostButton("이 기록 삭제", v -> confirm("이 기록을 삭제할까요?", "녹음과 분석 기록이 모두 삭제됩니다.", () -> { SessionStore.deleteSession(dir); detailSession = null; showSleep(); })); del.setTextColor(DANGER); export.addView(del, delp);
        page.addView(export, cardParams());
    }

    private void showSettings() {
        screen = "settings"; updateNav(); content.removeAllViews();
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);
        shell.addView(fixedBackHeader("수면 설정", "테마와 마이크 측정을 편하게 조절해요.", v -> showSleep()),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = bodyPage();
        scroll.addView(page);
        shell.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(shell);

        LinearLayout theme = card(); theme.addView(text("테마", 15, TEXT, true));
        LinearLayout themeRow = new LinearLayout(this); themeRow.setOrientation(LinearLayout.HORIZONTAL); themeRow.setPadding(0, dp(10), 0, 0);
        Button mint = choiceButton("🌿  민트", !pinkTheme(), v -> setThemeAndRefresh("mint"));
        Button pink = choiceButton("🌸  핑크", pinkTheme(), v -> setThemeAndRefresh("pink"));
        themeRow.addView(mint, new LinearLayout.LayoutParams(0, dp(54), 1f)); LinearLayout.LayoutParams tpp = new LinearLayout.LayoutParams(0, dp(54), 1f); tpp.leftMargin = dp(10); themeRow.addView(pink, tpp); theme.addView(themeRow); page.addView(theme, cardParams());

        LinearLayout measure = card();
        measure.addView(text("마이크 설정", 15, TEXT, true));
        TextView sensitivityHelp = text("민감도가 낮으면 큰·뚜렷한 소리만 후보로 잡고, 높이면 작은 소리까지 더 많이 잡습니다. 너무 높으면 코골이가 아닌 소리도 후보가 늘 수 있어요.", 11, MUTED, false);
        sensitivityHelp.setPadding(0, dp(5), 0, dp(12));
        measure.addView(sensitivityHelp);

        int sensitivity = prefs.getInt("sensitivity", 65);
        TextView sensLabel = text("감지 민감도", 12, TEXT, true);
        TextView sensValue = text(String.valueOf(sensitivity), 20, PRIMARY2, true);
        TextView sensGuide = text(sensitivityGuide(sensitivity), 11, MUTED, false);
        measure.addView(sensLabel);
        sensValue.setGravity(Gravity.CENTER);
        sensValue.setPadding(0, dp(4), 0, 0);
        measure.addView(sensValue);
        sensGuide.setGravity(Gravity.CENTER);
        sensGuide.setPadding(0, dp(2), 0, dp(8));
        measure.addView(sensGuide);

        SeekBar seek = new SeekBar(this);
        seek.setMax(100);
        seek.setProgress(sensitivity);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                sensValue.setText(String.valueOf(progress));
                sensGuide.setText(sensitivityGuide(progress));
                if (fromUser) prefs.edit().putInt("sensitivity", progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) { prefs.edit().putInt("sensitivity", bar.getProgress()).apply(); }
        });
        measure.addView(seek, match(dp(44)));

        LinearLayout fine = new LinearLayout(this);
        fine.setOrientation(LinearLayout.HORIZONTAL);
        fine.setGravity(Gravity.CENTER);
        Button minus = choiceButton("− 1", false, null);
        Button plus = choiceButton("+ 1", false, null);
        TextView fineHint = text("1단위 미세 조절", 11, MUTED, true);
        fineHint.setGravity(Gravity.CENTER);
        minus.setOnClickListener(v -> {
            int next = Math.max(0, seek.getProgress() - 1);
            seek.setProgress(next);
            prefs.edit().putInt("sensitivity", next).apply();
        });
        plus.setOnClickListener(v -> {
            int next = Math.min(100, seek.getProgress() + 1);
            seek.setProgress(next);
            prefs.edit().putInt("sensitivity", next).apply();
        });
        fine.addView(minus, new LinearLayout.LayoutParams(0, dp(42), 1f));
        fine.addView(fineHint, new LinearLayout.LayoutParams(0, dp(42), 1.3f));
        fine.addView(plus, new LinearLayout.LayoutParams(0, dp(42), 1f));
        measure.addView(fine);

        measure.addView(settingSwitch("전체 녹음", "개발자 검증용 AAC 전체 녹음 저장", "developer_full_recording", true));
        measure.addView(settingSwitch("코골이 후보 음원 저장", "후보 앞 3초를 포함한 WAV 구간 저장", "save_candidate_clips", true));
        page.addView(measure, cardParams());

        LinearLayout storage = card(); storage.addView(text("녹음 보관 기간", 15, TEXT, true));
        int[] vals = {7, 30, 90, 0}; String[] labs = {"7일", "30일", "90일", "계속"}; int current = prefs.getInt("retention_days", 30);
        LinearLayout chips = new LinearLayout(this); chips.setOrientation(LinearLayout.HORIZONTAL); chips.setPadding(0, dp(10), 0, dp(8));
        for (int i = 0; i < vals.length; i++) { final int val = vals[i]; Button b = choiceButton(labs[i], current == val, v -> { prefs.edit().putInt("retention_days", val).apply(); showSettings(); }); LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(44), 1f); if (i > 0) cp.leftMargin = dp(6); chips.addView(b, cp); }
        storage.addView(chips);
        storage.addView(text("현재 앱 데이터  " + humanBytes(SessionStore.folderSize(SessionStore.sessionsRoot(this))), 11, MUTED, false));
        LinearLayout.LayoutParams wp = match(dp(44)); wp.topMargin = dp(10); Button wipe = ghostButton("전체 수면 데이터 삭제", v -> confirm("전체 데이터를 삭제할까요?", "모든 수면 녹음과 분석 기록이 삭제됩니다.", () -> { SessionStore.deleteAll(this); showSettings(); })); wipe.setTextColor(DANGER); storage.addView(wipe, wp); page.addView(storage, cardParams());

        LinearLayout privacy = card(); privacy.addView(text("개인정보 보호", 15, TEXT, true));
        privacy.addView(checkLine("수면 기록과 녹음은 앱 내부 저장소에 저장됩니다."));
        privacy.addView(checkLine("수면 기록을 자체 서버로 자동 업로드하지 않습니다."));
        privacy.addView(checkLine("지도 등 다른 기능의 인터넷 통신과 수면 데이터는 분리합니다."));
        page.addView(privacy, cardParams());

        LinearLayout dev = card(); dev.addView(text("개발자 검증", 15, TEXT, true)); dev.addView(kv("판정 엔진", SnoreDetector.VERSION)); dev.addView(kv("분석", "16 kHz / mono")); dev.addView(kv("전체 녹음", "AAC-LC 32 kbps")); dev.addView(kv("후보 음원", "PCM16 WAV")); page.addView(dev, cardParams());
    }

    private String sensitivityGuide(int value) {
        if (value <= 34) return "낮음 · 큰 소리 위주로 엄격하게 감지";
        if (value <= 69) return "보통 · 일반적인 소리를 균형 있게 감지";
        return "높음 · 작은 소리까지 감지 · 오탐이 늘 수 있음";
    }

    private View settingSwitch(String title, String desc, String key, boolean def) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(0, dp(10), 0, dp(4));
        LinearLayout texts = new LinearLayout(this); texts.setOrientation(LinearLayout.VERTICAL); texts.addView(text(title, 13, TEXT, true)); texts.addView(text(desc, 11, MUTED, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); Switch sw = new Switch(this); sw.setChecked(prefs.getBoolean(key, def));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.edit().putBoolean(key, isChecked).apply()); row.addView(sw, new LinearLayout.LayoutParams(dp(58), dp(50))); return row;
    }

    private void ensureMicAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingStartAfterPermission = true; requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC); return;
        }
        startMeasurement();
    }
    private void startMeasurement() {
        if (prefs.getBoolean(SleepRecorderService.KEY_RECORDING, false)) return;
        try {
            startForegroundService(new Intent(this, SleepRecorderService.class).setAction(SleepRecorderService.ACTION_START));
            Toast.makeText(this, "수면 측정을 시작합니다. 충전 연결을 권장합니다.", Toast.LENGTH_LONG).show(); handler.postDelayed(this::showSleep, 500);
        } catch (Exception e) { Toast.makeText(this, "측정을 시작하지 못했습니다: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }
    private void stopMeasurement() {
        startService(new Intent(this, SleepRecorderService.class).setAction(SleepRecorderService.ACTION_STOP));
        Toast.makeText(this, "수면 측정을 종료하고 기록을 정리합니다.", Toast.LENGTH_SHORT).show();
        waitForSleepStop(0);
    }

    private void waitForSleepStop(int attempt) {
        handler.postDelayed(() -> {
            boolean recording = prefs.getBoolean(SleepRecorderService.KEY_RECORDING, false);
            if (!recording || attempt >= 20) { showSleep(); return; }
            waitForSleepStop(attempt + 1);
        }, 250);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && pendingStartAfterPermission) startMeasurement();
            else if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) Toast.makeText(this, "수면 소리를 측정하려면 마이크 권한이 필요합니다.", Toast.LENGTH_LONG).show();
            pendingStartAfterPermission = false;
        }
    }
    private void requestNotificationPermissionIfHelpful() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4103);
    }

    private void showReviewDialog(File dir, int index) {
        String[] labels = {"코골이", "코골이 아님", "애매함", "미검토로 되돌리기"}; String[] values = {"SNORE", "NOT_SNORE", "UNCERTAIN", "UNREVIEWED"};
        new AlertDialog.Builder(this).setTitle("이 구간을 어떻게 판정할까요?").setItems(labels, (d, which) -> {
            try { SessionStore.reviewEvent(dir, index, values[which]); showSessionDetail(dir); }
            catch (Exception e) { Toast.makeText(this, "판정 저장 실패", Toast.LENGTH_SHORT).show(); }
        }).show();
    }

    private File resolveClip(File dir, JSONObject event) {
        String rel = event.optString("clipFile", "");
        if (rel.isEmpty() || "null".equals(rel)) {
            Toast.makeText(this, "이 후보에는 저장된 음원이 없습니다.", Toast.LENGTH_SHORT).show();
            return null;
        }
        File clip = new File(dir, rel);
        if (!clip.exists()) {
            Toast.makeText(this, "녹음 파일이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
            return null;
        }
        return clip;
    }

    private void toggleClip(File dir, JSONObject event, Button toggle, SeekBar seek, TextView time) {
        File clip = resolveClip(dir, event);
        if (clip == null) return;
        String path = clip.getAbsolutePath();
        if (player != null && path.equals(activeClipPath)) {
            try {
                if (player.isPlaying()) player.pause();
                else {
                    if (player.getCurrentPosition() >= Math.max(0, player.getDuration() - 100)) player.seekTo(0);
                    player.start();
                }
                updatePlaybackNow();
            } catch (Exception e) {
                stopPlayer();
                Toast.makeText(this, "재생 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            return;
        }
        startClip(clip, toggle, seek, time, false);
    }

    private void restartClip(File dir, JSONObject event, Button toggle, SeekBar seek, TextView time) {
        File clip = resolveClip(dir, event);
        if (clip == null) return;
        String path = clip.getAbsolutePath();
        if (player != null && path.equals(activeClipPath)) {
            try {
                player.seekTo(0);
                player.start();
                updatePlaybackNow();
            } catch (Exception e) {
                stopPlayer();
                Toast.makeText(this, "재생 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            return;
        }
        startClip(clip, toggle, seek, time, true);
    }

    private void startClip(File clip, Button toggle, SeekBar seek, TextView time, boolean fromBeginning) {
        stopPlayer();
        try {
            player = new MediaPlayer();
            player.setDataSource(clip.getAbsolutePath());
            player.prepare();
            activeClipPath = clip.getAbsolutePath();
            activePlaybackToggle = toggle;
            activePlaybackSeek = seek;
            activePlaybackTime = time;
            activePlaybackSeeking = false;
            seek.setMax(Math.max(1, player.getDuration()));
            seek.setProgress(0);
            time.setText("00:00 / " + formatPlaybackTime(player.getDuration()));
            player.setOnCompletionListener(mp -> {
                try { mp.seekTo(0); } catch (Exception ignored) {}
                if (activePlaybackToggle != null) activePlaybackToggle.setText("▶ 재생");
                if (activePlaybackSeek != null) activePlaybackSeek.setProgress(0);
                if (activePlaybackTime != null) activePlaybackTime.setText("00:00 / " + formatPlaybackTime(mp.getDuration()));
            });
            if (fromBeginning) player.seekTo(0);
            player.start();
            handler.removeCallbacks(playbackTicker);
            handler.post(playbackTicker);
        } catch (Exception e) {
            stopPlayer();
            Toast.makeText(this, "재생 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePlaybackNow() {
        handler.removeCallbacks(playbackTicker);
        handler.post(playbackTicker);
    }

    private void stopPlayer() {
        handler.removeCallbacks(playbackTicker);
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
        if (activePlaybackToggle != null) activePlaybackToggle.setText("▶ 재생");
        if (activePlaybackSeek != null) activePlaybackSeek.setProgress(0);
        if (activePlaybackTime != null) activePlaybackTime.setText("00:00 / --:--");
        activeClipPath = null;
        activePlaybackToggle = null;
        activePlaybackSeek = null;
        activePlaybackTime = null;
        activePlaybackSeeking = false;
    }

    private void beginExport(File dir, boolean includeClips, boolean includeFull) {
        pendingExportSession = dir; pendingExportClips = includeClips; pendingExportFull = includeFull;
        String suffix = includeFull ? "_full" : includeClips ? "_candidates" : "_analysis";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("application/zip"); intent.putExtra(Intent.EXTRA_TITLE, dir.getName() + suffix + ".zip"); startActivityForResult(intent, REQ_EXPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_EXPORT || resultCode != RESULT_OK || data == null || data.getData() == null || pendingExportSession == null) return;
        Uri uri = data.getData(); File dir = pendingExportSession; boolean clips = pendingExportClips, full = pendingExportFull; pendingExportSession = null;
        Toast.makeText(this, "내보내기 파일을 생성합니다.", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File zip = SessionStore.createExportZip(this, dir, clips, full);
                try (FileInputStream in = new FileInputStream(zip); OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IllegalStateException("저장 위치를 열 수 없습니다."); byte[] buffer = new byte[64 * 1024]; int n; while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                }
                runOnUiThread(() -> Toast.makeText(this, "테스트 데이터 저장 완료", Toast.LENGTH_LONG).show());
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "내보내기 실패: " + e.getMessage(), Toast.LENGTH_LONG).show()); }
        }, "SnoreLabExport").start();
    }

    private LinearLayout page() { LinearLayout p = new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL); p.setPadding(dp(18), dp(22), dp(18), dp(34)); p.setBackgroundColor(BG); return p; }
    private LinearLayout card() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(16), dp(16), dp(16), dp(16)); v.setBackground(round(CARD, 24, 1, pinkTheme() ? 0xFFFFE3EC : 0xFFE0F3EC)); return v; }
    private LinearLayout metric(String label, String value) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(5), dp(8), dp(5), 0);
        TextView val = text(value, 20, TEXT, true); val.setGravity(Gravity.CENTER); TextView lab = text(label, 11, MUTED, false); lab.setGravity(Gravity.CENTER); box.addView(val); box.addView(lab); return box;
    }
    private View kv(String key, String value) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(0, dp(10), 0, 0);
        TextView k = text(key, 12, MUTED, false); TextView v = text(value, 13, TEXT, true); v.setGravity(Gravity.RIGHT);
        row.addView(k, new LinearLayout.LayoutParams(0, dp(28), 1f)); row.addView(v, new LinearLayout.LayoutParams(0, dp(28), 1f)); return row;
    }
    private TextView checkLine(String s) { TextView v = text("✓  " + s, 12, TEXT, false); v.setPadding(0, dp(9), 0, 0); return v; }
    private TextView text(String s, int sp, int color, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); v.setLineSpacing(0, 1.08f); return v; }
    private Button actionButton(String s, boolean primary, View.OnClickListener click) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(15); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setTextColor(Color.WHITE); b.setBackground(round(primary ? PRIMARY2 : PRIMARY, 22, 0, 0)); b.setOnClickListener(click); return b; }
    private Button ghostButton(String s, View.OnClickListener click) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(12); b.setTextColor(TEXT); b.setBackground(round(CARD2, 18, 1, pinkTheme() ? 0xFFFFD7E3 : 0xFFD7EFE7)); if (click != null) b.setOnClickListener(click); return b; }
    private Button choiceButton(String s, boolean selected, View.OnClickListener click) { Button b = new Button(this); b.setText(s + (selected ? "  ✓" : "")); b.setAllCaps(false); b.setTextSize(12); b.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL); b.setTextColor(selected ? Color.WHITE : TEXT); b.setBackground(round(selected ? PRIMARY2 : CARD2, 18, 1, selected ? PRIMARY2 : (pinkTheme() ? 0xFFFFD7E3 : 0xFFD7EFE7))); b.setOnClickListener(click); return b; }
    private GradientDrawable round(int color, int radiusDp, int strokeDp, int strokeColor) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radiusDp)); if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor); return g; }
    private LinearLayout.LayoutParams cardParams() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.bottomMargin = dp(12); return p; }
    private LinearLayout.LayoutParams cardParamsCompact() { LinearLayout.LayoutParams p = cardParams(); p.bottomMargin = dp(8); return p; }
    private LinearLayout.LayoutParams match(int h) { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h); }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void confirm(String title, String message, Runnable yes) { new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("취소", null).setPositiveButton("확인", (d, w) -> yes.run()).show(); }
    private String labelKorean(String label) { switch (label) { case "SNORE": return "코골이"; case "NOT_SNORE": return "코골이 아님"; case "UNCERTAIN": return "애매함"; default: return "미검토"; } }
    private int labelColor(String label) { switch (label) { case "SNORE": return SUCCESS; case "NOT_SNORE": return DANGER; case "UNCERTAIN": return WARNING; default: return MUTED; } }
    private static String formatClockDuration(long ms) {
        long total = Math.max(0, ms / 1000); long h = total / 3600; long m = (total % 3600) / 60; long sec = total % 60;
        return String.format(Locale.KOREAN, "%02d:%02d:%02d", h, m, sec);
    }

    private static String formatPlaybackTime(long ms) {
        long total = Math.max(0, ms / 1000);
        long minutes = total / 60;
        long seconds = total % 60;
        return String.format(Locale.KOREAN, "%02d:%02d", minutes, seconds);
    }

    private static String formatDuration(long ms) {
        long total = Math.max(0, ms / 1000), h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        if (h > 0) return String.format(Locale.KOREAN, "%d시간 %02d분", h, m); if (m > 0) return String.format(Locale.KOREAN, "%d분 %02d초", m, s); return s + "초";
    }
    private static String humanBytes(long b) {
        if (b < 1024) return b + " B"; double kb = b / 1024.0; if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0; if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb); return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }
}
