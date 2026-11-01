package pl.tvplayer;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Styled bottom sheets for choosing and configuring a playlist source. */
final class SourceSheet {
    interface Listener {
        void onXtream(String server, String user, String password);
        void onM3uUrl(String url);
        void onM3uFile();
        void onClearLibrary();
    }

    private static final int SHEET = 0xFF170E1C, CARD = 0xFF241321, CARD_BORDER = 0xFF4A2234, ACCENT = 0xFFD63150,
        TEXT = 0xFFF5E9ED, MUTED = 0xFFB89AA8, HINT = 0xFF8E7180, DANGER = 0xFFFF6B7F;
    private final Activity activity;
    private final Listener listener;
    private boolean hasLibrary;

    SourceSheet(Activity activity, Listener listener) { this.activity = activity; this.listener = listener; }

    /** @param hasLibrary whether a library is saved; false hides the replace warning and the delete action. */
    void showMenu(boolean hasLibrary) {
        this.hasLibrary = hasLibrary;
        Dialog dialog = dialog();
        LinearLayout sheet = sheet(dialog, "Dodaj źródło", "Skąd pobrać kanały TV, filmy i seriale?", null);
        sheet.addView(option("XC", "Konto Xtream Codes", "Serwer, login i hasło · TV, filmy, seriale i EPG", true,
            () -> { dialog.dismiss(); showXtream(); }));
        sheet.addView(option("URL", "Lista M3U z internetu", "Wklej link do playlisty .m3u lub .m3u8", false,
            () -> { dialog.dismiss(); showM3uUrl(); }));
        sheet.addView(option("▤", "Plik M3U z telefonu", "Wybierz playlistę zapisaną w pamięci urządzenia", false,
            () -> { dialog.dismiss(); listener.onM3uFile(); }));
        if (hasLibrary) {
            TextView note = text("Nowe źródło zastąpi obecną bibliotekę. Ulubione zostaną zachowane, jeśli adresy kanałów się nie zmienią.", 12, MUTED);
            note.setPadding(dp(4), dp(10), dp(4), dp(4)); sheet.addView(note);
            Button clear = flat("Usuń bibliotekę z telefonu", DANGER);
            clear.setOnClickListener(v -> { dialog.dismiss(); confirmClear(); });
            sheet.addView(clear, matchWrap(dp(4)));
        }
        Button cancel = flat("Anuluj", MUTED); cancel.setOnClickListener(v -> dialog.dismiss());
        sheet.addView(cancel, matchWrap(0));
        dialog.show();
    }

    private void confirmClear() {
        Dialog dialog = dialog();
        LinearLayout sheet = sheet(dialog, "Usunąć bibliotekę?", "Kanały, filmy, seriale i ulubione zostaną usunięte z telefonu. Konto u dostawcy pozostaje bez zmian.", null);
        Button delete = primary("Usuń wszystko", 0xFFB3243F);
        delete.setOnClickListener(v -> { dialog.dismiss(); listener.onClearLibrary(); });
        sheet.addView(delete, matchWrap(dp(8)));
        Button cancel = flat("Anuluj", MUTED); cancel.setOnClickListener(v -> dialog.dismiss());
        sheet.addView(cancel, matchWrap(0));
        dialog.show();
    }

