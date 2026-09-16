#!/usr/bin/env python3
from pathlib import Path
import re

R = Path(__file__).resolve().parent.parent
J = R / 'app/src/main/java/com/yamo/snorelab'
A = R / 'app/src/main/assets/yamone-v23'
BUILD = R / 'app/build.gradle'

# Ordinary user activity/sleep records are local-only. Remove legacy Supabase
# upload implementations. Developer activity diagnostics continue through the
# Mac mini developer-dataset transport; sleep analysis gets a separate Mac mini
# uploader below. Audio is never sent.
for name in ('SupabaseActivityUploader.java', 'SupabaseSleepUploader.java'):
    p = J / name
    if p.exists(): p.unlink()

# Keep activity inheritance intact without any ordinary record-upload UI/network.
(J / 'UploadExerciseActivity.java').write_text(
    '''package com.yamo.snorelab;\n\n/** Local-only activity detail base. User activity records are never uploaded. */\npublic class UploadExerciseActivity extends ExerciseActivity {\n}\n''', encoding='utf-8')

# Developer-only Mac mini sleep analysis uploader. It reuses the same pairing
# token as developer activity diagnostics and deliberately excludes all audio.
(J / 'MacMiniSleepUploader.java').write_text(r'''package com.yamo.snorelab;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Developer-mode only: sends sleep analysis values to the paired Mac mini. Never sends audio. */
public final class MacMiniSleepUploader {
    private static final String BASE="https://yamone-data.anynow.net";
    private static final String TOKEN_KEY="macmini_token_v1";
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private MacMiniSleepUploader(){}
    public interface Callback { void onSuccess(boolean alreadyUploaded); void onFailure(String message); }

    public static void upload(Context context, File sessionDir, Callback callback){
        Context c=context.getApplicationContext();
        IO.execute(()->{try{Result r=uploadBlocking(c,sessionDir);if(r.success)callback.onSuccess(r.already);else callback.onFailure(r.message);}
            catch(Exception e){callback.onFailure(errorMessage(e));}});
    }
    private static Result uploadBlocking(Context c,File dir)throws Exception{
        if(!SystemSettingsBridge.isDeveloperMode(c))return Result.fail("개발자 모드에서만 수면 분석 데이터를 전송할 수 있습니다.");
        String token=DevCaptureService.prefs(c).getString(TOKEN_KEY,"");
        if(token.isEmpty())return Result.fail("먼저 설정 > 업로드에서 테스트 기기를 Mac mini에 연결해 주세요.");
        if(!owned(c,dir))return Result.fail("수면 기록을 찾을 수 없습니다.");
        JSONObject meta=SessionStore.readMeta(dir);
        if(!"complete".equals(meta.optString("status")))return Result.fail("완료된 수면 기록만 전송할 수 있습니다.");
        long start=meta.optLong("startEpochMs",0),end=meta.optLong("endEpochMs",0);
        if(start<=0||end<=start)return Result.fail("완료 시간이 올바르지 않은 기록입니다.");
        if(SleepUploadState.wasUploaded(dir,meta))return Result.ok(true);
        String id=SleepUploadState.clientRecordId(dir,meta);if(id.isEmpty())return Result.fail("기록 식별값을 만들 수 없습니다.");
        JSONObject payload=payload(meta,SessionStore.readEvents(dir),id);
        JSONObject answer=post(payload,token);
        if(!answer.optBoolean("ok"))return Result.fail("Mac mini 수면 분석 데이터 전송에 실패했습니다.");
        if(!SleepUploadState.markUploaded(dir,meta,System.currentTimeMillis()))return Result.fail("전송은 완료됐지만 휴대폰의 완료 표시 저장에 실패했습니다.");
        return Result.ok(answer.optBoolean("alreadyUploaded",false));
    }
    private static JSONObject payload(JSONObject meta,JSONArray events,String id)throws Exception{
        long start=meta.optLong("startEpochMs",0),end=meta.optLong("endEpochMs",0);
        long duration=Math.max(0,meta.optLong("durationMs",end-start));
        int confirmed=Math.max(0,meta.optInt("snoreConfirmedCount",0));
        long confirmedMs=Math.max(0,meta.optLong("confirmedSnoreMs",0)),candidateMs=0;
        JSONArray clean=new JSONArray();
        if(events!=null)for(int i=0;i<events.length();i++){
            JSONObject src=events.optJSONObject(i);if(src==null)continue;long d=Math.max(0,src.optLong("durationMs",0));candidateMs+=d;
            JSONObject e=new JSONObject().put("index",i).put("auto_candidate",true)
                .put("start_offset_ms",Math.max(0,src.optLong("startOffsetMs",0)))
                .put("end_offset_ms",Math.max(0,src.optLong("endOffsetMs",0))).put("duration_ms",d);
            copyDouble(e,"score_avg",src,"scoreAvg");copyDouble(e,"score_max",src,"scoreMax");
            copyDouble(e,"dbfs_avg",src,"dbfsAvg");copyDouble(e,"dbfs_max",src,"dbfsMax");
            copyDouble(e,"low_band_ratio_avg",src,"lowBandRatioAvg");copyDouble(e,"periodicity_avg",src,"periodicityAvg");
            copyDouble(e,"periodicity_max",src,"periodicityMax");copyDouble(e,"zero_cross_rate_avg",src,"zeroCrossRateAvg");
            copyDouble(e,"threshold_avg",src,"thresholdAvg");
            if(src.has("candidateWindowCount")&&!src.isNull("candidateWindowCount"))e.put("candidate_windows",Math.max(0,src.optInt("candidateWindowCount",0)));
            e.put("review_label",review(src.optString("reviewLabel","UNREVIEWED")));clean.put(e);
        }
        JSONObject metrics=new JSONObject().put("analysis_schema","sleep_features_v2")
            .put("candidate_count",clean.length()).put("candidate_duration_ms",candidateMs)
            .put("reviewed_count",Math.max(0,meta.optInt("reviewedCount",0))).put("confirmed_count",confirmed)
            .put("rejected_count",Math.max(0,meta.optInt("snoreRejectedCount",0))).put("uncertain_count",Math.max(0,meta.optInt("uncertainCount",0)))
            .put("confirmed_snore_ms",confirmedMs).put("events",clean).put("audio_uploaded",false);
        String v=meta.optString("detectorVersion","");if(!v.isEmpty())metrics.put("detector_version",v);
        return new JSONObject().put("client_record_id",id).put("started_at",iso(start)).put("ended_at",iso(end))
            .put("duration_seconds",Math.max(0,Math.round(duration/1000.0))).put("snore_seconds",Math.max(0,Math.round(confirmedMs/1000.0)))
            .put("snore_events",confirmed).put("metrics",metrics);
    }
    private static JSONObject post(JSONObject body,String token)throws Exception{
        long ticket=DevNetworkGuard.ticket();DevNetworkGuard.check(ticket);
        HttpURLConnection conn=DevNetworkGuard.open(new URL(BASE+"/dev-data/sleep"));
        try{byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);conn.setConnectTimeout(15000);conn.setReadTimeout(25000);conn.setRequestMethod("POST");conn.setDoOutput(true);
            conn.setRequestProperty("Authorization","Bearer "+token);conn.setRequestProperty("Content-Type","application/json; charset=utf-8");conn.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out=conn.getOutputStream()){DevNetworkGuard.check(ticket);out.write(bytes);}
            int status=conn.getResponseCode();InputStream in=status>=200&&status<300?conn.getInputStream():conn.getErrorStream();String text="";
            if(in!=null)try(InputStream src=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=src.read(b))!=-1&&out.size()<65536)out.write(b,0,n);text=new String(out.toByteArray(),StandardCharsets.UTF_8);}
            JSONObject r;try{r=text.isEmpty()?new JSONObject():new JSONObject(text);}catch(Exception e){r=new JSONObject().put("ok",false);}
            if(status<200||status>=300)throw new IOException("macmini_http_"+status+":"+r.optString("error","unknown"));return r;
        }finally{DevNetworkGuard.done(conn);}
    }
    private static void copyDouble(JSONObject t,String tk,JSONObject s,String sk)throws Exception{if(!s.has(sk)||s.isNull(sk))return;double v=s.optDouble(sk,Double.NaN);if(!Double.isNaN(v)&&!Double.isInfinite(v))t.put(tk,v);}
    private static String review(String v){return "SNORE".equals(v)||"NOT_SNORE".equals(v)||"UNCERTAIN".equals(v)?v:"UNREVIEWED";}
    private static boolean owned(Context c,File d){if(c==null||d==null||!d.isDirectory())return false;try{return d.getCanonicalPath().startsWith(SessionStore.sessionsRoot(c).getCanonicalPath()+File.separator);}catch(Exception e){return false;}}
    private static String iso(long ms){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.format(new Date(ms));}
    private static String errorMessage(Throwable e){String m=e.getMessage();if(m!=null&&m.contains("403"))return "Mac mini 테스트 기기 인증을 확인해 주세요.";return "수면 분석 데이터 전송 중 오류가 발생했습니다.";}
    private static final class Result{final boolean success,already;final String message;Result(boolean s,boolean a,String m){success=s;already=a;message=m;}static Result ok(boolean a){return new Result(true,a,"");}static Result fail(String m){return new Result(false,false,m);}}
}
''', encoding='utf-8')

