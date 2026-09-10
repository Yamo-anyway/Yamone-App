package com.yamo.snorelab;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/** Realtime/current lift wait list with optional resort map view. */
public class SkiWaitTimesActivity extends Activity {
    public static final String EXTRA_RESORT_KEY = "resort_key";
    public static final String EXTRA_RESORT_NAME = "resort_name";

    private static final int BG = 0xFF0B1324;
    private static final int CARD = 0xFF16243B;
    private static final int CARD2 = 0xFF111C31;
    private static final int TEXT = 0xFFF5F7FF;
    private static final int MUTED = 0xFF9DA9BF;
    private static final int PRIMARY = 0xFF6D72FF;
    private static final int PRIMARY2 = 0xFF8B8FFF;
    private static final int SUCCESS = 0xFF61D6A8;
    private static final int WARNING = 0xFFFFC56D;

    private String resortKey;
    private String resortName;
    private boolean mapMode;
    private boolean loading;
    private JSONObject snapshot = new JSONObject();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        resortKey = getIntent().getStringExtra(EXTRA_RESORT_KEY);
        resortName = getIntent().getStringExtra(EXTRA_RESORT_NAME);
        if (resortKey == null) resortKey = "";
        if (resortName == null) resortName = "";
        render();
        refresh();
    }

    private void refresh() {
        if (loading || resortKey.trim().isEmpty()) return;
        loading = true;
        render();
        SkiLiftApi.realtimeSnapshot(this, resortKey, new SkiLiftApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                runOnUiThread(() -> {
                    loading = false;
                    snapshot = data == null ? new JSONObject() : data;
                    if (snapshot.optBoolean("found", false)) {
                        resortName = snapshot.optString("resort_name", resortName);
                    }
                    render();
                });
            }

            @Override public void onFailure(String message) {
                runOnUiThread(() -> {
                    loading = false;
                    render();
                    Toast.makeText(SkiWaitTimesActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(10), dp(16), dp(30));
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        page.addView(YamoneBackHeader.create(this, "현재 예상 대기시간",
        resortName.isEmpty() ? "현재 스키장" : resortName,
        BG, TEXT, MUTED, v -> finish()));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        Button mode = ghostButton(mapMode ? "☷ 목록으로 보기" : "🗺 지도로 보기", v -> { mapMode = !mapMode; render(); });
        Button refresh = ghostButton(loading ? "불러오는 중…" : "↻ 새로고침", v -> refresh());
        refresh.setEnabled(!loading);
        controls.addView(mode, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        rp.leftMargin = dp(8);
        controls.addView(refresh, rp);
        LinearLayout.LayoutParams cp = match(dp(48)); cp.bottomMargin = dp(12);
        page.addView(controls, cp);

        if (loading && snapshot.length() == 0) {
            LinearLayout c = card();
            TextView t = text("리프트 대기정보를 불러오고 있어요…", 13, MUTED, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(20), 0, dp(20));
            c.addView(t);
            page.addView(c, cardParams());
            return;
        }

        if (!snapshot.optBoolean("found", false)) {
            LinearLayout c = card();
            c.addView(text("아직 등록된 스키장 정보가 없어요.", 15, TEXT, true));
            TextView d = text("이 스키장/리프트 정보가 등록되면 현재 대기와 과거 시간대 통계를 함께 볼 수 있습니다.", 12, MUTED, false);
            d.setPadding(0, dp(7), 0, 0);
            c.addView(d);
            page.addView(c, cardParams());
            return;
        }

        JSONArray lifts = snapshot.optJSONArray("lifts");
        if (lifts == null) lifts = new JSONArray();
        if (mapMode) {
            LinearLayout mapCard = card();
            mapCard.addView(text("리프트 하단 위치", 15, TEXT, true));
            TextView note = text("각 표시에는 리프트 이름과 현재 예상 대기시간이 함께 보입니다.", 11, MUTED, false);
            note.setPadding(0, dp(4), 0, dp(8));
            mapCard.addView(note);
            SkiWaitMapView map = new SkiWaitMapView(this);
            map.setLifts(lifts);
            LinearLayout.LayoutParams mp = match(dp(510));
            mapCard.addView(map, mp);
            page.addView(mapCard, cardParams());
        } else {
            buildList(page, lifts);
        }

        LinearLayout info = card();
        info.addView(text("예상시간 기준", 14, TEXT, true));
        TextView note = text("최근 30분 실측을 우선하고, 데이터가 적으면 같은 요일·시간대 과거 통계를 보조로 사용합니다. 자료가 부족하면 '정보 부족'으로 표시합니다.", 11, MUTED, false);
        note.setPadding(0, dp(6), 0, 0);
        info.addView(note);
        page.addView(info, cardParams());
    }

    private void buildList(LinearLayout page, JSONArray lifts) {
        LinearLayout card = card();
        card.addView(text("🚡 리프트", 15, TEXT, true));
        if (lifts.length() == 0) {
            TextView empty = text("등록된 리프트가 없습니다.", 12, MUTED, false);
            empty.setPadding(0, dp(12), 0, dp(8));
            card.addView(empty);
        } else {
            for (int i = 0; i < lifts.length(); i++) {
                JSONObject lift = lifts.optJSONObject(i);
                if (lift == null) continue;
                LinearLayout row = innerCard();
                LinearLayout top = new LinearLayout(this);
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setGravity(Gravity.CENTER_VERTICAL);
                String name = lift.optString("lift_name", "").trim();
                if (name.isEmpty()) name = lift.optString("lift_key", "리프트");
                top.addView(text(name, 14, TEXT, true), new LinearLayout.LayoutParams(0, dp(34), 1f));
                TextView wait = text(waitText(lift), 16, lift.isNull("estimated_wait_seconds") ? MUTED : SUCCESS, true);
                wait.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                top.addView(wait, new LinearLayout.LayoutParams(dp(100), dp(34)));
                row.addView(top);

                int recent = lift.optInt("recent_samples", 0);
                long historical = lift.optLong("historical_samples", 0);
                String source = lift.optString("estimate_source", "insufficient");
                String desc;
                if ("mixed".equals(source)) desc = "최근 실측 " + recent + "건 + 같은 시간대 통계 " + historical + "건";
                else if ("realtime".equals(source)) desc = "최근 실측 " + recent + "건 기준";
                else if ("historical".equals(source)) desc = "같은 요일·시간대 통계 " + historical + "건 기준";
                else desc = "아직 계산할 자료가 부족합니다.";
                row.addView(text(desc, 11, MUTED, false));
                card.addView(row, compactParams());
            }
        }
        page.addView(card, cardParams());
    }

    private String waitText(JSONObject lift) {
        if (lift == null || lift.isNull("estimated_wait_seconds")) return "정보 부족";
        int seconds = Math.max(0, lift.optInt("estimated_wait_seconds", 0));
        int minutes = Math.max(0, Math.round(seconds / 60f));
        return String.format(Locale.KOREAN, "약 %d분", minutes);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(15), dp(16), dp(15));
        c.setBackground(round(CARD, 18, 0, 0));
        return c;
    }

    private LinearLayout innerCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(10), dp(12), dp(10));
        c.setBackground(round(CARD2, 13, 1, 0xFF2F405C));
        return c;
    }

    private Button ghostButton(String label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextColor(TEXT);
        b.setTextSize(12);
        b.setBackground(round(CARD2, 13, 1, 0xFF35445F));
        b.setOnClickListener(click);
        return b;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setLineSpacing(0, 1.08f);
        return v;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(12);
        return p;
    }

    private LinearLayout.LayoutParams compactParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(8);
        return p;
    }

    private LinearLayout.LayoutParams match(int height) { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
