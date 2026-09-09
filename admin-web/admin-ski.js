'use strict';

(() => {
  let loaded = false;
  let resorts = [];
  let lifts = [];
  let suggestions = [];
  let unmatched = [];
  let maplibrePromise = null;
  let reviewMap = null;
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

  function distanceM(lat1, lon1, lat2, lon2) {
    const a1 = Number(lat1), o1 = Number(lon1), a2 = Number(lat2), o2 = Number(lon2);
    if (![a1, o1, a2, o2].every(Number.isFinite)) return Number.POSITIVE_INFINITY;
    const r = Math.PI / 180;
    const p1 = a1 * r, p2 = a2 * r;
    const dp = (a2 - a1) * r, dl = (o2 - o1) * r;
    const x = Math.sin(dp / 2) ** 2 + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) ** 2;
    return 6371000 * 2 * Math.asin(Math.min(1, Math.sqrt(x)));
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

  function candidatesFor(item, resortKey = '') {
    const key = resortKey || item?.resort_key || '';
    return lifts
      .filter((l) => !key || l.resort_key === key)
      .map((l) => {
        const lowerM = distanceM(item?.lower_lat, item?.lower_lon, l.lower_lat, l.lower_lon);
        const upperM = distanceM(item?.upper_lat, item?.upper_lon, l.upper_lat, l.upper_lon);
        const strong = lowerM <= 220 && upperM <= 260;
        const near = lowerM <= 500 && upperM <= 600;
        return { ...l, lowerM, upperM, strong, near, score: lowerM + upperM };
      })
      .filter((l) => l.near)
      .sort((a, b) => a.score - b.score);
  }

  function duplicateNotice(item, type, resortKey = '') {
    if (!Number.isFinite(Number(item?.lower_lat)) || !Number.isFinite(Number(item?.upper_lat))) return '';
    const candidates = candidatesFor(item, resortKey);
    const strong = candidates.filter((c) => c.strong);
    const best = strong[0] || candidates[0];
    if (!best) {
      return `<div class="ski-duplicate-box clear"><strong>✓ 주변 등록 리프트와 뚜렷한 중복 없음</strong><button class="ghost-button ski-open-map" data-kind="${type}" type="button">지도 비교</button></div>`;
    }
    const label = best.lift_name || best.lift_key || '등록 리프트';
    const level = best.strong ? 'warning' : 'near';
    const title = best.strong ? '⚠ 기존 리프트와 강하게 일치' : '주변에 비슷한 리프트 있음';
    return `<div class="ski-duplicate-box ${level}">
      <div><strong>${esc(title)}</strong><span>${esc(label)} · 하단 ${esc(Math.round(best.lowerM))}m · 상단 ${esc(Math.round(best.upperM))}m</span></div>
      <button class="soft-button ski-open-map" data-kind="${type}" type="button">지도 비교</button>
    </div>`;
  }

  function renderSuggestions(items) {
    const wrap = ski$('skiSuggestions');
    suggestions = items;
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
        ${needsResort ? `<div class="ski-duplicate-slot">${duplicateNotice(s, 'suggestion', s.resort_key || '')}</div>` : ''}
        <div class="ski-action-row">
          <button class="soft-button ski-approve-suggestion" type="button">승인</button>
          <button class="ghost-button ski-reject-suggestion" type="button">거절</button>
        </div>
      </article>`;
    }).join('');
  }

  function renderUnmatched(items) {
    const wrap = ski$('skiUnmatched');
    unmatched = items;
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
      <div class="ski-duplicate-slot">${duplicateNotice(o, 'observation', o.resort_key || '')}</div>
      <button class="primary-button ski-register-observation" type="button">새 리프트로 등록</button>
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
      lifts = Array.isArray(data?.lifts) ? data.lifts : [];
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

  function itemForCard(card, kind) {
    if (kind === 'suggestion') return suggestions.find((s) => s.suggestion_id === card.dataset.suggestionId) || null;
    return unmatched.find((o) => o.observation_id === card.dataset.observationId) || null;
  }

  function selectedResort(card, kind, item) {
    if (kind === 'suggestion') return card.querySelector('.ski-suggestion-resort')?.value || item?.resort_key || '';
    return card.querySelector('.ski-unmatched-resort')?.value || item?.resort_key || '';
  }

  function refreshDuplicateNotice(card, kind) {
    const item = itemForCard(card, kind);
    const slot = card.querySelector('.ski-duplicate-slot');
    if (!item || !slot) return;
    slot.innerHTML = duplicateNotice(item, kind, selectedResort(card, kind, item));
  }

  async function reviewSuggestion(card, action) {
    const id = card.dataset.suggestionId;
    const item = itemForCard(card, 'suggestion');
    const select = card.querySelector('.ski-suggestion-resort');
    const resortKey = select?.value || null;
    if (action === 'approve' && select && !resortKey) return setGlobalMessage('새 리프트를 승인하려면 등록할 스키장을 선택해 주세요.');
    const name = card.querySelector('h3')?.textContent || '리프트';

    if (action === 'approve' && item && !item.lift_id) {
      const strong = candidatesFor(item, resortKey || '').filter((c) => c.strong);
      if (strong.length) {
        const best = strong[0];
        const existing = best.lift_name || best.lift_key;
        if (!confirm(`기존 ${existing} 리프트와 강하게 일치합니다.\n하단 ${Math.round(best.lowerM)}m · 상단 ${Math.round(best.upperM)}m\n\n그래도 새 리프트로 승인할까요? 먼저 지도 비교 또는 기존 리프트 연결을 권장합니다.`)) return;
      } else if (!confirm(`${name} 제안을 승인할까요?`)) return;
    } else if (!confirm(action === 'approve' ? `${name} 제안을 승인할까요?` : `${name} 제안을 거절할까요?`)) return;

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
    const item = itemForCard(card, 'observation');
    const resortKey = card.querySelector('.ski-unmatched-resort')?.value || '';
    const liftName = card.querySelector('.ski-unmatched-name')?.value.trim() || '';
    if (!resortKey) return setGlobalMessage('등록할 스키장을 선택해 주세요.');
    if (!liftName) return setGlobalMessage('리프트 이름을 입력해 주세요.');

    const strong = item ? candidatesFor(item, resortKey).filter((c) => c.strong) : [];
    if (strong.length) {
      const best = strong[0];
      const existing = best.lift_name || best.lift_key;
      if (!confirm(`⚠ ${existing} 리프트와 강하게 일치합니다.\n하단 ${Math.round(best.lowerM)}m · 상단 ${Math.round(best.upperM)}m\n\n중복 등록 가능성이 있습니다. 그래도 새 리프트 ${liftName}(으)로 등록할까요?`)) return;
    } else if (!confirm(`${liftName} 리프트로 등록할까요? 같은 선형의 과거 기록도 함께 통계에 편입됩니다.`)) return;

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

  async function linkObservation(item, candidate) {
    if (!confirm(`${candidate.lift_name || candidate.lift_key} 리프트에 연결할까요?\n하단 ${Math.round(candidate.lowerM)}m · 상단 ${Math.round(candidate.upperM)}m\n\n같은 선형의 다른 미확인 기록도 함께 통계에 편입됩니다.`)) return;
    try {
      const result = await rpc('admin_ski_link_observation_to_lift', {
        p_observation_id: item.observation_id,
        p_lift_id: candidate.lift_id,
      });
      closeReviewMap();
      loaded = false;
      setGlobalMessage(`${candidate.lift_name || candidate.lift_key}에 연결 완료 · 과거 ${int(result?.backfilled_observations)}건 통계 편입`, 'success');
      await loadSkiAdmin(true);
    } catch (e) {
      setGlobalMessage(e.message || '기존 리프트에 연결하지 못했습니다.');
    }
  }

  async function linkSuggestion(item, candidate) {
    if (!confirm(`${item.proposed_name} 제안을 기존 ${candidate.lift_name || candidate.lift_key} 리프트에 연결할까요?`)) return;
    const applyName = confirm(`기존 리프트 이름도 “${item.proposed_name}”(으)로 변경할까요?\n\n확인: 이름도 변경\n취소: 기존 이름 유지`);
    try {
      const result = await rpc('admin_ski_link_suggestion_to_lift', {
        p_suggestion_id: item.suggestion_id,
        p_lift_id: candidate.lift_id,
        p_apply_proposed_name: applyName,
      });
      closeReviewMap();
      loaded = false;
      setGlobalMessage(`기존 리프트 연결 완료${applyName ? ' · 이름도 반영' : ''} · 과거 ${int(result?.backfilled_observations)}건 통계 편입`, 'success');
      await loadSkiAdmin(true);
    } catch (e) {
      setGlobalMessage(e.message || '기존 리프트에 제안을 연결하지 못했습니다.');
    }
  }

  function ensureModal() {
    let modal = ski$('skiMapModal');
    if (modal) return modal;
    modal = document.createElement('div');
    modal.id = 'skiMapModal';
    modal.className = 'ski-map-modal hidden';
    modal.innerHTML = `<div class="ski-map-dialog" role="dialog" aria-modal="true" aria-labelledby="skiMapTitle">
      <div class="ski-map-head"><div><p class="eyebrow">LIFT MAP REVIEW</p><h2 id="skiMapTitle">리프트 지도 비교</h2></div><button class="ghost-button ski-map-close" type="button">닫기</button></div>
      <div id="skiMapWarning" class="ski-map-warning"></div>
      <div id="skiReviewMap" class="ski-review-map"></div>
      <div class="ski-map-legend"><span><i class="target"></i>검토 대상</span><span><i class="existing"></i>기존 리프트</span></div>
      <div id="skiMapCandidates" class="ski-map-candidates"></div>
    </div>`;
    document.body.appendChild(modal);
    modal.addEventListener('click', (event) => {
      if (event.target === modal || event.target.closest('.ski-map-close')) closeReviewMap();
      const link = event.target.closest('.ski-link-existing');
      if (link) {
        const kind = modal.dataset.kind;
        const itemId = modal.dataset.itemId;
        const liftId = link.dataset.liftId;
        const item = kind === 'suggestion' ? suggestions.find((s) => s.suggestion_id === itemId) : unmatched.find((o) => o.observation_id === itemId);
        const candidate = candidatesFor(item, modal.dataset.resortKey || '').find((c) => c.lift_id === liftId);
        if (!item || !candidate) return;
        if (kind === 'suggestion') linkSuggestion(item, candidate);
        else linkObservation(item, candidate);
      }
    });
    return modal;
  }

  function ensureMapLibre() {
    if (!maplibrePromise) {
      maplibrePromise = import('https://unpkg.com/maplibre-gl@6.8.0/dist/maplibre-gl.mjs');
    }
    return maplibrePromise;
  }

  function closeReviewMap() {
    const modal = ski$('skiMapModal');
    if (reviewMap) {
      try { reviewMap.remove(); } catch (_) {}
      reviewMap = null;
    }
    if (modal) modal.classList.add('hidden');
  }

  function mapNearbyLifts(item, resortKey) {
    return lifts.filter((l) => {
      if (resortKey && l.resort_key !== resortKey) return false;
      const d1 = distanceM(item.lower_lat, item.lower_lon, l.lower_lat, l.lower_lon);
      const d2 = distanceM(item.lower_lat, item.lower_lon, l.upper_lat, l.upper_lon);
      return Math.min(d1, d2) <= 3500;
    });
  }

  async function openReviewMap(card, kind) {
    const item = itemForCard(card, kind);
    if (!item) return;
    const resortKey = selectedResort(card, kind, item);
    const modal = ensureModal();
    const candidates = candidatesFor(item, resortKey);
    const strong = candidates.filter((c) => c.strong);
    modal.dataset.kind = kind;
    modal.dataset.itemId = kind === 'suggestion' ? item.suggestion_id : item.observation_id;
    modal.dataset.resortKey = resortKey;
    modal.classList.remove('hidden');

    ski$('skiMapWarning').innerHTML = strong.length
      ? `<strong>⚠ 강한 중복 후보 ${strong.length}개</strong><span>하단 220m 이내 + 상단 260m 이내인 기존 리프트가 있습니다. 새 등록 전에 연결 여부를 확인하세요.</span>`
      : '<strong>주변 리프트 비교</strong><span>노란 선이 검토 대상이고 기존 등록 리프트는 지도에서 함께 표시됩니다.</span>';

    ski$('skiMapCandidates').innerHTML = candidates.length ? candidates.slice(0, 8).map((c) => `<article class="ski-map-candidate ${c.strong ? 'strong' : ''}">
      <div><strong>${esc(c.lift_name || c.lift_key)}</strong><span>${esc(c.resort_name || c.resort_key)} · 하단 ${esc(Math.round(c.lowerM))}m · 상단 ${esc(Math.round(c.upperM))}m</span></div>
      ${c.strong ? `<button class="soft-button ski-link-existing" data-lift-id="${esc(c.lift_id)}" type="button">기존 리프트로 연결</button>` : '<span class="muted tiny">근접 후보</span>'}
    </article>`).join('') : '<div class="empty-state">500~600m 범위 안에 비슷한 기존 리프트가 없습니다.</div>';

    const container = ski$('skiReviewMap');
    container.innerHTML = '<div class="ski-map-loading">지도 불러오는 중…</div>';
    try {
      const maplibregl = await ensureMapLibre();
      container.innerHTML = '';
      const centerLat = (Number(item.lower_lat) + Number(item.upper_lat)) / 2;
      const centerLon = (Number(item.lower_lon) + Number(item.upper_lon)) / 2;
      reviewMap = new maplibregl.Map({
        container,
        style: 'https://tiles.openfreemap.org/styles/liberty',
        center: [centerLon, centerLat],
        zoom: 14,
      });
      reviewMap.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right');

      reviewMap.on('load', () => {
        const nearby = mapNearbyLifts(item, resortKey);
        const features = [
          {
            type: 'Feature',
            properties: { kind: 'target', label: '검토 대상' },
            geometry: { type: 'LineString', coordinates: [[Number(item.lower_lon), Number(item.lower_lat)], [Number(item.upper_lon), Number(item.upper_lat)]] },
          },
          ...nearby.map((l) => ({
            type: 'Feature',
            properties: { kind: 'existing', label: l.lift_name || l.lift_key },
            geometry: { type: 'LineString', coordinates: [[Number(l.lower_lon), Number(l.lower_lat)], [Number(l.upper_lon), Number(l.upper_lat)]] },
          })),
        ];
        reviewMap.addSource('ski-review-lines', { type: 'geojson', data: { type: 'FeatureCollection', features } });
        reviewMap.addLayer({
          id: 'ski-existing-lines',
          type: 'line',
          source: 'ski-review-lines',
          filter: ['==', ['get', 'kind'], 'existing'],
          paint: { 'line-color': '#2563eb', 'line-width': 4, 'line-opacity': 0.68 },
        });
        reviewMap.addLayer({
          id: 'ski-target-line',
          type: 'line',
          source: 'ski-review-lines',
          filter: ['==', ['get', 'kind'], 'target'],
          paint: { 'line-color': '#f59e0b', 'line-width': 7, 'line-opacity': 0.95 },
        });

        const start = document.createElement('div');
        start.className = 'ski-map-point target';
        start.textContent = '하단';
        new maplibregl.Marker({ element: start }).setLngLat([Number(item.lower_lon), Number(item.lower_lat)]).addTo(reviewMap);
        const end = document.createElement('div');
        end.className = 'ski-map-point target';
        end.textContent = '상단';
        new maplibregl.Marker({ element: end }).setLngLat([Number(item.upper_lon), Number(item.upper_lat)]).addTo(reviewMap);

        nearby.forEach((l) => {
          const marker = document.createElement('div');
          marker.className = 'ski-map-point existing';
          marker.textContent = l.lift_name || l.lift_key;
          new maplibregl.Marker({ element: marker }).setLngLat([Number(l.lower_lon), Number(l.lower_lat)]).addTo(reviewMap);
        });

        const bounds = new maplibregl.LngLatBounds();
        bounds.extend([Number(item.lower_lon), Number(item.lower_lat)]);
        bounds.extend([Number(item.upper_lon), Number(item.upper_lat)]);
        nearby.forEach((l) => {
          bounds.extend([Number(l.lower_lon), Number(l.lower_lat)]);
          bounds.extend([Number(l.upper_lon), Number(l.upper_lat)]);
        });
        reviewMap.fitBounds(bounds, { padding: 58, maxZoom: 16, duration: 450 });
      });
    } catch (e) {
      container.innerHTML = `<div class="empty-state">지도를 불러오지 못했습니다. ${esc(e?.message || '')}</div>`;
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

  panel.addEventListener('change', (event) => {
    const select = event.target.closest('.ski-suggestion-resort, .ski-unmatched-resort');
    if (!select) return;
    const card = select.closest('.ski-review-card');
    refreshDuplicateNotice(card, card.dataset.suggestionId ? 'suggestion' : 'observation');
  });

  panel.addEventListener('click', (event) => {
    const approve = event.target.closest('.ski-approve-suggestion');
    const reject = event.target.closest('.ski-reject-suggestion');
    const register = event.target.closest('.ski-register-observation');
    const map = event.target.closest('.ski-open-map');
    if (approve) return reviewSuggestion(approve.closest('.ski-review-card'), 'approve');
    if (reject) return reviewSuggestion(reject.closest('.ski-review-card'), 'reject');
    if (register) return registerObservation(register.closest('.ski-review-card'));
    if (map) return openReviewMap(map.closest('.ski-review-card'), map.dataset.kind || (map.closest('.ski-review-card')?.dataset.suggestionId ? 'suggestion' : 'observation'));
  });
})();
