#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RING = ROOT / 'app/src/main/java/com/yamo/snorelab/AlarmRingActivity.java'


def replace_once(path: Path, old: str, new: str, label: str):
    s = path.read_text(encoding='utf-8')
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'v0.33.00 patch target not found: {label} ({path})')
    path.write_text(s.replace(old, new, 1), encoding='utf-8')

# The renewed app uses mint as the base theme. Alarm ringing must not switch to the legacy pink surface.
replace_once(RING,
'''        SharedPreferences prefs = getSharedPreferences(SleepRecorderService.PREFS, MODE_PRIVATE);
        boolean pink = "pink".equals(prefs.getString("yamone_theme", "pink"));
        BG = pink ? 0xFFFFF7FA : 0xFFF7FFFB;
        CARD = 0xFFFFFFFF;
        CARD2 = pink ? 0xFFFFEEF3 : 0xFFF0FAF6;
        TEXT = pink ? 0xFF4B2633 : 0xFF153633;
        MUTED = pink ? 0xFF9A7180 : 0xFF718984;
        PRIMARY = pink ? 0xFFFF769F : 0xFF56D1B3;
        PRIMARY2 = pink ? 0xFFE94778 : 0xFF159A7A;''',
'''        // v0.33: alarm ringing uses the same mint base as the renewed Yamone UI.
        BG = 0xFFFBFDFC;
        CARD = 0xFFFFFFFF;
        CARD2 = 0xFFF0FAF6;
        TEXT = 0xFF153633;
        MUTED = 0xFF718984;
        PRIMARY = 0xFF56D1B3;
        PRIMARY2 = 0xFF159A7A;''',
'force mint alarm palette')

replace_once(RING,
'''    private int pinkTextColor() {
        return "pink".equals(getSharedPreferences(SleepRecorderService.PREFS, MODE_PRIVATE).getString("yamone_theme", "pink"))
                ? 0xFF4B2633 : 0xFF08352A;
    }''',
'''    private int pinkTextColor() {
        // Kept for older helper calls, but v0.33 has one mint ringing palette.
        return 0xFF08352A;
    }''',
'legacy text helper mint')

print('Applied v0.33.00 mint-only native alarm ringing screen.')
