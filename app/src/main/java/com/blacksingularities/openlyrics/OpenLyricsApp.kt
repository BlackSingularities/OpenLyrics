package com.blacksingularities.openlyrics

import android.app.Application
import com.blacksingularities.openlyrics.app.LyricsController

/** Jeden kontroler na proces – wspólny dla ekranu i usługi z nakładką. */
class OpenLyricsApp : Application() {
    lateinit var platform: AndroidPlatform
        private set
    lateinit var controller: LyricsController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashLog.install(this)
        platform = AndroidPlatform(this)
        controller = LyricsController(platform)
    }

    companion object {
        lateinit var instance: OpenLyricsApp
            private set
    }
}
