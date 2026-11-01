package pl.tvplayer;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import static org.junit.Assert.*;
import android.content.Intent;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.view.*;
import android.widget.*;
import java.util.*;
@RunWith(AndroidJUnit4.class)
public class NavigationTest {
    private android.app.Instrumentation getInstrumentation() { return InstrumentationRegistry.getInstrumentation(); }
    @Test public void testRecordingServiceWritesTs() throws Exception {
        Context context=getInstrumentation().getTargetContext();
        MainActivity main=(MainActivity)getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
        okhttp3.mockwebserver.MockWebServer server=new okhttp3.mockwebserver.MockWebServer();
        byte[] packet=new byte[188]; packet[0]=0x47;
        okio.Buffer body=new okio.Buffer(); for(int i=0;i<10000;i++) body.write(packet);
        server.enqueue(new okhttp3.mockwebserver.MockResponse().setHeader("Content-Type","video/mp2t").setBody(body).throttleBody(18800,100,java.util.concurrent.TimeUnit.MILLISECONDS));
        server.start();
        java.io.File file=new java.io.File(context.getFilesDir(),"recording-test.ts");
        try {
            Intent service=new Intent(context,RecordingService.class).putExtra("destination",android.net.Uri.fromFile(file).toString())
                .putExtra("url",server.url("/live.ts").toString()).putExtra("name","Test recording").putExtra("minutes",1);
            context.startForegroundService(service);
            long deadline=System.currentTimeMillis()+8000;
            while(file.length()<18800 && System.currentTimeMillis()<deadline) Thread.sleep(100);
            assertTrue("Recording did not write TS data",file.length()>=18800);
            context.stopService(new Intent(context,RecordingService.class));
            Thread.sleep(500);
            try(java.io.FileInputStream input=new java.io.FileInputStream(file)) { assertEquals(0x47,input.read()); }
        } finally {
            context.stopService(new Intent(context,RecordingService.class)); server.shutdown(); file.delete();
            getInstrumentation().runOnMainSync(main::finish);
        }
    }

    private View find(View root,String text) {
        if(root instanceof TextView && text.equals(((TextView)root).getText().toString())) return root;
        if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) {
            View found=find(((ViewGroup)root).getChildAt(i),text); if(found!=null) return found;
        }
        return null;
    }
    @Test public void testTabsAndFullscreen() throws Exception {
        Context context=getInstrumentation().getTargetContext();
        new LibraryStore(context).save(Arrays.asList(
            new Channel("Test TV","https://example.com/live.ts","Test","","tv"),
            new Channel("Test Film","https://example.com/movie.mp4","Test","","movie"),
            new Channel("Test Serial","https://example.com/player_api.php","Test","","series")),Collections.emptySet());
        MainActivity main=(MainActivity)getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Thread.sleep(1500); getInstrumentation().waitForIdleSync();
        View root=main.getWindow().getDecorView();
        assertNotNull(find(root,"EPG")); assertNotNull(find(root,"TV")); assertNotNull(find(root,"Seriale")); assertNotNull(find(root,"Filmy"));
        getInstrumentation().runOnMainSync(() -> find(root,"Filmy").performClick());
        getInstrumentation().waitForIdleSync(); assertNotNull(find(root,"Test Film")); assertNull(find(root,"Test TV"));
        getInstrumentation().runOnMainSync(() -> find(root,"Seriale").performClick());
        getInstrumentation().waitForIdleSync(); assertNotNull(find(root,"Test Serial")); assertNull(find(root,"Test Film"));
        PlayerActivity player=(PlayerActivity)getInstrumentation().startActivitySync(new Intent(context,PlayerActivity.class).putExtra("url","https://example.com/movie.mp4").putExtra("name","Test Film").putExtra("kind","movie").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,player.getRequestedOrientation());
        getInstrumentation().runOnMainSync(() -> find(player.getWindow().getDecorView(),"↙").performClick());
        getInstrumentation().waitForIdleSync(); assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,player.getRequestedOrientation());
        getInstrumentation().runOnMainSync(() -> find(player.getWindow().getDecorView(),"⤢").performClick());
        getInstrumentation().waitForIdleSync(); assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,player.getRequestedOrientation());
        getInstrumentation().runOnMainSync(() -> find(player.getWindow().getDecorView(),"Opcje").performClick());
        getInstrumentation().waitForIdleSync();
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        getInstrumentation().runOnMainSync(() -> { player.finish(); main.finish(); });
    }
}
