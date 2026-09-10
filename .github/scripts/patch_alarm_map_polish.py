from pathlib import Path

ROOT = Path('app/src/main/java/com/yamo/snorelab')

def read(name): return (ROOT / name).read_text()
def write(name, text): (ROOT / name).write_text(text)
def rep(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing {label}: {old[:100]}')
    return text.replace(old, new, 1)

# Alarm ringing screen polish.
s = read('AlarmRingActivity.java')
if 'import android.widget.ImageView;' not in s:
    s = s.replace('import android.widget.Button;\n', 'import android.widget.Button;\nimport android.widget.ImageView;\n', 1)

s = rep(s,
'        if (Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);',
'''        if (Build.VERSION.SDK_INT >= 23) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }''', 'alarm system bars')

s = rep(s,
'''        TextView time = text(String.format(Locale.KOREAN, "%02d:%02d", item.hour, item.minute), 64, TEXT, true);
        time.setGravity(Gravity.CENTER);
        mainCard.addView(time, matchWrap());''',
'''        ImageView alarmIcon = new ImageView(this);
        alarmIcon.setImageResource(R.drawable.ic_nav_alarm);
        alarmIcon.setColorFilter(PRIMARY2);
        alarmIcon.setPadding(dp(14), dp(14), dp(14), dp(14));
        alarmIcon.setBackground(round(CARD2, 28));
        LinearLayout.LayoutParams alarmIconParams = new LinearLayout.LayoutParams(dp(56), dp(56));
        alarmIconParams.gravity = Gravity.CENTER_HORIZONTAL;
        alarmIconParams.bottomMargin = dp(12);
        mainCard.addView(alarmIcon, alarmIconParams);

        TextView time = text(String.format(Locale.KOREAN, "%02d:%02d", item.hour, item.minute), 64, TEXT, true);
        time.setGravity(Gravity.CENTER);
        mainCard.addView(time, matchWrap());''', 'alarm hero icon')

s = rep(s,
'''        String method = "TTS".equals(item.alertMode) ? "🗣  텍스트 읽기" : "🔔  알람음";
        TextView methodView = text(method + (item.vibrate ? "  ·  진동" : ""), 14, PRIMARY2, true);
        methodView.setGravity(Gravity.CENTER);
        mainCard.addView(methodView, matchWrap());''',
'''        String method = "TTS".equals(item.alertMode) ? "텍스트 읽기" : "알람음";
        LinearLayout methodRow = new LinearLayout(this);
        methodRow.setOrientation(LinearLayout.HORIZONTAL);
        methodRow.setGravity(Gravity.CENTER);
        methodRow.setPadding(dp(12), dp(8), dp(12), dp(8));
        methodRow.setBackground(round(CARD2, 18));
        ImageView methodIcon = new ImageView(this);
        methodIcon.setImageResource("TTS".equals(item.alertMode) ? R.drawable.ic_alarm_speech : R.drawable.ic_nav_alarm);
        methodIcon.setColorFilter(PRIMARY2);
        methodIcon.setPadding(dp(4), dp(4), dp(4), dp(4));
        methodRow.addView(methodIcon, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView methodView = text(method + (item.vibrate ? " · 진동" : ""), 13, PRIMARY2, true);
        LinearLayout.LayoutParams methodTextParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28));
        methodTextParams.leftMargin = dp(4);
        methodView.setGravity(Gravity.CENTER_VERTICAL);
        methodRow.addView(methodView, methodTextParams);
        LinearLayout.LayoutParams methodParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        methodParams.gravity = Gravity.CENTER_HORIZONTAL;
        mainCard.addView(methodRow, methodParams);''', 'alarm method chip')

s = rep(s,
'''            TextView title = text("📱  흔들어서 종료", 16, TEXT, true);
            title.setGravity(Gravity.CENTER);
            shakeCard.addView(title, matchWrap());''',
'''            ImageView shakeIcon = new ImageView(this);
            shakeIcon.setImageResource(R.drawable.ic_alarm_shake);
            shakeIcon.setColorFilter(PRIMARY2);
            shakeIcon.setPadding(dp(9), dp(9), dp(9), dp(9));
            shakeIcon.setBackground(round(CARD, 23));
            LinearLayout.LayoutParams shakeIconParams = new LinearLayout.LayoutParams(dp(46), dp(46));
            shakeIconParams.gravity = Gravity.CENTER_HORIZONTAL;
            shakeIconParams.bottomMargin = dp(7);
            shakeCard.addView(shakeIcon, shakeIconParams);
            TextView title = text("흔들어서 종료", 16, TEXT, true);
            title.setGravity(Gravity.CENTER);
            shakeCard.addView(title, matchWrap());''', 'alarm shake icon')

s = s.replace('Button snooze = button("😴  " + item.snoozeMinutes + "분 후 다시", CARD2, TEXT);',
              'Button snooze = button(item.snoozeMinutes + "분 후 다시", CARD2, PRIMARY2);')
s = s.replace('Button stop = button("■  알람 종료", PRIMARY, pinkTextColor());',
              'Button stop = button("알람 종료", PRIMARY2, 0xFFFFFFFF);')
write('AlarmRingActivity.java', s)

# Location-sharing map polish.
s = read('LocationSharingMapView.java')
s = s.replace('setBackgroundColor(0xFF101B2D);', 'setBackgroundColor(0xFFFFF7FA);')
s = s.replace('status.setTextColor(Color.WHITE);', 'status.setTextColor(0xFF9A7180);')
s = s.replace('status.setBackgroundColor(0x990B1324);', 'status.setBackgroundColor(0xEEFFF7FA);')
s = s.replace('myLocationButton.setText("◎ 내 위치");', 'myLocationButton.setText("내 위치");')
s = s.replace('myLocationButton.setTextColor(Color.WHITE);', 'myLocationButton.setTextColor(0xFFE94778);')
s = s.replace('myLocationButton.setBackground(round(0xE616243B, 13, 1, 0xFF7180A1));',
              'myLocationButton.setBackground(round(0xF8FFFFFF, 16, 1, 0xFFFFD7E3));')

s = rep(s,
'''            String markerText;
            if (self) markerText = "● 나 · " + nickname;
            else if ("disconnected".equals(state) || "location_stale".equals(state)) markerText = "⚠ " + nickname;
            else if ("waiting".equals(state)) markerText = "… " + nickname;
            else markerText = "● " + nickname;''',
'''            String markerText = self ? "나 · " + nickname : nickname;''', 'map marker text')

s = rep(s,
'''        if (self) {
            background = 0xEE4E55D8;
            foreground = Color.WHITE;
        } else if ("disconnected".equals(state) || "location_stale".equals(state)) {
            background = 0xEE482534;
            foreground = 0xFFFFC0CB;
        } else if ("waiting".equals(state)) {
            background = 0xEE2A3448;
            foreground = 0xFFCBD3E3;
        } else {
            background = 0xEE16243B;
            foreground = Color.WHITE;
        }''',
'''        if (self) {
            background = 0xF2E94778;
            foreground = Color.WHITE;
        } else if ("disconnected".equals(state) || "location_stale".equals(state)) {
            background = 0xF8FFF0F3;
            foreground = 0xFFE75B6D;
        } else if ("waiting".equals(state)) {
            background = 0xF8FFF7FA;
            foreground = 0xFF9A7180;
        } else {
            background = 0xF8FFFFFF;
            foreground = 0xFF4B2633;
        }''', 'map marker palette')

s = rep(s,
'''        canvas.drawRoundRect(rect, dp(12), dp(12), bg);

        textPaint.setColor(foreground);''',
'''        canvas.drawRoundRect(rect, dp(14), dp(14), bg);
        if (!self) {
            Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(Math.max(1f, density));
            border.setColor(0xFFFFD7E3);
            canvas.drawRoundRect(rect, dp(14), dp(14), border);
        }

        textPaint.setColor(foreground);''', 'map marker border')
write('LocationSharingMapView.java', s)

# Basic guard: old alarm/map UI tokens targeted here should be gone.
for name, tokens in {
    'AlarmRingActivity.java': ['🗣', '🔔', '📱', '😴', '■  알람 종료'],
    'LocationSharingMapView.java': ['◎ 내 위치', '⚠ ', '● 나 · ', '0xFF101B2D']
}.items():
    text = read(name)
    for token in tokens:
        if token in text:
            raise SystemExit(f'old token remains in {name}: {token}')
