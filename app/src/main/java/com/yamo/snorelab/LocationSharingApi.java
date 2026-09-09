package com.yamo.snorelab;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal Supabase RPC client for ephemeral location sharing. */
public final class LocationSharingApi {
    private static final String SUPABASE_URL = "https://kumucxomdviuwuwqcpmv.supabase.co";
    private static final String PUBLISHABLE_KEY = "sb_publishable_YYZp1A9A62KUxs5etSAiYg_0TvaK2hQ";
    private static final String AUTH_PREFS = "yamone_supabase_anonymous_auth_v1";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private LocationSharingApi() {}

    public interface JsonCallback {
        void onSuccess(JSONObject data);
        void onFailure(String message);
    }

    public interface BooleanCallback {
        void onSuccess(boolean value);
        void onFailure(String message);
    }

    public static void roomNameAvailable(Context context, String roomName, BooleanCallback callback) {
        run(context, () -> {
            JSONObject args = new JSONObject();
            args.put("p_room_name", roomName);
            HttpResult result = rpc(context, "location_room_name_available", args);
            if (!result.ok()) throw new IllegalStateException(serverMessage(result));
            callback.onSuccess("true".equalsIgnoreCase(result.body.trim()));
        }, callback::onFailure);
    }

    public static void createRoom(Context context, String roomName, String password, String nickname,
                                  int intervalSeconds, int shareMinutes, JsonCallback callback) {
        JSONObject args = new JSONObject();
        try {
            args.put("p_room_name", roomName);
            args.put("p_password", password);
            args.put("p_nickname", nickname);
            args.put("p_update_interval_seconds", intervalSeconds);
            args.put("p_share_minutes", shareMinutes);
        } catch (Exception e) {
            callback.onFailure("입력값을 준비하지 못했습니다.");
            return;
        }
        rpcAsync(context, "location_create_room", args, callback);
    }

    public static void joinRoom(Context context, String roomName, String password, String nickname,
                                int intervalSeconds, int shareMinutes, JsonCallback callback) {
        JSONObject args = new JSONObject();
        try {
            args.put("p_room_name", roomName);
            args.put("p_password", password);
            args.put("p_nickname", nickname);
            args.put("p_update_interval_seconds", intervalSeconds);
            args.put("p_share_minutes", shareMinutes);
        } catch (Exception e) {
            callback.onFailure("입력값을 준비하지 못했습니다.");
            return;
        }
        rpcAsync(context, "location_join_room", args, callback);
    }

    public static void snapshot(Context context, JsonCallback callback) {
        rpcAsync(context, "location_room_snapshot", new JSONObject(), callback);
    }

    public static void leave(Context context, JsonCallback callback) {
        rpcAsync(context, "location_leave", new JSONObject(), callback);
    }

    public static void updateNickname(Context context, String nickname, JsonCallback callback) {
        JSONObject args = new JSONObject();
        try { args.put("p_nickname", nickname); }
        catch (Exception e) { callback.onFailure("닉네임을 준비하지 못했습니다."); return; }
        rpcAsync(context, "location_update_nickname", args, callback);
    }

    public static void setInterval(Context context, int seconds, JsonCallback callback) {
        JSONObject args = new JSONObject();
        try { args.put("p_update_interval_seconds", seconds); }
        catch (Exception e) { callback.onFailure("갱신 주기를 준비하지 못했습니다."); return; }
        rpcAsync(context, "location_set_interval", args, callback);
    }

    public static void extend(Context context, int minutes, JsonCallback callback) {
        JSONObject args = new JSONObject();
        try { args.put("p_add_minutes", minutes); }
        catch (Exception e) { callback.onFailure("연장 시간을 준비하지 못했습니다."); return; }
        rpcAsync(context, "location_extend", args, callback);
    }

    public static void report(Context context, double lat, double lon, Float accuracy, JsonCallback callback) {
        JSONObject args = new JSONObject();
        try {
            args.put("p_lat", lat);
            args.put("p_lon", lon);
            if (accuracy == null) args.put("p_accuracy_m", JSONObject.NULL);
            else args.put("p_accuracy_m", accuracy.doubleValue());
        } catch (Exception e) {
            callback.onFailure("위치값을 준비하지 못했습니다.");
            return;
        }
        rpcAsync(context, "location_report", args, callback);
    }

    public static void heartbeat(Context context, JsonCallback callback) {
        rpcAsync(context, "location_heartbeat", new JSONObject(), callback);
    }

