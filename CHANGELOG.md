## 1.2.0

- Windows: instalacja w Program Files z uprawnieniami administratora; aktualizacja z aplikacji prosi o zgodę (UAC), instaluje się w tym samym folderze i nie zostawia drugiej kopii. Stara kopia z folderu użytkownika jest usuwana automatycznie.
- Windows: dowolna czcionka zainstalowana w systemie – lista z wyszukiwaniem i podglądem; pełna skala grubości 100–900.
- Gdy LRCLIB nie ma tekstu z czasami, aplikacja szuka też w NetEase Cloud Music, a na końcu w lyrics.ovh. Ręczne wyszukiwanie pokazuje wyniki z obu baz.
- Android: nakładka i obsługa odtwarzaczy (m.in. OpenAmp) odporne na błędy – problem wyłącza najwyżej nakładkę, a nie całą aplikację. Opis ewentualnego błędu można skopiować przy kolejnym uruchomieniu.

## 1.1.1

- Po chwilowej awarii LRCLIB (np. kod 503) aplikacja sama ponawia wyszukiwanie tekstu – zamiast zostawiać komunikat o błędzie do zmiany utworu.

## 1.1.0

- Nowe tryby śledzenia śpiewu: same wiersze, wypełnianie kolorem, przesuwana kreska śpiewania albo wypełnianie i kreska razem – dokładnie co do słowa przy rozszerzonym LRC, także w wierszach zawiniętych na kilka linii.
- Pełne ustawienia czcionki: krój, rozmiar aktywnego i pozostałych wierszy, grubość, odstęp liter i wierszy, kursywa, wielkie litery.
- Selektor kolorów (odcień, nasycenie, jasność) dla aktywnego i pozostałych wierszy, wypełnienia, kreski, obrysu, cienia i tła – z zapamiętywaniem własnych kolorów.
- Obrys liter oraz cień z regulacją rozmycia, krycia, przesunięcia i koloru; poświata aktywnego wiersza.
- Gotowe style: Klasyczny, Karaoke, Neon, Napisy filmowe, Minimalny, Pasek, Szeryfowy.
- Podgląd na żywo w ustawieniach – także gdy nic nie gra.
- Zapis tekstu wewnątrz plików MP3 (ID3) i FLAC (komentarz LYRICS) – automatycznie albo przyciskiem „Zapisz w pliku”; pozostałe tagi, okładka i dźwięk zostają nietknięte.
- Windows: połączenia z odtwarzaczami podającymi ścieżkę grającego pliku – AIMP i Winamp bez wtyczek, foobar2000 (beefweb) i VLC (interfejs WWW).

## 1.0.1

- Dopracowany interfejs: wszystkie opisy w ustawieniach są białe i czytelne na ciemnym tle.
- Nowe suwaki w stylu aplikacji: cienka ścieżka i okrągły uchwyt, wartość ustawiana dotknięciem albo przeciągnięciem w dowolnym miejscu.

## 1.0.0

- Pierwsze wydanie OpenLyrics na Windows i Androida.
- Tekst odtwarzanej piosenki w przezroczystej nakładce bez ramki, zawsze na wierzchu – wiersze przesuwają się płynnie od dołu do góry według znaczników czasu, aktywny wiersz jest powiększony i wypełnia się kolorem jak w karaoke (dokładnie co do słowa przy rozszerzonym LRC).
- Działa z każdym odtwarzaczem, który zgłasza się do systemu: Spotify, AIMP, foobar2000, MusicBee, przeglądarki, YouTube Music, Poweramp i inne.
- Tekst osadzony w pliku: MP3 (USLT, SYLT, TXXX), FLAC, OGG/Opus (komentarze Vorbis), M4A (©lyr), a także plik .lrc obok utworu. Gdy go brak – wyszukiwanie w otwartej bazie LRCLIB, z pamięcią podręczną i ręcznym wyborem wyniku.
- Przesunięcie czasu dla pojedynczego utworu i dla wszystkich tekstów, zapis tekstu do pliku .lrc.
- Pełna konfiguracja wyglądu: rozmiar, liczba wierszy, wyrównanie, kolory, cień liter, tło, przewijanie płynne lub wiersz po wierszu, wygaszanie krawędzi.
- Windows: nakładka przeciągana myszą i skalowana z rogu, po zablokowaniu przepuszcza kliknięcia; ikona w zasobniku, autostart, indeks biblioteki muzyki.
- Android: nakładka nad innymi aplikacjami, przeciągana palcem; odczyt plików z MediaStore.
- Aktualizacje pobierane bezpośrednio z redzikowski.com – instalator Windows aktualizuje po cichu i uruchamia aplikację ponownie.
