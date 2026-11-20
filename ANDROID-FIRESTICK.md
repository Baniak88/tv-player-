# Oryginalny Fire Stick i jego port na Androida

Podstawą obu paczek jest **Fire Stick 0.9.16**, commit `67fa2975945dc4a92400c568ac855596e93e4a98`. Port Androida korzysta bezpośrednio z jego manifestu, ekranów, motywów, zasobów i odtwarzacza. To ten sam interfejs poziomy z górną nawigacją oraz ekranami TV, filmów i seriali. Nie uruchamia starego ekranu telefonu „Co oglądamy?”.

| Urządzenie | Odpowiedni plik | Pakiet |
| --- | --- | --- |
| Fire Stick / Android TV | [Oryginalny APK TV 0.9.16](https://github.com/Baniak88/tv-player-/releases/download/v0.9.16-tv-preview/OKTV-Player-TV-0.9.16-preview.apk) | `pl.tvplayer.television` |
| Telefon / tablet z Androidem | [Port Android 0.9.16](https://github.com/Baniak88/tv-player-/releases/download/v0.9.16-android/OKTV-Player-Android-0.9.16.apk) | `pl.tvplayer.preview` |

Błędne wydanie Android 1.1.8 ze starym ekranem telefonu zostało wycofane. Nowy APK Androida ma `versionCode 33` oraz ten sam podpis preview, więc może je zaktualizować pomimo innej nazwy wersji. Instalacja nie wymaga usuwania poprzedniej aplikacji. Konta i biblioteki obu pakietów są lokalne; przy pierwszym uruchomieniu portu może być potrzebne dodanie źródła w Opcje → Źródło.

## Przywrócenie oryginalnego Fire Sticka z Chromebooka

Poniższy blok pobiera dokładnie oryginalną paczkę **0.9.16** i otwiera pakiet TV. Na Fire Sticku włącz debugowanie ADB, a w instalatorze wpisz jego IP. Przy prywatnym repozytorium potrzebne jest konto GitHub z dostępem.

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

To instalacja tego samego APK przez `install -r` z zachowaniem danych przy zgodnym podpisie. Instalator wybiera pakiet TV; nie wybiera starszej aplikacji telefonu, jeżeli obie zostały zainstalowane na Fire Sticku. Wymagany Fire OS 6+ / Android TV 7.1+. Vega OS i Fire OS 5 nie są obsługiwane.

Oryginalny APK Fire Sticka ma SHA-256 `6e75e4949b117e135b35b3e5ac59133d766ebc364c5cb90cb0418634043e9e39`. Pliki wydania TV nie zostały przebudowane ani podmienione.

## Instalacja portu na telefonie lub tablecie

1. Otwórz [wydanie Android 0.9.16](https://github.com/Baniak88/tv-player-/releases/tag/v0.9.16-android), zaloguj się na GitHubie, jeśli repozytorium jest prywatne, i pobierz `OKTV-Player-Android-0.9.16.apk`.
2. Otwórz APK na urządzeniu i zatwierdź instalację lub aktualizację. Wymagany Android 8.0+.
3. Aplikacja otwiera się poziomo z interfejsem Fire Sticka. Przyciski, plakaty i kanały wybierasz dotykiem. W osobnym module Androida dodano obsługę pojedynczego dotknięcia oraz dotykowe odpowiedniki przycisków pilota podczas oglądania TV: poprzedni/następny kanał, EPG, opcje, informacje i powrót. Pasek chowa się po kilku sekundach; dotknięcie obrazu pokazuje go ponownie. Te dodatki nie są kompilowane do wersji Fire Stick.

Suma APK znajduje się w `SHA256SUMS-Android.txt` w tym samym wydaniu. Filmy, seriale i kanały korzystają z danych dostawcy; brakujące obrazy mają miniatury zastępcze.

## Rozdzielenie kompilacji

- `:tv` to oryginalny Fire Stick 0.9.16. Jego kod i konfiguracja pozostają identyczne z oryginalnym wydaniem.
- `:android` kompiluje bezpośrednio manifest i źródła `tv/` oraz współdzielony odtwarzacz `app/src/main`. Ma osobny identyfikator i adapter dotyku. Nie kopiuje i nie przerabia ekranów TV.
- `:app` pozostaje historycznym modułem telefonu; nie jest źródłem publikowanego portu.
- `bash scripts/verify-firestick-base.sh` sprawdza brak zmian w `tv/` i współdzielonych źródłach odtwarzacza względem oryginalnego wydania.

```bash
gradle :app:testDebugUnitTest :android:lintDebug :android:assembleDebug :android:assembleDebugAndroidTest
```

APK portu: `android/build/outputs/apk/debug/android-debug.apk`. Workflow Android APK sprawdza również uruchomienie oryginalnego launchera, instalację, ponowną instalację, odtwarzanie po awarii źródła i rzeczywistą obsługę dotykiem na emulatorze API 35. Testy emulatora nie potwierdzają działania prywatnego źródła na fizycznym urządzeniu użytkownika.
