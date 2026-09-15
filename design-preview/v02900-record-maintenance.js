/* v0.29.00: maintain completed local movement records. */
(function(){
  'use strict';
  let selectedSessionId='';
  let queued=false;

  function parse(raw,fallback){try{return typeof raw==='string'?JSON.parse(raw):raw;}catch(e){return fallback;}}
  function recordsCall(name,args,fallback){
    try{
      if(!window.YamoneRecords||typeof YamoneRecords[name]!=='function')return fallback;
      return parse(YamoneRecords[name].apply(YamoneRecords,args||[]),fallback);
    }catch(e){return fallback;}
  }
  function movementCall(name,args,fallback){
    try{
      if(!window.YamoneMovement||typeof YamoneMovement[name]!=='function')return fallback;
      return parse(YamoneMovement[name].apply(YamoneMovement,args||[]),fallback);
    }catch(e){return fallback;}
  }
  function toast(text){
    const old=document.querySelector('.v2900-toast');if(old)old.remove();
    const el=document.createElement('div');el.className='v2900-toast';el.textContent=text;document.body.appendChild(el);
    setTimeout(()=>{if(el.isConnected)el.remove();},1900);
  }
  function closeModal(){const el=document.getElementById('v2900Modal');if(el)el.remove();}
  function returnToRecords(){
    selectedSessionId='';
    if(typeof view!=='undefined')view='main';
    if(typeof tab!=='undefined')tab='records';
    if(typeof recordFilter!=='undefined')recordFilter='move';
    if(typeof moveState!=='undefined')moveState='ready';
    if(typeof setFocusMode==='function')setFocusMode(false);
    if(typeof syncTabs==='function')syncTabs();
    if(typeof render==='function')render();
  }
  function modal(title,message,actionText,actionClass,onAction){
    closeModal();
    const root=document.createElement('div');root.id='v2900Modal';root.className='v2900-modal-backdrop';
    root.innerHTML=`<div class="v2900-modal" role="dialog" aria-modal="true"><h3>${title}</h3><p>${message}</p><div class="v2900-modal-actions"><button type="button" class="v2900-modal-cancel">취소</button><button type="button" class="${actionClass}">${actionText}</button></div></div>`;
    document.body.appendChild(root);
    root.querySelector('.v2900-modal-cancel').onclick=closeModal;
    root.onclick=e=>{if(e.target===root)closeModal();};
    root.querySelector('.'+actionClass).onclick=onAction;
  }
  function confirmDelete(){
    modal('이 기록을 삭제할까요?','휴대폰에 저장된 이 활동의 경로와 기록 데이터가 삭제됩니다. 삭제 후에는 되돌릴 수 없습니다.','삭제','v2900-modal-danger',()=>{
      const id=selectedSessionId;
      if(!id){closeModal();toast('삭제할 기록을 찾지 못했습니다.');return;}
      const result=recordsCall('deleteSavedRecord',[id],{ok:false,status:'bridge_unavailable'});
      closeModal();
      if(result&&result.ok){returnToRecords();toast('기록을 삭제했습니다.');}
      else toast('기록을 삭제하지 못했습니다.');
    });
  }
  function trimError(status){
    if(status==='too_short')return '선택한 시간보다 기록이 짧아 적용할 수 없습니다.';
    if(status==='already_uploaded')return '이미 전송한 기록은 소급 수정할 수 없습니다.';
    if(status==='trim_finalized')return '이미 종료시간 보정을 확정한 기록입니다.';
    if(status==='insufficient_route')return 'GPS 경로가 부족해 안전하게 보정할 수 없습니다.';
    return '종료시간 보정을 적용하지 못했습니다.';
  }
  function confirmTrim(sessionId,minutes){
    modal(`마지막 ${minutes}분을 삭제할까요?`,`종료 시점을 ${minutes}분 앞당기고 그 이후 GPS 경로를 삭제합니다. 거리·시간·평균값도 다시 계산됩니다. 한 번 확정하면 추가 소급 수정은 할 수 없습니다.`,'적용','v2900-modal-apply',()=>{
      const result=recordsCall('trimSavedRecordEnd',[sessionId,minutes],{ok:false,status:'bridge_unavailable'});
      closeModal();
      if(result&&result.ok){
        returnToRecords();
        toast(`마지막 ${minutes}분을 삭제해 기록을 다시 저장했습니다.`);
      }else toast(trimError(result&&result.status));
    });
  }
  function enhanceDetail(){
    if(typeof view==='undefined'||view!=='saved-record-detail'||!selectedSessionId)return;
    if(document.getElementById('v2900RecordActions'))return;
    const note=screen.querySelector('.v2513-real-note');if(!note)return;
    const actions=document.createElement('div');actions.id='v2900RecordActions';actions.className='v2900-record-actions';
    actions.innerHTML='<button type="button" class="v2900-record-delete">이 기록 삭제</button>';
    note.insertAdjacentElement('afterend',actions);
    actions.querySelector('.v2900-record-delete').onclick=confirmDelete;
  }
  function enhanceEndSummary(){
    if(!screen.classList.contains('v25-move-end-summary')||document.getElementById('v2900TrimCard'))return;
    const savebar=screen.querySelector('.v25-move-summary-savebar');if(!savebar)return;
    const latest=movementCall('getLatestRecord',[40],null);
    if(!latest||!latest.found||!latest.sessionId||!latest.meta||latest.meta.status!=='complete')return;
    const meta=latest.meta,duration=Math.max(0,Number(meta.durationMs)||0),sessionId=String(latest.sessionId);
    const section=document.createElement('div');section.className='move-section v2900-trim-section';section.textContent='종료시간 보정';
    const card=document.createElement('div');card.id='v2900TrimCard';card.className='card v2900-trim-card';
    if(meta.tailTrimFinalized){
      card.innerHTML=`<div class="v2900-trim-copy"><b>뒤쪽 기록 삭제</b><small>종료시간 보정이 이미 확정되었습니다.</small></div><div class="v2900-trim-done">${Math.max(0,Number(meta.tailTrimMinutes)||0)}분 소급 적용 완료</div>`;
    }else if(meta.uploaded){
      card.innerHTML='<div class="v2900-trim-copy"><b>뒤쪽 기록 삭제</b><small>이미 전송된 기록은 서버 기록과 달라질 수 있어 소급 수정하지 않습니다.</small></div>';
    }else{
      card.innerHTML=`<div class="v2900-trim-copy"><b>뒤쪽 기록 삭제</b><small>운동 종료를 늦게 눌렀다면 실제 종료 시점만큼 뒤에서 잘라낼 수 있습니다.</small></div><div class="v2900-trim-buttons"><button type="button" data-v2900-trim="1" ${duration<=60000?'disabled':''}>1분</button><button type="button" data-v2900-trim="3" ${duration<=180000?'disabled':''}>3분</button><button type="button" data-v2900-trim="5" ${duration<=300000?'disabled':''}>5분</button></div><p class="v2900-trim-note">선택 후 확인하면 GPS 끝 구간과 관련 수치가 다시 계산되며, 이후에는 다시 소급 수정할 수 없습니다.</p>`;
      card.querySelectorAll('[data-v2900-trim]').forEach(button=>button.onclick=()=>confirmTrim(sessionId,Number(button.dataset.v2900Trim)));
    }
    savebar.parentNode.insertBefore(section,savebar);
    savebar.parentNode.insertBefore(card,savebar);
  }
  function enhance(){queued=false;enhanceDetail();enhanceEndSummary();}
  function schedule(){if(queued)return;queued=true;requestAnimationFrame(enhance);}

  document.addEventListener('click',e=>{
    const record=e.target.closest&&e.target.closest('[data-v2513-saved]');
    if(record)selectedSessionId=String(record.dataset.v2513Saved||'');
    if(e.target.closest&&e.target.closest('#v2513SavedBack')){selectedSessionId='';closeModal();}
  },true);
  new MutationObserver(schedule).observe(screen,{childList:true,subtree:true});
  schedule();
})();
