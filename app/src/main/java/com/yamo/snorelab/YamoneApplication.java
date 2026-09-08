package com.yamo.snorelab;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/** Installs small UI enhancements without changing the existing MainActivity layout implementation. */
public class YamoneApplication extends Application implements Application.ActivityLifecycleCallbacks {
    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override public void onActivityResumed(Activity activity) {
        if (activity instanceof MainActivity) {
            SleepUploadUiEnhancer.attach((MainActivity) activity);
        }
    }

    @Override public void onActivityDestroyed(Activity activity) {
        if (activity instanceof MainActivity) {
            SleepUploadUiEnhancer.detach((MainActivity) activity);
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
}
