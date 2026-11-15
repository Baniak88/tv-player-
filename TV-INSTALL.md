# OKTV Player TV 0.9.5 Preview — instalacja

**Chromebook:** pobierz `OKTV-FireStick-Chromebook.zip` i skorzystaj z [instrukcji terminala Linux](CHROMEBOOK-FIRESTICK.md). Paczka zawiera APK i instalator ADB sprawdzający adres urządzenia, sumę APK i wynik aktualizacji.

## Fire TV Stick 4K

Użyj przygotowanego pliku **OKTV-Player-TV-0.9.5-preview.apk** lub paczki Chromebooka. Obsługiwane są Fire OS 6+ i Android TV 7.1+, w tym pierwsza generacja Sticka 4K. APK nie działa na Vega OS / 4K Select.

1. Pobierz APK w przeglądarce zalogowanej do GitHuba. Repozytorium jest prywatne — otwarcie linku w niezalogowanym Downloaderze może pokazać 404.
2. Prześlij plik na Fire TV. Włącz instalowanie aplikacji z używanego instalatora, następnie otwórz APK i zainstaluj TV Player.
3. Uruchom TV Player i wybierz **Zmień źródło → Dodaj przez telefon**.
4. Podłącz telefon i TV do tej samej sieci domowej. Zeskanuj QR z TV albo przepisz wyświetlony adres do przeglądarki telefonu.
5. Wpisz sześciocyfrowy kod z TV i dane Xtream lub link M3U. Naciśnij **Połącz telewizor**. TV rozpocznie pobieranie kanałów.

Formularz telefonu nie wymaga instalowania dodatkowej aplikacji ani konta pośrednika. Działa przez lokalne HTTP, dlatego używaj zaufanej sieci. Zamyka się po odebraniu danych, zamknięciu okna na TV lub po 5 minutach. Pięć błędnych kodów blokuje bieżącą sesję; otwórz ją ponownie na TV. Sieć gościnna z izolacją urządzeń może uniemożliwić połączenie. Nadal można wpisać źródło pilotem.

Z komputera z Android platform-tools można użyć:

```sh
adb connect ADRES_IP_FIRE_TV:5555
adb -s ADRES_IP_FIRE_TV:5555 install -r OKTV-Player-TV-0.9.5-preview.apk
```

Pilot: strzałki i OK wybierają pozycję. Menu lub przytrzymanie OK otwiera EPG i ulubione. Wstecz wraca. Odtwarzacz pozostaje poziomy i reaguje na przyciski multimedialne. TV pobiera się jako pierwsze; duże katalogi filmów i seriali dopiero po wybraniu sekcji. Wersja TV ma osobną, szyfrowaną bibliotekę, niezależną od aplikacji telefonu.

## Samsung Tizen — w tym telewizory z 2024 r.

Samsung Tizen nie instaluje APK. Osobna aplikacja jest w katalogu `samsung/`. Korzysta z Samsung AVPlay i pilota. Obsługuje Xtream/M3U, TV, filmy, seriale i odcinki, bukiety dostawcy, sortowanie, wyszukiwanie, ulubione oraz EPG z API konta. Docelowy rocznik to 2024; minimalna wersja pakietu to Tizen 4.0.

**TV-Player-Samsung-0.9.0-unsigned.wgt jest niepodpisanym pakietem deweloperskim. Nie jest jeszcze gotowy do instalacji na TV.** Potrzebny jest profil certyfikatu Samsung z certyfikatem autora i dystrybutora dopuszczającym konkretny telewizor. W czasie przygotowania nie były dostępne certyfikaty ani DUID użytkownika. Nie należy zmieniać nazwy APK lub źródłowego ZIP na WGT.

### Podpisanie i instalacja z komputera

1. Zainstaluj Tizen Studio, Samsung TV Extension i Samsung Certificate Extension.
2. Na TV otwórz Apps, wpisz **12345**, włącz Developer Mode, wpisz adres IP komputera i uruchom TV ponownie zgodnie z komunikatem Samsunga.
3. Połącz TV w Device Manager. W Certificate Manager utwórz profil **Samsung TV**, zaloguj się na własne konto Samsung i dodaj DUID telewizora. W Device Manager zezwól urządzeniu na instalowanie aplikacji.
4. Pobierz kod tego wydania na komputer. Połącz TV poleceniem `sdb connect ADRES_IP_TV:26101`; `sdb devices` pokaże urządzenie.
5. W katalogu repozytorium uruchom:

```sh
bash scripts/install-samsung.sh NAZWA_PROFILU_SAMSUNG NAZWA_URZADZENIA
```

Skrypt buduje projekt, podpisuje go lokalnym profilem, sprawdza obecność obu podpisów i uruchamia instalację przez Tizen CLI. Kopia podpisanego pakietu trafia do `dist/TV-Player-Samsung-0.9.0-signed.wgt`. Certyfikaty i ich hasła pozostają na komputerze użytkownika. Skrypt nie publikuje aplikacji w sklepie Samsunga.

Alternatywnie zaimportuj katalog `samsung` w Tizen Studio, wybierz profil certyfikatu Samsung i uruchom projekt jako Tizen Web Application na podłączonym TV.

Dokumentacja Samsunga: [połączenie z TV](https://developer.samsung.com/smarttv/develop/getting-started/using-sdk/tv-device.html), [certyfikaty i DUID](https://developer.samsung.com/smarttv/develop/getting-started/setting-up-sdk/creating-certificates.html), [Tizen CLI](https://developer.samsung.com/smarttv/develop/getting-started/using-sdk/command-line-interface.html).

### Co sprawdzono i co wymaga fizycznego TV

- Testy przeglądarkowe sprawdzają pilot, biblioteki, dopasowanie EPG i wywołania AVPlay przez atrapę API. Nie potwierdzają dekodowania obrazu na Samsungu.
- Import dużej listy i przetwarzanie JSON działają w workerach; interfejs wyświetla małą liczbę pozycji na stronie. Test obejmuje M3U ponad 30 MiB i 60 000 pozycji.
- Zapamiętane konto, katalogi i ulubione są szyfrowane AES-GCM w IndexedDB, z nieeksportowalnym kluczem WebCrypto. Jeśli model nie udostępnia tej pamięci, źródło działa w bieżącej sesji, a aplikacja informuje o problemie zapisu.
- Samsung pobiera EPG z API dostawcy. Nie deklarujemy odczytu DVB EIT z bajtów wewnątrz AVPlay. Obserwator EIT z Android/Fire TV nie jest dostępny w tej wersji Tizen.
- Podpisanie, instalacja i rzeczywiste kodeki wymagają sprawdzenia na TV z właściwym certyfikatem. Nie zostały jeszcze potwierdzone na urządzeniu użytkownika.
- Aplikacja nie zawiera kanałów ani list. Odtwarzanie i EPG zależą od własnego źródła oraz urządzenia.

## Sprawdzanie projektu

```sh
gradle :app:testDebugUnitTest :tv:lintDebug :tv:assembleDebug :tv:assembleDebugAndroidTest
node samsung/tests/core.test.cjs
node samsung/tests/browser.cjs
python3 scripts/package-samsung.py
```

Test przeglądarkowy wymaga Playwright i Chromium. Workflow TV sprawdza podpis APK, instalację i pilot na emulatorze Android API 25. Testy fizycznego Fire TV i Samsunga wymagają dostępu do urządzeń.
