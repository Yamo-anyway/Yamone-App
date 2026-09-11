package com.yamo.snorelab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Yamone first-renewal icon set.
 *
 * Everything is drawn locally with Canvas so the preview does not depend on emoji,
 * system icon fonts, or third-party artwork. The same shapes can be reused by the
 * production screens later.
 */
public class YamoneIconView extends View {
    public static final int NAV_HOME = 1;
    public static final int NAV_ACTIVITY = 2;
    public static final int NAV_RECORDS = 3;
    public static final int NAV_ALARM = 4;
    public static final int NAV_SETTINGS = 5;

    public static final int ACTIVITY_MULTI = 20;
    public static final int ACTIVITY_SNOW = 21;
    public static final int ACTIVITY_LOCATION = 22;
    public static final int ACTIVITY_SLEEP = 23;
    public static final int SEAL_HOME = 24;

    public static final int METRIC_TIME = 30;
    public static final int METRIC_DISTANCE = 31;
    public static final int METRIC_CALORIE = 32;

    public static final int CHEVRON_RIGHT = 40;
    public static final int CHEVRON_DOWN = 41;
    public static final int CHEVRON_UP = 42;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int iconType;
    private int accent = 0xFF2AA887;
    private int secondary = 0xFF70837F;
    private boolean selected;

    public YamoneIconView(Context context, int iconType) {
        super(context);
        this.iconType = iconType;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setIconType(int iconType) {
        this.iconType = iconType;
        invalidate();
    }

    public void setPalette(int accent, int secondary, boolean selected) {
        this.accent = accent;
        this.secondary = secondary;
        this.selected = selected;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        switch (iconType) {
            case NAV_HOME: drawHome(canvas, w, h); break;
            case NAV_ACTIVITY: drawRunner(canvas, w, h); break;
            case NAV_RECORDS: drawRecords(canvas, w, h); break;
            case NAV_ALARM: drawBell(canvas, w, h); break;
            case NAV_SETTINGS: drawGear(canvas, w, h); break;
            case ACTIVITY_MULTI: drawMultiActivity(canvas, w, h); break;
            case ACTIVITY_SNOW: drawSnow(canvas, w, h); break;
            case ACTIVITY_LOCATION: drawLocation(canvas, w, h); break;
            case ACTIVITY_SLEEP: drawSleep(canvas, w, h); break;
            case SEAL_HOME: drawHomeSeal(canvas, w, h); break;
            case METRIC_TIME: drawClock(canvas, w, h); break;
            case METRIC_DISTANCE: drawPin(canvas, w, h); break;
            case METRIC_CALORIE: drawFlame(canvas, w, h); break;
            case CHEVRON_RIGHT: drawChevron(canvas, w, h, 0); break;
            case CHEVRON_DOWN: drawChevron(canvas, w, h, 1); break;
            case CHEVRON_UP: drawChevron(canvas, w, h, 2); break;
        }
    }

    private int mainColor() { return selected ? accent : secondary; }

    private void stroke(float width, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(width);
        paint.setColor(color);
    }

    private void fill(int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
    }

    private void drawHome(Canvas c, float w, float h) {
        int color = mainColor();
        stroke(Math.max(2.8f, w * .09f), color);
        path.reset();
        path.moveTo(w * .20f, h * .50f);
        path.lineTo(w * .50f, h * .22f);
        path.lineTo(w * .80f, h * .50f);
        c.drawPath(path, paint);
        path.reset();
        path.moveTo(w * .28f, h * .45f);
        path.lineTo(w * .28f, h * .78f);
        path.lineTo(w * .72f, h * .78f);
        path.lineTo(w * .72f, h * .45f);
        c.drawPath(path, paint);
        c.drawLine(w * .50f, h * .78f, w * .50f, h * .61f, paint);
    }

    private void drawRunner(Canvas c, float w, float h) {
        int color = mainColor();
        float sw = Math.max(2.6f, w * .075f);
        stroke(sw, color);
        c.drawCircle(w * .57f, h * .24f, w * .08f, paint);
        c.drawLine(w * .53f, h * .34f, w * .45f, h * .54f, paint);
        c.drawLine(w * .48f, h * .43f, w * .30f, h * .48f, paint);
        c.drawLine(w * .49f, h * .44f, w * .68f, h * .40f, paint);
        c.drawLine(w * .45f, h * .54f, w * .67f, h * .65f, paint);
        c.drawLine(w * .45f, h * .54f, w * .29f, h * .75f, paint);
        c.drawLine(w * .67f, h * .65f, w * .81f, h * .65f, paint);
    }

    private void drawRecords(Canvas c, float w, float h) {
        int color = mainColor();
        stroke(Math.max(3f, w * .09f), color);
        c.drawLine(w * .27f, h * .72f, w * .27f, h * .50f, paint);
        c.drawLine(w * .50f, h * .72f, w * .50f, h * .30f, paint);
        c.drawLine(w * .73f, h * .72f, w * .73f, h * .42f, paint);
    }

    private void drawBell(Canvas c, float w, float h) {
        int color = mainColor();
        stroke(Math.max(2.6f, w * .075f), color);
        RectF bell = new RectF(w * .28f, h * .28f, w * .72f, h * .69f);
        c.drawArc(bell, 190, 160, false, paint);
        c.drawLine(w * .29f, h * .56f, w * .22f, h * .70f, paint);
        c.drawLine(w * .71f, h * .56f, w * .78f, h * .70f, paint);
        c.drawLine(w * .22f, h * .70f, w * .78f, h * .70f, paint);
        c.drawCircle(w * .50f, h * .77f, w * .035f, paint);
    }

    private void drawGear(Canvas c, float w, float h) {
        int color = mainColor();
        float cx = w / 2f, cy = h / 2f;
        stroke(Math.max(2.4f, w * .07f), color);
        c.drawCircle(cx, cy, w * .20f, paint);
        c.drawCircle(cx, cy, w * .065f, paint);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8.0;
            float x1 = cx + (float)Math.cos(a) * w * .22f;
            float y1 = cy + (float)Math.sin(a) * w * .22f;
            float x2 = cx + (float)Math.cos(a) * w * .31f;
            float y2 = cy + (float)Math.sin(a) * w * .31f;
            c.drawLine(x1, y1, x2, y2, paint);
        }
    }

