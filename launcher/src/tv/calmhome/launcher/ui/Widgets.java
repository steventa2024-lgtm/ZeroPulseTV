package tv.calmhome.launcher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import tv.calmhome.launcher.core.GlassMaterial;
import tv.calmhome.launcher.core.LiquidBrand;
import tv.calmhome.launcher.core.LiquidKey;
import tv.calmhome.launcher.core.LiquidTokens;
import tv.calmhome.launcher.core.TileArt;
import tv.calmhome.launcher.data.AppArt;

/**
 * Reusable ZeroPulse components with one shared focus language:
 * Electric Blue ring + blue spot-shadow glow + gentle scale, 150 ms decelerate (0 ms with reduced motion).
 */
public final class Widgets {
    public static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    public static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Context c;
    private final Theme t;
    private final DecelerateInterpolator ease = new DecelerateInterpolator(2f);
    /** Liquid Glass: logos keyed out of banners (null = not a plain logo), kept so a rebuilt dock does not key them again. */
    private final Map<String, LiquidKey.Logo> logos = new HashMap<>();
    private final Map<LiquidGlass, ValueAnimator> levelAnimators = new WeakHashMap<>();
    /** Corner radius of a Liquid Glass tile, in dp (the brief asks for 16-20). */
    static final float LIQUID_TILE_RADIUS = 18f;

    public Widgets(Context c, Theme t) {
        this.c = c;
        this.t = t;
    }

    // ------------------------------------------------------------------ focus

    /**
     * Card outline: hairline when idle, a ring when focused. In Liquid Glass the focused ring is the glass's own thin rim light (about
     * half a dp, 55 % white, with a faint inner sheen), not the 2.5 dp solid white of graphite: the brief asks for no heavy white
     * outlines, and Home's tiles show focus with their material, not a stroke.
     */
    public Drawable ring(boolean focused, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(t.dp(radiusDp));
        if (t.liquid) {
            g.setColor(focused ? 0x14FFFFFF : 0);
            g.setStroke(Math.max(1, t.dp(focused ? 0.75f : 0.5f)), Theme.withAlpha(Theme.SOFT_WHITE, focused ? 0.55f : 0.08f));
            return g;
        }
        g.setColor(0);
        if (focused) g.setStroke(t.dp(2.5f), Theme.ELECTRIC_BLUE);
        else g.setStroke(Math.max(1, t.dp(1)), Theme.withAlpha(Theme.SOFT_WHITE, 0.10f));
        return g;
    }

    /** Applies the shared focus look to a card; {@code label} (optional) fades in when focused. */
    public void focusable(final View card, final float radiusDp, final TextView label, final boolean labelAlways) {
        card.setFocusable(true);
        card.setClickable(true);
        card.setForeground(ring(false, radiusDp));
        card.setClipToOutline(true);
        if (Build.VERSION.SDK_INT >= 28) {
            card.setOutlineSpotShadowColor(Theme.ELECTRIC_BLUE);
            card.setOutlineAmbientShadowColor(Theme.ELECTRIC_BLUE);
        }
        if (label != null) label.setAlpha(labelAlways ? 0.85f : 0f);
        card.setOnFocusChangeListener((v, has) -> {
            v.setForeground(ring(has, radiusDp));
            float s = has && !t.reducedMotion ? t.focusScale : 1f;
            v.animate().scaleX(s).scaleY(s).translationZ(has ? t.dp(t.glowDp) : 0)
                    .setInterpolator(ease).setDuration(t.focusMs()).start();
            if (label != null) {
                label.animate().alpha(has ? 1f : (labelAlways ? 0.85f : 0f)).setDuration(t.focusMs()).start();
                label.setTextColor(has ? Theme.SOFT_WHITE : Theme.withAlpha(Theme.SOFT_WHITE, 0.85f));
            }
        });
    }

    // ------------------------------------------------------------------ app tile

