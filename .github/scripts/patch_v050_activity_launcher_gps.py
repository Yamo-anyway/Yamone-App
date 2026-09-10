from pathlib import Path
import base64
R=Path('.')
def rw(p): return (R/p).read_text()
def ww(p,s):
 p=R/p; p.parent.mkdir(parents=True,exist_ok=True); p.write_text(s)
def rep(t,a,b,n):
 c=t.count(a)
 if c!=1: raise SystemExit(f'{n}: {c}')
 return t.replace(a,b,1)

# launcher
(R/'app/src/main/res/drawable-nodpi/yamone_launcher_day_night.webp').write_bytes(base64.b64decode((R/'.github/assets/yamone_launcher_v050.b64').read_text().strip()))
ww('app/src/main/res/drawable/yamone_launcher_background.xml','''<layer-list xmlns:android="http://schemas.android.com/apk/res/android"><item><bitmap android:src="@drawable/yamone_launcher_day_night" android:gravity="fill"/></item></layer-list>''')
ww('app/src/main/res/drawable/yamone_launcher_foreground_clear.xml','''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108"><path android:fillColor="#00000000" android:pathData="M0,0h108v108h-108z"/></vector>''')
a='''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@drawable/yamone_launcher_background"/><foreground android:drawable="@drawable/yamone_launcher_foreground_clear"/></adaptive-icon>'''
ww('app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml',a); ww('app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml',a)
for name,pathdata in [('pause','M7,5h4v14H7zM13,5h4v14h-4z'),('play','M8,5L19,12L8,19z'),('stop','M7,7h10v10H7z')]:
 ww(f'app/src/main/res/drawable/ic_activity_control_{name}.xml',f'''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#FFFFFFFF" android:pathData="{pathdata}"/></vector>''')

# ExerciseActivity
p='app/src/main/java/com/yamo/snorelab/ExerciseActivity.java'; t=rw(p)
t=rep(t,'        header.addView(gear, new LinearLayout.LayoutParams(dp(44), dp(44)));\n        return header;\n    }\n\n    private void showHome() {','''        header.addView(gear, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return header;
    }

    private LinearLayout liveHeader(String title) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(8), dp(18), dp(9));
        header.setBackgroundColor(BG);
        TextView tv = text(title, 26, TEXT, true);
        tv.setGravity(Gravity.CENTER_VERTICAL); tv.setIncludeFontPadding(false);
        header.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        return header;
    }

    private void showHome() {''','live header')
t=rep(t,'        detailOpen = false;\n        detailDir = null;\n        setBottomNavVisible(!runtime.getBoolean(WalkingRecorderService.KEY_RECORDING, false));','''        detailOpen = false;
        detailDir = null;
        boolean recording = runtime.getBoolean(WalkingRecorderService.KEY_RECORDING, false);
        setBottomNavVisible(!recording);''','recording var')
t=rep(t,'        shell.addView(mainHeader("활동", ""),\n                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));','''        String liveType = runtime.getString(WalkingRecorderService.KEY_ACTIVITY_TYPE, "walking");
        shell.addView(recording ? liveHeader(activityLabel(liveType)) : mainHeader("활동", ""),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));''','live title')
t=rep(t,'        if (runtime.getBoolean(WalkingRecorderService.KEY_RECORDING, false)) {\n            buildLive(page);\n        } else {','''        if (recording) {
            buildLive(page);
        } else {''','record branch')
old='''        LinearLayout privacy = card();
        privacy.addView(text("🔒 위치 기록 원칙", 15, TEXT, true));
        TextView p = text("GPS 경로와 활동 기록은 휴대폰 내부에만 저장됩니다. 지도 배경을 표시할 때만 OpenFreeMap 지도 타일을 인터넷으로 불러오며, 기록한 GPS 경로를 서버에 업로드하지 않습니다.", 12, MUTED, false);
        p.setPadding(0, dp(8), 0, 0);
        privacy.addView(p);
        page.addView(privacy, cardParams());'''
