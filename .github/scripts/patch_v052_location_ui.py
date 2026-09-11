from pathlib import Path

ROOT = Path(".")
activity_path = ROOT / "app/src/main/java/com/yamo/snorelab/LocationSharingActivity.java"
map_path = ROOT / "app/src/main/java/com/yamo/snorelab/LocationSharingMapView.java"
launcher_bg_path = ROOT / "app/src/main/res/drawable/yamone_launcher_background.xml"
refresh_icon_path = ROOT / "app/src/main/res/drawable/ic_location_refresh.xml"

def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"missing block: {label}")
    return text.replace(old, new, 1)

def replace_between(text, start, end, replacement, label):
    i = text.find(start)
    if i < 0:
        raise SystemExit(f"missing start: {label}")
    j = text.find(end, i)
    if j < 0:
        raise SystemExit(f"missing end: {label}")
    return text[:i] + replacement + text[j:]

# ---------------- LocationSharingActivity ----------------
a = activity_path.read_text()

a = replace_once(a,
'''    private TextView statusNormal;
    private TextView statusContact;
    private TextView statusHelp;
    private TextView statusEmergency;
    private TextView activeStatusHint;
    private TextView nextStatusCheck;
    private long nextStatusCheckAtMs;
    private String currentSelfStatus = "normal";
''',
'''    private TextView statusNormal;
    private TextView statusContact;
    private TextView statusHelp;
    private TextView statusEmergency;
    private TextView activeStatusHint;
    private TextView participantHeaderTitle;
    private TextView nextStatusCheck;
    private ImageView manualRefreshButton;
    private TextView statusSummaryNormal;
    private TextView statusSummaryContact;
    private TextView statusSummaryHelp;
    private TextView statusSummaryEmergency;
    private long nextStatusCheckAtMs;
    private long manualRefreshUnlockAtMs;
    private boolean snapshotRefreshInFlight;
    private String currentSelfStatus = "normal";
''', "activity fields")

a = replace_once(a,
'''    private final Runnable activePoller = new Runnable() {
        @Override public void run() {
            if (!activeScreen) return;
            refreshActiveSnapshot();
            nextStatusCheckAtMs = System.currentTimeMillis() + ACTIVE_POLL_MS;
            updateStatusCountdownText();
            handler.postDelayed(this, ACTIVE_POLL_MS);
        }
    };
''',
'''    private final Runnable activePoller = new Runnable() {
        @Override public void run() {
            if (!activeScreen) return;
            performSnapshotRefresh(false);
        }
    };
''', "active poller")

a = replace_once(a,
'''        activeNetworkHint = null;
        activeStatusHint = null;
        nextStatusCheck = null;
''',
'''        activeNetworkHint = null;
        activeStatusHint = null;
        participantHeaderTitle = null;
        nextStatusCheck = null;
        manualRefreshButton = null;
        statusSummaryNormal = null;
        statusSummaryContact = null;
        statusSummaryHelp = null;
        statusSummaryEmergency = null;
''', "showActive refs")

map_anchor = '''        root.addView(sharingMap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));

        ScrollView scroll = new ScrollView(this);
'''
map_repl = '''        root.addView(sharingMap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));

        LinearLayout overallStatus = buildOverallStatusSummary();
        root.addView(overallStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        ScrollView scroll = new ScrollView(this);
'''
a = replace_once(a, map_anchor, map_repl, "status summary placement")

a = replace_between(
    a,
    '        LinearLayout myStatus = card();\n',
    '        LinearLayout participants = card();\n',
    '',
    "remove old status card"
)

