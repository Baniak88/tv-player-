package pl.tvplayer;
import android.app.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class GuideDialog {
    public static void open(Activity activity,Channel channel,Executor worker,Consumer<Channel> play) {
        GuideRepository repository=GuideRepository.get(activity);
        ProgressDialog waiting=new ProgressDialog(activity); waiting.setMessage("Pobieranie programu TV…"); waiting.setCancelable(true);
        waiting.setButton(android.content.DialogInterface.BUTTON_NEUTRAL,"Ze strumienia",(d,w) -> { waiting.dismiss(); openStream(activity,channel,play); });
        waiting.show();
        final GuideRepository.Data[] results=new GuideRepository.Data[2];
        final boolean[] done={false,!repository.isObserving(channel)};
        final StreamGuideProbe[] probe={null};
        waiting.setOnDismissListener(d -> { if(probe[0]!=null) probe[0].cancel(); });
        java.util.function.BiConsumer<Integer,GuideRepository.Data> accept=(index,data) -> {
            if(activity.isDestroyed() || activity.isFinishing() || !waiting.isShowing()) return;
            results[index]=data; done[index]=true;
            if(data.programs().stream().anyMatch(p -> p.end()>System.currentTimeMillis()/1000)) { waiting.dismiss(); show(activity,channel,data,play); return; }
            if(done[0] && done[1]) {
                waiting.dismiss(); GuideRepository.Data network=results[0];
                if(network==null) network=data;
                String error=network.error();
                if(results[1]!=null && !results[1].error().isEmpty()) error+=(error.isEmpty()?"":"\n\n")+results[1].error();
                show(activity,channel,new GuideRepository.Data(network.programs(),network.source(),error),play);
            } else if(index==0) waiting.setMessage("Sprawdzanie EPG w oglądanym kanale…");
        };
        // Observe the existing playback connection in parallel; never open another stream here.
        if(!done[1]) probe[0]=repository.requestObservedStream(channel,data -> accept.accept(1,data));
        repository.request(channel,true,data -> accept.accept(0,data));
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


