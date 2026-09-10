from pathlib import Path

ROOT = Path('app/src/main/java/com/yamo/snorelab')


def replace_method(path, signature, replacement):
    p = ROOT / path
    s = p.read_text()
    start = s.find(signature)
    if start < 0:
        raise SystemExit(f'{path}: method not found: {signature}')
    brace = s.find('{', start)
    depth = 0
    end = None
    for i in range(brace, len(s)):
        if s[i] == '{':
            depth += 1
        elif s[i] == '}':
            depth -= 1
            if depth == 0:
                end = i
                break
    if end is None:
        raise SystemExit(f'{path}: method close not found')
    p.write_text(s[:start] + replacement + s[end + 1:])


# Exercise overview cards: shared vector component, no long description text.
replace_method(
    'ExerciseActivity.java',
    'private void addCategoryCard(LinearLayout page, String icon, String title, String desc, View.OnClickListener click)',
'''private void addCategoryCard(LinearLayout page, String icon, String title, String desc, View.OnClickListener click) {
        int iconRes;
        if (title.contains("자전거")) iconRes = R.drawable.ic_activity_bike;
        else if (title.contains("스키")) iconRes = R.drawable.ic_activity_ski;
        else iconRes = R.drawable.ic_activity_walkrun;

        LinearLayout card = YamoneActivityEntry.create(this, iconRes, title, click);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(9);
        page.addView(card, params);
    }''')

p = ROOT / 'ExerciseActivity.java'
s = p.read_text()
old = '''        addCategoryCard(page, "🚶  🏃", "걷기 / 러닝", "걷기·러닝 단일 모드 또는 자동 통합모드로 기록합니다.", v -> showWalkRunMenu());
        addCategoryCard(page, "🚴", "자전거", "거리와 현재·평균·최고 속도, 이동 경로를 기록합니다.", v -> showCyclingMenu());
        addCategoryCard(page, "⛷  🏂", "스키 / 스노우보드", "겨울 활동은 지금은 준비된 화면만 보여줍니다.", v -> showSkiPreview());
'''
new = '''        TextView choose = text("활동 선택", 15, TEXT, true);
        choose.setPadding(dp(2), dp(7), 0, dp(10));
        page.addView(choose);
        addCategoryCard(page, "", "걷기 / 러닝", "", v -> showWalkRunMenu());
        addCategoryCard(page, "", "자전거", "", v -> showCyclingMenu());
        addCategoryCard(page, "", "스키 / 스노보드", "", v -> showSkiPreview());
'''
if old not in s:
    raise SystemExit('ExerciseActivity: category call block not found')
p.write_text(s.replace(old, new, 1))

# Hiking entry uses exactly the same visual component; remove the legacy compacting pass.
p = ROOT / 'LocationExerciseActivity.java'
s = p.read_text().replace('        compactActivityEntries(root);\n', '', 1)
p.write_text(s)

replace_method(
    'LocationExerciseActivity.java',
    'private void attachHikingEntry(View root)',
'''private void attachHikingEntry(View root) {
        if (root.findViewWithTag(HIKING_TAG) != null) return;
        TextView skiTitle = findClickableContaining(root, "스키 / 스노보드");
        if (skiTitle == null) skiTitle = findClickableContaining(root, "스키 / 스노우보드");
        if (skiTitle == null) return;
        View skiCard = clickableAncestor(skiTitle, root);
        if (skiCard == null || !(skiCard.getParent() instanceof LinearLayout)) return;
        LinearLayout page = (LinearLayout) skiCard.getParent();
        int index = page.indexOfChild(skiCard);
        if (index < 0) return;

        LinearLayout card = YamoneActivityEntry.create(
                this, R.drawable.ic_activity_hike, "등산 / 트레킹",
                v -> startActivity(new Intent(this, HikingActivity.class)));
        card.setTag(HIKING_TAG);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(9);
        page.addView(card, index, params);
    }''')

# Sanity checks.
exercise = (ROOT / 'ExerciseActivity.java').read_text()
location = (ROOT / 'LocationExerciseActivity.java').read_text()
if 'YamoneActivityEntry.create(this, iconRes, title, click)' not in exercise:
    raise SystemExit('ExerciseActivity: shared activity entry missing')
if 'R.drawable.ic_activity_hike' not in location:
    raise SystemExit('LocationExerciseActivity: hiking icon missing')
if 'compactActivityEntries(root);' in location:
    raise SystemExit('LocationExerciseActivity: old compacting pass still active')
