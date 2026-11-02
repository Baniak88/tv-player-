package pl.tvplayer;

import android.content.Context;
import android.os.*;
import androidx.media3.exoplayer.ExoPlayer;
import java.util.UUID;

/** A single short-lived transfer; ownership is removed from the sender before offering it. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class PlaybackHandoff {
    record Transfer(ExoPlayer player,Channel channel) {}
    private static final Handler UI=new Handler(Looper.getMainLooper());
    private static String token;
    private static Transfer pending;
    private static GuideRepository repository;
    private static final Runnable expire=PlaybackHandoff::clear;
    static String offer(Context context,ExoPlayer player,Channel channel) {
        clear(); token=UUID.randomUUID().toString(); pending=new Transfer(player,channel); repository=GuideRepository.get(context.getApplicationContext());
        UI.postDelayed(expire,5000); return token;
    }
    static Transfer take(String offered,String url) {
        if(offered==null || !offered.equals(token) || pending==null) return null;
        if(!pending.channel().url().equals(url)) { clear(); return null; }
        UI.removeCallbacks(expire); Transfer result=pending; pending=null; token=null; repository=null; return result;
    }
    static void discard(String offered) { if(offered!=null && offered.equals(token)) clear(); }
    private static void clear() {
        UI.removeCallbacks(expire);
        if(pending!=null) { pending.player().release(); repository.streamStopped(pending.channel()); }
        pending=null; token=null; repository=null;
    }
}
