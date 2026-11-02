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
    @Test public void liveMovieChannelsStayInTv() {
        assertEquals("tv",new Channel("Polsat Film HD","http://host:8080/user/pass/1234","PL| FILMOWE","").kind());
        assertEquals("tv",new Channel("TVN Film","https://example.com/stream/tvnfilm.m3u8","PL| FILMY","").kind());
        assertEquals("tv",new Channel("Canal+ Film","https://example.com/u/p/55.ts","VOD Kanały","").kind());
        assertEquals("movie",new Channel("Matrix","https://example.com/u/p/77","VOD | Filmy","").kind());
        assertEquals("movie",new Channel("Matrix","https://example.com/u/p/77.mkv","Akcja","").kind());
        assertEquals("series",new Channel("Serial S01 E02","https://example.com/u/p/78.mp4","Dramat","").section());
    }
    @Test public void storedMisclassifiedLiveChannelsMoveBackToTv() {
        assertEquals("tv",Channel.repairKind(new Channel("Polsat Film","http://host/u/p/12.ts","PL| FILMOWE","","movie")).section());
        assertEquals("movie",Channel.repairKind(new Channel("Film","https://example.com/movie/u/p/12.mkv","Kino","","movie")).section());
        assertEquals("series",Channel.repairKind(new Channel("Serial","https://example.com/player_api.php?action=get_series_info","Dramat","","series")).section());
    }
    @Test public void sourceAddressGetsSchemeWhenMissing() {
        assertEquals("http://dostawca.tv:8080",SourceSheet.normalize("  dostawca.tv:8080 "));
        assertEquals("https://example.com/a.m3u",SourceSheet.normalize("https://example.com/a.m3u"));
        assertEquals("",SourceSheet.normalize(" "));
    }
    @Test public void postersAreReadFromXtreamAndM3u() throws Exception {
        List<Channel> movies=SourceClient.readCatalog(new StringReader("[{\"stream_id\":12,\"name\":\"Film\",\"stream_icon\":\"https://img.example.com/p.jpg\"}]"),
            HttpUrl.get("https://example.com/"),"u","p",Collections.emptyMap(),"movie");
        assertEquals("https://img.example.com/p.jpg",movies.get(0).logo());
        List<Channel> series=SourceClient.readCatalog(new StringReader("[{\"series_id\":8,\"name\":\"Serial\",\"cover\":\"https://img.example.com/s.jpg\"}]"),
            HttpUrl.get("https://example.com/"),"u","p",Collections.emptyMap(),"series");
        assertEquals("https://img.example.com/s.jpg",series.get(0).logo());
        List<Channel> m3u=M3uParser.parse("#EXTINF:-1 tvg-logo=\"https://img.example.com/tvp.png\" group-title=\"PL\",TVP 1\nhttps://example.com/1.ts\n#EXTINF:-1,B\nhttps://example.com/2.ts",null);
        assertEquals("https://img.example.com/tvp.png",m3u.get(0).logo()); assertEquals("",m3u.get(1).logo());
        assertEquals("https://img.example.com/tvp.png",m3u.get(0).withOrder(3).logo());
    }
    @Test public void panelPlaylistPointsGuideAtThePanelNotTheStreamHost() throws Exception {
        HttpUrl playlist=HttpUrl.get("http://panel.example.com:8080/get.php?username=u&password=p&type=m3u_plus");
        List<Channel> parsed=M3uParser.parse("#EXTM3U\n#EXTINF:-1,TVP 1\nhttp://cdn.example.net/u/p/123.ts\n#EXTINF:-1,Film\nhttp://cdn.example.net/movie/u/p/9.mkv",playlist.toString());
        List<Channel> fixed=SourceClient.withPanelGuide(playlist,parsed);
        HttpUrl api=SourceClient.accountEndpoint(fixed.get(0));
        assertEquals("panel.example.com",api.host()); assertEquals(8080,api.port()); assertEquals("/player_api.php",api.encodedPath());
        assertEquals("u",api.queryParameter("username")); assertEquals("123",Guide.endpoint(fixed.get(0)).queryParameter("stream_id"));
        assertEquals("panel.example.com",Guide.xmltv(fixed.get(0)).host());
        assertEquals("",fixed.get(1).guideUrl());
        HttpUrl plain=HttpUrl.get("https://example.com/list.m3u");
        assertEquals(parsed,SourceClient.withPanelGuide(plain,parsed));
    }
}
