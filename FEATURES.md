# TV Player Preview 0.3.0

APK do bezpośredniej instalacji: Releases → v0.3.0-preview → TV-Player-0.3.0.apk. Artefakty Actions pozostają archiwami dla diagnostyki. Identyfikator i klucz podpisu Preview pozostają stałe, aplikacja aktualizuje 0.2.0.

## Działa w tej wersji

- Osobne sekcje TV, Seriale i Filmy, kategorie, wyszukiwanie, ulubione.
- Xtream: TV, katalog VOD z formatem dostawcy, seriale, sezony, odcinki pobierane na żądanie.
- M3U: rozpoznawanie filmów i odcinków po ścieżkach /movie/, /series/, znanych rozszerzeniach i oznaczeniach. M3U bez metadanych nie pozwala wiarygodnie odtworzyć struktury serial/sezon/odcinek; taka pozycja może pozostać w TV.
- Import strumieniowy bez limitu 30 MB. Szyfrowana biblioteka zgodna ze starszym zapisem.
- Pełny ekran obraca się według czujnika telefonu (FULL_SENSOR). Przycisk ↙ wyłącza pełny ekran i wraca do pionu; ⤢ przywraca pełny ekran.
- Wznawianie filmów i odcinków, odtwarzanie od początku w menu, wybór dostępnych ścieżek audio i napisów.

Po aktualizacji trzeba ponownie zaimportować konto Xtream, aby pobrać filmy i seriale. Poprzednia biblioteka ma tylko TV. Katalogi i dostępne formaty zależą od dostawcy.

## Kolejne funkcje

Strona referencyjna: https://www.formuler.tv/mytvonline3 . Funkcje odniesienia obejmują m.in. wyszukiwanie globalne, przypięte grupy, backup, timeshift, nagrywanie i PiP. Ta aplikacja nie implementuje jeszcze EPG, catch-up, lokalnego timeshiftu, nagrywania, PiP, wielu kont, kontroli rodzicielskiej ani eksportu ustawień. Nie oznaczamy ich w UI jako dostępnych. Wyszukiwanie tej wersji działa w wybranej sekcji.

## Sprawdzenie

CI: testy parserów i katalogów, lint, podpis APK, emulator Android 15: instalacja, otwarcie, aktualizacja, przełączanie katalogów na danych testowych, tryb czujnika i wyjście do pionu. Rzeczywiste odtwarzanie z dostawcą wymaga testu na telefonie; testy nie potwierdzają zachowania konkretnego serwera IPTV.
