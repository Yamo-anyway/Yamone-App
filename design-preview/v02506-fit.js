/* v0.25.06: fit each Summary View value to the maximum size available in its row. */
(function(){
  'use strict';
  const MIN=28;
  const MAX=132;
  let raf=0;

  function setSize(el,px){
    el.style.setProperty('font-size',px+'px','important');
  }

  function fits(el,wrap,size){
    setSize(el,size);
    const width=Math.max(0,wrap.clientWidth-2);
    const height=Math.max(0,wrap.clientHeight-2);
    return el.scrollWidth<=width+0.5 && el.scrollHeight<=height+0.5;
  }

  function fitValue(el){
    const wrap=el.closest('.v25-summary-value-wrap');
    if(!wrap || wrap.clientWidth<20 || wrap.clientHeight<20)return;
    let low=MIN,high=MAX,best=MIN;
    for(let i=0;i<9;i++){
      const mid=(low+high)/2;
      if(fits(el,wrap,mid)){best=mid;low=mid;}
      else high=mid;
    }
    setSize(el,Math.floor(best));
  }

  function fitAll(){
    raf=0;
    if(!screen || !screen.classList.contains('v25-summary-screen'))return;
    screen.querySelectorAll('.v25-summary-value-wrap > strong').forEach(fitValue);
  }

  function schedule(){
    if(raf)return;
    raf=requestAnimationFrame(()=>requestAnimationFrame(fitAll));
  }

  const previousRender=window.render;
  if(typeof previousRender==='function'){
    window.render=function(){
      const result=previousRender.apply(this,arguments);
      schedule();
      return result;
    };
  }

  if(typeof ResizeObserver!=='undefined'){
    const ro=new ResizeObserver(schedule);
    ro.observe(screen);
  }
  window.addEventListener('resize',schedule,{passive:true});
  new MutationObserver(schedule).observe(screen,{childList:true,subtree:true});
  if(document.fonts && document.fonts.ready)document.fonts.ready.then(schedule).catch(()=>{});
  schedule();
})();
