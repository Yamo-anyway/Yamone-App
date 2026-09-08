package com.yamo.snorelab;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Uploads one completed activity record only when explicitly called by the UI.
 * No automatic/background sync is performed.
 *
 * The client creates an anonymous Supabase Auth session on the first explicit
 * upload. No email, phone, display name, device model, advertising ID, or app
 * usage profile is sent by this class.
 */
public final class SupabaseActivityUploader {
    private static final String SUPABASE_URL = "https://kumucxomdviuwuwqcpmv.supabase.co";
    private static final String PUBLISHABLE_KEY = "sb_publishable_YYZp1A9A62KUxs5etSAiYg_0TvaK2hQ";
    private static final String AUTH_PREFS = "yamone_supabase_anonymous_auth_v1";
    private static final long ACTIVITY_ROUTE_MAX_BYTES = 2L * 1024L * 1024L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private SupabaseActivityUploader() {}

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
                callback.onFailure(uploadErrorMessage(e));
            }
        });
    }

    private static String uploadErrorMessage(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.US);
                if (lower.contains("anonymous sign-in disabled")) {
                    return "서버의 익명 로그인 설정이 꺼져 있어 업로드할 수 없습니다. 관리자 설정을 확인해 주세요.";
                }
                if (lower.contains("anonymous sign-in failed")) {
                    return "익명 서버 연결을 만들지 못했습니다. 네트워크 또는 서버 인증 설정을 확인해 주세요.";
                }
                if (lower.contains("receipt check failed")) {
                    return "이 기록의 기존 업로드 여부를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.";
                }
            }
            current = current.getCause();
        }
        return "업로드 중 오류가 발생했습니다.";
    }

    private static Result uploadBlocking(Context context, File sessionDir) throws Exception {
        if (sessionDir == null || !sessionDir.isDirectory()) return Result.fail("활동 기록을 찾을 수 없습니다.");

        JSONObject meta = WalkingStore.readMeta(sessionDir);
        if (!"complete".equals(meta.optString("status"))) return Result.fail("완료된 활동 기록만 업로드할 수 있습니다.");
        if (meta.optBoolean("uploaded", false)) return Result.ok(true);

        long startMs = meta.optLong("startEpochMs", 0);
        long endMs = meta.optLong("endEpochMs", 0);
        if (startMs <= 0 || endMs <= startMs) return Result.fail("완료 시간이 올바르지 않은 기록입니다.");

        String clientRecordId = WalkingStore.ensureClientRecordId(sessionDir);
        if (clientRecordId.isEmpty()) return Result.fail("기록 식별값을 만들 수 없습니다.");

        AuthSession auth = ensureAnonymousSession(context);
        if (auth == null) return Result.fail("익명 서버 연결을 만들 수 없습니다.");

        if (recordWasUploaded(auth.accessToken, clientRecordId)) {
            WalkingStore.markUploaded(sessionDir, System.currentTimeMillis());
            return Result.ok(true);
        }

        File routeFile = new File(sessionDir, "route.csv");
        String routeStoragePath = null;
        long routeBytes = 0;
        String routeSha256 = null;
        boolean routeUploadedNow = false;

        if (routeFile.isFile() && routeFile.length() > 0) {
            routeBytes = routeFile.length();
            if (routeBytes > ACTIVITY_ROUTE_MAX_BYTES) {
                return Result.fail("이 활동의 경로 파일이 업로드 제한을 초과했습니다.");
            }
            routeStoragePath = auth.userId + "/" + clientRecordId + "/route.csv";
            routeSha256 = sha256(routeFile);
            UploadObjectResult objectResult = uploadRoute(auth.accessToken, routeStoragePath, routeFile);
            if (!objectResult.success && !objectResult.alreadyExists) {
                return Result.fail(objectResult.message);
            }
            routeUploadedNow = objectResult.success;
        }

        JSONObject payload = buildActivityPayload(meta, clientRecordId, routeStoragePath, routeBytes, routeSha256);
        HttpResult insert = jsonRequest(
                "POST",
                SUPABASE_URL + "/rest/v1/activity_sessions",
                payload.toString(),
                auth.accessToken,
                "return=minimal"
        );

        if (insert.code >= 200 && insert.code < 300) {
            if (!WalkingStore.markUploaded(sessionDir, System.currentTimeMillis())) {
                return Result.fail("서버 업로드는 완료됐지만 휴대폰의 완료 표시 저장에 실패했습니다.");
            }
            return Result.ok(false);
        }

        if (insert.body.contains("record_already_uploaded") || insert.body.contains("duplicate key")) {
            if (routeUploadedNow && routeStoragePath != null) deleteRoute(auth.accessToken, routeStoragePath);
            WalkingStore.markUploaded(sessionDir, System.currentTimeMillis());
            return Result.ok(true);
        }

        if (routeUploadedNow && routeStoragePath != null) deleteRoute(auth.accessToken, routeStoragePath);
        return Result.fail(serverMessage(insert, "활동 기록 업로드에 실패했습니다."));
    }

    private static JSONObject buildActivityPayload(JSONObject meta, String clientRecordId,
                                                   String routePath, long routeBytes, String routeSha256) throws Exception {
        long startMs = meta.optLong("startEpochMs", 0);
        long endMs = meta.optLong("endEpochMs", 0);
        long durationMs = Math.max(0, meta.optLong("durationMs", endMs - startMs));
        long movingMs = Math.max(0, meta.optLong("movingMs", 0));
        long distanceM = Math.max(0, meta.optLong("distanceM", 0));
        long steps = Math.max(0, meta.optLong("steps", 0));
        String type = meta.optString("type", "walking");
        if (!"walking".equals(type) && !"running".equals(type) && !"walkrun".equals(type) && !"cycling".equals(type)) {
            type = "walking";
        }

        JSONObject metrics = new JSONObject();
        metrics.put("moving_ms", movingMs);
        metrics.put("goal_distance_m", Math.max(0, meta.optLong("goalDistanceM", 0)));
        metrics.put("goal_time_ms", Math.max(0, meta.optLong("goalTimeMs", 0)));
        metrics.put("goal_state", meta.optString("goalState", "ACTIVE"));
        metrics.put("gps_filter", meta.optString("gpsFilter", ""));
        metrics.put("rejected_gps_points", Math.max(0, meta.optInt("rejectedGpsPoints", 0)));
        metrics.put("gps_gap_resets", Math.max(0, meta.optInt("gpsGapResets", 0)));
        metrics.put("stationary_gps_discards", Math.max(0, meta.optInt("stationaryGpsDiscards", 0)));

        JSONArray splits = meta.optJSONArray("splitsMs");
        if (splits != null) metrics.put("splits_ms", splits);

        if ("walkrun".equals(type)) {
            metrics.put("walking_distance_m", Math.max(0, meta.optLong("walkingDistanceM", 0)));
            metrics.put("running_distance_m", Math.max(0, meta.optLong("runningDistanceM", 0)));
            metrics.put("walking_moving_ms", Math.max(0, meta.optLong("walkingMovingMs", 0)));
            metrics.put("running_moving_ms", Math.max(0, meta.optLong("runningMovingMs", 0)));
            metrics.put("auto_mode_switches", Math.max(0, meta.optInt("autoModeSwitches", 0)));
        }

        if (routePath != null) {
            metrics.put("route_storage_path", routePath);
            metrics.put("route_file_bytes", routeBytes);
            if (routeSha256 != null) metrics.put("route_sha256", routeSha256);
            metrics.put("route_format", "csv_v1");
        }

        JSONObject payload = new JSONObject();
        payload.put("client_record_id", clientRecordId);
        payload.put("activity_type", type);
        payload.put("started_at", isoUtc(startMs));
        payload.put("ended_at", isoUtc(endMs));
        payload.put("duration_seconds", Math.max(0, Math.round(durationMs / 1000.0)));
        payload.put("distance_m", distanceM);
        payload.put("steps", "cycling".equals(type) ? 0 : steps);
        if (movingMs > 0 && distanceM > 0) payload.put("avg_speed_mps", distanceM / (movingMs / 1000.0));
        payload.put("max_speed_mps", Math.max(0, meta.optDouble("maxSpeedKmh", 0)) / 3.6);
        payload.put("metrics", metrics);
        return payload;
    }

    private static boolean recordWasUploaded(String accessToken, String clientRecordId) throws Exception {
        JSONObject args = new JSONObject();
        args.put("p_record_kind", "activity");
        args.put("p_client_record_id", clientRecordId);
        HttpResult result = jsonRequest(
                "POST",
                SUPABASE_URL + "/rest/v1/rpc/record_was_uploaded",
                args.toString(),
                accessToken,
                null
        );
        if (result.code < 200 || result.code >= 300) throw new IllegalStateException("receipt check failed");
        return "true".equalsIgnoreCase(result.body.trim());
    }

    private static UploadObjectResult uploadRoute(String accessToken, String path, File file) throws Exception {
        URL url = new URL(SUPABASE_URL + "/storage/v1/object/activity-data/" + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15_000);
        c.setReadTimeout(30_000);
        c.setDoOutput(true);
        c.setRequestProperty("apikey", PUBLISHABLE_KEY);
        c.setRequestProperty("Authorization", "Bearer " + accessToken);
        c.setRequestProperty("Content-Type", "application/octet-stream");
        c.setRequestProperty("x-upsert", "false");
        c.setFixedLengthStreamingMode(file.length());

        try (OutputStream out = c.getOutputStream(); BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        }

        int code = c.getResponseCode();
        String body = readBody(c);
        c.disconnect();
        if (code >= 200 && code < 300) return UploadObjectResult.ok();
        if (code == 409 || body.toLowerCase(Locale.US).contains("already exists")) return UploadObjectResult.exists();
        return UploadObjectResult.fail(serverMessage(new HttpResult(code, body), "GPS 경로 업로드에 실패했습니다."));
    }

    private static void deleteRoute(String accessToken, String path) {
        try {
            URL url = new URL(SUPABASE_URL + "/storage/v1/object/activity-data/" + path);
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("DELETE");
            c.setConnectTimeout(10_000);
            c.setReadTimeout(10_000);
            c.setRequestProperty("apikey", PUBLISHABLE_KEY);
            c.setRequestProperty("Authorization", "Bearer " + accessToken);
            c.getResponseCode();
            c.disconnect();
        } catch (Exception ignored) {}
    }

    private static AuthSession ensureAnonymousSession(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE);
        String access = prefs.getString("access_token", "");
        String refresh = prefs.getString("refresh_token", "");
        String userId = prefs.getString("user_id", "");
        long expiresAt = prefs.getLong("expires_at_ms", 0);

        if (!access.isEmpty() && !userId.isEmpty() && expiresAt > System.currentTimeMillis() + 60_000L) {
            return new AuthSession(access, refresh, userId, expiresAt);
        }

        if (!refresh.isEmpty()) {
            JSONObject body = new JSONObject();
            body.put("refresh_token", refresh);
            HttpResult refreshed = authRequest("/auth/v1/token?grant_type=refresh_token", body.toString());
            if (refreshed.code >= 200 && refreshed.code < 300) {
                AuthSession session = parseAndSaveSession(prefs, refreshed.body);
                if (session != null) return session;
            }
        }

        JSONObject anonymousBody = new JSONObject();
        anonymousBody.put("data", new JSONObject());
        HttpResult created = authRequest("/auth/v1/signup", anonymousBody.toString());
        if (created.code < 200 || created.code >= 300) {
            String lower = created.body.toLowerCase(Locale.US);
            if (lower.contains("anonymous") && lower.contains("disabled")) {
                throw new IllegalStateException("anonymous sign-in disabled");
            }
            throw new IllegalStateException("anonymous sign-in failed");
        }
        AuthSession session = parseAndSaveSession(prefs, created.body);
        if (session == null) throw new IllegalStateException("invalid auth response");
        return session;
    }

    private static HttpResult authRequest(String path, String json) throws Exception {
        URL url = new URL(SUPABASE_URL + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15_000);
        c.setReadTimeout(20_000);
        c.setDoOutput(true);
        c.setRequestProperty("apikey", PUBLISHABLE_KEY);
        c.setRequestProperty("Authorization", "Bearer " + PUBLISHABLE_KEY);
        c.setRequestProperty("Content-Type", "application/json");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
        int code = c.getResponseCode();
        String body = readBody(c);
        c.disconnect();
        return new HttpResult(code, body);
    }

    private static AuthSession parseAndSaveSession(SharedPreferences prefs, String body) {
        try {
            JSONObject json = new JSONObject(body);
            String access = json.optString("access_token", "");
            String refresh = json.optString("refresh_token", "");
            JSONObject user = json.optJSONObject("user");
            String userId = user == null ? "" : user.optString("id", "");
            long expiresIn = Math.max(60, json.optLong("expires_in", 3600));
            long expiresAt = System.currentTimeMillis() + expiresIn * 1000L;
            if (access.isEmpty() || userId.isEmpty()) return null;
            prefs.edit()
                    .putString("access_token", access)
                    .putString("refresh_token", refresh)
                    .putString("user_id", userId)
                    .putLong("expires_at_ms", expiresAt)
                    .apply();
            return new AuthSession(access, refresh, userId, expiresAt);
        } catch (Exception e) {
            return null;
        }
    }

    private static HttpResult jsonRequest(String method, String urlString, String json,
                                          String accessToken, String prefer) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15_000);
        c.setReadTimeout(25_000);
        c.setDoOutput(true);
        c.setRequestProperty("apikey", PUBLISHABLE_KEY);
        c.setRequestProperty("Authorization", "Bearer " + accessToken);
        c.setRequestProperty("Content-Type", "application/json");
        if (prefer != null) c.setRequestProperty("Prefer", prefer);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
        int code = c.getResponseCode();
        String body = readBody(c);
        c.disconnect();
        return new HttpResult(code, body);
    }

    private static String readBody(HttpURLConnection c) {
        InputStream stream = null;
        try {
            stream = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
            if (stream == null) return "";
            try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                StringBuilder b = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) b.append(line);
                return b.toString();
            }
        } catch (Exception e) {
            return "";
        }
    }

    private static String serverMessage(HttpResult result, String fallback) {
        try {
            JSONObject json = new JSONObject(result.body);
            String message = json.optString("message", "");
            if (message.isEmpty()) message = json.optString("msg", "");
            if (!message.isEmpty()) {
                if (message.contains("db_write_disabled") || message.contains("storage")) return "무료 운영 안전 한도 때문에 현재 업로드가 잠겨 있습니다.";
                return fallback;
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private static String isoUtc(long epochMs) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(epochMs));
    }

    private static String sha256(File file) {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) digest.update(buffer, 0, n);
            byte[] hash = digest.digest();
            StringBuilder b = new StringBuilder(hash.length * 2);
            for (byte v : hash) b.append(String.format(Locale.US, "%02x", v & 0xff));
            return b.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static final class HttpResult {
        final int code;
        final String body;
        HttpResult(int code, String body) { this.code = code; this.body = body == null ? "" : body; }
    }

    private static final class AuthSession {
        final String accessToken;
        final String refreshToken;
        final String userId;
        final long expiresAtMs;
        AuthSession(String accessToken, String refreshToken, String userId, long expiresAtMs) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.userId = userId;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private static final class UploadObjectResult {
        final boolean success;
        final boolean alreadyExists;
        final String message;
        private UploadObjectResult(boolean success, boolean alreadyExists, String message) {
            this.success = success;
            this.alreadyExists = alreadyExists;
            this.message = message;
        }
        static UploadObjectResult ok() { return new UploadObjectResult(true, false, ""); }
        static UploadObjectResult exists() { return new UploadObjectResult(false, true, ""); }
        static UploadObjectResult fail(String message) { return new UploadObjectResult(false, false, message); }
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
