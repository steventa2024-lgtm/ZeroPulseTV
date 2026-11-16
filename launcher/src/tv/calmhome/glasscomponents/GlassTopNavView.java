package tv.calmhome.glasscomponents;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import java.util.List;

/** Native TV navigation: an outer glass rail and real focusable button children.
 * Selected page is a small dot; remote focus is the inner glass pill. They are
 * independent. Both surfaces sample the original wallpaper, preventing double tint.
 * Defaults adapt the approved material: 72dp rail / 0.65 unit and 48dp focus / 0.5 unit.
 */
public final class GlassTopNavView extends FrameLayout {
    public interface Listener { void onItemActivated(String id); }
    public static final class Item {
        public final String id,label;
        public Item(String id,String label) { if(id==null||label==null) throw new IllegalArgumentException("id and label required"); this.id=id; this.label=label; }
    }
    private final Surface rail;
    private final LinearLayout row;
    private GraphiteGlassRenderer.Backdrop focusBackdrop;
    private View anchor;
    private String selectedId;
    private float labelSp = 15;
    private float buttonDp = 48, buttonWidthDp = 0;
    private List<Item> lastItems;
    private Listener listener;
    /** Adaptation (additive, premium interactions): the focus glides between destinations instead of jumping (see {@link Glide}). */
    private final Glide glide;
    private Button lastFocused;
    private long lostAt=Long.MIN_VALUE;
    private boolean reducedMotion;
    public static final int GLIDE_MS=tv.calmhome.launcher.core.MotionTokens.PILL_GLIDE_MS;
    public GlassTopNavView(Context context) {
        super(context); setFocusable(false); setClipChildren(false);
        rail=new Surface(context,0.65f); rail.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(rail,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
        row=new LinearLayout(context); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams lp=new LayoutParams(LayoutParams.MATCH_PARENT,dp(48),Gravity.CENTER);
        lp.leftMargin=dp(12); lp.rightMargin=dp(12);
        glide=new Glide(context); addView(glide,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));   // between the rail and the buttons: under the labels
        addView(row,lp);
    }
    /** Adaptation (additive): no glide, the focus pill changes at once (reduced motion). */
    public void setReducedMotion(boolean reduced) { reducedMotion=reduced; }
    /** For tests and diagnostics: 0..1 while the focus is gliding, -1 when it is not. */
    public float glideProgress() { return glide.running()?glide.t:-1f; }
    /** UI thread. Prepare rail frost=2*.65*density*cacheScale and focus frost=2*.5*density*cacheScale. */
    public void bindBackdrops(GraphiteGlassRenderer.Backdrop railBackdrop,GraphiteGlassRenderer.Backdrop focusedBackdrop,View wallpaperAnchor) {
        rail.bindBackdrop(railBackdrop,wallpaperAnchor); focusBackdrop=focusedBackdrop; anchor=wallpaperAnchor;
        for(int i=0;i<row.getChildCount();i++) ((Button)row.getChildAt(i)).bindBackdrop(focusBackdrop,anchor);
    }
    public void setItems(List<Item> items,Listener listener) {
        if(items==null) throw new IllegalArgumentException("items required");
        this.listener=listener; lastItems=items; row.removeAllViews();
        java.util.HashSet<String> ids=new java.util.HashSet<>();
        for(Item item:items) {
            if(item==null||!ids.add(item.id)) throw new IllegalArgumentException("items need unique ids");
            Button button=new Button(getContext(),item); button.bindBackdrop(focusBackdrop,anchor);
            LinearLayout.LayoutParams lp=buttonWidthDp>0?new LinearLayout.LayoutParams(dp(buttonWidthDp),dp(buttonDp))
                    :new LinearLayout.LayoutParams(0,dp(buttonDp),1f);
            lp.leftMargin=dp(3); lp.rightMargin=dp(3);
            row.addView(button,lp);
        }
        lockEdges();
        updateSelection();
    }
    /**
     * ZeroPulse adaptation: Left on the first destination and Right on the last stay on the rail. Left and Right move within the
     * current horizontal region; without this, Right on the last button jumped into the app row and Left on the first one into its first tile.
     */
    private void lockEdges() {
        View first=null,last=null;
        for(int i=0;i<row.getChildCount();i++) { View b=row.getChildAt(i); b.setNextFocusLeftId(View.NO_ID); b.setNextFocusRightId(View.NO_ID); if(b.isFocusable()) { if(first==null) first=b; last=b; } }
        if(first==null) return;
        if(first.getId()==View.NO_ID) first.setId(View.generateViewId());
        if(last.getId()==View.NO_ID) last.setId(View.generateViewId());
        first.setNextFocusLeftId(first.getId()); last.setNextFocusRightId(last.getId());
    }
    /**
     * Adaptation (additive): the focus pill's height and each button's width in dp (48 and a share of the rail by default as supplied).
     * A fixed width lets the rail wrap its buttons instead of spreading them across the header; give the view a matching height.
     */
    public void setMetrics(float buttonHeightDp,float buttonWidthDp) {
        if(!Float.isFinite(buttonHeightDp)||buttonHeightDp<24||buttonHeightDp>72) throw new IllegalArgumentException("button height 24..72dp");
        if(!Float.isFinite(buttonWidthDp)||buttonWidthDp<0||buttonWidthDp>240) throw new IllegalArgumentException("button width 0..240dp (0 shares the rail)");
        buttonDp=buttonHeightDp; this.buttonWidthDp=buttonWidthDp;
        ((LayoutParams)row.getLayoutParams()).height=dp(buttonDp);
        if(buttonWidthDp>0) { ((LayoutParams)row.getLayoutParams()).width=LayoutParams.WRAP_CONTENT; }
        row.requestLayout();
        if(lastItems!=null) setItems(lastItems,listener);
    }
    /** With fixed button widths the rail is exactly as wide as its buttons (the rail's own glass must not claim the whole header). */
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        if(buttonWidthDp>0&&MeasureSpec.getMode(widthSpec)!=MeasureSpec.EXACTLY) {
            int wanted=dp(24)+row.getChildCount()*dp(buttonWidthDp+6);
            int available=MeasureSpec.getMode(widthSpec)==MeasureSpec.UNSPECIFIED?wanted:MeasureSpec.getSize(widthSpec);
            widthSpec=MeasureSpec.makeMeasureSpec(Math.min(wanted,available),MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthSpec,heightSpec);
    }
    /** Changes page selection only; does not move D-pad focus or invoke callbacks. */
    public void setSelectedId(String id) { selectedId=id; updateSelection(); }
    public String getSelectedId() { return selectedId; }
    public boolean requestFocusOn(String id) {
        for(int i=0;i<row.getChildCount();i++) { Button b=(Button)row.getChildAt(i); if(b.item.id.equals(id)&&b.isEnabled()) return b.requestFocus(); } return false;
    }
    public void setItemEnabled(String id,boolean enabled) {
        for(int i=0;i<row.getChildCount();i++) { Button b=(Button)row.getChildAt(i); if(b.item.id.equals(id)) { b.setEnabled(enabled); b.setFocusable(enabled); if(!enabled)b.clearFocus(); } }
        lockEdges();
    }
    private void updateSelection() { for(int i=0;i<row.getChildCount();i++) { Button b=(Button)row.getChildAt(i); b.setSelected(b.item.id.equals(selectedId)); b.invalidate(); } }
    private int dp(float value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    /** 15 sp in pixels. (Adaptation: the kit read the deprecated DisplayMetrics.scaledDensity; the project builds with -Werror.) */
    private float sp15() { return android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, labelSp, getResources().getDisplayMetrics()); }
    // ------------------------------------------------------------------ Adaptation (additive): live status on a destination
    private final Paint statusPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF statusRect=new RectF();
    private float spin;
    private android.animation.ValueAnimator spinAnim, ackAnim;
    private Button button(String id) { for(int i=0;i<row.getChildCount();i++) { Button b=(Button)row.getChildAt(i); if(b.item.id.equals(id)) return b; } return null; }
    /**
     * A small ring in {@code id}'s corner: {@code fraction} 0..1 is real progress, a negative value means working with no known size
     * (a short arc turns), NaN removes it. It draws inside the button; the rail, the label and the accessibility label are unchanged.
     */
    public void setItemProgress(String id,float fraction) {
        Button b=button(id); if(b==null) return;
        b.progress=fraction; b.invalidate();
        boolean turning=false; for(int i=0;i<row.getChildCount();i++) { float p=((Button)row.getChildAt(i)).progress; if(!Float.isNaN(p)&&p<0) turning=true; }
        if(turning&&!reducedMotion&&spinAnim==null) {
            android.animation.ValueAnimator a=android.animation.ValueAnimator.ofFloat(0f,360f); a.setDuration(1400); a.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            a.setInterpolator(new android.view.animation.LinearInterpolator());
            a.addUpdateListener(u->{ spin=(Float)u.getAnimatedValue(); for(int i=0;i<row.getChildCount();i++) { Button x=(Button)row.getChildAt(i); if(!Float.isNaN(x.progress)&&x.progress<0) x.invalidate(); } });
            spinAnim=a; a.start();
        } else if(!turning&&spinAnim!=null) { spinAnim.cancel(); spinAnim=null; }
    }
    public float itemProgress(String id) { Button b=button(id); return b==null?Float.NaN:b.progress; }
    /** A brief acknowledgment in {@code id}'s corner: green for done, amber for failed; it fades by itself. */
    public void acknowledge(String id,boolean ok) {
        final Button b=button(id); if(b==null) return;
        b.ackOk=ok; if(ackAnim!=null) ackAnim.cancel();
        if(reducedMotion) { b.ack=0f; b.invalidate(); return; }
        b.ack=1f; b.invalidate();                                                  // shown at once, then it fades
        android.animation.ValueAnimator a=android.animation.ValueAnimator.ofFloat(1f,0f); a.setDuration(tv.calmhome.launcher.core.MotionTokens.ACKNOWLEDGE_MS*3);
        a.addUpdateListener(u->{ b.ack=(Float)u.getAnimatedValue(); b.invalidate(); });
        ackAnim=a; a.start();
    }
    public float acknowledgment(String id) { Button b=button(id); return b==null?0f:b.ack; }
    /** A discreet dot in {@code id}'s corner (What's New): the label and position are unchanged. */
    public void setItemBadge(String id,boolean on) { Button b=button(id); if(b!=null) { b.badge=on; b.invalidate(); } }
    public boolean itemBadge(String id) { Button b=button(id); return b!=null&&b.badge; }
    @Override protected void onDetachedFromWindow() { if(spinAnim!=null) spinAnim.cancel(); spinAnim=null; if(ackAnim!=null) ackAnim.cancel(); ackAnim=null; super.onDetachedFromWindow(); }