old_participants = '''        LinearLayout participants = card();
        participants.setPadding(dp(10), dp(10), dp(10), dp(10));
        participants.addView(text("참여자", 13, TEXT, true));
        participantList = new LinearLayout(this);
'''
new_participants = '''        LinearLayout participants = card();
        participants.setPadding(dp(10), dp(10), dp(10), dp(10));

        LinearLayout participantHeader = new LinearLayout(this);
        participantHeader.setOrientation(LinearLayout.HORIZONTAL);
        participantHeader.setGravity(Gravity.CENTER_VERTICAL);

        participantHeaderTitle = text("참여자 (0명)", 13, TEXT, true);
        participantHeaderTitle.setSingleLine(true);
        participantHeaderTitle.setIncludeFontPadding(false);
        participantHeader.addView(participantHeaderTitle, new LinearLayout.LayoutParams(
                0, dp(34), 1f));

        TextView statusChange = text("내 상태 변경", 11, PRIMARY2, true);
        statusChange.setGravity(Gravity.CENTER);
        statusChange.setIncludeFontPadding(false);
        statusChange.setPadding(dp(9), 0, dp(9), 0);
        statusChange.setBackground(round(CARD2, 15, 1, BORDER));
        statusChange.setOnClickListener(v -> showStatusPickerDialog());
        LinearLayout.LayoutParams statusChangeParams = new LinearLayout.LayoutParams(
                dp(88), dp(32));
        statusChangeParams.leftMargin = dp(5);
        participantHeader.addView(statusChange, statusChangeParams);

        nextStatusCheck = text("01:00", 11, MUTED, true);
        nextStatusCheck.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        nextStatusCheck.setSingleLine(true);
        nextStatusCheck.setIncludeFontPadding(false);
        LinearLayout.LayoutParams countdownParams = new LinearLayout.LayoutParams(
                dp(48), dp(32));
        countdownParams.leftMargin = dp(5);
        participantHeader.addView(nextStatusCheck, countdownParams);

        manualRefreshButton = new ImageView(this);
        manualRefreshButton.setImageResource(R.drawable.ic_location_refresh);
        manualRefreshButton.setColorFilter(PRIMARY2);
        manualRefreshButton.setPadding(dp(7), dp(7), dp(7), dp(7));
        manualRefreshButton.setBackground(round(CARD2, 16, 1, BORDER));
        manualRefreshButton.setContentDescription("참여자 상태 새로고침");
        manualRefreshButton.setOnClickListener(v -> performSnapshotRefresh(true));
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
                dp(32), dp(32));
        refreshParams.leftMargin = dp(4);
        participantHeader.addView(manualRefreshButton, refreshParams);

        participants.addView(participantHeader);
        participantList = new LinearLayout(this);
'''
a = replace_once(a, old_participants, new_participants, "participant header")

a = replace_once(a,
'''        currentSelfStatus = self == null ? "normal" : self.optString("user_status", "normal");
        styleSelfStatus();
        if (sharingMap != null) sharingMap.setMembers(members);
        renderParticipants(members);
''',
'''        currentSelfStatus = self == null ? "normal" : self.optString("user_status", "normal");
        styleSelfStatus();
        updateOverallStatusSummary(members);
        if (participantHeaderTitle != null) {
            participantHeaderTitle.setText("참여자 (" + members.length() + "명)");
        }
        if (sharingMap != null) sharingMap.setMembers(members);
        renderParticipants(members);
''', "apply snapshot summary")

