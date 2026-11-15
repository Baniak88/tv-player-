# OKTV Player TV 0.9.7-preview — dźwięk

Wersja Android 1.1.6 i dotychczasowa wersja TV używały dekoderów systemowych Media3 1.5.1. Telefon może udostępniać kodeki, których nie ma w Fire OS. Sama zmiana wyjścia na PCM nie dodawała brakującego dekodera.

Wersja TV ma teraz oficjalny moduł FFmpeg Media3 1.5.1, budowany ze źródeł z FFmpeg 6.1.6. W trybie Stereo PCM preferuje on programowy dekoder audio. Dekodowanie obrazu pozostaje sprzętowe. Ten sam tor dźwięku działa w podglądzie i na pełnym ekranie; przekazywanie odtwarzacza zachowuje dekoder i bufor.

- Domyślne wyjście bez wcześniejszego wyboru to Stereo PCM. Jawnie zapisany wybór HDMI/Dolby pozostaje zachowany.
- Menu → Zgodność dźwięku → Stereo PCM uruchamia zgodny tor audio również na urządzeniach bez systemowego dekodera AC3, E-AC3, MPEG Layer II lub DTS.
- Dźwięk wielokanałowy jest miksowany do dwóch kanałów, z uwzględnieniem kanału centralnego z dialogami.
- Biblioteki zawierają wersje ARM 32-bit, ARM 64-bit oraz x86/x86_64 do testów. Build odmawia utworzenia paczki bez którejkolwiek wymaganej biblioteki.
- Licencje dekoderów są dostępne w Opcje → O aplikacji → Licencje audio. Kod źródłowy i instrukcja powtarzalnego buildu: [decoder-ffmpeg/README.md](decoder-ffmpeg/README.md).

Nowe testy używają własnych próbek AAC, AC3, E-AC3, MP2 i DTS. Sprawdzają rzeczywisty renderer FFmpeg, przetworzone bufory oraz dwukanałowy Android AudioTrack, także przy przejściu z podglądu na pełny ekran. Wynik automatycznych kontroli znajduje się w Actions/PR tej wersji. Nie zastępuje to sprawdzenia prywatnego kanału Warner na fizycznym urządzeniu użytkownika.

Pakiet i podpis preview pozostają te same: `pl.tvplayer.television`, versionCode 8. Instalacja: [CHROMEBOOK-FIRESTICK.md](CHROMEBOOK-FIRESTICK.md).
