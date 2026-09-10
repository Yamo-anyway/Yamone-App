from pathlib import Path

p = Path('app/src/main/java/com/yamo/snorelab/EnhancedExerciseActivity.java')
s = p.read_text()

def rep(old, new, label):
    global s
    if old not in s:
        raise SystemExit(f'missing {label}: {old[:100]}')
    s = s.replace(old, new, 1)

rep('''    private static final int CARD = 0xFF16243B;
    private static final int CARD2 = 0xFF111C31;
    private static final int TEXT = 0xFFF5F7FF;
    private static final int MUTED = 0xFF9DA9BF;
    private static final int PRIMARY = 0xFF6D72FF;
    private static final int PRIMARY2 = 0xFF8B8FFF;''',
'''    private int CARD = 0xFFFFFFFF;
    private int CARD2 = 0xFFFFEEF3;
    private int TEXT = 0xFF4B2633;
    private int MUTED = 0xFF9A7180;
    private int PRIMARY = 0xFFFF769F;
    private int PRIMARY2 = 0xFFE94778;
    private int BORDER = 0xFFFFD7E3;''', 'weekly palette fields')

rep('''    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        attachWeeklyChartEnhancer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        scheduleWeeklyEnhance();
    }''',
'''    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyPalette();
        attachWeeklyChartEnhancer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPalette();
        scheduleWeeklyEnhance();
    }

    private void applyPalette() {
        boolean pink = "pink".equals(getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "pink"));
        CARD = 0xFFFFFFFF;
        CARD2 = pink ? 0xFFFFEEF3 : 0xFFF0FAF6;
        TEXT = pink ? 0xFF4B2633 : 0xFF153633;
        MUTED = pink ? 0xFF9A7180 : 0xFF718984;
        PRIMARY = pink ? 0xFFFF769F : 0xFF56D1B3;
        PRIMARY2 = pink ? 0xFFE94778 : 0xFF159A7A;
        BORDER = pink ? 0xFFFFD7E3 : 0xFFD7EFE7;
    }''', 'weekly palette lifecycle')

rep('chip.setBackground(round(selected ? PRIMARY : CARD2, 12, selected ? 0 : 1, 0xFF35445F));',
    'chip.setBackground(round(selected ? PRIMARY2 : CARD2, 14, selected ? 0 : 1, BORDER));', 'weekly chip style')
rep('card.setBackground(round(CARD, 18, 0, 0));',
    'card.setBackground(round(CARD, 20, 1, BORDER));', 'weekly card style')

# Selected chips should stay legible in both theme families.
s = s.replace('TextView chip = text(label, 12, selected ? Color.WHITE : MUTED, true);',
              'TextView chip = text(label, 12, selected ? Color.WHITE : TEXT, true);')

# Guard against the old dark palette returning in this component.
for token in ['0xFF16243B', '0xFF111C31', '0xFFF5F7FF', '0xFF6D72FF', '0xFF8B8FFF', '0xFF35445F']:
    if token in s:
        raise SystemExit('old weekly chart palette remains: ' + token)

p.write_text(s)
