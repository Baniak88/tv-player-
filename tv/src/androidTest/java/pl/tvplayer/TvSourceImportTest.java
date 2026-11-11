package pl.tvplayer;

import android.content.*;
import android.view.KeyEvent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static pl.tvplayer.TvTestUi.*;

@RunWith(AndroidJUnit4.class)
public class TvSourceImportTest {
    private TvActivity home;
    private void start() throws Exception {
        home=(TvActivity)instrument().startActivitySync(new Intent(context(),TvActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitFor("Home missing",() -> find(home.getWindow().getDecorView(),"Co oglądamy?")!=null);
    }
    @Before public void setup() throws Exception {
        systemReady();
        new android.util.AtomicFile(new java.io.File(context().getFilesDir(),"tv-account.enc")).delete();
        new LibraryStore(context()).save(Collections.emptyList(),Collections.emptySet()); start();
    }
    @After public void cleanup() { close(home); }
    @Test public void m3uImportRefreshFailureAndCachedRestartPreserveLibrary() throws Exception {
        AtomicBoolean offline=new AtomicBoolean();
        try(MockWebServer server=new MockWebServer()) {
            server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) { return offline.get()?new MockResponse().setResponseCode(503):new MockResponse().setBody("#EXTM3U\n#EXTINF:-1 group-title=\"Test\",Kanał lokalny\nhttp://example.com/live.ts\n#EXTINF:-1 group-title=\"Filmy\",Film lokalny\nhttp://example.com/movie.mp4\n"); }}); server.start();
            invoke(home,"sourceDialog"); click("Adres listy M3U · wpisz pilotem"); click("Połącz"); assertNotNull("Empty URL should not close form",node("Połącz"));
            input(0,server.url("/list.m3u").toString()); click("Połącz"); waitFor("M3U import missing",() -> find(home.getWindow().getDecorView(),"Kanał lokalny")!=null);
            assertEquals("m3u",new TvAccountStore(context()).load().type()); assertEquals(1,new LibraryStore(context()).loadSection("movie",new HashSet<>()).size());
            offline.set(true); invoke(home,"refresh"); waitFor("Refresh failure not shown",() -> node("OK")!=null); click("OK");
            assertEquals(1,new LibraryStore(context()).loadSection("tv",new HashSet<>()).size()); assertEquals(1,new LibraryStore(context()).loadSection("movie",new HashSet<>()).size());
            close(home); start(); key(KeyEvent.KEYCODE_DPAD_CENTER); waitFor("Saved channel missing after restart",() -> find(home.getWindow().getDecorView(),"Kanał lokalny")!=null);
        }
    }
    @Test public void xtreamValidationImportsTvAndLoadsMoviesOnlyWhenOpened() throws Exception {
        java.util.List<String> actions=Collections.synchronizedList(new ArrayList<>());
        try(MockWebServer server=new MockWebServer()) {
            server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
                String action=request.getRequestUrl().queryParameter("action"); actions.add(action==null?"auth":action); String body;
                if(action==null) body="{\"user_info\":{\"auth\":1,\"status\":\"Active\"},\"server_info\":{\"timezone\":\"Europe/Warsaw\"}}";
                else if(action.endsWith("categories")) body="[{\"category_id\":\"1\",\"category_name\":\"Test dostawcy\"}]";
                else if(action.equals("get_live_streams")) body="[{\"stream_id\":12,\"name\":\"Kanał Xtream\",\"category_id\":\"1\"}]";
                else if(action.equals("get_vod_streams")) body="[{\"stream_id\":13,\"name\":\"Film Xtream\",\"category_id\":\"1\",\"container_extension\":\"mp4\"}]";
                else body="[]";
                return new MockResponse().setHeader("Content-Type","application/json").setBody(body);
            }}); server.start();
            invoke(home,"sourceDialog"); click("Konto Xtream Codes · wpisz pilotem"); input(0,server.url("/").toString()); click("Połącz"); assertNotNull("Missing login must keep form open",node("Połącz"));
            input(1,"test"); input(2,"pass"); click("Połącz"); waitFor("Xtream import missing",() -> find(home.getWindow().getDecorView(),"Kanał Xtream")!=null);
            assertFalse(actions.contains("get_vod_streams")); assertFalse(actions.contains("get_series"));
            click("Filmy"); waitFor("Lazy movie catalog missing",() -> find(home.getWindow().getDecorView(),"Film Xtream")!=null); assertTrue(actions.contains("get_vod_streams")); assertFalse(actions.contains("get_series"));
        }
    }
}
