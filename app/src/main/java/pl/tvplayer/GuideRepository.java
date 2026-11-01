package pl.tvplayer;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class GuideRepository {
    public record Data(List<Guide.Program> programs,String source,String error) {}
    private record Entry(Data data,long until) {}
    private static GuideRepository instance;
    static synchronized GuideRepository get(Context context) { if(instance==null) instance=new GuideRepository(context.getApplicationContext()); return instance; }
    private final SourceClient source=new SourceClient();
    private final XmltvStore xml;
    private final ExecutorService network=Executors.newFixedThreadPool(3);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Map<String,Entry> cache=new LinkedHashMap<>();
    private final Map<String,List<Consumer<Data>>> pending=new HashMap<>();
    private GuideRepository(Context context) { xml=new XmltvStore(context); }
    private String key(Channel channel,boolean full) { return channel.url()+"|"+channel.epgId()+"|"+channel.guideUrl()+"|"+full; }
    synchronized Data peek(Channel channel,boolean full) {
        Entry entry=cache.get(key(channel,full)); return entry!=null && entry.until()>System.currentTimeMillis()?entry.data():null;
    }
    void request(Channel channel,boolean full,Consumer<Data> callback) {
        String key=key(channel,full);
        synchronized(this) {
            Data hit=peek(channel,full); if(hit!=null) { ui.post(() -> callback.accept(hit)); return; }
            List<Consumer<Data>> waiting=pending.get(key); if(waiting!=null) { waiting.add(callback); return; }
            waiting=new ArrayList<>(); waiting.add(callback); pending.put(key,waiting);
        }
        network.execute(() -> {
            Data loaded;
            try { loaded=load(channel,full); } catch(Exception e) { loaded=new Data(Collections.emptyList(),"","Nie można odczytać programu EPG. Sprawdź źródło i połączenie."); }
            final Data result=loaded;
            List<Consumer<Data>> callbacks;
            synchronized(this) {
                cache.put(key,new Entry(result,System.currentTimeMillis()+(result.programs().isEmpty()?60000:300000)));
                while(cache.size()>400) cache.remove(cache.keySet().iterator().next());
                callbacks=pending.remove(key);
            }
            if(callbacks!=null) ui.post(() -> { for(Consumer<Data> ready:callbacks) ready.accept(result); });
        });
    }
    private Data load(Channel channel,boolean full) {
        List<Guide.Program> programs=Collections.emptyList(); String failure="";
        if(!channel.guideUrl().startsWith("xmltv:")) {
            try { programs=source.guide(channel,full); } catch(java.io.IOException e) { failure="API programu jest niedostępne."; }
            if(!programs.isEmpty() && programs.stream().anyMatch(p -> p.end()>System.currentTimeMillis()/1000)) return new Data(programs,"Xtream","");
        }
        try {
            okhttp3.HttpUrl url=Guide.xmltv(channel);
            if(url!=null) {
                List<Guide.Program> alternate=xml.load(channel,url);
                if(!alternate.isEmpty()) {
                    if(full && !programs.isEmpty()) {
                        Map<Long,Guide.Program> combined=new TreeMap<>(); for(Guide.Program p:alternate) combined.put(p.start(),p); for(Guide.Program p:programs) combined.put(p.start(),p);
                        return new Data(new ArrayList<>(combined.values()),"Xtream / XMLTV","");
                    }
                    return new Data(alternate,"XMLTV","");
                }
                if(!programs.isEmpty()) return new Data(programs,"Xtream","");
                return new Data(alternate,"XMLTV","Brak programu dopasowanego do tego kanału w XMLTV.");
            }
        } catch(java.io.IOException e) { failure="Nie udało się pobrać programu przez API ani XMLTV. Sprawdź połączenie lub adres EPG od dostawcy."; }
        return new Data(programs,"",failure.isEmpty()?"Brak adresu EPG. Dodaj listę z tvg-id i x-tvg-url lub konto Xtream.":failure);
    }
    static String summary(Data data,long now) {
        if(data==null) return "Wczytywanie EPG…";
        Guide.Program current=null,next=null;
        for(Guide.Program program:data.programs()) {
            if(program.current(now)) current=program;
            else if(program.start()>now && (next==null || program.start()<next.start())) next=program;
        }
        java.time.format.DateTimeFormatter clock=java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault());
        String text=current==null?"":clock.format(java.time.Instant.ofEpochSecond(current.start()))+"–"+clock.format(java.time.Instant.ofEpochSecond(current.end()))+"  "+current.title();
        if(next!=null) text+=(text.isEmpty()?"Najbliżej: ":"\nDalej: ")+clock.format(java.time.Instant.ofEpochSecond(next.start()))+"  "+next.title();
        return text.isEmpty()?"Brak aktualnego EPG · szczegóły pod EPG":text;
    }
}
