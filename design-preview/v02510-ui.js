/* v0.25.10: update Summary View values in place so the screen never flashes.
 * HH:MM changes only when the minute changes. Other values update without rebuilding DOM.
 */
(function(){
  'use strict';
  let scheduled=false;

  function bridgeState(){
    try{
      const b=window.YamoneMovement;
      if(!b||typeof b.getState!=='function')return null;
      const raw=b.getState();
      return typeof raw==='string'?JSON.parse(raw):raw;
    }catch(e){return null;}
  }
  function num(v,d){const x=Number(v);return Number.isFinite(x)?x:(d||0);}
  function uiType(s){
    const type=(s&&s.activityType)||'walking';
    if(type==='cycling')return 'bike';
    if(type==='running')return 'run';
    if(type==='walkrun')return (s&&s.autoMotionMode)==='running'?'run':'walk';
    return 'walk';
  }
  function label(t){return t==='bike'?'자전거':t==='run'?'달리기':'걷기';}
  function hhmm(ms){
    const total=Math.max(0,Math.floor(num(ms)/60000));
    const h=Math.floor(total/60),m=total%60;
    return String(h).padStart(2,'0')+':'+String(m).padStart(2,'0');
  }
  function distanceMain(m){
    const v=Math.max(0,num(m));
    return (v/1000).toFixed(v<10000?2:1);
  }
  function speedMain(kmh){return Math.max(0,num(kmh)).toFixed(1);}
  function paceMainFromMs(ms){
    if(!Number.isFinite(ms)||ms<=0)return "--'--″";
    let sec=Math.round(ms/1000);const min=Math.floor(sec/60);sec%=60;
    return `${min}'${String(sec).padStart(2,'0')}″`;
  }
  function avgKmh(s){const h=num(s&&s.movingMs)/3600000;return h>0?(num(s&&s.distanceM)/1000)/h:0;}
  function avgPaceMain(s){const d=num(s&&s.distanceM);return d>5?paceMainFromMs(num(s&&s.movingMs)/(d/1000)):"--'--″";}
  function recentMain(s,t){
    const a=Array.isArray(s&&s.splitsMs)?s.splitsMs:[];
    if(!a.length)return t==='bike'?'--.-':"--'--″";
    const ms=num(a[a.length-1]);
    if(t==='bike')return ms>0?(3600000/ms).toFixed(1):'--.-';
    return paceMainFromMs(ms);
  }
  function setText(el,text){if(el&&el.textContent!==text)el.textContent=text;}
  function findCard(prefix){
    const cards=screen.querySelectorAll('.v25-summary-card');
    for(const card of cards){
      const labelEl=card.querySelector('.v25-summary-label');
      if(labelEl&&labelEl.textContent.trim().startsWith(prefix))return card;
    }
    return null;
  }
  function setMain(prefix,value){
    const card=findCard(prefix);if(!card)return;
    setText(card.querySelector('.v25-summary-number'),value);
  }

  function updateGlance(s){
    if(typeof screen==='undefined'||!screen||!screen.classList.contains('v25-summary-screen'))return;
    if(typeof view==='undefined'||view!=='move')return;
    if(!s||!s.recording)return;
    const t=uiType(s);

    const title=screen.querySelector('.v25-summary-title-asset');
    if(title){
      const wanted=`assets/title-summary-${t}-v02503.svg`;
      if(!title.getAttribute('src')||!title.getAttribute('src').endsWith(`title-summary-${t}-v02503.svg`))title.src=wanted;
      title.alt=`요약보기 (${label(t)})`;
    }

    const timeCard=findCard('시간');
    if(timeCard){
      const labelEl=timeCard.querySelector('.v25-summary-label');
      setText(labelEl,'시간(HH:MM)');
      setText(timeCard.querySelector('.v25-summary-number'),hhmm(s.elapsedMs));
      const unit=timeCard.querySelector('.v25-summary-unit');if(unit)unit.remove();
    }
    setMain('거리',distanceMain(s.distanceM));
    if(t==='bike'){
      setMain('최근 1km 평균속도',recentMain(s,t));
      setMain('전체 평균속도',speedMain(avgKmh(s)));
      setMain('최대속도',speedMain(s.maxSpeedKmh));
    }else{
      setMain('최근 1km 페이스',recentMain(s,t));
      setMain('전체 페이스',avgPaceMain(s));
    }
  }

  window.v02510UpdateGlance=updateGlance;

  function schedule(){
    if(scheduled)return;
    scheduled=true;
    requestAnimationFrame(()=>{
      scheduled=false;
      if(typeof moveState!=='undefined'&&moveState==='glance')updateGlance(bridgeState());
    });
  }
  new MutationObserver(schedule).observe(screen,{childList:true,subtree:true});
  /* Safety cadence only. The normal 1-second native poll calls updateGlance in place;
   * this timer exists so HH:MM still advances if no other state changes occur. */
  setInterval(()=>{
    if(typeof moveState!=='undefined'&&moveState==='glance')updateGlance(bridgeState());
  },60000);
})();
