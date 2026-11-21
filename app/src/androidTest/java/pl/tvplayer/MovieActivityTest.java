package pl.tvplayer;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MovieActivityTest {
    private Instrumentation instrument() { return InstrumentationRegistry.getInstrumentation(); }
    private Context context() { return instrument().getTargetContext(); }
    private MockWebServer server;
    private MovieActivity activity;
    private Activity main,player;
    private volatile int apiCalls;
    private volatile String lastApi="";
    private boolean failFirst;
    @Before public void setup() throws Exception {
        context().getSharedPreferences("watch-progress",0).edit().clear().commit();
        server=new MockWebServer(); server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
            if(request.getPath().contains("player_api.php")) { apiCalls++; lastApi=request.getPath(); if(failFirst && apiCalls==1) return new MockResponse().setResponseCode(503); return new MockResponse().setHeader("Content-Type","application/json").setBody(fixture()); }
            if(request.getPath().contains(".png")) return new MockResponse().setHeader("Content-Type","image/png").setBody(new okio.Buffer().write(artwork()));
            return new MockResponse().setResponseCode(404);
        }}); server.start();
        new LibraryStore(context()).save(Arrays.asList(source(),new Channel("Zachowane TV","https://example.com/keep.ts","TV","","tv")),Collections.singleton("https://example.com/keep.ts"));
    }
    private byte[] artwork() {
        Bitmap bitmap=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(bitmap); Paint p=new Paint(3); p.setShader(new LinearGradient(0,0,960,540,new int[]{0xFF0F2439,0xFF446D7B,0xFFD7B58A},null,Shader.TileMode.CLAMP)); c.drawRect(0,0,960,540,p); p.setShader(null); p.setColor(0xFFE0D5B9); c.drawCircle(725,160,60,p); p.setColor(0xFF243E4D); Path mountain=new Path(); mountain.moveTo(0,470); mountain.lineTo(345,90); mountain.lineTo(690,470); c.drawPath(mountain,p); mountain=new Path(); mountain.moveTo(380,540); mountain.lineTo(750,240); mountain.lineTo(960,420); mountain.lineTo(960,540); c.drawPath(mountain,p); p.setColor(0xFF132C3C); c.drawRect(0,460,960,540,p); java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes); bitmap.recycle(); return bytes.toByteArray();
    }
    private Channel source() { return new Channel("PL - Północny szlak",server.url("/portal/movie/test/pass/731.mp4?token=original").toString(),"Kino","","movie").withLogo(server.url("/poster.png").toString()).withOrder(0); }
    private String fixture() { return "{\"info\":{\"name\":\"PL - Północny szlak\",\"plot\":\"Po latach rozłąki rodzeństwo wyrusza w podróż przez surowe krajobrazy północy. W drodze do rodzinnego domu odkrywają tajemnicę, która zmieni ich spojrzenie na przeszłość.\",\"releasedate\":\"2024-03-18\",\"duration_secs\":7320,\"rating\":\"8.2\",\"genre\":\"Przygodowy · Dramat\",\"director\":\"Aleksander Kowalski\",\"cast\":\"Anna Nowak, Piotr Wiśniewski, Maria Zielińska\",\"movie_image\":\""+server.url("/poster.png")+"\",\"backdrop_path\":[\""+server.url("/backdrop.png")+"\"]}}"; }
    private Intent intent(Channel source) { return new Intent(context(),MovieActivity.class).putExtra("url",source.url()).putExtra("name",source.name()).putExtra("group",source.group()).putExtra("logo",source.logo()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK); }
    private void start(Channel source) { activity=(MovieActivity)instrument().startActivitySync(intent(source)); }
    private View root() { return activity.getWindow().getDecorView(); }
    private View find(View root,String value) { if(root instanceof TextView && value.equals(((TextView)root).getText().toString())) return root; if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) { View hit=find(((ViewGroup)root).getChildAt(i),value); if(hit!=null) return hit; } return null; }
    private void waitText(String text) throws Exception { long until=System.currentTimeMillis()+10000; while(find(root(),text)==null && System.currentTimeMillis()<until) { Thread.sleep(80); instrument().waitForIdleSync(); } assertNotNull(text,find(root(),text)); }
    private void waitReady() throws Exception { waitText("+  Moja lista"); long until=System.currentTimeMillis()+4000; while(!find(root(),"+  Moja lista").isEnabled() && System.currentTimeMillis()<until) { Thread.sleep(80); instrument().waitForIdleSync(); } assertTrue(find(root(),"+  Moja lista").isEnabled()); }
    private void closeActivity(Activity target) throws Exception { if(target==null || target.isDestroyed()) return; instrument().runOnMainSync(target::finish); long until=System.currentTimeMillis()+5000; while(!target.isDestroyed() && System.currentTimeMillis()<until) { Thread.sleep(80); instrument().waitForIdleSync(); } }
    private Activity clickPlayback(String label) throws Exception { Instrumentation.ActivityMonitor monitor=instrument().addMonitor(PlayerActivity.class.getName(),null,false); instrument().runOnMainSync(() -> find(root(),label).performClick()); player=monitor.waitForActivityWithTimeout(7000); instrument().removeMonitor(monitor); assertNotNull("Movie did not start",player); return player; }
    private long position(Activity target) throws Exception { java.lang.reflect.Field f=PlayerActivity.class.getDeclaredField("position"); f.setAccessible(true); return f.getLong(target); }
    private void screenshot(String name) throws Exception { Thread.sleep(1000); instrument().waitForIdleSync(); final Bitmap[] image={null}; instrument().runOnMainSync(() -> { View root=root(); image[0]=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888); root.draw(new Canvas(image[0])); }); try(java.io.FileOutputStream out=context().openFileOutput(name,Context.MODE_PRIVATE)) { image[0].compress(Bitmap.CompressFormat.PNG,100,out); } image[0].recycle(); }
    @After public void close() throws Exception { closeActivity(player); closeActivity(activity); closeActivity(main); server.shutdown(); }
    @Test public void libraryMovieOpensDetailsWithMetadataAndKeepsOtherFavorites() throws Exception {
        main=instrument().startActivitySync(new Intent(context(),MainActivity.class).putExtra("section","movie").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)); View root=main.getWindow().getDecorView(); long until=System.currentTimeMillis()+8000; while(find(root,"PL - Północny szlak")==null && System.currentTimeMillis()<until) { Thread.sleep(80); instrument().waitForIdleSync(); } View title=find(root,"PL - Północny szlak"); assertNotNull(title);
        View frame=title; while(!(frame instanceof FrameLayout)) frame=(View)frame.getParent(); final View hero=frame;
        Instrumentation.ActivityMonitor monitor=instrument().addMonitor(MovieActivity.class.getName(),null,false); instrument().runOnMainSync(hero::performClick); activity=(MovieActivity)monitor.waitForActivityWithTimeout(7000); instrument().removeMonitor(monitor); assertNotNull("Selecting the movie did not open details",activity);
        waitText("Anna Nowak, Piotr Wiśniewski, Maria Zielińska"); assertNotNull(find(root(),"Północny szlak")); assertNotNull(find(root(),"2024   ·   2 godz. 2 min   ·   ★ 8.2")); assertNotNull(find(root(),"Aleksander Kowalski")); assertTrue(lastApi.contains("action=get_vod_info")); assertTrue(lastApi.contains("vod_id=731")); screenshot("movie-details.png"); waitReady();
        instrument().runOnMainSync(() -> find(root(),"+  Moja lista").performClick()); waitText("✓  Moja lista"); until=System.currentTimeMillis()+4000; while(!new LibraryStore(context()).loadFavorites().contains(source().url()) && System.currentTimeMillis()<until) Thread.sleep(80); Set<String> saved=new LibraryStore(context()).loadFavorites(); assertTrue(saved.contains(source().url())); assertTrue(saved.contains("https://example.com/keep.ts"));
        Activity playback=clickPlayback("▶  Odtwórz"); assertEquals(source().url(),playback.getIntent().getStringExtra("url")); assertEquals("movie",playback.getIntent().getStringExtra("kind"));
    }
    @Test public void legacyResumeAndExplicitRestartUseCorrectPositions() throws Exception {
        byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(source().url().getBytes(java.nio.charset.StandardCharsets.UTF_8)); String bookmark=android.util.Base64.encodeToString(hash,android.util.Base64.NO_WRAP); context().getSharedPreferences(PlayerActivity.class.getName(),0).edit().putLong(bookmark,65000).commit();
        start(source()); waitText("▶  Kontynuuj"); Activity resumed=clickPlayback("▶  Kontynuuj"); assertEquals(65000,position(resumed)); closeActivity(resumed); waitText("Od początku"); Activity restarted=clickPlayback("Od początku"); assertEquals(0,position(restarted)); assertEquals(source().url(),restarted.getIntent().getStringExtra("url")); assertFalse(WatchProgress.read(context(),source().url()).completed());
    }
    @Test public void metadataFailureDoesNotBlockPlaybackAndCanRetry() throws Exception {
        failFirst=true; start(source()); waitText("Ponów pobieranie opisu"); assertTrue(find(root(),"▶  Odtwórz").isEnabled()); instrument().runOnMainSync(() -> find(root(),"Ponów pobieranie opisu").performClick()); waitText("Anna Nowak, Piotr Wiśniewski, Maria Zielińska"); assertEquals(2,apiCalls); assertNull(find(root(),"Ponów pobieranie opisu"));
    }
    @Test public void plainM3uMoviePlaysWithoutMetadataApi() throws Exception {
        Channel generic=new Channel("Film z M3U",server.url("/files/video.mp4?token=original").toString(),"Kino","","movie"); start(generic); waitText("Opis nie został udostępniony przez dostawcę."); waitReady(); assertEquals(0,apiCalls); Activity playback=clickPlayback("▶  Odtwórz"); assertEquals(generic.url(),playback.getIntent().getStringExtra("url")); assertEquals(0,apiCalls);
    }
}
