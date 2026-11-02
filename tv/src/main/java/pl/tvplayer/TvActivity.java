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
    private boolean home=true,onlyFavorites,busy;
    private TvAccountStore.Account account;
    private LinearLayout page,body;
    private TextView status,heading;
    private ListView bouquets,list;
    private GridView posters;
    private BaseAdapter rows,covers;
    private int generation;
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
        TextView brand=text("TV PLAYER",19,Ui.TEXT); brand.setTypeface(null,Typeface.BOLD); header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextClock clock=new TextClock(this); clock.setTextColor(Ui.MUTED); clock.setTextSize(18); clock.setFormat24Hour("HH:mm"); clock.setFormat12Hour("HH:mm"); header.addView(clock);
        page.addView(header,new LinearLayout.LayoutParams(-1,dp(42)));
        body=column(); page.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        status=text("",15,Ui.MUTED); status.setPadding(0,dp(10),0,0); page.addView(status,new LinearLayout.LayoutParams(-1,dp(38)));
        setContentView(page);
        if(home) showHome(); else showCatalog();
    }
    private void showHome() {
        home=true; body.removeAllViews();
        heading=text("Co oglądamy?",32,Ui.TEXT); heading.setTypeface(null,Typeface.BOLD); heading.setPadding(0,dp(18),0,dp(24)); body.addView(heading);
        LinearLayout tiles=new LinearLayout(this); int height=dp(240);
        Button live=button("▣\n\nTelewizja na żywo",() -> open("tv")); live.setTextSize(25);
        LinearLayout.LayoutParams large=new LinearLayout.LayoutParams(0,height,1.2f); large.rightMargin=dp(18); tiles.addView(live,large);
        LinearLayout middle=column();
        Button films=button("▶  Filmy",() -> open("movie")),series=button("▤  Seriale",() -> open("series"));
        live.setId(View.generateViewId()); films.setId(View.generateViewId()); series.setId(View.generateViewId());
        live.setNextFocusRightId(films.getId()); films.setNextFocusLeftId(live.getId()); series.setNextFocusLeftId(live.getId());
        films.setNextFocusDownId(series.getId()); series.setNextFocusUpId(films.getId());
        LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(-1,0,1); cell.bottomMargin=dp(18); middle.addView(films,cell); middle.addView(series,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout.LayoutParams mid=new LinearLayout.LayoutParams(0,height,1); mid.rightMargin=dp(18); tiles.addView(middle,mid);
        LinearLayout actions=column();
        for(String label:new String[]{"Dodaj / zmień źródło","Odśwież bibliotekę","Informacje","Wyjście"}) {
            Button action=button(label,() -> { if(label.startsWith("Dodaj")) sourceDialog(); else if(label.startsWith("Odśwież")) refresh(); else if(label.equals("Wyjście")) finish(); else info(); });
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,0,1); size.bottomMargin=dp(10); actions.addView(action,size);
        }
        tiles.addView(actions,new LinearLayout.LayoutParams(0,height,1.1f)); body.addView(tiles);
        status.setText(account==null?"Dodaj konto Xtream lub adres listy M3U, aby rozpocząć.":"OK — wybierz · Strzałki — nawigacja · Wstecz — powrót"); live.requestFocus(); live.post(() -> { if(home && !isFinishing()) live.requestFocus(); });
    }
    private void open(String kind) { if(busy) return; section=kind; group=""; query=""; seriesTitle=""; onlyFavorites=false; items=new ArrayList<>(); home=false; showCatalog(); load(kind); }
    private void load(String kind) {
        if(busy) return; final int request=++generation; busy=true; status.setText("Wczytywanie biblioteki…");
        worker.execute(() -> {
            try {
                List<Channel> loaded=catalogs.get(kind);
                if(loaded==null) loaded=new LibraryStore(this).loadSection(kind,favorites);
                if(account!=null && account.type().equals("xtream") && !getPreferences(MODE_PRIVATE).getBoolean("loaded-"+kind,false)) {
                    loaded=source.xtreamSection(account.server(),account.user(),account.password(),kind,this::progress);
                    LibraryStore store=new LibraryStore(this); store.updateSection(kind,loaded);
                    getPreferences(MODE_PRIVATE).edit().putBoolean("loaded-"+kind,true).apply();
                }
                final List<Channel> result=loaded; catalogs.put(kind,result);
                runOnUiThread(() -> { if(isDestroyed() || request!=generation) return; busy=false; items=result; rebuildGroups(); });
            } catch(Exception e) { runOnUiThread(() -> { if(!isDestroyed()) { busy=false; status.setText("Nie udało się wczytać biblioteki."); error("Nie można pobrać katalogu. Sprawdź połączenie i ważność źródła."); } }); }
        });
    }
    private void progress(String value) { runOnUiThread(() -> { if(!isDestroyed()) status.setText(value); }); }
    private void showCatalog() {
        home=false; body.removeAllViews();
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        String[] labels={"‹ Główna","TV","Filmy","Seriale","Szukaj","☆ Ulubione","Sortuj","Źródło"};
        for(int i=0;i<labels.length;i++) {
            final int index=i;
            Button b=button(labels[i],() -> { if(index==0) { if(!busy) showHome(); } else if(index>=1 && index<=3) open(new String[]{"tv","movie","series"}[index-1]); else if(index==4) search(); else if(index==5) { onlyFavorites=!onlyFavorites; filter(); } else if(index==6) sort(); else sourceDialog(); });
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(48),i==0?1.25f:1); size.rightMargin=dp(6); bar.addView(b,size);
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
            public View getView(int p,View reused,ViewGroup parent) { return channelRow(shown.get(p)); }
        }; list.setAdapter(rows); catalog.addView(list,new FrameLayout.LayoutParams(-1,-1));
        posters=new GridView(this); posters.setNumColumns(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density>=1100?5:4); posters.setHorizontalSpacing(dp(14)); posters.setVerticalSpacing(dp(14)); posters.setSelector(Ui.shape(this,0xFF303640,12,Ui.ACCENT));
        covers=new BaseAdapter() {
            public int getCount() { return shown.size(); } public Object getItem(int p) { return shown.get(p); } public long getItemId(int p) { return p; }
            public View getView(int p,View reused,ViewGroup parent) { return poster(shown.get(p)); }
        }; posters.setDrawSelectorOnTop(true); posters.setSelector(Ui.shape(this,0x00000000,12,Ui.ACCENT)); posters.setAdapter(covers); catalog.addView(posters,new FrameLayout.LayoutParams(-1,-1));
        AdapterView.OnItemClickListener click=(parent,v,position,id) -> select(shown.get(position)); list.setOnItemClickListener(click); posters.setOnItemClickListener(click);
        AdapterView.OnItemLongClickListener menu=(parent,v,position,id) -> { options(shown.get(position)); return true; }; list.setOnItemLongClickListener(menu); posters.setOnItemLongClickListener(menu);
        split.addView(catalog,new LinearLayout.LayoutParams(0,-1,1)); body.addView(split,new LinearLayout.LayoutParams(-1,0,1));
        filter(); bouquets.requestFocus();
    }
    private View channelRow(Channel channel) {
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(14),dp(10),dp(14),dp(10)); row.setMinimumHeight(dp(84)); row.setBackground(Ui.shape(this,Ui.SURFACE,10,0));
        ImageView logo=new ImageView(this); logo.setScaleType(ImageView.ScaleType.FIT_CENTER); row.addView(logo,new LinearLayout.LayoutParams(dp(72),dp(52))); ImageLoader.get(this).load(channel.logo(),logo,dp(72),dp(52));
        LinearLayout copy=column(); copy.setPadding(dp(16),0,dp(10),0);
        TextView name=text(channel.name(),20,Ui.TEXT); name.setMaxLines(2); copy.addView(name);
        TextView epg=text(channel.kind().equals("tv")?GuideRepository.summary(GuideRepository.get(this).peek(channel,false),System.currentTimeMillis()/1000):channel.group(),15,Ui.MUTED); epg.setMaxLines(2); copy.addView(epg); row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        if(channel.kind().equals("tv")) { row.setTag(channel.url()); GuideRepository.get(this).request(channel,false,data -> { if(channel.url().equals(row.getTag())) epg.setText(GuideRepository.summary(data,System.currentTimeMillis()/1000)); }); }
        TextView star=text(favorites.contains(channel.url())?"★":"",22,Ui.STAR); row.addView(star); return row;
    }
    private View poster(Channel channel) {
        LinearLayout card=column(); card.setPadding(dp(5),dp(5),dp(5),dp(10)); card.setBackground(Ui.shape(this,Ui.SURFACE,12,0));
        ImageView image=new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setBackgroundColor(Ui.SURFACE_HIGH); card.addView(image,new LinearLayout.LayoutParams(-1,dp(180))); ImageLoader.get(this).load(channel.logo(),image,dp(135),dp(180));
        TextView name=text((favorites.contains(channel.url())?"★ ":"")+channel.name(),16,Ui.TEXT); name.setMaxLines(2); name.setPadding(dp(4),dp(7),dp(4),0); card.addView(name,new LinearLayout.LayoutParams(-1,dp(52))); return card;
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
        status.setText(busy?"Wczytywanie biblioteki…":shown.isEmpty()?"Brak pozycji. Dodaj źródło lub zmień filtr.":Ui.count(shown.size())+" pozycji · OK — odtwórz · Menu — EPG i ulubione · Wstecz — powrót");
    }
    private Channel selected() { if(home || shown.isEmpty()) return null; int index=(list.getVisibility()==View.VISIBLE?list:posters).getSelectedItemPosition(); return index>=0 && index<shown.size()?shown.get(index):null; }
    private void options(Channel channel) {
        List<String> actions=new ArrayList<>(); actions.add(channel.kind().equals("series")?"Odcinki":"Odtwórz"); if(channel.kind().equals("tv")) actions.add("Program TV i archiwum"); actions.add(favorites.contains(channel.url())?"Usuń z ulubionych":"Dodaj do ulubionych");
        new AlertDialog.Builder(this).setTitle(channel.name()).setItems(actions.toArray(new String[0]),(d,index) -> { String action=actions.get(index); if(action.contains("ulubionych")) favorite(channel); else if(action.startsWith("Program")) GuideDialog.open(this,channel,worker,this::play); else select(channel); }).setNegativeButton("Zamknij",null).show();
    }
    private void favorite(Channel channel) {
        if(!favorites.add(channel.url())) favorites.remove(channel.url()); final Set<String> saved=new HashSet<>(favorites);
        worker.execute(() -> { try { new LibraryStore(this).saveFavorites(saved); } catch(Exception ignored) {} }); filter();
    }
    private void select(Channel channel) {
        if(!channel.kind().equals("series")) { play(channel); return; }
        if(busy) return; busy=true; status.setText("Pobieranie odcinków…");
        worker.execute(() -> { try { List<Channel> episodes=source.episodes(channel); runOnUiThread(() -> { if(!isDestroyed()) { busy=false; seriesTitle=channel.name(); items=episodes; group=""; query=""; rebuildGroups(); list.requestFocus(); } }); } catch(Exception e) { runOnUiThread(() -> { busy=false; error("Nie udało się pobrać odcinków."); }); } });
    }
    private void play(Channel channel) { startActivity(new Intent(this,PlayerActivity.class).putExtra("television",true).putExtra("url",channel.url()).putExtra("name",channel.name()).putExtra("kind",channel.kind()).putExtra("guide",channel.guideUrl()).putExtra("epgId",channel.epgId()).putExtra("days",channel.archiveDays()).putExtra("timezone",channel.timezone())); }
    private void search() { EditText field=new EditText(this); field.setSingleLine(true); field.setText(query); new AlertDialog.Builder(this).setTitle("Szukaj w sekcji").setView(field).setPositiveButton("Szukaj",(d,w) -> { query=field.getText().toString().trim(); group=""; filter(); }).setNegativeButton("Anuluj",null).show(); }
    private void sort() { new AlertDialog.Builder(this).setTitle("Kolejność bukietów").setItems(BouquetOrder.LABELS,(d,index) -> { getPreferences(MODE_PRIVATE).edit().putString("sort-"+section,BouquetOrder.MODES[index]).apply(); rebuildGroups(); }).show(); }
    private void info() { new AlertDialog.Builder(this).setTitle("TV Player TV 0.9.0").setMessage("Wersja na Fire OS 6+ i Android TV 7.1+.\n\nTV, filmy, seriale, bukiety dostawcy, ulubione i EPG.\n\nOK: odtwórz\nMenu / przytrzymanie OK: opcje pozycji\nPlay/Pause: sterowanie odtwarzaniem\n\nAplikacja odtwarza własne źródła użytkownika.").setPositiveButton("Zamknij",null).show(); }
    private void error(String message) { if(!isDestroyed()) new AlertDialog.Builder(this).setTitle("TV Player").setMessage(message).setPositiveButton("OK",null).show(); }
    private void sourceDialog() {
        if(busy) return;
        new AlertDialog.Builder(this).setTitle("Dodaj źródło").setItems(new String[]{"Konto Xtream Codes","Adres listy M3U"},(d,index) -> sourceForm(index==0)).setNegativeButton("Anuluj",null).show();
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
        worker.execute(() -> {
            try {
                List<Channel> imported=next.type().equals("xtream")?source.xtreamSection(next.server(),next.user(),next.password(),"tv",this::progress):source.m3u(next.server());
                new LibraryStore(this).save(imported,new HashSet<>(favorites)); new TvAccountStore(this).save(next);
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
    @Override protected void onResume() { super.onResume(); if(!home && rows!=null) rows.notifyDataSetChanged(); }
    @Override protected void onDestroy() { generation++; worker.shutdown(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putString("section",section); state.putString("group",group); state.putBoolean("home",home); super.onSaveInstanceState(state); }
}
