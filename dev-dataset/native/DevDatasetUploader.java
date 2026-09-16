package com.yamo.snorelab;

import android.app.job.*;
import android.content.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Durable Mac mini outbox: upload immutable parts, acknowledge, then finalize. */
final class DevDatasetUploader {
    private static final String BASE="https://yamone-data.anynow.net";
    private static final String TOKEN_KEY="macmini_token_v1";
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private static final AtomicBoolean AGAIN=new AtomicBoolean();
    private static final int JOB=6816;

    static boolean paired(Context c){
        return !DevCaptureService.prefs(c).getString(TOKEN_KEY,"").isEmpty();
    }
    static File receipt(File dir,int part){return new File(dir,DevDatasetStore.name(part)+".mac.receipt.json");}
    static File completeReceipt(File dir){return new File(dir,"mac.complete.receipt.json");}

    static void enroll(Context context,String code){
        Context c=context.getApplicationContext();
        if(!SystemSettingsBridge.isDeveloperMode(c))return;
        IO.execute(()->{try{
            DevNetworkGuard.check();
            JSONObject result=requestJson(c,"POST","/dev-data/enroll",new JSONObject().put("code",code==null?"":code.trim()),null,DevNetworkGuard.ticket());
            String token=result.optString("token","");
            if(!result.optBoolean("ok")||token.length()<32)throw new IOException("pairing_failed");
            DevCaptureService.prefs(c).edit().putString(TOKEN_KEY,token).putBoolean("paired",true).putString("error","").apply();
            kick(c);
        }catch(Exception e){error(c,e);}});
    }
    static boolean busy(){return BUSY.get();}
    static List<File> sessions(Context c){
        File[] dirs=DevCaptureService.root(c).listFiles(f->f.isDirectory()&&f.getName().matches("[a-f0-9-]{36}"));
        List<File> out=new ArrayList<>();if(dirs!=null)Collections.addAll(out,dirs);
        out.sort(Comparator.comparingLong(File::lastModified).reversed());return out;
    }
    static boolean approved(Context c,File dir,JSONObject m){
        long epoch=DevCaptureService.prefs(c).getLong("approvalEpoch",0);
        if("automatic".equals(m.optString("mode"))&&m.optLong("approvalEpoch",-1)==epoch)return true;
        try{return DevDatasetStore.readJson(new File(dir,"approval.json")).optLong("epoch",-1)==epoch;}catch(Exception e){return false;}
    }
    static void approve(Context context,String id){
        Context c=context.getApplicationContext();if(!SystemSettingsBridge.isDeveloperMode(c)||!id.matches("[a-f0-9-]{36}"))return;
        IO.execute(()->{try{
            File dir=new File(DevCaptureService.root(c),id);JSONObject m=DevDatasetStore.readJson(new File(dir,"manifest.json"));
            if(!"closed".equals(m.optString("state")))throw new IOException("record_still_running");
            DevDatasetStore.atomicJson(new File(dir,"approval.json"),DevCaptureService.obj("epoch",DevCaptureService.prefs(c).getLong("approvalEpoch",0)));
            kick(c);
        }catch(Exception e){error(c,e);}});
    }
    static void deleteLocal(Context context,String id){
        Context c=context.getApplicationContext();if(!SystemSettingsBridge.isDeveloperMode(c)||id==null||!id.matches("[a-f0-9-]{36}"))return;
        IO.execute(()->{try{
            File dir=new File(DevCaptureService.root(c),id);
            JSONObject m=DevDatasetStore.readJson(new File(dir,"manifest.json"));
            if(!"closed".equals(m.optString("state")))throw new IOException("cannot_delete_active_capture");
            erase(dir);DevCaptureService.prefs(c).edit().putString("error","").apply();
        }catch(Exception e){error(c,e);}});
    }
    private static void erase(File f) throws IOException {
        File[] kids=f.listFiles();if(kids!=null)for(File k:kids)erase(k);
        if(!f.delete()&&f.exists())throw new IOException("local_delete_failed");
    }
    static void kick(Context context){
        Context c=context.getApplicationContext();
        if(!SystemSettingsBridge.isDeveloperMode(c)||!paired(c))return;
        AGAIN.set(true);
        if(!BUSY.compareAndSet(false,true))return;
        IO.execute(()->{boolean failed=false;
            try{do{AGAIN.set(false);drain(c);}while(AGAIN.get()&&SystemSettingsBridge.isDeveloperMode(c));}
            catch(Exception e){failed=true;error(c,e);schedule(c);}
            finally{BUSY.set(false);if(!failed&&AGAIN.get())kick(c);}
        });
    }
    private static void schedule(Context c){
        if(!SystemSettingsBridge.isDeveloperMode(c)||!paired(c))return;
        JobScheduler j=c.getSystemService(JobScheduler.class);
        if(j!=null)j.schedule(new JobInfo.Builder(JOB,new ComponentName(c,RetryJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(30000)
                .setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).setPersisted(true).build());
    }
    private static void drain(Context c) throws Exception {
        long ticket=DevNetworkGuard.ticket();
        String token=DevCaptureService.prefs(c).getString(TOKEN_KEY,"");
        if(token.isEmpty())throw new IOException("device_not_paired");
        List<File> queue=sessions(c);Collections.reverse(queue);
        for(File dir:queue){
            DevNetworkGuard.check(ticket);
            JSONObject m=DevDatasetStore.readJson(new File(dir,"manifest.json"));
            if(!approved(c,dir,m)||completeReceipt(dir).isFile())continue;
            int through=m.optInt("releasedThrough",-1);if(through<0)continue;
            String id=m.getString("id");
            JSONObject begin=requestJson(c,"POST","/dev-data/session/begin",new JSONObject().put("id",id).put("manifest",m),token,ticket);
            if(!begin.optBoolean("ok"))throw new IOException("server_begin_failed");
            for(File f:DevDatasetStore.descriptors(dir)){
                JSONObject p=DevDatasetStore.readJson(f);int number=p.getInt("part");if(number>through)continue;
                File receipt=receipt(dir,number);if(receipt.isFile())continue;
                DevNetworkGuard.check(ticket);
                if(!approved(c,dir,m))throw new IOException("upload_approval_revoked");
                File data=new File(dir,p.getString("file"));
                if(!p.getString("sha256").equals(DevDatasetStore.sha256(data)))throw new IOException("local_checksum_mismatch");
                uploadPart(c,id,number,data,p,token,ticket);
                DevDatasetStore.atomicJson(receipt,DevCaptureService.obj("sha256",p.getString("sha256"),"server","mac-mini","ackWallMs",System.currentTimeMillis()));
                DevCaptureService.prefs(c).edit().putLong("lastUploadMs",System.currentTimeMillis()).putString("error","").apply();
            }
            m=DevDatasetStore.readJson(new File(dir,"manifest.json"));
            if("closed".equals(m.optString("state"))){
                boolean all=true;for(int i=0;i<m.optInt("partCount",0);i++)all&=receipt(dir,i).isFile();
                if(all){
                    JSONObject fin=requestJson(c,"POST","/dev-data/session/"+id+"/finalize",new JSONObject().put("manifest",m),token,ticket);
                    if(!fin.optBoolean("ok"))throw new IOException("server_finalize_failed");
                    DevDatasetStore.atomicJson(completeReceipt(dir),DevCaptureService.obj("complete",true,"server","mac-mini","ackWallMs",System.currentTimeMillis()));
                }
            }
        }
    }
    private static void uploadPart(Context c,String id,int number,File file,JSONObject p,String token,long ticket) throws Exception {
        DevNetworkGuard.check(ticket);
        HttpURLConnection conn=DevNetworkGuard.open(new URL(BASE+"/dev-data/session/"+id+"/part/"+number));
        try{
            conn.setConnectTimeout(15000);conn.setReadTimeout(30000);conn.setRequestMethod("PUT");conn.setDoOutput(true);
            conn.setRequestProperty("Authorization","Bearer "+token);
            conn.setRequestProperty("Content-Type","application/gzip");
            conn.setRequestProperty("x-yamone-sha256",p.getString("sha256"));
            conn.setRequestProperty("x-yamone-first-seq",String.valueOf(p.getLong("firstSeq")));
            conn.setRequestProperty("x-yamone-last-seq",String.valueOf(p.getLong("lastSeq")));
            conn.setRequestProperty("x-yamone-event-count",String.valueOf(p.getLong("eventCount")));
            conn.setRequestProperty("x-yamone-first-wall-ms",String.valueOf(p.optLong("firstWallMs",0)));
            conn.setRequestProperty("x-yamone-last-wall-ms",String.valueOf(p.optLong("lastWallMs",0)));
            conn.setRequestProperty("x-yamone-reason",p.optString("reason",""));
            conn.setFixedLengthStreamingMode(file.length());
            try(InputStream in=new FileInputStream(file);OutputStream out=conn.getOutputStream()){
                byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){DevNetworkGuard.check(ticket);out.write(b,0,n);}
            }
            JSONObject result=readResponse(conn);
            if(!result.optBoolean("ok"))throw new IOException("part_upload_failed:"+result.optString("error","unknown"));
        }finally{DevNetworkGuard.done(conn);}
    }
    private static JSONObject requestJson(Context c,String method,String path,JSONObject body,String token,long ticket) throws Exception {
        DevNetworkGuard.check(ticket);
        HttpURLConnection conn=DevNetworkGuard.open(new URL(BASE+path));
        try{
            conn.setConnectTimeout(15000);conn.setReadTimeout(25000);conn.setRequestMethod(method);conn.setDoOutput(body!=null);
            conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
            if(token!=null&&!token.isEmpty())conn.setRequestProperty("Authorization","Bearer "+token);
            if(body!=null){byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);conn.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=conn.getOutputStream()){DevNetworkGuard.check(ticket);out.write(bytes);}}
            return readResponse(conn);
        }finally{DevNetworkGuard.done(conn);}
    }
    private static JSONObject readResponse(HttpURLConnection conn) throws Exception {
        int status=conn.getResponseCode();InputStream in=status>=200&&status<300?conn.getInputStream():conn.getErrorStream();
        String text="";if(in!=null)try(InputStream src=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=src.read(b))!=-1&&out.size()<65536)out.write(b,0,n);text=new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
        JSONObject json;try{json=text.isEmpty()?new JSONObject():new JSONObject(text);}catch(Exception e){json=new JSONObject().put("ok",false).put("error","invalid_server_response");}
        if(status<200||status>=300)throw new IOException("macmini_http_"+status+":"+json.optString("error","unknown"));
        return json;
    }
    private static void error(Context c,Exception e){String s=e.getMessage();if(s==null)s=e.getClass().getSimpleName();
        DevCaptureService.prefs(c).edit().putString("error",s.substring(0,Math.min(180,s.length()))).apply();}
    public static final class RetryJob extends JobService {
        private volatile Thread worker;
        @Override public boolean onStartJob(JobParameters p){
            if(!SystemSettingsBridge.isDeveloperMode(this)||!paired(this)||!BUSY.compareAndSet(false,true))return false;
            IO.execute(()->{worker=Thread.currentThread();boolean retry=false;
                try{do{AGAIN.set(false);drain(this);}while(AGAIN.get()&&SystemSettingsBridge.isDeveloperMode(this));}
                catch(Exception e){error(this,e);retry=SystemSettingsBridge.isDeveloperMode(this);}
                finally{Thread.interrupted();worker=null;BUSY.set(false);jobFinished(p,retry);if(!retry&&AGAIN.get())kick(this);}
            });return true;
        }
        @Override public boolean onStopJob(JobParameters p){Thread w=worker;if(w!=null){w.interrupt();DevNetworkGuard.cancelThread(w);}return SystemSettingsBridge.isDeveloperMode(this);}
    }
}
