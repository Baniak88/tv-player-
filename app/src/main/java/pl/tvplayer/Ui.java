package pl.tvplayer;

import android.content.Context;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.Button;
import android.widget.TextView;

/** OKTV palette: deep navy surfaces, one vivid coral-red accent and a cyan highlight for progress. */
final class Ui {
    static final String APP_NAME = "OKTV Player", BRAND = "OKTV";
    static final int BG = 0xFF0A0D14, SURFACE = 0xFF141A27, SURFACE_HIGH = 0xFF1C2335, BORDER = 0xFF2A3350,
        TEXT = 0xFFF3F5FA, MUTED = 0xFF9DA6BC, FAINT = 0xFF646D84, ON_ACCENT = 0xFFFFFFFF, ACCENT = 0xFFFF3D5E,
        HIGHLIGHT = 0xFF35D0FF, STAR = 0xFFFFC53D, DANGER = 0xFFFF6B6B;
    private Ui() { }

    static int dp(Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable shape(Context c, int color, float radius, int border) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c, radius));
        if (border != 0) d.setStroke(Math.max(1, dp(c, 1)), border);
        return d;
    }

    /** Background with pressed and focused (TV remote) feedback. */
    static Drawable touch(Context c, int color, float radius, int border) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, shape(c, lighter(color), radius, border == 0 ? 0 : lighter(border)));
        states.addState(new int[]{android.R.attr.state_focused}, shape(c, color, radius, ACCENT));
        states.addState(new int[]{}, shape(c, color, radius, border));
        return states;
    }

    static int lighter(int color) {
        if (Color.alpha(color) == 0) return 0x1FFFFFFF;
        return Color.rgb(Math.min(255, Color.red(color) + 22), Math.min(255, Color.green(color) + 22), Math.min(255, Color.blue(color) + 24));
    }

    static TextView text(Context c, String value, float size, int color) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t;
    }

    static Button button(Context c, String label) {
        Button b = new Button(c); b.setText(label); b.setTextSize(14); b.setAllCaps(false); b.setStateListAnimator(null);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(dp(c, 48)); b.setMinimumHeight(dp(c, 48));
        b.setPadding(dp(c, 12), 0, dp(c, 12), 0); style(b, false); return b;
    }

    /** Selected = light pill with dark text; otherwise quiet text on the surface. */
    static void style(Button b, boolean selected) {
        Context c = b.getContext();
        b.setTextColor(buttonColors(selected ? ON_ACCENT : MUTED));
        b.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        b.setBackground(touch(c, selected ? ACCENT : 0x00000000, 10, 0));
    }

    static void primary(Button b) {
        b.setTextColor(buttonColors(ON_ACCENT)); b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setBackground(touch(b.getContext(), ACCENT, 12, 0));
    }

    /** Secondary action: outlined pill on a raised surface, clearly a button but quieter than primary. */
    static void secondary(Button b) {
        b.setTextColor(buttonColors(TEXT)); b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setBackground(touch(b.getContext(), SURFACE_HIGH, 12, BORDER));
    }

    /** The remote must remain visible even on a filled coral primary button. */
    static void televisionButton(Button button,boolean primary) {
        Context context=button.getContext(); int color=primary?ACCENT:SURFACE_HIGH;
        StateListDrawable states=new StateListDrawable();
        GradientDrawable focused=shape(context,color,12,0); focused.setStroke(dp(context,3),TEXT);
        states.addState(new int[]{android.R.attr.state_pressed},shape(context,lighter(color),12,TEXT));
        states.addState(new int[]{android.R.attr.state_focused},focused);
        states.addState(new int[]{},shape(context,color,12,primary?0:BORDER));
        button.setTextColor(buttonColors(TEXT)); button.setTypeface(null,android.graphics.Typeface.BOLD);
        button.setBackground(states); button.setFocusable(true); button.setFocusableInTouchMode(true);
    }

    /** Small tinted tag-style button (e.g. EPG in channel rows). */
    static void chip(Button b) {
        b.setTextColor(buttonColors(ACCENT)); b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setBackground(touch(b.getContext(), 0x26FF3D5E, 14, 0));
    }

    static void ghost(Button b, int textColor) {
        b.setTextColor(buttonColors(textColor)); b.setTypeface(null, android.graphics.Typeface.NORMAL);
        b.setBackground(touch(b.getContext(), 0x00000000, 10, 0));
    }

    private static ColorStateList buttonColors(int enabled) { return new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{FAINT,enabled}); }
    static LinearLayout detailBar(Context context,String section,Runnable back) {
        LinearLayout bar=new LinearLayout(context); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(context,8),dp(context,2),dp(context,16),dp(context,2)); bar.setBackgroundColor(BG); bar.setClickable(true);
        Button arrow=button(context,"‹"); arrow.setTextSize(30); ghost(arrow,TEXT); arrow.setContentDescription(section.equals("Seriale")?"Wróć do seriali":"Wróć do filmów"); arrow.setOnClickListener(v -> back.run()); bar.addView(arrow,new LinearLayout.LayoutParams(dp(context,48),dp(context,48)));
        TextView title=text(context,section,16,TEXT); bar.addView(title,new LinearLayout.LayoutParams(0,-2,1)); TextView brand=text(context,BRAND,12,ACCENT); brand.setTypeface(null,android.graphics.Typeface.BOLD); brand.setLetterSpacing(.06f); bar.addView(brand); return bar;
    }
    static String count(int n) {
        return String.format(new java.util.Locale("pl", "PL"), "%,d", n).replace(',', ' ').replace('.', ' ');
    }
}

