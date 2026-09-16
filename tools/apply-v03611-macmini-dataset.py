#!/usr/bin/env python3
from pathlib import Path

R = Path(__file__).resolve().parent.parent
BUILD = R / 'app/build.gradle'
JS = R / 'app/src/main/assets/yamone-v23/v03610-dataset.js'

s = BUILD.read_text(encoding='utf-8')
s = s.replace("versionCode 104", "versionCode 105").replace("versionName '0.36.10'", "versionName '0.36.11'")
BUILD.write_text(s, encoding='utf-8')

s = JS.read_text(encoding='utf-8')
s = s.replace("const V='0.36.10'", "const V='0.36.11'")
JS.write_text(s, encoding='utf-8')

uploader = R / 'app/src/main/java/com/yamo/snorelab/DevDatasetUploader.java'
u = uploader.read_text(encoding='utf-8')
if 'https://yamone-data.anynow.net' not in u:
    raise SystemExit('Mac mini dataset endpoint missing')
if 'supabase.co' in u or 'SupabaseAnonymousRpcClient' in u:
    raise SystemExit('Supabase dependency remains in developer dataset uploader')
print('Applied v0.36.11 Mac mini developer dataset transport.')
