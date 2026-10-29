using System.IO.MemoryMappedFiles;
using System.Runtime.InteropServices;
using System.Text;

namespace OpenLyrics.Media;

/// <summary>
/// Ścieżka odtwarzanego pliku z odtwarzaczy, które ją udostępniają bez wtyczek:
/// AIMP (zdalny interfejs „AIMP2_RemoteInfo”) i Winamp oraz zgodne (komunikaty IPC okna).
/// </summary>
internal static class Players
{
    public static string CurrentFile(string app)
    {
        try
        {
            var a = app.ToLowerInvariant();
            if (a.Contains("aimp")) return Aimp() ?? "";
            if (a.Contains("winamp")) return Winamp() ?? "";
        }
        catch { }
        return "";
    }

    /// <summary>
    /// AIMP: nagłówek TAIMPRemoteFileInfo (rozmiar nagłówka, długości napisów), potem napisy UTF-16
    /// w kolejności: album, wykonawca, data, plik, gatunek, tytuł.
    /// </summary>
    static string? Aimp()
    {
        using var map = MemoryMappedFile.OpenExisting("AIMP2_RemoteInfo", MemoryMappedFileRights.Read);
        using var v = map.CreateViewAccessor(0, 2048, MemoryMappedFileAccess.Read);
        var header = v.ReadInt32(0);
        if (header < 64 || header > 512 || v.ReadInt32(4) == 0) return null;
        int album = v.ReadInt32(40), artist = v.ReadInt32(44), date = v.ReadInt32(48), file = v.ReadInt32(52);
        if (file <= 0 || file > 1000) return null;
        var offset = header + (album + artist + date) * 2;
        if (offset + file * 2 > 2048) return null;
        var buf = new byte[file * 2];
        v.ReadArray(offset, buf, 0, buf.Length);
        var path = Encoding.Unicode.GetString(buf);
        return File.Exists(path) ? path : null;
    }

    const uint WM_USER = 0x400;
    const int IPC_GETLISTPOS = 125;
    const int IPC_GETPLAYLISTFILEW = 214;

    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern IntPtr FindWindow(string cls, string? title);
    [DllImport("user32.dll")] static extern IntPtr SendMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("kernel32.dll")] static extern IntPtr OpenProcess(uint access, bool inherit, uint pid);
    [DllImport("kernel32.dll")] static extern bool ReadProcessMemory(IntPtr p, IntPtr addr, byte[] buf, int size, out IntPtr read);
    [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr h);

    /// <summary>Winamp: pozycja na liście, wskaźnik do ścieżki w pamięci odtwarzacza, odczyt tej pamięci.</summary>
    static string? Winamp()
    {
        var h = FindWindow("Winamp v1.x", null);
        if (h == IntPtr.Zero) return null;
        var pos = SendMessage(h, WM_USER, IntPtr.Zero, (IntPtr)IPC_GETLISTPOS);
        var ptr = SendMessage(h, WM_USER, pos, (IntPtr)IPC_GETPLAYLISTFILEW);
        if (ptr == IntPtr.Zero) return null;
        GetWindowThreadProcessId(h, out var pid);
        var proc = OpenProcess(0x0010, false, pid);
        if (proc == IntPtr.Zero) return null;
        try
        {
            var buf = new byte[1040];
            if (!ReadProcessMemory(proc, ptr, buf, buf.Length, out _)) return null;
            var s = Encoding.Unicode.GetString(buf);
            var end = s.IndexOf('\0');
            var path = end >= 0 ? s[..end] : s;
            return File.Exists(path) ? path : null;
        }
        finally { CloseHandle(proc); }
    }
}
