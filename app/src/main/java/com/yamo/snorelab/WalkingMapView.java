package com.yamo.snorelab;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;

import java.util.ArrayList;
import java.util.List;

import static org.maplibre.android.style.layers.PropertyFactory.lineCap;
import static org.maplibre.android.style.layers.PropertyFactory.lineColor;
import static org.maplibre.android.style.layers.PropertyFactory.lineJoin;
import static org.maplibre.android.style.layers.PropertyFactory.lineOpacity;
import static org.maplibre.android.style.layers.PropertyFactory.lineWidth;

/**
 * Fixed route preview used by walking/running/cycling and hiking summaries.
 * The map is intentionally non-interactive: it only shows the recorded route bounds.
 */
public class WalkingMapView extends FrameLayout {
    private static final String STYLE_URI = "https://tiles.openfreemap.org/styles/liberty";
    private static final String SOURCE_ID = "walking-route-source";
    private static final String LAYER_ID = "walking-route-layer";

    private final MapView mapView;
    private final TextView status;
    private MapLibreMap map;
    private GeoJsonSource routeSource;
    private List<WalkingStore.Point> points = new ArrayList<>();
    private boolean started;
    private boolean resumed;
    private boolean destroyed;
    private boolean styleReady;

    public WalkingMapView(Context context) {
        super(context);
        boolean pink = "pink".equals(context.getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "mint"));
        int previewBg = pink ? 0xFFFFEEF3 : 0xFFF0FAF6;
        int previewText = pink ? 0xFF9A7180 : 0xFF718984;
        String routeColor = pink ? "#FF769F" : "#35C6A6";
        setBackgroundColor(previewBg);

        MapLibre.getInstance(context.getApplicationContext());
        mapView = new MapView(context);
        mapView.onCreate(null);
        mapView.setAlpha(0f);
        mapView.setClickable(false);
        mapView.setFocusable(false);
        // Gestures are disabled, but touches must bubble to the parent ScrollView so
        // the activity detail can always scroll even when the gesture begins on the map.
        mapView.setOnTouchListener((v, event) -> false);
        addView(mapView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        status = new TextView(context);
        status.setText("이동 경로 준비 중…");
        status.setTextColor(previewText);
        status.setTextSize(12);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        status.setBackgroundColor(previewBg);
        addView(status, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mapView.getMapAsync(value -> {
            map = value;
            try {
                map.getUiSettings().setAllGesturesEnabled(false);
                map.getUiSettings().setCompassEnabled(false);
            } catch (Exception ignored) {}
            map.setStyle(STYLE_URI, style -> {
                routeSource = new GeoJsonSource(SOURCE_ID);
                style.addSource(routeSource);
                LineLayer routeLayer = new LineLayer(LAYER_ID, SOURCE_ID).withProperties(
                        lineColor(routeColor),
                        lineWidth(5.0f),
                        lineOpacity(0.96f),
                        lineCap("round"),
                        lineJoin("round")
                );
                style.addLayer(routeLayer);
                styleReady = true;
                updateRoute();
            });
        });
    }

    public void setPoints(List<WalkingStore.Point> value) {
        points = value == null ? new ArrayList<>() : new ArrayList<>(value);
        updateEmptyState();
        updateRoute();
    }

    public void setAnalysisSamples(List<ActivityRouteAnalysis.Sample> samples) {
        ArrayList<WalkingStore.Point> converted = new ArrayList<>();
        if (samples != null) {
            for (ActivityRouteAnalysis.Sample s : samples) {
                converted.add(new WalkingStore.Point(s.timeMs, s.lat, s.lon, s.accuracyM,
                        s.altitudeValid ? s.altitudeM : 0.0,
                        s.speedValid ? s.speedKmh / 3.6f : 0f));
            }
        }
        points = converted;
        updateEmptyState();
        updateRoute();
    }

    private void updateEmptyState() {
        if (points.isEmpty()) {
            status.setText("표시할 이동 경로가 없어요.");
            status.setVisibility(VISIBLE);
            mapView.setAlpha(0f);
        }
    }

    private void updateRoute() {
        if (!styleReady || routeSource == null || map == null || points.isEmpty()) return;
        ArrayList<Point> geo = new ArrayList<>();
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (WalkingStore.Point p : points) {
            geo.add(Point.fromLngLat(p.lon, p.lat));
            bounds.include(new LatLng(p.lat, p.lon));
        }
        if (geo.size() >= 2) routeSource.setGeoJson(LineString.fromLngLats(geo));
        else routeSource.setGeoJson(Point.fromLngLat(points.get(0).lon, points.get(0).lat));

        post(() -> {
            if (map == null || points.isEmpty()) return;
            try {
                WalkingStore.Point last = points.get(points.size() - 1);
                if (points.size() == 1) {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(last.lat, last.lon), 16.0));
                } else {
                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), dp(26)));
                }
                mapView.postDelayed(() -> {
                    mapView.setAlpha(1f);
                    status.setVisibility(GONE);
                }, 80L);
            } catch (Exception ignored) {
                status.setText("이동 경로를 표시하지 못했어요.");
                status.setVisibility(VISIBLE);
                mapView.setAlpha(0f);
            }
        });
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

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
