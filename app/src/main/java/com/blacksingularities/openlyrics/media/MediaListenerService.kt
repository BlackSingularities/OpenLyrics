package com.blacksingularities.openlyrics.media

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.graphics.Canvas
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.compositionContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.blacksingularities.openlyrics.CrashLog
import com.blacksingularities.openlyrics.OpenLyricsApp
import com.blacksingularities.openlyrics.data.OverlayBounds
import com.blacksingularities.openlyrics.ui.LyricsOverlay
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Usługa powiadomień – daje dostęp do sesji multimediów i żyje tak długo jak system ją
 * utrzymuje, więc to ona trzyma nakładkę z tekstem nad innymi aplikacjami.
 */
class MediaListenerService : NotificationListenerService() {
    private var scope: CoroutineScope? = null
    private var overlay: OverlayWindow? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        val app = OpenLyricsApp.instance
        app.platform.media.attach()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        val c = app.controller
        // Nakładka widoczna, gdy jest włączona, coś gra i mamy uprawnienie do rysowania nad aplikacjami.
        s.launch {
            combine(c.settingsFlow, app.platform.media.nowPlaying) { st, np -> Triple(st.overlayEnabled, st.overlayLocked, np != null) }
                .distinctUntilChanged()
                .collect { (enabled, locked, playing) ->
                    val can = Settings.canDrawOverlays(this@MediaListenerService)
                    runCatching {
                        if (enabled && playing && can) {
                            val o = overlay ?: OverlayWindow(this@MediaListenerService).also { overlay = it }
                            o.show(locked)
                        } else {
                            overlay?.hide()
                        }
                    }.onFailure { e ->
                        CrashLog.record(this@MediaListenerService, e, "nakładka – otwieranie")
                        runCatching { overlay?.hide() }
                        overlay = null
                    }
                }
        }
    }

    override fun onListenerDisconnected() {
        teardown()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        overlay?.hide()
        overlay = null
        scope?.cancel()
        scope = null
        runCatching { OpenLyricsApp.instance.platform.media.detach() }
    }
}

