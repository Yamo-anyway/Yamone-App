#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SERVICE = ROOT / 'app/src/main/java/com/yamo/snorelab/WalkingRecorderService.java'
BRIDGE = ROOT / 'app/src/main/java/com/yamo/snorelab/MovementBridge.java'
LIVE_JS = ROOT / 'app/src/main/assets/yamone-v23/v02508-real-move.js'
AUTO_JS = ROOT / 'app/src/main/assets/yamone-v23/v02516-auto-detect.js'


def replace_once(path: Path, old: str, new: str, label: str):
    s = path.read_text(encoding='utf-8')
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'v0.32.00 patch target not found: {label} ({path})')
    path.write_text(s.replace(old, new, 1), encoding='utf-8')

replace_once(SERVICE,
'''    public static final String KEY_LAST_ACCEPTED_FIX_MS = "last_accepted_fix_ms";''',
'''    public static final String KEY_LAST_ACCEPTED_FIX_MS = "last_accepted_fix_ms";
    public static final String KEY_GPS_SIGNAL_STATE = "gps_signal_state";
    public static final String KEY_GPS_GAP_AGE_MS = "gps_gap_age_ms";
    public static final String KEY_LONGEST_GPS_GAP_MS = "longest_gps_gap_ms";''',
'gps runtime keys')

replace_once(SERVICE,
'''    private int gpsGapResets;
    private int gpsShadowSegments;
    private double gpsShadowDistanceM;''',
'''    private int gpsGapResets;
    private long longestGpsGapMs;
    private int gpsShadowSegments;
    private double gpsShadowDistanceM;''',
'longest gap field')

replace_once(SERVICE,
'''        gpsGapResets = 0;
        gpsShadowSegments = 0;''',
'''        gpsGapResets = 0;
        longestGpsGapMs = 0L;
        gpsShadowSegments = 0;''',
'initialize longest gap')

replace_once(SERVICE,
'''        if (dtMs > GPS_GAP_RESET_MS) {
            gpsGapResets++;
            boolean bridged = bridgeGpsShadow(loc, now, dtMs);''',
'''        if (dtMs > GPS_GAP_RESET_MS) {
            gpsGapResets++;
            longestGpsGapMs = Math.max(longestGpsGapMs, dtMs);
            boolean bridged = bridgeGpsShadow(loc, now, dtMs);''',
'record longest gap')

replace_once(SERVICE,
'''        boolean movingBefore = lastAcceptedSpeedMps >= minSpeed * 0.75f;
        boolean movingAfter = !Float.isNaN(reportedMps)
                && reportedMps >= minSpeed * 0.75f && reportedMps <= maxSpeed * 1.10f;
        float minimumBridgeDistance = Math.max(isCycling() ? 15f : 8f, combinedAccuracy * 0.50f);

        // Bridge only plausible movement. A straight line deliberately underestimates curved tunnels,
        // but avoids inventing distance without a paid/external road-matching service.
        if (d < minimumBridgeDistance) return false;
        if (averageMps < minSpeed * 0.45f || averageMps > maxSpeed * 1.05f) return false;
        if (!(movingBefore || movingAfter || recentStep)) return false;''',
'''        boolean movingBefore = lastAcceptedSpeedMps >= minSpeed * 0.75f;
        boolean movingAfter = !Float.isNaN(reportedMps)
                && reportedMps >= minSpeed * 0.75f && reportedMps <= maxSpeed * 1.10f;
        float minimumBridgeDistance = Math.max(isCycling() ? 15f : 8f, combinedAccuracy * 0.50f);

        // Bridge only plausible movement. A straight line deliberately underestimates curved tunnels,
        // but avoids inventing distance without a paid/external road-matching service.
        if (d < minimumBridgeDistance) return false;
        if (averageMps < minSpeed * 0.45f || averageMps > maxSpeed * 1.05f) return false;
        boolean strongContinuity = movingBefore && movingAfter;
        // Cycling is easy to confuse with vehicle movement, so require movement on both sides of the gap.
        // Walking/running may additionally use a recent step as evidence that activity continued.
        if (isCycling()) {
            if (!strongContinuity) return false;
        } else if (!(strongContinuity || recentStep)) {
            return false;
        }''',
'stronger GPS shadow continuity')

replace_once(SERVICE,
'''                .putLong(KEY_PERSISTED_AT_MS, System.currentTimeMillis())
                .putLong(KEY_LAST_ACCEPTED_FIX_MS, lastAcceptedFixWallMs)
                .putString(KEY_SPLITS_JSON, WalkingStore.longListToJson(splitsMs).toString())''',
'''                .putLong(KEY_PERSISTED_AT_MS, System.currentTimeMillis())
                .putLong(KEY_LAST_ACCEPTED_FIX_MS, lastAcceptedFixWallMs)
                .putString(KEY_GPS_SIGNAL_STATE, gpsSignalState())
                .putLong(KEY_GPS_GAP_AGE_MS, gpsGapAgeMs())
                .putLong(KEY_LONGEST_GPS_GAP_MS, longestGpsGapMs)
                .putString(KEY_SPLITS_JSON, WalkingStore.longListToJson(splitsMs).toString())''',
'persist GPS gap state')

