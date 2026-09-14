/* v0.25.16: real auto-detect settings + inline GPS + discard-current-record flow. */
(function(){
  'use strict';
  const A='assets/';
  let scheduled=false;
  let cancelPoll=0;

  function nativeSettings(){
    try{
      if(!window.YamoneAutoDetect||typeof YamoneAutoDetect.getSettings!=='function')return null;
      const raw=YamoneAutoDetect.getSettings();
      return typeof raw==='string'?JSON.parse(raw):raw;
    }catch(e){return null;}
  }
  function defaults(){
    return {walk:false,run:false,bike:false,startMode:'ask',repromptMin:5,sensitivity:'normal',endMode:'ask',activityRecognition:false,backgroundLocation:false,notifications:false};
  }
  function loadSettings(){return Object.assign(defaults(),nativeSettings()||{});}
  function saveSettings(s){
    try{
      if(window.YamoneAutoDetect&&typeof YamoneAutoDetect.saveSettings==='function'){
        YamoneAutoDetect.saveSettings(JSON.stringify({
          walk:!!s.walk,run:!!s.run,bike:!!s.bike,
          startMode:s.startMode==='auto'?'auto':'ask',
          repromptMin:Number(s.repromptMin)===10?10:5,
          sensitivity:['fast','normal','accurate'].includes(s.sensitivity)?s.sensitivity:'normal',
          endMode:s.endMode==='auto'?'auto':'ask'
        }));
      }
    }catch(e){}
  }
  function requestPermissions(){
    try{if(window.YamoneMovement&&typeof YamoneMovement.requestPermissions==='function')YamoneMovement.requestPermissions();}catch(e){}
  }

  function renderAutoDetectSettingsV2516(){
    if(typeof screen==='undefined'||typeof settingHeader!=='function'||typeof settingToggle!=='function'||typeof settingSection!=='function'||typeof settingChips!=='function')return;
    const s=loadSettings();
    screen.innerHTML=`
      ${settingHeader('자동감지')}
      ${settingSection('활동별',
        settingToggle('v2516Walk','걷기','걷기 활동 감지',s.walk,'walk')+
        settingToggle('v2516Run','달리기','달리기 활동 감지',s.run,'run')+
        settingToggle('v2516Bike','자전거','자전거 활동 감지',s.bike,'bike')
      )}
      ${settingSection('감지 시 동작',`<div class="card setting-block v2516-two">${settingChips('v2516StartMode',[['ask','확인 후 시작'],['auto','자동 시작']],s.startMode)}</div>`)}
      ${settingSection('취소 후 다시 알림',`<div class="card setting-block v2516-two">${settingChips('v2516Reprompt',[['5','5분'],['10','10분']],String(s.repromptMin))}</div>`)}
      ${settingSection('감지 민감도',`<div class="card setting-block">${settingChips('v2516Sensitivity',[['fast','빠르게'],['normal','기본'],['accurate','정확하게']],s.sensitivity)}</div>`)}
      ${settingSection('자동 종료',`<div class="card setting-block v2516-two">${settingChips('v2516EndMode',[['auto','자동 종료'],['ask','확인 후 종료']],s.endMode)}</div>`)}
      ${(!s.activityRecognition||!s.notifications)?`<div class="setting-info v2516-permission-info">자동감지를 사용하려면 활동 인식과 알림 권한이 필요합니다.<button id="v2516Permission">권한 확인</button></div>`:''}
      ${(s.startMode==='auto'&&!s.backgroundLocation)?`<div class="setting-info v2516-permission-info">백그라운드에서 바로 GPS 기록하려면 위치를 ‘항상 허용’으로 설정해야 합니다. 허용되지 않으면 확인 알림으로 시작합니다.<button id="v2516BgLocation">위치 권한 설정</button></div>`:''}
      <div class="setting-info">활동이 일정 시간 계속될 때만 시작 후보로 판단합니다. 민감도는 감지 확인 시간을 조절합니다.</div>`;

    const back=document.getElementById('settingBack');
    if(back)back.onclick=()=>{settingPage=null;view='main';tab='settings';setFocusMode(false);syncTabs();render();};

    function mutate(fn,needsPermission){
      const next=loadSettings();fn(next);saveSettings(next);if(needsPermission)requestPermissions();renderAutoDetectSettingsV2516();
    }
    const map={v2516Walk:'walk',v2516Run:'run',v2516Bike:'bike'};
    Object.keys(map).forEach(id=>{
      const btn=screen.querySelector(`[data-setting-toggle="${id}"]`);
      if(btn)btn.onclick=()=>mutate(x=>{x[map[id]]=!x[map[id]];},true);
    });
    screen.querySelectorAll('[data-setting-chip="v2516StartMode"]').forEach(btn=>btn.onclick=()=>mutate(x=>{x.startMode=btn.dataset.value;},btn.dataset.value==='auto'));
    screen.querySelectorAll('[data-setting-chip="v2516Reprompt"]').forEach(btn=>btn.onclick=()=>mutate(x=>{x.repromptMin=Number(btn.dataset.value)===10?10:5;},false));
    screen.querySelectorAll('[data-setting-chip="v2516Sensitivity"]').forEach(btn=>btn.onclick=()=>mutate(x=>{x.sensitivity=btn.dataset.value;},false));
    screen.querySelectorAll('[data-setting-chip="v2516EndMode"]').forEach(btn=>btn.onclick=()=>mutate(x=>{x.endMode=btn.dataset.value;},false));
    const permission=document.getElementById('v2516Permission');if(permission)permission.onclick=requestPermissions;
    const bg=document.getElementById('v2516BgLocation');if(bg)bg.onclick=()=>{try{if(window.YamoneAutoDetect&&YamoneAutoDetect.openBackgroundLocationSettings)YamoneAutoDetect.openBackgroundLocationSettings();}catch(e){}};
  }

  const previousSettingDetail=window.renderSettingDetail;
  window.renderSettingDetail=function(){
    if(typeof settingPage!=='undefined'&&settingPage==='자동감지'){
      renderAutoDetectSettingsV2516();return;
    }
    return previousSettingDetail?previousSettingDetail.apply(this,arguments):undefined;
  };

  function movementState(){
    try{
      if(!window.YamoneMovement||typeof YamoneMovement.getState!=='function')return null;
      const raw=YamoneMovement.getState();return typeof raw==='string'?JSON.parse(raw):raw;
    }catch(e){return null;}
  }
  function gpsLabel(s){
    if(!s)return {text:'GPS 상태: 미확인',tone:'lost'};
    const age=Number(s.fixAgeMs),acc=s.accuracyM==null?NaN:Number(s.accuracyM);
    if(!Number.isFinite(age)||age<0||age>12000)return {text:'GPS 상태: 미확인',tone:'lost'};
    if(Number.isFinite(acc)&&acc<=20&&age<=5000)return {text:'GPS 상태: 양호',tone:'good'};
    if(Number.isFinite(acc)&&acc<=45&&age<=10000)return {text:'GPS 상태: 보통',tone:'fair'};
    return {text:'GPS 상태: 불안정',tone:'fair'};
  }

  function openCancelConfirm(){
    if(typeof moveOverlay==='undefined'||!moveOverlay)return;
    moveOverlay.dataset.v25MoveStop='1';
    moveOverlay.dataset.v2516Cancel='1';
    moveOverlay.classList.add('show');
    moveOverlay.innerHTML=`<div class="move-sheet"><div class="move-sheet-handle"></div><h3>기록을 취소할까요?</h3><p>현재 운동 기록은 저장하지 않고 삭제됩니다.</p><div class="move-sheet-actions"><button id="v2516CancelKeep" class="move-sheet-cancel">계속 기록</button><button id="v2516CancelConfirm" class="move-sheet-stop">기록 취소</button></div></div>`;
    const keep=document.getElementById('v2516CancelKeep');if(keep)keep.onclick=closeCancelOverlay;
    const confirm=document.getElementById('v2516CancelConfirm');if(confirm)confirm.onclick=cancelCurrentRecord;
    moveOverlay.onclick=e=>{if(e.target===moveOverlay)closeCancelOverlay();};
  }
  function closeCancelOverlay(){
    if(typeof moveOverlay==='undefined'||!moveOverlay)return;
    delete moveOverlay.dataset.v2516Cancel;
    delete moveOverlay.dataset.v25MoveStop;
    if(typeof closeMoveOverlay==='function')closeMoveOverlay();
    else {moveOverlay.classList.remove('show');moveOverlay.innerHTML='';}
  }
  function cancelCurrentRecord(){
    closeCancelOverlay();
    try{if(window.YamoneMovement&&typeof YamoneMovement.cancel==='function')YamoneMovement.cancel();}catch(e){}
    clearInterval(cancelPoll);let tries=0;
    cancelPoll=setInterval(()=>{
      const s=movementState();
      if(!s||!s.recording||++tries>40){
        clearInterval(cancelPoll);cancelPoll=0;
        if(typeof view!=='undefined')view='main';
        if(typeof tab!=='undefined')tab='activity';
        if(typeof moveState!=='undefined')moveState='ready';
        if(typeof setFocusMode==='function')setFocusMode(false);
        if(typeof syncTabs==='function')syncTabs();
        if(typeof render==='function')render();
        if(typeof say==='function')say('운동 기록을 취소했어요.');
      }
    },100);
  }

  function decorateLive(){
    if(typeof view==='undefined'||view!=='move'||typeof moveState==='undefined'||!['recording','paused'].includes(moveState))return;
    const primary=screen.querySelector('.v2508-real-live .move-primary');if(!primary)return;
    const head=primary.querySelector('.move-primary-head');
    if(head){
      let gps=head.querySelector('.v2516-inline-gps');
      if(!gps){gps=document.createElement('span');gps.className='v2516-inline-gps';head.appendChild(gps);}
      const g=gpsLabel(movementState());gps.textContent=g.text;gps.classList.remove('good','fair','lost');gps.classList.add(g.tone);
    }
    const dock=screen.querySelector('.v2508-real-live .v25-live-controls-under-route');
    if(dock){
      const controls=[...dock.querySelectorAll('.move-control')];
      const third=controls[2];
      if(third&&third.id!=='v2516RecordCancel'){
        third.id='v2516RecordCancel';third.classList.add('v2516-record-cancel');third.type='button';
        third.innerHTML=`<img src="${A}stop.png" alt=""><b>기록 취소</b><small>저장하지 않음</small>`;
        third.onclick=openCancelConfirm;
      }
    }
  }

  function apply(){scheduled=false;decorateLive();}
  function schedule(){if(scheduled)return;scheduled=true;requestAnimationFrame(apply);}
  new MutationObserver(schedule).observe(screen,{childList:true,subtree:true});
  setInterval(()=>{
    if(typeof view==='undefined'||view!=='move'||typeof moveState==='undefined'||!['recording','paused'].includes(moveState))return;
    decorateLive();
  },1000);
  schedule();
})();
