from pathlib import Path

path = Path('app/src/main/java/com/yamo/snorelab/LocationSharingActivity.java')
text = path.read_text(encoding='utf-8')

old = '''    private LinearLayout rootShell() {\n        LinearLayout root = new LinearLayout(this);\n        root.setOrientation(LinearLayout.VERTICAL);\n        root.setBackgroundColor(BG);\n\n        // Reserve system-bar space synchronously before the first frame. Previously the\n        // root started with only 8dp bottom padding and waited for onApplyWindowInsets,\n        // which made the fixed bottom CTA appear clipped and then jump upward later.\n        final int initialTop = initialStatusBarInset();\n        final int initialBottom = initialNavigationBarInset();\n        root.setPadding(0, initialTop + dp(4), 0, initialBottom + dp(6));\n\n        if (Build.VERSION.SDK_INT >= 21) {\n            root.setOnApplyWindowInsetsListener((v, insets) -> {\n                int top;\n                int bottom;\n                if (Build.VERSION.SDK_INT >= 30) {\n                    top = insets.getInsets(WindowInsets.Type.statusBars()).top;\n                    bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;\n                } else {\n                    top = insets.getSystemWindowInsetTop();\n                    bottom = insets.getSystemWindowInsetBottom();\n                }\n                top = Math.max(top, initialTop);\n                bottom = Math.max(bottom, initialBottom);\n                v.setPadding(0, top + dp(4), 0, bottom + dp(6));\n                return insets;\n            });\n            root.post(root::requestApplyInsets);\n        }\n        return root;\n    }\n'''

new = '''    private LinearLayout rootShell() {\n        LinearLayout root = new LinearLayout(this);\n        root.setOrientation(LinearLayout.VERTICAL);\n        root.setBackgroundColor(BG);\n\n        // Use one stable system-bar measurement for the lifetime of this screen.\n        // Do not request/apply a second WindowInsets pass after setContentView(): that\n        // delayed relayout made the header and the duplicate-check button jump upward\n        // a fraction of a second after entering the room form.\n        final int topInset = initialStatusBarInset();\n        final int bottomInset = initialNavigationBarInset();\n        root.setPadding(0, topInset + dp(4), 0, bottomInset + dp(6));\n        return root;\n    }\n'''

if old not in text:
    raise SystemExit('rootShell block not found')

text = text.replace(old, new, 1)
path.write_text(text, encoding='utf-8')
print('patched LocationSharingActivity to use static insets')
