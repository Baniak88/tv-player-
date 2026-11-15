package pl.tvplayer;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Shared OKTV 1.1.6 tile palette and vector icons for phone and television. */
final class HomeStyle {
    private HomeStyle() {}
    static Drawable tileBackground(Context context,String kind) {
        int[] colors=kind.equals("tv")?new int[]{0xFFFF3D5E,0xFFB0186F}:kind.equals("movie")?new int[]{0xFF6A5CFF,0xFF3A2A9E}:new int[]{0xFF00B8D9,0xFF0B6E99};
        StateListDrawable states=new StateListDrawable();
        GradientDrawable pressed=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Ui.lighter(colors[0]),Ui.lighter(colors[1])}); pressed.setCornerRadius(Ui.dp(context,18));
        GradientDrawable focused=new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors); focused.setCornerRadius(Ui.dp(context,18)); focused.setStroke(Ui.dp(context,3),0xFFFFFFFF);
        GradientDrawable normal=new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors); normal.setCornerRadius(Ui.dp(context,18));
        states.addState(new int[]{android.R.attr.state_pressed},pressed); states.addState(new int[]{android.R.attr.state_focused},focused); states.addState(new int[]{},normal);
        return states;
    }
    static View tvTile(Context context,String label,String kind,boolean large,Runnable click) {
        LinearLayout box=new LinearLayout(context); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(context,12),Ui.dp(context,12),Ui.dp(context,12),Ui.dp(context,12));
        box.setBackground(tileBackground(context,kind)); box.setElevation(Ui.dp(context,4));
        box.setFocusable(true); box.setFocusableInTouchMode(true); box.setClickable(true); box.setContentDescription(label); box.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        Icon image=new Icon(context,kind); box.addView(image,new LinearLayout.LayoutParams(Ui.dp(context,large?68:46),Ui.dp(context,large?68:46)));
        TextView name=Ui.text(context,label,large?26:22,Ui.TEXT); name.setTypeface(null,Typeface.BOLD); name.setGravity(Gravity.CENTER); name.setMaxLines(2); name.setPadding(0,Ui.dp(context,12),0,0); box.addView(name);
        box.setOnClickListener(v -> click.run()); return box;
    }
    static View tvAction(Context context,String label,String kind,Runnable click) {
        LinearLayout row=new LinearLayout(context); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(Ui.dp(context,18),Ui.dp(context,10),Ui.dp(context,14),Ui.dp(context,10));
        StateListDrawable background=new StateListDrawable();
        GradientDrawable focused=Ui.shape(context,Ui.SURFACE_HIGH,16,0); focused.setStroke(Ui.dp(context,3),Ui.ACCENT);
        background.addState(new int[]{android.R.attr.state_pressed},Ui.shape(context,Ui.lighter(Ui.SURFACE),16,Ui.ACCENT));
        background.addState(new int[]{android.R.attr.state_focused},focused); background.addState(new int[]{},Ui.shape(context,Ui.SURFACE,16,Ui.BORDER));
        row.setBackground(background); row.setFocusable(true); row.setFocusableInTouchMode(true); row.setClickable(true); row.setContentDescription(label); row.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        Icon glyph=new Icon(context,kind); glyph.tint=Ui.ACCENT; row.addView(glyph,new LinearLayout.LayoutParams(Ui.dp(context,28),Ui.dp(context,28)));
        TextView name=Ui.text(context,label,17,Ui.TEXT); name.setTypeface(null,Typeface.BOLD); name.setMaxLines(2); name.setPadding(Ui.dp(context,14),0,0,0); row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        row.setOnClickListener(v -> click.run()); return row;
    }
    static final class Icon extends View {
        private final String kind;
        private final Paint paint=new Paint(3);
        int tint=0xFFFFFFFF;
        Icon(Context context,String kind) { super(context); this.kind=kind; setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); canvas.save(); float scale=Math.min(getWidth(),getHeight())/48f;
            canvas.translate((getWidth()-48*scale)/2,(getHeight()-48*scale)/2); canvas.scale(scale,scale);
            paint.setColor(tint); paint.setStrokeWidth(2.4f); paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            switch(kind) {
                case "tv":
                    canvas.drawRoundRect(5,12,43,40,5,5,paint); canvas.drawLine(14,4,24,12,paint); canvas.drawLine(34,4,24,12,paint);
                    canvas.drawRoundRect(11,18,37,34,3,3,paint); play(canvas,22,22,7); break;
                case "movie": canvas.drawCircle(24,24,18,paint); play(canvas,20,16,13); break;
                case "series":
                    canvas.drawRect(6,17,42,40,paint); canvas.drawRect(6,8,42,17,paint);
                    for(int x=8;x<40;x+=10) canvas.drawLine(x,17,x+7,8,paint); canvas.drawLine(6,23,42,23,paint); break;
                case "account":
                    canvas.drawCircle(24,15,7,paint); canvas.drawArc(10,25,38,46,180,180,false,paint);
                    canvas.drawCircle(9,22,4,paint); canvas.drawCircle(39,22,4,paint); break;
                case "source":
                    for(int y=8;y<=32;y+=12) { canvas.drawRoundRect(7,y,41,y+8,2,2,paint); canvas.drawCircle(13,y+4,1,paint); } break;
                case "settings":
                    canvas.drawCircle(24,24,12,paint); canvas.drawCircle(24,24,4,paint);
                    for(int i=0;i<8;i++) { canvas.save(); canvas.rotate(i*45,24,24); canvas.drawLine(24,6,24,11,paint); canvas.restore(); } break;
                case "refresh":
                    canvas.drawArc(8,8,40,40,35,285,false,paint); canvas.drawLine(36,4,39,14,paint); canvas.drawLine(39,14,29,13,paint); break;
                default:
                    canvas.drawLine(22,6,8,6,paint); canvas.drawLine(8,6,8,42,paint); canvas.drawLine(8,42,22,42,paint);
                    canvas.drawLine(20,24,43,24,paint); canvas.drawLine(35,16,43,24,paint); canvas.drawLine(43,24,35,32,paint);
            }
            canvas.restore();
        }
        private void play(Canvas canvas,float x,float y,float size) { Path p=new Path(); p.moveTo(x,y); p.lineTo(x+size,y+size/2); p.lineTo(x,y+size); p.close(); canvas.drawPath(p,paint); }
    }
}
