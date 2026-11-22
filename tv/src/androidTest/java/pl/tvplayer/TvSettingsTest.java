package pl.tvplayer;

import android.app.*;
import android.content.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.media3.common.TrackSelectionParameters;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;
import static pl.tvplayer.TvTestUi.*;

@RunWith(AndroidJUnit4.class)
public class TvSettingsTest {
    private TvActivity home;
    @Before public void setup() throws Exception {
        systemReady();
        new android.util.AtomicFile(new java.io.File(context().getFilesDir(),"tv-account.enc")).delete();
        new LibraryStore(context()).save(Arrays.asList(new Channel("Alfa","https://example.com/a.ts","Sport","","tv"),new Channel("Beta","https://example.com/b.ts","Dzieci","","tv")),Collections.singleton("https://example.com/b.ts"));
        home=(TvActivity)instrument().startActivitySync(new Intent(context(),TvActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitFor("Home missing",() -> find(home.getWindow().getDecorView(),"Kanały i program TV")!=null);
        waitFor("Home has no input focus",home::hasWindowFocus);
    }
    @After public void cleanup() { close(home); context().getSharedPreferences("playback-network",0).edit().clear().commit(); context().getSharedPreferences("epg-settings",0).edit().clear().commit(); }
    @Test public void bufferModesPersistAndApplyVideoLimits() throws Exception {
        invoke(home,"settings"); click("Połączenie internetowe (bufor)"); click("Słabszy internet · większy bufor");
        assertTrue(PlaybackSettings.weakInternet(context()));
        TrackSelectionParameters weak=PlaybackSettings.tracks(context(),TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT);
        assertEquals(1280,weak.maxVideoWidth); assertEquals(720,weak.maxVideoHeight); assertEquals(2500000,weak.maxVideoBitrate);
        close(home); home=(TvActivity)instrument().startActivitySync(new Intent(context(),TvActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        assertTrue(PlaybackSettings.weakInternet(context())); invoke(home,"settings"); click("Połączenie internetowe (bufor)"); click("Standard · szybszy start");
        assertFalse(PlaybackSettings.weakInternet(context())); assertEquals(Integer.MAX_VALUE,PlaybackSettings.tracks(context(),weak).maxVideoBitrate);
    }
    private void text(String value) throws Exception {
        // The dialog opens asynchronously; wait for its field instead of reading the active window once.
        AccessibilityNodeInfo input=null; long until=android.os.SystemClock.elapsedRealtime()+5000;
        while(input==null && android.os.SystemClock.elapsedRealtime()<until) { input=findInput(instrument().getUiAutomation().getRootInActiveWindow()); if(input==null) Thread.sleep(100); }
        assertNotNull("No XMLTV input",input);
        android.os.Bundle args=new android.os.Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value); assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)); instrument().waitForIdleSync();
    }
    private AccessibilityNodeInfo findInput(AccessibilityNodeInfo node) { if(node==null) return null; if("android.widget.EditText".contentEquals(node.getClassName())) return node; for(int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo hit=findInput(node.getChild(i)); if(hit!=null) return hit; } return null; }
    @Test public void xmltvRejectsInvalidCredentialsAndSupportsPresetCancelSaveRemove() throws Exception {
        context().getSharedPreferences("epg-settings",0).edit().clear().commit();
        invoke(home,"settings"); click("Program TV (EPG): własny link XMLTV"); text("https://user:secret@example.com/guide.xml"); click("Zapisz");
        assertNull(EpgSettings.custom(context())); assertNotNull("Validation must keep dialog open",node("Zapisz"));
        click("Wstaw polskie EPG (epg.ovh)"); click("Anuluj"); assertNull(EpgSettings.custom(context()));
        invoke(home,"settings"); click("Program TV (EPG): własny link XMLTV"); click("Wstaw polskie EPG (epg.ovh)"); click("Zapisz"); assertEquals(EpgSettings.PUBLIC_PL,EpgSettings.custom(context()).toString());
        invoke(home,"settings"); click("Program TV (EPG): własny link XMLTV"); click("Usuń"); assertNull(EpgSettings.custom(context()));
    }
    @Test public void searchFavoritesAndAllBouquetOrdersWork() throws Exception {
        key(KeyEvent.KEYCODE_DPAD_CENTER); waitFor("TV catalog missing",() -> find(home.getWindow().getDecorView(),"Sport")!=null);
        for(int i=0;i<BouquetOrder.LABELS.length;i++) { click("Opcje"); click("Sortuj"); click(BouquetOrder.LABELS[i]); assertEquals(BouquetOrder.MODES[i],home.getPreferences(0).getString("sort-tv","")); }
        click("Szukaj"); text("Beta"); click("Szukaj");
        waitFor("Search result missing",() -> find(home.getWindow().getDecorView(),"Beta")!=null); assertNull(find(home.getWindow().getDecorView(),"Alfa"));
        click("Opcje"); click("☆ Ulubione"); assertNotNull(find(home.getWindow().getDecorView(),"Beta"));
        click("Szukaj"); text("Alfa"); click("Szukaj"); waitFor("Favorite filter did not hide nonfavorite",() -> find(home.getWindow().getDecorView(),"Alfa")==null);
    }
    @Test public void exitConfirmationCanCancelAndFinish() throws Exception {
        key(KeyEvent.KEYCODE_BACK); click("Zostań"); assertFalse(home.isFinishing());
        key(KeyEvent.KEYCODE_BACK); click("Wyjście"); waitFor("Exit did not finish activity",home::isFinishing);
    }
}
