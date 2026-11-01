package pl.tvplayer;

import java.net.URI;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class M3uParser {
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w-]+)=\"([^\"]*)\"");
    public static List<Channel> parse(String text, String baseUrl) {
        try { return parse(new StringReader(text), baseUrl); }
        catch (IOException impossible) { throw new java.io.UncheckedIOException(impossible); }
    }
    public static List<Channel> parse(Reader source, String baseUrl) throws IOException {
        BufferedReader reader = source instanceof BufferedReader ? (BufferedReader) source : new BufferedReader(source);
        List<Channel> channels = new ArrayList<>();
        String name = "", group = "Bez kategorii", epg = "", logo = "";
        String raw;
        while ((raw = reader.readLine()) != null) {
            String line = raw.replace("\uFEFF", "").trim();
            if (line.startsWith("#EXTINF:")) {
                int comma = -1;
                boolean quoted = false;
                for (int i = 0; i < line.length(); i++) {
                    if (line.charAt(i) == '"') quoted = !quoted;
                    if (line.charAt(i) == ',' && !quoted) { comma = i; break; }
                }
                name = comma >= 0 ? line.substring(comma + 1).trim() : "Kanał";
                group = "Bez kategorii"; epg = ""; logo = "";
                Matcher matcher = ATTRIBUTE.matcher(comma >= 0 ? line.substring(0, comma) : line);
                while (matcher.find()) {
                    if (matcher.group(1).equals("group-title")) group = matcher.group(2);
                    if (matcher.group(1).equals("tvg-id")) epg = matcher.group(2);
                    if (matcher.group(1).equals("tvg-logo")) logo = matcher.group(2);
                    if (matcher.group(1).equals("tvg-name") && name.isEmpty()) name = matcher.group(2);
                }
            } else if (line.startsWith("#EXTGRP:")) {
                group = line.substring(8).trim();
            } else if (!line.isEmpty() && !line.startsWith("#")) {
                try {
                    URI uri = baseUrl == null ? URI.create(line) : URI.create(baseUrl).resolve(line);
                    if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
                        channels.add(new Channel(name.isEmpty() ? "Kanał " + (channels.size() + 1) : name,
                                uri.toString(), group.isEmpty() ? "Bez kategorii" : group, epg).withLogo(logo));
                    }
                } catch (IllegalArgumentException ignored) { }
                name = ""; group = "Bez kategorii"; epg = ""; logo = "";
            }
        }
        return channels;
    }
}
