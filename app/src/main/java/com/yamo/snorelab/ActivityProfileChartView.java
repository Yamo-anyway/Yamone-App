package com.yamo.snorelab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.Collections;
import java.util.List;

/** Lightweight local chart for activity altitude or speed over the full timeline. */
public final class ActivityProfileChartView extends View {
    public static final int MODE_ALTITUDE = 1;
    public static final int MODE_SPEED = 2;

    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<ActivityRouteAnalysis.Sample> samples = Collections.emptyList();
    private int mode = MODE_ALTITUDE;

    public ActivityProfileChartView(Context context) {
        super(context);
        boolean pink = "pink".equals(context.getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "mint"));
        grid.setColor(pink ? 0x22E94778 : 0x22159A7A);
        grid.setStrokeWidth(dp(1));
        line.setColor(pink ? 0xFFE94778 : 0xFF159A7A);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(2.5f));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        text.setColor(pink ? 0xFF9A7180 : 0xFF718984);
        text.setTextSize(dp(10));
    }

    public void setData(ActivityRouteAnalysis.Result result, int mode) {
        this.samples = result == null ? Collections.emptyList() : result.samples;
        this.mode = mode;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = dp(8), right = getWidth() - dp(8), top = dp(10), bottom = getHeight() - dp(24);
        if (right <= left || bottom <= top) return;

        for (int i = 0; i <= 3; i++) {
            float y = top + (bottom - top) * i / 3f;
            canvas.drawLine(left, y, right, y, grid);
        }

        long minTime = Long.MAX_VALUE, maxTime = Long.MIN_VALUE;
        float minValue = Float.POSITIVE_INFINITY, maxValue = Float.NEGATIVE_INFINITY;
        int validCount = 0;
        for (ActivityRouteAnalysis.Sample s : samples) {
            if (!valid(s)) continue;
            float v = value(s);
            minValue = Math.min(minValue, v);
            maxValue = Math.max(maxValue, v);
            minTime = Math.min(minTime, s.timeMs);
            maxTime = Math.max(maxTime, s.timeMs);
            validCount++;
        }
        if (validCount < 2) {
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("표시할 데이터가 부족해요", (left + right) / 2f, (top + bottom) / 2f, text);
            return;
        }
        if (maxValue - minValue < 0.5f) {
            maxValue += 0.25f;
            minValue -= 0.25f;
        }
        long timeSpan = Math.max(1L, maxTime - minTime);

        Path path = new Path();
        boolean started = false;
        for (ActivityRouteAnalysis.Sample s : samples) {
            if (!valid(s)) {
                started = false;
                continue;
            }
            float x = left + (right - left) * ((s.timeMs - minTime) / (float) timeSpan);
            float v = value(s);
            float y = bottom - (bottom - top) * ((v - minValue) / (maxValue - minValue));
            if (!started) {
                path.moveTo(x, y);
                started = true;
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, line);

        text.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(formatValue(maxValue), left, dp(10), text);
        canvas.drawText("0:00", left, getHeight() - dp(5), text);
        text.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatValue(minValue), right, bottom + dp(12), text);
        canvas.drawText(formatDuration(timeSpan), right, getHeight() - dp(5), text);
    }

    private boolean valid(ActivityRouteAnalysis.Sample s) {
        return mode == MODE_ALTITUDE ? s.altitudeValid : s.speedValid;
    }

    private float value(ActivityRouteAnalysis.Sample s) {
        return mode == MODE_ALTITUDE ? (float) s.altitudeM : s.speedKmh;
    }

    private String formatValue(float value) {
        return mode == MODE_ALTITUDE ? String.format(java.util.Locale.KOREAN, "%.0fm", value)
                : String.format(java.util.Locale.KOREAN, "%.1fkm/h", value);
    }

    private static String formatDuration(long ms) {
        long totalMin = Math.max(0, ms / 60_000L);
        long h = totalMin / 60;
        long m = totalMin % 60;
        return h > 0 ? String.format(java.util.Locale.KOREAN, "%d:%02d", h, m)
                : String.format(java.util.Locale.KOREAN, "%d분", m);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
