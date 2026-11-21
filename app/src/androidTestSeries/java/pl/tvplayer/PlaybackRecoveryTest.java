package pl.tvplayer;

import android.os.Looper;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.datasource.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.Assert.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
@RunWith(AndroidJUnit4.class)
public class PlaybackRecoveryTest {
    private static final class Session {
        int prepares,seeks; boolean playing=true; long position=45000;
        final ExoPlayer player=(ExoPlayer)Proxy.newProxyInstance(ExoPlayer.class.getClassLoader(),new Class[]{ExoPlayer.class},(proxy,method,args) -> {
            switch(method.getName()) {
                case "getPlayWhenReady": case "isPlaying": return playing;
                case "getPlaybackState": return Player.STATE_IDLE;
                case "getPlayerError": return null;
                case "getApplicationLooper": return Looper.getMainLooper();
                case "getCurrentPosition": return position;
                case "seekToDefaultPosition": seeks++; position=0; return null;
                case "prepare": prepares++; return null;
                case "play": playing=true; return null;
                case "equals": return proxy==args[0];
                case "hashCode": return System.identityHashCode(proxy);
                default: return null;
            }
        });
    }
    private void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    private PlaybackException networkError() { return new PlaybackException("offline",null,PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED); }
    @Test public void reconnectsExistingLivePlayerAtLiveEdge() throws Exception {
        Session session=new Session(); PlaybackRecovery[] recovery=new PlaybackRecovery[1];
        main(() -> { recovery[0]=new PlaybackRecovery(session.player,true,text -> {}); recovery[0].onPlayerError(networkError()); recovery[0].onPlayerError(networkError()); });
        try { Thread.sleep(1400); main(() -> { assertEquals(1,session.prepares); assertEquals(1,session.seeks); }); }
        finally { main(recovery[0]::close); }
    }
    @Test public void vodReconnectKeepsPosition() throws Exception {
        Session session=new Session(); PlaybackRecovery[] recovery=new PlaybackRecovery[1];
        main(() -> { recovery[0]=new PlaybackRecovery(session.player,false,text -> {}); recovery[0].onPlayerError(networkError()); });
        try { Thread.sleep(1400); main(() -> { assertEquals(1,session.prepares); assertEquals(0,session.seeks); assertEquals(45000,session.position); }); }
        finally { main(recovery[0]::close); }
    }
    @Test public void closingOrPausingCancelsPendingRetry() throws Exception {
        Session closed=new Session(),paused=new Session(); PlaybackRecovery[] recovery=new PlaybackRecovery[2];
        main(() -> {
            recovery[0]=new PlaybackRecovery(closed.player,true,text -> {}); recovery[0].onPlayerError(networkError()); recovery[0].close();
            recovery[1]=new PlaybackRecovery(paused.player,true,text -> {}); recovery[1].onPlayerError(networkError());
            paused.playing=false; recovery[1].onPlayWhenReadyChanged(false,Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
        });
        try { Thread.sleep(1400); main(() -> { assertEquals(0,closed.prepares); assertEquals(0,paused.prepares); }); }
        finally { main(recovery[1]::close); }
    }
    @Test public void codecAndAccountErrorsAreNotRetried() {
        main(() -> {
            assertFalse(PlaybackRecovery.recoverable(new PlaybackException("codec",null,PlaybackException.ERROR_CODE_DECODING_FAILED)));
            for(int status:new int[]{401,403,404,458}) {
                HttpDataSource.InvalidResponseCodeException cause=new HttpDataSource.InvalidResponseCodeException(status,"error",null,Collections.emptyMap(),new DataSpec(android.net.Uri.parse("https://example.com/live.ts")),new byte[0]);
                assertFalse(PlaybackRecovery.recoverable(new PlaybackException("http",cause,PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)));
            }
        });
    }
}
