# OKTV Player: instalacja z Chromebooka na Fire Stick

Paczkę `OKTV-FireStick-Chromebook.zip` instalujesz bez kompilowania i bez logowania do GitHuba w terminalu. Zawiera APK **0.9.7-preview**, sumę SHA-256 oraz instalator. APK działa na **Fire OS 6 lub nowszym** i Android TV 7.1+. Nie działa na **Vega OS** ani starym Fire OS 5. Przed instalacją sprawdź model i system w ustawieniach Fire TV; sama nazwa „Fire Stick” nie oznacza zgodności.

## 1. Przygotuj urządzenia

1. Połącz Chromebooka i Fire Sticka z tą samą domową siecią. Unikaj sieci gościnnej z izolacją urządzeń.
2. Na Fire TV otwórz **Ustawienia → Mój Fire TV → Opcje programistyczne → Debugowanie ADB: Włącz**. Jeśli opcji nie ma, wejdź w **Informacje** i naciśnij nazwę modelu siedem razy. Wróć o ekran.
3. Odczytaj adres IPv4 w **Mój Fire TV → Informacje → Sieć**, np. `192.168.1.50`.
4. Na Chromebooku włącz **Ustawienia → Deweloperzy → Środowisko programistyczne Linux** i otwórz aplikację **Terminal → Linux**. Nie trzeba włączać trybu deweloperskiego całego Chromebooka ani debugowania Androida na Chromebooku. Na urządzeniach szkolnych/firmowych Linux może być zablokowany przez administratora.
5. Pobierz paczkę ZIP. W aplikacji **Pliki** przenieś ją z „Pobrane” do **Pliki Linuksa**, bez zmiany nazwy.

## 2. Wklej do terminala Linux

```bash
sudo apt update && sudo apt install -y adb unzip
unzip -o OKTV-FireStick-Chromebook.zip -d oktv-firestick
bash oktv-firestick/install-firestick.sh
```

Wpisz IP Fire Sticka. Na ekranie telewizora zaakceptuj pytanie **„Zezwolić na debugowanie USB?”** dla swojego Chromebooka. Nazwa „USB” jest poprawna także przy połączeniu przez Wi-Fi. Instalator czeka do 90 sekund; jeśli zgoda pojawi się później, uruchom ostatnią komendę ponownie.

Możesz też podać adres od razu:

```bash
bash oktv-firestick/install-firestick.sh 192.168.1.50
```

Skrypt sprawdza sumę APK, wersję systemu oraz wynik instalacji. Wysyła polecenia tylko do wskazanego IP, także gdy Chromebook ma inne urządzenie ADB. Aktualizuje pakiet `pl.tvplayer.television` przez `pm install -r`, zachowując ustawienia i bibliotekę przy zgodnym podpisie. Nie odinstalowuje aplikacji i nie wymusza obniżenia wersji. Paczka używa tego samego podpisu preview co poprzednie wydania tego projektu.

Po instalacji otworzy się OKTV Player. Wybierz **Opcje → Źródło → Dodaj przez telefon**, zeskanuj QR telefonem w tej samej sieci i wpisz dane dostawcy. Kod jest jednorazowy, ekran parowania wygasa po pięciu minutach. Następnie możesz wyłączyć Debugowanie ADB.

## Gdy instalacja się nie uda

- **Brak połączenia / unauthorized:** sprawdź IP, zgodę na TV i Debugowanie ADB. Urządzenia muszą się widzieć w LAN; VPN lub izolacja Wi-Fi mogą to blokować.
- **Connection refused:** sprawdź zgodność modelu i czy ADB jest włączone. Niektóre modele/systemy nie obsługują tej metody. Nie wyłączaj zabezpieczeń routera dla całej sieci.
- **Inny podpis:** zachowano starą aplikację i dane. Nie odinstalowuj jej bez kopii ustawień. Dotyczy to APK podpisanych innym kluczem.
- **Nowsza wersja:** pobierz nowszą paczkę; skrypt nie robi downgrade.
- **Brak miejsca:** usuń zbędne pliki/aplikacje na Fire TV i uruchom instalator ponownie.
- **Aplikacja zainstalowana, ale niewidoczna na stronie głównej:** otwórz **Ustawienia → Aplikacje → Zarządzaj zainstalowanymi aplikacjami → OKTV Player → Uruchom**.

## Dla osoby budującej paczkę

Po udanym buildzie i weryfikacji podpisu:

```bash
python3 -m unittest discover -s scripts/tests -v
python3 scripts/package-firestick.py
```

ZIP powstaje w `dist/`. Workflow TV dołącza go do artefaktów buildu. Wydanie GitHub publikowane jest dopiero przy ręcznym uruchomieniu workflow, po testach.
