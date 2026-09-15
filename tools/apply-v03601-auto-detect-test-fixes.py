#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MANAGER = ROOT / 'app/src/main/java/com/yamo/snorelab/ActivityAutoDetectManager.java'
BRIDGE = ROOT / 'app/src/main/java/com/yamo/snorelab/AutoDetectSettingsBridge.java'
SETTINGS_JS = ROOT / 'app/src/main/assets/yamone-v23/v02516-auto-detect.js'
INDEX = ROOT / 'app/src/main/assets/yamone-v23/index.html'
BUILD = ROOT / 'app/build.gradle'
TARGET = ROOT / 'app/src/main/assets/yamone-v23'
VERSION_SRC = ROOT / 'design-preview/v03601-version.js'


def replace_once(path: Path, old: str, new: str, label: str):
    s = path.read_text(encoding='utf-8')
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'v0.36.01 patch target not found: {label} ({path})')
    path.write_text(s.replace(old, new, 1), encoding='utf-8')


# Notification sound / vibration preferences.
replace_once(MANAGER,
'''import android.content.pm.PackageManager;\nimport android.os.Build;''',
'''import android.content.pm.PackageManager;\nimport android.media.RingtoneManager;\nimport android.os.Build;''',
'notification ringtone import')

replace_once(MANAGER,
'''    public static final String KEY_END_MODE = "end_mode";''',
'''    public static final String KEY_END_MODE = "end_mode";\n    public static final String KEY_NOTIFY_SOUND = "notify_sound";\n    public static final String KEY_NOTIFY_VIBRATE = "notify_vibrate";''',
'notification preference keys')

replace_once(MANAGER,
'''    private static final String CHANNEL = "yamone_activity_auto_detect_v1";''',
'''    private static final String CHANNEL_BOTH = "yamone_activity_auto_detect_v2_both";\n    private static final String CHANNEL_SOUND = "yamone_activity_auto_detect_v2_sound";\n    private static final String CHANNEL_VIBRATE = "yamone_activity_auto_detect_v2_vibrate";\n    private static final String CHANNEL_SILENT = "yamone_activity_auto_detect_v2_silent";''',
'notification channel variants')

# The Transition API already performs motion-state stabilization. For normal testing,
# do not add another AlarmManager delay that can be deferred by Android.
replace_once(MANAGER,
'''        if (isRecording(context)) return;\n        scheduleAlarm(context, ACTION_CONFIRM_CANDIDATE,\n                REQ_CANDIDATE_BASE + typeIndex(type), type, detectionDelayMs(p, type));''',
'''        if (isRecording(context)) return;\n        long delayMs = detectionDelayMs(p, type);\n        if (delayMs <= 0L) {\n            confirmCandidate(context, type);\n        } else {\n            scheduleAlarm(context, ACTION_CONFIRM_CANDIDATE,\n                    REQ_CANDIDATE_BASE + typeIndex(type), type, delayMs);\n        }''',
'normal detection immediate confirmation')

# Auto means auto: never silently fall back to the ask-start notification.
replace_once(MANAGER,
'''        String mode = p.getString(KEY_START_MODE, "ask");\n        if ("auto".equals(mode) && canAutoStartLocation(context)) {\n            if (startRecorder(context, type)) return;\n        }\n        showStartPrompt(context, type);''',
'''        String mode = p.getString(KEY_START_MODE, "ask");\n        if ("auto".equals(mode)) {\n            if (!canAutoStartLocation(context)) {\n                showBackgroundLocationRequired(context, type);\n                return;\n            }\n            startRecorder(context, type);\n            return;\n        }\n        showStartPrompt(context, type);''',
'auto start must not fall back to ask')

replace_once(MANAGER,
'''        } catch (RuntimeException e) {\n            showStartPrompt(context, detectedType);\n            return false;\n        }''',
'''        } catch (RuntimeException e) {\n            if ("auto".equals(prefs(context).getString(KEY_START_MODE, "ask"))) {\n                showAutoStartBlocked(context, detectedType);\n            } else {\n                showStartPrompt(context, detectedType);\n            }\n            return false;\n        }''',
'auto start runtime failure guidance')

