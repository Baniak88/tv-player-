package pl.tvplayer;

import android.app.*;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static pl.tvplayer.TvTestUi.*;

/** Includes the four original touch flows and adds real display rotation and persistent arrows. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
@RunWith(AndroidJUnit4.class)
public class AndroidResponsiveTest extends AndroidFirestickTouchTest {
    private void visible(View view) {
        assertNotNull(view); Rect box=new Rect(),screen=new Rect(); assertTrue("Control is hidden",view.getGlobalVisibleRect(box)); view.getRootView().getGlobalVisibleRect(screen);
        assertTrue("Control extends past the left/right edge",box.left>=screen.left && box.right<=screen.right); assertTrue(box.width()>0 && box.height()>0);
    }
    private ExoPlayer preview() throws Exception { return (ExoPlayer)field(field(home,"livePreview"),"player"); }
    @Test public void portraitHomeAndCataloguesFitAndKeepTheSelectedSection() throws Exception {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER,home.getRequestedOrientation()); AndroidScreenTest.rotate(home,true);
        for(String label:new String[]{"Główna","TV","Filmy","Seriale","Szukaj","Opcje"}) visible(clickable(root(home),label));
        screenshot(home,"android-home-portrait"); section("Filmy","movie",movie.name()); visible((View)field(home,"androidCategories")); screenshot(home,"android-movies-portrait");
        section("Seriale","series",series.name()); screenshot(home,"android-series-portrait"); AndroidScreenTest.rotate(home,false); assertEquals("series",field(home,"section")); assertNotNull(find(root(home),series.name())); screenshot(home,"android-series-landscape");
    }
    @Test public void previewRotationKeepsTheSameConnectionAndBouquet() throws Exception {
        section("TV","tv",a.name()); waitFor("Preview missing",() -> { try { return preview()!=null; } catch(Exception e) { return false; } }); playing(preview());
        tap(find(root(home),"Test TV")); waitFor("Bouquet was not selected",() -> { try { return "Test TV".equals(field(home,"group")); } catch(Exception e) { return false; } });
        ExoPlayer player=preview(); int opened=tvRequests.get(); AndroidScreenTest.rotate(home,true); assertSame(player,preview()); playing(preview()); assertEquals("Rotation opened another TV connection",opened,tvRequests.get()); assertEquals("Test TV",field(home,"group")); screenshot(home,"android-tv-portrait");
        AndroidScreenTest.rotate(home,false); assertSame(player,preview()); assertEquals(opened,tvRequests.get()); assertEquals("Test TV",field(home,"group")); screenshot(home,"android-tv-landscape");
    }
    @Test public void channelArrowsStayVisibleAfterTimeoutAndWorkInBothOrientations() throws Exception {
        section("TV","tv",a.name()); waitFor("Preview missing",() -> { try { return preview()!=null; } catch(Exception e) { return false; } }); playing(preview());
        opens(PlayerActivity.class.getName(),() -> tap(find(root(home),a.name()))); waitFor("Player missing",() -> { try { return field(child,"player")!=null; } catch(Exception e) { return false; } }); ExoPlayer player=(ExoPlayer)field(child,"player"); int opened=tvRequests.get();
        AndroidScreenTest.rotate(child,true); assertSame(player,field(child,"player")); playing(player); assertEquals(opened,tvRequests.get()); Thread.sleep(7500); instrument().waitForIdleSync();
        visible(clickable(root(child),"Poprzedni kanał")); visible(clickable(root(child),"Następny kanał")); screenshot(child,"android-player-tv-portrait"); tap(child,"Następny kanał"); waitFor("Portrait next channel failed",() -> b.url().equals(child.getIntent().getStringExtra("url"))); playing((ExoPlayer)field(child,"player"));
        AndroidScreenTest.rotate(child,false); visible(clickable(root(child),"Poprzedni kanał")); visible(clickable(root(child),"Następny kanał")); screenshot(child,"android-player-tv-landscape"); tap(child,"Poprzedni kanał"); waitFor("Landscape previous channel failed",() -> a.url().equals(child.getIntent().getStringExtra("url"))); playing((ExoPlayer)field(child,"player"));
    }
    @Test public void filmAndSeriesDetailsFollowPortraitAndLandscapeWithoutReloading() throws Exception {
        section("Filmy","movie",movie.name()); opens(MovieActivity.class.getName(),() -> tap(home,movie.name())); Activity detail=child; AndroidScreenTest.rotate(detail,true); visible((View)field(detail,"play")); screenshot(detail,"android-film-portrait"); AndroidScreenTest.rotate(detail,false); assertSame(detail,child); screenshot(detail,"android-film-landscape"); close(detail); child=null;
        waitFor("Library focus missing",home::hasWindowFocus); section("Seriale","series",series.name()); opens(SeriesActivity.class.getName(),() -> tap(home,series.name())); waitFor("Series data missing",() -> find(root(child),"Odcinki")!=null); int calls=seriesRequests.get(); AndroidScreenTest.rotate(child,true); visible((View)field(child,"play")); screenshot(child,"android-serial-portrait"); AndroidScreenTest.rotate(child,false); assertEquals("Rotation downloaded series data again",calls,seriesRequests.get()); screenshot(child,"android-serial-landscape");
    }
    @Test public void rotatingAMovieKeepsItsPlayerAndPlaybackPosition() throws Exception {
        section("Filmy","movie",movie.name()); opens(MovieActivity.class.getName(),() -> tap(home,movie.name())); View play=(View)field(child,"play"); opens(PlayerActivity.class.getName(),() -> tap(play)); waitFor("Movie player missing",() -> { try { return field(child,"player")!=null; } catch(Exception e) { return false; } }); ExoPlayer player=(ExoPlayer)field(child,"player"); playing(player);
        instrument().runOnMainSync(() -> player.seekTo(5000)); playing(player); AndroidScreenTest.rotate(child,true); assertSame(player,field(child,"player")); playing(player); final long[] position={0}; instrument().runOnMainSync(() -> position[0]=player.getCurrentPosition()); assertTrue("Movie restarted during rotation",position[0]>=4500); screenshot(child,"android-player-film-portrait"); AndroidScreenTest.rotate(child,false); assertSame(player,field(child,"player")); playing(player);
    }
}
