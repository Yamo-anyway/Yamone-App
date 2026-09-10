from pathlib import Path
import re

activity = Path('app/src/main/java/com/yamo/snorelab/LocationSharingActivity.java')
s = activity.read_text()

old = '''        Button nick = tinyButton("내 닉네임 변경");
        nick.setOnClickListener(v -> showNicknameDialog());
        LinearLayout.LayoutParams nickParams = new LinearLayout.LayoutParams(dp(122), dp(38));
        nickParams.bottomMargin = dp(10);
        pHead.addView(nick, nickParams);
'''
if old not in s:
    raise SystemExit('nickname button block not found')
s = s.replace(old, '')

pattern = re.compile(r'\n    private void showNicknameDialog\(\) \{.*?\n    \}\n\n    private void leaveRoom\(\)', re.S)
if not pattern.search(s):
    raise SystemExit('nickname dialog method not found')
s = pattern.sub('\n    private void leaveRoom()', s, count=1)

old = '''        applySnapshot(initial);
        ensureSharingService();
        scheduleActivePolling();
'''
new = '''        applySnapshot(initial);
        ensureSharingService();
        scheduleInitialLocationRefreshes();
        scheduleActivePolling();
'''
if old not in s:
    raise SystemExit('showActive scheduling block not found')
s = s.replace(old, new, 1)

marker = '''    private void scheduleActivePolling() {
        handler.removeCallbacks(activePoller);
        if (activeScreen) handler.postDelayed(activePoller, ACTIVE_POLL_MS);
    }
'''
insert = '''    private void scheduleInitialLocationRefreshes() {
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

''' + marker
if marker not in s:
    raise SystemExit('scheduleActivePolling marker not found')
s = s.replace(marker, insert, 1)
activity.write_text(s)

api = Path('app/src/main/java/com/yamo/snorelab/LocationSharingApi.java')
s = api.read_text()
pattern = re.compile(r'\n    public static void updateNickname\(Context context, String nickname, JsonCallback callback\) \{.*?\n    \}\n', re.S)
if not pattern.search(s):
    raise SystemExit('updateNickname API method not found')
s = pattern.sub('\n', s, count=1)

marker = '''    public static void snapshot(Context context, JsonCallback callback) {
        synchronized (SNAPSHOT_LOCK) {
            if (cachedSnapshot != null && System.currentTimeMillis() - cachedSnapshotAt < SNAPSHOT_CACHE_MS) {
                callback.onSuccess(copy(cachedSnapshot));
                return;
            }
        }
        run(() -> {
            String body = SupabaseAnonymousRpcClient.rpc(context, "location_room_snapshot", new JSONObject());
            JSONObject data = body.isEmpty() ? new JSONObject() : new JSONObject(body);
            cacheIfSnapshot(data);
            callback.onSuccess(data);
        }, callback::onFailure);
    }
'''
addition = marker + '''
    /** Fresh snapshot used only during the initial location-acquisition window. */
    public static void snapshotFresh(Context context, JsonCallback callback) {
        run(() -> {
            String body = SupabaseAnonymousRpcClient.rpc(context, "location_room_snapshot", new JSONObject());
            JSONObject data = body.isEmpty() ? new JSONObject() : new JSONObject(body);
            cacheIfSnapshot(data);
            callback.onSuccess(data);
        }, callback::onFailure);
    }
'''
if marker not in s:
    raise SystemExit('snapshot method marker not found')
s = s.replace(marker, addition, 1)
api.write_text(s)

service = Path('app/src/main/java/com/yamo/snorelab/LocationSharingService.java')
s = service.read_text()
s = s.replace(
    '    private static final long MIN_NETWORK_INTERVAL_MS = 60_000L;\n',
    '    private static final long MIN_NETWORK_INTERVAL_MS = 60_000L;\n'
    '    private static final long INITIAL_FIX_MIN_TIME_MS = 1_000L;\n'
    '    private static final long INITIAL_REPORT_RETRY_MS = 3_000L;\n'
    '    private static final long RECENT_LAST_KNOWN_MS = 30_000L;\n'
    '    private static final float RECENT_LAST_KNOWN_MAX_ACCURACY_M = 100f;\n', 1)
s = s.replace(
    '    private boolean warningShown;\n    private boolean leaveInFlight;\n',
    '    private boolean warningShown;\n    private boolean leaveInFlight;\n'
    '    private boolean initialFixPending;\n    private long lastReportAttemptAt;\n', 1)

