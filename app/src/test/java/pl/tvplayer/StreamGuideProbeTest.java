package pl.tvplayer;
import org.junit.Test;
import static org.junit.Assert.*;
import okhttp3.mockwebserver.*;
import java.util.*;
import java.util.concurrent.*;

public class StreamGuideProbeTest {
    @Test public void extractsEitFromSelectedStreamWithoutCallingApi() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(new okio.Buffer().write(EitFixture.stream(1793570400L))));
            Channel channel=new Channel("TVN",server.url("/live/u/p/101.ts").toString(),"TV","","tv");
            List<Guide.Program> programs=new StreamGuideProbe().read(channel);
            assertEquals("Wiadomości ze strumienia",programs.get(0).title()); assertEquals(2,programs.size());
            assertEquals("/live/u/p/101.ts",server.takeRequest().getPath()); assertEquals(1,server.getRequestCount());
        }
    }
    @Test public void cancellationClosesPendingConnection() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            StreamGuideProbe probe=new StreamGuideProbe(); ExecutorService worker=Executors.newSingleThreadExecutor();
            try {
                Future<?> future=worker.submit(() -> { try { probe.read(new Channel("TV",server.url("/stream.ts").toString(),"","","tv")); } catch(java.io.IOException expected) {} });
                assertNotNull(server.takeRequest(2,TimeUnit.SECONDS)); probe.cancel(); future.get(2,TimeUnit.SECONDS); assertTrue(probe.cancelled());
            } finally { probe.cancel(); worker.shutdownNow(); }
        }
    }
    @Test public void doesNotInventProgrammesForTsWithoutEit() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(new okio.Buffer().write(EitFixture.concat(EitFixture.padding(),EitFixture.padding()))));
            assertTrue(new StreamGuideProbe().read(new Channel("TV",server.url("/stream.ts").toString(),"","","tv")).isEmpty());
        }
    }
    @Test public void hlsPlaylistExplainsHowToInspectPlaybackSegments() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("#EXTM3U\n#EXTINF:6,\nsegment.ts\n"));
            try { new StreamGuideProbe().read(new Channel("TV",server.url("/live.m3u8").toString(),"","","tv")); fail(); }
            catch(java.io.IOException e) { assertTrue(e.getMessage().startsWith("To lista HLS")); }
        }
    }
}
