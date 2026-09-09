from pathlib import Path


def replace(path, old, new, count=None):
    p = Path(path)
    s = p.read_text()
    n = s.count(old)
    if n == 0:
        raise SystemExit(f'pattern not found in {path}: {old[:120]!r}')
    if count is not None and n != count:
        raise SystemExit(f'unexpected count {n} in {path}, wanted {count}: {old[:120]!r}')
    p.write_text(s.replace(old, new))
    print('patched', path, n)


main = 'app/src/main/java/com/yamo/snorelab/MainActivity.java'
replace(main, '    private String screen = "sleep";\n', '    private String screen = "home";\n    private String settingsReturnScreen = "home";\n', 1)
replace(main,
'''        else if ("activity".equals(startScreen)) startActivity(new Intent(this, ExerciseActivity.class));
        else if ("minigame".equals(startScreen)) startActivity(new Intent(this, MiniGameActivity.class));
        else if ("alarm".equals(startScreen)) startActivity(new Intent(this, AlarmActivity.class));
        else showSleep();''',
'''        else if ("activity".equals(startScreen)) startActivity(new Intent(this, LocationExerciseActivity.class));
        else if ("minigame".equals(startScreen)) startActivity(new Intent(this, MiniGameActivity.class));
        else if ("alarm".equals(startScreen)) startActivity(new Intent(this, AlarmActivity.class));
        else showHome();''', 1)
replace(main,
'''    @Override public void onBackPressed() {
        if (detailSession != null) { detailSession = null; showSleep(); return; }
        if (!"sleep".equals(screen)) { screen = "sleep"; showSleep(); return; }
        super.onBackPressed();
    }''',
'''    @Override public void onBackPressed() {
        if (detailSession != null) { detailSession = null; showSleep(); return; }
        if ("settings".equals(screen)) { returnFromSettings(); return; }
        if (!"home".equals(screen)) { showHome(); return; }
        super.onBackPressed();
    }''', 1)
replace(main,
'        activityNav = navItem("🏃\\n활동", false, v -> startActivity(new Intent(this, ExerciseActivity.class)));',
'        activityNav = navItem("🏃\\n활동", false, v -> startActivity(new Intent(this, LocationExerciseActivity.class)));', 1)
replace(main,
'        gear.setOnClickListener(v -> showSettings());',
'        gear.setOnClickListener(v -> { settingsReturnScreen = screen; showSettings(); });', 1)
replace(main,
'''        shell.addView(fixedBackHeader("수면 설정", "테마와 마이크 측정을 편하게 조절해요.", v -> showSleep()),''',
'''        shell.addView(fixedBackHeader("수면 설정", "테마와 마이크 측정을 편하게 조절해요.", v -> returnFromSettings()),''', 1)
marker = '    private String sensitivityGuide(int value) {'
insert = '''    private void returnFromSettings() {
        String target = settingsReturnScreen;
        settingsReturnScreen = "home";
        if ("sleep".equals(target)) showSleep();
        else showHome();
    }

'''
replace(main, marker, insert + marker, 1)

for f in [
    'app/src/main/java/com/yamo/snorelab/AlarmActivity.java',
    'app/src/main/java/com/yamo/snorelab/MiniGameActivity.java',
    'app/src/main/java/com/yamo/snorelab/LocationSharingHomeUiEnhancer.java',
]:
    p = Path(f)
    s = p.read_text()
    if 'ExerciseActivity.class' in s:
        p.write_text(s.replace('ExerciseActivity.class', 'LocationExerciseActivity.class'))
        print('activity route patched', f)

alarm = 'app/src/main/java/com/yamo/snorelab/AlarmActivity.java'
p = Path(alarm)
s = p.read_text()
anchor = '''    @Override protected void onDestroy() {
        stopPreview();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
'''
if anchor not in s:
    raise SystemExit('alarm onDestroy anchor missing')
s = s.replace(anchor, anchor + '''
    @Override public void onBackPressed() {
        if (editorOpen) {
            editorOpen = false;
            showList();
            return;
        }
        super.onBackPressed();
    }
''', 1)
p.write_text(s)

exercise = 'app/src/main/java/com/yamo/snorelab/ExerciseActivity.java'
replace(exercise,
'        nav.setPadding(dp(8), dp(7), dp(8), dp(9));\n        nav.setBackgroundColor(0xFF0E182A);',
'        nav.setPadding(dp(8), dp(7), dp(8), dp(8));\n        nav.setBackgroundColor(0xFFFFFFFF);\n        if (Build.VERSION.SDK_INT >= 21) nav.setElevation(dp(6));', 1)
replace(exercise, 'new LinearLayout.LayoutParams(0, dp(56), 1f)', 'new LinearLayout.LayoutParams(0, dp(60), 1f)')
replace(exercise,
'    private TextView navItem(String label, int color, View.OnClickListener click) { TextView v = text(label, 12, color, true); v.setGravity(Gravity.CENTER); v.setOnClickListener(click); return v; }',
'    private TextView navItem(String label, int color, View.OnClickListener click) { TextView v = text(label, 12, color, true); v.setGravity(Gravity.CENTER); if (color == PRIMARY2) v.setBackground(round(CARD2, 19, 0, 0)); v.setOnClickListener(click); return v; }', 1)

