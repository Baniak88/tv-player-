package pl.tvplayer;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Polish → English interface strings. Unknown text (titles, channel names, provider data) is left unchanged. */
final class Translations {
    private static final Map<String,String> EXACT = new HashMap<>();
    private static final List<Object[]> RULES = new ArrayList<>();
    private static void put(String... pairs) { for (int i = 0; i + 1 < pairs.length; i += 2) EXACT.put(pairs[i], pairs[i + 1]); }
    private static void rule(String regex, String replacement) { RULES.add(new Object[]{Pattern.compile(regex), replacement}); }
    static {
        put("Główna", "Home", "TV", "TV", "Filmy", "Movies", "Seriale", "Series", "Szukaj", "Search", "Opcje", "Options",
            "Telewizja na żywo", "Live TV", "Kanały i program TV", "Channels and TV guide", "Kategorie i Moja lista", "Categories and My list",
            "Sezony i odcinki", "Seasons and episodes", "Filmy z Twojej biblioteki", "Movies in your library", "Twoje seriale", "Your series",
            "Z TWOJEJ BIBLIOTEKI", "FROM YOUR LIBRARY", "Wybierz coś na dziś", "Pick something for tonight", "Filmy i seriale od Twojego dostawcy", "Movies and series from your provider",
            "Cała oferta", "All channels", "Wszystkie", "All", "★ Moja lista", "★ My list", "Wszystkie odcinki", "All episodes",
            "Konto", "Account", "Źródło", "Source", "Odśwież źródło", "Refresh source", "Ustawienia", "Settings", "O aplikacji", "About", "Wyjście", "Exit",
            "Zamknij", "Close", "Anuluj", "Cancel", "Zapisz", "Save", "Połącz", "Connect", "Usuń", "Remove", "Wklej", "Paste", "Spróbuj ponownie", "Try again",
            "Źródło: dodaj lub zmień", "Source: add or change", "Źródło: odśwież listę", "Source: refresh list", "Połączenie internetowe (bufor)", "Internet connection (buffer)",
            "Program TV (EPG): własny link XMLTV", "TV guide (EPG): custom XMLTV link", "Ekran i wygląd", "Display and appearance",
            "Marginesy ekranu", "Screen margins", "Wielkość tekstu", "Text size", "Kolory", "Colours", "Język", "Language",
            "Brak dodatkowego marginesu", "No extra margin", "Mały margines (2,5%)", "Small margin (2.5%)", "Duży margines (5%)", "Large margin (5%)",
            "Mały tekst", "Small text", "Standardowy tekst", "Standard text", "Duży tekst", "Large text", "Bardzo duży tekst", "Extra large text",
            "Morski i bursztyn (domyślny)", "Teal and amber (default)", "Niebieski", "Blue", "Zielony", "Green", "Fioletowy", "Purple", "Czerwony", "Red", "Grafitowy", "Graphite",
            "Dodaj źródło", "Add source", "Dodaj przez telefon · ta sama sieć Wi-Fi", "Add with phone · same Wi-Fi", "Konto Xtream Codes · wpisz pilotem", "Xtream Codes account · type with remote",
            "Adres listy M3U · wpisz pilotem", "M3U list address · type with remote", "Konto i biblioteka", "Account and library", "Zmień źródło", "Change source",
            "Nawigacja", "Navigation", "Wróć do przeglądania", "Back to browsing", "Zamknąć TV Player?", "Close TV Player?", "Zostań", "Stay",
            "Kategorie", "Categories", "☆ Ulubione", "☆ Favourites", "Sortuj", "Sort", "Widok", "View", "Rzędy okładek", "Cover rows", "Siatka plakatów", "Poster grid", "Lista", "List",
            "Kolejność dostawcy", "Provider order", "Ostatnio dodane", "Recently added", "Tytuł A–Z", "Title A–Z", "Tytuł Z–A", "Title Z–A", "Rok: najnowsze", "Year: newest",
            "Dodaj do ulubionych", "Add to favourites", "Usuń z ulubionych", "Remove from favourites", "Program TV i archiwum", "TV guide and catch-up",
            "Opcje odtwarzania", "Playback options", "Ścieżka audio", "Audio track", "Napisy", "Subtitles", "Odtwórz od początku", "Play from start",
            "Obraz w obrazie (PiP)", "Picture in picture (PiP)", "Nagraj kanał do pliku", "Record channel to file", "Zatrzymaj nagrywanie", "Stop recording",
            "Cofnij o 30 sekund", "Back 30 seconds", "Głośność i jasność", "Volume and brightness", "Połączenie internetowe", "Internet connection", "Zgodność dźwięku", "Audio compatibility",
            "Włącz, wyłącz lub zmień język", "Turn on, off or change language", "Program TV i powtórki", "TV guide and replays", "Oglądaj w małym oknie", "Watch in a small window",
            "EPG i oglądanie od początku", "EPG and start-over", "Teraz: standard", "Now: standard", "Teraz: słabszy internet", "Now: slower internet",
            "Automatycznie", "Automatic", "Wyłącz napisy", "Subtitles off", "Automatycznie · HDMI / Dolby", "Automatic · HDMI / Dolby", "Stereo PCM · gdy brak dźwięku", "Stereo PCM · if there is no sound",
            "Standard · szybszy start", "Standard · faster start", "Słabszy internet · większy bufor", "Slower internet · bigger buffer",
            "▶  Odtwórz", "▶  Play", "▶  Kontynuuj", "▶  Continue", "▶  Odtwórz ponownie", "▶  Play again", "+  Moja lista", "+  My list", "✓  Moja lista", "✓  My list",
            "Od początku", "From the start", "Odcinki", "Episodes", "Wybierz sezon", "Choose season", "Reżyseria", "Director", "Obsada", "Cast", "Gatunek", "Genre",
            "Pobieranie opisu…", "Loading description…", "Opis nie został udostępniony przez dostawcę.", "The provider has no description for this title.",
            "Ponów pobieranie opisu", "Retry loading description", "Obejrzano", "Watched", "Oglądanie w trakcie", "In progress", "Wczytywanie serialu…", "Loading series…",
            "NA ŻYWO", "LIVE", "●  NA ŻYWO", "●  LIVE", "OK pełny ekran · Menu opcje", "OK full screen · Menu options", "Brak programu TV dla tego kanału", "No TV guide for this channel",
            "Wczytywanie programu…", "Loading guide…", "Buforowanie…", "Buffering…", "Brak kanałów do podglądu", "No channels to preview",
            "TERAZ", "NOW", "POTEM", "NEXT", "Brak danych", "No data", "Brak bieżącego EPG od dostawcy", "No current EPG from the provider",
            "▲▼ kanały    ◀ program TV    ▶ opcje    OK ukryj", "▲▼ channels    ◀ TV guide    ▶ options    OK hide",
            "Wczytywanie biblioteki…", "Loading library…", "Trwa pobieranie biblioteki…", "Downloading library…", "Nie udało się wczytać biblioteki.", "Could not load the library.",
            "Biblioteka jest gotowa. Wybierz TV, filmy lub seriale.", "Library ready. Choose TV, movies or series.",
            "Dodaj konto Xtream lub adres listy M3U, aby rozpocząć.", "Add an Xtream account or an M3U list to start.",
            "Brak pozycji. Wybierz kategorię lub odśwież źródło.", "Nothing here. Choose a category or refresh the source.",
            "Aktualizacja źródła w tle…", "Updating source in the background…", "Łączenie ze źródłem…", "Connecting to source…",
            "Zapisana biblioteka · sprawdzanie aktualizacji…", "Saved library · checking for updates…", "Dostawca niedostępny · korzystasz z zapisanej biblioteki.", "Provider unavailable · using the saved library.",
            "Program TV", "TV guide", "Program TV (EPG)", "TV guide (EPG)", "Szukaj w sekcji", "Search this section", "Hasło", "Password", "Login", "Username");
        rule("^(\\d[\\d ]*) pozycji(.*)$", "$1 items$2");
        rule("^(\\d[\\d ]*) kanałów(.*)$", "$1 channels$2");
        rule("^Pokaż wszystkie · (.*)$", "Show all · $1");
        rule("^Pokaż wszystkie: (.*)$", "Show all: $1");
        rule("^Do końca: (\\d+) min$", "$1 min left");
        rule("^Pozostało (.*)$", "$1 left");
        rule("^Sezon (\\d+)(.*)$", "Season $1$2");
        rule("^Odcinek (\\d+)(.*)$", "Episode $1$2");
        rule("^Następnie (.*)$", "Next $1");
        rule("^Dalej: (.*)$", "Next: $1");
        rule("^(\\d+) godz\\. ?(\\d+)? ?min$", "$1 h $2 min");
        rule("^(\\d+) godz\\.\\s*$", "$1 h");
        rule("^(\\d+) min$", "$1 min");
        rule("^(\\d+) sezony$", "$1 seasons"); rule("^(\\d+) sezonów$", "$1 seasons"); rule("^1 sezon$", "1 season");
        rule("^Film · (.*)$", "Movie · $1"); rule("^Serial · (.*)$", "Series · $1");
        rule("^(Marginesy ekranu|Wielkość tekstu|Kolory|Język): (.*)$", "§$1: $2");
    }
    private Translations() { }

