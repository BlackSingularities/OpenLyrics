package com.blacksingularities.openlyrics.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.platform.win32.WinUser
import java.awt.Window

/** Drobne funkcje Win32 potrzebne nakładce i autostartowi. */
object Win32 {
    private const val WS_EX_TRANSPARENT = 0x00000020
    private const val WS_EX_LAYERED = 0x00080000
    private const val WS_EX_TOOLWINDOW = 0x00000080
    private const val WS_EX_NOACTIVATE = 0x08000000
    private const val WS_EX_APPWINDOW = 0x00040000
    private const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val RUN_NAME = "OpenLyrics"

    private fun hwnd(w: Window): WinDef.HWND? = runCatching {
        WinDef.HWND(Pointer(Native.getComponentID(w)))
    }.getOrNull()

    /**
     * Nakładka: bez przycisku na pasku zadań, bez kradzenia fokusu, a po zablokowaniu –
     * przepuszcza kliknięcia do okien pod spodem.
     */
    fun setOverlayStyle(w: Window, clickThrough: Boolean) {
        val h = hwnd(w) ?: return
        runCatching {
            var ex = User32.INSTANCE.GetWindowLong(h, WinUser.GWL_EXSTYLE)
            ex = (ex or WS_EX_LAYERED or WS_EX_TOOLWINDOW or WS_EX_NOACTIVATE) and WS_EX_APPWINDOW.inv()
            ex = if (clickThrough) ex or WS_EX_TRANSPARENT else ex and WS_EX_TRANSPARENT.inv()
            User32.INSTANCE.SetWindowLong(h, WinUser.GWL_EXSTYLE, ex)
        }
    }

    /** Polecenie uruchomienia: plik .exe z instalacji (w trybie deweloperskim – brak). */
    private fun launcher(): String? {
        val cmd = ProcessHandle.current().info().command().orElse(null) ?: return null
        val exe = java.io.File(cmd)
        // W instalacji jpackage proces to OpenLyrics.exe; java.exe oznacza uruchomienie z Gradle.
        return if (exe.name.equals("java.exe", true) || exe.name.equals("javaw.exe", true)) null else exe.absolutePath
    }

    fun isAutostart(): Boolean = runCatching {
        Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, RUN_NAME)
    }.getOrDefault(false)

    fun setAutostart(enabled: Boolean): Boolean = runCatching {
        if (enabled) {
            val exe = launcher() ?: return false
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, RUN_NAME, "\"$exe\" --minimized")
        } else if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, RUN_NAME)) {
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, RUN_NAME)
        }
        true
    }.getOrDefault(false)
}