for f in [main, alarm, 'app/src/main/java/com/yamo/snorelab/MiniGameActivity.java']:
    p = Path(f)
    s = p.read_text()
    needle = '        nav.setBackgroundColor(CARD);\n'
    start = s.find('private void buildRoot')
    segment = s[start:start + 3000] if start >= 0 else ''
    if needle in s and 'nav.setElevation(dp(6))' not in segment:
        s = s.replace(needle, needle + '        if (Build.VERSION.SDK_INT >= 21) nav.setElevation(dp(6));\n', 1)
        p.write_text(s)
        print('nav elevation patched', f)

loc = 'app/src/main/java/com/yamo/snorelab/LocationSharingActivity.java'
replace(loc, '    private boolean activeScreen;\n', '    private boolean activeScreen;\n    private String currentPage = "loading";\n', 1)
replace(loc,
'''    @Override public void onLowMemory() {
        super.onLowMemory();
        if (sharingMap != null) sharingMap.onLowMemory();
    }
''',
'''    @Override public void onLowMemory() {
        super.onLowMemory();
        if (sharingMap != null) sharingMap.onLowMemory();
    }

    @Override public void onBackPressed() {
        if ("room_form".equals(currentPage)) {
            showLanding();
            return;
        }
        finish();
    }
''', 1)
replace(loc, '    private void showLoading() {\n        activeScreen = false;', '    private void showLoading() {\n        currentPage = "loading";\n        activeScreen = false;', 1)
replace(loc, '    private void showLanding() {\n        activeScreen = false;', '    private void showLanding() {\n        currentPage = "landing";\n        activeScreen = false;', 1)
replace(loc, '    private void showRoomForm(boolean create) {\n        createMode = create;', '    private void showRoomForm(boolean create) {\n        currentPage = "room_form";\n        createMode = create;', 1)
replace(loc, '    private void showActive(JSONObject initial) {\n        activeScreen = true;', '    private void showActive(JSONObject initial) {\n        currentPage = "active";\n        activeScreen = true;', 1)
replace(loc,
'''        if (create) roomRow.addView(availabilityButton, checkParams);
        page.addView(roomRow);
''',
'''        if (create) roomRow.addView(availabilityButton, checkParams);
        LinearLayout.LayoutParams roomRowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        roomRowParams.bottomMargin = dp(8);
        page.addView(roomRow, roomRowParams);
''', 1)
replace(loc, '        availabilityText.setPadding(0, dp(5), 0, dp(12));', '        availabilityText.setPadding(0, dp(2), 0, dp(14));', 1)
replace(loc,
'''        pHead.addView(nick, new LinearLayout.LayoutParams(dp(122), dp(38)));
        participants.addView(pHead);''',
'''        LinearLayout.LayoutParams nickParams = new LinearLayout.LayoutParams(dp(122), dp(38));
        nickParams.bottomMargin = dp(10);
        pHead.addView(nick, nickParams);
        participants.addView(pHead);''', 1)
replace(loc,
'''        back.setOnClickListener(v -> {
            if (activeScreen) finish();
            else showLanding();
        });''',
'''        back.setOnClickListener(v -> {
            if ("room_form".equals(currentPage)) showLanding();
            else finish();
        });''', 1)
replace(loc,
'''    private Button smallButton(String value) {
        Button b = new Button(this);''',
'''    private Button smallButton(String value) {
        Button b = new Button(this);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);''', 1)
replace(loc,
'''    private Button tinyButton(String value) {
        Button b = softButton(value);''',
'''    private Button tinyButton(String value) {
        Button b = softButton(value);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);''', 1)

v2 = 'app/src/main/java/com/yamo/snorelab/LocationSharingActivityV2.java'
replace(v2,
'''        addBottomMargin(findExact(root, "⏱  공유 시간 연장하기"), 12);
        addBottomMargin(findExact(root, "▣  위치 공유 중단하기"), 22);''',
'''        addBottomMargin(findExact(root, "중복 확인"), 8);
        addBottomMargin(findExact(root, "내 닉네임 변경"), 10);
        addBottomMargin(findExact(root, "⏱  공유 시간 연장하기"), 12);
        addBottomMargin(findExact(root, "▣  위치 공유 중단하기"), 22);''', 1)

gradle = 'app/build.gradle'
replace(gradle,
"        versionCode 15\n        versionName '0.3.12-route-gps'",
"        versionCode 16\n        versionName '0.3.13-nav-spacing'", 1)
print('patch script completed')
