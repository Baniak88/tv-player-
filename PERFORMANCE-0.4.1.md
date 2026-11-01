# Szybsza biblioteka 0.4.1

Problem: na starcie czytano cały zaszyfrowany katalog przez CipherInputStream (aktualizacje szyfru co 512 bajtów, kosztowne z Android Keystore), odtwarzano wszystkie obiekty TV/VOD/seriali i dopiero wtedy pokazywano kanały. Gwiazdka wywoływała zapis całej biblioteki.

Zmiana: osobne pliki TV/movie/series, kompresja GZIP przed AES-GCM, zbiorcze uwierzytelnione odszyfrowanie skompresowanego pliku. TV wczytuje się na starcie, pozostałe sekcje po kliknięciu. Ulubione mają osobny mały plik. Dane pozostałych sekcji nie są skanowane przy filtrowaniu; wyszukiwanie ma opóźnienie 180 ms, aby nie przetwarzać każdej litery osobno.

Migrujemy stary library.enc automatycznie, jednorazowo. Nowy indeks pojawia się po zapisaniu wszystkich trzech sekcji. Stary plik jest usuwany dopiero po udanym zapisie. Pierwszy start starej dużej biblioteki nadal musi odszyfrować i przenieść jej zawartość; kolejne uruchomienia czytają tylko TV. Import z internetu nadal zależy od wielkości odpowiedzi i szybkości dostawcy.

Test emulatora: biblioteka 1000 kanałów TV, 40000 filmów i 10000 seriali; pomiar odczytu TV, osobny odczyt filmów; zmiana ulubionych nie zmienia pliku filmów; uszkodzenie pliku filmów nie blokuje TV; odczyt uszkodzonej sekcji jest odrzucany; test migracji starego cache z ulubionymi. Testy urządzenia obejmują też nawigację i nagrywanie TS.
