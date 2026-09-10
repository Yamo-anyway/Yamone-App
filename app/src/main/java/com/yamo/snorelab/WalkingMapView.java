package com.yamo.snorelab;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;

import java.util.ArrayList;
import java.util.List;

import static org.maplibre.android.style.layers.PropertyFactory.circleColor;
import static org.maplibre.android.style.layers.PropertyFactory.circleOpacity;
import static org.maplibre.android.style.layers.PropertyFactory.circleRadius;
import static org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor;
import static org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth;
import static org.maplibre.android.style.layers.PropertyFactory.lineCap;
import static org.maplibre.android.style.layers.PropertyFactory.lineColor;
import static org.maplibre.android.style.layers.PropertyFactory.lineJoin;
import static org.maplibre.android.style.layers.PropertyFactory.lineOpacity;
import static org.maplibre.android.style.layers.PropertyFactory.lineWidth;

/** Lightweight route map shared by live and completed activity screens. */
public class WalkingMapView extends FrameLayout {
    private static final String STYLE_URI = "https://tiles.openfreemap.org/styles/liberty";
    private static final String SOURCE_ID = "walking-route-source";
    private static final String LAYER_ID = "walking-route-layer";
    private static final String CURRENT_SOURCE_ID = "walking-current-source";
    private static final String CURRENT_LAYER_ID = "walking-current-layer";
    private static final long RENDER_GAP_BREAK_MS = 12_000L;
    private static final int MAX_RENDER_POINTS = 1200;
    private static final double TARGET_MARGIN_RATIO = 0.06;
    private static final double MIN_TARGET_MARGIN_DEG = 0.00020;

    private final MapView mapView;
    private final TextView status;
    private MapLibreMap map;
    private GeoJsonSource routeSource;
    private GeoJsonSource currentSource;
    private List<WalkingStore.Point> points = new ArrayList<>();
    private boolean started;
    private boolean resumed;
    private boolean destroyed;
    private boolean styleReady;
    private boolean interactive;
    private float touchDownX;
    private float touchDownY;

