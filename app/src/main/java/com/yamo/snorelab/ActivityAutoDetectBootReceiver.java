package com.yamo.snorelab;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores Activity Recognition transition subscriptions after reboot/app update. */
public final class ActivityAutoDetectBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        ActivityAutoDetectManager.syncRegistration(context.getApplicationContext());
    }
}
