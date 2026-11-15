package pl.tvplayer;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;
import static pl.tvplayer.TvTestUi.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
@RunWith(AndroidJUnit4.class)
public class TvLiveInfoTest {
    private PlayerActivity activity;
    private MockWebServer server;
    private ExoPlayer player() throws Exception { return (ExoPlayer)field(activity,"player"); }
    private void check(String message,BooleanSupplier condition) throws Exception { waitFor(message,() -> { boolean[] ok={false}; instrument().runOnMainSync(() -> ok[0]=condition.getAsBoolean()); return ok[0]; }); }
    @Before public void setup() throws Exception {
        systemReady(); PlaybackAudio.pcm(context(),false); server=new MockWebServer();
        byte[] bytes;
        try(InputStream in=instrument().getContext().getAssets().open("audio-compat.mkv"); ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] buffer=new byte[8192]; for(int n;(n=in.read(buffer))!=-1;) out.write(buffer,0,n); bytes=out.toByteArray(); }
        server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
            if(!request.getPath().endsWith(".mkv")) return new MockResponse().setResponseCode(404);
            int from=0; String range=request.getHeader("Range"); if(range!=null) from=Integer.parseInt(range.substring(6).split("-")[0]);
            if(from>=bytes.length) return new MockResponse().setResponseCode(416);
            MockResponse response=new MockResponse().setHeader("Content-Type","video/x-matroska").setHeader("Accept-Ranges","bytes").setBody(new okio.Buffer().write(bytes,from,bytes.length-from));
            if(range!=null) response.setResponseCode(206).setHeader("Content-Range","bytes "+from+"-"+(bytes.length-1)+"/"+bytes.length); return response;
        }}); server.start();
    }
    private void start(boolean live) throws Exception { activity=(PlayerActivity)instrument().startActivitySync(new Intent(context(),PlayerActivity.class).putExtra("television",true).putExtra("kind",live?"tv":"movie").putExtra("url",server.url("/audio.mkv").toString()).putExtra("name","Kanał demonstracyjny").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)); waitFor("Player focus missing",activity::hasWindowFocus); }
    @Test public void liveInfoShowsCurrentNextProgressAndRemoteCanHideAndRevealIt() throws Exception {
        long now=System.currentTimeMillis()/1000;
        Channel channel=new Channel("Kanał demonstracyjny",server.url("/audio.mkv").toString(),"","","tv");
        GuideRepository.get(context()).acceptStream(channel,Arrays.asList(new Guide.Program("Wieczorny seans","",now-300,now+1500,""),new Guide.Program("Następna audycja","",now+1500,now+3000,"")));
        start(true); TvLiveInfoBar bar=(TvLiveInfoBar)field(activity,"liveInfo"); assertNotNull(bar);
        key(KeyEvent.KEYCODE_INFO); waitFor("Current programme missing",() -> find(activity.getWindow().getDecorView(),"Teraz  "+clock(now-300)+"–"+clock(now+1500)+"  Wieczorny seans")!=null);
        assertNotNull(find(activity.getWindow().getDecorView(),"Następnie  "+clock(now+1500)+"  Następna audycja"));
        instrument().runOnMainSync(() -> { android.widget.ProgressBar progress=(android.widget.ProgressBar)bar.getChildAt(2); assertTrue(progress.getProgress()>100 && progress.getProgress()<300); });
        instrument().runOnMainSync(() -> { View root=activity.getWindow().getDecorView(); Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888); root.draw(new Canvas(bitmap)); try(FileOutputStream out=context().openFileOutput("tv095-live-info.png",0)) { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); } catch(IOException e) { throw new AssertionError(e); } finally { bitmap.recycle(); } });
        key(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(View.GONE,bar.getVisibility()); key(KeyEvent.KEYCODE_INFO); assertEquals(View.VISIBLE,bar.getVisibility());
        key(KeyEvent.KEYCODE_BACK); assertFalse(activity.isFinishing()); assertEquals(View.GONE,bar.getVisibility());
        key(KeyEvent.KEYCODE_DPAD_CENTER); instrument().runOnMainSync(() -> bar.guide(new GuideRepository.Data(Collections.emptyList(),"",""))); assertNotNull(find(activity.getWindow().getDecorView(),"Brak bieżącego EPG od dostawcy"));
    }
    @Test public void pcmModeSelectsAndDecodesAacAndSurvivesPlayerRecreation() throws Exception {
        androidx.media3.common.audio.ChannelMixingAudioProcessor mixer=PlaybackAudio.mixer();
        mixer.configure(new androidx.media3.common.audio.AudioProcessor.AudioFormat(48000,6,C.ENCODING_PCM_16BIT)); mixer.flush();
        java.nio.ByteBuffer sample=java.nio.ByteBuffer.allocateDirect(12).order(java.nio.ByteOrder.nativeOrder());
        sample.putShort((short)0).putShort((short)0).putShort((short)10000).putShort((short)0).putShort((short)0).putShort((short)0).flip(); mixer.queueInput(sample);
        java.nio.ByteBuffer stereo=mixer.getOutput().order(java.nio.ByteOrder.nativeOrder()); assertEquals(4,stereo.remaining()); short left=stereo.getShort(),right=stereo.getShort(); assertTrue("Downmix lost centre-channel dialogue",left>1000); assertEquals(left,right); mixer.reset();
        start(false); ExoPlayer before=player(); check("Video not ready",() -> before.getPlaybackState()==Player.STATE_READY); instrument().runOnMainSync(() -> { before.pause(); before.seekTo(5000); });
        key(KeyEvent.KEYCODE_MENU); click("Zgodność dźwięku"); click("Stereo PCM · gdy brak dźwięku");
        ExoPlayer current=player(); assertNotSame(before,current); assertTrue(PlaybackAudio.pcm(context()));
        check("PCM change lost paused position",() -> !current.getPlayWhenReady() && Math.abs(current.getCurrentPosition()-5000)<500);
        instrument().runOnMainSync(current::play);
        check("PCM path did not decode AAC",() -> current.getAudioFormat()!=null && MimeTypes.AUDIO_AAC.equals(current.getAudioFormat().sampleMimeType) && current.getAudioDecoderCounters()!=null && current.getAudioDecoderCounters().renderedOutputBufferCount>0);
        instrument().runOnMainSync(() -> assertEquals(2,current.getTrackSelectionParameters().maxAudioChannelCount));
        close(activity); activity=null; start(false); ExoPlayer restored=player(); check("Saved PCM mode not applied",() -> restored.getAudioFormat()!=null && MimeTypes.AUDIO_AAC.equals(restored.getAudioFormat().sampleMimeType));
    }
    private String clock(long time) { return java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochSecond(time)); }
    @After public void cleanup() throws Exception { close(activity); PlaybackAudio.pcm(context(),false); if(server!=null) server.shutdown(); }
}
