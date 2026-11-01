package pl.tvplayer;
import android.app.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class GuideDialog {
    public static void open(Activity activity,Channel channel,Executor worker,Consumer<Channel> play) {
        GuideRepository repository=GuideRepository.get(activity);
        if(repository.isObserving(channel)) { openStream(activity,channel,play); return; }
        ProgressDialog waiting=new ProgressDialog(activity); waiting.setMessage("Pobieranie programu TV…"); waiting.setCancelable(true);
        waiting.setButton(android.content.DialogInterface.BUTTON_NEUTRAL,"Ze strumienia",(d,w) -> { waiting.dismiss(); openStream(activity,channel,play); });
        waiting.show();
        repository.request(channel,true,data -> {
            if(activity.isDestroyed() || !waiting.isShowing()) return; waiting.dismiss();
            show(activity,channel,data,play);
        });
    }
    static void openStream(Activity activity,Channel channel,Consumer<Channel> play) {
        boolean playing=GuideRepository.get(activity).isObserving(channel);
        ProgressDialog waiting=new ProgressDialog(activity);
        waiting.setMessage(playing?"Oczekiwanie na EPG w oglądanym kanale (do 20 s)…":"Odczyt EPG ze strumienia (do 20 s)…");
        waiting.setCancelable(true); waiting.setButton(android.content.DialogInterface.BUTTON_NEGATIVE,"Anuluj",(d,w) -> {});
        waiting.show();
        StreamGuideProbe probe=GuideRepository.get(activity).requestStream(channel,data -> {
            if(activity.isDestroyed() || !waiting.isShowing()) return;
            waiting.dismiss(); show(activity,channel,data,play);
        });
        waiting.setOnDismissListener(d -> probe.cancel());
    }
    private static void show(Activity activity,Channel channel,GuideRepository.Data data,Consumer<Channel> play) {
        List<Guide.Program> programs=data.programs();
        if(programs.isEmpty()) {
            AlertDialog.Builder error=new AlertDialog.Builder(activity).setTitle("Program TV")
                .setMessage(data.error().isEmpty()?"Dostawca nie udostępnił programu dla tego kanału.":data.error()).setNegativeButton("Zamknij",null);
            if(!data.source().equals("Strumień DVB EIT")) error.setPositiveButton("Odczytaj strumień",(d,w) -> openStream(activity,channel,play));
            else error.setPositiveButton("Włącz kanał",(d,w) -> play.accept(channel));
            error.show(); return;
        }
        String[] labels=new String[programs.size()]; long now=System.currentTimeMillis()/1000;
        java.time.format.DateTimeFormatter date=java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(java.time.ZoneId.systemDefault());
        int current=0;
        for(int i=0;i<labels.length;i++) {
            Guide.Program p=programs.get(i); if(p.current(now)) current=i;
            labels[i]=(p.current(now)?"TERAZ · ":"")+date.format(java.time.Instant.ofEpochSecond(p.start()))+"  "+p.title()+(p.replayable(channel,now)?"  ↶":"");
        }
        AlertDialog listing=new AlertDialog.Builder(activity).setTitle(channel.name()+" · Program TV")
            .setItems(labels,(d,index) -> {
                Guide.Program p=programs.get(index);
                AlertDialog.Builder detail=new AlertDialog.Builder(activity).setTitle(p.title())
                    .setMessage(date.format(java.time.Instant.ofEpochSecond(p.start()))+" – "+date.format(java.time.Instant.ofEpochSecond(p.end()))+"\n\n"+p.description()+"\n\nŹródło: "+data.source())
                    .setNegativeButton("Zamknij",null).setNeutralButton("Na żywo",(parent,button) -> play.accept(channel));
                if(p.replayable(channel,System.currentTimeMillis()/1000)) detail.setPositiveButton("Oglądaj od początku",(parent,button) -> {
                    try { play.accept(new Channel("Archiwum · "+p.title(),Guide.replay(channel,p,System.currentTimeMillis()/1000),channel.group(),"","movie")); }
                    catch(java.io.IOException e) { info(activity,"Nie można przygotować archiwum tego kanału."); }
                });
                detail.show();
            }).setNegativeButton("Zamknij",null).create();
        listing.show(); listing.getListView().setSelection(current);
    }
    private static void info(Activity activity,String text) { new AlertDialog.Builder(activity).setTitle("Program TV").setMessage(text).setPositiveButton("Zamknij",null).show(); }
}

