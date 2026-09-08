'use strict';

(() => {
  let running = false;

  function ensureCard() {
    let card = document.getElementById('uploadReadinessCard');
    if (card) return card;

    const panel = document.getElementById('dashboardPanel');
    const summary = document.getElementById('summaryCards');
    if (!panel || !summary) return null;

    card = document.createElement('article');
    card.id = 'uploadReadinessCard';
    card.className = 'info-card readiness-card';
    card.innerHTML = `
      <div class="section-head compact">
        <div>
          <p class="eyebrow">APP UPLOAD CHECK</p>
          <h3>앱 업로드 준비 상태</h3>
        </div>
        <span id="readinessOverall" class="pill readiness-pill">확인 중…</span>
      </div>
      <div class="metric-pair"><span>익명 로그인</span><strong id="readinessAnonymous">-</strong></div>
      <div class="metric-pair"><span>서버 업로드</span><strong id="readinessUploads">-</strong></div>
      <div class="metric-pair"><span>관리자 API</span><strong id="readinessAdminApi">-</strong></div>
      <p id="readinessHint" class="muted tiny readiness-hint">실제 기록을 만들지 않고 서버 설정만 확인합니다.</p>`;
    summary.insertAdjacentElement('afterend', card);
    return card;
  }

  function setValue(id, text, ok) {
    const el = document.getElementById(id);
    if (!el) return;
    el.textContent = text;
    el.classList.remove('readiness-ok', 'readiness-bad');
    if (ok === true) el.classList.add('readiness-ok');
    if (ok === false) el.classList.add('readiness-bad');
  }

  async function authSettings() {
    const response = await fetch(`${SUPABASE_URL}/auth/v1/settings`, {
      headers: { apikey: PUBLISHABLE_KEY },
      cache: 'no-store',
    });
    if (!response.ok) throw new Error(`Auth 설정 확인 실패 (${response.status})`);
    return response.json();
  }

  async function checkReadiness() {
    if (running || !state?.session?.accessToken) return;
    ensureCard();
    running = true;

    const overall = document.getElementById('readinessOverall');
    const hint = document.getElementById('readinessHint');
    if (overall) {
      overall.textContent = '확인 중…';
      overall.classList.remove('ready', 'blocked');
    }

    let anonymousOk = false;
    let uploadsOk = false;
    let adminOk = false;
    const notes = [];

    try {
      const settings = await authSettings();
      anonymousOk = settings?.external?.anonymous_users === true;
      setValue('readinessAnonymous', anonymousOk ? '켜짐' : '꺼짐', anonymousOk);
      if (!anonymousOk) notes.push('Supabase Authentication에서 Anonymous Sign-Ins를 켜야 합니다.');
    } catch (error) {
      setValue('readinessAnonymous', '확인 실패', false);
      notes.push(error?.message || '익명 로그인 설정을 확인하지 못했습니다.');
    }

    try {
      const dashboard = await rpc('admin_dashboard_summary');
      adminOk = true;
      uploadsOk = dashboard?.limits?.uploads_enabled !== false;
      setValue('readinessAdminApi', '정상', true);
      setValue('readinessUploads', uploadsOk ? '정상' : '잠김', uploadsOk);
      if (!uploadsOk) notes.push('무료 운영 안전 한도로 앱 업로드가 현재 잠겨 있습니다.');
    } catch (error) {
      setValue('readinessAdminApi', '오류', false);
      setValue('readinessUploads', '확인 실패', false);
      notes.push(error?.message || '관리자 API 상태를 확인하지 못했습니다.');
    }

    const ready = anonymousOk && uploadsOk && adminOk;
    if (overall) {
      overall.textContent = ready ? '실기기 테스트 가능' : '확인 필요';
      overall.classList.toggle('ready', ready);
      overall.classList.toggle('blocked', !ready);
    }
    if (hint) {
      hint.textContent = ready
        ? '서버 준비가 끝났습니다. 휴대폰에서 완료된 기록 1건을 직접 업로드하면 됩니다.'
        : notes.join(' ');
    }
    running = false;
  }

  window.addEventListener('DOMContentLoaded', () => {
    ensureCard();
    const app = document.getElementById('appView');
    if (app) {
      const observer = new MutationObserver(() => {
        if (!app.classList.contains('hidden')) checkReadiness();
      });
      observer.observe(app, { attributes: true, attributeFilter: ['class'] });
    }
    const refresh = document.getElementById('refreshButton');
    if (refresh) refresh.addEventListener('click', () => setTimeout(checkReadiness, 0));
    if (app && !app.classList.contains('hidden')) checkReadiness();
  });
})();
