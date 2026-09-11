package com.yamo.snorelab;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Renewal 1 – implementation preview shell.
 *
 * This is intentionally built from normal Android Views and locally drawn Canvas icons,
 * so every layout shown here can be carried directly into the production screens.
 */
public class YamoneDesignPreviewActivity extends Activity {
    private YamoneDesignSystem.Palette palette;
    private SharedPreferences prefs;
    private FrameLayout content;
    private LinearLayout root;
    private TextView adBar;

    private final LinearLayout[] navItems = new LinearLayout[5];
    private final YamoneIconView[] navIcons = new YamoneIconView[5];
    private final TextView[] navLabels = new TextView[5];

    private String currentTab = "home";
    private int openMonth = -1;
    private int appInfoTapCount = 0;
    private boolean developerUnlocked = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(SleepRecorderService.PREFS, MODE_PRIVATE);
        developerUnlocked = prefs.getBoolean("design_preview_developer_unlocked", false);
        rebuild("home");
    }

    private void rebuild(String tab) {
        currentTab = tab;
        palette = YamoneDesignSystem.palette(this);
        applySystemBars();
        buildRoot();
        selectTab(tab);
    }

    private void applySystemBars() {
        getWindow().setStatusBarColor(palette.background);
        getWindow().setNavigationBarColor(palette.background);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = getWindow().getDecorView().getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void buildRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        adBar = YamoneDesignSystem.text(this, "AD  ·  배너 광고 영역", 10, palette.muted, false);
        adBar.setGravity(Gravity.CENTER);
        adBar.setBackground(YamoneDesignSystem.roundedBorder(palette.surface, palette.border, 12, 2, this));
        LinearLayout.LayoutParams adParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        adParams.setMargins(dp(16), dp(3), dp(16), dp(5));
        root.addView(adBar, adParams);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(7), dp(5), dp(7), dp(7));
        nav.setBackgroundColor(palette.surface);
        if (Build.VERSION.SDK_INT >= 21) nav.setElevation(dp(8));

        nav.addView(navItem(0, YamoneIconView.NAV_HOME, "홈", "home"), navCellParams());
        nav.addView(navItem(1, YamoneIconView.NAV_ACTIVITY, "활동", "activity"), navCellParams());
        nav.addView(navItem(2, YamoneIconView.NAV_RECORDS, "기록", "records"), navCellParams());
        nav.addView(navItem(3, YamoneIconView.NAV_ALARM, "알람", "alarm"), navCellParams());
        nav.addView(navItem(4, YamoneIconView.NAV_SETTINGS, "설정", "settings"), navCellParams());
        root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                int top;
                int bottom;
                if (Build.VERSION.SDK_INT >= 30) {
                    top = insets.getInsets(WindowInsets.Type.statusBars()).top;
                    bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                v.setPadding(0, top, 0, bottom);
                return insets;
            });
            root.requestApplyInsets();
        }
        setContentView(root);
    }

    private LinearLayout.LayoutParams navCellParams() {
        return new LinearLayout.LayoutParams(0, dp(60), 1f);
    }

    private LinearLayout navItem(int index, int iconType, String label, String tab) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(4), dp(4), dp(4), dp(3));

        YamoneIconView icon = new YamoneIconView(this, iconType);
        icon.setPalette(palette.primaryStrong, palette.muted, false);
        item.addView(icon, new LinearLayout.LayoutParams(dp(29), dp(29)));

        TextView text = YamoneDesignSystem.text(this, label, 10, palette.muted, true);
        text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(1);
        item.addView(text, tp);

        item.setOnClickListener(v -> selectTab(tab));
        navItems[index] = item;
        navIcons[index] = icon;
        navLabels[index] = text;
        return item;
    }

    private void selectTab(String tab) {
        currentTab = tab;
        updateNav();
        if ("home".equals(tab)) showHome();
        else if ("activity".equals(tab)) showActivity();
        else if ("records".equals(tab)) showRecords();
        else if ("alarm".equals(tab)) showAlarm();
        else showSettings();
    }

    private void updateNav() {
        String[] tabs = {"home", "activity", "records", "alarm", "settings"};
        for (int i = 0; i < navItems.length; i++) {
            boolean selected = tabs[i].equals(currentTab);
            navIcons[i].setPalette(palette.primaryStrong, palette.muted, selected);
            navLabels[i].setTextColor(selected ? palette.primaryStrong : palette.muted);
            navItems[i].setBackground(selected
                    ? YamoneDesignSystem.rounded(palette.surfaceSoft, 14, this)
                    : YamoneDesignSystem.rounded(Color.TRANSPARENT, 14, this));
        }
    }

    // ---------------------------------------------------------------------
    // HOME
    // ---------------------------------------------------------------------

    private void showHome() {
        LinearLayout page = beginPage("오늘의 활동", null);
        YamoneMockData.HomeSummary summary = YamoneMockData.homeSummary();

        LinearLayout statsCard = YamoneDesignSystem.card(this, palette);
        statsCard.setPadding(dp(10), dp(12), dp(10), dp(13));

        LinearLayout statHeader = new LinearLayout(this);
        statHeader.setOrientation(LinearLayout.HORIZONTAL);
        statHeader.setGravity(Gravity.CENTER_VERTICAL);
        statHeader.addView(YamoneDesignSystem.text(this, "오늘", 13, palette.text, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView date = YamoneDesignSystem.text(this,
                new SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(Calendar.getInstance().getTime()),
                10, palette.muted, false);
        statHeader.addView(date);
        statsCard.addView(statHeader);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setPadding(0, dp(10), 0, 0);
        metrics.addView(metric(YamoneIconView.METRIC_TIME, "활동 시간", summary.activityTime), weightCard());
        LinearLayout.LayoutParams p2 = weightCard(); p2.leftMargin = dp(7);
        metrics.addView(metric(YamoneIconView.METRIC_DISTANCE, "이동 거리", summary.distance), p2);
        LinearLayout.LayoutParams p3 = weightCard(); p3.leftMargin = dp(7);
        metrics.addView(metric(YamoneIconView.METRIC_CALORIE, "칼로리", summary.calories), p3);
        statsCard.addView(metrics);
        page.addView(statsCard, sectionParams());

        if (!summary.runningItems.isEmpty()) {
            page.addView(sectionTitle("현재 동작 중"), titleParams());
            for (YamoneMockData.RunningItem item : summary.runningItems) {
                page.addView(runningCard(item), cardSpacing());
            }
        }

        LinearLayout sealBanner = YamoneDesignSystem.card(this, palette, palette.surfaceSoft, palette.border);
        sealBanner.setPadding(dp(14), dp(10), dp(10), dp(10));
        LinearLayout sealRow = new LinearLayout(this);
        sealRow.setOrientation(LinearLayout.HORIZONTAL);
        sealRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(YamoneDesignSystem.text(this, "꾸준한 오늘이", 14, palette.text, true));
        TextView line2 = YamoneDesignSystem.text(this, "더 좋은 내일을 만들어요", 12, palette.muted, false);
        line2.setPadding(0, dp(3), 0, 0);
        words.addView(line2);
        sealRow.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        YamoneIconView seal = new YamoneIconView(this, YamoneIconView.SEAL_HOME);
        seal.setPalette(palette.primaryStrong, palette.muted, true);
        sealRow.addView(seal, new LinearLayout.LayoutParams(dp(118), dp(74)));
        sealBanner.addView(sealRow);
        page.addView(sealBanner, sectionParams());
    }

    private View metric(int iconType, String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(5), dp(9), dp(5), dp(9));
        box.setBackground(YamoneDesignSystem.rounded(palette.surfaceSoft, 14, this));

        YamoneIconView icon = new YamoneIconView(this, iconType);
        icon.setPalette(palette.primaryStrong, palette.muted, true);
        box.addView(icon, new LinearLayout.LayoutParams(dp(29), dp(29)));

        TextView valueView = YamoneDesignSystem.text(this, value, 15, palette.text, true);
        valueView.setGravity(Gravity.CENTER);
        valueView.setPadding(0, dp(3), 0, 0);
        box.addView(valueView);

        TextView labelView = YamoneDesignSystem.text(this, label, 9, palette.muted, false);
        labelView.setGravity(Gravity.CENTER);
        labelView.setPadding(0, dp(2), 0, 0);
        box.addView(labelView);
        return box;
    }

    private View runningCard(YamoneMockData.RunningItem item) {
        LinearLayout card = YamoneDesignSystem.card(this, palette);
        card.setPadding(dp(14), dp(13), dp(10), dp(13));
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(YamoneDesignSystem.text(this, item.title + " · " + item.state, 15, palette.text, true));
        TextView meta = YamoneDesignSystem.text(this, item.elapsed + " · " + item.keyMetric, 11, palette.muted, false);
        meta.setPadding(0, dp(4), 0, 0);
        words.addView(meta);
        line.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        YamoneIconView arrow = chevron(YamoneIconView.CHEVRON_RIGHT, palette.primaryStrong);
        line.addView(arrow, new LinearLayout.LayoutParams(dp(21), dp(29)));
        card.addView(line);
        return card;
    }

    // ---------------------------------------------------------------------
    // ACTIVITY
    // ---------------------------------------------------------------------

    private void showActivity() {
        LinearLayout page = beginPage("활동", "원하는 활동을 선택하세요");
        List<YamoneMockData.ActivityCardData> cards = YamoneMockData.activityCards();
        for (YamoneMockData.ActivityCardData item : cards) {
            page.addView(activityCard(item), cardSpacing());
        }
    }

    private View activityCard(YamoneMockData.ActivityCardData item) {
        int fill = activityFill(item.tintKind);
        int border = activityBorder(item.tintKind);
        int accent = activityAccent(item.tintKind);

        LinearLayout card = YamoneDesignSystem.card(this, palette, fill, border);
        card.setPadding(dp(8), dp(11), dp(9), dp(11));
        card.setMinimumHeight(dp(88));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout iconWrap = new FrameLayout(this);
        iconWrap.setBackground(YamoneDesignSystem.rounded(0xAAFFFFFF, 17, this));
        YamoneIconView icon = new YamoneIconView(this, item.iconType);
        icon.setPalette(accent, palette.muted, true);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.CENTER);
        iconWrap.addView(icon, ip);
        row.addView(iconWrap, new LinearLayout.LayoutParams(dp(64), dp(64)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        // Renewal 1: icon-to-copy gap is deliberately compact.
        wp.leftMargin = dp(7);
        row.addView(words, wp);

        words.addView(YamoneDesignSystem.text(this, item.title, 16, palette.text, true));
        TextView sub = YamoneDesignSystem.text(this, item.subtitle, 11, palette.muted, false);
        sub.setPadding(0, dp(4), 0, 0);
        words.addView(sub);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.HORIZONTAL);
        right.setGravity(Gravity.CENTER_VERTICAL);
        if (item.badge != null && !item.badge.isEmpty()) {
            TextView badge = YamoneDesignSystem.badge(this, item.badge, palette);
            right.addView(badge);
            LinearLayout.LayoutParams spacer = new LinearLayout.LayoutParams(dp(6), 1);
            right.addView(new View(this), spacer);
        }
        // Every activity card, including the combined walk/run/bike card, has a clear arrow.
        YamoneIconView arrow = chevron(YamoneIconView.CHEVRON_RIGHT, accent);
        right.addView(arrow, new LinearLayout.LayoutParams(dp(22), dp(30)));
        row.addView(right);

        card.addView(row);
        card.setOnClickListener(v -> Toast.makeText(this,
                item.title + " 준비 화면은 다음 목업 단계에서 연결합니다.", Toast.LENGTH_SHORT).show());
        return card;
    }

    private int activityFill(int kind) {
        boolean pink = YamoneDesignSystem.THEME_PINK.equals(YamoneDesignSystem.currentTheme(this));
        switch (kind) {
            case 1: return pink ? 0xFFFFF0F6 : 0xFFF0F4FF;
            case 2: return 0xFFFFF2EA;
            case 3: return 0xFFF3F0FF;
            default: return pink ? 0xFFFFEDF3 : 0xFFE8F8F2;
        }
    }

    private int activityBorder(int kind) {
        switch (kind) {
            case 1: return 0xFFC8D4F0;
            case 2: return 0xFFF0CDBD;
            case 3: return 0xFFD6D0F0;
            default: return palette.border;
        }
    }

    private int activityAccent(int kind) {
        switch (kind) {
            case 1: return 0xFF5E7FD8;
            case 2: return 0xFFE8785E;
            case 3: return 0xFF7462CB;
            default: return palette.primaryStrong;
        }
    }

    // ---------------------------------------------------------------------
    // RECORDS
    // ---------------------------------------------------------------------

    private void showRecords() {
        LinearLayout page = beginPage("기록", null);

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"전체", "이동", "Snow", "수면"};
        for (int i = 0; i < names.length; i++) {
            TextView filter = filterPill(names[i], i == 0);
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, dp(38), 1f);
            if (i > 0) fp.leftMargin = dp(6);
            filters.addView(filter, fp);
        }
        page.addView(filters, sectionParams());

        page.addView(sectionTitle("최근 기록"), titleParams());
        page.addView(recordCard(YamoneIconView.NAV_ACTIVITY, "9월 11일", "달리기", "5.2 km · 32분 · 평균 6'12\"/km"), cardSpacing());
        page.addView(recordCard(YamoneIconView.ACTIVITY_SNOW, "9월 10일", "Snow · 스키", "용평 · 활주 14회 · 23.8 km"), cardSpacing());

        page.addView(sectionTitle("지난 기록"), titleParams());
        for (int i = 1; i <= 4; i++) {
            final int monthIndex = i;
            page.addView(monthAccordion(monthIndex, monthLabel(monthIndex)), cardSpacing());
        }
    }

    private View monthAccordion(int monthIndex, String month) {
        boolean open = openMonth == monthIndex;
        LinearLayout box = YamoneDesignSystem.card(this, palette);
        box.setPadding(dp(14), dp(12), dp(10), dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(YamoneDesignSystem.text(this, month, 14, palette.text, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Month control sits at the far right and points down/up, never glued to the title.
        YamoneIconView arrow = chevron(open ? YamoneIconView.CHEVRON_UP : YamoneIconView.CHEVRON_DOWN, palette.muted);
        header.addView(arrow, new LinearLayout.LayoutParams(dp(23), dp(23)));
        box.addView(header);

        if (open) {
            View divider = YamoneDesignSystem.divider(this, palette);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, this.dp(1));
            dp.topMargin = this.dp(10);
            box.addView(divider, dp);

            TextView d1 = YamoneDesignSystem.text(this, "23일  ·  걷기 + 달리기  ·  4.1 km · 48분", 11, palette.muted, false);
            d1.setPadding(0, this.dp(11), 0, this.dp(2));
            box.addView(d1);
        }

        box.setOnClickListener(v -> {
            openMonth = open ? -1 : monthIndex;
            showRecords();
        });
        return box;
    }

    private TextView filterPill(String label, boolean selected) {
        TextView v = YamoneDesignSystem.text(this, label, 12,
                selected ? Color.WHITE : palette.muted, true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(YamoneDesignSystem.roundedBorder(
                selected ? palette.primaryStrong : palette.surfaceSoft,
                selected ? palette.primaryStrong : palette.border,
                14, 1, this));
        return v;
    }

    private View recordCard(int iconType, String date, String activity, String summary) {
        LinearLayout card = YamoneDesignSystem.card(this, palette);
        card.setPadding(dp(10), dp(11), dp(10), dp(11));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        YamoneIconView icon = new YamoneIconView(this, iconType);
        icon.setPalette(palette.primaryStrong, palette.muted, true);
        row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wp.leftMargin = dp(8);
        row.addView(words, wp);
        words.addView(YamoneDesignSystem.text(this, activity, 15, palette.text, true));
        TextView dateView = YamoneDesignSystem.text(this, date, 10, palette.muted, false);
        dateView.setPadding(0, dp(2), 0, 0);
        words.addView(dateView);
        TextView meta = YamoneDesignSystem.text(this, summary, 10, palette.muted, false);
        meta.setPadding(0, dp(4), 0, 0);
        words.addView(meta);

        YamoneIconView arrow = chevron(YamoneIconView.CHEVRON_RIGHT, palette.muted);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(20), dp(28)));
        card.addView(row);
        return card;
    }

    // ---------------------------------------------------------------------
    // ALARM
    // ---------------------------------------------------------------------

    private void showAlarm() {
        LinearLayout page = beginPage("알람", "기존 알람 기능은 그대로 유지합니다");

        TextView add = YamoneDesignSystem.button(this, "+  알람 추가", true, palette);
        add.setOnClickListener(v -> startActivity(new Intent(this, AlarmActivity.class)));
        LinearLayout.LayoutParams ap = YamoneDesignSystem.match(50, this);
        ap.bottomMargin = dp(11);
        page.addView(add, ap);

        page.addView(alarmRow("오전 06:30", "월 · 화 · 수 · 목 · 금", true), cardSpacing());
        page.addView(alarmRow("오전 07:00", "매일", false), cardSpacing());
        page.addView(alarmRow("오후 09:00", "매일", true), cardSpacing());
        page.addView(alarmRow("오후 10:00", "주말 (토 · 일)", false), cardSpacing());

        TextView note = YamoneDesignSystem.text(this,
                "알람 행을 누르면 현재 알람 관리 화면으로 이동합니다.", 10, palette.muted, false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(5), 0, 0);
        page.addView(note);
    }

    private View alarmRow(String time, String repeat, boolean on) {
        LinearLayout card = YamoneDesignSystem.card(this, palette);
        card.setPadding(dp(14), dp(12), dp(13), dp(12));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        YamoneIconView icon = new YamoneIconView(this, YamoneIconView.NAV_ALARM);
        icon.setPalette(on ? palette.primaryStrong : palette.muted, palette.muted, on);
        row.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wp.leftMargin = dp(9);
        row.addView(words, wp);
        words.addView(YamoneDesignSystem.text(this, time, 18, palette.text, true));
        TextView repeatView = YamoneDesignSystem.text(this, repeat, 10, palette.muted, false);
        repeatView.setPadding(0, dp(2), 0, 0);
        words.addView(repeatView);

        TextView toggle = YamoneDesignSystem.text(this, on ? "ON" : "OFF", 10,
                on ? Color.WHITE : palette.muted, true);
        toggle.setGravity(Gravity.CENTER);
        toggle.setBackground(YamoneDesignSystem.roundedBorder(
                on ? palette.primaryStrong : palette.surfaceSoft,
                on ? palette.primaryStrong : palette.border,
                999, 1, this));
        row.addView(toggle, new LinearLayout.LayoutParams(dp(46), dp(28)));

        card.addView(row);
        card.setOnClickListener(v -> startActivity(new Intent(this, AlarmActivity.class)));
        return card;
    }

    // ---------------------------------------------------------------------
    // SETTINGS
    // ---------------------------------------------------------------------

    private void showSettings() {
        LinearLayout page = beginPage("설정", null);
        page.addView(sectionTitle("테마"), titleParams());

        LinearLayout themes = new LinearLayout(this);
        themes.setOrientation(LinearLayout.HORIZONTAL);
        themes.addView(themePreview(YamoneDesignSystem.THEME_MINT, "민트"), weightCard());
        LinearLayout.LayoutParams pink = weightCard();
        pink.leftMargin = dp(9);
        themes.addView(themePreview(YamoneDesignSystem.THEME_PINK, "핑크"), pink);
        page.addView(themes, sectionParams());

        page.addView(sectionTitle("설정"), titleParams());
        page.addView(settingsRow(YamoneIconView.NAV_ACTIVITY, "활동 기록", "기록 방식과 목표"), cardSpacing());
        page.addView(settingsRow(YamoneIconView.NAV_ACTIVITY, "자동감지", "걷기 · 달리기 · 자전거"), cardSpacing());
        page.addView(settingsRow(YamoneIconView.ACTIVITY_SNOW, "Snow", "스키 · 스노보드"), cardSpacing());
        page.addView(settingsRow(YamoneIconView.ACTIVITY_SLEEP, "수면", "수면 기록 설정"), cardSpacing());
        page.addView(settingsRow(YamoneIconView.NAV_ALARM, "알람", "시계 알람 설정"), cardSpacing());
        page.addView(settingsRow(YamoneIconView.NAV_SETTINGS, "권한", "위치 · 알림 · 마이크"), cardSpacing());
        page.addView(settingsRow(YamoneIconView.NAV_RECORDS, "데이터 / 저장", "기록과 저장 공간"), cardSpacing());
        page.addView(settingsRow(YamoneIconView.NAV_SETTINGS, "앱 정보", "버전 · 라이선스"), cardSpacing());

        if (developerUnlocked) {
            TextView dev = YamoneDesignSystem.button(this, "개발자 시뮬레이션", false, palette);
            dev.setOnClickListener(v -> showDeveloperPanel());
            LinearLayout.LayoutParams dp = YamoneDesignSystem.match(50, this);
            dp.topMargin = this.dp(9);
            page.addView(dev, dp);
        }
    }

    private View themePreview(String theme, String label) {
        YamoneDesignSystem.Palette p = YamoneDesignSystem.paletteFor(theme);
        boolean selected = theme.equals(YamoneDesignSystem.currentTheme(this));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setBackground(YamoneDesignSystem.roundedBorder(
                p.surface, selected ? p.primaryStrong : p.border, 18, selected ? 2 : 1, this));

        YamoneIconView seal = new YamoneIconView(this, YamoneIconView.SEAL_HOME);
        seal.setPalette(p.primaryStrong, p.muted, true);
        card.addView(seal, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));

        TextView name = YamoneDesignSystem.text(this, label + (selected ? "  ✓" : ""), 14, p.text, true);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(4), 0, 0);
        card.addView(name);

        View strip = new View(this);
        strip.setBackground(YamoneDesignSystem.rounded(p.primary, 6, this));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        sp.topMargin = dp(8);
        card.addView(strip, sp);

        card.setOnClickListener(v -> {
            YamoneDesignSystem.setTheme(this, theme);
            rebuild("settings");
        });
        return card;
    }

    private View settingsRow(int iconType, String title, String subtitle) {
        LinearLayout card = YamoneDesignSystem.card(this, palette);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        YamoneIconView icon = new YamoneIconView(this, iconType);
        icon.setPalette(palette.primaryStrong, palette.muted, true);
        row.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wp.leftMargin = dp(8);
        row.addView(words, wp);
        words.addView(YamoneDesignSystem.text(this, title, 14, palette.text, true));
        TextView sub = YamoneDesignSystem.text(this, subtitle, 10, palette.muted, false);
        sub.setPadding(0, dp(2), 0, 0);
        words.addView(sub);

        YamoneIconView arrow = chevron(YamoneIconView.CHEVRON_RIGHT, palette.muted);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(19), dp(27)));
        card.addView(row);

        card.setOnClickListener(v -> {
            if ("앱 정보".equals(title)) {
                appInfoTapCount++;
                if (appInfoTapCount >= 7 && !developerUnlocked) {
                    developerUnlocked = true;
                    prefs.edit().putBoolean("design_preview_developer_unlocked", true).apply();
                    Toast.makeText(this, "개발자 시뮬레이션 메뉴가 열렸습니다.", Toast.LENGTH_SHORT).show();
                    showSettings();
                    return;
                }
            }
            Toast.makeText(this, title + " 상세 화면은 다음 단계에서 연결합니다.", Toast.LENGTH_SHORT).show();
        });
        return card;
    }

    private void showDeveloperPanel() {
        LinearLayout page = beginPage("개발자 시뮬레이션", "디자인 검수용");
        String[] actions = {
                "시간 배속 · 빠르게",
                "시간 배속 · 매우 빠르게",
                "자동감지 발생",
                "GPS 끊김",
                "터널 진입",
                "Snow 하루 진행",
                "수면 8시간 진행"
        };
        for (String action : actions) {
            TextView button = YamoneDesignSystem.button(this, action, false, palette);
            button.setOnClickListener(v -> Toast.makeText(this,
                    ((TextView) v).getText() + " 시뮬레이션 준비", Toast.LENGTH_SHORT).show());
            page.addView(button, buttonSpacing());
        }
    }

    // ---------------------------------------------------------------------
    // COMMON LAYOUT HELPERS
    // ---------------------------------------------------------------------

    private LinearLayout beginPage(String title, String subtitle) {
        content.removeAllViews();
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(palette.background);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(19), dp(12), dp(19), dp(10));
        header.addView(YamoneDesignSystem.text(this, title, 25, palette.text, true));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = YamoneDesignSystem.text(this, subtitle, 11, palette.muted, false);
            s.setPadding(0, dp(3), 0, 0);
            header.addView(s);
        }
        shell.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(17), dp(4), dp(17), dp(28));
        scroll.addView(page);
        shell.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(shell);
        return page;
    }

    private YamoneIconView chevron(int type, int color) {
        YamoneIconView arrow = new YamoneIconView(this, type);
        arrow.setPalette(color, color, true);
        return arrow;
    }

    private TextView sectionTitle(String title) {
        return YamoneDesignSystem.text(this, title, 15, palette.text, true);
    }

    private LinearLayout.LayoutParams titleParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(11);
        p.bottomMargin = dp(8);
        return p;
    }

    private LinearLayout.LayoutParams sectionParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(5);
        p.bottomMargin = dp(8);
        return p;
    }

    private LinearLayout.LayoutParams cardSpacing() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(9);
        return p;
    }

    private LinearLayout.LayoutParams buttonSpacing() {
        LinearLayout.LayoutParams p = YamoneDesignSystem.match(50, this);
        p.bottomMargin = dp(9);
        return p;
    }

    private LinearLayout.LayoutParams weightCard() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private String monthLabel(int monthsAgo) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -monthsAgo);
        return new SimpleDateFormat("yyyy년 MM월", Locale.KOREAN).format(calendar.getTime());
    }

    private int dp(int value) {
        return YamoneDesignSystem.dp(this, value);
    }

    @Override public void onBackPressed() {
        if (!"home".equals(currentTab)) {
            selectTab("home");
            return;
        }
        super.onBackPressed();
    }
}
