from pathlib import Path

ROOT = Path('app/src/main/java/com/yamo/snorelab')


def read(name):
    return (ROOT / name).read_text()


def write(name, text):
    (ROOT / name).write_text(text)


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing {label}')
    return text.replace(old, new, 1)


def replace_span(text, start, end, replacement, label):
    a = text.find(start)
    if a < 0:
        raise SystemExit(f'missing start {label}')
    b = text.find(end, a)
    if b < 0:
        raise SystemExit(f'missing end {label}')
    return text[:a] + replacement + text[b:]

# ---------------------------------------------------------------------------
# LocationSharingActivity: fixed map on top, compact controls/status/list.
# ---------------------------------------------------------------------------
s = read('LocationSharingActivity.java')

show_active = '''    private void showActive(JSONObject initial) {
        currentPage = "active";
        activeScreen = true;
        activeRoomTitle = null;
        activeInfo = null;
        activeNetworkHint = null;
        activeStatusHint = null;

        LinearLayout root = rootShell();

        // The map is the fixed top section. Only the controls/list below it scroll.
        sharingMap = new LocationSharingMapView(this);
        sharingMap.setBackground(round(CARD2, 0, 0, 0));
        root.addView(sharingMap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));

        ScrollView scroll = new ScrollView(this);
        activeScroll = scroll;
        LinearLayout page = bodyPage();
        page.setPadding(dp(14), dp(8), dp(14), dp(24));

        // Only the three requested sharing controls: remaining time, extend, stop.
        LinearLayout timeRow = card();
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        timeRow.setPadding(dp(12), dp(8), dp(10), dp(8));

        activeRemaining = text("남은 시간 확인 중…", 13, TEXT, true);
        timeRow.addView(activeRemaining, new LinearLayout.LayoutParams(
                0, dp(42), 1f));

        Button extend = softButton("시간 연장");
        extend.setTextSize(11);
        extend.setOnClickListener(v -> startActivity(new Intent(this, LocationSharingTimeActivity.class)));
        LinearLayout.LayoutParams extendParams = new LinearLayout.LayoutParams(dp(82), dp(40));
        extendParams.leftMargin = dp(6);
        timeRow.addView(extend, extendParams);

        Button leave = dangerButton("공유 중단");
        leave.setTextSize(11);
        leave.setOnClickListener(v -> confirmLeave());
        LinearLayout.LayoutParams leaveParams = new LinearLayout.LayoutParams(dp(82), dp(40));
        leaveParams.leftMargin = dp(6);
        timeRow.addView(leave, leaveParams);
        page.addView(timeRow, cardParams());

        LinearLayout myStatus = card();
        myStatus.setPadding(dp(12), dp(10), dp(12), dp(10));
        myStatus.addView(text("내 상태", 13, TEXT, true));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, dp(7), 0, 0);
        statusNormal = statusChoice("normal", "정상");
        statusContact = statusChoice("contact", "연락 요청");
        statusHelp = statusChoice("help", "도움 필요");
        statusEmergency = statusChoice("emergency", "긴급");
        statusRow.addView(statusNormal, new LinearLayout.LayoutParams(0, dp(38), 1f));
        statusRow.addView(statusContact, new LinearLayout.LayoutParams(0, dp(38), 1.28f));
        statusRow.addView(statusHelp, new LinearLayout.LayoutParams(0, dp(38), 1.28f));
        statusRow.addView(statusEmergency, new LinearLayout.LayoutParams(0, dp(38), 1f));
        myStatus.addView(statusRow);
        page.addView(myStatus, cardParams());

        LinearLayout participants = card();
        participants.setPadding(dp(10), dp(10), dp(10), dp(10));
        participants.addView(text("참여자", 13, TEXT, true));
        participantList = new LinearLayout(this);
        participantList.setOrientation(LinearLayout.VERTICAL);
        participantList.setPadding(0, dp(6), 0, 0);
        participants.addView(participantList);
        page.addView(participants, cardParams());

        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        applySnapshot(initial);
        ensureSharingService();
        scheduleInitialLocationRefreshes();
        scheduleActivePolling();
    }

'''
s = replace_span(s,
                 '    private void showActive(JSONObject initial) {',
                 '    private void confirmLeave() {',
                 show_active,
                 'showActive')

old_apply = '''        LocationSharingStateStore.update(this, roomName, shareUntil, interval, members.length());
        activeRoomTitle.setText(roomName);
        activeRemaining.setText("공유 중 · 남은 시간 " + remainingText(shareUntil));
        activeInfo.setText(String.format(Locale.KOREAN, "참여 %d명 · 내 위치 %s 간격 공유", members.length(), intervalLabel(interval)));
        currentSelfStatus = self == null ? "normal" : self.optString("user_status", "normal");
        styleSelfStatus();
        refreshStatusAlertHint();
        if (sharingMap != null) sharingMap.setMembers(members);
        renderParticipants(members);'''
