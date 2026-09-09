package com.yamo.snorelab;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/** Share-time extension picker opened from the active screen or expiry notification. */
public class LocationSharingTimeActivity extends Activity {
    private int BG;
    private int CARD;
    private int CARD2;
    private int TEXT;
    private int MUTED;
    private int PRIMARY;
    private int PRIMARY2;
    private int WARNING;
    private int BORDER;
    private int DANGER_BG;
    private int DANGER_TEXT;
    private int DANGER_BORDER;

    private LinearLayout page;
    private TextView remaining;
    private boolean busy;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyTheme();
        configureSystemBars();
        build();
        refresh();
    }

    private void applyTheme() {
        boolean pink = "pink".equals(getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "mint"));
        BG = pink ? 0xFFFFF7FA : 0xFFF7FFFB;
        CARD = 0xFFFFFFFF;
        CARD2 = pink ? 0xFFFFEEF3 : 0xFFF0FAF6;
        TEXT = pink ? 0xFF4B2633 : 0xFF153633;
        MUTED = pink ? 0xFF9A7180 : 0xFF718984;
        PRIMARY = pink ? 0xFFFF769F : 0xFF56D1B3;
        PRIMARY2 = pink ? 0xFFE94778 : 0xFF159A7A;
        WARNING = 0xFFE9A642;
        BORDER = pink ? 0xFFFFD7E3 : 0xFFD7EFE7;
        DANGER_BG = 0xFFFFF0F3;
        DANGER_TEXT = 0xFFE75B6D;
        DANGER_BORDER = 0xFFFFCBD3;
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);
        page.setPadding(dp(18), dp(20), dp(18), dp(36));
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener((v, insets) -> {
                int top = insets.getInsets(WindowInsets.Type.statusBars()).top;
                int bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                page.setPadding(dp(18), top + dp(14), dp(18), bottom + dp(30));
                return insets;
            });
            scroll.requestApplyInsets();
        }

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 34, PRIMARY2, true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(48)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("공유 시간 설정", 24, TEXT, true));
        titles.addView(text("현재 위치 공유 시간을 연장하거나 종료합니다.", 11, MUTED, false));
        top.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        page.addView(top);

        LinearLayout status = card();
        remaining = text("남은 시간 확인 중…", 14, PRIMARY2, true);
        status.addView(remaining);
        TextView rule = text("연장 시간은 현재 종료 예정 시각 뒤에 추가됩니다. 연장해도 과거 위치나 경로는 저장되지 않습니다.", 11, MUTED, false);
        rule.setPadding(0, dp(7), 0, 0);
        status.addView(rule);
        page.addView(status, cardParams());

        LinearLayout choices = card();
        choices.addView(text("추가 시간", 14, TEXT, true));
        addChoice(choices, "+30분", 30);
        addChoice(choices, "+1시간", 60);
        addChoice(choices, "+2시간", 120);
        addChoice(choices, "+4시간", 240);
        addChoice(choices, "+8시간", 480);
        addChoice(choices, "+12시간", 720);
        page.addView(choices, cardParams());

        Button stop = dangerButton("위치 공유 종료 · 방 나가기");
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        sp.topMargin = dp(14);
        page.addView(stop, sp);
        stop.setOnClickListener(v -> confirmStop());

        setContentView(scroll);
    }

    private void addChoice(LinearLayout parent, String label, int minutes) {
        Button button = softButton(label);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        p.topMargin = dp(8);
        parent.addView(button, p);
        button.setOnClickListener(v -> extend(minutes));
    }

    private void refresh() {
        LocationSharingApi.snapshot(this, new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                runOnUiThread(() -> {
                    if (!data.optBoolean("active", false)) {
                        LocationSharingStateStore.clear(LocationSharingTimeActivity.this);
                        remaining.setText("현재 참여 중인 위치 공유 방이 없습니다.");
                        remaining.setTextColor(WARNING);
                        return;
                    }
                    JSONArray members = data.optJSONArray("members");
                    JSONObject self = findSelf(members);
                    String until = self == null ? "" : self.optString("share_until", "");
                    String room = data.optString("room_name", "위치 공유 방");
                    int interval = self == null ? 60 : self.optInt("update_interval_seconds", 60);
                    int count = members == null ? 0 : members.length();
                    LocationSharingStateStore.update(LocationSharingTimeActivity.this, room, until, interval, count);
                    remaining.setText("현재 남은 시간 · " + remainingText(until));
                    remaining.setTextColor(PRIMARY2);
                });
            }

            @Override public void onFailure(String message) {
                runOnUiThread(() -> {
                    remaining.setText("남은 시간을 확인하지 못했습니다.");
                    remaining.setTextColor(WARNING);
                });
            }
        });
    }

    private void extend(int minutes) {
        if (busy) return;
        busy = true;
        remaining.setText("공유 시간을 연장하는 중…");
        LocationSharingApi.extend(this, minutes, new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                runOnUiThread(() -> {
                    busy = false;
                    Toast.makeText(LocationSharingTimeActivity.this, "위치 공유 시간을 연장했습니다.", Toast.LENGTH_SHORT).show();
                    LocationSharingService.start(LocationSharingTimeActivity.this);
                    refresh();
                });
            }

            @Override public void onFailure(String message) {
                runOnUiThread(() -> {
                    busy = false;
                    Toast.makeText(LocationSharingTimeActivity.this, message, Toast.LENGTH_SHORT).show();
                    refresh();
                });
            }
        });
    }

    private void confirmStop() {
        if (busy) return;
        new AlertDialog.Builder(this)
                .setTitle("위치 공유를 종료할까요?")
                .setMessage("내 마지막 위치가 서버에서 삭제되고 방에서 나갑니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("종료", (d, w) -> stopSharing())
                .show();
    }

    private void stopSharing() {
        busy = true;
        LocationSharingApi.leave(this, new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                runOnUiThread(() -> {
                    busy = false;
                    LocationSharingStateStore.clear(LocationSharingTimeActivity.this);
                    LocationSharingService.stop(LocationSharingTimeActivity.this);
                    Toast.makeText(LocationSharingTimeActivity.this, "위치 공유를 종료했습니다.", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }

            @Override public void onFailure(String message) {
                runOnUiThread(() -> {
                    busy = false;
                    Toast.makeText(LocationSharingTimeActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private JSONObject findSelf(JSONArray members) {
        if (members == null) return null;
        for (int i = 0; i < members.length(); i++) {
            JSONObject m = members.optJSONObject(i);
            if (m != null && m.optBoolean("is_self", false)) return m;
        }
        return null;
    }

    private String remainingText(String iso) {
        if (iso == null || iso.isEmpty()) return "-";
        try {
            long seconds = Math.max(0L, (Instant.parse(iso).toEpochMilli() - System.currentTimeMillis()) / 1000L);
            long hours = seconds / 3600L;
            long minutes = (seconds % 3600L) / 60L;
            if (hours > 0) return hours + "시간 " + minutes + "분";
            return Math.max(1L, minutes) + "분";
        } catch (DateTimeParseException e) {
            return "-";
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(round(CARD, 20, 1, BORDER));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(14);
        return p;
    }

    private Button softButton(String value) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextColor(PRIMARY2);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(CARD2, 14, 1, BORDER));
        return b;
    }

    private Button dangerButton(String value) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextColor(DANGER_TEXT);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(DANGER_BG, 15, 1, DANGER_BORDER));
        return b;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
