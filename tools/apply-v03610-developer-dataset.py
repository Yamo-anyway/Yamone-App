#!/usr/bin/env python3
from pathlib import Path
import re, shutil
R=Path(__file__).resolve().parent.parent
J=R/'app/src/main/java/com/yamo/snorelab'
A=R/'app/src/main/assets/yamone-v23'

def replace(p,old,new,label):
 s=p.read_text()
 if new in s:return
 if old not in s:raise SystemExit('v36.10 missing '+label+' '+str(p))
 p.write_text(s.replace(old,new,1))

for f in (R/'dev-dataset/native').glob('*.java'):shutil.copyfile(f,J/f.name)
for f in (R/'dev-dataset/web').iterdir():shutil.copyfile(f,A/f.name)

def rep(name,a,b):replace(J/name,a,b,name)
rep('YamoneApplication.java','        registerActivityLifecycleCallbacks(this);','        DevNetworkGuard.init(this);\n        registerActivityLifecycleCallbacks(this);')
rep('YamoneApplication.java','        if (locationSharingRecoveryAttempted) return;','        if (!SystemSettingsBridge.isDeveloperMode(activity)) return;\n        if (locationSharingRecoveryAttempted) return;')
rep('SystemSettingsBridge.java','                .edit().putBoolean(KEY_DEVELOPER_MODE, enabled).apply();\n        return enabled;',
    '                .edit().putBoolean(KEY_DEVELOPER_MODE, enabled).apply();\n        if (!enabled) DevCaptureService.disable(activity);\n        return enabled;')
rep('YamoneMovementActivity.java','        webView.addJavascriptInterface(new SystemSettingsBridge(this), "YamoneSystemSettings");',
    '        webView.addJavascriptInterface(new SystemSettingsBridge(this), "YamoneSystemSettings");\n        webView.addJavascriptInterface(new DevDatasetBridge(this), "YamoneDataset");')
rep('YamoneMovementActivity.java','            webView.removeJavascriptInterface("YamoneSystemSettings");',
    '            webView.removeJavascriptInterface("YamoneDataset");\n            webView.removeJavascriptInterface("YamoneSystemSettings");')
rep('LocationSharingService.java','    @Override public int onStartCommand(Intent intent, int flags, int startId) {',
    '    @Override public int onStartCommand(Intent intent, int flags, int startId) {\n        if (!SystemSettingsBridge.isDeveloperMode(this)) { stopSelf(); return START_NOT_STICKY; }')
# All own-server transport paths (including authentication and old queued uploads) check the gate.
for name in ['SupabaseAnonymousRpcClient.java','SupabaseActivityUploader.java']:
 p=J/name;s=p.read_text().replace('(HttpURLConnection) url.openConnection()','DevNetworkGuard.open(url)')
 s=s.replace('out.write(bytes);','DevNetworkGuard.check(); out.write(bytes);')
 s=s.replace('while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);','while ((n = in.read(buffer)) >= 0) { DevNetworkGuard.check(); out.write(buffer, 0, n); }')
 p.write_text(s)
rep('SupabaseAnonymousRpcClient.java','    private SupabaseAnonymousRpcClient() {}',
'''    private SupabaseAnonymousRpcClient() {}

    static JSONObject developerAuth(Context context) throws Exception {
        DevNetworkGuard.check();
        AuthSession s=ensureAnonymousSession(context.getApplicationContext());
        return new JSONObject().put("accessToken",s.accessToken).put("userId",s.userId);
    }''')
