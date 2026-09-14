package com.yamo.snorelab;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Native bridge used by the renewed v0.27 alarm UI. */
public final class AlarmBridge {
    private static final int REQUEST_NOTIFICATIONS = 7271;
    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioTrack previewTrack;
    private TextToSpeech previewTts;

    public AlarmBridge(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public String listAlarms() {
        JSONObject out = new JSONObject();
        JSONArray arr = new JSONArray();
        try {
            List<AlarmStore.Item> alarms = AlarmStore.load(activity);
            for (AlarmStore.Item item : alarms) arr.put(toUiJson(item));
            out.put("alarms", arr);
            out.put("count", arr.length());
        } catch (Exception ignored) { }
        return out.toString();
    }

    @JavascriptInterface
    public String getPermissionState() {
        JSONObject out = new JSONObject();
        try {
            out.put("exact", AlarmScheduler.canScheduleExact(activity));
            out.put("fullScreen", canUseFullScreenAlarm());
            out.put("notifications", Build.VERSION.SDK_INT < 33 || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
        } catch (Exception ignored) { }
        return out.toString();
    }

    @JavascriptInterface
    public String requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT < 31 || AlarmScheduler.canScheduleExact(activity)) return result(true, "already_granted").toString();
        activity.runOnUiThread(() -> {
            try {
                Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(i);
            } catch (Exception e) {
                try { activity.startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) { }
            }
        });
        return result(true, "opened").toString();
    }

