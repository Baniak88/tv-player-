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
    private final Context context;
    private final XmltvStore xml;
    private final StreamGuideStore streamStore;
    private final ExecutorService streamWorker=Executors.newSingleThreadExecutor();
    private final ExecutorService streamReaders=Executors.newFixedThreadPool(2);
    private final Map<String,Entry> streams=new LinkedHashMap<>();
    private final Map<String,Integer> observing=new HashMap<>();
    private final ExecutorService network=Executors.newFixedThreadPool(3);
    private final ExecutorService interactive=Executors.newFixedThreadPool(2);
    private final MetadataCache disk;
    private record Schedule(List<Guide.Program> programs,String source,long saved) {}
    private record Saved(Data data,long saved,boolean incomplete) {}
    private final Set<String> refreshing=ConcurrentHashMap.newKeySet();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Map<String,Entry> cache=new LinkedHashMap<>();
    private final Map<String,List<Consumer<Data>>> pending=new HashMap<>();
    private GuideRepository(Context context) { this.context=context.getApplicationContext(); xml=new XmltvStore(context); streamStore=new StreamGuideStore(context); disk=new MetadataCache(context,"epg-v1",200); }
    private String key(Channel channel,boolean full) { return channel.url()+"|"+channel.epgId()+"|"+channel.guideUrl()+"|"+full; }
    synchronized Data peek(Channel channel,boolean full) {
        Data stream=streamData(channel); if(stream!=null) return stream;
        Entry entry=cache.get(key(channel,full));
        if(!full) {
            Entry complete=cache.get(key(channel,true));
            if(complete!=null && complete.until()>System.currentTimeMillis() && usable(complete.data().programs())) entry=complete;
        }
        return entry!=null && entry.until()>System.currentTimeMillis() && (entry.data().programs().isEmpty() || usable(entry.data().programs()))?entry.data():null;
    }
    private synchronized Data streamData(Channel channel) {
        Entry entry=streams.get(channel.url()); long now=System.currentTimeMillis()/1000;
        return entry!=null && entry.until()>System.currentTimeMillis() && entry.data().programs().stream().anyMatch(p -> p.end()>now)?entry.data():null;
    }
    synchronized void streamStarted(Channel channel) { observing.put(channel.url(),observing.getOrDefault(channel.url(),0)+1); }
    synchronized void streamStopped(Channel channel) { int count=observing.getOrDefault(channel.url(),0); if(count<=1) observing.remove(channel.url()); else observing.put(channel.url(),count-1); }
    synchronized boolean isObserving(Channel channel) { return observing.containsKey(channel.url()); }
    void acceptStream(Channel channel,List<Guide.Program> programs) {
        long now=System.currentTimeMillis()/1000;
        java.time.format.DateTimeFormatter server=java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(Guide.zone(channel.timezone()));
        List<Guide.Program> valid=new ArrayList<>();
        for(Guide.Program p:programs) if(p.end()>now && p.start()<now+7*86400) valid.add(new Guide.Program(p.title(),p.description(),p.start(),p.end(),server.format(java.time.Instant.ofEpochSecond(p.start()))));
        if(valid.isEmpty()) return;
        Data data=new Data(Collections.unmodifiableList(valid),"Strumień DVB EIT","");
        synchronized(this) {
            Entry previous=streams.get(channel.url()); if(previous!=null && previous.data().equals(data)) return;
            streams.put(channel.url(),new Entry(data,System.currentTimeMillis()+21600000));
            while(streams.size()>400) streams.remove(streams.keySet().iterator().next());
        }
        streamWorker.execute(() -> { try { streamStore.save(channel,valid); } catch(RuntimeException ignored) { /* Playback must continue if disk storage is unavailable. */ } });
    }
    StreamGuideProbe requestStream(Channel channel,Consumer<Data> callback) {
        return requestStream(channel,callback,true);
    }
    StreamGuideProbe requestObservedStream(Channel channel,Consumer<Data> callback) { return requestStream(channel,callback,false); }
    private StreamGuideProbe requestStream(Channel channel,Consumer<Data> callback,boolean allowConnection) {
        StreamGuideProbe probe=new StreamGuideProbe();
        streamReaders.execute(() -> {
            Data result=streamData(channel);
            try {
                if(result==null) { List<Guide.Program> saved=streamStore.load(channel); if(!saved.isEmpty()) { acceptStream(channel,saved); result=streamData(channel); } }
                if(result==null && (!allowConnection || isObserving(channel))) {
                    long deadline=System.currentTimeMillis()+20000;
                    while(!probe.cancelled() && isObserving(channel) && System.currentTimeMillis()<deadline && (result=streamData(channel))==null) Thread.sleep(200);
                } else if(result==null && !probe.cancelled()) {
                    List<Guide.Program> read=probe.read(channel); acceptStream(channel,read); result=streamData(channel);
                }
                if(result==null) result=new Data(Collections.emptyList(),"Strumień DVB EIT","Nie znaleziono programu DVB EIT w odczytanym fragmencie strumienia. Dostawca może usuwać te dane lub przesyłać je inną metodą. Nie jest to dowód, że EPG nie istnieje.");
            } catch(java.io.IOException e) { result=new Data(Collections.emptyList(),"Strumień DVB EIT",e.getMessage()!=null && (e.getMessage().startsWith("To lista HLS") || e.getMessage().startsWith("Nie można otworzyć"))?e.getMessage():"Odczyt strumienia zakończył się bez programu DVB EIT (limit 20 s). Włącz kanał i spróbuj EPG podczas oglądania."); }
            catch(InterruptedException e) { Thread.currentThread().interrupt(); return; }
            catch(RuntimeException e) { result=new Data(Collections.emptyList(),"Strumień DVB EIT","Nie można odczytać programu ze strumienia."); }
            final Data loaded=result;
            if(!probe.cancelled()) ui.post(() -> { if(!probe.cancelled()) callback.accept(loaded); });
        });
        return probe;
    }
    void request(Channel channel,boolean full,Consumer<Data> callback) {
        String key=key(channel,full);
        synchronized(this) {
            Data hit=peek(channel,full); if(hit!=null) { ui.post(() -> callback.accept(hit)); return; }
            List<Consumer<Data>> waiting=pending.get(key); if(waiting!=null) { waiting.add(callback); return; }
            waiting=new ArrayList<>(); waiting.add(callback); pending.put(key,waiting);
        }
        (full?interactive:network).execute(() -> {
            Saved saved=readSaved(channel,full);
            if(saved!=null) {
                complete(key,channel,saved.data());
                if(saved.incomplete() || System.currentTimeMillis()-saved.saved()>=300000) refresh(channel,full);
                return;
            }
            Data loaded;
            try { loaded=load(channel,full); } catch(Exception e) { loaded=new Data(Collections.emptyList(),"","Nie można odczytać programu EPG. Sprawdź źródło i połączenie."); }
            complete(key,channel,loaded);
        });
    }
    private Saved readSaved(Channel channel,boolean full) {
        try {
            MetadataCache.Hit<Schedule> hit=disk.read("epg:"+key(channel,full),Schedule.class); boolean incomplete=false;
            if(hit==null || !usable(hit.value().programs())) { hit=disk.read("epg:"+key(channel,!full),Schedule.class); incomplete=full; }
            if(hit!=null && usable(hit.value().programs())) return new Saved(new Data(hit.value().programs(),hit.value().source(),""),hit.value().saved(),incomplete);
        } catch(RuntimeException ignored) {}
        return null;
    }
    private void complete(String key,Channel channel,Data result) {
        List<Consumer<Data>> callbacks;
        synchronized(this) {
            cache.put(key,new Entry(result,System.currentTimeMillis()+(result.programs().isEmpty()?60000:300000)));
            while(cache.size()>400) cache.remove(cache.keySet().iterator().next());
            callbacks=pending.remove(key);
        }
        if(callbacks!=null) ui.post(() -> { Data latest=streamData(channel); for(Consumer<Data> ready:callbacks) ready.accept(latest==null?result:latest); });
    }
    private void refresh(Channel channel,boolean full) {
        String key=key(channel,full); if(!refreshing.add(key)) return;
        network.execute(() -> {
            try {
                Data fresh=load(channel,full);
                if(usable(fresh.programs())) synchronized(this) { cache.put(key,new Entry(fresh,System.currentTimeMillis()+300000)); while(cache.size()>400) cache.remove(cache.keySet().iterator().next()); }
            } catch(Exception ignored) { /* Valid cached programmes remain available while the provider is offline. */ }
            finally { refreshing.remove(key); }
        });
    }
    void pauseBulkDownloads() { xml.cancelDownloads(); invalidateFailures(); }
    synchronized void invalidateFailures() { cache.entrySet().removeIf(entry -> entry.getValue().data().programs().isEmpty()); }
    private Data load(Channel channel,boolean full) {
        Data stream=streamData(channel); if(stream!=null) return stream;
        try { List<Guide.Program> saved=streamStore.load(channel); if(!saved.isEmpty()) { acceptStream(channel,saved); return streamData(channel); } } catch(RuntimeException ignored) {}
        String diskKey="epg:"+key(channel,full); Schedule saved=null;
        try { MetadataCache.Hit<Schedule> hit=disk.read(diskKey,Schedule.class); if(hit!=null && usable(hit.value().programs())) saved=hit.value(); }
        catch(RuntimeException ignored) {}
        List<Guide.Program> programs=Collections.emptyList(); String failure="";
        if(!channel.guideUrl().startsWith("xmltv:")) {
            try { programs=source.guide(channel,full); } catch(java.io.IOException e) { failure="API Xtream: "+safeFailure(e); }
            if(usable(programs)) return persist(diskKey,programs,"Xtream");
        }
        stream=streamData(channel); if(stream!=null) return stream;
        if(!full && PlaybackSettings.weakInternet(context)) {
            try { okhttp3.HttpUrl known=Guide.xmltv(channel); if(known!=null) { List<Guide.Program> stored=xml.cached(channel,known); if(usable(stored)) return persist(diskKey,stored,"XMLTV"); } }
            catch(java.io.IOException | IllegalArgumentException ignored) {}
            return new Data(Collections.emptyList(),"Oszczędzanie łącza","Dotknij EPG, aby pobrać pełny program TV.");
        }
        LinkedHashSet<okhttp3.HttpUrl> feeds=new LinkedHashSet<>();
        if(!channel.guideUrl().startsWith("xmltv:")) {
            try { feeds.addAll(source.discoverGuides(channel)); } catch(java.io.IOException | IllegalArgumentException ignored) {}
        }
        try { okhttp3.HttpUrl url=Guide.xmltv(channel); if(url!=null) feeds.add(url); } catch(java.io.IOException | IllegalArgumentException ignored) {}
        for(okhttp3.HttpUrl url:feeds) {
            try {
                List<Guide.Program> alternate=xml.load(channel,url);
                if(usable(alternate)) {
                    if(full && !programs.isEmpty()) {
                        Map<Long,Guide.Program> combined=new TreeMap<>(); for(Guide.Program p:alternate) combined.put(p.start(),p); for(Guide.Program p:programs) combined.put(p.start(),p);
                        return persist(diskKey,new ArrayList<>(combined.values()),"Xtream / XMLTV");
                    }
                    return persist(diskKey,alternate,"XMLTV");
                }
                failure+=(failure.isEmpty()?"":"\n")+"XMLTV: brak aktualnego programu dopasowanego do kanału.";
            } catch(java.io.IOException e) { failure+=(failure.isEmpty()?"":"\n")+"XMLTV: "+safeFailure(e); }
        }
        if(saved!=null) return new Data(saved.programs(),saved.source()+" · pamięć","");
        return new Data(programs,"",failure.isEmpty()?"Nie znaleziono aktualnego programu w API ani w EPG dostawcy.":failure);
    }
    private static boolean usable(List<Guide.Program> programs) { return programs!=null && programs.stream().anyMatch(p -> p.end()>System.currentTimeMillis()/1000); }
    private Data persist(String key,List<Guide.Program> programs,String sourceName) {
        try { disk.write(key,new Schedule(programs,sourceName,System.currentTimeMillis())); } catch(RuntimeException ignored) {}
        return new Data(programs,sourceName,"");
    }
    private static String safeFailure(java.io.IOException error) {
        // Do not expose account URLs, query strings or credentials in dialogs.
        String text=error.getMessage(); java.util.regex.Matcher code=java.util.regex.Pattern.compile("HTTP ([0-9]{3})").matcher(text==null?"":text);
        return code.find()?"serwer zwrócił HTTP "+code.group(1)+".":error instanceof java.io.InterruptedIOException?"przekroczono czas odpowiedzi.":"brak poprawnej odpowiedzi serwera.";
    }
    static String summary(Data data,long now) {
        if(data==null) return "Wczytywanie EPG…";
        if(data.source().equals("Oszczędzanie łącza") && data.programs().isEmpty()) return "Dotknij EPG, aby pobrać program";
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


