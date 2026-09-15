/* v0.30.00: real Android permission + local storage settings. */
(function(){
  'use strict';
  let refreshTimer=0;

  function parse(raw,fallback){try{return typeof raw==='string'?JSON.parse(raw):raw;}catch(e){return fallback;}}
  function call(name,args,fallback){
    try{
      const b=window.YamoneSystemSettings;
      if(!b||typeof b[name]!=='function')return fallback;
      return parse(b[name].apply(b,args||[]),fallback);
    }catch(e){return fallback;}
  }
  function settingTitle(name){
    if(typeof settingHeader==='function')return settingHeader(name);
    return `<div class="move-sticky"><div class="move-header-row"><button id="settingBack" class="setting-back">‹</button><div class="move-plain-title">${name}</div></div></div>`;
  }
  function backBind(){
    const back=document.getElementById('settingBack');
    if(back)back.onclick=()=>{settingPage=null;view='main';tab='settings';if(typeof setFocusMode==='function')setFocusMode(false);if(typeof syncTabs==='function')syncTabs();render();};
  }
  function status(ok,on='허용',off='필요'){
    return `<span class="v3000-status ${ok?'ok':'warn'}">${ok?on:off}</span>`;
  }
  function permissionRow(title,desc,ok,on,off){
    return `<div class="card v3000-settings-summary"><div><b>${title}</b><small>${desc}</small></div>${status(!!ok,on||'허용',off||'필요')}</div>`;
  }
  function permissionState(){return call('getPermissionState',[],{} )||{};}

  function renderPermissions(){
    const p=permissionState();
    if(typeof setFocusMode==='function')setFocusMode(true);
    screen.innerHTML=`${settingTitle('권한')}
      <div class="section">활동 기록</div>
      ${permissionRow('위치','걷기 · 달리기 · 자전거 GPS 기록',p.location,p.fineLocation?'정밀 허용':'허용')}
      ${permissionRow('백그라운드 위치','자동 시작·Snow 등 백그라운드 기록에 사용',p.backgroundLocation,'항상 허용','설정 필요')}
      ${permissionRow('활동 인식','걷기 · 달리기 · 자전거 자동감지',p.activityRecognition)}
      ${permissionRow('알림','자동감지 확인·활동 기록 상태 알림',p.notifications)}
      <div class="section">수면 · 알람</div>
      ${permissionRow('마이크','수면 중 코골이 후보 분석용 로컬 녹음',p.microphone)}
      ${permissionRow('정확한 알람','설정한 시각에 알람 울림',p.exactAlarm,'허용','설정 필요')}
      ${permissionRow('전체화면 알람','잠금화면에서 알람 화면 표시',p.fullScreenIntent,'허용','설정 확인')}
      <div class="v3000-settings-actions">
        <button id="v3000RequestPermissions" class="v3000-settings-primary">필요 권한 요청</button>
        <button id="v3000OpenAppSettings" class="v3000-settings-secondary">Android 앱 설정</button>
      </div>
      ${p.exactAlarm?``:`<div class="v3000-settings-actions v3000-settings-single"><button id="v3000ExactAlarm" class="v3000-settings-primary">정확한 알람 허용 설정</button></div>`}
      <div class="v3000-settings-note"><b>권한 상태는 휴대폰의 실제 Android 설정을 표시합니다.</b><br>백그라운드 위치처럼 앱 내부 팝업만으로 변경할 수 없는 권한은 Android 앱 설정에서 변경합니다.</div>`;
    backBind();
    const req=document.getElementById('v3000RequestPermissions');if(req)req.onclick=()=>call('requestCorePermissions',[],null);
    const app=document.getElementById('v3000OpenAppSettings');if(app)app.onclick=()=>call('openAppSettings',[],null);
    const exact=document.getElementById('v3000ExactAlarm');if(exact)exact.onclick=()=>call('openExactAlarmSettings',[],null);
  }

  function bytes(v){
    let n=Math.max(0,Number(v)||0);const units=['B','KB','MB','GB'];let i=0;
    while(n>=1024&&i<units.length-1){n/=1024;i++;}
    const digits=i===0?0:n<10?2:n<100?1:0;
    return `${n.toFixed(digits)} ${units[i]}`;
  }
  function storageRow(title,desc,size,count){
    const c=Number(count)||0;
    return `<div class="card v3000-storage-row"><div><b>${title}</b><small>${desc}${c>0?` · ${c}개 기록`:''}</small></div><strong>${bytes(size)}</strong></div>`;
  }
  function renderStorage(){
    const s=call('getStorageState',[],{})||{};
    const tracked=(Number(s.movementBytes)||0)+(Number(s.sleepBytes)||0)+(Number(s.snowBytes)||0);
    if(typeof setFocusMode==='function')setFocusMode(true);
    screen.innerHTML=`${settingTitle('데이터/저장')}
      <div class="card v3000-storage-total"><span>앱에서 관리하는 기록 데이터</span><strong>${bytes(tracked)}</strong><small>이동 활동 · 수면 · Snow 로컬 데이터 합계</small></div>
      <div class="section">저장 항목</div>
      ${storageRow('이동 활동','GPS 경로와 운동 요약',s.movementBytes,s.movementCount)}
      ${storageRow('수면','수면 분석값과 저장된 오디오/후보 구간',s.sleepBytes,s.sleepCount)}
      ${storageRow('Snow','Snow 기록과 오프라인 지도 데이터',s.snowBytes,s.snowCount)}
      ${storageRow('임시 캐시','내보내기·임시 처리 파일',s.cacheBytes,0)}
      <div class="v3000-settings-actions v3000-settings-single"><button id="v3000RefreshStorage" class="v3000-settings-primary">사용량 다시 계산</button></div>
      <div class="v3000-settings-note"><b>기록 원본은 휴대폰 내부에 저장됩니다.</b><br>이동 기록은 기록 상세에서 개별 삭제하고, 수면·Snow 데이터는 각 기능의 기록 관리에서 삭제합니다. 위치공유 데이터는 이 집계에서 제외했습니다.</div>`;
    backBind();
    const refresh=document.getElementById('v3000RefreshStorage');if(refresh)refresh.onclick=renderStorage;
  }

  const previous=window.renderSettingDetail;
  window.renderSettingDetail=function(){
    if(typeof settingPage!=='undefined'&&settingPage==='권한'){renderPermissions();return;}
    if(typeof settingPage!=='undefined'&&settingPage==='데이터/저장'){renderStorage();return;}
    return previous?previous.apply(this,arguments):undefined;
  };

  function refreshVisible(){
    if(typeof view==='undefined'||view!=='setting')return;
    if(settingPage==='권한')renderPermissions();
  }
  clearInterval(refreshTimer);
  refreshTimer=setInterval(refreshVisible,1500);
})();
