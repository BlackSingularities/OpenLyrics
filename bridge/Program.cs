using System.Text;
using System.Text.Json;
using Windows.Media.Control;

namespace OpenLyrics.Media;

/// <summary>
/// Odczyt bieżącej sesji multimediów Windows. Co ~300 ms (i przy każdej zmianie) wypisuje jeden
/// wiersz JSON: odtwarzacz, tytuł, wykonawca, album, długość, pozycja i stan. Polecenia na stdin:
/// "toggle", "next", "prev", "seek &lt;ms&gt;".
/// </summary>
public static class Program
{
    static GlobalSystemMediaTransportControlsSessionManager? manager;
    static readonly object gate = new();
    static string last = "";
    static long lastWrite;

    public static async Task<int> Main()
    {
        Console.OutputEncoding = new UTF8Encoding(false);
        Console.InputEncoding = new UTF8Encoding(false);
        try
        {
            manager = await GlobalSystemMediaTransportControlsSessionManager.RequestAsync();
        }
        catch (Exception e)
        {
            Write(new Dictionary<string, object?> { ["error"] = e.Message });
            return 1;
        }
        _ = Task.Run(ReadCommands);
        while (true)
        {
            try { await Publish(); }
            catch (Exception e) { Write(new Dictionary<string, object?> { ["error"] = e.Message }); }
            await Task.Delay(300);
        }
    }

    /// <summary>Sesja do pokazania: odtwarzająca, a gdy żadna nie gra – bieżąca systemowa.</summary>
    static GlobalSystemMediaTransportControlsSession? Pick()
    {
        if (manager == null) return null;
        var sessions = manager.GetSessions();
        foreach (var s in sessions)
        {
            var info = s.GetPlaybackInfo();
            if (info?.PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing) return s;
        }
        return manager.GetCurrentSession() ?? sessions.FirstOrDefault();
    }

    static async Task Publish()
    {
        var s = Pick();
        if (s == null)
        {
            Emit(new Dictionary<string, object?> { ["none"] = true });
            return;
        }
        var props = await s.TryGetMediaPropertiesAsync();
        var info = s.GetPlaybackInfo();
        var tl = s.GetTimelineProperties();
        var status = info?.PlaybackStatus ?? GlobalSystemMediaTransportControlsSessionPlaybackStatus.Closed;
        var playing = status == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing;
        var rate = info?.PlaybackRate ?? 1.0;
        var pos = tl.Position;
        var updated = tl.LastUpdatedTime;
        // Część odtwarzaczy aktualizuje pozycję rzadko – dopisujemy czas od ostatniej aktualizacji.
        if (playing && updated.Year > 2000)
        {
            var since = DateTimeOffset.Now - updated;
            if (since > TimeSpan.Zero && since < TimeSpan.FromHours(6)) pos += since * rate;
        }
        var end = tl.EndTime - tl.StartTime;
        Emit(new Dictionary<string, object?>
        {
            ["app"] = s.SourceAppUserModelId ?? "",
            ["title"] = props?.Title ?? "",
            ["artist"] = string.IsNullOrEmpty(props?.Artist) ? props?.AlbumArtist ?? "" : props!.Artist,
            ["album"] = props?.AlbumTitle ?? "",
            ["duration"] = (long)Math.Max(0, end.TotalMilliseconds),
            ["position"] = (long)Math.Max(0, (pos - tl.StartTime).TotalMilliseconds),
            ["playing"] = playing,
            ["rate"] = rate,
            ["at"] = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
        });
    }

    /// <summary>Wypisuje stan, gdy się zmienił (pomijając pozycję), a poza tym co 2 s – dla korekty pozycji.</summary>
    static void Emit(Dictionary<string, object?> d)
    {
        var key = string.Join("|", d.Where(kv => kv.Key != "position" && kv.Key != "at").Select(kv => kv.Key + "=" + kv.Value));
        var now = Environment.TickCount64;
        if (key == last && now - lastWrite < 2000) return;
        last = key;
        lastWrite = now;
        Write(d);
    }

    static void Write(Dictionary<string, object?> d)
    {
        var json = JsonSerializer.Serialize(d, BridgeJson.Default.DictionaryStringObject);
        lock (gate)
        {
            Console.Out.WriteLine(json);
            Console.Out.Flush();
        }
    }

    static async Task ReadCommands()
    {
        string? line;
        while ((line = await Console.In.ReadLineAsync()) != null)
        {
            try
            {
                var s = Pick();
                if (s == null) continue;
                var parts = line.Trim().Split(' ', 2);
                switch (parts[0])
                {
                    case "toggle": await s.TryTogglePlayPauseAsync(); break;
                    case "next": await s.TrySkipNextAsync(); break;
                    case "prev": await s.TrySkipPreviousAsync(); break;
                    case "seek":
                        if (parts.Length > 1 && long.TryParse(parts[1], out var ms))
                            await s.TryChangePlaybackPositionAsync(TimeSpan.FromMilliseconds(ms).Ticks);
                        break;
                    case "quit": Environment.Exit(0); break;
                }
                last = "";
            }
            catch { }
        }
        // Aplikacja zamknęła potok – kończymy razem z nią.
        Environment.Exit(0);
    }
}

[System.Text.Json.Serialization.JsonSerializable(typeof(Dictionary<string, object?>))]
[System.Text.Json.Serialization.JsonSerializable(typeof(string))]
[System.Text.Json.Serialization.JsonSerializable(typeof(long))]
[System.Text.Json.Serialization.JsonSerializable(typeof(bool))]
[System.Text.Json.Serialization.JsonSerializable(typeof(double))]
internal partial class BridgeJson : System.Text.Json.Serialization.JsonSerializerContext { }