    /**
     * Adaptation (additive): label size in sp, 15 by default as supplied. A six-destination header beside a clock needs a
     * smaller label for "Downloads" to fit its button instead of being cut short with an ellipsis.
     */
    public void setLabelSp(float sp) {
        if (!Float.isFinite(sp) || sp < 10 || sp > 24) throw new IllegalArgumentException("label size 10..24sp");
        labelSp = sp;
        for(int i=0;i<row.getChildCount();i++) { Button b=(Button)row.getChildAt(i); b.relabel(); }
    }

    /**
     * Adaptation (additive, premium interactions): while the remote moves focus from one destination to the next, a light capsule
     * slides from the old button to the new one, taking the new button's width, and fades as the new button's own glass pill fades
     * in. It is a translucent highlight in the pill's own geometry (a faint fill and rim), never an opaque second pill, and it draws
     * nothing at all when no glide is running. It moves by drawing, not by moving a glass surface, so no glass is re-rendered.
     */
    static final class Glide extends View {
        static final android.view.animation.Interpolator EASE=new android.view.animation.PathInterpolator(0.2f,0.9f,0.25f,1f);   // eased, no overshoot
        private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG), rim=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF from=new RectF(), to=new RectF(), now=new RectF();
        private android.animation.ValueAnimator anim;
        float t=1f;
        Glide(Context c) {
            super(c); setFocusable(false); setClickable(false); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            fill.setColor(Color.argb(46,235,242,250)); rim.setStyle(Paint.Style.STROKE); rim.setStrokeWidth(Math.max(1f,c.getResources().getDisplayMetrics().density));
            rim.setColor(Color.argb(110,235,242,250));
        }
        boolean running() { return anim!=null&&anim.isRunning(); }
        void start(float fl,float ft,float fr,float fb,float tl,float tt,float tr,float tb) {
            // From wherever the capsule is now, if a glide is still running (a rapid run of presses retargets, never queues).
            if(running()) from.set(now); else from.set(fl,ft,fr,fb);
            to.set(tl,tt,tr,tb);
            if(anim!=null) anim.cancel();
            android.animation.ValueAnimator a=android.animation.ValueAnimator.ofFloat(0f,1f);
            a.setDuration(GLIDE_MS); a.setInterpolator(EASE);
            a.addUpdateListener(u->{ t=(Float)u.getAnimatedValue(); invalidate(); });
            a.addListener(new android.animation.AnimatorListenerAdapter() { @Override public void onAnimationEnd(android.animation.Animator x) { t=1f; invalidate(); } });
            anim=a; t=0f; a.start();
        }
        @Override protected void onDetachedFromWindow() { if(anim!=null) anim.cancel(); anim=null; super.onDetachedFromWindow(); }
        @Override protected void onDraw(Canvas canvas) {
            if(!running()) return;
            now.set(from.left+(to.left-from.left)*t,from.top+(to.top-from.top)*t,from.right+(to.right-from.right)*t,from.bottom+(to.bottom-from.bottom)*t);
            float inset=getResources().getDisplayMetrics().density, r=Math.max(0f,now.height()/2f-inset);
            int a=Math.round(255*(1f-t));                                          // fades as the destination's own glass arrives
            fill.setAlpha(Math.round(46*(1f-t))); rim.setAlpha(Math.round(110*(1f-t)));
            if(a<=0) return;
            RectF shape=new RectF(now.left+inset,now.top+inset,now.right-inset,now.bottom-inset);
            canvas.drawRoundRect(shape,r,r,fill); canvas.drawRoundRect(shape,r,r,rim);
        }
    }

    private static class Surface extends GraphiteGlassRenderer.CachedView {
        private final float unit;
        private final RectF shape=new RectF();
        Surface(Context context,float unit) { super(context); this.unit=unit; setFocusable(false); }
        @Override protected void buildSilhouette(Path path,int w,int h) {
            materialUnitPx=dp(unit); bevelPx=Math.min(23*materialUnitPx,h*0.22f);
            float inset=dp(1); shape.set(inset,inset,Math.max(inset,w-inset),Math.max(inset,h-inset));
            path.addRoundRect(shape,Math.max(0,h/2f-inset),Math.max(0,h/2f-inset),Path.Direction.CW);
        }
    }
    private final class Button extends Surface {
        final Item item;
        final Paint.FontMetrics metrics=new Paint.FontMetrics();
        String visibleLabel;
        Button(Context context,Item item) {
            super(context,0.5f); this.item=item; visibleLabel=item.label; setFocusable(true); setClickable(true); setContentDescription(item.label);
            setOnClickListener(new OnClickListener() { @Override public void onClick(View view) {
                if(!isEnabled())return; setSelectedId(Button.this.item.id); if(listener!=null)listener.onItemActivated(Button.this.item.id);
            } });
        }
        @Override protected boolean showMaterial() { return isFocused()||(isSelected()&&!row.hasFocus()); }
        /** Adaptation (additive): how much of this button's glass shows, 0..1; it fades in while the glide arrives. */
        float reveal=1f; android.animation.ValueAnimator revealAnim;
        @Override protected float presence() { return reveal; }
        @Override protected void onFocusChanged(boolean gain,int direction,Rect previous) {
            super.onFocusChanged(gain,direction,previous);
            long now=android.os.SystemClock.uptimeMillis();
            if(!gain) { lastFocused=this; lostAt=now; }
            else {
                Button from=lastFocused;
                // A glide only for a direct move between two destinations of this rail (focus left one and arrived here at once).
                if(!reducedMotion&&from!=null&&from!=this&&from.getParent()==row&&now-lostAt<50&&isAttachedToWindow()) glideFrom(from);
                lastFocused=this;
            }
            for(int i=0;i<row.getChildCount();i++) row.getChildAt(i).invalidate();
        }
        private void glideFrom(Button from) {
            int ox=row.getLeft(),oy=row.getTop();                               // the glide spans this view; the buttons sit in the row
            glide.start(ox+from.getLeft(),oy+from.getTop(),ox+from.getRight(),oy+from.getBottom(),ox+getLeft(),oy+getTop(),ox+getRight(),oy+getBottom());
            if(revealAnim!=null) revealAnim.cancel();
            reveal=0f; invalidate();
            android.animation.ValueAnimator a=android.animation.ValueAnimator.ofFloat(0f,1f);
            a.setDuration(GLIDE_MS); a.setInterpolator(Glide.EASE);
            a.addUpdateListener(u->{ reveal=(Float)u.getAnimatedValue(); invalidate(); });
            a.addListener(new android.animation.AnimatorListenerAdapter() { @Override public void onAnimationCancel(android.animation.Animator x) { reveal=1f; invalidate(); } });
            revealAnim=a; a.start();
        }
        @Override protected void onDetachedFromWindow() { if(revealAnim!=null) revealAnim.cancel(); revealAnim=null; reveal=1f; super.onDetachedFromWindow(); }
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
            super.onSizeChanged(w,h,oldw,oldh);
            relabel();
        }
        void relabel() {
            android.text.TextPaint text=new android.text.TextPaint(foregroundPaint);
            text.setTextSize(sp15());
            visibleLabel=android.text.TextUtils.ellipsize(item.label,text,Math.max(0,getWidth()-dp(12)),android.text.TextUtils.TruncateAt.END).toString();
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); foregroundPaint.setColor(Color.rgb(242,246,250));
            foregroundPaint.setAlpha(isEnabled()?(isPressed()?180:255):100); foregroundPaint.setTextSize(sp15());
            foregroundPaint.setTextAlign(Paint.Align.CENTER); foregroundPaint.getFontMetrics(metrics);
            float baseline=getHeight()/2f-(metrics.ascent+metrics.descent)/2f-dp(2);
            canvas.drawText(visibleLabel,getWidth()/2f,baseline,foregroundPaint);
            if(isSelected()) canvas.drawCircle(getWidth()/2f,getHeight()-dp(7),dp(1.6f),foregroundPaint);
            drawStatus(canvas);
        }
        /** Adaptation (additive): live status in the button's top-right corner, inside its bounds (the rail's size never changes). */
        float progress=Float.NaN;   // NaN: none; -1: working, size unknown; 0..1: real aggregate progress
        float ack; boolean ackOk; boolean badge;
        private void drawStatus(Canvas canvas) {
            float r=dp(3.5f), cx=getWidth()-dp(10), cy=dp(9);
            if(!Float.isNaN(progress)) {
                statusPaint.setStyle(Paint.Style.STROKE); statusPaint.setStrokeWidth(dp(1.5f)); statusPaint.setStrokeCap(Paint.Cap.ROUND);
                statusRect.set(cx-r,cy-r,cx+r,cy+r);
                statusPaint.setColor(Color.argb(70,233,238,243)); canvas.drawOval(statusRect,statusPaint);
                statusPaint.setColor(Color.rgb(233,244,251));
                if(progress<0) canvas.drawArc(statusRect,-90+spin,90,false,statusPaint);
                else canvas.drawArc(statusRect,-90,Math.max(8f,360f*progress),false,statusPaint);
            } else if(ack>0.001f) {
                statusPaint.setStyle(Paint.Style.FILL);
                statusPaint.setColor(ackOk?Color.rgb(108,220,160):Color.rgb(240,180,90)); statusPaint.setAlpha(Math.round(255*ack));
                canvas.drawCircle(cx,cy,r,statusPaint);
            } else if(badge) {
                statusPaint.setStyle(Paint.Style.FILL); statusPaint.setColor(Color.rgb(95,211,243));
                canvas.drawCircle(cx,cy,dp(2.6f),statusPaint);
            }
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.Button"); info.setSelected(isSelected());
            if(android.os.Build.VERSION.SDK_INT>=30) info.setStateDescription(stateText());      // the label itself is never replaced
        }
        String stateText() {
            if(!Float.isNaN(progress)) return progress<0?"Downloading":"Downloading, "+Math.round(progress*100)+" percent";
            if(badge) return "New";
            return null;
        }
    }
}