summary_helpers = r'''    private LinearLayout buildOverallStatusSummary() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(6), dp(10), dp(6));
        row.setBackgroundColor(CARD);

        statusSummaryNormal = overallStatusChip("normal");
        statusSummaryContact = overallStatusChip("contact");
        statusSummaryHelp = overallStatusChip("help");
        statusSummaryEmergency = overallStatusChip("emergency");

        row.addView(statusSummaryNormal, new LinearLayout.LayoutParams(0, dp(40), 1f));
        row.addView(statusSummaryContact, new LinearLayout.LayoutParams(0, dp(40), 1f));
        row.addView(statusSummaryHelp, new LinearLayout.LayoutParams(0, dp(40), 1f));
        row.addView(statusSummaryEmergency, new LinearLayout.LayoutParams(0, dp(40), 1f));
        return row;
    }

    private TextView overallStatusChip(String status) {
        TextView chip = text("", 10, TEXT, true);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setIncludeFontPadding(false);
        chip.setCompoundDrawablePadding(dp(4));
        GradientDrawable dot = statusDotDrawable(status, false);
        int size = dp(9);
        dot.setBounds(0, 0, size, size);
        chip.setCompoundDrawables(dot, null, null, null);
        return chip;
    }

    private void updateOverallStatusSummary(JSONArray members) {
        int normal = 0;
        int contact = 0;
        int help = 0;
        int emergency = 0;
        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.optJSONObject(i);
            if (member == null) continue;
            String status = member.optString("user_status", "normal");
            if ("emergency".equals(status)) emergency++;
            else if ("help".equals(status)) help++;
            else if ("contact".equals(status)) contact++;
            else normal++;
        }
        setOverallStatusText(statusSummaryNormal, "정상", normal);
        setOverallStatusText(statusSummaryContact, "연락", contact);
        setOverallStatusText(statusSummaryHelp, "도움", help);
        setOverallStatusText(statusSummaryEmergency, "긴급", emergency);
    }

    private void setOverallStatusText(TextView view, String label, int count) {
        if (view != null) view.setText(label + " (" + count + "명)");
    }

'''
a = replace_once(a,
'    private void renderParticipants(JSONArray members) {\n',
summary_helpers + '    private void renderParticipants(JSONArray members) {\n',
"summary helpers")

status_picker = r'''    private void showStatusPickerDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(20), dp(20), dp(18));
        panel.setBackground(round(CARD, 26, 1, BORDER));

        TextView title = text("내 상태 변경", 19, TEXT, true);
        title.setIncludeFontPadding(false);
        panel.addView(title);

        TextView subtitle = text(
                "현재 상태는 " + userStatusLabel(currentSelfStatus) + "이에요.",
                12, MUTED, false);
        subtitle.setPadding(0, dp(5), 0, dp(14));
        panel.addView(subtitle);

        addStatusPickerRow(panel, dialog, "normal", "정상");
        addStatusPickerRow(panel, dialog, "contact", "연락 요청");
        addStatusPickerRow(panel, dialog, "help", "도움 필요");
        addStatusPickerRow(panel, dialog, "emergency", "긴급");

        TextView cancel = text("닫기", 13, MUTED, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setIncludeFontPadding(false);
        cancel.setBackground(round(CARD2, 16, 1, BORDER));
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        cancelParams.topMargin = dp(14);
        panel.addView(cancel, cancelParams);

        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.38f;
            window.setAttributes(attrs);
        }
        dialog.show();

        window = dialog.getWindow();
        if (window != null) {
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            window.setLayout(Math.min(dp(360), screenWidth - dp(36)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
    }

    private void addStatusPickerRow(LinearLayout panel, Dialog dialog, String status, String label) {
        boolean selected = status.equals(currentSelfStatus);
        int statusColor = LocationStatusPalette.color(status);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(12), 0);
        row.setBackground(round(LocationStatusPalette.softColor(status), 17, 1,
                selected ? statusColor : BORDER));

        View dot = new View(this);
        dot.setBackground(statusDotDrawable(status, selected));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                dp(selected ? 14 : 11), dp(selected ? 14 : 11));
        dotParams.rightMargin = dp(10);
        row.addView(dot, dotParams);

        TextView name = text(label, 13, selected ? statusColor : TEXT, true);
        name.setIncludeFontPadding(false);
        row.addView(name, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView state = text(selected ? "현재 상태" : "변경", 10,
                selected ? statusColor : MUTED, true);
        state.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        state.setIncludeFontPadding(false);
        row.addView(state, new LinearLayout.LayoutParams(dp(58), dp(48)));

        if (selected) {
            row.setEnabled(false);
            row.setAlpha(0.56f);
        } else {
            row.setClickable(true);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                requestStatusChange(status);
            });
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        params.topMargin = dp(7);
        panel.addView(row, params);
    }

'''
a = replace_once(a,
'    private void requestStatusChange(String status) {\n',
status_picker + '    private void requestStatusChange(String status) {\n',
"status picker")

a = a.replace(
'                "같은 방 참여자가 다음 상태 확인 시\\n" + label + " 상태를 볼 수 있어요.",',
'                "상태 변경은 서버에 바로 반영되고,\\n다른 참여자가 다음 갱신 시 확인할 수 있어요.",',
1
)

