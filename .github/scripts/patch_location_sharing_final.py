from pathlib import Path
ROOT = Path('app/src/main/java/com/yamo/snorelab')

def read(name): return (ROOT / name).read_text()
def write(name, s): (ROOT / name).write_text(s)
def rep(s, old, new, label):
    if old not in s:
        raise SystemExit('missing ' + label)
    return s.replace(old, new, 1)

# LocationSharingService: retry failed location uploads within one minute, while
# respecting the server's exact next_allowed_at when an upload is rate-limited.
s = read('LocationSharingService.java')
s = rep(s,
'''    private long lastReportAttemptAt;

    private final Runnable reporter''',
'''    private long lastReportAttemptAt;
    private final Runnable locationRetry = this::reportLatestIfNew;

    private final Runnable reporter''', 'location retry runnable')

old = '''                    @Override public void onSuccess(JSONObject data) {
                        handler.post(() -> {
                            reportInFlight = false;
                            lastReportedLocationReceivedAt = Math.max(lastReportedLocationReceivedAt, candidateReceipt);
                            if (wasInitialFix) {
                                initialFixPending = false;
                                LocationSharingApi.invalidateSnapshot();
                                // Fast location updates are only used until the first successful upload.
                                startLocationUpdates();
                            }
                            long serverUntil = parseInstant(data.optString("share_until", ""));
                            if (serverUntil > 0) shareUntilMs = serverUntil;
                            String shareUntil = shareUntilMs > 0 ? Instant.ofEpochMilli(shareUntilMs).toString() : "";
                            LocationSharingStateStore.update(LocationSharingService.this, roomName, shareUntil, intervalSeconds, memberCount);
                            updateNotification("위치 공유 중 · " + intervalLabel(intervalSeconds) + " 간격");
                        });
                    }

                    @Override public void onFailure(String message) {
                        handler.post(() -> {
                            reportInFlight = false;
                            String lower = message == null ? "" : message.toLowerCase(Locale.KOREAN);
                            if (lower.contains("참여 중인 위치 공유 방이 없습니다")) {
                                LocationSharingStateStore.clear(LocationSharingService.this);
                                LocationStatusAlert.clear(LocationSharingService.this);
                                cancelWarning();
                                stopSelf();
                            } else {
                                updateNotification("네트워크 연결 대기 중 · 위치 공유 유지");
                            }
                        });
                    }'''
new = '''                    @Override public void onSuccess(JSONObject data) {
                        handler.post(() -> {
                            reportInFlight = false;
                            handler.removeCallbacks(locationRetry);
                            boolean skipped = data.optBoolean("skipped", false);
                            if (skipped) {
                                long nextAllowed = parseInstant(data.optString("next_allowed_at", ""));
                                long delay = nextAllowed > System.currentTimeMillis()
                                        ? Math.max(1_000L, nextAllowed - System.currentTimeMillis() + 750L)
                                        : MIN_NETWORK_INTERVAL_MS;
                                handler.postDelayed(locationRetry, delay);
                            } else {
                                lastReportedLocationReceivedAt = Math.max(lastReportedLocationReceivedAt, candidateReceipt);
                                if (wasInitialFix) {
                                    initialFixPending = false;
                                    LocationSharingApi.invalidateSnapshot();
                                    // Fast location updates are only used until the first successful upload.
                                    startLocationUpdates();
                                }
                            }
                            long serverUntil = parseInstant(data.optString("share_until", ""));
                            if (serverUntil > 0) shareUntilMs = serverUntil;
                            String shareUntil = shareUntilMs > 0 ? Instant.ofEpochMilli(shareUntilMs).toString() : "";
                            LocationSharingStateStore.update(LocationSharingService.this, roomName, shareUntil, intervalSeconds, memberCount);
                            updateNotification("위치 공유 중 · " + intervalLabel(intervalSeconds) + " 간격");
                        });
                    }

                    @Override public void onFailure(String message) {
                        handler.post(() -> {
                            reportInFlight = false;
                            String lower = message == null ? "" : message.toLowerCase(Locale.KOREAN);
                            if (lower.contains("참여 중인 위치 공유 방이 없습니다")) {
                                LocationSharingStateStore.clear(LocationSharingService.this);
                                LocationStatusAlert.clear(LocationSharingService.this);
                                cancelWarning();
                                stopSelf();
                            } else {
                                updateNotification("네트워크 연결 대기 중 · 위치 공유 유지");
                                handler.removeCallbacks(locationRetry);
                                handler.postDelayed(locationRetry, MIN_NETWORK_INTERVAL_MS);
                            }
                        });
                    }'''
s = rep(s, old, new, 'location report retry handling')
write('LocationSharingService.java', s)

# LocationSharingActivity: make local-alert permission state explicit instead of
# implying that help/emergency will always create a device alert.
s = read('LocationSharingActivity.java')
s = rep(s,
'''        activeStatusHint = text("도움 필요·긴급 상태는 다른 참여자의 다음 확인 시 기기 알림으로 알려줘요.", 11, MUTED, false);''',
'''        activeStatusHint = text("", 11, MUTED, false);''', 'empty dynamic status hint')
s = rep(s,
'''        currentSelfStatus = self == null ? "normal" : self.optString("user_status", "normal");
        styleSelfStatus();''',
'''        currentSelfStatus = self == null ? "normal" : self.optString("user_status", "normal");
        styleSelfStatus();
        refreshStatusAlertHint();''', 'refresh status hint from snapshot')
