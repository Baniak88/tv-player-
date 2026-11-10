# TV 0.9.2 Preview: telefon, płynność i odzyskiwanie połączenia

Zmiany względem tagu `v0.9.1-tv-preview` (`66e3434`):

- Dodawanie konta Xtream lub linku M3U przez przeglądarkę telefonu w LAN. Telewizor pokazuje QR, adres i jednorazowy PIN. Serwer jest aktywny tylko przy otwartym oknie, przez maksymalnie 5 minut i do jednego zgłoszenia. Żadne zapisane konto nie jest udostępniane przez formularz. Dane zapisuje dotychczasowy szyfrowany magazyn TV.
- Jeden odtwarzacz ponawia chwilowe błędy sieci z odstępami 1, 2, 4, 8, 8, 8 sekund. TV wraca do bieżącej transmisji, VOD zachowuje pozycję. Pauza, zamknięcie ekranu i przekazanie podglądu anulują zaplanowaną próbę. Budżet odnawia się po 30 sekundach stabilnego odtwarzania lub ręcznym ponowieniu. Błędy uprawnień, kodeków i limitu konta HTTP 458 nie uruchamiają dodatkowych prób.
- Wspólne ustawienia HTTP i bufora dla TV, telefonu i VOD; tryb słabszego internetu dostępny także na TV. Duże pobrania XMLTV w tle są wstrzymywane podczas odtwarzania. EPG z pamięci i jawne otwarcie pełnego programu nadal działają.
- Wspólny limit czasu obejmuje żądanie, zmianę User-Agent i pojedyncze ponowienie pobierania. Przerwane pobranie jest parsowane od początku po ponowieniu, bez dopisywania niepełnych wyników. Respektowane są dłuższe `Retry-After`.
- Recykling widoków i plakatów TV; EPG wyłącznie dla widocznych kanałów po zatrzymaniu przewijania. Odświeżenie tego samego konta zachowuje zapisane VOD i seriale. Katalog z pamięci jest dostępny podczas aktualizacji i po błędzie dostawcy.
- Strumieniowy parser katalogu używa przygotowanych wzorców i adresów. Obserwator EIT kopiuje całe fragmenty zamiast każdego bajtu osobno.

## Sprawdzenie

Wymagane JDK 17, Gradle 8.9 i Android SDK 35:

```sh
gradle :app:testDebugUnitTest :app:lintDebug :tv:lintDebug :app:assembleDebug :tv:assembleDebug :tv:assembleDebugAndroidTest :app:assembleDebugAndroidTest
gradle :tv:connectedDebugAndroidTest
```

Testy jednostkowe obejmują duże źródła ponad 30 MB, błędy 503, limit 458, `Retry-After`, zerwane pobranie, wspólny limit czasu fallbacku EPG, parowanie LAN i granice pakietów EIT. Testy urządzeniowe obejmują anulowanie ponowienia przy pauzie/zamknięciu, pozycję VOD, blokadę pobrań XMLTV w tle i wznowienie prawdziwego Media3 po HTTP 503.

Pomiar JVM 17 na tym samym środowisku, mediana 5 prób po 3 rozgrzewkach: parsowanie 20 000 kanałów 176 → 80 ms; obserwacja 188 MiB pakietów TS bez tabel EIT 826 → 18 ms. To pomiary samego przetwarzania, bez sieci i dekodowania obrazu.

Szczegółowy wynik wykonanych testów znajduje się w dostarczonym raporcie. Nadal potrzebny jest dłuższy test z rzeczywistym dostawcą na urządzeniu użytkownika; brak danych konta nie pozwala potwierdzić jego limitów i stabilności serwera.
