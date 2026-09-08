'use strict';

const SUPABASE_URL = 'https://kumucxomdviuwuwqcpmv.supabase.co';
const PUBLISHABLE_KEY = 'sb_publishable_YYZp1A9A62KUxs5etSAiYg_0TvaK2hQ';
const SESSION_KEY = 'yamone_admin_session_v1';

const state = {
  session: null,
  activityLoaded: false,
  sleepLoaded: false,
};

const $ = (id) => document.getElementById(id);
const loginView = $('loginView');
const appView = $('appView');
const loginForm = $('loginForm');
const loginButton = $('loginButton');
const loginMessage = $('loginMessage');
const globalMessage = $('globalMessage');

function esc(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function decodeJwt(token) {
  try {
    const part = token.split('.')[1];
    if (!part) return {};
    const normalized = part.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
    const text = decodeURIComponent(atob(padded).split('').map((c) =>
      '%' + c.charCodeAt(0).toString(16).padStart(2, '0')
    ).join(''));
    return JSON.parse(text);
  } catch (_) {
    return {};
  }
}

function isAdminToken(token) {
  const claims = decodeJwt(token);
  return claims?.app_metadata?.role === 'admin';
}

function sessionEmail(token) {
  const claims = decodeJwt(token);
  return claims?.email || '관리자';
}

function saveSession(raw) {
  const expiresAt = Date.now() + Math.max(60, Number(raw.expires_in || 3600)) * 1000;
  state.session = {
    accessToken: raw.access_token,
    refreshToken: raw.refresh_token || '',
    expiresAt,
  };
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(state.session));
}

function readStoredSession() {
  try {
    const raw = JSON.parse(sessionStorage.getItem(SESSION_KEY) || 'null');
    if (!raw?.accessToken) return null;
    return raw;
  } catch (_) {
    return null;
  }
}

function clearSession() {
  state.session = null;
  state.activityLoaded = false;
  state.sleepLoaded = false;
  sessionStorage.removeItem(SESSION_KEY);
}

async function authPost(path, body, accessToken = PUBLISHABLE_KEY) {
  const response = await fetch(SUPABASE_URL + path, {
    method: 'POST',
    headers: {
      apikey: PUBLISHABLE_KEY,
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body || {}),
  });
  const text = await response.text();
  let data = {};
  try { data = text ? JSON.parse(text) : {}; } catch (_) { data = { message: text }; }
  if (!response.ok) {
    const error = new Error(data?.msg || data?.message || data?.error_description || '인증 요청에 실패했습니다.');
    error.status = response.status;
    throw error;
  }
  return data;
}

async function signIn(email, password) {
  const data = await authPost('/auth/v1/token?grant_type=password', { email, password });
  if (!data.access_token) throw new Error('로그인 세션을 만들 수 없습니다.');
  if (!isAdminToken(data.access_token)) {
    throw new Error('관리자 권한이 없는 계정입니다.');
  }
  saveSession(data);
}

async function refreshSession() {
  if (!state.session?.refreshToken) throw new Error('다시 로그인해 주세요.');
  const data = await authPost('/auth/v1/token?grant_type=refresh_token', {
    refresh_token: state.session.refreshToken,
  });
  if (!data.access_token || !isAdminToken(data.access_token)) {
    throw new Error('관리자 권한을 확인할 수 없습니다.');
  }
  saveSession(data);
}

async function ensureFreshSession() {
  if (!state.session?.accessToken) throw new Error('로그인이 필요합니다.');
  if (state.session.expiresAt > Date.now() + 90_000) return;
  await refreshSession();
}

