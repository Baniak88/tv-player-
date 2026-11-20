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
    /** Restarts a stream that stays in buffering or stops advancing without the player reporting an error. */
    private final Runnable stall=this::checkStall;
    private long lastPosition=-1,lastMoved;
    private static final long STALL_MS=20000;
    PlaybackRecovery(ExoPlayer player,boolean live,Consumer<String> notice) {
        this.player=player; this.live=live; this.notice=notice; player.addListener(this);
        // A player transferred from another screen may already have failed.
        if(player.getPlayerError()!=null) onPlayerError(player.getPlayerError());
        else if(live && player.getPlaybackState()==Player.STATE_ENDED) schedule();
        watch();
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
        ui.removeCallbacks(stable); watch();
        if(state==Player.STATE_READY) { pending=false; ui.removeCallbacks(retry); notice.accept(""); if(player.isPlaying()) ui.postDelayed(stable,30000); }
        else if(state==Player.STATE_ENDED && live) schedule();
    }
    @Override public void onIsPlayingChanged(boolean playing) {
        ui.removeCallbacks(stable); watch();
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
    /** Re-arms the stall check whenever playback state changes; it runs every few seconds while the user expects playback. */
    private void watch() { ui.removeCallbacks(stall); lastPosition=-1; lastMoved=android.os.SystemClock.elapsedRealtime(); if(!closed && player.getPlayWhenReady()) ui.postDelayed(stall,4000); }
    private void checkStall() {
        if(closed || !player.getPlayWhenReady() || pending || player.getPlayerError()!=null) return;
        if(player.getPlaybackSuppressionReason()!=Player.PLAYBACK_SUPPRESSION_REASON_NONE) { watch(); return; } // e.g. audio focus taken by another app
        int state=player.getPlaybackState(); long now=android.os.SystemClock.elapsedRealtime();
        if(state==Player.STATE_READY || state==Player.STATE_BUFFERING) {
            long position=player.getCurrentPosition();
            if(position!=lastPosition) { lastPosition=position; lastMoved=now; }
            else if(now-lastMoved>=STALL_MS) { restart(); return; }
        }
        ui.postDelayed(stall,4000);
    }
    /** A silent stall (no error, no progress): reopen the same stream, at the live edge or at the current film position. */
    private void restart() {
        if(attempts>=6) { notice.accept("Nie udało się wznowić. Ponów odtwarzanie lub wybierz inny kanał."); return; }
        attempts++; notice.accept("Obraz się zatrzymał · ponowne łączenie "+attempts+"/6…");
        long position=player.getCurrentPosition(); player.stop();
        if(live) player.seekToDefaultPosition(); else player.seekTo(position);
        player.prepare(); player.play(); watch();
    }
    void close() { closed=true; ui.removeCallbacksAndMessages(null); player.removeListener(this); }
}
