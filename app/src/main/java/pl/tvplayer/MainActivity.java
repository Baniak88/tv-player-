package pl.tvplayer;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import android.text.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int FILE_REQUEST = 41;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private List<Channel> channels = new ArrayList<>(), shown = new ArrayList<>();
    private final Set<String> favorites = new HashSet<>();
    private LibraryStore store;
    private TextView status;
    private EditText search;
    private Spinner groups;
    private ListView list;
    private Button favoriteButton, addButton;
    private boolean onlyFavorites, busy;
    private String selectedGroup = "Wszystkie kategorie";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store = new LibraryStore(this);
        LinearLayout root = column(); root.setPadding(dp(20), 0, dp(20), 0);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(20), insets.getSystemWindowInsetTop(), dp(20), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        TextView brand = label("TV PLAYER", 15); brand.setTextColor(Color.rgb(78, 226, 177)); brand.setPadding(0, dp(22), 0, dp(20)); root.addView(brand);
        TextView title = label("Twoja telewizja.\nZawsze pod ręką.", 30); title.setTypeface(null, Typeface.BOLD); root.addView(title);
        TextView hint = label("Na żywo • Twoje listy • Twoje kanały", 14); hint.setTextColor(0xFF9BAABE); hint.setPadding(0, dp(12), 0, dp(20)); root.addView(hint);
        addButton = button("＋ Dodaj źródło"); root.addView(addButton); addButton.setOnClickListener(v -> sourceMenu());
        search = input("Szukaj kanału", false); root.addView(search);
        groups = new Spinner(this); root.addView(groups);
        favoriteButton = button("☆ Pokaż ulubione"); root.addView(favoriteButton);
        favoriteButton.setOnClickListener(v -> { onlyFavorites = !onlyFavorites; favoriteButton.setText(onlyFavorites ? "★ Ulubione • pokaż wszystkie" : "☆ Pokaż ulubione"); filter(); });
        status = label("Wczytywanie biblioteki…", 13); status.setPadding(0, dp(12), 0, dp(12)); root.addView(status);
        list = new ListView(this); list.setDividerHeight(dp(8)); root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView tip = label("Przytrzymaj kanał, aby zmienić ulubione", 12); tip.setTextColor(0xFF9BAABE); tip.setPadding(0, dp(10), 0, dp(10)); root.addView(tip);
        setContentView(root);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int f) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { filter(); }
            public void afterTextChanged(Editable e) { }
        });
        groups.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { selectedGroup = (String) p.getItemAtPosition(pos); filter(); }
            public void onNothingSelected(AdapterView<?> p) { }
        });
        list.setOnItemClickListener((p, v, pos, id) -> {
            Channel c = shown.get(pos);
            startActivity(new Intent(this, PlayerActivity.class).putExtra("url", c.url()).putExtra("name", c.name()));
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            String url = shown.get(pos).url();
            if (!favorites.remove(url)) favorites.add(url);
            filter(); persist(); return true;
        });
        worker.execute(() -> {
            try {
                List<Channel> loaded = store.load(favorites);
                runOnUiThread(() -> { if (!isDestroyed()) { channels = loaded; refreshGroups(); } });
            } catch (Exception e) { runOnUiThread(() -> { refreshGroups(); error("Nie można odczytać zapisanej biblioteki. Dodaj ponownie źródło."); }); }
        });
    }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView label(String text, int size) { TextView t = new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(Color.WHITE); return t; }
    private Button button(String text) { Button b = new Button(this); b.setText(text); b.setAllCaps(false); return b; }
    private EditText input(String hint, boolean password) {
        EditText e = new EditText(this); e.setHint(hint); e.setSingleLine(true); e.setTextSize(16);
        e.setInputType(password ? android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD : android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return e;
    }
    private void refreshGroups() {
        TreeSet<String> set = new TreeSet<>(); for (Channel c : channels) set.add(c.group());
        List<String> values = new ArrayList<>(); values.add("Wszystkie kategorie"); values.addAll(set);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); groups.setAdapter(adapter);
        selectedGroup = values.get(0); filter();
    }
    private void filter() {
        if (list == null) return;
        String query = search.getText().toString().toLowerCase(Locale.ROOT);
        shown = new ArrayList<>();
        for (Channel c : channels) if ((!onlyFavorites || favorites.contains(c.url()))
                && (selectedGroup.equals("Wszystkie kategorie") || selectedGroup.equals(c.group()))
                && c.name().toLowerCase(Locale.ROOT).contains(query)) shown.add(c);
        list.setAdapter(new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int p) { return shown.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p, View reused, ViewGroup parent) {
                TextView row = reused instanceof TextView ? (TextView) reused : label("", 17);
                Channel c = shown.get(p);
                row.setText((favorites.contains(c.url()) ? "★  " : "▶  ") + c.name() + "\n" + c.group());
                row.setPadding(dp(16), dp(14), dp(16), dp(14)); row.setBackgroundColor(0xFF192638); return row;
            }
        });
        if (!busy) status.setText(channels.isEmpty() ? "Dodaj listę M3U lub konto Xtream Codes, aby zacząć." : shown.size() + " z " + channels.size() + " kanałów");
    }
    private void sourceMenu() {
        if (busy) return;
        new AlertDialog.Builder(this).setTitle("Dodaj źródło")
                .setItems(new String[]{"M3U / M3U8 — adres URL", "M3U / M3U8 — plik z telefonu", "Xtream Codes — serwer i konto", "Usuń bibliotekę"}, (d, which) -> {
                    if (which == 0) m3uDialog();
                    if (which == 1) startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), FILE_REQUEST);
                    if (which == 2) xtreamDialog();
                    if (which == 3) new AlertDialog.Builder(this).setMessage("Usunąć zapisane kanały i ulubione z telefonu?")
                            .setNegativeButton("Anuluj", null).setPositiveButton("Usuń", (a, b) -> { channels.clear(); favorites.clear(); persist(); refreshGroups(); }).show();
                }).show();
    }
    private LinearLayout form() { LinearLayout form = column(); form.setPadding(dp(20), dp(8), dp(20), 0); return form; }
    private void m3uDialog() {
        LinearLayout form = form(); EditText url = input("https://serwer/lista.m3u", false); url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); form.addView(url);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Lista M3U z internetu").setView(form)
                .setMessage("Import zastąpi bieżącą listę kanałów.").setNegativeButton("Anuluj", null).setPositiveButton("Wczytaj", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            try { SourceClient.validate(url.getText().toString()); }
            catch (Exception e) { url.setError("Podaj poprawny adres HTTP lub HTTPS"); return; }
            String address = url.getText().toString(); dialog.dismiss(); load(() -> new SourceClient().m3u(address));
        })); dialog.show();
    }
    private void xtreamDialog() {
        LinearLayout form = form(); EditText server = input("Serwer: https://dostawca:port", false), user = input("Nazwa użytkownika", false), pass = input("Hasło", true);
        server.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); form.addView(server); form.addView(user); form.addView(pass);
        form.addView(label("Import kanałów TV na żywo zastąpi bieżącą listę.", 13));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Xtream Codes").setView(form).setNegativeButton("Anuluj", null).setPositiveButton("Połącz", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            try { SourceClient.validate(server.getText().toString()); } catch (Exception e) { server.setError("Podaj adres HTTP lub HTTPS"); return; }
            if (user.getText().toString().trim().isEmpty() || pass.getText().length() == 0) { pass.setError("Uzupełnij login i hasło"); return; }
            String address = server.getText().toString(), username = user.getText().toString().trim(), password = pass.getText().toString();
            dialog.dismiss(); load(() -> new SourceClient().xtream(address, username, password));
        })); dialog.show();
    }
    private void load(Callable<List<Channel>> task) {
        busy = true; addButton.setEnabled(false); status.setText("Łączenie i wczytywanie kanałów…");
        worker.execute(() -> {
            try {
                List<Channel> result = task.call();
                if (result.isEmpty()) throw new Exception("Źródło nie zawiera obsługiwanych kanałów HTTP/HTTPS.");
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    channels = result;
                    Set<String> urls = new HashSet<>(); for (Channel c : channels) urls.add(c.url()); favorites.retainAll(urls);
                    busy = false; addButton.setEnabled(true); refreshGroups(); persist();
                });
            } catch (Exception e) {
                String message = e instanceof java.io.IOException || e instanceof IllegalArgumentException ? e.getMessage() : "Sprawdź adres, połączenie i dane konta.";
                // Do not expose exception URLs: they can contain credentials.
                if (message == null || message.contains("http") || message.length() > 180) message = "Nie udało się połączyć. Sprawdź adres, połączenie i dane konta.";
                final String safe = message;
                runOnUiThread(() -> { if (!isDestroyed()) { busy = false; addButton.setEnabled(true); filter(); error(safe); } });
            }
        });
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == FILE_REQUEST && result == RESULT_OK && data != null && data.getData() != null) {
            android.net.Uri uri = data.getData();
            load(() -> {
                try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new java.io.IOException("Nie można otworzyć pliku.");
                    return M3uParser.parse(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8), null);
                }
            });
        }
    }
    private void persist() {
        List<Channel> copy = new ArrayList<>(channels); Set<String> stars = new HashSet<>(favorites);
        worker.execute(() -> { try { store.save(copy, stars); } catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed()) error("Nie udało się zapisać biblioteki na urządzeniu."); }); } });
    }
    private void error(String message) { if (!isDestroyed()) new AlertDialog.Builder(this).setTitle("TV Player").setMessage(message).setPositiveButton("OK", null).show(); }
    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
