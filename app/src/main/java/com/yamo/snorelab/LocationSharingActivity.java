package com.yamo.snorelab;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Room creation/joining and live MapLibre location-sharing UI. */
public class LocationSharingActivity extends Activity {
    private static final int REQ_SHARE_PERMISSIONS = 7111;
    private static final long ACTIVE_POLL_MS = 15_000L;

    private static final int BG = 0xFF0B1324;
    private static final int CARD = 0xFF16243B;
    private static final int CARD2 = 0xFF111C31;
    private static final int TEXT = 0xFFF5F7FF;
    private static final int MUTED = 0xFF9DA9BF;
    private static final int PRIMARY = 0xFF6D72FF;
    private static final int PRIMARY2 = 0xFF8B8FFF;
    private static final int SUCCESS = 0xFF61D6A8;
    private static final int WARNING = 0xFFFFC56D;

    private static final String[] INTERVAL_LABELS = {"30초", "1분", "3분", "5분", "10분"};
    private static final int[] INTERVAL_SECONDS = {30, 60, 180, 300, 600};
    private static final String[] DURATION_LABELS = {"30분", "1시간", "2시간", "4시간", "8시간", "12시간"};
    private static final int[] DURATION_MINUTES = {30, 60, 120, 240, 480, 720};

    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout rootPage;
    private EditText roomNameInput;
    private EditText passwordInput;
    private EditText nicknameInput;
    private Spinner intervalSpinner;
    private Spinner durationSpinner;
    private TextView modeCreate;
    private TextView modeJoin;
    private TextView availabilityText;
    private Button availabilityButton;
    private Button actionButton;
    private boolean createMode = true;
    private boolean availabilityOk;
    private String checkedRoomName = "";
    private boolean pendingSubmitAfterPermission;
    private boolean askedActivePermission;

    private boolean activeScreen;
    private TextView activeRoomTitle;
    private TextView activeInfo;
    private TextView activeRemaining;
    private TextView activeNetworkHint;
    private LinearLayout participantList;
    private LocationSharingMapView sharingMap;

