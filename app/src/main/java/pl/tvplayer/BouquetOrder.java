package pl.tvplayer;
import java.util.*;
import java.text.Collator;
public final class BouquetOrder {
    public static final String[] MODES={"provider","az","za","count"};
    public static final String[] LABELS={"Kolejność dostawcy","Nazwa A–Z","Nazwa Z–A","Najwięcej pozycji"};
    public static LinkedHashMap<String,Integer> counts(List<Channel> items,String mode) {
        Map<String,Integer> count=new LinkedHashMap<>(), ranks=new HashMap<>();
        for(Channel c:items) { count.put(c.group(),count.getOrDefault(c.group(),0)+1); ranks.put(c.group(),Math.min(ranks.getOrDefault(c.group(),Integer.MAX_VALUE),c.bouquetOrder())); }
        List<String> names=new ArrayList<>(count.keySet());
        // Stable sorting keeps the playlist's first-appearance order when no API rank exists.
        names.sort(Comparator.comparingInt(ranks::get));
        Collator collator=Collator.getInstance(new Locale("pl","PL")); collator.setStrength(Collator.PRIMARY);
        if(mode.equals("az")) names.sort(collator::compare);
        if(mode.equals("za")) names.sort((left,right) -> collator.compare(right,left));
        if(mode.equals("count")) names.sort((left,right) -> Integer.compare(count.get(right),count.get(left)));
        LinkedHashMap<String,Integer> result=new LinkedHashMap<>(); for(String name:names) result.put(name,count.get(name)); return result;
    }
    static List<Channel> applyRanks(List<Channel> items,Map<String,String> categories) {
        // Names are compared without case/diacritics/punctuation, so "UK| MOVIES" in the M3U matches "UK | Movies" in the panel.
        Map<String,Integer> ranks=new LinkedHashMap<>(); int rank=0;
        for(String name:categories.values()) { String key=TitleSearch.normalize(name); if(!ranks.containsKey(key)) ranks.put(key,rank); rank++; }
        List<Channel> result=new ArrayList<>(items.size());
        for(Channel c:items) result.add(c.withOrder(ranks.getOrDefault(TitleSearch.normalize(c.group()),Integer.MAX_VALUE)));
        return result;
    }
}
