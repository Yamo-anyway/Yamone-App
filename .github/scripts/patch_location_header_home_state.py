from pathlib import Path

ROOT = Path('app/src/main/java/com/yamo/snorelab')


def patch(path, old, new, label):
    p = ROOT / path
    text = p.read_text()
    if old not in text:
        raise SystemExit(f'missing {label}')
    p.write_text(text.replace(old, new, 1))

# Main Home: rebuild when returning so location-sharing state is immediately reflected.
patch('MainActivity.java',
'''    @Override protected void onResume() {
        super.onResume();
        lastSleepRecordingUiState = null;
        handler.removeCallbacks(refresher);
        if ("sleep".equals(screen) && detailSession == null) showSleep();
        handler.post(refresher);
    }''',
'''    @Override protected void onResume() {
        super.onResume();
        lastSleepRecordingUiState = null;
        handler.removeCallbacks(refresher);
        if ("sleep".equals(screen) && detailSession == null) showSleep();
        else if ("home".equals(screen)) showHome();
        handler.post(refresher);
    }''',
'main onResume')

patch('MainActivity.java',
'''        String locationLabel = LocationSharingStateStore.isActive(this)
                ? "위치 공유 중 · 방 보기" : "위치 공유";''',
'''        String locationLabel = LocationSharingStateStore.isActive(this)
                ? "위치 공유 중" : "위치 공유";''',
'home location label')

# Landing: remove descriptions under create/join and tighten card height.
patch('LocationSharingActivity.java',
'''        LinearLayout create = locationMenuCard(R.drawable.ic_location_group, "방 만들기", "새로운 방을 만들어 친구를 초대해요", true);
        create.setOnClickListener(v -> showRoomForm(true));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78));''',
'''        LinearLayout create = locationMenuCard(R.drawable.ic_location_group, "방 만들기", "", true);
        create.setOnClickListener(v -> showRoomForm(true));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64));''',
'create room card')

patch('LocationSharingActivity.java',
'''        LinearLayout join = locationMenuCard(R.drawable.ic_location_group, "방 참여하기", "친구가 만든 방에 참여해요", false);
        join.setOnClickListener(v -> showRoomForm(false));
        LinearLayout.LayoutParams jp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78));''',
'''        LinearLayout join = locationMenuCard(R.drawable.ic_location_group, "방 참여하기", "", false);
        join.setOnClickListener(v -> showRoomForm(false));
        LinearLayout.LayoutParams jp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64));''',
'join room card')

# Active sharing: restore fixed back header above the map.
patch('LocationSharingActivity.java',
'''        LinearLayout root = rootShell();

        // The map is the fixed top section. Only the controls/list below it scroll.''',
'''        LinearLayout root = rootShell();
        root.addView(header("위치 공유", ""));

        // The map is the fixed top section. Only the controls/list below it scroll.''',
'active location header')

# Menu card helper: do not create an empty subtitle row.
patch('LocationSharingActivity.java',
'''        words.addView(text(title, 15, TEXT, true));
        TextView sub = text(subtitle, 11, MUTED, false);
        sub.setPadding(0, dp(3), 0, 0);
        words.addView(sub);
        card.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));''',
'''        words.addView(text(title, 15, TEXT, true));
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView sub = text(subtitle, 11, MUTED, false);
            sub.setPadding(0, dp(3), 0, 0);
            words.addView(sub);
        }
        card.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));''',
'location menu subtitle')

# Guards
main = (ROOT / 'MainActivity.java').read_text()
loc = (ROOT / 'LocationSharingActivity.java').read_text()
assert '? "위치 공유 중" : "위치 공유"' in main
assert 'else if ("home".equals(screen)) showHome();' in main
assert 'root.addView(header("위치 공유", ""));' in loc
assert '"방 만들기", "", true' in loc
assert '"방 참여하기", "", false' in loc
assert '새로운 방을 만들어 친구를 초대해요' not in loc
assert '친구가 만든 방에 참여해요' not in loc
