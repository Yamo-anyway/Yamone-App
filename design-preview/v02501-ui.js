(function(){
  'use strict';
  const V='0.25.02';
  const ASSET='assets/';
  let enhanceQueued=false;

  function forceMintBase(){
    if(document.body.dataset.theme!=='mint') document.body.dataset.theme='mint';
  }

  function replaceTextTitle(tabName,file,alt){
    if(typeof tab==='undefined' || tab!==tabName || typeof view==='undefined' || view!=='main')return;
    const h=screen.querySelector(':scope > h1.title');
    if(!h)return;
    const wrap=document.createElement('div');
    wrap.className='v24-page-title-wrap';
    wrap.innerHTML='<img class="v24-page-title" src="'+ASSET+file+'" alt="'+alt+'">';
    h.replaceWith(wrap);
  }

  function replaceTopAssets(){
    if(typeof tab!=='undefined' && tab==='activity' && typeof view!=='undefined' && view==='main'){
      const img=screen.querySelector('.title-asset.title-activity');
      if(img)img.src=ASSET+'title-activity-v02402.png';
    }
    replaceTextTitle('records','title-records-v02402.png','기록');
    replaceTextTitle('settings','title-settings-v02402.png','설정');
    screen.querySelectorAll('.alarm-title-asset').forEach(img=>img.src=ASSET+'title-alarm-v02402.png');
  }

  function replaceBackAssets(){
    screen.querySelectorAll('.move-back-asset-btn img,.setting-back img').forEach(img=>{
      img.src=ASSET+'back-v02402.png';
      img.alt='뒤로가기';
    });
  }

  function updateVersionText(){
    document.title='야모네 · v'+V;
    const home=screen.querySelector('.home-version');
    if(home && home.textContent!=='Version '+V)home.textContent='Version '+V;
    screen.querySelectorAll('.setting small,.app-info-card small').forEach(el=>{
      if(/^Version\s/.test(el.textContent.trim()) && el.textContent!=='Version '+V)el.textContent='Version '+V;
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
    if(caption && caption.textContent!=='위아래로 밀거나 선택된 시·분을 눌러 직접 입력하세요')caption.textContent='위아래로 밀거나 선택된 시·분을 눌러 직접 입력하세요';
  }

  function enhanceScreen(){
    enhanceQueued=false;
    forceMintBase();
    replaceTopAssets();
    replaceBackAssets();
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

  function commitInlineTime(input,hour){
    if(!input || input.dataset.committed==='1')return;
    input.dataset.committed='1';
    let n=parseInt(input.value,10);
    const min=hour?1:0;
    const max=hour?12:59;
    if(!Number.isFinite(n)){
      renderAlarmEditor();
      return;
    }
    n=Math.max(min,Math.min(max,n));
    if(typeof alarmDraft==='undefined' || !alarmDraft){
      renderAlarmEditor();
      return;
    }
    const period=alarmPeriodFrom24(alarmDraft.time);
    let h12=alarmHour12From24(alarmDraft.time);
    let minute=alarmDraft.time.split(':')[1];
    if(hour)h12=String(n).padStart(2,'0');
    else minute=String(n).padStart(2,'0');
    alarmDraft.time=alarmTo24(period,h12,minute);
    renderAlarmEditor();
  }

  function startInlineTimeInput(item){
    if(typeof alarmDraft==='undefined' || !alarmDraft)return;
    const col=item.closest('.wheel-column');
    if(!col)return;
    const hour=col.id==='alarmHourWheel';
    const min=hour?1:0;
    const max=hour?12:59;
    const current=String(Number(item.dataset.value));
    item.classList.add('v24-inline-editing');
    item.innerHTML='<input class="v24-inline-time-input" type="number" inputmode="numeric" enterkeyhint="done" min="'+min+'" max="'+max+'" value="'+current+'" aria-label="'+(hour?'시간':'분')+' 직접 입력">';
    const input=item.querySelector('.v24-inline-time-input');
    input.addEventListener('click',e=>e.stopPropagation());
    input.addEventListener('keydown',e=>{
      if(e.key==='Enter'){
        e.preventDefault();
        input.blur();
      }else if(e.key==='Escape'){
        e.preventDefault();
        input.dataset.committed='1';
        renderAlarmEditor();
      }
    });
    input.addEventListener('blur',()=>commitInlineTime(input,hour),{once:true});
    requestAnimationFrame(()=>{
      input.focus();
      try{input.select();}catch(e){}
    });
  }

  screen.addEventListener('click',e=>{
    if(e.target.closest('.v24-inline-time-input'))return;
    const item=e.target.closest('.wheel-item.selected');
    if(!item)return;
    e.preventDefault();
    e.stopImmediatePropagation();
    startInlineTimeInput(item);
  },true);

  new MutationObserver(queueEnhance).observe(screen,{childList:true,subtree:true});
  forceMintBase();
  queueEnhance();
})();
