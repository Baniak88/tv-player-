package pl.tvplayer;
import android.app.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
public final class GuideDialog {
    public static void open(Activity activity,Channel channel,Executor worker,Consumer<Channel> play) {
        ProgressDialog waiting=new ProgressDialog(activity); waiting.setMessage("Pobieranie programu TV…"); waiting.setCancelable(true); waiting.show();
        worker.execute(() -> {
            try {
                List<Guide.Program> programs=new SourceClient().guide(channel);
                activity.runOnUiThread(() -> {
                    if(activity.isDestroyed() || !waiting.isShowing()) return; waiting.dismiss();
                    if(programs.isEmpty()) { info(activity,"Dostawca nie udostępnił programu TV dla tego kanału."); return; }
                    String[] labels=new String[programs.size()]; long now=System.currentTimeMillis()/1000;
                    java.time.format.DateTimeFormatter date=java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(java.time.ZoneId.systemDefault());
                    for(int i=0;i<labels.length;i++) {
                        Guide.Program p=programs.get(i);
                        labels[i]=(p.current(now)?"TERAZ · ":"")+date.format(java.time.Instant.ofEpochSecond(p.start()))+"  "+p.title()+(p.replayable(channel,now)?"  ↶":"");
                    }
                    new AlertDialog.Builder(activity).setTitle(channel.name()+" · Program TV").setItems(labels,(d,index) -> {
                        Guide.Program p=programs.get(index);
                        AlertDialog.Builder detail=new AlertDialog.Builder(activity).setTitle(p.title())
                            .setMessage(date.format(java.time.Instant.ofEpochSecond(p.start()))+" – "+date.format(java.time.Instant.ofEpochSecond(p.end()))+"\n\n"+p.description())
                            .setNegativeButton("Zamknij",null).setNeutralButton("Na żywo",(dialog,button) -> play.accept(channel));
                        if(p.replayable(channel,System.currentTimeMillis()/1000)) detail.setPositiveButton("Oglądaj od początku",(dialog,button) -> {
                            try { play.accept(new Channel("Archiwum · "+p.title(),Guide.replay(channel,p,System.currentTimeMillis()/1000),channel.group(),"","movie")); }
                            catch(java.io.IOException e) { info(activity,e.getMessage()); }
                        });
                        detail.show();
                    }).setNegativeButton("Zamknij",null).show();
                });
            } catch(Exception e) { activity.runOnUiThread(() -> {
                if(activity.isDestroyed() || !waiting.isShowing()) return; waiting.dismiss();
                info(activity,"Nie udało się pobrać EPG. Sprawdź połączenie i czy dostawca obsługuje program TV dla tego kanału.");
            }); }
        });
    }
    private static void info(Activity a,String text) { new AlertDialog.Builder(a).setTitle("Program TV").setMessage(text).setPositiveButton("OK",null).show(); }
}
