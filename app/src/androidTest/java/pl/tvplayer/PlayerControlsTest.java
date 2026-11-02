package pl.tvplayer;

import android.app.*;
import android.content.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.media3.ui.PlayerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
@RunWith(AndroidJUnit4.class)
public class PlayerControlsTest {
    private Instrumentation instrument() { return InstrumentationRegistry.getInstrumentation(); }
    private Context context() { return instrument().getTargetContext(); }
    private void waitFocus(Activity activity) throws Exception { long until=SystemClock.elapsedRealtime()+7000; while(!activity.hasWindowFocus() && SystemClock.elapsedRealtime()<until) { Thread.sleep(80); instrument().waitForIdleSync(); } assertTrue("Player has no input focus",activity.hasWindowFocus()); }
    private View toolbar(PlayerActivity activity) throws Exception { java.lang.reflect.Field f=PlayerActivity.class.getDeclaredField("toolbar"); f.setAccessible(true); return (View)f.get(activity); }
    private PlayerView video(PlayerActivity activity) throws Exception { java.lang.reflect.Field f=PlayerActivity.class.getDeclaredField("view"); f.setAccessible(true); return (PlayerView)f.get(activity); }
    private void waitToolbar(PlayerActivity activity,int visibility) throws Exception { long until=SystemClock.elapsedRealtime()+7000; while(toolbar(activity).getVisibility()!=visibility && SystemClock.elapsedRealtime()<until) { Thread.sleep(80); instrument().waitForIdleSync(); } assertEquals("Wrong toolbar visibility",visibility,toolbar(activity).getVisibility()); }
    private View description(View root,String value) { if(value.contentEquals(root.getContentDescription()==null?"":root.getContentDescription())) return root; if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) { View found=description(((ViewGroup)root).getChildAt(i),value); if(found!=null) return found; } return null; }
    private View text(View root,String value) { if(root instanceof TextView && value.equals(((TextView)root).getText().toString())) return root; if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) { View found=text(((ViewGroup)root).getChildAt(i),value); if(found!=null) return found; } return null; }
    private int[] center(View target) { int[] point=new int[2]; instrument().runOnMainSync(() -> { target.getLocationOnScreen(point); point[0]+=target.getWidth()/2; point[1]+=target.getHeight()/2; }); return point; }
    private void tap(int[] point) { long now=SystemClock.uptimeMillis(); MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,point[0],point[1],0),up=MotionEvent.obtain(now,now+30,MotionEvent.ACTION_UP,point[0],point[1],0); try { instrument().sendPointerSync(down); instrument().sendPointerSync(up); } finally { down.recycle(); up.recycle(); } instrument().waitForIdleSync(); }
    @Test public void liveToolbarHidesAndTapRevealsWithoutActivatingHiddenBack() throws Exception {
        MockWebServer server=new MockWebServer();
        byte[] packets=new byte[188*10000]; java.util.Arrays.fill(packets,(byte)0xFF); for(int offset=0;offset<packets.length;offset+=188) { packets[offset]=0x47; packets[offset+1]=0x1F; packets[offset+2]=(byte)0xFF; packets[offset+3]=0x10; }
        server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) { return new MockResponse().setHeader("Content-Type","video/mp2t").setBody(new okio.Buffer().write(packets)).throttleBody(18800,250,java.util.concurrent.TimeUnit.MILLISECONDS); } }); server.start();
        Activity home=null; PlayerActivity activity=null;
        try {
            instrument().getUiAutomation();
            home=instrument().startActivitySync(context().getPackageManager().getLaunchIntentForPackage(context().getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)); waitFocus(home);
            activity=(PlayerActivity)instrument().startActivitySync(new Intent(context(),PlayerActivity.class).putExtra("url",server.url("/live.ts").toString()).putExtra("name","Test TV").putExtra("kind","tv").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); waitFocus(activity);
            View back=description(activity.getWindow().getDecorView(),"Wróć do biblioteki"); assertNotNull(back); int[] hiddenBack=center(back);
            waitToolbar(activity,View.GONE); assertFalse(video(activity).isControllerFullyVisible());
            tap(hiddenBack); waitToolbar(activity,View.VISIBLE); assertFalse("First reveal tap activated hidden Back",activity.isFinishing()); long visibleUntil=SystemClock.elapsedRealtime()+2000; while(!video(activity).isControllerFullyVisible() && SystemClock.elapsedRealtime()<visibleUntil) { Thread.sleep(50); instrument().waitForIdleSync(); } assertTrue(video(activity).isControllerFullyVisible());
            // A second tap while controls are visible renews the timeout instead of hiding them.
            int[] screen=center(video(activity)); screen[1]/=2; tap(screen); waitToolbar(activity,View.VISIBLE); Thread.sleep(700); assertEquals(View.VISIBLE,toolbar(activity).getVisibility());
            waitToolbar(activity,View.GONE); tap(screen); waitToolbar(activity,View.VISIBLE);
            View options=text(toolbar(activity),"Opcje"); assertNotNull(options); tap(center(options));
            long until=SystemClock.elapsedRealtime()+4000; boolean opened=false;
            while(SystemClock.elapsedRealtime()<until) { android.view.accessibility.AccessibilityNodeInfo root=instrument().getUiAutomation().getRootInActiveWindow(); if(root!=null && !root.findAccessibilityNodeInfosByText("Ścieżka audio").isEmpty()) { opened=true; break; } Thread.sleep(80); }
            assertTrue("Visible Options button stopped working",opened); instrument().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        } finally {
            final PlayerActivity closing=activity; if(closing!=null) instrument().runOnMainSync(closing::finish);
            final Activity launcher=home; if(launcher!=null) instrument().runOnMainSync(launcher::finish); instrument().waitForIdleSync(); server.shutdown();
        }
    }
}
