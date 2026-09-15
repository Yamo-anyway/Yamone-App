#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TARGET = ROOT / 'app/src/main/assets/yamone-v23'
INDEX = TARGET / 'index.html'
WALK = ROOT / 'app/src/main/java/com/yamo/snorelab/WalkingRecorderService.java'
MOVEMENT = ROOT / 'app/src/main/java/com/yamo/snorelab/MovementBridge.java'
SETTINGS = ROOT / 'app/src/main/java/com/yamo/snorelab/SystemSettingsBridge.java'
BUILD = ROOT / 'app/build.gradle'


def replace_once(path: Path, old: str, new: str, label: str):
    s = path.read_text(encoding='utf-8')
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'v0.36.07 target not found: {label} ({path})')
    path.write_text(s.replace(old, new, 1), encoding='utf-8')


# ---------------------------------------------------------------------------
# Tunnel/GPS-gap split interpolation.
# A bridged tunnel used to assign the entire gap duration to the first 1 km
# boundary crossed. That made the post-tunnel "recent 1 km" value ignore the
# remaining tunnel segment. Interpolate moving time at each crossed boundary so
# the completed split and the current partial split both retain their share.
# ---------------------------------------------------------------------------
s = WALK.read_text(encoding='utf-8')
method_start = s.find('    private boolean bridgeGpsShadow(')
method_end = s.find('    private void updateAutoMode(', method_start)
if method_start < 0 or method_end < 0:
    raise SystemExit('v0.36.07 tunnel bridge method not found')
block = s[method_start:method_end]

old_accumulate = '''        distanceM += d;
        movingMs += dtMs;
        gpsShadowSegments++;'''
new_accumulate = '''        double bridgeStartDistanceM = distanceM;
        long bridgeStartMovingMs = movingMs;
        distanceM += d;
        movingMs += dtMs;
        gpsShadowSegments++;'''
if new_accumulate not in block:
    if old_accumulate not in block:
        raise SystemExit('v0.36.07 tunnel accumulation target not found')
    block = block.replace(old_accumulate, new_accumulate, 1)

old_split = '''        while (distanceM >= nextSplitM) {
            long split = Math.max(0, movingMs - lastSplitMovingMs);
            splitsMs.add(split);
            lastSplitMovingMs = movingMs;
            nextSplitM += 1000;
        }
'''
new_split = '''        while (distanceM >= nextSplitM) {
            double progress = d <= 0f ? 1.0
                    : Math.max(0.0, Math.min(1.0, (nextSplitM - bridgeStartDistanceM) / d));
            long crossingMovingMs = bridgeStartMovingMs + Math.round(dtMs * progress);
            long split = Math.max(0L, crossingMovingMs - lastSplitMovingMs);
            splitsMs.add(split);
            lastSplitMovingMs = crossingMovingMs;
            nextSplitM += 1000;
        }
'''
if new_split not in block:
    if old_split not in block:
        raise SystemExit('v0.36.07 tunnel split target not found')
    block = block.replace(old_split, new_split, 1)

WALK.write_text(s[:method_start] + block + s[method_end:], encoding='utf-8')

# ---------------------------------------------------------------------------
# Persistent developer mode in the native settings bridge.
# ---------------------------------------------------------------------------
replace_once(
    SETTINGS,
    'import android.content.Intent;\n',
    'import android.content.Intent;\nimport android.content.SharedPreferences;\n',
    'settings SharedPreferences import')

replace_once(
    SETTINGS,
    '''public final class SystemSettingsBridge {
    private static final int REQUEST_CORE_PERMISSIONS = 6130;
    private final Activity activity;''',
    '''public final class SystemSettingsBridge {
    private static final int REQUEST_CORE_PERMISSIONS = 6130;
    private static final String INTERNAL_PREFS = "yamone_internal_settings";
    private static final String KEY_DEVELOPER_MODE = "developer_mode";
    private final Activity activity;''',
    'developer-mode constants')

replace_once(
    SETTINGS,
    '''    @JavascriptInterface
    public String getStorageState() {''',
    '''    @JavascriptInterface
    public boolean isDeveloperMode() {
        return isDeveloperMode(activity);
    }

    @JavascriptInterface
    public boolean setDeveloperMode(boolean enabled) {
        activity.getSharedPreferences(INTERNAL_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_DEVELOPER_MODE, enabled).apply();
        return enabled;
    }

    static boolean isDeveloperMode(Context context) {
        return context != null && context.getSharedPreferences(INTERNAL_PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_DEVELOPER_MODE, false);
    }

    @JavascriptInterface
    public String getStorageState() {''',
    'developer-mode bridge methods')

# The manual combined walk/run recorder is experimental. Even if stale UI or a
# JavaScript call asks for it, normal-user mode must not start it.
replace_once(
    MOVEMENT,
    '''    public String start(String requestedType) {
        if (!hasLocationPermission()) return result(false, "location_permission_required").toString();''',
    '''    public String start(String requestedType) {
        boolean wantsWalkRun = "auto".equals(requestedType) || "walkrun".equals(requestedType);
        if (wantsWalkRun && !SystemSettingsBridge.isDeveloperMode(activity)) {
            return result(false, "developer_mode_required").toString();
        }
        if (!hasLocationPermission()) return result(false, "location_permission_required").toString();''',
    'manual walkrun native gate')

# ---------------------------------------------------------------------------
# UI overlay: hide manual auto-switch in normal mode, 7-tap version unlock in
# App Info, and an explicit way back to normal-user mode.
# ---------------------------------------------------------------------------
for name in ['v03607-developer-mode.css', 'v03607-developer-mode.js', 'v03607-version.js']:
    src = ROOT / 'design-preview' / name
    if not src.is_file():
        raise SystemExit(f'v0.36.07 design file missing: {src}')
    (TARGET / name).write_bytes(src.read_bytes())

s = INDEX.read_text(encoding='utf-8')
if 'v03607-developer-mode.css' not in s:
    if '</head>' not in s:
        raise SystemExit('v0.36.07 index head missing')
    s = s.replace('</head>', '  <link rel="stylesheet" href="v03607-developer-mode.css">\n</head>', 1)

s = s.replace('  <script src="v03606-version.js"></script>\n', '')
s = s.replace('<script src="v03606-version.js"></script>\n', '')
if 'v03607-developer-mode.js' not in s:
    if '</body>' not in s:
        raise SystemExit('v0.36.07 index body missing')
    s = s.replace('</body>', '  <script src="v03607-developer-mode.js"></script>\n  <script src="v03607-version.js"></script>\n</body>', 1)
INDEX.write_text(s, encoding='utf-8')

replace_once(
    BUILD,
    "        versionCode 100\n        versionName '0.36.06'",
    "        versionCode 101\n        versionName '0.36.07'",
    'android version')

print('Applied v0.36.07 tunnel recent-1km interpolation and developer-mode gate.')
