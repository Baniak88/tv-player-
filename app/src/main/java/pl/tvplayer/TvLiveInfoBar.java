package pl.tvplayer;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

/**
 * Read-only TV infobar in the style of a satellite receiver: channel logo box, name and clock,
 * NOW with a green progress bar, NEXT, and a footer with audio details and remote hints.
 * Transport and menu keys remain handled by the player.
 */
final class TvLiveInfoBar extends LinearLayout {
    static final int GREEN=0xFF00E676, SOFT=0xFFC2CCD7, TRACK=0x80647181;
    private final TextView channel,clock,nowTime,now,nextTime,next,audio,remaining,logoText;
    private final ImageView logo;
    private final ProgressBar progress;
    private GuideRepository.Data data;
    TvLiveInfoBar(Context context) {
        super(context); setOrientation(VERTICAL); setPadding(dp(32),dp(56),dp(32),dp(18));
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0xCC07090C,0xF207090C,0xFA07090C}));
        LinearLayout main=new LinearLayout(context); main.setGravity(Gravity.CENTER_VERTICAL); addView(main,new LayoutParams(-1,-2));
        FrameLayout box=new FrameLayout(context); box.setBackground(Ui.shape(context,0xE61B1F25,12,0x33FFFFFF)); box.setPadding(dp(8),dp(8),dp(8),dp(8));
        logoText=text("",15,0xFFF3F6FA); logoText.setGravity(Gravity.CENTER); logoText.setMaxLines(2); logoText.setTypeface(null,Typeface.BOLD); box.addView(logoText,new FrameLayout.LayoutParams(-1,-1));
        logo=new ImageView(context); logo.setScaleType(ImageView.ScaleType.FIT_CENTER); box.addView(logo,new FrameLayout.LayoutParams(-1,-1));
        main.addView(box,new LayoutParams(dp(112),dp(80)));
        View divider=new View(context); divider.setBackgroundColor(0x33FFFFFF); LayoutParams line=new LayoutParams(dp(1),dp(80)); line.leftMargin=dp(18); line.rightMargin=dp(18); main.addView(divider,line);
        LinearLayout copy=new LinearLayout(context); copy.setOrientation(VERTICAL); main.addView(copy,new LayoutParams(0,-2,1));
        LinearLayout header=new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL); copy.addView(header,new LayoutParams(-1,-2));
        channel=text("",22,0xFFF3F6FA); channel.setTypeface(null,Typeface.BOLD); header.addView(channel,new LayoutParams(0,-2,1));
        clock=text("",20,0xFFF3F6FA); header.addView(clock,new LayoutParams(-2,-2));
        LinearLayout nowRow=row(copy,dp(8)); chip(nowRow,"TERAZ",GREEN,0x2600E676); nowTime=time(nowRow,0xFFF3F6FA); now=title(nowRow,18,0xFFF3F6FA);
        progress=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(1000);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN)); progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(TRACK));
        LayoutParams bar=new LayoutParams(-1,dp(4)); bar.topMargin=dp(10); bar.bottomMargin=dp(10); copy.addView(progress,bar);
        LinearLayout nextRow=row(copy,0); chip(nextRow,"POTEM",SOFT,0x1FFFFFFF); nextTime=time(nextRow,SOFT); next=title(nextRow,16,SOFT);
        LinearLayout footer=new LinearLayout(context); footer.setGravity(Gravity.CENTER_VERTICAL); LayoutParams bottom=new LayoutParams(-1,-2); bottom.topMargin=dp(14); addView(footer,bottom);
        audio=text("",13,SOFT); audio.setSingleLine(true); audio.setEllipsize(android.text.TextUtils.TruncateAt.END); footer.addView(audio,new LayoutParams(0,-2,1));
        TextView hints=text("▲▼ kanały    ◀ program TV    ▶ opcje    OK ukryj",13,0xFF8F99A6); hints.setPadding(dp(16),0,dp(16),0); footer.addView(hints,new LayoutParams(-2,-2));
        remaining=text("",13,SOFT); footer.addView(remaining,new LayoutParams(-2,-2));
    }
    private int dp(int value) { return Ui.dp(getContext(),value); }
    private TextView text(String value,int size,int color) { TextView text=Ui.text(getContext(),value,size,color); text.setIncludeFontPadding(false); return text; }
    private LinearLayout row(LinearLayout parent,int top) { LinearLayout row=new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL); LayoutParams size=new LayoutParams(-1,-2); size.topMargin=top; parent.addView(row,size); return row; }
    private void chip(LinearLayout row,String label,int color,int fill) {
        TextView chip=text(label,12,color); chip.setTypeface(null,Typeface.BOLD); chip.setLetterSpacing(.08f); chip.setGravity(Gravity.CENTER);
        chip.setBackground(Ui.shape(getContext(),fill,6,0)); row.addView(chip,new LayoutParams(dp(68),dp(24)));
    }
    private TextView time(LinearLayout row,int color) { TextView time=text("",15,color); time.setSingleLine(true); LayoutParams size=new LayoutParams(dp(108),-2); size.leftMargin=dp(12); row.addView(time,size); return time; }
    private TextView title(LinearLayout row,int size,int color) { TextView title=text("",size,color); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END); row.addView(title,new LayoutParams(0,-2,1)); return title; }
    void channel(String name) {
        String value=name==null?"":name; channel.setText(value);
        String shortName=value.replaceAll("(?i)\\b(fhd|uhd|hd|sd|4k|hevc|pl|raw)\\b|[|:\\[\\]]"," ").trim().replaceAll("\\s+"," ");
        logoText.setText(shortName.length()>14?shortName.substring(0,14):shortName);
    }
    void logo(String url) {
        ImageLoader.get(getContext()).load(url==null?"":url,logo,dp(96),dp(64));
        logoText.setVisibility(url!=null && !url.isEmpty()?View.INVISIBLE:View.VISIBLE);
    }
    void audio(String text) { audio.setText(text); }
    void guide(GuideRepository.Data value) { data=value; update(); }
    void update() {
        long time=System.currentTimeMillis()/1000; Guide.Program current=null,following=null;
        if(data!=null) for(Guide.Program p:data.programs()) {
            if(p.current(time)) current=p;
            if(p.start()>time && (following==null || p.start()<following.start())) following=p;
        }
        clock.setText(clock(time));
        nowTime.setText(current==null?"":clock(current.start())+" – "+clock(current.end()));
        now.setText(current==null?"Brak bieżącego EPG od dostawcy":current.title());
        nextTime.setText(following==null?"":clock(following.start()));
        next.setText(following==null?"Brak danych":following.title());
        progress.setVisibility(current==null?View.INVISIBLE:View.VISIBLE);
        if(current!=null) progress.setProgress((int)Math.max(0,Math.min(1000,(time-current.start())*1000/Math.max(1,current.end()-current.start()))));
        remaining.setText(current==null?"":"Do końca: "+Math.max(1,(current.end()-time+59)/60)+" min");
    }
    private String clock(long seconds) { return DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(seconds)); }
}
