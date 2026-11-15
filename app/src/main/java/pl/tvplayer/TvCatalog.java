package pl.tvplayer;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Presentation of flat M3U episodes; synthetic folder addresses never leave the catalogue. */
final class TvCatalog {
    private static final Pattern EPISODE=Pattern.compile("(?i)\\bS\\s*(\\d{1,3})\\s*[ ._-]*E\\s*(\\d{1,4})\\b");
    record Catalog(List<Channel> items,Map<String,List<Channel>> folders) {}
    private record Episode(Channel channel,int season,int number) {}
    static Catalog series(List<Channel> source) {
        List<Channel> items=new ArrayList<>(); Map<String,List<Episode>> grouped=new LinkedHashMap<>(); Map<String,List<Channel>> folders=new LinkedHashMap<>(); Map<String,String> identities=new HashMap<>();
        for(Channel channel:source) {
            if(!channel.kind().equals("episode")) { items.add(channel); continue; }
            Matcher match=EPISODE.matcher(channel.name());
            if(!match.find()) { items.add(channel); continue; }
            String title=channel.name().substring(0,match.start()).replaceFirst("[\\s|:·_-]+$","").trim();
            if(title.isEmpty()) { items.add(channel); continue; }
            String identity=channel.group()+"\n"+title.toLowerCase(Locale.ROOT);
            String key=identities.get(identity);
            if(key==null) { key="local-series:"+UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)); identities.put(identity,key); }
            List<Episode> episodes=grouped.get(key);
            if(episodes==null) {
                episodes=new ArrayList<>(); grouped.put(key,episodes);
                items.add(new Channel(title,key,channel.group(),"","series").withLogo(channel.logo()).withOrder(channel.bouquetOrder()));
            }
            episodes.add(new Episode(channel,Integer.parseInt(match.group(1)),Integer.parseInt(match.group(2))));
        }
        for(Map.Entry<String,List<Episode>> group:grouped.entrySet()) {
            group.getValue().sort(Comparator.comparingInt(Episode::season).thenComparingInt(Episode::number));
            List<Channel> episodes=new ArrayList<>(group.getValue().size()); for(Episode episode:group.getValue()) episodes.add(episode.channel()); folders.put(group.getKey(),episodes);
        }
        return new Catalog(items,folders);
    }
    static int season(Channel channel) { Matcher match=EPISODE.matcher(channel.name()); return match.find()?Integer.parseInt(match.group(1)):0; }
    static List<Channel> seasons(List<Channel> episodes) {
        List<Channel> result=new ArrayList<>(episodes.size());
        for(Channel c:episodes) result.add(new Channel(c.name(),c.url(),"Sezon "+season(c),c.epgId(),c.kind(),c.guideUrl(),c.archiveDays(),c.timezone(),season(c),c.logo(),c.added()));
        return result;
    }
}
