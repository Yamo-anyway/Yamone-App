/* v0.27.01 · Real alarm list/editor bridge. */
(function(){
  'use strict';

  function call(name,args,fallback){
    try{
      const b=window.YamoneAlarm;
      if(!b||typeof b[name]!=='function')return fallback;
      const raw=b[name].apply(b,args||[]);
      return typeof raw==='string'?JSON.parse(raw):raw;
    }catch(e){return fallback;}
  }
  function saySafe(text){try{if(typeof say==='function')say(text);}catch(e){}}
  function syncNativeAlarms(){
    const data=call('listAlarms',[],{alarms:[]});
    if(!data||!Array.isArray(data.alarms))return false;
    alarms=data.alarms.map(a=>({
      id:Number(a.id),time:String(a.time||'07:00'),label:String(a.label||'알람'),
      days:Array.isArray(a.days)?a.days.slice():[],enabled:!!a.enabled,
      method:a.method==='tts'?'tts':'sound',sound:String(a.sound||'Morning Bell'),
      ringtoneId:String(a.ringtoneId||'basic'),ttsText:String(a.ttsText||'알람 시간입니다.'),
      voiceStyle:String(a.voiceStyle||'FEMALE'),gradual:!!a.gradual,vibration:!!a.vibration,
      snoozeMin:Number(a.snoozeMin||10),snoozeCount:Number(a.snoozeCount||3),
      shakeEnabled:!!a.shakeEnabled,shakeCount:Number(a.shakeCount||3),
      shakeStrength:String(a.shakeStrength||'약하게'),skipNext:!!a.skipNext,
      specificDate:String(a.specificDate||''),nextTriggerMs:Number(a.nextTriggerMs||0),nextText:String(a.nextText||'예약 없음')
    }));
    return true;
  }
  function nativePermissions(){return call('getPermissionState',[],{exact:true,fullScreen:true,notifications:true});}

  const originalNextAlarmText=nextAlarmText;
  nextAlarmText=function(a){
    if(!a||!a.enabled)return '꺼짐';
    const next=a.nextText||originalNextAlarmText(a);
    return a.skipNext?'다음 1회 건너뜀 · '+next:next;
  };
  activeAlarmForNext=function(){
    return alarms.filter(a=>a.enabled&&Number(a.nextTriggerMs)>0)
      .sort((a,b)=>Number(a.nextTriggerMs)-Number(b.nextTriggerMs))[0]||null;
  };

  const originalRingtonePickerMarkup=ringtonePickerMarkup;
  ringtonePickerMarkup=function(a){
    return originalRingtonePickerMarkup(a).replace(
      '현재는 선택/미리듣기 구조를 먼저 적용한 목업입니다. 실제 야모네 전용 알람음 5종은 이후 직접 제작해 이 자리에 연결할 예정입니다.',
      '야모네 전용 알람음 5종입니다. 각 미리듣기는 실제 알람에 사용되는 동일한 소리를 재생합니다.'
    );
  };

  const originalRenderAlarm=renderAlarm;
  renderAlarm=function(){
    if(alarmView==='list')syncNativeAlarms();
    originalRenderAlarm();
    if(alarmView==='list')decorateRealAlarmList();
  };

  function decorateRealAlarmList(){
    const p=nativePermissions();
    const head=screen.querySelector('.alarm-page-head');
    if(head){
      const rows=[];
      if(!p.exact)rows.push(permissionRow('정확한 알람 권한','설정한 시각에 정확히 울리기 위해 필요합니다.','정확한 알람','exact'));
      if(!p.fullScreen)rows.push(permissionRow('잠금화면 전체화면','화면이 잠겨 있을 때 알람 화면을 바로 표시합니다.','전체화면','full'));
      if(!p.notifications)rows.push(permissionRow('알림 권한','알람이 울릴 때 알림과 종료 동작에 필요합니다.','알림 허용','notification'));
      if(rows.length){
        const box=document.createElement('div');box.className='v02701-alarm-permissions';box.innerHTML=rows.join('');head.insertAdjacentElement('afterend',box);
        box.querySelectorAll('[data-v02701-permission]').forEach(btn=>btn.onclick=()=>{
          const k=btn.dataset.v02701Permission;
          if(k==='exact')call('requestExactAlarmAccess');
          else if(k==='full')call('requestFullScreenAlarmAccess');
          else call('requestNotificationPermission');
        });
      }else{
        call('rescheduleEnabled');
      }
    }
    document.querySelectorAll('[data-toggle]').forEach(btn=>btn.onclick=e=>{
      e.stopPropagation();
      const id=Number(btn.dataset.toggle);const a=alarms.find(x=>x.id===id);if(!a)return;
      const r=call('setEnabled',[id,!a.enabled],{ok:false});
      if(r.ok&&!r.scheduled&&r.exactPermission===false)saySafe('알람은 저장됐지만 정확한 알람 권한이 필요합니다.');
      renderAlarm();
    });
    document.querySelectorAll('[data-skip]').forEach(btn=>btn.onclick=e=>{
      e.stopPropagation();const r=call('toggleSkip',[Number(btn.dataset.skip)],{ok:false});
      if(!r.ok)saySafe('다음 알람을 건너뛰도록 설정하지 못했습니다.');
      renderAlarm();
    });
    const demo=document.getElementById('alarmDemo');if(demo)demo.textContent='알람 울림 화면 · 실제 소리 테스트';
  }
  function permissionRow(title,sub,button,key){
    return `<div class="card v02701-alarm-permission"><img src="assets/settings-permission.png" alt=""><div><b>${title}</b><small>${sub}</small></div><button data-v02701-permission="${key}">${button}</button></div>`;
  }

  saveAlarmV2=function(){
    if(!alarmDraft)return;
    const label=document.getElementById('alarmLabelV2');if(label)alarmDraft.label=label.value.trim()||'알람';
    const t=document.getElementById('alarmTtsText');if(t)alarmDraft.ttsText=t.value.trim()||'알람 시간입니다.';
    alarmDraft.id=editingAlarmId||null;
    const r=call('saveAlarm',[JSON.stringify(alarmDraft)],{ok:false});
    if(!r.ok){saySafe('알람을 저장하지 못했습니다.');return;}
    if(!r.scheduled&&r.exactPermission===false)saySafe('알람은 저장됐습니다. 정확한 알람 권한을 허용해주세요.');
    else saySafe('알람이 저장되었습니다.');
    ringtonePickerOpen=false;alarmView='list';editingAlarmId=null;alarmDraft=null;setFocusMode(false);renderAlarm();
  };

  deleteAlarmV2=function(){
    if(!editingAlarmId)return;
    showAlarmConfirm('알람을 삭제할까요?','이 알람 예약이 취소되고 목록에서 삭제됩니다.','삭제',()=>{
      const r=call('deleteAlarm',[Number(editingAlarmId)],{ok:false});
      if(!r.ok){saySafe('알람을 삭제하지 못했습니다.');return;}
      ringtonePickerOpen=false;alarmView='list';editingAlarmId=null;alarmDraft=null;setFocusMode(false);renderAlarm();saySafe('알람이 삭제되었습니다.');
    });
  };

  function showAlarmConfirm(title,message,okText,onOk){
    if(typeof moveOverlay==='undefined'||!moveOverlay){if(window.confirm(title))onOk();return;}
    moveOverlay.classList.add('show');
    moveOverlay.innerHTML=`<div class="move-sheet"><div class="move-sheet-handle"></div><h3>${title}</h3><p>${message}</p><div class="move-sheet-actions"><button id="v02701Cancel" class="move-sheet-cancel">취소</button><button id="v02701Ok" class="move-sheet-stop">${okText}</button></div></div>`;
    const close=()=>{moveOverlay.classList.remove('show');moveOverlay.innerHTML='';};
    document.getElementById('v02701Cancel').onclick=close;
    document.getElementById('v02701Ok').onclick=()=>{close();onOk();};
    moveOverlay.onclick=e=>{if(e.target===moveOverlay)close();};
  }

  const originalBindAlarmEditor=bindAlarmEditor;
  bindAlarmEditor=function(){
    originalBindAlarmEditor();
    const save=document.getElementById('alarmSaveV2');if(save)save.onclick=saveAlarmV2;
    const del=document.getElementById('alarmDeleteV2');if(del&&editingAlarmId)del.onclick=deleteAlarmV2;
    document.querySelectorAll('[data-ringtone-preview]').forEach(btn=>btn.onclick=e=>{
      e.stopPropagation();call('previewTone',[String(btn.dataset.ringtonePreview||'basic')]);
    });
    const ttsPreview=document.getElementById('ttsPreview');if(ttsPreview)ttsPreview.onclick=()=>{
      const input=document.getElementById('alarmTtsText');call('previewTts',[input&&input.value.trim()?input.value.trim():'알람 시간입니다.',alarmDraft&&alarmDraft.voiceStyle||'FEMALE']);
    };
    const ringPreview=document.getElementById('alarmRingPreview');if(ringPreview)ringPreview.onclick=()=>{
      const label=document.getElementById('alarmLabelV2');if(label)alarmDraft.label=label.value.trim()||'알람';
      const input=document.getElementById('alarmTtsText');if(input)alarmDraft.ttsText=input.value.trim()||'알람 시간입니다.';
      openRingingAlarm(null,true);
    };
  };

  const originalOpenRingingAlarm=openRingingAlarm;
  openRingingAlarm=function(id=null,useDraft=false){
    const a=useDraft?alarmDraft:alarms.find(x=>x.id===id);
    if(a){
      if(a.method==='tts')call('previewTts',[a.ttsText||'알람 시간입니다.',a.voiceStyle||'FEMALE']);
      else call('previewTone',[a.ringtoneId||'basic']);
    }
    originalOpenRingingAlarm(id,useDraft);
  };
  const originalCloseRingingAlarm=closeRingingAlarm;
  closeRingingAlarm=function(){call('stopPreview');originalCloseRingingAlarm();};

  const originalCloseAlarmEditor=closeAlarmEditor;
  closeAlarmEditor=function(){call('stopPreview');originalCloseAlarmEditor();};

  const originalRenderSettings=renderSettings;
  renderSettings=function(){
    originalRenderSettings();
    const alarmSetting=screen.querySelector('[data-setting="알람"]');if(alarmSetting)alarmSetting.remove();
  };
  const originalRenderSettingDetail=renderSettingDetail;
  renderSettingDetail=function(){
    if(typeof settingPage!=='undefined'&&settingPage==='알람'){
      settingPage=null;view='main';tab='settings';setFocusMode(false);syncTabs();render();return;
    }
    return originalRenderSettingDetail.apply(this,arguments);
  };

  window.yamoneOpenAlarmList=function(){
    call('stopPreview');
    if(typeof settingPage!=='undefined')settingPage=null;
    view='main';tab='alarm';alarmView='list';editingAlarmId=null;alarmDraft=null;ringingAlarmId=null;
    if(typeof setFocusMode==='function')setFocusMode(false);if(typeof syncTabs==='function')syncTabs();render();return true;
  };

  /* Refresh actual schedules after returning from Android permission settings. */
  document.addEventListener('visibilitychange',()=>{
    if(!document.hidden&&typeof tab!=='undefined'&&tab==='alarm'&&typeof alarmView!=='undefined'&&alarmView==='list'){
      const p=nativePermissions();if(p.exact)call('rescheduleEnabled');renderAlarm();
    }
  });

  syncNativeAlarms();
})();
