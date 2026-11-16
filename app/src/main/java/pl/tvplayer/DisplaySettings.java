package pl.tvplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.View;

/** Screen settings for TV: overscan margins, text size, accent colours and interface language. */
final class DisplaySettings {
    private static final String STORE = "display-settings";
    static final String[] MARGINS = {"Brak dodatkowego marginesu", "Mały margines (2,5%)", "Duży margines (5%)"};
    static final String[] SIZES = {"Mały tekst", "Standardowy tekst", "Duży tekst", "Bardzo duży tekst"};
    // "Standard" is slightly smaller than Android's default: TV layouts read better with more room around the text.
    private static final float[] SCALES = {.82f, .9f, 1f, 1.12f};
    static final String[] THEMES = {"Granat i żółty (domyślny)", "Morski i bursztyn", "Niebieski", "Zielony", "Fioletowy", "Czerwony", "Grafitowy"};
    /** Selection colour and accent colour for each theme. */
    private static final int[][] PALETTES = {
        {0xFF243041, 0xFFF5C518}, {0xFF1E4C52, 0xFFF2B441}, {0xFF1F3F66, 0xFF5AB0FF}, {0xFF1F4D33, 0xFF5BD68A},
        {0xFF3B2A5C, 0xFFB794FF}, {0xFF5A2027, 0xFFFF6B6B}, {0xFF3A3D44, 0xFFE6E8EC}};
    static final String[] LANGUAGES = {"Polski", "English"};
    private DisplaySettings() { }

    private static SharedPreferences store(Context context) { return context.getApplicationContext().getSharedPreferences(STORE, Context.MODE_PRIVATE); }
    static int margin(Context context) { return clamp(store(context).getInt("margin", 0), MARGINS.length); }
    static int size(Context context) { return clamp(store(context).getInt("size", 1), SIZES.length); }
    static int theme(Context context) { return clamp(store(context).getInt("theme", 0), THEMES.length); }
    static boolean english(Context context) { return store(context).getInt("language", 0) == 1; }
    private static int clamp(int value, int count) { return value < 0 || value >= count ? 0 : value; }

    /** Loads the palette and language before any view is built. */
    static void apply(Context context) {
        int[] palette = PALETTES[theme(context)]; Ui.TV_SELECT = palette[0]; Ui.TV_GOLD = palette[1];
        Ui.english = english(context);
    }

    /** Base context with the chosen text scale; used from attachBaseContext. */
    static Context wrap(Context base) {
        float scale = SCALES[size(base)]; if (scale == 1f) return base;
        Configuration config = new Configuration(base.getResources().getConfiguration()); config.fontScale = config.fontScale * scale;
        return base.createConfigurationContext(config);
    }

    /** Extra overscan padding (fraction of the screen) added around a TV page. */
    static void pad(View view, int left, int top, int right, int bottom) {
        Context context = view.getContext(); float fraction = margin(context) == 2 ? .05f : margin(context) == 1 ? .025f : 0f;
        int x = Math.round(context.getResources().getDisplayMetrics().widthPixels * fraction), y = Math.round(context.getResources().getDisplayMetrics().heightPixels * fraction);
        view.setPadding(left + x, top + y, right + x, bottom + y);
    }

    static void choose(Activity activity) {
        String[] items = {"Marginesy ekranu: " + MARGINS[margin(activity)], "Wielkość tekstu: " + SIZES[size(activity)], "Kolory: " + THEMES[theme(activity)], "Język: " + LANGUAGES[english(activity) ? 1 : 0]};
        new Ui.Dialog(activity).setTitle("Ekran i wygląd").setItems(items, (d, which) -> {
            if (which == 0) pick(activity, "Marginesy ekranu", MARGINS, margin(activity), "margin");
            else if (which == 1) pick(activity, "Wielkość tekstu", SIZES, size(activity), "size");
            else if (which == 2) pick(activity, "Kolory", THEMES, theme(activity), "theme");
            else pick(activity, "Język", LANGUAGES, english(activity) ? 1 : 0, "language");
        }).setNegativeButton("Zamknij", null).show();
    }

    private static void pick(Activity activity, String title, String[] options, int current, String key) {
        new Ui.Dialog(activity).setTitle(title).setSingleChoiceItems(options, current, (dialog, which) -> {
            dialog.dismiss(); if (which == current) return;
            store(activity).edit().putInt(key, which).commit(); apply(activity);
            activity.recreate(); // every screen is rebuilt with the new margins, size, colours or language
        }).setNegativeButton("Zamknij", null).show();
    }
}
