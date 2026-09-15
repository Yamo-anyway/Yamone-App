#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TARGET = ROOT / 'app/src/main/assets/yamone-v23'
INDEX = TARGET / 'index.html'
BUILD = ROOT / 'app/build.gradle'
MAIN = ROOT / 'app/src/main/java/com/yamo/snorelab/MainActivity.java'
UPLOAD_ACTIVITY = ROOT / 'app/src/main/java/com/yamo/snorelab/UploadExerciseActivity.java'
SLEEP_UI = ROOT / 'app/src/main/java/com/yamo/snorelab/SleepUploadUiEnhancer.java'
ACTIVITY_UPLOADER = ROOT / 'app/src/main/java/com/yamo/snorelab/SupabaseActivityUploader.java'
SLEEP_UPLOADER = ROOT / 'app/src/main/java/com/yamo/snorelab/SupabaseSleepUploader.java'


def replace_once(path: Path, old: str, new: str, label: str):
    s = path.read_text(encoding='utf-8')
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'v0.36.09 target not found: {label} ({path})')
    path.write_text(s.replace(old, new, 1), encoding='utf-8')


# ---------------------------------------------------------------------------
# Activity record upload UI: developer mode only. If developer mode is turned
# off while the detail screen is still open, remove any already-inserted button.
# ---------------------------------------------------------------------------
replace_once(
    UPLOAD_ACTIVITY,
    '''    private void enhanceUploadUi() {
        View decor = getWindow().getDecorView();
        updatePrivacyCopy(decor);
''',
    '''    private void enhanceUploadUi() {
        View decor = getWindow().getDecorView();
        if (!SystemSettingsBridge.isDeveloperMode(this)) {
            View existing = decor.findViewWithTag(UPLOAD_TAG);
            if (existing != null && existing.getParent() instanceof ViewGroup) {
                ((ViewGroup) existing.getParent()).removeView(existing);
            }
            return;
        }
        updatePrivacyCopy(decor);
''',
    'activity upload UI developer gate')

# ---------------------------------------------------------------------------
# Sleep record upload UI: developer mode only, with live removal after switching
# back to normal-user mode.
# ---------------------------------------------------------------------------
replace_once(
    SLEEP_UI,
    '''    private static void enhance(MainActivity activity) {
        View root = activity.getWindow().getDecorView();
        updatePrivacyCopy(root);
''',
    '''    private static void enhance(MainActivity activity) {
        View root = activity.getWindow().getDecorView();
        if (!SystemSettingsBridge.isDeveloperMode(activity)) {
            View existing = root.findViewWithTag(UPLOAD_TAG);
            if (existing != null && existing.getParent() instanceof ViewGroup) {
                ((ViewGroup) existing.getParent()).removeView(existing);
            }
            return;
        }
        updatePrivacyCopy(root);
''',
    'sleep upload UI developer gate')

# ---------------------------------------------------------------------------
# Backend guard as well: stale UI, old intents, or direct calls cannot upload
# records while normal-user mode is active.
# ---------------------------------------------------------------------------
replace_once(
    ACTIVITY_UPLOADER,
    '''    public static void upload(Context context, File sessionDir, Callback callback) {
        Context appContext = context.getApplicationContext();
''',
    '''    public static void upload(Context context, File sessionDir, Callback callback) {
        if (!SystemSettingsBridge.isDeveloperMode(context)) {
            callback.onFailure("기록 업로드는 개발자 모드에서만 사용할 수 있습니다.");
            return;
        }
        Context appContext = context.getApplicationContext();
''',
    'activity uploader backend developer gate')

replace_once(
    SLEEP_UPLOADER,
    '''    public static void upload(Context context, File sessionDir, Callback callback) {
        Context appContext = context.getApplicationContext();
''',
    '''    public static void upload(Context context, File sessionDir, Callback callback) {
        if (!SystemSettingsBridge.isDeveloperMode(context)) {
            callback.onFailure("기록 업로드는 개발자 모드에서만 사용할 수 있습니다.");
            return;
        }
        Context appContext = context.getApplicationContext();
''',
    'sleep uploader backend developer gate')

# Normal-user screens should not advertise the hidden upload feature. Keep only
# local-storage/privacy wording. Developer mode gets the explicit upload copy
# from SleepUploadUiEnhancer/UploadExerciseActivity.
replace_once(
    MAIN,
    'TextView p = text("녹음과 분석 기록은 앱 내부에 저장하며 자동 업로드하지 않습니다.", 11, MUTED, false);',
    'TextView p = text("녹음과 분석 기록은 앱 내부에 저장합니다.", 11, MUTED, false);',
    'sleep home privacy copy')

replace_once(
    MAIN,
    'TextView privacyHelp = text("기록은 사용자가 선택하지 않으면 외부로 보내지 않아요.", 11, PRIMARY2, true);',
    'TextView privacyHelp = text("기록은 휴대폰 내부 저장소에서 관리해요.", 11, PRIMARY2, true);',
    'settings privacy headline')

replace_once(
    MAIN,
    'privacy.addView(checkLine("수면 기록을 자체 서버로 자동 업로드하지 않습니다."));',
    'privacy.addView(checkLine("수면 기록은 휴대폰 내부 저장소에서 관리합니다."));',
    'settings upload copy')

# ---------------------------------------------------------------------------
# Version marker.
# ---------------------------------------------------------------------------
version_src = ROOT / 'design-preview/v03609-version.js'
if not version_src.is_file():
    raise SystemExit('v0.36.09 version source missing')
(TARGET / 'v03609-version.js').write_bytes(version_src.read_bytes())

s = INDEX.read_text(encoding='utf-8')
s = s.replace('  <script src="v03608-version.js"></script>\n', '')
s = s.replace('<script src="v03608-version.js"></script>\n', '')
if 'v03609-version.js' not in s:
    if '</body>' not in s:
        raise SystemExit('v0.36.09 index body missing')
    s = s.replace('</body>', '  <script src="v03609-version.js"></script>\n</body>', 1)
INDEX.write_text(s, encoding='utf-8')

replace_once(
    BUILD,
    "        versionCode 102\n        versionName '0.36.08'",
    "        versionCode 103\n        versionName '0.36.09'",
    'android version')

print('Applied v0.36.09 developer-only record upload UI and backend gates.')
