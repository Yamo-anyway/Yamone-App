package com.yamo.snorelab;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Local marker for explicit one-time sleep uploads. */
public final class SleepUploadState {
    private static final String FILE_NAME = "server-upload.json";

    private SleepUploadState() {}

    public static String clientRecordId(File sessionDir, JSONObject meta) {
        if (sessionDir == null || meta == null) return "";
        long start = meta.optLong("startEpochMs", 0);
        long end = meta.optLong("endEpochMs", 0);
        if (start <= 0 || end <= start) return "";
        String seed = "yamone:sleep:v1:" + sessionDir.getName() + ":" + start + ":" + end;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static boolean wasUploaded(File sessionDir, JSONObject meta) {
        JSONObject state = read(sessionDir);
        String expected = clientRecordId(sessionDir, meta);
        return !expected.isEmpty()
                && expected.equals(state.optString("clientRecordId", ""))
                && state.optLong("uploadedAtEpochMs", 0) > 0;
    }

    public static boolean markUploaded(File sessionDir, JSONObject meta, long uploadedAtEpochMs) {
        String id = clientRecordId(sessionDir, meta);
        if (id.isEmpty()) return false;
        try {
            JSONObject state = new JSONObject();
            state.put("clientRecordId", id);
            state.put("uploadedAtEpochMs", Math.max(1, uploadedAtEpochMs));
            write(sessionDir, state);
            return wasUploaded(sessionDir, meta);
        } catch (Exception e) {
            return false;
        }
    }

    private static JSONObject read(File sessionDir) {
        if (sessionDir == null) return new JSONObject();
        File file = new File(sessionDir, FILE_NAME);
        if (!file.isFile()) return new JSONObject();
        try (FileInputStream in = new FileInputStream(file);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static void write(File sessionDir, JSONObject state) throws Exception {
        if (sessionDir == null || !sessionDir.isDirectory()) throw new IllegalArgumentException("invalid session");
        File file = new File(sessionDir, FILE_NAME);
        File temp = new File(sessionDir, FILE_NAME + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp, false)) {
            out.write(state.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
        if (file.exists() && !file.delete()) throw new IllegalStateException("cannot replace upload state");
        if (!temp.renameTo(file)) throw new IllegalStateException("cannot save upload state");
    }
}
