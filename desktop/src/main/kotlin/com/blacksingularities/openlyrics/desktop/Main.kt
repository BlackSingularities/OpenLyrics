package com.blacksingularities.openlyrics.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.blacksingularities.openlyrics.app.LyricsController
import com.blacksingularities.openlyrics.data.OverlayBounds
import com.blacksingularities.openlyrics.i18n.tr
import com.blacksingularities.openlyrics.ui.LyricsOverlay
import com.blacksingularities.openlyrics.ui.MainScreen
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import java.awt.Cursor
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import javax.swing.JFileChooser
import javax.swing.UIManager
import kotlin.system.exitProcess

/** Port do sygnału „pokaż okno” od drugiej uruchomionej kopii aplikacji. */
private const val INSTANCE_PORT = 47_391

@OptIn(FlowPreview::class)
fun main(args: Array<String>) {
    // Jedna kopia aplikacji: druga tylko prosi pierwszą o pokazanie okna.
    val dataDir = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "OpenLyrics").apply { mkdirs() }
    val lock = runCatching { RandomAccessFile(File(dataDir, ".lock"), "rw").channel.tryLock() }.getOrNull()
    if (lock == null) {
        runCatching { Socket(InetAddress.getLoopbackAddress(), INSTANCE_PORT).use { it.getOutputStream().write('S'.code) } }
        exitProcess(0)
    }
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    System.setProperty("skiko.renderApi", System.getProperty("skiko.renderApi") ?: "DIRECT3D")

    val platform = DesktopPlatform()
    val controller = LyricsController(platform)
    platform.controller = controller
    platform.pickFolder = {
        val ch = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = tr("Wybierz folder z muzyką")
            currentDirectory = platform.defaultMusic
        }
        if (ch.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) ch.selectedFile else null
    }
    platform.rescan()

    val minimized = "--minimized" in args || controller.settings.startMinimized
    var showMain by mutableStateOf(!minimized)
    Thread {
        runCatching {
            ServerSocket(INSTANCE_PORT, 4, InetAddress.getLoopbackAddress()).use { server ->
                while (true) server.accept().use { showMain = true }
            }
        }
    }.apply { isDaemon = true }.start()

    application(exitProcessOnExit = true) {
        val s = controller.settings
        val icon = painterResource("icon.png")
        fun quit() {
            platform.media.close()
            exitApplication()
        }

        Tray(
            icon = icon,
            tooltip = controller.track?.let { "OpenLyrics · ${it.artist} – ${it.title}" } ?: "OpenLyrics",
            onAction = { showMain = true },
            menu = {
                Item(tr("Pokaż OpenLyrics"), onClick = { showMain = true })
                CheckboxItem(tr("Nakładka"), checked = s.overlayEnabled) { v -> controller.updateSettings { it.copy(overlayEnabled = v) } }
                CheckboxItem(tr("Zablokuj nakładkę"), checked = s.overlayLocked) { v -> controller.updateSettings { it.copy(overlayLocked = v) } }
                Separator()
                Item(tr("Tekst wcześniej (+0,25 s)"), onClick = { controller.nudgeOffset(250) })
                Item(tr("Tekst później (−0,25 s)"), onClick = { controller.nudgeOffset(-250) })
                Separator()
                Item(tr("Zakończ"), onClick = { quit() })
            },
        )

        if (showMain) {
            val state = rememberWindowState(size = DpSize(480.dp, 860.dp), position = WindowPosition(Alignment.Center))
            Window(
                onCloseRequest = { showMain = false },
                state = state,
                title = "OpenLyrics",
                icon = icon,
            ) {
                LaunchedEffect(Unit) {
                    window.minimumSize = java.awt.Dimension(380, 520)
                    window.toFront()
                }
                MainScreen(controller)
            }
        }

        if (s.overlayEnabled) OverlayWindow(controller)
    }
}

@OptIn(FlowPreview::class)
@androidx.compose.runtime.Composable
private fun OverlayWindow(controller: LyricsController) {
    val s = controller.settings
    val screen = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
    val b = s.bounds
    val initial = remember {
        val w = b.width.coerceIn(240, screen.width)
        val h = b.height.coerceIn(80, screen.height)
        val x = if (b.x < 0) screen.x + (screen.width - w) / 2 else b.x
        val y = if (b.y < 0) screen.y + screen.height - h - 60 else b.y
        Triple(x, y, w to h)
    }
    val state = rememberWindowState(
        position = WindowPosition(initial.first.dp, initial.second.dp),
        size = DpSize(initial.third.first.dp, initial.third.second.dp),
    )
    Window(
        onCloseRequest = { controller.updateSettings { it.copy(overlayEnabled = false) } },
        state = state,
        title = "OpenLyrics – nakładka",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        focusable = false,
        icon = painterResource("icon.png"),
    ) {
        val locked = s.overlayLocked
        LaunchedEffect(locked) {
            Win32.setOverlayStyle(window, clickThrough = locked)
            window.isAlwaysOnTop = true
        }
        // Zapamiętanie położenia i rozmiaru po zakończeniu przesuwania.
        LaunchedEffect(Unit) {
            snapshotFlow { state.position to state.size }.drop(1).debounce(600).collect { (p, sz) ->
                val pos = p as? WindowPosition.Absolute ?: return@collect
                controller.updateSettings {
                    it.copy(bounds = OverlayBounds(pos.x.value.toInt(), pos.y.value.toInt(), sz.width.value.toInt(), sz.height.value.toInt()))
                }
            }
        }
        val track = controller.track
        val content = @androidx.compose.runtime.Composable {
            LyricsOverlay(
                track = track,
                state = controller.lyricsState,
                style = s.style,
                offsetMs = controller.offsetFor(track),
                frame = !locked,
                hideWhenPaused = s.hideWhenPaused,
                hideWhenNoLyrics = s.hideWhenNoLyrics,
            )
        }
        Box(Modifier.fillMaxSize()) {
            if (locked) {
                content()
            } else {
                WindowDraggableArea(Modifier.fillMaxSize()) { content() }
                // Uchwyt w prawym dolnym rogu – zmiana rozmiaru.
                var drag by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .background(Color.White.copy(alpha = if (drag) 0.9f else 0.55f), RoundedCornerShape(topStart = 12.dp, bottomEnd = 14.dp))
                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.SE_RESIZE_CURSOR)))
                        .pointerInput(Unit) {
                            detectDragGestures(onDragStart = { drag = true }, onDragEnd = { drag = false }, onDragCancel = { drag = false }) { change, amount ->
                                change.consume()
                                val sz = state.size
                                state.size = DpSize(
                                    (sz.width + amount.x.toDp()).coerceIn(240.dp, 3000.dp),
                                    (sz.height + amount.y.toDp()).coerceIn(80.dp, 2000.dp),
                                )
                            }
                        },
                )
            }
        }
    }
}
