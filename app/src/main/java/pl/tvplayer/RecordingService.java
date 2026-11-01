package pl.tvplayer;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import okhttp3.*;
import java.io.*;
import java.util.concurrent.*;
public final class RecordingService extends Service {
    public static volatile boolean active;
    private volatile boolean stopping;
    private volatile Call call;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private String title="Nagranie";
    @Override public IBinder onBind(Intent intent) { return null; }
    private Notification notification(String text,boolean running) {
        Notification.Builder builder=new Notification.Builder(this,"recordings").setSmallIcon(pl.tvplayer.R.drawable.ic_player)
            .setContentTitle(title).setContentText(text).setOngoing(running).setOnlyAlertOnce(true);
        if(running) {
            PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,RecordingService.class).setAction("STOP"),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,"Zatrzymaj",stop).build());
        }
        return builder.build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("recordings","Nagrania TV",NotificationManager.IMPORTANCE_LOW));
        if(intent==null || "STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if(active) return START_NOT_STICKY;
        title=intent.getStringExtra("name"); if(title==null) title="Nagranie TV";
        Uri target=intent.getData()!=null?intent.getData():intent.hasExtra("destination")?Uri.parse(intent.getStringExtra("destination")):null; String url=intent.getStringExtra("url");
        if(target==null || url==null) { stopSelf(); return START_NOT_STICKY; }
        active=true; stopping=false;
        startForeground(41,notification("Trwa nagrywanie…",true));
        int minutes=Math.max(1,Math.min(120,intent.getIntExtra("minutes",60)));
        handler.postDelayed(() -> { stopping=true; Call current=call; if(current!=null) current.cancel(); },minutes*60000L);
        worker.execute(() -> {
            long bytes=0; String failure=null;
            try {
                HttpUrl address=SourceClient.validate(url);
                if(!address.encodedPath().endsWith(".ts")) throw new IOException("Nagrywanie wymaga bezpośredniego strumienia MPEG-TS.");
                OkHttpClient client=new OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).build();
                call=client.newCall(new Request.Builder().url(address).header("User-Agent","TVPlayer/0.4.0").build());
                try(Response response=call.execute()) {
                    if(!response.isSuccessful() || response.body()==null) throw new IOException("Serwer odrzucił nagrywanie.");
                    String content=response.header("Content-Type","");
                    if(content.contains("text/html") || content.contains("application/json")) throw new IOException("Źródło nie zwróciło strumienia TV.");
                    try(InputStream input=response.body().byteStream(); OutputStream output=getContentResolver().openOutputStream(target,"w")) {
                        if(output==null) throw new IOException("Nie można zapisać pliku.");
                        byte[] buffer=new byte[64*1024]; int read;
                        while(!stopping && (read=input.read(buffer))!=-1) { output.write(buffer,0,read); bytes+=read; }
                        output.flush();
                    }
                }
            } catch(Exception e) { if(!stopping) failure="Nagrywanie przerwane. Sprawdź internet, miejsce na plik i limit połączeń dostawcy."; }
            final long saved=bytes; final String problem=failure;
            handler.post(() -> {
                active=false; handler.removeCallbacksAndMessages(null); stopForeground(STOP_FOREGROUND_REMOVE);
                String text=problem!=null?problem:(saved==0?"Nie zapisano danych. Sprawdź dostępność kanału.":"Zapisano "+(saved/1024/1024)+" MB w wybranym pliku.");
                manager.notify(42,notification(text,false)); stopSelf();
            });
        });
        return START_NOT_STICKY;
    }
    @Override public void onTimeout(int startId,int type) { stopping=true; if(call!=null) call.cancel(); stopSelf(); }
    @Override public void onDestroy() {
        stopping=true; if(call!=null) call.cancel(); handler.removeCallbacksAndMessages(null);
        active=false; worker.shutdown(); super.onDestroy();
    }
}