# All auto-detect notifications use the sound/vibration channel selected by the user.
manager_text = MANAGER.read_text(encoding='utf-8')
old_builder = 'new Notification.Builder(context, CHANNEL)'
new_builder = 'new Notification.Builder(context, notificationChannelId(context))'
count = manager_text.count(old_builder)
if count:
    MANAGER.write_text(manager_text.replace(old_builder, new_builder), encoding='utf-8')
elif manager_text.count(new_builder) < 3:
    raise SystemExit('v0.36.01 patch target not found: notification builders')

replace_once(MANAGER,
'''    private static void showPermissionPrompt(Context context, String type) {''',
'''    private static void showBackgroundLocationRequired(Context context, String type) {\n        ensureChannel(context);\n        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);\n        if (nm == null) return;\n        Intent openIntent = new Intent(context, YamoneMovementActivity.class)\n                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);\n        PendingIntent open = PendingIntent.getActivity(context, 6695 + typeIndex(type), openIntent,\n                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);\n        Notification n = new Notification.Builder(context, notificationChannelId(context))\n                .setSmallIcon(R.drawable.ic_launcher_foreground)\n                .setContentTitle(typeLabel(type) + " 자동 시작 권한 필요")\n                .setContentText("자동 시작을 위해 위치 권한을 ‘항상 허용’으로 설정해 주세요.")\n                .setContentIntent(open)\n                .setAutoCancel(true)\n                .build();\n        try { nm.notify(notificationId(type), n); } catch (SecurityException ignored) { }\n    }\n\n    private static void showAutoStartBlocked(Context context, String type) {\n        ensureChannel(context);\n        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);\n        if (nm == null) return;\n        Intent openIntent = new Intent(context, YamoneMovementActivity.class)\n                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);\n        PendingIntent open = PendingIntent.getActivity(context, 6698 + typeIndex(type), openIntent,\n                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);\n        Notification n = new Notification.Builder(context, notificationChannelId(context))\n                .setSmallIcon(R.drawable.ic_launcher_foreground)\n                .setContentTitle(typeLabel(type) + " 자동 시작 확인 필요")\n                .setContentText("위치 권한 또는 백그라운드 실행 설정을 확인해 주세요.")\n                .setContentIntent(open)\n                .setAutoCancel(true)\n                .build();\n        try { nm.notify(notificationId(type), n); } catch (SecurityException ignored) { }\n    }\n\n    private static void showPermissionPrompt(Context context, String type) {''',
'auto start permission notices')

replace_once(MANAGER,
'''    private static void ensureChannel(Context context) {\n        if (Build.VERSION.SDK_INT < 26) return;\n        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);\n        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;\n        NotificationChannel channel = new NotificationChannel(\n                CHANNEL, "활동 자동감지", NotificationManager.IMPORTANCE_DEFAULT);\n        channel.setDescription("걷기·달리기·자전거 자동감지와 시작/종료 확인 알림");\n        nm.createNotificationChannel(channel);\n    }''',
'''    private static String notificationChannelId(Context context) {\n        SharedPreferences p = prefs(context);\n        boolean sound = p.getBoolean(KEY_NOTIFY_SOUND, true);\n        boolean vibrate = p.getBoolean(KEY_NOTIFY_VIBRATE, true);\n        if (sound && vibrate) return CHANNEL_BOTH;\n        if (sound) return CHANNEL_SOUND;\n        if (vibrate) return CHANNEL_VIBRATE;\n        return CHANNEL_SILENT;\n    }\n\n    private static void ensureChannel(Context context) {\n        if (Build.VERSION.SDK_INT < 26) return;\n        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);\n        if (nm == null) return;\n        String id = notificationChannelId(context);\n        if (nm.getNotificationChannel(id) != null) return;\n        SharedPreferences p = prefs(context);\n        boolean sound = p.getBoolean(KEY_NOTIFY_SOUND, true);\n        boolean vibrate = p.getBoolean(KEY_NOTIFY_VIBRATE, true);\n        String name = "활동 자동감지" + (sound ? " · 소리" : "") + (vibrate ? " · 진동" : "");\n        if (!sound && !vibrate) name += " · 무음";\n        NotificationChannel channel = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT);\n        channel.setDescription("걷기·달리기·자전거 자동감지와 시작/종료 안내");\n        channel.enableVibration(vibrate);\n        if (sound) channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null);\n        else channel.setSound(null, null);\n        nm.createNotificationChannel(channel);\n    }''',
'notification channel preferences')

