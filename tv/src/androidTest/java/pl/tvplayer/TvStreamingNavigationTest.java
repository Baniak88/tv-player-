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
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static pl.tvplayer.TvTestUi.*;

@RunWith(AndroidJUnit4.class)
public class TvStreamingNavigationTest {
    private TvActivity home;
    private Activity child;
    private MockWebServer server;
    private final AtomicInteger metadata=new AtomicInteger();
    @Before public void setup() throws Exception {
        systemReady(); landscape(1280,720);
        new android.util.AtomicFile(new File(context().getFilesDir(),"tv-account.enc")).delete();
        byte[] stream; try(InputStream in=instrument().getContext().getAssets().open("live-controls.ts"); ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] bytes=new byte[8192]; for(int n;(n=in.read(bytes))!=-1;) out.write(bytes,0,n); stream=out.toByteArray(); }
        List<byte[]> images=new ArrayList<>(); for(int i=0;i<8;i++) images.add(poster(i));
        server=new MockWebServer(); server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
            if(request.getPath().startsWith("/art/")) { int index=Integer.parseInt(request.getPath().substring(5).split("\\.")[0]); return new MockResponse().setHeader("Content-Type","image/png").setHeader("Cache-Control","public,max-age=86400").setBody(new okio.Buffer().write(images.get(index%8))); }
            if(request.getPath().contains("player_api.php")) { metadata.incrementAndGet(); return new MockResponse().setHeader("Content-Type","application/json").setBody("{\"info\":{\"name\":\"Film testowy\",\"plot\":\"Opis filmu z biblioteki dostawcy.\"},\"epg_listings\":[]}"); }
            if(request.getPath().endsWith(".ts")) return new MockResponse().setHeader("Content-Type","video/mp2t").setBody(new okio.Buffer().write(stream));
            return new MockResponse().setResponseCode(404);
        }}); server.start();
        List<Channel> channels=new ArrayList<>();
        for(int g=0;g<2;g++) for(int i=1;i<=12;i++) channels.add(new Channel((g==0?"Sport ":"Informacje ")+i,server.url("/live/demo/secret/"+(g*20+i)+".ts").toString(),g==0?"Sport":"Informacje","","tv").withOrder(g).withLogo(server.url("/art/0.png").toString()));
        String[] titles={"Północny szlak","Ostatni rejs","Miasto po zmroku","Po drugiej stronie","Droga do domu","Wielki błękit","Cicha przystań","Nowy horyzont"};
        for(int g=0;g<2;g++) for(int i=0;i<30;i++) channels.add(new Channel(titles[i%8]+" · "+(g*30+i+1),server.url("/movie/demo/secret/"+(g*30+i)+".mp4").toString(),g==0?"Kino na wieczór":"Przygoda","","movie").withOrder(g).withLogo(server.url("/art/"+(i%8)+".png").toString()));
        for(int title=0;title<4;title++) for(int season=1;season<=2;season++) for(int episode=1;episode<=2;episode++) channels.add(new Channel(new String[]{"Nocna cisza","Północny szlak","Za zakrętem","Ostatni ślad"}[title]+" S0"+season+" E0"+episode,server.url("/series/demo/secret/"+title+"-"+season+"-"+episode+".mp4").toString(),"Polskie seriale","","episode").withLogo(server.url("/art/"+(title+2)+".png").toString()));
        new LibraryStore(context()).save(channels,Collections.emptySet());
    }
    private byte[] poster(int index) {
        Bitmap bitmap=Bitmap.createBitmap(280,420,Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(bitmap); Paint paint=new Paint(3);
        int[] colors={0xFF407F8D,0xFFC6986F,0xFF5656A0,0xFF658C6D,0xFFAB6B76,0xFF3979AC,0xFF737D94,0xFF8A6C9A};
        paint.setShader(new LinearGradient(0,0,280,420,new int[]{colors[index],0xFF0E1B2E},null,Shader.TileMode.CLAMP)); canvas.drawPaint(paint); paint.setShader(null);
        paint.setColor(0xFFEAC994); canvas.drawCircle(202,88,35,paint); paint.setColor(0xFF15283B); Path mountain=new Path(); mountain.moveTo(0,420); mountain.lineTo(76,142); mountain.lineTo(202,420); canvas.drawPath(mountain,paint); paint.setColor(0xFF244454); mountain.reset(); mountain.moveTo(118,420); mountain.lineTo(235,195); mountain.lineTo(280,420); canvas.drawPath(mountain,paint);
        paint.setColor(0xFFF4F5F8); paint.setTextSize(17); paint.setTypeface(Typeface.DEFAULT_BOLD); canvas.drawText(new String[]{"PÓŁNOCNY SZLAK","OSTATNI REJS","MIASTO PO ZMROKU","PO DRUGIEJ STRONIE","DROGA DO DOMU","WIELKI BŁĘKIT","CICHA PRZYSTAŃ","NOWY HORYZONT"}[index],15,370,paint);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes); bitmap.recycle(); return bytes.toByteArray();
    }
    private void remote(int key) throws Exception { instrument().sendKeyDownUpSync(key); instrument().waitForIdleSync(); Thread.sleep(200); }
    private TvShelfView shelves() throws Exception { return (TvShelfView)field(home,"shelves"); }
    private void start(boolean fresh) throws Exception {
        home=(TvActivity)instrument().startActivitySync(new Intent(context(),TvActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)); waitFor("TV window missing",home::hasWindowFocus);
        if(fresh) waitFor("Home shortcut has no focus",() -> { try { TvShelfView shelves=shelves(); return shelves!=null && shelves.selected()!=null && shelves.selected().url().equals("section:tv"); } catch(Exception e) { return false; } });
    }
    private void waitSelected(String name) throws Exception { try { waitFor("Shelf selection missing: "+name,() -> { try { return shelves()!=null && shelves().selected()!=null && shelves().selected().name().equals(name) && shelves().hasFocus(); } catch(Exception e) { return false; } }); } catch(AssertionError failure) { android.util.Log.e("StreamingState", "section="+field(home,"section")+" group="+field(home,"group")+" busy="+field(home,"busy")+" ready="+field(home,"catalogReady")+" pending="+field(home,"pendingLocation")+" focused="+home.getCurrentFocus()+" selected="+(shelves()==null?null:shelves().selected())); capture("tv096-failure.png"); throw failure; } }
    private void capture(String name) {
        instrument().runOnMainSync(() -> { View root=home.getWindow().getDecorView(); Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888); root.draw(new Canvas(bitmap)); try(FileOutputStream out=context().openFileOutput(name,0)) { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); } catch(IOException e) { throw new AssertionError(e); } finally { bitmap.recycle(); } });
    }
    private void recreate() throws Exception {
        TvActivity previous=home; instrument().runOnMainSync(previous::recreate);
        waitFor("Activity did not recreate",() -> {
            TvActivity[] resumed={null};
            instrument().runOnMainSync(() -> { for(Activity candidate:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) if(candidate instanceof TvActivity && candidate!=previous) resumed[0]=(TvActivity)candidate; });
            if(resumed[0]==null) return false; home=resumed[0]; return home.hasWindowFocus();
        });
        assertNotSame(previous,home); assertTrue("Previous activity should be destroyed",previous.isDestroyed());
    }
    @Test public void returningFromLivePlaybackAndRecreationKeepsBouquetChannelAndScroll() throws Exception {
        start(true); remote(KeyEvent.KEYCODE_DPAD_CENTER); waitFor("TV channels missing",() -> find(home.getWindow().getDecorView(),"Sport 1")!=null);
        remote(KeyEvent.KEYCODE_DPAD_DOWN); remote(KeyEvent.KEYCODE_DPAD_DOWN); remote(KeyEvent.KEYCODE_DPAD_RIGHT); for(int i=0;i<6;i++) remote(KeyEvent.KEYCODE_DPAD_DOWN);
        ListView list=(ListView)field(home,"list"); waitFor("Wrong bouquet",() -> { try { return field(home,"group").equals("Informacje"); } catch(Exception e) { return false; } });
        int selected=list.getSelectedItemPosition(),first=list.getFirstVisiblePosition(); assertEquals(6,selected);
        Instrumentation.ActivityMonitor monitor=instrument().addMonitor(PlayerActivity.class.getName(),null,false);
        try { remote(KeyEvent.KEYCODE_DPAD_CENTER); remote(KeyEvent.KEYCODE_DPAD_CENTER); child=monitor.waitForActivityWithTimeout(15000); } finally { instrument().removeMonitor(monitor); }
        assertNotNull(child); Object player=field(child,"player"); String original=child.getIntent().getStringExtra("url");
        assertNotNull(player); remote(KeyEvent.KEYCODE_DPAD_UP); assertNotEquals(original,child.getIntent().getStringExtra("url")); assertSame("Zapping must reuse the decoder/player",player,field(child,"player"));
        remote(KeyEvent.KEYCODE_DPAD_DOWN); assertEquals(original,child.getIntent().getStringExtra("url")); assertSame(player,field(child,"player"));
        remote(KeyEvent.KEYCODE_BACK); if(!child.isFinishing()) remote(KeyEvent.KEYCODE_BACK); waitFor("Return lost TV focus",home::hasWindowFocus);
        assertSame("Returning must hand the same player back to preview",player,field(field(home,"livePreview"),"player"));
        waitFor("Channel selection lost after playback",() -> list.getSelectedItemPosition()==selected && list.hasFocus()); assertEquals(first,list.getFirstVisiblePosition()); assertEquals("Informacje",field(home,"group")); assertFalse((Boolean)field(home,"home"));
        recreate(); ListView restored=(ListView)field(home,"list"); waitFor("Recreation lost channel",() -> restored.hasFocus() && restored.getSelectedItemPosition()==selected); assertEquals("Informacje",field(home,"group")); assertEquals(first,restored.getFirstVisiblePosition()); capture("tv096-bouquet-return.png");
        remote(KeyEvent.KEYCODE_BACK); assertFalse((Boolean)field(home,"home")); remote(KeyEvent.KEYCODE_BACK); assertNotNull(node("Nawigacja")); remote(KeyEvent.KEYCODE_BACK); assertEquals("Informacje",field(home,"group"));
    }
    @Test public void shelvesOpenMoviesAndAllItemsThenReturnToTheSameCard() throws Exception {
        start(true); remote(KeyEvent.KEYCODE_DPAD_RIGHT); remote(KeyEvent.KEYCODE_DPAD_CENTER); waitSelected("Północny szlak · 1");
        for(int i=0;i<3;i++) remote(KeyEvent.KEYCODE_DPAD_RIGHT); String selected=shelves().selected().name(); capture("tv096-shelves.png");
        Instrumentation.ActivityMonitor monitor=instrument().addMonitor(MovieActivity.class.getName(),null,false);
        try { remote(KeyEvent.KEYCODE_DPAD_CENTER); child=monitor.waitForActivityWithTimeout(15000); } finally { instrument().removeMonitor(monitor); }
        assertNotNull("Movie details missing",child); remote(KeyEvent.KEYCODE_BACK); waitSelected(selected);
        recreate(); waitSelected(selected);
        click("TV"); waitFor("TV tab missing",() -> { try { return field(home,"section").equals("tv") && (Boolean)field(home,"catalogReady") && !((List<?>)field(home,"shown")).isEmpty(); } catch(Exception e) { return false; } }); click("Filmy"); waitSelected(selected);
        for(int i=0;i<21;i++) remote(KeyEvent.KEYCODE_DPAD_RIGHT); waitFor("More card missing",() -> { try { return shelves().index()==24; } catch(Exception e) { return false; } });
        remote(KeyEvent.KEYCODE_DPAD_CENTER); waitFor("Complete bouquet missing",() -> { try { return ((List<?>)field(home,"shown")).size()==30 && field(home,"shelves")==null; } catch(Exception e) { return false; } });
        remote(KeyEvent.KEYCODE_BACK); waitFor("Back lost the shelf more card",() -> { try { return shelves()!=null && shelves().hasFocus() && shelves().index()==24; } catch(Exception e) { return false; } }); assertFalse((Boolean)field(home,"home"));
    }
    @Test public void m3uSeasonAndEpisodeResumeAfterRecreationAndBackReturnsToSeries() throws Exception {
        start(true); remote(KeyEvent.KEYCODE_DPAD_RIGHT); remote(KeyEvent.KEYCODE_DPAD_RIGHT); remote(KeyEvent.KEYCODE_DPAD_CENTER); waitSelected("Nocna cisza"); capture("tv096-streaming-series.png");
        remote(KeyEvent.KEYCODE_DPAD_CENTER); waitFor("Seasons missing",() -> find(home.getWindow().getDecorView(),"Sezon 2")!=null); remote(KeyEvent.KEYCODE_DPAD_LEFT); remote(KeyEvent.KEYCODE_DPAD_DOWN); remote(KeyEvent.KEYCODE_DPAD_DOWN); remote(KeyEvent.KEYCODE_DPAD_RIGHT); remote(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals("Sezon 2",field(home,"group")); int selected=((ListView)field(home,"list")).getSelectedItemPosition(); assertEquals(1,selected); recreate();
        waitFor("Season restoration failed",() -> { try { return field(home,"group").equals("Sezon 2") && ((ListView)field(home,"list")).getSelectedItemPosition()==selected; } catch(Exception e) { return false; } }); capture("tv096-streaming-seasons.png");
        remote(KeyEvent.KEYCODE_BACK); waitSelected("Nocna cisza"); assertEquals("",field(home,"seriesTitle")); assertFalse((Boolean)field(home,"home"));
    }
    @Test public void homeUsesBoundedLocalPreviewsWithThumbnailsWithoutProviderMetadataCalls() throws Exception {
        landscape(960,540); start(true); waitFor("Home movie preview missing",() -> find(home.getWindow().getDecorView(),"Filmy z Twojej biblioteki   ›")!=null);
        LibraryStore store=new LibraryStore(context()); assertEquals(18,store.previewSection("movie",18).size()); assertEquals(0,store.previewSection("movie",0).size());
        assertEquals(0,metadata.get()); waitFor("Home thumbnail did not load",() -> images(home.getWindow().getDecorView())>0); capture("tv096-streaming-home.png");
        remote(KeyEvent.KEYCODE_DPAD_DOWN); waitSelected("Północny szlak · 1"); assertEquals(0,metadata.get());
    }
    private int images(View view) { int count=view instanceof ImageView && ((ImageView)view).getDrawable()!=null?1:0; if(view instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) count+=images(group.getChildAt(i)); return count; }
    @After public void cleanup() throws Exception { close(child); close(home); if(server!=null) server.shutdown(); landscape(1280,720); }
}
