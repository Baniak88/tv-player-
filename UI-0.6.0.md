# TV Player Preview 0.6.0

## Ekran główny

Brak bukietów i odczytu bibliotek na starcie. W pionie: duży kafel TV, dwa szerokie kafle Filmy/Seriale, osobne czytelne przyciski konta, źródła i ustawień. Tekst nie jest zmniejszany do 9sp. W poziomie zachowany układ paneli z referencji z większymi odstępami. Bukiety pojawiają się dopiero po wejściu do TV, filmów lub seriali. Odśwież źródło uruchamia aktualizację zapisanego konta Xtream; dla źródeł M3U bez danych konta trzeba ponownie podać adres.

## Kolejność bukietów

Domyślnie kolejność kategorii z API dostawcy, nie alfabet ani kolejność identyfikatorów. Import zapisuje rangę kategorii, cache ją zachowuje. Dla starszej biblioteki aktualizacja kolejności pobiera tylko kategorie, bez całego katalogu; pracuje w tle. Zapis metadanych jednej sekcji nie odszyfrowuje innych sekcji i nie usuwa ulubionych. Przy niedostępnym API zachowywana jest kolejność zapisana w liście. M3U zachowuje kolejność pierwszego wystąpienia grupy.

Przycisk ↕ w katalogu: Kolejność dostawcy / Nazwa A–Z / Nazwa Z–A / Najwięcej pozycji. Wybór zapamiętywany oddzielnie dla TV, filmów i seriali. Cała oferta pozostaje przyciskiem zbiorczym nad właściwymi bukietami. Sortowanie bukietów nie zmienia kolejności kanałów wewnątrz nich.

## Obrót

Pełny ekran używa OrientationEventListener i jawnych orientacji Activity. Działa niezależnie od systemowego autoobrotu. Strefy przejściowe zapobiegają drganiom obrazu przy przechylaniu. Czujnik jest wyłączany poza pełnym ekranem, po opuszczeniu odtwarzacza i w PiP. Zwykły widok wraca do pionu. Aplikacja nie zmienia ustawienia autoobrotu telefonu.

## Weryfikacja

Testy jednostkowe: kolejność kategorii API różna od kolejności kanałów, sortowanie, M3U, aktualizacja rang i kierunki obrotu. Emulator: ekran główny bez bukietów, bukiety po wejściu w TV, wybór A–Z i powrót do dostawcy, zapamiętanie wyboru, obrót poprzez zmianę danych akcelerometru przy accelerometer_rotation=0, cache, migracja, nagrywanie. Zrzuty: pion, poziom, siatka bukietów, wybrany bukiet.
