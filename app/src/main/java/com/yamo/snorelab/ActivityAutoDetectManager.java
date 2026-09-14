package com.yamo.snorelab;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Low-power walking/running/cycling auto-detection built on the Google Activity
 * Recognition Transition API. GPS recording itself still lives in
 * {@link WalkingRecorderService}; this class only decides when to offer/start it.
 */
public final class ActivityAutoDetectManager {
    public static final String PREFS = "yamone_activity_auto_detect_v1";
    public static final String KEY_WALK_ENABLED = "walk_enabled";
    public static final String KEY_RUN_ENABLED = "run_enabled";
    public static final String KEY_BIKE_ENABLED = "bike_enabled";
    public static final String KEY_START_MODE = "start_mode";
    public static final String KEY_REPROMPT_MIN = "reprompt_min";
    public static final String KEY_SENSITIVITY = "sensitivity";
    public static final String KEY_END_MODE = "end_mode";

    static final String ACTION_TRANSITION = "com.yamo.snorelab.AUTO_ACTIVITY_TRANSITION";
    static final String ACTION_CONFIRM_CANDIDATE = "com.yamo.snorelab.AUTO_ACTIVITY_CONFIRM_CANDIDATE";
    static final String ACTION_REPROMPT = "com.yamo.snorelab.AUTO_ACTIVITY_REPROMPT";
    static final String ACTION_PROMPT_START = "com.yamo.snorelab.AUTO_ACTIVITY_PROMPT_START";
    static final String ACTION_PROMPT_CANCEL = "com.yamo.snorelab.AUTO_ACTIVITY_PROMPT_CANCEL";
    static final String ACTION_END_CHECK = "com.yamo.snorelab.AUTO_ACTIVITY_END_CHECK";
    static final String ACTION_END_SAVE = "com.yamo.snorelab.AUTO_ACTIVITY_END_SAVE";
    static final String ACTION_END_KEEP = "com.yamo.snorelab.AUTO_ACTIVITY_END_KEEP";
    static final String EXTRA_TYPE = "auto_activity_type";

    static final String TYPE_WALK = "walking";
    static final String TYPE_RUN = "running";
    static final String TYPE_BIKE = "cycling";

    private static final String KEY_ACTIVE_WALK = "active_walking";
    private static final String KEY_ACTIVE_RUN = "active_running";
    private static final String KEY_ACTIVE_BIKE = "active_cycling";

    private static final String CHANNEL = "yamone_activity_auto_detect_v1";
    private static final int NOTIFY_WALK = 6611;
    private static final int NOTIFY_RUN = 6612;
    private static final int NOTIFY_BIKE = 6613;
    private static final int NOTIFY_END = 6619;

    private static final int REQ_TRANSITIONS = 6600;
    private static final int REQ_CANDIDATE_BASE = 6620;
    private static final int REQ_REPROMPT_BASE = 6630;
    private static final int REQ_END_CHECK = 6640;
    private static final int REQ_PROMPT_START_BASE = 6650;
    private static final int REQ_PROMPT_CANCEL_BASE = 6660;
    private static final int REQ_END_SAVE = 6670;
    private static final int REQ_END_KEEP = 6671;

    private static final long END_INACTIVITY_MS = 3 * 60_000L;

    private ActivityAutoDetectManager() {}

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean anyEnabled(Context context) {
        SharedPreferences p = prefs(context);
        return p.getBoolean(KEY_WALK_ENABLED, false)
                || p.getBoolean(KEY_RUN_ENABLED, false)
                || p.getBoolean(KEY_BIKE_ENABLED, false);
    }

