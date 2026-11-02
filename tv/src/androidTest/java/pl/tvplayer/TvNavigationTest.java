package pl.tvplayer;
import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.util.*;

@RunWith(AndroidJUnit4.class)
public class TvNavigationTest {
    private Instrumentation instrument() { return InstrumentationRegistry.getInstrumentation(); }
    private Context context() { return instrument().getTargetContext(); }
    private View find(View view,String text) {
        if(view instanceof TextView && text.equals(((TextView)view).getText().toString())) return view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) { View hit=find(((ViewGroup)view).getChildAt(i),text); if(hit!=null) return hit; } return null;
    }
    private void waitText(View root,String text) throws Exception { long until=System.currentTimeMillis()+7000; while(find(root,text)==null && System.currentTimeMillis()<until) { Thread.sleep(100); instrument().waitForIdleSync(); } assertNotNull(text,find(root,text)); }
    private TvActivity start() throws Exception {
        new android.util.AtomicFile(new java.io.File(context().getFilesDir(),"tv-account.enc")).delete();
        new LibraryStore(context()).save(Arrays.asList(new Channel("Sport 1","https://example.com/sport.ts","Sport","","tv").withOrder(0),new Channel("Dzieci 1","https://example.com/kids.ts","Dzieci","","tv").withOrder(1),new Channel("Test Film","https://example.com/film.mp4","Kino","","movie")),Collections.emptySet());
        TvActivity activity=(TvActivity)instrument().startActivitySync(new Intent(context(),TvActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitText(activity.getWindow().getDecorView(),"Co oglądamy?"); long focused=System.currentTimeMillis()+5000; while(!activity.hasWindowFocus() && System.currentTimeMillis()<focused) Thread.sleep(100); instrument().waitForIdleSync(); assertTrue("TV activity has no input focus",activity.hasWindowFocus()); return activity;
    }
    private void key(int code) throws Exception { try(android.os.ParcelFileDescriptor.AutoCloseInputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument().getUiAutomation().executeShellCommand("input keyevent "+code))) { byte[] buffer=new byte[256]; while(input.read(buffer)!=-1) {} } instrument().waitForIdleSync(); Thread.sleep(200); }
    private void screenshot(TvActivity activity,String name) throws Exception { java.util.concurrent.atomic.AtomicReference<android.graphics.Bitmap> image=new java.util.concurrent.atomic.AtomicReference<>(); instrument().runOnMainSync(() -> { View root=activity.getWindow().getDecorView(); android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(root.getWidth(),root.getHeight(),android.graphics.Bitmap.Config.ARGB_8888); root.draw(new android.graphics.Canvas(bitmap)); image.set(bitmap); }); android.graphics.Bitmap bitmap=image.get(); try(java.io.FileOutputStream out=context().openFileOutput(name,Context.MODE_PRIVATE)) { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out); } bitmap.recycle(); }
    @Test public void homeAndMoviesWorkWithRemoteOnly() throws Exception {
        TvActivity activity=start();
        try {
            assertEquals("Telewizja na żywo",((TextView)activity.getCurrentFocus()).getText().toString().replace("▣\n\n", "")); screenshot(activity,"tv-home.png");
            key(KeyEvent.KEYCODE_DPAD_RIGHT); assertTrue(activity.getCurrentFocus() instanceof Button); assertEquals("▶  Filmy",((Button)activity.getCurrentFocus()).getText().toString());
            key(KeyEvent.KEYCODE_DPAD_CENTER); waitText(activity.getWindow().getDecorView(),"Test Film"); assertNull(find(activity.getWindow().getDecorView(),"Sport 1")); screenshot(activity,"tv-movies.png");
            key(KeyEvent.KEYCODE_BACK); waitText(activity.getWindow().getDecorView(),"Co oglądamy?");
        } finally { instrument().runOnMainSync(activity::finish); instrument().waitForIdleSync(); Thread.sleep(300); }
    }
    @Test public void bouquetsAndItemOptionsWorkWithRemote() throws Exception {
        TvActivity activity=start();
        try {
            key(KeyEvent.KEYCODE_DPAD_CENTER); waitText(activity.getWindow().getDecorView(),"Sport");
            key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_CENTER);
            waitText(activity.getWindow().getDecorView(),"Sport 1"); assertNull(find(activity.getWindow().getDecorView(),"Dzieci 1")); screenshot(activity,"tv-bouquet.png");
            key(KeyEvent.KEYCODE_MENU);
            android.view.accessibility.AccessibilityNodeInfo root=instrument().getUiAutomation().getRootInActiveWindow();
            assertFalse(root.findAccessibilityNodeInfosByText("Program TV i archiwum").isEmpty());
            assertFalse(root.findAccessibilityNodeInfosByText("Dodaj do ulubionych").isEmpty());
            key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_CENTER);
            Set<String> saved=new HashSet<>(); long until=System.currentTimeMillis()+5000;
            while(!saved.contains("https://example.com/sport.ts") && System.currentTimeMillis()<until) { Thread.sleep(100); new LibraryStore(context()).loadSection("tv",saved); }
            assertTrue("Favorite was not saved after remote selection",saved.contains("https://example.com/sport.ts"));
        } finally { instrument().runOnMainSync(activity::finish); instrument().waitForIdleSync(); Thread.sleep(300); }
    }
    @Test public void credentialsAreEncryptedAndReadableAfterRestart() throws Exception {
        TvAccountStore store=new TvAccountStore(context()); TvAccountStore.Account expected=new TvAccountStore.Account("xtream","https://example.com/","test-user","sensitive-password-for-TV-test"); store.save(expected); assertEquals(expected,new TvAccountStore(context()).load());
        String bytes=new String(new android.util.AtomicFile(new java.io.File(context().getFilesDir(),"tv-account.enc")).readFully(),java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(bytes.contains(expected.password())); assertFalse(bytes.contains(expected.user()));
    }
    @Test public void playerKeepsLandscapeAndHandlesRemoteMenu() throws Exception {
        PlayerActivity player=(PlayerActivity)instrument().startActivitySync(new Intent(context(),PlayerActivity.class).putExtra("television",true).putExtra("url","https://example.com/film.mp4").putExtra("name","Test Film").putExtra("kind","movie").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            instrument().waitForIdleSync(); assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,player.getRequestedOrientation());
            key(KeyEvent.KEYCODE_MENU); assertFalse(instrument().getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("Ścieżka audio").isEmpty()); key(KeyEvent.KEYCODE_BACK);
            key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); key(KeyEvent.KEYCODE_MEDIA_STOP);
        } finally { instrument().runOnMainSync(player::finish); instrument().waitForIdleSync(); Thread.sleep(300); }
    }
}
