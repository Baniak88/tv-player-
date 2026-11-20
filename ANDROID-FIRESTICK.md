# OKTV Player — Android i Fire Stick

Obie aplikacje powstają z tej samej bazy kodu. Android **1.1.8** zawiera aktualny kod wydania TV **0.9.16** (commit `67fa2975945dc4a92400c568ac855596e93e4a98`). Wygląd i sterowanie są dobrane do urządzenia.

| Urządzenie | Wydanie | Paczka | Wymagany system |
| --- | --- | --- | --- |
| Telefon / tablet | [Android 1.1.8](https://github.com/Baniak88/tv-player-/releases/tag/v1.1.8) | `OKTV-Player-1.1.8.apk` | Android 8.0+ |
| Fire Stick / Android TV | [TV 0.9.16](https://github.com/Baniak88/tv-player-/releases/tag/v0.9.16-tv-preview) | `OKTV-Player-TV-0.9.16-preview.apk` | Fire OS 6+ / Android TV 7.1+ |

## Telefon i tablet

1. Otwórz [wydanie Android 1.1.8](https://github.com/Baniak88/tv-player-/releases/tag/v1.1.8) w przeglądarce na urządzeniu. Przy prywatnym repozytorium zaloguj się na konto z dostępem.
2. W sekcji **Assets** pobierz `OKTV-Player-1.1.8.apk` i otwórz plik.
3. Jeśli Android o to poprosi, zezwól tej przeglądarce na instalowanie aplikacji. Wybierz **Zainstaluj / Aktualizuj**.
4. Otwórz OKTV Player, dodaj swoje źródło M3U lub konto Xtream. Aplikacja nie zawiera własnej oferty.

Telefon używa przycisków dotykowych i obsługuje pion oraz poziom. Dostępne są TV, filmy i seriale z obrazami udostępnianymi przez dostawcę, ulubione, podgląd oraz odtwarzacz z dodatkowymi dekoderami audio. Brak obrazu w danych dostawcy oznacza zastępczą miniaturę.

Android 1.1.8 zachowuje identyfikator `pl.tvplayer.preview` i dotychczasowy klucz preview. Aktualizacja zgodnie podpisanej starszej wersji zachowuje bibliotekę; nie trzeba jej odinstalowywać. Dane i konto nie synchronizują się automatycznie między telefonem a telewizorem.

Suma pobranego APK znajduje się w pliku `SHA256SUMS-Android.txt` w tym samym wydaniu. W terminalu, obok obu plików, można sprawdzić `sha256sum -c SHA256SUMS-Android.txt`.

## Fire Stick z terminala Chromebooka

Wklej cały blok w terminalu Linux. Konto GitHub jest potrzebne, jeżeli repozytorium jest prywatne. Instalator zapyta o IP Fire Sticka i użyje połączenia ADB z tym urządzeniem.

```bash
(
set -e
sudo apt update
sudo apt install -y gh adb unzip
gh auth status --hostname github.com >/dev/null 2>&1 ||
  gh auth login --hostname github.com --git-protocol https --web
oktv_dir=$(mktemp -d)
gh release download v0.9.16-tv-preview --repo Baniak88/tv-player- \
  --pattern OKTV-FireStick-Chromebook.zip --dir "$oktv_dir"
unzip -q "$oktv_dir/OKTV-FireStick-Chromebook.zip" -d "$oktv_dir/aplikacja"
bash "$oktv_dir/aplikacja/install-firestick.sh"
)
```

Szczegóły ADB i instalacji: [CHROMEBOOK-FIRESTICK.md](CHROMEBOOK-FIRESTICK.md). Paczka TV to osobny pakiet `pl.tvplayer.television`, z interfejsem do pilota. Nie działa na Vega OS ani Fire OS 5.

## Budowanie i sprawdzanie

Wymagane: JDK 17, Gradle 8.9, SDK 35 i Build Tools 35.0.0.

```bash
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
gradle :tv:lintDebug :tv:assembleDebug :tv:assembleDebugAndroidTest
```

APK telefonu: `app/build/outputs/apk/debug/app-debug.apk`. APK telewizora: `tv/build/outputs/apk/debug/tv-debug.apk`. Workflow **Android APK** uruchamia także instalację, ponowną instalację i natywne testy na emulatorze Android API 35. Wyniki i zrzuty ekranów są w artefakcie `Android-install-report`.

Próbki testowe i emulator nie potwierdzają dźwięku ani stabilności prywatnego kanału Warner na fizycznym Fire Sticku. EPG, dostępność transmisji i kompletność miniatur zależą również od dostawcy.
