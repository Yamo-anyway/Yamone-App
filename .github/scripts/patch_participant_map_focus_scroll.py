from pathlib import Path
p = Path('app/src/main/java/com/yamo/snorelab/LocationSharingActivity.java')
s = p.read_text()

def rep(old, new, label):
    global s
    if old not in s:
        raise SystemExit('missing ' + label)
    s = s.replace(old, new, 1)

rep('''    private LinearLayout participantList;
    private LocationSharingMapView sharingMap;''', '''    private LinearLayout participantList;
    private LocationSharingMapView sharingMap;
    private ScrollView activeScroll;''', 'active scroll field')

rep('''        ScrollView scroll = new ScrollView(this);
        LinearLayout page = bodyPage();
        page.setPadding(dp(14), dp(6), dp(14), dp(28));''', '''        ScrollView scroll = new ScrollView(this);
        activeScroll = scroll;
        LinearLayout page = bodyPage();
        page.setPadding(dp(14), dp(6), dp(14), dp(28));''', 'active scroll assign')

rep('''        pHead.addView(text("참여자", 15, TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        participants.addView(pHead);
        participantList = new LinearLayout(this);''', '''        pHead.addView(text("참여자", 15, TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        participants.addView(pHead);
        TextView participantHint = text("사용자를 누르면 지도에서 위치를 확인할 수 있어요.", 11, MUTED, false);
        participantHint.setPadding(0, dp(3), 0, 0);
        participants.addView(participantHint);
        participantList = new LinearLayout(this);''', 'participant hint')

rep('''            row.setOnClickListener(v -> {
                if (sharingMap == null) return;
                if (!sharingMap.moveToMember(memberId, memberNickname)) {
                    toast(memberNickname + "님의 위치를 아직 받지 못했어요.");
                }
            });''', '''            row.setOnClickListener(v -> {
                if (sharingMap == null) return;
                if (!sharingMap.moveToMember(memberId, memberNickname)) {
                    toast(memberNickname + "님의 위치를 아직 받지 못했어요.");
                    return;
                }
                if (activeScroll != null) {
                    activeScroll.post(() -> activeScroll.smoothScrollTo(0, Math.max(0, sharingMap.getTop() - dp(10))));
                }
            });''', 'participant click scroll')

p.write_text(s)
