/* v0.28.01 · Snow is real in developer mode only and hidden in release. */
(function(){
  'use strict';
  const AS='assets/';
  let lastSnowSessionId='';
  let lastSnowSummary=null;
  let selectedSnowRecordId='';
  let selectedSnowDescentIndex=-1;
  let snowStartPoll=0;
  let snowStopPoll=0;
  let snowLiveTick=0;

  function call(name,args,fallback){
    try{
      if(!window.YamoneSnow||typeof YamoneSnow[name]!=='function')return fallback;
      const raw=YamoneSnow[name].apply(YamoneSnow,args||[]);
      return typeof raw==='string'?JSON.parse(raw):raw;
    }catch(e){return fallback;}
  }
  function gate(){return call('getGateState',[],{developerBuild:false,unlocked:false,available:false,releaseExcluded:true,tapCount:0});}
  function available(){const g=gate();return !!(g.developerBuild&&g.unlocked&&g.available);}
  function state(){return call('getState',[],{available:false,recording:false,paused:false,state:'CHECKING',speedKmh:0,maxSpeedKmh:0,descentCount:0,liftCount:0,descentDistanceM:0,descentVerticalM:0,liftTimeMs:0,waitTimeMs:0});}
  function perms(){return call('getPermissionState',[],{fineLocation:false,backgroundLocation:false,notifications:false});}
  function settings(){return call('getSettings',[],{autoDetect:true,sport:'ski',offlineMaps:[]});}
  function snowRecords(){const r=call('listRecords',[200],{records:[]});return Array.isArray(r&&r.records)?r.records:[];}
  function detail(id,max){return call('getRecordDetail',[String(id||''),max||700],{found:false});}
  function storage(){return call('getStorageInfo',[],{bytes:0,records:0,offlineMaps:0,recording:false});}
  function cmd(name,args){return call(name,args||[],{ok:false,status:'bridge_unavailable'});}
  function n(v,d){const x=Number(v);return Number.isFinite(x)?x:(d||0);}
  function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
  function two(v){return String(v).padStart(2,'0');}
  function duration(ms){let s=Math.max(0,Math.floor(n(ms)/1000));const h=Math.floor(s/3600);s%=3600;const m=Math.floor(s/60),sec=s%60;if(h)return `${h}시간 ${m}분`;if(m)return `${m}분 ${two(sec)}초`;return `${sec}초`;}
  function clock(ms){let s=Math.max(0,Math.floor(n(ms)/1000));const h=Math.floor(s/3600);s%=3600;const m=Math.floor(s/60),sec=s%60;return `${two(h)}:${two(m)}:${two(sec)}`;}
  function km(m){return `${(Math.max(0,n(m))/1000).toFixed(n(m)<10000?2:1)} km`;}
  function speed(v){return `${Math.max(0,n(v)).toFixed(1)} km/h`;}
  function metres(v){return `${Math.round(Math.max(0,n(v))).toLocaleString()} m`;}
  function bytesText(v){let x=Math.max(0,n(v));if(x<1024)return `${Math.round(x)} B`;if(x<1024*1024)return `${(x/1024).toFixed(1)} KB`;if(x<1024*1024*1024)return `${(x/1024/1024).toFixed(x<100*1024*1024?1:0)} MB`;return `${(x/1024/1024/1024).toFixed(2)} GB`;}
  function dateText(ms){const d=new Date(n(ms));if(!Number.isFinite(d.getTime()))return '';const w=['일','월','화','수','목','금','토'][d.getDay()];return `${d.getFullYear()}년 ${d.getMonth()+1}월 ${d.getDate()}일 (${w}) ${two(d.getHours())}:${two(d.getMinutes())}`;}
  function timeText(ms){const d=new Date(n(ms));return Number.isFinite(d.getTime())?`${two(d.getHours())}:${two(d.getMinutes())}:${two(d.getSeconds())}`:'';}

  function hideSnowWhenUnavailable(){
    if(available())return;
    screen.querySelectorAll('[data-name="Snow"],[data-setting="Snow"]').forEach(el=>el.remove());
    const snowFilter=screen.querySelector('[data-record-filter="snow"]');if(snowFilter)snowFilter.remove();
  }

  /* Activity/settings gate. The native bridge is not registered at all in release. */
  const previousRenderActivity=window.renderActivity;
  window.renderActivity=function(){
    previousRenderActivity.apply(this,arguments);
    if(!available()){hideSnowWhenUnavailable();return;}
    const card=screen.querySelector('[data-name="Snow"]');
    if(card&&!card.querySelector('.v02801-dev-badge')){
      const copy=card.querySelector('.activity-copy');if(copy)copy.insertAdjacentHTML('beforeend','<span class="v02801-dev-badge">개발자 전용</span>');
    }
  };

  const previousRenderSettings=window.renderSettings;
  window.renderSettings=function(){
    previousRenderSettings.apply(this,arguments);
    if(!available()){hideSnowWhenUnavailable();return;}
    const card=screen.querySelector('[data-setting="Snow"]');
    if(card&&!card.querySelector('.v02801-dev-badge')){
      const copy=card.querySelector('.setting-copy');if(copy)copy.insertAdjacentHTML('beforeend','<span class="v02801-dev-badge">개발</span>');
    }
  };

  /* Actual persisted developer unlock through the existing hidden App Info gesture. */
  window.renderAppInfoSettings=function(){
    const g=gate();
    const cfg=settings();
    const st=state();
    const mapCount=Array.isArray(cfg.offlineMaps)?cfg.offlineMaps.length:0;
    screen.innerHTML=`
      ${settingHeader('앱 정보')}
      <button id="appVersionTap" class="card app-info-card">
        <img src="${AS}nav-settings.png"><div><b>야모네 활동 앱</b><small>Version 0.28.01</small></div>
      </button>
      <div class="setting-action-stack">
        <button class="card setting-wide-action"><b>이용 안내</b><span>›</span></button>
        <button class="card setting-wide-action"><b>개인정보 안내</b><span>›</span></button>
        <button class="card setting-wide-action"><b>오픈소스 라이선스</b><span>›</span></button>
      </div>
      ${g.developerBuild&&g.unlocked?`
        <div class="section">개발자 모드</div>
        <div class="card setting-block v02801-dev-panel">
          <div class="v02801-dev-only-mark"><span class="v02801-dev-badge">DEV</span> Snow 기능은 개발 빌드에서만 활성화됩니다.</div>
          <button id="v02801OpenSnow" class="primary">Snow 기능 열기</button>
          <button id="v02801InstallMap">개발 테스트 오프라인 지도 ${mapCount?'확인':'설치'}</button>
          <button id="v02801SimSnow">${st.recording&&st.simulation?'Snow 시뮬레이션 실행 중':'Snow 실시간 시뮬레이션 시작'}</button>
          <button id="v02801LockDev" class="danger" ${st.recording?'disabled':''}>개발자 모드 잠금</button>
        </div>
        <div class="setting-info">실제 출시 빌드에서는 Snow 진입 화면·서비스·자동감지 수신기가 비활성화됩니다.</div>`:''}`;
    bindSettingCommon();
    const tap=document.getElementById('appVersionTap');
    if(tap)tap.onclick=()=>{
      const r=cmd('registerDeveloperTap');
      if(!g.developerBuild)return;
      if(r.unlocked){if(typeof say==='function')say('개발자 모드가 활성화되었습니다.');renderAppInfoSettings();}
      else if(n(r.remaining)<=3&&typeof say==='function')say(`개발자 모드까지 ${Math.max(0,n(r.remaining))}번 남았습니다.`);
    };
    const open=document.getElementById('v02801OpenSnow');if(open)open.onclick=()=>openSnow();
    const map=document.getElementById('v02801InstallMap');if(map)map.onclick=()=>{const r=cmd('installDeveloperTestMap');if(r.ok&&typeof say==='function')say('개발 테스트 오프라인 지도를 준비했습니다.');renderAppInfoSettings();};
    const sim=document.getElementById('v02801SimSnow');if(sim)sim.onclick=()=>{openSnow();setTimeout(()=>startSnow(true),80);};
    const lock=document.getElementById('v02801LockDev');if(lock&&!st.recording)lock.onclick=()=>{cmd('lockDeveloperMode');view='main';tab='settings';settingPage=null;setFocusMode(false);syncTabs();render();};
  };

  /* Real Snow setting page: actual geofence toggle, permissions and local package manager. */
  window.renderSnowSettings=function(){
    if(!available()){settingPage=null;view='main';tab='settings';setFocusMode(false);syncTabs();render();return;}
    const cfg=settings(),p=perms(),maps=Array.isArray(cfg.offlineMaps)?cfg.offlineMaps:[];
    const mapRows=maps.length?maps.map(m=>`<div class="card v02801-map-row"><img src="${AS}route.png"><div><b>${esc(m.resortName||m.resortKey)}</b><small>지도 v${n(m.mapVersion,1)} · ${m.developerTest?'개발 테스트 패키지':'OSM 오프라인 패키지'} · ${bytesText(m.bytes)}</small></div><strong>설치됨</strong></div>`).join(''):`<div class="setting-info">설치된 Snow 오프라인 지도가 없습니다.</div>`;
    screen.innerHTML=`
      ${settingHeader('Snow')}
      <div class="v02801-dev-only-mark"><span class="v02801-dev-badge">DEV</span> 현장 테스트 전 개발자 전용</div>
      ${settingToggle('v02801SnowAuto','스키장 자동감지','설치된 오프라인 지도 경계 진입을 감지',!!cfg.autoDetect,'settings-snow')}
      ${!p.backgroundLocation&&cfg.autoDetect?`<div class="v02801-permission-warning">백그라운드 위치 권한이 있어야 앱이 화면 밖에 있을 때 스키장 경계 진입을 감지할 수 있습니다.<br><button id="v02801LocationSettings" class="v02801-ready-secondary">위치 권한 설정 열기</button></div>`:''}
      <div class="setting-section-title">오프라인 지도</div>
      ${mapRows}
      <div class="v02801-map-actions"><button id="v02801InstallDevMap">개발 테스트 지도 설치</button><button id="v02801DeleteDevMap" ${maps.some(m=>m.resortKey==='yamone-dev-snow')?'':'disabled'}>테스트 지도 삭제</button></div>
      <div class="setting-info">실제 지도는 공개 OSM 타일을 저장하지 않고 OSM 원본에서 스키장 영역만 추출한 전용 오프라인 패키지를 사용하도록 구성했습니다. 실제 스키장 패키지는 현장 테스트 단계에서 추가합니다.</div>`;
    const back=document.getElementById('settingBack');if(back)back.onclick=()=>{settingPage=null;view='main';tab='settings';setFocusMode(false);syncTabs();render();};
    const toggle=screen.querySelector('[data-setting-toggle="v02801SnowAuto"]');if(toggle)toggle.onclick=()=>{cmd('saveSettings',[JSON.stringify({autoDetect:!cfg.autoDetect})]);renderSnowSettings();};
    const loc=document.getElementById('v02801LocationSettings');if(loc)loc.onclick=()=>cmd('openLocationSettings');
    document.getElementById('v02801InstallDevMap').onclick=()=>{const r=cmd('installDeveloperTestMap');if(r.ok&&typeof say==='function')say('개발 테스트 지도를 설치했습니다.');renderSnowSettings();};
    const del=document.getElementById('v02801DeleteDevMap');if(del&&!del.disabled)del.onclick=()=>confirmSnow('개발 테스트 지도를 삭제할까요?','Snow 기록은 유지되고 테스트 지도 패키지만 삭제됩니다.','지도 삭제',()=>{cmd('deleteOfflineMap',['yamone-dev-snow']);renderSnowSettings();});
  };

  /* Keep Snow out of general storage UI in release/locked mode; use actual values in dev mode. */
  const previousDataSettings=window.renderDataSettings;
  window.renderDataSettings=function(){
    previousDataSettings.apply(this,arguments);
    const row=screen.querySelector('[data-delete-kind="Snow"]');
    const snowCell=[...screen.querySelectorAll('.storage-card>div')].find(x=>x.querySelector('span')&&x.querySelector('span').textContent.trim()==='Snow');
    if(!available()){
      if(row)row.remove();if(snowCell)snowCell.remove();return;
    }
    const st=storage();
    if(snowCell){const strong=snowCell.querySelector('strong');if(strong)strong.textContent=bytesText(st.bytes);}
    if(row)row.onclick=()=>confirmSnow('Snow 기록을 모두 삭제할까요?','저장된 모든 Snow 기록이 삭제됩니다. 오프라인 지도는 유지됩니다.','전체 삭제',()=>{const r=cmd('deleteAllRecords');if(r.ok){if(typeof say==='function')say('Snow 기록을 모두 삭제했습니다.');renderDataSettings();}});
  };

  function openSnow(){
    if(!available()){if(typeof say==='function')say('개발자 모드에서만 Snow를 사용할 수 있습니다.');return false;}
    settingPage=null;view='snow';const s=state();snowState=s.recording?(s.paused?'paused':'recording'):'ready';tab='activity';setFocusMode(true);syncTabs();render();return true;
  }
  window.yamoneOpenSnow=openSnow;

  function gpsStatus(s){
    if(s.simulation)return '시뮬레이션';
    const age=Number(s.fixAgeMs),acc=Number(s.accuracyM);
    if(!Number.isFinite(age)||!Number.isFinite(acc))return '미확인';
    if(age<=10000&&acc<=20)return '양호';
    if(age<=30000&&acc<=40)return '보통';
    return '불안정';
  }
  function motionLabel(s){if(s.paused)return '일시정지';if(s.state==='DESCENT')return '활주 중';if(s.state==='LIFT')return '리프트 탑승';if(s.state==='STOPPED')return '정지';return '움직임 판별 중';}
  function motionKey(s){if(s.paused)return 'paused';if(s.state==='DESCENT')return 'downhill';if(s.state==='LIFT')return 'lift';if(s.state==='STOPPED')return 'stationary';return 'checking';}

  window.snowReady=function(){
    if(!available()){view='main';snowState='ready';tab='activity';setFocusMode(false);syncTabs();render();return;}
    setFocusMode(true);
    const s=state();if(s.recording){snowState=s.paused?'paused':'recording';requestAnimationFrame(()=>render());return;}
    const cfg=settings(),p=perms();
    if(p.fineLocation)cmd('detectCurrentResort');
    const now=state();
    const resort=now.resortName||'스키장 미확인';
    const mapReady=!!now.mapInstalled;
    const sport=cfg.sport==='snowboard'?'snowboard':'ski';
    screen.innerHTML=`
      ${snowTop(false)}
      <div class="v02801-dev-only-mark"><span class="v02801-dev-badge">DEV</span> Snow 현장 테스트 전 개발 기능</div>
      <section class="card snow-hero"><img src="${AS}activity-snow.png" alt=""><div class="snow-hero-copy"><h2>Snow 시작</h2></div></section>
      <div class="move-section">종목</div>
      <div class="v02801-snow-sport"><button data-v02801-sport="ski" class="${sport==='ski'?'active':''}">스키</button><button data-v02801-sport="snowboard" class="${sport==='snowboard'?'active':''}">스노보드</button></div>
      <div class="move-section">현재 스키장</div>
      <div class="card snow-resort-card"><img src="${AS}activity-snow.png" alt=""><div><b>${esc(resort)}</b><strong>${now.resortName?'경계/기록 준비됨':'GPS 기록은 시작 가능'}</strong></div></div>
      <div class="move-section">준비 상태</div>
      <div class="snow-status-grid">
        <div class="card snow-status"><img src="${AS}gps.png"><div><b>위치</b><strong>${p.fineLocation?'권한 있음':'권한 필요'}</strong></div></div>
        <div class="card snow-status"><img src="${AS}route.png"><div><b>오프라인 지도</b><strong>${mapReady?'준비됨':'미설치'}</strong></div></div>
      </div>
      <div class="snow-note">같은 스키장·같은 날짜에 여러 번 기록해도 종료 시 하루 기록 하나로 합산합니다. 지도 패키지가 없으면 경로는 저장하지만 슬로프 이름은 미확인으로 남습니다.</div>
      ${cfg.autoDetect?'<div class="v02801-gate-note">스키장 자동감지 ON · 설치된 지도 경계 기준</div>':''}
      <div class="v02801-ready-actions"><button id="snowStart" class="snow-start">${sport==='snowboard'?'스노보드':'스키'} 기록 시작</button><button id="v02801SnowSim" class="v02801-ready-secondary">개발용 Snow 시뮬레이션</button></div>`;
    bindSnowBack();
    screen.querySelectorAll('[data-v02801-sport]').forEach(b=>b.onclick=()=>{cmd('saveSettings',[JSON.stringify({sport:b.dataset.v02801Sport})]);snowReady();});
    document.getElementById('snowStart').onclick=()=>startSnow(false);
    document.getElementById('v02801SnowSim').onclick=()=>startSnow(true);
  };

  function startSnow(simulation){
    if(!available())return;
    const p=perms();
    if(!p.fineLocation){cmd('requestCorePermissions');if(typeof say==='function')say('Snow 기록에는 위치 권한이 필요합니다. 권한을 허용한 뒤 다시 시작해주세요.');return;}
    const cfg=settings();
    const r=cmd(simulation?'startDeveloperSimulation':'start',[cfg.sport||'ski']);
    if(!r.ok){if(typeof say==='function')say(r.status==='location_permission_required'?'위치 권한이 필요합니다.':'Snow 기록을 시작하지 못했습니다.');return;}
    clearInterval(snowStartPoll);let tries=0;
    snowStartPoll=setInterval(()=>{const s=state();if(s.recording){clearInterval(snowStartPoll);snowStartPoll=0;lastSnowSessionId=s.sessionId||'';snowState=s.paused?'paused':'recording';render();}else if(++tries>40){clearInterval(snowStartPoll);snowStartPoll=0;if(typeof say==='function')say('Snow 기록 시작을 확인하지 못했습니다.');}},250);
  }

  function liveMapPayload(){return call('getActiveRoute',[450],{points:[],mapManifest:{},mapData:{}});}
  window.snowLive=function(){
    if(!available()){openSnow();return;}
    setFocusMode(true);
    const s=state();if(!s.recording){snowState='ready';snowReady();return;}
    if(s.sessionId)lastSnowSessionId=s.sessionId;
    const route=liveMapPayload();
    const mk=motionKey(s),label=motionLabel(s),gps=gpsStatus(s);
    screen.innerHTML=`
      ${snowTop(true)}
      <div class="v02801-dev-only-mark"><span class="v02801-dev-badge">DEV</span> ${s.simulation?'개발 시뮬레이션':'실제 GPS 기록'}</div>
      <section class="card snow-live-card v02801-snow-live">
        <div class="snow-live-head"><div class="snow-live-left"><img src="${AS}activity-snow.png"><div><h2>${esc(label)}</h2></div></div><span class="snow-state-pill">${s.paused?'일시정지':'Snow 기록 중'}</span></div>
        <div class="v02801-snow-state-strip"><span class="${mk==='downhill'?'active':''}">활주</span><span class="${mk==='lift'?'active':''}">리프트</span><span class="${mk==='stationary'?'active':''}">정지</span><span class="${mk==='checking'?'active':''}">판별 중</span></div>
        <div class="snow-primary"><div class="snow-primary-head"><img src="${AS}speed.png"><b>${mk==='downhill'?'현재 속도':mk==='lift'?'리프트 상태':'현재 상태'}</b></div><div id="v02801SnowPrimary" class="snow-primary-value">${mk==='downhill'?speed(s.speedKmh):mk==='lift'?'탑승 중':esc(label)}</div><div id="v02801SnowSub" class="snow-primary-sub">최고 속도 ${speed(s.maxSpeedKmh)} · GPS ${gps}</div></div>
        <div class="snow-metrics"><div class="card snow-metric"><img src="${AS}time.png"><span>시간</span><strong id="v02801SnowTime">${clock(s.activeElapsedMs)}</strong></div><div class="card snow-metric"><img src="${AS}distance.png"><span>활주 거리</span><strong id="v02801SnowDistance">${km(s.descentDistanceM)}</strong></div><div class="card snow-metric"><img src="${AS}activity-snow.png"><span>활주</span><strong id="v02801SnowRuns">${n(s.descentCount)}회</strong></div></div>
        <div class="card snow-map"><div><b>스키장 지도 · 실제 경로</b><small>${esc(s.resortName||'스키장 미확인')} · GPS ${gps}</small></div><div id="v02801LiveRoute">${routeMarkup(route)}</div></div>
        <div class="card snow-run-card"><h3>오늘 기록</h3><div class="snow-run-row"><div><b>활주</b><small>다운힐 누적</small></div><strong id="v02801RunRow">${n(s.descentCount)}회 · ${km(s.descentDistanceM)}</strong></div><div class="snow-run-row"><div><b>리프트</b><small>탑승 누적</small></div><strong id="v02801LiftRow">${n(s.liftCount)}회 · ${clock(s.liftTimeMs)}</strong></div><div class="snow-run-row"><div><b>하강고도</b><small>다운힐 누적</small></div><strong id="v02801VerticalRow">${metres(s.descentVerticalM)}</strong></div></div>
        <div class="snow-controls">${s.paused?`<button id="snowResume" class="snow-control resume"><img src="${AS}resume.png"><b>다시 시작</b><small>기록 재개</small></button>`:`<button id="snowPause" class="snow-control pause"><img src="${AS}pause.png"><b>일시정지</b><small>잠시 멈춤</small></button>`}<button id="snowStop" class="snow-control stop"><img src="${AS}stop.png"><b>Snow 종료</b><small>요약 후 저장</small></button><button class="snow-control"><img src="${AS}gps.png"><b>GPS</b><small id="v02801Gps">${gps}</small></button></div>
      </section>`;
    bindSnowBack();
    const p=document.getElementById('snowPause'),r=document.getElementById('snowResume');
    if(p)p.onclick=()=>{cmd('pause');setTimeout(()=>{snowState='paused';render();},180);};
    if(r)r.onclick=()=>{cmd('resume');setTimeout(()=>{snowState='recording';render();},180);};
    document.getElementById('snowStop').onclick=openSnowStop;
  };

  function updateSnowLive(){
    if(typeof view==='undefined'||view!=='snow'||!['recording','paused'].includes(snowState))return;
    const s=state();if(!s.recording)return;
    const mk=motionKey(s),label=motionLabel(s),gps=gpsStatus(s);
    const set=(id,text)=>{const el=document.getElementById(id);if(el&&el.textContent!==text)el.textContent=text;};
    set('v02801SnowPrimary',mk==='downhill'?speed(s.speedKmh):mk==='lift'?'탑승 중':label);
    set('v02801SnowSub',`최고 속도 ${speed(s.maxSpeedKmh)} · GPS ${gps}`);
    set('v02801SnowTime',clock(s.activeElapsedMs));set('v02801SnowDistance',km(s.descentDistanceM));set('v02801SnowRuns',`${n(s.descentCount)}회`);
    set('v02801RunRow',`${n(s.descentCount)}회 · ${km(s.descentDistanceM)}`);set('v02801LiftRow',`${n(s.liftCount)}회 · ${clock(s.liftTimeMs)}`);set('v02801VerticalRow',metres(s.descentVerticalM));set('v02801Gps',gps);
    if(++snowLiveTick%5===0){const box=document.getElementById('v02801LiveRoute');if(box)box.innerHTML=routeMarkup(liveMapPayload());}
  }

  window.openSnowStop=function(){
    if(!available())return;
    moveOverlay.dataset.v02801SnowConfirm='1';moveOverlay.classList.add('show');
    moveOverlay.innerHTML=`<div class="move-sheet"><div class="move-sheet-handle"></div><h3>Snow 기록을 종료할까요?</h3><p>종료하면 실제 GPS 기록을 마감하고 오늘 Snow 요약으로 이동합니다.</p><div class="move-sheet-actions"><button id="snowKeep" class="move-sheet-cancel">계속 기록</button><button id="snowConfirm" class="move-sheet-stop">종료</button></div></div>`;
    document.getElementById('snowKeep').onclick=closeSnowConfirm;
    document.getElementById('snowConfirm').onclick=finishSnow;
    moveOverlay.onclick=e=>{if(e.target===moveOverlay)closeSnowConfirm();};
  };
  function closeSnowConfirm(){delete moveOverlay.dataset.v02801SnowConfirm;if(typeof closeMoveOverlay==='function')closeMoveOverlay();else{moveOverlay.classList.remove('show');moveOverlay.innerHTML='';}}
  function finishSnow(){
    const before=state();if(before.sessionId)lastSnowSessionId=before.sessionId;closeSnowConfirm();const r=cmd('stop');if(r.sessionId)lastSnowSessionId=r.sessionId;
    clearInterval(snowStopPoll);let tries=0;
    snowStopPoll=setInterval(()=>{const s=state();if(!s.recording){const d=detail(lastSnowSessionId,900);if(d&&d.found){clearInterval(snowStopPoll);snowStopPoll=0;lastSnowSummary=d;snowState='summary';render();return;}}if(++tries>80){clearInterval(snowStopPoll);snowStopPoll=0;const d=detail(lastSnowSessionId,900);lastSnowSummary=d&&d.found?d:null;snowState=lastSnowSummary?'summary':'ready';render();}},250);
  }

  window.snowSummary=function(){
    if(!available()){openSnow();return;}
    setFocusMode(true);
    const d=(lastSnowSummary&&lastSnowSummary.found)?lastSnowSummary:detail(lastSnowSessionId,900);
    if(!d||!d.found){snowState='ready';snowReady();return;}
    lastSnowSummary=d;
    screen.innerHTML=`
      ${snowTop(true)}
      <section class="card snow-summary-top"><img src="${AS}activity-snow.png"><h2>오늘 Snow 기록 완료</h2></section>
      <div class="v02801-summary-meta">${d.developerSimulated?'<span class="v02801-dev-badge">시뮬레이션</span>':''}<span>${esc(d.resortName||'스키장 미확인')} · ${d.mergedSessionCount>1?`${d.mergedSessionCount}개 기록 합산`:'오늘 기록'}</span></div>
      <div class="snow-summary-grid"><div class="card snow-summary-item"><img src="${AS}time.png"><div><b>활동 시간</b><strong>${duration(d.activeDurationMs)}</strong></div></div><div class="card snow-summary-item"><img src="${AS}distance.png"><div><b>활주 거리</b><strong>${km(d.descentDistanceM)}</strong></div></div><div class="card snow-summary-item"><img src="${AS}speed.png"><div><b>최고 속도</b><strong>${speed(d.maxSpeedKmh)}</strong></div></div><div class="card snow-summary-item"><img src="${AS}activity-snow.png"><div><b>활주 횟수</b><strong>${n(d.descentCount)}회</strong></div></div><div class="card snow-summary-item"><img src="${AS}activity-snow.png"><div><b>리프트</b><strong>${n(d.liftCount)}회</strong></div></div><div class="card snow-summary-item"><img src="${AS}route.png"><div><b>누적 하강</b><strong>${metres(d.descentVerticalM)}</strong></div></div></div>
      <div class="snow-note">같은 스키장·같은 날짜의 여러 기록은 종료 시 하루 기록 하나로 합산합니다.</div>
      <button id="snowSave" class="snow-save">확인</button>`;
    bindSnowBack();
    document.getElementById('snowSave').onclick=()=>{lastSnowSummary=null;view='main';snowState='ready';tab='records';recordFilter='snow';setFocusMode(false);syncTabs();render();};
  };

  window.bindSnowBack=function(){
    const back=document.getElementById('snowBack');if(!back)return;
    back.onclick=()=>{view='main';snowState='ready';setFocusMode(false);tab='activity';syncTabs();render();};
  };

  /* Real record list merged with the existing real movement and sleep lists. */
  function snowRecordNode(r){
    const title=r.resortName||'Snow';const sport=r.sport==='snowboard'?'스노보드':'스키';
    return `<button class="card record record-open v02801-snow-record" data-v02801-snow="${esc(r.sessionId)}"><img src="${AS}activity-snow.png" alt=""><div class="record-copy"><b>${esc(title)}${r.developerSimulated?' (DEV)':''}</b><small>${esc(dateText(r.startEpochMs))} · ${sport}</small></div><div class="record-value"><b>${km(r.descentDistanceM)}</b><small>활주 ${n(r.descentCount)}회</small></div></button>`;
  }
  function parseJson(raw,fallback){try{return typeof raw==='string'?JSON.parse(raw):raw;}catch(e){return fallback;}}
  function startMapForExisting(){
    const out=new Map();
    try{if(window.YamoneRecords){const p=parseJson(YamoneRecords.getSavedRecords(200),{records:[]});(p.records||[]).forEach(r=>out.set(`m:${r.sessionId}`,n(r.startEpochMs)));}}catch(e){}
    try{if(window.YamoneSleep){const p=parseJson(YamoneSleep.listRecords(200),{records:[]});(p.records||[]).forEach(r=>out.set(`s:${r.sessionId}`,n(r.startEpochMs)));}}catch(e){}
    snowRecords().forEach(r=>out.set(`n:${r.sessionId}`,n(r.startEpochMs)));
    return out;
  }
  const previousRenderRecords=window.renderRecords;
  window.renderRecords=function(){
    if(!available()&&recordFilter==='snow')recordFilter='all';
    previousRenderRecords.apply(this,arguments);
    const filter=screen.querySelector('[data-record-filter="snow"]');
    if(!available()){if(filter)filter.remove();return;}
    const rs=snowRecords();
    const section=[...screen.querySelectorAll('.section')].find(x=>x.textContent.trim()==='저장된 기록');if(!section)return;
    if(recordFilter==='snow'){
      screen.querySelectorAll('[data-v2513-saved],[data-v02602-sleep]').forEach(x=>x.remove());
      const empty=screen.querySelector('.record-empty');if(empty)empty.remove();
      if(!rs.length){section.insertAdjacentHTML('afterend','<div class="record-empty">표시할 Snow 기록이 없습니다.</div>');}
      else{const frag=document.createDocumentFragment();rs.forEach(r=>{const t=document.createElement('template');t.innerHTML=snowRecordNode(r).trim();frag.appendChild(t.content.firstElementChild);});section.after(frag);}
    }else if(recordFilter==='all'){
      const frag=document.createDocumentFragment();rs.forEach(r=>{const t=document.createElement('template');t.innerHTML=snowRecordNode(r).trim();frag.appendChild(t.content.firstElementChild);});section.after(frag);
      const starts=startMapForExisting();const nodes=[...screen.querySelectorAll('[data-v2513-saved],[data-v02602-sleep],[data-v02801-snow]')];
      nodes.sort((a,b)=>{
        const ka=a.dataset.v2513Saved?`m:${a.dataset.v2513Saved}`:a.dataset.v02602Sleep?`s:${a.dataset.v02602Sleep}`:`n:${a.dataset.v02801Snow}`;
        const kb=b.dataset.v2513Saved?`m:${b.dataset.v2513Saved}`:b.dataset.v02602Sleep?`s:${b.dataset.v02602Sleep}`:`n:${b.dataset.v02801Snow}`;
        return (starts.get(kb)||0)-(starts.get(ka)||0);
      });
      const sorted=document.createDocumentFragment();nodes.forEach(x=>sorted.appendChild(x));section.after(sorted);
      const empty=screen.querySelector('.record-empty');if(empty&&nodes.length)empty.remove();
    }
    screen.querySelectorAll('[data-v02801-snow]').forEach(b=>b.onclick=()=>{selectedSnowRecordId=b.dataset.v02801Snow;selectedSnowDescentIndex=-1;view='saved-snow-detail';render();});
  };

  function classificationClass(c){return c&&c.type==='TREE_OR_OUTSIDE'?'tree':'';}
  function classificationLabel(c){return c&&c.label?c.label:'미확인 활주';}
  function renderSnowRecordDetail(){
    const d=detail(selectedSnowRecordId,1000);if(!d||!d.found){backSnowRecords();return;}
    setFocusMode(false);
    const desc=Array.isArray(d.descents)?d.descents:[],lifts=Array.isArray(d.lifts)?d.lifts:[];
    const descHtml=desc.length?desc.map((x,i)=>`<button class="v02801-real-descent" data-v02801-descent="${i}"><div class="v02801-real-descent-top"><b>활주 ${i+1}</b><span>${timeText(x.startMs)} → ${timeText(x.endMs)}</span></div><div class="v02801-real-descent-metrics"><span>거리<b>${km(x.distanceM)}</b></span><span>평균<b>${speed(x.avgSpeedKmh)}</b></span><span>최고<b>${speed(x.maxSpeedKmh)}</b></span><span>하강<b>${metres(x.verticalM)}</b></span></div><span class="v02801-classification ${classificationClass(x.classification)}">${esc(classificationLabel(x.classification))}</span></button>`).join(''):'<div class="record-empty">판별된 활주 구간이 없습니다.</div>';
    const liftsHtml=lifts.length?lifts.map((x,i)=>`<div class="v02801-lift-row"><div><b>${esc(x.liftName||`미확인 리프트 ${i+1}`)}</b><small>탑승 ${duration(x.rideDurationMs)}${n(x.waitDurationMs)>0?` · 대기 ${duration(x.waitDurationMs)}`:''}</small></div><strong>${metres(x.ascentM)}</strong></div>`).join(''):'<div class="record-empty">판별된 리프트 이용이 없습니다.</div>';
    screen.innerHTML=`<div class="v02801-snow-detail"><div class="move-sticky"><div class="move-header-row"><button id="v02801SnowRecordBack" class="move-back-asset-btn"><img class="move-back-asset" src="${AS}back-v02402.png" alt="뒤로가기"></button><div class="move-plain-title">Snow 기록 상세</div></div></div><div class="record-detail-summary"><div><b>${esc(d.resortName||'Snow 기록')}${d.developerSimulated?' (DEV)':''}</b><small>${esc(dateText(d.startEpochMs))}</small></div><span>${d.sport==='snowboard'?'스노보드':'스키'}</span></div><div class="detail-stat-grid v24-list-grid"><div class="card"><span>활동 시간</span><b>${duration(d.activeDurationMs)}</b></div><div class="card"><span>활주 거리</span><b>${km(d.descentDistanceM)}</b></div><div class="card"><span>활주 횟수</span><b>${n(d.descentCount)}회</b></div><div class="card"><span>리프트</span><b>${n(d.liftCount)}회</b></div><div class="card"><span>누적 하강</span><b>${metres(d.descentVerticalM)}</b></div><div class="card"><span>최고 속도</span><b>${speed(d.maxSpeedKmh)}</b></div></div><div class="detail-section-head"><b>전체 경로</b><small>${d.mapInstalled?'오프라인 지도 패키지 사용':'GPS 경로만 표시'}</small></div><div class="card">${routeMarkup({points:d.route,mapManifest:d.mapManifest,mapData:d.mapData})}</div><div class="detail-section-head"><b>활주별 기록</b><small>활주를 누르면 실제 구간 경로 보기</small></div><div class="card snow-descents-card">${descHtml}</div><div class="detail-section-head"><b>리프트 이용</b><small>GPS 기반 자동 판별</small></div><div class="card v02801-lift-list">${liftsHtml}</div><div class="setting-info">슬로프 이름은 설치된 오프라인 지도와 가까운 활주만 연결합니다. 경계 안 미매칭은 ‘미확인 활주’, 경계 밖은 ‘트리런 / 경계 밖 미확인’으로 보존합니다.</div><button id="v02801DeleteSnowRecord" class="v02801-danger">이 Snow 기록 삭제</button></div>`;
    document.getElementById('v02801SnowRecordBack').onclick=backSnowRecords;
    screen.querySelectorAll('[data-v02801-descent]').forEach(b=>b.onclick=()=>{selectedSnowDescentIndex=Number(b.dataset.v02801Descent);view='saved-snow-descent';render();});
    document.getElementById('v02801DeleteSnowRecord').onclick=()=>confirmSnow('Snow 기록을 삭제할까요?','이 기록의 GPS 경로와 활주·리프트 판별 데이터가 삭제됩니다.','기록 삭제',()=>{const r=cmd('deleteRecord',[selectedSnowRecordId]);if(r.ok)backSnowRecords();});
  }

  function renderSnowDescentDetail(){
    const d=detail(selectedSnowRecordId,1000);const desc=Array.isArray(d&&d.descents)?d.descents:[];const x=desc[selectedSnowDescentIndex];if(!d||!d.found||!x){view='saved-snow-detail';render();return;}
    setFocusMode(false);
    screen.innerHTML=`<div class="v02801-snow-detail"><div class="move-sticky"><div class="move-header-row"><button id="v02801DescentBack" class="move-back-asset-btn"><img class="move-back-asset" src="${AS}back-v02402.png" alt="뒤로가기"></button><div class="move-plain-title">활주 ${selectedSnowDescentIndex+1}</div></div></div><div class="record-detail-summary"><div><b>${esc(classificationLabel(x.classification))}</b><small>${timeText(x.startMs)} → ${timeText(x.endMs)} · ${duration(x.durationMs)}</small></div><span>활주</span></div><div class="detail-stat-grid v24-list-grid"><div class="card"><span>거리</span><b>${km(x.distanceM)}</b></div><div class="card"><span>평균 속도</span><b>${speed(x.avgSpeedKmh)}</b></div><div class="card"><span>최고 속도</span><b>${speed(x.maxSpeedKmh)}</b></div><div class="card"><span>하강고도</span><b>${metres(x.verticalM)}</b></div></div><div class="detail-section-head"><b>활주 경로</b><small>실제 GPS 순서</small></div><div class="card">${routeMarkup({points:x.route,mapManifest:d.mapManifest,mapData:d.mapData})}</div><span class="v02801-classification ${classificationClass(x.classification)}">${esc(classificationLabel(x.classification))}</span></div>`;
    document.getElementById('v02801DescentBack').onclick=()=>{view='saved-snow-detail';render();};
  }

  function backSnowRecords(){selectedSnowRecordId='';selectedSnowDescentIndex=-1;view='main';tab='records';recordFilter='snow';setFocusMode(false);syncTabs();render();}

  function routeMarkup(payload){
    const points=Array.isArray(payload&&payload.points)?payload.points:[];const map=payload&&payload.mapData&&typeof payload.mapData==='object'?payload.mapData:{};
    const trails=Array.isArray(map.trails)?map.trails:[],lifts=Array.isArray(map.lifts)?map.lifts:[];
    const coords=[];points.forEach(p=>{if(Number.isFinite(Number(p.lat))&&Number.isFinite(Number(p.lon)))coords.push([Number(p.lat),Number(p.lon)]);});
    [...trails,...lifts].forEach(f=>(Array.isArray(f.points)?f.points:[]).forEach(p=>{if(Array.isArray(p)&&p.length>=2)coords.push([Number(p[0]),Number(p[1])]);}));
    if(!coords.length)return '<div class="v02801-map-empty">아직 표시할 GPS 경로가 없습니다.<br>기록이 쌓이면 실제 위치 순서대로 표시됩니다.</div>';
    let minLat=Math.min(...coords.map(p=>p[0])),maxLat=Math.max(...coords.map(p=>p[0])),minLon=Math.min(...coords.map(p=>p[1])),maxLon=Math.max(...coords.map(p=>p[1]));
    if(maxLat-minLat<1e-6){maxLat+=.0001;minLat-=.0001}if(maxLon-minLon<1e-6){maxLon+=.0001;minLon-=.0001}
    const xy=(lat,lon)=>[5+90*(lon-minLon)/(maxLon-minLon),95-90*(lat-minLat)/(maxLat-minLat)];
    const poly=arr=>(arr||[]).map(p=>{const q=Array.isArray(p)?xy(Number(p[0]),Number(p[1])):xy(Number(p.lat),Number(p.lon));return `${q[0].toFixed(2)},${q[1].toFixed(2)}`;}).join(' ');
    const features=trails.map(f=>`<polyline class="trail" points="${poly(f.points)}"/>`).join('')+lifts.map(f=>`<polyline class="lift" points="${poly(f.points)}"/>`).join('');
    const groups=[];let current=[];let currentState='';points.forEach(p=>{const st=String(p.state||'CHECKING');if(current.length&&st!==currentState){groups.push([currentState,current]);current=[];}currentState=st;current.push(p);});if(current.length)groups.push([currentState,current]);
    const cls=st=>st==='DESCENT'?'route-descent':st==='LIFT'?'route-lift':st==='STOPPED'?'route-stopped':'route-checking';
    const routes=groups.filter(g=>g[1].length>1).map(g=>`<polyline class="${cls(g[0])}" points="${poly(g[1])}"/>`).join('');
    const first=points[0],last=points[points.length-1];const fxy=first?xy(Number(first.lat),Number(first.lon)):null,lxy=last?xy(Number(last.lat),Number(last.lon)):null;
    const dots=(fxy?`<circle class="start-dot" cx="${fxy[0]}" cy="${fxy[1]}" r="2.2"/>`:'')+(lxy?`<circle class="end-dot" cx="${lxy[0]}" cy="${lxy[1]}" r="2.2"/>`:'');
    const manifest=payload&&payload.mapManifest||{};const caption=manifest&&manifest.resortName?`${manifest.resortName} · 지도 v${n(manifest.mapVersion,1)}`:'오프라인 지도 미설치';
    return `<div class="v02801-route-map"><svg viewBox="0 0 100 100" preserveAspectRatio="none">${features}${routes}${dots}</svg></div><div class="v02801-map-caption"><span>${esc(caption)}</span><span>${manifest&&manifest.developerTest?'개발 테스트 지도':'GPS 경로 · 로컬 저장'}</span></div>`;
  }

  function confirmSnow(title,message,confirmText,onConfirm){
    moveOverlay.dataset.v02801SnowConfirm='1';moveOverlay.classList.add('show');
    moveOverlay.innerHTML=`<div class="move-sheet"><div class="move-sheet-handle"></div><h3>${esc(title)}</h3><p>${esc(message)}</p><div class="move-sheet-actions"><button id="v02801ConfirmCancel" class="move-sheet-cancel">취소</button><button id="v02801ConfirmOk" class="move-sheet-stop">${esc(confirmText)}</button></div></div>`;
    document.getElementById('v02801ConfirmCancel').onclick=closeSnowConfirm;document.getElementById('v02801ConfirmOk').onclick=()=>{closeSnowConfirm();onConfirm();};moveOverlay.onclick=e=>{if(e.target===moveOverlay)closeSnowConfirm();};
  }

  /* Route new detail pages before the original renderer. */
  const previousRender=window.render;
  window.render=function(){
    if(typeof view!=='undefined'&&view==='saved-snow-detail'){renderSnowRecordDetail();return;}
    if(typeof view!=='undefined'&&view==='saved-snow-descent'){renderSnowDescentDetail();return;}
    return previousRender.apply(this,arguments);
  };

  const previousBack=window.yamoneAndroidBack;
  window.yamoneAndroidBack=function(){
    if(moveOverlay&&moveOverlay.classList.contains('show'))return previousBack?previousBack():true;
    if(typeof view!=='undefined'&&view==='saved-snow-descent'){view='saved-snow-detail';render();return true;}
    if(typeof view!=='undefined'&&view==='saved-snow-detail'){backSnowRecords();return true;}
    if(typeof view!=='undefined'&&view==='snow'){view='main';snowState='ready';tab='activity';setFocusMode(false);syncTabs();render();return true;}
    return previousBack?previousBack():false;
  };

  setInterval(updateSnowLive,1000);
})();
