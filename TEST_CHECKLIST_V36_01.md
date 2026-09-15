# Yamone v0.36.01 field test checklist

Scope: walking / running / cycling auto-detection only. Location-sharing changes remain excluded.

## 1. Auto start
- Set start mode to `자동 시작`.
- Start walking with the app visible: recording should start without a `시작할까요?` prompt.
- Repeat with the app backgrounded and background location set to `항상 허용`: recording should start without the ask prompt.
- Remove background location and repeat: the app should show a permission/settings notice, not convert auto mode into an ask-start prompt.

## 2. Default detection responsiveness
- Set sensitivity to `기본`.
- Test walking, running, and cycling from a stopped state.
- Android Activity Transition ENTER should be handled immediately; there is no extra 60-second candidate alarm in default mode.
- `정확하게` intentionally keeps an extra persistence delay (run 15s, walk/bike 30s).

## 3. Auto-detect notification controls
- Settings > 자동감지 > 자동감지 알림.
- Test all combinations:
  - sound ON / vibration ON
  - sound ON / vibration OFF
  - sound OFF / vibration ON
  - sound OFF / vibration OFF
- Verify start/end/permission auto-detect notifications follow the selected combination.

## 4. Regression
- `확인 후 시작` must still show start/cancel actions.
- Bicycle vehicle suppression must still block car false starts.
- Auto-end behavior must remain unchanged.
