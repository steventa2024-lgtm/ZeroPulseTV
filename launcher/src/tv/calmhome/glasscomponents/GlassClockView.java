package tv.calmhome.glasscomponents;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import java.util.Calendar;
import java.util.Locale;

/** Noninteractive clock: the actual digit silhouettes are glass. No rectangular
 * glass card. Canonical outlines preserve counters and tabular numeral advances.
 * The emphasized material is an aesthetic endpoint, not fake remote focus.
 */
public final class GlassClockView extends GraphiteGlassRenderer.CachedView {
    private final Handler clockHandler=new Handler(Looper.getMainLooper());
    private int hour,minute;
    private boolean use24Hour=true,autoUpdate,outlineContrast=true;
    /** Adaptation (additive): a crisp outline traced along each numeral's edge, so the glass digits stay readable over any wallpaper. */
    private final Paint rimLight=new Paint(Paint.ANTI_ALIAS_FLAG),rimShade=new Paint(Paint.ANTI_ALIAS_FLAG);
    /**
     * Adaptation (additive): the numerals' interior. The renderer's glass is almost the sky behind it (3005 measured 85.8 against 81.3),
     * so a numeral read as an outline over nothing. The interior now carries a translucent frosted fill, lighter toward the top, and a
     * soft shade just inside its lower-right edge: the wallpaper still shows through, and the digit has body and depth.
     */
    private final Paint interior=new Paint(Paint.ANTI_ALIAS_FLAG),innerShade=new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean interiorFill=true;
    private float digitHeightDp=100,suffixX,suffixBaseline;
    private String digits="",period="";
    /** Where the time comes from: the system clock unless the host gives another source. The clock never holds a time of its own. */
    private java.util.function.LongSupplier time=System::currentTimeMillis;
    private Runnable onMinute;
    private Calendar now() { Calendar c=Calendar.getInstance(); c.setTimeInMillis(time.getAsLong()); return c; }
    private final Runnable tick=new Runnable() { @Override public void run() {
        if(!autoUpdate||!isAttachedToWindow()) return;
        Calendar now=now(); setTime(now.get(Calendar.HOUR_OF_DAY),now.get(Calendar.MINUTE));
        if(onMinute!=null) onMinute.run();
        clockHandler.postDelayed(this,60000-System.currentTimeMillis()%60000+30);
    } };
    /** Starts at the real time (never a placeholder), then follows the system clock while attached with {@link #setAutoUpdate}. */
    public GlassClockView(Context context) {
        super(context); setFocusable(false); setClickable(false);
        Calendar now=now(); hour=now.get(Calendar.HOUR_OF_DAY); minute=now.get(Calendar.MINUTE);
        rimLight.setStyle(Paint.Style.STROKE); rimLight.setStrokeJoin(Paint.Join.ROUND);
        rimShade.setStyle(Paint.Style.STROKE); rimShade.setStrokeJoin(Paint.Join.ROUND); rimShade.setColor(0x66000000);
        interior.setStyle(Paint.Style.FILL);
        innerShade.setStyle(Paint.Style.STROKE); innerShade.setStrokeJoin(Paint.Join.ROUND); innerShade.setColor(0x4D06101E);
        updateText();
    }
    /** The translucent interior and its inner shade (on by default); off gives the 3005 look, for comparison. */
    public void setInteriorFill(boolean value) { if(interiorFill!=value) { interiorFill=value; invalidate(); } }
    public boolean hasInteriorFill() { return interiorFill; }
    /** Adaptation (additive): the host's time source (the system clock by default); the clock reads it now and at every minute. */
    public void setTimeSource(java.util.function.LongSupplier source) {
        time=source==null?System::currentTimeMillis:source;
        Calendar now=now(); setTime(now.get(Calendar.HOUR_OF_DAY),now.get(Calendar.MINUTE));
    }
    /** Called after the clock moves to a new minute (a date line can follow it). */
    public void setOnMinuteChange(Runnable listener) { onMinute=listener; }
    /** The shown hour (0..23) and minute, for tests and diagnostics. */
    public int getHour() { return hour; }
    public int getMinute() { return minute; }
    /** The numerals' outline: a thin bright edge, lit from the upper left, over a faint dark one. On by default. */
    public void setOutlineContrast(boolean value) { if(outlineContrast!=value) { outlineContrast=value; invalidate(); } }
    public boolean hasOutlineContrast() { return outlineContrast; }
    public void setTime(int hour24,int minute) {
        if(hour24<0||hour24>23||minute<0||minute>59) throw new IllegalArgumentException("hour 0..23, minute 0..59");
        if(this.hour==hour24&&this.minute==minute) return;
        this.hour=hour24; this.minute=minute; updateText();
    }
    public void setUse24Hour(boolean value) { if(use24Hour!=value) { use24Hour=value; updateText(); } }
    public void setDigitHeightDp(float value) {
        if(!Float.isFinite(value)||value<24||value>180) throw new IllegalArgumentException("digit height 24..180dp");
        digitHeightDp=value; updateText();
    }
    /** Set false before manually driving setTime. No scheduling while detached. */
    public void setAutoUpdate(boolean value) { autoUpdate=value; clockHandler.removeCallbacks(tick); if(value&&isAttachedToWindow()) tick.run(); }
    /** Call after layout when preparing the matching 2*unit frost variant. */
    public float getMaterialUnitPx() { return materialUnitPx; }
    @Override protected boolean emphasized() { return true; }
    private void updateText() {
        digits=use24Hour?String.format(Locale.ROOT,"%02d:%02d",hour,minute)
                :String.format(Locale.ROOT,"%02d:%02d",hour%12==0?12:hour%12,minute);
        period=use24Hour?"":hour<12?"AM":"PM";
        setContentDescription(digits+(period.isEmpty()?"":" "+period));
        silhouette.rewind(); if(getWidth()>0&&getHeight()>0) buildSilhouette(silhouette,getWidth(),getHeight());
        dirtyMaterial(); requestLayout();
    }
    private float unitsWidth() { float width=0; for(int i=0;i<digits.length();i++) width+=ClockGlyphs.advance(digits.charAt(i)); return width; }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int width=(int)Math.ceil(unitsWidth()*dp(digitHeightDp)/100+dp(period.isEmpty()?8:58));
        int height=(int)Math.ceil(dp(digitHeightDp+8));
        setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(height,heightSpec));
    }
    @Override protected void buildSilhouette(Path path,int width,int height) {
        float padding=dp(4), reserve=period.isEmpty()?0:dp(50);
        float cap=Math.max(1,Math.min(dp(digitHeightDp),Math.min(height-2*padding,(width-2*padding-reserve)*100/unitsWidth())));
        materialUnitPx=cap/100; bevelPx=Math.min(23*materialUnitPx,cap*0.075f);
        path.setFillType(Path.FillType.WINDING);
        float x=padding,y=(height-cap)/2;
        for(int i=0;i<digits.length();i++) { char c=digits.charAt(i); ClockGlyphs.append(path,c,x,y,cap/100); x+=ClockGlyphs.advance(c)*cap/100; }
        suffixX=x+dp(8); suffixBaseline=y+cap-dp(4);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        super.onSizeChanged(w,h,oldw,oldh);
        if(w>0&&h>0) {
            rimLight.setShader(new LinearGradient(0,0,w,h,0xF5FFFFFF,0x80DDE8F5,Shader.TileMode.CLAMP));
            interior.setShader(new LinearGradient(0,h*0.10f,0,h*0.90f,0x4DEAF2FF,0x1FEAF2FF,Shader.TileMode.CLAMP));
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(interiorFill&&!silhouette.isEmpty()) {
            canvas.drawPath(silhouette,interior);                                            // the frosted body of the numerals
            float unit=Math.max(0.6f,materialUnitPx),shift=1.6f*unit;
            canvas.save(); canvas.clipPath(silhouette);
            canvas.translate(-shift,-shift*1.2f);                                            // light from the upper left: the shade sits inside the lower-right edge
            innerShade.setStrokeWidth(Math.max(1.5f,3.2f*unit)); canvas.drawPath(silhouette,innerShade);
            canvas.restore();
        }
        if(outlineContrast&&!silhouette.isEmpty()) {
            float width=Math.max(1.5f,1.8f*materialUnitPx);
            rimShade.setStrokeWidth(width*2.4f); canvas.drawPath(silhouette,rimShade);
            rimLight.setStrokeWidth(width); canvas.drawPath(silhouette,rimLight);
        }
        if(!period.isEmpty()) { foregroundPaint.setColor(Color.rgb(242,246,250));
            foregroundPaint.setTextSize(dp(18)); canvas.drawText(period,suffixX,suffixBaseline,foregroundPaint); }
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if(autoUpdate) tick.run(); }
    @Override protected void onDetachedFromWindow() { clockHandler.removeCallbacks(tick); super.onDetachedFromWindow(); }
}
