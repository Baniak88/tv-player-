package pl.tvplayer;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Line icons for the home tiles, drawn in code so they stay sharp on every TV resolution. */
final class TvTileIcon extends Drawable {
    static final int TV=0,MOVIE=1,SERIES=2;
    private final int kind,size;
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG),fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    TvTileIcon(int kind,int size) { this(kind,size,0xFFFFFFFF); }
    TvTileIcon(int kind,int size,int color) {
        this.kind=kind; this.size=size;
        stroke.setStyle(Paint.Style.STROKE); stroke.setColor(color); stroke.setStrokeCap(Paint.Cap.ROUND); stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL); fill.setColor(color);
    }
    static int kind(String url) { return url.equals("section:movie")?MOVIE:url.equals("section:series")?SERIES:TV; }
    @Override public void draw(Canvas canvas) {
        Rect b=getBounds(); float size=Math.min(b.width(),b.height()); if(size<=0) return;
        float x=b.exactCenterX()-size/2,y=b.exactCenterY()-size/2; stroke.setStrokeWidth(Math.max(2,size*.065f));
        canvas.save(); canvas.translate(x,y);
        if(kind==TV) television(canvas,size); else if(kind==MOVIE) clapper(canvas,size); else series(canvas,size);
        canvas.restore();
    }
    private void television(Canvas c,float s) {
        RectF screen=new RectF(s*.08f,s*.26f,s*.92f,s*.80f); c.drawRoundRect(screen,s*.08f,s*.08f,stroke);
        c.drawLine(s*.36f,s*.06f,s*.50f,s*.24f,stroke); c.drawLine(s*.64f,s*.06f,s*.50f,s*.24f,stroke);
        c.drawLine(s*.30f,s*.94f,s*.70f,s*.94f,stroke);
        Path play=new Path(); play.moveTo(s*.43f,s*.40f); play.lineTo(s*.62f,s*.53f); play.lineTo(s*.43f,s*.66f); play.close(); c.drawPath(play,fill);
    }
    private void clapper(Canvas c,float s) {
        RectF body=new RectF(s*.10f,s*.42f,s*.90f,s*.90f); c.drawRoundRect(body,s*.06f,s*.06f,stroke);
        c.save(); c.rotate(-14,s*.10f,s*.40f);
        RectF bar=new RectF(s*.10f,s*.20f,s*.90f,s*.38f); c.drawRoundRect(bar,s*.04f,s*.04f,stroke);
        for(float stripe=.30f;stripe<.85f;stripe+=.18f) c.drawLine(s*stripe,s*.20f,s*(stripe-.08f),s*.38f,stroke);
        c.restore();
        Path play=new Path(); play.moveTo(s*.42f,s*.53f); play.lineTo(s*.62f,s*.66f); play.lineTo(s*.42f,s*.79f); play.close(); c.drawPath(play,fill);
    }
    private void series(Canvas c,float s) {
        c.drawLine(s*.26f,s*.10f,s*.74f,s*.10f,stroke); c.drawLine(s*.16f,s*.22f,s*.84f,s*.22f,stroke);
        RectF front=new RectF(s*.06f,s*.34f,s*.94f,s*.90f); c.drawRoundRect(front,s*.08f,s*.08f,stroke);
        Path play=new Path(); play.moveTo(s*.41f,s*.48f); play.lineTo(s*.63f,s*.62f); play.lineTo(s*.41f,s*.76f); play.close(); c.drawPath(play,fill);
    }
    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }
    @Override public void setAlpha(int alpha) { stroke.setAlpha(alpha); fill.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { stroke.setColorFilter(filter); fill.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
