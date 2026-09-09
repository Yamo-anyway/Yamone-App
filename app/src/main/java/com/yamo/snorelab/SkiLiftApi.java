package com.yamo.snorelab;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** RPC client for ski resort/lift statistics. Full ski routes are never sent. */
public final class SkiLiftApi {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private SkiLiftApi() {}

    public interface JsonCallback {
        void onSuccess(JSONObject data);
        void onFailure(String message);
    }

    public static void detectResort(Context context, double lat, double lon, JsonCallback callback) {
        JSONObject args = new JSONObject();
        try {
            args.put("p_lat", lat);
            args.put("p_lon", lon);
        } catch (Exception e) {
            callback.onFailure("현재 위치를 준비하지 못했습니다.");
            return;
        }
        rpcAsync(context, "ski_detect_resort", args, callback);
    }

    public static void submitHistorical(Context context, List<SkiLiftStore.PendingObservation> observations,
                                        JsonCallback callback) {
        JSONArray items = new JSONArray();
        if (observations != null) {
            int count = Math.min(100, observations.size());
            for (int i = 0; i < count; i++) {
                JSONObject src = observations.get(i).observation;
                try { items.put(new JSONObject(src.toString())); }
                catch (Exception ignored) {}
            }
        }
        JSONObject args = new JSONObject();
        try { args.put("p_items", items); }
        catch (Exception e) { callback.onFailure("리프트 기록을 준비하지 못했습니다."); return; }
        rpcAsync(context, "ski_submit_historical", args, callback);
    }

    public static void submitRealtime(Context context, String resortKey, JSONObject observation,
                                      JsonCallback callback) {
        JSONObject args = new JSONObject();
        try {
            args.put("p_resort_key", resortKey == null ? "" : resortKey);
            args.put("p_item", observation == null ? new JSONObject() : new JSONObject(observation.toString()));
        } catch (Exception e) {
            callback.onFailure("실시간 리프트 정보를 준비하지 못했습니다.");
            return;
        }
        rpcAsync(context, "ski_submit_realtime", args, callback);
    }

    public static void resortSnapshot(Context context, String resortKey, JsonCallback callback) {
        JSONObject args = new JSONObject();
        try { args.put("p_resort_key", resortKey == null ? "" : resortKey); }
        catch (Exception e) { callback.onFailure("스키장 정보를 준비하지 못했습니다."); return; }
        rpcAsync(context, "ski_resort_snapshot", args, callback);
    }

    public static void realtimeSnapshot(Context context, String resortKey, JsonCallback callback) {
        JSONObject args = new JSONObject();
        try { args.put("p_resort_key", resortKey == null ? "" : resortKey); }
        catch (Exception e) { callback.onFailure("실시간 정보를 준비하지 못했습니다."); return; }
        rpcAsync(context, "ski_realtime_snapshot", args, callback);
    }

    public static void submitNameSuggestion(Context context, String resortKey, String liftId,
                                            String suggestionType, String proposedName, String currentName,
                                            Double lowerLat, Double lowerLon, Double upperLat, Double upperLon,
                                            JsonCallback callback) {
        JSONObject args = new JSONObject();
        try {
            args.put("p_resort_key", resortKey == null ? "" : resortKey);
            args.put("p_lift_id", liftId == null || liftId.trim().isEmpty() ? JSONObject.NULL : liftId.trim());
            args.put("p_suggestion_type", suggestionType == null ? "name" : suggestionType);
            args.put("p_proposed_name", proposedName == null ? "" : proposedName.trim());
            args.put("p_current_name", currentName == null ? "" : currentName.trim());
            args.put("p_lower_lat", lowerLat == null ? JSONObject.NULL : lowerLat);
            args.put("p_lower_lon", lowerLon == null ? JSONObject.NULL : lowerLon);
            args.put("p_upper_lat", upperLat == null ? JSONObject.NULL : upperLat);
            args.put("p_upper_lon", upperLon == null ? JSONObject.NULL : upperLon);
        } catch (Exception e) {
            callback.onFailure("리프트 이름 제안을 준비하지 못했습니다.");
            return;
        }
        rpcAsync(context, "ski_submit_name_suggestion", args, callback);
    }

    public static List<String> acceptedIds(JSONObject response) {
        List<String> ids = new ArrayList<>();
        if (response == null) return ids;
        JSONArray array = response.optJSONArray("accepted_ids");
        if (array == null) return ids;
        for (int i = 0; i < array.length(); i++) {
            String id = array.optString(i, "").trim();
            if (!id.isEmpty()) ids.add(id);
        }
        return ids;
    }

    private static void rpcAsync(Context context, String name, JSONObject args, JsonCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                String body = SupabaseAnonymousRpcClient.rpc(context, name, args);
                JSONObject data = body.isEmpty() ? new JSONObject() : new JSONObject(body);
                callback.onSuccess(data);
            } catch (Exception e) {
                callback.onFailure(friendly(e));
            }
        });
    }

    private static String friendly(Throwable error) {
        String raw = "";
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isEmpty()) { raw = current.getMessage(); break; }
            current = current.getCause();
        }
        String lower = raw.toLowerCase(Locale.US);
        if (lower.contains("anonymous sign-in disabled")) return "서버의 익명 로그인 설정이 꺼져 있습니다.";
        if (lower.contains("authentication_required")) return "서버 인증이 필요합니다.";
        if (lower.contains("invalid_lift_name")) return "리프트 이름을 1~80자로 입력해 주세요.";
        if (lower.contains("lift_not_found")) return "등록된 리프트를 찾지 못했습니다.";
        if (lower.contains("lift_coordinates_required")) return "리프트 위치 정보가 필요합니다.";
        if (lower.contains("too_many_items")) return "한 번에 제공할 수 있는 기록 수를 초과했습니다.";
        return "리프트 정보 서버에 연결하지 못했습니다.";
    }
}
