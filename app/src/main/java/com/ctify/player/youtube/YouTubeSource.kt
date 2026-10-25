package com.ctify.player.youtube

import com.ctify.player.data.Track
import com.ctify.player.diag.Diagnostics
import com.ctify.player.diag.Failure
import com.ctify.player.diag.classOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** A song on YouTube Music. */
data class YtSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val thumbnail: String? = null,
    val trackNumber: Int? = null,
) {
    val url: String get() = "https://music.youtube.com/watch?v=$videoId"

    /** A playable track that streams through ytstream:// until it's downloaded. */
    fun toTrack() = Track(
        id = STREAM_PREFIX + videoId,
        title = title,
        artist = artist,
        album = album ?: "YouTube",
        uri = YT_SCHEME + "://" + videoId,
        coverUri = thumbnail,
        durationMs = durationMs,
        isDemo = false,
    )

    companion object {
        const val STREAM_PREFIX = "yts_"
        const val YT_SCHEME = "ytstream"
    }
}

/** What a pasted or shared YouTube link points to. */
sealed interface YtLink {
    data class Song(val song: YtSong) : YtLink
    data class Album(val album: YtAlbum) : YtLink
    data class Playlist(val playlist: YtPlaylist) : YtLink
}

data class YtAlbum(val id: String, val title: String, val artist: String, val thumbnail: String?, val songs: List<YtSong>)

/** A normal YouTube playlist (PL..., VL...): songs keep their own covers and albums. */
data class YtPlaylist(val id: String, val title: String, val owner: String, val thumbnail: String?, val songs: List<YtSong>)

