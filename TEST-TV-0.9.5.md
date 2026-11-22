# Weryfikacja OKTV Player TV 0.9.5-preview

Data: 15 listopada 2026. Pakiet `pl.tvplayer.television`, versionCode 6, minimum API 25. Zmiany odpowiadają zgłoszeniu z fizycznego Fire Sticka: kanały UK Movies trafiały do filmów, logotypy były rozciągnięte, a katalog był trudny w obsłudze pilotem. Użytkownik potwierdził źródło M3U. Testy wykorzystują własną ofertę demonstracyjną i lokalny MockWebServer, bez danych abonenta.

## Pełny wynik GitHub Actions

[TV platforms #43729126105](https://github.com/Baniak88/tv-player-/actions/runs/43729126105) zakończył się sukcesem dla kodu `d74c7bec217c9a529113e9020545a49a79ab2ee6`. Zadania `fire-build`, `fire-test` i `samsung-test` są zielone. Wykonano **98 testów jednostkowych, 13 testów instalatora i 33 scenariusze urządzenia — łącznie 144 sprawdzenia TV**. Wszystkie zakończyły się sukcesem, w tym oba rozmiary katalogu, migracja M3U, audio PCM, pasek EPG oraz przewijanie przyciskami pilota. Publikacja wydania pozostała pominięta zgodnie z ręcznym trybem publikowania; gotowy ZIP jest artefaktem `television-chromebook`.

## Zakres poprawki

| Obszar | Sprawdzane zachowanie |
| --- | --- |
| Klasyfikacja M3U | Kategoria `UK\| MOVIES`, HEVC lub 24/7 nie przesądza o VOD. Adresy live, rozszerzenia plików, EPG i jawne typy mają właściwy priorytet. |
| Migracja | Zapisany katalog v2 przenosi filmowe kanały live do TV. Zachowuje konto M3U, oryginalne URL, kategorię, EPG, ulubione oraz prawdziwe filmy MP4 i film HLS jawnie oznaczony przez API. |
| Czytelność | 960×540 i 1280×720, co najmniej pięć pełnych wierszy live, małe proporcjonalne logotypy, pięć/sześć kolumn filmów, pełna nazwa pod katalogiem, przełączanie lista/plakaty. |
| Pilot | Prawo przechodzi z kategorii do zawartości, Wstecz wraca do kategorii, kolejny Wstecz do strony głównej. Obrys wskazuje aktualny fokus. |
| Seriale M3U | Odcinki `Sxx Exx` tworzą jeden folder tytułu w danej kategorii, sortowane liczbowo. Test 20 000 odcinków daje 1 000 folderów. API seriali pozostaje zachowane. |
| Sezony | Po otwarciu folderu fokus trafia na wybór sezonów. Strzałki filtrują sezon 2, Play przekazuje oryginalny URL jego odcinka do odtwarzacza w trybie TV, a Wstecz przywraca katalog seriali. |
| Pasek TV | Nazwa kanału, bieżąca i następna audycja, procent postępu, komunikat przy braku EPG, obsługa OK/Info/Wstecz. Natywny zrzut `tv095-live-info.png`. |
| Dźwięk PCM | Próbka sześciokanałowa z sygnałem wyłącznie w kanale centralnym po miksowaniu ma oba kanały stereo. Materiał z AC-3 i AAC: po zmianie trybu faktycznie dekodowane są bufory AAC, pauza i pozycja pozostają zachowane, a tryb działa po ponownym otwarciu odtwarzacza. |

## Wyniki lokalne

- 98 testów jednostkowych: zero błędów, awarii i pominięć.
- 13 testów instalatora i generatora ZIP: sukces.
- Budowanie aplikacji i APK testowego oraz lint aplikacji telefonicznej i TV: sukces.
- Trzy nowe scenariusze katalogu przeszły na emulatorze API 25 z 2 GB RAM. Dodatkowy przebieg potwierdził fokus kategorii sezonów, filtr sezonu 2 i oryginalny adres odcinka.
- Dwa scenariusze paska i audio przeszły lokalnie: `OK (2 tests)`. Obejmują również powrót fokusu okna po schowaniu paska oraz rzeczywiste bufory dekodowanego AAC. Pełny workflow potwierdził końcowy kod, w tym przewijanie przyciskami pilota bez utraty fokusu. Kod zawiera też ochronę paska w PiP; sprzętowe PiP pozostaje poza zakresem emulatora API 25.
- Dwa dotychczasowe scenariusze opcji odtwarzania również przeszły lokalnie: `OK (2 tests)`. Potwierdzono polskie/angielskie audio i napisy, wyłączenie napisów, przewijanie, restart filmu, zachowanie ustawień po zmianie bufora oraz archiwum HLS z trybem TV.
- Podpis APK jest zgodny z 0.9.4: certyfikat SHA-256 `f5e783f412e9b781f23ea6dc33f961c0e2561f002ffc7c28c855f043ad0ae7d2`. Instalacja używa aktualizacji `-r`, bez odinstalowania.

SHA-256 lokalnego APK z końcowego kodu `d74c7bec217c9a529113e9020545a49a79ab2ee6`:

```text
d3a24efd3399eaf738136f9b50d93c7f017e44bc31d58f5b6980f2813fb41704
```

Pobrano również gotowy artefakt `television-chromebook` z przebiegu GitHub Actions 43729126105 i zweryfikowano sumę całego artefaktu, CRC wewnętrznego ZIP, manifest `SHA256SUMS`, wersję oraz podpis APK. SHA-256 APK z CI (inny build tego samego kodu):

```text
00cf38b7edb81bf88ce3699f8c0320fb362f2b55d4807d4e489ffa0934825d5c
```

## Odtworzenie

```bash
gradle :app:testDebugUnitTest :app:lintDebug :tv:lintDebug :tv:assembleDebug :tv:assembleDebugAndroidTest --no-daemon
python3 -m unittest discover -s scripts/tests -v
bash -n scripts/install-firestick.sh scripts/smoke-tv.sh
python3 scripts/package-firestick.py
```

Workflow `TV platforms` wykonuje pełny zestaw 33 scenariuszy urządzenia, w tym dotychczasowe testy źródeł, telefonu w LAN, EPG, odtwarzania, recovery, ustawień, audio, napisów i nagrywania. Raporty i natywne zrzuty demonstracyjne są artefaktem `television-test-report`; ZIP instalacyjny jest w `television-chromebook`.

## Ograniczenia

Testy odbywają się na emulatorze, nie na fizycznym Fire Sticku użytkownika. Nie ma dostępu do jego serwera, konta ani Wi-Fi. Nie przeprowadzono wielogodzinnego seansu ani pomiaru płynności 4K/HDR na sprzęcie. PiP na API 25 sprawdzany jest jako bezpiecznie niedostępny; nie zastępuje to sprawdzenia miniokna na obsługującym je urządzeniu. Wstępne lokalne testy UI blokował komunikat awarii systemowego Pixel Launchera po zmianie rozdzielczości emulatora; po wyłączeniu tego launchera testy przeprowadzono ponownie. Nie są maskowane błędy aplikacji.

Testy wymiarów czytają fizyczne osie ekranu przed użyciem `wm size`: lokalny AVD ma naturalny ekran poziomy, a domyślny AVD CI pionowy. Czekają na rzeczywisty rozmiar okna i zasobów przy gęstości 160, następnie wymagają odpowiednio pięciu i sześciu kolumn. Wcześniejsza wersja przygotowania testu obracała zadane wymiary na urządzeniu o pionowych osiach.

M3U bez jednoznacznego typu i struktury URL nadal wymaga heurystyk. Sama nazwa kategorii `Movies` nie przenosi już kanału do VOD. Recovery pomaga przy krótkich przerwach, lecz nie zapewnia ciągłości, gdy serwer jest niedostępny lub łącze zbyt wolne. Zgłoszonego Warner Channel nie odtwarzano z konta użytkownika. Tryb PCM wymaga dostępnej ścieżki i dekodera obsługiwanego przez urządzenie; nie dołącza biblioteki FFmpeg ani brakującego dekodera Dolby. Zliczanie rzeczywiście dekodowanych buforów na emulatorze nie jest pomiarem słyszalności z głośników telewizora.
