package pl.tvplayer;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class TitleSearchTest {
    @Test public void ignoresCaseAccentsAndPunctuation() {
        assertEquals("zolw ninja 2024",TitleSearch.normalize("Żółw: Ninja (2024)"));
        assertTrue(TitleSearch.matches("PL - Północny szlak","polnocny"));
        assertTrue(TitleSearch.matches("Szybcy i wściekli 7","wsciekli szybcy"));
        assertFalse(TitleSearch.matches("Szybcy i wściekli 7","wsciekli 8"));
    }
    @Test public void ranksTitlePrefixThenWordStartThenAnywhere() {
        String[] q=TitleSearch.tokens("mat");
        assertEquals(0,TitleSearch.score(TitleSearch.normalize("PL - Matrix"),q));
        assertEquals(1,TitleSearch.score(TitleSearch.normalize("Powrót do Matrixa"),q));
        assertEquals(2,TitleSearch.score(TitleSearch.normalize("Automat"),q));
        assertEquals(-1,TitleSearch.score(TitleSearch.normalize("Avatar"),q));
    }
    @Test public void titleKeySkipsProviderTags() {
        assertEquals("matrix",TitleSearch.titleKey(TitleSearch.normalize("|PL| 4K - Matrix")));
        assertEquals("pl",TitleSearch.titleKey("pl"));
    }
    @Test public void sortsByTitleYearAndAdded() {
        List<Channel> items=Arrays.asList(
            new Channel("PL - Zorro (1998)","https://e.com/movie/u/p/1.mp4","Kino","","movie").withAdded(100),
            new Channel("Avatar 2009","https://e.com/movie/u/p/2.mp4","Kino","","movie").withAdded(300),
            new Channel("EN - Matrix (1999)","https://e.com/movie/u/p/3.mp4","Kino","","movie").withAdded(200));
        String[] keys=new String[3]; for(int i=0;i<3;i++) keys[i]=TitleSearch.normalize(items.get(i).name());
        assertEquals(Arrays.asList(1,2,0),sorted(items,keys,TitleSearch.AZ));
        assertEquals(Arrays.asList(0,2,1),sorted(items,keys,TitleSearch.ZA));
        assertEquals(Arrays.asList(1,2,0),sorted(items,keys,TitleSearch.ADDED));
        assertEquals(Arrays.asList(1,2,0),sorted(items,keys,TitleSearch.YEAR));
        assertEquals(Arrays.asList(0,1,2),sorted(items,keys,TitleSearch.PROVIDER));
        assertEquals(2009,TitleSearch.year("Avatar 2009")); assertEquals(0,TitleSearch.year("Kanał 12345"));
    }
    @Test public void addedSurvivesCopies() {
        Channel c=new Channel("A","https://e.com/movie/u/p/1.mp4","Kino","","movie").withAdded(42);
        assertEquals(42,c.withLogo("https://img/x.jpg").withOrder(3).withTimezone("UTC").added());
    }
    private static List<Integer> sorted(List<Channel> items,String[] keys,String mode) {
        List<Integer> p=new ArrayList<>(Arrays.asList(0,1,2)); TitleSearch.sort(p,items,keys,mode); return p;
    }
}