replace_once(MANAGER,
'''    private static long detectionDelayMs(SharedPreferences p, String type) {\n        String sensitivity = p.getString(KEY_SENSITIVITY, "normal");\n        if ("fast".equals(sensitivity)) {\n            if (TYPE_RUN.equals(type)) return 15_000L;\n            return 30_000L;\n        }\n        if ("accurate".equals(sensitivity)) {\n            if (TYPE_RUN.equals(type)) return 45_000L;\n            if (TYPE_BIKE.equals(type)) return 90_000L;\n            return 120_000L;\n        }\n        if (TYPE_RUN.equals(type)) return 30_000L;\n        return 60_000L;\n    }''',
'''    private static long detectionDelayMs(SharedPreferences p, String type) {\n        String sensitivity = p.getString(KEY_SENSITIVITY, "normal");\n        // Google Activity Transition ENTER is already stabilized by the platform.\n        // Fast/normal therefore react immediately instead of using a deferrable alarm.\n        if ("fast".equals(sensitivity) || "normal".equals(sensitivity)) return 0L;\n        if (TYPE_RUN.equals(type)) return 15_000L;\n        if (TYPE_BIKE.equals(type)) return 30_000L;\n        return 30_000L;\n    }''',
'detection delay tuning')

# Bridge new preferences to the Settings WebView.
replace_once(BRIDGE,
'''            out.put("endMode", sanitizeEndMode(\n                    prefs.getString(ActivityAutoDetectManager.KEY_END_MODE, "ask")));''',
'''            out.put("endMode", sanitizeEndMode(\n                    prefs.getString(ActivityAutoDetectManager.KEY_END_MODE, "ask")));\n            out.put("notifySound", prefs.getBoolean(ActivityAutoDetectManager.KEY_NOTIFY_SOUND, true));\n            out.put("notifyVibrate", prefs.getBoolean(ActivityAutoDetectManager.KEY_NOTIFY_VIBRATE, true));''',
'bridge notification settings output')

replace_once(BRIDGE,
'''            String endMode = sanitizeEndMode(in.optString("endMode", "ask"));''',
'''            String endMode = sanitizeEndMode(in.optString("endMode", "ask"));\n            boolean notifySound = in.optBoolean("notifySound",\n                    prefs.getBoolean(ActivityAutoDetectManager.KEY_NOTIFY_SOUND, true));\n            boolean notifyVibrate = in.optBoolean("notifyVibrate",\n                    prefs.getBoolean(ActivityAutoDetectManager.KEY_NOTIFY_VIBRATE, true));''',
'bridge notification settings input')

replace_once(BRIDGE,
'''                    .putString(ActivityAutoDetectManager.KEY_END_MODE, endMode)\n                    .apply();''',
'''                    .putString(ActivityAutoDetectManager.KEY_END_MODE, endMode)\n                    .putBoolean(ActivityAutoDetectManager.KEY_NOTIFY_SOUND, notifySound)\n                    .putBoolean(ActivityAutoDetectManager.KEY_NOTIFY_VIBRATE, notifyVibrate)\n                    .apply();''',
'bridge notification settings persistence')

# Settings UI: expose sound/vibration toggles and make auto-start permission behavior explicit.
replace_once(SETTINGS_JS,
'''    return {walk:false,run:false,bike:false,startMode:'ask',repromptMin:5,sensitivity:'normal',endMode:'ask',activityRecognition:false,backgroundLocation:false,notifications:false};''',
'''    return {walk:false,run:false,bike:false,startMode:'ask',repromptMin:5,sensitivity:'normal',endMode:'ask',notifySound:true,notifyVibrate:true,activityRecognition:false,backgroundLocation:false,notifications:false};''',
'js defaults')

