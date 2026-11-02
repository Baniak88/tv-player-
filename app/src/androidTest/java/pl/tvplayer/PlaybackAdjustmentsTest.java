package pl.tvplayer;

import android.app.*;
import android.content.*;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
@RunWith(AndroidJUnit4.class)
public class PlaybackAdjustmentsTest {
    private Instrumentation instrument() { return InstrumentationRegistry.getInstrumentation(); }
    private Context context() { return instrument().getTargetContext(); }
    private Object field(PlayerActivity activity,String name) throws Exception { java.lang.reflect.Field field=PlayerActivity.class.getDeclaredField(name); field.setAccessible(true); return field.get(activity); }
    private View find(View root,String text) {
        if(root instanceof TextView && text.equals(((TextView)root).getText().toString())) return root;
        if(text.contentEquals(root.getContentDescription()==null?"":root.getContentDescription())) return root;
        if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) { View found=find(((ViewGroup)root).getChildAt(i),text); if(found!=null) return found; } return null;
    }
    private View dialogView(String text) throws Exception {
        long until=SystemClock.elapsedRealtime()+3000;
        while(SystemClock.elapsedRealtime()<until) {
            final View[] found={null}; instrument().runOnMainSync(() -> { for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews()) if(root.isShown()) { View candidate=find(root,text); if(candidate!=null) { found[0]=candidate; break; } } });
            if(found[0]!=null && found[0].getWidth()>0 && found[0].getHeight()>0) return found[0]; Thread.sleep(80);
        } throw new AssertionError("Missing dialog view: "+text);
    }
    private void swipe(PlayerActivity activity,View video,float fraction,float from,float to) {
        instrument().runOnMainSync(() -> {
            int[] offset=new int[2],origin=new int[2]; video.getLocationOnScreen(offset); activity.getWindow().getDecorView().getLocationOnScreen(origin);
            float x=offset[0]-origin[0]+video.getWidth()*fraction; long start=SystemClock.uptimeMillis();
            for(int i=0;i<=10;i++) {
                float y=offset[1]-origin[1]+video.getHeight()*(from+(to-from)*i/10f);
                MotionEvent event=MotionEvent.obtain(start,start+i*16,i==0?MotionEvent.ACTION_DOWN:i==10?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,x,y,0); event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                try { activity.dispatchTouchEvent(event); } finally { event.recycle(); }
            }
        }); instrument().waitForIdleSync();
    }
    private void clickItem(View target) {
        View row=target;
        while(row.getParent() instanceof View && !(row.getParent() instanceof AdapterView)) row=(View)row.getParent();
        final View selected=row;
        if(selected.getParent() instanceof AdapterView) instrument().runOnMainSync(() -> { AdapterView<?> list=(AdapterView<?>)selected.getParent(); list.performItemClick(selected,list.getPositionForView(selected),0); });
        else instrument().runOnMainSync(target::performClick);
    }
    private void dragSlider(SeekBar bar,float percent) {
        instrument().runOnMainSync(() -> {
            long start=SystemClock.uptimeMillis(); float x=bar.getPaddingLeft()+(bar.getWidth()-bar.getPaddingLeft()-bar.getPaddingRight())*percent,y=bar.getHeight()/2f;
            MotionEvent down=MotionEvent.obtain(start,start,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(start,start+30,MotionEvent.ACTION_UP,x,y,0);
            try { bar.dispatchTouchEvent(down); bar.dispatchTouchEvent(up); } finally { down.recycle(); up.recycle(); }
        }); instrument().waitForIdleSync();
    }
    @Test public void gesturesAndAccessibleSlidersChangeRealVolumeAndWindowBrightnessWhilePlaying() throws Exception {
        MockWebServer server=new MockWebServer(); byte[] payload;
        try(java.io.InputStream input=instrument().getContext().getAssets().open("live-controls.ts");java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream()) { byte[] bytes=new byte[8192]; int read; while((read=input.read(bytes))!=-1) output.write(bytes,0,read); payload=output.toByteArray(); }
        server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) { return new MockResponse().setHeader("Content-Type","video/mp2t").setBody(new okio.Buffer().write(payload)); } }); server.start();
        AudioManager audio=(AudioManager)context().getSystemService(Context.AUDIO_SERVICE); int previous=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        int systemBrightness=android.provider.Settings.System.getInt(context().getContentResolver(),android.provider.Settings.System.SCREEN_BRIGHTNESS,128);
        Activity home=null; PlayerActivity activity=null;
        try {
            instrument().setInTouchMode(true);
            home=instrument().startActivitySync(new Intent(context(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            activity=(PlayerActivity)instrument().startActivitySync(new Intent(context(),PlayerActivity.class).putExtra("url",server.url("/controls.ts").toString()).putExtra("name","Test gestów").putExtra("kind","tv").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final PlayerActivity opened=activity; ExoPlayer engine=(ExoPlayer)field(activity,"player"); PlayerView video=(PlayerView)field(activity,"view"); View toolbar=(View)field(activity,"toolbar");
            long until=SystemClock.elapsedRealtime()+15000; boolean playing=false;
            while(SystemClock.elapsedRealtime()<until) { final boolean[] value={false}; instrument().runOnMainSync(() -> value[0]=engine.isPlaying()); if(value[0]) { playing=true; break; } Thread.sleep(80); }
            assertTrue("Real H264/AAC fixture did not start",playing);
            instrument().runOnMainSync(() -> { WindowManager.LayoutParams attrs=opened.getWindow().getAttributes(); attrs.screenBrightness=0.5f; opened.getWindow().setAttributes(attrs); audio.setStreamVolume(AudioManager.STREAM_MUSIC,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)/2,0); });
            until=SystemClock.elapsedRealtime()+7000; while(toolbar.getVisibility()!=View.GONE && SystemClock.elapsedRealtime()<until) { Thread.sleep(80); instrument().waitForIdleSync(); } assertEquals(View.GONE,toolbar.getVisibility());
            swipe(activity,video,0.25f,0.6f,0.4f); assertTrue("Left swipe did not brighten window",activity.getWindow().getAttributes().screenBrightness>0.65f);
            swipe(activity,video,0.25f,0.4f,0.6f); assertTrue("Left downward swipe did not dim window",activity.getWindow().getAttributes().screenBrightness<0.6f);
            int initial=audio.getStreamVolume(AudioManager.STREAM_MUSIC); swipe(activity,video,0.75f,0.6f,0.4f); assertTrue("Right swipe did not increase media volume",audio.getStreamVolume(AudioManager.STREAM_MUSIC)>initial);
            swipe(activity,video,0.75f,0.4f,0.6f); assertTrue("Right downward swipe did not lower media volume",audio.getStreamVolume(AudioManager.STREAM_MUSIC)<=initial+1);
            assertSame("Adjustments recreated playback",engine,field(activity,"player")); assertFalse(activity.isFinishing());
            instrument().runOnMainSync(video::showController); instrument().waitForIdleSync(); View options=find(toolbar,"Opcje"); assertNotNull(options); instrument().runOnMainSync(options::performClick);
            View choice=dialogView("Głośność i jasność"); clickItem(choice);
            SeekBar brightness=(SeekBar)dialogView("Jasność"),volume=(SeekBar)dialogView("Głośność");
            dragSlider(brightness,0.8f); assertTrue(activity.getWindow().getAttributes().screenBrightness>0.7f);
            dragSlider(volume,0.3f); assertTrue(audio.getStreamVolume(AudioManager.STREAM_MUSIC)<audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)/2);
            assertEquals("Player changed global brightness",systemBrightness,android.provider.Settings.System.getInt(context().getContentResolver(),android.provider.Settings.System.SCREEN_BRIGHTNESS,128));
            View automatic=dialogView("Jasność telefonu"); instrument().runOnMainSync(automatic::performClick); assertEquals(-1f,activity.getWindow().getAttributes().screenBrightness,0.001f);
            instrument().runOnMainSync(() -> { assertSame(engine,uncheckedPlayer(opened)); assertTrue("Video stopped during adjustments",engine.isPlaying()); assertTrue("Playback clock did not advance",engine.getCurrentPosition()>1000); });
        } finally {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,previous,0);
            final PlayerActivity closing=activity; if(closing!=null) instrument().runOnMainSync(closing::finish); final Activity launcher=home; if(launcher!=null) instrument().runOnMainSync(launcher::finish); instrument().waitForIdleSync(); server.shutdown();
        }
    }
    private ExoPlayer uncheckedPlayer(PlayerActivity activity) { try { return (ExoPlayer)field(activity,"player"); } catch(Exception error) { throw new AssertionError(error); } }
}
