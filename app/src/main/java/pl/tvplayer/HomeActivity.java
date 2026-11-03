package pl.tvplayer;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public final class HomeActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private List<Channel> tv=new ArrayList<>();
    private boolean loadingAccount;
    private static final java.util.concurrent.atomic.AtomicBoolean PRELOADING=new java.util.concurrent.atomic.AtomicBoolean();
    private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density); }
    private int scaled(int n) { return dp(Math.round(n*Math.max(1f,getResources().getConfiguration().fontScale))); }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String s,int size,int color) { TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t; }
    private Drawable interactive(boolean prominent) {
        return Ui.touch(this,prominent?Ui.SURFACE_HIGH:Ui.SURFACE,16,Ui.BORDER);
    }
    /** Vivid gradient per section so the three entry points are recognisable at a glance. */
    private Drawable gradient(String kind) {
        int[] colors=kind.equals("tv")?new int[]{0xFFFF3D5E,0xFFB0186F}:kind.equals("movie")?new int[]{0xFF6A5CFF,0xFF3A2A9E}:new int[]{0xFF00B8D9,0xFF0B6E99};
        StateListDrawable states=new StateListDrawable();
        GradientDrawable pressed=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Ui.lighter(colors[0]),Ui.lighter(colors[1])}); pressed.setCornerRadius(dp(18));
        GradientDrawable focused=new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors); focused.setCornerRadius(dp(18)); focused.setStroke(dp(3),0xFFFFFFFF);
        GradientDrawable normal=new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors); normal.setCornerRadius(dp(18));
        states.addState(new int[]{android.R.attr.state_pressed},pressed); states.addState(new int[]{android.R.attr.state_focused},focused); states.addState(new int[]{},normal);
        return states;
    }
    private View tile(String label,String icon,boolean large,Runnable click) {
        LinearLayout box=column(); box.setGravity(Gravity.CENTER); box.setPadding(dp(12),dp(10),dp(12),dp(10));
        box.setBackground(gradient(icon)); box.setElevation(dp(4)); box.setFocusable(true); box.setClickable(true); box.setContentDescription(label);
        Icon image=new Icon(this,icon); box.addView(image,new LinearLayout.LayoutParams(dp(large?54:36),dp(large?54:36)));
        TextView name=text(label,large?21:17,0xFFFFFFFF); name.setTypeface(null,Typeface.BOLD); name.setGravity(Gravity.CENTER); name.setMaxLines(2);
        name.setPadding(0,dp(large?12:10),0,0); box.addView(name); box.setOnClickListener(v -> click.run()); return box;
    }
    private View action(String label,String icon,Runnable click) {
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(14),dp(6),dp(12),dp(6));
        row.setBackground(interactive(false)); row.setFocusable(true); row.setClickable(true); row.setContentDescription(label);
        Icon glyph=new Icon(this,icon); glyph.tint=Ui.ACCENT; row.addView(glyph,new LinearLayout.LayoutParams(dp(24),dp(24)));
        TextView t=text(label,15,Ui.TEXT); t.setPadding(dp(12),0,0,0); t.setMaxLines(2); row.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        row.setOnClickListener(v -> click.run()); return row;
    }
    private View compactAction(String label,String icon,Runnable click) {
        LinearLayout row=(LinearLayout)action(label,icon,click); row.setPadding(dp(8),dp(6),dp(8),dp(6));
        ((TextView)row.getChildAt(1)).setTextSize(14);
        row.getChildAt(0).setLayoutParams(new LinearLayout.LayoutParams(dp(20),dp(20))); return row;
    }
    @Override public void onCreate(Bundle state) { super.onCreate(state); build(); preload(); }
    /** Decrypts the library in the background so TV, films and series open instantly. */
    private void preload() {
        if(!PRELOADING.compareAndSet(false,true)) return;
        Context app=getApplicationContext();
        new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            LibraryStore store=new LibraryStore(app);
            try { for(String section:new String[]{"tv","movie","series"}) store.warm(section); } finally { PRELOADING.set(false); }
        },"library-preload").start();
    }
    private void open(String section,String group,boolean source) {
        Intent intent=new Intent(this,MainActivity.class).putExtra("section",section);
        if(group!=null) intent.putExtra("bouquet",group);
        if(source) intent.putExtra("open_source",true);
        startActivity(intent);
    }
    private void build() {
        boolean wide=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout root=column();
        root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((view,insets) -> {
            view.setPadding(dp(14)+insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),dp(14)+insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        ScrollView page=new ScrollView(this); page.setFillViewport(true); page.setClipToPadding(false);
        LinearLayout header=column();
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(2),dp(12),dp(2),dp(4));
        TextView version=text(Ui.APP_NAME.toUpperCase(),13,Ui.ACCENT); version.setTypeface(null,Typeface.BOLD); version.setLetterSpacing(.08f); top.addView(version,new LinearLayout.LayoutParams(0,-2,1));
        TextClock clock=new TextClock(this); clock.setFormat24Hour("HH:mm"); clock.setFormat12Hour("HH:mm"); clock.setTextColor(Ui.MUTED); clock.setTextSize(14); top.addView(clock); header.addView(top);
        TextView brand=text("Co oglądamy?",wide?22:26,Ui.TEXT); brand.setTypeface(null,Typeface.BOLD);
        brand.setPadding(dp(2),dp(wide?6:16),0,dp(wide?12:20)); header.addView(brand);
        int gap=dp(12);
        if(wide) {
            int height=scaled(176);
            LinearLayout menu=new LinearLayout(this);
            LinearLayout.LayoutParams liveSize=new LinearLayout.LayoutParams(0,height,1.15f); liveSize.rightMargin=gap;
            menu.addView(tile("Telewizja na żywo","tv",true,() -> open("tv",null,false)),liveSize);
            LinearLayout middle=column(); LinearLayout films=new LinearLayout(this), account=new LinearLayout(this);
            LinearLayout.LayoutParams first=new LinearLayout.LayoutParams(0,-1,1); first.rightMargin=gap;
            films.addView(tile("Filmy","movie",false,() -> open("movie",null,false)),first);
            films.addView(tile("Seriale","series",false,() -> open("series",null,false)),new LinearLayout.LayoutParams(0,-1,1));
            account.addView(compactAction("Konto","account",this::account),new LinearLayout.LayoutParams(0,-1,1));
            LinearLayout.LayoutParams sourceSize=new LinearLayout.LayoutParams(0,-1,1); sourceSize.leftMargin=gap;
            account.addView(compactAction("Źródło","source",() -> open("tv",null,true)),sourceSize);
            LinearLayout.LayoutParams filmsSize=new LinearLayout.LayoutParams(-1,0,1.4f); filmsSize.bottomMargin=gap;
            middle.addView(films,filmsSize); middle.addView(account,new LinearLayout.LayoutParams(-1,0,1));
            LinearLayout.LayoutParams midSize=new LinearLayout.LayoutParams(0,height,1.6f); midSize.rightMargin=gap; menu.addView(middle,midSize);
            LinearLayout actions=column();
            View[] buttons={action("Ustawienia","settings",this::settings),action("Odśwież źródło","refresh",this::refreshSource),action("Wyjście","exit",this::finish)};
            for(int i=0;i<buttons.length;i++) { LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,0,1); if(i<2) size.bottomMargin=gap; actions.addView(buttons[i],size); }
            menu.addView(actions,new LinearLayout.LayoutParams(0,height,1.15f)); header.addView(menu);
        } else {
            header.addView(tile("Telewizja na żywo","tv",true,() -> open("tv",null,false)),new LinearLayout.LayoutParams(-1,scaled(136)));
            LinearLayout films=new LinearLayout(this);
            LinearLayout.LayoutParams filmSize=new LinearLayout.LayoutParams(0,scaled(124),1); filmSize.rightMargin=gap;
            films.addView(tile("Filmy","movie",false,() -> open("movie",null,false)),filmSize);
            films.addView(tile("Seriale","series",false,() -> open("series",null,false)),new LinearLayout.LayoutParams(0,scaled(124),1));
            LinearLayout.LayoutParams rowSize=new LinearLayout.LayoutParams(-1,-2); rowSize.topMargin=gap; header.addView(films,rowSize);
            // Everything about the source and the app in one small, labelled 2×2 grid.
            TextView manage=text("ZARZĄDZANIE",12,Ui.MUTED); manage.setTypeface(null,Typeface.BOLD); manage.setLetterSpacing(.08f); manage.setPadding(dp(4),dp(22),0,dp(10)); header.addView(manage);
            View[][] grid={{action("Konto","account",this::account),action("Zmień źródło","source",() -> open("tv",null,true))},{action("Odśwież źródło","refresh",this::refreshSource),action("Ustawienia","settings",this::settings)}};
            for(View[] pair:grid) {
                LinearLayout line=new LinearLayout(this);
                LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,scaled(56),1); left.rightMargin=gap; line.addView(pair[0],left);
                line.addView(pair[1],new LinearLayout.LayoutParams(0,scaled(56),1));
                LinearLayout.LayoutParams lineSize=new LinearLayout.LayoutParams(-1,-2); lineSize.bottomMargin=gap; header.addView(line,lineSize);
            }
        }
        header.setPadding(0,0,0,dp(14)); page.addView(header); root.addView(page,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
    }
    private void refreshSource() {
        startActivity(new Intent(this,MainActivity.class).putExtra("section","tv").putExtra("refresh_source",true));
    }
    private void account() {
        if(loadingAccount) return; loadingAccount=true;
        ProgressDialog waiting=new ProgressDialog(this); waiting.setMessage("Odczyt konta i biblioteki…"); waiting.show();
        worker.execute(() -> {
            try {
                List<Channel> loaded=new LibraryStore(this).loadSection("tv",new HashSet<>());
                runOnUiThread(() -> { if(!isDestroyed()) { tv=loaded; loadingAccount=false; waiting.dismiss(); showAccount(); } });
            } catch(Exception e) { runOnUiThread(() -> { if(!isDestroyed()) { loadingAccount=false; waiting.dismiss(); new AlertDialog.Builder(this).setMessage("Nie można odczytać biblioteki.").setPositiveButton("OK",null).show(); } }); }
        });
    }
    private void showAccount() {
        String host="Lista z telefonu lub adres M3U";
        if(!tv.isEmpty()) { android.net.Uri uri=android.net.Uri.parse(tv.get(0).url()); if(uri.getHost()!=null) host=uri.getHost(); }
        new AlertDialog.Builder(this).setTitle("Konto i biblioteka").setMessage("Źródło: "+host+"\nKanały TV: "+tv.size()+"\nBukiety TV: "+new HashSet<String>(tv.stream().map(Channel::group).collect(java.util.stream.Collectors.toList())).size()+"\n\nDane dostępu są przechowywane w zaszyfrowanej bibliotece.")
            .setPositiveButton("Zmień źródło",(d,w) -> open("tv",null,true)).setNegativeButton("Zamknij",null).show();
    }
    private void settings() {
        new AlertDialog.Builder(this).setTitle("Ustawienia").setItems(new String[]{"Źródło: dodaj lub zmień","Źródło: odśwież listę","Połączenie internetowe (bufor)","Uprawnienia aplikacji i PiP","O aplikacji","Program TV (EPG): własny link XMLTV"},(d,choice) -> {
            if(choice==5) { EpgSettings.choose(this,() -> Toast.makeText(this,EpgSettings.custom(this)==null?"Używam EPG dostawcy":"Zapisano własne źródło EPG",Toast.LENGTH_SHORT).show()); return; }
            if(choice==1) { refreshSource(); return; }
            if(choice==2) { PlaybackSettings.choose(this,() -> {}); return; }
            int index=choice==0?0:choice-2;
            if(index==0) open("tv",null,true);
            if(index==1) startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));
            if(index==2) new AlertDialog.Builder(this).setTitle(Ui.APP_NAME+" "+BuildConfig.VERSION_NAME).setMessage("Telewizja na żywo, filmy i seriale z Twojego źródła IPTV.\nProgram TV (EPG) i archiwum zależą od dostawcy.").setPositiveButton("OK",null).show();
        }).show();
    }
    @Override public void onConfigurationChanged(Configuration configuration) { super.onConfigurationChanged(configuration); build(); }
    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }

    private static final class Icon extends View {
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

