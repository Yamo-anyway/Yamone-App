/* v0.36.08: developer-mode gates for manual walk/run auto-switch and location sharing. */
(function(){
  'use strict';
  const UNLOCK_TAPS=7;
  const UNLOCK_WINDOW_MS=10000;
  let taps=0;
  let firstTapAt=0;
  let scheduled=false;

  function bridge(){
    try{return window.YamoneSystemSettings||null;}catch(e){return null;}
  }
  function isDeveloper(){
    try{
      const b=bridge();
      return !!(b&&typeof b.isDeveloperMode==='function'&&b.isDeveloperMode());
    }catch(e){return false;}
  }
  function setDeveloper(enabled){
    try{
      const b=bridge();
      if(!b||typeof b.setDeveloperMode!=='function')return false;
      b.setDeveloperMode(!!enabled);
      return true;
    }catch(e){return false;}
  }
  function toast(message){
    try{if(typeof say==='function')say(message);}catch(e){}
  }
  function rerender(){
    try{if(typeof render==='function')render();}catch(e){}
  }

  function hideManualAutoSwitch(){
    if(typeof view==='undefined'||view!=='move'||typeof moveState==='undefined'||moveState!=='ready')return;
    if(isDeveloper())return;
    try{if(typeof moveMode!=='undefined'&&moveMode==='auto')moveMode='walk';}catch(e){}
    const auto=screen.querySelector('[data-move-mode="auto"]');
    if(auto)auto.remove();
    screen.querySelectorAll('.move-note,.move-help,.setting-info,p,small').forEach(el=>{
      const text=(el.textContent||'').trim();
      if(text.includes('자동 전환은')||text.includes('자동 전환 모드'))el.style.display='none';
    });
  }

  function locationEntry(){
    if(typeof screen==='undefined'||!screen)return null;
    const direct=[...screen.querySelectorAll('button,.card,[role="button"],[onclick],[data-view],[data-action]')]
      .filter(el=>(el.textContent||'').replace(/\s+/g,'').includes('위치공유'))
      .sort((a,b)=>(a.textContent||'').length-(b.textContent||'').length);
    if(direct.length)return direct[0];

    const leaves=[...screen.querySelectorAll('*')]
      .filter(el=>(el.textContent||'').replace(/\s+/g,'').includes('위치공유'))
      .filter(el=>![...el.children].some(child=>(child.textContent||'').replace(/\s+/g,'').includes('위치공유')));
    for(const leaf of leaves){
      let node=leaf;
      for(let i=0;i<5&&node&&node!==screen;i++,node=node.parentElement){
        if(node.matches&&node.matches('button,.card,[role="button"],[onclick],[data-view],[data-action]'))return node;
      }
    }
    return null;
  }

  function gateLocationSharing(){
    if(typeof view==='undefined'||view!=='main'||typeof tab==='undefined'||tab!=='activity')return;
    const entry=locationEntry();
    if(!entry)return;
    entry.dataset.v3608DeveloperOnlyLocation='1';
    entry.hidden=!isDeveloper();
  }

  function decorateAppInfo(){
    if(typeof view==='undefined'||view!=='setting'||typeof settingPage==='undefined'||settingPage!=='앱 정보')return;
    const existing=document.getElementById('v3607DeveloperCard');
    if(!isDeveloper()){
      if(existing)existing.remove();
      return;
    }
    if(existing)return;
    const card=document.createElement('div');
    card.id='v3607DeveloperCard';
    card.className='card setting-block v3607-developer-card';
    card.innerHTML='<div class="v3607-developer-copy"><b>개발자 모드</b><small>실험 중인 기능이 표시됩니다.</small></div>'+
      '<button id="v3607DeveloperOff" class="v3607-developer-off" type="button">일반 사용자 모드로 전환</button>';
    screen.appendChild(card);
    const off=document.getElementById('v3607DeveloperOff');
    if(off)off.onclick=()=>{
      if(!setDeveloper(false))return;
      try{if(typeof moveMode!=='undefined'&&moveMode==='auto')moveMode='walk';}catch(e){}
      taps=0;firstTapAt=0;
      toast('일반 사용자 모드로 전환했습니다.');
      rerender();
    };
  }

  function apply(){
    scheduled=false;
    hideManualAutoSwitch();
    gateLocationSharing();
    decorateAppInfo();
  }
  function schedule(){
    if(scheduled)return;
    scheduled=true;
    requestAnimationFrame(apply);
  }

  document.addEventListener('click',e=>{
    if(typeof view==='undefined'||view!=='setting'||typeof settingPage==='undefined'||settingPage!=='앱 정보')return;
    if(isDeveloper())return;
    const candidate=e.target.closest('.app-info-card,.card,button,div,span,small');
    const text=((candidate&&candidate.textContent)||e.target.textContent||'').trim();
    if(!/Version\s*\d|버전/i.test(text))return;
    const now=Date.now();
    if(!firstTapAt||now-firstTapAt>UNLOCK_WINDOW_MS){firstTapAt=now;taps=0;}
    taps++;
    if(taps<UNLOCK_TAPS)return;
    taps=0;firstTapAt=0;
    if(setDeveloper(true)){
      toast('개발자 모드가 켜졌습니다.');
      rerender();
    }
  },true);

  if(typeof screen!=='undefined'&&screen){
    new MutationObserver(schedule).observe(screen,{childList:true,subtree:true});
  }
  schedule();
})();
