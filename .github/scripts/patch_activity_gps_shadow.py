from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise RuntimeError(f"patch marker not found: {label}")
    return text.replace(old, new, 1)


def patch_walking() -> None:
    p = Path("app/src/main/java/com/yamo/snorelab/WalkingRecorderService.java")
    s = p.read_text()

    s = replace_once(
        s,
        "    private static final long GPS_GAP_RESET_MS = 12_000L;\n    private static final long RECENT_STEP_WINDOW_MS = 5_000L;\n",
        "    private static final long GPS_GAP_RESET_MS = 10_000L;\n    private static final long GPS_SHADOW_MAX_MS = 10 * 60_000L;\n    private static final long RECENT_STEP_WINDOW_MS = 5_000L;\n",
        "walking constants",
    )
    s = replace_once(
        s,
        "    private int rejectedGpsPoints;\n    private int gpsGapResets;\n    private int stationaryGpsDiscards;\n",
        "    private int rejectedGpsPoints;\n    private int gpsGapResets;\n    private int gpsShadowSegments;\n    private double gpsShadowDistanceM;\n    private long gpsShadowDurationMs;\n    private int stationaryGpsDiscards;\n",
        "walking fields",
    )
    s = replace_once(
        s,
        "        rejectedGpsPoints = 0;\n        gpsGapResets = 0;\n        stationaryGpsDiscards = 0;\n",
        "        rejectedGpsPoints = 0;\n        gpsGapResets = 0;\n        gpsShadowSegments = 0;\n        gpsShadowDistanceM = 0;\n        gpsShadowDurationMs = 0;\n        stationaryGpsDiscards = 0;\n",
        "walking reset",
    )
    s = replace_once(
        s,
        """        if (dtMs > GPS_GAP_RESET_MS) {
            gpsGapResets++;
            currentSpeedKmh = 0f;
            resetAutoPending();
            resetMaxSpeedCandidate();
            rebaseStationaryAnchor(loc, now, true);
            persistRuntime();
            return;
        }
""",
        """        if (dtMs > GPS_GAP_RESET_MS) {
            gpsGapResets++;
            boolean bridged = bridgeGpsShadow(loc, now, dtMs);
            currentSpeedKmh = 0f;
            resetAutoPending();
            resetMaxSpeedCandidate();
            if (!bridged) rebaseStationaryAnchor(loc, now, true);
            persistRuntime();
            checkGoal();
            return;
        }
""",
        "walking gap branch",
    )

    if "private boolean bridgeGpsShadow(Location loc, long now, long dtMs)" not in s:
        marker = "    private void updateAutoMode(float filteredMps, long now) {"
        bridge = """    private boolean bridgeGpsShadow(Location loc, long now, long dtMs) {
        if (lastAccepted == null || dtMs <= GPS_GAP_RESET_MS || dtMs > GPS_SHADOW_MAX_MS) return false;

        float d = lastAccepted.distanceTo(loc);
        float dtSec = dtMs / 1000f;
        float averageMps = d / Math.max(0.001f, dtSec);
        float previousAccuracy = lastAccepted.hasAccuracy() ? lastAccepted.getAccuracy() : 0f;
        float combinedAccuracy = Math.max(previousAccuracy, loc.hasAccuracy() ? loc.getAccuracy() : 0f);
        float minSpeed = minMovingSpeedMps();
        float maxSpeed = maxMovingSpeedMps();
        float reportedMps = loc.hasSpeed() ? Math.max(0f, loc.getSpeed()) : Float.NaN;
        boolean recentStep = !isCycling() && stepAvailable && lastStepDetectedMs > 0
                && System.currentTimeMillis() - lastStepDetectedMs <= RECENT_STEP_WINDOW_MS;
        boolean movingBefore = lastAcceptedSpeedMps >= minSpeed * 0.75f;
        boolean movingAfter = !Float.isNaN(reportedMps)
                && reportedMps >= minSpeed * 0.75f && reportedMps <= maxSpeed * 1.10f;
        float minimumBridgeDistance = Math.max(isCycling() ? 15f : 8f, combinedAccuracy * 0.50f);

        // Bridge only plausible movement. A straight line deliberately underestimates curved tunnels,
        // but avoids inventing distance without a paid/external road-matching service.
        if (d < minimumBridgeDistance) return false;
        if (averageMps < minSpeed * 0.45f || averageMps > maxSpeed * 1.05f) return false;
        if (!(movingBefore || movingAfter || recentStep)) return false;

        distanceM += d;
        movingMs += dtMs;
        gpsShadowSegments++;
        gpsShadowDistanceM += d;
        gpsShadowDurationMs += dtMs;

        if (isWalkRun()) {
            if (\"running\".equals(autoMotionMode)) {
                runningDistanceM += d;
                runningMovingMs += dtMs;
            } else {
                walkingDistanceM += d;
                walkingMovingMs += dtMs;
            }
        }

        while (distanceM >= nextSplitM) {
            long split = Math.max(0, movingMs - lastSplitMovingMs);
            splitsMs.add(split);
            lastSplitMovingMs = movingMs;
            nextSplitM += 1000;
        }

        // Never feed the estimated bridge speed into max-speed confirmation.
        acceptAnchor(loc, now, Math.min(averageMps, maxSpeed), true);
        return true;
    }

"""
        if marker not in s:
            raise RuntimeError("patch marker not found: walking bridge insertion")
        s = s.replace(marker, bridge + marker, 1)

    s = replace_once(
        s,
        "            m.put(\"gpsGapResets\", gpsGapResets);\n            m.put(\"stationaryGpsDiscards\", stationaryGpsDiscards);\n",
        "            m.put(\"gpsGapResets\", gpsGapResets);\n            m.put(\"gpsShadowSegments\", gpsShadowSegments);\n            m.put(\"gpsShadowDistanceM\", Math.round(gpsShadowDistanceM));\n            m.put(\"gpsShadowDurationMs\", gpsShadowDurationMs);\n            m.put(\"stationaryGpsDiscards\", stationaryGpsDiscards);\n",
        "walking meta shadow stats",
    )
    if '"local_" + activityType + "_v6_shadow"' not in s:
        s = s.replace('"local_" + activityType + "_v5"', '"local_" + activityType + "_v6_shadow"', 1)

    p.write_text(s)


