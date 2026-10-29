package com.blacksingularities.openlyrics.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blacksingularities.openlyrics.data.AppSettings
import com.blacksingularities.openlyrics.data.SettingsStore
import com.blacksingularities.openlyrics.i18n.I18n
import com.blacksingularities.openlyrics.i18n.tr
import com.blacksingularities.openlyrics.lyrics.LrcLib
import com.blacksingularities.openlyrics.lyrics.Lyrics
import com.blacksingularities.openlyrics.lyrics.LyricsCache
import com.blacksingularities.openlyrics.lyrics.LyricsCandidate
import com.blacksingularities.openlyrics.lyrics.LyricsSource
import com.blacksingularities.openlyrics.lyrics.Lrc
import com.blacksingularities.openlyrics.media.NowPlaying
import com.blacksingularities.openlyrics.net.Http
import com.blacksingularities.openlyrics.platform.LocatedTrack
import com.blacksingularities.openlyrics.platform.Platform
import com.blacksingularities.openlyrics.platform.SetupItem
import com.blacksingularities.openlyrics.tags.TagReader
import com.blacksingularities.openlyrics.update.ReleaseInfo
import com.blacksingularities.openlyrics.update.UPDATE_API
import com.blacksingularities.openlyrics.update.UpdateState
import com.blacksingularities.openlyrics.update.isNewer
import com.blacksingularities.openlyrics.update.parseRelease
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Stan wyszukiwania tekstu dla bieżącego utworu. */
sealed interface LyricsState {
    data object Idle : LyricsState
    data class Searching(val step: String) : LyricsState
    data class Ready(val lyrics: Lyrics) : LyricsState
    data object NotFound : LyricsState
    data class Failed(val message: String) : LyricsState
}

/** Wyniki ręcznego wyszukiwania. */
sealed interface SearchState {
    data object Idle : SearchState
    data object Searching : SearchState
    data class Results(val items: List<LyricsCandidate>) : SearchState
    data class Failed(val message: String) : SearchState
}

/**
 * Serce aplikacji, wspólne dla Androida i Windows: śledzi odtwarzany utwór, szuka tekstu
 * (osadzony w pliku → plik .lrc obok → pamięć podręczna → LRCLIB) i trzyma ustawienia.
 */
