/* v0.25.05: approved full-height Summary View for walking/running/bike. */
(function(){
  'use strict';
  const A='assets/';

  function activityType(){ return moveMode==='auto' ? moveSubtype : moveMode; }
  function activityLabel(t){ return t==='walk'?'걷기':t==='run'?'달리기':'자전거'; }
  function titleAsset(t){ return `title-summary-${t}-v02503.svg`; }

  const data={
    walk:{distance:['5.24','km'],time:['00:42:18',''],recent:["11'18″",'/km'],overall:["10'52″",'/km']},
    run:{distance:['5.24','km'],time:['00:31:06',''],recent:["5'41″",'/km'],overall:["5'56″",'/km']},
    bike:{distance:['82.7','km'],time:['03:29','h:min'],recent:['25.8','km/h'],overall:['23.7','km/h'],max:['52.0','km/h']}
  };

  function card(icon,label,pair){
    const value=pair[0],unit=pair[1]||'';
    return `<section class="card v25-summary-card"><img src="${A}${icon}" alt=""><span class="v25-summary-label">${label}</span><div class="v25-summary-value-wrap"><strong><span class="v25-summary-number">${value}</span>${unit?`<span class="v25-summary-unit">${unit}</span>`:''}</strong></div></section>`;
  }

  function clearSummaryClasses(){
    screen.classList.remove('v25-summary-screen','v25-summary-rows-4','v25-summary-rows-5');
    screen.style.removeProperty('--v25-summary-rows');
  }

  function renderSummaryView(){
    setFocusMode(true);
    const t=activityType(),d=data[t];
    const rows=t==='bike'?5:4;
    clearSummaryClasses();
    screen.classList.add('v25-summary-screen',`v25-summary-rows-${rows}`);
    screen.style.setProperty('--v25-summary-rows',String(rows));
    const cards=t==='bike'
      ? card('distance.png','거리',d.distance)+card('time.png','시간',d.time)+card('speed.png','최근 1km 평균속도',d.recent)+card('speed.png','전체 평균속도',d.overall)+card('speed.png','최대속도',d.max)
      : card('distance.png','거리',d.distance)+card('time.png','시간',d.time)+card('pace.png','최근 1km 페이스',d.recent)+card('pace.png','전체 페이스',d.overall);
    screen.innerHTML=`<div class="move-sticky"><div class="move-header-row"><button id="moveSummaryBack" class="move-back-asset-btn"><img class="move-back-asset" src="${A}back-v02402.png" alt="뒤로가기"></button><img class="v25-summary-title-asset" src="${A}${titleAsset(t)}" alt="요약보기 (${activityLabel(t)})"></div></div><section class="v25-summary-body"><div class="v25-summary-grid">${cards}</div></section>`;
  }

  window.movePrimary=function(){
    const t=activityType();
    const button=moveState==='recording' ? `<button type="button" class="v25-summary-open" data-v25-summary-open aria-label="요약보기"><img src="${A}summary-view-button-v02503.svg" alt="요약보기"></button>` : '';
    if(t==='bike') return `<div class="move-primary"><div class="move-primary-head"><img src="${A}speed.png"><b>현재 속도</b></div><div class="v25-live-primary-row"><div class="move-primary-value">21.8 km/h</div>${button}</div><div class="move-primary-sub">평균 속도 18.6 km/h · 자전거 감지 중</div></div>`;
    const value=t==='walk'?"11'20″/km":"5'48″/km";
    const avg=t==='walk'?"10'52″/km":"5'56″/km";
    return `<div class="move-primary"><div class="move-primary-head"><img src="${A}pace.png"><b>현재 페이스</b></div><div class="v25-live-primary-row"><div class="move-primary-value">${value}</div>${button}</div><div class="move-primary-sub">평균 페이스 ${avg} · ${activityLabel(t)} 감지 중</div></div>`;
  };

  const originalRender=window.render;
  window.render=function(){
    if(view==='move' && moveState==='glance'){ renderSummaryView(); return; }
    clearSummaryClasses();
    return originalRender.apply(this,arguments);
  };

  document.addEventListener('click',e=>{
    const button=e.target.closest('[data-v25-summary-open]');
    if(!button || !screen.contains(button))return;
    e.preventDefault();
    e.stopPropagation();
    moveState='glance';
    render();
  });
})();
