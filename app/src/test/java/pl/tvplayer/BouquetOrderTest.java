package pl.tvplayer;
import org.junit.Test;
import java.util.*;
import java.io.StringReader;
import okhttp3.HttpUrl;
import static org.junit.Assert.*;
public class BouquetOrderTest {
    private Channel item(String name,String group,int order) { return new Channel(name,"https://example.com/"+name+".ts",group,"","tv","",0,"UTC",order); }
    @Test public void categoryApiOrderBeatsStreamAndAlphabeticOrder() throws Exception {
        Map<String,String> categories=SourceClient.readCategories(new StringReader("[{\"category_id\":7,\"category_name\":\"Sport\"},{\"category_id\":2,\"category_name\":\"Dzieci\"}]"));
        assertEquals(Arrays.asList("7","2"),new ArrayList<>(categories.keySet()));
        List<Channel> streams=SourceClient.readStreams(new StringReader("[{\"stream_id\":11,\"category_id\":2},{\"stream_id\":12,\"category_id\":7}]"),HttpUrl.get("https://example.com/"),"u","p",categories);
        assertEquals(Arrays.asList("Sport","Dzieci"),new ArrayList<>(BouquetOrder.counts(streams,"provider").keySet()));
        assertEquals("Dzieci",streams.get(0).group()); // Do not reorder channels themselves.
    }
    @Test public void explicitModesAndStableTies() {
        List<Channel> items=Arrays.asList(item("1","Sport",0),item("2","Dzieci",1),item("3","Dzieci",1),item("4","Wiadomości",2));
        assertEquals(Arrays.asList("Sport","Dzieci","Wiadomości"),new ArrayList<>(BouquetOrder.counts(items,"provider").keySet()));
        assertEquals(Arrays.asList("Dzieci","Sport","Wiadomości"),new ArrayList<>(BouquetOrder.counts(items,"az").keySet()));
        assertEquals(Arrays.asList("Wiadomości","Sport","Dzieci"),new ArrayList<>(BouquetOrder.counts(items,"za").keySet()));
        assertEquals(Arrays.asList("Dzieci","Sport","Wiadomości"),new ArrayList<>(BouquetOrder.counts(items,"count").keySet()));
    }
    @Test public void m3uKeepsFirstAppearance() {
        List<Channel> items=Arrays.asList(item("1","Z",Integer.MAX_VALUE),item("2","A",Integer.MAX_VALUE));
        assertEquals(Arrays.asList("Z","A"),new ArrayList<>(BouquetOrder.counts(items,"provider").keySet()));
    }
    @Test public void legacyItemsReceiveProviderRanks() {
        Map<String,String> categories=new LinkedHashMap<>(); categories.put("2","Sport"); categories.put("1","Dzieci");
        List<Channel> items=BouquetOrder.applyRanks(Arrays.asList(item("1","Dzieci",Integer.MAX_VALUE),item("2","Sport",Integer.MAX_VALUE)),categories);
        assertEquals(1,items.get(0).bouquetOrder()); assertEquals(0,items.get(1).bouquetOrder());
    }
    @Test public void accountEndpointRetainsPrefixAndEscapedCredentials() throws Exception {
        HttpUrl api=SourceClient.accountEndpoint(new Channel("Film","https://example.com/sub/movie/u/p/4.mkv","","","movie"));
        assertEquals("/sub/player_api.php",api.encodedPath()); assertEquals("u",api.queryParameter("username")); assertEquals("p",api.queryParameter("password"));
    }
    @Test public void rotationUsesFourDirectionsAndDeadBands() {
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,FullscreenRotation.requested(270,-1));
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,FullscreenRotation.requested(90,-1));
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,FullscreenRotation.requested(0,-1));
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,FullscreenRotation.requested(180,-1));
        assertEquals(8,FullscreenRotation.requested(45,8)); assertEquals(8,FullscreenRotation.requested(-1,8));
    }
}
