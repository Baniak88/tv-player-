package pl.tvplayer;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** The receiver-style infobar adapted to a phone's width, without remote-only hints. */
final class TvLiveInfoBar extends LinearLayout {
    static final int GREEN=0xFF00E676,SOFT=0xFFC2CCD7,TRACK=0x80647181;
    private final TextView channel,clock,now,next,audio,remaining;
    private final ImageView logo;
    private final ProgressBar progress;
    private GuideRepository.Data data;
    TvLiveInfoBar(Context context) {
        super(context); setOrientation(VERTICAL); setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x000A0D11,0xEB0A0D11,0xFA0A0D11}));
        LinearLayout header=new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL); addView(header,new LayoutParams(-1,-2));
        logo=new ImageView(context); logo.setScaleType(ImageView.ScaleType.FIT_CENTER); header.addView(logo,new LayoutParams(dp(44),dp(36)));
        channel=text("",18,Ui.TV_GOLD); channel.setTypeface(null,Typeface.BOLD); channel.setMaxLines(2); channel.setPadding(dp(8),0,dp(8),0); header.addView(channel,new LayoutParams(0,-2,1));
        clock=text("",14,SOFT); header.addView(clock,new LayoutParams(-2,-2));
        now=text("",17,Ui.TEXT); now.setTypeface(null,Typeface.BOLD); now.setMaxLines(2); now.setPadding(0,dp(8),0,0); addView(now,new LayoutParams(-1,-2));
        progress=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(1000); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN)); progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(TRACK)); LayoutParams bar=new LayoutParams(-1,dp(4)); bar.topMargin=dp(8); bar.bottomMargin=dp(6); addView(progress,bar);
        next=text("",14,SOFT); next.setMaxLines(2); addView(next,new LayoutParams(-1,-2));
        LinearLayout footer=new LinearLayout(context); footer.setPadding(0,dp(8),0,0); addView(footer,new LayoutParams(-1,-2));
        audio=text("",12,SOFT); audio.setSingleLine(true); audio.setEllipsize(android.text.TextUtils.TruncateAt.END); footer.addView(audio,new LayoutParams(0,-2,1));
        remaining=text("",12,SOFT); remaining.setPadding(dp(8),0,0,0); footer.addView(remaining,new LayoutParams(-2,-2));
    }
    private int dp(int value) { return Ui.dp(getContext(),value); }
    private TextView text(String value,int size,int color) { TextView result=Ui.text(getContext(),value,size,color); result.setIncludeFontPadding(false); return result; }
    void channel(String value) { channel.setText(value==null?"":value); }
    void logo(String url) { logo.setVisibility(url==null || url.isEmpty()?GONE:VISIBLE); ImageLoader.get(getContext()).load(url==null?"":url,logo,dp(44),dp(36)); }
    void audio(String value) { audio.setText(value); }
    void guide(GuideRepository.Data value) { data=value; update(); }
    void update() {
        long time=System.currentTimeMillis()/1000; Guide.Program current=null,following=null;
        if(data!=null) for(Guide.Program program:data.programs()) { if(program.current(time)) current=program; if(program.start()>time && (following==null || program.start()<following.start())) following=program; }
        clock.setText(clock(time)); now.setText(current==null?"Brak bieżącego EPG od dostawcy":clock(current.start())+"–"+clock(current.end())+"  "+current.title());
        next.setText(following==null?"Brak następnego programu":"Potem: "+clock(following.start())+"  "+following.title()); progress.setVisibility(current==null?GONE:VISIBLE);
        if(current!=null) progress.setProgress((int)Math.max(0,Math.min(1000,(time-current.start())*1000/Math.max(1,current.end()-current.start()))));
        remaining.setText(current==null?"":"Do końca: "+Math.max(1,(current.end()-time+59)/60)+" min");
    }
    private String clock(long seconds) { return DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(seconds)); }
}
