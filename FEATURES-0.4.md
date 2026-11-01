# TV Player Preview 0.4.0

Bezpośrednie APK: Releases → v0.4.0-preview → TV-Player-0.4.0.apk. Stały podpis i identyfikator Preview, aktualizacja 0.3.0.

- EPG kanałów Xtream: program, opisy, bieżąca audycja; przycisk EPG przy kanale oraz menu Opcje w odtwarzaczu.
- Archiwum dostawcy: odtwarzanie od początku w oknie retencji tv_archive_duration. Adres timeshift używa czasu serwera i czasu trwania w minutach. Wymaga ponownego importu Xtream, aby pobrać flagi archiwum i strefę czasową. Serwer musi obsługiwać archiwalne HLS.
- PiP: Opcje → Obraz w obrazie; wymaga obsługi i zgody systemu Android.
- Ręczne nagrania kanałów MPEG-TS (.ts): Opcje → Nagraj kanał do pliku. Użytkownik wybiera plik i limit 30/60/120 minut. Usługa pierwszoplanowa z powiadomieniem i przyciskiem Zatrzymaj. Dostawca może ograniczać równoległe połączenia; zapis i oglądanie korzystają z dwóch połączeń. Nagrania HLS i harmonogram nagrań nie są jeszcze obsługiwane.
- Cofnij o 30 sekund działa w materiałach, których źródło udostępnia przewijanie. Media3 zachowuje 30 sekund bufora wstecz; dostępny zakres zależy od źródła. Nie jest to niezależny lokalny timeshift dla wszystkich kanałów. Dla kanału bez przewijania dostępne jest archiwum w EPG, jeśli dostawca je udostępnia.

Zachowane: TV/Seriale/Filmy, import dużych list, sezony i odcinki, ulubione, audio/napisy, wznawianie, obrót pełnego ekranu. M3U bez danych dostawcy nie dostaje automatycznie EPG ani archiwum.

Testy: parser EPG, strefy czasowe, retencja archiwum, URL timeshift; emulator sprawdza nawigację, tryb ekranu i zapis testowego strumienia TS przez usługę. Rzeczywisty serwer IPTV i uprawnienia PiP konkretnego telefonu wymagają sprawdzenia na urządzeniu.
