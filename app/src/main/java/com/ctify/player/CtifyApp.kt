package com.ctify.player

import android.app.Application
import com.ctify.player.data.MusicRepository

class CtifyApp : Application() {
    val repository: MusicRepository by lazy { MusicRepository.get(this) }
}
