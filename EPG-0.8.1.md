# EPG 0.8.1

Poprawka oparta na wydaniu 0.8.0: filmy, seriale, okładki, źródła i sortowanie pozostają zachowane.

- Program „teraz” i „dalej” przy kanałach TV; pobieranie dotyczy widocznych wierszy. Odświeżanie opisu co minutę, pamięć podręczna wyników i łączenie identycznych zapytań.
- EPG Xtream: get_short_epg i get_simple_data_table z przejściem do alternatywnego endpointu przy braku programu lub błędzie. Krótsze limity czasu dla EPG.
- Obsługa starszych ścieżek /user/password/1234.ts bez /live/.
- XMLTV jako alternatywa: automatyczne xmltv.php dla konta Xtream lub x-tvg-url/url-tvg z nagłówka M3U. Obsługa XML i XML.gz, tvg-id, ostrożnego dopasowania po nazwie i wariantów RAW/HD.
- XMLTV jest parsowane strumieniowo do lokalnego indeksu SQLite. Jeden plik jest pobierany wspólnie dla kanałów tego źródła, bez osobnego transferu dla każdego wiersza. Cache 1 godzina, program od wczoraj do 3 dni naprzód. Dane logowania i URL źródła nie są zapisywane w bazie EPG; klucz źródła to SHA-256 URL. Biblioteka z URL pozostaje szyfrowana.
- Programy z różnymi formatami dat, null zamiast timestamp i brakującym czasem końca (ustalanym tylko na podstawie następnej audycji).
- Okno EPG przewija się do aktualnej audycji i pokazuje źródło programu. Brak dopasowania i awaria pobrania mają różne komunikaty.

Pierwsze pobranie dużego XMLTV może potrwać; nie blokuje ekranu biblioteki. Jeśli stara lista M3U zawiera adres EPG tylko w nagłówku, należy ją wczytać ponownie, ponieważ wcześniejsze wersje pomijały ten adres. Dostawca musi udostępniać program; aplikacja nie generuje audycji przy braku danych.

Testy: alternatywne API, starsze adresy, null/ISO/datowanie końca, nagłówek M3U i logo, parser XMLTV (offset, CDATA, zagnieżdżone opisy), nazwy RAW/HD. Emulator sprawdza XMLTV.gz, bieżącą i następną audycję w liście TV, okno EPG i jedno pobranie pliku dla dwóch kanałów. Testy używają kontrolowanego serwera testowego, nie konta użytkownika.
