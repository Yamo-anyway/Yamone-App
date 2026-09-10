package com.yamo.snorelab;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Local-first ski/snowboard activity screen. */
public class SkiActivity extends Activity {
    private static final int REQ_LOCATION = 5501;
    private static final int BG = 0xFF0B1324;
    private static final int CARD = 0xFF16243B;
    private static final int CARD2 = 0xFF111C31;
    private static final int TEXT = 0xFFF5F7FF;
    private static final int MUTED = 0xFF9DA9BF;
    private static final int PRIMARY = 0xFF6D72FF;
    private static final int PRIMARY2 = 0xFF8B8FFF;
    private static final int SUCCESS = 0xFF61D6A8;
    private static final int WARNING = 0xFFFFC56D;

    private static final String PREFS = "yamone_ski_ui_v1";
    private static final String KEY_SPORT = "sport";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            render();
            handler.postDelayed(this, 1000L);
        }
    };

    private LinearLayout page;
    private SharedPreferences prefs;
    private SharedPreferences runtime;
    private boolean providingHistorical;
    private boolean updatingResort;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        runtime = getSharedPreferences(SkiRecorderService.PREFS, MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildRoot();
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private void buildRoot() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(10), dp(16), dp(28));
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (Build.VERSION.SDK_INT >= 21) {
            scroll.setOnApplyWindowInsetsListener((v, insets) -> {
                int bottom = Build.VERSION.SDK_INT >= 30
                        ? insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                        : insets.getSystemWindowInsetBottom();
                v.setPadding(0, 0, 0, bottom);
                return insets;
            });
        }
        setContentView(scroll);
    }

    private void render() {
        if (page == null) return;
        page.removeAllViews();
        page.addView(header());

        ImageView heroImage = new ImageView(this);
        heroImage.setImageResource(R.drawable.yamone_ski);
        heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroImage.setBackground(rounded(CARD2, 22, 0, 0));
        heroImage.setClipToOutline(true);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(138));
        hp.bottomMargin = dp(12);
        page.addView(heroImage, hp);

        if (!isRecording()) buildSportCard();
        buildRecordingCard();
        if (!isRecording()) buildRecentRecordsCard();
        buildLiftCard();
        buildPrivacyCard();
    }

    private View header() {
    return YamoneBackHeader.create(this, "스키 / 스노보드", "오늘의 설원을 기록해요", BG, TEXT, MUTED, v -> finish());
}

    private void buildSportCard() {
        LinearLayout c = card();
        c.addView(text("종목 선택", 15, TEXT, true));
        TextView desc = text("기록 시작 전에 종목을 선택하세요.", 11, MUTED, false);
        desc.setPadding(0, dp(4), 0, dp(10));
        c.addView(desc);

        String sport = selectedSport();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(choiceButton("⛷  스키", "ski".equals(sport), v -> selectSport("ski")), new LinearLayout.LayoutParams(0, dp(52), 1f));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1f); p.leftMargin = dp(8);
        row.addView(choiceButton("🏂  스노보드", "snowboard".equals(sport), v -> selectSport("snowboard")), p);
        c.addView(row);
        page.addView(c, cardParams());
    }

    private void buildRecordingCard() {
        boolean recording = isRecording();
        LinearLayout c = card();
        if (!recording) {
            c.addView(text("스키 준비 완료", 16, TEXT, true));
            TextView desc = text("시작하면 화면이 꺼져 있어도 GPS 기록과 활주·리프트·정지 판별을 계속합니다.", 12, MUTED, false);
            desc.setPadding(0, dp(7), 0, dp(12));
            c.addView(desc);
            c.addView(actionButton("▶ " + ("snowboard".equals(selectedSport()) ? "스노보드" : "스키") + " 기록 시작", true, v -> startSession()), match(dp(56)));
            page.addView(c, cardParams());
            return;
        }

        String state = runtime.getString(SkiRecorderService.KEY_STATE, SkiRecorderService.STATE_CHECKING);
        String stateLabel = stateLabel(state);
        int stateColor = SkiRecorderService.STATE_DESCENT.equals(state) ? SUCCESS
                : SkiRecorderService.STATE_LIFT.equals(state) ? PRIMARY2
                : SkiRecorderService.STATE_STOPPED.equals(state) ? WARNING : MUTED;
        c.addView(text(stateLabel, 18, stateColor, true));

        TextView speed = text(String.format(Locale.KOREAN, "%.1f km/h", runtime.getFloat(SkiRecorderService.KEY_SPEED_KMH, 0)), 38, TEXT, true);
        speed.setGravity(Gravity.CENTER);
        speed.setPadding(0, dp(5), 0, dp(3));
        c.addView(speed);
        TextView elapsed = text("전체 시간  " + formatElapsed(System.currentTimeMillis() - runtime.getLong(SkiRecorderService.KEY_START_MS, System.currentTimeMillis())), 12, MUTED, true);
        elapsed.setGravity(Gravity.CENTER);
        c.addView(elapsed);

        LinearLayout row1 = new LinearLayout(this); row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(metric("활주", runtime.getInt(SkiRecorderService.KEY_DESCENT_COUNT, 0) + "회"), new LinearLayout.LayoutParams(0, dp(68), 1f));
        row1.addView(metric("리프트", runtime.getInt(SkiRecorderService.KEY_LIFT_COUNT, 0) + "회"), new LinearLayout.LayoutParams(0, dp(68), 1f));
        row1.addView(metric("최고속도", String.format(Locale.KOREAN, "%.1f", runtime.getFloat(SkiRecorderService.KEY_MAX_SPEED_KMH, 0)) + " km/h"), new LinearLayout.LayoutParams(0, dp(68), 1f));
        c.addView(row1);

        LinearLayout row2 = new LinearLayout(this); row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(metric("활주거리", String.format(Locale.KOREAN, "%.2f km", runtime.getLong(SkiRecorderService.KEY_DESCENT_DISTANCE_M, 0) / 1000.0)), new LinearLayout.LayoutParams(0, dp(68), 1f));
        row2.addView(metric("누적 하강", runtime.getLong(SkiRecorderService.KEY_DESCENT_VERTICAL_M, 0) + " m"), new LinearLayout.LayoutParams(0, dp(68), 1f));
        row2.addView(metric("GPS 고도", Math.round(runtime.getFloat(SkiRecorderService.KEY_ALTITUDE_M, 0)) + " m"), new LinearLayout.LayoutParams(0, dp(68), 1f));
        c.addView(row2);

        String resort = SkiResortStore.currentName(this);
        if (!resort.isEmpty()) {
            TextView resortView = text("📍 " + resort, 11, PRIMARY2, true);
            resortView.setPadding(0, dp(6), 0, 0);
            c.addView(resortView);
        }

        TextView detector = text("자동 판별: 속도 · 고도 변화 · 지속시간 · 리프트 이동 직선성을 함께 확인합니다.", 10, MUTED, false);
        detector.setPadding(0, dp(7), 0, dp(11));
        c.addView(detector);
        c.addView(actionButton("■ 기록 종료", false, v -> confirmStop()), match(dp(54)));
        page.addView(c, cardParams());
    }

    private void buildRecentRecordsCard() {
        List<File> sessions = SkiLiftStore.listSessions(this);
        LinearLayout c = card();
        c.addView(text("최근 스키 기록", 15, TEXT, true));
        int shown = 0;
        for (File dir : sessions) {
            if (shown >= 4) break;
            JSONObject m = SkiLiftStore.readSessionMeta(dir);
            if (!"complete".equals(m.optString("status", ""))) continue;
            long start = m.optLong("startEpochMs", 0);
            if (start <= 0) continue;
            String sport = "snowboard".equals(m.optString("sport")) ? "🏂 스노보드" : "⛷ 스키";
            String date = new SimpleDateFormat("M월 d일 HH:mm", Locale.KOREAN).format(new Date(start));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(9), 0, dp(4));
            LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL);
            words.addView(text(sport + " · " + date, 13, TEXT, true));
            words.addView(text(String.format(Locale.KOREAN, "활주 %d회 · %.2f km · 최고 %.1f km/h",
                    m.optInt("descentCount", 0), m.optLong("descentDistanceM", 0) / 1000.0, m.optDouble("maxSpeedKmh", 0)), 11, MUTED, false));
            row.addView(words, new LinearLayout.LayoutParams(0, dp(50), 1f));
            TextView arrow = text("›", 26, PRIMARY2, false); arrow.setGravity(Gravity.CENTER);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(30), dp(50)));
            row.setOnClickListener(v -> openSessionDetail(dir, false));
            c.addView(row);
            shown++;
        }
        if (shown == 0) {
            TextView empty = text("아직 완료된 스키 기록이 없어요.", 12, MUTED, false);
            empty.setPadding(0, dp(10), 0, 0);
            c.addView(empty);
        }
        page.addView(c, cardParams());
    }

    private void buildLiftCard() {
        int total = SkiLiftStore.countAllObservations(this);
        int pending = SkiLiftStore.countPendingHistorical(this);
        boolean realtime = SkiLiftStore.isRealtimeExchangeEnabled(this);
        long waitMs = runtime.getLong(SkiRecorderService.KEY_WAIT_TIME_MS, 0);
        long liftMs = runtime.getLong(SkiRecorderService.KEY_LIFT_TIME_MS, 0);

        LinearLayout c = card();
        c.addView(text("🚡 리프트 정보", 16, TEXT, true));
        String resortName = SkiResortStore.currentName(this);
        String summaryText = String.format(Locale.KOREAN, "내 기록 %,d건 · 미제공 %,d건", total, pending);
        if (!resortName.isEmpty()) summaryText += " · " + resortName;
        TextView summary = text(summaryText, 12, MUTED, false);
        summary.setPadding(0, dp(5), 0, dp(8));
        c.addView(summary);

        if (isRecording()) {
            LinearLayout today = new LinearLayout(this); today.setOrientation(LinearLayout.HORIZONTAL);
            today.addView(metric("오늘 대기", formatElapsed(waitMs)), new LinearLayout.LayoutParams(0, dp(62), 1f));
            today.addView(metric("리프트 시간", formatElapsed(liftMs)), new LinearLayout.LayoutParams(0, dp(62), 1f));
            c.addView(today);
        }

        LinearLayout realtimeRow = new LinearLayout(this);
        realtimeRow.setOrientation(LinearLayout.HORIZONTAL);
        realtimeRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text("실시간 리프트 정보", 14, TEXT, true));
        words.addView(text(realtime ? "최근 대기정보 제공·수신 ON" : "현재 OFF · 개인 기록만 저장", 11, realtime ? SUCCESS : MUTED, false));
        realtimeRow.addView(words, new LinearLayout.LayoutParams(0, dp(52), 1f));
        Switch toggle = new Switch(this);
        toggle.setChecked(realtime);
        toggle.setOnCheckedChangeListener((buttonView, checked) -> {
            SkiLiftStore.setRealtimeExchangeEnabled(this, checked);
            if (checked) ensureCurrentResort(null);
            render();
        });
        realtimeRow.addView(toggle, new LinearLayout.LayoutParams(dp(58), dp(52)));
        c.addView(realtimeRow);

        TextView realtimeGuide = text("ON이면 현재 스키장의 최근 리프트 대기정보만 자동 제공·수신합니다. 전체 GPS 경로는 서버로 보내지 않습니다.", 11, MUTED, false);
        realtimeGuide.setPadding(0, dp(2), 0, dp(10));
        c.addView(realtimeGuide);
        c.addView(actionButton("현재 예상 대기시간 보기", realtime, v -> showWaitTimes()), match(dp(52)));

        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        Button provide = ghostButton(providingHistorical ? "제공 중…" : "리프트 정보 제공", v -> showProvideInfo(pending));
        provide.setEnabled(!providingHistorical);
        row.addView(provide, new LinearLayout.LayoutParams(0, dp(50), 1f));
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(0, dp(50), 1f); up.leftMargin = dp(8);
        Button update = ghostButton(updatingResort ? "업데이트 중…" : "정보 업데이트", v -> showUpdateInfo());
        update.setEnabled(!updatingResort);
        row.addView(update, up);
        LinearLayout.LayoutParams rp = match(dp(50)); rp.topMargin = dp(8); c.addView(row, rp);
        page.addView(c, cardParams());
    }

    private void buildPrivacyCard() {
        LinearLayout c = card();
        c.addView(text("🔒 스키 기록 원칙", 14, TEXT, true));
        TextView p = text("전체 스키 GPS 경로는 휴대폰 내부에만 저장합니다. 수동 제공은 아직 제공하지 않은 리프트 이용 기록만 보내며, 실시간 ON은 최근 리프트 대기정보만 전송합니다.", 11, MUTED, false);
        p.setPadding(0, dp(6), 0, 0);
        c.addView(p);
        page.addView(c, cardParams());
    }

    private void selectSport(String sport) {
        prefs.edit().putString(KEY_SPORT, sport).apply();
        render();
    }

    private String selectedSport() {
        return "snowboard".equals(prefs.getString(KEY_SPORT, "ski")) ? "snowboard" : "ski";
    }

    private void startSession() {
        if (isRecording()) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            Toast.makeText(this, "위치 권한을 허용한 뒤 기록 시작을 다시 눌러주세요.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(this, SkiRecorderService.class).setAction(SkiRecorderService.ACTION_START).putExtra("sport", selectedSport());
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            Toast.makeText(this, "스키 기록을 시작합니다. 화면을 꺼도 GPS 기록은 계속됩니다.", Toast.LENGTH_LONG).show();
            handler.postDelayed(this::render, 500L);
        } catch (Exception e) {
            Toast.makeText(this, "스키 기록 시작 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmStop() {
        new AlertDialog.Builder(this)
                .setTitle("스키 기록 종료")
                .setMessage("오늘의 스키장 기록을 종료할까요? 감지된 리프트 이용 정보도 함께 휴대폰에 저장됩니다.")
                .setNegativeButton("계속 기록", null)
                .setPositiveButton("종료", (d, w) -> stopSession())
                .show();
    }

    private void stopSession() {
        String path = runtime.getString(SkiRecorderService.KEY_SESSION_DIR, "");
        File completedDir = path == null || path.isEmpty() ? null : new File(path);
        startService(new Intent(this, SkiRecorderService.class).setAction(SkiRecorderService.ACTION_STOP));
        Toast.makeText(this, "스키 기록을 저장합니다.", Toast.LENGTH_SHORT).show();
        waitForStop(completedDir, 0);
    }

    private void waitForStop(File completedDir, int attempt) {
        handler.postDelayed(() -> {
            boolean recording = isRecording();
            boolean complete = completedDir != null && completedDir.exists()
                    && "complete".equals(SkiLiftStore.readSessionMeta(completedDir).optString("status", ""));
            if (!recording && complete) {
                render();
                openSessionDetail(completedDir, true);
                return;
            }
            if (attempt >= 25) {
                render();
                if (complete) openSessionDetail(completedDir, true);
                else Toast.makeText(this, "기록 저장 상태를 확인해 주세요.", Toast.LENGTH_LONG).show();
                return;
            }
            waitForStop(completedDir, attempt + 1);
        }, 200L);
    }

    private void openSessionDetail(File dir, boolean justFinished) {
        if (dir == null || !dir.exists()) return;
        Intent i = new Intent(this, SkiSessionDetailActivity.class)
                .putExtra(SkiSessionDetailActivity.EXTRA_SESSION_PATH, dir.getAbsolutePath())
                .putExtra(SkiSessionDetailActivity.EXTRA_JUST_FINISHED, justFinished);
        startActivity(i);
    }

    private boolean isRecording() {
        return runtime != null && runtime.getBoolean(SkiRecorderService.KEY_RECORDING, false);
    }

    private void showProvideInfo(int pending) {
        if (pending <= 0) {
            new AlertDialog.Builder(this).setTitle("리프트 정보 제공")
                    .setMessage("아직 제공하지 않은 리프트 기록이 없습니다.")
                    .setPositiveButton("확인", null).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("리프트 정보 제공")
                .setMessage(String.format(Locale.KOREAN,
                        "아직 제공하지 않은 리프트 기록 %,d건을 제공합니다.\n\n전체 스키 경로와 개인 활동 기록은 전송하지 않으며, 성공한 기록만 '제공 완료'로 표시합니다.", pending))
                .setNegativeButton("취소", null)
                .setPositiveButton("제공", (d, w) -> {
                    providingHistorical = true;
                    render();
                    providePendingBatches(0);
                }).show();
    }

    private void providePendingBatches(int markedSoFar) {
        List<SkiLiftStore.PendingObservation> pending = SkiLiftStore.listPendingHistorical(this);
        if (pending.isEmpty()) {
            providingHistorical = false;
            runOnUiThread(() -> {
                render();
                new AlertDialog.Builder(this).setTitle("리프트 정보 제공 완료")
                        .setMessage(String.format(Locale.KOREAN, "%,d건을 제공했습니다. 이미 제공된 기록은 다시 전송하지 않습니다.", markedSoFar))
                        .setPositiveButton("확인", null).show();
            });
            return;
        }
        SkiLiftApi.submitHistorical(this, pending, new SkiLiftApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                List<String> ids = SkiLiftApi.acceptedIds(data);
                if (ids.isEmpty()) {
                    onFailure("서버에서 제공 완료 기록을 확인하지 못했습니다.");
                    return;
                }
                int marked = SkiLiftStore.markHistoricalProvided(SkiActivity.this, ids,
                        System.currentTimeMillis(), UUID.randomUUID().toString());
                if (marked <= 0 && !ids.isEmpty()) {
                    onFailure("휴대폰의 제공 완료 상태를 저장하지 못했습니다.");
                    return;
                }
                providePendingBatches(markedSoFar + marked);
            }
            @Override public void onFailure(String message) {
                providingHistorical = false;
                runOnUiThread(() -> {
                    render();
                    Toast.makeText(SkiActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showUpdateInfo() {
        if (updatingResort) return;
        updatingResort = true;
        render();
        ensureCurrentResort(() -> {
            String key = SkiResortStore.currentKey(this);
            if (key.isEmpty()) {
                updatingResort = false;
                render();
                Toast.makeText(this, "현재 위치에 등록된 스키장 정보가 아직 없습니다.", Toast.LENGTH_LONG).show();
                return;
            }
            SkiLiftApi.resortSnapshot(this, key, new SkiLiftApi.JsonCallback() {
                @Override public void onSuccess(JSONObject data) {
                    updatingResort = false;
                    runOnUiThread(() -> {
                        if (!data.optBoolean("found", false)) {
                            render();
                            Toast.makeText(SkiActivity.this, "등록된 스키장 정보를 찾지 못했습니다.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        SkiLiftStore.writeResortStatsCache(SkiActivity.this, key, data);
                        String name = data.optString("resort_name", SkiResortStore.currentName(SkiActivity.this));
                        SkiResortStore.setCurrent(SkiActivity.this, key, name);
                        JSONArray lifts = data.optJSONArray("lifts");
                        int count = lifts == null ? 0 : lifts.length();
                        render();
                        new AlertDialog.Builder(SkiActivity.this).setTitle("리프트 정보 업데이트 완료")
                                .setMessage(name + "\n등록 리프트 " + count + "개와 요일·시간대 통계를 휴대폰에 저장했습니다.")
                                .setPositiveButton("확인", null).show();
                    });
                }
                @Override public void onFailure(String message) {
                    updatingResort = false;
                    runOnUiThread(() -> { render(); Toast.makeText(SkiActivity.this, message, Toast.LENGTH_LONG).show(); });
                }
            });
        });
    }

    private void showWaitTimes() {
        if (!SkiLiftStore.isRealtimeExchangeEnabled(this)) {
            Toast.makeText(this, "실시간 리프트 정보를 먼저 ON 해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        ensureCurrentResort(() -> {
            String key = SkiResortStore.currentKey(this);
            if (key.isEmpty()) {
                Toast.makeText(this, "현재 위치에 등록된 스키장 정보가 아직 없습니다.", Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(new Intent(this, SkiWaitTimesActivity.class)
                    .putExtra(SkiWaitTimesActivity.EXTRA_RESORT_KEY, key)
                    .putExtra(SkiWaitTimesActivity.EXTRA_RESORT_NAME, SkiResortStore.currentName(this)));
        });
    }

    private void ensureCurrentResort(Runnable after) {
        String existing = SkiResortStore.currentKey(this);
        float lat = runtime.getFloat(SkiRecorderService.KEY_LAT, Float.NaN);
        float lon = runtime.getFloat(SkiRecorderService.KEY_LON, Float.NaN);
        if (!Float.isFinite(lat) || !Float.isFinite(lon)) {
            if (after != null) runOnUiThread(after);
            return;
        }
        SkiLiftApi.detectResort(this, lat, lon, new SkiLiftApi.JsonCallback() {
            @Override public void onSuccess(JSONObject data) {
                runOnUiThread(() -> {
                    if (data.optBoolean("found", false)) {
                        SkiResortStore.setCurrent(SkiActivity.this,
                                data.optString("resort_key", ""), data.optString("resort_name", ""));
                    } else if (existing.isEmpty()) {
                        SkiResortStore.clearCurrent(SkiActivity.this);
                    }
                    render();
                    if (after != null) after.run();
                });
            }
            @Override public void onFailure(String message) {
                runOnUiThread(() -> {
                    if (after != null && !existing.isEmpty()) after.run();
                    else if (after != null) Toast.makeText(SkiActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String stateLabel(String state) {
        if (SkiRecorderService.STATE_DESCENT.equals(state)) return "⛷ 활주 중";
        if (SkiRecorderService.STATE_LIFT.equals(state)) return "🚡 리프트 이동";
        if (SkiRecorderService.STATE_STOPPED.equals(state)) return "● 정지";
        return "GPS 움직임 판별 중";
    }

    private View metric(String label, String value) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER);
        TextView v = text(value, 15, TEXT, true); v.setGravity(Gravity.CENTER); box.addView(v);
        TextView l = text(label, 10, MUTED, false); l.setGravity(Gravity.CENTER); box.addView(l);
        return box;
    }

    private Button choiceButton(String label, boolean selected, View.OnClickListener click) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(label); b.setTextColor(TEXT); b.setTextSize(13); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(selected ? PRIMARY : CARD2, 14, selected ? 0 : 1, 0xFF35445F)); b.setOnClickListener(click); return b;
    }

    private Button actionButton(String label, boolean primary, View.OnClickListener click) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(label); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(primary ? PRIMARY : 0xFF33425B, 16, 0, 0)); b.setOnClickListener(click); return b;
    }

    private Button ghostButton(String label, View.OnClickListener click) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(label); b.setTextColor(TEXT); b.setTextSize(12);
        b.setBackground(rounded(CARD2, 13, 1, 0xFF35445F)); b.setOnClickListener(click); return b;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(16), dp(15), dp(16), dp(15)); c.setBackground(rounded(CARD, 18, 0, 0)); return c;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); v.setLineSpacing(0, 1.08f); return v;
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radiusDp)); if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor); return g;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.bottomMargin = dp(12); return p;
    }

    private LinearLayout.LayoutParams match(int height) { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height); }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private static String formatElapsed(long ms) {
        long sec = Math.max(0, ms / 1000); long h = sec / 3600; long m = (sec % 3600) / 60; long s = sec % 60;
        return h > 0 ? String.format(Locale.KOREAN, "%d:%02d:%02d", h, m, s) : String.format(Locale.KOREAN, "%02d:%02d", m, s);
    }
}
