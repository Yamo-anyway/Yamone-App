/* v0.25.12: notification taps return to the renewed live movement screen. */
(function(){
  'use strict';

  function state(){
    try{
      const bridge=window.YamoneMovement;
      if(!bridge||typeof bridge.getState!=='function')return null;
      const raw=bridge.getState();
      return typeof raw==='string'?JSON.parse(raw):raw;
    }catch(e){return null;}
  }

  function syncMovementMode(s){
    const type=(s&&s.activityType)||'walking';
    if(type==='cycling'){
      moveMode='bike';moveSubtype='bike';
    }else if(type==='running'){
      moveMode='run';moveSubtype='run';
    }else if(type==='walkrun'){
      moveMode='auto';moveSubtype=s&&s.autoMotionMode==='running'?'run':'walk';
    }else{
      moveMode='walk';moveSubtype='walk';
    }
  }

  window.yamoneOpenActiveMovement=function(openStopConfirm){
    const s=state();
    if(!s||!s.recording)return false;
    try{
      if(typeof closeMoveOverlay==='function')closeMoveOverlay();
      syncMovementMode(s);
      view='move';
      moveState=s.paused?'paused':'recording';
      if(typeof setFocusMode==='function')setFocusMode(true);
      if(typeof syncTabs==='function')syncTabs();
      render();
      if(openStopConfirm){
        requestAnimationFrame(()=>requestAnimationFrame(()=>{
          if(view==='move'&&['recording','paused'].includes(moveState)
              &&typeof openMoveStop==='function')openMoveStop();
        }));
      }
      return true;
    }catch(e){
      return false;
    }
  };
})();