new_apply = '''        LocationSharingStateStore.update(this, roomName, shareUntil, interval, members.length());
        if (activeRoomTitle != null) activeRoomTitle.setText(roomName);
        if (activeRemaining != null) activeRemaining.setText("남은 시간  " + remainingText(shareUntil));
        if (activeInfo != null) activeInfo.setText(String.format(Locale.KOREAN,
                "참여 %d명 · 내 위치 %s 간격 공유", members.length(), intervalLabel(interval)));
        currentSelfStatus = self == null ? "normal" : self.optString("user_status", "normal");
        styleSelfStatus();
        if (sharingMap != null) sharingMap.setMembers(members);
        renderParticipants(members);'''
s = replace_once(s, old_apply, new_apply, 'applySnapshot active fields')

render_participants = '''    private void renderParticipants(JSONArray members) {
        if (participantList == null) return;
        participantList.removeAllViews();
        if (members.length() == 0) {
            participantList.addView(text("참여자 정보를 불러오는 중이에요.", 12, MUTED, false));
            return;
        }

        List<JSONObject> ordered = new ArrayList<>();
        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.optJSONObject(i);
            if (member != null) ordered.add(member);
        }
        ordered.sort((a, b) -> {
            boolean aSelf = a.optBoolean("is_self", false);
            boolean bSelf = b.optBoolean("is_self", false);
            if (aSelf != bSelf) return aSelf ? -1 : 1;
            String an = a.optString("nickname", "사용자").trim();
            String bn = b.optString("nickname", "사용자").trim();
            int ci = an.compareToIgnoreCase(bn);
            return ci != 0 ? ci : an.compareTo(bn);
        });

        for (int i = 0; i < ordered.size(); i++) {
            JSONObject member = ordered.get(i);
            boolean self = member.optBoolean("is_self", false);
            String nickname = member.optString("nickname", "사용자");
            String userStatus = member.optString("user_status", "normal");
            String lastLocationAt = member.optString("last_location_at", "");

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(9), dp(5), dp(9), dp(5));
            row.setBackground(round(CARD2, 12, 0, 0));

            View dot = new View(this);
            dot.setBackground(statusDotDrawable(userStatus, false));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(11), dp(11));
            dotParams.rightMargin = dp(8);
            row.addView(dot, dotParams);

            TextView name = text(nickname + (self ? "  (나)" : ""), 12, TEXT, self);
            name.setSingleLine(true);
            row.addView(name, new LinearLayout.LayoutParams(0, dp(34), 1f));

            String age = ageText(lastLocationAt);
            TextView ageView = text(age.isEmpty() ? "위치 대기" : age, 11, MUTED, false);
            ageView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            ageView.setSingleLine(true);
            row.addView(ageView, new LinearLayout.LayoutParams(dp(76), dp(34)));

            final String memberId = member.optString("member_id", "");
            final String memberNickname = nickname;
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                if (sharingMap == null) return;
                if (!sharingMap.moveToMember(memberId, memberNickname)) {
                    toast(memberNickname + "님의 위치를 아직 받지 못했어요.");
                }
            });

            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
            if (i > 0) rp.topMargin = dp(3);
            participantList.addView(row, rp);
        }
    }

'''
s = replace_span(s,
                 '    private void renderParticipants(JSONArray members) {',
                 '    private TextView statusChoice(String key, String label) {',
                 render_participants,
                 'renderParticipants')

status_choice = '''    private TextView statusChoice(String key, String label) {
        TextView choice = text(label, 11, TEXT, false);
        choice.setGravity(Gravity.CENTER);
        choice.setCompoundDrawablePadding(dp(5));
        choice.setPadding(dp(2), 0, dp(2), 0);
        choice.setBackground(null);
        choice.setOnClickListener(v -> requestStatusChange(key));
        return choice;
    }

'''
s = replace_span(s,
                 '    private TextView statusChoice(String key, String label) {',
                 '    private void requestStatusChange(String status) {',
                 status_choice,
                 'statusChoice')

style_status = '''    private void styleStatusChoice(TextView choice, String status) {
        if (choice == null) return;
        boolean selected = status.equals(currentSelfStatus);
        int color = LocationStatusPalette.color(status);
        choice.setTextColor(selected ? color : TEXT);
        choice.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        choice.setAlpha(selected ? 1f : 0.78f);
        GradientDrawable dot = statusDotDrawable(status, selected);
        int dotSize = dp(selected ? 14 : 11);
        dot.setBounds(0, 0, dotSize, dotSize);
        choice.setCompoundDrawables(dot, null, null, null);
        choice.setBackground(null);
    }

    private GradientDrawable statusDotDrawable(String status, boolean selected) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(LocationStatusPalette.color(status));
        if (selected) dot.setStroke(dp(2), LocationStatusPalette.color(status));
        return dot;
    }

    private int userStatusColor(String status, boolean self, String connectionState) {
        return LocationStatusPalette.color(status);
    }

    private int userStatusBackground(String status) {
        return LocationStatusPalette.softColor(status);
    }

    private String userStatusLabel(String status) {
        return LocationStatusPalette.label(status);
    }

'''
s = replace_span(s,
                 '    private void styleStatusChoice(TextView chip, String status) {',
                 '    private String userStatusShortLabel(String status) {',
                 style_status,
                 'status style/palette')

