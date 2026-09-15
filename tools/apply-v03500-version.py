#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent;TARGET=ROOT/'app/src/main/assets/yamone-v23';src=ROOT/'design-preview/v03500-version.js';(TARGET/src.name).write_bytes(src.read_bytes());index=TARGET/'index.html';s=index.read_text(encoding='utf-8')
for old in ['v03400-version.js','v03300-version.js','v03200-version.js','v03100-version.js','v03000-version.js','v02900-version.js','v02802-version.js','v02801-version.js','v02701-version.js','v02602-version.js','v02516-version.js']:s=s.replace(f'  <script src="{old}"></script>\n','').replace(f'<script src="{old}"></script>\n','')
if 'src="v03500-version.js"' not in s:s=s.replace('</body>','  <script src="v03500-version.js"></script>\n</body>',1)
index.write_text(s,encoding='utf-8');print('Applied v0.35.00 version marker.')
