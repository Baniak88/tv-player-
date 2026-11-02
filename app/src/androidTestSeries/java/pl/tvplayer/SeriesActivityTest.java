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
public class SeriesActivityTest {
    private Instrumentation instrument() { return InstrumentationRegistry.getInstrumentation(); }
    private Context context() { return instrument().getTargetContext(); }
    private MockWebServer server;
    private SeriesActivity activity;
    private int attempts;
    private boolean failFirst,empty;
    @Before public void setup() throws Exception {
        context().getSharedPreferences("watch-progress",0).edit().clear().commit();
        new LibraryStore(context()).save(Collections.emptyList(),Collections.emptySet());
        server=new MockWebServer(); server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                if(request.getPath().contains("player_api.php")) { attempts++; if(failFirst && attempts==1) return new MockResponse().setResponseCode(503); return new MockResponse().setHeader("Content-Type","application/json").setBody(empty?"{\"info\":null,\"episodes\":[]}":fixture()); }
                if(request.getPath().contains(".png")) return new MockResponse().setHeader("Content-Type","image/png").setBody(new okio.Buffer().write(artwork()));
                return new MockResponse().setResponseCode(404);
            }
        }); server.start();
    }
    private byte[] artwork() {
        Bitmap image=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(image); Paint p=new Paint(3);
        p.setShader(new LinearGradient(0,0,960,540,new int[]{0xFF162C3D,0xFF274E59,0xFFDBA873},null,Shader.TileMode.CLAMP)); c.drawRect(0,0,960,540,p); p.setShader(null);
        p.setColor(0xFFEDD4AA); c.drawCircle(740,165,75,p); p.setColor(0xFF192D34); Path mountain=new Path(); mountain.moveTo(0,540); mountain.lineTo(210,120); mountain.lineTo(460,540); c.drawPath(mountain,p); mountain=new Path(); mountain.moveTo(280,540); mountain.lineTo(630,240); mountain.lineTo(960,540); c.drawPath(mountain,p);
        p.setColor(0xFF0C1C27); c.drawRect(0,452,960,540,p); java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream(); image.compress(Bitmap.CompressFormat.PNG,100,out); image.recycle(); return out.toByteArray();
    }
    private String fixture() {
        String image=server.url("/art.png").toString();
        return "{\"info\":{\"name\":\"PL - KRÓLOWA KLEOPATRA\",\"plot\":\"Historia jednej z najbardziej niezwykłych władczyń. Polityka, rywalizacja i walka o przyszłość Egiptu.\",\"releaseDate\":\"2023-05-10\",\"rating\":\"7.4\",\"genre\":\"Dokumentalny · Historia\",\"cover\":\""+image+"\",\"backdrop_path\":[\""+image+"\"]},\"episodes\":{\"1\":["+episode(101,1,1,"Rywale","Młoda Kleopatra rozpoczyna walkę o tron. Nowe sojusze mogą zmienić bieg historii.",image)+","+episode(102,1,2,"Z wizytą w Rzymie","Podróż do Rzymu przynosi nowe wyzwania i trudne decyzje.",image)+"],\"2\":["+episode(201,2,1,"Nowy początek","Kolejny rozdział historii Egiptu.",image)+"]}}";
    }
    private String episode(int id,int season,int number,String title,String plot,String image) {
        return "{\"id\":"+id+",\"season\":"+season+",\"episode_num\":"+number+",\"title\":\"PL - KRÓLOWA KLEOPATRA - S0"+season+"E0"+number+" - "+title+"\",\"container_extension\":\"mp4\",\"info\":{\"duration_secs\":2820,\"plot\":\""+plot+"\",\"movie_image\":\""+image+"\"}}";
    }
    private Channel source() { return new Channel("PL - KRÓLOWA KLEOPATRA",server.url("/player_api.php?username=test&password=test&action=get_series_info&series_id=9").toString(),"Dokumenty","","series"); }
    private void start(boolean tv) { activity=(SeriesActivity)instrument().startActivitySync(SeriesActivity.intent(context(),source(),tv).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)); }
    private View root() { return activity.getWindow().getDecorView(); }
    private View find(View view,String value) { if(view instanceof TextView && value.equals(((TextView)view).getText().toString())) return view; if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) { View found=find(((ViewGroup)view).getChildAt(i),value); if(found!=null) return found; } return null; }
    private <T extends View> T type(View view,Class<T> clazz) { if(clazz.isInstance(view)) return clazz.cast(view); if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) { T found=type(((ViewGroup)view).getChildAt(i),clazz); if(found!=null) return found; } return null; }
    private void waitText(String text) throws Exception { long until=System.currentTimeMillis()+10000; while(find(root(),text)==null && System.currentTimeMillis()<until) { Thread.sleep(80); instrument().waitForIdleSync(); } assertNotNull(text,find(root(),text)); }
    private void screenshot(String name) throws Exception { Thread.sleep(1000); instrument().waitForIdleSync(); final Bitmap[] image={null}; instrument().runOnMainSync(() -> { View root=root(); image[0]=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888); root.draw(new Canvas(image[0])); }); try(java.io.FileOutputStream out=context().openFileOutput(name,Context.MODE_PRIVATE)) { image[0].compress(Bitmap.CompressFormat.PNG,100,out); } image[0].recycle(); }
    private void key(int code) throws Exception { try(android.os.ParcelFileDescriptor.AutoCloseInputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument().getUiAutomation().executeShellCommand("input keyevent "+code))) { byte[] bytes=new byte[256]; while(in.read(bytes)!=-1) {} } instrument().waitForIdleSync(); Thread.sleep(180); }
    @After public void close() throws Exception { if(activity!=null) { instrument().runOnMainSync(activity::finish); instrument().waitForIdleSync(); } server.shutdown(); }
    @Test public void detailScreenShowsMetadataAndSwitchesSeasons() throws Exception {
        start(false); waitText("KRÓLOWA KLEOPATRA"); waitText("Odcinki"); waitText("1. Rywale"); assertNull(find(root(),"PL - KRÓLOWA KLEOPATRA - S01E01 - Rywale")); assertNotNull(find(root(),"47 min")); screenshot("series-details.png");
        Spinner spinner=type(root(),Spinner.class); assertNotNull(spinner); instrument().runOnMainSync(() -> spinner.setSelection(1)); waitText("1. Nowy początek"); assertNull(find(root(),"1. Rywale")); screenshot("series-season2.png");
        instrument().runOnMainSync(() -> ((Button)find(root(),"+  Moja lista")).performClick()); waitText("✓  Moja lista"); long until=System.currentTimeMillis()+4000; while(!new LibraryStore(context()).loadFavorites().contains(source().url()) && System.currentTimeMillis()<until) Thread.sleep(50); assertTrue(new LibraryStore(context()).loadFavorites().contains(source().url()));
    }
    @Test public void continueUsesLastWatchedEpisodeAndStartsCorrectStream() throws Exception {
        String url=server.url("/series/test/test/102.mp4").toString(); WatchProgress.save(context(),url,65000,2820000,false);
        start(false); waitText("▶  Kontynuuj"); assertNotNull(find(root(),"Sezon 1 · Odcinek 2 · Z wizytą w Rzymie"));
        Instrumentation.ActivityMonitor monitor=instrument().addMonitor(PlayerActivity.class.getName(),null,false);
        instrument().runOnMainSync(() -> find(root(),"▶  Kontynuuj").performClick()); Activity player=monitor.waitForActivityWithTimeout(7000); assertNotNull(player); assertEquals(url,player.getIntent().getStringExtra("url")); assertEquals("episode",player.getIntent().getStringExtra("kind")); java.lang.reflect.Field position=PlayerActivity.class.getDeclaredField("position"); position.setAccessible(true); assertEquals(65000L,position.getLong(player)); instrument().runOnMainSync(player::finish); instrument().removeMonitor(monitor);
        WatchProgress.save(context(),url,2820000,2820000,true); SeriesDetails detail=SeriesDetails.parse(new java.io.StringReader(fixture()),source()); assertEquals(201, Integer.parseInt(WatchProgress.next(context(),detail.allEpisodes()).channel().url().replaceFirst(".*[/]","").replace(".mp4","")));
    }
    @Test public void connectionFailureCanRetryAndEmptyProviderDataStaysReadable() throws Exception {
        failFirst=true; empty=true; start(false); waitText("Spróbuj ponownie"); instrument().runOnMainSync(() -> find(root(),"Spróbuj ponownie").performClick()); waitText("Dostawca nie udostępnił odcinków tego serialu."); assertNotNull(find(root(),"Opis nie został udostępniony przez dostawcę.")); assertFalse(find(root(),"▶  Odtwórz").isEnabled()); assertEquals(2,attempts);
    }
    @Test public void televisionRemoteReachesSeasonPickerAndEpisodePlayback() throws Exception {
        start(true); waitText("Odcinki"); waitText("1. Rywale"); long until=System.currentTimeMillis()+5000; while(activity.getCurrentFocus()==null && System.currentTimeMillis()<until) Thread.sleep(80); assertTrue(root().getWidth()>root().getHeight()); screenshot("series-television.png");
        Spinner spinner=type(root(),Spinner.class); instrument().runOnMainSync(() -> spinner.requestFocus()); key(KeyEvent.KEYCODE_DPAD_CENTER); key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_CENTER); waitText("1. Nowy początek");
        ListView rows=type(root(),ListView.class); instrument().runOnMainSync(() -> { rows.setSelection(1); rows.requestFocus(); }); instrument().waitForIdleSync();
        Instrumentation.ActivityMonitor monitor=instrument().addMonitor(PlayerActivity.class.getName(),null,false); key(KeyEvent.KEYCODE_DPAD_CENTER); Activity player=monitor.waitForActivityWithTimeout(7000); assertNotNull("Remote did not start selected episode",player); assertTrue(player.getIntent().getBooleanExtra("television",false)); assertTrue(player.getIntent().getStringExtra("url").endsWith("/201.mp4")); instrument().runOnMainSync(player::finish); instrument().removeMonitor(monitor);
    }
}
