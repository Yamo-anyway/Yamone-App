package com.yamo.snorelab;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Yamone mint/pink location sharing flow. Only the latest position is kept on the server. */
public class LocationSharingActivity extends Activity {
    private static final int REQ_SHARE_PERMISSIONS = 7111;
    private static final long ACTIVE_POLL_MS = 60_000L;
    private static final int DEFAULT_SHARE_MINUTES = 240;

    private int BG;
    private int CARD;
    private int CARD2;
    private int TEXT;
    private int MUTED;
    private int PRIMARY;
    private int PRIMARY2;
    private int SUCCESS;
    private int WARNING;
    private int BORDER;
    private int HINT;
    private int DANGER_BG;
    private int DANGER_TEXT;
    private int DANGER_BORDER;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private EditText roomNameInput;
    private EditText passwordInput;
    private EditText nicknameInput;
    private TextView availabilityText;
    private Button availabilityButton;
    private Button actionButton;
    private TextView interval1;
    private TextView interval3;
    private TextView interval5;
    private boolean createMode = true;
    private boolean availabilityOk;
    private String checkedRoomName = "";
    private int selectedIntervalSeconds = 60;
    private boolean pendingSubmitAfterPermission;
    private boolean askedActivePermission;

    private boolean activeScreen;
    private String currentPage = "loading";
    private TextView activeRoomTitle;
    private TextView activeInfo;
    private TextView activeRemaining;
    private TextView activeNetworkHint;
    private LinearLayout participantList;
    private LocationSharingMapView sharingMap;
    private ScrollView activeScroll;
    private TextView statusNormal;
    private TextView statusContact;
    private TextView statusHelp;
    private TextView statusEmergency;
    private TextView activeStatusHint;
    private String currentSelfStatus = "normal";

    private final Runnable activePoller = new Runnable() {
        @Override public void run() {
            if (!activeScreen) return;
            refreshActiveSnapshot();
            handler.postDelayed(this, ACTIVE_POLL_MS);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyTheme();
        configureSystemBars();
        showLoading();
        loadCurrentRoom();
    }

    @Override protected void onResume() {
        super.onResume();
        if (activeScreen) {
            scheduleActivePolling();
            ensureSharingService();
        }
    }

    @Override protected void onPause() {
        handler.removeCallbacks(activePoller);
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        if (sharingMap != null) sharingMap.onLowMemory();
    }

    @Override public void onBackPressed() {
        if ("room_form".equals(currentPage)) {
            showLanding();
            return;
        }
        finish();
    }

    private void applyTheme() {
        boolean pink = pink();
        BG = pink ? 0xFFFFF7FA : 0xFFF7FFFB;
        CARD = 0xFFFFFFFF;
        CARD2 = pink ? 0xFFFFEEF3 : 0xFFF0FAF6;
        TEXT = pink ? 0xFF4B2633 : 0xFF153633;
        MUTED = pink ? 0xFF9A7180 : 0xFF718984;
        PRIMARY = pink ? 0xFFFF769F : 0xFF56D1B3;
        PRIMARY2 = pink ? 0xFFE94778 : 0xFF159A7A;
        SUCCESS = pink ? 0xFFE94778 : 0xFF159A7A;
        WARNING = 0xFFE9A642;
        BORDER = pink ? 0xFFFFD7E3 : 0xFFD7EFE7;
        HINT = pink ? 0xFFB7929E : 0xFF8EA7A0;
        DANGER_BG = 0xFFFFF0F3;
        DANGER_TEXT = 0xFFE75B6D;
        DANGER_BORDER = 0xFFFFCBD3;
    }

    private boolean pink() {
        return "pink".equals(getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "pink"));
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void showLoading() {
        currentPage = "loading";
        activeScreen = false;
        LinearLayout root = rootShell();
        LinearLayout body = bodyPage();
        body.setGravity(Gravity.CENTER);
        ImageView pin = new ImageView(this);
        pin.setImageResource(R.drawable.ic_location_pin);
        pin.setColorFilter(PRIMARY2);
        pin.setPadding(dp(18), dp(18), dp(18), dp(18));
        pin.setBackground(round(CARD2, 34, 1, BORDER));
        LinearLayout.LayoutParams pinParams = new LinearLayout.LayoutParams(dp(68), dp(68));
        pinParams.gravity = Gravity.CENTER_HORIZONTAL;
        body.addView(pin, pinParams);
        TextView title = text("위치 공유", 25, TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(10), 0, dp(5));
        body.addView(title);
        TextView loading = text("공유 상태를 확인하고 있어요…", 13, MUTED, false);
        loading.setGravity(Gravity.CENTER);
        body.addView(loading);
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void loadCurrentRoom() {
        LocationSharingApi.snapshot(this, new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                runOnUiThread(() -> {
                    if (data.optBoolean("active", false)) showActive(data);
                    else {
                        LocationSharingStateStore.clear(LocationSharingActivity.this);
                        showLanding();
                    }
                });
            }

            @Override public void onFailure(String message) {
                runOnUiThread(() -> {
                    showLanding();
                    toast(message);
                });
            }
        });
    }

