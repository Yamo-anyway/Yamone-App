package com.yamo.snorelab;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.ArrayDeque;
import java.util.Deque;

/** v0.36.03 hybrid detector: Android event + local sensors + balanced GPS. */
public final class ActivityHybridDetectorService extends Service implements SensorEventListener {
    private static final String CHANNEL = "yamone_hybrid_activity_v1";
    private static final int NOTIFY = 6720;
    private static final long EVAL_MS = 1000L;
    private static final long STEP_WINDOW_MS = 20_000L;
    private static final long PLATFORM_STALE_MS = 90_000L;
    private static final long LOCATION_STALE_MS = 20_000L;
    private static final long CANDIDATE_GRACE_MS = 4_000L;
    private static final long INACTIVE_NOTIFY_MS = 30_000L;

    static final String KEY_RUNNING = "hybrid_running";
    static final String KEY_CANDIDATE = "hybrid_candidate";
    static final String KEY_CANDIDATE_SINCE = "hybrid_candidate_since";
    static final String KEY_CONFIDENCE = "hybrid_confidence";
    static final String KEY_CADENCE = "hybrid_cadence";
    static final String KEY_SPEED_KMH = "hybrid_speed_kmh";
    static final String KEY_ACCEL = "hybrid_accel_motion";
    static final String KEY_GYRO = "hybrid_gyro_motion";
    static final String KEY_PLATFORM = "hybrid_platform_hint";
    static final String KEY_LAST_EVAL = "hybrid_last_eval";

    private static final String KEY_PLATFORM_WALK = "hybrid_platform_walk";
    private static final String KEY_PLATFORM_RUN = "hybrid_platform_run";
    private static final String KEY_PLATFORM_BIKE = "hybrid_platform_bike";
    private static final String KEY_PLATFORM_VEHICLE = "hybrid_platform_vehicle";
    private static final String KEY_PLATFORM_WALK_AT = "hybrid_platform_walk_at";
    private static final String KEY_PLATFORM_RUN_AT = "hybrid_platform_run_at";
    private static final String KEY_PLATFORM_BIKE_AT = "hybrid_platform_bike_at";
    private static final String KEY_PLATFORM_VEHICLE_AT = "hybrid_platform_vehicle_at";

    private SharedPreferences prefs;
    private SensorManager sensorManager;
    private Sensor stepSensor;
    private float stepCounterBase = Float.NaN;
    private long observedStepCounter;
    private final Deque<Long> stepTimes = new ArrayDeque<>();
    private double accelMotionEma;
    private double gyroMotionEma;

    private FusedLocationProviderClient fused;
    private LocationCallback locationCallback;
    private Location lastLocation;
    private long lastLocationWallMs;
    private float speedKmh;

    private String candidate = "none";
    private long candidateSince;
    private long candidateLastSupport;
    private String mismatchCandidate = "none";
    private long mismatchSince;
    private String lastConfirmed = "none";
    private long inactiveSince;
    private boolean inactiveSignaled;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable evaluator = new Runnable() {
        @Override public void run() {
            evaluate();
            handler.postDelayed(this, EVAL_MS);
        }
    };

    public static void sync(Context context) {
        Context app = context.getApplicationContext();
        if (!ActivityAutoDetectManager.anyEnabled(app) || !hasForegroundLocation(app)) {
            stop(app);
            return;
        }
        ensureRunning(app);
    }

