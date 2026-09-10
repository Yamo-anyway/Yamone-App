from pathlib import Path

ROOT = Path('app/src/main/java/com/yamo/snorelab')


def replace_once(path, old, new):
    p = Path(path)
    s = p.read_text()
    if old not in s:
        raise SystemExit(f'{path}: expected block not found')
    p.write_text(s.replace(old, new, 1))


def icon_method_boolean():
    return '''    private TextView navItem(String label, boolean selected, View.OnClickListener click) {
        int icon = label.contains("활동") ? R.drawable.ic_nav_activity
                : label.contains("알람") ? R.drawable.ic_nav_alarm
                : label.contains("수면") ? R.drawable.ic_nav_sleep
                : R.drawable.ic_nav_home;
        String clean = label.contains("활동") ? "활동"
                : label.contains("알람") ? "알람"
                : label.contains("수면") ? "수면" : "홈";
        return YamoneBottomNav.create(this, icon, clean, selected, PRIMARY2, MUTED, CARD2, click);
    }
'''

# MainActivity
replace_once(
    ROOT / 'MainActivity.java',
'''    private TextView navItem(String label, boolean selected, View.OnClickListener click) {
        TextView v = text(label, 12, selected ? PRIMARY2 : MUTED, true);
        v.setGravity(Gravity.CENTER);
        v.setOnClickListener(click);
        return v;
    }
''',
icon_method_boolean())

replace_once(
    ROOT / 'MainActivity.java',
'''    private void styleNav(TextView item, boolean selected) {
        if (item == null) return;
        item.setTextColor(selected ? PRIMARY2 : MUTED);
        item.setBackground(selected ? round(CARD2, 19, 0, 0) : null);
    }
''',
'''    private void styleNav(TextView item, boolean selected) {
        YamoneBottomNav.apply(this, item, selected, PRIMARY2, MUTED, CARD2);
    }
''')

# AlarmActivity
replace_once(
    ROOT / 'AlarmActivity.java',
'''    private TextView navItem(String label, boolean selected, View.OnClickListener listener) {
        TextView v = text(label, 12, selected ? PRIMARY2 : MUTED, true);
        v.setGravity(Gravity.CENTER);
        if (selected) v.setBackground(rounded(CARD2, 19, 0, 0));
        v.setOnClickListener(listener);
        return v;
    }
''',
'''    private TextView navItem(String label, boolean selected, View.OnClickListener listener) {
        int icon = label.contains("활동") ? R.drawable.ic_nav_activity
                : label.contains("알람") ? R.drawable.ic_nav_alarm
                : label.contains("수면") ? R.drawable.ic_nav_sleep
                : R.drawable.ic_nav_home;
        String clean = label.contains("활동") ? "활동"
                : label.contains("알람") ? "알람"
                : label.contains("수면") ? "수면" : "홈";
        return YamoneBottomNav.create(this, icon, clean, selected, PRIMARY2, MUTED, CARD2, listener);
    }
''')

# ExerciseActivity
replace_once(
    ROOT / 'ExerciseActivity.java',
'''    private TextView navItem(String label, int color, View.OnClickListener click) { TextView v = text(label, 12, color, true); v.setGravity(Gravity.CENTER); if (color == PRIMARY2) v.setBackground(round(CARD2, 19, 0, 0)); v.setOnClickListener(click); return v; }
''',
'''    private TextView navItem(String label, int color, View.OnClickListener click) {
        int icon = label.contains("활동") ? R.drawable.ic_nav_activity
                : label.contains("알람") ? R.drawable.ic_nav_alarm
                : label.contains("수면") ? R.drawable.ic_nav_sleep
                : R.drawable.ic_nav_home;
        String clean = label.contains("활동") ? "활동"
                : label.contains("알람") ? "알람"
                : label.contains("수면") ? "수면" : "홈";
        return YamoneBottomNav.create(this, icon, clean, color == PRIMARY2, PRIMARY2, MUTED, CARD2, click);
    }
''')

# Slightly taller, airier common nav to match the showcase while staying above system navigation.
for name in ['MainActivity.java', 'AlarmActivity.java', 'ExerciseActivity.java']:
    p = ROOT / name
    s = p.read_text()
    s = s.replace('new LinearLayout.LayoutParams(0, dp(60), 1f)', 'new LinearLayout.LayoutParams(0, dp(62), 1f)')
    p.write_text(s)

# Verify old emoji/glyphs are no longer used as the actual nav item content after navItem cleanup.
for name in ['MainActivity.java', 'AlarmActivity.java', 'ExerciseActivity.java']:
    s = (ROOT / name).read_text()
    if 'YamoneBottomNav.create' not in s:
        raise SystemExit(f'{name}: shared bottom nav not applied')
