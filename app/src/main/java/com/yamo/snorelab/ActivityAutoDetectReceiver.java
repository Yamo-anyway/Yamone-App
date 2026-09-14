package com.yamo.snorelab;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives low-power activity transitions plus Yamone's own delayed checks/actions. */
public final class ActivityAutoDetectReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        ActivityAutoDetectManager.handleIntent(context.getApplicationContext(), intent);
    }
}
