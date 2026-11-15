package pl.tvplayer;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import okhttp3.HttpUrl;
import static org.junit.Assert.*;

public class LargePlaylistTest {
    // Generates a >30 MB source lazily, without allocating a huge fixture.
    static final class RepeatingReader extends Reader {
        final String prefix, repeat, suffix;
        final int repetitions;
        int part, iteration, offset;
        long chars;
        RepeatingReader(String prefix, String repeat, int repetitions, String suffix) {
            this.prefix = prefix; this.repeat = repeat; this.repetitions = repetitions; this.suffix = suffix;
        }
        @Override public int read(char[] out, int start, int count) {
            if (count == 0) return 0;
            int written = 0;
            while (written < count && part < 3) {
                String text = part == 0 ? prefix : part == 1 ? repeat : suffix;
                if (offset == text.length()) {
                    offset = 0;
                    if (part == 1 && ++iteration < repetitions) continue;
                    part++; continue;
                }
                int n = Math.min(count - written, text.length() - offset);
                text.getChars(offset, offset + n, out, start + written);
                offset += n; written += n; chars += n;
            }
            return written == 0 ? -1 : written;
        }
        @Override public void close() {}
    }
    static String padding() { char[] chars = new char[1024]; Arrays.fill(chars, 'x'); return new String(chars); }
    @Test public void m3uOverThirtyMbRetainsAllChannels() throws Exception {
        String metadata = padding();
        RepeatingReader reader = new RepeatingReader("\uFEFF#EXTM3U\r\n",
                "#EXTINF:-1 group-title=\"Sport\" tvg-logo=\"" + metadata + "\",Kanał\r\nhttps://example.com/live.ts\r\n",
                32000, "");
        List<Channel> channels = M3uParser.parse(reader, null);
        assertTrue(reader.chars > 30_000_000);
        assertEquals(32000, channels.size());
        assertEquals("Sport", channels.get(31999).group());
        assertEquals("Kanał", channels.get(31999).name());
    }
    @Test public void xtreamOverThirtyMbSkipsUnneededMetadata() throws Exception {
        String metadata = padding();
        String item = "{\"stream_id\":42,\"name\":\"TV\",\"category_id\":\"7\",\"epg_channel_id\":null,\"extra\":\"" + metadata + "\"}";
        RepeatingReader reader = new RepeatingReader("[", item + ",", 32000, item + "]");
        List<Channel> channels = SourceClient.readStreams(reader, HttpUrl.get("https://example.com/"), "login", "password", Collections.singletonMap("7", "Sport"));
        assertTrue(reader.chars > 30_000_000);
        assertEquals(32001, channels.size());
        assertEquals("Sport", channels.get(32000).group());
        assertEquals("", channels.get(0).epgId());
        assertEquals("https://example.com/live/login/password/42.ts", channels.get(0).url());
    }
    @Test public void readerFailurePropagatesInsteadOfReturningPartialImport() throws Exception {
        Reader broken = new Reader() {
            public int read(char[] b, int offset, int length) throws IOException { throw new IOException("read failed"); }
            public void close() {}
        };
        try { M3uParser.parse(broken, null); fail("Expected IOException"); }
        catch (IOException expected) { assertEquals("read failed", expected.getMessage()); }
    }
    @Test public void categoriesAcceptNumericIdsAndSkipMetadata() throws Exception {
        Map<String, String> categories = SourceClient.readCategories(new StringReader("[{\"category_id\":7,\"category_name\":\"Sport\",\"extra\":[1,2]}]"));
        assertEquals("Sport", categories.get("7"));
    }
    @Test public void malformedXtreamIsRejected() throws Exception {
        try {
            SourceClient.readStreams(new StringReader("[{\"stream_id\":1}"), HttpUrl.get("https://example.com/"), "u", "p", Collections.emptyMap());
            fail("Expected IOException");
        } catch (IOException expected) {}
    }
}
