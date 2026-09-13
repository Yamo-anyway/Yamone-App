#!/usr/bin/env bash
set -euo pipefail

ZIP_PATH="${1:-design-source/yamone-v23.zip}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET="$ROOT/app/src/main/assets/yamone-v23"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

if [ ! -f "$ZIP_PATH" ]; then
  echo "Yamone base design zip not found: $ZIP_PATH" >&2
  exit 2
fi

unzip -q "$ZIP_PATH" -d "$TMP"
SOURCE_DIR="$(find "$TMP" -type f -name index.html -print -quit | xargs -r dirname)"
if [ -z "$SOURCE_DIR" ] || [ ! -f "$SOURCE_DIR/app.js" ] || [ ! -f "$SOURCE_DIR/styles.css" ]; then
  echo "Invalid design zip: index.html/app.js/styles.css not found" >&2
  exit 3
fi

rm -rf "$TARGET"
mkdir -p "$TARGET/assets"
cp -R "$SOURCE_DIR"/. "$TARGET"/
rm -f "$TARGET/asset-audit.png" "$TARGET/move-assets-audit.png" || true

# Keep approved v24/v25 design patches. v0.25.12 keeps the current movement UI,
# Home active card and stable Summary View, then adds notification deep-link routing.
for file in mobile.css app-mobile.js v02403.css v02501-ui.js v02503-summary.css v02505-summary.css v02505-summary.js v02507-fit.css v02507-move.css v02507-move.js v02507-fit.js v02501-navigation.js v02508-real-move.css v02508-real-move.js v02509-ui.css v02510-ui.css v02510-ui.js v02511-home-active.css v02511-home-active.js v02512-notification.js v02512-version.js; do
  cp "$ROOT/design-preview/$file" "$TARGET/$file"
done
for file in title-activity-v02402.png title-records-v02402.png title-alarm-v02402.png title-settings-v02402.png back-v02402.png summary-view-button-v02503.svg title-summary-walk-v02503.svg title-summary-run-v02503.svg title-summary-bike-v02503.svg; do
  cp "$ROOT/design-preview/assets/$file" "$TARGET/assets/$file"
done

# v0.25.08 used to rebuild the entire Summary View every second. Replace only
# that polling branch so values are updated in place by v02510-ui.js instead.
python3 - "$TARGET/v02508-real-move.js" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text(encoding='utf-8')
old = "}else if(view==='move'&&moveState==='glance'&&s.recording){realGlance();}"
new = "}else if(view==='move'&&moveState==='glance'&&s.recording){if(window.v02510UpdateGlance)window.v02510UpdateGlance(s);}"
if old not in s:
    raise SystemExit('v0.25.10 glance polling patch target not found')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
PY

# The foreground notification used to open the legacy ExerciseActivity and its
# '기록 종료' action stopped the service immediately. Route both taps through the
# renewed WebView host; the stop action now only requests the existing confirm popup.
python3 - "$ROOT/app/src/main/java/com/yamo/snorelab/WalkingRecorderService.java" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text(encoding='utf-8')
old = '''    private Notification buildRecordingNotification(String message) {
        Intent open = new Intent(this, ExerciseActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 5101, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, WalkingRecorderService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 5103, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_RECORDING) : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(paused ? activityLabel() + " 기록 일시정지" : activityLabel() + " 기록 중")
                .setContentText(message)
                .setContentIntent(openPi)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "기록 종료", stopPi).build())
                .build();
    }
'''
new = '''    private Notification buildRecordingNotification(String message) {
        Intent open = movementNotificationIntent(false);
        PendingIntent openPi = PendingIntent.getActivity(this, 5101, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = movementNotificationIntent(true);
        PendingIntent stopPi = PendingIntent.getActivity(this, 5103, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_RECORDING) : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(paused ? activityLabel() + " 기록 일시정지" : activityLabel() + " 기록 중")
                .setContentText(message)
                .setContentIntent(openPi)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "기록 종료", stopPi).build())
                .build();
    }

    private Intent movementNotificationIntent(boolean openStopConfirm) {
        Intent intent = new Intent(this, YamoneDesignPreviewActivity.class);
        intent.putExtra(YamoneDesignPreviewActivity.EXTRA_OPEN_MOVEMENT, true);
        intent.putExtra(YamoneDesignPreviewActivity.EXTRA_OPEN_STOP_CONFIRM, openStopConfirm);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }
'''
if old not in s:
    raise SystemExit('v0.25.12 recording-notification patch target not found')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
PY

python3 - "$TARGET/index.html" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text(encoding='utf-8')
if 'name="viewport"' not in s:
    s = s.replace('<meta charset="utf-8">', '<meta charset="utf-8">\n  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, viewport-fit=cover">', 1)
for css in ['mobile.css', 'v02403.css', 'v02503-summary.css', 'v02505-summary.css', 'v02507-fit.css', 'v02507-move.css', 'v02508-real-move.css', 'v02509-ui.css', 'v02510-ui.css', 'v02511-home-active.css']:
    if f'href="{css}"' not in s:
        s = s.replace('</head>', f'  <link rel="stylesheet" href="{css}">\n</head>', 1)
for js in ['app-mobile.js', 'v02501-ui.js', 'v02505-summary.js', 'v02507-move.js', 'v02507-fit.js', 'v02501-navigation.js', 'v02508-real-move.js', 'v02510-ui.js', 'v02511-home-active.js', 'v02512-notification.js', 'v02512-version.js']:
    if f'src="{js}"' not in s:
        s = s.replace('</body>', f'  <script src="{js}"></script>\n</body>', 1)
p.write_text(s, encoding='utf-8')
PY

echo "Imported approved design with v0.25.12 renewed movement notification routing into: $TARGET"
