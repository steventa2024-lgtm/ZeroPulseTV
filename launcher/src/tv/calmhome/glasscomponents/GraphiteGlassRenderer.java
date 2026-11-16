package tv.calmhome.glasscomponents;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Java 8 / API 26+ source prototype. Worker-only CPU optics; no AGSL or screen capture.
 * The material samples one wallpaper, not intervening siblings/video. Canvas blur and
 * GPU rasterization differ across platforms: compare native screenshots before shipping.
 */
public final class GraphiteGlassRenderer {
    private GraphiteGlassRenderer() { }
    private static final int MAX_BACKDROP_PIXELS = 2097152;
    private static final int MAX_MASK_PIXELS = 524288;

    /** Immutable published wallpaper plus blurred pixels. Never recycle wallpaper manually. */
    public static final class Backdrop {
        public final Bitmap wallpaper;
        public final int width, height;
        private final int[] pixels;
        private Backdrop(Bitmap wallpaper, int[] pixels) {
            this.wallpaper = wallpaper; this.pixels = pixels;
            width = wallpaper.getWidth(); height = wallpaper.getHeight();
        }
        /** Worker only. Shares the sharp bitmap; blur always starts from sharp pixels.
         * sigma is in CACHE pixels, not dp. Cache one variant per material scale.
         */
        public Backdrop withFrost(float sigma) {
            workerOnly();
            if (!Float.isFinite(sigma) || sigma < 0 || sigma > 24) throw new IllegalArgumentException("frost 0..24px");
            int[] copy = new int[width * height];
            wallpaper.getPixels(copy, 0, width, 0, 0, width, height);
            gaussian(copy, width, height, sigma);
            return new Backdrop(wallpaper, copy);
        }
    }

    /** Worker only; center-crops into an opaque sharp bitmap, at most 2,097,152 pixels.
     * Input must be immutable software pixels. Never mutate/recycle it during preparation.
     * Display result.wallpaper stretched exactly over the anchor View passed to bindBackdrop.
     */
    public static Backdrop prepareBackdrop(Bitmap source, int width, int height) {
        workerOnly();
        if (source == null || source.isRecycled() || source.isMutable()
                || (android.os.Build.VERSION.SDK_INT >= 26 && source.getConfig() == Bitmap.Config.HARDWARE)) throw new IllegalArgumentException("immutable software bitmap required");
        if (width < 2 || height < 2 || (long) width * height > MAX_BACKDROP_PIXELS) throw new IllegalArgumentException("backdrop exceeds pixel budget");
        Bitmap work = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(work); canvas.drawColor(Color.rgb(20, 23, 27));
        float scale = Math.max(width / (float) source.getWidth(), height / (float) source.getHeight());
        Matrix matrix = new Matrix(); matrix.setScale(scale, scale);
        matrix.postTranslate((width - source.getWidth()*scale)/2, (height - source.getHeight()*scale)/2);
        canvas.drawBitmap(source, matrix, new Paint(Paint.FILTER_BITMAP_FLAG));
        Bitmap sharp = work.copy(Bitmap.Config.ARGB_8888, false);
        work.recycle(); // Unpublished temporary only.
        int[] pixels = new int[width * height]; sharp.getPixels(pixels, 0, width, 0, 0, width, height);
        return new Backdrop(sharp, pixels);
    }

