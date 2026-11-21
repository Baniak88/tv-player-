package pl.tvplayer;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;

@RunWith(AndroidJUnit4.class)
public class PerformanceTest {
    private android.app.Instrumentation instrument() { return InstrumentationRegistry.getInstrumentation(); }
    private Context context() { return instrument().getTargetContext(); }
    private byte[] image(int color) { Bitmap bitmap=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888); bitmap.eraseColor(color); ByteArrayOutputStream bytes=new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes); bitmap.recycle(); return bytes.toByteArray(); }
    @Test public void artworkRequestsAreCoalescedAndRecycledViewsKeepTheLatestImage() throws Exception {
        MockWebServer server=new MockWebServer(); byte[] red=image(Color.RED),blue=image(Color.BLUE);
        server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) { boolean slow=request.getPath().contains("slow"); return new MockResponse().setHeader("Content-Type","image/png").setHeader("Cache-Control","no-store").setBody(new okio.Buffer().write(request.getPath().contains("blue")?blue:red)).setBodyDelay(slow?1500:200,java.util.concurrent.TimeUnit.MILLISECONDS); } }); server.start();
        try {
            List<ImageView> views=new ArrayList<>(); String url=server.url("/shared-red.png").toString();
            instrument().runOnMainSync(() -> { for(int i=0;i<24;i++) { ImageView view=new ImageView(context()); views.add(view); ImageLoader.get(context()).load(url,view,64,64); } });
            long until=System.currentTimeMillis()+7000; boolean complete=false; while(System.currentTimeMillis()<until) { instrument().waitForIdleSync(); complete=true; for(ImageView view:views) if(view.getDrawable()==null) complete=false; if(complete) break; Thread.sleep(80); }
            assertTrue("Artwork did not finish",complete); assertEquals("Duplicate network fetches for identical artwork",1,server.getRequestCount());
            instrument().runOnMainSync(() -> { for(ImageView view:views) ImageLoader.get(context()).load(url,view,64,64); }); assertEquals(1,server.getRequestCount());
            ImageView recycled=views.get(0); instrument().runOnMainSync(() -> ImageLoader.get(context()).load(server.url("/slow-red.png").toString(),recycled,64,64)); Thread.sleep(100);
            instrument().runOnMainSync(() -> ImageLoader.get(context()).load(server.url("/fast-blue.png").toString(),recycled,64,64)); until=System.currentTimeMillis()+7000;
            while(recycled.getDrawable()==null && System.currentTimeMillis()<until) { Thread.sleep(80); instrument().waitForIdleSync(); }
            assertNotNull(recycled.getDrawable()); Thread.sleep(1700); instrument().waitForIdleSync(); int pixel=((BitmapDrawable)recycled.getDrawable()).getBitmap().getPixel(0,0); assertTrue("Stale image replaced the recycled view",Color.blue(pixel)>180 && Color.red(pixel)<40);
            android.util.Log.i("LibraryBenchmark","Artwork: 24 visible bindings = 1 HTTP request");
        } finally { server.shutdown(); }
    }
    @Test public void metadataCacheIsEncryptedAndReopensMovieWithoutNetwork() throws Exception {
        String secret="private-token-for-metadata-test",key="movie:https://example.com/movie/user/password/731.mp4?"+secret;
        MovieDetails original=new MovieDetails("Film testowy","Opis","https://example.com/cover.png?"+secret,"","2026","8","Dramat","Reżyser","Obsada",7200);
        MetadataCache cache=new MetadataCache(context()); cache.write(key,original); MetadataCache.Hit<MovieDetails> hit=cache.read(key,MovieDetails.class); assertNotNull(hit); assertTrue(hit.fresh()); assertEquals(original,hit.value());
        File folder=new File(context().getCacheDir(),"details-v1"); File[] files=folder.listFiles((dir,name) -> name.endsWith(".enc")); assertNotNull(files);
        for(File file:files) { assertFalse(file.getName().contains(secret)); ByteArrayOutputStream bytes=new ByteArrayOutputStream(); try(InputStream input=new FileInputStream(file)) { byte[] data=new byte[8192]; int n; while((n=input.read(data))!=-1) bytes.write(data,0,n); } assertFalse(new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.ISO_8859_1).contains(secret)); }
        MockWebServer server=new MockWebServer(); server.enqueue(new MockResponse().setHeader("Content-Type","application/json").setBody("{\"info\":{\"name\":\"Film z pamięci\",\"duration_secs\":3600}}")); server.start();
        Channel source=new Channel("Film",server.url("/movie/user/pass/731.mp4").toString(),"Kino","","movie"); MovieRepository first=new MovieRepository(context()); MovieDetails loaded=first.load(source); assertEquals(1,server.getRequestCount()); server.shutdown();
        java.lang.reflect.Field field=MovieRepository.class.getDeclaredField("CACHE"); field.setAccessible(true); Map<?,?> memory=(Map<?,?>)field.get(null); synchronized(memory) { memory.clear(); }
        long start=android.os.SystemClock.elapsedRealtime(); MovieDetails reopened=new MovieRepository(context()).load(source); long elapsed=android.os.SystemClock.elapsedRealtime()-start; assertEquals(loaded,reopened); assertTrue("Cached metadata took "+elapsed+" ms",elapsed<2000); android.util.Log.i("LibraryBenchmark","Cached movie metadata ms="+elapsed);
    }
}
