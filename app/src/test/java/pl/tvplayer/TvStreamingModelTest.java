package pl.tvplayer;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TvStreamingModelTest {
    @Test public void metadataPoolReusesRepeatedValuesAndStaysBounded() {
        BoundedStringPool pool=new BoundedStringPool(32); String group=new String("UK Movies"); assertSame(group,pool.canonical(group)); assertSame(group,pool.canonical(new String("UK Movies")));
        for(int i=0;i<10000;i++) pool.canonical("Artwork "+i); assertEquals(32,pool.size());
    }
    private Channel channel(int n,String group) { return new Channel("Film "+n,"https://provider.example/movie/user/secret/"+n+".mp4",group,"","movie"); }
    @Test public void shelvesKeepProviderOrderAndBoundLargeRows() {
        List<Channel> data=new ArrayList<>(); for(int i=0;i<20000;i++) data.add(channel(i,i<15000?"Przygoda":"Dramat"));
        List<TvShelfCatalog.Shelf> rows=TvShelfCatalog.build(data,"provider");
        assertEquals(2,rows.size()); assertEquals("Przygoda",rows.get(0).title()); assertEquals(15000,rows.get(0).total());
        assertEquals(24,rows.get(0).channels().size()); assertTrue(rows.get(0).more()); assertSame(data.get(15000),rows.get(1).channels().get(0));
    }
    @Test public void smallShelfDoesNotOfferAnEmptyMorePage() { TvShelfCatalog.Shelf row=TvShelfCatalog.build(Arrays.asList(channel(1,"Kino"),channel(2,"Kino")),"provider").get(0); assertFalse(row.more()); assertEquals(2,row.total()); }
    @Test public void browsingAnchorLocatesTheSameItemAfterReorderingWithoutSavingCredentials() {
        Channel wanted=channel(4,"Kino"); TvBrowseState state=new TvBrowseState(false,"movie","Kino","",false,"","",TvBrowseState.id(wanted),wanted.name(),0,0,0,0,0,true,false,"Kino",0,null);
        assertEquals(1,state.locate(Arrays.asList(channel(1,"Kino"),wanted))); String json=new com.google.gson.Gson().toJson(state);
        assertFalse(json.contains("secret")); assertFalse(json.contains("provider.example"));
        TvBrowseState restored=new com.google.gson.Gson().fromJson(json,TvBrowseState.class); assertEquals(state,restored); assertEquals(0,state.locate(Collections.singletonList(channel(9,"Kino"))));
    }
    @Test public void episodeParentLocationSurvivesSerialization() {
        TvBrowseState root=new TvBrowseState(false,"series","Polskie","noc",true,"","","id","Nocna",4,3,-12,1,-4,true,false,"Polskie",4,null);
        TvBrowseState episode=new TvBrowseState(false,"series","Sezon 2","",false,"local-series:123","Nocna","episode","S02 E03",2,1,-8,0,0,true,false,"",0,root);
        assertEquals(episode,new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(episode),TvBrowseState.class)); assertEquals(root,episode.parent());
    }
}
