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

new_show_sleep = '''private void showSleep() {
        screen = "sleep";
        updateNav();
        if (detailSession != null) { showSessionDetail(detailSession); return; }
        content.removeAllViews();

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);
        shell.addView(fixedHeader("수면", "잘 자는 것이, 더 좋은 나를 만들어요. 💕"),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = bodyPage();
        scroll.addView(page);
        shell.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(shell);

        boolean recording = prefs.getBoolean(SleepRecorderService.KEY_RECORDING, false);
        if (recording) {
            long start = prefs.getLong(SleepRecorderService.KEY_START_MS, System.currentTimeMillis());
            LinearLayout live = card();
            live.setGravity(Gravity.CENTER_HORIZONTAL);
            live.setPadding(dp(20), dp(22), dp(20), dp(22));

            ImageView icon = new ImageView(this);
            icon.setImageResource(R.drawable.ic_nav_sleep);
            icon.setColorFilter(PRIMARY2);
            icon.setPadding(dp(14), dp(14), dp(14), dp(14));
            icon.setBackground(round(CARD2, 31, 0, 0));
            live.addView(icon, new LinearLayout.LayoutParams(dp(62), dp(62)));

            TextView state = text("수면 측정 중", 17, PRIMARY2, true);
            state.setGravity(Gravity.CENTER);
            state.setPadding(0, dp(10), 0, 0);
            live.addView(state);

            TextView elapsed = text(formatClockDuration(System.currentTimeMillis() - start), 38, TEXT, true);
            elapsed.setGravity(Gravity.CENTER);
            elapsed.setPadding(0, dp(5), 0, dp(5));
            live.addView(elapsed);

            TextView hint = text("화면을 꺼도 계속 측정해요.\n편안하게 좋은 꿈 꾸세요 ♡", 12, MUTED, false);
            hint.setGravity(Gravity.CENTER);
            live.addView(hint);

            Button stop = actionButton("■  측정 종료", true, v -> stopMeasurement());
            LinearLayout.LayoutParams sp = match(dp(56));
            sp.topMargin = dp(18);
            live.addView(stop, sp);
            page.addView(live, cardParams());
            return;
        }

        List<File> sessions = SessionStore.listSessions(this);
        LinearLayout hero = card();
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_nav_sleep);
        icon.setColorFilter(PRIMARY2);
        icon.setPadding(dp(15), dp(15), dp(15), dp(15));
        icon.setBackground(round(CARD2, 33, 0, 0));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(66), dp(66));
        iconParams.rightMargin = dp(14);
        top.addView(icon, iconParams);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text("오늘도 편안한 밤 되세요", 19, TEXT, true));
        TextView cheer = text("야모네가 조용히 수면을 기록해드릴게요 ♡", 11, MUTED, false);
        cheer.setPadding(0, dp(5), 0, 0);
        words.addView(cheer);
        top.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hero.addView(top);

        if (!sessions.isEmpty()) {
            File last = sessions.get(0);
            JSONObject meta = SessionStore.readMeta(last);
            JSONArray events = SessionStore.readEvents(last);
            long duration = meta.optLong("durationMs", 0);
            int confirmed = meta.optInt("snoreConfirmedCount", 0);

            TextView recent = text("최근 수면  ·  " + SessionStore.formatLocalDateTime(meta.optLong("startEpochMs", 0)), 11, PRIMARY2, true);
            recent.setPadding(0, dp(16), 0, dp(8));
            hero.addView(recent);

            LinearLayout metrics = new LinearLayout(this);
            metrics.setOrientation(LinearLayout.HORIZONTAL);
            metrics.addView(sleepMetric("수면 시간", formatDuration(duration)),
                    new LinearLayout.LayoutParams(0, dp(72), 1f));
            LinearLayout.LayoutParams candidateParams = new LinearLayout.LayoutParams(0, dp(72), 1f);
            candidateParams.leftMargin = dp(7);
            metrics.addView(sleepMetric("코골이 후보", events.length() + "건"), candidateParams);
            LinearLayout.LayoutParams confirmedParams = new LinearLayout.LayoutParams(0, dp(72), 1f);
            confirmedParams.leftMargin = dp(7);
            metrics.addView(sleepMetric("확정", confirmed + "건"), confirmedParams);
            hero.addView(metrics);

            TextView open = text("최근 수면 결과 보기  ›", 11, PRIMARY2, true);
            open.setGravity(Gravity.RIGHT);
            open.setPadding(0, dp(10), 0, 0);
            hero.addView(open);
            hero.setOnClickListener(v -> { detailSession = last; showSessionDetail(last); });
        } else {
            TextView first = text("아직 수면 기록이 없어요. 첫 기록을 시작해보세요.", 12, MUTED, false);
            first.setPadding(0, dp(16), 0, 0);
            hero.addView(first);
        }
        page.addView(hero, cardParams());

        Button startButton = actionButton("수면 측정 시작", true, v -> ensureMicAndStart());
        LinearLayout.LayoutParams startParams = match(dp(58));
        startParams.bottomMargin = dp(8);
        page.addView(startButton, startParams);

        TextView guide = text("마이크는 수면 소리 분석에만 사용하며, 기록은 휴대폰에 저장돼요.", 10, MUTED, false);
        guide.setGravity(Gravity.CENTER);
        guide.setPadding(0, dp(2), 0, dp(16));
        page.addView(guide);

        if (!sessions.isEmpty()) {
            TextView h = text("최근 기록", 16, TEXT, true);
            h.setPadding(dp(2), dp(4), 0, dp(10));
            page.addView(h);
            for (int i = 0; i < Math.min(6, sessions.size()); i++) {
                page.addView(sessionRow(sessions.get(i)), cardParamsCompact());
            }
        }

        LinearLayout privacy = card();
        privacy.addView(text("수면 기록은 내 휴대폰에", 14, TEXT, true));
        TextView p = text("녹음과 분석 기록은 앱 내부에 저장하며 자동 업로드하지 않습니다.", 11, MUTED, false);
        p.setPadding(0, dp(6), 0, 0);
        privacy.addView(p);
        LinearLayout.LayoutParams pp = cardParams();
        pp.topMargin = dp(8);
        page.addView(privacy, pp);
    }'''

