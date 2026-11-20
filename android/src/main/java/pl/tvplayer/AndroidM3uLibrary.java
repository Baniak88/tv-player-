package pl.tvplayer;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import okhttp3.HttpUrl;

final class AndroidM3uLibrary {
    private static final Pattern EPISODE = Pattern.compile(
            "(?i)(?<![a-z0-9])(?:s\\s*\\d{1,3}[ ._-]*e\\s*\\d{1,4}|\\d{1,2}x\\d{1,3})(?![a-z0-9])");
    private static final Pattern SERIES_GROUP = Pattern.compile("(?i)\\b(?:series|seriale|seriali)\\b");

    static List<Channel> load(SourceClient source, String address, Consumer<String> progress) throws IOException {
        List<Channel> items = source.m3u(address);
        boolean hasSeries = false;
        for (int i = 0; i < items.size(); i++) {
            Channel item = items.get(i);
            if (!item.section().equals("series") && item.epgId().isEmpty()
                    && SERIES_GROUP.matcher(item.group()).find() && EPISODE.matcher(item.name()).find()) {
                HttpUrl url = HttpUrl.parse(item.url());
                if (url != null && !url.encodedPath().toLowerCase(Locale.ROOT).contains("/live/")) {
                    item = new Channel(item.name(), item.url(), item.group(), item.epgId(), "episode",
                            item.guideUrl(), item.archiveDays(), item.timezone(), item.bouquetOrder(), item.logo(), item.added());
                    items.set(i, item);
                }
            }
            hasSeries |= item.section().equals("series");
        }
        if (hasSeries) return items;
        HttpUrl playlist = SourceClient.validate(address);
        if (!playlist.pathSegments().get(playlist.pathSize()-1).equalsIgnoreCase("get.php")) return items;
        String user = playlist.queryParameter("username"), password = playlist.queryParameter("password");
        if (user == null || user.isEmpty() || password == null || password.isEmpty()) return items;
        HttpUrl panel = playlist.newBuilder().query(null).fragment(null).removePathSegment(playlist.pathSize()-1).build();
        try {
            items.addAll(source.xtreamSection(panel.toString(), user, password, "series", progress));
        } catch (Exception unavailable) {
            // The M3U remains usable when its optional series catalogue is unavailable.
        }
        return items;
    }
}
