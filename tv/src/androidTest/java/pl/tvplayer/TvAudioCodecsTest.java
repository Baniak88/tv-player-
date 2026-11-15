package pl.tvplayer;

import android.app.*;
import android.content.*;
import android.media.AudioTrack;
import android.view.*;
import androidx.media3.common.*;
import androidx.media3.decoder.ffmpeg.FfmpegLibrary;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.audio.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
import static pl.tvplayer.TvTestUi.*;

/** Real encoded packets -> FFmpeg -> the production stereo mixer -> Android AudioTrack. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
@RunWith(AndroidJUnit4.class)
public class TvAudioCodecsTest {
    private MockWebServer server;
    private Activity activity;
    private TvActivity home;
    private boolean hadMode,oldMode;
    @Before public void setup() throws Exception {
        systemReady(); android.content.SharedPreferences prefs=context().getSharedPreferences("playback-audio",0); hadMode=prefs.contains("pcm"); oldMode=prefs.getBoolean("pcm",true); prefs.edit().putBoolean("pcm",true).commit();
        new android.util.AtomicFile(new File(context().getFilesDir(),"tv-account.enc")).delete();
        Map<String,byte[]> data=new HashMap<>();
        for(String name:Arrays.asList("aac.ts","ac3.ts","eac3.ts","mp2.ts","dca.mkv")) try(InputStream input=instrument().getContext().getAssets().open("generated-audio/"+name); ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] bytes=new byte[8192]; for(int n;(n=input.read(bytes))!=-1;) out.write(bytes,0,n); data.put("/"+name,out.toByteArray()); }
        server=new MockWebServer(); server.setDispatcher(new Dispatcher() { public MockResponse dispatch(RecordedRequest request) {
            byte[] bytes=data.get(request.getRequestUrl().encodedPath()); if(bytes==null) return new MockResponse().setResponseCode(404);
            String range=request.getHeader("Range"); int offset=range==null?0:Integer.parseInt(range.substring(6).split("-")[0]); if(offset>=bytes.length) return new MockResponse().setResponseCode(416);
            MockResponse response=new MockResponse().setHeader("Accept-Ranges","bytes").setHeader("Content-Type",request.getPath().endsWith(".mkv")?"video/x-matroska":"video/mp2t").setBody(new okio.Buffer().write(bytes,offset,bytes.length-offset));
            return range==null?response:response.setResponseCode(206).setHeader("Content-Range","bytes "+offset+"-"+(bytes.length-1)+"/"+bytes.length);
        }}); server.start();
    }
    private void verify(ExoPlayer player,String mime,int channels) throws Exception {
        waitFor("Software audio did not reach AudioTrack: "+mime,() -> {
            boolean[] ready={false}; instrument().runOnMainSync(() -> {
                try {
                    if(player.getAudioFormat()==null || !mime.equals(player.getAudioFormat().sampleMimeType) || player.getAudioDecoderCounters()==null || player.getAudioDecoderCounters().renderedOutputBufferCount<3) return;
                    for(int i=0;i<player.getRendererCount();i++) { Renderer renderer=player.getRenderer(i); if(renderer.getTrackType()!=C.TRACK_TYPE_AUDIO || renderer.getState()==Renderer.STATE_DISABLED || !renderer.getName().equals("FfmpegAudioRenderer")) continue;
                        java.lang.reflect.Field sinkField=DecoderAudioRenderer.class.getDeclaredField("audioSink"); sinkField.setAccessible(true); Object sink=sinkField.get(renderer);
                        java.lang.reflect.Field trackField=DefaultAudioSink.class.getDeclaredField("audioTrack"); trackField.setAccessible(true); AudioTrack track=(AudioTrack)trackField.get(sink);
                        ready[0]=track!=null && track.getChannelCount()==2 && track.getPlayState()==AudioTrack.PLAYSTATE_PLAYING && player.getAudioFormat().channelCount==channels;
                    }
                } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
            }); return ready[0];
        });
    }
    private void play(String name,String mime,int channels) throws Exception {
        activity=instrument().startActivitySync(new Intent(context(),PlayerActivity.class).putExtra("television",true).putExtra("kind","movie").putExtra("url",server.url("/"+name).toString()).putExtra("name","Test dźwięku "+name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        verify((ExoPlayer)field(activity,"player"),mime,channels);
    }
    @Test public void nativeLibrarySupportsTvAudioAndNewProfilesDefaultToPcm() {
        context().getSharedPreferences("playback-audio",0).edit().remove("pcm").commit(); assertTrue(PlaybackAudio.pcm(context()));
        assertTrue("Native audio library absent from APK",FfmpegLibrary.isAvailable()); assertTrue(FfmpegLibrary.getVersion().startsWith("Lavc60."));
        for(String mime:Arrays.asList(MimeTypes.AUDIO_AAC,MimeTypes.AUDIO_AC3,MimeTypes.AUDIO_E_AC3,MimeTypes.AUDIO_MPEG_L2,MimeTypes.AUDIO_DTS)) assertTrue(mime,FfmpegLibrary.supportsFormat(mime));
        assertFalse("Software video must not replace hardware video",FfmpegLibrary.supportsFormat(MimeTypes.VIDEO_H264));
    }
    @Test public void aacStereoReachesAudioTrack() throws Exception { play("aac.ts",MimeTypes.AUDIO_AAC,2); }
    @Test public void ac3SurroundIsDecodedAndDownmixedToStereo() throws Exception { play("ac3.ts",MimeTypes.AUDIO_AC3,6); }
    @Test public void eac3SurroundIsDecodedAndDownmixedToStereo() throws Exception { play("eac3.ts",MimeTypes.AUDIO_E_AC3,6); }
    @Test public void mpegLayerTwoReachesAudioTrackWithoutAPlatformDecoder() throws Exception { play("mp2.ts",MimeTypes.AUDIO_MPEG_L2,2); }
    @Test public void dtsReachesStereoAudioTrack() throws Exception { play("dca.mkv",MimeTypes.AUDIO_DTS,2); }
    @Test public void previewHandsTheWorkingAc3DecoderToFullScreen() throws Exception {
        Channel channel=new Channel("Test AC3",server.url("/ac3.ts").toString(),"Test","","tv"); new LibraryStore(context()).save(Collections.singletonList(channel),Collections.emptySet());
        home=(TvActivity)instrument().startActivitySync(new Intent(context(),TvActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitFor("Home shortcut missing",() -> home.hasWindowFocus() && home.getCurrentFocus()!=null && "Telewizja na żywo".contentEquals(home.getCurrentFocus().getContentDescription()));
        instrument().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        waitFor("Preview missing",() -> { try { Object preview=field(home,"livePreview"); return preview!=null && field(preview,"player")!=null; } catch(Exception e) { return false; } });
        ExoPlayer original=(ExoPlayer)field(field(home,"livePreview"),"player"); verify(original,MimeTypes.AUDIO_AC3,6);
        Instrumentation.ActivityMonitor monitor=instrument().addMonitor(PlayerActivity.class.getName(),null,false);
        try { instrument().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT); instrument().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); activity=monitor.waitForActivityWithTimeout(15000); } finally { instrument().removeMonitor(monitor); }
        assertNotNull(activity); assertSame("Full screen opened a different decoder",original,field(activity,"player")); verify(original,MimeTypes.AUDIO_AC3,6);
    }
    @After public void cleanup() throws Exception { close(activity); close(home); if(server!=null) server.shutdown(); android.content.SharedPreferences.Editor prefs=context().getSharedPreferences("playback-audio",0).edit(); if(hadMode) prefs.putBoolean("pcm",oldMode); else prefs.remove("pcm"); prefs.commit(); }
}
