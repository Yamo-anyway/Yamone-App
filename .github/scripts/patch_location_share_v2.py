from pathlib import Path
import re

path = Path('app/src/main/java/com/yamo/snorelab/LocationSharingActivityV2.java')
s = path.read_text()

s = s.replace('    private boolean immediateReportAttempted;\n', '')
s = s.replace('        replaceNicknameAction(root);\n', '')
s = s.replace('        maybeSendImmediateLocation(root);\n', '')
s = s.replace('        addBottomMargin(findExact(root, "내 닉네임 변경"), 10);\n', '')
s = s.replace('                        immediateReportAttempted = false;\n', '')

for method_name in ['replaceNicknameAction', 'maybeSendImmediateLocation', 'newer', 'reportLocationQuietly', 'showYamoneNicknameDialog']:
    pattern = re.compile(r'\n    private [^{\n]+\b' + re.escape(method_name) + r'\([^)]*\) \{.*?\n    \}(?=\n\n    private |\n\})', re.S)
    m = pattern.search(s)
    if m:
        s = s[:m.start()] + s[m.end():]

# Strong verification for the removed nickname/legacy immediate-report paths.
for token in ['내 닉네임 변경', 'showYamoneNicknameDialog', 'updateNickname(', 'replaceNicknameAction',
              'maybeSendImmediateLocation', 'immediateReportAttempted', 'reportLocationQuietly']:
    if token in s:
        raise SystemExit('remaining token: ' + token)

path.write_text(s)
