from pathlib import Path

# 1) Stop activity-home from being rebuilt every second after a recording finishes.
p = Path('app/src/main/java/com/yamo/snorelab/ExerciseActivity.java')
s = p.read_text()
old = '''    private void showHome() {\n        detailOpen = false;\n        detailDir = null;\n        liveModeBreakdown = null;\n        content.removeAllViews();\n'''
new = '''    private void showHome() {\n        detailOpen = false;\n        detailDir = null;\n        // Clear stale live-view references before rebuilding the non-recording home.\n        // Otherwise the 1-second refresher sees the old liveDistance reference and\n        // calls showHome() repeatedly after recording has ended, resetting ScrollView to the top.\n        liveDistance = null;\n        liveTime = null;\n        livePace = null;\n        liveSteps = null;\n        liveSpeed = null;\n        liveMoving = null;\n        liveAltitude = null;\n        liveAccuracy = null;\n        liveModeBreakdown = null;\n        liveGoal = null;\n        liveProgress = null;\n        liveRoute = null;\n        lastRouteReload = 0L;\n        content.removeAllViews();\n'''
if old not in s:
    raise SystemExit('ExerciseActivity showHome marker not found')
s = s.replace(old, new, 1)
p.write_text(s)

# 2) Remove full-activity slide animations. Bottom navigation is already outside each
# screen ScrollView; disabling Activity transitions makes it visually stationary while
# switching Home / Activity / Alarm / Sleep / Mini-game.
t = Path('app/src/main/res/values/themes.xml')
x = t.read_text()
if 'Animation.SnoreLab.NoTransition' not in x:
    x = x.replace(
        '        <item name="android:windowBackground">#F7FFFB</item>\n',
        '        <item name="android:windowBackground">#F7FFFB</item>\n'
        '        <item name="android:windowAnimationStyle">@style/Animation.SnoreLab.NoTransition</item>\n'
    )
    x = x.replace(
        '</resources>',
        '''\n    <style name="Animation.SnoreLab.NoTransition">\n        <item name="android:activityOpenEnterAnimation">@null</item>\n        <item name="android:activityOpenExitAnimation">@null</item>\n        <item name="android:activityCloseEnterAnimation">@null</item>\n        <item name="android:activityCloseExitAnimation">@null</item>\n        <item name="android:taskOpenEnterAnimation">@null</item>\n        <item name="android:taskOpenExitAnimation">@null</item>\n        <item name="android:taskCloseEnterAnimation">@null</item>\n        <item name="android:taskCloseExitAnimation">@null</item>\n    </style>\n</resources>'''
    )
t.write_text(x)

print('patched activity scroll refresh and navigation transitions')
