package com.ctify.player

import android.app.Application
import com.ctify.player.data.MusicRepository
import com.ctify.player.data.db.CtifyDatabase
import com.ctify.player.diag.Diagnostics
import com.ctify.player.net.NetworkGate
import com.ctify.player.radio.RadioRepository
import com.ctify.player.youtube.MusicWatcher
import com.ctify.player.youtube.YtRuntime
import com.ctify.player.youtube.YtSession
import com.ctify.player.youtube.download.Downloads

class CtifyApp : Application() {
    val database: CtifyDatabase by lazy { CtifyDatabase.open(this) }
    val repository: MusicRepository by lazy { MusicRepository.get(this, database) }
    val radio: RadioRepository by lazy { RadioRepository(database.radio(), database.heardSongs()) }

    override fun onCreate() {
        super.onCreate()
        Diagnostics.init(this, database.diagnostics())
        NetworkGate.init(this)
        YtSession.init(this)
        YtRuntime.ensure(this)
        Downloads.init(this, database.downloads())
        MusicWatcher.init(this, database.library(), repository)
        com.ctify.player.youtube.Chapters.start()
    }
}
