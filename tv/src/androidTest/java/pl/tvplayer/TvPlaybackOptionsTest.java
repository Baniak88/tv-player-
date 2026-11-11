package pl.tvplayer;

import android.app.*;
import android.content.*;
import android.view.KeyEvent;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;
import static pl.tvplayer.TvTestUi.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
@RunWith(AndroidJUnit4.class)
public class TvPlaybackOptionsTest {
    private PlayerActivity activity;
    private Activity archive;
    @Before public void setup() throws Exception { systemReady(); }
    private ExoPlayer player() throws Exception { return (ExoPlayer)field(activity,"player"); }
    private void playerWait(String message,BooleanSupplier condition) throws Exception {
        waitFor(message,() -> { boolean[] result={false}; instrument().runOnMainSync(() -> result[0]=condition.getAsBoolean()); return result[0]; });
    }
    private byte[] asset(String name) throws Exception {
        try(InputStream input=instrument().getContext().getAssets().open(name);ByteArrayOutputStream output=new ByteArrayOutputStream()) { byte[] buffer=new byte[8192]; for(int n;(n=input.read(buffer))!=-1;) output.write(buffer,0,n); return output.toByteArray(); }
    }
    private void options(String label) throws Exception { waitFor("Player has no input focus",activity::hasWindowFocus); key(KeyEvent.KEYCODE_MENU); click(label); }
    private boolean selected(ExoPlayer player,int type,String language) {
        for(Tracks.Group group:player.getCurrentTracks().getGroups()) if(group.getType()==type) for(int i=0;i<group.length;i++) if(group.isTrackSelected(i) && language.equals(group.getTrackFormat(i).language)) return true;
        return false;
    }
    @After public void cleanup() { close(archive); close(activity); context().getSharedPreferences("playback-network",0).edit().clear().commit(); }
    @Test public void realAudioSubtitlesSeekRestartAndBufferChangeWork() throws Exception {
        byte[] media=asset("multitrack.mkv");
        try(MockWebServer server=new MockWebServer()) {
            server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
                int from=0; String range=request.getHeader("Range"); if(range!=null) from=Integer.parseInt(range.substring(6).split("-")[0]);
                if(from>=media.length) return new MockResponse().setResponseCode(416);
                MockResponse response=new MockResponse().setHeader("Content-Type","video/x-matroska").setHeader("Accept-Ranges","bytes").setBody(new okio.Buffer().write(media,from,media.length-from));
                if(range!=null) response.setResponseCode(206).setHeader("Content-Range","bytes "+from+"-"+(media.length-1)+"/"+media.length);
                return response;
            }}); server.start(); String url=server.url("/"+java.util.UUID.randomUUID()+".mkv").toString();
            activity=(PlayerActivity)instrument().startActivitySync(new Intent(context(),PlayerActivity.class).putExtra("television",true).putExtra("url",url).putExtra("name","Tracks test").putExtra("kind","movie").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            ExoPlayer current=player(); playerWait("Multitrack video not ready",() -> current.getPlaybackState()==Player.STATE_READY); instrument().runOnMainSync(current::pause);
            options("Ścieżka audio"); click("English"); playerWait("English audio not selected",() -> selected(current,C.TRACK_TYPE_AUDIO,"en"));
            options("Ścieżka audio"); click("Polski"); playerWait("Polish audio not selected",() -> selected(current,C.TRACK_TYPE_AUDIO,"pl"));
            options("Napisy"); click("Polskie"); instrument().runOnMainSync(() -> current.seekTo(10000));
            playerWait("Polish caption not decoded",() -> current.getCurrentCues().cues.stream().anyMatch(c -> c.text!=null && c.text.toString().contains("Polskie napisy")));
            options("Napisy"); click("English"); playerWait("English caption not decoded",() -> current.getCurrentCues().cues.stream().anyMatch(c -> c.text!=null && c.text.toString().contains("English test")));
            options("Napisy"); click("Wyłącz napisy"); playerWait("Captions not disabled",() -> current.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) && current.getCurrentCues().cues.isEmpty());
            instrument().runOnMainSync(() -> current.seekTo(60000)); playerWait("Seek failed",() -> current.getCurrentPosition()>=59900);
            options("Cofnij o 30 sekund"); playerWait("Rewind failed",() -> Math.abs(current.getCurrentPosition()-30000)<500);
            options("Odtwórz od początku"); playerWait("Restart failed",() -> current.getCurrentPosition()<500);
            instrument().runOnMainSync(() -> current.seekTo(30000)); options("Połączenie internetowe"); click("Słabszy internet · większy bufor");
            ExoPlayer replacement=player(); assertNotSame(current,replacement); playerWait("Buffer change lost paused position",() -> !replacement.getPlayWhenReady() && Math.abs(replacement.getCurrentPosition()-30000)<500);
            playerWait("Buffer change lost selected audio",() -> selected(replacement,C.TRACK_TYPE_AUDIO,"pl"));
            instrument().runOnMainSync(() -> assertTrue("Buffer change enabled disabled subtitles",replacement.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)));
            assertTrue(PlaybackSettings.weakInternet(context()));
            options("Obraz w obrazie (PiP)"); assertFalse(activity.isFinishing());
        }
    }
    @Test public void epgArchiveKeepsTelevisionModeAndUsesProviderTimeshift() throws Exception {
        byte[] stream=asset("live-controls.ts"),segment=asset("short-live.ts"); long now=System.currentTimeMillis()/1000; AtomicInteger epgCalls=new AtomicInteger();
        try(MockWebServer server=new MockWebServer()) {
            server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
                if(request.getPath().contains("player_api.php")) { epgCalls.incrementAndGet(); return new MockResponse().setHeader("Content-Type","application/json").setBody("{\"epg_listings\":[{\"title\":\"Archiwum testowe\",\"description\":\"Opis programu\",\"start_timestamp\":"+(now-600)+",\"stop_timestamp\":"+(now+600)+"}]}"); }
                if(request.getPath().contains("/timeshift/")) return new MockResponse().setHeader("Content-Type","application/vnd.apple.mpegurl").setBody("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:9\n#EXT-X-MEDIA-SEQUENCE:0\n#EXTINF:8.1,\n/archive-segment.ts\n#EXT-X-ENDLIST\n");
                if(request.getPath().equals("/archive-segment.ts")) return new MockResponse().setHeader("Content-Type","video/mp2t").setBody(new okio.Buffer().write(segment));
                return new MockResponse().setHeader("Content-Type","video/mp2t").setBody(new okio.Buffer().write(stream));
            }}); server.start();
            activity=(PlayerActivity)instrument().startActivitySync(new Intent(context(),PlayerActivity.class).putExtra("television",true).putExtra("url",server.url("/live/test/pass/12.ts").toString()).putExtra("name","Test archiwum").putExtra("kind","tv").putExtra("days",2).putExtra("timezone","UTC").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            options("Program TV i archiwum"); waitFor("EPG unavailable",() -> node("TERAZ · "+java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochSecond(now-600))+"  Archiwum testowe  ↶")!=null);
            String label="TERAZ · "+java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochSecond(now-600))+"  Archiwum testowe  ↶"; click(label); assertNotNull(node("Opis programu")!=null?node("Opis programu"):node("Oglądaj od początku"));
            Instrumentation.ActivityMonitor monitor=instrument().addMonitor(PlayerActivity.class.getName(),null,false);
            try { click("Oglądaj od początku"); archive=monitor.waitForActivityWithTimeout(10000); } finally { instrument().removeMonitor(monitor); }
            assertNotNull("Archive player missing",archive); assertTrue(archive.getIntent().getBooleanExtra("television",false)); assertEquals("movie",archive.getIntent().getStringExtra("kind"));
            assertTrue(archive.getIntent().getStringExtra("url").contains("/timeshift/test/pass/")); assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,archive.getRequestedOrientation()); assertTrue(epgCalls.get()>0);
            ExoPlayer replay=(ExoPlayer)field(archive,"player"); playerWait("Archive HLS did not reach playback",() -> replay.getPlaybackState()==Player.STATE_READY); instrument().runOnMainSync(replay::pause);
        }
    }
}
