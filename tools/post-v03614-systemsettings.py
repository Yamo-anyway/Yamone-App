#!/usr/bin/env python3
from pathlib import Path
p=Path(__file__).resolve().parent.parent/'app/src/main/java/com/yamo/snorelab/SystemSettingsBridge.java'
s=p.read_text(encoding='utf-8')
old='''        if (enabled) SnowAutoDetectManager.sync(activity);\n        else SnowAutoDetectManager.remove(activity);\n        return enabled;'''
new='''        if (!enabled) DevCaptureService.disable(activity);\n        if (enabled) SnowAutoDetectManager.sync(activity);\n        else SnowAutoDetectManager.remove(activity);\n        return enabled;'''
if old not in s and new not in s: raise SystemExit('post v0.36.14 SystemSettingsBridge target missing')
p.write_text(s.replace(old,new,1),encoding='utf-8')
