package pl.tvplayer;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import okhttp3.mockwebserver.*;

public class EpgCompatibilityTest {
    @Test public void shortEndpointFallbackWhenFullIsEmpty() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{\"epg_listings\":[]}"));
            server.enqueue(new MockResponse().setBody("{\"epg_listings\":[{\"title\":\"VGVzdA==\",\"start_timestamp\":1700000000,\"stop_timestamp\":1700003600}]}"));
            Channel channel=new Channel("Test",server.url("/u/p/7.ts").toString(),"News","","tv");
            List<Guide.Program> programs=new SourceClient().guide(channel);
            assertEquals("Test",programs.get(0).title());
            assertEquals("get_simple_data_table",server.takeRequest().getRequestUrl().queryParameter("action"));
            assertEquals("get_short_epg",server.takeRequest().getRequestUrl().queryParameter("action"));
        }
    }
    @Test public void legacyFlatStreamPreservesServerPrefix() throws Exception {
        Channel c=new Channel("TV","https://example.com/provider/user/pass/1234","News","tvn.pl","tv");
        okhttp3.HttpUrl api=Guide.endpoint(c);
        assertEquals("/provider/player_api.php",api.encodedPath()); assertEquals("1234",api.queryParameter("stream_id"));
        assertEquals("user",api.queryParameter("username")); assertEquals("pass",api.queryParameter("password"));
        assertEquals("/provider/xmltv.php",Guide.xmltv(c).encodedPath());
    }
    @Test public void acceptsNullTimestampsAndStopAlias() throws Exception {
        List<Guide.Program> list=Guide.parse(new StringReader("{\"epg_listings\":[{\"title\":\"News!\",\"start_timestamp\":null,\"start\":\"2023-11-14T22:13:20Z\",\"stop\":\"2023-11-14T23:13:20Z\"}]}"),"Europe/Warsaw");
        assertEquals(1,list.size()); assertEquals(1700000000L,list.get(0).start());
    }
    @Test public void infersMissingStopOnlyFromNextProgram() throws Exception {
        List<Guide.Program> list=Guide.parse(new StringReader("[{\"title\":\"One!\",\"start_timestamp\":1700000000},{\"title\":\"Two!\",\"start_timestamp\":1700003600,\"end_timestamp\":1700007200}]"),"UTC");
        assertEquals(2,list.size()); assertEquals(list.get(1).start(),list.get(0).end());
    }
    @Test public void m3uHeaderKeepsXmltvIdAndLogo() throws Exception {
        Channel c=M3uParser.parse("#EXTM3U x-tvg-url=\"https://example.com/epg.xml.gz\"\n#EXTINF:-1 tvg-id=\"tvn.pl\" tvg-logo=\"https://example.com/logo.png\",TVN\nhttps://example.com/live.ts",null).get(0);
        assertEquals("xmltv:https://example.com/epg.xml.gz",c.guideUrl()); assertEquals("tvn.pl",c.epgId());
        assertEquals("https://example.com/logo.png",c.logo());
        assertEquals(c.guideUrl(),c.withOrder(4).withTimezone("Europe/Warsaw").guideUrl());
    }
    @Test public void xmltvHandlesOffsetCdataAndNames() throws Exception {
        String xml="<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv><channel id=\"tvn.pl\"><display-name>TVN</display-name></channel><programme channel=\"tvn.pl\" start=\"20231114231320 +0100\" stop=\"20231115001320 +0100\"><title><![CDATA[Fakty & Pogoda]]></title><desc>Program <b>TV</b></desc></programme></tv>";
        List<String> names=new ArrayList<>(); List<Guide.Program> programs=new ArrayList<>();
        XmltvParser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),"UTC",new XmltvParser.Sink() {
            public void channel(String id,String name) { names.add(id+"="+name); }
            public void program(String id,String title,String description,long start,long end) { programs.add(new Guide.Program(title,description,start,end,"")); }
        });
        assertEquals("tvn.pl=TVN",names.get(0)); assertEquals("Fakty & Pogoda",programs.get(0).title());
        assertEquals("Program TV",programs.get(0).description()); assertEquals(1700000000L,programs.get(0).start());
    }
    @Test public void rawVariantNamesMatchStationNames() {
        assertEquals("tvn",XmltvStore.normalize("PL-VIP| TVN RAW"));
        assertEquals("tvn24bis",XmltvStore.normalize("PL-VIP| TVN24 BIS RAW"));
        assertEquals("polsatfilm",XmltvStore.normalize("Polsat Film HD"));
    }
}
