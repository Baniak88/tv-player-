package pl.tvplayer;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class SeriesDetailsTest {
    private Channel source() { return new Channel("PL - KRÓLOWA KLEOPATRA","https://example.com/portal/player_api.php?username=a%2Bb&password=p%26ss&action=get_series_info&series_id=17","Dokumenty","","series").withLogo("https://example.com/cover.jpg"); }
    private SeriesDetails parse(String json) throws Exception { return SeriesDetails.parse(new StringReader(json),source()); }
    @Test public void parsesMetadataAndOrdersSeasonsAndEpisodesNumerically() throws Exception {
        SeriesDetails detail=parse("{\"info\":{\"name\":\"PL - KRÓLOWA KLEOPATRA\",\"plot\":\"Historia władczyni\",\"releaseDate\":\"2023-05-10\",\"rating\":\"7.5\",\"backdrop_path\":[null,\"https://example.com/hero.jpg\"]},\"episodes\":{\"10\":[{\"id\":103,\"episode_num\":1}],\"2\":[{\"id\":102,\"episode_num\":2,\"title\":\"PL - KRÓLOWA KLEOPATRA - S02E02 - Z wizytą w Rzymie\",\"info\":{\"duration\":\"00:48:12\",\"plot\":\"Nowa podróż\",\"movie_image\":\"https://example.com/still.jpg\"}},{\"id\":101,\"episode_num\":1,\"title\":\"S02E01 - Rywale\"}]}}");
        assertEquals("KRÓLOWA KLEOPATRA",detail.title()); assertEquals("2023",detail.year()); assertEquals("7.5",detail.rating()); assertEquals("Historia władczyni",detail.overview()); assertEquals("https://example.com/hero.jpg",detail.backdrop());
        assertEquals(2,detail.seasons().get(0).number()); assertEquals(10,detail.seasons().get(1).number());
        SeriesDetails.Episode episode=detail.seasons().get(0).episodes().get(1); assertEquals("Z wizytą w Rzymie",episode.title()); assertEquals(2892,episode.durationSeconds()); assertEquals("Nowa podróż",episode.overview()); assertEquals("https://example.com/still.jpg",episode.image());
        assertEquals("https://example.com/portal/series/a+b/p&ss/102.mp4",episode.channel().url()); assertEquals("episode",episode.channel().kind()); assertEquals("Rywale",detail.seasons().get(0).episodes().get(0).title());
    }
    @Test public void incompleteProviderMetadataUsesHonestFallbacks() throws Exception {
        SeriesDetails detail=parse("{\"info\":null,\"episodes\":{\"1\":[{\"id\":1,\"episode_num\":1,\"title\":null,\"info\":{\"duration_secs\":2700}},{\"id\":2,\"title\":\"PL - KRÓLOWA KLEOPATRA - S01E02\",\"container_extension\":\"mkv\"},{\"id\":\"../wrong\"}]}}");
        assertEquals("https://example.com/cover.jpg",detail.cover()); assertEquals("",detail.overview()); assertEquals("",detail.backdrop()); assertEquals(2,detail.allEpisodes().size()); assertEquals("Odcinek 1",detail.allEpisodes().get(0).title()); assertEquals(2700,detail.allEpisodes().get(0).durationSeconds()); assertEquals("Odcinek 2",detail.allEpisodes().get(1).title()); assertTrue(detail.allEpisodes().get(1).channel().url().endsWith("2.mkv"));
    }
    @Test public void episodeTitlesRetainActualWordsAndRemoveOnlyLeadingMarkers() {
        assertEquals("Trudne decyzje",SeriesDetails.cleanEpisodeTitle("PL - KRÓLOWA KLEOPATRA - S01E03 - Trudne decyzje",source().name(),3));
        assertEquals("Droga S01E03 do domu",SeriesDetails.cleanEpisodeTitle("Droga S01E03 do domu",source().name(),3));
        assertEquals("Rywale",SeriesDetails.cleanEpisodeTitle("KRÓLOWA KLEOPATRA: 1x01 - Rywale",source().name(),1));
    }
    @Test public void missingEpisodesRemainEmpty() throws Exception {
        assertTrue(parse("{\"episodes\":[]}").allEpisodes().isEmpty());
        try { parse("{\"episodes\":\"bad\"}"); fail("Malformed episodes accepted"); } catch(IOException expected) {}
    }
}
