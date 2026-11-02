package pl.tvplayer;
import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Hashed stream keys; no access URLs or account passwords are stored in preferences. */
final class WatchProgress {
    record Entry(long position,long duration,long updated,boolean completed) {}
    private static String key(String url) {
        try { byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8)); StringBuilder result=new StringBuilder(); for(byte b:bytes) result.append(String.format(Locale.ROOT,"%02x",b&255)); return result.toString(); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static Entry read(Context context,String url) { android.content.SharedPreferences p=context.getSharedPreferences("watch-progress",Context.MODE_PRIVATE); String k=key(url); return new Entry(p.getLong(k+"-position",0),p.getLong(k+"-duration",0),p.getLong(k+"-updated",0),p.getBoolean(k+"-completed",false)); }
    static void save(Context context,String url,long position,long duration,boolean completed) {
        if(position<5000 && duration<=0 && !completed) return;
        String k=key(url); context.getSharedPreferences("watch-progress",Context.MODE_PRIVATE).edit().putLong(k+"-position",Math.max(0,position)).putLong(k+"-duration",Math.max(0,duration)).putLong(k+"-updated",System.currentTimeMillis()).putBoolean(k+"-completed",completed).apply();
    }
    static SeriesDetails.Episode next(Context context,List<SeriesDetails.Episode> episodes) {
        if(episodes.isEmpty()) return null; int last=-1; long updated=0;
        for(int i=0;i<episodes.size();i++) { Entry e=read(context,episodes.get(i).channel().url()); if(e.updated()>updated && (e.position()>=5000 || e.completed())) { last=i; updated=e.updated(); } }
        if(last<0) return episodes.get(0); Entry previous=read(context,episodes.get(last).channel().url());
        return previous.completed() && last+1<episodes.size()?episodes.get(last+1):episodes.get(last);
    }
}
