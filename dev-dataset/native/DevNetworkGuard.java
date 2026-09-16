package com.yamo.snorelab;

import android.content.Context;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/** Runtime privacy gate, NOT a substitute for server-side tester authorization. */
final class DevNetworkGuard {
    private static Context app;
    private static long generation;
    private static final Map<HttpURLConnection,Thread> requests=new WeakHashMap<>();
    static synchronized void init(Context c){app=c.getApplicationContext();}
    static synchronized long ticket() throws IOException {check();return generation;}
    static synchronized void check() throws IOException {
        if(Thread.currentThread().isInterrupted())throw new IOException("upload_cancelled");
        if(app==null||!SystemSettingsBridge.isDeveloperMode(app))throw new IOException("developer_mode_required");
    }
    static synchronized void check(long ticket) throws IOException {check();if(ticket!=generation)throw new IOException("upload_cancelled");}
    static synchronized HttpURLConnection open(URL url) throws IOException {
        check();
        HttpURLConnection c=(HttpURLConnection)url.openConnection(); requests.put(c,Thread.currentThread());return c;
    }
    static synchronized void cancel(){generation++;for(HttpURLConnection c:new ArrayList<>(requests.keySet()))if(c!=null)c.disconnect();requests.clear();}
    static synchronized void cancelThread(Thread thread){
        for(Map.Entry<HttpURLConnection,Thread> e:new ArrayList<>(requests.entrySet()))
            if(e.getValue()==thread&&e.getKey()!=null){e.getKey().disconnect();requests.remove(e.getKey());}
    }
    static synchronized void done(HttpURLConnection c){requests.remove(c);if(c!=null)c.disconnect();}
}
