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

    @Test public void retryAfterDatesAndLongDelaysAreRespected() {
        long now = java.time.Instant.parse("2026-11-20T12:00:00Z").toEpochMilli();
        assertEquals(19000, AndroidLoadErrorPolicy.retryDelay("Fri, 20 Nov 2026 12:00:19 GMT", now));
        assertEquals(C.TIME_UNSET, AndroidLoadErrorPolicy.retryDelay("Fri, 20 Nov 2026 12:03:00 GMT", now));
        assertEquals(C.TIME_UNSET, AndroidLoadErrorPolicy.retryDelay("120", now));
        assertEquals(C.TIME_UNSET, AndroidLoadErrorPolicy.retryDelay("invalid", now));
        assertEquals(5000, AndroidLoadErrorPolicy.retryDelay("0", now));
    }
    @After public void cleanup() throws Exception { close(player); HttpsURLConnection.setDefaultSSLSocketFactory(previousTls); if(secure!=null) secure.shutdown(); server.shutdown(); }
}