    /** 2/9: standalone location sharing menu. */
    private void showLanding() {
        currentPage = "landing";
        activeScreen = false;
        handler.removeCallbacks(activePoller);
        sharingMap = null;

        LinearLayout root = rootShell();
        root.addView(header("위치 공유", ""));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = bodyPage();

        TextView headline = text("지금, 소중한 사람들과\n함께 있는지 확인해보세요", 20, TEXT, true);
        headline.setPadding(0, dp(8), 0, dp(18));
        page.addView(headline);

        LinearLayout illustration = card();
        illustration.setGravity(Gravity.CENTER);
        YamonePastelArtView art = new YamonePastelArtView(this, YamonePastelArtView.MODE_LOCATION_SCENE);
        illustration.addView(art, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(138)));
        page.addView(illustration);

        LinearLayout create = locationMenuCard(R.drawable.ic_location_group, "방 만들기", "", true);
        create.setOnClickListener(v -> showRoomForm(true));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        cp.topMargin = dp(16);
        page.addView(create, cp);

        LinearLayout join = locationMenuCard(R.drawable.ic_location_group, "방 참여하기", "", false);
        join.setOnClickListener(v -> showRoomForm(false));
        LinearLayout.LayoutParams jp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        jp.topMargin = dp(10);
        page.addView(join, jp);

