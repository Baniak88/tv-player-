package pl.tvplayer;

import android.app.Activity;
import android.content.res.Configuration;
import java.io.*;
import static pl.tvplayer.TvTestUi.*;

final class AndroidScreenTest {
    static void rotation(int rotation) throws Exception {
        try(android.os.ParcelFileDescriptor.AutoCloseInputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument().getUiAutomation().executeShellCommand("wm user-rotation lock "+rotation))) { byte[] bytes=new byte[256]; while(in.read(bytes)!=-1) { } }
        instrument().waitForIdleSync();
    }
    static void rotate(Activity activity,boolean portrait) throws Exception {
        rotation(portrait?0:1);
        waitFor("Android did not follow the display orientation",() -> activity.getResources().getConfiguration().orientation==(portrait?Configuration.ORIENTATION_PORTRAIT:Configuration.ORIENTATION_LANDSCAPE) && activity.hasWindowFocus() && (portrait?activity.getWindow().getDecorView().getHeight()>activity.getWindow().getDecorView().getWidth():activity.getWindow().getDecorView().getWidth()>activity.getWindow().getDecorView().getHeight()));
        instrument().waitForIdleSync();
    }
}