    @JavascriptInterface
    public String requestFullScreenAlarmAccess() {
        if (Build.VERSION.SDK_INT < 34 || canUseFullScreenAlarm()) return result(true, "already_granted").toString();
        activity.runOnUiThread(() -> {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(i);
            } catch (Exception e) {
                try { activity.startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) { }
            }
        });
        return result(true, "opened").toString();
    }

    @JavascriptInterface
    public String requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return result(true, "already_granted").toString();
        }
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            }
        });
        return result(true, "requested").toString();
    }

    @JavascriptInterface
    public String saveAlarm(String rawJson) {
        JSONObject out = new JSONObject();
        try {
            JSONObject in = new JSONObject(rawJson == null ? "{}" : rawJson);
            long id = in.optLong("id", 0L);
            AlarmStore.Item existing = id > 0 ? AlarmStore.find(activity, id) : null;
            AlarmStore.Item item = existing == null ? new AlarmStore.Item() : copy(existing);
            if (id > 0) item.id = id;

            String time = in.optString("time", "07:00");
            String[] hm = time.split(":");
            item.hour = hm.length > 0 ? clamp(parseInt(hm[0], 7), 0, 23) : 7;
            item.minute = hm.length > 1 ? clamp(parseInt(hm[1], 0), 0, 59) : 0;
            item.label = cleanText(in.optString("label", "알람"), "알람", 80);
            item.enabled = in.optBoolean("enabled", true);

            for (int i = 0; i < 7; i++) item.days[i] = false;
            JSONArray days = in.optJSONArray("days");
            if (days != null) {
                for (int i = 0; i < days.length(); i++) {
                    int idx = dayIndex(days.optString(i, ""));
                    if (idx >= 0) item.days[idx] = true;
                }
            }
            if (in.has("specificDate")) item.specificDate = safeIsoDate(in.optString("specificDate", ""));
            else if (existing == null) item.specificDate = "";

            item.alertMode = "tts".equals(in.optString("method", "sound")) ? "TTS" : "SOUND";
            item.soundStyle = YamoneAlarmTone.styleForRingtoneId(in.optString("ringtoneId", "basic"));
            item.speechText = cleanText(in.optString("ttsText", "알람 시간입니다."), "알람 시간입니다.", 120);
            item.voiceStyle = "MALE".equals(in.optString("voiceStyle", "FEMALE")) ? "MALE" : "FEMALE";
            item.gradualVolume = in.optBoolean("gradual", true);
            item.vibrate = in.optBoolean("vibration", true);
            item.snoozeMinutes = clamp(in.optInt("snoozeMin", 10), 1, 60);
            item.snoozeCount = normalizeSnoozeCount(in.optInt("snoozeCount", 3));
            item.shakeToStop = in.optBoolean("shakeEnabled", false);
            item.shakeCount = clamp(in.optInt("shakeCount", 3), 3, 10);
            item.shakeStrength = "강하게".equals(in.optString("shakeStrength", "약하게")) ? "STRONG" : "WEAK";

            // The renewed editor does not expose the old missed-alarm retry setting.
            // New alarms therefore do not silently add a second retry policy.
            if (existing == null) item.retryCount = 0;
            item.volumePercent = existing == null ? 80 : item.volumePercent;
            item.gradualSeconds = existing == null ? 30 : item.gradualSeconds;

            AlarmStore.save(activity, item);
            AlarmScheduler.cancelAll(activity, item.id);
            boolean scheduled = !item.enabled || AlarmScheduler.scheduleNext(activity, item);

            out.put("ok", true);
            out.put("id", item.id);
            out.put("scheduled", scheduled);
            out.put("exactPermission", AlarmScheduler.canScheduleExact(activity));
            out.put("nextText", item.enabled ? AlarmScheduler.nextDateText(item) : "꺼짐");
        } catch (Exception e) {
            try { out.put("ok", false).put("status", "save_failed"); } catch (Exception ignored) { }
        }
        return out.toString();
    }

    @JavascriptInterface
    public String setEnabled(long id, boolean enabled) {
        AlarmStore.Item item = AlarmStore.find(activity, id);
        if (item == null) return result(false, "not_found").toString();
        item.enabled = enabled;
        AlarmStore.save(activity, item);
        AlarmScheduler.cancelAll(activity, id);
        boolean scheduled = !enabled || AlarmScheduler.scheduleNext(activity, item);
        JSONObject out = result(true, scheduled ? "saved" : "permission_required");
        try { out.put("scheduled", scheduled).put("exactPermission", AlarmScheduler.canScheduleExact(activity)); } catch (Exception ignored) { }
        return out.toString();
    }

    @JavascriptInterface
    public String toggleSkip(long id) {
        AlarmStore.Item item = AlarmStore.find(activity, id);
        if (item == null) return result(false, "not_found").toString();
        try {
            if (item.skipDate != null && !item.skipDate.isEmpty()) {
                item.skipDate = "";
            } else {
                long next = AlarmScheduler.nextTriggerMillis(item, System.currentTimeMillis());
                if (next <= 0) return result(false, "no_next_alarm").toString();
                LocalDate date = Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()).toLocalDate();
                item.skipDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            }
            AlarmStore.save(activity, item);
            boolean scheduled = !item.enabled || AlarmScheduler.scheduleNext(activity, item);
            JSONObject out = result(true, scheduled ? "saved" : "permission_required");
            out.put("skip", item.skipDate != null && !item.skipDate.isEmpty());
            out.put("scheduled", scheduled);
            return out.toString();
        } catch (Exception e) {
            return result(false, "skip_failed").toString();
        }
    }

    @JavascriptInterface
    public String deleteAlarm(long id) {
        AlarmStore.Item item = AlarmStore.find(activity, id);
        if (item == null) return result(false, "not_found").toString();
        AlarmScheduler.cancelAll(activity, id);
        AlarmStore.delete(activity, id);
        return result(true, "deleted").toString();
    }

    @JavascriptInterface
    public String previewTone(String ringtoneId) {
        final String style = YamoneAlarmTone.styleForRingtoneId(ringtoneId);
        activity.runOnUiThread(() -> playPreviewTone(style));
        return result(true, "playing").toString();
    }

    @JavascriptInterface
    public String previewTts(String text, String voiceStyle) {
        final String message = cleanText(text, "알람 시간입니다.", 120);
        final boolean male = "MALE".equals(voiceStyle);
        activity.runOnUiThread(() -> playPreviewTts(message, male));
        return result(true, "playing").toString();
    }

    @JavascriptInterface
    public String stopPreview() {
        activity.runOnUiThread(this::stopPreviewInternal);
        return result(true, "stopped").toString();
    }

    public void release() {
        activity.runOnUiThread(this::stopPreviewInternal);
    }

    private JSONObject toUiJson(AlarmStore.Item item) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", item.id);
        o.put("time", String.format(Locale.US, "%02d:%02d", item.hour, item.minute));
        o.put("label", item.label == null ? "알람" : item.label);
        JSONArray days = new JSONArray();
        String[] names = {"월", "화", "수", "목", "금", "토", "일"};
        for (int i = 0; i < 7; i++) if (item.days[i]) days.put(names[i]);
        o.put("days", days);
        o.put("enabled", item.enabled);
        o.put("method", "TTS".equals(item.alertMode) ? "tts" : "sound");
        o.put("ringtoneId", YamoneAlarmTone.ringtoneIdForStyle(item.soundStyle));
        o.put("sound", YamoneAlarmTone.displayName(item.soundStyle));
        o.put("ttsText", item.speechText == null ? "알람 시간입니다." : item.speechText);
        o.put("voiceStyle", item.voiceStyle == null ? "FEMALE" : item.voiceStyle);
        o.put("gradual", item.gradualVolume);
        o.put("vibration", item.vibrate);
        o.put("snoozeMin", item.snoozeMinutes);
        o.put("snoozeCount", item.snoozeCount);
        o.put("shakeEnabled", item.shakeToStop);
        o.put("shakeCount", item.shakeCount);
        o.put("shakeStrength", "STRONG".equals(item.shakeStrength) ? "강하게" : "약하게");
        o.put("skipNext", item.skipDate != null && !item.skipDate.isEmpty());
        o.put("specificDate", item.specificDate == null ? "" : item.specificDate);
        long next = item.enabled ? AlarmScheduler.nextTriggerMillis(item, System.currentTimeMillis()) : 0L;
        o.put("nextTriggerMs", next);
        o.put("nextText", item.enabled && next > 0 ? AlarmScheduler.nextDateText(item) : (item.enabled ? "예약 없음" : "꺼짐"));
        return o;
    }

    private void playPreviewTone(String style) {
        stopPreviewInternal();
        try {
            int sampleRate = 44_100;
            short[] pcm = YamoneAlarmTone.synth(style, sampleRate, 4);
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            previewTrack = new AudioTrack(attrs, format, pcm.length * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
            previewTrack.write(pcm, 0, pcm.length);
            previewTrack.setVolume(0.42f);
            previewTrack.play();
            handler.postDelayed(this::stopToneOnly, 4300L);
        } catch (Exception ignored) { }
    }

    private void playPreviewTts(String message, boolean male) {
        stopPreviewInternal();
        previewTts = new TextToSpeech(activity.getApplicationContext(), status -> {
            if (status != TextToSpeech.SUCCESS || previewTts == null) return;
            try {
                previewTts.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
            } catch (Exception ignored) { }
            previewTts.setLanguage(containsHangul(message) ? Locale.KOREAN : Locale.ENGLISH);
            previewTts.setSpeechRate(0.95f);
            previewTts.setPitch(male ? 0.82f : 1.10f);
            previewTts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "yamone_alarm_preview");
        });
    }

    private void stopPreviewInternal() {
        handler.removeCallbacksAndMessages(null);
        stopToneOnly();
        if (previewTts != null) {
            try { previewTts.stop(); previewTts.shutdown(); } catch (Exception ignored) { }
            previewTts = null;
        }
    }

    private void stopToneOnly() {
        if (previewTrack != null) {
            try { previewTrack.stop(); } catch (Exception ignored) { }
            try { previewTrack.release(); } catch (Exception ignored) { }
            previewTrack = null;
        }
    }

    private boolean canUseFullScreenAlarm() {
        if (Build.VERSION.SDK_INT < 34) return true;
        NotificationManager nm = activity.getSystemService(NotificationManager.class);
        return nm == null || nm.canUseFullScreenIntent();
    }

    private static AlarmStore.Item copy(AlarmStore.Item a) {
        AlarmStore.Item b = new AlarmStore.Item();
        b.id = a.id;
        b.hour = a.hour;
        b.minute = a.minute;
        b.label = a.label;
        b.enabled = a.enabled;
        System.arraycopy(a.days, 0, b.days, 0, 7);
        b.specificDate = a.specificDate;
        b.skipDate = a.skipDate;
        b.retryMinutes = a.retryMinutes;
        b.retryCount = a.retryCount;
        b.snoozeMinutes = a.snoozeMinutes;
        b.snoozeCount = a.snoozeCount;
        b.alertMode = a.alertMode;
        b.soundStyle = a.soundStyle;
        b.speechText = a.speechText;
        b.voiceStyle = a.voiceStyle;
        b.vibrate = a.vibrate;
        b.volumePercent = a.volumePercent;
        b.gradualVolume = a.gradualVolume;
        b.gradualSeconds = a.gradualSeconds;
        b.shakeToStop = a.shakeToStop;
        b.shakeCount = a.shakeCount;
        b.shakeStrength = a.shakeStrength;
        return b;
    }

    private static int dayIndex(String day) {
        if ("월".equals(day)) return 0;
        if ("화".equals(day)) return 1;
        if ("수".equals(day)) return 2;
        if ("목".equals(day)) return 3;
        if ("금".equals(day)) return 4;
        if ("토".equals(day)) return 5;
        if ("일".equals(day)) return 6;
        return -1;
    }

    private static int normalizeSnoozeCount(int count) {
        if (count >= 99 || count < 0) return 99;
        if (count <= 1) return 1;
        if (count <= 3) return 3;
        return 5;
    }

    private static String safeIsoDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        try { return LocalDate.parse(raw.trim(), DateTimeFormatter.ISO_LOCAL_DATE).format(DateTimeFormatter.ISO_LOCAL_DATE); }
        catch (Exception e) { return ""; }
    }

    private static int parseInt(String raw, int fallback) {
        try { return Integer.parseInt(raw); } catch (Exception e) { return fallback; }
    }

    private static String cleanText(String value, String fallback, int max) {
        String s = value == null ? "" : value.trim();
        if (s.isEmpty()) s = fallback;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private static boolean containsHangul(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0xAC00 && c <= 0xD7A3) return true;
        }
        return false;
    }

    private static JSONObject result(boolean ok, String status) {
        JSONObject out = new JSONObject();
        try { out.put("ok", ok).put("status", status); } catch (Exception ignored) { }
        return out;
    }
}
