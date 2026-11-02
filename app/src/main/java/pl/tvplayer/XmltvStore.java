package pl.tvplayer;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.*;
import okhttp3.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

final class XmltvStore extends SQLiteOpenHelper {
    private final OkHttpClient client=new OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(3,TimeUnit.MINUTES).build();
    private final Map<String,Object> locks=new HashMap<>();
    private final java.util.concurrent.ExecutorService refreshWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Set<String> refreshing=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<String,Long> failures=new java.util.concurrent.ConcurrentHashMap<>();
    XmltvStore(Context context) { super(context,"epg-cache.db",null,1); setWriteAheadLoggingEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE feeds (source TEXT PRIMARY KEY, updated INTEGER)");
        db.execSQL("CREATE TABLE stations (source TEXT, id TEXT, name TEXT)");
        db.execSQL("CREATE INDEX station_names ON stations(source,name)");
        db.execSQL("CREATE TABLE programs (source TEXT, channel TEXT, start INTEGER, end INTEGER, title TEXT, description TEXT)");
        db.execSQL("CREATE INDEX schedule ON programs(source,channel COLLATE NOCASE,start)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {}
    static String normalize(String input) {
        String s=java.text.Normalizer.normalize(input==null?"":input,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);
        s=s.replaceFirst("^[|\\s]*(?:(?:pl|pol|uk|us|de|fr|it|es|cz|sk)[|\\s:_-]+)?(?:vip[|\\s:_-]+)?","");
        s=s.replaceAll("(?:[\\s|_-]+(?:raw|fhd|hd|uhd|4k|hevc|h265))+$","");
        return s.replaceAll("[^\\p{L}\\p{N}]","");
    }
    private String key(HttpUrl url) {
        try { byte[] data=MessageDigest.getInstance("SHA-256").digest(url.toString().getBytes(StandardCharsets.UTF_8)); StringBuilder result=new StringBuilder(); for(byte b:data) result.append(String.format(Locale.ROOT,"%02x",b&255)); return result.toString(); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    List<Guide.Program> cached(Channel channel,HttpUrl url) { return read(channel,key(url)); }
    List<Guide.Program> load(Channel channel,HttpUrl url) throws IOException {
        String source=key(url); Object lock;
        synchronized(locks) { lock=locks.computeIfAbsent(source,k -> new Object()); }
        List<Guide.Program> saved=read(channel,source);
        if(saved.stream().anyMatch(p -> p.end()>System.currentTimeMillis()/1000)) {
            if(!fresh(source) && System.currentTimeMillis()>=failures.getOrDefault(source,0L) && refreshing.add(source)) refreshWorker.execute(() -> {
                try { synchronized(lock) { refresh(source,url,channel.timezone()); failures.remove(source); } }
                catch(IOException | RuntimeException ignored) { failures.put(source,System.currentTimeMillis()+60000); }
                finally { refreshing.remove(source); }
            });
            return saved;
        }
        synchronized(lock) {
            if(System.currentTimeMillis()<failures.getOrDefault(source,0L)) throw new IOException("Źródło XMLTV jest chwilowo niedostępne.");
            try { refresh(source,url,channel.timezone()); failures.remove(source); }
            catch(IOException e) { failures.put(source,System.currentTimeMillis()+60000); throw e; }
        }
        return read(channel,source);
    }
    private boolean fresh(String source) {
        try(Cursor cursor=getReadableDatabase().rawQuery("SELECT updated FROM feeds WHERE source=?",new String[]{source})) { return cursor.moveToFirst() && System.currentTimeMillis()/1000-cursor.getLong(0)<3600; }
    }
    private List<Guide.Program> read(Channel channel,String source) {
        SQLiteDatabase db=getReadableDatabase(); String id=channel.epgId();
        if(id.isEmpty() || !hasPrograms(db,source,id)) {
            if(!id.isEmpty()) id=station(db,source,id);
            if(id.isEmpty() || !hasPrograms(db,source,id)) id=station(db,source,channel.name());
        }
        if(id.isEmpty()) return new ArrayList<>();
        List<Guide.Program> result=new ArrayList<>();
        DateTimeFormatter server=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(Guide.zone(channel.timezone()));
        try(Cursor cursor=db.rawQuery("SELECT title,description,start,end FROM programs WHERE source=? AND channel=? COLLATE NOCASE AND end>start ORDER BY start LIMIT 500",new String[]{source,id})) {
            while(cursor.moveToNext()) result.add(new Guide.Program(cursor.getString(0),cursor.getString(1),cursor.getLong(2),cursor.getLong(3),server.format(Instant.ofEpochSecond(cursor.getLong(2)))));
        }
        return result;
    }
    private boolean hasPrograms(SQLiteDatabase db,String source,String id) {
        try(Cursor cursor=db.rawQuery("SELECT 1 FROM programs WHERE source=? AND channel=? COLLATE NOCASE LIMIT 1",new String[]{source,id})) { return cursor.moveToFirst(); }
    }
    private String station(SQLiteDatabase db,String source,String name) {
        try(Cursor cursor=db.rawQuery("SELECT DISTINCT id FROM stations WHERE source=? AND name=? LIMIT 2",new String[]{source,normalize(name)})) {
            if(!cursor.moveToFirst()) return ""; String id=cursor.getString(0); return cursor.moveToNext()?"":id;
        }
    }
    private void refresh(String source,HttpUrl url,String timezone) throws IOException {
        SQLiteDatabase db=getWritableDatabase(); long now=System.currentTimeMillis()/1000;
        try(Cursor cursor=db.rawQuery("SELECT updated FROM feeds WHERE source=?",new String[]{source})) { if(cursor.moveToFirst() && now-cursor.getLong(0)<3600) return; }
        try(Response response=client.newCall(new Request.Builder().url(url).header("User-Agent","TVPlayer/0.8.1").build()).execute()) {
            if(!response.isSuccessful() || response.body()==null) throw new IOException("Nie udało się pobrać XMLTV (HTTP "+response.code()+").");
            try(PushbackInputStream input=new PushbackInputStream(new BufferedInputStream(response.body().byteStream(),65536),2)) {
                int a=input.read(),b=input.read(); if(b>=0) input.unread(b); if(a>=0) input.unread(a);
                InputStream xml=a==31 && b==139?new GZIPInputStream(input,65536):input;
                db.beginTransaction();
                try {
                    db.delete("programs","source=?",new String[]{source}); db.delete("stations","source=?",new String[]{source});
                    SQLiteStatement station=db.compileStatement("INSERT INTO stations(source,id,name) VALUES(?,?,?)");
                    SQLiteStatement program=db.compileStatement("INSERT INTO programs(source,channel,start,end,title,description) VALUES(?,?,?,?,?,?)");
                    Set<String> identifiers=new HashSet<>();
                    try {
                        XmltvParser.parse(xml,timezone,new XmltvParser.Sink() {
                            public void channel(String id,String name) {
                                station.clearBindings(); station.bindString(1,source); station.bindString(2,id); station.bindString(3,normalize(name)); station.executeInsert();
                            }
                            public void program(String id,String title,String description,long start,long end) {
                                if(start<now-86400 || start>now+3*86400) return;
                                if(identifiers.add(id)) channel(id,id.replaceFirst("(?i)\\.[a-z]{2,3}$",""));
                                program.clearBindings(); program.bindString(1,source); program.bindString(2,id); program.bindLong(3,start); program.bindLong(4,end);
                                program.bindString(5,title); program.bindString(6,description); program.executeInsert();
                            }
                        });
                    } finally { station.close(); program.close(); }
                    db.execSQL("UPDATE programs SET end=(SELECT MIN(p.start) FROM programs p WHERE p.source=programs.source AND p.channel=programs.channel AND p.start>programs.start) WHERE source=? AND end<=start",new Object[]{source});
                    for(String table:new String[]{"programs","stations"}) db.execSQL("DELETE FROM "+table+" WHERE source IN (SELECT source FROM feeds WHERE updated<? AND source<>?)",new Object[]{now-86400,source});
                    db.execSQL("DELETE FROM feeds WHERE updated<? AND source<>?",new Object[]{now-86400,source});
                    db.execSQL("INSERT OR REPLACE INTO feeds(source,updated) VALUES(?,?)",new Object[]{source,now}); db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
            }
        } catch(RuntimeException e) { throw new IOException("Nie można zapisać programu XMLTV.",e); }
    }
}

