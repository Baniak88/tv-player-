package pl.tvplayer;

import okhttp3.*;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class SourceClient {
    private static final java.util.regex.Pattern STREAM_ID=java.util.regex.Pattern.compile("[0-9]+");
    private static final java.util.regex.Pattern EXTENSION=java.util.regex.Pattern.compile("[a-zA-Z0-9]{1,8}");
    static final ConnectionPool TRANSPORT_POOL=new ConnectionPool(8,5,TimeUnit.MINUTES);
    private final OkHttpClient client = new OkHttpClient.Builder().connectionPool(TRANSPORT_POOL)
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.MINUTES).build();
    private final OkHttpClient guideClient=new OkHttpClient.Builder().connectionPool(TRANSPORT_POOL).connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).callTimeout(12,TimeUnit.SECONDS).build();
    public static HttpUrl validate(String value) {
        HttpUrl url = HttpUrl.parse(value.trim());
        if (url == null || !url.username().isEmpty() || !url.password().isEmpty())
            throw new IllegalArgumentException("Wpisz poprawny adres http:// lub https:// bez danych logowania w adresie.");
        return url;
    }
    private interface ReaderTask<T> { T read(Reader reader) throws IOException; }
    private <T> T get(HttpUrl url, ReaderTask<T> task) throws IOException { return request(client,url,task); }
    private <T> T request(OkHttpClient http,HttpUrl url,ReaderTask<T> task) throws IOException { return request(http,url,task,0); }
    static final String AGENT="TVPlayer/1.0", PLAYER_AGENT="VLC/3.0.20 LibVLC/3.0.20";
    /** Hosts whose panel only answered when the app presented itself as a common media player. */
    private static final Set<String> PLAYER_HOSTS=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static String agent(HttpUrl url) { return PLAYER_HOSTS.contains(url.host())?PLAYER_AGENT:AGENT; }
    /** Some IPTV panels hide endpoints (404/403) from unknown apps; retry once like a media player and remember it. */
    static boolean blockedAgent(int code) { return code==403 || code==404 || code==406 || code==451; }
    static void preferPlayerAgent(HttpUrl url) { PLAYER_HOSTS.add(url.host()); }
    static boolean transientStatus(int code) { return code==408 || code==429 || code==500 || code==502 || code==503 || code==504; }
    private static final class HttpFailure extends IOException {
        final int code; final boolean retrySoon;
        HttpFailure(Response response) {
            super("Serwer zwrócił błąd HTTP "+response.code()+"."); code=response.code();
            String after=response.header("Retry-After");
            // Do not immediately retry a panel asking us to wait longer (including HTTP dates).
            retrySoon=after==null || after.trim().matches("[01]");
        }
    }
    private <T> T request(OkHttpClient http,HttpUrl url,ReaderTask<T> task,long budgetNanos) throws IOException {
        long budget=budgetNanos>0?budgetNanos:TimeUnit.MILLISECONDS.toNanos(http.callTimeoutMillis());
        long deadline=System.nanoTime()+budget;
        String agent=agent(url); int retries=0;
        while(true) {
            long remaining=deadline-System.nanoTime();
            if(remaining<=0) throw new java.net.SocketTimeoutException("Upłynął czas pobierania danych.");
            if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Pobieranie anulowane.");
            Call call=http.newCall(new Request.Builder().url(url).header("User-Agent",agent).build());
            call.timeout().timeout(remaining,TimeUnit.NANOSECONDS);
            try(Response response=call.execute()) {
                if(!response.isSuccessful() && blockedAgent(response.code()) && agent.equals(AGENT)) {
                    agent=PLAYER_AGENT; continue;
                }
                if(!response.isSuccessful()) throw new HttpFailure(response);
                if(response.body()==null) throw new IOException("Pusta odpowiedź serwera.");
                if(agent.equals(PLAYER_AGENT)) preferPlayerAgent(url);
                try(Reader reader=response.body().charStream()) { return task.read(reader); }
            } catch(IOException failure) {
                // Slow panels often time out once or twice while generating a large list: retry those a few times.
                boolean slow=failure instanceof java.net.SocketTimeoutException || failure instanceof java.net.ConnectException
                    || failure instanceof InterruptedIOException && "timeout".equals(failure.getMessage());
                if(retries>=(slow?3:1) || Thread.currentThread().isInterrupted()
                    || failure instanceof HttpFailure && (!transientStatus(((HttpFailure)failure).code) || !((HttpFailure)failure).retrySoon)
                    || failure instanceof javax.net.ssl.SSLException
                    || failure instanceof com.google.gson.stream.MalformedJsonException
                    || deadline-System.nanoTime()<TimeUnit.SECONDS.toNanos(1)) throw failure;
                retries++;
                try { Thread.sleep(1000L*retries); } catch(InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); throw new InterruptedIOException("Pobieranie anulowane.");
                }
            }
        }
    }
    public List<Channel> m3u(String url) throws IOException {
        HttpUrl address = validate(url);
        return withPanelGuide(address, get(address, reader -> M3uParser.parse(reader, address.toString())));
    }
    /** Optional completion for an Xtream-generated M3U. Never opens a media stream. */
    List<Channel> missingM3uLiveChannels(String url,List<Channel> current) throws IOException {
        HttpUrl playlist=validate(url); String user=playlist.queryParameter("username"),pass=playlist.queryParameter("password");
        if(user==null || pass==null || user.isEmpty() || pass.isEmpty() || playlist.pathSize()==0 || !playlist.pathSegments().get(playlist.pathSize()-1).equalsIgnoreCase("get.php")) return Collections.emptyList();
        HttpUrl panel=playlist.newBuilder().query(null).fragment(null).removePathSegment(playlist.pathSize()-1).build();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(18);
        Map<String,String> categories=request(guideClient,api(panel,user,pass,"get_live_categories"),SourceClient::readCategories,deadline-System.nanoTime());
        Set<String> known=new HashSet<>(); for(Channel channel:current) known.add(TitleSearch.normalize(channel.group()));
        if(categories.values().stream().allMatch(name -> known.contains(TitleSearch.normalize(name)))) return Collections.emptyList();
        long remaining=deadline-System.nanoTime(); if(remaining<=0) throw new java.net.SocketTimeoutException("Upłynął czas uzupełniania kategorii TV.");
        List<Channel> authoritative=request(guideClient,api(panel,user,pass,"get_live_streams"),reader -> readStreams(reader,panel,user,pass,categories),remaining);
        return missingGroups(current,authoritative);
    }
    /**
     * Live channels of a panel-generated M3U, completed with categories the playlist left out and ranked in the panel's
     * category order (as the provider arranges its bouquets). Empty when the saved order and content are already right.
     */
    List<Channel> liveInProviderOrder(String url,List<Channel> current) throws IOException {
        HttpUrl playlist=validate(url); String user=playlist.queryParameter("username"),pass=playlist.queryParameter("password");
        if(user==null || pass==null || user.isEmpty() || pass.isEmpty() || playlist.pathSize()==0 || !playlist.pathSegments().get(playlist.pathSize()-1).equalsIgnoreCase("get.php")) return Collections.emptyList();
        HttpUrl panel=playlist.newBuilder().query(null).fragment(null).removePathSegment(playlist.pathSize()-1).build();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(18);
        Map<String,String> categories=request(guideClient,api(panel,user,pass,"get_live_categories"),SourceClient::readCategories,deadline-System.nanoTime());
        if(categories.isEmpty()) return Collections.emptyList();
        Set<String> known=new HashSet<>(); for(Channel channel:current) known.add(TitleSearch.normalize(channel.group()));
        List<Channel> all=new ArrayList<>(current);
        if(!categories.values().stream().allMatch(name -> known.contains(TitleSearch.normalize(name)))) {
            long remaining=deadline-System.nanoTime(); if(remaining<=0) throw new java.net.SocketTimeoutException("Upłynął czas uzupełniania kategorii TV.");
            List<Channel> authoritative=request(guideClient,api(panel,user,pass,"get_live_streams"),reader -> readStreams(reader,panel,user,pass,categories),remaining);
            all.addAll(missingGroups(current,authoritative));
        }
        List<Channel> ranked=BouquetOrder.applyRanks(all,categories);
        boolean changed=ranked.size()!=current.size();
        for(int i=0;!changed && i<current.size();i++) changed=ranked.get(i).bouquetOrder()!=current.get(i).bouquetOrder();
        return changed?ranked:Collections.emptyList();
    }
    static List<Channel> missingGroups(List<Channel> current,List<Channel> authoritative) {
        Set<String> known=new HashSet<>(),urls=new HashSet<>(); for(Channel channel:current) { known.add(TitleSearch.normalize(channel.group())); urls.add(channel.url()); }
        List<Channel> result=new ArrayList<>(); for(Channel channel:authoritative) if(channel.kind().equals("tv") && !known.contains(TitleSearch.normalize(channel.group())) && urls.add(channel.url())) result.add(channel);
        return result;
    }
    void cancel() { client.dispatcher().cancelAll(); guideClient.dispatcher().cancelAll(); }
    /**
     * Playlists generated by an Xtream panel (get.php?username=…&password=…) often stream from another host or CDN.
     * The EPG API lives on the panel, so point live channels at it instead of guessing from the stream address.
     */
    static List<Channel> withPanelGuide(HttpUrl playlist, List<Channel> channels) {
        String user = playlist.queryParameter("username"), pass = playlist.queryParameter("password");
        if (user == null || pass == null || user.isEmpty() || playlist.pathSize() == 0) return channels;
        HttpUrl panel = playlist.newBuilder().query(null).fragment(null).removePathSegment(playlist.pathSize() - 1).build();
        List<Channel> result = new ArrayList<>(channels.size());
        for (Channel c : channels) {
            String id = "";
            if (c.kind().equals("tv")) {
                HttpUrl stream = HttpUrl.parse(c.url());
                if (stream != null && stream.pathSize() > 0) id = stream.pathSegments().get(stream.pathSize() - 1).split("\\.")[0];
            }
            if (!id.matches("[0-9]+")) { result.add(c); continue; }
            String guide = api(panel, user, pass, "get_simple_data_table").newBuilder().addQueryParameter("stream_id", id).build().toString();
            result.add(c.withGuideSource(guide));
        }
        return result;
    }
    private static HttpUrl api(HttpUrl base, String user, String pass, String action) {
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
        Map<String, String> result = new LinkedHashMap<>();
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
        Map<String,Integer> ranks=new HashMap<>(); int order=0;
        for(String id:categories.keySet()) ranks.put(id,order++);
        HttpUrl streams=base.newBuilder().addPathSegment(kind.equals("tv")?"live":"movie").addPathSegment(user).addPathSegment(pass).build();
        HttpUrl series=api(base,user,pass,"get_series_info"),guideBase=api(base,user,pass,"get_simple_data_table");
        List<Channel> result = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            String id = "", name = "Kanał", category = "", epg = "", extension = "mp4", icon = ""; long added = 0;
            int archive=0,days=0;
            reader.beginObject();
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "stream_id": case "series_id": id = value(reader); break;
                    case "container_extension": extension = value(reader); break;
                    case "tv_archive": try { archive=Integer.parseInt(value(reader)); } catch(NumberFormatException ignored) {} break;
                    case "tv_archive_duration": try { days=Integer.parseInt(value(reader)); } catch(NumberFormatException ignored) {} break;
                    case "stream_icon": case "cover": if (icon.isEmpty()) icon = value(reader); else reader.skipValue(); break;
                    case "added": case "last_modified": try { added = Math.max(added, Long.parseLong(value(reader).trim())); } catch (NumberFormatException ignored) {} break;
                    case "name": name = value(reader); break;
                    case "category_id": category = value(reader); break;
                    case "epg_channel_id": epg = value(reader); break;
                    default: reader.skipValue();
                }
            }
            reader.endObject();
            if (!STREAM_ID.matcher(id).matches()) continue;
            if (!EXTENSION.matcher(extension).matches()) extension = "mp4";
            String url;
            if (kind.equals("series")) {
                url = series.newBuilder().addQueryParameter("series_id",id).build().toString();
            } else {
                url = streams.newBuilder().addPathSegment(id + (kind.equals("tv") ? ".ts" : "." + extension)).build().toString();
            }
            String guide=kind.equals("tv")?guideBase.newBuilder().addQueryParameter("stream_id",id).build().toString():"";
            result.add(new Channel(name,url,categories.getOrDefault(category,"Bez kategorii"),epg,kind,guide,archive==1?Math.max(0,days):0,"UTC",ranks.getOrDefault(category,Integer.MAX_VALUE),icon,kind.equals("tv")?0:added));
        }
        reader.endArray();
        if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Nieprawidłowa odpowiedź serwera.");
        return result;
    }
    public List<Channel> xtream(String server, String user, String pass) throws Exception {
        return xtream(server, user, pass, step -> { });
    }
    public List<Channel> xtream(String server, String user, String pass, java.util.function.Consumer<String> progress) throws Exception {
        progress.accept("Logowanie do serwera…");
        HttpUrl raw = validate(server);
        HttpUrl base = raw.newBuilder().query(null).fragment(null).build();
        if (!base.encodedPath().endsWith("/")) base = base.newBuilder().addPathSegment("").build();
        JsonObject root = get(api(base, user, pass, null), reader -> JsonParser.parseReader(reader).getAsJsonObject());
        JsonObject auth = root.has("user_info") && root.get("user_info").isJsonObject() ? root.getAsJsonObject("user_info") : null;
        if (auth == null || !auth.has("auth") || !"1".equals(auth.get("auth").getAsString())
                || (auth.has("status") && !"Active".equalsIgnoreCase(auth.get("status").getAsString())))
            throw new IOException("Logowanie odrzucone. Sprawdź dane i ważność konta.");
        progress.accept("Pobieranie kanałów TV…");
        Map<String, String> categories = get(api(base, user, pass, "get_live_categories"), SourceClient::readCategories);
        final HttpUrl streamBase = base;
        List<Channel> all = get(api(base, user, pass, "get_live_streams"), reader -> readStreams(reader, streamBase, user, pass, categories));
        String timezone="UTC";
        if(root.has("server_info") && root.get("server_info").isJsonObject())
            timezone=Guide.field(root.getAsJsonObject("server_info"),"timezone","UTC");
        for(int i=0;i<all.size();i++) { Channel c=all.get(i); all.set(i,c.withTimezone(timezone)); }
        progress.accept("Kanały TV: "+all.size()+". Pobieranie filmów…");
        Map<String,String> movieCategories = get(api(base,user,pass,"get_vod_categories"),SourceClient::readCategories);
        all.addAll(get(api(base,user,pass,"get_vod_streams"),reader -> readCatalog(reader,streamBase,user,pass,movieCategories,"movie")));
        progress.accept("Pozycje: "+all.size()+". Pobieranie seriali…");
        Map<String,String> seriesCategories = get(api(base,user,pass,"get_series_categories"),SourceClient::readCategories);
        all.addAll(get(api(base,user,pass,"get_series"),reader -> readCatalog(reader,streamBase,user,pass,seriesCategories,"series")));
        return all;
    }
    /** TV loads live TV first, and fetches the larger VOD catalogs only when selected. */
    public List<Channel> xtreamSection(String server,String user,String pass,String section,java.util.function.Consumer<String> progress) throws Exception {
        if(!Arrays.asList("tv","movie","series").contains(section)) throw new IllegalArgumentException("Nieprawidłowa sekcja.");
        HttpUrl base=validate(server).newBuilder().query(null).fragment(null).build();
        if(!base.encodedPath().endsWith("/")) base=base.newBuilder().addPathSegment("").build();
        progress.accept("Sprawdzanie konta…");
        JsonObject root=get(api(base,user,pass,null),reader -> JsonParser.parseReader(reader).getAsJsonObject());
        JsonObject auth=root.has("user_info") && root.get("user_info").isJsonObject()?root.getAsJsonObject("user_info"):null;
        if(auth==null || !"1".equals(field(auth,"auth","")) || (auth.has("status") && !"Active".equalsIgnoreCase(field(auth,"status","")))) throw new IOException("Logowanie odrzucone. Sprawdź dane i ważność konta.");
        String prefix=section.equals("tv")?"live":section.equals("movie")?"vod":"series";
        progress.accept("Pobieranie bukietów…");
        Map<String,String> categories=get(api(base,user,pass,"get_"+prefix+"_categories"),SourceClient::readCategories);
        progress.accept("Pobieranie katalogu…");
        final HttpUrl address=base;
        List<Channel> items=get(api(base,user,pass,section.equals("series")?"get_series":"get_"+prefix+"_streams"),reader -> readCatalog(reader,address,user,pass,categories,section));
        String zone=root.has("server_info") && root.get("server_info").isJsonObject()?field(root.getAsJsonObject("server_info"),"timezone","UTC"):"UTC";
        if(section.equals("tv")) for(int i=0;i<items.size();i++) items.set(i,items.get(i).withTimezone(zone));
        return items;
    }
    static HttpUrl accountEndpoint(Channel c) throws IOException {
        if(!c.guideUrl().isEmpty() && !c.guideUrl().startsWith("xmltv:")) return validate(c.guideUrl());
        HttpUrl url=validate(c.url());
        if(url.queryParameter("username")!=null && url.queryParameter("password")!=null && url.encodedPath().endsWith("/player_api.php")) return url;
        List<String> parts=url.pathSegments(); int index=-1;
        for(int i=0;i<parts.size();i++) if(parts.get(i).equals("live") || parts.get(i).equals("movie") || parts.get(i).equals("series")) { index=i; break; }
        if(index<0 && parts.size()>=3 && parts.get(parts.size()-1).matches("[0-9]+(?:\\.[a-zA-Z0-9]+)?")) {
            int tail=parts.size()-3; HttpUrl.Builder base=url.newBuilder().query(null);
            for(int i=parts.size()-1;i>=tail;i--) base.removePathSegment(i);
            return base.addPathSegment("player_api.php").addQueryParameter("username",parts.get(tail)).addQueryParameter("password",parts.get(tail+1)).build();
        }
        if(index<0 || parts.size()!=index+4) return null;
        HttpUrl.Builder base=url.newBuilder().query(null);
        for(int i=parts.size()-1;i>=index;i--) base.removePathSegment(i);
        return base.addPathSegment("player_api.php").addQueryParameter("username",parts.get(index+1)).addQueryParameter("password",parts.get(index+2)).build();
    }
    public List<Channel> refreshCategoryOrder(List<Channel> items,String section) throws IOException {
        if(items.isEmpty()) return items;
        HttpUrl account=accountEndpoint(items.get(0)); if(account==null) return items;
        String action=section.equals("tv")?"get_live_categories":section.equals("movie")?"get_vod_categories":"get_series_categories";
        HttpUrl endpoint=account.newBuilder().removeAllQueryParameters("stream_id").removeAllQueryParameters("series_id").setQueryParameter("action",action).build();
        Map<String,String> categories=get(endpoint,SourceClient::readCategories);
        if(categories.isEmpty()) return items;
        return BouquetOrder.applyRanks(items,categories);
    }
    public List<Channel> refreshCatalog(List<Channel> items) throws Exception { return refreshCatalog(items, step -> { }); }
    public List<Channel> refreshCatalog(List<Channel> items, java.util.function.Consumer<String> progress) throws Exception {
        if(items.isEmpty()) throw new IOException("Brak zapisanego konta. Dodaj źródło.");
        HttpUrl account=accountEndpoint(items.get(0));
        if(account==null) throw new IOException("Tej listy nie można odświeżyć przez Xtream. Dodaj ponownie adres M3U.");
        String user=account.queryParameter("username"),pass=account.queryParameter("password");
        HttpUrl server=account.newBuilder().query(null).removePathSegment(account.pathSize()-1).build();
        return xtream(server.toString(),user,pass,progress);
    }
    public List<Guide.Program> guide(Channel channel) throws IOException { return guide(channel,true); }
    List<Guide.Program> guide(Channel channel,boolean full) throws IOException {
        HttpUrl base=Guide.endpoint(channel); IOException failure=null; boolean responded=false;
        Map<Long,Guide.Program> combined=new TreeMap<>(); long now=System.currentTimeMillis()/1000;
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        for(String action:full?new String[]{"get_simple_data_table","get_short_epg"}:new String[]{"get_short_epg","get_simple_data_table"}) {
            HttpUrl.Builder request=base.newBuilder().setQueryParameter("action",action).removeAllQueryParameters("limit");
            if(action.equals("get_short_epg")) request.setQueryParameter("limit",full?"100":"4");
            try {
                long remaining=deadline-System.nanoTime(); if(remaining<=0) break;
                List<Guide.Program> items=request(guideClient,request.build(),reader -> Guide.parse(reader,channel.timezone()),Math.min(remaining,TimeUnit.SECONDS.toNanos(4)));
                responded=true; for(Guide.Program item:items) combined.put(item.start(),item);
                // Historical rows are not evidence that this endpoint provides current EPG.
                if(items.stream().anyMatch(p -> p.end()>now)) return new ArrayList<>(combined.values());
            } catch(IOException e) { failure=e; }
        }
        if(!responded && failure!=null) throw failure; return new ArrayList<>(combined.values());
    }
    private final EpgDiscovery discovery=new EpgDiscovery();
    List<HttpUrl> discoverGuides(Channel channel) throws IOException { return discovery.discover(channel); }
    private final OkHttpClient seriesClient=new OkHttpClient.Builder().connectionPool(TRANSPORT_POOL).connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).build();
    public SeriesDetails seriesDetails(Channel series) throws IOException {
        return request(seriesClient,validate(series.url()),reader -> SeriesDetails.parse(reader,series));
    }
    public void cancelSeriesRequest() { seriesClient.dispatcher().cancelAll(); }
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
                    String still = "";
                    if (episode.has("info") && episode.get("info").isJsonObject()) still = field(episode.getAsJsonObject("info"),"movie_image","");
                    String url = base.newBuilder().addPathSegment("series").addPathSegment(user).addPathSegment(pass).addPathSegment(id+"."+ext).build().toString();
                    result.add(new Channel(seriesName+" · "+number+" · "+title,url,"Sezon "+season.getKey(),"","episode").withLogo(still));
                }
            }
            return result;
        } catch (JsonParseException | IllegalStateException e) { throw new IOException("Nieprawidłowa odpowiedź z odcinkami.",e); }
    }
    private static String field(JsonObject o,String name,String fallback) {
        JsonElement e=o.get(name); return e!=null && e.isJsonPrimitive()?e.getAsString():fallback;
    }
}


