package tv.calmhome.launcher.ui;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import tv.calmhome.launcher.core.DownloadSummary;
import tv.calmhome.launcher.core.HeroState;
import tv.calmhome.launcher.core.MotionTokens;
import tv.calmhome.launcher.data.AppArt;

/**
 * The centre of Home, in the greeting's own place and bounds. It holds the greeting exactly as it was, and, over it in the same
 * region, the live download, a short notice and (only when turned on) a featured installed app. Its size is the greeting's: the
 * other states fill that region and never make it larger, so nothing else on Home moves. States change by a crossfade
 * ({@link MotionTokens#HERO_FADE_MS}); nothing here takes focus.
 */
public final class HeroView extends FrameLayout {
    private final View greeting;
    private final LinearLayout download, featured;
    private final TextView noticeText, downloadTitle, downloadStatus, featuredName;
    private final ProgressRingView ring;
    private final ImageView featuredArt;
    private final int fadeMs;
    private final boolean reducedMotion;
    private HeroState.Kind showing = HeroState.Kind.GREETING;
    private boolean greetingEnabled = true;

    public HeroView(Context c, Theme t, View greetingBox) {
        super(c);
        setClipChildren(false);
        setFocusable(false);
        reducedMotion = t.reducedMotion;
        fadeMs = MotionTokens.of(MotionTokens.HERO_FADE_MS, t.reducedMotion);
        greeting = greetingBox;
        addView(greeting, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));   // decides the region's size

        // Active download: a slim ring beside the app's name and the real status line.
        download = new LinearLayout(c);
        download.setGravity(Gravity.CENTER);
        ring = new ProgressRingView(c, t.dp(3), t.reducedMotion);
        download.addView(ring, new LinearLayout.LayoutParams(t.dp(46), t.dp(46)));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        downloadTitle = t.text(c, Theme.T_TITLE, Theme.SOFT_WHITE, Theme.FONT_LIGHT);
        downloadTitle.setSingleLine(true);
        t.overPhoto(downloadTitle);
        downloadStatus = t.text(c, Theme.T_BODY, Theme.withAlpha(Theme.SOFT_WHITE, 0.82f), Theme.FONT_LIGHT);
        downloadStatus.setSingleLine(true);
        t.overPhoto(downloadStatus);
        words.addView(downloadTitle);
        words.addView(downloadStatus);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wl.leftMargin = t.dp(16);
        download.addView(words, wl);
        addOverlay(download);

        // A notice: one line, the greeting's own light type.
        noticeText = t.text(c, Theme.T_TITLE, Theme.SOFT_WHITE, Theme.FONT_LIGHT);
        noticeText.setGravity(Gravity.CENTER);
        noticeText.setSingleLine(true);
        t.overPhoto(noticeText);
        addOverlay(noticeText);

        // A featured installed app: its own artwork, drifting very slowly (a transform: nothing is decoded or re-drawn per frame).
        featured = new LinearLayout(c);
        featured.setGravity(Gravity.CENTER);
        featuredArt = new ImageView(c);
        featuredArt.setScaleType(ImageView.ScaleType.CENTER);
        featured.addView(featuredArt, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        featuredName = t.text(c, Theme.T_BODY, Theme.withAlpha(Theme.SOFT_WHITE, 0.85f), Theme.FONT_LIGHT);
        featuredName.setSingleLine(true);
        t.overPhoto(featuredName);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fl.leftMargin = t.dp(18);
        featured.addView(featuredName, fl);
        addOverlay(featured);
    }

    private void addOverlay(View v) {
        v.setAlpha(0f);
        v.setVisibility(INVISIBLE);
        addView(v, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));   // the greeting's region, never more
    }

    /** False when the viewer turned the greeting off: the region stays (other states still use it) but the greeting is hidden. */
    public void setGreetingEnabled(boolean on) {
        greetingEnabled = on;
        if (showing == HeroState.Kind.GREETING) show(greeting, on);
    }

    public HeroState.Kind showing() {
        return showing;
    }

    /** The live download's details (called with each real sample). */
    public void setDownload(DownloadSummary s) {
        if (s == null || s.shown == null) return;
        downloadTitle.setText(s.shown.name);
        StringBuilder line = new StringBuilder(DownloadSummary.statusText(s.status));
        if (s.percent >= 0) line.append("  ·  ").append(s.percent).append('%');
        String speed = DownloadSummary.speed(s.bytesPerSecond);
        if (speed != null) line.append("  ·  ").append(speed);
        String left = DownloadSummary.timeLeft(s.secondsLeft);
        if (left != null) line.append("  ·  ").append(left);
        if (s.active > 1) line.append("  ·  ").append(s.active).append(" in the queue");
        downloadStatus.setText(line);
        ring.setPercent(s.status == DownloadSummary.Status.DOWNLOADING ? s.percent : -1);
        download.setContentDescription(s.shown.name + ". " + line);
    }

    public void setNotice(String text) {
        noticeText.setText(text);
        noticeText.setContentDescription(text);
    }

    /** The featured app's own artwork and name (null clears it). */
    public void setFeatured(AppArt.Art art, String name) {
        featuredArt.setImageDrawable(art == null ? null : new BitmapDrawable(getResources(), art.bitmap));
        featuredName.setText(name == null ? "" : "Featured  ·  " + name);
    }

    /** Crossfades to {@code kind}. */
    public void showKind(HeroState.Kind kind) {
        if (kind == showing) return;
        showing = kind;
        show(greeting, kind == HeroState.Kind.GREETING && greetingEnabled);
        show(download, kind == HeroState.Kind.ACTIVE_DOWNLOAD);
        show(noticeText, kind == HeroState.Kind.NOTIFICATION);
        show(featured, kind == HeroState.Kind.FEATURED);
        if (kind == HeroState.Kind.FEATURED) drift();
        else featuredArt.animate().cancel();
    }

    private void show(View v, boolean on) {
        v.animate().cancel();
        if (on) {
            v.setVisibility(VISIBLE);
            if (fadeMs == 0) v.setAlpha(1f);
            else v.animate().alpha(1f).setDuration(fadeMs).start();
        } else if (v.getVisibility() == VISIBLE) {
            if (fadeMs == 0) { v.setAlpha(0f); v.setVisibility(INVISIBLE); }
            else v.animate().alpha(0f).setDuration(fadeMs).withEndAction(() -> v.setVisibility(INVISIBLE)).start();
        }
    }

    /** The slow Ken Burns drift: 1.0 to 1.03 and a few pixels across, over the featured turn. Off under reduced motion. */
    private void drift() {
        featuredArt.animate().cancel();
        featuredArt.setScaleX(1f);
        featuredArt.setScaleY(1f);
        featuredArt.setTranslationX(0f);
        if (reducedMotion) return;
        featuredArt.animate().scaleX(1.03f).scaleY(1.03f).translationX(-getResources().getDisplayMetrics().density * 4f)
                .setDuration(HeroState.FEATURED_TURN_MS).setInterpolator(new LinearInterpolator()).start();
    }

    // For tests.
    public View greetingLayer() { return greeting; }
    public View downloadLayer() { return download; }
    public View noticeLayer() { return noticeText; }
    public View featuredLayer() { return featured; }
    public ProgressRingView ring() { return ring; }
    public CharSequence downloadStatusText() { return downloadStatus.getText(); }
}