new='''        if (!recording) {
            LinearLayout privacy = card();
            privacy.addView(text("🔒 위치 기록 원칙", 15, TEXT, true));
            TextView p = text("GPS 경로와 활동 기록은 휴대폰 내부에만 저장됩니다. 지도 배경을 표시할 때만 OpenFreeMap 지도 타일을 인터넷으로 불러오며, 기록한 GPS 경로를 서버에 업로드하지 않습니다.", 12, MUTED, false);
            p.setPadding(0, dp(8), 0, 0); privacy.addView(p); page.addView(privacy, cardParams());
        }'''
t=rep(t,old,new,'privacy live hide')
t=rep(t,'        liveDistance = text("0.00 km", 42, TEXT, true);\n        liveDistance.setGravity(Gravity.CENTER);\n        liveDistance.setPadding(0, dp(5), 0, 0);\n        hero.addView(liveDistance, match(dp(64)));','''        liveDistance = text("0.00 km", 38, TEXT, true);
        liveDistance.setGravity(Gravity.CENTER); liveDistance.setPadding(0, dp(3), 0, 0);
        hero.addView(liveDistance, match(dp(56)));''','distance compact')
t=t.replace('new LinearLayout.LayoutParams(0, dp(74), 1f)','new LinearLayout.LayoutParams(0, dp(68), 1f)',3).replace('new LinearLayout.LayoutParams(0, dp(70), 1f)','new LinearLayout.LayoutParams(0, dp(64), 1f)',3)
t=rep(t,'        liveAccuracy = text("GPS 정확도 --", 11, MUTED, false); liveAccuracy.setGravity(Gravity.CENTER); hero.addView(liveAccuracy);','''        liveAccuracy = text("GPS 위치를 찾는 중…", 10, MUTED, false);
        liveAccuracy.setGravity(Gravity.CENTER); liveAccuracy.setPadding(0, dp(4), 0, 0); hero.addView(liveAccuracy);''','gps hint')
t=rep(t,'        LinearLayout.LayoutParams rp = match(dp(210)); rp.topMargin = dp(8); routeCard.addView(liveRoute, rp);','        LinearLayout.LayoutParams rp = match(dp(260)); rp.topMargin = dp(8); routeCard.addView(liveRoute, rp);','map height')
old='''        LinearLayout controls = new LinearLayout(this); controls.setOrientation(LinearLayout.HORIZONTAL);
        boolean paused = runtime.getBoolean(WalkingRecorderService.KEY_PAUSED, false);
        Button pause = ghostButton(paused ? "▶ 계속" : "Ⅱ 일시정지", v -> togglePause());
        Button stop = actionButton("■ 종료", false, v -> stopExercise());
        controls.addView(pause, new LinearLayout.LayoutParams(0, dp(54), 1f));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(54), 1f); sp.leftMargin = dp(10); controls.addView(stop, sp);
        page.addView(controls, cardParams());'''
new='''        LinearLayout controls = new LinearLayout(this); controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL); controls.setPadding(0, dp(2), 0, dp(5));
        boolean paused = runtime.getBoolean(WalkingRecorderService.KEY_PAUSED, false);
        View pause = activityControlButton(paused ? R.drawable.ic_activity_control_play : R.drawable.ic_activity_control_pause, paused ? "계속" : "일시정지", false, v -> togglePause());
        View stop = activityControlButton(R.drawable.ic_activity_control_stop, "종료", true, v -> stopExercise());
        controls.addView(pause, new LinearLayout.LayoutParams(0, dp(58), 1f));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(58), 1f); sp.leftMargin = dp(10); controls.addView(stop, sp);
        LinearLayout.LayoutParams cp = cardParams(); cp.topMargin = dp(2); cp.bottomMargin = dp(20); page.addView(controls, cp);'''
t=rep(t,old,new,'controls')
t=rep(t,'        float altitude = runtime.getFloat(WalkingRecorderService.KEY_ALTITUDE_M, Float.NaN);\n        float accuracy = runtime.getFloat(WalkingRecorderService.KEY_ACCURACY_M, Float.NaN);','''        float altitude = runtime.getFloat(WalkingRecorderService.KEY_ALTITUDE_M, Float.NaN);
        float accuracy = runtime.getFloat(WalkingRecorderService.KEY_ACCURACY_M, Float.NaN);
        long lastFix = runtime.getLong(WalkingRecorderService.KEY_LAST_ACCEPTED_FIX_MS, 0L);''','read lastfix')
