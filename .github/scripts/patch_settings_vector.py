from pathlib import Path
import re

ROOT = Path('app/src/main/java/com/yamo/snorelab')


def replace_gear(path, click_body):
    p = ROOT / path
    s = p.read_text()
    pattern = re.compile(
        r'\s*TextView gear = text\("⚙", 21, PRIMARY2, false\);\n'
        r'\s*gear\.setGravity\(Gravity\.CENTER\);\n'
        r'\s*gear\.setBackground\([^\n]+\);\n'
        r'\s*gear\.setOnClickListener\(v -> \{.*?\}\);\n'
        r'\s*header\.addView\(gear, new LinearLayout\.LayoutParams\(dp\(44\), dp\(44\)\)\);',
        re.S,
    )
    replacement = '\n        View gear = YamoneSettingsButton.create(this, v -> {' + click_body + '});\n        header.addView(gear, new LinearLayout.LayoutParams(dp(44), dp(44)));'
    new, n = pattern.subn(replacement, s, count=1)
    if n != 1:
        raise SystemExit(f'{path}: settings gear block not replaced')
    p.write_text(new)

replace_gear('MainActivity.java', ' settingsReturnScreen = screen; showSettings(); ')
replace_gear('AlarmActivity.java', ' startActivity(new Intent(this, MainActivity.class).putExtra("start_screen", "settings").putExtra("settings_return", "alarm")); finish(); ')
replace_gear('ExerciseActivity.java', ' Intent intent = new Intent(this, MainActivity.class).putExtra("start_screen", "settings").putExtra("settings_return", "activity"); startActivity(intent); ')

for name in ['MainActivity.java', 'AlarmActivity.java', 'ExerciseActivity.java']:
    s = (ROOT / name).read_text()
    if 'YamoneSettingsButton.create' not in s:
        raise SystemExit(f'{name}: vector settings button missing')
