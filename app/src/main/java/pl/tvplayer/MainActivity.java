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
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private List<Channel> channels = new ArrayList<>(), shown = new ArrayList<>();
    private final Set<String> loadedSections = new HashSet<>();
    private final Map<String,List<Channel>> sectionIndex = new HashMap<>();
    private String loadingMessage = "Odczyt zapisanych kanałów TV…";
    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable searchTask = () -> filter();
    private final Set<String> favorites = new HashSet<>();
    private LibraryStore store;
    private TextView status;
    private EditText search;
    private GridView bouquets;
    private TextView catalogTitle;
    private Button bouquetButton;
    private LinearLayout emptyPanel;
    private boolean showingBouquets=true;
    private List<String> bouquetNames=new ArrayList<>();
    private final Map<String,Integer> bouquetCounts=new TreeMap<>();
    private ListView list;
    private Button favoriteButton, addButton;
    private boolean onlyFavorites, busy;
    private TextView emptyText;
    private ProgressBar progress;
    private Button libraryButton;
    private String section = "tv";
    private final java.util.Map<String, Button> sectionButtons = new java.util.HashMap<>();
    private String selectedGroup = "Wszystkie kategorie";

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
        LinearLayout root=column(); root.setBackground(new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{0xFF0B1124,0xFF180B19,0xFF09090F}));
        root.setOnApplyWindowInsetsListener((v,insets) -> {
            v.setPadding(dp(16),insets.getSystemWindowInsetTop(),dp(16),insets.getSystemWindowInsetBottom()); return insets.consumeSystemWindowInsets();
        });
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(0,dp(wide?0:12),0,dp(wide?0:12));
        Button home=button("‹ Główna"); top.addView(home,new LinearLayout.LayoutParams(dp(100),dp(44)));
        home.setOnClickListener(v -> { startActivity(new Intent(this,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish(); });
        TextView brand=label("TV PLAYER",16); brand.setTypeface(null,Typeface.BOLD); brand.setGravity(Gravity.END);
        top.addView(brand,new LinearLayout.LayoutParams(0,-2,1)); brand.setVisibility(wide?View.GONE:View.VISIBLE); root.addView(top);
        TextView title=label("Twoje bukiety",26); catalogTitle=title; title.setTypeface(null,Typeface.BOLD); root.addView(title); title.setVisibility(wide?View.GONE:View.VISIBLE);
        TextView sub=label("Wybierz temat, potem to, co chcesz oglądać.",13); sub.setTextColor(0xFFBA9CAA); sub.setPadding(0,dp(5),0,dp(16)); root.addView(sub); sub.setVisibility(wide?View.GONE:View.VISIBLE);
        LinearLayout tabs=new LinearLayout(this);
        String[][] sections={{"tv","TV"},{"series","Seriale"},{"movie","Filmy"}};
        for(String[] entry:sections) {
            Button tab=button(entry[1]); sectionButtons.put(entry[0],tab);
            tabs.addView(tab,new LinearLayout.LayoutParams(0,dp(46),1));
            primary(tab,entry[0].equals(section));
            tab.setOnClickListener(v -> { if(busy) return; section=entry[0]; onlyFavorites=false; showingBouquets=true; selectedGroup="Wszystkie kategorie";
                primary(libraryButton,true); primary(favoriteButton,false);
                for(java.util.Map.Entry<String,Button> item:sectionButtons.entrySet()) primary(item.getValue(),item.getKey().equals(section));
                search.setText(""); if(loadedSections.contains(section)) refreshGroups(); else loadCachedSection(section); });
        }
        LinearLayout.LayoutParams tabSize=new LinearLayout.LayoutParams(-1,-2); tabSize.bottomMargin=dp(12); if(wide) top.addView(tabs,new LinearLayout.LayoutParams(0,dp(44),1)); else root.addView(tabs,tabSize);
        search=input("Szukaj w wybranej sekcji…",false); if(!wide) root.addView(search);
        LinearLayout tools=new LinearLayout(this); tools.setGravity(Gravity.CENTER_VERTICAL);
        if(wide) tools.addView(search,new LinearLayout.LayoutParams(0,dp(46),1));
        bouquetButton=button("▦ Bukiety"); bouquetButton.setMaxLines(1); bouquetButton.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tools.addView(bouquetButton,new LinearLayout.LayoutParams(0,dp(46),1));
        bouquetButton.setOnClickListener(v -> { showingBouquets=true; selectedGroup="Wszystkie kategorie"; search.setText(""); filter(); });
        addButton=button("＋ Źródło"); primary(addButton,true); LinearLayout.LayoutParams sourceSize=new LinearLayout.LayoutParams(dp(118),dp(46)); sourceSize.leftMargin=dp(10); tools.addView(addButton,sourceSize); root.addView(tools);
        addButton.setOnClickListener(v -> sourceMenu());
        status=label("Wczytywanie biblioteki…",12); status.setTextColor(0xFFB195A3); status.setPadding(dp(2),dp(12),0,dp(10)); root.addView(status);
        FrameLayout content=new FrameLayout(this);
        list=new ListView(this); list.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); list.setDividerHeight(dp(8)); list.setSelector(new android.graphics.drawable.ColorDrawable(0x19D62B4D)); list.setClipToPadding(false);
        content.addView(list,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout empty=column(); emptyPanel=empty; empty.setGravity(Gravity.CENTER); empty.setPadding(dp(20),dp(12),dp(20),dp(12));
        TextView symbol=label("▣",54); symbol.setTextColor(0xFFA51B37); empty.addView(symbol);
        emptyText=label("Wczytywanie biblioteki…",17); emptyText.setGravity(Gravity.CENTER); emptyText.setTextColor(0xFFB195A3); emptyText.setPadding(0,dp(12),0,dp(16)); empty.addView(emptyText);
        progress=new ProgressBar(this); progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xFFA51B37)); empty.addView(progress,new LinearLayout.LayoutParams(dp(30),dp(30)));
        content.addView(empty,new FrameLayout.LayoutParams(-1,-1)); list.setEmptyView(empty);
        bouquets=new GridView(this); bouquets.setNumColumns(2); bouquets.setHorizontalSpacing(dp(10)); bouquets.setVerticalSpacing(dp(10)); bouquets.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        bouquets.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); bouquets.setClipToPadding(false);
        bouquets.setOnItemClickListener((parent,v,index,id) -> { if(busy) return; selectedGroup=bouquetNames.get(index); showingBouquets=false; filter(); });
        content.addView(bouquets,new FrameLayout.LayoutParams(-1,-1)); root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout dock=new LinearLayout(this); dock.setPadding(dp(5),dp(5),dp(5),dp(5)); dock.setBackground(surface(0xFF160F1E,5,0xFF552335)); dock.setElevation(dp(3));
        libraryButton=button("▦ Bukiety"); favoriteButton=button("☆ Ulubione"); dock.addView(libraryButton,new LinearLayout.LayoutParams(0,dp(46),1)); dock.addView(favoriteButton,new LinearLayout.LayoutParams(0,dp(46),1)); primary(libraryButton,true);
        libraryButton.setOnClickListener(v -> { onlyFavorites=false; showingBouquets=true; selectedGroup="Wszystkie kategorie"; primary(libraryButton,true); primary(favoriteButton,false); filter(); });
        favoriteButton.setOnClickListener(v -> { onlyFavorites=true; showingBouquets=false; selectedGroup="Wszystkie kategorie"; primary(libraryButton,false); primary(favoriteButton,true); filter(); });
        LinearLayout.LayoutParams dockSize=new LinearLayout.LayoutParams(-1,-2); dockSize.topMargin=dp(10); dockSize.bottomMargin=dp(14); if(wide) { dock.removeView(favoriteButton); favoriteButton.setText("☆"); top.addView(favoriteButton,new LinearLayout.LayoutParams(dp(48),dp(44))); } else root.addView(dock,dockSize);
        setContentView(root); busy=true; addButton.setEnabled(false);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int f) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { searchHandler.removeCallbacks(searchTask); searchHandler.postDelayed(searchTask,180); }
            public void afterTextChanged(Editable e) { }
        });
        list.setOnItemClickListener((p, v, pos, id) -> {
            Channel c = shown.get(pos);
            if(c.kind().equals("series")) openSeries(c); else play(c);
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            String url = shown.get(pos).url();
            if (!favorites.remove(url)) favorites.add(url);
            filter(); persistFavorites(); return true;
        });
        if(state!=null) search.setText(state.getString("query",""));
        loadCachedSection(section);
    }
    private void loadCachedSection(String requested) {
        busy=true; addButton.setEnabled(false);
        loadingMessage=requested.equals("tv")?"Odczyt zapisanych kanałów TV…":requested.equals("movie")?"Odczyt zapisanych filmów…":"Odczyt zapisanych seriali…";
        status.setText(loadingMessage); filter();
        worker.execute(() -> {
            try {
                Set<String> savedFavorites=new HashSet<>();
                List<Channel> loaded=store.loadSection(requested,savedFavorites);
                runOnUiThread(() -> {
                    if(isDestroyed()) return;
                    channels.removeIf(c -> c.section().equals(requested)); channels.addAll(loaded); sectionIndex.put(requested,loaded);
                    if(loadedSections.isEmpty()) { favorites.clear(); favorites.addAll(savedFavorites); }
                    loadedSections.add(requested); busy=false; addButton.setEnabled(true); refreshGroups();
                    if(getIntent().getBooleanExtra("open_source",false)) { getIntent().removeExtra("open_source"); sourceMenu(); }
                });
            } catch(Exception e) {
                runOnUiThread(() -> {
                    if(isDestroyed()) return; busy=false; addButton.setEnabled(true); refreshGroups();
                    error("Nie można odczytać zapisanej sekcji. Dodaj ponownie źródło, jeśli problem się powtórzy.");
                });
            }
        });
    }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private android.graphics.drawable.GradientDrawable surface(int color,int radius,int border) {
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(radius)); bg.setStroke(dp(1),border); return bg;
    }
    private TextView label(String text,int size) { TextView t=new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(0xFFF5E9ED); return t; }
    private Button button(String text) { Button b=new Button(this); b.setText(text); b.setTextSize(14); b.setAllCaps(false); b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(dp(44)); b.setMinimumHeight(dp(44)); b.setPadding(dp(10),0,dp(10),0); b.setStateListAnimator(null); primary(b,false); return b; }
    private void primary(Button b,boolean selected) { b.setTextColor(selected?Color.WHITE:0xFFBFA1AE); b.setBackground(surface(selected?0xFFA51B37:0xFF241220,5,selected?0xFFC64660:0xFF542438)); }
    private EditText input(String hint,boolean password) {
        EditText e=new EditText(this); e.setHint(hint); e.setSingleLine(true); e.setTextSize(16); e.setTextColor(0xFFF5E9ED); e.setHintTextColor(0xFF9F8291);
        e.setPadding(dp(16),dp(12),dp(16),dp(12)); e.setBackground(surface(0xFF1A1222,5,0xFF5A2B3C)); e.setMinHeight(dp(50));
        e.setInputType(password ? android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD : android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2); size.bottomMargin=dp(10); e.setLayoutParams(size); return e;
    }
    private ScrollView scroll(LinearLayout form) { ScrollView s=new ScrollView(this); s.addView(form); return s; }
    private void refreshGroups() {
        bouquetCounts.clear();
        List<Channel> items=sectionIndex.getOrDefault(section,Collections.emptyList());
        for(Channel c:items) bouquetCounts.put(c.group(),bouquetCounts.getOrDefault(c.group(),0)+1);
        bouquetNames=new ArrayList<>(); bouquetNames.add("Wszystkie kategorie"); bouquetNames.addAll(bouquetCounts.keySet());
        if(!bouquetNames.contains(selectedGroup)) selectedGroup="Wszystkie kategorie";
        bouquets.setAdapter(new BaseAdapter() {
            public int getCount() { return bouquetNames.size(); }
            public Object getItem(int p) { return bouquetNames.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p,View reused,ViewGroup parent) {
                LinearLayout card;
                if(reused instanceof LinearLayout) card=(LinearLayout)reused;
                else {
                    card=column(); card.setPadding(dp(14),dp(14),dp(10),dp(12)); card.setBackground(surface(0xFF35101F,5,0xFF713044));
                    card.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,dp(getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?84:106)));
                    TextView title=label("",17); title.setTypeface(null,Typeface.BOLD); title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    card.addView(title,new LinearLayout.LayoutParams(-1,0,1));
                    TextView count=label("",12); count.setTextColor(0xFFD69DAB); card.addView(count);
                }
                String name=bouquetNames.get(p);
                ((TextView)card.getChildAt(0)).setText(p==0?"Cała oferta":name);
                ((TextView)card.getChildAt(1)).setText((p==0?items.size():bouquetCounts.get(name))+(section.equals("tv")?" kanałów  ›":" pozycji  ›"));
                card.setContentDescription("Bukiet "+name); return card;
            }
        });
        filter();
    }
    private void filter() {
        if (list == null) return;
        String query = search.getText().toString().toLowerCase(Locale.ROOT);
        boolean grid=showingBouquets && query.isEmpty() && !onlyFavorites;
        shown = new ArrayList<>();
        if(!grid) for (Channel c : sectionIndex.getOrDefault(section,Collections.emptyList())) if ((!onlyFavorites || favorites.contains(c.url()))
                && (selectedGroup.equals("Wszystkie kategorie") || selectedGroup.equals(c.group()))
                && c.name().toLowerCase(Locale.ROOT).contains(query)) shown.add(c);
        list.setAdapter(new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int p) { return shown.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p,View reused,ViewGroup parent) {
                Channel c=shown.get(p); LinearLayout row;
                if(reused instanceof LinearLayout) row=(LinearLayout)reused;
                else {
                    row=new LinearLayout(MainActivity.this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(14),dp(12),dp(10),dp(12)); row.setBackground(surface(0xFF211223,5,0xFF552335));
                    TextView icon=label("▶",17); icon.setTextColor(0xFFA51B37); icon.setGravity(Gravity.CENTER); icon.setBackground(surface(0xFF4F1428,12,0xFF4F1428)); row.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(44)));
                    LinearLayout text=column(); text.setPadding(dp(12),0,dp(8),0); TextView name=label("",16); name.setTypeface(null,Typeface.BOLD); name.setMaxLines(1); name.setEllipsize(android.text.TextUtils.TruncateAt.END); text.addView(name);
                    TextView group=label("",12); group.setTextColor(0xFFBA9AA9); group.setMaxLines(1); group.setEllipsize(android.text.TextUtils.TruncateAt.END); group.setPadding(0,dp(5),0,0); text.addView(group); row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
                    Button star=button("☆"); star.setFocusable(false); row.addView(star,new LinearLayout.LayoutParams(dp(48),dp(48)));
                    Button guide=button("EPG"); guide.setFocusable(false); row.addView(guide,new LinearLayout.LayoutParams(dp(48),dp(48)));
                }
                LinearLayout text=(LinearLayout)row.getChildAt(1); ((TextView)text.getChildAt(0)).setText(c.name()); ((TextView)text.getChildAt(1)).setText(c.group());
                Button star=(Button)row.getChildAt(2); boolean saved=favorites.contains(c.url()); star.setText(saved?"★":"☆"); star.setTextSize(23); star.setTextColor(saved?0xFFFFA000:0xFF9F8291);
                star.setContentDescription((saved?"Usuń z ulubionych: ":"Dodaj do ulubionych: ")+c.name());
                star.setOnClickListener(v -> { if(!favorites.remove(c.url())) favorites.add(c.url()); filter(); persistFavorites(); });
                Button guide=(Button)row.getChildAt(3); guide.setVisibility(c.kind().equals("tv")?View.VISIBLE:View.GONE);
                guide.setContentDescription("Program TV i archiwum: "+c.name());
                guide.setOnClickListener(v -> GuideDialog.open(MainActivity.this,c,worker,MainActivity.this::play));
                return row;
            }
        });
        progress.setVisibility(busy?View.VISIBLE:View.GONE);
        emptyText.setText(busy?loadingMessage
            :channels.isEmpty()?"Dodaj swoją telewizję\n\nWybierz ＋ Źródło i połącz konto Xtream Codes lub listę M3U."
            :onlyFavorites?"Jeszcze nic tutaj nie ma\n\nDotknij ☆ obok kanału, aby zapisać go w ulubionych."
            :"Brak wyników\n\nZmień nazwę lub kategorię. Jeśli katalog jest pusty, zaimportuj konto Xtream ponownie.");
        catalogTitle.setText(grid?(section.equals("tv")?"TV · Bukiety":section.equals("movie")?"Filmy · Bukiety":"Seriale · Bukiety")
            :onlyFavorites?"Ulubione":selectedGroup.equals("Wszystkie kategorie")?"Cała oferta":selectedGroup);
        int total=sectionIndex.getOrDefault(section,Collections.emptyList()).size();
        bouquets.setVisibility(grid && !busy && total>0?View.VISIBLE:View.GONE);
        list.setVisibility(!grid && !busy && !shown.isEmpty()?View.VISIBLE:View.GONE);
        emptyPanel.setVisibility(busy || total==0 || (!grid && shown.isEmpty())?View.VISIBLE:View.GONE);
        bouquetButton.setText(grid?"▦ Bukiety ("+bouquetCounts.size()+")":"‹ Bukiety");
        if(!busy) status.setText(total==0?"Brak pozycji w tej sekcji. Dodaj lub zmień źródło."
            :grid?bouquetCounts.size()+" bukietów  ·  "+total+" pozycji"
            :(selectedGroup.equals("Wszystkie kategorie")?"Cała oferta":selectedGroup)+"  ·  "+shown.size()+" pozycji");
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("section",section); out.putString("bouquet",selectedGroup); out.putBoolean("showingBouquets",showingBouquets);
        out.putBoolean("onlyFavorites",onlyFavorites); out.putString("query",search.getText().toString()); super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed() {
        if(!showingBouquets || !search.getText().toString().isEmpty() || onlyFavorites) {
            showingBouquets=true; onlyFavorites=false; selectedGroup="Wszystkie kategorie"; search.setText("");
            primary(libraryButton,true); primary(favoriteButton,false); filter();
        } else super.onBackPressed();
    }
    private void play(Channel c) {
        startActivity(new Intent(this,PlayerActivity.class).putExtra("url",c.url()).putExtra("name",c.name()).putExtra("kind",c.kind()).putExtra("guide",c.guideUrl()).putExtra("days",c.archiveDays()).putExtra("timezone",c.timezone()));
    }
    private void openSeries(Channel series) {
        android.app.ProgressDialog dialog=new android.app.ProgressDialog(this);
        dialog.setMessage("Wczytywanie sezonów i odcinków…"); dialog.setCancelable(true); dialog.show();
        worker.execute(() -> {
            try {
                List<Channel> episodes=new SourceClient().episodes(series);
                runOnUiThread(() -> {
                    if(isDestroyed() || !dialog.isShowing()) return;
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
        new AlertDialog.Builder(this).setTitle("Dodaj źródło")
                .setItems(new String[]{"M3U / M3U8 — adres URL", "M3U / M3U8 — plik z telefonu", "Xtream Codes — serwer i konto", "Usuń bibliotekę"}, (d, which) -> {
                    if (which == 0) m3uDialog();
                    if (which == 1) startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), FILE_REQUEST);
                    if (which == 2) xtreamDialog();
                    if (which == 3) new AlertDialog.Builder(this).setMessage("Usunąć zapisane kanały i ulubione z telefonu?")
                            .setNegativeButton("Anuluj", null).setPositiveButton("Usuń", (a, b) -> { channels.clear(); sectionIndex.clear(); favorites.clear(); loadedSections.addAll(Arrays.asList("tv","movie","series")); persist(); refreshGroups(); }).show();
                }).show();
    }
    private LinearLayout form() { LinearLayout form = column(); form.setPadding(dp(20), dp(8), dp(20), 0); return form; }
    private void m3uDialog() {
        LinearLayout form = form(); EditText url = input("https://serwer/lista.m3u", false); url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); form.addView(url);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Lista M3U z internetu").setView(scroll(form))
                .setMessage("Import zastąpi bieżącą listę kanałów.").setNegativeButton("Anuluj", null).setPositiveButton("Wczytaj", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            try { SourceClient.validate(url.getText().toString()); }
            catch (Exception e) { url.setError("Podaj poprawny adres HTTP lub HTTPS"); return; }
            String address = url.getText().toString(); dialog.dismiss(); load(() -> new SourceClient().m3u(address));
        })); dialog.show();
    }
    private void xtreamDialog() {
        LinearLayout form = form(); EditText server = input("Serwer: https://dostawca:port", false), user = input("Nazwa użytkownika", false), pass = input("Hasło", true);
        server.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); form.addView(server); form.addView(user); form.addView(pass);
        form.addView(label("Import TV, filmów i seriali zastąpi bieżącą bibliotekę.", 13));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Xtream Codes").setView(scroll(form)).setNegativeButton("Anuluj", null).setPositiveButton("Połącz", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            try { SourceClient.validate(server.getText().toString()); } catch (Exception e) { server.setError("Podaj adres HTTP lub HTTPS"); return; }
            if (user.getText().toString().trim().isEmpty() || pass.getText().length() == 0) { pass.setError("Uzupełnij login i hasło"); return; }
            String address = server.getText().toString(), username = user.getText().toString().trim(), password = pass.getText().toString();
            dialog.dismiss(); load(() -> new SourceClient().xtream(address, username, password));
        })); dialog.show();
    }
    private void load(Callable<List<Channel>> task) {
        busy = true; addButton.setEnabled(false); loadingMessage="Pobieranie katalogu od dostawcy…"; status.setText(loadingMessage); filter();
        worker.execute(() -> {
            try {
                List<Channel> result = task.call();
                if (result.isEmpty()) throw new java.io.IOException("Źródło nie zawiera obsługiwanych kanałów HTTP/HTTPS.");
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    channels = result;
                    sectionIndex.clear();
                    for(Channel c:result) sectionIndex.computeIfAbsent(c.section(),key -> new ArrayList<>()).add(c);
                    loadedSections.addAll(Arrays.asList("tv","movie","series"));
                    Set<String> urls = new HashSet<>(); for (Channel c : channels) urls.add(c.url()); favorites.retainAll(urls);
                    busy = false; addButton.setEnabled(true); refreshGroups(); persist();
                });
            } catch (Exception e) {
                String message = e instanceof java.io.IOException || e instanceof IllegalArgumentException ? e.getMessage() : "Sprawdź adres, połączenie i dane konta.";
                // Do not expose exception URLs: they can contain credentials.
                if (message == null || message.contains("http") || message.length() > 180) message = "Nie udało się połączyć. Sprawdź adres, połączenie i dane konta.";
                final String safe = message;
                runOnUiThread(() -> { if (!isDestroyed()) { busy = false; addButton.setEnabled(true); filter(); error(safe); } });
            }
        });
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == FILE_REQUEST && result == RESULT_OK && data != null && data.getData() != null) {
            android.net.Uri uri = data.getData();
            load(() -> {
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
    @Override protected void onDestroy() { searchHandler.removeCallbacksAndMessages(null); worker.shutdown(); super.onDestroy(); }
}
