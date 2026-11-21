package pl.tvplayer;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TvCatalogPresentationTest {
    @Test public void seriesUsesFirstAvailableArtworkWithoutChangingEpisodeMetadata() {
        Channel first=new Channel("Serial S01 E01","https://host/series/1.mp4","Seriale","");
        Channel second=new Channel("Serial S01 E02","https://host/series/2.mp4","Seriale","").withLogo("https://images.example/poster.jpg");
        Channel third=new Channel("Serial S01 E03","https://host/series/3.mp4","Seriale","").withLogo("https://images.example/still.jpg");
        TvCatalog.Catalog catalog=TvCatalog.series(Arrays.asList(first,second,third));
        Channel folder=catalog.items().get(0); assertEquals(second.logo(),folder.logo());
        assertEquals(Arrays.asList(first,second,third),catalog.folders().get(folder.url()));
        assertEquals(third.logo(),TvCatalog.seasons(catalog.folders().get(folder.url())).get(2).logo());
        assertEquals(second.logo(),TvCatalog.series(Arrays.asList(second,third,first)).items().get(0).logo());
    }
    @Test public void m3uArtworkResolvesRelativeUrlsAndNeverLeaksIntoTheNextEntry() {
        String text="#EXTM3U\n#EXTINF:-1 tvg-logo=\"../posters/film.jpg\",Film\n/movie/1.mp4\n#EXTINF:-1 tvg-logo=\"//images.example/series.jpg\",Serial S01 E01\n/series/2.mp4\n#EXTINF:-1,Bez obrazu\n/movie/3.mp4\n#EXTINF:-1 tvg-logo=\"file:///private.jpg\",Zły obraz\n/movie/4.mp4";
        List<Channel> items=M3uParser.parse(text,"https://provider.example/catalog/list.m3u?token=playlist-only");
        assertEquals("https://provider.example/posters/film.jpg",items.get(0).logo());
        assertEquals("https://images.example/series.jpg",items.get(1).logo());
        assertEquals("",items.get(2).logo()); assertEquals("",items.get(3).logo());
        assertEquals("https://images.example/series.jpg",TvCatalog.series(Collections.singletonList(items.get(1))).items().get(0).logo());
        assertEquals("https://images.example/A%20poster.jpg",M3uParser.parse("#EXTINF:-1 tvg-logo=\"https://images.example/A poster.jpg\",Film\nhttps://provider.example/movie/5.mp4",null).get(0).logo());
    }
    @Test public void movieBouquetNamesDoNotTurnLiveChannelsIntoVod() {
        for(String group:Arrays.asList("UK| MOVIES","UK| MOVIES HEVC","UK| 24/7 MOVIES & SERIES","US| MOVIES NETWORK","PL| FILMY")) {
            Channel c=M3uParser.parse("#EXTINF:-1 tvg-id=\"sky.uk\" group-title=\""+group+"\",UK| SKY CINEMA ACTION FHD\nhttps://provider.example/u/p/123",null).get(0);
            assertEquals(group,"tv",c.kind()); assertEquals("tv",Channel.repairKind(new Channel(c.name(),c.url(),group,c.epgId(),"movie")).kind());
            assertEquals("tv",new Channel(c.name(),c.url(),group,"").kind());
        }
    }
    @Test public void explicitVodFilesAndProviderTypesRemainMovies() {
        assertEquals("movie",new Channel("Film","https://host/movie/u/p/1.mkv","UK| MOVIES","").kind());
        assertEquals("movie",new Channel("Film","https://host/a.mp4","UK| MOVIES","").kind());
        assertEquals("movie",new Channel("Film","https://host/u/p/1","VOD | UK","").kind());
        assertEquals("movie",M3uParser.parse("#EXTINF:-1 tvg-type=\"vod\" group-title=\"UK| MOVIES\",Film\nhttps://host/u/p/1",null).get(0).kind());
        Channel explicit=new Channel("Film","https://host/custom/1","Dramat","","movie"); assertEquals(explicit,Channel.repairKind(explicit));
    }
    @Test public void m3uEpisodesBecomeOneFolderPerTitleAndBouquetInNumericOrder() {
        Channel second=new Channel("PL - Nocna cisza S02 E03","https://host/series/u/p/3.mp4","PL| SERIALE","");
        Channel first=new Channel("PL - Nocna cisza S01 E01","https://host/series/u/p/1.mp4","PL| SERIALE","");
        Channel otherLanguage=new Channel("PL - Nocna cisza S01 E01","https://host/series/u/p/2.mp4","EN| SERIES","");
        Channel api=new Channel("Serial API","https://host/player_api.php?series_id=9","PL| SERIALE","","series");
        TvCatalog.Catalog result=TvCatalog.series(Arrays.asList(second,first,otherLanguage,api));
        assertEquals(3,result.items().size()); assertEquals(2,result.folders().size()); assertEquals("PL - Nocna cisza",result.items().get(0).name());
        List<Channel> episodes=result.folders().get(result.items().get(0).url()); assertEquals(Arrays.asList(first,second),episodes);
        assertEquals(first.url(),TvCatalog.seasons(episodes).get(0).url()); assertEquals("Sezon 2",TvCatalog.seasons(episodes).get(1).group());
        assertSame(api,result.items().get(2));
        assertEquals(result.items().get(0).url(),TvCatalog.series(Arrays.asList(first,second)).items().get(0).url());
    }
    @Test public void largeFlatEpisodeCatalogKeepsOnlySeriesInTheGrid() {
        List<Channel> source=new ArrayList<>();
        for(int title=0;title<1000;title++) for(int episode=20;episode>=1;episode--) source.add(new Channel("Serial "+title+" S01 E"+episode,"https://host/series/u/p/"+title+"-"+episode+".mp4","PL| SERIALE",""));
        TvCatalog.Catalog catalog=TvCatalog.series(source); assertEquals(1000,catalog.items().size()); assertEquals(1000,catalog.folders().size());
        assertEquals("https://host/series/u/p/0-1.mp4",catalog.folders().get(catalog.items().get(0).url()).get(0).url());
    }
}
