from pathlib import Path

ROOT = Path('app/src/main/java/com/yamo/snorelab')

def read(name): return (ROOT / name).read_text()
def write(name, text): (ROOT / name).write_text(text)
def rep(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing {label}: {old[:180]}')
    return text.replace(old, new, 1)

# LocationSharingApi: predefined self status mutation.
s = read('LocationSharingApi.java')
s = rep(s,
'''    public static void setInterval(Context context, int seconds, JsonCallback callback) {
        JSONObject args = new JSONObject();
        try { args.put("p_update_interval_seconds", seconds); }
        catch (Exception e) { callback.onFailure("공유 간격을 준비하지 못했습니다."); return; }
        rpcMutationAsync(context, "location_set_interval", args, callback);
    }
''',
'''    public static void setInterval(Context context, int seconds, JsonCallback callback) {
        JSONObject args = new JSONObject();
        try { args.put("p_update_interval_seconds", seconds); }
        catch (Exception e) { callback.onFailure("공유 간격을 준비하지 못했습니다."); return; }
        rpcMutationAsync(context, "location_set_interval", args, callback);
    }

    public static void setStatus(Context context, String status, JsonCallback callback) {
        JSONObject args = new JSONObject();
        try { args.put("p_status", status); }
        catch (Exception e) { callback.onFailure("상태값을 준비하지 못했습니다."); return; }
        rpcMutationAsync(context, "location_set_status", args, callback);
    }
''', 'api set status')
s = rep(s,
'''        if (lower.contains("invalid_nickname")) return "닉네임을 1~24자로 입력해 주세요.";
        if (lower.contains("not_in_location_room")) return "현재 참여 중인 위치 공유 방이 없습니다.";''',
'''        if (lower.contains("invalid_nickname")) return "닉네임을 1~24자로 입력해 주세요.";
        if (lower.contains("invalid_user_status")) return "사용할 수 없는 위치공유 상태입니다.";
        if (lower.contains("not_in_location_room")) return "현재 참여 중인 위치 공유 방이 없습니다.";''', 'api friendly status')
write('LocationSharingApi.java', s)

# LocationSharingActivity: status controls and participant status labels.
s = read('LocationSharingActivity.java')
s = rep(s,
'''    private TextView activeNetworkHint;
    private LinearLayout participantList;
    private LocationSharingMapView sharingMap;''',
'''    private TextView activeNetworkHint;
    private LinearLayout participantList;
    private LocationSharingMapView sharingMap;
    private TextView statusNormal;
    private TextView statusContact;
    private TextView statusHelp;
    private TextView statusEmergency;
    private TextView activeStatusHint;
    private String currentSelfStatus = "normal";''', 'activity status fields')

s = rep(s,
'''        status.addView(activeNetworkHint);
        page.addView(status, cardParams());

        sharingMap = new LocationSharingMapView(this);''',
'''        status.addView(activeNetworkHint);
        page.addView(status, cardParams());

        LinearLayout myStatus = card();
        myStatus.addView(text("내 상태", 15, TEXT, true));
        activeStatusHint = text("도움 필요·긴급 상태는 다른 참여자의 다음 확인 시 기기 알림으로 알려줘요.", 11, MUTED, false);
        activeStatusHint.setPadding(0, dp(4), 0, dp(10));
        myStatus.addView(activeStatusHint);

        LinearLayout statusRow1 = new LinearLayout(this);
        statusRow1.setOrientation(LinearLayout.HORIZONTAL);
        statusNormal = statusChoice("normal", "정상");
        statusContact = statusChoice("contact", "연락 요청");
        statusRow1.addView(statusNormal, new LinearLayout.LayoutParams(0, dp(44), 1f));
        LinearLayout.LayoutParams contactParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        contactParams.leftMargin = dp(8);
        statusRow1.addView(statusContact, contactParams);
        myStatus.addView(statusRow1);

        LinearLayout statusRow2 = new LinearLayout(this);
        statusRow2.setOrientation(LinearLayout.HORIZONTAL);
        statusRow2.setPadding(0, dp(8), 0, 0);
        statusHelp = statusChoice("help", "도움 필요");
        statusEmergency = statusChoice("emergency", "긴급");
        statusRow2.addView(statusHelp, new LinearLayout.LayoutParams(0, dp(44), 1f));
        LinearLayout.LayoutParams emergencyParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        emergencyParams.leftMargin = dp(8);
        statusRow2.addView(statusEmergency, emergencyParams);
        myStatus.addView(statusRow2);
        page.addView(myStatus, cardParams());

        sharingMap = new LocationSharingMapView(this);''', 'activity status card')

s = rep(s,
'''        activeInfo.setText(String.format(Locale.KOREAN, "참여 %d명 · 내 위치 %s 간격 공유", members.length(), intervalLabel(interval)));
        if (sharingMap != null) sharingMap.setMembers(members);
        renderParticipants(members);''',
'''        activeInfo.setText(String.format(Locale.KOREAN, "참여 %d명 · 내 위치 %s 간격 공유", members.length(), intervalLabel(interval)));
        currentSelfStatus = self == null ? "normal" : self.optString("user_status", "normal");
        styleSelfStatus();
        if (sharingMap != null) sharingMap.setMembers(members);
        renderParticipants(members);''', 'activity apply self status')

s = rep(s,
'''            String state = member.optString("connection_state", "waiting");
            String lastLocationAt = member.optString("last_location_at", "");''',
'''            String state = member.optString("connection_state", "waiting");
            String userStatus = member.optString("user_status", "normal");
            String statusUpdatedAt = member.optString("status_updated_at", "");
            String lastLocationAt = member.optString("last_location_at", "");''', 'activity participant status fields')

s = rep(s,
'''            avatar.setImageResource(R.drawable.ic_location_person);
            avatar.setColorFilter(self ? PRIMARY2 : ("connected".equals(state) ? SUCCESS : MUTED));''',
'''            avatar.setImageResource(R.drawable.ic_location_person);
            avatar.setColorFilter(userStatusColor(userStatus, self, state));''', 'activity participant avatar status')

s = rep(s,
'''            detail.setPadding(0, dp(2), 0, 0);
            words.addView(detail);
            row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView dot = text("●", 14, "connected".equals(state) ? SUCCESS : MUTED, true);
            row.addView(dot);''',
'''            detail.setPadding(0, dp(2), 0, 0);
            words.addView(detail);
            if (!"normal".equals(userStatus)) {
                String statusAge = ageText(statusUpdatedAt);
                TextView userState = text(userStatusLabel(userStatus)
                        + (statusAge.isEmpty() ? "" : " · " + statusAge), 11, userStatusColor(userStatus, self, state), true);
                userState.setPadding(0, dp(3), 0, 0);
                words.addView(userState);
            }
            row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView badge = text("normal".equals(userStatus) ? "●" : userStatusShortLabel(userStatus),
                    "normal".equals(userStatus) ? 14 : 10, userStatusColor(userStatus, self, state), true);
            badge.setGravity(Gravity.CENTER);
            if (!"normal".equals(userStatus)) {
                badge.setPadding(dp(8), 0, dp(8), 0);
                badge.setBackground(round(userStatusBackground(userStatus), 12, 1, userStatusColor(userStatus, self, state)));
            }
            row.addView(badge, new LinearLayout.LayoutParams(
                    "normal".equals(userStatus) ? dp(24) : ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));''', 'activity participant status rendering')

insert_before = '''    private String connectionText(String state, String lastLocationAt) {'''
helpers = '''    private TextView statusChoice(String key, String label) {
        TextView chip = text(label, 12, TEXT, true);
        chip.setGravity(Gravity.CENTER);
        chip.setOnClickListener(v -> requestStatusChange(key));
        return chip;
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
                    if (activeStatusHint != null) {
                        activeStatusHint.setText("도움 필요·긴급 상태는 다른 참여자의 다음 확인 시 기기 알림으로 알려줘요.");
                        activeStatusHint.setTextColor(MUTED);
                    }
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

    private void styleSelfStatus() {
        styleStatusChoice(statusNormal, "normal");
        styleStatusChoice(statusContact, "contact");
        styleStatusChoice(statusHelp, "help");
        styleStatusChoice(statusEmergency, "emergency");
    }

    private void styleStatusChoice(TextView chip, String status) {
        if (chip == null) return;
        boolean selected = status.equals(currentSelfStatus);
        int color = userStatusColor(status, true, "connected");
        chip.setTextColor(selected && "emergency".equals(status) ? Color.WHITE : (selected ? color : TEXT));
        int fill = selected ? userStatusBackground(status) : CARD2;
        if (selected && "emergency".equals(status)) fill = DANGER_TEXT;
        chip.setBackground(round(fill, 15, 1, selected ? color : BORDER));
    }

    private int userStatusColor(String status, boolean self, String connectionState) {
        if ("emergency".equals(status)) return DANGER_TEXT;
        if ("help".equals(status)) return 0xFFE45A6A;
        if ("contact".equals(status)) return 0xFFD98A20;
        return self ? PRIMARY2 : ("connected".equals(connectionState) ? SUCCESS : MUTED);
    }

    private int userStatusBackground(String status) {
        if ("emergency".equals(status)) return 0xFFFFE4EA;
        if ("help".equals(status)) return 0xFFFFF0F3;
        if ("contact".equals(status)) return 0xFFFFF6E7;
        return CARD2;
    }

    private String userStatusLabel(String status) {
        if ("emergency".equals(status)) return "긴급";
        if ("help".equals(status)) return "도움 필요";
        if ("contact".equals(status)) return "연락 요청";
        return "정상";
    }

    private String userStatusShortLabel(String status) {
        if ("emergency".equals(status)) return "긴급";
        if ("help".equals(status)) return "도움";
        if ("contact".equals(status)) return "연락";
        return "정상";
    }

'''
if insert_before not in s:
    raise SystemExit('missing activity helper insertion')
s = s.replace(insert_before, helpers + insert_before, 1)

s = rep(s,
'''    private void ensureSharingService() {
        if (!activeScreen) return;
        if (hasLocationPermission()) LocationSharingService.start(this);
        else if (!askedActivePermission) {
            askedActivePermission = true;
            requestSharePermissions();
        }
    }''',
'''    private void ensureSharingService() {
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
    }''', 'activity notification permission prompt')

s = rep(s,
'''    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
''',
'''    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasNotificationPermission() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }
''', 'activity notification permission helper')
write('LocationSharingActivity.java', s)

# LocationSharingMapView: urgent/contact status influences markers and marker detail.
s = read('LocationSharingMapView.java')
s = rep(s,
'''            String state = member.optString("connection_state", "waiting");
            LatLng point = new LatLng(lat, lon);''',
'''            String state = member.optString("connection_state", "waiting");
            String userStatus = member.optString("user_status", "normal");
            LatLng point = new LatLng(lat, lon);''', 'map member status')
s = rep(s,
'''            Icon icon = IconFactory.getInstance(getContext()).fromBitmap(markerBitmap(markerText, self, state));
            String snippet = stateLabel(state);''',
'''            Icon icon = IconFactory.getInstance(getContext()).fromBitmap(markerBitmap(markerText, self, state, userStatus));
            String snippet = stateLabel(state) + statusSuffix(userStatus);''', 'map marker call')
s = rep(s,
'''    private Bitmap markerBitmap(String label, boolean self, String state) {''',
'''    private Bitmap markerBitmap(String label, boolean self, String state, String userStatus) {''', 'map marker signature')
s = rep(s,
'''        if (self) {
            background = 0xF2E94778;
            foreground = Color.WHITE;
        } else if ("disconnected".equals(state) || "location_stale".equals(state)) {''',
'''        if ("emergency".equals(userStatus)) {
            background = 0xF2E75B6D;
            foreground = Color.WHITE;
        } else if ("help".equals(userStatus)) {
            background = 0xF8FFF0F3;
            foreground = 0xFFE45A6A;
        } else if ("contact".equals(userStatus)) {
            background = 0xF8FFF6E7;
            foreground = 0xFFD98A20;
        } else if (self) {
            background = 0xF2E94778;
            foreground = Color.WHITE;
        } else if ("disconnected".equals(state) || "location_stale".equals(state)) {''', 'map status palette')
insert_state = '''    private String stateLabel(String state) {'''
status_suffix = '''    private String statusSuffix(String status) {
        if ("emergency".equals(status)) return " · 긴급";
        if ("help".equals(status)) return " · 도움 필요";
        if ("contact".equals(status)) return " · 연락 요청";
        return "";
    }

'''
if insert_state not in s: raise SystemExit('missing map state insertion')
s = s.replace(insert_state, status_suffix + insert_state, 1)
write('LocationSharingMapView.java', s)

# LocationSharingService: background minute refresh discovers urgent states and creates local notifications.
s = read('LocationSharingService.java')
s = rep(s,
'''    private static final long MIN_NETWORK_INTERVAL_MS = 60_000L;
    private static final long INITIAL_FIX_MIN_TIME_MS = 1_000L;''',
'''    private static final long MIN_NETWORK_INTERVAL_MS = 60_000L;
    private static final long STATUS_POLL_MS = 60_000L;
    private static final long INITIAL_FIX_MIN_TIME_MS = 1_000L;''', 'service status interval')
s = rep(s,
'''    private boolean leaveInFlight;
    private boolean initialFixPending;
    private long lastReportAttemptAt;''',
'''    private boolean leaveInFlight;
    private boolean initialFixPending;
    private boolean statusPollInFlight;
    private long lastReportAttemptAt;''', 'service status state')

insert_after_expiry = '''    private final Runnable expiryChecker = new Runnable() {
        @Override public void run() {
            if (!configured) return;
            long remaining = shareUntilMs <= 0 ? Long.MAX_VALUE : shareUntilMs - System.currentTimeMillis();
            if (remaining <= 0) {
                expireAndStop();
                return;
            }
            if (remaining <= WARNING_BEFORE_MS && !warningShown) {
                warningShown = true;
                showExpiryWarning(remaining);
            } else if (remaining > WARNING_BEFORE_MS + 30_000L && warningShown) {
                warningShown = false;
                cancelWarning();
            }
            handler.postDelayed(this, EXPIRY_CHECK_MS);
        }
    };
'''
status_runnable = insert_after_expiry + '''
    private final Runnable statusPoller = new Runnable() {
        @Override public void run() {
            if (!configured) return;
            pollStatusSnapshot();
            handler.postDelayed(this, STATUS_POLL_MS);
        }
    };
'''
s = rep(s, insert_after_expiry, status_runnable, 'service status runnable')

s = rep(s,
'''        if (!data.optBoolean("active", false)) {
            LocationSharingStateStore.clear(this);
            cancelWarning();
            stopSelf();
            return;
        }''',
'''        if (!data.optBoolean("active", false)) {
            LocationSharingStateStore.clear(this);
            LocationStatusAlert.clear(this);
            cancelWarning();
            stopSelf();
            return;
        }''', 'service inactive clear alerts')
s = rep(s,
'''        if (self == null) {
            LocationSharingStateStore.clear(this);
            cancelWarning();
            stopSelf();
            return;
        }''',
'''        if (self == null) {
            LocationSharingStateStore.clear(this);
            LocationStatusAlert.clear(this);
            cancelWarning();
            stopSelf();
            return;
        }''', 'service no self clear alerts')

s = rep(s,
'''        configured = true;
        leaveInFlight = false;
        LocationSharingStateStore.update(this, roomName, shareUntil, intervalSeconds, memberCount);''',
'''        configured = true;
        leaveInFlight = false;
        LocationSharingStateStore.update(this, roomName, shareUntil, intervalSeconds, memberCount);
        LocationStatusAlert.inspect(this, data);''', 'service inspect initial status')

s = rep(s,
'''        handler.removeCallbacks(expiryChecker);
        handler.post(expiryChecker);
    }

    private void startLocationUpdates() {''',
'''        handler.removeCallbacks(expiryChecker);
        handler.post(expiryChecker);
        handler.removeCallbacks(statusPoller);
        handler.postDelayed(statusPoller, STATUS_POLL_MS);
    }

    private void pollStatusSnapshot() {
        if (!configured || statusPollInFlight) return;
        statusPollInFlight = true;
        LocationSharingApi.snapshotFresh(this, new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                handler.post(() -> {
                    statusPollInFlight = false;
                    if (!configured) return;
                    if (!data.optBoolean("active", false)) {
                        LocationSharingStateStore.clear(LocationSharingService.this);
                        LocationStatusAlert.clear(LocationSharingService.this);
                        cancelWarning();
                        stopSelf();
                        return;
                    }
                    JSONArray freshMembers = data.optJSONArray("members");
                    memberCount = freshMembers == null ? memberCount : freshMembers.length();
                    LocationStatusAlert.inspect(LocationSharingService.this, data);
                });
            }
            @Override public void onFailure(String message) {
                handler.post(() -> statusPollInFlight = false);
            }
        });
    }

    private void startLocationUpdates() {''', 'service status poll method')

s = rep(s,
'''        handler.removeCallbacks(reporter);
        handler.removeCallbacks(expiryChecker);
        stopLocationUpdates();''',
'''        handler.removeCallbacks(reporter);
        handler.removeCallbacks(expiryChecker);
        handler.removeCallbacks(statusPoller);
        stopLocationUpdates();''', 'service user stop status poll')
# same block appears for expire; replace second occurrence after first mutation
if '''        handler.removeCallbacks(reporter);
        handler.removeCallbacks(expiryChecker);
        stopLocationUpdates();''' in s:
    s = s.replace('''        handler.removeCallbacks(reporter);
        handler.removeCallbacks(expiryChecker);
        stopLocationUpdates();''', '''        handler.removeCallbacks(reporter);
        handler.removeCallbacks(expiryChecker);
        handler.removeCallbacks(statusPoller);
        stopLocationUpdates();''', 1)

s = rep(s,
'''        LocationSharingStateStore.clear(this);
        postEndedNotification("위치 공유를 종료했습니다.");''',
'''        LocationSharingStateStore.clear(this);
        LocationStatusAlert.clear(this);
        postEndedNotification("위치 공유를 종료했습니다.");''', 'service leave clear status alerts')
s = rep(s,
'''        LocationSharingStateStore.clear(this);
        postEndedNotification("설정한 공유 시간이 끝나 위치 공유가 자동 종료되었습니다.");''',
'''        LocationSharingStateStore.clear(this);
        LocationStatusAlert.clear(this);
        postEndedNotification("설정한 공유 시간이 끝나 위치 공유가 자동 종료되었습니다.");''', 'service expiry clear status alerts')
s = rep(s,
'''    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;''',
'''    private void createChannels() {
        LocationStatusAlert.createChannel(this);
        if (Build.VERSION.SDK_INT < 26) return;''', 'service status alert channel')
write('LocationSharingService.java', s)

# Guards.
checks = {
    'LocationSharingApi.java': ['location_set_status', 'invalid_user_status'],
    'LocationSharingActivity.java': ['내 상태', '도움 필요', 'requestStatusChange', 'hasNotificationPermission'],
    'LocationSharingMapView.java': ['statusSuffix', 'user_status'],
    'LocationSharingService.java': ['STATUS_POLL_MS', 'LocationStatusAlert.inspect', 'snapshotFresh'],
}
for name, tokens in checks.items():
    text = read(name)
    for token in tokens:
        if token not in text:
            raise SystemExit(f'guard failed {name}: {token}')