replace_once(SETTINGS_JS,
'''          sensitivity:['fast','normal','accurate'].includes(s.sensitivity)?s.sensitivity:'normal',\n          endMode:s.endMode==='auto'?'auto':'ask' ''',
'''          sensitivity:['fast','normal','accurate'].includes(s.sensitivity)?s.sensitivity:'normal',\n          endMode:s.endMode==='auto'?'auto':'ask',\n          notifySound:s.notifySound!==false,\n          notifyVibrate:s.notifyVibrate!==false ''',
'js save notification settings')

replace_once(SETTINGS_JS,
'''      ${settingSection('자동 종료',`<div class="card setting-block v2516-two">${settingChips('v2516EndMode',[['auto','자동 종료'],['ask','확인 후 종료']],s.endMode)}</div>`)}\n      ${(!s.activityRecognition||!s.notifications)?''',
'''      ${settingSection('자동 종료',`<div class="card setting-block v2516-two">${settingChips('v2516EndMode',[['auto','자동 종료'],['ask','확인 후 종료']],s.endMode)}</div>`)}\n      ${settingSection('자동감지 알림',\n        settingToggle('v3601Sound','알림 소리','자동감지 안내 소리',s.notifySound,'notifySound')+\n        settingToggle('v3601Vibrate','진동','자동감지 안내 진동',s.notifyVibrate,'notifyVibrate')\n      )}\n      ${(!s.activityRecognition||!s.notifications)?''',
'js notification controls section')

replace_once(SETTINGS_JS,
'''      ${(s.startMode==='auto'&&!s.backgroundLocation)?`<div class="setting-info v2516-permission-info">백그라운드에서 바로 GPS 기록하려면 위치를 ‘항상 허용’으로 설정해야 합니다. 허용되지 않으면 확인 알림으로 시작합니다.<button id="v2516BgLocation">위치 권한 설정</button></div>`:''}\n      <div class="setting-info">활동이 일정 시간 계속될 때만 시작 후보로 판단합니다. 민감도는 감지 확인 시간을 조절합니다.</div>''',
'''      ${(s.startMode==='auto'&&!s.backgroundLocation)?`<div class="setting-info v2516-permission-info">백그라운드 자동 시작에는 위치를 ‘항상 허용’으로 설정해야 합니다. 허용되지 않으면 ‘시작할까요?’로 바꾸지 않고 권한 안내를 표시합니다.<button id="v2516BgLocation">위치 권한 설정</button></div>`:''}\n      <div class="setting-info">기본/빠르게는 Android 활동 전환 감지 즉시 처리하고, 정확하게만 추가 확인 시간을 둡니다.</div>''',
'js auto start and sensitivity guidance')

replace_once(SETTINGS_JS,
'''    Object.keys(map).forEach(id=>{\n      const btn=screen.querySelector(`[data-setting-toggle="${id}"]`);\n      if(btn)btn.onclick=()=>mutate(x=>{x[map[id]]=!x[map[id]];},true);\n    });\n    screen.querySelectorAll('[data-setting-chip="v2516StartMode"]')''',
'''    Object.keys(map).forEach(id=>{\n      const btn=screen.querySelector(`[data-setting-toggle="${id}"]`);\n      if(btn)btn.onclick=()=>mutate(x=>{x[map[id]]=!x[map[id]];},true);\n    });\n    [['v3601Sound','notifySound'],['v3601Vibrate','notifyVibrate']].forEach(([id,key])=>{\n      const btn=screen.querySelector(`[data-setting-toggle="${id}"]`);\n      if(btn)btn.onclick=()=>mutate(x=>{x[key]=!x[key];},false);\n    });\n    screen.querySelectorAll('[data-setting-chip="v2516StartMode"]')''',
'js notification toggle handlers')

# Version 0.36.01.
version_target = TARGET / 'v03601-version.js'
version_target.write_bytes(VERSION_SRC.read_bytes())
replace_once(INDEX,
'''  <script src="v03600-version.js"></script>''',
'''  <script src="v03601-version.js"></script>''',
'version script')
replace_once(BUILD, "        versionCode 94\n        versionName '0.36.00'", "        versionCode 95\n        versionName '0.36.01'", 'android version')

print('Applied v0.36.01 auto-detect test fixes: true auto start, immediate normal detection, sound/vibration controls.')
