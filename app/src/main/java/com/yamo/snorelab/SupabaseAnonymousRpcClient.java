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

/** Shared anonymous Supabase Auth/RPC session so concurrent app features do not race token refresh. */
final class SupabaseAnonymousRpcClient {
    private static final String SUPABASE_URL = "https://kumucxomdviuwuwqcpmv.supabase.co";
    private static final String PUBLISHABLE_KEY = "sb_publishable_YYZp1A9A62KUxs5etSAiYg_0TvaK2hQ";
    private static final String AUTH_PREFS = "yamone_supabase_anonymous_auth_v1";
    private static final Object AUTH_LOCK = new Object();

    private SupabaseAnonymousRpcClient() {}

    static String rpc(Context context, String name, JSONObject args) throws Exception {
        AuthSession session = ensureAnonymousSession(context.getApplicationContext());
        HttpResult result = jsonPost(SUPABASE_URL + "/rest/v1/rpc/" + name,
                args == null ? "{}" : args.toString(), session.accessToken);
        if (!result.ok()) throw new IllegalStateException(serverMessage(result));
        return result.body == null ? "" : result.body.trim();
    }

    private static AuthSession ensureAnonymousSession(Context context) throws Exception {
        synchronized (AUTH_LOCK) {
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
                String lower = created.body == null ? "" : created.body.toLowerCase(java.util.Locale.US);
                if (lower.contains("anonymous") && lower.contains("disabled")) {
                    throw new IllegalStateException("anonymous sign-in disabled");
                }
                throw new IllegalStateException("anonymous sign-in failed");
            }
            AuthSession session = parseAndSaveSession(prefs, created.body);
            if (session == null) throw new IllegalStateException("invalid auth response");
            return session;
        }
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

    private static String serverMessage(HttpResult result) {
        try {
            JSONObject json = new JSONObject(result.body);
            String value = json.optString("message", "");
            if (value.isEmpty()) value = json.optString("msg", "");
            if (!value.isEmpty()) return value;
        } catch (Exception ignored) {}
        return result.body == null || result.body.isEmpty() ? "server_error" : result.body;
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