def patch_hiking() -> None:
    p = Path("app/src/main/java/com/yamo/snorelab/HikingRecorderService.java")
    s = p.read_text()

    s = replace_once(
        s,
        "    private static final float MAX_SPEED_MPS = 8.5f;\n    private static final float MIN_MOVE_M = 2.0f;\n",
        "    private static final float MAX_SPEED_MPS = 8.5f;\n    private static final float MIN_MOVE_M = 2.0f;\n    private static final long GPS_SHADOW_MIN_MS = 10_000L;\n    private static final long GPS_SHADOW_MAX_MS = 10 * 60_000L;\n",
        "hiking constants",
    )
    s = replace_once(
        s,
        "    private Location lastLocation;\n    private long lastLocationTime;\n    private File sessionDir;\n",
        "    private Location lastLocation;\n    private long lastLocationTime;\n    private float lastSpeedMps;\n    private int gpsShadowSegments;\n    private double gpsShadowDistanceM;\n    private long gpsShadowDurationMs;\n    private File sessionDir;\n",
        "hiking fields",
    )
    s = replace_once(
        s,
        "        lastLocation = null;\n        lastLocationTime = 0L;\n        sessionDir = HikingStore.createSession(this, startMs);\n",
        "        lastLocation = null;\n        lastLocationTime = 0L;\n        lastSpeedMps = 0f;\n        gpsShadowSegments = 0;\n        gpsShadowDistanceM = 0;\n        gpsShadowDurationMs = 0L;\n        sessionDir = HikingStore.createSession(this, startMs);\n",
        "hiking reset",
    )
    s = replace_once(
        s,
        """        if (dtMs > 20_000L) {
            lastLocation = new Location(loc);
            lastLocationTime = now;
            append(loc, now, 0f);
            persist();
            return;
        }
""",
        """        if (dtMs > GPS_SHADOW_MIN_MS) {
            if (bridgeGpsShadow(loc, now, dtMs)) {
                persist();
                return;
            }
            lastLocation = new Location(loc);
            lastLocationTime = now;
            lastSpeedMps = 0f;
            append(loc, now, 0f);
            persist();
            return;
        }
""",
        "hiking gap branch",
    )
    s = replace_once(
        s,
        "        lastLocation = new Location(loc);\n        lastLocationTime = now;\n        append(loc, now, speed <= MAX_SPEED_MPS ? speed : 0f);\n",
        "        lastLocation = new Location(loc);\n        lastLocationTime = now;\n        lastSpeedMps = speed <= MAX_SPEED_MPS ? Math.max(0f, speed) : 0f;\n        append(loc, now, lastSpeedMps);\n",
        "hiking normal speed state",
    )

    if "private boolean bridgeGpsShadow(Location loc, long now, long dtMs)" not in s:
        marker = "    private void append(Location loc, long now, float speedMps) {"
        bridge = """    private boolean bridgeGpsShadow(Location loc, long now, long dtMs) {
        if (lastLocation == null || dtMs <= GPS_SHADOW_MIN_MS || dtMs > GPS_SHADOW_MAX_MS) return false;
        float d = lastLocation.distanceTo(loc);
        float dtSec = dtMs / 1000f;
        float averageMps = d / Math.max(0.001f, dtSec);
        float previousAccuracy = lastLocation.hasAccuracy() ? lastLocation.getAccuracy() : 0f;
        float combinedAccuracy = Math.max(previousAccuracy, loc.hasAccuracy() ? loc.getAccuracy() : 0f);
        float reportedMps = loc.hasSpeed() ? Math.max(0f, loc.getSpeed()) : Float.NaN;
        boolean movingBefore = lastSpeedMps >= 0.30f;
        boolean movingAfter = !Float.isNaN(reportedMps)
                && reportedMps >= 0.30f && reportedMps <= MAX_SPEED_MPS * 1.10f;
        float minimumBridgeDistance = Math.max(8f, combinedAccuracy * 0.50f);

        if (d < minimumBridgeDistance) return false;
        if (averageMps < 0.18f || averageMps > MAX_SPEED_MPS) return false;
        if (!(movingBefore || movingAfter || averageMps >= 0.45f)) return false;

        distanceM += d;
        gpsShadowSegments++;
        gpsShadowDistanceM += d;
        gpsShadowDurationMs += dtMs;
        lastLocation = new Location(loc);
        lastLocationTime = now;
        lastSpeedMps = averageMps;
        append(loc, now, averageMps);
        return true;
    }

"""
        if marker not in s:
            raise RuntimeError("patch marker not found: hiking bridge insertion")
        s = s.replace(marker, bridge + marker, 1)

    s = replace_once(
        s,
        "            m.put(\"locationStorage\", \"local_only\");\n            m.put(\"gpsFilter\", \"local_hiking_v1\");\n",
        "            m.put(\"locationStorage\", \"local_only\");\n            m.put(\"gpsFilter\", \"local_hiking_v2_shadow\");\n            m.put(\"gpsShadowSegments\", gpsShadowSegments);\n            m.put(\"gpsShadowDistanceM\", Math.round(gpsShadowDistanceM));\n            m.put(\"gpsShadowDurationMs\", gpsShadowDurationMs);\n",
        "hiking meta shadow stats",
    )

    p.write_text(s)


patch_walking()
patch_hiking()
print("activity GPS shadow patches applied")
