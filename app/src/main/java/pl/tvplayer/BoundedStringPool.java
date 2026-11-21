package pl.tvplayer;

import java.util.*;

/** Reuses repeated provider categories/artwork without interning an unbounded playlist. */
final class BoundedStringPool {
    private final int limit;
    private final Map<String,String> values=new LinkedHashMap<>(128,.75f,true);
    BoundedStringPool(int limit) { this.limit=Math.max(1,limit); }
    synchronized String canonical(String value) {
        if(value==null || value.isEmpty()) return "";
        String cached=values.get(value); if(cached!=null) return cached;
        values.put(value,value); if(values.size()>limit) values.remove(values.keySet().iterator().next()); return value;
    }
    synchronized int size() { return values.size(); }
}
