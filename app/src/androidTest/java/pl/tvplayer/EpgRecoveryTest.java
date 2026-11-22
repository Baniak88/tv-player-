package pl.tvplayer;

import android.app.*;
import android.content.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
@RunWith(AndroidJUnit4.class)
public class EpgRecoveryTest {
    private Instrumentation instrument() { return InstrumentationRegistry.getInstrumentation(); }
    private Context context() { return instrument().getTargetContext(); }
    private GuideRepository.Data request(Channel channel) throws Exception {
        java.util.concurrent.atomic.AtomicReference<GuideRepository.Data> result=new java.util.concurrent.atomic.AtomicReference<>(); CountDownLatch latch=new CountDownLatch(1);
        GuideRepository.get(context()).request(channel,true,data -> { result.set(data); latch.countDown(); });
        assertTrue("EPG did not finish",latch.await(10,TimeUnit.SECONDS)); return result.get();
    }
    private String json() { long now=System.currentTimeMillis()/1000; return "{\"epg_listings\":[{\"title\":\"Program testowy!\",\"start_timestamp\":"+(now-900)+",\"stop_timestamp\":"+(now+2700)+"}]}"; }
    @Test public void discoversProviderGuideAndMatchesProgrammeOnlyFeedWithoutEpgId() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            long now=System.currentTimeMillis()/1000;
            java.time.format.DateTimeFormatter format=java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z").withZone(java.time.ZoneOffset.UTC);
            String xml="<tv><programme channel=\"tvn.pl\" start=\""+format.format(java.time.Instant.ofEpochSecond(now-900))+"\" stop=\""+format.format(java.time.Instant.ofEpochSecond(now+2700))+"\"><title>Program z nagłówka</title></programme></tv>";
            java.util.concurrent.atomic.AtomicInteger headers=new java.util.concurrent.atomic.AtomicInteger(),defaults=new java.util.concurrent.atomic.AtomicInteger();
            server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
                if(request.getPath().startsWith("/prefix/get.php")) { headers.incrementAndGet(); return new MockResponse().setBody("#EXTM3U url-tvg=\"guide/current.xml\"\n#EXTINF:-1,Unused\n"); }
                if(request.getPath().startsWith("/prefix/guide/current.xml")) return new MockResponse().setBody(xml);
                if(request.getPath().startsWith("/prefix/xmltv.php")) { defaults.incrementAndGet(); return new MockResponse().setResponseCode(404); }
                return new MockResponse().setBody("{\"epg_listings\":[]}");
            }}); server.start();
            Channel channel=new Channel("PL-VIP| TVN RAW",server.url("/prefix/live/user/pass/17.ts").toString(),"TV","","tv");
            GuideRepository.Data data=request(channel); assertFalse(data.error(),data.programs().isEmpty()); assertEquals("Program z nagłówka",data.programs().get(0).title()); assertEquals("XMLTV",data.source());
            assertEquals(1,headers.get()); assertEquals("Provider's declared feed should be preferred over guessing xmltv.php",0,defaults.get());
        }
    }
    @Test public void encryptedGuideCacheReopensWithoutNetworkOrMemoryEntry() throws Exception {
        MockWebServer server=new MockWebServer(); server.enqueue(new MockResponse().setBody(json())); server.start();
        Channel channel=new Channel("TV",server.url("/live/user/pass/29.ts").toString(),"TV","","tv");
        GuideRepository.Data first;
        try { first=request(channel); assertFalse(first.programs().isEmpty()); assertEquals(1,server.getRequestCount()); }
        finally { server.shutdown(); }
        java.lang.reflect.Field field=GuideRepository.class.getDeclaredField("cache"); field.setAccessible(true); GuideRepository repository=GuideRepository.get(context());
        synchronized(repository) { ((Map<?,?>)field.get(repository)).clear(); }
        long start=SystemClock.elapsedRealtime(); GuideRepository.Data reopened=request(channel);
        assertEquals(first.programs(),reopened.programs()); assertTrue("Cached EPG took too long",SystemClock.elapsedRealtime()-start<2000);
        android.util.Log.i("LibraryBenchmark","Encrypted cached EPG ms="+(SystemClock.elapsedRealtime()-start));
    }
    private boolean contains(View root,String text) {
        if(root instanceof TextView && ((TextView)root).getText().toString().contains(text)) return true;
        if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) if(contains(((ViewGroup)root).getChildAt(i),text)) return true; return false;
    }
    @Test public void guideAutomaticallyUsesPlaybackEitWhileApiIsSlowWithoutAnotherStream() throws Exception {
        MockWebServer server=new MockWebServer(); byte[] payload=EitFixture.stream(System.currentTimeMillis()/1000-900);
        java.util.concurrent.atomic.AtomicInteger connections=new java.util.concurrent.atomic.AtomicInteger();
        server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
            if(request.getPath().startsWith("/live/")) { connections.incrementAndGet(); return new MockResponse().setHeader("Content-Type","video/mp2t").setBody(new okio.Buffer().write(payload)); }
            return new MockResponse().setBody("{\"epg_listings\":[]}").setBodyDelay(4,TimeUnit.SECONDS);
        }}); server.start();
        Channel channel=new Channel("PL-VIP| TVN RAW",server.url("/live/user/pass/101.ts").toString(),"TV","","tv");
        GuideRepository repository=GuideRepository.get(context()); repository.streamStarted(channel); Activity home=null;
        try {
            home=instrument().startActivitySync(new Intent(context(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            final Activity opened=home; instrument().runOnMainSync(() -> GuideDialog.open(opened,channel,Runnable::run,c -> {}));
            long start=SystemClock.elapsedRealtime();
            EitDataSource source=new EitDataSource(new androidx.media3.datasource.DefaultHttpDataSource.Factory().createDataSource(),channel,repository);
            try { source.open(new androidx.media3.datasource.DataSpec(android.net.Uri.parse(channel.url()))); byte[] bytes=new byte[4096]; while(source.read(bytes,0,bytes.length)!=-1) {} }
            finally { source.close(); }
            boolean shown=false; long until=start+3000;
            while(SystemClock.elapsedRealtime()<until) {
                final boolean[] found={false}; instrument().runOnMainSync(() -> { for(View window:android.view.inspector.WindowInspector.getGlobalWindowViews()) if(window.isShown() && contains(window,"Wiadomości ze strumienia")) found[0]=true; });
                if(found[0]) { shown=true; break; } Thread.sleep(80);
            }
            assertTrue("Playback EIT waited for the slow API",shown); assertEquals("EPG opened another channel connection",1,connections.get());
            instrument().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        } finally { repository.streamStopped(channel); final Activity closing=home; if(closing!=null) instrument().runOnMainSync(closing::finish); server.shutdown(); }
    }
}
