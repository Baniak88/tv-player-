package pl.tvplayer;

import com.google.gson.*;
import okhttp3.HttpUrl;
import java.io.*;
import java.util.*;

/** Optional provider metadata. Playback always uses the original library stream. */
public record MovieDetails(String title,String overview,String cover,String backdrop,String year,String rating,String genre,String director,String cast,long durationSeconds) {
    private static String value(JsonObject object,String key) { JsonElement v=object.get(key); if(v==null || !v.isJsonPrimitive()) return ""; String s=v.getAsString().trim(); return s.equalsIgnoreCase("null") || s.equalsIgnoreCase("n/a")?"":s; }
    private static JsonObject object(JsonObject root,String key) { JsonElement v=root.get(key); return v!=null && v.isJsonObject()?v.getAsJsonObject():new JsonObject(); }
    private static String first(String... values) { for(String s:values) if(s!=null && !s.isEmpty()) return s; return ""; }
    private static String image(String... values) { for(String s:values) if(s!=null && !s.isEmpty()) { HttpUrl u=HttpUrl.parse(s); if(u!=null && u.username().isEmpty() && u.password().isEmpty()) return u.toString(); } return ""; }
    static MovieDetails fallback(Channel source) { return new MovieDetails(SeriesDetails.cleanSeriesTitle(source.name()),"",image(source.logo()),"","","","","","",0); }
    /** Derive the API only for a recognizable Xtream movie URL, including portal prefixes. */
    static HttpUrl endpoint(Channel source) {
        HttpUrl url=HttpUrl.parse(source.url()); if(url==null || !url.username().isEmpty() || !url.password().isEmpty()) return null;
        List<String> path=url.pathSegments();
        for(int i=path.size()-4;i>=0;i--) {
            if(!path.get(i).equals("movie") || i+4!=path.size()) continue;
            String file=path.get(i+3); if(!file.matches("[0-9]+\\.[a-zA-Z0-9]{1,8}") || path.get(i+1).isEmpty() || path.get(i+2).isEmpty()) return null;
            HttpUrl.Builder base=url.newBuilder().encodedPath("/").query(null).fragment(null); for(int p=0;p<i;p++) base.addPathSegment(path.get(p));
            return base.addPathSegment("player_api.php").addQueryParameter("username",path.get(i+1)).addQueryParameter("password",path.get(i+2)).addQueryParameter("action","get_vod_info").addQueryParameter("vod_id",file.substring(0,file.indexOf('.'))).build();
        }
        return null;
    }
    static MovieDetails parse(Reader reader,Channel source) throws IOException {
        try {
            JsonElement parsed=JsonParser.parseReader(reader); if(!parsed.isJsonObject()) throw new IOException("Nieprawidłowe dane filmu.");
            JsonObject root=parsed.getAsJsonObject(),info=object(root,"info"),movie=object(root,"movie_data");
            String title=SeriesDetails.cleanSeriesTitle(first(value(info,"name"),value(movie,"name"),source.name()));
            String backdrop=""; JsonElement images=info.get("backdrop_path");
            if(images!=null && images.isJsonArray()) for(JsonElement item:images.getAsJsonArray()) if(item.isJsonPrimitive()) { backdrop=image(item.getAsString()); if(!backdrop.isEmpty()) break; }
            if(backdrop.isEmpty()) backdrop=image(value(info,"backdrop_path"),value(info,"backdrop"));
            String date=first(value(info,"releasedate"),value(info,"releaseDate"),value(info,"release_date"),value(info,"year")),year=date.matches("[0-9]{4}.*")?date.substring(0,4):"";
            return new MovieDetails(title,first(value(info,"plot"),value(info,"description"),value(info,"overview")),image(value(info,"cover_big"),value(info,"movie_image"),value(info,"cover"),value(movie,"stream_icon"),source.logo()),backdrop,year,value(info,"rating"),value(info,"genre"),value(info,"director"),first(value(info,"cast"),value(info,"actors")),SeriesDetails.duration(info));
        } catch(JsonParseException | IllegalStateException | IllegalArgumentException e) { throw new IOException("Nie można odczytać danych filmu.",e); }
    }
}
