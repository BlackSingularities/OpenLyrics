package com.blacksingularities.openlyrics

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.blacksingularities.openlyrics.media.MediaListenerService
import com.blacksingularities.openlyrics.ui.MainScreen
import com.blacksingularities.openlyrics.update.UpdateManager

class MainActivity : ComponentActivity() {
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        OpenLyricsApp.instance.controller.refreshSetup()
    }
    private val notifyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = OpenLyricsApp.instance
        app.platform.requestAudioPermission = {
            audioPermission.launch(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        UpdateManager.clearUpdatedNotification(this)
        if (Build.VERSION.SDK_INT >= 33) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { MainScreen(app.controller) }
        CrashLog.showIfAny(this)
    }

    override fun onResume() {
        super.onResume()
        val app = OpenLyricsApp.instance
        app.controller.refreshSetup()
        // Po nadaniu dostępu do powiadomień system czasem nie łączy usługi od razu – prosimy o to.
        if (app.platform.media.hasAccess()) {
            runCatching { NotificationListenerService.requestRebind(ComponentName(this, MediaListenerService::class.java)) }
        }
    }
}
