package pl.tvplayer;

import java.util.Locale;
import java.util.regex.Pattern;

public record Channel(String name, String url, String group, String epgId, String kind, String guideUrl, int archiveDays, String timezone, int bouquetOrder, String logo) {
    public Channel(String name,String url,String group,String epgId,String kind,String guideUrl,int archiveDays,String timezone,int bouquetOrder) {
        this(name,url,group,epgId,kind,guideUrl,archiveDays,timezone,bouquetOrder,"");
    }
    /** Same item with another bouquet rank. */
    Channel withOrder(int order) { return new Channel(name,url,group,epgId,kind,guideUrl,archiveDays,timezone,order,logo); }
    Channel withTimezone(String zone) { return new Channel(name,url,group,epgId,kind,guideUrl,archiveDays,zone,bouquetOrder,logo); }
    Channel withLogo(String image) { return new Channel(name,url,group,epgId,kind,guideUrl,archiveDays,timezone,bouquetOrder,image==null?"":image.trim()); }
    Channel withGuideSource(String source) { return new Channel(name,url,group,epgId,kind,source,archiveDays,timezone,bouquetOrder,logo); }
    private static final Pattern EPISODE = Pattern.compile(".*\\bs[0-9]{1,2}\\s?e[0-9]{1,3}\\b.*");
    private static final Pattern VIDEO_FILE = Pattern.compile(".*\\.(mp4|mkv|avi|mov|webm|m4v|wmv|flv|mpg|mpeg)$");
    private static final Pattern LIVE_FILE = Pattern.compile(".*\\.(ts|m3u8?)$");
    private static final Pattern VOD_GROUP = Pattern.compile(".*\\b(vod|movies?|filmy)\\b.*");
    private static final Pattern SERIES_GROUP = Pattern.compile(".*\\b(series|seriale)\\b.*");
    public Channel(String name,String url,String group,String epgId,String kind,String guideUrl,int archiveDays,String timezone) {
        this(name,url,group,epgId,kind,guideUrl,archiveDays,timezone,Integer.MAX_VALUE);
    }
    public Channel(String name,String url,String group,String epgId,String kind) {
        this(name,url,group,epgId,kind,"",0,"UTC");
    }
    public Channel(String name, String url, String group, String epgId) {
        this(name, url, group, epgId, infer(url, group, name));
    }
    public Channel {
        if(url==null) url=""; if(group==null) group="Bez kategorii"; if(name==null) name="";
        if (kind == null || kind.isEmpty()) kind = infer(url, group, name);
        if(guideUrl==null) guideUrl=""; if(logo==null) logo=""; if(timezone==null || timezone.isEmpty()) timezone="UTC";
    }
    /** Older versions classified live channels such as "Polsat Film" as movies; used once when migrating. */
    static Channel repairKind(Channel c) {
        if ((c.kind.equals("movie") || c.kind.equals("episode")) && infer(c.url, c.group, c.name).equals("tv"))
            return new Channel(c.name, c.url, c.group, c.epgId, "tv", c.guideUrl, c.archiveDays, c.timezone, c.bouquetOrder, c.logo);
        return c;
    }
    public String section() { return kind.equals("episode") ? "series" : kind; }
    /**
     * Classifies an M3U entry. The stream address decides first; channel names are never used for
     * movies, because live channels are often called "… Film" or "Kino …".
     */
    static String infer(String url, String group, String name) {
        String path = "/" + url.toLowerCase(Locale.ROOT).split("[?#]", 2)[0].replaceFirst("^[a-z][a-z0-9+.-]*://[^/]*", "");
        String title = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String category = group == null ? "" : group.toLowerCase(Locale.ROOT);
        if (path.contains("/series/")) return "episode";
        if (path.contains("/movie/") || path.contains("/movies/") || path.contains("/vod/")) return "movie";
        if (path.contains("/live/") || LIVE_FILE.matcher(path).matches()) return "tv";
        if (VIDEO_FILE.matcher(path).matches()) return EPISODE.matcher(title).matches() || SERIES_GROUP.matcher(category).matches() ? "episode" : "movie";
        if (VOD_GROUP.matcher(category).matches()) return "movie";
        return "tv";
    }
}
