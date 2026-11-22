package pl.tvplayer;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TvPhoneSetupTest {
    private Object field(Object value,String name) throws Exception { java.lang.reflect.Field field=value.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(value); }
    @Test public void phoneFormDeliversAccountAndClosesPairingDialog() throws Exception {
        TvTestUi.resetNavigation();
        Instrumentation instrument=InstrumentationRegistry.getInstrumentation(); Context context=instrument.getTargetContext();
        TvActivity activity=(TvActivity)instrument.startActivitySync(new Intent(context,TvActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        TvPhoneSetup[] setup=new TvPhoneSetup[1]; AtomicReference<TvAccountStore.Account> received=new AtomicReference<>();
        instrument.runOnMainSync(() -> { setup[0]=new TvPhoneSetup(activity); setup[0].show(received::set); });
        try {
            LocalSourceServer server=(LocalSourceServer)field(setup[0],"server"); assertNotNull("No LAN address found",server);
            AlertDialog dialog=(AlertDialog)field(setup[0],"dialog"); assertTrue(dialog.isShowing());
            long layoutDeadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while(dialog.getWindow().getDecorView().getWidth()==0 && System.nanoTime()<layoutDeadline) { Thread.sleep(50); instrument.waitForIdleSync(); }
            instrument.runOnMainSync(() -> {
                View root=dialog.getWindow().getDecorView(); Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888); root.draw(new Canvas(bitmap));
                try(java.io.FileOutputStream output=context.openFileOutput("tv-phone-setup.png",Context.MODE_PRIVATE)) { bitmap.compress(Bitmap.CompressFormat.PNG,100,output); }
                catch(java.io.IOException failure) { throw new AssertionError(failure); } finally { bitmap.recycle(); }
            });
            OkHttpClient client=new OkHttpClient();
            Request request=new Request.Builder().url(server.url()+"source").post(new FormBody.Builder().add("code",server.code()).add("type","xtream").add("server","https://provider.example/").add("user","phone-user").add("password","phone-password").build()).build();
            try(Response response=client.newCall(request).execute()) { assertEquals(200,response.code()); response.body().string(); }
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while(received.get()==null && System.nanoTime()<deadline) Thread.sleep(50);
            assertEquals(new TvAccountStore.Account("xtream","https://provider.example/","phone-user","phone-password"),received.get());
            instrument.runOnMainSync(() -> assertFalse("Pairing dialog remained open",dialog.isShowing()));
        } finally { instrument.runOnMainSync(() -> { setup[0].close(); activity.finish(); }); }
    }
}
