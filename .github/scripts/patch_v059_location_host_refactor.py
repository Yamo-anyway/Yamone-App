from pathlib import Path

java_path = Path('app/src/main/java/com/yamo/snorelab/LocationSharingActivity.java')
text = java_path.read_text()

# 1) Keep one Activity root for the lifetime of the screen.
old_fields = '''    private int stableTopInset = -1;\n    private int stableBottomInset = -1;\n'''
new_fields = '''    private int stableTopInset = -1;\n    private int stableBottomInset = -1;\n    private LinearLayout activityRoot;\n    private LinearLayout screenHost;\n'''
assert old_fields in text
text = text.replace(old_fields, new_fields, 1)

old_oncreate = '''        applyTheme();\n        configureSystemBars();\n        showLoading();\n'''
new_oncreate = '''        applyTheme();\n        configureSystemBars();\n        initPersistentScreenHost();\n        showLoading();\n'''
assert old_oncreate in text
text = text.replace(old_oncreate, new_oncreate, 1)

# Replace only Activity root swaps. Dialog setContentView calls are intentionally untouched.
assert text.count('        setContentView(root);') == 4
text = text.replace('        setContentView(root);', '        showScreen(root);')

configure_end = '''        getWindow().getDecorView().setSystemUiVisibility(flags);\n    }\n\n    private void showLoading() {\n'''
new_configure_end = '''        getWindow().getDecorView().setSystemUiVisibility(flags);\n    }\n\n    /**\n     * One stable top-level view for this Activity. System-bar padding belongs here,\n     * not to individual pages, so page changes can never move the whole coordinate system.\n     */\n    private void initPersistentScreenHost() {\n        if (stableTopInset < 0) stableTopInset = initialStatusBarInset();\n        if (stableBottomInset < 0) stableBottomInset = initialNavigationBarInset();\n\n        activityRoot = new LinearLayout(this);\n        activityRoot.setOrientation(LinearLayout.VERTICAL);\n        activityRoot.setBackgroundColor(BG);\n        activityRoot.setPadding(0, stableTopInset + dp(4), 0, stableBottomInset + dp(6));\n\n        screenHost = new LinearLayout(this);\n        screenHost.setOrientation(LinearLayout.VERTICAL);\n        screenHost.setBackgroundColor(BG);\n        activityRoot.addView(screenHost, new LinearLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));\n        setContentView(activityRoot);\n    }\n\n    private void showScreen(View screen) {\n        if (screenHost == null) initPersistentScreenHost();\n        screenHost.removeAllViews();\n        screenHost.addView(screen, new LinearLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));\n    }\n\n    private void showLoading() {\n'''
assert configure_end in text
text = text.replace(configure_end, new_configure_end, 1)

# 2) Page shells no longer own status/navigation insets.
old_root = '''    private LinearLayout rootShell() {\n        LinearLayout root = new LinearLayout(this);\n        root.setOrientation(LinearLayout.VERTICAL);\n        root.setBackgroundColor(BG);\n\n        // Use one stable system-bar measurement for the lifetime of this screen.\n        // Do not request/apply a second WindowInsets pass after setContentView(): that\n        // delayed relayout made the header and the duplicate-check button jump upward\n        // a fraction of a second after entering the room form.\n        // Measure system bars only once for this Activity. Reusing the exact same\n        // values prevents the landing -> room-form content swap from producing a\n        // one-frame vertical offset when WindowMetrics settle.\n        if (stableTopInset < 0) stableTopInset = initialStatusBarInset();\n        if (stableBottomInset < 0) stableBottomInset = initialNavigationBarInset();\n        root.setPadding(0, stableTopInset + dp(4), 0, stableBottomInset + dp(6));\n        return root;\n    }\n'''
new_root = '''    private LinearLayout rootShell() {\n        LinearLayout root = new LinearLayout(this);\n        root.setOrientation(LinearLayout.VERTICAL);\n        root.setBackgroundColor(BG);\n        return root;\n    }\n'''
assert old_root in text
text = text.replace(old_root, new_root, 1)

# 3) In the room form, keep header + room-name/duplicate-check outside the ScrollView.
form_pos = text.index('    private void showRoomForm(boolean create) {')
scroll_start = text.index('        ScrollView scroll = new ScrollView(this);', form_pos)
room_start = text.index('        page.addView(label("방 이름"));', scroll_start)
nickname_start = text.index('        page.addView(label("내 닉네임"));', room_start)
room_block = text[room_start:nickname_start]
room_block = room_block.replace('page.addView(', 'fixedTop.addView(')
room_block = room_block.replace('spacer(page,', 'spacer(fixedTop,')

fixed_intro = '''        LinearLayout fixedTop = bodyPage();\n        fixedTop.setPadding(dp(18), dp(8), dp(18), dp(2));\n'''
scroll_intro = '''        root.addView(fixedTop, new LinearLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));\n\n        ScrollView scroll = new ScrollView(this);\n        scroll.setFillViewport(true);\n        LinearLayout page = bodyPage();\n        page.setPadding(dp(18), dp(6), dp(18), dp(22));\n\n'''
text = text[:scroll_start] + fixed_intro + room_block + scroll_intro + text[nickname_start:]

# Update comments around the room form focus behavior: the root is now persistent.
text = text.replace(
'''        // Do not let the first EditText steal focus during the content-view swap.\n        // Otherwise Android may pan/scroll the new hierarchy a frame after the\n        // room form appears even before the user intentionally starts typing.\n''',
'''        // Start the form without an EditText focus. The fixed top area stays outside\n        // the ScrollView, so IME focus can only resize/scroll the lower form content.\n''',
1)

java_path.write_text(text)

build_path = Path('app/build.gradle')
build = build_path.read_text()
assert 'versionCode 61' in build
assert "versionName '0.3.58-room-form-layout-stability'" in build
build = build.replace('versionCode 61', 'versionCode 62', 1)
build = build.replace(
    "versionName '0.3.58-room-form-layout-stability'",
    "versionName '0.3.59-location-host-refactor'",
    1,
)
build_path.write_text(build)