# Observe the ACTUAL existing hybrid detector even during manual/continuous capture.
# Shadow mode changes eligibility for diagnosis, never emits automatic start/stop side effects.
p=J/'ActivityHybridDetectorService.java';s=p.read_text()
s=s.replace('!ActivityAutoDetectManager.anyEnabled(app)', '(!ActivityAutoDetectManager.anyEnabled(app) && !DevCaptureService.collecting(app))')
s=s.replace('!ActivityAutoDetectManager.anyEnabled(this)', '(!ActivityAutoDetectManager.anyEnabled(this) && !DevCaptureService.collecting(this))')
s=s.replace('if (isMovementRecording(app)) {','if (isMovementRecording(app) && !DevCaptureService.collecting(app)) {')
s=s.replace('|| isMovementRecording(app)) return;', '|| (isMovementRecording(app) && !DevCaptureService.collecting(app))) return;')
s=s.replace('if (isMovementRecording(this)) {','if (isMovementRecording(this) && !DevCaptureService.collecting(this)) {')
s=s.replace('if (isMovementRecording(context)) return;', 'if (isMovementRecording(context) && !DevCaptureService.collecting(context)) return;')
needle='    static void suspendForRecording(Context context) {\n'
s=s.replace(needle,needle+'        if (DevCaptureService.collecting(context)) { ensureRunning(context); return; }\n')
# Compute all-class scores for diagnostic capture, while recording which settings were overridden.
for typ in ['WALK','RUN','BIKE']:
 s=s.replace(f'ActivityAutoDetectManager.isTypeEnabled(this, ActivityAutoDetectManager.TYPE_{typ})',f'(DevCaptureService.collecting(this) || ActivityAutoDetectManager.isTypeEnabled(this, ActivityAutoDetectManager.TYPE_{typ}))')
needle='        updateCandidate(best, validMovement, now, evalDeltaMs);'
s=s.replace(needle,needle+'''
        DevCaptureService.emit("algorithm_prediction",DevCaptureService.obj(
                "engine","hybrid-0.36.06","diagnosticAllClasses",DevCaptureService.collecting(this),
                "best",best,"candidate",candidate,"confirmed",lastConfirmed,"validMovement",validMovement,
                "candidateActiveMs",candidateActiveMs,"candidateSince",candidateSince,"lastSupport",candidateLastSupport,
                "mismatch",mismatchCandidate,"mismatchSince",mismatchSince,"inactiveSince",inactiveSince,
                "walkScore",walk,"runScore",run,"cyclingScore",ride,"scoreNotProbability",score,
                "cadenceSpm",cadence,"speedKmh",speedKmh,"accelEma",accelMotionEma,"gyroEma",gyroMotionEma,
                "lastHardwareStepMs",lastHardwareStepMs,"lastPseudoStepMs",lastPseudoStepMs,
                "stepCounterBase",Float.isFinite(stepCounterBase)?stepCounterBase:org.json.JSONObject.NULL,"observedStepCounter",observedStepCounter,"accelStepAbove",accelStepAbove,
                "stepCountWindow",stepTimes.size(),"stepTimes",new org.json.JSONArray(stepTimes),
                "lastLocationWallMs",lastLocationWallMs,"platformWalk",gWalk,"platformRun",gRun,"platformBike",gBike,"platformVehicle",gVehicle,
                "confirmSeconds",ActivityAutoDetectManager.confirmSeconds(this)));
''')
# Capture the engine's own inputs at its native rates as well as independent diagnostic streams.
s=s.replace('    private void acceptLocation(Location l) {','    private void acceptLocation(Location l) {\n        DevCaptureService.emit("algorithm_location_input",DevCaptureService.location(l));')
s=s.replace('        int type = event.sensor.getType();','''        int type = event.sensor.getType();
        if(DevCaptureService.collecting(this)){
        org.json.JSONArray nativeValues=new org.json.JSONArray();
        for(float value:event.values)nativeValues.put(Float.isFinite(value)?value:org.json.JSONObject.NULL);
        DevCaptureService.emit("algorithm_sensor_input",DevCaptureService.obj("type",type,"sampleMonoNs",event.timestamp,"accuracy",event.accuracy,"values",nativeValues));
        }''')
p.write_text(s)
# Preserve the user's normal settings; add all transition registrations only for capture.
p=J/'ActivityAutoDetectManager.java';s=p.read_text()
s=s.replace('if (!anyEnabled(app)) {','if (!anyEnabled(app) && !DevCaptureService.collecting(app)) {')
for key in ['WALK','RUN','BIKE']:
 s=s.replace(f'if (p.getBoolean(KEY_{key}_ENABLED, false))',f'if (p.getBoolean(KEY_{key}_ENABLED, false) || DevCaptureService.collecting(app))')
for signature in ['static void onHybridCandidateConfirmed(Context context, String rawType) {','static void onHybridInactive(Context context) {','static void onHybridMotionResumed(Context context) {']:
 s=s.replace(signature,signature+'\n        if (DevCaptureService.collecting(context)) return;')