t=rep(t,'        liveAltitude.setText(Float.isNaN(altitude) ? "-- m" : String.format(Locale.KOREAN, "%.0f m", altitude));\n        liveAccuracy.setText(Float.isNaN(accuracy) ? "GPS 정확도 확인 중" : String.format(Locale.KOREAN, "GPS 정확도 ±%.0fm", accuracy));','''        liveAltitude.setText(Float.isNaN(altitude) ? "-- m" : String.format(Locale.KOREAN, "%.0f m", altitude));
        float limit = cycling ? 45f : ("running".equals(type) || "walkrun".equals(type) ? 35f : 30f);
        long age = lastFix <= 0 ? Long.MAX_VALUE : Math.max(0L, System.currentTimeMillis() - lastFix);
        if (lastFix <= 0) liveAccuracy.setText("GPS 위치를 찾는 중…");
        else if (age > 8_000L) liveAccuracy.setText("GPS 신호 확인 중 · 기존 경로 유지");
        else if (!Float.isNaN(accuracy) && accuracy > limit) liveAccuracy.setText(String.format(Locale.KOREAN, "GPS 신호 약함 · 정확도 ±%.0fm", accuracy));
        else liveAccuracy.setText(Float.isNaN(accuracy) ? "GPS 정확도 확인 중" : String.format(Locale.KOREAN, "GPS 정확도 ±%.0fm", accuracy));''','gps status')
t=rep(t,'        if (liveRoute != null && now - lastRouteReload > 4000) {','        if (liveRoute != null && now - lastRouteReload > 2000) {','route refresh')
mark='    private Button actionButton(String s, boolean primary, View.OnClickListener click) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setTextColor(Color.WHITE); b.setBackground(round(primary ? PRIMARY : 0xFF33425B, 16, 0, 0)); b.setOnClickListener(click); return b; }\n'
add=mark+'''    private View activityControlButton(int iconRes, String label, boolean danger, View.OnClickListener click) {
        boolean pink = pinkBottomNav(); int fill = pink ? 0xFFFFEEF3 : 0xFFF0FAF6; int border = pink ? 0xFFFFD7E3 : 0xFFD7EFE7;
        int fg = danger ? 0xFFE75B6D : (pink ? 0xFF4B2633 : 0xFF153633);
        LinearLayout b = new LinearLayout(this); b.setOrientation(LinearLayout.HORIZONTAL); b.setGravity(Gravity.CENTER); b.setClickable(true); b.setFocusable(true);
        b.setBackground(round(fill, 16, 1, danger ? 0xFFFFCBD3 : border)); if (Build.VERSION.SDK_INT >= 21) b.setElevation(dp(1.5f));
        ImageView iv = new ImageView(this); iv.setImageResource(iconRes); iv.setColorFilter(fg); b.addView(iv, new LinearLayout.LayoutParams(dp(21), dp(21)));
        TextView tv = text(label, 14, fg, true); tv.setGravity(Gravity.CENTER_VERTICAL); tv.setIncludeFontPadding(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)); lp.leftMargin = dp(8); b.addView(tv, lp); b.setOnClickListener(click); return b;
    }
'''
t=rep(t,mark,add,'control helper'); ww(p,t)

