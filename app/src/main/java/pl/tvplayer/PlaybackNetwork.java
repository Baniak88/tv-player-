package pl.tvplayer;

import android.content.Context;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

/** The same bounded HTTP transport for TV, phone, preview and VOD. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class PlaybackNetwork {
    static DefaultMediaSourceFactory sources(Context context,Channel live) {
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory()
            .setUserAgent(SourceClient.PLAYER_AGENT).setConnectTimeoutMs(10000).setReadTimeoutMs(12000)
            .setAllowCrossProtocolRedirects(true); // films and episodes are often redirected from http to an https CDN
        DefaultDataSource.Factory upstream=new DefaultDataSource.Factory(context.getApplicationContext(),http);
        if(live==null) return new DefaultMediaSourceFactory(upstream).setLoadErrorHandlingPolicy(new ProviderLoadErrorPolicy());
        GuideRepository guide=GuideRepository.get(context);
        return new DefaultMediaSourceFactory(() -> new EitDataSource(upstream.createDataSource(),live,guide)).setLoadErrorHandlingPolicy(new ProviderLoadErrorPolicy());
    }
}
