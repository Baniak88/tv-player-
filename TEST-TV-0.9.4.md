# Weryfikacja OKTV Player TV 0.9.4-preview

Pakiet: `pl.tvplayer.television`, versionCode 5, minimum Android API 25 / Fire OS 6. Data sprawdzania: 11 listopada 2026. Testy urządzenia wykorzystują emulator Android 7.1 (API 25), ekran 1280×720, 2 GB RAM. Dostawcy i konta w testach są symulowane przez lokalny MockWebServer; żadne dane abonenta nie są potrzebne.

## Potwierdzone wyniki lokalne

Pełny przebieg **GitHub Actions również zakończył się sukcesem** dla kodu `2e8dc78167e9d9055e7c2bdd6109e3c5a0dbdeee`: [TV platforms #42947872514](https://github.com/Baniak88/tv-player-/actions/runs/42947872514). Zielone są zadania `fire-build`, `fire-test` i `samsung-test`. W `fire-test` wykonano cały zestaw 28 scenariuszy. Zadanie publikacji pozostało pominięte zgodnie z regułą publikowania wyłącznie przez ręczne uruchomienie workflow. Gotowy ZIP jest także artefaktem `television-chromebook` tego przebiegu.

- 94 testy jednostkowe: bez błędów.
- 13 testów instalatora i generatora ZIP: bez błędów.
- 28 unikalnych scenariuszy urządzenia: potwierdzone. 24 scenariusze UI/ustawień/nagrywania/recovery przeszły przed korektą symulacji TCP; trzy testy rzeczywistej transmisji i scenariusz seriali przeszły następnie w oddzielnym, kompletnym przebiegu `OK (4 tests)`. Poprzednia symulacja nieprawidłowo używała `DISCONNECT_AT_START` z dynamicznym dispatcherem; poprawiony test zamyka socket przed nagłówkami, a przerwa w seansie następuje po faktycznym starcie odtwarzania.
- Build APK i obu lintów: sukces. APK ma poprawny podpis v2, zgodny z wydaniem 0.9.3.
- Gotowy ZIP uruchomiono przez TCP ADB na emulatorze: aktualizacja **0.9.3 / code 4 → 0.9.4 / code 5** zakończyła się sukcesem. SHA-256 plików zaszyfrowanego konta, ulubionych i wskaźnika katalogu pozostały identyczne. Ekran konta po aktualizacji prawidłowo odczytał zapisany testowy serwer, potwierdzając działanie odszyfrowania.

Łącznie: **135 testów/scenariuszy oraz osobna próba aktualizacji przez ADB**. Instalator sprawdzono na hoście Linux; nie był uruchamiany na fizycznym Chromebooku ani Fire Sticku. Przygotowanie emulatora do próby aktualizacji obejmowało kontrolowany downgrade do 0.9.3; dostarczony skrypt użytkownika nigdy nie wykonuje downgrade ani odinstalowania.

SHA-256 APK dostarczonego w paczce:

```text
53bb95049e66196d1321736721f9a74c421ed1fa3ece9f2660b399e3c87832c3
```

## Zakres

| Obszar | Sprawdzane zachowanie |
| --- | --- |
| Główna, konto, ustawienia | Sterowanie pilotem, kafelki, dialog konta, informacje o wersji, anulowanie i potwierdzenie wyjścia. |
| Źródła | Formularz i QR telefonu w LAN, ręczne M3U i Xtream, walidacja pól, zapis szyfrowany, pobranie TV przed filmami, zachowanie biblioteki po błędzie odświeżania i restarcie. |
| Katalog | TV, filmy, seriale; bukiety, wszystkie cztery tryby sortowania, wyszukiwanie i ulubione. |
| Filmy i seriale | Szczegóły filmu, wznowienie i start od początku, zachowanie pozostałych ulubionych, wybór sezonu i odcinka oraz przekazanie oryginalnego adresu do odtwarzacza. |
| Audio i napisy | Rzeczywisty plik MKV: dwa strumienie AAC i dwa strumienie SubRip; wybór polskiego/angielskiego audio, dekodowanie obu wersji napisów i ich wyłączenie. |
| Odtwarzacz | Pilot, krajobraz, cofnięcie o 30 s, początek filmu, zmiana bufora z zachowaniem pozycji, pauzy, audio i wyłączonych napisów; obsługa braku PiP na API 25. |
| EPG i archiwum | Lista i szczegóły audycji z API, adres timeshift, zachowanie trybu TV oraz faktyczne osiągnięcie gotowości odtwarzania archiwum HLS. Własny XMLTV: błędny URL z hasłem, preset, anulowanie, zapis i usunięcie. |
| Nagrywanie | Zapis dokładnych bajtów MPEG-TS, błąd HTTP 403, zatrzymanie oczekującego żądania, zgodny User-Agent oraz bezpieczna obsługa braku/zablokowania menedżera dokumentów. |
| Niestabilna sieć | HTTP 503, zerwane połączenie przed startem oraz zerwanie odpowiedzi po rozpoczęciu odtwarzania; wznowienie przez ten sam Media3. Osobno anulowanie retry przy pauzie/zamknięciu i ochrona pasma odtwarzania przed pobieraniem XMLTV w tle. |
| Instalator | Suma APK, interaktywne IP, walidacja adresu, zgoda ADB, stary system, drugi klient ADB, konflikt podpisu, błąd `pm` także z kodem procesu 0, brak miejsca i ręczne uruchomienie po udanej instalacji. |

## Poprawki w tej wersji

- Zmiana bufora zachowuje ręczny wybór audio i napisów.
- Archiwum uruchomione z odtwarzacza zachowuje tryb telewizyjny.
- Brak systemowego wyboru pliku nie zamyka aplikacji; pojawia się czytelny komunikat.
- Nagrywanie używa tego samego identyfikatora HTTP co odtwarzanie.
- Przyciski Play/Pause/Stop są przechwytywane przed przekazaniem do systemu; obsłużony przycisk nie uruchamia innej aplikacji muzycznej. Pauza działa także podczas buforowania i po błędzie źródła.
- Gotowy ZIP z APK, SHA-256 i instalatorem ADB dla terminala Linux na Chromebooku; aktualizacja używa `pm install -r` i nie usuwa aplikacji ani jej danych.

## Granice weryfikacji

Nie oznacza to sprawdzenia wszystkich kombinacji opcji, kodeków i modeli urządzeń. Nie było dostępu do fizycznego Fire Sticka, Chromebooka użytkownika, konta dostawcy, jego archiwum, rzeczywistego Wi-Fi ani wielogodzinnego seansu. Nie mierzono płynności 4K/HDR na Fire TV ani działania PiP na modelach, które je oferują. Nie czekano na limity nagrywania 30/60/120 minut. W testach sprawdzana jest m.in. odmowa otwarcia menedżera plików; dostępność takiej aplikacji na konkretnym Fire OS pozostaje zależna od urządzenia.

Testy sieci używają prawdziwego Media3 i lokalnych połączeń TCP, ale awarie są wymuszane przez serwer testowy. Większy bufor i retry pomagają przy krótkich przerwach; nie zastępują niedziałającego serwera ani brakującego pasma. Nagrywanie otwiera dodatkowe połączenie, więc pozostaje zależne od limitu dostawcy.

Pierwsze lokalne próby przerwał proces systemowy emulatora przy 1 GB RAM oraz automatyczny restart środowiska. Przywrócona kopia dysku AVD zatrzymała się następnie w trybie odszyfrowania bez LAN. Logi tych prób zachowano osobno; nie są zaliczane jako pełne udane przebiegi. Końcowe środowisko to czysty AVD z 2 GB RAM i wyłączonym szyfrowaniem wyłącznie dysku testowego emulatora. Szyfrowanie danych aplikacji przez Android Keystore pozostaje weryfikowane. Testowy helper zamyka wyłącznie komunikat awarii procesu **system**, nigdy komunikat awarii aplikacji.

## Odtworzenie sprawdzeń

```bash
gradle :app:testDebugUnitTest :app:lintDebug :tv:lintDebug :tv:assembleDebug :tv:assembleDebugAndroidTest --no-daemon
python3 -m unittest discover -s scripts/tests -v
bash -n scripts/install-firestick.sh scripts/smoke-tv.sh
python3 scripts/package-firestick.py
```

Testy urządzenia i zrzuty uruchamia workflow `TV platforms` przez `scripts/smoke-tv.sh`. Instrukcja użytkownika: [CHROMEBOOK-FIRESTICK.md](CHROMEBOOK-FIRESTICK.md).
