from pathlib import Path

p = Path('app/src/main/java/com/yamo/snorelab/MainActivity.java')
s = p.read_text()
old = '''TextView hint = text("화면을 꺼도 계속 측정해요.
편안하게 좋은 꿈 꾸세요 ♡", 12, MUTED, false);'''
new = 'TextView hint = text("화면을 꺼도 계속 측정해요.\\n편안하게 좋은 꿈 꾸세요 ♡", 12, MUTED, false);'
if old not in s:
    raise SystemExit('broken sleep hint string not found')
p.write_text(s.replace(old, new, 1))
