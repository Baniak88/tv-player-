package pl.tvplayer;

import okhttp3.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Explicit, bounded stream inspection from the channel list. Never scans the whole bouquet. */
final class StreamGuideProbe {
    private final OkHttpClient client=new OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(20,TimeUnit.SECONDS).build();
    private volatile boolean cancelled;
    private volatile Call call;
    void cancel() { cancelled=true; Call current=call; if(current!=null) current.cancel(); }
    boolean cancelled() { return cancelled; }
    List<Guide.Program> read(Channel channel) throws IOException {
        if(cancelled) throw new IOException("Anulowano.");
        TsEitParser parser=new TsEitParser(channel.name());
        call=client.newCall(new Request.Builder().url(SourceClient.validate(channel.url())).header("User-Agent","TVPlayer/0.8.2").build());
        try(Response response=call.execute()) {
            if(!response.isSuccessful() || response.body()==null) throw new IOException("Nie można otworzyć strumienia (HTTP "+response.code()+").");
            byte[] buffer=new byte[32768]; int total=0; long ready=0; List<Guide.Program> result=Collections.emptyList();
            try(InputStream input=response.body().byteStream()) {
                while(!cancelled && total<16*1024*1024) {
                    int count;
                    try { count=input.read(buffer); }
                    catch(IOException e) { if(!result.isEmpty()) return result; throw e; }
                    if(count<0) break;
                    if(total==0 && count>=7 && new String(buffer,0,7,java.nio.charset.StandardCharsets.US_ASCII).equals("#EXTM3U"))
                        throw new IOException("To lista HLS. Włącz kanał i otwórz EPG podczas oglądania — sprawdzę dane w segmentach obrazu.");
                    total+=count;
                    if(parser.feed(buffer,0,count)) result=parser.programs();
                    if(!result.isEmpty()) {
                        if(ready==0) ready=System.currentTimeMillis();
                        if(result.size()>=2 || System.currentTimeMillis()-ready>=2000) return result;
                    }
                }
            }
            return result;
        } finally { call=null; }
    }
}
