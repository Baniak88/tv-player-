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
    private List<String> names=new ArrayList<>();
    private final Map<String,Integer> counts=new TreeMap<>();
    private ListView page;
    private TextView summary;
    private boolean loading;
    private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String s,int size,int color) { TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t; }
    private GradientDrawable panel(int start,int end,int border) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{start,end});
        d.setCornerRadius(dp(5)); d.setStroke(dp(1),border); return d;
    }
    private Drawable interactive(boolean prominent) {
        StateListDrawable states=new StateListDrawable();
        Drawable selected=panel(0xFFB91C38,0xFF6F0920,0xFFFFD9DF);
        states.addState(new int[]{android.R.attr.state_pressed},selected);
        states.addState(new int[]{android.R.attr.state_focused},panel(0xFFAC1932,0xFF720D24,0xFFFFFFFF));
        states.addState(new int[]{},panel(prominent?0xFF99132B:0xFF471020,prominent?0xFF620B1C:0xFF200E19,prominent?0xFFE24B63:0xFF652032));
        return states;
    }
    private View tile(String label,String icon,boolean large,Runnable click) {
        LinearLayout box=column(); box.setGravity(Gravity.CENTER); box.setPadding(dp(3),dp(6),dp(3),dp(6));
        box.setBackground(interactive(large)); box.setFocusable(true); box.setClickable(true); box.setContentDescription(label);
        Icon image=new Icon(this,icon); box.addView(image,new LinearLayout.LayoutParams(dp(large?62:30),dp(large?62:30)));
        TextView name=text(label,large?16:12,Color.WHITE); name.setGravity(Gravity.CENTER); name.setMaxLines(2);
        name.setPadding(0,dp(large?16:6),0,0); box.addView(name); box.setOnClickListener(v -> click.run()); return box;
    }
    private View action(String label,String icon,Runnable click) {
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(8),dp(4),dp(5),dp(4));
        row.setBackground(interactive(false)); row.setFocusable(true); row.setClickable(true); row.setContentDescription(label);
        row.addView(new Icon(this,icon),new LinearLayout.LayoutParams(dp(21),dp(21)));
        TextView t=text(label,12,0xFFF1DBE0); t.setPadding(dp(6),0,0,0); t.setMaxLines(2); row.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        row.setOnClickListener(v -> click.run()); return row;
    }
    @Override public void onCreate(Bundle state) { super.onCreate(state); build(); }
    private void open(String section,String group,boolean source) {
        Intent intent=new Intent(this,MainActivity.class).putExtra("section",section);
        if(group!=null) intent.putExtra("bouquet",group);
        if(source) intent.putExtra("open_source",true);
        startActivity(intent);
    }
    private void build() {
        boolean wide=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout root=column();
        GradientDrawable backdrop=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF0B1124,0xFF120B18,0xFF290910,0xFF05070C}); root.setBackground(backdrop);
        root.setOnApplyWindowInsetsListener((view,insets) -> {
            view.setPadding(dp(14),insets.getSystemWindowInsetTop(),dp(14),insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        page=new ListView(this); page.setDivider(null); page.setCacheColorHint(Color.TRANSPARENT); page.setSelector(new ColorDrawable(Color.TRANSPARENT));
        LinearLayout header=column();
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(2),dp(12),dp(2),dp(4));
        TextView version=text("TV PLAYER  /  0.5",11,0xFFB98997); version.setLetterSpacing(.1f); top.addView(version,new LinearLayout.LayoutParams(0,-2,1));
        TextClock clock=new TextClock(this); clock.setFormat24Hour("HH:mm"); clock.setFormat12Hour("HH:mm"); clock.setTextColor(0xFFCFB9C5); clock.setTextSize(12); top.addView(clock); header.addView(top);
        TextView brand=text("TV PLAYER",wide?24:32,Color.WHITE); brand.setTypeface(null,Typeface.BOLD); brand.setLetterSpacing(.13f); brand.setGravity(Gravity.CENTER);
        brand.setPadding(0,dp(wide?10:36),0,dp(8)); header.addView(brand);
        TextView tag=text("TELEWIZJA  •  FILMY  •  SERIALE",10,0xFFB18C9B); tag.setLetterSpacing(.12f); tag.setGravity(Gravity.CENTER); tag.setPadding(0,0,0,dp(wide?14:30)); header.addView(tag);
        int gap=dp(7), height=dp(wide?152:196);
        LinearLayout menu=new LinearLayout(this);
        LinearLayout.LayoutParams liveSize=new LinearLayout.LayoutParams(0,height,1.05f); liveSize.rightMargin=gap;
        menu.addView(tile("Telewizja na żywo","tv",true,() -> open("tv",null,false)),liveSize);
        LinearLayout middle=column(); LinearLayout first=new LinearLayout(this),second=new LinearLayout(this);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-1,1); left.rightMargin=gap;
        first.addView(tile("Filmy","movie",false,() -> open("movie",null,false)),left);
        first.addView(tile("Seriale","series",false,() -> open("series",null,false)),new LinearLayout.LayoutParams(0,-1,1));
        second.addView(tile("Konto","account",false,this::account),new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout.LayoutParams sourceSize=new LinearLayout.LayoutParams(0,-1,1); sourceSize.leftMargin=gap;
        second.addView(tile("Zmień źródło","source",false,() -> open("tv",null,true)),sourceSize);
        LinearLayout.LayoutParams row1=new LinearLayout.LayoutParams(-1,0,1); row1.bottomMargin=gap; middle.addView(first,row1);
        middle.addView(second,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout.LayoutParams midSize=new LinearLayout.LayoutParams(0,height,1.25f); midSize.rightMargin=gap; menu.addView(middle,midSize);
        LinearLayout actions=column();
        View[] buttons={action("Ustawienia","settings",this::settings),action("Odśwież widok","refresh",this::reload),action("Wyjście","exit",this::finish)};
        for(int i=0;i<buttons.length;i++) { LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,0,1); if(i<2) size.bottomMargin=gap; actions.addView(buttons[i],size); }
        menu.addView(actions,new LinearLayout.LayoutParams(0,height,1.05f)); header.addView(menu);
        TextView heading=text("BUKIETY TV",14,Color.WHITE); heading.setTypeface(null,Typeface.BOLD); heading.setLetterSpacing(.08f); heading.setPadding(dp(2),dp(30),0,dp(7)); header.addView(heading);
        summary=text(loading?"Wczytywanie zapisanych bukietów…":"",12,0xFFAD8C9A); summary.setPadding(dp(2),0,0,dp(16)); header.addView(summary);
        page.addHeaderView(header,null,false); root.addView(page,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root); bind();
    }
    private void bind() {
        counts.clear(); for(Channel channel:tv) counts.put(channel.group(),counts.getOrDefault(channel.group(),0)+1);
        names=new ArrayList<>(counts.keySet());
        summary.setText(loading?"Wczytywanie zapisanych bukietów…":names.isEmpty()?"Brak bukietów. Wybierz „Zmień źródło”, aby dodać listę.":names.size()+" bukietów  ·  "+tv.size()+" kanałów  ·  wybierz temat");
        page.setAdapter(new BaseAdapter() {
            public int getCount() { return (names.size()+1)/2; }
            public Object getItem(int position) { return position; }
            public long getItemId(int position) { return position; }
            public View getView(int position,View convert,ViewGroup parent) {
                LinearLayout row=convert instanceof LinearLayout?(LinearLayout)convert:new LinearLayout(HomeActivity.this); row.removeAllViews();
                for(int col=0;col<2;col++) {
                    int index=position*2+col;
                    LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(98),1); size.bottomMargin=dp(10); if(col==0) size.rightMargin=dp(10);
                    if(index>=names.size()) { row.addView(new View(HomeActivity.this),size); continue; }
                    String name=names.get(index);
                    LinearLayout card=column(); card.setPadding(dp(14),dp(14),dp(10),dp(10)); card.setBackground(interactive(false)); card.setFocusable(true); card.setClickable(true);
                    TextView title=text(name,16,Color.WHITE); title.setTypeface(null,Typeface.BOLD); title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    card.addView(title,new LinearLayout.LayoutParams(-1,0,1));
                    card.addView(text(counts.get(name)+" kanałów   ›",12,0xFFD6A0AE)); card.setContentDescription("Bukiet "+name);
                    card.setOnClickListener(v -> open("tv",name,false)); row.addView(card,size);
                }
                return row;
            }
        });
    }
    @Override protected void onResume() { super.onResume(); reload(); }
    private void reload() {
        if(loading) return; loading=true; bind();
        worker.execute(() -> {
            try {
                List<Channel> loaded=new LibraryStore(this).loadSection("tv",new HashSet<>());
                runOnUiThread(() -> { if(!isDestroyed()) { tv=loaded; loading=false; bind(); } });
            } catch(Exception e) { runOnUiThread(() -> { if(!isDestroyed()) { loading=false; bind(); summary.setText("Nie udało się odczytać bukietów. Wybierz „Zmień źródło”."); } }); }
        });
    }
    private void account() {
        String host="Lista z telefonu lub adres M3U";
        if(!tv.isEmpty()) { android.net.Uri uri=android.net.Uri.parse(tv.get(0).url()); if(uri.getHost()!=null) host=uri.getHost(); }
        new AlertDialog.Builder(this).setTitle("Konto i biblioteka").setMessage("Źródło: "+host+"\nKanały TV: "+tv.size()+"\nBukiety TV: "+counts.size()+"\n\nDane dostępu są przechowywane w zaszyfrowanej bibliotece.")
            .setPositiveButton("Zmień źródło",(d,w) -> open("tv",null,true)).setNegativeButton("Zamknij",null).show();
    }
    private void settings() {
        new AlertDialog.Builder(this).setTitle("Ustawienia").setItems(new String[]{"Dodaj lub zmień źródło","Uprawnienia aplikacji i PiP","Informacje o wersji"},(d,index) -> {
            if(index==0) open("tv",null,true);
            if(index==1) startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));
            if(index==2) new AlertDialog.Builder(this).setTitle("TV Player Preview 0.5.0").setMessage("TV, filmy, seriale i widoczne bukiety.\nEPG i archiwum zależą od źródła.").setPositiveButton("OK",null).show();
        }).show();
    }
    @Override public void onConfigurationChanged(Configuration configuration) { super.onConfigurationChanged(configuration); build(); }
    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }

    private static final class Icon extends View {
        private final String kind;
        private final Paint paint=new Paint(3);
        Icon(Context context,String kind) { super(context); this.kind=kind; setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); canvas.save(); float scale=Math.min(getWidth(),getHeight())/48f;
            canvas.translate((getWidth()-48*scale)/2,(getHeight()-48*scale)/2); canvas.scale(scale,scale);
            paint.setColor(Color.WHITE); paint.setStrokeWidth(2.4f); paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
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
