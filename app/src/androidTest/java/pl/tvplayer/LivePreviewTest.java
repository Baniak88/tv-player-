package pl.tvplayer;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
@RunWith(AndroidJUnit4.class)
public class LivePreviewTest {
    private Instrumentation instrument() { return InstrumentationRegistry.getInstrumentation(); }
    private Context context() { return instrument().getTargetContext(); }
    private MockWebServer server;
    private MainActivity main;
    private PlayerActivity full;
    private Channel a,b;
    private final AtomicInteger streams=new AtomicInteger(),sd=new AtomicInteger(),hd=new AtomicInteger(),guides=new AtomicInteger();
    @Before public void setup() throws Exception {
        context().getSharedPreferences("playback-network",Context.MODE_PRIVATE).edit().putBoolean("weak",false).commit();
        context().getSharedPreferences("live-preview",Context.MODE_PRIVATE).edit().clear().commit();
        byte[] payload;
        try(java.io.InputStream input=instrument().getContext().getAssets().open("live-controls.ts");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) { byte[] bytes=new byte[8192]; int count; while((count=input.read(bytes))!=-1) out.write(bytes,0,count); payload=out.toByteArray(); }
        server=new MockWebServer();
        long now=System.currentTimeMillis()/1000; java.time.format.DateTimeFormatter time=java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z").withZone(java.time.ZoneOffset.UTC);
        StringBuilder xml=new StringBuilder("<tv>");
        for(String id:new String[]{"A","B"}) { xml.append("<channel id=\"").append(id).append("\"><display-name>Kanał ").append(id).append("</display-name></channel><programme channel=\"").append(id).append("\" start=\"").append(time.format(java.time.Instant.ofEpochSecond(now-600))).append("\" stop=\"").append(time.format(java.time.Instant.ofEpochSecond(now+3000))).append("\"><title>Program kanału ").append(id).append("</title></programme>"); } xml.append("</tv>");
        server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
            String path=request.getPath();
            if(path.startsWith("/guide.xml")) { guides.incrementAndGet(); return new MockResponse().setHeader("Content-Type","application/xml").setBody(xml.toString()); }
            if(path.startsWith("/master.m3u8")) return new MockResponse().setHeader("Content-Type","application/vnd.apple.mpegurl").setBody("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=150000,RESOLUTION=160x90,CODECS=\"avc1.42e028,mp4a.40.2\"\nsd/index.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=600000,RESOLUTION=1920x1080,CODECS=\"avc1.42e028,mp4a.40.2\"\nhd/index.m3u8\n");
            if(path.startsWith("/sd/index.m3u8") || path.startsWith("/hd/index.m3u8")) {
                if(path.startsWith("/sd")) sd.incrementAndGet(); else hd.incrementAndGet();
                return new MockResponse().setHeader("Content-Type","application/vnd.apple.mpegurl").setBody("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:60\n#EXT-X-MEDIA-SEQUENCE:0\n#EXTINF:60.0,\nsegment.ts\n#EXT-X-ENDLIST\n");
            }
            if(path.contains("segment.ts")) { MockResponse response=new MockResponse().setHeader("Content-Type","video/mp2t").setChunkedBody(new okio.Buffer().write(payload),1880); if(path.startsWith("/sd")) response.throttleBody(4096,1,TimeUnit.SECONDS); return response; }
            if(path.equals("/a.ts") || path.equals("/b.ts")) { streams.incrementAndGet(); return new MockResponse().setHeader("Content-Type","video/mp2t").setChunkedBody(new okio.Buffer().write(payload),1880); }
            return new MockResponse().setBody("[]");
        }}); server.start();
        String guide="xmltv:"+server.url("/guide.xml");
        a=new Channel("Kanał A",server.url("/a.ts").toString(),"Sport","A","tv",guide,0,"UTC",0);
        b=new Channel("Kanał B",server.url("/b.ts").toString(),"Sport","B","tv",guide,0,"UTC",0);
        new LibraryStore(context()).save(Arrays.asList(a,b,new Channel("Kanał C",server.url("/c.ts").toString(),"Dzieci","","tv").withOrder(1)),Collections.emptySet());
    }
    private View find(View root,String name) {
        if(root instanceof TextView && (name.equals(((TextView)root).getText().toString()) || name.equals("Program kanału A") && ((TextView)root).getText().toString().contains(name))) return root;
        if(name.contentEquals(root.getContentDescription()==null?"":root.getContentDescription())) return root;
        if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) { View found=find(((ViewGroup)root).getChildAt(i),name); if(found!=null) return found; } return null;
    }
    private View waitView(View root,String name) throws Exception {
        long until=SystemClock.elapsedRealtime()+10000; View result;
        while((result=find(root,name))==null && SystemClock.elapsedRealtime()<until) { instrument().waitForIdleSync(); Thread.sleep(60); }
        assertNotNull("Missing "+name,result); return result;
    }
    private Object field(Object target,String name) { try { java.lang.reflect.Field field=target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); } catch(Exception error) { throw new AssertionError(error); } }
    private LivePreview preview() { return (LivePreview)field(main,"preview"); }
    private ExoPlayer previewPlayer() { return (ExoPlayer)field(preview(),"player"); }
    private void clickItem(View item) {
        View row=item; while(row.getParent() instanceof View && !(row.getParent() instanceof AdapterView)) row=(View)row.getParent(); final View selected=row;
        instrument().runOnMainSync(() -> { AdapterView<?> list=(AdapterView<?>)selected.getParent(); list.performItemClick(selected,list.getPositionForView(selected),0); });
    }
    private void waitPlaying(java.util.function.Supplier<ExoPlayer> supplier) throws Exception {
        long until=SystemClock.elapsedRealtime()+30000; boolean playing=false;
        while(SystemClock.elapsedRealtime()<until) { final boolean[] value={false}; instrument().runOnMainSync(() -> { ExoPlayer player=supplier.get(); value[0]=player!=null && player.isPlaying() && player.getVideoSize().width>0 && pictureVisible(player); }); if(value[0]) { playing=true; break; } Thread.sleep(80); }
        assertTrue("Fixture video did not play",playing);
    }
    private boolean pictureVisible(ExoPlayer player) {
        PlayerView view=null;
        if(main!=null && previewPlayer()==player) view=(PlayerView)field(preview(),"video");
        else if(full!=null && field(full,"player")==player) view=(PlayerView)field(full,"view");
        if(view==null) return false; View shutter=view.findViewById(androidx.media3.ui.R.id.exo_shutter); return shutter!=null && shutter.getVisibility()!=View.VISIBLE;
    }
    private void start(boolean inside) throws Exception {
        Intent intent=new Intent(context(),MainActivity.class).putExtra("section","tv").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK); if(inside) intent.putExtra("bouquet","Sport"); main=(MainActivity)instrument().startActivitySync(intent);
        waitView(main.getWindow().getDecorView(),inside?"Kanał A":"Sport");
    }
    private void screenshot(String name) throws Exception {
        Thread.sleep(650); instrument().waitForIdleSync(); Bitmap bitmap=instrument().getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(context().getFilesDir(),name+".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); } bitmap.recycle();
    }
    @Test public void bouquetOpensPreviewAndFullScreenReturnsTheSamePlayerWithoutAnotherConnection() throws Exception {
        start(false); View root=main.getWindow().getDecorView(); assertNull(previewPlayer());
        Instrumentation.ActivityMonitor monitor=instrument().addMonitor(PlayerActivity.class.getName(),null,false);
        try {
            clickItem(waitView(root,"Sport")); waitPlaying(this::previewPlayer); assertEquals(a.url(),preview().selectedUrl()); assertEquals(0,monitor.getHits()); assertEquals(1,streams.get());
            long until=SystemClock.elapsedRealtime()+5000; TextView schedule=(TextView)field(preview(),"schedule"); while(!schedule.getText().toString().contains("Program kanału A") && SystemClock.elapsedRealtime()<until) { Thread.sleep(80); instrument().waitForIdleSync(); } assertTrue(schedule.getText().toString().contains("Program kanału A"));
            screenshot("tv-preview-portrait"); ExoPlayer original=previewPlayer();
            View button=waitView(root,"Otwórz podgląd na pełnym ekranie"); instrument().runOnMainSync(button::performClick); full=(PlayerActivity)monitor.waitForActivityWithTimeout(7000); assertNotNull(full); waitPlaying(() -> (ExoPlayer)field(full,"player"));
            assertSame("Full screen discarded the preview buffer",original,field(full,"player")); assertEquals(1,streams.get());
            PlayerView fullVideo=(PlayerView)field(full,"view"); instrument().runOnMainSync(() -> { fullVideo.showController(); fullVideo.findViewById(androidx.media3.ui.R.id.exo_next).performClick(); }); waitPlaying(() -> (ExoPlayer)field(full,"player"));
            assertEquals(b.url(),full.getIntent().getStringExtra("url")); ExoPlayer selectedInFull=(ExoPlayer)field(full,"player");
            instrument().runOnMainSync(full::finish); waitPlaying(this::previewPlayer); assertSame(selectedInFull,previewPlayer()); assertEquals(b.url(),preview().selectedUrl()); assertEquals(2,streams.get()); full=null;
            instrument().runOnMainSync(main::finish); instrument().waitForIdleSync(); start(false); View reopened=main.getWindow().getDecorView(); clickItem(waitView(reopened,"Sport")); waitPlaying(this::previewPlayer); assertEquals("Full-screen selection was not saved for the bouquet",b.url(),preview().selectedUrl());
            clickItem(waitView(reopened,"Kanał A")); waitPlaying(this::previewPlayer); assertEquals(a.url(),preview().selectedUrl());
            instrument().runOnMainSync(() -> find(reopened,"Wróć do listy bukietów").performClick()); instrument().waitForIdleSync(); assertNull(previewPlayer()); assertFalse(GuideRepository.get(context()).isObserving(a));
            clickItem(waitView(reopened,"Sport")); waitPlaying(this::previewPlayer); assertEquals("Last selected channel in this bouquet was lost",a.url(),preview().selectedUrl());
        } finally { instrument().removeMonitor(monitor); }
    }
    @Test public void landscapeRecreationKeepsSelectionAndPlacesListBesidePreview() throws Exception {
        start(true); clickItem(waitView(main.getWindow().getDecorView(),"Kanał B")); waitPlaying(this::previewPlayer);
        Instrumentation.ActivityMonitor monitor=instrument().addMonitor(MainActivity.class.getName(),null,false); MainActivity old=main;
        instrument().runOnMainSync(() -> old.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)); main=(MainActivity)monitor.waitForActivityWithTimeout(10000); instrument().removeMonitor(monitor); assertNotNull(main); assertNotSame(old,main); waitPlaying(this::previewPlayer); assertEquals(b.url(),preview().selectedUrl());
        final Rect[] bounds={new Rect(),new Rect()}; instrument().runOnMainSync(() -> { ((View)field(main,"list")).getGlobalVisibleRect(bounds[0]); preview().panel.getGlobalVisibleRect(bounds[1]); });
        assertTrue("Channel list should be left of the preview",bounds[0].right<=bounds[1].left); assertTrue(bounds[0].width()>Ui.dp(context(),200)); assertTrue(bounds[1].height()>Ui.dp(context(),100)); screenshot("tv-preview-landscape");
    }
    private View dialogView(String name) throws Exception {
        long until=SystemClock.elapsedRealtime()+5000;
        while(SystemClock.elapsedRealtime()<until) { final View[] target={null}; instrument().runOnMainSync(() -> { for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews()) if(root.isShown()) { View found=find(root,name); if(found!=null && found.getWidth()>0) { target[0]=found; break; } } }); if(target[0]!=null) return target[0]; Thread.sleep(80); } throw new AssertionError("Missing dialog option "+name);
    }
    @Test public void weakInternetOptionPersistsAndChoosesLowerAdaptiveVariantOnThrottledConnection() throws Exception {
        full=(PlayerActivity)instrument().startActivitySync(new Intent(context(),PlayerActivity.class).putExtra("url",server.url("/master.m3u8").toString()).putExtra("name","Test internetu").putExtra("kind","tv").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitPlaying(() -> (ExoPlayer)field(full,"player"));
        // Establish a high-quality starting point independent of the emulator's bandwidth estimate.
        instrument().runOnMainSync(() -> { ExoPlayer player=(ExoPlayer)field(full,"player"); player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setForceHighestSupportedBitrate(true).build()); player.seekTo(0); });
        long highUntil=SystemClock.elapsedRealtime()+8000; while(hd.get()==0 && SystemClock.elapsedRealtime()<highUntil) Thread.sleep(60);
        assertTrue("Normal playback could not select the available 1080p variant",hd.get()>0);
        View options=waitView(full.getWindow().getDecorView(),"Opcje"); instrument().runOnMainSync(options::performClick); clickItem(dialogView("Połączenie internetowe"));
        clickItem(dialogView("Słabszy internet · większy bufor"));
        long until=SystemClock.elapsedRealtime()+8000; while(!PlaybackSettings.weakInternet(context()) && SystemClock.elapsedRealtime()<until) Thread.sleep(60); assertTrue(PlaybackSettings.weakInternet(context()));
        waitPlaying(() -> (ExoPlayer)field(full,"player")); assertTrue("Adaptive quality did not switch to the lower variant",sd.get()>0);
        android.util.Log.i("LibraryBenchmark","Lower adaptive variant decoded over a 4 KB/s test connection");
        instrument().runOnMainSync(full::finish); full=null; start(true); waitPlaying(this::previewPlayer); Thread.sleep(500); assertEquals("Weak connection downloaded a bulk XMLTV feed in the background",0,guides.get());
        View epg=waitView(main.getWindow().getDecorView(),"Program TV i archiwum: Kanał A"); instrument().runOnMainSync(epg::performClick);
        until=SystemClock.elapsedRealtime()+6000; while(guides.get()==0 && SystemClock.elapsedRealtime()<until) Thread.sleep(60); assertEquals("Explicit EPG should still download the provider's guide",1,guides.get());
        dialogView("Program kanału A"); instrument().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
    }
    @After public void cleanup() throws Exception {
        if(full!=null && !full.isDestroyed()) instrument().runOnMainSync(full::finish);
        if(main!=null && !main.isDestroyed()) instrument().runOnMainSync(main::finish); instrument().waitForIdleSync();
        context().getSharedPreferences("playback-network",Context.MODE_PRIVATE).edit().putBoolean("weak",false).commit(); server.shutdown();
    }
}