        LinearLayout info = card();
        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView infoIcon = new ImageView(this);
        infoIcon.setImageResource(R.drawable.ic_location_info);
        infoIcon.setColorFilter(PRIMARY2);
        infoIcon.setPadding(dp(10), dp(10), dp(10), dp(10));
        infoIcon.setBackground(round(CARD2, 22, 0, 0));
        infoRow.addView(infoIcon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout infoText = new LinearLayout(this);
        infoText.setOrientation(LinearLayout.VERTICAL);
        infoText.addView(text("위치 공유란?", 13, TEXT, true));
        infoText.addView(text("방에 참여한 사람끼리 마지막 위치만 확인해요.", 11, MUTED, false));
        infoRow.addView(infoText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        info.addView(infoRow);
        LinearLayout.LayoutParams ip = cardParams();
        ip.topMargin = dp(16);
        page.addView(info, ip);

        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    /** 3/9 and 4/9: fixed bottom action, scrollable settings. */
    private void showRoomForm(boolean create) {
        currentPage = "room_form";
        createMode = create;
        availabilityOk = false;
        checkedRoomName = "";
        selectedIntervalSeconds = 60;

        LinearLayout root = rootShell();
        root.addView(header(create ? "방 만들기" : "방 참여하기", ""));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = bodyPage();
        page.setPadding(dp(18), dp(8), dp(18), dp(22));

        page.addView(label("방 이름"));
        LinearLayout roomRow = new LinearLayout(this);
        roomRow.setOrientation(LinearLayout.HORIZONTAL);
        roomNameInput = input(create ? "예) 야모네 스키 여행" : "참여할 방 이름을 입력하세요",
                InputType.TYPE_CLASS_TEXT);
        roomNameInput.setSingleLine(true);
        roomNameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        roomRow.addView(roomNameInput, new LinearLayout.LayoutParams(0, dp(52), 1f));

        availabilityButton = smallButton("중복 확인");
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(96), dp(52));
        checkParams.leftMargin = dp(8);
        if (create) roomRow.addView(availabilityButton, checkParams);
        LinearLayout.LayoutParams roomRowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        roomRowParams.bottomMargin = dp(8);
        page.addView(roomRow, roomRowParams);

        availabilityText = text("", 11, MUTED, false);
        availabilityText.setPadding(0, dp(2), 0, dp(14));
        if (create) page.addView(availabilityText);
        else spacer(page, 12);

        roomNameInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                availabilityOk = false;
                checkedRoomName = "";
                if (availabilityText != null) availabilityText.setText("");
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        if (create) availabilityButton.setOnClickListener(v -> checkAvailability());

        page.addView(label("내 닉네임"));
        nicknameInput = input("예) 지민", InputType.TYPE_CLASS_TEXT);
        nicknameInput.setSingleLine(true);
        nicknameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        nicknameInput.setText(LocationProfileStore.getNickname(this));
        page.addView(nicknameInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        spacer(page, 14);

        page.addView(label("방 비밀번호 (숫자만)"));
        passwordInput = input("4~6자리 숫자를 입력하세요",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        passwordInput.setSingleLine(true);
        passwordInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        page.addView(passwordInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        spacer(page, 18);

        page.addView(label("공유 간격"));
        LinearLayout intervals = new LinearLayout(this);
        intervals.setOrientation(LinearLayout.HORIZONTAL);
        interval1 = intervalChip("1분", 60);
        interval3 = intervalChip("3분", 180);
        interval5 = intervalChip("5분", 300);
        intervals.addView(interval1, new LinearLayout.LayoutParams(0, dp(46), 1f));
        LinearLayout.LayoutParams i3 = new LinearLayout.LayoutParams(0, dp(46), 1f);
        i3.leftMargin = dp(9);
        intervals.addView(interval3, i3);
        LinearLayout.LayoutParams i5 = new LinearLayout.LayoutParams(0, dp(46), 1f);
        i5.leftMargin = dp(9);
        intervals.addView(interval5, i5);
        page.addView(intervals);
        styleIntervalChips();

        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setPadding(dp(18), dp(10), dp(18), dp(10));
        bottom.setBackgroundColor(BG);
        actionButton = primaryButton(create ? "방 만들기" : "방 참여하기", create);
        actionButton.setOnClickListener(v -> ensurePermissionsThenSubmit());
        bottom.addView(actionButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        root.addView(bottom);

        setContentView(root);
    }

    private TextView intervalChip(String label, int seconds) {
        TextView chip = text(label, 14, TEXT, true);
        chip.setGravity(Gravity.CENTER);
        chip.setOnClickListener(v -> {
            selectedIntervalSeconds = seconds;
            styleIntervalChips();
        });
        return chip;
    }

    private void styleIntervalChips() {
        styleInterval(interval1, selectedIntervalSeconds == 60);
        styleInterval(interval3, selectedIntervalSeconds == 180);
        styleInterval(interval5, selectedIntervalSeconds == 300);
    }

    private void styleInterval(TextView chip, boolean selected) {
        if (chip == null) return;
        chip.setTextColor(selected ? (pink() ? PRIMARY2 : 0xFF0C7F65) : TEXT);
        int fill = selected ? (pink() ? 0xFFFFE2EB : 0xFFDDF8EF) : CARD2;
        int stroke = selected ? PRIMARY : BORDER;
        chip.setBackground(round(fill, 18, 1, stroke));
    }

    private void checkAvailability() {
        String name = value(roomNameInput);
        if (name.length() < 2) {
            availabilityText.setText("방 이름을 2자 이상 입력해 주세요.");
            availabilityText.setTextColor(WARNING);
            return;
        }
        availabilityButton.setEnabled(false);
        availabilityText.setText("확인 중…");
        availabilityText.setTextColor(MUTED);
        LocationSharingApi.roomNameAvailable(this, name, new LocationSharingApi.BooleanCallback() {
            @Override public void onSuccess(boolean available) {
                runOnUiThread(() -> {
                    availabilityButton.setEnabled(true);
                    availabilityOk = available;
                    checkedRoomName = available ? name.trim() : "";
                    availabilityText.setText(available ? "사용 가능한 방 이름입니다. ✓" : "이미 사용 중인 방 이름입니다.");
                    availabilityText.setTextColor(available ? SUCCESS : WARNING);
                });
            }
            @Override public void onFailure(String message) {
                runOnUiThread(() -> {
                    availabilityButton.setEnabled(true);
                    availabilityOk = false;
                    checkedRoomName = "";
                    availabilityText.setText(message);
                    availabilityText.setTextColor(WARNING);
                });
            }
        });
    }

    private void ensurePermissionsThenSubmit() {
        if (hasLocationPermission()) submit();
        else {
            pendingSubmitAfterPermission = true;
            requestSharePermissions();
        }
    }

    private void submit() {
        String room = value(roomNameInput);
        String password = value(passwordInput);
        String nickname = value(nicknameInput);
        if (room.length() < 2) { toast("방 이름을 입력해 주세요."); return; }
        if (!password.matches("[0-9]{4,6}")) { toast("비밀번호는 숫자 4~6자리로 입력해 주세요."); return; }
        if (nickname.isEmpty()) { toast("닉네임을 입력해 주세요."); return; }
        if (createMode && (!availabilityOk || !room.trim().equals(checkedRoomName))) {
            toast("방 이름 중복 확인을 먼저 해 주세요.");
            return;
        }

        actionButton.setEnabled(false);
        actionButton.setText(createMode ? "방 만드는 중…" : "참여하는 중…");

        LocationSharingApi.JsonCallback callback = new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                runOnUiThread(() -> showActive(data));
            }
            @Override public void onFailure(String message) {
                runOnUiThread(() -> {
                    actionButton.setEnabled(true);
                    actionButton.setText(createMode ? "방 만들기" : "방 참여하기");
                    toast(message);
                });
            }
        };

        if (createMode) {
            LocationSharingApi.createRoom(this, room, password, nickname,
                    selectedIntervalSeconds, DEFAULT_SHARE_MINUTES, callback);
        } else {
            LocationSharingApi.joinRoom(this, room, password, nickname,
                    selectedIntervalSeconds, DEFAULT_SHARE_MINUTES, callback);
        }
    }

    /** 6/9 + 7/9: map, participants, extension and stop controls. */
    private void showActive(JSONObject initial) {
        currentPage = "active";
        activeScreen = true;
        activeRoomTitle = null;
        activeInfo = null;
        activeNetworkHint = null;
        activeStatusHint = null;

        LinearLayout root = rootShell();
        root.addView(header("위치 공유", ""));

        // The map is the fixed top section. Only the controls/list below it scroll.
        sharingMap = new LocationSharingMapView(this);
        sharingMap.setBackground(round(CARD2, 0, 0, 0));
        root.addView(sharingMap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));

        ScrollView scroll = new ScrollView(this);
        activeScroll = scroll;
        LinearLayout page = bodyPage();
        page.setPadding(dp(14), dp(8), dp(14), dp(24));

        // Only the three requested sharing controls: remaining time, extend, stop.
        LinearLayout timeRow = card();
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        timeRow.setPadding(dp(12), dp(8), dp(10), dp(8));

        activeRemaining = text("남은 시간 확인 중…", 13, TEXT, true);
        activeRemaining.setGravity(Gravity.CENTER_VERTICAL);
        activeRemaining.setIncludeFontPadding(false);
        timeRow.addView(activeRemaining, new LinearLayout.LayoutParams(
                0, dp(40), 1f));

        Button extend = softButton("시간 연장");
        extend.setTextSize(11);
        extend.setOnClickListener(v -> startActivity(new Intent(this, LocationSharingTimeActivity.class)));
        LinearLayout.LayoutParams extendParams = new LinearLayout.LayoutParams(dp(82), dp(40));
        extendParams.leftMargin = dp(6);
        timeRow.addView(extend, extendParams);

        Button leave = dangerButton("공유 중단");
        leave.setTextSize(11);
        leave.setOnClickListener(v -> confirmLeave());
        LinearLayout.LayoutParams leaveParams = new LinearLayout.LayoutParams(dp(82), dp(40));
        leaveParams.leftMargin = dp(6);
        timeRow.addView(leave, leaveParams);
        page.addView(timeRow, cardParams());

        LinearLayout myStatus = card();
        myStatus.setPadding(dp(12), dp(10), dp(12), dp(10));
        myStatus.addView(text("내 상태", 13, TEXT, true));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, dp(7), 0, 0);
        statusNormal = statusChoice("normal", "정상");
        statusContact = statusChoice("contact", "연락 요청");
        statusHelp = statusChoice("help", "도움 필요");
        statusEmergency = statusChoice("emergency", "긴급");
        statusRow.addView(statusNormal, new LinearLayout.LayoutParams(0, dp(38), 1f));
        statusRow.addView(statusContact, new LinearLayout.LayoutParams(0, dp(38), 1.28f));
        statusRow.addView(statusHelp, new LinearLayout.LayoutParams(0, dp(38), 1.28f));
        statusRow.addView(statusEmergency, new LinearLayout.LayoutParams(0, dp(38), 1f));
        myStatus.addView(statusRow);
        page.addView(myStatus, cardParams());

        LinearLayout participants = card();
        participants.setPadding(dp(10), dp(10), dp(10), dp(10));
        participants.addView(text("참여자", 13, TEXT, true));
        participantList = new LinearLayout(this);
        participantList.setOrientation(LinearLayout.VERTICAL);
        participantList.setPadding(0, dp(6), 0, 0);
        participants.addView(participantList);
        page.addView(participants, cardParams());

        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        applySnapshot(initial);
        ensureSharingService();
        scheduleInitialLocationRefreshes();
        scheduleActivePolling();
    }

    private void confirmLeave() {
        new AlertDialog.Builder(this)
                .setTitle("위치 공유를 중단할까요?")
                .setMessage("중단하면 내 위치가 더 이상 다른 참여자에게 표시되지 않아요.")
                .setNegativeButton("취소", null)
                .setPositiveButton("공유 중단하기", (d, w) -> leaveRoom())
                .show();
    }

    private void applySnapshot(JSONObject data) {
        if (!activeScreen) return;
        if (!data.optBoolean("active", false)) {
            LocationSharingStateStore.clear(this);
            LocationSharingService.stop(this);
            toast("위치 공유가 종료되었습니다.");
            showLanding();
            return;
        }

        String roomName = data.optString("room_name", "위치 공유 방");
        JSONArray members = data.optJSONArray("members");
        if (members == null) members = new JSONArray();
        JSONObject self = findSelf(members);
        String shareUntil = self == null ? data.optString("share_until", "") : self.optString("share_until", "");
        int interval = self == null ? data.optInt("update_interval_seconds", 60) : self.optInt("update_interval_seconds", 60);

        LocationSharingStateStore.update(this, roomName, shareUntil, interval, members.length());
        if (activeRoomTitle != null) activeRoomTitle.setText(roomName);
        if (activeRemaining != null) activeRemaining.setText("남은 시간  " + remainingText(shareUntil));
        if (activeInfo != null) activeInfo.setText(String.format(Locale.KOREAN,
                "참여 %d명 · 내 위치 %s 간격 공유", members.length(), intervalLabel(interval)));
        currentSelfStatus = self == null ? "normal" : self.optString("user_status", "normal");
        styleSelfStatus();
        if (sharingMap != null) sharingMap.setMembers(members);
        renderParticipants(members);
    }

    private void renderParticipants(JSONArray members) {
        if (participantList == null) return;
        participantList.removeAllViews();
        if (members.length() == 0) {
            participantList.addView(text("참여자 정보를 불러오는 중이에요.", 12, MUTED, false));
            return;
        }

        List<JSONObject> ordered = new ArrayList<>();
        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.optJSONObject(i);
            if (member != null) ordered.add(member);
        }
        ordered.sort((a, b) -> {
            boolean aSelf = a.optBoolean("is_self", false);
            boolean bSelf = b.optBoolean("is_self", false);
            if (aSelf != bSelf) return aSelf ? -1 : 1;
            String an = a.optString("nickname", "사용자").trim();
            String bn = b.optString("nickname", "사용자").trim();
            int ci = an.compareToIgnoreCase(bn);
            return ci != 0 ? ci : an.compareTo(bn);
        });

        for (int i = 0; i < ordered.size(); i++) {
            JSONObject member = ordered.get(i);
            boolean self = member.optBoolean("is_self", false);
            String nickname = member.optString("nickname", "사용자");
            String userStatus = member.optString("user_status", "normal");
            String lastLocationAt = member.optString("last_location_at", "");

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(9), dp(5), dp(9), dp(5));
            row.setBackground(round(CARD2, 12, 0, 0));

            View dot = new View(this);
            dot.setBackground(statusDotDrawable(userStatus, false));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(11), dp(11));
            dotParams.rightMargin = dp(8);
            row.addView(dot, dotParams);