    void showXtream() {
        Dialog dialog = dialog();
        LinearLayout sheet = sheet(dialog, "Konto Xtream Codes", "Dane otrzymasz od dostawcy usługi.", () -> { dialog.dismiss(); showMenu(hasLibrary); });
        Field server = field(sheet, "Adres serwera", "np. http://dostawca.tv:8080", InputType.TYPE_TEXT_VARIATION_URI, true);
        Field user = field(sheet, "Nazwa użytkownika", "login", InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, false);
        Field pass = field(sheet, "Hasło", "hasło", InputType.TYPE_TEXT_VARIATION_PASSWORD, false);
        addReveal(pass);
        TextView info = text("Port wpisz po dwukropku, jeśli dostawca go podał. Brak http:// zostanie uzupełniony.", 12, HINT);
        info.setPadding(dp(4), 0, dp(4), dp(12)); sheet.addView(info);
        Button connect = primary("Połącz i pobierz", ACCENT); sheet.addView(connect, matchWrap(dp(4)));
        Runnable submit = () -> {
            boolean ok = true;
            String address = normalize(server.input.getText().toString());
            try { SourceClient.validate(address); server.error(null); }
            catch (Exception e) { server.error("Podaj adres serwera, np. http://dostawca.tv:8080"); ok = false; }
            String login = user.input.getText().toString().trim();
            if (login.isEmpty()) { user.error("Wpisz nazwę użytkownika"); ok = false; } else user.error(null);
            if (pass.input.getText().length() == 0) { pass.error("Wpisz hasło"); ok = false; } else pass.error(null);
            if (!ok) return;
            dialog.dismiss(); listener.onXtream(address, login, pass.input.getText().toString());
        };
        connect.setOnClickListener(v -> submit.run());
        pass.input.setImeOptions(EditorInfo.IME_ACTION_GO);
        pass.input.setOnEditorActionListener((v, action, event) -> { submit.run(); return true; });
        dialog.show(); focus(dialog, server.input);
    }

    void showM3uUrl() {
        Dialog dialog = dialog();
        LinearLayout sheet = sheet(dialog, "Lista M3U z internetu", "Link do playlisty od dostawcy lub z własnego serwera.", () -> { dialog.dismiss(); showMenu(hasLibrary); });
        Field url = field(sheet, "Adres playlisty", "https://serwer/lista.m3u", InputType.TYPE_TEXT_VARIATION_URI, true);
        String clip = clipboardUrl();
        if (clip != null && url.input.getText().length() == 0) url.input.setText(clip);
        Button load = primary("Wczytaj listę", ACCENT); sheet.addView(load, matchWrap(dp(8)));
        Runnable submit = () -> {
            String address = normalize(url.input.getText().toString());
            try { SourceClient.validate(address); }
            catch (Exception e) { url.error("Podaj poprawny adres http:// lub https://"); return; }
            dialog.dismiss(); listener.onM3uUrl(address);
        };
        load.setOnClickListener(v -> submit.run());
        url.input.setImeOptions(EditorInfo.IME_ACTION_GO);
        url.input.setOnEditorActionListener((v, action, event) -> { submit.run(); return true; });
        dialog.show(); focus(dialog, url.input);
    }

