package pl.tvplayer;

import java.util.Locale;
import java.util.regex.Pattern;

public record Channel(String name, String url, String group, String epgId, String kind, String guideUrl, int archiveDays, String timezone, int bouquetOrder, String logo, long added) {
    public Channel(String name,String url,String group,String epgId,String kind,String guideUrl,int archiveDays,String timezone,int bouquetOrder,String logo) {
        this(name,url,group,epgId,kind,guideUrl,archiveDays,timezone,bouquetOrder,logo,0);
    }
    public Channel(String name,String url,String group,String epgId,String kind,String guideUrl,int archiveDays,String timezone,int bouquetOrder) {
        this(name,url,group,epgId,kind,guideUrl,archiveDays,timezone,bouquetOrder,"");
    }
    /** Same item with the provider's "added" time (seconds since 1970), used for "Ostatnio dodane". */
    Channel withAdded(long seconds) { return new Channel(name,url,group,epgId,kind,guideUrl,archiveDays,timezone,bouquetOrder,logo,Math.max(0,seconds)); }
    /** Same item with another bouquet rank. */
    Channel withOrder(int order) { return new Channel(name,url,group,epgId,kind,guideUrl,archiveDays,timezone,order,logo,added); }
    Channel withTimezone(String zone) { return new Channel(name,url,group,epgId,kind,guideUrl,archiveDays,zone,bouquetOrder,logo,added); }
    Channel withLogo(String image) { return new Channel(name,url,group,epgId,kind,guideUrl,archiveDays,timezone,bouquetOrder,image==null?"":image.trim(),added); }
    Channel withGuideSource(String source) { return new Channel(name,url,group,epgId,kind,source,archiveDays,timezone,bouquetOrder,logo,added); }
    private static final Pattern EPISODE = Pattern.compile(".*\\bs[0-9]{1,2}\\s?e[0-9]{1,3}\\b.*");
    private static final Pattern VIDEO_FILE = Pattern.compile(".*\\.(mp4|mkv|avi|mov|webm|m4v|wmv|flv|mpg|mpeg)$");
    private static final Pattern LIVE_FILE = Pattern.compile(".*\\.(ts|m3u8?)$");
    private static final Pattern VOD_GROUP = Pattern.compile(".*\\bvod\\b.*");
    private static final Pattern MOVIE_CHANNEL_GROUP = Pattern.compile(".*\\b(movies?|filmy)\\b.*");
    private static final Pattern SERIES_GROUP = Pattern.compile(".*\\b(series|seriale)\\b.*");
    public Channel(String name,String url,String group,String epgId,String kind,String guideUrl,int archiveDays,String timezone) {
        this(name,url,group,epgId,kind,guideUrl,archiveDays,timezone,Integer.MAX_VALUE);
    }
    public Channel(String name,String url,String group,String epgId,String kind) {
        this(name,url,group,epgId,kind,"",0,"UTC");
    }
    public Channel(String name, String url, String group, String epgId) {
        this(name, url, group, epgId, infer(url, group, name, epgId));
    }
    public Channel {
        if(url==null) url=""; if(group==null) group="Bez kategorii"; if(name==null) name=""; if(epgId==null) epgId="";
        if (kind == null || kind.isEmpty()) kind = infer(url, group, name, epgId);
        if(guideUrl==null) guideUrl=""; if(logo==null) logo=""; if(timezone==null || timezone.isEmpty()) timezone="UTC";
    }
    /** Older versions classified live channels such as "Polsat Film" as movies; used once when migrating. */
    static Channel repairKind(Channel c) {
        String inferred=infer(c.url,c.group,c.name,c.epgId);
        String path=streamPath(c.url);
        boolean liveAddress=path.contains("/live/") || LIVE_FILE.matcher(path).matches();
        boolean movieBouquet=MOVIE_CHANNEL_GROUP.matcher(c.group.toLowerCase(Locale.ROOT)).matches();
        if ((c.kind.equals("movie") || c.kind.equals("episode")) && inferred.equals("tv") && (liveAddress || movieBouquet || !c.epgId.isEmpty()))
            return new Channel(c.name, c.url, c.group, c.epgId, "tv", c.guideUrl, c.archiveDays, c.timezone, c.bouquetOrder, c.logo, c.added);
        return c;
    }
    /** v2 only guessed VOD from the bouquet for addresses without a known stream/file suffix. */
    static Channel repairMovieBouquet(Channel c) {
        String path=streamPath(c.url);
        if(!c.kind.equals("movie") || LIVE_FILE.matcher(path).matches() || VIDEO_FILE.matcher(path).matches()) return c;
        if(MOVIE_CHANNEL_GROUP.matcher(c.group.toLowerCase(Locale.ROOT)).matches() && infer(c.url,c.group,c.name,c.epgId).equals("tv"))
            return new Channel(c.name,c.url,c.group,c.epgId,"tv",c.guideUrl,c.archiveDays,c.timezone,c.bouquetOrder,c.logo,c.added);
        return c;
    }
    public String section() { return kind.equals("episode") ? "series" : kind; }
    /**
     * Classifies an M3U entry. The stream address decides first; channel names are never used for
     * movies, because live channels are often called "… Film" or "Kino …".
     */
    static String infer(String url, String group, String name) {
        return infer(url,group,name,"");
    }
    private static String streamPath(String url) {
        String value=url==null?"":url; int end=value.length(),query=value.indexOf('?'),fragment=value.indexOf('#');
        if(query>=0) end=Math.min(end,query); if(fragment>=0) end=Math.min(end,fragment);
        value=value.substring(0,end).toLowerCase(Locale.ROOT); int scheme=value.indexOf("://");
        if(scheme>=0) { int start=value.indexOf('/',scheme+3); value=start<0?"":value.substring(start); }
        return "/"+value;
    }
    private static String infer(String url,String group,String name,String epgId) {
        String path = streamPath(url);
        String title = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String category = group == null ? "" : group.toLowerCase(Locale.ROOT);
        if (path.contains("/series/")) return "episode";
        if (path.contains("/movie/") || path.contains("/movies/") || path.contains("/vod/")) return "movie";
        if (path.contains("/live/") || LIVE_FILE.matcher(path).matches()) return "tv";
        if (VIDEO_FILE.matcher(path).matches()) return EPISODE.matcher(title).matches() || SERIES_GROUP.matcher(category).matches() ? "episode" : "movie";
        if(epgId!=null && !epgId.isEmpty()) return "tv";
        if(EPISODE.matcher(title).matches()) return "episode";
        if (VOD_GROUP.matcher(category).matches()) return "movie";
        return "tv";
    }
}

