package pl.tvplayer;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SourceResilienceTest {
    private static final String PLAYLIST="#EXTM3U\n#EXTINF:-1,TV Test\nhttps://example.com/live.ts\n";
    @Test public void transientProviderFailureRetriesGetOnce() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(503));
            server.enqueue(new MockResponse().setBody(PLAYLIST));
            List<Channel> channels=new SourceClient().m3u(server.url("/list").toString());
            assertEquals("TV Test",channels.get(0).name()); assertEquals(2,server.getRequestCount());
        }
    }
    @Test public void persistentFailureStopsAfterOneRetry() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(503)); server.enqueue(new MockResponse().setResponseCode(503));
            try { new SourceClient().m3u(server.url("/list").toString()); fail("Expected HTTP error"); }
            catch(IOException expected) { assertTrue(expected.getMessage().contains("503")); }
            assertEquals(2,server.getRequestCount());
        }
    }
    @Test public void accountConnectionLimitNeverRetries() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(458));
            try { new SourceClient().m3u(server.url("/list").toString()); fail("Expected account error"); }
            catch(IOException expected) { assertTrue(expected.getMessage().contains("458")); }
            assertEquals(1,server.getRequestCount());
        }
    }
    @Test public void respectsProviderRetryAfter() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After","60"));
            try { new SourceClient().m3u(server.url("/list").toString()); fail("Expected rate limit"); }
            catch(IOException expected) { assertTrue(expected.getMessage().contains("429")); }
            assertEquals(1,server.getRequestCount());
        }
    }
    @Test public void truncatedDownloadIsReplacedWithCompletePlaylist() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(PLAYLIST.repeat(20)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));
            server.enqueue(new MockResponse().setBody(PLAYLIST));
            List<Channel> channels=new SourceClient().m3u(server.url("/list").toString());
            assertEquals(1,channels.size()); assertEquals(2,server.getRequestCount());
        }
    }
    @Test public void userAgentFallbackSharesGuideRequestBudget() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            long now=System.currentTimeMillis()/1000;
            server.enqueue(new MockResponse().setResponseCode(403).setHeadersDelay(2500,TimeUnit.MILLISECONDS));
            server.enqueue(new MockResponse().setBody("{}").setBodyDelay(3,TimeUnit.SECONDS));
            server.enqueue(new MockResponse().setBody("{\"epg_listings\":[{\"title\":\"Program\",\"start_timestamp\":"+(now-60)+",\"stop_timestamp\":"+(now+3600)+"}]}"));
            Channel channel=new Channel("TV",server.url("/live/u/p/1.ts").toString(),"TV","","tv");
            long start=System.nanoTime();
            assertEquals(1,new SourceClient().guide(channel,true).size());
            long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
            assertTrue("User-agent fallback reset timeout: "+elapsed,elapsed<5300);
            assertEquals(3,server.getRequestCount());
        }
    }
}
