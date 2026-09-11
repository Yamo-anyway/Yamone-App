from pathlib import Path

path = Path('app/src/main/java/com/yamo/snorelab/LocationSharingActivity.java')
text = path.read_text(encoding='utf-8')

old = '''        LinearLayout roomRow = new LinearLayout(this);\n        roomRow.setOrientation(LinearLayout.HORIZONTAL);\n'''
new = '''        LinearLayout roomRow = new LinearLayout(this);\n        roomRow.setOrientation(LinearLayout.HORIZONTAL);\n        roomRow.setGravity(Gravity.CENTER_VERTICAL);\n'''
if old not in text:
    raise SystemExit('roomRow block not found')
text = text.replace(old, new, 1)

old = '''        availabilityButton = smallButton("중복 확인");\n        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(96), dp(52));\n        checkParams.leftMargin = dp(8);\n'''
new = '''        availabilityButton = smallButton("중복 확인");\n        availabilityButton.setTextSize(13);\n        availabilityButton.setGravity(Gravity.CENTER);\n        availabilityButton.setIncludeFontPadding(false);\n        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(96), dp(46));\n        checkParams.leftMargin = dp(8);\n'''
if old not in text:
    raise SystemExit('availability button block not found')
text = text.replace(old, new, 1)

path.write_text(text, encoding='utf-8')
print('patched duplicate check button sizing')
