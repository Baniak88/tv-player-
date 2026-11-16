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
import android.view.View;

/** OKTV palette: deep navy surfaces, one vivid coral-red accent and a cyan highlight for progress. */
final class Ui {
    static final String APP_NAME = "OKTV Player", BRAND = "OKTV";
    // Neutral charcoal rather than navy: on backlit LED TVs dark blue glows as a bright blue sheet.
    static final int BG = 0xFF0B0C0F, SURFACE = 0xFF17191E, SURFACE_HIGH = 0xFF22252C, BORDER = 0xFF33363F,
        TEXT = 0xFFF4F5F7, MUTED = 0xFFA4A8B2, FAINT = 0xFF6C707A, ON_ACCENT = 0xFFFFFFFF, ACCENT = 0xFFFF3D5E,
        HIGHLIGHT = 0xFF35D0FF, STAR = 0xFFFFC53D, DANGER = 0xFFFF6B6B;
    /** Minimal TV palette (receiver-skin style): graphite cards, one teal selection and a warm amber accent. */
    static final int TV_CARD = 0xFF161A1F, TV_LINE = 0xFF262B31;
    /** Selection and accent colours of the chosen theme (see DisplaySettings). */
    static int TV_SELECT = 0xFF2F3A4D, TV_GOLD = 0xFFF5C518;
    /** English interface (DisplaySettings); Polish is the default and the source language. */
    static volatile boolean english;
    private static final int R_SOURCE = 0x7f0a0ff0, R_SHOWN = 0x7f0a0ff1;
    private Ui() { }

    static String tr(String text) { return english ? Translations.english(text) : text; }

    /** Translates every label in a window and keeps doing so as screens change; a no-op in Polish. */
    static void localize(View root) {
        if (!english || root == null) return;
        translateTree(root);
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> translateTree(root));
    }
    private static void translateTree(View view) {
        if (view instanceof android.widget.EditText) return;
        if (view instanceof TextView) {
            TextView label = (TextView) view; CharSequence current = label.getText();
            if (current != null && current.length() > 0) {
                String text = current.toString();
                if (!text.equals(label.getTag(R_SHOWN))) { String translated = Translations.english(text); label.setTag(R_SOURCE, text); label.setTag(R_SHOWN, translated); if (!translated.equals(text)) label.setText(translated); }
            }
            CharSequence description = view.getContentDescription();
            if (description != null) { String translated = Translations.english(description.toString()); if (!translated.contentEquals(description)) view.setContentDescription(translated); }
        }
        if (view instanceof android.view.ViewGroup) { android.view.ViewGroup group = (android.view.ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) translateTree(group.getChildAt(i)); }
    }

    /** AlertDialog builder that translates its texts and list rows when English is chosen. */
    static final class Dialog extends android.app.AlertDialog.Builder {
        Dialog(Context context) { super(context); }
        Dialog(Context context, int theme) { super(context, theme); }
        @Override public Dialog setTitle(CharSequence title) { super.setTitle(title == null ? null : tr(title.toString())); return this; }
        @Override public Dialog setMessage(CharSequence message) { super.setMessage(message == null ? null : tr(message.toString())); return this; }
        @Override public Dialog setItems(CharSequence[] items, android.content.DialogInterface.OnClickListener listener) { super.setItems(translated(items), listener); return this; }
        @Override public Dialog setSingleChoiceItems(CharSequence[] items, int checked, android.content.DialogInterface.OnClickListener listener) { super.setSingleChoiceItems(translated(items), checked, listener); return this; }
        @Override public Dialog setPositiveButton(CharSequence text, android.content.DialogInterface.OnClickListener listener) { super.setPositiveButton(text == null ? null : tr(text.toString()), listener); return this; }
        @Override public Dialog setNegativeButton(CharSequence text, android.content.DialogInterface.OnClickListener listener) { super.setNegativeButton(text == null ? null : tr(text.toString()), listener); return this; }
        @Override public Dialog setNeutralButton(CharSequence text, android.content.DialogInterface.OnClickListener listener) { super.setNeutralButton(text == null ? null : tr(text.toString()), listener); return this; }
        @Override public android.app.AlertDialog create() { android.app.AlertDialog dialog = super.create(); if (english) dialog.setOnShowListener(d -> { if (dialog.getWindow() != null) localize(dialog.getWindow().getDecorView()); }); return dialog; }
        private static CharSequence[] translated(CharSequence[] items) { if (!english || items == null) return items; CharSequence[] copy = new CharSequence[items.length]; for (int i = 0; i < items.length; i++) copy[i] = items[i] == null ? null : Translations.english(items[i].toString()); return copy; }
    }

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
        // Minimal TV buttons: white primary with dark text, graphite secondary; focus is a clear amber/white frame.
        Context context=button.getContext(); int color=primary?TEXT:SURFACE_HIGH;
        StateListDrawable states=new StateListDrawable();
        GradientDrawable focused=shape(context,primary?TEXT:TV_SELECT,12,0); focused.setStroke(dp(context,3),primary?TV_GOLD:TEXT);
        states.addState(new int[]{android.R.attr.state_pressed},shape(context,primary?0xFFDADDE2:lighter(color),12,TEXT));
        states.addState(new int[]{android.R.attr.state_focused},focused);
        states.addState(new int[]{},shape(context,color,12,primary?0:TV_LINE));
        button.setTextColor(buttonColors(primary?BG:TEXT)); button.setTypeface(null,android.graphics.Typeface.BOLD);
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

