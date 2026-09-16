package com.yamo.snorelab;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.*;
import android.location.*;
import android.os.*;
import android.provider.Settings;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Explicit developer capture, independent from the user's normal movement recorder. */
public final class DevCaptureService extends Service implements SensorEventListener {
    static final String PREFS="yamone_dev_dataset_v1";
    static final String START="com.yamo.snorelab.DEV_CAPTURE_START", STOP="com.yamo.snorelab.DEV_CAPTURE_STOP";
    private static volatile DevCaptureService instance;
    private volatile boolean collecting;
    private HandlerThread thread;
    private Handler writer;
    private DevDatasetStore store;
    private SensorManager sensors;
    private LocationManager locations;
    private PowerManager.WakeLock wake;
    private LocationListener listener;
    private GnssStatus.Callback gnss;
    private final Map<Integer,Long> lastSensor=new HashMap<>();
    private final AtomicInteger queued=new AtomicInteger();
    private final AtomicLong dropped=new AtomicLong();
    private long lastHealth, lastWake, lastGnss;
    private String mode="", recordId="";
    private static final int NOTIFY=6810;

    static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,MODE_PRIVATE);}
    static File root(Context c){File f=new File(c.getNoBackupFilesDir(),"developer-datasets");if(!f.exists())f.mkdirs();return f;}
    static boolean collecting(Context c){DevCaptureService s=instance;return s!=null&&s.collecting&&SystemSettingsBridge.isDeveloperMode(c);}
    static boolean automatic(Context c){return collecting(c)&&"automatic".equals(instance.mode);}
    static boolean allowed(Context c){return SystemSettingsBridge.isDeveloperMode(c);}
    static void emit(String stream,JSONObject data){
        DevCaptureService s=instance;
        if(s==null||!s.collecting||!allowed(s))return;
        long wall=System.currentTimeMillis(),mono=SystemClock.elapsedRealtimeNanos();
        if(s.queued.incrementAndGet()>512){s.queued.decrementAndGet();s.dropped.incrementAndGet();return;}
        s.writer.post(()->{try{if(s.collecting)s.append(stream,data,wall,mono);}finally{s.queued.decrementAndGet();}});
    }
    static JSONObject obj(Object... pairs){JSONObject j=new JSONObject();for(int i=0;i+1<pairs.length;i+=2)try{j.put(String.valueOf(pairs[i]),pairs[i+1]);}catch(Exception ignored){}return j;}
    static void recorderStarted(Context c,String id,String type,boolean detected,long startWall){
        if(!allowed(c))return;
        JSONObject e=obj("recordId",id,"requestedType",type,"startedByAutoDetect",detected,"startWallMs",startWall,
                "labelSource",detected?"algorithm_proposal":"manual_activity_selection","boundaryExcludeMs",10000);
        if(collecting(c)){emit("record_start",e);return;}
        if(!prefs(c).getBoolean("manualCapture",true))return;
        try {Intent i=new Intent(c,DevCaptureService.class).setAction(START).putExtra("mode","local")
                .putExtra("recordId",id).putExtra("recordEvent",e.toString());c.startForegroundService(i);}
        catch(RuntimeException ex){prefs(c).edit().putString("error","수동 진단 수집을 시작하지 못했습니다: "+ex.getClass().getSimpleName()).apply();}
    }
    static void recorderEnded(Context c,String id,JSONObject summary){
        if(!allowed(c)||instance==null)return;
        emit("record_end",obj("recordId",id,"summary",summary));
        DevCaptureService s=instance;
        if(!"automatic".equals(s.mode)&&id.equals(s.recordId))s.writer.post(()->s.finish("record_end"));
    }
    static void disable(Context c){
        DevNetworkGuard.cancel();
        prefs(c).edit().putBoolean("autoRequested",false).putLong("approvalEpoch",prefs(c).getLong("approvalEpoch",0)+1).apply();
        try{c.stopService(new Intent(c,LocationSharingService.class));}catch(Exception ignored){}
        DevCaptureService s=instance;if(s!=null&&s.writer!=null)s.writer.post(()->s.finish("general_mode"));
    }
    @Override public void onCreate(){
        super.onCreate();DevNetworkGuard.init(this);
        thread=new HandlerThread("yamone-dev-capture");thread.start();writer=new Handler(thread.getLooper());instance=this;
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&STOP.equals(intent.getAction())){writer.post(()->finish("switch_off"));return START_NOT_STICKY;}
        if(!allowed(this)||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){stopSelf();return START_NOT_STICKY;}
        if(collecting)return START_STICKY;
        try{foreground();}catch(RuntimeException e){prefs(this).edit().putString("error","권한 또는 백그라운드 실행 제한: "+e.getClass().getSimpleName()).apply();stopSelf();return START_NOT_STICKY;}
        writer.post(()->begin(intent));return START_STICKY;
    }
    private void foreground(){
        NotificationManager nm=getSystemService(NotificationManager.class);
        NotificationChannel ch=new NotificationChannel("yamone_dev_capture","개발자 원본 데이터 수집",NotificationManager.IMPORTANCE_LOW);
        ch.setSound(null,null);nm.createNotificationChannel(ch);
        PendingIntent open=PendingIntent.getActivity(this,NOTIFY,new Intent(this,YamoneMovementActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,NOTIFY+1,new Intent(this,DevCaptureService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,"yamone_dev_capture").setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("야모네 개발자 데이터 기록 중").setContentText("GPS·센서 원본 수집 · 자동 업로드는 정시마다 전송")
                .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"기록 종료",stop).build()).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFY,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);else startForeground(NOTIFY,n);
    }
    private void begin(Intent intent){
        try{
            if(!allowed(this)){stopSelf();return;}
            long wall=System.currentTimeMillis(),mono=SystemClock.elapsedRealtimeNanos();
            File dir=null;JSONObject initial=null;
            if(intent==null){
                String id=prefs(this).getString("activeId","");
                if(!id.matches("[a-f0-9-]{36}")){stopSelf();return;}
                dir=new File(root(this),id);initial=DevDatasetStore.readJson(new File(dir,"manifest.json"));
                if(!"recording".equals(initial.optString("state"))){stopSelf();return;}
            }else{
                if(store!=null)return;
                String priorId=prefs(this).getString("activeId","");
                if(priorId.matches("[a-f0-9-]{36}")){
                    File priorDir=new File(root(this),priorId);
                    JSONObject prior=DevDatasetStore.readJson(new File(priorDir,"manifest.json"));
                    if("recording".equals(prior.optString("state"))){
                        DevDatasetStore recovered=new DevDatasetStore(priorDir,prior,wall,mono);
                        recovered.finish("interrupted_before_new_start",prior.optLong("lastWallMs",wall),prior.optLong("lastMonoNs",mono));
                    }
                }
                mode=intent.getStringExtra("mode");if(!"automatic".equals(mode))mode="local";
                recordId=intent.getStringExtra("recordId");if(recordId==null)recordId="";
                String id=UUID.randomUUID().toString();dir=new File(root(this),id);
                initial=obj("id",id,"state","recording","mode",mode,"recordId",recordId,"startWallMs",wall,"startMonoNs",mono,
                        "clockEpoch",UUID.randomUUID().toString(),"bootCount",bootCount(),"releasedThrough",-1,
                        "approvalEpoch",prefs(this).getLong("approvalEpoch",0),"schemaVersion",1,
                        "boundaryExcludeMs",10000,"algorithmVersion","hybrid-0.36.06","sourceCommit",BuildConfig.DATASET_SOURCE_REVISION,
                        "appVersion",getPackageManager().getPackageInfo(getPackageName(),0).versionName,
                        "device",obj("manufacturer",Build.MANUFACTURER,"model",Build.MODEL,"sdk",Build.VERSION.SDK_INT,"osRelease",Build.VERSION.RELEASE),
                        "timezone",TimeZone.getDefault().getID(),"requestedRates",obj("accelerometerHz",25,"gyroscopeHz",25,"gravityHz",5,"rotationHz",5,"pressureHz",1,"gpsIntervalMs",1000));
            }
            mode=initial.optString("mode","local");recordId=initial.optString("recordId","");
            store=new DevDatasetStore(dir,initial,wall,mono);
            if(intent==null&&initial.optInt("bootCount",-1)!=bootCount()){
                store.finish("device_reboot",initial.optLong("lastWallMs",wall),initial.optLong("lastMonoNs",mono));
                store=null;prefs(this).edit().remove("activeId").putBoolean("autoRequested",false).apply();stopSelf();return;
            }
            collecting=true;prefs(this).edit().putString("activeId",dir.getName()).putString("error","").putBoolean("autoRequested","automatic".equals(mode)).apply();
            append(intent==null?"process_restart":"session_start",obj("gapUnobserved",intent==null,"settings",new JSONObject(ActivityAutoDetectManager.prefs(this).getAll())),wall,mono);
            if(intent!=null&&intent.hasExtra("recordEvent"))append("record_start",new JSONObject(intent.getStringExtra("recordEvent")),wall,mono);
            registerInputs();
            new Handler(Looper.getMainLooper()).post(()->ActivityAutoDetectManager.syncRegistration(this));
            writer.post(ticker);
        }catch(Exception e){fail(e);}
    }
    private static long folderBytes(File f){if(f.isFile())return f.length();long n=0;File[] children=f.listFiles();if(children!=null)for(File c:children)n+=folderBytes(c);return n;}
    private int bootCount(){try{return Settings.Global.getInt(getContentResolver(),Settings.Global.BOOT_COUNT);}catch(Exception e){return -1;}}
    private void registerInputs() throws Exception {
        sensors=getSystemService(SensorManager.class);
        JSONArray inventory=new JSONArray();
        int[] types={Sensor.TYPE_ACCELEROMETER,Sensor.TYPE_GYROSCOPE,Sensor.TYPE_GRAVITY,Sensor.TYPE_ROTATION_VECTOR,Sensor.TYPE_PRESSURE,Sensor.TYPE_STEP_DETECTOR,Sensor.TYPE_STEP_COUNTER};
        for(int type:types){Sensor s=sensors==null?null:sensors.getDefaultSensor(type);boolean registered=false;
            if(s!=null)try{registered=sensors.registerListener(this,s,type==1||type==4?40000:200000,0,writer);}catch(RuntimeException ignored){}
            inventory.put(obj("type",type,"present",s!=null,"registered",registered,"name",s==null?JSONObject.NULL:s.getName(),"vendor",s==null?JSONObject.NULL:s.getVendor(),"resolution",s==null?JSONObject.NULL:s.getResolution(),"maxRange",s==null?JSONObject.NULL:s.getMaximumRange()));}
        store.update("sensors",inventory);append("sensor_inventory",obj("sensors",inventory),System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());
        locations=getSystemService(LocationManager.class);
        listener=new LocationListener(){
            @Override public void onLocationChanged(Location l){append("gps_raw",location(l),System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());}
            @Override public void onStatusChanged(String p,int status,Bundle extras){}
            @Override public void onProviderEnabled(String p){append("gps_provider",obj("provider",p,"enabled",true),System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());}
            @Override public void onProviderDisabled(String p){append("gps_provider",obj("provider",p,"enabled",false),System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());}
        };
        if(locations!=null){for(String p:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER})try{
            locations.requestLocationUpdates(p,LocationManager.GPS_PROVIDER.equals(p)?1000L:5000L,0f,listener,thread.getLooper());
        }catch(RuntimeException e){append("capability",obj("provider",p,"error",e.getClass().getSimpleName()),System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());}
        gnss=new GnssStatus.Callback(){@Override public void onSatelliteStatusChanged(GnssStatus s){long t=SystemClock.elapsedRealtime();if(t-lastGnss<1000)return;lastGnss=t;JSONArray list=new JSONArray();
            for(int i=0;i<s.getSatelliteCount();i++)list.put(obj("constellation",s.getConstellationType(i),"svid",s.getSvid(i),"cn0DbHz",s.getCn0DbHz(i),"used",s.usedInFix(i),"elevation",s.getElevationDegrees(i)));
            append("gnss_status",obj("satellites",list),System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());}};
        try{locations.registerGnssStatusCallback(gnss,writer);}catch(RuntimeException ignored){}}
        if(checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)==PackageManager.PERMISSION_GRANTED||Build.VERSION.SDK_INT<29)try{
            ActivityRecognition.getClient(this).requestActivityUpdates(5000,recognitionIntent(this))
                .addOnFailureListener(e->emit("capability",obj("activityRecognitionError",e.getClass().getSimpleName())));
        }catch(RuntimeException e){emit("capability",obj("activityRecognitionError",e.getClass().getSimpleName()));}
        PowerManager pm=getSystemService(PowerManager.class);wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":dev-capture");wake.setReferenceCounted(false);wake.acquire(600000);lastWake=SystemClock.elapsedRealtime();
    }
    static JSONObject location(Location l){if(l==null)return new JSONObject();return obj("provider",l.getProvider(),"fixWallMs",l.getTime(),"fixMonoNs",l.getElapsedRealtimeNanos(),
        "lat",l.getLatitude(),"lon",l.getLongitude(),"accuracyM",l.hasAccuracy()?l.getAccuracy():JSONObject.NULL,
        "speedMps",l.hasSpeed()?l.getSpeed():JSONObject.NULL,"speedAccuracyMps",l.hasSpeedAccuracy()?l.getSpeedAccuracyMetersPerSecond():JSONObject.NULL,
        "altitudeM",l.hasAltitude()?l.getAltitude():JSONObject.NULL,"verticalAccuracyM",l.hasVerticalAccuracy()?l.getVerticalAccuracyMeters():JSONObject.NULL,
        "bearingDeg",l.hasBearing()?l.getBearing():JSONObject.NULL,"bearingAccuracyDeg",l.hasBearingAccuracy()?l.getBearingAccuracyDegrees():JSONObject.NULL,"mock",l.isFromMockProvider());}
    @Override public void onSensorChanged(SensorEvent e){
        if(!collecting||!allowed(this))return;
        int type=e.sensor.getType();long interval=(type==1||type==4)?40_000_000L:type==6?1_000_000_000L:200_000_000L;
        long before=lastSensor.getOrDefault(type,0L);
        if(type!=Sensor.TYPE_STEP_COUNTER&&type!=Sensor.TYPE_STEP_DETECTOR&&e.timestamp-before<interval*8/10)return;
        lastSensor.put(type,e.timestamp);JSONArray values=new JSONArray();for(float f:e.values)values.put(Float.isFinite(f)?f:JSONObject.NULL);
        append("sensor",obj("type",type,"accuracy",e.accuracy,"sampleMonoNs",e.timestamp,"values",values),System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());
    }
    @Override public void onAccuracyChanged(Sensor s,int a){append("sensor_accuracy",obj("type",s.getType(),"accuracy",a),System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());}
    private final Runnable ticker=new Runnable(){@Override public void run(){
        if(!collecting)return;if(!allowed(DevCaptureService.this)){finish("general_mode");return;}
        long wall=System.currentTimeMillis(),mono=SystemClock.elapsedRealtimeNanos();
        try{
            if(root(DevCaptureService.this).getUsableSpace()<256L*1024*1024){finish("low_storage");return;}
            if(wall-lastHealth>=60000){
                if(folderBytes(root(DevCaptureService.this))>1024L*1024*1024){finish("local_dataset_limit");return;}
                lastHealth=wall;PowerManager pm=getSystemService(PowerManager.class);BatteryManager bm=getSystemService(BatteryManager.class);
                append("health",obj("batteryPercent",bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),"powerSave",pm.isPowerSaveMode(),"interactive",pm.isInteractive(),"freeBytes",root(DevCaptureService.this).getUsableSpace(),"droppedEvents",dropped.get(),"timezone",TimeZone.getDefault().getID(),"offsetMs",TimeZone.getDefault().getOffset(wall)),wall,mono);
            }
            JSONObject runtime=new JSONObject(getSharedPreferences(WalkingRecorderService.PREFS,MODE_PRIVATE).getAll());runtime.remove(WalkingRecorderService.KEY_SESSION_DIR);
            append("recorder_state",runtime,wall,mono);
            if (getSharedPreferences(SkiRecorderService.PREFS,MODE_PRIVATE).getBoolean(SkiRecorderService.KEY_RECORDING,false)) {
                JSONObject snow=new JSONObject(getSharedPreferences(SkiRecorderService.PREFS,MODE_PRIVATE).getAll());snow.remove(SkiRecorderService.KEY_SESSION_DIR);
                append("snow_recorder_state",snow,wall,mono);
            }
            if (getSharedPreferences(HikingRecorderService.PREFS,MODE_PRIVATE).getBoolean(HikingRecorderService.KEY_RECORDING,false)) {
                JSONObject hike=new JSONObject(getSharedPreferences(HikingRecorderService.PREFS,MODE_PRIVATE).getAll());hike.remove(HikingRecorderService.KEY_SESSION_DIR);
                append("hiking_recorder_state",hike,wall,mono);
            }
            boolean due=store.tick(wall,mono);
            if(due||wall%60000<1200)DevDatasetUploader.kick(DevCaptureService.this);
            if(SystemClock.elapsedRealtime()-lastWake>540000&&wake!=null){wake.acquire(600000);lastWake=SystemClock.elapsedRealtime();}
            writer.postDelayed(this,1000);
        }catch(Exception e){fail(e);}
    }};
    private void append(String stream,JSONObject value,long wall,long mono){if(store==null)return;try{store.append(stream,value,wall,mono);}catch(Exception e){fail(e);}}
    private void fail(Exception e){prefs(this).edit().putString("error","진단 기록 오류: "+e.getClass().getSimpleName()+" / "+String.valueOf(e.getMessage())).apply();finish("storage_error");}
    private void finish(String reason){
        if(!collecting&&store==null){stopSelf();return;}collecting=false;
        writer.removeCallbacks(ticker);
        try{if(sensors!=null)sensors.unregisterListener(this);if(locations!=null){if(listener!=null)locations.removeUpdates(listener);if(gnss!=null)locations.unregisterGnssStatusCallback(gnss);}}catch(Exception ignored){}
        try{ActivityRecognition.getClient(this).removeActivityUpdates(recognitionIntent(this));}catch(Exception ignored){}
        try{if(wake!=null&&wake.isHeld())wake.release();}catch(Exception ignored){}
        try{if(store!=null){if("general_mode".equals(reason))store.update("approvalEpoch",-1);store.finish(reason,System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());}}catch(Exception e){prefs(this).edit().putString("error","중단된 원본은 기기에 남아 있습니다: "+e.getClass().getSimpleName()).apply();try{if(store!=null)store.close();}catch(Exception ignored){}}
        store=null;prefs(this).edit().remove("activeId").putBoolean("autoRequested",false).apply();
        new Handler(Looper.getMainLooper()).post(()->{ActivityHybridDetectorService.stop(this);new Handler(Looper.getMainLooper()).postDelayed(()->ActivityAutoDetectManager.syncRegistration(this),300);});
        if(allowed(this))DevDatasetUploader.kick(this);
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    static PendingIntent recognitionIntent(Context c){int flags=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0);
        return PendingIntent.getBroadcast(c,6815,new Intent(c,RecognitionReceiver.class).setAction("com.yamo.snorelab.DEV_RECOGNITION"),flags);}
    public static final class RecognitionReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context c,Intent i){if(!collecting(c)||!ActivityRecognitionResult.hasResult(i))return;
            ActivityRecognitionResult r=ActivityRecognitionResult.extractResult(i);if(r==null)return;JSONArray a=new JSONArray();
            for(DetectedActivity d:r.getProbableActivities())a.put(obj("type",d.getType(),"confidence",d.getConfidence()));
            emit("platform_recognition",obj("resultWallMs",r.getTime(),"resultMonoMs",r.getElapsedRealtimeMillis(),"activities",a));}
    }
    @Override public void onDestroy(){
        if(writer!=null){writer.post(()->{if(collecting||store!=null)finish("service_destroyed");thread.quitSafely();});}
        if(instance==this)instance=null;super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
