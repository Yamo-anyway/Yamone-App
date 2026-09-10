from pathlib import Path

ROOT = Path('app/src/main/java/com/yamo/snorelab')

def read(name): return (ROOT / name).read_text()
def write(name, text): (ROOT / name).write_text(text)
def rep(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing {label}: {old[:180]}')
    return text.replace(old, new, 1)

# Participant rows: tap a member to focus their latest location on the map.
s = read('LocationSharingActivity.java')
old = '''            row.addView(badge, new LinearLayout.LayoutParams(
                    "normal".equals(userStatus) ? dp(24) : ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));

            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);'''
new = '''            row.addView(badge, new LinearLayout.LayoutParams(
                    "normal".equals(userStatus) ? dp(24) : ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));

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

            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);'''
s = rep(s, old, new, 'participant row map focus')
write('LocationSharingActivity.java', s)

# Map: resolve the latest coordinates by member_id at click time and focus them.
s = read('LocationSharingMapView.java')
old = '''    public void moveToSelf() {
        if (map == null) return;
        if (selfLatLng == null) {
            status.setText("아직 내 위치를 받지 못했어요.");
            status.setVisibility(VISIBLE);
            postDelayed(() -> {
                if (map != null && selfLatLng != null) status.setVisibility(GONE);
            }, 1800);
            return;
        }
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(selfLatLng, 16.0), 420);
    }
'''
new = '''    public void moveToSelf() {
        if (map == null) return;
        if (selfLatLng == null) {
            showTransientStatus("아직 내 위치를 받지 못했어요.");
            return;
        }
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(selfLatLng, 16.0), 420);
    }

    public boolean moveToMember(String memberId, String nickname) {
        if (map == null || memberId == null || memberId.isEmpty()) return false;
        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.optJSONObject(i);
            if (member == null || !memberId.equals(member.optString("member_id", ""))) continue;
            if (member.isNull("last_lat") || member.isNull("last_lon")) {
                showTransientStatus((nickname == null || nickname.isEmpty() ? "선택한 사용자" : nickname) + "님의 위치를 아직 받지 못했어요.");
                return false;
            }
            double lat = member.optDouble("last_lat", Double.NaN);
            double lon = member.optDouble("last_lon", Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) return false;
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lon), 16.0), 420);
            return true;
        }
        return false;
    }

    private void showTransientStatus(String message) {
        status.setText(message);
        status.setVisibility(VISIBLE);
        postDelayed(() -> {
            if (map != null) status.setVisibility(GONE);
        }, 1800);
    }
'''
s = rep(s, old, new, 'map move to member')
write('LocationSharingMapView.java', s)

for name, tokens in {
    'LocationSharingActivity.java': ['row.setOnClickListener', 'moveToMember(memberId, memberNickname)'],
    'LocationSharingMapView.java': ['public boolean moveToMember', 'showTransientStatus'],
}.items():
    text = read(name)
    for token in tokens:
        if token not in text:
            raise SystemExit(f'guard failed {name}: {token}')
