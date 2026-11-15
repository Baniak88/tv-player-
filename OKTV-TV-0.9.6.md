# OKTV Player TV 0.9.6-preview

Nowy układ TV jest inspirowany aplikacjami streamingowymi: ciemne tło, spokojny pasek zakładek, wyróżniona pozycja i poziome rzędy miniatur według kategorii dostawcy. Zachowuje markę OKTV i korzysta z rzeczywistej biblioteki użytkownika.

- TV ma osobne kolumny bukietów i kanałów oraz podgląd obrazu z programem obok. Pierwsze OK wybiera podgląd, kolejne otwiera pełny ekran. Strzałki góra/dół i przyciski kanał +/− zmieniają kanał podczas oglądania w obrębie bieżącego bukietu. Odtwarzacz przekazuje ten sam strumień pomiędzy podglądem a pełnym ekranem.
- Filmy i seriale domyślnie mają poziome rzędy okładek. Góra/dół zmienia rząd, lewo/prawo wybiera pozycję, OK otwiera szczegóły. „Pokaż wszystkie” otwiera kompletny bukiet, a Wstecz przywraca wybrany rząd i kartę.
- TV, filmy i seriale zapamiętują miejsce osobno. Powrót z odtwarzania i szczegółów oraz odtworzenie aktywności przywracają kategorię, zaznaczenie i przewinięcie. Sezony M3U wracają do wybranego serialu.
- Wstecz z głównego katalogu otwiera nawigację zamiast samodzielnie przełączać na ekran główny. Z listy TV najpierw przenosi fokus do bukietów. „Główna” jest osobnym wyborem.
- Ekran główny pokazuje skróty TV/Filmy/Seriale, powrót do przeglądania i niewielkie podglądy zapisanej biblioteki. Nie pobiera całej oferty od dostawcy przy otwieraniu strony głównej.
- Rzędy wyświetlają najwyżej 24 pozycje podglądu na kategorię; pozostała oferta jest dostępna przez „Pokaż wszystkie”. Widoki są wirtualizowane, a grafiki zakrytego ekranu przestają się pobierać podczas oglądania.
- Przy przełączaniu działów używany jest przygotowany katalog w pamięci; nie trzeba ponownie grupować wszystkich odcinków. Powtarzające się nazwy kategorii i adresy grafik współdzielą ograniczoną pulę pamięci. Odświeżanie źródła M3U działa w tle przy zachowaniu zapisanej biblioteki.
- Wyszukiwanie obejmuje nazwy bukietów i ignoruje separatory, np. „UK | Movies”. Dla list M3U wygenerowanych przez `get.php` aplikacja próbuje dodatkowo uzupełnić brakujące bukiety TV z API tego samego dostawcy. Zwykłe listy M3U nie są sondowane. Jeśli dostawca nie zwraca danej kategorii, aplikacja nie tworzy jej zawartości.
- Opcje katalogu zawierają kategorie, ulubione, sortowanie oraz wybór rzędów, siatki lub listy. Konto, źródło i ustawienia są w „Opcje”.

Zapis lokalizacji używa skrótów SHA-256 zamiast adresów strumieni. Hasła i pełne URL dostawcy nie trafiają do preferencji nawigacji. Katalog, konto i ulubione zachowują dotychczasowy format oraz szyfrowanie.

Aktualizacja: ten sam pakiet `pl.tvplayer.television` i podpis preview, versionCode 7. Fire OS 6+ / Android TV API 25+. Instrukcja: [CHROMEBOOK-FIRESTICK.md](CHROMEBOOK-FIRESTICK.md).

Program TV, grafiki i dostępność materiałów zależą od źródła. Zrzuty i automatyczne testy wykorzystują własne materiały demonstracyjne. Nie oznacza to sprawdzenia urządzenia i konta dostawcy użytkownika.