            TextView name = text(nickname + (self ? "  (나)" : ""), 12, TEXT, self);
            name.setSingleLine(true);
            name.setGravity(Gravity.CENTER_VERTICAL);
            name.setIncludeFontPadding(false);
            row.addView(name, new LinearLayout.LayoutParams(0, dp(34), 1f));

            String age = ageText(lastLocationAt);
            TextView ageView = text(age.isEmpty() ? "위치 대기" : age, 11, MUTED, false);
            ageView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            ageView.setSingleLine(true);
            ageView.setIncludeFontPadding(false);
            row.addView(ageView, new LinearLayout.LayoutParams(dp(76), dp(34)));

            final String memberId = member.optString("member_id", "");
            final String memberNickname = nickname;
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                if (sharingMap == null) return;
                if (!sharingMap.moveToMember(memberId, memberNickname)) {
                    toast(memberNickname + "님의 위치를 아직 받지 못했어요.");
                }
            });

            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
            if (i > 0) rp.topMargin = dp(3);
            participantList.addView(row, rp);
        }
    }

    private TextView statusChoice(String key, String label) {
        TextView choice = text(label, 11, TEXT, false);
        choice.setGravity(Gravity.CENTER);
        choice.setCompoundDrawablePadding(dp(5));
        choice.setPadding(dp(2), 0, dp(2), 0);
        choice.setBackground(null);
        choice.setOnClickListener(v -> requestStatusChange(key));
        return choice;
    }

    private void requestStatusChange(String status) {
        if (status == null || status.equals(currentSelfStatus)) return;
        if ("help".equals(status) || "emergency".equals(status)) {
            String label = userStatusLabel(status);
            new AlertDialog.Builder(this)
                    .setTitle(label + " 상태로 바꿀까요?")
                    .setMessage("같은 방 참여자가 다음 상태 확인을 할 때 야모네가 기기 알림과 진동으로 알려줄 수 있어요.")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("상태 변경", (dialog, which) -> commitStatusChange(status))
                    .show();
            return;
        }
        commitStatusChange(status);
    }

    private void commitStatusChange(String status) {
        if (activeStatusHint != null) {
            activeStatusHint.setText("상태 변경 중…");
            activeStatusHint.setTextColor(MUTED);
        }
        LocationSharingApi.setStatus(this, status, new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                runOnUiThread(() -> {
                    applySnapshot(data);
                    refreshStatusAlertHint();
                });
            }
            @Override public void onFailure(String message) {
                runOnUiThread(() -> {
                    if (activeStatusHint != null) {
                        activeStatusHint.setText("상태를 변경하지 못했어요. 다시 시도해 주세요.");
                        activeStatusHint.setTextColor(WARNING);
                    }
                    toast(message);
                });
            }
        });
    }

    private void refreshStatusAlertHint() {
        if (activeStatusHint == null) return;
        if (hasNotificationPermission()) {
            activeStatusHint.setText("도움 필요·긴급 상태는 다른 참여자의 다음 확인 시 기기 알림으로 알려줘요.");
            activeStatusHint.setTextColor(MUTED);
        } else {
            activeStatusHint.setText("알림 권한이 꺼져 있어요. 상태는 화면에서 확인할 수 있지만 도움·긴급 기기 알림은 표시되지 않아요.");
            activeStatusHint.setTextColor(WARNING);
        }
    }

    private void styleSelfStatus() {
        styleStatusChoice(statusNormal, "normal");
        styleStatusChoice(statusContact, "contact");
        styleStatusChoice(statusHelp, "help");
        styleStatusChoice(statusEmergency, "emergency");
    }

    private void styleStatusChoice(TextView choice, String status) {
        if (choice == null) return;
        boolean selected = status.equals(currentSelfStatus);
        int color = LocationStatusPalette.color(status);
        choice.setTextColor(selected ? color : TEXT);
        choice.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        choice.setAlpha(selected ? 1f : 0.78f);
        GradientDrawable dot = statusDotDrawable(status, selected);
        int dotSize = dp(selected ? 14 : 11);
        dot.setBounds(0, 0, dotSize, dotSize);
        choice.setCompoundDrawables(dot, null, null, null);
        choice.setBackground(null);
    }

    private GradientDrawable statusDotDrawable(String status, boolean selected) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(LocationStatusPalette.color(status));
        if (selected) dot.setStroke(dp(2), LocationStatusPalette.color(status));
        return dot;
    }

    private int userStatusColor(String status, boolean self, String connectionState) {
        return LocationStatusPalette.color(status);
    }

    private int userStatusBackground(String status) {
        return LocationStatusPalette.softColor(status);
    }

    private String userStatusLabel(String status) {
        return LocationStatusPalette.label(status);
    }

    private String userStatusShortLabel(String status) {
        if ("emergency".equals(status)) return "긴급";
        if ("help".equals(status)) return "도움";
        if ("contact".equals(status)) return "연락";
        return "정상";
    }

    private String connectionText(String state, String lastLocationAt) {
        String age = ageText(lastLocationAt);
        if ("connected".equals(state)) return "지금" + (age.isEmpty() ? "" : " · " + age);
        if ("disconnected".equals(state)) return "연결 끊김" + (age.isEmpty() ? "" : " · 마지막 위치 " + age);
        if ("location_stale".equals(state)) return "위치 갱신 대기" + (age.isEmpty() ? "" : " · " + age);
        return "첫 위치를 기다리는 중";
    }

    private void refreshActiveSnapshot() {
        if (!activeScreen) return;
        LocationSharingApi.snapshot(this, new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) { runOnUiThread(() -> applySnapshot(data)); }
            @Override public void onFailure(String message) {
                runOnUiThread(() -> {
                    if (activeNetworkHint != null) {
                        activeNetworkHint.setText("네트워크 연결 대기 중 · 마지막으로 받은 위치를 유지합니다.");
                        activeNetworkHint.setTextColor(WARNING);
                    }
                });
            }
        });
    }

    private void scheduleInitialLocationRefreshes() {
        // Only the first few seconds bypass the participant cache so the first uploaded
        // position appears promptly. Regular polling remains one minute afterward.
        handler.postDelayed(this::refreshActiveSnapshotFresh, 1_500L);
        handler.postDelayed(this::refreshActiveSnapshotFresh, 4_000L);
        handler.postDelayed(this::refreshActiveSnapshotFresh, 8_000L);
    }

    private void refreshActiveSnapshotFresh() {
        if (!activeScreen) return;
        LocationSharingApi.snapshotFresh(this, new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) { runOnUiThread(() -> applySnapshot(data)); }
            @Override public void onFailure(String message) { }
        });
    }

    private void scheduleActivePolling() {
        handler.removeCallbacks(activePoller);
        if (activeScreen) handler.postDelayed(activePoller, ACTIVE_POLL_MS);
    }

    private void ensureSharingService() {
        if (!activeScreen) return;
        if (hasLocationPermission()) {
            LocationSharingService.start(this);
            if (!hasNotificationPermission() && !askedActivePermission) {
                askedActivePermission = true;
                requestSharePermissions();
            }
        } else if (!askedActivePermission) {
            askedActivePermission = true;
            requestSharePermissions();
        }
    }

    private void leaveRoom() {
        LocationSharingApi.leave(this, new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                runOnUiThread(() -> {
                    LocationSharingStateStore.clear(LocationSharingActivity.this);
                    LocationSharingService.stop(LocationSharingActivity.this);
                    toast("위치 공유를 종료했습니다.");
                    showLanding();
                });
            }
            @Override public void onFailure(String message) { runOnUiThread(() -> toast(message)); }
        });
    }

    private void requestSharePermissions() {
        List<String> permissions = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);

        if (permissions.isEmpty()) {
            if (pendingSubmitAfterPermission) {
                pendingSubmitAfterPermission = false;
                submit();
            } else if (activeScreen) LocationSharingService.start(this);
            return;
        }
        requestPermissions(permissions.toArray(new String[0]), REQ_SHARE_PERMISSIONS);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_SHARE_PERMISSIONS) return;
        if (!hasLocationPermission()) {
            pendingSubmitAfterPermission = false;
            toast("위치 공유를 사용하려면 위치 권한이 필요합니다.");
            return;
        }
        if (pendingSubmitAfterPermission) {
            pendingSubmitAfterPermission = false;
            submit();
        } else if (activeScreen) LocationSharingService.start(this);
        if (activeScreen) refreshStatusAlertHint();
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasNotificationPermission() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private JSONObject findSelf(JSONArray members) {
        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.optJSONObject(i);
            if (member != null && member.optBoolean("is_self", false)) return member;
        }
        return null;
    }

    private LinearLayout rootShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(0, dp(8), 0, dp(8));
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
                v.setPadding(0, top + dp(4), 0, bottom + dp(4));
                return insets;
            });
            root.requestApplyInsets();
        }
        return root;
    }

    private LinearLayout header(String title, String subtitle) {
    LinearLayout header = YamoneBackHeader.create(this, title, subtitle, BG, TEXT, MUTED, v -> {
        if ("room_form".equals(currentPage)) showLanding();
        else finish();
    });
    header.setPadding(dp(10), dp(5), dp(18), dp(5));
    return header;
}

    private LinearLayout bodyPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(8), dp(18), dp(32));
        page.setBackgroundColor(BG);
        return page;
    }

    private LinearLayout locationMenuCard(int iconRes, String title, String subtitle, boolean primary) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        // 64dp card: 44dp icon + 6dp vertical padding fits without clipping.
        card.setPadding(dp(14), dp(6), dp(12), dp(6));
        card.setBackground(round(CARD, 22, 1, BORDER));
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(primary ? 2 : 1));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(PRIMARY2);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(round(primary ? 0xFFFFE2EB : CARD2, 22, 0, 0));
        card.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setGravity(Gravity.CENTER_VERTICAL);
        words.setPadding(dp(12), 0, dp(8), 0);
        TextView titleView = text(title, 15, TEXT, true);
        titleView.setSingleLine(true);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setIncludeFontPadding(false);
        words.addView(titleView);
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView sub = text(subtitle, 11, MUTED, false);
            sub.setPadding(0, dp(3), 0, 0);
            words.addView(sub);
        }
        card.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_yamone_chevron_right);
        arrow.setColorFilter(PRIMARY2);
        arrow.setPadding(dp(7), dp(7), dp(7), dp(7));
        arrow.setBackground(round(CARD2, 17, 0, 0));
        card.addView(arrow, new LinearLayout.LayoutParams(dp(34), dp(34)));
        return card;
    }

    private TextView bigMenuButton(String title, String subtitle, boolean pinkButton) {
        LinearLayout wrapper = new LinearLayout(this);
        // Kept as TextView for a single, reliable rounded click target.
        TextView v = text(title + "\n" + subtitle, 15, Color.WHITE, true);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(20), 0, dp(18), 0);
        int fill = pinkButton ? 0xFFFF6F98 : 0xFF45CDAE;
        v.setBackground(round(fill, 22, 0, 0));
        return v;
    }

    private TextView label(String value) {
        TextView v = text(value, 13, TEXT, true);
        v.setPadding(0, dp(3), 0, dp(7));
        return v;
    }

    private EditText input(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(HINT);
        e.setTextColor(TEXT);
        e.setTextSize(14);
        e.setInputType(inputType);
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setBackground(round(CARD, 15, 1, BORDER));
        return e;
    }

    private Button primaryButton(String value, boolean pinkButton) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        b.setBackground(round(pinkButton ? 0xFFFF6F98 : 0xFF45CDAE, 18, 0, 0));
        return b;
    }

    private Button softButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(PRIMARY2);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        b.setBackground(round(CARD2, 16, 1, BORDER));
        return b;
    }

    private Button dangerButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(DANGER_TEXT);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        b.setBackground(round(DANGER_BG, 16, 1, DANGER_BORDER));
        return b;
    }

    private Button smallButton(String value) {
        Button b = new Button(this);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        b.setText(value);
        b.setTextColor(pink() ? PRIMARY2 : 0xFF0C7F65);
        b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackground(round(pink() ? 0xFFFFE2EB : 0xFFDDF8EF, 15, 1, PRIMARY));
        return b;
    }

    private Button tinyButton(String value) {
        Button b = softButton(value);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        b.setTextSize(10);
        b.setPadding(dp(5), 0, dp(5), 0);
        return b;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(round(CARD, 20, 1, BORDER));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(12);
        return p;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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

    private GradientDrawable round(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    private void spacer(LinearLayout parent, int heightDp) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private String value(EditText input) {
        return input == null ? "" : input.getText().toString().trim();
    }

    private String remainingText(String iso) {
        if (iso == null || iso.isEmpty()) return "-";
        try {
            long seconds = Math.max(0L, (Instant.parse(iso).toEpochMilli() - System.currentTimeMillis()) / 1000L);
            long hours = seconds / 3600L;
            long minutes = (seconds % 3600L) / 60L;
            if (hours > 0) return hours + "시간 " + minutes + "분";
            return Math.max(0L, minutes) + "분";
        } catch (DateTimeParseException e) { return "-"; }
    }

    private String ageText(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            long seconds = Math.max(0L, (System.currentTimeMillis() - Instant.parse(iso).toEpochMilli()) / 1000L);
            if (seconds < 60) return "방금";
            long minutes = seconds / 60L;
            if (minutes < 60) return minutes + "분 전";
            return (minutes / 60L) + "시간 전";
        } catch (DateTimeParseException e) { return ""; }
    }

    private String intervalLabel(int seconds) {
        return Math.max(1, seconds / 60) + "분";
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
