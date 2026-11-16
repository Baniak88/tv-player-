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
    private Player.Listener playbackListener;
    private PlaybackRecovery recovery;
    private PlayerView view;
    private LinearLayout error;
    private TextView message,title;
    private LiveChannelQueue channelQueue;
    private PlaybackAdjustments adjustments;
    private Button full;
    private String url, bookmark;
    private long position;
    private boolean fullscreen=true, onDemand, resumed, enteringPip, television;
    private OrientationEventListener rotation;
    private int sensorOrientation=ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
    private LinearLayout toolbar;
    private TvLiveInfoBar liveInfo;
    private final android.os.Handler infoHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable hideInfo=() -> setLiveInfoVisible(false);
    private final java.util.concurrent.ExecutorService guideWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final int RECORD_FILE=72;
    private int recordingMinutes=60;
    private boolean streamObserved;
    private Channel liveChannel() {
        return new Channel(getIntent().getStringExtra("name"),url,getIntent().getStringExtra("group"),getIntent().getStringExtra("epgId"),onDemand?"movie":"tv",
            getIntent().getStringExtra("guide"),getIntent().getIntExtra("days",0),getIntent().getStringExtra("timezone"));
    }
    private int dp(int value) { return (int)(value*getResources().getDisplayMetrics().density); }
    private Button action(String text) {
        Button b=new Button(this); b.setText(text); b.setTextColor(-1); b.setTextSize(14); b.setAllCaps(false); b.setStateListAnimator(null);
        b.setTypeface(null,android.graphics.Typeface.BOLD); b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(dp(44)); b.setMinimumHeight(dp(44)); b.setPadding(dp(12),0,dp(12),0);
        b.setBackground(Ui.touch(this,0x52000000,22,0x40FFFFFF));
        return b;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(state!=null && state.getBundle("live_channel")!=null) getIntent().replaceExtras(state.getBundle("live_channel"));
        url=getIntent().getStringExtra("url"); if(url==null) { finish(); return; }
        television=getIntent().getBooleanExtra("television",false) || (getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK)==Configuration.UI_MODE_TYPE_TELEVISION;
        if(television) sensorOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        String kind=getIntent().getStringExtra("kind");
        onDemand=kind!=null?!kind.equals("tv"):!Channel.infer(url,"","").equals("tv");
        try {
            byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            bookmark=android.util.Base64.encodeToString(hash,android.util.Base64.NO_WRAP);
        } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        position=onDemand?getPreferences(MODE_PRIVATE).getLong(bookmark,0):0;
        if(onDemand) { WatchProgress.Entry saved=WatchProgress.read(this,url); if(saved.updated()>0) position=saved.completed()?0:saved.position(); }
        if(state!=null) { position=state.getLong("position"); fullscreen=state.getBoolean("fullscreen",true); }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SECURE);
        FrameLayout root=new FrameLayout(this); root.setBackgroundColor(0xFF000000);
        view=new PlayerView(this); view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        View progressBar=view.findViewById(androidx.media3.ui.R.id.exo_progress);
        if(progressBar instanceof androidx.media3.ui.DefaultTimeBar) {
            androidx.media3.ui.DefaultTimeBar timeBar=(androidx.media3.ui.DefaultTimeBar)progressBar;
            timeBar.setPlayedColor(Ui.ACCENT); timeBar.setScrubberColor(Ui.ACCENT); timeBar.setBufferedColor(0x80FFFFFF); timeBar.setUnplayedColor(0x40FFFFFF); timeBar.setAdMarkerColor(Ui.HIGHLIGHT);
        }
        root.addView(view,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this); toolbar=top; top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(12),dp(24),dp(12),dp(18));
        // Soft fade instead of a hard black band, so the picture stays visible behind the controls.
        top.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xD9000000,0x80000000,0x00000000}));
        Button back=action("‹"); back.setContentDescription("Wróć do biblioteki"); top.addView(back,new LinearLayout.LayoutParams(dp(46),dp(44))); back.setOnClickListener(v -> finish());
        title=new TextView(this); title.setText(getIntent().getStringExtra("name")); title.setTextColor(-1); title.setTextSize(15); title.setMaxLines(2); title.setPadding(dp(10),0,dp(6),0); top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button settings=action("Audio / CC"); top.addView(settings,new LinearLayout.LayoutParams(dp(92),dp(44)));
        settings.setText("Opcje"); settings.setOnClickListener(v -> showOptions());
        full=action("↙"); full.setContentDescription("Włącz lub wyłącz pełny ekran"); top.addView(full,new LinearLayout.LayoutParams(dp(46),dp(44)));
        full.setOnClickListener(v -> { fullscreen=!fullscreen; updateFullscreen(); });
        root.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        if(television) {
            full.setVisibility(View.GONE);
            // TVs crop ~5% of each edge (overscan): keep the title bar and the playback controls inside the safe area.
            top.setPadding(dp(48),dp(28),dp(48),dp(18));
            View controller=view.findViewById(androidx.media3.ui.R.id.exo_controller);
            if(controller!=null) controller.setPadding(dp(48),dp(24),dp(48),dp(28));
            if(progressBar instanceof androidx.media3.ui.DefaultTimeBar) { androidx.media3.ui.DefaultTimeBar timeBar=(androidx.media3.ui.DefaultTimeBar)progressBar; timeBar.setPlayedColor(Ui.TV_GOLD); timeBar.setScrubberColor(Ui.TV_GOLD); }
        }
        if(television && !onDemand) {
            view.setUseController(false); back.setFocusable(false); settings.setFocusable(false);
            liveInfo=new TvLiveInfoBar(this); liveInfo.channel(getIntent().getStringExtra("name")); liveInfo.logo(getIntent().getStringExtra("logo"));
            liveInfo.guide(null); liveInfo.audio("Audio: oczekiwanie na ścieżkę");
            root.addView(liveInfo,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        }
        // The top bar follows the player controls everywhere: films and series hide it together with the controls, like live TV.
        view.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener)(visibility -> { if(liveInfo==null && !isInPictureInPictureMode()) toolbar.setVisibility(visibility); }));
        if(onDemand && !television) view.setControllerShowTimeoutMs(4000);
        if(!television && !onDemand) {
            view.setShowPreviousButton(true); view.setShowNextButton(true); view.setShowRewindButton(false); view.setShowFastForwardButton(false);
            view.setControllerShowTimeoutMs(4000);
            view.setControllerAutoShow(false);
            // Same as films and series: a tap shows the bars, the next tap (or 4 s) hides them.
            view.setControllerHideOnTouch(true);
        }
        error=new LinearLayout(this); error.setOrientation(LinearLayout.VERTICAL); error.setGravity(Gravity.CENTER);
        error.setBackgroundColor(0xEE141A27); error.setPadding(dp(24),dp(20),dp(24),dp(20));
        message=new TextView(this); message.setTextColor(-1); message.setTextSize(17); error.addView(message);
        Button retry=action("Spróbuj ponownie"); error.addView(retry);
        retry.setOnClickListener(v -> { if(player!=null) { error.setVisibility(View.GONE); if(recovery!=null) recovery.retryNow(); else { player.prepare(); player.play(); } } });
        error.setVisibility(View.GONE); root.addView(error,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        rotation=new OrientationEventListener(this,android.hardware.SensorManager.SENSOR_DELAY_NORMAL) {
            @Override public void onOrientationChanged(int angle) {
                if(television || !resumed || !fullscreen || enteringPip || isInPictureInPictureMode()) return;
                int requested=FullscreenRotation.requested(angle,sensorOrientation);
                if(requested!=sensorOrientation) { sensorOrientation=requested; setRequestedOrientation(requested); revealLiveControls(); }
            }
        };
        if(!television) adjustments=new PlaybackAdjustments(this,root,state);
        setContentView(root); updateFullscreen();
        if(!onDemand) loadChannelQueue();
        // The system may drop the fullscreen flags (focus change, dialogs, rotation); restore them.
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(visibility -> {
            if(fullscreen && (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN)==0) root.postDelayed(this::restoreImmersive,1500);
        });
    }
    private void loadChannelQueue() {
        channelQueue=LiveChannelQueue.restore(getIntent().getStringExtra("navigation_session"),url); if(channelQueue!=null) return;
        final String selected=url; final boolean explicit=getIntent().getBooleanExtra("navigation_scope",false);
        guideWorker.execute(() -> {
            try {
                Set<String> favorites=new HashSet<>(); List<Channel> channels=new LibraryStore(this).loadSection("tv",favorites); String group=getIntent().getStringExtra("navigation_group");
                if(!explicit) { group=""; for(Channel channel:channels) if(channel.url().equals(selected)) { group=channel.group(); break; } }
                LiveChannelQueue restored=LiveChannelQueue.rebuild(channels,favorites,group,getIntent().getStringExtra("navigation_query"),getIntent().getBooleanExtra("navigation_favorites",false),selected);
                runOnUiThread(() -> { if(isDestroyed() || !selected.equals(url)) return; channelQueue=restored; if(player!=null && restored.canNavigate()) { view.setPlayer(channelControls(player)); describeChannelButtons(); revealLiveControls(); } });
            } catch(Exception ignored) { /* Direct streams stay playable without a saved navigation list. */ }
        });
    }
    private Player channelControls(ExoPlayer delegate) {
        return new ForwardingPlayer(delegate) {
            private boolean navigationCommand(int command) { return command==Player.COMMAND_SEEK_TO_PREVIOUS || command==Player.COMMAND_SEEK_TO_NEXT || command==Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM || command==Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM; }
            @Override public boolean isCommandAvailable(int command) { return navigationCommand(command)?channelQueue!=null && channelQueue.canNavigate():super.isCommandAvailable(command); }
            @Override public Player.Commands getAvailableCommands() {
                Player.Commands.Builder commands=super.getAvailableCommands().buildUpon();
                for(int command:new int[]{Player.COMMAND_SEEK_TO_PREVIOUS,Player.COMMAND_SEEK_TO_NEXT,Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM}) { if(channelQueue!=null && channelQueue.canNavigate()) commands.add(command); else commands.remove(command); } return commands.build();
            }
            @Override public void seekToPrevious() { changeChannel(-1); }
            @Override public void seekToPreviousMediaItem() { changeChannel(-1); }
            @Override public void seekToNext() { changeChannel(1); }
            @Override public void seekToNextMediaItem() { changeChannel(1); }
        };
    }
    private void describeChannelButtons() {
        View previous=view.findViewById(androidx.media3.ui.R.id.exo_prev),next=view.findViewById(androidx.media3.ui.R.id.exo_next);
        if(previous!=null) previous.setContentDescription("Poprzedni kanał"); if(next!=null) next.setContentDescription("Następny kanał");
    }
    private void changeChannel(int offset) {
        if(onDemand || channelQueue==null || !resumed || isFinishing() || isDestroyed() || enteringPip || isInPictureInPictureMode()) return; Channel channel=channelQueue.move(offset); if(channel==null) return;
        // One player: stop the old source before opening the next and retain the decoder/audio output.
        ExoPlayer reusable=player;
        if(player!=null) { closeRecovery(); view.setPlayer(null); if(playbackListener!=null) player.removeListener(playbackListener); player.stop(); player=null; } stopStreamObservation();
        url=channel.url(); position=0;
        getIntent().putExtra("url",url).putExtra("name",channel.name()).putExtra("kind","tv").putExtra("group",channel.group()).putExtra("logo",channel.logo()).putExtra("guide",channel.guideUrl()).putExtra("epgId",channel.epgId()).putExtra("days",channel.archiveDays()).putExtra("timezone",channel.timezone());
        title.setText(channel.name()); error.setVisibility(View.GONE); startPlayback(reusable); revealLiveControls();
        if(liveInfo!=null) { liveInfo.channel(channel.name()); liveInfo.logo(channel.logo()); liveInfo.guide(null); showLiveInfo(); }
    }
    private void restoreImmersive() {
        if(isFinishing() || !fullscreen || enteringPip || isInPictureInPictureMode()) return;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if(hasFocus) { restoreImmersive(); if(liveInfo==null) revealLiveControls(); } }
    private void updateFullscreen() {
        if(television) { fullscreen=true; sensorOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; }
        if(enteringPip || isInPictureInPictureMode()) return;
        setRequestedOrientation(fullscreen?sensorOrientation:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if(rotation!=null) { if(!television && fullscreen && resumed && !enteringPip && !isInPictureInPictureMode()) rotation.enable(); else rotation.disable(); }
        int flags=fullscreen?View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION:0;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        FrameLayout.LayoutParams size=(FrameLayout.LayoutParams)view.getLayoutParams();
        size.height=fullscreen?-1:dp(240); size.gravity=Gravity.CENTER; view.setLayoutParams(size);
        full.setText(fullscreen?"↙":"⤢");
    }
    private void revealLiveControls() {
        if(liveInfo!=null) { showLiveInfo(); return; }
        if(view==null || television || onDemand || !resumed || isFinishing() || enteringPip || isInPictureInPictureMode()) return;
        view.showController();
    }
    private void setLiveInfoVisible(boolean visible) {
        if(liveInfo==null) return;
        // The receiver-style infobar carries the channel name and key hints; the touch toolbar (whose buttons a remote cannot reach) stays hidden.
        liveInfo.setVisibility(visible?View.VISIBLE:View.GONE); toolbar.setVisibility(View.GONE);
        infoHandler.removeCallbacks(hideInfo);
    }
    private void showLiveInfo() {
        if(liveInfo==null || !resumed || isFinishing() || enteringPip || isInPictureInPictureMode()) return;
        setLiveInfoVisible(true); liveInfo.update();
        if(player!=null) liveInfo.audio(PlaybackAudio.description(player.getCurrentTracks()));
        String requestedUrl=url;
        GuideRepository.get(this).request(liveChannel(),false,data -> { if(resumed && !isDestroyed() && liveInfo!=null && requestedUrl.equals(url)) liveInfo.guide(data); });
        infoHandler.postDelayed(hideInfo,6500);
    }
    @Override public void onBackPressed() { if(liveInfo!=null && liveInfo.getVisibility()==View.VISIBLE) { setLiveInfoVisible(false); return; } super.onBackPressed(); }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if(adjustments!=null) {
            int result=adjustments.touch(event,view,toolbar,resumed && fullscreen && !enteringPip && !isInPictureInPictureMode() && !error.isShown());
            if(result==PlaybackAdjustments.START) { MotionEvent cancel=MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL); super.dispatchTouchEvent(cancel); cancel.recycle(); }
            if(result!=PlaybackAdjustments.PASS) return true;
        }
        // Let the tapped view finish its click before controls can change focus or visibility.
        boolean handled=super.dispatchTouchEvent(event);
        // Live TV taps are handled by the player view itself (toggle), exactly like films and series.
        return handled;
    }
    @Override public void onConfigurationChanged(Configuration config) { super.onConfigurationChanged(config); if(!isInPictureInPictureMode()) { updateFullscreen(); revealLiveControls(); } }
    private void showOptions() {
        List<String> labels=new ArrayList<>(Arrays.asList("Ścieżka audio","Napisy","Odtwórz od początku","Obraz w obrazie (PiP)"));
        if(!onDemand) { labels.add("Program TV i archiwum"); labels.add(RecordingService.active?"Zatrzymaj nagrywanie":"Nagraj kanał do pliku"); }
        labels.add("Cofnij o 30 sekund");
        if(adjustments!=null) labels.add("Głośność i jasność");
        labels.add("Połączenie internetowe");
        if(television) labels.add("Zgodność dźwięku");
        AlertDialog options=new AlertDialog.Builder(this,television?android.R.style.Theme_Material_Dialog_Alert:0).setTitle(television?null:"Opcje odtwarzania").setAdapter(optionRows(labels),(d,i) -> {
            if(labels.get(i).equals("Zgodność dźwięku")) { chooseAudioOutput(); return; }
            if(labels.get(i).equals("Połączenie internetowe")) { PlaybackSettings.choose(this,() -> {
                boolean playing=player!=null && player.getPlayWhenReady();
                TrackSelectionParameters selections=player==null?null:player.getTrackSelectionParameters();
                if(player!=null && onDemand) position=player.getCurrentPosition();
                if(player!=null) { closeRecovery(); view.setPlayer(null); player.release(); player=null; } stopStreamObservation(); error.setVisibility(View.GONE); startPlayback(); if(player!=null && !playing) player.pause();
                if(player!=null && selections!=null) player.setTrackSelectionParameters(PlaybackSettings.tracks(this,selections));
            }); return; }
            if(labels.get(i).equals("Głośność i jasność")) { adjustments.showDialog(); return; }
            if(labels.get(i).equals("Cofnij o 30 sekund")) {
                if(player!=null && player.isCurrentMediaItemSeekable()) player.seekTo(Math.max(0,player.getCurrentPosition()-30000));
                else Toast.makeText(this,"Źródło nie udostępnia przewijania. Dla TV sprawdź archiwum w EPG.",Toast.LENGTH_LONG).show();
                return;
            }
            switch(i) {
                case 0: chooseTrack(C.TRACK_TYPE_AUDIO); break;
                case 1: chooseTrack(C.TRACK_TYPE_TEXT); break;
                case 2:
                    if(!onDemand) openGuide(); else if(player!=null) player.seekTo(0);
                    break;
                case 3: enterPip(); break;
                case 4: openGuide(); break;
                case 5: if(RecordingService.active) stopService(new android.content.Intent(this,RecordingService.class)); else chooseRecording(); break;
            }
        }).create();
        options.show();
        if(television && options.getWindow()!=null) {
            // TV: a compact panel docked on the right, full height and scrollable, so every option fits inside the overscan-safe area.
            android.view.Window window=options.getWindow(); window.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
            window.setLayout(dp(400),getResources().getDisplayMetrics().heightPixels-dp(48));
            window.setBackgroundDrawable(Ui.shape(this,Ui.TV_CARD,16,Ui.TV_LINE)); window.setDimAmount(.45f);
            ListView list=options.getListView(); if(list!=null) { list.setDivider(new android.graphics.drawable.ColorDrawable(Ui.TV_LINE)); list.setDividerHeight(Math.max(1,dp(1))); list.setPadding(0,dp(8),0,dp(8)); list.setClipToPadding(false); }
        }
    }
    private void chooseAudioOutput() {
        boolean previous=PlaybackAudio.pcm(this);
        new AlertDialog.Builder(this).setTitle("Zgodność dźwięku")
            .setSingleChoiceItems(new String[]{"Automatycznie · HDMI / Dolby","Stereo PCM · gdy brak dźwięku"},previous?1:0,(dialog,index) -> {
                dialog.dismiss(); if(previous==(index==1)) return;
                PlaybackAudio.pcm(this,index==1);
                if(player==null) return;
                boolean playing=player.getPlayWhenReady(); position=onDemand?player.getCurrentPosition():0;
                TrackSelectionParameters tracks=player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,false)
                    .setMaxAudioChannelCount(Integer.MAX_VALUE).setPreferredAudioMimeTypes().build();
                closeRecovery(); view.setPlayer(null); player.release(); player=null; stopStreamObservation(); error.setVisibility(View.GONE);
                startPlayback(); player.setTrackSelectionParameters(PlaybackAudio.tracks(this,PlaybackSettings.tracks(this,tracks))); if(!playing) player.pause();
            }).setNegativeButton("Zamknij",null).show();
    }
    /** Options as large rows with an icon and a one-line hint; labels stay exact for accessibility services. */
    private ListAdapter optionRows(List<String> labels) {
        return new BaseAdapter() {
            public int getCount() { return labels.size(); }
            public Object getItem(int p) { return labels.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p,View reused,ViewGroup parent) {
                LinearLayout row;
                if(reused instanceof LinearLayout) row=(LinearLayout)reused;
                else {
                    row=new LinearLayout(PlayerActivity.this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(television?18:20),dp(television?6:10),dp(television?18:20),dp(television?6:10)); row.setMinimumHeight(dp(television?52:60));
                    TextView icon=new TextView(PlayerActivity.this); icon.setGravity(Gravity.CENTER); icon.setTextSize(television?14:16); icon.setTextColor(television?Ui.TV_GOLD:Ui.ACCENT); icon.setTypeface(null,android.graphics.Typeface.BOLD);
                    icon.setBackground(Ui.shape(PlayerActivity.this,television?0x1AFFFFFF:0x26FF3D5E,20,0)); row.addView(icon,new LinearLayout.LayoutParams(dp(television?34:40),dp(television?34:40)));
                    LinearLayout copy=new LinearLayout(PlayerActivity.this); copy.setOrientation(LinearLayout.VERTICAL); copy.setPadding(dp(16),0,0,0);
                    TextView label=new TextView(PlayerActivity.this); label.setTextSize(television?15:16); label.setTextColor(Ui.TEXT); copy.addView(label);
                    TextView hint=new TextView(PlayerActivity.this); hint.setTextSize(12); hint.setTextColor(Ui.MUTED); hint.setSingleLine(true); copy.addView(hint);
                    row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
                }
                String label=labels.get(p);
                ((TextView)row.getChildAt(0)).setText(optionIcon(label));
                LinearLayout copy=(LinearLayout)row.getChildAt(1); ((TextView)copy.getChildAt(0)).setText(label);
                String hint=optionHint(label); TextView hintView=(TextView)copy.getChildAt(1); hintView.setText(hint); hintView.setVisibility(hint.isEmpty()?View.GONE:View.VISIBLE);
                return row;
            }
        };
    }
    private static String optionIcon(String label) {
        switch(label) {
            case "Ścieżka audio": return "♪"; case "Napisy": return "CC"; case "Odtwórz od początku": return "⟲"; case "Obraz w obrazie (PiP)": return "▣";
            case "Program TV i archiwum": return "☰"; case "Nagraj kanał do pliku": return "●"; case "Zatrzymaj nagrywanie": return "■";
            case "Cofnij o 30 sekund": return "30"; case "Głośność i jasność": return "☀"; case "Połączenie internetowe": return "⇅"; default: return "•";
        }
    }
    private String optionHint(String label) {
        switch(label) {
            case "Ścieżka audio": return "Język i lektor"; case "Napisy": return "Włącz, wyłącz lub zmień język";
            case "Odtwórz od początku": return onDemand?"Zacznij od pierwszej minuty":"Program TV i powtórki";
            case "Obraz w obrazie (PiP)": return "Oglądaj w małym oknie"; case "Program TV i archiwum": return "EPG i oglądanie od początku";
            case "Głośność i jasność": return "Suwaki dla tego odtwarzania"; case "Połączenie internetowe": return PlaybackSettings.weakInternet(this)?"Teraz: słabszy internet":"Teraz: standard";
            default: return "";
        }
    }
    private void openGuide() {
        GuideDialog.open(this,liveChannel(),guideWorker,c -> {
            if(c.kind().equals("tv")) { if(player!=null) { player.seekToDefaultPosition(); player.play(); } return; }
            startActivity(new android.content.Intent(this,PlayerActivity.class).putExtra("television",television).putExtra("url",c.url()).putExtra("name",c.name()).putExtra("kind",c.kind()));
        });
    }
    private void enterPip() {
        if(player==null) return;
        if(android.os.Build.VERSION.SDK_INT<26 || !getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toast.makeText(this,"To urządzenie nie udostępnia PiP.",Toast.LENGTH_LONG).show(); return;
        }
        enteringPip=true; if(adjustments!=null) adjustments.dismissFeedback(); if(rotation!=null) rotation.disable();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        try {
            boolean entered=enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new android.util.Rational(16,9)).build());
            if(!entered) { enteringPip=false; updateFullscreen(); Toast.makeText(this,"Włącz zgodę Obraz w obrazie w ustawieniach aplikacji.",Toast.LENGTH_LONG).show(); }
        } catch(IllegalStateException | IllegalArgumentException e) { enteringPip=false; updateFullscreen(); Toast.makeText(this,"Nie można włączyć PiP. Sprawdź ustawienia aplikacji.",Toast.LENGTH_LONG).show(); }
    }
    @Override public void onPictureInPictureModeChanged(boolean inPip,Configuration config) {
        super.onPictureInPictureModeChanged(inPip,config); toolbar.setVisibility(inPip?View.GONE:View.VISIBLE); view.setUseController(!inPip && liveInfo==null);
        if(liveInfo!=null) setLiveInfoVisible(false);
        enteringPip=false; if(inPip && rotation!=null) rotation.disable();
        if(!inPip) { updateFullscreen(); revealLiveControls(); }
    }
    private void chooseRecording() {
        String path=android.net.Uri.parse(url).getPath();
        if(path==null || !path.endsWith(".ts")) { Toast.makeText(this,"Nagrywanie tej wersji obsługuje bezpośrednie kanały MPEG-TS.",Toast.LENGTH_LONG).show(); return; }
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},73);
        new AlertDialog.Builder(this).setTitle("Czas nagrania").setMessage("Nagranie otwiera dodatkowe połączenie do dostawcy. Wybierz czas i plik.")
            .setPositiveButton("Dalej",(dialog,which) -> new AlertDialog.Builder(this).setTitle("Maksymalny czas")
                .setItems(new String[]{"30 minut","60 minut","120 minut"},(d,index) -> {
                    recordingMinutes=new int[]{30,60,120}[index];
                    if(!requestRecordingFile(this,RECORD_FILE)) Toast.makeText(this,"System nie udostępnia wyboru pliku. Nagrywanie wymaga systemowego menedżera dokumentów.",Toast.LENGTH_LONG).show();
                }).show()).setNegativeButton("Anuluj",null).show();
    }
    static boolean requestRecordingFile(Activity activity,int request) {
        try {
            activity.startActivityForResult(new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("video/mp2t")
                .putExtra(android.content.Intent.EXTRA_TITLE,"TV-"+System.currentTimeMillis()+".ts"),request);
            return true;
        } catch(android.content.ActivityNotFoundException | SecurityException unavailable) { return false; }
    }
    @Override protected void onActivityResult(int request,int result,android.content.Intent data) {
        super.onActivityResult(request,result,data);
        if(request==RECORD_FILE && result==RESULT_OK && data!=null && data.getData()!=null) {
            try {
                android.content.Intent service=new android.content.Intent(this,RecordingService.class).setData(data.getData())
                    .putExtra("url",url).putExtra("name",getIntent().getStringExtra("name")).putExtra("minutes",recordingMinutes);
                if(android.os.Build.VERSION.SDK_INT>=26) startForegroundService(service); else startService(service); Toast.makeText(this,"Rozpoczynam nagrywanie. Zatrzymasz je w Opcjach lub powiadomieniu.",Toast.LENGTH_LONG).show();
            } catch(RuntimeException e) { Toast.makeText(this,"Nie można uruchomić nagrywania. Sprawdź uprawnienia i plik.",Toast.LENGTH_LONG).show(); }
        }
    }
    @Override protected void onResume() { super.onResume(); resumed=true; if(!isInPictureInPictureMode()) { updateFullscreen(); revealLiveControls(); } }
    @Override protected void onPause() { resumed=false; infoHandler.removeCallbacks(hideInfo); if(adjustments!=null) adjustments.dismissFeedback(); saveWatchProgress(); if(rotation!=null) rotation.disable(); super.onPause(); }
    @Override protected void onStart() { super.onStart(); startPlayback(); }
    private void startPlayback() { startPlayback(null); }
    private void startPlayback(ExoPlayer reusable) {
        if(url==null) return;
        if(player!=null) return;
        String token=getIntent().getStringExtra("playback_handoff"); getIntent().removeExtra("playback_handoff");
        PlaybackHandoff.Transfer transfer=!onDemand?PlaybackHandoff.take(token,url):null;
        if(reusable!=null) { player=reusable; GuideRepository.get(this).streamStarted(liveChannel()); streamObserved=true; }
        else if(transfer!=null) { player=transfer.player(); streamObserved=true; }
        else {
            android.content.Context playbackContext=!television && !onDemand?getApplicationContext():this;
            ExoPlayer.Builder builder=new ExoPlayer.Builder(playbackContext,PlaybackAudio.renderers(this));
            builder.setLoadControl(PlaybackSettings.loadControl(this));
            builder.setMediaSourceFactory(PlaybackNetwork.sources(this,onDemand?null:liveChannel()));
            if(television || !onDemand) builder.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true).setHandleAudioBecomingNoisy(true);
            player=builder.build();
            GuideRepository.get(this).streamStarted(liveChannel()); streamObserved=true;
        }
        player.setTrackSelectionParameters(PlaybackSettings.tracks(this,player.getTrackSelectionParameters()));
        if(television) player.setTrackSelectionParameters(PlaybackAudio.tracks(this,player.getTrackSelectionParameters()));
        final ExoPlayer current=player; view.setPlayer(!television && !onDemand?channelControls(player):player);
        if(!television && !onDemand) describeChannelButtons();
        playbackListener=new Player.Listener() {
            @Override public void onTracksChanged(Tracks tracks) { if(player==current && liveInfo!=null) liveInfo.audio(PlaybackAudio.description(tracks)); }
            @Override public void onPlayerError(PlaybackException e) {
                if(player!=current) return;
                message.setText("Nie można odtworzyć materiału. Sprawdź internet, dostępność źródła lub kodeki urządzenia.\nKod: "+e.getErrorCodeName());
                error.setVisibility(View.VISIBLE);
            }
        }; player.addListener(playbackListener);
        recovery=new PlaybackRecovery(player,!onDemand,text -> {
            if(player!=current) return;
            message.setText(text); error.setVisibility(text.isEmpty()?View.GONE:View.VISIBLE);
        });
        if(transfer!=null) return;
        MediaItem.Builder item=new MediaItem.Builder().setUri(url);
        String path=android.net.Uri.parse(url).getPath();
        if(path!=null && path.endsWith(".ts")) item.setMimeType(MimeTypes.VIDEO_MP2T);
        player.setMediaSource(PlaybackNetwork.sources(this,onDemand?null:liveChannel()).createMediaSource(item.build()));
        if(onDemand) player.seekTo(position); else player.seekToDefaultPosition(); player.prepare(); player.play();
        if(onDemand && position>5000) Toast.makeText(this,"Wznawiam oglądanie. Od początku: Opcje.",Toast.LENGTH_LONG).show();
    }
    @Override public void finish() {
        if(!onDemand && player!=null && getIntent().getBooleanExtra("return_preview",false)) {
            closeRecovery(); ExoPlayer transferring=player; view.setPlayer(null); if(playbackListener!=null) transferring.removeListener(playbackListener); player=null; streamObserved=false;
            String token=PlaybackHandoff.offer(this,transferring,liveChannel());
            setResult(RESULT_OK,new android.content.Intent().putExtras(getIntent().getExtras()).putExtra("url",url).putExtra("playback_handoff",token));
        }
        super.finish();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code=event.getKeyCode();
        if(television && !onDemand && (code==KeyEvent.KEYCODE_CHANNEL_UP || code==KeyEvent.KEYCODE_CHANNEL_DOWN || code==KeyEvent.KEYCODE_DPAD_UP || code==KeyEvent.KEYCODE_DPAD_DOWN || code==KeyEvent.KEYCODE_MEDIA_NEXT || code==KeyEvent.KEYCODE_MEDIA_PREVIOUS)) {
            if(event.getAction()==KeyEvent.ACTION_UP && !event.isCanceled()) changeChannel(code==KeyEvent.KEYCODE_CHANNEL_UP || code==KeyEvent.KEYCODE_DPAD_UP || code==KeyEvent.KEYCODE_MEDIA_NEXT?1:-1);
            return true;
        }
        // Fire TV remotes deliver Menu to whichever view holds focus, so handle it here instead of relying on onKeyUp.
        // Right/Left are free during live TV (Up/Down zap), so they give one-press access to options and the guide.
        if(television && (code==KeyEvent.KEYCODE_MENU || code==KeyEvent.KEYCODE_SETTINGS || liveInfo!=null && (code==KeyEvent.KEYCODE_DPAD_RIGHT || code==KeyEvent.KEYCODE_DPAD_LEFT || code==KeyEvent.KEYCODE_GUIDE))) {
            if(event.getAction()==KeyEvent.ACTION_UP && !event.isCanceled()) { if(code==KeyEvent.KEYCODE_DPAD_LEFT || code==KeyEvent.KEYCODE_GUIDE) openGuide(); else showOptions(); }
            return true;
        }
        if(liveInfo!=null && (code==KeyEvent.KEYCODE_MEDIA_REWIND || code==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)) {
            if(event.getAction()==KeyEvent.ACTION_UP && !event.isCanceled() && player!=null && player.isCurrentMediaItemSeekable())
                player.seekTo(Math.max(0,player.getCurrentPosition()+(code==KeyEvent.KEYCODE_MEDIA_REWIND?-10000:10000)));
            return true;
        }
        if(liveInfo!=null && (code==KeyEvent.KEYCODE_DPAD_CENTER || code==KeyEvent.KEYCODE_ENTER || code==KeyEvent.KEYCODE_INFO)) {
            if(event.getAction()==KeyEvent.ACTION_UP && !event.isCanceled()) { if(code!=KeyEvent.KEYCODE_INFO && liveInfo.getVisibility()==View.VISIBLE) setLiveInfoVisible(false); else showLiveInfo(); }
            return true;
        }
        if(television && (code==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code==KeyEvent.KEYCODE_MEDIA_PLAY || code==KeyEvent.KEYCODE_MEDIA_PAUSE || code==KeyEvent.KEYCODE_MEDIA_STOP)) {
            // Consume DOWN as well as UP before the window can send it to another media app.
            if(event.getAction()==KeyEvent.ACTION_UP && !event.isCanceled()) {
                if(code==KeyEvent.KEYCODE_MEDIA_STOP) finish();
                else if(player!=null) {
                    if(code==KeyEvent.KEYCODE_MEDIA_PAUSE || (code==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && player.getPlayWhenReady())) player.pause();
                    else player.play();
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean onKeyUp(int code,KeyEvent event) {
        if(television && code==KeyEvent.KEYCODE_MENU) { showOptions(); return true; }
        if(television && code==KeyEvent.KEYCODE_INFO && !onDemand) { openGuide(); return true; }
        return super.onKeyUp(code,event);
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
    private void saveWatchProgress() {
        if(player!=null && onDemand) { long at=player.getCurrentPosition(),duration=player.getDuration(); WatchProgress.save(this,url,at,duration,player.getPlaybackState()==Player.STATE_ENDED || (duration>0 && at>=duration-2000)); }
    }
    @Override protected void onStop() {
        if(player!=null && !isInPictureInPictureMode()) {
            position=onDemand?player.getCurrentPosition():0;
            long duration=player.getDuration();
            boolean completed=player.getPlaybackState()==Player.STATE_ENDED || (duration>0 && position>=duration-2000);
            if(onDemand) WatchProgress.save(this,url,position,duration,completed);
            if(completed) position=0;
            if(onDemand) getPreferences(MODE_PRIVATE).edit().putLong(bookmark,position).apply();
            closeRecovery(); view.setPlayer(null); player.release(); player=null; stopStreamObservation();
        }
        super.onStop();
    }
    @Override protected void onDestroy() {
        infoHandler.removeCallbacksAndMessages(null);
        if(adjustments!=null) adjustments.dismissFeedback();
        if(player!=null) { saveWatchProgress(); closeRecovery(); view.setPlayer(null); player.release(); player=null; }
        stopStreamObservation();
        if(rotation!=null) rotation.disable(); guideWorker.shutdown(); super.onDestroy();
    }
    private void stopStreamObservation() { if(streamObserved) { GuideRepository.get(this).streamStopped(liveChannel()); streamObserved=false; } }
    private void closeRecovery() { if(recovery!=null) { recovery.close(); recovery=null; } }
    @Override protected void onSaveInstanceState(Bundle out) {
        if(adjustments!=null) adjustments.save(out);
        if(!onDemand && getIntent().getExtras()!=null) out.putBundle("live_channel",new Bundle(getIntent().getExtras()));
        out.putLong("position",player==null?position:player.getCurrentPosition()); out.putBoolean("fullscreen",fullscreen); super.onSaveInstanceState(out);
    }
}