s=s.replace('    private static boolean startRecorder(Context context, String detectedType) {','    private static boolean startRecorder(Context context, String detectedType) {\n        if (DevCaptureService.automatic(context)) return false;')
s=s.replace('            boolean entering = event.getTransitionType() == ActivityTransition.ACTIVITY_TRANSITION_ENTER;','''            boolean entering = event.getTransitionType() == ActivityTransition.ACTIVITY_TRANSITION_ENTER;
            DevCaptureService.emit("platform_transition",DevCaptureService.obj("type",event.getActivityType(),"enter",entering,"eventMonoNs",event.getElapsedRealTimeNanos()));''')
p.write_text(s)
# Existing movement/snow/hiking record controls still own their own local sessions.
# The capture session can span multiple such records, with independent labels.
p=J/'WalkingRecorderService.java';s=p.read_text()
s=s.replace('        writeMeta("recording", 0L);','''        writeMeta("recording", 0L);
        DevCaptureService.recorderStarted(this,sessionDir.getName(),activityType,startedByAutoDetect,startMs);''',1)
s=s.replace('        writeMeta("complete", end);','''        writeMeta("complete", end);
        DevCaptureService.recorderEnded(this,sessionDir.getName(),WalkingStore.readMeta(sessionDir));''',1)
s=s.replace('        File doomed = sessionDir;','''        File doomed = sessionDir;
        if (doomed!=null)DevCaptureService.recorderEnded(this,doomed.getName(),DevCaptureService.obj("cancelled",true));''',1)
s=s.replace('    private void pauseRecording() {','    private void pauseRecording() {\n        DevCaptureService.emit("record_pause",DevCaptureService.obj("recordId",sessionDir==null?"":sessionDir.getName()));')
s=s.replace('    private void resumeRecording() {','    private void resumeRecording() {\n        DevCaptureService.emit("record_resume",DevCaptureService.obj("recordId",sessionDir==null?"":sessionDir.getName()));')
start=s.index('    private void onLocationChanged(Location loc) {')
end=s.index('    private boolean bridgeGpsShadow(',start)
method=s[start:end]
# Every rejection reason is assigned in the same branch that rejects the point.
reason_names=['accuracy','non_increasing_time','implausible_speed','reported_derived_mismatch','implausible_acceleration','filtered_speed_limit']
assert method.count('rejectedGpsPoints++;')==len(reason_names)
# Replace by ordered callback so previously replaced occurrences aren't matched again.
it=iter(reason_names)
method=re.sub(r'rejectedGpsPoints\+\+;',lambda _: 'diagnosticDecision="'+next(it)+'"; rejectedGpsPoints++;',method)
method=method.replace('stationaryGpsDiscards++;','diagnosticDecision="stationary"; stationaryGpsDiscards++;')
method=method.replace('if (d < noiseFloorM) {','if (d < noiseFloorM) {\n            diagnosticDecision="noise_floor";')
method=method.replace('if (derivedMps < minMovingSpeedMps) {','if (derivedMps < minMovingSpeedMps) {\n            diagnosticDecision="below_moving_speed";')
method=method.replace('boolean bridged = bridgeGpsShadow(loc, now, dtMs);','boolean bridged = bridgeGpsShadow(loc, now, dtMs);\n            diagnosticDecision=bridged?"gap_bridged_estimate":"gap_not_bridged";')
method=method.replace('        acceptAnchor(loc, now, filteredMps, true);','        diagnosticDecision="accepted_moving";\n        acceptAnchor(loc, now, filteredMps, true);')
method=method.replace('private void onLocationChanged(Location loc)','private void onLocationChangedTracked(Location loc)')
wrapper='''    private String diagnosticDecision="anchor";
    private void onLocationChanged(Location loc) {
        diagnosticDecision="anchor_or_ignored";
        double previousDistance=distanceM;
        try { onLocationChangedTracked(loc); }
        finally {
            if(loc!=null&&DevCaptureService.collecting(this))DevCaptureService.emit("recorder_fix",DevCaptureService.obj(
                "recordId",sessionDir==null?"":sessionDir.getName(),"raw",DevCaptureService.location(loc),
                "decision",diagnosticDecision,"distanceDeltaM",distanceM-previousDistance,
                "distanceM",distanceM,"movingMs",movingMs,"speedKmh",currentSpeedKmh,"maxSpeedKmh",maxSpeedKmh,
                "activityType",activityType,"autoMode",autoMotionMode,"gpsGapResets",gpsGapResets,
                "gpsShadowDistanceM",gpsShadowDistanceM,"gpsShadowDurationMs",gpsShadowDurationMs,
                "rejectedPoints",rejectedGpsPoints,"stationaryDiscards",stationaryGpsDiscards,
                "hardMaxMps",hardMaxSpeedMps(),"minimumMovingMps",minMovingSpeedMps(),"maximumMovingMps",maxMovingSpeedMps(),
                "maximumAccelerationMps2",maxAccelerationMps2()));
        }
    }

'''
s=s[:start]+wrapper+method+s[end:];p.write_text(s)
# Additional existing activity types share the same diagnostic session timeline.
rep('HikingRecorderService.java', '        writeMeta("recording", 0L);', '        writeMeta("recording", 0L);\n        DevCaptureService.recorderStarted(this,"hike:"+sessionDir.getName(),"hiking",false,startMs);')
rep('HikingRecorderService.java', '        writeMeta("complete", end);', '        writeMeta("complete", end);\n        DevCaptureService.recorderEnded(this,"hike:"+sessionDir.getName(),HikingStore.readMeta(sessionDir));')
rep('SkiRecorderService.java','        resetDetector();\n        if (!paused) startLocation();','        DevCaptureService.recorderStarted(this,"snow:"+sessionDir.getName(),sport,true,startMs);\n        resetDetector();\n        if (!paused) startLocation();')
rep('SkiRecorderService.java', '            SnowDayMerger.mergePriorSameDayIntoCurrent(this, sessionDir);', '            DevCaptureService.recorderEnded(this,"snow:"+sessionDir.getName(),SkiLiftStore.readSessionMeta(sessionDir));\n            SnowDayMerger.mergePriorSameDayIntoCurrent(this, sessionDir);')
# Manifest components; no exported capture control or recognition receiver.
p=R/'app/src/main/AndroidManifest.xml';s=p.read_text()
s=s.replace('</application>','''    <service android:name=".DevCaptureService" android:foregroundServiceType="location" android:exported="false" />
        <service android:name=".DevDatasetUploader$RetryJob" android:permission="android.permission.BIND_JOB_SERVICE" android:exported="true" />
        <receiver android:name=".DevCaptureService$RecognitionReceiver" android:exported="false" />
    </application>''')
