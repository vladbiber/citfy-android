package com.ctify.player.recommend

import android.content.Context
import com.ctify.player.data.MusicRepository
import com.ctify.player.data.Track
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.ui.youtube.toYtSong
import com.ctify.player.ui.youtube.ytVideoId
import com.ctify.player.youtube.SongMatcher
import com.ctify.player.youtube.YouTubeSource
import com.ctify.player.youtube.YtSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class Shelf(val title: String) {
    FOR_YOU("For you"),
    PLAYED("From what you played"),
    LIKED("From what you liked"),
    DOWNLOADS("From your downloads"),
}

/**
 * Home's song-radio shelves: the YouTube mix of a few seed songs per shelf, cleaned by [RadioMixer].
 * Kept in memory and in a JSON file for [TTL_MS], so opening Home doesn't ask YouTube every time.
 */
object Recommendations {
    private const val TTL_MS = 12 * 60 * 60 * 1000L
    private const val RETRY_MS = 15 * 60 * 1000L
    private const val SEEDS = 3
    private const val PER_SHELF = 30

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _shelves = MutableStateFlow<Map<Shelf, List<YtSong>>>(emptyMap())
    val shelves: StateFlow<Map<Shelf, List<YtSong>>> = _shelves.asStateFlow()
    private var savedAt = 0L
    private var triedAt = 0L
    private var loaded = false
    private var job: Job? = null
    // local files matched to a video once per session; "" = no match
    private val matched = ConcurrentHashMap<String, String>()

    /** Shows the cached shelves and refreshes them in the background once they are stale. */
    fun refresh(context: Context, repo: MusicRepository) {
        if (job?.isActive == true) return
        val file = File(context.applicationContext.cacheDir, "recommendations.json")
        job = scope.launch {
            if (!loaded) { loaded = true; runCatching { read(file) } }
            val now = System.currentTimeMillis()
            if (now - savedAt < TTL_MS || now - triedAt < RETRY_MS) return@launch
            // offline or YouTube switched off: keep whatever is cached
            if (NetworkGate.blockReason(Feature.YOUTUBE) != null) return@launch
            triedAt = now
            val fresh = build(repo)
            if (fresh.values.any { it.isNotEmpty() }) {
                _shelves.value = fresh
                savedAt = now
                runCatching { write(file) }
            }
        }
    }

    private suspend fun build(repo: MusicRepository): Map<Shelf, List<YtSong>> {
        val recent = repo.recentlyPlayed(100).first()
        val most = repo.mostPlayed(20).first()
        val played = recent.mapNotNullTo(HashSet()) { it.ytVideoId() }
        val favorites = repo.favoriteTracks()
        val downloads = repo.tracks.value.filter { it.id.startsWith("yt_") }
        val shelves = mapOf(
            Shelf.PLAYED to shelf((recent.take(2) + most.take(2)).distinctBy { it.id }, recent + most, played),
            Shelf.LIKED to shelf(favorites.shuffled(), favorites, played),
            Shelf.DOWNLOADS to shelf(downloads.shuffled(), downloads, played),
        )
        return shelves + (Shelf.FOR_YOU to RadioMixer.mix(shelves.values.toList(), played).take(PER_SHELF))
    }

    /** Mixes of the first [SEEDS] songs that have a YouTube video; [known] songs are left out. */
    private suspend fun shelf(seeds: List<Track>, known: List<Track>, played: Set<String>): List<YtSong> {
        val mixes = ArrayList<List<YtSong>>()
        for (t in seeds) {
            if (mixes.size == SEEDS) break
            val id = videoId(t) ?: continue
            try {
                mixes += YouTubeSource.radio(id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // one failed mix only thins the shelf
            }
        }
        // known local files have no video id; sameWork still catches them by title and artist
        return RadioMixer.clean(known.map { it.toYtSong() }, RadioMixer.mix(mixes, played), played).take(PER_SHELF)
    }

    private suspend fun videoId(t: Track): String? {
        t.ytVideoId()?.let { return it }
        matched[t.id]?.let { return it.ifEmpty { null } }
        val v = runCatching { SongMatcher.candidates(t.artist, t.title, t.durationMs).firstOrNull()?.takeIf { it.score >= SongMatcher.ACCEPT }?.song?.videoId }
            .getOrElse { if (it is CancellationException) throw it else return null }
        matched[t.id] = v.orEmpty()
        return v
    }

    private fun read(file: File) {
        if (!file.exists()) return
        val o = JSONObject(file.readText())
        savedAt = o.optLong("savedAt")
        val shelves = o.getJSONObject("shelves")
        _shelves.value = Shelf.entries.associateWith { s ->
            val a = shelves.optJSONArray(s.name) ?: JSONArray()
            (0 until a.length()).map { i ->
                val j = a.getJSONObject(i)
                YtSong(j.getString("v"), j.getString("t"), j.getString("a"), durationMs = j.optLong("d"), thumbnail = j.optString("th").ifEmpty { null })
            }
        }
    }

    private fun write(file: File) {
        val shelves = JSONObject()
        _shelves.value.forEach { (s, list) ->
            shelves.put(s.name, JSONArray(list.map { JSONObject().put("v", it.videoId).put("t", it.title).put("a", it.artist).put("d", it.durationMs).put("th", it.thumbnail ?: "") }))
        }
        file.writeText(JSONObject().put("savedAt", savedAt).put("shelves", shelves).toString())
    }
}
