package pl.tvplayer;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

/** Android-only input adapter; all screens and their layout remain the Fire Stick originals. */
public final class AndroidApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity,Bundle state) {
                View root=activity.getWindow().getDecorView();
                root.getViewTreeObserver().addOnGlobalLayoutListener(() -> touchTargets(root));
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
}
