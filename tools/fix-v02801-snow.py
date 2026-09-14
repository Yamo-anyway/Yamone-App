#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
p = ROOT / 'app/src/main/java/com/yamo/snorelab/SnowOfflineMapStore.java'
s = p.read_text(encoding='utf-8')
old = '    private static JSONArray point(double lat, double lon) {\n'
new = '    private static JSONArray point(double lat, double lon) throws Exception {\n'
if old not in s:
    raise SystemExit('v0.28.01 compile fix target not found')
p.write_text(s.replace(old, new, 1), encoding='utf-8')
print('Applied v0.28.01 Snow compile fix.')
