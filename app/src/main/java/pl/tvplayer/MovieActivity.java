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
    private int restoredScroll;
    private int dp(int n) { return Ui.dp(this,n); }
    private LinearLayout column() { LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(String s,int size,int color) { return Ui.text(this,s,size,color); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); repository=new MovieRepository(this); String url=getIntent().getStringExtra("url"); if(url==null) { finish(); return; }
        television=getIntent().getBooleanExtra("television",false) || (getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_TYPE_MASK)==android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
        if(television) { setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE); getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN); }
        source=new Channel(getIntent().getStringExtra("name"),url,getIntent().getStringExtra("group"),"","movie").withLogo(getIntent().getStringExtra("logo")); details=MovieDetails.fallback(source); loading=MovieDetails.endpoint(source)!=null;
        if(state!=null) restoredScroll=state.getInt("scroll");
        scroll=new ScrollView(this); scroll.setBackgroundColor(Ui.BG); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        
        content=column(); scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        FrameLayout page=new FrameLayout(this); page.setBackgroundColor(Ui.BG); page.setOnApplyWindowInsetsListener((v,insets) -> { v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets.consumeSystemWindowInsets(); }); scroll.setPadding(0,0,0,dp(20)); page.addView(scroll,new FrameLayout.LayoutParams(-1,-1)); page.addView(Ui.detailBar(this,"Filmy",this::finish),new FrameLayout.LayoutParams(-1,dp(52),Gravity.TOP)); setContentView(page); render(); load();
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
        if(television) { renderTelevision(); return; }
        int y=Math.max(restoredScroll,scroll.getScrollY()); restoredScroll=0; content.removeAllViews(); if(details.cover().isEmpty() && details.backdrop().isEmpty()) content.addView(new View(this),new LinearLayout.LayoutParams(-1,dp(52)));
        int width=getResources().getDisplayMetrics().widthPixels,heroHeight=Math.min(dp(300),Math.max(dp(180),width*9/16));
        FrameLayout hero=new FrameLayout(this); hero.setBackgroundColor(Ui.SURFACE);
        ImageView artwork=new ImageView(this); artwork.setScaleType(details.backdrop().isEmpty()?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP); hero.addView(artwork,new FrameLayout.LayoutParams(-1,-1)); ImageLoader.get(this).load(details.backdrop().isEmpty()?details.cover():details.backdrop(),artwork,width,heroHeight);
        View shade=new View(this); shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x440E0F11,0x110E0F11,Ui.BG})); hero.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        Button back=Ui.button(this,"‹"); back.setVisibility(View.GONE); back.setTextSize(30); back.setTextColor(Ui.TEXT); back.setContentDescription("Wróć do filmów"); back.setOnClickListener(v -> finish()); FrameLayout.LayoutParams backSize=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.LEFT); backSize.leftMargin=dp(12); backSize.topMargin=dp(10); hero.addView(back,backSize);
        TextView brand=text(Ui.BRAND,12,Ui.ACCENT); brand.setVisibility(View.GONE); brand.setTypeface(null,Typeface.BOLD); FrameLayout.LayoutParams brandSize=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT); brandSize.topMargin=dp(26); brandSize.rightMargin=dp(20); hero.addView(brand,brandSize); if(!details.cover().isEmpty() || !details.backdrop().isEmpty()) content.addView(hero,new LinearLayout.LayoutParams(-1,heroHeight));
        LinearLayout copy=column(); copy.setPadding(dp(20),dp(4),dp(20),dp(24)); content.addView(copy,new LinearLayout.LayoutParams(-1,-2));
        TextView title=text(details.title(),28,Ui.TEXT); title.setTypeface(null,Typeface.BOLD); copy.addView(title);
        List<String> meta=new ArrayList<>(); if(!details.year().isEmpty()) meta.add(details.year()); if(details.durationSeconds()>0) meta.add(duration(details.durationSeconds())); if(!details.rating().isEmpty() && !details.rating().equals("0")) meta.add("★ "+details.rating());
        if(!meta.isEmpty()) { TextView line=text(TextUtils.join("   ·   ",meta),14,Ui.MUTED); line.setPadding(0,dp(10),0,0); copy.addView(line); }
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(1000); progress.setProgressTintList(ColorStateList.valueOf(Ui.TEXT)); LinearLayout.LayoutParams progressSize=new LinearLayout.LayoutParams(-1,dp(4)); progressSize.topMargin=dp(16); copy.addView(progress,progressSize);
        progressText=text("",13,Ui.MUTED); progressText.setPadding(0,dp(7),0,0); copy.addView(progressText);
        play=Ui.button(this,"▶  Odtwórz"); Ui.primary(play); play.setTextSize(16); play.setOnClickListener(v -> playback(false)); LinearLayout.LayoutParams playSize=new LinearLayout.LayoutParams(-1,dp(52)); playSize.topMargin=dp(18); copy.addView(play,playSize);
        LinearLayout actions=new LinearLayout(this); actions.setPadding(0,dp(8),0,0); star=Ui.button(this,"+  Moja lista"); Ui.secondary(star); star.setOnClickListener(v -> favorite()); actions.addView(star,new LinearLayout.LayoutParams(0,dp(48),1)); restart=Ui.button(this,"Od początku"); Ui.secondary(restart); restart.setOnClickListener(v -> playback(true)); LinearLayout.LayoutParams restartSize=new LinearLayout.LayoutParams(0,dp(48),1); restartSize.leftMargin=dp(10); actions.addView(restart,restartSize); copy.addView(actions);
        if(loading) { LinearLayout status=new LinearLayout(this); status.setGravity(Gravity.CENTER_VERTICAL); status.setPadding(0,dp(18),0,0); ProgressBar spinner=new ProgressBar(this); status.addView(spinner,new LinearLayout.LayoutParams(dp(22),dp(22))); TextView label=text("Pobieranie opisu…",14,Ui.MUTED); label.setPadding(dp(10),0,0,0); status.addView(label); copy.addView(status); }
        TextView synopsis=text(details.overview().isEmpty()?(loading?"":failed?"Nie udało się pobrać opisu. Film możesz odtworzyć bez tych danych.":"Opis nie został udostępniony przez dostawcę."):details.overview(),16,Ui.TEXT); synopsis.setLineSpacing(dp(4),1); synopsis.setPadding(0,dp(20),0,0); copy.addView(synopsis);
        if(failed) { Button retry=Ui.button(this,"Ponów pobieranie opisu"); retry.setTextColor(Ui.TEXT); retry.setOnClickListener(v -> { loading=true; failed=false; render(); load(); }); LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-2,dp(48)); size.topMargin=dp(10); copy.addView(retry,size); }
        detail(copy,"Gatunek",details.genre()); detail(copy,"Reżyseria",details.director()); detail(copy,"Obsada",details.cast());
        updateProgress(); scroll.post(() -> scroll.scrollTo(0,y));
    }
    private Button tvButton(String label,boolean primary) {
        Button button=Ui.button(this,label); button.setTextSize(18); Ui.televisionButton(button,primary); return button;
    }
    private void renderTelevision() {
        View previous=getCurrentFocus(); Object focused=previous==null?null:previous.getTag();
        int y=Math.max(restoredScroll,scroll.getScrollY()); restoredScroll=0; content.removeAllViews();
        int width=getResources().getDisplayMetrics().widthPixels,heroHeight=dp(440);
        FrameLayout hero=new FrameLayout(this); televisionHero=hero; hero.setBackgroundColor(Ui.SURFACE); content.addView(hero,new LinearLayout.LayoutParams(-1,heroHeight));
        ImageView artwork=new ImageView(this); artwork.setScaleType(details.backdrop().isEmpty()?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP); hero.addView(artwork,new FrameLayout.LayoutParams(-1,-1));
        ImageLoader.get(this).load(details.backdrop().isEmpty()?details.cover():details.backdrop(),artwork,width,heroHeight);
        View shade=new View(this); shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Ui.BG,0xE60A0D14,0x220A0D14})); hero.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout copy=column(); televisionCopy=copy; televisionCopyWidth=Math.min(width-dp(32),Math.max(dp(540),width*3/5)); copy.setPadding(dp(32),dp(72),dp(24),dp(20)); hero.addView(copy,new FrameLayout.LayoutParams(televisionCopyWidth,-2,Gravity.LEFT));
        TextView title=text(details.title(),30,Ui.TEXT); title.setTypeface(null,Typeface.BOLD); title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END); copy.addView(title);
        List<String> meta=new ArrayList<>(); if(!details.year().isEmpty()) meta.add(details.year()); if(details.durationSeconds()>0) meta.add(duration(details.durationSeconds())); if(!details.rating().isEmpty() && !details.rating().equals("0")) meta.add("★ "+details.rating());
        TextView metadata=text(TextUtils.join("   ·   ",meta),17,Ui.MUTED); metadata.setPadding(0,dp(12),0,dp(6)); copy.addView(metadata);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(1000); progress.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT)); copy.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));
        progressText=text("",15,Ui.MUTED); progressText.setPadding(0,dp(6),0,0); copy.addView(progressText);
        LinearLayout actions=new LinearLayout(this); actions.setPadding(0,dp(14),0,0);
        play=tvButton("▶  Odtwórz",true); play.setTag("movie-play"); play.setContentDescription("Odtwórz film"); play.setOnClickListener(v -> playback(false));
        star=tvButton("+  Moja lista",false); star.setTag("movie-star"); star.setOnClickListener(v -> favorite());
        LinearLayout.LayoutParams playSize=new LinearLayout.LayoutParams(0,dp(52),1); playSize.rightMargin=dp(12); actions.addView(play,playSize); actions.addView(star,new LinearLayout.LayoutParams(dp(180),dp(52))); copy.addView(actions);
        restart=tvButton("Od początku",false); restart.setTag("movie-restart"); restart.setOnClickListener(v -> playback(true)); LinearLayout.LayoutParams restartSize=new LinearLayout.LayoutParams(-2,dp(48)); restartSize.topMargin=dp(10); copy.addView(restart,restartSize);
        if(!details.genre().isEmpty()) { TextView genre=text(details.genre(),16,Ui.MUTED); genre.setPadding(0,dp(16),0,0); copy.addView(genre); }
        LinearLayout description=column(); description.setPadding(dp(32),dp(20),dp(32),dp(28)); content.addView(description);
        if(loading) description.addView(text("Pobieranie opisu…",15,Ui.MUTED));
        TextView synopsis=text(details.overview().isEmpty()?(loading?"":failed?"Nie udało się pobrać opisu. Film możesz odtworzyć bez tych danych.":"Opis nie został udostępniony przez dostawcę."):details.overview(),18,Ui.TEXT); synopsis.setLineSpacing(dp(4),1); description.addView(synopsis);
        if(failed) { Button retry=tvButton("Ponów pobieranie opisu",false); retry.setOnClickListener(v -> { loading=true; failed=false; render(); load(); }); LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-2,dp(48)); size.topMargin=dp(14); description.addView(retry,size); }
        detail(description,"Reżyseria",details.director()); detail(description,"Obsada",details.cast());
        play.setId(View.generateViewId()); star.setId(View.generateViewId()); restart.setId(View.generateViewId());
        play.setNextFocusRightId(star.getId()); star.setNextFocusLeftId(play.getId()); restart.setNextFocusUpId(play.getId());
        updateProgress(); if(restart.getVisibility()==View.VISIBLE) { play.setNextFocusDownId(restart.getId()); star.setNextFocusDownId(restart.getId()); }
        View target="movie-star".equals(focused) && star.isEnabled()?star:"movie-restart".equals(focused) && restart.getVisibility()==View.VISIBLE?restart:play;
        if(previous==null || focused!=null) target.post(target::requestFocus);
        scroll.post(() -> scroll.scrollTo(0,y));
    }
    private void detail(LinearLayout parent,String label,String value) { if(value.isEmpty()) return; TextView name=text(label,13,Ui.MUTED); name.setPadding(0,dp(22),0,dp(5)); parent.addView(name); TextView body=text(value,15,Ui.TEXT); body.setLineSpacing(dp(3),1); parent.addView(body); }
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
        if(television && televisionHero!=null && televisionCopy!=null) {
            // Text determines the hero height; a large backdrop must not expand a ScrollView child.
            televisionCopy.measure(View.MeasureSpec.makeMeasureSpec(televisionCopyWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            int height=Math.max(dp(440),televisionCopy.getMeasuredHeight());
            if(televisionHero.getLayoutParams().height!=height) televisionHero.setLayoutParams(new LinearLayout.LayoutParams(-1,height));
        }
    }
    private void playback(boolean fromStart) { if(fromStart) WatchProgress.restart(this,source.url()); startActivity(new Intent(this,PlayerActivity.class).putExtra("url",source.url()).putExtra("name",details.title()).putExtra("kind","movie").putExtra("television",television)); }
    private void favorite() { if(!favoritesReady) return; if(!favorites.add(source.url())) favorites.remove(source.url()); updateProgress(); Set<String> saved=new HashSet<>(favorites); worker.execute(() -> { try { new LibraryStore(this).saveFavorites(saved); } catch(Exception e) { runOnUiThread(() -> { if(!isDestroyed()) Toast.makeText(this,"Nie udało się zapisać Mojej listy.",Toast.LENGTH_LONG).show(); }); } }); }
    @Override public boolean onKeyUp(int code,KeyEvent event) {
        if(television && (code==KeyEvent.KEYCODE_MEDIA_PLAY || code==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) { playback(false); return true; }
        return super.onKeyUp(code,event);
    }
    @Override protected void onResume() { super.onResume(); updateProgress(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putInt("scroll",scroll.getScrollY()); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() { repository.cancel(); network.shutdownNow(); worker.shutdown(); super.onDestroy(); }
}

