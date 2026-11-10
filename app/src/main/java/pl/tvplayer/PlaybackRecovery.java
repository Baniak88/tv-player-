package pl.tvplayer;

import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.*;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import java.util.function.Consumer;

/** Retries the existing player, never opening a second stream. Owned by its current screen. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class PlaybackRecovery implements Player.Listener {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExoPlayer player;
    private final boolean live;
    private final Consumer<String> notice;
    private int attempts;
    private boolean pending,closed;
    private final Runnable stable=() -> attempts=0;
    private final Runnable retry=this::retry;
    PlaybackRecovery(ExoPlayer player,boolean live,Consumer<String> notice) {
        this.player=player; this.live=live; this.notice=notice; player.addListener(this);
        // A player transferred from another screen may already have failed.
        if(player.getPlayerError()!=null) onPlayerError(player.getPlayerError());
        else if(live && player.getPlaybackState()==Player.STATE_ENDED) schedule();
    }
    static boolean recoverable(PlaybackException error) {
        for(Throwable cause=error;cause!=null;cause=cause.getCause()) {
            if(cause instanceof HttpDataSource.InvalidResponseCodeException response)
                return SourceClient.transientStatus(response.responseCode);
        }
        return error.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
            || error.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
            || error.errorCode==PlaybackException.ERROR_CODE_IO_UNSPECIFIED
            || error.errorCode==PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW;
    }
    @Override public void onPlayerError(PlaybackException error) {
        ui.removeCallbacks(stable);
        if(recoverable(error) || live && error.errorCode==PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE) schedule();
    }
    @Override public void onPlaybackStateChanged(int state) {
        ui.removeCallbacks(stable);
        if(state==Player.STATE_READY) { pending=false; ui.removeCallbacks(retry); notice.accept(""); if(player.isPlaying()) ui.postDelayed(stable,30000); }
        else if(state==Player.STATE_ENDED && live) schedule();
    }
    @Override public void onIsPlayingChanged(boolean playing) {
        ui.removeCallbacks(stable);
        if(playing) ui.postDelayed(stable,30000);
    }
    @Override public void onPlayWhenReadyChanged(boolean ready,int reason) {
        if(!ready) { ui.removeCallbacks(retry); pending=false; }
        else if(player.getPlayerError()!=null) onPlayerError(player.getPlayerError());
        else if(live && player.getPlaybackState()==Player.STATE_ENDED) schedule();
    }
    private void schedule() {
        if(closed || pending || !player.getPlayWhenReady()) return;
        if(attempts>=6) { notice.accept("Nie udało się wznowić. Ponów odtwarzanie lub wybierz inny kanał."); return; }
        long delay=Math.min(8000,1000L<<attempts++); pending=true;
        notice.accept("Przerwa w połączeniu · ponawianie "+attempts+"/6…"); ui.postDelayed(retry,delay);
    }
    private void retry() {
        pending=false;
        if(closed || !player.getPlayWhenReady()) return;
        // Rejoin live TV at the live edge; VOD keeps its current playback position.
        if(live) player.seekToDefaultPosition();
        player.prepare();
    }
    void retryNow() {
        attempts=0; ui.removeCallbacks(retry); pending=false;
        if(live) player.seekToDefaultPosition();
        player.prepare(); player.play();
    }
    void close() { closed=true; ui.removeCallbacksAndMessages(null); player.removeListener(this); }
}