    private void drawMultiActivity(Canvas c, float w, float h) {
        // Three seal characters: walk / run / bike. Kept simple enough to render cleanly at 56dp.
        float third = w / 3f;
        drawMiniSeal(c, third * .46f, h * .47f, third * .30f, 0);
        drawMiniSeal(c, third * 1.48f, h * .45f, third * .30f, 1);
        drawMiniBikeSeal(c, third * 2.50f, h * .47f, third * .30f);
    }

    private void drawMiniSeal(Canvas c, float cx, float cy, float r, int mode) {
        fill(Color.WHITE);
        paint.setShadowLayer(1.5f, 0, 1f, 0x22000000);
        c.drawOval(new RectF(cx - r * .95f, cy - r * .40f, cx + r * 1.05f, cy + r * .72f), paint);
        c.drawCircle(cx, cy - r * .42f, r * .65f, paint);
        paint.clearShadowLayer();
        stroke(Math.max(1.6f, r * .12f), 0xFF3E5960);
        c.drawOval(new RectF(cx - r * .95f, cy - r * .40f, cx + r * 1.05f, cy + r * .72f), paint);
        c.drawCircle(cx, cy - r * .42f, r * .65f, paint);
        fill(0xFF2E3B40);
        c.drawCircle(cx - r * .20f, cy - r * .48f, r * .055f, paint);
        c.drawCircle(cx + r * .20f, cy - r * .48f, r * .055f, paint);
        c.drawCircle(cx, cy - r * .34f, r * .045f, paint);
        stroke(Math.max(1.4f, r * .10f), accent);
        if (mode == 0) {
            // small backpack / walking strap
            c.drawLine(cx - r * .68f, cy - r * .20f, cx - r * .72f, cy + r * .28f, paint);
        } else {
            // running headband and motion line
            c.drawArc(new RectF(cx - r * .52f, cy - r * .96f, cx + r * .52f, cy - r * .20f), 200, 140, false, paint);
            c.drawLine(cx - r * 1.18f, cy + r * .30f, cx - r * .82f, cy + r * .30f, paint);
        }
    }

