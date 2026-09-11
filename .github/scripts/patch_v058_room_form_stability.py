from pathlib import Path

java_path = Path('app/src/main/java/com/yamo/snorelab/LocationSharingActivity.java')
text = java_path.read_text()

old = '''    private boolean pendingSubmitAfterPermission;\n    private boolean askedActivePermission;\n'''
new = '''    private boolean pendingSubmitAfterPermission;\n    private boolean askedActivePermission;\n    private int stableTopInset = -1;\n    private int stableBottomInset = -1;\n'''
assert old in text
text = text.replace(old, new, 1)

old = '''        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);\n        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;\n'''
new = '''        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);\n        // Keep the window itself stationary when an EditText gains focus. The form's\n        // scroll area may resize for the IME, but the header must never be panned.\n        getWindow().setSoftInputMode(\n                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE\n                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);\n        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;\n'''
assert old in text
text = text.replace(old, new, 1)

old = '''        final int topInset = initialStatusBarInset();\n        final int bottomInset = initialNavigationBarInset();\n        root.setPadding(0, topInset + dp(4), 0, bottomInset + dp(6));\n'''
new = '''        // Measure system bars only once for this Activity. Reusing the exact same\n        // values prevents the landing -> room-form content swap from producing a\n        // one-frame vertical offset when WindowMetrics settle.\n        if (stableTopInset < 0) stableTopInset = initialStatusBarInset();\n        if (stableBottomInset < 0) stableBottomInset = initialNavigationBarInset();\n        root.setPadding(0, stableTopInset + dp(4), 0, stableBottomInset + dp(6));\n'''
assert old in text
text = text.replace(old, new, 1)

old = '''        root.addView(bottom);\n\n        setContentView(root);\n    }\n\n    private TextView intervalChip'''
new = '''        root.addView(bottom);\n\n        // Do not let the first EditText steal focus during the content-view swap.\n        // Otherwise Android may pan/scroll the new hierarchy a frame after the\n        // room form appears even before the user intentionally starts typing.\n        root.setFocusableInTouchMode(true);\n        setContentView(root);\n        root.requestFocus();\n    }\n\n    private TextView intervalChip'''
assert old in text
text = text.replace(old, new, 1)

java_path.write_text(text)

manifest_path = Path('app/src/main/AndroidManifest.xml')
manifest = manifest_path.read_text()
old = '''        <activity android:name=".LocationSharingActivity" android:screenOrientation="portrait" android:exported="false" />'''
new = '''        <activity android:name=".LocationSharingActivity" android:screenOrientation="portrait" android:windowSoftInputMode="stateAlwaysHidden|adjustResize" android:exported="false" />'''
assert old in manifest
manifest = manifest.replace(old, new, 1)
manifest_path.write_text(manifest)

build_path = Path('app/build.gradle')
build = build_path.read_text()
assert "versionCode 60" in build
assert "versionName '0.3.57-room-form-inset-stability'" in build
build = build.replace('versionCode 60', 'versionCode 61', 1)
build = build.replace("versionName '0.3.57-room-form-inset-stability'", "versionName '0.3.58-room-form-layout-stability'", 1)
build_path.write_text(build)

workflow_path = Path('.github/workflows/build-android.yml')
workflow = workflow_path.read_text()
assert 'yamone-app-v0.3.57-room-form-inset-stability-dev-apk' in workflow
workflow = workflow.replace(
    'yamone-app-v0.3.57-room-form-inset-stability-dev-apk',
    'yamone-app-v0.3.58-room-form-layout-stability-dev-apk',
    1,
)
workflow_path.write_text(workflow)
