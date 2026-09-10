package com.yamo.snorelab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Local activity profile chart with fixed full-range Y axis and zoomable/pannable time axis. */
public final class ActivityProfileChartView extends View {
    public static final int MODE_ALTITUDE = 1;
    public static final int MODE_SPEED = 2;

    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private final int touchSlop;

    private List<ActivityRouteAnalysis.Sample> samples = Collections.emptyList();
    private int mode = MODE_ALTITUDE;

    private long fullMinTime;
    private long fullMaxTime;
    private float fullMinValue;
    private float fullMaxValue;
    private boolean hasRange;

    // Time-axis viewport. zoom=1 means the full activity. viewportStart is a fraction of full duration.
    private float zoom = 1f;
    private float viewportStart = 0f;
    private static final float MAX_ZOOM = 12f;

    private float downX;
    private float downY;
    private float lastX;
    private boolean horizontalDrag;

    public ActivityProfileChartView(Context context) {
        super(context);
        boolean pink = "pink".equals(context.getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "mint"));
        grid.setColor(pink ? 0x22E94778 : 0x22159A7A);
        grid.setStrokeWidth(dp(1));
        axis.setColor(pink ? 0x449A7180 : 0x44718984);
        axis.setStrokeWidth(dp(1));
        line.setColor(pink ? 0xFFE94778 : 0xFF159A7A);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(2.5f));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        text.setColor(pink ? 0xFF9A7180 : 0xFF718984);
        text.setTextSize(dp(10));
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }

            @Override public boolean onScale(ScaleGestureDetector detector) {
                if (!hasRange || getWidth() <= 0) return false;
                float oldZoom = zoom;
                float newZoom = clamp(oldZoom * detector.getScaleFactor(), 1f, MAX_ZOOM);
                if (Math.abs(newZoom - oldZoom) < 0.001f) return true;

                float plotLeft = plotLeft();
                float plotRight = plotRight();
                float plotWidth = Math.max(1f, plotRight - plotLeft);
                float focus = clamp((detector.getFocusX() - plotLeft) / plotWidth, 0f, 1f);

                float oldVisible = 1f / oldZoom;
                float newVisible = 1f / newZoom;
                float focusFull = viewportStart + focus * oldVisible;
                zoom = newZoom;
                viewportStart = clamp(focusFull - focus * newVisible, 0f, 1f - newVisible);
                invalidate();
                return true;
            }

            @Override public void onScaleEnd(ScaleGestureDetector detector) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            }
        });
        setClickable(true);
    }

    public void setData(ActivityRouteAnalysis.Result result, int mode) {
        this.samples = result == null ? Collections.emptyList() : result.samples;
        this.mode = mode;
        zoom = 1f;
        viewportStart = 0f;
        calculateFullRange();
        invalidate();
    }

    private void calculateFullRange() {
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;
        float minValue = Float.POSITIVE_INFINITY;
        float maxValue = Float.NEGATIVE_INFINITY;
        int count = 0;
        for (ActivityRouteAnalysis.Sample s : samples) {
            if (!valid(s)) continue;
            float v = value(s);
            minTime = Math.min(minTime, s.timeMs);
            maxTime = Math.max(maxTime, s.timeMs);
            minValue = Math.min(minValue, v);
            maxValue = Math.max(maxValue, v);
            count++;
        }
        hasRange = count >= 2 && maxTime > minTime;
        if (!hasRange) return;
        if (maxValue - minValue < 0.5f) {
            maxValue += 0.25f;
            minValue -= 0.25f;
        }
        fullMinTime = minTime;
        fullMaxTime = maxTime;
        fullMinValue = minValue;
        fullMaxValue = maxValue;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = plotLeft();
        float right = plotRight();
        float top = dp(12);
        float bottom = getHeight() - dp(31);
        if (right <= left || bottom <= top) return;

        // Grid and Y labels always use the complete activity's value range, even while zoomed.
        for (int i = 0; i <= 4; i++) {
            float ratio = i / 4f;
            float y = top + (bottom - top) * ratio;
            canvas.drawLine(left, y, right, y, grid);
            if (hasRange) {
                float v = fullMaxValue - (fullMaxValue - fullMinValue) * ratio;
                text.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(formatValue(v), left - dp(7), y + dp(3.5f), text);
            }
        }
        canvas.drawLine(left, top, left, bottom, axis);
        canvas.drawLine(left, bottom, right, bottom, axis);

        if (!hasRange) {
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("표시할 데이터가 부족해요", (left + right) / 2f, (top + bottom) / 2f, text);
            return;
        }

        long fullSpan = Math.max(1L, fullMaxTime - fullMinTime);
        float visibleFraction = 1f / zoom;
        long visibleStart = fullMinTime + (long) (fullSpan * viewportStart);
        long visibleSpan = Math.max(1L, (long) (fullSpan * visibleFraction));
        long visibleEnd = Math.min(fullMaxTime, visibleStart + visibleSpan);
        float ySpan = fullMaxValue - fullMinValue;

        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        Path path = new Path();
        boolean started = false;
        ActivityRouteAnalysis.Sample previous = null;
        for (ActivityRouteAnalysis.Sample s : samples) {
            if (!valid(s)) {
                started = false;
                previous = null;
                continue;
            }
            if (s.timeMs < visibleStart) {
                previous = s;
                continue;
            }
            if (s.timeMs > visibleEnd) break;

            if (!started && previous != null && valid(previous)) {
                float px = left + (right - left) * ((previous.timeMs - visibleStart) / (float) Math.max(1L, visibleEnd - visibleStart));
                float py = bottom - (bottom - top) * ((value(previous) - fullMinValue) / ySpan);
                path.moveTo(px, py);
                started = true;
            }

            float x = left + (right - left) * ((s.timeMs - visibleStart) / (float) Math.max(1L, visibleEnd - visibleStart));
            float y = bottom - (bottom - top) * ((value(s) - fullMinValue) / ySpan);
            if (!started) {
                path.moveTo(x, y);
                started = true;
            } else {
                path.lineTo(x, y);
            }
            previous = s;
        }
        canvas.drawPath(path, line);
        canvas.restore();

        // X labels show the two edges of the currently visible time window.
        // They live below the plot, away from the left-side Y labels, so labels never overlap.
        float xLabelY = getHeight() - dp(6);
        text.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(formatElapsed(visibleStart - fullMinTime), left, xLabelY, text);
        text.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatElapsed(visibleEnd - fullMinTime), right, xLabelY, text);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = event.getX();
                downY = event.getY();
                horizontalDrag = false;
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (scaleDetector.isInProgress()) return true;
                float totalDx = event.getX() - downX;
                float totalDy = event.getY() - downY;
                if (!horizontalDrag && Math.abs(totalDx) > touchSlop && Math.abs(totalDx) > Math.abs(totalDy) * 1.15f) {
                    horizontalDrag = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (horizontalDrag && zoom > 1f) {
                    float width = Math.max(1f, plotRight() - plotLeft());
                    float dx = event.getX() - lastX;
                    float visible = 1f / zoom;
                    viewportStart = clamp(viewportStart - (dx / width) * visible, 0f, 1f - visible);
                    invalidate();
                } else if (!horizontalDrag && Math.abs(totalDy) > touchSlop && Math.abs(totalDy) > Math.abs(totalDx)) {
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                }
                lastX = event.getX();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                horizontalDrag = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return true;
        }
    }

    private float plotLeft() {
        // Enough room for values like "123.4km/h" while keeping Y labels on the left only.
        return dp(mode == MODE_ALTITUDE ? 48 : 67);
    }

    private float plotRight() {
        return getWidth() - dp(8);
    }

    private boolean valid(ActivityRouteAnalysis.Sample s) {
        return mode == MODE_ALTITUDE ? s.altitudeValid : s.speedValid;
    }

    private float value(ActivityRouteAnalysis.Sample s) {
        return mode == MODE_ALTITUDE ? (float) s.altitudeM : s.speedKmh;
    }

    private String formatValue(float value) {
        return mode == MODE_ALTITUDE
                ? String.format(Locale.KOREAN, "%.0fm", value)
                : String.format(Locale.KOREAN, "%.1fkm/h", value);
    }

    private static String formatElapsed(long ms) {
        long totalSec = Math.max(0L, ms / 1000L);
        long h = totalSec / 3600L;
        long m = (totalSec % 3600L) / 60L;
        long s = totalSec % 60L;
        return h > 0
                ? String.format(Locale.KOREAN, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.KOREAN, "%d:%02d", m, s);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
