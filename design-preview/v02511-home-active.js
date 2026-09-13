/* v0.25.11: show the current real movement activity on Home and reopen it. */
(function(){
  'use strict';
  const A='assets/';

  function state(){
    try{
      const b=window.YamoneMovement;
      if(!b||typeof b.getState!=='function')return {recording:false};
      const raw=b.getState();
      return typeof raw==='string'?JSON.parse(raw):raw;
    }catch(e){return {recording:false};}
  }
  function typeOf(s){
    if(s.activityType==='cycling')return 'bike';
    if(s.activityType==='running')return 'run';
    if(s.activityType==='walkrun')return s.autoMotionMode==='running'?'run':'walk';
    return 'walk';
  }
  function label(t){return t==='bike'?'자전거':t==='run'?'달리기':'걷기';}
  function icon(t){return t==='bike'?'bike.png':t==='run'?'run.png':'walk.png';}
  function minuteText(ms){
    const m=Math.max(0,Math.floor((Number(ms)||0)/60000));
    return m<1?'1분 미만':`${m}분`;
  }
  function distanceText(m){
    const km=Math.max(0,Number(m)||0)/1000;
    return `${km.toFixed(km<10?2:1)} km`;
  }
  function onHome(){
    return typeof view!=='undefined'&&typeof tab!=='undefined'&&view==='main'&&tab==='home';
  }
  function restoreEmpty(show){
    const empty=screen&&screen.querySelector?screen.querySelector('.empty'):null;
    if(empty)empty.style.display=show?'':'none';
  }
  function removeCard(){
    const card=document.getElementById('homeMovementRunning');
    if(card)card.remove();
    const section=screen&&screen.querySelector?screen.querySelector('.running-section'):null;
    if(section&&!section.querySelector('.running-card'))section.remove();
    if(onHome()&&!document.querySelector('.running-section'))restoreEmpty(true);
  }
  function ensureSection(){
    let section=screen.querySelector('.running-section');
    if(section)return section;
    section=document.createElement('div');
    section.className='running-section';
    section.innerHTML='<div class="running-title">현재 진행 중</div>';
    const empty=screen.querySelector('.empty');
    if(empty)screen.insertBefore(section,empty);
    else{
      const stats=screen.querySelector('.stats');
      if(stats&&stats.nextSibling)screen.insertBefore(section,stats.nextSibling);
      else screen.appendChild(section);
    }
    return section;
  }
  function openMovement(){
    const s=state();
    if(!s.recording){sync();return;}
    view='move';
    moveState=s.paused?'paused':'recording';
    if(typeof setFocusMode==='function')setFocusMode(true);
    render();
  }
  function sync(){
    if(!onHome())return;
    const s=state();
    if(!s.recording){removeCard();return;}
    restoreEmpty(false);
    const section=ensureSection();
    let card=document.getElementById('homeMovementRunning');
    if(!card){
      card=document.createElement('div');
      card.className='card running-card';
      card.id='homeMovementRunning';
      card.innerHTML='<img alt=""><div class="running-card-copy"><b></b><small></small></div><span class="running-card-pill">열기</span>';
      card.onclick=openMovement;
      section.appendChild(card);
    }else if(card.parentElement!==section){section.appendChild(card);}
    const t=typeOf(s);
    const img=card.querySelector('img');
    const title=card.querySelector('b');
    const sub=card.querySelector('small');
    const src=A+icon(t);
    const titleText=`${label(t)} ${s.paused?'일시정지':'기록 중'}`;
    const prefix=s.activityType==='walkrun'?'자동 전환 · ':'';
    const subText=`${prefix}${minuteText(s.elapsedMs)} · ${distanceText(s.distanceM)}`;
    if(img&&img.getAttribute('src')!==src)img.setAttribute('src',src);
    if(title&&title.textContent!==titleText)title.textContent=titleText;
    if(sub&&sub.textContent!==subText)sub.textContent=subText;
  }

  const original=window.renderHome;
  if(typeof original==='function'){
    window.renderHome=function(){
      const result=original.apply(this,arguments);
      requestAnimationFrame(sync);
      return result;
    };
  }
  setInterval(sync,1000);
  window.v02511SyncHomeActive=sync;
})();
