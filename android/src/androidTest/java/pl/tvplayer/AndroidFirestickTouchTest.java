package pl.tvplayer;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
import static pl.tvplayer.TvTestUi.*;

/** Real touch input on a phone, using the original Fire Stick launcher and detail screens. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
@RunWith(AndroidJUnit4.class)
public class AndroidFirestickTouchTest {
    protected TvActivity home;
    protected Activity child;
    protected MockWebServer server;
    protected Channel movie,series,a,b;
    protected final java.util.concurrent.atomic.AtomicInteger tvRequests=new java.util.concurrent.atomic.AtomicInteger(),seriesRequests=new java.util.concurrent.atomic.AtomicInteger();
    @Before public void setup() throws Exception {
        landscape(800,400); AndroidScreenTest.rotation(1); waitFor("Phone density has not settled",() -> context().getResources().getDisplayMetrics().densityDpi==160); instrument().waitForIdleSync(); resetNavigation();
        new android.util.AtomicFile(new File(context().getFilesDir(),"tv-account.enc")).delete();
        context().getSharedPreferences("watch-progress",0).edit().clear().commit();
        byte[] stream; try(InputStream input=instrument().getContext().getAssets().open("live-controls.ts"); ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] bytes=new byte[8192]; for(int n;(n=input.read(bytes))!=-1;) out.write(bytes,0,n); stream=out.toByteArray(); }
        Bitmap bitmap=Bitmap.createBitmap(240,360,Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(bitmap); Paint paint=new Paint(); paint.setColor(0xFF24445C); canvas.drawRect(0,0,240,360,paint); paint.setColor(0xFFF5C518); canvas.drawCircle(120,120,60,paint); ByteArrayOutputStream out=new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG,100,out); byte[] image=out.toByteArray(); bitmap.recycle();
        server=new MockWebServer(); server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
            if(request.getPath().contains("art.png")) return new MockResponse().setHeader("Content-Type","image/png").setBody(new okio.Buffer().write(image));
            if(request.getPath().contains("get_series_info")) { seriesRequests.incrementAndGet(); String art=server.url("/art.png").toString(); return new MockResponse().setHeader("Content-Type","application/json").setBody("{\"info\":{\"name\":\"Serial dotykowy\",\"cover\":\""+art+"\",\"plot\":\"Serial w interfejsie Fire Stick\"},\"episodes\":{\"1\":[{\"id\":1,\"season\":1,\"episode_num\":1,\"title\":\"Odcinek testowy\",\"container_extension\":\"ts\",\"info\":{\"movie_image\":\""+art+"\"}}]}}"); }
            if(request.getPath().contains(".ts") || request.getPath().contains(".mp4")) { if(request.getPath().startsWith("/a.ts") || request.getPath().startsWith("/b.ts")) tvRequests.incrementAndGet(); String range=request.getHeader("Range"); int offset=range==null?0:Integer.parseInt(range.substring(6).split("-")[0]); if(offset>=stream.length) return new MockResponse().setResponseCode(416); MockResponse response=new MockResponse().setHeader("Content-Type","video/mp2t").setHeader("Accept-Ranges","bytes").setBody(new okio.Buffer().write(stream,offset,stream.length-offset)); return range==null?response:response.setResponseCode(206).setHeader("Content-Range","bytes "+offset+"-"+(stream.length-1)+"/"+stream.length); }
            return new MockResponse().setResponseCode(404);
        }}); server.start();
        String art=server.url("/art.png").toString();
        a=new Channel("Kanał dotykowy A",server.url("/a.ts").toString(),"Test TV","","tv"); b=new Channel("Kanał dotykowy B",server.url("/b.ts").toString(),"Test TV","","tv");
        movie=new Channel("Film dotykowy",server.url("/film.mp4").toString(),"Test filmy","","movie").withLogo(art);
        series=new Channel("Serial dotykowy",server.url("/player_api.php?username=test&password=test&action=get_series_info&series_id=9").toString(),"Test seriale","","series").withLogo(art);
        new LibraryStore(context()).save(Arrays.asList(a,b,movie,series,new Channel("Biblioteka S02E03",server.url("/series/203.ts").toString(),"M3U","","episode")),Collections.emptySet());
        Intent launch=context().getPackageManager().getLaunchIntentForPackage(context().getPackageName()); assertNotNull(launch); assertEquals(TvActivity.class.getName(),launch.getComponent().getClassName());
        home=(TvActivity)instrument().startActivitySync(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitFor("Phone launcher did not gain focus",() -> {
            // Activity recreation after a display change can replace startActivitySync's instance.
            instrument().runOnMainSync(() -> { for(Activity active:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) if(active instanceof TvActivity && !active.isFinishing()) home=(TvActivity)active; });
            return home.hasWindowFocus();
        });
        AndroidScreenTest.rotate(home,false);
    }
    protected View root(Activity activity) { return activity.getWindow().getDecorView(); }
    protected View clickable(View view,String label) {
        if(!view.isShown()) return null;
        if(label.equals(String.valueOf(view.getContentDescription())) || view instanceof TextView && label.contentEquals(((TextView)view).getText())) {
            View target=view;
            while(!target.isClickable() && target.getParent() instanceof View) target=(View)target.getParent();
            if(target.isClickable()) return target;
        }
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) { View hit=clickable(((ViewGroup)view).getChildAt(i),label); if(hit!=null) return hit; }
        return null;
    }
    protected void tap(Activity activity,String label) throws Exception {
        Rect bounds=new Rect();
        try { waitFor("Visible touch target missing: "+label,() -> {
            boolean[] ready={false}; instrument().runOnMainSync(() -> {
                // A configuration callback can replace the navigation after focus is gained.
                // Resolve the current view on every pass instead of retaining the old subtree.
                View target=clickable(root(activity),label);
                if(target!=null) ready[0]=touchBounds(target,bounds);
            }); return ready[0];
        }); } catch(AssertionError failure) {
            instrument().runOnMainSync(() -> {
                android.util.Log.e("TestRunner","Touch failure: "+label+" activity="+activity+" destroyed="+activity.isDestroyed()+" finishing="+activity.isFinishing()+" focus="+activity.hasWindowFocus());
                dumpTouchTree(root(activity),"");
            });
            screenshot(activity,"android-touch-failure"); throw failure;
        }
        pointer(bounds);
    }
    private void dumpTouchTree(View view,String depth) {
        Rect box=new Rect(); boolean visible=view.getGlobalVisibleRect(box);
        android.util.Log.e("TestRunner",depth+view.getClass().getSimpleName()+" text="+(view instanceof TextView?((TextView)view).getText():view.getContentDescription())+" size="+view.getWidth()+"x"+view.getHeight()+" attached="+view.isAttachedToWindow()+" shown="+view.isShown()+" visible="+visible+" rect="+box);
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) dumpTouchTree(((ViewGroup)view).getChildAt(i),depth+" ");
    }
    private boolean touchBounds(View target,Rect bounds) {
        target.requestRectangleOnScreen(new Rect(0,0,target.getWidth(),target.getHeight()),true);
        if(!target.isAttachedToWindow() || !target.isShown() || !target.getGlobalVisibleRect(bounds) || bounds.width()<=0 || bounds.height()<=0) return false;
        // Dialog roots are offset from the screen origin. Root-relative rectangles
        // would tap another row (or the background) in a centred phone dialog.
        int[] screen=new int[2],window=new int[2]; View root=target.getRootView(); root.getLocationOnScreen(screen); root.getLocationInWindow(window);
        bounds.offset(screen[0]-window[0],screen[1]-window[1]);
        android.util.Log.i("TestRunner","Touch "+target.getClass().getSimpleName()+" screen="+bounds+" windowOffset="+(screen[0]-window[0])+","+(screen[1]-window[1])); return true;
    }
    protected void tap(View target) throws Exception {
        assertNotNull(target); Rect bounds=new Rect();
        // Window focus may arrive before the first layout after an emulator rotation.
        // Wait for an actual visible touch rectangle, rather than tapping a zero-sized view.
        waitFor("Touch target is outside the viewport",() -> {
            boolean[] ready={false}; instrument().runOnMainSync(() -> {
                ready[0]=touchBounds(target,bounds);
            }); return ready[0];
        });
        pointer(bounds);
    }
    private void pointer(Rect bounds) throws Exception {
        long now=SystemClock.uptimeMillis(); MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY(),0),up=MotionEvent.obtain(now,now+80,MotionEvent.ACTION_UP,bounds.exactCenterX(),bounds.exactCenterY(),0);
        try { instrument().sendPointerSync(down); instrument().sendPointerSync(up); } finally { down.recycle(); up.recycle(); } instrument().waitForIdleSync();
    }
    protected void section(String label,String kind,String title) throws Exception {
        tap(home,label); waitFor("Section did not load: "+label,() -> { try { return !((Boolean)field(home,"busy")) && kind.equals(field(home,"section")) && find(root(home),title)!=null; } catch(Exception error) { return false; } });
    }
    protected void screenshot(Activity activity,String name) {
        screenshot(root(activity),name);
    }
    protected void screenshot(View root,String name) {
        instrument().runOnMainSync(() -> { Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888); root.draw(new Canvas(bitmap)); try(FileOutputStream out=context().openFileOutput(name+".png",0)) { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); } catch(IOException e) { throw new AssertionError(e); } finally { bitmap.recycle(); } });
    }
    protected void playing(ExoPlayer player) throws Exception { assertNotNull(player); waitFor("Touch playback did not start",() -> { boolean[] ready={false}; instrument().runOnMainSync(() -> ready[0]=player.isPlaying() && player.getAudioFormat()!=null); return ready[0]; }); }
    protected Activity opens(String className,Throwing action) throws Exception { Instrumentation.ActivityMonitor monitor=instrument().addMonitor(className,null,false); try { action.run(); Activity opened=monitor.waitForActivityWithTimeout(15000); assertNotNull("Touch did not open "+className,opened); child=opened; waitFor("Child has no focus",opened::hasWindowFocus); return opened; } finally { instrument().removeMonitor(monitor); } }
    protected interface Throwing { void run() throws Exception; }
    @Test public void phoneLaunchesTheOriginalFireStickHomeAndTouchSwitchesAllSections() throws Exception {
        assertTrue("Native audio decoders missing",androidx.media3.decoder.ffmpeg.FfmpegLibrary.isAvailable());
        assertEquals(Configuration.ORIENTATION_LANDSCAPE,home.getResources().getConfiguration().orientation); assertNull("Old phone home must not be the launcher",find(root(home),"Co oglądamy?"));
        waitFor("Fire Stick home tiles missing",() -> find(root(home),"Kanały i program TV")!=null); screenshot(home,"android-firestick-home");
        section("Filmy","movie",movie.name()); screenshot(home,"android-firestick-movies"); section("Seriale","series",series.name()); screenshot(home,"android-firestick-series"); section("TV","tv",a.name()); tap(home,"Główna"); waitFor("Home did not return",() -> find(root(home),"Kanały i program TV")!=null);
    }
    @Test public void filmUsesTheFireStickDetailPageAndTouchStartsPlayback() throws Exception {
        section("Filmy","movie",movie.name()); opens(MovieActivity.class.getName(),() -> tap(home,movie.name()));
        assertTrue(child.getIntent().getBooleanExtra("television",false)); waitFor("TV film layout missing",() -> { try { return field(child,"televisionCopy")!=null; } catch(Exception e) { return false; } }); screenshot(child,"android-firestick-film");
        View play=(View)field(child,"play"); opens(PlayerActivity.class.getName(),() -> tap(play)); waitFor("Player missing",() -> { try { return field(child,"player")!=null; } catch(Exception e) { return false; } }); playing((ExoPlayer)field(child,"player"));
    }
    @Test public void serialUsesTheFireStickDetailPageAndTouchStartsAnEpisode() throws Exception {
        section("Seriale","series",series.name()); opens(SeriesActivity.class.getName(),() -> tap(home,series.name()));
        assertTrue(child.getIntent().getBooleanExtra("television",false)); waitFor("Episodes did not load",() -> find(root(child),"Odcinki")!=null); screenshot(child,"android-firestick-serial");
        View play=(View)field(child,"play"); opens(PlayerActivity.class.getName(),() -> tap(play)); waitFor("Episode player missing",() -> { try { return field(child,"player")!=null; } catch(Exception e) { return false; } }); playing((ExoPlayer)field(child,"player"));
    }
    @Test public void touchOpensLivePreviewFullScreenAndSwitchesChannel() throws Exception {
        section("TV","tv",a.name()); waitFor("Live preview missing",() -> { try { Object preview=field(home,"livePreview"); return preview!=null && field(preview,"player")!=null; } catch(Exception e) { return false; } }); ExoPlayer preview=(ExoPlayer)field(field(home,"livePreview"),"player"); playing(preview); screenshot(home,"android-firestick-tv");
        // The original TV layout opens full screen by selecting the current channel again.
        opens(PlayerActivity.class.getName(),() -> tap(find(root(home),a.name()))); waitFor("Full screen player missing",() -> { try { return field(child,"player")!=null; } catch(Exception e) { return false; } }); assertSame(preview,field(child,"player"));
        tap(child,"Następny kanał"); waitFor("Touch did not switch channel",() -> b.url().equals(child.getIntent().getStringExtra("url"))); playing((ExoPlayer)field(child,"player"));
    }
    @After public void cleanup() throws Exception { instrument().runOnMainSync(() -> { for(Activity activity:new ArrayList<>(androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED))) activity.finish(); }); close(child); close(home); if(server!=null) server.shutdown(); }
}
