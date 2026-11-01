package pl.tvplayer;
import android.test.InstrumentationTestCase;
import android.content.Intent;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.view.*;
import android.widget.*;
import java.util.*;
public class NavigationTest extends InstrumentationTestCase {
    private View find(View root,String text) {
        if(root instanceof TextView && text.equals(((TextView)root).getText().toString())) return root;
        if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) {
            View found=find(((ViewGroup)root).getChildAt(i),text); if(found!=null) return found;
        }
        return null;
    }
    public void testTabsAndFullscreen() throws Exception {
        Context context=getInstrumentation().getTargetContext();
        new LibraryStore(context).save(Arrays.asList(
            new Channel("Test TV","https://example.com/live.ts","Test","","tv"),
            new Channel("Test Film","https://example.com/movie.mp4","Test","","movie"),
            new Channel("Test Serial","https://example.com/player_api.php","Test","","series")),Collections.emptySet());
        MainActivity main=(MainActivity)getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Thread.sleep(1500); getInstrumentation().waitForIdleSync();
        View root=main.getWindow().getDecorView();
        assertNotNull(find(root,"TV")); assertNotNull(find(root,"Seriale")); assertNotNull(find(root,"Filmy"));
        getInstrumentation().runOnMainSync(() -> find(root,"Filmy").performClick());
        getInstrumentation().waitForIdleSync(); assertNotNull(find(root,"Test Film")); assertNull(find(root,"Test TV"));
        getInstrumentation().runOnMainSync(() -> find(root,"Seriale").performClick());
        getInstrumentation().waitForIdleSync(); assertNotNull(find(root,"Test Serial")); assertNull(find(root,"Test Film"));
        PlayerActivity player=(PlayerActivity)getInstrumentation().startActivitySync(new Intent(context,PlayerActivity.class).putExtra("url","https://example.com/movie.mp4").putExtra("name","Test Film").putExtra("kind","movie").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,player.getRequestedOrientation());
        getInstrumentation().runOnMainSync(() -> find(player.getWindow().getDecorView(),"↙").performClick());
        getInstrumentation().waitForIdleSync(); assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,player.getRequestedOrientation());
        getInstrumentation().runOnMainSync(() -> find(player.getWindow().getDecorView(),"⤢").performClick());
        getInstrumentation().waitForIdleSync(); assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,player.getRequestedOrientation());
        getInstrumentation().runOnMainSync(() -> { player.finish(); main.finish(); });
    }
}