    private void drawMiniBikeSeal(Canvas c, float cx, float cy, float r) {
        stroke(Math.max(1.7f, r * .11f), accent);
        float wheel = r * .38f;
        c.drawCircle(cx - r * .62f, cy + r * .50f, wheel, paint);
        c.drawCircle(cx + r * .60f, cy + r * .50f, wheel, paint);
        path.reset();
        path.moveTo(cx - r * .62f, cy + r * .50f);
        path.lineTo(cx - r * .10f, cy + r * .12f);
        path.lineTo(cx + r * .22f, cy + r * .50f);
        path.lineTo(cx - r * .62f, cy + r * .50f);
        path.moveTo(cx - r * .10f, cy + r * .12f);
        path.lineTo(cx + r * .40f, cy + r * .12f);
        path.lineTo(cx + r * .60f, cy + r * .50f);
        c.drawPath(path, paint);
        drawMiniSeal(c, cx - r * .05f, cy - r * .18f, r * .78f, 0);
        // helmet
        fill(0xFF65B9EE);
        c.drawArc(new RectF(cx - r * .48f, cy - r * 1.00f, cx + r * .44f, cy - r * .22f), 185, 170, true, paint);
    }

    private void drawSnow(Canvas c, float w, float h) {
        // Mountain backdrop
        fill(0xFFDCEFF8);
        path.reset();
        path.moveTo(w * .04f, h * .70f);
        path.lineTo(w * .26f, h * .33f);
        path.lineTo(w * .40f, h * .55f);
        path.lineTo(w * .57f, h * .25f);
        path.lineTo(w * .91f, h * .70f);
        path.close();
        c.drawPath(path, paint);

        // Ski seal on the left
        drawMiniSeal(c, w * .34f, h * .52f, w * .15f, 0);
        fill(0xFF6AA8E8);
        c.drawArc(new RectF(w * .22f, h * .25f, w * .46f, h * .47f), 190, 160, true, paint);
        stroke(Math.max(2f, w * .025f), accent);
        c.drawLine(w * .18f, h * .77f, w * .49f, h * .73f, paint);
        c.drawLine(w * .17f, h * .82f, w * .50f, h * .78f, paint);

        // Snowboard seal on the right – visually shows both ski and board in one Snow card.
        drawMiniSeal(c, w * .70f, h * .50f, w * .13f, 0);
        stroke(Math.max(2.2f, w * .026f), 0xFFE76D95);
        RectF board = new RectF(w * .56f, h * .72f, w * .88f, h * .82f);
        c.drawArc(board, 180, 180, false, paint);
    }

    private void drawLocation(Canvas c, float w, float h) {
        // folded map
        stroke(Math.max(2f, w * .025f), 0xFF65B9A3);
        path.reset();
        path.moveTo(w * .08f, h * .64f);
        path.lineTo(w * .31f, h * .52f);
        path.lineTo(w * .53f, h * .62f);
        path.lineTo(w * .82f, h * .48f);
        path.lineTo(w * .91f, h * .76f);
        path.lineTo(w * .62f, h * .88f);
        path.lineTo(w * .38f, h * .78f);
        path.lineTo(w * .12f, h * .89f);
        path.close();
        c.drawPath(path, paint);
        c.drawLine(w * .31f, h * .52f, w * .38f, h * .78f, paint);
        c.drawLine(w * .53f, h * .62f, w * .62f, h * .88f, paint);

        // pin
        fill(0xFFFF7B78);
        c.drawCircle(w * .58f, h * .28f, w * .13f, paint);
        path.reset();
        path.moveTo(w * .49f, h * .35f);
        path.lineTo(w * .58f, h * .55f);
        path.lineTo(w * .67f, h * .35f);
        path.close();
        c.drawPath(path, paint);
        fill(Color.WHITE);
        c.drawCircle(w * .58f, h * .28f, w * .045f, paint);
    }

    private void drawSleep(Canvas c, float w, float h) {
        fill(0xFFD7D6FF);
        c.drawCircle(w * .38f, h * .42f, w * .26f, paint);
        fill(0xFFF7F8FF);
        c.drawCircle(w * .49f, h * .34f, w * .24f, paint);
        drawMiniSeal(c, w * .58f, h * .62f, w * .18f, 0);
        fill(0xFFFFD86B);
        c.drawCircle(w * .80f, h * .22f, w * .035f, paint);
        c.drawCircle(w * .72f, h * .31f, w * .025f, paint);
    }

