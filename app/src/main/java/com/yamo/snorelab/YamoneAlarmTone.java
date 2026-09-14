package com.yamo.snorelab;

/** Five original, deterministic Yamone alarm tones synthesized on-device. */
public final class YamoneAlarmTone {
    private YamoneAlarmTone() {}

    public static String normalizeStyle(String style) {
        if (style == null) return "BASIC";
        switch (style) {
            case "SOFT":
            case "BEAUTIFUL":
            case "FRESH":
            case "LOUD":
            case "BASIC":
                return style;
            case "PULSE":
                return "LOUD";
            case "STRONG":
            default:
                return "BASIC";
        }
    }

    public static String styleForRingtoneId(String id) {
        if ("soft".equals(id)) return "SOFT";
        if ("beautiful".equals(id)) return "BEAUTIFUL";
        if ("fresh".equals(id)) return "FRESH";
        if ("loud".equals(id)) return "LOUD";
        return "BASIC";
    }

    public static String ringtoneIdForStyle(String style) {
        style = normalizeStyle(style);
        if ("SOFT".equals(style)) return "soft";
        if ("BEAUTIFUL".equals(style)) return "beautiful";
        if ("FRESH".equals(style)) return "fresh";
        if ("LOUD".equals(style)) return "loud";
        return "basic";
    }

    public static String displayName(String style) {
        style = normalizeStyle(style);
        if ("SOFT".equals(style)) return "Soft Dawn";
        if ("BEAUTIFUL".equals(style)) return "Crystal Garden";
        if ("FRESH".equals(style)) return "Fresh Start";
        if ("LOUD".equals(style)) return "Power Alarm";
        return "Morning Bell";
    }

    public static short[] synth(String style, int sampleRate, int seconds) {
        style = normalizeStyle(style);
        int length = Math.max(1, sampleRate * Math.max(1, seconds));
        short[] pcm = new short[length];
        for (int i = 0; i < length; i++) {
            double t = i / (double) sampleRate;
            double v;
            switch (style) {
                case "SOFT": v = softDawn(t); break;
                case "BEAUTIFUL": v = crystalGarden(t); break;
                case "FRESH": v = freshStart(t); break;
                case "LOUD": v = powerAlarm(t); break;
                default: v = morningBell(t); break;
            }
            v = Math.max(-0.98, Math.min(0.98, v));
            pcm[i] = (short) Math.round(v * Short.MAX_VALUE);
        }
        return pcm;
    }

    private static double morningBell(double t) {
        double[] notes = {659.25, 783.99, 987.77, 783.99, 659.25, 987.77};
        double step = 0.52;
        int n = ((int) Math.floor(t / step)) % notes.length;
        double local = t % step;
        double env = attackDecay(local, 0.018, 4.6);
        double f = notes[n];
        return env * (0.58 * sine(f, t) + 0.20 * sine(f * 2.0, t) + 0.08 * sine(f * 3.0, t));
    }

    private static double softDawn(double t) {
        double[] notes = {523.25, 659.25, 783.99, 659.25};
        double step = 0.84;
        int n = ((int) Math.floor(t / step)) % notes.length;
        double local = t % step;
        double env = attackDecay(local, 0.09, 2.2);
        double f = notes[n];
        double pad = 0.07 * sine(261.63, t) + 0.05 * sine(329.63, t);
        return env * (0.34 * sine(f, t) + 0.09 * sine(f * 2.0, t)) + pad * (0.5 + 0.5 * Math.sin(Math.PI * Math.min(1.0, local / step)));
    }

    private static double crystalGarden(double t) {
        double[] notes = {659.25, 783.99, 987.77, 1318.51, 987.77, 783.99, 1046.50, 1318.51};
        double step = 0.38;
        int n = ((int) Math.floor(t / step)) % notes.length;
        double local = t % step;
        double env = attackDecay(local, 0.008, 6.8);
        double f = notes[n];
        return env * (0.48 * sine(f, t) + 0.18 * sine(f * 2.01, t) + 0.10 * sine(f * 3.98, t));
    }

    private static double freshStart(double t) {
        double[] notes = {523.25, 659.25, 783.99, 1046.50, 783.99, 880.00, 987.77, 1046.50};
        double step = 0.31;
        int n = ((int) Math.floor(t / step)) % notes.length;
        double local = t % step;
        double env = attackDecay(local, 0.012, 5.2);
        double f = notes[n];
        double pulse = (local < step * 0.78) ? 1.0 : 0.25;
        return pulse * env * (0.53 * sine(f, t) + 0.15 * sine(f * 2.0, t));
    }

    private static double powerAlarm(double t) {
        double cycle = t % 0.62;
        boolean first = cycle < 0.18;
        boolean second = cycle >= 0.28 && cycle < 0.46;
        if (!first && !second) return 0.0;
        double local = first ? cycle : cycle - 0.28;
        double f = first ? 880.0 : 1174.66;
        double edge = Math.min(1.0, Math.min(local / 0.012, (0.18 - local) / 0.018));
        edge = Math.max(0.0, edge);
        return edge * (0.67 * sine(f, t) + 0.18 * sine(f * 2.0, t) + 0.07 * sine(f * 0.5, t));
    }

    private static double sine(double hz, double t) {
        return Math.sin(2.0 * Math.PI * hz * t);
    }

    private static double attackDecay(double local, double attack, double decayRate) {
        if (local < 0) return 0.0;
        double a = Math.min(1.0, local / Math.max(0.001, attack));
        return a * Math.exp(-decayRate * Math.max(0.0, local - attack));
    }
}
