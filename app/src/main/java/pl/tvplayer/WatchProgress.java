package pl.tvplayer;
import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Hashed stream keys; no access URLs or account passwords are stored in preferences. */
final class WatchProgress {
    record Entry(long position,long duration,long updated,boolean completed) {}
    private static final Map<String,String> KEYS=new LinkedHashMap<>();
    private static String key(String url) {
        synchronized(KEYS) { String hit=KEYS.get(url); if(hit!=null) return hit; }
        try { byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8)); StringBuilder result=new StringBuilder(64); String hex="0123456789abcdef"; for(byte b:bytes) result.append(hex.charAt((b&255)>>>4)).append(hex.charAt(b&15)); String value=result.toString(); synchronized(KEYS) { KEYS.put(url,value); while(KEYS.size()>2048) KEYS.remove(KEYS.keySet().iterator().next()); } return value; }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static Entry read(Context context,String url) { android.content.SharedPreferences p=context.getSharedPreferences("watch-progress",Context.MODE_PRIVATE); String k=key(url); return new Entry(p.getLong(k+"-position",0),p.getLong(k+"-duration",0),p.getLong(k+"-updated",0),p.getBoolean(k+"-completed",false)); }
    static void save(Context context,String url,long position,long duration,boolean completed) {
        if(position<5000 && duration<=0 && !completed) return;
        if(duration<=0) duration=read(context,url).duration();
        String k=key(url); context.getSharedPreferences("watch-progress",Context.MODE_PRIVATE).edit().putLong(k+"-position",Math.max(0,position)).putLong(k+"-duration",Math.max(0,duration)).putLong(k+"-updated",System.currentTimeMillis()).putBoolean(k+"-completed",completed).apply();
    }
    /** Explicit replay keeps a zero bookmark even when the stream duration is still unknown. */
    static void restart(Context context,String url) {
        Entry entry=read(context,url); String k=key(url);
        context.getSharedPreferences("watch-progress",Context.MODE_PRIVATE).edit().putLong(k+"-position",0).putLong(k+"-duration",entry.duration()).putLong(k+"-updated",System.currentTimeMillis()).putBoolean(k+"-completed",false).apply();
    }
    static Map<String,Entry> readEntries(Context context,List<SeriesDetails.Episode> episodes) {
        Map<String,?> values=context.getSharedPreferences("watch-progress",Context.MODE_PRIVATE).getAll(); Map<String,Entry> result=new HashMap<>();
        for(SeriesDetails.Episode episode:episodes) { String url=episode.channel().url(),k=key(url); result.put(url,new Entry(number(values.get(k+"-position")),number(values.get(k+"-duration")),number(values.get(k+"-updated")),Boolean.TRUE.equals(values.get(k+"-completed")))); } return result;
    }
    /** Started, unfinished items of a catalog section, newest first by last watch time (url → last update). One preferences read. */
    static LinkedHashMap<String,Long> inProgress(Context context,List<Channel> items) {
        Map<String,?> values=context.getSharedPreferences("watch-progress",Context.MODE_PRIVATE).getAll(); List<Map.Entry<String,Long>> found=new ArrayList<>();
        if(values.isEmpty()) return new LinkedHashMap<>();
        java.security.MessageDigest digest; try { digest=java.security.MessageDigest.getInstance("SHA-256"); } catch(java.security.NoSuchAlgorithmException e) { return new LinkedHashMap<>(); }
        char[] hex="0123456789abcdef".toCharArray(); char[] out=new char[64];
        for(Channel item:items) {
            byte[] bytes=digest.digest(item.url().getBytes(StandardCharsets.UTF_8));
            for(int i=0;i<bytes.length;i++) { out[i*2]=hex[(bytes[i]&255)>>>4]; out[i*2+1]=hex[bytes[i]&15]; }
            String k=new String(out); long position=number(values.get(k+"-position")),duration=number(values.get(k+"-duration")),updated=number(values.get(k+"-updated"));
            if(updated<=0 || position<60000 || Boolean.TRUE.equals(values.get(k+"-completed")) || (duration>0 && position>duration-120000)) continue;
            found.add(new AbstractMap.SimpleEntry<>(item.url(),updated));
        }
        found.sort((a,b) -> Long.compare(b.getValue(),a.getValue()));
        LinkedHashMap<String,Long> result=new LinkedHashMap<>(); for(Map.Entry<String,Long> e:found) result.put(e.getKey(),e.getValue()); return result;
    }
    private static long number(Object value) { return value instanceof Number?((Number)value).longValue():0; }
    static SeriesDetails.Episode next(Context context,List<SeriesDetails.Episode> episodes) { return next(episodes,readEntries(context,episodes)); }
    static SeriesDetails.Episode next(List<SeriesDetails.Episode> episodes,Map<String,Entry> entries) {
        if(episodes.isEmpty()) return null; int last=-1; long updated=0;
        for(int i=0;i<episodes.size();i++) { Entry e=entries.get(episodes.get(i).channel().url()); if(e!=null && e.updated()>updated && (e.position()>=5000 || e.completed())) { last=i; updated=e.updated(); } }
        if(last<0) return episodes.get(0); Entry previous=entries.get(episodes.get(last).channel().url());
        return previous.completed() && last+1<episodes.size()?episodes.get(last+1):episodes.get(last);
    }
}
