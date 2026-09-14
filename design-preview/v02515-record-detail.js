/* v0.25.15: saved-record detail shows the full route and includes the final partial (<1 km) segment. */
(function(){
  'use strict';
  let selectedSessionId='';
  let queued=false;

  function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
  function n(v){const x=Number(v);return Number.isFinite(x)?x:0;}
  function two(v){return String(v).padStart(2,'0');}

  function readDetail(sessionId){
    try{
      if(!window.YamoneRecords||typeof YamoneRecords.getSavedRecordDetail!=='function')return null;
      const raw=YamoneRecords.getSavedRecordDetail(String(sessionId||''),700);
      const parsed=typeof raw==='string'?JSON.parse(raw):raw;
      return parsed&&parsed.found?parsed:null;
    }catch(e){return null;}
  }

  function metricFor(ms,type,distanceM){
    ms=n(ms);distanceM=n(distanceM);
    if(ms<=0||distanceM<=0)return '-';
    if(type==='cycling'){
      const hours=ms/3600000;
      return hours>0?`${((distanceM/1000)/hours).toFixed(1)} km/h`:'-';
    }
    const km=distanceM/1000;
    let sec=Math.round((ms/km)/1000);
    const min=Math.floor(sec/60);sec%=60;
    return `${min}'${two(sec)}″ /km`;
  }

  function splitRows(r){
    const type=r.type||'walking';
    const full=Array.isArray(r.splitsMs)?r.splitsMs.map(n):[];
    const rows=[];
    let usedMs=0;
    full.forEach((ms,i)=>{
      usedMs+=Math.max(0,ms);
      rows.push(`<div class="move-segment"><img src="assets/${type==='cycling'?'speed.png':'pace.png'}"><div><b>${i+1} km</b><small>완료 구간</small></div><strong>${esc(metricFor(ms,type,1000))}</strong></div>`);
    });

    const totalDistance=Math.max(0,n(r.distanceM));
    const totalMoving=Math.max(0,n(r.movingMs));
    const partialDistance=Math.max(0,totalDistance-full.length*1000);
    const partialMs=Math.max(0,totalMoving-usedMs);
    if(partialDistance>1&&partialMs>0){
      const km=(partialDistance/1000).toFixed(2);
      rows.push(`<div class="move-segment v2515-partial-segment"><img src="assets/${type==='cycling'?'speed.png':'pace.png'}"><div><b>마지막 ${km} km</b><small>1km 미만 구간</small></div><strong>${esc(metricFor(partialMs,type,partialDistance))}</strong></div>`);
    }

    if(!rows.length&&totalDistance>1&&totalMoving>0){
      const km=(totalDistance/1000).toFixed(2);
      rows.push(`<div class="move-segment v2515-partial-segment"><img src="assets/${type==='cycling'?'speed.png':'pace.png'}"><div><b>${km} km</b><small>1km 미만 구간</small></div><strong>${esc(metricFor(totalMoving,type,totalDistance))}</strong></div>`);
    }
    return rows.length?rows.join(''):'<div class="record-empty">표시할 이동 구간이 없습니다.</div>';
  }

  function mercator(lat,lon,z){
    const scale=256*Math.pow(2,z);
    const safeLat=Math.max(-85.0511,Math.min(85.0511,lat));
    const sin=Math.sin(safeLat*Math.PI/180);
    return {x:(lon+180)/360*scale,y:(.5-Math.log((1+sin)/(1-sin))/(4*Math.PI))*scale};
  }
  function chooseZoom(points,w,h){
    if(points.length<2)return 17;
    for(let z=18;z>=3;z--){
      const px=points.map(p=>mercator(Number(p.lat),Number(p.lon),z));
      const xs=px.map(p=>p.x),ys=px.map(p=>p.y);
      if(Math.max(...xs)-Math.min(...xs)<=Math.max(40,w-44)&&Math.max(...ys)-Math.min(...ys)<=Math.max(40,h-44))return z;
    }
    return 3;
  }
  function drawRoute(id,rawPoints){
    const el=document.getElementById(id);if(!el)return;
    const points=(rawPoints||[]).filter(p=>Number.isFinite(Number(p.lat))&&Number.isFinite(Number(p.lon)));
    const w=Math.max(240,el.clientWidth||320),h=Math.max(180,el.clientHeight||220);
    if(!points.length){el.innerHTML='<div class="v2508-map-wait">저장된 GPS 경로가 없습니다.</div>';return;}
    const z=chooseZoom(points,w,h),px=points.map(p=>mercator(Number(p.lat),Number(p.lon),z));
    let minX=Math.min(...px.map(p=>p.x)),maxX=Math.max(...px.map(p=>p.x));
    let minY=Math.min(...px.map(p=>p.y)),maxY=Math.max(...px.map(p=>p.y));
    let cx=(minX+maxX)/2,cy=(minY+maxY)/2;
    if(points.length===1){cx=px[0].x;cy=px[0].y;}
    const left=cx-w/2,top=cy-h/2;
    const tx0=Math.floor(left/256),tx1=Math.floor((left+w)/256),ty0=Math.floor(top/256),ty1=Math.floor((top+h)/256),tiles=Math.pow(2,z);
    let tileHtml='';
    for(let ty=ty0;ty<=ty1;ty++)for(let tx=tx0;tx<=tx1;tx++){
      if(ty<0||ty>=tiles)continue;
      const wrapped=((tx%tiles)+tiles)%tiles;
      tileHtml+=`<img class="v2508-tile" src="https://tile.openstreetmap.org/${z}/${wrapped}/${ty}.png" alt="" draggable="false" style="left:${Math.round(tx*256-left)}px;top:${Math.round(ty*256-top)}px">`;
    }
    const path=px.map(p=>`${(p.x-left).toFixed(1)},${(p.y-top).toFixed(1)}`).join(' ');
    const start=px[0],end=px[px.length-1];
    el.innerHTML=`<div class="v2508-tile-layer">${tileHtml}</div><svg class="v2508-route-svg" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none"><polyline class="v2508-route-line-shadow" points="${path}"/><polyline class="v2508-route-line" points="${path}"/><circle class="v2508-route-start" cx="${(start.x-left).toFixed(1)}" cy="${(start.y-top).toFixed(1)}" r="6"/><circle class="v2508-route-current-ring" cx="${(end.x-left).toFixed(1)}" cy="${(end.y-top).toFixed(1)}" r="9"/><circle class="v2508-route-current" cx="${(end.x-left).toFixed(1)}" cy="${(end.y-top).toFixed(1)}" r="5"/></svg><div class="v2508-attribution">© OpenStreetMap contributors</div>`;
  }

  function enhance(){
    queued=false;
    if(typeof view==='undefined'||view!=='saved-record-detail'||!selectedSessionId)return;
    const root=document.querySelector('.record-detail-summary');
    if(!root||screen.querySelector(`[data-v2515-detail="${CSS.escape(selectedSessionId)}"]`))return;
    const detail=readDetail(selectedSessionId);if(!detail)return;
    const r=detail.record||{};
    const splitHead=[...screen.querySelectorAll('.detail-section-head')].find(el=>el.querySelector('b')&&el.querySelector('b').textContent.trim()==='구간 요약');
    if(!splitHead)return;

    const routeHead=document.createElement('div');
    routeHead.className='detail-section-head';
    routeHead.dataset.v2515Detail=selectedSessionId;
    routeHead.innerHTML='<b>전체 경로</b><small>저장된 GPS 경로 전체</small>';
    const routeCard=document.createElement('div');
    routeCard.className='card v2508-summary-map-card';
    routeCard.innerHTML='<div id="v2515SavedRouteMap" class="v2508-route-map"><div class="v2508-map-wait">경로 불러오는 중</div></div>';
    splitHead.parentNode.insertBefore(routeHead,splitHead);
    splitHead.parentNode.insertBefore(routeCard,splitHead);

    const small=splitHead.querySelector('small');if(small)small.textContent='1km 구간 + 마지막 1km 미만 구간';
    const splitCard=splitHead.nextElementSibling;
    if(splitCard&&splitCard.classList.contains('move-segments'))splitCard.innerHTML=splitRows(r);
    requestAnimationFrame(()=>drawRoute('v2515SavedRouteMap',detail.points||[]));
  }

  function schedule(){
    if(queued)return;queued=true;requestAnimationFrame(enhance);
  }

  document.addEventListener('click',e=>{
    const b=e.target.closest&&e.target.closest('[data-v2513-saved]');
    if(b)selectedSessionId=String(b.dataset.v2513Saved||'');
    if(e.target.closest&&e.target.closest('#v2513SavedBack'))selectedSessionId='';
  },true);

  new MutationObserver(schedule).observe(screen,{childList:true,subtree:true});
  schedule();
})();