    /** ZeroPulse addition: render counters for diagnostics (how many glass renders ran, how long they took, on this device). */
    public static final class Stats {
        private static final java.util.concurrent.atomic.AtomicLong COUNT=new java.util.concurrent.atomic.AtomicLong(),
                TOTAL_NANOS=new java.util.concurrent.atomic.AtomicLong(), MAX_NANOS=new java.util.concurrent.atomic.AtomicLong(),
                LAST_NANOS=new java.util.concurrent.atomic.AtomicLong(), TOTAL_PIXELS=new java.util.concurrent.atomic.AtomicLong(),
                SUBMITTED=new java.util.concurrent.atomic.AtomicLong();
        private Stats() { }
        /** ZeroPulse addition: render jobs handed to the worker (a job that is cancelled before it finishes is still counted here, not in renders()). */
        public static long submitted() { return SUBMITTED.get(); }
        static void record(long nanos,int pixels) {
            COUNT.incrementAndGet(); TOTAL_NANOS.addAndGet(nanos); TOTAL_PIXELS.addAndGet(pixels); LAST_NANOS.set(nanos);
            long seen; do { seen=MAX_NANOS.get(); } while(nanos>seen&&!MAX_NANOS.compareAndSet(seen,nanos));
        }
        public static long renders() { return COUNT.get(); }
        public static double averageMillis() { long n=COUNT.get(); return n==0?0:TOTAL_NANOS.get()/1e6/n; }
        public static double maxMillis() { return MAX_NANOS.get()/1e6; }
        public static double lastMillis() { return LAST_NANOS.get()/1e6; }
        public static double averagePixels() { long n=COUNT.get(); return n==0?0:TOTAL_PIXELS.get()/(double)n; }
        public static void reset() { COUNT.set(0); TOTAL_NANOS.set(0); MAX_NANOS.set(0); LAST_NANOS.set(0); TOTAL_PIXELS.set(0); SUBMITTED.set(0); }
    }

    public static final class Frames {
        public final Bitmap idle, emphasized;
        Frames(Bitmap idle, Bitmap emphasized) { this.idle = idle; this.emphasized = emphasized; }
        void discardUnpublished() { idle.recycle(); emphasized.recycle(); }
    }

