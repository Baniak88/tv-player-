package pl.tvplayer;

import android.app.*;
import android.content.Context;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.exoplayer.DefaultLoadControl;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class PlaybackSettings {
    static boolean weakInternet(Context context) { return context.getSharedPreferences("playback-network",Context.MODE_PRIVATE).getBoolean("weak",false); }
    static DefaultLoadControl loadControl(Context context) {
        boolean weak=weakInternet(context);
        // Start as quickly as before (1.5 s), but keep a much deeper buffer once playing so short network dips do not stutter.
        return new DefaultLoadControl.Builder().setBufferDurationsMs(weak?25000:15000,weak?60000:45000,weak?4000:1500,weak?6000:3500)
            .setTargetBufferBytes(64*1024*1024).setPrioritizeTimeOverSizeThresholds(false).setBackBuffer(10000,true).build();
    }
    static TrackSelectionParameters tracks(Context context,TrackSelectionParameters current) {
        boolean weak=weakInternet(context);
        return current.buildUpon().setMaxVideoSize(weak?1280:Integer.MAX_VALUE,weak?720:Integer.MAX_VALUE)
            .setMaxVideoBitrate(weak?2500000:Integer.MAX_VALUE).build();
    }
    static void choose(Activity activity,Runnable changed) {
        boolean previous=weakInternet(activity);
        android.widget.TextView note=new android.widget.TextView(activity); note.setTextSize(13); int padding=Ui.dp(activity,20); note.setPadding(padding,Ui.dp(activity,8),padding,Ui.dp(activity,8));
        note.setText("Większy bufor pomaga przy chwilowych spadkach prędkości, ale wydłuża start. W źródłach adaptacyjnych preferuje dostępne warianty do 720p i 2,5 Mb/s. Przy stałej jakości wybierz kanał SD/HD zamiast FHD/4K.");
        new Ui.Dialog(activity).setTitle("Połączenie internetowe").setView(note)
            .setSingleChoiceItems(new String[]{"Standard · szybszy start","Słabszy internet · większy bufor"},previous?1:0,(dialog,index) -> {
                boolean weak=index==1;
                activity.getSharedPreferences("playback-network",Context.MODE_PRIVATE).edit().putBoolean("weak",weak).apply(); dialog.dismiss();
                if(weak!=previous) { if(weak) GuideRepository.get(activity).pauseBulkDownloads(); else GuideRepository.get(activity).invalidateFailures(); changed.run(); }
            })
            .setNegativeButton("Zamknij",null).show();
    }
}
