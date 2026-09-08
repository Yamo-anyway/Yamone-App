'use strict';

(() => {
  const cache = { activity: [], sleep: [] };

  const originalRpc = rpc;
  rpc = async function(name, params = {}, retry = true) {
    const data = await originalRpc(name, params, retry);
    if (name === 'admin_activity_records') {
      cache.activity = Array.isArray(data?.records) ? data.records : [];
    } else if (name === 'admin_sleep_records') {
      cache.sleep = Array.isArray(data?.records) ? data.records : [];
    }
    return data;
  };

  async function removeStorageObjects(objects) {
    const valid = (Array.isArray(objects) ? objects : []).filter((item) =>
      item && typeof item.bucket === 'string' && item.bucket && typeof item.path === 'string' && item.path
    );
    if (!valid.length) return;

    const grouped = new Map();
    for (const item of valid) {
      if (!grouped.has(item.bucket)) grouped.set(item.bucket, []);
      grouped.get(item.bucket).push(item.path);
    }

    await ensureFreshSession();

    for (const [bucket, paths] of grouped.entries()) {
      const unique = [...new Set(paths)];
      for (let i = 0; i < unique.length; i += 1000) {
        const prefixes = unique.slice(i, i + 1000);
        const response = await fetch(`${SUPABASE_URL}/storage/v1/object/${encodeURIComponent(bucket)}`, {
          method: 'DELETE',
          headers: {
            apikey: PUBLISHABLE_KEY,
            Authorization: `Bearer ${state.session.accessToken}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ prefixes }),
        });

        if (!response.ok) {
          let message = 'Storage 파일 삭제에 실패했습니다.';
          try {
            const body = await response.json();
            message = body?.message || body?.error || message;
          } catch (_) {}
          throw new Error(message);
        }
      }
    }
  }

  function deleteConfirmText(kind) {
    const label = kind === 'activity' ? '활동 기록' : '수면 분석 기록';
    return `${label}을 서버에서 삭제할까요?\n\n` +
      '• 관련 서버 기록과 Storage 파일은 영구 삭제됩니다.\n' +
      '• 삭제 후 복구할 수 없습니다.\n' +
      '• 이미 업로드했다는 1회 업로드 이력은 계속 유지됩니다.\n' +
      '• 따라서 같은 기록은 삭제 후에도 다시 업로드할 수 없습니다.';
  }

  async function deleteRecord(kind, recordId, button) {
    if (!recordId || !window.confirm(deleteConfirmText(kind))) return;

    const originalText = button.textContent;
    button.disabled = true;
    button.textContent = '삭제 중…';
    setGlobalMessage('');

    let requestId = null;
    try {
      const plan = await rpc('admin_record_delete_prepare', {
        p_record_kind: kind,
        p_record_id: recordId,
      });
      requestId = plan?.request_id || null;
      if (!requestId) throw new Error('삭제 요청을 만들 수 없습니다.');

      await removeStorageObjects(plan?.storage_objects);
      await rpc('admin_record_delete_finalize', { p_request_id: requestId });

      if (kind === 'activity') {
        state.activityLoaded = false;
        await loadActivity(true);
      } else {
        state.sleepLoaded = false;
        await loadSleep(true);
      }
      await loadDashboard();

      const label = kind === 'activity' ? '활동 기록' : '수면 분석 기록';
      setGlobalMessage(`${label}을 삭제했습니다. 1회 업로드 이력은 유지됩니다.`, 'success');
    } catch (error) {
      if (requestId) {
        try {
          await rpc('admin_record_delete_fail', {
            p_request_id: requestId,
            p_error_message: error?.message || 'delete_failed',
          });
        } catch (_) {}
      }
      setGlobalMessage(error?.message || '기록을 삭제하지 못했습니다. 서버 기록은 유지됩니다.');
      button.disabled = false;
      button.textContent = originalText;
    }
  }

  function bindActivityDeleteButtons() {
    const headerRow = document.querySelector('#activityPanel thead tr');
    if (headerRow && !headerRow.querySelector('.delete-column-head')) {
      const th = document.createElement('th');
      th.className = 'delete-column-head';
      th.textContent = '관리';
      headerRow.appendChild(th);
    }

    const rows = [...document.querySelectorAll('#activityTableBody tr')];
    rows.forEach((row, index) => {
      if (row.querySelector('.record-delete-button')) return;
      const record = cache.activity[index];
      if (!record?.id) return;
      const td = document.createElement('td');
      td.className = 'delete-cell';
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'record-delete-button';
      button.textContent = '삭제';
      button.addEventListener('click', () => deleteRecord('activity', record.id, button));
      td.appendChild(button);
      row.appendChild(td);
    });
  }

  function bindSleepDeleteButtons() {
    const records = [...document.querySelectorAll('#sleepRecords .sleep-record')];
    records.forEach((details, index) => {
      if (details.querySelector('.record-delete-button')) return;
      const record = cache.sleep[index];
      if (!record?.id) return;
      const titleRow = details.querySelector('.sleep-title-row');
      if (!titleRow) return;
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'record-delete-button sleep-delete-button';
      button.textContent = '삭제';
      button.addEventListener('click', (event) => {
        event.preventDefault();
        event.stopPropagation();
        deleteRecord('sleep', record.id, button);
      });
      titleRow.appendChild(button);
    });
  }

  const originalLoadActivity = loadActivity;
  loadActivity = async function(force = false) {
    await originalLoadActivity(force);
    bindActivityDeleteButtons();
  };

  const originalLoadSleep = loadSleep;
  loadSleep = async function(force = false) {
    await originalLoadSleep(force);
    bindSleepDeleteButtons();
  };

  bindActivityDeleteButtons();
  bindSleepDeleteButtons();
})();
