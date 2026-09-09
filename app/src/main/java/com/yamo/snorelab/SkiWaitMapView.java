package com.yamo.snorelab;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
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
import java.util.Locale;

/** Resort overview map showing lift lower stations with name + estimated wait. */
@SuppressWarnings("deprecation")
public final class SkiWaitMapView extends FrameLayout {
    private static final String STYLE_URI = "https://tiles.openfreemap.org/styles/liberty";

    private final MapView mapView;
    private final TextView status;
    private MapLibreMap map;
    private JSONArray lifts = new JSONArray();
    private boolean initialCameraDone;
    private boolean started;
    private boolean resumed;
    private boolean destroyed;

    public SkiWaitMapView(Context context) {
        super(context);
        setBackgroundColor(0xFF101B2D);
        MapLibre.getInstance(context.getApplicationContext());
        mapView = new MapView(context);
        mapView.onCreate(null);
        addView(mapView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        status = new TextView(context);
        status.setText("스키장 지도 불러오는 중…");
        status.setTextColor(Color.WHITE);
        status.setTextSize(12);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        status.setBackgroundColor(0x990B1324);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        sp.gravity = Gravity.TOP;
        addView(status, sp);

        mapView.getMapAsync(value -> {
            map = value;
            map.getUiSettings().setAllGesturesEnabled(true);
            map.setStyle(STYLE_URI, style -> renderLifts());
        });
    }

    public void setLifts(JSONArray value) {
        lifts = value == null ? new JSONArray() : value;
        renderLifts();
    }

    private void renderLifts() {
        if (map == null || map.getStyle() == null) return;
        map.clear();
        List<LatLng> points = new ArrayList<>();
        int rendered = 0;
        for (int i = 0; i < lifts.length(); i++) {
            JSONObject lift = lifts.optJSONObject(i);
            if (lift == null || lift.isNull("lower_lat") || lift.isNull("lower_lon")) continue;
            double lat = lift.optDouble("lower_lat", Double.NaN);
            double lon = lift.optDouble("lower_lon", Double.NaN);
            if (!Double.isFinite(lat) || !Double.isFinite(lon)) continue;
            String name = lift.optString("lift_name", "").trim();
            if (name.isEmpty()) name = lift.optString("lift_key", "리프트");
            String wait = waitText(lift);
            String label = name + "  " + wait;
            LatLng pos = new LatLng(lat, lon);
            points.add(pos);
            Icon icon = IconFactory.getInstance(getContext()).fromBitmap(markerBitmap(label, !lift.isNull("estimated_wait_seconds")));
            map.addMarker(new MarkerOptions().position(pos).title(name).snippet(wait).icon(icon));
            rendered++;
        }
        if (rendered == 0) {
            status.setText("표시할 등록 리프트 위치가 없습니다.");
            status.setVisibility(VISIBLE);
        } else {
            status.setVisibility(GONE);
        }
        if (!initialCameraDone && !points.isEmpty()) {
            initialCameraDone = true;
            post(() -> fit(points));
        }
    }

    private void fit(List<LatLng> points) {
        if (map == null || points.isEmpty()) return;
        try {
            if (points.size() == 1) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(points.get(0), 15.5));
                return;
            }
            LatLngBounds.Builder b = new LatLngBounds.Builder();
            for (LatLng p : points) b.include(p);
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), dp(54)), 450);
        } catch (Exception ignored) {}
    }

    private String waitText(JSONObject lift) {
        if (lift == null || lift.isNull("estimated_wait_seconds")) return "정보 부족";
        int seconds = Math.max(0, lift.optInt("estimated_wait_seconds", 0));
        int minutes = Math.max(0, Math.round(seconds / 60f));
        return String.format(Locale.KOREAN, "약 %d분", minutes);
    }

    private Bitmap markerBitmap(String label, boolean hasEstimate) {
        float density = getResources().getDisplayMetrics().density;
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(11.5f * density);
        float textWidth = textPaint.measureText(label);
        int width = Math.max(dp(105), Math.round(textWidth + dp(22)));
        int height = dp(34);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(hasEstimate ? 0xEE16243B : 0xDD3B4250);
        canvas.drawRoundRect(new RectF(0, 0, width, height), dp(12), dp(12), bg);
        textPaint.setColor(hasEstimate ? Color.WHITE : 0xFFD0D5E0);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float y = height / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, dp(11), y, textPaint);
        return bitmap;
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

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
