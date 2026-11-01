package pl.tvplayer;
import org.junit.Test;
import java.io.*;
import java.util.*;
import okhttp3.HttpUrl;
import static org.junit.Assert.*;
public class GuideTest {
    private Channel channel(int days) {
        return new Channel("TV","https://example.com/prefix/live/u/p/7.ts","News","","tv","",days,"Europe/Warsaw");
    }
    @Test public void derivesEndpointWithServerPrefix() throws Exception {
        HttpUrl url=Guide.endpoint(channel(7));
        assertEquals("/prefix/player_api.php",url.encodedPath()); assertEquals("7",url.queryParameter("stream_id"));
        assertEquals("u",url.queryParameter("username"));
    }
    @Test public void parsesBase64AndSkipsBrokenIntervals() throws Exception {
        List<Guide.Program> programs=Guide.parse(new StringReader("{\"epg_listings\":[{\"title\":\"V2lhZG9tb8WbY2k=\",\"description\":\"T3Bpcw==\",\"start_timestamp\":1700000000,\"stop_timestamp\":1700003600},{\"title\":\"Bad\",\"start_timestamp\":0}]}"),"UTC");
        assertEquals(1,programs.size()); assertEquals("Wiadomości",programs.get(0).title()); assertEquals("Opis",programs.get(0).description());
    }
    @Test public void archiveRespectsRetentionAndFuture() {
        Guide.Program p=new Guide.Program("Test","",1700000000,1700003600,"2023-11-14 23:13:20");
        assertTrue(p.replayable(channel(7),1700001800)); assertFalse(p.replayable(channel(0),1700001800));
        assertFalse(p.replayable(channel(7),1699999999)); assertFalse(p.replayable(channel(7),1700000000+8*86400));
    }
    @Test public void archiveUsesProviderClockAndClipsToNow() throws Exception {
        Guide.Program p=new Guide.Program("Test","",1700000000,1700003600,"2023-11-14 23:13:20");
        String url=Guide.replay(channel(7),p,1700001800);
        assertEquals("https://example.com/prefix/timeshift/u/p/30/2023-11-14:23-13/7.m3u8",url);
    }
    @Test public void timestampFallbackUsesServerTimezone() throws Exception {
        List<Guide.Program> programs=Guide.parse(new StringReader("{\"epg_listings\":[{\"title\":\"News!\",\"start\":\"2023-11-14 23:13:20\",\"end\":\"2023-11-15 00:13:20\"}]}"),"Europe/Warsaw");
        assertEquals(1700000000L,programs.get(0).start()); assertEquals("News!",programs.get(0).title());
    }
    @Test public void providerArchiveFlagsSurviveImport() throws Exception {
        List<Channel> channels=SourceClient.readCatalog(new StringReader("[{\"stream_id\":7,\"tv_archive\":1,\"tv_archive_duration\":7}]"),HttpUrl.get("https://example.com/"),"u","p",Collections.emptyMap(),"tv");
        assertEquals(7,channels.get(0).archiveDays()); assertEquals("get_simple_data_table",HttpUrl.get(channels.get(0).guideUrl()).queryParameter("action"));
    }
}
