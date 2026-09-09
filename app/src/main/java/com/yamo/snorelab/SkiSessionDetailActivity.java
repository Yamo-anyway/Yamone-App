package com.yamo.snorelab;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Local-only ski session summary. */
public class SkiSessionDetailActivity extends Activity {
    public static final String EXTRA_SESSION_PATH = "session_path";
    public static final String EXTRA_JUST_FINISHED = "just_finished";

    private static final int BG = 0xFF0B1324;
    private static final int CARD = 0xFF16243B;
    private static final int CARD2 = 0xFF111C31;
    private static final int TEXT = 0xFFF5F7FF;
    private static final int MUTED = 0xFF9DA9BF;
    private static final int PRIMARY2 = 0xFF8B8FFF;
    private static final int SUCCESS = 0xFF61D6A8;

    private File sessionDir;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
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
        String icon = "스노보드".equals(sport) ? "🏂" : "⛷";

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 34, TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(52)));
        header.addView(text(icon + " " + sport + " 기록", 22, TEXT, true), new LinearLayout.LayoutParams(0, dp(52), 1f));
        page.addView(header);

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
        runs.addView(text("⛷ 활주 기록", 15, TEXT, true));
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
        liftCard.addView(text("🚡 리프트 / 대기 기록", 15, TEXT, true));
        if (lifts.length() == 0) {
            TextView empty = text("자동 판별된 리프트 이용 기록이 없습니다.", 12, MUTED, false);
            empty.setPadding(0, dp(10), 0, 0);
            liftCard.addView(empty);
        } else {
            for (int i = 0; i < lifts.length(); i++) {
                JSONObject lift = lifts.optJSONObject(i);
                if (lift == null) continue;
                String liftName = lift.optString("liftName", "").trim();
                if (liftName.isEmpty()) liftName = "이름 없는 리프트";
                long rideStart = lift.optLong("rideStartEpochMs", 0);
                String time = rideStart > 0 ? new SimpleDateFormat("HH:mm", Locale.KOREAN).format(new Date(rideStart)) : "--:--";
                LinearLayout item = innerCard();
                item.addView(text("🚡 " + liftName + " · " + time, 13, TEXT, true));
                item.addView(text("대기 " + formatElapsed(lift.optLong("waitDurationMs", 0))
                        + " · 이동 " + formatElapsed(lift.optLong("rideDurationMs", 0))
                        + " · 상승 " + Math.round(lift.optDouble("ascentM", 0)) + " m", 11, MUTED, false));
                int confidence = (int) Math.round(lift.optDouble("confidence", 0) * 100);
                String confidenceText = confidence >= 85 ? "높음" : confidence >= 65 ? "보통" : "검토 필요";
                item.addView(text("자동 감지 신뢰도 " + confidenceText + " · " + confidence + "%", 10, PRIMARY2, false));
                liftCard.addView(item, compactParams());
            }
            TextView note = text("이름 없는 리프트의 이름 제안/수정 요청은 리프트 서버 연결 단계에서 붙입니다.", 10, MUTED, false);
            note.setPadding(0, dp(8), 0, 0);
            liftCard.addView(note);
        }
        page.addView(liftCard, cardParams());
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
        c.setBackground(round(CARD, 18, 0, 0));
        return c;
    }

    private LinearLayout innerCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(11), dp(12), dp(11));
        c.setBackground(round(CARD2, 14, 1, 0xFF2F405C));
        return c;
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
