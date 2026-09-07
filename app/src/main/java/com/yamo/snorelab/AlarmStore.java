package com.yamo.snorelab;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class AlarmStore {
    // Keep the existing preference key so previously created test alarms migrate automatically.
    private static final String PREFS = "snorelab_alarm_store_v1";
    private static final String KEY = "alarms";

    private AlarmStore() {}

    public static final class Item {
        public long id = System.currentTimeMillis();
        public int hour = 7;
        public int minute = 0;
        public String label = "알람";
        public boolean enabled = true;

        // Monday=0 ... Sunday=6. No selected day means a one-time alarm.
        public final boolean[] days = new boolean[7];
        // Optional ISO yyyy-MM-dd date. When set, this takes priority over weekday repeat.
        public String specificDate = "";
        public String skipDate = "";

        // Missed-alarm retry policy.
        public int retryMinutes = 5;
        public int retryCount = 3;
        // User-triggered snooze interval from the ringing screen.
        public int snoozeMinutes = 5;

        // Exactly one primary alert method is active: SOUND or TTS.
        public String alertMode = "SOUND";
        public String soundStyle = "STRONG";
        public String speechText = "일어날 시간입니다";
        public String voiceStyle = "FEMALE";

        // Device cues / loudness.
        public boolean vibrate = true;
        public int volumePercent = 80;
        public boolean gradualVolume = true;
        public int gradualSeconds = 30;

        // When enabled, ordinary stop controls must not dismiss the alarm.
        // The user has to complete the configured shake count. Snooze remains available.
        public boolean shakeToStop = false;
        public int shakeCount = 3;

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("hour", hour);
            o.put("minute", minute);
            o.put("label", label);
            o.put("enabled", enabled);
            JSONArray d = new JSONArray();
            for (boolean day : days) d.put(day);
            o.put("days", d);
            o.put("specificDate", specificDate);
            o.put("skipDate", skipDate);
            o.put("retryMinutes", retryMinutes);
            o.put("retryCount", retryCount);
            o.put("snoozeMinutes", snoozeMinutes);
            o.put("alertMode", alertMode);
            o.put("soundStyle", soundStyle);
            o.put("speechText", speechText);
            o.put("voiceStyle", voiceStyle);
            o.put("vibrate", vibrate);
            o.put("volumePercent", volumePercent);
            o.put("gradualVolume", gradualVolume);
            o.put("gradualSeconds", gradualSeconds);
            o.put("shakeToStop", shakeToStop);
            o.put("shakeCount", shakeCount);
            // Compatibility with old records.
            o.put("ttsEnabled", "TTS".equals(alertMode));
            return o;
        }

        static Item fromJson(JSONObject o) {
            Item a = new Item();
            a.id = o.optLong("id", System.currentTimeMillis());
            a.hour = clamp(o.optInt("hour", 7), 0, 23);
            a.minute = clamp(o.optInt("minute", 0), 0, 59);
            a.label = o.optString("label", "알람");
            a.enabled = o.optBoolean("enabled", true);
            JSONArray d = o.optJSONArray("days");
            if (d != null) {
                for (int i = 0; i < Math.min(7, d.length()); i++) a.days[i] = d.optBoolean(i, false);
            }
            a.specificDate = o.optString("specificDate", "");
            a.skipDate = o.optString("skipDate", "");
            a.retryMinutes = clamp(o.optInt("retryMinutes", 5), 1, 60);
            a.retryCount = clampRetryCount(o.optInt("retryCount", 3));
            a.snoozeMinutes = clamp(o.optInt("snoozeMinutes", 5), 1, 60);

            a.alertMode = o.has("alertMode") ? o.optString("alertMode", "SOUND") : "SOUND";
            if (!"TTS".equals(a.alertMode)) a.alertMode = "SOUND";
            a.soundStyle = o.optString("soundStyle", "STRONG");
            if (!"PULSE".equals(a.soundStyle) && !"SOFT".equals(a.soundStyle)) {
                a.soundStyle = "STRONG";
            }
            a.speechText = o.optString("speechText", o.optString("label", "일어날 시간입니다"));
            a.voiceStyle = "MALE".equals(o.optString("voiceStyle", "FEMALE")) ? "MALE" : "FEMALE";

            a.vibrate = o.optBoolean("vibrate", true);
            a.volumePercent = clamp(o.optInt("volumePercent", 80), 10, 100);
            a.gradualVolume = o.optBoolean("gradualVolume", true);
            a.gradualSeconds = clamp(o.optInt("gradualSeconds", 30), 10, 120);

            a.shakeToStop = o.optBoolean("shakeToStop", false);
            a.shakeCount = clamp(o.optInt("shakeCount", 3), 3, 10);
            return a;
        }

        public boolean repeats() {
            if (specificDate != null && !specificDate.isEmpty()) return false;
            for (boolean day : days) if (day) return true;
            return false;
        }
    }

    public static synchronized List<Item> load(Context context) {
        ArrayList<Item> out = new ArrayList<>();
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(Item.fromJson(o));
            }
        } catch (Exception ignored) {}
        Collections.sort(out, Comparator.comparingInt((Item a) -> a.hour).thenComparingInt(a -> a.minute));
        return out;
    }

    public static synchronized Item find(Context context, long id) {
        for (Item a : load(context)) if (a.id == id) return a;
        return null;
    }

    public static synchronized void save(Context context, Item item) {
        List<Item> list = load(context);
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == item.id) {
                list.set(i, item);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(item);
        write(context, list);
    }

    public static synchronized void delete(Context context, long id) {
        List<Item> list = load(context);
        list.removeIf(a -> a.id == id);
        write(context, list);
    }

    private static void write(Context context, List<Item> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Item a : list) arr.put(a.toJson());
        } catch (Exception ignored) {}
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit().putString(KEY, arr.toString()).apply();
    }

    private static int clampRetryCount(int value) {
        if (value < 0) return -1;
        return clamp(value, 0, 10);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