p.write_text(s)
# Settings integration only: all other screens retain their existing design.
p=A/'app.js';s=p.read_text()
s=s.replace("${sr('settings-app-info','앱 정보','Version 0.00.23')}","${sr('settings-app-info','앱 정보','Version 0.00.23')}\n      ${window.yamoneDatasetDeveloper&&window.yamoneDatasetDeveloper()?sr('settings-data-storage','업로드','개발자 원본 수집 · 정시 분할 전송'):''}")
s=s.replace("function renderSettingDetail(){","function renderSettingDetail(){\n  if(settingPage==='업로드'&&window.yamoneRenderDataset)return window.yamoneRenderDataset();")
p.write_text(s)
p=A/'index.html';s=p.read_text();s=re.sub(r'\s*<script src="v03609-version.js"></script>','',s)
s=s.replace('</head>','<link rel="stylesheet" href="v03610-dataset.css">\n</head>')
s=s.replace('</body>','<script src="v03610-dataset.js"></script>\n</body>');p.write_text(s)
p=R/'app/build.gradle';s=p.read_text().replace("versionCode 103","versionCode 104").replace("versionName '0.36.09'","versionName '0.36.10'")
s=s.replace('    namespace', '    buildFeatures { buildConfig true }\n    namespace',1)
s=s.replace("        applicationId 'com.yamo.snorelab'","        applicationId 'com.yamo.snorelab'\n        buildConfigField 'String', 'DATASET_SOURCE_REVISION', '\"' + (System.getenv('YAMONE_DATASET_REVISION') ?: System.getenv('GITHUB_SHA') ?: 'local-uncommitted') + '\"'",1)
s=s.replace("dependencies {","dependencies {\n    testImplementation 'junit:junit:4.13.2'\n    testImplementation 'org.json:json:20240303'",1);p.write_text(s)
t=R/'app/src/test/java/com/yamo/snorelab';t.mkdir(parents=True,exist_ok=True)
for f in (R/'dev-dataset/tests').glob('*.java'):shutil.copyfile(f,t/f.name)
print('Applied v0.36.10 private developer datasets, trace instrumentation, and privacy gates.')
