# Ekran główny i bukiety 0.5.0

Układ według dostarczonej referencji: ciemne granatowo-czarne tło, czerwone kafle, duży kafel TV po lewej, Filmy/Seriale/Konto/Zmień źródło w środku i Ustawienia/Odśwież widok/Wyjście po prawej. Własna identyfikacja TV Player i rysowane ikony konturowe. Układ reaguje na orientację telefonu.

Pod menu na stronie głównej widoczne są rzeczywiste bukiety TV z zaimportowanej biblioteki i liczby kanałów. Kliknięcie bukietu otwiera bezpośrednio jego kanały. W TV, filmach i serialach widok startowy pokazuje siatkę kategorii zamiast rozwijanego selektora. Przycisk Bukiety i cofnięcie wracają z listy do siatki; wyszukiwanie i ulubione pozostają dostępne.

Odśwież widok ponownie odczytuje lokalny cache bukietów. Nie pobiera ponownie listy od dostawcy. Zmień źródło otwiera istniejący import M3U/Xtream. Konto pokazuje host źródła i stan lokalnej biblioteki, bez haseł i bez wymyślania informacji o abonamencie. Ustawienia prowadzą do źródeł, uprawnień systemowych i informacji o wersji.

Zachowany podział cache i odczyt TV bez skanowania filmów i seriali. Siatki i listy są wirtualizowane. Hasła i zapis ulubionych pozostają szyfrowane.

Testy: ekran główny, widoczne bukiety Sport i Dzieci na danych testowych, bezpośrednie wejście w Sport bez kanałów dziecięcych, kategorie filmów i seriali, odtwarzacz, zapis nagrania, duży cache i migracja. Zrzuty ekranu rzeczywistego emulatora: pion, poziom, lista wybranego bukietu.
