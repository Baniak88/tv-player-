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
        assertNotNull(view); Rect box=new Rect(),screen=new Rect(); assertTrue("Control is hidden",view.getGlobalVisibleRect(box));
        View root=view.getRootView(); int[] corner=new int[2]; root.getLocationOnScreen(corner); screen.set(corner[0],corner[1],corner[0]+root.getWidth(),corner[1]+root.getHeight());
        int[] origin=new int[2]; view.getLocationOnScreen(origin);
        assertTrue("Control extends past the left/right edge",origin[0]>=screen.left && origin[0]+view.getWidth()<=screen.right); assertTrue(box.width()>0 && box.height()>0);
    }
    private ExoPlayer preview() throws Exception { return (ExoPlayer)field(field(home,"livePreview"),"player"); }
    @Test public void portraitHomeAndCataloguesFitAndKeepTheSelectedSection() throws Exception {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,home.getRequestedOrientation());
        for(int i=0;i<3;i++) { AndroidScreenTest.rotate(home,false); for(String label:new String[]{"Główna","TV","Filmy","Seriale","Szukaj","Opcje"}) visible(clickable(root(home),label)); AndroidScreenTest.rotate(home,true); }
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
    private void dialogFits(Activity activity,AlertDialog dialog) throws Exception {
        waitFor("Dialog extends outside the phone screen",() -> {
            boolean[] fit={false}; instrument().runOnMainSync(() -> {
                View decor=dialog.getWindow().getDecorView(); int[] xy=new int[2]; decor.getLocationOnScreen(xy);
                android.util.DisplayMetrics display=activity.getResources().getDisplayMetrics();
                fit[0]=dialog.isShowing() && decor.getWidth()>0 && decor.getHeight()>0 && xy[0]>=0 && xy[1]>=0 && xy[0]+decor.getWidth()<=display.widthPixels && xy[1]+decor.getHeight()<=display.heightPixels;
            }); return fit[0];
        });
    }
    @Test public void playbackOptionsFitPortraitAndLandscapeAndExposeTheLastAction() throws Exception {
        section("TV","tv",a.name()); waitFor("Preview missing",() -> { try { return preview()!=null; } catch(Exception e) { return false; } }); playing(preview());
        opens(PlayerActivity.class.getName(),() -> tap(find(root(home),a.name()))); AndroidScreenTest.rotate(child,true);
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,child.getRequestedOrientation());
        tap(child,"Opcje odtwarzania"); AlertDialog options=(AlertDialog)field(child,"androidOptions"); dialogFits(child,options);
        screenshot(options.getWindow().getDecorView(),"android-options-portrait");
        AndroidScreenTest.rotate(child,false); dialogFits(child,options); screenshot(options.getWindow().getDecorView(),"android-options-landscape");
        AndroidScreenTest.rotate(child,true); dialogFits(child,options); ListView list=options.getListView();
        instrument().runOnMainSync(() -> list.setSelection(list.getCount()-1));
        waitFor("Last playback action is unreachable",() -> find(options.getWindow().getDecorView(),"Zgodność dźwięku")!=null);
        tap(find(options.getWindow().getDecorView(),"Zgodność dźwięku"));
        click("Stereo PCM · gdy brak dźwięku"); waitFor("Audio action did not apply",() -> PlaybackAudio.pcm(context()));
        playing((ExoPlayer)field(child,"player"));
        instrument().runOnMainSync(() -> PlaybackAudio.pcm(context(),false));
    }
    private void selectBouquet(String name,String searchText) throws Exception {
        tap((View)field(home,"androidCategories")); AlertDialog dialog=(AlertDialog)field(home,"androidBouquetsDialog"); dialogFits(home,dialog);
        EditText search=dialog.getWindow().getDecorView().findViewWithTag("android-bouquet-search");
        visible(search); tap(search); instrument().sendStringSync(searchText);
        ListView list=dialog.getWindow().getDecorView().findViewWithTag("android-bouquet-list");
        screenshot(dialog.getWindow().getDecorView(),"android-bouquets-portrait");
        try { waitFor("Bouquet search did not filter the visible list",() -> !search.getText().toString().trim().isEmpty() && list.getAdapter().getCount()==1 && list.getChildCount()==1 && find(list.getChildAt(0),name)!=null); }
        catch(AssertionError failure) { android.util.Log.e("TestRunner","Bouquet query="+search.getText()+" rows="+list.getAdapter().getCount()+" children="+list.getChildCount()+" searchHeight="+search.getHeight()+" listHeight="+list.getHeight()); screenshot(dialog.getWindow().getDecorView(),"android-bouquets-portrait"); throw failure; }
        screenshot(dialog.getWindow().getDecorView(),"android-bouquets-portrait"); tap(find(list,name));
        waitFor("Bouquet selection did not filter the catalogue",() -> { try { return name.equals(field(home,"group")); } catch(Exception error) { return false; } });
    }
    @Test public void portraitBouquetButtonFindsM3uGroupsAndPreservesSelectionOnReturn() throws Exception {
        java.util.List<Channel> channels=new java.util.ArrayList<>(java.util.Arrays.asList(a,b,movie,series));
        StringBuilder m3u=new StringBuilder("#EXTM3U\n#EXTINF:-1 group-title=\"UK | MOVIES\",UK Movies test\n"+server.url("/uk.ts")+"\n");
        for(int i=0;i<30;i++) m3u.append("#EXTINF:-1 group-title=\"Bukiet ").append(i).append("\",Kanał ").append(i).append('\n').append(server.url("/extra"+i+".ts")).append('\n');
        channels.addAll(M3uParser.parse(m3u.toString(),server.url("/").toString()));
        Channel otherMovie=new Channel("Film UK",server.url("/film2.mp4").toString(),"UK VOD","","movie").withLogo(movie.logo());
        Channel otherSeries=new Channel("Serial UK",series.url().replace("series_id=9","series_id=10"),"UK SERIALE","","series").withLogo(series.logo());
        channels.add(otherMovie); channels.add(otherSeries); new LibraryStore(context()).save(channels,java.util.Collections.emptySet());
        AndroidScreenTest.rotate(home,true); section("TV","tv",a.name());
        selectBouquet("UK | MOVIES","UK Movies"); waitFor("UK live channel is missing",() -> find(root(home),"UK Movies test")!=null);
        assertNull("A channel from another bouquet is still shown",find(root(home),a.name())); screenshot(home,"android-bouquet-selected");
        waitFor("UK preview missing",() -> { try { return preview()!=null; } catch(Exception e) { return false; } }); playing(preview());
        opens(PlayerActivity.class.getName(),() -> tap(find(root(home),"UK Movies test"))); close(child); child=null; waitFor("Library did not resume",home::hasWindowFocus);
        assertEquals("UK | MOVIES",field(home,"group")); AndroidScreenTest.rotate(home,false); assertEquals("UK | MOVIES",field(home,"group")); AndroidScreenTest.rotate(home,true);
        section("Filmy","movie",movie.name()); selectBouquet("UK VOD","UK VOD"); waitFor("Movie bouquet did not render",() -> find(root(home),otherMovie.name())!=null && find(root(home),movie.name())==null);
        section("Seriale","series",series.name()); selectBouquet("UK SERIALE","UK SERIALE"); waitFor("Series bouquet did not render",() -> find(root(home),otherSeries.name())!=null && find(root(home),series.name())==null);
    }
}
