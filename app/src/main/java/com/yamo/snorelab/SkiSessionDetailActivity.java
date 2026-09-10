package com.yamo.snorelab;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Local-only ski session summary. Lift-name suggestions send only lift metadata, never the ski route. */
public class SkiSessionDetailActivity extends Activity {
    public static final String EXTRA_SESSION_PATH = "session_path";
    public static final String EXTRA_JUST_FINISHED = "just_finished";

    private static final int BG = 0xFFFFF7FA;
    private static final int CARD = 0xFFFFFFFF;
    private static final int CARD2 = 0xFFFFEEF3;
    private static final int TEXT = 0xFF4B2633;
    private static final int MUTED = 0xFF9A7180;
    private static final int PRIMARY2 = 0xFFE94778;
    private static final int SUCCESS = 0xFF36A57D;

    private File sessionDir;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = getWindow().getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
        sessionDir = resolveSession(getIntent().getStringExtra(EXTRA_SESSION_PATH));
        if (sessionDir == null) {
            finish();
            return;
        }
        render();
    }

    private File resolveSession(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        try {
            File root = SkiLiftStore.sessionsRoot(this).getCanonicalFile();
            File dir = new File(path).getCanonicalFile();
            if (!dir.exists() || !dir.isDirectory()) return null;
            if (!dir.getPath().startsWith(root.getPath() + File.separator)) return null;
            return dir;
        } catch (Exception e) {
            return null;
        }
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(12), dp(16), dp(30));
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        JSONObject meta = SkiLiftStore.readSessionMeta(sessionDir);
        JSONArray lifts = SkiLiftStore.readLiftObservations(sessionDir);
        List<SkiSessionAnalysis.DescentSummary> descents = SkiSessionAnalysis.readDescents(sessionDir);
        List<WalkingStore.Point> mapPoints = SkiSessionAnalysis.readMapPoints(sessionDir, 1600);
        boolean justFinished = getIntent().getBooleanExtra(EXTRA_JUST_FINISHED, false);
        String sport = "snowboard".equals(meta.optString("sport")) ? "스노보드" : "스키";
        page.addView(YamoneBackHeader.create(this, sport + " 기록", null,
        BG, TEXT, MUTED, v -> finish()));

        if (justFinished) {
            LinearLayout done = card();
            TextView check = text("✓", 34, SUCCESS, true);
            check.setGravity(Gravity.CENTER);
            done.addView(check, match(dp(44)));
            TextView label = text("오늘의 " + sport + " 기록이 저장되었습니다.", 17, TEXT, true);
            label.setGravity(Gravity.CENTER);
            done.addView(label);
            TextView sub = text("GPS 경로와 리프트 기록은 휴대폰에 보관됩니다.", 11, MUTED, false);
            sub.setGravity(Gravity.CENTER);
            sub.setPadding(0, dp(5), 0, 0);
            done.addView(sub);
            page.addView(done, cardParams());
        }

        long start = meta.optLong("startEpochMs", 0);
        long end = meta.optLong("endEpochMs", 0);
        if (start > 0) {
            String date = new SimpleDateFormat("yyyy년 M월 d일 (E)  HH:mm", Locale.KOREAN).format(new Date(start));
            if (end > start) date += " ~ " + new SimpleDateFormat("HH:mm", Locale.KOREAN).format(new Date(end));
            TextView dateView = text(date, 12, MUTED, false);
            dateView.setPadding(0, 0, 0, dp(10));
            page.addView(dateView);
        }

        LinearLayout summary = card();
        summary.addView(text("오늘 기록", 16, TEXT, true));
        LinearLayout r1 = metricRow();
        r1.addView(metric("활주", meta.optInt("descentCount", descents.size()) + "회"), weight());
        r1.addView(metric("리프트", meta.optInt("liftCount", lifts.length()) + "회"), weight());
        r1.addView(metric("최고속도", String.format(Locale.KOREAN, "%.1f km/h", meta.optDouble("maxSpeedKmh", 0))), weight());
        summary.addView(r1);
        LinearLayout r2 = metricRow();
        r2.addView(metric("활주거리", String.format(Locale.KOREAN, "%.2f km", meta.optLong("descentDistanceM", 0) / 1000.0)), weight());
        r2.addView(metric("누적 하강", meta.optLong("descentVerticalM", 0) + " m"), weight());
        r2.addView(metric("총 시간", formatElapsed(meta.optLong("durationMs", Math.max(0, end - start)))), weight());
        summary.addView(r2);
        summary.addView(kv("리프트 이동시간", formatElapsed(meta.optLong("liftTimeMs", 0))));
        summary.addView(kv("추정 대기시간 합계", formatElapsed(meta.optLong("waitTimeMs", 0))));
        page.addView(summary, cardParams());

        LinearLayout mapCard = card();
        mapCard.addView(text("전체 이동 경로", 15, TEXT, true));
        if (mapPoints.isEmpty()) {
            TextView empty = text("표시할 GPS 경로가 없습니다.", 12, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(18), 0, dp(12));
            mapCard.addView(empty);
        } else {
            WalkingMapView map = new WalkingMapView(this);
            map.setPoints(mapPoints);
            LinearLayout.LayoutParams mp = match(dp(250));
            mp.topMargin = dp(8);
            mapCard.addView(map, mp);
        }
        page.addView(mapCard, cardParams());

        LinearLayout runs = card();
        runs.addView(text("활주 기록", 15, TEXT, true));
        if (descents.isEmpty()) {
            TextView empty = text("자동 판별된 활주 구간이 없습니다.", 12, MUTED, false);
            empty.setPadding(0, dp(10), 0, 0);
            runs.addView(empty);
        } else {
            for (int i = 0; i < descents.size(); i++) {
                SkiSessionAnalysis.DescentSummary d = descents.get(i);
                LinearLayout item = innerCard();
                String time = new SimpleDateFormat("HH:mm", Locale.KOREAN).format(new Date(d.startMs));
                item.addView(text((i + 1) + "번째 활주 · " + time, 13, TEXT, true));
                item.addView(text(String.format(Locale.KOREAN,
                        "%.2f km · %s · 평균 %.1f km/h · 최고 %.1f km/h",
                        d.distanceM / 1000.0, formatElapsed(d.endMs - d.startMs), d.avgSpeedKmh, d.maxSpeedKmh),
                        11, MUTED, false));
                item.addView(text(String.format(Locale.KOREAN, "하강 %.0f m", d.verticalM), 11, PRIMARY2, true));
                runs.addView(item, compactParams());
            }
        }
        page.addView(runs, cardParams());

        LinearLayout liftCard = card();
        liftCard.addView(text("리프트 / 대기 기록", 15, TEXT, true));
        if (lifts.length() == 0) {
            TextView empty = text("자동 판별된 리프트 이용 기록이 없습니다.", 12, MUTED, false);
            empty.setPadding(0, dp(10), 0, 0);
            liftCard.addView(empty);
        } else {
            for (int i = 0; i < lifts.length(); i++) {
                JSONObject lift = lifts.optJSONObject(i);
                if (lift == null) continue;
                JSONObject cachedLift = SkiResortStore.resolveCachedLift(this, lift);
                String officialName = cachedLift == null ? "" : cachedLift.optString("lift_name", "").trim();
                String localName = lift.optString("liftName", "").trim();
                String liftName = !officialName.isEmpty() ? officialName : (!localName.isEmpty() ? localName : "이름 없는 리프트");
                long rideStart = lift.optLong("rideStartEpochMs", 0);
                String time = rideStart > 0 ? new SimpleDateFormat("HH:mm", Locale.KOREAN).format(new Date(rideStart)) : "--:--";
                LinearLayout item = innerCard();
                item.addView(text(liftName + " · " + time, 13, TEXT, true));
                item.addView(text("대기 " + formatElapsed(lift.optLong("waitDurationMs", 0))
                        + " · 이동 " + formatElapsed(lift.optLong("rideDurationMs", 0))
                        + " · 상승 " + Math.round(lift.optDouble("ascentM", 0)) + " m", 11, MUTED, false));
                int confidence = (int) Math.round(lift.optDouble("confidence", 0) * 100);
                String confidenceText = confidence >= 85 ? "높음" : confidence >= 65 ? "보통" : "검토 필요";
                item.addView(text("자동 감지 신뢰도 " + confidenceText + " · " + confidence + "%", 10, PRIMARY2, false));

                final JSONObject liftForClick = lift;
                final JSONObject cachedForClick = cachedLift;
                final JSONObject metaForClick = meta;
                String actionLabel = cachedLift != null && !officialName.isEmpty()
                        ? "이름 수정 요청"
                        : "리프트 이름 알려주기";
                Button suggest = ghostButton(actionLabel, v -> showLiftNameSuggestion(liftForClick, cachedForClick, metaForClick));
                LinearLayout.LayoutParams bp = match(dp(42));
                bp.topMargin = dp(8);
                item.addView(suggest, bp);
                liftCard.addView(item, compactParams());
            }
            TextView note = text("제안한 이름은 바로 적용되지 않고 검토 후 승인되면 다음 리프트 정보 업데이트에 반영됩니다.", 10, MUTED, false);
            note.setPadding(0, dp(8), 0, 0);
            liftCard.addView(note);
        }
        page.addView(liftCard, cardParams());
    }

    private void showLiftNameSuggestion(JSONObject lift, JSONObject cachedLift, JSONObject meta) {
        if (lift == null) return;
        String officialName = cachedLift == null ? "" : cachedLift.optString("lift_name", "").trim();
        String cachedLiftId = cachedLift == null ? "" : cachedLift.optString("lift_id", "").trim();
        String type = cachedLift == null ? "new_lift" : (officialName.isEmpty() ? "name" : "correction");
        String title = "correction".equals(type) ? "리프트 이름 수정 요청" : "리프트 이름 알려주기";

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setTextSize(15);
        input.setHint("리프트 이름 입력");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(round(CARD2, 16, 1, 0xFFFFD7E3));
        if (!officialName.isEmpty()) input.setText(officialName);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(20), dp(4), dp(20), 0);
        if (!officialName.isEmpty()) {
            TextView current = text("현재 이름: " + officialName, 12, MUTED, false);
            current.setPadding(0, 0, 0, dp(8));
            wrap.addView(current);
        }
        wrap.addView(input, match(dp(52)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("제안은 검토 후 반영됩니다. 전체 스키 경로는 전송하지 않습니다.")
                .setView(wrap)
                .setNegativeButton("취소", null)
                .setPositiveButton("요청 보내기", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String proposed = input.getText() == null ? "" : input.getText().toString().trim();
            if (proposed.isEmpty() || proposed.length() > 80) {
                input.setError("리프트 이름을 1~80자로 입력해 주세요.");
                return;
            }
            String resortKey = lift.optString("resortKey", "").trim();
            if (resortKey.isEmpty() && meta != null) resortKey = meta.optString("resortKey", "").trim();
            if (resortKey.isEmpty()) resortKey = SkiResortStore.currentKey(this);

            Double lowerLat = numberOrNull(lift, "lowerLat");
            Double lowerLon = numberOrNull(lift, "lowerLon");
            Double upperLat = numberOrNull(lift, "upperLat");
            Double upperLon = numberOrNull(lift, "upperLon");
            String finalResortKey = resortKey;
            input.setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            SkiLiftApi.submitNameSuggestion(this, finalResortKey,
                    cachedLiftId.isEmpty() ? null : cachedLiftId,
                    type, proposed, officialName,
                    lowerLat, lowerLon, upperLat, upperLon,
                    new SkiLiftApi.JsonCallback() {
                        @Override public void onSuccess(JSONObject data) {
                            runOnUiThread(() -> {
                                dialog.dismiss();
                                Toast.makeText(SkiSessionDetailActivity.this,
                                        "검토 요청을 보냈어요. 승인 후 정보 업데이트에 반영됩니다.", Toast.LENGTH_LONG).show();
                            });
                        }

                        @Override public void onFailure(String message) {
                            runOnUiThread(() -> {
                                input.setEnabled(true);
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                Toast.makeText(SkiSessionDetailActivity.this, message, Toast.LENGTH_LONG).show();
                            });
                        }
                    });
        }));
        dialog.show();
    }

    private static Double numberOrNull(JSONObject object, String key) {
        if (object == null || object.isNull(key)) return null;
        double value = object.optDouble(key, Double.NaN);
        return Double.isFinite(value) ? value : null;
    }

    private LinearLayout metricRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        return row;
    }

    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, dp(68), 1f); }

    private View metric(String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView v = text(value, 14, TEXT, true); v.setGravity(Gravity.CENTER); box.addView(v);
        TextView l = text(label, 10, MUTED, false); l.setGravity(Gravity.CENTER); box.addView(l);
        return box;
    }

    private View kv(String key, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(9), 0, 0);
        TextView k = text(key, 12, MUTED, false);
        TextView v = text(value, 13, TEXT, true); v.setGravity(Gravity.END);
        row.addView(k, new LinearLayout.LayoutParams(0, dp(27), 1f));
        row.addView(v, new LinearLayout.LayoutParams(0, dp(27), 1f));
        return row;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(15), dp(16), dp(15));
        c.setBackground(round(CARD, 22, 1, 0xFFFFE3EC));
        return c;
    }

    private LinearLayout innerCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(11), dp(12), dp(11));
        c.setBackground(round(CARD2, 16, 1, 0xFFFFD7E3));
        return c;
    }

    private Button ghostButton(String label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextColor(TEXT);
        b.setTextSize(12);
        b.setBackground(round(CARD2, 16, 1, 0xFFFFD7E3));
        b.setOnClickListener(click);
        return b;
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

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static String formatElapsed(long ms) {
        long sec = Math.max(0, ms / 1000);
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        return h > 0 ? String.format(Locale.KOREAN, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.KOREAN, "%02d:%02d", m, s);
    }
}
