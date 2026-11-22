package pl.tvplayer;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class MovieDetailsTest {
    private Channel source(String url) { return new Channel("PL - Północny szlak",url,"Kino","","movie").withLogo("https://example.com/poster.jpg"); }
    @Test public void derivesXtreamMetadataEndpointWithoutChangingPlaybackAddress() {
        String original="https://example.com:8443/portal/movie/a%2Bb/p%2Fss/731.mkv?token=private"; Channel movie=source(original); okhttp3.HttpUrl endpoint=MovieDetails.endpoint(movie);
        assertNotNull(endpoint); assertEquals("/portal/player_api.php",endpoint.encodedPath()); assertEquals("a+b",endpoint.queryParameter("username")); assertEquals("p/ss",endpoint.queryParameter("password")); assertEquals("731",endpoint.queryParameter("vod_id")); assertEquals("get_vod_info",endpoint.queryParameter("action")); assertNull(endpoint.queryParameter("token")); assertEquals(original,movie.url());
    }
    @Test public void genericM3uUrlsDoNotInventAnApiEndpoint() {
        assertNull(MovieDetails.endpoint(source("https://cdn.example.com/files/movie.mp4"))); assertNull(MovieDetails.endpoint(source("https://example.com/movie/user/pass/not-an-id.mp4"))); assertNull(MovieDetails.endpoint(source("https://example.com/movie/user/pass/123.mp4/extra")));
    }
    @Test public void parsesProviderMetadataAndOnlyAcceptsHttpArtwork() throws Exception {
        MovieDetails d=MovieDetails.parse(new StringReader("{\"info\":{\"name\":\"PL - Północny szlak\",\"plot\":\"Podróż przez góry.\",\"releasedate\":\"2024-03-18\",\"duration\":\"02:02:00\",\"rating\":\"8.2\",\"genre\":\"Przygodowy\",\"director\":\"Aleksander Kowalski\",\"cast\":\"Anna Nowak\",\"movie_image\":\"file:///private.jpg\",\"backdrop_path\":[null,\"https://example.com/backdrop.jpg\"]}}"),source("https://example.com/movie/u/p/1.mp4"));
        assertEquals("Północny szlak",d.title()); assertEquals("Podróż przez góry.",d.overview()); assertEquals("2024",d.year()); assertEquals(7320,d.durationSeconds()); assertEquals("8.2",d.rating()); assertEquals("Anna Nowak",d.cast()); assertEquals("Aleksander Kowalski",d.director()); assertEquals("https://example.com/poster.jpg",d.cover()); assertEquals("https://example.com/backdrop.jpg",d.backdrop());
    }
    @Test public void missingMetadataUsesSourceAndMalformedPayloadFailsClearly() throws Exception {
        Channel source=source("https://example.com/movie/u/p/1.mp4"); MovieDetails d=MovieDetails.parse(new StringReader("{\"info\":null,\"movie_data\":{\"name\":\"PL - Nazwa od dostawcy\"}}"),source); assertEquals("Nazwa od dostawcy",d.title()); assertEquals("",d.overview()); assertEquals(0,d.durationSeconds()); assertEquals(source.logo(),d.cover());
        try { MovieDetails.parse(new StringReader("[]"),source); fail("Invalid response accepted"); } catch(IOException expected) {}
    }
    @Test public void shortDurationIsHoursAndMinutesAndRatingIsRounded() throws Exception {
        MovieDetails film=MovieDetails.parse(new StringReader("{\"info\":{\"name\":\"Film\",\"duration\":\"01:42\",\"rating\":\"6.182\"}}"),source("https://example.com/movie/u/p/2.mp4"));
        assertEquals(6120,film.durationSeconds()); assertEquals("6.2",MovieActivity.rating(film.rating())); assertEquals("8",MovieActivity.rating("8.0"));
        MovieDetails episode=MovieDetails.parse(new StringReader("{\"info\":{\"name\":\"Odcinek\",\"duration\":\"45:30\"}}"),source("https://example.com/movie/u/p/3.mp4"));
        assertEquals(2730,episode.durationSeconds());
    }
}