# Reuse the existing sleep detail enhancer, but expose it only in developer mode
# and route it to the Mac mini uploader. Normal users never see the button.
enh = J / 'SleepUploadUiEnhancer.java'
if enh.exists():
    s = enh.read_text(encoding='utf-8')
    s = s.replace('SupabaseSleepUploader.upload(activity, sessionDir, new SupabaseSleepUploader.Callback()',
                  'MacMiniSleepUploader.upload(activity, sessionDir, new MacMiniSleepUploader.Callback()')
    s = s.replace('    private static void enhance(MainActivity activity) {\n        View root = activity.getWindow().getDecorView();\n        updatePrivacyCopy(root);',
                  '    private static void enhance(MainActivity activity) {\n        if (!SystemSettingsBridge.isDeveloperMode(activity)) return;\n        View root = activity.getWindow().getDecorView();')
    s = s.replace('button.setText("☁ 이 기록 업로드");','button.setText("개발자 분석 전송");')
    s = s.replace('button.setText("☁ 이 기록 업로드");','button.setText("개발자 분석 전송");')
    s = s.replace('이 수면 기록의 분석 데이터를 서버로 보낼까요?', '이 수면 기록의 분석 데이터를 Mac mini 테스트 서버로 보낼까요?')
    s = s.replace('자동으로 전송하지 않으며, 이 기록은 한 번만 전송됩니다.', '개발자 모드에서만 수동으로 전송하며, 이 기록은 한 번만 전송됩니다.')
    s = s.replace('수면 분석 데이터 업로드', '개발자 수면 분석 전송')
    s = s.replace('이미 서버에 업로드된 기록입니다.', '이미 Mac mini에 전송된 기록입니다.')
    s = s.replace('수면 분석 데이터 1건을 업로드했습니다.', '수면 분석 데이터 1건을 Mac mini에 전송했습니다.')
    s = s.replace('✓ 업로드 완료', '✓ 전송 완료')
    enh.write_text(s, encoding='utf-8')

