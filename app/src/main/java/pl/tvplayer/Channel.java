package pl.tvplayer;

public record Channel(String name, String url, String group, String epgId, String kind) {
    public Channel(String name, String url, String group, String epgId) {
        this(name, url, group, epgId, infer(url, group, name));
    }
    public Channel {
        if (kind == null || kind.isEmpty()) kind = infer(url, group, name);
    }
    public String section() { return kind.equals("episode") ? "series" : kind; }
    static String infer(String url, String group, String name) {
        String path = url.toLowerCase(java.util.Locale.ROOT).split("\\?", 2)[0];
        String text = (group + " " + name).toLowerCase(java.util.Locale.ROOT);
        if (path.contains("/series/") || text.matches(".*\\bs[0-9]{1,2}e[0-9]{1,3}\\b.*")) return "episode";
        if (path.contains("/movie/") || text.matches(".*\\b(vod|movies|filmy|film)\\b.*")
                || path.matches(".*\\.(mp4|mkv|avi|mov|webm)$")) return "movie";
        return "tv";
    }
}
