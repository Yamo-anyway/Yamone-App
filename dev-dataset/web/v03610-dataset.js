/* Developer data collection UI. No network requests from JavaScript. */
(function(){'use strict';
 const V='0.36.10';let signature='',lastMode=null;
 const esc=s=>String(s==null?'':s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 function developer(){try{return !!window.YamoneSystemSettings&&YamoneSystemSettings.isDeveloperMode();}catch(e){return false;}}
 function call(name,...args){try{if(!developer()||!window.YamoneDataset)return null;return YamoneDataset[name](...args);}catch(e){return null;}}
 function state(){try{return JSON.parse(call('getState')||'{}');}catch(e){return {};}}
 const date=n=>n?new Date(n).toLocaleString('ko-KR'):'-';
 window.yamoneDatasetDeveloper=developer;
 window.yamoneRenderDataset=function(){
  if(!developer()){settingPage=null;view='main';tab='settings';render();return;}
  const s=state();signature=JSON.stringify(s);if(typeof setFocusMode==='function')setFocusMode(true);
  screen.innerHTML=`${settingHeader('업로드')}
   <div class="card dataset-panel"><div class="dataset-head"><b>자동 기록 · 업로드</b><button id="datasetToggle" class="dataset-button dataset-toggle">${s.automatic?'ON':'OFF'}</button></div>
   <p>ON부터 OFF까지가 진단 기록 1개입니다. 시스템 정시마다 조각을 보내고 종료 시 마지막 조각만 전송합니다. 활동 자동감지 설정과 수동 시작·종료는 별도로 유지합니다.</p>
   <small>${s.active?(s.automatic?'연속 테스트 수집 중':'수동 활동 진단 수집 중'):'수집 대기'} · ${s.uploading?'서버 전송 중':'전송 대기'}<br>마지막 전송: ${date(s.lastUploadMs)}</small></div>
   <div class="card dataset-panel"><div class="dataset-head"><b>수동 활동 진단 저장</b><button id="datasetManual" class="dataset-button">${s.manualCapture?'ON':'OFF'}</button></div><p>개발자 모드에서 기존 활동을 시작하면 원본을 함께 로컬 저장합니다. 자동 기록 OFF 상태의 수동 진단 기록은 아래에서 선택해 업로드합니다.</p></div>
   ${s.paired?'':`<div class="card dataset-panel"><b>내 테스트 기기 연결</b><p>발급받은 1회용 코드를 입력합니다. 개발자 모드만 켠 다른 기기는 서버에 접근할 수 없습니다.</p><input id="datasetCode" class="dataset-input" type="password" autocomplete="off" maxlength="32" placeholder="테스터 연결 코드"><div class="dataset-actions"><button id="datasetEnroll">기기 연결</button></div></div>`}
   ${s.active?`<div class="card dataset-panel"><b>실제 활동 표시</b><p>자동 추정값과 구분해 저장합니다. 표시하지 않은 구간은 정답 미확인으로 남습니다.</p><select id="datasetPlacement" class="dataset-input"><option value="unknown">휴대폰 위치 미지정</option value="hand">손</option><option value="pocket">주머니</option><option value="bag">가방</option><option value="handlebar">자전거 거치대</option></select><div class="dataset-actions">${[['unknown','미확인'],['still','정지'],['walking','걷기'],['running','달리기'],['cycling','자전거'],['vehicle','차량']].map(([k,v])=>`<button data-dataset-label="${k}">${v}</button>`).join('')}</div><div class="dataset-actions"><button data-dataset-event="tunnel_enter">터널 진입</button><button data-dataset-event="tunnel_exit">터널 이탈</button><button data-dataset-event="gps_issue">GPS 이상</button></div></div>`:''}
   <div class="dataset-error">${esc(s.error||'')}</div>
   <div class="dataset-actions"><button id="datasetRetry">승인된 기록 재전송</button><button id="datasetRefresh">새로고침</button></div>
   <div class="section">진단 기록</div><div class="dataset-list">${(s.sessions||[]).map(r=>`<article class="card"><b>${r.mode==='automatic'?'연속 테스트':'수동·자동감지 활동 진단'}</b><small>${date(r.startWallMs)} ~ ${date(r.endWallMs)}<br>${r.complete?'서버 기록 확정':r.state==='closed'?'로컬 종료 · 전송 확인 대기':'기록 중'} · ${r.acked}/${r.parts} 조각 · ${(r.bytes/1048576).toFixed(1)} MB<br>${esc(r.id)}</small>${r.state==='closed'?`<div class="dataset-actions">${!r.complete?`<button data-dataset-upload="${esc(r.id)}">이 진단 기록 업로드</button>`:''}<button data-dataset-delete="${esc(r.id)}">기기 진단 원본 삭제</button></div>`:''}</article>`).join('')||'<p>아직 진단 기록이 없습니다.</p>'}</div>
   <p class="dataset-note">원본은 기기에 남습니다. 센서가 없거나 권한이 부족한 항목은 누락 사유를 기록합니다. 통신 실패 시 전송은 지연되며, 모든 조각이 확인된 후에만 서버 기록이 완료됩니다.</p>`;
  const back=document.getElementById('settingBack');if(back)back.onclick=()=>{settingPage=null;view='main';tab='settings';setFocusMode(false);syncTabs();render();};
  document.getElementById('datasetToggle').onclick=()=>call(s.automatic?'stopAutomatic':'startAutomatic');
  document.getElementById('datasetManual').onclick=()=>{call('setManualCapture',!s.manualCapture);window.yamoneRenderDataset();};
  const enroll=document.getElementById('datasetEnroll');if(enroll)enroll.onclick=()=>{const code=document.getElementById('datasetCode');call('enroll',code.value);code.value='';say('기기 연결을 확인합니다.');};
  document.getElementById('datasetRetry').onclick=()=>call('retry');document.getElementById('datasetRefresh').onclick=window.yamoneRenderDataset;
  screen.querySelectorAll('[data-dataset-label]').forEach(b=>b.onclick=()=>{call('mark',b.dataset.datasetLabel,document.getElementById('datasetPlacement').value,'');say('실제 활동을 표시했습니다.');});
  screen.querySelectorAll('[data-dataset-event]').forEach(b=>b.onclick=()=>{call('event',b.dataset.datasetEvent);say('상황을 표시했습니다.');});
  screen.querySelectorAll('[data-dataset-delete]').forEach(b=>b.onclick=()=>call('deleteLocal',b.dataset.datasetDelete));
  screen.querySelectorAll('[data-dataset-upload]').forEach(b=>b.onclick=()=>call('upload',b.dataset.datasetUpload));
 };
 function update(){
  const dev=developer();document.documentElement.dataset.yamoneDev=dev?'on':'off';
  if(!dev&&typeof view!=='undefined'&&view==='location'){if(typeof stopLocationTimers==='function')stopLocationTimers(true);locationSharingActive=false;view='main';tab='activity';render();}
  if(typeof view!=='undefined'&&view==='setting'&&settingPage==='업로드'){
   const focused=document.activeElement;const editing=focused&&['INPUT','SELECT'].includes(focused.tagName);
   const s=state();if(!editing&&JSON.stringify(s)!==signature)window.yamoneRenderDataset();
  }
  document.title='야모네 · v'+V;
  document.querySelectorAll('.home-version,.setting small,.app-info-card small').forEach(el=>{if(/^Version\s/.test(el.textContent.trim())&&el.textContent!=='Version '+V)el.textContent='Version '+V;});
  if(lastMode!==null&&lastMode!==dev&&typeof view!=='undefined'&&view==='main'&&tab==='settings')render();lastMode=dev;
 }
 setInterval(update,1500);update();
})();
