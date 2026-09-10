package com.yamo.snorelab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small vector-style pastel illustrations used by Yamone home/location sharing screens. */
public final class YamonePastelArtView extends View {
    public static final int MODE_LOCATION = 1;
    public static final int MODE_ACTIVITY = 2;
    public static final int MODE_LIFT = 3;
    public static final int MODE_STATS = 4;
    public static final int MODE_LOCATION_SCENE = 5;
    public static final int MODE_BUNNY = 6;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int mode;
    private final boolean pink;

    private final int mint = 0xFF4CCFB0;
    private final int mintDark = 0xFF159A7A;
    private final int mintLight = 0xFFDDF8EF;
    private final int pinkMain = 0xFFFF769F;
    private final int pinkDark = 0xFFE94778;
    private final int pinkLight = 0xFFFFE2EB;
    private final int ink = 0xFF24423F;
    private final int sky = 0xFFEAF9FF;
    private final int snow = 0xFFF9FEFF;

    public YamonePastelArtView(Context context, int mode) {
        super(context);
        this.mode = mode;
        this.pink = "pink".equals(context.getSharedPreferences(SleepRecorderService.PREFS, 0)
                .getString("yamone_theme", "pink"));
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        switch (mode) {
            case MODE_ACTIVITY: drawActivity(c); break;
            case MODE_LIFT: drawLift(c); break;
            case MODE_STATS: drawStats(c); break;
            case MODE_LOCATION_SCENE: drawScene(c); break;
            case MODE_BUNNY: drawBunny(c); break;
            case MODE_LOCATION:
            default: drawLocation(c); break;
        }
    }

    private float sx() { return getWidth() / 100f; }
    private float sy() { return getHeight() / 100f; }
    private float x(float v) { return v * sx(); }
    private float y(float v) { return v * sy(); }

    private void fill(int color) { p.setStyle(Paint.Style.FILL); p.setColor(color); p.setStrokeWidth(1f); }
    private void stroke(int color, float width) { p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setColor(color); p.setStrokeWidth(width); }
    private void oval(Canvas c, float l, float t, float r, float b, int color) { fill(color); c.drawOval(new RectF(x(l), y(t), x(r), y(b)), p); }
    private void rect(Canvas c, float l, float t, float r, float b, float radius, int color) { fill(color); c.drawRoundRect(new RectF(x(l), y(t), x(r), y(b)), x(radius), x(radius), p); }

    private void drawLocation(Canvas c) {
        int main = pink ? pinkMain : mint;
        int dark = pink ? pinkDark : mintDark;
        int light = pink ? pinkLight : mintLight;
        oval(c, 18, 10, 82, 74, light);
        path.reset();
        path.moveTo(x(50), y(12));
        path.cubicTo(x(31), y(12), x(20), y(26), x(20), y(42));
        path.cubicTo(x(20), y(62), x(40), y(78), x(50), y(91));
        path.cubicTo(x(60), y(78), x(80), y(62), x(80), y(42));
        path.cubicTo(x(80), y(26), x(69), y(12), x(50), y(12));
        fill(main); c.drawPath(path, p);
        oval(c, 37, 29, 63, 55, Color.WHITE);
        oval(c, 43, 35, 57, 49, dark);
    }

    private void drawActivity(Canvas c) {
        int main = pink ? pinkMain : mint;
        int light = pink ? pinkLight : mintLight;
        oval(c, 9, 9, 91, 91, light);
        path.reset();
        path.moveTo(x(18), y(58));
        path.cubicTo(x(29), y(56), x(37), y(48), x(44), y(36));
        path.lineTo(x(58), y(43));
        path.cubicTo(x(64), y(52), x(72), y(57), x(84), y(61));
        path.lineTo(x(86), y(73));
        path.cubicTo(x(64), y(80), x(40), y(79), x(16), y(72));
        path.close();
        fill(Color.WHITE); c.drawPath(path, p);
        stroke(main, Math.max(3f, x(2.8f))); c.drawPath(path, p);
        stroke(main, Math.max(2f, x(2f)));
        c.drawLine(x(46), y(48), x(60), y(53), p);
        c.drawLine(x(41), y(54), x(56), y(59), p);
        fill(main); c.drawRoundRect(new RectF(x(18), y(68), x(84), y(76)), x(4), x(4), p);
    }

    private void drawLift(Canvas c) {
        int main = pink ? pinkMain : mint;
        int dark = pink ? pinkDark : mintDark;
        int light = pink ? pinkLight : mintLight;
        oval(c, 8, 8, 92, 92, light);
        stroke(dark, Math.max(2f, x(2.3f)));
        c.drawLine(x(17), y(22), x(83), y(22), p);
        c.drawLine(x(50), y(22), x(50), y(37), p);
        rect(c, 24, 35, 76, 71, 10, main);
        rect(c, 30, 41, 47, 58, 4, sky);
        rect(c, 53, 41, 70, 58, 4, sky);
        stroke(dark, Math.max(2f, x(2f)));
        c.drawLine(x(36), y(72), x(36), y(81), p);
        c.drawLine(x(64), y(72), x(64), y(81), p);
        c.drawLine(x(29), y(81), x(43), y(81), p);
        c.drawLine(x(57), y(81), x(71), y(81), p);
    }

