from pathlib import Path

ROOT = Path('app/src/main/java/com/yamo/snorelab')

def read(name):
    return (ROOT / name).read_text()

def write(name, text):
    (ROOT / name).write_text(text)

def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing {label}')
    return text.replace(old, new, 1)

# MainActivity: compact top-level header, remove Home activity/sleep quick buttons,
# and make Location Sharing a stable first-class Home button.
s = read('MainActivity.java')
s = replace_once(s,
'''        words.addView(text(title, 26, TEXT, true));
        TextView sub = text(subtitle, 12, MUTED, false);
        sub.setPadding(0, dp(2), 0, 0);
        words.addView(sub);
        header.addView(words, new LinearLayout.LayoutParams(0, dp(62), 1f));''',
'''        words.addView(text(title, 26, TEXT, true));
        header.addView(words, new LinearLayout.LayoutParams(0, dp(54), 1f));''',
'main fixed header subtitle')

s = replace_once(s,
'''        shell.addView(fixedHeader("홈", "좋은 하루예요! 오늘도 빛나는 당신을 응원해요 💕"),''',
'''        shell.addView(fixedHeader("홈", ""),''',
'home header copy')

s = replace_once(s,
'''        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button activity = actionButton("♡  활동 시작", true, v -> startActivity(new Intent(this, LocationExerciseActivity.class)));
        Button sleep = ghostButton("☾  수면 기록", v -> showSleep());
        actions.addView(activity, new LinearLayout.LayoutParams(0, dp(52), 1f));
        LinearLayout.LayoutParams sleepParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        sleepParams.leftMargin = dp(8);
        actions.addView(sleep, sleepParams);
        LinearLayout.LayoutParams actionRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionRowParams.bottomMargin = dp(12);
        page.addView(actions, actionRowParams);''',
'''        String locationLabel = LocationSharingStateStore.isActive(this)
                ? "위치 공유 중 · 방 보기" : "위치 공유";
        Button locationSharing = actionButton(locationLabel, true,
                v -> startActivity(new Intent(this, LocationSharingActivityV2.class)));
        LinearLayout.LayoutParams locationParams = match(dp(54));
        locationParams.bottomMargin = dp(12);
        page.addView(locationSharing, locationParams);''',
'home quick actions')

s = replace_once(s,
'''        shell.addView(fixedHeader("수면", "잘 자는 것이, 더 좋은 나를 만들어요. 💕"),''',
'''        shell.addView(fixedHeader("수면", ""),''',
'sleep header copy')
write('MainActivity.java', s)

# AlarmActivity: title only on top-level Alarm screen.
s = read('AlarmActivity.java')
s = replace_once(s,
'''        words.addView(text(title, 26, TEXT, true));
        TextView sub = text(subtitle, 12, MUTED, false);
        sub.setPadding(0, dp(2), 0, 0);
        words.addView(sub);
        header.addView(words, new LinearLayout.LayoutParams(0, dp(62), 1f));''',
'''        words.addView(text(title, 26, TEXT, true));
        header.addView(words, new LinearLayout.LayoutParams(0, dp(54), 1f));''',
'alarm fixed header subtitle')

s = replace_once(s,
'''        shell.addView(fixedHeader("알람", "잊지 말고, 챙겨요! 좋은 습관이 좋은 하루를 만들어요 💕"), matchWrap());''',
'''        shell.addView(fixedHeader("알람", ""), matchWrap());''',
'alarm header copy')
write('AlarmActivity.java', s)

# ExerciseActivity: title only on top-level Activity screen.
s = read('ExerciseActivity.java')
s = replace_once(s,
'''        words.addView(text(title, 26, TEXT, true));
        TextView sub = text(subtitle, 12, MUTED, false);
        sub.setPadding(0, dp(2), 0, 0);
        words.addView(sub);
        header.addView(words, new LinearLayout.LayoutParams(0, dp(62), 1f));''',
'''        words.addView(text(title, 26, TEXT, true));
        header.addView(words, new LinearLayout.LayoutParams(0, dp(54), 1f));''',
'activity main header subtitle')

s = replace_once(s,
'''        shell.addView(mainHeader("활동", "오늘도 움직여요! 작은 움직임이 큰 변화를 만들어요 💕"),''',
'''        shell.addView(mainHeader("활동", ""),''',
'activity header copy')
write('ExerciseActivity.java', s)

# Guards
checks = {
    'MainActivity.java': ['Button locationSharing = actionButton(locationLabel', 'fixedHeader("홈", "")', 'fixedHeader("수면", "")'],
    'AlarmActivity.java': ['fixedHeader("알람", "")'],
    'ExerciseActivity.java': ['mainHeader("활동", "")'],
}
for name, tokens in checks.items():
    text = read(name)
    for token in tokens:
        if token not in text:
            raise SystemExit(f'guard failed {name}: {token}')

if '♡  활동 시작' in read('MainActivity.java') or '☾  수면 기록' in read('MainActivity.java'):
    raise SystemExit('old Home quick action remained')
