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
  function toast(text){
    const old=document.querySelector('.v2900-toast');if(old)old.remove();
    const el=document.createElement('div');el.className='v2900-toast';el.textContent=text;document.body.appendChild(el);
    setTimeout(()=>{if(el.isConnected)el.remove();},1800);
  }
  function closeModal(){const el=document.getElementById('v2900DeleteModal');if(el)el.remove();}
  function returnToRecords(){
    selectedSessionId='';
    if(typeof view!=='undefined')view='main';
    if(typeof tab!=='undefined')tab='records';
    if(typeof recordFilter!=='undefined')recordFilter='move';
    if(typeof setFocusMode==='function')setFocusMode(false);
    if(typeof syncTabs==='function')syncTabs();
    if(typeof render==='function')render();
  }
  function confirmDelete(){
    closeModal();
    const modal=document.createElement('div');modal.id='v2900DeleteModal';modal.className='v2900-modal-backdrop';
    modal.innerHTML=`<div class="v2900-modal" role="dialog" aria-modal="true" aria-labelledby="v2900DeleteTitle"><h3 id="v2900DeleteTitle">이 기록을 삭제할까요?</h3><p>휴대폰에 저장된 이 활동의 경로와 기록 데이터가 삭제됩니다. 삭제 후에는 되돌릴 수 없습니다.</p><div class="v2900-modal-actions"><button type="button" class="v2900-modal-cancel">취소</button><button type="button" class="v2900-modal-danger">삭제</button></div></div>`;
    document.body.appendChild(modal);
    modal.querySelector('.v2900-modal-cancel').onclick=closeModal;
    modal.onclick=e=>{if(e.target===modal)closeModal();};
    modal.querySelector('.v2900-modal-danger').onclick=()=>{
      const id=selectedSessionId;
      if(!id){closeModal();toast('삭제할 기록을 찾지 못했습니다.');return;}
      const result=recordsCall('deleteSavedRecord',[id],{ok:false,status:'bridge_unavailable'});
      closeModal();
      if(result&&result.ok){
        returnToRecords();
        toast('기록을 삭제했습니다.');
      }else{
        toast('기록을 삭제하지 못했습니다.');
      }
    };
  }
  function enhanceDetail(){
    queued=false;
    if(typeof view==='undefined'||view!=='saved-record-detail'||!selectedSessionId)return;
    if(document.getElementById('v2900RecordActions'))return;
    const note=screen.querySelector('.v2513-real-note');
    if(!note)return;
    const actions=document.createElement('div');actions.id='v2900RecordActions';actions.className='v2900-record-actions';
    actions.innerHTML='<button type="button" class="v2900-record-delete">이 기록 삭제</button>';
    note.insertAdjacentElement('afterend',actions);
    actions.querySelector('.v2900-record-delete').onclick=confirmDelete;
  }
  function schedule(){if(queued)return;queued=true;requestAnimationFrame(enhanceDetail);}

  document.addEventListener('click',e=>{
    const record=e.target.closest&&e.target.closest('[data-v2513-saved]');
    if(record)selectedSessionId=String(record.dataset.v2513Saved||'');
    if(e.target.closest&&e.target.closest('#v2513SavedBack')){selectedSessionId='';closeModal();}
  },true);
  new MutationObserver(schedule).observe(screen,{childList:true,subtree:true});
  schedule();
})();
