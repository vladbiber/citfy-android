package com.ctify.player.youtube

import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Reads a public Spotify playlist or album from its embed page and finds each song on YouTube Music. */
object SpotifyImport {
    data class SpTrack(val title: String, val artist: String, val durationMs: Long)
    data class SpList(val name: String, val cover: String?, val tracks: List<SpTrack>)

    private val LINK = Regex("(?:open\\.spotify\\.com/(?:intl-[a-z]+/)?|spotify:)(playlist|album)[/:]([A-Za-z0-9]{10,})")

    fun isLink(text: String) = LINK.containsMatchIn(text)

    /** The embed page lists up to about 100 songs, which is all it can see without an account. */
    suspend fun read(link: String): SpList = withContext(Dispatchers.IO) {
        val m = LINK.find(link) ?: throw IllegalArgumentException("This isn't a Spotify playlist or album link")
        val (kind, id) = m.destructured
        val req = NetworkGate.request("https://open.spotify.com/embed/$kind/$id", Feature.SPOTIFY_IMPORT).build()
        val html = NetworkGate.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("Spotify answered HTTP ${r.code}")
            r.body.string()
        }
        val json = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: throw IllegalStateException("Spotify changed its page; the import needs an update")
        val e = JSONObject(json).getJSONObject("props").getJSONObject("pageProps").getJSONObject("state")
            .getJSONObject("data").getJSONObject("entity")
        val list = e.getJSONArray("trackList")
        val tracks = (0 until list.length()).map { list.getJSONObject(it) }.map {
            SpTrack(it.optString("title"), it.optString("subtitle"), it.optLong("duration"))
        }.filter { it.title.isNotBlank() }
        val cover = e.optJSONObject("coverArt")?.optJSONArray("sources")?.optJSONObject(0)?.optString("url")
        SpList(e.optString("name").ifBlank { e.optString("title", "Spotify playlist") }, cover?.takeIf { it.isNotBlank() }, tracks)
    }

    /** Best YouTube match for one Spotify song, or null when nothing is close enough. */
    suspend fun match(t: SpTrack): YtSong? {
        // Spotify lists every artist; the first one is what YouTube titles use
        val artist = t.artist.split(",").first().trim()
        val best = try {
            SongMatcher.candidates(artist, t.title, t.durationMs).firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        delay(250)
        return best?.takeIf { it.score >= MIN_SCORE }?.song
    }

    private const val MIN_SCORE = 0.6
}
