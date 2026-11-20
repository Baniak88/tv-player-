package pl.tvplayer;

import java.util.List;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class M3uSeriesTest {
    @Test public void panelSeriesAreAddedWhenThePlaylistHasNone() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
                String action=request.getRequestUrl().queryParameter("action");
                if(request.getPath().startsWith("/get.php")) return new MockResponse().setBody("#EXTM3U\n#EXTINF:-1 group-title=\"Sport\",Sport 1\n"+server.url("/live/u/p/1.ts")+"\n#EXTINF:-1 group-title=\"Kino\",Film\n"+server.url("/movie/u/p/2.mp4")+"\n");
                if(request.getPath().startsWith("/player_api.php") && action==null) return new MockResponse().setBody("{\"user_info\":{\"auth\":1,\"status\":\"Active\"},\"server_info\":{\"timezone\":\"Europe/Warsaw\"}}");
                if("get_series_categories".equals(action)) return new MockResponse().setBody("[{\"category_id\":\"7\",\"category_name\":\"|MULTI| NETFLIX SERIALE\"}]");
                if("get_series".equals(action)) return new MockResponse().setBody("[{\"series_id\":42,\"name\":\"Wiedźmin\",\"category_id\":\"7\",\"cover\":\"\"}]");
                return new MockResponse().setResponseCode(404);
            }}); server.start();
            List<Channel> items=M3uSeries.load(new SourceClient(),server.url("/get.php?username=u&password=p&type=m3u_plus").toString(),value -> {});
            assertTrue(items.stream().anyMatch(c -> c.section().equals("tv")));
            assertTrue(items.stream().anyMatch(c -> c.section().equals("movie")));
            assertTrue("Panel series missing: "+items,items.stream().anyMatch(c -> c.section().equals("series") && c.name().contains("Wiedźmin") && c.group().equals("|MULTI| NETFLIX SERIALE")));
        }
    }
    @Test public void plainPlaylistsNeverProbeAPanel() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("#EXTINF:-1 group-title=\"Sport\",Sport 1\n"+server.url("/live/u/p/1.ts")+"\n"));
            List<Channel> items=M3uSeries.load(new SourceClient(),server.url("/playlist.m3u").toString(),value -> {});
            assertEquals(1,items.size()); assertEquals(1,server.getRequestCount());
        }
    }
}
