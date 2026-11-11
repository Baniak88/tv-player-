# TV Player — Android

Wersja TV: **OKTV Player TV 0.9.4-preview**. [Prosta instalacja z terminala Chromebooka na Fire Stick](CHROMEBOOK-FIRESTICK.md). [Pozostałe sposoby instalacji na TV](TV-INSTALL.md). [Wygląd interfejsu na bazie OKTV 1.1.6](OKTV-TV-0.9.3.md).

Pierwsza wersja natywnego odtwarzacza IPTV po polsku na telefony z Androidem 8.0 lub nowszym.

## Funkcje

- M3U/M3U8 z adresu HTTP/HTTPS lub pliku wybranego z telefonu.
- Xtream Codes: adres serwera (z portem, jeśli wymagany), login i hasło; import telewizji na żywo.
- Kategorie, wyszukiwanie, ulubione (przytrzymanie kanału).
- Odtwarzanie przez AndroidX Media3: HLS, DASH i MPEG-TS, zależnie od kodeków urządzenia.
- Pełny ekran, zmiana orientacji i ponowienie po błędzie.
- Lokalna biblioteka szyfrowana AES-GCM z kluczem Android Keystore. Kopie zapasowe aplikacji wyłączone.

To początkowa wersja inspirowana funkcjami MyTVOnline+, z własnym interfejsem. Nie zawiera kanałów ani abonamentu. Nie obejmuje jeszcze EPG, VOD, seriali, nagrywania, catch-up ani wielu profili. Metadane tvg-id są odczytywane na potrzeby przyszłego EPG. Nie obsługuje dodatkowych nagłówków z dyrektyw VLC/Kodi w M3U.

## Instalacja

Workflow **Android APK** buduje APK w GitHub Actions. Po udanym przebiegu otwórz **Actions → Android APK → Artifacts → tv-player-debug**, pobierz i rozpakuj ZIP. Przenieś `app-debug.apk` na telefon i zezwól na instalację z używanej przeglądarki lub menedżera plików.

APK debug służy do testów. Nie jest wydaniem sklepowym. Przed publikacją potrzebne są testy na urządzeniu i podpis release. Kolejne kompilacje debug z CI mogą mieć inny klucz i wymagać odinstalowania starej wersji (co usuwa bibliotekę).

## Budowanie

Wymagane: JDK 17, Gradle 8.9, Android SDK 35, Build Tools 35.0.0. Otwórz projekt w Android Studio albo ustaw `ANDROID_HOME` i uruchom:

```sh
gradle testDebugUnitTest lintDebug assembleDebug
```

Wynik: `app/build/outputs/apk/debug/app-debug.apk`.

## Prywatność i połączenia

Dane źródła trafiają bezpośrednio do wskazanego dostawcy. Aplikacja nie ma serwera pośredniczącego ani analityki. Hasła znajdują się w adresach Xtream wymaganych przez protokół, dlatego zapisana biblioteka jest szyfrowana. Preferuj HTTPS; HTTP jest dozwolone dla zgodności z dostawcami, lecz przesyła dane bez szyfrowania. Import zastępuje bieżącą bibliotekę; nie zapisuje formularza logowania i nie odświeża źródeł automatycznie. Bibliotekę można usunąć w menu „Dodaj źródło”.

## Walidacja

Kod wymaga kompilacji i testu z rzeczywistym dostawcą na telefonie. Samo przygotowanie kodu nie potwierdza poprawnego odtwarzania. Workflow uruchamia testy parsera, Android Lint i kompilację przed udostępnieniem APK.

## Wersja 0.1.1

Usunięto limit importu 30 MB. M3U (URL i pliki), kanały i kategorie Xtream oraz zaszyfrowana biblioteka są przetwarzane strumieniowo. Nie ma stałego limitu rozmiaru źródła; liczba kanałów pozostaje ograniczona dostępną pamięcią telefonu. Testy regresji generują źródła M3U i Xtream większe niż 30 MB. Pobieranie ma limit czasu 5 minut.
