# OKTV Player na Xiaomi TV Box S (3. generacja)

Wersja dla **Xiaomi TV Box S 3. gen., model `MiTV-AFMU0`, Google TV / Android TV 14**. To ta sama aplikacja co na Fire Sticku: identyczny APK **0.9.6-preview**, pakiet `pl.tvplayer.television`, podpis preview i wszystkie funkcje (TV z podglądem, filmy, seriale, EPG, ulubione, nagrywanie, dodawanie źródła przez telefon). Nic nie jest wycięte ani zmienione.

Paczka `OKTV-Xiaomi-TV-Box.zip` zawiera APK, sumę SHA-256, instalator `install-xiaomi.sh` i tę instrukcję. Działa też na innych urządzeniach Android TV / Google TV 7.1+.

## 1. Przygotuj Xiaomi TV Box

1. Połącz box i komputer/Chromebook z tą samą siecią domową (nie gościnną).
2. Włącz Opcje programisty: **Ustawienia → System → Informacje → Kompilacja systemu operacyjnego Android TV** — naciśnij OK **7 razy**, aż pojawi się „Jesteś teraz programistą”.
3. Wróć do **Ustawienia → System → Opcje programisty** i włącz **Debugowanie USB**.
4. Odczytaj adres IP: **Ustawienia → Sieć i internet →** nazwa Twojej sieci, np. `192.168.1.50`.

## 2. Instalacja z terminala (Chromebook: Terminal → Linux, albo Linux/macOS)

Pobierz ZIP do katalogu, w którym działa terminal (na Chromebooku: **Pliki Linuksa**), a następnie:

```bash
sudo apt update && sudo apt install -y adb unzip
unzip -o OKTV-Xiaomi-TV-Box.zip -d oktv-xiaomi
bash oktv-xiaomi/install-xiaomi.sh 192.168.1.50
```

Na telewizorze pojawi się **„Zezwolić na debugowanie USB?”** — zaznacz „Zawsze zezwalaj z tego komputera” i wybierz **Zezwól**. Instalator czeka do 90 sekund. Sprawdza sumę APK, wersję Androida i model, instaluje przez `pm install -r` (aktualizacja zachowuje bibliotekę) i uruchamia OKTV Player.

### Jeśli port 5555 jest zamknięty (Debugowanie bezprzewodowe w Androidzie 14)

Na części oprogramowania Google TV 14 połączenie przez sam port 5555 nie działa. Wtedy:

1. **Opcje programisty → Debugowanie bezprzewodowe: Włącz**, potem wejdź w tę pozycję.
2. Zapisz adres z ekranu głównego Debugowania bezprzewodowego, np. `192.168.1.50:41234` (to adres połączenia).
3. Wybierz **Sparuj urządzenie za pomocą kodu**. Pokaże się drugi adres, np. `192.168.1.50:37123`, i sześciocyfrowy kod.
4. Uruchom:

```bash
bash oktv-xiaomi/install-xiaomi.sh 192.168.1.50:41234 --pair 192.168.1.50:37123
```

Wpisz kod z TV, gdy terminal o to poprosi. Porty zmieniają się po każdym włączeniu Debugowania bezprzewodowego — zawsze przepisuj bieżące.

## 3. Instalacja bez komputera (alternatywa)

1. Zainstaluj ze Sklepu Google Play aplikację do pobierania plików na TV (np. przeglądarkę plików lub „Send Files to TV”).
2. **Ustawienia → Aplikacje → Zabezpieczenia i ograniczenia → Nieznane źródła** — zezwól tylko tej aplikacji.
3. Prześlij `OKTV-Player-TV.apk` na box (pendrive USB, sieć lub telefon) i otwórz go, aby zainstalować.
4. Po instalacji wyłącz zgodę dla nieznanych źródeł.

Repozytorium jest prywatne, więc link GitHuba otwarty w niezalogowanej aplikacji na TV może pokazać 404. Pobierz plik na komputerze lub telefonie.

## 4. Pierwsze uruchomienie

OKTV Player pojawi się w **Aplikacje → Twoje aplikacje** z banerem OKTV. Możesz go przypiąć do ekranu głównego (przytrzymaj OK → Dodaj do ulubionych/Przenieś). Wybierz **Opcje → Źródło → Dodaj przez telefon**, zeskanuj kod QR telefonem w tej samej sieci i wpisz dane Xtream lub link M3U. Na koniec możesz wyłączyć Debugowanie USB i Debugowanie bezprzewodowe.

Pilot Xiaomi: strzałki i OK — wybór; **Wstecz** — powrót; przytrzymanie OK lub przycisk menu — EPG i ulubione; góra/dół podczas oglądania TV — zmiana kanału w bukiecie. Przy pierwszym nagrywaniu Android 14 zapyta o zgodę na powiadomienia (potrzebne do zatrzymania nagrania z powiadomienia).

## Gdy coś nie działa

- **unauthorized / brak połączenia:** sprawdź IP, Debugowanie USB, zgodę na ekranie TV; spróbuj trybu `--pair`. Sieć gościnna, VPN lub izolacja Wi-Fi blokują połączenie.
- **Inny podpis:** na boxie jest wersja podpisana innym kluczem. Instalator jej nie usuwa. Odinstaluj ją ręcznie tylko, jeśli nie potrzebujesz jej danych.
- **Nowsza wersja:** pobierz nowszą paczkę; instalator nie obniża wersji.
- **Brak miejsca:** usuń zbędne aplikacje (**Ustawienia → Aplikacje**), uruchom ponownie.
- **Nie widać aplikacji:** **Ustawienia → Aplikacje → Wyświetl wszystkie aplikacje → OKTV Player → Otwórz**.

Program TV, kodeki i dostępność materiałów zależą od źródła i urządzenia. Automatyczne testy uruchamiają tę samą aplikację na emulatorze Android TV 14 (API 34); nie zastępuje to sprawdzenia na fizycznym boxie z kontem dostawcy.

## Dla osoby budującej paczkę

```bash
python3 -m unittest discover -s scripts/tests -v
python3 scripts/package-xiaomi.py
```

ZIP powstaje w `dist/OKTV-Xiaomi-TV-Box.zip` i zawiera ten sam APK co `OKTV-FireStick-Chromebook.zip`.
