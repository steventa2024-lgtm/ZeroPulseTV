import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Renders the "ZeroPulse Signature" wallpapers: original procedural artwork in the brand palette.
 * Deterministic (fixed seeds), so re-running produces identical files.
 * Usage: java SignatureArt.java <output dir>
 */
public class SignatureArt {
    static final int W = 1920, H = 1080;
    static final Color DEEP = new Color(0x06090D), NAVY = new Color(0x0A1220), PANEL = new Color(0x101A2B),
            BLUE = new Color(0x2F80FF), CYAN = new Color(0x35D6FF), HOLO = new Color(0x5AA9FF);
    /** Same pulse polyline as the logo (PulseLogo.PULSE). */
    static final float[] PULSE = {0f, .5f, .20f, .5f, .27f, .36f, .34f, .5f, .43f, .5f, .51f, .04f,
            .60f, .96f, .68f, .5f, .77f, .5f, .83f, .38f, .89f, .5f, 1f, .5f};

    public static void main(String[] a) throws Exception {
        File dir = new File(a.length > 0 ? a[0] : ".");
        dir.mkdirs();
        save(pulseHorizon(), new File(dir, "zp01.jpg"));
        save(reactorDawn(), new File(dir, "zp02.jpg"));
        save(midnightWaves(), new File(dir, "zp03.jpg"));
        System.out.println("wrote zp01-03.jpg to " + dir.getAbsolutePath());
    }

    static Graphics2D canvas(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setPaint(new GradientPaint(0, 0, DEEP, 0, H, NAVY));
        g.fillRect(0, 0, W, H);
        return g;
    }