# recorder GPS freshness
p='app/src/main/java/com/yamo/snorelab/WalkingRecorderService.java'; t=rw(p)
t=rep(t,'    public static final String KEY_SPLITS_JSON = "splits_json";','    public static final String KEY_SPLITS_JSON = "splits_json";\n    public static final String KEY_LAST_ACCEPTED_FIX_MS = "last_accepted_fix_ms";','key')
t=rep(t,'    private Location lastAccepted;\n    private long lastAcceptedTime;\n    private float lastAcceptedSpeedMps;','    private Location lastAccepted;\n    private long lastAcceptedTime;\n    private long lastAcceptedFixWallMs;\n    private float lastAcceptedSpeedMps;','field')
t=rep(t,'        lastAccepted = null;\n        lastAcceptedTime = 0;\n        lastWrittenTime = 0;','        lastAccepted = null;\n        lastAcceptedTime = 0;\n        lastAcceptedFixWallMs = 0L;\n        lastWrittenTime = 0;','reset')
t=rep(t,'''        float activityAccuracyLimit = isCycling() ? MAX_ACCEPTABLE_ACCURACY_M
                : (isRunning() || isWalkRun() ? 35f : 30f);
        if (loc.hasAccuracy() && loc.getAccuracy() > activityAccuracyLimit) {
            rejectedGpsPoints++;
            resetMaxSpeedCandidate();
            return;
        }

        long now = loc.getTime() > 0 ? loc.getTime() : System.currentTimeMillis();
        accuracyM = loc.hasAccuracy() ? loc.getAccuracy() : Float.NaN;''','''        float activityAccuracyLimit = isCycling() ? MAX_ACCEPTABLE_ACCURACY_M
                : (isRunning() || isWalkRun() ? 35f : 30f);
        accuracyM = loc.hasAccuracy() ? loc.getAccuracy() : Float.NaN;
        if (loc.hasAccuracy() && loc.getAccuracy() > activityAccuracyLimit) {
            rejectedGpsPoints++; currentSpeedKmh = 0f; resetMaxSpeedCandidate(); persistRuntime(); return;
        }
        long now = loc.getTime() > 0 ? loc.getTime() : System.currentTimeMillis();''','weak fix')
t=rep(t,'        lastAccepted = new Location(loc);\n        lastAcceptedTime = now;\n        lastAcceptedSpeedMps = Math.max(0f, speedMps);','        lastAccepted = new Location(loc);\n        lastAcceptedTime = now;\n        lastAcceptedFixWallMs = System.currentTimeMillis();\n        lastAcceptedSpeedMps = Math.max(0f, speedMps);','accept fix')
t=rep(t,'        lastAccepted = new Location(loc);\n        lastAcceptedTime = now;\n        lastAcceptedSpeedMps = 0f;','        lastAccepted = new Location(loc);\n        lastAcceptedTime = now;\n        lastAcceptedFixWallMs = System.currentTimeMillis();\n        lastAcceptedSpeedMps = 0f;','rebase fix')
t=rep(t,'                .putLong(KEY_PERSISTED_AT_MS, System.currentTimeMillis())\n                .putString(KEY_SPLITS_JSON, WalkingStore.longListToJson(splitsMs).toString())','                .putLong(KEY_PERSISTED_AT_MS, System.currentTimeMillis())\n                .putLong(KEY_LAST_ACCEPTED_FIX_MS, lastAcceptedFixWallMs)\n                .putString(KEY_SPLITS_JSON, WalkingStore.longListToJson(splitsMs).toString())','persist fix')
t=rep(t,'        accuracyM = runtime.getFloat(KEY_ACCURACY_M, Float.NaN);\n        goalDistanceM = runtime.getLong(KEY_GOAL_DISTANCE_M, 0L);','        accuracyM = runtime.getFloat(KEY_ACCURACY_M, Float.NaN);\n        lastAcceptedFixWallMs = runtime.getLong(KEY_LAST_ACCEPTED_FIX_MS, 0L);\n        goalDistanceM = runtime.getLong(KEY_GOAL_DISTANCE_M, 0L);','restore fix')
t=t.replace('m.put("gpsFilter", "local_" + activityType + "_v6_shadow");','m.put("gpsFilter", "local_" + activityType + "_v7_segmented");',1); ww(p,t)