s = rep(s,
'''                    if (activeStatusHint != null) {
                        activeStatusHint.setText("도움 필요·긴급 상태는 다른 참여자의 다음 확인 시 기기 알림으로 알려줘요.");
                        activeStatusHint.setTextColor(MUTED);
                    }''',
'''                    refreshStatusAlertHint();''', 'status hint after mutation')
insert = '''    private void styleSelfStatus() {'''
helper = '''    private void refreshStatusAlertHint() {
        if (activeStatusHint == null) return;
        if (hasNotificationPermission()) {
            activeStatusHint.setText("도움 필요·긴급 상태는 다른 참여자의 다음 확인 시 기기 알림으로 알려줘요.");
            activeStatusHint.setTextColor(MUTED);
        } else {
            activeStatusHint.setText("알림 권한이 꺼져 있어요. 상태는 화면에서 확인할 수 있지만 도움·긴급 기기 알림은 표시되지 않아요.");
            activeStatusHint.setTextColor(WARNING);
        }
    }

'''
if insert not in s: raise SystemExit('missing status hint helper insertion')
s = s.replace(insert, helper + insert, 1)
s = rep(s,
'''        if (pendingSubmitAfterPermission) {
            pendingSubmitAfterPermission = false;
            submit();
        } else if (activeScreen) LocationSharingService.start(this);
    }''',
'''        if (pendingSubmitAfterPermission) {
            pendingSubmitAfterPermission = false;
            submit();
        } else if (activeScreen) LocationSharingService.start(this);
        if (activeScreen) refreshStatusAlertHint();
    }''', 'status hint after permission result')
write('LocationSharingActivity.java', s)

# Home active-sharing summary: remove the last old emoji/text-arrow remnants.
s = read('LocationSharingHomeUiEnhancer.java')
s = rep(s, 'TextView title = text(activity, "● 위치 공유 중", 15, primary2(activity), true);',
        'TextView title = text(activity, "위치 공유 중", 15, primary2(activity), true);', 'home sharing title cleanup')
s = rep(s,
'''        TextView arrow = text(activity, "›", 27, primary2(activity), false);
        head.addView(arrow);''',
'''        TextView actionHint = text(activity, "보기", 11, primary2(activity), true);
        actionHint.setGravity(Gravity.CENTER);
        actionHint.setPadding(dp(activity, 9), 0, dp(activity, 9), 0);
        actionHint.setBackground(round(activity, pink(activity) ? 0xFFFFE3EC : 0xFFDFF8F0, 13, 0, 0));
        head.addView(actionHint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 30)));''', 'home sharing arrow cleanup')
s = rep(s,
'''            info.setText("👥 " + count + "명  ·  ⏱ " + remaining + "  ·  위치 " + interval + "분 간격");''',
'''            info.setText("참여 " + count + "명  ·  " + remaining + "  ·  위치 " + interval + "분 간격");''', 'home sharing info emoji cleanup')
write('LocationSharingHomeUiEnhancer.java', s)

# Normal participants need no decorative dot: status badges are only shown when
# the participant has explicitly selected a non-normal state.
s = read('LocationSharingActivity.java')
old = '''            TextView badge = text("normal".equals(userStatus) ? "●" : userStatusShortLabel(userStatus),
                    "normal".equals(userStatus) ? 14 : 10, userStatusColor(userStatus, self, state), true);
            badge.setGravity(Gravity.CENTER);
            if (!"normal".equals(userStatus)) {
                badge.setPadding(dp(8), 0, dp(8), 0);
                badge.setBackground(round(userStatusBackground(userStatus), 12, 1, userStatusColor(userStatus, self, state)));
            }
            row.addView(badge, new LinearLayout.LayoutParams(
                    "normal".equals(userStatus) ? dp(24) : ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));'''
new = '''            if (!"normal".equals(userStatus)) {
                TextView badge = text(userStatusShortLabel(userStatus), 10, userStatusColor(userStatus, self, state), true);
                badge.setGravity(Gravity.CENTER);
                badge.setPadding(dp(8), 0, dp(8), 0);
                badge.setBackground(round(userStatusBackground(userStatus), 12, 1, userStatusColor(userStatus, self, state)));
                row.addView(badge, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));
            }'''
s = rep(s, old, new, 'normal participant dot cleanup')
write('LocationSharingActivity.java', s)

for name, tokens in {
    'LocationSharingService.java': ['locationRetry', 'next_allowed_at', 'postDelayed(locationRetry, MIN_NETWORK_INTERVAL_MS)'],
    'LocationSharingActivity.java': ['refreshStatusAlertHint()', '알림 권한이 꺼져 있어요'],
    'LocationSharingHomeUiEnhancer.java': ['TextView actionHint', '참여 " + count + "명'],
}.items():
    text = read(name)
    for token in tokens:
        if token not in text:
            raise SystemExit(f'guard failed {name}: {token}')
