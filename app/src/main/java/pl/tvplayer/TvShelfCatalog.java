package pl.tvplayer;

import java.util.*;

/** Bounded shelves: large catalogues keep only a small preview per provider category. */
final class TvShelfCatalog {
    static final int PREVIEW=24;
    record Shelf(String key,String title,List<Channel> channels,int total) { boolean more() { return total>channels.size(); } }
    static List<Shelf> build(List<Channel> channels,String order) {
        Map<String,Integer> counts=BouquetOrder.counts(channels,order);
        Map<String,List<Channel>> previews=new HashMap<>();
        for(Channel channel:channels) { List<Channel> row=previews.computeIfAbsent(channel.group(),key -> new ArrayList<>()); if(row.size()<PREVIEW) row.add(channel); }
        List<Shelf> rows=new ArrayList<>();
        for(Map.Entry<String,Integer> entry:counts.entrySet()) rows.add(new Shelf(entry.getKey(),entry.getKey(),previews.get(entry.getKey()),entry.getValue()));
        return rows;
    }
}
