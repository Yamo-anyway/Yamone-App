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

/** Durable outbox: acknowledge immutable parts, then finalize once all parts exist. */
final class DevDatasetUploader {
    private static final String BASE="https://kumucxomdviuwuwqcpmv.supabase.co";
    private static final String KEY="sb_publishable_YYZp1A9A62KUxs5etSAiYg_0TvaK2hQ";
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private static final AtomicBoolean AGAIN=new AtomicBoolean();
    private static final int JOB=6816;
    static void enroll(Context context,String code){
        Context c=context.getApplicationContext();
        if(!SystemSettingsBridge.isDeveloperMode(c))return;
        IO.execute(()->{try{
            DevNetworkGuard.check();String answer=SupabaseAnonymousRpcClient.rpc(c,"dev_dataset_enroll",DevCaptureService.obj("p_code",code.trim()));
            if(!new JSONObject(answer).optBoolean("ok"))throw new IOException("pairing_failed");
            DevCaptureService.prefs(c).edit().putBoolean("paired",true).putString("error","").apply();kick(c);
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
        if(!SystemSettingsBridge.isDeveloperMode(c)||!DevCaptureService.prefs(c).getBoolean("paired",false))return;
        AGAIN.set(true);
        if(!BUSY.compareAndSet(false,true))return;
        IO.execute(()->{boolean failed=false;
            try{do{AGAIN.set(false);drain(c);}while(AGAIN.get()&&SystemSettingsBridge.isDeveloperMode(c));}
            catch(Exception e){failed=true;error(c,e);schedule(c);}
            finally{BUSY.set(false);if(!failed&&AGAIN.get())kick(c);}
        });
    }
    private static void schedule(Context c){
        if(!SystemSettingsBridge.isDeveloperMode(c))return;
        JobScheduler j=c.getSystemService(JobScheduler.class);
        if(j!=null)j.schedule(new JobInfo.Builder(JOB,new ComponentName(c,RetryJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(30000)
                .setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).setPersisted(true).build());
    }
    private static void drain(Context c) throws Exception {
        long ticket=DevNetworkGuard.ticket();JSONObject auth=null;
        List<File> queue=sessions(c);Collections.reverse(queue);
        for(File dir:queue){
            DevNetworkGuard.check(ticket);
            JSONObject m=DevDatasetStore.readJson(new File(dir,"manifest.json"));
            if(!approved(c,dir,m)||new File(dir,"complete.receipt.json").isFile())continue;
            int through=m.optInt("releasedThrough",-1);if(through<0)continue;
            if(auth==null)auth=SupabaseAnonymousRpcClient.developerAuth(c);
            String uid=auth.getString("userId"),token=auth.getString("accessToken"),id=m.getString("id");
            File owner=new File(dir,"owner.json");
            if(owner.isFile()&&!uid.equals(DevDatasetStore.readJson(owner).optString("userId")))throw new IOException("dataset_owner_changed");
            rpc(c,"dev_dataset_begin",DevCaptureService.obj("p_id",id,"p_manifest",m),ticket);
            DevDatasetStore.atomicJson(owner,DevCaptureService.obj("userId",uid));
            for(File f:DevDatasetStore.descriptors(dir)){
                JSONObject p=DevDatasetStore.readJson(f);int number=p.getInt("part");if(number>through)continue;
                File receipt=new File(dir,DevDatasetStore.name(number)+".receipt.json");if(receipt.isFile())continue;
                DevNetworkGuard.check(ticket);
                if(!approved(c,dir,m))throw new IOException("upload_approval_revoked");
                File data=new File(dir,p.getString("file"));
                if(!p.getString("sha256").equals(DevDatasetStore.sha256(data)))throw new IOException("local_checksum_mismatch");
                String path=uid+"/"+id+"/"+String.format(Locale.US,"%06d",number)+"-"+p.getString("sha256")+".jsonl.gz";
                uploadFile(data,path,token,ticket);
                rpc(c,"dev_dataset_commit_part",DevCaptureService.obj("p_id",id,"p_part",p),ticket);
                DevDatasetStore.atomicJson(receipt,DevCaptureService.obj("sha256",p.getString("sha256"),"ackWallMs",System.currentTimeMillis()));
                DevCaptureService.prefs(c).edit().putLong("lastUploadMs",System.currentTimeMillis()).putString("error","").apply();
            }
            // Never mark complete while offline/missing a part; this control request carries no raw data.
            m=DevDatasetStore.readJson(new File(dir,"manifest.json"));
            if("closed".equals(m.optString("state"))){
                boolean all=true;for(int i=0;i<m.optInt("partCount",0);i++)all&=new File(dir,DevDatasetStore.name(i)+".receipt.json").isFile();
                if(all){rpc(c,"dev_dataset_finalize",DevCaptureService.obj("p_id",id,"p_manifest",m),ticket);
                    DevDatasetStore.atomicJson(new File(dir,"complete.receipt.json"),DevCaptureService.obj("complete",true,"ackWallMs",System.currentTimeMillis()));}
            }
        }
    }
    private static JSONObject rpc(Context c,String name,JSONObject args,long ticket) throws Exception {
        DevNetworkGuard.check(ticket);JSONObject r=new JSONObject(SupabaseAnonymousRpcClient.rpc(c,name,args));
        DevNetworkGuard.check(ticket);if(!r.optBoolean("ok"))throw new IOException("server_ack_missing");return r;
    }
    private static void uploadFile(File file,String path,String token,long ticket) throws Exception {
        DevNetworkGuard.check(ticket);HttpURLConnection c=DevNetworkGuard.open(new URL(BASE+"/storage/v1/object/developer-datasets/"+path));
        try{
            c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setRequestMethod("POST");c.setDoOutput(true);
            c.setRequestProperty("apikey",KEY);c.setRequestProperty("Authorization","Bearer "+token);
            c.setRequestProperty("Content-Type","application/gzip");c.setRequestProperty("x-upsert","false");c.setFixedLengthStreamingMode(file.length());
            DevNetworkGuard.check(ticket);
            try(InputStream in=new FileInputStream(file);OutputStream out=c.getOutputStream()){
                byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){DevNetworkGuard.check(ticket);out.write(b,0,n);}
            }
            int status=c.getResponseCode();
            if(status<200||status>=300){
                String body="";InputStream err=c.getErrorStream();if(err!=null)try(InputStream e=err){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[1024];int n;while(b.size()<4096&&(n=e.read(buf,0,Math.min(buf.length,4096-b.size())))!=-1)b.write(buf,0,n);body=new String(b.toByteArray(),StandardCharsets.UTF_8);}
                // Storage returns 400 ResourceAlreadyExists in some versions, 409 in others.
                if(status!=409&&!(status==400&&(body.contains("Duplicate")||body.contains("already exists")||body.contains("ResourceAlreadyExists"))))
                    throw new IOException("storage_http_"+status+" (테스터 등록·저장 한도·네트워크 확인)");
            }
        }finally{DevNetworkGuard.done(c);}
    }
    private static void error(Context c,Exception e){String s=e.getMessage();if(s==null)s=e.getClass().getSimpleName();
        DevCaptureService.prefs(c).edit().putString("error",s.substring(0,Math.min(180,s.length()))).apply();}
    public static final class RetryJob extends JobService {
        private volatile Thread worker;
        @Override public boolean onStartJob(JobParameters p){
            if(!SystemSettingsBridge.isDeveloperMode(this)||!DevCaptureService.prefs(this).getBoolean("paired",false)||!BUSY.compareAndSet(false,true))return false;
            IO.execute(()->{worker=Thread.currentThread();boolean retry=false;
                try{do{AGAIN.set(false);drain(this);}while(AGAIN.get()&&SystemSettingsBridge.isDeveloperMode(this));}
                catch(Exception e){error(this,e);retry=SystemSettingsBridge.isDeveloperMode(this);}
                finally{Thread.interrupted();worker=null;BUSY.set(false);jobFinished(p,retry);if(!retry&&AGAIN.get())kick(this);}
            });return true;
        }
        @Override public boolean onStopJob(JobParameters p){Thread w=worker;if(w!=null){w.interrupt();DevNetworkGuard.cancelThread(w);}return SystemSettingsBridge.isDeveloperMode(this);}
    }
}
