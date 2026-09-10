from pathlib import Path

ROOT = Path('app/src/main/java/com/yamo/snorelab')


def read(name):
    return (ROOT / name).read_text()


def write(name, text):
    (ROOT / name).write_text(text)


def rep(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing {label}: {old[:80]}')
    return text.replace(old, new, 1)

# Keep the default theme pink consistently in sub-screens that read the shared preference directly.
for p in ROOT.glob('*.java'):
    s = p.read_text()
    s2 = s.replace('getString("yamone_theme", "mint")', 'getString("yamone_theme", "pink")')
    if s2 != s:
        p.write_text(s2)

# Hiking: replace old emoji/character UI with existing Yamone vector assets.
s = read('HikingActivity.java')
if 'import android.widget.ImageView;' not in s:
    s = s.replace('import android.widget.Button;\n', 'import android.widget.Button;\nimport android.widget.ImageView;\n', 1)
s = rep(s,
'''        TextView icon = text("🥾  ⛰️", 38, primary2(), false);\n        icon.setGravity(Gravity.CENTER);\n        hero.addView(icon);''',
'''        ImageView icon = new ImageView(this);\n        icon.setImageResource(R.drawable.ic_activity_hike);\n        icon.setColorFilter(primary2());\n        icon.setPadding(dp(14), dp(14), dp(14), dp(14));\n        icon.setBackground(round(card2(), 30, 0, 0));\n        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(60), dp(60));\n        iconParams.gravity = Gravity.CENTER_HORIZONTAL;\n        hero.addView(icon, iconParams);''', 'hiking ready icon')
s = s.replace('primaryButton("▶  등산 / 트레킹 기록 시작")', 'primaryButton("등산 / 트레킹 기록 시작")')
s = rep(s,
'''        TextView icon = text("⛰️", 24, primary2(), false);\n        icon.setGravity(Gravity.CENTER);\n        row.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));''',
'''        ImageView icon = new ImageView(this);\n        icon.setImageResource(R.drawable.ic_activity_hike);\n        icon.setColorFilter(primary2());\n        icon.setPadding(dp(10), dp(10), dp(10), dp(10));\n        icon.setBackground(round(card2(), 22, 0, 0));\n        row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));''', 'hiking record icon')
s = rep(s,
'''        TextView arrow = text("›", 27, primary2(), false);\n        arrow.setGravity(Gravity.CENTER);\n        row.addView(arrow, new LinearLayout.LayoutParams(dp(30), dp(46)));''',
'''        ImageView arrow = new ImageView(this);\n        arrow.setImageResource(R.drawable.ic_yamone_chevron_right);\n        arrow.setColorFilter(primary2());\n        arrow.setPadding(dp(9), dp(9), dp(9), dp(9));\n        arrow.setBackground(round(card2(), 18, 0, 0));\n        row.addView(arrow, new LinearLayout.LayoutParams(dp(36), dp(36)));''', 'hiking chevron')
s = s.replace('dangerButton("■  등산 / 트레킹 기록 종료")', 'dangerButton("등산 / 트레킹 기록 종료")')
s = s.replace('text("🔒 등산 / 트레킹 경로는 내 휴대폰에", 14, textColor(), true)', 'text("등산 / 트레킹 경로는 내 휴대폰에", 14, textColor(), true)')
write('HikingActivity.java', s)

# Main sleep recent row: remove text chevron and use the common vector chevron.
s = read('MainActivity.java')
s = s.replace('text("최근 수면 결과 보기  ›", 11, PRIMARY2, true)', 'text("최근 수면 결과 보기", 11, PRIMARY2, true)')
s = rep(s,
'''        TextView arrow = text("›", 26, PRIMARY2, false);\n        arrow.setGravity(Gravity.CENTER);\n        arrow.setBackground(round(CARD2, 18, 0, 0));\n        c.addView(arrow, new LinearLayout.LayoutParams(dp(36), dp(36)));''',
'''        ImageView arrow = new ImageView(this);\n        arrow.setImageResource(R.drawable.ic_yamone_chevron_right);\n        arrow.setColorFilter(PRIMARY2);\n        arrow.setPadding(dp(9), dp(9), dp(9), dp(9));\n        arrow.setBackground(round(CARD2, 18, 0, 0));\n        c.addView(arrow, new LinearLayout.LayoutParams(dp(36), dp(36)));''', 'sleep recent chevron')
write('MainActivity.java', s)

# Ski overview: bring the old dark screen into the Yamone pink/white family.
s = read('SkiActivity.java')
colors = {
    'private static final int BG = 0xFF0B1324;': 'private static final int BG = 0xFFFFF7FA;',
    'private static final int CARD = 0xFF16243B;': 'private static final int CARD = 0xFFFFFFFF;',
    'private static final int CARD2 = 0xFF111C31;': 'private static final int CARD2 = 0xFFFFEEF3;',
    'private static final int TEXT = 0xFFF5F7FF;': 'private static final int TEXT = 0xFF4B2633;',
    'private static final int MUTED = 0xFF9DA9BF;': 'private static final int MUTED = 0xFF9A7180;',
    'private static final int PRIMARY = 0xFF6D72FF;': 'private static final int PRIMARY = 0xFFFF769F;',
    'private static final int PRIMARY2 = 0xFF8B8FFF;': 'private static final int PRIMARY2 = 0xFFE94778;',
    'private static final int SUCCESS = 0xFF61D6A8;': 'private static final int SUCCESS = 0xFF36A57D;',
    'private static final int WARNING = 0xFFFFC56D;': 'private static final int WARNING = 0xFFE9A642;'
}
for old, new in colors.items():
    s = rep(s, old, new, 'ski color')
s = rep(s, 'getWindow().setNavigationBarColor(BG);\n        buildRoot();',
'''getWindow().setNavigationBarColor(BG);\n        if (Build.VERSION.SDK_INT >= 23) {\n            int flags = getWindow().getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;\n            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;\n            getWindow().getDecorView().setSystemUiVisibility(flags);\n        }\n        buildRoot();''', 'ski system bars')
s = s.replace('choiceButton("⛷  스키"', 'choiceButton("스키"')
s = s.replace('choiceButton("🏂  스노보드"', 'choiceButton("스노보드"')
s = s.replace('actionButton("▶ " +', 'actionButton(')
s = s.replace(' + " 기록 시작", true, v -> startSession())', ' + " 기록 시작", true, v -> startSession())')
s = s.replace('"📍 " + resort', '"현재 스키장  " + resort')
s = s.replace('actionButton("■ 기록 종료"', 'actionButton("기록 종료"')
s = s.replace('String sport = "snowboard".equals(m.optString("sport")) ? "🏂 스노보드" : "⛷ 스키";', 'String sport = "snowboard".equals(m.optString("sport")) ? "스노보드" : "스키";')
s = rep(s,
'''            TextView arrow = text("›", 26, PRIMARY2, false); arrow.setGravity(Gravity.CENTER);\n            row.addView(arrow, new LinearLayout.LayoutParams(dp(30), dp(50)));''',
'''            ImageView arrow = new ImageView(this);\n            arrow.setImageResource(R.drawable.ic_yamone_chevron_right);\n            arrow.setColorFilter(PRIMARY2);\n            arrow.setPadding(dp(8), dp(8), dp(8), dp(8));\n            arrow.setBackground(rounded(CARD2, 18, 0, 0));\n            row.addView(arrow, new LinearLayout.LayoutParams(dp(36), dp(36)));''', 'ski recent chevron')
s = s.replace('text("🚡 리프트 정보", 16, TEXT, true)', 'text("리프트 정보", 16, TEXT, true)')
s = s.replace('text("🔒 스키 기록 원칙", 14, TEXT, true)', 'text("스키 기록 원칙", 14, TEXT, true)')
s = s.replace('return "⛷ 활주 중";', 'return "활주 중";')
s = s.replace('return "🚡 리프트 이동";', 'return "리프트 이동";')
s = rep(s,
'''        Button b = new Button(this); b.setAllCaps(false); b.setText(label); b.setTextColor(TEXT); b.setTextSize(13); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);\n        b.setBackground(rounded(selected ? PRIMARY : CARD2, 14, selected ? 0 : 1, 0xFF35445F)); b.setOnClickListener(click); return b;''',
'''        Button b = new Button(this); b.setAllCaps(false); b.setText(label); b.setTextColor(selected ? Color.WHITE : TEXT); b.setTextSize(13); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);\n        b.setBackground(rounded(selected ? PRIMARY2 : CARD2, 16, selected ? 0 : 1, 0xFFFFD7E3)); b.setOnClickListener(click); return b;''', 'ski choice style')
s = s.replace('rounded(primary ? PRIMARY : 0xFF33425B, 16, 0, 0)', 'rounded(primary ? PRIMARY2 : 0xFFE75B6D, 18, 0, 0)')
s = s.replace('rounded(CARD2, 13, 1, 0xFF35445F)', 'rounded(CARD2, 16, 1, 0xFFFFD7E3)')
s = s.replace('rounded(CARD, 18, 0, 0)', 'rounded(CARD, 22, 1, 0xFFFFE3EC)')
write('SkiActivity.java', s)

# Ski detail: same light palette and no emoji headings.
s = read('SkiSessionDetailActivity.java')
if 'import android.os.Build;' not in s:
    s = s.replace('import android.os.Bundle;\n', 'import android.os.Build;\nimport android.os.Bundle;\n', 1)
colors2 = {
    'private static final int BG = 0xFF0B1324;': 'private static final int BG = 0xFFFFF7FA;',
    'private static final int CARD = 0xFF16243B;': 'private static final int CARD = 0xFFFFFFFF;',
    'private static final int CARD2 = 0xFF111C31;': 'private static final int CARD2 = 0xFFFFEEF3;',
    'private static final int TEXT = 0xFFF5F7FF;': 'private static final int TEXT = 0xFF4B2633;',
    'private static final int MUTED = 0xFF9DA9BF;': 'private static final int MUTED = 0xFF9A7180;',
    'private static final int PRIMARY2 = 0xFF8B8FFF;': 'private static final int PRIMARY2 = 0xFFE94778;',
    'private static final int SUCCESS = 0xFF61D6A8;': 'private static final int SUCCESS = 0xFF36A57D;'
}
for old, new in colors2.items():
    s = rep(s, old, new, 'ski detail color')
s = rep(s, 'getWindow().setNavigationBarColor(BG);\n        sessionDir =',
'''getWindow().setNavigationBarColor(BG);\n        if (Build.VERSION.SDK_INT >= 23) {\n            int flags = getWindow().getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;\n            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;\n            getWindow().getDecorView().setSystemUiVisibility(flags);\n        }\n        sessionDir =''', 'ski detail system bars')
s = s.replace('String icon = "스노보드".equals(sport) ? "🏂" : "⛷";\n\n        page.addView(YamoneBackHeader.create(this, icon + " " + sport + " 기록", null,', 'page.addView(YamoneBackHeader.create(this, sport + " 기록", null,')
s = s.replace('text("⛷ 활주 기록", 15, TEXT, true)', 'text("활주 기록", 15, TEXT, true)')
s = s.replace('text("🚡 리프트 / 대기 기록", 15, TEXT, true)', 'text("리프트 / 대기 기록", 15, TEXT, true)')
s = s.replace('text("🚡 " + liftName + " · " + time, 13, TEXT, true)', 'text(liftName + " · " + time, 13, TEXT, true)')
s = s.replace('round(CARD2, 12, 1, 0xFF35445F)', 'round(CARD2, 16, 1, 0xFFFFD7E3)')
s = s.replace('round(CARD, 18, 0, 0)', 'round(CARD, 22, 1, 0xFFFFE3EC)')
s = s.replace('round(CARD2, 14, 1, 0xFF2F405C)', 'round(CARD2, 16, 1, 0xFFFFD7E3)')
s = s.replace('round(CARD2, 12, 1, 0xFF35445F)', 'round(CARD2, 16, 1, 0xFFFFD7E3)')
write('SkiSessionDetailActivity.java', s)

# Ski wait list: same palette and plain vector-era labels.
s = read('SkiWaitTimesActivity.java')
if 'import android.os.Build;' not in s:
    s = s.replace('import android.os.Bundle;\n', 'import android.os.Build;\nimport android.os.Bundle;\n', 1)
colors3 = {
    'private static final int BG = 0xFF0B1324;': 'private static final int BG = 0xFFFFF7FA;',
    'private static final int CARD = 0xFF16243B;': 'private static final int CARD = 0xFFFFFFFF;',
    'private static final int CARD2 = 0xFF111C31;': 'private static final int CARD2 = 0xFFFFEEF3;',
    'private static final int TEXT = 0xFFF5F7FF;': 'private static final int TEXT = 0xFF4B2633;',
    'private static final int MUTED = 0xFF9DA9BF;': 'private static final int MUTED = 0xFF9A7180;',
    'private static final int PRIMARY = 0xFF6D72FF;': 'private static final int PRIMARY = 0xFFFF769F;',
    'private static final int PRIMARY2 = 0xFF8B8FFF;': 'private static final int PRIMARY2 = 0xFFE94778;',
    'private static final int SUCCESS = 0xFF61D6A8;': 'private static final int SUCCESS = 0xFF36A57D;',
    'private static final int WARNING = 0xFFFFC56D;': 'private static final int WARNING = 0xFFE9A642;'
}
for old, new in colors3.items():
    s = rep(s, old, new, 'ski wait color')
s = rep(s, 'getWindow().setNavigationBarColor(BG);\n        resortKey =',
'''getWindow().setNavigationBarColor(BG);\n        if (Build.VERSION.SDK_INT >= 23) {\n            int flags = getWindow().getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;\n            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;\n            getWindow().getDecorView().setSystemUiVisibility(flags);\n        }\n        resortKey =''', 'ski wait system bars')
s = s.replace('mapMode ? "☷ 목록으로 보기" : "🗺 지도로 보기"', 'mapMode ? "목록으로 보기" : "지도로 보기"')
s = s.replace('loading ? "불러오는 중…" : "↻ 새로고침"', 'loading ? "불러오는 중…" : "새로고침"')
s = s.replace('text("🚡 리프트", 15, TEXT, true)', 'text("리프트", 15, TEXT, true)')
s = s.replace('round(CARD, 18, 0, 0)', 'round(CARD, 22, 1, 0xFFFFE3EC)')
s = s.replace('round(CARD2, 13, 1, 0xFF2F405C)', 'round(CARD2, 16, 1, 0xFFFFD7E3)')
s = s.replace('round(CARD2, 13, 1, 0xFF35445F)', 'round(CARD2, 16, 1, 0xFFFFD7E3)')
write('SkiWaitTimesActivity.java', s)

# Sanity checks: no old dark palette and no old chevron remain in the screens touched here.
for name in ['SkiActivity.java', 'SkiSessionDetailActivity.java', 'SkiWaitTimesActivity.java']:
    t = read(name)
    if '0xFF0B1324' in t or '0xFF16243B' in t or '0xFF111C31' in t:
        raise SystemExit('dark ski palette remains in ' + name)
for name in ['HikingActivity.java', 'SkiActivity.java']:
    if 'text("›"' in read(name):
        raise SystemExit('text chevron remains in ' + name)
