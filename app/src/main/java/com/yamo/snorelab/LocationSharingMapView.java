package com.yamo.snorelab;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.Icon;
import org.maplibre.android.annotations.IconFactory;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Ephemeral room map. Only the latest member positions supplied by the room
 * snapshot are rendered; no route/history is kept by this view.
 */
@SuppressWarnings("deprecation")
public final class LocationSharingMapView extends FrameLayout {
    private static final String STYLE_URI = "https://tiles.openfreemap.org/styles/liberty";

    private final MapView mapView;
    private final TextView status;
    private final TextView myLocationButton;
    private final TextView allLocationsButton;
    private MapLibreMap map;
    private JSONArray members = new JSONArray();
    private LatLng selfLatLng;
    private boolean initialCameraDone;
    private boolean started;
    private boolean resumed;
    private boolean destroyed;

    public LocationSharingMapView(Context context) {
        super(context);
        setBackgroundColor(pink() ? 0xFFFFF7FA : 0xFFF7FFFB);

        MapLibre.getInstance(context.getApplicationContext());
        mapView = new MapView(context);
        mapView.onCreate(null);
        addView(mapView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        status = new TextView(context);
        status.setText("지도 불러오는 중…");
        status.setTextColor(pink() ? 0xFF9A7180 : 0xFF718984);
        status.setTextSize(12);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        status.setBackgroundColor(pink() ? 0xEEFFF7FA : 0xEEF7FFFB);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        sp.gravity = Gravity.TOP;
        addView(status, sp);

        myLocationButton = new TextView(context);
        myLocationButton.setText("내 위치");
        myLocationButton.setTextColor(pink() ? 0xFFE94778 : 0xFF159A7A);
        myLocationButton.setTextSize(12);
        myLocationButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        myLocationButton.setGravity(Gravity.CENTER);
        myLocationButton.setPadding(dp(10), 0, dp(10), 0);
        myLocationButton.setBackground(round(
                pink() ? 0xF8FFF7FA : 0xF8F7FFFB,
                16, 1, pink() ? 0xFFFFD7E3 : 0xFFD7EFE7));
        myLocationButton.setOnClickListener(v -> moveToSelf());
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(92), dp(40));
        bp.gravity = Gravity.END | Gravity.BOTTOM;
        bp.setMargins(0, 0, dp(12), dp(12));
        addView(myLocationButton, bp);

        allLocationsButton = new TextView(context);
        allLocationsButton.setText("전체보기");
        allLocationsButton.setTextColor(pink() ? 0xFFE94778 : 0xFF159A7A);
        allLocationsButton.setTextSize(12);
        allLocationsButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        allLocationsButton.setGravity(Gravity.CENTER);
        allLocationsButton.setPadding(dp(10), 0, dp(10), 0);
        allLocationsButton.setBackground(round(
                pink() ? 0xF8FFF7FA : 0xF8F7FFFB,
                16, 1, pink() ? 0xFFFFD7E3 : 0xFFD7EFE7));
        allLocationsButton.setOnClickListener(v -> moveToAll());
        FrameLayout.LayoutParams allParams = new FrameLayout.LayoutParams(dp(92), dp(40));
        allParams.gravity = Gravity.END | Gravity.BOTTOM;
        allParams.setMargins(0, 0, dp(112), dp(12));
        addView(allLocationsButton, allParams);

        mapView.getMapAsync(value -> {
            map = value;
            map.getUiSettings().setAllGesturesEnabled(true);
            map.setInfoWindowAdapter(marker -> buildInfoWindow(marker));
            map.setStyle(STYLE_URI, style -> renderMembers());
        });
    }

    public void setMembers(JSONArray value) {
        members = value == null ? new JSONArray() : value;
        renderMembers();
    }

    private void renderMembers() {
        if (map == null || map.getStyle() == null) return;
        map.clear();
        selfLatLng = null;

        List<LatLng> positions = new ArrayList<>();
        int rendered = 0;
        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.optJSONObject(i);
            if (member == null || member.isNull("last_lat") || member.isNull("last_lon")) continue;

            double lat = member.optDouble("last_lat", Double.NaN);
            double lon = member.optDouble("last_lon", Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) continue;

            boolean self = member.optBoolean("is_self", false);
            String nickname = member.optString("nickname", self ? "나" : "사용자");
            String state = member.optString("connection_state", "waiting");
            String userStatus = member.optString("user_status", "normal");
            LatLng point = new LatLng(lat, lon);
            positions.add(point);
            if (self) selfLatLng = point;

            String markerText = nickname + (self ? " (나)" : "");
            Icon icon = IconFactory.getInstance(getContext())
                    .fromBitmap(markerBitmap(markerText, self, state, userStatus));
            String snippet = userStatus + "\u001F"
                    + state + "\u001F"
                    + member.optString("last_location_at", "") + "\u001F"
                    + (self ? "1" : "0");
            map.addMarker(new MarkerOptions()
                    .position(point)
                    .title(nickname)
                    .snippet(snippet)
                    .icon(icon));
            rendered++;
        }

