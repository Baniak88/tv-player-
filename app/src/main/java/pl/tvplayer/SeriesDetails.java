package pl.tvplayer;

import com.google.gson.*;
import okhttp3.HttpUrl;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/** Provider metadata for a series. Display titles are separate from stream identities. */
public record SeriesDetails(String title,String overview,String cover,String backdrop,String year,String rating,String genre,String cast,List<Season> seasons) {
    public record Episode(Channel channel,int season,int number,String title,String overview,String image,long durationSeconds) {}
    public record Season(int number,String title,List<Episode> episodes) {}
    private static String value(JsonObject object,String name) { JsonElement v=object.get(name); if(v==null || !v.isJsonPrimitive()) return ""; String s=v.getAsString().trim(); return s.equalsIgnoreCase("null") || s.equalsIgnoreCase("n/a")?"":s; }
    private static JsonObject object(JsonObject object,String name) { JsonElement v=object.get(name); return v!=null && v.isJsonObject()?v.getAsJsonObject():new JsonObject(); }
    private static int number(String value,int fallback) { try { return Integer.parseInt(value); } catch(NumberFormatException e) { return fallback; } }
    private static String first(String... strings) { for(String value:strings) if(value!=null && !value.isEmpty()) return value; return ""; }
    static String cleanSeriesTitle(String title) { return title.replaceFirst("(?i)^(?:PL|POL|EN|UK|US|DE|FR|ES|IT)\\s*(?:\\||:| - )\\s*","").trim(); }
    static String cleanEpisodeTitle(String raw,String series,int episode) {
        String text=raw.trim(),display=cleanSeriesTitle(series);
        for(String prefix:new String[]{series,display}) if(!prefix.isEmpty()) text=text.replaceFirst("(?iu)^"+Pattern.quote(prefix)+"\\s*[-·:|]*\\s*","");
        text=cleanSeriesTitle(text);
        if(!display.isEmpty()) text=text.replaceFirst("(?iu)^"+Pattern.quote(display)+"\\s*[-·:|]*\\s*","");
        text=text.replaceFirst("(?i)^(?:S[0-9]+\\s*E[0-9]+|[0-9]+x[0-9]+|(?:Odcinek|Episode)\\s*[0-9]+)\\s*[-·:|]?\\s*","");
        text=text.replaceFirst("^"+episode+"\\s*[.·:-]\\s*","").trim();
        return text.isEmpty()?"Odcinek "+episode:text;
    }
    private static String image(String... values) { for(String value:values) if(value!=null && !value.isEmpty()) { HttpUrl url=HttpUrl.parse(value); if(url!=null && url.username().isEmpty() && url.password().isEmpty()) return url.toString(); } return ""; }
    private static String backdrop(JsonObject info) {
        JsonElement value=info.get("backdrop_path");
        if(value!=null && value.isJsonArray()) for(JsonElement item:value.getAsJsonArray()) if(item.isJsonPrimitive()) { String url=image(item.getAsString()); if(!url.isEmpty()) return url; }
        return image(SeriesDetails.value(info,"backdrop_path"),SeriesDetails.value(info,"backdrop"));
    }
    static long duration(JsonObject info) {
        try { long seconds=Long.parseLong(value(info,"duration_secs")); if(seconds>0) return seconds; } catch(NumberFormatException ignored) {}
        String[] parts=value(info,"duration").split(":"); long result=0;
        try { if(parts.length>=2 && parts.length<=3) { for(String part:parts) result=result*60+Integer.parseInt(part); return Math.max(0,result); } } catch(NumberFormatException ignored) {}
        return 0;
    }
    static SeriesDetails parse(Reader reader,Channel source) throws IOException {
        try {
            JsonElement parsed=JsonParser.parseReader(reader); if(!parsed.isJsonObject()) throw new IOException("Nieprawidłowe dane serialu.");
            JsonObject root=parsed.getAsJsonObject(),info=object(root,"info");
            String title=cleanSeriesTitle(first(value(info,"name"),source.name()));
            String overview=first(value(info,"plot"),value(info,"overview"),value(info,"description"));
            String cover=image(value(info,"cover_big"),value(info,"cover"),source.logo()),backdrop=backdrop(info);
            String date=first(value(info,"releaseDate"),value(info,"release_date"),value(info,"year")),year=date.matches("[0-9]{4}.*")?date.substring(0,4):"";
            Map<Integer,String> names=new HashMap<>(); JsonElement seasonData=root.get("seasons");
            if(seasonData!=null && seasonData.isJsonArray()) for(JsonElement entry:seasonData.getAsJsonArray()) if(entry.isJsonObject()) { JsonObject season=entry.getAsJsonObject(); int n=number(value(season,"season_number"),-1); String name=value(season,"name"); if(n>=0 && !name.isEmpty()) names.put(n,name); }
            HttpUrl address=SourceClient.validate(source.url()); String user=address.queryParameter("username"),password=address.queryParameter("password");
            if(user==null || password==null) throw new IOException("Brak danych konta serialu.");
            HttpUrl base=address.newBuilder().query(null).removePathSegment(address.pathSize()-1).build();
            TreeMap<Integer,List<Episode>> groups=new TreeMap<>(); JsonElement data=root.get("episodes");
            if(data!=null && !data.isJsonNull() && !(data.isJsonArray() && data.getAsJsonArray().isEmpty())) {
                if(!data.isJsonObject()) throw new IOException("Nieprawidłowa lista odcinków.");
                for(Map.Entry<String,JsonElement> group:data.getAsJsonObject().entrySet()) {
                    if(!group.getValue().isJsonArray()) continue;
                    for(JsonElement entry:group.getValue().getAsJsonArray()) {
                        if(!entry.isJsonObject()) continue; JsonObject ep=entry.getAsJsonObject(),detail=object(ep,"info"); String id=value(ep,"id"); if(!id.matches("[0-9]+")) continue;
                        int season=number(value(ep,"season"),number(group.getKey(),1)),ordinal=number(value(ep,"episode_num"),groups.getOrDefault(season,Collections.emptyList()).size()+1);
                        String extension=value(ep,"container_extension"); if(!extension.matches("[a-zA-Z0-9]{1,8}")) extension="mp4";
                        String name=cleanEpisodeTitle(value(ep,"title"),source.name(),ordinal),still=image(value(detail,"movie_image"),value(detail,"cover_big"));
                        String url=base.newBuilder().addPathSegment("series").addPathSegment(user).addPathSegment(password).addPathSegment(id+"."+extension).build().toString();
                        Channel channel=new Channel(title+" · S"+season+":E"+ordinal+" · "+name,url,"Sezon "+season,"","episode").withLogo(still);
                        groups.computeIfAbsent(season,k -> new ArrayList<>()).add(new Episode(channel,season,ordinal,name,first(value(detail,"plot"),value(detail,"overview")),still,duration(detail)));
                    }
                }
            }
            List<Season> seasons=new ArrayList<>(); for(Map.Entry<Integer,List<Episode>> group:groups.entrySet()) { group.getValue().sort(Comparator.comparingInt(Episode::number)); seasons.add(new Season(group.getKey(),names.getOrDefault(group.getKey(),"Sezon "+group.getKey()),Collections.unmodifiableList(group.getValue()))); }
            return new SeriesDetails(title,overview,cover,backdrop,year,value(info,"rating"),value(info,"genre"),value(info,"cast"),Collections.unmodifiableList(seasons));
        } catch(JsonParseException | IllegalStateException | IllegalArgumentException e) { throw new IOException("Nie można odczytać danych serialu.",e); }
    }
    public List<Episode> allEpisodes() { List<Episode> result=new ArrayList<>(); for(Season season:seasons) result.addAll(season.episodes()); return result; }
}
