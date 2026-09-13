/* v0.25.07: calculate each row's maximum number size, then use the smallest
 * of those maxima for every Summary View main value. Units stay fixed-size.
 */
(function(){
  'use strict';
  const MIN=28;
  const MAX=150;
  let raf=0;

  function setNumberSize(el,px){
    el.style.setProperty('font-size',px+'px','important');
  }

  function fits(number,strong,wrap,size){
    setNumberSize(number,size);
    const width=Math.max(0,wrap.clientWidth-2);
    const height=Math.max(0,wrap.clientHeight-2);
    return strong.scrollWidth<=width+0.5 && strong.scrollHeight<=height+0.5;
  }

  function individualMax(strong){
    const number=strong.querySelector('.v25-summary-number');
    const wrap=strong.closest('.v25-summary-value-wrap');
    if(!number || !wrap || wrap.clientWidth<20 || wrap.clientHeight<20)return MIN;
    let low=MIN,high=MAX,best=MIN;
    for(let i=0;i<10;i++){
      const mid=(low+high)/2;
      if(fits(number,strong,wrap,mid)){best=mid;low=mid;}
      else high=mid;
    }
    return Math.floor(best);
  }

  function fitAll(){
    raf=0;
    if(!screen || !screen.classList.contains('v25-summary-screen'))return;
    const strongs=[...screen.querySelectorAll('.v25-summary-value-wrap > strong')];
    if(!strongs.length)return;
    const maxima=strongs.map(individualMax);
    const common=Math.max(MIN,Math.min(...maxima));
    strongs.forEach(strong=>{
      const number=strong.querySelector('.v25-summary-number');
      if(number)setNumberSize(number,common);
    });
    screen.dataset.v25CommonValueFont=String(common);
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
