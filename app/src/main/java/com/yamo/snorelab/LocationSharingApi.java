package com.yamo.snorelab;

import android.content.Context;

import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Supabase RPC client for ephemeral location sharing using the shared anonymous session. */
public final class LocationSharingApi {
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
        run(() -> {
            JSONObject args = new JSONObject();
            args.put("p_room_name", roomName);
            String body = SupabaseAnonymousRpcClient.rpc(context, "location_room_name_available", args);
            callback.onSuccess("true".equalsIgnoreCase(body.trim()));
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
            args.put("p_accuracy_m", accuracy == null ? JSONObject.NULL : accuracy.doubleValue());
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
        run(() -> {
            String body = SupabaseAnonymousRpcClient.rpc(context, name, args);
            JSONObject data = body.isEmpty() ? new JSONObject() : new JSONObject(body);
            callback.onSuccess(data);
        }, callback::onFailure);
    }

    private interface ThrowingRunnable { void run() throws Exception; }
    private interface Failure { void onFailure(String message); }

    private static void run(ThrowingRunnable task, Failure failure) {
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
}
