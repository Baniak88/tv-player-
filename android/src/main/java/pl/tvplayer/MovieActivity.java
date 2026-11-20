package pl.tvplayer;

import android.app.Activity;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Shared OKTV movie page. Provider metadata never blocks the original stream. */
public final class MovieActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private MovieRepository repository;
    private final Set<String> favorites=new HashSet<>();
    private Channel source;
    private MovieDetails details;
    private ScrollView scroll;
    private LinearLayout content;
    private FrameLayout televisionHero;
    private LinearLayout televisionCopy;
    private int televisionCopyWidth;
    private Button play,star,restart;
    private TextView progressText;
    private ProgressBar progress;
    private boolean loading,failed,favoritesReady,television;
    private View androidDetailBar;
    private int restoredScroll;
    private int dp(int n) { return Ui.dp(this,n); }
    private LinearLayout column() { LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(String s,int size,int color) { return Ui.text(this,s,size,color); }
    @Override protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(base); android.content.res.Configuration scale=DisplaySettings.override(base); if(scale!=null) applyOverrideConfiguration(scale); }
    @Override public void onCreate(Bundle state) {
        DisplaySettings.apply(this);
        super.onCreate(state); repository=new MovieRepository(this); String url=getIntent().getStringExtra("url"); if(url==null) { finish(); return; }
        television=getIntent().getBooleanExtra("television",false) || (getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_TYPE_MASK)==android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
        if(television) { setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER); getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN); }
        source=new Channel(getIntent().getStringExtra("name"),url,getIntent().getStringExtra("group"),"","movie").withLogo(getIntent().getStringExtra("logo")); details=MovieDetails.fallback(source); loading=MovieDetails.endpoint(source)!=null;
        if(state!=null) restoredScroll=state.getInt("scroll");
        scroll=new ScrollView(this); scroll.setBackgroundColor(Ui.BG); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        
        content=column(); scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        FrameLayout page=new FrameLayout(this); page.setBackgroundColor(Ui.BG); page.setOnApplyWindowInsetsListener((v,insets) -> { v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets.consumeSystemWindowInsets(); }); scroll.setPadding(0,0,0,dp(20)); page.addView(scroll,new FrameLayout.LayoutParams(-1,-1)); androidDetailBar=Ui.detailBar(this,"Filmy",this::finish); page.addView(androidDetailBar,new FrameLayout.LayoutParams(-1,dp(52),Gravity.TOP)); /* on TV the remote's Back key is enough and the bar covered the title */ AndroidLayout.insets(page); setContentView(page); Ui.localize(page); if(television) DisplaySettings.pad(findViewById(android.R.id.content),0,0,0,0); render(); load();
    }
    private void load() {
        worker.execute(() -> {
            try { Set<String> saved=new LibraryStore(this).loadFavorites(); runOnUiThread(() -> { if(!isDestroyed()) { favorites.clear(); favorites.addAll(saved); favoritesReady=true; updateProgress(); } }); }
            catch(Exception e) { runOnUiThread(() -> { if(!isDestroyed()) Toast.makeText(this,"Nie udało się odczytać Mojej listy.",Toast.LENGTH_LONG).show(); }); }
        });
        network.execute(() -> {
            try { MovieDetails result=repository.load(source); runOnUiThread(() -> { if(isDestroyed()) return; details=result; loading=false; failed=false; render(); }); }
            catch(Exception e) { runOnUiThread(() -> { if(isDestroyed()) return; loading=false; failed=true; render(); }); }
        });
    }
    private void render() {
        if(androidDetailBar!=null) androidDetailBar.setVisibility(AndroidLayout.portrait(this)?View.VISIBLE:View.GONE);
        if(television && !AndroidLayout.portrait(this)) { renderTelevision(); return; }
        int y=Math.max(restoredScroll,scroll.getScrollY()); restoredScroll=0; content.removeAllViews(); if(details.cover().isEmpty() && details.backdrop().isEmpty()) content.addView(new View(this),new LinearLayout.LayoutParams(-1,dp(52)));
        int width=getResources().getDisplayMetrics().widthPixels,heroHeight=Math.min(dp(300),Math.max(dp(180),width*9/16));
        FrameLayout hero=new FrameLayout(this); hero.setBackgroundColor(Ui.SURFACE);
        ImageView artwork=new ImageView(this); artwork.setScaleType(details.backdrop().isEmpty()?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP); hero.addView(artwork,new FrameLayout.LayoutParams(-1,-1)); ImageLoader.get(this).load(details.backdrop().isEmpty()?details.cover():details.backdrop(),artwork,width,heroHeight);
        View shade=new View(this); shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x440E0F11,0x110E0F11,Ui.BG})); hero.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        Button back=Ui.button(this,"‹"); back.setVisibility(View.GONE); back.setTextSize(30); back.setTextColor(Ui.TEXT); back.setContentDescription("Wróć do filmów"); back.setOnClickListener(v -> finish()); FrameLayout.LayoutParams backSize=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.LEFT); backSize.leftMargin=dp(12); backSize.topMargin=dp(10); hero.addView(back,backSize);
        TextView brand=text(Ui.BRAND,12,Ui.ACCENT); brand.setVisibility(View.GONE); brand.setTypeface(null,Typeface.BOLD); FrameLayout.LayoutParams brandSize=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT); brandSize.topMargin=dp(26); brandSize.rightMargin=dp(20); hero.addView(brand,brandSize); if(!details.cover().isEmpty() || !details.backdrop().isEmpty()) content.addView(hero,new LinearLayout.LayoutParams(-1,heroHeight));
        LinearLayout copy=column(); copy.setPadding(dp(20),dp(4),dp(20),dp(24)); content.addView(copy,new LinearLayout.LayoutParams(-1,-2));
        TextView title=text(details.title(),28,Ui.TEXT); title.setTypeface(null,Typeface.BOLD); copy.addView(title);
        List<String> meta=new ArrayList<>(); if(!details.year().isEmpty()) meta.add(details.year()); if(details.durationSeconds()>0) meta.add(duration(details.durationSeconds())); if(!details.rating().isEmpty() && !details.rating().equals("0")) meta.add("★ "+rating(details.rating()));
        if(!meta.isEmpty()) { TextView line=text(TextUtils.join("   ·   ",meta),14,Ui.MUTED); line.setPadding(0,dp(10),0,0); copy.addView(line); }
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(1000); progress.setProgressTintList(ColorStateList.valueOf(Ui.TEXT)); LinearLayout.LayoutParams progressSize=new LinearLayout.LayoutParams(-1,dp(4)); progressSize.topMargin=dp(16); copy.addView(progress,progressSize);
        progressText=text("",13,Ui.MUTED); progressText.setPadding(0,dp(7),0,0); copy.addView(progressText);
        play=Ui.button(this,"▶  Odtwórz"); Ui.televisionButton(play,true); play.setTextSize(16); play.setOnClickListener(v -> playback(false)); LinearLayout.LayoutParams playSize=new LinearLayout.LayoutParams(-1,dp(52)); playSize.topMargin=dp(18); copy.addView(play,playSize);
        LinearLayout actions=new LinearLayout(this); actions.setPadding(0,dp(8),0,0); star=Ui.button(this,"+  Moja lista"); Ui.secondary(star); star.setOnClickListener(v -> favorite()); actions.addView(star,new LinearLayout.LayoutParams(0,dp(48),1)); restart=Ui.button(this,"Od początku"); Ui.secondary(restart); restart.setOnClickListener(v -> playback(true)); LinearLayout.LayoutParams restartSize=new LinearLayout.LayoutParams(0,dp(48),1); restartSize.leftMargin=dp(10); actions.addView(restart,restartSize); copy.addView(actions);
        if(loading) { LinearLayout status=new LinearLayout(this); status.setGravity(Gravity.CENTER_VERTICAL); status.setPadding(0,dp(18),0,0); ProgressBar spinner=new ProgressBar(this); status.addView(spinner,new LinearLayout.LayoutParams(dp(22),dp(22))); TextView label=text("Pobieranie opisu…",14,Ui.MUTED); label.setPadding(dp(10),0,0,0); status.addView(label); copy.addView(status); }
        TextView synopsis=text(details.overview().isEmpty()?(loading?"":failed?"Nie udało się pobrać opisu. Film możesz odtworzyć bez tych danych.":"Opis nie został udostępniony przez dostawcę."):details.overview(),16,Ui.TEXT); synopsis.setLineSpacing(dp(4),1); synopsis.setPadding(0,dp(20),0,0); copy.addView(synopsis);
        if(failed) { Button retry=Ui.button(this,"Ponów pobieranie opisu"); retry.setTextColor(Ui.TEXT); retry.setOnClickListener(v -> { loading=true; failed=false; render(); load(); }); LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-2,dp(48)); size.topMargin=dp(10); copy.addView(retry,size); }
        detail(copy,"Gatunek",details.genre()); detail(copy,"Reżyseria",details.director()); detail(copy,"Obsada",details.cast());
        updateProgress(); scroll.post(() -> scroll.scrollTo(0,y));
    }
    private Button tvButton(String label,boolean primary) {
        Button button=Ui.button(this,label); button.setTextSize(15); button.setMinHeight(dp(42)); button.setMinimumHeight(dp(42)); button.setPadding(dp(18),0,dp(18),0); Ui.televisionButton(button,primary); return button;
    }
    private void renderTelevision() {
        View previous=getCurrentFocus(); Object focused=previous==null?null:previous.getTag();
        int y=Math.max(restoredScroll,scroll.getScrollY()); restoredScroll=0; content.removeAllViews();
        // Symmetric details page: dimmed backdrop, poster on the left, and one readable column with title, facts, synopsis, people and actions.
        int width=getResources().getDisplayMetrics().widthPixels,heroHeight=getResources().getDisplayMetrics().heightPixels-dp(20),side=dp(48),posterWidth=Math.min(dp(250),width/4);
        // The page grows with long synopses instead of pushing the title off screen; it is never shorter than the screen.
        FrameLayout hero=new FrameLayout(this); televisionHero=hero; hero.setBackgroundColor(Ui.BG); hero.setMinimumHeight(heroHeight); content.addView(hero,new LinearLayout.LayoutParams(-1,-2));
        ImageView artwork=new ImageView(this); artwork.setScaleType(ImageView.ScaleType.CENTER_CROP); artwork.setAlpha(details.backdrop().isEmpty()?.18f:.35f); hero.addView(artwork,new FrameLayout.LayoutParams(-1,-1));
        ImageLoader.get(this).load(details.backdrop().isEmpty()?details.cover():details.backdrop(),artwork,width,heroHeight);
        View shade=new View(this); shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xF20B0C0F,0xCC0B0C0F,0x990B0C0F})); hero.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout layout=new LinearLayout(this); layout.setGravity(Gravity.CENTER_VERTICAL); layout.setPadding(side,dp(40),side,dp(40)); hero.addView(layout,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_VERTICAL));
        FrameLayout poster=new FrameLayout(this); poster.setBackground(Ui.shape(this,Ui.TV_CARD,12,Ui.TV_LINE)); poster.setClipToOutline(true);
        TextView posterName=text(details.title(),16,Ui.MUTED); posterName.setGravity(Gravity.CENTER); posterName.setPadding(dp(12),dp(12),dp(12),dp(12)); poster.addView(posterName,new FrameLayout.LayoutParams(-1,-1));
        ImageView cover=new ImageView(this); cover.setScaleType(ImageView.ScaleType.CENTER_CROP); poster.addView(cover,new FrameLayout.LayoutParams(-1,-1));
        String coverUrl=!details.cover().isEmpty()?details.cover():source.logo(); ImageLoader.get(this).load(coverUrl,cover,posterWidth,posterWidth*3/2);
        layout.addView(poster,new LinearLayout.LayoutParams(posterWidth,posterWidth*3/2));
        LinearLayout copy=column(); televisionCopy=copy; televisionCopyWidth=width-side*2-posterWidth-dp(40); copy.setPadding(dp(40),0,0,0); layout.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=text(details.title(),28,Ui.TEXT); title.setTypeface(null,Typeface.BOLD); title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END); copy.addView(title);
        long length=filmLength(); List<String> meta=new ArrayList<>(); if(!details.year().isEmpty()) meta.add(details.year()); if(length>0) meta.add(duration(length)); if(!details.rating().isEmpty() && !details.rating().equals("0")) meta.add("★ "+rating(details.rating()));
        TextView metadata=text(TextUtils.join("   ·   ",meta),17,Ui.TV_GOLD); metadata.setPadding(0,dp(10),0,0); copy.addView(metadata);
        if(!details.genre().isEmpty()) { TextView genre=text(details.genre(),15,Ui.MUTED); genre.setPadding(0,dp(6),0,0); copy.addView(genre); }
        if(loading) { TextView wait=text("Pobieranie opisu…",15,Ui.MUTED); wait.setPadding(0,dp(16),0,0); copy.addView(wait); }
        TextView synopsis=text(details.overview().isEmpty()?(loading?"":failed?"Nie udało się pobrać opisu. Film możesz odtworzyć bez tych danych.":"Opis nie został udostępniony przez dostawcę."):details.overview(),18,Ui.TEXT);
        synopsis.setLineSpacing(dp(3),1); synopsis.setMaxLines(5); synopsis.setEllipsize(TextUtils.TruncateAt.END); synopsis.setPadding(0,dp(16),0,0); copy.addView(synopsis);
        LinearLayout description=copy;
        if(failed) { Button retry=tvButton("Ponów pobieranie opisu",false); retry.setOnClickListener(v -> { loading=true; failed=false; render(); load(); }); LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-2,dp(48)); size.topMargin=dp(12); description.addView(retry,size); }
        person(description,"Reżyseria",details.director()); person(description,"Obsada",details.cast());
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(1000); progress.setProgressTintList(ColorStateList.valueOf(Ui.TV_GOLD)); progress.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.TV_LINE));
        LinearLayout.LayoutParams progressSize=new LinearLayout.LayoutParams(-1,dp(4)); progressSize.topMargin=dp(18); copy.addView(progress,progressSize);
        progressText=text("",15,Ui.MUTED); progressText.setPadding(0,dp(6),0,0); copy.addView(progressText);
        LinearLayout actions=new LinearLayout(this); actions.setPadding(0,dp(16),0,0);
        play=tvButton("▶  Odtwórz",true); play.setTag("movie-play"); play.setContentDescription("Odtwórz film"); play.setOnClickListener(v -> playback(false));
        star=tvButton("+  Moja lista",false); star.setTag("movie-star"); star.setOnClickListener(v -> favorite());
        LinearLayout.LayoutParams playSize=new LinearLayout.LayoutParams(dp(196),dp(44)); playSize.rightMargin=dp(12); actions.addView(play,playSize); actions.addView(star,new LinearLayout.LayoutParams(dp(176),dp(44))); copy.addView(actions);
        restart=tvButton("Od początku",false); restart.setTag("movie-restart"); restart.setOnClickListener(v -> playback(true)); LinearLayout.LayoutParams restartSize=new LinearLayout.LayoutParams(dp(196),dp(40)); restartSize.topMargin=dp(10); copy.addView(restart,restartSize);
        play.setId(View.generateViewId()); star.setId(View.generateViewId()); restart.setId(View.generateViewId());
        play.setNextFocusRightId(star.getId()); star.setNextFocusLeftId(play.getId()); restart.setNextFocusUpId(play.getId());
        updateProgress(); if(restart.getVisibility()==View.VISIBLE) { play.setNextFocusDownId(restart.getId()); star.setNextFocusDownId(restart.getId()); }
        View target="movie-star".equals(focused) && star.isEnabled()?star:"movie-restart".equals(focused) && restart.getVisibility()==View.VISIBLE?restart:play;
        if(previous==null || focused!=null) target.post(target::requestFocus);
        scroll.post(() -> scroll.scrollTo(0,y));
    }
    /** One compact line per credit on TV: muted label, then names. */
    private void person(LinearLayout parent,String label,String value) {
        if(value.isEmpty()) return; LinearLayout line=new LinearLayout(this); line.setPadding(0,dp(10),0,0);
        line.addView(text(label,15,Ui.MUTED),new LinearLayout.LayoutParams(dp(110),-2));
        TextView names=text(value,15,Ui.TEXT); names.setMaxLines(1); names.setEllipsize(TextUtils.TruncateAt.END); line.addView(names,new LinearLayout.LayoutParams(0,-2,1)); parent.addView(line);
    }
    private void detail(LinearLayout parent,String label,String value) { if(value.isEmpty()) return; TextView name=text(label,13,Ui.MUTED); name.setPadding(0,dp(22),0,dp(5)); parent.addView(name); TextView body=text(value,15,Ui.TEXT); body.setLineSpacing(dp(3),1); parent.addView(body); }
    /** "6.182" → "6.2"; non-numeric ratings are shown as given. */
    static String rating(String value) { try { double number=Double.parseDouble(value.replace(',','.')); return String.format(java.util.Locale.ROOT,number==Math.rint(number)?"%.0f":"%.1f",number); } catch(NumberFormatException e) { return value; } }
    /** Length in seconds: the player's measured duration wins; provider values under 10 minutes are treated as unknown. */
    private long filmLength() {
        WatchProgress.Entry entry=WatchProgress.read(this,source.url()); if(entry.duration()>=600000) return entry.duration()/1000;
        return details.durationSeconds()>=600?details.durationSeconds():0;
    }
    private static String duration(long seconds) { long minutes=Math.max(1,(seconds+59)/60); return minutes>=60?(minutes/60)+" godz. "+(minutes%60==0?"":minutes%60+" min"):minutes+" min"; }
    private WatchProgress.Entry position() {
        WatchProgress.Entry entry=WatchProgress.read(this,source.url()); if(entry.updated()>0) return entry;
        try { byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(source.url().getBytes(java.nio.charset.StandardCharsets.UTF_8)); String key=android.util.Base64.encodeToString(hash,android.util.Base64.NO_WRAP); long legacy=getSharedPreferences(PlayerActivity.class.getName(),MODE_PRIVATE).getLong(key,0); return new WatchProgress.Entry(legacy,details.durationSeconds()*1000,0,false); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private void updateProgress() {
        if(play==null) return; WatchProgress.Entry entry=position(); boolean continuing=entry.position()>=5000 && !entry.completed(); long duration=entry.duration()>0?entry.duration():details.durationSeconds()*1000;
        play.setText(entry.completed()?"▶  Odtwórz ponownie":continuing?"▶  Kontynuuj":"▶  Odtwórz"); restart.setVisibility(continuing?View.VISIBLE:View.GONE);
        if(television) { play.setNextFocusDownId(continuing?restart.getId():View.NO_ID); star.setNextFocusDownId(continuing?restart.getId():View.NO_ID); }
        star.setText(favorites.contains(source.url())?"✓  Moja lista":"+  Moja lista"); star.setEnabled(favoritesReady); star.setAlpha(favoritesReady?1f:.45f);
        boolean show=duration>0 && (continuing || entry.completed()); progress.setVisibility(show?View.VISIBLE:View.GONE); if(show) progress.setProgress(entry.completed()?1000:(int)Math.min(1000,entry.position()*1000/duration));
        progressText.setVisibility(continuing || entry.completed()?View.VISIBLE:View.GONE); progressText.setText(entry.completed()?"Obejrzano":duration>entry.position()?"Pozostało "+duration((duration-entry.position())/1000):"Oglądanie w trakcie");
        if(television && !AndroidLayout.portrait(this) && televisionHero!=null && televisionCopy!=null) {
            // Text determines the hero height; a large backdrop must not expand a ScrollView child.
            televisionCopy.measure(View.MeasureSpec.makeMeasureSpec(televisionCopyWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            int height=getResources().getDisplayMetrics().heightPixels-dp(72);
            televisionHero.setMinimumHeight(height);
        }
    }
    private void playback(boolean fromStart) { if(fromStart) WatchProgress.restart(this,source.url()); startActivity(new Intent(this,PlayerActivity.class).putExtra("url",source.url()).putExtra("name",details.title()).putExtra("kind","movie").putExtra("television",television)); }
    private void favorite() { if(!favoritesReady) return; if(!favorites.add(source.url())) favorites.remove(source.url()); updateProgress(); Set<String> saved=new HashSet<>(favorites); worker.execute(() -> { try { new LibraryStore(this).saveFavorites(saved); } catch(Exception e) { runOnUiThread(() -> { if(!isDestroyed()) Toast.makeText(this,"Nie udało się zapisać Mojej listy.",Toast.LENGTH_LONG).show(); }); } }); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code=event.getKeyCode();
        if(television && (code==KeyEvent.KEYCODE_MEDIA_PLAY || code==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            if(event.getAction()==KeyEvent.ACTION_UP && !event.isCanceled()) playback(false);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onResume() { super.onResume(); updateProgress(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putInt("scroll",scroll.getScrollY()); super.onSaveInstanceState(state); }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) { super.onConfigurationChanged(config); render(); }
    @Override protected void onDestroy() { repository.cancel(); network.shutdownNow(); worker.shutdown(); super.onDestroy(); }
}

