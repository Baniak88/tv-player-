package pl.tvplayer;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;
public class M3uParserTest {
    @Test public void parsesBomQuotedCommasAndRelativeUrls() {
        List<Channel> c = M3uParser.parse("\uFEFF#EXTM3U\r\n#EXTINF:-1 tvg-id=\"a\" group-title=\"News, PL\",Kanał A\r\nstream.m3u8", "https://example.com/list.m3u");
        assertEquals(1, c.size()); assertEquals("News, PL", c.get(0).group());
        assertEquals("Kanał A", c.get(0).name()); assertEquals("a", c.get(0).epgId());
        assertEquals("https://example.com/stream.m3u8", c.get(0).url());
    }
    @Test public void ignoresUnsupportedSchemesAndResetsMetadata() {
        List<Channel> c = M3uParser.parse("#EXTINF:-1 group-title=\"Sport\",A\nfile:///private\nhttps://example.com/b", null);
        assertEquals(1, c.size()); assertEquals("Bez kategorii", c.get(0).group()); assertEquals("Kanał 1", c.get(0).name());
    }
    @Test public void acceptsExtGrp() {
        assertEquals("Sport", M3uParser.parse("#EXTINF:-1,A\n#EXTGRP:Sport\nhttps://example.com/a", null).get(0).group());
    }
    @Test public void rejectsRelativeWithoutBase() { assertTrue(M3uParser.parse("relative.ts\nhttp://[broken", null).isEmpty()); }
    @Test public void acceptsEmptyPlaylist() { assertTrue(M3uParser.parse("#EXTM3U\n", null).isEmpty()); }
}
