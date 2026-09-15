#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
STORE=ROOT/'app/src/main/java/com/yamo/snorelab/SnowOfflineMapStore.java'
SNOW_JS=ROOT/'app/src/main/assets/yamone-v23/v02801-snow.js'


def replace_once(path,old,new,label):
    s=path.read_text(encoding='utf-8')
    if new in s:return
    if old not in s:raise SystemExit(f'v0.34.00 target not found: {label} ({path})')
    path.write_text(s.replace(old,new,1),encoding='utf-8')

replace_once(STORE,
'''                JSONObject clean = new JSONObject(m.toString());
                clean.put("installed", true);
                clean.put("bytes", folderSize(dir));
                out.put(clean);''',
'''                JSONObject clean = new JSONObject(m.toString());
                clean.put("installed", true);
                clean.put("bytes", folderSize(dir));
                JSONObject validation = SnowMapPackageValidator.validate(context, clean.optString("resortKey", dir.getName()));
                clean.put("packageValid", validation.optBoolean("valid", false));
                clean.put("licenseReady", validation.optBoolean("licenseReady", false));
                clean.put("productionReady", validation.optBoolean("productionReady", false));
                clean.put("validationIssues", validation.optJSONArray("issues"));
                out.put(clean);''','installed validation fields')

replace_once(STORE,
'''            JSONObject m = installed.optJSONObject(i);
            if (m == null) continue;
            double clat = m.optDouble("centerLat", Double.NaN);''',
'''            JSONObject m = installed.optJSONObject(i);
            if (m == null || !m.optBoolean("packageValid", false)) continue;
            double clat = m.optDouble("centerLat", Double.NaN);''','skip invalid resort packages')

replace_once(STORE,
'''            JSONObject manifest = readManifest(context, resortKey);
            JSONObject map = readMapData(context, resortKey);
            if (manifest.length() == 0 || map.length() == 0) return out;''',
'''            JSONObject manifest = readManifest(context, resortKey);
            JSONObject map = readMapData(context, resortKey);
            JSONObject validation = SnowMapPackageValidator.validate(context, resortKey);
            if (manifest.length() == 0 || map.length() == 0 || !validation.optBoolean("valid", false)) return out;''','classifier package validation')

replace_once(SNOW_JS,
'''    const mapRows=maps.length?maps.map(m=>`<div class="card v02801-map-row"><img src="${AS}route.png"><div><b>${esc(m.resortName||m.resortKey)}</b><small>지도 v${n(m.mapVersion,1)} · ${m.developerTest?'개발 테스트 패키지':'OSM 오프라인 패키지'} · ${bytesText(m.bytes)}</small></div><strong>설치됨</strong></div>`).join(''):`<div class="setting-info">설치된 Snow 오프라인 지도가 없습니다.</div>`;''',
'''    const mapRows=maps.length?maps.map(m=>`<div class="card v02801-map-row"><img src="${AS}route.png"><div><b>${esc(m.resortName||m.resortKey)}</b><small>지도 v${n(m.mapVersion,1)} · ${m.developerTest?'개발 테스트 패키지':'OSM 오프라인 패키지'} · ${bytesText(m.bytes)}</small></div><strong>${m.packageValid?(m.developerTest?'테스트 정상':(m.productionReady?'검증됨':'라이선스 확인')):'사용 불가'}</strong></div>`).join(''):`<div class="setting-info">설치된 Snow 오프라인 지도가 없습니다.</div>`;''','Snow map validation UI')

replace_once(SNOW_JS,
'''      <div class="setting-info">실제 지도는 공개 OSM 타일을 저장하지 않고 OSM 원본에서 스키장 영역만 추출한 전용 오프라인 패키지를 사용하도록 구성했습니다. 실제 스키장 패키지는 현장 테스트 단계에서 추가합니다.</div>`;''',
'''      <div class="setting-info">실제 지도는 공개 OSM 타일을 저장하지 않고 OSM 원본에서 스키장 영역만 추출한 전용 오프라인 패키지를 사용합니다. 실사용 패키지는 OSM 출처와 ODbL 표기, 스키장 경계·슬로프·리프트 구조가 모두 검증되어야 자동감지와 슬로프 분류에 사용됩니다.</div>`;''','Snow validation explanation')

print('Applied v0.34.00 Snow package validation and OSM/ODbL gate.')
