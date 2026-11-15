package pl.tvplayer;
import android.content.pm.ActivityInfo;
public final class FullscreenRotation {
    public static int requested(int degrees,int previous) {
        if(degrees<0) return previous;
        if(degrees<=30 || degrees>=330) return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        if(degrees>=60 && degrees<=120) return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        if(degrees>=150 && degrees<=210) return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
        if(degrees>=240 && degrees<=300) return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        return previous;
    }
}
