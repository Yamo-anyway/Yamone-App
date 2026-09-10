package com.yamo.snorelab;

import android.app.Activity;
import android.graphics.Color;
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

/** Approved Yamone share-time extension picker. */
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

    private TextView remaining;
    private TextView choice30;
    private TextView choice60;
    private TextView choice120;
    private TextView choice180;
    private Button extendButton;
    private int selectedMinutes = 60;
    private boolean busy;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyTheme();
        configureSystemBars();
        build();
        refresh();
    }

    private void applyTheme() {
        boolean pink = pink();
        BG = pink ? 0xFFFFF7FA : 0xFFF7FFFB;
        CARD = 0xFFFFFFFF;
        CARD2 = pink ? 0xFFFFEEF3 : 0xFFF0FAF6;
        TEXT = pink ? 0xFF4B2633 : 0xFF153633;
        MUTED = pink ? 0xFF9A7180 : 0xFF718984;
        PRIMARY = pink ? 0xFFFF769F : 0xFF56D1B3;
        PRIMARY2 = pink ? 0xFFE94778 : 0xFF159A7A;
        WARNING = 0xFFE9A642;
        BORDER = pink ? 0xFFFFD7E3 : 0xFFD7EFE7;
    }

    private boolean pink() {
        return "pink".equals(getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "pink"));
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        applyInsets(root);

        LinearLayout top = YamoneBackHeader.create(this, "공유 시간 연장", null,
        BG, TEXT, MUTED, v -> finish());
    top.setPadding(dp(10), dp(5), dp(18), dp(5));
    root.addView(top);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(18), dp(22), dp(18), dp(24));

        TextView clock = text("⏰", 64, TEXT, false);
        clock.setGravity(Gravity.CENTER);
        page.addView(clock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(104)));

        TextView question = text("얼마나 더 공유할까요?", 20, TEXT, true);
        question.setGravity(Gravity.CENTER);
        page.addView(question);
        TextView sub = text("선택한 시간만큼 더 위치를 공유해요.", 12, MUTED, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(7), 0, dp(20));
        page.addView(sub);

        remaining = text("현재 남은 시간 확인 중…", 13, PRIMARY2, true);
        remaining.setGravity(Gravity.CENTER);
        remaining.setPadding(dp(12), dp(10), dp(12), dp(10));
        remaining.setBackground(round(CARD2, 16, 1, BORDER));
        page.addView(remaining, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        choices.setPadding(0, dp(22), 0, 0);
        choice30 = choice("30분", 30);
        choice60 = choice("1시간", 60);
        choice120 = choice("2시간", 120);
        choice180 = choice("3시간", 180);
        choices.addView(choice30, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams p60 = new LinearLayout.LayoutParams(0, dp(48), 1f); p60.leftMargin = dp(7);
        choices.addView(choice60, p60);
        LinearLayout.LayoutParams p120 = new LinearLayout.LayoutParams(0, dp(48), 1f); p120.leftMargin = dp(7);
        choices.addView(choice120, p120);
        LinearLayout.LayoutParams p180 = new LinearLayout.LayoutParams(0, dp(48), 1f); p180.leftMargin = dp(7);
        choices.addView(choice180, p180);
        page.addView(choices);
        styleChoices();

        TextView note = text("연장 후에도 언제든지 위치 공유를 중단할 수 있어요.", 11, MUTED, false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(18), 0, 0);
        page.addView(note);

        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setPadding(dp(18), dp(10), dp(18), dp(10));
        extendButton = new Button(this);
        extendButton.setText("연장하기");
        extendButton.setTextColor(Color.WHITE);
        extendButton.setTextSize(15);
        extendButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        extendButton.setAllCaps(false);
        extendButton.setBackground(round(0xFF45CDAE, 18, 0, 0));
        extendButton.setOnClickListener(v -> extend());
        bottom.addView(extendButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        root.addView(bottom);

        setContentView(root);
    }

    private TextView choice(String label, int minutes) {
        TextView v = text(label, 13, TEXT, true);
        v.setGravity(Gravity.CENTER);
        v.setOnClickListener(view -> {
            selectedMinutes = minutes;
            styleChoices();
        });
        return v;
    }

    private void styleChoices() {
        styleChoice(choice30, selectedMinutes == 30);
        styleChoice(choice60, selectedMinutes == 60);
        styleChoice(choice120, selectedMinutes == 120);
        styleChoice(choice180, selectedMinutes == 180);
    }

    private void styleChoice(TextView v, boolean selected) {
        if (v == null) return;
        int fill = selected ? (pink() ? 0xFFFFE2EB : 0xFFDDF8EF) : CARD2;
        int stroke = selected ? PRIMARY : BORDER;
        v.setTextColor(selected ? PRIMARY2 : TEXT);
        v.setBackground(round(fill, 18, 1, stroke));
    }

    private void refresh() {
        LocationSharingApi.snapshot(this, new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                runOnUiThread(() -> {
                    if (!data.optBoolean("active", false)) {
                        remaining.setText("현재 참여 중인 위치 공유 방이 없습니다.");
                        remaining.setTextColor(WARNING);
                        extendButton.setEnabled(false);
                        return;
                    }
                    JSONObject self = findSelf(data.optJSONArray("members"));
                    String until = self == null ? data.optString("share_until", "") : self.optString("share_until", "");
                    remaining.setText("현재 남은 시간 · " + remainingText(until));
                    remaining.setTextColor(PRIMARY2);
                    extendButton.setEnabled(true);
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

    private void extend() {
        if (busy) return;
        busy = true;
        extendButton.setEnabled(false);
        extendButton.setText("연장하는 중…");
        LocationSharingApi.extend(this, selectedMinutes, new LocationSharingApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                runOnUiThread(() -> {
                    busy = false;
                    Toast.makeText(LocationSharingTimeActivity.this, "위치 공유 시간을 연장했습니다.", Toast.LENGTH_SHORT).show();
                    LocationSharingService.start(LocationSharingTimeActivity.this);
                    finish();
                });
            }
            @Override public void onFailure(String message) {
                runOnUiThread(() -> {
                    busy = false;
                    extendButton.setEnabled(true);
                    extendButton.setText("연장하기");
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
        } catch (DateTimeParseException e) { return "-"; }
    }

    private void applyInsets(View root) {
        if (Build.VERSION.SDK_INT < 30) return;
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getInsets(WindowInsets.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            v.setPadding(0, top + dp(4), 0, bottom + dp(4));
            return insets;
        });
        root.requestApplyInsets();
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
