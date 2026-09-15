/* v0.36.04: place auto-detect sound/vibration assets in the row icon slot, never inside the switch. */
(function(){
  'use strict';
  let queued=false;
  const ITEMS=[
    ['v3601Sound','assets/autodetect-sound-v03602.svg','알림 소리'],
    ['v3601Vibrate','assets/autodetect-vibrate-v03602.svg','진동']
  ];

  function fixRows(){
    queued=false;
    try{
      if(typeof settingPage!=='undefined'&&settingPage!=='자동감지')return;
      if(typeof screen==='undefined'||!screen)return;

      ITEMS.forEach(([id,src,alt])=>{
        const toggle=screen.querySelector(`[data-setting-toggle="${id}"]`);
        if(!toggle)return;

        // v0.36.02 accidentally inserted the asset inside the switch. Remove it if present.
        toggle.querySelectorAll('.v3602-setting-asset').forEach(el=>el.remove());

        const row=toggle.closest('.setting-control-row');
        if(!row)return;
        let icon=row.querySelector('.setting-control-icon');
        if(!icon){
          icon=document.createElement('img');
          icon.className='setting-control-icon v3604-autodetect-icon';
          const copy=row.querySelector('.setting-control-copy');
          if(copy)row.insertBefore(icon,copy); else row.prepend(icon);
        }
        icon.classList.add('v3604-autodetect-icon');
        if(icon.getAttribute('src')!==src)icon.setAttribute('src',src);
        icon.setAttribute('alt',alt);
      });

      const status=document.getElementById('v3603HybridStatus');
      if(status)status.classList.add('v3604-hybrid-status');
    }catch(e){}
  }

  function schedule(){
    if(queued)return;
    queued=true;
    requestAnimationFrame(fixRows);
  }

  if(typeof screen!=='undefined'&&screen){
    new MutationObserver(schedule).observe(screen,{childList:true,subtree:true});
  }
  schedule();
})();
