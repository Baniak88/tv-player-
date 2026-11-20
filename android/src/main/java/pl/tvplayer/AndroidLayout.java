package pl.tvplayer;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;

/** Android screen sizing; the Fire Stick source and build do not include this class. */
final class AndroidLayout {
    static boolean portrait(Context context) { return context.getResources().getConfiguration().orientation==Configuration.ORIENTATION_PORTRAIT; }
    static int width(Context context) { return Math.round(context.getResources().getDisplayMetrics().widthPixels/context.getResources().getDisplayMetrics().density); }
    static void insets(View root) {
        root.setOnApplyWindowInsetsListener((view,insets) -> {
            int left=insets.getSystemWindowInsetLeft(),top=insets.getSystemWindowInsetTop(),right=insets.getSystemWindowInsetRight(),bottom=insets.getSystemWindowInsetBottom();
            if(Build.VERSION.SDK_INT>=28 && insets.getDisplayCutout()!=null) { android.view.DisplayCutout cutout=insets.getDisplayCutout(); left=Math.max(left,cutout.getSafeInsetLeft()); top=Math.max(top,cutout.getSafeInsetTop()); right=Math.max(right,cutout.getSafeInsetRight()); bottom=Math.max(bottom,cutout.getSafeInsetBottom()); }
            view.setPadding(left,top,right,bottom); return insets;
        });
        root.requestApplyInsets();
    }
}
