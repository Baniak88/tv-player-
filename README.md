# TV Player — Android

Oryginalny **Fire Stick 0.9.16** i jego bezpośredni **port Android 0.9.16**. Obie paczki mają interfejs z Fire Sticka: ekran poziomy, te same ekrany TV, filmów i seriali. [Przywrócenie Fire Sticka i instalacja portu na Androidzie](ANDROID-FIRESTICK.md). Błędne wydanie Android 1.1.8 ze starym ekranem telefonu zostało wycofane.

Wersja TV: **OKTV Player TV 0.9.16-preview**. [Prosta instalacja z terminala Chromebooka na Fire Stick](CHROMEBOOK-FIRESTICK.md). [Pozostałe sposoby instalacji na TV](TV-INSTALL.md).

[Nowy interfejs TV i powrót do bukietu 0.9.6](OKTV-TV-0.9.6.md). [Zmiany katalogu TV 0.9.5](OKTV-TV-0.9.5.md). [Raport testów 0.9.5](TEST-TV-0.9.5.md). [Poprzedni raport testów 0.9.4](TEST-TV-0.9.4.md).

Natywny odtwarzacz IPTV po polsku na telefony i tablety z Androidem 8.0 lub nowszym.

## Funkcje

- M3U/M3U8 z adresu HTTP/HTTPS lub pliku wybranego z telefonu.
- Xtream Codes: adres serwera (z portem, jeśli wymagany), login i hasło; import telewizji na żywo.
- Kategorie, wyszukiwanie, ulubione (przytrzymanie kanału).
- Odtwarzanie przez AndroidX Media3: HLS, DASH i MPEG-TS, z dodatkowymi dekoderami audio FFmpeg.
- Pełny ekran, zmiana orientacji i ponowienie po błędzie.
- Lokalna biblioteka szyfrowana AES-GCM z kluczem Android Keystore. Kopie zapasowe aplikacji wyłączone.

Dostępne są również filmy i seriale z miniaturami dostawcy, podgląd TV, EPG i archiwum. Dostępność transmisji, obrazów, EPG i archiwum zależy od źródła. Aplikacja nie zawiera kanałów ani abonamentu.

## Instalacja

[Port Android 0.9.16](https://github.com/Baniak88/tv-player-/releases/tag/v0.9.16-android): pobierz `OKTV-Player-Android-0.9.16.apk`, otwórz go na telefonie lub tablecie i zatwierdź instalację. Przy prywatnym repozytorium zaloguj się na konto GitHub z dostępem. [Oryginalny Fire Stick 0.9.16](https://github.com/Baniak88/tv-player-/releases/tag/v0.9.16-tv-preview) pozostaje osobno, z niezmienionym APK. [Instrukcja obu wersji i gotowy kod przywrócenia Fire Sticka](ANDROID-FIRESTICK.md).

Paczki preview nie są wydaniami sklepowymi. Android używa pakietu `pl.tvplayer.preview` i zachowuje dotychczasowy klucz preview. Aktualizacja zgodnie podpisanej wersji zachowuje lokalną bibliotekę bez odinstalowywania. Wersja TV to osobny pakiet `pl.tvplayer.television`.

## Budowanie

Wymagane: JDK 17, Gradle 8.9, Android SDK 35, Build Tools 35.0.0. Otwórz projekt w Android Studio albo ustaw `ANDROID_HOME` i uruchom:

```sh
gradle :app:testDebugUnitTest :android:lintDebug :android:assembleDebug :android:assembleDebugAndroidTest
```

Wynik portu: `android/build/outputs/apk/debug/android-debug.apk`. Moduł `:android` używa bezpośrednio manifestu i ekranów `:tv`; różnice to identyfikator paczki, rosnący kod wersji i adapter dotyku. `bash scripts/verify-firestick-base.sh` potwierdza niezmieniony kod oryginalnego Fire Sticka. Historyczny moduł `:app` nie jest publikowany jako ten port.

## Prywatność i połączenia

Dane źródła trafiają bezpośrednio do wskazanego dostawcy. Aplikacja nie ma serwera pośredniczącego ani analityki. Hasła znajdują się w adresach Xtream wymaganych przez protokół, dlatego zapisana biblioteka jest szyfrowana. Preferuj HTTPS; HTTP jest dozwolone dla zgodności z dostawcami, lecz przesyła dane bez szyfrowania. Telefon i telewizor nie synchronizują automatycznie kont ani bibliotek.

## Walidacja

Workflow **Android APK** sprawdza niezmieniony kod Fire Sticka, testy JVM, lint, podpis APK, launcher TV, instalację, ponowną instalację, odtwarzanie po awarii źródła oraz rzeczywistą obsługę dotykiem na emulatorze API 35. Raport i zrzuty ekranów znajdują się w artefakcie `Android-FireStick-test-report`. Artefakty Actions są dostępne przez pięć dni; gotowe APK pozostają w Releases. Testy emulatora nie zastępują sprawdzenia prywatnego źródła na fizycznym urządzeniu.

## Wersja 0.1.1

Usunięto limit importu 30 MB. M3U (URL i pliki), kanały i kategorie Xtream oraz zaszyfrowana biblioteka są przetwarzane strumieniowo. Nie ma stałego limitu rozmiaru źródła; liczba kanałów pozostaje ograniczona dostępną pamięcią telefonu. Testy regresji generują źródła M3U i Xtream większe niż 30 MB. Pobieranie ma limit czasu 5 minut.
