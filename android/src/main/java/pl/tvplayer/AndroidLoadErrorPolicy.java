package pl.tvplayer;

import androidx.media3.common.C;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class AndroidLoadErrorPolicy extends DefaultLoadErrorHandlingPolicy {
    private static final long DEFAULT_WAIT_MS = 5000;
    private static final long MAX_WAIT_MS = 30000;

    @Override public long getRetryDelayMsFor(LoadErrorInfo info) {
        for (Throwable cause = info.exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpDataSource.InvalidResponseCodeException response && response.responseCode == 458) {
                if (info.errorCount > 1) return C.TIME_UNSET;
                for (Map.Entry<String, List<String>> header : response.headerFields.entrySet()) {
                    if ("Retry-After".equalsIgnoreCase(header.getKey()) && !header.getValue().isEmpty()) {
                        return retryDelay(header.getValue().get(0), System.currentTimeMillis());
                    }
                }
                return DEFAULT_WAIT_MS;
            }
        }
        return super.getRetryDelayMsFor(info);
    }

    // Do not retry early when the provider requests a longer wait.
    static long retryDelay(String header, long nowMs) {
        String value = header.trim();
        long delay;
        try {
            if (value.matches("[0-9]+")) {
                long seconds = Long.parseLong(value);
                if (seconds > MAX_WAIT_MS / 1000) return C.TIME_UNSET;
                delay = seconds * 1000;
            } else {
                delay = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant().toEpochMilli() - nowMs;
            }
        } catch (NumberFormatException | DateTimeParseException | ArithmeticException invalid) {
            return C.TIME_UNSET;
        }
        return delay > MAX_WAIT_MS ? C.TIME_UNSET : Math.max(DEFAULT_WAIT_MS, delay);
    }
}
