(function(){
  'use strict';
  const V='0.24.01';
  let enhanceQueued=false;

  function forceMintBase(){
    if(document.body.dataset.theme!=='mint') document.body.dataset.theme='mint';
  }

  function replaceTopTitle(tabName,file,alt){
    if(typeof tab==='undefined' || tab!==tabName || typeof view==='undefined' || view!=='main')return;
    const h=screen.querySelector(':scope > h1.title');
    if(!h)return;
    const wrap=document.createElement('div');
    wrap.className='v24-page-title-wrap';
    wrap.innerHTML='<img class="v24-page-title" src="assets/'+file+'" alt="'+alt+'">';
    h.replaceWith(wrap);
  }

  function updateVersionText(){
    document.title='야모네 · v'+V;
    const home=screen.querySelector('.home-version');
    if(home)home.textContent='Version '+V;
    screen.querySelectorAll('.setting small,.app-info-card small').forEach(el=>{
      if(/^Version\s/.test(el.textContent.trim()))el.textContent='Version '+V;
    });
  }

  function removeThemeChooser(){
    if(typeof tab==='undefined' || tab!=='settings' || typeof view==='undefined' || view!=='main')return;
    const grid=screen.querySelector('.theme-grid');
    if(!grid)return;
    const label=grid.previousElementSibling;
    if(label && label.classList.contains('section') && label.textContent.trim()==='테마')label.remove();
    grid.remove();
  }

  function labelOf(card){
    const s=card.querySelector('span');
    return s?s.textContent.trim():'';
  }

  function decorateRecordLists(){
    screen.classList.remove('v24-move-record','v24-snow-record');
    if(typeof view==='undefined' || view!=='record-detail' || typeof recordDetailId==='undefined' || !recordDetailId)return;
    if(typeof recordSamples==='undefined')return;
    const r=recordSamples[recordDetailId];
    if(!r)return;
    const first=screen.querySelector('.detail-stat-grid');
    if(r.kind==='move'){
      screen.classList.add('v24-move-record');
      if(first)first.classList.add('v24-list-grid');
      return;
    }
    if(r.kind!=='snow')return;
    screen.classList.add('v24-snow-record');
    if(first){
      [...first.children].forEach(card=>{
        if(['거리','칼로리','누적 상승'].includes(labelOf(card)))card.remove();
      });
      first.classList.add('v24-list-grid');
    }
    const extra=screen.querySelector('.snow-extra-grid');
    if(extra){
      [...extra.children].forEach(card=>{
        const label=labelOf(card);
        if(['전체 이동 거리','총 하강고도','평균 활주속도','최고 속도'].includes(label))card.remove();
      });
      extra.classList.add('v24-list-grid');
    }
  }

  function decorateSummaryLists(){
    const moveGrid=screen.querySelector('.move-summary-grid');
    if(moveGrid)moveGrid.classList.add('v24-move-summary-list');
    const snowGrid=screen.querySelector('.snow-summary-grid');
    if(snowGrid)snowGrid.classList.add('v24-snow-summary-list');
  }

  function updateAlarmHint(){
    const caption=screen.querySelector('.wheel-time-caption');
    if(caption)caption.textContent='위아래로 밀거나 선택된 숫자를 눌러 직접 입력하세요';
  }

  function enhanceScreen(){
    enhanceQueued=false;
    forceMintBase();
    replaceTopTitle('records','title-records-v02401.svg','기록');
    replaceTopTitle('settings','title-settings-v02401.svg','설정');
    updateVersionText();
    removeThemeChooser();
    decorateRecordLists();
    decorateSummaryLists();
    updateAlarmHint();
  }

  function queueEnhance(){
    if(enhanceQueued)return;
    enhanceQueued=true;
    requestAnimationFrame(enhanceScreen);
  }

  function closeOverlay(){
    if(typeof closeMoveOverlay==='function')closeMoveOverlay();
    else if(typeof moveOverlay!=='undefined' && moveOverlay){moveOverlay.classList.remove('show');moveOverlay.innerHTML='';}
  }

  function openDirectTimeInput(item){
    if(typeof alarmDraft==='undefined' || !alarmDraft || typeof moveOverlay==='undefined')return;
    const col=item.closest('.wheel-column');
    if(!col)return;
    const hour=col.id==='alarmHourWheel';
    const min=hour?1:0,max=hour?12:59;
    const label=hour?'시간':'분';
    moveOverlay.classList.add('show');
    moveOverlay.innerHTML='<div class="move-sheet">'
      +'<div class="move-sheet-handle"></div><h3>'+label+' 직접 입력</h3>'
      +'<p>숫자를 입력한 뒤 적용을 누르세요.</p>'
      +'<input id="v24DirectTime" class="v24-number-input" type="number" inputmode="numeric" min="'+min+'" max="'+max+'" value="'+Number(item.dataset.value)+'">'
      +'<div class="v24-number-hint">'+(hour?'1 ~ 12':'0 ~ 59')+'</div>'
      +'<div class="move-sheet-actions"><button id="v24TimeCancel" class="move-sheet-cancel">취소</button><button id="v24TimeApply" class="move-sheet-stop" style="background:var(--primary);color:#fff">적용</button></div></div>';
    const input=document.getElementById('v24DirectTime');
    document.getElementById('v24TimeCancel').onclick=closeOverlay;
    document.getElementById('v24TimeApply').onclick=()=>{
      let n=parseInt(input.value,10);
      if(!Number.isFinite(n))return;
      n=Math.max(min,Math.min(max,n));
      const period=alarmPeriodFrom24(alarmDraft.time);
      let h12=alarmHour12From24(alarmDraft.time);
      let minute=alarmDraft.time.split(':')[1];
      if(hour)h12=String(n).padStart(2,'0');
      else minute=String(n).padStart(2,'0');
      alarmDraft.time=alarmTo24(period,h12,minute);
      closeOverlay();
      renderAlarmEditor();
    };
    moveOverlay.onclick=e=>{if(e.target===moveOverlay)closeOverlay()};
    setTimeout(()=>{input.focus();input.select();},60);
  }

  screen.addEventListener('click',e=>{
    const item=e.target.closest('.wheel-item.selected');
    if(!item)return;
    e.preventDefault();
    e.stopImmediatePropagation();
    openDirectTimeInput(item);
  },true);

  function showExitConfirm(){
    if(typeof moveOverlay==='undefined' || !moveOverlay)return false;
    moveOverlay.classList.add('show');
    moveOverlay.innerHTML='<div class="move-sheet v24-exit-sheet"><div class="move-sheet-handle"></div>'
      +'<h3>야모네를 종료할까요?</h3><p>앱을 종료하거나 계속 사용할 수 있습니다.</p>'
      +'<div class="move-sheet-actions"><button id="v24ExitCancel" class="move-sheet-cancel">취소</button><button id="v24ExitConfirm" class="move-sheet-stop">종료</button></div></div>';
    document.getElementById('v24ExitCancel').onclick=closeOverlay;
    document.getElementById('v24ExitConfirm').onclick=()=>{
      try{if(window.YamoneNative && YamoneNative.finishApp)YamoneNative.finishApp();}catch(e){}
    };
    moveOverlay.onclick=e=>{if(e.target===moveOverlay)closeOverlay()};
    return true;
  }

  const previousBack=window.yamoneAndroidBack;
  window.yamoneAndroidBack=function(){
    try{
      if(previousBack && previousBack())return true;
      return showExitConfirm();
    }catch(e){return showExitConfirm();}
  };

  new MutationObserver(queueEnhance).observe(screen,{childList:true,subtree:true});
  forceMintBase();
  queueEnhance();
})();
