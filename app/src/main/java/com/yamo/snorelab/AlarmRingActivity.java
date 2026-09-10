package com.yamo.snorelab;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class AlarmRingActivity extends Activity implements SensorEventListener {
    private int BG;
    private int CARD;
    private int CARD2;
    private int TEXT;
    private int MUTED;
    private int PRIMARY;
    private int PRIMARY2;

    private long alarmId;
    private AlarmStore.Item item;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private int shakeCount;
    private long firstShakeAt;
    private long lastShakeAt;
    private TextView shakeProgress;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        SharedPreferences prefs = getSharedPreferences(SleepRecorderService.PREFS, MODE_PRIVATE);
        boolean pink = "pink".equals(prefs.getString("yamone_theme", "pink"));
        BG = pink ? 0xFFFFF7FA : 0xFFF7FFFB;
        CARD = 0xFFFFFFFF;
        CARD2 = pink ? 0xFFFFEEF3 : 0xFFF0FAF6;
        TEXT = pink ? 0xFF4B2633 : 0xFF153633;
        MUTED = pink ? 0xFF9A7180 : 0xFF718984;
        PRIMARY = pink ? 0xFFFF769F : 0xFF56D1B3;
        PRIMARY2 = pink ? 0xFFE94778 : 0xFF159A7A;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }

        alarmId = getIntent().getLongExtra("alarm_id", -1L);
        item = AlarmStore.find(this, alarmId);
        if (item == null) {
            finish();
            return;
        }

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(4), dp(24), dp(28));
        root.setBackgroundColor(BG);

        if (Build.VERSION.SDK_INT >= 21) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                int top;
                int bottom;
                if (Build.VERSION.SDK_INT >= 30) {
                    top = insets.getInsets(WindowInsets.Type.statusBars()).top;
                    bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                v.setPadding(dp(24), top + dp(4), dp(24), bottom + dp(22));
                return insets;
            });
            root.requestApplyInsets();
        }

        TextView brand = text("야모네 · 알람", 14, PRIMARY2, true);
        brand.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams brandLp = matchWrap();
        brandLp.topMargin = dp(12);
        root.addView(brand, brandLp);

        LinearLayout mainCard = new LinearLayout(this);
        mainCard.setOrientation(LinearLayout.VERTICAL);
        mainCard.setGravity(Gravity.CENTER_HORIZONTAL);
        mainCard.setPadding(dp(20), dp(28), dp(20), dp(26));
        mainCard.setBackground(round(CARD, 28));
        LinearLayout.LayoutParams mcp = matchWrap();
        mcp.topMargin = dp(18);
        root.addView(mainCard, mcp);

        ImageView alarmIcon = new ImageView(this);
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
        mainCard.addView(time, matchWrap());

        TextView label = text(item.label == null || item.label.isEmpty() ? "알람" : item.label, 22, TEXT, true);
        label.setGravity(Gravity.CENTER);
        label.setPadding(dp(8), dp(8), dp(8), dp(12));
        mainCard.addView(label, matchWrap());

        String method = "TTS".equals(item.alertMode) ? "텍스트 읽기" : "알람음";
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
        mainCard.addView(methodRow, methodParams);

        if ("TTS".equals(item.alertMode) && item.speechText != null && !item.speechText.trim().isEmpty()) {
            TextView speech = text("“" + item.speechText.trim() + "”", 14, MUTED, false);
            speech.setGravity(Gravity.CENTER);
            speech.setPadding(dp(10), dp(10), dp(10), 0);
            mainCard.addView(speech, matchWrap());
        }

        TextView meta = text("스누즈 " + item.snoozeMinutes + "분  ·  " + retryText(item), 12, MUTED, false);
        meta.setGravity(Gravity.CENTER);
        meta.setPadding(0, dp(10), 0, 0);
        mainCard.addView(meta, matchWrap());

        TextView spacer = new TextView(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        if (item.shakeToStop) {
            LinearLayout shakeCard = new LinearLayout(this);
            shakeCard.setOrientation(LinearLayout.VERTICAL);
            shakeCard.setGravity(Gravity.CENTER_HORIZONTAL);
            shakeCard.setPadding(dp(18), dp(18), dp(18), dp(18));
            shakeCard.setBackground(round(CARD2, 22));
            ImageView shakeIcon = new ImageView(this);
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
            shakeCard.addView(title, matchWrap());
            shakeProgress = text("0 / " + item.shakeCount + "회", 28, PRIMARY2, true);
            shakeProgress.setGravity(Gravity.CENTER);
            shakeProgress.setPadding(0, dp(8), 0, dp(5));
            shakeCard.addView(shakeProgress, matchWrap());
            TextView rule = text("일반 종료 버튼은 사용할 수 없어요.\n정한 횟수만큼 강하게 흔들면 종료됩니다.", 12, MUTED, false);
            rule.setGravity(Gravity.CENTER);
            shakeCard.addView(rule, matchWrap());
            LinearLayout.LayoutParams scp = matchWrap();
            scp.bottomMargin = dp(14);
            root.addView(shakeCard, scp);
        }

        Button snooze = button(item.snoozeMinutes + "분 후 다시", CARD2, PRIMARY2);
        snooze.setOnClickListener(v -> snooze());
        root.addView(snooze, match(dp(58)));

        if (!item.shakeToStop) {
            Button stop = button("알람 종료", PRIMARY2, 0xFFFFFFFF);
            LinearLayout.LayoutParams sp = match(dp(64));
            sp.topMargin = dp(12);
            root.addView(stop, sp);
            stop.setOnClickListener(v -> dismissAlarm(false));
        }

        setContentView(root);
    }

    private int pinkTextColor() {
        return "pink".equals(getSharedPreferences(SleepRecorderService.PREFS, MODE_PRIVATE).getString("yamone_theme", "pink"))
                ? 0xFF4B2633 : 0xFF08352A;
    }

    private String retryText(AlarmStore.Item a) {
        if (a.retryCount == 0) return "재알림 없음";
        if (a.retryCount < 0) return a.retryMinutes + "분 간격 재알림";
        return a.retryMinutes + "분 간격 · 최대 " + a.retryCount + "회";
    }

    private void snooze() {
        AlarmScheduler.cancelRetry(this, alarmId);
        if (AlarmScheduler.scheduleSnooze(this, alarmId, item.snoozeMinutes)) {
            stopService(new Intent(this, AlarmRingService.class));
            Toast.makeText(this, item.snoozeMinutes + "분 후 다시 울립니다.", Toast.LENGTH_SHORT).show();
            finishAndRemoveTask();
        } else {
            Toast.makeText(this, "정확한 알람 권한을 확인해주세요.", Toast.LENGTH_LONG).show();
        }
    }

    private void dismissAlarm(boolean fromShake) {
        if (item != null && item.shakeToStop && !fromShake) {
            Toast.makeText(this, "흔들기 횟수를 완료해야 종료할 수 있어요.", Toast.LENGTH_SHORT).show();
            return;
        }
        AlarmScheduler.dismiss(this, alarmId);
        finishAndRemoveTask();
    }

    @Override protected void onResume() {
        super.onResume();
        if (item != null && item.shakeToStop && sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override protected void onPause() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (item == null || event.sensor.getType() != Sensor.TYPE_ACCELEROMETER || !item.shakeToStop) return;
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        double magnitude = Math.sqrt(x * x + y * y + z * z);
        if (magnitude < 18.5) return;

        long now = System.currentTimeMillis();
        if (now - lastShakeAt < 280) return;
        if (firstShakeAt == 0 || now - firstShakeAt > Math.max(3500L, item.shakeCount * 900L)) {
            firstShakeAt = now;
            shakeCount = 1;
        } else {
            shakeCount++;
        }
        lastShakeAt = now;
        int required = Math.max(3, Math.min(10, item.shakeCount));
        if (shakeProgress != null) shakeProgress.setText(Math.min(shakeCount, required) + " / " + required + "회");
        if (shakeCount >= required) {
            Toast.makeText(this, "흔들기 완료 · 알람 종료", Toast.LENGTH_SHORT).show();
            dismissAlarm(true);
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override public void onBackPressed() {
        // Back must never silently dismiss a ringing alarm.
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private Button button(String s, int bg, int fg) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(fg);
        b.setBackground(round(bg, 18));
        return b;
    }

    private GradientDrawable round(int fill, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private LinearLayout.LayoutParams match(int h) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