/** Okno nakładki: ComposeView w WindowManager z własnym cyklem życia. */
private class OverlayWindow(private val context: Context) : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: SafeFrame? = null
    private var params: WindowManager.LayoutParams? = null
    private var locked = false
    private var recomposer: Recomposer? = null

    init {
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    private fun density() = context.resources.displayMetrics.density

    @SuppressLint("ClickableViewAccessibility")
    fun show(lock: Boolean) {
        locked = lock
        val app = OpenLyricsApp.instance
        val c = app.controller
        val st = c.settings
        val p = params ?: WindowManager.LayoutParams(
            overlayWidth(),
            overlayHeight(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Bez FLAG_HARDWARE_ACCELERATED okno dodane do WindowManagera rysuje się na płótnie
            // programowym: warstwy Compose, wygaszanie krawędzi (BlendMode.DstIn) i cienie liter
            // wychodzą wtedy przezroczyste, a tekst niemal niewidoczny.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val b = st.phoneBounds
            y = if (b.y >= 0) b.y else (context.resources.displayMetrics.heightPixels * 0.62f).roundToInt()
            x = if (b.x >= 0 && b.width > 0) b.x else 0
            if (Build.VERSION.SDK_INT >= 31) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        params = p
        applyLock(p)
        val existing = view
        if (existing != null) {
            runCatching { wm.updateViewLayout(existing, p) }
            return
        }
        // Błąd w kompozycji, pomiarze albo rysowaniu nakładki wyłącza tylko nakładkę – nie całą aplikację.
        val failed = CoroutineExceptionHandler { _, e -> fail(e, "nakładka – kompozycja") }
        val compose = ComposeView(context).apply {
            val r = Recomposer(AndroidUiDispatcher.CurrentThread + failed)
            recomposer = r
            compositionContext = r
            CoroutineScope(AndroidUiDispatcher.CurrentThread + failed).launch { r.runRecomposeAndApplyChanges() }
            // Nazwa inna niż ComposeView.Content() – wewnątrz apply tamta wywoływałaby samą siebie.
            setContent { OverlayContent() }
        }
        val v = SafeFrame(context) { e -> fail(e, "nakładka – rysowanie") }.apply {
            setViewTreeLifecycleOwner(this@OverlayWindow)
            setViewTreeSavedStateRegistryOwner(this@OverlayWindow)
            addView(compose)
        }
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        runCatching { wm.addView(v, p) }.onSuccess { view = v }
    }

    /** Zapisana wysokość, a bez niej wyliczona z liczby widocznych wierszy. */
    private fun overlayHeight(): Int {
        val s = OpenLyricsApp.instance.controller.settings
        s.phoneBounds.height.takeIf { it > 0 }?.let { return it }
        val st = s.style
        val lines = st.visibleLines.coerceIn(1, 15)
        val header = if (st.showHeader) st.fontSize * 1.2f else 0f
        return ((lines * st.fontSize * 1.7f + header + 24) * density()).roundToInt()
    }

    /** Zapisana szerokość, a bez niej pełna szerokość ekranu. */
    private fun overlayWidth(): Int = OpenLyricsApp.instance.controller.settings.phoneBounds.width
        .takeIf { it > 0 } ?: WindowManager.LayoutParams.MATCH_PARENT

    private fun minSize() = (56 * density()).roundToInt()

    /**
     * Zablokowana nakładka nie przyjmuje dotyku. Od Androida 12 dotyk przechodzi przez okno innej
     * aplikacji tylko wtedy, gdy jest ono co najwyżej w 80% kryjące – stąd alpha 0,8.
     */
    private fun applyLock(p: WindowManager.LayoutParams) {
        p.height = overlayHeight()
        p.width = overlayWidth()
        if (locked) {
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            p.alpha = 0.8f
        } else {
            p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            p.alpha = 1f
        }
    }

    @Composable
    private fun OverlayContent() {
        val c = OpenLyricsApp.instance.controller
        val s = c.settings
        val track = c.track
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(s.overlayLocked) {
                    if (s.overlayLocked) return@pointerInput
                    detectDragGestures(onDragEnd = { saveBounds() }) { change, amount ->
                        change.consume()
                        move(amount.x.roundToInt(), amount.y.roundToInt())
                    }
                },
        ) {
            LyricsOverlay(
                track = track,
                state = c.lyricsState,
                style = s.style,
                offsetMs = c.offsetFor(track),
                frame = !s.overlayLocked,
                hideWhenPaused = s.hideWhenPaused,
                hideWhenNoLyrics = s.hideWhenNoLyrics,
            )
            // Róg do zmiany rozmiaru – tylko gdy nakładka nie jest zablokowana.
            if (!s.overlayLocked) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(34.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(topStart = 14.dp, bottomEnd = 14.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(onDragEnd = { saveBounds() }) { change, amount ->
                                change.consume()
                                resize(amount.x.roundToInt(), amount.y.roundToInt())
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.OpenInFull, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                }
            }
        }
        // Zmiana stylu (liczba wierszy, rozmiar) – nowa wysokość okna, dopóki nie ustawiono własnej.
        androidx.compose.runtime.LaunchedEffect(s.style.visibleLines, s.style.fontSize, s.style.showHeader, s.phoneBounds.height, s.phoneBounds.width) {
            val p = params ?: return@LaunchedEffect
            p.height = overlayHeight()
            p.width = overlayWidth()
            view?.let { runCatching { wm.updateViewLayout(it, p) } }
        }
    }

    /** Przesunięcie okna; w poziomie tylko wtedy, gdy nakładka nie zajmuje całej szerokości. */
    private fun move(dx: Int, dy: Int) {
        val p = params ?: return
        val m = context.resources.displayMetrics
        p.y = (p.y + dy).coerceIn(0, (m.heightPixels - p.height).coerceAtLeast(0))
        if (p.width != WindowManager.LayoutParams.MATCH_PARENT) {
            p.x = (p.x + dx).coerceIn(0, (m.widthPixels - p.width).coerceAtLeast(0))
        }
        view?.let { runCatching { wm.updateViewLayout(it, p) } }
    }

    /** Zmiana rozmiaru rogiem: pierwszy ruch utrwala obecną szerokość, dalej rośnie i maleje swobodnie. */
    private fun resize(dx: Int, dy: Int) {
        val p = params ?: return
        val m = context.resources.displayMetrics
        val min = minSize()
        val current = if (p.width == WindowManager.LayoutParams.MATCH_PARENT) m.widthPixels else p.width
        p.width = (current + dx).coerceIn(min, (m.widthPixels - p.x).coerceAtLeast(min))
        p.height = (p.height + dy).coerceIn(min, (m.heightPixels - p.y).coerceAtLeast(min))
        view?.let { runCatching { wm.updateViewLayout(it, p) } }
    }

    private fun saveBounds() {
        val p = params ?: return
        val width = if (p.width == WindowManager.LayoutParams.MATCH_PARENT) 0 else p.width
        OpenLyricsApp.instance.controller.updateSettings { it.copy(phoneBounds = OverlayBounds(p.x, p.y, width, p.height)) }
    }

    private fun fail(e: Throwable, where: String) {
        CrashLog.record(context, e, where)
        // Po awarii kompozycja jest w nieznanym stanie – zamykamy okno; przy kolejnej zmianie utworu powstanie nowe.
        android.os.Handler(android.os.Looper.getMainLooper()).post { hide() }
    }

    fun hide() {
        val v = view ?: return
        runCatching { wm.removeView(v) }
        view = null
        recomposer?.cancel()
        recomposer = null
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }
}

/** Kontener nakładki przechwytujący wyjątki z pomiaru, układu, rysowania i dotyku. */
private class SafeFrame(context: Context, private val onError: (Throwable) -> Unit) : FrameLayout(context) {
    private var broken = false

    private inline fun guard(block: () -> Unit) {
        if (broken) return
        try {
            block()
        } catch (e: Throwable) {
            broken = true
            onError(e)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        try {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        } catch (e: Throwable) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
            if (!broken) {
                broken = true
                onError(e)
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) = guard { super.onLayout(changed, left, top, right, bottom) }

    override fun dispatchDraw(canvas: Canvas) = guard { super.dispatchDraw(canvas) }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        var handled = false
        guard { handled = super.dispatchTouchEvent(ev) }
        return handled
    }
}
