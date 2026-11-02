package pl.tvplayer;

import android.app.*;
import android.content.Context;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Window brightness and media volume; no global brightness settings are modified. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class PlaybackAdjustments {
    static final int PASS=0,START=1,CONSUME=2;
    private final Activity activity;
    private final AudioManager audio;
    private final TextView feedback;
    private final Runnable hide;
    private float downX,downY,initialBrightness;
    private int initialVolume;
    private boolean candidate,active,brightnessGesture;
    PlaybackAdjustments(Activity activity,FrameLayout root,Bundle state) {
        this.activity=activity; audio=(AudioManager)activity.getSystemService(Context.AUDIO_SERVICE);
        activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        if(state!=null && state.containsKey("playback_brightness")) brightness(state.getFloat("playback_brightness"));
        feedback=new TextView(activity); feedback.setTextColor(-1); feedback.setTextSize(18); feedback.setGravity(Gravity.CENTER);
        feedback.setPadding(dp(20),dp(14),dp(20),dp(14)); feedback.setElevation(dp(8)); feedback.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable(); shape.setColor(0xE6202125); shape.setCornerRadius(dp(18)); feedback.setBackground(shape);
        feedback.setVisibility(View.GONE); FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER); root.addView(feedback,size);
        hide=() -> feedback.setVisibility(View.GONE);
    }
    private int dp(int value) { return Math.round(value*activity.getResources().getDisplayMetrics().density); }
    private float brightness() {
        float value=activity.getWindow().getAttributes().screenBrightness;
        if(value<0) value=Settings.System.getInt(activity.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,128)/255f;
        return Math.max(0.03f,Math.min(1f,value));
    }
    private void brightness(float value) {
        WindowManager.LayoutParams attrs=activity.getWindow().getAttributes(); float bounded=Math.max(0.03f,Math.min(1f,value)); if(Math.abs(attrs.screenBrightness-bounded)<0.005f) return; attrs.screenBrightness=bounded; activity.getWindow().setAttributes(attrs);
    }
    private int volume() { return audio==null?0:audio.getStreamVolume(AudioManager.STREAM_MUSIC); }
    private int maxVolume() { return audio==null?1:Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)); }
    private void volume(int value) {
        if(audio==null || audio.isVolumeFixed()) return;
        int bounded=Math.max(0,Math.min(maxVolume(),value));
        if(bounded!=volume()) try { audio.setStreamVolume(AudioManager.STREAM_MUSIC,bounded,0); } catch(SecurityException ignored) {}
    }
    private void showFeedback(String value) {
        if(!value.contentEquals(feedback.getText())) feedback.setText(value);
        feedback.setVisibility(View.VISIBLE); feedback.removeCallbacks(hide); feedback.postDelayed(hide,900);
    }
    private boolean controlsAt(View root,int x,int y) {
        if(!root.isShown()) return false;
        Rect bounds=new Rect(); if(!root.getGlobalVisibleRect(bounds) || !bounds.contains(x,y)) return false;
        if(root instanceof Button || root instanceof ImageButton || root instanceof SeekBar || root instanceof androidx.media3.ui.TimeBar) return true;
        if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) if(controlsAt(((ViewGroup)root).getChildAt(i),x,y)) return true;
        return false;
    }
    int touch(MotionEvent event,View video,View toolbar,boolean enabled) {
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN) {
            active=false; candidate=false;
            Rect bounds=new Rect(); if(!enabled || !video.getGlobalVisibleRect(bounds)) return PASS;
            int x=(int)event.getRawX(),y=(int)event.getRawY();
            // Keep system edge gestures, transport buttons and the toolbar available.
            if(x<bounds.left+dp(24) || x>bounds.right-dp(24) || y<bounds.top+dp(24) || y>bounds.bottom-dp(24) || controlsAt(video,x,y)) return PASS;
            Rect top=new Rect(); if(toolbar.isShown() && toolbar.getGlobalVisibleRect(top) && top.contains(x,y)) return PASS;
            downX=event.getRawX(); downY=event.getRawY(); brightnessGesture=x<bounds.centerX(); initialBrightness=brightness(); initialVolume=volume(); candidate=true;
        } else if(action==MotionEvent.ACTION_POINTER_DOWN) { candidate=false; return active?CONSUME:PASS;
        } else if(action==MotionEvent.ACTION_MOVE && candidate) {
            float dy=downY-event.getRawY(),dx=event.getRawX()-downX; boolean started=false;
            if(!active) {
                if(Math.abs(dx)>dp(12) && Math.abs(dx)>Math.abs(dy)) { candidate=false; return PASS; }
                if(Math.abs(dy)<dp(12) || Math.abs(dy)<Math.abs(dx)*1.3f) return PASS;
                active=true; started=true;
            }
            float change=dy/Math.max(dp(120),video.getHeight())*1.5f;
            if(brightnessGesture) { brightness(initialBrightness+change); showFeedback("Jasność  "+Math.round(brightness()*100)+"%"); }
            else { volume(initialVolume+Math.round(change*maxVolume())); showFeedback(audio!=null && audio.isVolumeFixed()?"Głośność reguluje urządzenie":"Głośność  "+Math.round(100f*volume()/maxVolume())+"%"); }
            return started?START:CONSUME;
        } else if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL) {
            boolean consumed=active; active=false; candidate=false; return consumed?CONSUME:PASS;
        }
        return active?CONSUME:PASS;
    }
    void showDialog() {
        LinearLayout body=new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(24),dp(12),dp(24),dp(8));
        slider(body,"Głośność",volume(),maxVolume(),audio!=null && !audio.isVolumeFixed(),value -> volume(value));
        slider(body,"Jasność",Math.round(brightness()*100),100,true,value -> brightness(value/100f));
        TextView hint=new TextView(activity); hint.setText("Przesuń w górę lub w dół po obrazie: po lewej zmienisz jasność, po prawej głośność."); hint.setPadding(0,dp(12),0,0); body.addView(hint);
        new AlertDialog.Builder(activity).setTitle("Obraz i dźwięk").setView(body).setPositiveButton("Gotowe",null).setNeutralButton("Jasność telefonu",(d,w) -> {
            WindowManager.LayoutParams attrs=activity.getWindow().getAttributes(); attrs.screenBrightness=WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; activity.getWindow().setAttributes(attrs);
        }).show();
    }
    private void slider(LinearLayout body,String name,int value,int max,boolean enabled,java.util.function.IntConsumer change) {
        TextView label=new TextView(activity); label.setTextSize(16); label.setText(name+"  "+Math.round(100f*value/max)+"%"); body.addView(label);
        SeekBar bar=new SeekBar(activity); bar.setContentDescription(name); bar.setMax(max); bar.setProgress(value); bar.setEnabled(enabled); bar.setMinimumHeight(dp(48)); body.addView(bar,new LinearLayout.LayoutParams(-1,dp(52)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seek,int progress,boolean fromUser) { if(fromUser) { change.accept(progress); int actual=name.equals("Jasność")?Math.round(brightness()*100):Math.round(100f*volume()/maxVolume()); label.setText(name+"  "+actual+"%"); } }
            public void onStartTrackingTouch(SeekBar seek) {}
            public void onStopTrackingTouch(SeekBar seek) {}
        });
    }
    void save(Bundle out) { float value=activity.getWindow().getAttributes().screenBrightness; if(value>=0) out.putFloat("playback_brightness",value); }
    void dismissFeedback() { feedback.removeCallbacks(hide); feedback.setVisibility(View.GONE); candidate=false; active=false; }
}
