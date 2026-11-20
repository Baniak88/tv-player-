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

    /** Downloads the playlist and adds the panel's series when the playlist itself carries none. */
    static List<Channel> load(SourceClient source, String address, Consumer<String> progress) throws java.io.IOException {
        List<Channel> items = new java.util.ArrayList<>(source.m3u(address)); boolean hasSeries = false;
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
            hasSeries |= item.section().equals("series");
        }
        if (!hasSeries) items.addAll(panelSeries(source, address, progress));
        return items;
    }

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
