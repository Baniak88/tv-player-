package pl.tvplayer;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import android.text.*;
import java.util.*;
import java.util.concurrent.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class MainActivity extends Activity {
    private static final int FILE_REQUEST = 41, LIVE_FULLSCREEN=42;
    private LivePreview preview;
    private LinearLayout liveArea,dockBar,liveListColumn;
    private LinearLayout screenRoot;
    private boolean wideLayout;
    private String fullscreenToken;
    private static final java.util.regex.Pattern RECENT=java.util.regex.Pattern.compile(".*(ostatnio|nowo|nowości|nowosci|new|recent|latest|2026|2025).*",java.util.regex.Pattern.CASE_INSENSITIVE|java.util.regex.Pattern.UNICODE_CASE);
    private static final String ALL = "Wszystkie kategorie";
    /** Netflix-style rows built from the library itself; never equal to a provider bouquet name. */
    private static final String CONTINUE="\u0001continue", MYLIST="\u0001mylist", RECENTLY="\u0001recent";
    private final ExecutorService indexer = Executors.newSingleThreadExecutor();
    private LinkedHashMap<String,Long> continuing=new LinkedHashMap<>();
    private boolean gridMode=true;
    private Button clearSearch;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService network = Executors.newFixedThreadPool(2);
    private final Set<String> orderRequested=new HashSet<>();
    private int catalogGeneration;
    private boolean syncingOrder;
    private List<Channel> channels = new ArrayList<>(), shown = new ArrayList<>();
    private final Set<String> loadedSections = new HashSet<>();
    private final Map<String,List<Channel>> sectionIndex = new HashMap<>();
    /** Lower-case names per section, computed once so typing in search stays smooth. */
    private final Map<String,String[]> searchKeys = new HashMap<>();
    private String loadingMessage = "Wczytywanie…";
    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable searchTask = () -> filter(true);
    private final Runnable epgTick=new Runnable() {
        public void run() { if(rows!=null && section.equals("tv") && list.getVisibility()==View.VISIBLE) rows.notifyDataSetChanged(); searchHandler.postDelayed(this,60000); }
    };
    private final Set<String> favorites = new HashSet<>();
    private LibraryStore store;
    private TextView status;
    private EditText search;
    private GridView bouquets;
    private LinearLayout crumb;
    private TextView crumbTitle;
    private Button sortButton;
    private LinearLayout emptyPanel;
    private boolean showingBouquets=true;
    private List<String> bouquetNames=new ArrayList<>();
    private final Map<String,Integer> bouquetCounts=new LinkedHashMap<>();
    private ListView list;
    private BaseAdapter rows;
    private Button favoriteButton;
    private boolean onlyFavorites, busy;
    private TextView emptyText;
    private ProgressBar progress;
    private Button emptyAction;
    private Button libraryButton;
    private String section = "tv";
    private final java.util.Map<String, Button> sectionButtons = new java.util.HashMap<>();
    private String selectedGroup = ALL;
    private ListView shelves;
    private BaseAdapter shelfAdapter, posterAdapter;
    private GridView posters;
    private List<String> shelfNames=new ArrayList<>();
    private final Map<String,List<Channel>> shelfItems=new HashMap<>();
    private FrameLayout hero;
    private ImageView heroImage;
    private TextView heroTitle, heroMeta;
    private Button heroPlay, heroSave;
    private Channel heroItem;
    private LinearLayout coverHint;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store = new LibraryStore(this);
        String requested=getIntent().getStringExtra("section");
        if(Arrays.asList("tv","movie","series").contains(requested)) section=requested;
        String requestedGroup=getIntent().getStringExtra("bouquet");
        if(requestedGroup!=null) { selectedGroup=requestedGroup; showingBouquets=false; }
        if(state!=null) {
            section=state.getString("section",section); selectedGroup=state.getString("bouquet",selectedGroup);
            showingBouquets=state.getBoolean("showingBouquets",showingBouquets); onlyFavorites=state.getBoolean("onlyFavorites",false);
        }
        boolean wide=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE; wideLayout=wide;
        LinearLayout root=column(); screenRoot=root; root.setFocusableInTouchMode(true); root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v,insets) -> {
            v.setPadding(dp(16)+insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),dp(16)+insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets.consumeSystemWindowInsets();
        });
        // Top bar: back · section switch · sort. No duplicated headings.
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(0,dp(wide?4:10),0,dp(10));
        Button home=Ui.button(this,"‹"); home.setTextSize(26); Ui.ghost(home,Ui.TEXT); home.setContentDescription("Wróć do ekranu głównego");
        top.addView(home,new LinearLayout.LayoutParams(dp(48),dp(48)));
        home.setOnClickListener(v -> { startActivity(new Intent(this,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish(); });
        LinearLayout tabs=new LinearLayout(this); tabs.setPadding(dp(3),dp(3),dp(3),dp(3)); tabs.setBackground(Ui.shape(this,Ui.SURFACE,12,Ui.BORDER));
        String[][] sections={{"tv","TV"},{"movie","Filmy"},{"series","Seriale"}};
        for(String[] entry:sections) {
            Button tab=Ui.button(this,entry[1]); sectionButtons.put(entry[0],tab);
            tab.setMinHeight(0); tab.setMinimumHeight(0); tab.setPadding(dp(4),0,dp(4),0);
            tabs.addView(tab,new LinearLayout.LayoutParams(0,-1,1));
            Ui.style(tab,entry[0].equals(section));
            tab.setOnClickListener(v -> { if(busy || entry[0].equals(section) && showingBouquets && !onlyFavorites) return; section=entry[0]; onlyFavorites=false; showingBouquets=true; selectedGroup=ALL;
                Ui.style(libraryButton,true); Ui.style(favoriteButton,false);
                for(java.util.Map.Entry<String,Button> item:sectionButtons.entrySet()) Ui.style(item.getValue(),item.getKey().equals(section));
                updateSearchHint(); search.setText(""); if(loadedSections.contains(section)) refreshGroups(); else loadCachedSection(section); });
        }
        LinearLayout.LayoutParams tabsSize=new LinearLayout.LayoutParams(0,dp(54),1); tabsSize.leftMargin=dp(4); tabsSize.rightMargin=dp(8);
        top.addView(tabs,tabsSize);
        sortButton=Ui.button(this,"⇅"); sortButton.setTextSize(20); Ui.ghost(sortButton,Ui.MUTED); sortButton.setContentDescription("Sortuj bukiety");
        top.addView(sortButton,new LinearLayout.LayoutParams(dp(48),dp(48))); sortButton.setOnClickListener(v -> chooseSort());
        root.addView(top);
        search=input("",false); updateSearchHint(); search.setPadding(dp(40),0,dp(44),0);
        // Search field with a magnifier and a clear button, like streaming apps.
        FrameLayout searchBox=new FrameLayout(this);
        searchBox.addView(search,new FrameLayout.LayoutParams(-1,-1));
        TextView glass=Ui.text(this,"⌕",20,Ui.FAINT); glass.setGravity(Gravity.CENTER); glass.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        searchBox.addView(glass,new FrameLayout.LayoutParams(dp(40),-1,Gravity.START|Gravity.CENTER_VERTICAL));
        clearSearch=Ui.button(this,"✕"); Ui.ghost(clearSearch,Ui.MUTED); clearSearch.setTextSize(16); clearSearch.setContentDescription("Wyczyść wyszukiwanie"); clearSearch.setVisibility(View.GONE);
        clearSearch.setOnClickListener(v -> { search.setText(""); search.requestFocus(); });
        searchBox.addView(clearSearch,new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.END|Gravity.CENTER_VERTICAL));
        LinearLayout.LayoutParams searchSize=new LinearLayout.LayoutParams(-1,dp(48));
        if(wide) { searchSize=new LinearLayout.LayoutParams(dp(260),dp(48)); searchSize.leftMargin=dp(4); top.addView(searchBox,top.getChildCount()-1,searchSize); }
        else root.addView(searchBox,searchSize);
        // Breadcrumb shown only inside a bouquet or favourites; otherwise a quiet summary line.
        crumb=new LinearLayout(this); crumb.setGravity(Gravity.CENTER_VERTICAL); crumb.setPadding(0,dp(8),0,dp(4));
        Button up=Ui.button(this,"‹ Bukiety"); Ui.ghost(up,Ui.MUTED); up.setContentDescription("Wróć do listy bukietów");
        up.setOnClickListener(v -> backToBouquets());
        crumb.addView(up,new LinearLayout.LayoutParams(-2,dp(48)));
        crumbTitle=Ui.text(this,"",17,Ui.TEXT); crumbTitle.setTypeface(null,Typeface.BOLD); crumbTitle.setMaxLines(1); crumbTitle.setEllipsize(TextUtils.TruncateAt.END);
        crumbTitle.setPadding(dp(4),0,0,0); crumb.addView(crumbTitle,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(crumb);
        status=Ui.text(this,"",12,Ui.FAINT); status.setPadding(dp(2),dp(10),0,dp(10)); root.addView(status);
        FrameLayout content=new FrameLayout(this);
        list=new ListView(this); list.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); list.setDividerHeight(dp(6)); list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); list.setClipToPadding(false);
        list.setFastScrollEnabled(true);
        liveArea=new LinearLayout(this); liveArea.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        preview=new LivePreview(this,wide,state,this::play,() -> { if(rows!=null) rows.notifyDataSetChanged(); });
        if(wide) {
            liveListColumn=column(); liveListColumn.addView(list,new LinearLayout.LayoutParams(-1,0,1));
            liveArea.addView(liveListColumn,new LinearLayout.LayoutParams(0,-1,0.43f));
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-1,0.57f); size.leftMargin=dp(12); liveArea.addView(preview.panel,size);
        } else { liveArea.addView(preview.panel,new LinearLayout.LayoutParams(-1,-2)); LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,0,1); size.topMargin=dp(10); liveArea.addView(list,size); }
        liveArea.setVisibility(View.GONE); content.addView(liveArea,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout empty=column(); emptyPanel=empty; empty.setGravity(Gravity.CENTER); empty.setPadding(dp(24),dp(12),dp(24),dp(12));
        progress=new ProgressBar(this); progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.MUTED)); empty.addView(progress,new LinearLayout.LayoutParams(dp(28),dp(28)));
        emptyText=Ui.text(this,"",15,Ui.MUTED); emptyText.setGravity(Gravity.CENTER); emptyText.setLineSpacing(0,1.2f); emptyText.setPadding(0,dp(14),0,dp(18)); empty.addView(emptyText);
        emptyAction=Ui.button(this,"＋ Dodaj źródło"); Ui.primary(emptyAction); emptyAction.setTextSize(15); emptyAction.setOnClickListener(v -> sourceMenu());
        empty.addView(emptyAction,new LinearLayout.LayoutParams(dp(220),dp(48)));
        content.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        bouquets=new GridView(this); bouquets.setNumColumns(wide?4:2); bouquets.setHorizontalSpacing(dp(8)); bouquets.setVerticalSpacing(dp(8)); bouquets.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        bouquets.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); bouquets.setClipToPadding(false); bouquets.setFastScrollEnabled(true);
        bouquets.setOnItemClickListener((parent,v,index,id) -> { if(busy) return; selectedGroup=bouquetNames.get(index); showingBouquets=false; filter(true); });
        content.addView(bouquets,new FrameLayout.LayoutParams(-1,-1)); buildVodViews(content,wide); root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout dock=new LinearLayout(this); dockBar=dock; dock.setPadding(dp(3),dp(3),dp(3),dp(3)); dock.setBackground(Ui.shape(this,Ui.SURFACE,14,Ui.BORDER));
        libraryButton=Ui.button(this,"Bukiety"); favoriteButton=Ui.button(this,"☆ Ulubione");
        dock.addView(libraryButton,new LinearLayout.LayoutParams(0,dp(48),1)); dock.addView(favoriteButton,new LinearLayout.LayoutParams(0,dp(48),1));
        Ui.style(libraryButton,!onlyFavorites); Ui.style(favoriteButton,onlyFavorites);
        libraryButton.setOnClickListener(v -> backToBouquets());
        favoriteButton.setOnClickListener(v -> { onlyFavorites=true; showingBouquets=false; selectedGroup=ALL; Ui.style(libraryButton,false); Ui.style(favoriteButton,true); filter(true); });
        LinearLayout.LayoutParams dockSize=new LinearLayout.LayoutParams(-1,-2); dockSize.topMargin=dp(8); dockSize.bottomMargin=dp(12);
        if(wide) { dock.removeView(favoriteButton); favoriteButton.setText("☆"); favoriteButton.setTextSize(20); favoriteButton.setContentDescription("Ulubione"); top.addView(favoriteButton,new LinearLayout.LayoutParams(dp(48),dp(48))); }
        else root.addView(dock,dockSize);
        rows=new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int p) { return shown.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p,View reused,ViewGroup parent) { return row(shown.get(p),reused); }
        };
        list.setAdapter(rows);
        setContentView(root); root.requestFocus(); busy=true;
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int f) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { searchHandler.removeCallbacks(searchTask); searchHandler.postDelayed(searchTask,150); }
            public void afterTextChanged(Editable e) { }
        });
        list.setOnItemClickListener((p, v, pos, id) -> {
            Channel c = shown.get(pos);
            if(c.kind().equals("series")) openSeries(c); else if(c.kind().equals("movie")) openMovie(c); else preview.select(c);
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> { toggleFavorite(shown.get(pos)); return true; });
        if(state!=null) search.setText(state.getString("query",""));
        loadCachedSection(section);
    }
    private void updateSearchHint() {
        search.setHint(section.equals("tv")?"Szukaj kanału":section.equals("movie")?"Szukaj filmu":"Szukaj serialu");
    }
    private void backToBouquets() {
        onlyFavorites=false; showingBouquets=true; selectedGroup=ALL; Ui.style(libraryButton,true); Ui.style(favoriteButton,false);
        if(search.getText().length()>0) search.setText(""); filter(true);
    }
    private void toggleFavorite(Channel c) {
        boolean added=!favorites.remove(c.url()); if(added) favorites.add(c.url());
        persistFavorites();
        if(vod()) updateMyList();
        String place=vod()?"„Moja lista”":"ulubionych"; Toast.makeText(this,(added?"Dodano do ":"Usunięto z ")+place,Toast.LENGTH_SHORT).show();
        // Only the favourites view changes its rows; elsewhere keep the scroll position.
        if(onlyFavorites) filter(false); else { rows.notifyDataSetChanged(); posterAdapter.notifyDataSetChanged(); }
        if(shelves.getVisibility()==View.VISIBLE) shelfAdapter.notifyDataSetChanged();
    }
    /** Keeps the "Moja lista" row current without rebuilding (and scrolling) the whole shelf list. */
    private void updateMyList() {
        List<Channel> mine=new ArrayList<>(); int count=0;
        for(Channel c:sectionIndex.getOrDefault(section,Collections.emptyList())) if(favorites.contains(c.url())) { count++; if(mine.size()<16) mine.add(c); }
        boolean listed=shelfNames.contains(MYLIST);
        if(mine.isEmpty()) { if(listed) { shelfNames.remove(MYLIST); shelfItems.remove(MYLIST); specialCounts.remove(MYLIST); } }
        else { shelfItems.put(MYLIST,mine); specialCounts.put(MYLIST,count); if(!listed) shelfNames.add(shelfNames.contains(CONTINUE)?1:0,MYLIST); }
        shelfAdapter.notifyDataSetChanged();
    }
    private void putSection(String name,List<Channel> items) {
        sectionIndex.put(name,items); searchKeys.remove(name);
        // Search keys are prepared off the main thread; filter() falls back to on-the-fly keys meanwhile.
        indexer.execute(() -> {
            String[] keys=searchKeysFor(items);
            runOnUiThread(() -> { if(!isDestroyed() && sectionIndex.get(name)==items) { searchKeys.put(name,keys); if(name.equals(section) && search.getText().length()>0) filter(false); } });
        });
    }
    private static String[] searchKeysFor(List<Channel> items) {
        String[] keys=new String[items.size()]; for(int i=0;i<keys.length;i++) keys[i]=TitleSearch.normalize(items.get(i).name()); return keys;
    }
    private static String display(String group) {
        return group.equals(CONTINUE)?"Kontynuuj oglądanie":group.equals(MYLIST)?"Moja lista":group.equals(RECENTLY)?"Ostatnio dodane":group.equals(ALL)?"Cała oferta":group;
    }
    private static boolean special(String group) { return group.startsWith("\u0001"); }
    private void loadCachedSection(String requested) {
        busy=true;
        loadingMessage=requested.equals("tv")?"Wczytywanie kanałów…":requested.equals("movie")?"Wczytywanie filmów…":"Wczytywanie seriali…";
        status.setText(loadingMessage); filter();
        worker.execute(() -> {
            try {
                Set<String> savedFavorites=new HashSet<>();
                List<Channel> loaded=store.loadSection(requested,savedFavorites);
                String[] normalized=searchKeysFor(loaded);
                runOnUiThread(() -> {
                    if(isDestroyed()) return;
                    channels.removeIf(c -> c.section().equals(requested)); channels.addAll(loaded); sectionIndex.put(requested,loaded); searchKeys.put(requested,normalized);
                    if(loadedSections.isEmpty()) { favorites.clear(); favorites.addAll(savedFavorites); }
                    loadedSections.add(requested); busy=false; refreshGroups();
                    if(loaded.stream().anyMatch(c -> c.bouquetOrder()==Integer.MAX_VALUE)) syncOrder(requested,false);
                    if(getIntent().getBooleanExtra("open_source",false)) { getIntent().removeExtra("open_source"); sourceMenu(); }
                    if(getIntent().getBooleanExtra("refresh_source",false)) {
                        getIntent().removeExtra("refresh_source");
                        load(progress -> new SourceClient().refreshCatalog(loaded, progress));
                    }
                });
            } catch(Exception e) {
                runOnUiThread(() -> {
                    if(isDestroyed()) return; busy=false; refreshGroups();
                    error("Nie można odczytać zapisanej sekcji. Dodaj ponownie źródło, jeśli problem się powtórzy.");
                });
            }
        });
    }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private EditText input(String hint,boolean password) {
        EditText e=new EditText(this); e.setHint(hint); e.setSingleLine(true); e.setTextSize(15); e.setTextColor(Ui.TEXT); e.setHintTextColor(Ui.FAINT);
        e.setPadding(dp(14),0,dp(14),0); e.setBackground(Ui.shape(this,Ui.SURFACE,12,Ui.BORDER));
        e.setCompoundDrawablePadding(dp(8));
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        e.setInputType(password ? android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD : android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setOnEditorActionListener((v,action,event) -> { ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0); return true; });
        return e;
    }
    private String sortMode() { return getSharedPreferences("catalog-ui",MODE_PRIVATE).getString("sort-"+section,"provider"); }
    private String titleMode() { return getSharedPreferences("catalog-ui",MODE_PRIVATE).getString("titles-"+section,TitleSearch.PROVIDER); }
    private void chooseTitleSort() {
        String mode=titleMode(); int selected=0;
        for(int i=0;i<TitleSearch.MODES.length;i++) if(TitleSearch.MODES[i].equals(mode)) selected=i;
        new AlertDialog.Builder(this).setTitle("Sortuj tytuły").setSingleChoiceItems(TitleSearch.LABELS,selected,(dialog,index) -> {
            getSharedPreferences("catalog-ui",MODE_PRIVATE).edit().putString("titles-"+section,TitleSearch.MODES[index]).apply();
            dialog.dismiss(); filter(true);
        }).setNegativeButton("Anuluj",null).show();
    }
    private void chooseSort() {
        if(vod() && !gridMode) { chooseTitleSort(); return; }
        String mode=sortMode(); int selected=0;
        for(int i=0;i<BouquetOrder.MODES.length;i++) if(BouquetOrder.MODES[i].equals(mode)) selected=i;
        new AlertDialog.Builder(this).setTitle("Sortowanie bukietów").setSingleChoiceItems(BouquetOrder.LABELS,selected,(dialog,index) -> {
            getSharedPreferences("catalog-ui",MODE_PRIVATE).edit().putString("sort-"+section,BouquetOrder.MODES[index]).apply();
            showingBouquets=true; onlyFavorites=false; Ui.style(libraryButton,true); Ui.style(favoriteButton,false); selectedGroup=ALL; search.setText(""); dialog.dismiss(); refreshGroups();
            if(index==0) syncOrder(section,true);
        }).setNegativeButton("Anuluj",null).show();
    }
    private void syncOrder(String requested,boolean force) {
        if(!force && !orderRequested.add(requested)) return;
        final int generation=catalogGeneration;
        List<Channel> snapshot=new ArrayList<>(sectionIndex.getOrDefault(requested,Collections.emptyList()));
        if(snapshot.isEmpty()) return;
        syncingOrder=true; filter();
        network.execute(() -> {
            try {
                List<Channel> updated=new SourceClient().refreshCategoryOrder(snapshot,requested);
                runOnUiThread(() -> {
                    if(isDestroyed() || generation!=catalogGeneration) return;
                    syncingOrder=false;
                    if(!updated.equals(snapshot)) {
                        channels.removeIf(c -> c.section().equals(requested)); channels.addAll(updated); putSection(requested,updated);
                        if(!worker.isShutdown()) worker.execute(() -> { try { store.updateSection(requested,updated); } catch(Exception ignored) {} });
                    }
                    refreshGroups();
                });
            } catch(Exception e) { runOnUiThread(() -> { if(!isDestroyed() && generation==catalogGeneration) { syncingOrder=false; filter(); } }); }
        });
    }
    private void refreshGroups() {
        bouquetCounts.clear();
        List<Channel> items=sectionIndex.getOrDefault(section,Collections.emptyList());
        bouquetCounts.putAll(BouquetOrder.counts(items,sortMode()));
        bouquetNames=new ArrayList<>(); bouquetNames.add(ALL); bouquetNames.addAll(bouquetCounts.keySet());
        if(!bouquetNames.contains(selectedGroup)) selectedGroup=ALL;
        boolean wide=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE; wideLayout=wide;
        bouquets.setAdapter(new BaseAdapter() {
            public int getCount() { return bouquetNames.size(); }
            public Object getItem(int p) { return bouquetNames.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p,View reused,ViewGroup parent) {
                LinearLayout card;
                if(reused instanceof LinearLayout) card=(LinearLayout)reused;
                else {
                    card=column(); card.setPadding(dp(14),dp(12),dp(12),dp(12));
                    card.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,dp(wide?72:84)));
                    TextView title=Ui.text(MainActivity.this,"",15,Ui.TEXT); title.setTypeface(null,Typeface.BOLD); title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END);
                    card.addView(title,new LinearLayout.LayoutParams(-1,0,1));
                    TextView count=Ui.text(MainActivity.this,"",12,Ui.MUTED); card.addView(count);
                }
                String name=bouquetNames.get(p);
                card.setBackground(Ui.touch(MainActivity.this,p==0?Ui.SURFACE_HIGH:Ui.SURFACE,12,Ui.BORDER));
                ((TextView)card.getChildAt(0)).setText(p==0?"Cała oferta":name);
                int n=p==0?items.size():bouquetCounts.get(name);
                ((TextView)card.getChildAt(1)).setText(Ui.count(n)+(section.equals("tv")?" kanałów":section.equals("movie")?" filmów":" seriali"));
                card.setContentDescription("Bukiet "+name); return card;
            }
        });
        if(vod()) prepareShelves(items);
        filter(true);
    }
    private View row(Channel c,View reused) {
        LinearLayout row;
        if(reused instanceof LinearLayout) row=(LinearLayout)reused;
        else {
            row=column(); row.setPadding(dp(12),dp(10),dp(8),dp(10)); row.setMinimumHeight(dp(94));
            row.setBackground(Ui.touch(this,Ui.SURFACE,12,0));
            LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout badge=new FrameLayout(this); badge.setBackground(Ui.shape(this,Ui.SURFACE_HIGH,10,0)); badge.setClipToOutline(true);
            TextView icon=Ui.text(this,"",14,Ui.TEXT); icon.setGravity(Gravity.CENTER); icon.setTypeface(null,Typeface.BOLD);
            badge.addView(icon,new FrameLayout.LayoutParams(-1,-1));
            ImageView logo=new ImageView(this); logo.setScaleType(ImageView.ScaleType.FIT_CENTER); logo.setPadding(dp(4),dp(4),dp(4),dp(4));
            badge.addView(logo,new FrameLayout.LayoutParams(-1,-1));
            header.addView(badge,new LinearLayout.LayoutParams(dp(46),dp(40)));
            TextView name=Ui.text(this,"",15,Ui.TEXT); name.setMaxLines(2); name.setEllipsize(TextUtils.TruncateAt.END); name.setPadding(dp(10),0,dp(4),0);
            header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            Button guide=Ui.button(this,"EPG"); guide.setFocusable(false); guide.setTextSize(12); Ui.ghost(guide,Ui.MUTED); guide.setPadding(0,0,0,0); guide.setSingleLine(true); header.addView(guide,new LinearLayout.LayoutParams(dp(48),dp(48)));
            Button star=Ui.button(this,"☆"); star.setFocusable(false); star.setTextSize(20); Ui.ghost(star,Ui.FAINT); header.addView(star,new LinearLayout.LayoutParams(dp(40),dp(48)));
            row.addView(header,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout schedule=column(); schedule.setPadding(0,dp(6),dp(4),0);
            TextView now=Ui.text(this,"",13,Ui.MUTED); now.setMaxLines(2); now.setEllipsize(TextUtils.TruncateAt.END); schedule.addView(now);
            TextView next=Ui.text(this,"",12,Ui.FAINT); next.setMaxLines(1); next.setEllipsize(TextUtils.TruncateAt.END); next.setPadding(0,dp(4),0,0); schedule.addView(next);
            row.addView(schedule,new LinearLayout.LayoutParams(-1,-2));
        }
        row.setBackground(Ui.touch(this,preview!=null && c.url().equals(preview.selectedUrl())?0xFF243344:Ui.SURFACE,12,0));
        row.setMinimumHeight(dp(80));
        LinearLayout header=(LinearLayout)row.getChildAt(0),schedule=(LinearLayout)row.getChildAt(1);
        FrameLayout badge=(FrameLayout)header.getChildAt(0); TextView icon=(TextView)badge.getChildAt(0);
        ImageLoader.get(this).load(c.logo(),(ImageView)badge.getChildAt(1),dp(46),dp(40));
        String initial=c.name().replaceAll("^[^\\p{L}\\p{N}]*(?:[A-Z]{2,3}\\s*[|:]\\s*)?","");
        icon.setText(c.kind().equals("tv")?(initial.isEmpty()?"▶":initial.substring(0,Math.min(2,initial.length())).toUpperCase(Locale.ROOT)):c.kind().equals("series")?"▤":"▶");
        ((TextView)header.getChildAt(1)).setText(c.name());
        TextView detail=(TextView)schedule.getChildAt(0),next=(TextView)schedule.getChildAt(1);
        if(c.kind().equals("tv")) {
            GuideRepository repository=GuideRepository.get(this);
            bindSchedule(detail,next,repository.peek(c,false)); row.setTag(c.url());
            java.lang.ref.WeakReference<TextView> target=new java.lang.ref.WeakReference<>(detail),following=new java.lang.ref.WeakReference<>(next);
            java.lang.ref.WeakReference<LinearLayout> holder=new java.lang.ref.WeakReference<>(row);
            java.lang.ref.WeakReference<LivePreview> previewTarget=new java.lang.ref.WeakReference<>(preview);
            row.postDelayed(() -> {
                LinearLayout visible=holder.get(); android.graphics.Rect bounds=new android.graphics.Rect();
                if(isDestroyed() || visible==null || !visible.isShown() || !c.url().equals(visible.getTag()) || !visible.getGlobalVisibleRect(bounds)) return;
                repository.request(c,false,data -> {
                    TextView view=target.get(),after=following.get(); LinearLayout owner=holder.get();
                    if(view!=null && after!=null && owner!=null && c.url().equals(owner.getTag())) bindSchedule(view,after,data);
                    LivePreview selected=previewTarget.get(); if(selected!=null) selected.guideChanged(c.url());
                });
            },180);
        } else { detail.setText(c.group()); next.setVisibility(View.GONE); }
        Button guide=(Button)header.getChildAt(2); guide.setVisibility(c.kind().equals("tv")?View.VISIBLE:View.GONE);
        guide.setContentDescription("Program TV i archiwum: "+c.name());
        guide.setOnClickListener(v -> GuideDialog.open(MainActivity.this,c,network,MainActivity.this::play));
        Button star=(Button)header.getChildAt(3); boolean saved=favorites.contains(c.url()); star.setText(saved?"★":"☆"); star.setTextColor(saved?Ui.STAR:Ui.FAINT);
        star.setContentDescription((saved?"Usuń z ulubionych: ":"Dodaj do ulubionych: ")+c.name());
        star.setOnClickListener(v -> toggleFavorite(c));
        return row;
    }
    private static void bindSchedule(TextView now,TextView next,GuideRepository.Data data) {
        String[] lines=GuideRepository.summary(data,System.currentTimeMillis()/1000).split("\\n",2);
        now.setText(lines[0]); next.setText(lines.length>1?lines[1]:""); next.setVisibility(lines.length>1?View.VISIBLE:View.GONE);
    }
    private void filter() { filter(true); }
    /** Hiding an adapter view also drops its old rows, so hidden screens never keep stale items. */
    private void show(AbsListView view,BaseAdapter adapter,boolean visible) {
        if(!visible && view.getVisibility()==View.VISIBLE) view.setAdapter(adapter);
        view.setVisibility(visible?View.VISIBLE:View.GONE);
    }
    private void filter(boolean resetScroll) {
        if (list == null) return;
        String query = search.getText().toString().trim();
        String[] tokens=TitleSearch.tokens(query);
        boolean querying=tokens.length>0;
        boolean grid=showingBouquets && !querying && !onlyFavorites; gridMode=grid;
        if(clearSearch!=null) clearSearch.setVisibility(query.isEmpty()?View.GONE:View.VISIBLE);
        List<Channel> items=sectionIndex.getOrDefault(section,Collections.emptyList());
        String[] keys=searchKeys.get(section); if(keys!=null && keys.length!=items.size()) keys=null;
        List<Channel> next = new ArrayList<>();
        if(!grid) {
            // Searching from the shelves or bouquets covers the whole section; inside a row it stays in that row.
            boolean searchAll=showingBouquets && querying;
            String group=searchAll?ALL:selectedGroup;
            boolean mine=onlyFavorites || group.equals(MYLIST);
            int[] scores=querying?new int[items.size()]:null;
            List<Integer> hits=new ArrayList<>();
            for (int i=0;i<items.size();i++) {
                Channel c=items.get(i);
                if (mine && !favorites.contains(c.url())) continue;
                if (group.equals(CONTINUE) && !continuing.containsKey(c.url())) continue;
                if (group.equals(RECENTLY) && c.added()<=0) continue;
                if (!special(group) && !group.equals(ALL) && !group.equals(c.group())) continue;
                if (querying) { int score=TitleSearch.score(keys!=null?keys[i]:TitleSearch.normalize(c.name()),tokens); if(score<0) continue; scores[i]=score; }
                hits.add(i);
            }
            if(vod()) {
                if(group.equals(CONTINUE)) hits.sort(Comparator.comparingLong((Integer i) -> continuing.getOrDefault(items.get(i).url(),0L)).reversed());
                else {
                    String mode=group.equals(RECENTLY)?TitleSearch.ADDED:titleMode();
                    String[] sortKeys=keys;
                    if(sortKeys==null && (mode.equals(TitleSearch.AZ) || mode.equals(TitleSearch.ZA))) { sortKeys=new String[items.size()]; for(Integer i:hits) sortKeys[i]=TitleSearch.normalize(items.get(i).name()); }
                    TitleSearch.sort(hits,items,sortKeys,mode);
                }
                // Best matches first; the chosen title order is kept within each match level (stable sort).
                if(querying) { final int[] rank=scores; hits.sort(Comparator.comparingInt((Integer i) -> rank[i])); }
            }
            for(Integer i:hits) next.add(items.get(i));
        }
        shown=next;
        boolean live=!grid && !busy && !shown.isEmpty() && !vod(); liveArea.setVisibility(live?View.VISIBLE:View.GONE);
        if(wideLayout) {
            ViewGroup parent=(ViewGroup)crumb.getParent();
            if(live && parent!=liveListColumn) { parent.removeView(crumb); liveListColumn.addView(crumb,0,new LinearLayout.LayoutParams(-1,-2)); }
            else if(!live && parent!=screenRoot) { parent.removeView(crumb); screenRoot.addView(crumb,1,new LinearLayout.LayoutParams(-1,-2)); }
        }
        if(!wideLayout) {
            ViewGroup parent=(ViewGroup)favoriteButton.getParent();
            if(live && parent!=crumb) {
                parent.removeView(favoriteButton); favoriteButton.setText("☆"); favoriteButton.setTextSize(20); favoriteButton.setContentDescription("Ulubione"); crumb.addView(favoriteButton,new LinearLayout.LayoutParams(dp(48),dp(48)));
            } else if(!live && parent!=dockBar) {
                parent.removeView(favoriteButton); favoriteButton.setText("☆ Ulubione"); favoriteButton.setTextSize(14); dockBar.addView(favoriteButton,new LinearLayout.LayoutParams(0,dp(48),1));
            }
            dockBar.setVisibility(live?View.GONE:View.VISIBLE);
        }
        preview.show(shown,live,onlyFavorites?"favorites":selectedGroup+"|"+query);
        rows.notifyDataSetChanged(); posterAdapter.notifyDataSetChanged();
        if(resetScroll) { list.setSelection(0); posters.setSelection(0); }
        int total=items.size();
        progress.setVisibility(busy?View.VISIBLE:View.GONE);
        emptyAction.setVisibility(!busy && channels.isEmpty()?View.VISIBLE:View.GONE);
        emptyText.setText(busy?loadingMessage
            :channels.isEmpty()?"Brak źródła\n\nPołącz konto Xtream Codes albo wczytaj listę M3U."
            :total==0?(section.equals("movie")?"Brak filmów\n\nTo źródło nie udostępnia filmów VOD.":section.equals("series")?"Brak seriali\n\nTo źródło nie udostępnia seriali.":"Brak kanałów TV w tym źródle.")
            :onlyFavorites?"Brak ulubionych\n\nDotknij ☆ przy pozycji, aby ją tu zapisać."
            :querying?"Brak wyników dla „"+query+"”\n\nSprawdź pisownię albo wpisz krótszy fragment tytułu.":"Brak pozycji w tym rzędzie.");
        boolean inside=!grid && !busy && total>0;
        crumb.setVisibility(inside?View.VISIBLE:View.GONE);
        crumbTitle.setText(!inside?"":onlyFavorites?"Ulubione":querying && showingBouquets?"Wyniki wyszukiwania":display(selectedGroup));
        boolean vod=vod();
        status.setVisibility(busy || live || total==0 || (grid && vod)?View.GONE:View.VISIBLE);
        sortButton.setVisibility(grid || (vod && !shown.isEmpty())?View.VISIBLE:View.INVISIBLE);
        sortButton.setContentDescription(grid?"Sortuj bukiety":"Sortuj tytuły");
        if(heroItem!=null) heroSave.setText(favorites.contains(heroItem.url())?"✓  Moja lista":"＋  Moja lista");
        bouquets.setVisibility(grid && !busy && total>0 && !vod?View.VISIBLE:View.GONE);
        shelves.setVisibility(grid && !busy && total>0 && vod?View.VISIBLE:View.GONE);
        show(list,rows,!grid && !busy && !shown.isEmpty() && !vod);
        show(posters,posterAdapter,!grid && !busy && !shown.isEmpty() && vod);
        emptyPanel.setVisibility(busy || total==0 || (!grid && shown.isEmpty())?View.VISIBLE:View.GONE);
        if(!busy) status.setText(grid?Ui.count(bouquetCounts.size())+" bukietów · "+Ui.count(total)+" pozycji"+(syncingOrder?" · aktualizuję kolejność…":"")
            :querying?Ui.count(shown.size())+(shown.size()==1?" wynik":" wyników")+" dla „"+query+"”"
            :Ui.count(shown.size())+(shown.size()==1?" pozycja":" pozycji")+(vod() && !special(selectedGroup)?" · "+TitleSearch.label(titleMode()):""));
    }
    // ---- Films and series: streaming-style shelves and poster grid ------------------------------

    private boolean vod() { return !section.equals("tv"); }
    private boolean hasCovers(List<Channel> items) {
        int checked=0;
        for(Channel c:items) { if(!c.logo().isEmpty()) return true; if(++checked>400) break; }
        return false;
    }
    private void buildVodViews(FrameLayout content,boolean wide) {
        shelves=new ListView(this); shelves.setDivider(null); shelves.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        shelves.setClipToPadding(false); shelves.setPadding(0,0,0,dp(8)); shelves.setVerticalScrollBarEnabled(false);
        // Featured item on top of the shelves.
        LinearLayout header=column();
        hero=new FrameLayout(this); hero.setClipToOutline(true); hero.setBackground(Ui.shape(this,Ui.SURFACE,16,0));
        heroImage=new ImageView(this); heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(heroImage,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this); shade.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0x660A0D14,0xF20A0D14}));
        hero.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout heroText=column(); heroText.setPadding(dp(16),0,dp(16),dp(16));
        heroMeta=Ui.text(this,"",12,Ui.MUTED); heroMeta.setMaxLines(1); heroMeta.setEllipsize(TextUtils.TruncateAt.END); heroText.addView(heroMeta);
        heroTitle=Ui.text(this,"",22,Ui.TEXT); heroTitle.setTypeface(null,Typeface.BOLD); heroTitle.setMaxLines(2); heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        heroTitle.setPadding(0,dp(2),0,dp(12)); heroText.addView(heroTitle);
        LinearLayout heroButtons=new LinearLayout(this);
        heroPlay=Ui.button(this,"▶  Odtwórz"); Ui.primary(heroPlay); heroPlay.setTextSize(15);
        hero.setOnClickListener(v -> { if(heroItem!=null) open(heroItem); });
        heroPlay.setOnClickListener(v -> { if(heroItem!=null) { if(heroItem.kind().equals("series")) openSeries(heroItem); else play(heroItem); } });
        heroButtons.addView(heroPlay,new LinearLayout.LayoutParams(0,dp(48),1));
        heroSave=Ui.button(this,"＋  Moja lista"); heroSave.setTextColor(Ui.TEXT); heroSave.setBackground(Ui.touch(this,0x33FFFFFF,12,0));
        heroSave.setOnClickListener(v -> { if(heroItem!=null) { toggleFavorite(heroItem); bindHero(); } });
        LinearLayout.LayoutParams saveSize=new LinearLayout.LayoutParams(0,dp(48),1); saveSize.leftMargin=dp(10); heroButtons.addView(heroSave,saveSize);
        heroText.addView(heroButtons);
        hero.addView(heroText,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        header.addView(hero,new LinearLayout.LayoutParams(-1,dp(wide?220:300)));
        // Shown instead of the hero when the saved library has no artwork yet.
        coverHint=column(); coverHint.setPadding(dp(16),dp(14),dp(16),dp(14)); coverHint.setBackground(Ui.shape(this,Ui.SURFACE,16,Ui.BORDER));
        TextView hintText=Ui.text(this,"Brak okładek w zapisanej bibliotece. Odśwież źródło, aby pobrać plakaty filmów i seriali.",13,Ui.MUTED); coverHint.addView(hintText);
        Button refresh=Ui.button(this,"Odśwież źródło"); Ui.primary(refresh);
        refresh.setOnClickListener(v -> { List<Channel> items=sectionIndex.getOrDefault(section,Collections.emptyList()); if(!busy && !items.isEmpty()) load(progress -> new SourceClient().refreshCatalog(items,progress)); });
        LinearLayout.LayoutParams refreshSize=new LinearLayout.LayoutParams(-2,dp(40)); refreshSize.topMargin=dp(10); coverHint.addView(refresh,refreshSize);
        header.addView(coverHint,new LinearLayout.LayoutParams(-1,-2));
        View gap=new View(this); header.addView(gap,new LinearLayout.LayoutParams(1,dp(6)));
        shelves.addHeaderView(header,null,false);
        shelfAdapter=new BaseAdapter() {
            public int getCount() { return shelfNames.size(); }
            public Object getItem(int p) { return shelfNames.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p,View reused,ViewGroup parent) { return shelf(shelfNames.get(p),reused); }
        };
        shelves.setAdapter(shelfAdapter);
        shelves.setOnItemClickListener((parent,v,index,id) -> {
            int row=index-shelves.getHeaderViewsCount(); if(busy || row<0 || row>=shelfNames.size()) return;
            selectedGroup=shelfNames.get(row); showingBouquets=false; filter(true);
        });
        content.addView(shelves,new FrameLayout.LayoutParams(-1,-1));
        posters=new GridView(this); posters.setNumColumns(wide?6:3); posters.setHorizontalSpacing(dp(10)); posters.setVerticalSpacing(dp(14));
        posters.setStretchMode(GridView.STRETCH_COLUMN_WIDTH); posters.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        posters.setClipToPadding(false); posters.setPadding(0,dp(4),0,dp(12)); posters.setFastScrollEnabled(true);
        posterAdapter=new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int p) { return shown.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p,View reused,ViewGroup parent) {
                int columns=Math.max(1,posters.getNumColumns());
                int available=posters.getWidth()>0?posters.getWidth():getResources().getDisplayMetrics().widthPixels-dp(32);
                return poster(shown.get(p),reused,Math.max(dp(60),(available-dp(10)*(columns-1))/columns));
            }
        };
        posters.setAdapter(posterAdapter);
        posters.setOnItemClickListener((parent,v,index,id) -> open(shown.get(index)));
        posters.setOnItemLongClickListener((parent,v,index,id) -> { toggleFavorite(shown.get(index)); return true; });
        content.addView(posters,new FrameLayout.LayoutParams(-1,-1));
    }
    private void open(Channel c) { if(c.kind().equals("series")) openSeries(c); else if(c.kind().equals("movie")) openMovie(c); else preview.select(c); }
    private final Map<String,Integer> specialCounts=new HashMap<>();
    private void prepareShelves(List<Channel> items) {
        shelfItems.clear(); specialCounts.clear();
        List<Channel> mine=new ArrayList<>(); int mineCount=0, dated=0;
        // Newest 16 by provider "added" time without sorting the whole catalogue.
        PriorityQueue<Channel> newest=new PriorityQueue<>(Comparator.comparingLong(Channel::added));
        for(Channel c:items) {
            List<Channel> row=shelfItems.computeIfAbsent(c.group(),key -> new ArrayList<>()); if(row.size()<12) row.add(c);
            if(favorites.contains(c.url())) { mineCount++; if(mine.size()<16) mine.add(c); }
            if(c.added()>0) { dated++; newest.add(c); if(newest.size()>16) newest.poll(); }
        }
        List<Channel> recent=new ArrayList<>(newest); recent.sort(Comparator.comparingLong(Channel::added).reversed());
        shelfNames=new ArrayList<>();
        if(!continuing.isEmpty()) {
            List<Channel> resume=new ArrayList<>(); Map<String,Channel> byUrl=new HashMap<>(); for(Channel c:items) if(continuing.containsKey(c.url())) byUrl.put(c.url(),c);
            for(String url:continuing.keySet()) { Channel c=byUrl.get(url); if(c!=null && resume.size()<16) resume.add(c); }
            if(!resume.isEmpty()) { shelfNames.add(CONTINUE); shelfItems.put(CONTINUE,resume); specialCounts.put(CONTINUE,byUrl.size()); }
        }
        if(!mine.isEmpty()) { shelfNames.add(MYLIST); shelfItems.put(MYLIST,mine); specialCounts.put(MYLIST,mineCount); }
        if(!recent.isEmpty()) { shelfNames.add(RECENTLY); shelfItems.put(RECENTLY,recent); specialCounts.put(RECENTLY,dated); }
        shelfNames.addAll(bouquetCounts.keySet());
        if(section.equals("movie")) refreshContinuing(items);
        heroItem=null;
        if(hasCovers(items)) {
            // Prefer a "recently added" style bouquet, otherwise the first one with artwork.
            List<String> order=new ArrayList<>(shelfNames);
            order.sort(Comparator.comparingInt(name -> RECENT.matcher(name).matches()?0:1));
            for(String name:order) { for(Channel c:shelfItems.get(name)) if(!c.logo().isEmpty()) { heroItem=c; break; } if(heroItem!=null) break; }
        }
        bindHero();
        coverHint.setVisibility(!items.isEmpty() && heroItem==null?View.VISIBLE:View.GONE);
        shelves.setAdapter(shelfAdapter);
    }
    /** Reads watch progress for the visible films in the background and adds the "Kontynuuj oglądanie" row. */
    private void refreshContinuing(List<Channel> items) {
        final String target=section;
        indexer.execute(() -> {
            LinkedHashMap<String,Long> found=WatchProgress.inProgress(getApplicationContext(),items);
            runOnUiThread(() -> {
                if(isDestroyed() || !target.equals(section) || sectionIndex.get(target)!=items || found.keySet().equals(continuing.keySet())) return;
                continuing=found; prepareShelves(items); filter(false);
            });
        });
    }
    private void bindHero() {
        hero.setVisibility(heroItem==null?View.GONE:View.VISIBLE);
        if(heroItem==null) { heroTitle.setText(""); heroMeta.setText(""); heroImage.setImageDrawable(null); return; }
        heroTitle.setText(heroItem.name()); int year=TitleSearch.year(heroItem.name());
        heroMeta.setText((heroItem.kind().equals("series")?"SERIAL":"FILM")+(year>0?"  ·  "+year:"")+"  ·  "+heroItem.group().toUpperCase(Locale.ROOT));
        heroPlay.setText(heroItem.kind().equals("series")?"▶  Odcinki":"▶  Odtwórz");
        boolean saved=favorites.contains(heroItem.url()); heroSave.setText(saved?"✓  Moja lista":"＋  Moja lista");
        ImageLoader.get(this).load(heroItem.logo(),heroImage,getResources().getDisplayMetrics().widthPixels,dp(300));
    }
    private View shelf(String name,View reused) {
        LinearLayout row;
        if(reused instanceof LinearLayout) row=(LinearLayout)reused;
        else {
            row=column(); row.setPadding(0,dp(10),0,dp(6));
            LinearLayout head=new LinearLayout(this); head.setMinimumHeight(dp(48)); head.setBackground(Ui.touch(this,Ui.BG,10,0)); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(dp(2),0,dp(2),dp(8));
            TextView title=Ui.text(this,"",16,Ui.TEXT); title.setTypeface(null,Typeface.BOLD); title.setMaxLines(1); title.setEllipsize(TextUtils.TruncateAt.END);
            head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            TextView more=Ui.text(this,"",12,Ui.MUTED); head.addView(more);
            row.addView(head);
            HorizontalScrollView scroller=new HorizontalScrollView(this); scroller.setHorizontalScrollBarEnabled(false); scroller.setClipToPadding(false);
            LinearLayout strip=new LinearLayout(this); scroller.addView(strip); row.addView(scroller);
        }
        LinearLayout head=(LinearLayout)row.getChildAt(0);
        ((TextView)head.getChildAt(0)).setText(display(name));
        Integer total=special(name)?specialCounts.get(name):bouquetCounts.get(name);
        ((TextView)head.getChildAt(1)).setText(Ui.count(total==null?0:total)+"  ›");
        head.setOnClickListener(v -> { if(busy) return; selectedGroup=name; showingBouquets=false; filter(true); });
        HorizontalScrollView scroller=(HorizontalScrollView)row.getChildAt(1);
        if(!name.equals(row.getTag())) scroller.scrollTo(0,0);
        row.setTag(name);
        LinearLayout strip=(LinearLayout)scroller.getChildAt(0);
        List<Channel> items=shelfItems.getOrDefault(name,Collections.emptyList());
        int width=dp(getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?118:112);
        for(int i=0;i<items.size();i++) {
            View old=i<strip.getChildCount()?strip.getChildAt(i):null;
            View card=poster(items.get(i),old,width);
            if(old==null) { LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(width,-2); size.rightMargin=dp(10); strip.addView(card,size); }
            card.setVisibility(View.VISIBLE);
            Channel item=items.get(i);
            card.setOnClickListener(v -> open(item));
            card.setOnLongClickListener(v -> { toggleFavorite(item); return true; });
        }
        for(int i=items.size();i<strip.getChildCount();i++) strip.getChildAt(i).setVisibility(View.GONE);
        return row;
    }
    /** Poster card: 2:3 artwork (or a quiet title placeholder), favourite badge and one-line title. */
    private View poster(Channel c,View reused,int width) {
        LinearLayout card;
        if(reused instanceof LinearLayout && reused.getTag()!=null) card=(LinearLayout)reused;
        else {
            card=column(); card.setTag("poster");
            FrameLayout frame=new FrameLayout(this); frame.setClipToOutline(true); frame.setBackground(Ui.shape(this,Ui.SURFACE_HIGH,10,0));
            TextView placeholder=Ui.text(this,"",13,Ui.MUTED); placeholder.setGravity(Gravity.CENTER); placeholder.setPadding(dp(8),dp(8),dp(8),dp(8));
            placeholder.setMaxLines(4); placeholder.setEllipsize(TextUtils.TruncateAt.END);
            frame.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
            ImageView image=new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
            TextView badge=Ui.text(this,"★",12,Ui.STAR); badge.setGravity(Gravity.CENTER); badge.setBackground(Ui.shape(this,0xCC0A0D14,12,0));
            FrameLayout.LayoutParams badgeSize=new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.TOP|Gravity.END); badgeSize.topMargin=dp(6); badgeSize.rightMargin=dp(6);
            frame.addView(badge,badgeSize);
            card.addView(frame,new LinearLayout.LayoutParams(-1,width*3/2));
            TextView title=Ui.text(this,"",12,Ui.MUTED); title.setMaxLines(1); title.setEllipsize(TextUtils.TruncateAt.END); title.setPadding(dp(2),dp(6),dp(2),0);
            card.addView(title);
        }
        FrameLayout frame=(FrameLayout)card.getChildAt(0);
        LinearLayout.LayoutParams frameSize=(LinearLayout.LayoutParams)frame.getLayoutParams();
        if(frameSize.height!=width*3/2) { frameSize.height=width*3/2; frame.setLayoutParams(frameSize); }
        ((TextView)frame.getChildAt(0)).setText(c.name());
        ImageLoader.get(this).load(c.logo(),(ImageView)frame.getChildAt(1),width,width*3/2);
        frame.getChildAt(2).setVisibility(favorites.contains(c.url())?View.VISIBLE:View.GONE);
        ((TextView)card.getChildAt(1)).setText(c.name());
        card.setContentDescription(c.name());
        return card;
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if(preview!=null) preview.save(out);
        out.putString("section",section); out.putString("bouquet",selectedGroup); out.putBoolean("showingBouquets",showingBouquets);
        out.putBoolean("onlyFavorites",onlyFavorites); out.putString("query",search.getText().toString()); super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed() {
        if(!showingBouquets || !search.getText().toString().isEmpty() || onlyFavorites) {
            showingBouquets=true; onlyFavorites=false; selectedGroup=ALL; search.setText("");
            Ui.style(libraryButton,true); Ui.style(favoriteButton,false); filter();
        } else super.onBackPressed();
    }
    private void play(Channel c) {
        Intent intent=new Intent(this,PlayerActivity.class).putExtra("url",c.url()).putExtra("name",c.name()).putExtra("kind",c.kind()).putExtra("guide",c.guideUrl()).putExtra("epgId",c.epgId()).putExtra("days",c.archiveDays()).putExtra("timezone",c.timezone()).putExtra("group",c.group()).putExtra("logo",c.logo());
        if(c.kind().equals("tv")) intent.putExtra("navigation_session",LiveChannelQueue.remember(shown,c.url())).putExtra("navigation_scope",true).putExtra("navigation_group",selectedGroup.equals(ALL)?"":selectedGroup).putExtra("navigation_query",search.getText().toString()).putExtra("navigation_favorites",onlyFavorites);
        if(c.kind().equals("tv")) {
            fullscreenToken=preview.handoff(); intent.putExtra("playback_handoff",fullscreenToken).putExtra("return_preview",true); startActivityForResult(intent,LIVE_FULLSCREEN);
        } else startActivity(intent);
    }
    // MainActivity is a phone screen; keep the phone activity out of the shared TV source set.
    private void openMovie(Channel movie) { startActivity(new Intent().setClassName(this,"pl.tvplayer.MovieActivity").putExtra("url",movie.url()).putExtra("name",movie.name()).putExtra("group",movie.group()).putExtra("logo",movie.logo())); }
    private void openSeries(Channel series) { startActivity(SeriesActivity.intent(this,series,false)); }
    private void sourceMenu() {
        if (busy) return;
        new SourceSheet(this, new SourceSheet.Listener() {
            public void onXtream(String server, String user, String password) { load(progress -> new SourceClient().xtream(server, user, password, progress)); }
            public void onM3uUrl(String url) { load(progress -> { progress.accept("Pobieranie listy M3U…"); return new SourceClient().m3u(url); }); }
            public void onM3uFile() {
                try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), FILE_REQUEST); }
                catch (ActivityNotFoundException e) { error("Brak aplikacji do wyboru plików na tym urządzeniu."); }
            }
            public void onClearLibrary() {
                catalogGeneration++; syncingOrder=false; orderRequested.clear(); channels.clear(); sectionIndex.clear(); searchKeys.clear(); favorites.clear();
                loadedSections.addAll(Arrays.asList("tv","movie","series")); persist(); refreshGroups();
                Toast.makeText(MainActivity.this, "Biblioteka usunięta z telefonu", Toast.LENGTH_SHORT).show();
            }
        }).showMenu(!channels.isEmpty() || loadedSections.size() < 3);
    }
    private interface ImportTask { List<Channel> run(java.util.function.Consumer<String> progress) throws Exception; }
    private void load(ImportTask task) {
        catalogGeneration++; syncingOrder=false; orderRequested.clear();
        busy = true; loadingMessage="Łączenie ze źródłem…"; status.setText(loadingMessage); filter();
        network.execute(() -> {
            try {
                List<Channel> result = task.run(step -> runOnUiThread(() -> {
                    if (isDestroyed() || !busy) return;
                    loadingMessage = step; status.setText(step); emptyText.setText(step);
                }));
                if (result.isEmpty()) throw new java.io.IOException("Źródło nie zawiera obsługiwanych kanałów HTTP/HTTPS.");
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    channels = result;
                    sectionIndex.clear(); searchKeys.clear();
                    Map<String,List<Channel>> parts=new HashMap<>();
                    for(Channel c:result) parts.computeIfAbsent(c.section(),key -> new ArrayList<>()).add(c);
                    for(Map.Entry<String,List<Channel>> part:parts.entrySet()) putSection(part.getKey(),part.getValue());
                    loadedSections.addAll(Arrays.asList("tv","movie","series"));
                    Set<String> urls = new HashSet<>(); for (Channel c : channels) urls.add(c.url()); favorites.retainAll(urls);
                    busy = false;
                    if (sectionIndex.getOrDefault(section, Collections.emptyList()).isEmpty()) {
                        for (String candidate : new String[]{"tv","movie","series"}) if (sectionIndex.containsKey(candidate)) { section = candidate; break; }
                        for (Map.Entry<String,Button> item : sectionButtons.entrySet()) Ui.style(item.getValue(), item.getKey().equals(section));
                    }
                    showingBouquets = true; onlyFavorites = false; selectedGroup = ALL; search.setText("");
                    Ui.style(libraryButton, true); Ui.style(favoriteButton, false);
                    refreshGroups(); persist();
                    Toast.makeText(this, importSummary(), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                String message = e instanceof java.io.IOException || e instanceof IllegalArgumentException ? e.getMessage() : "Sprawdź adres, połączenie i dane konta.";
                // Do not expose exception URLs: they can contain credentials.
                if (message == null || message.contains("http") || message.length() > 180) message = "Nie udało się połączyć. Sprawdź adres, połączenie i dane konta.";
                final String safe = message;
                runOnUiThread(() -> { if (!isDestroyed()) { busy = false; filter(); error(safe); } });
            }
        });
    }
    private String importSummary() {
        int tv = sectionIndex.getOrDefault("tv", Collections.emptyList()).size();
        int movies = sectionIndex.getOrDefault("movie", Collections.emptyList()).size();
        int series = sectionIndex.getOrDefault("series", Collections.emptyList()).size();
        StringBuilder text = new StringBuilder("Zaimportowano: ");
        text.append(tv).append(" TV");
        if (movies > 0) text.append(" · ").append(movies).append(" filmów");
        if (series > 0) text.append(" · ").append(series).append(" seriali");
        return text.toString();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if(request==LIVE_FULLSCREEN && (isFinishing() || isDestroyed())) { if(data!=null) PlaybackHandoff.discard(data.getStringExtra("playback_handoff")); return; }
        if(request==LIVE_FULLSCREEN) {
            PlaybackHandoff.discard(fullscreenToken); fullscreenToken=null;
            if(result==RESULT_OK && data!=null && data.getStringExtra("url")!=null) {
                String returned=data.getStringExtra("url"),token=data.getStringExtra("playback_handoff"); Channel found=null;
                for(Channel channel:shown) if(channel.url().equals(returned)) { found=channel; break; }
                if(found==null) found=new Channel(data.getStringExtra("name"),returned,data.getStringExtra("group"),data.getStringExtra("epgId"),"tv",data.getStringExtra("guide"),data.getIntExtra("days",0),data.getStringExtra("timezone"));
                if(section.equals("tv")) { preview.receive(token,found); int index=shown.indexOf(found); if(index>=0) list.setSelection(index); } else PlaybackHandoff.discard(token);
            }
        }
        if (request == FILE_REQUEST && result == RESULT_OK && data != null && data.getData() != null) {
            android.net.Uri uri = data.getData();
            load(progress -> {
                progress.accept("Odczyt pliku M3U…");
                try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new java.io.IOException("Nie można otworzyć pliku.");
                    return M3uParser.parse(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8), null);
                }
            });
        }
    }
    private void persistFavorites() {
        Set<String> snapshot=new HashSet<>(favorites);
        worker.execute(() -> { try { store.saveFavorites(snapshot); } catch(Exception e) {
            runOnUiThread(() -> { if(!isDestroyed()) error("Nie udało się zapisać ulubionych."); });
        } });
    }
    private void persist() {
        List<Channel> copy = new ArrayList<>(channels); Set<String> stars = new HashSet<>(favorites);
        worker.execute(() -> { try { store.save(copy, stars); } catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed()) error("Nie udało się zapisać biblioteki na urządzeniu."); }); } });
    }
    private void error(String message) { if (!isDestroyed()) new AlertDialog.Builder(this).setTitle(Ui.APP_NAME).setMessage(message).setPositiveButton("OK", null).show(); }
    @Override protected void onResume() { super.onResume(); if(preview!=null) preview.resume(); worker.execute(() -> { try { Set<String> saved=store.loadFavorites(); runOnUiThread(() -> { if(!isDestroyed() && !favorites.equals(saved)) { favorites.clear(); favorites.addAll(saved); if(vod() && sectionIndex.containsKey(section)) updateMyList(); filter(false); } }); } catch(Exception ignored) {} }); if(section.equals("movie") && sectionIndex.containsKey("movie")) refreshContinuing(sectionIndex.get("movie")); if(rows!=null && section.equals("tv")) rows.notifyDataSetChanged(); searchHandler.removeCallbacks(epgTick); searchHandler.postDelayed(epgTick,60000); }
    @Override public void onWindowFocusChanged(boolean focus) { super.onWindowFocusChanged(focus); if(focus && rows!=null && section.equals("tv")) rows.notifyDataSetChanged(); }
    @Override protected void onPause() { searchHandler.removeCallbacks(epgTick); if(preview!=null) preview.pause(); super.onPause(); }
    @Override protected void onDestroy() { if(preview!=null) preview.destroy(); searchHandler.removeCallbacksAndMessages(null); worker.shutdown(); indexer.shutdownNow(); network.shutdownNow(); super.onDestroy(); }
}




