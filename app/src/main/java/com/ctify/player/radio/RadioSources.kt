package com.ctify.player.radio

import com.ctify.player.integration.HttpStatusException
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/** Blocking GET through the gate for small text responses, capped at [maxBytes]. */
internal suspend fun fetchText(url: String, maxBytes: Long = 1L shl 20, headers: Map<String, String> = emptyMap()): String =
    withContext(Dispatchers.IO) {
        val req: Request = NetworkGate.request(url, Feature.RADIO).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        NetworkGate.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpStatusException(resp.code, "HTTP ${resp.code} from ${req.url.host}")
            val source = resp.body.source()
            source.request(maxBytes)
            val buf = source.buffer
            buf.readUtf8(minOf(buf.size, maxBytes))
        }
    }

/** The cliamp channels (radio.cliamp.stream): curated streams with live listener counts. */
class CliampRadio {
    suspend fun stations(): List<Station> {
        val body = JSONObject(fetchText("$BASE/stations"))
        val listeners = runCatching { activeListeners() }.getOrDefault(emptyMap())
        val arr = body.getJSONArray("stations")
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val id = o.optString("id").ifBlank { return@mapNotNull null }
            val stream = o.optString("stream").takeIf { RadioUrlPolicy.isAllowed(it) } ?: return@mapNotNull null
            Station(
                uuid = "cliamp-$id",
                name = o.optString("name", id),
                streamUrl = stream,
                homepage = "https://cliamp.stream",
                favicon = "$BASE/logo.svg",
                tags = o.optString("genre").lowercase().split(' ', '/').filter { it.isNotBlank() }.joinToString(","),
                codec = "MP3",
                source = StationSource.CLIAMP,
                listeners = listeners[id],
                description = o.optString("description").takeIf { it.isNotBlank() },
            )
        }
    }

    private suspend fun activeListeners(): Map<String, Int> {
        val stations = JSONObject(fetchText("$BASE/statistics")).getJSONObject("stations")
        return stations.keys().asSequence().associateWith { stations.getJSONObject(it).optInt("active_listeners") }
    }

    companion object {
        const val BASE = "https://radio.cliamp.stream"
    }
}

/**
 * Turns a directory URL into something ExoPlayer can play: .m3u/.pls/.asx playlists are
 * read for their first stream; plain streams pass through. Every URL found is re-checked.
 */
object StreamResolver {
    private val playlistExt = Regex("(?i)\\.(m3u|pls|asx|xspf)(\\?.*)?$")

    suspend fun resolve(url: String): String {
        if (!RadioUrlPolicy.isAllowed(url)) throw IllegalArgumentException("Blocked stream address")
        // .m3u8 is HLS, which ExoPlayer plays directly
        if (!playlistExt.containsMatchIn(url) || url.contains(".m3u8", true)) return url
        val body = fetchText(url, maxBytes = 64 * 1024)
        val found = parsePlaylist(body).firstOrNull { RadioUrlPolicy.isAllowed(it) }
            ?: throw IllegalStateException("Playlist has no playable stream")
        // playlists can point to playlists, one level is enough in practice
        return if (playlistExt.containsMatchIn(found) && !found.contains(".m3u8", true)) {
            parsePlaylist(fetchText(found, 64 * 1024)).firstOrNull { RadioUrlPolicy.isAllowed(it) } ?: found
        } else found
    }

    fun parsePlaylist(body: String): List<String> {
        val pls = Regex("(?im)^File\\d+\\s*=\\s*(\\S+)").findAll(body).map { it.groupValues[1] }.toList()
        if (pls.isNotEmpty()) return pls
        val xml = Regex("(?i)(?:href|<location>)\\s*=?\\s*\"?(https?://[^\"<\\s]+)").findAll(body).map { it.groupValues[1] }.toList()
        if (xml.isNotEmpty()) return xml
        return body.lineSequence().map { it.trim() }.filter { it.startsWith("http", true) }.toList()
    }
}

/** Now-playing for stations without stream titles: NTS shows and FIP tracks (logic from cliamp, MIT). */
object ExternalNowPlaying {
    /** How to poll [streamUrl], or null if it isn't NTS or FIP. */
    fun pollerFor(streamUrl: String): Pair<Long, suspend () -> String?>? {
        val u = streamUrl.lowercase()
        return when {
            u.contains("ntslive.net") || u.contains("radiomast.io/nts") || u.contains("/nts1") || u.contains("/nts2") -> {
                val channel = if (u.contains("stream2") || u.contains("nts2")) "2" else "1"
                30_000L to suspend { nts(channel) }
            }
            u.contains("radiofrance.fr") && u.contains("fip") -> {
                val id = fipStations.firstOrNull { u.contains(it.first) }?.second ?: 7
                15_000L to suspend { fip(id) }
            }
            else -> null
        }
    }

    private val fipStations = listOf(
        "fipjazz" to 65, "fipgroove" to 66, "fiprock" to 64, "fipreggae" to 71,
        "fipelectro" to 74, "fipmetal" to 77, "fipnouveau" to 70, "fipworld" to 69, "fipmonde" to 69,
    )

    private suspend fun nts(channel: String): String? {
        val results = JSONObject(fetchText("https://www.nts.live/api/v2/live")).optJSONArray("results") ?: return null
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            if (r.optString("channel_name") != channel) continue
            val now = r.optJSONObject("now") ?: return null
            val name = now.optJSONObject("embeds")?.optJSONObject("details")?.optString("name")?.trim()
            return name?.takeIf { it.isNotEmpty() } ?: now.optString("broadcast_title").trim().takeIf { it.isNotEmpty() }
        }
        return null
    }

    private suspend fun fip(station: Int): String? {
        val steps = JSONObject(fetchText("https://api.radiofrance.fr/livemeta/pull/$station")).optJSONObject("steps") ?: return null
        val now = System.currentTimeMillis() / 1000
        var best: JSONObject? = null
        for (k in steps.keys()) {
            val s = steps.getJSONObject(k)
            val start = s.optLong("start"); val end = s.optLong("end")
            if (start <= now && now < end) { best = s; break }
            if (start <= now && start > (best?.optLong("start") ?: -1)) best = s
        }
        val b = best ?: return null
        val artist = b.optString("authors").trim(); val title = b.optString("title").trim()
        return when {
            artist.isNotEmpty() && title.isNotEmpty() -> "$artist - $title"
            else -> title.ifEmpty { artist }.ifEmpty { null }
        }
    }
}
