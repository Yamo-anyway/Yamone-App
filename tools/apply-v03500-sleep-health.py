#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
SERVICE=ROOT/'app/src/main/java/com/yamo/snorelab/SleepRecorderService.java'
BRIDGE=ROOT/'app/src/main/java/com/yamo/snorelab/SleepBridge.java'
UI=ROOT/'app/src/main/assets/yamone-v23/v02602-sleep.js'

def replace_once(path,old,new,label):
    s=path.read_text(encoding='utf-8')
    if new in s:return
    if old not in s:raise SystemExit(f'v0.35.00 target not found: {label} ({path})')
    path.write_text(s.replace(old,new,1),encoding='utf-8')

replace_once(SERVICE,
'''    public static final String KEY_START_MS = "active_start_ms";
    public static final String CHANNEL_ID = "sleep_measurement";''',
'''    public static final String KEY_START_MS = "active_start_ms";
    public static final String KEY_HEARTBEAT_MS = "sleep_heartbeat_ms";
    public static final String KEY_CAPTURED_SECONDS = "sleep_captured_seconds";
    public static final String CHANNEL_ID = "sleep_measurement";''','sleep health keys')

replace_once(SERVICE,
'''            prefs.edit().putBoolean(KEY_RECORDING, true).putString(KEY_SESSION_ID, sessionDir.getName())
                    .putLong(KEY_START_MS, startMs)
                    .remove("sleep_current_dbfs")''',
'''            prefs.edit().putBoolean(KEY_RECORDING, true).putString(KEY_SESSION_ID, sessionDir.getName())
                    .putLong(KEY_START_MS, startMs)
                    .putLong(KEY_HEARTBEAT_MS, startMs)
                    .putLong(KEY_CAPTURED_SECONDS, 0L)
                    .remove("sleep_current_dbfs")''','initialize health heartbeat')

replace_once(SERVICE,
'''                        prefs.edit()
                                .putFloat("sleep_current_dbfs", (float) result.dbfs)
                                .putFloat("sleep_current_score", (float) result.score)
                                .putBoolean("sleep_current_candidate", candidate)
                                .apply();''',
'''                        long capturedSeconds = processedWindowSamples / SAMPLE_RATE + 1L;
                        prefs.edit()
                                .putFloat("sleep_current_dbfs", (float) result.dbfs)
                                .putFloat("sleep_current_score", (float) result.score)
                                .putBoolean("sleep_current_candidate", candidate)
                                .putLong(KEY_HEARTBEAT_MS, System.currentTimeMillis())
                                .putLong(KEY_CAPTURED_SECONDS, capturedSeconds)
                                .apply();''','update recording heartbeat')

replace_once(SERVICE,
'''            prefs.edit().putBoolean(KEY_RECORDING, false).remove(KEY_SESSION_ID).remove(KEY_START_MS)
                    .remove("sleep_current_dbfs").remove("sleep_current_score")''',
'''            prefs.edit().putBoolean(KEY_RECORDING, false).remove(KEY_SESSION_ID).remove(KEY_START_MS)
                    .remove(KEY_HEARTBEAT_MS).remove(KEY_CAPTURED_SECONDS)
                    .remove("sleep_current_dbfs").remove("sleep_current_score")''','cleanup health heartbeat')

replace_once(BRIDGE,
'''            out.put("currentCandidate", prefs.getBoolean(KEY_CURRENT_CANDIDATE, false));
            File dir = sessionDir(id);''',
'''            out.put("currentCandidate", prefs.getBoolean(KEY_CURRENT_CANDIDATE, false));
            long heartbeat = prefs.getLong(SleepRecorderService.KEY_HEARTBEAT_MS, 0L);
            out.put("heartbeatMs", heartbeat);
            out.put("heartbeatAgeMs", heartbeat <= 0L ? -1L : Math.max(0L, System.currentTimeMillis() - heartbeat));
            out.put("capturedSeconds", Math.max(0L, prefs.getLong(SleepRecorderService.KEY_CAPTURED_SECONDS, 0L)));
            out.put("captureHealthy", !recording || (heartbeat > 0L && System.currentTimeMillis() - heartbeat <= 15_000L));
            File dir = sessionDir(id);''','expose sleep health')

replace_once(UI,
'''  function pctText(part,total){return total>0?`${Math.min(100,Math.max(0,part/total*100)).toFixed(1)}%`:'0.0%';}

  function graphMarkup(points){''',
'''  function pctText(part,total){return total>0?`${Math.min(100,Math.max(0,part/total*100)).toFixed(1)}%`:'0.0%';}
  function healthText(s){return s&&s.captureHealthy!==false?'측정 정상':'측정 확인 필요';}

  function graphMarkup(points){''','sleep health label helper')

replace_once(UI,
'''        <div class="sleep-live-head"><div class="sleep-live-left"><img src="${A}activity-sleep.png"><div><h2>수면 기록 중</h2></div></div><span class="sleep-live-pill">측정 중</span></div>''',
'''        <div class="sleep-live-head"><div class="sleep-live-left"><img src="${A}activity-sleep.png"><div><h2>수면 기록 중</h2></div></div><span id="v3500SleepHealth" class="sleep-live-pill">${healthText(s)}</span></div>''','sleep live health pill')

replace_once(UI,
'''    set('v02602Candidates',`${Math.max(0,num(s.candidateCount))}개`);
    const btn=document.getElementById('sleepCandidate');''',
'''    set('v02602Candidates',`${Math.max(0,num(s.candidateCount))}개`);
    set('v3500SleepHealth',healthText(s));
    const btn=document.getElementById('sleepCandidate');''','update sleep health pill')

print('Applied v0.35.00 sleep recording heartbeat diagnostics.')