old = '''        roomName = data.optString("room_name", "위치 공유 방");
        memberCount = members == null ? 0 : members.length();
        intervalSeconds = clampInterval(self.optInt("update_interval_seconds", 60));
'''
new = '''        roomName = data.optString("room_name", "위치 공유 방");
        memberCount = members == null ? 0 : members.length();
        intervalSeconds = clampInterval(self.optInt("update_interval_seconds", 60));
        initialFixPending = self.optString("last_location_at", "").trim().isEmpty();
'''
if old not in s:
    raise SystemExit('configuration block not found')
s = s.replace(old, new, 1)

old = '''        locationListener = this::onLocationChanged;
        long minTimeMs = Math.max(MIN_NETWORK_INTERVAL_MS, intervalSeconds * 1000L);
        try {
'''
new = '''        locationListener = this::onLocationChanged;
        long minTimeMs = initialFixPending
                ? INITIAL_FIX_MIN_TIME_MS
                : Math.max(MIN_NETWORK_INTERVAL_MS, intervalSeconds * 1000L);
        if (initialFixPending) tryRecentLastKnownLocation();
        try {
'''
if old not in s:
    raise SystemExit('location update interval block not found')
s = s.replace(old, new, 1)

marker = '    private void onLocationChanged(Location location) {\n'
helper = '''    private void tryRecentLastKnownLocation() {
        Location best = null;
        String[] providers = {LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER};
        for (String provider : providers) {
            try {
                if (!locationManager.isProviderEnabled(provider)) continue;
                Location candidate = locationManager.getLastKnownLocation(provider);
                if (!isRecentUsable(candidate)) continue;
                if (best == null || candidate.getTime() > best.getTime()
                        || (candidate.getTime() == best.getTime() && candidate.hasAccuracy()
                        && (!best.hasAccuracy() || candidate.getAccuracy() < best.getAccuracy()))) {
                    best = candidate;
                }
            } catch (SecurityException | IllegalArgumentException ignored) { }
        }
        if (best != null) onLocationChanged(best);
    }

    private boolean isRecentUsable(Location location) {
        if (location == null || location.getTime() <= 0) return false;
        long age = Math.abs(System.currentTimeMillis() - location.getTime());
        if (age > RECENT_LAST_KNOWN_MS) return false;
        return !location.hasAccuracy() || location.getAccuracy() <= RECENT_LAST_KNOWN_MAX_ACCURACY_M;
    }

''' + marker
if marker not in s:
    raise SystemExit('onLocationChanged marker not found')
s = s.replace(marker, helper, 1)

old = '''        if (!configured || reportInFlight || latestLocation == null) return;
        if (latestLocationReceivedAt <= lastReportedLocationReceivedAt) return;
'''
new = '''        if (!configured || reportInFlight || latestLocation == null) return;
        if (latestLocationReceivedAt <= lastReportedLocationReceivedAt) return;
        long now = System.currentTimeMillis();
        if (lastReportedLocationReceivedAt == 0 && now - lastReportAttemptAt < INITIAL_REPORT_RETRY_MS) return;
'''
if old not in s:
    raise SystemExit('report guard block not found')
s = s.replace(old, new, 1)

old = '''        reportInFlight = true;
        LocationSharingApi.report(
'''
new = '''        reportInFlight = true;
        lastReportAttemptAt = System.currentTimeMillis();
        final boolean wasInitialFix = initialFixPending || lastReportedLocationReceivedAt == 0;
        LocationSharingApi.report(
'''
if old not in s:
    raise SystemExit('report start block not found')
s = s.replace(old, new, 1)

old = '''                            lastReportedLocationReceivedAt = Math.max(lastReportedLocationReceivedAt, candidateReceipt);
                            long serverUntil = parseInstant(data.optString("share_until", ""));
'''
new = '''                            lastReportedLocationReceivedAt = Math.max(lastReportedLocationReceivedAt, candidateReceipt);
                            if (wasInitialFix) {
                                initialFixPending = false;
                                LocationSharingApi.invalidateSnapshot();
                                // Fast location updates are only used until the first successful upload.
                                startLocationUpdates();
                            }
                            long serverUntil = parseInstant(data.optString("share_until", ""));
'''
if old not in s:
    raise SystemExit('report success block not found')
s = s.replace(old, new, 1)
service.write_text(s)
