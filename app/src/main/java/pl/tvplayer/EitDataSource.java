package pl.tvplayer;

import android.net.Uri;
import androidx.media3.datasource.*;
import java.io.IOException;
import java.util.*;

/** Observes the bytes already read by Media3; it never opens a second playback connection. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class EitDataSource implements DataSource {
    private final DataSource upstream;
    private final Channel channel;
    private final GuideRepository repository;
    private TsEitParser parser;
    private long lastPublished;
    private boolean dirty;
    EitDataSource(DataSource upstream,Channel channel,GuideRepository repository) { this.upstream=upstream; this.channel=channel; this.repository=repository; }
    @Override public void addTransferListener(TransferListener listener) { upstream.addTransferListener(listener); }
    @Override public long open(DataSpec spec) throws IOException { parser=new TsEitParser(channel.name()); dirty=false; lastPublished=0; return upstream.open(spec); }
    @Override public int read(byte[] buffer,int offset,int length) throws IOException {
        int count=upstream.read(buffer,offset,length);
        if(count>0 && parser!=null) {
            dirty|=parser.feed(buffer,offset,count);
            long now=System.currentTimeMillis();
            if(dirty && now-lastPublished>=2000) { publish(); lastPublished=now; }
        }
        return count;
    }
    private void publish() { if(parser!=null) { List<Guide.Program> programs=parser.programs(); if(!programs.isEmpty()) repository.acceptStream(channel,programs); dirty=false; } }
    @Override public Uri getUri() { return upstream.getUri(); }
    @Override public Map<String,List<String>> getResponseHeaders() { return upstream.getResponseHeaders(); }
    @Override public void close() throws IOException { try { publish(); } finally { parser=null; upstream.close(); } }
}