    private static void rpcAsync(Context context, String name, JSONObject args, JsonCallback callback) {
        run(context, () -> {
            HttpResult result = rpc(context, name, args);
            if (!result.ok()) throw new IllegalStateException(serverMessage(result));
            String body = result.body.trim();
            JSONObject data = body.isEmpty() ? new JSONObject() : new JSONObject(body);
            callback.onSuccess(data);
        }, callback::onFailure);
    }

    private static HttpResult rpc(Context context, String name, JSONObject args) throws Exception {
        AuthSession session = ensureAnonymousSession(context.getApplicationContext());
        return jsonPost(SUPABASE_URL + "/rest/v1/rpc/" + name, args.toString(), session.accessToken);
    }

    private interface ThrowingRunnable { void run() throws Exception; }
    private interface Failure { void onFailure(String message); }

    private static void run(Context context, ThrowingRunnable task, Failure failure) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try { task.run(); }
            catch (Exception e) { failure.onFailure(errorMessage(e)); }
        });
    }

    private static String errorMessage(Throwable error) {
        String message = "";
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isEmpty()) {
                message = current.getMessage();
                break;
            }
            current = current.getCause();
        }
        if (message.isEmpty()) return "위치 공유 서버에 연결하지 못했습니다.";
        return friendly(message);
    }

    private static String friendly(String raw) {
        String lower = raw.toLowerCase(Locale.US);
        if (lower.contains("anonymous sign-in disabled")) return "서버의 익명 로그인 설정이 꺼져 있습니다.";
        if (lower.contains("room_name_taken")) return "이미 사용 중인 방 이름입니다.";
        if (lower.contains("already_in_location_room")) return "이미 다른 위치 공유 방에 참여 중입니다.";
        if (lower.contains("room_or_password_invalid")) return "방 이름 또는 비밀번호가 맞지 않습니다.";
        if (lower.contains("join_temporarily_blocked")) return "비밀번호 오류가 여러 번 발생해 잠시 후 다시 참여할 수 있습니다.";
        if (lower.contains("password_must_be_4_digits")) return "비밀번호는 숫자 4자리로 입력해 주세요.";
        if (lower.contains("invalid_room_name")) return "방 이름을 2~40자로 입력해 주세요.";
        if (lower.contains("invalid_nickname")) return "닉네임을 1~24자로 입력해 주세요.";
        if (lower.contains("not_in_location_room")) return "현재 참여 중인 위치 공유 방이 없습니다.";
        if (lower.contains("authentication_required")) return "서버 인증이 필요합니다.";
        return "위치 공유 처리 중 오류가 발생했습니다.";
    }

    private static String serverMessage(HttpResult result) {
        try {
            JSONObject json = new JSONObject(result.body);
            String value = json.optString("message", "");
            if (value.isEmpty()) value = json.optString("msg", "");
            if (!value.isEmpty()) return value;
        } catch (Exception ignored) {}
        return result.body.isEmpty() ? "server_error" : result.body;
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
            HttpResult refreshed = authPost("/auth/v1/token?grant_type=refresh_token", body.toString());
            if (refreshed.ok()) {
                AuthSession session = parseAndSaveSession(prefs, refreshed.body);
                if (session != null) return session;
            }
        }

        JSONObject body = new JSONObject();
        body.put("data", new JSONObject());
        HttpResult created = authPost("/auth/v1/signup", body.toString());
        if (!created.ok()) {
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

    private static HttpResult authPost(String path, String body) throws Exception {
        URL url = new URL(SUPABASE_URL + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15_000);
        c.setReadTimeout(20_000);
        c.setDoOutput(true);
        c.setRequestProperty("apikey", PUBLISHABLE_KEY);
        c.setRequestProperty("Authorization", "Bearer " + PUBLISHABLE_KEY);
        c.setRequestProperty("Content-Type", "application/json");
        write(c, body);
        int code = c.getResponseCode();
        String response = read(c);
        c.disconnect();
        return new HttpResult(code, response);
    }

    private static HttpResult jsonPost(String urlString, String body, String accessToken) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15_000);
        c.setReadTimeout(25_000);
        c.setDoOutput(true);
        c.setRequestProperty("apikey", PUBLISHABLE_KEY);
        c.setRequestProperty("Authorization", "Bearer " + accessToken);
        c.setRequestProperty("Content-Type", "application/json");
        write(c, body);
        int code = c.getResponseCode();
        String response = read(c);
        c.disconnect();
        return new HttpResult(code, response);
    }

    private static void write(HttpURLConnection c, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
    }

    private static String read(HttpURLConnection c) {
        try {
            InputStream stream = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
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

    private static final class HttpResult {
        final int code;
        final String body;
        HttpResult(int code, String body) { this.code = code; this.body = body == null ? "" : body; }
        boolean ok() { return code >= 200 && code < 300; }
    }
}
