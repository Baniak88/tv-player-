package pl.tvplayer;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.util.*;
import java.util.function.Consumer;

/** Phone bouquet preview. Exactly one selected stream is opened; scrolling never starts streams. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class LivePreview {
    final LinearLayout panel;
    private final Activity activity;
    private final boolean television;
    private final PlayerView video;
    private final TextView title,schedule,notice;
    private TextView programTitle,times,remaining,description; private ProgressBar progress;
    private final Button pause,full;
    private final Consumer<Channel> fullscreen;
    private final Runnable selectionChanged;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable guideTick=new Runnable() { public void run() { updateGuide(); if(active && channel!=null) ui.postDelayed(this,10000); } };
    private ExoPlayer player;
    private PlaybackRecovery recovery;
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
        @Override public void onPlayerError(PlaybackException error) { notice.setText(television?"Nie można odtworzyć podglądu. Wybierz kanał i spróbuj ponownie.":"Nie można odtworzyć podglądu. Dotknij kanału, aby ponowić."); notice.setVisibility(View.VISIBLE); }
    };
    LivePreview(Activity activity,boolean wide,Bundle state,Consumer<Channel> fullscreen,Runnable selectionChanged) {
        this(activity,wide,state,fullscreen,selectionChanged,false);
    }
    LivePreview(Activity activity,boolean wide,Bundle state,Consumer<Channel> fullscreen,Runnable selectionChanged,boolean television) {
        this.activity=activity; this.television=television; activity.setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC); this.fullscreen=fullscreen; this.selectionChanged=selectionChanged;
        remembered=state==null?"":state.getString("preview_url","");
        panel=new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL); panel.setBackground(Ui.shape(activity,television?Ui.TV_CARD:Ui.SURFACE,14,television?0:Ui.BORDER)); panel.setClipToOutline(true);
        if(television) panel.setPadding(dp(12),dp(12),dp(12),dp(8));
        FrameLayout picture=new FrameLayout(activity); picture.setBackground(Ui.shape(activity,0xFF000000,10,television?0x33FFFFFF:0)); picture.setClipToOutline(true);
        video=new PlayerView(activity); video.setUseController(false); video.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING); video.setKeepContentOnPlayerReset(false);
        picture.addView(video,new FrameLayout.LayoutParams(-1,-1));
        notice=Ui.text(activity,"",13,Ui.TEXT); notice.setGravity(Gravity.CENTER); notice.setPadding(dp(16),dp(10),dp(16),dp(10)); notice.setBackgroundColor(0xB3000000); notice.setVisibility(View.GONE);
        picture.addView(notice,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        if(television) { int screenHeight=Math.round(activity.getResources().getDisplayMetrics().heightPixels/activity.getResources().getDisplayMetrics().density); panel.addView(picture,new LinearLayout.LayoutParams(-1,dp(Math.min((televisionWidth()-24)*9/16,screenHeight*34/100)))); }
        else if(wide) panel.addView(picture,new LinearLayout.LayoutParams(-1,0,1));
        else { int height=Math.min(Math.round((activity.getResources().getDisplayMetrics().widthPixels-dp(32))*9f/16),Math.round(activity.getResources().getDisplayMetrics().heightPixels*0.21f)); panel.addView(picture,new LinearLayout.LayoutParams(-1,Math.max(dp(130),Math.min(dp(200),height)))); }
        if(television) {
            // Receiver-skin layout: channel in amber with a LIVE chip, programme title, times, remaining time, progress and synopsis.
            LinearLayout head=new LinearLayout(activity); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(dp(4),dp(12),dp(4),0);
            channelLabel=Ui.text(activity,"",17,Ui.TV_GOLD); channelLabel.setSingleLine(true); channelLabel.setEllipsize(TextUtils.TruncateAt.END); head.addView(channelLabel,new LinearLayout.LayoutParams(0,-2,1));
            TextView live=Ui.text(activity,"●  NA ŻYWO",12,Ui.TEXT); live.setTypeface(null,android.graphics.Typeface.BOLD); live.setPadding(dp(10),dp(4),dp(10),dp(4)); live.setBackground(Ui.shape(activity,0xFF0E1013,12,0x33FFFFFF));
            android.text.SpannableString dot=new android.text.SpannableString("●  NA ŻYWO"); dot.setSpan(new android.text.style.ForegroundColorSpan(0xFFFF5A5A),0,1,0); live.setText(dot); head.addView(live);
            panel.addView(head,new LinearLayout.LayoutParams(-1,-2));
            programTitle=Ui.text(activity,"",21,Ui.TEXT); programTitle.setSingleLine(true); programTitle.setEllipsize(TextUtils.TruncateAt.END); programTitle.setPadding(dp(4),dp(4),dp(4),0); panel.addView(programTitle,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout timing=new LinearLayout(activity); timing.setPadding(dp(4),dp(10),dp(4),0);
            times=Ui.text(activity,"",15,Ui.TEXT); timing.addView(times,new LinearLayout.LayoutParams(0,-2,1));
            remaining=Ui.text(activity,"",14,Ui.MUTED); timing.addView(remaining); panel.addView(timing,new LinearLayout.LayoutParams(-1,-2));
            progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(1000);
            progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.TV_GOLD)); progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.TV_LINE));
            LinearLayout.LayoutParams bar=new LinearLayout.LayoutParams(-1,dp(4)); bar.leftMargin=dp(4); bar.rightMargin=dp(4); bar.topMargin=dp(6); panel.addView(progress,bar);
            description=Ui.text(activity,"",14,Ui.MUTED); description.setMaxLines(2); description.setEllipsize(TextUtils.TruncateAt.END); description.setPadding(dp(4),dp(8),dp(4),0); panel.addView(description,new LinearLayout.LayoutParams(-1,0,1));
        }
        LinearLayout controls=new LinearLayout(activity); controls.setGravity(Gravity.CENTER_VERTICAL); controls.setPadding(dp(4),0,dp(4),0);
        pause=Ui.button(activity,"Ⅱ"); pause.setId(View.generateViewId()); pause.setContentDescription("Wstrzymaj podgląd"); pause.setTextSize(18); Ui.ghost(pause,Ui.TEXT); controls.addView(pause,new LinearLayout.LayoutParams(dp(48),dp(48)));
        pause.setOnClickListener(v -> { if(player==null) { start(); return; } if(player.getPlayerError()!=null) { recovery.retryNow(); } else if(player.getPlayWhenReady()) player.pause(); else player.play(); });
        title=Ui.text(activity,"Podgląd",television?16:14,Ui.TEXT); title.setMaxLines(television?2:1); title.setEllipsize(TextUtils.TruncateAt.END); title.setTypeface(null,android.graphics.Typeface.BOLD); controls.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button internet=Ui.button(activity,television?"Bufor":"⚙"); internet.setTextSize(television?12:16); internet.setContentDescription("Ustawienia słabszego internetu"); Ui.ghost(internet,Ui.MUTED); controls.addView(internet,new LinearLayout.LayoutParams(dp(television?60:48),dp(48)));
        internet.setOnClickListener(v -> PlaybackSettings.choose(activity,() -> { release(); start(); selectionChanged.run(); }));
        full=Ui.button(activity,television?"Ekran":"⤢"); full.setTextSize(television?13:23); full.setContentDescription("Otwórz podgląd na pełnym ekranie"); Ui.ghost(full,Ui.TEXT); controls.addView(full,new LinearLayout.LayoutParams(dp(television?80:48),dp(48)));
        full.setOnClickListener(v -> { if(channel!=null) fullscreen.accept(channel); });
        panel.addView(controls,new LinearLayout.LayoutParams(-1,dp(48)));
        if(television) { title.setTextSize(13); title.setTypeface(null,android.graphics.Typeface.NORMAL); title.setTextColor(Ui.MUTED); }
        schedule=Ui.text(activity,"",television?14:12,Ui.MUTED); schedule.setMaxLines(television?4:wide?2:1); schedule.setEllipsize(TextUtils.TruncateAt.END); schedule.setPadding(dp(12),0,dp(12),dp(8));
        schedule.setContentDescription("Aktualny program podglądu"); schedule.setMinHeight(dp(24)); if(!television) panel.addView(schedule,new LinearLayout.LayoutParams(-1,-2));
        schedule.setOnClickListener(v -> { if(channel!=null) GuideDialog.open(activity,channel,Runnable::run,c -> { if(c.kind().equals("tv")) select(c); else fullscreen.accept(c); }); });
        panel.setVisibility(View.GONE);
    }
    private int dp(int n) { return Ui.dp(activity,n); }
    private TextView channelLabel;
    /** Preview card width on TV: about 40% of the screen, as in the receiver skin. */
    int televisionWidth() { float screen=activity.getResources().getDisplayMetrics().widthPixels/activity.getResources().getDisplayMetrics().density; return Math.round(Math.max(300,Math.min(520,(screen-64)*0.40f))); }
    Channel selected() { return channel; }
    View controls() { return pause; }
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
        channel=selected; remembered=selected.url(); title.setText(television?"OK pełny ekran · Menu opcje":"Na żywo · "+selected.name()); if(channelLabel!=null) channelLabel.setText(selected.name()); full.setEnabled(true); pause.setEnabled(true);
        if(active && visible) { if(user && player!=null && player.getPlayerError()!=null && same) { recovery.retryNow(); } else start(); }
        rememberSelection();
        updateGuide(); if(active && visible) { ui.removeCallbacks(guideTick); ui.postDelayed(guideTick,1000); } if(user) selectionChanged.run();
    }
    void guideChanged(String url) { if(channel!=null && channel.url().equals(url)) updateGuide(); }
    private void updateGuide() {
        if(channel==null || !visible) return;
        GuideRepository repository=GuideRepository.get(activity); GuideRepository.Data data=repository.peek(channel,false);
        if(data!=null) { guide=data; schedule.setText(GuideRepository.summary(data,System.currentTimeMillis()/1000)); showProgramme(data); return; }
        showProgramme(guide);
        schedule.setText(guide==null?(television?"Program TV · Menu na kanale → Program TV i archiwum":"Program TV · dotknij, aby sprawdzić EPG"):GuideRepository.summary(guide,System.currentTimeMillis()/1000));
        // The channel rows already fetch provider EPG; the preview only observes their cache and EIT.
    }
    private void showProgramme(GuideRepository.Data data) {
        if(programTitle==null) return;
        long now=System.currentTimeMillis()/1000; Guide.Program current=null,next=null;
        if(data!=null) for(Guide.Program program:data.programs()) { if(program.current(now)) current=program; else if(program.start()>now && (next==null || program.start()<next.start())) next=program; }
        java.time.format.DateTimeFormatter clock=java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault());
        programTitle.setText(current!=null?current.title():data==null?"Wczytywanie programu…":"Brak programu TV dla tego kanału");
        times.setText(current==null?"":clock.format(java.time.Instant.ofEpochSecond(current.start()))+"  –  "+clock.format(java.time.Instant.ofEpochSecond(current.end())));
        remaining.setText(current==null?"":"Do końca: "+Math.max(1,(current.end()-now+59)/60)+" min");
        progress.setVisibility(current==null?View.INVISIBLE:View.VISIBLE);
        if(current!=null) progress.setProgress((int)Math.max(0,Math.min(1000,(now-current.start())*1000/Math.max(1,current.end()-current.start()))));
        String about=current!=null && current.description()!=null?current.description().trim():"";
        description.setText(!about.isEmpty()?about:next!=null?"Następnie "+clock.format(java.time.Instant.ofEpochSecond(next.start()))+"  "+next.title():"");
    }
    void resume() { active=true; if(player!=null && weak!=PlaybackSettings.weakInternet(activity)) release(); start(); ui.removeCallbacks(guideTick); ui.post(guideTick); }
    void pause() { active=false; ui.removeCallbacks(guideTick); release(); }
    void destroy() { pause(); ui.removeCallbacksAndMessages(null); }
    private void start() {
        if(!active || !visible || channel==null || player!=null || activity.isFinishing()) return;
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Context context=activity.getApplicationContext(); Channel current=channel; GuideRepository repository=GuideRepository.get(context);
        ExoPlayer.Builder builder=new ExoPlayer.Builder(context,PlaybackAudio.renderers(context));
        player=builder.setLoadControl(PlaybackSettings.loadControl(context)).setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true).setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(PlaybackNetwork.sources(context,current)).build();
        repository.streamStarted(current); observing=true; weak=PlaybackSettings.weakInternet(context);
        player.setTrackSelectionParameters(PlaybackSettings.tracks(context,player.getTrackSelectionParameters())); attach();
        if(television) player.setTrackSelectionParameters(PlaybackAudio.tracks(context,player.getTrackSelectionParameters()));
        MediaItem.Builder item=new MediaItem.Builder().setUri(current.url()); String path=android.net.Uri.parse(current.url()).getPath(); if(path!=null && path.endsWith(".ts")) item.setMimeType(MimeTypes.VIDEO_MP2T);
        player.setMediaItem(item.build()); player.prepare(); player.play();
    }
    private void attach() { listener=events; player.addListener(listener); video.setPlayer(player); recovery=new PlaybackRecovery(player,true,text -> { notice.setText(text); notice.setVisibility(text.isEmpty()?View.GONE:View.VISIBLE); }); updatePause(); }
    private void updatePause() { boolean playing=player!=null && player.getPlayWhenReady(); pause.setText(playing?"Ⅱ":"▶"); pause.setContentDescription(playing?"Wstrzymaj podgląd":"Wznów podgląd"); }
    String handoff() {
        if(player==null || channel==null) return null;
        closeRecovery(); ExoPlayer transferring=player; video.setPlayer(null); if(listener!=null) transferring.removeListener(listener); player=null; observing=false; listener=null;
        return PlaybackHandoff.offer(activity,transferring,channel);
    }
    void receive(String token,Channel returned) {
        PlaybackHandoff.Transfer transfer=PlaybackHandoff.take(token,returned.url());
        if(transfer==null) { select(returned); return; }
        release(); activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); channel=returned; remembered=returned.url(); guide=null; player=transfer.player(); observing=true; weak=PlaybackSettings.weakInternet(activity);
        title.setText(television?"OK pełny ekran · Menu opcje":"Na żywo · "+returned.name()); if(channelLabel!=null) channelLabel.setText(returned.name()); rememberSelection(); attach(); updateGuide(); selectionChanged.run();
    }
    private void release() {
        closeRecovery();
        if(!television) activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(player!=null) { video.setPlayer(null); if(listener!=null) player.removeListener(listener); player.release(); player=null; listener=null; }
        if(observing && channel!=null) { GuideRepository.get(activity).streamStopped(channel); observing=false; }
        notice.setVisibility(View.GONE);
    }
    private void closeRecovery() { if(recovery!=null) { recovery.close(); recovery=null; } }
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
