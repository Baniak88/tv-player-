package pl.tvplayer;

import android.content.Context;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
@RunWith(AndroidJUnit4.class)
public class StreamResumptionTest {
    @Test public void media3ResumesAfterProviderRecoversWithoutAnotherPlayer() throws Exception {
        android.app.Instrumentation instrument=InstrumentationRegistry.getInstrumentation();
        Context context=instrument.getTargetContext(); byte[] stream;
        try(InputStream input=instrument.getContext().getAssets().open("live-controls.ts");ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; for(int n;(n=input.read(buffer))!=-1;) output.write(buffer,0,n); stream=output.toByteArray();
        }
        AtomicInteger requests=new AtomicInteger(); AtomicBoolean recovering=new AtomicBoolean(); CountDownLatch ready=new CountDownLatch(1);
        try(MockWebServer server=new MockWebServer()) {
            server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
                requests.incrementAndGet();
                if(!recovering.get()) return new MockResponse().setResponseCode(503);
                return new MockResponse().setHeader("Content-Type","video/mp2t").setBody(new okio.Buffer().write(stream));
            }}); server.start();
            ExoPlayer[] player=new ExoPlayer[1]; PlaybackRecovery[] recovery=new PlaybackRecovery[1];
            instrument.runOnMainSync(() -> {
                player[0]=new ExoPlayer.Builder(context).setMediaSourceFactory(PlaybackNetwork.sources(context,null)).build();
                player[0].addListener(new Player.Listener() { @Override public void onPlaybackStateChanged(int state) { if(state==Player.STATE_READY) ready.countDown(); } });
                recovery[0]=new PlaybackRecovery(player[0],true,text -> { if(text.contains("ponawianie")) recovering.set(true); });
                player[0].setMediaItem(new MediaItem.Builder().setUri(server.url("/live.ts").toString()).setMimeType(MimeTypes.VIDEO_MP2T).build());
                player[0].prepare(); player[0].play();
            });
            try {
                assertTrue("Player did not resume after HTTP 503",ready.await(60,TimeUnit.SECONDS));
                assertTrue("Recovery was not exercised",recovering.get()); assertTrue(requests.get()>=2);
            } finally { instrument.runOnMainSync(() -> { recovery[0].close(); player[0].release(); }); }
        }
    }
}
