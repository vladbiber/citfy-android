package com.ctify.player

import android.app.Application
import com.ctify.player.data.MusicRepository
import com.ctify.player.data.db.CtifyDatabase
import com.ctify.player.diag.Diagnostics
import com.ctify.player.net.NetworkGate
import com.ctify.player.radio.RadioRepository

class CtifyApp : Application() {
    val database: CtifyDatabase by lazy { CtifyDatabase.open(this) }
    val repository: MusicRepository by lazy { MusicRepository.get(this, database) }
    val radio: RadioRepository by lazy { RadioRepository(database.radio()) }

    override fun onCreate() {
        super.onCreate()
        Diagnostics.init(this, database.diagnostics())
        NetworkGate.init(this)
    }
}
