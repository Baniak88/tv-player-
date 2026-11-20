package pl.tvplayer;

import android.net.Uri;
import android.os.SystemClock;
import androidx.media3.common.C;
import androidx.media3.datasource.*;
import java.io.*;
import java.util.*;

/** Bounded compatibility for redirected media, rejected agents and broken small byte ranges. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class AndroidHttpSource implements DataSource {
    private static final Map<String,Boolean> PLATFORM_HOSTS=Collections.synchronizedMap(new LinkedHashMap<String,Boolean>(16,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Boolean> eldest) { return size()>64; }
    });
    private static final long MAX_RANGE_SKIP=2L*1024*1024;
    private final List<TransferListener> listeners=new ArrayList<>();
    private HttpDataSource current;
    private long remaining=C.LENGTH_UNSET;
    private HttpDataSource create(boolean platform) {
        DefaultHttpDataSource.Factory factory=new DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(10000).setReadTimeoutMs(12000);
        factory.setUserAgent(platform?System.getProperty("http.agent"):SourceClient.PLAYER_AGENT);
        HttpDataSource result=factory.createDataSource(); for(TransferListener listener:listeners) result.addTransferListener(listener); return result;
    }
    @Override public long open(DataSpec spec) throws IOException {
        String origin=spec.uri.getScheme()+"://"+spec.uri.getHost()+":"+spec.uri.getPort(); boolean platform=PLATFORM_HOSTS.containsKey(origin),rangeFallback=false;
        HttpDataSource.InvalidResponseCodeException rangeFailure=null;
        for(int attempt=0;attempt<3;attempt++) {
            current=create(platform);
            DataSpec request=rangeFallback?spec.buildUpon().setPosition(0).setLength(C.LENGTH_UNSET).build():spec;
            try {
                long size=current.open(request);
                if(rangeFallback) {
                    long left=spec.position,deadline=SystemClock.elapsedRealtime()+15000; byte[] bytes=new byte[8192];
                    while(left>0) {
                        if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Pobieranie anulowane.");
                        if(SystemClock.elapsedRealtime()>deadline) throw new java.net.SocketTimeoutException("Serwer zbyt wolno obsługuje przewijanie.");
                        int count=current.read(bytes,0,(int)Math.min(bytes.length,left)); if(count==C.RESULT_END_OF_INPUT) { if(rangeFailure!=null) throw rangeFailure; throw new EOFException("Zapisana pozycja jest poza dostępnym filmem."); } left-=count;
                    }
                    size=size==C.LENGTH_UNSET?C.LENGTH_UNSET:Math.max(0,size-spec.position);
                }
                remaining=spec.length==C.LENGTH_UNSET?size:size==C.LENGTH_UNSET?spec.length:Math.min(size,spec.length);
                if(platform) PLATFORM_HOSTS.put(origin,true); return remaining;
            } catch(HttpDataSource.InvalidResponseCodeException error) {
                try { close(); } catch(IOException closing) { error.addSuppressed(closing); }
                // A second, standard Android request can serve panels that reject the VLC agent.
                // Authentication errors never change identity or loop indefinitely.
                if(!platform && (error.responseCode==403 || error.responseCode==406)) { platform=true; continue; }
                if(error.responseCode==416 && !rangeFallback && spec.httpMethod==DataSpec.HTTP_METHOD_GET && spec.position>0 && spec.position<=MAX_RANGE_SKIP && (spec.flags&DataSpec.FLAG_ALLOW_GZIP)==0) { rangeFallback=true; rangeFailure=error; continue; }
                throw error;
            } catch(IOException | RuntimeException error) { try { close(); } catch(IOException closing) { error.addSuppressed(closing); } throw error; }
        }
        throw new IOException("Serwer nie obsłużył żądania odtwarzania.");
    }
    @Override public int read(byte[] buffer,int offset,int length) throws IOException {
        if(length==0) return 0; if(remaining==0) return C.RESULT_END_OF_INPUT; if(current==null) throw new IOException("Połączenie nie jest otwarte.");
        int count=current.read(buffer,offset,remaining==C.LENGTH_UNSET?length:(int)Math.min(length,remaining)); if(count>0 && remaining!=C.LENGTH_UNSET) remaining-=count; return count;
    }
    @Override public Uri getUri() { return current==null?null:current.getUri(); }
    @Override public Map<String,List<String>> getResponseHeaders() { return current==null?Collections.emptyMap():current.getResponseHeaders(); }
    @Override public void addTransferListener(TransferListener listener) { listeners.add(listener); if(current!=null) current.addTransferListener(listener); }
    @Override public void close() throws IOException { HttpDataSource old=current; current=null; remaining=C.LENGTH_UNSET; if(old!=null) old.close(); }
}