/** Search, stream and browse YouTube Music. Every call goes through NetworkGate. */
object YouTubeSource {
    private const val MAX_ITEMS = 200
    private const val CACHE_MS = 5 * 60 * 1000L
    private const val CACHE_SIZE = 32

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private class Hit(val at: Long, val value: Deferred<Any>) {
        @Volatile var result: Any? = null
    }
    private val cache = object : LinkedHashMap<String, Hit>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Hit>) = size > CACHE_SIZE
    }

    /** Fetches the search client version once, so the first search is a single request. */
    fun warmUp() {
        scope.launch {
            runCatching { YtRuntime.ensure(); YoutubeParsingHelper.getYoutubeMusicClientVersion() }
        }
    }

    /** A cached result from the last [CACHE_MS], or null; lets the screen show it before any wait. */
    @Suppress("UNCHECKED_CAST")
    fun <T> cached(kind: String, query: String): T? = synchronized(cache) {
        val hit = cache[key(kind, query)] ?: return null
        if (System.currentTimeMillis() - hit.at > CACHE_MS) return null
        hit.result as T?
    }

    private fun key(kind: String, query: String) = kind + "|" + YtRuntime.hl + "|" + query.trim().lowercase()

    /** One request per query: repeats come from the cache, and identical queries share the request in flight. */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> memo(kind: String, query: String, fetch: suspend () -> T): T {
        val k = key(kind, query)
        val hit = synchronized(cache) {
            cache[k]?.takeIf { System.currentTimeMillis() - it.at < CACHE_MS && !it.value.isCancelled }
                ?: Hit(System.currentTimeMillis(), scope.async { fetch() as Any }).also { cache[k] = it }
        }
        val d = hit.value
        return try {
            (d.await() as T).also { hit.result = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            synchronized(cache) { if (cache[k]?.value === d) cache.remove(k) }
            throw e
        }
    }

    suspend fun search(query: String): List<YtSong> = memo(SONGS, query) {
        lookup("Search songs", query) {
            val info = SearchInfo.getInfo(ServiceList.YouTube, ServiceList.YouTube.searchQHFactory.fromQuery(query, filter(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), null))
            info.relatedItems.mapNotNull(::toSong)
        }
    }

    /** Every YouTube video, not only music: for live sets, rare uploads and other niche audio. */
    suspend fun searchVideos(query: String): List<YtSong> = memo(VIDEOS, query) {
        lookup("Search videos", query) {
            val info = SearchInfo.getInfo(ServiceList.YouTube, ServiceList.YouTube.searchQHFactory.fromQuery(query, filter(YoutubeSearchQueryHandlerFactory.VIDEOS), null))
            info.relatedItems.mapNotNull(::toSong)
        }
    }

    suspend fun searchAlbums(query: String): List<YtPlaylistRef> = memo(ALBUMS, query) {
        lookup("Search albums", query) {
            val info = SearchInfo.getInfo(ServiceList.YouTube, ServiceList.YouTube.searchQHFactory.fromQuery(query, filter(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS), null))
            info.relatedItems.filterIsInstance<PlaylistInfoItem>().mapNotNull { p ->
                val id = listId(p.url) ?: return@mapNotNull null
                YtPlaylistRef(id, p.name, p.uploaderName.orEmpty(), p.thumbnailUrl)
            }
        }
    }

    const val SONGS = "songs"
    const val VIDEOS = "videos"
    const val ALBUMS = "albums"

    /** True for youtube.com, music.youtube.com and youtu.be links. */
    fun isLink(text: String) = Regex("(?i)(https?://)?([a-z]+\\.)?(youtube\\.com|youtu\\.be)/").containsMatchIn(text)

    /** Opens a song, album or playlist link; a link with both a video and a list opens the list, except RD mixes. */
    suspend fun fromLink(text: String): YtLink {
        val url = Regex("\\S*(youtube\\.com|youtu\\.be)/\\S*").find(text)?.value ?: text.trim()
        listId(url)?.takeUnless { it.startsWith("RD") }?.let { id ->
            return if (id.startsWith("OLAK")) YtLink.Album(album(id)) else YtLink.Playlist(openPlaylist(id))
        }
        val id = videoId(url) ?: throw YtParseException("This link isn't a YouTube song, album or playlist")
        return YtLink.Song(song(id))
    }

    /** Title, artist and length of one video. */
    suspend fun song(videoId: String): YtSong = lookup("Open song", videoId) {
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        YtSong(
            videoId = videoId,
            title = info.name,
            artist = info.uploaderName?.removeSuffix(" - Topic")?.takeIf { it.isNotBlank() } ?: "Unknown artist",
            durationMs = info.duration.coerceAtLeast(0L) * 1000L,
            thumbnail = info.thumbnailUrl,
        )
    }

    /** The chapter marks of a video; fewer than two means it has none. */
    suspend fun chapters(videoId: String): List<Chapter> = lookup("Chapters", videoId) {
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        info.streamSegments.map { Chapter(it.title.orEmpty(), it.startTimeSeconds * 1000L) }
            .sortedBy { it.startMs }.distinctBy { it.startMs }.takeIf { it.size >= 2 }.orEmpty()
    }

    /** Resolves an audio-only stream, M4A preferred; see [StreamResolver]. */
    suspend fun stream(videoId: String, fresh: Boolean = false, forDownload: Boolean = false): YtStream =
        StreamResolver.resolve(videoId, fresh, forDownload)

    /** An album playlist (OLAK5uy...) with track numbers and one shared cover. */
    suspend fun album(id: String): YtAlbum = lookup("Open album", id) {
        val (info, items) = playlist("https://www.youtube.com/playlist?list=$id")
        val artist = info.uploaderName?.removeSuffix(" - Topic")?.takeIf { it.isNotBlank() }
        // album playlists are named "Album - <title>" in the session language
        val title = info.name.replace(Regex("^\\S+ [-–] "), "")
        val songs = items.mapIndexedNotNull { i, s -> toSong(s)?.copy(album = title, trackNumber = i + 1, thumbnail = info.thumbnailUrl ?: s.thumbnailUrl) }
        YtAlbum(id, title, artist ?: songs.firstOrNull()?.artist ?: "Unknown artist", info.thumbnailUrl, songs)
    }

    /** A playlist with its name, owner and cover; VL prefix allowed. */
    suspend fun openPlaylist(id: String): YtPlaylist = lookup("Open playlist", id) {
        val clean = id.removePrefix("VL")
        val (info, items) = playlist("https://www.youtube.com/playlist?list=$clean")
        val owner = info.uploaderName?.removeSuffix(" - Topic")?.takeIf { it.isNotBlank() }
        YtPlaylist(clean, info.name, owner ?: "YouTube", info.thumbnailUrl, items.mapNotNull(::toSong))
    }

    /** Songs of any playlist id, VL prefix allowed. */
    suspend fun playlistSongs(id: String): List<YtSong> = lookup("Open playlist", id) {
        playlist("https://www.youtube.com/playlist?list=${id.removePrefix("VL")}").second.mapNotNull(::toSong)
    }

    /** Song radio: the RDAMVM mix that YouTube Music plays after a song. */
    suspend fun radio(videoId: String): List<YtSong> = lookup("Song radio", videoId) {
        playlist("https://www.youtube.com/watch?v=$videoId&list=RDAMVM$videoId", maxPages = 2).second.mapNotNull(::toSong)
    }

    suspend fun moodsAndGenres(): List<YtCategory> = lookup("Browse moods and genres", null) {
        YtBrowse.categories(YtBrowse.browse("FEmusic_moods_and_genres"))
    }

    suspend fun categoryPlaylists(c: YtCategory): List<YtPlaylistRef> = lookup("Browse ${c.title}", c.browseId) {
        YtBrowse.playlists(YtBrowse.browse(c.browseId, c.params))
    }

    suspend fun charts(): List<YtPlaylistRef> = lookup("Browse charts", null) {
        YtBrowse.playlists(YtBrowse.browse("FEmusic_charts"))
    }

    private fun playlist(url: String, maxPages: Int = 10): Pair<PlaylistInfo, List<StreamInfoItem>> {
        val info = PlaylistInfo.getInfo(ServiceList.YouTube, url)
        val items = ArrayList<StreamInfoItem>(info.relatedItems)
        var page = info.nextPage
        var n = 1
        while (page != null && n < maxPages && items.size < MAX_ITEMS) {
            val more = PlaylistInfo.getMoreItems(ServiceList.YouTube, url, page)
            items += more.items
            page = more.nextPage
            n++
        }
        return info to items.distinctBy { it.url }
    }

    private fun filter(name: String): List<FilterItem> {
        val f = ServiceList.YouTube.searchQHFactory.availableContentFilter.filterGroups
            .flatMap { it.filterItems.asList() }.firstOrNull { it.name == name } ?: throw YtParseException("Search filter $name missing")
        return listOf(f)
    }

    fun videoId(url: String?): String? = url?.let {
        Regex("(?:v=|youtu\\.be/|/shorts/)([A-Za-z0-9_-]{11})").find(it)?.groupValues?.get(1)
    }

    private fun listId(url: String?): String? = url?.let { Regex("[?&]list=([A-Za-z0-9_-]+)").find(it)?.groupValues?.get(1) }

    private fun toSong(item: InfoItem): YtSong? {
        val s = item as? StreamInfoItem ?: return null
        val id = videoId(s.url) ?: return null
        return YtSong(
            videoId = id,
            title = s.name,
            artist = s.uploaderName?.removeSuffix(" - Topic")?.takeIf { it.isNotBlank() } ?: "Unknown artist",
            durationMs = s.duration.coerceAtLeast(0L) * 1000L,
            thumbnail = s.thumbnailUrl,
        )
    }

    /** Runs a lookup on IO and files a diagnostic when it fails. */
    private suspend fun <T> lookup(action: String, item: String?, block: suspend () -> T): T = withContext(Dispatchers.IO) {
        YtRuntime.ensure()
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Diagnostics.record(
                Failure(
                    feature = "YouTube",
                    action = action,
                    component = StreamResolver.COMPONENT,
                    item = item,
                    error = e,
                    errorClass = YtFailures.errorClass(e) ?: classOf(e, (e as? YtHttpException)?.status),
                )
            )
            throw e
        }
    }
}
