package pl.tvplayer;
import org.junit.Test;
import static org.junit.Assert.*;
import okhttp3.mockwebserver.*;
import java.util.*;

public class TvCatalogTest {
    @Test public void liveTvDoesNotWaitForMoviesOrSeries() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{\"user_info\":{\"auth\":1,\"status\":\"Active\"},\"server_info\":{\"timezone\":\"Europe/Warsaw\"}}"));
            server.enqueue(new MockResponse().setBody("[{\"category_id\":1,\"category_name\":\"Sport\"}]"));
            server.enqueue(new MockResponse().setBody("[{\"stream_id\":7,\"name\":\"Sport 1\",\"category_id\":1,\"epg_channel_id\":\"sport1\"}]"));
            List<Channel> channels=new SourceClient().xtreamSection(server.url("/").toString(),"u","p","tv",message -> {});
            assertEquals("Europe/Warsaw",channels.get(0).timezone()); assertEquals("Sport",channels.get(0).group()); assertEquals(3,server.getRequestCount());
            assertNull(server.takeRequest().getRequestUrl().queryParameter("action"));
            assertEquals("get_live_categories",server.takeRequest().getRequestUrl().queryParameter("action"));
            assertEquals("get_live_streams",server.takeRequest().getRequestUrl().queryParameter("action"));
        }
    }
}
