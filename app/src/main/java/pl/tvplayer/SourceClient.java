package pl.tvplayer;

import okhttp3.*;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class SourceClient {
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.MINUTES).build();
    public static HttpUrl validate(String value) {
        HttpUrl url = HttpUrl.parse(value.trim());
        if (url == null || !url.username().isEmpty() || !url.password().isEmpty())
            throw new IllegalArgumentException("Wpisz poprawny adres http:// lub https:// bez danych logowania w adresie.");
        return url;
    }
    private interface ReaderTask<T> { T read(Reader reader) throws IOException; }
    private <T> T get(HttpUrl url, ReaderTask<T> task) throws IOException {
        try (Response response = client.newCall(new Request.Builder().url(url).header("User-Agent", "TVPlayer/0.3.0").build()).execute()) {
            if (!response.isSuccessful() || response.body() == null)
                throw new IOException("Serwer zwrócił błąd HTTP " + response.code() + ".");
            // Parse directly from the response: no size-based rejection or full-body copy.
            try (Reader reader = response.body().charStream()) { return task.read(reader); }
        }
    }
    public List<Channel> m3u(String url) throws IOException {
        HttpUrl address = validate(url);
        return get(address, reader -> M3uParser.parse(reader, address.toString()));
    }
    private HttpUrl api(HttpUrl base, String user, String pass, String action) {
        HttpUrl.Builder builder = base.newBuilder().addPathSegment("player_api.php")
                .addQueryParameter("username", user).addQueryParameter("password", pass);
        if (action != null) builder.addQueryParameter("action", action);
        return builder.build();
    }
    static String value(JsonReader reader) throws IOException {
        JsonToken token = reader.peek();
        if (token == JsonToken.STRING || token == JsonToken.NUMBER) return reader.nextString();
        reader.skipValue(); return "";
    }
    static Map<String, String> readCategories(Reader source) throws IOException {
        JsonReader reader = new JsonReader(source);
        Map<String, String> result = new HashMap<>();
        reader.beginArray();
        while (reader.hasNext()) {
            String id = "", name = "Bez kategorii";
            reader.beginObject();
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "category_id": id = value(reader); break;
                    case "category_name": name = value(reader); break;
                    default: reader.skipValue();
                }
            }
            reader.endObject(); result.put(id, name);
        }
        reader.endArray();
        if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Nieprawidłowa odpowiedź serwera.");
        return result;
    }
    static List<Channel> readStreams(Reader source, HttpUrl base, String user, String pass, Map<String, String> categories) throws IOException {
        return readCatalog(source, base, user, pass, categories, "tv");
    }
    static List<Channel> readCatalog(Reader source, HttpUrl base, String user, String pass, Map<String,String> categories, String kind) throws IOException {
        JsonReader reader = new JsonReader(source);
        List<Channel> result = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            String id = "", name = "Kanał", category = "", epg = "", extension = "mp4";
            reader.beginObject();
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "stream_id": case "series_id": id = value(reader); break;
                    case "container_extension": extension = value(reader); break;
                    case "name": name = value(reader); break;
                    case "category_id": category = value(reader); break;
                    case "epg_channel_id": epg = value(reader); break;
                    default: reader.skipValue();
                }
            }
            reader.endObject();
            if (!id.matches("[0-9]+")) continue;
            if (!extension.matches("[a-zA-Z0-9]{1,8}")) extension = "mp4";
            String url;
            if (kind.equals("series")) {
                url = base.newBuilder().addPathSegment("player_api.php").addQueryParameter("username",user)
                    .addQueryParameter("password",pass).addQueryParameter("action","get_series_info").addQueryParameter("series_id",id).build().toString();
            } else {
                url = base.newBuilder().addPathSegment(kind.equals("tv") ? "live" : "movie").addPathSegment(user).addPathSegment(pass)
                    .addPathSegment(id + (kind.equals("tv") ? ".ts" : "." + extension)).build().toString();
            }
            result.add(new Channel(name, url, categories.getOrDefault(category, "Bez kategorii"), epg, kind));
        }
        reader.endArray();
        if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Nieprawidłowa odpowiedź serwera.");
        return result;
    }
    public List<Channel> xtream(String server, String user, String pass) throws Exception {
        HttpUrl raw = validate(server);
        HttpUrl base = raw.newBuilder().query(null).fragment(null).build();
        if (!base.encodedPath().endsWith("/")) base = base.newBuilder().addPathSegment("").build();
        JsonObject root = get(api(base, user, pass, null), reader -> JsonParser.parseReader(reader).getAsJsonObject());
        JsonObject auth = root.has("user_info") && root.get("user_info").isJsonObject() ? root.getAsJsonObject("user_info") : null;
        if (auth == null || !auth.has("auth") || !"1".equals(auth.get("auth").getAsString())
                || (auth.has("status") && !"Active".equalsIgnoreCase(auth.get("status").getAsString())))
            throw new IOException("Logowanie odrzucone. Sprawdź dane i ważność konta.");
        Map<String, String> categories = get(api(base, user, pass, "get_live_categories"), SourceClient::readCategories);
        final HttpUrl streamBase = base;
        List<Channel> all = get(api(base, user, pass, "get_live_streams"), reader -> readStreams(reader, streamBase, user, pass, categories));
        Map<String,String> movieCategories = get(api(base,user,pass,"get_vod_categories"),SourceClient::readCategories);
        all.addAll(get(api(base,user,pass,"get_vod_streams"),reader -> readCatalog(reader,streamBase,user,pass,movieCategories,"movie")));
        Map<String,String> seriesCategories = get(api(base,user,pass,"get_series_categories"),SourceClient::readCategories);
        all.addAll(get(api(base,user,pass,"get_series"),reader -> readCatalog(reader,streamBase,user,pass,seriesCategories,"series")));
        return all;
    }
    public List<Channel> episodes(Channel series) throws IOException {
        HttpUrl address = validate(series.url());
        String user = address.queryParameter("username"), pass = address.queryParameter("password");
        if (user == null || pass == null) throw new IOException("Brak danych konta serialu. Zaimportuj źródło ponownie.");
        HttpUrl base = address.newBuilder().query(null).removePathSegment(address.pathSize()-1).build();
        return get(address, reader -> parseEpisodes(reader,base,user,pass,series.name()));
    }
    static List<Channel> parseEpisodes(Reader reader,HttpUrl base,String user,String pass,String seriesName) throws IOException {
        try {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonElement data = root.get("episodes");
            List<Channel> result = new ArrayList<>();
            if (data == null || data.isJsonNull()) return result;
            if (!data.isJsonObject()) throw new IOException("Nieprawidłowa lista sezonów.");
            for (Map.Entry<String,JsonElement> season : data.getAsJsonObject().entrySet()) {
                if (!season.getValue().isJsonArray()) continue;
                for (JsonElement element : season.getValue().getAsJsonArray()) {
                    if (!element.isJsonObject()) continue;
                    JsonObject episode = element.getAsJsonObject();
                    String id = field(episode,"id","");
                    if (!id.matches("[0-9]+")) continue;
                    String ext = field(episode,"container_extension","mp4");
                    if (!ext.matches("[a-zA-Z0-9]{1,8}")) ext = "mp4";
                    String number = field(episode,"episode_num","");
                    String title = field(episode,"title","Odcinek " + number);
                    String url = base.newBuilder().addPathSegment("series").addPathSegment(user).addPathSegment(pass).addPathSegment(id+"."+ext).build().toString();
                    result.add(new Channel(seriesName+" · "+number+" · "+title,url,"Sezon "+season.getKey(),"","episode"));
                }
            }
            return result;
        } catch (JsonParseException | IllegalStateException e) { throw new IOException("Nieprawidłowa odpowiedź z odcinkami.",e); }
    }
    private static String field(JsonObject o,String name,String fallback) {
        JsonElement e=o.get(name); return e!=null && e.isJsonPrimitive()?e.getAsString():fallback;
    }
}
