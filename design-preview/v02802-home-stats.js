/* v0.28.02 · Home statistics from real local activity records + today's Snow card. */
(function(){
  'use strict';
  const A='assets/';

  function parse(raw,fallback){
    try{return typeof raw==='string'?JSON.parse(raw):raw;}catch(e){return fallback;}
  }
  function num(v){const n=Number(v);return Number.isFinite(n)?n:0;}
  function onHome(){return typeof view!=='undefined'&&typeof tab!=='undefined'&&view==='main'&&tab==='home';}
  function todayBounds(){
    const now=new Date();
    const start=new Date(now.getFullYear(),now.getMonth(),now.getDate()).getTime();
    return [start,start+24*60*60*1000];
  }
  function isToday(ms,bounds){const t=num(ms);return t>=bounds[0]&&t<bounds[1];}

  function movementRecords(){
    try{
      if(!window.YamoneRecords||typeof YamoneRecords.getSavedRecords!=='function')return [];
      const payload=parse(YamoneRecords.getSavedRecords(100),{records:[]});
      return Array.isArray(payload&&payload.records)?payload.records:[];
    }catch(e){return [];}
  }
  function movementState(){
    try{
      if(!window.YamoneMovement||typeof YamoneMovement.getState!=='function')return {recording:false};
      return parse(YamoneMovement.getState(),{recording:false})||{recording:false};
    }catch(e){return {recording:false};}
  }
  function snowRecords(){
    try{
      if(!window.YamoneSnow||typeof YamoneSnow.listRecords!=='function')return [];
      const payload=parse(YamoneSnow.listRecords(100),{records:[]});
      return Array.isArray(payload&&payload.records)?payload.records:[];
    }catch(e){return [];}
  }

  function activityToday(){
    const bounds=todayBounds();
    const saved=movementRecords().filter(r=>isToday(r.startEpochMs,bounds));
    let movingMs=0,distanceM=0,count=saved.length;
    saved.forEach(r=>{
      const moving=num(r.movingMs);
      movingMs+=moving>0?moving:num(r.durationMs);
      distanceM+=num(r.distanceM);
    });
    const active=movementState();
    if(active.recording&&isToday(active.startMs,bounds)){
      const moving=num(active.movingMs);
      movingMs+=moving>0?moving:num(active.elapsedMs);
      distanceM+=num(active.distanceM);
      count+=1;
    }
    return {movingMs,distanceM,count};
  }

  function snowToday(){
    const bounds=todayBounds();
    const rows=snowRecords().filter(r=>isToday(r.startEpochMs,bounds));
    if(!rows.length)return null;
    let activeMs=0,distanceM=0,descents=0,lifts=0,maxSpeed=0;
    const resorts=[];
    const sports=new Set();
    rows.forEach(r=>{
      activeMs+=num(r.activeDurationMs)>0?num(r.activeDurationMs):num(r.durationMs);
      distanceM+=num(r.descentDistanceM);
      descents+=num(r.descentCount);
      lifts+=num(r.liftCount);
      maxSpeed=Math.max(maxSpeed,num(r.maxSpeedKmh));
      const resort=String(r.resortName||'').trim();
      if(resort&&!resorts.includes(resort))resorts.push(resort);
      sports.add(r.sport==='snowboard'?'스노보드':'스키');
    });
    return {rows,activeMs,distanceM,descents,lifts,maxSpeed,resorts,sports:[...sports]};
  }

  function timeText(ms){
    const total=Math.max(0,Math.round(num(ms)/60000));
    if(total<60)return `${total}분`;
    const h=Math.floor(total/60),m=total%60;
    return m?`${h}시간 ${m}분`:`${h}시간`;
  }
  function distanceText(m){
    const km=Math.max(0,num(m))/1000;
    if(km===0)return '0.0 km';
    return `${km.toFixed(km<10?2:1)} km`;
  }
  function speedText(v){return `${Math.max(0,num(v)).toFixed(1)} km/h`;}

  function updateStats(){
    if(!onHome())return;
    const stats=screen.querySelector('.stats');
    if(!stats)return;
    const a=activityToday();
    const values=[
      {icon:'stat-time.png',label:'활동 시간',value:timeText(a.movingMs)},
      {icon:'stat-distance.png',label:'이동 거리',value:distanceText(a.distanceM)},
      {icon:'activity-move.png',label:'활동 횟수',value:`${a.count}회`}
    ];
    if(stats.dataset.v02802!=='1'){
      stats.dataset.v02802='1';
      stats.innerHTML=values.map((x,i)=>`<div class="card stat v02802-home-stat" data-v02802-stat="${i}"><img src="${A}${x.icon}"><span>${x.label}</span><strong></strong></div>`).join('');
    }
    values.forEach((x,i)=>{
      const card=stats.querySelector(`[data-v02802-stat="${i}"]`);if(!card)return;
      const img=card.querySelector('img'),span=card.querySelector('span'),strong=card.querySelector('strong');
      if(img&&img.getAttribute('src')!==A+x.icon)img.setAttribute('src',A+x.icon);
      if(span&&span.textContent!==x.label)span.textContent=x.label;
      if(strong&&strong.textContent!==x.value)strong.textContent=x.value;
    });
  }

  function snowResortText(s){
    if(!s||!s.resorts.length)return '스키장 미확인';
    if(s.resorts.length===1)return s.resorts[0];
    return `${s.resorts[0]} 외 ${s.resorts.length-1}곳`;
  }
  function snowSportText(s){
    if(!s||!s.sports.length)return 'Snow';
    return s.sports.join(' · ');
  }
  function openSnowRecords(){
    if(typeof recordFilter!=='undefined')recordFilter='snow';
    view='main';tab='records';
    if(typeof setFocusMode==='function')setFocusMode(false);
    if(typeof syncTabs==='function')syncTabs();
    if(typeof render==='function')render();
  }
  function updateSnowCard(){
    if(!onHome())return;
    const current=document.getElementById('v02802HomeSnow');
    const snow=snowToday();
    if(!snow){if(current)current.remove();return;}
    let card=current;
    if(!card){
      card=document.createElement('button');
      card.type='button';
      card.id='v02802HomeSnow';
      card.className='card v02802-home-snow';
      card.onclick=openSnowRecords;
      card.innerHTML=`<div class="v02802-home-snow-head"><img src="${A}activity-snow.png" alt=""><div><b>오늘 Snow</b><small></small></div><span>기록 보기 ›</span></div><div class="v02802-home-snow-grid"><div><span>활동 시간</span><strong data-snow="time"></strong></div><div><span>활주 거리</span><strong data-snow="distance"></strong></div><div><span>활주</span><strong data-snow="runs"></strong></div><div><span>리프트</span><strong data-snow="lifts"></strong></div></div><div class="v02802-home-snow-max"></div>`;
      const stats=screen.querySelector('.stats');
      if(stats&&stats.nextSibling)screen.insertBefore(card,stats.nextSibling);else if(stats)stats.after(card);else screen.appendChild(card);
    }
    const sub=card.querySelector('small');
    if(sub)sub.textContent=`${snowResortText(snow)} · ${snowSportText(snow)}`;
    const set=(key,value)=>{const el=card.querySelector(`[data-snow="${key}"]`);if(el&&el.textContent!==value)el.textContent=value;};
    set('time',timeText(snow.activeMs));set('distance',distanceText(snow.distanceM));set('runs',`${snow.descents}회`);set('lifts',`${snow.lifts}회`);
    const max=card.querySelector('.v02802-home-snow-max');
    if(max)max.textContent=`오늘 최고 속도 ${speedText(snow.maxSpeed)} · 저장된 Snow 기록 ${snow.rows.length}개`;
  }

  function sync(){if(!onHome())return;updateStats();updateSnowCard();}

  const previousHome=window.renderHome;
  if(typeof previousHome==='function'){
    window.renderHome=function(){
      const result=previousHome.apply(this,arguments);
      requestAnimationFrame(sync);
      return result;
    };
  }
  setInterval(sync,1000);
  window.v02802SyncHomeStats=sync;
})();
