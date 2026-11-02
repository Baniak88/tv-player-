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
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private final SourceClient client=new SourceClient();
    private final Set<String> favorites=new HashSet<>();
    private Channel source;
    private SeriesDetails details;
    private boolean television,loaded,favoritesReady;
    private List<SeriesDetails.Episode> allEpisodes=Collections.emptyList();
    private Map<String,WatchProgress.Entry> progressEntries=Collections.emptyMap();
    private Button seasonButton;
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
            public View getView(int p,View reused,ViewGroup parent) { return episodeRow(episodes.get(p),reused); }
        };
        list.setAdapter(adapter); list.setOnItemClickListener((parent,v,index,id) -> { int row=index-list.getHeaderViewsCount(); if(row>=0&&row<episodes.size()) play(episodes.get(row)); });
        if(television) setContentView(list);
        else {
            FrameLayout page=new FrameLayout(this); page.setBackgroundColor(Ui.BG);
            page.setOnApplyWindowInsetsListener((v,insets) -> { v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets.consumeSystemWindowInsets(); });
            list.setOnApplyWindowInsetsListener(null); list.setPadding(0,0,0,dp(16)); page.addView(list,new FrameLayout.LayoutParams(-1,-1)); page.addView(Ui.detailBar(this,"Seriale",this::finish),new FrameLayout.LayoutParams(-1,dp(52),Gravity.TOP)); setContentView(page);
        }
        loading(); load();
    }
    private Button back() { Button back=button("‹"); back.setTextSize(30); back.setContentDescription("Wróć do seriali"); back.setOnClickListener(v -> finish()); return back; }
    private void loading() { header.removeAllViews(); header.setPadding(dp(20),dp(television?16:68),dp(20),dp(20)); if(television) header.addView(back(),new LinearLayout.LayoutParams(dp(48),dp(48))); TextView title=text(SeriesDetails.cleanSeriesTitle(source.name()),26,Ui.TEXT); title.setPadding(0,dp(28),0,dp(20)); header.addView(title); ProgressBar spinner=new ProgressBar(this); header.addView(spinner,new LinearLayout.LayoutParams(dp(44),dp(44))); header.addView(text("Wczytywanie serialu…",16,Ui.MUTED)); }
    private void load() {
        worker.execute(() -> { try { Set<String> saved=new LibraryStore(this).loadFavorites(); runOnUiThread(() -> { if(!isDestroyed()) { favorites.clear(); favorites.addAll(saved); favoritesReady=true; updateProgress(); } }); } catch(Exception ignored) {} });
        Cached cached; synchronized(CACHE) { cached=CACHE.get(source.url()); }
        final Cached memory=cached!=null && cached.until()>System.currentTimeMillis()?cached:null;
        network.execute(() -> {
            boolean shown=false;
            try {
                if(memory!=null) { applyDetails(memory.details()); return; }
                MetadataCache disk=new MetadataCache(this); MetadataCache.Hit<SeriesDetails> hit=television?null:disk.read("series:"+source.url(),SeriesDetails.class);
                if(hit!=null && hit.value().seasons()!=null) { applyDetails(hit.value()); shown=true; if(hit.fresh()) { remember(hit.value()); return; } }
                SeriesDetails result=client.seriesDetails(source); remember(result);
                if(!television) disk.write("series:"+source.url(),result); applyDetails(result);
            } catch(Exception e) { if(shown) return; runOnUiThread(() -> { if(isDestroyed()) return; header.removeAllViews(); header.setPadding(dp(20),dp(television?16:68),dp(20),dp(20)); if(television) header.addView(back(),new LinearLayout.LayoutParams(dp(48),dp(48))); TextView title=text(SeriesDetails.cleanSeriesTitle(source.name()),26,Ui.TEXT); title.setPadding(0,dp(24),0,dp(20)); header.addView(title); header.addView(text("Nie udało się pobrać danych serialu. Sprawdź połączenie i dostępność odcinków.",16,Ui.MUTED)); Button retry=button("Spróbuj ponownie"); retry.setOnClickListener(v -> { loading(); load(); }); header.addView(retry); }); }
        });
    }
    private void remember(SeriesDetails value) { synchronized(CACHE) { CACHE.put(source.url(),new Cached(value,System.currentTimeMillis()+900000)); while(CACHE.size()>20) CACHE.remove(CACHE.keySet().iterator().next()); } }
    private void applyDetails(SeriesDetails value) {
        List<SeriesDetails.Episode> items=value.allEpisodes(); Map<String,WatchProgress.Entry> progress=WatchProgress.readEntries(this,items);
        runOnUiThread(() -> { if(isDestroyed()) return; int first=list.getFirstVisiblePosition(),top=list.getChildAt(0)==null?0:list.getChildAt(0).getTop(); boolean preserve=loaded; details=value; allEpisodes=items; progressEntries=progress; loaded=true; render(); if(preserve) list.setSelectionFromTop(first,top); });
    }
    private void selectSeason(int index) {
        if(details==null || index==selectedSeason || index<0 || index>=details.seasons().size()) return;
        int first=list.getFirstVisiblePosition(),top=list.getChildAt(0)==null?0:list.getChildAt(0).getTop(); selectedSeason=index; episodes=details.seasons().get(index).episodes();
        if(seasonButton!=null) seasonButton.setText(details.seasons().get(index).title()+"  ▾");
        adapter.notifyDataSetChanged(); updateProgress(); list.setSelectionFromTop(Math.min(first,episodes.size()),top);
    }
    private void render() {
        header.removeAllViews(); header.setPadding(0,0,0,0); if(!television && details.cover().isEmpty() && details.backdrop().isEmpty()) header.addView(new View(this),new LinearLayout.LayoutParams(-1,dp(52)));
        FrameLayout hero=new FrameLayout(this); hero.setBackgroundColor(Ui.SURFACE); int heroHeight=television?dp(410):Math.min(dp(260),Math.max(dp(160),getResources().getDisplayMetrics().widthPixels*9/16)); hero.setMinimumHeight(heroHeight);
        ImageView artwork=new ImageView(this); artwork.setScaleType(details.backdrop().isEmpty()?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP); hero.addView(artwork,new FrameLayout.LayoutParams(-1,-1));
        ImageLoader.get(this).load(details.backdrop().isEmpty()?details.cover():details.backdrop(),artwork,getResources().getDisplayMetrics().widthPixels,heroHeight);
        View scrim=new View(this); scrim.setBackground(new GradientDrawable(television?GradientDrawable.Orientation.LEFT_RIGHT:GradientDrawable.Orientation.TOP_BOTTOM,television?new int[]{Ui.BG,0xDD0E0F11,0x220E0F11}:new int[]{0x220E0F11,0x110E0F11,Ui.BG})); hero.addView(scrim,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout copy=column(); copy.setPadding(dp(television?32:18),dp(television?58:0),dp(television?32:18),dp(18));
        TextView name=text(details.title(),television?30:26,Ui.TEXT); name.setTypeface(null,Typeface.BOLD); name.setMaxLines(television?2:3); copy.addView(name);
        List<String> meta=new ArrayList<>(); if(!details.year().isEmpty()) meta.add(details.year()); if(!details.rating().isEmpty()&&!details.rating().equals("0")) meta.add("★ "+details.rating()); if(!details.seasons().isEmpty()) meta.add(details.seasons().size()==1?"1 sezon":details.seasons().size()+((details.seasons().size()%10>=2 && details.seasons().size()%10<=4 && (details.seasons().size()%100<12 || details.seasons().size()%100>14))?" sezony":" sezonów"));
        if(!meta.isEmpty()) { TextView info=text(android.text.TextUtils.join("   ·   ",meta),television?16:14,Ui.MUTED); info.setPadding(0,dp(10),0,dp(4)); copy.addView(info); }
        LinearLayout actions=new LinearLayout(this); actions.setPadding(0,dp(14),0,dp(10));
        play=button("▶  Odtwórz"); if(!television) Ui.primary(play); play.setContentDescription("Odtwórz serial"); play.setOnClickListener(v -> { if(next!=null) play(next); });
        LinearLayout.LayoutParams primary=new LinearLayout.LayoutParams(0,dp(52),1); primary.rightMargin=dp(10); actions.addView(play,primary);
        star=button("+  Moja lista"); star.setTextSize(television?17:14); star.setOnClickListener(v -> toggleFavorite()); actions.addView(star,new LinearLayout.LayoutParams(dp(television?154:126),dp(52))); copy.addView(actions);
        playHint=text("",television?15:13,Ui.MUTED); copy.addView(playHint);
        TextView synopsis=text(details.overview().isEmpty()?"Opis nie został udostępniony przez dostawcę.":details.overview(),television?17:15,Ui.TEXT); synopsis.setMaxLines(television?2:4); synopsis.setEllipsize(TextUtils.TruncateAt.END); synopsis.setLineSpacing(dp(3),1); synopsis.setPadding(0,dp(14),0,0); copy.addView(synopsis);
        if(!details.overview().isEmpty()) { synopsis.setContentDescription("Opis serialu. Wybierz, aby przeczytać całość."); synopsis.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(details.title()).setMessage(details.overview()).setPositiveButton("Zamknij",null).show()); }
        if(!television && !details.genre().isEmpty()) { TextView genre=text(details.genre(),television?15:13,Ui.MUTED); genre.setPadding(0,dp(12),0,0); copy.addView(genre); }
        if(television) { FrameLayout.LayoutParams infoSize=new FrameLayout.LayoutParams(Math.max(dp(420),getResources().getDisplayMetrics().widthPixels/2),-2,Gravity.LEFT); hero.addView(copy,infoSize); }
        if(television || !details.cover().isEmpty() || !details.backdrop().isEmpty()) header.addView(hero,new LinearLayout.LayoutParams(-1,heroHeight)); if(!television) header.addView(copy);
        Button back=back(); if(!television) back.setVisibility(View.GONE); FrameLayout.LayoutParams backSize=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.LEFT); backSize.leftMargin=dp(television?26:14); backSize.topMargin=dp(12); hero.addView(back,backSize);
        TextView brand=text("TV PLAYER",12,Ui.MUTED); if(!television) brand.setVisibility(View.GONE); brand.setTypeface(null,Typeface.BOLD); FrameLayout.LayoutParams brandSize=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT); brandSize.rightMargin=dp(18); brandSize.topMargin=dp(26); hero.addView(brand,brandSize);
        LinearLayout seasonBar=new LinearLayout(this); seasonBar.setPadding(dp(television?32:18),dp(16),dp(television?32:18),dp(8)); seasonBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView label=text("Odcinki",television?24:20,Ui.TEXT); label.setTypeface(null,Typeface.BOLD); seasonBar.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        seasonButton=null;
        if(!details.seasons().isEmpty()) {
            selectedSeason=Math.max(0,Math.min(selectedSeason,details.seasons().size()-1));
            if(!television) {
                if(details.seasons().size()==1) {
                    TextView only=text(details.seasons().get(0).title(),16,Ui.MUTED); only.setGravity(Gravity.CENTER); only.setPadding(dp(14),0,dp(14),0); only.setBackground(Ui.shape(this,Ui.SURFACE,10,Ui.BORDER)); only.setContentDescription(only.getText()+", jedyny sezon"); seasonBar.addView(only,new LinearLayout.LayoutParams(dp(148),dp(48)));
                } else {
                    seasonButton=button(details.seasons().get(selectedSeason).title()+"  ▾"); seasonButton.setTextColor(Ui.TEXT); seasonButton.setBackground(Ui.touch(this,Ui.SURFACE_HIGH,10,Ui.BORDER)); seasonButton.setContentDescription("Wybierz sezon"); seasonBar.addView(seasonButton,new LinearLayout.LayoutParams(dp(164),dp(48)));
                    seasonButton.setOnClickListener(v -> { String[] names=new String[details.seasons().size()]; for(int i=0;i<names.length;i++) names[i]=details.seasons().get(i).title(); new AlertDialog.Builder(this).setTitle("Wybierz sezon").setSingleChoiceItems(names,selectedSeason,(dialog,index) -> { selectSeason(index); dialog.dismiss(); }).setNegativeButton("Zamknij",null).show(); });
                }
            } else {
            Spinner seasons=new Spinner(this,television?Spinner.MODE_DIALOG:Spinner.MODE_DROPDOWN); seasons.setPrompt("Wybierz sezon"); seasons.setFocusable(true); seasons.setFocusableInTouchMode(television); seasons.setContentDescription("Wybierz sezon"); seasons.setBackground(Ui.touch(this,Ui.SURFACE_HIGH,10,Ui.BORDER));
            List<String> names=new ArrayList<>(); for(SeriesDetails.Season season:details.seasons()) names.add(season.title());
            ArrayAdapter<String> choices=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,names) {
                @Override public View getView(int p,View reused,ViewGroup parent) { TextView view=(TextView)super.getView(p,reused,parent); view.setText(names.get(p)+"  ▾"); view.setTextColor(Ui.TEXT); view.setTextSize(television?18:16); view.setPadding(dp(14),dp(8),dp(14),dp(8)); return view; }
                @Override public View getDropDownView(int p,View reused,ViewGroup parent) { TextView view=(TextView)super.getDropDownView(p,reused,parent); view.setTextColor(Ui.TEXT); view.setTextSize(18); view.setBackgroundColor(Ui.SURFACE_HIGH); view.setPadding(dp(16),dp(12),dp(16),dp(12)); return view; }
            }; choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); seasons.setAdapter(choices); seasonBar.addView(seasons,new LinearLayout.LayoutParams(dp(television?210:164),dp(48))); selectedSeason=Math.min(selectedSeason,details.seasons().size()-1); seasons.setSelection(selectedSeason);
            seasons.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() { public void onNothingSelected(AdapterView<?> parent) {} public void onItemSelected(AdapterView<?> parent,View view,int position,long id) { selectedSeason=position; episodes=details.seasons().get(position).episodes(); adapter.notifyDataSetChanged(); updateProgress(); } });
            }
            episodes=details.seasons().get(selectedSeason).episodes();
        } else { episodes=Collections.emptyList(); }
        header.addView(seasonBar);
        if(episodes.isEmpty()) { TextView empty=text("Dostawca nie udostępnił odcinków tego serialu.",16,Ui.MUTED); empty.setPadding(dp(18),dp(16),dp(18),dp(24)); header.addView(empty); }
        adapter.notifyDataSetChanged(); updateProgress(); if(television && next!=null) play.post(() -> play.requestFocus());
    }
    private void updateProgress() {
        if(details==null || play==null) return; next=WatchProgress.next(allEpisodes,progressEntries);
        WatchProgress.Entry current=next==null?new WatchProgress.Entry(0,0,0,false):entry(next); boolean continuing=next!=null && current.position()>=5000 && !current.completed();
        play.setText(continuing?"▶  Kontynuuj":"▶  Odtwórz"); play.setEnabled(next!=null); playHint.setText(next==null?"":"Sezon "+next.season()+" · Odcinek "+next.number()+(fallbackTitle(next)?"":" · "+next.title())); star.setEnabled(favoritesReady); star.setText(favorites.contains(source.url())?"✓  Moja lista":"+  Moja lista");
    }
    private WatchProgress.Entry entry(SeriesDetails.Episode episode) { return progressEntries.getOrDefault(episode.channel().url(),new WatchProgress.Entry(0,0,0,false)); }
    private boolean fallbackTitle(SeriesDetails.Episode episode) { return episode.title().equalsIgnoreCase("Odcinek "+episode.number()); }
    private static final class EpisodeRow {
        LinearLayout row; ImageView image; ProgressBar progress; TextView name,metadata,plot; SeriesDetails.Episode bound;
    }
    private View episodeRow(SeriesDetails.Episode episode,View reused) {
        EpisodeRow holder;
        if(reused!=null && reused.getTag() instanceof EpisodeRow) holder=(EpisodeRow)reused.getTag();
        else {
            holder=new EpisodeRow(); holder.row=new LinearLayout(this); holder.row.setGravity(Gravity.TOP); holder.row.setPadding(dp(television?32:18),dp(10),dp(television?32:18),dp(10)); holder.row.setMinimumHeight(dp(television?146:96)); holder.row.setBackground(Ui.touch(this,Ui.BG,10,Ui.BORDER)); holder.row.setFocusable(television); holder.row.setFocusableInTouchMode(television); holder.row.setClickable(true); holder.row.setTag(holder);
            FrameLayout thumb=new FrameLayout(this); thumb.setBackground(Ui.shape(this,Ui.SURFACE_HIGH,8,0)); thumb.setClipToOutline(true); int width=dp(television?220:128),height=width*9/16;
            holder.image=new ImageView(this); thumb.addView(holder.image,new FrameLayout.LayoutParams(-1,-1)); TextView icon=text("▶",television?26:20,Ui.TEXT); icon.setGravity(Gravity.CENTER); icon.setShadowLayer(dp(4),0,0,0xFF000000); thumb.addView(icon,new FrameLayout.LayoutParams(-1,-1));
            holder.progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); holder.progress.setMax(1000); holder.progress.setProgressTintList(ColorStateList.valueOf(Ui.TEXT)); thumb.addView(holder.progress,new FrameLayout.LayoutParams(-1,dp(4),Gravity.BOTTOM)); holder.row.addView(thumb,new LinearLayout.LayoutParams(width,height));
            LinearLayout copy=column(); copy.setPadding(dp(14),0,0,0); holder.name=text("",television?21:16,Ui.TEXT); holder.name.setTypeface(null,Typeface.BOLD); holder.name.setMaxLines(2); holder.name.setEllipsize(TextUtils.TruncateAt.END); copy.addView(holder.name);
            holder.metadata=text("",television?16:12,Ui.MUTED); holder.metadata.setPadding(0,dp(5),0,0); copy.addView(holder.metadata);
            holder.plot=text("",television?16:13,Ui.MUTED); holder.plot.setMaxLines(3); holder.plot.setEllipsize(TextUtils.TruncateAt.END); holder.plot.setPadding(0,dp(7),0,0); copy.addView(holder.plot); holder.row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            final EpisodeRow target=holder; holder.row.setOnClickListener(v -> { if(target.bound!=null) play(target.bound); });
        }
        holder.bound=episode; holder.row.setContentDescription(fallbackTitle(episode)?episode.title():"Odcinek "+episode.number()+": "+episode.title()); holder.name.setText(fallbackTitle(episode)?episode.title():episode.number()+". "+episode.title());
        holder.image.setScaleType(episode.image().isEmpty()?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP); int width=dp(television?220:128); ImageLoader.get(this).load(episode.image().isEmpty()?details.cover():episode.image(),holder.image,width,width*9/16);
        WatchProgress.Entry progress=entry(episode); boolean watched=progress.duration()>0 && (progress.position()>0 || progress.completed()); holder.progress.setVisibility(watched?View.VISIBLE:View.GONE); holder.progress.setProgress(watched?(progress.completed()?1000:(int)Math.min(1000,progress.position()*1000/progress.duration())):0);
        String metadata=episode.durationSeconds()>0?Math.max(1,(episode.durationSeconds()+59)/60)+" min":""; if(progress.completed()) metadata+=(metadata.isEmpty()?"":" · ")+"Obejrzano"; else if(progress.position()>5000) metadata+=(metadata.isEmpty()?"":" · ")+"W trakcie";
        holder.metadata.setText(metadata); holder.metadata.setVisibility(metadata.isEmpty()?View.GONE:View.VISIBLE); holder.plot.setText(episode.overview()); holder.plot.setVisibility(episode.overview().isEmpty()?View.GONE:View.VISIBLE); return holder.row;
    }
    private void play(SeriesDetails.Episode episode) { Channel channel=episode.channel(); startActivity(new Intent(this,PlayerActivity.class).putExtra("url",channel.url()).putExtra("name",channel.name()).putExtra("kind","episode").putExtra("television",television)); }
    private void toggleFavorite() { if(!favorites.add(source.url())) favorites.remove(source.url()); updateProgress(); Set<String> saved=new HashSet<>(favorites); worker.execute(() -> { try { new LibraryStore(this).saveFavorites(saved); } catch(Exception e) { runOnUiThread(() -> { if(!isDestroyed()) Toast.makeText(this,"Nie udało się zapisać Mojej listy.",Toast.LENGTH_SHORT).show(); }); } }); }
    @Override protected void onResume() { super.onResume(); if(loaded) { final SeriesDetails current=details; final List<SeriesDetails.Episode> items=allEpisodes; worker.execute(() -> { Map<String,WatchProgress.Entry> latest=WatchProgress.readEntries(this,items); runOnUiThread(() -> { if(!isDestroyed() && details==current) { progressEntries=latest; updateProgress(); adapter.notifyDataSetChanged(); } }); }); } }
    @Override protected void onDestroy() { client.cancelSeriesRequest(); network.shutdownNow(); worker.shutdown(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putInt("season",selectedSeason); super.onSaveInstanceState(state); }
}

