/* v0.36.02: decorate auto-detect sound/vibration settings with dedicated assets. */
(function(){
  'use strict';
  let queued=false;
  const ITEMS=[
    ['v3601Sound','assets/autodetect-sound-v03602.svg','알림 소리'],
    ['v3601Vibrate','assets/autodetect-vibrate-v03602.svg','진동']
  ];
  function decorate(){
    queued=false;
    try{
      if(typeof settingPage!=='undefined'&&settingPage!=='자동감지')return;
      if(typeof screen==='undefined'||!screen)return;
      ITEMS.forEach(([id,src,alt])=>{
        const row=screen.querySelector(`[data-setting-toggle="${id}"]`);
        if(!row||row.querySelector('.v3602-setting-asset'))return;
        const icon=document.createElement('span');
        icon.className='v3602-setting-asset';
        icon.setAttribute('aria-hidden','true');
        icon.innerHTML=`<img src="${src}" alt="${alt}">`;
        row.prepend(icon);
      });
    }catch(e){}
  }
  function schedule(){
    if(queued)return;
    queued=true;
    requestAnimationFrame(decorate);
  }
  if(typeof screen!=='undefined'&&screen){
    new MutationObserver(schedule).observe(screen,{childList:true,subtree:true});
  }
  schedule();
})();
