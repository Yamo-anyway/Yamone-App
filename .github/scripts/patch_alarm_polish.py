from pathlib import Path

p = Path('app/src/main/java/com/yamo/snorelab/AlarmActivity.java')
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

new_alarm_card = '''private View alarmCard(AlarmStore.Item item) {
        LinearLayout c = card();
        c.setPadding(dp(15), dp(14), dp(15), dp(14));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_nav_alarm);
        icon.setColorFilter(PRIMARY2);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(rounded(CARD2, 23, 0, 0));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        iconParams.rightMargin = dp(12);
        head.addView(icon, iconParams);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView time = text(String.format(Locale.KOREAN, "%02d:%02d", item.hour, item.minute), 32,
                item.enabled ? TEXT : MUTED, true);
        left.addView(time);
        TextView label = text(item.label == null || item.label.trim().isEmpty() ? "알람" : item.label,
                12, item.enabled ? TEXT : MUTED, true);
        label.setPadding(0, dp(1), 0, 0);
        left.addView(label);
        head.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch enabled = new Switch(this);
        enabled.setChecked(item.enabled);
        enabled.setContentDescription(item.enabled ? "알람 켜짐" : "알람 꺼짐");
        head.addView(enabled, new LinearLayout.LayoutParams(dp(58), dp(52)));
        c.addView(head);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(11), 0, 0);

        String mode = "TTS".equals(item.alertMode) ? "텍스트 읽기" : "알람음";
        TextView modeChip = alarmChip(mode, item.enabled ? PRIMARY2 : MUTED);
        chips.addView(modeChip);

        TextView scheduleChip = alarmChip(scheduleText(item), item.enabled ? PRIMARY2 : MUTED);
        LinearLayout.LayoutParams scheduleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
        scheduleParams.leftMargin = dp(6);
        chips.addView(scheduleChip, scheduleParams);
        c.addView(chips);

        StringBuilder detail = new StringBuilder("스누즈 ").append(item.snoozeMinutes).append("분");
        if (item.vibrate) detail.append(" · 진동");
        if (item.shakeToStop) detail.append(" · 흔들기 ").append(item.shakeCount).append("회");
        TextView details = text(detail.toString(), 11, MUTED, false);
        details.setPadding(0, dp(7), 0, 0);
        c.addView(details);

        TextView next = text(item.enabled ? "다음  " + AlarmScheduler.nextDateText(item) : "알람 꺼짐",
                12, item.enabled ? PRIMARY2 : MUTED, true);
        next.setPadding(0, dp(6), 0, dp(10));
        c.addView(next);

        if (!item.skipDate.isEmpty()) {
            TextView skipped = text("이번 알람 건너뜀  " + item.skipDate, 11, WARNING, true);
            skipped.setPadding(dp(10), dp(7), dp(10), dp(7));
            skipped.setBackground(rounded(0xFFFFF6E7, 12, 0, 0));
            LinearLayout.LayoutParams skippedParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            skippedParams.bottomMargin = dp(9);
            c.addView(skipped, skippedParams);
        }

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button skip = ghostButton("이번만 건너뛰기", PRIMARY2, v -> skipNext(item));
        Button edit = ghostButton("수정", TEXT, v -> showEditor(item));
        Button del = ghostButton("삭제", DANGER, v -> confirmDelete(item));
        buttons.addView(skip, new LinearLayout.LayoutParams(0, dp(42), 1.35f));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(42), 0.8f);
        bp.leftMargin = dp(7);
        buttons.addView(edit, bp);
        LinearLayout.LayoutParams dpv = new LinearLayout.LayoutParams(0, dp(42), 0.8f);
        dpv.leftMargin = dp(7);
        buttons.addView(del, dpv);
        c.addView(buttons);

        enabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.enabled = isChecked;
            if (isChecked && item.specificDate != null && !item.specificDate.isEmpty()) {
                try {
                    if (LocalDate.parse(item.specificDate).isBefore(LocalDate.now())) {
                        item.enabled = false;
                        enabled.setChecked(false);
                        Toast.makeText(this, "이미 지난 날짜의 알람이에요.", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception ignored) {}
            }
            AlarmStore.save(this, item);
            if (item.enabled) {
                if (!AlarmScheduler.scheduleNext(this, item)) Toast.makeText(this, "정확한 알람 권한 또는 날짜를 확인해주세요.", Toast.LENGTH_LONG).show();
            } else {
                AlarmScheduler.cancelAll(this, item.id);
            }
            showList();
        });
        return c;
    }'''

s = replace_method(s, 'private View alarmCard(AlarmStore.Item item)', new_alarm_card)

anchor = '    private void confirmDelete(AlarmStore.Item item) {'
helper = '''    private TextView alarmChip(String value, int color) {
        TextView chip = text(value, 11, color, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(11), 0, dp(11), 0);
        chip.setBackground(rounded(CARD2, 12, 0, 0));
        chip.setSingleLine(true);
        return chip;
    }\n\n'''
idx = s.find(anchor)
if idx < 0:
    raise SystemExit('confirmDelete anchor not found')
s = s[:idx] + helper + s[idx:]

# Slightly softer main action copy; behavior unchanged.
s = s.replace('Button add = actionButton("＋  새 알람 만들기", v -> showEditor(null));',
              'Button add = actionButton("＋  새 알람 만들기", v -> showEditor(null));', 1)

if 'private TextView alarmChip' not in s or 'R.drawable.ic_nav_alarm' not in s:
    raise SystemExit('alarm polish verification failed')
p.write_text(s)
