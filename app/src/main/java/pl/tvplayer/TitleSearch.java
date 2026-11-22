package pl.tvplayer;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Accent-insensitive title search, ranking and sorting shared by catalog screens and the live channel queue. */
final class TitleSearch {
    static final String PROVIDER = "provider", ADDED = "added", AZ = "az", ZA = "za", YEAR = "year";
    static final String[] MODES = {PROVIDER, ADDED, AZ, ZA, YEAR};
    static final String[] LABELS = {"Kolejność dostawcy", "Ostatnio dodane", "Tytuł A–Z", "Tytuł Z–A", "Rok: najnowsze"};
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern YEAR_PATTERN = Pattern.compile("(?<![0-9])(19[2-9][0-9]|20[0-4][0-9])(?![0-9])");
    /** Tags providers put in front of titles ("PL - ", "|EN| ", "4K-PL"); ignored when sorting by title. */
    private static final Set<String> TAGS = new HashSet<>(Arrays.asList("pl", "en", "eng", "uk", "us", "de", "fr", "es", "it", "nl", "tr", "ru",
        "4k", "uhd", "fhd", "hd", "sd", "hevc", "vod", "multi", "sub", "subs", "lektor", "dubbing", "napisy", "vip", "new", "top"));
    private TitleSearch() { }

    /** Lower case, without Polish/other diacritics, punctuation collapsed to single spaces. */
    static String normalize(String value) {
        if (value == null || value.isEmpty()) return "";
        String text = value.toLowerCase(Locale.ROOT).replace('ł', 'l').replace('ø', 'o').replace('ß', 's').replace('æ', 'a');
        boolean ascii = true;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) > 127) { ascii = false; break; }
        if (!ascii) text = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        return SEPARATORS.matcher(text).replaceAll(" ").trim();
    }

    /** Normalized title without leading language/quality tags, used for A–Z ordering and prefix matches. */
    static String titleKey(String normalized) {
        int start = 0, removed = 0;
        while (removed < 3) {
            int space = normalized.indexOf(' ', start);
            if (space < 0) break;
            if (!TAGS.contains(normalized.substring(start, space))) break;
            start = space + 1; removed++;
        }
        return start == 0 ? normalized : normalized.substring(start);
    }

    static String[] tokens(String query) {
        String normalized = normalize(query);
        return normalized.isEmpty() ? new String[0] : normalized.split(" ");
    }

    /** -1 when a token is missing; otherwise 0 title starts with the query, 1 every word starts a title word, 2 anywhere. */
    static int score(String key, String[] tokens) {
        if (tokens.length == 0) return 2;
        boolean wordStarts = true;
        for (String token : tokens) {
            int at = key.indexOf(token);
            if (at < 0) return -1;
            if (wordStarts) {
                boolean start = false;
                while (at >= 0) { if (at == 0 || key.charAt(at - 1) == ' ') { start = true; break; } at = key.indexOf(token, at + 1); }
                wordStarts = start;
            }
        }
        String phrase = String.join(" ", tokens);
        if (key.startsWith(phrase) || titleKey(key).startsWith(phrase)) return 0;
        return wordStarts ? 1 : 2;
    }

    static boolean matches(String name, String query) { return score(normalize(name), tokens(query)) >= 0; }

    static int year(String name) {
        Matcher m = YEAR_PATTERN.matcher(name == null ? "" : name); int found = 0;
        while (m.find()) found = Integer.parseInt(m.group(1));
        return found;
    }

    /** Sorts positions of items; keys are normalized names aligned with items. Stable for equal values. */
    static void sort(List<Integer> positions, List<Channel> items, String[] keys, String mode) {
        Comparator<Integer> order;
        switch (mode) {
            case ADDED: order = Comparator.comparingLong((Integer i) -> items.get(i).added()).reversed(); break;
            case AZ: case ZA: {
                // Compute each title key once; comparators would otherwise allocate per comparison on 50k+ titles.
                Map<Integer,String> titles = new HashMap<>(positions.size() * 2);
                for (Integer i : positions) titles.put(i, titleKey(keys[i]));
                order = Comparator.comparing(titles::get);
                if (mode.equals(ZA)) order = order.reversed();
                break;
            }
            case YEAR: order = Comparator.comparingInt((Integer i) -> year(items.get(i).name())).reversed(); break;
            default: return;
        }
        positions.sort(order);
    }

    static String label(String mode) {
        for (int i = 0; i < MODES.length; i++) if (MODES[i].equals(mode)) return LABELS[i];
        return LABELS[0];
    }
}
