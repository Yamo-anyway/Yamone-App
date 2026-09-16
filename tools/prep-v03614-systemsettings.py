#!/usr/bin/env python3
from pathlib import Path
p=Path(__file__).resolve().parent.parent/'app/src/main/java/com/yamo/snorelab/SystemSettingsBridge.java'
s=p.read_text(encoding='utf-8')
old='''        activity.getSharedPreferences(INTERNAL_PREFS, Context.MODE_PRIVATE)\n                .edit().putBoolean(KEY_DEVELOPER_MODE, enabled).apply();\n        if (!enabled) DevCaptureService.disable(activity);\n        return enabled;'''
new='''        activity.getSharedPreferences(INTERNAL_PREFS, Context.MODE_PRIVATE)\n                .edit().putBoolean(KEY_DEVELOPER_MODE, enabled).apply();\n        return enabled;'''
if old not in s and new not in s: raise SystemExit('prep v0.36.14 SystemSettingsBridge target missing')
p.write_text(s.replace(old,new,1),encoding='utf-8')
