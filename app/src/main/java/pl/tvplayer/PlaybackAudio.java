package pl.tvplayer;

import android.content.Context;
import androidx.media3.common.*;
import androidx.media3.common.audio.*;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.audio.*;

/** A PCM path for televisions whose HDMI device advertises unusable encoded audio. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class PlaybackAudio {
    static boolean pcm(Context context) { return context.getSharedPreferences("playback-audio",0).getBoolean("pcm",true); }
    static void pcm(Context context,boolean enabled) { context.getSharedPreferences("playback-audio",0).edit().putBoolean("pcm",enabled).apply(); }
    static DefaultRenderersFactory renderers(Context context) {
        boolean pcm=pcm(context);
        return new DefaultRenderersFactory(context) {
            @Override protected AudioSink buildAudioSink(Context ctx,boolean floatOutput,boolean playbackParams) {
                // Without Context, capabilities stay explicitly PCM instead of following an HDMI broadcast.
                return pcm?new DefaultAudioSink.Builder().setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES).setAudioProcessors(new AudioProcessor[]{mixer()}).build():super.buildAudioSink(ctx,floatOutput,playbackParams);
            }
        }.setEnableDecoderFallback(true).setExtensionRendererMode(pcm?DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER:DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
    }
    static ChannelMixingAudioProcessor mixer() {
        ChannelMixingAudioProcessor mixer=new ChannelMixingAudioProcessor();
        mixer.putChannelMixingMatrix(ChannelMixingMatrix.create(1,2));
        mixer.putChannelMixingMatrix(ChannelMixingMatrix.create(2,2));
        for(int channels=3;channels<=8;channels++) {
            float[] weights=new float[channels*2]; weights[0]=1; weights[3]=1;
            int surround=2;
            if(channels!=4) { weights[4]=weights[5]=.7071f; surround=3; }
            if(channels>=6) { weights[6]=weights[7]=.25f; surround=4; }
            if(channels==7) { weights[8]=weights[9]=.5f; surround=5; }
            for(int i=surround;i<channels;i++) weights[i*2+(i-surround)%2]=.7071f;
            float left=0,right=0; for(int i=0;i<channels;i++) { left+=weights[i*2]; right+=weights[i*2+1]; }
            mixer.putChannelMixingMatrix(new ChannelMixingMatrix(channels,2,weights).scaleBy(1/Math.max(left,right)));
        }
        return mixer;
    }
    static TrackSelectionParameters tracks(Context context,TrackSelectionParameters current) {
        if(!pcm(context)) return current;
        return current.buildUpon().setMaxAudioChannelCount(2)
            .setPreferredAudioMimeTypes(MimeTypes.AUDIO_AAC,MimeTypes.AUDIO_MPEG,MimeTypes.AUDIO_AC3,MimeTypes.AUDIO_E_AC3).build();
    }
    static String description(Tracks tracks) {
        boolean audio=false,supported=false;
        for(Tracks.Group group:tracks.getGroups()) if(group.getType()==C.TRACK_TYPE_AUDIO) for(int i=0;i<group.length;i++) {
            audio=true; supported|=group.isTrackSupported(i);
            if(group.isTrackSelected(i)) {
                Format f=group.getTrackFormat(i); String codec=f.sampleMimeType==null?"Audio":f.sampleMimeType.replace("audio/","").toUpperCase(java.util.Locale.ROOT);
                return codec+(f.language==null?"":" · "+f.language)+(f.channelCount>0?" · "+f.channelCount+" kanały":"");
            }
        }
        return audio?(supported?"Audio: brak wybranej ścieżki":"Audio: kodek nieobsługiwany przez urządzenie"):"Audio: oczekiwanie na ścieżkę";
    }
}