# Keep SleepUploadState for a durable one-time receipt. Restore lifecycle hooks if
# an earlier patch removed them.
app = J / 'YamoneApplication.java'
s = app.read_text(encoding='utf-8')
if 'SleepUploadUiEnhancer.attach(main);' not in s:
    marker='            if (activity instanceof MainActivity) {'
    if marker in s:
        s=s.replace(marker, marker+'\n                SleepUploadUiEnhancer.attach((MainActivity) activity);',1)
if 'SleepUploadUiEnhancer.detach(main);' not in s:
    marker='    @Override public void onActivityDestroyed(Activity activity) {'
    if marker in s:
        s=s.replace(marker, marker+'\n        if (activity instanceof MainActivity) SleepUploadUiEnhancer.detach((MainActivity) activity);',1)
app.write_text(s, encoding='utf-8')

# v0.36.10 temporarily routed this generic Supabase helper through the developer
# guard. Restore it because SkiLiftApi still legitimately uses Supabase; it is not
# an activity/sleep record collection path.
p = J / 'SupabaseAnonymousRpcClient.java'
s = p.read_text(encoding='utf-8')
s = re.sub(r'\n    static JSONObject developerAuth\(Context context\) throws Exception \{.*?\n    \}\n','\n',s,count=1,flags=re.S)
s = s.replace('DevNetworkGuard.open(url)', '(HttpURLConnection) url.openConnection()')
s = s.replace('DevNetworkGuard.check(); out.write(bytes);', 'out.write(bytes);')
p.write_text(s, encoding='utf-8')

# Explicit local-only privacy language for ordinary users.
main = J / 'MainActivity.java'
s = main.read_text(encoding='utf-8')
s = s.replace('TextView p = text("녹음과 분석 기록은 앱 내부에 저장합니다.", 11, MUTED, false);','TextView p = text("수면 기록과 분석 결과는 휴대폰 내부에만 저장합니다.", 11, MUTED, false);')
s = s.replace('TextView privacyHelp = text("기록은 휴대폰 내부 저장소에서 관리해요.", 11, PRIMARY2, true);','TextView privacyHelp = text("활동·수면 개인 기록은 휴대폰 내부에서만 관리해요.", 11, PRIMARY2, true);')
s = s.replace('privacy.addView(checkLine("수면 기록은 휴대폰 내부 저장소에서 관리합니다."));','privacy.addView(checkLine("활동·수면 개인 기록은 야모네 서버로 전송하지 않습니다."));')
main.write_text(s, encoding='utf-8')

# Version.
s = BUILD.read_text(encoding='utf-8').replace('versionCode 106','versionCode 107').replace("versionName '0.36.12'","versionName '0.36.13'")
BUILD.write_text(s, encoding='utf-8')
js=A/'v03610-dataset.js';s=js.read_text(encoding='utf-8').replace("const V='0.36.12'","const V='0.36.13'");js.write_text(s,encoding='utf-8')
for asset in A.rglob('*'):
    if not asset.is_file(): continue
    try:text=asset.read_text(encoding='utf-8')
    except (UnicodeDecodeError,OSError):continue
    new=text.replace('0.36.12','0.36.13')
    if new!=text:asset.write_text(new,encoding='utf-8')
print('Applied v0.36.13: ordinary activity/sleep local-only; developer sleep analysis routes to Mac mini.')
