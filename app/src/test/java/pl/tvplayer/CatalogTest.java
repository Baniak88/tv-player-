package pl.tvplayer;
import org.junit.Test;
import java.io.StringReader;
import java.util.*;
import okhttp3.HttpUrl;
import static org.junit.Assert.*;
public class CatalogTest {
    @Test public void moviesKeepProviderExtension() throws Exception {
        List<Channel> movies=SourceClient.readCatalog(new StringReader("[{\"stream_id\":12,\"name\":\"Film\",\"category_id\":7,\"container_extension\":\"mkv\"}]"),
            HttpUrl.get("https://example.com/"),"u","p",Collections.singletonMap("7","Kino"),"movie");
        assertEquals("movie",movies.get(0).section());
        assertEquals("https://example.com/movie/u/p/12.mkv",movies.get(0).url());
    }
    @Test public void seriesResolveToApiInsteadOfVideo() throws Exception {
        List<Channel> series=SourceClient.readCatalog(new StringReader("[{\"series_id\":8,\"name\":\"Serial\"}]"),
            HttpUrl.get("https://example.com/"),"u","p",Collections.emptyMap(),"series");
        assertEquals("series",series.get(0).kind());
        assertEquals("8",HttpUrl.get(series.get(0).url()).queryParameter("series_id"));
    }
    @Test public void episodesHavePlayableUrlsAndSeasons() throws Exception {
        List<Channel> episodes=SourceClient.parseEpisodes(new StringReader("{\"episodes\":{\"2\":[{\"id\":21,\"episode_num\":1,\"title\":\"Pilot\",\"container_extension\":\"mp4\"}],\"1\":[]}}"),
            HttpUrl.get("https://example.com/"),"u","p","Serial");
        assertEquals(1,episodes.size()); assertEquals("Sezon 2",episodes.get(0).group());
        assertEquals("episode",episodes.get(0).kind()); assertEquals("series",episodes.get(0).section());
        assertEquals("https://example.com/series/u/p/21.mp4",episodes.get(0).url());
    }
    @Test public void m3uClassifiesKnownVodPathsButKeepsLiveTv() {
        assertEquals("movie",new Channel("A","https://example.com/movie/u/p/1.mkv","Kino","").kind());
        assertEquals("series",new Channel("A","https://example.com/series/u/p/2.mp4","Serial","").section());
        assertEquals("tv",new Channel("TV","https://example.com/live/u/p/3.ts","Sport","").kind());
    }
}
