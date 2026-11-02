package pl.tvplayer;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

final class MovieRepository {
    private record Cached(MovieDetails details,long until) {}
    private static final Map<String,Cached> CACHE=new LinkedHashMap<>();
    private final OkHttpClient http=new OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(12,TimeUnit.SECONDS).callTimeout(15,TimeUnit.SECONDS).build();
    MovieDetails load(Channel source) throws IOException {
        synchronized(CACHE) { Cached hit=CACHE.get(source.url()); if(hit!=null && hit.until()>System.currentTimeMillis()) return hit.details(); }
        HttpUrl endpoint=MovieDetails.endpoint(source); if(endpoint==null) return MovieDetails.fallback(source);
        try(Response response=http.newCall(new Request.Builder().url(endpoint).header("User-Agent","TVPlayer/"+BuildConfig.VERSION_NAME).build()).execute()) {
            if(!response.isSuccessful() || response.body()==null) throw new IOException("Nie można pobrać opisu filmu.");
            MovieDetails details; try(Reader reader=response.body().charStream()) { details=MovieDetails.parse(reader,source); }
            synchronized(CACHE) { CACHE.put(source.url(),new Cached(details,System.currentTimeMillis()+900000)); while(CACHE.size()>30) CACHE.remove(CACHE.keySet().iterator().next()); }
            return details;
        }
    }
    void cancel() { http.dispatcher().cancelAll(); }
}
