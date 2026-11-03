package pl.tvplayer;
import org.junit.Test;
import static org.junit.Assert.*;
public class XmltvNameTest {
    @Test public void providerNamesMatchGuideNames() {
        assertEquals(XmltvStore.normalize("Canal+ Premium"),XmltvStore.normalize("PL| CANAL+ PREMIUM FHD"));
        assertEquals(XmltvStore.normalize("Canal+ 1"),XmltvStore.normalize("PL| CANAL+ 1 FHD"));
        assertEquals(XmltvStore.normalize("TVN"),XmltvStore.normalize("PL| TVN HD"));
        assertEquals(XmltvStore.normalize("Polsat Film"),XmltvStore.normalize("PL| POLSAT FILM HD"));
        assertEquals(XmltvStore.normalize("iTVN Extra"),XmltvStore.normalize("PL| ITVN EXTRA (NA)"));
        assertEquals(XmltvStore.normalize("TVP 3 Wrocław"),XmltvStore.normalize("PL| TVP3 WROCLAW (NA)"));
        assertEquals(XmltvStore.normalize("TVP 1"),XmltvStore.normalize("PL| TVP 1 HD"));
    }
}
