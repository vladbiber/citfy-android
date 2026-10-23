package com.ctify.player.youtube

import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.player.PlayerHub
import com.ctify.player.ui.youtube.ytVideoId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class Chapter(val title: String, val startMs: Long)

/** YouTube chapters of the playing song, empty when it has none or it is not from YouTube. */
object Chapters {
    val current = MutableStateFlow<List<Chapter>>(emptyList())
    private val cache = ConcurrentHashMap<String, List<Chapter>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            PlayerHub.playback.map { it?.track?.ytVideoId() }.distinctUntilChanged().collectLatest { load(it) }
        }
    }

    private suspend fun load(videoId: String?) {
        current.value = emptyList()
        if (videoId == null) return
        cache[videoId]?.let { current.value = it; return }
        if (NetworkGate.blockReason(Feature.YOUTUBE) != null) return
        val list = try {
            YouTubeSource.chapters(videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        cache[videoId] = list
        current.value = list
    }

    /** Index of the chapter playing at [ms], -1 before the first one. */
    fun indexAt(list: List<Chapter>, ms: Long) = list.indexOfLast { it.startMs <= ms }
}
