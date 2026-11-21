package pl.tvplayer;

import androidx.media3.common.C;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import java.util.List;
import java.util.Map;

/**
 * IPTV panels answer HTTP 458 while a previous session of the same account is still open (e.g. right after a
 * channel or film change). Waiting once, as the server asks, lets playback continue instead of failing.
 */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class ProviderLoadErrorPolicy extends DefaultLoadErrorHandlingPolicy {
    private static final long DEFAULT_WAIT_MS = 5000, MAX_WAIT_MS = 30000;

    @Override public long getRetryDelayMsFor(LoadErrorInfo info) {
        for (Throwable cause = info.exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpDataSource.InvalidResponseCodeException response && response.responseCode == 458) {
                if (info.errorCount > 2) return C.TIME_UNSET;
                for (Map.Entry<String, List<String>> header : response.headerFields.entrySet()) {
                    if ("Retry-After".equalsIgnoreCase(header.getKey()) && !header.getValue().isEmpty()) {
                        try { long seconds = Long.parseLong(header.getValue().get(0).trim()); return seconds * 1000 > MAX_WAIT_MS ? C.TIME_UNSET : Math.max(DEFAULT_WAIT_MS, seconds * 1000); }
                        catch (NumberFormatException ignored) { return DEFAULT_WAIT_MS; }
                    }
                }
                return DEFAULT_WAIT_MS;
            }
        }
        return super.getRetryDelayMsFor(info);
    }
}
