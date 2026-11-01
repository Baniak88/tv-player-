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

public class MainActivity extends Activity {
    private static final int FILE_REQUEST = 41;
    private static final String ALL = "Wszystkie kategorie";
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
        boolean wide=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout root=column(); root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v,insets) -> {
            v.setPadding(dp(16)+insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),dp(16)+insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets.consumeSystemWindowInsets();
        });
        // Top bar: back · section switch · sort. No duplicated headings.
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(0,dp(wide?4:10),0,dp(10));
        Button home=Ui.button(this,"‹"); home.setTextSize(26); Ui.ghost(home,Ui.TEXT); home.setContentDescription("Wróć do ekranu głównego");
        top.addView(home,new LinearLayout.LayoutParams(dp(44),dp(44)));
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
        LinearLayout.LayoutParams tabsSize=new LinearLayout.LayoutParams(0,dp(44),1); tabsSize.leftMargin=dp(4); tabsSize.rightMargin=dp(8);
        top.addView(tabs,tabsSize);
        sortButton=Ui.button(this,"⇅"); sortButton.setTextSize(20); Ui.ghost(sortButton,Ui.MUTED); sortButton.setContentDescription("Sortuj bukiety");
        top.addView(sortButton,new LinearLayout.LayoutParams(dp(44),dp(44))); sortButton.setOnClickListener(v -> chooseSort());
        root.addView(top);
        search=input("",false); updateSearchHint();
        LinearLayout.LayoutParams searchSize=new LinearLayout.LayoutParams(-1,dp(48));
        if(wide) { searchSize=new LinearLayout.LayoutParams(dp(240),dp(44)); searchSize.leftMargin=dp(4); top.addView(search,top.getChildCount()-1,searchSize); }
        else root.addView(search,searchSize);
        // Breadcrumb shown only inside a bouquet or favourites; otherwise a quiet summary line.
        crumb=new LinearLayout(this); crumb.setGravity(Gravity.CENTER_VERTICAL); crumb.setPadding(0,dp(8),0,dp(4));
        Button up=Ui.button(this,"‹ Bukiety"); Ui.ghost(up,Ui.MUTED); up.setContentDescription("Wróć do listy bukietów");
        up.setOnClickListener(v -> backToBouquets());
        crumb.addView(up,new LinearLayout.LayoutParams(-2,dp(40)));
        crumbTitle=Ui.text(this,"",17,Ui.TEXT); crumbTitle.setTypeface(null,Typeface.BOLD); crumbTitle.setMaxLines(1); crumbTitle.setEllipsize(TextUtils.TruncateAt.END);
        crumbTitle.setPadding(dp(4),0,0,0); crumb.addView(crumbTitle,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(crumb);
        status=Ui.text(this,"",12,Ui.FAINT); status.setPadding(dp(2),dp(10),0,dp(10)); root.addView(status);
        FrameLayout content=new FrameLayout(this);
        list=new ListView(this); list.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); list.setDividerHeight(dp(6)); list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); list.setClipToPadding(false);
        list.setFastScrollEnabled(true);
        content.addView(list,new FrameLayout.LayoutParams(-1,-1));
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
        LinearLayout dock=new LinearLayout(this); dock.setPadding(dp(3),dp(3),dp(3),dp(3)); dock.setBackground(Ui.shape(this,Ui.SURFACE,14,Ui.BORDER));
        libraryButton=Ui.button(this,"Bukiety"); favoriteButton=Ui.button(this,"☆ Ulubione");
        dock.addView(libraryButton,new LinearLayout.LayoutParams(0,dp(42),1)); dock.addView(favoriteButton,new LinearLayout.LayoutParams(0,dp(42),1));
        Ui.style(libraryButton,!onlyFavorites); Ui.style(favoriteButton,onlyFavorites);
        libraryButton.setOnClickListener(v -> backToBouquets());
        favoriteButton.setOnClickListener(v -> { onlyFavorites=true; showingBouquets=false; selectedGroup=ALL; Ui.style(libraryButton,false); Ui.style(favoriteButton,true); filter(true); });
        LinearLayout.LayoutParams dockSize=new LinearLayout.LayoutParams(-1,-2); dockSize.topMargin=dp(8); dockSize.bottomMargin=dp(12);
        if(wide) { dock.removeView(favoriteButton); favoriteButton.setText("☆"); favoriteButton.setTextSize(20); favoriteButton.setContentDescription("Ulubione"); top.addView(favoriteButton,new LinearLayout.LayoutParams(dp(44),dp(44))); }
        else root.addView(dock,dockSize);
        rows=new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int p) { return shown.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p,View reused,ViewGroup parent) { return row(shown.get(p),reused); }
        };
        list.setAdapter(rows);
        setContentView(root); busy=true;
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int f) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { searchHandler.removeCallbacks(searchTask); searchHandler.postDelayed(searchTask,150); }
            public void afterTextChanged(Editable e) { }
        });
        list.setOnItemClickListener((p, v, pos, id) -> {
            Channel c = shown.get(pos);
            if(c.kind().equals("series")) openSeries(c); else play(c);
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
        if(!favorites.remove(c.url())) favorites.add(c.url());
        persistFavorites();
        // Only the favourites view changes its rows; elsewhere keep the scroll position.
        if(onlyFavorites) filter(false); else { rows.notifyDataSetChanged(); posterAdapter.notifyDataSetChanged(); }
        if(shelves.getVisibility()==View.VISIBLE) shelfAdapter.notifyDataSetChanged();
    }
    private void putSection(String name,List<Channel> items) {
        sectionIndex.put(name,items);
        String[] keys=new String[items.size()];
        for(int i=0;i<keys.length;i++) keys[i]=items.get(i).name().toLowerCase(Locale.ROOT);
        searchKeys.put(name,keys);
    }
    private void loadCachedSection(String requested) {
        busy=true;
        loadingMessage=requested.equals("tv")?"Wczytywanie kanałów…":requested.equals("movie")?"Wczytywanie filmów…":"Wczytywanie seriali…";
        status.setText(loadingMessage); filter();
        worker.execute(() -> {
            try {
                Set<String> savedFavorites=new HashSet<>();
                List<Channel> loaded=store.loadSection(requested,savedFavorites);
                runOnUiThread(() -> {
                    if(isDestroyed()) return;
                    channels.removeIf(c -> c.section().equals(requested)); channels.addAll(loaded); putSection(requested,loaded);
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
    private void chooseSort() {
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
        boolean wide=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
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
            row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12),dp(8),dp(4),dp(8)); row.setMinimumHeight(dp(64));
            row.setBackground(Ui.touch(this,Ui.SURFACE,12,0));
            FrameLayout badge=new FrameLayout(this); badge.setBackground(Ui.shape(this,Ui.SURFACE_HIGH,10,0)); badge.setClipToOutline(true);
            TextView icon=Ui.text(this,"",14,Ui.TEXT); icon.setGravity(Gravity.CENTER); icon.setTypeface(null,Typeface.BOLD);
            badge.addView(icon,new FrameLayout.LayoutParams(-1,-1));
            ImageView logo=new ImageView(this); logo.setScaleType(ImageView.ScaleType.FIT_CENTER); logo.setPadding(dp(4),dp(4),dp(4),dp(4));
            badge.addView(logo,new FrameLayout.LayoutParams(-1,-1));
            row.addView(badge,new LinearLayout.LayoutParams(dp(52),dp(42)));
            LinearLayout text=column(); text.setPadding(dp(12),0,dp(6),0);
            TextView name=Ui.text(this,"",15,Ui.TEXT); name.setMaxLines(1); name.setEllipsize(TextUtils.TruncateAt.END); text.addView(name);
            TextView group=Ui.text(this,"",12,Ui.MUTED); group.setMaxLines(1); group.setEllipsize(TextUtils.TruncateAt.END); group.setPadding(0,dp(3),0,0); text.addView(group);
            TextView next=Ui.text(this,"",11,Ui.FAINT); next.setMaxLines(1); next.setEllipsize(TextUtils.TruncateAt.END); next.setPadding(0,dp(3),0,0); text.addView(next);
            row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
            Button guide=Ui.button(this,"EPG"); guide.setFocusable(false); guide.setTextSize(12); Ui.ghost(guide,Ui.MUTED); row.addView(guide,new LinearLayout.LayoutParams(dp(48),dp(44)));
            Button star=Ui.button(this,"☆"); star.setFocusable(false); star.setTextSize(20); Ui.ghost(star,Ui.FAINT); row.addView(star,new LinearLayout.LayoutParams(dp(44),dp(44)));
        }
        FrameLayout badge=(FrameLayout)row.getChildAt(0); TextView icon=(TextView)badge.getChildAt(0);
        ImageLoader.get(this).load(c.logo(),(ImageView)badge.getChildAt(1),dp(52),dp(42));
        String initial=c.name().replaceAll("^[^\\p{L}\\p{N}]*(?:[A-Z]{2,3}\\s*[|:]\\s*)?","");
        icon.setText(c.kind().equals("tv")?(initial.isEmpty()?"▶":initial.substring(0,Math.min(2,initial.length())).toUpperCase(Locale.ROOT)):c.kind().equals("series")?"▤":"▶");
        LinearLayout text=(LinearLayout)row.getChildAt(1); ((TextView)text.getChildAt(0)).setText(c.name());
        TextView detail=(TextView)text.getChildAt(1),next=(TextView)text.getChildAt(2); detail.setMaxLines(1);
        if(c.kind().equals("tv")) {
            GuideRepository repository=GuideRepository.get(this);
            bindSchedule(detail,next,repository.peek(c,false)); row.setTag(c.url());
            java.lang.ref.WeakReference<TextView> target=new java.lang.ref.WeakReference<>(detail),following=new java.lang.ref.WeakReference<>(next);
            java.lang.ref.WeakReference<LinearLayout> holder=new java.lang.ref.WeakReference<>(row);
            repository.request(c,false,data -> {
                TextView view=target.get(),after=following.get(); LinearLayout owner=holder.get();
                if(view!=null && after!=null && owner!=null && c.url().equals(owner.getTag())) bindSchedule(view,after,data);
            });
        } else { detail.setText(c.group()); next.setVisibility(View.GONE); }
        Button guide=(Button)row.getChildAt(2); guide.setVisibility(c.kind().equals("tv")?View.VISIBLE:View.GONE);
        guide.setContentDescription("Program TV i archiwum: "+c.name());
        guide.setOnClickListener(v -> GuideDialog.open(MainActivity.this,c,network,MainActivity.this::play));
        Button star=(Button)row.getChildAt(3); boolean saved=favorites.contains(c.url()); star.setText(saved?"★":"☆"); star.setTextColor(saved?Ui.STAR:Ui.FAINT);
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
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        boolean grid=showingBouquets && query.isEmpty() && !onlyFavorites;
        List<Channel> items=sectionIndex.getOrDefault(section,Collections.emptyList());
        String[] keys=searchKeys.get(section);
        List<Channel> next = new ArrayList<>();
        if(!grid) {
            boolean all=selectedGroup.equals(ALL) || (showingBouquets && !query.isEmpty());
            for (int i=0;i<items.size();i++) {
                Channel c=items.get(i);
                if (onlyFavorites && !favorites.contains(c.url())) continue;
                if (!all && !selectedGroup.equals(c.group())) continue;
                if (!query.isEmpty() && !(keys!=null && keys.length==items.size()?keys[i]:c.name().toLowerCase(Locale.ROOT)).contains(query)) continue;
                next.add(c);
            }
        }
        shown=next; rows.notifyDataSetChanged(); posterAdapter.notifyDataSetChanged();
        if(resetScroll) { list.setSelection(0); posters.setSelection(0); }
        int total=items.size();
        progress.setVisibility(busy?View.VISIBLE:View.GONE);
        emptyAction.setVisibility(!busy && channels.isEmpty()?View.VISIBLE:View.GONE);
        emptyText.setText(busy?loadingMessage
            :channels.isEmpty()?"Brak źródła\n\nPołącz konto Xtream Codes albo wczytaj listę M3U."
            :total==0?(section.equals("movie")?"Brak filmów\n\nTo źródło nie udostępnia filmów VOD.":section.equals("series")?"Brak seriali\n\nTo źródło nie udostępnia seriali.":"Brak kanałów TV w tym źródle.")
            :onlyFavorites?"Brak ulubionych\n\nDotknij ☆ przy pozycji, aby ją tu zapisać."
            :"Brak wyników dla „"+query+"”");
        boolean inside=!grid && !busy && total>0;
        crumb.setVisibility(inside?View.VISIBLE:View.GONE);
        crumbTitle.setText(!inside?"":onlyFavorites?"Ulubione":!query.isEmpty() && showingBouquets?"Wyniki wyszukiwania":selectedGroup.equals(ALL)?"Cała oferta":selectedGroup);
        boolean vod=vod();
        status.setVisibility(busy || total==0 || (grid && vod)?View.GONE:View.VISIBLE);
        sortButton.setVisibility(grid?View.VISIBLE:View.INVISIBLE);
        bouquets.setVisibility(grid && !busy && total>0 && !vod?View.VISIBLE:View.GONE);
        shelves.setVisibility(grid && !busy && total>0 && vod?View.VISIBLE:View.GONE);
        show(list,rows,!grid && !busy && !shown.isEmpty() && !vod);
        show(posters,posterAdapter,!grid && !busy && !shown.isEmpty() && vod);
        emptyPanel.setVisibility(busy || total==0 || (!grid && shown.isEmpty())?View.VISIBLE:View.GONE);
        if(!busy) status.setText(grid?Ui.count(bouquetCounts.size())+" bukietów · "+Ui.count(total)+" pozycji"+(syncingOrder?" · aktualizuję kolejność…":"")
            :Ui.count(shown.size())+(shown.size()==1?" pozycja":" pozycji"));
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
        View shade=new View(this); shade.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0x660E0F11,0xF00E0F11}));
        hero.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout heroText=column(); heroText.setPadding(dp(16),0,dp(16),dp(16));
        heroMeta=Ui.text(this,"",12,Ui.MUTED); heroMeta.setMaxLines(1); heroMeta.setEllipsize(TextUtils.TruncateAt.END); heroText.addView(heroMeta);
        heroTitle=Ui.text(this,"",22,Ui.TEXT); heroTitle.setTypeface(null,Typeface.BOLD); heroTitle.setMaxLines(2); heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        heroTitle.setPadding(0,dp(2),0,dp(12)); heroText.addView(heroTitle);
        LinearLayout heroButtons=new LinearLayout(this);
        heroPlay=Ui.button(this,"▶  Odtwórz"); Ui.primary(heroPlay); heroPlay.setTextSize(15);
        heroPlay.setOnClickListener(v -> { if(heroItem!=null) open(heroItem); });
        heroButtons.addView(heroPlay,new LinearLayout.LayoutParams(0,dp(44),1));
        heroSave=Ui.button(this,"＋  Moja lista"); heroSave.setTextColor(Ui.TEXT); heroSave.setBackground(Ui.touch(this,0x33FFFFFF,12,0));
        heroSave.setOnClickListener(v -> { if(heroItem!=null) { toggleFavorite(heroItem); bindHero(); } });
        LinearLayout.LayoutParams saveSize=new LinearLayout.LayoutParams(0,dp(44),1); saveSize.leftMargin=dp(10); heroButtons.addView(heroSave,saveSize);
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
    private void open(Channel c) { if(c.kind().equals("series")) openSeries(c); else play(c); }
    private void prepareShelves(List<Channel> items) {
        shelfItems.clear();
        for(Channel c:items) { List<Channel> row=shelfItems.computeIfAbsent(c.group(),key -> new ArrayList<>()); if(row.size()<24) row.add(c); }
        shelfNames=new ArrayList<>(bouquetCounts.keySet());
        heroItem=null;
        if(hasCovers(items)) {
            // Prefer a "recently added" style bouquet, otherwise the first one with artwork.
            List<String> order=new ArrayList<>(shelfNames);
            order.sort(Comparator.comparingInt(name -> name.toLowerCase(Locale.ROOT).matches(".*(ostatnio|nowo|nowości|nowosci|new|recent|latest|2026|2025).*")?0:1));
            for(String name:order) { for(Channel c:shelfItems.get(name)) if(!c.logo().isEmpty()) { heroItem=c; break; } if(heroItem!=null) break; }
        }
        bindHero();
        coverHint.setVisibility(!items.isEmpty() && heroItem==null?View.VISIBLE:View.GONE);
        shelves.setAdapter(shelfAdapter);
    }
    private void bindHero() {
        hero.setVisibility(heroItem==null?View.GONE:View.VISIBLE);
        if(heroItem==null) { heroTitle.setText(""); heroMeta.setText(""); heroImage.setImageDrawable(null); return; }
        heroTitle.setText(heroItem.name()); heroMeta.setText(heroItem.group().toUpperCase(Locale.ROOT));
        heroPlay.setText(heroItem.kind().equals("series")?"▶  Odcinki":"▶  Odtwórz");
        boolean saved=favorites.contains(heroItem.url()); heroSave.setText(saved?"✓  Moja lista":"＋  Moja lista");
        ImageLoader.get(this).load(heroItem.logo(),heroImage,getResources().getDisplayMetrics().widthPixels,dp(300));
    }
    private View shelf(String name,View reused) {
        LinearLayout row;
        if(reused instanceof LinearLayout) row=(LinearLayout)reused;
        else {
            row=column(); row.setPadding(0,dp(10),0,dp(6));
            LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(dp(2),0,dp(2),dp(8));
            TextView title=Ui.text(this,"",16,Ui.TEXT); title.setTypeface(null,Typeface.BOLD); title.setMaxLines(1); title.setEllipsize(TextUtils.TruncateAt.END);
            head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            TextView more=Ui.text(this,"",12,Ui.MUTED); head.addView(more);
            row.addView(head);
            HorizontalScrollView scroller=new HorizontalScrollView(this); scroller.setHorizontalScrollBarEnabled(false); scroller.setClipToPadding(false);
            LinearLayout strip=new LinearLayout(this); scroller.addView(strip); row.addView(scroller);
        }
        LinearLayout head=(LinearLayout)row.getChildAt(0);
        ((TextView)head.getChildAt(0)).setText(name);
        Integer total=bouquetCounts.get(name);
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
            TextView badge=Ui.text(this,"★",12,Ui.STAR); badge.setGravity(Gravity.CENTER); badge.setBackground(Ui.shape(this,0xCC0E0F11,12,0));
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
        startActivity(new Intent(this,PlayerActivity.class).putExtra("url",c.url()).putExtra("name",c.name()).putExtra("kind",c.kind()).putExtra("guide",c.guideUrl()).putExtra("days",c.archiveDays()).putExtra("timezone",c.timezone()));
    }
    /** Episodes already fetched in this process; reopening a series is then instant. */
    private static final Map<String,List<Channel>> EPISODES=java.util.Collections.synchronizedMap(new LinkedHashMap<String,List<Channel>>(16,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,List<Channel>> eldest) { return size()>40; }
    });
    private void openSeries(Channel series) {
        android.app.ProgressDialog dialog=new android.app.ProgressDialog(this);
        dialog.setMessage("Wczytywanie odcinków…"); dialog.setCancelable(true);
        if(!EPISODES.containsKey(series.url())) dialog.show();
        network.execute(() -> {
            try {
                List<Channel> cached=EPISODES.get(series.url());
                List<Channel> episodes=cached!=null?cached:new SourceClient().episodes(series);
                if(cached==null && !episodes.isEmpty()) EPISODES.put(series.url(),episodes);
                runOnUiThread(() -> {
                    if(isDestroyed() || (cached==null && !dialog.isShowing())) return;
                    dialog.dismiss();
                    if(episodes.isEmpty()) { error("Dostawca nie udostępnił odcinków tego serialu."); return; }
                    java.util.TreeMap<String,List<Channel>> seasons=new java.util.TreeMap<>((left,right) -> {
                        try { return Integer.compare(Integer.parseInt(left.replace("Sezon ","")),Integer.parseInt(right.replace("Sezon ",""))); }
                        catch(NumberFormatException e) { return left.compareTo(right); }
                    });
                    for(Channel episode:episodes) seasons.computeIfAbsent(episode.group(),key -> new ArrayList<>()).add(episode);
                    List<String> names=new ArrayList<>(seasons.keySet());
                    new AlertDialog.Builder(this).setTitle(series.name()).setItems(names.toArray(new String[0]),(d,index) -> {
                        List<Channel> selected=seasons.get(names.get(index)); String[] labels=new String[selected.size()];
                        for(int i=0;i<selected.size();i++) labels[i]=selected.get(i).name();
                        new AlertDialog.Builder(this).setTitle(names.get(index)).setItems(labels,(e,n) -> play(selected.get(n))).setNegativeButton("Zamknij",null).show();
                    }).setNegativeButton("Zamknij",null).show();
                });
            } catch(Exception e) {
                runOnUiThread(() -> { if(!isDestroyed() && dialog.isShowing()) { dialog.dismiss(); error("Nie udało się pobrać odcinków. Sprawdź połączenie i dostępność serialu."); } });
            }
        });
    }
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
    private void error(String message) { if (!isDestroyed()) new AlertDialog.Builder(this).setTitle("TV Player").setMessage(message).setPositiveButton("OK", null).show(); }
    @Override protected void onResume() { super.onResume(); searchHandler.removeCallbacks(epgTick); searchHandler.postDelayed(epgTick,60000); }
    @Override protected void onPause() { searchHandler.removeCallbacks(epgTick); super.onPause(); }
    @Override protected void onDestroy() { searchHandler.removeCallbacksAndMessages(null); worker.shutdown(); network.shutdownNow(); super.onDestroy(); }
}
