package com.ctify.player.recommend

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import com.ctify.player.data.MusicRepository
import com.ctify.player.data.Track
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.ui.youtube.toYtSong
import com.ctify.player.ui.youtube.ytVideoId
import com.ctify.player.youtube.YouTubeSource
import com.ctify.player.youtube.YtSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * The Up next switch: Library plays the queue the listener started, Recommended plays the YouTube mix
 * of the song playing now. The UI sets [recommended]; PlaybackController watches it and rebuilds the queue.
 */
object UpNext {
    /** songs fetched per mix */
    const val BATCH = 25
    private var prefs: SharedPreferences? = null
    val recommended = MutableStateFlow(false)
    /** true while a mix is fetched for the switch */
    val loading = MutableStateFlow(false)
    /** why the switch fell back to Library, until the next choice */
    val error = MutableStateFlow<String?>(null)

    fun init(context: Context) {
        if (prefs != null) return
        HiddenArtists.init(context)
        val p = context.applicationContext.getSharedPreferences("up_next", Context.MODE_PRIVATE)
        prefs = p
        recommended.value = p.getBoolean("recommended", false)
    }

    fun choose(on: Boolean) {
        error.value = null
        recommended.value = on
        prefs?.edit()?.putBoolean("recommended", on)?.apply()
    }

    /** Back to Library for now (the saved choice stays), with [message] shown under the switch. */
    fun fail(message: String) {
        error.value = message
        recommended.value = false
    }

    /** The mix of [current] ready to queue; throws [Unavailable] with a message the switch can show. */
    suspend fun fetch(context: Context, repo: MusicRepository, current: Track, queued: List<Track>): List<Track> {
        val online = context.getSystemService(ConnectivityManager::class.java)?.activeNetwork != null
        if (!online || NetworkGate.offline.value) throw Unavailable(OFFLINE)
        NetworkGate.blockReason(Feature.YOUTUBE)?.let { throw Unavailable(it) }
        val mix = try {
            val id = current.ytVideoId() ?: SongRadio.find(current.artist, current.title, current.durationMs)?.videoId
            if (id == null) emptyList() else YouTubeSource.radio(id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw Unavailable("Couldn't load recommendations")
        }
        val out = withContext(Dispatchers.Default) { pick(current, queued, repo.tracks.value, mix, HiddenArtists.names.value) }
        if (out.isEmpty()) throw Unavailable("No recommendations for this song")
        repo.remember(out)
        return out
    }

    class Unavailable(message: String) : Exception(message)

    private const val OFFLINE = "Can't load recommendations offline"

    /**
     * What plays after [current]: its [mix] cleaned by [RadioMixer], without hidden artists or songs already
     * [queued]; songs in the [library] play from the library copy, the rest stream.
     */
    fun pick(current: Track, queued: List<Track>, library: List<Track>, mix: List<YtSong>, hidden: Set<String>, max: Int = BATCH): List<Track> {
        val known = queued + current
        val ids = known.mapTo(HashSet()) { it.id }
        val byVideo = library.filter { it.ytVideoId() != null }.associateBy { it.ytVideoId() }
        val byWork = library.groupBy { RadioMixer.work(it.title) }
        return HiddenArtists.filter(RadioMixer.clean(known.map { it.toYtSong() }, mix, emptySet()), hidden) { it.artist }
            .map { s -> byVideo[s.videoId] ?: byWork[RadioMixer.work(s.title)]?.firstOrNull { RadioMixer.sameWork(it.toYtSong(), s) } ?: s.toTrack() }
            .filter { it.id !in ids }
            .distinctBy { it.id }
            .take(max)
    }
}
