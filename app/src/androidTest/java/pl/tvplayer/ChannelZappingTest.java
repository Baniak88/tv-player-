package pl.tvplayer;

import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.util.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
@RunWith(AndroidJUnit4.class)
public class ChannelZappingTest {
    private android.app.Instrumentation instrument() { return InstrumentationRegistry.getInstrumentation(); }
    private Context context() { return instrument().getTargetContext(); }
    private MockWebServer server;
    private MainActivity main;
    private PlayerActivity activity;
    private Channel a,b,c;
    @Before public void setup() throws Exception {
        server=new MockWebServer(); byte[] packets=new byte[188*10000]; Arrays.fill(packets,(byte)0xFF); for(int offset=0;offset<packets.length;offset+=188) { packets[offset]=0x47; packets[offset+1]=0x1F; packets[offset+2]=(byte)0xFF; packets[offset+3]=0x10; }
        server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) { return new MockResponse().setHeader("Content-Type","video/mp2t").setBody(new okio.Buffer().write(packets)).throttleBody(18800,250,java.util.concurrent.TimeUnit.MILLISECONDS); } }); server.start();
        a=new Channel("Kanał A",server.url("/a.ts").toString(),"Sport","a.xml","tv",server.url("/guide-a").toString(),2,"Europe/Warsaw",0);
        b=new Channel("Kanał B",server.url("/b.ts").toString(),"Sport","b.xml","tv",server.url("/guide-b").toString(),7,"UTC",0);
        c=new Channel("Kanał C",server.url("/c.ts").toString(),"Informacje","c.xml","tv",server.url("/guide-c").toString(),1,"Europe/London",1);
        new LibraryStore(context()).save(Arrays.asList(a,new Channel("### Sport ###",server.url("/header.ts").toString(),"Sport","","tv").withOrder(0),b,c),new HashSet<>(Arrays.asList(a.url(),c.url())));
    }
    private View description(View root,String name) { if(name.contentEquals(root.getContentDescription()==null?"":root.getContentDescription())) return root; if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) { View hit=description(((ViewGroup)root).getChildAt(i),name); if(hit!=null) return hit; } return null; }
    private View text(View root,String name) { if(root instanceof TextView && name.equals(((TextView)root).getText().toString())) return root; if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) { View hit=text(((ViewGroup)root).getChildAt(i),name); if(hit!=null) return hit; } return null; }
    private void waitText(View root,String name) throws Exception { long until=System.currentTimeMillis()+8000; while(text(root,name)==null && System.currentTimeMillis()<until) { instrument().waitForIdleSync(); Thread.sleep(60); } assertNotNull(name,text(root,name)); }
    private void start(boolean favorites) throws Exception {
        Intent intent=new Intent(context(),MainActivity.class).putExtra("section","tv").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK); if(!favorites) intent.putExtra("bouquet","Sport"); main=(MainActivity)instrument().startActivitySync(intent); View root=main.getWindow().getDecorView();
        if(favorites) { waitText(root,"Sport"); instrument().runOnMainSync(() -> text(root,"☆ Ulubione").performClick()); }
        waitText(root,"Kanał A"); View row=text(root,"Kanał A"); while(row.getParent() instanceof View && !(row.getParent() instanceof AdapterView)) row=(View)row.getParent(); final View chosen=row;
        android.app.Instrumentation.ActivityMonitor monitor=instrument().addMonitor(PlayerActivity.class.getName(),null,false); instrument().runOnMainSync(() -> { AdapterView<?> parent=(AdapterView<?>)chosen.getParent(); parent.performItemClick(chosen,parent.getPositionForView(chosen),0); View full=description(root,"Otwórz podgląd na pełnym ekranie"); assertNotNull(full); full.performClick(); }); activity=(PlayerActivity)monitor.waitForActivityWithTimeout(7000); instrument().removeMonitor(monitor); assertNotNull(activity); waitChannel(a);
    }
    private PlayerView video() throws Exception { java.lang.reflect.Field field=PlayerActivity.class.getDeclaredField("view"); field.setAccessible(true); return (PlayerView)field.get(activity); }
    private ExoPlayer engine() throws Exception { java.lang.reflect.Field field=PlayerActivity.class.getDeclaredField("player"); field.setAccessible(true); return (ExoPlayer)field.get(activity); }
    private void waitChannel(Channel channel) throws Exception { long until=System.currentTimeMillis()+7000; while(!channel.url().equals(activity.getIntent().getStringExtra("url")) && System.currentTimeMillis()<until) { instrument().waitForIdleSync(); Thread.sleep(60); } assertEquals(channel.url(),activity.getIntent().getStringExtra("url")); waitText(activity.getWindow().getDecorView(),channel.name()); assertEquals(channel.epgId(),activity.getIntent().getStringExtra("epgId")); assertEquals(channel.guideUrl(),activity.getIntent().getStringExtra("guide")); assertEquals(channel.archiveDays(),activity.getIntent().getIntExtra("days",0)); assertEquals(channel.timezone(),activity.getIntent().getStringExtra("timezone")); final boolean[] mediaReady={false}; until=System.currentTimeMillis()+7000; while(!mediaReady[0] && System.currentTimeMillis()<until) { instrument().runOnMainSync(() -> { try { ExoPlayer current=engine(); mediaReady[0]=current!=null && current.getCurrentMediaItem()!=null && channel.url().equals(current.getCurrentMediaItem().localConfiguration.uri.toString()); } catch(Exception e) { throw new AssertionError(e); } }); if(!mediaReady[0]) Thread.sleep(60); } assertTrue("Wrong playback URI",mediaReady[0]); }
    private void click(boolean next) throws Exception {
        final PlayerView view=video(); instrument().runOnMainSync(view::showController); instrument().waitForIdleSync(); View button=view.findViewById(next?androidx.media3.ui.R.id.exo_next:androidx.media3.ui.R.id.exo_prev); assertNotNull(button);
        long until=System.currentTimeMillis()+5000; while(!button.isEnabled() && System.currentTimeMillis()<until) { instrument().waitForIdleSync(); Thread.sleep(60); } assertTrue("Channel arrow disabled",button.isEnabled()); assertEquals(next?"Następny kanał":"Poprzedni kanał",button.getContentDescription().toString()); instrument().runOnMainSync(button::performClick); instrument().waitForIdleSync();
    }
    private void close(Activity target) throws Exception { if(target==null || target.isDestroyed()) return; instrument().runOnMainSync(target::finish); long until=System.currentTimeMillis()+5000; while(!target.isDestroyed() && System.currentTimeMillis()<until) { Thread.sleep(60); instrument().waitForIdleSync(); } }
    @After public void cleanup() throws Exception { close(activity); close(main); server.shutdown(); }
    @Test public void arrowsSwitchWithinBouquetAndUpdateStreamAndEpgMetadata() throws Exception {
        start(false); ExoPlayer old=engine(); click(true); waitChannel(b); assertNotSame("Old player was kept with the wrong EPG source",old,engine()); assertFalse(activity.isFinishing()); click(true); waitChannel(a); click(false); waitChannel(b); click(false); waitChannel(a); assertEquals("Sport",activity.getIntent().getStringExtra("navigation_group"));
    }
    @Test public void favoritesScopeAndCurrentChannelSurviveRecreationWithoutMemorySession() throws Exception {
        start(true); click(true); waitChannel(c); assertTrue(activity.getIntent().getBooleanExtra("navigation_favorites",false));
        java.lang.reflect.Field field=LiveChannelQueue.class.getDeclaredField("SESSIONS"); field.setAccessible(true); Map<?,?> sessions=(Map<?,?>)field.get(null); synchronized(sessions) { sessions.clear(); }
        android.app.Instrumentation.ActivityMonitor monitor=instrument().addMonitor(PlayerActivity.class.getName(),null,false); PlayerActivity old=activity; instrument().runOnMainSync(old::recreate); PlayerActivity recreated=(PlayerActivity)monitor.waitForActivityWithTimeout(10000); instrument().removeMonitor(monitor); assertNotNull("Player was not recreated",recreated); assertNotSame(old,recreated); activity=recreated; waitChannel(c); click(true); waitChannel(a); click(false); waitChannel(c);
    }
}