    public WalkingMapView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        boolean pink = "pink".equals(context.getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "pink"));
        int previewBg = pink ? 0xFFFFEEF3 : 0xFFF0FAF6;
        int previewText = pink ? 0xFF9A7180 : 0xFF718984;
        String routeColor = pink ? "#FF769F" : "#35C6A6";
        setBackgroundColor(previewBg);

        MapLibre.getInstance(context.getApplicationContext());
        mapView = new MapView(context);
        mapView.onCreate(null);
        mapView.setAlpha(0f);
        installScrollFriendlyTouchHandling();
        applyInteractionMode();
        addView(mapView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        status = new TextView(context);
        status.setText("이동 경로 준비 중…");
        status.setTextColor(previewText);
        status.setTextSize(12);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        status.setBackgroundColor(previewBg);
        status.setClickable(false);
        status.setFocusable(false);
        addView(status, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mapView.getMapAsync(value -> {
            map = value;
            applyInteractionMode();
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
                currentSource = new GeoJsonSource(CURRENT_SOURCE_ID); style.addSource(currentSource);
                CircleLayer currentLayer = new CircleLayer(CURRENT_LAYER_ID, CURRENT_SOURCE_ID).withProperties(circleColor(routeColor), circleRadius(6.5f), circleOpacity(1.0f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2.5f));
                style.addLayer(currentLayer); styleReady = true; updateRoute();
            });
        });
    }

    /** Completed-record screens opt in. Live recording maps stay fixed for smooth GPS updates. */
    public void setInteractive(boolean value) {
        interactive = value;
        applyInteractionMode();
    }

    private void applyInteractionMode() {
        mapView.setEnabled(interactive);
        mapView.setClickable(interactive);
        mapView.setFocusable(interactive);
        if (map == null) return;
        try {
            map.getUiSettings().setAllGesturesEnabled(false);
            if (interactive) {
                map.getUiSettings().setScrollGesturesEnabled(true);
                map.getUiSettings().setZoomGesturesEnabled(true);
            }
            map.getUiSettings().setRotateGesturesEnabled(false);
            map.getUiSettings().setTiltGesturesEnabled(false);
            map.getUiSettings().setDoubleTapGesturesEnabled(false);
            map.getUiSettings().setQuickZoomGesturesEnabled(false);
            map.getUiSettings().setAllVelocityAnimationsEnabled(false);
            map.getUiSettings().setCompassEnabled(false);
        } catch (Exception ignored) {}
    }

    private void installScrollFriendlyTouchHandling() {
        mapView.setOnTouchListener((v, event) -> {
            if (!interactive) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                return false;
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                touchDownX = event.getX();
                touchDownY = event.getY();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            } else if (action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (event.getPointerCount() >= 2) {
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                } else {
                    float dx = Math.abs(event.getX() - touchDownX);
                    float dy = Math.abs(event.getY() - touchDownY);
                    boolean mapGesture = dx > dp(6) && dx > dy * 1.15f;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(mapGesture);
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
    }

    public void setPoints(List<WalkingStore.Point> value) {
        if (value == null || value.isEmpty()) { if (points.isEmpty()) updateEmptyState(); return; }
        points = new ArrayList<>(value); updateEmptyState(); updateRoute();
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

    private List<WalkingStore.Point> renderPoints() {
        if (points.size() <= MAX_RENDER_POINTS) return points;
        ArrayList<WalkingStore.Point> reduced = new ArrayList<>(MAX_RENDER_POINTS);
        double step = (points.size() - 1.0) / (MAX_RENDER_POINTS - 1.0);
        int lastIndex = -1;
        for (int i = 0; i < MAX_RENDER_POINTS - 1; i++) {
            int index = Math.min(points.size() - 1, (int) Math.round(i * step));
            if (index != lastIndex) {
                reduced.add(points.get(index));
                lastIndex = index;
            }
        }
        if (lastIndex != points.size() - 1) reduced.add(points.get(points.size() - 1));
        return reduced;
    }

    private void updateRoute() {
        if (!styleReady || routeSource == null || map == null || points.isEmpty()) return;
        List<WalkingStore.Point> visible = renderPoints(); ArrayList<Feature> lines = new ArrayList<>(); ArrayList<Point> seg = new ArrayList<>(); WalkingStore.Point prev = null;
        for (WalkingStore.Point p : visible) { boolean cut = prev != null && p.timeMs - prev.timeMs > RENDER_GAP_BREAK_MS && p.speedMps <= 0.01f; if (cut) { addSegment(lines, seg); seg = new ArrayList<>(); } seg.add(Point.fromLngLat(p.lon, p.lat)); prev = p; }
        addSegment(lines, seg); routeSource.setGeoJson(FeatureCollection.fromFeatures(lines));
        if (currentSource != null) { WalkingStore.Point last = visible.get(visible.size()-1); currentSource.setGeoJson(Point.fromLngLat(last.lon, last.lat)); }
        post(this::fitAndConstrainCamera);
    }

    private void addSegment(List<Feature> lines, List<Point> seg) { if (seg != null && seg.size() >= 2) lines.add(Feature.fromGeometry(LineString.fromLngLats(seg))); }

    private void fitAndConstrainCamera() {
        if (map == null || points.isEmpty()) return;
        try {
            WalkingStore.Point last = points.get(points.size() - 1);
            if (points.size() == 1) {
                LatLng target = new LatLng(last.lat, last.lon);
                map.setMinZoomPreference(16.0);
                map.setLatLngBoundsForCameraTarget(singlePointBounds(last.lat, last.lon));
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 16.0));
            } else {
                double minLat = Double.POSITIVE_INFINITY;
                double maxLat = Double.NEGATIVE_INFINITY;
                double minLon = Double.POSITIVE_INFINITY;
                double maxLon = Double.NEGATIVE_INFINITY;
                LatLngBounds.Builder routeBuilder = new LatLngBounds.Builder();
                for (WalkingStore.Point p : points) {
                    routeBuilder.include(new LatLng(p.lat, p.lon));
                    minLat = Math.min(minLat, p.lat);
                    maxLat = Math.max(maxLat, p.lat);
                    minLon = Math.min(minLon, p.lon);
                    maxLon = Math.max(maxLon, p.lon);
                }
                LatLngBounds routeBounds = routeBuilder.build();
                int pad = dp(26);
                CameraPosition fit = map.getCameraForLatLngBounds(routeBounds, new int[]{pad, pad, pad, pad});
                if (fit != null) map.setMinZoomPreference(fit.zoom);
                map.setLatLngBoundsForCameraTarget(expandedTargetBounds(minLat, maxLat, minLon, maxLon));
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(routeBounds, pad));
            }
            mapView.postDelayed(() -> {
                mapView.setAlpha(1f);
                status.setVisibility(GONE);
            }, 60L);
        } catch (Exception ignored) {
            status.setText("이동 경로를 표시하지 못했어요.");
            status.setVisibility(VISIBLE);
            mapView.setAlpha(0f);
        }
    }

    private LatLngBounds expandedTargetBounds(double minLat, double maxLat, double minLon, double maxLon) {
        double latSpan = Math.max(0.0, maxLat - minLat);
        double lonSpan = Math.max(0.0, maxLon - minLon);
        double latMargin = Math.max(MIN_TARGET_MARGIN_DEG, latSpan * TARGET_MARGIN_RATIO);
        double lonMargin = Math.max(MIN_TARGET_MARGIN_DEG, lonSpan * TARGET_MARGIN_RATIO);
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(new LatLng(Math.max(-85.0, minLat - latMargin), Math.max(-180.0, minLon - lonMargin)));
        builder.include(new LatLng(Math.min(85.0, maxLat + latMargin), Math.min(180.0, maxLon + lonMargin)));
        return builder.build();
    }

    private LatLngBounds singlePointBounds(double lat, double lon) {
        double margin = 0.003;
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(new LatLng(Math.max(-85.0, lat - margin), Math.max(-180.0, lon - margin)));
        builder.include(new LatLng(Math.min(85.0, lat + margin), Math.min(180.0, lon + margin)));
        return builder.build();
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