replace_once(SERVICE,
'''    private void writeMeta(String status, long endMs) {''',
'''    private String gpsSignalState() {
        if (paused) return "paused";
        if (lastAcceptedFixWallMs <= 0L) return "waiting";
        long age = Math.max(0L, System.currentTimeMillis() - lastAcceptedFixWallMs);
        return age > GPS_GAP_RESET_MS ? "gap" : "ok";
    }

    private long gpsGapAgeMs() {
        if (lastAcceptedFixWallMs <= 0L) return -1L;
        return Math.max(0L, System.currentTimeMillis() - lastAcceptedFixWallMs);
    }

    private void writeMeta(String status, long endMs) {''',
'gps signal helper')

replace_once(SERVICE,
'''            m.put("gpsFilter", "local_" + activityType + "_v7_segmented");
            m.put("rejectedGpsPoints", rejectedGpsPoints);
            m.put("gpsGapResets", gpsGapResets);''',
'''            m.put("gpsFilter", "local_" + activityType + "_v8_gap_guarded");
            m.put("rejectedGpsPoints", rejectedGpsPoints);
            m.put("gpsGapResets", gpsGapResets);
            m.put("longestGpsGapMs", longestGpsGapMs);
            m.put("gpsShadowPolicy", "strong_continuity_or_recent_steps_v8");''',
'GPS metadata version')

replace_once(SERVICE,
'''        lastAcceptedFixWallMs = runtime.getLong(KEY_LAST_ACCEPTED_FIX_MS, 0L);
        goalDistanceM = runtime.getLong(KEY_GOAL_DISTANCE_M, 0L);''',
'''        lastAcceptedFixWallMs = runtime.getLong(KEY_LAST_ACCEPTED_FIX_MS, 0L);
        longestGpsGapMs = runtime.getLong(KEY_LONGEST_GPS_GAP_MS, 0L);
        goalDistanceM = runtime.getLong(KEY_GOAL_DISTANCE_M, 0L);''',
'restore longest gap')

replace_once(BRIDGE,
'''            out.put("lastFixMs", lastFix);
            out.put("fixAgeMs", lastFix <= 0 ? -1 : Math.max(0L, System.currentTimeMillis() - lastFix));
            out.put("goalState", runtime.getString(WalkingRecorderService.KEY_GOAL_STATE, "ACTIVE"));''',
'''            out.put("lastFixMs", lastFix);
            out.put("fixAgeMs", lastFix <= 0 ? -1 : Math.max(0L, System.currentTimeMillis() - lastFix));
            out.put("gpsSignalState", runtime.getString(WalkingRecorderService.KEY_GPS_SIGNAL_STATE, "waiting"));
            out.put("gpsGapAgeMs", runtime.getLong(WalkingRecorderService.KEY_GPS_GAP_AGE_MS, -1L));
            out.put("longestGpsGapMs", runtime.getLong(WalkingRecorderService.KEY_LONGEST_GPS_GAP_MS, 0L));
            out.put("goalState", runtime.getString(WalkingRecorderService.KEY_GOAL_STATE, "ACTIVE"));''',
'expose GPS gap state')

replace_once(LIVE_JS,
'''  function gpsState(s){
    const age=n(s&&s.fixAgeMs,-1),acc=s&&s.accuracyM==null?NaN:Number(s.accuracyM);
    if(age<0||age>12000)return {text:'GPS 미확인',tone:'lost'};''',
'''  function gpsState(s){
    const age=n(s&&s.fixAgeMs,-1),acc=s&&s.accuracyM==null?NaN:Number(s.accuracyM),signal=String(s&&s.gpsSignalState||'');
    if(signal==='paused')return {text:'GPS 대기 · 일시정지',tone:'fair'};
    if(signal==='gap'||age>12000)return {text:'GPS 미확인 · 기록 계속',tone:'lost'};
    if(signal==='waiting'||age<0)return {text:'GPS 수신 대기',tone:'lost'};''',
'live GPS gap label')

replace_once(AUTO_JS,
'''  function gpsLabel(s){
    if(!s)return {text:'GPS 상태: 미확인',tone:'lost'};
    const age=Number(s.fixAgeMs),acc=s.accuracyM==null?NaN:Number(s.accuracyM);
    if(!Number.isFinite(age)||age<0||age>12000)return {text:'GPS 상태: 미확인',tone:'lost'};''',
'''  function gpsLabel(s){
    if(!s)return {text:'GPS 상태: 미확인',tone:'lost'};
    const age=Number(s.fixAgeMs),acc=s.accuracyM==null?NaN:Number(s.accuracyM),signal=String(s.gpsSignalState||'');
    if(signal==='paused')return {text:'GPS 상태: 대기 · 일시정지',tone:'fair'};
    if(signal==='gap'||(Number.isFinite(age)&&age>12000))return {text:'GPS 상태: 미확인 · 활동 기록 계속',tone:'lost'};
    if(signal==='waiting'||!Number.isFinite(age)||age<0)return {text:'GPS 상태: 수신 대기',tone:'lost'};''',
'inline GPS gap label')

print('Applied v0.32.00 guarded GPS gap/tunnel behavior and explicit gap state.')
