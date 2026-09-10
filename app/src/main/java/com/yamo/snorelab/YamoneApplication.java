package com.yamo.snorelab;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

/** Installs Yamone UI enhancements and keeps the launcher entry on Home. */
public class YamoneApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private boolean locationSharingRecoveryAttempted;
    private boolean activityRecordingRecoveryAttempted;

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override public void onActivityPreCreated(Activity activity, Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= 29 && activity instanceof MainActivity
                && activity.getIntent().getStringExtra("start_screen") == null) {
            activity.getIntent().putExtra("start_screen", "home");
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        // onActivityPreCreated does not exist on Android 8/9. Re-open once with Home there.
        if (Build.VERSION.SDK_INT < 29 && activity instanceof MainActivity
                && activity.getIntent().getStringExtra("start_screen") == null) {
            Intent home = new Intent(activity, MainActivity.class).putExtra("start_screen", "home");
            activity.startActivity(home);
            activity.finish();
        }
    }

    @Override public void onActivityResumed(Activity activity) {
        if (activity instanceof MainActivity) {
            MainActivity main = (MainActivity) activity;
            SleepUploadUiEnhancer.attach(main);
            ProfileSettingsUiEnhancer.attach(main);
            LocationSharingHomeUiEnhancer.attach(main);
            LocationSharingHomeUiEnhancer.refresh(main);
            recoverLocationSharingIfNeeded(main);
            recoverActivityRecordingIfNeeded(main);
        }

        if (activity instanceof LocationExerciseActivity
                || activity instanceof SkiActivity
                || activity instanceof SkiSessionDetailActivity
                || activity instanceof SkiWaitTimesActivity
                || activity instanceof HikingActivity) {
            ActivitySystemBarUiEnhancer.apply(activity);
        }
    }

    private void recoverLocationSharingIfNeeded(MainActivity activity) {
        if (locationSharingRecoveryAttempted) return;
        locationSharingRecoveryAttempted = true;
        if (!LocationSharingStateStore.isActive(activity)) return;
        boolean hasLocation = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (hasLocation) LocationSharingService.start(activity);
    }

    private void recoverActivityRecordingIfNeeded(MainActivity activity) {
        if (activityRecordingRecoveryAttempted) return;
        activityRecordingRecoveryAttempted = true;
        boolean hasLocation = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasLocation) return;
        if (activity.getSharedPreferences(WalkingRecorderService.PREFS, MODE_PRIVATE)
                .getBoolean(WalkingRecorderService.KEY_RECORDING, false)) {
            startRecorder(activity, new Intent(activity, WalkingRecorderService.class));
        }
        if (activity.getSharedPreferences(HikingRecorderService.PREFS, MODE_PRIVATE)
                .getBoolean(HikingRecorderService.KEY_RECORDING, false)) {
            startRecorder(activity, new Intent(activity, HikingRecorderService.class));
        }
        if (activity.getSharedPreferences(SkiRecorderService.PREFS, MODE_PRIVATE)
                .getBoolean(SkiRecorderService.KEY_RECORDING, false)) {
            startRecorder(activity, new Intent(activity, SkiRecorderService.class));
        }
    }

    private void startRecorder(Activity activity, Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= 26) activity.startForegroundService(intent);
            else activity.startService(intent);
        } catch (Exception ignored) {}
    }

    @Override public void onActivityDestroyed(Activity activity) {
        if (activity instanceof MainActivity) {
            MainActivity main = (MainActivity) activity;
            SleepUploadUiEnhancer.detach(main);
            ProfileSettingsUiEnhancer.detach(main);
            LocationSharingHomeUiEnhancer.detach(main);
        }
        ActivitySystemBarUiEnhancer.detach(activity);
    }

    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
}