poll_start = '    private void scheduleActivePolling() {\n'
poll_end = '    private void ensureSharingService() {\n'
new_polling = r'''    private void scheduleActivePolling() {
        resetRefreshSchedule(10_000L);
    }

    private void resetRefreshSchedule(long manualLockMs) {
        handler.removeCallbacks(activePoller);
        handler.removeCallbacks(statusCountdown);
        if (!activeScreen) return;

        long now = System.currentTimeMillis();
        nextStatusCheckAtMs = now + ACTIVE_POLL_MS;
        manualRefreshUnlockAtMs = now + Math.max(0L, manualLockMs);
        updateStatusCountdownText();
        updateManualRefreshState();

        handler.postDelayed(activePoller, ACTIVE_POLL_MS);
        handler.postDelayed(statusCountdown, 1_000L);
    }

    private void performSnapshotRefresh(boolean manual) {
        if (!activeScreen || snapshotRefreshInFlight) return;
        long now = System.currentTimeMillis();
        if (manual && now < manualRefreshUnlockAtMs) return;

        snapshotRefreshInFlight = true;
        resetRefreshSchedule(30_000L);

        LocationSharingApi.snapshotFresh(this, new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                runOnUiThread(() -> {
                    snapshotRefreshInFlight = false;
                    applySnapshot(data);
                    updateManualRefreshState();
                });
            }

            @Override public void onFailure(String message) {
                runOnUiThread(() -> {
                    snapshotRefreshInFlight = false;
                    updateManualRefreshState();
                    if (activeNetworkHint != null) {
                        activeNetworkHint.setText("네트워크 연결 대기 중 · 마지막으로 받은 위치를 유지합니다.");
                        activeNetworkHint.setTextColor(WARNING);
                    }
                    toast("최신 상태를 불러오지 못했어요.");
                });
            }
        });
    }

    private void updateStatusCountdownText() {
        if (nextStatusCheck == null) return;
        long remainingMs = Math.max(0L, nextStatusCheckAtMs - System.currentTimeMillis());
        long totalSeconds = (remainingMs + 999L) / 1_000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        nextStatusCheck.setText(String.format(Locale.KOREAN, "%02d:%02d", minutes, seconds));
        updateManualRefreshState();
    }

    private void updateManualRefreshState() {
        if (manualRefreshButton == null) return;
        boolean enabled = activeScreen
                && !snapshotRefreshInFlight
                && System.currentTimeMillis() >= manualRefreshUnlockAtMs;
        manualRefreshButton.setEnabled(enabled);
        manualRefreshButton.setClickable(enabled);
        manualRefreshButton.setAlpha(enabled ? 1f : 0.32f);
    }

'''
a = replace_between(a, poll_start, poll_end, new_polling, "refresh scheduling")

menu_start = '    private LinearLayout locationMenuCard(int iconRes, String title, String subtitle, boolean primary) {\n'
menu_end = '    private TextView bigMenuButton(String title, String subtitle, boolean pinkButton) {\n'
new_menu = r'''    private LinearLayout locationMenuCard(int iconRes, String title, String subtitle, boolean primary) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(6), dp(12), dp(6));
        card.setBackground(round(CARD, 22, 1, BORDER));
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(primary ? 2 : 1));

        LinearLayout leftSlot = new LinearLayout(this);
        leftSlot.setGravity(Gravity.CENTER);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(PRIMARY2);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(round(primary ? 0xFFFFE2EB : CARD2, 22, 0, 0));
        leftSlot.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        card.addView(leftSlot, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setGravity(Gravity.CENTER);
        TextView titleView = text(title, 15, TEXT, true);
        titleView.setSingleLine(true);
        titleView.setGravity(Gravity.CENTER);
        titleView.setIncludeFontPadding(false);
        words.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView sub = text(subtitle, 11, MUTED, false);
            sub.setGravity(Gravity.CENTER);
            sub.setPadding(0, dp(3), 0, 0);
            words.addView(sub, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        card.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        LinearLayout rightSlot = new LinearLayout(this);
        rightSlot.setGravity(Gravity.CENTER);
        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_yamone_chevron_right);
        arrow.setColorFilter(PRIMARY2);
        arrow.setPadding(dp(7), dp(7), dp(7), dp(7));
        arrow.setBackground(round(CARD2, 17, 0, 0));
        rightSlot.addView(arrow, new LinearLayout.LayoutParams(dp(34), dp(34)));
        card.addView(rightSlot, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return card;
    }

'''
a = replace_between(a, menu_start, menu_end, new_menu, "location menu centering")

