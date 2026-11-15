package pl.tvplayer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** Read-only TV overlay; transport and menu keys remain handled by the player. */
final class TvLiveInfoBar extends LinearLayout {
    private final TextView channel,now,next,audio;
    private final ProgressBar progress;
    private GuideRepository.Data data;
    TvLiveInfoBar(Context context) {
        super(context); setOrientation(VERTICAL); setPadding(Ui.dp(context,28),Ui.dp(context,22),Ui.dp(context,28),Ui.dp(context,18));
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0xE6101420,0xF5101420}));
        channel=line(20); channel.setTypeface(null,android.graphics.Typeface.BOLD); now=line(18);
        progress=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(1000); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        LinearLayout.LayoutParams bar=new LinearLayout.LayoutParams(-1,Ui.dp(context,4)); bar.topMargin=Ui.dp(context,8); bar.bottomMargin=Ui.dp(context,8); addView(progress,bar);
        next=line(15); next.setTextColor(0xFFCDD2DF); audio=line(13); audio.setTextColor(0xFFADB4C5);
    }
    private TextView line(int size) { TextView text=new TextView(getContext()); text.setTextSize(size); text.setTextColor(Color.WHITE); text.setSingleLine(true); text.setEllipsize(android.text.TextUtils.TruncateAt.END); addView(text,new LinearLayout.LayoutParams(-1,-2)); return text; }
    void channel(String name) { channel.setText(name); }
    void audio(String text) { audio.setText(text+" · OK / Info: informacje · Menu: opcje"); }
    void guide(GuideRepository.Data value) { data=value; update(); }
    void update() {
        long time=System.currentTimeMillis()/1000; Guide.Program current=null,following=null;
        if(data!=null) for(Guide.Program p:data.programs()) {
            if(p.current(time)) current=p;
            if(p.start()>time && (following==null || p.start()<following.start())) following=p;
        }
        now.setText(current==null?"Brak bieżącego EPG od dostawcy":"Teraz  "+clock(current.start())+"–"+clock(current.end())+"  "+current.title());
        next.setText(following==null?"Następnie: brak danych":"Następnie  "+clock(following.start())+"  "+following.title());
        progress.setVisibility(current==null?View.GONE:View.VISIBLE);
        if(current!=null) progress.setProgress((int)Math.max(0,Math.min(1000,(time-current.start())*1000/Math.max(1,current.end()-current.start()))));
    }
    private String clock(long seconds) { return DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(seconds)); }
}
