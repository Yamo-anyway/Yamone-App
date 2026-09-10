from pathlib import Path

ROOT = Path('app/src/main/java/com/yamo/snorelab')


def patch_file(name, replacements):
    path = ROOT / name
    text = path.read_text()
    for label, old, new in replacements:
        if old not in text:
            raise SystemExit(f'{name}: missing {label}')
        text = text.replace(old, new, 1)
    path.write_text(text)


patch_file('MainActivity.java', [
    ('bottom nav field',
     '    private FrameLayout content;\n',
     '    private FrameLayout content;\n    private LinearLayout bottomNav;\n'),
    ('bottom nav assignment',
     '        LinearLayout nav = new LinearLayout(this);\n',
     '        bottomNav = new LinearLayout(this);\n        LinearLayout nav = bottomNav;\n'),
    ('bottom nav helper',
     '        setContentView(root);\n    }\n\n    private TextView navItem',
     '        setContentView(root);\n    }\n\n    private void setBottomNavVisible(boolean visible) {\n        if (bottomNav != null) bottomNav.setVisibility(visible ? View.VISIBLE : View.GONE);\n    }\n\n    private TextView navItem'),
    ('home nav visible',
     '    private void showHome() {\n        detailSession = null;\n        screen = "home";\n        updateNav();\n',
     '    private void showHome() {\n        detailSession = null;\n        screen = "home";\n        updateNav();\n        setBottomNavVisible(true);\n'),
    ('sleep top nav visible',
     '    private void showSleep() {\n        screen = "sleep";\n        updateNav();\n        if (detailSession != null) { showSessionDetail(detailSession); return; }\n',
     '    private void showSleep() {\n        screen = "sleep";\n        updateNav();\n        if (detailSession != null) { showSessionDetail(detailSession); return; }\n        setBottomNavVisible(true);\n'),
    ('sleep detail nav hidden',
     '    private void showSessionDetail(File dir) {\n        screen = "sleep"; updateNav(); content.removeAllViews(); stopPlayer();\n',
     '    private void showSessionDetail(File dir) {\n        screen = "sleep"; updateNav(); setBottomNavVisible(false); content.removeAllViews(); stopPlayer();\n'),
    ('settings nav hidden',
     '    private void showSettings() {\n        screen = "settings"; updateNav(); content.removeAllViews();\n',
     '    private void showSettings() {\n        screen = "settings"; updateNav(); setBottomNavVisible(false); content.removeAllViews();\n'),
])

patch_file('AlarmActivity.java', [
    ('bottom nav field',
     '    private FrameLayout content;\n',
     '    private FrameLayout content;\n    private LinearLayout bottomNav;\n'),
    ('bottom nav assignment',
     '        LinearLayout nav = new LinearLayout(this);\n',
     '        bottomNav = new LinearLayout(this);\n        LinearLayout nav = bottomNav;\n'),
    ('bottom nav helper',
     '        setContentView(root);\n    }\n\n    private void goMain',
     '        setContentView(root);\n    }\n\n    private void setBottomNavVisible(boolean visible) {\n        if (bottomNav != null) bottomNav.setVisibility(visible ? View.VISIBLE : View.GONE);\n    }\n\n    private void goMain'),
    ('alarm list nav visible',
     '    private void showList() {\n        editorOpen = false;\n',
     '    private void showList() {\n        editorOpen = false;\n        setBottomNavVisible(true);\n'),
    ('alarm editor nav hidden',
     '    private void showEditor(AlarmStore.Item existing) {\n        editorOpen = true;\n',
     '    private void showEditor(AlarmStore.Item existing) {\n        editorOpen = true;\n        setBottomNavVisible(false);\n'),
])

patch_file('ExerciseActivity.java', [
    ('bottom nav field',
     '    private FrameLayout content;\n',
     '    private FrameLayout content;\n    private LinearLayout bottomNav;\n'),
    ('bottom nav assignment',
     '        LinearLayout nav = new LinearLayout(this);\n',
     '        bottomNav = new LinearLayout(this);\n        LinearLayout nav = bottomNav;\n'),
    ('bottom nav helper',
     '        setContentView(root);\n    }\n\n    private LinearLayout mainHeader',
     '        setContentView(root);\n    }\n\n    private void setBottomNavVisible(boolean visible) {\n        if (bottomNav != null) bottomNav.setVisibility(visible ? View.VISIBLE : View.GONE);\n    }\n\n    private LinearLayout mainHeader'),
    ('activity home/live nav state',
     '    private void showHome() {\n        detailOpen = false;\n        detailDir = null;\n',
     '    private void showHome() {\n        detailOpen = false;\n        detailDir = null;\n        setBottomNavVisible(!runtime.getBoolean(WalkingRecorderService.KEY_RECORDING, false));\n'),
    ('walkrun nav hidden',
     '    private void showWalkRunMenu() {\n        detailOpen = true;\n',
     '    private void showWalkRunMenu() {\n        detailOpen = true;\n        setBottomNavVisible(false);\n'),
    ('cycling nav hidden',
     '    private void showCyclingMenu() {\n        detailOpen = true;\n',
     '    private void showCyclingMenu() {\n        detailOpen = true;\n        setBottomNavVisible(false);\n'),
    ('ski preview nav hidden',
     '    private void showSkiPreview() {\n        detailOpen = true;\n',
     '    private void showSkiPreview() {\n        detailOpen = true;\n        setBottomNavVisible(false);\n'),
    ('activity summary nav hidden',
     '    private void showActivitySummary(File dir, boolean justFinished) {\n        detailOpen = true; detailDir = dir; content.removeAllViews();\n',
     '    private void showActivitySummary(File dir, boolean justFinished) {\n        detailOpen = true; detailDir = dir; setBottomNavVisible(false); content.removeAllViews();\n'),
])

# Sanity checks
main = (ROOT / 'MainActivity.java').read_text()
alarm = (ROOT / 'AlarmActivity.java').read_text()
exercise = (ROOT / 'ExerciseActivity.java').read_text()
assert 'private LinearLayout bottomNav;' in main
assert 'showSessionDetail(File dir)' in main and 'setBottomNavVisible(false); content.removeAllViews(); stopPlayer();' in main
assert 'screen = "settings"; updateNav(); setBottomNavVisible(false);' in main
assert 'private LinearLayout bottomNav;' in alarm
assert 'editorOpen = true;\n        setBottomNavVisible(false);' in alarm
assert 'editorOpen = false;\n        setBottomNavVisible(true);' in alarm
assert 'private LinearLayout bottomNav;' in exercise
assert 'setBottomNavVisible(!runtime.getBoolean(WalkingRecorderService.KEY_RECORDING, false));' in exercise
assert exercise.count('setBottomNavVisible(false);') >= 4