activity_path.write_text(a)

# ---------------- LocationSharingMapView ----------------
m = map_path.read_text()

m = replace_once(m,
'''import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
''',
'''import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
''', "map imports android")

m = replace_once(m,
'''import org.maplibre.android.annotations.IconFactory;
import org.maplibre.android.annotations.MarkerOptions;
''',
'''import org.maplibre.android.annotations.IconFactory;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
''', "map marker import")

m = replace_once(m,
'''import java.util.ArrayList;
import java.util.List;
''',
'''import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
''', "map time imports")

m = replace_once(m,
'''    private final TextView status;
    private final TextView myLocationButton;
''',
'''    private final TextView status;
    private final TextView myLocationButton;
    private final TextView allLocationsButton;
''', "map fields")

button_anchor = '''        addView(myLocationButton, bp);

        mapView.getMapAsync(value -> {
'''
button_repl = '''        addView(myLocationButton, bp);

        allLocationsButton = new TextView(context);
        allLocationsButton.setText("전체보기");
        allLocationsButton.setTextColor(pink() ? 0xFFE94778 : 0xFF159A7A);
        allLocationsButton.setTextSize(12);
        allLocationsButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        allLocationsButton.setGravity(Gravity.CENTER);
        allLocationsButton.setPadding(dp(10), 0, dp(10), 0);
        allLocationsButton.setBackground(round(
                pink() ? 0xF8FFF7FA : 0xF8F7FFFB,
                16, 1, pink() ? 0xFFFFD7E3 : 0xFFD7EFE7));
        allLocationsButton.setOnClickListener(v -> moveToAll());
        FrameLayout.LayoutParams allParams = new FrameLayout.LayoutParams(dp(92), dp(40));
        allParams.gravity = Gravity.END | Gravity.BOTTOM;
        allParams.setMargins(0, 0, dp(112), dp(12));
        addView(allLocationsButton, allParams);

        mapView.getMapAsync(value -> {
'''
m = replace_once(m, button_anchor, button_repl, "all locations button")

m = replace_once(m,
'''            map = value;
            map.getUiSettings().setAllGesturesEnabled(true);
            map.setStyle(STYLE_URI, style -> renderMembers());
''',
'''            map = value;
            map.getUiSettings().setAllGesturesEnabled(true);
            map.setInfoWindowAdapter(marker -> buildInfoWindow(marker));
            map.setStyle(STYLE_URI, style -> renderMembers());
''', "info window adapter")

m = replace_once(m,
'''            String snippet = stateLabel(state) + statusSuffix(userStatus);
            map.addMarker(new MarkerOptions()
                    .position(point)
                    .title(nickname)
                    .snippet(snippet)
                    .icon(icon));
''',
'''            String snippet = userStatus + "\\u001F"
                    + state + "\\u001F"
                    + member.optString("last_location_at", "") + "\\u001F"
                    + (self ? "1" : "0");
            map.addMarker(new MarkerOptions()
                    .position(point)
                    .title(nickname)
                    .snippet(snippet)
                    .icon(icon));
''', "marker popup metadata")

