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
    private final Map<String,List<Channel>> catalogs=new ConcurrentHashMap<>();
    private final Map<String,TvCatalog.Catalog> preparedCatalogs=new ConcurrentHashMap<>();
    private final ExecutorService sourceWorker=Executors.newSingleThreadExecutor();
    private volatile int sourceEpoch;
    private boolean refreshing;
    private LivePreview livePreview;
    private String fullscreenToken;
    private static final int LIVE_FULLSCREEN=86;
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
    private TvShelfView shelves;
    private TextView emptyState;
    private boolean catalogGrid,rebinding,catalogReady;
    private TvBrowseState pendingLocation,parentLocation;
    private TvBrowseState pausedLocation;
    private TvBrowseState homeRecovery;
    private List<TvShelfCatalog.Shelf> homePreview=Collections.emptyList();
    private int homeRequest;
    private final com.google.gson.Gson json=new com.google.gson.Gson();
    private static final int TV_BG=0xFF080F19,TV_BLUE=0xFF00A8E1;
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
        TvBrowseState location=readLocation(state==null?navigationStore().getString("location",""):state.getString("location",""));
        if(location!=null) applyLocation(location);
        build(); loadAccount();
    }
    private void loadAccount() {
        worker.execute(() -> { try { account=new TvAccountStore(this).load(); } catch(Exception ignored) {} runOnUiThread(() -> { if(!isDestroyed()) { if(!home) load(section); else status.setText(account==null?"Dodaj konto Xtream lub adres listy M3U, aby rozpocząć.":"Biblioteka jest gotowa. Wybierz TV, filmy lub seriale."); } }); });
    }
    private void build() {
        page=column(); page.setBackgroundColor(TV_BG); page.setPadding(dp(24),dp(12),dp(24),dp(10));
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text(Ui.APP_NAME.toUpperCase(Locale.ROOT),18,Ui.TEXT); brand.setTypeface(null,Typeface.BOLD); brand.setLetterSpacing(.08f); header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextClock clock=new TextClock(this); clock.setTextColor(Ui.MUTED); clock.setTextSize(18); clock.setFormat24Hour("HH:mm"); clock.setFormat12Hour("HH:mm"); header.addView(clock);
        page.addView(header,new LinearLayout.LayoutParams(-1,dp(30)));
        body=column(); page.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        status=text("",13,Ui.MUTED); status.setSingleLine(true); status.setEllipsize(android.text.TextUtils.TruncateAt.END); status.setPadding(0,dp(6),0,0); page.addView(status,new LinearLayout.LayoutParams(-1,dp(26)));
        setContentView(page);
        if(home) showHome(); else showCatalog();
    }
    private android.content.SharedPreferences navigationStore() { return getSharedPreferences("tv-navigation",MODE_PRIVATE); }
    private TvBrowseState readLocation(String value) { try { return value==null || value.isEmpty()?null:json.fromJson(value,TvBrowseState.class); } catch(RuntimeException malformed) { return null; } }
    private void applyLocation(TvBrowseState location) {
        pendingLocation=location; home=location.home(); section=location.section(); group=location.group(); query=location.query(); onlyFavorites=location.favorites();
        seriesFolderUrl=location.folderId(); seriesTitle=location.folderTitle(); catalogGrid=location.grid(); parentLocation=location.parent();
    }
    private TvBrowseState location() {
        Channel selected=selected(); int selectedIndex=selected==null?0:Math.max(0,shown.indexOf(selected));
        int first=0,top=0,categoryFirst=0,categoryTop=0; boolean focused=false; String shelfKey=""; int shelfIndex=0;
        if(shelves!=null) { first=shelves.firstVisible(); top=shelves.offset(); categoryFirst=shelves.horizontalFirst(); categoryTop=shelves.horizontalOffset(); focused=shelves.hasFocus(); shelfKey=shelves.shelf(); shelfIndex=shelves.index(); }
        else if(list!=null && posters!=null) {
            AbsListView content=contentView(); first=content.getFirstVisiblePosition(); View chosen=content.getSelectedView(); top=chosen==null?0:chosen.getTop(); focused=content.hasFocus();
            if(bouquets!=null) { categoryFirst=bouquets.getSelectedItemPosition(); View selectedCategory=bouquets.getSelectedView(); categoryTop=selectedCategory==null?0:selectedCategory.getTop(); }
        }
        return new TvBrowseState(home,section,group,query,onlyFavorites,seriesTitle.isEmpty()?"":seriesFolderUrl,seriesTitle,TvBrowseState.id(selected),selected==null?"":selected.name(),selectedIndex,first,top,categoryFirst,categoryTop,focused,catalogGrid,shelfKey,shelfIndex,parentLocation);
    }
    private void rememberLocation() {
        if(pendingLocation!=null && !catalogReady) return;
        TvBrowseState location=location(); android.content.SharedPreferences.Editor edit=navigationStore().edit().putString("location",json.toJson(location));
        if(!home) edit.putString("last-browse",json.toJson(location)).putString("section-"+section,json.toJson(location)); edit.apply();
    }
    private void restoreLocation(TvBrowseState location) {
        if(location==null || location.home()) { showHome(); return; }
        generation++; busy=false; applyLocation(location); catalogReady=false; items=new ArrayList<>(); shown=new ArrayList<>(); groups=new ArrayList<>(); seriesFolders=Collections.emptyMap(); showCatalog(); focusNavigation(); load(section);
    }
    private void restoreContent(TvBrowseState location) {
        if(location==null || home) return;
        if(shelves!=null) return; // Shelf submit restores both axes and the focused card.
        ui.post(() -> {
            if(home || list==null || pendingLocation!=location) return;
            AbsListView content=contentView(); int selected=location.locate(shown);
            if(location.contentFocus()) content.requestFocus(); else if(bouquets!=null) bouquets.requestFocus();
            content.setSelection(selected);
            if(content instanceof ListView) ((ListView)content).setSelectionFromTop(selected,location.top());
            else if(content instanceof GridView) ((GridView)content).setSelection(selected);
            if(bouquets!=null && bouquets.getVisibility()==View.VISIBLE) bouquets.setSelectionFromTop(group.isEmpty()?0:Math.max(0,groups.indexOf(group)+1),location.categoryTop());
            rebinding=false; pendingLocation=null;
        });
    }
    private boolean shelfLayout() { return !section.equals("tv") && seriesTitle.isEmpty() && !catalogGrid && navigationStore().getString("view-"+section,"shelves").equals("shelves"); }
    private void addNavigation() {
        navigation=new LinearLayout(this); navigation.setGravity(Gravity.CENTER_VERTICAL); navigation.setPadding(0,dp(4),0,dp(4));
        String[] labels={"Główna","TV","Filmy","Seriale","Szukaj","Opcje"};
        for(int i=0;i<labels.length;i++) {
            final int index=i; Button tab=button(labels[i],() -> { if(index==0) showHome(); else if(index<=3) open(new String[]{"tv","movie","series"}[index-1]); else if(index==4) { if(home) open("movie"); search(); } else if(home) homeOptions(); else catalogOptions(); });
            tab.setId(View.generateViewId()); tab.setTextSize(17); tab.setSingleLine(true); tab.setPadding(dp(16),0,dp(16),0);
            boolean active=home?i==0:i==1 && section.equals("tv") || i==2 && section.equals("movie") || i==3 && section.equals("series");
            android.graphics.drawable.StateListDrawable background=new android.graphics.drawable.StateListDrawable(); background.addState(new int[]{android.R.attr.state_focused},Ui.shape(this,Ui.TEXT,20,0)); background.addState(new int[]{},Ui.shape(this,0,20,0)); tab.setBackground(background);
            tab.setTextColor(new ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},new int[]{TV_BG,active?TV_BLUE:Ui.MUTED})); tab.setTypeface(null,active?Typeface.BOLD:Typeface.NORMAL);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-2,dp(42)); size.rightMargin=dp(12); navigation.addView(tab,size);
        }
        body.addView(navigation);
    }
    private void focusNavigation() { if(navigation!=null) navigation.getChildAt(home?0:section.equals("tv")?1:section.equals("movie")?2:3).requestFocus(); }
    private TvShelfView createShelves() {
        return new TvShelfView(this,new TvShelfView.Actions() {
            public void selected(Channel channel,String row,int index) { if(channel!=null) describe(channel); }
            public void open(Channel channel) { openCard(channel); }
            public void more(TvShelfCatalog.Shelf row) { openShelfGrid(row); }
            public void options(Channel channel) { if(!channel.kind().equals("shortcut")) TvActivity.this.options(channel); }
            public void navigation() { focusNavigation(); }
        });
    }
    private void clearCatalogViews() { if(livePreview!=null) { livePreview.destroy(); livePreview=null; } shelves=null; emptyState=null; list=null; posters=null; bouquets=null; rows=null; covers=null; selectionInfo=null; }
    private void openCard(Channel channel) { if(channel.url().startsWith("section:")) open(channel.url().substring(8)); else if(channel.url().equals("action:resume")) restoreLocation(readLocation(navigationStore().getString("last-browse",""))); else select(channel); }
    private void showHome() {
        busy=false; generation++;
        if(!home && list!=null || !home && shelves!=null) rememberLocation();
        homeRecovery=pendingLocation!=null && pendingLocation.home()?pendingLocation:null;
        home=true; seriesTitle=""; parentLocation=null; catalogGrid=false; pendingLocation=null; ui.removeCallbacks(guideRows); body.removeAllViews(); clearCatalogViews(); addNavigation();
        heading=text("Co oglądamy?",22,Ui.TEXT); heading.setTypeface(null,Typeface.BOLD); heading.setPadding(dp(5),dp(4),0,dp(8)); body.addView(heading);
        shelves=createShelves(); body.addView(shelves,new LinearLayout.LayoutParams(-1,0,1)); showHomeRows(true);
        status.setText(account==null?"Dodaj źródło w Opcje → Źródło → Dodaj przez telefon":"←→ wybór · ↑↓ kategorie · OK otwórz · Menu opcje");
        rememberLocation(); loadHomePreviews();
    }
    private void showHomeRows(boolean focus) {
        if(!home || shelves==null) return;
        List<Channel> shortcuts=new ArrayList<>(); shortcuts.add(new Channel("Telewizja na żywo","section:tv","Oglądaj","","shortcut")); shortcuts.add(new Channel("Filmy","section:movie","Oglądaj","","shortcut")); shortcuts.add(new Channel("Seriale","section:series","Oglądaj","","shortcut"));
        TvBrowseState last=readLocation(navigationStore().getString("last-browse","")); if(last!=null) shortcuts.add(new Channel("Wróć do przeglądania","action:resume","Oglądaj","","shortcut"));
        List<TvShelfCatalog.Shelf> rows=new ArrayList<>(); rows.add(new TvShelfCatalog.Shelf("start","Oglądaj",shortcuts,shortcuts.size())); rows.addAll(homePreview);
        TvBrowseState anchor=homeRecovery!=null?homeRecovery:focus?null:location(); boolean ready=homeRecovery==null || rows.stream().anyMatch(row -> row.key().equals(homeRecovery.shelfKey()));
        shelves.submit(rows,anchor,ready && (homeRecovery!=null || focus || shelves.hasFocus())); if(ready) homeRecovery=null;
    }
    private void loadHomePreviews() {
        final int request=++homeRequest;
        worker.execute(() -> {
            List<TvShelfCatalog.Shelf> preview=new ArrayList<>();
            try { LibraryStore store=new LibraryStore(this); List<Channel> movies=store.previewSection("movie",18),series=TvCatalog.series(store.previewSection("series",240)).items();
                if(!movies.isEmpty()) preview.add(new TvShelfCatalog.Shelf("home-movies","Filmy z Twojej biblioteki",movies,movies.size()));
                if(!series.isEmpty()) preview.add(new TvShelfCatalog.Shelf("home-series","Twoje seriale",series,series.size()));
            } catch(Exception ignored) { }
            runOnUiThread(() -> { if(isDestroyed() || request!=homeRequest) return; homePreview=preview; if(home) showHomeRows(false); });
        });
    }
    private void homeOptions() {
        new AlertDialog.Builder(this).setTitle("Opcje").setItems(new String[]{"Konto","Źródło","Odśwież źródło","Ustawienia","O aplikacji","Wyjście"},(d,i) -> {
            switch(i) { case 0: account(); break; case 1: sourceDialog(); break; case 2: refresh(); break; case 3: settings(); break; case 4: info(); break; case 5: finish(); break; }
        }).show();
    }
    private void open(String kind) { if(!home && section.equals(kind)) return; busy=false; if(!home) rememberLocation(); TvBrowseState saved=readLocation(navigationStore().getString("section-"+kind,"")); if(saved!=null) { restoreLocation(saved); return; } generation++; section=kind; group=""; query=""; seriesTitle=""; seriesFolderUrl=""; parentLocation=null; pendingLocation=null; catalogGrid=false; catalogReady=false; onlyFavorites=false; seriesFolders=Collections.emptyMap(); items=new ArrayList<>(); shown=new ArrayList<>(); groups=new ArrayList<>(); home=false; showCatalog(); focusNavigation(); load(kind); }
    private TvCatalog.Catalog presentation(String kind,List<Channel> channels) { return kind.equals("series")?TvCatalog.series(channels):new TvCatalog.Catalog(channels,Collections.emptyMap()); }
    private void applyCatalog(TvCatalog.Catalog catalog) {
        catalogReady=true;
        items=catalog.items(); seriesFolders=catalog.folders();
        if(!seriesTitle.isEmpty()) {
            List<Channel> episodes=seriesFolders.get(seriesFolderUrl);
            if(episodes!=null) { seriesRootItems=items; items=TvCatalog.seasons(episodes); }
            else { seriesTitle=""; seriesFolderUrl=""; parentLocation=null; group=""; showCatalog(); }
        }
        TvBrowseState restore=pendingLocation; rebuildGroups(); restoreContent(restore);
        if(restore==null && shelves==null) { if(bouquets.getVisibility()==View.VISIBLE) bouquets.requestFocus(); else contentView().requestFocus(); }
    }
    private void load(String kind) {
        TvCatalog.Catalog prepared=preparedCatalogs.get(kind); if(prepared!=null) { busy=false; applyCatalog(prepared); return; }
        if(busy) return; final int request=++generation; busy=true; status.setText("Wczytywanie biblioteki…");
        final int epoch=sourceEpoch;
        worker.execute(() -> {
            try {
                List<Channel> loaded=catalogs.get(kind);
                if(loaded==null) loaded=new LibraryStore(this).loadSection(kind,favorites);
                if(account!=null && account.type().equals("xtream") && !getPreferences(MODE_PRIVATE).getBoolean("loaded-"+kind,false)) {
                    final List<Channel> cached=loaded; final TvCatalog.Catalog cachedView=presentation(kind,cached);
                    if(!cached.isEmpty()) runOnUiThread(() -> {
                        if(isDestroyed() || request!=generation || epoch!=sourceEpoch) return;
                        applyCatalog(cachedView); status.setText("Zapisana biblioteka · sprawdzanie aktualizacji…");
                    });
                    try {
                        loaded=source.xtreamSection(account.server(),account.user(),account.password(),kind,this::progress);
                        LibraryStore store=new LibraryStore(this); store.updateSection(kind,loaded);
                        getPreferences(MODE_PRIVATE).edit().putBoolean("loaded-"+kind,true).apply();
                    } catch(Exception failure) {
                        if(cached.isEmpty()) throw failure;
                        if(epoch==sourceEpoch) catalogs.put(kind,cached);
                        runOnUiThread(() -> {
                            if(isDestroyed() || request!=generation || epoch!=sourceEpoch) return;
                            busy=false; applyCatalog(cachedView); status.setText("Dostawca niedostępny · korzystasz z zapisanej biblioteki.");
                        });
                        return;
                    }
                }
                if(epoch==sourceEpoch) catalogs.put(kind,loaded); final TvCatalog.Catalog result=presentation(kind,loaded); if(epoch==sourceEpoch) preparedCatalogs.put(kind,result);
                runOnUiThread(() -> { if(isDestroyed() || request!=generation || epoch!=sourceEpoch) return; busy=false; applyCatalog(result); });
            } catch(Exception e) { runOnUiThread(() -> { if(!isDestroyed() && request==generation && epoch==sourceEpoch) { busy=false; status.setText("Nie udało się wczytać biblioteki."); error("Nie można pobrać katalogu. Sprawdź połączenie i ważność źródła."); } }); }
        });
    }
    private void progress(String value) { runOnUiThread(() -> { if(!isDestroyed()) status.setText(value); }); }
    private void showCatalog() {
        home=false; scrolling=false; body.removeAllViews();
        if(shelfLayout()) { showStreamingCatalog(); return; }
        clearCatalogViews(); addNavigation();
        heading=text(title(section),22,Ui.TEXT); heading.setSingleLine(true); heading.setEllipsize(android.text.TextUtils.TruncateAt.END); heading.setTypeface(null,Typeface.BOLD); heading.setPadding(0,dp(10),0,dp(8)); body.addView(heading);
        LinearLayout split=new LinearLayout(this);
        bouquets=new ListView(this); bouquets.setId(View.generateViewId()); bouquets.setDivider(null); bouquets.setSelector(catalogCursor()); bouquets.setDrawSelectorOnTop(true); bouquets.setItemsCanFocus(false); bouquets.setPadding(0,0,dp(10),0);
        bouquets.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id) { if(parent==bouquets && parent.hasFocus() && !busy && !rebinding) chooseGroup(position,false); }
        });
        bouquets.setOnItemClickListener((parent,view,position,id) -> chooseGroup(position,true));
        bouquets.setOnKeyListener((view,key,event) -> {
            if(key!=KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) chooseGroup(bouquets.getSelectedItemPosition(),true);
            return true;
        });
        int sidebar=section.equals("tv") || !seriesTitle.isEmpty()?(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density>=1100?220:200):0;
        if(sidebar==0) bouquets.setVisibility(View.GONE);
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
        split.addView(catalog,new LinearLayout.LayoutParams(0,-1,1));
        if(section.equals("tv")) {
            livePreview=new LivePreview(this,true,null,this::play,() -> { if(rows!=null) rows.notifyDataSetChanged(); },true);
            LinearLayout.LayoutParams previewSize=new LinearLayout.LayoutParams(dp(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density>=1100?420:320),-1); previewSize.leftMargin=dp(14); split.addView(livePreview.panel,previewSize);
            list.setNextFocusRightId(livePreview.controls().getId());
            livePreview.controls().setNextFocusLeftId(list.getId());
        }
        body.addView(split,new LinearLayout.LayoutParams(-1,0,1));
        selectionInfo=text("",17,Ui.TEXT); selectionInfo.setMaxLines(2); selectionInfo.setEllipsize(android.text.TextUtils.TruncateAt.END); selectionInfo.setPadding(dp(sidebar+4),dp(6),0,0); selectionInfo.setContentDescription("Wybrana pozycja"); body.addView(selectionInfo,new LinearLayout.LayoutParams(-1,dp(48)));
        filter(); if(bouquets.getVisibility()==View.VISIBLE) bouquets.requestFocus(); else contentView().requestFocus();
    }
    private void showStreamingCatalog() {
        clearCatalogViews(); addNavigation();
        LinearLayout filters=new LinearLayout(this); filters.setGravity(Gravity.CENTER_VERTICAL);
        heading=text(title(section),22,Ui.TEXT); heading.setTypeface(null,Typeface.BOLD); filters.addView(heading,new LinearLayout.LayoutParams(0,dp(42),1));
        Button categories=button("Kategorie",this::categories),favoritesButton=button("Moja lista",() -> { onlyFavorites=!onlyFavorites; filter(); });
        categories.setTextSize(15); favoritesButton.setTextSize(15); filters.addView(categories,new LinearLayout.LayoutParams(dp(136),dp(38))); filters.addView(favoritesButton,new LinearLayout.LayoutParams(dp(136),dp(38))); body.addView(filters);
        FrameLayout content=new FrameLayout(this); shelves=createShelves(); content.addView(shelves,new FrameLayout.LayoutParams(-1,-1));
        emptyState=text("Brak pozycji. Wybierz kategorię lub odśwież źródło.",19,Ui.MUTED); emptyState.setGravity(Gravity.CENTER); content.addView(emptyState,new FrameLayout.LayoutParams(-1,-1)); body.addView(content,new LinearLayout.LayoutParams(-1,0,1)); filter();
        for(int i=0;i<navigation.getChildCount();i++) navigation.getChildAt(i).setOnKeyListener((v,code,event) -> { if(code==KeyEvent.KEYCODE_DPAD_DOWN) { if(event.getAction()==KeyEvent.ACTION_DOWN) shelves.focusCurrent(); return true; } return false; });
    }
    private boolean listMode() { return section.equals("tv") || !seriesTitle.isEmpty() || navigationStore().getString("view-"+section,"shelves").equals("list"); }
    private AbsListView contentView() { return listMode()?list:posters; }
    private void chooseGroup(int position,boolean enter) {
        if(position<0 || position>groups.size()) return;
        String next=position==0?"":groups.get(position-1);
        if(!next.equals(group)) { group=next; query=""; filter(); contentView().setSelection(0); bouquets.invalidateViews(); }
        if(enter) { contentView().requestFocus(); if(!shown.isEmpty()) contentView().setSelection(0); }
    }
    private void catalogOptions() {
        List<String> actions=new ArrayList<>(); actions.add("Kategorie"); actions.add("☆ Ulubione"); actions.add("Sortuj"); if(!section.equals("tv") && seriesTitle.isEmpty()) actions.add("Widok"); actions.add("Źródło"); actions.add("Konto"); actions.add("Ustawienia"); actions.add("Odśwież źródło"); actions.add("Główna");
        new AlertDialog.Builder(this).setTitle("Opcje katalogu").setItems(actions.toArray(new String[0]),(d,index) -> {
            switch(actions.get(index)) { case "Kategorie": categories(); break; case "☆ Ulubione": onlyFavorites=!onlyFavorites; filter(); break; case "Sortuj": sort(); break; case "Źródło": sourceDialog(); break; case "Konto": account(); break; case "Ustawienia": settings(); break; case "Odśwież źródło": refresh(); break; case "Główna": showHome(); break; case "Widok": chooseView(); break; }
        }).setNegativeButton("Zamknij",null).show();
    }
    private void chooseView() {
        String[] values={"shelves","grid","list"}; String current=navigationStore().getString("view-"+section,"shelves");
        new AlertDialog.Builder(this).setTitle("Widok biblioteki").setSingleChoiceItems(new String[]{"Rzędy okładek","Siatka plakatów","Lista"},Math.max(0,Arrays.asList(values).indexOf(current)),(d,index) -> {
            TvBrowseState before=location(); navigationStore().edit().putString("view-"+section,values[index]).apply(); catalogGrid=false; parentLocation=null;
            pendingLocation=new TvBrowseState(false,section,group,query,onlyFavorites,"","",before.selectedId(),before.selectedName(),before.selectedIndex(),0,0,0,0,true,false,before.shelfKey(),before.shelfIndex(),null);
            d.dismiss(); showCatalog(); rebuildGroups(); restoreContent(pendingLocation);
        }).setNegativeButton("Zamknij",null).show();
    }
    private void categories() {
        List<String> labels=new ArrayList<>(); labels.add(seriesTitle.isEmpty()?"Cała oferta":"Wszystkie odcinki"); labels.addAll(groups);
        List<String> visible=new ArrayList<>(labels); ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_single_choice,visible);
        EditText search=new EditText(this); search.setSingleLine(true); search.setHint("Szukaj bukietu, np. UK Movies"); search.setTextSize(17); search.setPadding(dp(20),dp(10),dp(20),dp(10));
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence text,int start,int count,int after) {} public void onTextChanged(CharSequence text,int start,int before,int count) { String value=text.toString(); adapter.clear(); for(String label:labels) if(value.isEmpty() || TitleSearch.matches(label,value)) adapter.add(label); adapter.notifyDataSetChanged(); } public void afterTextChanged(android.text.Editable text) {}
        });
        new AlertDialog.Builder(this).setTitle(section.equals("tv")?"Bukiety TV":"Kategorie · "+title(section)).setView(search).setSingleChoiceItems(adapter,-1,(d,index) -> {
            String selected=adapter.getItem(index); TvBrowseState before=location(); group=selected.equals(labels.get(0))?"":selected; query=""; d.dismiss();
            if(shelves!=null && !home && !group.isEmpty()) { openShelfGrid(new TvShelfCatalog.Shelf(group,group,Collections.emptyList(),0)); parentLocation=before; }
            else { filter(); if(shelves!=null) shelves.focusCurrent(); else if(!shown.isEmpty()) { contentView().requestFocus(); contentView().setSelection(0); } }
        }).setPositiveButton("Odśwież źródło",(d,w) -> refresh()).setNegativeButton("Zamknij",null).show();
    }
    private void openShelfGrid(TvShelfCatalog.Shelf row) {
        if(home) { open(row.key().equals("home-series")?"series":"movie"); return; }
        parentLocation=location(); group=row.key(); query=""; catalogGrid=true; pendingLocation=null; showCatalog(); rebuildGroups(); contentView().requestFocus(); contentView().setSelection(0);
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
        row.view.setBackground(Ui.shape(this,livePreview!=null && channel.url().equals(livePreview.selectedUrl())?0xFF12334D:Ui.SURFACE,8,0));
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
        if(shelves!=null) { filter(); return; }
        rebinding=true;
        bouquets.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels) {
            @Override public View getView(int p,View reused,ViewGroup parent) {
                TextView row=(TextView)super.getView(p,reused,parent); boolean active=p==0?group.isEmpty():group.equals(groups.get(p-1));
                row.setLayoutParams(new AbsListView.LayoutParams(-1,-2)); row.setTextSize(16); row.setTextColor(active?Ui.TEXT:Ui.MUTED); row.setTypeface(null,active?Typeface.BOLD:Typeface.NORMAL); row.setMinHeight(dp(52)); row.setMaxLines(2); row.setEllipsize(android.text.TextUtils.TruncateAt.END); row.setPadding(dp(12),dp(8),dp(10),dp(8)); row.setBackground(Ui.shape(TvActivity.this,active?Ui.SURFACE_HIGH:0,8,0)); return row;
            }
        }); bouquets.setSelection(group.isEmpty()?0:Math.max(0,groups.indexOf(group)+1)); filter(); if(pendingLocation==null) ui.post(() -> rebinding=false);
    }
    private void filter() {
        if(home) return;
        TvBrowseState anchor=pendingLocation!=null?pendingLocation:location(); boolean firstFocus=shelves!=null && shelves.selected()==null;
        shown=new ArrayList<>(); String[] tokens=TitleSearch.tokens(query); Map<String,Boolean> matchingGroups=new HashMap<>();
        for(Channel channel:items) if((group.isEmpty() || group.equals(channel.group())) && (!onlyFavorites || isFavorite(channel)) && (tokens.length==0 || matchingGroups.computeIfAbsent(channel.group(),value -> TitleSearch.score(TitleSearch.normalize(value),tokens)>=0) || TitleSearch.score(TitleSearch.normalize(channel.name()),tokens)>=0)) shown.add(channel);
        if(shelves!=null) {
            List<TvShelfCatalog.Shelf> data=TvShelfCatalog.build(shown,getPreferences(MODE_PRIVATE).getString("sort-"+section,"provider"));
            boolean focus=catalogReady && (firstFocus || anchor.contentFocus()); shelves.submit(data,anchor,focus); if(!focus && pendingLocation!=null) focusNavigation(); if(catalogReady) pendingLocation=null;
            if(emptyState!=null) emptyState.setVisibility(catalogReady && shown.isEmpty()?View.VISIBLE:View.GONE);
            heading.setText(title(section)+(onlyFavorites?" · Moja lista":"")+(group.isEmpty()?"":" · "+group));
            status.setText(busy?"Wczytywanie biblioteki…":Ui.count(shown.size())+" pozycji · ←→ okładki · ↑↓ kategorie · OK szczegóły · Wstecz nawigacja"); return;
        }
        if(list==null) return;
        if(rows!=null) rows.notifyDataSetChanged(); if(covers!=null) covers.notifyDataSetChanged();
        boolean television=listMode(); list.setVisibility(television?View.VISIBLE:View.GONE); posters.setVisibility(television?View.GONE:View.VISIBLE);
        if(livePreview!=null) { livePreview.show(shown,true,group+"|"+query+"|"+onlyFavorites); if(foreground) livePreview.resume(); }
        heading.setText(!seriesTitle.isEmpty()?seriesTitle:title(section)+(onlyFavorites?" · Ulubione":"")+(group.isEmpty()?"":" · "+group));
        if(bouquets.getVisibility()==View.VISIBLE) bouquets.setNextFocusRightId(contentView().getId());
        if(selectionInfo!=null) { if(shown.isEmpty()) selectionInfo.setText(""); else describe(shown.get(Math.max(0,Math.min(shown.size()-1,contentView().getSelectedItemPosition())))); }
        status.setText(busy?"Wczytywanie biblioteki…":shown.isEmpty()?"Brak pozycji. Zmień kategorię lub dodaj źródło.":Ui.count(shown.size())+(livePreview!=null?" kanałów · OK podgląd / pełny ekran · Menu opcje":" pozycji · ↑↓ wybór · OK otwórz · Menu opcje · Wstecz nawigacja"));
    }
    private Channel selected() { if(home || shown.isEmpty()) return null; if(shelves!=null) return shelves.selected(); if(list==null || posters==null) return null; int index=contentView().getSelectedItemPosition(); return index>=0 && index<shown.size()?shown.get(index):null; }
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
        rememberLocation();
        if(channel.kind().equals("tv") && livePreview!=null && !channel.url().equals(livePreview.selectedUrl())) { livePreview.select(channel); return; }
        if(seriesFolders.containsKey(channel.url())) { openLocalSeries(channel); return; }
        if(channel.kind().equals("series")) startActivity(SeriesActivity.intent(this,channel,true)); else if(channel.kind().equals("movie")) startActivity(new Intent(this,MovieActivity.class).putExtra("television",true).putExtra("url",channel.url()).putExtra("name",channel.name()).putExtra("group",channel.group()).putExtra("logo",channel.logo())); else play(channel);
    }
    private void openLocalSeries(Channel folder) {
        TvBrowseState before=location(); seriesRootItems=items; seriesFolderUrl=folder.url(); parentLocation=before;
        generation++; busy=false; items=TvCatalog.seasons(seriesFolders.get(folder.url())); seriesTitle=folder.name(); group=""; query=""; onlyFavorites=false; pendingLocation=null; showCatalog(); rebuildGroups(); contentView().requestFocus(); contentView().setSelection(0);
    }
    private void closeLocalSeries() { TvBrowseState parent=parentLocation; if(parent!=null) restoreLocation(parent); else { seriesTitle=""; seriesFolderUrl=""; parentLocation=null; items=seriesRootItems==null?new ArrayList<>():seriesRootItems; group=""; showCatalog(); rebuildGroups(); } }
    private void play(Channel channel) {
        rememberLocation(); Intent intent=new Intent(this,PlayerActivity.class).putExtra("television",true).putExtra("url",channel.url()).putExtra("name",channel.name()).putExtra("kind",channel.kind()).putExtra("group",channel.group()).putExtra("logo",channel.logo()).putExtra("guide",channel.guideUrl()).putExtra("epgId",channel.epgId()).putExtra("days",channel.archiveDays()).putExtra("timezone",channel.timezone());
        if(channel.kind().equals("tv")) {
            intent.putExtra("navigation_session",LiveChannelQueue.remember(shown,channel.url())).putExtra("navigation_scope",true).putExtra("navigation_group",group).putExtra("navigation_query",query).putExtra("navigation_favorites",onlyFavorites);
            if(livePreview!=null && channel.url().equals(livePreview.selectedUrl())) { fullscreenToken=livePreview.handoff(); intent.putExtra("playback_handoff",fullscreenToken).putExtra("return_preview",true); startActivityForResult(intent,LIVE_FULLSCREEN); return; }
        }
        startActivity(intent);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data); if(request!=LIVE_FULLSCREEN) return;
        PlaybackHandoff.discard(fullscreenToken); fullscreenToken=null;
        if(result!=RESULT_OK || data==null || data.getStringExtra("url")==null) return;
        String token=data.getStringExtra("playback_handoff"),returned=data.getStringExtra("url");
        if(isDestroyed() || isFinishing() || livePreview==null || !section.equals("tv")) { PlaybackHandoff.discard(token); return; }
        Channel channel=null; int index=-1; for(int i=0;i<shown.size();i++) if(shown.get(i).url().equals(returned)) { channel=shown.get(i); index=i; break; }
        if(channel==null) { PlaybackHandoff.discard(token); return; }
        int top=pausedLocation==null?0:pausedLocation.top();
        pausedLocation=null; pendingLocation=null; livePreview.receive(token,channel); list.requestFocus(); list.setSelectionFromTop(index,top); rememberLocation();
    }
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
        if(busy || refreshing) return;
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
        if(busy || refreshing) return;
        final boolean sameSource=next.equals(account); refreshing=true; busy=!sameSource || !catalogReady; final int epoch=++sourceEpoch; generation++;
        status.setText(sameSource && catalogReady?"Aktualizacja źródła w tle…":"Łączenie ze źródłem…");
        final Set<String> savedFavorites=new HashSet<>(favorites);
        sourceWorker.execute(() -> {
            try {
                List<Channel> imported=next.type().equals("xtream")?source.xtreamSection(next.server(),next.user(),next.password(),"tv",this::progress):source.m3u(next.server());
                if(isDestroyed() || epoch!=sourceEpoch) return;
                LibraryStore store=new LibraryStore(this);
                if(sameSource && next.type().equals("xtream") && new java.io.File(getFilesDir(),"catalog.current").isFile()) store.updateSection("tv",imported);
                else store.save(imported,savedFavorites);
                new TvAccountStore(this).save(next);
                android.content.SharedPreferences.Editor flags=getPreferences(MODE_PRIVATE).edit().putBoolean("loaded-tv",true);
                if(!sameSource || !next.type().equals("xtream")) flags.putBoolean("loaded-movie",!next.type().equals("xtream")).putBoolean("loaded-series",!next.type().equals("xtream")); flags.apply();
                runOnUiThread(() -> {
                    if(isDestroyed() || epoch!=sourceEpoch) return;
                    TvBrowseState current=location(); account=next; catalogs.clear(); preparedCatalogs.clear(); homePreview=Collections.emptyList(); homeRequest++; busy=false; refreshing=false;
                    if(sameSource && !current.home()) restoreLocation(current);
                    else if(sameSource) showHome();
                    else { navigationStore().edit().remove("location").remove("last-browse").remove("section-tv").remove("section-movie").remove("section-series").apply(); section="tv"; group=""; query=""; seriesTitle=""; seriesFolderUrl=""; parentLocation=null; pendingLocation=null; catalogGrid=false; home=false; openInitialTv(); }
                });
                if(next.type().equals("m3u")) completeLiveGroups(next,epoch);
            } catch(Exception failure) {
                runOnUiThread(() -> { if(!isDestroyed() && epoch==sourceEpoch) { refreshing=false; busy=false; status.setText("Nie udało się odświeżyć źródła. Zapisana biblioteka pozostaje dostępna."); error("Sprawdź połączenie i dostępność źródła. Zapisana biblioteka pozostaje dostępna."); } });
            }
        });
    }
    private void openInitialTv() { catalogReady=false; items=new ArrayList<>(); shown=new ArrayList<>(); groups=new ArrayList<>(); showCatalog(); load("tv"); }
    private void completeLiveGroups(TvAccountStore.Account expected,int epoch) {
        try {
            LibraryStore store=new LibraryStore(this); List<Channel> current=store.loadSection("tv",new HashSet<>());
            List<Channel> missing=source.missingM3uLiveChannels(expected.server(),current);
            if(missing.isEmpty() || epoch!=sourceEpoch || isDestroyed()) return;
            List<Channel> complete=new ArrayList<>(current); complete.addAll(missing); store.updateSection("tv",complete);
            runOnUiThread(() -> { if(isDestroyed() || epoch!=sourceEpoch || !expected.equals(account)) return; catalogs.remove("tv"); preparedCatalogs.remove("tv"); if(foreground && !home && section.equals("tv")) { TvBrowseState before=location(); restoreLocation(before); status.setText("Uzupełniono brakujące bukiety TV z panelu dostawcy."); } });
        } catch(Exception ignored) { /* A valid M3U remains usable when the optional panel is unavailable. */ }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code=event.getKeyCode();
        if((!home || shelves!=null) && (code==KeyEvent.KEYCODE_MEDIA_PLAY || code==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            if(event.getAction()==KeyEvent.ACTION_UP && !event.isCanceled()) { Channel selected=home?shelves.selected():selected(); if(selected!=null) openCard(selected); }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean onKeyUp(int code,KeyEvent event) {
        if(!home && (code==KeyEvent.KEYCODE_MENU || code==KeyEvent.KEYCODE_INFO)) { if(shelves!=null || contentView().hasFocus()) { Channel selected=selected(); if(selected!=null) options(selected); } else catalogOptions(); return true; }
        if(home && code==KeyEvent.KEYCODE_MENU) { homeOptions(); return true; }
        return super.onKeyUp(code,event);
    }
    @Override public void onBackPressed() {
        if(busy) { status.setText("Trwa pobieranie biblioteki…"); return; }
        if(!home && parentLocation!=null) { restoreLocation(parentLocation); return; }
        if(livePreview!=null && livePreview.panel.hasFocus()) { list.requestFocus(); return; }
        if(!home && list!=null && contentView().hasFocus() && bouquets.getVisibility()==View.VISIBLE) { bouquets.setSelection(group.isEmpty()?0:Math.max(0,groups.indexOf(group)+1)); bouquets.requestFocus(); return; }
        if(!home) {
            rememberLocation();
            new AlertDialog.Builder(this).setTitle("Nawigacja").setItems(new String[]{"Wróć do przeglądania","Główna","TV","Filmy","Seriale"},(d,index) -> { if(index==1) showHome(); else if(index>=2) open(new String[]{"tv","movie","series"}[index-2]); }).setNegativeButton("Zamknij",null).show(); return;
        }
        new AlertDialog.Builder(this).setTitle("Zamknąć TV Player?").setPositiveButton("Wyjście",(d,w) -> finish()).setNegativeButton("Zostań",null).show();
    }
    @Override protected void onResume() {
        super.onResume(); foreground=true; scheduleGuides();
        ImageLoader.get(this).resume(page);
        if(livePreview!=null) livePreview.resume();
        if(!home && !busy && pausedLocation!=null) { pendingLocation=pausedLocation; if(shelves!=null) filter(); else restoreContent(pendingLocation); } pausedLocation=null;
        worker.execute(() -> { try { Set<String> saved=new LibraryStore(this).loadFavorites(); runOnUiThread(() -> { if(!isDestroyed() && !favorites.equals(saved)) { favorites.clear(); favorites.addAll(saved); if(!home) filter(); } }); } catch(Exception ignored) {} });
    }
    @Override protected void onPause() { pausedLocation=location(); rememberLocation(); foreground=false; ui.removeCallbacks(guideRows); if(livePreview!=null) livePreview.pause(); ImageLoader.get(this).pause(page); if(phoneSetup!=null) phoneSetup.close(); super.onPause(); }
    @Override protected void onDestroy() { generation++; homeRequest++; sourceEpoch++; ui.removeCallbacksAndMessages(null); if(livePreview!=null) livePreview.destroy(); source.cancel(); worker.shutdown(); sourceWorker.shutdownNow(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putString("location",json.toJson(pendingLocation!=null?pendingLocation:location())); super.onSaveInstanceState(state); }
}
