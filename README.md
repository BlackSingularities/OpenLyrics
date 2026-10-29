# OpenLyrics

**Tekst odtwarzanej piosenki nad wszystkimi oknami – na Windows i Androida.** Przezroczysta nakładka bez ramki, w której wiersze przesuwają się od dołu do góry zgodnie ze znacznikami czasu. Tekst pochodzi z samego pliku muzycznego, a gdy go tam nie ma – z otwartej bazy LRCLIB.

[![Build & Release](../../actions/workflows/release.yml/badge.svg)](../../actions/workflows/release.yml)

## Pobieranie

Najnowsza wersja jest zawsze na **[redzikowski.com/portfolio/openlyrics](https://redzikowski.com/portfolio/openlyrics)**. Każdy wypchnięty tag `vX.Y.Z` buduje i publikuje wydanie na obie platformy, a zainstalowana aplikacja sama pobiera kolejne wersje z redzikowski.com.

**Windows 10 / 11**
1. Uruchom `OpenLyrics-x.y.z-setup.exe` – instalacja dla bieżącego użytkownika, bez uprawnień administratora.
2. Ikona OpenLyrics pojawi się w zasobniku obok zegara.

**Android 10+**
1. Zainstaluj `OpenLyrics-x.y.z.apk` (zezwól na instalację z tego źródła).
2. W aplikacji nadaj trzy uprawnienia: dostęp do powiadomień (informacja o odtwarzanym utworze), wyświetlanie nad aplikacjami i pliki muzyczne.

## Jak to działa

| Krok | Windows | Android |
|---|---|---|
| **Co gra** | Sesja multimediów Windows (te same dane, co w systemowym panelu głośności) – mały mostek `OpenLyricsMedia.exe` | Sesje multimediów wszystkich odtwarzaczy przez usługę powiadomień |
| **Gdzie jest plik** | Indeks wybranych folderów z muzyką – po tagach, a bez tagów po nazwie pliku | Adres z odtwarzacza albo MediaStore (tytuł, wykonawca, długość) |
| **Tekst** | osadzony w pliku → `.lrc` obok → zapisany wcześniej → LRCLIB | to samo |
| **Nakładka** | Okno bez ramki, przezroczyste, zawsze na wierzchu; zablokowane przepuszcza kliknięcia | Okno nad aplikacjami; zablokowane przepuszcza dotyk |

### Obsługiwane tagi z tekstem

| Format | Tagi |
|---|---|
| **MP3** (także ID3 w WAV/AIFF) | `USLT` (LRC albo zwykły tekst), `SYLT` (czasy w ms, także słowo po słowie), `TXXX:LYRICS` / `SYNCEDLYRICS` |
| **FLAC, OGG, Opus** | komentarze Vorbis `LYRICS`, `SYNCEDLYRICS`, `UNSYNCEDLYRICS` |
| **M4A / ALAC** | atom `©lyr` |
| **Plik obok** | `Utwór.lrc` – także rozszerzony LRC z czasami słów `<mm:ss.xx>` i `[offset:]` |

## Funkcje

- Płynne przewijanie (wiersz stoi, potem łagodnie ustępuje następnemu) albo skokowe wiersz po wierszu.
- Karaoke: aktywny wiersz wypełnia się kolorem w rytm śpiewu – dokładnie co do słowa przy rozszerzonym LRC.
- Rozmiar, liczba widocznych wierszy, wysokość aktywnego wiersza, wyrównanie, kolory, krycie, powiększenie, cień liter, tło od całkowicie przezroczystego, wygaszanie krawędzi, tytuł nad tekstem.
- Przesunięcie czasu dla jednego utworu (zapamiętywane) i dla wszystkich tekstów.
- Ręczne wyszukiwanie w LRCLIB z wyborem wyniku zapamiętywanym dla utworu; zapis tekstu do `.lrc` obok pliku.
- Porządkowanie tytułów z przeglądarek i YouTube („Wykonawca - Tytuł (Official Video)”, kanały VEVO / „- Topic”).
- Sterowanie odtwarzaczem (poprzedni / pauza / następny) z okna aplikacji.
- Windows: zasobnik z menu, autostart, start zminimalizowany, jedna kopia aplikacji.
- Interfejs po polsku i angielsku.

## Architektura

```
shared/src/commonMain/   # wspólny kod: interfejs (Compose Multiplatform), nakładka LyricsOverlay,
                         # LyricsController, parser LRC, czytnik tagów, LRCLIB, ustawienia, aktualizacje
desktop/                 # Windows: Compose Desktop, okno nakładki (JNA), zasobnik, indeks biblioteki
bridge/                  # mostek C# do GlobalSystemMediaTransportControls (sesja multimediów Windows)
app/                     # Android: usługa powiadomień z nakładką, MediaStore, PackageInstaller
```

- **Kotlin Multiplatform · Compose Multiplatform · Material 3** – ten sam kod interfejsu na obu platformach, w stylu OpenDocument.
- Czytnik tagów bez zewnętrznych bibliotek (ID3v2.2–2.4, FLAC, OGG, MP4).

## Budowanie lokalnie

Wymagany JDK 17, Android SDK (API 36) i .NET 8 SDK (mostek Windows):

```bash
dotnet publish bridge/MediaBridge.csproj -c Release -o desktop/resources/windows
gradle :desktop:run
gradle :app:assembleDebug
gradle :shared:desktopTest
```

## Licencja

MIT · Pomysł i wykonanie: Adam Rędzikowski · [redzikowski.com](https://redzikowski.com) · [☕ Postaw kawę](https://buycoffee.to/adamredzikowski)
