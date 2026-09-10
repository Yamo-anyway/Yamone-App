package com.yamo.snorelab;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AlarmRingService extends Service {
    public static final String ACTION_START = "com.yamo.snorelab.ALARM_RING_START";
    public static final String ACTION_STOP = "com.yamo.snorelab.ALARM_RING_STOP";
    private static final String CHANNEL = "snorelab_alarm_v1";
    private static final int NOTIFICATION_ID = 7711;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioTrack audioTrack;
    private TextToSpeech tts;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private long alarmId = -1L;
    private AlarmStore.Item item;
    private boolean ringing;
    private long ringStartedAt;

    private final Runnable autoStop = () -> {
        if (item != null && item.retryCount == 0 && alarmId >= 0) {
            AlarmScheduler.markActive(this, alarmId, false, 0);
        }
        stopRinging(false);
    };
    private final Runnable volumeRamp = new Runnable() {
        @Override public void run() {
            if (!ringing || item == null) return;
            float volume = currentVolume();
            if (audioTrack != null) {
                try { audioTrack.setVolume(volume); } catch (Exception ignored) {}
            }
            if (item.gradualVolume && volume + 0.01f < targetVolume()) {
                handler.postDelayed(this, 500L);
            }
        }
    };
    private final Runnable speechLoop = new Runnable() {
        @Override public void run() {
            if (!ringing || item == null || !"TTS".equals(item.alertMode) || tts == null) return;
            String speech = item.speechText == null ? "" : item.speechText.trim();
            if (speech.isEmpty()) speech = "일어날 시간입니다";
            speakText(speech);
            long hold = Math.min(12_000L, Math.max(3_500L, speech.length() * 190L));
            handler.postDelayed(this, Math.max(7_000L, hold + 2_500L));
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            long id = intent.getLongExtra("alarm_id", alarmId);
            AlarmStore.Item found = AlarmStore.find(this, id);
            if (found != null && found.shakeToStop) return START_NOT_STICKY;
            if (id >= 0) AlarmScheduler.dismiss(this, id);
            stopRinging(true);
            return START_NOT_STICKY;
        }

        alarmId = intent == null ? -1L : intent.getLongExtra("alarm_id", -1L);
        item = AlarmStore.find(this, alarmId);
        if (item == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification(item));
        beginRinging();
        return START_NOT_STICKY;
    }

    private void beginRinging() {
        stopMediaOnly();
        ringing = true;
        ringStartedAt = System.currentTimeMillis();
        acquireWakeLock();

        if ("TTS".equals(item.alertMode)) startTts();
        else startSynthSound(item.soundStyle);

        if (item.vibrate) startVibration();
        handler.removeCallbacks(volumeRamp);
        if (item.gradualVolume) handler.post(volumeRamp);
        handler.removeCallbacks(autoStop);
        handler.postDelayed(autoStop, 60_000L);
    }

    private Notification buildNotification(AlarmStore.Item alarm) {
        Intent full = new Intent(this, AlarmRingActivity.class)
                .putExtra("alarm_id", alarm.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent fullPi = PendingIntent.getActivity(this, (int) (alarm.id & 0x3fffffff), full,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        String method = "TTS".equals(alarm.alertMode) ? "텍스트 읽기" : "알람음";
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(String.format(Locale.KOREAN, "%02d:%02d  %s", alarm.hour, alarm.minute, alarm.label))
                .setContentText(alarm.shakeToStop ? method + " · 흔들어서 종료" : method)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(fullPi)
                .setFullScreenIntent(fullPi, true);

        // Shake-only alarms intentionally do not expose a notification stop shortcut.
        if (!alarm.shakeToStop) {
            Intent stop = new Intent(this, AlarmReceiver.class)
                    .setAction(AlarmReceiver.ACTION_STOP)
                    .putExtra("alarm_id", alarm.id);
            PendingIntent stopPi = PendingIntent.getBroadcast(this, (int) (alarm.id & 0x3fffffff) + 17, stop,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel, "알람 종료", stopPi).build());
        }
        return b.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "야모네 알람", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("사용자가 직접 설정한 정확 시간 알람");
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setSound(null, null);
        ch.enableVibration(false);
        nm.createNotificationChannel(ch);
    }

    private void startSynthSound(String style) {
        try {
            int sampleRate = 44_100;
            short[] pcm = AlarmActivity.synth(style, sampleRate, 4);
            AudioAttributes attrs = alarmAudioAttributes();
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            audioTrack = new AudioTrack(attrs, format, pcm.length * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
            audioTrack.write(pcm, 0, pcm.length);
            audioTrack.setLoopPoints(0, pcm.length, -1);
            audioTrack.setVolume(currentVolume());
            audioTrack.play();
        } catch (Exception ignored) {}
    }

    private AudioAttributes alarmAudioAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    }

    private float targetVolume() {
        if (item == null) return 0.8f;
        return Math.max(0.10f, Math.min(1.0f, item.volumePercent / 100f));
    }

    private float currentVolume() {
        float target = targetVolume();
        if (item == null || !item.gradualVolume) return target;
        long elapsed = Math.max(0L, System.currentTimeMillis() - ringStartedAt);
        long total = Math.max(10, item.gradualSeconds) * 1000L;
        float fraction = Math.min(1f, elapsed / (float) total);
        float start = Math.max(0.08f, target * 0.18f);
        return Math.min(target, start + (target - start) * fraction);
    }

    private void startVibration() {
        try {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            long[] pattern = {0, 650, 250, 650, 500};
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            else vibrator.vibrate(pattern, 0);
        } catch (Exception ignored) {}
    }

    private void startTts() {
        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status != TextToSpeech.SUCCESS || !ringing || item == null || !"TTS".equals(item.alertMode)) return;
            try { tts.setAudioAttributes(alarmAudioAttributes()); } catch (Exception ignored) {}
            tts.setSpeechRate(0.95f);
            tts.setPitch("MALE".equals(item.voiceStyle) ? 0.82f : 1.12f);
            handler.postDelayed(speechLoop, 400L);
        });
    }

    private void speakText(String text) {
        if (tts == null || text == null || text.trim().isEmpty()) return;
        List<String> parts = splitLanguageRuns(text.trim());
        tts.stop();
        int idx = 0;
        for (String part : parts) {
            if (containsHangul(part)) tts.setLanguage(Locale.KOREAN);
            else tts.setLanguage(Locale.ENGLISH);
            Bundle params = new Bundle();
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, currentVolume());
            tts.speak(part, idx == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, params,
                    "alarm_" + alarmId + "_" + idx);
            idx++;
        }
    }

    private static List<String> splitLanguageRuns(String text) {
        ArrayList<String> out = new ArrayList<>();
        if (text.isEmpty()) return out;
        StringBuilder b = new StringBuilder();
        boolean currentKo = containsHangul(String.valueOf(text.charAt(0)));
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean ko = c >= 0xAC00 && c <= 0xD7A3;
            boolean neutral = Character.isWhitespace(c) || Character.isDigit(c) || ",.!?:;'-/".indexOf(c) >= 0;
            if (!neutral && ko != currentKo && b.length() > 0) {
                out.add(b.toString());
                b.setLength(0);
                currentKo = ko;
            }
            b.append(c);
        }
        if (b.length() > 0) out.add(b.toString());
        return out;
    }

    private static boolean containsHangul(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0xAC00 && c <= 0xD7A3) return true;
        }
        return false;
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null) return;
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Yamone:AlarmRing");
            wakeLock.acquire(70_000L);
        } catch (Exception ignored) {}
    }

    private void stopMediaOnly() {
        handler.removeCallbacks(speechLoop);
        handler.removeCallbacks(volumeRamp);
        if (audioTrack != null) {
            try { audioTrack.stop(); } catch (Exception ignored) {}
            try { audioTrack.release(); } catch (Exception ignored) {}
            audioTrack = null;
        }
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
        }
        if (vibrator != null) {
            try { vibrator.cancel(); } catch (Exception ignored) {}
            vibrator = null;
        }
    }

    private void stopRinging(boolean userDismissed) {
        ringing = false;
        handler.removeCallbacks(autoStop);
        handler.removeCallbacks(speechLoop);
        handler.removeCallbacks(volumeRamp);
        stopMediaOnly();
        if (wakeLock != null && wakeLock.isHeld()) {
            try { wakeLock.release(); } catch (Exception ignored) {}
        }
        wakeLock = null;
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        ringing = false;
        stopMediaOnly();
        if (wakeLock != null && wakeLock.isHeld()) {
            try { wakeLock.release(); } catch (Exception ignored) {}
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
