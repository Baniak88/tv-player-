package pl.tvplayer;

import okhttp3.*;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class SourceClient {
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS).build();
    public static HttpUrl validate(String value) {
        HttpUrl url = HttpUrl.parse(value.trim());
        if (url == null || !url.username().isEmpty() || !url.password().isEmpty())
            throw new IllegalArgumentException("Wpisz poprawny adres http:// lub https:// bez danych logowania w adresie.");
        return url;
    }
    private String get(HttpUrl url) throws IOException {
        try (Response response = client.newCall(new Request.Builder().url(url).header("User-Agent", "TVPlayer/0.1").build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Serwer zwrócił błąd HTTP " + response.code() + ".");
            if (response.body().contentLength() > 30_000_000) throw new IOException("Lista przekracza limit 30 MB.");
            try (java.io.InputStream input = response.body().byteStream()) {
                return readLimited(input);
            }
        }
    }
    static String readLimited(java.io.InputStream input) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int read;
        while ((read = input.read(buffer)) != -1) {
            if (out.size() + read > 30_000_000) throw new IOException("Lista przekracza limit 30 MB.");
            out.write(buffer, 0, read);
        }
        return out.toString(java.nio.charset.StandardCharsets.UTF_8.name());
    }
    public List<Channel> m3u(String url) throws IOException { return M3uParser.parse(get(validate(url)), url.trim()); }
    private HttpUrl api(HttpUrl base, String user, String pass, String action) {
        HttpUrl.Builder builder = base.newBuilder().addPathSegment("player_api.php")
                .addQueryParameter("username", user).addQueryParameter("password", pass);
        if (action != null) builder.addQueryParameter("action", action);
        return builder.build();
    }
    public List<Channel> xtream(String server, String user, String pass) throws Exception {
        HttpUrl raw = validate(server);
        HttpUrl base = raw.newBuilder().query(null).fragment(null).build();
        if (!base.encodedPath().endsWith("/")) base = base.newBuilder().addPathSegment("").build();
        JSONObject auth = new JSONObject(get(api(base, user, pass, null))).optJSONObject("user_info");
        if (auth == null || auth.optInt("auth", 0) != 1 || !"Active".equalsIgnoreCase(auth.optString("status", "Active")))
            throw new IOException("Logowanie odrzucone. Sprawdź dane i ważność konta.");
        Map<String, String> categories = new HashMap<>();
        JSONArray groups = new JSONArray(get(api(base, user, pass, "get_live_categories")));
        for (int i = 0; i < groups.length(); i++) {
            JSONObject g = groups.getJSONObject(i);
            categories.put(g.optString("category_id"), g.optString("category_name", "Bez kategorii"));
        }
        JSONArray streams = new JSONArray(get(api(base, user, pass, "get_live_streams")));
        List<Channel> result = new ArrayList<>();
        for (int i = 0; i < streams.length(); i++) {
            JSONObject item = streams.getJSONObject(i);
            String id = item.optString("stream_id");
            if (!id.matches("[0-9]+")) continue;
            String url = base.newBuilder().addPathSegment("live").addPathSegment(user).addPathSegment(pass)
                    .addPathSegment(id + ".ts").build().toString();
            result.add(new Channel(item.optString("name", "Kanał"), url,
                    categories.getOrDefault(item.optString("category_id"), "Bez kategorii"), item.optString("epg_channel_id")));
        }
        return result;
    }
}
