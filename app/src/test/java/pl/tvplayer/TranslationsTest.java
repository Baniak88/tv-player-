package pl.tvplayer;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class TranslationsTest {
    @Test public void interfaceTextIsTranslatedAndProviderDataIsKept() {
        assertEquals("Live TV", Translations.english("Telewizja na żywo"));
        assertEquals("582 items · ↑↓ select · OK open · Menu options · Back navigation", Translations.english("582 pozycji · ↑↓ wybór · OK otwórz · Menu opcje · Wstecz nawigacja"));
        assertEquals("25 min left", Translations.english("Do końca: 25 min"));
        assertEquals("Colours: Blue", Translations.english("Kolory: Niebieski"));
        assertEquals("Season 1 · Episode 1 · Rywale", Translations.english("Sezon 1 · Odcinek 1 · Rywale"));
        assertEquals("Movies · |PL| FAMILIJNE", Translations.english("Filmy · |PL| FAMILIJNE"));
        assertEquals("PL - Flavia (2026) Dubbing", Translations.english("PL - Flavia (2026) Dubbing"));
    }
}
