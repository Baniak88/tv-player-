package pl.tvplayer;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Landscape, remote-first shell shared by Fire OS and Android TV. */
public final class TvActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final SourceClient source=new SourceClient();
    private final Set<String> favorites=new HashSet<>();
    private final Map<String,List<Channel>> catalogs=new HashMap<>();
    private List<Channel> items=new ArrayList<>(),shown=new ArrayList<>();
    private List<String> groups=new ArrayList<>();
    private String section="tv",group="",query="",seriesTitle="";
    private boolean home=true,onlyFavorites,busy,loadingAccount;
    private TvAccountStore.Account account;
    private TvPhoneSetup phoneSetup;
    private LinearLayout page,body;
    private TextView status,heading;
    private ListView bouquets,list;
    private GridView posters;
    private BaseAdapter rows,covers;
    private int generation;
    private final android.os.Handler ui=new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean scrolling,foreground;
    private final Runnable guideRows=this::loadVisibleGuides;
    private static final class ChannelRow {
        LinearLayout view; ImageView logo; TextView name,epg,star; Channel channel; boolean requested;
    }
    private static final class Poster {
        LinearLayout view; FrameLayout artwork; ImageView image; TextView name,placeholder,badge;
    }
    private void scheduleGuides() {
        ui.removeCallbacks(guideRows);
        if(foreground && !scrolling && !home) ui.postDelayed(guideRows,200);
    }
    private void loadVisibleGuides() {
        if(home || list==null || !list.isShown() || scrolling) return;
        for(int i=0;i<list.getChildCount();i++) {
            Object tag=list.getChildAt(i).getTag();
            if(!(tag instanceof ChannelRow row) || row.requested || !row.channel.kind().equals("tv")) continue;
            Channel channel=row.channel; row.requested=true;
            GuideRepository.get(this).request(channel,false,data -> {
                if(row.channel==channel && row.view.isShown()) row.epg.setText(GuideRepository.summary(data,System.currentTimeMillis()/1000));
            });
        }
    }
    private int dp(int n) { return Ui.dp(this,n); }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String value,int size,int color) { return Ui.text(this,value,size,color); }
    private StateListDrawable focus(int normal) {
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},Ui.shape(this,0xFF303640,12,Ui.ACCENT));
        states.addState(new int[]{android.R.attr.state_pressed},Ui.shape(this,0xFF303640,12,Ui.ACCENT));
        states.addState(new int[]{},Ui.shape(this,normal,12,Ui.BORDER)); return states;
    }
    private Button button(String value,Runnable action) {
        Button b=Ui.button(this,value); b.setTextSize(17); b.setTextColor(Ui.TEXT); b.setFocusable(true); b.setFocusableInTouchMode(true); b.setBackground(focus(Ui.SURFACE)); b.setOnClickListener(v -> action.run()); return b;
    }
    private String title(String kind) { return kind.equals("tv")?"Telewizja na żywo":kind.equals("movie")?"Filmy":"Seriale"; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_SECURE);
        if(state!=null) { section=state.getString("section","tv"); group=state.getString("group",""); home=state.getBoolean("home",true); }
        build(); loadAccount();
    }
    private void loadAccount() {
        worker.execute(() -> { try { account=new TvAccountStore(this).load(); } catch(Exception ignored) {} runOnUiThread(() -> { if(!isDestroyed()) { if(!home) load(section); else status.setText(account==null?"Dodaj konto Xtream lub adres listy M3U, aby rozpocząć.":"Biblioteka jest gotowa. Wybierz TV, filmy lub seriale."); } }); });
    }
    private void build() {
        page=column(); page.setBackgroundColor(Ui.BG); page.setPadding(dp(32),dp(18),dp(32),dp(16));
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text(Ui.APP_NAME.toUpperCase(Locale.ROOT),18,Ui.ACCENT); brand.setTypeface(null,Typeface.BOLD); brand.setLetterSpacing(.08f); header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextClock clock=new TextClock(this); clock.setTextColor(Ui.MUTED); clock.setTextSize(18); clock.setFormat24Hour("HH:mm"); clock.setFormat12Hour("HH:mm"); header.addView(clock);
        page.addView(header,new LinearLayout.LayoutParams(-1,dp(42)));
        body=column(); page.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        status=text("",15,Ui.MUTED); status.setPadding(0,dp(10),0,0); page.addView(status,new LinearLayout.LayoutParams(-1,dp(38)));
        setContentView(page);
        if(home) showHome(); else showCatalog();
    }
    private void showHome() {
        home=true; ui.removeCallbacks(guideRows); body.removeAllViews();
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        LinearLayout content=column(); scroll.addView(content,new ScrollView.LayoutParams(-1,-2)); body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        heading=text("Co oglądamy?",32,Ui.TEXT); heading.setTypeface(null,Typeface.BOLD); heading.setPadding(0,dp(18),0,dp(26)); content.addView(heading);
        LinearLayout tiles=new LinearLayout(this); int height=dp(300),gap=dp(16);
        View live=HomeStyle.tvTile(this,"Telewizja na żywo","tv",true,() -> open("tv"));
        LinearLayout.LayoutParams liveSize=new LinearLayout.LayoutParams(0,height,1.15f); liveSize.rightMargin=gap; tiles.addView(live,liveSize);
        LinearLayout middle=column(),films=new LinearLayout(this),management=new LinearLayout(this);
        View movies=HomeStyle.tvTile(this,"Filmy","movie",false,() -> open("movie"));
        View series=HomeStyle.tvTile(this,"Seriale","series",false,() -> open("series"));
        LinearLayout.LayoutParams movieSize=new LinearLayout.LayoutParams(0,-1,1); movieSize.rightMargin=gap; films.addView(movies,movieSize); films.addView(series,new LinearLayout.LayoutParams(0,-1,1));
        View accountTile=HomeStyle.tvAction(this,"Konto","account",this::account);
        View sourceTile=HomeStyle.tvAction(this,"Zmień źródło","source",this::sourceDialog);
        LinearLayout.LayoutParams accountSize=new LinearLayout.LayoutParams(0,-1,1); accountSize.rightMargin=gap; management.addView(accountTile,accountSize); management.addView(sourceTile,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout.LayoutParams filmRow=new LinearLayout.LayoutParams(-1,0,1.65f); filmRow.bottomMargin=gap; middle.addView(films,filmRow); middle.addView(management,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout.LayoutParams middleSize=new LinearLayout.LayoutParams(0,height,1.6f); middleSize.rightMargin=gap; tiles.addView(middle,middleSize);
        LinearLayout actions=column();
        View settings=HomeStyle.tvAction(this,"Ustawienia","settings",this::settings);
        View refresh=HomeStyle.tvAction(this,"Odśwież źródło","refresh",this::refresh);
        View exit=HomeStyle.tvAction(this,"Wyjście","exit",this::finish);
        View[] buttons={settings,refresh,exit};
        for(int i=0;i<buttons.length;i++) { LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,0,1); if(i<buttons.length-1) size.bottomMargin=gap; actions.addView(buttons[i],size); }
        tiles.addView(actions,new LinearLayout.LayoutParams(0,height,1.15f)); content.addView(tiles);
        View[] focusable={live,movies,series,accountTile,sourceTile,settings,refresh,exit}; for(View view:focusable) view.setId(View.generateViewId());
        live.setNextFocusRightId(movies.getId()); live.setNextFocusDownId(accountTile.getId());
        movies.setNextFocusLeftId(live.getId()); movies.setNextFocusRightId(series.getId()); movies.setNextFocusDownId(accountTile.getId());
        series.setNextFocusLeftId(movies.getId()); series.setNextFocusRightId(settings.getId()); series.setNextFocusDownId(sourceTile.getId());
        accountTile.setNextFocusUpId(movies.getId()); accountTile.setNextFocusLeftId(live.getId()); accountTile.setNextFocusRightId(sourceTile.getId()); accountTile.setNextFocusDownId(refresh.getId());
        sourceTile.setNextFocusUpId(series.getId()); sourceTile.setNextFocusLeftId(accountTile.getId()); sourceTile.setNextFocusRightId(settings.getId()); sourceTile.setNextFocusDownId(exit.getId());
        settings.setNextFocusLeftId(series.getId()); settings.setNextFocusDownId(refresh.getId());
        refresh.setNextFocusUpId(settings.getId()); refresh.setNextFocusLeftId(sourceTile.getId()); refresh.setNextFocusDownId(exit.getId());
        exit.setNextFocusUpId(refresh.getId()); exit.setNextFocusLeftId(sourceTile.getId());
        status.setText(account==null?"Dodaj konto lub listę M3U · Zmień źródło → Dodaj przez telefon":"OK — wybierz · Strzałki — nawigacja · Wstecz — powrót");
        live.requestFocus(); live.post(() -> { if(home && !isFinishing()) live.requestFocus(); });
    }
    private void open(String kind) { if(busy) return; section=kind; group=""; query=""; seriesTitle=""; onlyFavorites=false; items=new ArrayList<>(); home=false; showCatalog(); load(kind); }
    private void load(String kind) {
        if(busy) return; final int request=++generation; busy=true; status.setText("Wczytywanie biblioteki…");
        worker.execute(() -> {
            try {
                List<Channel> loaded=catalogs.get(kind);
                if(loaded==null) loaded=new LibraryStore(this).loadSection(kind,favorites);
                if(account!=null && account.type().equals("xtream") && !getPreferences(MODE_PRIVATE).getBoolean("loaded-"+kind,false)) {
                    final List<Channel> cached=loaded;
                    if(!cached.isEmpty()) runOnUiThread(() -> {
                        if(isDestroyed() || request!=generation) return;
                        items=cached; rebuildGroups(); status.setText("Zapisana biblioteka · sprawdzanie aktualizacji…");
                    });
                    try {
                        loaded=source.xtreamSection(account.server(),account.user(),account.password(),kind,this::progress);
                        LibraryStore store=new LibraryStore(this); store.updateSection(kind,loaded);
                        getPreferences(MODE_PRIVATE).edit().putBoolean("loaded-"+kind,true).apply();
                    } catch(Exception failure) {
                        if(cached.isEmpty()) throw failure;
                        catalogs.put(kind,cached);
                        runOnUiThread(() -> {
                            if(isDestroyed() || request!=generation) return;
                            busy=false; items=cached; rebuildGroups(); status.setText("Dostawca niedostępny · korzystasz z zapisanej biblioteki.");
                        });
                        return;
                    }
                }
                final List<Channel> result=loaded; catalogs.put(kind,result);
                runOnUiThread(() -> { if(isDestroyed() || request!=generation) return; busy=false; items=result; rebuildGroups(); });
            } catch(Exception e) { runOnUiThread(() -> { if(!isDestroyed()) { busy=false; status.setText("Nie udało się wczytać biblioteki."); error("Nie można pobrać katalogu. Sprawdź połączenie i ważność źródła."); } }); }
        });
    }
    private void progress(String value) { runOnUiThread(() -> { if(!isDestroyed()) status.setText(value); }); }
    private void showCatalog() {
        home=false; scrolling=false; body.removeAllViews();
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        String[] labels={"‹ Główna","TV","Filmy","Seriale","Szukaj","☆ Ulubione","Sortuj","Źródło"};
        for(int i=0;i<labels.length;i++) {
            final int index=i;
            Button b=button(labels[i],() -> { if(index==0) { if(!busy) showHome(); } else if(index>=1 && index<=3) open(new String[]{"tv","movie","series"}[index-1]); else if(index==4) search(); else if(index==5) { onlyFavorites=!onlyFavorites; filter(); } else if(index==6) sort(); else sourceDialog(); });
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(48),i==0?1.25f:1); size.rightMargin=dp(6); if(i>=1 && i<=3 && section.equals(new String[]{"tv","movie","series"}[i-1])) Ui.televisionButton(b,true); bar.addView(b,size);
        }
        body.addView(bar);
        heading=text(title(section),24,Ui.TEXT); heading.setTypeface(null,Typeface.BOLD); heading.setPadding(0,dp(16),0,dp(12)); body.addView(heading);
        LinearLayout split=new LinearLayout(this);
        bouquets=new ListView(this); bouquets.setDivider(null); bouquets.setSelector(Ui.shape(this,0xFF303640,10,Ui.ACCENT)); bouquets.setPadding(dp(2),dp(2),dp(10),dp(2));
        bouquets.setOnItemClickListener((p,v,position,id) -> { group=position==0?"":groups.get(position-1); query=""; filter(); (section.equals("tv")||!seriesTitle.isEmpty()?list:posters).requestFocus(); });
        split.addView(bouquets,new LinearLayout.LayoutParams(dp(220),-1));
        FrameLayout catalog=new FrameLayout(this);
        list=new ListView(this); list.setDividerHeight(dp(8)); list.setSelector(Ui.shape(this,0xFF303640,10,Ui.ACCENT)); list.setItemsCanFocus(false); list.setDrawSelectorOnTop(true); list.setSelector(Ui.shape(this,0x00000000,10,Ui.ACCENT));
        rows=new BaseAdapter() {
            public int getCount() { return shown.size(); } public Object getItem(int p) { return shown.get(p); } public long getItemId(int p) { return p; }
            public View getView(int p,View reused,ViewGroup parent) { return channelRow(shown.get(p),reused); }
        }; list.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView view,int state) { scrolling=state!=SCROLL_STATE_IDLE; scheduleGuides(); }
            public void onScroll(AbsListView view,int first,int count,int total) { scheduleGuides(); }
        }); list.setAdapter(rows); catalog.addView(list,new FrameLayout.LayoutParams(-1,-1));
        posters=new GridView(this); posters.setNumColumns(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density>=1100?5:4); posters.setHorizontalSpacing(dp(14)); posters.setVerticalSpacing(dp(14)); posters.setSelector(Ui.shape(this,0xFF303640,12,Ui.ACCENT));
        covers=new BaseAdapter() {
            public int getCount() { return shown.size(); } public Object getItem(int p) { return shown.get(p); } public long getItemId(int p) { return p; }
            public View getView(int p,View reused,ViewGroup parent) { return poster(shown.get(p),reused); }
        }; posters.setDrawSelectorOnTop(true); posters.setSelector(Ui.shape(this,0x00000000,12,Ui.ACCENT)); posters.setAdapter(covers); catalog.addView(posters,new FrameLayout.LayoutParams(-1,-1));
        AdapterView.OnItemClickListener click=(parent,v,position,id) -> select(shown.get(position)); list.setOnItemClickListener(click); posters.setOnItemClickListener(click);
        AdapterView.OnItemLongClickListener menu=(parent,v,position,id) -> { options(shown.get(position)); return true; }; list.setOnItemLongClickListener(menu); posters.setOnItemLongClickListener(menu);
        split.addView(catalog,new LinearLayout.LayoutParams(0,-1,1)); body.addView(split,new LinearLayout.LayoutParams(-1,0,1));
        filter(); bouquets.requestFocus();
    }
    private View channelRow(Channel channel,View reused) {
        ChannelRow row=reused!=null && reused.getTag() instanceof ChannelRow?(ChannelRow)reused.getTag():null;
        if(row==null) {
            row=new ChannelRow(); row.view=new LinearLayout(this); row.view.setGravity(Gravity.CENTER_VERTICAL); row.view.setPadding(dp(14),dp(10),dp(14),dp(10)); row.view.setMinimumHeight(dp(84)); row.view.setBackground(Ui.shape(this,Ui.SURFACE,10,0));
            row.logo=new ImageView(this); row.logo.setScaleType(ImageView.ScaleType.FIT_CENTER); row.view.addView(row.logo,new LinearLayout.LayoutParams(dp(72),dp(52)));
            LinearLayout copy=column(); copy.setPadding(dp(16),0,dp(10),0);
            row.name=text("",20,Ui.TEXT); row.name.setMaxLines(2); copy.addView(row.name);
            row.epg=text("",15,Ui.MUTED); row.epg.setMaxLines(2); copy.addView(row.epg); row.view.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            row.star=text("",22,Ui.STAR); row.view.addView(row.star); row.view.setTag(row);
        }
        row.channel=channel; row.requested=false; row.name.setText(channel.name()); row.star.setText(favorites.contains(channel.url())?"★":"");
        row.epg.setText(channel.kind().equals("tv")?GuideRepository.summary(GuideRepository.get(this).peek(channel,false),System.currentTimeMillis()/1000):channel.group());
        ImageLoader.get(this).load(channel.logo(),row.logo,dp(72),dp(52)); scheduleGuides(); return row.view;
    }
    private View poster(Channel channel,View reused) {
        Poster card=reused!=null && reused.getTag() instanceof Poster?(Poster)reused.getTag():null;
        int columns=Math.max(1,posters.getNumColumns());
        int available=posters.getWidth()>0?posters.getWidth():getResources().getDisplayMetrics().widthPixels-dp(64+220);
        int width=Math.max(dp(60),(available-dp(14)*(columns-1))/columns),height=width*3/2;
        if(card==null) {
            card=new Poster(); card.view=column(); card.view.setPadding(dp(5),dp(5),dp(5),dp(10)); card.view.setBackground(Ui.shape(this,Ui.SURFACE,12,0));
            card.artwork=new FrameLayout(this); card.artwork.setBackground(Ui.shape(this,Ui.SURFACE_HIGH,10,0)); card.artwork.setClipToOutline(true); card.view.addView(card.artwork,new LinearLayout.LayoutParams(-1,height));
            card.placeholder=text("",15,Ui.MUTED); card.placeholder.setGravity(Gravity.CENTER); card.placeholder.setPadding(dp(8),dp(8),dp(8),dp(8)); card.placeholder.setMaxLines(4); card.artwork.addView(card.placeholder,new FrameLayout.LayoutParams(-1,-1));
            card.image=new ImageView(this); card.image.setScaleType(ImageView.ScaleType.CENTER_CROP); card.artwork.addView(card.image,new FrameLayout.LayoutParams(-1,-1));
            card.badge=text("★",16,Ui.STAR); card.badge.setGravity(Gravity.CENTER); card.badge.setBackground(Ui.shape(this,0xCC0A0D14,14,0));
            FrameLayout.LayoutParams badgeSize=new FrameLayout.LayoutParams(dp(30),dp(30),Gravity.TOP|Gravity.END); badgeSize.topMargin=dp(6); badgeSize.rightMargin=dp(6); card.artwork.addView(card.badge,badgeSize);
            card.name=text("",16,Ui.TEXT); card.name.setMaxLines(2); card.name.setPadding(dp(4),dp(7),dp(4),0); card.view.addView(card.name,new LinearLayout.LayoutParams(-1,dp(52))); card.view.setTag(card);
        }
        if(card.artwork.getLayoutParams().height!=height) card.artwork.setLayoutParams(new LinearLayout.LayoutParams(-1,height));
        ImageLoader.get(this).load(channel.logo(),card.image,width,height); card.placeholder.setText(channel.name()); card.badge.setVisibility(favorites.contains(channel.url())?View.VISIBLE:View.GONE); card.name.setText(channel.name()); card.view.setContentDescription(channel.name()); return card.view;
    }
    private void rebuildGroups() {
        groups=new ArrayList<>(BouquetOrder.counts(items,getPreferences(MODE_PRIVATE).getString("sort-"+section,"provider")).keySet());
        List<String> labels=new ArrayList<>(); labels.add("Cała oferta"); labels.addAll(groups);
        bouquets.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels) {
            @Override public View getView(int p,View reused,ViewGroup parent) { TextView row=(TextView)super.getView(p,reused,parent); row.setTextSize(18); row.setTextColor(Ui.TEXT); row.setMinHeight(dp(56)); row.setPadding(dp(14),dp(10),dp(10),dp(10)); return row; }
        }); filter();
    }
    private void filter() {
        if(list==null) return;
        shown=new ArrayList<>(); String needle=query.toLowerCase(Locale.ROOT);
        for(Channel channel:items) if((group.isEmpty() || group.equals(channel.group())) && (!onlyFavorites || favorites.contains(channel.url())) && channel.name().toLowerCase(Locale.ROOT).contains(needle)) shown.add(channel);
        if(rows!=null) rows.notifyDataSetChanged(); if(covers!=null) covers.notifyDataSetChanged();
        boolean television=section.equals("tv") || !seriesTitle.isEmpty(); list.setVisibility(television?View.VISIBLE:View.GONE); posters.setVisibility(television?View.GONE:View.VISIBLE);
        heading.setText(!seriesTitle.isEmpty()?seriesTitle:title(section)+(onlyFavorites?" · Ulubione":"")+(group.isEmpty()?"":" · "+group));
        status.setText(busy?"Wczytywanie biblioteki…":shown.isEmpty()?"Brak pozycji. Dodaj źródło lub zmień filtr.":Ui.count(shown.size())+" pozycji · OK — "+(section.equals("tv")?"odtwórz":"szczegóły")+" · Menu — "+(section.equals("tv")?"EPG i ulubione":"opcje i ulubione")+" · Wstecz — powrót");
    }
    private Channel selected() { if(home || shown.isEmpty()) return null; int index=(list.getVisibility()==View.VISIBLE?list:posters).getSelectedItemPosition(); return index>=0 && index<shown.size()?shown.get(index):null; }
    private void options(Channel channel) {
        List<String> actions=new ArrayList<>(); if(channel.kind().equals("movie")) actions.add("Szczegóły filmu"); actions.add(channel.kind().equals("series")?"Odcinki":"Odtwórz"); if(channel.kind().equals("tv")) actions.add("Program TV i archiwum"); actions.add(favorites.contains(channel.url())?"Usuń z ulubionych":"Dodaj do ulubionych");
        new AlertDialog.Builder(this).setTitle(channel.name()).setItems(actions.toArray(new String[0]),(d,index) -> { String action=actions.get(index); if(action.contains("ulubionych")) favorite(channel); else if(action.startsWith("Program")) GuideDialog.open(this,channel,worker,this::play); else if(action.equals("Odtwórz")) play(channel); else select(channel); }).setNegativeButton("Zamknij",null).show();
    }
    private void favorite(Channel channel) {
        if(!favorites.add(channel.url())) favorites.remove(channel.url()); final Set<String> saved=new HashSet<>(favorites);
        worker.execute(() -> { try { new LibraryStore(this).saveFavorites(saved); } catch(Exception ignored) {} }); filter();
    }
    private void select(Channel channel) {
        if(channel.kind().equals("series")) startActivity(SeriesActivity.intent(this,channel,true)); else if(channel.kind().equals("movie")) startActivity(new Intent(this,MovieActivity.class).putExtra("television",true).putExtra("url",channel.url()).putExtra("name",channel.name()).putExtra("group",channel.group()).putExtra("logo",channel.logo())); else play(channel);
    }
    private void play(Channel channel) { startActivity(new Intent(this,PlayerActivity.class).putExtra("television",true).putExtra("url",channel.url()).putExtra("name",channel.name()).putExtra("kind",channel.kind()).putExtra("guide",channel.guideUrl()).putExtra("epgId",channel.epgId()).putExtra("days",channel.archiveDays()).putExtra("timezone",channel.timezone())); }
    private void search() { EditText field=new EditText(this); field.setSingleLine(true); field.setText(query); new AlertDialog.Builder(this).setTitle("Szukaj w sekcji").setView(field).setPositiveButton("Szukaj",(d,w) -> { query=field.getText().toString().trim(); group=""; filter(); }).setNegativeButton("Anuluj",null).show(); }
    private void sort() { new AlertDialog.Builder(this).setTitle("Kolejność bukietów").setItems(BouquetOrder.LABELS,(d,index) -> { getPreferences(MODE_PRIVATE).edit().putString("sort-"+section,BouquetOrder.MODES[index]).apply(); rebuildGroups(); }).show(); }
    private void account() {
        if(loadingAccount) return; loadingAccount=true;
        worker.execute(() -> {
            try {
                List<Channel> channels=new LibraryStore(this).loadSection("tv",new HashSet<>()); Set<String> bouquets=new HashSet<>(); for(Channel channel:channels) bouquets.add(channel.group());
                runOnUiThread(() -> {
                    loadingAccount=false; if(isFinishing() || isDestroyed()) return;
                    okhttp3.HttpUrl address=account==null?null:okhttp3.HttpUrl.parse(account.server()==null?"":account.server()); String host=account==null?"Nie dodano źródła":address==null?"Nieprawidłowy zapis źródła":address.host();
                    new AlertDialog.Builder(this).setTitle("Konto i biblioteka").setMessage("Źródło: "+host+"\nKanały TV: "+Ui.count(channels.size())+"\nBukiety TV: "+Ui.count(bouquets.size())+"\n\nDane dostępu są przechowywane w zaszyfrowanej bibliotece.").setPositiveButton("Zmień źródło",(d,w) -> sourceDialog()).setNegativeButton("Zamknij",null).show();
                });
            } catch(Exception failure) { runOnUiThread(() -> { loadingAccount=false; error("Nie można odczytać konta i biblioteki."); }); }
        });
    }
    private void settings() {
        new AlertDialog.Builder(this).setTitle("Ustawienia").setItems(new String[]{"Źródło: dodaj lub zmień","Źródło: odśwież listę","Połączenie internetowe (bufor)","Program TV (EPG): własny link XMLTV","O aplikacji"},(d,index) -> {
            if(index==0) sourceDialog(); else if(index==1) refresh(); else if(index==2) PlaybackSettings.choose(this,() -> {}); else if(index==3) EpgSettings.choose(this,() -> GuideRepository.get(this).invalidateFailures()); else info();
        }).setNegativeButton("Zamknij",null).show();
    }
    private void info() { new AlertDialog.Builder(this).setTitle(Ui.APP_NAME+" "+BuildConfig.VERSION_NAME).setMessage("Telewizja na żywo, filmy i seriale z Twojego źródła IPTV.\nProgram TV (EPG) i archiwum zależą od dostawcy.\n\nOK: wybierz lub odtwórz\nStrzałki: nawigacja\nMenu / przytrzymanie OK: opcje pozycji\nPlay/Pause: sterowanie odtwarzaniem").setPositiveButton("OK",null).show(); }
    private void error(String message) { if(!isFinishing() && !isDestroyed()) new AlertDialog.Builder(this).setTitle(Ui.APP_NAME).setMessage(message).setPositiveButton("OK",null).show(); }
    private void sourceDialog() {
        if(busy) return;
        new AlertDialog.Builder(this).setTitle("Dodaj źródło").setItems(new String[]{"Dodaj przez telefon · ta sama sieć Wi-Fi","Konto Xtream Codes · wpisz pilotem","Adres listy M3U · wpisz pilotem"},(d,index) -> {
            if(index==0) { if(phoneSetup==null) phoneSetup=new TvPhoneSetup(this); phoneSetup.show(this::importSource); }
            else sourceForm(index==1);
        }).setNegativeButton("Anuluj",null).show();
    }
    private void sourceForm(boolean xtream) {
        LinearLayout fields=column(); fields.setPadding(dp(24),dp(12),dp(24),0);
        EditText address=new EditText(this); address.setHint(xtream?"Serwer http:// lub https://":"Adres listy M3U"); address.setSingleLine(true); address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI); fields.addView(address);
        EditText user=new EditText(this),password=new EditText(this); user.setHint("Login"); user.setSingleLine(true); password.setHint("Hasło"); password.setSingleLine(true); password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if(xtream) { fields.addView(user); fields.addView(password); }
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(xtream?"Konto Xtream Codes":"Lista M3U").setView(fields).setPositiveButton("Połącz",null).setNegativeButton("Anuluj",null).create(); dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try { SourceClient.validate(address.getText().toString()); } catch(IllegalArgumentException e) { address.setError("Podaj poprawny adres http:// lub https://"); return; }
            if(xtream && (user.getText().toString().trim().isEmpty() || password.getText().toString().isEmpty())) { user.setError("Wpisz login i hasło"); return; }
            TvAccountStore.Account next=new TvAccountStore.Account(xtream?"xtream":"m3u",address.getText().toString().trim(),user.getText().toString().trim(),password.getText().toString()); dialog.dismiss(); importSource(next);
        });
    }
    private void refresh() { if(busy) return; if(account==null) sourceDialog(); else importSource(account); }
    private void importSource(TvAccountStore.Account next) {
        if(busy) return; busy=true; status.setText("Łączenie ze źródłem…");
        final Set<String> savedFavorites=new HashSet<>(favorites);
        worker.execute(() -> {
            try {
                List<Channel> imported=next.type().equals("xtream")?source.xtreamSection(next.server(),next.user(),next.password(),"tv",this::progress):source.m3u(next.server());
                LibraryStore store=new LibraryStore(this);
                if(next.equals(account) && next.type().equals("xtream") && new java.io.File(getFilesDir(),"catalog.current").isFile()) store.updateSection("tv",imported);
                else store.save(imported,savedFavorites);
                new TvAccountStore(this).save(next);
                getPreferences(MODE_PRIVATE).edit().putBoolean("loaded-tv",true).putBoolean("loaded-movie",!next.type().equals("xtream")).putBoolean("loaded-series",!next.type().equals("xtream")).apply();
                runOnUiThread(() -> { if(isDestroyed()) return; account=next; catalogs.clear(); busy=false; section="tv"; group=""; query=""; seriesTitle=""; items=new ArrayList<>(); for(Channel channel:imported) if(channel.section().equals("tv")) items.add(channel); home=false; showCatalog(); rebuildGroups(); bouquets.requestFocus(); });
            } catch(Exception e) { runOnUiThread(() -> { if(!isDestroyed()) { busy=false; status.setText("Nie udało się dodać źródła."); error("Sprawdź adres, login, hasło i połączenie z internetem. Dotychczasowa biblioteka pozostaje dostępna."); } }); }
        });
    }
    @Override public boolean onKeyUp(int code,KeyEvent event) {
        if(!home && (code==KeyEvent.KEYCODE_MENU || code==KeyEvent.KEYCODE_INFO)) { Channel selected=selected(); if(selected!=null) options(selected); return true; }
        if(!home && code==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { Channel selected=selected(); if(selected!=null) select(selected); return true; }
        return super.onKeyUp(code,event);
    }
    @Override public void onBackPressed() { if(busy) { status.setText("Trwa pobieranie biblioteki…"); return; } if(!home && !seriesTitle.isEmpty()) { seriesTitle=""; open("series"); } else if(!home && (!query.isEmpty() || onlyFavorites)) { query=""; onlyFavorites=false; filter(); } else if(!home) showHome(); else new AlertDialog.Builder(this).setTitle("Zamknąć TV Player?").setPositiveButton("Wyjście",(d,w) -> finish()).setNegativeButton("Zostań",null).show(); }
    @Override protected void onResume() { super.onResume(); foreground=true; scheduleGuides(); worker.execute(() -> { try { Set<String> saved=new LibraryStore(this).loadFavorites(); runOnUiThread(() -> { if(!isDestroyed() && !favorites.equals(saved)) { favorites.clear(); favorites.addAll(saved); if(!home) filter(); } }); } catch(Exception ignored) {} }); if(!home && rows!=null) rows.notifyDataSetChanged(); }
    @Override protected void onPause() { foreground=false; ui.removeCallbacks(guideRows); if(phoneSetup!=null) phoneSetup.close(); super.onPause(); }
    @Override protected void onDestroy() { generation++; ui.removeCallbacksAndMessages(null); worker.shutdown(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putString("section",section); state.putString("group",group); state.putBoolean("home",home); super.onSaveInstanceState(state); }
}