    /** A 16:9 app card with its TV banner (or icon + name), and a label underneath. */
    public LinearLayout appTile(Drawable banner, Drawable icon, String name, int wDp, int hDp, boolean labelAlways,
                                View.OnClickListener click, View.OnLongClickListener longClick) {
        return appTile(name, banner, icon, name, wDp, hDp, labelAlways, click, longClick);
    }

    /** As above; {@code id} (the package name) lets Liquid Glass recognise the apps whose glass is tuned by hand. */
    public LinearLayout appTile(String id, Drawable banner, Drawable icon, String name, int wDp, int hDp, boolean labelAlways,
                                View.OnClickListener click, View.OnLongClickListener longClick) {
        FrameLayout card = new FrameLayout(c);
        LiquidGlass[] liquid = null;
        if (t.liquid) {
            liquid = liquidCard(card, id, banner, icon, name, hDp);
        } else if (banner != null) {
            card.setBackground(t.glass(Theme.R_TILE, .86f));
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(banner);
            card.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
        } else {
            card.setBackground(t.glass(Theme.R_TILE, .86f));
            LinearLayout inner = new LinearLayout(c);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setGravity(Gravity.CENTER);
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(icon);
            inner.addView(iv, new LinearLayout.LayoutParams(t.dp(hDp * 0.42f), t.dp(hDp * 0.42f)));
            TextView tv = t.text(c, Theme.T_CAPTION, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
            tv.setText(name);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setPadding(t.dp(6), t.dp(5), t.dp(6), 0);
            inner.addView(tv);
            card.addView(inner, new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        card.setContentDescription(name);
        card.setOnClickListener(click);
        if (longClick != null) {
            card.setLongClickable(true);
            card.setOnLongClickListener(longClick);
        }

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        col.addView(card, new LinearLayout.LayoutParams(t.dp(wDp), t.dp(hDp)));
        TextView label = t.text(c, Theme.T_LABEL, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        t.overPhoto(label);
        label.setText(name);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(t.dp(wDp), WRAP);
        lp.topMargin = t.dp(10);
        col.addView(label, lp);
        if (liquid != null) focusableLiquid(card, liquid, label, labelAlways);
        else focusable(card, Theme.R_TILE, label, labelAlways);
        return col;
    }

    // ------------------------------------------------------------------ approved graphite glass: Home tiles and quick actions

    /** Height of a Home quick action in dp: slim (the old capsules were 42 dp). */
    public static final int QUICK_ACTION_DP = 36;

    /**
     * A Home app tile on the approved graphite glass (Liquid Glass only), for artwork the caller already holds (a module
     * tile's glyph, say). An installed app's tile uses the overload that takes its {@link AppArt.Art}.
     */
    public LinearLayout glassAppTile(String id, Drawable banner, Drawable icon, String name, int wDp, int hDp, boolean labelAlways,
                                     View.OnClickListener click, View.OnLongClickListener longClick) {
        int tw = t.dp(wDp), th = t.dp(hDp);
        AppArt.Art art = banner != null ? AppArt.fromBanner(banner, "drawable", tw, th) : AppArt.fromIcon(icon, "drawable", tw, th);
        if (art == null) art = AppArt.monogram(name, tw, th);
        return glassAppTilePx(id, art, name, tw, th, labelAlways, click, longClick);
    }

    /**
     * A Home app tile on the approved graphite glass (Liquid Glass only), {@code wPx x hPx} pixels: the shared renderer's glass,
     * the app's own artwork centred above it ({@link TileArtView}), and the app's name underneath (shown on focus, or always
     * when {@code labelAlways} or when the app ships no artwork and the monogram needs its name). The returned column's first
     * child is the focusable {@link ZeroPulseGlassAppTile}; {@link #cardOf} finds it.
     */
    public LinearLayout glassAppTilePx(String id, AppArt.Art art, String name, int wPx, int hPx, boolean labelAlways,
                                       View.OnClickListener click, View.OnLongClickListener longClick) {
        final int ms = t.reducedMotion ? 0 : LiquidTokens.FOCUS_MS;
        final boolean showName = labelAlways || art.kind == TileArt.Kind.MONOGRAM;
        final ZeroPulseGlassAppTile card = new ZeroPulseGlassAppTile(c, t.kit, hPx, ms);
        card.setContent(new TileArtView(c, art));
        card.setGlowColor(tv.calmhome.launcher.core.BrandColor.known(id, name));   // a known brand's colour; otherwise the artwork's own
        card.setGlowEnabled(t.focusGlow);
        card.setContentDescription(name);
        // OK is acknowledged at once (the artwork dips); a second OK while the launch is in flight does not launch the app twice.
        card.setOnClickListener(click == null ? null : v -> { if (card.acknowledgeLaunch()) click.onClick(v); });
        if (longClick != null) {
            card.setLongClickable(true);
            card.setOnLongClickListener(longClick);
        }
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        col.addView(card, new LinearLayout.LayoutParams(wPx, hPx));
        final TextView label = t.text(c, Theme.T_LABEL, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        t.overPhoto(label);
        label.setText(name);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(wPx, WRAP);
        lp.topMargin = t.dp(9);
        col.addView(label, lp);
        label.setAlpha(showName ? 0.85f : 0f);
        card.setOnFocusChangeListener((v, has) -> {
            label.animate().alpha(has ? 1f : (showName ? 0.85f : 0f)).setDuration(ms).start();
            label.setTextColor(has ? Theme.SOFT_WHITE : Theme.withAlpha(Theme.SOFT_WHITE, 0.85f));
        });
        return col;
    }

    /**
     * A genuine empty state on the approved glass (Liquid Glass only): a focusable card with a title and one line of
     * explanation, {@code wDp} wide. It is a message, not an app tile, and its click is a real action.
     */
    public LinearLayout glassNotice(String title, String detail, int wDp, int hDp, View.OnClickListener click) {
        ZeroPulseGlassAppTile card = new ZeroPulseGlassAppTile(c, t.kit, t.dp(hDp), t.reducedMotion ? 0 : LiquidTokens.FOCUS_MS);
        LinearLayout inner = new LinearLayout(c);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);
        TextView head = t.text(c, Theme.T_TITLE, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        head.setText(title);
        head.setGravity(Gravity.CENTER);
        inner.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));
        TextView sub = t.text(c, Theme.T_BODY, Theme.withAlpha(Theme.SOFT_WHITE, 0.78f), Theme.FONT_REGULAR);
        sub.setText(detail);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(MATCH, WRAP);
        sp.topMargin = t.dp(4);
        inner.addView(sub, sp);
        card.setContent(inner);
        card.setContentDescription(title + ". " + detail);
        card.setOnClickListener(click);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.addView(card, new LinearLayout.LayoutParams(t.dp(wDp), t.dp(hDp)));
        return col;
    }

    /**
     * A control of the approved glass around {@code content} (Liquid Glass only; null in graphite): the shared renderer's glass, finish
     * and focus crossfade, no outline and no second background. {@code idleGlass} shows the glass all the time (a button, a card);
     * false shows it only while focused (a list row). {@code contentScale} above 1 lets the content, never the glass, swell on focus.
     * The caller sets the click and the content description.
     */
    public ZeroPulseGlassRow glassButton(View content, GraphiteGlassSurface.Shape shape, boolean idleGlass, float contentScale) {
        if (!t.liquid || t.kit == null) return null;
        ZeroPulseGlassRow row = new ZeroPulseGlassRow(c, t.kit, shape, t.reducedMotion ? 0 : LiquidTokens.FOCUS_MS, idleGlass, contentScale);
        row.content().addView(content, new FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER));
        return row;
    }

    /** A capsule button of the approved glass with an icon and a label (Liquid Glass only; null in graphite). */
    public ZeroPulseGlassRow glassPillButton(Glyph.Kind icon, String text, float textSp, View.OnClickListener click) {
        if (!t.liquid || t.kit == null) return null;
        LinearLayout line = new LinearLayout(c);
        line.setGravity(Gravity.CENTER);
        line.setPadding(t.dp(14), 0, t.dp(14), 0);
        if (icon != null) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(new Glyph(icon, Theme.SOFT_WHITE));
            line.addView(iv, new LinearLayout.LayoutParams(t.dp(15), t.dp(15)));
        }
        if (text != null) {
            TextView tv = t.text(c, textSp, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
            tv.setText(text);
            tv.setSingleLine(true);
            LinearLayout.LayoutParams lp = wrap();
            if (icon != null) lp.leftMargin = t.dp(6);
            line.addView(tv, lp);
        }
        ZeroPulseGlassRow row = glassButton(line, GraphiteGlassSurface.Shape.CAPSULE, true, 1f);
        row.setContentDescription(text);
        row.setOnClickListener(click);
        return row;
    }

    /**
     * A text field inside a capsule of the approved glass (Liquid Glass only; null in graphite). The field keeps focus and the keyboard;
     * the glass lights with the focus material while the field (or a button inside the capsule) has focus, so the field needs no ring.
     */
    public ZeroPulseGlassRow glassField(android.widget.EditText field, View leading, View trailing) {
        if (!t.liquid || t.kit == null) return null;
        LinearLayout line = new LinearLayout(c);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(t.dp(14), 0, t.dp(5), 0);
        if (leading != null) line.addView(leading, new LinearLayout.LayoutParams(t.dp(17), t.dp(17)));
        field.setBackground(null);
        field.setForeground(null);
        field.setOnFocusChangeListener(null);
        line.addView(field, new LinearLayout.LayoutParams(0, WRAP, 1f));
        if (trailing != null) line.addView(trailing);
        ZeroPulseGlassRow row = new ZeroPulseGlassRow(c, t.kit, GraphiteGlassSurface.Shape.CAPSULE, t.reducedMotion ? 0 : LiquidTokens.FOCUS_MS, true);
        row.content().addView(line, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER_VERTICAL));
        row.followChildFocus();
        return row;
    }

    /** A Home quick action on the approved graphite glass (Liquid Glass only). */
    public ZeroPulseGlassQuickAction quickAction(Glyph.Kind icon, String text, View.OnClickListener click) {
        ZeroPulseGlassQuickAction action = new ZeroPulseGlassQuickAction(c, t, t.kit, icon, text, t.dp(QUICK_ACTION_DP),
                t.reducedMotion ? 0 : LiquidTokens.FOCUS_MS);
        action.setOnClickListener(click);
        return action;
    }

    /**
     * Liquid Glass only: gives a control that is already a pill (a text button, a part of the model picker) the clear glass of the
     * Home chips and their focus behaviour, so what another screen adds looks like the rest of the Updated UI. Returns the glass
     * (a caller may layer a mark on it), or null in graphite, where the caller keeps its classic styling.
     */
    public Drawable glassPill(View v) {
        if (!t.liquid) return null;
        final LiquidGlass[] layers = t.liquidLayers(LiquidSpec.chip(), 40);
        v.setBackground(layers[0]);
        v.setForeground(layers[1]);
        focusableLiquid(v, layers, null, false);
        return layers[0];
    }

    // ------------------------------------------------------------------ Liquid Glass tile

    /**
     * Fills a card with Liquid Glass: clear glass tinted by the banner's colours with only the logo on top (the banner's
     * own backdrop is keyed out). A banner that is not a plain logo is drawn as a translucent picture, and an app with no
     * banner as its rounded icon and its name. Returns the glass's back and front parts.
     */
    private LiquidGlass[] liquidCard(FrameLayout card, String id, Drawable banner, Drawable icon, String name, int hDp) {
        LiquidKey.Logo logo = banner == null ? null : logo(id, banner);
        LiquidBrand brand = LiquidBrand.of(id, name);
        LiquidGlass[] layers = t.liquidLayers(LiquidSpec.tile(logo, brand), LIQUID_TILE_RADIUS);
        card.setBackground(layers[0]);
        card.setForeground(layers[1]);
        if (logo != null) {
            float px = t.pxPerDp();
            card.addView(new LiquidLogoView(c, logo, brand.logoGlow, LiquidTokens.LOGO_PAD_X_DP * px, LiquidTokens.LOGO_PAD_Y_DP * px),
                    new FrameLayout.LayoutParams(MATCH, MATCH));
        } else if (banner != null) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(banner);
            iv.setImageAlpha(158);
            roundClip(iv, t.dp(LIQUID_TILE_RADIUS));
            card.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
        } else {
            LinearLayout inner = new LinearLayout(c);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setGravity(Gravity.CENTER);
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(icon);
            roundClip(iv, t.dp(8));
            inner.addView(iv, new LinearLayout.LayoutParams(t.dp(hDp * 0.42f), t.dp(hDp * 0.42f)));
            // One line, a semi-bold condensed sans-serif (never a serif), shrunk to fit with 20 dp to spare.
            TextView tv = new TextView(c);
            tv.setTextColor(Theme.SOFT_WHITE);
            tv.setTypeface(Typeface.create(Theme.FONT_CONDENSED, Typeface.BOLD));
            tv.setIncludeFontPadding(false);
            tv.setText(name);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setGravity(Gravity.CENTER);
            tv.setAutoSizeTextTypeUniformWithConfiguration(Math.max(1, Math.round(9 * t.scale)), Math.max(2, Math.round(13 * t.scale)), 1,
                    android.util.TypedValue.COMPLEX_UNIT_SP);
            int pad = t.dp(LiquidTokens.LOGO_PAD_X_DP);
            tv.setPadding(pad, t.dp(5), pad, 0);
            inner.addView(tv, new LinearLayout.LayoutParams(MATCH, WRAP));
            card.addView(inner, new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        return layers;
    }

    /** The logo keyed out of a banner, or null if the banner is not a logo on a plain backdrop. Kept per app and size. */
    private LiquidKey.Logo logo(String id, Drawable banner) {
        String key = id + "|" + banner.getIntrinsicWidth() + "x" + banner.getIntrinsicHeight();
        if (logos.containsKey(key)) return logos.get(key);
        LiquidKey.Logo logo = null;
        try {
            int iw = banner.getIntrinsicWidth(), ih = banner.getIntrinsicHeight();
            float scale = iw > 0 && ih > 0 ? Math.min(1f, 320f / iw) : 1f;      // banners are 16:9, usually 320x180
            int w = Math.max(8, iw > 0 ? Math.round(iw * scale) : 320), h = Math.max(8, ih > 0 ? Math.round(ih * scale) : 180);
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Rect saved = banner.copyBounds();
            banner.setBounds(0, 0, w, h);
            banner.draw(new Canvas(bmp));
            banner.setBounds(saved);
            int[] px = new int[w * h];
            bmp.getPixels(px, 0, w, 0, 0, w, h);
            bmp.recycle();
            logo = LiquidKey.extract(px, w, h);
        } catch (RuntimeException ignored) { }
        logos.put(key, logo);
        return logo;
    }

    /** Forget the keyed logos (an app was installed, updated or removed, so its banner may have changed). */
    public void clearLogoCache() {
        logos.clear();
    }

    private static void roundClip(View v, final int radiusPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        v.setClipToOutline(true);
    }

    /**
     * The Liquid Glass focus language: the glass catches the light (the double bezel, the brighter rim), the card scales
     * 1.03-1.07x and lifts 8-12 dp on a navy shadow, over 180 ms; {@code label} (optional) fades in under it.
     */
    private void focusableLiquid(View card, final LiquidGlass[] layers, final TextView label, final boolean labelAlways) {
        card.setFocusable(true);
        card.setClickable(true);
        if (Build.VERSION.SDK_INT >= 28) {
            card.setOutlineSpotShadowColor(LiquidTokens.SHADOW_TINT);
            card.setOutlineAmbientShadowColor(LiquidTokens.SHADOW_TINT);
        }
        if (label != null) label.setAlpha(labelAlways ? 0.85f : 0f);
        final int ms = t.reducedMotion ? 0 : LiquidTokens.FOCUS_MS;
        card.setOnFocusChangeListener((v, has) -> {
            float s = has && !t.reducedMotion ? t.liquidScale : 1f;
            v.animate().scaleX(s).scaleY(s).translationZ(has ? t.dp(t.liquidLiftDp) : 0).setInterpolator(ease).setDuration(ms).start();
            animateLevel(layers[0], has, ms);
            if (label != null) {
                label.animate().alpha(has ? 1f : (labelAlways ? 0.85f : 0f)).setDuration(ms).start();
                label.setTextColor(has ? Theme.SOFT_WHITE : Theme.withAlpha(Theme.SOFT_WHITE, 0.85f));
            }
        });
    }

    /** Animates a glass surface's focus level to 1 (focused) or 0, replacing any animation still running on it. */
    private void animateLevel(final LiquidGlass glass, boolean focused, int ms) {
        ValueAnimator running = levelAnimators.remove(glass);
        if (running != null) running.cancel();
        if (ms <= 0) {
            glass.setFocusLevel(focused ? 1f : 0f);
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(glass.focusLevel(), focused ? 1f : 0f);
        a.setDuration(ms);
        a.setInterpolator(ease);
        a.addUpdateListener(u -> glass.setFocusLevel((Float) u.getAnimatedValue()));
        levelAnimators.put(glass, a);
        a.start();
    }

    /** The focusable card inside a tile made by {@link #appTile}. */
    public static View cardOf(View tile) {
        return ((ViewGroup) tile).getChildAt(0);
    }

    /**
     * Left and Right move within the current horizontal region: on the first control of a row Left stays put, and on the last
     * one Right does. Without this the window's focus search jumps to whatever is nearest in that direction, which is another
     * region (Right on the last quick action went to the half-visible eighth favorite). The middle controls keep the default.
     */
    public static void lockRowEnds(java.util.List<View> focusables) {
        for (View v : focusables) {
            v.setNextFocusLeftId(View.NO_ID);
            v.setNextFocusRightId(View.NO_ID);
        }
        if (focusables.isEmpty()) return;
        View first = focusables.get(0), last = focusables.get(focusables.size() - 1);
        if (first.getId() == View.NO_ID) first.setId(View.generateViewId());
        if (last.getId() == View.NO_ID) last.setId(View.generateViewId());
        first.setNextFocusLeftId(first.getId());
        last.setNextFocusRightId(last.getId());
    }

    // ------------------------------------------------------------------ chips and nav

    /** Quick-action chip: icon + label on a glass pill. {@code accent} tints the icon cyan (Assistant). */
    public LinearLayout chip(Glyph.Kind icon, String text, boolean accent, View.OnClickListener click) {
        final LinearLayout chip = new LinearLayout(c);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(t.dp(16), 0, t.dp(16), 0);
        final LiquidGlass[] liquid = t.liquid ? t.liquidLayers(LiquidSpec.chip(), 40) : null;
        if (liquid != null) {
            chip.setBackground(liquid[0]);
            chip.setForeground(liquid[1]);
        } else chip.setBackground(t.glass(Theme.R_CHIP, 0.62f));
        final Glyph g = new Glyph(icon, Theme.SOFT_WHITE);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(g);
        chip.addView(iv, new LinearLayout.LayoutParams(t.dp(18), t.dp(18)));
        TextView tv = t.text(c, Theme.T_NAV, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        tv.setText(text);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        tlp.leftMargin = t.dp(9);
        chip.addView(tv, tlp);
        chip.setContentDescription(text);
        if (accent && liquid == null) chip.setBackground(t.outlined(Theme.withAlpha(Theme.PANEL_NAVY, 0.62f), t.accentBlue(0.7f), 1, Theme.R_CHIP));
        final Drawable idle = chip.getBackground();
        chip.setOnClickListener(click);
        if (liquid != null) {
            focusableLiquid(chip, liquid, null, false);
            return chip;
        }
        chip.setFocusable(true);
        chip.setClickable(true);
        if (Build.VERSION.SDK_INT >= 28) {
            chip.setOutlineSpotShadowColor(Theme.ELECTRIC_BLUE);
            chip.setOutlineAmbientShadowColor(Theme.ELECTRIC_BLUE);
        }
        chip.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? t.outlined(0xED555A60, Theme.ELECTRIC_BLUE, 2, Theme.R_CHIP) : idle);
            v.animate().scaleX(has && !t.reducedMotion ? 1.05f : 1f).scaleY(has && !t.reducedMotion ? 1.05f : 1f)
                    .translationZ(has ? t.dp(t.glowDp * 0.6f) : 0).setDuration(t.focusMs()).start();
        });
        return chip;
    }

    /**
     * Replaces a row of chips. A focused chip hands focus to the new chip with the same label (or the first one)
     * before the old chips are removed: removing a focused view sends focus to the first focusable view on screen,
     * which on the TV meant the screen's side rail.
     */
    public static void replaceChips(LinearLayout row, java.util.List<? extends View> chips) {
        View had = row.findFocus();
        String label = had != null ? String.valueOf(had.getContentDescription()) : null;
        int old = row.getChildCount();
        View next = null;
        for (View v : chips) {
            row.addView(v);
            if (next == null && label != null && label.equals(String.valueOf(v.getContentDescription()))) next = v;
        }
        if (had != null && !chips.isEmpty()) (next != null ? next : chips.get(0)).requestFocus();
        row.removeViews(0, old);
    }

    /** Top-navigation item. {@code current} marks the screen you are on. */
    public TextView navItem(String text, boolean current, View.OnClickListener click) {
        final TextView v = t.text(c, Theme.T_NAV, current ? Theme.SOFT_WHITE : Theme.withAlpha(Theme.SOFT_WHITE, 0.82f),
                current ? Theme.FONT_MEDIUM : Theme.FONT_REGULAR);
        v.setText(text);
        v.setGravity(Gravity.CENTER);
        v.setPadding(t.dp(8), t.dp(7), t.dp(8), t.dp(7));
        v.setSingleLine(true);
        v.setFocusable(true);
        v.setClickable(true);
        v.setContentDescription(text);
        final Drawable idle = current ? t.raised(Theme.R_PILL, false) : null;
        v.setBackground(idle);
        v.setOnClickListener(click);
        final int onPill = t.liquid ? Theme.SOFT_WHITE : Theme.DEEP_BLACK;      // graphite's focused pill is silver; Liquid Glass's is milky
        v.setOnFocusChangeListener((view, has) -> {
            view.setBackground(has ? t.raised(Theme.R_PILL, true) : idle);
            v.setTextColor(has ? onPill : Theme.SOFT_WHITE);
            for (Drawable d : v.getCompoundDrawablesRelative()) if (d instanceof Glyph) d.setTint(has ? onPill : Theme.SOFT_WHITE);
        });
        return v;
    }

    // ------------------------------------------------------------------ screens

    /**
     * Standard full screen used by launcher panels and the Streaming/Web activities: dark gradient, header
     * (logo, title, clock), then {@code content}. {@code shade} 1 = standard; lower shows more wallpaper.
     */
    public FrameLayout screen(String title, View content, float shade) {
        FrameLayout f = new FrameLayout(c);
        f.setClipChildren(false);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{
                Theme.withAlpha(Theme.DEEP_BLACK, 0.94f * shade), Theme.withAlpha(Theme.MIDNIGHT_NAVY, 0.88f * shade * shade),
                Theme.withAlpha(Theme.DEEP_BLACK, 0.95f)});
        f.setBackground(bg);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(t.safeX(), t.safeY(), t.safeX(), 0);

        FrameLayout header = new FrameLayout(c);
        LinearLayout left = new LinearLayout(c);
        left.setGravity(Gravity.CENTER_VERTICAL);
        left.addView(new PulseLogo(c, t.dp(26)));
        View sep = new View(c);
        sep.setBackgroundColor(Theme.withAlpha(Theme.SOFT_WHITE, 0.22f));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Math.max(1, t.dp(1)), t.dp(22));
        sl.leftMargin = t.dp(14);
        sl.rightMargin = t.dp(16);
        left.addView(sep, sl);
        TextView h = t.text(c, Theme.T_HEADING, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        h.setText(title);
        left.addView(h);
        header.addView(left, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.START | Gravity.CENTER_VERTICAL));
        TextClock clock = new TextClock(c);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        t.style(clock, 26, Theme.SOFT_WHITE, Theme.FONT_LIGHT);
        clock.setGravity(Gravity.END);
        clock.setSingleLine(true);
        // Fixed width: content-sized clocks were clipped on this TV after the time changed.
        header.addView(clock, new FrameLayout.LayoutParams(t.dp(140), WRAP, Gravity.END | Gravity.CENTER_VERTICAL));
        col.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        clp.topMargin = t.dp(22);
        col.addView(content, clp);
        f.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));
        return f;
    }

    /** Side-rail entry (icon + label), styled like the Settings categories. {@link #paintRail} shows the state. */
    public LinearLayout railItem(Glyph.Kind icon, String text) {
        LinearLayout item = new LinearLayout(c);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(t.dp(14), t.dp(11), t.dp(14), t.dp(11));
        item.setFocusable(true);
        item.setClickable(true);
        item.setContentDescription(text);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(new Glyph(icon, Theme.SOFT_WHITE));
        item.addView(iv, new LinearLayout.LayoutParams(t.dp(18), t.dp(18)));
        TextView l = t.text(c, Theme.T_BODY + 1, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        l.setText(text);
        LinearLayout.LayoutParams ll = wrap();
        ll.leftMargin = t.dp(12);
        item.addView(l, ll);
        return item;
    }

    public void paintRail(View item, boolean selected) {
        if (item.isFocused()) item.setBackground(t.outlined(Theme.withAlpha(Theme.ELECTRIC_BLUE, 0.3f), Theme.ELECTRIC_BLUE, 1.5f, 10));
        else if (selected) item.setBackground(t.rounded(t.accentBlue(0.22f), 10));
        else item.setBackground(null);
    }

    /**
     * Honest state for a screen without content (empty, unavailable, error): icon, title, explanation. Used
     * instead of placeholder tiles, so nothing on screen pretends to be playable or loaded.
     */
    public LinearLayout stateCard(Glyph.Kind icon, int iconColor, String title, String body) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(t.dp(28), t.dp(26), t.dp(28), t.dp(26));
        card.setBackground(t.glass(Theme.R_PANEL, 0.85f));
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(new Glyph(icon, iconColor));
        card.addView(iv, new LinearLayout.LayoutParams(t.dp(34), t.dp(34)));
        TextView h = t.text(c, Theme.T_TITLE + 3, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        h.setText(title);
        LinearLayout.LayoutParams hl = wrap();
        hl.topMargin = t.dp(14);
        card.addView(h, hl);
        TextView b = t.text(c, Theme.T_BODY, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        b.setText(body);
        b.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(MATCH, WRAP);
        bl.topMargin = t.dp(8);
        card.addView(b, bl);
        return card;
    }

    public LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(WRAP, WRAP);
    }
}
