from pathlib import Path

ROOT = Path('app/src/main/java/com/yamo/snorelab')

def path(name): return ROOT / name

def read(name): return path(name).read_text()

def write(name, text): path(name).write_text(text)

def rep(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing {label}: {old[:120]}')
    return text.replace(old, new, 1)

# Base location-sharing flow.
s = read('LocationSharingActivity.java')
if 'import android.widget.ImageView;' not in s:
    s = s.replace('import android.widget.EditText;\n', 'import android.widget.EditText;\nimport android.widget.ImageView;\n', 1)

s = rep(s,
'''        TextView pin = text("📍", 48, PRIMARY2, false);\n        pin.setGravity(Gravity.CENTER);\n        body.addView(pin, matchWrap());''',
'''        ImageView pin = new ImageView(this);\n        pin.setImageResource(R.drawable.ic_location_pin);\n        pin.setColorFilter(PRIMARY2);\n        pin.setPadding(dp(18), dp(18), dp(18), dp(18));\n        pin.setBackground(round(CARD2, 34, 1, BORDER));\n        LinearLayout.LayoutParams pinParams = new LinearLayout.LayoutParams(dp(68), dp(68));\n        pinParams.gravity = Gravity.CENTER_HORIZONTAL;\n        body.addView(pin, pinParams);''', 'loading pin')

s = s.replace('text("지금, 소중한 사람들과\\n함께 있는지 확인해보세요 💕", 20, TEXT, true)',
              'text("지금, 소중한 사람들과\\n함께 있는지 확인해보세요", 20, TEXT, true)', 1)

s = rep(s,
'''        LinearLayout illustration = card();\n        illustration.setGravity(Gravity.CENTER);\n        TextView art = text("🏔️   👩🏻‍🦰  📍  👦🏻   🗺️", 34, TEXT, false);\n        art.setGravity(Gravity.CENTER);\n        illustration.addView(art, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(112)));\n        page.addView(illustration);''',
'''        LinearLayout illustration = card();\n        illustration.setGravity(Gravity.CENTER);\n        YamonePastelArtView art = new YamonePastelArtView(this, YamonePastelArtView.MODE_LOCATION_SCENE);\n        illustration.addView(art, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(138)));\n        page.addView(illustration);''', 'landing illustration')

s = rep(s,
'''        TextView create = bigMenuButton("👥  방 만들기", "새로운 방을 만들어 친구를 초대해요", true);\n        create.setOnClickListener(v -> showRoomForm(true));\n        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70));\n        cp.topMargin = dp(16);\n        page.addView(create, cp);\n\n        TextView join = bigMenuButton("👥  방 참여하기", "친구가 만든 방에 참여해요", false);\n        join.setOnClickListener(v -> showRoomForm(false));\n        LinearLayout.LayoutParams jp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70));\n        jp.topMargin = dp(10);\n        page.addView(join, jp);''',
'''        LinearLayout create = locationMenuCard(R.drawable.ic_location_group, "방 만들기", "새로운 방을 만들어 친구를 초대해요", true);\n        create.setOnClickListener(v -> showRoomForm(true));\n        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78));\n        cp.topMargin = dp(16);\n        page.addView(create, cp);\n\n        LinearLayout join = locationMenuCard(R.drawable.ic_location_group, "방 참여하기", "친구가 만든 방에 참여해요", false);\n        join.setOnClickListener(v -> showRoomForm(false));\n        LinearLayout.LayoutParams jp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78));\n        jp.topMargin = dp(10);\n        page.addView(join, jp);''', 'landing menu cards')

s = rep(s,
'''        TextView infoIcon = text("ⓘ", 22, PRIMARY2, true);\n        infoIcon.setGravity(Gravity.CENTER);\n        infoRow.addView(infoIcon, new LinearLayout.LayoutParams(dp(44), dp(44)));''',
'''        ImageView infoIcon = new ImageView(this);\n        infoIcon.setImageResource(R.drawable.ic_location_info);\n        infoIcon.setColorFilter(PRIMARY2);\n        infoIcon.setPadding(dp(10), dp(10), dp(10), dp(10));\n        infoIcon.setBackground(round(CARD2, 22, 0, 0));\n        infoRow.addView(infoIcon, new LinearLayout.LayoutParams(dp(44), dp(44)));''', 'info icon')

s = s.replace('activeRemaining = text("● 공유 중 · 남은 시간 확인 중…", 12, PRIMARY2, true);',
              'activeRemaining = text("공유 중 · 남은 시간 확인 중…", 12, PRIMARY2, true);', 1)
s = s.replace('Button extend = softButton("⏱  공유 시간 연장하기");', 'Button extend = softButton("공유 시간 연장하기");', 1)
s = s.replace('Button leave = dangerButton("▣  위치 공유 중단하기");', 'Button leave = dangerButton("위치 공유 중단하기");', 1)
s = s.replace('activeRemaining.setText("● 공유 중 · 남은 시간 " + remainingText(shareUntil));',
              'activeRemaining.setText("공유 중 · 남은 시간 " + remainingText(shareUntil));', 1)

s = rep(s,
'''            TextView avatar = text(self ? "🌸" : "🙂", 22, TEXT, false);\n            avatar.setGravity(Gravity.CENTER);\n            row.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(40)));''',
'''            ImageView avatar = new ImageView(this);\n            avatar.setImageResource(R.drawable.ic_location_person);\n            avatar.setColorFilter(self ? PRIMARY2 : ("connected".equals(state) ? SUCCESS : MUTED));\n            avatar.setPadding(dp(9), dp(9), dp(9), dp(9));\n            avatar.setBackground(round(self ? 0xFFFFE2EB : CARD, 20, 1, BORDER));\n            row.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(40)));''', 'participant avatar')

# Add common menu-card helper before the old legacy helper. Keeping the old helper is harmless and avoids wider churn.
anchor = '''    private TextView bigMenuButton(String title, String subtitle, boolean pinkButton) {'''
helper = '''    private LinearLayout locationMenuCard(int iconRes, String title, String subtitle, boolean primary) {\n        LinearLayout card = new LinearLayout(this);\n        card.setOrientation(LinearLayout.HORIZONTAL);\n        card.setGravity(Gravity.CENTER_VERTICAL);\n        card.setPadding(dp(14), dp(10), dp(12), dp(10));\n        card.setBackground(round(CARD, 22, 1, BORDER));\n        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(primary ? 2 : 1));\n\n        ImageView icon = new ImageView(this);\n        icon.setImageResource(iconRes);\n        icon.setColorFilter(PRIMARY2);\n        icon.setPadding(dp(11), dp(11), dp(11), dp(11));\n        icon.setBackground(round(primary ? 0xFFFFE2EB : CARD2, 24, 0, 0));\n        card.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));\n\n        LinearLayout words = new LinearLayout(this);\n        words.setOrientation(LinearLayout.VERTICAL);\n        words.setPadding(dp(12), 0, dp(8), 0);\n        words.addView(text(title, 15, TEXT, true));\n        TextView sub = text(subtitle, 11, MUTED, false);\n        sub.setPadding(0, dp(3), 0, 0);\n        words.addView(sub);\n        card.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));\n\n        ImageView arrow = new ImageView(this);\n        arrow.setImageResource(R.drawable.ic_yamone_chevron_right);\n        arrow.setColorFilter(PRIMARY2);\n        arrow.setPadding(dp(8), dp(8), dp(8), dp(8));\n        arrow.setBackground(round(CARD2, 18, 0, 0));\n        card.addView(arrow, new LinearLayout.LayoutParams(dp(36), dp(36)));\n        return card;\n    }\n\n'''
if helper.strip() not in s:
    if anchor not in s: raise SystemExit('menu helper anchor missing')
    s = s.replace(anchor, helper + anchor, 1)
write('LocationSharingActivity.java', s)

# Share-time picker: clock emoji -> vector icon, and remove hard-coded mint CTA.
s = read('LocationSharingTimeActivity.java')
if 'import android.widget.ImageView;' not in s:
    s = s.replace('import android.widget.Button;\n', 'import android.widget.Button;\nimport android.widget.ImageView;\n', 1)
s = rep(s,
'''        TextView clock = text("⏰", 64, TEXT, false);\n        clock.setGravity(Gravity.CENTER);\n        page.addView(clock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(104)));''',
'''        ImageView clock = new ImageView(this);\n        clock.setImageResource(R.drawable.ic_location_clock);\n        clock.setColorFilter(PRIMARY2);\n        clock.setPadding(dp(22), dp(22), dp(22), dp(22));\n        clock.setBackground(round(CARD2, 38, 1, BORDER));\n        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(dp(76), dp(76));\n        clockParams.gravity = Gravity.CENTER_HORIZONTAL;\n        clockParams.bottomMargin = dp(12);\n        page.addView(clock, clockParams);''', 'share time clock')
s = s.replace('extendButton.setBackground(round(0xFF45CDAE, 18, 0, 0));',
              'extendButton.setBackground(round(PRIMARY2, 18, 0, 0));', 1)
write('LocationSharingTimeActivity.java', s)

# Compatibility V2: base labels changed, so keep its custom stop dialog and spacing hooks aligned.
s = read('LocationSharingActivityV2.java')
s = s.replace('findExact(root, "▣  위치 공유 중단하기")', 'findExact(root, "위치 공유 중단하기")')
s = s.replace('findExact(root, "⏱  공유 시간 연장하기")', 'findExact(root, "공유 시간 연장하기")')
s = s.replace('findExact(root, "▣  위치 공유 중단하기")', 'findExact(root, "위치 공유 중단하기")')
write('LocationSharingActivityV2.java', s)

# Sanity checks.
base = read('LocationSharingActivity.java')
for bad in ['📍', '👥  방', '🏔️   👩🏻‍🦰', '🌸" : "🙂', '⏱  공유 시간', '▣  위치 공유']:
    if bad in base:
        raise SystemExit('legacy location emoji remains: ' + bad)
for needle in ['MODE_LOCATION_SCENE', 'ic_location_group', 'ic_location_person', 'ic_yamone_chevron_right']:
    if needle not in base:
        raise SystemExit('missing base polish: ' + needle)
time = read('LocationSharingTimeActivity.java')
if 'text("⏰"' in time or 'round(0xFF45CDAE' in time:
    raise SystemExit('time screen old style remains')
