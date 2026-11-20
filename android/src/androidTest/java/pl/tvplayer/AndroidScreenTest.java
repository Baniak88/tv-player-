package pl.tvplayer;

import android.app.Activity;
import android.content.res.Configuration;
import java.io.*;
import static pl.tvplayer.TvTestUi.*;

final class AndroidScreenTest {
    static void rotation(int rotation) throws Exception {
        // Send physical accelerometer input through the emulator host. Changing wm's
        // user rotation would not test FULL_SENSOR, which deliberately ignores that lock.
        java.net.HttpURLConnection request=(java.net.HttpURLConnection)new java.net.URL("http://10.0.2.2:8769/rotate/"+rotation).openConnection();
        request.setConnectTimeout(10000); request.setReadTimeout(10000);
        try { org.junit.Assert.assertEquals("Emulator sensor injection failed",200,request.getResponseCode()); } finally { request.disconnect(); }
        instrument().waitForIdleSync();
    }
    static void rotate(Activity activity,boolean portrait) throws Exception {
        rotation(portrait?0:1);
        waitFor("Android did not follow the sensor orientation",() -> activity.getResources().getConfiguration().orientation==(portrait?Configuration.ORIENTATION_PORTRAIT:Configuration.ORIENTATION_LANDSCAPE) && !activity.isDestroyed() && (portrait?activity.getWindow().getDecorView().getHeight()>activity.getWindow().getDecorView().getWidth():activity.getWindow().getDecorView().getWidth()>activity.getWindow().getDecorView().getHeight()));
        instrument().waitForIdleSync();
    }
}
