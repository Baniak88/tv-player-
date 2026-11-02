package pl.tvplayer;

import org.junit.Test;
import static org.junit.Assert.*;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import java.io.*;
import java.util.*;

public class EpgRecoveryTest {
    private String program(String title,long start) { return "{\"epg_listings\":[{\"title\":\""+title+"\",\"start_timestamp\":"+start+",\"stop_timestamp\":"+(start+3600)+"}]}"; }
    @Test public void staleFullGuideStillTriesCurrentShortGuideWithoutFullLimit() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            long now=System.currentTimeMillis()/1000;
            server.enqueue(new MockResponse().setBody(program("Old!",now-86400)));
            server.enqueue(new MockResponse().setBody(program("Now!",now-900)));
            Channel channel=new Channel("TV",server.url("/prefix/live/user/pass/17.ts").toString(),"TV","","tv");
            List<Guide.Program> data=new SourceClient().guide(channel,true);
            assertTrue(data.stream().anyMatch(p -> p.current(now) && p.title().equals("Now!")));
            RecordedRequest full=server.takeRequest(),shortGuide=server.takeRequest();
            assertEquals("get_simple_data_table",full.getRequestUrl().queryParameter("action"));
            assertNull("Simple table must not be truncated to the oldest 100 rows",full.getRequestUrl().queryParameter("limit"));
            assertEquals("get_short_epg",shortGuide.getRequestUrl().queryParameter("action")); assertEquals("17",shortGuide.getRequestUrl().queryParameter("stream_id"));
        }
    }
    @Test public void staleShortGuideStillTriesFullGuide() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            long now=System.currentTimeMillis()/1000;
            server.enqueue(new MockResponse().setBody(program("Old!",now-86400)));
            server.enqueue(new MockResponse().setBody(program("Now!",now-900)));
            List<Guide.Program> data=new SourceClient().guide(new Channel("TV",server.url("/live/user/pass/17.ts").toString(),"TV","","tv"),false);
            assertTrue(data.stream().anyMatch(p -> p.current(now))); assertEquals(2,server.getRequestCount());
        }
    }
    @Test public void discoversProviderHeaderOnceAcrossChannelsAndNeverReadsCatalogue() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("#EXTM3U x-tvg-url='guides/epg.xml.gz?token=x&amp;lang=pl'\n#EXTINF:-1,Unused\n"));
            EpgDiscovery discovery=new EpgDiscovery();
            Channel one=new Channel("One",server.url("/prefix/live/user/pass/17.ts").toString(),"TV","","tv");
            Channel two=new Channel("Two",server.url("/prefix/live/user/pass/18.ts").toString(),"TV","","tv");
            List<HttpUrl> urls=discovery.discover(one); assertEquals(urls,discovery.discover(two)); assertEquals(1,server.getRequestCount());
            assertEquals(server.url("/prefix/guides/epg.xml.gz?token=x&lang=pl"),urls.get(0));
            RecordedRequest request=server.takeRequest(); assertEquals("/prefix/get.php",request.getRequestUrl().encodedPath()); assertEquals("m3u_plus",request.getRequestUrl().queryParameter("type"));
            Reader bounded=new StringReader("#EXTM3U url-tvg=\"/guide.xml\"\n") { @Override public int read() throws IOException { int c=super.read(); if(c==-1) throw new AssertionError("Read beyond header"); return c; } };
            assertEquals(1,EpgDiscovery.header(bounded,server.url("/get.php")).size());
        }
    }
    @Test public void headerIsBoundedAndRejectsEmbeddedUserInfoAndNonPlaylistResponses() throws Exception {
        HttpUrl base=HttpUrl.get("https://provider.example/get.php");
        assertTrue(EpgDiscovery.header(new StringReader("<html>url-tvg=\"https://other.example/epg.xml\"</html>"),base).isEmpty());
        List<HttpUrl> values=EpgDiscovery.header(new StringReader("\uFEFF#EXTM3U URL-TVG=\"https://user:pass@other.example/x, /a.xml /a.xml /b.xml /c.xml /d.xml\"\n"),base);
        assertEquals(3,values.size()); assertEquals("/a.xml",values.get(0).encodedPath());
        assertTrue(EpgDiscovery.header(new StringReader("#EXTM3U "+"x".repeat(65536)+" url-tvg=/hidden.xml"),base).isEmpty());
    }
}