async function rpc(name, params = {}, retry = true) {
  await ensureFreshSession();
  const response = await fetch(`${SUPABASE_URL}/rest/v1/rpc/${name}`, {
    method: 'POST',
    headers: {
      apikey: PUBLISHABLE_KEY,
      Authorization: `Bearer ${state.session.accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(params),
  });
  const text = await response.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (_) { data = text; }

  if (response.ok) return data;

  if ((response.status === 401 || response.status === 403) && retry && state.session?.refreshToken) {
    try {
      await refreshSession();
      return await rpc(name, params, false);
    } catch (_) {
      // fall through to the original server error below
    }
  }

  const serverMessage = data?.message || data?.hint || '';
  if (serverMessage.includes('admin_required')) {
    clearSession();
    showLogin('관리자 권한이 필요합니다.');
    throw new Error('관리자 권한이 필요합니다.');
  }
  throw new Error(serverMessage || '관리자 데이터를 불러오지 못했습니다.');
}

async function logout() {
  const token = state.session?.accessToken;
  clearSession();
  if (token) {
    try {
      await fetch(`${SUPABASE_URL}/auth/v1/logout?scope=local`, {
        method: 'POST',
        headers: { apikey: PUBLISHABLE_KEY, Authorization: `Bearer ${token}` },
      });
    } catch (_) {}
  }
  showLogin('');
}

function showLogin(message = '') {
  appView.classList.add('hidden');
  loginView.classList.remove('hidden');
  loginMessage.textContent = message;
  $('passwordInput').value = '';
}

function showApp() {
  loginView.classList.add('hidden');
  appView.classList.remove('hidden');
  $('accountBadge').textContent = sessionEmail(state.session.accessToken);
}

function setGlobalMessage(message = '', kind = 'error') {
  if (!message) {
    globalMessage.textContent = '';
    globalMessage.className = 'global-message hidden';
    return;
  }
  globalMessage.textContent = message;
  globalMessage.className = `global-message ${kind}`;
}

function formatBytes(bytes) {
  const n = Number(bytes || 0);
  if (n < 1024) return `${n} B`;
  if (n < 1024 ** 2) return `${(n / 1024).toFixed(1)} KB`;
  if (n < 1024 ** 3) return `${(n / 1024 ** 2).toFixed(1)} MB`;
  return `${(n / 1024 ** 3).toFixed(2)} GB`;
}

function formatDate(value) {
  if (!value) return '-';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '-';
  return new Intl.DateTimeFormat('ko-KR', {
    year: '2-digit', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(d);
}

function formatDuration(seconds) {
  const total = Math.max(0, Math.round(Number(seconds || 0)));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  if (h > 0) return `${h}시간 ${m}분`;
  if (m > 0) return `${m}분 ${s}초`;
  return `${s}초`;
}

function formatDistance(meters) {
  const m = Number(meters || 0);
  return m >= 1000 ? `${(m / 1000).toFixed(2)} km` : `${Math.round(m)} m`;
}

function formatSpeed(mps) {
  const v = Number(mps || 0);
  return `${(v * 3.6).toFixed(1)} km/h`;
}

function num(value, digits = 2) {
  const n = Number(value);
  if (!Number.isFinite(n)) return '-';
  return n.toFixed(digits);
}

function int(value) {
  const n = Number(value || 0);
  return new Intl.NumberFormat('ko-KR').format(Math.round(n));
}

function summaryCard(label, value) {
  return `<article class="summary-card"><span>${esc(label)}</span><strong>${esc(value)}</strong></article>`;
}

function updateUsageBar(barId, used, warning, stop) {
  const bar = $(barId);
  const pct = stop > 0 ? Math.min(100, (used / stop) * 100) : 0;
  bar.style.width = `${pct}%`;
  bar.classList.remove('warning', 'danger');
  if (used >= stop) bar.classList.add('danger');
  else if (used >= warning) bar.classList.add('warning');
}

async function loadDashboard() {
  setGlobalMessage('');
  try {
    const data = await rpc('admin_dashboard_summary');
    const limits = data?.limits || {};
    const db = Number(data?.db_bytes || 0);
    const storage = Number(data?.storage_bytes || 0);
    const dbWarning = Number(limits.db_warning_bytes || 0);
    const dbStop = Number(limits.db_stop_bytes || 0);
    const storageWarning = Number(limits.storage_warning_bytes || 0);
    const storageStop = Number(limits.storage_stop_bytes || 0);

    $('dbUsageText').textContent = `${formatBytes(db)} / ${formatBytes(dbStop)}`;
    $('dbUsageHint').textContent = `경고 ${formatBytes(dbWarning)} · 업로드 중지 ${formatBytes(dbStop)}`;
    $('storageUsageText').textContent = `${formatBytes(storage)} / ${formatBytes(storageStop)}`;
    $('storageUsageHint').textContent = `경고 ${formatBytes(storageWarning)} · 업로드 중지 ${formatBytes(storageStop)}`;
    updateUsageBar('dbUsageBar', db, dbWarning, dbStop);
    updateUsageBar('storageUsageBar', storage, storageWarning, storageStop);

    $('summaryCards').innerHTML = [
      summaryCard('활동 기록', int(data?.activity_sessions)),
      summaryCard('수면 기록', int(data?.sleep_sessions)),
      summaryCard('삭제 대기', int(data?.pending_deletions)),
      summaryCard('업로드 가능', limits.uploads_enabled === false ? '잠김' : '정상'),
    ].join('');

    $('activityCount').textContent = int(data?.activity_sessions);
    $('activityReceiptCount').textContent = int(data?.activity_upload_receipts);
    $('activityStorage').textContent = formatBytes(data?.activity_storage_bytes);
    $('activity7dPill').textContent = `최근 7일 ${int(data?.activity_uploaded_last_7d)}건`;

    $('sleepCount').textContent = int(data?.sleep_sessions);
    $('sleepReceiptCount').textContent = int(data?.sleep_upload_receipts);
    $('sleepStorage').textContent = formatBytes(data?.sleep_storage_bytes);
    $('sleep7dPill').textContent = `최근 7일 ${int(data?.sleep_uploaded_last_7d)}건`;
    $('lastUpdated').textContent = `갱신 ${new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(new Date())}`;
  } catch (e) {
    setGlobalMessage(e.message || '대시보드를 불러오지 못했습니다.');
  }
}

function activityLabel(type) {
  return ({ walking: '걷기', running: '러닝', walkrun: '걷기/러닝', cycling: '자전거' })[type] || type || '-';
}

async function loadActivity(force = false) {
  if (state.activityLoaded && !force) return;
  setGlobalMessage('');
  try {
    const data = await rpc('admin_activity_records', { p_limit: 100, p_offset: 0 });
    const s = data?.summary || {};
    $('activitySummary').innerHTML = [
      summaryCard('전체', `${int(s.record_count)}건`),
      summaryCard('총 거리', formatDistance(s.total_distance_m)),
      summaryCard('총 시간', formatDuration(s.total_duration_seconds)),
      summaryCard('총 걸음', int(s.total_steps)),
      summaryCard('자전거', `${int(s.cycling_count)}건`),
    ].join('');

    const records = Array.isArray(data?.records) ? data.records : [];
    $('activityEmpty').classList.toggle('hidden', records.length !== 0);
    $('activityTableBody').innerHTML = records.map((r) => `
      <tr>
        <td>${esc(formatDate(r.uploaded_at))}</td>
        <td><span class="pill">${esc(activityLabel(r.activity_type))}</span></td>
        <td>${esc(formatDuration(r.duration_seconds))}</td>
        <td>${esc(formatDistance(r.distance_m))}</td>
        <td>${esc(int(r.steps))}</td>
        <td>${esc(formatSpeed(r.avg_speed_mps))}</td>
        <td>${esc(formatSpeed(r.max_speed_mps))}</td>
      </tr>`).join('');
    state.activityLoaded = true;
  } catch (e) {
    setGlobalMessage(e.message || '활동 기록을 불러오지 못했습니다.');
  }
}

function reviewLabel(value) {
  const map = {
    SNORE: ['코골이', 'label-snore'],
    NOT_SNORE: ['아님', 'label-not'],
    UNCERTAIN: ['애매', 'label-uncertain'],
    UNREVIEWED: ['미확인', 'label-unreviewed'],
  };
  return map[value] || [value || '미확인', 'label-unreviewed'];
}

function eventRows(metrics) {
  const events = Array.isArray(metrics?.events) ? metrics.events : [];
  if (!events.length) return '<div class="empty-state">저장된 코골이 후보 분석값이 없습니다.</div>';
  return `<div class="event-table"><div class="table-scroll"><table>
    <thead><tr>
      <th>#</th><th>시점</th><th>길이</th><th>평균점수</th><th>최대점수</th><th>dBFS 평균</th><th>dBFS 최대</th>
      <th>저주파</th><th>주기성 평균</th><th>주기성 최대</th><th>Zero-cross</th><th>임계값</th><th>윈도</th><th>판정</th>
    </tr></thead>
    <tbody>${events.map((e, index) => {
      const [label, cls] = reviewLabel(e.review_label);
      return `<tr>
        <td>${index + 1}</td>
        <td>${esc(formatDuration(Number(e.start_offset_ms || 0) / 1000))}</td>
        <td>${esc(formatDuration(Number(e.duration_ms || 0) / 1000))}</td>
        <td>${esc(num(e.score_avg, 1))}</td>
        <td>${esc(num(e.score_max, 1))}</td>
        <td>${esc(num(e.dbfs_avg, 1))}</td>
        <td>${esc(num(e.dbfs_max, 1))}</td>
        <td>${esc(num(e.low_band_ratio_avg, 3))}</td>
        <td>${esc(num(e.periodicity_avg, 3))}</td>
        <td>${esc(num(e.periodicity_max, 3))}</td>
        <td>${esc(num(e.zero_cross_rate_avg, 4))}</td>
        <td>${esc(num(e.threshold_avg, 1))}</td>
        <td>${esc(e.candidate_windows ?? '-')}</td>
        <td class="${cls}">${esc(label)}</td>
      </tr>`;
    }).join('')}</tbody>
  </table></div></div>`;
}

function renderSleepRecord(r, index) {
  const m = r.metrics || {};
  const candidateCount = Number(m.candidate_count || (Array.isArray(m.events) ? m.events.length : 0));
  return `<details class="sleep-record">
    <summary>
      <div class="sleep-title-row">
        <strong>${esc(formatDate(r.started_at))} 수면</strong>
        <span class="muted tiny">업로드 ${esc(formatDate(r.uploaded_at))}</span>
      </div>
      <div class="sleep-stats">
        <span class="stat-chip">수면 ${esc(formatDuration(r.duration_seconds))}</span>
        <span class="stat-chip">코골이 ${esc(formatDuration(r.snore_seconds))}</span>
        <span class="stat-chip">확정 ${esc(int(m.confirmed_count ?? r.snore_events))}</span>
        <span class="stat-chip">후보 ${esc(int(candidateCount))}</span>
        <span class="stat-chip">제외 ${esc(int(m.rejected_count))}</span>
        <span class="stat-chip">애매 ${esc(int(m.uncertain_count))}</span>
      </div>
    </summary>
    <div class="sleep-detail">
      ${eventRows(m)}
    </div>
  </details>`;
}

async function loadSleep(force = false) {
  if (state.sleepLoaded && !force) return;
  setGlobalMessage('');
  try {
    const data = await rpc('admin_sleep_records', { p_limit: 100, p_offset: 0 });
    const s = data?.summary || {};
    $('sleepSummary').innerHTML = [
      summaryCard('수면 기록', `${int(s.record_count)}건`),
      summaryCard('코골이 후보', int(s.candidate_count)),
      summaryCard('확정', int(s.confirmed_count)),
      summaryCard('제외', int(s.rejected_count)),
      summaryCard('애매', int(s.uncertain_count)),
    ].join('');

    const records = Array.isArray(data?.records) ? data.records : [];
    $('sleepEmpty').classList.toggle('hidden', records.length !== 0);
    $('sleepRecords').innerHTML = records.map(renderSleepRecord).join('');
    state.sleepLoaded = true;
  } catch (e) {
    setGlobalMessage(e.message || '수면 분석 기록을 불러오지 못했습니다.');
  }
}

async function refreshCurrentTab() {
  const active = document.querySelector('.tab.active')?.dataset.tab || 'dashboard';
  if (active === 'dashboard') await loadDashboard();
  if (active === 'activity') await loadActivity(true);
  if (active === 'sleep') await loadSleep(true);
}

function selectTab(name) {
  document.querySelectorAll('.tab').forEach((tab) => tab.classList.toggle('active', tab.dataset.tab === name));
  document.querySelectorAll('.panel').forEach((panel) => panel.classList.remove('active-panel'));
  $(`${name}Panel`).classList.add('active-panel');
  if (name === 'activity') loadActivity();
  if (name === 'sleep') loadSleep();
  if (name === 'dashboard') loadDashboard();
}

loginForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  const email = $('emailInput').value.trim();
  const password = $('passwordInput').value;
  loginButton.disabled = true;
  loginButton.textContent = '확인 중…';
  loginMessage.textContent = '';
  try {
    await signIn(email, password);
    showApp();
    await loadDashboard();
  } catch (e) {
    clearSession();
    loginMessage.textContent = e.message || '로그인에 실패했습니다.';
  } finally {
    loginButton.disabled = false;
    loginButton.textContent = '로그인';
  }
});

$('logoutButton').addEventListener('click', logout);
$('refreshButton').addEventListener('click', refreshCurrentTab);
$('activityReload').addEventListener('click', () => loadActivity(true));
$('sleepReload').addEventListener('click', () => loadSleep(true));
document.querySelectorAll('.tab').forEach((tab) => tab.addEventListener('click', () => selectTab(tab.dataset.tab)));

(async function boot() {
  const stored = readStoredSession();
  if (!stored) return showLogin('');
  state.session = stored;
  try {
    await ensureFreshSession();
    if (!isAdminToken(state.session.accessToken)) throw new Error('관리자 권한이 없는 계정입니다.');
    showApp();
    await loadDashboard();
  } catch (e) {
    clearSession();
    showLogin(e.message || '다시 로그인해 주세요.');
  }
})();
