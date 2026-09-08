package com.yamo.snorelab;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends one completed sleep record only after an explicit user action.
 * Audio bytes, audio file names, and audio file paths are intentionally excluded.
 */
public final class SupabaseSleepUploader {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private SupabaseSleepUploader() {}

    public interface Callback {
        void onSuccess(boolean alreadyUploaded);
        void onFailure(String message);
    }

    public static void upload(Context context, File sessionDir, Callback callback) {
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                Result result = uploadBlocking(appContext, sessionDir);
                if (result.success) callback.onSuccess(result.alreadyUploaded);
                else callback.onFailure(result.message);
            } catch (Exception e) {
                callback.onFailure("수면 기록 업로드 중 오류가 발생했습니다.");
            }
        });
    }

    private static Result uploadBlocking(Context context, File sessionDir) throws Exception {
        if (!isOwnedSession(context, sessionDir)) return Result.fail("수면 기록을 찾을 수 없습니다.");

        JSONObject meta = SessionStore.readMeta(sessionDir);
        if (!"complete".equals(meta.optString("status"))) {
            return Result.fail("완료된 수면 기록만 업로드할 수 있습니다.");
        }

        long startMs = meta.optLong("startEpochMs", 0);
        long endMs = meta.optLong("endEpochMs", 0);
        if (startMs <= 0 || endMs <= startMs) return Result.fail("완료 시간이 올바르지 않은 기록입니다.");
        if (SleepUploadState.wasUploaded(sessionDir, meta)) return Result.ok(true);

        String clientRecordId = SleepUploadState.clientRecordId(sessionDir, meta);
        if (clientRecordId.isEmpty()) return Result.fail("기록 식별값을 만들 수 없습니다.");

        Object auth = callPrivateStatic("ensureAnonymousSession", new Class[]{Context.class}, context);
        if (auth == null) return Result.fail("익명 서버 연결을 만들 수 없습니다.");
        String accessToken = readStringField(auth, "accessToken");
        if (accessToken.isEmpty()) return Result.fail("익명 서버 연결을 만들 수 없습니다.");

        if (recordWasUploaded(accessToken, clientRecordId)) {
            SleepUploadState.markUploaded(sessionDir, meta, System.currentTimeMillis());
            return Result.ok(true);
        }

        JSONArray events = SessionStore.readEvents(sessionDir);
        JSONObject payload = buildPayload(meta, events, clientRecordId);
        Object response = jsonRequest("POST", supabaseUrl() + "/rest/v1/sleep_sessions",
                payload.toString(), accessToken, "return=minimal");
        int code = readIntField(response, "code");
        String body = readStringField(response, "body");

        if (code >= 200 && code < 300) {
            if (!SleepUploadState.markUploaded(sessionDir, meta, System.currentTimeMillis())) {
                return Result.fail("서버 업로드는 완료됐지만 휴대폰의 완료 표시 저장에 실패했습니다.");
            }
            return Result.ok(false);
        }

        if (body.contains("record_already_uploaded") || body.contains("duplicate key")) {
            SleepUploadState.markUploaded(sessionDir, meta, System.currentTimeMillis());
            return Result.ok(true);
        }
        if (body.contains("db_write_disabled")) {
            return Result.fail("무료 운영 안전 한도 때문에 현재 업로드가 잠겨 있습니다.");
        }
        return Result.fail("수면 기록 업로드에 실패했습니다.");
    }

    private static JSONObject buildPayload(JSONObject meta, JSONArray events, String clientRecordId) throws Exception {
        long startMs = meta.optLong("startEpochMs", 0);
        long endMs = meta.optLong("endEpochMs", 0);
        long durationMs = Math.max(0, meta.optLong("durationMs", endMs - startMs));
        int confirmed = Math.max(0, meta.optInt("snoreConfirmedCount", 0));
        long confirmedMs = Math.max(0, meta.optLong("confirmedSnoreMs", 0));
        long candidateMs = 0;

        JSONArray cleanEvents = new JSONArray();
        if (events != null) {
            for (int i = 0; i < events.length(); i++) {
                JSONObject source = events.optJSONObject(i);
                if (source == null) continue;
                long eventDuration = Math.max(0, source.optLong("durationMs", 0));
                candidateMs += eventDuration;

                JSONObject event = new JSONObject();
                event.put("index", i);
                event.put("auto_candidate", true);
                event.put("start_offset_ms", Math.max(0, source.optLong("startOffsetMs", 0)));
                event.put("end_offset_ms", Math.max(0, source.optLong("endOffsetMs", 0)));
                event.put("duration_ms", eventDuration);
                putDoubleIfPresent(event, "score_avg", source, "scoreAvg");
                putDoubleIfPresent(event, "score_max", source, "scoreMax");
                putDoubleIfPresent(event, "dbfs_avg", source, "dbfsAvg");
                putDoubleIfPresent(event, "dbfs_max", source, "dbfsMax");
                putDoubleIfPresent(event, "low_band_ratio_avg", source, "lowBandRatioAvg");
                putDoubleIfPresent(event, "periodicity_avg", source, "periodicityAvg");
                putDoubleIfPresent(event, "periodicity_max", source, "periodicityMax");
                putDoubleIfPresent(event, "zero_cross_rate_avg", source, "zeroCrossRateAvg");
                putDoubleIfPresent(event, "threshold_avg", source, "thresholdAvg");
                if (source.has("candidateWindowCount") && !source.isNull("candidateWindowCount")) {
                    event.put("candidate_window_count", Math.max(0, source.optInt("candidateWindowCount", 0)));
                }
                event.put("review_label", reviewLabel(source.optString("reviewLabel", "UNREVIEWED")));
                cleanEvents.put(event);
            }
        }

        JSONObject metrics = new JSONObject();
        metrics.put("analysis_schema", "sleep_features_v2");
        String detectorVersion = meta.optString("detectorVersion", "");
        if (!detectorVersion.isEmpty()) metrics.put("detector_version", detectorVersion);
        metrics.put("candidate_count", cleanEvents.length());
        metrics.put("candidate_duration_ms", candidateMs);
        metrics.put("reviewed_count", Math.max(0, meta.optInt("reviewedCount", 0)));
        metrics.put("confirmed_count", confirmed);
        metrics.put("rejected_count", Math.max(0, meta.optInt("snoreRejectedCount", 0)));
        metrics.put("uncertain_count", Math.max(0, meta.optInt("uncertainCount", 0)));
        metrics.put("confirmed_snore_ms", confirmedMs);
        metrics.put("events", cleanEvents);
        metrics.put("audio_uploaded", false);

        JSONObject payload = new JSONObject();
        payload.put("client_record_id", clientRecordId);
        payload.put("started_at", isoUtc(startMs));
        payload.put("ended_at", isoUtc(endMs));
        payload.put("duration_seconds", Math.max(0, Math.round(durationMs / 1000.0)));
        payload.put("snore_seconds", Math.max(0, Math.round(confirmedMs / 1000.0)));
        payload.put("snore_events", confirmed);
        payload.put("metrics", metrics);
        return payload;
    }

    private static void putDoubleIfPresent(JSONObject target, String targetKey,
                                           JSONObject source, String sourceKey) throws Exception {
        if (!source.has(sourceKey) || source.isNull(sourceKey)) return;
        double value = source.optDouble(sourceKey, Double.NaN);
        if (!Double.isNaN(value) && !Double.isInfinite(value)) target.put(targetKey, value);
    }

    private static boolean recordWasUploaded(String accessToken, String clientRecordId) throws Exception {
        JSONObject args = new JSONObject();
        args.put("p_record_kind", "sleep");
        args.put("p_client_record_id", clientRecordId);
        Object response = jsonRequest("POST", supabaseUrl() + "/rest/v1/rpc/record_was_uploaded",
                args.toString(), accessToken, null);
        int code = readIntField(response, "code");
        if (code < 200 || code >= 300) throw new IllegalStateException("receipt check failed");
        return "true".equalsIgnoreCase(readStringField(response, "body").trim());
    }

    private static String reviewLabel(String value) {
        if ("SNORE".equals(value) || "NOT_SNORE".equals(value) || "UNCERTAIN".equals(value)) return value;
        return "UNREVIEWED";
    }

    private static boolean isOwnedSession(Context context, File dir) {
        if (context == null || dir == null || !dir.isDirectory()) return false;
        try {
            String root = SessionStore.sessionsRoot(context).getCanonicalPath() + File.separator;
            return dir.getCanonicalPath().startsWith(root);
        } catch (Exception e) {
            return false;
        }
    }

    private static Object jsonRequest(String method, String url, String body, String token, String prefer) throws Exception {
        return callPrivateStatic("jsonRequest",
                new Class[]{String.class, String.class, String.class, String.class, String.class},
                method, url, body, token, prefer);
    }

    private static String supabaseUrl() throws Exception {
        Field field = SupabaseActivityUploader.class.getDeclaredField("SUPABASE_URL");
        field.setAccessible(true);
        Object value = field.get(null);
        return value == null ? "" : String.valueOf(value);
    }

    private static String isoUtc(long epochMs) throws Exception {
        Object value = callPrivateStatic("isoUtc", new Class[]{long.class}, epochMs);
        return value == null ? "" : String.valueOf(value);
    }

    private static Object callPrivateStatic(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = SupabaseActivityUploader.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static String readStringField(Object object, String name) throws Exception {
        if (object == null) return "";
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(object);
        return value == null ? "" : String.valueOf(value);
    }

    private static int readIntField(Object object, String name) throws Exception {
        if (object == null) return 0;
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(object);
    }

    private static final class Result {
        final boolean success;
        final boolean alreadyUploaded;
        final String message;
        private Result(boolean success, boolean alreadyUploaded, String message) {
            this.success = success;
            this.alreadyUploaded = alreadyUploaded;
            this.message = message;
        }
        static Result ok(boolean alreadyUploaded) { return new Result(true, alreadyUploaded, ""); }
        static Result fail(String message) { return new Result(false, false, message); }
    }
}
