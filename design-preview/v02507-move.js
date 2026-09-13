/* v0.25.07: requested movement recording/stop/summary flow changes. */
(function(){
  'use strict';
  const A='assets/';
  let savedAtStop=false;

  const originalCloseMoveOverlay=window.closeMoveOverlay;
  if(typeof originalCloseMoveOverlay==='function'){
    window.closeMoveOverlay=function(){
      if(typeof moveOverlay!=='undefined' && moveOverlay)delete moveOverlay.dataset.v25MoveStop;
      return originalCloseMoveOverlay.apply(this,arguments);
    };
  }

  function currentType(){return moveMode==='auto'?moveSubtype:moveMode;}
  function currentLabel(t){return t==='walk'?'걷기':t==='run'?'달리기':'자전거';}

  window.moveLive=function(paused){
    setFocusMode(true);
    screen.classList.remove('v25-move-end-summary');
    if(!paused)savedAtStop=false;
    const t=currentType();
    const label=currentLabel(t);
    screen.innerHTML=`
      ${moveTop('이동 활동 기록',paused?'일시정지':'기록 중')}
      <section class="card move-live-card">
        <div class="move-live-head">
          <div class="move-live-left"><img src="${A}${t}.png"><div><h2>${label} ${paused?'일시정지':'기록 중'}</h2></div></div>
          <span class="move-auto-pill">${moveMode==='auto'?'자동 전환':'수동 시작'}</span>
        </div>
        ${movePrimary()}
        <div class="move-metrics">
          <div class="card move-metric"><img src="${A}time.png"><span>시간</span><strong>00:38:14</strong></div>
          <div class="card move-metric"><img src="${A}distance.png"><span>거리</span><strong>${t==='bike'?'12.4 km':'5.2 km'}</strong></div>
          <div class="card move-metric"><img src="${A}calorie.png"><span>칼로리</span><strong>${t==='bike'?'284':'418'} kcal</strong></div>
        </div>
        <div class="card move-map"><img src="${A}route.png"><div><b>운동 경로</b><p>실제 앱에서는 현재 위치와 이동 경로가 표시됩니다.</p></div></div>
        <div class="move-controls v25-live-controls-under-route">
          ${paused
            ? `<button id="moveResume" class="move-control resume"><img src="${A}resume.png"><b>다시 시작</b><small>기록 재개</small></button>`
            : `<button id="movePause" class="move-control pause"><img src="${A}pause.png"><b>일시정지</b><small>잠시 멈춤</small></button>`}
          <button id="moveStop" class="move-control stop"><img src="${A}stop.png"><b>운동 종료</b><small>요약 보기</small></button>
          <button class="move-control"><img src="${A}gps.png"><b>GPS 상태</b><small>양호</small></button>
        </div>
        <div class="card move-segments v25-live-segments-bottom">
          <h3>자동 구간 구분</h3>
          <div class="move-segment"><img src="${A}walk.png"><div><b>걷기</b><small>00:00 ~ 00:07</small></div><strong>0.52 km</strong></div>
          <div class="move-segment"><img src="${A}run.png"><div><b>달리기</b><small>00:08 ~ 00:27</small></div><strong>3.10 km</strong></div>
          <div class="move-segment"><img src="${A}bike.png"><div><b>자전거</b><small>00:28 ~ 현재</small></div><strong>1.58 km</strong></div>
        </div>
      </section>`;
    bindMoveBack();
    const p=document.getElementById('movePause'),r=document.getElementById('moveResume'),s=document.getElementById('moveStop');
    if(p)p.onclick=()=>{moveState='paused';render()};
    if(r)r.onclick=()=>{moveState='recording';render()};
    if(s)s.onclick=openMoveStop;
  };

  window.openMoveStop=function(){
    moveOverlay.dataset.v25MoveStop='1';
    moveOverlay.classList.add('show');
    moveOverlay.innerHTML=`<div class="move-sheet"><div class="move-sheet-handle"></div><h3>운동을 종료할까요?</h3><p>종료 시점에 운동 기록을 저장하고 요약 화면으로 이동합니다.</p><div class="move-sheet-actions"><button id="moveKeep" class="move-sheet-cancel">계속 기록</button><button id="moveConfirm" class="move-sheet-stop">종료</button></div></div>`;
    document.getElementById('moveKeep').onclick=closeMoveOverlay;
    document.getElementById('moveConfirm').onclick=()=>{
      savedAtStop=true;
      window.__yamoneMoveSavedAtStop={saved:true,savedAt:Date.now(),mode:currentType()};
      closeMoveOverlay();
      moveState='summary';
      render();
    };
    moveOverlay.onclick=e=>{if(e.target===moveOverlay)closeMoveOverlay()};
  };

  window.moveSummary=function(){
    setFocusMode(true);
    const t=currentType();
    const label=currentLabel(t);
    screen.classList.add('v25-move-end-summary');
    screen.innerHTML=`
      <div class="move-sticky"><div class="move-header-row v25-summary-header-only"><h1 class="move-plain-title">운동 요약</h1></div></div>
      <section class="card move-summary-top"><img src="${A}complete.png"><h2>이동 활동이 완료되었어요</h2></section>
      <div class="move-summary-grid">
        <div class="card move-summary-item"><img src="${A}time.png"><div><b>총 시간</b><strong>00:38:14</strong></div></div>
        <div class="card move-summary-item"><img src="${A}distance.png"><div><b>총 거리</b><strong>${t==='bike'?'12.4 km':'5.2 km'}</strong></div></div>
        <div class="card move-summary-item"><img src="${A}calorie.png"><div><b>칼로리</b><strong>${t==='bike'?'284':'418'} kcal</strong></div></div>
        <div class="card move-summary-item"><img src="${A}${t}.png"><div><b>마지막 활동</b><strong>${label}</strong></div></div>
      </div>
      <div class="move-section">구간 요약</div>
      <div class="card move-segments">
        <div class="move-segment"><img src="${A}walk.png"><div><b>걷기</b><small>시작 7분</small></div><strong>0.52 km</strong></div>
        <div class="move-segment"><img src="${A}run.png"><div><b>달리기</b><small>중간 20분</small></div><strong>3.10 km</strong></div>
        <div class="move-segment"><img src="${A}bike.png"><div><b>자전거</b><small>마지막 11분</small></div><strong>1.58 km</strong></div>
      </div>
      <div class="v25-move-summary-savebar"><button id="moveSave" class="move-save">확인</button></div>`;
    document.getElementById('moveSave').onclick=()=>{
      /* The record was already saved when End was confirmed. */
      if(!savedAtStop)window.__yamoneMoveSavedAtStop={saved:true,savedAt:Date.now(),mode:t,recovered:true};
      screen.classList.remove('v25-move-end-summary');
      view='main';tab='records';setFocusMode(false);syncTabs();render();
    };
  };

  const previousRender=window.render;
  if(typeof previousRender==='function'){
    window.render=function(){
      if(!(typeof view!=='undefined' && view==='move' && typeof moveState!=='undefined' && moveState==='summary')){
        screen.classList.remove('v25-move-end-summary');
      }
      return previousRender.apply(this,arguments);
    };
  }
})();
