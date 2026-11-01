package pl.tvplayer;

import android.app.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.util.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView view;
    private LinearLayout error;
    private TextView message;
    private Button full;
    private String url, bookmark;
    private long position;
    private boolean fullscreen=true, onDemand;
    private int dp(int value) { return (int)(value*getResources().getDisplayMetrics().density); }
    private Button action(String text) {
        Button b=new Button(this); b.setText(text); b.setTextColor(-1); b.setTextSize(13); b.setAllCaps(false);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(10),0,dp(10),0);
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();
        shape.setColor(0xCC263246); shape.setCornerRadius(dp(14)); b.setBackground(shape);
        return b;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        url=getIntent().getStringExtra("url"); if(url==null) { finish(); return; }
        String kind=getIntent().getStringExtra("kind");
        onDemand=kind!=null?!kind.equals("tv"):!Channel.infer(url,"","").equals("tv");
        try {
            byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            bookmark=android.util.Base64.encodeToString(hash,android.util.Base64.NO_WRAP);
        } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        position=onDemand?getPreferences(MODE_PRIVATE).getLong(bookmark,0):0;
        if(state!=null) { position=state.getLong("position"); fullscreen=state.getBoolean("fullscreen",true); }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SECURE);
        FrameLayout root=new FrameLayout(this); root.setBackgroundColor(0xFF080D16);
        view=new PlayerView(this); view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        root.addView(view,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(10),dp(24),dp(10),dp(6)); top.setBackgroundColor(0x99080D16);
        Button back=action("‹"); back.setContentDescription("Wróć do biblioteki"); top.addView(back,new LinearLayout.LayoutParams(dp(46),dp(44))); back.setOnClickListener(v -> finish());
        TextView title=new TextView(this); title.setText(getIntent().getStringExtra("name")); title.setTextColor(-1); title.setTextSize(15); title.setMaxLines(2); title.setPadding(dp(10),0,dp(6),0); top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button settings=action("Audio / CC"); top.addView(settings,new LinearLayout.LayoutParams(dp(92),dp(44)));
        settings.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Odtwarzanie")
            .setItems(new String[]{"Ścieżka audio","Napisy","Odtwórz od początku"},(d,i) -> {
                if(i==0) chooseTrack(C.TRACK_TYPE_AUDIO); else if(i==1) chooseTrack(C.TRACK_TYPE_TEXT);
                else if(player!=null) player.seekTo(0);
            }).show());
        full=action("↙"); full.setContentDescription("Włącz lub wyłącz pełny ekran"); top.addView(full,new LinearLayout.LayoutParams(dp(46),dp(44)));
        full.setOnClickListener(v -> { fullscreen=!fullscreen; updateFullscreen(); });
        root.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        error=new LinearLayout(this); error.setOrientation(LinearLayout.VERTICAL); error.setGravity(Gravity.CENTER);
        error.setBackgroundColor(0xEE142033); error.setPadding(dp(24),dp(20),dp(24),dp(20));
        message=new TextView(this); message.setTextColor(-1); message.setTextSize(17); error.addView(message);
        Button retry=action("Spróbuj ponownie"); error.addView(retry);
        retry.setOnClickListener(v -> { if(player!=null) { error.setVisibility(View.GONE); player.prepare(); player.play(); } });
        error.setVisibility(View.GONE); root.addView(error,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        setContentView(root); updateFullscreen();
    }
    private void updateFullscreen() {
        setRequestedOrientation(fullscreen?ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        int flags=fullscreen?View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION:0;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        FrameLayout.LayoutParams size=(FrameLayout.LayoutParams)view.getLayoutParams();
        size.height=fullscreen?-1:dp(240); size.gravity=Gravity.CENTER; view.setLayoutParams(size);
        full.setText(fullscreen?"↙":"⤢");
    }
    @Override public void onConfigurationChanged(Configuration config) { super.onConfigurationChanged(config); updateFullscreen(); }
    @Override protected void onStart() {
        super.onStart(); if(url==null) return;
        player=new ExoPlayer.Builder(this).build(); view.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException e) {
                message.setText("Nie można odtworzyć materiału. Sprawdź internet, dostępność źródła lub kodeki telefonu.\nKod: "+e.getErrorCodeName());
                error.setVisibility(View.VISIBLE);
            }
        });
        MediaItem.Builder item=new MediaItem.Builder().setUri(url);
        String path=android.net.Uri.parse(url).getPath();
        if(path!=null && path.endsWith(".ts")) item.setMimeType(MimeTypes.VIDEO_MP2T);
        player.setMediaItem(item.build()); player.seekTo(position); player.prepare(); player.play();
        if(onDemand && position>5000) Toast.makeText(this,"Wznawiam oglądanie. Od początku: Audio / CC.",Toast.LENGTH_LONG).show();
    }
    private void chooseTrack(int type) {
        if(player==null) return;
        List<String> labels=new ArrayList<>(); List<Runnable> actions=new ArrayList<>();
        labels.add(type==C.TRACK_TYPE_TEXT?"Wyłącz napisy":"Automatycznie");
        actions.add(() -> player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
            .clearOverridesOfType(type).setTrackTypeDisabled(type,type==C.TRACK_TYPE_TEXT).build()));
        for(Tracks.Group group:player.getCurrentTracks().getGroups()) {
            if(group.getType()!=type) continue;
            for(int i=0;i<group.length;i++) {
                if(!group.isTrackSupported(i)) continue;
                final int index=i;
                Format format=group.getTrackFormat(i);
                String label=format.label!=null?format.label:format.language!=null?format.language:"Ścieżka "+(labels.size()+1);
                labels.add(label);
                actions.add(() -> player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .clearOverridesOfType(type).setTrackTypeDisabled(type,false)
                    .addOverride(new TrackSelectionOverride(group.getMediaTrackGroup(),index)).build()));
            }
        }
        if(labels.size()==1) { Toast.makeText(this,"Źródło nie udostępnia dodatkowych ścieżek.",Toast.LENGTH_LONG).show(); return; }
        new AlertDialog.Builder(this).setTitle(type==C.TRACK_TYPE_TEXT?"Napisy":"Audio").setItems(labels.toArray(new String[0]),(d,i) -> { if(player!=null) actions.get(i).run(); }).show();
    }
    @Override protected void onStop() {
        if(player!=null) {
            position=onDemand?player.getCurrentPosition():0;
            long duration=player.getDuration();
            if(player.getPlaybackState()==Player.STATE_ENDED || (duration>0 && position>=duration-2000)) position=0;
            if(onDemand) getPreferences(MODE_PRIVATE).edit().putLong(bookmark,position).apply();
            view.setPlayer(null); player.release(); player=null;
        }
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putLong("position",player==null?position:player.getCurrentPosition()); out.putBoolean("fullscreen",fullscreen); super.onSaveInstanceState(out);
    }
}
