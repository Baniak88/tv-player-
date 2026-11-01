package pl.tvplayer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.Button;
import android.widget.TextView;

/** Minimal graphite palette shared by every screen. */
final class Ui {
    static final int BG = 0xFF0E0F11, SURFACE = 0xFF16171A, SURFACE_HIGH = 0xFF1E2024, BORDER = 0xFF2A2C31,
        TEXT = 0xFFEDEEF0, MUTED = 0xFF8E939C, FAINT = 0xFF5F636B, ON_ACCENT = 0xFF0E0F11, ACCENT = 0xFFF2F3F5,
        STAR = 0xFFF2C14E, DANGER = 0xFFFF6B6B;
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
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(dp(c, 40)); b.setMinimumHeight(dp(c, 40));
        b.setPadding(dp(c, 12), 0, dp(c, 12), 0); style(b, false); return b;
    }

    /** Selected = light pill with dark text; otherwise quiet text on the surface. */
    static void style(Button b, boolean selected) {
        Context c = b.getContext();
        b.setTextColor(selected ? ON_ACCENT : MUTED);
        b.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        b.setBackground(touch(c, selected ? ACCENT : 0x00000000, 10, 0));
    }

    static void primary(Button b) {
        b.setTextColor(ON_ACCENT); b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setBackground(touch(b.getContext(), ACCENT, 12, 0));
    }

    static void ghost(Button b, int textColor) {
        b.setTextColor(textColor); b.setTypeface(null, android.graphics.Typeface.NORMAL);
        b.setBackground(touch(b.getContext(), 0x00000000, 10, 0));
    }

    static String count(int n) {
        return String.format(new java.util.Locale("pl", "PL"), "%,d", n).replace(',', ' ').replace('.', ' ');
    }
}