    static Color alpha(Color c, float a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(255 * Math.max(0, Math.min(1, a))));
    }

    /** Glow = the same shape stroked several times, wide and faint to narrow and bright. */
    static void glowStroke(Graphics2D g, java.awt.Shape s, Color c, float core) {
        float[][] layers = {{core * 9, .04f}, {core * 5, .08f}, {core * 2.6f, .18f}, {core * 1.4f, .45f}, {core, 1f}};
        for (float[] l : layers) {
            g.setStroke(new BasicStroke(l[0], BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(alpha(c, l[1]));
            g.draw(s);
        }
    }

    static Path2D pulse(float x, float y, float w, float h) {
        Path2D p = new Path2D.Float();
        for (int i = 0; i < PULSE.length; i += 2) {
            float px = x + PULSE[i] * w, py = y + PULSE[i + 1] * h;
            if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
        }
        return p;
    }

    static void stars(Graphics2D g, long seed, int n, float maxY) {
        Random r = new Random(seed);
        for (int i = 0; i < n; i++) {
            float x = r.nextFloat() * W, y = r.nextFloat() * maxY, s = 0.8f + r.nextFloat() * 1.8f;
            g.setColor(alpha(r.nextFloat() < 0.3f ? CYAN : Color.WHITE, 0.15f + r.nextFloat() * 0.45f));
            g.fill(new Ellipse2D.Float(x, y, s, s));
        }
    }

    // 1. Pulse Horizon: a glowing pulse crossing a dark horizon, perspective grid below
    static BufferedImage pulseHorizon() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas(img);
        stars(g, 7, 260, H * 0.55f);
        float hy = H * 0.60f;
        g.setPaint(new RadialGradientPaint(W * 0.62f, hy, W * 0.55f, new float[]{0f, 0.4f, 1f},
                new Color[]{alpha(BLUE, 0.38f), alpha(BLUE, 0.10f), alpha(BLUE, 0f)}));
        g.fillRect(0, 0, W, H);
        g.setStroke(new BasicStroke(1.2f));
        for (int i = 1; i <= 14; i++) {                       // horizontal grid lines, denser near the horizon
            float y = hy + (H - hy) * (float) Math.pow(i / 14f, 1.8);
            g.setColor(alpha(HOLO, 0.16f * (1 - i / 18f)));
            g.drawLine(0, Math.round(y), W, Math.round(y));
        }
        for (int i = -18; i <= 18; i++) {                     // converging verticals
            float x = W * 0.62f + i * 140;
            g.setColor(alpha(HOLO, 0.10f));
            g.drawLine(Math.round(W * 0.62f + i * 12), Math.round(hy), Math.round(x * 1.0f + i * 120), H);
        }
        g.setColor(alpha(CYAN, 0.35f));
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(0, Math.round(hy), W, Math.round(hy));
        glowStroke(g, pulse(W * 0.30f, hy - 150, W * 0.62f, 300), BLUE, 4.5f);
        glowStroke(g, pulse(W * 0.30f, hy - 150, W * 0.62f, 300), CYAN, 1.6f);
        g.dispose();
        return img;
    }

    // 2. Reactor Dawn: off-centre concentric rings and arcs rising like a sun
    static BufferedImage reactorDawn() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas(img);
        stars(g, 21, 320, H);
        float cx = W * 0.74f, cy = H * 0.46f;
        g.setPaint(new RadialGradientPaint(cx, cy, 620, new float[]{0f, 0.35f, 1f},
                new Color[]{alpha(HOLO, 0.40f), alpha(BLUE, 0.14f), alpha(BLUE, 0f)}));
        g.fillRect(0, 0, W, H);
        float[] radii = {110, 170, 240, 330, 440};
        for (int i = 0; i < radii.length; i++) {
            float r = radii[i];
            g.setStroke(new BasicStroke(i == 1 ? 3f : 1.4f));
            g.setColor(alpha(i % 2 == 0 ? HOLO : CYAN, 0.55f - i * 0.08f));
            g.draw(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
        }
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int k = 0; k < 4; k++) {
            g.setColor(alpha(BLUE, 0.85f));
            g.drawArc(Math.round(cx - 205), Math.round(cy - 205), 410, 410, 20 + k * 90, 55);
        }
        g.setPaint(new RadialGradientPaint(cx, cy, 95, new float[]{0f, 1f},
                new Color[]{alpha(CYAN, 0.9f), alpha(BLUE, 0.15f)}));
        g.fill(new Ellipse2D.Float(cx - 95, cy - 95, 190, 190));
        g.setColor(alpha(PANEL, 0.6f));
        g.fill(new Ellipse2D.Float(cx - 60, cy - 60, 120, 120));
        glowStroke(g, pulse(cx - 48, cy - 22, 96, 44), CYAN, 2.2f);
        g.dispose();
        return img;
    }

    // 3. Midnight Waves: layered waveforms flowing across the lower half
    static BufferedImage midnightWaves() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas(img);
        stars(g, 33, 180, H * 0.45f);
        g.setPaint(new RadialGradientPaint(W * 0.5f, H * 0.95f, W * 0.7f, new float[]{0f, 1f},
                new Color[]{alpha(BLUE, 0.30f), alpha(BLUE, 0f)}));
        g.fillRect(0, 0, W, H);
        for (int k = 0; k < 16; k++) {
            Path2D p = new Path2D.Float();
            float base = H * 0.50f + k * 22, amp = 34 + k * 5, ph = k * 0.55f;
            for (int x = 0; x <= W; x += 8) {
                double y = base + amp * Math.sin(x / 260.0 + ph) + amp * 0.35 * Math.sin(x / 97.0 - ph * 1.7);
                if (x == 0) p.moveTo(x, y); else p.lineTo(x, y);
            }
            Color c = k % 4 == 0 ? CYAN : (k % 2 == 0 ? HOLO : BLUE);
            g.setComposite(AlphaComposite.SrcOver);
            g.setStroke(new BasicStroke(k % 4 == 0 ? 2.2f : 1.3f));
            g.setColor(alpha(c, 0.55f - k * 0.025f));
            g.draw(p);
        }
        glowStroke(g, pulse(W * 0.58f, H * 0.36f - 70, W * 0.28f, 140), BLUE, 3.2f);
        g.dispose();
        return img;
    }

    static void save(BufferedImage img, File f) throws Exception {
        ImageWriter wr = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam p = wr.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(0.9f);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(f)) {
            wr.setOutput(out);
            wr.write(null, new IIOImage(img, null, null), p);
        }
        wr.dispose();
    }
}