class LyricsController(val platform: Platform) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = SettingsStore(File(platform.dataDir, "settings.json"))
    val cache = LyricsCache(File(platform.dataDir, "lyrics"))

    var settings by mutableStateOf(store.load())
        private set
    private val _settingsFlow = MutableStateFlow(settings)
    val settingsFlow: StateFlow<AppSettings> = _settingsFlow

    var track by mutableStateOf<NowPlaying?>(null)
        private set
    var lyricsState by mutableStateOf<LyricsState>(LyricsState.Idle)
        private set
    /** Plik utworu, jeśli został odnaleziony. */
    var located by mutableStateOf<LocatedTrack?>(null)
        private set
    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    var search by mutableStateOf<SearchState>(SearchState.Idle)
        private set
    var setup by mutableStateOf<List<SetupItem>>(emptyList())
        private set
    var mediaAvailable by mutableStateOf(true)
        private set
    /** Krótki komunikat na dole ekranu. */
    var toast by mutableStateOf<String?>(null)
    var cacheCount by mutableStateOf(0)
        private set

    private val _lyricsFlow = MutableStateFlow<LyricsState>(LyricsState.Idle)
    val lyricsFlow: StateFlow<LyricsState> = _lyricsFlow

    private var resolveJob: Job? = null
    private var updateJob: Job? = null
    private var toastJob: Job? = null

    init {
        Http.userAgent = "OpenLyrics/${platform.appVersion} (${platform.name}; +https://redzikowski.com)"
        I18n.apply(settings.language, platform.systemLanguage)
        scope.launch {
            platform.media.nowPlaying.collect { np -> track = np }
        }
        scope.launch {
            platform.media.available.collect { mediaAvailable = it }
        }
        // Nowy utwór (inny wykonawca/tytuł) – szukamy tekstu od nowa.
        scope.launch {
            platform.media.nowPlaying
                .map { it?.takeIf { t -> t.title.isNotBlank() } }
                .distinctUntilChanged { a, b -> a?.key == b?.key && a?.durationMs == b?.durationMs }
                .collect { np -> resolve(np) }
        }
        scope.launch(Dispatchers.IO) { cacheCount = cache.count() }
        refreshSetup()
        if (settings.autoUpdate) {
            scope.launch {
                delay(4_000)
                checkUpdate(silent = true)
            }
        }
    }

    // ------------------------------------------------------------------ ustawienia

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val old = settings
        val s = transform(old)
        if (s == old) return
        settings = s
        _settingsFlow.value = s
        if (s.language != old.language) I18n.apply(s.language, platform.systemLanguage)
        scope.launch(Dispatchers.IO) { store.save(s) }
    }

    fun refreshSetup() {
        setup = runCatching { platform.setupItems() }.getOrDefault(emptyList())
    }

    /** Łączne przesunięcie tekstu dla bieżącego utworu (ms, dodatnie – tekst wcześniej). */
    fun offsetFor(np: NowPlaying?): Long = settings.globalOffsetMs + (np?.let { settings.trackOffsets[it.key] } ?: 0L)

    fun nudgeOffset(deltaMs: Long) {
        val np = track ?: return
        updateSettings { s ->
            val v = (s.trackOffsets[np.key] ?: 0L) + deltaMs
            s.copy(trackOffsets = if (v == 0L) s.trackOffsets - np.key else s.trackOffsets + (np.key to v))
        }
        showToast(tr("Przesunięcie: {0} s", formatOffset(offsetFor(track))))
    }

    fun resetTrackOffset() {
        val np = track ?: return
        updateSettings { it.copy(trackOffsets = it.trackOffsets - np.key) }
    }

    fun showToast(text: String) {
        toast = text
        toastJob?.cancel()
        toastJob = scope.launch {
            delay(2_200)
            toast = null
        }
    }

    // ------------------------------------------------------------------ teksty

    private fun setLyrics(state: LyricsState) {
        lyricsState = state
        _lyricsFlow.value = state
    }

    fun reload() {
        val np = track ?: return
        track?.let { cache.remove(it.key) }
        resolve(np, forceOnline = true)
    }

    private fun resolve(np: NowPlaying?, forceOnline: Boolean = false) {
        resolveJob?.cancel()
        located = null
        search = SearchState.Idle
        if (np == null) {
            setLyrics(LyricsState.Idle)
            return
        }
        setLyrics(LyricsState.Searching(tr("Szukanie pliku utworu…")))
        resolveJob = scope.launch(Dispatchers.IO) {
            // Przy chwilowej awarii sieci lub LRCLIB ponawiamy w tle, dopóki gra ten sam utwór.
            var attempt = 0
            while (isActive) {
                try {
                    val result = findLyrics(np, forceOnline)
                    if (!isActive) return@launch
                    setLyrics(if (result != null && !result.isEmpty) LyricsState.Ready(result) else LyricsState.NotFound)
                    return@launch
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    attempt++
                    val wait = (10_000L * attempt).coerceAtMost(60_000)
                    setLyrics(LyricsState.Failed(tr("{0} – ponowna próba za {1} s", e.message ?: e.toString(), wait / 1000)))
                    delay(wait)
                }
            }
        }
    }

    private suspend fun findLyrics(np: NowPlaying, forceOnline: Boolean): Lyrics? {
        // Ręcznie wybrany tekst ma pierwszeństwo przed wszystkim.
        val cached = cache.get(np.key)
        if (cached?.source == LyricsSource.MANUAL && !forceOnline) return cached

        val file = runCatching { platform.locate(np) }.getOrNull()
        located = file
        var embedded: Lyrics? = null
        if (file != null) {
            setLyrics(LyricsState.Searching(tr("Odczyt tekstu z pliku…")))
            embedded = runCatching { file.open().use { TagReader.read(it, file.name)?.lyrics } }.getOrNull()
                ?.takeIf { !it.isEmpty }
                ?.copy(origin = file.path ?: file.name)
            if (embedded == null || !embedded.synced) {
                val side = runCatching { file.readSidecar() }.getOrNull()
                if (!side.isNullOrBlank()) {
                    val l = Lrc.parse(side, LyricsSource.SIDECAR, (file.path ?: file.name).substringBeforeLast('.') + ".lrc")
                    if (!l.isEmpty && (embedded == null || l.synced)) embedded = l
                }
            }
        }
        if (embedded != null && (embedded.synced || !settings.preferSynced || !settings.searchOnline)) return embedded
        if (cached != null && !forceOnline && (cached.synced || embedded == null)) return cached

        if (!settings.searchOnline) return embedded ?: cached
        if (!forceOnline && cache.recentlyMissing(np.key)) return embedded ?: cached

        setLyrics(LyricsState.Searching(tr("Szukanie w LRCLIB…")))
        val online = runCatching {
            retrying {
                LrcLib.get(np)?.takeIf { it.hasSynced || it.instrumental || !it.plain.isNullOrBlank() }
                    ?: LrcLib.best(np, LrcLib.search(np.artist, np.title))
                    ?: LrcLib.best(np, LrcLib.search("${np.artist} ${np.title}"))
            }
        }.getOrElse { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (embedded != null || cached != null) null else throw IllegalStateException(tr("Brak połączenia z LRCLIB: {0}", e.message ?: ""))
        }
        if (online == null) {
            if (embedded == null) cache.markMissing(np.key)
            return embedded ?: cached
        }
        val lyrics = online.toLyrics()
        // Tekst bez czasów z pliku jest równie dobry jak taki sam z sieci.
        if (embedded != null && !lyrics.synced && !lyrics.instrumental) return embedded
        cache.put(np.key, lyrics)
        cacheCount = cache.count()
        if (settings.saveSidecar && lyrics.synced) writeSidecar(file, np, lyrics, quiet = true)
        if (settings.saveEmbedded && lyrics.synced) writeEmbedded(file, np, lyrics, quiet = true)
        return lyrics
    }

    /** Kilka szybkich prób – LRCLIB czasem odpowiada 502/503 przez kilka sekund. */
    private suspend fun <T> retrying(block: () -> T): T {
        var last: Throwable? = null
        for (wait in longArrayOf(0, 1_500, 4_000)) {
            if (wait > 0) delay(wait)
            try {
                return block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                last = e
            }
        }
        throw last!!
    }

    private fun writeSidecar(file: LocatedTrack?, np: NowPlaying, lyrics: Lyrics, quiet: Boolean): Boolean {
        val w = file?.writeSidecar ?: return false
        val ok = runCatching { w(lyrics.toLrc(np.title, np.artist)) }.getOrDefault(false)
        if (!quiet) showToast(if (ok) tr("Zapisano plik .lrc obok utworu") else tr("Nie można zapisać pliku .lrc"))
        return ok
    }

    private fun writeEmbedded(file: LocatedTrack?, np: NowPlaying, lyrics: Lyrics, quiet: Boolean): Boolean {
        val w = file?.writeEmbedded ?: return false
        val ok = runCatching { w(lyrics.toLrc(np.title, np.artist)) }.getOrDefault(false)
        if (!quiet) showToast(if (ok) tr("Tekst zapisany wewnątrz pliku") else tr("Nie można zapisać tekstu w pliku"))
        return ok
    }

    fun saveEmbeddedNow() {
        val np = track ?: return
        val l = (lyricsState as? LyricsState.Ready)?.lyrics ?: return
        scope.launch(Dispatchers.IO) { writeEmbedded(located, np, l, quiet = false) }
    }

    fun saveSidecarNow() {
        val np = track ?: return
        val l = (lyricsState as? LyricsState.Ready)?.lyrics ?: return
        scope.launch(Dispatchers.IO) { writeSidecar(located, np, l, quiet = false) }
    }

    fun searchManually(query: String) {
        search = SearchState.Searching
        scope.launch(Dispatchers.IO) {
            search = runCatching { SearchState.Results(LrcLib.search(query).sortedByDescending { it.hasSynced }) }
                .getOrElse { SearchState.Failed(it.message ?: it.toString()) }
        }
    }

    fun clearSearch() {
        search = SearchState.Idle
    }

    /** Ręczny wybór tekstu – zapamiętany na stałe dla tego utworu. */
    fun choose(candidate: LyricsCandidate) {
        val np = track ?: return
        val l = candidate.toLyrics().copy(source = LyricsSource.MANUAL)
        cache.put(np.key, l)
        cacheCount = cache.count()
        setLyrics(LyricsState.Ready(l))
        search = SearchState.Idle
        showToast(tr("Tekst zapamiętany dla tego utworu"))
    }

    fun forgetTrack() {
        val np = track ?: return
        cache.remove(np.key)
        cacheCount = cache.count()
        resolve(np)
    }

    fun clearCache() {
        scope.launch(Dispatchers.IO) {
            val n = cache.clear()
            cacheCount = 0
            showToast(tr("Usunięto {0} zapisanych tekstów", n))
        }
    }

    // ------------------------------------------------------------------ aktualizacje

    fun checkUpdate(silent: Boolean = false) {
        if (update is UpdateState.Checking || update is UpdateState.Downloading) return
        update = UpdateState.Checking
        scope.launch(Dispatchers.IO) {
            update = try {
                val body = Http.get(UPDATE_API, "application/vnd.github+json") ?: error(tr("Brak wydań"))
                val info = parseRelease(body, platform.updateAssetSuffix)
                if (isNewer(info.version, platform.appVersion)) UpdateState.Available(info) else UpdateState.UpToDate
            } catch (e: Throwable) {
                if (silent) UpdateState.Idle else UpdateState.Failed(e.message ?: e.toString())
            }
        }
    }

    fun updateNow() {
        val info = (update as? UpdateState.Available)?.info ?: return
        if (!platform.canSelfUpdate) {
            platform.openUri(info.pageUrl)
            return
        }
        download(info)
    }

    private fun download(info: ReleaseInfo) {
        updateJob?.cancel()
        updateJob = scope.launch(Dispatchers.IO) {
            try {
                val dir = platform.updateDir.apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val name = info.assetName.ifEmpty { "OpenLyrics-${info.version}${platform.updateAssetSuffix}" }.replace(Regex("[^\\w.\\-]"), "_")
                val target = File(dir, name)
                update = UpdateState.Downloading(info, 0f)
                Http.download(info.assetUrl, target, info.sizeBytes, { !isActive }) { p -> update = UpdateState.Downloading(info, p) }
                update = UpdateState.Ready(info, target)
                installUpdate()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                update = UpdateState.Failed(e.message ?: e.toString())
            }
        }
    }

    fun installUpdate() {
        val ready = update as? UpdateState.Ready ?: return
        update = UpdateState.Installing(ready.info)
        scope.launch {
            try {
                withContext(Dispatchers.IO) { platform.installUpdate(ready.file) }
                // Na Androidzie system może poprosić o potwierdzenie – przycisk zostaje dostępny.
                delay(8_000)
                if (update is UpdateState.Installing) update = ready
            } catch (e: Throwable) {
                update = UpdateState.Failed(e.message ?: e.toString())
            }
        }
    }

    fun skipUpdate() {
        val info = (update as? UpdateState.Available)?.info ?: return
        updateSettings { it.copy(skippedVersion = info.version) }
        update = UpdateState.Idle
    }

    companion object {
        fun formatOffset(ms: Long): String {
            val sign = if (ms > 0) "+" else if (ms < 0) "−" else "±"
            val a = kotlin.math.abs(ms)
            return sign + (a / 1000) + "," + ((a % 1000) / 100)
        }
    }
}