write('LocationSharingActivity.java', s)

# ---------------------------------------------------------------------------
# LocationSharingActivityV2: remove the non-idempotent 5-second location copy
# rewrite that caused "최종 위치" to multiply forever.
# ---------------------------------------------------------------------------
s = read('LocationSharingActivityV2.java')
s = replace_once(s,
'''        replaceStopAction(root);
        polishParticipantLocationLabels(root);
        polishBottomSpacing(root);''',
'''        replaceStopAction(root);
        polishBottomSpacing(root);''',
'V2 remove participant label rewriter')

s = replace_once(s,
'''    private void replaceStopAction(View root) {
        TextView stop = findExact(root, "위치 공유 중단하기");
        if (stop != null) stop.setOnClickListener(v -> showYamoneStopDialog());
    }''',
'''    private void replaceStopAction(View root) {
        TextView stop = findExact(root, "공유 중단");
        if (stop == null) stop = findExact(root, "위치 공유 중단하기");
        if (stop != null) stop.setOnClickListener(v -> showYamoneStopDialog());
    }''',
'V2 stop label')

# Remove the old mutating functions entirely so they cannot accidentally be called later.
start = s.find('    private void polishParticipantLocationLabels(View view) {')
end = s.find('    private void polishBottomSpacing(View root) {', start)
if start < 0 or end < 0:
    raise SystemExit('missing V2 location label helper span')
s = s[:start] + s[end:]
write('LocationSharingActivityV2.java', s)

# ---------------------------------------------------------------------------
# Main / Alarm / Activity bottom navigation uses theme background, not white.
# ---------------------------------------------------------------------------
s = read('MainActivity.java')
s = replace_once(s, '        nav.setBackgroundColor(CARD);', '        nav.setBackgroundColor(BG);', 'Main nav background')
write('MainActivity.java', s)

s = read('AlarmActivity.java')
s = replace_once(s, '        nav.setBackgroundColor(CARD);', '        nav.setBackgroundColor(BG);', 'Alarm nav background')
write('AlarmActivity.java', s)

s = read('ExerciseActivity.java')
s = replace_once(s,
'''        nav.setBackgroundColor(0xFFFFFFFF);''',
'''        nav.setBackgroundColor(bottomNavBackground());''',
'Exercise nav background')

old_nav_item = '''        return YamoneBottomNav.create(this, icon, clean, color == PRIMARY2, PRIMARY2, MUTED, CARD2, click);
    }
    private TextView text(String s, int sp, int color, boolean bold)'''
new_nav_item = '''        return YamoneBottomNav.create(this, icon, clean, color == PRIMARY2,
                bottomNavActive(), bottomNavMuted(), bottomNavSelectedBackground(), click);
    }

    private boolean pinkBottomNav() {
        return "pink".equals(getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "pink"));
    }
    private int bottomNavBackground() { return pinkBottomNav() ? 0xFFFFF7FA : 0xFFF7FFFB; }
    private int bottomNavSelectedBackground() { return pinkBottomNav() ? 0xFFFFE3EC : 0xFFDFF8F0; }
    private int bottomNavActive() { return pinkBottomNav() ? 0xFFE94778 : 0xFF159A7A; }
    private int bottomNavMuted() { return pinkBottomNav() ? 0xFF9A7180 : 0xFF718984; }

    private TextView text(String s, int sp, int color, boolean bold)'''
s = replace_once(s, old_nav_item, new_nav_item, 'Exercise themed nav palette')
write('ExerciseActivity.java', s)

# Guards
checks = {
    'LocationSharingActivity.java': [
        'root.addView(sharingMap',
        'Button extend = softButton("시간 연장")',
        'Button leave = dangerButton("공유 중단")',
        'ordered.sort((a, b) ->',
        'statusDotDrawable(userStatus, false)',
        'LocationStatusPalette.color(status)',
    ],
    'LocationSharingActivityV2.java': [
        'TextView stop = findExact(root, "공유 중단")',
    ],
    'MainActivity.java': ['nav.setBackgroundColor(BG);'],
    'AlarmActivity.java': ['nav.setBackgroundColor(BG);'],
    'ExerciseActivity.java': ['nav.setBackgroundColor(bottomNavBackground());', 'bottomNavSelectedBackground()'],
}
for name, tokens in checks.items():
    text = read(name)
    for token in tokens:
        if token not in text:
            raise SystemExit(f'guard failed {name}: {token}')

if 'polishParticipantLocationLabels(root)' in read('LocationSharingActivityV2.java'):
    raise SystemExit('old multiplying location-label polish call remained')