        if (rendered == 0) {
            status.setText("참여자의 첫 위치를 기다리는 중…");
            status.setVisibility(VISIBLE);
        } else {
            status.setVisibility(GONE);
        }

        if (!initialCameraDone && !positions.isEmpty()) {
            initialCameraDone = true;
            post(() -> fitInitialCamera(positions));
        }
    }

    private void fitInitialCamera(List<LatLng> positions) {
        if (map == null || positions.isEmpty()) return;
        try {
            if (positions.size() == 1) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(positions.get(0), 16.0));
                return;
            }
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            for (LatLng point : positions) builder.include(point);
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), dp(46)), 450);
        } catch (Exception ignored) {}
    }

    public void moveToAll() {
        if (map == null) return;
        List<LatLng> positions = new ArrayList<>();
        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.optJSONObject(i);
            if (member == null || member.isNull("last_lat") || member.isNull("last_lon")) continue;
            double lat = member.optDouble("last_lat", Double.NaN);
            double lon = member.optDouble("last_lon", Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
            positions.add(new LatLng(lat, lon));
        }
        if (positions.isEmpty()) {
            showTransientStatus("아직 표시할 참여자 위치가 없어요.");
            return;
        }
        fitInitialCamera(positions);
    }

    private View buildInfoWindow(Marker marker) {
        String raw = marker == null ? "" : marker.getSnippet();
        String[] parts = raw == null ? new String[0] : raw.split("\\u001F", -1);
        String userStatus = parts.length > 0 && !parts[0].isEmpty() ? parts[0] : "normal";
        String connectionState = parts.length > 1 ? parts[1] : "waiting";
        String lastLocationAt = parts.length > 2 ? parts[2] : "";
        boolean self = parts.length > 3 && "1".equals(parts[3]);

        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(11), dp(14), dp(11));
        panel.setMinimumWidth(dp(164));
        panel.setBackground(round(
                pink() ? 0xFFFFFBFC : 0xFFFBFFFD,
                18, 1, pink() ? 0xFFFFC7D8 : 0xFFC9E9DF));

        TextView name = new TextView(getContext());
        name.setText((marker == null ? "사용자" : marker.getTitle()) + (self ? "  (나)" : ""));
        name.setTextColor(pink() ? 0xFF4B2633 : 0xFF153633);
        name.setTextSize(14);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setIncludeFontPadding(false);
        panel.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));

        LinearLayout statusRow = new LinearLayout(getContext());
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(getContext());
        GradientDrawable dotDrawable = new GradientDrawable();
        dotDrawable.setShape(GradientDrawable.OVAL);
        dotDrawable.setColor(LocationStatusPalette.color(userStatus));
        dot.setBackground(dotDrawable);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotParams.rightMargin = dp(7);
        statusRow.addView(dot, dotParams);

        TextView statusText = new TextView(getContext());
        statusText.setText(LocationStatusPalette.label(userStatus));
        statusText.setTextColor(LocationStatusPalette.color(userStatus));
        statusText.setTextSize(11);
        statusText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusText.setIncludeFontPadding(false);
        statusRow.addView(statusText, new LinearLayout.LayoutParams(
                0, dp(24), 1f));
        panel.addView(statusRow);

        String age = ageText(lastLocationAt);
        String detail = age.isEmpty() ? stateLabel(connectionState)
                : age + " · " + stateLabel(connectionState);
        TextView detailText = new TextView(getContext());
        detailText.setText(detail);
        detailText.setTextColor(pink() ? 0xFF9A7180 : 0xFF718984);
        detailText.setTextSize(10);
        detailText.setIncludeFontPadding(false);
        panel.addView(detailText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));
        return panel;
    }

    private String ageText(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            long seconds = Math.max(0L,
                    (System.currentTimeMillis() - Instant.parse(iso).toEpochMilli()) / 1000L);
            if (seconds < 60L) return "방금";
            long minutes = seconds / 60L;
            if (minutes < 60L) return minutes + "분 전";
            return (minutes / 60L) + "시간 전";
        } catch (DateTimeParseException ignored) {
            return "";
        }
    }

    public void moveToSelf() {
        if (map == null) return;
        if (selfLatLng == null) {
            showTransientStatus("아직 내 위치를 받지 못했어요.");
            return;
        }
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(selfLatLng, 16.0), 420);
    }

    public boolean moveToMember(String memberId, String nickname) {
        if (map == null || memberId == null || memberId.isEmpty()) return false;
        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.optJSONObject(i);
            if (member == null || !memberId.equals(member.optString("member_id", ""))) continue;
            if (member.isNull("last_lat") || member.isNull("last_lon")) {
                showTransientStatus((nickname == null || nickname.isEmpty() ? "선택한 사용자" : nickname)
                        + "님의 위치를 아직 받지 못했어요.");
                return false;
            }
            double lat = member.optDouble("last_lat", Double.NaN);
            double lon = member.optDouble("last_lon", Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) return false;
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lon), 16.0), 420);
            return true;
        }
        return false;
    }

    private void showTransientStatus(String message) {
        status.setText(message);
        status.setVisibility(VISIBLE);
        postDelayed(() -> {
            if (map != null) status.setVisibility(GONE);
        }, 1800);
    }

    /** Marker is always nickname text with the same status-color dot used in the list/status selector. */
    private Bitmap markerBitmap(String label, boolean self, String state, String userStatus) {
        float density = getResources().getDisplayMetrics().density;
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, self ? Typeface.BOLD : Typeface.NORMAL));
        textPaint.setTextSize(12f * density);
        int foreground = pink() ? 0xFF4B2633 : 0xFF153633;
        if ("disconnected".equals(state) || "location_stale".equals(state)) {
            foreground = pink() ? 0xFF9A7180 : 0xFF718984;
        }
        textPaint.setColor(foreground);

        int dotColor = LocationStatusPalette.color(userStatus);
        int dotDiameter = dp(11);
        int textStart = dp(29);
        float textWidth = textPaint.measureText(label);
        int width = Math.max(dp(78), Math.round(textWidth + textStart + dp(10)));
        int height = dp(34);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(pink() ? 0xF9FFF7FA : 0xF9F7FFFB);
        RectF rect = new RectF(0, 0, width, height);
        canvas.drawRoundRect(rect, dp(14), dp(14), bg);

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(self ? 1.8f : 1f));
        border.setColor(self ? dotColor : (pink() ? 0xFFFFD7E3 : 0xFFD7EFE7));
        canvas.drawRoundRect(rect, dp(14), dp(14), border);

        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(dotColor);
        float dotCx = dp(11) + dotDiameter / 2f;
        float dotCy = height / 2f;
        canvas.drawCircle(dotCx, dotCy, dotDiameter / 2f, dot);

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float y = height / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, textStart, y, textPaint);
        return bitmap;
    }

    private String statusSuffix(String status) {
        if ("normal".equals(status)) return "";
        return " · " + LocationStatusPalette.label(status);
    }

    private String stateLabel(String state) {
        if ("connected".equals(state)) return "연결됨";
        if ("disconnected".equals(state)) return "연결 끊김 · 마지막 위치 표시";
        if ("location_stale".equals(state)) return "위치 갱신 끊김 · 마지막 위치 표시";
        return "첫 위치 기다리는 중";
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (destroyed) return;
        try { if (!started) { mapView.onStart(); started = true; } } catch (Exception ignored) {}
        try { if (!resumed) { mapView.onResume(); resumed = true; } } catch (Exception ignored) {}
    }

    @Override protected void onDetachedFromWindow() {
        if (!destroyed) {
            try { if (resumed) mapView.onPause(); } catch (Exception ignored) {}
            try { if (started) mapView.onStop(); } catch (Exception ignored) {}
            try { mapView.onDestroy(); } catch (Exception ignored) {}
            resumed = false;
            started = false;
            destroyed = true;
        }
        super.onDetachedFromWindow();
    }

    public void onLowMemory() {
        if (!destroyed) try { mapView.onLowMemory(); } catch (Exception ignored) {}
    }

    private boolean pink() {
        return "pink".equals(getContext().getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "pink"));
    }

    private GradientDrawable round(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
