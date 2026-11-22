package pl.tvplayer;

import android.app.*;
import android.content.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static pl.tvplayer.TvTestUi.*;

@RunWith(AndroidJUnit4.class)
public class TvRecordingTest {
    @Test public void missingOrBlockedDocumentPickerDoesNotCrash() {
        instrument().runOnMainSync(() -> {
        Activity missing=new Activity() { @Override public void startActivityForResult(Intent intent,int request) { throw new ActivityNotFoundException("No documents UI on this TV"); } };
        Activity blocked=new Activity() { @Override public void startActivityForResult(Intent intent,int request) { throw new SecurityException("Blocked picker"); } };
        assertFalse(PlayerActivity.requestRecordingFile(missing,72)); assertFalse(PlayerActivity.requestRecordingFile(blocked,72));
        final Intent[] requested={null};
        Activity supported=new Activity() { @Override public void startActivityForResult(Intent intent,int request) { requested[0]=intent; assertEquals(72,request); } };
        assertTrue(PlayerActivity.requestRecordingFile(supported,72)); assertEquals(Intent.ACTION_CREATE_DOCUMENT,requested[0].getAction()); assertEquals("video/mp2t",requested[0].getType());
        });
    }
    @Test public void recordingWritesExactTransportBytesAndStops() throws Exception {
        byte[] packets=new byte[188*1000]; for(int i=0;i<packets.length;i++) packets[i]=(byte)(i%251); for(int i=0;i<packets.length;i+=188) packets[i]=0x47;
        File target=new File(context().getFilesDir(),"fire-recording-test.ts");
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setHeader("Content-Type","video/mp2t").setBody(new okio.Buffer().write(packets)).throttleBody(18800,100,TimeUnit.MILLISECONDS)); server.start();
            start(target,server.url("/live.ts").toString()); waitFor("Recording did not finish",() -> target.length()==packets.length && !RecordingService.active);
            assertEquals(SourceClient.PLAYER_AGENT,server.takeRequest(2,TimeUnit.SECONDS).getHeader("User-Agent"));
            try(FileInputStream input=new FileInputStream(target);ByteArrayOutputStream output=new ByteArrayOutputStream()) { byte[] buffer=new byte[4096]; for(int n;(n=input.read(buffer))!=-1;) output.write(buffer,0,n); assertArrayEquals(packets,output.toByteArray()); }
        } finally { context().stopService(new Intent(context(),RecordingService.class)); target.delete(); }
    }
    @Test public void providerErrorAndStopCancelRecording() throws Exception {
        File target=new File(context().getFilesDir(),"fire-recording-error.ts");
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(403)); server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)); server.start();
            start(target,server.url("/live.ts").toString()); assertNotNull(server.takeRequest(5,TimeUnit.SECONDS)); waitFor("Recording active after HTTP 403",() -> !RecordingService.active); assertEquals(0,target.length());
            start(target,server.url("/live.ts").toString()); assertNotNull(server.takeRequest(5,TimeUnit.SECONDS)); waitFor("Recording not active while the provider is silent",() -> RecordingService.active);
            context().startService(new Intent(context(),RecordingService.class).setAction("STOP")); waitFor("Stop did not cancel recording",() -> !RecordingService.active);
        } finally { context().stopService(new Intent(context(),RecordingService.class)); target.delete(); }
    }
    private void start(File target,String url) {
        Intent intent=new Intent(context(),RecordingService.class).setData(android.net.Uri.fromFile(target)).putExtra("url",url).putExtra("name","Recording check").putExtra("minutes",1);
        if(android.os.Build.VERSION.SDK_INT>=26) context().startForegroundService(intent); else context().startService(intent);
    }
}