    public static void syncRegistration(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences p = prefs(app);
        clearDisabledState(app, p);
        if (!anyEnabled(app) || !hasRecognitionPermission(app)) {
            removeRegistration(app);
            return;
        }

        List<ActivityTransition> transitions = new ArrayList<>();
        if (p.getBoolean(KEY_WALK_ENABLED, false)) addTransitions(transitions, DetectedActivity.WALKING);
        if (p.getBoolean(KEY_RUN_ENABLED, false)) addTransitions(transitions, DetectedActivity.RUNNING);
        if (p.getBoolean(KEY_BIKE_ENABLED, false)) addTransitions(transitions, DetectedActivity.ON_BICYCLE);
        if (transitions.isEmpty()) {
            removeRegistration(app);
            return;
        }

        try {
            ActivityTransitionRequest request = new ActivityTransitionRequest(transitions);
            ActivityRecognition.getClient(app)
                    .requestActivityTransitionUpdates(request, transitionPendingIntent(app));
        } catch (RuntimeException ignored) { }
    }

    private static void removeRegistration(Context context) {
        try {
            ActivityRecognition.getClient(context)
                    .removeActivityTransitionUpdates(transitionPendingIntent(context));
        } catch (RuntimeException ignored) { }
    }

    private static void addTransitions(List<ActivityTransition> out, int activityType) {
        out.add(new ActivityTransition.Builder()
                .setActivityType(activityType)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());
        out.add(new ActivityTransition.Builder()
                .setActivityType(activityType)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build());
    }

    static void handleIntent(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (ACTION_TRANSITION.equals(action)) {
            handleTransition(context, intent);
            return;
        }
        String type = normalizeType(intent.getStringExtra(EXTRA_TYPE));
        if (ACTION_CONFIRM_CANDIDATE.equals(action)) confirmCandidate(context, type);
        else if (ACTION_REPROMPT.equals(action)) reprompt(context, type);
        else if (ACTION_PROMPT_START.equals(action)) startFromPrompt(context, type);
        else if (ACTION_PROMPT_CANCEL.equals(action)) cancelPrompt(context, type);
        else if (ACTION_END_CHECK.equals(action)) checkAutoEnd(context);
        else if (ACTION_END_SAVE.equals(action)) endAndSave(context);
        else if (ACTION_END_KEEP.equals(action)) keepRecording(context);
    }

    private static void handleTransition(Context context, Intent intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return;
        ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
        if (result == null) return;
        for (ActivityTransitionEvent event : result.getTransitionEvents()) {
            String type = fromDetectedActivity(event.getActivityType());
            if (type == null) continue;
            if (event.getTransitionType() == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                onEnter(context, type);
            } else if (event.getTransitionType() == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                onExit(context, type);
            }
        }
    }

    private static void onEnter(Context context, String type) {
        SharedPreferences p = prefs(context);
        if (!isEnabled(p, type)) return;
        setActive(p, type, true);
        cancelAlarm(context, ACTION_REPROMPT, REQ_REPROMPT_BASE + typeIndex(type), type);
        if (isRecording(context)) return;
        scheduleAlarm(context, ACTION_CONFIRM_CANDIDATE,
                REQ_CANDIDATE_BASE + typeIndex(type), type, detectionDelayMs(p, type));
    }

    private static void onExit(Context context, String type) {
        SharedPreferences p = prefs(context);
        setActive(p, type, false);
        cancelAlarm(context, ACTION_CONFIRM_CANDIDATE, REQ_CANDIDATE_BASE + typeIndex(type), type);
        cancelAlarm(context, ACTION_REPROMPT, REQ_REPROMPT_BASE + typeIndex(type), type);
        if (isRecording(context)) {
            scheduleAlarm(context, ACTION_END_CHECK, REQ_END_CHECK, type, END_INACTIVITY_MS);
        }
    }

    private static void confirmCandidate(Context context, String type) {
        SharedPreferences p = prefs(context);
        if (!isEnabled(p, type) || !isActive(p, type) || isRecording(context)) return;
        String mode = p.getString(KEY_START_MODE, "ask");
        if ("auto".equals(mode) && canAutoStartLocation(context)) {
            if (startRecorder(context, type)) return;
        }
        showStartPrompt(context, type);
    }

