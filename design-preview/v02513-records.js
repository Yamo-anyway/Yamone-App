/* v0.26.01: Records shows only actually saved local records; all mock/sample records are removed. */
(function(){
  'use strict';
  const A='assets/';
  let savedById=Object.create(null);
  let selectedSavedId=null;

  function readSaved(){
    try{
      if(!window.YamoneRecords || typeof YamoneRecords.getSavedRecords!=='function')return [];
      const raw=YamoneRecords.getSavedRecords(100);
      const parsed=typeof raw==='string'?JSON.parse(raw):raw;
      const list=Array.isArray(parsed&&parsed.records)?parsed.records:[];
      savedById=Object.create(null);
      list.forEach(r=>{ if(r&&r.sessionId)savedById[String(r.sessionId)]=r; });
      return list;
    }catch(e){
      savedById=Object.create(null);
      return [];
    }
  }

  function typeInfo(type){
    if(type==='cycling')return {label:'자전거',mode:'bike',icon:'activity-move'};
    if(type==='running')return {label:'달리기',mode:'run',icon:'activity-move'};
    if(type==='walkrun')return {label:'걷기/달리기',mode:'run',icon:'activity-move'};
    return {label:'걷기',mode:'run',icon:'activity-move'};
  }

  function n(v){const x=Number(v);return Number.isFinite(x)?x:0;}
  function two(v){return String(v).padStart(2,'0');}
  function daypart(ms){const h=new Date(n(ms)).getHours();return h<12?'오전':h<18?'오후':'저녁';}
  function titleOf(r){const info=typeInfo(r.type);return `${daypart(r.startEpochMs)} ${info.label}`;}
  function dateOf(ms){
    const d=new Date(n(ms));
    if(!Number.isFinite(d.getTime()))return '';
    const w=['일','월','화','수','목','금','토'][d.getDay()];
    return `${d.getFullYear()}년 ${d.getMonth()+1}월 ${d.getDate()}일 (${w}) ${two(d.getHours())}:${two(d.getMinutes())}`;
  }
  function distanceOf(m){const km=Math.max(0,n(m))/1000;return `${km.toFixed(km<10?2:1)} km`;}
  function durationOf(ms){
    let sec=Math.max(0,Math.floor(n(ms)/1000));
    const h=Math.floor(sec/3600);sec%=3600;
    const m=Math.floor(sec/60),s=sec%60;
    if(h>0)return `${h}시간 ${m}분`;
    return `${m}분 ${two(s)}초`;
  }
  function speedOf(kmh){return `${Math.max(0,n(kmh)).toFixed(1)} km/h`;}
  function avgSpeed(r){const h=n(r.movingMs)/3600000;return h>0?(n(r.distanceM)/1000)/h:0;}
  function paceOf(r){
    const km=n(r.distanceM)/1000,ms=n(r.movingMs);
    if(km<=0||ms<=0)return "--'--″ /km";
    let sec=Math.round((ms/km)/1000);const min=Math.floor(sec/60);sec%=60;
    return `${min}'${two(sec)}″ /km`;
  }
  function splitText(ms,type){
    ms=n(ms);if(ms<=0)return '-';
    if(type==='cycling')return `${(3600000/ms).toFixed(1)} km/h`;
    let sec=Math.round(ms/1000);const min=Math.floor(sec/60);sec%=60;
    return `${min}'${two(sec)}″ /km`;
  }
  function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}

  function realListItem(r){
    const info=typeInfo(r.type);
    return `<button class="card record record-open v2513-real-record" data-v2513-saved="${esc(r.sessionId)}">
      <img src="${A}${info.icon}.png" alt="">
      <div class="record-copy"><b>${esc(titleOf(r))}</b><small>${esc(dateOf(r.startEpochMs))}</small></div>
      <div class="record-value"><b>${esc(distanceOf(r.distanceM))}</b><small>${esc(durationOf(r.durationMs))}</small></div>
    </button>`;
  }

  window.renderRecords=function(){
    const saved=readSaved();
    const showSaved=recordFilter==='all'||recordFilter==='move';
    const realHtml=showSaved?saved.map(realListItem).join(''):'';

    screen.innerHTML=`<h1 class="title">기록</h1>
      <div class="filters">
        <button class="filter ${recordFilter==='all'?'active':''}" data-record-filter="all">전체</button>
        <button class="filter ${recordFilter==='move'?'active':''}" data-record-filter="move">이동</button>
        <button class="filter ${recordFilter==='snow'?'active':''}" data-record-filter="snow">Snow</button>
        <button class="filter ${recordFilter==='sleep'?'active':''}" data-record-filter="sleep">수면</button>
      </div>
      <div class="section">저장된 기록</div>
      ${realHtml||`<div class="record-empty">표시할 기록이 없습니다.</div>`}`;

    document.querySelectorAll('[data-record-filter]').forEach(b=>b.onclick=()=>{recordFilter=b.dataset.recordFilter;renderRecords();});
    document.querySelectorAll('[data-v2513-saved]').forEach(b=>b.onclick=()=>{selectedSavedId=b.dataset.v2513Saved;view='saved-record-detail';render();});
  };

  function renderSavedDetail(){
    const r=savedById[selectedSavedId]||readSaved().find(x=>String(x.sessionId)===String(selectedSavedId));
    if(!r){view='main';tab='records';render();return;}
    const info=typeInfo(r.type);
    const avg=r.type==='cycling'?speedOf(avgSpeed(r)):paceOf(r);
    const splits=Array.isArray(r.splitsMs)?r.splitsMs:[];
    const splitHtml=splits.length?splits.map((ms,i)=>`<div class="move-segment"><img src="${A}${r.type==='cycling'?'speed.png':'pace.png'}"><div><b>${i+1} km</b><small>실제 저장 구간</small></div><strong>${esc(splitText(ms,r.type))}</strong></div>`).join(''):`<div class="record-empty">완료된 1km 구간이 없습니다.</div>`;
    setFocusMode(false);
    screen.innerHTML=`
      <div class="move-sticky"><div class="move-header-row">
        <button id="v2513SavedBack" class="move-back-asset-btn"><img class="move-back-asset" src="${A}back-v02402.png" alt="뒤로가기"></button>
        <div class="move-plain-title">기록 상세</div>
      </div></div>
      <div class="record-detail-summary"><div><b>${esc(titleOf(r))}</b><small>${esc(dateOf(r.startEpochMs))}</small></div><span>${esc(info.label)}</span></div>
      <div class="detail-stat-grid v24-list-grid">
        <div class="card"><span>거리</span><b>${esc(distanceOf(r.distanceM))}</b></div>
        <div class="card"><span>시간</span><b>${esc(durationOf(r.durationMs))}</b></div>
        <div class="card"><span>${r.type==='cycling'?'평균 속도':'평균 페이스'}</span><b>${esc(avg)}</b></div>
        <div class="card"><span>최고 속도</span><b>${esc(speedOf(r.maxSpeedKmh))}</b></div>
        ${r.type==='cycling'?'':`<div class="card"><span>걸음수</span><b>${Math.round(n(r.steps))} 걸음</b></div>`}
      </div>
      <div class="detail-section-head"><b>구간 요약</b><small>실제 저장된 1km 구간</small></div>
      <div class="card move-segments">${splitHtml}</div>
      <div class="v2513-real-note">이 항목은 휴대폰에 실제 저장된 이동 활동 기록입니다.</div>`;
    document.getElementById('v2513SavedBack').onclick=()=>{selectedSavedId=null;view='main';tab='records';syncTabs();render();};
  }

  const previousRender=window.render;
  window.render=function(){
    if(typeof view!=='undefined'&&view==='saved-record-detail'){renderSavedDetail();return;}
    return previousRender.apply(this,arguments);
  };

  const previousBack=window.yamoneAndroidBack;
  window.yamoneAndroidBack=function(){
    if(typeof view!=='undefined'&&view==='saved-record-detail'){
      selectedSavedId=null;view='main';tab='records';syncTabs();render();return true;
    }
    return previousBack?previousBack():false;
  };
})();