    private final Runnable activePoller = new Runnable() {
        @Override public void run() {
            if (!activeScreen) return;
            refreshActiveSnapshot();
            handler.postDelayed(this, ACTIVE_POLL_MS);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
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

    private void showLoading() {
        activeScreen = false;
        handler.removeCallbacks(activePoller);
        ScrollView scroll = shell();
        rootPage.addView(text("📍 위치 공유", 25, TEXT, true));
        TextView sub = text("현재 위치만 공유하며 이동 경로는 서버에 저장하지 않습니다.", 12, MUTED, false);
        sub.setPadding(0, dp(6), 0, dp(18));
        rootPage.addView(sub);
        LinearLayout card = card();
        TextView loading = text("위치 공유 상태 확인 중…", 14, MUTED, true);
        loading.setGravity(Gravity.CENTER);
        card.addView(loading, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(90)));
        rootPage.addView(card, cardParams());
        setContentView(scroll);
    }

    private void loadCurrentRoom() {
        LocationSharingApi.snapshot(this, new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                runOnUiThread(() -> {
                    if (data.optBoolean("active", false)) showActive(data);
                    else showEntry();
                });
            }

            @Override public void onFailure(String message) {
                runOnUiThread(() -> {
                    showEntry();
                    toast(message);
                });
            }
        });
    }

    private void showEntry() {
        activeScreen = false;
        handler.removeCallbacks(activePoller);
        sharingMap = null;
        ScrollView scroll = shell();
        addHeader("📍 위치 공유", "방을 만들거나 기존 방에 참여해 서로의 마지막 위치를 확인합니다.");

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeCreate = modeChip("방 만들기", true);
        modeJoin = modeChip("방 참여", false);
        modeCreate.setOnClickListener(v -> switchMode(true));
        modeJoin.setOnClickListener(v -> switchMode(false));
        modeRow.addView(modeCreate, new LinearLayout.LayoutParams(0, dp(44), 1f));
        LinearLayout.LayoutParams jp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        jp.leftMargin = dp(8);
        modeRow.addView(modeJoin, jp);
        rootPage.addView(modeRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout form = card();
        form.setPadding(dp(16), dp(17), dp(16), dp(18));

        form.addView(label("방 이름"));
        LinearLayout roomRow = new LinearLayout(this);
        roomRow.setOrientation(LinearLayout.HORIZONTAL);
        roomNameInput = input("2~40자", InputType.TYPE_CLASS_TEXT);
        roomNameInput.setSingleLine(true);
        roomNameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        roomRow.addView(roomNameInput, new LinearLayout.LayoutParams(0, dp(50), 1f));
        availabilityButton = smallButton("중복 확인");
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(94), dp(50));
        ap.leftMargin = dp(8);
        roomRow.addView(availabilityButton, ap);
        form.addView(roomRow);
        availabilityText = text("", 11, MUTED, false);
        availabilityText.setPadding(0, dp(6), 0, dp(10));
        form.addView(availabilityText);

        roomNameInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                availabilityOk = false;
                checkedRoomName = "";
                if (createMode) {
                    availabilityText.setText("");
                    availabilityText.setTextColor(MUTED);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        availabilityButton.setOnClickListener(v -> checkAvailability());

        form.addView(label("비밀번호"));
        passwordInput = input("숫자 4자리", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        passwordInput.setSingleLine(true);
        passwordInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        form.addView(passwordInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        spacer(form, 11);

        form.addView(label("닉네임"));
        nicknameInput = input("닉네임", InputType.TYPE_CLASS_TEXT);
        nicknameInput.setSingleLine(true);
        nicknameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        nicknameInput.setText(LocationProfileStore.getNickname(this));
        form.addView(nicknameInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        TextView nickNote = text("설정의 닉네임을 우선 사용합니다. 이 방에서만 다른 이름으로 바꿔도 됩니다.", 11, MUTED, false);
        nickNote.setPadding(0, dp(6), 0, dp(11));
        form.addView(nickNote);

        form.addView(label("위치 갱신 주기"));
        intervalSpinner = spinner(INTERVAL_LABELS);
        intervalSpinner.setSelection(1);
        form.addView(intervalSpinner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        TextView battery = text("갱신 주기가 짧을수록 위치는 더 자주 갱신되지만 배터리와 데이터 사용량이 증가할 수 있습니다.", 11, WARNING, false);
        battery.setPadding(0, dp(6), 0, dp(11));
        form.addView(battery);

        form.addView(label("공유 가능 시간"));
        durationSpinner = spinner(DURATION_LABELS);
        durationSpinner.setSelection(3);
        form.addView(durationSpinner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        TextView expiry = text("공유 시간이 끝나면 자동으로 방에서 나갑니다. 종료 5분 전 알림과 시간 연장은 다음 단계에서 연결합니다.", 11, MUTED, false);
        expiry.setPadding(0, dp(6), 0, dp(14));
        form.addView(expiry);

        actionButton = primaryButton("방 만들기");
        actionButton.setOnClickListener(v -> ensurePermissionsThenSubmit());
        form.addView(actionButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        rootPage.addView(form, cardParams());

        LinearLayout principle = card();
        principle.addView(text("🔒 위치 공유 원칙", 14, TEXT, true));
        TextView rule = text("서버에는 참여 중인 사람의 마지막 위치만 유지합니다. 과거 경로는 저장하지 않으며, 마지막 사람이 나가거나 공유시간이 만료되면 빈 방은 자동 삭제됩니다.", 12, MUTED, false);
        rule.setPadding(0, dp(8), 0, 0);
        principle.addView(rule);
        rootPage.addView(principle, cardParams());

        setContentView(scroll);
        switchMode(createMode);
    }

    private void switchMode(boolean create) {
        createMode = create;
        if (modeCreate == null) return;
        styleMode(modeCreate, create);
        styleMode(modeJoin, !create);
        availabilityButton.setVisibility(create ? View.VISIBLE : View.GONE);
        availabilityText.setVisibility(create ? View.VISIBLE : View.GONE);
        actionButton.setText(create ? "방 만들기" : "방 참여하기");
        if (!create) {
            availabilityOk = false;
            checkedRoomName = "";
        }
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
        if (hasLocationPermission()) {
            submit();
            return;
        }
        pendingSubmitAfterPermission = true;
        requestSharePermissions();
    }

    private void submit() {
        String room = value(roomNameInput);
        String password = value(passwordInput);
        String nickname = value(nicknameInput);
        if (room.length() < 2) { toast("방 이름을 입력해 주세요."); return; }
        if (!password.matches("[0-9]{4}")) { toast("비밀번호는 숫자 4자리로 입력해 주세요."); return; }
        if (nickname.isEmpty()) { toast("닉네임을 입력해 주세요."); return; }
        if (createMode && (!availabilityOk || !room.trim().equals(checkedRoomName))) {
            toast("방 이름 중복 확인을 먼저 해 주세요.");
            return;
        }

        int interval = INTERVAL_SECONDS[Math.max(0, intervalSpinner.getSelectedItemPosition())];
        int duration = DURATION_MINUTES[Math.max(0, durationSpinner.getSelectedItemPosition())];
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

        if (createMode) LocationSharingApi.createRoom(this, room, password, nickname, interval, duration, callback);
        else LocationSharingApi.joinRoom(this, room, password, nickname, interval, duration, callback);
    }

    private void showActive(JSONObject initial) {
        activeScreen = true;
        ScrollView scroll = shell();
        addHeader("📍 위치 공유 중", "활동과 별개로 화면이 꺼져도 위치 공유가 계속됩니다.");

        LinearLayout status = card();
        activeRoomTitle = text("위치 공유 방", 22, TEXT, true);
        status.addView(activeRoomTitle);
        activeInfo = text("참여 상태 확인 중…", 12, MUTED, false);
        activeInfo.setPadding(0, dp(7), 0, dp(4));
        status.addView(activeInfo);
        activeRemaining = text("공유 종료까지 -", 12, PRIMARY2, true);
        status.addView(activeRemaining);
        activeNetworkHint = text("상대 위치가 갱신 주기를 넘겨 들어오지 않으면 마지막 위치를 유지하고 ‘연결 끊김’으로 표시합니다.", 11, MUTED, false);
        activeNetworkHint.setPadding(0, dp(7), 0, 0);
        status.addView(activeNetworkHint);
        rootPage.addView(status, cardParams());

        sharingMap = new LocationSharingMapView(this);
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360));
        mapParams.topMargin = dp(12);
        rootPage.addView(sharingMap, mapParams);

        LinearLayout participants = card();
        LinearLayout participantHead = new LinearLayout(this);
        participantHead.setOrientation(LinearLayout.HORIZONTAL);
        participantHead.setGravity(Gravity.CENTER_VERTICAL);
        participantHead.addView(text("참여자", 14, TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button nick = tinyButton("내 닉네임 변경");
        nick.setOnClickListener(v -> showNicknameDialog());
        participantHead.addView(nick, new LinearLayout.LayoutParams(dp(122), dp(38)));
        participants.addView(participantHead);
        participantList = new LinearLayout(this);
        participantList.setOrientation(LinearLayout.VERTICAL);
        participantList.setPadding(0, dp(8), 0, 0);
        participants.addView(participantList);
        rootPage.addView(participants, cardParams());

        Button refresh = softButton("방 상태 새로고침");
        refresh.setOnClickListener(v -> refreshActiveSnapshot());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        rp.topMargin = dp(12);
        rootPage.addView(refresh, rp);

        Button leave = dangerButton("위치 공유 종료 · 방 나가기");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        lp.topMargin = dp(10);
        rootPage.addView(leave, lp);
        leave.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("위치 공유를 종료할까요?")
                .setMessage("내 위치 정보는 서버에서 삭제되고 방에서 나갑니다. 다른 사람이 남아 있으면 방은 계속 유지됩니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("종료", (d, w) -> leaveRoom())
                .show());

        setContentView(scroll);
        applySnapshot(initial);
        ensureSharingService();
        scheduleActivePolling();
    }

    private void applySnapshot(JSONObject data) {
        if (!activeScreen) return;
        if (!data.optBoolean("active", false)) {
            LocationSharingService.stop(this);
            toast("위치 공유가 종료되었습니다.");
            showEntry();
            return;
        }

        String roomName = data.optString("room_name", "위치 공유 방");
        JSONArray members = data.optJSONArray("members");
        if (members == null) members = new JSONArray();
        JSONObject self = findSelf(members);
        String nickname = self == null ? "-" : self.optString("nickname", "-");
        String shareUntil = self == null ? "" : self.optString("share_until", "");
        int interval = self == null ? 60 : self.optInt("update_interval_seconds", 60);

        activeRoomTitle.setText(roomName);
        activeInfo.setText(String.format(Locale.KOREAN, "참여 %d명 · 내 닉네임 %s · %s 갱신", members.length(), nickname, intervalLabel(interval)));
        activeRemaining.setText("공유 종료까지 " + remainingText(shareUntil));
        sharingMap.setMembers(members);
        renderParticipants(members);
    }

    private void renderParticipants(JSONArray members) {
        participantList.removeAllViews();
        if (members.length() == 0) {
            participantList.addView(text("참여자가 없습니다.", 12, MUTED, false));
            return;
        }
        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.optJSONObject(i);
            if (member == null) continue;
            boolean self = member.optBoolean("is_self", false);
            String nickname = member.optString("nickname", "사용자");
            String state = member.optString("connection_state", "waiting");
            String lastLocationAt = member.optString("last_location_at", "");

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(10), dp(9), dp(10), dp(9));
            row.setBackground(round(CARD2, 13, 0, 0));
            String title = self ? "● " + nickname + "  (나)" : "● " + nickname;
            if ("disconnected".equals(state) || "location_stale".equals(state)) title = "⚠ " + nickname + (self ? "  (나)" : "");
            else if ("waiting".equals(state)) title = "… " + nickname + (self ? "  (나)" : "");
            row.addView(text(title, 12, TEXT, true));
            TextView detail = text(connectionText(state, lastLocationAt), 11,
                    "connected".equals(state) ? SUCCESS : ("waiting".equals(state) ? MUTED : WARNING), false);
            detail.setPadding(0, dp(3), 0, 0);
            row.addView(detail);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) p.topMargin = dp(7);
            participantList.addView(row, p);
        }
    }

    private String connectionText(String state, String lastLocationAt) {
        String age = ageText(lastLocationAt);
        if ("connected".equals(state)) return "연결됨" + (age.isEmpty() ? "" : " · 위치 " + age);
        if ("disconnected".equals(state)) return "연결 끊김 · 마지막 위치" + (age.isEmpty() ? "" : " " + age);
        if ("location_stale".equals(state)) return "위치 갱신 끊김 · 마지막 위치" + (age.isEmpty() ? "" : " " + age);
        return "첫 위치를 기다리는 중";
    }

    private void refreshActiveSnapshot() {
        if (!activeScreen) return;
        LocationSharingApi.snapshot(this, new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                runOnUiThread(() -> applySnapshot(data));
            }

            @Override public void onFailure(String message) {
                runOnUiThread(() -> {
                    if (activeNetworkHint != null) {
                        activeNetworkHint.setText("네트워크 연결을 확인하는 중입니다. 지도에는 마지막으로 받은 위치를 유지합니다.");
                        activeNetworkHint.setTextColor(WARNING);
                    }
                });
            }
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
            return;
        }
        if (!askedActivePermission) {
            askedActivePermission = true;
            requestSharePermissions();
        }
    }

    private void showNicknameDialog() {
        JSONObject dummy = new JSONObject();
        final EditText input = input("새 닉네임", InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        new AlertDialog.Builder(this)
                .setTitle("위치 공유 닉네임")
                .setMessage("이 방에서 표시할 닉네임만 변경합니다.")
                .setView(input)
                .setNegativeButton("취소", null)
                .setPositiveButton("변경", (d, w) -> {
                    String nickname = input.getText().toString().trim();
                    if (nickname.isEmpty()) { toast("닉네임을 입력해 주세요."); return; }
                    LocationSharingApi.updateNickname(this, nickname, new LocationSharingApi.JsonCallback() {
                        @Override public void onSuccess(JSONObject data) {
                            runOnUiThread(() -> {
                                toast("닉네임을 변경했습니다.");
                                applySnapshot(data);
                            });
                        }
                        @Override public void onFailure(String message) { runOnUiThread(() -> toast(message)); }
                    });
                })
                .show();
    }

    private void leaveRoom() {
        LocationSharingApi.leave(this, new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                runOnUiThread(() -> {
                    LocationSharingService.stop(LocationSharingActivity.this);
                    toast("위치 공유를 종료했습니다.");
                    showEntry();
                });
            }

            @Override public void onFailure(String message) {
                runOnUiThread(() -> toast(message));
            }
        });
    }

    private void requestSharePermissions() {
        List<String> permissions = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (permissions.isEmpty()) {
            if (pendingSubmitAfterPermission) {
                pendingSubmitAfterPermission = false;
                submit();
            } else if (activeScreen) {
                LocationSharingService.start(this);
            }
            return;
        }
        requestPermissions(permissions.toArray(new String[0]), REQ_SHARE_PERMISSIONS);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_SHARE_PERMISSIONS) return;
        if (!hasLocationPermission()) {
            pendingSubmitAfterPermission = false;
            toast("화면이 꺼져도 위치를 공유하려면 위치 권한이 필요합니다.");
            return;
        }
        if (pendingSubmitAfterPermission) {
            pendingSubmitAfterPermission = false;
            submit();
        } else if (activeScreen) {
            LocationSharingService.start(this);
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private JSONObject findSelf(JSONArray members) {
        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.optJSONObject(i);
            if (member != null && member.optBoolean("is_self", false)) return member;
        }
        return null;
    }

    private ScrollView shell() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        rootPage = new LinearLayout(this);
        rootPage.setOrientation(LinearLayout.VERTICAL);
        rootPage.setPadding(dp(18), dp(20), dp(18), dp(38));
        scroll.addView(rootPage, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void addHeader(String title, String subtitle) {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 34, PRIMARY2, true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(48)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(title, 24, TEXT, true));
        titles.addView(text(subtitle, 11, MUTED, false));
        top.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        rootPage.addView(top);
        spacer(rootPage, 18);
    }

    private TextView label(String value) {
        TextView v = text(value, 12, MUTED, true);
        v.setPadding(0, dp(4), 0, dp(6));
        return v;
    }

    private EditText input(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFF68758D);
        e.setTextColor(TEXT);
        e.setTextSize(14);
        e.setInputType(inputType);
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setBackground(round(CARD2, 14, 1, 0xFF33435F));
        return e;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values);
        spinner.setAdapter(adapter);
        spinner.setBackground(round(CARD2, 14, 1, 0xFF33435F));
        spinner.setPadding(dp(12), 0, dp(8), 0);
        return spinner;
    }

    private TextView modeChip(String value, boolean selected) {
        TextView v = text(value, 13, selected ? Color.WHITE : MUTED, true);
        v.setGravity(Gravity.CENTER);
        styleMode(v, selected);
        return v;
    }

    private void styleMode(TextView v, boolean selected) {
        v.setTextColor(selected ? Color.WHITE : MUTED);
        v.setBackground(round(selected ? PRIMARY : CARD2, 14, 1, selected ? PRIMARY : 0xFF33435F));
    }

    private Button primaryButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackground(round(PRIMARY, 15, 0, 0));
        return b;
    }

    private Button softButton(String value) {
        Button b = primaryButton(value);
        b.setTextColor(PRIMARY2);
        b.setBackground(round(CARD, 15, 1, 0xFF33435F));
        return b;
    }

    private Button dangerButton(String value) {
        Button b = primaryButton(value);
        b.setTextColor(0xFFFFB1BE);
        b.setBackground(round(0xFF351B2A, 15, 1, 0xFF6A3047));
        return b;
    }

    private Button smallButton(String value) {
        Button b = primaryButton(value);
        b.setTextSize(11);
        return b;
    }

    private Button tinyButton(String value) {
        Button b = softButton(value);
        b.setTextSize(10);
        b.setPadding(dp(5), 0, dp(5), 0);
        return b;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(round(CARD, 18, 0, 0));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(12);
        return p;
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
            long seconds = Math.max(0, (Instant.parse(iso).toEpochMilli() - System.currentTimeMillis()) / 1000L);
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            if (hours > 0) return hours + "시간 " + minutes + "분";
            return minutes + "분";
        } catch (DateTimeParseException e) {
            return "-";
        }
    }

    private String ageText(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            long seconds = Math.max(0, (System.currentTimeMillis() - Instant.parse(iso).toEpochMilli()) / 1000L);
            if (seconds < 60) return "방금";
            long minutes = seconds / 60;
            if (minutes < 60) return minutes + "분 전";
            long hours = minutes / 60;
            return hours + "시간 전";
        } catch (DateTimeParseException e) {
            return "";
        }
    }

    private String intervalLabel(int seconds) {
        if (seconds < 60) return seconds + "초";
        return (seconds / 60) + "분";
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