    static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.isEmpty() && !value.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) value = "http://" + value;
        return value;
    }

    // ---- building blocks -------------------------------------------------------------------

    private static final class Field {
        final EditText input; final TextView error; final LinearLayout row;
        Field(EditText input, TextView error, LinearLayout row) { this.input = input; this.error = error; this.row = row; }
        void error(String message) {
            error.setText(message == null ? "" : message); error.setVisibility(message == null ? View.GONE : View.VISIBLE);
            row.setBackground(box(row.getContext(), message == null ? 0xFF5A2B3C : DANGER));
            if (message != null) input.requestFocus();
        }
    }

    private Field field(LinearLayout parent, String label, String hint, int variation, boolean pasteButton) {
        TextView caption = text(label, 13, MUTED); caption.setTypeface(null, Typeface.BOLD);
        caption.setPadding(dp(4), dp(6), 0, dp(6)); parent.addView(caption);
        LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL); row.setBackground(box(activity, 0xFF5A2B3C));
        EditText input = new EditText(activity); input.setHint(hint); input.setSingleLine(true); input.setTextSize(16);
        input.setTextColor(TEXT); input.setHintTextColor(HINT); input.setBackground(null); input.setPadding(dp(14), dp(12), dp(8), dp(12));
        input.setInputType(InputType.TYPE_CLASS_TEXT | variation | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_NEXT); input.setMinHeight(dp(50));
        row.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        if (pasteButton) {
            Button paste = flat("Wklej", ACCENT); paste.setTextSize(13);
            paste.setOnClickListener(v -> { String clip = clipboardText(); if (clip != null) { input.setText(clip.trim()); input.setSelection(input.getText().length()); } });
            row.addView(paste, new LinearLayout.LayoutParams(-2, dp(44)));
        }
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        TextView error = text("", 12, DANGER); error.setPadding(dp(4), dp(4), 0, 0); error.setVisibility(View.GONE); parent.addView(error);
        View gap = new View(activity); parent.addView(gap, new LinearLayout.LayoutParams(1, dp(8)));
        Field field = new Field(input, error, row);
        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { if (error.getVisibility() == View.VISIBLE) field.error(null); }
            public void afterTextChanged(android.text.Editable e) { }
        });
        return field;
    }

    private void addReveal(Field field) {
        Button reveal = flat("Pokaż", ACCENT); reveal.setTextSize(13);
        reveal.setOnClickListener(v -> {
            boolean hidden = (field.input.getInputType() & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD;
            int position = field.input.getSelectionEnd();
            field.input.setInputType(InputType.TYPE_CLASS_TEXT | (hidden ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            field.input.setTypeface(Typeface.DEFAULT); field.input.setSelection(Math.max(0, position));
            reveal.setText(hidden ? "Ukryj" : "Pokaż");
        });
        field.input.setTypeface(Typeface.DEFAULT);
        field.row.addView(reveal, new LinearLayout.LayoutParams(-2, dp(44)));
    }

    private View option(String badge, String title, String description, boolean recommended, Runnable action) {
        LinearLayout card = new LinearLayout(activity); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(12), dp(14)); card.setBackground(pressable(recommended ? 0xFF3A1226 : CARD, recommended ? 0xFF8C2C45 : CARD_BORDER));
        card.setClickable(true); card.setFocusable(true); card.setOnClickListener(v -> action.run());
        TextView icon = text(badge, badge.length() > 1 ? 13 : 20, Color.WHITE); icon.setTypeface(null, Typeface.BOLD); icon.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable(); circle.setShape(GradientDrawable.OVAL); circle.setColor(recommended ? ACCENT : 0xFF4F1A2E); icon.setBackground(circle);
        card.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout copy = new LinearLayout(activity); copy.setOrientation(LinearLayout.VERTICAL); copy.setPadding(dp(14), 0, dp(8), 0);
        LinearLayout heading = new LinearLayout(activity); heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(title, 16, TEXT); name.setTypeface(null, Typeface.BOLD); name.setMaxLines(1); name.setEllipsize(TextUtils.TruncateAt.END);
        heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        if (recommended) {
            TextView tag = text("POLECANE", 10, Color.WHITE); tag.setTypeface(null, Typeface.BOLD); tag.setPadding(dp(7), dp(2), dp(7), dp(2));
            tag.setBackground(rounded(0xFF8C2C45, 10, 0xFF8C2C45));
            LinearLayout.LayoutParams tagSize = new LinearLayout.LayoutParams(-2, -2); tagSize.leftMargin = dp(6); heading.addView(tag, tagSize);
        }
        copy.addView(heading);
        TextView detail = text(description, 13, MUTED); detail.setPadding(0, dp(3), 0, 0); copy.addView(detail);
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chevron = text("›", 24, MUTED); card.addView(chevron);
        card.setContentDescription(title + ". " + description);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2); size.bottomMargin = dp(10); card.setLayoutParams(size);
        return card;
    }

    private Dialog dialog() {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Dialog_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    private LinearLayout sheet(Dialog dialog, String title, String subtitle, Runnable back) {
        LinearLayout sheet = new LinearLayout(activity); sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(18), dp(10), dp(18), dp(18));
        View handle = new View(activity); handle.setBackground(rounded(0xFF5A3445, 3, 0xFF5A3445));
        LinearLayout.LayoutParams handleSize = new LinearLayout.LayoutParams(dp(40), dp(5)); handleSize.gravity = Gravity.CENTER_HORIZONTAL; handleSize.bottomMargin = dp(14);
        sheet.addView(handle, handleSize);
        LinearLayout header = new LinearLayout(activity); header.setGravity(Gravity.CENTER_VERTICAL);
        if (back != null) {
            Button backButton = flat("‹", TEXT); backButton.setTextSize(26); backButton.setContentDescription("Wróć do wyboru źródła");
            backButton.setOnClickListener(v -> back.run());
            header.addView(backButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }
        TextView heading = text(title, 21, TEXT); heading.setTypeface(null, Typeface.BOLD);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        sheet.addView(header);
        TextView sub = text(subtitle, 13, MUTED); sub.setPadding(back != null ? dp(4) : 0, dp(4), 0, dp(16)); sheet.addView(sub);

        ScrollView scroll = new ScrollView(activity); scroll.setFillViewport(true); scroll.addView(sheet);
        FrameLayout frame = new FrameLayout(activity);
        GradientDrawable background = new GradientDrawable(); background.setColor(SHEET);
        float r = dp(20); background.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0}); background.setStroke(dp(1), 0xFF3A1C2B);
        frame.setBackground(background);
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -2));
        frame.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(0, 0, 0, insets.getSystemWindowInsetBottom()); return insets; });
        dialog.setContentView(frame);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.getDecorView().setPadding(0, 0, 0, 0);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT; params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.BOTTOM; params.dimAmount = 0.6f;
            int max = activity.getResources().getConfiguration().screenWidthDp;
            if (max > 600) params.width = dp(560);
            window.setAttributes(params);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            window.setWindowAnimations(android.R.style.Animation_InputMethod);
        }
        return sheet;
    }

    private void focus(Dialog dialog, EditText input) {
        input.requestFocus();
        Window window = dialog.getWindow();
        if (window != null) window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private String clipboardText() {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) return null;
        ClipData data = clipboard.getPrimaryClip();
        if (data == null || data.getItemCount() == 0) return null;
        CharSequence text = data.getItemAt(0).coerceToText(activity);
        return text == null ? null : text.toString();
    }

    private String clipboardUrl() {
        String text = clipboardText();
        if (text == null) return null;
        text = text.trim();
        return text.matches("(?i)^https?://\\S+$") ? text : null;
    }

    private Button primary(String label, int color) {
        Button button = new Button(activity); button.setText(label); button.setAllCaps(false); button.setTextSize(16);
        button.setTypeface(null, Typeface.BOLD); button.setTextColor(Color.WHITE); button.setStateListAnimator(null);
        button.setMinHeight(dp(52)); button.setMinimumHeight(dp(52)); button.setBackground(pressable(color, color));
        return button;
    }

    private Button flat(String label, int color) {
        Button button = new Button(activity); button.setText(label); button.setAllCaps(false); button.setTextSize(15);
        button.setTextColor(color); button.setStateListAnimator(null); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(dp(44)); button.setMinimumHeight(dp(44)); button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(pressable(Color.TRANSPARENT, Color.TRANSPARENT));
        return button;
    }

    private LinearLayout.LayoutParams matchWrap(int top) {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2); size.topMargin = top; return size;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(activity); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }

    private GradientDrawable rounded(int color, int radius, int border) {
        GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(radius)); bg.setStroke(dp(1), border); return bg;
    }

    private static GradientDrawable box(Context context, int border) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0xFF1F1426); bg.setCornerRadius(10 * density); bg.setStroke((int) (density * 1.5f), border); return bg;
    }

    private android.graphics.drawable.Drawable pressable(int color, int border) {
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, rounded(blend(color), 12, blend(border)));
        states.addState(new int[]{android.R.attr.state_focused}, rounded(color, 12, ACCENT));
        states.addState(new int[]{}, rounded(color, 12, border));
        return states;
    }

    private static int blend(int color) {
        if (Color.alpha(color) == 0) return 0x22FFFFFF;
        return Color.rgb(Math.min(255, Color.red(color) + 28), Math.min(255, Color.green(color) + 18), Math.min(255, Color.blue(color) + 22));
    }

    private int dp(int n) { return (int) (n * activity.getResources().getDisplayMetrics().density); }
}
