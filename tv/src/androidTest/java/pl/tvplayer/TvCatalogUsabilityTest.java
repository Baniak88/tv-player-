package pl.tvplayer;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
import static pl.tvplayer.TvTestUi.*;

@RunWith(AndroidJUnit4.class)
public class TvCatalogUsabilityTest {
    private MockWebServer server;
    private TvActivity home;
    private Activity player;
    private final List<Channel> fixture=new ArrayList<>();
    private String liveUrl;
    private int viewportWidth;
    @Before public void setup() throws Exception {
        systemReady(); server=new MockWebServer();
        byte[] poster=image(false),logo=image(true);
        server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
            if(request.getPath().endsWith(".png")) return new MockResponse().setHeader("Content-Type","image/png").setBody(new okio.Buffer().write(request.getPath().contains("logo")?logo:poster));
            return new MockResponse().setResponseCode(404);
        }}); server.start();
        for(int i=1;i<=12;i++) fixture.add(new Channel("UK| SKY CINEMA ACTION FHD "+i,server.url("/u/p/"+(100+i)).toString(),"UK| MOVIES","sky"+i+".uk","movie").withLogo(server.url("/logo.png").toString()));
        liveUrl=fixture.get(0).url();
        for(int i=1;i<=12;i++) fixture.add(new Channel("Ostatni rejs – wersja reżyserska (2024) · "+i,server.url("/movie/u/p/"+i+".mp4").toString(),"Filmy przygodowe","","movie").withLogo(server.url("/poster.png").toString()));
        fixture.add(new Channel("Film HLS z API",server.url("/vod-playlist.m3u8").toString(),"UK| MOVIES","","movie"));
        fixture.add(episode("Nocna cisza S02 E03",3)); fixture.add(episode("Nocna cisza S01 E02",2)); fixture.add(episode("Nocna cisza S01 E01",1));
        fixture.add(episode("Północny szlak S01 E02",5)); fixture.add(episode("Północny szlak S01 E01",4));
        new android.util.AtomicFile(new File(context().getFilesDir(),"tv-account.enc")).delete();
        new LibraryStore(context()).save(fixture,new HashSet<>(Arrays.asList(liveUrl,fixture.get(12).url())));
        try(FileOutputStream out=context().openFileOutput("catalog.kinds",0)) { out.write('2'); }
    }
    private Channel episode(String name,int id) { return new Channel("PL - "+name,server.url("/series/u/p/"+id+".mp4?token=original").toString(),"PL| SERIALE / PROGRAMY","").withLogo(server.url("/poster.png").toString()); }
    private byte[] image(boolean landscape) throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(landscape?300:280,landscape?120:420,Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(bitmap); Paint paint=new Paint(3);
        paint.setShader(new LinearGradient(0,0,bitmap.getWidth(),bitmap.getHeight(),0xFF17334B,0xFF73566A,Shader.TileMode.CLAMP)); canvas.drawPaint(paint); paint.setShader(null);
        paint.setColor(0xFFE6C496); canvas.drawCircle(bitmap.getWidth()*.7f,bitmap.getHeight()*.25f,landscape?24:42,paint);
        paint.setColor(Color.WHITE); paint.setTextSize(landscape?34:24); canvas.drawText(landscape?"TV CINEMA":"NOCNA CISZA",16,bitmap.getHeight()*.7f,paint);
        ByteArrayOutputStream out=new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG,100,out); bitmap.recycle(); return out.toByteArray();
    }
    private String shellOutput(String command) throws Exception { try(android.os.ParcelFileDescriptor.AutoCloseInputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument().getUiAutomation().executeShellCommand(command)); ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] buffer=new byte[512]; for(int n;(n=in.read(buffer))!=-1;) out.write(buffer,0,n); return out.toString("UTF-8"); } }
    private void shell(String command) throws Exception { shellOutput(command); }
    private void viewport(int width,int height) throws Exception {
        viewportWidth=width;
        // wm size uses the device's natural axes, not the activity's landscape axes.
        java.util.regex.Matcher physical=java.util.regex.Pattern.compile("Physical size:\\s*(\\d+)x(\\d+)").matcher(shellOutput("wm size"));
        assertTrue("Physical display dimensions unavailable",physical.find());
        boolean portrait=Integer.parseInt(physical.group(1))<Integer.parseInt(physical.group(2));
        shell("wm size "+(portrait?height+"x"+width:width+"x"+height)); shell("wm density 160");
    }
    private void start() throws Exception {
        resetNavigation();
        home=(TvActivity)instrument().startActivitySync(new Intent(context(),TvActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        home.getPreferences(0).edit().remove("list-movie").remove("list-series").commit();
        waitFor("Home missing",() -> find(home.getWindow().getDecorView(),"Kanały i program TV")!=null); waitFor("Home has no focus",home::hasWindowFocus);
        waitHomeShortcut();
        try { waitFor("Window/resources did not apply requested viewport "+viewportWidth+" at density 160",() -> {
            boolean[] ready={false}; instrument().runOnMainSync(() -> { android.util.DisplayMetrics metrics=home.getResources().getDisplayMetrics(); ready[0]=metrics.widthPixels==viewportWidth && metrics.densityDpi==160 && home.getWindow().getDecorView().getWidth()==viewportWidth; }); return ready[0];
        }); } catch(AssertionError failure) { android.util.DisplayMetrics metrics=home.getResources().getDisplayMetrics(); throw new AssertionError(failure.getMessage()+"; actual resources="+metrics.widthPixels+"x"+metrics.heightPixels+", density="+metrics.densityDpi+", window="+home.getWindow().getDecorView().getWidth()+"x"+home.getWindow().getDecorView().getHeight(),failure); }
    }
    private void waitHomeShortcut() throws Exception { waitFor("Home shortcut is not ready",() -> { try { TvShelfView shelf=(TvShelfView)field(home,"shelves"); return home.hasWindowFocus() && (Boolean)field(home,"home") && shelf!=null && shelf.hasFocus() && shelf.selected()!=null && shelf.selected().url().equals("section:tv"); } catch(Exception e) { return false; } }); }
    private void screenshot(String name) {
        instrument().runOnMainSync(() -> { View root=home.getWindow().getDecorView(); Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888); root.draw(new Canvas(bitmap)); try(FileOutputStream out=context().openFileOutput(name,0)) { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); } catch(Exception e) { throw new AssertionError(e); } finally { bitmap.recycle(); } });
    }
    @Test public void cachedV2MovieBouquetsMigrateWithoutLosingAccountFavoritesOrRealVod() throws Exception {
        TvAccountStore.Account account=new TvAccountStore.Account("m3u",server.url("/list.m3u").toString(),"",""); new TvAccountStore(context()).save(account);
        Set<String> favorites=new HashSet<>(); LibraryStore store=new LibraryStore(context()); List<Channel> tv=store.loadSection("tv",favorites),movies=store.loadSection("movie",new HashSet<>());
        assertEquals(12,tv.size()); assertEquals(13,movies.size()); assertEquals(liveUrl,tv.get(0).url()); assertEquals("UK| MOVIES",tv.get(0).group()); assertEquals("sky1.uk",tv.get(0).epgId());
        assertTrue(favorites.contains(liveUrl)); assertTrue(favorites.contains(fixture.get(12).url())); assertEquals(2,favorites.size()); assertEquals(account,new TvAccountStore(context()).load());
        assertTrue(movies.stream().anyMatch(c -> c.name().equals("Film HLS z API"))); assertEquals(5,store.loadSection("series",new HashSet<>()).size());
    }
    @Test public void fireStickAndFullHdCatalogKeepTitlesReadableAndBackReturnsToCategories() throws Exception {
        for(int width:new int[]{960,1280}) {
            viewport(width,width==960?540:720); start(); key(KeyEvent.KEYCODE_DPAD_CENTER);
            waitFor("Live movie channel missing",() -> find(home.getWindow().getDecorView(),"UK| SKY CINEMA ACTION FHD 1")!=null);
            ListView list=(ListView)field(home,"list"); GridView grid=(GridView)field(home,"posters"); assertEquals(View.VISIBLE,list.getVisibility()); assertEquals(View.GONE,grid.getVisibility());
            key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_RIGHT); waitFor("Right must enter the channel list",list::hasFocus);
            instrument().runOnMainSync(() -> { int full=0; for(int i=0;i<list.getChildCount();i++) if(list.getChildAt(i).getBottom()<=list.getHeight()) full++; assertTrue("Too few readable live rows",full>=5); });
            screenshot("tv096-live-"+width+".png");
            key(KeyEvent.KEYCODE_BACK); assertFalse(list.hasFocus()); assertNotNull(find(home.getWindow().getDecorView(),"UK| SKY CINEMA ACTION FHD 1")); key(KeyEvent.KEYCODE_BACK);
            assertNotNull(node("Nawigacja")); key(KeyEvent.KEYCODE_BACK); key(KeyEvent.KEYCODE_DPAD_UP); key(KeyEvent.KEYCODE_DPAD_UP); key(KeyEvent.KEYCODE_DPAD_LEFT); key(KeyEvent.KEYCODE_DPAD_CENTER); waitFor("Home action missing",() -> find(home.getWindow().getDecorView(),"Kanały i program TV")!=null);
            waitHomeShortcut(); key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER); waitFor("Real film missing",() -> { try { return home.hasWindowFocus() && !(Boolean)field(home,"home") && field(home,"section").equals("movie") && (Boolean)field(home,"catalogReady") && find(home.getWindow().getDecorView(),"Ostatni rejs – wersja reżyserska (2024) · 1")!=null; } catch(Exception e) { return false; } });
            TvShelfView shelves=(TvShelfView)field(home,"shelves"); assertNotNull("Movies must use category shelves",shelves); waitFor("Shelf has no remote focus",shelves::hasFocus);
            instrument().runOnMainSync(() -> { View hero=shelves.getLayoutManager().findViewByPosition(0); assertNotNull("Selected title banner must remain visible",hero); assertTrue("Selected title banner was clipped",hero.getTop()>=0); });
            assertNull(find(home.getWindow().getDecorView(),"UK| SKY CINEMA ACTION FHD 1")); screenshot("tv096-movies-"+width+".png");
            click("Opcje"); click("Widok"); click("Siatka plakatów");
            grid=(GridView)field(home,"posters"); assertEquals(width==960?5:6,grid.getNumColumns());
            ImageView artwork=(ImageView)((android.widget.FrameLayout)((android.widget.LinearLayout)grid.getChildAt(0)).getChildAt(0)).getChildAt(1); assertEquals(ImageView.ScaleType.FIT_CENTER,artwork.getScaleType());
            click("Opcje"); click("Widok"); click("Lista"); assertEquals(View.VISIBLE,((ListView)field(home,"list")).getVisibility()); screenshot("tv096-movies-list-"+width+".png");
            click("Opcje"); click("Widok"); click("Rzędy okładek"); assertNotNull(field(home,"shelves"));
            close(home); home=null;
        }
    }
    @Test public void flatM3uSeriesOpenFoldersSeasonsAndOriginalEpisodeWithRemote() throws Exception {
        viewport(960,540); start(); key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER);
        waitFor("Series did not group into two titles",() -> { try { return ((List<?>)field(home,"shown")).size()==2; } catch(Exception e) { return false; } });
        screenshot("tv096-series-960.png"); waitFor("Series shelf has no focus",() -> { try { return ((TvShelfView)field(home,"shelves")).hasFocus(); } catch(Exception e) { return false; } }); key(KeyEvent.KEYCODE_DPAD_CENTER);
        waitFor("Season categories missing",() -> find(home.getWindow().getDecorView(),"Sezon 2")!=null);
        key(KeyEvent.KEYCODE_DPAD_LEFT); ListView seasons=(ListView)field(home,"bouquets"); waitFor("Season picker must receive remote focus",seasons::hasFocus);
        screenshot("tv096-episodes-960.png");
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN);
        waitFor("Season 2 did not filter the episodes",() -> { try { return ((List<?>)field(home,"shown")).size()==1; } catch(Exception e) { return false; } });
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        Instrumentation.ActivityMonitor monitor=instrument().addMonitor(PlayerActivity.class.getName(),null,false);
        try { key(KeyEvent.KEYCODE_MEDIA_PLAY); player=monitor.waitForActivityWithTimeout(10000); } finally { instrument().removeMonitor(monitor); }
        assertNotNull("Episode did not start",player); assertEquals(server.url("/series/u/p/3.mp4?token=original").toString(),player.getIntent().getStringExtra("url")); assertTrue(player.getIntent().getBooleanExtra("television",false));
        close(player); player=null; waitFor("Catalog did not regain focus",home::hasWindowFocus); key(KeyEvent.KEYCODE_BACK);
        waitFor("Back did not restore the selected series shelf",() -> { try { TvShelfView shelves=(TvShelfView)field(home,"shelves"); return shelves!=null && shelves.selected()!=null && shelves.selected().name().equals("PL - Nocna cisza"); } catch(Exception e) { return false; } });
    }
    @After public void cleanup() throws Exception { close(player); close(home); if(server!=null) server.shutdown(); landscape(1280,720); }
}
