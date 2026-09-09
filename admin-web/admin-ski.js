'use strict';

(() => {
  let loaded = false;
  let resorts = [];
  const ski$ = (id) => document.getElementById(id);

  function coord(lat, lon) {
    const a = Number(lat), b = Number(lon);
    if (!Number.isFinite(a) || !Number.isFinite(b)) return '-';
    return `${a.toFixed(5)}, ${b.toFixed(5)}`;
  }

  function pct(value) {
    const n = Number(value);
    if (!Number.isFinite(n)) return '-';
    return `${Math.round(n * 100)}%`;
  }

  function resortOptions(selected = '') {
    if (!resorts.length) return '<option value="">스키장 먼저 등록</option>';
    return `<option value="">스키장 선택</option>${resorts.map((r) =>
      `<option value="${esc(r.resort_key)}" ${r.resort_key === selected ? 'selected' : ''}>${esc(r.resort_name)} · ${esc(r.resort_key)}</option>`
    ).join('')}`;
  }

  function suggestionTypeLabel(type) {
    return ({ name: '이름 제안', correction: '이름 수정', new_lift: '새 리프트' })[type] || type || '제안';
  }

  function renderSuggestions(items) {
    const wrap = ski$('skiSuggestions');
    if (!items.length) {
      wrap.innerHTML = '<div class="empty-state">검토 대기 중인 리프트 이름 제안이 없습니다.</div>';
      return;
    }
    wrap.innerHTML = items.map((s) => {
      const needsResort = !s.lift_id;
      return `<article class="ski-review-card" data-suggestion-id="${esc(s.suggestion_id)}">
        <div class="ski-review-head">
          <div>
            <span class="pill">${esc(suggestionTypeLabel(s.suggestion_type))}</span>
            <h3>${esc(s.proposed_name || '이름 없음')}</h3>
          </div>
          <span class="muted tiny">${esc(formatDate(s.created_at))}</span>
        </div>
        <div class="ski-review-grid">
          <div><span>현재 이름</span><strong>${esc(s.current_name || '-')}</strong></div>
          <div><span>스키장</span><strong>${esc(s.resort_name || s.resort_key || '미지정')}</strong></div>
          <div><span>하단</span><strong>${esc(coord(s.lower_lat, s.lower_lon))}</strong></div>
          <div><span>상단</span><strong>${esc(coord(s.upper_lat, s.upper_lon))}</strong></div>
        </div>
        ${needsResort ? `<label class="ski-field compact"><span>등록할 스키장</span><select class="ski-suggestion-resort">${resortOptions(s.resort_key || '')}</select></label>` : ''}
        <div class="ski-action-row">
          <button class="soft-button ski-approve-suggestion" type="button">승인</button>
          <button class="ghost-button ski-reject-suggestion" type="button">거절</button>
        </div>
      </article>`;
    }).join('');
  }

  function renderUnmatched(items) {
    const wrap = ski$('skiUnmatched');
    if (!items.length) {
      wrap.innerHTML = '<div class="empty-state">아직 미등록 리프트 관측 후보가 없습니다.</div>';
      return;
    }
    wrap.innerHTML = items.map((o) => `<article class="ski-review-card" data-observation-id="${esc(o.observation_id)}">
      <div class="ski-review-head">
        <div>
          <span class="pill">미등록 관측</span>
          <h3>${esc(o.proposed_lift_name || '이름 없는 리프트')}</h3>
        </div>
        <span class="muted tiny">${esc(formatDate(o.created_at))}</span>
      </div>
      <div class="ski-review-grid">
        <div><span>대기</span><strong>${esc(formatDuration(o.wait_seconds))}</strong></div>
        <div><span>리프트 이동</span><strong>${esc(formatDuration(o.ride_seconds))}</strong></div>
        <div><span>상승</span><strong>${esc(Math.round(Number(o.ascent_m || 0)))} m</strong></div>
        <div><span>감지 신뢰도</span><strong>${esc(pct(o.confidence))}</strong></div>
        <div><span>하단</span><strong>${esc(coord(o.lower_lat, o.lower_lon))}</strong></div>
        <div><span>상단</span><strong>${esc(coord(o.upper_lat, o.upper_lon))}</strong></div>
      </div>
      <div class="ski-register-row">
        <label class="ski-field"><span>스키장</span><select class="ski-unmatched-resort">${resortOptions(o.resort_key || '')}</select></label>
        <label class="ski-field"><span>리프트 이름</span><input class="ski-unmatched-name" maxlength="80" value="${esc(o.proposed_lift_name || '')}" placeholder="예: Summit Chair" /></label>
      </div>
      <button class="primary-button ski-register-observation" type="button">이 리프트 등록</button>
      <p class="muted tiny ski-card-note">등록하면 같은 선형의 미확인 과거 기록도 찾아 통계에 자동 편입합니다.</p>
    </article>`).join('');
  }

  function renderResorts(items) {
    const wrap = ski$('skiResortList');
    if (!items.length) {
      wrap.innerHTML = '<div class="empty-state">등록된 스키장이 없습니다. 위에서 첫 스키장을 등록하세요.</div>';
      return;
    }
    wrap.innerHTML = items.map((r) => `<article class="ski-resort-chip">
      <div><strong>${esc(r.resort_name)}</strong><span>${esc(r.resort_key)} · ${esc(r.time_zone)}</span></div>
      <b>${esc(int(r.lift_count))}개 리프트</b>
    </article>`).join('');
  }

  async function loadSkiAdmin(force = false) {
    if (loaded && !force) return;
    setGlobalMessage('');
    try {
      const data = await rpc('admin_ski_overview');
      const s = data?.summary || {};
      resorts = Array.isArray(data?.resorts) ? data.resorts : [];
      ski$('skiSummary').innerHTML = [
        summaryCard('스키장', `${int(s.resorts)}곳`),
        summaryCard('등록 리프트', `${int(s.lifts)}개`),
        summaryCard('이름 검토 대기', `${int(s.pending_suggestions)}건`),
        summaryCard('미등록 관측', `${int(s.unmatched_observations)}건`),
        summaryCard('과거 통계 표본', `${int(s.historical_samples)}건`),
        summaryCard('최근 실시간', `${int(s.realtime_reports)}건`),
      ].join('');
      renderResorts(resorts);
      renderSuggestions(Array.isArray(data?.suggestions) ? data.suggestions : []);
      renderUnmatched(Array.isArray(data?.unmatched) ? data.unmatched : []);
      ski$('skiUpdated').textContent = `갱신 ${new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(new Date())}`;
      loaded = true;
    } catch (e) {
      setGlobalMessage(e.message || '스키 리프트 관리 데이터를 불러오지 못했습니다.');
    }
  }

  async function createResort(event) {
    event.preventDefault();
    const button = ski$('skiResortCreateButton');
    const name = ski$('skiResortName').value.trim();
    const key = ski$('skiResortKey').value.trim().toLowerCase();
    const latText = ski$('skiResortLat').value.trim();
    const lonText = ski$('skiResortLon').value.trim();
    const timeZone = ski$('skiResortTimezone').value.trim() || 'Asia/Seoul';
    const country = ski$('skiResortCountry').value.trim().toUpperCase() || 'KR';
    if (!name || !key) return setGlobalMessage('스키장 이름과 스키장 키를 입력해 주세요.');
    if (!!latText !== !!lonText) return setGlobalMessage('스키장 중심 좌표는 위도·경도를 함께 입력해 주세요.');
    const lat = latText ? Number(latText) : null;
    const lon = lonText ? Number(lonText) : null;
    if ((latText && !Number.isFinite(lat)) || (lonText && !Number.isFinite(lon))) return setGlobalMessage('스키장 좌표를 확인해 주세요.');

    button.disabled = true;
    button.textContent = '등록 중…';
    try {
      await rpc('admin_ski_create_resort', {
        p_resort_name: name,
        p_resort_key: key,
        p_center_lat: lat,
        p_center_lon: lon,
        p_time_zone: timeZone,
        p_country_code: country,
      });
      ski$('skiResortForm').reset();
      ski$('skiResortTimezone').value = 'Asia/Seoul';
      ski$('skiResortCountry').value = 'KR';
      loaded = false;
      setGlobalMessage(`${name} 스키장을 등록했습니다.`, 'success');
      await loadSkiAdmin(true);
    } catch (e) {
      setGlobalMessage(e.message || '스키장을 등록하지 못했습니다.');
    } finally {
      button.disabled = false;
      button.textContent = '스키장 등록';
    }
  }

  async function reviewSuggestion(card, action) {
    const id = card.dataset.suggestionId;
    const select = card.querySelector('.ski-suggestion-resort');
    const resortKey = select?.value || null;
    if (action === 'approve' && select && !resortKey) return setGlobalMessage('새 리프트를 승인하려면 등록할 스키장을 선택해 주세요.');
    const name = card.querySelector('h3')?.textContent || '리프트';
    if (!confirm(action === 'approve' ? `${name} 제안을 승인할까요?` : `${name} 제안을 거절할까요?`)) return;

    card.classList.add('busy');
    try {
      const result = await rpc('admin_ski_review_suggestion', {
        p_suggestion_id: id,
        p_action: action,
        p_resort_key: resortKey,
      });
      loaded = false;
      const backfilled = Number(result?.backfilled_observations || 0);
      setGlobalMessage(action === 'approve' ? `승인했습니다.${backfilled ? ` 과거 ${backfilled}건을 통계에 편입했습니다.` : ''}` : '제안을 거절했습니다.', 'success');
      await loadSkiAdmin(true);
    } catch (e) {
      setGlobalMessage(e.message || '제안을 처리하지 못했습니다.');
    } finally {
      card.classList.remove('busy');
    }
  }

  async function registerObservation(card) {
    const observationId = card.dataset.observationId;
    const resortKey = card.querySelector('.ski-unmatched-resort')?.value || '';
    const liftName = card.querySelector('.ski-unmatched-name')?.value.trim() || '';
    if (!resortKey) return setGlobalMessage('등록할 스키장을 선택해 주세요.');
    if (!liftName) return setGlobalMessage('리프트 이름을 입력해 주세요.');
    if (!confirm(`${liftName} 리프트로 등록할까요? 같은 선형의 과거 기록도 함께 통계에 편입됩니다.`)) return;

    card.classList.add('busy');
    try {
      const result = await rpc('admin_ski_create_lift_from_observation', {
        p_observation_id: observationId,
        p_resort_key: resortKey,
        p_lift_name: liftName,
      });
      loaded = false;
      setGlobalMessage(`${liftName} 등록 완료 · 과거 ${int(result?.backfilled_observations)}건 통계 편입`, 'success');
      await loadSkiAdmin(true);
    } catch (e) {
      setGlobalMessage(e.message || '리프트를 등록하지 못했습니다.');
    } finally {
      card.classList.remove('busy');
    }
  }

  const panel = ski$('skiPanel');
  const tab = document.querySelector('.tab[data-tab="ski"]');
  const refresh = ski$('refreshButton');
  const reload = ski$('skiReload');
  const form = ski$('skiResortForm');

  if (!panel || !tab) return;

  window.loadSkiAdmin = loadSkiAdmin;
  tab.addEventListener('click', () => loadSkiAdmin());
  reload?.addEventListener('click', () => loadSkiAdmin(true));
  refresh?.addEventListener('click', () => {
    if (document.querySelector('.tab.active')?.dataset.tab === 'ski') loadSkiAdmin(true);
  });
  form?.addEventListener('submit', createResort);

  panel.addEventListener('click', (event) => {
    const approve = event.target.closest('.ski-approve-suggestion');
    const reject = event.target.closest('.ski-reject-suggestion');
    const register = event.target.closest('.ski-register-observation');
    if (approve) return reviewSuggestion(approve.closest('.ski-review-card'), 'approve');
    if (reject) return reviewSuggestion(reject.closest('.ski-review-card'), 'reject');
    if (register) return registerObservation(register.closest('.ski-review-card'));
  });
})();
