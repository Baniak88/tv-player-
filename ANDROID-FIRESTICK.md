# Oryginalny Fire Stick i jego port na Androida

Podstawą obu paczek jest **Fire Stick 0.9.16**, commit `67fa2975945dc4a92400c568ac855596e93e4a98`. Port Androida zachowuje jego motywy, zasoby i styl ekranów, z osobnymi wariantami Androida dostosowanymi do pionu i poziomu oraz poprawionym transportem HTTP. Nie uruchamia starego ekranu telefonu „Co oglądamy?”.

| Urządzenie | Odpowiedni plik | Pakiet |
| --- | --- | --- |
| Fire Stick / Android TV | [Oryginalny APK TV 0.9.16](https://github.com/Baniak88/tv-player-/releases/download/v0.9.16-tv-preview/OKTV-Player-TV-0.9.16-preview.apk) | `pl.tvplayer.television` |
| Telefon / tablet z Androidem | [Port Android 0.9.21](https://github.com/Baniak88/tv-player-/releases/download/v0.9.21-android/OKTV-Player-Android-0.9.21.apk) | `pl.tvplayer.preview` |

Błędne wydanie Android 1.1.8 ze starym ekranem telefonu zostało wycofane. Nowy APK Androida ma `versionCode 38` oraz ten sam podpis preview, więc może je zaktualizować pomimo innej nazwy wersji. Instalacja nie wymaga usuwania poprzedniej aplikacji. Konta i biblioteki obu pakietów są lokalne; przy pierwszym uruchomieniu portu może być potrzebne dodanie źródła w Opcje → Źródło.

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

1. Otwórz [wydanie Android 0.9.21](https://github.com/Baniak88/tv-player-/releases/tag/v0.9.21-android), zaloguj się na GitHubie, jeśli repozytorium jest prywatne, i pobierz `OKTV-Player-Android-0.9.21.apk`.
2. Otwórz APK na urządzeniu i zatwierdź instalację lub aktualizację. Wymagany Android 8.0+.
3. Ekran obraca się automatycznie według czujnika telefonu, również przy systemowej blokadzie obrotu. Zachowuje ciemny interfejs Fire Sticka. W pionie przycisk **Bukiety TV / filmów / seriali** otwiera listę grup z wyszukiwaniem; wybrany bukiet pozostaje po obrocie i po powrocie z oglądania. Menu odtwarzacza jest przewijane i dopasowane do szerokości telefonu. Przyciski, plakaty i kanały wybierasz dotykiem. Strzałki ◀/▶ na pełnym ekranie TV chowają się razem z paskiem opcji po 5 sekundach. Dotknięcie obrazu pokazuje je ponownie. Te dodatki nie są kompilowane do wersji Fire Stick.

Suma APK znajduje się w `SHA256SUMS-Android.txt` w tym samym wydaniu. Filmy, seriale i kanały korzystają z danych dostawcy; brakujące obrazy mają miniatury zastępcze.

## Rozdzielenie kompilacji

- `:tv` to oryginalny Fire Stick 0.9.16. Jego kod i konfiguracja pozostają identyczne z oryginalnym wydaniem.
- `:android` używa wspólnej bazy oraz własnego manifestu i wariantów ekranów/transportu dla telefonu. Zmiany są w module `android/`; oryginalne pliki TV pozostają niezmienione. Szczegóły: [android/README.md](android/README.md).
- `:app` pozostaje historycznym modułem telefonu; nie jest źródłem publikowanego portu.
- `bash scripts/verify-firestick-base.sh` sprawdza brak zmian w `tv/` i współdzielonych źródłach odtwarzacza względem oryginalnego wydania.

```bash
gradle :app:testDebugUnitTest :android:lintDebug :android:assembleDebug :android:assembleDebugAndroidTest
```

APK portu: `android/build/outputs/apk/debug/android-debug.apk`. Workflow Android APK sprawdza również uruchomienie oryginalnego launchera, instalację, ponowną instalację, odtwarzanie po awarii źródła i rzeczywistą obsługę dotykiem na emulatorze API 35. Testy emulatora nie potwierdzają działania prywatnego źródła na fizycznym urządzeniu użytkownika.
