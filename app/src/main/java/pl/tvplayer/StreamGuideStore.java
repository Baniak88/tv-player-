package pl.tvplayer;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Only a hash of the stream URL is persisted; provider credentials never enter this database. */
final class StreamGuideStore extends SQLiteOpenHelper {
    StreamGuideStore(Context context) { super(context,"stream-epg.db",null,1); setWriteAheadLoggingEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) { db.execSQL("CREATE TABLE programs (stream TEXT, start INTEGER, end INTEGER, title TEXT, description TEXT, server_start TEXT, updated INTEGER, PRIMARY KEY(stream,start))"); }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {}
    private String key(Channel channel) {
        try {
            byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(channel.url().getBytes(StandardCharsets.UTF_8));
            StringBuilder key=new StringBuilder(); for(byte b:bytes) key.append(String.format(Locale.ROOT,"%02x",b&255)); return key.toString();
        } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    synchronized List<Guide.Program> load(Channel channel) {
        List<Guide.Program> result=new ArrayList<>(); long now=System.currentTimeMillis()/1000;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT title,description,start,end,server_start FROM programs WHERE stream=? AND end>? AND updated>? ORDER BY start LIMIT 500",new String[]{key(channel),String.valueOf(now),String.valueOf(now-21600)})) {
            while(c.moveToNext()) result.add(new Guide.Program(c.getString(0),c.getString(1),c.getLong(2),c.getLong(3),c.getString(4)));
        }
        return result;
    }
    synchronized void save(Channel channel,List<Guide.Program> programs) {
        SQLiteDatabase db=getWritableDatabase(); long now=System.currentTimeMillis()/1000;
        db.beginTransaction();
        try {
            db.delete("programs","end<? OR updated<?",new String[]{String.valueOf(now-86400),String.valueOf(now-21600)});
            try(SQLiteStatement insert=db.compileStatement("INSERT OR REPLACE INTO programs VALUES(?,?,?,?,?,?,?)")) {
                for(Guide.Program p:programs) {
                    if(p.end()<=now || p.start()>now+7*86400) continue;
                    insert.clearBindings(); insert.bindString(1,key(channel)); insert.bindLong(2,p.start()); insert.bindLong(3,p.end());
                    insert.bindString(4,p.title()); insert.bindString(5,p.description()); insert.bindString(6,p.serverStart()); insert.bindLong(7,now); insert.executeInsert();
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