    /** Returns the English text, or the original when nothing is known about it. */
    static String english(String text) {
        if (text == null || text.isEmpty()) return text;
        String exact = EXACT.get(text); if (exact != null) return exact;
        for (Object[] rule : RULES) {
            Matcher m = ((Pattern) rule[0]).matcher(text);
            if (m.matches()) {
                String result = m.replaceFirst((String) rule[1]).replace(" null min", " min");
                if (result.startsWith("§")) { int colon = result.indexOf(": "); return english(result.substring(1, colon)) + ": " + english(result.substring(colon + 2)); }
                return translateParts(result);
            }
        }
        return translateParts(text);
    }
    /** Hints such as "← kategorie  ·  OK szczegóły" are translated part by part. */
    private static String translateParts(String text) {
        for (String separator : new String[]{"\n", "  ·  ", " · "}) {
            if (!text.contains(separator)) continue;
            String[] parts = text.split(Pattern.quote(separator), -1); boolean changed = false;
            for (int i = 0; i < parts.length; i++) { String translated = part(parts[i]); if (!translated.equals(parts[i])) { parts[i] = translated; changed = true; } }
            return changed ? String.join(separator, parts) : text;
        }
        return text;
    }
    private static final Map<String,String> HINTS = new HashMap<>();
    static {
        String[] hints = {"← kategorie", "← categories", "↑↓ rzędy", "↑↓ rows", "OK szczegóły", "OK details", "Menu opcje", "Menu options", "↑↓ wybór", "↑↓ select",
            "OK otwórz", "OK open", "Wstecz nawigacja", "Back navigation", "OK podgląd / pełny ekran", "OK preview / full screen", "← → wybierz", "← → select",
            "↓ Twoja biblioteka", "↓ Your library", "Moja lista", "My list", "Ulubione", "Favourites", "Program TV", "TV guide", "pamięć", "memory"};
        for (int i = 0; i + 1 < hints.length; i += 2) HINTS.put(hints[i], hints[i + 1]);
    }
    private static String part(String piece) {
        String exact = EXACT.get(piece); if (exact != null) return exact;
        String hint = HINTS.get(piece.trim()); if (hint != null) return piece.replace(piece.trim(), hint);
        for (Object[] rule : RULES) { Matcher m = ((Pattern) rule[0]).matcher(piece); if (m.matches()) return m.replaceFirst((String) rule[1]).replace(" null min", " min"); }
        return piece;
    }
}