    /** Worker only. Mask is an antialiased silhouette with transparent holes and a
     * transparent border. Coordinates/origin are in anchor-display pixels; sx/sy map
     * display pixels to backdrop cache pixels. Unit is the approved logical material
     * unit expressed in display pixels. Bevel is also in display pixels.
     */
    public static Frames render(Backdrop source, Bitmap mask, float originX, float originY,
                                float sx, float sy, float unit, float bevel) {
        workerOnly();
        int w = mask.getWidth(), h = mask.getHeight(), count = w*h;
        if (count > MAX_MASK_PIXELS || unit <= 0 || bevel <= 0 || !Float.isFinite(unit)
                || !Float.isFinite(bevel) || sx <= 0 || sy <= 0) throw new IllegalArgumentException("invalid material or mask budget");
        int[] pixels = new int[count]; mask.getPixels(pixels, 0, w, 0, 0, w, h);
        float[] inside = new float[count], outside = new float[count], temporary = new float[count];
        distanceTransform(pixels,w,h,true,temporary,inside);
        distanceTransform(pixels,w,h,false,temporary,outside);
        for (int i=0;i<count;i++) inside[i] = (pixels[i]>>>24)>=128
                ? 0.5f-(float)Math.sqrt(outside[i]) : (float)Math.sqrt(inside[i])-0.5f;
        // Raster EDT normals otherwise show pixel ridges on curves. Smooth normals,
        // retaining original distance for optical depth and original alpha for coverage.
        smoothNormals(inside,temporary,outside,w,h,Math.max(1f,2f*unit));
        int[] active = new int[count];
        for (int y=0;y<h;y++) {
            interrupted();
            for (int x=0;x<w;x++) {
                int index=y*w+x, alpha=pixels[index]>>>24;
                if (alpha==0) { pixels[index]=0; continue; }
                float nx=outside[y*w+Math.min(w-1,x+1)]-outside[y*w+Math.max(0,x-1)];
                float ny=outside[Math.min(h-1,y+1)*w+x]-outside[Math.max(0,y-1)*w+x];
                float length=(float)Math.hypot(nx,ny);
                if (length>0.0001f) { nx/=length; ny/=length; } else { nx=0; ny=-1; }
                float depth=Math.max(0,-inside[index]), edge=Math.max(0,1-depth/bevel);
                float shift=11*unit*edge*edge;
                float sampleX=(originX+x+0.5f+nx*shift)*sx;
                float sampleY=(originY+y+0.5f+ny*shift)*sy;
                float light=(float)Math.pow(Math.max(0,-0.63f*nx-0.78f*ny),3);
                float bounce=(float)Math.pow(Math.max(0,0.63f*nx+0.78f*ny),5);
                float directional=light+0.68f*bounce;
                float shade=(float)Math.exp(-depth/(4.3f*unit))*(1-light)*0.075f;
                float idleSpec=Math.min(0.93f,(float)Math.exp(-depth/unit)*(0.11f+0.48f*directional)
                        +(float)Math.exp(-depth/(7*unit))*directional*0.12f);
                float activeSpec=Math.min(0.93f,(float)Math.exp(-depth/(1.35f*unit))*(0.27f+0.66f*directional)
                        +(float)Math.exp(-depth/(7*unit))*directional*0.18f);
                int idleColor=alpha<<24, activeColor=alpha<<24;
                for (int c=0;c<3;c++) {
                    float value=sample(source,sampleX,sampleY,16-c*8);
                    float tint=c==0?19:c==1?23:29;
                    value=(value*0.91f+tint*0.09f)*(1-shade);
                    float bright=value*0.965f+(176+c*3)*0.035f;
                    int target=242+c*4;
                    idleColor|=channel(value+(target-value)*idleSpec)<<(16-c*8);
                    activeColor|=channel(bright+(target-bright)*activeSpec)<<(16-c*8);
                }
                pixels[index]=idleColor; active[index]=activeColor;
            }
        }
        interrupted();
        return new Frames(Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888),
                Bitmap.createBitmap(active,w,h,Bitmap.Config.ARGB_8888));
    }

    private static float sample(Backdrop b,float x,float y,int shift) {
        x=Math.max(0,Math.min(b.width-1.001f,x)); y=Math.max(0,Math.min(b.height-1.001f,y));
        int ix=(int)x,iy=(int)y; float dx=x-ix,dy=y-iy; int offset=iy*b.width+ix;
        float a=(b.pixels[offset]>>>shift)&255, c=(b.pixels[offset+b.width]>>>shift)&255;
        a+=(((b.pixels[offset+1]>>>shift)&255)-a)*dx;
        c+=(((b.pixels[offset+b.width+1]>>>shift)&255)-c)*dx;
        return a+(c-a)*dy;
    }
    private static int channel(float value) { return Math.max(0,Math.min(255,Math.round(value))); }

    /** Exact squared Euclidean distance to thresholded pixels; AA coverage is retained separately. */
    private static void distanceTransform(int[] mask,int w,int h,boolean toInside,float[] tmp,float[] out) {
        int n=Math.max(w,h); float[] f=new float[n],d=new float[n]; int[] v=new int[n]; double[] z=new double[n+1];
        for(int y=0;y<h;y++) { interrupted(); for(int x=0;x<w;x++) f[x]=((mask[y*w+x]>>>24)>=128)==toInside?0:Float.POSITIVE_INFINITY;
            edt(f,d,w,v,z); System.arraycopy(d,0,tmp,y*w,w); }
        for(int x=0;x<w;x++) { interrupted(); for(int y=0;y<h;y++) f[y]=tmp[y*w+x];
            edt(f,d,h,v,z); for(int y=0;y<h;y++) out[y*w+x]=d[y]; }
    }
    private static void edt(float[] f,float[] d,int n,int[] v,double[] z) {
        int k=-1;
        for(int q=0;q<n;q++) {
            if(Float.isInfinite(f[q])) continue;
            double s=Double.NEGATIVE_INFINITY;
            while(k>=0) { int p=v[k]; s=((double)f[q]+(double)q*q-f[p]-(double)p*p)/(2.0*(q-p));
                if(s>z[k]) break; k--; }
            k++; v[k]=q; z[k]=k==0?Double.NEGATIVE_INFINITY:s; z[k+1]=Double.POSITIVE_INFINITY;
        }
        if(k<0) { Arrays.fill(d,0,n,Float.POSITIVE_INFINITY); return; }
        int j=0;
        for(int q=0;q<n;q++) { while(z[j+1]<q) j++; float delta=q-v[j]; d[q]=delta*delta+f[v[j]]; }
    }
    private static void gaussian(int[] pixels,int w,int h,float sigma) {
        if(sigma<0.01f) return;
        int radius=(int)Math.ceil(3*sigma); float[] kernel=new float[2*radius+1]; float sum=0;
        for(int k=-radius;k<=radius;k++) { float value=(float)Math.exp(-(k*k)/(2*sigma*sigma)); kernel[k+radius]=value; sum+=value; }
        for(int k=0;k<kernel.length;k++) kernel[k]/=sum;
        int[] temporary=new int[pixels.length];
        for(int pass=0;pass<2;pass++) {
            int[] input=pass==0?pixels:temporary, output=pass==0?temporary:pixels;
            for(int y=0;y<h;y++) { interrupted(); for(int x=0;x<w;x++) {
                float r=0,g=0,b=0;
                for(int k=-radius;k<=radius;k++) { int xx=pass==0?Math.max(0,Math.min(w-1,x+k)):x;
                    int yy=pass==1?Math.max(0,Math.min(h-1,y+k)):y; int p=input[yy*w+xx]; float a=kernel[k+radius];
                    r+=((p>>>16)&255)*a; g+=((p>>>8)&255)*a; b+=(p&255)*a; }
                output[y*w+x]=0xFF000000|(channel(r)<<16)|(channel(g)<<8)|channel(b);
            } }
        }
    }
    private static void smoothNormals(float[] source,float[] tmp,float[] out,int w,int h,float sigma) {
        sigma=Math.min(6,sigma); int radius=(int)Math.ceil(3*sigma);
        float[] kernel=new float[2*radius+1]; float sum=0;
        for(int i=-radius;i<=radius;i++) { float v=(float)Math.exp(-(i*i)/(2*sigma*sigma)); kernel[i+radius]=v; sum+=v; }
        for(int i=0;i<kernel.length;i++) kernel[i]/=sum;
        for(int pass=0;pass<2;pass++) { float[] from=pass==0?source:tmp, to=pass==0?tmp:out;
            for(int y=0;y<h;y++) { interrupted(); for(int x=0;x<w;x++) { float v=0;
                for(int k=-radius;k<=radius;k++) { int xx=pass==0?Math.max(0,Math.min(w-1,x+k)):x;
                    int yy=pass==1?Math.max(0,Math.min(h-1,y+k)):y; v+=from[yy*w+xx]*kernel[k+radius]; }
                to[y*w+x]=v;
            } }
        }
    }
    private static void interrupted() { if(Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException(); }
    private static void workerOnly() { if(Looper.myLooper()==Looper.getMainLooper()) throw new IllegalStateException("prepare/render on a worker"); }

    /** Shared cache plumbing. Only subclasses draw masks; labels are drawn separately.
     * Same unscaled/unrotated root required. Position changes invalidate old pixels.
     * While rebuilding, subclasses display a deliberate plain silhouette/text fallback.
     */
    public abstract static class CachedView extends View {
        private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(1,1,20,TimeUnit.SECONDS,new LinkedBlockingQueue<Runnable>());
        static { WORKER.allowCoreThreadTimeOut(true); }
        private final Handler main=new Handler(Looper.getMainLooper());
        private final Paint bitmapPaint=new Paint(Paint.FILTER_BITMAP_FLAG);
        private final int[] position=new int[2], anchorPosition=new int[2];
        private final ViewTreeObserver.OnPreDrawListener observer=new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() { refresh(); return true; }
        };
        /** ZeroPulse change: how long a surface that already has glass must hold still before it is re-rendered for its new place. */
        public static final long SETTLE_MS=100;
        private final Runnable settle=new Runnable() { @Override public void run() { if(isAttachedToWindow()) refresh(); } };
        private ViewTreeObserver tree;
        private Backdrop backdrop;
        private View anchor;
        private Frames frames;
        private Future<?> pending;
        private int version,lastX=Integer.MIN_VALUE,lastY=Integer.MIN_VALUE,lastAnchorW,lastAnchorH;
        private int candidateX=Integer.MIN_VALUE,candidateY=Integer.MIN_VALUE,candidateAnchorW,candidateAnchorH;
        private long candidateSince;
        protected float materialUnitPx=1,bevelPx=23;
        protected final Paint foregroundPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        protected final Path silhouette=new Path();
        public CachedView(Context context) { super(context); setWillNotDraw(false); }
        /** UI thread. Anchor must display backdrop.wallpaper at exactly its full bounds. */
        public final void bindBackdrop(Backdrop value,View wallpaperAnchor) {
            // ZeroPulse change: a new wallpaper keeps the last glass on screen until the new glass is ready (no grey flash).
            backdrop=value; anchor=wallpaperAnchor; cancel(); lastX=Integer.MIN_VALUE; invalidate();
        }
        protected abstract void buildSilhouette(Path path,int width,int height);
        protected boolean emphasized() { return isFocused(); }
        /** 0 = idle frame, 1 = focus (emphasized) frame, in between = a crossfade of the two (ZeroPulse addition: smooth focus). */
        protected float emphasis() { return emphasized() ? 1f : 0f; }
        protected boolean showMaterial() { return true; }
        /**
         * ZeroPulse addition: how much of the glass shows, 0..1 (1 by default). A control whose glass belongs to focus (a list row) has
         * none until it takes focus: at 0 nothing is drawn and no render is started, so a screen of twenty rows renders only those
         * the viewer reaches.
         */
        protected float presence() { return 1f; }
        /** True once real glass pixels exist for the current backdrop (otherwise the deliberate plain silhouette shows). */
        public final boolean hasMaterial() { return frames!=null; }
        protected final void dirtyMaterial() { cancel(); frames=null; lastX=Integer.MIN_VALUE; invalidate(); }
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
            super.onSizeChanged(w,h,oldw,oldh); silhouette.rewind(); buildSilhouette(silhouette,w,h); dirtyMaterial();
        }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); tree=getViewTreeObserver(); tree.addOnPreDrawListener(observer); dirtyMaterial(); }
        @Override protected void onDetachedFromWindow() {
            if(tree!=null&&tree.isAlive()) tree.removeOnPreDrawListener(observer); tree=null;
            main.removeCallbacks(settle);
            cancel(); frames=null; super.onDetachedFromWindow();
        }
        @Override protected void drawableStateChanged() { super.drawableStateChanged(); invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); if(!showMaterial()) return;
            float shown=Math.max(0f,Math.min(1f,presence())); if(shown<=0.001f) return;
            if(frames!=null) {
                int alpha=Math.round((isEnabled()?255:100)*shown); float e=Math.max(0f,Math.min(1f,emphasis()));
                bitmapPaint.setAlpha(alpha);
                if(e<=0.001f) canvas.drawBitmap(frames.idle,0,0,bitmapPaint);
                else if(e>=0.999f) canvas.drawBitmap(frames.emphasized,0,0,bitmapPaint);
                else { canvas.drawBitmap(frames.idle,0,0,bitmapPaint); bitmapPaint.setAlpha(Math.round(alpha*e)); canvas.drawBitmap(frames.emphasized,0,0,bitmapPaint); }
            }
            else { foregroundPaint.setColor(0xCC68717D); canvas.drawPath(silhouette,foregroundPaint); }
        }
        private void cancel() { version++; if(pending!=null) { pending.cancel(true); if(pending instanceof Runnable) WORKER.remove((Runnable)pending); } pending=null; }
        private void refresh() {
            boolean valid=backdrop!=null&&anchor!=null&&anchor.isAttachedToWindow()&&getRootView()==anchor.getRootView()
                    &&anchor.getWidth()>0&&anchor.getHeight()>0&&getWidth()>0&&getHeight()>0
                    &&(long)getWidth()*getHeight()<=MAX_MASK_PIXELS&&untransformed(this)&&untransformed(anchor)
                    &&getScrollX()==0&&getScrollY()==0&&anchor.getScrollX()==0&&anchor.getScrollY()==0;
            if(!valid) { if(frames!=null||pending!=null) dirtyMaterial(); return; }
            if(frames==null&&presence()<=0.001f) return;          // ZeroPulse: nothing shows yet, so nothing is rendered yet
            getLocationInWindow(position); anchor.getLocationInWindow(anchorPosition);
            int x=position[0]-anchorPosition[0], y=position[1]-anchorPosition[1];
            if(x==lastX&&y==lastY&&anchor.getWidth()==lastAnchorW&&anchor.getHeight()==lastAnchorH) { main.removeCallbacks(settle); return; }
            // ZeroPulse change: a surface that already has glass and is still moving (a scrolling row, a tile sliding) keeps that glass and
            // is re-rendered once it has held still for SETTLE_MS, instead of cancelling and restarting a render on every frame of the move.
            long now=android.os.SystemClock.uptimeMillis();
            if(x!=candidateX||y!=candidateY||anchor.getWidth()!=candidateAnchorW||anchor.getHeight()!=candidateAnchorH) {
                candidateX=x; candidateY=y; candidateAnchorW=anchor.getWidth(); candidateAnchorH=anchor.getHeight(); candidateSince=now;
            }
            if(frames!=null&&now-candidateSince<SETTLE_MS) { main.removeCallbacks(settle); main.postDelayed(settle,SETTLE_MS-(now-candidateSince)+1); return; }
            main.removeCallbacks(settle);
            // ZeroPulse change: the previous frames stay until the re-render lands (a moved tile briefly carries its old glass).
            cancel(); lastX=x; lastY=y; lastAnchorW=anchor.getWidth(); lastAnchorH=anchor.getHeight();
            final int token=version,w=getWidth(),h=getHeight(); final Backdrop source=backdrop;
            final Path maskPath=new Path(silhouette); final float ox=x,oy=y;
            final float sx=source.width/(float)lastAnchorW,sy=source.height/(float)lastAnchorH,unit=materialUnitPx,bevel=bevelPx;
            Stats.SUBMITTED.incrementAndGet();
            pending=WORKER.submit(new Runnable() { @Override public void run() {
                Bitmap mask=null; Frames ready=null;
                try { interrupted(); mask=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                    Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.WHITE); new Canvas(mask).drawPath(maskPath,paint);
                    long began=System.nanoTime();
                    ready=render(source,mask,ox,oy,sx,sy,unit,bevel); interrupted(); final Frames published=ready;
                    Stats.record(System.nanoTime()-began,w*h);
                    main.post(new Runnable() { @Override public void run() {
                        if(!isAttachedToWindow()||token!=version) { published.discardUnpublished(); return; }
                        frames=published; pending=null; invalidate();
                    } }); ready=null;
                } catch(java.util.concurrent.CancellationException cancelled) { }
                finally { if(mask!=null) mask.recycle(); if(ready!=null) ready.discardUnpublished(); }
            } }); invalidate();
        }
        private static boolean untransformed(View view) {
            for(View v=view;;) { if(v.getScaleX()!=1||v.getScaleY()!=1||v.getRotation()!=0||v.getRotationX()!=0||v.getRotationY()!=0||v.getAnimation()!=null) return false;
                ViewParent p=v.getParent(); if(!(p instanceof View)) return true; v=(View)p; }
        }
        protected final float dp(float value) { return value*getResources().getDisplayMetrics().density; }
    }
}