    private void drawStats(Canvas c) {
        int main = pink ? pinkMain : mint;
        int dark = pink ? pinkDark : mintDark;
        int light = pink ? pinkLight : mintLight;
        oval(c, 9, 9, 91, 91, light);
        rect(c, 22, 52, 35, 78, 6, main);
        rect(c, 43, 34, 56, 78, 6, dark);
        rect(c, 64, 20, 77, 78, 6, main);
        stroke(ink, Math.max(2f, x(1.8f)));
        c.drawLine(x(17), y(82), x(83), y(82), p);
    }

    private void drawScene(Canvas c) {
        int main = pink ? pinkMain : mint;
        int dark = pink ? pinkDark : mintDark;
        int light = pink ? pinkLight : mintLight;
        fill(pink ? 0xFFFFF5F8 : 0xFFF4FFFB);
        c.drawRoundRect(new RectF(0, 0, getWidth(), getHeight()), x(8), x(8), p);

        // clouds
        oval(c, 8, 12, 26, 25, sky); oval(c, 17, 8, 37, 24, sky);
        oval(c, 72, 13, 90, 25, sky); oval(c, 63, 9, 82, 24, sky);

        // mountains
        path.reset(); path.moveTo(x(6), y(58)); path.lineTo(x(28), y(25)); path.lineTo(x(48), y(58)); path.close(); fill(0xFFBFEFE0); c.drawPath(path,p);
        path.reset(); path.moveTo(x(42), y(58)); path.lineTo(x(66), y(20)); path.lineTo(x(92), y(58)); path.close(); fill(0xFFD5F3FF); c.drawPath(path,p);
        path.reset(); path.moveTo(x(20), y(37)); path.lineTo(x(28), y(25)); path.lineTo(x(35), y(37)); path.lineTo(x(31), y(35)); path.lineTo(x(28), y(40)); path.lineTo(x(25), y(35)); path.close(); fill(snow); c.drawPath(path,p);
        path.reset(); path.moveTo(x(58), y(32)); path.lineTo(x(66), y(20)); path.lineTo(x(75), y(33)); path.lineTo(x(70), y(30)); path.lineTo(x(66), y(36)); path.lineTo(x(62), y(30)); path.close(); fill(snow); c.drawPath(path,p);

        // map card
        path.reset();
        path.moveTo(x(18), y(54)); path.lineTo(x(40), y(48)); path.lineTo(x(60), y(55)); path.lineTo(x(82), y(48));
        path.lineTo(x(82), y(82)); path.lineTo(x(60), y(88)); path.lineTo(x(40), y(81)); path.lineTo(x(18), y(87)); path.close();
        fill(Color.WHITE); p.setShadowLayer(x(2),0,x(1),0x22000000); c.drawPath(path,p); p.clearShadowLayer();
        stroke(0xFFB7EBDD, Math.max(2f, x(1.5f)));
        c.drawLine(x(40), y(48), x(40), y(81), p); c.drawLine(x(60), y(55), x(60), y(88), p);
        stroke(0xFFAEDCE9, Math.max(2f, x(1.6f))); c.drawLine(x(22), y(71), x(76), y(61), p);

        // three avatars
        drawAvatar(c, 27, 58, main, 0xFFFFD3C5);
        drawAvatar(c, 72, 61, dark, 0xFFFFD3C5);
        drawAvatar(c, 49, 69, main, 0xFFF4C6A8);

        // location pin center
        path.reset();
        path.moveTo(x(52), y(44));
        path.cubicTo(x(45), y(44), x(41), y(49), x(41), y(55));
        path.cubicTo(x(41), y(63), x(49), y(69), x(52), y(74));
        path.cubicTo(x(55), y(69), x(63), y(63), x(63), y(55));
        path.cubicTo(x(63), y(49), x(59), y(44), x(52), y(44));
        fill(main); c.drawPath(path,p); oval(c, 48, 50, 56, 58, Color.WHITE);
    }

    private void drawAvatar(Canvas c, float cx, float cy, int jacket, int skin) {
        oval(c, cx-6, cy-9, cx+6, cy+3, 0xFF3C3A3B);
        oval(c, cx-5, cy-7, cx+5, cy+3, skin);
        rect(c, cx-7, cy+1, cx+7, cy+11, 5, jacket);
        rect(c, cx-7, cy-10, cx+7, cy-5, 5, jacket);
    }

    private void drawBunny(Canvas c) {
        int main = pink ? pinkMain : mint;
        int light = pink ? pinkLight : mintLight;
        oval(c, 12, 12, 88, 88, light);
        // ears
        oval(c, 28, 10, 43, 48, Color.WHITE); oval(c, 57, 10, 72, 48, Color.WHITE);
        oval(c, 32, 16, 39, 40, main); oval(c, 61, 16, 68, 40, main);
        // head/body
        oval(c, 24, 31, 76, 82, Color.WHITE);
        oval(c, 34, 48, 40, 54, ink); oval(c, 60, 48, 66, 54, ink);
        oval(c, 47, 58, 53, 64, main);
        stroke(ink, Math.max(2f, x(1.6f)));
        c.drawLine(x(50), y(64), x(46), y(68), p); c.drawLine(x(50), y(64), x(54), y(68), p);
    }
}
