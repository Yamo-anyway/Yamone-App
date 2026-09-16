#!/usr/bin/env python3
from pathlib import Path
import re

R = Path(__file__).resolve().parent.parent
J = R / 'app/src/main/java/com/yamo/snorelab'
A = R / 'app/src/main/assets/yamone-v23'
BUILD = R / 'app/build.gradle'

# User activity/sleep records are local-only. Remove the old Supabase uploader
# implementations and sleep upload UI/state from the generated Android source.
# Developer diagnostic capture remains separate and sends only to the paired
# Mac mini while developer mode is enabled.
for name in (
    'SupabaseActivityUploader.java',
    'SupabaseSleepUploader.java',
    'SleepUploadUiEnhancer.java',
    'SleepUploadState.java',
):
    p = J / name
    if p.exists():
        p.unlink()

# UploadExerciseActivity is part of the existing activity-screen inheritance
# chain (EnhancedExerciseActivity extends it). Keep that class name as a plain
# local-only ExerciseActivity with no upload button or network code.
(J / 'UploadExerciseActivity.java').write_text(
    '''package com.yamo.snorelab;\n\n/** Local-only activity detail base. User activity records are never uploaded. */\npublic class UploadExerciseActivity extends ExerciseActivity {\n}\n''',
    encoding='utf-8')

# Remove the old sleep upload enhancer lifecycle hooks.
app = J / 'YamoneApplication.java'
s = app.read_text(encoding='utf-8')
s = s.replace('            SleepUploadUiEnhancer.attach(main);\n', '')
s = s.replace('            SleepUploadUiEnhancer.detach(main);\n', '')
app.write_text(s, encoding='utf-8')

# v0.36.10 temporarily routed SupabaseAnonymousRpcClient through the developer
# network guard for the old Supabase developer-dataset transport. That transport
# is gone. Restore the ordinary client because SkiLiftApi still legitimately uses
# Supabase and is unrelated to activity/sleep record collection.
p = J / 'SupabaseAnonymousRpcClient.java'
s = p.read_text(encoding='utf-8')
s = re.sub(
    r'\n    static JSONObject developerAuth\(Context context\) throws Exception \{\n'
    r'        DevNetworkGuard\.check\(\);\n'
    r'        AuthSession s=ensureAnonymousSession\(context\.getApplicationContext\(\)\);\n'
    r'        return new JSONObject\(\)\.put\("accessToken",s\.accessToken\)\.put\("userId",s\.userId\);\n'
    r'    \}\n',
    '\n', s, count=1)
s = s.replace('DevNetworkGuard.open(url)', '(HttpURLConnection) url.openConnection()')
s = s.replace('DevNetworkGuard.check(); out.write(bytes);', 'out.write(bytes);')
p.write_text(s, encoding='utf-8')

# Explicit local-only privacy language for ordinary users.
main = J / 'MainActivity.java'
s = main.read_text(encoding='utf-8')
s = s.replace(
    'TextView p = text("녹음과 분석 기록은 앱 내부에 저장합니다.", 11, MUTED, false);',
    'TextView p = text("수면 기록과 분석 결과는 휴대폰 내부에만 저장합니다.", 11, MUTED, false);')
s = s.replace(
    'TextView privacyHelp = text("기록은 휴대폰 내부 저장소에서 관리해요.", 11, PRIMARY2, true);',
    'TextView privacyHelp = text("활동·수면 개인 기록은 휴대폰 내부에서만 관리해요.", 11, PRIMARY2, true);')
s = s.replace(
    'privacy.addView(checkLine("수면 기록은 휴대폰 내부 저장소에서 관리합니다."));',
    'privacy.addView(checkLine("활동·수면 개인 기록은 야모네 서버로 전송하지 않습니다."));')
main.write_text(s, encoding='utf-8')

# Version.
s = BUILD.read_text(encoding='utf-8')
s = s.replace('versionCode 106', 'versionCode 107').replace("versionName '0.36.12'", "versionName '0.36.13'")
BUILD.write_text(s, encoding='utf-8')

js = A / 'v03610-dataset.js'
s = js.read_text(encoding='utf-8').replace("const V='0.36.12'", "const V='0.36.13'")
js.write_text(s, encoding='utf-8')

# Keep every visible version marker in sync.
for asset in A.rglob('*'):
    if not asset.is_file():
        continue
    try:
        text = asset.read_text(encoding='utf-8')
    except (UnicodeDecodeError, OSError):
        continue
    new = text.replace('0.36.12', '0.36.13')
    if new != text:
        asset.write_text(new, encoding='utf-8')

print('Applied v0.36.13 local-only user activity/sleep records; developer Mac mini diagnostics remain.')
