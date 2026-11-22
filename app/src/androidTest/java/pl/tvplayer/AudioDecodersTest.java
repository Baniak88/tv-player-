package pl.tvplayer;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.decoder.ffmpeg.FfmpegLibrary;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Channels with Dolby or MPEG layer II audio must not play silently on devices without those decoders. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
@RunWith(AndroidJUnit4.class)
public class AudioDecodersTest {
    @Test public void ffmpegDecodesBroadcastAudioFormats() {
        assertTrue("FFmpeg audio library is not loaded",FfmpegLibrary.isAvailable());
        for(String mime:new String[]{MimeTypes.AUDIO_AC3,MimeTypes.AUDIO_E_AC3,MimeTypes.AUDIO_MPEG_L2,MimeTypes.AUDIO_MPEG,MimeTypes.AUDIO_DTS,MimeTypes.AUDIO_AAC})
            assertTrue("Missing software decoder for "+mime,FfmpegLibrary.supportsFormat(mime));
        Format ac3=new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AC3).setChannelCount(6).setSampleRate(48000).build();
        assertNotNull(ac3);
    }
}