    private void drawHomeSeal(Canvas c, float w, float h) {
        float cx = w * .52f, cy = h * .52f, r = Math.min(w, h) * .26f;
        fill(Color.WHITE);
        paint.setShadowLayer(5f, 0, 3f, 0x22000000);
        c.drawOval(new RectF(cx - r * 1.45f, cy - r * .42f, cx + r * 1.38f, cy + r * .72f), paint);
        c.drawCircle(cx - r * .54f, cy - r * .39f, r * .72f, paint);
        paint.clearShadowLayer();
        stroke(Math.max(2f, r * .075f), 0xFF3D5960);
        c.drawOval(new RectF(cx - r * 1.45f, cy - r * .42f, cx + r * 1.38f, cy + r * .72f), paint);
        c.drawCircle(cx - r * .54f, cy - r * .39f, r * .72f, paint);
        fill(0xFF2F3A3E);
        c.drawCircle(cx - r * .76f, cy - r * .45f, r * .07f, paint);
        c.drawCircle(cx - r * .35f, cy - r * .45f, r * .07f, paint);
        c.drawCircle(cx - r * .55f, cy - r * .28f, r * .055f, paint);
        // scarf in theme accent
        stroke(Math.max(4f, r * .16f), accent);
        c.drawArc(new RectF(cx - r * 1.06f, cy - r * .10f, cx - r * .05f, cy + r * .55f), 210, 125, false, paint);
    }

    private void drawClock(Canvas c, float w, float h) {
        int color = accent;
        stroke(Math.max(2.3f, w * .07f), color);
        c.drawCircle(w * .50f, h * .50f, w * .29f, paint);
        c.drawLine(w * .50f, h * .50f, w * .50f, h * .32f, paint);
        c.drawLine(w * .50f, h * .50f, w * .66f, h * .57f, paint);
    }

    private void drawPin(Canvas c, float w, float h) {
        fill(accent);
        c.drawCircle(w * .50f, h * .38f, w * .22f, paint);
        path.reset();
        path.moveTo(w * .34f, h * .52f);
        path.lineTo(w * .50f, h * .82f);
        path.lineTo(w * .66f, h * .52f);
        path.close();
        c.drawPath(path, paint);
        fill(Color.WHITE);
        c.drawCircle(w * .50f, h * .38f, w * .07f, paint);
    }

    private void drawFlame(Canvas c, float w, float h) {
        fill(0xFFFF755F);
        path.reset();
        path.moveTo(w * .50f, h * .12f);
        path.cubicTo(w * .28f, h * .35f, w * .26f, h * .52f, w * .31f, h * .67f);
        path.cubicTo(w * .35f, h * .83f, w * .47f, h * .90f, w * .57f, h * .86f);
        path.cubicTo(w * .76f, h * .79f, w * .80f, h * .57f, w * .67f, h * .41f);
        path.cubicTo(w * .61f, h * .34f, w * .57f, h * .25f, w * .50f, h * .12f);
        path.close();
        c.drawPath(path, paint);
        fill(0xFFFFD773);
        c.drawOval(new RectF(w * .43f, h * .54f, w * .61f, h * .78f), paint);
    }

    private void drawChevron(Canvas c, float w, float h, int direction) {
        stroke(Math.max(dp(3), Math.min(w, h) * .14f), mainColor());
        path.reset();
        if (direction == 0) {
            path.moveTo(w * .34f, h * .20f);
            path.lineTo(w * .66f, h * .50f);
            path.lineTo(w * .34f, h * .80f);
        } else if (direction == 1) {
            path.moveTo(w * .20f, h * .36f);
            path.lineTo(w * .50f, h * .66f);
            path.lineTo(w * .80f, h * .36f);
        } else {
            path.moveTo(w * .20f, h * .64f);
            path.lineTo(w * .50f, h * .34f);
            path.lineTo(w * .80f, h * .64f);
        }
        c.drawPath(path, paint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
