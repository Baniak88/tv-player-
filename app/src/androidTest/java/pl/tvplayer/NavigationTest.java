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

    private boolean eventuallyFullscreen(PlayerActivity player) throws Exception {
        long deadline=System.currentTimeMillis()+3000;
        while(System.currentTimeMillis()<deadline) {
            getInstrumentation().waitForIdleSync();
            if((player.getWindow().getDecorView().getSystemUiVisibility() & View.SYSTEM_UI_FLAG_FULLSCREEN)!=0) return true;
            Thread.sleep(100);
        }
        return false;
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
        List<Channel> ranked=Collections.singletonList(new Channel("Ranked TV","https://example.com/ranked.ts","News","","tv","",0,"UTC",3));
        store.updateSection("tv",ranked);
        assertEquals(3,store.loadSection("tv",new HashSet<>()).get(0).bouquetOrder());
        assertEquals(40000,store.loadSection("movie",new HashSet<>()).size());
        // An unrelated damaged VOD file must not prevent TV from opening.
        generation=new String(java.nio.file.Files.readAllBytes(new java.io.File(context.getFilesDir(),"catalog.current").toPath()),java.nio.charset.StandardCharsets.UTF_8);
        movie=new java.io.File(context.getFilesDir(),"catalog-"+generation+"-movie.enc");
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(movie)) { out.write(new byte[]{1,2,3}); }
        assertEquals(1,store.loadSection("tv",new HashSet<>()).size());
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
        LibraryStore stale=new LibraryStore(context); List<Channel> oldRows=stale.loadSection("tv",new HashSet<>());
        Channel fresh=new Channel("Fresh","https://example.com/fresh.ts","New","","tv","",0,"UTC",5);
        store.save(Collections.singletonList(fresh),Collections.singleton(fresh.url()));
        stale.updateSection("tv",oldRows);
        assertEquals("Fresh",store.loadSection("tv",new HashSet<>()).get(0).name());
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
    private View description(View root,String text) {
        if(text.contentEquals(root.getContentDescription()==null?"":root.getContentDescription())) return root;
        if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) { View found=description(((ViewGroup)root).getChildAt(i),text); if(found!=null) return found; }
        return null;
    }
    private android.widget.GridView grid(View root) {
        if(root instanceof android.widget.GridView) return (android.widget.GridView)root;
        if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) { android.widget.GridView found=grid(((ViewGroup)root).getChildAt(i)); if(found!=null) return found; }
        return null;
    }
    /** Taps a sort option and waits until the app saved it; repeats the tap if the dialog was not ready yet. */
    private void chooseSort(Context context,String label,String mode) throws Exception {
        android.content.SharedPreferences prefs=context.getSharedPreferences("catalog-ui",Context.MODE_PRIVATE);
        for(int attempt=0;attempt<3;attempt++) {
            clickDialog(label);
            long deadline=System.currentTimeMillis()+2000;
            while(System.currentTimeMillis()<deadline) {
                getInstrumentation().waitForIdleSync();
                if(mode.equals(prefs.getString("sort-tv",""))) { Thread.sleep(200); getInstrumentation().waitForIdleSync(); return; }
                Thread.sleep(100);
            }
        }
        fail("Sort option was not applied: "+label);
    }
    private void clickDialog(String text) throws Exception {
        java.util.List<android.view.accessibility.AccessibilityNodeInfo> nodes=new ArrayList<>();
        long deadline=System.currentTimeMillis()+4000;
        while(nodes.isEmpty() && System.currentTimeMillis()<deadline) {
            Thread.sleep(100);
            android.view.accessibility.AccessibilityNodeInfo root=getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if(root!=null) nodes=root.findAccessibilityNodeInfosByText(text);
        }
        assertFalse(nodes.isEmpty()); android.graphics.Rect bounds=new android.graphics.Rect(); nodes.get(0).getBoundsInScreen(bounds);
        long now=android.os.SystemClock.uptimeMillis();
        getInstrumentation().sendPointerSync(android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0));
        getInstrumentation().sendPointerSync(android.view.MotionEvent.obtain(now,now+30,android.view.MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0));
        getInstrumentation().waitForIdleSync();
    }
    @Test public void testHomeWithoutBouquetsAndProviderSorting() throws Exception {
        Context context=getInstrumentation().getTargetContext();
        context.getSharedPreferences("catalog-ui",Context.MODE_PRIVATE).edit().clear().commit();
        new LibraryStore(context).save(Arrays.asList(
            new Channel("Sport 1","https://example.com/sport.ts","Sport","","tv","",0,"UTC",0),
            new Channel("Sport 2","https://example.com/sport2.ts","Sport","","tv","",0,"UTC",0),
            new Channel("Kids 1","https://example.com/kids.ts","Dzieci","","tv","",0,"UTC",1)),Collections.emptySet());
        HomeActivity home=(HomeActivity)getInstrumentation().startActivitySync(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        View root=home.getWindow().getDecorView(); waitForText(root,"Telewizja na żywo");
        assertNotNull(find(root,"Filmy")); assertNotNull(find(root,"Seriale")); assertNotNull(find(root,"Konto")); assertNotNull(find(root,"Zmień źródło"));
        assertNull(find(root,"Sport")); assertNull(find(root,"Dzieci")); assertNull(find(root,"BUKIETY TV"));
        screenshot("home-portrait.png");
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
        getInstrumentation().runOnMainSync(() -> ((View)find(root,"Telewizja na żywo").getParent()).performClick());
        MainActivity catalog=(MainActivity)monitor.waitForActivityWithTimeout(5000); assertNotNull(catalog);
        View catalogRoot=catalog.getWindow().getDecorView(); waitForText(catalogRoot,"Sport");
        assertEquals("Sport",grid(catalogRoot).getAdapter().getItem(1)); assertEquals("Dzieci",grid(catalogRoot).getAdapter().getItem(2));
        getInstrumentation().runOnMainSync(() -> description(catalogRoot,"Sortuj bukiety").performClick());
        chooseSort(context,"Nazwa A–Z","az");
        assertEquals("Dzieci",grid(catalogRoot).getAdapter().getItem(1));
        assertEquals("az",context.getSharedPreferences("catalog-ui",Context.MODE_PRIVATE).getString("sort-tv",""));
        getInstrumentation().runOnMainSync(() -> description(catalogRoot,"Sortuj bukiety").performClick());
        chooseSort(context,"Kolejność dostawcy","provider");
        assertEquals("Sport",grid(catalogRoot).getAdapter().getItem(1));
        screenshot("bouquets-grid.png");
        getInstrumentation().runOnMainSync(() -> clickGridText(catalogRoot,"Sport"));
        waitForText(catalogRoot,"Sport 1"); assertNull(find(catalogRoot,"Kids 1")); screenshot("sport-bouquet.png");
        getInstrumentation().removeMonitor(monitor);
        getInstrumentation().runOnMainSync(catalog::finish); getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(() -> home.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        Thread.sleep(1000); getInstrumentation().waitForIdleSync(); screenshot("home-landscape.png");
        getInstrumentation().runOnMainSync(home::finish);
    }
    private void shell(String command) throws Exception {
        try(android.os.ParcelFileDescriptor.AutoCloseInputStream stream=new android.os.ParcelFileDescriptor.AutoCloseInputStream(getInstrumentation().getUiAutomation().executeShellCommand(command))) {
            byte[] buffer=new byte[1024]; while(stream.read(buffer)!=-1) {}
        }
    }
    @Test public void testFullscreenWithSystemRotationLocked() throws Exception {
        Context context=getInstrumentation().getTargetContext();
        int original=android.provider.Settings.System.getInt(context.getContentResolver(),android.provider.Settings.System.ACCELEROMETER_ROTATION,1);
        PlayerActivity player=null;
        java.io.File ready=new java.io.File(context.getFilesDir(),"rotation-ready"),landscape=new java.io.File(context.getFilesDir(),"rotation-landscape-ok");
        try {
            shell("settings put system accelerometer_rotation 0");
            player=(PlayerActivity)getInstrumentation().startActivitySync(new Intent(context,PlayerActivity.class).putExtra("url","https://example.com/test.mp4").putExtra("name","Rotation lock test").putExtra("kind","movie").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            getInstrumentation().waitForIdleSync(); Thread.sleep(500); ready.createNewFile();
            long deadline=System.currentTimeMillis()+20000;
            while(player.getResources().getConfiguration().orientation!=android.content.res.Configuration.ORIENTATION_LANDSCAPE && System.currentTimeMillis()<deadline) Thread.sleep(100);
            assertEquals("Physical rotation did not reach landscape",android.content.res.Configuration.ORIENTATION_LANDSCAPE,player.getResources().getConfiguration().orientation);
            int requested=player.getRequestedOrientation();
            assertTrue(requested==ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || requested==ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
            assertEquals(0,android.provider.Settings.System.getInt(context.getContentResolver(),android.provider.Settings.System.ACCELEROMETER_ROTATION,1));
            landscape.createNewFile(); deadline=System.currentTimeMillis()+20000;
            while(player.getResources().getConfiguration().orientation!=android.content.res.Configuration.ORIENTATION_PORTRAIT && System.currentTimeMillis()<deadline) Thread.sleep(100);
            assertEquals("Physical rotation did not return to portrait",android.content.res.Configuration.ORIENTATION_PORTRAIT,player.getResources().getConfiguration().orientation);
            assertEquals(0,android.provider.Settings.System.getInt(context.getContentResolver(),android.provider.Settings.System.ACCELEROMETER_ROTATION,1));
            android.util.Log.i("RotationTest","Landscape and portrait passed with accelerometer_rotation=0");
        } finally {
            ready.delete(); landscape.delete(); shell("settings put system accelerometer_rotation "+original);
            final PlayerActivity closing=player; if(closing!=null) getInstrumentation().runOnMainSync(closing::finish);
        }
    }
    @Test public void testXmltvFallbackAndInlineGuide() throws Exception {
        Context context=getInstrumentation().getTargetContext();
        okhttp3.mockwebserver.MockWebServer server=new okhttp3.mockwebserver.MockWebServer();
        long now=System.currentTimeMillis()/1000;
        java.time.format.DateTimeFormatter stamp=java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z").withZone(java.time.ZoneOffset.UTC);
        String xml="<tv><channel id=\"tvn.pl\"><display-name>TVN</display-name></channel><channel id=\"tvn7.pl\"><display-name>TVN 7</display-name></channel>";
        for(String id:new String[]{"tvn.pl","tvn7.pl"}) {
            xml+="<programme channel=\""+id+"\" start=\""+stamp.format(java.time.Instant.ofEpochSecond(now-900))+"\" stop=\""+stamp.format(java.time.Instant.ofEpochSecond(now+900))+"\"><title>Fakty testowe</title><desc>Opis programu testowego.</desc></programme>";
            xml+="<programme channel=\""+id+"\" start=\""+stamp.format(java.time.Instant.ofEpochSecond(now+900))+"\" stop=\""+stamp.format(java.time.Instant.ofEpochSecond(now+3600))+"\"><title>Pogoda testowa</title></programme>";
        }
        xml+="</tv>";
        java.io.ByteArrayOutputStream compressed=new java.io.ByteArrayOutputStream();
        try(java.util.zip.GZIPOutputStream gzip=new java.util.zip.GZIPOutputStream(compressed)) { gzip.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        final byte[] payload=compressed.toByteArray();
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            public okhttp3.mockwebserver.MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request) {
                return new okhttp3.mockwebserver.MockResponse().setHeader("Content-Type","application/gzip").setBody(new okio.Buffer().write(payload));
            }
        });
        server.start(); MainActivity activity=null;
        try {
            String feed="xmltv:"+server.url("/epg.xml.gz");
            List<Channel> channels=Arrays.asList(
                new Channel("PL-VIP| TVN RAW",server.url("/stream1.ts").toString(),"Test EPG","tvn.pl","tv",feed,0,"UTC",0),
                new Channel("PL-VIP| TVN 7 RAW",server.url("/stream2.ts").toString(),"Test EPG","tvn7.pl","tv",feed,0,"UTC",0));
            new LibraryStore(context).save(channels,Collections.emptySet());
            activity=(MainActivity)getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).putExtra("section","tv").putExtra("bouquet","Test EPG").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            View root=activity.getWindow().getDecorView();
            long deadline=System.currentTimeMillis()+12000;
            while(System.currentTimeMillis()<deadline && findContaining(root,"Fakty testowe")==null) { Thread.sleep(100); getInstrumentation().waitForIdleSync(); }
            assertNotNull("Current programme missing from channel row",findContaining(root,"Fakty testowe"));
            assertNotNull("Next programme missing from channel row",findContaining(root,"Pogoda testowa"));
            screenshot("epg-channel-list.png");
            GuideRepository.Data second=GuideRepository.get(context).peek(channels.get(1),false);
            if(second==null) { Thread.sleep(500); second=GuideRepository.get(context).peek(channels.get(1),false); }
            assertNotNull(second); assertFalse(second.programs().isEmpty()); assertEquals(1,server.getRequestCount());
            final MainActivity opened=activity;
            getInstrumentation().runOnMainSync(() -> GuideDialog.open(opened,channels.get(0),Runnable::run,c -> {}));
            Thread.sleep(700); getInstrumentation().waitForIdleSync();
            android.view.accessibility.AccessibilityNodeInfo window=getInstrumentation().getUiAutomation().getRootInActiveWindow();
            assertFalse(window.findAccessibilityNodeInfosByText("Fakty testowe").isEmpty());
            screenshot("epg-program-dialog.png"); assertEquals(1,server.getRequestCount());
            getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        } finally { if(activity!=null) { final MainActivity closing=activity; getInstrumentation().runOnMainSync(closing::finish); } server.shutdown(); }
    }
    private View findContaining(View root,String text) {
        if(root instanceof TextView && ((TextView)root).getText().toString().contains(text)) return root;
        if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) { View found=findContaining(((ViewGroup)root).getChildAt(i),text); if(found!=null) return found; }
        return null;
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
        assertTrue((player.getWindow().getDecorView().getSystemUiVisibility() & View.SYSTEM_UI_FLAG_FULLSCREEN)!=0);
        getInstrumentation().runOnMainSync(() -> find(player.getWindow().getDecorView(),"↙").performClick());
        getInstrumentation().waitForIdleSync(); assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,player.getRequestedOrientation());
        getInstrumentation().runOnMainSync(() -> find(player.getWindow().getDecorView(),"⤢").performClick());
        getInstrumentation().waitForIdleSync(); assertTrue(eventuallyFullscreen(player));
        getInstrumentation().runOnMainSync(() -> find(player.getWindow().getDecorView(),"Opcje").performClick());
        getInstrumentation().waitForIdleSync();
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        getInstrumentation().runOnMainSync(() -> { player.finish(); main.finish(); });
    }
}