    static void ensureRunning(Context context) {
        Context app = context.getApplicationContext();
        if (!ActivityAutoDetectManager.anyEnabled(app) || !hasForegroundLocation(app)) return;
        Intent i = new Intent(app, ActivityHybridDetectorService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i);
            else app.startService(i);
        } catch (RuntimeException ignored) { }
    }

    static void stop(Context context) {
        try { context.getApplicationContext().stopService(new Intent(context, ActivityHybridDetectorService.class)); }
        catch (RuntimeException ignored) { }
    }

    static void notePlatformActivity(Context context, int activityType, boolean entering) {
        SharedPreferences p = ActivityAutoDetectManager.prefs(context);
        long now = System.currentTimeMillis();
        SharedPreferences.Editor e = p.edit();
        switch (activityType) {
            case com.google.android.gms.location.DetectedActivity.WALKING:
                e.putBoolean(KEY_PLATFORM_WALK, entering).putLong(KEY_PLATFORM_WALK_AT, now); break;
            case com.google.android.gms.location.DetectedActivity.RUNNING:
                e.putBoolean(KEY_PLATFORM_RUN, entering).putLong(KEY_PLATFORM_RUN_AT, now); break;
            case com.google.android.gms.location.DetectedActivity.ON_BICYCLE:
                e.putBoolean(KEY_PLATFORM_BIKE, entering).putLong(KEY_PLATFORM_BIKE_AT, now); break;
            case com.google.android.gms.location.DetectedActivity.IN_VEHICLE:
                e.putBoolean(KEY_PLATFORM_VEHICLE, entering).putLong(KEY_PLATFORM_VEHICLE_AT, now); break;
            default: return;
        }
        e.apply();
        ensureRunning(context);
    }

    static boolean candidateMatches(Context context, String type) {
        String c = ActivityAutoDetectManager.prefs(context).getString(KEY_CANDIDATE, "none");
        if (ActivityAutoDetectManager.TYPE_BIKE.equals(type)) return "cycling".equals(c);
        if (ActivityAutoDetectManager.TYPE_RUN.equals(type)) return "running".equals(c);
        return "walking".equals(c);
    }

    static boolean motionActive(Context context) {
        SharedPreferences p = ActivityAutoDetectManager.prefs(context);
        long last = p.getLong(KEY_LAST_EVAL, 0L);
        return last > 0L && System.currentTimeMillis() - last < 10_000L
                && !"none".equals(p.getString(KEY_CANDIDATE, "none"));
    }

    private static boolean hasForegroundLocation(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void onCreate() {
        super.onCreate();
        prefs = ActivityAutoDetectManager.prefs(this);
        createChannel();
        startDetectorForeground();
        startSensors();
        startBalancedLocation();
        prefs.edit().putBoolean(KEY_RUNNING, true).apply();
        handler.post(evaluator);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!ActivityAutoDetectManager.anyEnabled(this) || !hasForegroundLocation(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void startDetectorForeground() {
        Intent openIntent = new Intent(this, YamoneMovementActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(this, 6721, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("야모네 자동감지 실행 중")
                .setContentText("걸음 · 센서 · 속도 · Android 활동 신호를 함께 확인합니다")
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            try { startForeground(NOTIFY, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION); }
            catch (RuntimeException e) { startForeground(NOTIFY, n); }
        } else startForeground(NOTIFY, n);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "활동 자동감지 실행", NotificationManager.IMPORTANCE_LOW);
        ch.setSound(null, null);
        ch.enableVibration(false);
        nm.createNotificationChannel(ch);
    }

    private void startSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager == null) return;
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        if (stepSensor == null) stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        try {
            if (accel != null) sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL);
            if (gyro != null) sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_NORMAL);
            if (stepSensor != null) sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } catch (SecurityException ignored) { }
    }

    private void startBalancedLocation() {
        if (!hasForegroundLocation(this)) return;
        fused = LocationServices.getFusedLocationProviderClient(this);
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5_000L)
                .setMinUpdateIntervalMillis(3_000L)
                .setMaxUpdateDelayMillis(10_000L)
                .build();
        locationCallback = new LocationCallback() {
            @Override public void onLocationResult(LocationResult result) {
                if (result != null && result.getLastLocation() != null) acceptLocation(result.getLastLocation());
            }
        };
        try { fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper()); }
        catch (SecurityException ignored) { }
    }

    private void acceptLocation(Location l) {
        long now = System.currentTimeMillis();
        float measured = l.hasSpeed() ? Math.max(0f, l.getSpeed() * 3.6f) : Float.NaN;
        if (Float.isNaN(measured) && lastLocation != null) {
            long dt = Math.max(1L, now - lastLocationWallMs);
            if (dt <= 15_000L) measured = lastLocation.distanceTo(l) / (dt / 1000f) * 3.6f;
        }
        if (!Float.isNaN(measured) && measured <= 180f) {
            speedKmh = speedKmh <= 0f ? measured : speedKmh * 0.65f + measured * 0.35f;
        }
        lastLocation = new Location(l);
        lastLocationWallMs = now;
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event == null || event.sensor == null) return;
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_ACCELEROMETER && event.values.length >= 3) {
            double x = event.values[0], y = event.values[1], z = event.values[2];
            double motion = Math.abs(Math.sqrt(x*x + y*y + z*z) - SensorManager.GRAVITY_EARTH);
            accelMotionEma = accelMotionEma * 0.92 + motion * 0.08;
        } else if (type == Sensor.TYPE_GYROSCOPE && event.values.length >= 3) {
            double x = event.values[0], y = event.values[1], z = event.values[2];
            gyroMotionEma = gyroMotionEma * 0.92 + Math.sqrt(x*x + y*y + z*z) * 0.08;
        } else if (type == Sensor.TYPE_STEP_DETECTOR) {
            addSteps(1);
        } else if (type == Sensor.TYPE_STEP_COUNTER && event.values.length > 0) {
            float current = event.values[0];
            if (Float.isNaN(stepCounterBase)) { stepCounterBase = current; observedStepCounter = 0; }
            else {
                long total = Math.max(0L, Math.round(current - stepCounterBase));
                long delta = Math.min(6L, Math.max(0L, total - observedStepCounter));
                observedStepCounter = total;
                if (delta > 0) addSteps((int) delta;
            }
        }
    }

    private void addSteps(int count) {
        long now = System.currentTimeMillis();
        for (int i=0;i<count;i++) stepTimes.addLast(now - (count-1L-i)*250L);
        pruneSteps(now);
    }

    private void pruneSteps(long now) {
        while (!stepTimes.isEmpty() && now - stepTimes.peekFirst() > STEP_WINDOW_MS) stepTimes.removeFirst();
    }

    private float cadenceSpm(long now) {
        pruneSteps(now);
        if (stepTimes.size() < 3) return 0f;
        long first = stepTimes.peekFirst(), last = stepTimes.peekLast();
        if (last <= first) return 0f;
        return Math.min(240f, (stepTimes.size()-1) * 60_000f / (last-first));
    }

    private boolean platformActive(String key, String atKey, long now) {
        return prefs.getBoolean(key, false) && now - prefs.getLong(atKey, 0L) <= PLATFORM_STALE_MS;
    }

    private void evaluate() {
        if (!ActivityAutoDetectManager.anyEnabled(this)) { stopSelf(); return; }
        long now = System.currentTimeMillis();
        if (lastLocationWallMs > 0 && now - lastLocationWallMs > LOCATION_STALE_MS) speedKmh *= 0.75f;
        if (speedKmh < 0.2f) speedKmh = 0f;
        float cadence = cadenceSpm(now);
        boolean gWalk = platformActive(KEY_PLATFORM_WALK, KEY_PLATFORM_WALK_AT, now);
        boolean gRun = platformActive(KEY_PLATFORM_RUN, KEY_PLATFORM_RUN_AT, now);
        boolean gBike = platformActive(KEY_PLATFORM_BIKE, KEY_PLATFORM_BIKE_AT, now);
        boolean gVehicle = platformActive(KEY_PLATFORM_VEHICLE, KEY_PLATFORM_VEHICLE_AT, now);

        int walk = ActivityAutoDetectManager.isTypeEnabled(this, ActivityAutoDetectManager.TYPE_WALK) ? walkScore(cadence, speedKmh, gWalk, gRun) : -999;
        int run = ActivityAutoDetectManager.isTypeEnabled(this, ActivityAutoDetectManager.TYPE_RUN) ? runScore(cadence, speedKmh, gRun, gWalk) : -999;
        int ride = ActivityAutoDetectManager.isTypeEnabled(this, ActivityAutoDetectManager.TYPE_BIKE) ? rideScore(cadence, speedKmh, gBike, gVehicle) : -999;

        String best = "none"; int score = 0;
        if (walk >= 50 && walk >= run && walk >= ride) { best="walking"; score=walk; }
        if (run >= 50 && run > walk && run >= ride) { best="running"; score=run; }
        if (ride >= 50 && ride >= walk+5 && ride >= run+5) { best="cycling"; score=ride; }
        if (ActivityAutoDetectManager.isTypeEnabled(this, ActivityAutoDetectManager.TYPE_BIKE) && speedKmh >= 12f && cadence < 45f && ride >= 40) {
            best="cycling"; score=Math.max(score, ride);
        }

        updateCandidate(best, now);
        String platform = gVehicle ? "vehicle→cycling" : gBike ? "bicycle" : gRun ? "running" : gWalk ? "walking" : "none";
        prefs.edit().putBoolean(KEY_RUNNING,true).putString(KEY_CANDIDATE,candidate).putLong(KEY_CANDIDATE_SINCE,candidateSince)
                .putInt(KEY_CONFIDENCE,score).putFloat(KEY_CADENCE,cadence).putFloat(KEY_SPEED_KMH,speedKmh)
                .putFloat(KEY_ACCEL,(float)accelMotionEma).putFloat(KEY_GYRO,(float)gyroMotionEma).putString(KEY_PLATFORM,platform)
                .putLong(KEY_LAST_EVAL,now).apply();
    }

    private int walkScore(float cadence,float speed,boolean platformWalk,boolean platformRun){
        int s=platformWalk?35:0;
        if(cadence>=60&&cadence<=145)s+=35; else if(cadence>=40&&cadence<=170)s+=18;
        if(speed>=1&&speed<=7.5)s+=25; else if(speed>=0.4&&speed<=10)s+=10;
        if(accelMotionEma>=0.10&&accelMotionEma<=2.8)s+=10;
        if(gyroMotionEma>=0.03&&gyroMotionEma<=2.5)s+=5;
        if(platformRun)s-=20; if(speed>12)s-=40; return s;
    }
    private int runScore(float cadence,float speed,boolean platformRun,boolean platformWalk){
        int s=platformRun?35:0;
        if(cadence>=145&&cadence<=220)s+=35; else if(cadence>=115&&cadence<=230)s+=18;
        if(speed>=7&&speed<=22)s+=30; else if(speed>=5&&speed<=25)s+=15;
        if(accelMotionEma>=0.35)s+=12; if(gyroMotionEma>=0.10)s+=5;
        if(platformWalk&&cadence<135)s-=15; if(speed>0&&speed<2.5)s-=25; return s;
    }
    private int rideScore(float cadence,float speed,boolean platformBike,boolean platformVehicle){
        int s=platformBike?42:0; if(platformVehicle)s+=48;
        if(speed>=10)s+=35; else if(speed>=5)s+=18;
        if(cadence<=25)s+=20; else if(cadence<=45)s+=10;
        if(accelMotionEma<=1.6)s+=8; if(gyroMotionEma<=1.2)s+=7;
        if(cadence>=80)s-=35; return s;
    }

    private void updateCandidate(String best,long now){
        if(best.equals(candidate)){ mismatchCandidate="none"; mismatchSince=0; candidateLastSupport=now; }
        else if("none".equals(best)){
            if("none".equals(candidate)||now-candidateLastSupport>CANDIDATE_GRACE_MS)setCandidate("none",now);
        } else if("none".equals(candidate))setCandidate(best,now);
        else if(!best.equals(mismatchCandidate)){ mismatchCandidate=best; mismatchSince=now; }
        else if(now-mismatchSince>=3000L)setCandidate(best,now);

        if(!"none".equals(candidate)){
            inactiveSince=0; inactiveSignaled=false; ActivityAutoDetectManager.onHybridMotionResumed(this);
            int confirmSec=ActivityAutoDetectManager.confirmSeconds(this);
            if(candidateSince>0&&now-candidateSince>=confirmSec*1000L&&!candidate.equals(lastConfirmed)){
                lastConfirmed=candidate; ActivityAutoDetectManager.onHybridCandidateConfirmed(this,candidate);
            }
        } else {
            if(inactiveSince<=0)inactiveSince=now;
            if(!inactiveSignaled&&now-inactiveSince>=INACTIVE_NOTIFY_MS){ inactiveSignaled=true; lastConfirmed="none"; ActivityAutoDetectManager.onHybridInactive(this); }
        }
    }

    private void setCandidate(String value,long now){
        candidate=value; candidateSince="none".equals(value)?0L:now; candidateLastSupport="none".equals(value)?0L:now;
        mismatchCandidate="none"; mismatchSince=0; if("none".equals(value))lastConfirmed="none";
    }

    @Override public void onDestroy(){
        handler.removeCallbacks(evaluator);
        if(sensorManager!=null)sensorManager.unregisterListener(this);
        if(fused!=null&&locationCallback!=null)fused.removeLocationUpdates(locationCallback);
        prefs.edit().putBoolean(KEY_RUNNING,false).putString(KEY_CANDIDATE,"none").apply();
        super.onDestroy();
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    @Override public IBinder onBind(Intent intent){return null;}
}
