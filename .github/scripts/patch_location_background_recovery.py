from pathlib import Path

ROOT = Path('app/src/main/java/com/yamo/snorelab')

def read(name): return (ROOT / name).read_text()
def write(name, text): (ROOT / name).write_text(text)
def rep(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing {label}: {old[:180]}')
    return text.replace(old, new, 1)

# 1) LocationSharingService: remember which providers are actually registered and
# re-register when GPS/network location is turned back on while sharing continues.
s = read('LocationSharingService.java')
s = rep(s,
'''    private boolean initialFixPending;
    private boolean statusPollInFlight;
    private long lastReportAttemptAt;''',
'''    private boolean initialFixPending;
    private boolean statusPollInFlight;
    private boolean gpsRegistered;
    private boolean networkRegistered;
    private long lastReportAttemptAt;''', 'provider registration fields')

s = rep(s,
'''    private final Runnable statusPoller = new Runnable() {
        @Override public void run() {
            if (!configured) return;
            pollStatusSnapshot();
            handler.postDelayed(this, STATUS_POLL_MS);
        }
    };''',
'''    private final Runnable statusPoller = new Runnable() {
        @Override public void run() {
            if (!configured) return;
            refreshLocationProviderRegistrations();
            pollStatusSnapshot();
            handler.postDelayed(this, STATUS_POLL_MS);
        }
    };''', 'provider refresh in status poller')

s = rep(s,
'''            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        minTimeMs,
                        0f,
                        locationListener,
                        Looper.getMainLooper());
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        minTimeMs,
                        0f,
                        locationListener,
                        Looper.getMainLooper());
            }''',
'''            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        minTimeMs,
                        0f,
                        locationListener,
                        Looper.getMainLooper());
                gpsRegistered = true;
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        minTimeMs,
                        0f,
                        locationListener,
                        Looper.getMainLooper());
                networkRegistered = true;
            }''', 'mark provider registrations')

insert_before = '''    private void tryRecentLastKnownLocation() {'''
helper = '''    private void refreshLocationProviderRegistrations() {
        if (!configured || !hasLocationPermission()) return;
        LocationManager manager = locationManager;
        if (manager == null) manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) return;

        boolean gpsEnabled = false;
        boolean networkEnabled = false;
        try { gpsEnabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER); }
        catch (Exception ignored) {}
        try { networkEnabled = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER); }
        catch (Exception ignored) {}

        if (!gpsEnabled) gpsRegistered = false;
        if (!networkEnabled) networkRegistered = false;

        if ((gpsEnabled && !gpsRegistered) || (networkEnabled && !networkRegistered)) {
            startLocationUpdates();
        }
    }

'''
if insert_before not in s:
    raise SystemExit('missing provider helper insertion')
s = s.replace(insert_before, helper + insert_before, 1)

s = rep(s,
'''        locationListener = null;
        locationManager = null;''',
'''        locationListener = null;
        locationManager = null;
        gpsRegistered = false;
        networkRegistered = false;''', 'clear provider registrations')
write('LocationSharingService.java', s)

# 2) YamoneApplication: if the app process is relaunched while a locally mirrored
# sharing session is still active, restart the foreground location service from
# the visible MainActivity. This also covers a user reopening the app after an OS kill.
s = read('YamoneApplication.java')
s = rep(s,
'''import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;''',
'''import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;''', 'application imports')

s = rep(s,
'''public class YamoneApplication extends Application implements Application.ActivityLifecycleCallbacks {
    @Override public void onCreate() {''',
'''public class YamoneApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private boolean locationSharingRecoveryAttempted;

    @Override public void onCreate() {''', 'application recovery field')

s = rep(s,
'''        if (activity instanceof MainActivity) {
            MainActivity main = (MainActivity) activity;
            SleepUploadUiEnhancer.attach(main);
            ProfileSettingsUiEnhancer.attach(main);
            LocationSharingHomeUiEnhancer.attach(main);
            LocationSharingHomeUiEnhancer.refresh(main);
        }''',
'''        if (activity instanceof MainActivity) {
            MainActivity main = (MainActivity) activity;
            SleepUploadUiEnhancer.attach(main);
            ProfileSettingsUiEnhancer.attach(main);
            LocationSharingHomeUiEnhancer.attach(main);
            LocationSharingHomeUiEnhancer.refresh(main);
            recoverLocationSharingIfNeeded(main);
        }''', 'application resume recovery call')

insert_before = '''    @Override public void onActivityDestroyed(Activity activity) {'''
helper = '''    private void recoverLocationSharingIfNeeded(MainActivity activity) {
        if (locationSharingRecoveryAttempted) return;
        locationSharingRecoveryAttempted = true;
        if (!LocationSharingStateStore.isActive(activity)) return;
        boolean hasLocation = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (hasLocation) LocationSharingService.start(activity);
    }

'''
if insert_before not in s:
    raise SystemExit('missing application helper insertion')
s = s.replace(insert_before, helper + insert_before, 1)
write('YamoneApplication.java', s)

for name, tokens in {
    'LocationSharingService.java': ['refreshLocationProviderRegistrations()', 'gpsRegistered = true', 'networkRegistered = true'],
    'YamoneApplication.java': ['recoverLocationSharingIfNeeded(main)', 'LocationSharingService.start(activity)'],
}.items():
    text = read(name)
    for token in tokens:
        if token not in text:
            raise SystemExit(f'guard failed {name}: {token}')
