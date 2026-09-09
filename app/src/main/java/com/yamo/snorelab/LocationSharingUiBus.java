package com.yamo.snorelab;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-process UI notification bus for immediate location-sharing state changes. */
public final class LocationSharingUiBus {
    public interface Listener { void onLocationSharingStateChanged(); }

    private static final CopyOnWriteArrayList<WeakReference<Listener>> LISTENERS = new CopyOnWriteArrayList<>();

    private LocationSharingUiBus() {}

    public static void add(Listener listener) {
        if (listener == null) return;
        remove(listener);
        LISTENERS.add(new WeakReference<>(listener));
    }

    public static void remove(Listener listener) {
        if (listener == null) return;
        Iterator<WeakReference<Listener>> it = LISTENERS.iterator();
        while (it.hasNext()) {
            WeakReference<Listener> ref = it.next();
            Listener value = ref.get();
            if (value == null || value == listener) LISTENERS.remove(ref);
        }
    }

    public static void notifyChanged() {
        for (WeakReference<Listener> ref : LISTENERS) {
            Listener listener = ref.get();
            if (listener == null) {
                LISTENERS.remove(ref);
                continue;
            }
            try { listener.onLocationSharingStateChanged(); }
            catch (Exception ignored) {}
        }
    }
}
