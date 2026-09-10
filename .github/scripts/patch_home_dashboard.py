from pathlib import Path

p = Path('app/src/main/java/com/yamo/snorelab/MainActivity.java')
s = p.read_text()


def replace_method(source, signature, replacement):
    start = source.find(signature)
    if start < 0:
        raise SystemExit('method not found: ' + signature)
    brace = source.find('{', start)
    depth = 0
    end = None
    for i in range(brace, len(source)):
        if source[i] == '{':
            depth += 1
        elif source[i] == '}':
            depth -= 1
            if depth == 0:
                end = i
                break
    if end is None:
        raise SystemExit('method close not found: ' + signature)
    return source[:start] + replacement + source[end + 1:]

new_show_home = '''private void showHome() {
        detailSession = null;
        screen = "home";
        updateNav();
        content.removeAllViews();

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);
        shell.addView(fixedHeader("홈", "좋은 하루예요! 오늘도 빛나는 당신을 응원해요 💕"),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = bodyPage();
        scroll.addView(page);
        shell.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(shell);

        long[] today = todayActivityTotals();
        long distanceM = today[0];
        long durationMs = today[1];
        long steps = today[2];
        long completed = today[3];

        LinearLayout hero = card();
        hero.setPadding(dp(18), dp(16), dp(18), dp(18));

        LinearLayout heroTop = new LinearLayout(this);
        heroTop.setOrientation(LinearLayout.HORIZONTAL);
        heroTop.setGravity(Gravity.CENTER_VERTICAL);

        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.yamone_home);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(112), dp(112));
        imageParams.rightMargin = dp(12);
        heroTop.addView(image, imageParams);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView small = text("오늘의 활동", 13, PRIMARY2, true);
        words.addView(small);
        TextView stepValue = text(String.format(Locale.KOREAN, "%,d", steps), 34, TEXT, true);
        stepValue.setPadding(0, dp(2), 0, 0);
        words.addView(stepValue);
        words.addView(text("걸음", 12, MUTED, false));
        TextView cheer = text(steps > 0 ? "오늘도 차곡차곡 쌓이고 있어요 ♡" : "가볍게 첫 걸음을 시작해볼까요? ♡", 11, MUTED, false);
        cheer.setPadding(0, dp(8), 0, 0);
        words.addView(cheer);
        heroTop.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hero.addView(heroTop);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setPadding(0, dp(12), 0, 0);
        metrics.addView(homeMetric("거리", String.format(Locale.KOREAN, "%.2f km", distanceM / 1000.0)),
                new LinearLayout.LayoutParams(0, dp(70), 1f));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(0, dp(70), 1f);
        timeParams.leftMargin = dp(7);
        metrics.addView(homeMetric("활동 시간", homeDuration(durationMs)), timeParams);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(0, dp(70), 1f);
        countParams.leftMargin = dp(7);
        metrics.addView(homeMetric("완료", completed + "회"), countParams);
        hero.addView(metrics);
        page.addView(hero, cardParams());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button activity = actionButton("♡  활동 시작", true, v -> startActivity(new Intent(this, LocationExerciseActivity.class)));
        Button sleep = ghostButton("☾  수면 기록", v -> showSleep());
        actions.addView(activity, new LinearLayout.LayoutParams(0, dp(52), 1f));
        LinearLayout.LayoutParams sleepParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        sleepParams.leftMargin = dp(8);
        actions.addView(sleep, sleepParams);
        LinearLayout.LayoutParams actionRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionRowParams.bottomMargin = dp(12);
        page.addView(actions, actionRowParams);

        TextView note = text("작은 습관이 특별한 하루를 만들어요 ♡", 12, PRIMARY2, true);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(8), 0, dp(8));
        page.addView(note);
    }'''

s = replace_method(s, 'private void showHome()', new_show_home)

anchor = '    private void showPlaceholderScreen(String target) {'
helper = '''    private long[] todayActivityTotals() {
        long distance = 0L;
        long duration = 0L;
        long steps = 0L;
        long completed = 0L;
        String today = new SimpleDateFormat("yyyyMMdd", Locale.KOREAN).format(new Date());
        for (File dir : WalkingStore.listSessions(this)) {
            JSONObject meta = WalkingStore.readMeta(dir);
            if (!"complete".equals(meta.optString("status"))) continue;
            long start = meta.optLong("startEpochMs", 0L);
            if (start <= 0L) continue;
            String day = new SimpleDateFormat("yyyyMMdd", Locale.KOREAN).format(new Date(start));
            if (!today.equals(day)) continue;
            distance += Math.max(0L, meta.optLong("distanceM", 0L));
            duration += Math.max(0L, meta.optLong("durationMs", 0L));
            steps += Math.max(0L, meta.optLong("steps", 0L));
            completed++;
        }
        return new long[]{distance, duration, steps, completed};
    }

    private View homeMetric(String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackground(round(CARD2, 17, 0, 0));
        TextView valueView = text(value, 15, TEXT, true);
        valueView.setGravity(Gravity.CENTER);
        TextView labelView = text(label, 10, MUTED, false);
        labelView.setGravity(Gravity.CENTER);
        labelView.setPadding(0, dp(2), 0, 0);
        box.addView(valueView);
        box.addView(labelView);
        return box;
    }

    private String homeDuration(long ms) {
        long totalMinutes = Math.max(0L, ms / 60000L);
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours > 0L) return hours + "시간 " + minutes + "분";
        return minutes + "분";
    }

'''
idx = s.find(anchor)
if idx < 0:
    raise SystemExit('showPlaceholderScreen anchor not found')
s = s[:idx] + helper + s[idx:]

if 'todayActivityTotals()' not in s or 'homeMetric(' not in s:
    raise SystemExit('home dashboard verification failed')
p.write_text(s)
