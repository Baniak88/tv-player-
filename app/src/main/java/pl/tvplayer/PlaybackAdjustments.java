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
    private final LinearLayout feedback;
    private final TextView feedbackLabel;
    private final ProgressBar feedbackLevel;
    private final Runnable hide;
    private float downX,downY,initialBrightness;
    private int initialVolume;
    private boolean candidate,active,brightnessGesture;
    PlaybackAdjustments(Activity activity,FrameLayout root,Bundle state) {
        this.activity=activity; audio=(AudioManager)activity.getSystemService(Context.AUDIO_SERVICE);
        activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        if(state!=null && state.containsKey("playback_brightness")) brightness(state.getFloat("playback_brightness"));
        // Gesture feedback: label with percentage and a coloured level bar.
        feedback=new LinearLayout(activity); feedback.setOrientation(LinearLayout.VERTICAL); feedback.setGravity(Gravity.CENTER);
        feedback.setPadding(dp(22),dp(14),dp(22),dp(16)); feedback.setElevation(dp(8)); feedback.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        feedback.setBackground(Ui.shape(activity,0xE6141A27,20,0x40FFFFFF));
        feedbackLabel=new TextView(activity); feedbackLabel.setTextColor(Ui.TEXT); feedbackLabel.setTextSize(17); feedbackLabel.setTypeface(null,android.graphics.Typeface.BOLD); feedbackLabel.setGravity(Gravity.CENTER);
        feedback.addView(feedbackLabel);
        feedbackLevel=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal); feedbackLevel.setMax(100);
        feedbackLevel.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT)); feedbackLevel.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x55FFFFFF));
        LinearLayout.LayoutParams levelSize=new LinearLayout.LayoutParams(dp(170),dp(8)); levelSize.topMargin=dp(10); feedback.addView(feedbackLevel,levelSize);
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
    private void showFeedback(String value,int percent) {
        if(!value.contentEquals(feedbackLabel.getText())) feedbackLabel.setText(value);
        feedbackLevel.setVisibility(percent<0?View.GONE:View.VISIBLE); if(percent>=0) feedbackLevel.setProgress(percent);
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
            if(brightnessGesture) { brightness(initialBrightness+change); int level=Math.round(brightness()*100); showFeedback("☀  Jasność  "+level+"%",level); }
            else { volume(initialVolume+Math.round(change*maxVolume())); int level=Math.round(100f*volume()/maxVolume()); if(audio!=null && audio.isVolumeFixed()) showFeedback("Głośność reguluje urządzenie",-1); else showFeedback((level==0?"🔇":"🔊")+"  Głośność  "+level+"%",level); }
            return started?START:CONSUME;
        } else if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL) {
            boolean consumed=active; active=false; candidate=false; return consumed?CONSUME:PASS;
        }
        return active?CONSUME:PASS;
    }
    void showDialog() {
        LinearLayout body=new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(20),dp(8),dp(20),dp(4));
        slider(body,"🔊","Głośność",volume(),maxVolume(),audio!=null && !audio.isVolumeFixed(),value -> volume(value),() -> Math.round(100f*volume()/maxVolume()));
        slider(body,"☀","Jasność",Math.round(brightness()*100),100,true,value -> brightness(value/100f),() -> Math.round(brightness()*100));
        TextView hint=new TextView(activity); hint.setTextColor(Ui.MUTED); hint.setTextSize(13); hint.setLineSpacing(0,1.15f);
        hint.setText("Wskazówka: przesuń palcem w górę lub w dół po obrazie — po lewej stronie jasność, po prawej głośność."); hint.setPadding(0,dp(14),0,0); body.addView(hint);
        new AlertDialog.Builder(activity).setTitle("Obraz i dźwięk").setView(body).setPositiveButton("Gotowe",null).setNeutralButton("Jasność telefonu",(d,w) -> {
            WindowManager.LayoutParams attrs=activity.getWindow().getAttributes(); attrs.screenBrightness=WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; activity.getWindow().setAttributes(attrs);
        }).show();
    }
    /** One setting row: icon, name and live percentage, then − [slider] + with large touch targets. */
    private void slider(LinearLayout body,String icon,String name,int value,int max,boolean enabled,java.util.function.IntConsumer change,java.util.function.IntSupplier actual) {
        LinearLayout head=new LinearLayout(activity); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(0,dp(14),0,dp(2));
        TextView title=new TextView(activity); title.setTextSize(16); title.setTextColor(Ui.TEXT); title.setTypeface(null,android.graphics.Typeface.BOLD); title.setText(icon+"  "+name+(enabled?"":" · reguluje urządzenie"));
        head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView percent=new TextView(activity); percent.setTextSize(16); percent.setTextColor(Ui.ACCENT); percent.setTypeface(null,android.graphics.Typeface.BOLD); percent.setText(Math.round(100f*value/max)+"%");
        head.addView(percent); body.addView(head);
        LinearLayout line=new LinearLayout(activity); line.setGravity(Gravity.CENTER_VERTICAL);
        SeekBar bar=new SeekBar(activity); bar.setContentDescription(name); bar.setMax(max); bar.setProgress(value); bar.setEnabled(enabled); bar.setMinimumHeight(dp(48));
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT)); bar.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x55FFFFFF)); bar.setPadding(dp(14),0,dp(14),0);
        Runnable refresh=() -> percent.setText(actual.getAsInt()+"%");
        Button minus=step(name,"−",-1), plus=step(name,"+",1);
        int stepSize=Math.max(1,Math.round(max/10f));
        minus.setOnClickListener(v -> { int next=Math.max(0,bar.getProgress()-stepSize); bar.setProgress(next); change.accept(next); refresh.run(); });
        plus.setOnClickListener(v -> { int next=Math.min(max,bar.getProgress()+stepSize); bar.setProgress(next); change.accept(next); refresh.run(); });
        minus.setEnabled(enabled); plus.setEnabled(enabled);
        line.addView(minus,new LinearLayout.LayoutParams(dp(44),dp(44))); line.addView(bar,new LinearLayout.LayoutParams(0,dp(52),1)); line.addView(plus,new LinearLayout.LayoutParams(dp(44),dp(44)));
        body.addView(line);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seek,int progress,boolean fromUser) { if(fromUser) { change.accept(progress); refresh.run(); } }
            public void onStartTrackingTouch(SeekBar seek) {}
            public void onStopTrackingTouch(SeekBar seek) {}
        });
    }
    private Button step(String name,String sign,int direction) {
        Button b=new Button(activity); b.setText(sign); b.setTextSize(20); b.setTextColor(Ui.TEXT); b.setAllCaps(false); b.setStateListAnimator(null); b.setPadding(0,0,0,0);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0); b.setBackground(Ui.touch(activity,Ui.SURFACE_HIGH,22,0));
        b.setContentDescription((direction<0?"Zmniejsz ":"Zwiększ ")+name.toLowerCase(java.util.Locale.ROOT)); return b;
    }
    void save(Bundle out) { float value=activity.getWindow().getAttributes().screenBrightness; if(value>=0) out.putFloat("playback_brightness",value); }
    void dismissFeedback() { feedback.removeCallbacks(hide); feedback.setVisibility(View.GONE); candidate=false; active=false; }
}
