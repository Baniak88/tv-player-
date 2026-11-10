package pl.tvplayer;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PlaybackBandwidthTest {
    @Test public void watchingDefersBulkEpgButExplicitGuideAndCacheStillWork() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            long now=System.currentTimeMillis()/1000;
            java.time.format.DateTimeFormatter time=java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z").withZone(java.time.ZoneOffset.UTC);
            String xml="<tv><channel id=\"bandwidth\"><display-name>Bandwidth</display-name></channel><programme channel=\"bandwidth\" start=\""+time.format(java.time.Instant.ofEpochSecond(now-60))+"\" stop=\""+time.format(java.time.Instant.ofEpochSecond(now+3600))+"\"><title>Test programu</title></programme></tv>";
            server.enqueue(new MockResponse().setBody(xml)); server.start();
            okhttp3.HttpUrl url=server.url("/"+java.util.UUID.randomUUID()+".xml");
            Channel channel=new Channel("Bandwidth","https://example.com/live.ts","TV","bandwidth","tv");
            XmltvStore store=new XmltvStore(InstrumentationRegistry.getInstrumentation().getTargetContext());
            try {
                store.pauseBackground(true);
                assertTrue(store.load(channel,url).isEmpty()); assertEquals(0,server.getRequestCount());
                assertEquals("Test programu",store.load(channel,url,true).get(0).title()); assertEquals(1,server.getRequestCount());
                assertEquals(1,store.load(channel,url).size()); assertEquals(1,server.getRequestCount());
                store.pauseBackground(false);
                assertEquals(1,store.load(channel,url).size()); assertEquals(1,server.getRequestCount());
            } finally { store.close(); }
        }
    }
}
