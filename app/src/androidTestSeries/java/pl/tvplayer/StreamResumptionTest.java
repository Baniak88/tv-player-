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
        resume(false);
    }
    @Test public void media3ResumesAfterTransportDisconnect() throws Exception {
        resume(true);
    }
    @Test public void playingStreamRecoversAfterConnectionDropsMidBody() throws Exception {
        android.app.Instrumentation instrument=InstrumentationRegistry.getInstrumentation(); Context context=instrument.getTargetContext(); byte[] stream;
        try(InputStream input=instrument.getContext().getAssets().open("short-live.ts");ByteArrayOutputStream output=new ByteArrayOutputStream()) { byte[] buffer=new byte[4096]; for(int n;(n=input.read(buffer))!=-1;) output.write(buffer,0,n); stream=output.toByteArray(); }
        AtomicInteger requests=new AtomicInteger(); AtomicBoolean recovering=new AtomicBoolean(); CountDownLatch firstReady=new CountDownLatch(1),resumed=new CountDownLatch(1);
        try(MockWebServer server=new MockWebServer()) {
            server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
                if(requests.getAndIncrement()==0) return new MockResponse().setHeader("Content-Type","video/mp2t").setChunkedBody(new okio.Buffer().write(stream),188)
                    .throttleBody(1880,100,TimeUnit.MILLISECONDS).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY);
                if(!recovering.get()) return new MockResponse().setResponseCode(503);
                return new MockResponse().setHeader("Content-Type","video/mp2t").setBody(new okio.Buffer().write(stream));
            }}); server.start(); String address=server.url("/interrupted.ts").toString(); ExoPlayer[] player={null}; PlaybackRecovery[] recovery={null};
            instrument.runOnMainSync(() -> {
                player[0]=new ExoPlayer.Builder(context).setMediaSourceFactory(PlaybackNetwork.sources(context,null)).build();
                player[0].addListener(new Player.Listener() { @Override public void onPlaybackStateChanged(int state) { if(state==Player.STATE_READY) { if(recovering.get()) resumed.countDown(); else firstReady.countDown(); } } });
                recovery[0]=new PlaybackRecovery(player[0],true,text -> { if(text.contains("ponawianie")) recovering.set(true); });
                player[0].setMediaItem(new MediaItem.Builder().setUri(address).setMimeType(MimeTypes.VIDEO_MP2T).build()); player[0].prepare(); player[0].play();
            });
            try { assertTrue("Stream never began before transport failure",firstReady.await(30,TimeUnit.SECONDS)); assertTrue("Playback did not recover after a mid-stream disconnect",resumed.await(60,TimeUnit.SECONDS)); assertTrue(recovering.get()); assertTrue(requests.get()>=3); }
            finally { instrument.runOnMainSync(() -> { recovery[0].close(); player[0].release(); }); }
        }
    }
    private void resume(boolean disconnect) throws Exception {
        android.app.Instrumentation instrument=InstrumentationRegistry.getInstrumentation();
        Context context=instrument.getTargetContext(); byte[] stream;
        try(InputStream input=instrument.getContext().getAssets().open("live-controls.ts");ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; for(int n;(n=input.read(buffer))!=-1;) output.write(buffer,0,n); stream=output.toByteArray();
        }
        AtomicInteger requests=new AtomicInteger(); AtomicBoolean recovering=new AtomicBoolean(); CountDownLatch ready=new CountDownLatch(1);
        try(MockWebServer server=new MockWebServer()) {
            server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
                requests.incrementAndGet();
                if(!recovering.get()) return disconnect?new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START):new MockResponse().setResponseCode(503);
                return new MockResponse().setHeader("Content-Type","video/mp2t").setBody(new okio.Buffer().write(stream));
            }}); server.start();
            String address=server.url("/live.ts").toString();
            ExoPlayer[] player=new ExoPlayer[1]; PlaybackRecovery[] recovery=new PlaybackRecovery[1];
            instrument.runOnMainSync(() -> {
                player[0]=new ExoPlayer.Builder(context).setMediaSourceFactory(PlaybackNetwork.sources(context,null)).build();
                player[0].addListener(new Player.Listener() { @Override public void onPlaybackStateChanged(int state) { if(state==Player.STATE_READY) ready.countDown(); } });
                recovery[0]=new PlaybackRecovery(player[0],true,text -> { if(text.contains("ponawianie")) recovering.set(true); });
                player[0].setMediaItem(new MediaItem.Builder().setUri(address).setMimeType(MimeTypes.VIDEO_MP2T).build());
                player[0].prepare(); player[0].play();
            });
            try {
                assertTrue("Player did not resume after HTTP 503",ready.await(60,TimeUnit.SECONDS));
                assertTrue("Recovery was not exercised",recovering.get()); assertTrue(requests.get()>=2);
            } finally { instrument.runOnMainSync(() -> { recovery[0].close(); player[0].release(); }); }
        }
    }
}
