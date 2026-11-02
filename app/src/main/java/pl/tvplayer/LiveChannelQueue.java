package pl.tvplayer;

import java.util.*;

/** Small process-local navigation sessions; channel catalogs are never placed in Intent extras. */
final class LiveChannelQueue {
    private static final Map<String,List<Channel>> SESSIONS=new LinkedHashMap<>();
    private final List<Channel> channels;
    private int selected;
    private LiveChannelQueue(List<Channel> items,String current) { channels=normalize(items); selected=indexOf(current); }
    private static List<Channel> normalize(List<Channel> items) {
        Map<String,Channel> unique=new LinkedHashMap<>();
        for(Channel item:items) { String name=item.name().trim(); if(!item.kind().equals("tv") || item.url().isEmpty() || (name.startsWith("###") && name.endsWith("###"))) continue; unique.putIfAbsent(item.url(),item); }
        return Collections.unmodifiableList(new ArrayList<>(unique.values()));
    }
    static String remember(List<Channel> items,String current) {
        LiveChannelQueue queue=new LiveChannelQueue(items,current); if(queue.selected<0) return null;
        synchronized(SESSIONS) { String token=UUID.randomUUID().toString(); SESSIONS.put(token,queue.channels); while(SESSIONS.size()>3) SESSIONS.remove(SESSIONS.keySet().iterator().next()); return token; }
    }
    static LiveChannelQueue restore(String token,String current) { synchronized(SESSIONS) { List<Channel> saved=SESSIONS.get(token); return saved==null?null:new LiveChannelQueue(saved,current); } }
    static LiveChannelQueue rebuild(List<Channel> items,Set<String> favorites,String group,String query,boolean onlyFavorites,String current) {
        List<Channel> scope=new ArrayList<>(); String search=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        for(Channel item:items) { if(group!=null && !group.isEmpty() && !group.equals(item.group())) continue; if(onlyFavorites && !favorites.contains(item.url())) continue; if(!search.isEmpty() && !item.name().toLowerCase(Locale.ROOT).contains(search)) continue; scope.add(item); }
        return new LiveChannelQueue(scope,current);
    }
    private int indexOf(String url) { for(int i=0;i<channels.size();i++) if(channels.get(i).url().equals(url)) return i; return -1; }
    boolean canNavigate() { return selected>=0 && channels.size()>1; }
    Channel move(int offset) { if(!canNavigate()) return null; selected=Math.floorMod(selected+offset,channels.size()); return channels.get(selected); }
}
