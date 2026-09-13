/* v0.25.09: minute-only Summary View time display.
 * The native recorder still tracks elapsed time precisely; this patch only
 * changes the glance UI to HH:MM so the visible value advances once a minute.
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

  function hhmm(ms){
    const total=Math.max(0,Math.floor((Number(ms)||0)/60000));
    const h=Math.floor(total/60);
    const m=total%60;
    return String(h).padStart(2,'0')+':'+String(m).padStart(2,'0');
  }

  function patchSummaryTime(){
    scheduled=false;
    if(typeof screen==='undefined'||!screen)return;
    if(typeof view==='undefined'||view!=='move')return;
    if(typeof moveState==='undefined'||moveState!=='glance')return;
    const cards=screen.querySelectorAll('.v25-summary-card');
    let timeCard=null;
    cards.forEach(card=>{
      const label=card.querySelector('.v25-summary-label');
      if(label&&/^시간(?:\(|$)/.test(label.textContent.trim()))timeCard=card;
    });
    if(!timeCard)return;
    const label=timeCard.querySelector('.v25-summary-label');
    if(label&&label.textContent!=='시간(HH:MM)')label.textContent='시간(HH:MM)';
    const state=bridgeState();
    const number=timeCard.querySelector('.v25-summary-number');
    if(number&&state){
      const value=hhmm(state.elapsedMs);
      if(number.textContent!==value)number.textContent=value;
    }
    const unit=timeCard.querySelector('.v25-summary-unit');
    if(unit)unit.remove();
  }

  function schedule(){
    if(scheduled)return;
    scheduled=true;
    requestAnimationFrame(patchSummaryTime);
  }

  new MutationObserver(schedule).observe(screen,{childList:true,subtree:true});
  /* The displayed HH:MM value intentionally changes only at minute cadence.
   * Mutation handling above reapplies the same minute value if the underlying
   * real-time screen is re-rendered between minute boundaries. */
  setInterval(patchSummaryTime,60000);
  schedule();
})();
