/* v0.25.01+: visited-screen history, not a hard-coded return-to-home.
 * This is an in-memory UI history. Recording/service state is never rewound.
 */
(function () {
  'use strict';
  const stack = [];
  let restoring = false;
  let renderDepth = 0;
  let exitFocus = null;
  const copy = value => value == null ? value : JSON.parse(JSON.stringify(value));

  function readRoute() {
    const route = {tab, view};
    if (view === 'setting') route.settingPage = settingPage;
    if (view === 'record-detail') {
      route.recordDetailId = recordDetailId;
      route.snowRunDetailOpen = !!snowRunDetailOpen;
      if (snowRunDetailOpen) route.snowSelectedDescentId = snowSelectedDescentId;
    }
    if (view === 'main' && tab === 'alarm') {
      route.alarmView = alarmView;
      if (alarmView === 'edit') route.editingAlarmId = editingAlarmId;
      if (alarmView === 'ring') route.ringingAlarmId = ringingAlarmId;
    }
    if (view === 'move') route.moveState = moveState;
    if (view === 'location') route.locationState = locationState;
    return route;
  }
  const key = route => JSON.stringify(route);

  function entry() {
    return {
      route: readRoute(), scrollTop: screen.scrollTop,
      ui: {
        openMonth, recordFilter, recordSelectedSegment, snowSelectedDescentId,
        alarmDraft: copy(alarmDraft), alarmPreview: copy(window.__alarmPreview),
        ringtonePickerOpen
      }
    };
  }
  function rememberCurrent() {
    if (restoring || !stack.length) return;
    if (key(stack[stack.length - 1].route) === key(readRoute())) {
      stack[stack.length - 1] = entry();
    }
  }
  function trackRoute() {
    if (restoring) return;
    const next = entry();
    if (!stack.length || key(next.route) !== key(stack[stack.length - 1].route)) {
      stack.push(next);
    }
  }
  // Catch programmatic route transitions too; nested render calls add one entry.
  for (const name of ['render', 'renderAlarm', 'renderAlarmEditor',
    'renderAlarmRinging', 'renderRecords', 'renderRecordDetail', 'renderSettingDetail']) {
    const original = window[name];
    if (typeof original !== 'function') continue;
    window[name] = function (...args) {
      renderDepth++;
      try { return original.apply(this, args); }
      finally { if (--renderDepth === 0) trackRoute(); }
    };
  }

  // A wheel's delayed scroll callback can fire after Back removes the editor.
  // Ignore detached controls rather than mutating a cleared/new alarm draft.
  const originalWheelUpdate = window.updateWheelSelected;
  window.updateWheelSelected = function (element, onChange, commit = true) {
    if (!element || !element.isConnected) return;
    return originalWheelUpdate(element, onChange, commit);
  };

  function validRoute(route) {
    if (route.view === 'record-detail') {
      return !!recordSamples[route.recordDetailId] && !deletedRecordIds.includes(route.recordDetailId);
    }
    if (route.editingAlarmId != null) return alarms.some(a => a.id === route.editingAlarmId);
    return true;
  }
  function restoreEntry(previous) {
    restoring = true;
    try {
      const r = previous.route, u = previous.ui;
      // These timers only play the mockup audio; do not stop a recording/service.
      if (typeof stopSleepRecordPlaybackTimer === 'function') stopSleepRecordPlaybackTimer();
      if (typeof stopSleepCandidateAudioTimer === 'function') stopSleepCandidateAudioTimer();
      tab = r.tab;
      view = r.view;
      settingPage = r.settingPage || null;
      recordDetailId = r.recordDetailId || null;
      snowRunDetailOpen = !!r.snowRunDetailOpen;
      snowSelectedDescentId = r.snowSelectedDescentId || u.snowSelectedDescentId;
      recordSelectedSegment = u.recordSelectedSegment;
      recordFilter = u.recordFilter;
      openMonth = u.openMonth;
      if (r.view === 'main' && r.tab === 'alarm') {
        alarmView = r.alarmView || 'list';
        editingAlarmId = r.editingAlarmId == null ? null : r.editingAlarmId;
        ringingAlarmId = r.ringingAlarmId == null ? null : r.ringingAlarmId;
        alarmDraft = alarmView === 'edit' ? copy(u.alarmDraft) : null;
        window.__alarmPreview = alarmView === 'ring' ? copy(u.alarmPreview) : null;
        ringtonePickerOpen = alarmView === 'edit' && !!u.ringtonePickerOpen;
      }
      if (r.view === 'move') moveState = r.moveState || 'ready';
      if (r.view === 'location') {
        // Back navigation must never restart a finished sharing session.
        locationState = ['active', 'extend'].includes(r.locationState) && !locationSharingActive
          ? 'ended' : r.locationState;
      }
      syncTabs();
      render();
      screen.scrollTop = previous.scrollTop;
      const restoreKey = key(readRoute());
      requestAnimationFrame(() => {
        if (key(readRoute()) === restoreKey) screen.scrollTop = previous.scrollTop;
      });
      stack[stack.length - 1] = entry();
    } finally { restoring = false; }
  }

  function closeExit() {
    closeMoveOverlay();
    delete moveOverlay.dataset.v25Exit;
    if (exitFocus && exitFocus.isConnected) exitFocus.focus({preventScroll: true});
    exitFocus = null;
  }
  function showExit() {
    if (moveOverlay.dataset.v25Exit === '1') return true;
    exitFocus = document.activeElement;
    moveOverlay.dataset.v25Exit = '1';
    moveOverlay.classList.add('show');
    moveOverlay.innerHTML = '<div class="move-sheet v24-exit-sheet" role="dialog" aria-modal="true" aria-labelledby="v25ExitTitle">'
      + '<div class="move-sheet-handle"></div><h3 id="v25ExitTitle">야모네를 나갈까요?</h3>'
      + '<p>나가기를 누르면 앱을 종료합니다.</p><div class="move-sheet-actions">'
      + '<button id="v25ExitCancel" class="move-sheet-cancel">취소</button>'
      + '<button id="v25ExitConfirm" class="move-sheet-stop">나가기</button></div></div>';
    document.getElementById('v25ExitCancel').onclick = closeExit;
    document.getElementById('v25ExitConfirm').onclick = () => {
      // Only explicit confirmation can ask Android to finish the Activity.
      if (window.YamoneNative && typeof YamoneNative.finishApp === 'function') {
        YamoneNative.finishApp();
      } else {
        say('앱에서는 나가기를 누르면 종료됩니다.');
      }
    };
    moveOverlay.onclick = e => { if (e.target === moveOverlay) closeExit(); };
    document.getElementById('v25ExitCancel').focus({preventScroll: true});
    return true;
  }

  function back() {
    // A popup or keyboard edit is dismissed before popping the screen history.
    if (moveOverlay.classList.contains('show')) {
      if (moveOverlay.dataset.v25Exit === '1') closeExit();
      else closeMoveOverlay();
      return true;
    }
    const sheet = document.getElementById('alarmSheetBackdrop');
    if (sheet && sheet.classList.contains('show')) {
      sheet.classList.remove('show');
      return true;
    }
    const input = document.activeElement;
    if (input && input.matches('.v24-inline-time-input')) {
      input.blur();
      return true;
    }
    if (view === 'main' && tab === 'alarm' && alarmView === 'edit' && ringtonePickerOpen) {
      ringtonePickerOpen = false;
      renderAlarmEditor();
      return true;
    }
    rememberCurrent();
    while (stack.length > 1) {
      stack.pop();
      const previous = stack[stack.length - 1];
      if (!validRoute(previous.route)) continue;
      restoreEntry(previous);
      return true;
    }
    return showExit();
  }

  // Capture outgoing scroll/selection before the original click handler mutates it.
  document.addEventListener('click', e => {
    rememberCurrent();
    const toolbarBack = e.target.closest(
      '.move-back-asset-btn,.setting-back,#recordDetailBack,#snowDetailTopBack');
    if (toolbarBack && screen.contains(toolbarBack)) {
      e.preventDefault();
      e.stopImmediatePropagation();
      back();
    }
  }, true);
  screen.addEventListener('scroll', rememberCurrent, {passive: true});
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape' && !e.defaultPrevented) { e.preventDefault(); back(); }
    if (e.key === 'Tab' && moveOverlay.dataset.v25Exit === '1') {
      const cancel = document.getElementById('v25ExitCancel');
      const confirm = document.getElementById('v25ExitConfirm');
      if (e.shiftKey && document.activeElement === cancel) { e.preventDefault(); confirm.focus(); }
      else if (!e.shiftKey && document.activeElement === confirm) { e.preventDefault(); cancel.focus(); }
    }
  });
  trackRoute();
  // Replace both legacy fixed-home Back and the v24 fallback exit wrapper.
  window.yamoneAndroidBack = back;
  window.YamoneNavigation = Object.freeze({
    back,
    snapshot: () => stack.map(item => copy(item.route)),
    get canGoBack() { return stack.length > 1; }
  });
})();
