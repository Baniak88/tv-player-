package pl.tvplayer;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SourceResilienceTest {
    @Test public void completesMissingLiveBouquetFromPanelWithoutOpeningStreams() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("[{\"category_id\":\"1\",\"category_name\":\"Sport\"},{\"category_id\":\"2\",\"category_name\":\"UK | Movies\"}]"));
            server.enqueue(new MockResponse().setBody("[{\"stream_id\":1,\"name\":\"Sport\",\"category_id\":\"1\"},{\"stream_id\":2,\"name\":\"Sky Cinema\",\"category_id\":\"2\"}]"));
            List<Channel> current=List.of(new Channel("Sport",server.url("/live/u/p/1.ts").toString(),"Sport","","tv"));
            List<Channel> result=new SourceClient().missingM3uLiveChannels(server.url("/get.php?username=u&password=p&type=m3u_plus").toString(),current);
            assertEquals(1,result.size()); assertEquals("UK | Movies",result.get(0).group()); assertEquals("tv",result.get(0).kind());
            assertTrue(server.takeRequest().getPath().contains("action=get_live_categories")); assertTrue(server.takeRequest().getPath().contains("action=get_live_streams")); assertEquals(2,server.getRequestCount());
        }
    }
    @Test public void recoveredBouquetsFollowTheProviderCategoryOrder() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("[{\"category_id\":\"1\",\"category_name\":\"Sport\"},{\"category_id\":\"2\",\"category_name\":\"UK | Movies\"},{\"category_id\":\"3\",\"category_name\":\"Dzieci\"}]"));
            server.enqueue(new MockResponse().setBody("[{\"stream_id\":2,\"name\":\"Sky Cinema\",\"category_id\":\"2\"}]"));
            List<Channel> current=List.of(new Channel("Sport 1",server.url("/live/u/p/1.ts").toString(),"Sport","","tv"),new Channel("Bajki",server.url("/live/u/p/3.ts").toString(),"Dzieci","","tv"));
            List<Channel> result=new SourceClient().liveInProviderOrder(server.url("/get.php?username=u&password=p&type=m3u_plus").toString(),current);
            assertEquals(3,result.size());
            assertEquals(List.of("Sport","UK | Movies","Dzieci"),new java.util.ArrayList<>(BouquetOrder.counts(result,"provider").keySet()));
        }
    }
    @Test public void completeM3uSkipsTheLargeLiveStreamsDownload() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("[{\"category_id\":\"1\",\"category_name\":\"UK | Movies\"}]"));
            List<Channel> current=List.of(new Channel("Sky",server.url("/1.ts").toString(),"UK Movies","","tv"));
            assertTrue(new SourceClient().missingM3uLiveChannels(server.url("/get.php?username=u&password=p").toString(),current).isEmpty()); assertEquals(1,server.getRequestCount());
        }
    }
    @Test public void plainM3uDoesNotGuessOrProbePanelCredentials() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            assertTrue(new SourceClient().missingM3uLiveChannels(server.url("/playlist.m3u").toString(),List.of()).isEmpty()); assertEquals(0,server.getRequestCount());
        }
    }
    @Test public void unavailableOptionalPanelDoesNotInventChannels() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404));
            try { new SourceClient().missingM3uLiveChannels(server.url("/get.php?username=u&password=p").toString(),List.of()); fail("Expected unavailable panel"); } catch(IOException expected) { assertTrue(expected.getMessage().contains("404")); }
            assertEquals(1,server.getRequestCount());
        }
    }
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
