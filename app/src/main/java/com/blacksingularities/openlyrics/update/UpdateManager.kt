package com.blacksingularities.openlyrics.update

import com.blacksingularities.openlyrics.i18n.tr
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.blacksingularities.openlyrics.BuildConfig
import com.blacksingularities.openlyrics.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Aktualizacje z redzikowski.com: pobrany plik APK instalowany
 * przez PackageInstaller. Od Androida 12, gdy aplikacja sama zainstalowała poprzednią wersję,
 * system może zaktualizować ją bez dodatkowego potwierdzenia.
 */
object UpdateManager {
    const val ACTION_STATUS = "com.blacksingularities.openlyrics.UPDATE_STATUS"
    const val RESTART_KEY = "update.restart"
    private const val CHANNEL = "updates"
    const val NOTIFICATION_ID = 4711

    /**
     * Po aktualizacji: otwiera aplikację ponownie. Android 10+ może zablokować otwarcie ekranu
     * z tła – wtedy zostaje powiadomienie „dotknij, aby otworzyć” (usuwane po starcie aplikacji).
     */
    fun relaunchAfterUpdate(context: Context) {
        val prefs = context.getSharedPreferences("openlyrics_update", Context.MODE_PRIVATE)
        val requested = prefs.getLong(RESTART_KEY, 0L)
        if (requested == 0L || System.currentTimeMillis() - requested > 30 * 60_000L) return
        prefs.edit().remove(RESTART_KEY).apply()
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) ?: return
        runCatching { context.startActivity(launch) }
        notifyUpdated(context, launch)
    }

    private fun notifyUpdated(context: Context, launch: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            android.app.NotificationChannel(CHANNEL, tr("Aktualizacje"), android.app.NotificationManager.IMPORTANCE_HIGH),
        )
        val pending = PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(tr("OpenLyrics zaktualizowany do {0}", BuildConfig.VERSION_NAME))
            .setContentText(tr("Dotknij, aby otworzyć aplikację"))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, notification) }
    }

    fun clearUpdatedNotification(context: Context) {
        runCatching { context.getSystemService(android.app.NotificationManager::class.java)?.cancel(NOTIFICATION_ID) }
    }

    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    /**
     * Cicha aktualizacja (bez okna systemu) jest możliwa od Androida 12, gdy bieżącą wersję
     * zainstalowała sama aplikacja. Pierwszą wersję instaluje zwykle przeglądarka – wtedy
     * potrzebne jest jedno potwierdzenie na ekranie.
     */
    fun canInstallSilently(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31 || !canInstall(context)) return false
        val installer = runCatching {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        }.getOrNull()
        return installer == context.packageName
    }

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    suspend fun install(context: Context, file: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        // Znacznik dla nowej wersji: po instalacji uruchom aplikację ponownie.
        context.getSharedPreferences("openlyrics_update", Context.MODE_PRIVATE).edit()
            .putLong(RESTART_KEY, System.currentTimeMillis()).apply()
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, UpdateReceiver::class.java).setAction(ACTION_STATUS)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, id, intent, flags)
            session.commit(pending.intentSender)
        }
    }
}

/** Odbiera status instalacji; gdy system wymaga potwierdzenia – pokazuje jego okno. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateManager.relaunchAfterUpdate(context)
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: tr("kod {0}", status)
                if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                    Toast.makeText(context, tr("Aktualizacja nieudana: {0}", msg), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

/** Nowa wersja zainstalowana – system wysyła to do zaktualizowanej aplikacji. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) UpdateManager.relaunchAfterUpdate(context)
    }
}
