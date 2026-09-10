from pathlib import Path

p = Path('app/src/main/java/com/yamo/snorelab/LocationSharingActivity.java')
text = p.read_text()

def replace_once(old, new, label):
    global text
    if old not in text:
        raise SystemExit(f'missing {label}')
    text = text.replace(old, new, 1)

replace_once(
'''        activeRemaining = text("남은 시간 확인 중…", 13, TEXT, true);
        timeRow.addView(activeRemaining, new LinearLayout.LayoutParams(
                0, dp(42), 1f));''',
'''        activeRemaining = text("남은 시간 확인 중…", 13, TEXT, true);
        activeRemaining.setGravity(Gravity.CENTER_VERTICAL);
        activeRemaining.setIncludeFontPadding(false);
        timeRow.addView(activeRemaining, new LinearLayout.LayoutParams(
                0, dp(40), 1f));''',
'active remaining alignment')

replace_once(
'''            TextView name = text(nickname + (self ? "  (나)" : ""), 12, TEXT, self);
            name.setSingleLine(true);
            row.addView(name, new LinearLayout.LayoutParams(0, dp(34), 1f));''',
'''            TextView name = text(nickname + (self ? "  (나)" : ""), 12, TEXT, self);
            name.setSingleLine(true);
            name.setGravity(Gravity.CENTER_VERTICAL);
            name.setIncludeFontPadding(false);
            row.addView(name, new LinearLayout.LayoutParams(0, dp(34), 1f));''',
'participant nickname alignment')

replace_once(
'''            TextView ageView = text(age.isEmpty() ? "위치 대기" : age, 11, MUTED, false);
            ageView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            ageView.setSingleLine(true);''',
'''            TextView ageView = text(age.isEmpty() ? "위치 대기" : age, 11, MUTED, false);
            ageView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            ageView.setSingleLine(true);
            ageView.setIncludeFontPadding(false);''',
'participant age alignment')

replace_once(
'''        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(10), dp(12), dp(10));''',
'''        card.setGravity(Gravity.CENTER_VERTICAL);
        // 64dp card: 44dp icon + 6dp vertical padding fits without clipping.
        card.setPadding(dp(14), dp(6), dp(12), dp(6));''',
'location menu card padding')

replace_once(
'''        icon.setPadding(dp(11), dp(11), dp(11), dp(11));
        icon.setBackground(round(primary ? 0xFFFFE2EB : CARD2, 24, 0, 0));
        card.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(12), 0, dp(8), 0);
        words.addView(text(title, 15, TEXT, true));''',
'''        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(round(primary ? 0xFFFFE2EB : CARD2, 22, 0, 0));
        card.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setGravity(Gravity.CENTER_VERTICAL);
        words.setPadding(dp(12), 0, dp(8), 0);
        TextView titleView = text(title, 15, TEXT, true);
        titleView.setSingleLine(true);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setIncludeFontPadding(false);
        words.addView(titleView);''',
'location menu icon and title alignment')

replace_once(
'''        arrow.setPadding(dp(8), dp(8), dp(8), dp(8));
        arrow.setBackground(round(CARD2, 18, 0, 0));
        card.addView(arrow, new LinearLayout.LayoutParams(dp(36), dp(36)));''',
'''        arrow.setPadding(dp(7), dp(7), dp(7), dp(7));
        arrow.setBackground(round(CARD2, 17, 0, 0));
        card.addView(arrow, new LinearLayout.LayoutParams(dp(34), dp(34)));''',
'location menu arrow alignment')

replace_once(
'''        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackground(round(pinkButton ? 0xFFFF6F98 : 0xFF45CDAE, 18, 0, 0));''',
'''        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        b.setBackground(round(pinkButton ? 0xFFFF6F98 : 0xFF45CDAE, 18, 0, 0));''',
'primary button alignment')

replace_once(
'''        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackground(round(CARD2, 16, 1, BORDER));''',
'''        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        b.setBackground(round(CARD2, 16, 1, BORDER));''',
'soft button alignment')

replace_once(
'''        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackground(round(DANGER_BG, 16, 1, DANGER_BORDER));''',
'''        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        b.setBackground(round(DANGER_BG, 16, 1, DANGER_BORDER));''',
'danger button alignment')

# Guards
assert 'activeRemaining.setGravity(Gravity.CENTER_VERTICAL);' in text
assert 'name.setGravity(Gravity.CENTER_VERTICAL);' in text
assert 'card.setPadding(dp(14), dp(6), dp(12), dp(6));' in text
assert 'card.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));' in text
assert 'titleView.setIncludeFontPadding(false);' in text

p.write_text(text)
