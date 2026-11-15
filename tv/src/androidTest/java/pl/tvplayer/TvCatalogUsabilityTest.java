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
    private void shell(String command) throws Exception { try(android.os.ParcelFileDescriptor.AutoCloseInputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument().getUiAutomation().executeShellCommand(command))) { byte[] buffer=new byte[512]; while(in.read(buffer)!=-1) {} } }
    private void viewport(int width,int height) throws Exception { shell("wm size "+width+"x"+height); shell("wm density 160"); Thread.sleep(300); }
    private void start() throws Exception { home=(TvActivity)instrument().startActivitySync(new Intent(context(),TvActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)); home.getPreferences(0).edit().remove("list-movie").remove("list-series").commit(); waitFor("Home missing",() -> find(home.getWindow().getDecorView(),"Co oglądamy?")!=null); waitFor("Home has no focus",home::hasWindowFocus); }
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
            screenshot("tv095-live-"+width+".png");
            key(KeyEvent.KEYCODE_BACK); assertFalse(list.hasFocus()); assertNotNull(find(home.getWindow().getDecorView(),"UK| SKY CINEMA ACTION FHD 1")); key(KeyEvent.KEYCODE_BACK);
            waitFor("Second Back must return home",() -> find(home.getWindow().getDecorView(),"Co oglądamy?")!=null);
            key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER); waitFor("Real film missing",() -> find(home.getWindow().getDecorView(),"Ostatni rejs – wersja reżyserska (2024) · 1")!=null);
            grid=(GridView)field(home,"posters"); assertEquals(width==960?5:6,grid.getNumColumns()); assertNull(find(home.getWindow().getDecorView(),"UK| SKY CINEMA ACTION FHD 1"));
            key(KeyEvent.KEYCODE_DPAD_RIGHT); // The right arrow also works without confirming the category.
            GridView posters=grid; waitFor("Movie grid has no focus",posters::hasFocus);
            TextView detail=(TextView)field(home,"selectionInfo"); assertTrue(detail.getText().toString().contains("wersja reżyserska (2024)"));
            ImageView artwork=(ImageView)((android.widget.FrameLayout)((android.widget.LinearLayout)grid.getChildAt(0)).getChildAt(0)).getChildAt(1); assertEquals(ImageView.ScaleType.FIT_CENTER,artwork.getScaleType());
            screenshot("tv095-movies-"+width+".png");
            click("Opcje"); click("Widok: lista"); assertEquals(View.VISIBLE,((ListView)field(home,"list")).getVisibility()); screenshot("tv095-movies-list-"+width+".png");
            click("Opcje"); click("Widok: plakaty"); assertEquals(View.VISIBLE,((GridView)field(home,"posters")).getVisibility());
            close(home); home=null;
        }
    }
    @Test public void flatM3uSeriesOpenFoldersSeasonsAndOriginalEpisodeWithRemote() throws Exception {
        viewport(960,540); start(); key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER);
        waitFor("Series did not group into two titles",() -> { try { return ((List<?>)field(home,"shown")).size()==2; } catch(Exception e) { return false; } });
        screenshot("tv095-series-960.png"); key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER);
        waitFor("Season categories missing",() -> find(home.getWindow().getDecorView(),"Sezon 2")!=null); screenshot("tv095-episodes-960.png");
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_RIGHT);
        Instrumentation.ActivityMonitor monitor=instrument().addMonitor(PlayerActivity.class.getName(),null,false);
        try { key(KeyEvent.KEYCODE_MEDIA_PLAY); player=monitor.waitForActivityWithTimeout(10000); } finally { instrument().removeMonitor(monitor); }
        assertNotNull("Episode did not start",player); assertEquals(server.url("/series/u/p/1.mp4?token=original").toString(),player.getIntent().getStringExtra("url")); assertTrue(player.getIntent().getBooleanExtra("television",false));
        close(player); player=null; waitFor("Catalog did not regain focus",home::hasWindowFocus); key(KeyEvent.KEYCODE_BACK); key(KeyEvent.KEYCODE_BACK);
        waitFor("Back did not restore the series grid",() -> { try { return ((GridView)field(home,"posters")).getVisibility()==View.VISIBLE; } catch(Exception e) { return false; } });
    }
    @After public void cleanup() throws Exception { close(player); close(home); if(server!=null) server.shutdown(); shell("wm size 720x1280"); shell("wm density 160"); }
}
