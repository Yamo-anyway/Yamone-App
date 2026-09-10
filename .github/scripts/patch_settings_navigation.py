from pathlib import Path

ROOT = Path('app/src/main/java/com/yamo/snorelab')

# 1) Activity -> Settings: do not leave a duplicate activity instance underneath settings.
p = ROOT / 'ExerciseActivity.java'
s = p.read_text()
old = '''View gear = YamoneSettingsButton.create(this, v -> { Intent intent = new Intent(this, MainActivity.class).putExtra("start_screen", "settings").putExtra("settings_return", "activity"); startActivity(intent); });'''
new = '''View gear = YamoneSettingsButton.create(this, v -> { Intent intent = new Intent(this, MainActivity.class).putExtra("start_screen", "settings").putExtra("settings_return", "activity"); startActivity(intent); finish(); });'''
if old not in s:
    raise SystemExit('ExerciseActivity settings listener not found')
s = s.replace(old, new, 1)
p.write_text(s)

# 2) Alarm back rule: editor -> alarm list, alarm top -> Home.
p = ROOT / 'AlarmActivity.java'
s = p.read_text()
anchor = '''    @Override protected void onDestroy() {
        stopPreview();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
'''
replacement = anchor + '''
    @Override public void onBackPressed() {
        if (editorOpen) {
            showList();
            return;
        }
        goMain("home");
    }
'''
if anchor not in s:
    raise SystemExit('AlarmActivity onDestroy anchor not found')
if '@Override public void onBackPressed()' not in s:
    s = s.replace(anchor, replacement, 1)
p.write_text(s)

# 3) Settings screen visual wording / hierarchy polish without changing stored settings behavior.
p = ROOT / 'MainActivity.java'
s = p.read_text()
repls = {
    'fixedBackHeader("설정", "야모네의 테마와 기록 환경을 편하게 조절해요.", v -> returnFromSettings())':
        'fixedBackHeader("설정", "내게 편한 야모네로 맞춰보세요. 💕", v -> returnFromSettings())',
    'LinearLayout theme = card(); theme.addView(text("테마", 15, TEXT, true));':
        'LinearLayout theme = card(); theme.addView(text("화면 테마", 16, TEXT, true)); TextView themeHelp = text("야모네의 전체 분위기를 선택해요.", 11, MUTED, false); themeHelp.setPadding(0, dp(4), 0, 0); theme.addView(themeHelp);',
    'Button mint = choiceButton("🌿  민트", !pinkTheme(), v -> setThemeAndRefresh("mint"));':
        'Button mint = choiceButton("민트", !pinkTheme(), v -> setThemeAndRefresh("mint"));',
    'Button pink = choiceButton("🌸  핑크", pinkTheme(), v -> setThemeAndRefresh("pink"));':
        'Button pink = choiceButton("핑크", pinkTheme(), v -> setThemeAndRefresh("pink"));',
    'measure.addView(text("마이크 설정", 15, TEXT, true));':
        'measure.addView(text("수면 감지", 16, TEXT, true));',
    'LinearLayout storage = card(); storage.addView(text("녹음 보관 기간", 15, TEXT, true));':
        'LinearLayout storage = card(); storage.addView(text("기록 보관", 16, TEXT, true)); TextView storageHelp = text("수면 녹음과 분석 기록의 보관 기간을 정해요.", 11, MUTED, false); storageHelp.setPadding(0, dp(4), 0, 0); storage.addView(storageHelp);',
    'LinearLayout privacy = card(); privacy.addView(text("개인정보 보호", 15, TEXT, true));':
        'LinearLayout privacy = card(); privacy.addView(text("개인정보", 16, TEXT, true)); TextView privacyHelp = text("기록은 사용자가 선택하지 않으면 외부로 보내지 않아요.", 11, PRIMARY2, true); privacyHelp.setPadding(0, dp(4), 0, dp(2)); privacy.addView(privacyHelp);',
    'LinearLayout dev = card(); dev.addView(text("개발자 검증", 15, TEXT, true));':
        'LinearLayout dev = card(); dev.addView(text("개발자 검증", 16, TEXT, true)); TextView devHelp = text("분석 엔진과 저장 형식을 확인하는 정보예요.", 11, MUTED, false); devHelp.setPadding(0, dp(4), 0, dp(2)); dev.addView(devHelp);'
}
for old, new in repls.items():
    if old not in s:
        raise SystemExit('MainActivity settings text block not found: ' + old[:50])
    s = s.replace(old, new, 1)

# Give settings cards slightly more breathing room while retaining the common card style.
old = 'LinearLayout themeRow = new LinearLayout(this); themeRow.setOrientation(LinearLayout.HORIZONTAL); themeRow.setPadding(0, dp(10), 0, 0);'
new = 'LinearLayout themeRow = new LinearLayout(this); themeRow.setOrientation(LinearLayout.HORIZONTAL); themeRow.setPadding(0, dp(14), 0, 0);'
s = s.replace(old, new, 1)

p.write_text(s)

# Sanity checks.
if 'startActivity(intent); finish();' not in (ROOT / 'ExerciseActivity.java').read_text():
    raise SystemExit('Activity settings stack fix missing')
if 'if (editorOpen)' not in (ROOT / 'AlarmActivity.java').read_text():
    raise SystemExit('Alarm back rule missing')
main = (ROOT / 'MainActivity.java').read_text()
for needle in ['화면 테마', '수면 감지', '기록 보관', '개인정보']:
    if needle not in main:
        raise SystemExit('Settings polish missing: ' + needle)
