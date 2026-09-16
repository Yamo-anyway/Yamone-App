package com.yamo.snorelab;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.*;
import android.webkit.JavascriptInterface;
import org.json.*;
import java.io.*;

/** UI calls are allowed only in app developer mode; capture requires a visible user action. */
public final class DevDatasetBridge {
    private final Activity activity;
    public DevDatasetBridge(Activity activity){this.activity=activity;DevNetworkGuard.init(activity);}
    @JavascriptInterface public String getState(){
        if(!SystemSettingsBridge.isDeveloperMode(activity))return "{\"developer\":false}";
        JSONObject out=DevCaptureService.obj("developer",true,"active",DevCaptureService.collecting(activity),"automatic",DevCaptureService.automatic(activity),
                "paired",DevCaptureService.prefs(activity).getBoolean("paired",false),"uploading",DevDatasetUploader.busy(),
                "manualCapture",DevCaptureService.prefs(activity).getBoolean("manualCapture",true),
                "error",DevCaptureService.prefs(activity).getString("error",""),"lastUploadMs",DevCaptureService.prefs(activity).getLong("lastUploadMs",0));
        JSONArray list=new JSONArray();
        for(File dir:DevDatasetUploader.sessions(activity))try{
            JSONObject m=DevDatasetStore.readJson(new File(dir,"manifest.json"));int ack=0,parts=0;long bytes=0;
            for(File f:DevDatasetStore.descriptors(dir)){JSONObject p=DevDatasetStore.readJson(f);parts++;bytes+=p.optLong("bytes");if(new File(dir,DevDatasetStore.name(p.getInt("part"))+".receipt.json").isFile())ack++;}
            list.put(DevCaptureService.obj("id",dir.getName(),"mode",m.optString("mode"),"state",m.optString("state"),"startWallMs",m.optLong("startWallMs"),"endWallMs",m.optLong("endWallMs"),"nextHourMs",m.optLong("nextHourMs"),"parts",parts,"acked",ack,"bytes",bytes,"complete",new File(dir,"complete.receipt.json").isFile(),"approved",DevDatasetUploader.approved(activity,dir,m)));
            if(list.length()>=30)break;
        }catch(Exception ignored){}
        try{out.put("sessions",list);}catch(Exception ignored){}return out.toString();
    }
    @JavascriptInterface public void enroll(String code){if(SystemSettingsBridge.isDeveloperMode(activity))DevDatasetUploader.enroll(activity,code);}
    @JavascriptInterface public void startAutomatic(){
        if(!SystemSettingsBridge.isDeveloperMode(activity))return;
        activity.runOnUiThread(()->{
            if(activity.isFinishing()||activity.isDestroyed()||!SystemSettingsBridge.isDeveloperMode(activity))return;
            if(DevCaptureService.collecting(activity)){notice("진행 중인 수동 진단 기록을 종료한 후 켜 주세요.");return;}
            if(!DevCaptureService.prefs(activity).getBoolean("paired",false)){notice("먼저 테스터 연결 코드를 등록해 주세요.");return;}
            if(activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){notice("설정 > 권한에서 정밀 위치 권한을 허용해 주세요.");return;}
            LocationManager lm=activity.getSystemService(LocationManager.class);if(lm==null||!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)){notice("휴대폰 위치 기능을 켜 주세요.");return;}
            new AlertDialog.Builder(activity).setTitle("개발자 자동 기록·업로드를 시작할까요?")
                .setMessage("ON부터 OFF까지 GPS 원본, 센서값, 활동 판단과 수동 표시를 연속 기록합니다. 매 정시에 미전송 구간을 비공개 테스트 서버로 보내며, OFF 시 마지막 구간을 보내 기록 1건으로 확정합니다.\n\n화면이 꺼져도 수집하며 배터리·저장공간·모바일 데이터를 사용합니다. 음원·연락처·광고 ID는 수집하지 않습니다.")
                .setNegativeButton("취소",null).setPositiveButton("시작",(d,w)->{
                    if(!SystemSettingsBridge.isDeveloperMode(activity))return;
                    try{activity.startForegroundService(new Intent(activity,DevCaptureService.class).setAction(DevCaptureService.START).putExtra("mode","automatic"));}
                    catch(RuntimeException e){notice("수집을 시작하지 못했습니다: "+e.getClass().getSimpleName());}
                }).show();
        });
    }
    @JavascriptInterface public void stopAutomatic(){if(!SystemSettingsBridge.isDeveloperMode(activity)||!DevCaptureService.collecting(activity))return;
        activity.runOnUiThread(()->{try{activity.startService(new Intent(activity,DevCaptureService.class).setAction(DevCaptureService.STOP));}catch(RuntimeException e){notice("종료 요청 실패");}});}
    @JavascriptInterface public void setManualCapture(boolean value){if(SystemSettingsBridge.isDeveloperMode(activity))DevCaptureService.prefs(activity).edit().putBoolean("manualCapture",value).apply();}
    @JavascriptInterface public void mark(String label,String placement,String context){
        if(!SystemSettingsBridge.isDeveloperMode(activity)||!DevCaptureService.collecting(activity))return;
        if(label==null||!label.matches("unknown|still|walking|running|cycling|vehicle"))return;
        DevCaptureService.emit("user_label",DevCaptureService.obj("activity",label,"labelSource","user_confirmed","placement",clean(placement),"context",clean(context)));
    }
    @JavascriptInterface public void event(String event){if(SystemSettingsBridge.isDeveloperMode(activity)&&event!=null&&event.matches("tunnel_enter|tunnel_exit|gps_issue|pause_reference|resume_reference"))DevCaptureService.emit("user_event",DevCaptureService.obj("event",event));}
    @JavascriptInterface public void upload(String id){
        if(!SystemSettingsBridge.isDeveloperMode(activity))return;
        activity.runOnUiThread(()->new AlertDialog.Builder(activity).setTitle("진단 기록 업로드")
            .setMessage("이 기록의 GPS·센서 원본과 활동 판단·수동 표시를 비공개 테스트 서버로 보냅니다. 이미 확인된 조각은 다시 보내지 않습니다.")
            .setNegativeButton("취소",null).setPositiveButton("업로드",(d,w)->DevDatasetUploader.approve(activity,id)).show());
    }
    @JavascriptInterface public void deleteLocal(String id){
        if(!SystemSettingsBridge.isDeveloperMode(activity))return;
        activity.runOnUiThread(()->new AlertDialog.Builder(activity).setTitle("기기의 진단 원본을 삭제할까요?")
            .setMessage("이 진단 세션의 GPS·센서 원본만 기기에서 삭제합니다. 아직 전송되지 않은 원본은 복구할 수 없습니다. 일반 활동 기록과 이미 서버에 업로드된 자료는 삭제하지 않습니다.")
            .setNegativeButton("취소",null).setPositiveButton("원본 삭제",(d,w)->DevDatasetUploader.deleteLocal(activity,id)).show());
    }
    @JavascriptInterface public void retry(){if(SystemSettingsBridge.isDeveloperMode(activity))DevDatasetUploader.kick(activity);}
    private static String clean(String s){if(s==null)return "unknown";return s.substring(0,Math.min(60,s.length()));}
    private void notice(String s){DevCaptureService.prefs(activity).edit().putString("error",s).apply();android.widget.Toast.makeText(activity,s,android.widget.Toast.LENGTH_LONG).show();}
}
