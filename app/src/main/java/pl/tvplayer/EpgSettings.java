package pl.tvplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import okhttp3.HttpUrl;

/** Optional XMLTV guide chosen by the user, for providers that do not serve EPG themselves. */
final class EpgSettings {
    private EpgSettings() { }
    static HttpUrl custom(Context context) {
        String value = context.getSharedPreferences("epg-settings", Context.MODE_PRIVATE).getString("xmltv", "");
        return value.isEmpty() ? null : HttpUrl.parse(value);
    }
    static void choose(Activity activity, Runnable changed) {
        HttpUrl current = custom(activity);
        LinearLayout body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(activity, 20); body.setPadding(pad, Ui.dp(activity, 6), pad, 0);
        TextView info = new TextView(activity); info.setTextSize(13); info.setTextColor(Ui.MUTED); info.setLineSpacing(0, 1.15f);
        info.setText("Jeśli dostawca nie udostępnia programu TV, wklej link do pliku XMLTV (.xml lub .xml.gz), np. od dostawcy lub z serwisu z polskim EPG. Kanały są dopasowywane po identyfikatorze EPG albo po nazwie (bez dopisków PL, HD, FHD).");
        body.addView(info);
        EditText input = new EditText(activity); input.setSingleLine(true); input.setHint("https://…/epg.xml.gz");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); input.setTextColor(Ui.TEXT); input.setHintTextColor(Ui.FAINT);
        if (current != null) input.setText(current.toString());
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2); size.topMargin = Ui.dp(activity, 12); body.addView(input, size);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Program TV (EPG)").setView(body)
            .setPositiveButton("Zapisz", null).setNegativeButton("Anuluj", null)
            .setNeutralButton(current == null ? "Wklej" : "Usuń", null).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                if (current != null) { save(activity, ""); dialog.dismiss(); changed.run(); return; }
                android.content.ClipboardManager clip = (android.content.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clip != null && clip.hasPrimaryClip() && clip.getPrimaryClip().getItemCount() > 0) input.setText(String.valueOf(clip.getPrimaryClip().getItemAt(0).coerceToText(activity)).trim());
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String value = SourceSheet.normalize(input.getText().toString());
                if (value.isEmpty()) { save(activity, ""); dialog.dismiss(); changed.run(); return; }
                HttpUrl url = HttpUrl.parse(value);
                if (url == null || !url.username().isEmpty() || !url.password().isEmpty()) { input.setError("Podaj poprawny adres http:// lub https://"); return; }
                save(activity, url.toString()); dialog.dismiss(); changed.run();
            });
        });
        dialog.show();
    }
    private static void save(Context context, String value) {
        context.getSharedPreferences("epg-settings", Context.MODE_PRIVATE).edit().putString("xmltv", value).apply();
        GuideRepository.get(context).invalidateFailures();
    }
}
