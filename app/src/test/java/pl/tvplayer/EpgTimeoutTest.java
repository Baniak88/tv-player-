package pl.tvplayer;
import org.junit.Test;
import static org.junit.Assert.*;
import okhttp3.mockwebserver.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class EpgTimeoutTest {
    @Test public void slowFullEndpointDoesNotHoldBackWorkingShortEndpoint() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            long now=System.currentTimeMillis()/1000;
            server.enqueue(new MockResponse().setBody("{\"epg_listings\":[]}").setBodyDelay(8,TimeUnit.SECONDS));
            server.enqueue(new MockResponse().setBody("{\"epg_listings\":[{\"title\":\"Aktualny program!\",\"start_timestamp\":"+(now-600)+",\"stop_timestamp\":"+(now+3000)+"}]}"));
            Channel channel=new Channel("TV",server.url("/live/user/pass/31.ts").toString(),"TV","","tv");
            long started=System.nanoTime(); List<Guide.Program> programmes=new SourceClient().guide(channel,true); long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
            assertTrue("Fallback waited for the slow provider endpoint: "+elapsed+" ms",elapsed<6500); assertEquals("Aktualny program!",programmes.get(0).title()); assertEquals(2,server.getRequestCount());
        }
    }
}
