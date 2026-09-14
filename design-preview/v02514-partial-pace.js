/* v0.25.14: keep live pace stable and include the current partial (<1 km) segment. */
(function(){
  'use strict';
  let lastLivePace='';
  let lastSessionKey='';
  let scheduled=false;

  function bridgeCall(name,args,fallback){
    try{
      const b=window.YamoneMovement;
      if(!b||typeof b[name]!=='function')return fallback;
      const raw=b[name].apply(b,args||[]);
      return typeof raw==='string'?JSON.parse(raw):raw;
    }catch(e){return fallback;}
  }
  function state(){return bridgeCall('getState',[],null);}
  function latest(){return bridgeCall('getLatestRecord',[420],null);}
  function num(v,d){const x=Number(v);return Number.isFinite(x)?x:(d||0);}
  function uiType(s){
    const type=(s&&s.activityType)||'walking';
    if(type==='cycling')return 'bike';
    if(type==='running')return 'run';
    if(type==='walkrun')return (s&&s.autoMotionMode)==='running'?'run':'walk';
    return 'walk';
  }
  function paceMain(ms){
    if(!Number.isFinite(ms)||ms<=0)return '';
    let sec=Math.round(ms/1000);const min=Math.floor(sec/60);sec%=60;
    return `${min}'${String(sec).padStart(2,'0')}″`;
  }
  function paceFull(ms){const p=paceMain(ms);return p?`${p}/km`:'';}
  function shortTime(ms){
    let sec=Math.max(0,Math.floor(num(ms)/1000));
    const h=Math.floor(sec/3600);sec%=3600;
    const m=Math.floor(sec/60),s=sec%60;
    return h>0?`${h}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`:`${m}:${String(s).padStart(2,'0')}`;
  }
  function partial(s){
    const splits=Array.isArray(s&&s.splitsMs)?s.splitsMs:[];
    const completedMs=splits.reduce((sum,v)=>sum+Math.max(0,num(v)),0);
    const completedM=splits.length*1000;
    const distance=Math.max(0,num(s&&s.distanceM)-completedM);
    const moving=Math.max(0,num(s&&s.movingMs)-completedMs);
    return {splits,completedMs,completedM,distance,moving};
  }
  function recentValue(s,t,mainOnly){
    const p=partial(s);
    let distance=p.distance,ms=p.moving;
    if(distance<=5||ms<=0){
      if(!p.splits.length)return '';
      distance=1000;ms=Math.max(0,num(p.splits[p.splits.length-1]));
    }
    if(t==='bike'){
      const kmh=ms>0?(distance/1000)/(ms/3600000):0;
      return kmh>0?kmh.toFixed(1):'';
    }
    const perKm=ms/(distance/1000);
    return mainOnly?paceMain(perKm):paceFull(perKm);
  }
  function averagePace(s){
    const d=num(s&&s.distanceM),ms=num(s&&s.movingMs);
    if(d<=5||ms<=0)return '';
    return paceFull(ms/(d/1000));
  }
  function sessionKey(s){return String((s&&s.sessionDir)||'')+'|'+String((s&&s.startMs)||'');}

  function stabilizeLivePace(){
    if(typeof view==='undefined'||view!=='move'||typeof moveState==='undefined'||!['recording','paused'].includes(moveState))return;
    const el=document.getElementById('realPrimary');
    if(!el)return;
    const s=state();if(!s||!s.recording||uiType(s)==='bike')return;
    const key=sessionKey(s);if(key!==lastSessionKey){lastSessionKey=key;lastLivePace='';}
    const text=el.textContent.trim();
    if(text && !text.includes('--') && text!=='측정 중'){
      lastLivePace=text;
      return;
    }
    const fallback=lastLivePace||averagePace(s)||recentValue(s,uiType(s),false)||'측정 중';
    if(el.textContent!==fallback)el.textContent=fallback;
  }

  function findSummaryCard(prefix){
    for(const card of screen.querySelectorAll('.v25-summary-card')){
      const label=card.querySelector('.v25-summary-label');
      if(label&&label.textContent.trim().startsWith(prefix))return card;
    }
    return null;
  }
  function patchGlance(s){
    if(!s||!s.recording||typeof moveState==='undefined'||moveState!=='glance')return;
    const t=uiType(s),prefix=t==='bike'?'최근 1km 평균속도':'최근 1km 페이스';
    const card=findSummaryCard(prefix);if(!card)return;
    const value=recentValue(s,t,true);if(!value)return;
    const number=card.querySelector('.v25-summary-number');
    if(number&&number.textContent!==value)number.textContent=value;
  }

  const previousGlance=window.v02510UpdateGlance;
  if(typeof previousGlance==='function'){
    window.v02510UpdateGlance=function(s){
      previousGlance(s);
      patchGlance(s);
    };
  }

  function patchEndSummary(){
    if(!screen.classList.contains('v25-move-end-summary'))return;
    const sections=[...screen.querySelectorAll('.move-section')];
    const heading=sections.find(el=>el.textContent.trim()==='구간 요약');
    const box=heading&&heading.nextElementSibling;
    if(!box||!box.classList.contains('move-segments')||box.dataset.v2514Partial==='1')return;
    const rec=latest();if(!rec||!rec.found||!rec.meta)return;
    const m=rec.meta;
    const s={activityType:m.type||'walking',autoMotionMode:m.autoMotionModeLast||'walking',distanceM:num(m.distanceM),movingMs:num(m.movingMs),splitsMs:Array.isArray(m.splitsMs)?m.splitsMs:[]};
    const p=partial(s);if(p.distance<=5||p.moving<=0){box.dataset.v2514Partial='1';return;}
    const t=uiType(s),distanceText=(p.distance/1000).toFixed(p.distance<10000?2:1)+' km';
    const strong=t==='bike'?`${((p.distance/1000)/(p.moving/3600000)).toFixed(1)} km/h`:paceFull(p.moving/(p.distance/1000));
    const row=document.createElement('div');
    row.className='move-segment v2514-partial-segment';
    row.innerHTML=`<img src="assets/${t==='bike'?'speed.png':'pace.png'}"><div><b>${distanceText}</b><small>${shortTime(p.moving)} · 마지막 구간</small></div><strong>${strong}</strong>`;
    const empty=box.querySelector('.v2508-empty-segment');if(empty)empty.remove();
    box.appendChild(row);box.dataset.v2514Partial='1';
  }

  function apply(){
    scheduled=false;
    stabilizeLivePace();
    if(typeof moveState!=='undefined'&&moveState==='glance')patchGlance(state());
    patchEndSummary();
  }
  function schedule(){if(scheduled)return;scheduled=true;queueMicrotask(apply);}
  new MutationObserver(schedule).observe(screen,{childList:true,subtree:true,characterData:true});
  setInterval(()=>{
    stabilizeLivePace();
    if(typeof moveState!=='undefined'&&moveState==='glance')patchGlance(state());
  },1000);
  schedule();
})();
