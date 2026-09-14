package com.yamo.snorelab;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores Activity Recognition and developer-only Snow geofences after reboot/app update. */
public final class ActivityAutoDetectBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        ActivityAutoDetectManager.syncRegistration(app);
        // SnowAutoDetectManager is self-gated: release/locked builds register nothing.
        SnowAutoDetectManager.sync(app);
    }
}
