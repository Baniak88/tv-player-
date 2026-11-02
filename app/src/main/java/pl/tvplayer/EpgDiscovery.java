package pl.tvplayer;

import okhttp3.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

/** Some Xtream providers declare their guide only in the generated playlist header. */
final class EpgDiscovery {
    private record Entry(List<HttpUrl> urls,long until) {}
    private final Map<String,Entry> cache=new LinkedHashMap<>();
    private final OkHttpClient client=new OkHttpClient.Builder().connectionPool(SourceClient.TRANSPORT_POOL)
        .connectTimeout(5,TimeUnit.SECONDS).readTimeout(5,TimeUnit.SECONDS).callTimeout(8,TimeUnit.SECONDS).build();
    synchronized List<HttpUrl> discover(Channel channel) throws IOException {
        HttpUrl account=SourceClient.accountEndpoint(channel); if(account==null) return Collections.emptyList();
        HttpUrl playlist=account.newBuilder().query(null).removePathSegment(account.pathSize()-1).addPathSegment("get.php")
            .addQueryParameter("username",account.queryParameter("username")).addQueryParameter("password",account.queryParameter("password"))
            .addQueryParameter("type","m3u_plus").addQueryParameter("output","ts").build();
        String key=playlist.toString(); Entry old=cache.get(key); long now=System.currentTimeMillis();
        if(old!=null && old.until()>now) return old.urls();
        List<HttpUrl> urls=Collections.emptyList();
        try(Response response=client.newCall(new Request.Builder().url(playlist).header("User-Agent","TVPlayer/0.8.8").build()).execute()) {
            if(!response.isSuccessful() || response.body()==null) throw new IOException("Nagłówek playlisty: HTTP "+response.code()+".");
            urls=header(response.body().charStream(),response.request().url());
            return urls;
        } finally {
            cache.put(key,new Entry(urls,now+(urls.isEmpty()?300000:3600000)));
            while(cache.size()>8) cache.remove(cache.keySet().iterator().next());
        }
    }
    static List<HttpUrl> header(Reader reader,HttpUrl base) throws IOException {
        // Read just the header, never materialize a provider's entire catalogue for EPG.
        StringBuilder line=new StringBuilder(); int c;
        while(line.length()<65536 && (c=reader.read())!=-1) { if(c=='\n') break; if(c!='\r' && c!='\uFEFF') line.append((char)c); }
        if(!line.toString().trim().startsWith("#EXTM3U")) return Collections.emptyList();
        Matcher fields=Pattern.compile("(?i)(?:x-tvg-url|url-tvg|tvg-url)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s]+))").matcher(line);
        LinkedHashSet<HttpUrl> result=new LinkedHashSet<>();
        while(fields.find()) {
            String value=fields.group(1)!=null?fields.group(1):fields.group(2)!=null?fields.group(2):fields.group(3);
            for(String candidate:value.replace("&amp;","&").trim().split("[,\\s]+")) {
                if(candidate.isEmpty()) continue; HttpUrl url=base.resolve(candidate);
                if(url!=null && url.username().isEmpty() && url.password().isEmpty()) result.add(url);
                if(result.size()==3) return new ArrayList<>(result);
            }
        }
        return new ArrayList<>(result);
    }
}
