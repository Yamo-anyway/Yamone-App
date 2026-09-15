(function(){
  'use strict';
  const V='0.32.00';
  function apply(){
    document.title='야모네 · v'+V;
    const home=document.querySelector('.home-version');
    if(home)home.textContent='Version '+V;
    document.querySelectorAll('.setting small,.app-info-card small').forEach(el=>{
      if(/^Version\s/.test(el.textContent.trim()))el.textContent='Version '+V;
    });
  }
  new MutationObserver(()=>requestAnimationFrame(apply)).observe(document.body,{childList:true,subtree:true});
  apply();
})();
