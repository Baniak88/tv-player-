# OKTV Player TV 0.9.5-preview

Ta wersja poprawia katalog na Fire Sticku na podstawie zgłoszenia z kanałami Sky Cinema w sekcji filmów i nieczytelnymi kafelkami.

- Nazwy kategorii M3U, takie jak `UK| MOVIES`, `MOVIES HEVC` i `24/7 MOVIES & SERIES`, nie oznaczają automatycznie VOD. Rozpoznawane są adresy strumieni, pliki wideo, identyfikatory EPG oraz jawne oznaczenia typu. Prawdziwe filmy i typy zwrócone przez API pozostają zachowane.
- Zapisana biblioteka z poprzedniej wersji jest porządkowana przy pierwszym otwarciu katalogu. Nie trzeba ponownie wpisywać źródła; adresy odtwarzania, kategorie, logotypy, dane EPG i ulubione pozostają zachowane.
- TV ma niższe wiersze i małe logotypy. Brakujące EPG nie zajmuje dodatkowej linii w każdym wierszu.
- Tylko aktualny fokus pilota ma biały obrys. Wybrana kategoria ma spokojne tło. Strzałki góra/dół przeglądają kategorie, prawo przechodzi do pozycji, a Wstecz z listy wraca do kategorii.
- Filmy i seriale mają 5 kolumn na mniejszym ekranie i 6 na większym, proporcjonalne obrazy oraz pełną nazwę wybranej pozycji pod katalogiem. W **Opcje → Widok: lista / plakaty** można wybrać czytelniejszy widok, zachowując zaznaczoną pozycję.
- Odcinki M3U z oznaczeniami `S01 E01` są grupowane w folder serialu i sezony. Poszczególne adresy odcinków pozostają oryginalne. Kontenery seriali z API nadal korzystają ze swojego dotychczasowego ekranu szczegółów.
- W TV na żywo pojawia się dolny pasek z nazwą kanału, audycją teraz i następnie, postępem oraz formatem audio. OK przełącza widoczność, Info otwiera pasek, Wstecz najpierw go chowa. Pasek znika po 6,5 sekundy. Gdy dostawca nie udostępnia EPG, wyświetlany jest czytelny komunikat. Pełny program i archiwum pozostają w Menu → Program TV i archiwum.
- **Menu → Zgodność dźwięku → Stereo PCM · gdy brak dźwięku** wyłącza przekazywanie zakodowanego dźwięku przez HDMI, preferuje dostępną ścieżkę AAC i miksuje dekodowane audio do stereo, zachowując kanał dialogów. Ustawienie pozostaje zapisane; zmiana zachowuje pauzę i pozycję filmu. Automatyczne HDMI/Dolby można przywrócić w tym samym miejscu. Włączono zapasowy dekoder w razie błędu inicjalizacji podstawowego. Nie dodaje to brakującego dekodera AC-3/E-AC-3 do urządzenia, które go nie ma.

Weryfikacja obejmuje testy regresji parsera i katalog z 20 000 odcinków, migrację zapisanej biblioteki z poprzednimi regułami oraz sterowanie pilotem przy 960×540 i 1280×720. Obrazy w zrzutach testowych są demonstracyjne; aplikacja pobiera właściwe materiały od dostawcy.

Instalacja i aktualizacja z Chromebooka: [CHROMEBOOK-FIRESTICK.md](CHROMEBOOK-FIRESTICK.md). Pakiet `OKTV-FireStick-Chromebook.zip` jest dołączany do artefaktu `television-chromebook` w przebiegu GitHub Actions **TV platforms**. Podpis i identyfikator `pl.tvplayer.television` pozostają zgodne z 0.9.4; versionCode wzrósł do 6. Fire OS 6+ / Android TV 7.1+, bez obsługi Vega OS.

Rzeczywista oferta i model Fire Sticka użytkownika nie są dostępne w środowisku testowym. Nazwy i adresy bez jednoznacznych oznaczeń pozostają ograniczeniem heurystyk M3U; sama nazwa `Movies` nie jest już powodem przeniesienia kanału do filmów.