map_helpers = r'''    public void moveToAll() {
        if (map == null) return;
        List<LatLng> positions = new ArrayList<>();
        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.optJSONObject(i);
            if (member == null || member.isNull("last_lat") || member.isNull("last_lon")) continue;
            double lat = member.optDouble("last_lat", Double.NaN);
            double lon = member.optDouble("last_lon", Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
            positions.add(new LatLng(lat, lon));
        }
        if (positions.isEmpty()) {
            showTransientStatus("아직 표시할 참여자 위치가 없어요.");
            return;
        }
        fitInitialCamera(positions);
    }

    private View buildInfoWindow(Marker marker) {
        String raw = marker == null ? "" : marker.getSnippet();
        String[] parts = raw == null ? new String[0] : raw.split("\\u001F", -1);
        String userStatus = parts.length > 0 && !parts[0].isEmpty() ? parts[0] : "normal";
        String connectionState = parts.length > 1 ? parts[1] : "waiting";
        String lastLocationAt = parts.length > 2 ? parts[2] : "";
        boolean self = parts.length > 3 && "1".equals(parts[3]);

        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(11), dp(14), dp(11));
        panel.setMinimumWidth(dp(164));
        panel.setBackground(round(
                pink() ? 0xFFFFFBFC : 0xFFFBFFFD,
                18, 1, pink() ? 0xFFFFC7D8 : 0xFFC9E9DF));

        TextView name = new TextView(getContext());
        name.setText((marker == null ? "사용자" : marker.getTitle()) + (self ? "  (나)" : ""));
        name.setTextColor(pink() ? 0xFF4B2633 : 0xFF153633);
        name.setTextSize(14);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setIncludeFontPadding(false);
        panel.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));

        LinearLayout statusRow = new LinearLayout(getContext());
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(getContext());
        GradientDrawable dotDrawable = new GradientDrawable();
        dotDrawable.setShape(GradientDrawable.OVAL);
        dotDrawable.setColor(LocationStatusPalette.color(userStatus));
        dot.setBackground(dotDrawable);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotParams.rightMargin = dp(7);
        statusRow.addView(dot, dotParams);

        TextView statusText = new TextView(getContext());
        statusText.setText(LocationStatusPalette.label(userStatus));
        statusText.setTextColor(LocationStatusPalette.color(userStatus));
        statusText.setTextSize(11);
        statusText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusText.setIncludeFontPadding(false);
        statusRow.addView(statusText, new LinearLayout.LayoutParams(
                0, dp(24), 1f));
        panel.addView(statusRow);

        String age = ageText(lastLocationAt);
        String detail = age.isEmpty() ? stateLabel(connectionState)
                : age + " · " + stateLabel(connectionState);
        TextView detailText = new TextView(getContext());
        detailText.setText(detail);
        detailText.setTextColor(pink() ? 0xFF9A7180 : 0xFF718984);
        detailText.setTextSize(10);
        detailText.setIncludeFontPadding(false);
        panel.addView(detailText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));
        return panel;
    }

    private String ageText(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            long seconds = Math.max(0L,
                    (System.currentTimeMillis() - Instant.parse(iso).toEpochMilli()) / 1000L);
            if (seconds < 60L) return "방금";
            long minutes = seconds / 60L;
            if (minutes < 60L) return minutes + "분 전";
            return (minutes / 60L) + "시간 전";
        } catch (DateTimeParseException ignored) {
            return "";
        }
    }

'''
m = replace_once(m,
'    public void moveToSelf() {\n',
map_helpers + '    public void moveToSelf() {\n',
"map helpers")

map_path.write_text(m)

refresh_icon_path.write_text('''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="#FF000000"
        android:strokeWidth="2"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M20,8V4h-4 M20,4c-1.8,-1.7 -4.2,-2.7 -6.8,-2.7C8.1,1.3 4,5.4 4,10.5" />
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="#FF000000"
        android:strokeWidth="2"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M4,16v4h4 M4,20c1.8,1.7 4.2,2.7 6.8,2.7c5.1,0 9.2,-4.1 9.2,-9.2" />
</vector>
''')

launcher_bg_path.write_text('''<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#FFFDFD"/>
        </shape>
    </item>
    <item
        android:left="15dp"
        android:top="15dp"
        android:right="15dp"
        android:bottom="15dp">
        <bitmap
            android:src="@drawable/yamone_launcher_day_night"
            android:gravity="fill"/>
    </item>
</layer-list>
''')

print("patched location sharing UI, refresh policy, map controls/popups, launcher safe area")
