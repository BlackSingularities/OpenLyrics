package com.blacksingularities.openlyrics.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Sesje multimediów wszystkich odtwarzaczy (Spotify, YouTube Music, Poweramp, Samsung Music…).
 * System udostępnia je tylko aplikacjom z dostępem do powiadomień – stąd [MediaListenerService].
 */
class AndroidMedia(private val context: Context) : MediaSource {
    private val _now = MutableStateFlow<NowPlaying?>(null)
    private val _available = MutableStateFlow(false)
    override val nowPlaying: StateFlow<NowPlaying?> = _now
    override val available: StateFlow<Boolean> = _available

    private val main = Handler(Looper.getMainLooper())
    private var manager: MediaSessionManager? = null
    private var controllers: List<MediaController> = emptyList()
    private var active: MediaController? = null
    private val callbacks = HashMap<MediaController, MediaController.Callback>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> onSessions(list.orEmpty()) }

    fun hasAccess(): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(context, MediaListenerService::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** Wywoływane po połączeniu usługi powiadomień. */
    fun attach() {
        main.post {
            val m = context.getSystemService(MediaSessionManager::class.java) ?: return@post
            val component = ComponentName(context, MediaListenerService::class.java)
            runCatching {
                m.addOnActiveSessionsChangedListener(sessionsListener, component, main)
                manager = m
                _available.value = true
                onSessions(m.getActiveSessions(component))
            }.onFailure { _available.value = false }
        }
    }

    fun detach() {
        main.post {
            runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
            callbacks.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
            callbacks.clear()
            controllers = emptyList()
            active = null
            manager = null
            _available.value = hasAccess()
            _now.value = null
        }
    }

    private fun onSessions(list: List<MediaController>) = runCatching { updateSessions(list) }.let { Unit }

    private fun updateSessions(list: List<MediaController>) {
        val tokens = list.map { it.sessionToken }.toSet()
        callbacks.keys.filter { it.sessionToken !in tokens }.forEach { c ->
            callbacks.remove(c)?.let { cb -> runCatching { c.unregisterCallback(cb) } }
        }
        for (c in list) {
            if (callbacks.keys.any { it.sessionToken == c.sessionToken }) continue
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = safePublish()
                override fun onMetadataChanged(metadata: MediaMetadata?) = safePublish()
                override fun onSessionDestroyed() = safePublish()
            }
            runCatching { c.registerCallback(cb, main) }
            callbacks[c] = cb
        }
        controllers = callbacks.keys.toList()
        publish()
    }

    private fun safePublish() {
        runCatching { publish() }
    }

    /** Wybór sesji: grająca; jeśli żadna – ta, która grała ostatnio. */
    private fun publish() {
        val playing = controllers.firstOrNull { runCatching { it.playbackState?.state }.getOrNull() == PlaybackState.STATE_PLAYING }
        val pick = playing ?: active?.takeIf { it in controllers } ?: controllers.firstOrNull()
        active = pick
        // Odtwarzacze podają metadane różnej jakości – błąd jednego nie może zamknąć aplikacji.
        _now.value = pick?.let { runCatching { toNowPlaying(it) }.getOrNull() }
    }

    private fun toNowPlaying(c: MediaController): NowPlaying? {
        val md = c.metadata ?: return null
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return null
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val st = c.playbackState
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val last = st?.lastPositionUpdateTime?.takeIf { it > 0 } ?: nowElapsed
        val label = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString()
        }.getOrDefault(c.packageName)
        return NowPlaying(
            player = label,
            title = title,
            artist = artist,
            album = md.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
            durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
            positionMs = st?.position?.coerceAtLeast(0) ?: 0,
            // Pozycja z PlaybackState odnosi się do zegara elapsedRealtime – przeliczamy na czas ścienny.
            capturedAt = nowWall - (nowElapsed - last),
            playing = st?.state == PlaybackState.STATE_PLAYING,
            rate = st?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            fileHint = listOf(MediaMetadata.METADATA_KEY_MEDIA_URI, MediaMetadata.METADATA_KEY_MEDIA_ID)
                .mapNotNull { k -> md.getString(k) }
                .firstOrNull { it.startsWith("content://") || it.startsWith("file://") || it.startsWith("/") }
                ?.let { if (it.startsWith("/")) "file://$it" else it },
        ).tidy()
    }

    override fun playPause() {
        val c = active ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    override fun next() {
        active?.transportControls?.skipToNext()
    }

    override fun previous() {
        active?.transportControls?.skipToPrevious()
    }

    override fun seekTo(positionMs: Long) {
        active?.transportControls?.seekTo(positionMs)
    }

    init {
        _available.value = hasAccess()
    }
}
