package pl.tvplayer;

import android.content.Context;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

/** Android transport; the Fire Stick keeps its original PlaybackNetwork implementation. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class PlaybackNetwork {
    static DefaultMediaSourceFactory sources(Context context,Channel live) {
        DefaultDataSource.Factory upstream=new DefaultDataSource.Factory(context.getApplicationContext(),AndroidHttpSource::new);
        DataSource.Factory media = upstream;
        if (live != null) {
            GuideRepository guide = GuideRepository.get(context);
            media = () -> new EitDataSource(upstream.createDataSource(), live, guide);
        }
        return new DefaultMediaSourceFactory(media).setLoadErrorHandlingPolicy(new AndroidLoadErrorPolicy());
    }
}
