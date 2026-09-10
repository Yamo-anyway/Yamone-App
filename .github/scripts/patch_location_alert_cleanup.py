from pathlib import Path
p = Path('app/src/main/java/com/yamo/snorelab/LocationSharingService.java')
s = p.read_text()
old = '''    public static void stop(Context context) {
        context.stopService(new Intent(context, LocationSharingService.class));
    }'''
new = '''    public static void stop(Context context) {
        LocationStatusAlert.clear(context);
        context.stopService(new Intent(context, LocationSharingService.class));
    }'''
if old not in s:
    raise SystemExit('missing LocationSharingService.stop')
s = s.replace(old, new, 1)
old2 = '''                            if (lower.contains("참여 중인 위치 공유 방이 없습니다")) {
                                LocationSharingStateStore.clear(LocationSharingService.this);
                                cancelWarning();'''
new2 = '''                            if (lower.contains("참여 중인 위치 공유 방이 없습니다")) {
                                LocationSharingStateStore.clear(LocationSharingService.this);
                                LocationStatusAlert.clear(LocationSharingService.this);
                                cancelWarning();'''
if old2 not in s:
    raise SystemExit('missing no-room failure cleanup')
s = s.replace(old2, new2, 1)
p.write_text(s)
