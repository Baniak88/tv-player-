package pl.tvplayer;

import android.app.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.ui.PlayerView;
import java.util.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class PlayerActivity extends Activity {
    private ExoPlayer player;
    private Player.Listener playbackListener;
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
        Button b=new Button(this); b.setText(text); b.setTextColor(-1); b.setTextSize(13); b.setAllCaps(false);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(10),0,dp(10),0);
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();
        shape.setColor(0x33FFFFFF); shape.setCornerRadius(dp(12)); b.setBackground(shape);
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
        root.addView(view,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this); toolbar=top; top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(10),dp(24),dp(10),dp(6)); top.setBackgroundColor(0x99000000);
        Button back=action("‹"); back.setContentDescription("Wróć do biblioteki"); top.addView(back,new LinearLayout.LayoutParams(dp(46),dp(44))); back.setOnClickListener(v -> finish());
        title=new TextView(this); title.setText(getIntent().getStringExtra("name")); title.setTextColor(-1); title.setTextSize(15); title.setMaxLines(2); title.setPadding(dp(10),0,dp(6),0); top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button settings=action("Audio / CC"); top.addView(settings,new LinearLayout.LayoutParams(dp(92),dp(44)));
        settings.setText("Opcje"); settings.setOnClickListener(v -> showOptions());
        full=action("↙"); full.setContentDescription("Włącz lub wyłącz pełny ekran"); top.addView(full,new LinearLayout.LayoutParams(dp(46),dp(44)));
        full.setOnClickListener(v -> { fullscreen=!fullscreen; updateFullscreen(); });
        root.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        if(television) full.setVisibility(View.GONE);
        // The top bar follows the player controls everywhere: films and series hide it together with the controls, like live TV.
        view.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener)(visibility -> { if(!isInPictureInPictureMode()) toolbar.setVisibility(visibility); }));
        if(onDemand && !television) view.setControllerShowTimeoutMs(4000);
        if(!television && !onDemand) {
            view.setShowPreviousButton(true); view.setShowNextButton(true); view.setShowRewindButton(false); view.setShowFastForwardButton(false);
            view.setControllerShowTimeoutMs(4000);
            view.setControllerAutoShow(false);
            view.setControllerHideOnTouch(false);
        }
        error=new LinearLayout(this); error.setOrientation(LinearLayout.VERTICAL); error.setGravity(Gravity.CENTER);
        error.setBackgroundColor(0xEE16171A); error.setPadding(dp(24),dp(20),dp(24),dp(20));
        message=new TextView(this); message.setTextColor(-1); message.setTextSize(17); error.addView(message);
        Button retry=action("Spróbuj ponownie"); error.addView(retry);
        retry.setOnClickListener(v -> { if(player!=null) { error.setVisibility(View.GONE); player.prepare(); player.play(); } });
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
        if(!television && !onDemand) loadChannelQueue();
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
        if(television || onDemand || channelQueue==null || !resumed || isFinishing() || isDestroyed() || enteringPip || isInPictureInPictureMode()) return; Channel channel=channelQueue.move(offset); if(channel==null) return;
        // Stop observation while the intent still identifies the old channel.
        if(player!=null) { view.setPlayer(null); player.release(); player=null; } stopStreamObservation();
        url=channel.url(); position=0;
        getIntent().putExtra("url",url).putExtra("name",channel.name()).putExtra("kind","tv").putExtra("group",channel.group()).putExtra("logo",channel.logo()).putExtra("guide",channel.guideUrl()).putExtra("epgId",channel.epgId()).putExtra("days",channel.archiveDays()).putExtra("timezone",channel.timezone());
        title.setText(channel.name()); error.setVisibility(View.GONE); startPlayback(); revealLiveControls();
    }
    private void restoreImmersive() {
        if(isFinishing() || !fullscreen || enteringPip || isInPictureInPictureMode()) return;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if(hasFocus) { restoreImmersive(); revealLiveControls(); } }
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
        if(view==null || television || onDemand || !resumed || isFinishing() || enteringPip || isInPictureInPictureMode()) return;
        view.showController();
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if(adjustments!=null) {
            int result=adjustments.touch(event,view,toolbar,resumed && fullscreen && !enteringPip && !isInPictureInPictureMode() && !error.isShown());
            if(result==PlaybackAdjustments.START) { MotionEvent cancel=MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL); super.dispatchTouchEvent(cancel); cancel.recycle(); }
            if(result!=PlaybackAdjustments.PASS) return true;
        }
        // Let the tapped view finish its click before controls can change focus or visibility.
        boolean handled=super.dispatchTouchEvent(event);
        if(event.getActionMasked()==MotionEvent.ACTION_UP && view!=null && !television && !onDemand) view.post(this::revealLiveControls);
        return handled;
    }
    @Override public void onConfigurationChanged(Configuration config) { super.onConfigurationChanged(config); if(!isInPictureInPictureMode()) { updateFullscreen(); revealLiveControls(); } }
    private void showOptions() {
        List<String> labels=new ArrayList<>(Arrays.asList("Ścieżka audio","Napisy","Odtwórz od początku","Obraz w obrazie (PiP)"));
        if(!onDemand) { labels.add("Program TV i archiwum"); labels.add(RecordingService.active?"Zatrzymaj nagrywanie":"Nagraj kanał do pliku"); }
        labels.add("Cofnij o 30 sekund");
        if(adjustments!=null) labels.add("Głośność i jasność");
        if(!television && !onDemand) labels.add("Połączenie internetowe");
        new AlertDialog.Builder(this).setTitle("Odtwarzanie").setItems(labels.toArray(new String[0]),(d,i) -> {
            if(labels.get(i).equals("Połączenie internetowe")) { PlaybackSettings.choose(this,() -> {
                boolean playing=player!=null && player.getPlayWhenReady();
                if(player!=null) { view.setPlayer(null); player.release(); player=null; } stopStreamObservation(); error.setVisibility(View.GONE); startPlayback(); if(player!=null && !playing) player.pause();
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
        }).show();
    }
    private void openGuide() {
        GuideDialog.open(this,liveChannel(),guideWorker,c -> {
            if(c.kind().equals("tv")) { if(player!=null) { player.seekToDefaultPosition(); player.play(); } return; }
            startActivity(new android.content.Intent(this,PlayerActivity.class).putExtra("url",c.url()).putExtra("name",c.name()).putExtra("kind",c.kind()));
        });
    }
    private void enterPip() {
        if(player==null) return;
        if(android.os.Build.VERSION.SDK_INT<26 || !getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toast.makeText(this,"Ten telefon nie udostępnia PiP.",Toast.LENGTH_LONG).show(); return;
        }
        enteringPip=true; if(adjustments!=null) adjustments.dismissFeedback(); if(rotation!=null) rotation.disable();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        try {
            boolean entered=enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new android.util.Rational(16,9)).build());
            if(!entered) { enteringPip=false; updateFullscreen(); Toast.makeText(this,"Włącz zgodę Obraz w obrazie w ustawieniach aplikacji.",Toast.LENGTH_LONG).show(); }
        } catch(IllegalStateException | IllegalArgumentException e) { enteringPip=false; updateFullscreen(); Toast.makeText(this,"Nie można włączyć PiP. Sprawdź ustawienia aplikacji.",Toast.LENGTH_LONG).show(); }
    }
    @Override public void onPictureInPictureModeChanged(boolean inPip,Configuration config) {
        super.onPictureInPictureModeChanged(inPip,config); toolbar.setVisibility(inPip?View.GONE:View.VISIBLE); view.setUseController(!inPip);
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
                    startActivityForResult(new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("video/mp2t")
                        .putExtra(android.content.Intent.EXTRA_TITLE,"TV-"+System.currentTimeMillis()+".ts"),RECORD_FILE);
                }).show()).setNegativeButton("Anuluj",null).show();
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
    @Override protected void onPause() { resumed=false; if(adjustments!=null) adjustments.dismissFeedback(); saveWatchProgress(); if(rotation!=null) rotation.disable(); super.onPause(); }
    @Override protected void onStart() { super.onStart(); startPlayback(); }
    private void startPlayback() {
        if(url==null) return;
        if(player!=null) return;
        String token=getIntent().getStringExtra("playback_handoff"); getIntent().removeExtra("playback_handoff");
        PlaybackHandoff.Transfer transfer=!television && !onDemand?PlaybackHandoff.take(token,url):null;
        if(transfer!=null) { player=transfer.player(); streamObserved=true; }
        else {
            ExoPlayer.Builder builder=new ExoPlayer.Builder(!television && !onDemand?getApplicationContext():this);
            builder.setLoadControl(!television && !onDemand?PlaybackSettings.loadControl(this):new DefaultLoadControl.Builder().setBackBuffer(30000,true).build());
            if(!television && !onDemand) builder.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true).setHandleAudioBecomingNoisy(true);
            if(!onDemand) {
                Channel channel=liveChannel(); GuideRepository repository=GuideRepository.get(this);
                androidx.media3.datasource.DefaultDataSource.Factory upstream=new androidx.media3.datasource.DefaultDataSource.Factory(!television?getApplicationContext():this);
                builder.setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(() -> new EitDataSource(upstream.createDataSource(),channel,repository)));
                repository.streamStarted(channel); streamObserved=true;
            }
            player=builder.build();
        }
        if(!television && !onDemand) player.setTrackSelectionParameters(PlaybackSettings.tracks(this,player.getTrackSelectionParameters()));
        final ExoPlayer current=player; view.setPlayer(!television && !onDemand?channelControls(player):player);
        if(!television && !onDemand) describeChannelButtons();
        playbackListener=new Player.Listener() {
            @Override public void onPlayerError(PlaybackException e) {
                if(player!=current) return;
                message.setText("Nie można odtworzyć materiału. Sprawdź internet, dostępność źródła lub kodeki telefonu.\nKod: "+e.getErrorCodeName());
                error.setVisibility(View.VISIBLE);
            }
        }; player.addListener(playbackListener);
        if(transfer!=null) return;
        MediaItem.Builder item=new MediaItem.Builder().setUri(url);
        String path=android.net.Uri.parse(url).getPath();
        if(path!=null && path.endsWith(".ts")) item.setMimeType(MimeTypes.VIDEO_MP2T);
        player.setMediaItem(item.build()); player.seekTo(position); player.prepare(); player.play();
        if(onDemand && position>5000) Toast.makeText(this,"Wznawiam oglądanie. Od początku: Opcje.",Toast.LENGTH_LONG).show();
    }
    @Override public void finish() {
        if(!television && !onDemand && player!=null && getIntent().getBooleanExtra("return_preview",false)) {
            ExoPlayer transferring=player; view.setPlayer(null); if(playbackListener!=null) transferring.removeListener(playbackListener); player=null; streamObserved=false;
            String token=PlaybackHandoff.offer(this,transferring,liveChannel());
            setResult(RESULT_OK,new android.content.Intent().putExtras(getIntent().getExtras()).putExtra("url",url).putExtra("playback_handoff",token));
        }
        super.finish();
    }
    @Override public boolean onKeyUp(int code,KeyEvent event) {
        if(television && code==KeyEvent.KEYCODE_MENU) { showOptions(); return true; }
        if(television && code==KeyEvent.KEYCODE_INFO && !onDemand) { openGuide(); return true; }
        if(television && code==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && player!=null) { if(player.isPlaying()) player.pause(); else player.play(); return true; }
        if(television && code==KeyEvent.KEYCODE_MEDIA_STOP) { finish(); return true; }
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
            view.setPlayer(null); player.release(); player=null; stopStreamObservation();
        }
        super.onStop();
    }
    @Override protected void onDestroy() {
        if(adjustments!=null) adjustments.dismissFeedback();
        if(player!=null) { saveWatchProgress(); view.setPlayer(null); player.release(); player=null; }
        stopStreamObservation();
        if(rotation!=null) rotation.disable(); guideWorker.shutdown(); super.onDestroy();
    }
    private void stopStreamObservation() { if(streamObserved) { GuideRepository.get(this).streamStopped(liveChannel()); streamObserved=false; } }
    @Override protected void onSaveInstanceState(Bundle out) {
        if(adjustments!=null) adjustments.save(out);
        if(!television && !onDemand && getIntent().getExtras()!=null) out.putBundle("live_channel",new Bundle(getIntent().getExtras()));
        out.putLong("position",player==null?position:player.getCurrentPosition()); out.putBoolean("fullscreen",fullscreen); super.onSaveInstanceState(out);
    }
}



