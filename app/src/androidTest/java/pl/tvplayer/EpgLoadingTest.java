package pl.tvplayer;

import android.content.Context;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class EpgLoadingTest {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    @Test public void staleCurrentProgrammeIsImmediateAndRefreshesOnceInBackground() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            long now=System.currentTimeMillis()/1000;
            String json="{\"epg_listings\":[{\"title\":\"Nowy program!\",\"start_timestamp\":"+(now-600)+",\"stop_timestamp\":"+(now+3000)+"}]}";
            server.enqueue(new MockResponse().setBody(json).setBodyDelay(2,TimeUnit.SECONDS)); server.start();
            Channel channel=new Channel("Cache",server.url("/live/user/pass/7865.ts").toString(),"TV","","tv");
            String key="epg:"+channel.url()+"|"+channel.epgId()+"|"+channel.guideUrl()+"|true";
            Guide.Program saved=new Guide.Program("Zapisany program!","",now-900,now+2700,"2026-01-01 10:00:00");
            Map<String,Object> snapshot=new HashMap<>(); snapshot.put("programs",Collections.singletonList(saved)); snapshot.put("source","Xtream"); snapshot.put("saved",System.currentTimeMillis()-30*60000);
            new MetadataCache(context(),"epg-v1",200).write(key,snapshot);
            GuideRepository repository=GuideRepository.get(context()); AtomicReference<GuideRepository.Data> initial=new AtomicReference<>(); CountDownLatch first=new CountDownLatch(1);
            long started=SystemClock.elapsedRealtime(); repository.request(channel,true,data -> { initial.set(data); first.countDown(); });
            assertTrue("Valid saved EPG waited for the network",first.await(1500,TimeUnit.MILLISECONDS)); assertEquals("Zapisany program!",initial.get().programs().get(0).title());
            android.util.Log.i("LibraryBenchmark","Stale EPG immediately displayed ms="+(SystemClock.elapsedRealtime()-started));
            CountDownLatch reopened=new CountDownLatch(5); for(int i=0;i<5;i++) repository.request(channel,true,data -> reopened.countDown()); assertTrue(reopened.await(1500,TimeUnit.MILLISECONDS));
            long until=SystemClock.elapsedRealtime()+8000; boolean updated=false;
            while(SystemClock.elapsedRealtime()<until) { GuideRepository.Data data=repository.peek(channel,true); if(data!=null && data.programs().stream().anyMatch(p -> p.title().equals("Nowy program!"))) { updated=true; break; } Thread.sleep(60); }
            assertTrue("Background refresh never replaced cached EPG",updated); assertEquals("Reopening started duplicate refreshes",1,server.getRequestCount());
        }
    }
    @Test public void largeFeedWithMissingStopsImportsQuicklyAndRemainsAvailableOffline() throws Exception {
        MockWebServer server=new MockWebServer(); long now=System.currentTimeMillis()/1000;
        java.time.format.DateTimeFormatter time=java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z").withZone(java.time.ZoneOffset.UTC);
        StringBuilder xml=new StringBuilder("<tv>");
        for(int station=0;station<200;station++) xml.append("<channel id=\"station-").append(station).append("\"><display-name>Stacja ").append(station).append("</display-name></channel>");
        for(int station=0;station<200;station++) for(int slot=0;slot<64;slot++) {
            long start=now+(slot-24)*3600L; xml.append("<programme channel=\"station-").append(station).append("\" start=\"").append(time.format(java.time.Instant.ofEpochSecond(start))).append('"');
            if(slot==63) xml.append(" stop=\"").append(time.format(java.time.Instant.ofEpochSecond(start+3600))).append('"');
            xml.append("><title>Program ").append(slot).append("</title></programme>");
        } xml.append("</tv>");
        server.enqueue(new MockResponse().setBody(xml.toString())); server.start(); XmltvStore store=new XmltvStore(context());
        okhttp3.HttpUrl url=server.url("/large-missing-stops.xml"); Channel channel=new Channel("Stacja 199",server.url("/199.ts").toString(),"TV","station-199","tv","xmltv:"+url,0,"UTC");
        try {
            long started=SystemClock.elapsedRealtime(); List<Guide.Program> programs=store.load(channel,url); long elapsed=SystemClock.elapsedRealtime()-started;
            assertTrue("Large EPG import took "+elapsed+" ms",elapsed<15000); assertTrue(programs.stream().anyMatch(p -> p.current(now))); for(Guide.Program program:programs) assertTrue(program.end()>program.start()); assertEquals(1,server.getRequestCount());
            android.util.Log.i("LibraryBenchmark","XMLTV 12,800 programmes with missing stop timestamps ms="+elapsed);
        } finally { server.shutdown(); }
        long started=SystemClock.elapsedRealtime(); assertFalse(store.load(channel,url).isEmpty()); assertTrue("Cached XMLTV attempted to download again",SystemClock.elapsedRealtime()-started<1500); store.close();
    }
}
