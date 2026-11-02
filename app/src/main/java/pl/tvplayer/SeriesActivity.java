package pl.tvplayer;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Full series detail page: artwork, provider synopsis, season selector and virtualized episodes. */
public final class SeriesActivity extends Activity {
    private record Cached(SeriesDetails details,long until) {}
    private static final Map<String,Cached> CACHE=new LinkedHashMap<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final SourceClient client=new SourceClient();
    private final Set<String> favorites=new HashSet<>();
    private Channel source;
    private SeriesDetails details;
    private boolean television,loaded;
    private int selectedSeason;
    private ListView list;
    private LinearLayout header;
    private Button play,star;
    private TextView playHint;
    private BaseAdapter adapter;
    private List<SeriesDetails.Episode> episodes=Collections.emptyList();
    private SeriesDetails.Episode next;
    public static Intent intent(Context context,Channel channel,boolean tv) {
        return new Intent(context,SeriesActivity.class).putExtra("url",channel.url()).putExtra("name",channel.name()).putExtra("group",channel.group()).putExtra("logo",channel.logo()).putExtra("television",tv);
    }
    private int dp(int value) { return Ui.dp(this,value); }
    private LinearLayout column() { LinearLayout layout=new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private TextView text(String value,int size,int color) { return Ui.text(this,value,size,color); }
    private Button button(String label) {
        Button button=Ui.button(this,label); button.setTextSize(television?18:16); button.setFocusable(true); button.setFocusableInTouchMode(television);
        if(television) { StateListDrawable background=new StateListDrawable(); background.addState(new int[]{android.R.attr.state_focused},Ui.shape(this,0xFF394253,10,Ui.TEXT)); background.addState(new int[]{},Ui.shape(this,Ui.SURFACE_HIGH,10,Ui.BORDER)); button.setBackground(background); button.setTextColor(Ui.TEXT); }
        return button;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); String url=getIntent().getStringExtra("url"); if(url==null) { finish(); return; }
        television=getIntent().getBooleanExtra("television",false)||(getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_TYPE_MASK)==android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
        if(television) { setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE); getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN); }
        source=new Channel(getIntent().getStringExtra("name"),url,getIntent().getStringExtra("group"),"","series").withLogo(getIntent().getStringExtra("logo"));
        if(state!=null) selectedSeason=state.getInt("season",0);
        list=new ListView(this); list.setBackgroundColor(Ui.BG); list.setDivider(new ColorDrawable(Ui.BG)); list.setDividerHeight(dp(10)); list.setHeaderDividersEnabled(false); list.setFooterDividersEnabled(false); list.setItemsCanFocus(television); list.setClipToPadding(false);
        if(television) { list.setDrawSelectorOnTop(true); list.setSelector(Ui.shape(this,0,12,Ui.ACCENT)); }
        list.setOnApplyWindowInsetsListener((view,insets) -> { view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()+dp(16)); return insets.consumeSystemWindowInsets(); });
        header=column(); list.addHeaderView(header,null,false);
        adapter=new BaseAdapter() {
            public int getCount() { return episodes.size(); } public Object getItem(int p) { return episodes.get(p); } public long getItemId(int p) { return p; }
            public View getView(int p,View reused,ViewGroup parent) { return episodeRow(episodes.get(p)); }
        };
        list.setAdapter(adapter); list.setOnItemClickListener((parent,v,index,id) -> { int row=index-list.getHeaderViewsCount(); if(row>=0&&row<episodes.size()) play(episodes.get(row)); });
        setContentView(list); loading(); load();
    }
    private Button back() { Button back=button("‹"); back.setTextSize(30); back.setContentDescription("Wróć do seriali"); back.setOnClickListener(v -> finish()); return back; }
    private void loading() { header.removeAllViews(); header.setPadding(dp(20),dp(16),dp(20),dp(20)); header.addView(back(),new LinearLayout.LayoutParams(dp(48),dp(48))); TextView title=text(SeriesDetails.cleanSeriesTitle(source.name()),26,Ui.TEXT); title.setPadding(0,dp(28),0,dp(20)); header.addView(title); ProgressBar spinner=new ProgressBar(this); header.addView(spinner,new LinearLayout.LayoutParams(dp(44),dp(44))); header.addView(text("Wczytywanie serialu…",16,Ui.MUTED)); }
    private void load() {
        Cached cached; synchronized(CACHE) { cached=CACHE.get(source.url()); }
        final Cached hit=cached!=null&&cached.until()>System.currentTimeMillis()?cached:null;
        worker.execute(() -> {
            try {
                Set<String> saved=new LibraryStore(this).loadFavorites();
                SeriesDetails result=hit!=null?hit.details():client.seriesDetails(source);
                synchronized(CACHE) { CACHE.put(source.url(),new Cached(result,System.currentTimeMillis()+900000)); while(CACHE.size()>20) CACHE.remove(CACHE.keySet().iterator().next()); }
                runOnUiThread(() -> { if(isDestroyed()) return; favorites.clear(); favorites.addAll(saved); details=result; loaded=true; render(); });
            } catch(Exception e) { runOnUiThread(() -> { if(isDestroyed()) return; header.removeAllViews(); header.setPadding(dp(20),dp(16),dp(20),dp(20)); header.addView(back(),new LinearLayout.LayoutParams(dp(48),dp(48))); TextView title=text(SeriesDetails.cleanSeriesTitle(source.name()),26,Ui.TEXT); title.setPadding(0,dp(24),0,dp(20)); header.addView(title); header.addView(text("Nie udało się pobrać danych serialu. Sprawdź połączenie i dostępność odcinków.",16,Ui.MUTED)); Button retry=button("Spróbuj ponownie"); retry.setOnClickListener(v -> { loading(); load(); }); header.addView(retry); }); }
        });
    }
    private void render() {
        header.removeAllViews(); header.setPadding(0,0,0,0);
        FrameLayout hero=new FrameLayout(this); hero.setBackgroundColor(Ui.SURFACE); int heroHeight=dp(television?410:232);
        ImageView artwork=new ImageView(this); artwork.setScaleType(details.backdrop().isEmpty()?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP); hero.addView(artwork,new FrameLayout.LayoutParams(-1,-1));
        ImageLoader.get(this).load(details.backdrop().isEmpty()?details.cover():details.backdrop(),artwork,getResources().getDisplayMetrics().widthPixels,heroHeight);
        View scrim=new View(this); scrim.setBackground(new GradientDrawable(television?GradientDrawable.Orientation.LEFT_RIGHT:GradientDrawable.Orientation.TOP_BOTTOM,television?new int[]{Ui.BG,0xDD0E0F11,0x220E0F11}:new int[]{0x220E0F11,0x110E0F11,Ui.BG})); hero.addView(scrim,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout copy=column(); copy.setPadding(dp(television?32:18),dp(television?68:0),dp(television?32:18),dp(18));
        TextView name=text(details.title(),television?34:26,Ui.TEXT); name.setTypeface(null,Typeface.BOLD); name.setMaxLines(television?2:3); copy.addView(name);
        List<String> meta=new ArrayList<>(); if(!details.year().isEmpty()) meta.add(details.year()); if(!details.rating().isEmpty()&&!details.rating().equals("0")) meta.add("★ "+details.rating()); if(!details.seasons().isEmpty()) meta.add(details.seasons().size()==1?"1 sezon":details.seasons().size()+" sezony");
        if(!meta.isEmpty()) { TextView info=text(android.text.TextUtils.join("   ·   ",meta),television?16:14,Ui.MUTED); info.setPadding(0,dp(10),0,dp(4)); copy.addView(info); }
        LinearLayout actions=new LinearLayout(this); actions.setPadding(0,dp(14),0,dp(10));
        play=button("▶  Odtwórz"); if(!television) Ui.primary(play); play.setContentDescription("Odtwórz serial"); play.setOnClickListener(v -> { if(next!=null) play(next); });
        LinearLayout.LayoutParams primary=new LinearLayout.LayoutParams(0,dp(52),1); primary.rightMargin=dp(10); actions.addView(play,primary);
        star=button("+  Moja lista"); star.setTextSize(television?17:14); star.setOnClickListener(v -> toggleFavorite()); actions.addView(star,new LinearLayout.LayoutParams(dp(television?154:126),dp(52))); copy.addView(actions);
        playHint=text("",television?15:13,Ui.MUTED); copy.addView(playHint);
        TextView synopsis=text(details.overview().isEmpty()?"Opis nie został udostępniony przez dostawcę.":details.overview(),television?17:15,Ui.TEXT); synopsis.setMaxLines(television?2:4); synopsis.setEllipsize(TextUtils.TruncateAt.END); synopsis.setLineSpacing(dp(3),1); synopsis.setPadding(0,dp(14),0,0); copy.addView(synopsis);
        if(!details.overview().isEmpty()) { synopsis.setContentDescription("Opis serialu. Wybierz, aby przeczytać całość."); synopsis.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(details.title()).setMessage(details.overview()).setPositiveButton("Zamknij",null).show()); }
        if(!television && !details.genre().isEmpty()) { TextView genre=text(details.genre(),television?15:13,Ui.MUTED); genre.setPadding(0,dp(12),0,0); copy.addView(genre); }
        if(television) { FrameLayout.LayoutParams infoSize=new FrameLayout.LayoutParams(Math.max(dp(420),getResources().getDisplayMetrics().widthPixels/2),-1,Gravity.LEFT); hero.addView(copy,infoSize); }
        header.addView(hero,new LinearLayout.LayoutParams(-1,heroHeight)); if(!television) header.addView(copy);
        Button back=back(); FrameLayout.LayoutParams backSize=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.LEFT); backSize.leftMargin=dp(television?26:14); backSize.topMargin=dp(12); hero.addView(back,backSize);
        TextView brand=text("TV PLAYER",12,Ui.MUTED); brand.setTypeface(null,Typeface.BOLD); FrameLayout.LayoutParams brandSize=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT); brandSize.rightMargin=dp(18); brandSize.topMargin=dp(26); hero.addView(brand,brandSize);
        LinearLayout seasonBar=new LinearLayout(this); seasonBar.setPadding(dp(television?32:18),dp(16),dp(television?32:18),dp(8)); seasonBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView label=text("Odcinki",television?24:20,Ui.TEXT); label.setTypeface(null,Typeface.BOLD); seasonBar.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        if(!details.seasons().isEmpty()) {
            Spinner seasons=new Spinner(this); seasons.setContentDescription("Wybierz sezon"); seasons.setBackground(Ui.touch(this,Ui.SURFACE_HIGH,10,Ui.BORDER));
            List<String> names=new ArrayList<>(); for(SeriesDetails.Season season:details.seasons()) names.add(season.title());
            ArrayAdapter<String> choices=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,names) {
                @Override public View getView(int p,View reused,ViewGroup parent) { TextView view=(TextView)super.getView(p,reused,parent); view.setTextColor(Ui.TEXT); view.setTextSize(television?18:16); view.setPadding(dp(14),dp(8),dp(14),dp(8)); return view; }
                @Override public View getDropDownView(int p,View reused,ViewGroup parent) { TextView view=(TextView)super.getDropDownView(p,reused,parent); view.setTextColor(Ui.TEXT); view.setTextSize(18); view.setBackgroundColor(Ui.SURFACE_HIGH); view.setPadding(dp(16),dp(12),dp(16),dp(12)); return view; }
            }; choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); seasons.setAdapter(choices); seasonBar.addView(seasons,new LinearLayout.LayoutParams(dp(television?210:164),dp(48))); selectedSeason=Math.min(selectedSeason,details.seasons().size()-1); seasons.setSelection(selectedSeason);
            seasons.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() { public void onNothingSelected(AdapterView<?> parent) {} public void onItemSelected(AdapterView<?> parent,View view,int position,long id) { selectedSeason=position; episodes=details.seasons().get(position).episodes(); adapter.notifyDataSetChanged(); updateProgress(); } });
            episodes=details.seasons().get(selectedSeason).episodes();
        } else { episodes=Collections.emptyList(); }
        header.addView(seasonBar);
        if(episodes.isEmpty()) { TextView empty=text("Dostawca nie udostępnił odcinków tego serialu.",16,Ui.MUTED); empty.setPadding(dp(18),dp(16),dp(18),dp(24)); header.addView(empty); }
        adapter.notifyDataSetChanged(); updateProgress(); if(television && next!=null) play.post(() -> play.requestFocus());
    }
    private void updateProgress() {
        if(details==null || play==null) return; next=WatchProgress.next(this,details.allEpisodes());
        boolean continuing=next!=null&&WatchProgress.read(this,next.channel().url()).position()>=5000&&!WatchProgress.read(this,next.channel().url()).completed();
        play.setText(continuing?"▶  Kontynuuj":"▶  Odtwórz"); play.setEnabled(next!=null); playHint.setText(next==null?"":"Sezon "+next.season()+" · Odcinek "+next.number()+" · "+next.title()); star.setText(favorites.contains(source.url())?"✓  Moja lista":"+  Moja lista");
    }
    private View episodeRow(SeriesDetails.Episode episode) {
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.TOP); row.setPadding(dp(television?32:18),dp(10),dp(television?32:18),dp(12)); row.setMinimumHeight(dp(television?146:112)); row.setBackground(Ui.touch(this,Ui.BG,10,Ui.BORDER));
        row.setFocusable(television); row.setFocusableInTouchMode(television); row.setClickable(true); row.setContentDescription("Odcinek "+episode.number()+": "+episode.title()); row.setOnClickListener(v -> play(episode));
        FrameLayout thumb=new FrameLayout(this); thumb.setBackground(Ui.shape(this,Ui.SURFACE_HIGH,8,0)); thumb.setClipToOutline(true); int width=dp(television?220:128),height=width*9/16;
        ImageView image=new ImageView(this); image.setScaleType(episode.image().isEmpty()?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP); thumb.addView(image,new FrameLayout.LayoutParams(-1,-1)); ImageLoader.get(this).load(episode.image().isEmpty()?details.cover():episode.image(),image,width,height);
        TextView icon=text("▶",television?26:20,Ui.TEXT); icon.setGravity(Gravity.CENTER); icon.setShadowLayer(dp(4),0,0,0xFF000000); thumb.addView(icon,new FrameLayout.LayoutParams(-1,-1));
        WatchProgress.Entry progress=WatchProgress.read(this,episode.channel().url());
        if(progress.duration()>0 && (progress.position()>0||progress.completed())) { ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); bar.setMax(1000); bar.setProgress(progress.completed()?1000:(int)Math.min(1000,progress.position()*1000/progress.duration())); bar.setProgressTintList(ColorStateList.valueOf(Ui.TEXT)); FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(-1,dp(4),Gravity.BOTTOM); thumb.addView(bar,size); }
        row.addView(thumb,new LinearLayout.LayoutParams(width,height));
        LinearLayout copy=column(); copy.setPadding(dp(14),0,0,0); TextView name=text(episode.number()+". "+episode.title(),television?21:16,Ui.TEXT); name.setTypeface(null,Typeface.BOLD); name.setMaxLines(2); name.setEllipsize(TextUtils.TruncateAt.END); copy.addView(name);
        String metadata=episode.durationSeconds()>0?Math.max(1,(episode.durationSeconds()+59)/60)+" min":""; if(progress.completed()) metadata+=(metadata.isEmpty()?"":" · ")+"Obejrzano"; else if(progress.position()>5000) metadata+=(metadata.isEmpty()?"":" · ")+"W trakcie";
        if(!metadata.isEmpty()) { TextView length=text(metadata,television?16:12,Ui.MUTED); length.setPadding(0,dp(5),0,0); copy.addView(length); }
        if(!episode.overview().isEmpty()) { TextView plot=text(episode.overview(),television?16:13,Ui.MUTED); plot.setMaxLines(television?3:3); plot.setEllipsize(TextUtils.TruncateAt.END); plot.setPadding(0,dp(7),0,0); copy.addView(plot); }
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1)); return row;
    }
    private void play(SeriesDetails.Episode episode) { Channel channel=episode.channel(); startActivity(new Intent(this,PlayerActivity.class).putExtra("url",channel.url()).putExtra("name",channel.name()).putExtra("kind","episode").putExtra("television",television)); }
    private void toggleFavorite() { if(!favorites.add(source.url())) favorites.remove(source.url()); updateProgress(); Set<String> saved=new HashSet<>(favorites); worker.execute(() -> { try { new LibraryStore(this).saveFavorites(saved); } catch(Exception e) { runOnUiThread(() -> { if(!isDestroyed()) Toast.makeText(this,"Nie udało się zapisać Mojej listy.",Toast.LENGTH_SHORT).show(); }); } }); }
    @Override protected void onResume() { super.onResume(); if(loaded) { updateProgress(); adapter.notifyDataSetChanged(); } }
    @Override protected void onDestroy() { client.cancelSeriesRequest(); worker.shutdown(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putInt("season",selectedSeason); super.onSaveInstanceState(state); }
}
