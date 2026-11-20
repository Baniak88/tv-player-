package pl.tvplayer;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import okhttp3.HttpUrl;

/**
 * Series for M3U sources. Many Xtream panels generate get.php playlists with live channels and films only;
 * their series exist solely in the panel API (get_series), which is what other IPTV players read as well.
 */
final class M3uSeries {
    private static final Pattern EPISODE = Pattern.compile(
            "(?i)(?<![a-z0-9])(?:s\\s*\\d{1,3}[ ._-]*e\\s*\\d{1,4}|\\d{1,2}x\\d{1,3})(?![a-z0-9])");
    private static final Pattern SERIES_GROUP = Pattern.compile("(?i)\\b(?:series|seriale|seriali)\\b");
    private M3uSeries() { }

    /** Downloads the playlist and, for panel playlists, uses the panel's series when they are fuller than the playlist's. */
    static List<Channel> load(SourceClient source, String address, Consumer<String> progress) throws java.io.IOException {
        List<Channel> items = new java.util.ArrayList<>(source.m3u(address));
        for (int i = 0; i < items.size(); i++) {
            Channel item = items.get(i);
            // "S01E02" titles inside a "Series" bouquet are episodes even when the address looks like a film.
            if (!item.section().equals("series") && item.epgId().isEmpty() && SERIES_GROUP.matcher(item.group()).find() && EPISODE.matcher(item.name()).find()) {
                HttpUrl url = HttpUrl.parse(item.url());
                if (url != null && !url.encodedPath().toLowerCase(Locale.ROOT).contains("/live/")) {
                    item = new Channel(item.name(), item.url(), item.group(), item.epgId(), "episode", item.guideUrl(), item.archiveDays(), item.timezone(), item.bouquetOrder(), item.logo(), item.added());
                    items.set(i, item);
                }
            }
        }
        return preferFuller(items, panelSeries(source, address, progress));
    }

    /**
     * Keeps whichever series catalogue is fuller: the playlist's own (it may carry just a stray title or two)
     * or the panel's. Other sections are never touched.
     */
    static List<Channel> preferFuller(List<Channel> items, List<Channel> panel) {
        if (panel.isEmpty() || groups(panel) <= groups(items) && titles(panel) <= titles(items)) return items;
        List<Channel> result = new java.util.ArrayList<>(items.size() + panel.size());
        for (Channel item : items) if (!item.section().equals("series")) result.add(item);
        result.addAll(panel); return result;
    }
    /** Number of series categories in a list (only its series entries count). */
    static int groups(List<Channel> items) { java.util.Set<String> names = new java.util.HashSet<>(); for (Channel c : items) if (c.section().equals("series")) names.add(c.group()); return names.size(); }
    private static int titles(List<Channel> items) { int count = 0; for (Channel c : items) if (c.section().equals("series")) count++; return count; }

    /** The series catalogue of the panel behind a get.php playlist; empty for plain playlists or when the panel refuses. */
    static List<Channel> panelSeries(SourceClient source, String address, Consumer<String> progress) {
        try {
            HttpUrl playlist = SourceClient.validate(address);
            if (playlist.pathSize() == 0 || !playlist.pathSegments().get(playlist.pathSize() - 1).equalsIgnoreCase("get.php")) return Collections.emptyList();
            String user = playlist.queryParameter("username"), password = playlist.queryParameter("password");
            if (user == null || user.isEmpty() || password == null || password.isEmpty()) return Collections.emptyList();
            HttpUrl panel = playlist.newBuilder().query(null).fragment(null).removePathSegment(playlist.pathSize() - 1).build();
            return source.xtreamSection(panel.toString(), user, password, "series", progress);
        } catch (Exception unavailable) {
            return Collections.emptyList(); // the playlist stays usable without its optional series catalogue
        }
    }
}
