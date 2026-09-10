package com.yamo.snorelab;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AlarmActivity extends Activity {
    private static final String KEY_THEME = "yamone_theme";

    private int BG;
    private int CARD;
    private int CARD2;
    private int TEXT;
    private int MUTED;
    private int PRIMARY;
    private int PRIMARY2;
    private int DANGER;
    private int WARNING;

    private FrameLayout content;
    private LinearLayout bottomNav;
    private boolean editorOpen;
    private SharedPreferences prefs;
    private AudioTrack previewTrack;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(SleepRecorderService.PREFS, MODE_PRIVATE);
        applyTheme();
        buildRoot();
        showList();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4201);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (!editorOpen && content != null) showList();
    }

    @Override protected void onDestroy() {
        stopPreview();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private boolean pinkTheme() {
        return "pink".equals(prefs == null ? "mint" : prefs.getString(KEY_THEME, "pink"));
    }

    private void applyTheme() {
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
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = getWindow().getDecorView().getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        bottomNav = new LinearLayout(this);
        LinearLayout nav = bottomNav;
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(7), dp(8), dp(8));
        nav.setBackgroundColor(BG);
        if (Build.VERSION.SDK_INT >= 21) nav.setElevation(dp(6));
        nav.addView(navItem("⌂\n홈", false, v -> goMain("home")), new LinearLayout.LayoutParams(0, dp(62), 1f));
        nav.addView(navItem("🏃\n활동", false, v -> { startActivity(new Intent(this, LocationExerciseActivity.class)); finish(); }), new LinearLayout.LayoutParams(0, dp(62), 1f));
        nav.addView(navItem("⏰\n알람", true, v -> showList()), new LinearLayout.LayoutParams(0, dp(62), 1f));
        nav.addView(navItem("☾\n수면", false, v -> goMain("sleep")), new LinearLayout.LayoutParams(0, dp(62), 1f));
        root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (Build.VERSION.SDK_INT >= 21) {
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
                // Common Yamone rule: status bar inset + a small breathing space.
                v.setPadding(0, top + dp(4), 0, bottom);
                return insets;
            });
            root.requestApplyInsets();
        }
        setContentView(root);
    }

    private void setBottomNavVisible(boolean visible) {
        if (bottomNav != null) bottomNav.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void goMain(String screen) {
        startActivity(new Intent(this, MainActivity.class).putExtra("start_screen", screen));
        finish();
    }

    private LinearLayout fixedHeader(String title, String subtitle) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(8), dp(18), dp(9));
        header.setBackgroundColor(BG);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setGravity(Gravity.CENTER_VERTICAL);
        words.addView(text(title, 26, TEXT, true));
        header.addView(words, new LinearLayout.LayoutParams(0, dp(54), 1f));
        View gear = YamoneSettingsButton.create(this, v -> { startActivity(new Intent(this, MainActivity.class).putExtra("start_screen", "settings").putExtra("settings_return", "alarm")); finish(); });
        header.addView(gear, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return header;
    }

    private LinearLayout fixedBackHeader(String title, String subtitle) {
    LinearLayout header = YamoneBackHeader.create(this, title, subtitle, BG, TEXT, MUTED, v -> showList());
    header.setPadding(dp(12), dp(7), dp(18), dp(8));
    return header;
}

    private LinearLayout bodyPage() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(18), dp(6), dp(18), dp(34));
        p.setBackgroundColor(BG);
        return p;
    }

    private void showList() {
        editorOpen = false;
        setBottomNavVisible(true);
        stopPreview();
        content.removeAllViews();

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);
        shell.addView(fixedHeader("알람", ""), matchWrap());

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = bodyPage();
        scroll.addView(page);
        shell.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(shell);

        Button add = actionButton("＋  새 알람 만들기", v -> showEditor(null));
        LinearLayout.LayoutParams ap = match(dp(56));
        ap.bottomMargin = dp(12);
        page.addView(add, ap);

        if (!AlarmScheduler.canScheduleExact(this)) {
            LinearLayout permission = card();
            permission.addView(text("정확한 시간 알람 권한이 필요해요", 15, TEXT, true));
            TextView d = text("Android의 ‘알람 및 리마인더’ 접근을 허용해야 설정한 시각에 정확히 울릴 수 있어요.", 12, MUTED, false);
            d.setPadding(0, dp(7), 0, dp(10));
            permission.addView(d);
            permission.addView(ghostButton("권한 설정 열기", PRIMARY2, v -> requestExactAlarmAccess()), match(dp(46)));
            page.addView(permission, cardParams());
        }

        if (!canUseFullScreenAlarm()) {
            LinearLayout permission = card();
            permission.addView(text("잠금화면 전체화면 알람을 확인해주세요", 15, TEXT, true));
            TextView d = text("화면이 잠겨 있을 때 알람 화면을 바로 띄우려면 전체화면 알림 권한이 필요할 수 있어요.", 12, MUTED, false);
            d.setPadding(0, dp(7), 0, dp(10));
            permission.addView(d);
            permission.addView(ghostButton("전체화면 알람 설정", PRIMARY2, v -> requestFullScreenAlarmAccess()), match(dp(46)));
            page.addView(permission, cardParams());
        }

        List<AlarmStore.Item> alarms = AlarmStore.load(this);
        if (alarms.isEmpty()) {
            LinearLayout empty = card();
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView mascot = new ImageView(this);
            mascot.setImageResource(R.drawable.yamone_alarm);
            mascot.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            empty.addView(mascot, match(dp(190)));
            TextView title = text("첫 알람을 만들어볼까요?", 18, TEXT, true);
            title.setGravity(Gravity.CENTER);
            empty.addView(title);
            TextView d = text("알람음 또는 텍스트 읽기, 스누즈, 흔들어 종료까지 설정할 수 있어요.", 12, MUTED, false);
            d.setGravity(Gravity.CENTER);
            d.setPadding(0, dp(7), 0, 0);
            empty.addView(d);
            page.addView(empty, cardParams());
        } else {
            for (AlarmStore.Item item : alarms) page.addView(alarmCard(item), cardParams());
        }
    }

    private View alarmCard(AlarmStore.Item item) {
        LinearLayout c = card();
        c.setPadding(dp(15), dp(14), dp(15), dp(14));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_nav_alarm);
        icon.setColorFilter(PRIMARY2);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(rounded(CARD2, 23, 0, 0));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        iconParams.rightMargin = dp(12);
        head.addView(icon, iconParams);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView time = text(String.format(Locale.KOREAN, "%02d:%02d", item.hour, item.minute), 32,
                item.enabled ? TEXT : MUTED, true);
        left.addView(time);
        TextView label = text(item.label == null || item.label.trim().isEmpty() ? "알람" : item.label,
                12, item.enabled ? TEXT : MUTED, true);
        label.setPadding(0, dp(1), 0, 0);
        left.addView(label);
        head.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch enabled = new Switch(this);
        enabled.setChecked(item.enabled);
        enabled.setContentDescription(item.enabled ? "알람 켜짐" : "알람 꺼짐");
        head.addView(enabled, new LinearLayout.LayoutParams(dp(58), dp(52)));
        c.addView(head);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(11), 0, 0);

        String mode = "TTS".equals(item.alertMode) ? "텍스트 읽기" : "알람음";
        TextView modeChip = alarmChip(mode, item.enabled ? PRIMARY2 : MUTED);
        chips.addView(modeChip);

        TextView scheduleChip = alarmChip(scheduleText(item), item.enabled ? PRIMARY2 : MUTED);
        LinearLayout.LayoutParams scheduleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
        scheduleParams.leftMargin = dp(6);
        chips.addView(scheduleChip, scheduleParams);
        c.addView(chips);

        StringBuilder detail = new StringBuilder("스누즈 ").append(item.snoozeMinutes).append("분");
        if (item.vibrate) detail.append(" · 진동");
        if (item.shakeToStop) detail.append(" · 흔들기 ").append(item.shakeCount).append("회");
        TextView details = text(detail.toString(), 11, MUTED, false);
        details.setPadding(0, dp(7), 0, 0);
        c.addView(details);

        TextView next = text(item.enabled ? "다음  " + AlarmScheduler.nextDateText(item) : "알람 꺼짐",
                12, item.enabled ? PRIMARY2 : MUTED, true);
        next.setPadding(0, dp(6), 0, dp(10));
        c.addView(next);

        if (!item.skipDate.isEmpty()) {
            TextView skipped = text("이번 알람 건너뜀  " + item.skipDate, 11, WARNING, true);
            skipped.setPadding(dp(10), dp(7), dp(10), dp(7));
            skipped.setBackground(rounded(0xFFFFF6E7, 12, 0, 0));
            LinearLayout.LayoutParams skippedParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            skippedParams.bottomMargin = dp(9);
            c.addView(skipped, skippedParams);
        }

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button skip = ghostButton("이번만 건너뛰기", PRIMARY2, v -> skipNext(item));
        Button edit = ghostButton("수정", TEXT, v -> showEditor(item));
        Button del = ghostButton("삭제", DANGER, v -> confirmDelete(item));
        buttons.addView(skip, new LinearLayout.LayoutParams(0, dp(42), 1.35f));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(42), 0.8f);
        bp.leftMargin = dp(7);
        buttons.addView(edit, bp);
        LinearLayout.LayoutParams dpv = new LinearLayout.LayoutParams(0, dp(42), 0.8f);
        dpv.leftMargin = dp(7);
        buttons.addView(del, dpv);
        c.addView(buttons);

        enabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.enabled = isChecked;
            if (isChecked && item.specificDate != null && !item.specificDate.isEmpty()) {
                try {
                    if (LocalDate.parse(item.specificDate).isBefore(LocalDate.now())) {
                        item.enabled = false;
                        enabled.setChecked(false);
                        Toast.makeText(this, "이미 지난 날짜의 알람이에요.", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception ignored) {}
            }
            AlarmStore.save(this, item);
            if (item.enabled) {
                if (!AlarmScheduler.scheduleNext(this, item)) Toast.makeText(this, "정확한 알람 권한 또는 날짜를 확인해주세요.", Toast.LENGTH_LONG).show();
            } else {
                AlarmScheduler.cancelAll(this, item.id);
            }
            showList();
        });
        return c;
    }

    private TextView alarmChip(String value, int color) {
        TextView chip = text(value, 11, color, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(11), 0, dp(11), 0);
        chip.setBackground(rounded(CARD2, 12, 0, 0));
        chip.setSingleLine(true);
        return chip;
    }

    private void confirmDelete(AlarmStore.Item item) {
        new AlertDialog.Builder(this)
                .setTitle("알람 삭제")
                .setMessage(String.format(Locale.KOREAN, "%02d:%02d 알람을 삭제할까요?", item.hour, item.minute))
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (d, which) -> {
                    AlarmScheduler.cancelAll(this, item.id);
                    AlarmStore.delete(this, item.id);
                    Toast.makeText(this, "알람을 삭제했습니다.", Toast.LENGTH_SHORT).show();
                    showList();
                }).show();
    }

    private void skipNext(AlarmStore.Item item) {
        String old = item.skipDate;
        item.skipDate = "";
        long next = AlarmScheduler.nextTriggerMillis(item, System.currentTimeMillis());
        item.skipDate = old;
        if (next <= 0) {
            Toast.makeText(this, "건너뛸 다음 알람이 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        String date = Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        item.skipDate = date;
        AlarmStore.save(this, item);
        if (item.enabled) AlarmScheduler.scheduleNext(this, item);
        Toast.makeText(this, date + " 알람을 한 번 건너뜁니다.", Toast.LENGTH_LONG).show();
        showList();
    }

    private void showEditor(AlarmStore.Item existing) {
        editorOpen = true;
        setBottomNavVisible(false);
        stopPreview();
        content.removeAllViews();
        AlarmStore.Item draft = existing == null ? new AlarmStore.Item() : copy(existing);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);
        shell.addView(fixedBackHeader(existing == null ? "새 알람" : "알람 수정", "설정한 내용은 휴대폰에 저장돼요."), matchWrap());

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = bodyPage();
        scroll.addView(page);
        shell.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(shell);

        LinearLayout timeCard = card();
        timeCard.addView(text("알람 시간", 15, TEXT, true));
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER);
        Spinner hour = spinner(numberStrings(0, 23));
        hour.setSelection(draft.hour);
        Spinner minute = spinner(numberStrings(0, 59));
        minute.setSelection(draft.minute);
        timeRow.addView(hour, new LinearLayout.LayoutParams(0, dp(62), 1f));
        TextView colon = text(":", 32, PRIMARY2, true);
        colon.setGravity(Gravity.CENTER);
        timeRow.addView(colon, new LinearLayout.LayoutParams(dp(42), dp(62)));
        timeRow.addView(minute, new LinearLayout.LayoutParams(0, dp(62), 1f));
        timeCard.addView(timeRow);
        timeCard.addView(text("00:00 ~ 23:59 · 24시간 기준", 11, MUTED, false));
        page.addView(timeCard, cardParams());

        LinearLayout nameCard = card();
        nameCard.addView(text("알람 이름", 15, TEXT, true));
        EditText label = editText(draft.label, "예: 아침 운동");
        LinearLayout.LayoutParams lp = match(dp(54));
        lp.topMargin = dp(9);
        nameCard.addView(label, lp);
        page.addView(nameCard, cardParams());

        LinearLayout scheduleCard = card();
        scheduleCard.addView(text("반복 / 날짜", 15, TEXT, true));
        String[] selectedDate = {draft.specificDate == null ? "" : draft.specificDate};
        Switch dateMode = settingSwitch("특정 날짜에 한 번만", !selectedDate[0].isEmpty());
        scheduleCard.addView(dateMode);

        LinearLayout dateSection = new LinearLayout(this);
        dateSection.setOrientation(LinearLayout.VERTICAL);
        Button dateButton = ghostButton(selectedDate[0].isEmpty() ? "날짜 선택" : selectedDate[0], PRIMARY2, null);
        dateButton.setOnClickListener(v -> openDatePicker(selectedDate, dateButton));
        dateSection.addView(dateButton, match(dp(48)));
        TextView dateHint = text("지정 날짜는 요일 반복보다 우선합니다.", 11, MUTED, false);
        dateHint.setPadding(0, dp(5), 0, dp(5));
        dateSection.addView(dateHint);
        scheduleCard.addView(dateSection);

        LinearLayout repeatSection = new LinearLayout(this);
        repeatSection.setOrientation(LinearLayout.VERTICAL);
        repeatSection.addView(text("반복 요일", 12, MUTED, true));
        LinearLayout daysRow = new LinearLayout(this);
        daysRow.setOrientation(LinearLayout.HORIZONTAL);
        daysRow.setPadding(0, dp(10), 0, dp(6));
        String[] dayNames = {"일", "월", "화", "수", "목", "금", "토"};
        int[] storeIndex = {6, 0, 1, 2, 3, 4, 5};
        boolean[] selectedDays = draft.days.clone();
        TextView[] dayChips = new TextView[7];
        for (int i = 0; i < 7; i++) {
            final int uiIndex = i;
            final int dataIndex = storeIndex[i];
            TextView chip = dayChip(dayNames[i], selectedDays[dataIndex]);
            chip.setOnClickListener(v -> {
                selectedDays[dataIndex] = !selectedDays[dataIndex];
                styleDayChip(dayChips[uiIndex], selectedDays[dataIndex]);
            });
            dayChips[i] = chip;
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            if (i > 0) cp.leftMargin = dp(5);
            daysRow.addView(chip, cp);
        }
        repeatSection.addView(daysRow);
        repeatSection.addView(text("모두 해제하면 다음 가능한 시각에 한 번만 울려요.", 11, MUTED, false));
        scheduleCard.addView(repeatSection);
        Runnable refreshDateMode = () -> {
            dateSection.setVisibility(dateMode.isChecked() ? View.VISIBLE : View.GONE);
            repeatSection.setVisibility(dateMode.isChecked() ? View.GONE : View.VISIBLE);
        };
        dateMode.setOnCheckedChangeListener((buttonView, checked) -> refreshDateMode.run());
        refreshDateMode.run();
        page.addView(scheduleCard, cardParams());

        LinearLayout wakeCard = card();
        wakeCard.addView(text("알람 방식", 15, TEXT, true));
        wakeCard.addView(text("알람음과 텍스트 읽기는 동시에 사용하지 않아요.", 11, MUTED, false));
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setPadding(0, dp(10), 0, dp(8));
        String[] mode = {"TTS".equals(draft.alertMode) ? "TTS" : "SOUND"};
        Button soundMode = segmentButton("🔔 알람음", "SOUND".equals(mode[0]));
        Button ttsMode = segmentButton("🗣 텍스트 읽기", "TTS".equals(mode[0]));
        modeRow.addView(soundMode, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams ttp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        ttp.leftMargin = dp(8);
        modeRow.addView(ttsMode, ttp);
        wakeCard.addView(modeRow);

        LinearLayout soundSection = new LinearLayout(this);
        soundSection.setOrientation(LinearLayout.VERTICAL);
        soundSection.addView(text("알람음 선택", 12, MUTED, true));
        String[] sounds = {"부드러운 벨", "선명 알람", "빠른 펄스"};
        String[] soundValues = {"SOFT", "STRONG", "PULSE"};
        Spinner sound = spinner(sounds);
        sound.setSelection("SOFT".equals(draft.soundStyle) ? 0 : "PULSE".equals(draft.soundStyle) ? 2 : 1);
        soundSection.addView(sound, match(dp(52)));
        Button preview = ghostButton("▶  알람음 미리듣기", PRIMARY2, v -> previewSound(soundValues[sound.getSelectedItemPosition()]));
        LinearLayout.LayoutParams pvp = match(dp(44));
        pvp.topMargin = dp(6);
        soundSection.addView(preview, pvp);
        wakeCard.addView(soundSection);

        LinearLayout ttsSection = new LinearLayout(this);
        ttsSection.setOrientation(LinearLayout.VERTICAL);
        ttsSection.addView(text("읽을 텍스트", 12, MUTED, true));
        EditText speech = editText(draft.speechText, "예: 일어날 시간입니다. 오늘도 좋은 하루 보내세요.");
        speech.setSingleLine(false);
        speech.setMinLines(2);
        ttsSection.addView(speech, match(dp(82)));
        ttsSection.addView(text("음성", 12, MUTED, true));
        String[] voices = {"높은 음성 · 기기 TTS", "낮은 음성 · 기기 TTS"};
        Spinner voice = spinner(voices);
        voice.setSelection("MALE".equals(draft.voiceStyle) ? 1 : 0);
        ttsSection.addView(voice, match(dp(52)));
        TextView ttsNote = text("휴대폰의 TTS 엔진을 사용해 한글/영문을 읽습니다.", 11, MUTED, false);
        ttsNote.setPadding(0, dp(3), 0, 0);
        ttsSection.addView(ttsNote);
        wakeCard.addView(ttsSection);

        Runnable refreshMode = () -> {
            boolean isTts = "TTS".equals(mode[0]);
            styleSegment(soundMode, !isTts);
            styleSegment(ttsMode, isTts);
            soundSection.setVisibility(isTts ? View.GONE : View.VISIBLE);
            ttsSection.setVisibility(isTts ? View.VISIBLE : View.GONE);
        };
        soundMode.setOnClickListener(v -> { mode[0] = "SOUND"; refreshMode.run(); });
        ttsMode.setOnClickListener(v -> { mode[0] = "TTS"; refreshMode.run(); });
        refreshMode.run();
        page.addView(wakeCard, cardParams());

        LinearLayout cueCard = card();
        cueCard.addView(text("진동 / 스누즈", 15, TEXT, true));
        Switch vibrate = settingSwitch("알람과 함께 진동", draft.vibrate);
        cueCard.addView(vibrate);
        cueCard.addView(text("스누즈 간격", 12, MUTED, true));
        String[] snoozeLabels = {"3분", "5분", "10분", "15분", "30분"};
        int[] snoozeValues = {3, 5, 10, 15, 30};
        Spinner snooze = spinner(snoozeLabels);
        snooze.setSelection(positionOf(snoozeValues, draft.snoozeMinutes, 1));
        cueCard.addView(snooze, match(dp(52)));
        page.addView(cueCard, cardParams());

        LinearLayout volumeCard = card();
        volumeCard.addView(text("알람 볼륨", 15, TEXT, true));
        TextView volumeText = text("볼륨  " + draft.volumePercent + "%", 12, PRIMARY2, true);
        volumeCard.addView(volumeText);
        SeekBar volume = new SeekBar(this);
        volume.setMax(90);
        volume.setProgress(Math.max(0, draft.volumePercent - 10));
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                volumeText.setText("볼륨  " + (progress + 10) + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        volumeCard.addView(volume, match(dp(46)));
        Switch gradual = settingSwitch("처음에는 작게, 점점 크게", draft.gradualVolume);
        volumeCard.addView(gradual);
        LinearLayout gradualSection = new LinearLayout(this);
        gradualSection.setOrientation(LinearLayout.VERTICAL);
        gradualSection.addView(text("목표 볼륨까지 올라가는 시간", 12, MUTED, true));
        String[] gradualLabels = {"15초", "30초", "60초"};
        int[] gradualValues = {15, 30, 60};
        Spinner gradualSeconds = spinner(gradualLabels);
        gradualSeconds.setSelection(positionOf(gradualValues, draft.gradualSeconds, 1));
        gradualSection.addView(gradualSeconds, match(dp(52)));
        volumeCard.addView(gradualSection);
        gradualSection.setVisibility(gradual.isChecked() ? View.VISIBLE : View.GONE);
        gradual.setOnCheckedChangeListener((buttonView, checked) -> gradualSection.setVisibility(checked ? View.VISIBLE : View.GONE));
        page.addView(volumeCard, cardParams());

        LinearLayout retryCard = card();
        retryCard.addView(text("놓친 알람 재알림", 15, TEXT, true));
        retryCard.addView(text("알람 화면에서 종료하지 않았을 때 다시 울리는 설정이에요.", 11, MUTED, false));
        String[] intervals = {"1분 간격", "3분 간격", "5분 간격", "10분 간격", "15분 간격", "30분 간격"};
        int[] intervalValues = {1, 3, 5, 10, 15, 30};
        Spinner interval = spinner(intervals);
        interval.setSelection(positionOf(intervalValues, draft.retryMinutes, 2));
        retryCard.addView(interval, match(dp(52)));
        String[] counts = {"반복 안 함", "1회", "2회", "3회", "5회", "10회", "무제한 · 종료할 때까지"};
        int[] countValues = {0, 1, 2, 3, 5, 10, -1};
        Spinner count = spinner(counts);
        count.setSelection(positionOf(countValues, draft.retryCount, 3));
        retryCard.addView(count, match(dp(52)));
        page.addView(retryCard, cardParams());

        LinearLayout stopCard = card();
        stopCard.addView(text("알람 종료", 15, TEXT, true));
        Switch shake = settingSwitch("흔들어서만 종료", draft.shakeToStop);
        stopCard.addView(shake);
        TextView stopRule = text("켜면 화면/알림의 일반 종료 버튼으로는 끌 수 없고, 정한 횟수만큼 흔들어야 종료돼요. 스누즈는 사용할 수 있어요.", 11, MUTED, false);
        stopRule.setPadding(0, 0, 0, dp(6));
        stopCard.addView(stopRule);
        LinearLayout shakeSection = new LinearLayout(this);
        shakeSection.setOrientation(LinearLayout.VERTICAL);
        shakeSection.addView(text("필요한 흔들기 횟수", 12, MUTED, true));
        String[] shakeOptions = numberStrings(3, 10);
        Spinner shakeCount = spinner(shakeOptions);
        shakeCount.setSelection(Math.max(0, Math.min(7, draft.shakeCount - 3)));
        shakeSection.addView(shakeCount, match(dp(52)));
        stopCard.addView(shakeSection);
        shakeSection.setVisibility(shake.isChecked() ? View.VISIBLE : View.GONE);
        shake.setOnCheckedChangeListener((buttonView, checked) -> shakeSection.setVisibility(checked ? View.VISIBLE : View.GONE));
        page.addView(stopCard, cardParams());

        Button save = actionButton("알람 저장", v -> {
            draft.hour = hour.getSelectedItemPosition();
            draft.minute = minute.getSelectedItemPosition();
            draft.label = label.getText().toString().trim();
            if (draft.label.isEmpty()) draft.label = "알람";

            if (dateMode.isChecked()) {
                if (selectedDate[0].isEmpty()) {
                    Toast.makeText(this, "알람 날짜를 선택해주세요.", Toast.LENGTH_LONG).show();
                    return;
                }
                draft.specificDate = selectedDate[0];
                for (int i = 0; i < 7; i++) draft.days[i] = false;
            } else {
                draft.specificDate = "";
                for (int i = 0; i < 7; i++) draft.days[i] = selectedDays[i];
            }

            draft.retryMinutes = intervalValues[interval.getSelectedItemPosition()];
            draft.retryCount = countValues[count.getSelectedItemPosition()];
            draft.snoozeMinutes = snoozeValues[snooze.getSelectedItemPosition()];
            draft.alertMode = mode[0];
            draft.soundStyle = soundValues[sound.getSelectedItemPosition()];
            draft.speechText = speech.getText().toString().trim();
            if ("TTS".equals(draft.alertMode) && draft.speechText.isEmpty()) {
                Toast.makeText(this, "텍스트 읽기 방식은 읽을 문구를 입력해주세요.", Toast.LENGTH_LONG).show();
                return;
            }
            draft.voiceStyle = voice.getSelectedItemPosition() == 1 ? "MALE" : "FEMALE";
            draft.vibrate = vibrate.isChecked();
            draft.volumePercent = volume.getProgress() + 10;
            draft.gradualVolume = gradual.isChecked();
            draft.gradualSeconds = gradualValues[gradualSeconds.getSelectedItemPosition()];
            draft.shakeToStop = shake.isChecked();
            draft.shakeCount = shakeCount.getSelectedItemPosition() + 3;
            draft.enabled = true;
            draft.skipDate = "";

            AlarmStore.save(this, draft);
            boolean ok = AlarmScheduler.scheduleNext(this, draft);
            if (!ok) {
                Toast.makeText(this, "알람은 저장했어요. 정확한 알람 권한이나 날짜를 확인해주세요.", Toast.LENGTH_LONG).show();
                if (!AlarmScheduler.canScheduleExact(this)) requestExactAlarmAccess();
            } else {
                Toast.makeText(this, "알람을 저장했습니다.", Toast.LENGTH_SHORT).show();
            }
            showList();
        });
        LinearLayout.LayoutParams sp = match(dp(56));
        sp.topMargin = dp(4);
        sp.bottomMargin = dp(10);
        page.addView(save, sp);

        if (existing != null) {
            Button delete = ghostButton("이 알람 삭제", DANGER, v -> confirmDelete(existing));
            LinearLayout.LayoutParams delp = match(dp(48));
            delp.bottomMargin = dp(18);
            page.addView(delete, delp);
        }
    }

    private void openDatePicker(String[] selectedDate, Button button) {
        LocalDate base;
        try {
            base = selectedDate[0].isEmpty() ? LocalDate.now() : LocalDate.parse(selectedDate[0]);
        } catch (Exception e) {
            base = LocalDate.now();
        }
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            LocalDate chosen = LocalDate.of(year, month + 1, dayOfMonth);
            selectedDate[0] = chosen.format(DateTimeFormatter.ISO_LOCAL_DATE);
            button.setText(selectedDate[0]);
        }, base.getYear(), base.getMonthValue() - 1, base.getDayOfMonth());
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 60_000L);
        dialog.show();
    }

    private AlarmStore.Item copy(AlarmStore.Item a) {
        AlarmStore.Item b = new AlarmStore.Item();
        b.id = a.id;
        b.hour = a.hour;
        b.minute = a.minute;
        b.label = a.label;
        b.enabled = a.enabled;
        System.arraycopy(a.days, 0, b.days, 0, 7);
        b.specificDate = a.specificDate;
        b.skipDate = a.skipDate;
        b.retryMinutes = a.retryMinutes;
        b.retryCount = a.retryCount;
        b.snoozeMinutes = a.snoozeMinutes;
        b.alertMode = a.alertMode;
        b.soundStyle = a.soundStyle;
        b.speechText = a.speechText;
        b.voiceStyle = a.voiceStyle;
        b.vibrate = a.vibrate;
        b.volumePercent = a.volumePercent;
        b.gradualVolume = a.gradualVolume;
        b.gradualSeconds = a.gradualSeconds;
        b.shakeToStop = a.shakeToStop;
        b.shakeCount = a.shakeCount;
        return b;
    }

    private boolean canUseFullScreenAlarm() {
        if (Build.VERSION.SDK_INT < 34) return true;
        NotificationManager nm = getSystemService(NotificationManager.class);
        return nm == null || nm.canUseFullScreenIntent();
    }

    private void requestFullScreenAlarmAccess() {
        if (Build.VERSION.SDK_INT < 34) return;
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) {}
        }
    }

    private void requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT < 31) return;
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) {}
        }
    }

    private String scheduleText(AlarmStore.Item a) {
        if (a.specificDate != null && !a.specificDate.isEmpty()) return a.specificDate + " 1회";
        if (!a.repeats()) return "한 번만";
        boolean all = true;
        for (boolean d : a.days) all &= d;
        if (all) return "매일";
        String[] names = {"월", "화", "수", "목", "금", "토", "일"};
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if (a.days[i]) {
                if (b.length() > 0) b.append(" ");
                b.append(names[i]);
            }
        }
        return b.toString();
    }

    private void previewSound(String style) {
        stopPreview();
        try {
            int sampleRate = 44_100;
            int seconds = 2;
            short[] pcm = synth(style, sampleRate, seconds);
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            previewTrack = new AudioTrack(attrs, format, pcm.length * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
            previewTrack.write(pcm, 0, pcm.length);
            previewTrack.setVolume(0.35f);
            previewTrack.play();
            handler.postDelayed(this::stopPreview, 2200L);
        } catch (Exception e) {
            Toast.makeText(this, "미리듣기를 재생하지 못했어요.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPreview() {
        handler.removeCallbacksAndMessages(null);
        if (previewTrack != null) {
            try { previewTrack.stop(); } catch (Exception ignored) {}
            try { previewTrack.release(); } catch (Exception ignored) {}
            previewTrack = null;
        }
    }

    static short[] synth(String style, int sampleRate, int seconds) {
        short[] pcm = new short[sampleRate * seconds];
        for (int i = 0; i < pcm.length; i++) {
            double t = i / (double) sampleRate;
            double amp;
            double hz;
            if ("PULSE".equals(style)) {
                boolean on = ((int) (t * 7)) % 2 == 0;
                hz = 980.0;
                amp = on ? 0.80 : 0.0;
            } else if ("SOFT".equals(style)) {
                hz = 620.0 + 90.0 * Math.sin(2.0 * Math.PI * 0.7 * t);
                amp = 0.50 + 0.12 * Math.sin(2.0 * Math.PI * 1.3 * t);
            } else {
                int part = ((int) (t * 2)) % 4;
                hz = part < 2 ? 820.0 : 1080.0;
                amp = ((int) (t * 6)) % 3 == 2 ? 0.28 : 0.84;
            }
            double phase = t % 0.25;
            double edge = Math.min(1.0, Math.min(phase / 0.018, (0.25 - phase) / 0.018));
            if (edge < 0) edge = 0;
            pcm[i] = (short) (Short.MAX_VALUE * amp * edge * Math.sin(2.0 * Math.PI * hz * t));
        }
        return pcm;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(15), dp(16), dp(15));
        c.setBackground(rounded(CARD, 20, 0, 0));
        return c;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams p = matchWrap();
        p.bottomMargin = dp(11);
        return p;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private EditText editText(String value, String hint) {
        EditText e = new EditText(this);
        e.setText(value == null ? "" : value);
        e.setHint(hint);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setPadding(dp(12), dp(9), dp(12), dp(9));
        e.setBackground(rounded(CARD2, 14, 1, PRIMARY));
        return e;
    }

    private Button actionButton(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(pinkTheme() ? 0xFF4B2633 : 0xFF08352A);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(PRIMARY, 16, 0, 0));
        b.setOnClickListener(listener);
        return b;
    }

    private Button ghostButton(String label, int color, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(color);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(CARD2, 14, 1, color == DANGER ? DANGER : PRIMARY));
        if (listener != null) b.setOnClickListener(listener);
        return b;
    }

    private Button segmentButton(String label, boolean selected) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        styleSegment(b, selected);
        return b;
    }

    private void styleSegment(Button b, boolean selected) {
        b.setTextColor(selected ? (pinkTheme() ? 0xFF4B2633 : 0xFF08352A) : TEXT);
        b.setBackground(rounded(selected ? PRIMARY : CARD2, 14, 1, selected ? PRIMARY : PRIMARY2));
    }

    private TextView dayChip(String label, boolean selected) {
        TextView v = text(label, 14, TEXT, true);
        v.setGravity(Gravity.CENTER);
        styleDayChip(v, selected);
        return v;
    }

    private void styleDayChip(TextView v, boolean selected) {
        if (v == null) return;
        v.setTextColor(selected ? (pinkTheme() ? 0xFF4B2633 : 0xFF08352A) : MUTED);
        v.setBackground(rounded(selected ? PRIMARY : CARD2, 12, 1, selected ? PRIMARY : PRIMARY2));
    }

    private Switch settingSwitch(String label, boolean checked) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(TEXT);
        s.setTextSize(14);
        s.setChecked(checked);
        s.setPadding(0, dp(7), 0, dp(5));
        return s;
    }

    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                styleSpinnerText(v, false);
                return v;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                styleSpinnerText(v, true);
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
        s.setBackground(rounded(CARD2, 12, 1, PRIMARY));
        return s;
    }

    private void styleSpinnerText(TextView v, boolean dropdown) {
        v.setTextColor(TEXT);
        v.setTextSize(16);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(14), dp(10), dp(14), dp(10));
        if (dropdown) v.setBackgroundColor(CARD2);
    }

    private TextView navItem(String label, boolean selected, View.OnClickListener listener) {
        int icon = label.contains("활동") ? R.drawable.ic_nav_activity
                : label.contains("알람") ? R.drawable.ic_nav_alarm
                : label.contains("수면") ? R.drawable.ic_nav_sleep
                : R.drawable.ic_nav_home;
        String clean = label.contains("활동") ? "활동"
                : label.contains("알람") ? "알람"
                : label.contains("수면") ? "수면" : "홈";
        return YamoneBottomNav.create(this, icon, clean, selected, PRIMARY2, MUTED, CARD2, listener);
    }

    private GradientDrawable rounded(int fill, float radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private static String[] numberStrings(int start, int end) {
        ArrayList<String> values = new ArrayList<>();
        for (int i = start; i <= end; i++) values.add(String.format(Locale.US, "%02d", i));
        return values.toArray(new String[0]);
    }

    private static int positionOf(int[] values, int value, int fallback) {
        for (int i = 0; i < values.length; i++) if (values[i] == value) return i;
        return Math.max(0, Math.min(values.length - 1, fallback));
    }

    private LinearLayout.LayoutParams match(int h) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override public void onBackPressed() {
        if (editorOpen) showList();
        else super.onBackPressed();
    }
}
