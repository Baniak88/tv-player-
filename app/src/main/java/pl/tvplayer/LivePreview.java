package pl.tvplayer;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import java.util.*;
import java.util.function.Consumer;

/** Phone bouquet preview. Exactly one selected stream is opened; scrolling never starts streams. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class LivePreview {
    final LinearLayout panel;
    private final Activity activity;
    private final PlayerView video;
    private final TextView title,schedule,notice;
    private final Button pause,full;
    private final Consumer<Channel> fullscreen;
    private final Runnable selectionChanged;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable guideTick=new Runnable() { public void run() { updateGuide(); if(active && channel!=null) ui.postDelayed(this,10000); } };
    private ExoPlayer player;
    private Player.Listener listener;
    private Channel channel;
    private boolean active,visible,observing,weak;
    private String remembered="",scope="";
    private GuideRepository.Data guide;
    private List<Channel> visibleChannels=Collections.emptyList();
    private final Player.Listener events=new Player.Listener() {
        @Override public void onPlaybackStateChanged(int state) {
            if(player==null) return;
            notice.setText(state==Player.STATE_BUFFERING?"Buforowanie…":state==Player.STATE_ENDED?"Koniec strumienia · wybierz kanał ponownie":"");
            notice.setVisibility(notice.getText().length()==0?View.GONE:View.VISIBLE); updatePause();
        }
        @Override public void onPlayWhenReadyChanged(boolean ready,int reason) { updatePause(); }
        @Override public void onPlayerError(PlaybackException error) { notice.setText("Nie można odtworzyć podglądu. Dotknij kanału, aby ponowić."); notice.setVisibility(View.VISIBLE); }
    };
    LivePreview(Activity activity,boolean wide,Bundle state,Consumer<Channel> fullscreen,Runnable selectionChanged) {
        this.activity=activity; activity.setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC); this.fullscreen=fullscreen; this.selectionChanged=selectionChanged;
        remembered=state==null?"":state.getString("preview_url","");
        panel=new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL); panel.setBackground(Ui.shape(activity,Ui.SURFACE,14,Ui.BORDER)); panel.setClipToOutline(true);
        FrameLayout picture=new FrameLayout(activity); picture.setBackgroundColor(0xFF000000);
        video=new PlayerView(activity); video.setUseController(false); video.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING); video.setKeepContentOnPlayerReset(false);
        picture.addView(video,new FrameLayout.LayoutParams(-1,-1));
        notice=Ui.text(activity,"",13,Ui.TEXT); notice.setGravity(Gravity.CENTER); notice.setPadding(dp(16),dp(10),dp(16),dp(10)); notice.setBackgroundColor(0xB3000000); notice.setVisibility(View.GONE);
        picture.addView(notice,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        if(wide) panel.addView(picture,new LinearLayout.LayoutParams(-1,0,1));
        else { int height=Math.min(Math.round((activity.getResources().getDisplayMetrics().widthPixels-dp(32))*9f/16),Math.round(activity.getResources().getDisplayMetrics().heightPixels*0.21f)); panel.addView(picture,new LinearLayout.LayoutParams(-1,Math.max(dp(130),Math.min(dp(200),height)))); }
        LinearLayout controls=new LinearLayout(activity); controls.setGravity(Gravity.CENTER_VERTICAL); controls.setPadding(dp(4),0,dp(4),0);
        pause=Ui.button(activity,"Ⅱ"); pause.setContentDescription("Wstrzymaj podgląd"); pause.setTextSize(18); Ui.ghost(pause,Ui.TEXT); controls.addView(pause,new LinearLayout.LayoutParams(dp(48),dp(48)));
        pause.setOnClickListener(v -> { if(player==null) { start(); return; } if(player.getPlayerError()!=null) { player.prepare(); player.play(); } else if(player.getPlayWhenReady()) player.pause(); else player.play(); });
        title=Ui.text(activity,"Podgląd",14,Ui.TEXT); title.setMaxLines(1); title.setEllipsize(TextUtils.TruncateAt.END); title.setTypeface(null,android.graphics.Typeface.BOLD); controls.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button internet=Ui.button(activity,"⚙"); internet.setContentDescription("Ustawienia słabszego internetu"); Ui.ghost(internet,Ui.MUTED); controls.addView(internet,new LinearLayout.LayoutParams(dp(48),dp(48)));
        internet.setOnClickListener(v -> PlaybackSettings.choose(activity,() -> { release(); start(); selectionChanged.run(); }));
        full=Ui.button(activity,"⤢"); full.setTextSize(23); full.setContentDescription("Otwórz podgląd na pełnym ekranie"); Ui.ghost(full,Ui.TEXT); controls.addView(full,new LinearLayout.LayoutParams(dp(48),dp(48)));
        full.setOnClickListener(v -> { if(channel!=null) fullscreen.accept(channel); });
        panel.addView(controls,new LinearLayout.LayoutParams(-1,dp(48)));
        schedule=Ui.text(activity,"",12,Ui.MUTED); schedule.setMaxLines(wide?2:1); schedule.setEllipsize(TextUtils.TruncateAt.END); schedule.setPadding(dp(12),0,dp(12),dp(8));
        schedule.setContentDescription("Aktualny program podglądu"); schedule.setMinHeight(dp(24)); panel.addView(schedule,new LinearLayout.LayoutParams(-1,-2));
        schedule.setOnClickListener(v -> { if(channel!=null) GuideDialog.open(activity,channel,Runnable::run,c -> { if(c.kind().equals("tv")) select(c); else fullscreen.accept(c); }); });
        panel.setVisibility(View.GONE);
    }
    private int dp(int n) { return Ui.dp(activity,n); }
    Channel selected() { return channel; }
    String selectedUrl() { return channel==null?"":channel.url(); }
    private boolean playable(Channel item) { String name=item.name().trim(); return item.kind().equals("tv") && !item.url().isEmpty() && !(name.startsWith("###") && name.endsWith("###")); }
    void show(List<Channel> channels,boolean show,String selectedScope) {
        visible=show; panel.setVisibility(show?View.VISIBLE:View.GONE);
        if(!show) { release(); return; }
        scope=selectedScope; visibleChannels=channels;
        Channel selected=null; String wanted=channel==null?remembered:channel.url(); boolean found=false;
        for(Channel item:channels) if(playable(item)) { if(selected==null) selected=item; if(item.url().equals(wanted)) { selected=item; found=true; break; } }
        if(!found) {
            android.content.SharedPreferences prefs=activity.getSharedPreferences("live-preview",Context.MODE_PRIVATE); String key=hash(scope),stored=prefs.getString(key,""); int index=prefs.getInt(key+".index",-1);
            if(index>=0 && index<channels.size() && playable(channels.get(index)) && hash(channels.get(index).url()).equals(stored)) selected=channels.get(index);
            else if(!stored.isEmpty()) for(Channel item:channels) if(playable(item) && hash(item.url()).equals(stored)) { selected=item; break; }
        }
        if(selected==null) { channel=null; release(); full.setEnabled(false); pause.setEnabled(false); title.setText("Brak kanałów do podglądu"); return; }
        select(selected,false);
    }
    void select(Channel selected) { select(selected,true); }
    private void select(Channel selected,boolean user) {
        if(!playable(selected)) return;
        boolean same=channel!=null && channel.url().equals(selected.url());
        if(!same) { release(); guide=null; }
        channel=selected; remembered=selected.url(); title.setText("Na żywo · "+selected.name()); full.setEnabled(true); pause.setEnabled(true);
        if(active && visible) { if(user && player!=null && player.getPlayerError()!=null && same) { player.prepare(); player.play(); } else start(); }
        rememberSelection();
        updateGuide(); if(active && visible) { ui.removeCallbacks(guideTick); ui.postDelayed(guideTick,1000); } if(user) selectionChanged.run();
    }
    void guideChanged(String url) { if(channel!=null && channel.url().equals(url)) updateGuide(); }
    private void updateGuide() {
        if(channel==null || !visible) return;
        GuideRepository repository=GuideRepository.get(activity); GuideRepository.Data data=repository.peek(channel,false);
        if(data!=null) { guide=data; schedule.setText(GuideRepository.summary(data,System.currentTimeMillis()/1000)); return; }
        schedule.setText(guide==null?"Program TV · dotknij, aby sprawdzić EPG":GuideRepository.summary(guide,System.currentTimeMillis()/1000));
        // The channel rows already fetch provider EPG; the preview only observes their cache and EIT.
    }
    void resume() { active=true; if(player!=null && weak!=PlaybackSettings.weakInternet(activity)) release(); start(); ui.removeCallbacks(guideTick); ui.post(guideTick); }
    void pause() { active=false; ui.removeCallbacks(guideTick); release(); }
    void destroy() { pause(); ui.removeCallbacksAndMessages(null); }
    private void start() {
        if(!active || !visible || channel==null || player!=null || activity.isFinishing()) return;
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Context context=activity.getApplicationContext(); Channel current=channel; GuideRepository repository=GuideRepository.get(context);
        DefaultDataSource.Factory upstream=new DefaultDataSource.Factory(context);
        player=new ExoPlayer.Builder(context).setLoadControl(PlaybackSettings.loadControl(context)).setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true).setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(() -> new EitDataSource(upstream.createDataSource(),current,repository))).build();
        repository.streamStarted(current); observing=true; weak=PlaybackSettings.weakInternet(context);
        player.setTrackSelectionParameters(PlaybackSettings.tracks(context,player.getTrackSelectionParameters())); attach();
        MediaItem.Builder item=new MediaItem.Builder().setUri(current.url()); String path=android.net.Uri.parse(current.url()).getPath(); if(path!=null && path.endsWith(".ts")) item.setMimeType(MimeTypes.VIDEO_MP2T);
        player.setMediaItem(item.build()); player.prepare(); player.play();
    }
    private void attach() { listener=events; player.addListener(listener); video.setPlayer(player); updatePause(); }
    private void updatePause() { boolean playing=player!=null && player.getPlayWhenReady(); pause.setText(playing?"Ⅱ":"▶"); pause.setContentDescription(playing?"Wstrzymaj podgląd":"Wznów podgląd"); }
    String handoff() {
        if(player==null || channel==null) return null;
        ExoPlayer transferring=player; video.setPlayer(null); if(listener!=null) transferring.removeListener(listener); player=null; observing=false; listener=null;
        return PlaybackHandoff.offer(activity,transferring,channel);
    }
    void receive(String token,Channel returned) {
        PlaybackHandoff.Transfer transfer=PlaybackHandoff.take(token,returned.url());
        if(transfer==null) { select(returned); return; }
        release(); activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); channel=returned; remembered=returned.url(); guide=null; player=transfer.player(); observing=true; weak=PlaybackSettings.weakInternet(activity);
        title.setText("Na żywo · "+returned.name()); rememberSelection(); attach(); updateGuide(); selectionChanged.run();
    }
    private void release() {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(player!=null) { video.setPlayer(null); if(listener!=null) player.removeListener(listener); player.release(); player=null; listener=null; }
        if(observing && channel!=null) { GuideRepository.get(activity).streamStopped(channel); observing=false; }
        notice.setVisibility(View.GONE);
    }
    private void rememberSelection() {
        if(channel==null) return;
        android.content.SharedPreferences prefs=activity.getSharedPreferences("live-preview",Context.MODE_PRIVATE); String key=hash(scope),identity=hash(channel.url()); int index=visibleChannels.indexOf(channel);
        if(!identity.equals(prefs.getString(key,"")) || index>=0 && index!=prefs.getInt(key+".index",-1)) prefs.edit().putString(key,identity).putInt(key+".index",index).apply();
    }
    private static String hash(String value) {
        try { return android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP); }
        catch(java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    void save(Bundle state) { if(channel!=null) state.putString("preview_url",channel.url()); }
}
