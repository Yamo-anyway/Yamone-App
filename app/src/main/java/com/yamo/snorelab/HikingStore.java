package com.yamo.snorelab;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Local-only hiking session storage. */
public final class HikingStore {
    private HikingStore() {}

    public static File root(Context context) {
        File dir = new File(context.getFilesDir(), "activity/hiking");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File createSession(Context context, long startMs) {
        String id = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(startMs));
        File dir = new File(root(context), id);
        if (!dir.exists()) dir.mkdirs();
        File route = new File(dir, "route.csv");
        if (!route.exists()) {
            try (BufferedWriter w = new BufferedWriter(new FileWriter(route, false))) {
                w.write("time_ms,lat,lon,accuracy_m,altitude_m,speed_mps\n");
            } catch (Exception ignored) {}
        }
        return dir;
    }

    public static void appendRoute(File dir, long timeMs, double lat, double lon,
                                   float accuracy, double altitude, float speedMps) {
        if (dir == null) return;
        try (BufferedWriter w = new BufferedWriter(new FileWriter(new File(dir, "route.csv"), true))) {
            w.write(String.format(Locale.US, "%d,%.7f,%.7f,%.1f,%.1f,%.3f\n",
                    timeMs, lat, lon, accuracy, altitude, speedMps));
        } catch (Exception ignored) {}
    }

    public static void writeMeta(File dir, JSONObject meta) {
        if (dir == null || meta == null) return;
        try (BufferedWriter w = new BufferedWriter(new FileWriter(new File(dir, "session.json"), false))) {
            w.write(meta.toString(2));
        } catch (Exception ignored) {}
    }

    public static JSONObject readMeta(File dir) {
        if (dir == null) return new JSONObject();
        File f = new File(dir, "session.json");
        if (!f.exists()) return new JSONObject();
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) b.append(line).append('\n');
            return new JSONObject(b.toString());
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static List<File> listSessions(Context context) {
        File[] files = root(context).listFiles(File::isDirectory);
        if (files == null) return new ArrayList<>();
        List<File> out = new ArrayList<>();
        Collections.addAll(out, files);
        out.sort(Comparator.comparing(File::getName).reversed());
        return out;
    }
}