    private static void reprompt(Context context, String type) {
        SharedPreferences p = prefs(context);
        if (!isEnabled(p, type) || !isActive(p, type) || isRecording(context)) return;
        showStartPrompt(context, type);
    }

    private static void startFromPrompt(Context context, String type) {
        cancelStartPrompt(context, type);
        cancelAlarm(context, ACTION_REPROMPT, REQ_REPROMPT_BASE + typeIndex(type), type);
        if (!isRecording(context)) startRecorder(context, type);
    }

    private static void cancelPrompt(Context context, String type) {
        cancelStartPrompt(context, type);
        SharedPreferences p = prefs(context);
        if (!isEnabled(p, type) || !isActive(p, type)) return;
        int minutes = sanitizeReprompt(p.getInt(KEY_REPROMPT_MIN, 5));
        scheduleAlarm(context, ACTION_REPROMPT,
                REQ_REPROMPT_BASE + typeIndex(type), type, minutes * 60_000L);
    }

    private static void checkAutoEnd(Context context) {
        if (!isRecording(context) || !startedByAutoDetect(context) || anyActive(context)) return;
        String mode = prefs(context).getString(KEY_END_MODE, "ask");
        if ("auto".equals(mode)) endAndSave(context);
        else showEndPrompt(context);
    }

    private static void endAndSave(Context context) {
        cancelEndPrompt(context);
        if (!isRecording(context) || !startedByAutoDetect(context)) return;
        Intent service = new Intent(context, WalkingRecorderService.class)
                .setAction(WalkingRecorderService.ACTION_STOP);
        try { context.startService(service); } catch (RuntimeException ignored) { }
    }

    private static void keepRecording(Context context) {
        cancelEndPrompt(context);
    }