new_session_row = '''private View sessionRow(File dir) {
        JSONObject meta = SessionStore.readMeta(dir);
        JSONArray events = SessionStore.readEvents(dir);
        LinearLayout c = card();
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_nav_sleep);
        icon.setColorFilter(PRIMARY2);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(round(CARD2, 22, 0, 0));
        c.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(dp(12), 0, 0, 0);
        left.addView(text(SessionStore.formatLocalDateTime(meta.optLong("startEpochMs", 0)), 13, TEXT, true));
        TextView summary = text(formatDuration(meta.optLong("durationMs", 0)) + "  ·  후보 " + events.length() + "건", 11, MUTED, false);
        summary.setPadding(0, dp(3), 0, 0);
        left.addView(summary);
        c.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = text("›", 26, PRIMARY2, false);
        arrow.setGravity(Gravity.CENTER);
        arrow.setBackground(round(CARD2, 18, 0, 0));
        c.addView(arrow, new LinearLayout.LayoutParams(dp(36), dp(36)));
        c.setOnClickListener(v -> { detailSession = dir; showSessionDetail(dir); });
        return c;
    }'''

s = replace_method(s, 'private void showSleep()', new_show_sleep)
s = replace_method(s, 'private View sessionRow(File dir)', new_session_row)

anchor = '    private View sessionRow(File dir) {'
helper = '''    private View sleepMetric(String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackground(round(CARD2, 17, 0, 0));
        TextView v = text(value, 14, TEXT, true);
        v.setGravity(Gravity.CENTER);
        TextView l = text(label, 10, MUTED, false);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, dp(2), 0, 0);
        box.addView(v);
        box.addView(l);
        return box;
    }\n\n'''
idx = s.find(anchor)
if idx < 0:
    raise SystemExit('sessionRow anchor not found')
s = s[:idx] + helper + s[idx:]

if 'private View sleepMetric' not in s or 'R.drawable.ic_nav_sleep' not in s:
    raise SystemExit('sleep polish verification failed')
p.write_text(s)
