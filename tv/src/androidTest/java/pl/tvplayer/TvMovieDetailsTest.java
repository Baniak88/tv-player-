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
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TvMovieDetailsTest {
    private MockWebServer server;
    private TvActivity library;
    private MovieActivity movie;
    private Activity player;
    private final AtomicInteger apiCalls=new AtomicInteger();
    private Instrumentation instrument() { return InstrumentationRegistry.getInstrumentation(); }
    private Context context() { return instrument().getTargetContext(); }
    private byte[] artwork() {
        Bitmap bitmap=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(bitmap); Paint paint=new Paint(3);
        paint.setShader(new LinearGradient(0,0,960,540,new int[]{0xFF11283A,0xFF35616B,0xFFCA9871},null,Shader.TileMode.CLAMP)); canvas.drawRect(0,0,960,540,paint); paint.setShader(null);
        paint.setColor(0xFFEDD4AA); canvas.drawCircle(760,150,65,paint); paint.setColor(0xFF172E38); Path mountain=new Path(); mountain.moveTo(0,540); mountain.lineTo(260,130); mountain.lineTo(580,540); mountain.lineTo(780,260); mountain.lineTo(960,540); mountain.close(); canvas.drawPath(mountain,paint);
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes); bitmap.recycle(); return bytes.toByteArray();
    }
    @Before public void setup() throws Exception {
        TvTestUi.resetNavigation();
        new android.util.AtomicFile(new java.io.File(context().getFilesDir(),"tv-account.enc")).delete(); context().getSharedPreferences("watch-progress",0).edit().clear().commit();
        byte[] image=artwork(); server=new MockWebServer(); server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
            if(request.getPath().contains("player_api.php")) { apiCalls.incrementAndGet(); String art=server.url("/art.png").toString(); return new MockResponse().setHeader("Content-Type","application/json").setBody("{\"info\":{\"name\":\"PL - Północny szlak\",\"plot\":\"Podróż przez północne krajobrazy prowadzi do odkrycia rodzinnej tajemnicy.\",\"releasedate\":\"2024-03-18\",\"duration_secs\":7320,\"rating\":\"8.2\",\"genre\":\"Przygodowy · Dramat\",\"director\":\"Aleksander Kowalski\",\"cast\":\"Anna Nowak, Piotr Wiśniewski\",\"movie_image\":\""+art+"\",\"backdrop_path\":[\""+art+"\"]}}"); }
            if(request.getPath().contains("art.png")) return new MockResponse().setHeader("Content-Type","image/png").setBody(new okio.Buffer().write(image));
            return new MockResponse().setResponseCode(404);
        }}); server.start();
    }
    private Channel source() { return new Channel("PL - Północny szlak",server.url("/portal/movie/test/pass/731.mp4?token=original").toString(),"Przygoda","","movie").withLogo(server.url("/art.png").toString()); }
    private View find(View root,String text) { if(root instanceof TextView && text.equals(((TextView)root).getText().toString())) return root; if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) { View found=find(((ViewGroup)root).getChildAt(i),text); if(found!=null) return found; } return null; }
    private ImageView artwork(View root) { if(root instanceof ImageView) return (ImageView)root; if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) { ImageView found=artwork(((ViewGroup)root).getChildAt(i)); if(found!=null) return found; } return null; }
    private void waitText(Activity activity,String text) throws Exception { long deadline=System.currentTimeMillis()+10000; while(find(activity.getWindow().getDecorView(),text)==null && System.currentTimeMillis()<deadline) { Thread.sleep(80); instrument().waitForIdleSync(); } assertNotNull(text,find(activity.getWindow().getDecorView(),text)); }
    private void key(int code) throws Exception { try(android.os.ParcelFileDescriptor.AutoCloseInputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument().getUiAutomation().executeShellCommand("input keyevent "+code))) { byte[] buffer=new byte[256]; while(input.read(buffer)!=-1) {} } instrument().waitForIdleSync(); Thread.sleep(200); }
    private void screenshot() throws Exception {
        long deadline=System.currentTimeMillis()+6000; while(System.currentTimeMillis()<deadline) { ImageView art=artwork(movie.getWindow().getDecorView()); if(art!=null && art.getDrawable()!=null) break; Thread.sleep(80); instrument().waitForIdleSync(); }
        instrument().runOnMainSync(() -> { View root=movie.getWindow().getDecorView(); Bitmap image=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888); root.draw(new Canvas(image)); try(java.io.FileOutputStream output=context().openFileOutput("movie-television.png",Context.MODE_PRIVATE)) { image.compress(Bitmap.CompressFormat.PNG,100,output); } catch(Exception e) { throw new AssertionError(e); } finally { image.recycle(); } });
    }
    @Test public void catalogOpensMovieDetailsAndRemoteCanFavoriteAndPlay() throws Exception {
        Channel source=source(); new LibraryStore(context()).save(Collections.singletonList(source),new HashSet<>(Collections.singleton("https://example.com/keep.ts")));
        library=(TvActivity)instrument().startActivitySync(new Intent(context(),TvActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)); waitText(library,"Kanały i program TV");
        TvTestUi.waitFor("Movie library has no input focus",library::hasWindowFocus); key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER); waitText(library,source.name());
        TvTestUi.waitFor("Movie shelf has no focus",() -> { try { return ((TvShelfView)TvTestUi.field(library,"shelves")).hasFocus(); } catch(Exception e) { return false; } });
        Instrumentation.ActivityMonitor details=instrument().addMonitor(MovieActivity.class.getName(),null,false); key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); movie=(MovieActivity)details.waitForActivityWithTimeout(7000); instrument().removeMonitor(details); assertNotNull("Movie did not open its details",movie);
        waitText(movie,"Aleksander Kowalski"); waitText(movie,"2024   ·   2 godz. 2 min   ·   ★ 8.2");
        long deadline=System.currentTimeMillis()+5000; while(!find(movie.getWindow().getDecorView(),"+  Moja lista").isEnabled() && System.currentTimeMillis()<deadline) Thread.sleep(80);
        assertEquals(1,apiCalls.get()); screenshot(); key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER); waitText(movie,"✓  Moja lista");
        deadline=System.currentTimeMillis()+5000; Set<String> saved=new HashSet<>(); while(System.currentTimeMillis()<deadline) { saved=new LibraryStore(context()).loadFavorites(); if(saved.contains(source.url())) break; Thread.sleep(80); }
        assertTrue(saved.contains(source.url())); assertTrue(saved.contains("https://example.com/keep.ts"));
        key(KeyEvent.KEYCODE_DPAD_LEFT); Instrumentation.ActivityMonitor playback=instrument().addMonitor(PlayerActivity.class.getName(),null,false); key(KeyEvent.KEYCODE_MEDIA_PLAY); player=playback.waitForActivityWithTimeout(7000); instrument().removeMonitor(playback);
        assertNotNull("Remote did not start playback",player); assertEquals(source.url(),player.getIntent().getStringExtra("url")); assertTrue(player.getIntent().getBooleanExtra("television",false));
    }
    @Test public void m3uMovieCanResumeAndRestartWithoutMetadataApi() throws Exception {
        String url=server.url("/files/movie.mp4?token=original").toString(); WatchProgress.save(context(),url,65000,120000,false);
        movie=(MovieActivity)instrument().startActivitySync(new Intent(context(),MovieActivity.class).putExtra("television",true).putExtra("url",url).putExtra("name","Film M3U").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitText(movie,"▶  Kontynuuj"); assertEquals(0,apiCalls.get());
        key(KeyEvent.KEYCODE_DPAD_DOWN); assertEquals("Od początku",((TextView)movie.getCurrentFocus()).getText().toString());
        Instrumentation.ActivityMonitor playback=instrument().addMonitor(PlayerActivity.class.getName(),null,false); key(KeyEvent.KEYCODE_DPAD_CENTER); player=playback.waitForActivityWithTimeout(7000); instrument().removeMonitor(playback);
        assertNotNull(player); assertEquals(url,player.getIntent().getStringExtra("url")); assertTrue(player.getIntent().getBooleanExtra("television",false));
        java.lang.reflect.Field position=PlayerActivity.class.getDeclaredField("position"); position.setAccessible(true); assertEquals(0,position.getLong(player)); assertEquals(0,apiCalls.get());
    }
    @After public void close() throws Exception { for(Activity activity:new Activity[]{player,movie,library}) if(activity!=null && !activity.isDestroyed()) { instrument().runOnMainSync(activity::finish); instrument().waitForIdleSync(); } if(server!=null) server.shutdown(); }
}
