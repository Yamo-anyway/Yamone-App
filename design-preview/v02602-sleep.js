/* v0.26.02: connect renewed sleep UI to the real local microphone recorder and SessionStore. */
(function(){
  'use strict';
  const A='assets/';
  let lastSleepSessionId='';
  let lastSleepSummary=null;
  let selectedSleepId='';
  let permissionPoll=0;
  let startPoll=0;
  let stopPoll=0;
  let liveTickCount=0;

  function call(name,args,fallback){
    try{
      const b=window.YamoneSleep;
      if(!b||typeof b[name]!=='function')return fallback;
      const raw=b[name].apply(b,args||[]);
      return typeof raw==='string'?JSON.parse(raw):raw;
    }catch(e){return fallback;}
  }
  function state(){return call('getState',[],{recording:false,candidateCount:0});}
  function permissions(){return call('getPermissionState',[],{microphone:false,notifications:false});}
  function settings(){return call('getSettings',[],{snoreDetection:true,sensitivity:'normal',candidateClips:true});}
  function storage(){return call('getStorageInfo',[],{bytes:0,records:0,recording:false});}
  function records(){const r=call('listRecords',[200],{records:[]});return Array.isArray(r&&r.records)?r.records:[];}
  function detail(id,max){return call('getRecordDetail',[String(id||''),max||260],{found:false});}
  function activeGraph(){const r=call('getActiveGraph',[80],{points:[]});return Array.isArray(r&&r.points)?r.points:[];}
  function command(name,args){return call(name,args||[],{ok:false,status:'bridge_unavailable'});}
  function num(v,d){const x=Number(v);return Number.isFinite(x)?x:(d||0);}
  function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
  function two(v){return String(v).padStart(2,'0');}
  function clock(ms){let s=Math.max(0,Math.floor(num(ms)/1000));const h=Math.floor(s/3600);s%=3600;const m=Math.floor(s/60);s%=60;return `${two(h)}:${two(m)}:${two(s)}`;}
  function shortDuration(ms){let s=Math.max(0,Math.floor(num(ms)/1000));const h=Math.floor(s/3600);s%=3600;const m=Math.floor(s/60);const sec=s%60;if(h)return `${h}시간 ${m}분`;if(m)return `${m}분 ${sec}초`;return `${sec}초`;}
  function offsetClock(ms){let s=Math.max(0,Math.floor(num(ms)/1000));const h=Math.floor(s/3600);s%=3600;const m=Math.floor(s/60),sec=s%60;return `${two(h)}:${two(m)}:${two(sec)}`;}
  function dateText(ms){const d=new Date(num(ms));if(!Number.isFinite(d.getTime()))return '';const w=['일','월','화','수','목','금','토'][d.getDay()];return `${d.getFullYear()}년 ${d.getMonth()+1}월 ${d.getDate()}일 (${w}) ${two(d.getHours())}:${two(d.getMinutes())}`;}
  function bytesText(bytes){let n=Math.max(0,num(bytes));if(n<1024)return `${Math.round(n)} B`;if(n<1024*1024)return `${(n/1024).toFixed(1)} KB`;if(n<1024*1024*1024)return `${(n/1024/1024).toFixed(n<100*1024*1024?1:0)} MB`;return `${(n/1024/1024/1024).toFixed(2)} GB`;}
  function dbText(v){const n=Number(v);return Number.isFinite(n)?`${n.toFixed(1)} dBFS`:'측정 중';}
  function pctText(part,total){return total>0?`${Math.min(100,Math.max(0,part/total*100)).toFixed(1)}%`:'0.0%';}

  function graphMarkup(points){
    points=Array.isArray(points)?points:[];
    if(!points.length)return '<div class="v02602-sleep-chart-empty">소리 데이터 수집 중</div>';
    return `<div class="v02602-sleep-chart">${points.map(p=>{
      const db=Math.max(-80,Math.min(-5,num(p.dbfs,-80)));
      const height=Math.max(7,Math.min(100,8+((db+80)/75)*92));
      return `<i class="${p.candidate?'candidate':''}" style="height:${height.toFixed(1)}%" title="${db.toFixed(1)} dBFS"></i>`;
    }).join('')}</div>`;
  }

  function sleepBack(){
    try{command('stopPlayback');}catch(e){}
    view='main';sleepState='ready';sleepPlaying=false;setFocusMode(false);tab='activity';syncTabs();render();
  }
  function bindRealSleepBack(){const b=document.getElementById('sleepBack');if(b)b.onclick=sleepBack;}

  window.sleepReady=function(){
    setFocusMode(true);
    const s=state();
    if(s.recording){sleepState='recording';requestAnimationFrame(()=>render());return;}
    const p=permissions(),st=storage(),cfg=settings();
    screen.innerHTML=`
      ${sleepTop(false)}
      <section class="card sleep-hero"><img src="${A}activity-sleep.png" alt=""><div class="sleep-hero-copy"><h2>수면 기록 시작</h2></div></section>
      <div class="move-section">준비 상태</div>
      <div class="sleep-ready-grid">
        <div class="card sleep-ready-card"><img src="${A}settings-permission.png"><div><b>마이크</b><strong class="v02602-sleep-status ${p.microphone?'':'warn'}">${p.microphone?'사용 가능':'권한 필요'}</strong></div></div>
        <div class="card sleep-ready-card"><img src="${A}settings-data-storage.png"><div><b>수면 데이터</b><strong>${bytesText(st.bytes)}</strong></div></div>
      </div>
      <div class="sleep-note">수면 소리와 코골이 후보는 기기 내부에 저장됩니다.${cfg.snoreDetection?' 코골이 후보 감지가 켜져 있습니다.':' 코골이 후보 감지는 현재 꺼져 있습니다.'}</div>
      <button id="sleepStart" class="sleep-start">수면 기록 시작</button>`;
    bindRealSleepBack();
    document.getElementById('sleepStart').onclick=startSleep;
  };

  function startSleep(){
    const p=permissions();
    if(!p.microphone){
      command('requestPermissions');
      if(typeof say==='function')say('마이크 권한을 허용하면 수면 기록을 시작합니다.');
      clearInterval(permissionPoll);let tries=0;
      permissionPoll=setInterval(()=>{
        if(permissions().microphone){clearInterval(permissionPoll);permissionPoll=0;startSleepNative();}
        else if(++tries>=60){clearInterval(permissionPoll);permissionPoll=0;if(typeof say==='function')say('수면 기록에는 마이크 권한이 필요합니다.');}
      },500);
      return;
    }
    startSleepNative();
  }
  function startSleepNative(){
    const r=command('start');
    if(!r.ok){if(typeof say==='function')say('수면 기록을 시작하지 못했습니다.');return;}
    clearInterval(startPoll);let tries=0;
    startPoll=setInterval(()=>{
      const s=state();
      if(s.recording){clearInterval(startPoll);startPoll=0;lastSleepSessionId=s.sessionId||'';sleepState='recording';render();}
      else if(++tries>=40){clearInterval(startPoll);startPoll=0;if(typeof say==='function')say('수면 기록 시작을 확인하지 못했습니다.');}
    },250);
  }

  window.sleepLive=function(){
    setFocusMode(true);
    const s=state();
    if(!s.recording){sleepState='ready';sleepReady();return;}
    if(s.sessionId)lastSleepSessionId=s.sessionId;
    const points=activeGraph();
    screen.innerHTML=`
      ${sleepTop(true)}
      <section class="card sleep-live-card v02602-sleep-live">
        <div class="sleep-live-head"><div class="sleep-live-left"><img src="${A}activity-sleep.png"><div><h2>수면 기록 중</h2></div></div><span class="sleep-live-pill">측정 중</span></div>
        <div class="sleep-current">
          <div class="sleep-current-top"><img src="${A}activity-sleep.png"><b>현재 소리 수준</b></div>
          <div id="v02602CurrentDb" class="sleep-current-value">${dbText(s.currentDbfs)}</div>
          <div id="v02602CurrentState" class="sleep-current-sub">${!s.snoreDetection?'코골이 감지 꺼짐':s.currentCandidate?'코골이 후보 감지 중':'코골이 후보 없음'}</div>
        </div>
        <div class="sleep-metrics">
          <div class="card sleep-metric"><img src="${A}time.png"><span>기록 시간</span><strong id="v02602Elapsed">${clock(s.elapsedMs)}</strong></div>
          <div class="card sleep-metric"><img src="${A}activity-sleep.png"><span>후보 구간</span><strong id="v02602Candidates">${Math.max(0,num(s.candidateCount))}개</strong></div>
          <div class="card sleep-metric"><img src="${A}settings-data-storage.png"><span>저장</span><strong>로컬</strong></div>
        </div>
        <div class="card sleep-chart-card"><div class="sleep-chart-title"><b>소리 크기</b><span>실제 최근 흐름 · dBFS</span></div><div id="v02602LiveGraph">${graphMarkup(points)}</div></div>
        <div class="sleep-controls"><button id="sleepStop" class="sleep-control stop">수면 기록 종료</button><button class="sleep-control" id="sleepCandidate">코골이 후보 ${Math.max(0,num(s.candidateCount))}개</button></div>
      </section>`;
    bindRealSleepBack();
    document.getElementById('sleepStop').onclick=window.openSleepStop;
    document.getElementById('sleepCandidate').onclick=()=>{const now=state();if(typeof say==='function')say(`현재까지 실제 후보 ${Math.max(0,num(now.candidateCount))}개가 저장되었습니다.`);};
  };

  function updateLive(){
    if(typeof view==='undefined'||view!=='sleep'||typeof sleepState==='undefined'||sleepState!=='recording')return;
    const s=state();
    if(!s.recording)return;
    const set=(id,text)=>{const el=document.getElementById(id);if(el&&el.textContent!==text)el.textContent=text;};
    set('v02602CurrentDb',dbText(s.currentDbfs));
    set('v02602CurrentState',!s.snoreDetection?'코골이 감지 꺼짐':s.currentCandidate?'코골이 후보 감지 중':'코골이 후보 없음');
    set('v02602Elapsed',clock(s.elapsedMs));
    set('v02602Candidates',`${Math.max(0,num(s.candidateCount))}개`);
    const btn=document.getElementById('sleepCandidate');if(btn)btn.textContent=`코골이 후보 ${Math.max(0,num(s.candidateCount))}개`;
    if(++liveTickCount%5===0){const g=document.getElementById('v02602LiveGraph');if(g)g.innerHTML=graphMarkup(activeGraph());}
  }

  window.openSleepStop=function(){
    moveOverlay.dataset.v02602SleepConfirm='1';moveOverlay.classList.add('show');
    moveOverlay.innerHTML=`<div class="move-sheet"><div class="move-sheet-handle"></div><h3>수면 기록을 종료할까요?</h3><p>종료하면 지금까지 측정한 소리 흐름과 코골이 후보가 저장됩니다.</p><div class="move-sheet-actions"><button id="sleepKeep" class="move-sheet-cancel">계속 기록</button><button id="sleepConfirm" class="move-sheet-stop">종료</button></div></div>`;
    document.getElementById('sleepKeep').onclick=closeSleepConfirm;
    document.getElementById('sleepConfirm').onclick=finishSleep;
    moveOverlay.onclick=e=>{if(e.target===moveOverlay)closeSleepConfirm();};
  };
  function closeSleepConfirm(){delete moveOverlay.dataset.v02602SleepConfirm;if(typeof closeMoveOverlay==='function')closeMoveOverlay();else{moveOverlay.classList.remove('show');moveOverlay.innerHTML='';}}
  function finishSleep(){
    const s=state();if(s.sessionId)lastSleepSessionId=s.sessionId;closeSleepConfirm();command('stop');renderSleepFinishing();
    clearInterval(stopPoll);let tries=0;
    stopPoll=setInterval(()=>{
      const now=state();
      if(!now.recording){
        const d=detail(lastSleepSessionId,260);
        if(d&&d.found){clearInterval(stopPoll);stopPoll=0;lastSleepSummary=d;sleepState='summary';render();return;}
      }
      if(++tries>=80){clearInterval(stopPoll);stopPoll=0;const d=detail(lastSleepSessionId,260);lastSleepSummary=d&&d.found?d:null;sleepState=lastSleepSummary?'summary':'ready';render();}
    },250);
  }
  function renderSleepFinishing(){setFocusMode(true);screen.innerHTML=`${sleepTop(true)}<div class="v02602-finishing"><img src="${A}activity-sleep.png"><b>수면 기록을 저장하고 있어요</b><small>소리 분석과 후보 목록을 마무리하는 중입니다.</small></div>`;bindRealSleepBack();}

  window.sleepSummary=function(){
    setFocusMode(true);
    const d=(lastSleepSummary&&lastSleepSummary.found)?lastSleepSummary:detail(lastSleepSessionId,260);
    if(!d||!d.found){sleepState='ready';sleepReady();return;}
    lastSleepSummary=d;
    const points=Array.isArray(d.graph)?d.graph:[];
    screen.innerHTML=`
      ${sleepTop(true)}
      <section class="card sleep-summary-top"><img src="${A}activity-sleep.png"><h2>수면 기록 완료</h2></section>
      <div class="sleep-summary-grid">
        <div class="card sleep-summary-item"><img src="${A}time.png"><div><b>총 수면 기록</b><strong>${shortDuration(d.durationMs)}</strong></div></div>
        <div class="card sleep-summary-item"><img src="${A}activity-sleep.png"><div><b>코골이 후보</b><strong>${Math.max(0,num(d.candidateCount))}개</strong></div></div>
        <div class="card sleep-summary-item"><img src="${A}settings-data-storage.png"><div><b>저장 위치</b><strong>기기 내부</strong></div></div>
        <div class="card sleep-summary-item"><img src="${A}settings-permission.png"><div><b>분석 상태</b><strong>완료</strong></div></div>
      </div>
      <div class="v02602-sleep-summary-sound"><div class="card"><span>평균 소리 수준</span><strong>${dbText(d.averageDbfs)}</strong></div><div class="card"><span>최대 소리 수준</span><strong>${dbText(d.maxDbfs)}</strong></div></div>
      <div class="card sleep-chart-card"><div class="sleep-chart-title"><b>전체 소리 흐름</b><span>분홍색은 코골이 후보</span></div>${graphMarkup(points)}</div>
      <div class="sleep-note">후보 음원 재생과 ‘아님’ 표시는 기록 상세에서 확인할 수 있습니다.</div>
      <button id="sleepSave" class="sleep-save">확인</button>`;
    bindRealSleepBack();
    document.getElementById('sleepSave').onclick=()=>{lastSleepSummary=null;view='main';sleepState='ready';tab='records';setFocusMode(false);syncTabs();render();};
  };

  window.yamoneOpenActiveSleep=function(openStop){
    const s=state();view='sleep';sleepState=s.recording?'recording':'ready';tab='activity';setFocusMode(true);syncTabs();render();
    if(openStop&&s.recording)setTimeout(()=>window.openSleepStop(),180);
    return true;
  };

  /* Records: keep the v0.26.01 real movement nodes (and their existing handlers),
     then merge real sleep records by timestamp. */
  function sleepListItem(r){
    return `<button class="card record record-open v02602-sleep-record" data-v02602-sleep="${esc(r.sessionId)}"><img src="${A}activity-sleep.png" alt=""><div class="record-copy"><b>수면 기록</b><small>${esc(dateText(r.startEpochMs))}</small></div><div class="record-value"><b>${esc(shortDuration(r.durationMs))}</b><small>후보 ${Math.max(0,num(r.candidateCount))}개</small></div></button>`;
  }
  function movementStartMap(){
    const out=new Map();
    try{const raw=YamoneRecords.getSavedRecords(100);const p=typeof raw==='string'?JSON.parse(raw):raw;(p.records||[]).forEach(r=>out.set(String(r.sessionId),num(r.startEpochMs)));}catch(e){}
    return out;
  }
  const previousRenderRecords=window.renderRecords;
  window.renderRecords=function(){
    previousRenderRecords.apply(this,arguments);
    const sleepRecords=records();
    if(recordFilter!=='all'&&recordFilter!=='sleep')return;
    const section=[...screen.querySelectorAll('.section')].find(x=>x.textContent.trim()==='저장된 기록');if(!section)return;
    const empty=screen.querySelector('.record-empty');
    const sleepNodes=sleepRecords.map(r=>{const t=document.createElement('template');t.innerHTML=sleepListItem(r).trim();const node=t.content.firstElementChild;node.dataset.v02602Start=String(num(r.startEpochMs));return node;});
    if(recordFilter==='sleep'){
      if(empty)empty.remove();
      if(!sleepNodes.length){section.insertAdjacentHTML('afterend','<div class="record-empty">표시할 기록이 없습니다.</div>');return;}
      const frag=document.createDocumentFragment();sleepNodes.forEach(n=>frag.appendChild(n));section.after(frag);
    }else{
      const starts=movementStartMap();
      const moveNodes=[...screen.querySelectorAll('[data-v2513-saved]')];
      const combined=[];
      moveNodes.forEach(n=>combined.push({node:n,start:starts.get(String(n.dataset.v2513Saved))||0}));
      sleepNodes.forEach((n,i)=>combined.push({node:n,start:num(sleepRecords[i].startEpochMs)}));
      if(empty&&combined.length)empty.remove();
      if(combined.length){combined.sort((a,b)=>b.start-a.start);const frag=document.createDocumentFragment();combined.forEach(x=>frag.appendChild(x.node));section.after(frag);}
    }
    screen.querySelectorAll('[data-v02602-sleep]').forEach(b=>b.onclick=()=>{selectedSleepId=b.dataset.v02602Sleep;view='saved-sleep-detail';render();});
  };

  function openSleepRecord(id){selectedSleepId=String(id||'');view='saved-sleep-detail';render();}
  window.v02602OpenSleepRecord=openSleepRecord;

  function renderSleepDetail(){
    const d=detail(selectedSleepId,300);
    if(!d||!d.found){view='main';tab='records';syncTabs();render();return;}
    setFocusMode(false);
    const events=Array.isArray(d.events)?d.events:[];
    const candidateHtml=events.length?events.map(e=>candidateRow(d,e)).join(''):'<div class="record-empty">감지된 코골이 후보가 없습니다.</div>';
    screen.innerHTML=`<div class="v02602-sleep-detail">
      <div class="move-sticky"><div class="move-header-row"><button id="v02602SleepBack" class="move-back-asset-btn"><img class="move-back-asset" src="${A}back-v02402.png" alt="뒤로가기"></button><div class="move-plain-title">수면 기록 상세</div></div></div>
      <div class="record-detail-summary"><div><b>수면 기록</b><small>${esc(dateText(d.startEpochMs))}</small></div><span>수면</span></div>
      <div class="detail-stat-grid v24-list-grid">
        <div class="card"><span>기록 시간</span><b>${esc(shortDuration(d.durationMs))}</b></div>
        <div class="card"><span>코골이 후보</span><b>${Math.max(0,num(d.candidateCount))}개</b></div>
        <div class="card"><span>후보 비율</span><b>${pctText(num(d.candidateDurationMs),num(d.durationMs))}</b></div>
        <div class="card"><span>평균 소리</span><b>${esc(dbText(d.averageDbfs))}</b></div>
        <div class="card"><span>최대 소리</span><b>${esc(dbText(d.maxDbfs))}</b></div>
        <div class="card"><span>저장 용량</span><b>${esc(bytesText(d.storageBytes))}</b></div>
      </div>
      <div class="detail-section-head"><b>전체 소리 흐름</b><small>분홍색은 후보 구간</small></div>
      <div class="card sleep-chart-card">${graphMarkup(d.graph)}</div>
      <div class="detail-section-head"><b>코골이 후보</b><small>기기에 저장된 후보 음원</small></div>
      <div class="v02602-candidates">${candidateHtml}</div>
      <div class="v02602-sleep-detail-actions"><button id="v02602DeleteSleepRecord" class="v02602-danger">이 수면 기록 삭제</button></div>
    </div>`;
    document.getElementById('v02602SleepBack').onclick=backToSleepRecords;
    const del=document.getElementById('v02602DeleteSleepRecord');if(del)del.onclick=()=>confirmAction('수면 기록을 삭제할까요?','이 기록의 소리 분석 데이터와 저장된 후보 음원이 모두 삭제됩니다.','기록 삭제',()=>{const r=command('deleteRecord',[selectedSleepId]);if(r.ok){selectedSleepId='';backToSleepRecords();}else if(typeof say==='function')say('수면 기록을 삭제하지 못했습니다.');});
    bindCandidateActions(d);
    updatePlaybackUi();
  }
  function backToSleepRecords(){command('stopPlayback');selectedSleepId='';view='main';tab='records';recordFilter='sleep';syncTabs();render();}

  function candidateRow(d,e){
    const label=String(e.reviewLabel||'UNREVIEWED');const rejected=label==='NOT_SNORE';
    return `<div class="card v02602-candidate" data-sleep-event="${e.index}"><div class="v02602-candidate-head"><div><b>후보 ${Math.max(1,num(e.displayNumber,e.index+1))} · ${offsetClock(e.startOffsetMs)}</b><small>${shortDuration(e.durationMs)} · 최대 ${dbText(e.dbfsMax)}</small></div><strong>${rejected?'코골이 아님':label==='SNORE'?'코골이 확인':label==='UNCERTAIN'?'확인 필요':'후보'}</strong></div><div class="v02602-candidate-actions"><button data-sleep-play="${e.index}" ${e.hasClip?'':'disabled'}>${e.hasClip?'▶ 재생':'음원 없음'}</button><button class="${rejected?'active':''}" data-sleep-reject="${e.index}">${rejected?'아님 ✓':'아님'}</button><button class="danger" data-sleep-delete-clip="${e.index}" ${e.hasClip?'':'disabled'}>음원 삭제</button></div><div class="v02602-playback" data-sleep-playback="${e.index}"><input type="range" min="0" max="1" value="0" data-sleep-seek="${e.index}"><small><span data-sleep-play-pos="${e.index}">00:00</span><span data-sleep-play-dur="${e.index}">00:00</span></small></div></div>`;
  }
  function mmss(ms){const s=Math.max(0,Math.floor(num(ms)/1000));return `${two(Math.floor(s/60))}:${two(s%60)}`;}
  function bindCandidateActions(d){
    screen.querySelectorAll('[data-sleep-play]').forEach(btn=>btn.onclick=()=>{
      const idx=Number(btn.dataset.sleepPlay);const p=call('getPlaybackState',[],{active:false});
      if(p.active&&String(p.sessionId)===String(selectedSleepId)&&Number(p.eventIndex)===idx){command(p.playing?'pauseClip':'resumeClip');}
      else command('playClip',[selectedSleepId,idx]);
      setTimeout(updatePlaybackUi,120);
    });
    screen.querySelectorAll('[data-sleep-reject]').forEach(btn=>btn.onclick=()=>{
      const idx=Number(btn.dataset.sleepReject);const e=(d.events||[]).find(x=>Number(x.index)===idx);const next=e&&e.reviewLabel==='NOT_SNORE'?'UNREVIEWED':'NOT_SNORE';const r=command('reviewEvent',[selectedSleepId,idx,next]);if(r.ok)renderSleepDetail();
    });
    screen.querySelectorAll('[data-sleep-delete-clip]').forEach(btn=>btn.onclick=()=>{
      const idx=Number(btn.dataset.sleepDeleteClip);confirmAction('후보 음원을 삭제할까요?','후보 시간과 분석값은 남고, 이 후보의 음원 파일만 삭제됩니다.','음원 삭제',()=>{const r=command('deleteClip',[selectedSleepId,idx]);if(r.ok)renderSleepDetail();});
    });
    screen.querySelectorAll('[data-sleep-seek]').forEach(range=>range.onchange=()=>command('seekClip',[Number(range.value)]));
  }
  function updatePlaybackUi(){
    if(typeof view==='undefined'||view!=='saved-sleep-detail')return;
    const p=call('getPlaybackState',[],{active:false,playing:false,positionMs:0,durationMs:0,eventIndex:-1});
    screen.querySelectorAll('[data-sleep-playback]').forEach(box=>box.classList.toggle('active',p.active&&Number(box.dataset.sleepPlayback)===Number(p.eventIndex)));
    screen.querySelectorAll('[data-sleep-play]').forEach(btn=>{const same=p.active&&Number(btn.dataset.sleepPlay)===Number(p.eventIndex);if(same)btn.textContent=p.playing?'⏸ 일시정지':'▶ 계속';else if(!btn.disabled)btn.textContent='▶ 재생';});
    if(p.active){const range=screen.querySelector(`[data-sleep-seek="${p.eventIndex}"]`);if(range){range.max=String(Math.max(1,num(p.durationMs)));range.value=String(Math.max(0,num(p.positionMs)));}const pos=screen.querySelector(`[data-sleep-play-pos="${p.eventIndex}"]`),dur=screen.querySelector(`[data-sleep-play-dur="${p.eventIndex}"]`);if(pos)pos.textContent=mmss(p.positionMs);if(dur)dur.textContent=mmss(p.durationMs);}
  }

  /* Sleep settings: real detector options, total local storage and confirmed delete-all. */
  const previousSettingDetail=window.renderSettingDetail;
  window.renderSettingDetail=function(){
    if(typeof settingPage!=='undefined'&&settingPage==='수면'){renderRealSleepSettings();return;}
    return previousSettingDetail?previousSettingDetail.apply(this,arguments):undefined;
  };
  function renderRealSleepSettings(){
    setFocusMode(true);const cfg=settings(),st=storage();
    screen.innerHTML=`${settingHeader('수면')}
      ${settingToggle('v02602Snore','코골이 감지','코골이 후보 구간 표시',!!cfg.snoreDetection,'settings-sleep')}
      ${settingSection('감지 민감도',`<div class="card setting-block">${settingChips('v02602Sensitivity',[['low','낮음'],['normal','보통'],['high','높음']],cfg.sensitivity||'normal')}</div>`)}
      ${settingSection('수면 데이터',`<div class="card v02602-storage-card"><img src="${A}settings-data-storage.png"><div><b>전체 저장 용량</b><small>저장된 수면 기록 ${Math.max(0,num(st.records))}개</small></div><strong>${bytesText(st.bytes)}</strong></div><button id="v02602DeleteAllSleep" class="v02602-storage-delete" ${st.recording?'disabled':''}>전체 수면 데이터 삭제</button>`)}
      <div class="setting-info v02602-sleep-settings-note">개별 수면 기록 삭제는 기록 상세 화면에서 할 수 있습니다.${st.recording?' 현재 수면 기록 중에는 전체 삭제할 수 없습니다.':''}</div>`;
    const back=document.getElementById('settingBack');if(back)back.onclick=()=>{settingPage=null;view='main';tab='settings';setFocusMode(false);syncTabs();render();};
    const toggle=screen.querySelector('[data-setting-toggle="v02602Snore"]');if(toggle)toggle.onclick=()=>{command('saveSettings',[JSON.stringify({snoreDetection:!cfg.snoreDetection,sensitivity:cfg.sensitivity||'normal'})]);renderRealSleepSettings();};
    screen.querySelectorAll('[data-setting-chip="v02602Sensitivity"]').forEach(btn=>btn.onclick=()=>{command('saveSettings',[JSON.stringify({snoreDetection:!!cfg.snoreDetection,sensitivity:btn.dataset.value})]);renderRealSleepSettings();});
    const del=document.getElementById('v02602DeleteAllSleep');if(del&&!st.recording)del.onclick=()=>confirmAction('전체 수면 데이터를 삭제할까요?','저장된 모든 수면 기록과 후보 음원이 삭제됩니다. 이 작업은 되돌릴 수 없습니다.','전체 삭제',()=>{const r=command('deleteAll');if(r.ok){if(typeof say==='function')say('수면 데이터를 모두 삭제했습니다.');renderRealSleepSettings();}else if(typeof say==='function')say('수면 데이터를 삭제하지 못했습니다.');});
  }

  function confirmAction(title,message,confirmText,onConfirm){
    moveOverlay.dataset.v02602SleepConfirm='1';moveOverlay.classList.add('show');
    moveOverlay.innerHTML=`<div class="move-sheet"><div class="move-sheet-handle"></div><h3>${esc(title)}</h3><p>${esc(message)}</p><div class="move-sheet-actions"><button id="v02602ConfirmCancel" class="move-sheet-cancel">취소</button><button id="v02602ConfirmOk" class="move-sheet-stop">${esc(confirmText)}</button></div></div>`;
    document.getElementById('v02602ConfirmCancel').onclick=closeSleepConfirm;
    document.getElementById('v02602ConfirmOk').onclick=()=>{closeSleepConfirm();onConfirm();};
    moveOverlay.onclick=e=>{if(e.target===moveOverlay)closeSleepConfirm();};
  }

  const previousRender=window.render;
  window.render=function(){if(typeof view!=='undefined'&&view==='saved-sleep-detail'){renderSleepDetail();return;}return previousRender.apply(this,arguments);};
  const previousBack=window.yamoneAndroidBack;
  window.yamoneAndroidBack=function(){
    if(typeof view!=='undefined'&&view==='saved-sleep-detail'){backToSleepRecords();return true;}
    if(typeof view!=='undefined'&&view==='sleep'){
      if(moveOverlay&&moveOverlay.classList.contains('show')){closeSleepConfirm();return true;}
      sleepBack();return true;
    }
    return previousBack?previousBack():false;
  };

  setInterval(()=>{updateLive();updatePlaybackUi();},1000);
})();