    private static boolean startRecorder(Context context, String detectedType) {
        if (!hasForegroundLocationPermission(context)) {
            showPermissionPrompt(context, detectedType);
            return false;
        }
        String recorderType = recorderType(context, detectedType);
        Intent service = new Intent(context, WalkingRecorderService.class)
                .setAction(WalkingRecorderService.ACTION_START)
                .putExtra("activity_type", recorderType)
                .putExtra(WalkingRecorderService.EXTRA_STARTED_BY_AUTO_DETECT, true);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
            return true;
        } catch (RuntimeException e) {
            showStartPrompt(context, detectedType);
            return false;
        }
    }

    private static String recorderType(Context context, String detectedType) {
        SharedPreferences p = prefs(context);
        if ((TYPE_WALK.equals(detectedType) || TYPE_RUN.equals(detectedType))
                && p.getBoolean(KEY_WALK_ENABLED, false)
                && p.getBoolean(KEY_RUN_ENABLED, false)) {
            return "walkrun";
        }
        return detectedType;
    }

    private static void showStartPrompt(Context context, String type) {
        ensureChannel(context);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        PendingIntent start = actionPendingIntent(context, ACTION_PROMPT_START,
                REQ_PROMPT_START_BASE + typeIndex(type), type);
        PendingIntent cancel = actionPendingIntent(context, ACTION_PROMPT_CANCEL,
                REQ_PROMPT_CANCEL_BASE + typeIndex(type), type);
        Intent openIntent = new Intent(context, YamoneMovementActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(context, 6680 + typeIndex(type), openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(typeLabel(type) + "를 감지했어요")
                .setContentText("활동 기록을 시작할까요?")
                .setContentIntent(open)
                .setAutoCancel(false)
                .addAction(new Notification.Action.Builder(null, "기록 시작", start).build())
                .addAction(new Notification.Action.Builder(null, "취소", cancel).build())
                .build();
        try { nm.notify(notificationId(type), notification); } catch (SecurityException ignored) { }
    }

    private static void showPermissionPrompt(Context context, String type) {
        ensureChannel(context);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Intent openIntent = new Intent(context, YamoneMovementActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(context, 6690 + typeIndex(type), openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(typeLabel(type) + " 기록 준비")
                .setContentText("위치 권한을 확인한 뒤 기록을 시작해 주세요.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        try { nm.notify(notificationId(type), n); } catch (SecurityException ignored) { }
    }

    private static void showEndPrompt(Context context) {
        ensureChannel(context);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        PendingIntent save = actionPendingIntent(context, ACTION_END_SAVE, REQ_END_SAVE, null);
        PendingIntent keep = actionPendingIntent(context, ACTION_END_KEEP, REQ_END_KEEP, null);
        Notification n = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("활동이 끝난 것 같아요")
                .setContentText("현재 기록을 종료할까요?")
                .setOngoing(false)
                .addAction(new Notification.Action.Builder(null, "종료", save).build())
                .addAction(new Notification.Action.Builder(null, "계속 기록", keep).build())
                .build();
        try { nm.notify(NOTIFY_END, n); } catch (SecurityException ignored) { }
    }

    private static void cancelStartPrompt(Context context, String type) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(notificationId(type));
    }

    private static void cancelEndPrompt(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFY_END);
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "활동 자동감지", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("걷기·달리기·자전거 자동감지와 시작/종료 확인 알림");
        nm.createNotificationChannel(channel);
    }

    private static PendingIntent transitionPendingIntent(Context context) {
        Intent intent = new Intent(context, ActivityAutoDetectReceiver.class).setAction(ACTION_TRANSITION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        return PendingIntent.getBroadcast(context, REQ_TRANSITIONS, intent, flags);
    }

    private static PendingIntent actionPendingIntent(Context context, String action, int requestCode, String type) {
        Intent intent = new Intent(context, ActivityAutoDetectReceiver.class).setAction(action);
        if (type != null) intent.putExtra(EXTRA_TYPE, type);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void scheduleAlarm(Context context, String action, int requestCode, String type, long delayMs) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = actionPendingIntent(context, action, requestCode, type);
        long when = SystemClock.elapsedRealtime() + Math.max(1_000L, delayMs);
        try {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, pi);
        } catch (RuntimeException ignored) { }
    }

    private static void cancelAlarm(Context context, String action, int requestCode, String type) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try { am.cancel(actionPendingIntent(context, action, requestCode, type)); }
        catch (RuntimeException ignored) { }
    }

    private static long detectionDelayMs(SharedPreferences p, String type) {
        String sensitivity = p.getString(KEY_SENSITIVITY, "normal");
        if ("fast".equals(sensitivity)) {
            if (TYPE_RUN.equals(type)) return 15_000L;
            return 30_000L;
        }
        if ("accurate".equals(sensitivity)) {
            if (TYPE_RUN.equals(type)) return 45_000L;
            if (TYPE_BIKE.equals(type)) return 90_000L;
            return 120_000L;
        }
        if (TYPE_RUN.equals(type)) return 30_000L;
        return 60_000L;
    }

    private static boolean canAutoStartLocation(Context context) {
        if (Build.VERSION.SDK_INT < 29) return true;
        if (context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED) return true;
        ActivityManager.RunningAppProcessInfo info = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(info);
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
    }

    private static boolean hasForegroundLocationPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean hasRecognitionPermission(Context context) {
        return Build.VERSION.SDK_INT < 29
                || context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean hasBackgroundLocation(Context context) {
        return Build.VERSION.SDK_INT < 29
                || context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isRecording(Context context) {
        return context.getSharedPreferences(WalkingRecorderService.PREFS, Context.MODE_PRIVATE)
                .getBoolean(WalkingRecorderService.KEY_RECORDING, false);
    }

    private static boolean startedByAutoDetect(Context context) {
        return context.getSharedPreferences(WalkingRecorderService.PREFS, Context.MODE_PRIVATE)
                .getBoolean(WalkingRecorderService.KEY_STARTED_BY_AUTO_DETECT, false);
    }

    private static boolean anyActive(Context context) {
        SharedPreferences p = prefs(context);
        return p.getBoolean(KEY_ACTIVE_WALK, false)
                || p.getBoolean(KEY_ACTIVE_RUN, false)
                || p.getBoolean(KEY_ACTIVE_BIKE, false);
    }

    private static void clearDisabledState(Context context, SharedPreferences p) {
        SharedPreferences.Editor e = p.edit();
        if (!p.getBoolean(KEY_WALK_ENABLED, false)) {
            e.putBoolean(KEY_ACTIVE_WALK, false);
            cancelAlarm(context, ACTION_CONFIRM_CANDIDATE, REQ_CANDIDATE_BASE, TYPE_WALK);
            cancelAlarm(context, ACTION_REPROMPT, REQ_REPROMPT_BASE, TYPE_WALK);
        }
        if (!p.getBoolean(KEY_RUN_ENABLED, false)) {
            e.putBoolean(KEY_ACTIVE_RUN, false);
            cancelAlarm(context, ACTION_CONFIRM_CANDIDATE, REQ_CANDIDATE_BASE + 1, TYPE_RUN);
            cancelAlarm(context, ACTION_REPROMPT, REQ_REPROMPT_BASE + 1, TYPE_RUN);
        }
        if (!p.getBoolean(KEY_BIKE_ENABLED, false)) {
            e.putBoolean(KEY_ACTIVE_BIKE, false);
            cancelAlarm(context, ACTION_CONFIRM_CANDIDATE, REQ_CANDIDATE_BASE + 2, TYPE_BIKE);
            cancelAlarm(context, ACTION_REPROMPT, REQ_REPROMPT_BASE + 2, TYPE_BIKE);
        }
        e.apply();
    }

    private static boolean isEnabled(SharedPreferences p, String type) {
        if (TYPE_RUN.equals(type)) return p.getBoolean(KEY_RUN_ENABLED, false);
        if (TYPE_BIKE.equals(type)) return p.getBoolean(KEY_BIKE_ENABLED, false);
        return p.getBoolean(KEY_WALK_ENABLED, false);
    }

    private static boolean isActive(SharedPreferences p, String type) {
        return p.getBoolean(activeKey(type), false);
    }

    private static void setActive(SharedPreferences p, String type, boolean value) {
        p.edit().putBoolean(activeKey(type), value).apply();
    }

    private static String activeKey(String type) {
        if (TYPE_RUN.equals(type)) return KEY_ACTIVE_RUN;
        if (TYPE_BIKE.equals(type)) return KEY_ACTIVE_BIKE;
        return KEY_ACTIVE_WALK;
    }

    private static String fromDetectedActivity(int activity) {
        if (activity == DetectedActivity.WALKING) return TYPE_WALK;
        if (activity == DetectedActivity.RUNNING) return TYPE_RUN;
        if (activity == DetectedActivity.ON_BICYCLE) return TYPE_BIKE;
        return null;
    }

    private static String normalizeType(String type) {
        if (TYPE_RUN.equals(type) || TYPE_BIKE.equals(type)) return type;
        return TYPE_WALK;
    }

    private static int typeIndex(String type) {
        if (TYPE_RUN.equals(type)) return 1;
        if (TYPE_BIKE.equals(type)) return 2;
        return 0;
    }

    private static int notificationId(String type) {
        if (TYPE_RUN.equals(type)) return NOTIFY_RUN;
        if (TYPE_BIKE.equals(type)) return NOTIFY_BIKE;
        return NOTIFY_WALK;
    }

    private static String typeLabel(String type) {
        if (TYPE_RUN.equals(type)) return "달리기";
        if (TYPE_BIKE.equals(type)) return "자전거";
        return "걷기";
    }

    static int sanitizeReprompt(int minutes) {
        return minutes == 10 ? 10 : 5;
    }
}
