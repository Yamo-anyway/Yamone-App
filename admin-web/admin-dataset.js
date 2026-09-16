/* Private dataset review/export. Uses existing administrator authentication. */
(function(){'use strict';
 const panel=document.getElementById('datasetPanel');if(!panel)return;
 const message=document.getElementById('datasetMessage');
 const fmt=n=>n?new Date(Number(n)).toLocaleString('ko-KR'):'-';
 async function load(){try{
  message.textContent='불러오는 중…';const rows=await rpc('dev_dataset_admin_list');
  document.getElementById('datasetRecords').innerHTML=(rows||[]).map(r=>`<article class="sleep-record"><h3>${r.mode==='automatic'?'연속 테스트':'수동·자동감지 활동 진단'}</h3><p>${esc(fmt(r.start_ms))} · ${esc(r.status)} · ${r.received_parts}조각 · ${(Number(r.stored_bytes)/1048576).toFixed(1)}MB</p><small>${esc(r.id)}</small><p>${r.status==='complete'?`<button class="soft-button" data-dataset-export="${esc(r.id)}">원본 내려받기 (.tar)</button>`:'마지막 조각 및 종료 확인 대기'}</p></article>`).join('')||'<p>업로드된 진단 기록이 없습니다.</p>';
  message.textContent='원본은 비공개 저장소에 보관됩니다. 완료된 세션만 내려받습니다.';
 }catch(e){message.textContent=e.message||String(e);}}
 document.getElementById('datasetReload').onclick=load;
 document.querySelector('[data-tab="dataset"]').addEventListener('click',load);
 document.getElementById('datasetInvite').onclick=async()=>{try{
  const r=await rpc('dev_dataset_admin_invite');document.getElementById('datasetInviteCode').textContent=r.code;
  message.textContent='24시간 유효한 1회용 코드입니다. 본인 휴대폰의 개발자 설정 > 업로드 > 내 테스트 기기 연결에 입력하세요. 공개 게시하지 마세요.';
 }catch(e){message.textContent=e.message||String(e);}};
 const encoder=new TextEncoder();
 function tarEntry(name,body){
  if(!/^[a-f0-9-]{36}\/(manifest\.json|part_[0-9]{6}\.(meta\.json|jsonl\.gz))$/.test(name))throw Error('invalid_archive_path');
  const header=new Uint8Array(512);const put=(at,size,text)=>header.set(encoder.encode(text).slice(0,size),at);
  put(0,100,name);put(100,8,'0000600\0');put(108,8,'0000000\0');put(116,8,'0000000\0');
  put(124,12,body.length.toString(8).padStart(11,'0')+'\0');put(136,12,'00000000000\0');
  header.fill(32,148,156);header[156]=48;put(257,6,'ustar\0');put(263,2,'00');
  const sum=header.reduce((a,b)=>a+b,0);put(148,8,sum.toString(8).padStart(6,'0')+'\0 ');
  return [header,body,new Uint8Array((512-body.length%512)%512)];
 }
 async function fetchPart(path){
  if(!/^[a-f0-9-]{36}\/[a-f0-9-]{36}\/[0-9]{6}-[a-f0-9]{64}\.jsonl\.gz$/.test(path))throw Error('invalid_object_path');
  for(let attempt=0;attempt<2;attempt++){
   await ensureFreshSession();const response=await fetch(SUPABASE_URL+'/storage/v1/object/authenticated/developer-datasets/'+path,{headers:{apikey:PUBLISHABLE_KEY,Authorization:'Bearer '+state.session.accessToken}});
   if(response.status===401&&attempt===0){await refreshSession();continue;}
   if(!response.ok)throw Error('원본 수신 실패 HTTP '+response.status);
   return new Uint8Array(await response.arrayBuffer());
  }throw Error('인증 실패');
 }
 panel.addEventListener('click',async e=>{const button=e.target.closest('[data-dataset-export]');if(!button)return;
  button.disabled=true;
  try{
   const id=button.dataset.datasetExport;const pack=await rpc('dev_dataset_admin_export',{p_id:id});
   const entries=[...tarEntry(id+'/manifest.json',encoder.encode(JSON.stringify(pack.manifest)))];
   let expectedSeq=0;for(let i=0;i<pack.parts.length;i++){
    const item=pack.parts[i],p=item.detail;
    if(p.part!==i||p.firstSeq!==expectedSeq||p.eventCount!==p.lastSeq-p.firstSeq+1)throw Error('조각/이벤트 순서 오류');expectedSeq=p.lastSeq+1;
    message.textContent=`원본 확인 중 ${i+1}/${pack.parts.length}`;
    const bytes=await fetchPart(item.path);const hash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)),b=>b.toString(16).padStart(2,'0')).join('');
    if(hash!==p.sha256||bytes.length!==p.bytes)throw Error('원본 무결성 확인 실패');
    const base='part_'+String(i).padStart(6,'0');entries.push(...tarEntry(id+'/'+base+'.jsonl.gz',bytes),...tarEntry(id+'/'+base+'.meta.json',encoder.encode(JSON.stringify(p))));
   }
   if(expectedSeq!==pack.manifest.eventCount||pack.parts.length!==pack.manifest.partCount)throw Error('기록 완료 정보 불일치');
   entries.push(new Uint8Array(1024));const blob=new Blob(entries,{type:'application/x-tar'}),url=URL.createObjectURL(blob),a=document.createElement('a');
   a.href=url;a.download=id+'.tar';a.click();setTimeout(()=>URL.revokeObjectURL(url),60000);
   message.textContent='다운로드 완료. 압축을 푼 세션 폴더를 analyze_dataset.py에 전달하세요. 원본 GPS가 들어 있으므로 비공개로 보관하세요.';
  }catch(e){message.textContent=e.message||String(e);}finally{button.disabled=false;}
 });
})();
