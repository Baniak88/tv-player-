package pl.tvplayer;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Android-only input adapter; all screens and their layout remain the Fire Stick originals. */
public final class AndroidApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity,Bundle state) {
                View root=activity.getWindow().getDecorView();
                root.getViewTreeObserver().addOnGlobalLayoutListener(() -> { touchTargets(root); liveControls(activity); });
            }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityResumed(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity,Bundle state) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
    }
    private static void touchTargets(View view) {
        // TV controls can consume the first tap just to acquire focus. Android uses ordinary
        // single-tap buttons; text fields retain touch focus so the on-screen keyboard works.
        if(!(view instanceof EditText) && view.isFocusableInTouchMode()) view.setFocusableInTouchMode(false);
        if(view instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) touchTargets(group.getChildAt(i));
    }
    private static void key(Activity activity,int code) {
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));
    }
    private static void liveControls(Activity activity) {
        if(!(activity instanceof PlayerActivity) || !activity.getIntent().getBooleanExtra("television",false) || !"tv".equals(activity.getIntent().getStringExtra("kind"))) return;
        ViewGroup content=activity.findViewById(android.R.id.content);
        if(content==null || content.getChildCount()==0 || !(content.getChildAt(0) instanceof FrameLayout stage)) return;
        View existing=stage.findViewWithTag("android-live-controls");
        if(existing!=null) { if(activity.isInPictureInPictureMode()) existing.setVisibility(View.GONE); return; }
        LinearLayout controls=new LinearLayout(activity); controls.setTag("android-live-controls"); controls.setPadding(Ui.dp(activity,4),0,Ui.dp(activity,4),0);
        controls.setBackground(Ui.shape(activity,0xB3101620,12,0));
        String[] labels={"‹","−","＋","EPG","⚙","i"};
        String[] descriptions={"Wróć do listy kanałów","Poprzedni kanał","Następny kanał","Program TV","Opcje odtwarzania","Informacja o kanale"};
        int[] keys={KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_MEDIA_PREVIOUS,KeyEvent.KEYCODE_MEDIA_NEXT,KeyEvent.KEYCODE_GUIDE,KeyEvent.KEYCODE_MENU,KeyEvent.KEYCODE_INFO};
        Runnable hide=() -> controls.setVisibility(View.GONE);
        for(int i=0;i<labels.length;i++) {
            final int index=i; Button button=Ui.button(activity,labels[i]); button.setTextSize(i==3?13:20); button.setPadding(0,0,0,0); button.setContentDescription(descriptions[i]); Ui.ghost(button,Ui.TEXT);
            button.setFocusableInTouchMode(false);
            button.setOnClickListener(view -> { controls.removeCallbacks(hide); if(index==0) activity.finish(); else key(activity,keys[index]); controls.postDelayed(hide,6500); });
            controls.addView(button,new LinearLayout.LayoutParams(Ui.dp(activity,48),Ui.dp(activity,48)));
        }
        FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END); size.topMargin=Ui.dp(activity,12); size.rightMargin=Ui.dp(activity,16); stage.addView(controls,size); controls.postDelayed(hide,6500);
        // The original live-TV screen hides Media3's buttons and expects remote keys.
        // Android gets touch equivalents; tapping the picture reveals/hides them.
        stage.setOnTouchListener((view,event) -> {
            if(activity.isInPictureInPictureMode()) return false;
            if(event.getActionMasked()==MotionEvent.ACTION_UP) { controls.removeCallbacks(hide); boolean show=controls.getVisibility()!=View.VISIBLE; controls.setVisibility(show?View.VISIBLE:View.GONE); if(show) { key(activity,KeyEvent.KEYCODE_INFO); controls.postDelayed(hide,6500); } }
            return true;
        });
    }
}
