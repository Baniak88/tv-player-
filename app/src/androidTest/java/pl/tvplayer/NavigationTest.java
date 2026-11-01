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

    private void waitForText(View root,String text) throws Exception {
        long deadline=System.currentTimeMillis()+5000;
        while(System.currentTimeMillis()<deadline) {
            getInstrumentation().waitForIdleSync(); if(find(root,text)!=null) return; Thread.sleep(50);
        }
        fail("Missing UI text: "+text);
    }
    @Test public void testLargeCatalogLoadsOnlyRequestedSection() throws Exception {
        Context context=getInstrumentation().getTargetContext(); LibraryStore store=new LibraryStore(context);
        List<Channel> items=new ArrayList<>();
        for(int i=0;i<1000;i++) items.add(new Channel("TV "+i,"https://example.com/live/"+i+".ts","News","","tv"));
        for(int i=0;i<40000;i++) items.add(new Channel("Film "+i,"https://example.com/movie/"+i+".mkv","Kino","","movie"));
        for(int i=0;i<10000;i++) items.add(new Channel("Serial "+i,"https://example.com/series/"+i,"Seriale","","series"));
        store.save(items,Collections.emptySet());
        Set<String> favorites=new HashSet<>();
        long start=android.os.SystemClock.elapsedRealtime();
        List<Channel> tv=store.loadSection("tv",favorites);
        long elapsed=android.os.SystemClock.elapsedRealtime()-start;
        android.util.Log.i("LibraryBenchmark","TV=1000; movies=40000; series=10000; TV load ms="+elapsed);
        assertEquals(1000,tv.size()); assertTrue("Cached TV took "+elapsed+" ms",elapsed<5000);
        String generation=new String(java.nio.file.Files.readAllBytes(new java.io.File(context.getFilesDir(),"catalog.current").toPath()),java.nio.charset.StandardCharsets.UTF_8);
        java.io.File movie=new java.io.File(context.getFilesDir(),"catalog-"+generation+"-movie.enc");
        byte[] before=java.nio.file.Files.readAllBytes(movie.toPath());
        store.saveFavorites(Collections.singleton(tv.get(0).url()));
        assertTrue(Arrays.equals(before,java.nio.file.Files.readAllBytes(movie.toPath())));
        assertEquals(40000,store.loadSection("movie",new HashSet<>()).size());
        // An unrelated damaged VOD file must not prevent TV from opening.
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(movie)) { out.write(new byte[]{1,2,3}); }
        assertEquals(1000,store.loadSection("tv",new HashSet<>()).size());
        boolean rejected=false; try { store.loadSection("movie",new HashSet<>()); } catch(Exception expected) { rejected=true; }
        assertTrue(rejected);
        store.save(Collections.emptyList(),Collections.emptySet());
    }
    @Test public void testLegacyCacheMigratesWithFavorites() throws Exception {
        Context context=getInstrumentation().getTargetContext(); LibraryStore store=new LibraryStore(context);
        store.save(Collections.emptyList(),Collections.emptySet());
        java.security.KeyStore keys=java.security.KeyStore.getInstance("AndroidKeyStore"); keys.load(null);
        javax.crypto.Cipher cipher=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,(javax.crypto.SecretKey)keys.getKey("tv-library",null));
        String json="{\"channels\":[{\"name\":\"Legacy\",\"url\":\"https://example.com/live.ts\",\"kind\":\"tv\"}],\"favorites\":[\"https://example.com/live.ts\"]}";
        byte[] encoded=cipher.doFinal(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(context.getFilesDir(),"library.enc"))) { out.write(cipher.getIV()); out.write(encoded); }
        new android.util.AtomicFile(new java.io.File(context.getFilesDir(),"catalog.current")).delete();
        Set<String> favorites=new HashSet<>();
        assertEquals("Legacy",store.loadSection("tv",favorites).get(0).name());
        assertTrue(favorites.contains("https://example.com/live.ts"));
        assertTrue(new java.io.File(context.getFilesDir(),"catalog.current").exists());
        assertFalse(new java.io.File(context.getFilesDir(),"library.enc").exists());
        assertEquals("Legacy",store.loadSection("tv",new HashSet<>()).get(0).name());
        store.save(Collections.emptyList(),Collections.emptySet());
    }
    private void clickGridText(View root,String text) {
        View target=find(root,text); assertNotNull(target);
        View child=target;
        while(child.getParent() instanceof View && !(child.getParent() instanceof android.widget.AdapterView)) child=(View)child.getParent();
        android.widget.AdapterView<?> parent=(android.widget.AdapterView<?>)child.getParent();
        parent.performItemClick(child,parent.getPositionForView(child),0);
    }
    private void screenshot(String name) throws Exception {
        Thread.sleep(800); getInstrumentation().waitForIdleSync();
        android.graphics.Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getInstrumentation().getTargetContext().getFilesDir(),name))) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
        }
        bitmap.recycle();
    }
    @Test public void testHomeDashboardAndVisibleBouquets() throws Exception {
        Context context=getInstrumentation().getTargetContext();
        new LibraryStore(context).save(Arrays.asList(
            new Channel("Sport 1","https://example.com/sport.ts","Sport","","tv"),
            new Channel("Sport 2","https://example.com/sport2.ts","Sport","","tv"),
            new Channel("Kids 1","https://example.com/kids.ts","Dzieci","","tv")),Collections.emptySet());
        HomeActivity home=(HomeActivity)getInstrumentation().startActivitySync(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        View root=home.getWindow().getDecorView(); waitForText(root,"Sport");
        assertNotNull(find(root,"Telewizja na żywo")); assertNotNull(find(root,"Filmy")); assertNotNull(find(root,"Seriale"));
        assertNotNull(find(root,"Konto")); assertNotNull(find(root,"Zmień źródło")); assertNotNull(find(root,"Ustawienia"));
        assertNotNull(find(root,"Odśwież widok")); assertNotNull(find(root,"Wyjście")); assertNotNull(find(root,"Dzieci"));
        screenshot("home-portrait.png");
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
        getInstrumentation().runOnMainSync(() -> ((View)find(root,"Sport").getParent()).performClick());
        MainActivity catalog=(MainActivity)monitor.waitForActivityWithTimeout(5000); assertNotNull(catalog);
        waitForText(catalog.getWindow().getDecorView(),"Sport 1");
        assertNull(find(catalog.getWindow().getDecorView(),"Kids 1")); screenshot("sport-bouquet.png");
        getInstrumentation().removeMonitor(monitor);
        getInstrumentation().runOnMainSync(catalog::finish);
        getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(() -> home.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        Thread.sleep(1000); getInstrumentation().waitForIdleSync(); screenshot("home-landscape.png");
        getInstrumentation().runOnMainSync(home::finish);
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
            new Channel("Test TV","https://example.com/live.ts","Sport","","tv"),
            new Channel("Test Kids","https://example.com/kids.ts","Dzieci","","tv"),
            new Channel("Test Film","https://example.com/movie.mp4","Kino","","movie"),
            new Channel("Test Serial","https://example.com/player_api.php","Dramat","","series")),Collections.emptySet());
        MainActivity main=(MainActivity)getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Thread.sleep(1500); getInstrumentation().waitForIdleSync();
        View root=main.getWindow().getDecorView();
        waitForText(root,"Sport"); assertNotNull(find(root,"Dzieci"));
        getInstrumentation().runOnMainSync(() -> clickGridText(root,"Sport"));
        getInstrumentation().waitForIdleSync(); assertNotNull(find(root,"Test TV")); assertNull(find(root,"Test Kids"));
        assertNotNull(find(root,"EPG")); assertNotNull(find(root,"TV")); assertNotNull(find(root,"Seriale")); assertNotNull(find(root,"Filmy"));
        getInstrumentation().runOnMainSync(() -> find(root,"Filmy").performClick());
        getInstrumentation().waitForIdleSync(); waitForText(root,"Kino");
        getInstrumentation().runOnMainSync(() -> clickGridText(root,"Kino"));
        waitForText(root,"Test Film"); assertNotNull(find(root,"Test Film")); assertNull(find(root,"Test TV"));
        getInstrumentation().runOnMainSync(() -> find(root,"Seriale").performClick());
        getInstrumentation().waitForIdleSync(); waitForText(root,"Dramat");
        getInstrumentation().runOnMainSync(() -> clickGridText(root,"Dramat"));
        waitForText(root,"Test Serial"); assertNotNull(find(root,"Test Serial")); assertNull(find(root,"Test Film"));
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
