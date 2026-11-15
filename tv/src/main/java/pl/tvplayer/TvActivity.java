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
    private Map<String,List<Channel>> seriesFolders=Collections.emptyMap();
    private List<Channel> seriesRootItems;
    private String seriesParentGroup="",seriesParentQuery="",seriesFolderUrl="";
    private boolean seriesParentFavorites;
    private String section="tv",group="",query="",seriesTitle="";
    private boolean home=true,onlyFavorites,busy,loadingAccount;
    private TvAccountStore.Account account;
    private TvPhoneSetup phoneSetup;
    private LinearLayout page,body;
    private TextView status,heading,selectionInfo;
    private LinearLayout navigation;
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
                if(row.channel==channel && row.view.isShown()) showGuide(row,data);
            });
        }
    }
    private int dp(int n) { return Ui.dp(this,n); }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String value,int size,int color) { return Ui.text(this,value,size,color); }
    private StateListDrawable focus(int normal) {
        StateListDrawable states=new StateListDrawable();
        GradientDrawable cursor=Ui.shape(this,Ui.SURFACE_HIGH,10,0); cursor.setStroke(dp(3),Ui.TEXT);
        states.addState(new int[]{android.R.attr.state_focused},cursor);
        states.addState(new int[]{android.R.attr.state_pressed},Ui.shape(this,Ui.SURFACE_HIGH,10,Ui.TEXT));
        states.addState(new int[]{},Ui.shape(this,normal,12,Ui.BORDER)); return states;
    }
    private StateListDrawable catalogCursor() {
        StateListDrawable states=new StateListDrawable(); GradientDrawable cursor=Ui.shape(this,0,8,0); cursor.setStroke(dp(3),Ui.TEXT);
        states.addState(new int[]{android.R.attr.state_focused},cursor);
        states.addState(new int[]{android.R.attr.state_pressed},cursor);
        states.addState(new int[]{},Ui.shape(this,0,8,0)); return states;
    }
    private Button button(String value,Runnable action) {
        Button b=Ui.button(this,value); b.setTextSize(17); b.setTextColor(Ui.TEXT); b.setFocusable(true); b.setFocusableInTouchMode(true); b.setBackground(focus(Ui.SURFACE)); b.setOnClickListener(v -> action.run()); return b;
    }
    private String title(String kind) { return kind.equals("tv")?"Telewizja na żywo":kind.equals("movie")?"Filmy":"Seriale"; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if(state!=null) { section=state.getString("section","tv"); group=state.getString("group",""); home=state.getBoolean("home",true); }
        build(); loadAccount();
    }
    private void loadAccount() {
        worker.execute(() -> { try { account=new TvAccountStore(this).load(); } catch(Exception ignored) {} runOnUiThread(() -> { if(!isDestroyed()) { if(!home) load(section); else status.setText(account==null?"Dodaj konto Xtream lub adres listy M3U, aby rozpocząć.":"Biblioteka jest gotowa. Wybierz TV, filmy lub seriale."); } }); });
    }
    private void build() {
        page=column(); page.setBackgroundColor(Ui.BG); page.setPadding(dp(24),dp(12),dp(24),dp(10));
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text(Ui.APP_NAME.toUpperCase(Locale.ROOT),18,Ui.ACCENT); brand.setTypeface(null,Typeface.BOLD); brand.setLetterSpacing(.08f); header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextClock clock=new TextClock(this); clock.setTextColor(Ui.MUTED); clock.setTextSize(18); clock.setFormat24Hour("HH:mm"); clock.setFormat12Hour("HH:mm"); header.addView(clock);
        page.addView(header,new LinearLayout.LayoutParams(-1,dp(30)));
        body=column(); page.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        status=text("",13,Ui.MUTED); status.setSingleLine(true); status.setEllipsize(android.text.TextUtils.TruncateAt.END); status.setPadding(0,dp(6),0,0); page.addView(status,new LinearLayout.LayoutParams(-1,dp(26)));
        setContentView(page);
        if(home) showHome(); else showCatalog();
    }
    private void showHome() {
        home=true; selectionInfo=null; ui.removeCallbacks(guideRows); body.removeAllViews();
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
    private void open(String kind) { if(busy) return; section=kind; group=""; query=""; seriesTitle=""; onlyFavorites=false; seriesFolders=Collections.emptyMap(); items=new ArrayList<>(); home=false; showCatalog(); load(kind); }
    private TvCatalog.Catalog presentation(String kind,List<Channel> channels) { return kind.equals("series")?TvCatalog.series(channels):new TvCatalog.Catalog(channels,Collections.emptyMap()); }
    private void applyCatalog(TvCatalog.Catalog catalog) { items=catalog.items(); seriesFolders=catalog.folders(); rebuildGroups(); }
    private void load(String kind) {
        if(busy) return; final int request=++generation; busy=true; status.setText("Wczytywanie biblioteki…");
        worker.execute(() -> {
            try {
                List<Channel> loaded=catalogs.get(kind);
                if(loaded==null) loaded=new LibraryStore(this).loadSection(kind,favorites);
                if(account!=null && account.type().equals("xtream") && !getPreferences(MODE_PRIVATE).getBoolean("loaded-"+kind,false)) {
                    final List<Channel> cached=loaded; final TvCatalog.Catalog cachedView=presentation(kind,cached);
                    if(!cached.isEmpty()) runOnUiThread(() -> {
                        if(isDestroyed() || request!=generation) return;
                        applyCatalog(cachedView); status.setText("Zapisana biblioteka · sprawdzanie aktualizacji…");
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
                            busy=false; applyCatalog(cachedView); status.setText("Dostawca niedostępny · korzystasz z zapisanej biblioteki.");
                        });
                        return;
                    }
                }
                catalogs.put(kind,loaded); final TvCatalog.Catalog result=presentation(kind,loaded);
                runOnUiThread(() -> { if(isDestroyed() || request!=generation) return; busy=false; applyCatalog(result); });
            } catch(Exception e) { runOnUiThread(() -> { if(!isDestroyed() && request==generation) { busy=false; status.setText("Nie udało się wczytać biblioteki."); error("Nie można pobrać katalogu. Sprawdź połączenie i ważność źródła."); } }); }
        });
    }
    private void progress(String value) { runOnUiThread(() -> { if(!isDestroyed()) status.setText(value); }); }
    private void showCatalog() {
        home=false; scrolling=false; body.removeAllViews();
        navigation=new LinearLayout(this); navigation.setGravity(Gravity.CENTER_VERTICAL);
        String[] labels={"‹ Główna","TV","Filmy","Seriale","Szukaj","☆ Ulubione","Opcje"};
        for(int i=0;i<labels.length;i++) {
            final int index=i;
            Button b=button(labels[i],() -> { if(index==0) { if(!busy) showHome(); } else if(index>=1 && index<=3) open(new String[]{"tv","movie","series"}[index-1]); else if(index==4) search(); else if(index==5) { onlyFavorites=!onlyFavorites; filter(); } else catalogOptions(); });
            b.setId(View.generateViewId()); b.setTextSize(16); b.setSingleLine(true); b.setEllipsize(android.text.TextUtils.TruncateAt.END); b.setPadding(dp(8),0,dp(8),0);
            if(index>=1 && index<=3 && section.equals(new String[]{"tv","movie","series"}[index-1])) { b.setTextColor(Ui.ACCENT); b.setTypeface(null,Typeface.BOLD); }
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(42),i==0?1.1f:1); size.rightMargin=dp(6); navigation.addView(b,size);
        }
        body.addView(navigation);
        heading=text(title(section),22,Ui.TEXT); heading.setSingleLine(true); heading.setEllipsize(android.text.TextUtils.TruncateAt.END); heading.setTypeface(null,Typeface.BOLD); heading.setPadding(0,dp(10),0,dp(8)); body.addView(heading);
        LinearLayout split=new LinearLayout(this);
        bouquets=new ListView(this); bouquets.setId(View.generateViewId()); bouquets.setDivider(null); bouquets.setSelector(catalogCursor()); bouquets.setDrawSelectorOnTop(true); bouquets.setItemsCanFocus(false); bouquets.setPadding(0,0,dp(10),0);
        bouquets.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id) { if(parent==bouquets && parent.hasFocus() && !busy) chooseGroup(position,false); }
        });
        bouquets.setOnItemClickListener((parent,view,position,id) -> chooseGroup(position,true));
        int sidebar=getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density>=1100?220:200;
        split.addView(bouquets,new LinearLayout.LayoutParams(dp(sidebar),-1));
        FrameLayout catalog=new FrameLayout(this);
        list=new ListView(this); list.setId(View.generateViewId()); list.setDivider(new ColorDrawable(Ui.BG)); list.setDividerHeight(dp(4)); list.setItemsCanFocus(false); list.setDrawSelectorOnTop(true); list.setSelector(catalogCursor());
        rows=new BaseAdapter() {
            public int getCount() { return shown.size(); } public Object getItem(int p) { return shown.get(p); } public long getItemId(int p) { return p; }
            public View getView(int p,View reused,ViewGroup parent) { return channelRow(shown.get(p),reused); }
        };
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView view,int state) { scrolling=state!=SCROLL_STATE_IDLE; scheduleGuides(); }
            public void onScroll(AbsListView view,int first,int count,int total) { scheduleGuides(); }
        }); list.setAdapter(rows); catalog.addView(list,new FrameLayout.LayoutParams(-1,-1));
        posters=new GridView(this); posters.setId(View.generateViewId()); posters.setNumColumns(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density>=1100?6:5); posters.setHorizontalSpacing(dp(12)); posters.setVerticalSpacing(dp(12)); posters.setClipToPadding(false); posters.setPadding(dp(3),dp(3),dp(3),dp(3));
        covers=new BaseAdapter() {
            public int getCount() { return shown.size(); } public Object getItem(int p) { return shown.get(p); } public long getItemId(int p) { return p; }
            public View getView(int p,View reused,ViewGroup parent) { return poster(shown.get(p),reused); }
        }; posters.setDrawSelectorOnTop(true); posters.setSelector(catalogCursor()); posters.setAdapter(covers); catalog.addView(posters,new FrameLayout.LayoutParams(-1,-1));
        AdapterView.OnItemSelectedListener selected=new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id) { if(parent==contentView() && position>=0 && position<shown.size()) describe(shown.get(position)); }
        }; list.setOnItemSelectedListener(selected); posters.setOnItemSelectedListener(selected);
        AdapterView.OnItemClickListener click=(parent,view,position,id) -> select(shown.get(position)); list.setOnItemClickListener(click); posters.setOnItemClickListener(click);
        AdapterView.OnItemLongClickListener menu=(parent,view,position,id) -> { options(shown.get(position)); return true; }; list.setOnItemLongClickListener(menu); posters.setOnItemLongClickListener(menu);
        bouquets.setNextFocusRightId(contentView().getId()); list.setNextFocusLeftId(bouquets.getId()); posters.setNextFocusLeftId(bouquets.getId());
        int tab=section.equals("tv")?1:section.equals("movie")?2:3;
        bouquets.setNextFocusUpId(navigation.getChildAt(tab).getId()); list.setNextFocusUpId(navigation.getChildAt(tab).getId()); posters.setNextFocusUpId(navigation.getChildAt(tab).getId());
        for(int i=0;i<navigation.getChildCount();i++) navigation.getChildAt(i).setNextFocusDownId(bouquets.getId());
        split.addView(catalog,new LinearLayout.LayoutParams(0,-1,1)); body.addView(split,new LinearLayout.LayoutParams(-1,0,1));
        selectionInfo=text("",17,Ui.TEXT); selectionInfo.setMaxLines(2); selectionInfo.setEllipsize(android.text.TextUtils.TruncateAt.END); selectionInfo.setPadding(dp(sidebar+4),dp(6),0,0); selectionInfo.setContentDescription("Wybrana pozycja"); body.addView(selectionInfo,new LinearLayout.LayoutParams(-1,dp(48)));
        filter(); bouquets.requestFocus();
    }
    private boolean listMode() { return section.equals("tv") || !seriesTitle.isEmpty() || getPreferences(MODE_PRIVATE).getBoolean("list-"+section,false); }
    private AbsListView contentView() { return listMode()?list:posters; }
    private void chooseGroup(int position,boolean enter) {
        if(position<0 || position>groups.size()) return;
        String next=position==0?"":groups.get(position-1);
        if(!next.equals(group)) { group=next; query=""; filter(); contentView().setSelection(0); bouquets.invalidateViews(); }
        if(enter) { contentView().requestFocus(); if(!shown.isEmpty()) contentView().setSelection(0); }
    }
    private void catalogOptions() {
        List<String> actions=new ArrayList<>(); actions.add("Sortuj"); if(!section.equals("tv") && seriesTitle.isEmpty()) actions.add(listMode()?"Widok: plakaty":"Widok: lista"); actions.add("Źródło");
        new AlertDialog.Builder(this).setTitle("Opcje katalogu").setItems(actions.toArray(new String[0]),(d,index) -> {
            String action=actions.get(index); if(action.equals("Sortuj")) sort(); else if(action.equals("Źródło")) sourceDialog();
            else {
                Channel previous=selected(); getPreferences(MODE_PRIVATE).edit().putBoolean("list-"+section,!listMode()).apply(); showCatalog(); rebuildGroups();
                if(previous!=null) for(int i=0;i<shown.size();i++) if(shown.get(i).url().equals(previous.url())) { contentView().setSelection(i); break; }
                contentView().requestFocus();
            }
        }).setNegativeButton("Zamknij",null).show();
    }
    private void describe(Channel channel) {
        if(selectionInfo==null) return;
        List<Channel> folder=seriesFolders.get(channel.url());
        selectionInfo.setText(channel.name()+(folder==null?"":" · "+Ui.count(folder.size())+" odcinków"));
    }
    private View channelRow(Channel channel,View reused) {
        ChannelRow row=reused!=null && reused.getTag() instanceof ChannelRow?(ChannelRow)reused.getTag():null;
        if(row==null) {
            row=new ChannelRow(); row.view=new LinearLayout(this); row.view.setGravity(Gravity.CENTER_VERTICAL); row.view.setPadding(dp(12),dp(8),dp(12),dp(8)); row.view.setMinimumHeight(dp(60)); row.view.setBackground(Ui.shape(this,Ui.SURFACE,8,0));
            row.logo=new ImageView(this); row.logo.setScaleType(ImageView.ScaleType.FIT_CENTER); row.view.addView(row.logo,new LinearLayout.LayoutParams(dp(52),dp(38)));
            LinearLayout copy=column(); copy.setPadding(dp(12),0,dp(10),0);
            row.name=text("",18,Ui.TEXT); row.name.setSingleLine(true); row.name.setEllipsize(android.text.TextUtils.TruncateAt.END); copy.addView(row.name);
            row.epg=text("",14,Ui.MUTED); row.epg.setSingleLine(true); row.epg.setEllipsize(android.text.TextUtils.TruncateAt.END); copy.addView(row.epg); row.view.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            row.star=text("",22,Ui.STAR); row.view.addView(row.star); row.view.setTag(row);
        }
        row.channel=channel; row.requested=false; row.name.setText(channel.name()); row.star.setText(isFavorite(channel)?"★":"");
        if(channel.kind().equals("tv")) showGuide(row,GuideRepository.get(this).peek(channel,false)); else { row.epg.setText(channel.group()); row.epg.setVisibility(View.VISIBLE); }
        ImageLoader.get(this).load(channel.logo(),row.logo,dp(52),dp(38)); scheduleGuides(); return row.view;
    }
    private void showGuide(ChannelRow row,GuideRepository.Data data) {
        String current=data==null || data.programs().isEmpty()?"":GuideRepository.summary(data,System.currentTimeMillis()/1000).split("\\n",2)[0];
        if(current.startsWith("Brak aktualnego")) current="";
        row.epg.setText(current); row.epg.setVisibility(current.isEmpty()?View.GONE:View.VISIBLE);
    }
    private View poster(Channel channel,View reused) {
        Poster card=reused!=null && reused.getTag() instanceof Poster?(Poster)reused.getTag():null;
        int columns=Math.max(1,posters.getNumColumns());
        int available=posters.getWidth()>0?posters.getWidth():getResources().getDisplayMetrics().widthPixels-dp(48+220);
        int maxArtwork=getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density<650?160:190;
        int width=Math.max(dp(60),(available-dp(12)*(columns-1)-dp(6))/columns),height=Math.min(width*3/2,dp(maxArtwork));
        if(card==null) {
            card=new Poster(); card.view=column(); card.view.setPadding(dp(5),dp(5),dp(5),dp(6)); card.view.setBackground(Ui.shape(this,Ui.SURFACE,8,0));
            card.artwork=new FrameLayout(this); card.artwork.setBackground(Ui.shape(this,Ui.SURFACE_HIGH,10,0)); card.artwork.setClipToOutline(true); card.view.addView(card.artwork,new LinearLayout.LayoutParams(-1,height));
            card.placeholder=text("",15,Ui.MUTED); card.placeholder.setGravity(Gravity.CENTER); card.placeholder.setPadding(dp(8),dp(8),dp(8),dp(8)); card.placeholder.setMaxLines(4); card.artwork.addView(card.placeholder,new FrameLayout.LayoutParams(-1,-1));
            card.image=new ImageView(this); card.image.setScaleType(ImageView.ScaleType.FIT_CENTER); card.artwork.addView(card.image,new FrameLayout.LayoutParams(-1,-1));
            card.badge=text("★",16,Ui.STAR); card.badge.setGravity(Gravity.CENTER); card.badge.setBackground(Ui.shape(this,0xCC0A0D14,14,0));
            FrameLayout.LayoutParams badgeSize=new FrameLayout.LayoutParams(dp(30),dp(30),Gravity.TOP|Gravity.END); badgeSize.topMargin=dp(6); badgeSize.rightMargin=dp(6); card.artwork.addView(card.badge,badgeSize);
            card.name=text("",16,Ui.TEXT); card.name.setMaxLines(2); card.name.setEllipsize(android.text.TextUtils.TruncateAt.END); card.name.setPadding(dp(4),dp(6),dp(4),0); card.view.addView(card.name,new LinearLayout.LayoutParams(-1,dp(52))); card.view.setTag(card);
        }
        if(card.artwork.getLayoutParams().height!=height) card.artwork.setLayoutParams(new LinearLayout.LayoutParams(-1,height));
        ImageLoader.get(this).load(channel.logo(),card.image,width,height); card.placeholder.setText(channel.name()); card.badge.setVisibility(isFavorite(channel)?View.VISIBLE:View.GONE); card.name.setText(channel.name()); card.view.setContentDescription(channel.name()); return card.view;
    }
    private void rebuildGroups() {
        Map<String,Integer> counts=BouquetOrder.counts(items,getPreferences(MODE_PRIVATE).getString("sort-"+section,"provider")); groups=new ArrayList<>(counts.keySet());
        List<String> labels=new ArrayList<>(); labels.add(seriesTitle.isEmpty()?"Cała oferta":"Wszystkie odcinki"); labels.addAll(groups);
        bouquets.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels) {
            @Override public View getView(int p,View reused,ViewGroup parent) {
                TextView row=(TextView)super.getView(p,reused,parent); boolean active=p==0?group.isEmpty():group.equals(groups.get(p-1));
                row.setLayoutParams(new AbsListView.LayoutParams(-1,-2)); row.setTextSize(16); row.setTextColor(active?Ui.TEXT:Ui.MUTED); row.setTypeface(null,active?Typeface.BOLD:Typeface.NORMAL); row.setMinHeight(dp(52)); row.setMaxLines(2); row.setEllipsize(android.text.TextUtils.TruncateAt.END); row.setPadding(dp(12),dp(8),dp(10),dp(8)); row.setBackground(Ui.shape(TvActivity.this,active?Ui.SURFACE_HIGH:0,8,0)); return row;
            }
        }); bouquets.setSelection(group.isEmpty()?0:Math.max(0,groups.indexOf(group)+1)); filter();
    }
    private void filter() {
        if(list==null) return;
        shown=new ArrayList<>(); String needle=query.toLowerCase(Locale.ROOT);
        for(Channel channel:items) if((group.isEmpty() || group.equals(channel.group())) && (!onlyFavorites || isFavorite(channel)) && channel.name().toLowerCase(Locale.ROOT).contains(needle)) shown.add(channel);
        if(rows!=null) rows.notifyDataSetChanged(); if(covers!=null) covers.notifyDataSetChanged();
        boolean television=listMode(); list.setVisibility(television?View.VISIBLE:View.GONE); posters.setVisibility(television?View.GONE:View.VISIBLE);
        heading.setText(!seriesTitle.isEmpty()?seriesTitle:title(section)+(onlyFavorites?" · Ulubione":"")+(group.isEmpty()?"":" · "+group));
        bouquets.setNextFocusRightId(contentView().getId());
        if(selectionInfo!=null) { if(shown.isEmpty()) selectionInfo.setText(""); else describe(shown.get(Math.max(0,Math.min(shown.size()-1,contentView().getSelectedItemPosition())))); }
        status.setText(busy?"Wczytywanie biblioteki…":shown.isEmpty()?"Brak pozycji. Zmień filtr lub dodaj źródło.":Ui.count(shown.size())+" pozycji · ↑↓ wybór · → lista · OK wybierz · Menu opcje · Wstecz kategorie");
    }
    private Channel selected() { if(home || shown.isEmpty()) return null; int index=(list.getVisibility()==View.VISIBLE?list:posters).getSelectedItemPosition(); return index>=0 && index<shown.size()?shown.get(index):null; }
    private void options(Channel channel) {
        List<String> actions=new ArrayList<>(); if(channel.kind().equals("movie")) actions.add("Szczegóły filmu"); actions.add(channel.kind().equals("series")?"Odcinki":"Odtwórz"); if(channel.kind().equals("tv")) actions.add("Program TV i archiwum"); actions.add(isFavorite(channel)?"Usuń z ulubionych":"Dodaj do ulubionych");
        new AlertDialog.Builder(this).setTitle(channel.name()).setItems(actions.toArray(new String[0]),(d,index) -> { String action=actions.get(index); if(action.contains("ulubionych")) favorite(channel); else if(action.startsWith("Program")) GuideDialog.open(this,channel,worker,this::play); else if(action.equals("Odtwórz")) play(channel); else select(channel); }).setNegativeButton("Zamknij",null).show();
    }
    private void favorite(Channel channel) {
        if(isFavorite(channel)) { favorites.remove(channel.url()); List<Channel> episodes=seriesFolders.get(channel.url()); if(episodes!=null) for(Channel episode:episodes) favorites.remove(episode.url()); } else favorites.add(channel.url()); final Set<String> saved=new HashSet<>(favorites);
        worker.execute(() -> { try { new LibraryStore(this).saveFavorites(saved); } catch(Exception ignored) {} }); filter();
    }
    private boolean isFavorite(Channel channel) {
        if(favorites.contains(channel.url())) return true;
        List<Channel> episodes=seriesFolders.get(channel.url()); if(episodes!=null) for(Channel episode:episodes) if(favorites.contains(episode.url())) return true;
        return false;
    }
    private void select(Channel channel) {
        if(seriesFolders.containsKey(channel.url())) { openLocalSeries(channel); return; }
        if(channel.kind().equals("series")) startActivity(SeriesActivity.intent(this,channel,true)); else if(channel.kind().equals("movie")) startActivity(new Intent(this,MovieActivity.class).putExtra("television",true).putExtra("url",channel.url()).putExtra("name",channel.name()).putExtra("group",channel.group()).putExtra("logo",channel.logo())); else play(channel);
    }
    private void openLocalSeries(Channel folder) {
        seriesRootItems=items; seriesParentGroup=group; seriesParentQuery=query; seriesParentFavorites=onlyFavorites; seriesFolderUrl=folder.url();
        generation++; busy=false; items=TvCatalog.seasons(seriesFolders.get(folder.url())); seriesTitle=folder.name(); group=""; query=""; onlyFavorites=false; showCatalog(); rebuildGroups();
    }
    private void closeLocalSeries() {
        seriesTitle=""; items=seriesRootItems; group=seriesParentGroup; query=seriesParentQuery; onlyFavorites=seriesParentFavorites; showCatalog(); rebuildGroups();
        for(int i=0;i<shown.size();i++) if(shown.get(i).url().equals(seriesFolderUrl)) { contentView().setSelection(i); break; } contentView().requestFocus();
    }
    private void play(Channel channel) { startActivity(new Intent(this,PlayerActivity.class).putExtra("television",true).putExtra("url",channel.url()).putExtra("name",channel.name()).putExtra("kind",channel.kind()).putExtra("guide",channel.guideUrl()).putExtra("epgId",channel.epgId()).putExtra("days",channel.archiveDays()).putExtra("timezone",channel.timezone())); }
    private void search() { EditText field=new EditText(this); field.setSingleLine(true); field.setText(query); new AlertDialog.Builder(this).setTitle("Szukaj w sekcji").setView(field).setPositiveButton("Szukaj",(d,w) -> { query=field.getText().toString().trim(); group=""; filter(); }).setNegativeButton("Anuluj",null).show(); }
    private void sort() { new AlertDialog.Builder(this).setTitle("Kolejność kategorii").setItems(BouquetOrder.LABELS,(d,index) -> { getPreferences(MODE_PRIVATE).edit().putString("sort-"+section,BouquetOrder.MODES[index]).apply(); rebuildGroups(); }).show(); }
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
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code=event.getKeyCode();
        if(!home && (code==KeyEvent.KEYCODE_MEDIA_PLAY || code==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            if(event.getAction()==KeyEvent.ACTION_UP && !event.isCanceled()) { Channel selected=selected(); if(selected!=null) select(selected); }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean onKeyUp(int code,KeyEvent event) {
        if(!home && (code==KeyEvent.KEYCODE_MENU || code==KeyEvent.KEYCODE_INFO)) { if(contentView().hasFocus()) { Channel selected=selected(); if(selected!=null) options(selected); } else catalogOptions(); return true; }
        return super.onKeyUp(code,event);
    }
    @Override public void onBackPressed() {
        if(busy) { status.setText("Trwa pobieranie biblioteki…"); return; }
        if(!home && contentView().hasFocus()) { bouquets.setSelection(group.isEmpty()?0:Math.max(0,groups.indexOf(group)+1)); bouquets.requestFocus(); return; }
        if(!home && !seriesTitle.isEmpty()) closeLocalSeries();
        else if(!home && (!query.isEmpty() || onlyFavorites)) { query=""; onlyFavorites=false; filter(); }
        else if(!home) showHome();
        else new AlertDialog.Builder(this).setTitle("Zamknąć TV Player?").setPositiveButton("Wyjście",(d,w) -> finish()).setNegativeButton("Zostań",null).show();
    }
    @Override protected void onResume() { super.onResume(); foreground=true; scheduleGuides(); worker.execute(() -> { try { Set<String> saved=new LibraryStore(this).loadFavorites(); runOnUiThread(() -> { if(!isDestroyed() && !favorites.equals(saved)) { favorites.clear(); favorites.addAll(saved); if(!home) filter(); } }); } catch(Exception ignored) {} }); if(!home && rows!=null) rows.notifyDataSetChanged(); }
    @Override protected void onPause() { foreground=false; ui.removeCallbacks(guideRows); if(phoneSetup!=null) phoneSetup.close(); super.onPause(); }
    @Override protected void onDestroy() { generation++; ui.removeCallbacksAndMessages(null); worker.shutdown(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putString("section",section); state.putString("group",seriesTitle.isEmpty()?group:seriesParentGroup); state.putBoolean("home",home); super.onSaveInstanceState(state); }
}
