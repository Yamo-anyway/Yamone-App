(function(){
  function notifyTheme(){
    try{
      if(window.YamoneNative && YamoneNative.setTheme){
        YamoneNative.setTheme(document.body.dataset.theme || 'mint');
      }
    }catch(e){}
  }
  new MutationObserver(notifyTheme).observe(document.body,{attributes:true,attributeFilter:['data-theme']});
  notifyTheme();

  window.yamoneAndroidBack=function(){
    try{
      if(typeof moveOverlay!=='undefined' && moveOverlay && moveOverlay.classList.contains('show')){
        if(typeof closeMoveOverlay==='function') closeMoveOverlay();
        else { moveOverlay.classList.remove('show'); moveOverlay.innerHTML=''; }
        return true;
      }
      const sheet=document.getElementById('alarmSheetBackdrop');
      if(sheet && sheet.classList.contains('show')){
        sheet.classList.remove('show');
        return true;
      }
      if(typeof alarmView!=='undefined' && alarmView!=='list'){
        if(typeof closeRingingAlarm==='function' && alarmView==='ringing') closeRingingAlarm();
        else if(typeof closeAlarmEditor==='function') closeAlarmEditor();
        return true;
      }
      if(typeof settingPage!=='undefined' && settingPage){
        settingPage=null; view='main'; if(typeof setFocusMode==='function')setFocusMode(false); render(); return true;
      }
      if(typeof snowRunDetailOpen!=='undefined' && snowRunDetailOpen){
        snowRunDetailOpen=false; if(typeof renderRecordDetail==='function') renderRecordDetail(); return true;
      }
      if(typeof recordDetailId!=='undefined' && recordDetailId){
        recordDetailId=null; view='main'; render(); return true;
      }
      if(typeof view!=='undefined' && view!=='main'){
        if(typeof stopSleepTimer==='function')stopSleepTimer();
        if(typeof stopLocationTimers==='function')stopLocationTimers(true);
        view='main';
        if(typeof setFocusMode==='function')setFocusMode(false);
        render(); return true;
      }
      if(typeof tab!=='undefined' && tab!=='home'){
        tab='home'; if(typeof syncTabs==='function')syncTabs(); render(); return true;
      }
    }catch(e){}
    return false;
  };
})();
