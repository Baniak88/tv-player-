package pl.tvplayer;

import android.app.*;
import android.content.*;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import okhttp3.mockwebserver.*;
import okhttp3.tls.*;
import org.junit.*;
import org.junit.runner.RunWith;
import javax.net.ssl.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
import static pl.tvplayer.TvTestUi.*;

/** Real HTTP/TLS and media packets through the Android-only production transport. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
@RunWith(AndroidJUnit4.class)
public class AndroidMovieHttpTest {
    private MockWebServer server,secure;
    private PlayerActivity player;
    private SSLSocketFactory previousTls;
    private byte[] movie;
    @Before public void setup() throws Exception {
        previousTls=HttpsURLConnection.getDefaultSSLSocketFactory(); server=new MockWebServer();
        try(InputStream in=instrument().getContext().getAssets().open("live-controls.ts"); ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] bytes=new byte[8192]; for(int n;(n=in.read(bytes))!=-1;) out.write(bytes,0,n); movie=out.toByteArray(); }
        context().getSharedPreferences("watch-progress",0).edit().clear().commit();
    }
    private MockResponse stream(RecordedRequest request) {
        String range=request.getHeader("Range"); int offset=range==null?0:Integer.parseInt(range.substring(6).split("-")[0]); if(offset>=movie.length) return new MockResponse().setResponseCode(416);
        MockResponse response=new MockResponse().setHeader("Content-Type","video/mp2t").setHeader("Accept-Ranges","bytes").setBody(new okio.Buffer().write(movie,offset,movie.length-offset));
        return range==null?response:response.setResponseCode(206).setHeader("Content-Range","bytes "+offset+"-"+(movie.length-1)+"/"+movie.length);
    }
    private void start(String url) throws Exception {
        player=(PlayerActivity)instrument().startActivitySync(new Intent(context(),PlayerActivity.class).putExtra("url",url).putExtra("kind","movie").putExtra("name","Test HTTP filmu").putExtra("television",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
    private void playing() throws Exception {
        waitFor("Movie did not decode after HTTP compatibility handling",() -> {
            boolean[] ready={false}; instrument().runOnMainSync(() -> { try { ExoPlayer engine=(ExoPlayer)field(player,"player"); ready[0]=engine!=null && engine.isPlaying() && engine.getAudioFormat()!=null && engine.getAudioDecoderCounters()!=null && engine.getAudioDecoderCounters().renderedOutputBufferCount>2; } catch(Exception e) { throw new AssertionError(e); } }); return ready[0];
        });
    }
    @Test public void moviePlaysAcrossHttpsToHttpRedirectAndKeepsTheTicket() throws Exception {
        AtomicReference<String> ticket=new AtomicReference<>(); server.setDispatcher(new Dispatcher() { public MockResponse dispatch(RecordedRequest request) { ticket.set(request.getRequestUrl().queryParameter("ticket")); return stream(request); } }); server.start();
        HeldCertificate certificate=new HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build();
        HandshakeCertificates host=new HandshakeCertificates.Builder().heldCertificate(certificate).build(),client=new HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate()).build();
        secure=new MockWebServer(); secure.useHttps(host.sslSocketFactory(),false); secure.setDispatcher(new Dispatcher() { public MockResponse dispatch(RecordedRequest request) { return new MockResponse().setResponseCode(302).setHeader("Location",server.url("/movie.mp4?ticket=one%2Btwo")); } }); secure.start();
        HttpsURLConnection.setDefaultSSLSocketFactory(client.sslSocketFactory()); start(secure.url("/redirect.mp4").toString()); playing(); assertEquals("one+two",ticket.get()); assertTrue(secure.getRequestCount()>0);
    }
    @Test public void movieUsesOnePlatformAgentFallbackWhenThePanelRejectsVlc() throws Exception {
        AtomicInteger rejected=new AtomicInteger(),accepted=new AtomicInteger(); AtomicReference<String> ticket=new AtomicReference<>();
        server.setDispatcher(new Dispatcher() { public MockResponse dispatch(RecordedRequest request) {
            if(SourceClient.PLAYER_AGENT.equals(request.getHeader("User-Agent"))) { rejected.incrementAndGet(); return new MockResponse().setResponseCode(406); }
            accepted.incrementAndGet(); ticket.set(request.getRequestUrl().queryParameter("ticket")); return stream(request);
        }}); server.start(); start(server.url("/movie.mp4?ticket=unchanged").toString()); playing(); assertEquals(1,rejected.get()); assertTrue(accepted.get()>0); assertEquals("unchanged",ticket.get());
    }
    @Test public void rejectedSmallRangeFallsBackToAnExactBoundedRead() throws Exception {
        byte[] data=new byte[8192]; for(int i=0;i<data.length;i++) data[i]=(byte)(i%251);
        server.setDispatcher(new Dispatcher() { public MockResponse dispatch(RecordedRequest request) { return request.getHeader("Range")!=null?new MockResponse().setResponseCode(416):new MockResponse().setBody(new okio.Buffer().write(data)); } }); server.start();
        AndroidHttpSource source=new AndroidHttpSource();
        try {
            assertEquals(256,source.open(new DataSpec.Builder().setUri(server.url("/movie.mp4?ticket=range").toString()).setPosition(2048).setLength(256).build()));
            byte[] result=new byte[256]; int offset=0; while(offset<result.length) { int count=source.read(result,offset,result.length-offset); assertTrue(count>0); offset+=count; }
            assertArrayEquals(Arrays.copyOfRange(data,2048,2304),result); assertEquals(C.RESULT_END_OF_INPUT,source.read(new byte[1],0,1));
        } finally { source.close(); }
        assertEquals(2,server.getRequestCount()); assertEquals("bytes=2048-2303",server.takeRequest().getHeader("Range")); RecordedRequest plain=server.takeRequest(); assertNull(plain.getHeader("Range")); assertEquals("range",plain.getRequestUrl().queryParameter("ticket"));
    }
    @Test public void authenticationIsNotRetriedByCompatibilityAndTheUiShowsTheHttpCode() throws Exception {
        server.setDispatcher(new Dispatcher() { public MockResponse dispatch(RecordedRequest request) { return new MockResponse().setResponseCode(401); } }); server.start(); String url=server.url("/movie.mp4?password=private-test-marker").toString();
        AndroidHttpSource source=new AndroidHttpSource();
        try { source.open(new DataSpec(Uri.parse(url))); fail("401 must be reported"); } catch(HttpDataSource.InvalidResponseCodeException denied) { assertEquals(401,denied.responseCode); } finally { source.close(); }
        assertEquals("A compatibility fallback must not retry a password error",1,server.getRequestCount()); start(url);
        waitFor("The error panel omitted the real HTTP status",() -> { try { return ((android.widget.TextView)field(player,"message")).getText().toString().contains("HTTP 401"); } catch(Exception e) { return false; } });
        assertFalse(((android.widget.TextView)field(player,"message")).getText().toString().contains("private-test-marker"));
    }
    @Test public void aLargeRejectedRangeDoesNotDownloadTheMovieFromTheBeginning() throws Exception {
        server.setDispatcher(new Dispatcher() { public MockResponse dispatch(RecordedRequest request) { return new MockResponse().setResponseCode(416); } }); server.start(); AndroidHttpSource source=new AndroidHttpSource();
        try { source.open(new DataSpec.Builder().setUri(server.url("/movie.mp4").toString()).setPosition(4L*1024*1024).build()); fail("An expensive range fallback must not be attempted"); } catch(HttpDataSource.InvalidResponseCodeException denied) { assertEquals(416,denied.responseCode); } finally { source.close(); }
        assertEquals(1,server.getRequestCount());
    }
    @Test public void hlsMovieRecoversWhenProviderTemporarilyRefusesSegment() throws Exception {
        String playlist = "#EXTM3U\n#EXT-X-TARGETDURATION:60\n#EXTINF:60,\nsegment.ts?ticket=abc\n#EXT-X-ENDLIST\n";
        AtomicInteger segments = new AtomicInteger();
        AtomicLong refusedAt = new AtomicLong(), retriedAt = new AtomicLong();
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                if ("/movie.m3u8".equals(request.getPath())) {
                    return new MockResponse().setHeader("Content-Type", "application/vnd.apple.mpegurl").setBody(playlist);
                }
                if (!"/segment.ts?ticket=abc".equals(request.getPath())) return new MockResponse().setResponseCode(404);
                if (segments.incrementAndGet() == 1) {
                    refusedAt.set(android.os.SystemClock.elapsedRealtime());
                    return new MockResponse().setResponseCode(458).setHeader("Retry-After", "6");
                }
                retriedAt.compareAndSet(0, android.os.SystemClock.elapsedRealtime());
                return stream(request);
            }
        });
        server.start();
        start(server.url("/movie.m3u8").toString());
        playing();
        assertTrue("Retried before Retry-After expired", retriedAt.get() - refusedAt.get() >= 6000);
    }

    @Test public void persistent458StopsAfterOneDelayedRetryAndReportsStatus() throws Exception {
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(458);
            }
        });
        server.start();
        start(server.url("/movie.mp4?ticket=private-test-marker").toString());
        waitFor("No terminal HTTP 458 message", () -> {
            try {
                return ((android.widget.TextView)field(player, "message")).getText().toString().contains("HTTP 458");
            } catch(Exception e) { return false; }
        });
        assertEquals(2, server.getRequestCount());
        assertFalse(((android.widget.TextView)field(player, "message")).getText().toString().contains("private-test-marker"));
    }

    @Test public void expiredVodSegmentReopensPlaylistAtPlaybackPosition() throws Exception {
        byte[] shortSegment;
        try (InputStream input = instrument().getContext().getAssets().open("vod-session.ts");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            for (int n; (n = input.read(buffer)) != -1;) output.write(buffer, 0, n);
            shortSegment = output.toByteArray();
        }
        AtomicInteger playlists = new AtomicInteger(), refusals = new AtomicInteger();
        java.util.concurrent.CountDownLatch watching = new java.util.concurrent.CountDownLatch(1);
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                String path = request.getPath();
                if ("/vod.m3u8".equals(path)) {
                    int generation = playlists.incrementAndGet();
                    String playlist = "#EXTM3U\n#EXT-X-TARGETDURATION:60\n"
                            + "#EXTINF:60,\nearly.ts\n#EXT-X-DISCONTINUITY\n#EXTINF:60,\nearly.ts\n"
                            + "#EXT-X-DISCONTINUITY\n#EXTINF:60,\nearly.ts\n#EXT-X-DISCONTINUITY\n"
                            + "#EXTINF:6,\nshort.ts\n#EXT-X-DISCONTINUITY\n#EXTINF:60,\nnext.ts?session="
                            + generation + "\n#EXT-X-ENDLIST\n";
                    return new MockResponse().setHeader("Content-Type", "application/vnd.apple.mpegurl").setBody(playlist);
                }
                if ("/next.ts?session=1".equals(path)) {
                    if (!watching.await(20, java.util.concurrent.TimeUnit.SECONDS)) return new MockResponse().setResponseCode(503);
                    refusals.incrementAndGet();
                    return new MockResponse().setResponseCode(458);
                }
                return new MockResponse().setHeader("Content-Type", "video/mp2t")
                        .setBody(new okio.Buffer().write("/short.ts".equals(path) ? shortSegment : movie));
            }
        });
        server.start();
        String address = server.url("/vod.m3u8").toString();
        WatchProgress.save(context(), address, 181000, 246000, false);
        start(address);
        ExoPlayer engine = (ExoPlayer)field(player, "player");
        AtomicLong failedPosition = new AtomicLong();
        try {
            playing();
            instrument().runOnMainSync(() -> engine.addListener(new androidx.media3.common.Player.Listener() {
                @Override public void onPlayerError(androidx.media3.common.PlaybackException failure) {
                    if (AndroidPlaybackErrors.status(failure) == 458) failedPosition.set(engine.getContentPosition());
                }
            }));
        } finally { watching.countDown(); }
        waitFor("The original VOD playlist was not reopened", () -> playlists.get() == 2);
        assertEquals(2, refusals.get());
        assertTrue("Movie was not already playing in minute four", failedPosition.get() >= 181000);
        waitFor("Playback did not advance beyond the previously refused segment", () -> {
            boolean[] resumed = {false};
            instrument().runOnMainSync(() -> resumed[0] = engine.isPlaying()
                    && engine.getContentPosition() > Math.max(186000, failedPosition.get()));
            return resumed[0];
        });
        assertEquals(2, playlists.get());
    }

    @Test public void retryAfterDatesAndLongDelaysAreRespected() {
        long now = java.time.Instant.parse("2026-11-20T12:00:00Z").toEpochMilli();
        assertEquals(19000, AndroidLoadErrorPolicy.retryDelay("Fri, 20 Nov 2026 12:00:19 GMT", now));
        assertEquals(C.TIME_UNSET, AndroidLoadErrorPolicy.retryDelay("Fri, 20 Nov 2026 12:03:00 GMT", now));
        assertEquals(C.TIME_UNSET, AndroidLoadErrorPolicy.retryDelay("120", now));
        assertEquals(C.TIME_UNSET, AndroidLoadErrorPolicy.retryDelay("invalid", now));
        assertEquals(5000, AndroidLoadErrorPolicy.retryDelay("0", now));
    }
    @Test public void m3uSeriesAcceptsNumberedHlsEpisodesAndPreservesLiveChannels() throws Exception {
        String playlist = "#EXTM3U\n#EXTINF:-1 group-title=\"PL SERIALE\",Test S01.E02\n/v/one.m3u8\n"
                + "#EXTINF:-1 group-title=\"PL SERIALE\",Test S01E02\n/live/two.ts\n";
        server.enqueue(new MockResponse().setBody(playlist));
        server.start();
        List<Channel> items = AndroidM3uLibrary.load(new SourceClient(),server.url("/list").toString(), text -> {});
        assertEquals("episode",items.get(0).kind());
        assertEquals("tv",items.get(1).kind());
    }

    @Test public void emptySeriesInPanelPlaylistLoadsProvidersSeriesCatalogue() throws Exception {
        server.enqueue(new MockResponse().setBody("#EXTM3U\n#EXTINF:-1,TV\n/live/test.ts\n"));
        server.enqueue(new MockResponse().setBody("{\"user_info\":{\"auth\":1,\"status\":\"Active\"}}"));
        server.enqueue(new MockResponse().setBody("[{\"category_id\":\"3\",\"category_name\":\"Seriale\"}]"));
        server.enqueue(new MockResponse().setBody("[{\"series_id\":7,\"name\":\"Serial panelu\",\"category_id\":\"3\"}]"));
        server.start();
        List<Channel> items = AndroidM3uLibrary.load(new SourceClient(),server.url("/get.php?username=test&password=test").toString(), text -> {});
        assertEquals(2,items.size());
        assertEquals("series",items.get(1).section());
        assertEquals("Serial panelu",items.get(1).name());
    }
    @After public void cleanup() throws Exception { close(player); HttpsURLConnection.setDefaultSSLSocketFactory(previousTls); if(secure!=null) secure.shutdown(); server.shutdown(); }
}