# map segmented + current point
p='app/src/main/java/com/yamo/snorelab/WalkingMapView.java'; t=rw(p)
t=rep(t,'import org.maplibre.android.style.layers.LineLayer;\nimport org.maplibre.android.style.sources.GeoJsonSource;\nimport org.maplibre.geojson.LineString;\nimport org.maplibre.geojson.Point;','import org.maplibre.android.style.layers.CircleLayer;\nimport org.maplibre.android.style.layers.LineLayer;\nimport org.maplibre.android.style.sources.GeoJsonSource;\nimport org.maplibre.geojson.Feature;\nimport org.maplibre.geojson.FeatureCollection;\nimport org.maplibre.geojson.LineString;\nimport org.maplibre.geojson.Point;','imports')
t=rep(t,'import static org.maplibre.android.style.layers.PropertyFactory.lineCap;','import static org.maplibre.android.style.layers.PropertyFactory.circleColor;\nimport static org.maplibre.android.style.layers.PropertyFactory.circleOpacity;\nimport static org.maplibre.android.style.layers.PropertyFactory.circleRadius;\nimport static org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor;\nimport static org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth;\nimport static org.maplibre.android.style.layers.PropertyFactory.lineCap;','circle imports')
t=rep(t,'    private static final String SOURCE_ID = "walking-route-source";\n    private static final String LAYER_ID = "walking-route-layer";\n    private static final int MAX_RENDER_POINTS = 1200;','    private static final String SOURCE_ID = "walking-route-source";\n    private static final String LAYER_ID = "walking-route-layer";\n    private static final String CURRENT_SOURCE_ID = "walking-current-source";\n    private static final String CURRENT_LAYER_ID = "walking-current-layer";\n    private static final long RENDER_GAP_BREAK_MS = 12_000L;\n    private static final int MAX_RENDER_POINTS = 1200;','ids')
t=rep(t,'    private MapLibreMap map;\n    private GeoJsonSource routeSource;\n    private List<WalkingStore.Point> points = new ArrayList<>();','    private MapLibreMap map;\n    private GeoJsonSource routeSource;\n    private GeoJsonSource currentSource;\n    private List<WalkingStore.Point> points = new ArrayList<>();','current source')
t=rep(t,'                style.addLayer(routeLayer);\n                styleReady = true;\n                updateRoute();','''                style.addLayer(routeLayer);
                currentSource = new GeoJsonSource(CURRENT_SOURCE_ID); style.addSource(currentSource);
                CircleLayer currentLayer = new CircleLayer(CURRENT_LAYER_ID, CURRENT_SOURCE_ID).withProperties(circleColor(routeColor), circleRadius(6.5f), circleOpacity(1.0f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2.5f));
                style.addLayer(currentLayer); styleReady = true; updateRoute();''','current layer')
t=rep(t,'    public void setPoints(List<WalkingStore.Point> value) {\n        points = value == null ? new ArrayList<>() : new ArrayList<>(value);\n        updateEmptyState();\n        updateRoute();\n    }','''    public void setPoints(List<WalkingStore.Point> value) {
        if (value == null || value.isEmpty()) { if (points.isEmpty()) updateEmptyState(); return; }
        points = new ArrayList<>(value); updateEmptyState(); updateRoute();
    }''','preserve route')
t=rep(t,'''    private void updateRoute() {
        if (!styleReady || routeSource == null || map == null || points.isEmpty()) return;
        List<WalkingStore.Point> visible = renderPoints();
        ArrayList<Point> geo = new ArrayList<>(visible.size());
        for (WalkingStore.Point p : visible) geo.add(Point.fromLngLat(p.lon, p.lat));
        if (geo.size() >= 2) routeSource.setGeoJson(LineString.fromLngLats(geo));
        else routeSource.setGeoJson(Point.fromLngLat(points.get(0).lon, points.get(0).lat));
        post(this::fitAndConstrainCamera);
    }''','''    private void updateRoute() {
        if (!styleReady || routeSource == null || map == null || points.isEmpty()) return;
        List<WalkingStore.Point> visible = renderPoints(); ArrayList<Feature> lines = new ArrayList<>(); ArrayList<Point> seg = new ArrayList<>(); WalkingStore.Point prev = null;
        for (WalkingStore.Point p : visible) { boolean cut = prev != null && p.timeMs - prev.timeMs > RENDER_GAP_BREAK_MS && p.speedMps <= 0.01f; if (cut) { addSegment(lines, seg); seg = new ArrayList<>(); } seg.add(Point.fromLngLat(p.lon, p.lat)); prev = p; }
        addSegment(lines, seg); routeSource.setGeoJson(FeatureCollection.fromFeatures(lines));
        if (currentSource != null) { WalkingStore.Point last = visible.get(visible.size()-1); currentSource.setGeoJson(Point.fromLngLat(last.lon, last.lat)); }
        post(this::fitAndConstrainCamera);
    }

    private void addSegment(List<Feature> lines, List<Point> seg) { if (seg != null && seg.size() >= 2) lines.add(Feature.fromGeometry(LineString.fromLngLats(seg))); }''','segmented route'); ww(p,t)
print('ok')
