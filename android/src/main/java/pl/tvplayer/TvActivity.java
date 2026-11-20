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
    /** Background preparation of films, series and channels so opening a section is instant. */
    private final ExecutorService prefetcher=Executors.newSingleThreadExecutor(task -> { Thread thread=new Thread(task,"tv-library-prefetch"); thread.setPriority(Thread.MIN_PRIORITY); return thread; });
    private final Map<String,Object> sectionLocks=new ConcurrentHashMap<>();
    private volatile boolean prefetched;
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
    private ImageView backdrop;
    private List<Channel> countsSource; private String countsMode=""; private Map<String,Integer> countsCache=Collections.emptyMap();
    private LinearLayout navigation;
    private Button[] navigationTabs;
    private Button androidCategories;
    private AlertDialog androidBouquetsDialog;
    private ListView bouquets,list,rail;
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
    private static final int TV_BG=Ui.BG,TV_BLUE=Ui.ACCENT;
    private int generation;
    private final android.os.Handler ui=new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean scrolling;
    private volatile boolean foreground;
    private final Runnable guideRows=this::loadVisibleGuides;
    private static final class ChannelRow {
        LinearLayout view; ImageView logo; TextView number,name,badges,epg,star; ProgressBar progress; Channel channel; boolean requested;
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
    /** "UK| MOVIES" → "UK", "Sport" → "SP": a short badge for bouquet rows. */
    private static String initials(String label) {
        String clean=label.replaceAll("[^\\p{L}\\p{N} ]"," ").trim(); if(clean.isEmpty()) return "•";
        String[] words=clean.split("\\s+"); String first=words[0];
        if(first.length()<=3 && first.equals(first.toUpperCase(Locale.ROOT))) return first;
        return words.length>1?(first.substring(0,1)+words[1].substring(0,1)).toUpperCase(Locale.ROOT):first.substring(0,Math.min(2,first.length())).toUpperCase(Locale.ROOT);
    }
    private String title(String kind) { return kind.equals("tv")?"Telewizja na żywo":kind.equals("movie")?"Filmy":"Seriale"; }
    @Override protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(base); android.content.res.Configuration scale=DisplaySettings.override(base); if(scale!=null) applyOverrideConfiguration(scale); }
    @Override public void onCreate(Bundle state) {
        DisplaySettings.apply(this); super.onCreate(state); getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((was,now) -> TvShelfView.trace("focus "+focusName(was)+" -> "+focusName(now)));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        TvBrowseState location=readLocation(state==null?navigationStore().getString("location",""):state.getString("location",""));
        if(location!=null) applyLocation(location);
        build(); loadAccount();
    }
    private void loadAccount() {
        worker.execute(() -> { try { account=new TvAccountStore(this).load(); } catch(Exception ignored) {} runOnUiThread(() -> { if(!isDestroyed()) { if(!home) load(section); else status.setText(account==null?"Dodaj konto Xtream lub adres listy M3U, aby rozpocząć.":"Biblioteka jest gotowa. Wybierz TV, filmy lub seriale."); } }); });
    }
    private void build() {
        page=column(); page.setBackgroundColor(0); DisplaySettings.pad(page,dp(AndroidLayout.portrait(this)?12:20),dp(8),dp(AndroidLayout.portrait(this)?12:20),dp(8)); // overscan-safe sides; Fire TV "Calibrate display" covers the rest
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text(Ui.APP_NAME.toUpperCase(Locale.ROOT),18,Ui.TEXT); brand.setTypeface(null,Typeface.BOLD); brand.setLetterSpacing(.08f);
        android.text.SpannableString logo=new android.text.SpannableString(brand.getText()); logo.setSpan(new android.text.style.ForegroundColorSpan(Ui.TV_GOLD),0,Math.min(4,logo.length()),0); brand.setText(logo); header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextClock clock=new TextClock(this); clock.setTextColor(Ui.MUTED); clock.setTextSize(18); clock.setFormat24Hour("HH:mm"); clock.setFormat12Hour("HH:mm"); header.addView(clock);
        page.addView(header,new LinearLayout.LayoutParams(-1,dp(30)));
        body=column(); page.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        status=text("",15,Ui.MUTED); status.setSingleLine(true); status.setEllipsize(android.text.TextUtils.TruncateAt.END); status.setPadding(0,dp(6),0,0); page.addView(status,new LinearLayout.LayoutParams(-1,dp(26)));
        // Backdrop layer: navy gradient everywhere; on home a softly enlarged poster from the library glows behind the tiles.
        FrameLayout root=new FrameLayout(this); root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF141A22,0xFF0E1218,0xFF0A0D11})); // LED TVs brighten dark blue a lot, so the base stays near black
        backdrop=new ImageView(this); backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP); backdrop.setAlpha(.14f); root.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
        View scrim=new View(this); scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xB30A0D11,0x8C0A0D11,0xE60A0D11})); root.addView(scrim,new FrameLayout.LayoutParams(-1,-1));
        root.addView(page,new FrameLayout.LayoutParams(-1,-1));
        AndroidLayout.insets(root); setContentView(root); Ui.localize(root);
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
        navigation=column(); navigation.setPadding(0,dp(4),0,dp(4));
        int columns=AndroidLayout.width(this)<600?3:6; LinearLayout row=null;
        String[] labels={"Główna","TV","Filmy","Seriale","Szukaj","Opcje"};
        navigationTabs=new Button[labels.length];
        for(int i=0;i<labels.length;i++) {
            // Ordinary weighted rows avoid GridLayout retaining oversized column bounds
            // across Android configuration changes and pushing tabs beyond the screen.
            if(i%columns==0) { row=new LinearLayout(this); navigation.addView(row,new LinearLayout.LayoutParams(-1,dp(44))); }
            final int index=i; Button tab=button(labels[i],() -> { if(index==0) showHome(); else if(index<=3) open(new String[]{"tv","movie","series"}[index-1]); else if(index==4) { if(home) open("movie"); search(); } else if(home) homeOptions(); else catalogOptions(); });
            tab.setId(View.generateViewId()); tab.setTextSize(15); tab.setSingleLine(true); tab.setPadding(dp(14),0,dp(14),0); tab.setMinHeight(0); tab.setMinimumHeight(0);
            boolean active=home?i==0:i==1 && section.equals("tv") || i==2 && section.equals("movie") || i==3 && section.equals("series");
            android.graphics.drawable.StateListDrawable background=new android.graphics.drawable.StateListDrawable(); background.addState(new int[]{android.R.attr.state_focused},Ui.shape(this,Ui.TEXT,20,0)); background.addState(new int[]{},Ui.shape(this,0,20,0)); tab.setBackground(background);
            tab.setTextColor(new ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},new int[]{TV_BG,active?Ui.TEXT:Ui.MUTED})); tab.setTypeface(null,active?Typeface.BOLD:Typeface.NORMAL);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-1,1); size.rightMargin=dp(4); row.addView(tab,size); navigationTabs[i]=tab;
        }
        body.addView(navigation,new LinearLayout.LayoutParams(-1,-2));
    }
    private static String focusName(View view) { return view==null?"null":view.getClass().getSimpleName()+(view.getContentDescription()!=null?"/"+view.getContentDescription():view instanceof TextView?"/"+((TextView)view).getText():""); }
    private void focusNavigation() { StackTraceElement[] from=new Throwable().getStackTrace(); TvShelfView.trace("focusNavigation from "+(from.length>1?from[1].getMethodName()+":"+from[1].getLineNumber():"?")+(from.length>2?" < "+from[2].getMethodName()+":"+from[2].getLineNumber():"")); if(navigationTabs!=null) navigationTabs[home?0:section.equals("tv")?1:section.equals("movie")?2:3].requestFocus(); }
    private TvShelfView createShelves() {
        return new TvShelfView(this,new TvShelfView.Actions() {
            public void selected(Channel channel,String row,int index) { if(channel!=null) describe(channel); }
            public void open(Channel channel) { openCard(channel); }
            public void more(TvShelfCatalog.Shelf row) { openShelfGrid(row); }
            public void options(Channel channel) { if(!channel.kind().equals("shortcut")) TvActivity.this.options(channel); }
            public void navigation() { focusNavigation(); }
        });
    }
    private void clearCatalogViews() { if(livePreview!=null) { livePreview.destroy(); livePreview=null; } shelves=null; androidCategories=null; emptyState=null; list=null; posters=null; bouquets=null; rail=null; rows=null; covers=null; selectionInfo=null; }
    private void openCard(Channel channel) { if(channel.url().startsWith("section:")) open(channel.url().substring(8)); else if(channel.url().equals("action:resume")) restoreLocation(readLocation(navigationStore().getString("last-browse",""))); else select(channel); }
    private void showHome() {
        busy=false; generation++;
        if(!home && list!=null || !home && shelves!=null) rememberLocation();
        homeRecovery=pendingLocation!=null && pendingLocation.home()?pendingLocation:null;
        home=true; seriesTitle=""; parentLocation=null; catalogGrid=false; pendingLocation=null; ui.removeCallbacks(guideRows); body.removeAllViews(); clearCatalogViews(); addNavigation();
        // No page title on home: the three centred section tiles are the page. The detached heading keeps shared code simple.
        heading=text("",22,Ui.TEXT);
        shelves=createShelves(); body.addView(shelves,new LinearLayout.LayoutParams(-1,0,1)); showHomeRows(true);
        status.setText(account==null?"Dodaj źródło w Opcje → Źródło → Dodaj przez telefon":"Wybierz TV, filmy lub seriale");
        rememberLocation(); loadHomePreviews(); showBackdrop(); ui.postDelayed(this::prefetchLibrary,1200);
    }
    private void showHomeRows(boolean focus) {
        if(!home || shelves==null) return;
        List<Channel> shortcuts=new ArrayList<>(); shortcuts.add(new Channel("Telewizja na żywo","section:tv",countLine("tv"),"","shortcut")); shortcuts.add(new Channel("Filmy","section:movie",countLine("movie"),"","shortcut")); shortcuts.add(new Channel("Seriale","section:series",countLine("series"),"","shortcut"));
        List<TvShelfCatalog.Shelf> rows=new ArrayList<>(); rows.add(new TvShelfCatalog.Shelf("start","Oglądaj",shortcuts,shortcuts.size())); rows.addAll(homePreview);
        TvBrowseState anchor=homeRecovery!=null?homeRecovery:focus?null:location(); boolean ready=homeRecovery==null || rows.stream().anyMatch(row -> row.key().equals(homeRecovery.shelfKey()));
        shelves.submit(rows,anchor,ready && (homeRecovery!=null || focus || shelves.hasFocus())); if(ready) homeRecovery=null;
    }
    /** A tiny poster scaled up full screen reads as a soft blur behind the home tiles (no RenderEffect on Fire OS 7). */
    private void showBackdrop() {
        // Home uses the same calm dark background as every other screen; a poster backdrop looked too bright on LED TVs.
        if(backdrop!=null) backdrop.setVisibility(View.GONE);
    }
    private void loadHomePreviews() {
        final int request=++homeRequest;
        worker.execute(() -> {
            List<TvShelfCatalog.Shelf> preview=new ArrayList<>();
            try { LibraryStore store=new LibraryStore(this); List<Channel> movies=store.previewSection("movie",18),series=TvCatalog.series(store.previewSection("series",240)).items();
                if(!movies.isEmpty()) preview.add(new TvShelfCatalog.Shelf("home-movies","Filmy z Twojej biblioteki",movies,movies.size()));
                if(!series.isEmpty()) preview.add(new TvShelfCatalog.Shelf("home-series","Twoje seriale",series,series.size()));
            } catch(Exception ignored) { }
            runOnUiThread(() -> { if(isDestroyed() || request!=homeRequest) return; homePreview=preview; if(home) { showHomeRows(false); showBackdrop(); } });
        });
    }
    private void homeOptions() {
        new Ui.Dialog(this).setTitle("Opcje").setItems(new String[]{"Konto","Źródło","Odśwież źródło","Ustawienia","O aplikacji","Wyjście"},(d,i) -> {
            switch(i) { case 0: account(); break; case 1: sourceDialog(); break; case 2: refresh(); break; case 3: settings(); break; case 4: info(); break; case 5: finish(); break; }
        }).show();
    }
    private void open(String kind) { if(!home && section.equals(kind)) return; busy=false; if(!home) rememberLocation(); TvBrowseState saved=readLocation(navigationStore().getString("section-"+kind,"")); if(saved!=null) { restoreLocation(saved); return; } generation++; section=kind; group=""; query=""; seriesTitle=""; seriesFolderUrl=""; parentLocation=null; pendingLocation=null; catalogGrid=false; catalogReady=false; onlyFavorites=false; seriesFolders=Collections.emptyMap(); items=new ArrayList<>(); shown=new ArrayList<>(); groups=new ArrayList<>(); home=false; showCatalog(); focusNavigation(); load(kind); }
    /** Built off the UI thread: separator rows are dropped here so opening a section does no per-item regex work on screen. */
    private TvCatalog.Catalog presentation(String kind,List<Channel> channels) { TvCatalog.Catalog catalog=kind.equals("series")?TvCatalog.series(channels):new TvCatalog.Catalog(channels,Collections.emptyMap()); return new TvCatalog.Catalog(Channel.withoutSeparators(catalog.items()),catalog.folders()); }
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
    /** "+5 000 kanałów" style line for the home tiles, from the last time the section was loaded. */
    private String countLine(String kind) {
        int count=navigationStore().getInt("count-"+kind,-1); if(count<=0) return "";
        return "+"+Ui.count(count)+(kind.equals("tv")?" kanałów":kind.equals("movie")?" filmów":" seriali");
    }
    private void rememberCount(String kind,TvCatalog.Catalog catalog) {
        int count=catalog.items().size(); if(navigationStore().getInt("count-"+kind,-1)==count) return;
        navigationStore().edit().putInt("count-"+kind,count).apply();
        runOnUiThread(() -> { if(home && !isDestroyed()) showHomeRows(false); });
    }
    private Object sectionLock(String kind) { return sectionLocks.computeIfAbsent(kind,key -> new Object()); }
    /** Prepares every saved section once per source in the background (decrypt + presentation), never downloading from the provider. */
    private void prefetchLibrary() {
        if(prefetched || account==null || isDestroyed()) return; prefetched=true; final int epoch=sourceEpoch; final TvAccountStore.Account target=account;
        prefetcher.execute(() -> {
            for(String kind:new String[]{"tv","movie","series"}) {
                if(isDestroyed() || epoch!=sourceEpoch) return;
                // Never compete with playback for the CPU: stop while a player is in front and resume on the next home visit.
                if(!foreground) { prefetched=false; return; }
                try {
                    synchronized(sectionLock(kind)) {
                        if(preparedCatalogs.containsKey(kind)) continue;
                        // Only the library already saved on the device is prepared; provider downloads still happen when a section is opened.
                        if(target.type().equals("xtream") && !getPreferences(MODE_PRIVATE).getBoolean("loaded-"+kind,false)) continue;
                        List<Channel> loaded=catalogs.get(kind); if(loaded==null) loaded=new LibraryStore(this).loadSection(kind,new HashSet<>());
                        if(epoch!=sourceEpoch) return;
                        catalogs.put(kind,loaded); TvCatalog.Catalog prepared=presentation(kind,loaded); preparedCatalogs.put(kind,prepared); rememberCount(kind,prepared);
                    }
                } catch(Exception ignored) { /* The section is loaded normally when opened. */ }
            }
        });
    }
    private void load(String kind) {
        TvCatalog.Catalog prepared=preparedCatalogs.get(kind); if(prepared!=null) { busy=false; applyCatalog(prepared); return; }
        if(busy) return; final int request=++generation; busy=true; status.setText("Wczytywanie biblioteki…");
        final int epoch=sourceEpoch;
        worker.execute(() -> { synchronized(sectionLock(kind)) {
            // Waits only while the background prefetch is preparing this very section, then reuses its result.
            TvCatalog.Catalog ready=preparedCatalogs.get(kind);
            if(ready!=null) { runOnUiThread(() -> { if(isDestroyed() || request!=generation || epoch!=sourceEpoch) return; busy=false; applyCatalog(ready); }); return; }
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
                        if(isDestroyed() || epoch!=sourceEpoch) return;
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
                if(epoch==sourceEpoch) catalogs.put(kind,loaded); final TvCatalog.Catalog result=presentation(kind,loaded); if(epoch==sourceEpoch) { preparedCatalogs.put(kind,result); rememberCount(kind,result); }
                runOnUiThread(() -> { if(isDestroyed() || request!=generation || epoch!=sourceEpoch) return; busy=false; applyCatalog(result); });
            } catch(Exception e) { runOnUiThread(() -> { if(!isDestroyed() && request==generation && epoch==sourceEpoch) { busy=false; status.setText("Nie udało się wczytać biblioteki."); error("Nie można pobrać katalogu. Sprawdź połączenie i ważność źródła."); } }); }
        } });
    }
    private void progress(String value) { runOnUiThread(() -> { if(!isDestroyed()) status.setText(value); }); }
    private void showCatalog() {
        home=false; scrolling=false; body.removeAllViews(); if(backdrop!=null) { ImageLoader.get(this).load("",backdrop,dp(18),dp(27)); backdrop.setVisibility(View.GONE); }
        if(shelfLayout()) { showStreamingCatalog(); return; }
        clearCatalogViews(); addNavigation();
        heading=text(title(section),22,Ui.TEXT); heading.setSingleLine(true); heading.setEllipsize(android.text.TextUtils.TruncateAt.END); heading.setTypeface(null,Typeface.BOLD); heading.setPadding(0,dp(10),0,dp(8)); body.addView(heading);
        LinearLayout split=new LinearLayout(this); if(AndroidLayout.portrait(this)) split.setOrientation(LinearLayout.VERTICAL);
        bouquets=new ListView(this); bouquets.setId(View.generateViewId()); bouquets.setDivider(null); bouquets.setSelector(listCursor()); bouquets.setDrawSelectorOnTop(false); bouquets.setItemsCanFocus(false); bouquets.setPadding(0,0,dp(10),0);
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
        // Category grids keep the category list on the left too, so another category is one press away.
        int sidebar=AndroidLayout.portrait(this)?0:section.equals("tv") || !seriesTitle.isEmpty() || catalogGrid?(AndroidLayout.width(this)>=1100?220:140):0;
        if(sidebar==0) bouquets.setVisibility(View.GONE);
        split.addView(bouquets,new LinearLayout.LayoutParams(dp(sidebar),-1));
        if(AndroidLayout.portrait(this)) addAndroidCategories();
        FrameLayout catalog=new FrameLayout(this);
        list=new ListView(this); list.setId(View.generateViewId()); list.setDivider(new ColorDrawable(0)); list.setDividerHeight(dp(6)); list.setItemsCanFocus(false); list.setDrawSelectorOnTop(true); list.setSelector(cardCursor());
        rows=new BaseAdapter() {
            public int getCount() { return shown.size(); } public Object getItem(int p) { return shown.get(p); } public long getItemId(int p) { return p; }
            public View getView(int p,View reused,ViewGroup parent) { return channelRow(shown.get(p),reused,p); }
        };
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView view,int state) { scrolling=state!=SCROLL_STATE_IDLE; scheduleGuides(); }
            public void onScroll(AbsListView view,int first,int count,int total) { scheduleGuides(); }
        }); list.setAdapter(rows); catalog.addView(list,new FrameLayout.LayoutParams(-1,-1));
        posters=new GridView(this); posters.setId(View.generateViewId()); posters.setNumColumns(Math.max(2,(AndroidLayout.width(this)-sidebar-40)/140)); posters.setHorizontalSpacing(dp(12)); posters.setVerticalSpacing(dp(12)); posters.setClipToPadding(false); posters.setPadding(dp(3),dp(3),dp(3),dp(3));
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
        bouquets.setNextFocusUpId(navigationTabs[tab].getId()); list.setNextFocusUpId(navigationTabs[tab].getId()); posters.setNextFocusUpId(navigationTabs[tab].getId());
        for(Button button:navigationTabs) button.setNextFocusDownId(bouquets.getId());
        split.addView(catalog,AndroidLayout.portrait(this)?new LinearLayout.LayoutParams(-1,0,1):new LinearLayout.LayoutParams(0,-1,1));
        if(section.equals("tv")) {
            livePreview=new LivePreview(this,!AndroidLayout.portrait(this),null,this::play,() -> { if(rows!=null) rows.notifyDataSetChanged(); },!AndroidLayout.portrait(this));
            LinearLayout.LayoutParams previewSize=AndroidLayout.portrait(this)?new LinearLayout.LayoutParams(-1,dp(Math.min(280,AndroidLayout.width(this)*9/16+80))):new LinearLayout.LayoutParams(dp(Math.min(livePreview.televisionWidth(),Math.max(240,AndroidLayout.width(this)*36/100))),-1);
            if(AndroidLayout.portrait(this)) { previewSize.bottomMargin=dp(8); split.addView(livePreview.panel,0,previewSize); } else { previewSize.leftMargin=dp(14); split.addView(livePreview.panel,previewSize); }
            list.setNextFocusRightId(livePreview.controls().getId());
            livePreview.controls().setNextFocusLeftId(list.getId());
        }
        body.addView(split,new LinearLayout.LayoutParams(-1,0,1));
        selectionInfo=text("",17,Ui.TEXT); selectionInfo.setMaxLines(2); selectionInfo.setEllipsize(android.text.TextUtils.TruncateAt.END); selectionInfo.setPadding(dp(sidebar+4),dp(6),0,0); selectionInfo.setContentDescription("Wybrana pozycja"); body.addView(selectionInfo,new LinearLayout.LayoutParams(-1,dp(48)));
        if(section.equals("tv")) selectionInfo.setVisibility(View.GONE); // the live preview card already names the selected channel
        filter(); if(bouquets.getVisibility()==View.VISIBLE) bouquets.requestFocus(); else contentView().requestFocus();
    }
    private void showStreamingCatalog() {
        clearCatalogViews(); addNavigation();
        LinearLayout filters=new LinearLayout(this); filters.setGravity(Gravity.CENTER_VERTICAL);
        heading=text(title(section),22,Ui.TEXT); heading.setTypeface(null,Typeface.BOLD); filters.addView(heading,new LinearLayout.LayoutParams(0,dp(42),1)); body.addView(filters);
        // Canal+ style: categories always visible on the left, Netflix style shelves on the right.
        LinearLayout split=new LinearLayout(this); if(AndroidLayout.portrait(this)) split.setOrientation(LinearLayout.VERTICAL);
        rail=new ListView(this); rail.setId(View.generateViewId()); rail.setDivider(null); rail.setSelector(listCursor()); rail.setDrawSelectorOnTop(false); rail.setItemsCanFocus(false); rail.setPadding(0,0,dp(10),0);
        rail.setOnItemClickListener((parent,view,position,id) -> openRail(position));
        // Canal+ style: the category list is always shown on the left. It is focusable only after Left on the first card,
        // so initial focus and returning from a category grid always land on the remembered card.
        rail.setFocusable(false); rail.setBackground(Ui.shape(this,Ui.TV_CARD,12,0)); rail.setPadding(dp(6),dp(6),dp(6),dp(6)); rail.setClipToPadding(false);
        rail.setOnFocusChangeListener((view,focused) -> { if(!focused) view.setFocusable(false); });
        rail.setOnKeyListener((view,key,event) -> {
            if(key!=KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0 && shelves!=null) shelves.focusCurrent();
            return true;
        });
        LinearLayout.LayoutParams railSize=new LinearLayout.LayoutParams(dp(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density>=1100?230:196),-1); railSize.rightMargin=dp(14); railSize.bottomMargin=dp(8); split.addView(rail,railSize); if(AndroidLayout.portrait(this)) { rail.setVisibility(View.GONE); addAndroidCategories(); }
        FrameLayout content=new FrameLayout(this); shelves=createShelves(); content.addView(shelves,new FrameLayout.LayoutParams(-1,-1));
        emptyState=text("Brak pozycji. Wybierz kategorię lub odśwież źródło.",19,Ui.MUTED); emptyState.setGravity(Gravity.CENTER); content.addView(emptyState,new FrameLayout.LayoutParams(-1,-1));
        split.addView(content,AndroidLayout.portrait(this)?new LinearLayout.LayoutParams(-1,0,1):new LinearLayout.LayoutParams(0,-1,1)); body.addView(split,new LinearLayout.LayoutParams(-1,0,1)); bindRail(); filter();
        for(Button button:navigationTabs) button.setOnKeyListener((v,code,event) -> { if(code==KeyEvent.KEYCODE_DPAD_DOWN) { if(event.getAction()==KeyEvent.ACTION_DOWN) shelves.focusCurrent(); return true; } return false; });
    }
    private void addAndroidCategories() {
        androidCategories=Ui.button(this,""); androidCategories.setTextSize(16); androidCategories.setTextColor(Ui.TV_GOLD);
        androidCategories.setGravity(Gravity.CENTER_VERTICAL|Gravity.START); androidCategories.setMaxLines(2); androidCategories.setEllipsize(android.text.TextUtils.TruncateAt.END);
        androidCategories.setMinimumHeight(dp(48)); androidCategories.setPadding(dp(12),dp(8),dp(12),dp(8));
        androidCategories.setBackground(Ui.shape(this,Ui.SURFACE_HIGH,10,Ui.BORDER));
        androidCategories.setOnClickListener(view -> categories());
        body.addView(androidCategories,new LinearLayout.LayoutParams(-1,-2)); updateAndroidCategories();
    }
    private void updateAndroidCategories() {
        if(androidCategories==null) return;
        String label=section.equals("tv")?"Bukiety TV":section.equals("movie")?"Bukiety filmów":"Bukiety seriali";
        String selected=group.isEmpty()?(onlyFavorites?"Ulubione":"Cała oferta"):group;
        androidCategories.setText(label+" ("+groups.size()+") · "+selected+"  ▾");
        androidCategories.setContentDescription(label+": "+selected+". Wybierz bukiet");
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
        TvBrowseState before=location(); Channel selected=livePreview==null?null:livePreview.selected(); boolean loading=busy; int request=generation;
        String transfer=livePreview==null?null:livePreview.handoff(); boolean active=foreground; foreground=false;
        super.onConfigurationChanged(configuration); pendingLocation=before; build(); if(loading) { busy=true; generation=request; }
        if(transfer!=null) { if(livePreview!=null && selected!=null) livePreview.receive(transfer,selected); else PlaybackHandoff.discard(transfer); }
        foreground=active; if(active && livePreview!=null) livePreview.resume(); scheduleGuides();
        if(!home) restoreContent(before);
        AndroidLayout.fitDialog(this,androidBouquetsDialog);
    }

    /** Left category list for films and series: all, my list, then the provider's categories. */
    private void bindRail() {
        updateAndroidCategories();
        if(rail==null) return;
        List<String> labels=new ArrayList<>(); labels.add("Wszystkie"); labels.add("★ Moja lista"); labels.addAll(groups);
        rail.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels) {
            @Override public View getView(int p,View reused,ViewGroup parent) {
                TextView row=(TextView)super.getView(p,reused,parent); boolean active=p==0?!onlyFavorites:p==1 && onlyFavorites;
                row.setLayoutParams(new AbsListView.LayoutParams(-1,-2)); row.setTextSize(16); row.setTextColor(active?Ui.TV_GOLD:Ui.TEXT); row.setTypeface(null,active?Typeface.BOLD:Typeface.NORMAL);
                row.setMinHeight(dp(48)); row.setMaxLines(2); row.setEllipsize(android.text.TextUtils.TruncateAt.END); row.setPadding(dp(16),dp(6),dp(10),dp(6)); row.setBackground(null); return row;
            }
        });
    }
    private void openRail(int position) {
        if(position==0) { if(onlyFavorites) { onlyFavorites=false; filter(); bindRail(); } if(shelves!=null) shelves.focusCurrent(); return; }
        if(position==1) { onlyFavorites=!onlyFavorites; filter(); bindRail(); if(shelves!=null) shelves.focusCurrent(); return; }
        String chosen=groups.get(position-2); openShelfGrid(new TvShelfCatalog.Shelf(chosen,chosen,Collections.emptyList(),0));
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
        new Ui.Dialog(this).setTitle("Opcje katalogu").setItems(actions.toArray(new String[0]),(d,index) -> {
            switch(actions.get(index)) { case "Kategorie": categories(); break; case "☆ Ulubione": onlyFavorites=!onlyFavorites; filter(); break; case "Sortuj": sort(); break; case "Źródło": sourceDialog(); break; case "Konto": account(); break; case "Ustawienia": settings(); break; case "Odśwież źródło": refresh(); break; case "Główna": showHome(); break; case "Widok": chooseView(); break; }
        }).setNegativeButton("Zamknij",null).show();
    }
    private void chooseView() {
        String[] values={"shelves","grid","list"}; String current=navigationStore().getString("view-"+section,"shelves");
        new Ui.Dialog(this).setTitle("Widok biblioteki").setSingleChoiceItems(new String[]{"Rzędy okładek","Siatka plakatów","Lista"},Math.max(0,Arrays.asList(values).indexOf(current)),(d,index) -> {
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
        search.setTag("android-bouquet-search");
        int checked=group.isEmpty()?0:labels.indexOf(group);
        if(androidBouquetsDialog!=null) androidBouquetsDialog.dismiss();
        androidBouquetsDialog=new Ui.Dialog(this).setTitle(section.equals("tv")?"Bukiety TV":"Bukiety · "+title(section)).setView(search).setSingleChoiceItems(adapter,checked,(d,index) -> {
            String selected=adapter.getItem(index); TvBrowseState before=location(); group=selected.equals(labels.get(0))?"":selected; query=""; onlyFavorites=false; d.dismiss();
            if(shelves!=null && !home && !group.isEmpty()) { openShelfGrid(new TvShelfCatalog.Shelf(group,group,Collections.emptyList(),0)); parentLocation=before; }
            else { filter(); if(shelves!=null) shelves.focusCurrent(); else if(!shown.isEmpty()) { contentView().requestFocus(); contentView().setSelection(0); } }
        }).setPositiveButton("Odśwież źródło",(d,w) -> refresh()).setNegativeButton("Zamknij",null).create();
        androidBouquetsDialog.show(); AndroidLayout.fitDialog(this,androidBouquetsDialog);
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
    private View channelRow(Channel channel,View reused,int position) {
        ChannelRow row=reused!=null && reused.getTag() instanceof ChannelRow?(ChannelRow)reused.getTag():null;
        if(row==null) {
            // Channel card: dark logo box, bold name with quality/EPG badges, current programme with a slim progress bar, favourite star.
            row=new ChannelRow(); row.view=new LinearLayout(this); row.view.setGravity(Gravity.CENTER_VERTICAL); row.view.setPadding(dp(10),dp(7),dp(14),dp(7)); row.view.setMinimumHeight(dp(60));
            row.number=text("",13,Ui.MUTED); row.number.setGravity(Gravity.CENTER); row.view.addView(row.number,new LinearLayout.LayoutParams(dp(30),-2));
            FrameLayout logoBox=new FrameLayout(this); logoBox.setBackground(Ui.shape(this,0xFF141821,8,0)); logoBox.setPadding(dp(5),dp(4),dp(5),dp(4));
            row.logo=new ImageView(this); row.logo.setScaleType(ImageView.ScaleType.FIT_CENTER); logoBox.addView(row.logo,new FrameLayout.LayoutParams(-1,-1)); row.view.addView(logoBox,new LinearLayout.LayoutParams(dp(62),dp(46)));
            LinearLayout copy=column(); copy.setPadding(dp(12),0,dp(8),0);
            LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
            row.name=text("",17,Ui.TEXT); row.name.setTypeface(null,Typeface.BOLD); row.name.setSingleLine(true); row.name.setEllipsize(android.text.TextUtils.TruncateAt.END); head.addView(row.name,new LinearLayout.LayoutParams(0,-2,1));
            row.badges=text("",10,0xFF10141A); row.badges.setTypeface(null,Typeface.BOLD); row.badges.setLetterSpacing(.06f); row.badges.setPadding(dp(5),dp(1),dp(5),dp(1)); row.badges.setBackground(Ui.shape(this,0xD9E6E8EC,3,0)); LinearLayout.LayoutParams chip=new LinearLayout.LayoutParams(-2,-2); chip.leftMargin=dp(8); head.addView(row.badges,chip);
            copy.addView(head,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout line=new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL); line.setPadding(0,dp(3),0,0);
            row.epg=text("",14,Ui.MUTED); row.epg.setSingleLine(true); row.epg.setEllipsize(android.text.TextUtils.TruncateAt.END); line.addView(row.epg,new LinearLayout.LayoutParams(0,-2,1));
            row.progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); row.progress.setMax(1000); row.progress.setProgressTintList(ColorStateList.valueOf(Ui.TV_GOLD)); row.progress.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.TV_LINE));
            LinearLayout.LayoutParams bar=new LinearLayout.LayoutParams(dp(44),dp(4)); bar.leftMargin=dp(10); line.addView(row.progress,bar);
            copy.addView(line,new LinearLayout.LayoutParams(-1,-2)); row.view.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            row.star=text("",20,Ui.TV_GOLD); row.view.addView(row.star); row.view.setTag(row);
        }
        row.channel=channel; row.requested=false; row.name.setText(channel.name()); row.star.setText(isFavorite(channel)?"★":"");
        row.number.setText(channel.kind().equals("tv") && position>=0?String.valueOf(position+1):""); row.number.setVisibility(channel.kind().equals("tv")?View.VISIBLE:View.GONE);
        boolean playing=livePreview!=null && channel.url().equals(livePreview.selectedUrl());
        // Transparent rows let the teal list cursor show through; the channel in the preview gets a faint tint and amber name.
        row.view.setBackground(rowBackground(playing)); row.name.setTextColor(playing?Ui.TV_GOLD:Ui.TEXT);
        String badge=badges(channel); row.badges.setText(badge); row.badges.setVisibility(badge.isEmpty()?View.GONE:View.VISIBLE);
        if(channel.kind().equals("tv")) showGuide(row,GuideRepository.get(this).peek(channel,false)); else { row.epg.setText(channel.group()); row.epg.setVisibility(View.VISIBLE); }
        ImageLoader.get(this).load(channel.logo(),row.logo,dp(52),dp(38)); scheduleGuides(); return row.view;
    }
    private void showGuide(ChannelRow row,GuideRepository.Data data) {
        String current=data==null || data.programs().isEmpty()?"":GuideRepository.summary(data,System.currentTimeMillis()/1000).split("\\n",2)[0];
        if(current.startsWith("Brak aktualnego")) current="";
        current=current.replaceFirst("^\\s*\\d{1,2}:\\d{2}\\s*[–-]\\s*\\d{1,2}:\\d{2}\\s+","");
        row.epg.setText(current); row.epg.setVisibility(current.isEmpty()?View.GONE:View.VISIBLE);
        long now=System.currentTimeMillis()/1000; Guide.Program playing=null;
        if(data!=null) for(Guide.Program program:data.programs()) if(program.current(now)) playing=program;
        if(row.progress!=null) { row.progress.setVisibility(playing==null?View.GONE:View.VISIBLE); if(playing!=null) row.progress.setProgress((int)Math.max(0,Math.min(1000,(now-playing.start())*1000/Math.max(1,playing.end()-playing.start())))); }
    }
    private android.graphics.drawable.Drawable rowBackground(boolean playing) { return Ui.shape(this,playing?0xFF232A36:0xFF181C24,10,0); }
    /** Quality and service badges shown beside the channel name, e.g. "FHD · EPG". */
    private static String badges(Channel channel) {
        String name=channel.name().toUpperCase(Locale.ROOT); List<String> parts=new ArrayList<>();
        if(name.matches(".*\\b(4K|UHD)\\b.*")) parts.add("4K"); else if(name.matches(".*\\bFHD\\b.*")) parts.add("FHD"); else if(name.matches(".*\\bHD\\b.*")) parts.add("HD");
        if(!channel.epgId().isEmpty()) parts.add("EPG"); if(channel.archiveDays()>0) parts.add("ARCH");
        return String.join(" · ",parts);
    }
    /** Selected channel card: lighter overlay with an accent bar on the left edge (drawn above the opaque card). */
    private android.graphics.drawable.Drawable cardCursor() {
        GradientDrawable light=Ui.shape(this,0x1AFFFFFF,10,0x33FFFFFF); GradientDrawable edge=Ui.shape(this,Ui.TV_GOLD,2,0);
        android.graphics.drawable.LayerDrawable focused=new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{light,edge});
        focused.setLayerWidth(1,dp(4)); focused.setLayerGravity(1,Gravity.START|Gravity.FILL_VERTICAL);
        StateListDrawable states=new StateListDrawable(); states.addState(new int[]{android.R.attr.state_focused},focused); states.addState(new int[]{android.R.attr.state_pressed},focused); states.addState(new int[]{},new ColorDrawable(0)); return states;
    }
    /** Teal list cursor with an amber edge (drawn below the transparent rows), as in the receiver skin. */
    private android.graphics.drawable.Drawable listCursor() {
        GradientDrawable fill=Ui.shape(this,Ui.TV_SELECT,6,0); GradientDrawable edge=Ui.shape(this,Ui.TV_GOLD,2,0);
        android.graphics.drawable.LayerDrawable focused=new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{fill,edge});
        focused.setLayerInset(1,0,0,0,0); focused.setLayerWidth(1,dp(4)); focused.setLayerGravity(1,Gravity.START|Gravity.FILL_VERTICAL);
        StateListDrawable states=new StateListDrawable(); states.addState(new int[]{android.R.attr.state_focused},focused); states.addState(new int[]{android.R.attr.state_pressed},focused); states.addState(new int[]{},new ColorDrawable(0)); return states;
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
            // The whole poster stays visible; once artwork arrives it covers the title placeholder behind its letterbox.
            card.image=new ImageView(this) { @Override public void setImageDrawable(android.graphics.drawable.Drawable drawable) { super.setImageDrawable(drawable); setBackgroundColor(drawable==null?0:Ui.SURFACE_HIGH); } };
            card.image.setScaleType(ImageView.ScaleType.FIT_CENTER); card.artwork.addView(card.image,new FrameLayout.LayoutParams(-1,-1));
            card.badge=text("★",16,Ui.STAR); card.badge.setGravity(Gravity.CENTER); card.badge.setBackground(Ui.shape(this,0xCC0A0D14,14,0));
            FrameLayout.LayoutParams badgeSize=new FrameLayout.LayoutParams(dp(30),dp(30),Gravity.TOP|Gravity.END); badgeSize.topMargin=dp(6); badgeSize.rightMargin=dp(6); card.artwork.addView(card.badge,badgeSize);
            card.name=text("",16,Ui.TEXT); card.name.setMaxLines(2); card.name.setEllipsize(android.text.TextUtils.TruncateAt.END); card.name.setPadding(dp(4),dp(6),dp(4),0); card.view.addView(card.name,new LinearLayout.LayoutParams(-1,dp(52))); card.view.setTag(card);
        }
        if(card.artwork.getLayoutParams().height!=height) card.artwork.setLayoutParams(new LinearLayout.LayoutParams(-1,height));
        ImageLoader.get(this).load(channel.logo(),card.image,width,height); card.placeholder.setText(channel.name()); card.badge.setVisibility(isFavorite(channel)?View.VISIBLE:View.GONE); card.name.setText(channel.name()); card.view.setContentDescription(channel.name()); return card.view;
    }
    private void rebuildGroups() {
        String sortMode=getPreferences(MODE_PRIVATE).getString("sort-"+section,"provider");
        if(countsSource!=items || !sortMode.equals(countsMode)) { countsCache=BouquetOrder.counts(items,sortMode); countsSource=items; countsMode=sortMode; } // reused while browsing bouquets
        Map<String,Integer> counts=countsCache; groups=new ArrayList<>(counts.keySet());
        List<String> labels=new ArrayList<>(); labels.add(seriesTitle.isEmpty()?"Cała oferta":"Wszystkie odcinki"); labels.addAll(groups);
        if(shelves!=null) { bindRail(); filter(); return; }
        rebinding=true;
        final int total=items.size();
        // Bouquet rail as in the reference: round badge with the bouquet's initials, name, and the channel count under the active one.
        bouquets.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels) {
            @Override public View getView(int p,View reused,ViewGroup parent) {
                LinearLayout row=reused instanceof LinearLayout?(LinearLayout)reused:null; TextView badge,name,count;
                if(row==null) {
                    row=new LinearLayout(TvActivity.this); row.setGravity(Gravity.CENTER_VERTICAL); row.setLayoutParams(new AbsListView.LayoutParams(-1,-2)); row.setMinimumHeight(dp(50)); row.setPadding(dp(10),dp(6),dp(10),dp(6));
                    badge=text("",11,Ui.TEXT); badge.setGravity(Gravity.CENTER); badge.setTypeface(null,Typeface.BOLD); row.addView(badge,new LinearLayout.LayoutParams(dp(32),dp(32)));
                    LinearLayout copy=column(); copy.setPadding(dp(12),0,0,0); name=text("",16,Ui.TEXT); name.setMaxLines(2); name.setEllipsize(android.text.TextUtils.TruncateAt.END); copy.addView(name);
                    count=text("",12,Ui.MUTED); copy.addView(count); row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
                } else { badge=(TextView)row.getChildAt(0); LinearLayout copy=(LinearLayout)row.getChildAt(1); name=(TextView)copy.getChildAt(0); count=(TextView)copy.getChildAt(1); }
                String label=getItem(p); boolean active=p==0?group.isEmpty():group.equals(groups.get(p-1));
                badge.setText(initials(label)); badge.setTextColor(active?0xFF10141A:Ui.TEXT); badge.setBackground(Ui.shape(TvActivity.this,active?Ui.TV_GOLD:0x1FFFFFFF,16,0));
                name.setText(label); name.setTextColor(active?Ui.TEXT:0xB3FFFFFF); name.setTypeface(null,active?Typeface.BOLD:Typeface.NORMAL);
                Integer size=p==0?Integer.valueOf(total):counts.get(groups.get(p-1)); count.setText(size==null?"":Ui.count(size)+(section.equals("tv")?" kanałów":" pozycji")); count.setVisibility(active && size!=null?View.VISIBLE:View.GONE);
                return row;
            }
        }); bouquets.setSelection(group.isEmpty()?0:Math.max(0,groups.indexOf(group)+1)); updateAndroidCategories(); filter(); if(pendingLocation==null) ui.post(() -> rebinding=false);
    }
    private void filter() {
        if(home) return;
        updateAndroidCategories();
        TvBrowseState anchor=pendingLocation!=null?pendingLocation:location(); boolean firstFocus=shelves!=null && shelves.selected()==null;
        shown=new ArrayList<>(); String[] tokens=TitleSearch.tokens(query); Map<String,Boolean> matchingGroups=new HashMap<>();
        for(Channel channel:items) if((group.isEmpty() || group.equals(channel.group())) && (!onlyFavorites || isFavorite(channel)) && (tokens.length==0 || matchingGroups.computeIfAbsent(channel.group(),value -> TitleSearch.score(TitleSearch.normalize(value),tokens)>=0) || TitleSearch.score(TitleSearch.normalize(channel.name()),tokens)>=0)) shown.add(channel);
        if(shelves!=null) {
            List<TvShelfCatalog.Shelf> data=TvShelfCatalog.build(shown,getPreferences(MODE_PRIVATE).getString("sort-"+section,"provider"));
            boolean focus=catalogReady && (firstFocus || anchor.contentFocus()); shelves.submit(data,anchor,focus); if(!focus && pendingLocation!=null) focusNavigation(); if(catalogReady) pendingLocation=null;
            if(emptyState!=null) emptyState.setVisibility(catalogReady && shown.isEmpty()?View.VISIBLE:View.GONE);
            heading.setText(title(section)+(onlyFavorites?" · Moja lista":"")+(group.isEmpty()?"":" · "+group));
            status.setText(busy?"Wczytywanie biblioteki…":Ui.count(shown.size())+" pozycji  ·  Dotknij: szczegóły  ·  Przytrzymaj: opcje"); return;
        }
        if(list==null) return;
        if(rows!=null) rows.notifyDataSetChanged(); if(covers!=null) covers.notifyDataSetChanged();
        boolean television=listMode(); list.setVisibility(television?View.VISIBLE:View.GONE); posters.setVisibility(television?View.GONE:View.VISIBLE);
        if(livePreview!=null) { livePreview.show(shown,true,group+"|"+query+"|"+onlyFavorites); if(foreground) livePreview.resume(); }
        heading.setText(!seriesTitle.isEmpty()?seriesTitle:title(section)+(onlyFavorites?" · Ulubione":"")+(group.isEmpty()?"":" · "+group));
        if(bouquets.getVisibility()==View.VISIBLE) bouquets.setNextFocusRightId(contentView().getId());
        if(selectionInfo!=null) { if(shown.isEmpty()) selectionInfo.setText(""); else describe(shown.get(Math.max(0,Math.min(shown.size()-1,contentView().getSelectedItemPosition())))); }
        status.setText(busy?"Wczytywanie biblioteki…":shown.isEmpty()?"Brak pozycji. Zmień kategorię lub dodaj źródło.":Ui.count(shown.size())+(livePreview!=null?" kanałów · Dotknij kanału: podgląd / pełny ekran":" pozycji · ↑↓ wybór · OK otwórz · Menu opcje · Wstecz nawigacja"));
    }
    private Channel selected() { if(home || shown.isEmpty()) return null; if(shelves!=null) return shelves.selected(); if(list==null || posters==null) return null; int index=contentView().getSelectedItemPosition(); return index>=0 && index<shown.size()?shown.get(index):null; }
    private void options(Channel channel) {
        List<String> actions=new ArrayList<>(); if(channel.kind().equals("movie")) actions.add("Szczegóły filmu"); actions.add(channel.kind().equals("series")?"Odcinki":"Odtwórz"); if(channel.kind().equals("tv")) actions.add("Program TV i archiwum"); actions.add(isFavorite(channel)?"Usuń z ulubionych":"Dodaj do ulubionych");
        new Ui.Dialog(this).setTitle(channel.name()).setItems(actions.toArray(new String[0]),(d,index) -> { String action=actions.get(index); if(action.contains("ulubionych")) favorite(channel); else if(action.startsWith("Program")) GuideDialog.open(this,channel,worker,this::play); else if(action.equals("Odtwórz")) play(channel); else select(channel); }).setNegativeButton("Zamknij",null).show();
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
    private void search() { EditText field=new EditText(this); field.setSingleLine(true); field.setText(query); new Ui.Dialog(this).setTitle("Szukaj w sekcji").setView(field).setPositiveButton("Szukaj",(d,w) -> { query=field.getText().toString().trim(); group=""; filter(); }).setNegativeButton("Anuluj",null).show(); }
    private void sort() { new Ui.Dialog(this).setTitle("Kolejność kategorii").setItems(BouquetOrder.LABELS,(d,index) -> { getPreferences(MODE_PRIVATE).edit().putString("sort-"+section,BouquetOrder.MODES[index]).apply(); rebuildGroups(); }).show(); }
    private void account() {
        if(loadingAccount) return; loadingAccount=true;
        worker.execute(() -> {
            try {
                List<Channel> channels=new LibraryStore(this).loadSection("tv",new HashSet<>()); Set<String> bouquets=new HashSet<>(); for(Channel channel:channels) bouquets.add(channel.group());
                runOnUiThread(() -> {
                    loadingAccount=false; if(isFinishing() || isDestroyed()) return;
                    okhttp3.HttpUrl address=account==null?null:okhttp3.HttpUrl.parse(account.server()==null?"":account.server()); String host=account==null?"Nie dodano źródła":address==null?"Nieprawidłowy zapis źródła":address.host();
                    new Ui.Dialog(this).setTitle("Konto i biblioteka").setMessage("Źródło: "+host+"\nKanały TV: "+Ui.count(channels.size())+"\nBukiety TV: "+Ui.count(bouquets.size())+"\n\nDane dostępu są przechowywane w zaszyfrowanej bibliotece.").setPositiveButton("Zmień źródło",(d,w) -> sourceDialog()).setNegativeButton("Zamknij",null).show();
                });
            } catch(Exception failure) { runOnUiThread(() -> { loadingAccount=false; error("Nie można odczytać konta i biblioteki."); }); }
        });
    }
    private void settings() {
        new Ui.Dialog(this).setTitle("Ustawienia").setItems(new String[]{"Źródło: dodaj lub zmień","Źródło: odśwież listę","Połączenie internetowe (bufor)","Program TV (EPG): własny link XMLTV","Ekran i wygląd","O aplikacji"},(d,index) -> {
            if(index==0) sourceDialog(); else if(index==1) refresh(); else if(index==2) PlaybackSettings.choose(this,() -> {}); else if(index==3) EpgSettings.choose(this,() -> GuideRepository.get(this).invalidateFailures()); else if(index==4) DisplaySettings.choose(this); else info();
        }).setNegativeButton("Zamknij",null).show();
    }
    private void info() { new Ui.Dialog(this).setTitle(Ui.APP_NAME+" "+BuildConfig.VERSION_NAME).setMessage("Telewizja na żywo, filmy i seriale z Twojego źródła IPTV.\nProgram TV (EPG) i archiwum zależą od dostawcy.\n\nOK: wybierz lub odtwórz\nStrzałki: nawigacja\nMenu / przytrzymanie OK: opcje pozycji\nPlay/Pause: sterowanie odtwarzaniem").setPositiveButton("OK",null).show(); }
    private void error(String message) { if(!isFinishing() && !isDestroyed()) new Ui.Dialog(this).setTitle(Ui.APP_NAME).setMessage(message).setPositiveButton("OK",null).show(); }
    private void sourceDialog() {
        if(busy || refreshing) return;
        new Ui.Dialog(this).setTitle("Dodaj źródło").setItems(new String[]{"Dodaj przez telefon · ta sama sieć Wi-Fi","Konto Xtream Codes · wpisz pilotem","Adres listy M3U · wpisz pilotem"},(d,index) -> {
            if(index==0) { if(phoneSetup==null) phoneSetup=new TvPhoneSetup(this); phoneSetup.show(this::importSource); }
            else sourceForm(index==1);
        }).setNegativeButton("Anuluj",null).show();
    }
    private void sourceForm(boolean xtream) {
        LinearLayout fields=column(); fields.setPadding(dp(24),dp(12),dp(24),0);
        EditText address=new EditText(this); address.setHint(xtream?"Serwer http:// lub https://":"Adres listy M3U"); address.setSingleLine(true); address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI); fields.addView(address);
        EditText user=new EditText(this),password=new EditText(this); user.setHint("Login"); user.setSingleLine(true); password.setHint("Hasło"); password.setSingleLine(true); password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if(xtream) { fields.addView(user); fields.addView(password); }
        AlertDialog dialog=new Ui.Dialog(this).setTitle(xtream?"Konto Xtream Codes":"Lista M3U").setView(fields).setPositiveButton("Połącz",null).setNegativeButton("Anuluj",null).create(); dialog.show();
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
                    TvBrowseState current=location(); account=next; catalogs.clear(); preparedCatalogs.clear(); prefetched=false; homePreview=Collections.emptyList(); homeRequest++; busy=false; refreshing=false;
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
            List<Channel> complete=source.liveInProviderOrder(expected.server(),current);
            if(complete.isEmpty() || epoch!=sourceEpoch || isDestroyed()) return;
            store.updateSection("tv",complete);
            runOnUiThread(() -> { if(isDestroyed() || epoch!=sourceEpoch || !expected.equals(account)) return; catalogs.remove("tv"); preparedCatalogs.remove("tv"); if(foreground && !home && section.equals("tv")) { TvBrowseState before=location(); restoreLocation(before); status.setText("Uzupełniono brakujące bukiety TV z panelu dostawcy."); } });
        } catch(Exception ignored) { /* A valid M3U remains usable when the optional panel is unavailable. */ }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code=event.getKeyCode();
        // The category list is reached only on purpose: Left on the first card of a shelf. It never takes initial focus,
        // so returning from a category grid restores the previously selected card.
        if(code==KeyEvent.KEYCODE_DPAD_LEFT && rail!=null && shelves!=null && shelves.hasFocus() && shelves.index()==0) {
            if(event.getAction()==KeyEvent.ACTION_DOWN) { rail.setFocusable(true); rail.requestFocus(); }
            return true;
        }
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
        if(rail!=null && rail.hasFocus() && shelves!=null) { shelves.focusCurrent(); return; } // Back closes the category list
        if(!home && parentLocation!=null) { restoreLocation(parentLocation); return; }
        if(livePreview!=null && livePreview.panel.hasFocus()) { list.requestFocus(); return; }
        if(!home && list!=null && contentView().hasFocus() && bouquets.getVisibility()==View.VISIBLE) { bouquets.setSelection(group.isEmpty()?0:Math.max(0,groups.indexOf(group)+1)); bouquets.requestFocus(); return; }
        if(!home) {
            rememberLocation();
            new Ui.Dialog(this).setTitle("Nawigacja").setItems(new String[]{"Wróć do przeglądania","Główna","TV","Filmy","Seriale"},(d,index) -> { if(index==1) showHome(); else if(index>=2) open(new String[]{"tv","movie","series"}[index-2]); }).setNegativeButton("Zamknij",null).show(); return;
        }
        new Ui.Dialog(this).setTitle("Zamknąć TV Player?").setPositiveButton("Wyjście",(d,w) -> finish()).setNegativeButton("Zostań",null).show();
    }
    @Override protected void onResume() {
        super.onResume(); foreground=true; scheduleGuides(); ui.postDelayed(this::prefetchLibrary,2500); // resumes background preparation paused for playback
        ImageLoader.get(this).resume(page);
        if(livePreview!=null) livePreview.resume();
        if(!home && !busy && pausedLocation!=null) { pendingLocation=pausedLocation; if(shelves!=null) filter(); else restoreContent(pendingLocation); } pausedLocation=null;
        worker.execute(() -> { try { Set<String> saved=new LibraryStore(this).loadFavorites(); runOnUiThread(() -> { if(!isDestroyed() && !favorites.equals(saved)) { favorites.clear(); favorites.addAll(saved); if(!home) filter(); } }); } catch(Exception ignored) {} });
    }
    @Override protected void onPause() { pausedLocation=location(); rememberLocation(); foreground=false; ui.removeCallbacks(guideRows); if(livePreview!=null) livePreview.pause(); ImageLoader.get(this).pause(page); if(phoneSetup!=null) phoneSetup.close(); super.onPause(); }
    @Override protected void onDestroy() { if(androidBouquetsDialog!=null) androidBouquetsDialog.dismiss(); generation++; homeRequest++; sourceEpoch++; ui.removeCallbacksAndMessages(null); if(livePreview!=null) livePreview.destroy(); source.cancel(); worker.shutdown(); prefetcher.shutdown(); sourceWorker.shutdownNow(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putString("location",json.toJson(pendingLocation!=null?pendingLocation:location())); super.onSaveInstanceState(state); }
}
