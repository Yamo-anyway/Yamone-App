/* v0.36.00: integrated runtime readiness, excluding location sharing. */
(function(){
  'use strict';
  function parse(raw,fallback){try{return typeof raw==='string'?JSON.parse(raw):raw;}catch(e){return fallback;}}
  function state(){
    try{
      if(!window.YamoneReadiness||typeof YamoneReadiness.getState!=='function')return {};
      return parse(YamoneReadiness.getState(),{})||{};
    }catch(e){return {};}
  }
  function item(label,ok,okText,badText){return `<div class="v3600-ready-item"><span>${label}</span><strong class="${ok?'':'warn'}">${ok?okText:badText}</strong></div>`;}
  function inject(){
    if(typeof settingPage==='undefined'||settingPage!=='앱 정보')return;
    if(document.getElementById('v3600Readiness'))return;
    const anchor=screen.querySelector('.app-info-card');if(!anchor)return;
    const s=state();
    const gps=String(s.gpsState||'waiting');
    const gpsText=s.movementActive?(gps==='gap'?'GPS 미확인 · 기록 계속':gps==='ok'?'GPS 정상':'GPS 수신 대기'):'대기';
    const snowText=(Number(s.snowProductionReadyMaps)||0)>0?`${s.snowProductionReadyMaps}개 실사용 가능`:(Number(s.snowInvalidMaps)||0)>0?'지도 검증 필요':'현장 지도 준비 전';
    const box=document.createElement('div');box.id='v3600Readiness';box.className='card v3600-readiness';
    box.innerHTML=`<div class="v3600-readiness-head"><b>통합 진단</b><span>v${String(s.version||'')}</span></div>
      <div class="v3600-ready-grid">
        ${item('위치 권한',!!s.location,'사용 가능','권한 필요')}
        ${item('활동 인식',!!s.activityRecognition,'사용 가능','권한 필요')}
        ${item('마이크',!!s.microphone,'사용 가능','권한 필요')}
        ${item('알림',!!s.notifications,'사용 가능','권한 필요')}
        ${item('정확한 알람',!!s.exactAlarm,'사용 가능','설정 필요')}
        ${item('백그라운드 위치',!!s.backgroundLocation,'사용 가능','설정 필요')}
        ${item('이동 기록',true,gpsText,gpsText)}
        ${item('수면 측정',!!s.sleepHealthy,s.sleepActive?'측정 정상':'대기',s.sleepActive?'측정 확인 필요':'대기')}
        ${item('Snow 지도',(Number(s.snowInvalidMaps)||0)===0,snowText,'지도 검증 필요')}
        ${item('핵심 권한',!!s.corePermissionReady,'준비됨','확인 필요')}
      </div>
      <div class="v3600-field"><b>실기기 확인이 필요한 항목</b><ul><li>걷기·달리기·자전거 자동감지와 자동차 오인식</li><li>터널·지하 등 GPS 끊김 후 경로 복귀</li><li>잠금화면 정확한 알람·스누즈·흔들기 종료</li><li>수면 7~8시간 연속 측정과 배터리 사용량</li><li>실제 스키장 Snow 오프라인 지도와 경계·슬로프 판정</li></ul></div>
      <div class="v3600-excluded">위치공유 기능은 이번 통합 진단과 개발 순서에서 제외했습니다.</div>`;
    anchor.insertAdjacentElement('afterend',box);
  }
  const previous=window.renderAppInfoSettings;
  if(typeof previous==='function'){
    window.renderAppInfoSettings=function(){const r=previous.apply(this,arguments);requestAnimationFrame(inject);return r;};
  }
  const observer=new MutationObserver(()=>requestAnimationFrame(inject));observer.observe(screen,{childList:true,subtree:true});
  requestAnimationFrame(inject);
})();
