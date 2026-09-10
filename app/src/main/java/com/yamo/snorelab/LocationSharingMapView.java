package com.yamo.snorelab;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.Icon;
import org.maplibre.android.annotations.IconFactory;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;

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
    private MapLibreMap map;
    private JSONArray members = new JSONArray();
    private LatLng selfLatLng;
    private boolean initialCameraDone;
    private boolean started;
    private boolean resumed;
    private boolean destroyed;

    public LocationSharingMapView(Context context) {
        super(context);
        setBackgroundColor(0xFFFFF7FA);

        MapLibre.getInstance(context.getApplicationContext());
        mapView = new MapView(context);
        mapView.onCreate(null);
        addView(mapView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        status = new TextView(context);
        status.setText("지도 불러오는 중…");
        status.setTextColor(0xFF9A7180);
        status.setTextSize(12);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        status.setBackgroundColor(0xEEFFF7FA);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        sp.gravity = Gravity.TOP;
        addView(status, sp);

        myLocationButton = new TextView(context);
        myLocationButton.setText("내 위치");
        myLocationButton.setTextColor(0xFFE94778);
        myLocationButton.setTextSize(12);
        myLocationButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        myLocationButton.setGravity(Gravity.CENTER);
        myLocationButton.setPadding(dp(10), 0, dp(10), 0);
        myLocationButton.setBackground(round(0xF8FFFFFF, 16, 1, 0xFFFFD7E3));
        myLocationButton.setOnClickListener(v -> moveToSelf());
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(92), dp(40));
        bp.gravity = Gravity.END | Gravity.BOTTOM;
        bp.setMargins(0, 0, dp(12), dp(12));
        addView(myLocationButton, bp);

        mapView.getMapAsync(value -> {
            map = value;
            map.getUiSettings().setAllGesturesEnabled(true);
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
            LatLng point = new LatLng(lat, lon);
            positions.add(point);
            if (self) selfLatLng = point;

            String markerText = self ? "나 · " + nickname : nickname;

            Icon icon = IconFactory.getInstance(getContext()).fromBitmap(markerBitmap(markerText, self, state));
            String snippet = stateLabel(state);
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

    public void moveToSelf() {
        if (map == null) return;
        if (selfLatLng == null) {
            status.setText("아직 내 위치를 받지 못했어요.");
            status.setVisibility(VISIBLE);
            postDelayed(() -> {
                if (map != null && selfLatLng != null) status.setVisibility(GONE);
            }, 1800);
            return;
        }
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(selfLatLng, 16.0), 420);
    }

    private Bitmap markerBitmap(String label, boolean self, String state) {
        float density = getResources().getDisplayMetrics().density;
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(12f * density);
        float textWidth = textPaint.measureText(label);
        int width = Math.max(dp(72), Math.round(textWidth + dp(22)));
        int height = dp(34);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        int background;
        int foreground;
        if (self) {
            background = 0xF2E94778;
            foreground = Color.WHITE;
        } else if ("disconnected".equals(state) || "location_stale".equals(state)) {
            background = 0xF8FFF0F3;
            foreground = 0xFFE75B6D;
        } else if ("waiting".equals(state)) {
            background = 0xF8FFF7FA;
            foreground = 0xFF9A7180;
        } else {
            background = 0xF8FFFFFF;
            foreground = 0xFF4B2633;
        }

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(background);
        RectF rect = new RectF(0, 0, width, height);
        canvas.drawRoundRect(rect, dp(14), dp(14), bg);
        if (!self) {
            Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(Math.max(1f, density));
            border.setColor(0xFFFFD7E3);
            canvas.drawRoundRect(rect, dp(14), dp(14), border);
        }

        textPaint.setColor(foreground);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float y = height / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, dp(11), y, textPaint);
        return bitmap;
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
