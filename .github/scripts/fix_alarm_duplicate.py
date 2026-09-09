from pathlib import Path

p = Path('app/src/main/java/com/yamo/snorelab/AlarmActivity.java')
s = p.read_text()
needle = '''
    @Override public void onBackPressed() {
        if (editorOpen) {
            editorOpen = false;
            showList();
            return;
        }
        super.onBackPressed();
    }
'''
if needle not in s:
    raise SystemExit('inserted duplicate onBackPressed block not found')
s = s.replace(needle, '', 1)
p.write_text(s)
print('removed duplicate AlarmActivity onBackPressed')
