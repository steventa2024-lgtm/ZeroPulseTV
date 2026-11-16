package tv.calmhome.launcher.core;

import java.io.ByteArrayOutputStream;

/**
 * The interface sounds, synthesised: short, quiet, original tones (no recordings, no third-party assets). Each is a few dozen
 * milliseconds of 16-bit mono PCM at 44.1 kHz with a smooth attack and decay so nothing clicks. Pure Java: unit-tested off-device.
 */
public final class UiTones {
    private UiTones() { }

    public enum Tone { TICK, THUD, WHOOSH, SUCCESS, ERROR }

    public static final int RATE = 44_100;

    /** The tone's samples, -1..1. */
    public static float[] samples(Tone t) {
        switch (t) {
            case TICK: return add(new float[ms(22)], 0, 1900f, 0.30f, 0.004f, 0.0006f);
            case THUD: {
                float[] s = add(new float[ms(80)], 0, 140f, 0.55f, 0.028f, 0.002f);
                return add(s, 0, 280f, 0.18f, 0.016f, 0.002f);
            }
            case WHOOSH: return whoosh(ms(220));
            case SUCCESS: {
                float[] s = new float[ms(260)];
                add(s, 0, 660f, 0.28f, 0.06f, 0.003f);
                return add(s, ms(90), 990f, 0.26f, 0.08f, 0.003f);
            }
            default: {
                float[] s = new float[ms(200)];
                add(s, 0, 220f, 0.34f, 0.03f, 0.003f);
                return add(s, ms(110), 196f, 0.34f, 0.03f, 0.003f);
            }
        }
    }

    /** A complete WAV file (RIFF, PCM 16-bit mono) for the tone. */
    public static byte[] wav(Tone t) {
        float[] s = samples(t);
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + s.length * 2);
        int data = s.length * 2;
        ascii(out, "RIFF"); le32(out, 36 + data); ascii(out, "WAVE");
        ascii(out, "fmt "); le32(out, 16); le16(out, 1); le16(out, 1); le32(out, RATE); le32(out, RATE * 2); le16(out, 2); le16(out, 16);
        ascii(out, "data"); le32(out, data);
        for (float v : s) le16(out, Math.round(Math.max(-1f, Math.min(1f, v)) * 32767));
        return out.toByteArray();
    }

    static int ms(int ms) {
        return RATE * ms / 1000;
    }

    /** Adds a sine of {@code hz} from sample {@code at}: attack {@code attack} s, then an exponential decay with time constant {@code tau} s. */
    private static float[] add(float[] s, int at, float hz, float amp, float tau, float attack) {
        for (int i = at; i < s.length; i++) {
            float t = (i - at) / (float) RATE;
            float env = (t < attack ? t / attack : 1f) * (float) Math.exp(-(t - Math.min(t, attack)) / tau);
            s[i] += amp * env * (float) Math.sin(2 * Math.PI * hz * t);
        }
        fadeOut(s, ms(4));
        return s;
    }

    /** Soft filtered noise that swells and fades: a restrained "whoosh" (deterministic: the same every time). */
    private static float[] whoosh(int n) {
        float[] s = new float[n];
        java.util.Random r = new java.util.Random(7);
        float low = 0f;
        for (int i = 0; i < n; i++) {
            float p = i / (float) n;
            float cut = 0.02f + 0.10f * (float) Math.sin(Math.PI * p);       // the filter opens and closes with the swell
            low += cut * ((r.nextFloat() * 2f - 1f) - low);
            s[i] = low * 0.9f * (float) Math.sin(Math.PI * p);
        }
        fadeOut(s, ms(6));
        return s;
    }

    private static void fadeOut(float[] s, int n) {
        for (int i = Math.max(0, s.length - n); i < s.length; i++) s[i] *= (s.length - i) / (float) n;
    }

    private static void ascii(ByteArrayOutputStream o, String s) {
        for (int i = 0; i < s.length(); i++) o.write(s.charAt(i));
    }

    private static void le16(ByteArrayOutputStream o, int v) {
        o.write(v & 255);
        o.write((v >> 8) & 255);
    }

    private static void le32(ByteArrayOutputStream o, int v) {
        le16(o, v & 0xFFFF);
        le16(o, (v >>> 16) & 0xFFFF);
    }
}
