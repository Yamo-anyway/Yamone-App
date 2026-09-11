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

public class YamoneDesignPreviewActivity extends Activity {
    private YamoneDesignSystem.Palette palette;
    private SharedPreferences prefs;
    private FrameLayout content;
    private LinearLayout root;
    private TextView adBar;
    private final TextView[] navViews = new TextView[5];
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void buildRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        adBar = YamoneDesignSystem.text(this, "AD  ·  배너 광고 영역", 11, palette.muted, false);
        adBar.setGravity(Gravity.CENTER);
        adBar.setBackground(YamoneDesignSystem.roundedBorder(palette.surface, palette.border, 12, 1, this));
        LinearLayout.LayoutParams adParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        adParams.setMargins(dp(16), dp(3), dp(16), dp(5));
        root.addView(adBar, adParams);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(5), dp(8), dp(7));
        nav.setBackgroundColor(palette.surface);
        if (Build.VERSION.SDK_INT >= 21) nav.setElevation(dp(8));

        navViews[0] = navItem("⌂", "홈", "home");
        navViews[1] = navItem("●", "활동", "activity");
        navViews[2] = navItem("≡", "기록", "records");
        navViews[3] = navItem("◷", "알람", "alarm");
        navViews[4] = navItem("⚙", "설정", "settings");
        for (TextView item : navViews) nav.addView(item, new LinearLayout.LayoutParams(0, dp(58), 1f));
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

    private TextView navItem(String icon, String label, String tab) {
        TextView view = YamoneDesignSystem.text(this, icon + "\n" + label, 11, palette.muted, true);
        view.setGravity(Gravity.CENTER);
        view.setOnClickListener(v -> selectTab(tab));
        return view;
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
        for (int i = 0; i < navViews.length; i++) {
            boolean selected = tabs[i].equals(currentTab);
            navViews[i].setTextColor(selected ? palette.primaryStrong : palette.muted);
            navViews[i].setBackground(selected
                    ? YamoneDesignSystem.rounded(palette.surfaceSoft, 15, this)
                    : ColorDrawableCompat.transparent());
        }
    }

    private void showHome() {
        LinearLayout page = beginPage("오늘의 활동", null);
        YamoneMockData.HomeSummary summary = YamoneMockData.homeSummary();

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(metric("활동 시간", summary.activityTime), weightCard());
        LinearLayout.LayoutParams p2 = weightCard(); p2.leftMargin = dp(8);
        metrics.addView(metric("이동 거리", summary.distance), p2);
        LinearLayout.LayoutParams p3 = weightCard(); p3.leftMargin = dp(8);
        metrics.addView(metric("칼로리", summary.calories), p3);
        page.addView(metrics, sectionParams());

        if (!summary.runningItems.isEmpty()) {
            TextView h = sectionTitle("현재 동작 중");
            page.addView(h, titleParams());
            for (YamoneMockData.RunningItem item : summary.runningItems) {
                page.addView(runningCard(item), cardSpacing());
            }
        }

        TextView hint = YamoneDesignSystem.text(this,
                "활동 중인 항목이 있으면 이곳에 최대 2개의 카드가 표시됩니다.", 11, palette.muted, false);
        hint.setPadding(dp(2), dp(8), dp(2), dp(6));
        page.addView(hint);
    }

    private View metric(String label, String value) {
        LinearLayout card = YamoneDesignSystem.card(this, palette);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(8), dp(15), dp(8), dp(15));
        TextView valueView = YamoneDesignSystem.text(this, value, 17, palette.text, true);
        valueView.setGravity(Gravity.CENTER);
        TextView labelView = YamoneDesignSystem.text(this, label, 11, palette.muted, false);
        labelView.setGravity(Gravity.CENTER);
        labelView.setPadding(0, dp(5), 0, 0);
        card.addView(valueView);
        card.addView(labelView);
        return card;
    }

    private View runningCard(YamoneMockData.RunningItem item) {
        LinearLayout card = YamoneDesignSystem.card(this, palette);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(YamoneDesignSystem.text(this, item.title + " · " + item.state, 15, palette.text, true));
        TextView meta = YamoneDesignSystem.text(this, item.elapsed + " · " + item.keyMetric, 11, palette.muted, false);
        meta.setPadding(0, dp(5), 0, 0);
        words.addView(meta);
        line.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        line.addView(YamoneDesignSystem.text(this, "›", 28, palette.primaryStrong, true));
        card.addView(line);
        return card;
    }

    private void showActivity() {
        LinearLayout page = beginPage("활동", "원하는 활동을 선택하세요");
        List<YamoneMockData.ActivityCardData> cards = YamoneMockData.activityCards();
        for (YamoneMockData.ActivityCardData item : cards) {
            page.addView(activityCard(item), cardSpacing());
        }
    }

    private View activityCard(YamoneMockData.ActivityCardData item) {
        LinearLayout card = YamoneDesignSystem.card(this, palette);
        card.setPadding(dp(16), dp(15), dp(14), dp(15));
        card.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = YamoneDesignSystem.text(this, item.icon, 20, palette.primaryStrong, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(YamoneDesignSystem.rounded(palette.surfaceSoft, 18, this));
        row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wp.leftMargin = dp(13);
        row.addView(words, wp);
        words.addView(YamoneDesignSystem.text(this, item.title, 16, palette.text, true));
        TextView sub = YamoneDesignSystem.text(this, item.subtitle, 11, palette.muted, false);
        sub.setPadding(0, dp(4), 0, 0);
        words.addView(sub);

        if (item.badge != null && !item.badge.isEmpty()) {
            row.addView(YamoneDesignSystem.badge(this, item.badge, palette));
        } else {
            row.addView(YamoneDesignSystem.text(this, "›", 27, palette.primaryStrong, true));
        }
        card.addView(row);
        card.setOnClickListener(v -> Toast.makeText(this, item.title + " 준비 화면은 다음 목업 단계에서 연결합니다.", Toast.LENGTH_SHORT).show());
        return card;
    }

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
        page.addView(recordCard("9월 11일", "달리기", "5.2 km · 32분 · 평균 6'12\"/km"), cardSpacing());
        page.addView(recordCard("9월 10일", "Snow", "용평 · 활주 14회 · 23.8 km"), cardSpacing());

        for (int i = 1; i <= 4; i++) {
            final int monthIndex = i;
            String month = monthLabel(i);
            LinearLayout monthBox = YamoneDesignSystem.card(this, palette);
            monthBox.setPadding(dp(15), dp(13), dp(15), dp(13));
            TextView header = YamoneDesignSystem.text(this, month + (openMonth == i ? "  ⌄" : "  ›"), 14, palette.text, true);
            monthBox.addView(header);
            monthBox.setOnClickListener(v -> {
                openMonth = openMonth == monthIndex ? -1 : monthIndex;
                showRecords();
            });
            if (openMonth == i) {
                TextView d = YamoneDesignSystem.text(this, "8월 23일   걷기 + 달리기   4.1 km · 48분", 11, palette.muted, false);
                d.setPadding(0, dp(12), 0, dp(2));
                monthBox.addView(d);
            }
            page.addView(monthBox, cardSpacing());
        }
    }

    private TextView filterPill(String label, boolean selected) {
        TextView v = YamoneDesignSystem.text(this, label, 12, selected ? Color.WHITE : palette.muted, true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(YamoneDesignSystem.rounded(selected ? palette.primaryStrong : palette.surfaceSoft, 13, this));
        return v;
    }

    private View recordCard(String date, String activity, String summary) {
        LinearLayout card = YamoneDesignSystem.card(this, palette);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.addView(YamoneDesignSystem.text(this, date, 11, palette.muted, true));
        TextView title = YamoneDesignSystem.text(this, activity, 16, palette.text, true);
        title.setPadding(0, dp(4), 0, 0);
        card.addView(title);
        TextView meta = YamoneDesignSystem.text(this, summary, 11, palette.muted, false);
        meta.setPadding(0, dp(5), 0, 0);
        card.addView(meta);
        return card;
    }

    private void showAlarm() {
        LinearLayout page = beginPage("알람", null);
        LinearLayout card = YamoneDesignSystem.card(this, palette);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.addView(YamoneDesignSystem.text(this, "기존 알람 기능 유지", 17, palette.text, true));
        TextView explain = YamoneDesignSystem.text(this,
                "알람 동작 로직은 그대로 두고 다음 단계에서 새 디자인을 입힙니다.", 12, palette.muted, false);
        explain.setPadding(0, dp(7), 0, dp(16));
        card.addView(explain);
        TextView open = YamoneDesignSystem.button(this, "현재 알람 기능 열기", true, palette);
        open.setOnClickListener(v -> startActivity(new Intent(this, AlarmActivity.class)));
        card.addView(open, YamoneDesignSystem.match(52, this));
        page.addView(card, sectionParams());
    }

    private void showSettings() {
        LinearLayout page = beginPage("설정", null);
        page.addView(sectionTitle("테마"), titleParams());

        LinearLayout themes = new LinearLayout(this);
        themes.setOrientation(LinearLayout.HORIZONTAL);
        themes.addView(themePreview(YamoneDesignSystem.THEME_MINT, "민트"), weightCard());
        LinearLayout.LayoutParams pink = weightCard(); pink.leftMargin = dp(9);
        themes.addView(themePreview(YamoneDesignSystem.THEME_PINK, "핑크"), pink);
        page.addView(themes, sectionParams());

        page.addView(sectionTitle("설정"), titleParams());
        String[] rows = {"활동 기록", "자동감지", "Snow", "수면", "알람", "권한", "데이터 / 저장", "앱 정보"};
        for (String row : rows) page.addView(settingsRow(row), cardSpacing());

        if (developerUnlocked) {
            TextView dev = YamoneDesignSystem.button(this, "개발자 시뮬레이션", false, palette);
            dev.setOnClickListener(v -> showDeveloperPanel());
            LinearLayout.LayoutParams dp = YamoneDesignSystem.match(50, this);
            dp.topMargin = this.dp(8);
            page.addView(dev, dp);
        }
    }

    private View themePreview(String theme, String label) {
        YamoneDesignSystem.Palette p = YamoneDesignSystem.paletteFor(theme);
        boolean selected = theme.equals(YamoneDesignSystem.currentTheme(this));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(13), dp(13), dp(13));
        card.setBackground(YamoneDesignSystem.roundedBorder(
                p.surface, selected ? p.primaryStrong : p.border, 18, selected ? 2 : 1, this));
        TextView top = YamoneDesignSystem.text(this, label + (selected ? "  ✓" : ""), 15, p.text, true);
        card.addView(top);
        View swatch = new View(this);
        swatch.setBackground(YamoneDesignSystem.rounded(p.primary, 10, this));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28));
        sp.topMargin = dp(10);
        card.addView(swatch, sp);
        card.setOnClickListener(v -> {
            YamoneDesignSystem.setTheme(this, theme);
            rebuild("settings");
        });
        return card;
    }

    private View settingsRow(String title) {
        LinearLayout card = YamoneDesignSystem.card(this, palette);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), dp(12), dp(15), dp(12));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(YamoneDesignSystem.text(this, title, 14, palette.text, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(YamoneDesignSystem.text(this, "›", 25, palette.primaryStrong, true));
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
            Toast.makeText(this, title + " 상세는 디자인 목업에서 이어집니다.", Toast.LENGTH_SHORT).show();
        });
        return card;
    }

    private void showDeveloperPanel() {
        LinearLayout page = beginPage("개발자 시뮬레이션", "디자인 검수용");
        String[] actions = {"시간 배속 · 빠르게", "시간 배속 · 매우 빠르게", "자동감지 발생", "GPS 끊김", "터널 진입", "Snow 하루 진행"};
        for (String action : actions) {
            TextView button = YamoneDesignSystem.button(this, action, false, palette);
            button.setOnClickListener(v -> Toast.makeText(this, ((TextView) v).getText() + " 시뮬레이션 준비", Toast.LENGTH_SHORT).show());
            page.addView(button, buttonSpacing());
        }
    }

    private LinearLayout beginPage(String title, String subtitle) {
        content.removeAllViews();
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(palette.background);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(13), dp(20), dp(11));
        header.addView(YamoneDesignSystem.text(this, title, 25, palette.text, true));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = YamoneDesignSystem.text(this, subtitle, 11, palette.muted, false);
            s.setPadding(0, dp(3), 0, 0);
            header.addView(s);
        }
        shell.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(5), dp(18), dp(28));
        scroll.addView(page);
        shell.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(shell);
        return page;
    }

    private TextView sectionTitle(String title) {
        return YamoneDesignSystem.text(this, title, 15, palette.text, true);
    }

    private LinearLayout.LayoutParams titleParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(12);
        p.bottomMargin = dp(8);
        return p;
    }

    private LinearLayout.LayoutParams sectionParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(5);
        p.bottomMargin = dp(8);
        return p;
    }

    private LinearLayout.LayoutParams cardSpacing() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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

    private int dp(int value) { return YamoneDesignSystem.dp(this, value); }

    @Override public void onBackPressed() {
        if (!"home".equals(currentTab)) {
            selectTab("home");
            return;
        }
        super.onBackPressed();
    }

    private static final class ColorDrawableCompat {
        static android.graphics.drawable.ColorDrawable transparent() {
            return new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT);
        }
    }
}
