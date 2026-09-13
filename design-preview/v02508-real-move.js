/* v0.25.08: connect the renewed walking/running/cycling UI to WalkingRecorderService. */
(function(){
  'use strict';
  const A='assets/';
  const POLL_MS=1000;
  const ROUTE_EVERY=3;
  let tickCount=0;
  let permissionTimer=0;
  let startTimer=0;
  let stopTimer=0;
  let latestState=null;
  let latestRecord=null;
  let lastRouteSignature='';

  function bridge(){return window.YamoneMovement||null;}
  function jsonCall(name,args,fallback){
    try{
      const b=bridge();
      if(!b||typeof b[name]!=='function')return fallback;
      const raw=b[name].apply(b,args||[]);
      return typeof raw==='string'?JSON.parse(raw):raw;
    }catch(e){return fallback;}
  }
  function getState(){return jsonCall('getState',[],{recording:false,paused:false,splitsMs:[]});}
  function getPermissions(){return jsonCall('getPermissionState',[],{location:false});}
  function getRoute(max){return jsonCall('getRoute',[max||240],{points:[]});}
  function getLatest(max){return jsonCall('getLatestRecord',[max||400],{found:false});}
  function command(name,arg){return jsonCall(name,arg==null?[]:[arg],{ok:false,status:'bridge_unavailable'});}

  function activityUiType(s){
    const type=(s&&s.activityType)||'walking';
    if(type==='cycling')return 'bike';
    if(type==='running')return 'run';
    if(type==='walkrun')return (s&&s.autoMotionMode)==='running'?'run':'walk';
    return 'walk';
  }
  function syncModeFromState(s){
    if(!s)return;
    if(s.activityType==='cycling'){moveMode='bike';moveSubtype='bike';}
    else if(s.activityType==='running'){moveMode='run';moveSubtype='run';}
    else if(s.activityType==='walkrun'){moveMode='auto';moveSubtype=s.autoMotionMode==='running'?'run':'walk';}
    else {moveMode='walk';moveSubtype='walk';}
  }
  function label(t){return t==='bike'?'자전거':t==='run'?'달리기':'걷기';}
  function icon(t){return t==='bike'?'bike.png':t==='run'?'run.png':'walk.png';}
  function n(v,d){const x=Number(v);return Number.isFinite(x)?x:(d||0);}
  function fmtTime(ms){
    let sec=Math.max(0,Math.floor(n(ms)/1000));
    const h=Math.floor(sec/3600);sec%=3600;
    const m=Math.floor(sec/60),s=sec%60;
    return [h,m,s].map(x=>String(x).padStart(2,'0')).join(':');
  }
  function fmtShortTime(ms){
    let sec=Math.max(0,Math.floor(n(ms)/1000));
    const h=Math.floor(sec/3600);sec%=3600;
    const m=Math.floor(sec/60),s=sec%60;
    return h>0?`${h}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`:`${m}:${String(s).padStart(2,'0')}`;
  }
  function fmtDistance(m){return `${(Math.max(0,n(m))/1000).toFixed(Math.max(0,n(m))<10000?2:1)} km`;}
  function fmtSpeed(kmh){return `${Math.max(0,n(kmh)).toFixed(1)} km/h`;}
  function paceFromKmh(kmh){
    kmh=n(kmh);
    if(kmh<0.3)return "--'--″/km";
    return paceFromMsPerKm(3600000/kmh);
  }
  function paceFromMsPerKm(ms){
    if(!Number.isFinite(ms)||ms<=0)return "--'--″/km";
    let sec=Math.round(ms/1000);const min=Math.floor(sec/60);sec%=60;
    return `${min}'${String(sec).padStart(2,'0')}″/km`;
  }
  function avgKmh(s){const hours=n(s&&s.movingMs)/3600000;return hours>0?(n(s&&s.distanceM)/1000)/hours:0;}
  function avgPace(s){const d=n(s&&s.distanceM);return d>5?paceFromMsPerKm(n(s&&s.movingMs)/(d/1000)):"--'--″/km";}
  function recentSplit(s,t){
    const a=Array.isArray(s&&s.splitsMs)?s.splitsMs:[];
    if(!a.length)return t==='bike'?'--.- km/h':"--'--″/km";
    const ms=n(a[a.length-1]);
    if(t==='bike')return ms>0?`${(3600000/ms).toFixed(1)} km/h`:'--.- km/h';
    return paceFromMsPerKm(ms);
  }
  function gpsState(s){
    const age=n(s&&s.fixAgeMs,-1),acc=s&&s.accuracyM==null?NaN:Number(s.accuracyM);
    if(age<0||age>12000)return {text:'GPS 미확인',tone:'lost'};
    if(Number.isFinite(acc)&&acc<=20&&age<=5000)return {text:'양호',tone:'good'};
    if(Number.isFinite(acc)&&acc<=45&&age<=10000)return {text:'보통',tone:'fair'};
    return {text:'불안정',tone:'fair'};
  }
  function summaryValues(s){
    const t=activityUiType(s);
    return {type:t,distance:fmtDistance(s.distanceM),time:fmtTime(s.elapsedMs),recent:recentSplit(s,t),overall:t==='bike'?fmtSpeed(avgKmh(s)):avgPace(s),max:fmtSpeed(s.maxSpeedKmh)};
  }

  function requestAndStart(type){
    command('requestPermissions');
    say('위치 권한을 허용하면 운동 기록을 시작합니다.');
    clearInterval(permissionTimer);
    let tries=0;
    permissionTimer=setInterval(()=>{
      const p=getPermissions();
      if(p.location){clearInterval(permissionTimer);permissionTimer=0;startNative(type);}
      else if(++tries>=60){clearInterval(permissionTimer);permissionTimer=0;say('위치 권한이 필요합니다.');}
    },500);
  }
  function startNative(type){
    const result=command('start',type);
    if(!result.ok){
      if(result.status==='location_permission_required'){requestAndStart(type);return;}
      say('활동 기록을 시작하지 못했습니다.');return;
    }
    clearInterval(startTimer);
    let tries=0;
    startTimer=setInterval(()=>{
      const s=getState();
      if(s.recording){clearInterval(startTimer);startTimer=0;latestState=s;syncModeFromState(s);moveState=s.paused?'paused':'recording';render();}
      else if(++tries>=30){clearInterval(startTimer);startTimer=0;say('GPS 기록 시작을 확인하지 못했습니다.');}
    },200);
  }

  window.moveReady=function(){
    setFocusMode(true);
    const active=getState();
    if(active.recording){latestState=active;syncModeFromState(active);moveState=active.paused?'paused':'recording';render();return;}
    screen.innerHTML=`
      ${moveTop('걷기 · 달리기 · 자전거','활동 시작 준비')}
      <section class="card move-hero"><img class="move-hero-icon" src="${A}move-main.png" alt=""><div class="move-hero-copy"><h2>이동 활동 시작</h2><div class="move-badges"><span class="move-badge"><img src="${A}gps.png">실제 GPS 기록</span><span class="move-badge"><img src="${A}auto-switch.png">로컬 보정</span></div></div></section>
      <div class="move-section">시작 방식</div>
      <div class="move-mode-grid">${moveModeButton('auto','auto-switch.png','자동 전환','걷기 · 달리기')}${moveModeButton('walk','walk.png','걷기','페이스')}${moveModeButton('run','run.png','달리기','페이스')}${moveModeButton('bike','bike.png','자전거','속도')}</div>
      <div class="move-section">기록 상태</div>
      <div class="move-status-grid"><div class="card move-status"><img src="${A}gps.png"><div><b>GPS</b><small>위치 권한 확인 후 실제 좌표를 기록합니다.</small></div></div><div class="card move-status"><img src="${A}route.png"><div><b>운동 경로</b><small>기록 중 실제 이동 경로를 지도에 표시합니다.</small></div></div></div>
      <div class="move-section">안내</div><div class="move-note">자동 전환은 현재 실제 기록 서비스의 걷기↔달리기 구분을 사용합니다. 자전거는 자전거를 선택해 시작합니다.</div>
      <button id="moveStart" class="move-start">활동 시작</button>`;
    bindMoveBack();
    document.querySelectorAll('[data-move-mode]').forEach(b=>b.onclick=()=>{moveMode=b.dataset.moveMode;if(moveMode!=='auto')moveSubtype=moveMode;moveReady();});
    document.getElementById('moveStart').onclick=()=>{const type=moveMode==='auto'?'auto':moveMode;const p=getPermissions();if(!p.location){requestAndStart(type);return;}startNative(type);};
  };

  function livePrimary(t,s){
    const summaryButton=`<button type="button" class="v25-summary-open" data-v25-summary-open aria-label="요약보기"><img src="${A}summary-view-button-v02503.svg" alt="요약보기"></button>`;
    if(t==='bike')return `<div class="move-primary"><div class="move-primary-head"><img src="${A}speed.png"><b>현재 속도</b></div><div class="v25-live-primary-row"><div id="realPrimary" class="move-primary-value">${fmtSpeed(s.currentSpeedKmh)}</div>${summaryButton}</div><div id="realPrimarySub" class="move-primary-sub">평균 속도 ${fmtSpeed(avgKmh(s))}</div></div>`;
    return `<div class="move-primary"><div class="move-primary-head"><img src="${A}pace.png"><b>현재 페이스</b></div><div class="v25-live-primary-row"><div id="realPrimary" class="move-primary-value">${paceFromKmh(s.currentSpeedKmh)}</div>${summaryButton}</div><div id="realPrimarySub" class="move-primary-sub">평균 페이스 ${avgPace(s)} · ${label(t)}</div></div>`;
  }
  function segmentRow(t,name,ms,distance){return `<div class="move-segment"><img src="${A}${icon(t)}"><div><b>${name}</b><small>${fmtShortTime(ms)} 이동</small></div><strong>${fmtDistance(distance)}</strong></div>`;}
  function segmentRows(s){
    if(s.activityType==='walkrun'){
      const rows=[];
      if(n(s.walkingDistanceM)>0||n(s.walkingMovingMs)>0)rows.push(segmentRow('walk','걷기',s.walkingMovingMs,s.walkingDistanceM));
      if(n(s.runningDistanceM)>0||n(s.runningMovingMs)>0)rows.push(segmentRow('run','달리기',s.runningMovingMs,s.runningDistanceM));
      return rows.length?rows.join(''):`<div class="v2508-empty-segment">이동이 확인되면 걷기·달리기 구간이 표시됩니다.</div>`;
    }
    const t=activityUiType(s);return segmentRow(t,label(t),s.movingMs,s.distanceM);
  }

  window.moveLive=function(paused){
    setFocusMode(true);screen.classList.remove('v25-move-end-summary','v25-summary-screen');
    const s=getState();latestState=s;if(!s.recording){moveState='ready';moveReady();return;}
    syncModeFromState(s);paused=!!s.paused;moveState=paused?'paused':'recording';
    const t=activityUiType(s),g=gpsState(s);
    screen.innerHTML=`${moveTop('이동 활동 기록',paused?'일시정지':'기록 중')}<section class="card move-live-card v2508-real-live">
      <div class="move-live-head"><div class="move-live-left"><img src="${A}${icon(t)}"><div><h2 id="realActivityLabel">${label(t)} ${paused?'일시정지':'기록 중'}</h2></div></div><span class="move-auto-pill">${s.activityType==='walkrun'?'자동 전환':'수동 시작'}</span></div>
      ${livePrimary(t,s)}
      <div class="move-metrics"><div class="card move-metric"><img src="${A}time.png"><span>시간</span><strong id="realElapsed">${fmtTime(s.elapsedMs)}</strong></div><div class="card move-metric"><img src="${A}distance.png"><span>거리</span><strong id="realDistance">${fmtDistance(s.distanceM)}</strong></div><div class="card move-metric"><img src="${A}${t==='bike'?'speed.png':'walk.png'}"><span>${t==='bike'?'최고속도':'걸음수'}</span><strong id="realThirdMetric">${t==='bike'?fmtSpeed(s.maxSpeedKmh):`${Math.round(n(s.steps))} 걸음`}</strong></div></div>
      <div class="card move-map v2508-map-card"><div class="v2508-map-title"><img src="${A}route.png"><div><b>운동 경로</b><small>실제 GPS 경로 · 지도 타일은 저장하지 않음</small></div></div><div id="moveRealMap" class="v2508-route-map"><div class="v2508-map-wait">GPS 수신 대기 중</div></div></div>
      <div class="move-controls v25-live-controls-under-route">${paused?`<button id="moveResume" class="move-control resume"><img src="${A}resume.png"><b>다시 시작</b><small>기록 재개</small></button>`:`<button id="movePause" class="move-control pause"><img src="${A}pause.png"><b>일시정지</b><small>잠시 멈춤</small></button>`}<button id="moveStop" class="move-control stop"><img src="${A}stop.png"><b>운동 종료</b><small>종료 시 저장</small></button><button class="move-control" type="button"><img src="${A}gps.png"><b>GPS 상태</b><small id="realGpsState" class="v2508-gps-${g.tone}">${g.text}</small></button></div>
      <div class="card move-segments v25-live-segments-bottom"><h3>${s.activityType==='walkrun'?'자동 구간 구분':'활동 구간'}</h3><div id="realSegments">${segmentRows(s)}</div></div>
      </section>`;
    bindMoveBack();
    const p=document.getElementById('movePause'),r=document.getElementById('moveResume'),stop=document.getElementById('moveStop');
    if(p)p.onclick=()=>{command('pause');setTimeout(()=>{moveState='paused';render();},120);};
    if(r)r.onclick=()=>{command('resume');setTimeout(()=>{moveState='recording';render();},120);};
    if(stop)stop.onclick=openMoveStop;
    const glance=document.querySelector('[data-v25-summary-open]');if(glance)glance.onclick=e=>{e.preventDefault();e.stopPropagation();moveState='glance';render();};
    updateRoute(true);
  };

  function updateLiveDom(s){
    if(!s||!s.recording||view!=='move'||!['recording','paused'].includes(moveState))return;
    syncModeFromState(s);const t=activityUiType(s),g=gpsState(s);const set=(id,text)=>{const el=document.getElementById(id);if(el&&el.textContent!==text)el.textContent=text;};
    set('realElapsed',fmtTime(s.elapsedMs));set('realDistance',fmtDistance(s.distanceM));set('realThirdMetric',t==='bike'?fmtSpeed(s.maxSpeedKmh):`${Math.round(n(s.steps))} 걸음`);set('realPrimary',t==='bike'?fmtSpeed(s.currentSpeedKmh):paceFromKmh(s.currentSpeedKmh));set('realPrimarySub',t==='bike'?`평균 속도 ${fmtSpeed(avgKmh(s))}`:`평균 페이스 ${avgPace(s)} · ${label(t)}`);set('realGpsState',g.text);
    const seg=document.getElementById('realSegments');if(seg)seg.innerHTML=segmentRows(s);const act=document.getElementById('realActivityLabel');if(act)act.textContent=`${label(t)} ${s.paused?'일시정지':'기록 중'}`;
  }

  window.openMoveStop=function(){
    moveOverlay.dataset.v25MoveStop='1';moveOverlay.classList.add('show');
    moveOverlay.innerHTML=`<div class="move-sheet"><div class="move-sheet-handle"></div><h3>운동을 종료할까요?</h3><p>종료하는 시점에 현재 운동 기록이 저장됩니다.</p><div class="move-sheet-actions"><button id="moveKeep" class="move-sheet-cancel">계속 기록</button><button id="moveConfirm" class="move-sheet-stop">종료</button></div></div>`;
    document.getElementById('moveKeep').onclick=closeMoveOverlay;document.getElementById('moveConfirm').onclick=()=>{closeMoveOverlay();finishNativeRecording();};moveOverlay.onclick=e=>{if(e.target===moveOverlay)closeMoveOverlay();};
  };
  function finishNativeRecording(){
    command('stop');moveState='finishing';renderFinishing();clearInterval(stopTimer);let tries=0;
    stopTimer=setInterval(()=>{const s=getState(),rec=getLatest(420);if(!s.recording&&rec.found&&rec.meta&&rec.meta.status==='complete'){clearInterval(stopTimer);stopTimer=0;latestState=s;latestRecord=rec;moveState='summary';render();}else if(++tries>=50){clearInterval(stopTimer);stopTimer=0;latestRecord=rec.found?rec:null;moveState='summary';render();}},150);
  }
  function renderFinishing(){setFocusMode(true);screen.innerHTML=`<div class="move-sticky"><div class="move-header-row v25-summary-header-only"><h1 class="move-plain-title">운동 종료</h1></div></div><div class="v2508-finishing"><img src="${A}complete.png" alt=""><b>운동 기록을 저장하고 있어요</b><small>잠시만 기다려 주세요.</small></div>`;}
  function recordState(rec){const m=(rec&&rec.meta)||{};return {activityType:m.type||'walking',autoMotionMode:m.autoMotionModeLast||'walking',elapsedMs:n(m.durationMs),movingMs:n(m.movingMs),distanceM:n(m.distanceM),steps:n(m.steps),maxSpeedKmh:n(m.maxSpeedKmh),splitsMs:Array.isArray(m.splitsMs)?m.splitsMs:[],walkingDistanceM:n(m.walkingDistanceM),runningDistanceM:n(m.runningDistanceM),walkingMovingMs:n(m.walkingMovingMs),runningMovingMs:n(m.runningMovingMs)};}

  window.moveSummary=function(){
    setFocusMode(true);const rec=latestRecord&&latestRecord.found?latestRecord:getLatest(420);latestRecord=rec;const s=recordState(rec);syncModeFromState(s);const t=activityUiType(s);screen.classList.add('v25-move-end-summary');
    const splits=Array.isArray(s.splitsMs)?s.splitsMs:[];const splitHtml=splits.length?splits.map((ms,i)=>`<div class="move-segment"><img src="${A}${t==='bike'?'speed.png':'pace.png'}"><div><b>${i+1} km</b><small>${fmtShortTime(ms)}</small></div><strong>${t==='bike'?`${(3600000/n(ms)).toFixed(1)} km/h`:paceFromMsPerKm(n(ms))}</strong></div>`).join(''):`<div class="v2508-empty-segment">완료된 1km 구간이 없습니다.</div>`;
    screen.innerHTML=`<div class="move-sticky"><div class="move-header-row v25-summary-header-only"><h1 class="move-plain-title">운동 요약</h1></div></div><section class="card move-summary-top"><img src="${A}complete.png"><h2>이동 활동이 저장되었어요</h2></section><div class="move-summary-grid"><div class="card move-summary-item"><img src="${A}time.png"><div><b>총 시간</b><strong>${fmtTime(s.elapsedMs)}</strong></div></div><div class="card move-summary-item"><img src="${A}distance.png"><div><b>총 거리</b><strong>${fmtDistance(s.distanceM)}</strong></div></div><div class="card move-summary-item"><img src="${A}${t==='bike'?'speed.png':'walk.png'}"><div><b>${t==='bike'?'최고 속도':'걸음수'}</b><strong>${t==='bike'?fmtSpeed(s.maxSpeedKmh):`${Math.round(s.steps)} 걸음`}</strong></div></div><div class="card move-summary-item"><img src="${A}${icon(t)}"><div><b>활동</b><strong>${label(t)}</strong></div></div></div><div class="move-section">운동 경로</div><div class="card v2508-summary-map-card"><div id="summaryRealMap" class="v2508-route-map"><div class="v2508-map-wait">경로 불러오는 중</div></div></div><div class="move-section">구간 요약</div><div class="card move-segments">${splitHtml}</div><div class="v25-move-summary-savebar"><button id="moveSave" class="move-save">확인</button></div>`;
    document.getElementById('moveSave').onclick=()=>{screen.classList.remove('v25-move-end-summary');view='main';tab='records';moveState='ready';setFocusMode(false);syncTabs();render();};requestAnimationFrame(()=>drawRouteMap('summaryRealMap',(rec&&rec.points)||[]));
  };

  function realGlance(){
    const s=getState();if(!s.recording){moveState='ready';moveReady();return;}latestState=s;syncModeFromState(s);setFocusMode(true);const v=summaryValues(s),t=v.type;
    const row=(ico,name,value,unit)=>{let main=value,shownUnit=unit||'';if(!shownUnit){const match=String(value).match(/^(.*?)(\s(?:km\/h|km)|\/km)$/);if(match){main=match[1];shownUnit=match[2].trim();}}return `<section class="card v25-summary-card"><img src="${A}${ico}" alt=""><span class="v25-summary-label">${name}</span><div class="v25-summary-value-wrap"><strong><span class="v25-summary-number">${main}</span>${shownUnit?`<span class="v25-summary-unit">${shownUnit}</span>`:''}</strong></div></section>`;};
    const cards=t==='bike'?row('distance.png','거리',v.distance)+row('time.png','시간',v.time)+row('speed.png','최근 1km 평균속도',v.recent)+row('speed.png','전체 평균속도',v.overall)+row('speed.png','최대속도',v.max):row('distance.png','거리',v.distance)+row('time.png','시간',v.time)+row('pace.png','최근 1km 페이스',v.recent)+row('pace.png','전체 페이스',v.overall);const rows=t==='bike'?5:4;
    screen.classList.add('v25-summary-screen',`v25-summary-rows-${rows}`);screen.style.setProperty('--v25-summary-rows',String(rows));screen.innerHTML=`<div class="move-sticky"><div class="move-header-row"><button id="v2508GlanceBack" class="v2508-glance-back"><img class="move-back-asset" src="${A}back-v02402.png" alt="뒤로가기"></button><img class="v25-summary-title-asset" src="${A}title-summary-${t}-v02503.svg" alt="요약보기 (${label(t)})"></div></div><section class="v25-summary-body"><div class="v25-summary-grid">${cards}</div></section>`;
    document.getElementById('v2508GlanceBack').onclick=()=>{screen.classList.remove('v25-summary-screen',`v25-summary-rows-${rows}`);screen.style.removeProperty('--v25-summary-rows');moveState=s.paused?'paused':'recording';render();};
  }

  function updateRoute(force){
    if(view!=='move'||!['recording','paused'].includes(moveState))return;if(!force&&tickCount%ROUTE_EVERY!==0)return;const route=getRoute(260),points=Array.isArray(route.points)?route.points:[];const sig=points.length?`${points.length}:${points[points.length-1].t}`:'0';if(force||sig!==lastRouteSignature){lastRouteSignature=sig;drawRouteMap('moveRealMap',points);}
  }
  function mercator(lat,lon,z){const scale=256*Math.pow(2,z),sin=Math.sin(Math.max(-85.0511,Math.min(85.0511,lat))*Math.PI/180);return {x:(lon+180)/360*scale,y:(.5-Math.log((1+sin)/(1-sin))/(4*Math.PI))*scale};}
  function chooseZoom(points,w,h){if(points.length<2)return 17;for(let z=18;z>=3;z--){const px=points.map(p=>mercator(p.lat,p.lon,z)),xs=px.map(p=>p.x),ys=px.map(p=>p.y);if(Math.max(...xs)-Math.min(...xs)<=Math.max(40,w-44)&&Math.max(...ys)-Math.min(...ys)<=Math.max(40,h-44))return z;}return 3;}
  function drawRouteMap(id,points){
    const el=document.getElementById(id);if(!el)return;points=(points||[]).filter(p=>Number.isFinite(Number(p.lat))&&Number.isFinite(Number(p.lon)));const w=Math.max(240,el.clientWidth||320),h=Math.max(150,el.clientHeight||180);if(!points.length){el.innerHTML='<div class="v2508-map-wait">GPS 수신 대기 중</div>';return;}
    const z=chooseZoom(points,w,h),px=points.map(p=>mercator(Number(p.lat),Number(p.lon),z));let minX=Math.min(...px.map(p=>p.x)),maxX=Math.max(...px.map(p=>p.x)),minY=Math.min(...px.map(p=>p.y)),maxY=Math.max(...px.map(p=>p.y)),cx=(minX+maxX)/2,cy=(minY+maxY)/2;if(points.length===1){cx=px[0].x;cy=px[0].y;}const left=cx-w/2,top=cy-h/2,tx0=Math.floor(left/256),tx1=Math.floor((left+w)/256),ty0=Math.floor(top/256),ty1=Math.floor((top+h)/256),tiles=Math.pow(2,z);let tileHtml='';
    for(let ty=ty0;ty<=ty1;ty++)for(let tx=tx0;tx<=tx1;tx++){if(ty<0||ty>=tiles)continue;const wrapped=((tx%tiles)+tiles)%tiles;tileHtml+=`<img class="v2508-tile" src="https://tile.openstreetmap.org/${z}/${wrapped}/${ty}.png" alt="" draggable="false" style="left:${Math.round(tx*256-left)}px;top:${Math.round(ty*256-top)}px">`;}
    const path=px.map(p=>`${(p.x-left).toFixed(1)},${(p.y-top).toFixed(1)}`).join(' '),start=px[0],end=px[px.length-1];el.innerHTML=`<div class="v2508-tile-layer">${tileHtml}</div><svg class="v2508-route-svg" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none"><polyline class="v2508-route-line-shadow" points="${path}"/><polyline class="v2508-route-line" points="${path}"/><circle class="v2508-route-start" cx="${(start.x-left).toFixed(1)}" cy="${(start.y-top).toFixed(1)}" r="6"/><circle class="v2508-route-current-ring" cx="${(end.x-left).toFixed(1)}" cy="${(end.y-top).toFixed(1)}" r="9"/><circle class="v2508-route-current" cx="${(end.x-left).toFixed(1)}" cy="${(end.y-top).toFixed(1)}" r="5"/></svg><div class="v2508-attribution">© OpenStreetMap contributors</div>`;
  }

  const previousRender=window.render;
  window.render=function(){if(view==='move'&&moveState==='glance'){realGlance();return;}if(view==='move'&&moveState==='finishing'){renderFinishing();return;}return previousRender.apply(this,arguments);};
  setInterval(()=>{
    tickCount++;const s=getState();latestState=s;
    if(view==='move'&&['recording','paused'].includes(moveState)){
      if(!s.recording)return;if(!!s.paused!==(moveState==='paused')){moveState=s.paused?'paused':'recording';render();return;}updateLiveDom(s);updateRoute(false);
    }else if(view==='move'&&moveState==='glance'&&s.recording){realGlance();}
  },POLL_MS);
})();
