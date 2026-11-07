package com.blacksingularities.openlyrics.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openlyrics.i18n.tr
import com.blacksingularities.openlyrics.platform.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Zgłoszenia do redzikowski.com (Android i Windows):
 *  - raport o błędzie: awaria zapisana przez [install], proponowana przy następnym starcie
 *    (→ „Raporty błędów” w panelu),
 *  - zgłoszenie błędu albo sugestia funkcjonalności (→ „Sugestie i zgłoszenia”).
 * E-mail jest opcjonalny; nic nie wychodzi bez decyzji użytkownika.
 */
object LyricsFeedback {
    private const val ENDPOINT = "https://redzikowski.com/api/feedback"
    private const val FILE = "feedback-pending-crash.txt"

    private fun file(platform: Platform) = File(platform.dataDir, FILE)

    fun install(platform: Platform) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
                file(platform).writeText("${platform.appVersion}\nWątek: ${thread.name}\n${sw.toString().take(60_000)}")
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pendingCrash(platform: Platform): Pair<String, String>? = runCatching {
        val f = file(platform).takeIf { it.isFile } ?: return null
        val text = f.readText()
        text.substringBefore('\n') to text.substringAfter('\n')
    }.getOrNull()

    fun discardCrash(platform: Platform) {
        runCatching { file(platform).delete() }
    }

    private val android get() = System.getProperty("java.vendor").orEmpty().contains("Android", ignoreCase = true)

    private fun osDescription(platform: Platform): String =
        if (android) runCatching {
            val build = Class.forName("android.os.Build\$VERSION")
            "Android ${build.getField("RELEASE").get(null)} (API ${build.getField("SDK_INT").get(null)})"
        }.getOrDefault("Android")
        else "${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})"

    private fun deviceDescription(): String =
        if (android) runCatching {
            val build = Class.forName("android.os.Build")
            "${build.getField("MANUFACTURER").get(null)} ${build.getField("MODEL").get(null)}"
        }.getOrDefault("")
        else "Java ${System.getProperty("java.version")} · ${Runtime.getRuntime().availableProcessors()} CPU"

    suspend fun send(
        platform: Platform,
        kind: String,
        message: String,
        email: String,
        diagnostics: Boolean = true,
        stack: String = "",
        version: String = platform.appVersion,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                put("kind", kind)
                put("app", "openlyrics")
                put("appName", "OpenLyrics")
                put("appVersion", version)
                put("platform", if (android) "android" else "windows")
                put("locale", Locale.getDefault().toLanguageTag())
                put("message", message.trim())
                put("email", email.trim())
                if (stack.isNotBlank()) put("stack", stack)
                if (diagnostics || kind == "crash") {
                    put("os", osDescription(platform))
                    put("device", deviceDescription())
                }
            }.toString()
            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code !in 200..299) error("HTTP $code")
            } finally {
                conn.disconnect()
            }
        }
    }
}

/** Formularz zgłoszenia błędu albo sugestii. */
@Composable
fun FeedbackDialog(platform: Platform, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var feature by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var diagnostics by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(tr("Zgłoś błąd lub sugestię")) },
        text = {
            if (sent) {
                Text(tr("Dziękuję! Zgłoszenie dotarło."))
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !feature, onClick = { feature = false }, label = { Text(tr("Błąd")) })
                        FilterChip(selected = feature, onClick = { feature = true }, label = { Text(tr("Sugestia")) })
                    }
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it.take(8000); error = null },
                        placeholder = { Text(if (feature) tr("Czego brakuje albo co można zrobić lepiej?") else tr("Co się stało? Co robiłeś tuż przed błędem?")) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        enabled = !sending,
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.take(200) },
                        label = { Text(tr("E-mail (opcjonalnie)")) },
                        supportingText = { Text(tr("Tylko jeśli chcesz dostać odpowiedź")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !sending,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = diagnostics, onCheckedChange = { diagnostics = it }, enabled = !sending)
                        Text(tr("Dołącz wersję aplikacji i dane urządzenia"), style = MaterialTheme.typography.bodySmall)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            if (sent) {
                TextButton(onClick = onDismiss) { Text(tr("Zamknij")) }
            } else {
                TextButton(
                    enabled = !sending,
                    onClick = {
                        if (message.trim().length < 3) {
                            error = tr("Opisz problem w kilku słowach.")
                            return@TextButton
                        }
                        sending = true
                        scope.launch {
                            LyricsFeedback.send(platform, if (feature) "feature" else "bug", message, email, diagnostics)
                                .onSuccess { sent = true }
                                .onFailure { error = tr("Nie udało się wysłać. Sprawdź połączenie i spróbuj ponownie.") }
                            sending = false
                        }
                    },
                ) {
                    if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(tr("Wyślij"))
                }
            }
        },
        dismissButton = { if (!sent) TextButton(onClick = onDismiss, enabled = !sending) { Text(tr("Anuluj")) } },
    )
}

/** Pytanie o wysłanie raportu po awarii z poprzedniego uruchomienia. */
@Composable
fun CrashReportPrompt(platform: Platform) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var crash by remember { mutableStateOf<Pair<String, String>?>(null) }
    var comment by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var details by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { crash = LyricsFeedback.pendingCrash(platform) }
    val (version, stack) = crash ?: return

    AlertDialog(
        onDismissRequest = {},
        title = { Text(tr("OpenLyrics napotkał błąd")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Ostatnio aplikacja zakończyła działanie przez błąd. Wysłać autorowi raport? Zawiera opis błędu, wersję aplikacji i dane urządzenia – bez Twoich plików."))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(4000) },
                    placeholder = { Text(tr("Co robiłeś przed awarią? (opcjonalnie)")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !sending,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.take(200) },
                    label = { Text(tr("E-mail (opcjonalnie)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !sending,
                )
                Row {
                    TextButton(onClick = { details = !details }) { Text(if (details) tr("Ukryj szczegóły") else tr("Pokaż szczegóły")) }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(stack)) }) { Text(tr("Kopiuj")) }
                }
                if (details) Text(stack.lines().take(40).joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !sending,
                onClick = {
                    sending = true
                    scope.launch {
                        LyricsFeedback.send(platform, "crash", comment, email, stack = stack, version = version)
                            .onSuccess { LyricsFeedback.discardCrash(platform); crash = null }
                            .onFailure { error = tr("Nie udało się wysłać. Sprawdź połączenie i spróbuj ponownie.") }
                        sending = false
                    }
                },
            ) {
                if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(tr("Wyślij raport"))
            }
        },
        dismissButton = {
            TextButton(enabled = !sending, onClick = { LyricsFeedback.discardCrash(platform); crash = null }) { Text(tr("Nie wysyłaj")) }
        },
    )
}
